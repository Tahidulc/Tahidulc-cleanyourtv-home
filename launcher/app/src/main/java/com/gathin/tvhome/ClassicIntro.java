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
    private static final long PREP_KICKOFF_MS = 150_000L, PREP_RESTART_MS = 25_000L, PREP_SECOND_MS = 10 * 60_000L, PREP_KEEP_MS = 15 * 60_000L;

    private static Handler handler() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /**
     * The kick-off pop-up's style (and the restart's and the second half's): "classic" (from the family's clip: crests,
     * trophy and teams), "strap" (the first design: a strap in the scoreboard's own look that folds into it) or "off".
     */
    static String popupStyle(Context c) {
        String s = prefs(c).getString("popup_style", null);
        return s != null ? s : prefs(c).getBoolean("popup", true) ? "classic" : "off";
    }

    static void setPopupStyle(Context c, String style) {
        prefs(c).edit().putString("popup_style", style).apply();
    }

    /** The line-ups' style before kick-off: "classic" (the beam-in, from the reel), "stadium" (the first design) or "off". */
    static String lineupsStyle(Context c) {
        String s = prefs(c).getString("lineups_style", null);
        return s != null ? s : prefs(c).getBoolean("lineups", true) ? "classic" : "off";
    }

    static void setLineupsStyle(Context c, String style) {
        prefs(c).edit().putString("lineups_style", style).apply();
    }

    /** Pure: the next style on the card's OK: classic, then the first design, then off. */
    static String nextStyle(String style, String first) {
        return "classic".equals(style) ? first : first.equals(style) ? "off" : "classic";
    }

    /** The matchday intro over the last advert break before kick-off (intro.html, with the real-footage film): on or off. */
    static boolean introOn(Context c) {
        return prefs(c).getBoolean("intro", true);
    }

    static void setIntro(Context c, boolean on) {
        prefs(c).edit().putBoolean("intro", on).apply();
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
        if (!"strap".equals(page)) return BroadcastData.kickoff(kind, m, score, scorer, minute);
        return BroadcastData.strap(kind, m, score, scorer, minute, BoardCover.style(c).name(), BoardCover.boardRect1920(),
                "kickoff".equals(kind) ? MatchExtras.weatherNow(c, g) : "");
    }

    /** Whether one of them is on the screen now. */
    static boolean showing() {
        return view != null && !view.isOver();
    }

    static boolean showingLineups() {
        return showing() && ("lineups".equals(viewKind) || "stadium".equals(viewKind));
    }

    /** Whether the overlay on now covers the board and takes Back: the line-ups and the intro. */
    private static boolean covers(String kind) {
        return "lineups".equals(kind) || "stadium".equals(kind) || "intro".equals(kind);
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
        if ("pre".equals(now) && g.kickoff > 0 && lineupsOn(c) && Moments.lineupsDue(lineupsStyle(c), g.kickoff - screenNow)) lineups(c, g);
        // the matchday intro over the last break: made ready a little before, then on the whole screen
        if ("pre".equals(now) && g.kickoff > 0 && introOn(c)) {
            long until = g.kickoff - screenNow;
            String key = g.id + "|intro";
            if (Moments.introDue(until)) intro(c, g, key);
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
                show(app, BroadcastView.page(app, page, cfg, ClassicIntro::gone), "strap".equals(page) ? "strap" : kind);
            });
        });
    }

    /**
     * The intro's settings for a game: its competition's trophy, the teams, and the film: the family's own video when
     * one was sent from the phone and is chosen (IntroVideo), else the one in this app's bundle.
     */
    private static JSONObject introCfg(Context c, ScoresTicker.Game g) throws org.json.JSONException {
        if (IntroVideo.useOwn(c)) {
            return BroadcastData.intro(match(g), "file://" + IntroVideo.file(c).getAbsolutePath(),
                    Moments.introStart(IntroVideo.durationMs(c)));
        }
        return BroadcastData.intro(match(g), true);
    }

    /** The intro now (made ready before, else as soon as it is ready), once a game. */
    private static void intro(Context c, ScoresTicker.Game g, String key) {
        if (shown.contains(key) || showing() || !BoardCover.playerUp(c)) return;
        if (goPrepared(key)) return;
        if (preparing || fetching) return;
        shown.add(key);
        Context app = c.getApplicationContext();
        try {
            show(app, BroadcastView.page(app, "intro", introCfg(app, g), ClassicIntro::gone), "intro");
            android.util.Log.i("Sport", "matchday intro shown (not made ready before): " + key);
        } catch (Exception e) {
            android.util.Log.w("Sport", "matchday intro: " + e);
        }
    }

    /** The intro made ready out of sight for key; ready: told when it can go (a try's). */
    private static void holdIntro(Context c, ScoresTicker.Game g, String key, BroadcastView.Ready ready) {
        try {
            hold(c.getApplicationContext(), "intro", introCfg(c.getApplicationContext(), g), "intro", key, ready);
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
                String page = "stadium".equals(lineupsStyle(app)) ? "stadium" : "lineups";
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
        lineupsUp(kind);
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
        boolean wasLineups = covers(viewKind) || "strap".equals(viewKind);
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
        } else if ("stadium".equals(head)) {
            forced = "stadium";
            head = "lineups";
        } else if ("lineups".equals(head)) {
            forced = "classic";
        }
        String kind = head, force = forced;
        JSONObject q = quality(sp.length > 1 ? sp[1] : "");
        if ("intro".equals(kind)) {
            tryIntro(c, chosen, lg, q, note);
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

    /** A try of the matchday intro, for the game chosen, else the league's latest played (its trophy and teams). */
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
                    cfg = introCfg(app, g);
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
                hold(app, "intro", got, "intro", key, (v) -> {
                    if (goPrepared(key)) trial = true;
                });
            });
        });
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
        if ("classic".equals(force)) return "lineups";
        return "stadium".equals(lineupsStyle(c)) ? "stadium" : "lineups";
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
