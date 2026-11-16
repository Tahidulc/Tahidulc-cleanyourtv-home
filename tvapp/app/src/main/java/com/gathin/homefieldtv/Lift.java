package com.gathin.homefieldtv;

import android.os.SystemClock;

import com.gathin.homefieldtv.engine.Exec;

/**
 * How lifted the tile the remote is on is, and the one it left: the new one grows into its lift and ring as the old one
 * settles back, in about a sixth of a second. Asked once a frame by the view drawing them.
 */
final class Lift {
    private Object on, was;
    private float up = 1f, down;
    private long last;

    /** The frame's step, with what the remote is on now; true while either is still moving. */
    boolean step(Object focus) {
        long t = SystemClock.uptimeMillis();
        float dt = last == 0 ? 16f : Math.min(48f, t - last);
        last = t;
        if (focus != on) {
            was = on;
            down = was == null ? 0f : up;
            on = focus;
            up = 0f;
        }
        if (Exec.inline) {
            up = 1f;
            down = 0f;
            was = null;
            last = 0;
            return false;
        }
        float k = 1f - (float) Math.exp(-dt / 55.0);
        boolean moving = false;
        if (up < 0.995f) {
            up += (1f - up) * k;
            moving = true;
        } else {
            up = 1f;
        }
        if (down > 0.005f) {
            down -= down * k;
            moving = true;
        } else {
            down = 0f;
            was = null;
        }
        if (!moving) last = 0;
        return moving;
    }

    /** An item's lift, 0 (resting) to 1 (where the remote is). */
    float of(Object item) {
        if (item == null) return 0f;
        if (item == on) return up;
        return item == was ? down : 0f;
    }

    /** The one the remote just left, still settling (or null). */
    Object leaving() {
        return was;
    }
}
