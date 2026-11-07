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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A football match or an F1 session being watched in the player with its overlays off: a small solid button comes into
 * the top right corner (OverlayButton: HOLD and the remote's OK, "for overlays"), and OK held on it turns them on: for
 * a match the scoreboard over the channel's own and the cover over its logo (BoardCover), for F1 the timing tower down
 * the left and the cover (F1Tower). They go where that broadcaster puts its own (BoardPlace: the TV cannot see the
 * picture under TiviMate, so by the channel's broadcaster, as its places were last set or as it is known to lay them
 * out). A short OK goes on to the player; Back puts the button away; it goes by itself after a few seconds, and asks
 * once a game (a day of F1).
 *
 * <p>What is being watched: the game on the channel tuned from here or in Match mode (GoalWatchService.watchedAmong),
 * or, read off the player's own channel banner by the channel helper as the channel is changed in TiviMate (seen),
 * the one channel a match or F1 is on now that the banner names.
 */
final class OverlayPrompt {
    /** How long the button stays up. */
    static final long SHOW_MS = 12_000L;
    /** A match kicking off this soon counts as on (its build-up). */
    static final long SOON_MS = 15 * 60_000L;
    private static final String FILE = "overlay_prompt";
    private static Handler main;
    private static final ExecutorService io = Executors.newSingleThreadExecutor();

    private static Context app;
    private static FrameLayout win;
    private static OverlayButton button;
    /** What it asks for: "football" (game) or "f1". */
    private static String kind;
    private static ScoresTicker.Game game;
    private static long okDown = -1;
    private static boolean fired, checking;
    /** The channels a match or F1 is on now ("game:<id>" or "f1", each with its channels' names): what a banner names. */
    private static volatile Map<String, List<String>> onNow = new HashMap<>();
    private static String seenKey;
    private static long seenAt;
    private static final Runnable hideRun = OverlayPrompt::hide, onHold = OverlayPrompt::fire;

    private OverlayPrompt() { }

    /** The main thread's handler, made when first wanted (the pure parts load without one, for the tests). */
    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Whether it asks over a match (the Scoreboard card's switch; on unless switched off). */
    static boolean on(Context c) {
        return prefs(c).getBoolean("on", true);
    }

    static void setOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("on", on).apply();
        if (!on) hide();
    }

    static boolean isShowing() {
        return win != null;
    }

    // ---- what is being watched -----------------------------------------------------------------------------------------

    /**
     * Each scores poll (main thread): the channels the matches and F1 are on now worked out off the main thread (the TV
     * guide), and the button put up when one of them is being watched in the player with its overlays off.
     */
    static void check(Context c, List<ScoresTicker.Game> games, boolean f1Live) {
        app = c.getApplicationContext();
        if (!on(app) || checking || games == null) return;
        long now = System.currentTimeMillis();
        List<ScoresTicker.Game> live = new ArrayList<>();
        for (ScoresTicker.Game g : games) {
            if (g != null && g.id != null && (g.state == 0 || g.state == 1 && g.kickoff > now && g.kickoff - now <= SOON_MS)) live.add(g);
        }
        if (live.isEmpty() && !f1Live) {
            onNow = new HashMap<>();
            return;
        }
        checking = true;
        Context a = app;
        io.execute(() -> {
            Map<String, List<String>> on = new HashMap<>();
            ScoresTicker.Game watched = null;
            boolean f1Watched = false;
            try {
                String[] channels = a.getResources().getStringArray(R.array.football_tv_channels);
                for (ScoresTicker.Game g : live) {
                    TvGuide.Watch w = TvGuide.watch(channels, g);
                    if (w != null && !w.names.isEmpty()) on.put("game:" + g.id, w.names);
                }
                if (f1Live) {
                    ScoresTicker.Game f1 = new ScoresTicker.Game();
                    f1.leagueLogo = F1Results.F1_LOGO;
                    TvGuide.Watch w = TvGuide.watch(channels, f1);
                    if (w != null && !w.names.isEmpty()) on.put("f1", w.names);
                }
                watched = GoalWatchService.watchedAmong(live);
                f1Watched = on.containsKey("f1") && InStep.onChannel(InStep.tunedNames(a), on.get("f1"));
            } catch (RuntimeException ignored) {
                // looked for again at the next poll
            }
            ScoresTicker.Game w = watched;
            boolean f1 = f1Watched;
            main().post(() -> {
                checking = false;
                onNow = on;
                if (w != null) maybeAsk(a, "football", w);
                else if (f1) maybeAsk(a, "f1", null);
            });
        });
    }

    /**
     * The player's screen as the channel helper reads it (main thread, TiviMate's, at most every second): when it names
     * one channel a match or F1 is on now (its banner as the channel changes), that is the channel watched, kept as if
     * tuned from here (InStep), and the button asked for. A screen naming more than one (its guide, its lists) says
     * nothing.
     */
    static void seen(Context c, List<String> texts) {
        Map<String, List<String>> on = onNow;
        if (on.isEmpty() || texts == null || texts.isEmpty() || texts.size() > 40) return;
        String[] hit = among(texts, on);
        if (hit == null) return;
        app = c.getApplicationContext();
        long now = System.currentTimeMillis();
        if (hit[0].equals(seenKey) && now - seenAt < 10 * 60_000L) return;
        seenKey = hit[0];
        seenAt = now;
        // The name as the player shows it first (its HD or UHD), then the others for the same channel.
        List<String> names = new ArrayList<>();
        names.add(hit[1]);
        for (String n : on.get(hit[0])) if (!n.equals(hit[1])) names.add(n);
        if (!InStep.onChannel(Collections.singletonList(InStep.channel(app)), names) || !InStep.channel(app).equals(hit[1])) {
            InStep.tuned(app, names);
            AlertLog.note(app, "channel watched, from the player's banner: " + hit[1]);
        }
        if ("f1".equals(hit[0])) {
            maybeAsk(app, "f1", null);
        } else {
            ScoresTicker.Game g = GoalWatchService.latestGame(hit[0].substring(5));
            if (g != null) maybeAsk(app, "football", g);
        }
    }

    /**
     * Pure: which one of the matches or F1 (each with its channels' names) the texts on the screen name, and the text
     * naming it ({key, text}); null when none does, or more than one (a list of channels, not the banner).
     */
    static String[] among(List<String> texts, Map<String, List<String>> on) {
        String[] found = null;
        for (Map.Entry<String, List<String>> e : on.entrySet()) {
            String named = null;
            for (String t : texts) {
                if (t != null && t.length() <= 60 && InStep.onChannel(Collections.singletonList(t), e.getValue())) {
                    named = t.trim();
                    break;
                }
            }
            if (named == null) continue;
            if (found != null) return null;
            found = new String[]{e.getKey(), named};
        }
        return found;
    }

    /** Pure: the key a question is asked once under: a game's id, else F1's on that day ("f1|20261107"). */
    static String askKey(String kind, ScoresTicker.Game g, long now) {
        if ("f1".equals(kind)) return "f1|" + new java.text.SimpleDateFormat("yyyyMMdd", Locale.UK).format(new java.util.Date(now));
        return "g|" + (g == null ? "" : g.id);
    }

    private static boolean asked(Context c, String key) {
        return prefs(c).getStringSet("asked", Collections.emptySet()).contains(key);
    }

    private static void markAsked(Context c, String key) {
        Set<String> was = prefs(c).getStringSet("asked", Collections.emptySet());
        Set<String> now = new HashSet<>(was);
        if (now.size() > 60) now.clear();
        now.add(key);
        prefs(c).edit().putStringSet("asked", now).apply();
    }

    /** The button, if this is being watched with its overlays off, over the player with nothing else of ours up. */
    private static void maybeAsk(Context c, String k, ScoresTicker.Game g) {
        if (win != null || !on(c) || ("football".equals(k) && (g == null || g.id == null))) return;
        String key = askKey(k, g, System.currentTimeMillis());
        if (asked(c, key)) return;
        // Its overlays on already: nothing to ask.
        if ("football".equals(k) && g.id.equals(BoardCover.game(c))) return;
        if ("f1".equals(k) && F1Tower.isShowing()) return;
        if (!BoardCover.playerUp(c) || BoardCover.adjusting() || BoardCover.menuShowing() || ScoreCard.busy()
                || LineupCard.isShowing() || QuietHold.up() || InStepCard.isShowing()) return;
        markAsked(c, key);
        show(c, k, g);
    }

    // ---- the button ------------------------------------------------------------------------------------------------------

    private static void show(Context c, String k, ScoresTicker.Game g) {
        app = c.getApplicationContext();
        android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
        int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
        float scale = sw / 1920f;
        int w = Math.round(200 * scale), h = Math.round(100 * scale);
        OverlayButton b = new OverlayButton(app);
        b.foot("f1".equals(k) ? "TIMING · LOGO" : "SCOREBOARD · LOGO");
        b.remote("streamer".equalsIgnoreCase(Config.themeString(app, "ok_button", "tcl")));
        FrameLayout f = new FrameLayout(app);
        f.addView(b, new FrameLayout.LayoutParams(w, h));
        // Not FLAG_NOT_TOUCHABLE: Android 12 and later turn such a window down to 80% (see BoardCover.params).
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = sw - w - Math.round(sw * 0.026f);
        lp.y = Math.round(sh * 0.04f);
        lp.setTitle("OverlayPrompt");
        try {
            app.getSystemService(WindowManager.class).addView(f, lp);
        } catch (RuntimeException e) {
            AlertLog.note(app, "the overlays button could not show: " + e);
            return;
        }
        win = f;
        button = b;
        kind = k;
        game = g;
        okDown = -1;
        fired = false;
        if (!InputKeyService.running) AppSearchService.keysFor("overlays", true);
        b.setAlpha(0f);
        b.setTranslationX(w * 0.5f);
        b.animate().alpha(1f).translationX(0f).setDuration(320).withLayer().start();
        main().removeCallbacks(hideRun);
        main().postDelayed(hideRun, SHOW_MS);
        AlertLog.note(app, "overlays button up: " + ("f1".equals(k) ? "F1" : AlertLog.game(g)));
    }

    /** Away (its time up, Back, or the overlays on). */
    static void hide() {
        main().removeCallbacks(hideRun);
        main().removeCallbacks(onHold);
        FrameLayout f = win;
        OverlayButton b = button;
        win = null;
        button = null;
        okDown = -1;
        if (f == null) return;
        if (!InputKeyService.running) AppSearchService.keysFor("overlays", false);
        Runnable gone = () -> {
            try {
                f.getContext().getSystemService(WindowManager.class).removeView(f);
            } catch (RuntimeException ignored) {
                // gone already
            }
        };
        if (b == null) {
            gone.run();
            return;
        }
        b.animate().alpha(0f).translationX(b.getWidth() * 0.5f).setDuration(260).withLayer().withEndAction(gone).start();
    }

    /**
     * The remote while the button is up (the channel helper takes the keys for it on the TCL, the key helper has them on
     * the Streamer): OK held turns the overlays on; a short OK goes on to the player on its release; Back puts the button
     * away; the rest go to the player as ever.
     */
    static boolean key(KeyEvent e) {
        if (win == null) return false;
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main().post(OverlayPrompt::hide);
            return true;
        }
        if (!QuietHold.isOk(code)) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getDownTime() == okDown) return true;
            okDown = e.getDownTime();
            fired = false;
            main().removeCallbacks(onHold);
            main().postDelayed(onHold, QuietHold.HOLD_MS);
            OverlayButton b = button;
            if (b != null) main().post(() -> b.holding(true, QuietHold.HOLD_MS));
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP && e.getDownTime() == okDown) {
            main().removeCallbacks(onHold);
            okDown = -1;
            OverlayButton b = button;
            if (b != null) main().post(() -> b.holding(false, 0));
            if (fired || e.isCanceled()) return true;
            // A short OK: the player's, as pressed.
            main().post(AppSearchService::ok);
            return true;
        }
        return false;
    }

    /** OK held: the overlays on, where this channel's broadcaster puts its own. */
    private static void fire() {
        Context c = app;
        if (okDown < 0 || c == null || win == null) return;
        fired = true;
        String channel = InStep.channel(c);
        if ("f1".equals(kind)) {
            BoardCover.setLogoOn(c, true);
            F1Tower.open(c, false);
            AlertLog.note(c, "overlays on (F1) from the button" + (channel.isEmpty() ? "" : ", " + channel));
        } else if (game != null) {
            BoardCover.setBoardShown(c, true);
            BoardCover.setLogoOn(c, true);
            BoardCover.start(c, game);
            AlertLog.note(c, "overlays on from the button: " + AlertLog.game(game) + (channel.isEmpty() ? "" : ", " + channel)
                    + ", placed for " + (BoardPlace.broadcaster(channel).isEmpty() ? "the channel" : BoardPlace.broadcaster(channel)));
        }
        hide();
    }
}
