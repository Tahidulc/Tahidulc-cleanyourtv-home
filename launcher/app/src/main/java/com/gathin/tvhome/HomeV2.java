package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Launcher v2: Home in the style of Sky Glass. The app picture fills the top right, the scores and the clock stay on
 * top, the dock's apps become a slim rail of icons down the left, and the focused app's own tiles (its Continue
 * watching and Recommended, live channels for TiviMate) sit in one row of four that lines up with the screen; six
 * large boxes along the bottom open the TV guide, Discover, Movies, TV shows, Sport and the apps.
 *
 * MainActivity hosts it when the theme's "launcher" is "v2"; "v1" (the default) keeps the
 * classic Home, untouched. Everything is drawn in a 1920-wide design space and scaled to the screen, and the remote
 * is handled here zone by zone (top buttons, rail, row, boxes) rather than through Android's focus search.
 */
final class HomeV2 {
    /** Whether this TV shows launcher v2: the theme's "launcher" ("v1" or "v2"), set only when the theme is pushed. */
    static boolean chosen(Context c) {
        // An older build kept a choice from the settings menu here; it no longer counts.
        c.getSharedPreferences("launcher_version", Context.MODE_PRIVATE).edit().clear().apply();
        return "v2".equalsIgnoreCase(Config.themeString(c, "launcher", "v1").trim());
    }

    // ---- look ------------------------------------------------------------------------------------------------------

    static final int BG_DEEP = 0xFF11154A, BG_MID = 0xFF1D2378, BG_GLOW = 0xFF3A2A9A;
    private static final int GLASS = 0x1AFFFFFF, GLASS_LINE = 0x52FFFFFF;
    static final PathInterpolator SKY = new PathInterpolator(0.25f, 0.8f, 0.25f, 1f);
    // The sections. Kids (6) is the dock layout's own, after Apps; beside the rail there are the first six boxes.
    static final int[] CAT_COLOURS = {0xFFFF8A1F, 0xFF9B5CFF, 0xFFFF4FA3, 0xFF4F8BFF, 0xFF3DDC84, 0xFF2AD1C9, 0xFFFFC93C};
    static final String[] CAT_NAMES = {"TV Guide", "Discover", "Movies", "TV shows", "Sports", "Apps", "Kids"};
    static final int[] CAT_ICONS = {R.drawable.ic_v2_guide, R.drawable.ic_v2_discover, R.drawable.ic_v2_movies,
            R.drawable.ic_v2_shows, R.drawable.ic_v2_sport, R.drawable.ic_v2_grid, R.drawable.ic_v2_kids};
    private static final String[] CAT_MODES = {"guide", "discover", "movies", "shows", "sport", "apps", "kids"};
    static final int KIDS = 6;
    /** The sections' order along the dock: Kids between Sports and Apps. */
    static final int[] DOCK_ORDER = {0, 1, 2, 3, 4, KIDS, 5};

    /** Where section `which` stands along the dock (beside the rail, its own place). */
    private int dockPos(int which) {
        if (!dockMode) return which;
        for (int i = 0; i < DOCK_ORDER.length; i++) if (DOCK_ORDER[i] == which) return i;
        return which;
    }

    /** The section standing at place `pos` along the dock. */
    private int sectionAt(int pos) {
        return dockMode ? DOCK_ORDER[pos] : pos;
    }

    /** The rail: a glyph for each kind of dock app, matched on its package. */
    static int glyph(String pkg) {
        if (pkg == null) return R.drawable.ic_v2_apps;
        if (pkg.equals("ar.tvplayer.tv") || pkg.contains("tivimate")) return R.drawable.ic_v2_tv;
        if (pkg.contains("youtube")) return R.drawable.ic_v2_youtube;
        if (pkg.contains("netflix")) return R.drawable.ic_v2_netflix;
        if (pkg.contains("disney")) return R.drawable.ic_v2_disney;
        if (pkg.contains("amazon")) return R.drawable.ic_v2_prime;
        return R.drawable.ic_v2_play;
    }

    private static String railLabel(String pkg, String label) {
        if (pkg == null) return "ALL APPS";
        if (pkg.equals("ar.tvplayer.tv")) return "LIVE TV · ON NOW";
        if (pkg.contains("youtube")) return "YOUTUBE · CONTINUE WATCHING & RECOMMENDED";
        if (pkg.contains("debrid")) return "NEW RELEASES · JUST ADDED";
        return label.toUpperCase(Locale.ROOT) + " · POPULAR NOW";
    }

    // ---- state -----------------------------------------------------------------------------------------------------

    static final class Tile {
        String title, sub, image, logo, chip, badge;
        /** The hero above the row while it is focused. */
        String heroImage, heroLogo, heroTop, heroTitle, heroMeta, heroText;
        float progress = -1;
        int progressColour = Color.WHITE;
        Drawable icon;
        Runnable ok;
        int tmdbId;
        boolean tv;
        /** Sport: "football" or "other", and the competition's logo for the corner chip. */
        String group, compLogo;
        /** The playlist's stream for a channel's tile (it plays in the tile), and when an event starts (its reminder). */
        String stream, channel;
        long start;
        /** A video's thumbnail (YouTube's): shown whole, over a blur of itself, with no words over it. */
        boolean video;
        /** Under the hero's title, as Sky's: the running time, then marks (HD, 4K, DOLBY VISION, ATMOS, S, AD, the
         * age rating) and the streaming service's logo. */
        String duration, service, serviceId;
        List<String> specs = new ArrayList<>();
    }

    private static final int Z_TOP = 0, Z_RAIL = 1, Z_ROW = 2, Z_CATS = 3, Z_SCORES = 4, Z_MATCH = 5;

    final MainActivity a;
    final Handler main = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newFixedThreadPool(3);
    private final ExecutorService pics = Executors.newFixedThreadPool(6);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(
            (int) Math.min(Runtime.getRuntime().maxMemory() / 6, 48L * 1024 * 1024)) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private final Map<String, List<Tile>> rows = new HashMap<>();
    private final Map<String, Long> rowsAt = new HashMap<>();
    private final Map<String, String> logos = new HashMap<>();
    float s;
    Typeface regular, semi, displayRegular, displaySemi;
    String tmdbKey;
    private List<String> dock;
    List<String[]> channels;

    FrameLayout root;
    ImageView heroA, heroB;
    /**
     * lite_home (a theme's, for a TV whose graphics chip is slower, the TCL): the same Home, with its big redraws made
     * rarer and shorter. The hero (most of the screen) changes once the focus rests rather than while it moves, and
     * cross-fades more quickly; a tile's video starts a moment later, once the focus has settled on it.
     */
    boolean lite;
    private boolean heroFrontA = true;
    private String heroShown;
    LinearLayout htext;
    private ImageView hTop, hLogo;
    private TextView hTopText, hTitle, hMeta, hText;
    private LinearLayout hSpecs;
    private Tile heroTile;
    final List<FrameLayout> railViews = new ArrayList<>();
    /**
     * The dock layout (theme "home_dock", on unless turned off): no rail down the left; the rail's apps, in their own
     * colours, and the six sections in one glass dock along the foot, and the row of tiles the full width above it.
     */
    boolean dockMode;
    /** The liquid-glass focus over the row and the dock (the dock layout). */
    private GlassLens lens;
    /** The dock's glass and the rule between its apps and its sections. */
    private View dockBg, dockSep;
    /**
     * The dock's own layer (its glass, the focus glass, the apps, the rule and the sections), so the whole dock can
     * tuck away under the foot of the screen while a section's posters have the focus, and pop back up.
     */
    private FrameLayout dockLayer;
    /** The dock tucked away (theme "dock_tuck", on unless turned off); the chip at the foot that says where it went. */
    private boolean dockAway;
    private LinearLayout dockHandle;
    private ImageView dockHandleIcon, dockHandleArrow;
    /** The dock: its top and height; the sections' width. */
    private static final int DOCK_Y = 944, DOCK_H = 100, DOCK_APP = 64, DOCK_SEC = 112, DOCK_GAP = 14;
    /** How far a dock app's icon rises under the focus (as the sections do), and the glass's margin round it. */
    static final int APP_LIFT = 4, APP_GLASS = 7;
    /** Match Day's panel, from the top of the screen. */
    private static final int MATCH_Y = 190;
    TextView label;
    FrameLayout viewport;
    private LinearLayout row;
    final List<View> catViews = new ArrayList<>();
    private final List<View> topViews = new ArrayList<>();
    private TextView wifi;
    private View vpn;
    /**
     * The top bar as option B ("Corners", theme "top_corners", on in the dock layout): the time with the date and the
     * weather under it at the top left; at the top right one score pill that cycles through the games (a dot for each)
     * beside a round Control Centre button, all on one line. A section's page moves up PAGE_LIFT into the room it frees.
     */
    boolean corners;
    static final int PAGE_LIFT = 90;
    /** Option B's round Control Centre button and the score pill's dots. */
    private ImageView ccButton;
    private LinearLayout pageDots;

    /** Where the Control Centre and its panels grow out of: the top bar's button (null when there is none on screen). */
    View controlButton() {
        return ccButton != null ? ccButton : wifi;
    }

    /** Where the weather card grows out of. */
    View weatherButton() {
        return weatherView;
    }

    /** adb only (--ez open_notifications true): the Control Centre's Notifications, as its tile opens them. */
    void openNotifications() {
        if (notifyPanel != null) notifyPanel.show();
    }

    /** adb only (test_live_ticker): a live game in the top bar's ticker, its clock ticking. */
    void testLiveTicker() {
        if (scores != null) scores.testLive();
    }

    /** adb only (--ez test_coming_up true): TV shows on its Coming up tab (popular series' next episodes when yours have none). */
    void testComingUp() {
        ComingUp.test = true;
        openBox(3);
    }

    /**
     * adb only (--ez test_episode_map true): TV shows, the first series in its row focused and its panel opened with the
     * Episode map open (sample episodes when TMDB has none).
     */
    void testEpisodeMap() {
        TitlePanel.autoMap = true;
        openBox(3);
        tryEpisodeMap(8);
    }

    /** The page's first series' panel once its posters are in (tried every second and a half, tries times). */
    private void tryEpisodeMap(int tries) {
        main.postDelayed(() -> {
            boolean done = page instanceof PagesV2.PosterPage && ((PagesV2.PosterPage) page).openFirstSeries();
            if (!done && tries > 1) tryEpisodeMap(tries - 1);
            else if (!done) TitlePanel.autoMap = false;
        }, 1500);
    }

    /** How far a section's page sits higher than it was laid out (option B's room at the top; 0 on Home). */
    int pageLift() {
        return corners && page != null ? PAGE_LIFT : 0;
    }

    /**
     * On Home under the one-line top bar: Match Day and the hero's words sit this much higher, into the room the old
     * clock left, and Match Day a little larger (HOME_GROW); the row under them stays where it is.
     */
    static final int HOME_LIFT = 72;
    static final float HOME_GROW = 1.12f;

    int homeLift() {
        return corners ? HOME_LIFT : 0;
    }
    ScoresTicker scores;
    private WeatherLine weatherLine;
    private GuideV2 guide;
    /** The page open over Home (Discover, Movies, TV shows, Sport), or null. */
    private PagesV2.Page page;

    private int zone = Z_ROW, topIdx, railIdx, rowIdx, catIdx;
    /** Match Day at the top of Home (MatchDay), and which of its buttons the focus is on. */
    private MatchDay match;
    private int matchIdx;
    /** Home has just come back: when Match Day has a game, the focus starts on it. */
    private boolean matchFirst = true;
    /** The rail app whose tiles are in the row (-1: the row is For you or one of the boxes'). */
    private int railSel = -1;
    private String mode = "foryou";
    private List<Tile> shown = new ArrayList<>();
    private int generation;
    private final Runnable heroLater = () -> { if (rowIdx < shown.size()) hero(shown.get(rowIdx)); };
    private final Runnable network = this::updateNetwork;

    HomeV2(MainActivity activity) {
        a = activity;
    }

    // ---- building --------------------------------------------------------------------------------------------------

    void create() {
        // Pictures (and TMDB's answers) kept on disk too, as their servers allow: a page opened after Home restarted
        // shows its posters from the TV's own storage instead of the network.
        try {
            if (android.net.http.HttpResponseCache.getInstalled() == null) {
                android.net.http.HttpResponseCache.install(new java.io.File(a.getCacheDir(), "http"), 96L * 1024 * 1024);
            }
            // Pictures only (fetch asks for it): scores, the weather and the lists are never answered from the disk.
            new URL("https://localhost/").openConnection().setDefaultUseCaches(false);
        } catch (java.io.IOException ignored) {
        }
        int w = a.getResources().getDisplayMetrics().widthPixels, h = a.getResources().getDisplayMetrics().heightPixels;
        s = Math.max(w, h) / 1920f;
        // Inter for text, and its Display cut (tighter, finer) from 34 px up: the clean SF-style look.
        regular = a.getResources().getFont(R.font.inter_regular);
        semi = a.getResources().getFont(R.font.inter_semibold);
        displayRegular = a.getResources().getFont(R.font.inter_display_regular);
        displaySemi = a.getResources().getFont(R.font.inter_display_semibold);
        tmdbKey = Config.themeString(a, "tmdb_key", "").trim();
        lite = Config.themeFlag(a, "lite_home", false);
        dockMode = Config.themeFlag(a, "home_dock", true);
        if (dockMode) { TILE_W = 448; TILE_H = 300; }
        corners = dockMode && Config.themeFlag(a, "top_corners", true);
        // The top bar in the notifications' own Sky-style look (theme "sky_top"): no pills; else the frosted ones.
        ScoresTicker.sky = corners && Config.themeFlag(a, "sky_top", true);
        ScoresTicker.frost = corners && !ScoresTicker.sky && Config.themeFlag(a, "frosted_pills", true);
        PillMorph.squareCorner = ScoresTicker.sky ? SkyStyle.corner(a) : -1;
        players = new TilePlayer(a);
        players.useOverlay(lite);
        if (!tmdbKey.isEmpty()) players.warm();
        dock = new ArrayList<>(Config.load(a).dockApps);
        // Two at once left mid-way (the launcher restarted): its windows full size and every app's sound back.
        TwoUp.recover(a);
        channels = guideChannels(a);
        root = new FrameLayout(a);
        root.setClipChildren(false);
        // Holds Android's focus when nothing on Home should show it (the score pills let go to it).
        root.setFocusable(true);
        root.setDefaultFocusHighlightEnabled(false);
        // The background, its glow and the shades that keep the words readable, drawn once into one picture: the
        // graphics chip then fills the screen once a frame instead of four times.
        // The background (theme "background"): "mesh", soft plum, teal and amber light over midnight, gently shifting
        // while Home shows (still on lite_home); "navy" the old radial blue.
        String bgStyle = Config.themeString(a, "background", "blur");
        mesh = !"navy".equals(bgStyle);
        // "blur": the focused content's own picture, heavily blurred and darkened, as the whole background.
        blurBg = "blur".equals(bgStyle);
        if (mesh) {
            screenW = w;
            screenH = h;
            meshPair[0] = meshBackground(w, h, 0, MESH_DEFAULT);
            meshPair[1] = lite ? null : meshBackground(w, h, 1, MESH_DEFAULT);
            fadeMesh(meshPair[0], 0);
        } else {
            bgShown = background(w, h);
            root.setBackground(new android.graphics.drawable.BitmapDrawable(a.getResources(), bgShown));
        }
        a.setContentView(root);
        a.getWindow().setBackgroundDrawable(null);

        heroA = heroView();
        heroB = heroView();
        heroB.setAlpha(0f);

        buildClock();
        buildButtons();
        buildHeroText();
        buildRail();
        label = text(18, semi, 0xBFFFFFFF);
        label.setLetterSpacing(0.11f);
        // The row's heading (For You, Live TV, Netflix...) over the first tile, always a clear gap under Match Day's
        // buttons (they end at 520), never over them: 552 in the dock layout, 532 beside the rail.
        int headY = MATCH_Y + MatchDay.BUTTONS_Y + MatchDay.BUTTONS_H + (dockMode ? 32 : 12);
        add(label, dockMode ? 48 : 180, headY, 1500, 34);
        match = new MatchDay(this);
        // The clubs' colours where the hero's picture would be, just above it.
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(px(1160), px(652));
        blp.leftMargin = px(760);
        root.addView(match.backdrop, root.indexOfChild(heroB) + 1, blp);
        add(match.panel, dockMode ? 56 : 216, MATCH_Y - homeLift(), 1320, 342);
        if (corners) {
            // Larger in the room the top bar left (it still ends above the row's heading).
            match.panel.setPivotX(0);
            match.panel.setPivotY(0);
            match.panel.setScaleX(HOME_GROW);
            match.panel.setScaleY(HOME_GROW);
        }
        FrameLayout chipBox = new FrameLayout(a);
        chipBox.addView(match.chip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        add(chipBox, dockMode ? 1176 : 1120, headY - (dockMode ? 4 : 8), 700, 40);
        match.changed = this::matchChanged;
        match.kickedOff = () -> bringBackMatch(true);
        viewport = new FrameLayout(a);
        // Clipped to its own box: a row scrolled along stays inside it, clear of the rail.
        viewport.setOutlineProvider(ViewOutlineProvider.BOUNDS);
        viewport.setClipToOutline(true);
        // Only the box clips, not the row's own bounds: a focused tile grows past the row and keeps its whole outline.
        viewport.setClipChildren(false);
        // The four tiles fill the width from beside the rail (180) to near the screen's edge (1896); the row is cut off
        // just after the fourth (room for it to grow when focused), so the fifth waits out of sight until it slides.
        // The dock layout: the row from the left edge (48) to near the right (1876), four wider tiles in it, a page at a
        // time. Its window is cut where the gaps either side of the page are (36 and 1888): nothing of the page before or
        // after shows, only the four, with room for the focused one to grow.
        if (dockMode) add(viewport, 36, 576, 1852, TILE_H + 52); else add(viewport, 164, 556, 1743, TILE_H + 52);
        row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(TILE_H));
        rlp.leftMargin = px(rowInset());
        rlp.topMargin = px(24);
        viewport.addView(row, rlp);
        buildCats();
        if (dockMode) layoutDock();

        String[] leagues = a.getResources().getStringArray(R.array.score_leagues);
        if (leagues.length > 0) {
            scores = new ScoresTicker(a, root, a.getResources().getDisplayMetrics().density, leagues, new ScoresTicker.Host() {
                @Override public void onScoresOpen(ScoresTicker.Game game) { watchGame(game); }
                @Override public void onScoresDown() {
                    View f = a.getCurrentFocus();
                    if (f != null) f.clearFocus();
                    zone = Z_TOP;
                    paint();
                }

                @Override public void onScoresEdge(boolean right) {
                    // Out of the score bar sideways: onto the weather (right) or the Control Centre (left).
                    View f = a.getCurrentFocus();
                    if (f != null) f.clearFocus();
                    zone = Z_TOP;
                    int to = topViews.indexOf(right ? weatherView : ccButton);
                    if (to >= 0) topIdx = to;
                    paint();
                }
            });
            scores.setTypeface(a.getResources().getFont(R.font.inter_ui));
            scores.equalWidths(true);
            scores.showF1(Config.themeFlag(a, "f1_scores_bar", false));
            scores.showAlerts(!Config.themeFlag(a, "alerts_everywhere", false));
            float dp = a.getResources().getDisplayMetrics().density;
            if (corners) {
                // Option B: one pill at the top right, beside the Control Centre button, cycling through the games.
                scores.pageListener = this::showPageDots;
                root.post(() -> {
                    scores.place(px(CORNER_PILL_X), px(CORNER_Y), px(CORNER_PILL_R));
                    // Its frosted glass: the background as it is now (made before the bar was).
                    scores.backdrop(bgShown, 0);
                });
                // An alert grows out of the pill to the right, as far as the weather, date and time (they stay in sight).
                ScoresTicker.lane(px(CORNER_PILL_X), px(CORNER_LANE_R), px(CORNER_Y));
                scores.isHomePill = true;
            } else {
                // place() takes the bar's right edge: 96 in from each side, so the pills sit centred.
                root.post(() -> scores.place(px(96), Math.round(23 * dp), root.getWidth() - px(96)));
                ScoresTicker.lane(-1, 0, 0);
            }
        }
        guide = new GuideV2(a, root, s, regular, semi, channels, this::refocus);
        if (lite) cache();
        load("foryou");
        paint();
    }

    /**
     * lite_home: the glass pieces that seldom change (the boxes, the rail's icons, the top buttons, the score bar)
     * each kept as one finished picture on the graphics chip. When a key press redraws the screen, each is put down as
     * a single image instead of its gradients, rims, icons and words being painted over one another again.
     */
    private void cache() {
        List<View> still = new ArrayList<>(catViews);
        still.addAll(railViews);
        still.addAll(topViews);
        still.add(htext);
        for (View v : still) v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (scores != null) scores.layer(true);
    }

    private ImageView heroView() {
        // One picture: its fade is drawn straight with the alpha, no offscreen copy of the whole picture first.
        ImageView v = new ImageView(a) {
            @Override public boolean hasOverlappingRendering() {
                return false;
            }
        };
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        add(v, 520, 0, 1400, 720);
        return v;
    }

    private View fade(GradientDrawable.Orientation o, int[] colours) {
        View v = new View(a);
        v.setBackground(new GradientDrawable(o, colours));
        return v;
    }

