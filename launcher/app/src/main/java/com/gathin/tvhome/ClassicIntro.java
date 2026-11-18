package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.gathin.broadcast.BroadcastData;
import com.gathin.broadcast.BroadcastView;
import com.gathin.broadcast.MatchIntro;
import com.gathin.broadcast.Moments;

import org.json.JSONObject;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The classic kick-off pop-up and line-ups over the channel, as the user's clips have them (overlays/: BroadcastView),
 * while the scoreboard is on for a game (BoardCover), each with its switch on the Scoreboard card:
 * <ul>
 * <li>the line-ups once, from seven minutes before kick-off on the screen (when FotMob has them), both teams in about a
 * minute and a half, the scoreboard out of the way meanwhile; Back closes them, straight back to the channel;</li>
 * <li>the pop-up as the kick-off reaches the screen, when play starts again after a goal (40 seconds after the goal shows,
 * with the score, scorer and minute) and at the second half's kick-off (with the score).</li>
 * </ul>
 * Each moment is shown once. Nothing shows over Home, another app, the screensaver or another of the launcher's frames,
 * except a try from the Scoreboard card (tryIt), which shows over whatever is in front.
 */
final class ClassicIntro {
    private ClassicIntro() { }

    private static final String FILE = "classic_intro";

    private static Handler main;
    private static FrameLayout win;
    private static BroadcastView view;
    private static String viewKind = "";
    /** The moments shown, "game|kind" (and the score for a restart): each once. */
    private static final Set<String> shown = new HashSet<>();
    /** Each game's phase on the board as last seen: pre, h1, ht, h2, ft. */
    private static final Map<String, String> phases = new HashMap<>();
    private static final Map<String, Long> lineupsTried = new HashMap<>();
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    /** The match intro's gathering (its clubs' squads and pictures: up to a minute), apart from the pop-ups' own. */
    private static final ExecutorService matchIo = Executors.newSingleThreadExecutor();
    /**
     * The match intro's settings made ahead for one game (its id), written out in each style it can have (megabytes, its
     * pictures: written on the gathering's thread, so that a break only hands them over), when, and whether being made.
     */
    private static String matchNet, matchUcl;
    private static String matchFor;
    private static long matchAt;
    private static boolean matchMaking;
    /** Each game's ad breaks so far that had an intro (the styles take turns). */
    private static final Map<String, Integer> breaksSeen = new HashMap<>();
    /**
     * When each game's last intro started (any: a break's or the matchday's) and its last break's intro (wall clock):
     * never one straight after another, and no matchday intro just after a break has had one (Moments).
     */
    private static final Map<String, Long> introAt = new HashMap<>(), breakIntroAt = new HashMap<>();
    private static boolean fetching;
    /** A try from the Scoreboard card or adb is on: over whatever is in front, not taken down with the board. */
    private static boolean trial;
    /** When each game's phase last changed (uptime): half time's length, for the second half's pop-up made ready. */
    private static final Map<String, Long> phaseSince = new HashMap<>();
    /**
     * A pop-up made ready before its moment (BroadcastView.prepare), out of sight in an unseen window (alpha 0), so that it
     * is on the screen the moment comes (it took the TCL four to twelve seconds to get one ready): the kick-off's from
     * two and a half minutes before, a goal's restart 25 seconds after the goal shows, the second half's from ten
     * minutes into half time. One at a time, kept a quarter of an hour at most.
     */
    private static BroadcastView prep;
    private static FrameLayout prepWin;
    private static String prepKey, prepKind = "";
    private static long prepAt;
    private static boolean preparing;
    private static final long PREP_KICKOFF_MS = 150_000L, PREP_RESTART_MS = 12_000L, PREP_SECOND_MS = 10 * 60_000L, PREP_KEEP_MS = 15 * 60_000L;

    private static Handler handler() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /**
     * The kick-off pop-up's style (and the restart's and the second half's): "strap" (in my scoreboard's look, whichever
     * look the board is in, folding into it: a new install's), "classic" (from the family's clip: crests, trophy and teams)
     * or "off". Once, the family's classic was switched to the strap, as they asked (followBoard).
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

    /** Pure: the pop-up's style after the card's OK: in the scoreboard's look, then classic, then off. */
    static String nextPopup(String style) {
        return "strap".equals(style) ? "classic" : "classic".equals(style) ? "off" : "strap";
    }

    static void setPopupStyle(Context c, String style) {
        prefs(c).edit().putString("popup_style", style).apply();
    }

    /**
     * The line-ups' style before kick-off: "classic" (the beam-in, from the reel), "stadium" (the first design), "studio"
     * (each team beside a panel in its competition's colours, its kit turning in 3D, a page a position on the beat) or "off".
     */
    static String lineupsStyle(Context c) {
        String s = prefs(c).getString("lineups_style", null);
        return s != null ? s : prefs(c).getBoolean("lineups", true) ? "classic" : "off";
    }

    static void setLineupsStyle(Context c, String style) {
        prefs(c).edit().putString("lineups_style", style).apply();
    }

    /** Pure: the line-ups' next style on the card's OK: classic, the stadium board, the studio, then off. */
    static String nextLineups(String style) {
        switch (style == null ? "" : style) {
            case "classic": return "stadium";
            case "stadium": return "studio";
            case "studio": return "off";
            default: return "classic";
        }
    }

    /** Pure: the page for a line-ups style ("lineups" the classic's). */
    static String lineupsPage(String style) {
        return "stadium".equals(style) ? "stadium" : "studio".equals(style) ? "studio" : "lineups";
    }

    /**
     * The intros (reveal.html: the competition's own film, then Whitebrook Road Sports) over the advert breaks before
     * kick-off (to eight minutes before it), else twelve minutes before it, and as F1, UFC and boxing begin on the channel
     * watched: on or off.
     */
    static boolean introOn(Context c) {
        return prefs(c).getBoolean("intro", true);
    }

    static void setIntro(Context c, boolean on) {
        prefs(c).edit().putBoolean("intro", on).apply();
    }

    /**
     * The season intros (the top five leagues: every club in a scene of its own, then the Whitebrook Road Sports ending)
     * over an advert break before the game, else as its matchday intro: on unless off.
     */
    static boolean seasonOn(Context c) {
        return prefs(c).getBoolean("season", true);
    }

    static void setSeason(Context c, boolean on) {
        prefs(c).edit().putBoolean("season", on).apply();
    }

    /**
     * The match intro (overlays/src/match): an open in a big network's style for the game itself, its two clubs and their
     * best-known players, the competition's other clubs, Whitebrook Road Sports to end; in a Champions League game its
     * own style in turn. Over the breaks before the game (in turn with the competition's own) and as its matchday intro.
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
     * Whether the channel's logo should be watched now for the intros over g's advert breaks (AdBreak), the ad-break
     * screen on or off: one of them is on and the kick-off is from two hours to seven minutes off (Moments.breaksWatch).
     */
    static boolean breaksWatch(Context c, ScoresTicker.Game g, long now) {
        return g != null && g.id != null && g.kickoff > 0 && breaksOn(c) && Moments.breaksWatch(g.kickoff - now);
    }

