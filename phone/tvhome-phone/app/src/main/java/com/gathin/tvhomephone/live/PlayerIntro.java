package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;

import com.gathin.broadcast.BroadcastData;
import com.gathin.broadcast.BroadcastView;
import com.gathin.broadcast.Moments;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.TestAlerts;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The classic kick-off pop-up and line-ups over the player's picture, as on the TV (the launcher's ClassicIntro): the
 * line-ups once, from seven minutes before kick-off on the picture (Back closes them, the scoreboard out of their way
 * meanwhile); the pop-up at the kick-off, when play starts again after a goal and at the second half's kick-off. The
 * scoreboard (ScoreOverlay) tells it the channel's own game as the picture shows it. Each has its switch in Options.
 */
final class PlayerIntro {
    private static final String FILE = "classic_intro";

    private final Activity a;
    private final FrameLayout root;
    private final BooleanSupplier canShow;
    private final Consumer<Boolean> lineupsUp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Set<String> shown = new HashSet<>();
    private final Map<String, String> phases = new HashMap<>();
    private final Map<String, Long> tried = new HashMap<>();
    private BroadcastView view;
    private String viewKind = "";
    private boolean fetching;
    /** The picture's place in the player (the overlay is put over it). */
    private float px, py, pw = -1, ph = -1;

    PlayerIntro(Activity a, FrameLayout root, BooleanSupplier canShow, Consumer<Boolean> lineupsUp) {
        this.a = a;
        this.root = root;
        this.canShow = canShow;
        this.lineupsUp = lineupsUp;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** The line-ups' tab swish as anything slides out beside the scoreboard (the scorer, the stats, alerts): on unless off. */
    static boolean swishOn(Context c) {
        return prefs(c).getBoolean("swish", true);
    }

    static void setSwishOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("swish", on).apply();
        if (on) com.gathin.broadcast.Sfx.play(c, com.gathin.broadcast.Sfx.SWISH, 0f, 0.7f);
    }

    /** The family's swish as a goal's new score comes out on the scoreboard, every look: on unless switched off. */
    static boolean scoreSwishOn(Context c) {
        return prefs(c).getBoolean("score_swish", true);
    }

