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
    private static final int[] CAT_COLOURS = {0xFFFF8A1F, 0xFF9B5CFF, 0xFFFF4FA3, 0xFF4F8BFF, 0xFF3DDC84, 0xFF2AD1C9};
    private static final String[] CAT_NAMES = {"TV Guide", "Discover", "Movies", "TV shows", "Sport", "Apps"};
    private static final int[] CAT_ICONS = {R.drawable.ic_v2_guide, R.drawable.ic_v2_discover, R.drawable.ic_v2_movies,
            R.drawable.ic_v2_shows, R.drawable.ic_v2_sport, R.drawable.ic_v2_grid};
    private static final String[] CAT_MODES = {"guide", "discover", "movies", "shows", "sport", "apps"};

    /** The rail: a glyph for each kind of dock app, matched on its package. */
    private static int glyph(String pkg) {
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
    }

    private static final int Z_TOP = 0, Z_RAIL = 1, Z_ROW = 2, Z_CATS = 3;

    final MainActivity a;
    final Handler main = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newFixedThreadPool(3);
    private final ExecutorService pics = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(
            (int) Math.min(Runtime.getRuntime().maxMemory() / 6, 48L * 1024 * 1024)) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private final Map<String, List<Tile>> rows = new HashMap<>();
    private final Map<String, Long> rowsAt = new HashMap<>();
    private final Map<String, String> logos = new HashMap<>();
    float s;
    Typeface regular, semi;
    String tmdbKey;
    private List<String> dock;
    List<String[]> channels;

    FrameLayout root;
    ImageView heroA, heroB;
    private boolean heroFrontA = true;
    private String heroShown;
    LinearLayout htext;
    private ImageView hTop, hLogo;
    private TextView hTopText, hTitle, hMeta, hText;
    final List<FrameLayout> railViews = new ArrayList<>();
    TextView label;
    FrameLayout viewport;
    private LinearLayout row;
    final List<View> catViews = new ArrayList<>();
    private final List<View> topViews = new ArrayList<>();
    private TextView wifi;
    private View vpn;
    ScoresTicker scores;
    private WeatherLine weatherLine;
    private GuideV2 guide;
    /** The page open over Home (Discover, Movies, TV shows, Sport), or null. */
    private PagesV2.Page page;

    private int zone = Z_ROW, topIdx, railIdx, rowIdx, catIdx;
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
        int w = a.getResources().getDisplayMetrics().widthPixels, h = a.getResources().getDisplayMetrics().heightPixels;
        s = Math.max(w, h) / 1920f;
        regular = a.getResources().getFont(R.font.hind_regular);
        semi = a.getResources().getFont(R.font.hind_semibold);
        tmdbKey = Config.themeString(a, "tmdb_key", "").trim();
        dock = new ArrayList<>(Config.load(a).dockApps);
        channels = guideChannels(a);
        root = new FrameLayout(a);
        root.setClipChildren(false);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{BG_MID, BG_DEEP});
        root.setBackground(bg);
        a.setContentView(root);
        a.getWindow().setBackgroundDrawable(null);
        View glow = new View(a);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{BG_GLOW, 0x003A2A9A});
        g.setGradientRadius(px(1100));
        g.setGradientCenter(0.85f, 0.1f);
        glow.setBackground(g);
        add(glow, 0, 0, 1920, 1080);

        heroA = heroView();
        heroB = heroView();
        heroB.setAlpha(0f);
        // The words over the picture stay readable: a shade from the left, the full height of the screen.
        add(fade(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xCC111548, 0x80111548, 0x00111548}), 0, 0, 1300, 1080);
        add(fade(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x8C0A0C32, 0x000A0C32}), 0, 0, 1920, 240);

        buildClock();
        buildButtons();
        buildHeroText();
        buildRail();
        label = text(18, semi, 0xBFFFFFFF);
        label.setLetterSpacing(0.11f);
        add(label, 216, 532, 1500, 34);
        viewport = new FrameLayout(a);
        // Clipped to its own box: a row scrolled along stays inside it, clear of the rail.
        viewport.setOutlineProvider(ViewOutlineProvider.BOUNDS);
        viewport.setClipToOutline(true);
        add(viewport, 192, 556, 1656, 268);
        row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(216));
        rlp.leftMargin = px(24);
        rlp.topMargin = px(24);
        viewport.addView(row, rlp);
        buildCats();

        String[] leagues = a.getResources().getStringArray(R.array.score_leagues);
        if (leagues.length > 0) {
            scores = new ScoresTicker(a, root, a.getResources().getDisplayMetrics().density, leagues, new ScoresTicker.Host() {
                @Override public void onScoresOpen(ScoresTicker.Game game) { }
                @Override public void onScoresDown() { }
            });
            scores.showF1(Config.themeFlag(a, "f1_scores_bar", false));
            scores.showAlerts(!Config.themeFlag(a, "alerts_everywhere", false));
            float dp = a.getResources().getDisplayMetrics().density;
            root.post(() -> scores.place(px(96), Math.round(23 * dp), px(1728)));
        }
        guide = new GuideV2(a, root, s, regular, semi, channels, this::refocus);
        load("foryou");
        paint();
    }

    private ImageView heroView() {
        ImageView v = new Faded(a, px(560), px(260));
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
        LinearLayout clock = new LinearLayout(a);
        clock.setOrientation(LinearLayout.HORIZONTAL);
        clock.setBaselineAligned(false);
        LinearLayout hm = new LinearLayout(a);
        hm.setOrientation(LinearLayout.HORIZONTAL);
        hm.setBaselineAligned(true);
        TextClock time = new TextClock(a);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        style(time, 92, a.getResources().getFont(R.font.hind_regular), Color.WHITE);
        time.setLetterSpacing(-0.02f);
        time.setIncludeFontPadding(false);
        hm.addView(time);
        TextClock secs = new TextClock(a);
        secs.setFormat24Hour(":ss");
        secs.setFormat12Hour(":ss");
        style(secs, 46, regular, 0xB3FFFFFF);
        secs.setIncludeFontPadding(false);
        hm.addView(secs);
        // Same-width digits, in a box as wide as the widest time: the date and weather beside it never move.
        time.setFontFeatureSettings("tnum");
        secs.setFontFeatureSettings("tnum");
        android.graphics.Paint pt = new android.graphics.Paint();
        pt.setTypeface(time.getTypeface());
        pt.setFontFeatureSettings("tnum");
        pt.setTextSize(px(92));
        float w = pt.measureText("00:00");
        pt.setTextSize(px(46));
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
        date.setAllCaps(true);
        date.setLetterSpacing(0.17f);
        col.addView(date);
        TextView weather = text(22, regular, Color.WHITE);
        weather.setLetterSpacing(0.14f);
        weather.setGravity(Gravity.CENTER_VERTICAL);
        weather.setPadding(px(24), 0, px(26), 0);
        weather.setBackground(glass(25, false));
        weather.setCompoundDrawablePadding(px(12));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(50));
        wlp.topMargin = px(10);
        col.addView(weather, wlp);
        clock.addView(col);
        add(clock, 96, 92, 1100, 160);
        weatherLine = new WeatherLine(weather, a.getString(R.string.weather_latitude), a.getString(R.string.weather_longitude),
                a.getString(R.string.weather_place));
    }

    private void buildButtons() {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.END);
        wifi = text(24, regular, Color.WHITE);
        wifi.setGravity(Gravity.CENTER_VERTICAL);
        wifi.setPadding(px(22), 0, px(24), 0);
        Drawable wi = a.getDrawable(R.drawable.ic_wifi);
        if (wi != null) {
            wi = wi.mutate();
            wi.setBounds(0, 0, px(30), px(30));
            wi.setTint(Color.WHITE);
            wifi.setCompoundDrawables(wi, null, null, null);
        }
        wifi.setCompoundDrawablePadding(px(12));
        wifi.setText("Wi-Fi");
        topButton(bar, wifi, ViewGroup.LayoutParams.WRAP_CONTENT, () -> open(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)));
        ImageView nord = new ImageView(a);
        nord.setImageResource(R.drawable.v2_nord);
        nord.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int ip = px(19);
        nord.setPadding(ip, ip + px(1), ip, ip - px(1));
        vpn = nord;
        String vpnApp = Config.themeStrings(a, "top_bar_apps", new ArrayList<>()).isEmpty() ? ""
                : Config.themeStrings(a, "top_bar_apps", new ArrayList<>()).get(0);
        if (!vpnApp.isEmpty() && installed(vpnApp)) topButton(bar, nord, px(66), () -> openApp(vpnApp));
        ImageView bulb = icon(R.drawable.ic_bulb);
        Intent smartHome = new Intent("com.google.android.libraries.tv.smarthome.intent.action.OPEN_SMART_HOME")
                .setPackage("com.google.android.apps.tv.dreamx").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (smartHome.resolveActivity(a.getPackageManager()) != null) {
            topButton(bar, bulb, px(66), () -> LightsPanel.show(a, bulb, Config.themeStrings(a, "lights_rooms",
                    Arrays.asList(a.getResources().getStringArray(R.array.lights_rooms))), smartHome));
        }
        ImageView mic = icon(R.drawable.ic_mic);
        topButton(bar, mic, px(66), () -> open(new Intent(Intent.ACTION_ASSIST)));
        ImageView gear = icon(R.drawable.ic_settings);
        topButton(bar, gear, px(66), () -> open(new Intent(android.provider.Settings.ACTION_SETTINGS)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(900), px(66), Gravity.TOP | Gravity.END);
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
        hTitle.setLineSpacing(0, 0.95f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = px(10);
        htext.addView(hTitle, hlp);
        hMeta = text(20, regular, 0xC7FFFFFF);
        hMeta.setLetterSpacing(0.03f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.bottomMargin = px(10);
        htext.addView(hMeta, mlp);
        hText = text(23, regular, 0xEBFFFFFF);
        hText.setMaxLines(2);
        hText.setEllipsize(TextUtils.TruncateAt.END);
        hText.setShadowLayer(px(10), 0, px(2), 0x73000000);
        htext.addView(hText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        add(htext, 216, 226, 900, 300);
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
                    art.setImageDrawable(icon);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            f.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ImageView g = new ImageView(a);
            g.setImageResource(glyph(p));
            g.setAlpha(0.85f);
            f.addView(g, new FrameLayout.LayoutParams(px(30), px(30), Gravity.CENTER));
            add(f, 96, y, 64, 64);
            railViews.add(f);
            y += 84;
        }
        // The white bar beside the app whose tiles are showing.
        View mark = new View(a);
        mark.setBackground(fill(3, Color.WHITE));
        mark.setAlpha(0f);
        mark.setTag("mark");
        add(mark, 80, 300 + 16, 5, 32);
    }

    private void buildCats() {
        int x = 216, w = (1608 - 5 * 18) / 6;
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
            add(box, x, 852, w, 150);
            catViews.add(box);
            x += w + 18;
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
        if (Config.themeFlag(a, "alerts_everywhere", false)) GoalWatchService.startEverywhere(a);
        // Rows age: what is on now and the apps' own rows are read again when Home comes back after a while.
        Long at = rowsAt.get(mode);
        if (at != null && System.currentTimeMillis() - at > 5 * 60_000L) {
            rowsAt.remove(mode);
            rows.remove(mode);
            load(mode);
        }
    }

    void stop() {
        GoalWatchService.laneHome = false;
        weatherLine.stop();
        if (scores != null) scores.stop();
        main.removeCallbacks(network);
    }

    void destroy() {
        io.shutdownNow();
        pics.shutdownNow();
        cache.evictAll();
        guide.close();
    }

    void trim() {
        cache.evictAll();
    }

    /** Home pressed: back to For you, first tile, guide closed. */
    void home() {
        guide.hide();
        closePage();
        railSel = -1;
        zone = Z_ROW;
        rowIdx = 0;
        load("foryou");
        paint();
    }

    private void refocus() {
        paint();
    }

    // ---- keys ------------------------------------------------------------------------------------------------------

    /** All keys come here first; true when v2 used it. */
    boolean key(KeyEvent e) {
        if (guide.isOpen()) return guide.key(e);
        if (page != null) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) closePage();
                return true;
            }
            page.key(e);
            return true;
        }
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean nav = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) back();
            return true;
        }
        if (!ok && !nav) return false;
        // OK acts on release (as Android's buttons do), arrows on press and repeat.
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && e.getRepeatCount() == 0 && !e.isCanceled()) press();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        move(code);
        return true;
    }

    private void back() {
        if (zone != Z_ROW || railSel != -1 || !"foryou".equals(mode) || rowIdx != 0) home();
    }

    private void move(int code) {
        int n = shown.size();
        switch (zone) {
            case Z_TOP:
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && topIdx > 0) topIdx--;
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && topIdx < topViews.size() - 1) topIdx++;
                else if (code == KeyEvent.KEYCODE_DPAD_DOWN) zone = n > 0 ? Z_ROW : Z_RAIL;
                break;
            case Z_RAIL:
                if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    if (railIdx > 0) selectRail(railIdx - 1); else { zone = Z_TOP; topIdx = 0; }
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (railIdx < railViews.size() - 1) selectRail(railIdx + 1); else { zone = Z_CATS; catIdx = 0; }
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && n > 0) {
                    zone = Z_ROW;
                    rowIdx = 0;
                }
                break;
            case Z_ROW:
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (rowIdx > 0) rowIdx--; else { zone = Z_RAIL; railIdx = Math.max(0, railSel); if (railSel < 0) selectRail(0); }
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (rowIdx < n - 1) rowIdx++;
                } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    zone = Z_TOP;
                    topIdx = 0;
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    zone = Z_CATS;
                    catIdx = Math.min(5, rowIdx * 6 / 4);
                }
                if (zone == Z_ROW) heroSoon();
                break;
            case Z_CATS:
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (catIdx > 0) catIdx--; else { zone = Z_RAIL; railIdx = railViews.size() - 1; }
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && catIdx < 5) {
                    catIdx++;
                } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    zone = n > 0 ? Z_ROW : Z_RAIL;
                }
                break;
            default:
        }
        paint();
    }

    private void press() {
        switch (zone) {
            case Z_TOP:
                Object r = topViews.get(topIdx).getTag();
                if (r instanceof Runnable) ((Runnable) r).run();
                return;
            case Z_RAIL:
                String p = (String) railViews.get(railIdx).getTag();
                if (p == null) { zone = Z_ROW; rowIdx = 0; paint(); } else openApp(p);
                return;
            case Z_ROW:
                if (rowIdx < shown.size()) {
                    View t = row.getChildAt(rowIdx);
                    if (t != null) t.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90)
                            .withEndAction(() -> t.animate().scaleX(1.07f).scaleY(1.07f).setDuration(140).setInterpolator(SKY).start()).start();
                    Runnable go = shown.get(rowIdx).ok;
                    if (go != null) main.postDelayed(go, 120);
                }
                return;
            case Z_CATS:
                if (catIdx == 0) {
                    guide.show();
                    return;
                }
                if (catIdx >= 1 && catIdx <= 4) {
                    openPage(catIdx);
                    return;
                }
                railSel = -1;
                load(CAT_MODES[catIdx]);
                zone = Z_ROW;
                rowIdx = 0;
                paint();
                return;
            default:
        }
    }

    /** Opens the page behind a box: 1 Discover, 2 Movies, 3 TV shows, 4 Sport. Home's own row makes way for it. */
    private void openPage(int which) {
        page = which == 1 ? PagesV2.discover(this) : which == 2 ? PagesV2.movies(this) : which == 3 ? PagesV2.shows(this) : new PagesV2.SportPage(this);
        boolean sport = which == 4, full = which == 2 || which == 3;
        List<View> away = new ArrayList<>(Arrays.asList(label, viewport));
        if (full || sport) away.addAll(catViews);
        if (full) { away.addAll(railViews); away.add(root.findViewWithTag("mark")); }
        if (sport) { away.add(htext); away.add(heroA); away.add(heroB); }
        for (View v : away) if (v != null) v.animate().alpha(0f).setDuration(180).start();
        hidden = away;
        page.open();
        paint();
    }

    private List<View> hidden = new ArrayList<>();

    private void closePage() {
        if (page == null) return;
        page.close();
        page = null;
        heroTopColour(0xFFC9A7FF);
        for (View v : hidden) {
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

    private void selectRail(int i) {
        railIdx = i;
        railSel = i;
        String p = (String) railViews.get(i).getTag();
        load(p == null ? "apps" : p);
    }

    // ---- drawing the focus -----------------------------------------------------------------------------------------

    void paint() {
        for (int i = 0; i < railViews.size(); i++) {
            FrameLayout f = railViews.get(i);
            boolean on = zone == Z_RAIL && i == railIdx, sel = i == railSel;
            boolean colour = (on || sel) && f.getTag() != null;
            f.getChildAt(0).animate().alpha(colour ? 1f : 0f).setDuration(150).start();
            f.getChildAt(1).animate().alpha(colour ? 0f : 0.85f).setDuration(150).start();
            f.animate().scaleX(on ? 1.12f : 1f).scaleY(on ? 1.12f : 1f).setDuration(150).setInterpolator(SKY).start();
            f.setForeground(on ? ring(18) : null);
            if (f.getTag() == null) f.setBackground(fill(18, on || sel ? 0x4DFFFFFF : GLASS));
        }
        View mark = root.findViewWithTag("mark");
        if (mark != null) {
            boolean show = railSel >= 0 && zone != Z_RAIL;
            mark.animate().alpha(show ? 1f : 0f).setDuration(150).start();
            if (railSel >= 0) mark.animate().translationY(px(84) * railSel).setDuration(200).setInterpolator(SKY).start();
        }
        for (int i = 0; i < row.getChildCount(); i++) {
            View t = row.getChildAt(i);
            boolean on = zone == Z_ROW && i == rowIdx;
            t.animate().scaleX(on ? 1.07f : 1f).scaleY(on ? 1.07f : 1f).setDuration(150).setInterpolator(SKY).start();
            t.setElevation(on ? px(8) : 0);
            ((FrameLayout) t).setForeground(on ? ring(16) : null);
        }
        int first = zone == Z_ROW ? Math.max(0, rowIdx - 3) : 0;
        row.animate().translationX(-first * px(408)).setDuration(300).setInterpolator(SKY).start();
        for (int i = 0; i < catViews.size(); i++) {
            FrameLayout box = (FrameLayout) catViews.get(i);
            boolean on = zone == Z_CATS && i == catIdx && page == null;
            boolean sel = page != null ? i == catIdx : railSel < 0 && i > 0 && CAT_MODES[i].equals(mode);
            GradientDrawable b = fill(16, on ? 0xEBFFFFFF : sel ? (CAT_COLOURS[i] & 0x00FFFFFF) | 0x73000000 : 0x14FFFFFF);
            b.setStroke(Math.max(1, px(1)), on ? 0xFFFFFFFF : 0x24FFFFFF);
            box.setBackground(b);
            LinearLayout in = (LinearLayout) box.getChildAt(1);
            ((ImageView) in.getChildAt(0)).setColorFilter(on ? 0xFF1A1D24 : Color.WHITE);
            ((TextView) in.getChildAt(1)).setTextColor(on ? 0xFF1A1D24 : Color.WHITE);
            ((TextView) in.getChildAt(1)).setTypeface(on ? semi : regular);
            box.animate().scaleX(on ? 1.04f : 1f).scaleY(on ? 1.04f : 1f).setDuration(150).setInterpolator(SKY).start();
        }
        for (int i = 0; i < topViews.size(); i++) {
            View v = topViews.get(i);
            boolean on = zone == Z_TOP && i == topIdx;
            boolean vpnOn = v == vpn && vpnUp;
            GradientDrawable b = fill(33, on ? 0xEBFFFFFF : vpnOn ? 0x3D9B5CFF : GLASS);
            b.setStroke(px(1.5f), on ? Color.WHITE : vpnOn ? 0xB3C4A8FF : GLASS_LINE);
            v.setBackground(b);
            int ink = on ? 0xFF1A1D24 : Color.WHITE;
            if (v instanceof ImageView) {
                if (v == vpn) ((ImageView) v).setColorFilter(on ? 0xFF1A1D24 : vpnOn ? 0xFFC9A2FF : Color.WHITE);
                else ((ImageView) v).setColorFilter(ink);
            } else if (v instanceof TextView) {
                ((TextView) v).setTextColor(ink);
                for (Drawable d : ((TextView) v).getCompoundDrawables()) if (d != null) d.setTint(ink);
            }
        }
    }

    Drawable ring(int r) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(r));
        g.setStroke(px(3), Color.WHITE);
        return g;
    }

    // ---- the row ---------------------------------------------------------------------------------------------------

    private void load(String m) {
        mode = m;
        int gen = ++generation;
        String railPkg = railSel >= 0 ? (String) railViews.get(railSel).getTag() : null;
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
                if (t.tmdbId == 0 || tmdbKey.isEmpty()) continue;
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
            default: return railLabel(railPkg, appLabel(railPkg));
        }
    }

    private void fill(List<Tile> tiles, int gen) {
        shown = tiles;
        if (rowIdx >= tiles.size()) rowIdx = Math.max(0, tiles.size() - 1);
        row.animate().alpha(0f).setDuration(110).withEndAction(() -> {
            if (gen != generation) return;
            row.removeAllViews();
            for (Tile t : tiles) row.addView(tileView(t));
            ViewGroup.LayoutParams rl = row.getLayoutParams();
            rl.width = Math.max(4, tiles.size()) * px(408);
            row.setLayoutParams(rl);
            if (tiles.isEmpty()) for (int i = 0; i < 4; i++) row.addView(emptyTile());
            row.setTranslationX(0);
            row.animate().alpha(1f).setDuration(180).start();
            paint();
            if (!tiles.isEmpty()) hero(tiles.get(Math.min(rowIdx, tiles.size() - 1)));
        }).start();
    }

    private View emptyTile() {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(fill(16, 0x10FFFFFF));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(384), px(216));
        lp.rightMargin = px(24);
        f.setLayoutParams(lp);
        return f;
    }

    private View tileView(Tile t) {
        FrameLayout f = new FrameLayout(a);
        f.setClipToOutline(true);
        f.setOutlineProvider(round(16));
        f.setBackground(fill(16, t.icon != null ? GLASS : 0xFF222433));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(384), px(216));
        lp.rightMargin = px(24);
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
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(pic, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        image(pic, t.image, px(384));
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
            bar.addView(done, new FrameLayout.LayoutParams(Math.round(px(384) * Math.min(1f, t.progress)), ViewGroup.LayoutParams.MATCH_PARENT));
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
        main.postDelayed(heroLater, 320);
    }

    /** The picture and the words above the row for a tile: the picture crossfades, the words swap after a beat. */
    void hero(Tile t) {
        String img = t.heroImage != null ? t.heroImage : t.image;
        if (img != null && !img.equals(heroShown)) {
            heroShown = img;
            ImageView in = heroFrontA ? heroB : heroA, out = heroFrontA ? heroA : heroB;
            heroFrontA = !heroFrontA;
            image(in, img, px(1400), () -> {
                in.animate().alpha(1f).setDuration(450).setInterpolator(SKY).start();
                out.animate().alpha(0f).setDuration(450).setInterpolator(SKY).start();
            });
        } else if (img == null) {
            heroShown = null;
            heroA.animate().alpha(0f).setDuration(300).start();
            heroB.animate().alpha(0f).setDuration(300).start();
        }
        htext.animate().alpha(0f).setDuration(120).withEndAction(() -> {
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
            hText.setText(t.heroText);
            hText.setVisibility(t.heroText == null || t.heroText.isEmpty() ? View.GONE : View.VISIBLE);
            htext.animate().alpha(1f).setDuration(160).start();
        }).start();
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
        t.heroTop = pkg == null ? (src.pill != null ? "NEW RELEASE" : null) : null;
        String target = pkg != null ? pkg : debridPackage();
        t.ok = () -> {
            if (target != null) {
                AppSearchService.search(a, target, src.title, src.tv);
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
            t.heroImage = r[1];
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
    private List<Tile> liveNow(boolean sportOnly) {
        List<String[]> order = new ArrayList<>();
        for (String[] c : channels) if (c[2].contains("Sport")) order.add(c);
        if (!sportOnly) for (String[] c : channels) if (!c[2].contains("Sport") && order.size() < 24) order.add(c);
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
            String logoUrl = n.logo;
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
        long now = System.currentTimeMillis();
        List<Object[]> events = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int day = 0; day < 3; day++) {
            String date = Instant.ofEpochMilli(now).plusSeconds(day * 86_400L).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
            JSONArray sch = new JSONObject(get("https://awk.epgsky.com/hawk/linear/schedule/" + date + "/" + ids)).optJSONArray("schedule");
            for (int i = 0; sch != null && i < sch.length(); i++) {
                JSONObject s = sch.getJSONObject(i);
                JSONArray ev = s.optJSONArray("events");
                for (int k = 0; ev != null && k < ev.length(); k++) {
                    JSONObject e = ev.getJSONObject(k);
                    String title = e.optString("t");
                    long st = e.optLong("st") * 1000L, en = st + e.optLong("d") * 1000L;
                    if (!title.toLowerCase(Locale.ROOT).startsWith("live") || en < now || !seen.add(title + st)) continue;
                    if (title.matches("(?i)live:? (sky sports fc|the premier league show|ref watch|transfer).*")) continue;
                    events.add(new Object[]{st, en, title, e.optString("sy"), e.optString("programmeuuid"), s.optString("sid")});
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
            t.compLogo = comp[1];
            t.image = uuid.isEmpty() ? null : "https://images.metadata.sky.com/pd-image/" + uuid + "/16-9/640";
            t.heroImage = uuid.isEmpty() ? null : "https://images.metadata.sky.com/pd-image/" + uuid + "/16-9/1280";
            String logo = "https://d2n0069hmnqmmx.cloudfront.net/epgdata/1.0/newchanlogos/600/600/skychb" + sid + ".png";
            boolean on = st <= now;
            t.chip = on ? "LIVE NOW" : when.format(Instant.ofEpochMilli(st)).toUpperCase(Locale.UK);
            t.sub = "Live on " + name.get(sid);
            t.badge = logo;
            t.heroTop = logo;
            t.heroTitle = t.title;
            t.heroMeta = (on ? "On now" : when.format(Instant.ofEpochMilli(st))) + " · Live on " + name.get(sid);
            t.heroText = (String) e[3];
            String ch = name.get(sid);
            t.ok = () -> tune(ch, logo);
            out.add(t);
        }
        return out;
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
            JSONObject j = new JSONObject(get("https://api.themoviedb.org/3/" + k + "/images?include_image_language=en,null&api_key=" + tmdbKey));
            JSONArray l = j.optJSONArray("logos");
            for (int i = 0; l != null && i < l.length(); i++) {
                JSONObject o = l.getJSONObject(i);
                if (!"en".equals(o.optString("iso_639_1"))) continue;
                String path = o.optString("file_path");
                if (path.endsWith(".svg")) path = path.replace(".svg", ".png");
                found = "https://image.tmdb.org/t/p/w500" + path;
                break;
            }
        } catch (Exception ignored) {
        }
        synchronized (logos) {
            logos.put(k, found);
        }
        return found;
    }

    // ---- actions ---------------------------------------------------------------------------------------------------

    void tune(String channel, String logoUrl) {
        String pkg = "ar.tvplayer.tv";
        GoalWatchService.onAppOpened(a, pkg);
        Bitmap logo = logoUrl == null ? null : cache.get(logoUrl + "@" + px(64));
        AppSearchService.tune(a, pkg, java.util.Collections.singletonList(channel), logo);
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
            if (then != null) then.run();
            return;
        }
        pics.execute(() -> {
            Bitmap b = fetch(url, width);
            if (b == null) return;
            cache.put(key, b);
            main.post(() -> {
                if (!key.equals(v.getTag(R.id.v2_url))) return;
                v.setImageBitmap(b);
                if (then != null) then.run();
            });
        });
    }

    private Bitmap fetch(String url, int width) {
        try {
            byte[] data;
            if (url.startsWith("http")) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
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
            return url.contains("newchanlogos") ? trim(b) : b;
        } catch (Exception | OutOfMemoryError e) {
            return null;
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

    /** The hero picture, fading out towards its left edge and its foot (an alpha mask, so it melts into any background). */
    private static final class Faded extends ImageView {
        private final int left, foot;
        private final android.graphics.Paint mask = new android.graphics.Paint();

        Faded(Context c, int left, int foot) {
            super(c);
            this.left = left;
            this.foot = foot;
            mask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        }

        @Override protected void onDraw(android.graphics.Canvas c) {
            int w = getWidth(), h = getHeight();
            int save = c.saveLayer(0, 0, w, h, null);
            super.onDraw(c);
            mask.setShader(new android.graphics.LinearGradient(0, 0, left, 0, 0x00000000, 0xFF000000, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, mask);
            mask.setShader(new android.graphics.LinearGradient(0, h - foot, 0, h, 0xFF000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, mask);
            c.restoreToCount(save);
        }
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
        t.setTypeface(face);
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

    private boolean installed(String pkg) {
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
