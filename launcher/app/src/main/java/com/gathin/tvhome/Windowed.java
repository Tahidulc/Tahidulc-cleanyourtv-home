package com.gathin.tvhome;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.function.Consumer;

/**
 * One app in a window of its own over whatever is on (Android's freeform windows, through the TV's own debugging
 * connection, as Split screen opens its two): only on a TV whose system allows windows (the Google TV Streamer; the
 * TCL's has them switched off, TwoUp's check). Match night puts TiviMate in the Live Centre's picture, and the
 * doorbell's live view goes in a corner when someone rings.
 */
final class Windowed {
    private Windowed() { }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String FREEFORM = "settings put global enable_freeform_support 1; settings put global force_resizable_activities 1; ";

    /** This TV puts apps in windows (Split screen's check said yes). */
    static boolean supported(Context c) {
        return TwoUp.support(c) > 0;
    }

    /** A rectangle in 1920 x 1080 terms, on this screen. */
    static int[] rect(Context c, int l, int t, int r, int b) {
        float k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        return new int[]{Math.round(l * k), Math.round(t * k), Math.round(r * k), Math.round(b * k)};
    }

    /**
     * pkg in a window at r: a task of its own already in a window is moved there; one full screen is closed and opened
     * again in a window (it comes back where it was: TiviMate on its last channel). done gets the task's id, or -1.
     */
    static void open(Context c, String pkg, int[] r, Consumer<Integer> done) {
        ComponentName comp = TwoUp.launch(c.getPackageManager(), pkg);
        if (comp == null) { done.accept(-1); return; }
        LocalAdb.shellOut(c, FREEFORM + TwoUp.LIST, out -> {
            if (out == null) { done.accept(-1); return; }
            int[] t = TwoUp.taskOf(out, pkg);
            StringBuilder cmd = new StringBuilder();
            if (t != null && t[1] == 0) cmd.append("am stack remove ").append(t[0]).append("; ");
            if (t == null || t[1] == 0) cmd.append("am start --windowingMode 5 -n ").append(comp.flattenToShortString()).append(" >/dev/null 2>&1; sleep 1; ");
            cmd.append(TwoUp.LIST);
            LocalAdb.shellOut(c, cmd.toString(), got -> {
                int[] now = got == null ? null : TwoUp.taskOf(got, pkg);
                if (now == null || now[1] == 0) { done.accept(-1); return; }
                resize(c, now[0], r);
                done.accept(now[0]);
            });
        });
    }

    /**
     * A notification's tap (the doorbell's live view for this ring) opened straight into a window at r, where the
     * TV allows it; then checked: done gets the task's id in a window, or -1 when it came up full screen (as an answer).
     */
    static void openTap(Context c, PendingIntent tap, String pkg, int[] r, Consumer<Integer> done) {
        LocalAdb.shell(c, FREEFORM.trim());
        try {
            ActivityOptions o = ActivityOptions.makeBasic();
            o.setLaunchBounds(new Rect(r[0], r[1], r[2], r[3]));
            if (Build.VERSION.SDK_INT >= 34) o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            tap.send(c, 0, null, null, null, null, o.toBundle());
        } catch (Exception e) {
            done.accept(-1);
            return;
        }
        MAIN.postDelayed(() -> LocalAdb.shellOut(c, TwoUp.LIST, out -> {
            int[] t = out == null ? null : TwoUp.taskOf(out, pkg);
            if (t == null || t[1] == 0) { done.accept(-1); return; }
            resize(c, t[0], r);
            done.accept(t[0]);
        }), 1_800L);
    }

    static void resize(Context c, int task, int[] r) {
        if (task < 0) return;
        LocalAdb.shell(c, "am task resize " + task + " " + r[0] + " " + r[1] + " " + r[2] + " " + r[3]);
    }

    /** The window as big as the screen (the app goes on as it is). */
    static void full(Context c, int task) {
        int w = c.getResources().getDisplayMetrics().widthPixels, h = c.getResources().getDisplayMetrics().heightPixels;
        resize(c, task, new int[]{0, 0, w, h});
    }

    /** The window's app closed (its task removed). */
    static void close(Context c, int task) {
        if (task >= 0) LocalAdb.shell(c, "am stack remove " + task);
    }
}