    static void setScoreSwishOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("score_swish", on).apply();
        com.gathin.broadcast.Sfx.warm(c, com.gathin.broadcast.Sfx.SCORE);
        // heard as it is switched on (the first time once it has loaded)
        if (on) new Handler(Looper.getMainLooper()).postDelayed(() -> com.gathin.broadcast.Sfx.play(c, com.gathin.broadcast.Sfx.SCORE, 0f, 0.85f), 250);
    }

    /**
     * The sound over the scoreboard's goal animation (Sfx.GOALS: "off", "swoosh", "roar", ...): the family's swoosh unless
     * another is chosen (a new key, so the swoosh they picked replaces whatever was tried before it).
     */
    static String goalSound(Context c) {
        return prefs(c).getString("goal_sound_pick", com.gathin.broadcast.Sfx.GOAL_DEFAULT);
    }

    static void setGoalSound(Context c, String key) {
        prefs(c).edit().putString("goal_sound_pick", key).apply();
        com.gathin.broadcast.Sfx.warm(c, "goal-" + key);
        // heard as it is chosen (the first time once it has loaded)
        if (!"off".equals(key)) new Handler(Looper.getMainLooper()).postDelayed(() -> com.gathin.broadcast.Sfx.goal(c, key), 250);
    }

    /** The pop-up's style: "classic" (crests, trophy and teams), "strap" (in the scoreboard's look) or "off". */
    static String popupStyle(Context c) {
        String s = prefs(c).getString("popup_style", null);
        return s != null ? s : prefs(c).getBoolean("popup", true) ? "classic" : "off";
    }

    static void setPopupStyle(Context c, String style) {
        prefs(c).edit().putString("popup_style", style).apply();
    }

    /** The line-ups' style: "classic" (the beam-in), "stadium" (each eleven on a board over a ground lit for the day) or "off". */
    static String lineupsStyle(Context c) {
        String s = prefs(c).getString("lineups_style", null);
        return s != null ? s : prefs(c).getBoolean("lineups", true) ? "classic" : "off";
    }

    static void setLineupsStyle(Context c, String style) {
        prefs(c).edit().putString("lineups_style", style).apply();
    }

    /** The intros (reveal.html) over the last break before kick-off, and as F1, UFC and boxing begin: on unless off. */
    static boolean introOn(Context c) {
        return prefs(c).getBoolean("intro", true);
    }

    static void setIntro(Context c, boolean on) {
        prefs(c).edit().putBoolean("intro", on).apply();
    }

    /**
     * The season intros (the top five leagues: every club in a scene of its own, then the Whitebrook Road Sports ending)
     * over an ad break before the game (ScoreOverlay's break watch), else as its matchday intro: on unless off.
     */
    static boolean seasonOn(Context c) {
        return prefs(c).getBoolean("season", true);
    }

    static void setSeason(Context c, boolean on) {
        prefs(c).edit().putBoolean("season", on).apply();
    }

    static boolean popupOn(Context c) {
        return !"off".equals(popupStyle(c));
    }

    static boolean lineupsOn(Context c) {
        return !"off".equals(lineupsStyle(c));
    }

    /** Where the scoreboard is over the picture, on a 1920 x 1080 picture {x, y, w, h} (the strap folds into it); null: unknown. */
    private java.util.function.Supplier<float[]> boardAt = () -> null;

    void boardAt(java.util.function.Supplier<float[]> where) {
        boardAt = where == null ? () -> null : where;
    }

    boolean showing() {
        return view != null && !view.isOver();
    }

    /**
     * The moments' pop-ups in the classic look: the kick-off, play starting again after a goal, the second half. The
     * scoreboard is away while one is on (never on the picture with it) and comes back in once it has gone, with its swish
     * (ScoreBoard). The strap is the board's own look and folds into it, so it is not one of them.
     */
    static boolean moment(String kind) {
        return com.gathin.broadcast.Moments.boardMoment(kind);
    }

    /** Whether a moment's pop-up is on the picture now: the scoreboard stays away meanwhile. */
    boolean momentShowing() {
        return showing() && moment(viewKind);
    }

    /** Told when a moment's pop-up comes or goes (the scoreboard away or back in now). */
    private Runnable momentChanged = () -> { };

    void momentChanged(Runnable r) {
        momentChanged = r == null ? () -> { } : r;
    }

    // ---- when ---------------------------------------------------------------------------------------------------------

    /** The channel's own game as the picture shows it (each time the scoreboard draws); screenNow: the picture's time. */
    void tick(Game g, long screenNow) {
        if (g == null || g.id == null) return;
        if (showing() && !canShow.getAsBoolean()) stop();
        String was = phases.get(g.id);
        // The half the scores give, for a game joined part way through (no kick-off or half time seen).
        String now = Moments.phase(false, g.state, g.halftime, was, g.period >= 1 && g.period <= 4 ? g.period : 0);
        if (!now.isEmpty()) phases.put(g.id, now);
        String kind = was == null ? null : Moments.moment(was, now);
        if (kind != null && popupOn(a)) popup(g, kind, "kickoff".equals(kind) ? null : new int[]{g.homeScore, g.awayScore}, null, null);
        if ("pre".equals(now) && g.kickoff > 0 && lineupsOn(a) && Moments.lineupsDue(lineupsStyle(a), g.kickoff - screenNow)) lineups(g);
        if ("pre".equals(now) && g.kickoff > 0 && introOn(a) && Moments.introDue(g.kickoff - screenNow)) intro(g);
    }

    /**
     * The matchday intro over the picture, once a game: the film, then the competition's trophy and the Matchday card; for
     * a top-five league's game whose season intro has not been over a break, that instead.
     */
    private void intro(Game g) {
        String key = g.id + "|intro";
        if (shown.contains(key) || showing() || fetching || !canShow.getAsBoolean()) return;
        shown.add(key);
        try {
            BroadcastData.Match m = match(g);
            String sk = BroadcastData.seasonKey(m.compId, m.compName);
            JSONObject cfg = seasonOn(a) && !sk.isEmpty() && !IntroVideo.useOwn(a) && shown.add(g.id + "|season")
                    ? BroadcastData.season(sk, BroadcastData.fixture(m.homeName, m.awayName)) : introCfg(a, g);
            show(BroadcastView.page(a, "reveal", cfg, this::gone), "intro");
        } catch (Exception ignored) {
            // not this time
        }
    }

    // ---- the season intro over an ad break before the game ----------------------------------------------------------

    /** The season intro is the one on (over a break). */
    private boolean seasonUp;

    /**
     * An ad break has started (the channel's logo gone): for a top-five league's game, from an hour and a half to six
     * minutes before its kick-off, the league's season intro over it, once a game. True when it is on (the break's own
     * screen waits for its end).
     */
    boolean seasonBreak(Game g) {
        if (g == null || g.id == null || g.kickoff <= 0 || !seasonOn(a) || showing() || fetching || !canShow.getAsBoolean()) return false;
        BroadcastData.Match m = match(g);
        String key = BroadcastData.seasonKey(m.compId, m.compName);
        if (key.isEmpty() || shown.contains(g.id + "|season") || !Moments.seasonBreakDue(g.kickoff - System.currentTimeMillis())) return false;
        try {
            show(BroadcastView.page(a, "reveal", BroadcastData.season(key, BroadcastData.fixture(m.homeName, m.awayName)), this::gone), "intro");
        } catch (Exception e) {
            return false;
        }
        shown.add(g.id + "|season");
        seasonUp = true;
        return true;
    }

    /** Whether the season intro is on now (the break's own screen waits). */
    boolean seasonShowing() {
        return seasonUp && showing();
    }

    /** The break is over (the channel's logo back): the season intro fades to the programme. */
    void seasonBack() {
        if (seasonShowing()) view.back();
    }

    /** The intro's settings for a game: its competition's own film (else plain football's), the two teams under it. */
    private static JSONObject introCfg(Context c, Game g) throws org.json.JSONException {
        BroadcastData.Match m = match(g);
        return cfgFor(c, BroadcastData.introKey(m.compId, m.compName), BroadcastData.fixture(m.homeName, m.awayName));
    }

    /** An intro's settings: key's film (or the family's own video, then the ending), the line under the wordmark. */
    private static JSONObject cfgFor(Context c, String key, String line) throws org.json.JSONException {
        if (IntroVideo.useOwn(c)) return BroadcastData.intro(key, line, "file://" + IntroVideo.file(c).getAbsolutePath(), IntroVideo.durationMs(c));
        return BroadcastData.intro(key, line, null, 0);
    }

    /** The channel's own sound off while an intro plays (the player's volume), back after. */
    private Consumer<Boolean> hush = (on) -> { };

    void hush(Consumer<Boolean> h) {
        hush = h == null ? (on) -> { } : h;
    }

    /** The programmes on the channel now and next: F1's, UFC's and boxing's intros four minutes before they begin. */
    void sportTick(List<OverMatches.Programme> guide) {
        if (guide == null || !introOn(a) || showing() || fetching || !canShow.getAsBoolean()) return;
        long now = System.currentTimeMillis();
        for (OverMatches.Programme p : guide) {
            String sport = Moments.sportOf(p.title);
            if (sport == null || !Moments.programmeDue(p.start, now)) continue;
            String key = sport + "|" + p.start;
            if (shown.contains(key)) continue;
            shown.add(key);
            try {
                show(BroadcastView.page(a, "reveal", cfgFor(a, sport, Moments.eventLine(p.title)), this::gone), "intro");
            } catch (Exception ignored) {
                // not this time
            }
            return;
        }
    }

    /** Tests: a sport's intro ("f1", "ufc", "boxing", "football"), now. */
    void testSport(String key, IntConsumer note) {
        if (showing() || fetching) {
            note.accept(R.string.live7_wait);
            return;
        }
        String line = "f1".equals(key) ? "Abu Dhabi Grand Prix" : "ufc".equals(key) ? "UFC main card"
                : "boxing".equals(key) ? "Tonight's main event" : "England v Spain";
        try {
            show(BroadcastView.page(a, "reveal", cfgFor(a, key, line), this::gone), "intro");
        } catch (Exception e) {
            note.accept(R.string.live7_no_game);
        }
    }

    /** Tests: the matchday intro now, with the game watched (its trophy and teams), else a recent Premier League game. */
    void testIntro(Game watching, IntConsumer note) {
        if (showing() || fetching) {
            note.accept(R.string.live7_wait);
            return;
        }
        fetching = true;
        Context app = a.getApplicationContext();
        io.execute(() -> {
            Game g = null;
            try {
                g = watching != null && watching.id != null ? watching : TestAlerts.candidates(null).get(0);
            } catch (RuntimeException ignored) {
                // the made-up one, then
            }
            if (g == null) g = TestAlerts.sample(app, null);
            JSONObject cfg = null;
            try {
                cfg = introCfg(app, g);
            } catch (Exception ignored) {
                // said below
            }
            JSONObject got = cfg;
            h.post(() -> {
                fetching = false;
                if (got == null) {
                    note.accept(R.string.live7_no_game);
                    return;
                }
                if (showing() || a.isFinishing()) {
                    note.accept(R.string.live7_wait);
                    return;
                }
                show(BroadcastView.page(a, "reveal", got, this::gone), "intro");
            });
        });
    }

    /** A goal reached the picture (1 home, 2 away): the restart's pop-up in a little while, if the half goes on. */
    void goal(Game g, int side) {
        if (g == null || g.id == null || !popupOn(a)) return;
        int[] score = {g.homeScore, g.awayScore};
        String id = g.id;
        long at = android.os.SystemClock.uptimeMillis();
        // What comes out of the scoreboard after the goal waits for this pop-up (restartWaiting), so they never meet.
        restartUntil = at + Moments.RESTART_WAIT_MS;
        h.postDelayed(new Runnable() {
            @Override public void run() {
                String ph = phases.get(id);
                if (!"h1".equals(ph) && !"h2".equals(ph)) {
                    restartUntil = 0;
                    return;
                }
                // Still in the channel's replays of it (the board away, or as the whole picture shows: LivePlay): on the
                // restart, once live play is back.
                int go = Moments.restartNow(android.os.SystemClock.uptimeMillis() - at, inReplay.getAsBoolean(), liveVerdict.getAsInt());
                if (go == 0) {
                    h.postDelayed(this, 1_000L);
                    return;
                }
                if (go < 0) {
                    restartUntil = 0;
                    return;
                }
                // A moment's grace while it is made ready to show (showing() then says so).
                restartUntil = android.os.SystemClock.uptimeMillis() + 3_000L;
                String[] last = Moments.lastGoal(g.goals, side);
                popup(g, "restart", score, Moments.scorer(last), last == null ? "" : BroadcastData.minute(last[2]));
            }
        }, com.gathin.broadcast.LivePlay.FIRST_MS);
    }

    /** Until when a goal's restart pop-up may still come (uptime; 0: none waiting). */
    private long restartUntil;

    /** Whether a goal's restart pop-up is still to come: what comes out of the scoreboard after the goal waits for it. */
    boolean restartWaiting() {
        return android.os.SystemClock.uptimeMillis() < restartUntil;
    }

    /** Whether the channel is in its replays now (the scoreboard away, BugCover): the restart's pop-up waits for live play. */
    private BooleanSupplier inReplay = () -> false;
    /** What the whole picture says after a goal (LivePlay's verdict): BLIND keeps the old timing. */
    private java.util.function.IntSupplier liveVerdict = () -> com.gathin.broadcast.LivePlay.BLIND;

    void liveVerdict(java.util.function.IntSupplier s) {
        liveVerdict = s == null ? () -> com.gathin.broadcast.LivePlay.BLIND : s;
    }

    void inReplay(BooleanSupplier s) {
        inReplay = s == null ? () -> false : s;
    }

    // ---- showing ------------------------------------------------------------------------------------------------------

    /** Pure: an ESPN colour ("ffff00", "#ff0000") as ARGB; 0 when there is none. */
    static int colour(String hex) {
        if (hex == null) return 0;
        String s = hex.trim().replace("#", "");
        if (s.length() != 6) return 0;
        try {
            return 0xFF000000 | Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static BroadcastData.Match match(Game g) {
        BroadcastData.Match m = new BroadcastData.Match();
        m.homeName = g.homeFull != null && !g.homeFull.isEmpty() ? g.homeFull : g.homeName;
        m.awayName = g.awayFull != null && !g.awayFull.isEmpty() ? g.awayFull : g.awayName;
        m.homeShort = g.home;
        m.awayShort = g.away;
        m.homeColor = colour(g.homeColor);
        m.awayColor = colour(g.awayColor);
        m.homeAlt = colour(g.homeAlt);
        m.awayAlt = colour(g.awayAlt);
        m.homeCrestUrl = g.homeLogo;
        m.awayCrestUrl = g.awayLogo;
        m.compLogoUrl = g.leagueLogo;
        m.compId = g.slug == null ? "" : g.slug;
        m.compName = g.leagueName == null ? "" : g.leagueName;
        m.venue = g.venue == null ? "" : g.venue;
        m.kickoff = g.kickoff;
        return m;
    }

    private void popup(Game g, String kind, int[] score, String scorer, String minute) {
        String key = g.id + "|" + kind + (score == null ? "" : "|" + score[0] + "-" + score[1]);
        if (shown.contains(key) || showing() || !canShow.getAsBoolean()) return;
        shown.add(key);
        BroadcastData.Match m = match(g);
        boolean strap = "strap".equals(popupStyle(a));
        String look = ScoreBoard.style(a).name();
        float[] board = boardAt.get();
        android.content.res.AssetManager assets = a.getAssets();
        io.execute(() -> {
            JSONObject cfg;
            try {
                cfg = strap ? BroadcastData.strap(kind, m, score, scorer, minute, look, board, "")
                        : BroadcastData.withMark(assets, BroadcastData.kickoff(kind, m, score, scorer, minute));
            } catch (Exception e) {
                return;
            }
            h.post(() -> {
                if (showing() || !canShow.getAsBoolean() || a.isFinishing()) return;
                show(BroadcastView.page(a, strap ? "strap" : "kickoff", cfg, this::gone), strap ? "strap" : kind);
            });
        });
    }

    private void lineups(Game g) {
        String key = g.id + "|lineups";
        long now = System.currentTimeMillis();
        Long at = tried.get(g.id);
        if (shown.contains(key) || fetching || showing() || at != null && now - at < Moments.RETRY_MS || !canShow.getAsBoolean()) return;
        fetching = true;
        tried.put(g.id, now);
        BroadcastData.Match m = match(g);
        File cache = a.getCacheDir();
        String homeName = g.homeName, awayName = g.awayName;
        io.execute(() -> {
            JSONObject cfg = null;
            try {
                int id = FotMob.find(g.id, homeName, awayName, g.kickoff);
                cfg = BroadcastData.lineups(m, FotMob.raw(id), cache);
            } catch (Exception ignored) {
                // tried again in a minute
            }
            JSONObject got = cfg;
            h.post(() -> {
                fetching = false;
                if (got == null || shown.contains(key) || showing() || !canShow.getAsBoolean() || a.isFinishing()) return;
                shown.add(key);
                String page = "stadium".equals(lineupsStyle(a)) ? "stadium" : "lineups";
                show(BroadcastView.page(a, page, got, this::gone), page);
            });
        });
    }

    private void show(BroadcastView v, String kind) {
        view = v;
        viewKind = kind;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(pw > 0 ? Math.round(pw) : -1, ph > 0 ? Math.round(ph) : -1);
        // just over the picture and the scoreboard: the player's own tile and buttons still come up over it
        root.addView(v, Math.min(5, root.getChildCount()), lp);
        v.setTranslationX(pw > 0 ? px : 0);
        v.setTranslationY(ph > 0 ? py : 0);
        if (hidesBoard(kind)) lineupsUp.accept(true);
        if ("intro".equals(kind)) hush.accept(true);
        if (moment(kind)) momentChanged.run();
    }

    /** The line-ups (either) and the strap (which draws the board itself as it folds into it): the board out of the way. */
    private static boolean hidesBoard(String kind) {
        return "lineups".equals(kind) || "stadium".equals(kind) || "strap".equals(kind) || "intro".equals(kind);
    }

    private void gone(BroadcastView v) {
        if (v != view) return;
        boolean wasLineups = hidesBoard(viewKind);
        boolean wasMoment = moment(viewKind);
        seasonUp = false;
        if ("intro".equals(viewKind)) hush.accept(false);
        view = null;
        viewKind = "";
        root.removeView(v);
        if (wasLineups) lineupsUp.accept(false);
        if (wasMoment) momentChanged.run();
    }

    /** The picture laid out again (turned, resized): the overlay kept over it. */
    void place(float x, float y, float w, float h) {
        px = x;
        py = y;
        pw = w;
        ph = h;
        if (view == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        int ww = w > 0 ? Math.round(w) : -1, hh = h > 0 ? Math.round(h) : -1;
        if (lp.width != ww || lp.height != hh) {
            lp.width = ww;
            lp.height = hh;
            view.setLayoutParams(lp);
        }
        view.setTranslationX(w > 0 ? x : 0);
        view.setTranslationY(h > 0 ? y : 0);
    }

    /** Back: the line-ups fade away if they are on (true), before anything else Back does. */
    boolean back() {
        if (!showing() || !("lineups".equals(viewKind) || "stadium".equals(viewKind) || "intro".equals(viewKind))) return false;
        view.back();
        return true;
    }

    /** Taken down at once (the player stopping, the small window). */
    void stop() {
        if (view != null) view.stop();
        restartUntil = 0;
    }

    // ---- test notifications (the player's options, PlayerTests) ------------------------------------------------------

    /**
     * Tests: the kick-off pop-up in a style ("classic", or "strap" in the scoreboard's look) at a moment ("kickoff",
     * "restart", "second"), now, whatever the style chosen: on the game watched, else a recent Premier League game (as the
     * TV's tries find one), else a made-up one. Nothing is marked shown, so the real moments still come. note: why not now.
     */
    void testPopup(String style, String kind, Game watching, IntConsumer note) {
        if (showing() || fetching) {
            note.accept(R.string.live7_wait);
            return;
        }
        fetching = true;
        boolean strap = "strap".equals(style);
        String look = ScoreBoard.style(a).name();
        float[] board = boardAt.get();
        Context app = a.getApplicationContext();
        io.execute(() -> {
            JSONObject cfg = null;
            List<Game> games = new ArrayList<>();
            try {
                games.addAll(watching != null && watching.id != null ? Collections.singletonList(watching) : TestAlerts.candidates(null));
            } catch (RuntimeException ignored) {
                // the made-up one, then
            }
            int looked = 0;
            for (Game g : games) {
                if ("restart".equals(kind) && g.goals.isEmpty() && g != watching) continue;
                if (++looked > 3) break;
                cfg = testPopupCfg(g, kind, strap, look, board);
                if (cfg != null) break;
            }
            if (cfg == null) cfg = testPopupCfg(TestAlerts.sample(app, null), kind, strap, look, board);
            JSONObject got = strap ? cfg : BroadcastData.withMark(app.getAssets(), cfg);
            h.post(() -> {
                fetching = false;
                if (got == null) {
                    note.accept(R.string.live7_no_game);
                    return;
                }
                if (showing() || a.isFinishing()) {
                    note.accept(R.string.live7_wait);
                    return;
                }
                show(BroadcastView.page(a, strap ? "strap" : "kickoff", got, this::gone), strap ? "strap" : kind);
            });
        });
    }

    /** A test pop-up's settings for game g (its crests read in); null when it can't be made. Off the main thread. */
    private static JSONObject testPopupCfg(Game g, String kind, boolean strap, String look, float[] board) {
        try {
            BroadcastData.Match m = match(g);
            boolean restart = "restart".equals(kind);
            int[] score = "kickoff".equals(kind) ? null : testScore(g, restart);
            String[] last = g.goals.isEmpty() ? null : g.goals.get(g.goals.size() - 1);
            String scorer = !restart ? null : last == null ? "A. Player" : Moments.scorer(last);
            String minute = !restart ? null : last == null ? "23'" : BroadcastData.minute(last[2]);
            return strap ? BroadcastData.strap(kind, m, score, scorer, minute, look, board, "")
                    : BroadcastData.kickoff(kind, m, score, scorer, minute);
        } catch (Exception e) {
            return null;
        }
    }

    /** Pure: the score a test pop-up shows: the game's own; 1–0 for a restart when it has no goal yet. */
    static int[] testScore(Game g, boolean restart) {
        if (restart && g.homeScore + g.awayScore == 0) return new int[]{1, 0};
        return new int[]{g.homeScore, g.awayScore};
    }

    /**
     * Tests: the line-ups in a style ("lineups", the classic beam-in, or "stadium") now, whatever the style chosen: on
     * the game watched when FotMob has its line-ups, else on a recent Premier League game's (as the TV's tries find one).
     * Back closes them, as the real ones. note: why not now.
     */
    void testLineups(String style, Game watching, IntConsumer note) {
        if (showing() || fetching) {
            note.accept(R.string.live7_wait);
            return;
        }
        fetching = true;
        note.accept(R.string.live7_looking);
        File cache = a.getCacheDir();
        io.execute(() -> {
            JSONObject cfg = null;
            try {
                cfg = TestAlerts.tryOn(watching, 5, g -> {
                    try {
                        return BroadcastData.lineups(match(g), FotMob.raw(FotMob.find(g.id, g.homeName, g.awayName, g.kickoff)), cache);
                    } catch (Exception oneGame) {
                        return null;
                    }
                });
            } catch (RuntimeException ignored) {
                // none found: said below
            }
            JSONObject got = cfg;
            h.post(() -> {
                fetching = false;
                if (got == null) {
                    note.accept(R.string.live7_no_lineups);
                    return;
                }
                if (showing() || a.isFinishing()) {
                    note.accept(R.string.live7_wait);
                    return;
                }
                String page = "stadium".equals(style) ? "stadium" : "lineups";
                show(BroadcastView.page(a, page, got, this::gone), page);
            });
        });
    }
}