    private static long at(Map<String, Long> m, String id) {
        Long t = m.get(id);
        return t == null ? 0 : t;
    }

    /** Whether g's matchday intro may come now: no break's intro lately, none started just before (Moments). */
    private static boolean matchdayWanted(String id, long now) {
        return Moments.matchdayWanted(at(breakIntroAt, id), now) && Moments.introSpaced(at(introAt, id), now);
    }

    /** The match intro's settings for g made (again) out of sight, ready for its breaks (main thread). */
    private static void warmMatch(Context c, ScoresTicker.Game g) {
        if (g == null || g.id == null || matchMaking) return;
        long now = System.currentTimeMillis();
        if (g.id.equals(matchFor) && now - matchAt < Moments.MATCH_REMAKE_MS) return;
        matchMaking = true;
        Context app = c.getApplicationContext();
        BroadcastData.Match m = match(g);
        String fixture = fixtureOf(g);
        boolean uclGame = ucl(g);
        matchIo.execute(() -> {
            String net = null, uc = null;
            int n = 0;
            long t0 = android.os.SystemClock.uptimeMillis();
            try {
                int id = FotMob.find(g.id, g.homeName, g.awayName, g.kickoff);
                JSONObject cfg = MatchIntro.build(app.getAssets(), m, id > 0 ? FotMob.raw(id) : null, app.getCacheDir(), fixture);
                n = players(cfg);
                net = cfg.put("style", "network").toString();
                if (uclGame) uc = cfg.put("style", "ucl").toString();
            } catch (Exception e) {
                android.util.Log.w("Sport", "match intro not made: " + e);
            }
            String gotNet = net, gotUcl = uc;
            int got = n;
            long ms = android.os.SystemClock.uptimeMillis() - t0;
            handler().post(() -> {
                matchMaking = false;
                if (gotNet == null) return;
                matchNet = gotNet;
                matchUcl = gotUcl;
                matchFor = g.id;
                matchAt = System.currentTimeMillis();
                android.util.Log.i("Sport", "match intro made for " + g.id + " in " + ms + " ms: " + got + " players, "
                        + (gotNet.length() / 1024) + " KB");
            });
        });
    }

    private static int players(JSONObject cfg) {
        JSONObject h = cfg.optJSONObject("home"), a = cfg.optJSONObject("away");
        return (h == null || h.optJSONArray("players") == null ? 0 : h.optJSONArray("players").length())
                + (a == null || a.optJSONArray("players") == null ? 0 : a.optJSONArray("players").length());
    }

    /** The match intro's settings for g in a style ("ucl" or "network"), written out, or null when not made for it. */
    private static String matchJson(ScoresTicker.Game g, String style) {
        if (g == null || g.id == null || !g.id.equals(matchFor)) return null;
        return "ucl".equals(style) ? matchUcl : matchNet;
    }

    /** Pure: whether a game is the Champions League's (its own style of the match intro). */
    static boolean ucl(ScoresTicker.Game g) {
        return g != null && "ucl".equals(BroadcastData.introTrophy(g.slug, g.league));
    }

    static boolean popupOn(Context c) {
        return !"off".equals(popupStyle(c));
    }

    static boolean lineupsOn(Context c) {
        return !"off".equals(lineupsStyle(c));
    }

    /** The page for a pop-up in this TV's style. */
    private static String popupPage(Context c) {
        return "strap".equals(popupStyle(c)) ? "strap" : "kickoff";
    }

    /**
     * A pop-up's settings for its page (io thread): the classic's, or the strap's with the board's look, where the board
     * is and the ground's weather now.
     */
    private static JSONObject popupCfg(Context c, String page, ScoresTicker.Game g, BroadcastData.Match m, String kind, int[] score,
                                       String scorer, String minute) throws org.json.JSONException {
        if (!"strap".equals(page)) return BroadcastData.withMark(c.getAssets(), BroadcastData.kickoff(kind, m, score, scorer, minute));
        // the board as it is drawn: the competition's logos it draws (for dark grounds and its own colours), each side's
        // colour on it and the name it gives the club; By competition's look as the board resolves it for the game
        String league = g.leagueLogo != null ? g.leagueLogo : GoalWatchService.leagueLogoFor(g);
        m.compLogo = BoardCover.compLogo(c, league);
        m.compLight = BoardCover.compLogoLight(c, league);
        m.homeBoard = BoardBug.colour(g.homeColor, g.homeAlt);
        m.awayBoard = BoardBug.colour(g.awayColor, g.awayAlt);
        m.homeBoardName = g.homeName == null ? "" : g.homeName;
        m.awayBoardName = g.awayName == null ? "" : g.awayName;
        return BroadcastData.strap(kind, m, score, scorer, minute, BroadcastData.strapLook(BoardCover.style(c).name(), g.slug, g.league),
                BoardCover.boardRect1920(), "kickoff".equals(kind) ? MatchExtras.weatherNow(c, g) : "");
    }

    /** Whether one of them is on the screen now. */
    static boolean showing() {
        return view != null && !view.isOver();
    }

    /**
     * The moments' pop-ups in the classic look: the kick-off, play starting again after a goal, the second half. The board
     * is away while one is on (never on the screen with it) and comes back in once it has gone, with its swish (BoardCover).
     * The strap is the board's own look and folds into it, so it is not one of them.
     */
    static boolean moment(String kind) {
        return com.gathin.broadcast.Moments.boardMoment(kind);
    }

    /** Whether a moment's pop-up is on the screen now: the board stays away meanwhile. */
    static boolean momentShowing() {
        return showing() && moment(viewKind);
    }

    /** Until when a goal's restart pop-up may still come (uptime; 0: none waiting). */
    private static long restartUntil;
    /** The game and the side of the goal whose restart pop-up may still come. */
    private static String restartGame;
    private static int restartSide;
    /**
     * The goals whose restart pop-up came up (Moments.restartKey: the game, the side, that side's goals with it): their
     * card over the app (GoalWatchService) and their scorer beside the board (BoardCover) are left out, the pop-up said it.
     */
    private static final Set<String> restartsUp = new HashSet<>();

    /** Whether the restart pop-up came up for the side's n-th goal of the game (1 home, 2 away). */
    static boolean restartShowed(String gameId, int side, int n) {
        return gameId != null && n > 0 && restartsUp.contains(Moments.restartKey(gameId, side, n));
    }

    /** Whether a goal's restart pop-up may still come in this game (its card waits to see). */
    static boolean restartPending(String gameId) {
        return gameId != null && gameId.equals(restartGame) && restartWaiting();
    }

