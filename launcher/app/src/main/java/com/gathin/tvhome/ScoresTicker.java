package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.util.LruCache;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Today's football on the greeting's line: one glass box per game, from ESPN's public scoreboards for the leagues
 * in config.xml score_leagues ("eng.1|PL"). As many boxes as fit make a page; with more than one page, each stays
 * PAGE_MS and then flips up to the next. Scores refresh every minute while a game is on, every 10 minutes
 * otherwise, and nothing runs while Home is not in front. Hidden on a day without games.
 */
final class ScoresTicker {
    /** What OK and Down on a game box do: open the configured app, go back to the top bar. */
    interface Host {
        /** OK on a game's box: g is that game. */
        void onScoresOpen(Game g);
        void onScoresDown();

        /** Left past the first game (right false) or Right past the last (right true): out to the top row's neighbour. */
        default void onScoresEdge(boolean right) {
        }
    }

    private static final long LIVE_MS = 60_000L;
    private static final long IDLE_MS = 10 * 60_000L;
    private static final long PAGE_MS = 8_000L;
    private static final long FLIP_MS = 450L;
    private static final String FEED = "https://site.api.espn.com/apis/site/v2/sports/soccer/%s/scoreboard?dates=%s";

    /** One game, ready to draw. */
    static final class Game {
        String id, league, home, away, homeScore, awayScore, detail;
        /** ESPN's team ids, and the league slug the game came from (for its match summary). */
        String homeId, awayId, slug;
        /** The teams' colours (0 when ESPN has none), and while live the match clock in seconds as read at clockAt. */
        int homeColor, awayColor;
        /** The ground, as ESPN names it ("Anfield"); null when it does not say. */
        String venue;
        /** Their second colours (ESPN's alternateColor), for the away side's kit when the first colours clash. */
        int homeAlt, awayAlt;
        /** The clubs' full names ("Tottenham Hotspur"), for finding the game in a TV guide. */
        String homeName, awayName;
        /** adb tests only: the moment to look up in the TV guide instead of the kick-off. */
        long tvAt;
        /** Where it is on in the US when no British channel has it (ESPN's listing, English-language TV first). */
        String abroad;
        /** Every channel ESPN lists for it, English-language TV first. */
        List<String> broadcasts = new ArrayList<>();
        double clockSec;
        long clockAt;
        boolean halftime;
        /** Logo URLs: the competition (its dark-background version) and both crests; null when ESPN has none. */
        String leagueLogo, homeLogo, awayLogo;
        int state;   // 0 live, 1 upcoming, 2 finished: the order they are shown in
        long kickoff;
        /** Every goal so far: {unique key, scorer, minute, "home"/"away", "pen"/"og"/""}. */
        final List<String[]> goals = new ArrayList<>();
        /** Every red card: {unique key, player, minute, "home"/"away", ""}. */
        final List<String[]> reds = new ArrayList<>();
        /** An F1 session instead of a game: the leading drivers as {code, team logo url}, else a line of text. */
        List<String[]> drivers;
        String text;
    }

    /** An F1 box beside the football (a theme's "f1_scores_bar"): the live session, else the last one, else the next. */
    private boolean f1;

    private Typeface face;

    /** Launcher v2's face for the pills: a family whose bold is its own semibold, so the bold spans stay crisp. */
    void setTypeface(Typeface family) {
        face = family;
        measure.setTypeface(Typeface.create(family, Typeface.BOLD));
    }

    private boolean equal;

    /** Told which page of games shows and how many there are (option B's dots under its one pill). */
    java.util.function.BiConsumer<Integer, Integer> pageListener;

    private void pageShown() {
        if (pageListener != null) pageListener.accept(page, box.getVisibility() == View.VISIBLE ? pages.size() : 0);
    }

    /**
     * Where an alert on v2's Home stands, in screen pixels: from left to right, centred on the line at centreY (option
     * B: grown out of the one pill leftwards, the clock left in sight). left -1: over the score bar, as before.
     */
    static volatile int laneLeft = -1, laneRight, laneCentre;

    /** Option B on Home (not pop-up mode, which keeps its own bar): a bar across the top stands in the lane's place. */
    static boolean corner() {
        return laneLeft >= 0 && !PopupFrame.isShowing();
    }

    /**
     * A Home bar's window (h high) put in its place: under option B in the lane from its left (the pill's), centred on
     * the pill's line; else across the top, centred, at y.
     */
    static void placeBar(Context c, android.view.WindowManager.LayoutParams lp, int h, int y, boolean wrap) {
        if (corner()) {
            // From the lane's start (the pill's left), growing to the right; one only as long as it needs, the same.
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = laneLeft;
            lp.y = Math.max(0, laneCentre - h / 2);
        } else {
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = y;
        }
    }

    static void lane(int left, int right, int centreY) {
        laneLeft = left;
        laneRight = right;
        laneCentre = centreY;
    }

    /** Every pill the same width (launcher v2): the F1 one no wider than the games beside it. */
    void equalWidths(boolean on) {
        equal = on;
    }

    void showF1(boolean on) {
        f1 = on;
    }

    /** The goal alert that drops over the line; off when GoalWatchService shows its pop-ups on Home too. */
    private boolean alertsOn = true;

    void showAlerts(boolean on) {
        alertsOn = on;
    }

    private static final long ALERT_MS = 10_000L;
    /** Goals already announced (or already scored when Home first read the scores). */
    private final java.util.Set<String> seenGoals = new java.util.HashSet<>();
    private boolean primed;
    private final java.util.ArrayDeque<Object[]> alerts = new java.util.ArrayDeque<>();
    private View alertView;

    private final Context context;
    private final float density;
    private final FrameLayout box;
    private final String[] leagues;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Paint measure = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Game> games = new ArrayList<>();
    private final List<List<Game>> pages = new ArrayList<>();
    private int page;
    private LinearLayout shownRow;
    private boolean running;
    private final Runnable refresh = this::fetch;
    private final Runnable flip = this::nextPage;
    /** Logos at LOGO px, in memory and on disk (they change once a season, not once a minute). */
    private final LruCache<String, Bitmap> logos = new LruCache<>(80);
    private final ExecutorService logoIo = Executors.newFixedThreadPool(2);
    private final Map<String, List<ImageView>> waiting = new HashMap<>();
    private final File logoDir;
    private final int logo;

    private final Host host;

