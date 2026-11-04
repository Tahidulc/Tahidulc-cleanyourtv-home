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
    private static final long READ_MS = 30_000L, LEAGUE_MS = 12_000L;

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
    /**
     * The ticker in pages, not sliding without end: a line that moved every frame made the TV recompose the whole
     * frame over the playing match sixty times a second, and the video stuttered. Each page of scores holds still for
     * TICKER_PAGE_MS, then the next glides in (one short move); the second view is the page coming in.
     */
    private static TextView tickerNext;
    private static final long TICKER_PAGE_MS = 7_000L, TICKER_MOVE_MS = 420L;
    private static List<String> tickerPages = new ArrayList<>();
    private static int tickerAt;
    private static boolean tickerMoving;

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
        openHere(ctx, null);
    }

    /** The frame over whatever is on, on this game (Match night: TiviMate in its window in the frame's picture). */
    static void openHere(Context ctx, String gameId) {
        app = ctx.getApplicationContext();
        close();
        io.execute(() -> {
            List<ScoresTicker.Game> read = scores();
            ScoresTicker.Game pick = choose(read, gameId);
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

    /** A club's line in a table: ESPN's id, name, crest, played, won, drawn, lost, goal difference, points, its place. */
    private static final class Club {
        String id, name, abbr, logo;
        int played, won, drawn, lost, gd, pts, rank;
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
                c.won = intOf(st.get("wins"));
                c.drawn = intOf(st.get("ties"));
                c.lost = intOf(st.get("losses"));
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
            x.played = c.played; x.won = c.won; x.drawn = c.drawn; x.lost = c.lost; x.gd = c.gd; x.pts = c.pts; x.rank = c.rank;
            now.put(c.id, x);
        }
        Map<String, String> playing = new HashMap<>();
        for (ScoresTicker.Game g : games) {
            if ((g.state != 0 && !(testCard && g.state == 1)) || !slug.equals(g.slug)) continue;
            Club h = now.get(g.homeId), a = now.get(g.awayId);
            int hs = intOf(g.homeScore), as = intOf(g.awayScore);
            if (h != null) { h.played++; h.gd += hs - as; h.pts += hs > as ? 3 : hs == as ? 1 : 0; result(h, hs, as); playing.put(h.id, hs + "–" + as); }
            if (a != null) { a.played++; a.gd += as - hs; a.pts += as > hs ? 3 : hs == as ? 1 : 0; result(a, as, hs); playing.put(a.id, as + "–" + hs); }
        }
        List<Club> sorted = new ArrayList<>(now.values());
        sorted.sort((x, y) -> x.pts != y.pts ? Integer.compare(y.pts, x.pts) : x.gd != y.gd ? Integer.compare(y.gd, x.gd)
                : Integer.compare(x.rank, y.rank));
        List<Object[]> out = new ArrayList<>();
        for (Club c : sorted) out.add(new Object[]{c, c.rank, playing.get(c.id)});
        return out;
    }

    /** A game under way counted as it stands: won, drawn or lost at its score. */
    private static void result(Club c, int scored, int against) {
        if (scored > against) c.won++;
        else if (scored == against) c.drawn++;
        else c.lost++;
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
        // The time and date in boxes of their own size: the minute turning over redraws them alone, with no new
        // layout of the frame over the match.
        clock = text(c, "", 50, 0xFFFFFFFF, ScoreCard.outfit(c, 600));
        clock.setSingleLine(true);
        clock.setFontFeatureSettings("tnum");
        column.addView(clock, new LinearLayout.LayoutParams(-1, px(64)));
        date = text(c, "", 18, 0xFFC9CEF2, ScoreCard.outfit(c, 500));
        date.setSingleLine(true);
        column.addView(date, new LinearLayout.LayoutParams(-1, px(26)));
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
        // Two lines the band's own width, one showing and one coming in: fixed sizes, so a new page is only a redraw.
        tickerText = tickerView(c);
        tickerNext = tickerView(c);
        tickerNext.setVisibility(View.INVISIBLE);
        band.addView(tickerText, new FrameLayout.LayoutParams(-1, -1));
        band.addView(tickerNext, new FrameLayout.LayoutParams(-1, -1));
        tickerLine = band;
        eventsBox = new LinearLayout(c);
        eventsBox.setOrientation(LinearLayout.VERTICAL);
        eventsBox.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0E1450, 0xFF0A0D3D}));
        eventsBox.setPadding(px(24), px(8), px(24), px(8));
        foot.addView(eventsBox, new LinearLayout.LayoutParams(-1, 0, 1f));

        // The game's scoreboard in the top left corner, wide and tall enough to cover the broadcaster's own, and its
        // stats beside it.
        // The scoreboard and its stats are one bar: the stats and facts fold out of the scoreboard's right end, the
        // bar's rounded end going out with them, and back in.
        bar = new Bar(c);
        bar.setElevation(px(12));
        // Shown smaller, from its top left corner (where it sits): everything in it, the stats too, in proportion.
        bar.setPivotX(0);
        bar.setPivotY(0);
        bar.setScaleX(BOARD_SCALE);
        bar.setScaleY(BOARD_SCALE);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(px(BOARD_W + STATS_W), px(BOARD_H), Gravity.TOP | Gravity.START);
        ml.leftMargin = px(24);
        ml.topMargin = px(22);
        win.addView(bar, ml);
        statsBox = new FrameLayout(c);
        statsBox.setBackground(statsGround());
        FrameLayout.LayoutParams stl = new FrameLayout.LayoutParams(px(STATS_W), -1, Gravity.TOP | Gravity.START);
        stl.leftMargin = px(BOARD_W);
        bar.addView(statsBox, stl);
        mini = new LinearLayout(c);
        mini.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(mini, new FrameLayout.LayoutParams(px(BOARD_W), -1, Gravity.TOP | Gravity.START));
        statsOpen = false;
        openNow = 0;
        setOpen(0);
        if (!SHOW_BOARD) bar.setVisibility(View.GONE);

        // Back closes it: the remote's Back key in the column's top right corner.
        ImageView back = new ImageView(c);
        back.setImageBitmap(GoalWatchService.backButton(Config.themeString(c, "ok_button", "tcl"), px(30)));
        back.setTag(PopupMotion.BACK_KEY);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(px(30), px(30), Gravity.TOP | Gravity.END);
        bl.rightMargin = px(24);
        bl.topMargin = px(26);
        win.addView(back, bl);

        if (renderTo != null) {
            java.io.File dir = renderTo;
            renderTo = null;
            photograph(c, win, dir);
            return;
        }
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

    private static final int BOARD_W = 748, BOARD_H = 112, STATS_W = 540, COMP_W = 108;
    /**
     * The scoreboard (and the stats folding out of it) drawn at this size: built at the sizes above, then shown at 0.6,
     * about 450 x 67 on a 1920 x 1080 screen, a little bigger than the Premier League's own international score box
     * (about 380 x 44). It was the full size, which took too much of the picture.
     */
    private static final float BOARD_SCALE = 0.6f;
    /**
     * Whether the game's scoreboard shows in the top left corner (with its stats folding out of it, Up taking the
     * remote to it). Off: the channel's own scoreboard shows there instead, nothing of ours over it.
     */
    private static final boolean SHOW_BOARD = false;
    /** The stats' labels on the white panel: a soft navy. */
    private static final int STAT_LABEL = 0xFF6A6F9C;
    /** Each page of stats on show, how many pages per fold-out, the scoreboard alone in between, the first fold-out. */
    private static final long STATS_MS = 6_000L, SHUT_MS = 15_000L, FIRST_OPEN_MS = 4_000L;
    private static final int PAGES_OPEN = 2;
    private static FrameLayout statsBox;
    private static Bar bar;
    /** How far the stats have folded out (0 in, 1 out), and the fold under way. */
    private static float openNow;
    private static android.animation.ValueAnimator folding;
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
        if (g == null) { bar.setVisibility(View.GONE); return; }
        if (SHOW_BOARD && !boardHidden) bar.setVisibility(View.VISIBLE);
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
        mid.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2457E0, 0xFF12309C}));
        boardScore = text(c, g.state == 1 ? "v" : score(g.homeScore) + "-" + score(g.awayScore), 58, 0xFFFFFFFF, ScoreCard.outfit(c, 900));
        boardScore.setGravity(Gravity.CENTER);
        mid.addView(boardScore, new LinearLayout.LayoutParams(-2, -2));
        boardClock = text(c, "", 27, 0xFFFFFFFF, ScoreCard.outfit(c, 700));
        boardClock.setGravity(Gravity.CENTER);
        boardClock.setSingleLine(true);
        // Same-width digits in a box of its own size: the second ticking over is a redraw of the clock alone, never a
        // new layout of the whole frame over the match (which made the video hitch once a second).
        boardClock.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(200), px(36));
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
            // Every player named, his picture ready before the drawing.
            List<String[]> named = new ArrayList<>();
            for (ScoresTicker.Game g : one) {
                List<String[]> ps = statPages.get(g.id);
                if (ps != null) named.addAll(ps);
                if (lastGoal.containsKey(g.id)) named.add(lastGoal.get(g.id));
                if (lastSub.containsKey(g.id)) named.add(lastSub.get(g.id));
            }
            for (ScoresTicker.Game g : one) if (lastLineups.containsKey(g.id)) named.addAll(lastLineups.get(g.id));
            for (String[] p : named) {
                if ("lineup".equals(p[3])) for (String who : p[2].split("\\|")) face(who, p[5]);
                if ("player".equals(p[3]) || "sub".equals(p[3])) face(p[1], p.length > 5 ? p[5] : null);
                if ("sub".equals(p[3])) face(p[2], p[5]);
                if ("goals".equals(p[3]) && p.length > 5) for (int s2 = 4; s2 <= 5; s2++) for (String who : p[s2].split("\\|")) face(who, null);
            }
            main.post(() -> {
                float was = k;
                k = app.getResources().getDisplayMetrics().widthPixels / 1920f;
                TextView score = boardScore, clockView = boardClock;
                int w = px(BOARD_W), h = px(BOARD_H), gap = px(16), sw = px(STATS_W);
                // The first game three times (alone, then folded out on two pages of its stats), then one game of
                // each other competition alone.
                List<ScoresTicker.Game> rows = new ArrayList<>();
                List<String[]> pageOf = new ArrayList<>();
                for (int i = 0; i < one.size(); i++) {
                    ScoresTicker.Game g = one.get(i);
                    List<String[]> ps = statPages.get(g.id);
                    if (every) {
                        // Its latest goal and substitution as they fold out when they happen, then every page.
                        if (lastGoal.containsKey(g.id)) { rows.add(g); pageOf.add(lastGoal.get(g.id)); }
                        if (lastSub.containsKey(g.id)) { rows.add(g); pageOf.add(lastSub.get(g.id)); }
                        if (g.state != 1 && lastLineups.containsKey(g.id)) for (String[] x : lastLineups.get(g.id)) { rows.add(g); pageOf.add(x); }
                        for (int p = 0; ps != null && p < ps.size(); p++) { rows.add(g); pageOf.add(ps.get(p)); }
                        continue;
                    }
                    rows.add(g);
                    pageOf.add(null);
                    if (i == 0 && ps != null) for (int p = 0; p < Math.min(2, ps.size()); p++) { rows.add(g); pageOf.add(ps.get(Math.floorMod(statAt + p, ps.size()))); }
                }
                Bitmap out = Bitmap.createBitmap(w + sw + 2 * gap, Math.max(1, rows.size()) * (h + gap) + gap, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(out);
                cv.drawColor(0xFF2A2E3A);
                for (int i = 0; i < rows.size(); i++) {
                    ScoresTicker.Game g = rows.get(i);
                    boolean out2 = pageOf.get(i) != null;
                    float reach = out2 ? w + sw : w, r = px(14);
                    cv.save();
                    cv.translate(gap, gap + i * (h + gap));
                    // One bar: one outline round the scoreboard and, folded out, its stats.
                    android.graphics.Path clip = new android.graphics.Path();
                    clip.addRoundRect(new RectF(0, 0, reach, h), r, r, android.graphics.Path.Direction.CW);
                    cv.clipPath(clip);
                    if (out2) {
                        cv.save();
                        cv.translate(w, 0);
                        android.graphics.drawable.Drawable ground2 = statsGround();
                        ground2.setBounds(0, 0, sw, h);
                        ground2.draw(cv);
                        View page = pageView(app, g, pageOf.get(i));
                        page.measure(View.MeasureSpec.makeMeasureSpec(sw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                        page.layout(0, 0, sw, h);
                        centreTexts(page);
                        page.draw(cv);
                        cv.restore();
                    }
                    LinearLayout b = new LinearLayout(app);
                    b.setOrientation(LinearLayout.HORIZONTAL);
                    buildBoard(app, b, g);
                    boardClock.setText(clockText(g));
                    b.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
                    b.layout(0, 0, w, h);
                    centreTexts(b);
                    b.draw(cv);
                    if (out2) {
                        android.graphics.Paint hair = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                        hair.setColor(0x260A0D33);
                        hair.setStrokeWidth(Math.max(1, px(1.5f)));
                        cv.drawLine(w, h * 0.24f, w, h * 0.76f, hair);
                    }
                    cv.restore();
                }
                boardScore = score;
                boardClock = clockView;
                k = was;
                java.io.File f = new java.io.File(app.getExternalFilesDir(null), "boards.png");
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { out.compress(Bitmap.CompressFormat.PNG, 100, o); } catch (Exception ignored) { }
            });
        });
    }

    // ---- adb only: the scoreboard's animations drawn to frames, for a video ---------------------------------------

    /**
     * adb only: the scoreboard and its notifications as they move, drawn frame by frame off screen (nothing shown) with
     * the same curves and timings as on screen, the holds between shortened: the stats folding out and turning a page
     * and folding in, a goal, a substitution, the line-ups, then the remote's Up (the ring), Back (put away) and Up
     * (back). Thirty frames a second at 1280 x 720 over a pitch, to files/video/f0000.jpg on; files/video/done.txt last.
     */
    static void renderVideo(Context ctx) {
        if (app == null) app = ctx.getApplicationContext();
        io.execute(() -> {
            List<ScoresTicker.Game> read = scores();
            ScoresTicker.Game pick = null;
            for (ScoresTicker.Game x : read) if (pick == null && x.state == 0 && x.slug != null) pick = x;
            for (ScoresTicker.Game x : read) if (pick == null && x.state == 2 && x.slug != null) pick = x;
            if (pick == null) return;
            ScoresTicker.Game g = pick;
            stats(g);
            crest(g.homeLogo);
            crest(g.awayLogo);
            compLogo(compUrl(g));
            List<String[]> named = new ArrayList<>();
            if (statPages.get(g.id) != null) named.addAll(statPages.get(g.id));
            if (lastGoal.get(g.id) != null) named.add(lastGoal.get(g.id));
            if (lastSub.get(g.id) != null) named.add(lastSub.get(g.id));
            if (lastLineups.get(g.id) != null) named.addAll(lastLineups.get(g.id));
            for (String[] p : named) {
                if ("lineup".equals(p[3])) for (String who : p[2].split("\\|")) face(who, p[5]);
                if ("player".equals(p[3]) || "sub".equals(p[3])) face(p[1], p.length > 5 ? p[5] : null);
                if ("sub".equals(p[3])) face(p[2], p[5]);
                if ("goals".equals(p[3]) && p.length > 5) for (int s2 = 4; s2 <= 5; s2++) for (String who : p[s2].split("\\|")) face(who, null);
            }
            main.post(() -> new Film(g).start());
        });
    }

    /** adb only: where the next frame built is photographed instead of shown (live_*.png), or null. */
    private static java.io.File renderTo;
    /** adb only: the highlights to photograph offered and played in that frame, with their game's line; or null. */
    private static List<OfficialVideos.Video> renderHighlights;
    private static String renderHighlightsLine;

    /**
     * adb only: the Live Centre over a pitch with a finished game's official highlights (by a club's name) offered and
     * then played large, photographed on a display of its own (files/shots/high_0.png, high_1.png); nothing shown.
     */
    static void renderHighlights(Context ctx, String club) {
        if (host != null) return;
        app = ctx.getApplicationContext();
        String want = club == null ? "" : OfficialVideos.plain(club);
        io.execute(() -> {
            List<ScoresTicker.Game> read = scores();
            for (ScoresTicker.Game g : read) {
                if (g.state != 2 || !OfficialVideos.covered(g.slug)) continue;
                if (!want.isEmpty() && !OfficialVideos.plain(g.homeName + " " + g.awayName).contains(want)) continue;
                List<OfficialVideos.Video> v = OfficialVideos.highlights(g);
                if (v.isEmpty()) continue;
                crest(g.homeLogo);
                crest(g.awayLogo);
                compLogo(compUrl(g));
                stats(g);
                java.io.File dir = new java.io.File(app.getExternalFilesDir(null), "shots");
                dir.mkdirs();
                String line = g.homeName + " " + score(g.homeScore) + "–" + score(g.awayScore) + " " + g.awayName;
                main.post(() -> {
                    if (host != null) return;
                    games = read;
                    shownId = g.id;
                    renderHighlights = v;
                    renderHighlightsLine = line;
                    renderTo = dir;
                    show(false);
                });
                return;
            }
        });
    }

    /**
     * adb only: the Live Centre over a pitch (standing in for the channel), photographed as the screen would draw it
     * but on a display of its own (nothing shown, the TV may sleep): a game under way that has its line-ups, its
     * scoreboard folded out on each side's line-up pages, to files/shots/live_0.png on.
     */
    static void renderScreen(Context ctx) {
        if (host != null) return;
        app = ctx.getApplicationContext();
        io.execute(() -> {
            List<ScoresTicker.Game> read = scores();
            extras(read);
            ScoresTicker.Game pick = null;
            for (ScoresTicker.Game g : read) {
                if (pick != null || g.state != 0 || g.slug == null) continue;
                stats(g);
                if (lastLineups.get(g.id) != null) pick = g;
            }
            if (pick == null) return;
            ScoresTicker.Game g = pick;
            for (String[] p : lastLineups.get(g.id)) for (String who : p[2].split("\\|")) face(who, p[5]);
            crest(g.homeLogo);
            crest(g.awayLogo);
            compLogo(compUrl(g));
            java.io.File dir = new java.io.File(app.getExternalFilesDir(null), "shots");
            dir.mkdirs();
            main.post(() -> {
                if (host != null) return;
                games = read;
                shownId = g.id;
                renderTo = dir;
                show(false);
            });
        });
    }

    /** The frame built, filled and photographed on its own display, the scoreboard folded out on each line-up page. */
    private static void photograph(Context c, FrameLayout win, java.io.File dir) {
        // Filled as if shown (the frame fills itself only while it is up), and let go of once photographed.
        host = win;
        fill();
        ScoresTicker.Game g = shown();
        List<String[]> xi = g == null ? null : lastLineups.get(g.id);
        int w = c.getResources().getDisplayMetrics().widthPixels, h = c.getResources().getDisplayMetrics().heightPixels;
        FrameLayout root = new FrameLayout(c);
        ImageView ground = new ImageView(c);
        ground.setScaleType(ImageView.ScaleType.FIT_XY);
        ground.setImageBitmap(Snap.pitch(w, h));
        root.addView(ground, new FrameLayout.LayoutParams(-1, -1));
        root.addView(win, new FrameLayout.LayoutParams(-1, -1));
        List<Runnable> steps = new ArrayList<>();
        List<java.io.File> out = new ArrayList<>();
        if (renderHighlights != null) {
            List<OfficialVideos.Video> v = renderHighlights;
            String line = renderHighlightsLine;
            renderHighlights = null;
            steps.add(() -> offerHighlights(line, v));
            out.add(new java.io.File(dir, "high_0.png"));
            steps.add(LiveCentre::watchOffer);
            out.add(new java.io.File(dir, "high_1.png"));
            Snap.series(c, root, w, h, steps, out, 7_000L, () -> {
                closeBig();
                if (host == win) host = null;
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(dir, "high_done.txt"))) { o.write('1'); } catch (Exception ignored) { }
            });
            return;
        }
        for (int i = 0; xi != null && i < xi.size(); i++) {
            String[] p = xi.get(i);
            steps.add(() -> { putPage(pageView(app, g, p), false); setOpen(1); clockNow(); });
            out.add(new java.io.File(dir, "live_" + i + ".png"));
        }
        if (steps.isEmpty()) { host = null; return; }
        Snap.series(c, root, w, h, steps, out, 1_500L, () -> {
            if (host == win) host = null;
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(dir, "live_done.txt"))) { o.write('1'); } catch (Exception ignored) { }
        });
    }

    /** The film's script: a step's kind, how long it takes, the page it brings, and the caption from then on. */
    private static final class Shot {
        static final int HOLD = 0, OPEN = 1, TURN = 2, SHUT = 3, RING = 4, HIDE = 5, SHOW = 6;
        final int kind;
        final long ms;
        final String[] page;
        final String caption;
        Shot(int kind, long ms, String[] page, String caption) { this.kind = kind; this.ms = ms; this.page = page; this.caption = caption; }
    }

    /** Where everything stands at a moment of the film. */
    private static final class Pose {
        double shut = 1, turn = 1, scale = 1, alpha = 1, rise = 0;
        String[] page, before;
        boolean ring;
        String caption = "";
        Pose copy() {
            Pose o = new Pose();
            o.shut = shut; o.turn = turn; o.scale = scale; o.alpha = alpha; o.rise = rise;
            o.page = page; o.before = before; o.ring = ring; o.caption = caption;
            return o;
        }
    }

    private static final class Film {
        static final int FPS = 30, W = 1280, H = 720;
        final ScoresTicker.Game g;
        final List<Shot> script = new ArrayList<>();
        final List<Pose> starts = new ArrayList<>();
        final Map<String[], View> views = new java.util.IdentityHashMap<>();
        final android.view.animation.Interpolator out = new DecelerateInterpolator(2.4f), in = new android.view.animation.AccelerateInterpolator(1.8f),
                ease = new android.view.animation.AccelerateDecelerateInterpolator();
        LinearLayout board;
        TextView clockView;
        Bitmap ground, frame;
        java.io.File dir;
        long total;
        int frameAt, clockShown = -1, clockBase = -1;
        float was;

        Film(ScoresTicker.Game g) { this.g = g; }

        void start() {
            was = k;
            k = W / 1920f;
            List<String[]> pages = statPages.get(g.id);
            List<String[]> bars = new ArrayList<>();
            for (int i = 0; pages != null && i < pages.size(); i++) if ("bar".equals(pages.get(i)[3]) && bars.size() < 2) bars.add(pages.get(i));
            String[] goal = lastGoal.get(g.id), sub = lastSub.get(g.id);
            List<String[]> xi = lastLineups.get(g.id);
            add(Shot.HOLD, 1500, null, "The scoreboard");
            if (bars.size() == 2) {
                add(Shot.OPEN, 620, bars.get(0), "Stats fold out of the scoreboard");
                add(Shot.HOLD, 2400, null, null);
                add(Shot.TURN, 320, bars.get(1), "Next page of stats");
                add(Shot.HOLD, 2400, null, null);
                add(Shot.SHUT, 460, null, "Folding back in");
                add(Shot.HOLD, 1200, null, null);
            }
            if (goal != null) {
                add(Shot.OPEN, 620, goal, "A goal");
                add(Shot.HOLD, 3200, null, null);
                add(Shot.SHUT, 460, null, null);
                add(Shot.HOLD, 1000, null, null);
            }
            if (sub != null) {
                add(Shot.OPEN, 620, sub, "A substitution");
                add(Shot.HOLD, 3200, null, null);
                add(Shot.SHUT, 460, null, null);
                add(Shot.HOLD, 1000, null, null);
            }
            if (xi != null && xi.size() == 4) {
                add(Shot.OPEN, 620, xi.get(0), "The line-ups are out");
                for (int i = 1; i < 4; i++) {
                    add(Shot.HOLD, 2200, null, null);
                    add(Shot.TURN, 320, xi.get(i), null);
                }
                add(Shot.HOLD, 2200, null, null);
                add(Shot.SHUT, 460, null, null);
                add(Shot.HOLD, 1000, null, null);
            }
            add(Shot.RING, 160, null, "Up: the scoreboard chosen");
            add(Shot.HOLD, 1000, null, null);
            add(Shot.HIDE, 240, null, "Back: put away");
            add(Shot.HOLD, 1100, null, null);
            add(Shot.SHOW, 280, null, "Up: back again");
            add(Shot.HOLD, 1600, null, null);
            // Each step's starting pose, from the one before.
            Pose p = new Pose();
            for (Shot s : script) { starts.add(p.copy()); p = pose(s, p, 1); total += s.ms; }
            // The scoreboard, the pages and the pitch, made once.
            board = new LinearLayout(app);
            board.setOrientation(LinearLayout.HORIZONTAL);
            TextView score = boardScore, clock = boardClock;
            buildBoard(app, board, g);
            clockView = boardClock;
            boardScore = score;
            boardClock = clock;
            String t = clockText(g);
            if (t.matches("\\d+:\\d\\d")) clockBase = Integer.parseInt(t.split(":")[0]) * 60 + Integer.parseInt(t.split(":")[1]);
            clockView.setText(t);
            lay(board, px(BOARD_W), px(BOARD_H));
            for (Shot s : script) if (s.page != null && !views.containsKey(s.page)) {
                View v = pageView(app, g, s.page);
                lay(v, px(STATS_W), px(BOARD_H));
                views.put(s.page, v);
            }
            ground = pitch();
            frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            dir = new java.io.File(app.getExternalFilesDir(null), "video");
            java.io.File[] old = dir.listFiles();
            if (old != null) for (java.io.File f : old) f.delete();
            dir.mkdirs();
            main.post(this::next);
        }

        void add(int kind, long ms, String[] page, String caption) { script.add(new Shot(kind, ms, page, caption)); }

        void lay(View v, int w, int h) {
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            v.layout(0, 0, w, h);
            centreTexts(v);
        }

        /** The pose a step leaves at a share of its way through (q from 0 to 1). */
        Pose pose(Shot s, Pose from, double q) {
            Pose o = from.copy();
            if (s.caption != null) o.caption = s.caption;
            switch (s.kind) {
                case Shot.OPEN: o.page = s.page; o.before = null; o.turn = 1; o.shut = 1 - out.getInterpolation((float) q); break;
                case Shot.TURN: o.before = from.page; o.page = s.page; o.turn = q; break;
                case Shot.SHUT: o.shut = in.getInterpolation((float) q); break;
                case Shot.RING: o.ring = true; o.scale = 1 + 0.03 * ease.getInterpolation((float) q); break;
                case Shot.HIDE:
                    o.ring = false;
                    o.scale = 1.03 - 0.03 * ease.getInterpolation((float) Math.min(1, q * 240 / 160));
                    o.alpha = 1 - ease.getInterpolation((float) q);
                    o.rise = -20 * ease.getInterpolation((float) q);
                    break;
                case Shot.SHOW: o.scale = 1; o.alpha = ease.getInterpolation((float) q); o.rise = -20 * (1 - ease.getInterpolation((float) q)); break;
                default: break;
            }
            return o;
        }

        void next() {
            long frames = total * FPS / 1000;
            for (int n = 0; n < 4 && frameAt <= frames; n++, frameAt++) draw(frameAt * 1000L / FPS);
            if (frameAt <= frames) { main.post(this::next); return; }
            k = was;
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(dir, "done.txt"))) {
                o.write((frameAt + "\n").getBytes());
            } catch (Exception ignored) { }
        }

        void draw(long t) {
            long at = 0;
            int i = 0;
            while (i < script.size() - 1 && at + script.get(i).ms <= t) at += script.get(i++).ms;
            Shot s = script.get(i);
            Pose p = pose(s, starts.get(i), s.ms == 0 ? 1 : Math.min(1, (t - at) / (double) s.ms));
            // The match clock, a second on every second of film.
            if (clockBase >= 0 && clockShown != (int) (t / 1000)) {
                clockShown = (int) (t / 1000);
                int secs = clockBase + clockShown;
                clockView.setText(String.format(Locale.ROOT, "%02d:%02d", secs / 60, secs % 60));
                lay(board, px(BOARD_W), px(BOARD_H));
            }
            Canvas c = new Canvas(frame);
            c.drawBitmap(ground, 0, 0, null);
            float x0 = px(24), y0 = px(22), bw = px(BOARD_W), bh = px(BOARD_H), sw = px(STATS_W), r = px(14);
            float reach = bw + (float) ((1 - p.shut) * sw);
            int layer = c.saveLayerAlpha(0, 0, W, H, (int) Math.round(255 * p.alpha));
            c.translate(0, px((float) p.rise));
            c.scale((float) p.scale, (float) p.scale, x0, y0);
            // One bar: its shadow, then the scoreboard and as much of the stats as has come out, in one outline.
            android.graphics.Paint shade = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            shade.setColor(0xFF000000);
            shade.setShadowLayer(px(16), 0, px(5), 0x73000000);
            c.drawRoundRect(new RectF(x0, y0, x0 + reach, y0 + bh), r, r, shade);
            c.save();
            c.translate(x0, y0);
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(new RectF(0, 0, reach, bh), r, r, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            if (p.page != null && p.shut < 1) {
                c.save();
                c.translate(bw - (float) (p.shut * sw), 0);
                android.graphics.drawable.Drawable ground2 = statsGround();
                ground2.setBounds(0, 0, (int) sw, (int) bh);
                ground2.draw(c);
                double newQ = p.turn, oldQ = Math.min(1, p.turn * 320 / 260);
                if (p.before != null && oldQ < 1) page(c, views.get(p.before), 1 - ease.getInterpolation((float) oldQ), -18 * ease.getInterpolation((float) oldQ));
                page(c, views.get(p.page), p.before == null ? 1 : ease.getInterpolation((float) newQ), p.before == null ? 0 : 18 * (1 - ease.getInterpolation((float) newQ)));
                c.restore();
            }
            board.draw(c);
            if (reach > bw + px(4)) {
                android.graphics.Paint hair = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                hair.setColor(0x260A0D33);
                hair.setStrokeWidth(Math.max(1, px(1.5f)));
                c.drawLine(bw, bh * 0.24f, bw, bh * 0.76f, hair);
            }
            c.restore();
            if (p.ring) ring(c, x0, y0, reach, bh, r);
            c.restoreToCount(layer);
            caption(c, p.caption);
            java.io.File f = new java.io.File(dir, String.format(Locale.ROOT, "f%04d.jpg", frameAt));
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { frame.compress(Bitmap.CompressFormat.JPEG, 82, o); } catch (Exception ignored) { }
        }

        void page(Canvas c, View v, float alpha, float rise) {
            if (v == null || alpha <= 0) return;
            int l = c.saveLayerAlpha(0, -px(30), px(STATS_W), px(BOARD_H) + px(30), Math.round(255 * Math.max(0, Math.min(1, alpha))));
            c.translate(0, px(rise));
            v.draw(c);
            c.restoreToCount(l);
        }

        void ring(Canvas c, float x, float y, float w, float h, float r) {
            android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            pt.setStyle(android.graphics.Paint.Style.STROKE);
            pt.setStrokeWidth(px(4));
            pt.setColor(0xFFFFFFFF);
            c.drawRoundRect(new RectF(x + px(2), y + px(2), x + w - px(2), y + h - px(2)), r, r, pt);
        }

        void caption(Canvas c, String text) {
            if (text == null || text.isEmpty()) return;
            android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            pt.setTypeface(ScoreCard.outfit(app, 700));
            pt.setTextSize(px(34));
            float tw = pt.measureText(text), pad = px(26), h = px(68), x = px(48), y = H - px(48) - h;
            android.graphics.Paint bg = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            bg.setColor(0xCC0B0F45);
            c.drawRoundRect(new RectF(x, y, x + tw + 2 * pad, y + h), h / 2, h / 2, bg);
            pt.setColor(0xFFFFFFFF);
            c.drawText(text, x + pad, y + h / 2 - (pt.ascent() + pt.descent()) / 2, pt);
        }

        /** A pitch under floodlights: mown stripes, darker towards the edges. */
        Bitmap pitch() {
            Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            android.graphics.Paint pt = new android.graphics.Paint();
            int stripe = W / 12;
            for (int i = 0; i < 12; i++) {
                pt.setColor(i % 2 == 0 ? 0xFF2F7D36 : 0xFF2A7231);
                c.drawRect(i * stripe, 0, (i + 1) * stripe + 1, H, pt);
            }
            android.graphics.Paint shade = new android.graphics.Paint();
            shade.setShader(new android.graphics.RadialGradient(W / 2f, H * 0.55f, W * 0.75f, 0x00000000, 0x99000000, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, shade);
            android.graphics.Paint line = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            line.setColor(0x99FFFFFF);
            line.setStyle(android.graphics.Paint.Style.STROKE);
            line.setStrokeWidth(px(5));
            c.drawLine(W / 2f, 0, W / 2f, H, line);
            c.drawCircle(W / 2f, H * 0.55f, H * 0.2f, line);
            return b;
        }
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
            List<String[]> xi = lineupPages(g, d.optJSONArray("rosters"));
            lineupsOut(g, xi);
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
                if (xi != null) pages.addAll(xi);
                else if (shapes != null) pages.add(shapes);
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
            if (g.state != 1) newEvents(g, d.optJSONArray("keyEvents"));
            // The pictures of the players the pages name, looked up once and kept.
            for (String[] p : pages) {
                if ("lineup".equals(p[3])) for (String who : p[2].split("\\|")) wantFace(who, p[5]);
                if ("player".equals(p[3])) wantFace(p[1], p[5]);
                if ("goals".equals(p[3]) && p.length > 5) {
                    for (String who : p[4].split("\\|")) wantFace(who, g.homeName);
                    for (String who : p[5].split("\\|")) wantFace(who, g.awayName);
                }
            }
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
            String name = who == null ? "" : who.optString("displayName");
            if (name.isEmpty()) continue;
            String at = e.optJSONObject("clock") == null ? "" : e.getJSONObject("clock").optString("displayValue").replace("'+", "+");
            if (type.contains("penalty")) at += " pen";
            else if (type.contains("own goal")) at += " og";
            String team = e.optJSONObject("team") == null ? "" : e.getJSONObject("team").optString("id");
            (team.equals(g.awayId) ? away : home).computeIfAbsent(name, x -> new ArrayList<>()).add(at);
        }
        if (home.isEmpty() && away.isEmpty()) return null;
        // {label, home's line, away's line, "goals", home's scorers in full ("|"), away's, home's each ("|"), away's each}
        return new String[]{"GOALS", TextUtils.join("  ·  ", scorers(home)), TextUtils.join("  ·  ", scorers(away)), "goals",
                TextUtils.join("|", home.keySet()), TextUtils.join("|", away.keySet()),
                TextUtils.join("|", scorers(home)), TextUtils.join("|", scorers(away))};
    }

    /** Each scorer as "Mbappé 11' pen, 54'". */
    private static List<String> scorers(Map<String, List<String>> m) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : m.entrySet()) out.add(GoalWatchService.surname(e.getKey()) + " " + TextUtils.join(", ", e.getValue()));
        return out;
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
                        home ? g.homeLogo : g.awayLogo, home ? g.homeName : g.awayName, String.valueOf(sideColours(g)[home ? 0 : 1])};
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
            if (host == null || eventOn) return;
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
        if (bar == null) return;
        if (folding != null) folding.cancel();
        folding = android.animation.ValueAnimator.ofFloat(openNow, open ? 1f : 0f);
        folding.setDuration(open ? 620 : 460);
        folding.setInterpolator(open ? new DecelerateInterpolator(2.4f) : new android.view.animation.AccelerateInterpolator(1.8f));
        folding.addUpdateListener(a -> setOpen((float) a.getAnimatedValue()));
        folding.start();
    }

    /** The bar this far out: its rounded end past the scoreboard by that share of the stats, the stats coming with it. */
    private static void setOpen(float f) {
        openNow = f;
        if (bar == null || statsBox == null) return;
        bar.reveal(px(BOARD_W) + f * px(STATS_W));
        statsBox.setTranslationX(-(1 - f) * px(STATS_W));
    }

    /**
     * The scoreboard and its stats as one bar: a single outline and shadow round both, its rounded right end as far
     * out as the stats have come, a hairline between the scoreboard and the stats while they show, and the remote's
     * white ring round the whole of it when chosen.
     */
    private static final class Bar extends FrameLayout {
        float reveal;
        boolean ring;
        private final android.graphics.Paint line = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG),
                edge = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        Bar(Context c) {
            super(c);
            setWillNotDraw(false);
            setClipToOutline(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, Math.max(1, Math.round(reveal)), v.getHeight(), px(14)); }
            });
            line.setColor(0x260A0D33);
            line.setStrokeWidth(Math.max(1, px(1.5f)));
            edge.setStyle(android.graphics.Paint.Style.STROKE);
            edge.setStrokeWidth(px(4));
            edge.setColor(0xFFFFFFFF);
        }

        void reveal(float w) {
            reveal = w;
            invalidateOutline();
            invalidate();
        }

        @Override protected void dispatchDraw(Canvas c) {
            super.dispatchDraw(c);
            float x = px(BOARD_W), h = getHeight();
            if (reveal > x + px(4)) c.drawLine(x, h * 0.24f, x, h * 0.76f, line);
            if (ring) {
                float in = px(2);
                c.drawRoundRect(new RectF(in, in, reveal - in, h - in), px(13), px(13), edge);
            }
        }
    }

    /** Another game on the scoreboard: its stats fold out soon (or, already out, show it a page or two more). */
    private static void restartStats() {
        main.removeCallbacks(statsTurn);
        main.removeCallbacks(nextEvent);
        foldOuts.clear();
        eventOn = false;
        pagesShown = 0;
        main.postDelayed(statsTurn, statsOpen ? STATS_MS : 1_200L);
    }

    /** The stats' ground: the scoreboard's own white, as its sides have it (the bar's outline rounds its end). */
    private static android.graphics.drawable.Drawable statsGround() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFFFFFFF, 0xFFE9ECF5});
    }

    /** One page of the stats: its label across the top, the home side's figure left, the away side's right. */
    private static void fillStats(Context c, boolean turn) {
        if (statsBox == null || eventOn) return;
        putPage(statsPage(c, shown()), turn);
    }

    /** A page into the stats panel: in place of the one there, or, turning, rising in as the old one rises away. */
    private static void putPage(View page, boolean turn) {
        if (statsBox == null) return;
        View old = statsBox.getChildCount() > 0 ? statsBox.getChildAt(statsBox.getChildCount() - 1) : null;
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
        return pageView(c, g, pages == null || pages.isEmpty() ? null : pages.get(Math.floorMod(statAt, pages.size())));
    }

    /** One page of stats or facts (or, without one, the game's competition). */
    private static View pageView(Context c, ScoresTicker.Game g, String[] p) {
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(px(22), px(10), px(22), px(10));
        if (p == null) {
            TextView t = text(c, g == null ? "" : ScoresTicker.competition(g), 22, STAT_LABEL, ScoreCard.outfit(c, 600));
            page.addView(t);
        } else {
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
            } else if ("goals".equals(p[3]) && goalChips(c, g, p, row, room)) {
                // Each scorer with his picture (goalChips), when they all fit.
            } else if ("lineup".equals(p[3])) {
                lineupRow(c, p, row, room);
            } else if ("sub".equals(p[3])) {
                int half = (room - px(18)) / 2;
                row.addView(subSide(c, p[1], true, p, half), new LinearLayout.LayoutParams(half, -2));
                row.addView(new View(c), new LinearLayout.LayoutParams(px(18), 1));
                row.addView(subSide(c, p[2], false, p, half), new LinearLayout.LayoutParams(half, -2));
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
                row.addView(portrait(c, p[1], p.length > 4 ? p[4] : null, p.length > 6 ? intOf(p[6]) : 0, 58), new LinearLayout.LayoutParams(px(58), px(58)));
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
                mid.addView(split3(c, g, p[1], p.length > 4 ? p[4] : "0", p[2]), bl3);
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
                    row.addView(split(c, g, p[1], p[2]), bl);
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
    private static View split(Context c, ScoresTicker.Game g, String hv, String av) {
        float h = leadingNumber(hv), a = leadingNumber(av);
        float share = h + a <= 0 ? 0.5f : h / (h + a);
        LinearLayout bar = new LinearLayout(c);
        bar.setClipToOutline(true);
        bar.setOutlineProvider(round(px(5)));
        // Each side in its own colour.
        int[] kit = sideColours(g);
        View l = new View(c), r = new View(c);
        l.setBackgroundColor(kit[0]);
        r.setBackgroundColor(kit[1]);
        bar.addView(l, new LinearLayout.LayoutParams(0, -1, Math.max(0.02f, share)));
        View gap = new View(c);
        gap.setBackgroundColor(0xFFF4F5FA);
        bar.addView(gap, new LinearLayout.LayoutParams(px(3), -1));
        bar.addView(r, new LinearLayout.LayoutParams(0, -1, Math.max(0.02f, 1f - share)));
        return bar;
    }

    /** Win, draw, win as one bar: each side's chance in its own colour, the draw's grey. */
    private static View split3(Context c, ScoresTicker.Game g, String hv, String dv, String av) {
        float h = leadingNumber(hv), d = leadingNumber(dv), a = leadingNumber(av);
        LinearLayout bar = new LinearLayout(c);
        bar.setClipToOutline(true);
        bar.setOutlineProvider(round(px(5)));
        float[] parts = {h, d, a};
        int[] kit = sideColours(g);
        int[] colours = {kit[0], 0xFFC4C8DC, kit[1]};
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

    /**
     * Each side's colour for its share of a stat: its kit's, or its second colour where the first is too pale to show
     * on the white; where the two sides' are too alike, the away side's second colour (else the home side's), and
     * failing both a grey-blue for the away side.
     */
    private static int[] sideColours(ScoresTicker.Game g) {
        if (g == null) return new int[]{NAVY, 0xFF8A90B8};
        int h = usable(g.homeColor, g.homeAlt), a = usable(g.awayColor, g.awayAlt);
        if (alike(h, a)) {
            int alt = clear(g.awayAlt);
            if (alt != 0 && !alike(h, alt)) a = alt;
            else if ((alt = clear(g.homeAlt)) != 0 && !alike(alt, a)) h = alt;
            else a = 0xFF8A90B8;
        }
        return new int[]{h, a};
    }

    private static int usable(int colour, int second) {
        int c = clear(colour);
        if (c == 0) c = clear(second);
        return c != 0 ? c : NAVY;
    }

    /** The colour, if it shows on the white panel; else 0. */
    private static int clear(int c) {
        if (c == 0) return 0;
        int r = (c >> 16) & 0xFF, gr = (c >> 8) & 0xFF, b = c & 0xFF;
        return (r * 299 + gr * 587 + b * 114) / 1000 > 200 ? 0 : 0xFF000000 | c;
    }

    /** Whether two colours are too close to tell apart at a glance (the "redmean" distance). */
    private static boolean alike(int x, int y) {
        int r1 = (x >> 16) & 0xFF, g1 = (x >> 8) & 0xFF, b1 = x & 0xFF, r2 = (y >> 16) & 0xFF, g2 = (y >> 8) & 0xFF, b2 = y & 0xFF;
        double rm = (r1 + r2) / 2.0, dr = r1 - r2, dg = g1 - g2, db = b1 - b2;
        return Math.sqrt((2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db) < 120;
    }

    // ---- players' pictures --------------------------------------------------------------------------------------

    /** Pictures are looked up here, apart from the scores and crests, as a lookup can take a while. */
    private static final ExecutorService faces = Executors.newSingleThreadExecutor();
    /** Players with no picture anywhere (looked up this time the launcher runs). */
    private static final java.util.Set<String> noFace = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * A player's head and shoulders: the Premier League's own photo, else TheSportsDB's cut-out, else last season's
     * from the league; kept with the crests. Null without one. Off the main thread.
     */
    private static Bitmap face(String who, String club) {
        if (who == null || who.isEmpty() || app == null) return null;
        String key = "face|" + who;
        Bitmap have = crests.get(key);
        if (have != null || noFace.contains(who)) return have;
        Bitmap b = null;
        boolean asked = true;
        try {
            String pl = PremierLeague.photo(app, who, club);
            if (pl != null) b = cutout(pl);
            if (b == null) {
                PlayerPhotos.Cutout cut = PlayerPhotos.cutout(app, who, club);
                asked = cut.url != null;
                b = PlayerPhotos.photo(app, cut);
            }
            if (b == null && pl != null) b = cutout(PremierLeague.older(pl));
        } catch (RuntimeException e) {
            asked = false;
        }
        if (b == null) {
            if (asked) noFace.add(who);
            return null;
        }
        b = PlayerPhotos.headAndShoulders(b);
        crests.put(key, b);
        return b;
    }

    /** Players whose pictures are waiting to be looked up, so each is asked for once at a time. */
    private static final java.util.Set<String> faceQueued = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** A player's picture looked up in the background, unless it is kept, known missing, or already waiting. */
    private static void wantFace(String who, String club) {
        if (who == null || who.isEmpty() || crests.get("face|" + who) != null || noFace.contains(who) || !faceQueued.add(who)) return;
        faces.execute(() -> {
            try { face(who, club); } finally { faceQueued.remove(who); }
        });
    }

    private static Bitmap cutout(String url) {
        if (url == null) return null;
        PlayerPhotos.Cutout c = new PlayerPhotos.Cutout();
        c.url = url;
        return PlayerPhotos.photo(app, c);
    }

    /**
     * A player's picture in a circle (a figure in his club's colour while it has none), his club's crest small at
     * its foot when given.
     */
    private static View portrait(Context c, String who, String crestUrl, int kit, int size) {
        FrameLayout f = new FrameLayout(c);
        ImageView pic = new ImageView(c);
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(0xFFE1E5F0);
        pic.setBackground(disc);
        pic.setClipToOutline(true);
        pic.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
        });
        Bitmap b = who == null ? null : crests.get("face|" + who);
        pic.setImageBitmap(b != null ? b : LineupCard.avatar(kit, kit, ""));
        f.addView(pic, new FrameLayout.LayoutParams(px(size), px(size)));
        if (crestUrl != null) {
            ImageView crest = new ImageView(c);
            crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
            loadCrest(crest, crestUrl);
            int cs = Math.round(size * 0.38f);
            f.addView(crest, new FrameLayout.LayoutParams(px(cs), px(cs), Gravity.BOTTOM | Gravity.END));
        }
        return f;
    }

    /**
     * The scorers, each with his picture, when they all fit in the row: false (nothing added) when they do not, and
     * the plain line is drawn instead.
     */
    private static boolean goalChips(Context c, ScoresTicker.Game g, String[] p, LinearLayout row, int room) {
        if (p.length < 8) return false;
        String[][] full = {p[4].isEmpty() ? new String[0] : p[4].split("\\|"), p[5].isEmpty() ? new String[0] : p[5].split("\\|")};
        String[][] bits = {p[6].isEmpty() ? new String[0] : p[6].split("\\|"), p[7].isEmpty() ? new String[0] : p[7].split("\\|")};
        android.graphics.Paint pt = text(c, "", 22, INK, ScoreCard.outfit(c, 700)).getPaint();
        float need = px(18);
        for (int s = 0; s < 2; s++) {
            if (bits[s].length == 0) need += pt.measureText("–");
            for (int i = 0; i < bits[s].length; i++) need += px(40 + 6) + pt.measureText(bits[s][i]) + (i > 0 ? px(16) : 0);
        }
        if (need > room || full[0].length != bits[0].length || full[1].length != bits[1].length) return false;
        int[] kit = sideColours(g);
        for (int s = 0; s < 2; s++) {
            LinearLayout side = new LinearLayout(c);
            side.setGravity(Gravity.CENTER_VERTICAL);
            if (bits[s].length == 0) side.addView(text(c, "–", 22, INK, ScoreCard.outfit(c, 700)));
            for (int i = 0; i < bits[s].length; i++) {
                LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(px(40), px(40));
                if (i > 0) fl.leftMargin = px(16);
                side.addView(portrait(c, full[s][i], null, kit[s], 40), fl);
                TextView t = text(c, bits[s][i], 22, INK, ScoreCard.outfit(c, 700));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
                tl.leftMargin = px(6);
                side.addView(t, tl);
            }
            row.addView(side, new LinearLayout.LayoutParams(-2, -2));
            if (s == 0) row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        return true;
    }

    /** One side of a substitution: the player's picture, ON in green or OFF in red, and his name. */
    private static View subSide(Context c, String who, boolean on, String[] p, int width) {
        LinearLayout side = new LinearLayout(c);
        side.setGravity(Gravity.CENTER_VERTICAL);
        side.addView(portrait(c, who, null, p.length > 6 ? intOf(p[6]) : 0, 54), new LinearLayout.LayoutParams(px(54), px(54)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView way = text(c, on ? "▲ ON" : "▼ OFF", 15, on ? 0xFF1F9D5C : 0xFFD0182B, ScoreCard.outfit(c, 900));
        way.setLetterSpacing(0.08f);
        words.addView(way);
        TextView name = text(c, who, 22, INK, ScoreCard.outfit(c, 800));
        fit(name, 22, 14, width - px(64));
        words.addView(name);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.leftMargin = px(10);
        side.addView(words, wl);
        return side;
    }

    // ---- the line-ups ----------------------------------------------------------------------------------------------

    /** Each game's line-ups as pages once they are out (for adb's test and render). */
    private static final Map<String, List<String[]>> lastLineups = new java.util.concurrent.ConcurrentHashMap<>();
    /** Whether each game read had its line-ups yet, so their coming out can be told. */
    private static final Map<String, Boolean> lineupsKnown = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Both starting elevens as four pages, home first: each side's keeper and defence, then its midfield and attack,
     * every player with his picture and surname, the side and its formation across the top.
     * {label, surnames ("|"), full names ("|"), "lineup", crest, club, kit}. Null until both are out.
     */
    private static List<String[]> lineupPages(ScoresTicker.Game g, org.json.JSONArray rosters) throws org.json.JSONException {
        List<String[]> out = new ArrayList<>();
        for (int side = 0; side < 2; side++) {
            org.json.JSONObject r = null;
            for (int i = 0; rosters != null && i < rosters.length(); i++) {
                org.json.JSONObject x = rosters.getJSONObject(i);
                if ("home".equals(x.optString("homeAway")) == (side == 0)) r = x;
            }
            org.json.JSONArray players = r == null ? null : r.optJSONArray("roster");
            List<String[]> back = new ArrayList<>(), front = new ArrayList<>();   // {surname, full name, position}
            for (int i = 0; players != null && i < players.length(); i++) {
                org.json.JSONObject pl = players.getJSONObject(i);
                if (!pl.optBoolean("starter")) continue;
                org.json.JSONObject a = pl.optJSONObject("athlete");
                if (a == null) continue;
                String pos = pl.optJSONObject("position") == null ? "" : pl.getJSONObject("position").optString("abbreviation").toUpperCase(Locale.ROOT);
                String last = a.optString("lastName", "");
                String[] one = {last.isEmpty() || "null".equals(last) ? a.optString("displayName") : last, a.optString("displayName"), pos};
                (defence(pos) ? back : front).add(one);
            }
            if (back.size() + front.size() < 11) return null;
            java.util.Comparator<String[]> across = (x, y) -> Integer.compare(across(x[2]), across(y[2]));
            back.sort(across);
            front.sort(across);
            boolean home = side == 0;
            String name = (home ? g.homeName : g.awayName);
            String shape = r.optString("formation", "");
            String label = "LINE-UP  ·  " + (name == null ? "" : name.toUpperCase(Locale.ROOT)) + (shape.isEmpty() ? "" : "  ·  " + shape);
            for (List<String[]> line : java.util.Arrays.asList(back, front)) {
                List<String> sur = new ArrayList<>(), full = new ArrayList<>();
                for (String[] x : line) { sur.add(x[0]); full.add(x[1]); }
                out.add(new String[]{label, TextUtils.join("|", sur), TextUtils.join("|", full), "lineup",
                        home ? g.homeLogo : g.awayLogo, name, String.valueOf(sideColours(g)[home ? 0 : 1])});
            }
        }
        return out;
    }

    /** The keeper and the back line ("G", "CD-L", "RB", "LWB"...). */
    private static boolean defence(String pos) {
        return pos.equals("G") || pos.equals("GK") || pos.startsWith("CD") || pos.startsWith("CB") || pos.equals("D")
                || pos.equals("RB") || pos.equals("LB") || pos.equals("RWB") || pos.equals("LWB") || pos.equals("SW");
    }

    /** Where a position stands, keeper first, then from the back forward and, along each line, right to left. */
    private static int across(String pos) {
        int line = pos.equals("G") || pos.equals("GK") ? 0 : defence(pos) ? 1 : pos.startsWith("DM") ? 2
                : pos.startsWith("CM") || pos.equals("M") ? 3 : pos.equals("RM") || pos.equals("LM") ? 4
                : pos.startsWith("AM") ? 5 : 6;
        // Right to left: the right-back (or right winger) outside the right centre-back, and so on.
        int side = pos.endsWith("-R") ? 1 : pos.endsWith("-L") ? 3 : pos.startsWith("R") ? 0 : pos.startsWith("L") ? 4 : 2;
        return line * 5 + side;
    }

    /**
     * The line-ups read: kept, and when they have just come out (the game read before without them) told at once:
     * the panel folds out with all four pages in turn, the pictures looked up as it goes (it waits no more than a few
     * seconds for them).
     */
    private static void lineupsOut(ScoresTicker.Game g, List<String[]> xi) {
        if (xi != null) lastLineups.put(g.id, xi);
        Boolean before = lineupsKnown.put(g.id, xi != null);
        if (xi == null || before == null || before) return;
        tellLineups(g.id, xi);
    }

    private static void tellLineups(String gameId, List<String[]> xi) {
        java.util.concurrent.atomic.AtomicBoolean told = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable tell = () -> { if (told.compareAndSet(false, true)) showEvents(gameId, new ArrayList<>(xi)); };
        faces.execute(() -> {
            for (String[] p : xi) for (String who : p[2].split("\\|")) face(who, p[5]);
            main.post(tell);
        });
        main.postDelayed(tell, 6_000L);
    }

    /** One line of a side's eleven: each player's picture over his surname, across the panel. */
    private static void lineupRow(Context c, String[] p, LinearLayout row, int room) {
        String[] names = p[1].split("\\|"), full = p[2].split("\\|");
        int n = Math.max(1, names.length), chip = Math.min(px(86), room / n), kit = p.length > 6 ? intOf(p[6]) : 0;
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < names.length; i++) {
            LinearLayout one = new LinearLayout(c);
            one.setOrientation(LinearLayout.VERTICAL);
            one.setGravity(Gravity.CENTER_HORIZONTAL);
            one.addView(portrait(c, i < full.length ? full[i] : names[i], null, kit, 44), new LinearLayout.LayoutParams(px(44), px(44)));
            TextView t = text(c, names[i], 15, INK, ScoreCard.outfit(c, 700));
            t.setGravity(Gravity.CENTER);
            fit(t, 15, 10, chip - px(6));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
            tl.topMargin = px(3);
            one.addView(t, tl);
            row.addView(one, new LinearLayout.LayoutParams(chip, -2));
        }
    }

    /** adb's test hook: the shown game's line-ups told as if just out. */
    static void testLineups() {
        ScoresTicker.Game g = shown();
        List<String[]> xi = g == null ? null : lastLineups.get(g.id);
        if (xi != null) tellLineups(g.id, xi);
    }

    // ---- goals and substitutions as they happen: the panel folds out with them ------------------------------------

    /** Each game's goals and substitutions already seen, so only new ones fold the panel out. */
    private static final Map<String, java.util.Set<String>> seenEvents = new java.util.concurrent.ConcurrentHashMap<>();
    /** Each game's latest substitution and goal as pages (for adb's render). */
    private static final Map<String, String[]> lastSub = new java.util.concurrent.ConcurrentHashMap<>(), lastGoal = new java.util.concurrent.ConcurrentHashMap<>();
    private static final List<String[]> foldOuts = new ArrayList<>();
    private static boolean eventOn;
    private static final long EVENT_MS = 9_000L;

    /**
     * The game's goals and substitutions in its match summary: those not seen before become pages (a goal: the scorer
     * with his picture; a substitution: both players with theirs), their pictures looked up, then shown one after
     * another with the panel folded out. On the first read of a game nothing is shown: they are all old news.
     */
    private static void newEvents(ScoresTicker.Game g, org.json.JSONArray keyEvents) throws org.json.JSONException {
        java.util.Set<String> seen = seenEvents.get(g.id);
        boolean first = seen == null;
        if (first) { seen = java.util.concurrent.ConcurrentHashMap.newKeySet(); seenEvents.put(g.id, seen); }
        List<String[]> fresh = new ArrayList<>();
        for (int i = 0; keyEvents != null && i < keyEvents.length(); i++) {
            org.json.JSONObject e = keyEvents.getJSONObject(i);
            String type = e.optJSONObject("type") == null ? "" : e.getJSONObject("type").optString("text").toLowerCase(Locale.ROOT);
            boolean sub = type.contains("substitution"), goal = e.optBoolean("scoringPlay") && !type.contains("shootout");
            if (!sub && !goal) continue;
            org.json.JSONArray ps = e.optJSONArray("participants");
            String a = name(ps, 0), b = name(ps, 1);
            if (a.isEmpty()) continue;
            String team = e.optJSONObject("team") == null ? "" : e.getJSONObject("team").optString("id");
            boolean home = !team.equals(g.awayId);
            String club = home ? g.homeName : g.awayName;
            String at = e.optJSONObject("clock") == null ? "" : e.getJSONObject("clock").optString("displayValue").replace("'+", "+");
            String side = e.optJSONObject("team") == null ? "" : e.getJSONObject("team").optString("displayName").toUpperCase(Locale.ROOT);
            String[] page;
            if (sub) {
                page = new String[]{"SUBSTITUTION  ·  " + side + "  ·  " + at, a, b, "sub", home ? g.homeLogo : g.awayLogo, club,
                        String.valueOf(sideColours(g)[home ? 0 : 1])};
                lastSub.put(g.id, page);
            } else {
                String how = type.contains("penalty") ? "Penalty" : type.contains("own goal") ? "Own goal"
                        : !b.isEmpty() ? "Assist: " + b : type.contains("header") ? "Header" : "";
                page = new String[]{"GOAL  ·  " + side + "  ·  " + at, a, how, "player", home ? g.homeLogo : g.awayLogo, club,
                        String.valueOf(sideColours(g)[home ? 0 : 1])};
                lastGoal.put(g.id, page);
            }
            String key = type + "|" + (e.optJSONObject("clock") == null ? "" : e.getJSONObject("clock").optString("value")) + "|" + a;
            if (seen.add(key) && !first) fresh.add(page);
        }
        if (fresh.isEmpty()) return;
        String id = g.id;
        faces.execute(() -> {
            for (String[] p : fresh) { face(p[1], p[5]); if ("sub".equals(p[3])) face(p[2], p[5]); }
            main.post(() -> showEvents(id, fresh));
        });
    }

    private static String name(org.json.JSONArray ps, int i) throws org.json.JSONException {
        org.json.JSONObject who = ps == null || ps.length() <= i ? null : ps.getJSONObject(i).optJSONObject("athlete");
        return who == null ? "" : who.optString("displayName");
    }

    /** New goals and substitutions of the game on the scoreboard, in turn, with the panel folded out. */
    private static void showEvents(String gameId, List<String[]> fresh) {
        ScoresTicker.Game g = shown();
        if (host == null || boardHidden || g == null || !gameId.equals(g.id)) return;
        foldOuts.addAll(fresh);
        if (!eventOn) nextEvent.run();
    }

    private static final Runnable nextEvent = new Runnable() {
        @Override public void run() {
            if (host == null) { eventOn = false; foldOuts.clear(); return; }
            if (foldOuts.isEmpty()) {
                // All shown: the panel folds back in, and the stats come round again as before.
                eventOn = false;
                openStats(false);
                main.removeCallbacks(statsTurn);
                main.postDelayed(statsTurn, SHUT_MS);
                return;
            }
            eventOn = true;
            main.removeCallbacks(statsTurn);
            String[] p = foldOuts.remove(0);
            putPage(pageView(app, shown(), p), statsOpen);
            if (!statsOpen) openStats(true);
            main.postDelayed(this, "lineup".equals(p[3]) ? 6_500L : EVENT_MS);
        }
    };

    /** adb's test hook: the shown game's latest substitution (else its latest goal) folded out as it happens. */
    static void testEvent() {
        ScoresTicker.Game g = shown();
        if (g == null) return;
        String[] p = lastSub.containsKey(g.id) ? lastSub.get(g.id) : lastGoal.get(g.id);
        if (p == null) return;
        faces.execute(() -> {
            face(p[1], p[5]);
            if ("sub".equals(p[3])) face(p[2], p[5]);
            List<String[]> one = new ArrayList<>();
            one.add(p);
            main.post(() -> showEvents(g.id, one));
        });
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
        leagueBox.addView(tableLine(c, null, "", "", "P", "W", "D", "L", "GD", "PTS", null, 0, true), new LinearLayout.LayoutParams(-1, px(24)));
        int max = Math.min(rows.size(), 20);
        for (int i = 0; i < max; i++) {
            Object[] r = rows.get(i);
            Club club = (Club) r[0];
            int moved = (Integer) r[1] - (i + 1);
            leagueBox.addView(tableLine(c, club.logo, String.valueOf(i + 1), shortName(club.name, club.abbr), String.valueOf(club.played),
                    String.valueOf(club.won), String.valueOf(club.drawn), String.valueOf(club.lost), (club.gd > 0 ? "+" : "") + club.gd, String.valueOf(club.pts), (String) r[2], moved, false),
                    new LinearLayout.LayoutParams(-1, px(rows.size() > 18 ? 26 : 28)));
        }
        leagueBox.setAlpha(0f);
        leagueBox.animate().alpha(1f).setDuration(300).withLayer().start();
    }

    private static View tableLine(Context c, String crest, String pos, String name, String p, String w, String d, String l, String gd, String pts, String score,
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
        // Played, won, drawn and lost narrow, so the club's name keeps its room in the box.
        for (String[] col : new String[][]{{p, "26"}, {w, "24"}, {d, "24"}, {l, "24"}, {gd, "38"}}) {
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

    /** The ticker: every game's score, league by league, a page at a time along the white band. */
    private static void fillTicker(Context c) {
        List<String> parts = new ArrayList<>();
        String last = null;
        for (ScoresTicker.Game g : games) {
            String head = "";
            if (!java.util.Objects.equals(g.slug, last)) {
                last = g.slug;
                for (String e : LEAGUES) if (e.startsWith(g.slug + "|")) head = e.split("\\|")[1] + "   ";
            }
            if (g.state == 1) parts.add(head + g.home + " v " + g.away + " (" + minute(g) + ")");
            else parts.add(head + g.home + ' ' + score(g.homeScore) + '–' + score(g.awayScore) + ' ' + g.away + " (" + minute(g) + ")");
        }
        F1Results.Weekend w = f1;
        if (w != null) {
            for (F1Results.Session x : w.sessions) {
                if (!"in".equals(x.state) || x.top.isEmpty()) continue;
                StringBuilder f = new StringBuilder("F1  ").append(x.isRace() && x.lap > 0 ? "LAP " + x.lap : F1Results.label(x.abbr)).append("   ");
                for (int i = 0; i < Math.min(5, x.top.size()); i++) f.append(i + 1).append(' ').append(F1Results.code(x.top.get(i))).append("   ");
                parts.add(f.toString().trim());
            }
        }
        if (parts.isEmpty()) parts.add("No games today");
        // As many as fit the band's width on each page, "    ·    " between them.
        float room = (tickerLine != null && tickerLine.getWidth() > 0 ? tickerLine.getWidth() : px(1500)) - px(72);
        android.text.TextPaint paint = tickerText.getPaint();
        String gap = "    ·    ";
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        for (String part : parts) {
            String next = page.length() == 0 ? part : page + gap + part;
            if (page.length() > 0 && paint.measureText(next) > room) {
                pages.add(page.toString());
                page = new StringBuilder(part);
            } else {
                page = new StringBuilder(next);
            }
        }
        if (page.length() > 0) pages.add(page.toString());
        if (pages.equals(tickerPages)) return;
        boolean first = tickerPages.isEmpty();
        tickerPages = pages;
        if (tickerAt >= pages.size()) tickerAt = 0;
        // The page showing changes at once only the first time (or when it is gone); else at the next turn.
        if (first || !tickerMoving && !pages.contains(tickerText.getText().toString())) tickerText.setText(pages.get(tickerAt));
        main.removeCallbacks(tickerTurn);
        main.postDelayed(tickerTurn, TICKER_PAGE_MS);
    }

    private static TextView tickerView(Context c) {
        TextView t = text(c, "", 21, INK, ScoreCard.outfit(c, 700));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(px(36), 0, px(36), 0);
        return t;
    }

    /** Every TICKER_PAGE_MS the next page glides in from the right as the one showing goes left; still in between. */
    private static final Runnable tickerTurn = new Runnable() {
        @Override public void run() {
            if (host == null || tickerNext == null) return;
            main.postDelayed(this, TICKER_PAGE_MS);
            if (tickerPages.size() < 2 || tickerMoving || flashing != null) return;
            tickerAt = (tickerAt + 1) % tickerPages.size();
            float wdt = tickerLine.getWidth() > 0 ? tickerLine.getWidth() : px(1500);
            TextView out = tickerText, in = tickerNext;
            in.setText(tickerPages.get(tickerAt));
            in.setTranslationX(wdt);
            in.setAlpha(1f);
            in.setVisibility(View.VISIBLE);
            tickerMoving = true;
            android.view.animation.PathInterpolator ease = new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f);
            out.animate().translationX(-wdt * 0.5f).alpha(0f).setDuration(TICKER_MOVE_MS).setInterpolator(ease).start();
            in.animate().translationX(0f).setDuration(TICKER_MOVE_MS).setInterpolator(ease).withEndAction(() -> {
                out.setVisibility(View.INVISIBLE);
                out.setTranslationX(0f);
                out.setAlpha(1f);
                tickerText = in;
                tickerNext = out;
                tickerMoving = false;
            }).start();
        }
    };

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
                GoalClips.highlights(g, (g.homeName != null ? g.homeName : g.home) + " " + score(g.homeScore) + "–"
                        + score(g.awayScore) + " " + (g.awayName != null ? g.awayName : g.away));
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
        // Highlights playing: Back stops them (the channel's sound comes back); every other key waits.
        if (bigBox != null) {
            if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(LiveCentre::closeBig);
            return code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_VOLUME_MUTE;
        }
        // A highlights card up: OK plays them.
        if (offerBox != null && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(LiveCentre::watchOffer);
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(() -> { if (onBoard) hideBoard(); else close(); });
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (SHOW_BOARD && down && e.getRepeatCount() == 0) main.post(() -> focusBoard(true));
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
            bar.setVisibility(View.VISIBLE);
            bar.setAlpha(0f);
            bar.setTranslationY(-px(20));
            bar.animate().alpha(1f).translationY(0).setDuration(280).start();
        }
        onBoard = on;
        bar.ring = on;
        bar.invalidate();
        // It grows from its top left corner.
        bar.setPivotX(0);
        bar.setPivotY(0);
        bar.animate().scaleX(BOARD_SCALE * (on ? 1.03f : 1f)).scaleY(BOARD_SCALE * (on ? 1.03f : 1f)).setDuration(160).start();
    }

    /** Back on the scoreboard: it and its stats slide away (Up brings them back). */
    private static void hideBoard() {
        focusBoard(false);
        boardHidden = true;
        bar.animate().alpha(0f).translationY(-px(20)).setDuration(240).withEndAction(() -> { if (boardHidden) bar.setVisibility(View.GONE); }).start();
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

    /**
     * The games a goal's clip is looked for in: every LaLiga game (ESPN publishes a clip of each of its goals), the
     * Premier League's and the Champions League's, and the big clubs' anywhere (in case ESPN has one).
     */
    private static boolean clipWanted(ScoresTicker.Game g) {
        if ("esp.1".equals(g.slug) || "eng.1".equals(g.slug) || "uefa.champions".equals(g.slug)) return true;
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

    // ---- official highlights at full time ------------------------------------------------------------------------

    private static FrameLayout offerBox, bigBox;
    private static List<OfficialVideos.Video> offerList;
    private static String offerLine;
    private static final List<Object[]> offerQueue = new ArrayList<>();
    private static android.media.MediaPlayer bigPlayer;
    private static android.webkit.WebView bigWeb;
    private static android.media.AudioFocusRequest bigFocus;
    private static final long OFFER_MS = 20_000L;
    private static final Runnable offerTimeout = () -> endOffer(true);

    /**
     * A finished game's official highlights are up: a card in the clip's corner (HIGHLIGHTS on blue, the result, the
     * video's picture and "OK Watch") for twenty seconds; OK plays them. Waits for a goal clip or another card.
     */
    static void offerHighlights(String line, List<OfficialVideos.Video> list) {
        if (host == null || list == null || list.isEmpty()) return;
        if (clipBox != null || offerBox != null || bigBox != null) { offerQueue.add(new Object[]{line, list}); return; }
        Context c = app;
        int w = px(576), h = px(324);
        FrameLayout box = new FrameLayout(c);
        box.setClipToOutline(true);
        box.setOutlineProvider(round(px(14)));
        box.setBackgroundColor(0xFF000000);
        box.setElevation(px(14));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(videoHead(c, "HIGHLIGHTS", line, null), new LinearLayout.LayoutParams(-1, px(38)));
        FrameLayout pic = new FrameLayout(c);
        ImageView thumb = new ImageView(c);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pic.addView(thumb, new FrameLayout.LayoutParams(-1, -1));
        // "OK Watch" in a white pill at the picture's foot.
        LinearLayout pill = new LinearLayout(c);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(px(12), px(6), px(16), px(6));
        GradientDrawable pg = new GradientDrawable();
        pg.setColor(0xF2FFFFFF);
        pg.setCornerRadius(px(24));
        pill.setBackground(pg);
        ImageView ok = new ImageView(c);
        ok.setImageBitmap(PagesV2.SportPage.okKey(Config.themeString(c, "ok_button", "tcl"), px(30)));
        pill.addView(ok, new LinearLayout.LayoutParams(px(30), px(30)));
        TextView watch = text(c, "Watch", 19, INK, ScoreCard.outfit(c, 800));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.leftMargin = px(8);
        pill.addView(watch, wl);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        pl.rightMargin = px(14);
        pl.bottomMargin = px(14);
        pic.addView(pill, pl);
        col.addView(pic, new LinearLayout.LayoutParams(w, h));
        box.addView(col, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(w, h + px(38), Gravity.TOP | Gravity.START);
        bl.leftMargin = px(1500 - 576 - 26);
        bl.topMargin = px(900 - 324 - 38 - 22);
        host.addView(box, bl);
        offerBox = box;
        offerList = list;
        offerLine = line;
        String t = list.get(0).thumb;
        if (t != null && !t.isEmpty()) io.execute(() -> {
            Bitmap b = ScoresTicker.cachedLogo(app, t, 480);
            if (b != null) main.post(() -> thumb.setImageBitmap(b));
        });
        box.setAlpha(0f);
        box.setTranslationY(px(30));
        box.animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).start();
        main.removeCallbacks(offerTimeout);
        main.postDelayed(offerTimeout, OFFER_MS);
    }

    /** The card away; the next one waiting (or a goal clip's turn) after it unless the highlights are being played. */
    private static void endOffer(boolean next) {
        main.removeCallbacks(offerTimeout);
        FrameLayout box = offerBox;
        offerBox = null;
        if (box == null) return;
        box.animate().alpha(0f).translationY(px(30)).setDuration(260).withEndAction(() -> {
            if (box.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) box.getParent()).removeView(box);
            if (next && host != null && bigBox == null && clipBox == null && !offerQueue.isEmpty()) {
                Object[] n = offerQueue.remove(0);
                @SuppressWarnings("unchecked") List<OfficialVideos.Video> v = (List<OfficialVideos.Video>) n[1];
                offerHighlights((String) n[0], v);
            }
        }).start();
    }

    /** OK on the card: the highlights played large over the channel, with their sound. */
    private static void watchOffer() {
        List<OfficialVideos.Video> list = offerList;
        String line = offerLine;
        endOffer(false);
        if (list != null) playBig(list, 0, line);
    }

    /**
     * Highlights played large over the channel's picture, with their sound (the channel's paused meanwhile, as for a
     * moment, and back when they end or Back stops them); a video YouTube will not play here gives way to the next.
     */
    private static void playBig(List<OfficialVideos.Video> list, int at, String line) {
        if (host == null || at >= list.size()) { closeBig(); return; }
        closeBig();
        Context c = app;
        OfficialVideos.Video v = list.get(at);
        // Under the scoreboard and above the ticker, over the middle of the channel's picture.
        int w = px(1216), h = px(684);
        FrameLayout box = new FrameLayout(c);
        box.setClipToOutline(true);
        box.setOutlineProvider(round(px(16)));
        box.setBackgroundColor(0xFF000000);
        box.setElevation(px(18));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(videoHead(c, "HIGHLIGHTS", line, "Back to close"), new LinearLayout.LayoutParams(-1, px(44)));
        View video;
        if (v.youtube != null) {
            bigWeb = YouTubeBox.make(c, v.youtube, false, ev -> {
                if (ev.startsWith("error")) playBig(list, at + 1, line);
                else if ("state0".equals(ev)) closeBig();
            });
            video = bigWeb;
        } else {
            android.view.TextureView tv = new android.view.TextureView(c);
            tv.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int sw, int sh) {
                    android.media.MediaPlayer mp = new android.media.MediaPlayer();
                    bigPlayer = mp;
                    try {
                        mp.setDataSource(v.mp4);
                        mp.setSurface(new android.view.Surface(st));
                        mp.setOnPreparedListener(m -> { if (bigPlayer == m) m.start(); });
                        mp.setOnCompletionListener(m -> closeBig());
                        mp.setOnErrorListener((m, what, extra) -> { playBig(list, at + 1, line); return true; });
                        mp.prepareAsync();
                    } catch (Exception e) {
                        playBig(list, at + 1, line);
                    }
                }
                @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int sw, int sh) { }
                @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) { return true; }
                @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) { }
            });
            video = tv;
        }
        col.addView(video, new LinearLayout.LayoutParams(w, h));
        box.addView(col, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(w, h + px(44), Gravity.TOP | Gravity.START);
        bl.leftMargin = px((1500 - 1216) / 2);
        bl.topMargin = px(22 + (SHOW_BOARD ? BOARD_H * BOARD_SCALE + 18 : 0));
        host.addView(box, bl);
        bigBox = box;
        box.setAlpha(0f);
        box.setScaleX(0.96f);
        box.setScaleY(0.96f);
        box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(new DecelerateInterpolator(2f)).start();
        // The channel's sound paused for the highlights' (a passing interruption, so TiviMate carries on after).
        android.media.AudioManager am = c.getSystemService(android.media.AudioManager.class);
        bigFocus = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build()).build();
        if (am != null) am.requestAudioFocus(bigFocus);
    }

    /** The highlights stopped and away, the channel's sound back; the next card waiting, if any. */
    static void closeBig() {
        android.webkit.WebView web = bigWeb;
        bigWeb = null;
        android.media.MediaPlayer mp = bigPlayer;
        bigPlayer = null;
        if (mp != null) { try { mp.release(); } catch (RuntimeException ignored) { } }
        if (bigFocus != null && app != null) {
            android.media.AudioManager am = app.getSystemService(android.media.AudioManager.class);
            if (am != null) am.abandonAudioFocusRequest(bigFocus);
            bigFocus = null;
        }
        FrameLayout box = bigBox;
        bigBox = null;
        if (web != null) {
            web.loadUrl("about:blank");
            main.postDelayed(web::destroy, 400L);
        }
        if (box == null) return;
        box.animate().alpha(0f).setDuration(220).withEndAction(() -> {
            if (box.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) box.getParent()).removeView(box);
            if (host != null && clipBox == null && offerBox == null && !offerQueue.isEmpty()) {
                Object[] n = offerQueue.remove(0);
                @SuppressWarnings("unchecked") List<OfficialVideos.Video> v = (List<OfficialVideos.Video>) n[1];
                offerHighlights((String) n[0], v);
            }
        }).start();
    }

    /** A video box's head: its label on a blue slant, the game, and a hint at the right when given. */
    private static View videoHead(Context c, String what, String line, String hint) {
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(NAVY);
        head.setPadding(0, 0, px(14), 0);
        TextView label = text(c, what, 17, 0xFFFFFFFF, ScoreCard.outfit(c, 900));
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(px(14), 0, px(20), 0);
        label.setBackground(new ScoreCard.Slant(0xFF2457E0, px(10), false));
        head.addView(label, new LinearLayout.LayoutParams(-2, -1));
        TextView match = text(c, line == null ? "" : line, 18, 0xFFFFFFFF, ScoreCard.outfit(c, 700));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, -2, 1f);
        ml.leftMargin = px(12);
        head.addView(match, ml);
        if (hint != null) {
            TextView h = text(c, "", 15, 0xFFC9CEF2, ScoreCard.outfit(c, 600));
            // The remote's own Back (or OK) key in place of the word.
            h.setText(Keys.icons(c, hint, h.getTextSize(), true));
            head.addView(h, new LinearLayout.LayoutParams(-2, -2));
        }
        return head;
    }

    /**
     * adb only: with the Live Centre open, a finished game's highlights offered as at full time (a game by a club's
     * name, else the first finished one with highlights up).
     */
    static void testHighlights(String club) {
        if (host == null) return;
        String want = club == null ? "" : OfficialVideos.plain(club);
        List<ScoresTicker.Game> pool = new ArrayList<>(games);
        io.execute(() -> {
            for (ScoresTicker.Game g : pool) {
                if (g.state != 2 || !OfficialVideos.covered(g.slug)) continue;
                if (!want.isEmpty() && !OfficialVideos.plain(g.homeName + " " + g.awayName).contains(want)) continue;
                List<OfficialVideos.Video> v = OfficialVideos.highlights(g);
                if (v.isEmpty()) continue;
                String line = g.homeName + " " + score(g.homeScore) + "–" + score(g.awayScore) + " " + g.awayName;
                main.post(() -> offerHighlights(line, v));
                return;
            }
        });
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
            } else if (!offerQueue.isEmpty() && host != null) {
                Object[] n = offerQueue.remove(0);
                @SuppressWarnings("unchecked") List<OfficialVideos.Video> v = (List<OfficialVideos.Video>) n[1];
                offerHighlights((String) n[0], v);
            }
        }).start();
    }

    static void close() {
        MatchNight.ended();
        if (folding != null) { folding.cancel(); folding = null; }
        closeBig();
        offerQueue.clear();
        main.removeCallbacks(offerTimeout);
        offerBox = null;
        offerList = null;
        onBoard = false;
        boardHidden = !SHOW_BOARD;
        eventOn = false;
        foldOuts.clear();
        main.removeCallbacks(nextEvent);
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
        main.removeCallbacks(tickerTurn);
        tickerPages = new ArrayList<>();
        tickerAt = 0;
        tickerMoving = false;
        tickerNext = null;
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