    /** The restart pop-up up for the game at this score (the goal's side as it was scored). */
    private static void restartUp(String gameId, int[] score) {
        if (gameId == null || !gameId.equals(restartGame)) return;
        String k = Moments.restartKey(gameId, restartSide, score);
        if (k == null) return;
        if (restartsUp.size() > 200) restartsUp.clear();
        restartsUp.add(k);
    }

    /** Whether a goal's restart pop-up is still to come: what comes out of the board after the goal waits for it. */
    static boolean restartWaiting() {
        return android.os.SystemClock.uptimeMillis() < restartUntil;
    }

    static boolean showingLineups() {
        return showing() && ("lineups".equals(viewKind) || "stadium".equals(viewKind) || "studio".equals(viewKind));
    }

    /** Whether the overlay on now covers the board and takes Back: the line-ups and the intro. */
    private static boolean covers(String kind) {
        return "lineups".equals(kind) || "stadium".equals(kind) || "studio".equals(kind) || "intro".equals(kind);
    }

    // ---- when (com.gathin.broadcast.Moments: the same on the phone) ---------------------------------------------

    /**
     * From the board each time it draws the game (main thread): its phase moved on (the kick-off, the second half's), or
     * the line-ups due. screenNow: the time the channel's picture has reached; period: the half the scores give for it (0
     * not known), so a game joined part way through still has its goals' restart pop-ups.
     */
    static void tick(Context c, ScoresTicker.Game g, boolean pre, int state, boolean halftime, String score, long screenNow, int period) {
        if (g == null || g.id == null) return;
        if (showing() && !BoardCover.playerUp(c)) hide();
        String was = phases.get(g.id);
        String now = Moments.phase(pre, state, halftime, was, period);
        long up = android.os.SystemClock.uptimeMillis();
        if (!now.isEmpty()) {
            if (!now.equals(was)) phaseSince.put(g.id, up);
            phases.put(g.id, now);
        }
        if (prep != null && up - prepAt > PREP_KEEP_MS) dropPrepared();
        String kind = was == null ? null : Moments.moment(was, now);
        if (kind != null && popupOn(c)) popup(c, g, kind, "kickoff".equals(kind) ? null : BroadcastData.score(score), null, null);
        if ("pre".equals(now) && g.kickoff > 0 && lineupsOn(c) && Moments.lineupsDue(lineupsStyle(c), g.kickoff - screenNow)) lineups(c, g);
        // the match intro's clubs and players gathered ahead of the breaks before the game
        if ("pre".equals(now) && g.kickoff > 0 && matchOn(c) && Moments.matchWarm(g.kickoff - screenNow)) warmMatch(c, g);
        // the matchday intro twelve minutes before, where no break has brought one lately: made ready a little before,
        // then on the whole screen
        if ("pre".equals(now) && g.kickoff > 0 && introOn(c)) {
            long until = g.kickoff - screenNow;
            String key = g.id + "|intro";
            if (!matchdayWanted(g.id, System.currentTimeMillis())) {
                if (key.equals(prepKey)) dropPrepared();
            } else if (Moments.introDue(until)) intro(c, g, key);
            else if (Moments.introPrep(until) && !showing() && !fetching && !preparing && BoardCover.playerUp(c)
                    && !key.equals(prepKey) && !shown.contains(key)) holdIntro(c, g, key, null);
        }
        // made ready before their moments, while nothing else is on
        if (!popupOn(c) || showing() || fetching || !BoardCover.playerUp(c)) return;
        long untilKo = g.kickoff - screenNow;
        if ("pre".equals(now) && g.kickoff > 0 && untilKo <= PREP_KICKOFF_MS && untilKo > -10 * 60_000L) {
            prepare(c, g, "kickoff", null, null, null, g.id + "|kickoff", null, null);
        }
        Long since = phaseSince.get(g.id);
        if ("ht".equals(now) && since != null && up - since >= PREP_SECOND_MS) {
            int[] sc = BroadcastData.score(score);
            if (sc != null) prepare(c, g, "second", sc, null, null, g.id + "|second|" + sc[0] + "-" + sc[1], null, null);
        }
    }

    /** A goal reached the screen (the board's moment): the restart's pop-up in a little while, if the half goes on. */
    static void goal(Context c, ScoresTicker.Game g, String score, int side) {
        if (g == null || !popupOn(c)) return;
        String id = g.id;
        Context app = c.getApplicationContext();
        handler().postDelayed(() -> {
            String ph = phases.get(id);
            if (!"h1".equals(ph) && !"h2".equals(ph) || showing() || !BoardCover.playerUp(app)) return;
            ScoresTicker.Game now = GoalWatchService.latestGame(id);
            ScoresTicker.Game game = now != null ? now : g;
            String[] last = Moments.lastGoal(game.goals, side);
            int[] sc = BroadcastData.score(score);
            if (sc == null) return;
            prepare(app, game, "restart", sc, Moments.scorer(last), last == null ? "" : BroadcastData.minute(last[2]),
                    id + "|restart|" + sc[0] + "-" + sc[1], null, null);
        }, PREP_RESTART_MS);
        long at = android.os.SystemClock.uptimeMillis();
        // What comes out of the board after the goal waits for this pop-up (restartWaiting), so they never meet.
        restartUntil = at + Moments.RESTART_WAIT_MS;
        restartGame = id;
        restartSide = side;
        handler().postDelayed(new Runnable() {
            @Override public void run() {
                String ph = phases.get(id);
                if (!"h1".equals(ph) && !"h2".equals(ph)) {   // half time or full time came first
                    restartUntil = 0;
                    return;
                }
                // Still in the channel's replays of it (the board away, BugCover; or as the whole picture shows: LivePlay):
                // on the restart, once live play is back.
                long t = android.os.SystemClock.uptimeMillis();
                int go = Moments.restartNow(t - at, BugCover.away(), BugCover.PLAY.verdict(t));
                if (go == 0) {
                    handler().postDelayed(this, 1_000L);
                    return;
                }
                if (go < 0) {
                    restartUntil = 0;
                    AlertLog.note(app, "restart pop-up let go: the channel's replays ran on");
                    return;
                }
                // A moment's grace while it is made ready to show (showing() then says so).
                restartUntil = android.os.SystemClock.uptimeMillis() + 3_000L;
                ScoresTicker.Game now = GoalWatchService.latestGame(id);
                ScoresTicker.Game game = now != null ? now : g;
                String[] last = Moments.lastGoal(game.goals, side);
                popup(c, game, "restart", BroadcastData.score(score), Moments.scorer(last), last == null ? "" : BroadcastData.minute(last[2]));
            }
        }, com.gathin.broadcast.LivePlay.FIRST_MS);
    }

    // ---- showing ----------------------------------------------------------------------------------------------------

