package com.gathin.tvhome;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * See it again: OK on the goal card of the game being watched (its card comes as the goal shows, InStep) takes the
 * channel back to just before it, with the remote's own rewind (TiviMate rewinds a channel that has catch-up or its
 * own recording of the last minutes, a step at a time), sent through the TV's own debugging connection (LocalAdb,
 * once the TV has allowed it). A bar at the top says it is a replay; OK on it (or a minute and a half) takes the
 * channel forward to live again by as many steps.
 */
final class Replay {
    private Replay() { }

    /** One press of the rewind, as TiviMate steps (its default), and how far before the goal it goes. */
    static final int STEP_S = 10, BEFORE_S = 20;
    private static final long SHOW_MS = 90_000L;
    private static final int REWIND = 89, FORWARD = 90;
    private static Handler main;

    /** The main thread's handler, made when first wanted (the tests use the sums without Android's looper). */
    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static FrameLayout bar;
    private static int steps;
    private static Context app;
    private static final Runnable away = Replay::live;

    /** Whether replays can be sent: the TV has allowed its own debugging connection. */
    static boolean available(Context c) {
        return LocalAdb.trusted(c);
    }

    /** Pure: the presses to go back to BEFORE_S before a goal that showed sinceMs ago (one at least, 18 at most). */
    static int steps(long sinceMs) {
        long s = Math.max(0, sinceMs / 1000) + BEFORE_S;
        return (int) Math.max(1, Math.min(18, (s + STEP_S - 1) / STEP_S));
    }

    /** Pure: the presses as one input command ("input keyevent 89 89 89"). */
    static String command(int key, int n) {
        StringBuilder b = new StringBuilder("input keyevent");
        for (int i = 0; i < n; i++) b.append(' ').append(key);
        return b.toString();
    }

    /** Back to just before a goal that showed sinceMs ago, the bar up (main thread). */
    static void seeAgain(Context c, long sinceMs) {
        if (!available(c)) return;
        app = c.getApplicationContext();
        steps = steps(sinceMs);
        LocalAdb.shell(app, command(REWIND, steps));
        AlertLog.note(app, "see it again: " + steps + " steps back");
        showBar();
    }

    /** Live again: as many steps forward as went back, the bar away. */
    static void live() {
        main().removeCallbacks(away);
        if (app != null && steps > 0) LocalAdb.shell(app, command(FORWARD, steps));
        steps = 0;
        close();
    }

    /** OK on the bar (live again) and Back (the bar away, the replay left playing). */
    static boolean key(KeyEvent e) {
        if (bar == null) return false;
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main().post(() -> { steps = 0; close(); });
            return true;
        }
        if (!QuietHold.isOk(code)) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main().post(Replay::live);
        return true;
    }

    private static void showBar() {
        close();
        Context sw = new GoalWatchService.SoftwareWindow(app);
        float d = app.getResources().getDisplayMetrics().density;
        FrameLayout win = new FrameLayout(sw);
        LinearLayout box = new LinearLayout(sw);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Math.round(18 * d), Math.round(10 * d), Math.round(22 * d), Math.round(10 * d));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF2FFB547);
        g.setCornerRadius(14 * d);
        box.setBackground(g);
        TextView t = new TextView(sw);
        t.setText("REPLAY  ·  OK: back to live  ·  Back: keep watching");
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setTextColor(0xFF1A1203);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t);
        win.addView(box, new FrameLayout.LayoutParams(-2, -2));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Math.round(36 * d);
        lp.y = Math.round(30 * d);
        lp.setTitle("Replay");
        try {
            app.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            return;
        }
        bar = win;
        QuietHold.shown(app, win, () -> { steps = 0; close(); });
        if (!InputKeyService.running) AppSearchService.keysFor("replay", true);
        main().postDelayed(away, SHOW_MS);
    }

    private static void close() {
        main().removeCallbacks(away);
        FrameLayout v = bar;
        bar = null;
        if (v == null) return;
        if (!InputKeyService.running) AppSearchService.keysFor("replay", false);
        try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (RuntimeException ignored) { }
    }
}