    ScoresTicker(Context context, FrameLayout root, float density, String[] leagues, Host host) {
        this.host = host;
        this.context = context;
        this.density = density;
        this.leagues = leagues;
        logo = dp(20);
        logoDir = new File(context.getCacheDir(), "logos");
        box = new FrameLayout(context);
        box.setClipChildren(true);
        box.setVisibility(View.GONE);
        box.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(box, new FrameLayout.LayoutParams(0, dp(30), Gravity.TOP | Gravity.LEFT));
        measure.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f,
                context.getResources().getDisplayMetrics()));
        measure.setTypeface(Typeface.DEFAULT_BOLD);   // measure as if all bold: the widest case
    }

    /** Option B's frosted pills (theme "frosted_pills", on with it): a tinted body, so the words read over a bright picture. */
    static volatile boolean frost;

    /** The pills' glass, which an alert on v2's Home takes too, so it reads as one of them. */
    static int glassRes() {
        if (frost && !LightUi.on()) return R.drawable.glass_frost;
        return LightUi.on() ? R.drawable.glass_light : LightUi.glass() ? R.drawable.glass_dark : R.drawable.glass_box;
    }

    /**
     * An alert's glass on v2's Home: the pills' own, washed with the alert's colour when it has one (F1's red, the
     * BBC's, the delivery app's, Tesla's), its rim in that colour too; tint 0 for the plain glass (football).
     */
    static android.graphics.drawable.Drawable laneGlass(Context c, int tint) {
        android.graphics.drawable.Drawable base = c.getDrawable(glassRes()).mutate();
        if (tint == 0) return base;
        float d = c.getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable wash = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{(tint & 0xFFFFFF) | 0x52000000, (tint & 0xFFFFFF) | 0x2E000000});
        wash.setCornerRadius(16 * d);
        wash.setStroke(Math.max(1, Math.round(d)), (tint & 0xFFFFFF) | 0xA6000000);
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{base, wash});
    }

    /** The bar kept as one finished picture on the graphics chip (a slower TV's Home), redrawn only when it changes. */
    void layer(boolean on) {
        box.setLayerType(on ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null);
    }

    /** Steps aside while an alert stands in the bar's place (launcher v2's Home), and comes back after. */
    void cover(boolean on) {
        covered = on;
        if (!on && !heldRolls.isEmpty()) {
            // The bar back: the score changes that came while it was covered slide into place now, once it shows.
            List<Runnable> go = new ArrayList<>(heldRolls);
            heldRolls.clear();
            main.postDelayed(() -> { for (Runnable r : go) r.run(); }, 380);
        }
        box.animate().cancel();
        // Home's pill under option B: the bar has just folded back into it, so it shows again at once.
        if (isHomePill && !on) { box.animate().alpha(1f).setDuration(180).setStartDelay(0).start(); return; }
        box.animate().alpha(on ? 0f : 1f).setDuration(on ? 160 : 320).setStartDelay(on ? 0 : 120).start();
    }

    /** Home's one pill under option B: its place is where the alerts grow out of (PillMorph). */
    boolean isHomePill;

    /**
     * Home's top bar in the notifications' own look (theme "sky_top", on with option B): the one box per game a
     * Sky-style ticker bar (SkyStyle), so an alert growing out of it reads as the same thing opening up.
     */
    static volatile boolean sky;

    private boolean skyBar() {
        return isHomePill && sky;
    }

    /** A size on the 1920-wide design, in pixels (the Sky bars' own scale). */
    private int sk(float v) {
        return Math.max(1, Math.round(v * context.getResources().getDisplayMetrics().widthPixels / 1920f));
    }

    /** The boxes' height: the Sky ticker bar's, else the pills'. */
    private int barH() {
        return skyBar() ? sk(50) : dp(30);
    }

    /** Puts the boxes at (x, centreY) in root coordinates, up to right; called once the greeting is laid out. */
    void place(int x, int centreY, int right) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
        int w = Math.max(0, right - x);
        if (lp.height != barH()) lp.height = barH();
        if (isHomePill) PillMorph.pill(x, centreY - lp.height / 2, right, centreY - lp.height / 2 + lp.height);
        if (lp.width == w && lp.leftMargin == x && lp.topMargin == centreY - lp.height / 2) return;
        lp.width = w;
        lp.leftMargin = x;
        lp.topMargin = centreY - lp.height / 2;
        box.setLayoutParams(lp);
        // The frosted ground cut again for the box's new place.
        if (backdropSource != null) frostFrom(backdropSource, 0);
        paginate();
    }

    void start() {
        if (leagues.length == 0) return;
        running = true;
        main.removeCallbacks(refresh);
        fetch();
        schedulePage();
        tickClocks();
    }

    void stop() {
        running = false;
        main.removeCallbacks(refresh);
        main.removeCallbacks(flip);
        main.removeCallbacks(clockTick);
    }

    // ---- live clocks: a live game's box counts its match time by the second -----------------------

    /** {label, game} for each live football box on the line's pages. */
    private final List<Object[]> clocks = new ArrayList<>();
    private final Runnable clockTick = this::tickClocks;

    /** "67:23", counting on from ESPN's last reading of the clock; "HT" at half-time; ESPN's own text otherwise. */
    static String liveClock(Game g) {
        if (g.halftime) return "HT";
        if (g.clockAt == 0 || g.clockSec <= 0) return g.detail;
        long secs = (long) g.clockSec + (System.currentTimeMillis() - g.clockAt) / 1000L;
        return String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
    }

    private void tickClocks() {
        main.removeCallbacks(clockTick);
        for (java.util.Iterator<Object[]> it = clocks.iterator(); it.hasNext(); ) {
            Object[] c = it.next();
            TextView v = (TextView) c[0];
            // A page that was turned away and dropped takes its boxes with it.
            if (!v.isAttachedToWindow()) { it.remove(); continue; }
            // The Sky bar's box: the minute ticking in green, its dot blinking with it.
            v.setText("skyclock".equals(v.getTag()) ? liveClock((Game) c[1]) : detail((Game) c[1]));
            if ("skyclock".equals(v.getTag()) && v.getParent() instanceof View) {
                View dot = ((View) v.getParent()).findViewWithTag("skydot");
                if (dot != null) dot.setAlpha(dot.getAlpha() > 0.7f ? 0.35f : 1f);
            }
        }
        if (running && !clocks.isEmpty()) main.postDelayed(clockTick, 1000L);
    }

    void close() {
        stop();
        io.shutdownNow();
        logoIo.shutdownNow();
    }

    // ---- data ------------------------------------------------------------------------------------

    private void fetch() {
        main.removeCallbacks(refresh);
        io.execute(() -> {
            List<Game> found = load(leagues);
            if (f1) {
                Game race = f1Game(F1Results.weekend(null));
                if (race != null) found.add(race);
            }
            found.sort((a, b) -> a.state != b.state ? Integer.compare(a.state, b.state)
                    : a.state == 2 ? Long.compare(b.kickoff, a.kickoff) : Long.compare(a.kickoff, b.kickoff));
            boolean live = false;
            for (Game g : found) live |= g.state == 0;
            final boolean anyLive = live;
            main.post(() -> {
                games = found;
                // New goals since the last read become alerts; the first read only learns what is already in.
                for (Game g : found) {
                    for (String[] goal : g.goals) {
                        if (!seenGoals.add(goal[0]) || !primed) continue;
                        // The pill's own goal notice, except for a game the service announces itself (with the
                        // alerts everywhere): that one is the service's long new-style bar.
                        if (alertsOn || !GoalWatchService.follows(context, g)) alerts.add(new Object[]{g, goal});
                    }
                }
                if (!found.isEmpty()) primed = true;
                paginate();
                showNextAlert();
                if (running) main.postDelayed(refresh, anyLive ? LIVE_MS : IDLE_MS);
            });
        });
    }

    /** Today's games for these leagues ("eng.1|PL"), unsorted; a league that fails to load is left out. */
    private static java.util.Set<String> englishIds = new java.util.HashSet<>();
    private static long englishAt;

    /** The Premier League clubs' ESPN ids (read once a day); the last read when ESPN cannot be asked. */
    static synchronized java.util.Set<String> englishClubs() {
        if (!englishIds.isEmpty() && System.currentTimeMillis() - englishAt < 86_400_000L) return englishIds;
        try {
            JSONObject root = new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/teams"));
            JSONArray teams = root.getJSONArray("sports").getJSONObject(0).getJSONArray("leagues").getJSONObject(0).getJSONArray("teams");
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (int i = 0; i < teams.length(); i++) ids.add(teams.getJSONObject(i).getJSONObject("team").optString("id"));
            if (!ids.isEmpty()) { englishIds = ids; englishAt = System.currentTimeMillis(); }
        } catch (Exception ignored) {
        }
        return englishIds;
    }

    static List<Game> load(String[] leagues) {
        return load(leagues, LocalDate.now(ZoneId.systemDefault()));
    }

    /** One day's games for these leagues, unsorted. */
    static List<Game> load(String[] leagues, LocalDate day) {
        ZoneId zone = ZoneId.systemDefault();
        String date = day.format(DateTimeFormatter.BASIC_ISO_DATE);
        List<Game> found = new ArrayList<>();
        for (String entry : leagues) {
            String[] parts = entry.split("\\|", 2);
            String slug = parts[0].trim();
            String tag = parts.length > 1 ? parts[1].trim() : slug;
            try {
                JSONObject board = new JSONObject(get(String.format(FEED, slug, date)));
                String leagueLogo = leagueLogo(board);
                JSONArray events = board.optJSONArray("events");
                for (int i = 0; events != null && i < events.length(); i++) {
                    // One game ESPN sends half-written does not hide the rest of its league.
                    Game g;
                    try {
                        g = parse(events.getJSONObject(i), tag, zone);
                    } catch (Exception oneGame) {
                        continue;
                    }
                    if (g == null) continue;
                    g.leagueLogo = leagueLogo;
                    g.slug = slug;
                    found.add(g);
                }
            } catch (Exception ignored) {
                // one league failing leaves the others
            }
        }
        return found;
    }

    private static Game parse(JSONObject e, String tag, ZoneId zone) throws Exception {
        JSONObject c = e.getJSONArray("competitions").getJSONObject(0);
        JSONObject type = c.getJSONObject("status").getJSONObject("type");
        JSONObject home = null, away = null;
        JSONArray teams = c.getJSONArray("competitors");
        for (int t = 0; t < teams.length(); t++) {
            JSONObject team = teams.getJSONObject(t);
            if ("home".equals(team.optString("homeAway"))) home = team; else away = team;
        }
        if (home == null || away == null) return null;
        Game g = new Game();
        String homeId = home.getJSONObject("team").optString("id"), id = e.optString("id");
        g.id = id;
        JSONArray details = c.optJSONArray("details");
        for (int d = 0; details != null && d < details.length(); d++) {
            JSONObject x = details.getJSONObject(d);
            boolean red = x.optBoolean("redCard") || (x.optJSONObject("type") != null
                    && x.getJSONObject("type").optString("text").toLowerCase(java.util.Locale.ROOT).contains("red card"));
            if (!x.optBoolean("scoringPlay") && !red) continue;
            JSONArray who = x.optJSONArray("athletesInvolved");
            String scorer = who != null && who.length() > 0 ? who.getJSONObject(0).optString("displayName") : "";
            String minute = x.optJSONObject("clock") != null ? x.getJSONObject("clock").optString("displayValue") : "";
            String side = homeId.equals(x.optJSONObject("team") != null ? x.getJSONObject("team").optString("id") : "") ? "home" : "away";
            String kind = x.optBoolean("ownGoal") ? "og" : x.optBoolean("penaltyKick") ? "pen" : "";
            if (red) g.reds.add(new String[]{"R|" + id + "|" + minute + "|" + scorer, scorer, minute, side, ""});
            else g.goals.add(new String[]{id + "|" + minute + "|" + scorer, scorer, minute, side, kind});
        }
        g.league = tag;
        JSONObject ground = c.optJSONObject("venue");
        if (ground != null && !ground.optString("fullName").isEmpty()) g.venue = ground.optString("fullName");
        g.homeId = homeId;
        g.awayId = away.getJSONObject("team").optString("id");
        g.home = home.getJSONObject("team").optString("abbreviation");
        g.away = away.getJSONObject("team").optString("abbreviation");
        g.homeLogo = home.getJSONObject("team").optString("logo", null);
        g.homeName = home.getJSONObject("team").optString("displayName", null);
        g.awayName = away.getJSONObject("team").optString("displayName", null);
        g.homeAlt = color(home.getJSONObject("team").optString("alternateColor", ""));
        g.awayAlt = color(away.getJSONObject("team").optString("alternateColor", ""));
        g.homeColor = color(home.getJSONObject("team").optString("color", ""));
        g.awayColor = color(away.getJSONObject("team").optString("color", ""));
        if (g.awayColor == g.homeColor) g.awayColor = color(away.getJSONObject("team").optString("alternateColor", ""));
        g.awayLogo = away.getJSONObject("team").optString("logo", null);
        g.homeScore = home.optString("score", "0");
        g.awayScore = away.optString("score", "0");
        // ESPN can list a goal a refresh before it raises the score; never show fewer goals than it lists.
        int homeGoals = 0, awayGoals = 0;
        for (String[] goal : g.goals) {
            if ("og".equals(goal[4])) continue;   // which side ESPN credits an own goal to is not certain: skip them
            if ("home".equals(goal[3])) homeGoals++; else awayGoals++;
        }
        g.homeScore = String.valueOf(Math.max(homeGoals, parseInt(g.homeScore)));
        g.awayScore = String.valueOf(Math.max(awayGoals, parseInt(g.awayScore)));
        // ESPN writes "2026-10-25T14:00Z", which Instant.parse refuses (no seconds); OffsetDateTime accepts it.
        ZonedDateTime when = OffsetDateTime.parse(e.getString("date")).toInstant().atZone(zone);
        g.kickoff = when.toInstant().toEpochMilli();
        String state = type.optString("state");
        if ("in".equals(state)) {
            g.state = 0;
            g.detail = type.optString("shortDetail", "LIVE");
            g.clockSec = c.getJSONObject("status").optDouble("clock", 0);
            g.abroad = abroad(c);
            g.broadcasts = broadcasts(c);
            g.clockAt = System.currentTimeMillis();
            g.halftime = type.optString("name").contains("HALFTIME");
        } else if ("pre".equals(state)) {
            g.state = 1;
            g.detail = when.format(DateTimeFormatter.ofPattern("HH:mm"));
        } else {
            g.state = 2;
            g.detail = type.optString("shortDetail", "FT");
        }
        return g;
    }

    /**
     * The game's major incidents from its ESPN match summary: penalties missed or saved, VAR checks and goals ruled
     * out (goals and red cards come from the scoreboard). {unique key, what, player, minute}.
     */
    static List<String[]> incidents(Game g) {
        List<String[]> out = new ArrayList<>();
        if (g.slug == null || g.id == null) return out;
        try {
            JSONObject summary = new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + g.slug
                    + "/summary?event=" + g.id));
            JSONArray events = summary.optJSONArray("keyEvents");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject k = events.getJSONObject(i);
                String type = k.optJSONObject("type") == null ? "" : k.getJSONObject("type").optString("text");
                String what = incident(type);
                if (what == null) continue;
                JSONArray who = k.optJSONArray("participants");
                JSONObject athlete = who != null && who.length() > 0 ? who.getJSONObject(0).optJSONObject("athlete") : null;
                String minute = k.optJSONObject("clock") == null ? "" : k.getJSONObject("clock").optString("displayValue");
                out.add(new String[]{"I|" + g.id + "|" + k.optString("id", type + minute), what,
                        athlete == null ? "" : athlete.optString("displayName"), minute});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** The most recent finished game of the league in the last few days, or null. */
    static Game lastFinished(String slug, String tag, int days) {
        ZoneId zone = ZoneId.systemDefault();
        for (int back = 1; back <= days; back++) {
            String date = LocalDate.now(zone).minusDays(back).format(DateTimeFormatter.BASIC_ISO_DATE);
            try {
                JSONArray events = new JSONObject(get(String.format(FEED, slug, date))).optJSONArray("events");
                for (int i = 0; events != null && i < events.length(); i++) {
                    Game g = parse(events.getJSONObject(i), tag, zone);
                    if (g != null && g.state == 2) { g.slug = slug; return g; }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * For a test: each club's starting eleven from its latest finished game in the last few weeks, so a sample
     * kick-off reminder for (home, away) shows those two clubs' own players; null when either is not found.
     */
    static Side[] latestLineups(String slug, String tag, String home, String away, int days) {
        ZoneId zone = ZoneId.systemDefault();
        Side h = null, a = null;
        for (int back = 1; back <= days && (h == null || a == null); back++) {
            String date = LocalDate.now(zone).minusDays(back).format(DateTimeFormatter.BASIC_ISO_DATE);
            try {
                JSONArray events = new JSONObject(get(String.format(FEED, slug, date))).optJSONArray("events");
                for (int i = 0; events != null && i < events.length(); i++) {
                    Game g = parse(events.getJSONObject(i), tag, zone);
                    if (g == null || g.state != 2) continue;
                    g.slug = slug;
                    boolean hasHome = h == null && (home.equals(g.home) || home.equals(g.away));
                    boolean hasAway = a == null && (away.equals(g.home) || away.equals(g.away));
                    if (!hasHome && !hasAway) continue;
                    Side[] both = lineups(g);
                    if (both == null) continue;
                    if (hasHome) h = home.equals(g.home) ? both[0] : both[1];
                    if (hasAway) a = away.equals(g.home) ? both[0] : both[1];
                }
            } catch (Exception ignored) {
            }
        }
        return h == null || a == null ? null : new Side[]{h, a};
    }

    /** One side's starting eleven: {surname, headshot url, shirt number} per player, in ESPN's order. */
    static final class Side {
        String abbr, crest, formation, name;
        final List<String[]> players = new ArrayList<>();
    }

    /** Both starting line-ups from the match summary, home first; null until they are announced (about an hour before). */
    static Side[] lineups(Game g) {
        if (g.slug == null || g.id == null) return null;
        try {
            JSONObject summary = new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + g.slug
                    + "/summary?event=" + g.id));
            JSONArray rosters = summary.optJSONArray("rosters");
            Side home = null, away = null;
            for (int i = 0; rosters != null && i < rosters.length(); i++) {
                JSONObject r = rosters.getJSONObject(i);
                JSONObject team = r.optJSONObject("team");
                Side side = new Side();
                side.formation = r.optString("formation", "");
                JSONArray list = r.optJSONArray("roster");
                for (int k = 0; list != null && k < list.length(); k++) {
                    JSONObject p = list.getJSONObject(k);
                    if (!p.optBoolean("starter")) continue;
                    JSONObject a = p.getJSONObject("athlete");
                    JSONObject shot = a.optJSONObject("headshot");
                    String photo = shot != null ? shot.optString("href", null)
                            : "https://a.espncdn.com/i/headshots/soccer/players/full/" + a.optString("id") + ".png";
                    String pos = p.optJSONObject("position") == null ? "" : p.getJSONObject("position").optString("abbreviation", "");
                    side.players.add(new String[]{GoalWatchService.surname(a.optString("displayName")), photo, p.optString("jersey", ""), pos,
                            a.optString("displayName"), a.optString("id")});
                }
                if (team != null && team.optString("id").equals(g.homeId)) { side.abbr = g.home; side.crest = g.homeLogo; home = side; }
                else { side.abbr = g.away; side.crest = g.awayLogo; away = side; }
                side.name = team == null ? null : team.optString("displayName", null);
            }
            if (home == null || away == null || home.players.size() < 11 || away.players.size() < 11) return null;
            return new Side[]{home, away};
        } catch (Exception e) {
            return null;
        }
    }

    /** The incidents worth a pop-up, with their icon; null for everything else (substitutions, yellow cards). */
    private static String incident(String type) {
        String t = type.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("penalty") && (t.contains("miss") || t.contains("wide") || t.contains("post") || t.contains("bar"))) return "❌  Pen missed";
        if (t.contains("penalty") && t.contains("saved")) return "🧤  Pen saved";
        if (t.contains("disallowed") || t.contains("cancelled") || t.contains("overturned")) return "🚫  Goal ruled out";
        if (t.contains("var")) return "📺  VAR";
        return null;
    }

    /** The competition's logo for dark backgrounds, else its default one. */
    private static String leagueLogo(JSONObject board) {
        JSONArray leagues = board.optJSONArray("leagues");
        JSONArray list = leagues == null || leagues.length() == 0 ? null : leagues.optJSONObject(0).optJSONArray("logos");
        String fallback = null;
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject l = list.optJSONObject(i);
            String rel = String.valueOf(l.optJSONArray("rel"));
            if (rel.contains("dark")) return l.optString("href", null);
            if (fallback == null) fallback = l.optString("href", null);
        }
        return fallback;
    }

    /**
     * The weekend's box: a session under way first (its top three and the lap), else one that finished in the last
     * twelve hours (its top three), else the next qualifying, sprint or race in the coming four days (day and time).
     */
    static Game f1Game(F1Results.Weekend w) {
        if (w == null) return null;
        long now = System.currentTimeMillis();
        F1Results.Session live = null, done = null, next = null;
        for (F1Results.Session s : w.sessions) {
            if ("in".equals(s.state)) live = s;
            else if ("post".equals(s.state) && s.counts() && now - s.start < 12 * 60 * 60_000L + 3 * 60 * 60_000L) done = s;
            else if ("pre".equals(s.state) && s.counts() && next == null && s.start - now < 4 * 24 * 60 * 60_000L) next = s;
        }
        F1Results.Session s = live != null ? live : done != null ? done : next;
        if (s == null) return null;
        Game g = new Game();
        g.id = "F1|" + s.id;
        g.league = "F1";
        g.leagueLogo = F1Results.F1_LOGO;
        g.home = s.label;
        g.kickoff = s.start;
        g.state = s == live ? 0 : s == done ? 2 : 1;
        if (s == next) {
            ZonedDateTime when = java.time.Instant.ofEpochMilli(s.start).atZone(ZoneId.systemDefault());
            boolean today = when.toLocalDate().equals(LocalDate.now());
            g.detail = when.format(DateTimeFormatter.ofPattern(today ? "HH:mm" : "EEE HH:mm", java.util.Locale.getDefault()));
            // "Mexico City", not "Mexico City Grand Prix": the F1 logo and the session already say what it is.
            g.text = w.name.replace(" Grand Prix", "").replace("Grand Prix", "").trim();
        } else {
            g.drivers = new ArrayList<>();
            for (int i = 0; i < Math.min(3, s.top.size()); i++) {
                g.drivers.add(new String[]{F1Results.code(s.top.get(i)), s.logos.get(i)});
            }
            g.detail = s == live ? (s.isRace() && s.lap > 0 ? "LAP " + s.lap : "LIVE") : "FINAL";
        }
        return g;
    }

    /** "004170" to an opaque colour; 0 when it is not one. */
    static int color(String hex) {
        try { return hex.length() == 6 ? 0xFF000000 | Integer.parseInt(hex, 16) : 0; } catch (Exception e) { return 0; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    // ---- goal alerts ------------------------------------------------------------------------------

    /** Drops the next goal alert over the scores line for ALERT_MS, then lifts it away. */
    private void showNextAlert() {
        if (alertView != null || alerts.isEmpty() || !running || box.getVisibility() != View.VISIBLE) return;
        Object[] next = alerts.poll();
        Game g = (Game) next[0];
        String[] goal = (String[]) next[1];
        boolean homeGoal = "home".equals(goal[3]);
        LinearLayout a = new LinearLayout(context);
        a.setOrientation(LinearLayout.HORIZONTAL);
        a.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColors(new int[]{0xE6143D2A, 0xE60E2A1D});
        bg.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM);
        a.setBackground(bg);
        a.setPadding(dp(14), 0, dp(14), 0);
        TextView tag = label(goalTag(this.games, g));
        a.addView(tag);
        a.addView(logoView(homeGoal ? g.homeLogo : g.awayLogo, dp(12)));
        SpannableStringBuilder line = new SpannableStringBuilder();
        line.append(goal[1]);
        line.setSpan(new StyleSpan(Typeface.BOLD), 0, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        line.append("  ").append(goal[2]);
        if (!goal[4].isEmpty()) line.append(goal[4].equals("pen") ? " (pen)" : " (og)");
        int at = line.length();
        line.append("     ").append(g.home).append("  ").append(g.homeScore).append("–").append(g.awayScore)
                .append("  ").append(g.away);
        line.setSpan(new ForegroundColorSpan(0xCCFFFFFF), at, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        a.addView(label(line), margin(dp(8)));
        a.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        int h = dp(30);
        a.setTranslationY(-h);
        box.addView(a);
        alertView = a;
        a.animate().translationY(0f).setDuration(FLIP_MS).start();
        main.postDelayed(() -> a.animate().translationY(-h).alpha(0f).setDuration(FLIP_MS).withEndAction(() -> {
            box.removeView(a);
            alertView = null;
            showNextAlert();
        }).start(), ALERT_MS);
    }

    private static CharSequence goalTag(List<Game> latest, Game g) {
        // The score from the newest refresh of this game, as the alert goes up.
        for (Game n : latest) if (n.id != null && n.id.equals(g.id)) { g.homeScore = n.homeScore; g.awayScore = n.awayScore; }
        SpannableStringBuilder s = new SpannableStringBuilder("⚽");
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(0xFF4ADE80), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    // ---- pages -----------------------------------------------------------------------------------

    /** Splits the games into pages of as many boxes as fit the width, and shows the current one. */
    private void paginate() {
        pages.clear();
        int width = box.getLayoutParams().width;
        if (games.isEmpty() || width <= 0) {
            box.setVisibility(View.GONE);
            box.removeAllViews();
            shownRow = null;
            alertView = null;
            pageShown();
            return;
        }
        int gap = dp(10);
        List<Game> current = new ArrayList<>();
        int used = 0;
        for (Game g : games) {
            int w = boxWidth(g);
            if (!current.isEmpty() && used + gap + w > width) {
                pages.add(current);
                current = new ArrayList<>();
                used = 0;
            }
            used += (current.isEmpty() ? 0 : gap) + w;
            current.add(g);
        }
        if (!current.isEmpty()) pages.add(current);
        if (page >= pages.size()) page = 0;
        // A refresh of the same games (the Sky bar): their numbers changed where they stand, a new score or time
        // sliding up into place; nothing built or laid out again (a new game, a kick-off or a final whistle rebuilds).
        if (skyBar() && shownRow != null && box.getVisibility() == View.VISIBLE && samePage(shownRow, pages.get(page))) {
            update(shownRow, pages.get(page));
            pageShown();
            return;
        }
        int focused = focusedIndex();
        box.setVisibility(View.VISIBLE);
        if (shownRow != null) box.removeView(shownRow);
        shownRow = row(pages.get(page));
        box.addView(shownRow, 0);   // under a goal alert, if one is showing
        if (focused >= 0) focusBox(Math.min(focused, shownRow.getChildCount() - 1));
        pageShown();
    }

    /** Whether one of the game boxes has Android's focus. */
    boolean hasFocus() {
        return box.hasFocus();
    }

        /** Focus the first game box; false when there are no scores on screen. */
    boolean focus() {
        if (shownRow == null || box.getVisibility() != View.VISIBLE || shownRow.getChildCount() == 0) return false;
        return shownRow.getChildAt(0).requestFocus();
    }

    /**
     * adb only (MainActivity test_live_ticker): a live game first in the bar, its clock ticking from 67:12, to see how a
     * live game looks (Liverpool 2–1 Brighton in the Premier League).
     */
    void testLive() {
        Game g = new Game();
        g.league = "PL";
        g.slug = "eng.1";
        g.leagueLogo = "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/23.png";
        g.home = "LIV";
        g.away = "BHA";
        g.homeName = "Liverpool";
        g.awayName = "Brighton & Hove Albion";
        g.homeLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/364.png";
        g.awayLogo = "https://a.espncdn.com/i/teamlogos/soccer/500/331.png";
        g.homeScore = "2";
        g.awayScore = "1";
        g.state = 0;
        g.detail = "67'";
        g.clockSec = 67 * 60 + 12;
        g.clockAt = System.currentTimeMillis();
        List<Game> with = new ArrayList<>(games);
        with.add(0, g);
        games = with;
        page = 0;
        paginate();
        tickClocks();
    }

    /** Focus the last game (coming in from the right): the last page's last box; false when none is on screen. */
    boolean focusLast() {
        if (shownRow == null || box.getVisibility() != View.VISIBLE || pages.isEmpty()) return false;
        if (page != pages.size() - 1) {
            page = pages.size() - 1;
            box.removeView(shownRow);
            shownRow = row(pages.get(page));
            box.addView(shownRow, 0);
            pageShown();
        }
        return shownRow.getChildCount() > 0 && shownRow.getChildAt(shownRow.getChildCount() - 1).requestFocus();
    }

    private int focusedIndex() {
        if (shownRow == null) return -1;
        for (int i = 0; i < shownRow.getChildCount(); i++) if (shownRow.getChildAt(i).hasFocus()) return i;
        return -1;
    }

    private void focusBox(int index) {
        if (shownRow != null && index >= 0 && index < shownRow.getChildCount()) shownRow.getChildAt(index).requestFocus();
    }

    /** Left/Right past a page's end: the previous or next page, with the focus on its nearest box. */
    private void turnPage(boolean forward) {
        if (pages.size() < 2) return;
        page = (page + (forward ? 1 : pages.size() - 1)) % pages.size();
        if (shownRow != null) box.removeView(shownRow);
        shownRow = row(pages.get(page));
        box.addView(shownRow, 0);
        focusBox(forward ? 0 : shownRow.getChildCount() - 1);
        pageShown();
    }

    /**
     * The next or previous game slid in by hand (the Sky ticker): the one showing glides out to the side and fades as
     * the next glides in from the other, inside the bar's own edges; the focus goes onto it.
     */
    private void slidePage(boolean forward) {
        if (pages.size() < 2 || shownRow == null) return;
        page = (page + (forward ? 1 : pages.size() - 1)) % pages.size();
        LinearLayout out = shownRow;
        LinearLayout in = row(pages.get(page));
        slideRows(out, in, forward);
        shownRow = in;
        focusBox(forward ? 0 : in.getChildCount() - 1);
        pageShown();
        schedulePage();
    }

    /** The slides' timing and ease: the next game in over 340 ms, the last out over 260 (quick out, long settle). */
    private static final long SLIDE_IN_MS = 340, SLIDE_OUT_MS = 260;
    private static final android.view.animation.PathInterpolator SLIDE = new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f);

    /**
     * One row of games slid out to the side and faded as the next slides in from the other (forward: out to the left,
     * in from the right), inside the bar's own edges. The frosted ground stays where it is on the screen while the
     * games move across it (each box's ground is drawn for where it is now, so it is redrawn as they go).
     */
    private void slideRows(LinearLayout out, LinearLayout in, boolean forward) {
        float w = box.getWidth() > 0 ? box.getWidth() : dp(200);
        // Option F: the next game's wedges charge in from the ends over the last one sliding a little aside and going.
        box.addView(in);
        out.animate().cancel();
        out.animate().translationX(forward ? -w * 0.18f : w * 0.18f).alpha(0f).setDuration(SLIDE_OUT_MS / 2 + 60).setInterpolator(SLIDE)
                .withEndAction(() -> box.removeView(out)).start();
        charge(in);
    }

    private void schedulePage() {
        main.removeCallbacks(flip);
        if (running) main.postDelayed(flip, PAGE_MS);
    }

    /**
     * The next page of games: under the Sky bar it slides in from the right as the one showing slides out to the left
     * (as Right by hand does); the pills' page slides up and out while the next one rises in from below.
     */
    private void nextPage() {
        // Never flip the page out from under a box the viewer is on.
        if (running && pages.size() > 1 && shownRow != null && !box.hasFocus() && alertView == null) {
            page = (page + 1) % pages.size();
            LinearLayout out = shownRow;
            LinearLayout in = row(pages.get(page));
            if (skyBar()) {
                slideRows(out, in, true);
                shownRow = in;
                pageShown();
                schedulePage();
                return;
            }
            int h = box.getHeight() > 0 ? box.getHeight() : dp(30);
            in.setTranslationY(h);
            in.setAlpha(0f);
            box.addView(in, 0);
            out.animate().translationY(-h).alpha(0f).setDuration(FLIP_MS)
                    .withEndAction(() -> box.removeView(out)).start();
            in.animate().translationY(0f).alpha(1f).setDuration(FLIP_MS).start();
            shownRow = in;
            pageShown();
        }
        schedulePage();
    }

    private LinearLayout row(List<Game> list) {
        // The page flip fades the row as separate pills (they never overlap), not as an offscreen copy per frame.
        LinearLayout row = new LinearLayout(context) {
            @Override public boolean hasOverlappingRendering() {
                return false;
            }
        };
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < list.size(); i++) {
            Game g = list.get(i);
            if (skyBar()) {
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, barH(), 1f);
                if (i > 0) sl.leftMargin = dp(10);
                row.addView(skyBox(g, i, list.size()), sl);
                continue;
            }
            LinearLayout b = new LinearLayout(context);
            b.setOrientation(LinearLayout.HORIZONTAL);
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setBackgroundResource(glassRes());
            if (LightUi.on()) b.setElevation(dp(3));
            b.setPadding(dp(12), 0, dp(12), 0);
            b.setFocusable(true);
            final int index = i, count = list.size();
            android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
            ring.setCornerRadius(dp(16));
            ring.setStroke(dp(2), LightUi.ink(0xFFFFFFFF));
            ring.setColor(LightUi.on() ? 0x0A000000 : 0x26FFFFFF);
            b.setOnFocusChangeListener((v, has) -> v.setForeground(has ? ring : null));
            b.setOnClickListener(v -> host.onScoresOpen(g));
            b.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                switch (keyCode) {
                    case android.view.KeyEvent.KEYCODE_DPAD_DOWN: host.onScoresDown(); return true;
                    case android.view.KeyEvent.KEYCODE_DPAD_UP: return true;
                    case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                        if (index == 0) turnPage(false); else focusBox(index - 1);
                        return true;
                    case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (index == count - 1) turnPage(true); else focusBox(index + 1);
                        return true;
                    default: return false;
                }
            });
            if (g.leagueLogo != null) b.addView(logoView(g.leagueLogo, 0));
            else b.addView(label(league(g)));
            if (g.drivers != null || g.text != null) {
                // F1: the session, then its top three with their team logos (or the Grand Prix, before it starts).
                b.addView(label(bold(g.home)), margin(dp(8)));
                if (g.drivers != null) {
                    for (String[] d : g.drivers) {
                        b.addView(logoView(d[1], dp(8)));
                        b.addView(label(d[0]), margin(dp(3)));
                    }
                } else {
                    // The race's name gives way (shortened, "…") when the box is narrow: the time after it stays whole.
                    TextView name = label(g.text);
                    name.setSingleLine(true);
                    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    name.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    nl.leftMargin = dp(8);
                    b.addView(name, nl);
                }
            } else {
                b.addView(logoView(g.homeLogo, dp(12)));
                b.addView(label(teams(g)), margin(dp(6)));
                b.addView(logoView(g.awayLogo, dp(6)));
            }
            TextView when = label(detail(g));
            b.addView(when, margin(dp(8)));
            if (g.state == 0 && g.drivers == null && g.text == null) {
                clocks.add(new Object[]{when, g});
                main.removeCallbacks(clockTick);
                main.postDelayed(clockTick, 1000L);
            }
            // Each box grows in proportion to what it holds, so a page always fills the whole line.
            b.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(30), equal ? 1f : boxWidth(g));
            if (i > 0) lp.leftMargin = dp(10);
            row.addView(b, lp);
        }
        row.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barH()));
        return row;
    }

    /**
     * One game as option F, "Wedges": on a dark ground with square corners, a slanted wedge in each club's own colour
     * from either end (the competition's mark, the home crest and code on the left one; the away code and crest on the
     * right), the score on the dark plate between them in bold italic, then after the whole fixture the time, FT, or the
     * live minute in green with a green dot that blinks as it ticks. Changing games, the wedges charge in from the ends
     * and the plate flips in (charge); a goal, the scorer's wedge lunges across with a flash as the score rolls up
     * (lunge). Focused: a white rim inside its own edge (it stays its size, so nothing is cut off). F1: its red wedge
     * with the session, then the leaders with their team logos.
     */
    private FrameLayout skyBox(Game g, int index, int count) {
        // Square-edged, as the notifications over the apps (not a pill).
        float r = SkyStyle.corner(context);
        FrameLayout b = new FrameLayout(context) {
            @Override public boolean hasOverlappingRendering() {
                return false;
            }
        };
        android.graphics.drawable.GradientDrawable ground = new android.graphics.drawable.GradientDrawable();
        ground.setColor(0xFF0C0D1D);
        ground.setCornerRadius(r);
        ground.setStroke(Math.max(1, sk(1)), 0x1FFFFFFF);
        b.setBackground(ground);
        Shown shown = new Shown();
        shown.key = key(g);
        shown.state = g.state;
        shown.f1 = g.drivers != null || g.text != null;
        shown.hs = g.homeScore;
        shown.as = g.awayScore;
        b.setTag(shown);
        b.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        b.setClipToOutline(true);
        b.setFocusable(true);
        android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
        ring.setCornerRadius(r);
        ring.setColor(0x1FFFFFFF);
        ring.setStroke(sk(2.5f), 0xF2FFFFFF);
        // Focused: the box lit and a white rim drawn inside its own edge (it stays its size: grown, the rim was cut
        // off by the bar's box).
        b.setOnFocusChangeListener((v, has) -> v.setForeground(has ? ring : null));
        b.setOnClickListener(v -> host.onScoresOpen(g));
        // Left and Right slide through the games one by one; past the first or the last, out to the top row's
        // neighbour (the Control Centre to the left, the weather to the right). Up and Down back to the top row.
        b.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
                case android.view.KeyEvent.KEYCODE_DPAD_UP:
                    host.onScoresDown();
                    return true;
                case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                    if (index > 0) focusBox(index - 1);
                    else if (page > 0) slidePage(false);
                    else host.onScoresEdge(false);
                    return true;
                case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (index < count - 1) focusBox(index + 1);
                    else if (page < pages.size() - 1) slidePage(true);
                    else host.onScoresEdge(true);
                    return true;
                default: return false;
            }
        });
        boolean f1 = shown.f1;
        int homeCol = f1 ? SkyStyle.F1_RED : wedgeColour(g.homeColor, 0xFF3A4CC0);
        int awayCol = wedgeColour(g.awayColor == g.homeColor ? (g.awayAlt != 0 ? g.awayAlt : 0) : g.awayColor, 0xFF6B7280);
        int homeInk = lightGround(homeCol) ? 0xFF0C0D1D : 0xFFFFFFFF, awayInk = lightGround(awayCol) ? 0xFF0C0D1D : 0xFFFFFFFF;
        // The wedges, under everything else.
        View hw = new View(context);
        hw.setBackground(new Wedge(homeCol, 0f, 1f, 0.85f, 0f));
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(sk(f1 ? 150 : 158), ViewGroup.LayoutParams.MATCH_PARENT);
        b.addView(hw, hl);
        shown.homeWedge = hw;
        if (!f1) {
            View aw = new View(context);
            aw.setBackground(new Wedge(awayCol, 0.16f, 1f, 0.84f, 0f));
            FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(sk(148), ViewGroup.LayoutParams.MATCH_PARENT);
            al.leftMargin = sk(208);
            b.addView(aw, al);
            shown.awayWedge = aw;
        }
        // On the home wedge: the competition's mark, then the crest and code (or F1's session).
        LinearLayout left = new LinearLayout(context);
        left.setGravity(Gravity.CENTER_VERTICAL);
        if (g.leagueLogo != null) {
            ImageView lv = new ImageView(context);
            lv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            loadLogo(g.leagueLogo, lv);
            left.addView(lv, new LinearLayout.LayoutParams(sk(f1 ? 30 : 18), sk(20)));
        } else {
            left.addView(wedgeText(league(g), 11, homeInk, 800));
        }
        if (f1) {
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sl.leftMargin = sk(8);
            left.addView(wedgeText(g.home, 16, homeInk, 900), sl);
        } else {
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(sk(24), sk(24));
            cl.leftMargin = sk(7);
            left.addView(glassCrest(g.homeLogo), cl);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.leftMargin = sk(7);
            left.addView(wedgeText(g.home, 18, homeInk, 900), tl);
        }
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ll.leftMargin = sk(9);
        b.addView(left, ll);
        shown.homeGroup = left;
        if (f1) {
            // F1: the leaders with their team logos (or the Grand Prix's name before it starts), on the dark ground.
            LinearLayout mid = new LinearLayout(context);
            mid.setGravity(Gravity.CENTER_VERTICAL);
            if (g.drivers != null) {
                for (String[] d : g.drivers) {
                    ImageView tl = new ImageView(context);
                    tl.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    loadLogo(d[1], tl);
                    LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(sk(18), sk(18));
                    il.leftMargin = mid.getChildCount() == 0 ? 0 : sk(9);
                    mid.addView(tl, il);
                    LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    nl.leftMargin = sk(4);
                    mid.addView(wedgeText(d[0], 15, 0xFFFFFFFF, 800), nl);
                }
            } else {
                TextView name = wedgeText(g.text, 15, 0xE6FFFFFF, 700);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                mid.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            ml.leftMargin = sk(162);
            ml.rightMargin = sk(70);
            b.addView(mid, ml);
            shown.awayGroup = mid;
        } else {
            // The score (or "v" before kick-off) on the dark plate between the wedges.
            TextView plate = wedgeText(plateText(g), g.state == 1 ? 18 : 25, g.state == 1 ? 0xCCFFFFFF : 0xFFFFFFFF, 900);
            plate.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(sk(90), ViewGroup.LayoutParams.MATCH_PARENT);
            pl.leftMargin = sk(138);
            b.addView(plate, pl);
            shown.plate = plate;
            // On the away wedge: its code, then its crest.
            LinearLayout right = new LinearLayout(context);
            right.setGravity(Gravity.CENTER_VERTICAL);
            right.addView(wedgeText(g.away, 18, awayInk, 900));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(sk(24), sk(24));
            cl.leftMargin = sk(7);
            right.addView(glassCrest(g.awayLogo), cl);
            FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            rl.leftMargin = sk(236);
            b.addView(right, rl);
            shown.awayGroup = right;
        }
        // After the whole fixture: the time, FT, or live the minute in green with a dot that blinks as it ticks.
        boolean live = g.state == 0 && !f1;
        LinearLayout end = new LinearLayout(context);
        end.setGravity(Gravity.CENTER);
        if (live) {
            View dot = new View(context);
            dot.setTag("skydot");
            android.graphics.drawable.GradientDrawable dg = new android.graphics.drawable.GradientDrawable();
            dg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dg.setColor(LIVE_GREEN);
            dot.setBackground(dg);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(sk(6), sk(6));
            dl.rightMargin = sk(4);
            end.addView(dot, dl);
        }
        TextView when = wedgeText(detail(g), 15, live ? LIVE_GREEN : 0xE6FFFFFF, 800);
        when.setTag("skyclock");
        shown.when = when;
        // Same-width digits: the live clock ticking each second never moves the box's edge.
        when.setFontFeatureSettings("tnum");
        end.addView(when);
        b.addView(end, new FrameLayout.LayoutParams(sk(66), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        if (live) {
            clocks.add(new Object[]{when, g});
            main.removeCallbacks(clockTick);
            main.postDelayed(clockTick, 1000L);
        }
        return b;
    }

    /** The live minute's green (the clock ticking), and its dot's. */
    private static final int LIVE_GREEN = 0xFF4ADE80;

    /** The plate's words: the score once it has started ("2–1"), "v" before kick-off. */
    private static String plateText(Game g) {
        return g.state == 1 ? "v" : g.homeScore + "–" + g.awayScore;
    }

    /** Bold italic words on the wedges and the plate (Outfit, as the notifications). */
    private TextView wedgeText(CharSequence text, float designPx, int colour, int weight) {
        TextView t = skyText(text, designPx, colour, weight);
        Typeface base = ScoreCard.outfit(context, weight);
        if (android.os.Build.VERSION.SDK_INT >= 28) t.setTypeface(Typeface.create(base, weight, true));
        else t.setTypeface(base, Typeface.ITALIC);
        return t;
    }

    /** A club's colour made fit for a wedge carrying its words: near black lifted, a white kit a slate grey. */
    private static int wedgeColour(int c, int fallback) {
        if (c == 0) c = fallback;
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(c | 0xFF000000, hsv);
        if (hsv[2] < 0.3f) hsv[2] = 0.36f;
        if (hsv[1] < 0.12f && hsv[2] > 0.8f) hsv[2] = 0.42f;
        return android.graphics.Color.HSVToColor(hsv);
    }

    /** Whether a wedge's colour is light enough (a bright yellow) that its words go dark. */
    private static boolean lightGround(int c) {
        double l = (0.2126 * android.graphics.Color.red(c) + 0.7152 * android.graphics.Color.green(c) + 0.0722 * android.graphics.Color.blue(c)) / 255.0;
        return l > 0.62;
    }

    /**
     * A slanted wedge in a club's colour, a little lighter at its top than its foot; its four corners as shares of
     * its width (top-left, top-right, bottom-right, bottom-left). Its path and shader made when its size changes.
     */
    private static final class Wedge extends android.graphics.drawable.Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private final int colour;
        private final float tl, tr, br, bl;

        Wedge(int colour, float tl, float tr, float br, float bl) {
            this.colour = colour;
            this.tl = tl;
            this.tr = tr;
            this.br = br;
            this.bl = bl;
        }

        @Override protected void onBoundsChange(android.graphics.Rect b) {
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(colour, hsv);
            float v = hsv[2];
            hsv[2] = Math.min(1f, v * 1.12f);
            int top = android.graphics.Color.HSVToColor(hsv);
            hsv[2] = v * 0.7f;
            int foot = android.graphics.Color.HSVToColor(hsv);
            fill.setShader(new android.graphics.LinearGradient(0, b.top, 0, b.bottom, top, foot, android.graphics.Shader.TileMode.CLAMP));
            float w = b.width();
            path.reset();
            path.moveTo(b.left + tl * w, b.top);
            path.lineTo(b.left + tr * w, b.top);
            path.lineTo(b.left + br * w, b.bottom);
            path.lineTo(b.left + bl * w, b.bottom);
            path.close();
        }

        @Override public void draw(android.graphics.Canvas c) {
            c.drawPath(path, fill);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /**
     * A new game's box arriving (option F): its wedges charge in from the two ends, its words come up on them and its
     * score plate flips in. Cheap: the views' own moves, no picture made.
     */
    private void charge(LinearLayout row) {
        float w = sk(260);
        for (int i = 0; i < row.getChildCount(); i++) {
            Object t = row.getChildAt(i).getTag();
            if (!(t instanceof Shown)) continue;
            Shown sh = (Shown) t;
            if (sh.homeWedge != null) {
                sh.homeWedge.setTranslationX(-w);
                sh.homeWedge.animate().translationX(0f).setDuration(SLIDE_IN_MS).setInterpolator(SLIDE).start();
            }
            if (sh.awayWedge != null) {
                sh.awayWedge.setTranslationX(w);
                sh.awayWedge.animate().translationX(0f).setDuration(SLIDE_IN_MS).setInterpolator(SLIDE).start();
            }
            for (View v : new View[]{sh.homeGroup, sh.awayGroup, sh.when}) {
                if (v == null) continue;
                v.setAlpha(0f);
                v.animate().alpha(1f).setStartDelay(130).setDuration(200).start();
            }
            if (sh.plate != null) {
                sh.plate.setCameraDistance(sk(2400));
                sh.plate.setRotationX(90f);
                sh.plate.setAlpha(0f);
                sh.plate.animate().rotationX(0f).alpha(1f).setStartDelay(150).setDuration(240).setInterpolator(SLIDE).start();
            }
        }
    }

    /** A goal (option F): the scorer's wedge lunges across towards the plate and back, under a quick flash of light. */
    private void lunge(FrameLayout box, View wedge, boolean home) {
        if (wedge == null) return;
        if (covered) { heldRolls.add(() -> lunge(box, wedge, home)); return; }
        wedge.setPivotX(home ? 0f : wedge.getWidth());
        wedge.animate().cancel();
        wedge.animate().scaleX(1.28f).setDuration(150).setInterpolator(SLIDE).withEndAction(() ->
                wedge.animate().scaleX(1f).setDuration(320).setInterpolator(SLIDE).withEndAction(null).start()).start();
        View flash = new View(context);
        flash.setBackgroundColor(0xFFFFFFFF);
        flash.setAlpha(0f);
        box.addView(flash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        flash.animate().alpha(0.35f).setDuration(90).withEndAction(() ->
                flash.animate().alpha(0f).setDuration(260).withEndAction(() -> box.removeView(flash)).start()).start();
    }

    /** Which side's score went up between two readings (1 home, 2 away, 0 neither). */
    private static int scored(String hsWas, String asWas, Game g) {
        try {
            if (g.homeScore != null && hsWas != null && Integer.parseInt(g.homeScore.trim()) > Integer.parseInt(hsWas.trim())) return 1;
            if (g.awayScore != null && asWas != null && Integer.parseInt(g.awayScore.trim()) > Integer.parseInt(asWas.trim())) return 2;
        } catch (NumberFormatException ignored) {
            // not numbers yet
        }
        return 0;
    }

    // ---- the Sky bar's refresh in place -------------------------------------------------------------------------

    /** What a Sky box shows (its tag): which game, in what state, and its score and time words. */
    private static final class Shown {
        String key;
        int state;
        boolean f1;
        TextView plate, when;
        /** Option F's moving parts: the wedges, the words on them, and the score as last shown. */
        View homeWedge, awayWedge, homeGroup, awayGroup;
        String hs, as;
    }

    /** A game told apart from the others on refresh: ESPN's id, else its league and clubs. */
    private static String key(Game g) {
        return g.id != null ? g.id : g.league + "|" + g.home + "|" + g.away + "|" + g.text;
    }

    /** Whether the row already shows these games, in this order and each in the same state (football only). */
    private boolean samePage(LinearLayout row, List<Game> list) {
        if (row.getChildCount() != list.size()) return false;
        for (int i = 0; i < list.size(); i++) {
            Object tag = row.getChildAt(i).getTag();
            Game g = list.get(i);
            if (!(tag instanceof Shown)) return false;
            Shown sh = (Shown) tag;
            if (sh.f1 || g.drivers != null || g.text != null || sh.state != g.state || !sh.key.equals(key(g))) return false;
        }
        return true;
    }

    /** The row's games refreshed where they stand: a changed score or time slides up into place; the live clock just ticks. */
    private void update(LinearLayout row, List<Game> list) {
        for (int i = 0; i < list.size(); i++) {
            View b = row.getChildAt(i);
            Shown sh = (Shown) b.getTag();
            Game g = list.get(i);
            b.setOnClickListener(v -> host.onScoresOpen(g));
            if (sh.plate != null) {
                String t = plateText(g);
                if (!t.contentEquals(sh.plate.getText())) {
                    roll(sh.plate, t);
                    // A goal: the scorer's wedge lunges across with a flash as the score rolls up.
                    int side = scored(sh.hs, sh.as, g);
                    if (side != 0 && b instanceof FrameLayout) lunge((FrameLayout) b, side == 1 ? sh.homeWedge : sh.awayWedge, side == 1);
                }
                sh.hs = g.homeScore;
                sh.as = g.awayScore;
            }
            if (g.state == 0) {
                // The clock reads the fresh game from now on (its minute as ESPN last read it), ticking as before.
                for (Object[] c : clocks) if (c[0] == sh.when) c[1] = g;
            } else if (sh.when != null) {
                String d = detail(g).toString();
                if (!d.contentEquals(sh.when.getText())) roll(sh.when, d);
            }
        }
    }

    /** While an alert stands over the bar (cover): the bar's changes wait for it to show again. */
    private boolean covered;
    private final List<Runnable> heldRolls = new ArrayList<>();

    /** New words slid up into place: the old out upward and faded, the new in from below (about a quarter second). */
    private void roll(TextView v, String text) {
        if (covered) {
            // Seen once the bar is back (the words set now, so a later refresh compares with them).
            CharSequence was = v.getText();
            v.setText(text);
            heldRolls.add(() -> { v.setText(was); roll(v, text); });
            return;
        }
        float d = barH() * 0.7f;
        v.animate().cancel();
        v.animate().translationY(-d).alpha(0f).setDuration(110).setInterpolator(new android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f))
                .setUpdateListener(null).withEndAction(() -> {
                    v.setText(text);
                    v.setTranslationY(d);
                    v.animate().translationY(0f).alpha(1f).setDuration(160).setInterpolator(SLIDE).withEndAction(null).start();
                }).start();
    }

    // ---- the Sky bar's ground: the screen behind it, blurred -----------------------------------------------------

    /**
     * The frosted ground behind the Sky boxes: the part of Home's background under the bar (from the picture Home
     * has just made of it, a quarter of the screen's size), blurred a little more, its colours lifted and a touch
     * brighter, kept small (a quarter of the bar's own size) and drawn up under the boxes; the one before it kept
     * while the new one fades in, in step with Home's own background. Where each sits, in the bar's own pixels, and
     * how dark a wash the words need on it (more on a bright picture).
     */
    private Bitmap frostNow, frostWas, backdropSource;
    private final android.graphics.RectF frostAt = new android.graphics.RectF(), frostWasAt = new android.graphics.RectF();
    private float frostDark, frostWasDark, frostFade = 1f;
    private android.animation.ValueAnimator frostAnim;

    /**
     * Home's background has changed to bg (fading over ms, at once for 0): the bar's ground follows, cut from it.
     * Called on the main thread; the cut is a few thousand pixels, so it costs next to nothing.
     */
    void backdrop(Bitmap bg, int ms) {
        backdropSource = bg;
        // Only the Sky bar is frosted (the pills elsewhere keep their own glass).
        if (skyBar()) frostFrom(bg, ms);
    }

    private void frostFrom(Bitmap bg, int ms) {
        if (bg == null || bg.isRecycled()) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
        if (lp.width <= 0 || lp.height <= 0) return;
        View parent = (View) box.getParent();
        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int rootW = parent != null && parent.getWidth() > 0 ? parent.getWidth() : dm.widthPixels;
        int rootH = parent != null && parent.getHeight() > 0 ? parent.getHeight() : dm.heightPixels;
        float s = bg.getWidth() / (float) rootW;
        // The bar's place on the screen with a margin for the blur, clamped to the screen; cut at a quarter size.
        int pad = sk(16);
        int l = Math.max(0, lp.leftMargin - pad), t = Math.max(0, lp.topMargin - pad);
        int r = Math.min(rootW, lp.leftMargin + lp.width + pad), btm = Math.min(rootH, lp.topMargin + lp.height + pad);
        int w = Math.max(2, (r - l) / 4), h = Math.max(2, (btm - t) / 4);
        if (r <= l || btm <= t) return;
        Bitmap out;
        int[] px = new int[w * h];
        try {
            out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(out);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
            cm.setSaturation(1.3f);
            cm.postConcat(new android.graphics.ColorMatrix(new float[]{
                    1.08f, 0, 0, 0, 10, 0, 1.08f, 0, 0, 10, 0, 0, 1.08f, 0, 10, 0, 0, 0, 1, 0}));
            p.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
            c.drawBitmap(bg, new android.graphics.Rect(Math.round(l * s), Math.round(t * s), Math.round(r * s), Math.round(btm * s)),
                    new android.graphics.Rect(0, 0, w, h), p);
            out.getPixels(px, 0, w, 0, 0, w, h);
        } catch (RuntimeException e) {
            return;
        }
        HomeV2.boxBlur(px, w, h, 2, 2);
        long lum = 0;
        for (int v : px) lum += ((v >> 16 & 0xFF) * 299 + (v >> 8 & 0xFF) * 587 + (v & 0xFF) * 114) / 1000;
        float bright = lum / (float) px.length / 255f;
        out.setPixels(px, 0, w, 0, 0, w, h);
        out.prepareToDraw();
        // White words read on it: a dark wash only as the picture under the bar gets bright, none on a dark one.
        float dark = Math.max(0f, Math.min(0.45f, (bright - 0.4f) * 1.4f));
        if (frostAnim != null) frostAnim.cancel();
        if (frostNow == null || ms <= 0) {
            frostWas = null;
            frostFade = 1f;
        } else {
            frostWas = frostNow;
            frostWasAt.set(frostAt);
            frostWasDark = frostDark;
            frostFade = 0f;
        }
        frostNow = out;
        frostAt.set(l - lp.leftMargin, t - lp.topMargin, r - lp.leftMargin, btm - lp.topMargin);
        frostDark = dark;
        if (frostWas != null) {
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(ms);
            va.setInterpolator(new android.view.animation.LinearInterpolator());   // as Home's own cross-fade
            va.addUpdateListener(a -> { frostFade = (float) a.getAnimatedValue(); frostMoved(null); });
            va.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    frostFade = 1f;
                    frostWas = null;
                    frostMoved(null);
                }
            });
            frostAnim = va;
            va.start();
        }
        frostMoved(null);
    }

    /** The boxes' grounds drawn again (of one row, or of every row in the bar): the ground has moved or changed. */
    private void frostMoved(LinearLayout only) {
        for (int i = 0; i < box.getChildCount(); i++) {
            View row = box.getChildAt(i);
            if (only != null && row != only) continue;
            if (!(row instanceof ViewGroup)) continue;
            ViewGroup g = (ViewGroup) row;
            for (int k = 0; k < g.getChildCount(); k++) {
                android.graphics.drawable.Drawable d = g.getChildAt(k).getBackground();
                if (d instanceof Frost) d.invalidateSelf();
            }
        }
    }

    /**
     * A Sky box's ground: frosted glass over the screen. The blurred cut of what lies behind it (cross-faded while it
     * changes), a dark wash as the picture needs, a light white wash and an even hairline rim all round (no sheen or
     * shine along the top: it read as a white streak across the box's head). Drawn for where its box
     * is now in the bar, so the picture stays put on the screen while the games slide over it. The box clips it to its
     * square corners. Its shaders made when its size changes, never per frame.
     */
    private final class Frost extends android.graphics.drawable.Drawable {
        private final Paint pic = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG), fill = new Paint(),
                rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float radius, line;
        private final android.graphics.RectF edge = new android.graphics.RectF(), dst = new android.graphics.RectF();

        Frost(float radius, float line) {
            this.radius = radius;
            this.line = Math.max(1f, line);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(this.line);
            rim.setColor(0x47FFFFFF);
        }

        @Override protected void onBoundsChange(android.graphics.Rect b) {
            edge.set(b.left + line / 2f, b.top + line / 2f, b.right - line / 2f, b.bottom - line / 2f);
        }

        @Override public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            // Where the box is in the bar now (its row may be sliding).
            float ox = 0, oy = 0;
            if (getCallback() instanceof View) {
                View v = (View) getCallback();
                ox = v.getLeft() + v.getTranslationX();
                oy = v.getTop() + v.getTranslationY();
                if (v.getParent() instanceof View && v.getParent() != box) {
                    View row = (View) v.getParent();
                    ox += row.getLeft() + row.getTranslationX();
                    oy += row.getTop() + row.getTranslationY();
                }
            }
            Bitmap now = frostNow, was = frostWas;
            if (now == null) {
                // No picture of the background yet (or the plain navy one): plain light frosted glass.
                fill.setColor(0x33000000);
                c.drawRect(b, fill);
                fill.setColor(0x29FFFFFF);
                c.drawRect(b, fill);
            } else {
                float fade = was == null ? 1f : frostFade;
                if (was != null) {
                    dst.set(frostWasAt);
                    dst.offset(-ox, -oy);
                    pic.setAlpha(255);
                    c.drawBitmap(was, null, dst, pic);
                }
                dst.set(frostAt);
                dst.offset(-ox, -oy);
                pic.setAlpha(Math.round(255 * fade));
                c.drawBitmap(now, null, dst, pic);
                float dark = was == null ? frostDark : frostWasDark + (frostDark - frostWasDark) * fade;
                if (dark > 0.01f) {
                    fill.setColor(Math.round(dark * 255) << 24);
                    c.drawRect(b, fill);
                }
                fill.setColor(0x21FFFFFF);
                c.drawRect(b, fill);
            }
            c.drawRoundRect(edge, radius, radius, rim);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private TextView skyText(CharSequence text, float designPx, int colour, int weight) {
        TextView t = new TextView(context);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        t.setTextColor(colour);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sk(designPx));
        t.setTypeface(ScoreCard.outfit(context, weight));
        t.setText(text == null ? "" : text.toString());
        return t;
    }

    /** A club's crest on the glass, as the club draws it (no light-ground version: the glass is dark). */
    private ImageView glassCrest(String url) {
        ImageView v = new ImageView(context);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (url != null) loadLogo(url, v);
        return v;
    }

    /** A fine upright line between the glass box's parts. */
    private View hairline() {
        View v = new View(context);
        v.setBackgroundColor(0x38FFFFFF);
        return v;
    }

    private LinearLayout.LayoutParams hairlineLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, sk(1.5f)), sk(24));
        lp.leftMargin = sk(14);
        lp.rightMargin = sk(14);
        return lp;
    }

    private TextView label(CharSequence text) {
        TextView t = new TextView(context);
        t.setSingleLine(true);
        t.setTextColor(LightUi.ink(0xE6FFFFFF));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        t.setIncludeFontPadding(false);
        if (face != null) {
            t.setTypeface(face);
            t.getPaint().setSubpixelText(true);
        }
        t.setText(text);
        return t;
    }

    private static LinearLayout.LayoutParams margin(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = left;
        return lp;
    }

    private ImageView logoView(String url, int left) {
        ImageView v = new ImageView(context);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(logo, logo);
        lp.leftMargin = left;
        v.setLayoutParams(lp);
        if (url != null) loadLogo(url, v);
        return v;
    }

    /** Width of one game's box: its texts, three logos and their gaps, and the padding. */
    private int boxWidth(Game g) {
        if (g.drivers != null || g.text != null) {
            float t = measure.measureText(g.home) + measure.measureText(detail(g).toString());
            int extra = dp(8 + 8);
            if (g.drivers != null) for (String[] d : g.drivers) { t += measure.measureText(d[0]); extra += logo + dp(8 + 3); }
            else { t += measure.measureText(g.text); extra += dp(8); }
            return Math.round(t) + logo + extra + dp(24) + dp(4);
        }
        float text = measure.measureText(teams(g).toString()) + measure.measureText(detail(g).toString());
        int lead = g.leagueLogo != null ? logo : Math.round(measure.measureText(league(g).toString()));
        return Math.round(text) + lead + 2 * logo + dp(12 + 6 + 6 + 8) + dp(24) + dp(4);
    }

    /** Spanish-language networks in ESPN's US listings, passed over for an English one. */
    private static final String[] SPANISH = {"deportes", "univision", "unimas", "unimás", "tudn", "telemundo", "galavision", "galavisión", "vix"};

    /**
     * The game's US channels from ESPN's scoreboard (its geoBroadcasts): English-language TV first, then streaming,
     * then anything; joined as "CBS Sports Network / Paramount+". Null when ESPN lists none.
     */
    static String abroad(JSONObject competition) {
        List<String> all = broadcasts(competition);
        if (all.isEmpty()) return null;
        return android.text.TextUtils.join(" / ", all.subList(0, Math.min(2, all.size())));
    }

    /** ESPN's channels for the game: English-language TV, then streaming, then the rest. */
    static List<String> broadcasts(JSONObject competition) {
        JSONArray geo = competition.optJSONArray("geoBroadcasts");
        if (geo == null) return null;
        List<String> tv = new ArrayList<>(), streaming = new ArrayList<>(), other = new ArrayList<>();
        for (int i = 0; i < geo.length(); i++) {
            JSONObject b = geo.optJSONObject(i);
            JSONObject media = b == null ? null : b.optJSONObject("media"), kind = b == null ? null : b.optJSONObject("type");
            String name = media == null ? "" : media.optString("shortName", "");
            if (name.isEmpty()) continue;
            String type = kind == null ? "" : kind.optString("shortName", "");
            boolean spanish = false;
            for (String sp : SPANISH) if (name.toLowerCase(java.util.Locale.ROOT).contains(sp)) spanish = true;
            List<String> into = spanish ? other : "TV".equals(type) ? tv : streaming;
            if (!into.contains(name)) into.add(name);
        }
        List<String> all = new ArrayList<>(tv);
        all.addAll(streaming);
        all.addAll(other);
        return all;
    }

    /** Each competition's own name, by ESPN slug, for the places with room for it (the tags are short for the pills). */
    private static final Map<String, String> NAMES = new HashMap<>();
    static {
        String[][] n = {{"eng.1", "Premier League"}, {"eng.2", "Championship"}, {"eng.3", "League One"}, {"eng.4", "League Two"},
                {"eng.fa", "FA Cup"}, {"eng.league_cup", "EFL Cup"}, {"sco.1", "Scottish Premiership"}, {"uefa.champions", "Champions League"},
                {"uefa.europa", "Europa League"}, {"uefa.europa.conf", "Conference League"}, {"uefa.nations", "Nations League"},
                {"esp.1", "LaLiga"}, {"esp.copa_del_rey", "Copa del Rey"}, {"ita.1", "Serie A"}, {"ita.coppa_italia", "Coppa Italia"},
                {"ger.1", "Bundesliga"}, {"ger.dfb_pokal", "DFB-Pokal"}, {"fra.1", "Ligue 1"}, {"fra.coupe_de_france", "Coupe de France"},
                {"ned.1", "Eredivisie"}, {"por.1", "Liga Portugal"}, {"usa.1", "MLS"}, {"fifa.world", "World Cup"},
                {"fifa.friendly", "International Friendly"}, {"fifa.worldq.uefa", "World Cup Qualifier"},
                {"uefa.euroq", "Euro Qualifier"}, {"uefa.euro", "Euros"}, {"esp.super_cup", "Supercopa"}};
        for (String[] x : n) NAMES.put(x[0], x[1]);
    }

    /** The game's competition by its own name ("Bundesliga"); the tag where the slug is not known. */
    static String competition(Game g) {
        String n = g.slug == null ? null : NAMES.get(g.slug);
        return n != null ? n : g.league == null ? "" : g.league;
    }

    private static CharSequence league(Game g) {
        SpannableStringBuilder s = new SpannableStringBuilder(g.league);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(0xFFC77DFF), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private static CharSequence bold(String text) {
        SpannableStringBuilder s = new SpannableStringBuilder(text);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private static CharSequence teams(Game g) {
        SpannableStringBuilder s = new SpannableStringBuilder();
        if (g.state == 1) {
            s.append(g.home).append("  v  ").append(g.away);
        } else {
            s.append(g.home).append("  ");
            int at = s.length();
            s.append(g.homeScore).append("–").append(g.awayScore);
            s.setSpan(new StyleSpan(Typeface.BOLD), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.append("  ").append(g.away);
        }
        return s;
    }

    private static CharSequence detail(Game g) {
        boolean football = g.drivers == null && g.text == null;
        SpannableStringBuilder s = new SpannableStringBuilder(g.state == 0 && football ? liveClock(g) : g.detail);
        s.setSpan(new ForegroundColorSpan(g.state == 0 ? (LightUi.on() ? 0xFF16A34A : 0xFF4ADE80) : LightUi.ink(0x99FFFFFF)), 0, s.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    // ---- logos -----------------------------------------------------------------------------------

    private void loadLogo(String url, ImageView into) {
        Bitmap hit = logos.get(url);
        if (hit != null) { into.setImageBitmap(hit); return; }
        List<ImageView> list = waiting.get(url);
        if (list != null) { list.add(into); return; }
        list = new ArrayList<>();
        list.add(into);
        waiting.put(url, list);
        logoIo.execute(() -> {
            Bitmap bmp = fetchLogo(url);
            main.post(() -> {
                List<ImageView> views = waiting.remove(url);
                if (bmp == null || views == null) return;
                logos.put(url, bmp);
                for (ImageView v : views) v.setImageBitmap(bmp);
            });
        });
    }

    /** From the disk cache, or from ESPN's resizer at twice the logo size, then saved for next time. */
    private Bitmap fetchLogo(String url) {
        // On the Sky ticker's pale stage: the logo as it is (its version for light grounds), never whitened.
        if (url.startsWith("light:")) return cachedLogo(context, url.substring(6).replace("/500-dark/", "/500/"), logo);
        // The scores line is dark glass, but for a light wallpaper's white pills: there, a competition's logo in its
        // version for light backgrounds.
        if (LightUi.on()) return cachedLogo(context, url.replace("/500-dark/", "/500/"), logo);
        return forDark(cachedLogo(context, url, logo));
    }

    /**
     * The logo as it reads on a dark background: one that is almost black and without colour (Mercedes' F1 star)
     * comes back white (as ESPN's own version for dark grounds does Spurs' navy cockerel), the rest unchanged. A dark
     * crest in strong colour (Everton's royal blue) is the club's own badge and stays as it is (it came back a white
     * shape).
     */
    static Bitmap forDark(Bitmap b) {
        if (b == null) return null;
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        long sum = 0, chroma = 0;
        int n = 0;
        for (int c : px) {
            if ((c >>> 24) < 128) continue;
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, bl = c & 0xFF;
            sum += (r * 30 + g * 59 + bl * 11) / 100;
            chroma += Math.max(r, Math.max(g, bl)) - Math.min(r, Math.min(g, bl));
            n++;
        }
        if (n == 0 || sum / n >= 70 || chroma / n >= 90) return b;
        for (int i = 0; i < px.length; i++) px[i] = (px[i] & 0xFF000000) | 0x00F2F4F7;
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /**
     * A crest, logo or photo for drawing at about size px: fetched once at its source's full size into the disk cache,
     * then made from that copy at twice the size (at least 256 pixels) with smooth scaling. Off the main thread.
     */
    static Bitmap cachedLogo(Context context, String url, int logo) {
        if (url == null || url.isEmpty()) return null;
        File dir = new File(context.getCacheDir(), "pictures");
        dropOldCache(context);
        // Every picture kept once, as its source publishes it (full size): each use takes its own size from that copy.
        String src = original(url);
        File file = new File(dir, Integer.toHexString(src.hashCode()) + Integer.toHexString(src.length()) + ".img");
        if (!file.isFile()) {
            byte[] data = download(src);
            if (data == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            if (dir.isDirectory() || dir.mkdirs()) {
                File part = new File(dir, file.getName() + ".part");
                try (FileOutputStream fo = new FileOutputStream(part)) { fo.write(data); } catch (Exception e) { return null; }
                if (!part.renameTo(file)) { part.delete(); return null; }
            }
        }
        // At least twice the size it is drawn at (and never under 256 pixels), so it stays sharp however it is shown.
        Bitmap b = decodeTo(file, Math.max(256, Math.min(1024, logo * 2)));
        if (b == null) { file.delete(); return null; }
        return url.endsWith("#uhd") ? uhdLogo(context, b) : b;
    }

    /**
     * The full-size picture behind an address: ESPN's original rather than its resizer's copy, TheSportsDB's full cut-out
     * rather than its smaller ones.
     */
    static String original(String url) {
        String u = url.endsWith("#uhd") ? url.substring(0, url.length() - 4) : url;
        if (u.startsWith("https://a.espncdn.com/combiner/i?img=")) {
            String img = u.substring("https://a.espncdn.com/combiner/i?img=".length());
            int amp = img.indexOf('&');
            u = "https://a.espncdn.com" + (amp < 0 ? img : img.substring(0, amp));
        }
        return u.replaceAll("(thesportsdb\\.com/images/.+)/(tiny|small|medium|preview)$", "$1");
    }

    private static byte[] download(String src) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(src).openConnection();
            // Wikimedia asks every client to name itself.
            if (src.contains("wikimedia.org")) conn.setRequestProperty("User-Agent", "cleanyourtv-launcher/1.0 (personal TV launcher; github.com/Gathin23/cleanyourtv)");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(10000);
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 6 * 1024 * 1024) return null;
                }
                return out.toByteArray();
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The picture from its file at about the size asked (its longer side), never larger than it is: read in coarsely
     * (whole halvings, cheaply) to no less than one and a half times that, then brought down in halves with filtering
     * and a last smooth step, so edges stay clean; marked for mipmaps, so drawn smaller still it stays smooth.
     */
    private static Bitmap decodeTo(File f, int target) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int big = Math.max(o.outWidth, o.outHeight), sample = 1;
        while (big / (sample * 2) >= target * 3 / 2) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), d);
        if (b == null) return null;
        while (Math.max(b.getWidth(), b.getHeight()) >= target * 2) {
            Bitmap half = Bitmap.createScaledBitmap(b, Math.max(1, b.getWidth() / 2), Math.max(1, b.getHeight() / 2), true);
            if (half != b) b.recycle();
            b = half;
        }
        int now = Math.max(b.getWidth(), b.getHeight());
        if (now > target) {
            float k = target / (float) now;
            Bitmap fit = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * k)), Math.max(1, Math.round(b.getHeight() * k)), true);
            if (fit != b) b.recycle();
            b = fit;
        }
        b.setHasMipMap(true);
        return b;
    }

    private static boolean oldCacheDropped;

    /** The cache of the smaller copies the pictures used to be kept as, cleared away once. */
    private static void dropOldCache(Context context) {
        if (oldCacheDropped) return;
        oldCacheDropped = true;
        File old = new File(context.getCacheDir(), "logos");
        if (!old.isDirectory()) return;
        new Thread(() -> {
            File[] fs = old.listFiles();
            if (fs != null) for (File x : fs) x.delete();
            old.delete();
        }).start();
    }

    /**
     * A Sky channel logo for the UHD version of its channel: Sky has no logo of its own for those (its UHD comes in its
     * HD channels), so the logo's last box, "HD", is widened and says "UHD" instead, in the same white. A logo whose
     * divider before "HD" cannot be found comes back as it was. A logo URL ending "#uhd" asks for it.
     */
    static Bitmap uhdLogo(Context c, Bitmap im) {
        try {
            int w = im.getWidth(), h = im.getHeight();
            int[] px = new int[w * h];
            im.getPixels(px, 0, w, 0, 0, w, h);
            int top = -1, bottom = -1;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if ((px[y * w + x] >>> 24) > 60) { if (top < 0) top = y; bottom = y; break; }
            if (top < 0) return im;
            int high = bottom - top + 1;
            int[] cov = new int[w];
            for (int x = 0; x < w; x++) for (int y = top; y <= bottom; y++) if ((px[y * w + x] >>> 24) > 60) cov[x]++;
            int right = -1;
            for (int x = w - 1; x >= 0; x--) if (cov[x] > 0) { right = x; break; }
            int div = -1;
            for (int x = right - 6; x > w / 3; x--) if (cov[x] >= 0.55f * high && x + 3 < w && cov[x + 3] < 0.4f * high) { div = x; break; }
            if (div < 0) return im;
            int tL = -1, tR = -1, tT = -1, tB = -1;
            for (int x = div + 4; x < right - 3; x++) for (int y = top + 4; y < bottom - 3; y++) {
                if ((px[y * w + x] >>> 24) <= 60) continue;
                if (tL < 0) tL = x;
                tR = x;
                if (tT < 0 || y < tT) tT = y;
                if (y > tB) tB = y;
            }
            if (tL < 0) return im;
            int keep = tR + 3, wide = Math.round((keep - div) * 1.5f), nw = div + wide + (w - keep);
            Bitmap out = Bitmap.createBitmap(nw, h, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(out);
            cv.drawBitmap(im, new android.graphics.Rect(0, 0, div + 3, h), new android.graphics.Rect(0, 0, div + 3, h), null);
            android.graphics.Rect col = new android.graphics.Rect(keep, 0, keep + 1, h);
            for (int x = div + 3; x < div + wide; x++) cv.drawBitmap(im, col, new android.graphics.Rect(x, 0, x + 1, h), null);
            cv.drawBitmap(im, new android.graphics.Rect(keep, 0, w, h), new android.graphics.Rect(div + wide, 0, nw, h), null);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setColor(android.graphics.Color.WHITE);
            p.setTypeface(c.getResources().getFont(R.font.inter_semibold));
            p.setTextSize((tB - tT + 1) * 1.38f);
            android.graphics.Rect bb = new android.graphics.Rect();
            p.getTextBounds("UHD", 0, 3, bb);
            float cx = div + 3 + (wide - 3) / 2f;
            cv.drawText("UHD", cx - (bb.left + bb.right) / 2f, (tT + tB) / 2f - (bb.top + bb.bottom) / 2f, p);
            return out;
        } catch (RuntimeException e) {
            return im;
        }
    }

    /** A channel's Sky logo, asked for in its UHD form when the playlist's channel is the UHD one. */
    static String skyLogo(String sid, String channelName) {
        String url = "https://d2n0069hmnqmmx.cloudfront.net/epgdata/1.0/newchanlogos/600/600/skychb" + sid + ".png";
        return channelName != null && channelName.matches("(?i).*\\b(uhd|4k)\\b.*") ? url + "#uhd" : url;
    }

    static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 2 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
