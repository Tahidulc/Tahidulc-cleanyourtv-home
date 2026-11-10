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
import com.gathin.broadcast.Moments;

import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The classic kick-off pop-up and line-ups over the channel, as the user's clips have them (overlays/: BroadcastView),
 * while the scoreboard is on for a game (BoardCover), each with its switch on the Scoreboard card:
 * <ul>
 * <li>the line-ups once, from seven minutes before kick-off on the screen (when FotMob has them), both teams in about a
 * minute and a half, the scoreboard out of the way meanwhile; Back closes them, straight back to the channel;</li>
 * <li>the pop-up as the kick-off reaches the screen, when play starts again after a goal (40 seconds after the goal shows,
 * with the score, scorer and minute) and at the second half's kick-off (with the score).</li>
 * </ul>
 * Each moment is shown once. Nothing shows over Home, another app, the screensaver or another of the launcher's frames.
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
    private static boolean fetching;

    private static Handler handler() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** The kick-off pop-up (and the restart's and the second half's): on unless switched off. */
    static boolean popupOn(Context c) {
        return prefs(c).getBoolean("popup", true);
    }

    static void setPopupOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("popup", on).apply();
    }

    /** The classic line-ups before kick-off: on unless switched off. */
    static boolean lineupsOn(Context c) {
        return prefs(c).getBoolean("lineups", true);
    }

    static void setLineupsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("lineups", on).apply();
    }

    /** Whether one of them is on the screen now. */
    static boolean showing() {
        return view != null && !view.isOver();
    }

    static boolean showingLineups() {
        return showing() && "lineups".equals(viewKind);
    }

    // ---- when (com.gathin.broadcast.Moments: the same on the phone) ---------------------------------------------

    /**
     * From the board each time it draws the game (main thread): its phase moved on (the kick-off, the second half's), or
     * the line-ups due. screenNow: the time the channel's picture has reached.
     */
    static void tick(Context c, ScoresTicker.Game g, boolean pre, int state, boolean halftime, String score, long screenNow) {
        if (g == null || g.id == null) return;
        if (showing() && !BoardCover.playerUp(c)) hide();
        String was = phases.get(g.id);
        String now = Moments.phase(pre, state, halftime, was);
        if (!now.isEmpty()) phases.put(g.id, now);
        String kind = was == null ? null : Moments.moment(was, now);
        if (kind != null && popupOn(c)) popup(c, g, kind, "kickoff".equals(kind) ? null : BroadcastData.score(score), null, null);
        if ("pre".equals(now) && g.kickoff > 0 && lineupsOn(c) && Moments.lineupsDue(g.kickoff - screenNow)) lineups(c, g);
    }

    /** A goal reached the screen (the board's moment): the restart's pop-up in a little while, if the half goes on. */
    static void goal(Context c, ScoresTicker.Game g, String score, int side) {
        if (g == null || !popupOn(c)) return;
        String id = g.id;
        handler().postDelayed(() -> {
            String ph = phases.get(id);
            if (!"h1".equals(ph) && !"h2".equals(ph)) return;   // half time or full time came first
            ScoresTicker.Game now = GoalWatchService.latestGame(id);
            ScoresTicker.Game game = now != null ? now : g;
            String[] last = Moments.lastGoal(game.goals, side);
            popup(c, game, "restart", BroadcastData.score(score), Moments.scorer(last), last == null ? "" : BroadcastData.minute(last[2]));
        }, Moments.RESTART_MS);
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
        return m;
    }

    private static void popup(Context c, ScoresTicker.Game g, String kind, int[] score, String scorer, String minute) {
        String key = g.id + "|" + kind + (score == null ? "" : "|" + score[0] + "-" + score[1]);
        if (shown.contains(key) || showing() || !BoardCover.playerUp(c)) return;
        shown.add(key);
        BroadcastData.Match m = match(g);
        Context app = c.getApplicationContext();
        io.execute(() -> {
            JSONObject cfg;
            try {
                cfg = BroadcastData.kickoff(kind, m, score, scorer, minute);
            } catch (Exception e) {
                return;
            }
            handler().post(() -> {
                if (showing() || !BoardCover.playerUp(app)) return;
                show(app, BroadcastView.kickoff(app, cfg, ClassicIntro::gone), kind);
            });
        });
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
        io.execute(() -> {
            JSONObject cfg = null;
            try {
                int id = FotMob.find(g.id, g.homeName, g.awayName, g.kickoff);
                cfg = BroadcastData.lineups(m, FotMob.raw(id), cache);
            } catch (Exception ignored) {
                // tried again in a minute
            }
            JSONObject got = cfg;
            handler().post(() -> {
                fetching = false;
                if (got == null || shown.contains(key) || showing() || !BoardCover.playerUp(app)) return;
                shown.add(key);
                show(app, BroadcastView.lineups(app, got, ClassicIntro::gone), "lineups");
            });
        });
    }

    private static void show(Context c, BroadcastView v, String kind) {
        removeWindow();
        FrameLayout f = new FrameLayout(c);
        f.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("ClassicIntro");
        try {
            c.getSystemService(WindowManager.class).addView(f, lp);
        } catch (Exception e) {
            v.stop();
            return;
        }
        win = f;
        view = v;
        viewKind = kind;
        if ("lineups".equals(kind)) {
            BoardCover.classicLineups(true);
            if (!InputKeyService.running) AppSearchService.keysFor("classic", true);
        }
    }

    /** Over (its own end, Back, or taken down): the window goes, the board comes back. */
    private static void gone(BroadcastView v) {
        if (v != null && v != view) return;
        boolean wasLineups = "lineups".equals(viewKind);
        view = null;
        viewKind = "";
        removeWindow();
        if (wasLineups) {
            BoardCover.classicLineups(false);
            AppSearchService.keysFor("classic", false);
        }
    }

    private static void removeWindow() {
        if (win == null) return;
        FrameLayout w = win;
        win = null;
        try {
            w.getContext().getSystemService(WindowManager.class).removeView(w);
        } catch (Exception ignored) { }
    }

    /** Taken down at once (Home, another app, the board off). */
    static void hide() {
        if (view != null) view.stop();
        else removeWindow();
    }

    /** Back while the line-ups are on: they fade away to the channel (from the key helpers, as the board's keys). */
    static boolean key(KeyEvent e) {
        if (!showingLineups()) return false;
        int code = e.getKeyCode();
        if (code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_ESCAPE) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) handler().post(() -> {
            if (view != null) view.back();
        });
        return true;
    }

    /** adb tests only: the pop-up or the line-ups for a game now, as if its moment had come. */
    static void test(Context c, ScoresTicker.Game g, String kind) {
        shown.removeIf((k) -> k.startsWith(g.id + "|"));
        lineupsTried.remove(g.id);
        if ("lineups".equals(kind)) lineups(c, g);
        else popup(c, g, kind, "kickoff".equals(kind) ? null : new int[]{1, 0}, "restart".equals(kind) ? "A. Player" : null, "restart".equals(kind) ? "23'" : null);
    }
}
