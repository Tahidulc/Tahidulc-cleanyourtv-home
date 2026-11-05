package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * Keeps what stays on the screen from burning into a phone's or tablet's OLED panel: the scoreboard, Homefield's mark
 * over the channel's logo, the scoreboard over other apps. Once a minute they move a pixel or two round a slow circle
 * (never far enough to uncover what they cover), and after five minutes without a change (a goal, an alert, a touch)
 * they dim a little, solid still, brightening again as soon as something happens. On until switched off in Settings.
 */
final class BurnGuard {
    private static final long STEP_MS = 60_000L, DIM_AFTER_MS = 5 * 60_000L;
    /** How dim: the colours at this share of their brightness, never see-through. */
    private static final float DIM = 0.72f;

    /** A view that dims itself (solid, darker). */
    interface Dims {
        void dim(boolean on);
    }

    static boolean on(Context c) {
        return c.getApplicationContext().getSharedPreferences("score_overlay", Context.MODE_PRIVATE).getBoolean("burn", true);
    }

    static void setOn(Context c, boolean v) {
        c.getApplicationContext().getSharedPreferences("score_overlay", Context.MODE_PRIVATE).edit().putBoolean("burn", v).apply();
    }

    /** Pure: step n's place on the circle, {x, y} each -1, 0 or 1: nine places, back to the start. */
    static int[] orbit(int n) {
        int[][] path = {{0, 0}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        int[] p = path[((n % path.length) + path.length) % path.length];
        return new int[]{p[0], p[1]};
    }

    /** Pure: whether it is dimmed, changedAt ms ago... at now. */
    static boolean dimmed(long changedAt, long now) {
        return now - changedAt >= DIM_AFTER_MS;
    }

    private final Context c;
    private final View[] moved;
    private final Dims[] dimmed;
    private final float radius;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int step;
    private long changedAt;
    private boolean dim, running;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            h.removeCallbacks(this);
            if (!running) return;
            if (!on(c)) {
                reset();
            } else {
                step++;
                int[] o = orbit(step);
                for (View v : moved) if (v != null) v.animate().translationX(o[0] * radius).translationY(o[1] * radius).setDuration(1500).start();
                setDim(dimmed(changedAt, System.currentTimeMillis()));
            }
            h.postDelayed(this, STEP_MS);
        }
    };

    /** moved: the views moved round the circle (radiusDp each way); dimmed: those that dim. */
    BurnGuard(Context c, float radiusDp, View[] moved, Dims[] dimmed) {
        this.c = c;
        this.moved = moved;
        this.dimmed = dimmed;
        this.radius = radiusDp * c.getResources().getDisplayMetrics().density;
    }

    void start() {
        running = true;
        changedAt = System.currentTimeMillis();
        h.removeCallbacks(tick);
        h.postDelayed(tick, STEP_MS);
    }

    void stop() {
        running = false;
        h.removeCallbacks(tick);
        reset();
    }

    /** Something changed on it (a goal, an alert, a touch): bright again, and five more minutes before it dims. */
    void poke() {
        changedAt = System.currentTimeMillis();
        if (dim) setDim(false);
    }

    private void reset() {
        for (View v : moved) {
            if (v == null) continue;
            v.animate().cancel();
            v.setTranslationX(0);
            v.setTranslationY(0);
        }
        setDim(false);
    }

    private void setDim(boolean on) {
        if (on == dim) return;
        dim = on;
        for (Dims d : dimmed) if (d != null) d.dim(on);
    }

    /** A view dimmed through its layer (solid, darker): for OverlayBug and LogoCover. */
    static void dimLayer(View v, boolean on) {
        if (!on) {
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            return;
        }
        ColorMatrix m = new ColorMatrix();
        m.setScale(DIM, DIM, DIM, 1f);
        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(m));
        v.setLayerType(View.LAYER_TYPE_HARDWARE, p);
    }
}
