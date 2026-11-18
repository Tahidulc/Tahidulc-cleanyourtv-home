package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;

import com.gathin.broadcast.BroadcastData;
import com.gathin.broadcast.BroadcastView;
import com.gathin.broadcast.MatchIntro;
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
    /** The match intro's gathering (its clubs' squads and pictures: up to a minute), apart from the pop-ups'. */
    private final ExecutorService matchIo = Executors.newSingleThreadExecutor();
    /**
     * The match intro's settings made ahead for one game (its id), written out in each style it can have (megabytes, its
     * pictures: written on the gathering's thread, so that a break only hands them over), when, whether being made; the
     * breaks with an intro so far.
     */
    private String matchNet, matchUcl;
    private String matchFor;
    private long matchAt;
    private boolean matchMaking;
    private final Map<String, Integer> breaksSeen = new HashMap<>();
    /**
     * When each game's last intro started (any: a break's or the matchday's) and its last break's intro (wall clock):
     * never one straight after another, and no matchday intro just after a break has had one (Moments).
     */
    private final Map<String, Long> introAt = new HashMap<>(), breakIntroAt = new HashMap<>();
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

    /**
     * The pop-up's style: "strap" (in my scoreboard's look, whichever look the board is in: a new install's), "classic"
     * (crests, trophy and teams) or "off". Once, the family's classic was switched to the strap, as they asked (followBoard).
     */
    static String popupStyle(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(FOLLOWS_BOARD, false)) {
            p.edit().putString("popup_style", followBoard(p.getString("popup_style", null), p.getBoolean("popup", true)))
                    .putBoolean(FOLLOWS_BOARD, true).apply();
        }
        String s = p.getString("popup_style", null);
        return s != null ? s : "strap";
    }

    /** Set once the pop-up was switched to follow the board (Nov 2026). */
    private static final String FOLLOWS_BOARD = "popup_follows_board";

    /**
     * Pure: the pop-up's style after the one switch to the board's look: the strap for classic (and for one never chosen,
     * unless the old pop-up switch was off); off stays off; the strap stays.
     */
    static String followBoard(String style, boolean wasOn) {
        if ("off".equals(style)) return "off";
        if (style == null) return wasOn ? "strap" : "off";
        return "strap";
    }

    /** The look the strap is drawn in for g: the board's, By competition's resolved as the board resolves it for g. */
    private static String strapLook(Context c, Game g) {
        String name = g.leagueName != null && !g.leagueName.isEmpty() ? g.leagueName : g.league;
        return BroadcastData.strapLook(ScoreBoard.style(c).name(), g.slug, name);
    }

    /**
     * What the strap shows of the board as it is drawn (io thread): the competition's logos the board draws (for dark
     * grounds and for light ones), each side's colour on it and the name it gives the club; and its lettering for the 3D kits.
     */
    private static JSONObject strapCfg(Context c, Game g, BroadcastData.Match m, String kind, int[] score, String scorer, String minute,
                                       float[] board) throws org.json.JSONException {
        m.homeBoard = OverlayBug.colour(g.homeColor, g.homeAlt);
        m.awayBoard = OverlayBug.colour(g.awayColor, g.awayAlt);
        m.homeBoardName = g.homeName == null ? "" : g.homeName;
        m.awayBoardName = g.awayName == null ? "" : g.awayName;
        int px = Math.round(28 * c.getResources().getDisplayMetrics().density * 2);
        String dark = com.gathin.tvhomephone.sports.Competitions.logo(g), light = com.gathin.tvhomephone.sports.Competitions.lightLogo(g.slug);
        m.compLogo = dark == null ? null : com.gathin.tvhomephone.sports.LiveGames.picture(c, dark, px);
        m.compLight = light == null ? null : com.gathin.tvhomephone.sports.LiveGames.picture(c, light, px);
        JSONObject o = BroadcastData.strap(kind, m, score, scorer, minute, strapLook(c, g), board, "");
        o.put("boardFont", "sans");
        return o;
    }

    static void setPopupStyle(Context c, String style) {
        prefs(c).edit().putString("popup_style", style).apply();
    }

    /**
     * The line-ups' style: "classic" (the beam-in), "stadium" (each eleven on a board over a ground lit for the day), "studio"
     * (each team beside a panel in its competition's colours, its kit turning in 3D, a page a position on the beat) or "off".
     */
    static String lineupsStyle(Context c) {
        String s = prefs(c).getString("lineups_style", null);
        return s != null ? s : prefs(c).getBoolean("lineups", true) ? "classic" : "off";
    }

    static void setLineupsStyle(Context c, String style) {
        prefs(c).edit().putString("lineups_style", style).apply();
    }

    /**
     * The intros (reveal.html) over the ad breaks before kick-off (to eight minutes before it), else twelve minutes before
     * it, and as F1, UFC and boxing begin: on unless off.
     */
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

    /**
     * The match intro (overlays/src/match): the game itself, its clubs and their best-known players in a big network's
     * style (a Champions League game's in its own too), Whitebrook Road Sports to end: over the ad breaks before the
     * game in turn with the competition's own, and as its matchday intro. On unless off.
     */
    static boolean matchOn(Context c) {
        return prefs(c).getBoolean("match", true);
    }

    static void setMatch(Context c, boolean on) {
        prefs(c).edit().putBoolean("match", on).apply();
    }

    /** Whether any intro the breaks before a game can have is on: the match intro, a league's season intro, the competition's film. */
    static boolean breaksOn(Context c) {
        return matchOn(c) || seasonOn(c) || introOn(c);
    }

    /**
     * Whether the channel's logo should be watched now for the intros over g's ad breaks (ScoreOverlay), the ad-break
     * screen on or off: one of them is on and the kick-off is from two hours to seven minutes off (Moments.breaksWatch).
     */
    boolean breaksWatch(Game g, long now) {
        return g != null && g.id != null && g.kickoff > 0 && breaksOn(a) && Moments.breaksWatch(g.kickoff - now);
    }

    private static long at(Map<String, Long> m, String id) {
        Long t = m.get(id);
        return t == null ? 0 : t;
    }

    /** Whether g's matchday intro may come now: no break's intro lately, none started just before (Moments). */
    private boolean matchdayWanted(String id, long now) {
        return Moments.matchdayWanted(at(breakIntroAt, id), now) && Moments.introSpaced(at(introAt, id), now);
    }

    /** The match intro's settings for g made (again) out of sight, ready for its breaks (main thread). */
    private void warmMatch(Game g) {
        if (g == null || g.id == null || matchMaking) return;
        if (g.id.equals(matchFor) && System.currentTimeMillis() - matchAt < Moments.MATCH_REMAKE_MS) return;
        matchMaking = true;
        BroadcastData.Match m = match(g);
        Context app = a.getApplicationContext();
        boolean uclGame = ucl(g);
        matchIo.execute(() -> {
            String net = null, uc = null;
            try {
                int id = FotMob.find(g.id, m.homeName, m.awayName, g.kickoff);
                JSONObject cfg = MatchIntro.build(app.getAssets(), m, id > 0 ? FotMob.raw(id) : null, app.getCacheDir(), BroadcastData.fixture(m.homeName, m.awayName));
                net = cfg.put("style", "network").toString();
                if (uclGame) uc = cfg.put("style", "ucl").toString();
            } catch (Exception ignored) {
                // tried again in a while
            }
            String gotNet = net, gotUcl = uc;
            h.post(() -> {
                matchMaking = false;
                if (gotNet == null) return;
                matchNet = gotNet;
                matchUcl = gotUcl;
                matchFor = g.id;
                matchAt = System.currentTimeMillis();
            });
        });
    }

    /** The match intro's settings for g in a style ("ucl" or "network"), written out, or null when not made for it. */
    private String matchJson(Game g, String style) {
        if (g == null || g.id == null || !g.id.equals(matchFor)) return null;
        return "ucl".equals(style) ? matchUcl : matchNet;
    }

    private static boolean ucl(Game g) {
        BroadcastData.Match m = match(g);
        return "ucl".equals(BroadcastData.introTrophy(m.compId, m.compName));
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
        if ("pre".equals(now) && g.kickoff > 0 && matchOn(a) && Moments.matchWarm(g.kickoff - screenNow)) warmMatch(g);
        if ("pre".equals(now) && g.kickoff > 0 && introOn(a) && Moments.introDue(g.kickoff - screenNow)
                && matchdayWanted(g.id, System.currentTimeMillis())) intro(g);
    }

    /**
     * The matchday intro over the picture, once a game, in the style made for its competition: a top-five league's season
     * intro (its clubs one by one), else the competition's film, then the trophy and the Matchday card.
     */
    private void intro(Game g) {
        String key = g.id + "|intro";
        if (shown.contains(key) || showing() || fetching || !canShow.getAsBoolean()) return;
        shown.add(key);
        try {
            BroadcastData.Match m = match(g);
            String mc = matchOn(a) ? matchJson(g, ucl(g) ? "ucl" : "network") : null;
            introAt.put(g.id, System.currentTimeMillis());
            if (mc != null) {
                show(BroadcastView.page(a, "match", mc, this::gone), "intro");
                return;
            }
            String sk = BroadcastData.seasonKey(m.compId, m.compName);
            JSONObject cfg = seasonOn(a) && !sk.isEmpty() && !IntroVideo.useOwn(a)
                    ? BroadcastData.season(sk, BroadcastData.fixture(m.homeName, m.awayName)) : introCfg(a, g);
            show(BroadcastView.page(a, "reveal", cfg, this::gone), "intro");
        } catch (Exception ignored) {
            // not this time
        }
    }

    // ---- the intros over the ad breaks before the game ----------------------------------------------------------------

    /** The intros on are a break's (its end fades them); this break has had them (once a break: the next brings them again). */
    private boolean breakUp, breakDone;

    /** Told when an intro comes over the picture: the ad-break screen goes for it (never on the picture with it). */
    private Runnable introUp = () -> { };

    void introUp(Runnable r) {
        introUp = r == null ? () -> { } : r;
    }

    /** Whether an intro (a break's or the matchday's) is over the picture now: the break's own screen waits. */
    boolean introShowing() {
        return showing() && "intro".equals(viewKind);
    }

    /**
     * An ad break has started (the channel's logo gone), from an hour and a half to eight minutes before the
     * game's kick-off: an intro over it, every break, in the style made for its competition (a top-five league's season
     * intro, else the competition's own film). True when it is on.
     */
    boolean breakIntros(Game g) {
        if (breakDone || g == null || g.id == null || g.kickoff <= 0 || showing() || fetching || !canShow.getAsBoolean()) return false;
        long t = System.currentTimeMillis();
        if (!Moments.breakIntrosDue(g.kickoff - t) || !Moments.introSpaced(at(introAt, g.id), t)) return false;
        BroadcastData.Match m = match(g);
        String sk = seasonOn(a) ? BroadcastData.seasonKey(m.compId, m.compName) : "";
        String ck = introOn(a) ? BroadcastData.introKey(m.compId, m.compName) : "";
        Integer seen = breaksSeen.get(g.id);
        int n = seen == null ? 0 : seen;
        String style = Moments.breakStyle(n, matchOn(a) && matchJson(g, "network") != null, !sk.isEmpty() || !ck.isEmpty(), ucl(g));
        if (style.isEmpty()) return false;
        try {
            if ("other".equals(style)) show(BroadcastView.page(a, "reveal", BroadcastData.breakIntros(sk, ck, BroadcastData.fixture(m.homeName, m.awayName)), this::gone), "intro");
            else show(BroadcastView.page(a, "match", matchJson(g, style), this::gone), "intro");
        } catch (Exception e) {
            return false;
        }
        breaksSeen.put(g.id, n + 1);
        introAt.put(g.id, t);
        breakIntroAt.put(g.id, t);
        shown.add(g.id + "|break");
        breakUp = true;
        breakDone = true;
        return true;
    }

    /** The break is over (the channel's logo back): its intros fade to the programme; the next break gets them again. */
    void breakOver() {
        breakDone = false;
        if (breakUp && showing()) view.back();
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

    /** The channel's own sound lowered under the studio line-ups' music (the player's volume), back after. */
    private Consumer<Boolean> duck = (on) -> { };

    void duck(Consumer<Boolean> d) {
        duck = d == null ? (on) -> { } : d;
    }

    /** Pure: the page for a line-ups style ("lineups" the classic's). */
    static String lineupsPage(String style) {
        return "stadium".equals(style) ? "stadium" : "studio".equals(style) ? "studio" : "lineups";
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
        restartGame = id;
        restartSide = side;
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
    /** The game and the side of the goal whose restart pop-up may still come. */
    private String restartGame;
    private int restartSide;
    /**
     * The goals whose restart pop-up came up (Moments.restartKey: the game, the side, that side's goals with it): their
     * scorer beside the scoreboard is left out, the pop-up said it.
     */
    private final Set<String> restartsUp = new HashSet<>();

    /** Whether the restart pop-up came up for the side's n-th goal of the game (1 home, 2 away). */
    boolean restartShowed(String gameId, int side, int n) {
        return gameId != null && n > 0 && restartsUp.contains(Moments.restartKey(gameId, side, n));
    }

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
        m.homeId = g.homeId == null ? "" : g.homeId;
        m.awayId = g.awayId == null ? "" : g.awayId;
        com.gathin.broadcast.Worn.Kit[] kits = com.gathin.broadcast.Worn.get(g.id);
        m.homeKit = kits == null || kits[0] == null ? "" : kits[0].type;
        m.awayKit = kits == null || kits[1] == null ? "" : kits[1].type;
        return m;
    }

    private void popup(Game g, String kind, int[] score, String scorer, String minute) {
        String key = g.id + "|" + kind + (score == null ? "" : "|" + score[0] + "-" + score[1]);
        if (shown.contains(key) || showing() || !canShow.getAsBoolean()) return;
        shown.add(key);
        BroadcastData.Match m = match(g);
        boolean strap = "strap".equals(popupStyle(a));
        float[] board = boardAt.get();
        android.content.res.AssetManager assets = a.getAssets();
        Context app = a.getApplicationContext();
        io.execute(() -> {
            JSONObject cfg;
            try {
                cfg = strap ? strapCfg(app, g, m, kind, score, scorer, minute, board)
                        : BroadcastData.withMark(assets, BroadcastData.kickoff(kind, m, score, scorer, minute));
            } catch (Exception e) {
                return;
            }
            h.post(() -> {
                if (showing() || !canShow.getAsBoolean() || a.isFinishing()) return;
                show(BroadcastView.page(a, strap ? "strap" : "kickoff", cfg, this::gone), strap ? "strap" : kind);
                // A goal's: its scorer beside the scoreboard is left out (ScoreBoard), the pop-up said it.
                if ("restart".equals(kind) && g.id != null && g.id.equals(restartGame)) {
                    String k = Moments.restartKey(g.id, restartSide, score);
                    if (k != null) {
                        if (restartsUp.size() > 200) restartsUp.clear();
                        restartsUp.add(k);
                    }
                }
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
        String style = lineupsStyle(a);
        io.execute(() -> {
            JSONObject cfg = null;
            try {
                int id = FotMob.find(g.id, homeName, awayName, g.kickoff);
                cfg = BroadcastData.lineups(m, FotMob.raw(id), cache);
                if ("studio".equals(style)) BroadcastData.studio(cfg, m);
            } catch (Exception ignored) {
                // tried again in a minute
            }
            JSONObject got = cfg;
            h.post(() -> {
                fetching = false;
                if (got == null || shown.contains(key) || showing() || !canShow.getAsBoolean() || a.isFinishing()) return;
                shown.add(key);
                String page = lineupsPage(style);
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
        if ("intro".equals(kind)) {
            hush.accept(true);
            introUp.run();
        }
        if ("studio".equals(kind)) duck.accept(true);
        if (moment(kind)) momentChanged.run();
    }

    /** The line-ups (either) and the strap (which draws the board itself as it folds into it): the board out of the way. */
    private static boolean hidesBoard(String kind) {
        return "lineups".equals(kind) || "stadium".equals(kind) || "studio".equals(kind) || "strap".equals(kind) || "intro".equals(kind);
    }

    private void gone(BroadcastView v) {
        if (v != view) return;
        boolean wasLineups = hidesBoard(viewKind);
        boolean wasMoment = moment(viewKind);
        breakUp = false;
        if ("intro".equals(viewKind)) hush.accept(false);
        if ("studio".equals(viewKind)) duck.accept(false);
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
        if (!showing() || !("lineups".equals(viewKind) || "stadium".equals(viewKind) || "studio".equals(viewKind) || "intro".equals(viewKind))) return false;
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
                cfg = testPopupCfg(app, g, kind, strap, board);
                if (cfg != null) break;
            }
            if (cfg == null) cfg = testPopupCfg(app, TestAlerts.sample(app, null), kind, strap, board);
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
    private static JSONObject testPopupCfg(Context c, Game g, String kind, boolean strap, float[] board) {
        try {
            BroadcastData.Match m = match(g);
            boolean restart = "restart".equals(kind);
            int[] score = "kickoff".equals(kind) ? null : testScore(g, restart);
            String[] last = g.goals.isEmpty() ? null : g.goals.get(g.goals.size() - 1);
            String scorer = !restart ? null : last == null ? "A. Player" : Moments.scorer(last);
            String minute = !restart ? null : last == null ? "23'" : BroadcastData.minute(last[2]);
            return strap ? strapCfg(c, g, m, kind, score, scorer, minute, board)
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
     * Tests: the line-ups in a style ("lineups", the classic beam-in, "stadium" or "studio") now, whatever the style chosen: on
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
                        BroadcastData.Match m = match(g);
                        JSONObject o = BroadcastData.lineups(m, FotMob.raw(FotMob.find(g.id, g.homeName, g.awayName, g.kickoff)), cache);
                        return "studio".equals(style) ? BroadcastData.studio(o, m) : o;
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
                String page = "stadium".equals(style) || "studio".equals(style) ? style : "lineups";
                show(BroadcastView.page(a, page, got, this::gone), page);
            });
        });
    }
}
