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
    private static boolean fetching;
    /** A try from the Scoreboard card or adb is on: over whatever is in front, not taken down with the board. */
    private static boolean trial;
    /** When each game's phase last changed (uptime): half time's length, for the second half's pop-up made ready. */
    private static final Map<String, Long> phaseSince = new HashMap<>();
    /**
     * A pop-up made ready before its moment (BroadcastView.prepare), out of sight in a window a pixel square, so that it
     * is on the screen the moment comes (it took the TCL four to twelve seconds to get one ready): the kick-off's from
     * two and a half minutes before, a goal's restart 25 seconds after the goal shows, the second half's from ten
     * minutes into half time. One at a time, kept a quarter of an hour at most.
     */
    private static BroadcastView prep;
    private static FrameLayout prepWin;
    private static String prepKey, prepKind = "";
    private static long prepAt;
    private static boolean preparing;
    private static final long PREP_KICKOFF_MS = 150_000L, PREP_RESTART_MS = 25_000L, PREP_SECOND_MS = 10 * 60_000L, PREP_KEEP_MS = 15 * 60_000L;

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
        long up = android.os.SystemClock.uptimeMillis();
        if (!now.isEmpty()) {
            if (!now.equals(was)) phaseSince.put(g.id, up);
            phases.put(g.id, now);
        }
        if (prep != null && up - prepAt > PREP_KEEP_MS) dropPrepared();
        String kind = was == null ? null : Moments.moment(was, now);
        if (kind != null && popupOn(c)) popup(c, g, kind, "kickoff".equals(kind) ? null : BroadcastData.score(score), null, null);
        if ("pre".equals(now) && g.kickoff > 0 && lineupsOn(c) && Moments.lineupsDue(g.kickoff - screenNow)) lineups(c, g);
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
        if (goPrepared(key)) return;
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
        lineupsUp(kind);
        return true;
    }

    /** The line-ups on the screen: the board out of the way, Back taken here (no key helper on the TCL). */
    private static void lineupsUp(String kind) {
        if (!"lineups".equals(kind)) return;
        BoardCover.classicLineups(true);
        if (!InputKeyService.running) AppSearchService.keysFor("classic", true);
    }

    /** The overlay's window: the whole screen (MATCH_PARENT), or a pixel square while a pop-up is made ready (1). */
    private static WindowManager.LayoutParams params(int size) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
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
        io.execute(() -> {
            JSONObject cfg = null;
            try {
                cfg = BroadcastData.kickoff(kind, m, score, scorer, minute);
                if (q != null && q.length() > 0) cfg.put("q", q);
            } catch (Exception ignored) {
                // not made ready: the moment makes it then, as before
            }
            JSONObject got = cfg;
            handler().post(() -> {
                preparing = false;
                if (got != null && !shown.contains(key)) hold(app, "kickoff", got, kind, key, ready);
            });
        });
    }

    /** The overlay (page "kickoff" or "lineups", made from cfg) made ready in a window a pixel square (main thread). */
    private static boolean hold(Context app, String page, JSONObject cfg, String kind, String key, BroadcastView.Ready ready) {
        dropPrepared();
        BroadcastView v;
        try {
            v = BroadcastView.prepare(app, page, cfg, ClassicIntro::gone, ready);
        } catch (Exception e) {
            return false;
        }
        FrameLayout f = new FrameLayout(app);
        f.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        try {
            app.getSystemService(WindowManager.class).addView(f, params(1));
        } catch (Exception e) {
            v.stop();
            return false;
        }
        prep = v;
        prepWin = f;
        prepKey = key;
        prepKind = kind;
        prepAt = android.os.SystemClock.uptimeMillis();
        android.util.Log.i("Sport", "classic " + kind + " being made ready: " + key);
        return true;
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
        lineupsUp(prepKind);
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
        boolean wasLineups = "lineups".equals(viewKind);
        view = null;
        viewKind = "";
        trial = false;
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

    /** Taken down at once (Home, another app, the board off); a try plays on to its end (or Back, for the line-ups). */
    static void hide() {
        if (trial) return;
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
        String kind = sp[0];
        JSONObject q = quality(sp.length > 1 ? sp[1] : "");
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
                for (ScoresTicker.Game g : candidates(chosen)) {
                    if ("restart".equals(kind) && g.goals.isEmpty() && g != chosen) continue;
                    if (++looked > (lu ? 5 : 3)) break;
                    BroadcastData.Match m = match(g);
                    try {
                        if (lu) {
                            cfg = BroadcastData.lineups(m, FotMob.raw(FotMob.find(g.id, g.homeName, g.awayName, g.kickoff)), app.getCacheDir());
                            if (cfg != null && q.length() > 0) cfg.put("q", q);
                        } else {
                            int[] score = "kickoff".equals(kind) ? null : trialScore(g);
                            String[] last = g.goals.isEmpty() ? null : g.goals.get(g.goals.size() - 1);
                            boolean goal = "restart".equals(kind);
                            cfg = BroadcastData.kickoff(kind, m, score, goal ? Moments.scorer(last) : null,
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
                hold(app, lu ? "lineups" : "kickoff", got, kind, key, (v) -> {
                    if (goPrepared(key)) trial = true;
                });
            });
        });
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
    private static List<ScoresTicker.Game> candidates(ScoresTicker.Game chosen) {
        List<ScoresTicker.Game> out = new ArrayList<>();
        if (chosen != null && chosen.id != null) out.add(chosen);
        LocalDate day = LocalDate.now(ZoneId.systemDefault());
        for (int back = 0; back < 14 && out.size() < 8; back++) {
            List<ScoresTicker.Game> played = new ArrayList<>();
            for (ScoresTicker.Game g : ScoresTicker.load(new String[]{"eng.1|PL"}, day.minusDays(back))) {
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