    private static BroadcastData.Match match(ScoresTicker.Game g) {
        BroadcastData.Match m = new BroadcastData.Match();
        m.homeName = g.homeName != null ? g.homeName : g.home;
        m.awayName = g.awayName != null ? g.awayName : g.away;
        m.homeShort = g.home;
        m.awayShort = g.away;
        m.homeColor = g.homeColor == 0 ? 0 : 0xFF000000 | g.homeColor;
        m.awayColor = g.awayColor == 0 ? 0 : 0xFF000000 | g.awayColor;
        m.homeAlt = g.homeAlt == 0 ? 0 : 0xFF000000 | g.homeAlt;
        m.awayAlt = g.awayAlt == 0 ? 0 : 0xFF000000 | g.awayAlt;
        m.homeCrestUrl = g.homeLogo;
        m.awayCrestUrl = g.awayLogo;
        m.compLogoUrl = g.leagueLogo;
        m.compId = g.slug == null ? "" : g.slug;
        m.compName = g.league == null ? "" : g.league;
        m.venue = g.venue == null ? "" : g.venue;
        m.kickoff = g.kickoff;
        m.homeId = g.homeId == null ? "" : g.homeId;
        m.awayId = g.awayId == null ? "" : g.awayId;
        com.gathin.broadcast.Worn.Kit[] kits = com.gathin.broadcast.Worn.get(g.id);
        m.homeKit = kits == null || kits[0] == null ? "" : kits[0].type;
        m.awayKit = kits == null || kits[1] == null ? "" : kits[1].type;
        return m;
    }

    private static void popup(Context c, ScoresTicker.Game g, String kind, int[] score, String scorer, String minute) {
        String key = g.id + "|" + kind + (score == null ? "" : "|" + score[0] + "-" + score[1]);
        if (shown.contains(key) || showing() || !BoardCover.playerUp(c)) return;
        boolean restart = "restart".equals(kind);
        if (goPrepared(key)) {
            if (restart) restartUp(g.id, score);
            return;
        }
        shown.add(key);
        BroadcastData.Match m = match(g);
        Context app = c.getApplicationContext();
        String page = popupPage(c);
        io.execute(() -> {
            JSONObject cfg;
            try {
                cfg = popupCfg(app, page, g, m, kind, score, scorer, minute);
            } catch (Exception e) {
                return;
            }
            handler().post(() -> {
                if (showing() || !BoardCover.playerUp(app)) return;
                if (show(app, BroadcastView.page(app, page, cfg, ClassicIntro::gone), "strap".equals(page) ? "strap" : kind) && restart) {
                    restartUp(g.id, score);
                }
            });
        });
    }

    /**
     * The intro's settings for a game, in the style made for its competition: a top-five league's season intro (its clubs
     * one by one), else the competition's own film (else plain football's); the family's own video when they chose it;
     * the two teams under it (season: false for the competition's film whatever the league, a try's).
     */
    private static JSONObject introCfg(Context c, ScoresTicker.Game g) throws org.json.JSONException {
        return introCfg(c, g, true);
    }

    private static JSONObject introCfg(Context c, ScoresTicker.Game g, boolean season) throws org.json.JSONException {
        String sk = BroadcastData.seasonKey(g.slug, g.league);
        if (season && seasonOn(c) && !sk.isEmpty() && !IntroVideo.useOwn(c)) return BroadcastData.season(sk, fixtureOf(g));
        return cfgFor(c, BroadcastData.introKey(g.slug, g.league), fixtureOf(g));
    }

    private static String fixtureOf(ScoresTicker.Game g) {
        return BroadcastData.fixture(g.homeName != null ? g.homeName : g.home, g.awayName != null ? g.awayName : g.away);
    }

    // ---- the intros over the ad breaks before the game -------------------------------------------------------------

    /** The intros on are a break's: the break's end fades them, their end brings the break's own screen. */
    private static boolean breakUp;

    /** Whether an intro (any: a break's, the matchday's, a sport's, a try) is over the channel now. */
    static boolean introShowing() {
        return showing() && "intro".equals(viewKind);
    }

    /**
     * An advert break has started on the channel (AdBreak, the channel's logo gone), from an hour and a half to eight
     * minutes before the game's kick-off: an intro over it, every break, in the style made for its competition (a
     * top-five league's season intro, else the competition's own film), the channel's sound off meanwhile; not when the
     * game's last intro started under five minutes before. True when it is on (the break's own screen waits for its end;
     * it is never on the screen with it). Main thread.
     */
    static boolean breakIntros(Context c, ScoresTicker.Game g) {
        if (g == null || g.id == null || g.kickoff <= 0 || showing() || fetching) return false;
        long t = System.currentTimeMillis();
        if (!Moments.breakIntrosDue(g.kickoff - t) || !Moments.introSpaced(at(introAt, g.id), t)) return false;
        String sk = seasonOn(c) ? BroadcastData.seasonKey(g.slug, g.league) : "";
        String ck = introOn(c) ? BroadcastData.introKey(g.slug, g.league) : "";
        boolean uclGame = ucl(g);
        boolean matchReady = matchOn(c) && matchJson(g, "network") != null;
        Integer seen = breaksSeen.get(g.id);
        int n = seen == null ? 0 : seen;
        String style = Moments.breakStyle(n, matchReady, !sk.isEmpty() || !ck.isEmpty(), uclGame);
        if (style.isEmpty()) return false;
        Context app = c.getApplicationContext();
        // the matchday intro made ready meanwhile is let go: this break's leaves it out
        if ((g.id + "|intro").equals(prepKey)) dropPrepared();
        try {
            boolean ok = "other".equals(style)
                    ? show(app, BroadcastView.page(app, "reveal", BroadcastData.breakIntros(sk, ck, fixtureOf(g)), ClassicIntro::gone), "intro")
                    : show(app, BroadcastView.page(app, "match", matchJson(g, style), ClassicIntro::gone), "intro");
            if (!ok) return false;
        } catch (Exception e) {
            return false;
        }
        breaksSeen.put(g.id, n + 1);
        introAt.put(g.id, t);
        breakIntroAt.put(g.id, t);
        shown.add(g.id + "|break");
        breakUp = true;
        IntroQuiet.hush(app);
        AlertLog.note(app, "intro over the break before " + AlertLog.game(g) + ": " + ("ucl".equals(style) ? "the match's, Champions League style"
                : "network".equals(style) ? "the match's" : sk.isEmpty() ? "the competition's (" + ck + ")" : "the season's (" + sk + ")"));
        return true;
    }

    /** The break is over (the channel's logo back): its intros fade to the programme. */
    static void breakOver() {
        if (breakUp && view != null) view.back();
    }

    /**
     * A try of a league's season intro ("pl", "laliga", "seriea", "bundesliga", "ligue1"), what its games' breaks get,
     * with the game's teams when given.
     */
    static void trySeason(Context c, String key, ScoresTicker.Game g, Consumer<String> note) {
        if (showing() || fetching) {
            note.accept("Wait for the one on to finish");
            return;
        }
        Context app = c.getApplicationContext();
        try {
            String fixture = g != null && key.equals(BroadcastData.seasonKey(g.slug, g.league)) ? fixtureOf(g) : "";
            String id = "try|season|" + android.os.SystemClock.uptimeMillis();
            hold(app, "reveal", BroadcastData.season(key, fixture), "intro", id, (v) -> {
                if (goPrepared(id)) trial = true;
            });
        } catch (Exception e) {
            note.accept("Couldn't make it just now");
        }
    }