    private void buildClock() {
        if (corners) { buildCorners(); return; }
        LinearLayout clock = new LinearLayout(a);
        clock.setOrientation(LinearLayout.HORIZONTAL);
        clock.setBaselineAligned(false);
        LinearLayout hm = new LinearLayout(a);
        hm.setOrientation(LinearLayout.HORIZONTAL);
        hm.setBaselineAligned(true);
        TextClock time = new TextClock(a);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        style(time, 82, a.getResources().getFont(R.font.inter_display_light), Color.WHITE);
        time.setLetterSpacing(-0.03f);
        time.setIncludeFontPadding(false);
        hm.addView(time);
        TextClock secs = new TextClock(a);
        secs.setFormat24Hour(":ss");
        secs.setFormat12Hour(":ss");
        style(secs, 40, a.getResources().getFont(R.font.inter_display_light), 0xB3FFFFFF);
        secs.setIncludeFontPadding(false);
        hm.addView(secs);
        // Same-width digits, in a box as wide as the widest time: the date and weather beside it never move.
        time.setFontFeatureSettings("tnum");
        secs.setFontFeatureSettings("tnum");
        android.graphics.Paint pt = new android.graphics.Paint();
        pt.setTypeface(time.getTypeface());
        pt.setFontFeatureSettings("tnum");
        pt.setTextSize(px(82));
        float w = pt.measureText("00:00");
        pt.setTextSize(px(40));
        w += pt.measureText(":00");
        clock.addView(hm, new LinearLayout.LayoutParams(Math.round(w) + px(4), ViewGroup.LayoutParams.WRAP_CONTENT));
        View div = new View(a);
        div.setBackgroundColor(0x59FFFFFF);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(px(2), px(66));
        dlp.leftMargin = px(26);
        dlp.rightMargin = px(26);
        dlp.topMargin = px(14);
        clock.addView(div, dlp);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        TextClock date = new TextClock(a);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        style(date, 21, regular, 0xB8FFFFFF);
        date.setSingleLine(true);   // before all-caps: single-line swaps the text transformation
        date.setAllCaps(true);
        date.setLetterSpacing(0.15f);
        col.addView(date);
        TextView weather = text(22, regular, Color.WHITE);
        weather.setLetterSpacing(0.06f);
        weather.setGravity(Gravity.CENTER_VERTICAL);
        weather.setPadding(px(24), 0, px(26), 0);
        weather.setBackground(glass(25, false));
        weather.setCompoundDrawablePadding(px(12));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(50));
        wlp.topMargin = px(10);
        col.addView(weather, wlp);
        clock.addView(col);
        add(clock, dockMode ? 52 : 96, 92, 1100, 160);
        weatherLine = new WeatherLine(weather, a.getString(R.string.weather_latitude), a.getString(R.string.weather_longitude),
                a.getString(R.string.weather_place));
        // A button too, as on v1: the first of the top row (Left from the Wi-Fi pill), OK opens the weather card.
        weatherPanel = new WeatherPanel(this, a.getString(R.string.weather_latitude), a.getString(R.string.weather_longitude),
                a.getString(R.string.weather_place));
        weatherView = weather;
        weather.setTag((Runnable) () -> weatherPanel.show());
        topViews.add(weather);
    }

    /**
     * The top bar's right end, compact: the date over the weather (two small lines, right-aligned; the weather still a
     * button: OK opens the weather card), a fine rule, then the time. No pills: plain words on the picture, the focus
     * a white chip under the weather when it is on it.
     */
    private void buildCorners() {
        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        line.setBaselineAligned(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.END);
        TextClock date = new TextClock(a);
        date.setFormat24Hour("EEE d MMM");
        date.setFormat12Hour("EEE d MMM");
        style(date, 16, semi, 0xE6FFFFFF);
        date.setSingleLine(true);
        date.setLetterSpacing(0.04f);
        date.setIncludeFontPadding(false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.rightMargin = px(8);
        col.addView(date, dlp);
        TextView weather = text(16, regular, Color.WHITE);
        weather.setLetterSpacing(0.02f);
        weather.setGravity(Gravity.CENTER_VERTICAL);
        weather.setSingleLine(true);
        weather.setIncludeFontPadding(false);
        weather.setPadding(px(8), 0, px(8), 0);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(28));
        wlp.topMargin = px(2);
        col.addView(weather, wlp);
        line.addView(col);
        View rule = new View(a);
        rule.setBackgroundColor(0x59FFFFFF);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(px(2), px(42));
        rlp.leftMargin = px(12);
        rlp.rightMargin = px(16);
        line.addView(rule, rlp);
        TextClock time = new TextClock(a);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        style(time, 44, a.getResources().getFont(R.font.inter_display_light), Color.WHITE);
        time.setLetterSpacing(-0.02f);
        time.setIncludeFontPadding(false);
        time.setFontFeatureSettings("tnum");
        line.addView(time);
        add(line, 1864 - CORNER_RIGHT_W, CORNER_Y - 32, CORNER_RIGHT_W, 64);
        // The temperature alone (the place is in the weather card).
        weatherLine = new WeatherLine(weather, a.getString(R.string.weather_latitude), a.getString(R.string.weather_longitude), "");
        weatherPanel = new WeatherPanel(this, a.getString(R.string.weather_latitude), a.getString(R.string.weather_longitude),
                a.getString(R.string.weather_place));
        weatherView = weather;
        weather.setTag((Runnable) () -> weatherPanel.show());
        topViews.add(weather);
    }

    /** Option B: the score pill's place (right of it, the Control Centre button) and its line's centre, in design pixels. */
    static final int CORNER_CC = 56, CORNER_PILL_X = 56 + CORNER_CC + 14, CORNER_PILL_R = CORNER_PILL_X + 414, CORNER_Y = 46;
    /** Option B's right end (weather, date, time), right-aligned in this width; the alerts' lane stops short of it. */
    static final int CORNER_RIGHT_W = 360, CORNER_LANE_R = 1864 - 270;

    /** Option B's dots under the score pill: one for each game, the one showing long and white. */
    private void showPageDots(int at, int of) {
        if (pageDots == null) return;
        pageDots.removeAllViews();
        if (of < 2) return;
        int shown = Math.min(of, 12);
        for (int i = 0; i < shown; i++) {
            View d = new View(a);
            boolean on = i == Math.min(at, shown - 1);
            d.setBackground(fill(3, on ? Color.WHITE : 0x66FFFFFF));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(on ? 18 : 6), px(6));
            if (i > 0) lp.leftMargin = px(6);
            pageDots.addView(d, lp);
        }
    }

    /** Option B's frosted glass for the weather pill and the Control Centre button (as the score pill's glass_frost). */
    private Drawable frost(float radius, boolean vpnOn) {
        GradientDrawable body = fill(radius, vpnOn ? 0xA63A1F7A : 0x99151B52);
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x38FFFFFF, 0x14FFFFFF});
        sheen.setCornerRadius(px(radius));
        sheen.setStroke(px(1.5f), vpnOn ? 0xB3C4A8FF : 0x61FFFFFF);
        return new android.graphics.drawable.LayerDrawable(new Drawable[]{body, sheen});
    }

    /** An alert in the score pill's place (option B): the pill, its dots and the Control Centre button make way. */
    private void coverCorner(boolean on) {
        if (scores != null) scores.cover(on);
        for (View v : new View[]{ccButton, pageDots}) {
            if (v == null) continue;
            v.animate().cancel();
            v.animate().alpha(on ? 0f : 1f).setDuration(on ? 160 : 320).setStartDelay(on ? 0 : 120).start();
        }
    }


    private WeatherPanel weatherPanel;
    private SmartPanel smartPanel;
    /** One search for everything: the magnifier in the top bar, or the remote's Search key. */
    private SearchPanel search;
    /** Your viewing: in the Control Centre. */
    private ViewingPanel viewing;
    /** The Control Centre: the one button at the top right. */
    private ControlCentre controlCentre;
    /** The Control Centre's Notifications: the main switches and one per kind (NotifyPanel). */
    private NotifyPanel notifyPanel;
    /** A title's panel: OK held on a poster. */
    private TitlePanel titlePanel;

    /** A title's details under the hero (the cast, the saga so far, My list); play is what OK on its poster does. */
    void showTitle(PagesV2.Title t, Runnable play) {
        showTitle(t, play, true);
    }

    void showTitle(PagesV2.Title t, Runnable play, boolean held) {
        if (titlePanel == null) titlePanel = new TitlePanel(this);
        titlePanel.show(t, play, held);
    }

    /** The details open under the hero: its description and the page's tabs and posters make way (and come back). */
    void heroMakeWay(boolean away) {
        if (hText != null) hText.animate().alpha(away ? 0f : 1f).setDuration(away ? 160 : 260).start();
        if (page != null) page.makeWay(away);
    }

    /** Up past the top of a page (from its details): on to the top bar, the page staying where it is. */
    void upFromPage() {
        if (page != null && !preview) leavePage(KeyEvent.KEYCODE_DPAD_UP);
    }

    /**
     * OK on a title in the panels: on one of the TV's streaming apps, Debrid Stream's page for it (as the pages do; the
     * app itself where Debrid Stream is not on this TV); on none of them, the store on the TV that sells it, else a note.
     */
    void openTitle(PagesV2.Title t) {
        io.execute(() -> {
            JSONObject gb = null;
            try {
                JSONObject r = new JSONObject(get("https://api.themoviedb.org/3/" + (t.tv ? "tv/" : "movie/") + t.id
                        + "/watch/providers?api_key=" + tmdbKey)).optJSONObject("results");
                gb = r == null ? null : r.optJSONObject("GB");
            } catch (Exception ignored) {
                // treated as on none of the apps
            }
            JSONObject g = gb;
            main.post(() -> openTitle(t, g));
        });
    }

    /** What OK on a poster does in Discover, Movies and TV shows: the app it was watched in, else Debrid Stream's page. */
    void playLikePages(PagesV2.Title t) {
        String pkg = debridPackage();
        if (t.app != null && !t.app.equals(pkg)) { openApp(t.app); return; }
        if (pkg == null) return;
        // On from Debrid Stream's page to its Play, for a title on one of the TV's streaming apps (looked up while the
        // search runs; a title on none of them stops at its page, as before).
        AppSearchService.wantPlay(t.title, t.id == 0 ? Boolean.FALSE : null);
        searchTitle(pkg, t.title, t.tv, t.id, t.backdrop != null ? t.backdrop : t.poster);
        if (t.id == 0) return;
        io.execute(() -> {
            boolean yes = onYourApps(this, PagesV2.providersGb(tmdbKey, t.id, t.tv)) != null;
            main.post(() -> AppSearchService.playDecided(t.title, yes));
        });
    }

    /**
     * A title opened where it may be watched: the licensed streaming app on this TV that carries it in the UK (its own
     * search for the title), else the store on this TV that rents or sells it, else a note. Never Debrid Stream, Kleva TV,
     * TiviMate or any IPTV app (the Episode map and Coming up open titles this way).
     */
    void openLicensed(PagesV2.Title t) {
        io.execute(() -> {
            JSONObject gb = t.id > 0 ? PagesV2.providersGb(tmdbKey, t.id, t.tv) : null;
            main.post(() -> {
                String art = t.backdrop != null ? t.backdrop : t.poster;
                String app = onYourApps(this, gb);
                if (app == null) app = storeFor(this, gb);
                if (app != null) { searchTitle(app, t.title, t.tv, t.id, art); return; }
                android.widget.Toast.makeText(a, t.title + " isn't on your streaming apps yet", android.widget.Toast.LENGTH_SHORT).show();
            });
        });
    }

    void openTitle(PagesV2.Title t, JSONObject gb) {
        String art = t.backdrop != null ? t.backdrop : t.poster;
        String app = onYourApps(this, gb);
        if (app != null) {
            String d = debridPackage();
            // On your apps: Debrid Stream's page and on to its Play (Surprise me's episode, when asked, comes first).
            if (d != null) AppSearchService.wantPlay(t.title, Boolean.TRUE);
            searchTitle(d != null ? d : app, t.title, t.tv, t.id, art);
            return;
        }
        String store = storeFor(this, gb);
        if (store != null) { searchTitle(store, t.title, t.tv, t.id, art); return; }
        android.widget.Toast.makeText(a, t.title + " isn't on your streaming apps yet", android.widget.Toast.LENGTH_SHORT).show();
    }
    private TextView weatherView;

    private void buildButtons() {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.END);
        // One button, the Control Centre (after Apple TV's): the Wi-Fi network's name and the controls icon, purple while
        // NordVPN is on; OK opens the panel with what used to be a row of buttons here.
        wifi = text(24, regular, Color.WHITE);
        wifi.setGravity(Gravity.CENTER_VERTICAL);
        wifi.setPadding(px(22), 0, px(22), 0);
        Drawable wi = a.getDrawable(R.drawable.ic_wifi), cc = a.getDrawable(R.drawable.ic_controls);
        if (wi != null) { wi = wi.mutate(); wi.setBounds(0, 0, px(30), px(30)); wi.setTint(Color.WHITE); }
        if (cc != null) { cc = cc.mutate(); cc.setBounds(0, 0, px(30), px(30)); cc.setTint(Color.WHITE); }
        wifi.setCompoundDrawables(wi, null, cc, null);
        wifi.setCompoundDrawablePadding(px(14));
        wifi.setText("Wi-Fi");
        vpn = wifi;
        List<ControlCentre.Item> items = new ArrayList<>();
        items.add(new ControlCentre.Item("Wi-Fi", R.drawable.ic_wifi, () -> wifi.getText().toString(), null,
                () -> open(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))));
        items.add(new ControlCentre.Item("Inputs", R.drawable.ic_input, () -> "HDMI and sources", null, () -> showInputs(wifi)));
        // Split screen: two apps on screen together (TiviMate beside YouTube), the dock's apps to choose from.
        items.add(new ControlCentre.Item("Split screen", R.drawable.ic_two_up, TwoUp::describe, TwoUp::active, () -> {
            controlCentre.hide();
            List<String> apps = new ArrayList<>(dock);
            apps.add("ar.tvplayer.tv");
            apps.add("com.google.android.youtube.tv");
            TwoUp.choose(this, apps);
        }));
        String vpnApp = Config.themeStrings(a, "top_bar_apps", new ArrayList<>()).isEmpty() ? ""
                : Config.themeStrings(a, "top_bar_apps", new ArrayList<>()).get(0);
        if (!vpnApp.isEmpty() && installed(vpnApp)) {
            items.add(new ControlCentre.Item("NordVPN", R.drawable.v2_nord, () -> vpnUp ? "Connected" : "Off", () -> vpnUp, () -> openApp(vpnApp)));
        }
        Intent smartHome = new Intent("com.google.android.libraries.tv.smarthome.intent.action.OPEN_SMART_HOME")
                .setPackage("com.google.android.apps.tv.dreamx").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // The smart home panel: the cameras (Tuya Smart's live view), every light (Tuya's when the TV has a Tuya project, else by Google
        // Assistant), the brightness, the car and a delivery on its way.
        if (smartHome.resolveActivity(a.getPackageManager()) != null || installed(CameraAlert.TUYA) || TuyaCloud.configured(a)) {
            smartPanel = new SmartPanel(this);
            items.add(new ControlCentre.Item("Lights & home", R.drawable.ic_bulb, () -> "Lights, cameras, the car", null, () -> smartPanel.show()));
        }
        // The other TV: a message popped up there (with a chime), or what is playing here sent over to it.
        items.add(new ControlCentre.Item("Message", R.drawable.ic_message, () -> "Type it, or a quick one", null,
                () -> TvLink.open(this, false, true)).in("THE OTHER TV"));
        items.add(new ControlCentre.Item("Send what's on", R.drawable.ic_cast, () -> TvLink.describe(a), null,
                () -> TvLink.open(this, true, false)).in("THE OTHER TV"));
        // Today's alerts: every goal, red card and full time today, what came up and what was missed, OK showing it again.
        items.add(new ControlCentre.Item("Today's alerts", R.drawable.ic_bell, AlertToday::summary, null, () -> AlertToday.open(this)));
        // Notifications: on Home and over apps on or off, and each kind (goals, the news, the doorbell) on or off.
        notifyPanel = new NotifyPanel(this);
        items.add(new ControlCentre.Item("Notifications", R.drawable.ic_bell, () -> NotifyPrefs.summary(a), null, () -> notifyPanel.show()));
        viewing = new ViewingPanel(this);
        items.add(new ControlCentre.Item("Your viewing", R.drawable.ic_chart, () -> "Your week on TV", null, () -> viewing.show()));
        search = new SearchPanel(this);
        items.add(new ControlCentre.Item("Search", R.drawable.ic_search, () -> "TV, films, sport", null, () -> search.show()));
        items.add(new ControlCentre.Item("Assistant", R.drawable.ic_mic, () -> "Ask Google", null, () -> open(new Intent(Intent.ACTION_ASSIST))));
        items.add(new ControlCentre.Item("Settings", R.drawable.ic_settings, () -> "The TV's settings", null,
                () -> open(new Intent(android.provider.Settings.ACTION_SETTINGS))));
        // The cameras at the top, when the TV has them: the first one named in the theme, OK opening the smart home panel.
        List<String> cams = CameraAlert.cameras(a);
        String cam = smartPanel == null || !installed(CameraAlert.TUYA) ? null : cams.isEmpty() ? "Cameras" : cams.get(0);
        controlCentre = new ControlCentre(this, items, cam, cam == null ? null : () -> smartPanel.show(), () -> DoorbellAlert.open(a));
        if (corners) {
            // Option B: a round button with the controls icon only (the network's name is in the Control Centre).
            ccButton = new ImageView(a);
            ccButton.setImageResource(R.drawable.ic_controls);
            ccButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int p = px(14);
            ccButton.setPadding(p, p, p, p);
            ccButton.setColorFilter(Color.WHITE);
            if (!ScoresTicker.sky) ccButton.setBackground(glass(CORNER_CC / 2f, false));
            ccButton.setTag((Runnable) () -> controlCentre.show());
            ccButton.setContentDescription("Control Centre");
            vpn = ccButton;
            // At the top left, first of the top row (Left/Right in the screen's order: it, then the weather).
            add(ccButton, 56, CORNER_Y - CORNER_CC / 2, CORNER_CC, CORNER_CC);
            topViews.add(0, ccButton);
            pageDots = new LinearLayout(a);
            pageDots.setOrientation(LinearLayout.HORIZONTAL);
            pageDots.setGravity(Gravity.CENTER);
            add(pageDots, CORNER_PILL_X, CORNER_Y + 32, CORNER_PILL_R - CORNER_PILL_X, 10);
            return;
        }
        topButton(bar, wifi, ViewGroup.LayoutParams.WRAP_CONTENT, () -> controlCentre.show());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(1000), px(66), Gravity.TOP | Gravity.END);
        lp.topMargin = px(112);
        lp.rightMargin = px(96);
        root.addView(bar, lp);
    }

    private ImageView icon(int res) {
        ImageView v = new ImageView(a);
        v.setImageResource(res);
        v.setColorFilter(Color.WHITE);
        int p = px(18);
        v.setPadding(p, p, p, p);
        return v;
    }

    private void topButton(LinearLayout bar, View v, int width, Runnable ok) {
        v.setBackground(glass(33, false));
        v.setTag(ok);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, px(66));
        lp.leftMargin = px(16);
        bar.addView(v, lp);
        topViews.add(v);
    }

    private void buildHeroText() {
        htext = new LinearLayout(a);
        htext.setOrientation(LinearLayout.VERTICAL);
        hTop = new ImageView(a);
        hTop.setAdjustViewBounds(true);
        hTop.setScaleType(ImageView.ScaleType.FIT_START);
        hTop.setMaxHeight(px(30));
        hTop.setMaxWidth(px(320));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = px(14);
        htext.addView(hTop, tlp);
        hTopText = text(16, semi, 0xFFC9A7FF);
        hTopText.setLetterSpacing(0.19f);
        LinearLayout.LayoutParams ttlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ttlp.bottomMargin = px(12);
        htext.addView(hTopText, ttlp);
        hLogo = new ImageView(a);
        hLogo.setAdjustViewBounds(true);
        hLogo.setScaleType(ImageView.ScaleType.FIT_START);
        hLogo.setMaxHeight(px(96));
        hLogo.setMaxWidth(px(420));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.bottomMargin = px(16);
        htext.addView(hLogo, llp);
        hTitle = text(50, semi, Color.WHITE);
        hTitle.setMaxLines(2);
        hTitle.setEllipsize(TextUtils.TruncateAt.END);
        hTitle.setLineSpacing(0, 1.05f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = px(10);
        htext.addView(hTitle, hlp);
        hSpecs = new LinearLayout(a);
        hSpecs.setOrientation(LinearLayout.HORIZONTAL);
        hSpecs.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(46));
        slp.bottomMargin = px(8);
        htext.addView(hSpecs, slp);
        hMeta = text(20, regular, 0xC7FFFFFF);
        hMeta.setLetterSpacing(0.03f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.bottomMargin = px(10);
        htext.addView(hMeta, mlp);
        hText = text(22, regular, 0xEBFFFFFF);
        hText.setMaxLines(3);
        hText.setEllipsize(TextUtils.TruncateAt.END);
        hText.setShadowLayer(px(10), 0, px(2), 0x73000000);
        // The description in a narrower column than the title: a long one wraps before it reaches the picture.
        htext.addView(hText, new LinearLayout.LayoutParams(px(760), ViewGroup.LayoutParams.WRAP_CONTENT));
        add(htext, dockMode ? 56 : 216, 226 - homeLift(), 1060, 340);
        // A slower TV: the hero's words kept as a finished picture, made again only when they change. Drawn live, the
        // description's soft shadow was worked out again on every frame Home drew, and while a trailer or channel plays
        // in a tile that is every frame, leaving the TCL's graphics chip little room for the glass and the row's slide.
        if (Config.themeFlag(a, "lite_home", false)) htext.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    private void buildRail() {
        List<String> apps = new ArrayList<>();
        for (String p : dock) if (installed(p)) apps.add(p);
        apps.add(null);   // all apps
        int y = 300;
        for (String p : apps) {
            FrameLayout f = new FrameLayout(a);
            f.setTag(p);
            f.setClipToOutline(true);
            f.setOutlineProvider(round(18));
            f.setBackground(fill(18, GLASS));
            ImageView art = new ImageView(a);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setAlpha(0f);
            if (p != null) {
                try {
                    Drawable icon = a.getPackageManager().getApplicationIcon(p);
                    art.setImageDrawable(fullSquare(icon));
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            f.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ImageView g = new ImageView(a);
            g.setImageResource(glyph(p));
            g.setAlpha(0.85f);
            f.addView(g, new FrameLayout.LayoutParams(px(30), px(30), Gravity.CENTER));
            if (!dockMode) add(f, 96, y, 64, 64);
            railViews.add(f);
            y += 84;
        }
        // The white bar beside the app whose tiles are showing (in the dock, a dot under it).
        View mark = new View(a);
        mark.setBackground(fill(3, Color.WHITE));
        mark.setAlpha(0f);
        mark.setTag("mark");
        if (!dockMode) add(mark, 80, 300 + 16, 5, 32);
        else markView = mark;
    }

    private View markView;

    /**
     * An app's icon filling its whole rounded square. An adaptive icon (Disney+, Prime Video) is two layers Android
     * shows through its circle mask, which left the button's glass showing in the corners; here its layers are drawn
     * edge to edge instead, the middle 72 of their 108 units filling the square, as a square-icon launcher shows them.
     * Any other icon comes back as it is.
     */
    private Drawable fullSquare(Drawable icon) {
        return fullSquare(a, icon, Math.max(96, px(DOCK_APP * 1.5f)));   // sharp at the dock's swell
    }

    /** fullSquare at n pixels square (the pop-up frame's dock uses it too). */
    static Drawable fullSquare(android.content.Context a, Drawable icon, int n) {
        if (!(icon instanceof android.graphics.drawable.AdaptiveIconDrawable)) return icon;
        android.graphics.drawable.AdaptiveIconDrawable ad = (android.graphics.drawable.AdaptiveIconDrawable) icon;
        try {
            Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(b);
            int o = n / 4;   // the layers are half as big again as what shows: a quarter off each side
            for (Drawable layer : new Drawable[]{ad.getBackground(), ad.getForeground()}) {
                if (layer == null) continue;
                layer.setBounds(-o, -o, n + o, n + o);
                layer.draw(c);
            }
            return new android.graphics.drawable.BitmapDrawable(a.getResources(), b);
        } catch (RuntimeException e) {
            return icon;
        }
    }

    /** Where the row starts inside its window: the first tile at 48 in the dock layout, 180 beside the rail. */
    private int rowInset() {
        return dockMode ? 12 : 16;
    }

    /**
     * The dock: one pane of glass along the foot, centred; the apps (their own icons, as the rail showed them when
     * focused) then a thin rule then the six sections, each its icon in its colour over its name. The liquid-glass
     * focus sits between the glass and the icons.
     */
    private void layoutDock() {
        int n = railViews.size(), sections = catViews.size();
        int width = DOCK_EDGE + n * DOCK_APP + (n - 1) * DOCK_GAP + DOCK_EDGE + 2 + DOCK_GAP + sections * DOCK_SEC + (sections - 1) * DOCK_GAP + 20;
        int x0 = (1920 - width) / 2;
        dockLayer = new FrameLayout(a);
        dockLayer.setClipChildren(false);
        root.addView(dockLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dockBg = new View(a);
        GradientDrawable g = fill(30, 0x1FFFFFFF);
        g.setStroke(px(1.5f), 0x3DFFFFFF);
        dockBg.setBackground(g);
        addDock(dockBg, x0, DOCK_Y, width, DOCK_H);
        lens = new GlassLens(a, s);
        dockLayer.addView(lens, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int x = x0 + DOCK_EDGE;
        for (FrameLayout f : railViews) {
            addDock(f, x, DOCK_Y + (DOCK_H - DOCK_APP) / 2, DOCK_APP, DOCK_APP);
            x += DOCK_APP + DOCK_GAP;
        }
        x += DOCK_EDGE - DOCK_GAP;
        dockSep = new View(a);
        dockSep.setBackgroundColor(0x47FFFFFF);
        addDock(dockSep, x, DOCK_Y + 20, 2, DOCK_H - 40);
        x += 2 + DOCK_GAP;
        for (int k = 0; k < catViews.size(); k++) {
            addDock(catViews.get(sectionAt(k)), x, DOCK_Y + 10, DOCK_SEC, DOCK_H - 20);
            x += DOCK_SEC + DOCK_GAP;
        }
        if (markView != null) {
            markView.setBackground(fill(3, Color.WHITE));
            addDock(markView, 0, DOCK_Y + DOCK_H - 11, 6, 6);
        }
        // Where the dock went: a small glass pill with the section's own icon in its colour and a down arrow, inside
        // the part of the picture every TV shows (its foot 54 above the screen's edge: at the very edge, a TV that
        // trims its picture cut it off), centred under the posters.
        dockHandle = new LinearLayout(a);
        dockHandle.setGravity(Gravity.CENTER);
        dockHandle.setContentDescription("Dock below: press Down");
        dockHandleIcon = new ImageView(a);
        dockHandleIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        dockHandle.addView(dockHandleIcon, new LinearLayout.LayoutParams(px(22), px(22)));
        dockHandleArrow = new ImageView(a);
        dockHandleArrow.setImageResource(R.drawable.ic_chevron_down);
        dockHandleArrow.setScaleType(ImageView.ScaleType.FIT_CENTER);
        dockHandleArrow.setImageAlpha(235);
        LinearLayout.LayoutParams arl = new LinearLayout.LayoutParams(px(22), px(22));
        arl.leftMargin = px(8);
        dockHandle.addView(dockHandleArrow, arl);
        GradientDrawable hg = fill(18, 0x2EFFFFFF);
        hg.setStroke(px(1.5f), 0x47FFFFFF);
        dockHandle.setBackground(hg);
        dockHandle.setAlpha(0f);
        dockHandle.setVisibility(View.INVISIBLE);
        add(dockHandle, 912, 990, 96, 36);
    }

    private void addDock(View v, int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(w), px(h));
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        dockLayer.addView(v, lp);
    }

    /**
     * The dock away while a section's posters have the focus: it drops under the screen's foot and fades (quickly, out
     * of the way), the posters grow into the room, and a small chip at the foot says where it went. Down past the last
     * row, Back, or anything that puts the focus on the dock brings it back, popping up with a little overshoot.
     */
    private void dockRoom() {
        if (!dockMode || dockLayer == null) return;
        boolean away = page != null && !preview && zone != Z_RAIL && page.growsDown()
                && Config.themeFlag(a, "dock_tuck", true);
        if (away == dockAway) return;
        dockAway = away;
        dockLayer.animate().cancel();
        Fluid.stop(dockLayer, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y);
        dockHandle.animate().cancel();
        if (away) {
            dockLayer.animate().translationY(px(170)).alpha(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f))
                    .withEndAction(() -> { if (dockAway) dockLayer.setVisibility(View.INVISIBLE); }).start();
            if (pageWhich >= 0 && pageWhich < CAT_ICONS.length) {
                dockHandleIcon.setImageResource(CAT_ICONS[pageWhich]);
                dockHandleIcon.setColorFilter(CAT_COLOURS[pageWhich]);
            }
            dockHandle.setVisibility(View.VISIBLE);
            dockHandle.setTranslationY(px(20));
            dockHandle.animate().alpha(handleHeld ? 0f : 1f).translationY(0).setStartDelay(260).setDuration(320).setInterpolator(SKY)
                    .withEndAction(() -> {
                        if (!dockAway || handleHeld) return;
                        // The arrow nods down three times, to say where the dock went, then the pill dims and rests.
                        android.animation.ObjectAnimator nod = android.animation.ObjectAnimator.ofFloat(dockHandleArrow,
                                View.TRANSLATION_Y, 0f, px(5), 0f);
                        nod.setDuration(520);
                        nod.setRepeatCount(2);
                        nod.setInterpolator(SKY);
                        nod.start();
                        dockHandle.animate().alpha(0.55f).setStartDelay(2600).setDuration(800).start();
                    }).start();
            if (page != null) page.layer.setClipBounds(null);
        } else {
            dockLayer.setVisibility(View.VISIBLE);
            // Back up on a spring, as an iPhone's dock.
            dockLayer.animate().alpha(1f).setDuration(200).setInterpolator(Fluid.EASE).start();
            Fluid.spring(dockLayer, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, 0f, 420f, 0.78f);
            dockHandle.animate().alpha(0f).translationY(px(20)).setStartDelay(0).setDuration(160)
                    .withEndAction(() -> { if (!dockAway) dockHandle.setVisibility(View.INVISIBLE); }).start();
            if (page != null && keepsBoxes(pageWhich)) clipAboveBoxes();
        }
        if (page != null) page.room(away);
    }

    /** The dock's chip held out of the way by the page (its rows scrolling past the foot, Sports). */
    private boolean handleHeld;

    /**
     * A page that scrolls rows past the foot (Sports) keeps the dock's chip out of its thumbnails' way: show false
     * while the focus is in the rows, true where Down brings the dock (the last row) or the page's top part has it.
     * Back in sight it nods once to say where the dock is.
     */
    void dockHandleHint(boolean show) {
        if (dockHandle == null) return;
        boolean held = !show;
        if (held == handleHeld) return;
        handleHeld = held;
        if (!dockAway) return;
        dockHandle.animate().cancel();
        if (held) {
            dockHandle.animate().alpha(0f).translationY(px(16)).setStartDelay(0).setDuration(180).setInterpolator(SKY).start();
            return;
        }
        dockHandle.setVisibility(View.VISIBLE);
        dockHandle.animate().alpha(1f).translationY(0).setStartDelay(0).setDuration(260).setInterpolator(SKY).withEndAction(() -> {
            if (!dockAway || handleHeld) return;
            android.animation.ObjectAnimator nod = android.animation.ObjectAnimator.ofFloat(dockHandleArrow, View.TRANSLATION_Y, 0f, px(5), 0f);
            nod.setDuration(520);
            nod.setInterpolator(SKY);
            nod.start();
            dockHandle.animate().alpha(0.55f).setStartDelay(2600).setDuration(800).start();
        }).start();
    }

    /** A dock item's place in the dock (apps first, then the sections); -1 when the focus is not in the dock. */
    private int dockFocus() {
        if (zone == Z_RAIL) return railIdx;
        if (zone == Z_CATS) return railViews.size() + dockPos(catIdx);
        return -1;
    }

    /** The dock swells under the focus: the item itself most, its neighbours a little. */
    /**
     * How far an app's icon moves aside (design px) while app `at` has the focus: its glass reaches 13.5 past the
     * swollen icon's edge, more than the gap to the next, so the neighbours slide out of its way (10, those beyond 12)
     * and the glass keeps 8 clear of every icon. Nothing moves while the focus is elsewhere.
     */
    static float dockShift(int i, int at, int apps) {
        if (at < 0 || at >= apps || i >= apps || i == at) return 0f;
        return Math.signum(i - at) * (Math.abs(i - at) == 1 ? 10f : 12f);
    }

    /** Room at the dock's left end and before its rule, for the end apps to move aside into. */
    static final int DOCK_EDGE = 28;

    private float swell(int at) {
        int f = dockFocus();
        if (f < 0) return 1f;
        int d = Math.abs(at - f);
        return d == 0 ? 1.2f : d == 1 ? 1.07f : 1f;
    }

    /** The glass to whatever has the focus: a ring round the tile, a pill behind the dock item; else away. */
    private void placeLens() {
        if (lens == null) return;
        boolean overHome = page == null && !guide.isOpen();
        if (zone == Z_ROW && overHome && rowIdx < row.getChildCount()) {
            float[] b = tileGlass();
            lens.to(b[0], b[1], b[2], b[3], b[4], true);
            return;
        }
        View item = zone == Z_RAIL && railIdx < railViews.size() ? railViews.get(railIdx)
                : zone == Z_CATS && catIdx < catViews.size() && (page == null || preview) ? catViews.get(catIdx) : null;
        if (item != null && zone == Z_RAIL) {
            // An app's icon: the glass a rounded square round it, the same margin all round, its corners following the
            // icon's, rising with it. (The sections' pill, wider than tall and left where it was as the icon rose, left
            // the icon off centre, poking out of its top over a thick band of glass below.)
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) item.getLayoutParams();
            float grow = 1.2f, size = lp.width * grow, m = px(APP_GLASS);
            float cx = lp.leftMargin + lp.width / 2f, cy = lp.topMargin + lp.height / 2f - px(APP_LIFT);
            lens.to(cx - size / 2f - m, cy - size / 2f - m, size + 2 * m, size + 2 * m, px(18) * grow + m, false);
            return;
        }
        if (item != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) item.getLayoutParams();
            float grow = 1.2f, w = lp.width * grow, cx = lp.leftMargin + lp.width / 2f;
            lens.to(cx - w / 2f - px(10), px(DOCK_Y + 8), w + px(20), px(DOCK_H - 16), px(26), false);
            return;
        }
        lens.hide();
    }

    /** The glass round the focused tile, where it rests once the row has slid (four tiles in full). */
    private float[] tileGlass() {
        int first = rowFirst();
        FrameLayout.LayoutParams vp = (FrameLayout.LayoutParams) viewport.getLayoutParams();
        float w = px(TILE_W), h = px(TILE_H), grow = 1.05f, pad = px(7);
        float x = vp.leftMargin + px(rowInset()) + (rowIdx - first) * px(TILE_W + TILE_GAP) - w * (grow - 1) / 2f;
        float y = vp.topMargin + px(24) - h * (grow - 1) / 2f;
        return new float[]{x - pad, y - pad, w * grow + 2 * pad, h * grow + 2 * pad, px(16) * grow + pad};
    }

    /**
     * The row's first tile in full. In the dock layout the row goes a page of four at a time, and the page stays put
     * while the focus is in the dock or on Match Day (back up, the same four are there). Beside the rail the focus moves
     * along to the fourth, then the row slides one tile at a time, back to the start when the focus leaves it.
     */
    private int rowFirst() {
        int i = Math.max(0, Math.min(rowIdx, row.getChildCount() - 1));
        if (dockMode) return (i / 4) * 4;
        return zone == Z_ROW ? Math.max(0, i - 3) : 0;
    }

    /** The page the row last showed, so a turn to another can be seen and given its animation; -1 before the first. */
    private int shownFirst = -1;

    /**
     * Onto another page of four: the row slides the whole page, the new page's tiles coming in one after another (a
     * short wave, each a touch behind the one before, as if drawn along), while the glass sweeps across with them to
     * the tile the focus lands on. The page before stays drawn until it has slid out of sight.
     */
    private void turnPage(int first) {
        int dir = shownFirst < 0 ? 0 : Integer.signum(first - shownFirst);
        shownFirst = first;
        for (int i = 0; i < row.getChildCount(); i++)
            row.getChildAt(i).setVisibility(i >= first - 4 && i <= first + 7 ? View.VISIBLE : View.INVISIBLE);
        row.animate().translationX(-first * px(TILE_W + TILE_GAP)).setDuration(dir == 0 ? 240 : 460).setInterpolator(SKY).start();
        if (dir == 0) return;
        for (int k = 0; k < 4 && first + k < row.getChildCount(); k++) {
            // Coming in from the right going on (from the left going back), the far end of the page furthest behind.
            int n = dir > 0 ? k : 3 - k;
            View t = row.getChildAt(first + k);
            // The focused tile keeps growing to its focus size with the rest of its arrival (cancelling its animation
            // left it at its resting size, smaller than the glass round it, which is drawn for the grown tile).
            float size = zone == Z_ROW && first + k == rowIdx ? 1.05f : 1f;
            t.setTranslationX(dir * px(70 + 45 * n));
            t.setAlpha(0.55f);
            t.animate().translationX(0).alpha(1f).scaleX(size).scaleY(size).setStartDelay(25L * n).setDuration(300).setInterpolator(SKY).start();
        }
    }

    /** The section boxes: all six on screen, lined up under the row of tiles (216 to 1824). */
    private static final int CAT_GAP = 14, CAT_W = 256, CAT_X = 216 + (1608 - 6 * CAT_W - 5 * CAT_GAP) / 2, CAT_Y = 886, CAT_H = 160;

    private void buildCats() {
        if (dockMode) { buildDockSections(); return; }
        int x = CAT_X, w = CAT_W;
        for (int i = 0; i < 6; i++) {
            FrameLayout box = new FrameLayout(a);
            box.setClipToOutline(true);
            box.setOutlineProvider(round(16));
            GradientDrawable b = fill(16, 0x14FFFFFF);
            b.setStroke(Math.max(1, px(1)), 0x24FFFFFF);
            box.setBackground(b);
            View strip = new View(a);
            strip.setBackgroundColor(CAT_COLOURS[i]);
            box.addView(strip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(5)));
            LinearLayout in = new LinearLayout(a);
            in.setOrientation(LinearLayout.VERTICAL);
            in.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(a);
            ic.setImageResource(CAT_ICONS[i]);
            ic.setColorFilter(Color.WHITE);
            in.addView(ic, new LinearLayout.LayoutParams(px(40), px(40)));
            TextView t = text(26, regular, Color.WHITE);
            t.setText(CAT_NAMES[i]);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = px(10);
            in.addView(t, tlp);
            box.addView(in, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            add(box, x, CAT_Y, w, CAT_H);
            catViews.add(box);
            x += w + CAT_GAP;
        }
    }

    /** The sections for the dock: the icon in the section's colour over its name; placed by layoutDock. */
    private void buildDockSections() {
        for (int i = 0; i < CAT_NAMES.length; i++) {
            FrameLayout box = new FrameLayout(a);
            box.setClipChildren(false);
            View strip = new View(a);
            strip.setVisibility(View.GONE);
            box.addView(strip, new FrameLayout.LayoutParams(1, 1));
            LinearLayout in = new LinearLayout(a);
            in.setOrientation(LinearLayout.VERTICAL);
            in.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(a);
            ic.setImageResource(CAT_ICONS[i]);
            ic.setColorFilter(CAT_COLOURS[i]);
            in.addView(ic, new LinearLayout.LayoutParams(px(32), px(32)));
            TextView t = text(16, regular, Color.WHITE);
            t.setText(CAT_NAMES[i]);
            t.setSingleLine(true);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = px(6);
            in.addView(t, tlp);
            box.addView(in, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            catViews.add(box);
        }
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------------

    void start() {
        weatherLine.start();
        if (scores != null) scores.start();
        updateNetwork();
    }

    void resume() {
        GoalWatchService.laneHome = true;
        main.removeCallbacks(shiftMesh);
        if (mesh && !lite) main.postDelayed(shiftMesh, MESH_EVERY_MS / 3);
        matchFirst = true;
        match.start();
        main.removeCallbacks(matchBack);
        main.postDelayed(matchBack, MATCH_BACK_MS);
        if (match.active() && zone == Z_ROW && page == null && !guide.isOpen()) { zone = Z_MATCH; matchIdx = 0; matchFirst = false; paint(); }
        // An alert on Home takes the score bar's place: the pills fade out under it and back once it has gone.
        if (scores != null) {
            if (corners) {
                coverCorner(false);
                GoalWatchService.laneListener = this::coverCorner;
            } else {
                scores.cover(false);
                GoalWatchService.laneListener = scores::cover;
            }
        }
        // An alert that came up over an app or the screensaver moves into the score bar's place.
        GoalWatchService.relane();
        if (Config.themeFlag(a, "alerts_everywhere", false)) GoalWatchService.startEverywhere(a);
        // Prayer times for the Control Centre's section: today's and tomorrow's timetable, read when not yet kept.
        if (Prayer.on(a)) io.execute(() -> Prayer.refresh(a));
        // Predictions: the other TVs' picks and points, so the leaderboard is the same on each.
        Predictions.sync(a);
        // A child's birthday: the party greets them the first time Home comes up that day (Birthday).
        main.postDelayed(() -> Birthday.maybeGreet(this), 1_200L);
        // What was watched while Home was away, matched to TMDB for Continue Watching.
        io.execute(() -> WatchHistory.match(a, tmdbKey));
        // Discover, Movies and TV shows read ahead once Home has settled (only what has gone stale is read again).
        main.removeCallbacks(warmPages);
        main.postDelayed(warmPages, lite ? 6000 : 3000);
        // Rows age: what is on now and the apps' own rows are read again when Home comes back after a while.
        Long at = rowsAt.get(mode);
        if (at != null && System.currentTimeMillis() - at > 5 * 60_000L) {
            rowsAt.remove(mode);
            rows.remove(mode);
            load(mode);
        }
    }

    /** Sport's tiles as last read: shown at once when Sport opens, and read again behind them. */
    volatile List<Tile> sportTiles;
    private volatile long sportAt;
    /** Sport's own reader: not queued behind Home's pictures and details. */
    final ExecutorService sportIo = Executors.newSingleThreadExecutor();

    private final Runnable warmPages = () -> {
        PagesV2.warm(this);
        // Sport's fixtures read ahead too (Sky's guide, the playlist, the leagues take a while): ready when it opens.
        if (sportTiles == null || System.currentTimeMillis() - sportAt > 10 * 60_000L) sportFetch();
    };

    /** sport(), kept for next time (an empty answer, before the channels are known, is not kept). */
    List<Tile> sportNow() throws Exception {
        long t0 = System.currentTimeMillis();
        List<Tile> t = sport();
        android.util.Log.i("Sport", "fixtures read in " + (System.currentTimeMillis() - t0) + " ms: " + t.size() + " tiles");
        if (!t.isEmpty()) { sportTiles = t; sportAt = System.currentTimeMillis(); }
        return t;
    }

    private java.util.concurrent.Future<List<Tile>> sportFetch;
    /** Sport's page while open: given Sky's tiles as soon as they are read, before the playlist's and the leagues'. */
    volatile java.util.function.Consumer<List<Tile>> sportEarly;

    /** The fixtures being read (one read at a time: Sport opening during the read ahead waits for that one). */
    synchronized java.util.concurrent.Future<List<Tile>> sportFetch() {
        if (sportFetch == null || sportFetch.isDone()) sportFetch = sportIo.submit(this::sportNow);
        return sportFetch;
    }

    /** Whether the fixtures kept were read in the last few minutes (Sport shows them and reads no more). */
    boolean sportFresh() {
        return sportTiles != null && System.currentTimeMillis() - sportAt < 3 * 60_000L;
    }

    void stop() {
        main.removeCallbacks(warmPages);
        main.removeCallbacks(shiftMesh);
        main.removeCallbacks(matchBack);
        if (match != null) match.stop();
        if (players != null) players.stop();
        playingTile = null;
        GoalWatchService.laneHome = false;
        // Pop-up mode's frame has its own pills for the alerts to stand in for: left to it.
        if (!PopupFrame.isShowing()) GoalWatchService.laneListener = null;
        weatherLine.stop();
        if (weatherPanel != null) weatherPanel.hide();
        if (scores != null) scores.stop();
        main.removeCallbacks(network);
    }

    void destroy() {
        io.shutdownNow();
        sportIo.shutdownNow();
        pics.shutdownNow();
        cache.evictAll();
        guide.close();
    }

    void trim() {
        cache.evictAll();
    }

    /** Home pressed: back to For you, first tile, guide closed. */
    /** The smart home panel, from adb's test hook (MainActivity). */
    void openSmartPanel() {
        if (smartPanel != null) smartPanel.show();
    }

    /** adb test (MainActivity, assist_test): the smart home panel's Assistant sign on its first light, asking the time. */
    void testAssist() {
        if (smartPanel == null) return;
        smartPanel.show();
        main.postDelayed(smartPanel::testAssist, 900);
    }

    /** Your viewing, opened on a tab (MainActivity, from the card on the 1st of the month). */
    void showViewing(int tab) {
        if (viewing != null) viewing.show(tab);
    }

    /** What the microphone heard for Search (MainActivity's answer from the speech recogniser). */
    void heard(String words) {
        if (search != null) search.heard(words);
    }

    void home() {
        // Home pressed while two apps are on screen together: their options, not Home.
        if (TwoUp.home(this)) return;
        if (controlCentre != null) controlCentre.hide();
        if (notifyPanel != null) notifyPanel.hide();
        Surprise.close();
        EpisodeDice.close();
        Recap.close();
        AlertToday.close();
        TvLink.close();
        Birthday.close();
        MonthRecap.close();
        Predictions.close();
        if (titlePanel != null) titlePanel.hide();
        if (viewing != null) viewing.hide();
        if (search != null) search.hide();
        if (smartPanel != null) smartPanel.hide();
        guide.hide();
        closePage();
        railSel = -1;
        zone = Z_ROW;
        rowIdx = 0;
        if (match != null && match.active()) { zone = Z_MATCH; matchIdx = 0; }
        load("foryou");
        paint();
    }

    /**
     * Match Day came, changed or went: Home just back with the focus on the first tile moves up onto it; gone while the
     * focus was on it, the focus goes down to the row.
     */
    /** A minute with no key pressed on Home: Match Day comes back up from its chip. */
    private static final long MATCH_BACK_MS = 60_000L;
    private final Runnable matchBack = () -> bringBackMatch(false);

    /**
     * Match Day back up from its chip by itself: after a minute with no key pressed (as Home's top comes back on other
     * TVs), and when the game kicks off. Only while the focus is on For You or the boxes with nothing open over Home;
     * otherwise the idle wait starts again.
     */
    private void bringBackMatch(boolean kickOff) {
        if (match == null || !match.active() || zone == Z_MATCH) return;
        boolean busy = page != null || guide.isOpen() || (controlCentre != null && controlCentre.isOpen())
                || (notifyPanel != null && notifyPanel.isOpen())
                || (smartPanel != null && smartPanel.isOpen()) || (weatherPanel != null && weatherPanel.isOpen())
                || (titlePanel != null && titlePanel.isOpen()) || (viewing != null && viewing.isOpen())
                || (search != null && search.isOpen()) || Surprise.isOpen() || EpisodeDice.isOpen() || Recap.isOpen()
                || AlertToday.isOpen() || TvLink.isOpen() || Birthday.isOpen() || MonthRecap.isOpen()
                || Predictions.isOpen();
        if (busy || (zone != Z_ROW && zone != Z_CATS)) {
            if (!kickOff) { main.removeCallbacks(matchBack); main.postDelayed(matchBack, MATCH_BACK_MS); }
            return;
        }
        zone = Z_MATCH;
        matchIdx = 0;
        paint();
    }

    private void matchChanged() {
        if (match.active()) {
            if (matchFirst && zone == Z_ROW && rowIdx == 0 && page == null && !guide.isOpen()) { zone = Z_MATCH; matchIdx = 0; }
            matchFirst = false;
            matchIdx = Math.max(0, Math.min(matchIdx, match.buttonCount() - 1));
        } else if (zone == Z_MATCH) {
            zone = shown.isEmpty() ? Z_RAIL : Z_ROW;
            if (rowIdx < shown.size()) hero(shown.get(rowIdx));
        }
        paint();
    }

    private void refocus() {
        paint();
    }

    // ---- keys ------------------------------------------------------------------------------------------------------

    /** All keys come here first; true when v2 used it. */
    boolean key(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            main.removeCallbacks(matchBack);
            main.postDelayed(matchBack, MATCH_BACK_MS);
        }
        // Two at once: its options (Home pressed while both are up) or the apps to choose from.
        if (TwoUp.open()) return TwoUp.key(e);
        if (controlCentre != null && controlCentre.isOpen()) return controlCentre.key(e);
        if (notifyPanel != null && notifyPanel.isOpen()) return notifyPanel.key(e);
        if (Surprise.isOpen()) return Surprise.key(e);
        if (EpisodeDice.isOpen()) return EpisodeDice.key(e);
        if (Recap.isOpen()) return Recap.key(e);
        if (AlertToday.isOpen()) return AlertToday.key(e);
        if (TvLink.isOpen()) return TvLink.key(e);
        if (Birthday.isOpen()) return Birthday.key(e);
        if (MonthRecap.isOpen()) return MonthRecap.key(e);
        if (Predictions.isOpen()) return Predictions.key(e);
        if (titlePanel != null && titlePanel.isOpen()) return titlePanel.key(e);
        if (viewing != null && viewing.isOpen()) return viewing.key(e);
        if (search != null && search.isOpen()) return search.key(e);
        if (e.getKeyCode() == KeyEvent.KEYCODE_SEARCH && search != null) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (smartPanel != null) smartPanel.hide();
                if (weatherPanel != null) weatherPanel.hide();
                search.show();
            }
            return true;
        }
        if (smartPanel != null && smartPanel.isOpen()) return smartPanel.key(e);
        if (weatherPanel != null && weatherPanel.isOpen()) return weatherPanel.key(e);
        if (guide.isOpen()) return guide.key(e);
        if (page != null && !preview && !parked) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP && !page.back()) closePage();
                return true;
            }
            int edge = e.getKeyCode();
            if (!page.key(e) && e.getAction() == KeyEvent.ACTION_DOWN) leavePage(edge);
            return true;
        }
        int code = e.getKeyCode();
        // On the score pills their own keys apply (as on v1): Left and Right along them, OK opens the game, Down back.
        if (zone == Z_SCORES && code != KeyEvent.KEYCODE_BACK) return false;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean nav = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (page != null && (preview || parked)) {
                    // Back from a section showing over the boxes: closed, and the focus up on the row, as resting on the
                    // box would only open the section again.
                    boolean onBoxes = zone == Z_CATS;
                    closePage();
                    if (onBoxes && !shown.isEmpty()) zone = Z_ROW;
                    paint();
                } else if (zone == Z_MATCH) {
                    // Back on Match Day: down to the row, Match Day staying where it is. (It used to put Match Day away
                    // until the next day, which a Back pressed out of habit did without anyone meaning it.)
                    zone = shown.isEmpty() ? Z_CATS : Z_ROW;
                    if (rowIdx < shown.size()) hero(shown.get(rowIdx));
                    paint();
                } else if (zone == Z_SCORES) {
                    View f = a.getCurrentFocus();
                    if (f != null) f.clearFocus();
                    zone = Z_TOP;
                    paint();
                } else back();
            }
            return true;
        }
        if (!ok && !nav) return false;
        // OK acts on release (as Android's buttons do), arrows on press and repeat.
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && e.getRepeatCount() == 0 && !e.isCanceled()) press();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (popUp(code, e)) return true;
        move(code);
        return true;
    }

    // ---- pop-up mode: Up twice on an app or a tile opens it inside Home's frame (PopupFrame) ----------------------

    private long popUpAt;
    private Runnable popTarget;
    private int popZone, popRail, popRow;

    /**
     * Up pressed twice on a tile: the first moves on as ever (the tile it left noted), the second puts the focus back on
     * that tile and opens it in pop-up mode. Not on the rail's apps, where Up is for going through them. Only with the
     * key helper, which keeps the remote with the app once it is open.
     */
    private boolean popUp(int code, KeyEvent e) {
        if (code != KeyEvent.KEYCODE_DPAD_UP || e.getRepeatCount() > 0 || !PopupFrame.available() || page != null) {
            if (code != KeyEvent.KEYCODE_DPAD_UP) popTarget = null;
            return false;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (popTarget != null && now - popUpAt < 450L) {
            Runnable go = popTarget;
            popTarget = null;
            zone = popZone;
            if (zone == Z_RAIL) { railIdx = popRail; selectRail(railIdx); } else rowIdx = popRow;
            paint();
            go.run();
            main.postDelayed(() -> PopupFrame.show(a), 350L);
            return true;
        }
        popTarget = null;
        if (zone == Z_ROW && rowIdx < shown.size()) popTarget = shown.get(rowIdx).ok;
        popUpAt = now;
        popZone = zone;
        popRail = railIdx;
        popRow = rowIdx;
        return false;
    }

    /** A box chosen in pop-up mode's frame: Home on that box's section (0 the TV guide). */
    void openBox(int which) {
        // Any section there is: Kids (6) only in the dock layout.
        if (which < 0 || which >= catViews.size()) return;
        guide.hide();
        if (page != null) closePage();
        zone = Z_CATS;
        catIdx = which;
        if (which == 0) guide.show(); else openPage(which, false);
        paint();
    }

    private void back() {
        if (zone != Z_ROW || railSel != -1 || !"foryou".equals(mode) || rowIdx != 0) home();
    }

    private void move(int code) {
        int n = shown.size();
        switch (zone) {
            case Z_TOP:
                // Over an open section, Down goes back into it where the focus was.
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && parked && page != null) { parked = false; zone = Z_CATS; page.entered(); break; }
                if (code == KeyEvent.KEYCODE_DPAD_UP && scores != null && scores.focus()) zone = Z_SCORES;
                // The top bar's one line (option B): the Control Centre, Right into the score bar, then the weather.
                else if (corners && code == KeyEvent.KEYCODE_DPAD_RIGHT && topIdx < topViews.size() && topViews.get(topIdx) == ccButton
                        && scores != null && scores.focus()) zone = Z_SCORES;
                else if (corners && code == KeyEvent.KEYCODE_DPAD_LEFT && topIdx < topViews.size() && topViews.get(topIdx) == weatherView
                        && scores != null && scores.focusLast()) zone = Z_SCORES;
                else if (code == KeyEvent.KEYCODE_DPAD_LEFT && topIdx > 0) topIdx--;
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && topIdx < topViews.size() - 1) topIdx++;
                else if (code == KeyEvent.KEYCODE_DPAD_DOWN && match.active() && page == null) { zone = Z_MATCH; matchIdx = 0; }
                else if (code == KeyEvent.KEYCODE_DPAD_DOWN) zone = n > 0 ? Z_ROW : Z_RAIL;
                break;
            case Z_MATCH:
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (matchIdx > 0) { matchIdx--; break; }
                    if (dockMode) break;   // no rail to the left
                    zone = Z_RAIL;
                    railIdx = Math.max(0, railSel);
                    if (railSel < 0 || railSel >= railViews.size() || railViews.get(railSel).getTag() == null) selectRail(railIdx);
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (matchIdx < match.buttonCount() - 1) matchIdx++;
                    break;
                } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    zone = Z_TOP;
                    topIdx = 0;
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    zone = n > 0 ? Z_ROW : Z_CATS;
                }
                // Off Match Day: the hero is the tile's again. The row read again: Left onto the rail has just put that
                // app's row in its place (empty on a TCL at first), and the old count crashed Home.
                if (zone != Z_MATCH && rowIdx < shown.size()) hero(shown.get(rowIdx));
                break;
            case Z_RAIL:
                if (dockMode) {
                    // Along the dock's apps: each shows its tiles as it is passed; past the last, on to the sections.
                    if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (railIdx > 0) selectRail(railIdx - 1);
                    } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (railIdx < railViews.size() - 1) selectRail(railIdx + 1);
                        else { if (appsFromRail) closePage(); zone = Z_CATS; catIdx = 0; }
                    } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                        if (appsFromRail && page != null) { enterPage(); return; }
                        int m = shown.size();
                        if (m > 0) { zone = Z_ROW; rowIdx = Math.min(rowIdx, m - 1); hero(shown.get(rowIdx)); }
                        else { zone = Z_TOP; topIdx = 0; }
                    }
                    break;
                }
                if (parked && page != null) {
                    // Beside an open section: along the rail without changing what shows; Right back into the section.
                    // Right goes back into the section; Up or Down onto another app shows that app's row in its place
                    // (YouTube's recommendations), as moving along the rail does anywhere else.
                    if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { parked = false; zone = Z_CATS; page.entered(); break; }
                    int to = code == KeyEvent.KEYCODE_DPAD_UP ? railIdx - 1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? railIdx + 1 : railIdx;
                    if (to < 0) { zone = Z_TOP; topIdx = 0; break; }
                    if (to == railIdx || to >= railViews.size()) break;
                    parked = false;
                    closePage();
                    zone = Z_RAIL;
                    selectRail(to);
                    break;
                }
                if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    if (railIdx > 0) selectRail(railIdx - 1); else { if (appsFromRail) closePage(); zone = Z_TOP; topIdx = 0; }
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (railIdx < railViews.size() - 1) selectRail(railIdx + 1); else { closePage(); zone = Z_CATS; catIdx = 0; }
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && appsFromRail && page != null) {
                    // All apps: into the Apps page showing beside it.
                    enterPage();
                    return;
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && n > 0) {
                    zone = Z_ROW;
                    rowIdx = 0;
                }
                break;
            case Z_ROW:
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (rowIdx > 0) rowIdx--;
                    else if (!dockMode) {
                        zone = Z_RAIL;
                        railIdx = Math.max(0, railSel);
                        // Back on All apps (or nothing chosen yet): what it shows comes up with it.
                        if (railSel < 0 || railSel >= railViews.size() || railViews.get(railSel).getTag() == null) selectRail(railIdx);
                    }
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (rowIdx < n - 1) rowIdx++;
                } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    // Up from the row: Match Day when there is one today, else the top bar.
                    if (match.active() && page == null) { zone = Z_MATCH; matchIdx = 0; }
                    else { zone = Z_TOP; topIdx = 0; }
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (dockMode) { downToDock(); break; }
                    zone = Z_CATS;
                    catIdx = Math.min(5, (rowIdx - Math.max(0, rowIdx - 3)) * 6 / 4);
                }
                if (zone == Z_ROW) heroSoon();
                break;
            case Z_CATS:
                if (dockMode && code == KeyEvent.KEYCODE_DPAD_DOWN) break;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (dockPos(catIdx) > 0) catIdx = sectionAt(dockPos(catIdx) - 1);
                    else { if (page != null) closePage(); zone = Z_RAIL; selectRail(railViews.size() - 1); }
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && dockPos(catIdx) < catViews.size() - 1) {
                    catIdx = sectionAt(dockPos(catIdx) + 1);
                } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    // Up from a box goes into the section showing above it; with none showing, back to the row.
                    if (page != null && preview) { enterPage(); return; }
                    if (page != null) closePage();
                    zone = n > 0 ? Z_ROW : Z_RAIL;
                }
                break;
            default:
        }
        paint();
    }

    /**
     * Down from the row in the dock layout: onto the dock item under the focused tile. An app is only pointed at, its
     * tiles not yet in the row (Left, Right or OK do that); a section shows above, as resting on it does.
     */
    private void downToDock() {
        int first = rowFirst();
        FrameLayout.LayoutParams vp = (FrameLayout.LayoutParams) viewport.getLayoutParams();
        float cx = vp.leftMargin + px(rowInset()) + (rowIdx - first) * px(TILE_W + TILE_GAP) + px(TILE_W) / 2f;
        int best = -1;
        float bestD = Float.MAX_VALUE;
        List<View> all = new ArrayList<>(railViews);
        all.addAll(catViews);
        for (int i = 0; i < all.size(); i++) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) all.get(i).getLayoutParams();
            float d = Math.abs(lp.leftMargin + lp.width / 2f - cx);
            if (d < bestD) { bestD = d; best = i; }
        }
        if (best < 0) return;
        if (best < railViews.size()) { zone = Z_RAIL; railIdx = best; }
        else { zone = Z_CATS; catIdx = best - railViews.size(); }
    }

    private void press() {
        switch (zone) {
            case Z_MATCH:
                match.press(matchIdx);
                return;
            case Z_TOP:
                if (topIdx < 0 || topIdx >= topViews.size()) return;
                Object r = topViews.get(topIdx).getTag();
                if (r instanceof Runnable) ((Runnable) r).run();
                return;
            case Z_RAIL:
                if (railIdx < 0 || railIdx >= railViews.size()) return;
                String p = (String) railViews.get(railIdx).getTag();
                if (p == null) {
                    // All apps beside a section: the Apps page in its place.
                    if (parked) { closePage(); zone = Z_RAIL; selectRail(railIdx); }
                    // In the dock, pointed at from the row: the Apps page opened now.
                    if (dockMode && page == null) selectRail(railIdx);
                    if (page != null) enterPage();
                } else openApp(p);
                return;
            case Z_ROW:
                if (rowIdx < shown.size()) {
                    View t = row.getChildAt(rowIdx);
                    if (t != null) t.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90)
                            // Back to the focus size the glass is drawn for (beside the rail, the ring is the tile's own).
                            .withEndAction(() -> t.animate().scaleX(dockMode ? 1.05f : 1.07f).scaleY(dockMode ? 1.05f : 1.07f).setDuration(140).setInterpolator(SKY).start()).start();
                    Runnable go = shown.get(rowIdx).ok;
                    if (go != null) main.postDelayed(go, 120);
                }
                return;
            case Z_CATS:
                if (catIdx == 0) {
                    if (page != null) closePage();
                    guide.show();
                    return;
                }
                // OK on a box: into its section (already showing above, or opened now).
                if (page != null && preview && pageWhich == catIdx) { enterPage(); return; }
                if (page != null) closePage();
                openPage(catIdx, false);
                return;
            default:
        }
    }

    /** Opens the page behind a box: 1 Discover, 2 Movies, 3 TV shows, 4 Sport. Home's own row makes way for it. */
    private void openPage(int which) {
        openPage(which, false);
    }

    // ---- a section showing above the boxes while they keep the focus (hovering one shows it) ------------------------

    /** The section shows above the boxes, which keep the focus: Up or OK goes into it, Left and Right on to others. */
    boolean preview;
    /** A section open with the focus up on the top buttons (or the score pills): Down goes back into it. */
    private boolean parked;
    private int pageWhich;
    private final Runnable previewNow = this::previewNow;

    /** The box the focus rests on, a moment after it got there: its section, above the boxes. */
    private void previewNow() {
        if (zone != Z_CATS || guide.isOpen()) return;
        if (page != null && pageWhich == catIdx) return;
        if (page != null) closePage();
        if (catIdx == 0) {
            // The TV guide is full screen: on hover, the channels on now in the row; OK opens the guide. Once only: each
            // paint asks for this again, and loading the row each time faded it out and in twice a second.
            if ("ar.tvplayer.tv".equals(mode) && railSel == -1) return;
            railSel = -1;
            load("ar.tvplayer.tv");
            paint();
            return;
        }
        openPage(catIdx, true);
    }

    /** Into the section showing above the boxes: it takes the keys, and the boxes make way as when it opens on OK. */
    private void enterPage() {
        if (page == null) return;
        preview = false;
        // Movies and TV shows keep the boxes under them: their rows scroll above the boxes, cut off there.
        boolean keep = keepsBoxes(pageWhich);
        if (!keep) page.layer.setClipBounds(null);
        List<View> more = new ArrayList<>();
        if (pageWhich >= 2 && !keep) more.addAll(catViews);
        for (View v : more) v.animate().alpha(0f).setDuration(180).withEndAction(() -> v.setVisibility(View.INVISIBLE)).start();
        hidden.addAll(more);
        page.entered();
        paint();
    }

    private void openPage(int which, boolean hover) {
        players.stop();
        playingTile = null;
        main.removeCallbacks(previewNow);
        pageWhich = which;
        preview = hover;
        page = which == 1 ? PagesV2.discover(this) : which == 2 ? PagesV2.movies(this) : which == 3 ? PagesV2.shows(this)
                : which == 5 ? new PagesV2.AppsPage(this) : which == KIDS ? PagesV2.kids(this) : new PagesV2.SportPage(this);
        boolean sport = which == 4 || which == 5, full = which == 2 || which == 3 || which == KIDS;
        List<View> away = new ArrayList<>(Arrays.asList(label, viewport));
        if ((full || sport) && !hover && !keepsBoxes(which)) away.addAll(catViews);
        // The rail stays beside every section: Left from a section reaches it (the section kept open under it).
        if (sport) { away.add(htext); away.add(heroA); away.add(heroB); }
        // Faded views go INVISIBLE too, so the GPU stops drawing them under the page.
        for (View v : away) if (v != null) v.animate().alpha(0f).setDuration(180).withEndAction(() -> v.setVisibility(View.INVISIBLE)).start();
        hidden = away;
        page.open();
        // Option B: the hero's words go up with the page, into the room the top bar left.
        htext.setTranslationY(-px(Math.max(0, pageLift() - homeLift())));
        if (hover || keepsBoxes(which)) clipAboveBoxes();
        paint();
    }

    /** Discover, Movies and TV shows: the boxes stay on screen under them, the page cut off just above. */
    /** Every section keeps the boxes under it (Discover, Movies, TV shows, Sport, Apps): its rows scroll above them. */
    private static boolean keepsBoxes(int which) {
        return which >= 1 && which <= KIDS;
    }

    /** A section showing while the boxes keep the focus is cut off just above them. */
    private void clipAboveBoxes() {
        if (dockAway) return;
        View first = catViews.get(0);
        int top = first.getTop() > 0 ? first.getTop() : px(CAT_Y);
        // In the page's own place: a page moved up (option B) is cut at the same line on the screen.
        page.layer.setClipBounds(new android.graphics.Rect(0, 0, Math.max(1, root.getWidth()), top - px(10) + px(pageLift())));
    }

    private List<View> hidden = new ArrayList<>();

    /**
     * Past a page's edge: the page closes and the focus goes on, as if Home were under it: Down to the boxes (on this
     * page's own), Left to the rail, Up to the top buttons. Another box then opens with OK, no Back needed.
     */
    private void leavePage(int code) {
        // The dock layout has nothing to the left of a page: Left at its edge stays.
        if (dockMode && code == KeyEvent.KEYCODE_DPAD_LEFT) return;
        int to = code == KeyEvent.KEYCODE_DPAD_DOWN ? Z_CATS : code == KeyEvent.KEYCODE_DPAD_LEFT ? Z_RAIL
                : code == KeyEvent.KEYCODE_DPAD_UP ? Z_TOP : -1;
        if (to < 0) return;
        if (to == Z_CATS && page != null) {
            // Down out of a section: back to the boxes, the section still showing above them.
            for (View v : catViews) {
                v.animate().cancel();
                v.setVisibility(View.VISIBLE);
                v.animate().alpha(1f).setDuration(200).start();
            }
            hidden.removeAll(catViews);
            preview = true;
            clipAboveBoxes();
            zone = Z_CATS;
            catIdx = pageWhich;
            paint();
            return;
        }
        if (to == Z_TOP && page != null) {
            // Up out of a section: on to the weather and the top buttons, the section staying where it is.
            parked = true;
            zone = Z_TOP;
            topIdx = 0;
            paint();
            return;
        }
        if (to == Z_RAIL && appsFromRail && page != null) {
            // Left out of the Apps page opened from the rail: back on All apps, the page still showing beside it.
            for (View v : catViews) {
                v.animate().cancel();
                v.setVisibility(View.VISIBLE);
                v.animate().alpha(1f).setDuration(200).start();
            }
            hidden.removeAll(catViews);
            preview = true;
            clipAboveBoxes();
            zone = Z_RAIL;
            railIdx = railViews.size() - 1;
            paint();
            return;
        }
        if (to == Z_RAIL && page != null) {
            // Left out of a section: onto the rail beside it, the section staying; Right goes back in, OK opens an app.
            parked = true;
            zone = Z_RAIL;
            railIdx = Math.max(0, Math.min(railViews.size() - 1, railSel >= 0 ? railSel : 0));
            paint();
            return;
        }
        closePage();
        zone = to;
        paint();
    }

    private void closePage() {
        if (page == null) return;
        // A page's hold on the dock's chip ends with it.
        handleHeld = false;
        preview = false;
        parked = false;
        appsFromRail = false;
        main.removeCallbacks(previewNow);
        page.close();
        page = null;
        htext.setTranslationY(0);
        heroTopColour(0xFFC9A7FF);
        for (View v : hidden) {
            if (v == null) continue;
            v.animate().cancel();
            v.setVisibility(View.VISIBLE);
            if (v == heroA || v == heroB) continue;
            v.animate().alpha(1f).setDuration(220).start();
        }
        hidden = new ArrayList<>();
        heroShown = null;
        if (rowIdx < shown.size()) hero(shown.get(rowIdx));
        paint();
    }

    /** The colour of the words above a hero's title (each page's own: Movies pink, TV shows blue). */
    void heroTopColour(int colour) {
        hTopText.setTextColor(colour);
    }

    /** The Apps page is showing because the rail's All apps has the focus (not the Apps box). */
    private boolean appsFromRail;

    private void selectRail(int i) {
        // The rail fills in a moment after Home starts: a key in that moment has no app to land on (it crashed).
        if (i < 0 || i >= railViews.size()) return;
        railIdx = i;
        railSel = i;
        String p = (String) railViews.get(i).getTag();
        if (p == null) {
            // All apps: the same Apps page as the Apps box, showing as when the box is hovered; Right or OK goes in.
            if (page != null && pageWhich == 5) { appsFromRail = true; return; }
            closePage();
            openPage(5, true);
            clipAboveBoxes();
            appsFromRail = true;
            return;
        }
        closePage();
        // The dock layout: another app's tiles start at their first page.
        if (dockMode) rowIdx = 0;
        load(p);
    }

    // ---- drawing the focus -----------------------------------------------------------------------------------------

    /**
     * Whether v is to be painted in a look (state) other than the one it has: true once, then false until it changes.
     * A key press restyles only the few views whose look changes; restyling all of them (new backgrounds, fonts that
     * lay the text out again, dozens of animations re-run) made every press cost a slow frame or two.
     */
    static boolean restyle(View v, int state) {
        Object was = v.getTag(R.id.v2_state);
        if (was instanceof Integer && (Integer) was == state) return false;
        v.setTag(R.id.v2_state, state);
        return true;
    }

    void paint() {
        // The background's light in Match Day's clubs' colours while it has the focus.
        if (mesh && zone == Z_MATCH && page == null && match != null && match.active()) adaptMeshClubs(match.shownHome, match.shownAway);
        dockRoom();
        // A score pill keeps Android's focus (and its light) after OK took the viewer to the game; once Home's own
        // focus is elsewhere, the pill lets it go, so only one thing is ever lit.
        if (zone != Z_SCORES && scores != null && scores.hasFocus()) root.requestFocus();
        if (match != null) {
            boolean onMatch = zone == Z_MATCH && match.active();
            match.paint(onMatch, matchIdx, page == null && !guide.isOpen());
            if (onMatch) {
                main.removeCallbacks(heroLater);
                htext.animate().cancel();
                htext.setAlpha(0f);
                heroPicture(match.pick().picture);
            }
        }
        for (int i = 0; dockMode && i < railViews.size(); i++) {
            // The dock: every app in its own colours, swelling as the glass comes near.
            FrameLayout f = railViews.get(i);
            boolean on = zone == Z_RAIL && i == railIdx;
            float sw = swell(i);
            if (!restyle(f, (on ? 1 : 0) | Math.round(sw * 100) << 2)) continue;
            boolean app = f.getTag() != null;
            f.getChildAt(0).setAlpha(app ? 1f : 0f);
            f.getChildAt(1).setAlpha(app ? 0f : 0.9f);
            f.setForeground(null);
            if (!app) f.setBackground(fill(18, GLASS));
            float aside = px(dockShift(i, zone == Z_RAIL ? railIdx : -1, railViews.size()));
            f.animate().scaleX(sw).scaleY(sw).translationX(aside).translationY(on ? -px(APP_LIFT) : 0).setDuration(260).setInterpolator(SKY).start();
        }
        for (int i = 0; !dockMode && i < railViews.size(); i++) {
            FrameLayout f = railViews.get(i);
            boolean on = zone == Z_RAIL && i == railIdx, sel = i == railSel;
            boolean colour = (on || sel) && f.getTag() != null;
            if (!restyle(f, (on ? 1 : 0) | (sel ? 2 : 0) | (colour ? 4 : 0))) continue;
            f.getChildAt(0).animate().alpha(colour ? 1f : 0f).setDuration(150).start();
            f.getChildAt(1).animate().alpha(colour ? 0f : 0.85f).setDuration(150).start();
            f.animate().scaleX(on ? 1.12f : 1f).scaleY(on ? 1.12f : 1f).setDuration(150).setInterpolator(SKY).start();
            f.setForeground(on ? ring(18) : null);
            if (f.getTag() == null) f.setBackground(fill(18, on || sel ? 0x4DFFFFFF : GLASS));
        }
        View mark = dockMode ? markView : root.findViewWithTag("mark");
        if (dockMode && mark != null) {
            // The dot under the app whose tiles are showing.
            boolean show = railSel >= 0 && railSel < railViews.size() && zone != Z_RAIL;
            mark.animate().alpha(show ? 1f : 0f).setDuration(150).start();
            if (railSel >= 0 && railSel < railViews.size()) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) railViews.get(railSel).getLayoutParams();
                mark.setTranslationX(lp.leftMargin + lp.width / 2f - px(3));
            }
        } else if (mark != null) {
            boolean show = railSel >= 0 && zone != Z_RAIL;
            mark.animate().alpha(show ? 1f : 0f).setDuration(150).start();
            if (railSel >= 0) mark.animate().translationY(px(84) * railSel).setDuration(200).setInterpolator(SKY).start();
        }
        for (int i = 0; i < row.getChildCount(); i++) {
            View t = row.getChildAt(i);
            boolean on = zone == Z_ROW && i == rowIdx;
            if (!restyle(t, on ? 1 : 0)) continue;
            // A little less than the sections' 1.07: the tiles fill the row edge to edge, and the fourth stays inside it.
            // No start delay left over from a page turn's wave: the focus grows at once.
            t.animate().scaleX(on ? 1.05f : 1f).scaleY(on ? 1.05f : 1f).setStartDelay(0).setDuration(150).setInterpolator(SKY).start();
            // Lifted off the page when focused: on a spring (no shadow under it).
            Fluid.lift(t, on, px(22), 0);
            // The dock layout's ring is the liquid glass, drawn over the row.
            ((FrameLayout) t).setForeground(on && !dockMode ? ring(16) : null);
        }
        if (dockMode) placeLens();
        // Four tiles in full. The dock layout: a page of four at a time. Beside the rail: the focus moves along to the
        // fourth, then the row slides one at a time.
        int first = rowFirst();
        if (dockMode) {
            if (first != shownFirst) turnPage(first);
        } else {
            window(row, first);
            row.animate().translationX(-first * px(TILE_W + TILE_GAP)).setDuration(240).setInterpolator(SKY).start();
        }
        playSoon();
        for (int i = 0; dockMode && i < catViews.size(); i++) {
            // A section in the dock: white and a little bolder under the glass, swelling with it.
            FrameLayout box = (FrameLayout) catViews.get(i);
            boolean on = zone == Z_CATS && i == catIdx && (page == null || preview);
            boolean sel = page != null && i == pageWhich;
            float sw = swell(railViews.size() + dockPos(i));
            if (!restyle(box, (on ? 1 : 0) | (sel ? 2 : 0) | Math.round(sw * 100) << 2)) continue;
            LinearLayout in = (LinearLayout) box.getChildAt(1);
            ((TextView) in.getChildAt(1)).setTypeface(on || sel ? semi : regular);
            ((ImageView) in.getChildAt(0)).setColorFilter(on ? Color.WHITE : CAT_COLOURS[i]);
            box.animate().scaleX(sw).scaleY(sw).translationY(on ? -px(4) : 0).setDuration(260).setInterpolator(SKY).start();
        }
        for (int i = 0; !dockMode && i < catViews.size(); i++) {
            FrameLayout box = (FrameLayout) catViews.get(i);
            boolean on = zone == Z_CATS && i == catIdx && (page == null || preview);
            // The box whose section is showing (Apps, too, when the rail's All apps opened it).
            boolean sel = page != null ? i == pageWhich : railSel < 0 && i > 0 && CAT_MODES[i].equals(mode);
            if (!restyle(box, (on ? 1 : 0) | (sel ? 2 : 0))) continue;
            GradientDrawable b = fill(16, on ? 0xEBFFFFFF : sel ? (CAT_COLOURS[i] & 0x00FFFFFF) | 0x73000000 : 0x14FFFFFF);
            b.setStroke(Math.max(1, px(1)), on ? 0xFFFFFFFF : 0x24FFFFFF);
            box.setBackground(b);
            LinearLayout in = (LinearLayout) box.getChildAt(1);
            ((ImageView) in.getChildAt(0)).setColorFilter(on ? 0xFF1A1D24 : Color.WHITE);
            ((TextView) in.getChildAt(1)).setTextColor(on ? 0xFF1A1D24 : Color.WHITE);
            ((TextView) in.getChildAt(1)).setTypeface(on ? semi : regular);
            box.animate().scaleX(on ? 1.04f : 1f).scaleY(on ? 1.04f : 1f).setDuration(150).setInterpolator(SKY).start();
        }
        // Resting on a box shows its section after a moment, no OK needed.
        main.removeCallbacks(previewNow);
        boolean guideUp = catIdx == 0 && page == null && "ar.tvplayer.tv".equals(mode) && railSel == -1;
        if (zone == Z_CATS && (page == null || preview) && !(page != null && pageWhich == catIdx) && !guideUp) main.postDelayed(previewNow, 420);
        for (int i = 0; i < topViews.size(); i++) {
            View v = topViews.get(i);
            boolean on = zone == Z_TOP && i == topIdx;
            boolean vpnOn = v == vpn && vpnUp;
            if (!restyle(v, (on ? 1 : 0) | (vpnOn ? 2 : 0))) continue;
            float rad = v == weatherView ? (ScoresTicker.sky ? 14 : corners ? 19 : 25) : v == ccButton ? CORNER_CC / 2f : 33;
            GradientDrawable b = fill(rad, on ? 0xEBFFFFFF : vpnOn ? 0x3D9B5CFF : GLASS);
            b.setStroke(px(1.5f), on ? Color.WHITE : vpnOn ? 0xB3C4A8FF : GLASS_LINE);
            // Option B's pills frosted, as the score pill: a tinted body under the glass, the words clear on any picture.
            // The Sky top bar: no pills, the words and the icon plain on the picture until focused (a white chip then).
            v.setBackground(!on && ScoresTicker.sky ? (vpnOn ? fill(rad, 0x3D9B5CFF) : null)
                    : !on && ScoresTicker.frost ? frost(rad, vpnOn) : b);
            int ink = on ? 0xFF1A1D24 : Color.WHITE;
            if (v instanceof ImageView) {
                if (v == vpn) ((ImageView) v).setColorFilter(on ? 0xFF1A1D24 : vpnOn ? 0xFFC9A2FF : Color.WHITE);
                else ((ImageView) v).setColorFilter(ink);
            } else if (v instanceof TextView) {
                ((TextView) v).setTextColor(ink);
                // The weather's icon keeps its own colours, darkened only on the white of the focus.
                for (Drawable d : ((TextView) v).getCompoundDrawables()) {
                    if (d == null) continue;
                    // Its own copy: the same icon drawn elsewhere (the weather card's hours) keeps its colours.
                    d = d.mutate();
                    if (v == weatherView && !on) d.setTintList(null); else d.setTint(ink);
                }
            }
        }
    }

    // ---- the focused tile plays: a film's or series' trailer, or the channel live, with sound ----------------------

    TilePlayer players;
    private final Map<Integer, String> trailers = new HashMap<>();
    private View playingTile;
    private final Runnable playNow = this::playNow;

    private void playSoon() {
        View t = zone == Z_ROW && page == null && rowIdx < row.getChildCount() ? row.getChildAt(rowIdx) : null;
        if (t == playingTile) return;
        playingTile = t;
        main.removeCallbacks(playNow);
        players.stop();
        if (t != null) main.postDelayed(playNow, lite ? 800 : 500);
    }

    private void playNow() {
        View v = playingTile;
        if (v == null || rowIdx >= shown.size() || !(v instanceof FrameLayout)) return;
        Tile t = shown.get(rowIdx);
        FrameLayout tile = (FrameLayout) v;
        if (t.stream != null && !t.stream.isEmpty() && !Config.themeString(a, "iptv_server", "").isEmpty()) {
            players.live(tile, 1, Config.themeString(a, "iptv_server", "") + "/live/" + Config.themeString(a, "iptv_user", "") + "/"
                    + Config.themeString(a, "iptv_pass", "") + "/" + previewStream(t), tile.findViewWithTag("pic"),
                    t.channel != null && t.channel.matches("(?i).*\\b(uhd|4k|hdr)\\b.*") && previewStream(t).equals(t.stream));
            return;
        }
        if (t.tmdbId == 0 || tmdbKey.isEmpty()) return;
        String have = trailers.get(t.tmdbId);
        if (have != null) { if (!have.isEmpty()) players.trailer(tile, 0, have, tile.findViewWithTag("pic")); return; }
        PagesV2.Title q = new PagesV2.Title();
        q.id = t.tmdbId;
        q.tv = t.tv;
        io.execute(() -> {
            String id = PagesV2.trailer(tmdbKey, q);
            main.post(() -> {
                trailers.put(t.tmdbId, id);
                if (playingTile == tile && !id.isEmpty()) players.trailer(tile, 0, id, tile.findViewWithTag("pic"));
            });
        });
    }

    /**
     * Only the tiles in view, and one either side for the slide, are drawn: a long row's others are left out, so the
     * GPU has less to do each frame.
     */
    static void window(LinearLayout strip, int first) {
        for (int i = 0; i < strip.getChildCount(); i++)
            strip.getChildAt(i).setVisibility(i >= first - 1 && i <= first + 5 ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * The focus outline: a white line drawn just inside the tile's rounded edge, so the clip that rounds the tile never
     * cuts it, the whole outline showing evenly all round.
     */
    Drawable ring(int r) {
        int w = Math.max(2, px(3));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Math.max(0, px(r) - w / 2f));
        g.setStroke(w, Color.WHITE);
        return new android.graphics.drawable.InsetDrawable(g, (w + 1) / 2);
    }

    /** A page's hero description: as many lines as the page has room for above its posters (two above its tabs). */
    private int heroLines = 2;

    void heroLines(int lines) {
        if (lines == heroLines && page != null && hText.getMaxLines() == lines) return;
        heroLines = lines;
        if (page != null) hText.setMaxLines(lines);
    }

    // ---- the row ---------------------------------------------------------------------------------------------------

    private void load(String m) {
        // The row already showing this, as it is: nothing to do (filling it again fades it out and in).
        List<Tile> already = rows.get(m);
        if (m.equals(mode) && already != null && already == shown && row.getChildCount() > 0) return;
        mode = m;
        int gen = ++generation;
        String railPkg = railSel >= 0 && railSel < railViews.size() ? (String) railViews.get(railSel).getTag() : null;
        label.setText(labelFor(m, railPkg));
        List<Tile> cached = rows.get(m);
        if (cached != null) {
            fill(cached, gen);
            return;
        }
        fill(new ArrayList<>(), gen);
        io.execute(() -> {
            List<Tile> got;
            try {
                got = read(m);
            } catch (Exception e) {
                got = new ArrayList<>();
            }
            List<Tile> done = got;
            main.post(() -> {
                rows.put(m, done);
                rowsAt.put(m, System.currentTimeMillis());
                if (gen == generation) fill(done, gen);
            });
            // Title logos for the films and series, after the row is up.
            for (Tile t : done) {
                if (tmdbKey.isEmpty()) continue;
                // A film or series from an app's own row (Netflix's, Prime Video's) has no TMDB id: found by its name,
                // so it has its logo too. Not channels, apps, sport or YouTube's videos.
                if (t.tmdbId == 0) {
                    boolean title = t.stream == null && t.icon == null && t.group == null && t.title != null
                            && !m.contains("youtube") && !m.equals("ar.tvplayer.tv");
                    int[] found = title ? findTitle(t.title, t.tv) : null;
                    if (found == null) continue;
                    t.tmdbId = found[0];
                    t.tv = found[1] == 1;
                }
                String logo = tmdbLogo(t.tmdbId, t.tv);
                if (logo == null) continue;
                t.logo = logo;
                t.heroLogo = logo;
                main.post(() -> { if (gen == generation) refreshTile(t); });
            }
        });
    }

    private String labelFor(String m, String railPkg) {
        switch (m) {
            case "foryou": return "FOR YOU";
            case "discover": return "DISCOVER · TRENDING TODAY";
            case "movies": return "MOVIES · NEW AND POPULAR";
            case "shows": return "TV SHOWS · NEW AND POPULAR";
            case "sport": return "LIVE SPORT · THIS WEEK";
            case "apps": return "ALL APPS";
            default: {
                // The guide's hover shows the live row with no rail tile focused: the mode is the package.
                String pkg = railPkg != null ? railPkg : m;
                return railLabel(pkg, appLabel(pkg));
            }
        }
    }

    private void fill(List<Tile> tiles, int gen) {
        shown = tiles;
        if (rowIdx >= tiles.size()) rowIdx = Math.max(0, tiles.size() - 1);
        row.animate().alpha(0f).setDuration(80).withEndAction(() -> {
            if (gen != generation) return;
            row.removeAllViews();
            for (Tile t : tiles) row.addView(tileView(t));
            ViewGroup.LayoutParams rl = row.getLayoutParams();
            rl.width = Math.max(4, tiles.size()) * px(TILE_W + TILE_GAP);
            row.setLayoutParams(rl);
            if (tiles.isEmpty()) for (int i = 0; i < 4; i++) row.addView(emptyTile());
            row.setTranslationX(0);
            row.animate().alpha(1f).setDuration(140).start();
            paint();
            if (!tiles.isEmpty()) hero(tiles.get(Math.min(rowIdx, tiles.size() - 1)));
        }).start();
    }

    private View emptyTile() {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(fill(16, 0x10FFFFFF));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(TILE_W), px(TILE_H));
        lp.rightMargin = px(TILE_GAP);
        f.setLayoutParams(lp);
        return f;
    }

    /**
     * Home's tiles: four across from beside the rail (180) to near the screen's edge (1896), much wider than the
     * sections' (420, not 384, the gaps 12) and taller (270, not 216), reaching most of the way down to the boxes;
     * Right past the fourth slides the row on.
     */
    /** A tile's size: four to a page (wider and taller in the dock layout, with no rail beside them). */
    int TILE_W = 420, TILE_H = 270;
    static final int TILE_GAP = 12;

    private View tileView(Tile t) {
        FrameLayout f = new FrameLayout(a);
        // lite_home: the tile as one finished picture (its picture, shade, logo and words painted once).
        if (lite) f.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        f.setClipToOutline(true);
        f.setOutlineProvider(round(16));
        f.setBackground(fill(16, t.icon != null ? GLASS : 0xFF222433));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(TILE_W), px(TILE_H));
        lp.rightMargin = px(TILE_GAP);
        f.setLayoutParams(lp);
        f.setTag(t);
        if (t.icon != null) {
            // An app: its icon and name on glass.
            LinearLayout in = new LinearLayout(a);
            in.setOrientation(LinearLayout.VERTICAL);
            in.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(a);
            ic.setImageDrawable(t.icon);
            ic.setClipToOutline(true);
            ic.setOutlineProvider(round(22));
            in.addView(ic, new LinearLayout.LayoutParams(px(96), px(96)));
            TextView n = text(22, regular, Color.WHITE);
            n.setText(t.title);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = px(14);
            in.addView(n, nlp);
            f.addView(in, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return f;
        }
        ImageView pic = new ImageView(a);
        pic.setTag("pic");   // found by name: a trailer slides in under it
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (t.video) {
            // A video's thumbnail whole (16:9 in a taller tile): over a soft blur of itself, not black, above and below.
            ImageView blur = new ImageView(a);
            blur.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(blur, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            pic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            f.addView(pic, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            image(pic, t.image, px(TILE_W), () -> {
                Drawable d = pic.getDrawable();
                if (d instanceof android.graphics.drawable.BitmapDrawable) {
                    Bitmap b = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
                    Bitmap tiny = Bitmap.createScaledBitmap(b, 24, Math.max(2, 24 * b.getHeight() / Math.max(1, b.getWidth())), true);
                    blur.setImageBitmap(tiny);
                    blur.setColorFilter(0x66000000, android.graphics.PorterDuff.Mode.SRC_ATOP);
                }
            });
        } else {
            f.addView(pic, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            image(pic, t.image, px(TILE_W));
        }
        View shade = new View(a);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xD9000000}));
        f.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout cap = new LinearLayout(a);
        cap.setOrientation(LinearLayout.VERTICAL);
        cap.setTag("cap");
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        clp.leftMargin = px(18);
        clp.rightMargin = px(14);
        clp.bottomMargin = px(14);
        f.addView(cap, clp);
        fillCaption(cap, t);
        // A video's tile is its thumbnail alone (the title is in the hero above): no words over it.
        if (t.video) {
            cap.setVisibility(View.GONE);
            shade.setVisibility(View.GONE);
        }
        // A film's or series' tile is its picture alone (and its trailer): no words over it.
        if (t.tmdbId != 0 && t.stream == null) {
            cap.setVisibility(View.GONE);
            shade.setVisibility(View.GONE);
            titleLogo(f, t.tmdbId, t.tv, 230, 64);
        }
        if (t.chip != null) {
            TextView chip = text(16, semi, Color.WHITE);
            chip.setText(t.chip);
            chip.setLetterSpacing(0.05f);
            chip.setPadding(px(10), px(2), px(10), px(1));
            chip.setBackground(fill(8, 0xB3000000));
            FrameLayout.LayoutParams ch = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
            ch.topMargin = px(12);
            ch.rightMargin = px(12);
            f.addView(chip, ch);
        }
        if (false) {
            ImageView b = new ImageView(a);
            b.setAdjustViewBounds(true);
            b.setMaxHeight(px(30));
            b.setMaxWidth(px(64));
            b.setBackground(fill(9, Color.WHITE));
            b.setPadding(px(6), px(4), px(6), px(4));
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(40), Gravity.TOP | Gravity.START);
            bl.topMargin = px(10);
            bl.leftMargin = px(12);
            f.addView(b, bl);
            image(b, t.badge, px(64));
        }
        if (t.progress >= 0) {
            FrameLayout bar = new FrameLayout(a);
            bar.setBackgroundColor(0x40FFFFFF);
            View done = new View(a);
            done.setBackgroundColor(t.progressColour);
            bar.addView(done, new FrameLayout.LayoutParams(Math.round(px(TILE_W) * Math.min(1f, t.progress)), ViewGroup.LayoutParams.MATCH_PARENT));
            f.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(5), Gravity.BOTTOM));
        }
        return f;
    }

    private void fillCaption(LinearLayout cap, Tile t) {
        cap.removeAllViews();
        if (t.badge != null) {
            ImageView ch = new ImageView(a);
            ch.setAdjustViewBounds(true);
            ch.setMaxHeight(px(22));
            ch.setMaxWidth(px(200));
            ch.setScaleType(ImageView.ScaleType.FIT_START);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(22));
            clp.bottomMargin = px(8);
            cap.addView(ch, clp);
            image(ch, t.badge, px(200));
        }
        if (t.logo != null) {
            ImageView l = new ImageView(a);
            l.setAdjustViewBounds(true);
            l.setMaxHeight(px(t.sub == null ? 62 : 26));
            l.setMaxWidth(px(t.sub == null ? 250 : 160));
            l.setScaleType(ImageView.ScaleType.FIT_START);
            cap.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            image(l, t.logo, px(250));
        }
        if (t.title != null && (t.logo == null || t.sub != null)) {
            TextView n = text(23, semi, Color.WHITE);
            n.setText(t.title);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            n.setShadowLayer(px(6), 0, px(1), 0x99000000);
            cap.addView(n);
        }
        if (t.sub != null) {
            TextView sub = text(17, regular, 0xD9FFFFFF);
            sub.setText(t.sub);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            cap.addView(sub);
        }
    }

    private void refreshTile(Tile t) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v.getTag() != t) continue;
            View cap = v.findViewWithTag("cap");
            if (cap instanceof LinearLayout) fillCaption((LinearLayout) cap, t);
            if (zone == Z_ROW && i == rowIdx || zone != Z_ROW && i == 0) hero(t);
        }
    }

    private void heroSoon() {
        main.removeCallbacks(heroLater);
        main.postDelayed(heroLater, lite ? 420 : 240);
    }

    /** The picture and the words above the row for a tile: the picture crossfades, the words swap after a beat. */
    /** Each hero's number: a picture's late arrival for an older one does nothing. */
    private int heroGen;

    void hero(Tile t) {
        // Match Day holds the top while the focus is on it: the tile's picture and words wait for the focus.
        if (zone == Z_MATCH && match != null && match.active()) { heroTile = t; return; }
        String img = t.heroImage != null ? t.heroImage : t.image;
        heroPicture(img);
        htextFor(t);
    }

    /** The hero's picture, cross-faded in (null: none). */
    void heroPicture(String img) {
        if (img != null && !img.equals(heroShown)) {
            // A new picture: any older one still on its way is not to be faded in when it arrives.
            int gen = ++heroGen;
            heroShown = img;
            ImageView in = heroFrontA ? heroB : heroA, out = heroFrontA ? heroA : heroB;
            heroFrontA = !heroFrontA;
            heroImage(in, img, () -> {
                // A picture that arrives after the hero has moved on (a section's, back on the TV guide) stays hidden.
                if (gen != heroGen) return;
                in.animate().alpha(1f).setDuration(lite ? 200 : 320).setInterpolator(SKY).start();
                out.animate().alpha(0f).setDuration(lite ? 200 : 320).setInterpolator(SKY).start();
            });
        } else if (img == null) {
            heroGen++;
            heroShown = null;
            heroA.animate().alpha(0f).setDuration(300).start();
            heroB.animate().alpha(0f).setDuration(300).start();
        }
    }

    private void htextFor(Tile t) {
        // The words fade as one cached picture (withLayer), not redrawn line by line each frame of the fade.
        htext.animate().alpha(0f).setDuration(90).withLayer().withEndAction(() -> {
            hTop.setImageDrawable(null);
            hTop.setVisibility(t.heroTop != null && t.heroTop.startsWith("http") ? View.VISIBLE : View.GONE);
            if (hTop.getVisibility() == View.VISIBLE) image(hTop, t.heroTop, px(320));
            hTopText.setVisibility(t.heroTop != null && !t.heroTop.startsWith("http") ? View.VISIBLE : View.GONE);
            hTopText.setText(t.heroTop);
            String logo = t.heroLogo;
            hLogo.setImageDrawable(null);
            hLogo.setVisibility(logo != null ? View.VISIBLE : View.GONE);
            if (logo != null) image(hLogo, logo, px(420));
            String title = t.heroTitle != null ? t.heroTitle : t.title;
            hTitle.setVisibility(logo == null && title != null ? View.VISIBLE : View.GONE);
            hTitle.setText(title);
            hMeta.setText(t.heroMeta);
            hMeta.setVisibility(t.heroMeta == null ? View.GONE : View.VISIBLE);
            heroTile = t;
            specs(t);
            // "Press OK to open": the remote's OK key, not the word.
            hText.setText(t.heroText == null ? null : Keys.icons(a, t.heroText, hText.getTextSize(), true));
            hText.setVisibility(t.heroText == null || t.heroText.isEmpty() ? View.GONE : View.VISIBLE);
            htext.animate().alpha(1f).setDuration(140).withLayer().start();
        }).start();
    }

    // ---- the line of marks under the hero's title ------------------------------------------------------------------

    private final Map<Integer, String[]> details = new HashMap<>();

    /** Fills the line under the title; a film's or series' running time and service are looked up the first time. */
    private void specs(Tile t) {
        // A film or series always has its picture, HDR and sound marks: the store's where it gave them, else the
        // service's usual ones (filled in once TMDB has said where it streams).
        String[] have = t.tmdbId != 0 ? details.get(t.tmdbId) : null;
        if (have != null) apply(t, have);
        hSpecs.removeAllViews();
        if (t.duration != null) {
            TextView d = text(20, semi, 0xEBFFFFFF);
            d.setText(t.duration);
            hSpecs.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        // Dolby Vision with Atmos: Dolby's one "Dolby Vision · Atmos" badge in the HDR mark's place, not two.
        List<String> marks = new ArrayList<>(t.specs);
        if (marks.contains("DOLBY VISION") && marks.contains("DOLBY ATMOS")) {
            marks.set(marks.indexOf("DOLBY VISION"), "DOLBY VISION ATMOS");
            marks.remove("DOLBY ATMOS");
        }
        // Subtitles and audio description first, then picture quality and sound, the HDR formats, any other marks
        // (the service's logo last).
        marks.sort(java.util.Comparator.comparingInt(m -> m.equals("S") || m.equals("AD") ? -1 : QUALITY.contains(m) ? 0
                : SOUND.contains(m) || m.equals("DOLBY VISION ATMOS") ? 1 : HDR.contains(m) ? 2 : 3));
        for (String m : marks) hSpecs.addView(mark(m), gapped(hSpecs.getChildCount() == 0 ? 0 : 10));
        int wordmark = serviceLogo(t.serviceId);
        Drawable banner = wordmark != 0 ? null : serviceBanner(t.serviceId);
        if (wordmark != 0) {
            // The service's own logo on its own, no box: Netflix's red, iPlayer's pink, the others white.
            ImageView l = new ImageView(a);
            l.setImageResource(wordmark);
            l.setAdjustViewBounds(true);
            l.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Drawable d = l.getDrawable();
            boolean wide = d.getIntrinsicWidth() > 2.5f * d.getIntrinsicHeight();
            l.setMaxHeight(px(wide ? 28 : 46));
            l.setMaxWidth(px(150));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = hSpecs.getChildCount() == 0 ? 0 : px(16);
            hSpecs.addView(l, lp);
        } else if (banner != null) {
            // The service's app as the TV shows it (its wide banner, Netflix's or Prime Video's own): clear at a
            // glance, at the marks' height.
            ImageView l = new ImageView(a);
            l.setScaleType(ImageView.ScaleType.CENTER_CROP);
            l.setImageDrawable(banner);
            l.setClipToOutline(true);
            l.setOutlineProvider(round(7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(80), px(45));
            lp.leftMargin = hSpecs.getChildCount() == 0 ? 0 : px(14);
            hSpecs.addView(l, lp);
        } else if (t.service != null) {
            ImageView l = new ImageView(a);
            l.setScaleType(ImageView.ScaleType.FIT_CENTER);
            l.setBackground(fill(7, 0x33FFFFFF));
            l.setClipToOutline(true);
            l.setOutlineProvider(round(7));
            // The service's own logo, clear at the marks' height and no bigger.
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(44), px(44));
            lp.leftMargin = hSpecs.getChildCount() == 0 ? 0 : px(14);
            hSpecs.addView(l, lp);
            image(l, t.service, px(92));
        }
        hSpecs.setVisibility(hSpecs.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        // As much of the description as fits above the row: three lines on Home, five under a page's hero (its rows
        // start lower down); with the marks' line taking room on Home, two.
        hText.setMaxLines(page != null ? heroLines : hSpecs.getChildCount() > 0 ? 2 : 3);
        if (t.tmdbId != 0 && have == null && !tmdbKey.isEmpty()) {
            int id = t.tmdbId;
            boolean tv = t.tv;
            io.execute(() -> {
                String[] got = tmdbDetails(id, tv);
                main.post(() -> {
                    details.put(id, got);
                    if (heroTile == t) specs(t);
                });
            });
        }
    }

    /** TMDB's UK watch providers and the apps that play them on this TV. */
    private static final Map<String, String> SERVICE_APPS = new HashMap<>();
    static {
        String[][] apps = {{"8", "com.netflix.ninja"}, {"9", "com.amazon.amazonvideo.livingroom"}, {"119", "com.amazon.amazonvideo.livingroom"},
                {"337", "com.disney.disneyplus"}, {"350", "com.apple.atve.androidtv.appletv"}, {"2", "com.apple.atve.androidtv.appletv"},
                {"38", "bbc.iplayer.android"}, {"41", "air.ITVMobilePlayer"}, {"54", "air.ITVMobilePlayer"}, {"103", "com.channel4.ondemand"},
                {"333", "com.channel5.my5"}, {"39", "com.nowtv.androidtv"}, {"29", "com.bskyb.skygo"}, {"73", "com.tubitv"},
                {"531", "com.cbs.ca"}, {"1899", "com.wbd.stream"}};
        for (String[] x : apps) SERVICE_APPS.put(x[0], x[1]);
    }
    private final Map<String, Drawable> banners = new HashMap<>();

    /** The app on this TV for a TMDB provider id, or null (Search). */
    static String serviceApp(String provider) {
        return SERVICE_APPS.get(provider);
    }

    /** TMDB's UK streaming services whose apps are on this TV, as discover's with_watch_providers takes them ("8|9|337"). */
    static String installedProviders(HomeV2 h) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : SERVICE_APPS.entrySet()) {
            if ("2".equals(e.getKey())) continue;   // Apple's store, not a subscription
            if (h.installed(e.getValue())) b.append(b.length() > 0 ? "|" : "").append(e.getKey());
        }
        return b.toString();
    }

    /** The app on this TV that streams a title in the UK (TMDB's "flatrate" providers for it, gb), or null. */
    static String onYourApps(HomeV2 h, JSONObject gb) {
        JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
        for (int i = 0; flat != null && i < flat.length(); i++) {
            JSONObject p = flat.optJSONObject(i);
            String app = p == null ? null : SERVICE_APPS.get(String.valueOf(p.optInt("provider_id")));
            if (app != null && h.installed(app)) return app;
        }
        return null;
    }

    /** The store on this TV that rents or sells a title in the UK (Prime Video, Apple TV, YouTube, Google TV), or null. */
    static String storeFor(HomeV2 h, JSONObject gb) {
        String[][] stores = {{"10", "com.amazon.amazonvideo.livingroom"}, {"2", "com.apple.atve.androidtv.appletv"},
                {"192", "com.google.android.youtube.tv"}, {"3", "com.google.android.videos"}};
        for (String kind : new String[]{"rent", "buy"}) {
            JSONArray list = gb == null ? null : gb.optJSONArray(kind);
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject p = list.optJSONObject(i);
                String id = p == null ? "" : String.valueOf(p.optInt("provider_id"));
                for (String[] s : stores) if (s[0].equals(id) && h.installed(s[1])) return s[1];
            }
        }
        return null;
    }

    /** The services' own logos, transparent, shipped with the launcher (TMDB's providers by id); 0 for the others. */
    static int serviceLogo(String provider) {
        if (provider == null) return 0;
        switch (provider) {
            case "8": return R.drawable.svc_netflix;
            case "9": case "119": return R.drawable.svc_prime;
            case "337": return R.drawable.svc_disney;
            case "350": case "2": return R.drawable.svc_appletv;
            case "38": return R.drawable.svc_iplayer;
            case "41": case "54": return R.drawable.svc_itvx;
            case "103": return R.drawable.svc_c4;
            case "333": return R.drawable.svc_my5;
            case "29": return R.drawable.svc_sky;
            case "531": case "582": return R.drawable.svc_paramount;
            default: return 0;
        }
    }

    /** The service's app's TV banner, when the app is on this TV; null otherwise (TMDB's logo stands in). */
    private Drawable serviceBanner(String provider) {
        if (provider == null) return null;
        if (banners.containsKey(provider)) return banners.get(provider);
        Drawable got = null;
        String pkg = SERVICE_APPS.get(provider);
        if (pkg != null) {
            PackageManager pm = a.getPackageManager();
            try {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(pkg);
                List<android.content.pm.ResolveInfo> ri = pm.queryIntentActivities(i, 0);
                if (!ri.isEmpty()) got = ri.get(0).activityInfo.loadBanner(pm);
                if (got == null) got = pm.getApplicationBanner(pkg);
            } catch (Exception ignored) {
            }
        }
        banners.put(provider, got);
        return got;
    }

    private static void apply(Tile t, String[] d) {
        if (d[0] != null) t.duration = d[0];
        if (t.service == null && d[1] != null) t.service = d[1];
        if (t.serviceId == null && d[2] != null) t.serviceId = d[2];
        fillSpecs(t, d[2], d[3]);
    }

    private static final java.util.Set<String> QUALITY = new java.util.HashSet<>(Arrays.asList("4K UHD", "4K", "UHD", "HD", "SD"));
    private static final java.util.Set<String> HDR = new java.util.HashSet<>(Arrays.asList("DOLBY VISION", "HDR10+", "HDR10", "HDR"));
    private static final java.util.Set<String> SOUND = new java.util.HashSet<>(Arrays.asList("DOLBY ATMOS", "DOLBY", "5.1"));

    /**
     * The picture, HDR and sound marks a title lacks, as its service streams it in Britain (TMDB's provider id):
     * Apple TV+, Netflix and Disney+ in 4K with Dolby Vision and Atmos, Prime Video in 4K with HDR10+ and 5.1, NOW
     * in HD with 5.1; elsewhere 4K and HDR10 for anything from 2015 on, HD before. The store's own marks win.
     */
    private static void fillSpecs(Tile t, String provider, String year) {
        boolean hasQ = false, hasH = false, hasS = false;
        for (String m : t.specs) { hasQ |= QUALITY.contains(m); hasH |= HDR.contains(m); hasS |= SOUND.contains(m); }
        if (hasQ && hasH && hasS && t.specs.contains("HDR10")) return;
        int y = 0;
        try { y = Integer.parseInt(year); } catch (Exception ignored) { }
        String q, h, snd;
        switch (provider == null ? "" : provider) {
            case "350": case "8": case "337": q = "4K UHD"; h = "DOLBY VISION"; snd = "DOLBY ATMOS"; break;
            case "9": q = "4K UHD"; h = "HDR10+"; snd = "5.1"; break;
            case "39": case "29": q = "HD"; h = "HDR10"; snd = "5.1"; break;
            default: q = y >= 2015 || y == 0 ? "4K UHD" : "HD"; h = "HDR10"; snd = "5.1";
        }
        List<String> out = new ArrayList<>();
        out.add(hasQ ? first(t.specs, QUALITY) : q);
        // Every HDR format it comes in: Dolby Vision and HDR10+ streams carry HDR10 underneath, so HDR10's logo
        // shows beside theirs (Dolby Vision, HDR10+, HDR10).
        String hdr = hasH ? first(t.specs, HDR) : h;
        out.add(hdr);
        if (!hdr.equals("HDR10")) out.add("HDR10");
        out.add(hasS ? first(t.specs, SOUND) : snd);
        for (String m : t.specs) if (!QUALITY.contains(m) && !HDR.contains(m) && !SOUND.contains(m)) out.add(m);
        t.specs.clear();
        t.specs.addAll(out);
    }

    private static String first(List<String> specs, java.util.Set<String> of) {
        for (String m : specs) if (of.contains(m)) return m;
        return null;
    }

    /** {running time ("2h 9m", "55 mins"), the UK streaming service's logo}; from TMDB, in one call. */
    private String[] tmdbDetails(int id, boolean tv) {
        String[] out = new String[4];
        try {
            JSONObject o = new JSONObject(get("https://api.themoviedb.org/3/" + (tv ? "tv/" : "movie/") + id
                    + "?append_to_response=watch/providers&api_key=" + tmdbKey));
            int mins = o.optInt("runtime", 0);
            if (tv) {
                JSONArray r = o.optJSONArray("episode_run_time");
                if (r != null && r.length() > 0) mins = r.optInt(0);
                JSONObject last = o.optJSONObject("last_episode_to_air");
                if (mins == 0 && last != null) mins = last.optInt("runtime", 0);
            }
            if (mins > 0) out[0] = mins >= 90 && !tv ? (mins / 60) + "h " + (mins % 60) + "m" : mins + " mins";
            JSONObject gb = o.optJSONObject("watch/providers") == null ? null
                    : o.getJSONObject("watch/providers").optJSONObject("results") == null ? null
                    : o.getJSONObject("watch/providers").getJSONObject("results").optJSONObject("GB");
            JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
            if (flat != null && flat.length() > 0) {
                out[1] = "https://image.tmdb.org/t/p/w92" + flat.getJSONObject(0).optString("logo_path");
                out[2] = String.valueOf(flat.getJSONObject(0).optInt("provider_id"));
            }
            String date = o.optString(tv ? "first_air_date" : "release_date", "");
            if (date.length() >= 4) out[3] = date.substring(0, 4);
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Sky's marks for a programme from its guide entry, and its running time. */
    static void skySpecs(Tile t, JSONObject e, String channel) {
        if (e == null) return;
        long mins = e.optLong("d") / 60;
        if (mins > 0) t.duration = mins >= 120 ? (mins / 60) + "h " + (mins % 60 == 0 ? "" : (mins % 60) + "m") : mins + " mins";
        boolean uhd = channel != null && channel.matches("(?i).*\\b(uhd|4k)\\b.*");
        if (uhd) t.specs.add("UHD");
        else if (e.optBoolean("hd")) t.specs.add("HD");
        if (e.optString("at").contains("D")) t.specs.add("DOLBY");
        if (e.optBoolean("s")) t.specs.add("S");
        if (e.optBoolean("ad")) t.specs.add("AD");
        String r = e.optString("r");
        if (!r.isEmpty() && !r.equals("--")) t.specs.add("age:" + r);
    }

    private LinearLayout.LayoutParams gapped(int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(28));
        lp.leftMargin = px(gap);
        return lp;
    }

    /**
     * One mark, drawn as Sky draws "HD": letters in a thin white outline. Subtitles' "S" is filled white; an age rating
     * is a filled square in the BBFC's colour.
     */
    /** The marks' boxes: one height for the worded ones (4K UHD, 5.1) and the logos (HDR10, Dolby Vision) alike. */
    private static final int MARK_H = 28;
    /** A mark that is Because you watched's lilac pill ("because:Because you watched Lupin"), not a format. */
    static final String BECAUSE = "because:";

    private View mark(String m) {
        // The formats with logos of their own (Dolby's, HDR10's): the logo in white, inside the same outlined box as
        // 4K UHD and 5.1, at the same height. Dolby Vision with Atmos is Dolby's one stacked badge, a little taller
        // inside its box.
        int logo = m.equals("DOLBY VISION ATMOS") ? R.drawable.fmt_dolby_vision_atmos
                : m.equals("DOLBY VISION") ? R.drawable.fmt_dolby_vision : m.equals("DOLBY ATMOS") ? R.drawable.fmt_dolby_atmos
                : m.equals("HDR10+") ? R.drawable.fmt_hdr10plus : m.equals("HDR10") ? R.drawable.fmt_hdr10
                : m.equals("DOLBY") ? R.drawable.fmt_dolby : 0;
        if (logo != 0) {
            FrameLayout box = new FrameLayout(a);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(4));
            g.setStroke(Math.max(1, px(1.5f)), 0xD9FFFFFF);
            box.setBackground(g);
            box.setPadding(px(8), 0, px(8), 0);
            box.setMinimumHeight(px(MARK_H));
            ImageView v = new ImageView(a);
            v.setImageResource(logo);
            v.setAdjustViewBounds(true);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int inner = m.equals("DOLBY VISION ATMOS") ? 22 : m.startsWith("HDR") ? 17 : 15;
            v.setMaxHeight(px(inner));
            box.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(inner), Gravity.CENTER));
            return box;
        }
        if (m.startsWith(BECAUSE)) {
            // Because you watched: why it is here, in the page's lilac, a round-again arrow before the words.
            TextView v = text(16, semi, 0xFFEFE4FF);
            v.setText(m.substring(BECAUSE.length()));
            v.setGravity(Gravity.CENTER_VERTICAL);
            v.setSingleLine(true);
            v.setPadding(px(12), 0, px(14), 0);
            v.setHeight(px(MARK_H + 4));
            Drawable icon = WatchCards.PathIcon.again(0xFFEFE4FF);
            icon.setBounds(0, 0, px(16), px(16));
            v.setCompoundDrawables(icon, null, null, null);
            v.setCompoundDrawablePadding(px(8));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px((MARK_H + 4) / 2f));
            g.setColor(0x38C9A7FF);
            g.setStroke(Math.max(1, px(1.5f)), 0xB3C9A7FF);
            v.setBackground(g);
            return v;
        }
        boolean age = m.startsWith("age:");
        String label = age ? m.substring(4) : m;
        TextView v = text(age ? 16 : 15, semi, Color.WHITE);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setLetterSpacing(age ? 0f : 0.04f);
        v.setPadding(px(age ? 7 : 8), 0, px(age ? 7 : 8), 0);
        v.setMinWidth(px(28));
        v.setHeight(px(MARK_H));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(age ? 6 : 4));
        if (age) {
            String u = label.toUpperCase(Locale.ROOT);
            int c = u.equals("U") ? 0xFF2BA84A : u.equals("PG") ? 0xFFF2B705 : u.startsWith("12") ? 0xFFF07F1A
                    : u.equals("15") ? 0xFFE6007E : u.equals("18") ? 0xFFDA291C : 0xFF6B7280;
            g.setColor(c);
        } else if (m.equals("S")) {
            g.setColor(0xF2FFFFFF);
            v.setTextColor(0xFF1A1D24);
        } else {
            g.setStroke(Math.max(1, px(1.5f)), 0xD9FFFFFF);
        }
        v.setBackground(g);
        return v;
    }

    // ---- reading the rows (off the main thread) --------------------------------------------------------------------

    private List<Tile> read(String m) throws Exception {
        switch (m) {
            case "foryou": {
                List<Tile> out = new ArrayList<>();
                List<Tile> live = liveNow(true);
                for (int i = 0; i < Math.min(2, live.size()); i++) out.add(live.get(i));
                if (!tmdbKey.isEmpty()) for (Trending.Title t : Trending.trending(tmdbKey)) {
                    if (out.size() >= 12) break;
                    out.add(title(t, null));
                }
                return out;
            }
            case "discover": return titles(Trending.trending(tmdbKey), null);
            case "movies": return titles(filter(Trending.newReleases(tmdbKey, "GB"), false), null);
            case "shows": return titles(filter(Trending.newReleases(tmdbKey, "GB"), true), null);
            case "sport": return sport();
            case "apps": return apps();
            default: return app(m);
        }
    }

    private static List<Trending.Title> filter(List<Trending.Title> in, boolean tv) {
        List<Trending.Title> out = new ArrayList<>();
        for (Trending.Title t : in) if (t.tv == tv) out.add(t);
        return out;
    }

    private List<Tile> titles(List<Trending.Title> in, String pkg) {
        List<Tile> out = new ArrayList<>();
        for (Trending.Title t : in) out.add(title(t, pkg));
        return out;
    }

    /** A film or series from TMDB: OK looks it up in the app it belongs to (Debrid Stream when none). */
    private Tile title(Trending.Title src, String pkg) {
        Tile t = new Tile();
        t.title = src.title;
        t.image = src.backdrop != null ? src.backdrop : src.image;
        t.heroImage = src.backdrop != null ? src.backdrop.replace("/w780/", "/w1280/") : src.image;
        t.tmdbId = src.tmdbId;
        t.tv = src.tv;
        String what = src.tv ? "Series" : "Film";
        t.heroMeta = join(" · ", src.year, src.genre, what);
        t.heroText = src.summary;
        // The store's own marks, where it gave them (v1's: 4K, Dolby Vision, HDR10+, Atmos, the age rating).
        if (src.quality != null) t.specs.add(src.quality.equals("4K") ? "4K UHD" : src.quality);
        if (src.dolbyVision) t.specs.add("DOLBY VISION");
        else if (src.hdr10plus) t.specs.add("HDR10+");
        else if (src.hdr) t.specs.add("HDR10");
        if (src.atmos) t.specs.add("DOLBY ATMOS");
        if (src.age != null) t.specs.add("age:" + src.age);
        t.service = src.service;
        // Part-watched in any app: the bar along the tile's foot, as on the posters.
        Float done = WatchHistory.progress(a).get(src.tmdbId);
        if (done != null) { t.progress = done; t.progressColour = 0xFFE50914; }
        t.heroTop = pkg == null ? (src.pill != null ? "NEW RELEASE" : null) : null;
        String target = pkg != null ? pkg : debridPackage();
        t.ok = () -> {
            if (target != null) {
                searchTitle(target, src.title, src.tv, src.tmdbId, t.heroImage);
                GoalWatchService.onAppOpened(a, target);
            }
        };
        return t;
    }

    String debridPackage() {
        for (String p : dock) if (p.contains("debrid") && installed(p)) return p;
        return null;
    }

    /** One dock app's tiles: what it publishes to Android's TV rows, else what is popular on it from TMDB. */
    private List<Tile> app(String pkg) {
        if (pkg.equals("ar.tvplayer.tv")) return liveNow(false);
        if (pkg.contains("debrid")) return titles(Trending.newReleases(tmdbKey, "GB"), null);
        List<Tile> out = new ArrayList<>();
        for (String[] r : published(pkg)) {
            Tile t = new Tile();
            t.title = r[0];
            t.image = r[1];
            if (pkg.contains("youtube") && r[1] != null) {
                // YouTube's thumbnail without the black bars its usual one has: see fetch.
                t.video = true;
                t.image = r[1] + "#yt";
            }
            t.heroImage = t.image;
            t.heroTop = appLabel(pkg).toUpperCase(Locale.ROOT);
            t.heroTitle = r[0];
            t.heroMeta = r[3];
            if (r[4] != null) {
                t.progress = Float.parseFloat(r[4]);
                t.progressColour = pkg.contains("youtube") ? 0xFFFF0033 : pkg.contains("netflix") ? 0xFFE50914 : Color.WHITE;
            }
            String uri = r[2];
            t.ok = () -> {
                try {
                    Intent i = uri == null ? null : Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                    if (i == null) { openApp(pkg); return; }
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(i);
                    GoalWatchService.onAppOpened(a, pkg);
                } catch (Exception e) {
                    openApp(pkg);
                }
            };
            out.add(t);
        }
        // An app that publishes little (Netflix shows only what is being watched) is topped up with what is popular on it.
        if (out.size() >= 8 || tmdbKey.isEmpty()) return out;
        int provider = pkg.contains("netflix") ? 8 : pkg.contains("disney") ? 337 : pkg.contains("amazon") ? 9 : 0;
        if (provider == 0) return out;
        Trending.Category c = new Trending.Category(appLabel(pkg), false, false, 0);
        c.provider = provider;
        out.addAll(titles(Trending.service(tmdbKey, "GB", c), pkg));
        return out;
    }

    /** {title, picture, intent, line, progress} from TvContract's Continue watching and preview rows. */
    private List<String[]> published(String pkg) {
        List<String[]> out = new ArrayList<>();
        ContentResolver cr = a.getContentResolver();
        String[][] tables = {{"content://android.media.tv/watch_next_program", "1"}, {"content://android.media.tv/preview_program", "0"}};
        for (String[] table : tables) {
            try (Cursor c = cr.query(Uri.parse(table[0]), null, null, null, null)) {
                if (c == null) continue;
                int iPkg = c.getColumnIndex("package_name"), iTitle = c.getColumnIndex("title"), iPoster = c.getColumnIndex("poster_art_uri"),
                        iThumb = c.getColumnIndex("thumbnail_uri"), iIntent = c.getColumnIndex("intent_uri"), iPos = c.getColumnIndex("last_playback_position_millis"),
                        iDur = c.getColumnIndex("duration_millis"), iEp = c.getColumnIndex("episode_title"), iBrowsable = c.getColumnIndex("browsable");
                while (c.moveToNext() && out.size() < 16) {
                    if (!pkg.equals(str(c, iPkg))) continue;
                    if (iBrowsable >= 0 && !c.isNull(iBrowsable) && c.getInt(iBrowsable) == 0) continue;
                    String thumb = str(c, iThumb), poster = str(c, iPoster);
                    String pic = thumb != null ? thumb : poster;
                    String line = "1".equals(table[1]) ? "Continue watching" : "Recommended";
                    String ep = str(c, iEp);
                    String progress = null;
                    if ("1".equals(table[1]) && iPos >= 0 && iDur >= 0 && !c.isNull(iPos) && !c.isNull(iDur) && c.getLong(iDur) > 0) {
                        long pos = c.getLong(iPos), dur = c.getLong(iDur);
                        progress = String.valueOf(Math.min(1f, pos / (float) dur));
                        line += " · " + Math.max(1, Math.round((dur - pos) / 60_000.0)) + " min left";
                    }
                    if (ep != null && !ep.isEmpty()) line = ep + " · " + line;
                    out.add(new String[]{str(c, iTitle), pic, str(c, iIntent), line, progress});
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static String str(Cursor c, int i) {
        return i >= 0 && !c.isNull(i) ? c.getString(i) : null;
    }

    /** What is on now on the playlist's channels, sport first: OK puts TiviMate on that channel. */
    /** Live TV's first five, in this order: the main channels, as on any British guide. */
    private static final String[] MAIN_FIVE = {"(?i)^bbc one\\b.*", "(?i)^bbc two\\b.*", "(?i)^itv ?1\\b.*", "(?i)^channel 4\\b.*", "(?i)^(channel )?5( (hd|uhd))?$"};

    private List<Tile> liveNow(boolean sportOnly) {
        List<String[]> order = new ArrayList<>();
        if (!sportOnly) {
            // BBC One, BBC Two, ITV1, Channel 4 and 5 first, then the sports channels, then the news.
            for (String re : MAIN_FIVE)
                for (String[] c : channels) if (c[1].matches(re)) { order.add(c); break; }
        }
        for (String[] c : channels) if (c[2].contains("Sport") && !order.contains(c)) order.add(c);
        if (!sportOnly) for (String[] c : channels) if (c[2].contains("News") && !order.contains(c) && order.size() < 36) order.add(c);
        if (order.isEmpty()) return new ArrayList<>();
        String[] ids = new String[order.size()];
        Map<String, String> names = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            ids[i] = order.get(i)[0] + "|" + order.get(i)[1];
            names.put(order.get(i)[1], order.get(i)[1]);
        }
        List<Tile> out = new ArrayList<>();
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
        long now = System.currentTimeMillis();
        // Sky's guide answers for at most 20 channels a time.
        List<UkTv.Now> now_ = new ArrayList<>();
        for (int from = 0; from < ids.length; from += 20)
            now_.addAll(UkTv.now(Arrays.copyOfRange(ids, from, Math.min(ids.length, from + 20))));
        for (UkTv.Now n : now_) {
            if (sportOnly && !n.title.toLowerCase(Locale.ROOT).startsWith("live")) continue;
            Tile t = new Tile();
            t.title = n.title.replaceFirst("^(?i)live:? ", "");
            t.image = n.image;
            t.heroImage = n.image == null ? null : n.image.replace("/16-9/640", "/16-9/1280");
            t.sub = "On now · " + hm.format(Instant.ofEpochMilli(n.start)) + " – " + hm.format(Instant.ofEpochMilli(n.end));
            t.badge = n.logo;
            t.heroTop = n.logo;
            t.heroTitle = t.title;
            long left = Math.max(1, (n.end - now) / 60_000L);
            t.heroMeta = n.channel + " · " + left + " min left" + (n.next != null ? " · Next: " + n.next : "");
            t.heroText = n.synopsis;
            t.progress = Math.max(0f, Math.min(1f, (now - n.start) / (float) Math.max(1, n.end - n.start)));
            String channel = n.channel;
            String logoUrl = n.logo != null && channel != null && channel.matches("(?i).*\\b(uhd|4k)\\b.*") ? n.logo + "#uhd" : n.logo;
            t.badge = logoUrl;
            t.heroTop = logoUrl;
            t.stream = streamOf(channel);
            t.channel = channel;
            skySpecs(t, n.raw, channel);
            t.ok = () -> tune(channel, logoUrl);
            out.add(t);
        }
        return out;
    }

    /** Live sport over the next three days on the playlist's sports channels, from Sky's guide. */
    List<Tile> sport() throws Exception {
        List<String[]> sports = new ArrayList<>();
        for (String[] c : channels) if (c[2].contains("Sport")) sports.add(c);
        StringBuilder ids = new StringBuilder();
        Map<String, String> name = new HashMap<>();
        for (String[] c : sports) {
            if (ids.length() > 0) ids.append(',');
            ids.append(c[0]);
            name.put(c[0], c[1]);
        }
        List<Tile> out = new ArrayList<>();
        if (ids.length() == 0) return out;
        long now = System.currentTimeMillis(), t0 = now;
        // Everything read side by side: Sky's three days, the playlist (and its clubs' crests), the leagues' fixtures.
        // One after another they took most of a minute.
        ExecutorService ahead = Executors.newFixedThreadPool(5);
        java.util.concurrent.Future<?> playlist = ahead.submit(() -> PlaylistSport.prepare(this));
        java.util.concurrent.Future<?> leagues = ahead.submit(PlaylistSport::prepareFixtures);
        List<java.util.concurrent.Future<String>> days = new ArrayList<>();
        for (int day = 0; day < 3; day++) {
            String date = Instant.ofEpochMilli(now).plusSeconds(day * 86_400L).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
            days.add(ahead.submit(() -> get("https://awk.epgsky.com/hawk/linear/schedule/" + date + "/" + ids)));
        }
        List<Object[]> events = new ArrayList<>();
        // One listing per programme: the same one on a channel and its 4K twin (Main Event and Main Event UHD) is
        // kept on the 4K one.
        Map<String, Integer> seen = new HashMap<>();
        for (java.util.concurrent.Future<String> day : days) {
            JSONArray sch;
            try {
                sch = new JSONObject(day.get(30, java.util.concurrent.TimeUnit.SECONDS)).optJSONArray("schedule");
            } catch (Exception e) {
                continue;
            }
            for (int i = 0; sch != null && i < sch.length(); i++) {
                JSONObject s = sch.getJSONObject(i);
                JSONArray ev = s.optJSONArray("events");
                for (int k = 0; ev != null && k < ev.length(); k++) {
                    JSONObject e = ev.getJSONObject(k);
                    String title = e.optString("t");
                    long st = e.optLong("st") * 1000L, en = st + e.optLong("d") * 1000L;
                    if (!title.toLowerCase(Locale.ROOT).startsWith("live") || en < now) continue;
                    if (title.matches("(?i)live:? (sky sports fc|the premier league show|ref watch|transfer).*")) continue;
                    Object[] row = {st, en, title, e.optString("sy"), e.optString("programmeuuid"), s.optString("sid"), e};
                    Integer at = seen.get(title + st);
                    if (at == null) { seen.put(title + st, events.size()); events.add(row); }
                    else if (uhdName(name.get(s.optString("sid"))) && !uhdName(name.get((String) events.get(at)[5]))) events.set(at, row);
                }
            }
        }
        events.sort((x, y) -> Long.compare((Long) x[0], (Long) y[0]));
        DateTimeFormatter when = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK).withZone(ZoneId.systemDefault());
        for (Object[] e : events) {
            if (out.size() >= 80) break;
            long st = (Long) e[0], en = (Long) e[1];
            String sid = (String) e[5], uuid = (String) e[4];
            Tile t = new Tile();
            String raw = (String) e[2];
            t.title = raw.replaceFirst("^(?i)live:? ", "").replaceFirst("^(EFL|PL|SNF|MNF|B'Liga|SPFL|Serie A|La Liga|WSL|UCL|UEL|FA Cup|Carabao Cup|LaLiga): ", "")
                    .replace("Utd", "United").replace("C. Palace", "Crystal Palace").replace("B. Munich", "Bayern Munich");
            String[] comp = competition(raw);
            t.group = comp[0];
            // "X v Y" is not always football: cricket, tennis and the rest on their own channels or by their own words.
            String chan = name.get(sid);
            if ("football".equals(t.group) && (chan != null && chan.matches("(?i).*\\b(cricket|tennis|golf|f1|racing|darts|nfl|nba)\\b.*")
                    || raw.matches("(?i).*\\b(wta|atp|tri series|test match|t20|odi|ipl|bbl|nfl|nba|nhl|mlb|darts|snooker|golf|rugby|super league|netball|boxing|ufc)\\b.*"))) {
                t.group = "other";
            }
            t.compLogo = comp[1];
            t.image = uuid.isEmpty() ? null : "https://images.metadata.sky.com/pd-image/" + uuid + "/16-9/640";
            t.heroImage = uuid.isEmpty() ? null : "https://images.metadata.sky.com/pd-image/" + uuid + "/16-9/1280";
            String logo = ScoresTicker.skyLogo(sid, name.get(sid));
            boolean on = st <= now;
            t.chip = on ? "LIVE NOW" : when.format(Instant.ofEpochMilli(st)).toUpperCase(Locale.UK);
            t.sub = "Live on " + name.get(sid);
            t.badge = logo;
            t.heroTop = logo;
            t.heroTitle = t.title;
            t.heroMeta = (on ? "On now" : when.format(Instant.ofEpochMilli(st))) + " · Live on " + name.get(sid);
            t.heroText = (String) e[3];
            String ch = name.get(sid);
            t.stream = on ? streamOf(ch) : null;
            t.channel = ch;
            skySpecs(t, (JSONObject) e[6], ch);
            t.start = st;
            t.ok = () -> tune(ch, logo);
            out.add(t);
        }
        android.util.Log.i("Sport", "Sky's guide: " + out.size() + " tiles in " + (System.currentTimeMillis() - t0) + " ms");
        // Sky's tiles on the page now, the rest joining them as they come.
        java.util.function.Consumer<List<Tile>> early = sportEarly;
        if (early != null) early.accept(new ArrayList<>(out));
        try { playlist.get(60, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) { }
        android.util.Log.i("Sport", "playlist read by " + (System.currentTimeMillis() - t0) + " ms");
        // The playlist's own games (Kleva TV's list): those Sky's guide does not have, with their broadcaster.
        out.addAll(PlaylistSport.tiles(this, out));
        try { leagues.get(60, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) { }
        android.util.Log.i("Sport", "leagues read by " + (System.currentTimeMillis() - t0) + " ms");
        ahead.shutdown();
        // Then the leagues' other games over the same days (the Premier League's, Europe's), each with its channel.
        out.addAll(PlaylistSport.leagueTiles(this, out));
        out = PlaylistSport.once(out);
        out.sort((x, y) -> Long.compare(x.start, y.start));
        return out;
    }

    /** A 4K channel's name ("Sky Sports Main Event UHD", "… 4K"). */
    static boolean uhdName(String n) {
        return n != null && n.matches("(?i).*\\b(uhd|4k|ultra ?hd)\\b.*");
    }

    /** {"football" or "other", competition logo or null} from a Sky guide title such as "Live PL: Spurs v C. Palace". */
    static String[] competition(String title) {
        String t = title.replaceFirst("^(?i)live:? ", "");
        String espn = "https://a.espncdn.com/i/leaguelogos/soccer/500/";
        String[][] foot = {{"PL:", "23"}, {"SNF:", "23"}, {"MNF:", "23"}, {"EFL:", "24"}, {"B'Liga:", "10"}, {"Serie A:", "12"},
                {"SPFL:", "45"}, {"La Liga:", "15"}, {"LaLiga:", "15"}, {"UCL:", "2"}, {"UEL:", "2310"}, {"FA Cup:", "40"},
                {"Carabao Cup:", "41"}, {"WSL:", null}};
        for (String[] f : foot) if (t.startsWith(f[0])) return new String[]{"football", f[1] == null ? null : espn + f[1] + ".png"};
        if (t.matches("(?i).*\\b(v|vs)\\b.*") && !t.matches("(?i).*\\b(nfl|nba|wnba|afl|aflw|bbl|wbbl|ipl|nrl|rugby|cricket|test|t20|odi|darts|netball|super league|urc|hockey|ice|basketball|baseball|bears|saracens|harlequins|quins|bath|sale|gloucester|northampton|exeter|chiefs|red bulls|@)\\b.*"))
            return new String[]{"football", null};
        String leagues = "https://a.espncdn.com/i/teamlogos/leagues/500/";
        if (t.contains("F1")) return new String[]{"other", leagues + "f1.png"};
        if (t.contains("WNBA")) return new String[]{"other", leagues + "wnba.png"};
        if (t.matches("(?i).*\\baflw?\\b.*")) return new String[]{"other", leagues + "afl.png"};
        if (t.contains("UFC")) return new String[]{"other", leagues + "ufc.png"};
        if (t.contains("NFL")) return new String[]{"other", leagues + "nfl.png"};
        if (t.contains("NBA")) return new String[]{"other", leagues + "nba.png"};
        return new String[]{"other", null};
    }

    private List<Tile> apps() {
        List<Tile> out = new ArrayList<>();
        PackageManager pm = a.getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(q, 0);
        found.sort((x, y) -> x.loadLabel(pm).toString().compareToIgnoreCase(y.loadLabel(pm).toString()));
        List<String> hidden = Config.load(a).hiddenApps;
        for (ResolveInfo r : found) {
            String p = r.activityInfo.packageName;
            if (p.equals(a.getPackageName()) || hidden.contains(p)) continue;
            Tile t = new Tile();
            t.title = r.loadLabel(pm).toString();
            t.icon = r.loadIcon(pm);
            t.heroTitle = t.title;
            t.heroText = "Press OK to open";
            t.ok = () -> openApp(p);
            out.add(t);
        }
        return out;
    }

    /** The logo of a film or series from TMDB, in English, or null. */
    String tmdbLogo(int id, boolean tv) {
        String k = (tv ? "tv/" : "movie/") + id;
        synchronized (logos) {
            if (logos.containsKey(k)) return logos.get(k);
        }
        String found = null;
        try {
            // Every logo it has, in any language: English first, then one with no words of a language, then the
            // title's own (a Spanish film's Spanish logo), then any; within each, TMDB's best voted.
            JSONObject j = new JSONObject(get("https://api.themoviedb.org/3/" + k + "/images?api_key=" + tmdbKey));
            JSONArray l = j.optJSONArray("logos");
            int bestRank = Integer.MAX_VALUE;
            double bestVote = -1;
            for (int i = 0; l != null && i < l.length(); i++) {
                JSONObject o = l.getJSONObject(i);
                String lang = o.isNull("iso_639_1") ? "" : o.optString("iso_639_1");
                int rank = lang.equals("en") ? 0 : lang.isEmpty() ? 1 : 2;
                double vote = o.optDouble("vote_average", 0) + o.optInt("vote_count", 0) * 0.001;
                if (rank < bestRank || rank == bestRank && vote > bestVote) {
                    bestRank = rank;
                    bestVote = vote;
                    String path = o.optString("file_path");
                    if (path.endsWith(".svg")) path = path.replace(".svg", ".png");
                    found = "https://image.tmdb.org/t/p/w500" + path;
                }
            }
        } catch (Exception e) {
            // Not answered (the network, TMDB busy): not remembered as having no logo, so the next look asks again.
            return null;
        }
        synchronized (logos) {
            logos.put(k, found);
        }
        return found;
    }

    // ---- actions ---------------------------------------------------------------------------------------------------

    void tune(String channel, String logoUrl) {
        tune(java.util.Collections.singletonList(channel), logoUrl);
    }

    /**
     * TiviMate on the first of these channels it has, as v1 does (its own search; a 4K version next to the channel is
     * preferred). The channel's logo is fetched first when it is not to hand, so the cover always shows the logo.
     */
    void tune(List<String> channels, String given) {
        String pkg = "ar.tvplayer.tv";
        players.stop();
        String logoUrl = given != null ? given : logoFor(channels);
        // The cover over TiviMate shows the channel's logo large: Sky's full-size picture, trimmed, never a tile's copy.
        if (logoUrl == null) {
            GoalWatchService.onAppOpened(a, pkg);
            AppSearchService.tune(a, pkg, channels, null);
            return;
        }
        io.execute(() -> {
            Bitmap got = ScoresTicker.cachedLogo(a, logoUrl, px(512));
            Bitmap logo = logoUrl.contains("newchanlogos") ? trim(got) : got;
            main.post(() -> {
                GoalWatchService.onAppOpened(a, pkg);
                AppSearchService.tune(a, pkg, channels, logo);
            });
        });
    }

    /**
     * The TV's inputs in a glass list under the button (Android's TV input list: HDMI ports in order, then the rest),
     * each by its own name; OK on one switches the TV to it. A TV with none says so.
     */
    private void showInputs(View anchor) {
        android.media.tv.TvInputManager tim = (android.media.tv.TvInputManager) a.getSystemService(Context.TV_INPUT_SERVICE);
        List<android.media.tv.TvInputInfo> list = new ArrayList<>();
        if (tim != null) for (android.media.tv.TvInputInfo i : tim.getTvInputList()) if (i.isPassthroughInput() && !i.isHidden(a)) list.add(i);
        list.sort((x, y) -> {
            boolean hx = x.getType() == android.media.tv.TvInputInfo.TYPE_HDMI, hy = y.getType() == android.media.tv.TvInputInfo.TYPE_HDMI;
            if (hx != hy) return hx ? -1 : 1;
            return String.valueOf(label(x)).compareToIgnoreCase(String.valueOf(label(y)));
        });
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(10), px(10), px(10), px(10));
        GradientDrawable bg = fill(26, 0xF21B1F3B);
        bg.setStroke(Math.max(1, px(1.5f)), GLASS_LINE);
        box.setBackground(bg);
        int width = px(320);
        android.widget.PopupWindow pop = new android.widget.PopupWindow(box, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pop.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        if (list.isEmpty()) {
            TextView none = text(22, regular, 0xB3FFFFFF);
            none.setText("No inputs on this TV");
            none.setPadding(px(20), px(16), px(20), px(16));
            box.addView(none);
        }
        for (android.media.tv.TvInputInfo info : list) {
            TextView row = text(24, regular, Color.WHITE);
            row.setText(label(info));
            row.setPadding(px(22), px(14), px(22), px(14));
            row.setFocusable(true);
            android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
            sl.addState(new int[]{android.R.attr.state_focused}, fill(16, 0xEBFFFFFF));
            sl.addState(new int[0], new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            row.setBackground(sl);
            row.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                    new int[]{0xFF1A1D24, Color.WHITE}));
            row.setOnClickListener(v -> {
                pop.dismiss();
                Intent go = new Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.buildChannelUriForPassthroughInput(info.getId()));
                open(go);
            });
            box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        pop.showAsDropDown(anchor, anchor.getWidth() - width, px(12));
        if (box.getChildCount() > 0) box.getChildAt(0).requestFocus();
    }

    private CharSequence label(android.media.tv.TvInputInfo i) {
        CharSequence custom = i.loadCustomLabel(a);
        return custom != null && custom.length() > 0 ? custom : i.loadLabel(a);
    }

    /**
     * Opens a title in Debrid Stream (its search, through the helper), with the title's own logo (TMDB's, its name as
     * drawn) and picture on the cover while it loads: both fetched first, so the cover always has the logo.
     */
    /**
     * The title's page in the app (Debrid Stream): the app opens at once, with the cover over it showing whatever of
     * the title's picture and logo is already in hand (the hero has usually fetched both); what is not comes onto the
     * cover as it arrives. A title with no TMDB id (an app's own row) is looked up by name for its logo.
     */
    void searchTitle(String pkg, String title, boolean tv, int tmdbId, String art) {
        String known;
        synchronized (logos) {
            known = tmdbId != 0 ? logos.get((tv ? "tv/" : "movie/") + tmdbId) : null;
        }
        Bitmap logo = known == null ? null : cached(known);
        Bitmap pic = art == null ? null : cached(art);
        Runnable open = () -> {
            AppSearchService.search(a, pkg, title, tv, pic, logo);
            GoalWatchService.onAppOpened(a, pkg);
        };
        // The helper off (a TCL turns it off now and then): switched back on first, given a moment to start.
        if (!AppSearchService.running()) {
            AppSearchService.ensure(a);
            main.postDelayed(open, 1600);
        } else {
            open.run();
        }
        if (logo != null && pic != null) return;
        io.execute(() -> {
            int id = tmdbId;
            boolean isTv = tv;
            if (id == 0 && !tmdbKey.isEmpty()) {
                int[] found = findTitle(title, tv);
                if (found != null) { id = found[0]; isTv = found[1] == 1; }
            }
            String logoUrl = logo != null || id == 0 || tmdbKey.isEmpty() ? null : tmdbLogo(id, isTv);
            Bitmap l = logo != null ? logo : logoUrl == null ? null : fetch(logoUrl, px(600));
            Bitmap p = pic != null ? pic : art == null ? null : fetch(art, px(960));
            main.post(() -> AppSearchService.coverPictures(p, l));
        });
    }

    /**
     * A game in Kleva TV (its search, the first channel for it, playing), under the cover with the game's picture; the
     * helper switched back on first if a TCL turned it off. otherwise: when Kleva TV is not on this TV.
     */
    void kleva(String home, String away, String title, String art, Runnable otherwise) {
        players.stop();
        Bitmap pic = art == null ? null : cached(art);
        boolean wake = !AppSearchService.running();
        if (wake) AppSearchService.ensure(a);
        long asked = System.currentTimeMillis();
        // The loading cover shows the two clubs' crests, not the game's name (they come from the picture's cache).
        pics.execute(() -> {
            Bitmap pair = art != null && art.startsWith("crest:") ? pair(art.substring(6)) : null;
            // Behind them, the clubs' colours blurred (the tile's picture, made again if it has left the cache).
            Bitmap bg = pic != null || art == null ? pic : fetch(art, 480);
            Runnable open = () -> {
                if (!AppSearchService.kleva(a, home, away, title, bg, pair, otherwise)) { otherwise.run(); return; }
                GoalWatchService.onAppOpened(a, "com.plaintextdev.klevaTv");
            };
            main.postDelayed(open, wake ? Math.max(0, 1600 - (System.currentTimeMillis() - asked)) : 0);
        });
    }

    /** "home|away" crests side by side on clear ground, as the loading cover's logo for a game. */
    private Bitmap pair(String spec) {
        String[] p = spec.split("\\|");
        if (p.length < 2) return null;
        Bitmap home = fetch(p[0], 300), away = fetch(p[1], 300);
        if (home == null || away == null) return null;
        int size = 300, gap = 200;
        Bitmap out = Bitmap.createBitmap(size * 2 + gap, size, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        for (int i = 0; i < 2; i++) {
            Bitmap b = i == 0 ? home : away;
            float scale = size / (float) Math.max(b.getWidth(), b.getHeight());
            float bw = b.getWidth() * scale, bh = b.getHeight() * scale, cx = i == 0 ? size / 2f : size * 1.5f + gap;
            c.drawBitmap(b, null, new android.graphics.RectF(cx - bw / 2, (size - bh) / 2, cx + bw / 2, (size + bh) / 2), paint);
        }
        return out;
    }

    /** A picture already fetched for url, at whatever size it was fetched; null when there is none. */
    private Bitmap cached(String url) {
        Bitmap best = null;
        for (Map.Entry<String, Bitmap> e : cache.snapshot().entrySet()) {
            String k = e.getKey();
            if (!k.startsWith(url) || (k.length() > url.length() && k.charAt(url.length()) != '@' && k.charAt(url.length()) != '#')) continue;
            if (k.endsWith("#hero")) continue;   // the hero's copy has its edges faded away
            if (best == null || e.getValue().getWidth() > best.getWidth()) best = e.getValue();
        }
        return best;
    }

    /** A film or series on TMDB by its name: {id, 1 for a series}; the kind asked for first. Null when none. */
    int[] findTitle(String title, boolean tv) {
        try {
            String q = java.net.URLEncoder.encode(title, "UTF-8");
            for (boolean series : new boolean[]{tv, !tv}) {
                JSONArray r = new JSONObject(get("https://api.themoviedb.org/3/search/" + (series ? "tv" : "movie") + "?query=" + q
                        + "&api_key=" + tmdbKey)).optJSONArray("results");
                if (r != null && r.length() > 0) return new int[]{r.getJSONObject(0).getInt("id"), series ? 1 : 0};
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * A film's or series' own logo (TMDB's, its name as drawn) at a tile's foot, over a soft shade, above any trailer:
     * looked up in the background; a title with no logo stays as its picture alone.
     */
    View titleLogo(FrameLayout tile, int tmdbId, boolean tv, int maxW, int maxH) {
        // Its fades drawn straight, the shade and the logo each with the alpha: as one group, the foot was painted into
        // an offscreen copy on every frame of every fade (and the poster's widening changed its size each frame too).
        FrameLayout foot = new FrameLayout(a) {
            @Override public boolean hasOverlappingRendering() {
                return false;
            }
        };
        foot.setTag("titlelogo");
        foot.setAlpha(0f);
        View soft = new View(a);
        soft.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x8C000000}));
        foot.addView(soft, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageView l = new ImageView(a);
        l.setAdjustViewBounds(true);
        l.setScaleType(ImageView.ScaleType.FIT_START);
        l.setMaxWidth(px(maxW));
        l.setMaxHeight(px(maxH));
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        ll.leftMargin = px(16);
        ll.bottomMargin = px(14);
        foot.addView(l, ll);
        tile.addView(foot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(maxH + 60), Gravity.BOTTOM));
        if (tmdbKey.isEmpty()) return foot;
        Runnable show = () -> showLogo(tile, foot, maxW, 0);
        String known;
        synchronized (logos) {
            known = logos.get((tv ? "tv/" : "movie/") + tmdbId);
        }
        if (known != null) {
            // Looked up before (read ahead): straight from the cache, no trip through the reader.
            image(l, known, px(maxW * 2), show);
            return foot;
        }
        io.execute(() -> {
            String url = tmdbLogo(tmdbId, tv);
            if (url == null) return;
            main.post(() -> image(l, url, px(maxW * 2), show));
        });
        return foot;
    }

    /**
     * The logo in once its poster is wide: a logo that comes while the poster is still widening (a read-ahead one
     * comes at once) waits for it, a few looks a little apart, rather than staying hidden; one that comes after the
     * poster narrowed again stays hidden until it widens.
     */
    private void showLogo(FrameLayout tile, View foot, int maxW, int tries) {
        if (foot.getParent() == null) return;
        ViewGroup.LayoutParams lp = tile.getLayoutParams();
        if (lp != null && lp.width > 0 && lp.width < px(maxW)) {
            if (tries < 4) main.postDelayed(() -> showLogo(tile, foot, maxW, tries + 1), 120);
            return;
        }
        foot.animate().alpha(1f).setDuration(250).start();
    }

    /** A channel's logo from the TV guide's list, by any of its names: for a tune that came with none. */
    private String logoFor(List<String> names) {
        for (String n : names) {
            String want = n.replaceAll("(?i)\\s*\\b(f?hd|uhd|4k)\\b\\s*$", "").trim();
            for (String[] c : channels) {
                String have = c[1].replaceAll("(?i)\\s*\\b(f?hd|uhd|4k)\\b\\s*$", "").trim();
                if (have.equalsIgnoreCase(want)) return ScoresTicker.skyLogo(c[0], c[1]);
            }
        }
        return null;
    }

    /** OK on a score pill: TiviMate on the channel showing that game, found as v1 finds it. */
    private void watchGame(ScoresTicker.Game g) {
        String[] football = a.getResources().getStringArray(R.array.football_tv_channels);
        io.execute(() -> {
            TvGuide.Watch w = TvGuide.watch(football, g);
            main.post(() -> {
                if (w == null || w.names == null || w.names.isEmpty()) { openApp("ar.tvplayer.tv"); return; }
                tune(w.names, w.logo);
            });
        });
    }

    void openApp(String pkg) {
        PackageManager pm = a.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return;
        open(i);
        GoalWatchService.onAppOpened(a, pkg);
    }

    private void open(Intent i) {
        try {
            a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    // ---- the network pill and the VPN ------------------------------------------------------------------------------

    private boolean vpnUp;

    private void updateNetwork() {
        String text = "Offline";
        boolean vpnNow = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) a.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifiOn = false, ethernet = false, online = false;
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    // Tailscale only links this box to its own devices: not the VPN.
                    if (!MainActivity.tailnetOnly(cm.getLinkProperties(net))) vpnNow = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifiOn = true;
            }
            if (online) text = ethernet ? "Ethernet" : "Wi-Fi";
            if (wifiOn && !ethernet) {
                WifiManager wm = (WifiManager) a.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        wifi.setText(text);
        if (vpnNow != vpnUp) {
            vpnUp = vpnNow;
            paint();
        }
        main.removeCallbacks(network);
        main.postDelayed(network, 15_000L);
    }

    // ---- the playlist's channels -----------------------------------------------------------------------------------

    /** The theme's "guide_channels" ("Sky service id|TiviMate name|group"), in the playlist's order. */
    /**
     * The stream a channel's tile plays: for a UHD channel, its HD version when the theme names one (preview_streams,
     * "Sky Sports Main Event UHD|3609333"). A UHD channel is HDR: on a TCL its picture cannot be drawn inside Home
     * (sound only), and shown by the TV's video hardware it switches the whole screen to HDR with a notice in the
     * corner. The HD one shows at once in a tile that size, lighter to play, and OK still tunes the UHD one.
     */
    private String previewStream(Tile t) {
        return previewStream(a, t.channel, t.stream);
    }

    /** As above for any channel by name (the TV guide's corner preview too). */
    static String previewStream(Context c, String channel, String stream) {
        if (channel == null || !channel.matches("(?i).*\\b(uhd|4k|hdr)\\b.*")) return stream;
        for (String line : Config.themeStrings(c, "preview_streams", new ArrayList<>())) {
            String[] p = line.split("\\|");
            if (p.length > 1 && p[0].trim().equalsIgnoreCase(channel.trim()) && !p[1].trim().isEmpty()) return p[1].trim();
        }
        return stream;
    }

    private String streamOf(String channelName) {
        if (channelName == null) return null;
        for (String[] c : channels) if (c[1].equalsIgnoreCase(channelName)) return c[4];
        return null;
    }

    static List<String[]> guideChannels(Context c) {
        List<String[]> out = new ArrayList<>();
        for (String line : Config.themeStrings(c, "guide_channels", new ArrayList<>())) {
            String[] p = line.split("\\|");
            if (p.length < 2 || p[0].trim().isEmpty()) continue;
            out.add(new String[]{p[0].trim(), p[1].trim(), p.length > 2 ? p[2].trim() : "", p.length > 3 ? p[3].trim() : "",
                    p.length > 4 ? p[4].trim() : ""});
        }
        if (out.isEmpty()) {
            for (String line : c.getResources().getStringArray(R.array.uk_tv_channels)) {
                String[] p = line.split("\\|", 2);
                out.add(new String[]{p[0].trim(), p.length > 1 ? p[1].trim() : p[0].trim(), "Entertainment", "", ""});
            }
        }
        return out;
    }

    // ---- pictures --------------------------------------------------------------------------------------------------

    void image(ImageView v, String url, int width) {
        image(v, url, width, null);
    }

    /** A picture fetched ahead into the cache, at the width a later image(…, url, width) will ask for. */
    void prefetch(String url, int width) {
        if (url == null) return;
        String key = url + "@" + width;
        if (cache.get(key) != null) return;
        pics.execute(() -> {
            if (cache.get(key) != null) return;
            Bitmap b = fetch(url, width);
            if (b == null) return;
            b.prepareToDraw();
            cache.put(key, b);
        });
    }

    /** A film's or series' logo looked up and fetched ahead, as titleLogo(…, maxW, …) will show it. */
    void prefetchLogo(int id, boolean tv, int maxW) {
        if (id == 0 || tmdbKey.isEmpty()) return;
        io.execute(() -> {
            String url = tmdbLogo(id, tv);
            if (url != null) prefetch(url, px(maxW * 2));
        });
    }

    void image(ImageView v, String url, int width, Runnable then) {
        if (url == null) {
            v.setImageDrawable(null);
            return;
        }
        String key = url + "@" + width;
        v.setTag(R.id.v2_url, key);
        Bitmap hit = cache.get(key);
        if (hit != null) {
            v.setImageBitmap(hit);
            if (url.contains("newchanlogos")) fitLogo(v, hit);
            if (then != null) then.run();
            return;
        }
        pics.execute(() -> {
            Bitmap b = fetch(url, width);
            if (b == null) return;
            // Handed to the graphics chip now, while it is idle, not on the frame that first shows it.
            b.prepareToDraw();
            cache.put(key, b);
            main.post(() -> {
                if (!key.equals(v.getTag(R.id.v2_url))) return;
                v.setImageBitmap(b);
                if (url.contains("newchanlogos")) fitLogo(v, b);
                if (then != null) then.run();
            });
        });
    }

    private Bitmap fetch(String url, int width) {
        if (url.endsWith("#yt")) return video(url.substring(0, url.length() - 3), width);
        if (url.startsWith("crest:")) return crests(url.substring(6), width);
        // Surprise me's moods: drawn here, not fetched.
        if (url.startsWith("mood:")) return MoodArt.draw(a, url, width);
        // The Kids page's YouTube Kids card: drawn here too.
        if (url.startsWith("ytkids:")) return KidsArt.draw(a, url, width);
        // The Kids page's shows the launcher draws (space, dance).
        if (url.startsWith("kidsact:")) return KidsArt.scene(a, url.substring(8), width);
        try {
            byte[] data;
            if (url.startsWith("http")) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setUseCaches(true);
                c.setConnectTimeout(6000);
                c.setReadTimeout(10000);
                try (InputStream in = c.getInputStream()) {
                    data = readAll(in);
                } finally {
                    c.disconnect();
                }
            } else {
                try (InputStream in = a.getContentResolver().openInputStream(Uri.parse(url))) {
                    if (in == null) return null;
                    data = readAll(in);
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= width) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            // lite_home: no wider than it is drawn. A picture twice its size on screen made the graphics chip read four
            // times its pixels every frame (ten posters at once), and took four times as long to hand over to it.
            if (lite && b != null && b.getWidth() > width * 1.2f) {
                b = Bitmap.createScaledBitmap(b, width, Math.max(1, Math.round(b.getHeight() * width / (float) b.getWidth())), true);
            }
            if (url.contains("newchanlogos")) b = trim(b);
            return url.endsWith("#uhd") ? ScoresTicker.uhdLogo(a, b) : b;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * A game's picture from its two clubs' crests ("crest:home|away"): each on its own half, in the crest's own colour
     * (its average, darkened), the halves meeting on a slant, as Kleva TV draws its fixtures.
     */
    private Bitmap crests(String spec, int width) {
        String[] p = spec.split("\\|");
        if (p.length < 2) return null;
        Bitmap home = fetch(p[0], width / 2), away = fetch(p[1], width / 2);
        if (home == null || away == null) return null;
        int w = Math.max(320, width), h = w * 9 / 16;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        int ch = tone(home), ca = tone(away);
        android.graphics.Path left = new android.graphics.Path();
        left.moveTo(0, 0);
        left.lineTo(w * 0.56f, 0);
        left.lineTo(w * 0.44f, h);
        left.lineTo(0, h);
        left.close();
        paint.setShader(new android.graphics.LinearGradient(0, 0, 0, h, ch, darker(ch), android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(new android.graphics.LinearGradient(0, 0, 0, h, ca, darker(ca), android.graphics.Shader.TileMode.CLAMP));
        c.save();
        c.clipOutPath(left);
        c.drawRect(0, 0, w, h, paint);
        c.restore();
        paint.setShader(null);
        float size = h * 0.56f;
        for (int i = 0; i < 2; i++) {
            Bitmap b = i == 0 ? home : away;
            float scale = size / Math.max(b.getWidth(), b.getHeight());
            float bw = b.getWidth() * scale, bh = b.getHeight() * scale, cx = w * (i == 0 ? 0.25f : 0.75f), cy = h * 0.46f;
            c.drawBitmap(b, null, new android.graphics.RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2), paint);
        }
        return out;
    }

    /** A crest's colour: the average of its opaque pixels, kept from going too pale or too dark. */
    private static int tone(Bitmap b) {
        Bitmap s = Bitmap.createScaledBitmap(b, 24, 24, true);
        long r = 0, g = 0, bl = 0, n = 0;
        for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
            int c = s.getPixel(x, y);
            if (Color.alpha(c) < 200) continue;
            int lum = (Color.red(c) + Color.green(c) + Color.blue(c)) / 3;
            if (lum > 235) continue;   // the white of the badge
            r += Color.red(c); g += Color.green(c); bl += Color.blue(c); n++;
        }
        if (n == 0) return 0xFF2A2F4A;
        float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (bl / n), hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.2f);
        hsv[2] = Math.max(0.35f, Math.min(0.75f, hsv[2]));
        return Color.HSVToColor(hsv);
    }

    private static int darker(int c) {
        return Color.rgb(Color.red(c) * 6 / 10, Color.green(c) * 6 / 10, Color.blue(c) * 6 / 10);
    }

    /**
     * A YouTube thumbnail whole and without bars: the video's full-size widescreen one (maxresdefault) where it has one;
     * else its usual one, which is 4:3 with the 16:9 picture between black bars, cut to the picture.
     */
    private Bitmap video(String url, int width) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(https://i\\d?\\.ytimg\\.com/vi(?:_webp)?/[^/]+/)").matcher(url);
        if (m.lookingAt()) {
            Bitmap wide = fetch(m.group(1).replace("/vi_webp/", "/vi/") + "maxresdefault.jpg", width);
            if (wide != null && wide.getWidth() > 200) return wide;
        }
        Bitmap b = fetch(url, width);
        if (b == null) return null;
        float aspect = b.getWidth() / (float) b.getHeight();
        if (aspect < 1.5f) {
            // 4:3 with bars: the middle 16:9 of it.
            int h = Math.round(b.getWidth() * 9f / 16f), top = Math.max(0, (b.getHeight() - h) / 2);
            b = Bitmap.createBitmap(b, 0, top, b.getWidth(), Math.min(h, b.getHeight() - top));
        }
        return b;
    }

    /**
     * A Sky channel logo in a room made for the long ones (Sky Sports Main Event): a block-shaped one (Sky One, a
     * square badge) would come out a fraction of their size, so it is given more height, about the same area.
     */
    static void fitLogo(ImageView v, Bitmap b) {
        if (b == null || b.getHeight() == 0) return;
        Object base = v.getTag(R.id.v2_logo_h);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        int[] room = base instanceof int[] ? (int[]) base : new int[]{v.getMaxHeight(), lp != null ? lp.height : -2};
        // Only rooms made for long logos (wider than they are tall several times over): not the tiles' white badge.
        if (room[0] >= Integer.MAX_VALUE / 2 || v.getMaxWidth() < room[0] * 2.5f) return;
        v.setTag(R.id.v2_logo_h, room);
        float k = b.getWidth() < b.getHeight() * 2.2f ? 1.75f : 1f;
        if (room[0] > 0 && room[0] < Integer.MAX_VALUE / 2) v.setMaxHeight(Math.round(room[0] * k));
        if (lp != null && room[1] > 0) {
            lp.height = Math.round(room[1] * k);
            v.setLayoutParams(lp);
        }
    }

    /** A logo without the empty square around it (Sky's channel logos sit in the middle of a 600 by 600 canvas). */
    static Bitmap trim(Bitmap b) {
        if (b == null || !b.hasAlpha()) return b;
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int l = w, t = h, r = -1, bo = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if ((px[y * w + x] >>> 24) < 24) continue;
            if (x < l) l = x;
            if (x > r) r = x;
            if (y < t) t = y;
            if (y > bo) bo = y;
        }
        if (r < l || bo < t) return b;
        return Bitmap.createBitmap(b, l, t, r - l + 1, bo - t + 1);
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0 && out.size() < 8 * 1024 * 1024) out.write(buf, 0, n);
        return out.toByteArray();
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(12000);
        try (InputStream in = c.getInputStream()) {
            return new String(readAll(in), "UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /**
     * The hero picture, faded towards its left edge and its foot once, when it arrives (an alpha mask baked into the
     * bitmap off the main thread), so drawing and crossfading it costs no offscreen pass on any frame.
     */
    private void heroImage(ImageView v, String url, Runnable then) {
        String key = url + "#hero";
        v.setTag(R.id.v2_url, key);
        Bitmap hit = cache.get(key);
        if (hit != null) {
            v.setImageBitmap(hit);
            Bitmap raw = blurSource.get(key);
            if (zone != Z_MATCH || page != null) adaptMeshFrom(raw != null ? raw : hit);
            then.run();
            return;
        }
        pics.execute(() -> {
            Bitmap b = fetch(url, px(1400));
            if (b == null) return;
            // A small copy of the whole picture (before its fades) for the background's blur.
            Bitmap raw = blurBg ? Bitmap.createScaledBitmap(b, BLUR_W, BLUR_H, true) : null;
            if (raw != null) blurSource.put(key, raw);
            Bitmap m = fade(b);
            m.prepareToDraw();
            cache.put(key, m);
            main.post(() -> {
                if (!key.equals(v.getTag(R.id.v2_url))) return;
                v.setImageBitmap(m);
                if (zone != Z_MATCH || page != null) adaptMeshFrom(raw != null ? raw : m);
                then.run();
            });
        });
    }

    /** Home's background in one bitmap: the blue gradient, the glow at the top right and the shades from the left and top. */
    /**
     * The mesh background, adaptive: its light takes its colours from what is on the screen (the hero's picture, Match
     * Day's two clubs), cross-fading to them once the focus settles; while Home shows it also drifts between two
     * arrangements of the same light (not on lite_home). The two arrangements of the colours now, the one showing.
     */
    private boolean mesh;
    private final Bitmap[] meshPair = new Bitmap[2];
    private int meshVariant;
    private Drawable meshShown;
    private int screenW, screenH;
    /** The blobs' colours (with their strength): plum top left, teal bottom right, amber top right, violet at the foot. */
    private static final int[] MESH_DEFAULT = {0x8C6B2C8F, 0x730E7C86, 0x4DD98A3A, 0x593B2A9A};
    private int[] meshColours = MESH_DEFAULT, meshWanted;
    private final ExecutorService meshIo = Executors.newSingleThreadExecutor();
    // Quick to follow what is on screen: drawn a quarter of a second after the focus stops, in under half a second.
    private static final int MESH_FADE_MS = 12_000, MESH_ADAPT_MS = 450;
    private static final long MESH_EVERY_MS = 75_000L, MESH_SETTLE_MS = 220L;

    /** The background's picture now (the score bar's frosted glass is cut from it). */
    private Bitmap bgShown;

    /** The background cross-fades to this picture of the light over ms (at once for 0). */
    private void fadeMesh(Bitmap next, int ms) {
        if (next == null) return;
        bgShown = next;
        // The score bar's glass shows the same picture behind it, blurred, changing with it.
        if (scores != null) scores.backdrop(next, ms);
        Drawable to = new android.graphics.drawable.BitmapDrawable(a.getResources(), next);
        if (meshShown == null || ms <= 0) {
            root.setBackground(to);
        } else {
            android.graphics.drawable.TransitionDrawable t = new android.graphics.drawable.TransitionDrawable(new Drawable[]{meshShown, to});
            t.setCrossFadeEnabled(true);
            root.setBackground(t);
            t.startTransition(ms);
        }
        meshShown = to;
    }

    /** Every so often while Home shows: the light drifts to its other arrangement (a long cross-fade), then back. */
    private final Runnable shiftMesh = new Runnable() {
        @Override public void run() {
            if (!mesh || lite) return;
            if (!showingPicture && meshPair[meshVariant ^ 1] != null) {
                meshVariant ^= 1;
                fadeMesh(meshPair[meshVariant], MESH_FADE_MS);
            }
            main.postDelayed(this, MESH_EVERY_MS);
        }
    };

    /** The light to take these colours (four, with their strength), once the focus has settled on them. */
    void adaptMesh(int[] colours) {
        if (!mesh || colours == null || Arrays.equals(colours, meshWanted)) return;
        meshWanted = colours;
        main.removeCallbacks(adaptNow);
        main.postDelayed(adaptNow, settleMs());
    }

    /**
     * How long the focus must rest before the background changes: on lite_home (the TCL) long enough that a run of
     * presses along a row changes it once, at the end, not a whole-screen cross-fade under every step of the slide.
     */
    private long settleMs() {
        return lite ? 650L : MESH_SETTLE_MS;
    }

    private final Runnable adaptNow = () -> {
        int[] want = meshWanted;
        if (want == null || (!this.showingPicture && Arrays.equals(want, meshColours))) return;
        int v = meshVariant;
        boolean two = !lite;
        meshIo.execute(() -> {
            Bitmap b0 = meshBackground(screenW, screenH, v, want);
            Bitmap b1 = two ? meshBackground(screenW, screenH, v ^ 1, want) : null;
            main.post(() -> {
                if (want != meshWanted) return;   // something newer was asked for meanwhile
                meshColours = want;
                showingPicture = false;
                meshPair[v] = b0;
                meshPair[v ^ 1] = b1;
                meshVariant = v;
                fadeMesh(b0, MESH_ADAPT_MS);
            });
        });
    };

    /** The light in a picture's colours: its left, middle and right thirds and its strongest colour (see palette). */
    void adaptMeshFrom(Bitmap pic) {
        if (!mesh || pic == null || pic.isRecycled()) return;
        // Sampled off the main thread (scaling a 1400-wide picture down cost a frame on each move), the latest only.
        final int asked = ++meshSampleAt;
        main.removeCallbacks(adaptNow);
        main.removeCallbacks(blurNow);
        if (blurBg) {
            // The picture itself, blurred: once the focus has stopped on it.
            blurPic = pic;
            blurAsked = asked;
            main.postDelayed(blurNow, settleMs());
            return;
        }
        meshIo.execute(() -> {
            if (asked != meshSampleAt || pic.isRecycled()) return;
            int[] c = palette(pic);
            if (c != null) main.post(() -> { if (asked == meshSampleAt) adaptMesh(c); });
        });
    }

    /** "blur": the content's picture as the background; the small copies kept for it, and the one asked for now. */
    private boolean blurBg, showingPicture;
    private static final int BLUR_W = 128, BLUR_H = 72;
    private final android.util.LruCache<String, Bitmap> blurSource = new android.util.LruCache<>(48);
    private Bitmap blurPic;
    private int blurAsked;

    private final Runnable blurNow = () -> {
        Bitmap pic = blurPic;
        int asked = blurAsked;
        if (pic == null) return;
        meshIo.execute(() -> {
            if (asked != this.meshSampleAt || pic.isRecycled()) return;
            Bitmap bg = blurredBackground(pic);
            if (bg == null) return;
            main.post(() -> {
                if (asked != this.meshSampleAt) return;
                meshWanted = null;
                meshColours = null;
                showingPicture = true;
                fadeMesh(bg, MESH_ADAPT_MS);
            });
        });
    };

    /**
     * The background as the content's own picture: shrunk small and blurred hard (three box passes, as a Gaussian),
     * drawn up to a quarter of the screen's size and smoothed again, its colours a little richer, then darkened a
     * third of the way towards midnight, with a light vignette only at the far edges and the shades that keep the
     * words readable (the canvas's option B, "Vivid"). Off the main thread.
     */
    private Bitmap blurredBackground(Bitmap pic) {
        try {
            int sw = Math.max(screenW, screenH), sh = Math.min(screenW, screenH);
            int bw = Math.max(2, sw / 4), bh = Math.max(2, sh / 4);
            int tw = 72, th = 40;
            Bitmap tiny = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas tc = new android.graphics.Canvas(tiny);
            tc.drawColor(0xFF0B0C21);
            // The picture over the whole screen, cropped to fill it.
            int pw = pic.getWidth(), ph = pic.getHeight();
            float want = tw / (float) th, have = pw / (float) ph;
            android.graphics.Rect src = have > want
                    ? new android.graphics.Rect(Math.round((pw - ph * want) / 2f), 0, Math.round((pw + ph * want) / 2f), ph)
                    : new android.graphics.Rect(0, Math.round((ph - pw / want) / 2f), pw, Math.round((ph + pw / want) / 2f));
            android.graphics.Paint fp = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
            tc.drawBitmap(pic, src, new android.graphics.Rect(0, 0, tw, th), fp);
            int[] px = new int[tw * th];
            tiny.getPixels(px, 0, tw, 0, 0, tw, th);
            boxBlur(px, tw, th, 4, 3);
            tiny.setPixels(px, 0, tw, 0, 0, tw, th);
            Bitmap out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(out);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG | android.graphics.Paint.DITHER_FLAG);
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
            cm.setSaturation(1.1f);
            p.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
            c.drawBitmap(tiny, null, new android.graphics.Rect(0, 0, bw, bh), p);
            tiny.recycle();
            int[] big = new int[bw * bh];
            out.getPixels(big, 0, bw, 0, 0, bw, bh);
            boxBlur(big, bw, bh, 6, 2);
            out.setPixels(big, 0, bw, 0, 0, bw, bh);
            android.graphics.Paint q = new android.graphics.Paint(android.graphics.Paint.DITHER_FLAG);
            // Darkened 35% towards midnight, a little deeper at the far edges (the vignette), as the cards sit on it.
            c.drawColor(0x5907081A);
            q.setShader(new android.graphics.RadialGradient(bw * 0.62f, bh * 0.36f, bw * 0.94f,
                    new int[]{0x00000000, 0x00000000, 0x6B000000}, new float[]{0f, 0.6f, 1f}, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, bw, bh, q);
            shades(c, q, 0, 0, bw / (float) sw);
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A box blur of r, passes times (three passes come close to a Gaussian), in place, edges held. */
    static void boxBlur(int[] px, int w, int h, int r, int passes) {
        int[] tmp = new int[px.length];
        for (int n = 0; n < passes; n++) {
            blurLine(px, tmp, w, h, r, true);
            blurLine(tmp, px, w, h, r, false);
        }
    }

    private static void blurLine(int[] in, int[] out, int w, int h, int r, boolean across) {
        int lines = across ? h : w, len = across ? w : h, div = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            int ra = 0, ga = 0, ba = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[index(l, Math.max(0, Math.min(len - 1, i)), w, across)];
                ra += (c >> 16) & 0xFF; ga += (c >> 8) & 0xFF; ba += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                out[index(l, i, w, across)] = 0xFF000000 | ((ra / div) << 16) | ((ga / div) << 8) | (ba / div);
                int add = in[index(l, Math.min(len - 1, i + r + 1), w, across)];
                int sub = in[index(l, Math.max(0, i - r), w, across)];
                ra += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                ga += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                ba += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    private static int index(int line, int i, int w, boolean across) {
        return across ? line * w + i : i * w + line;
    }

    /** The latest picture asked to colour the light (an older one still being sampled is dropped). */
    private volatile int meshSampleAt;

    /** The light in two clubs' colours (Match Day): the home side's at the left and the foot, the away side's at the right. */
    void adaptMeshClubs(int home, int away) {
        if (home == 0 && away == 0) return;
        int h = soft(home != 0 ? home : away), w = soft(away != 0 ? away : home);
        adaptMesh(new int[]{0x8C000000 | (h & 0xFFFFFF), 0x73000000 | (w & 0xFFFFFF), 0x55000000 | (w & 0xFFFFFF), 0x59000000 | (h & 0xFFFFFF)});
    }

    /** A colour toned for the light behind the words: saturated but never loud, mid dark. */
    private static int soft(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.max(0.45f, Math.min(0.85f, hsv[1] * 1.1f));
        hsv[2] = Math.max(0.42f, Math.min(0.62f, hsv[2]));
        return Color.HSVToColor(hsv) & 0xFFFFFF;
    }

    /**
     * Four colours from a picture for the light (with their strengths): the left third's, the right third's, the most
     * vivid, and the middle's; each the average of its pixels weighted by how vivid they are (see-through and grey
     * ones count for little), then toned with soft(). Null for a picture with nothing to go on.
     */
    private static int[] palette(Bitmap pic) {
        Bitmap small;
        try {
            small = Bitmap.createScaledBitmap(pic, 32, 18, true);
        } catch (RuntimeException e) {
            return null;
        }
        double[][] sum = new double[3][4];
        float[] hsv = new float[3];
        int vivid = 0;
        double best = -1;
        for (int y = 0; y < 18; y++) for (int x = 0; x < 32; x++) {
            int c = small.getPixel(x, y);
            if ((c >>> 24) < 160) continue;
            Color.colorToHSV(c, hsv);
            double wgt = hsv[1] * hsv[1] * (hsv[2] > 0.15f && hsv[2] < 0.97f ? 1 : 0.1) + 0.002;
            int band = Math.min(2, x * 3 / 32);
            sum[band][0] += Color.red(c) * wgt;
            sum[band][1] += Color.green(c) * wgt;
            sum[band][2] += Color.blue(c) * wgt;
            sum[band][3] += wgt;
            double score = hsv[1] * hsv[2];
            if (score > best) { best = score; vivid = c; }
        }
        if (small != pic) small.recycle();
        int[] avg = new int[3];
        int got = 0;
        for (int i = 0; i < 3; i++) {
            if (sum[i][3] <= 0.01) continue;
            avg[i] = Color.rgb((int) (sum[i][0] / sum[i][3]), (int) (sum[i][1] / sum[i][3]), (int) (sum[i][2] / sum[i][3]));
            got++;
        }
        if (got == 0) return null;
        for (int i = 0; i < 3; i++) if (avg[i] == 0) avg[i] = avg[1] != 0 ? avg[1] : avg[2] != 0 ? avg[2] : avg[0];
        if (vivid == 0) vivid = avg[2];
        return new int[]{0x8C000000 | soft(avg[0]), 0x73000000 | soft(avg[2]), 0x55000000 | soft(vivid), 0x59000000 | soft(avg[1])};
    }

    /**
     * The mesh background (variant 0 or 1, its blobs a little apart) in these colours: midnight, with large soft
     * blobs (top left, bottom right, top right, along the foot) and the shades that keep the words readable, in
     * midnight. Drawn at a third of the screen's size (it is all soft light) and scaled up; off the main thread too.
     */
    private Bitmap meshBackground(int w, int h, int variant, int[] colours) {
        int sw = Math.max(w, h), sh = Math.min(w, h);
        // A quarter of the screen's size: soft light, scaled up; small enough to draw in a few milliseconds.
        int bw = Math.max(2, sw / 4), bh = Math.max(2, sh / 4);
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.DITHER_FLAG | android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(0, 0, bw, bh, 0xFF0B0C21, 0xFF15163A, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, bw, bh, p);
        boolean v = variant == 1;
        // {x, y, radius (of the width)} for each blob in its colour.
        float[][] at = v
                ? new float[][]{{0.25f, 0.32f, 0.68f}, {0.72f, 0.66f, 0.62f}, {0.95f, 0.22f, 0.36f}, {0.38f, 0.94f, 0.55f}}
                : new float[][]{{0.15f, 0.20f, 0.70f}, {0.82f, 0.78f, 0.60f}, {0.86f, 0.10f, 0.38f}, {0.48f, 1.00f, 0.55f}};
        for (int i = 0; i < at.length && i < colours.length; i++) {
            int col = colours[i];
            p.setShader(new android.graphics.RadialGradient(at[i][0] * bw, at[i][1] * bh, at[i][2] * bw,
                    new int[]{col, (col & 0xFFFFFF) | ((col >>> 24) / 3 << 24), col & 0xFFFFFF}, new float[]{0f, 0.45f, 1f},
                    android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, bw, bh, p);
        }
        shades(c, p, 0, 0, bw / (float) sw);
        return b;
    }

    private Bitmap background(int w, int h) {
        int sw = Math.max(w, h), sh = Math.min(w, h);
        Bitmap b = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.DITHER_FLAG);
        p.setShader(new android.graphics.LinearGradient(0, 0, sw, sh, BG_MID, BG_DEEP, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, sw, sh, p);
        p.setShader(new android.graphics.RadialGradient(sw * 0.85f, sh * 0.1f, px(1100), BG_GLOW, 0x003A2A9A, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, sw, sh, p);
        shades(c, p, 0, 0, 1f);
        return b;
    }

    /**
     * The two shades (from the left over 1300 design pixels, from the top over 240) drawn into a canvas whose (0, 0) is at
     * design point (x0, y0) of the screen, at scale design-to-canvas pixels.
     */
    private void shades(android.graphics.Canvas c, android.graphics.Paint p, float x0, float y0, float scale) {
        float k = s * scale;
        int shade = mesh ? 0x0B0C21 : 0x111548, top = mesh ? 0x0A0B1E : 0x0A0C32;
        p.setShader(new android.graphics.LinearGradient(-x0 * k, 0, (1300 - x0) * k, 0,
                new int[]{0xCC000000 | shade, 0x80000000 | shade, shade}, null, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(-x0 * k, -y0 * k, (1300 - x0) * k, (1080 - y0) * k, p);
        p.setShader(new android.graphics.LinearGradient(0, -y0 * k, 0, (240 - y0) * k, 0x8C000000 | top, top, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(-x0 * k, -y0 * k, (1920 - x0) * k, (240 - y0) * k, p);
    }

    /** The bitmap with the hero's fades: out to the left over 40% of the view's width, and out at its foot. */
    private Bitmap fade(Bitmap src) {
        int bw = src.getWidth(), bh = src.getHeight();
        Bitmap out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        c.drawBitmap(src, 0, 0, null);
        // What centre-crop shows of it in the 1400 by 720 view.
        float view = 1400f / 720f, visW = bw, visH = bh, x0 = 0, y0 = 0;
        if (bw / (float) bh > view) { visW = bh * view; x0 = (bw - visW) / 2f; } else { visH = bw / view; y0 = (bh - visH) / 2f; }
        android.graphics.Paint p = new android.graphics.Paint();
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        p.setShader(new android.graphics.LinearGradient(x0, 0, x0 + visW * 0.4f, 0, 0x00000000, 0xFF000000, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, bw, bh, p);
        p.setShader(new android.graphics.LinearGradient(0, y0 + visH * (1 - 260f / 720f), 0, y0 + visH, 0xFF000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, bw, bh, p);
        // The background's shades over the picture too (they used to be views above it): the view sits at design
        // (520, 0), 1400 wide, so one design pixel is visW / 1400 bitmap pixels.
        android.graphics.Paint q = new android.graphics.Paint();
        q.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP));
        c.save();
        c.translate(x0, y0);
        shades(c, q, 520, 0, visW / 1400f / s);
        c.restore();
        return out;
    }

    // ---- small helpers ---------------------------------------------------------------------------------------------

    int px(float design) {
        return Math.round(design * s);
    }

    void add(View v, int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(w), px(h));
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        root.addView(v, lp);
    }

    TextView text(float size, Typeface face, int colour) {
        TextView t = new TextView(a);
        style(t, size, face, colour);
        return t;
    }

    private void style(TextView t, float size, Typeface face, int colour) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        if (size >= 34 && face == regular) face = displayRegular;
        else if (size >= 34 && face == semi) face = displaySemi;
        t.setTypeface(face);
        // Glyphs placed on sub-pixels, not snapped to whole ones: even spacing and sharper edges on a TV.
        t.getPaint().setSubpixelText(true);
        t.setLineSpacing(0, 1.22f);
        t.setTextColor(colour);
        t.setIncludeFontPadding(false);
    }

    GradientDrawable fill(float radius, int colour) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(radius));
        g.setColor(colour);
        return g;
    }

    private GradientDrawable glass(float radius, boolean on) {
        GradientDrawable g = fill(radius, on ? 0xEBFFFFFF : GLASS);
        g.setStroke(px(1.5f), on ? Color.WHITE : GLASS_LINE);
        return g;
    }

    ViewOutlineProvider round(float radius) {
        int r = px(radius);
        return new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        };
    }

    boolean installed(String pkg) {
        try {
            a.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String appLabel(String pkg) {
        if (pkg == null) return "Apps";
        try {
            return a.getPackageManager().getApplicationLabel(a.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static String join(String sep, String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (b.length() > 0) b.append(sep);
            b.append(p);
        }
        return b.toString();
    }
}
