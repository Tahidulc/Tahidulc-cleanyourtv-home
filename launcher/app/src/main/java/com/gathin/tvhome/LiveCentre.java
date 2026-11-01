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
    static final String[] LEAGUES = {"eng.1|PREMIER LEAGUE", "eng.2|CHAMPIONSHIP", "esp.1|LALIGA", "ita.1|SERIE A",
            "ger.1|BUNDESLIGA", "fra.1|LIGUE 1"};
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
            List<ScoresTicker.Game> read = read(app);
            ScoresTicker.Game pick = choose(read, gameId);
            Object[] w = pick == null ? null : GoalWatchService.watchForGame(app, pick);
            main.post(() -> {
                games = read;
                shownId = pick == null ? null : pick.id;
                learn(read, true);
                boolean tuning = w != null && tune(w);
                show(!tuning && w == null);
                if (tuning) waitForTiviMate(0);
            });
        });
    }

    /** The six leagues' games today, live first, the Premier League's first within each state. */
    private static List<ScoresTicker.Game> read(Context c) {
        List<ScoresTicker.Game> all = ScoresTicker.load(LEAGUES);
        all.sort((a, b) -> a.state != b.state ? Integer.compare(a.state, b.state) : Integer.compare(rank(a), rank(b)));
        // The tables of the leagues with games under way (their standings before today's games went on).
        for (ScoresTicker.Game g : all) if ((g.state == 0 || testCard) && g.slug != null) standings(g.slug);
        return all;
    }

    // ---- live tables ----------------------------------------------------------------------------------------------

    /** A club's line in a table: ESPN's id, name, crest, played, goal difference, points, and its place. */
    private static final class Club {
        String id, name, abbr, logo;
        int played, gd, pts, rank;
    }

    private static final Map<String, List<Club>> tables = new java.util.concurrent.ConcurrentHashMap<>();
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

        // The game's score, small, in the top left corner (over the broadcaster's own).
        mini = new LinearLayout(c);
        mini.setOrientation(LinearLayout.VERTICAL);
        mini.setClipToOutline(true);
        mini.setOutlineProvider(round(px(12)));
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(px(270), -2, Gravity.TOP | Gravity.START);
        ml.leftMargin = px(28);
        ml.topMargin = px(26);
        win.addView(mini, ml);

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
        boolean takeKeys = !InputKeyService.running;
        if (takeKeys) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            return;
        }
        host = win;
        if (takeKeys) {
            // Without the key helper (the TCL) the frame takes the remote's keys itself while it shows.
            win.setFocusable(true);
            win.setFocusableInTouchMode(true);
            win.requestFocus();
            win.setOnKeyListener((v, code, e) -> handle(code, e));
        }
        fill();
        win.setAlpha(0f);
        win.animate().alpha(1f).setDuration(320).withLayer().start();
        main.postDelayed(tick, 1_000L);
        main.postDelayed(reread, READ_MS);
        main.postDelayed(nextLeague, LEAGUE_MS);
    }

    /** The Live Centre's stamp, where Sky's channel name sits: LIVE on the launcher's green, CENTRE on white. */
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

    private static void fillMini(Context c) {
        mini.removeAllViews();
        ScoresTicker.Game g = shown();
        if (g == null) { mini.setVisibility(View.GONE); return; }
        mini.setVisibility(View.VISIBLE);
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(NAVY);
        head.setPadding(px(14), px(6), px(14), px(6));
        head.addView(text(c, minute(g), 22, 0xFFFFFFFF, ScoreCard.outfit(c, 800)));
        TextView ground = text(c, g.venue == null ? "" : g.venue, 12, 0xFFC9CEF2, ScoreCard.outfit(c, 500));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(0, -2, 1f);
        gl.leftMargin = px(10);
        head.addView(ground, gl);
        mini.addView(head, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(0xF7FFFFFF);
        body.setPadding(px(14), px(8), px(10), px(10));
        body.addView(text(c, ScoresTicker.competition(g), 13, NAVY, ScoreCard.outfit(c, 700)));
        body.addView(side(c, g, true), new LinearLayout.LayoutParams(-1, -2));
        body.addView(side(c, g, false), new LinearLayout.LayoutParams(-1, -2));
        mini.addView(body, new LinearLayout.LayoutParams(-1, -2));
    }

    /** One side in the corner card: its crest, name and score box, and its scorers under it. */
    private static View side(Context c, ScoresTicker.Game g, boolean home) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(0, px(6), 0, 0);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView crest = new ImageView(c);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loadCrest(crest, home ? g.homeLogo : g.awayLogo);
        row.addView(crest, new LinearLayout.LayoutParams(px(24), px(24)));
        TextView name = text(c, shortName(home ? g.homeName : g.awayName, home ? g.home : g.away), 19, INK, ScoreCard.outfit(c, 700));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
        nl.leftMargin = px(8);
        row.addView(name, nl);
        TextView score = text(c, score(home ? g.homeScore : g.awayScore), 20, 0xFFFFFFFF, ScoreCard.outfit(c, 800));
        score.setGravity(Gravity.CENTER);
        score.setBackgroundColor(NAVY);
        row.addView(score, new LinearLayout.LayoutParams(px(30), px(30)));
        v.addView(row);
        StringBuilder who = new StringBuilder();
        for (String[] goal : g.goals) {
            if (!(home ? "home" : "away").equals(goal[3])) continue;
            if (who.length() > 0) who.append(", ");
            who.append(GoalWatchService.surname(goal[1])).append(' ').append(goal[2]);
        }
        if (who.length() > 0) {
            TextView w = text(c, who.toString(), 11.5f, 0xFF4A507F, ScoreCard.outfit(c, 500));
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
            wl.leftMargin = px(32);
            v.addView(w, wl);
        }
        return v;
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
            int colour = "RED".equals(e[0]) ? RED : "FT".equals(e[0]) ? NAVY : 0xFF1F9D5C;
            TextView label = text(c, e[0], 15, 0xFFFFFFFF, ScoreCard.outfit(c, 900));
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setPadding(px(10), 0, px(18), 0);
            label.setBackground(new ScoreCard.Slant(colour, px(8), false));
            row.addView(label, new LinearLayout.LayoutParams(px(84), px(26)));
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
                fresh.add(new Object[]{minuteValue(goal[2]), new String[]{"GOAL", line, GoalWatchService.surname(goal[1]), goal[2], goal[3]}});
            }
            for (String[] red : g.reds) {
                String key = g.id + "|r" + red[0];
                if (seen.containsKey(key)) continue;
                seen.put(key, now);
                fresh.add(new Object[]{minuteValue(red[2]), new String[]{"RED", line, GoalWatchService.surname(red[1]), red[2], red[3]}});
            }
            Integer before = lastStates.put(g.id, g.state);
            if (!first && before != null && before == 0 && g.state == 2) {
                fresh.add(new Object[]{999, new String[]{"FT", line, "Full time", "", ""}});
            }
        }
        fresh.sort((a, b) -> Integer.compare((Integer) b[0], (Integer) a[0]));
        List<String[]> add = new ArrayList<>();
        for (Object[] f : fresh) add.add((String[]) f[1]);
        events.addAll(0, add);
        while (events.size() > 20) events.remove(events.size() - 1);
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

    private static boolean handle(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(LiveCentre::close);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) main.post(() -> step(code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1));
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) main.post(() -> { leagueAt += code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1; fillLeague(app); });
            return true;
        }
        return false;
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

    static void close() {
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

    private static void loadCrest(ImageView v, String url) {
        if (url == null) return;
        io.execute(() -> {
            Bitmap b = ScoresTicker.cachedLogo(app, url, 96);
            if (b != null) main.post(() -> v.setImageBitmap(ScoreCard.onNavy(b)));
        });
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