    /** An intro's settings: key's film (or the family's own video sent from the phone, then the ending), the line under it. */
    private static JSONObject cfgFor(Context c, String key, String fixture) throws org.json.JSONException {
        if (IntroVideo.useOwn(c)) return BroadcastData.intro(key, fixture, "file://" + IntroVideo.file(c).getAbsolutePath(), IntroVideo.durationMs(c));
        return BroadcastData.intro(key, fixture, null, 0);
    }

    /** The intro now (made ready before, else as soon as it is ready), once a game. */
    private static void intro(Context c, ScoresTicker.Game g, String key) {
        if (shown.contains(key) || showing() || !BoardCover.playerUp(c)) return;
        if (goPrepared(key)) {
            introAt.put(g.id, System.currentTimeMillis());
            return;
        }
        if (preparing || fetching) return;
        shown.add(key);
        Context app = c.getApplicationContext();
        try {
            String mc = matchOn(app) ? matchJson(g, ucl(g) ? "ucl" : "network") : null;
            if (show(app, mc != null ? BroadcastView.page(app, "match", mc, ClassicIntro::gone) : BroadcastView.page(app, "reveal", introCfg(app, g), ClassicIntro::gone), "intro")) {
                IntroQuiet.hush(app);
                introAt.put(g.id, System.currentTimeMillis());
            }
            android.util.Log.i("Sport", "matchday intro shown (not made ready before): " + key);
        } catch (Exception e) {
            android.util.Log.w("Sport", "matchday intro: " + e);
        }
    }

    /** The intro made ready out of sight for key; ready: told when it can go (a try's). */
    private static void holdIntro(Context c, ScoresTicker.Game g, String key, BroadcastView.Ready ready) {
        try {
            Context app = c.getApplicationContext();
            String mc = matchOn(app) ? matchJson(g, ucl(g) ? "ucl" : "network") : null;
            if (mc != null) hold(app, "match", mc, "intro", key, ready);
            else hold(app, "reveal", introCfg(app, g), "intro", key, ready);
        } catch (Exception e) {
            android.util.Log.w("Sport", "matchday intro not made ready: " + e);
        }
    }

    private static void lineups(Context c, ScoresTicker.Game g) {
        String key = g.id + "|lineups";
        long now = System.currentTimeMillis();
        Long tried = lineupsTried.get(g.id);
        if (shown.contains(key) || fetching || showing() || tried != null && now - tried < Moments.RETRY_MS) return;
        if (!BoardCover.playerUp(c)) return;
        fetching = true;
        lineupsTried.put(g.id, now);
        BroadcastData.Match m = match(g);
        Context app = c.getApplicationContext();
        File cache = app.getCacheDir();
        String style = lineupsStyle(app);
        io.execute(() -> {
            JSONObject cfg = null;
            try {
                int id = FotMob.find(g.id, g.homeName, g.awayName, g.kickoff);
                cfg = BroadcastData.lineups(m, FotMob.raw(id), cache);
                if ("studio".equals(style)) BroadcastData.studio(cfg, m);
            } catch (Exception ignored) {
                // tried again in a minute
            }
            JSONObject got = cfg;
            handler().post(() -> {
                fetching = false;
                if (got == null || shown.contains(key) || showing() || !BoardCover.playerUp(app)) return;
                shown.add(key);
                String page = lineupsPage(style);
                show(app, BroadcastView.page(app, page, got, ClassicIntro::gone), page);
            });
        });
    }

    private static boolean show(Context c, BroadcastView v, String kind) {
        removeWindow();
        FrameLayout f = new FrameLayout(c);
        f.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        WindowManager.LayoutParams lp = params(WindowManager.LayoutParams.MATCH_PARENT);
        try {
            c.getSystemService(WindowManager.class).addView(f, lp);
        } catch (Exception e) {
            v.stop();
            return false;
        }
        win = f;
        view = v;
        viewKind = kind;
        if ("intro".equals(kind)) AdBreak.introUp();
        if ("studio".equals(kind)) IntroQuiet.duck(c);   // its music over the channel, the channel's sound lowered meanwhile
        lineupsUp(kind);
        if (moment(kind)) BoardCover.resume(c);   // the board away at once, not on the screen with it
        return true;
    }

    /** The line-ups on the screen: the board out of the way, Back taken here (no key helper on the TCL). */
    private static void lineupsUp(String kind) {
        boolean lu = covers(kind);
        if (!lu && !"strap".equals(kind)) return;
        BoardCover.classicLineups(true);   // the strap draws the board itself as it folds into it
        if (lu && !InputKeyService.running) AppSearchService.keysFor("classic", true);
    }

