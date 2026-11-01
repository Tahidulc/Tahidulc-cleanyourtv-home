package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Live Centre, after Sky Sports News on a Saturday afternoon, in the launcher's own look: the game playing live in
 * TiviMate underneath (put on its channel when a guide lists it), and over it a frame: the game's score small in the
 * top left corner (over the broadcaster's own), a column down the right with the time, the Live Centre's stamp, every
 * live score league by league (the Premier League first, then the Championship, LaLiga, Serie A, the Bundesliga and
 * Ligue 1) and one Premier League game large, and along the foot a ticker of every score and the latest goals, red
 * cards and results in the score cards' style. Up and Down change the game shown; Back closes it. Everything is
 * read every 30 seconds off the main thread; the ticker is one layer sliding along.
 */
final class LiveCentre {
    static final String[] LEAGUES = {"eng.1|PREMIER LEAGUE", "uefa.champions|CHAMPIONS LEAGUE", "eng.2|CHAMPIONSHIP",
            "esp.1|LALIGA", "ita.1|SERIE A", "ger.1|BUNDESLIGA", "fra.1|LIGUE 1"};
    private static final String TIVIMATE = "ar.tvplayer.tv";
    /** adb's test hook (test_live_card): the Sports page shows the Live Centre card with today's games, live or not. */
    static volatile boolean testCard;
    private static final int NAVY = 0xFF151A63, NAVY_DEEP = 0xFF0B0F45, INK = 0xFF0A0D33, GREEN = 0xFF3DDC84, RED = 0xFFE3061B;
    private static final long READ_MS = 30_000L, LEAGUE_MS = 8_000L;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static FrameLayout host;
    private static float k;
    private static Context app;

    /** The games, the one shown, which league the column shows, and every event seen with when it was first seen. */
    private static List<ScoresTicker.Game> games = new ArrayList<>();
    private static String shownId;
    private static int leagueAt, featuredAt;
    private static final Map<String, Long> seen = new HashMap<>();
    private static final List<String[]> events = new ArrayList<>();   // {kind, match line, who, minute, scoring side}
    private static final Map<String, Integer> lastStates = new HashMap<>();

    /** The frame's parts, filled from the games. */
    private static LinearLayout mini, leagueBox, featured, eventsBox;
    private static TextView clock, date, tickerText;
    private static View tickerLine;
    private static FrameLayout centre;
    private static android.animation.ObjectAnimator slide;

    private LiveCentre() { }

    private static int px(float v) {
        return Math.max(1, Math.round(v * k));
    }

    static boolean isShowing() {
        return host != null;
    }

    /** Shown over Home itself (the game on no channel), not over TiviMate: Home coming back leaves it. */
    private static boolean onHome;

    static boolean overHome() {
        return onHome;
    }

    /**
     * Opens it on a game (by ESPN's id; null: one of your clubs' if one is live, else the Premier League's first live
     * game, else any live game): the games are read, that game's channel put on in TiviMate when a guide lists it, and
     * the frame shown once TiviMate is up.
     */
    static void open(Context ctx, String gameId) {
        app = ctx.getApplicationContext();
        close();
        io.execute(() -> {
            // The scores alone first, so the game's channel goes on at once; the tables, incidents, F1, crests and the
            // game's stats are read after, while TiviMate finds the channel.
            List<ScoresTicker.Game> read = scores();
            ScoresTicker.Game pick = choose(read, gameId);
            Object[] w = pick == null ? null : GoalWatchService.watchForGame(app, pick);
            main.post(() -> {
                games = read;
                shownId = pick == null ? null : pick.id;
                learn(read, true);
                boolean tuning = w != null && tune(w);
                if (tuning) waitForTiviMate(0); else show(w == null);
            });
            extras(read);
            if (pick != null) stats(pick);
            main.post(() -> { if (host != null) fill(); });
        });
    }

    /** adb's test hook ("here"): the frame over whatever is on, its channel not changed. */
    static void openHere(Context ctx) {
        app = ctx.getApplicationContext();
        close();
        io.execute(() -> {
            List<ScoresTicker.Game> read = scores();
            ScoresTicker.Game pick = choose(read, null);
            if (pick != null) stats(pick);
            main.post(() -> {
                games = read;
                shownId = pick == null ? null : pick.id;
                learn(read, true);
                show(false);
            });
            extras(read);
            main.post(() -> { if (host != null) fill(); });
        });
    }

    /** The six leagues' games today, live first, the Premier League's first within each state. */
    private static List<ScoresTicker.Game> read(Context c) {
        List<ScoresTicker.Game> all = scores();
        if (c != null) extras(all);
        ScoresTicker.Game g = null;
        for (ScoresTicker.Game x : all) if (x.id != null && x.id.equals(shownId)) g = x;
        if (g != null) stats(g);
        return all;
    }

    /** The leagues' games, live first, the Premier League's first within each state. */
    private static List<ScoresTicker.Game> scores() {
        List<ScoresTicker.Game> all = ScoresTicker.load(LEAGUES);
        all.sort((a, b) -> a.state != b.state ? Integer.compare(a.state, b.state) : Integer.compare(rank(a), rank(b)));
        return all;
    }

    /** Everything else the frame shows, read after the scores: tables, incidents, F1, tennis and UFC, crests. */
    private static void extras(List<ScoresTicker.Game> all) {
        Context c = app;
        // The tables of the leagues with games under way (their standings before today's games went on).
        for (ScoresTicker.Game g : all) if ((g.state == 0 || testCard) && g.slug != null) standings(g.slug);
        // VAR decisions and penalties missed or saved: each game under way's match summary, every other read.
        if (c != null && (reads++ % 2 == 0)) {
            int n = 0;
            for (ScoresTicker.Game g : all) {
                if (g.state != 0 || n++ >= 12) continue;
                incidents.put(g.id, ScoresTicker.incidents(g));
            }
        }
        // F1's weekend (its sessions, live order and laps), and tennis and UFC every fourth read.
        if (c != null) {
            F1Results.Weekend w = F1Results.weekend(null);
            if (w != null) f1 = w;
            if (reads % 4 == 1) {
                List<MoreSports.Result> o = new ArrayList<>();
                try { o.addAll(MoreSports.tennis()); } catch (RuntimeException ignored) { }
                try { o.addAll(MoreSports.ufc()); } catch (RuntimeException ignored) { }
                others = o;
            }
        }
        // Every crest ready before it is drawn: the games', the leagues' and the tables' clubs'.
        if (c != null) {
            for (ScoresTicker.Game g : all) { crest(g.homeLogo); crest(g.awayLogo); crest(g.leagueLogo); compLogo(compUrl(g)); }
            for (List<Club> t : tables.values()) for (Club x : t) crest(x.logo);
        }
    }

    // ---- live tables ----------------------------------------------------------------------------------------------

    /** A club's line in a table: ESPN's id, name, crest, played, goal difference, points, and its place. */
    private static final class Club {
        String id, name, abbr, logo;
        int played, gd, pts, rank;
    }

    private static final Map<String, List<Club>> tables = new java.util.concurrent.ConcurrentHashMap<>();
    /** Each game under way's VAR decisions and penalties ({key, what, player, minute}), and how many reads so far. */
    private static final Map<String, List<String[]>> incidents = new java.util.concurrent.ConcurrentHashMap<>();
    private static int reads;
    /** F1's weekend as last read, tennis and UFC as last read, and how each session or match stood before. */
    private static volatile F1Results.Weekend f1;
    private static volatile List<MoreSports.Result> others = new ArrayList<>();
    private static final Map<String, String> otherStates = new HashMap<>();
    private static final Map<String, Long> tablesAt = new java.util.concurrent.ConcurrentHashMap<>();

    /** A league's standings from ESPN (as they stood before today's games under way), kept ten minutes. Off the main thread. */
    private static void standings(String slug) {
        Long at = tablesAt.get(slug);
        if (at != null && System.currentTimeMillis() - at < 10 * 60_000L) return;
        tablesAt.put(slug, System.currentTimeMillis());
        try {
            org.json.JSONObject root = new org.json.JSONObject(ScoresTicker.get("https://site.api.espn.com/apis/v2/sports/soccer/" + slug + "/standings"));
            org.json.JSONArray entries = root.getJSONArray("children").getJSONObject(0).getJSONObject("standings").getJSONArray("entries");
            List<Club> rows = new ArrayList<>();
            for (int n = 0; n < entries.length(); n++) {
                org.json.JSONObject e = entries.getJSONObject(n), team = e.getJSONObject("team");
                Map<String, String> st = new HashMap<>();
                org.json.JSONArray stats = e.optJSONArray("stats");
                for (int k = 0; stats != null && k < stats.length(); k++) {
                    org.json.JSONObject x = stats.getJSONObject(k);
                    st.put(x.optString("name"), x.optString("value", x.optString("displayValue")));
                }
                Club c = new Club();
                c.id = team.optString("id");
                c.name = team.optString("displayName");
                c.abbr = team.optString("abbreviation");
                org.json.JSONArray logos = team.optJSONArray("logos");
                c.logo = logos != null && logos.length() > 0 ? logos.getJSONObject(0).optString("href") : null;
                c.played = intOf(st.get("gamesPlayed"));
                c.gd = intOf(st.get("pointDifferential"));
                c.pts = intOf(st.get("points"));
                c.rank = intOf(st.get("rank"));
                rows.add(c);
            }
            rows.sort((a, b) -> Integer.compare(a.rank, b.rank));
            if (!rows.isEmpty()) tables.put(slug, rows);
        } catch (Exception e) {
            tablesAt.put(slug, System.currentTimeMillis() - 9 * 60_000L);   // again in a minute
        }
    }

    private static int intOf(String v) {
        try { return v == null ? 0 : Math.round(Float.parseFloat(v)); } catch (NumberFormatException e) { return 0; }
    }

    /**
     * The table as it stands now: the standings with every game under way counted at its score (played, goal
     * difference, points), sorted again. {club, its place before, playing now (its score "2-1"), or null}.
     */
    private static List<Object[]> liveTable(String slug) {
        List<Club> base = tables.get(slug);
        if (base == null) return null;
        Map<String, Club> now = new HashMap<>();
        for (Club c : base) {
            Club x = new Club();
            x.id = c.id; x.name = c.name; x.abbr = c.abbr; x.logo = c.logo;
            x.played = c.played; x.gd = c.gd; x.pts = c.pts; x.rank = c.rank;
            now.put(c.id, x);
        }
        Map<String, String> playing = new HashMap<>();
        for (ScoresTicker.Game g : games) {
            if ((g.state != 0 && !(testCard && g.state == 1)) || !slug.equals(g.slug)) continue;
            Club h = now.get(g.homeId), a = now.get(g.awayId);
            int hs = intOf(g.homeScore), as = intOf(g.awayScore);
            if (h != null) { h.played++; h.gd += hs - as; h.pts += hs > as ? 3 : hs == as ? 1 : 0; playing.put(h.id, hs + "–" + as); }
            if (a != null) { a.played++; a.gd += as - hs; a.pts += as > hs ? 3 : hs == as ? 1 : 0; playing.put(a.id, as + "–" + hs); }
        }
        List<Club> sorted = new ArrayList<>(now.values());
        sorted.sort((x, y) -> x.pts != y.pts ? Integer.compare(y.pts, x.pts) : x.gd != y.gd ? Integer.compare(y.gd, x.gd)
                : Integer.compare(x.rank, y.rank));
        List<Object[]> out = new ArrayList<>();
        for (Club c : sorted) out.add(new Object[]{c, c.rank, playing.get(c.id)});
        return out;
    }

    private static int rank(ScoresTicker.Game g) {
        for (int i = 0; i < LEAGUES.length; i++) if (LEAGUES[i].startsWith(g.slug + "|")) return i;
        return LEAGUES.length;
    }

    private static ScoresTicker.Game choose(List<ScoresTicker.Game> all, String id) {
        if (id != null) for (ScoresTicker.Game g : all) if (id.equals(g.id)) return g;
        for (ScoresTicker.Game g : all) if (g.state == 0 && GoalWatchService.follows(app, g)) return g;
        for (ScoresTicker.Game g : all) if (g.state == 0) return g;
        return all.isEmpty() ? null : all.get(0);
    }

    /** The game's channel put on: TiviMate's own search behind its cover, or the playlist's own way to it. */
    private static boolean tune(Object[] w) {
        try {
            if (w.length > 2 && w[2] instanceof Runnable) {
                ((Runnable) w[2]).run();
                return true;
            }
            @SuppressWarnings("unchecked") List<String> names = (List<String>) w[0];
            GoalWatchService.onAppOpened(app, TIVIMATE);
            return AppSearchService.tune(app, TIVIMATE, names, (Bitmap) w[1]);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The frame once the channel is on (the helper done, its cover gone); at the latest after 20 seconds. */
    private static void waitForTiviMate(int tries) {
        if (host != null) return;
        if (AppSearchService.tuning() && tries < 50) {
            main.postDelayed(() -> waitForTiviMate(tries + 1), 400L);
            return;
        }
        show(false);
    }

    private static ScoresTicker.Game shown() {
        for (ScoresTicker.Game g : games) if (g.id != null && g.id.equals(shownId)) return g;
        return games.isEmpty() ? null : games.get(0);
    }

    // ---- the frame ------------------------------------------------------------------------------------------------

    private static void show(boolean noChannel) {
        if (host != null || app == null) return;
        Context c = app;
        onHome = noChannel;
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        FrameLayout win = new FrameLayout(c);

        // Not on any channel: a plain ground where the game would be, with the game large on it.
        centre = new FrameLayout(c);
        if (noChannel) centre.setBackgroundColor(0xFF0B0E33);
        win.addView(centre, new FrameLayout.LayoutParams(px(1500), px(900)));

        // The column down the right.
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF151A63, 0xFF0B0F45}));
        column.setPadding(px(30), px(34), px(30), px(26));
        FrameLayout.LayoutParams col = new FrameLayout.LayoutParams(px(420), -1, Gravity.END);
        win.addView(column, col);
        clock = text(c, "", 50, 0xFFFFFFFF, ScoreCard.outfit(c, 600));
        column.addView(clock);
        date = text(c, "", 18, 0xFFC9CEF2, ScoreCard.outfit(c, 500));
        column.addView(date);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, px(38));
        sl.topMargin = px(12);
        column.addView(stamp(c), sl);
        leagueBox = new LinearLayout(c);
        leagueBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, 0, 1f);
        ll.topMargin = px(28);
        column.addView(leagueBox, ll);
        featured = new LinearLayout(c);
        featured.setOrientation(LinearLayout.VERTICAL);
        column.addView(featured, new LinearLayout.LayoutParams(-1, -2));

        // The foot: the ticker of every score, and under it the latest events.
        LinearLayout foot = new LinearLayout(c);
        foot.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(px(1500), px(180), Gravity.BOTTOM | Gravity.START);
        win.addView(foot, fl);
        FrameLayout band = new FrameLayout(c);
        band.setBackgroundColor(0xFFFFFFFF);
        band.setClipChildren(true);
        foot.addView(band, new LinearLayout.LayoutParams(-1, px(44)));
        tickerText = text(c, "", 21, INK, ScoreCard.outfit(c, 700));
        tickerText.setSingleLine(true);
        tickerText.setEllipsize(null);
        tickerText.setGravity(Gravity.CENTER_VERTICAL);
        // No layer: the line can be far wider than the GPU's largest texture; sliding a plain view costs no redraw.
        band.addView(tickerText, new FrameLayout.LayoutParams(-2, -1, Gravity.CENTER_VERTICAL));
        tickerLine = band;
        eventsBox = new LinearLayout(c);
        eventsBox.setOrientation(LinearLayout.VERTICAL);
        eventsBox.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0E1450, 0xFF0A0D3D}));
        eventsBox.setPadding(px(24), px(8), px(24), px(8));
        foot.addView(eventsBox, new LinearLayout.LayoutParams(-1, 0, 1f));

        // The game's scoreboard in the top left corner, wide and tall enough to cover the broadcaster's own, and its
        // stats beside it.
        mini = new LinearLayout(c);
        mini.setOrientation(LinearLayout.HORIZONTAL);
        mini.setClipToOutline(true);
        mini.setOutlineProvider(round(px(14)));
        mini.setElevation(px(12));
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(px(BOARD_W), px(BOARD_H), Gravity.TOP | Gravity.START);
        ml.leftMargin = px(24);
        ml.topMargin = px(22);
        win.addView(mini, ml);
        // The stats and facts fold out of the scoreboard's right end and back in: a panel tucked behind it (the
        // scoreboard is drawn over it, higher up), in a drawer that hides whatever of it has not come out yet.
        statsDrawer = new FrameLayout(c);
        FrameLayout.LayoutParams stl = new FrameLayout.LayoutParams(px(STATS_W + TUCK), px(BOARD_H), Gravity.TOP | Gravity.START);
        stl.leftMargin = px(24 + BOARD_W - TUCK);
        stl.topMargin = px(22);
        win.addView(statsDrawer, stl);
        statsBox = new FrameLayout(c);
        statsBox.setClipToOutline(true);
        statsBox.setOutlineProvider(round(px(14)));
        statsBox.setBackground(statsGround());
        statsBox.setPadding(px(TUCK), 0, 0, 0);
        statsDrawer.addView(statsBox, new FrameLayout.LayoutParams(-1, -1));
        statsOpen = false;
        statsBox.setTranslationX(-px(STATS_W + TUCK));

        // Back closes it: the remote's Back key in the column's top right corner.
        ImageView back = new ImageView(c);
        back.setImageBitmap(GoalWatchService.backButton(Config.themeString(c, "ok_button", "tcl"), px(30)));
        back.setTag(PopupMotion.BACK_KEY);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(px(30), px(30), Gravity.TOP | Gravity.END);
        bl.rightMargin = px(24);
        bl.topMargin = px(26);
        win.addView(back, bl);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.setTitle("LiveCentre");
        // The remote stays with TiviMate (the window never takes the focus): its keys come through the key helper, or
        // on a TV without one (the TCL) through the channel helper while the frame shows. Taking the focus instead hid
        // TiviMate from the channel helper as it searched, and the channel was never put on.
        boolean takeKeys = !InputKeyService.running && !AppSearchService.running();
        if (takeKeys) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            return;
        }
        host = win;
        if (!InputKeyService.running && !takeKeys) AppSearchService.keysFor("live", true);
        if (takeKeys) {
            // With no helper at all the frame takes the remote's keys itself while it shows.
            win.setFocusable(true);
            win.setFocusableInTouchMode(true);
            win.requestFocus();
            win.setOnKeyListener((v, code, e) -> handle(code, e));
        }
        fill();
        win.setAlpha(0f);
        win.animate().alpha(1f).setDuration(320).withLayer().start();
        main.postDelayed(tick, 1_000L);
        main.post(clockTick);
        main.postDelayed(statsTurn, FIRST_OPEN_MS);
        main.postDelayed(reread, READ_MS);
        main.postDelayed(nextLeague, LEAGUE_MS);
    }

    /** The Live Centre's stamp, where Sky's channel name sits: LIVE on the launcher's green, CENTRE on white. */
    /**
     * The LIVE CENTRE stamp painted into a picture of the given height (the Sports page's bar), as the frame draws it:
     * LIVE in dark green on the launcher's green with its slanted edge, CENTRE in navy on white, rounded corners.
     */
    static Bitmap stampPicture(Context c, int height) {
        float u = height / 38f;
        Paint live = new Paint(Paint.ANTI_ALIAS_FLAG), word = new Paint(Paint.ANTI_ALIAS_FLAG);
        live.setTypeface(ScoreCard.outfit(c, 900));
        live.setTextSize(19 * u);
        live.setColor(0xFF062814);
        word.setTypeface(ScoreCard.outfit(c, 900));
        word.setTextSize(19 * u);
        word.setLetterSpacing(0.06f);
        word.setColor(NAVY);
        float liveW = live.measureText("LIVE") + (12 + 18) * u, wordW = word.measureText("CENTRE");
        int w = Math.round(liveW + 6 * u + wordW + 14 * u);
        Bitmap b = Bitmap.createBitmap(w, height, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, w, height), 6 * u, 6 * u, Path.Direction.CW);
        cv.clipPath(clip);
        cv.drawColor(0xFFFFFFFF);
        Paint green = new Paint(Paint.ANTI_ALIAS_FLAG);
        green.setColor(GREEN);
        Path slant = new Path();
        slant.moveTo(0, 0);
        slant.lineTo(liveW, 0);
        slant.lineTo(liveW - 10 * u, height);
        slant.lineTo(0, height);
        slant.close();
        cv.drawPath(slant, green);
        Paint.FontMetrics fm = live.getFontMetrics();
        float base = height / 2f - (fm.ascent + fm.descent) / 2f;
        cv.drawText("LIVE", 12 * u, base, live);
        cv.drawText("CENTRE", liveW + 6 * u, base, word);
        return b;
    }

    private static View stamp(Context c) {
        LinearLayout s = new LinearLayout(c);
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setClipToOutline(true);
        s.setOutlineProvider(round(px(6)));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(px(6));
        s.setBackground(bg);
        TextView live = text(c, "LIVE", 19, 0xFF062814, ScoreCard.outfit(c, 900));
        live.setGravity(Gravity.CENTER);
        live.setPadding(px(12), 0, px(18), 0);
        live.setBackground(new ScoreCard.Slant(GREEN, px(10), false));
        s.addView(live, new LinearLayout.LayoutParams(-2, -1));
        TextView centreWord = text(c, "CENTRE", 19, NAVY, ScoreCard.outfit(c, 900));
        centreWord.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.leftMargin = px(6);
        cl.rightMargin = px(14);
        s.addView(centreWord, cl);
        return s;
    }

    /** Every part from the games as they now are. */
    private static void fill() {
        if (host == null) return;
        Context c = app;
        java.util.Date now = new java.util.Date();
        clock.setText(new java.text.SimpleDateFormat("HH:mm", Locale.UK).format(now));
        date.setText(new java.text.SimpleDateFormat("EEEE d MMMM", Locale.UK).format(now));
        fillMini(c);
        fillLeague(c);
        fillFeatured(c);
        fillTicker(c);
        fillEvents(c);
        fillCentre(c);
    }

    private static final int BOARD_W = 748, BOARD_H = 112, STATS_W = 540, COMP_W = 108, TUCK = 18;
    /** The stats' labels on the white panel: a soft navy. */
    private static final int STAT_LABEL = 0xFF6A6F9C;
    /** Each page of stats on show, how many pages per fold-out, the scoreboard alone in between, the first fold-out. */
    private static final long STATS_MS = 6_000L, SHUT_MS = 15_000L, FIRST_OPEN_MS = 4_000L;
    private static final int PAGES_OPEN = 2;
    private static FrameLayout statsBox, statsDrawer;
    /** Whether the stats are folded out now, and how many pages they have shown this time. */
    private static boolean statsOpen;
    private static int pagesShown;
    private static TextView boardScore, boardClock;

    /**
     * The scoreboard as the broadcasters draw theirs, larger: each side on white with its crest over its three letters,
     * edged in its own colour, and between them the score and the match clock on red.
     */
    private static void fillMini(Context c) {
        mini.removeAllViews();
        ScoresTicker.Game g = shown();
        if (g == null) { mini.setVisibility(View.GONE); statsDrawer.setVisibility(View.GONE); return; }
        if (!boardHidden) {
            mini.setVisibility(View.VISIBLE);
            statsDrawer.setVisibility(View.VISIBLE);
        }
        buildBoard(c, mini, g);
        clockNow();
        fillStats(c, false);
    }

    /** The scoreboard's parts, left to right: the competition, the home side, the score and clock, the away side. */
    private static void buildBoard(Context c, LinearLayout into, ScoresTicker.Game g) {
        View comp = compTile(c, g);
        if (comp != null) into.addView(comp, new LinearLayout.LayoutParams(px(COMP_W), -1));
        into.addView(side(c, g, true), new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        mid.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFE3283A, 0xFFB8121F}));
        boardScore = text(c, g.state == 1 ? "v" : score(g.homeScore) + "-" + score(g.awayScore), 58, 0xFFFFFFFF, ScoreCard.outfit(c, 900));
        boardScore.setGravity(Gravity.CENTER);
        mid.addView(boardScore, new LinearLayout.LayoutParams(-2, -2));
        boardClock = text(c, "", 27, 0xFFFFFFFF, ScoreCard.outfit(c, 700));
        boardClock.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.topMargin = px(2);
        mid.addView(boardClock, cl);
        into.addView(mid, new LinearLayout.LayoutParams(px(210), -1));
        into.addView(side(c, g, false), new LinearLayout.LayoutParams(0, -1, 1f));
    }

    /**
     * adb only: one scoreboard per competition (a game from each, live first) drawn to files/boards.png, nothing on
     * screen. The Live Centre's own board, when it is open, is left as it is.
     */
    static void renderBoards(Context ctx, boolean every) {
        if (app == null) app = ctx.getApplicationContext();
        io.execute(() -> {
            List<ScoresTicker.Game> read = scores();
            List<ScoresTicker.Game> one = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            if (every) {
                // Every page there is: a game under way or just over (its match stats), then one still to come.
                ScoresTicker.Game on = null, next = null;
                for (ScoresTicker.Game g : read) {
                    if (on == null && g.state != 1) on = g;
                    if (next == null && g.state == 1) next = g;
                }
                if (on != null) one.add(on);
                if (next != null) one.add(next);
            } else {
                for (ScoresTicker.Game g : read) if (g.slug != null && seen.add(g.slug)) one.add(g);
            }
            for (ScoresTicker.Game g : one) { crest(g.homeLogo); crest(g.awayLogo); compLogo(compUrl(g)); }
            if (every) for (ScoresTicker.Game g : one) stats(g);
            else if (!one.isEmpty()) stats(one.get(0));
            main.post(() -> {
                float was = k;
                k = app.getResources().getDisplayMetrics().widthPixels / 1920f;
                TextView score = boardScore, clockView = boardClock;
                int w = px(BOARD_W), h = px(BOARD_H), gap = px(16), sw = px(STATS_W + TUCK);
                // The first game three times (alone, then folded out on two pages of its stats), then one game of
                // each other competition alone.
                List<ScoresTicker.Game> rows = new ArrayList<>();
                List<Integer> pageOf = new ArrayList<>();
                for (int i = 0; i < one.size(); i++) {
                    if (every) {
                        List<String[]> ps = statPages.get(one.get(i).id);
                        for (int p = 0; ps != null && p < ps.size(); p++) { rows.add(one.get(i)); pageOf.add(Math.floorMod(p - statAt, ps.size())); }
                        continue;
                    }
                    rows.add(one.get(i));
                    pageOf.add(-1);
                    if (i == 0) for (int p = 0; p < 2; p++) { rows.add(one.get(0)); pageOf.add(p); }
                }
                Bitmap out = Bitmap.createBitmap(w - px(TUCK) + sw + 2 * gap, Math.max(1, rows.size()) * (h + gap) + gap, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(out);
                cv.drawColor(0xFF2A2E3A);
                int was0 = statAt;
                for (int i = 0; i < rows.size(); i++) {
                    ScoresTicker.Game g = rows.get(i);
                    if (pageOf.get(i) >= 0) {
                        statAt = was0 + pageOf.get(i);
                        FrameLayout panel = new FrameLayout(app);
                        panel.setBackground(statsGround());
                        panel.setPadding(px(TUCK), 0, 0, 0);
                        panel.addView(statsPage(app, g), new FrameLayout.LayoutParams(-1, -1));
                        panel.measure(View.MeasureSpec.makeMeasureSpec(sw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                        panel.layout(0, 0, sw, h);
                        centreTexts(panel);
                        cv.save();
                        cv.translate(gap + w - px(TUCK), gap + i * (h + gap));
                        panel.draw(cv);
                        cv.restore();
                    }
                    LinearLayout b = new LinearLayout(app);
                    b.setOrientation(LinearLayout.HORIZONTAL);
                    buildBoard(app, b, g);
                    boardClock.setText(clockText(g));
                    b.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                    b.layout(0, 0, w, h);
                    centreTexts(b);
                    cv.save();
                    cv.translate(gap, gap + i * (h + gap));
                    // The scoreboard's own rounded corners, as its outline gives them on screen.
                    android.graphics.Path clip = new android.graphics.Path();
                    clip.addRoundRect(new RectF(0, 0, w, h), px(14), px(14), android.graphics.Path.Direction.CW);
                    cv.clipPath(clip);
                    b.draw(cv);
                    cv.restore();
                }
                statAt = was0;
                boardScore = score;
                boardClock = clockView;
                k = was;
                java.io.File f = new java.io.File(app.getExternalFilesDir(null), "boards.png");
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { out.compress(Bitmap.CompressFormat.PNG, 100, o); } catch (Exception ignored) { }
            });
        });
    }

    /**
     * A centred one-line TextView is laid out very wide and scrolled to its text just before it is drawn on screen;
     * drawn off screen it is never scrolled, and its text lands far off to the right. Scrolled here as on screen.
     */
    private static void centreTexts(View v) {
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) centreTexts(g.getChildAt(i));
        } else if (v instanceof TextView) {
            TextView t = (TextView) v;
            android.text.Layout l = t.getLayout();
            if (l == null) return;
            int space = t.getWidth() - t.getCompoundPaddingLeft() - t.getCompoundPaddingRight();
            int along = Gravity.getAbsoluteGravity(t.getGravity(), View.LAYOUT_DIRECTION_LTR) & Gravity.HORIZONTAL_GRAVITY_MASK;
            if (along == Gravity.CENTER_HORIZONTAL) t.scrollTo((int) Math.floor((l.getLineLeft(0) + l.getLineRight(0)) / 2f - space / 2f), 0);
            else if (along == Gravity.RIGHT) t.scrollTo((int) Math.ceil(l.getLineRight(0) - space), 0);
        }
    }

    /** The competition's mark at the scoreboard's left end, in white on its own colour, as the broadcasters show it. */
    private static View compTile(Context c, ScoresTicker.Game g) {
        String url = compUrl(g);
        if (url == null) return null;
        FrameLayout t = new FrameLayout(c);
        t.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, compColours(g.slug)));
        ImageView logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        t.addView(logo, new FrameLayout.LayoutParams(px(76), px(76), Gravity.CENTER));
        Bitmap have = crests.get("comp|" + url);
        if (have != null) logo.setImageBitmap(have);
        else io.execute(() -> {
            Bitmap b = compLogo(url);
            if (b != null) main.post(() -> logo.setImageBitmap(b));
        });
        return t;
    }

    private static String compUrl(ScoresTicker.Game g) {
        return g == null ? null : g.leagueLogo != null ? g.leagueLogo : GoalWatchService.leagueLogoFor(g);
    }

    /** The Premier League's purple, UEFA's night blue, else the launcher's navy: {top, bottom}. */
    private static int[] compColours(String slug) {
        if ("eng.1".equals(slug)) return new int[]{0xFF4B0E58, 0xFF2E0033};
        if (slug != null && slug.startsWith("uefa.")) return new int[]{0xFF15257A, 0xFF070E40};
        return new int[]{NAVY, NAVY_DEEP};
    }

    /**
     * A competition's logo for its tile: ESPN's version for dark grounds; the Premier League's lion alone, in white
     * (ESPN's carries the words under it). Kept with the crests. Off the main thread.
     */
    private static Bitmap compLogo(String url) {
        if (url == null) return null;
        String key = "comp|" + url;
        Bitmap have = crests.get(key);
        if (have != null) return have;
        Bitmap b;
        if (GoalWatchService.premier(url)) {
            Bitmap full = ScoresTicker.cachedLogo(app, url.replace("/500-dark/", "/500/"), 160);
            b = full == null ? null : GoalWatchService.lionOnly(full, true);
        } else {
            Bitmap dark = url.contains("/500/") ? ScoresTicker.cachedLogo(app, url.replace("/500/", "/500-dark/"), 128) : null;
            b = ScoreCard.onNavy(dark != null ? dark : ScoresTicker.cachedLogo(app, url, 128));
            // The Bundesliga's red badge alone: its wordmark under it is too dark to read on the navy.
            if (b != null && url.matches(".*/leaguelogos/soccer/500(-dark)?/10\\.png")) b = GoalWatchService.lionOnly(b, false);
        }
        if (b != null) crests.put(key, b);
        return b;
    }

    /** One side: its crest alone, large, on white (its three letters only while it has no crest). */
    private static View side(Context c, ScoresTicker.Game g, boolean home) {
        FrameLayout v = new FrameLayout(c);
        v.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFFFFFFF, 0xFFE9ECF5}));
        String logo = home ? g.homeLogo : g.awayLogo;
        if (logo == null) {
            TextView name = text(c, home ? g.home : g.away, 38, INK, ScoreCard.outfit(c, 800));
            name.setLetterSpacing(0.04f);
            v.addView(name, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            return v;
        }
        ImageView crest = new ImageView(c);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loadCrest(crest, logo);
        v.addView(crest, new FrameLayout.LayoutParams(px(84), px(84), Gravity.CENTER));
        return v;
    }

    /** The match clock: minutes and seconds counting on from ESPN's last reading; HT, FT, or the kick-off time. */
    private static void clockNow() {
        ScoresTicker.Game g = shown();
        if (boardClock == null || g == null) return;
        boardClock.setText(clockText(g));
    }

    private static String clockText(ScoresTicker.Game g) {
        String t;
        if (g.state == 2) t = "FT";
        else if (g.state == 1) t = minute(g);
        else if (g.halftime) t = "HT";
        else if (g.clockAt > 0 && g.clockSec > 0) {
            long secs = (long) g.clockSec + (System.currentTimeMillis() - g.clockAt) / 1000L;
            t = String.format(Locale.ROOT, "%02d:%02d", secs / 60, secs % 60);
        } else {
            // Only the minute ("67'", "90'+3'"): counted on from the start of that minute, as a stadium clock runs,
            // and never past its end until ESPN says the next minute has come.
            long secs = countedFromMinute(g);
            t = secs >= 0 ? String.format(Locale.ROOT, "%02d:%02d", secs / 60, secs % 60) : minute(g);
        }
        return t;
    }

    /** A game's minute as ESPN last gave it, and when it was first seen: {minute (with added time), seen at}. */
    private static final Map<String, long[]> minuteSeen = new HashMap<>();

    private static long countedFromMinute(ScoresTicker.Game g) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)'?\\s*(?:\\+\\s*(\\d+))?").matcher(g.detail == null ? "" : g.detail);
        if (!m.find()) return -1;
        int minute = Integer.parseInt(m.group(1)) + (m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
        if (minute <= 0) return -1;
        long now = System.currentTimeMillis();
        long[] seen = minuteSeen.get(g.id);
        if (seen == null || seen[0] != minute) { seen = new long[]{minute, now}; minuteSeen.put(g.id, seen); }
        long into = Math.min(59, (now - seen[1]) / 1000L);
        return (minute - 1) * 60L + into;
    }

    private static final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (host == null) return;
            clockNow();
            main.postDelayed(this, 1_000L);
        }
    };

    // ---- the game's stats, beside the scoreboard -----------------------------------------------------------------

    /** The shown game's stats from its ESPN match summary: {label, home, away, kind} pages; kind "bar" or "form". */
    private static final Map<String, List<String[]>> statPages = new java.util.concurrent.ConcurrentHashMap<>();
    private static int statAt;

    /** Reads the game's match summary: the match stats while it is on or over, the form and the places always. */
    private static void stats(ScoresTicker.Game g) {
        if (g == null || g.id == null || g.slug == null) return;
        List<String[]> pages = new ArrayList<>();
        try {
            org.json.JSONObject d = new org.json.JSONObject(ScoresTicker.get("https://site.api.espn.com/apis/site/v2/sports/soccer/"
                    + g.slug + "/summary?event=" + g.id));
            Map<String, String> h = new HashMap<>(), a = new HashMap<>();
            org.json.JSONObject box = d.optJSONObject("boxscore");
            org.json.JSONArray teams = box == null ? null : box.optJSONArray("teams");
            for (int i = 0; teams != null && i < teams.length(); i++) {
                org.json.JSONObject t = teams.getJSONObject(i);
                String id = t.getJSONObject("team").optString("id");
                Map<String, String> m = id.equals(g.homeId) ? h : id.equals(g.awayId) ? a : (i == 0 ? h : a);
                org.json.JSONArray st = t.optJSONArray("statistics");
                for (int k = 0; st != null && k < st.length(); k++) m.put(st.getJSONObject(k).optString("name"), st.getJSONObject(k).optString("displayValue"));
            }
            // Who scored, the player of the match, both formations and the win chances before kick-off.
            String[] goals = g.state != 1 ? goalsPage(g, d.optJSONArray("keyEvents")) : null;
            String[] star = g.state != 1 ? starPage(g, d.optJSONArray("rosters")) : null;
            String[] shapes = formations(g, d.optJSONArray("rosters"));
            String[] odds = winChance(d.optJSONArray("odds"));
            if (goals != null) pages.add(goals);
            if (g.state != 1 && h.containsKey("possessionPct")) {
                pages.add(new String[]{"POSSESSION", pct(h.get("possessionPct")), pct(a.get("possessionPct")), "bar"});
                if (h.containsKey("totalShots")) pages.add(new String[]{"SHOTS (ON TARGET)", h.get("totalShots") + " (" + h.getOrDefault("shotsOnTarget", "0") + ")",
                        a.get("totalShots") + " (" + a.getOrDefault("shotsOnTarget", "0") + ")", "bar"});
                if (star != null) pages.add(star);
                if (odds != null) pages.add(odds);
                if (h.containsKey("wonCorners")) pages.add(new String[]{"CORNERS", h.get("wonCorners"), a.get("wonCorners"), "bar"});
                if (shapes != null) pages.add(shapes);
                if (h.containsKey("accuratePasses") && h.containsKey("totalPasses"))
                    pages.add(new String[]{"PASS ACCURACY", passes(h), passes(a), "bar"});
                if (h.containsKey("foulsCommitted")) pages.add(new String[]{"FOULS", h.get("foulsCommitted"), a.get("foulsCommitted"), "bar"});
                int cards = intOf(h.get("yellowCards")) + intOf(a.get("yellowCards")) + intOf(h.get("redCards")) + intOf(a.get("redCards"));
                if (cards > 0) pages.add(new String[]{"CARDS (RED)", h.get("yellowCards") + " (" + h.getOrDefault("redCards", "0") + ")",
                        a.get("yellowCards") + " (" + a.getOrDefault("redCards", "0") + ")", "bar"});
                if (h.containsKey("saves")) pages.add(new String[]{"SAVES", h.get("saves"), a.get("saves"), "bar"});
                // The detailed stats.
                if (h.containsKey("offsides")) pages.add(new String[]{"OFFSIDES", h.get("offsides"), a.get("offsides"), "bar"});
                if (h.containsKey("totalTackles")) pages.add(new String[]{"TACKLES WON", of(h, "effectiveTackles", "totalTackles"), of(a, "effectiveTackles", "totalTackles"), "bar"});
                if (h.containsKey("interceptions")) pages.add(new String[]{"INTERCEPTIONS", h.get("interceptions"), a.get("interceptions"), "bar"});
                if (h.containsKey("totalCrosses")) pages.add(new String[]{"CROSSES COMPLETED", of(h, "accurateCrosses", "totalCrosses"), of(a, "accurateCrosses", "totalCrosses"), "bar"});
                if (h.containsKey("totalLongBalls")) pages.add(new String[]{"LONG BALLS COMPLETED", of(h, "accurateLongBalls", "totalLongBalls"), of(a, "accurateLongBalls", "totalLongBalls"), "bar"});
                if (h.containsKey("totalClearance")) pages.add(new String[]{"CLEARANCES", h.get("totalClearance"), a.get("totalClearance"), "bar"});
                if (h.containsKey("blockedShots")) pages.add(new String[]{"BLOCKED SHOTS", h.get("blockedShots"), a.get("blockedShots"), "bar"});
            } else {
                if (odds != null) pages.add(odds);
                if (shapes != null) pages.add(shapes);
                if (star != null) pages.add(star);
            }
            // Form: each side's last five, oldest first as ESPN lists them.
            org.json.JSONArray five = d.optJSONArray("lastFiveGames");
            String fh = null, fa = null;
            for (int i = 0; five != null && i < five.length(); i++) {
                org.json.JSONObject x = five.getJSONObject(i);
                String id = x.optJSONObject("team") == null ? "" : x.getJSONObject("team").optString("id");
                StringBuilder r = new StringBuilder();
                org.json.JSONArray ev = x.optJSONArray("events");
                for (int k = 0; ev != null && k < ev.length() && r.length() < 5; k++) r.append(ev.getJSONObject(k).optString("gameResult", "-"));
                if (id.equals(g.homeId)) fh = r.toString(); else if (id.equals(g.awayId)) fa = r.toString();
            }
            if (fh != null && fa != null) pages.add(new String[]{"FORM · LAST FIVE", fh, fa, "form"});
            // The league table: each side's place and points.
            org.json.JSONObject table = d.optJSONObject("standings");
            org.json.JSONArray groups = table == null ? null : table.optJSONArray("groups");
            String ph = null, pa = null;
            for (int gi = 0; groups != null && gi < groups.length(); gi++) {
                org.json.JSONObject st = groups.getJSONObject(gi).optJSONObject("standings");
                org.json.JSONArray entries = st == null ? null : st.optJSONArray("entries");
                for (int i = 0; entries != null && i < entries.length(); i++) {
                    org.json.JSONObject e = entries.getJSONObject(i);
                    Map<String, String> v = new HashMap<>();
                    org.json.JSONArray es = e.optJSONArray("stats");
                    for (int k = 0; es != null && k < es.length(); k++) v.put(es.getJSONObject(k).optString("name"), es.getJSONObject(k).optString("displayValue"));
                    String place = ordinal(intOf(v.get("rank"))) + " · " + v.getOrDefault("points", "0") + " pts";
                    if (e.optString("id").equals(g.homeId)) ph = place;
                    if (e.optString("id").equals(g.awayId)) pa = place;
                }
            }
            if (ph != null && pa != null) pages.add(new String[]{"LEAGUE TABLE", ph, pa, "text"});
            if (g.state == 1 && g.venue != null) pages.add(0, new String[]{"KICK-OFF " + minute(g), g.venue, "", "venue"});
        } catch (Exception ignored) {
        }
        if (!pages.isEmpty()) statPages.put(g.id, pages);
    }

    private static String pct(String v) {
        try { return Math.round(Float.parseFloat(v)) + "%"; } catch (RuntimeException e) { return v == null ? "" : v; }
    }

    private static String passes(Map<String, String> m) {
        int ok = intOf(m.get("accuratePasses")), all = intOf(m.get("totalPasses"));
        return all > 0 ? Math.round(ok * 100f / all) + "%" : "";
    }

    /** "15/22": the ones that came off, of all tried. */
    private static String of(Map<String, String> m, String done, String tried) {
        return intOf(m.get(done)) + "/" + intOf(m.get(tried));
    }

    /**
     * Each side's scorers in the order they scored, a scorer's goals together: "Ünal 68' · Satriano 73'",
     * "Mbappé 11' pen, 54'". Null before a goal.
     */
    private static String[] goalsPage(ScoresTicker.Game g, org.json.JSONArray events) throws org.json.JSONException {
        Map<String, List<String>> home = new java.util.LinkedHashMap<>(), away = new java.util.LinkedHashMap<>();
        for (int i = 0; events != null && i < events.length(); i++) {
            org.json.JSONObject e = events.getJSONObject(i);
            if (!e.optBoolean("scoringPlay")) continue;
            String type = e.optJSONObject("type") == null ? "" : e.getJSONObject("type").optString("text").toLowerCase(Locale.ROOT);
            if (type.contains("shootout")) continue;
            org.json.JSONArray ps = e.optJSONArray("participants");
            org.json.JSONObject who = ps == null || ps.length() == 0 ? null : ps.getJSONObject(0).optJSONObject("athlete");
            String name = who == null ? "" : GoalWatchService.surname(who.optString("displayName"));
            if (name.isEmpty()) continue;
            String at = e.optJSONObject("clock") == null ? "" : e.getJSONObject("clock").optString("displayValue").replace("'+", "+");
            if (type.contains("penalty")) at += " pen";
            else if (type.contains("own goal")) at += " og";
            String team = e.optJSONObject("team") == null ? "" : e.getJSONObject("team").optString("id");
            (team.equals(g.awayId) ? away : home).computeIfAbsent(name, x -> new ArrayList<>()).add(at);
        }
        if (home.isEmpty() && away.isEmpty()) return null;
        return new String[]{"GOALS", scorers(home), scorers(away), "goals"};
    }

    private static String scorers(Map<String, List<String>> m) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, List<String>> e : m.entrySet()) {
            if (b.length() > 0) b.append("  ·  ");
            b.append(e.getKey()).append(' ').append(TextUtils.join(", ", e.getValue()));
        }
        return b.toString();
    }

    /**
     * The player of the match so far, from both line-ups' own figures: goals count most, then assists, shots on
     * target, saves and fouls won, less cards and own goals. {label, name, what he has done, "player", his crest}.
     * Null until someone has done enough (a goal, or a couple of shots on target, or a few saves).
     */
    private static String[] starPage(ScoresTicker.Game g, org.json.JSONArray rosters) throws org.json.JSONException {
        double best = 3.99;
        String[] pick = null;
        for (int r = 0; rosters != null && r < rosters.length(); r++) {
            org.json.JSONObject side = rosters.getJSONObject(r);
            boolean home = "home".equals(side.optString("homeAway"));
            org.json.JSONArray players = side.optJSONArray("roster");
            for (int i = 0; players != null && i < players.length(); i++) {
                org.json.JSONObject pl = players.getJSONObject(i);
                Map<String, Double> v = new HashMap<>();
                org.json.JSONArray st = pl.optJSONArray("stats");
                for (int k = 0; st != null && k < st.length(); k++) v.put(st.getJSONObject(k).optString("name"), st.getJSONObject(k).optDouble("value", 0));
                double goals = v.getOrDefault("totalGoals", 0d), assists = v.getOrDefault("goalAssists", 0d);
                double onTarget = v.getOrDefault("shotsOnTarget", 0d), shots = v.getOrDefault("totalShots", 0d), saves = v.getOrDefault("saves", 0d);
                double score = 10 * goals + 6 * assists + 2 * onTarget + 0.5 * Math.max(0, shots - onTarget) + 1.5 * saves
                        + 0.3 * v.getOrDefault("foulsSuffered", 0d) - 2 * v.getOrDefault("yellowCards", 0d)
                        - 6 * v.getOrDefault("redCards", 0d) - 6 * v.getOrDefault("ownGoals", 0d);
                if (score <= best) continue;
                org.json.JSONObject who = pl.optJSONObject("athlete");
                if (who == null) continue;
                List<String> did = new ArrayList<>();
                if (goals > 0) did.add(count((int) goals, "goal"));
                if (assists > 0) did.add(count((int) assists, "assist"));
                if (goals == 0 && onTarget > 0) did.add(count((int) onTarget, "shot") + " on target");
                if (saves > 0) did.add(count((int) saves, "save"));
                best = score;
                pick = new String[]{"PLAYER OF THE MATCH", who.optString("displayName"), TextUtils.join("  ·  ", did), "player",
                        home ? g.homeLogo : g.awayLogo};
            }
        }
        return pick;
    }

    private static String count(int n, String what) {
        return n + " " + what + (n == 1 ? "" : "s");
    }

    /** Both sides' formations once the line-ups are out ("4-2-3-1"). */
    private static String[] formations(ScoresTicker.Game g, org.json.JSONArray rosters) throws org.json.JSONException {
        String h = null, a = null;
        for (int r = 0; rosters != null && r < rosters.length(); r++) {
            org.json.JSONObject side = rosters.getJSONObject(r);
            String f = side.optString("formation", "");
            if (f.isEmpty()) continue;
            if ("home".equals(side.optString("homeAway"))) h = f; else a = f;
        }
        return h == null || a == null ? null : new String[]{"FORMATIONS", h, a, "text"};
    }

    /**
     * Each side's chance of winning, and of a draw, as the odds before kick-off put them (each price as a chance, the
     * three scaled to make 100). {label, home, away, "three", draw}. Null without prices.
     */
    private static String[] winChance(org.json.JSONArray odds) {
        org.json.JSONObject o = odds == null || odds.length() == 0 ? null : odds.optJSONObject(0);
        if (o == null) return null;
        double h = chance(o.optJSONObject("homeTeamOdds")), a = chance(o.optJSONObject("awayTeamOdds")), d = chance(o.optJSONObject("drawOdds"));
        if (h <= 0 || a <= 0 || d <= 0) return null;
        double all = h + a + d;
        int ph = (int) Math.round(100 * h / all), pa = (int) Math.round(100 * a / all);
        return new String[]{"WIN CHANCE  ·  BEFORE KICK-OFF", ph + "%", pa + "%", "three", Math.max(0, 100 - ph - pa) + "%"};
    }

    /** A price (American odds, +260 or -500) as the chance it stands for. */
    private static double chance(org.json.JSONObject x) {
        if (x == null || !x.has("moneyLine")) return 0;
        double m = x.optDouble("moneyLine", 0);
        if (m == 0 || Double.isNaN(m)) return 0;
        return m > 0 ? 100 / (m + 100) : -m / (-m + 100);
    }

    private static String ordinal(int n) {
        int t = n % 100;
        String s = t >= 11 && t <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + s;
    }

    /**
     * The scoreboard alone, then its stats folding out of it: PAGES_OPEN pages of stats and facts, one after the
     * other, then folded back in, and the scoreboard alone again. Nothing folds out until the game has stats.
     */
    private static final Runnable statsTurn = new Runnable() {
        @Override public void run() {
            if (host == null) return;
            ScoresTicker.Game g = shown();
            List<String[]> pages = g == null ? null : statPages.get(g.id);
            if (!statsOpen) {
                if (pages == null || pages.isEmpty() || boardHidden) { main.postDelayed(this, SHUT_MS / 3); return; }
                statAt++;
                fillStats(app, false);
                openStats(true);
                pagesShown = 1;
                main.postDelayed(this, STATS_MS);
            } else if (pagesShown < Math.min(PAGES_OPEN, pages == null ? 1 : pages.size())) {
                statAt++;
                fillStats(app, true);
                pagesShown++;
                main.postDelayed(this, STATS_MS);
            } else {
                openStats(false);
                main.postDelayed(this, SHUT_MS);
            }
        }
    };

    /** The stats fold out of the scoreboard's right end (slowing as they come), or back in behind it. */
    private static void openStats(boolean open) {
        statsOpen = open;
        if (statsBox == null) return;
        statsBox.animate().cancel();
        statsBox.animate().translationX(open ? 0f : -px(STATS_W + TUCK)).setDuration(open ? 620 : 460)
                .setInterpolator(open ? new DecelerateInterpolator(2.4f) : new android.view.animation.AccelerateInterpolator(1.8f))
                .start();
    }

    /** Another game on the scoreboard: its stats fold out soon (or, already out, show it a page or two more). */
    private static void restartStats() {
        main.removeCallbacks(statsTurn);
        pagesShown = 0;
        main.postDelayed(statsTurn, statsOpen ? STATS_MS : 1_200L);
    }

    /** The stats panel's ground: the scoreboard's own white. */
    private static android.graphics.drawable.Drawable statsGround() {
        GradientDrawable sb = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFFFFFFF, 0xFFE9ECF5});
        sb.setCornerRadius(px(14));
        return sb;
    }

    /** One page of the stats: its label across the top, the home side's figure left, the away side's right. */
    private static void fillStats(Context c, boolean turn) {
        if (statsBox == null) return;
        View old = statsBox.getChildCount() > 0 ? statsBox.getChildAt(statsBox.getChildCount() - 1) : null;
        View page = statsPage(c, shown());
        statsBox.addView(page, new FrameLayout.LayoutParams(-1, -1));
        if (old != null && turn) {
            page.setAlpha(0f);
            page.setTranslationY(px(18));
            page.animate().alpha(1f).translationY(0).setDuration(320).start();
            old.animate().alpha(0f).translationY(-px(18)).setDuration(260).withEndAction(() -> statsBox.removeView(old)).start();
        } else if (old != null) {
            statsBox.removeView(old);
        }
    }

    /** The game's stats page now showing (statAt), or its competition while it has none. */
    private static View statsPage(Context c, ScoresTicker.Game g) {
        List<String[]> pages = g == null ? null : statPages.get(g.id);
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(px(22), px(10), px(22), px(10));
        if (pages == null || pages.isEmpty()) {
            TextView t = text(c, g == null ? "" : ScoresTicker.competition(g), 22, STAT_LABEL, ScoreCard.outfit(c, 600));
            page.addView(t);
        } else {
            String[] p = pages.get(Math.floorMod(statAt, pages.size()));
            TextView label = text(c, p[0], 16, STAT_LABEL, ScoreCard.outfit(c, 800));
            label.setLetterSpacing(0.14f);
            page.addView(label);
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = px(8);
            page.addView(row, rl);
            int room = px(STATS_W) - px(44);
            if ("venue".equals(p[3])) {
                // The ground's name as large as the panel takes it: 34 for "Anfield", smaller for a long one.
                TextView v = text(c, p[1], 34, INK, ScoreCard.outfit(c, 800));
                v.setGravity(Gravity.CENTER);
                fit(v, 34, 20, room);
                row.addView(v, new LinearLayout.LayoutParams(-1, -2));
            } else if ("goals".equals(p[3])) {
                // Each side's scorers in as much of the row as they need: a side yet to score leaves its room to the other.
                TextView hs = text(c, p[1].isEmpty() ? "–" : p[1], 24, INK, ScoreCard.outfit(c, 700));
                TextView as = text(c, p[2].isEmpty() ? "–" : p[2], 24, INK, ScoreCard.outfit(c, 700));
                as.setGravity(Gravity.END);
                int avail = room - px(18);
                float wh = hs.getPaint().measureText(hs.getText().toString()), wa = as.getPaint().measureText(as.getText().toString());
                int bh = wh + wa <= avail ? (int) Math.ceil(wh) : Math.round(avail * wh / (wh + wa));
                int ba = avail - bh;
                fit(hs, 24, 14, bh);
                fit(as, 24, 14, ba);
                row.addView(hs, new LinearLayout.LayoutParams(bh, -2));
                row.addView(new View(c), new LinearLayout.LayoutParams(px(18), 1));
                row.addView(as, new LinearLayout.LayoutParams(ba, -2));
            } else if ("player".equals(p[3])) {
                ImageView crest = new ImageView(c);
                crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
                loadCrest(crest, p.length > 4 ? p[4] : null);
                row.addView(crest, new LinearLayout.LayoutParams(px(46), px(46)));
                LinearLayout who = new LinearLayout(c);
                who.setOrientation(LinearLayout.VERTICAL);
                TextView name = text(c, p[1], 30, INK, ScoreCard.outfit(c, 800));
                fit(name, 30, 18, room - px(60));
                who.addView(name);
                TextView did = text(c, p[2], 18, STAT_LABEL, ScoreCard.outfit(c, 600));
                fit(did, 18, 13, room - px(60));
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
                dl.topMargin = px(2);
                who.addView(did, dl);
                LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
                wl.leftMargin = px(14);
                row.addView(who, wl);
            } else if ("three".equals(p[3])) {
                row.addView(text(c, p[1], 34, INK, ScoreCard.outfit(c, 800)), new LinearLayout.LayoutParams(-2, -2));
                LinearLayout mid = new LinearLayout(c);
                mid.setOrientation(LinearLayout.VERTICAL);
                mid.setGravity(Gravity.CENTER_HORIZONTAL);
                TextView draw = text(c, "DRAW " + (p.length > 4 ? p[4] : ""), 14, STAT_LABEL, ScoreCard.outfit(c, 800));
                draw.setGravity(Gravity.CENTER);
                mid.addView(draw);
                LinearLayout.LayoutParams bl3 = new LinearLayout.LayoutParams(-1, px(10));
                bl3.topMargin = px(5);
                mid.addView(split3(c, p[1], p.length > 4 ? p[4] : "0", p[2]), bl3);
                LinearLayout.LayoutParams ml3 = new LinearLayout.LayoutParams(0, -2, 1f);
                ml3.leftMargin = px(16);
                ml3.rightMargin = px(16);
                row.addView(mid, ml3);
                row.addView(text(c, p[2], 34, INK, ScoreCard.outfit(c, 800)), new LinearLayout.LayoutParams(-2, -2));
            } else if ("form".equals(p[3])) {
                row.addView(form(c, p[1]), new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(new View(c), new LinearLayout.LayoutParams(px(18), 1));
                row.addView(form(c, p[2]), new LinearLayout.LayoutParams(0, -2, 1f));
            } else {
                TextView hv = text(c, p[1], 34, INK, ScoreCard.outfit(c, 800));
                row.addView(hv, new LinearLayout.LayoutParams(-2, -2));
                if ("bar".equals(p[3])) {
                    LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, px(10), 1f);
                    bl.leftMargin = px(16);
                    bl.rightMargin = px(16);
                    row.addView(split(c, p[1], p[2]), bl);
                } else {
                    row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
                }
                TextView av = text(c, p[2], 34, INK, ScoreCard.outfit(c, 800));
                row.addView(av, new LinearLayout.LayoutParams(-2, -2));
            }
        }
        return page;
    }

    /** The two sides' share of a stat as one bar in their colours (the home side's from the left). */
    private static View split(Context c, String hv, String av) {
        float h = leadingNumber(hv), a = leadingNumber(av);
        float share = h + a <= 0 ? 0.5f : h / (h + a);
        LinearLayout bar = new LinearLayout(c);
        bar.setClipToOutline(true);
        bar.setOutlineProvider(round(px(5)));
        // Both sides in the scoreboard's red, the side ahead in full and the other paler.
        View l = new View(c), r = new View(c);
        l.setBackgroundColor(share >= 0.5f ? 0xFFE3283A : 0xFFF2A3AB);
        r.setBackgroundColor(share >= 0.5f && share != 0.5f ? 0xFFF2A3AB : 0xFFE3283A);
        bar.addView(l, new LinearLayout.LayoutParams(0, -1, Math.max(0.02f, share)));
        View gap = new View(c);
        gap.setBackgroundColor(0xFFF4F5FA);
        bar.addView(gap, new LinearLayout.LayoutParams(px(3), -1));
        bar.addView(r, new LinearLayout.LayoutParams(0, -1, Math.max(0.02f, 1f - share)));
        return bar;
    }

    /** Win, draw, win as one bar: the favourite's share in the scoreboard's red, the other paler, the draw grey. */
    private static View split3(Context c, String hv, String dv, String av) {
        float h = leadingNumber(hv), d = leadingNumber(dv), a = leadingNumber(av);
        LinearLayout bar = new LinearLayout(c);
        bar.setClipToOutline(true);
        bar.setOutlineProvider(round(px(5)));
        float[] parts = {h, d, a};
        int[] colours = {h >= a ? 0xFFE3283A : 0xFFF2A3AB, 0xFFC4C8DC, a > h ? 0xFFE3283A : 0xFFF2A3AB};
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                View gap = new View(c);
                gap.setBackgroundColor(0xFFF4F5FA);
                bar.addView(gap, new LinearLayout.LayoutParams(px(3), -1));
            }
            View v = new View(c);
            v.setBackgroundColor(colours[i]);
            bar.addView(v, new LinearLayout.LayoutParams(0, -1, Math.max(0.02f, parts[i])));
        }
        return bar;
    }

    /** The text at up to maxPx (design pixels), smaller as needed, down to minPx, to fit in widthPx on one line. */
    private static void fit(TextView v, float maxPx, float minPx, int widthPx) {
        android.graphics.Paint pt = new android.graphics.Paint(v.getPaint());
        float size = maxPx * k;
        pt.setTextSize(size);
        float w = pt.measureText(v.getText().toString());
        if (w > widthPx && w > 0) size = Math.max(minPx * k, size * widthPx / w * 0.98f);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }

    private static float leadingNumber(String v) {
        if (v == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[0-9]+(\\.[0-9]+)?").matcher(v);
        return m.find() ? Float.parseFloat(m.group()) : 0;
    }

    /** Last five as chips: W green, D grey, L red. */
    private static View form(Context c, String results) {
        LinearLayout f = new LinearLayout(c);
        f.setGravity(Gravity.CENTER);
        for (int i = 0; i < results.length(); i++) {
            char r = results.charAt(i);
            TextView chip = text(c, String.valueOf(r), 20, 0xFFFFFFFF, ScoreCard.outfit(c, 900));
            chip.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(px(6));
            bg.setColor(r == 'W' ? 0xFF1F9D5C : r == 'L' ? 0xFFD0182B : 0xFF5A6090);
            chip.setBackground(bg);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(36), px(36));
            if (i > 0) cl.leftMargin = px(6);
            f.addView(chip, cl);
        }
        return f;
    }

    /** The column's league block: the league's name and logo, then each game's score, its minute at the end. */
    private static void fillLeague(Context c) {
        leagueBox.removeAllViews();
        // The pages in turn: a league's scores, then its live table while it has games under way ("t:" + slug).
        List<String> pages = new ArrayList<>();
        for (String e : LEAGUES) {
            String slug = e.split("\\|")[0];
            boolean any = false, live = false;
            for (ScoresTicker.Game g : games) if (slug.equals(g.slug)) { any = true; live |= g.state == 0 || testCard; }
            if (any) pages.add(slug);
            if (live && tables.containsKey(slug)) pages.add("t:" + slug);
        }
        if (pages.isEmpty()) {
            leagueBox.addView(text(c, "No games today", 20, 0xFFC9CEF2, ScoreCard.outfit(c, 500)));
            return;
        }
        String page = pages.get(Math.floorMod(leagueAt, pages.size()));
        if (page.startsWith("t:")) { fillTable(c, page.substring(2)); return; }
        String slug = page;
        String name = slug;
        for (String e : LEAGUES) if (e.startsWith(slug + "|")) name = e.split("\\|")[1];
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        for (ScoresTicker.Game g : games) if (slug.equals(g.slug) && g.leagueLogo != null) { loadCrest(logo, g.leagueLogo); break; }
        head.addView(logo, new LinearLayout.LayoutParams(px(30), px(30)));
        TextView ln = text(c, titleCase(name), 24, 0xFFFFFFFF, ScoreCard.outfit(c, 600));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
        nl.leftMargin = px(10);
        head.addView(ln, nl);
        leagueBox.addView(head);
        int n = 0;
        for (ScoresTicker.Game g : games) {
            if (!slug.equals(g.slug)) continue;
            if (n++ >= 8) break;
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, px(9), 0, 0);
            TextView h = text(c, g.home, 19, 0xFFFFFFFF, ScoreCard.outfit(c, 600));
            row.addView(h, new LinearLayout.LayoutParams(px(66), -2));
            TextView s = text(c, g.state == 1 ? "v" : score(g.homeScore) + "–" + score(g.awayScore), 19, INK, ScoreCard.outfit(c, 800));
            s.setGravity(Gravity.CENTER);
            s.setBackgroundColor(0xFFFFFFFF);
            row.addView(s, new LinearLayout.LayoutParams(px(60), px(28)));
            TextView a = text(c, g.away, 19, 0xFFFFFFFF, ScoreCard.outfit(c, 600));
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(px(66), -2);
            al.leftMargin = px(12);
            row.addView(a, al);
            TextView m = text(c, minute(g), 15, g.state == 0 ? GREEN : 0xFFAEB5E8, ScoreCard.outfit(c, 700));
            m.setGravity(Gravity.END);
            row.addView(m, new LinearLayout.LayoutParams(0, -2, 1f));
            leagueBox.addView(row);
        }
        leagueBox.setAlpha(0f);
        leagueBox.animate().alpha(1f).setDuration(300).withLayer().start();
    }

    /**
     * A league's live table, every club: its place (and the places it has moved since kick-off, in green or red), its
     * crest and name, played, goal difference and points; the clubs playing now on a green wash with their score.
     */
    private static void fillTable(Context c, String slug) {
        List<Object[]> rows = liveTable(slug);
        if (rows == null) return;
        String name = slug;
        for (String e : LEAGUES) if (e.startsWith(slug + "|")) name = e.split("\\|")[1];
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        for (ScoresTicker.Game g : games) if (slug.equals(g.slug) && g.leagueLogo != null) { loadCrest(logo, g.leagueLogo); break; }
        head.addView(logo, new LinearLayout.LayoutParams(px(30), px(30)));
        TextView ln = text(c, titleCase(name), 24, 0xFFFFFFFF, ScoreCard.outfit(c, 600));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
        nl.leftMargin = px(10);
        head.addView(ln, nl);
        TextView tag = text(c, "LIVE TABLE", 13, 0xFF062814, ScoreCard.outfit(c, 900));
        tag.setLetterSpacing(0.08f);
        tag.setPadding(px(8), px(4), px(8), px(4));
        GradientDrawable tb = new GradientDrawable();
        tb.setColor(GREEN);
        tb.setCornerRadius(px(4));
        tag.setBackground(tb);
        head.addView(tag);
        leagueBox.addView(head);
        leagueBox.addView(tableLine(c, null, "", "", "P", "GD", "PTS", null, 0, true), new LinearLayout.LayoutParams(-1, px(24)));
        int max = Math.min(rows.size(), 20);
        for (int i = 0; i < max; i++) {
            Object[] r = rows.get(i);
            Club club = (Club) r[0];
            int moved = (Integer) r[1] - (i + 1);
            leagueBox.addView(tableLine(c, club.logo, String.valueOf(i + 1), shortName(club.name, club.abbr), String.valueOf(club.played),
                    (club.gd > 0 ? "+" : "") + club.gd, String.valueOf(club.pts), (String) r[2], moved, false),
                    new LinearLayout.LayoutParams(-1, px(rows.size() > 18 ? 26 : 28)));
        }
        leagueBox.setAlpha(0f);
        leagueBox.animate().alpha(1f).setDuration(300).withLayer().start();
    }

    private static View tableLine(Context c, String crest, String pos, String name, String p, String gd, String pts, String score,
                                  int moved, boolean header) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(4), 0, px(4), 0);
        if (score != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x293DDC84);
            bg.setCornerRadius(px(5));
            r.setBackground(bg);
        }
        int muted = 0xFFAEB5E8;
        float size = header ? 12 : 15;
        TextView ps = text(c, pos, size, header ? muted : 0xFFFFFFFF, ScoreCard.outfit(c, 700));
        ps.setGravity(Gravity.END);
        r.addView(ps, new LinearLayout.LayoutParams(px(22), -2));
        TextView mv = text(c, moved > 0 ? "▲" : moved < 0 ? "▼" : "", 10, moved > 0 ? GREEN : 0xFFFF5A5F, ScoreCard.outfit(c, 700));
        mv.setGravity(Gravity.CENTER);
        r.addView(mv, new LinearLayout.LayoutParams(px(16), -2));
        ImageView cr = new ImageView(c);
        cr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (crest != null) loadCrest(cr, crest);
        r.addView(cr, new LinearLayout.LayoutParams(px(18), px(18)));
        TextView n = text(c, name, size, 0xFFFFFFFF, ScoreCard.outfit(c, header ? 700 : 600));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
        nl.leftMargin = px(8);
        r.addView(n, nl);
        if (score != null) {
            TextView sc = text(c, score, 12, GREEN, ScoreCard.outfit(c, 800));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
            sl.rightMargin = px(6);
            r.addView(sc, sl);
        }
        for (String[] col : new String[][]{{p, "30"}, {gd, "40"}}) {
            TextView t = text(c, col[0], size, header ? muted : 0xFFC9CEF2, ScoreCard.outfit(c, 600));
            t.setGravity(Gravity.END);
            r.addView(t, new LinearLayout.LayoutParams(px(Integer.parseInt(col[1])), -2));
        }
        TextView pt = text(c, pts, size, header ? muted : 0xFFFFFFFF, ScoreCard.outfit(c, 800));
        pt.setGravity(Gravity.END);
        r.addView(pt, new LinearLayout.LayoutParams(px(40), -2));
        return r;
    }

    /** One Premier League game large at the column's foot, as Sky shows one: each side and its score in a box. */
    private static void fillFeatured(Context c) {
        featured.removeAllViews();
        List<ScoresTicker.Game> pl = new ArrayList<>();
        for (ScoresTicker.Game g : games) if ("eng.1".equals(g.slug) && g.state == 0) pl.add(g);
        if (pl.isEmpty()) for (ScoresTicker.Game g : games) if ("eng.1".equals(g.slug) && g.state == 2) pl.add(g);
        if (pl.isEmpty()) for (ScoresTicker.Game g : games) if ("eng.1".equals(g.slug)) pl.add(g);
        if (pl.isEmpty()) return;
        ScoresTicker.Game g = pl.get(Math.floorMod(featuredAt, pl.size()));
        View rule = new View(c);
        rule.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, Math.max(1, px(1.5f)));
        rl.bottomMargin = px(14);
        featured.addView(rule, rl);
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(c, "Premier League", 20, 0xFFC9CEF2, ScoreCard.outfit(c, 500)), new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(text(c, minute(g), 18, g.state == 0 ? GREEN : 0xFFAEB5E8, ScoreCard.outfit(c, 700)));
        featured.addView(top);
        for (boolean home : new boolean[]{true, false}) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, px(10), 0, 0);
            TextView name = text(c, shortName(home ? g.homeName : g.awayName, home ? g.home : g.away), 30, 0xFFFFFFFF, ScoreCard.outfit(c, 700));
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView s = text(c, g.state == 1 ? "" : score(home ? g.homeScore : g.awayScore), 30, INK, ScoreCard.outfit(c, 800));
            s.setGravity(Gravity.CENTER);
            s.setBackgroundColor(0xFFFFFFFF);
            // Before kick-off: no score, so no box.
            if (g.state == 1) s.setVisibility(View.INVISIBLE);
            row.addView(s, new LinearLayout.LayoutParams(px(44), px(44)));
            featured.addView(row);
        }
    }

    /** The ticker: every game's score, league by league, sliding along the white band without end. */
    private static void fillTicker(Context c) {
        StringBuilder s = new StringBuilder();
        String last = null;
        for (ScoresTicker.Game g : games) {
            if (!java.util.Objects.equals(g.slug, last)) {
                last = g.slug;
                for (String e : LEAGUES) if (e.startsWith(g.slug + "|")) s.append("     ").append(e.split("\\|")[1]).append("   ");
            }
            if (g.state == 1) s.append(g.home).append(" v ").append(g.away).append(" (").append(minute(g)).append(")    ·    ");
            else s.append(g.home).append(' ').append(score(g.homeScore)).append('–').append(score(g.awayScore)).append(' ').append(g.away)
                    .append(" (").append(minute(g)).append(")    ·    ");
        }
        F1Results.Weekend w = f1;
        if (w != null) {
            for (F1Results.Session x : w.sessions) {
                if (!"in".equals(x.state) || x.top.isEmpty()) continue;
                s.append("     F1  ").append(x.isRace() && x.lap > 0 ? "LAP " + x.lap : F1Results.label(x.abbr)).append("   ");
                for (int i = 0; i < Math.min(5, x.top.size()); i++) s.append(i + 1).append(' ').append(F1Results.code(x.top.get(i))).append("   ");
                s.append(" ·    ");
            }
        }
        if (s.length() == 0) s.append("     No games today     ");
        String text = s.toString();
        if (text.equals(tickerText.getText().toString())) return;
        // Sliding already: the new scores go in when this pass ends, so the line never jumps back mid-way.
        if (slide != null && slide.isRunning()) { pendingTicker = text; return; }
        slideTicker(text);
    }

    private static String pendingTicker;

    private static void slideTicker(String text) {
        if (host == null) return;
        tickerText.setText(text);
        // Its own width, the whole line: as wrap_content the band would cut it at the band's width.
        tickerText.setLayoutParams(new FrameLayout.LayoutParams((int) Math.ceil(tickerText.getPaint().measureText(text)) + px(40), -1,
                Gravity.CENTER_VERTICAL));
        if (slide != null) slide.cancel();
        tickerText.post(() -> {
            int w = tickerText.getWidth(), band = tickerLine.getWidth();
            if (w <= 0 || band <= 0 || host == null) return;
            slide = android.animation.ObjectAnimator.ofFloat(tickerText, View.TRANSLATION_X, band, -w);
            slide.setDuration(Math.max(10_000L, (long) ((band + w) / (float) px(110) * 1000)));
            slide.setInterpolator(new LinearInterpolator());
            slide.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            slide.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationRepeat(android.animation.Animator a) {
                    String next = pendingTicker;
                    pendingTicker = null;
                    if (next != null) main.post(() -> slideTicker(next));
                }
            });
            slide.start();
            // The first pass starts with the line already at the band's left edge, so it is there at once.
            slide.setCurrentFraction(band / (float) (band + w));
        });
    }

    /** The latest four events, each as the score cards show one: its label on a slanted block, the game, who and when. */
    private static void fillEvents(Context c) {
        eventsBox.removeAllViews();
        int n = 0;
        for (String[] e : events) {
            if (n++ >= 4) break;
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int colour = eventColour(e[0]);
            TextView label = text(c, e[0], 15, 0xFFFFFFFF, ScoreCard.outfit(c, 900));
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setPadding(px(10), 0, px(18), 0);
            label.setBackground(new ScoreCard.Slant(colour, px(8), false));
            row.addView(label, new LinearLayout.LayoutParams(px(100), px(26)));
            TextView match = text(c, e[1], 17, 0xFFFFFFFF, ScoreCard.outfit(c, 700));
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, -2, 1.2f);
            ml.leftMargin = px(14);
            row.addView(match, ml);
            TextView who = text(c, e[2] + (e[3].isEmpty() ? "" : " (" + e[3] + ")"), 16, 0xFFFFD166, ScoreCard.outfit(c, 600));
            row.addView(who, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, 0, 1f);
            eventsBox.addView(row, rl);
        }
        if (n == 0) eventsBox.addView(text(c, "Goals, red cards and results show here as they happen", 16, 0xFFC9CEF2, ScoreCard.outfit(c, 500)));
    }

    /** Where the game would be when it is on no channel: the game large, and that it is on nowhere. */
    private static void fillCentre(Context c) {
        centre.removeAllViews();
        if (!(centre.getBackground() != null)) return;
        ScoresTicker.Game g = shown();
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        if (g != null) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView hc = new ImageView(c), ac = new ImageView(c);
            hc.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ac.setScaleType(ImageView.ScaleType.FIT_CENTER);
            loadCrest(hc, g.homeLogo);
            loadCrest(ac, g.awayLogo);
            row.addView(hc, new LinearLayout.LayoutParams(px(120), px(120)));
            TextView s = text(c, score(g.homeScore) + " – " + score(g.awayScore), 96, 0xFFFFFFFF, ScoreCard.outfit(c, 900));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
            sl.leftMargin = sl.rightMargin = px(40);
            row.addView(s, sl);
            row.addView(ac, new LinearLayout.LayoutParams(px(120), px(120)));
            v.addView(row);
            TextView names = text(c, (g.homeName == null ? g.home : g.homeName) + " v " + (g.awayName == null ? g.away : g.awayName), 30,
                    0xFFFFFFFF, ScoreCard.outfit(c, 700));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
            nl.topMargin = px(18);
            v.addView(names, nl);
        }
        TextView note = text(c, "Not on any of your channels · followed live here", 18, 0xFFAEB5E8, ScoreCard.outfit(c, 500));
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, -2);
        ol.topMargin = px(10);
        v.addView(note, ol);
        centre.addView(v, new FrameLayout.LayoutParams(-1, -1));
    }

    // ---- what happens -------------------------------------------------------------------------------------------

    /**
     * New goals, red cards and results since the last read, newest first. The first read only learns what has
     * happened already (all of it, newest minute first), so the foot is never empty.
     */
    private static void learn(List<ScoresTicker.Game> read, boolean first) {
        long now = System.currentTimeMillis();
        List<Object[]> fresh = new ArrayList<>();
        for (ScoresTicker.Game g : read) {
            String line = g.home + " " + score(g.homeScore) + "  " + g.away + " " + score(g.awayScore);
            for (String[] goal : g.goals) {
                String key = g.id + "|" + goal[0];
                if (seen.containsKey(key)) continue;
                seen.put(key, now);
                if (!first && clipWanted(g)) GoalClips.look(g, GoalWatchService.surname(goal[1]), goal[2], line);
                fresh.add(new Object[]{minuteValue(goal[2]), new String[]{"GOAL", line, GoalWatchService.surname(goal[1]), goal[2], goal[3]}});
            }
            for (String[] red : g.reds) {
                String key = g.id + "|r" + red[0];
                if (seen.containsKey(key)) continue;
                seen.put(key, now);
                fresh.add(new Object[]{minuteValue(red[2]), new String[]{"RED", line, GoalWatchService.surname(red[1]), red[2], red[3]}});
            }
            List<String[]> inc = incidents.get(g.id);
            for (int i = 0; inc != null && i < inc.size(); i++) {
                String[] x = inc.get(i);
                String key = g.id + "|i" + x[0];
                if (seen.containsKey(key)) continue;
                seen.put(key, now);
                String what = x[1] == null ? "" : x[1].replaceAll("^[^A-Za-z]+", "").trim();
                boolean pen = what.toLowerCase(Locale.UK).contains("pen");
                String who = x[2] == null || x[2].isEmpty() ? what : GoalWatchService.surname(x[2]) + " · " + what;
                fresh.add(new Object[]{minuteValue(x[3]), new String[]{pen ? "PEN" : "VAR", line, who, x[3] == null ? "" : x[3], ""}});
            }
            Integer before = lastStates.put(g.id, g.state);
            if (!first && before != null && before == 0 && g.state == 2) {
                fresh.add(new Object[]{999, new String[]{"FT", line, "Full time", "", ""}});
            }
        }
        // F1: lights out, a new leader, the result (or pole).
        F1Results.Weekend w = f1;
        if (w != null) {
            String gp = F1Results.shortName(w.name == null ? "" : w.name).replace("Grand Prix", "GP");
            for (F1Results.Session x : w.sessions) {
                if (!x.counts()) continue;
                String before = otherStates.put("f1|" + x.id, x.state);
                String leader = x.top.isEmpty() ? null : x.top.get(0);
                String lastLeader = leader == null ? null : otherStates.put("f1l|" + x.id, leader);
                if (first || before == null) continue;
                String where = gp + " · " + F1Results.label(x.abbr).charAt(0) + F1Results.label(x.abbr).substring(1).toLowerCase(Locale.UK);
                if ("pre".equals(before) && "in".equals(x.state)) {
                    fresh.add(new Object[]{1000, new String[]{"F1", where, x.isRace() ? "Lights out" : "Under way", "", ""}});
                } else if ("in".equals(before) && "post".equals(x.state) && leader != null) {
                    String rest = (x.top.size() > 1 ? " · P2 " + x.top.get(1) : "") + (x.top.size() > 2 ? " · P3 " + x.top.get(2) : "");
                    fresh.add(new Object[]{1000, new String[]{"F1", where, (x.isRace() ? "Winner " : "Pole ") + leader + rest, "", ""}});
                } else if ("in".equals(x.state) && leader != null && lastLeader != null && !leader.equals(lastLeader)) {
                    fresh.add(new Object[]{1000, new String[]{"F1", where + (x.isRace() && x.lap > 0 ? " · Lap " + x.lap : ""), leader + " leads", "", ""}});
                }
            }
        }
        // Tennis and UFC: each match or fight as it ends.
        for (MoreSports.Result r : others) {
            String before = otherStates.put("o|" + r.id, r.state);
            if (first || before == null || "post".equals(before) || !"post".equals(r.state)) continue;
            boolean ufc = "UFC".equals(r.tour);
            String where = ufc ? (r.event == null ? "UFC" : r.event) + (r.mainEvent ? " · Main event" : "")
                    : r.tour + (r.event == null ? "" : " · " + r.event) + (r.round == null ? "" : " · " + r.round);
            fresh.add(new Object[]{1000, new String[]{ufc ? "UFC" : "TENNIS", where,
                    r.winner + " beat " + r.loser + (r.score == null || r.score.isEmpty() ? "" : " · " + r.score), "", ""}});
        }
        fresh.sort((a, b) -> Integer.compare((Integer) b[0], (Integer) a[0]));
        List<String[]> add = new ArrayList<>();
        for (Object[] f : fresh) add.add((String[]) f[1]);
        events.addAll(0, add);
        while (events.size() > 20) events.remove(events.size() - 1);
        // What has just happened flashes across the ticker, the oldest first.
        if (!first) for (int i = add.size() - 1; i >= 0; i--) flash(add.get(i));
    }

    // ---- the ticker's flash ---------------------------------------------------------------------------------------

    private static final List<String[]> flashes = new ArrayList<>();
    private static View flashing;

    private static int eventColour(String kind) {
        switch (kind) {
            case "RED": return RED;
            case "FT": return NAVY;
            case "VAR": return 0xFF7B4DFF;
            case "PEN": return 0xFFFF8A1F;
            case "F1": return 0xFFE10600;
            case "UFC": return 0xFF2E2E36;
            case "TENNIS": return 0xFF3E8E2E;
            case "BOXING": return 0xFFB8901E;
            default: return 0xFF1F9D5C;
        }
    }

    /**
     * An event across the ticker's white band for nine seconds, as a news channel's breaking strap: its label on
     * the slant in its colour (GOAL green, RED red, VAR purple, PEN orange, FT navy), the game's score and who. The
     * scores slide on again after. Events that come together take their turn.
     */
    static void flash(String[] e) {
        if (host == null || tickerLine == null) return;
        if (flashing != null) { flashes.add(e); return; }
        Context c = app;
        FrameLayout band = (FrameLayout) tickerLine;
        LinearLayout strap = new LinearLayout(c);
        strap.setGravity(Gravity.CENTER_VERTICAL);
        strap.setBackgroundColor(0xFFFFFFFF);
        TextView label = text(c, e[0], 21, 0xFFFFFFFF, ScoreCard.outfit(c, 900));
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(px(22), 0, px(30), 0);
        label.setLetterSpacing(0.06f);
        label.setBackground(new ScoreCard.Slant(eventColour(e[0]), px(14), false));
        strap.addView(label, new LinearLayout.LayoutParams(-2, -1));
        TextView match = text(c, e[1], 21, INK, ScoreCard.outfit(c, 800));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
        ml.leftMargin = px(16);
        strap.addView(match, ml);
        TextView who = text(c, "·  " + e[2] + (e[3] == null || e[3].isEmpty() ? "" : "  " + e[3]), 21, 0xFF4A507F, ScoreCard.outfit(c, 700));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.leftMargin = px(14);
        strap.addView(who, wl);
        band.addView(strap, new FrameLayout.LayoutParams(-1, -1));
        flashing = strap;
        strap.setTranslationY(-band.getHeight());
        strap.animate().translationY(0).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).start();
        label.setScaleX(0.6f);
        label.setScaleY(0.6f);
        label.animate().scaleX(1f).scaleY(1f).setStartDelay(200).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
        main.postDelayed(() -> strap.animate().translationY(band.getHeight()).setDuration(280).withEndAction(() -> {
            band.removeView(strap);
            if (flashing == strap) flashing = null;
            if (!flashes.isEmpty() && host != null) flash(flashes.remove(0));
        }).start(), 9_000L);
    }

    /**
     * adb's test hook: a goal, a red card, a VAR decision, a penalty saved and full time in one of today's games
     * (the Premier League's first), each on the ticker and in the events, ten seconds apart.
     */
    static void testEvents() {
        if (host == null) return;
        ScoresTicker.Game g = null;
        for (ScoresTicker.Game x : games) if ("eng.1".equals(x.slug)) { g = x; break; }
        if (g == null && !games.isEmpty()) g = games.get(0);
        if (g == null) return;
        String h = g.home, a = g.away;
        String[][] script = {
                {"GOAL", h + " 1  " + a + " 0", "Salah", "23'", "home"},
                {"RED", h + " 1  " + a + " 0", "Saliba", "38'", "away"},
                {"VAR", h + " 1  " + a + " 0", "Goal ruled out · offside", "52'", ""},
                {"PEN", h + " 1  " + a + " 0", "Raya · Penalty saved", "64'", ""},
                {"FT", h + " 1  " + a + " 0", "Full time", "", ""},
                {"F1", "Mexico City GP · Race · Lap 23", "Norris leads", "", ""},
                {"UFC", "UFC Fight Night · Main event", "Pereira beat Ankalaev · KO/TKO R2", "", ""},
                {"TENNIS", "ATP · Paris Masters · Quarterfinal", "Sinner beat Fritz · 6-4 7-6", "", ""}};
        for (int i = 0; i < script.length; i++) {
            String[] e = script[i];
            main.postDelayed(() -> {
                if (host == null) return;
                events.add(0, e);
                fillEvents(app);
                flash(e);
            }, i * 10_500L);
        }
    }

    private static int minuteValue(String m) {
        try {
            String d = m.replaceAll("[^0-9+]", "");
            int plus = d.indexOf('+');
            return plus > 0 ? Integer.parseInt(d.substring(0, plus)) * 10 + Integer.parseInt(d.substring(plus + 1)) / 10 : Integer.parseInt(d) * 10;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            if (host == null) return;
            java.util.Date now = new java.util.Date();
            clock.setText(new java.text.SimpleDateFormat("HH:mm", Locale.UK).format(now));
            main.postDelayed(this, 15_000L);
        }
    };

    private static final Runnable nextLeague = new Runnable() {
        @Override public void run() {
            if (host == null) return;
            leagueAt++;
            featuredAt++;
            fillLeague(app);
            fillFeatured(app);
            main.postDelayed(this, LEAGUE_MS);
        }
    };

    private static final Runnable reread = new Runnable() {
        @Override public void run() {
            if (host == null) return;
            io.execute(() -> {
                List<ScoresTicker.Game> read = read(app);
                main.post(() -> {
                    if (host == null || read.isEmpty()) return;
                    games = read;
                    learn(read, false);
                    fill();
                });
            });
            main.postDelayed(this, READ_MS);
        }
    };

    // ---- keys ---------------------------------------------------------------------------------------------------

    /** The remote while it shows (InputKeyService, before any app): Back closes, Up and Down change the game. */
    static boolean key(KeyEvent e) {
        if (host == null) return false;
        return handle(e.getKeyCode(), e);
    }

    /**
     * Up: onto the scoreboard (a white ring round it; brought back first if it was put away). On it, Left and Right
     * change the game and Back puts the scoreboard and its stats away; Down comes off it. Off it, Left and Right turn
     * the column's leagues and Back closes the Live Centre.
     */
    private static boolean handle(int code, KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(() -> { if (onBoard) hideBoard(); else close(); });
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (down && e.getRepeatCount() == 0) main.post(() -> focusBoard(true));
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (down && e.getRepeatCount() == 0) main.post(() -> focusBoard(false));
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int by = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            if (down && e.getRepeatCount() == 0) main.post(() -> {
                if (onBoard) step(by);
                else { leagueAt += by; fillLeague(app); }
            });
            return true;
        }
        return false;
    }

    /** Whether the remote is on the scoreboard, and whether the scoreboard has been put away. */
    private static boolean onBoard, boardHidden;

    private static void focusBoard(boolean on) {
        if (host == null || mini == null) return;
        if (on && boardHidden) {
            boardHidden = false;
            for (View v : new View[]{mini, statsDrawer}) {
                v.setVisibility(View.VISIBLE);
                v.setAlpha(0f);
                v.setTranslationY(-px(20));
                v.animate().alpha(1f).translationY(0).setDuration(280).start();
            }
        }
        onBoard = on;
        for (View v : new View[]{mini, statsBox}) {
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(px(14));
            ring.setStroke(px(4), 0xFFFFFFFF);
            v.setForeground(on ? ring : null);
        }
        // Both grow from the scoreboard's top left corner, so the stats stay tucked behind it.
        mini.setPivotX(0);
        mini.setPivotY(0);
        statsDrawer.setPivotX(-(px(24 + BOARD_W - TUCK) - px(24)));
        statsDrawer.setPivotY(0);
        for (View v : new View[]{mini, statsDrawer}) v.animate().scaleX(on ? 1.03f : 1f).scaleY(on ? 1.03f : 1f).setDuration(160).start();
    }

    /** Back on the scoreboard: it and its stats slide away (Up brings them back). */
    private static void hideBoard() {
        focusBoard(false);
        boardHidden = true;
        for (View v : new View[]{mini, statsDrawer}) {
            v.animate().alpha(0f).translationY(-px(20)).setDuration(240).withEndAction(() -> { if (boardHidden) v.setVisibility(View.GONE); }).start();
        }
    }

    /** The next or the previous live game shown, its channel put on when a guide lists it. */
    private static void step(int by) {
        List<ScoresTicker.Game> live = new ArrayList<>();
        for (ScoresTicker.Game g : games) if (g.state == 0) live.add(g);
        if (live.isEmpty()) return;
        int at = 0;
        for (int i = 0; i < live.size(); i++) if (live.get(i).id.equals(shownId)) at = i;
        ScoresTicker.Game next = live.get(Math.floorMod(at + by, live.size()));
        shownId = next.id;
        fillMini(app);
        io.execute(() -> {
            stats(next);
            main.post(() -> { if (host != null) { fillStats(app, false); restartStats(); } });
            Object[] w = GoalWatchService.watchForGame(app, next);
            main.post(() -> {
                if (host == null) return;
                boolean on = w != null && tune(w);
                if (!on) {
                    centre.setBackgroundColor(0xFF0B0E33);
                } else {
                    centre.setBackground(null);
                }
                fillCentre(app);
            });
        });
    }

    // ---- goal clips -----------------------------------------------------------------------------------------------

    /** The games a goal's clip is looked for in: the Premier League's, the Champions League's, and the big clubs'. */
    private static boolean clipWanted(ScoresTicker.Game g) {
        if ("eng.1".equals(g.slug) || "uefa.champions".equals(g.slug)) return true;
        String both = F1Results.key((g.homeName == null ? "" : g.homeName) + " | " + (g.awayName == null ? "" : g.awayName));
        for (String club : new String[]{"real madrid", "barcelona", "ac milan", "internazionale", "inter milan", "juventus",
                "bayern", "paris saint-germain", "paris saint germain"}) {
            if (both.contains(club)) return true;
        }
        return false;
    }

    private static FrameLayout clipBox;
    private static android.media.MediaPlayer clipPlayer;
    private static final List<String[]> clipQueue = new ArrayList<>();

    /**
     * A goal's clip, played once in a frame over the bottom right of the picture, as a replay would be: GOAL on the
     * green slant, the scorer and the minute above it; muted, the channel's own sound carrying on. Clips that come while
     * one plays wait their turn; none longer than 90 seconds.
     */
    static void playClip(String url, String scorer, String minute, String line) {
        if (host == null || url == null) return;
        if (clipBox != null) { clipQueue.add(new String[]{url, scorer, minute, line}); return; }
        Context c = app;
        int w = px(576), h = px(324);
        FrameLayout box = new FrameLayout(c);
        box.setClipToOutline(true);
        box.setOutlineProvider(round(px(14)));
        box.setBackgroundColor(0xFF000000);
        box.setElevation(px(14));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(NAVY);
        head.setPadding(0, 0, px(14), 0);
        TextView label = text(c, "GOAL", 17, 0xFF062814, ScoreCard.outfit(c, 900));
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(px(14), 0, px(20), 0);
        label.setBackground(new ScoreCard.Slant(GREEN, px(10), false));
        head.addView(label, new LinearLayout.LayoutParams(-2, -1));
        TextView who = text(c, (scorer == null ? "" : scorer) + (minute == null || minute.isEmpty() ? "" : "  " + minute), 18,
                0xFFFFFFFF, ScoreCard.outfit(c, 700));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.leftMargin = px(10);
        head.addView(who, wl);
        TextView match = text(c, line == null ? "" : line, 14, 0xFFC9CEF2, ScoreCard.outfit(c, 600));
        match.setGravity(Gravity.END);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, -2, 1f);
        ml.leftMargin = px(10);
        head.addView(match, ml);
        col.addView(head, new LinearLayout.LayoutParams(-1, px(38)));
        android.view.TextureView video = new android.view.TextureView(c);
        col.addView(video, new LinearLayout.LayoutParams(w, h));
        box.addView(col, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(w, h + px(38), Gravity.TOP | Gravity.START);
        bl.leftMargin = px(1500 - 576 - 26);
        bl.topMargin = px(900 - 324 - 38 - 22);
        host.addView(box, bl);
        clipBox = box;
        box.setAlpha(0f);
        box.setTranslationY(px(30));
        video.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int sw, int sh) {
                android.media.MediaPlayer mp = new android.media.MediaPlayer();
                clipPlayer = mp;
                try {
                    mp.setDataSource(url);
                    mp.setSurface(new android.view.Surface(st));
                    mp.setVolume(0f, 0f);
                    mp.setOnPreparedListener(m -> {
                        if (clipPlayer != m) return;
                        m.start();
                        box.animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).start();
                    });
                    mp.setOnCompletionListener(m -> endClip());
                    mp.setOnErrorListener((m, what, extra) -> { endClip(); return true; });
                    mp.prepareAsync();
                } catch (Exception e) {
                    endClip();
                }
            }
            @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int sw, int sh) { }
            @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) { return true; }
            @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) { }
        });
        main.removeCallbacks(clipTimeout);
        main.postDelayed(clipTimeout, 90_000L);
    }

    private static final Runnable clipTimeout = LiveCentre::endClip;

    /** The clip done (or failed): its frame away, and the next one waiting played. */
    private static void endClip() {
        main.removeCallbacks(clipTimeout);
        android.media.MediaPlayer mp = clipPlayer;
        clipPlayer = null;
        if (mp != null) { try { mp.release(); } catch (RuntimeException ignored) { } }
        FrameLayout box = clipBox;
        if (box == null) return;
        box.animate().alpha(0f).translationY(px(30)).setDuration(260).withEndAction(() -> {
            if (box.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) box.getParent()).removeView(box);
            if (clipBox == box) clipBox = null;
            if (!clipQueue.isEmpty() && host != null) {
                String[] n = clipQueue.remove(0);
                playClip(n[0], n[1], n[2], n[3]);
            }
        }).start();
    }

    static void close() {
        onBoard = false;
        boardHidden = false;
        main.removeCallbacks(clockTick);
        main.removeCallbacks(statsTurn);
        if (host != null && !InputKeyService.running) AppSearchService.keysFor("live", false);
        GoalClips.stop();
        flashes.clear();
        flashing = null;
        clipQueue.clear();
        android.media.MediaPlayer mp = clipPlayer;
        clipPlayer = null;
        if (mp != null) { try { mp.release(); } catch (RuntimeException ignored) { } }
        clipBox = null;
        main.removeCallbacks(clipTimeout);
        main.removeCallbacks(tick);
        main.removeCallbacks(reread);
        main.removeCallbacks(nextLeague);
        if (slide != null) { slide.cancel(); slide = null; }
        pendingTicker = null;
        FrameLayout v = host;
        host = null;
        if (v == null) return;
        v.setOnKeyListener(null);
        v.animate().alpha(0f).setDuration(220).withLayer().withEndAction(() -> {
            try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (RuntimeException ignored) { }
        }).start();
    }

    // ---- small helpers --------------------------------------------------------------------------------------------

    private static String minute(ScoresTicker.Game g) {
        if (g.state == 2) return "FT";
        if (g.state == 1) return g.kickoff > 0 ? new java.text.SimpleDateFormat("HH:mm", Locale.UK).format(new java.util.Date(g.kickoff)) : "";
        if (g.halftime) return "HT";
        if (g.detail != null && !g.detail.isEmpty()) return g.detail;
        return "LIVE";
    }

    private static String score(String s) {
        return s == null || s.isEmpty() ? "0" : s;
    }

    private static String shortName(String full, String abbr) {
        if (full == null || full.isEmpty()) return abbr == null ? "" : abbr;
        String n = full.replaceFirst(" (Hotspur|Wanderers|Albion|Athletic)$", "").replaceFirst("^AFC ", "");
        if (n.equals("Manchester United")) return "Man Utd";
        if (n.equals("Manchester City")) return "Man City";
        if (n.equals("Wolverhampton")) return "Wolves";
        if (n.equals("Nottingham Forest")) return "Nott'm Forest";
        return n.length() > 16 && abbr != null ? abbr : n;
    }

    private static String titleCase(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.toLowerCase(Locale.UK).split(" ")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(w.equals("laliga") ? "LaLiga" : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return b.toString();
    }

    /** Every crest and league logo, ready to draw: read with the games and the tables, so none is waited for. */
    private static final android.util.LruCache<String, Bitmap> crests = new android.util.LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    private static void loadCrest(ImageView v, String url) {
        if (url == null) return;
        Bitmap have = crests.get(url);
        if (have != null) { v.setImageBitmap(have); return; }
        io.execute(() -> {
            Bitmap b = crest(url);
            if (b != null) main.post(() -> v.setImageBitmap(b));
        });
    }

    /** A crest from the logo cache on disk (or ESPN), keylined for the navy, kept in memory. Off the main thread. */
    private static Bitmap crest(String url) {
        if (url == null) return null;
        Bitmap have = crests.get(url);
        if (have != null) return have;
        Bitmap b = ScoresTicker.cachedLogo(app, url, 112);
        if (b == null) return null;
        Bitmap n = ScoreCard.onNavy(b);
        crests.put(url, n);
        return n;
    }

    private static TextView text(Context c, String s, float sizePx, int colour, Typeface face) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * k);
        v.setTextColor(colour);
        v.setTypeface(face);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    private static ViewOutlineProvider round(int r) {
        return new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        };
    }
}