    /** The overlay's window over the whole screen (MATCH_PARENT). */
    private static WindowManager.LayoutParams params(int size) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Not FLAG_NOT_TOUCHABLE: Android 12 and later turn such an overlay down to 80% (to let touches through),
                // and the channel showed through the crests, trophy and line-ups (as BoardCover's windows found). A TV has
                // no touch screen; not focusable keeps every key with the app.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("ClassicIntro");
        return lp;
    }

    // ---- made ready before the moment ---------------------------------------------------------------------------

    /**
     * The pop-up for key (kind, with its score, scorer and minute) made ready now, out of sight, for its moment (main
     * thread). q: how much to draw (adb tries), else the page's own choice. ready: told when it can go (a try's).
     */
    private static void prepare(Context c, ScoresTicker.Game g, String kind, int[] score, String scorer, String minute, String key,
                                JSONObject q, BroadcastView.Ready ready) {
        if (preparing || key.equals(prepKey) || shown.contains(key)) return;
        dropPrepared();
        preparing = true;
        BroadcastData.Match m = match(g);
        Context app = c.getApplicationContext();
        String page = popupPage(c);
        io.execute(() -> {
            JSONObject cfg = null;
            try {
                cfg = popupCfg(app, page, g, m, kind, score, scorer, minute);
                if (q != null && q.length() > 0) cfg.put("q", q);
            } catch (Exception ignored) {
                // not made ready: the moment makes it then, as before
            }
            JSONObject got = cfg;
            handler().post(() -> {
                preparing = false;
                if (got != null && !shown.contains(key)) hold(app, page, got, "strap".equals(page) ? "strap" : kind, key, ready);
            });
        });
    }

    /** The overlay (its page, made from cfg) made ready in an unseen window over the whole screen (main thread). */
    private static boolean hold(Context app, String page, JSONObject cfg, String kind, String key, BroadcastView.Ready ready) {
        return hold(app, page, cfg.toString(), kind, key, ready);
    }

    /** The same with its settings already written out (the match intro's, megabytes, written off the main thread). */
    private static boolean hold(Context app, String page, String cfgJson, String kind, String key, BroadcastView.Ready ready) {
        dropPrepared();
        BroadcastView v;
        try {
            v = BroadcastView.prepare(app, page, cfgJson, ClassicIntro::gone, ready);
        } catch (Exception e) {
            return false;
        }
        FrameLayout f = new FrameLayout(app);
        f.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        try {
            // The whole screen from the start, unseen (alpha 0, which the compositor skips): a WebView made in a window a
            // pixel square and then stretched kept drawing at that size on the TVs (nothing, or a blown-up corner, showed).
            WindowManager.LayoutParams hidden = params(WindowManager.LayoutParams.MATCH_PARENT);
            hidden.alpha = 0f;
            app.getSystemService(WindowManager.class).addView(f, hidden);
        } catch (Exception e) {
            v.stop();
            return false;
        }
        prep = v;
        prepWin = f;
        prepKey = key;
        prepKind = kind;
        prepAt = android.os.SystemClock.uptimeMillis();
        // Let go after PREP_KEEP_MS whatever happens: tick() looked at its age only while the board showed a game, so one
        // made ready before Home, another app or the board going off stayed in its unseen window (a whole web page, its
        // 3D trophy and its sounds) for as long as the launcher ran.
        handler().removeCallbacks(dropStale);
        handler().postDelayed(dropStale, PREP_KEEP_MS + 1_000L);
        android.util.Log.i("Sport", "classic " + kind + " being made ready: " + key);
        return true;
    }

    private static final Runnable dropStale = () -> {
        if (prep != null && android.os.SystemClock.uptimeMillis() - prepAt >= PREP_KEEP_MS) {
            android.util.Log.i("Sport", "classic " + prepKind + " made ready but never shown: let go");
            dropPrepared();
        }
    };

    /** The pages alive now, made ready and showing (each a web view), for the self-check. */
    static int alive() {
        return (prep != null ? 1 : 0) + (view != null ? 1 : 0);
    }

    /** A memory warning (MemoryWatch): the pop-up made ready out of sight let go (made again for its moment). */
    static void releaseHeld() {
        if (prep != null) {
            android.util.Log.i("Sport", "classic " + prepKind + " made ready: let go for memory");
            dropPrepared();
        }
    }

    /** The pop-up made ready for key, on the whole screen now (it plays as soon as it is ready, if it is not yet). */
    private static boolean goPrepared(String key) {
        if (prep == null || !key.equals(prepKey) || prep.isOver() || showing()) return false;
        BroadcastView v = prep;
        FrameLayout f = prepWin;
        prep = null;
        prepWin = null;
        prepKey = null;
        removeWindow();
        try {
            f.getContext().getSystemService(WindowManager.class).updateViewLayout(f, params(WindowManager.LayoutParams.MATCH_PARENT));
        } catch (Exception e) {
            v.stop();
            return false;
        }
        win = f;
        view = v;
        viewKind = prepKind;
        if ("studio".equals(prepKind)) IntroQuiet.duck(f.getContext());
        lineupsUp(prepKind);
        if (moment(prepKind)) BoardCover.resume(f.getContext());   // the board away at once, not on the screen with it
        if ("intro".equals(prepKind)) {
            IntroQuiet.hush(f.getContext());
            AdBreak.introUp();   // never on the screen with the ad-break screen
        }
        shown.add(key);
        android.util.Log.i("Sport", "classic " + prepKind + " shown" + (v.isReady() ? ", made ready before" : ", still getting ready"));
        v.go();
        return true;
    }

    /** The pop-up made ready, let go (its moment passed, or another is wanted). */
    private static void dropPrepared() {
        if (prep != null) prep.stop();   // gone() takes its window
        else removePrepWindow();
    }

    private static void removePrepWindow() {
        if (prepWin == null) return;
        FrameLayout w = prepWin;
        prepWin = null;
        try {
            w.getContext().getSystemService(WindowManager.class).removeView(w);
        } catch (Exception ignored) { }
    }

    /** Over (its own end, Back, or taken down): the window goes, the board comes back. */
    private static void gone(BroadcastView v) {
        if (v != null && v == prep) {
            removePrepWindow();
            prep = null;
            prepKey = null;
            return;
        }
        if (v != null && v != view) return;
        boolean wasLineups = covers(viewKind) || "strap".equals(viewKind);
        boolean wasMoment = moment(viewKind);
        boolean wasIntro = "intro".equals(viewKind);
        breakUp = false;
        if ("intro".equals(viewKind) && v != null) IntroQuiet.release(v.getContext());
        if ("studio".equals(viewKind) && v != null) IntroQuiet.unduck(v.getContext());
        view = null;
        viewKind = "";
        trial = false;
        removeWindow();
        if (wasLineups) {
            BoardCover.classicLineups(false);
            AppSearchService.keysFor("classic", false);
        }
        // the board back in now, as a broadcaster's comes in, with its swish
        if (wasMoment) BoardCover.momentGone();
        if (wasIntro && v != null) AdBreak.introGone(v.getContext().getApplicationContext());
    }

    private static void removeWindow() {
        if (win == null) return;
        FrameLayout w = win;
        win = null;
        try {
            w.getContext().getSystemService(WindowManager.class).removeView(w);
        } catch (Exception ignored) { }
    }

    /** Taken down at once (Home, another app, the board off); a try plays on to its end (or Back, for the line-ups). */
    static void hide() {
        if (trial) return;
        if (view != null) view.stop();
        else removeWindow();
        // The one made ready out of sight goes too (made again when the board is back for its moment): kept, it held a
        // whole web page in an unseen window with nothing left to show it.
        dropPrepared();
    }

    /** Back while the line-ups are on: they fade away to the channel (from the key helpers, as the board's keys). */
    static boolean key(KeyEvent e) {
        if (!showing() || !covers(viewKind)) return false;
        int code = e.getKeyCode();
        if (code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_ESCAPE) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) handler().post(() -> {
            if (view != null) view.back();
        });
        return true;
    }

    // ---- a try (the Scoreboard card's Try rows, adb) ------------------------------------------------------------

    /**
     * The pop-up ("kickoff", "restart", "second") or the line-ups ("lineups") now, over whatever is in front, for a real
     * game: the one chosen on the Scoreboard card first, else the Premier League's latest played, looking back a
     * fortnight (the line-ups need a game whose teams are out; the goal's pop-up one with a goal). note: why nothing came
     * (main thread). Main thread.
     */
    static void tryIt(Context c, ScoresTicker.Game chosen, String spec, Consumer<String> note) {
        // adb may add how much to draw after a bar: "kickoff|scale=0.6,flat,t3every=2,t3res=0.6" (the page's q)
        String[] sp = spec.split("\\|", 2);
        // adb may name the style: "strap:kickoff", "classic:restart", "stadium", "lineups" (else this TV's own styles)
        String head = sp[0], forced = null;
        // and the league to take the game from: "classic:kickoff@laliga", "stadium@esp.1" (else the Premier League)
        String leagueSpec = "eng.1|PL";
        int at = head.indexOf('@');
        if (at >= 0) {
            leagueSpec = league(head.substring(at + 1));
            head = head.substring(0, at);
        }
        final String lg = leagueSpec;
        if (head.startsWith("strap:") || head.startsWith("classic:")) {
            forced = head.substring(0, head.indexOf(':'));
            head = head.substring(head.indexOf(':') + 1);
        } else if ("stadium".equals(head) || "studio".equals(head)) {
            forced = head;
            head = "lineups";
        } else if ("lineups".equals(head)) {
            forced = "classic";
        }
        String kind = head, force = forced;
        JSONObject q = quality(sp.length > 1 ? sp[1] : "");
        if (kind.startsWith("intro:")) {
            trySport(c, kind.substring(6), note);
            return;
        }
        if ("intro".equals(kind)) {
            tryIntro(c, chosen, lg, q, note);
            return;
        }
        if ("match".equals(kind) || kind.startsWith("match:")) {
            tryMatch(c, chosen, at >= 0 ? lg : null, kind.startsWith("match:") ? kind.substring(6) : "network", q, note);
            return;
        }
        if (showing() || fetching) {
            note.accept("Wait for the one on to finish");
            return;
        }
        Context app = c.getApplicationContext();
        boolean lu = "lineups".equals(kind);
        fetching = true;
        long t0 = android.os.SystemClock.uptimeMillis();
        io.execute(() -> {
            JSONObject cfg = null;
            String about = null;
            int looked = 0;
            try {
                for (ScoresTicker.Game g : candidates(chosen, lg)) {
                    if ("restart".equals(kind) && g.goals.isEmpty() && g != chosen) continue;
                    if (++looked > (lu ? 5 : 3)) break;
                    BroadcastData.Match m = match(g);
                    try {
                        if (lu) {
                            cfg = BroadcastData.lineups(m, FotMob.raw(FotMob.find(g.id, g.homeName, g.awayName, g.kickoff)), app.getCacheDir());
                            if (cfg != null && "studio".equals(tryLineupsPage(app, force))) BroadcastData.studio(cfg, m);
                            if (cfg != null && q.length() > 0) cfg.put("q", q);
                        } else {
                            int[] score = "kickoff".equals(kind) ? null : trialScore(g);
                            String[] last = g.goals.isEmpty() ? null : g.goals.get(g.goals.size() - 1);
                            boolean goal = "restart".equals(kind);
                            cfg = popupCfg(app, tryPopupPage(app, force), g, m, kind, score, goal ? Moments.scorer(last) : null,
                                    goal ? (last == null ? "" : BroadcastData.minute(last[2])) : null);
                            if (q.length() > 0) cfg.put("q", q);
                        }
                    } catch (Exception oneGame) {
                        cfg = null;
                    }
                    if (cfg != null) {
                        about = m.homeName + " v " + m.awayName;
                        break;
                    }
                }
            } catch (RuntimeException ignored) {
                // nothing found: said below
            }
            JSONObject got = cfg;
            String game = about;
            int tries = looked;
            handler().post(() -> {
                fetching = false;
                if (got == null) {
                    android.util.Log.i("Sport", "classic try " + kind + ": nothing after " + tries + " game(s)");
                    note.accept(tries == 0 ? "Couldn't reach the scores just now" : lu ? "No game with its line-ups out to try" : "Couldn't make it just now");
                    return;
                }
                if (showing()) return;
                android.util.Log.i("Sport", "classic try " + kind + ": " + game + ", data in " + (android.os.SystemClock.uptimeMillis() - t0) + " ms");
                // made ready out of sight, then the whole screen the moment it is (no empty window while it loads)
                String key = "try|" + kind + "|" + android.os.SystemClock.uptimeMillis();
                String page = lu ? tryLineupsPage(app, force) : tryPopupPage(app, force);
                String shownAs = lu ? page : "strap".equals(page) ? "strap" : kind;
                hold(app, page, got, shownAs, key, (v) -> {
                    if (goPrepared(key)) trial = true;
                });
            });
        });
    }

    /** A try of the matchday intro, for the game chosen, else the league's latest played (its competition's film and teams). */
    private static void tryIntro(Context c, ScoresTicker.Game chosen, String league, JSONObject q, Consumer<String> note) {
        if (showing() || fetching) {
            note.accept("Wait for the one on to finish");
            return;
        }
        Context app = c.getApplicationContext();
        fetching = true;
        io.execute(() -> {
            JSONObject cfg = null;
            String about = null;
            try {
                for (ScoresTicker.Game g : candidates(chosen, league)) {
                    cfg = introCfg(app, g, false);
                    if (q.length() > 0) cfg.put("q", q);
                    about = (g.homeName != null ? g.homeName : g.home) + " v " + (g.awayName != null ? g.awayName : g.away);
                    break;
                }
            } catch (Exception ignored) {
                cfg = null;
            }
            JSONObject got = cfg;
            String game = about;
            handler().post(() -> {
                fetching = false;
                if (got == null) {
                    note.accept("Couldn't reach the scores just now");
                    return;
                }
                if (showing()) return;
                android.util.Log.i("Sport", "matchday intro try: " + game);
                String key = "try|intro|" + android.os.SystemClock.uptimeMillis();
                hold(app, "reveal", got, "intro", key, (v) -> {
                    if (goPrepared(key)) trial = true;
                });
            });
        });
    }

    /**
     * A try of the match intro in a style ("network", or "ucl": the Champions League's), for the game chosen, else the
     * league's next (league: ESPN's slug and name, "uefa.champions|Champions League"; null for the style's own: the
     * Champions League's for "ucl", else the Premier League's). Its data takes a while (up to a minute).
     */
    private static void tryMatch(Context c, ScoresTicker.Game chosen, String league, String style, JSONObject q, Consumer<String> note) {
        if (showing() || fetching) {
            note.accept("Wait for the one on to finish");
            return;
        }
        Context app = c.getApplicationContext();
        String lg = league != null ? league : "ucl".equals(style) ? "uefa.champions|Champions League" : "eng.1|PL";
        fetching = true;
        note.accept("Getting the players ready (up to a minute)");
        matchIo.execute(() -> {
            String json = null, about = null;
            int n = 0;
            try {
                for (ScoresTicker.Game g : candidates("ucl".equals(style) && !ucl(chosen) ? null : chosen, lg)) {
                    int id = FotMob.find(g.id, g.homeName, g.awayName, g.kickoff);
                    JSONObject cfg = MatchIntro.build(app.getAssets(), match(g), id > 0 ? FotMob.raw(id) : null, app.getCacheDir(), fixtureOf(g)).put("style", style);
                    if (q.length() > 0) cfg.put("q", q);
                    n = players(cfg);
                    json = cfg.toString();
                    about = (g.homeName != null ? g.homeName : g.home) + " v " + (g.awayName != null ? g.awayName : g.away);
                    break;
                }
            } catch (Exception ignored) {
                json = null;
            }
            String got = json, game = about;
            int count = n;
            handler().post(() -> {
                fetching = false;
                if (got == null) {
                    note.accept("Couldn't reach the scores just now");
                    return;
                }
                if (showing()) return;
                android.util.Log.i("Sport", "match intro try (" + style + "): " + game + ", " + count + " players");
                String key = "try|match|" + android.os.SystemClock.uptimeMillis();
                hold(app, "match", got, "intro", key, (v) -> {
                    if (goPrepared(key)) trial = true;
                });
            });
        });
    }

    /** What a sport's try writes under the wordmark. */
    private static String sample(String key) {
        switch (key) {
            case "f1": return "Abu Dhabi Grand Prix";
            case "ufc": return "UFC main card";
            case "boxing": return "Tonight's main event";
            default: return "England v Spain";
        }
    }

    /** A try of a sport's intro ("f1", "ufc", "boxing", or "football" and any competition's key), over whatever is in front. */
    static void trySport(Context c, String key, Consumer<String> note) {
        if (showing() || fetching) {
            note.accept("Wait for the one on to finish");
            return;
        }
        Context app = c.getApplicationContext();
        try {
            String k = java.util.Arrays.asList(BroadcastData.INTRO_KEYS).contains(key) ? key : "football";
            String id = "try|intro|" + android.os.SystemClock.uptimeMillis();
            hold(app, "reveal", cfgFor(app, k, sample(k)), "intro", id, (v) -> {
                if (goPrepared(id)) trial = true;
            });
        } catch (Exception e) {
            note.accept("Couldn't make it just now");
        }
    }

    // ---- F1, UFC and boxing: as they begin on the channel watched ------------------------------------------------

    /** Whether the channel last tuned is an F1 one by its name ("Sky Sports F1", "F1 TV"). */
    static boolean tunedF1(Context c) {
        for (String n : InStep.tunedNames(c)) {
            String k = " " + n.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ") + " ";
            if (k.contains(" f1 ") || k.contains(" formula")) return true;
        }
        return false;
    }

    /**
     * Each scores poll (main thread): F1's intro four minutes before the race starts (ESPN's weekend) on an F1 channel,
     * and UFC's and boxing's four minutes before their programme starts on the channel tuned (Sky's guide: prog is its
     * {title, start ms}, or null); each once.
     */
    static void sportTick(Context c, F1Results.Weekend weekend, Object[] prog) {
        if (!introOn(c) || showing() || fetching || preparing || !BoardCover.playerUp(c)) return;
        long now = System.currentTimeMillis();
        String title = prog == null ? null : (String) prog[0];
        long start = prog == null ? 0 : (Long) prog[1];
        String sport = Moments.sportOf(title);
        if (weekend != null) {
            for (F1Results.Session s : weekend.sessions) {
                if (!"Race".equals(s.abbr) || !"pre".equals(s.state) || !Moments.raceIntroDue(s.start - now)) continue;
                if ("f1".equals(sport) || tunedF1(c)) sportIntro(c, "f1", weekend.name, "f1|" + s.id);
                return;
            }
        }
        if (sport == null || !Moments.programmeDue(start, now)) return;
        if ("f1".equals(sport) && weekend != null) return;   // the race's own start, above
        sportIntro(c, sport, Moments.eventLine(title), sport + "|" + start);
    }

    private static void sportIntro(Context c, String key, String line, String momentKey) {
        if (shown.contains(momentKey) || momentKey.equals(prepKey)) return;
        Context app = c.getApplicationContext();
        try {
            android.util.Log.i("Sport", "intro for " + key + ": " + line);
            hold(app, "reveal", cfgFor(app, key, line), "intro", momentKey, (v) -> goPrepared(momentKey));
        } catch (Exception e) {
            android.util.Log.w("Sport", "intro for " + key + ": " + e);
        }
    }

    /** A try's pop-up page: the style adb named, else this TV's. */
    private static String tryPopupPage(Context c, String force) {
        if ("strap".equals(force)) return "strap";
        if ("classic".equals(force)) return "kickoff";
        return popupPage(c);
    }

    /** A try's line-ups page: the style adb named, else this TV's. */
    private static String tryLineupsPage(Context c, String force) {
        if ("stadium".equals(force)) return "stadium";
        if ("studio".equals(force)) return "studio";
        if ("classic".equals(force)) return "lineups";
        return lineupsPage(lineupsStyle(c));
    }

    /** "scale=0.6,flat,t3every=2" as the page's q: {"scale":0.6,"flat":true,"t3every":2}. */
    private static JSONObject quality(String words) {
        JSONObject q = new JSONObject();
        for (String w : words.split(",")) {
            String[] kv = w.trim().split("=", 2);
            if (kv[0].isEmpty()) continue;
            try {
                if (kv.length == 1) q.put(kv[0], true);
                else q.put(kv[0], Double.parseDouble(kv[1]));
            } catch (Exception ignored) {
                // a word it does not know is left out
            }
        }
        return q;
    }

    /** The games a try looks at, in turn: the one chosen, then the Premier League's played ones, latest first (io thread). */
    /** Pure: a try's league as ScoresTicker takes it: "laliga" or "esp.1" to "esp.1|LALIGA", "pl" to "eng.1|PL". */
    static String league(String s) {
        String k = s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT);
        switch (k) {
            case "": case "pl": case "eng.1": case "premier": return "eng.1|PL";
            case "laliga": case "la liga": case "esp": case "esp.1": return "esp.1|LALIGA";
            default: return k.contains("|") ? s.trim() : k + "|" + k.toUpperCase(java.util.Locale.ROOT);
        }
    }

    private static List<ScoresTicker.Game> candidates(ScoresTicker.Game chosen, String league) {
        List<ScoresTicker.Game> out = new ArrayList<>();
        if (chosen != null && chosen.id != null) out.add(chosen);
        LocalDate day = LocalDate.now(ZoneId.systemDefault());
        for (int back = 0; back < 14 && out.size() < 8; back++) {
            List<ScoresTicker.Game> played = new ArrayList<>();
            for (ScoresTicker.Game g : ScoresTicker.load(new String[]{league}, day.minusDays(back))) {
                if (g.state != 1 && g.id != null && (chosen == null || !g.id.equals(chosen.id))) played.add(g);
            }
            played.sort(Comparator.comparingLong((ScoresTicker.Game g) -> g.kickoff).reversed());
            out.addAll(played);
        }
        return out;
    }

    /** The game's score as it stands (or ended), for the goal's and the second half's pop-ups; 1–0 when it has none. */
    private static int[] trialScore(ScoresTicker.Game g) {
        try {
            return new int[]{Integer.parseInt(g.homeScore.trim()), Integer.parseInt(g.awayScore.trim())};
        } catch (RuntimeException e) {
            return new int[]{1, 0};
        }
    }
}
