package com.gathin.homefieldtv;

import android.view.animation.AnimationUtils;

import com.gathin.homefieldtv.engine.Exec;

/**
 * A value easing towards where it should be, a step each frame the view is drawn (postInvalidateOnAnimation, so on
 * Choreographer's frames and its frame time). A key sets a new target and it carries on from where it is, never starting
 * again: a key held down moves it on smoothly however fast the repeats come, where an animator restarted each time would
 * stutter. Nothing is made as it runs.
 */
final class Smooth {
    /** Where it is now, and where it is going. */
    float value, target;
    private final float tau, eps;
    private long last;

    /** tauMs: how quickly it closes in (about two thirds of the way in that long). Pixels: it arrives within a third of one. */
    Smooth(float tauMs) {
        this(tauMs, -1f);
    }

    /** The same, arriving once within eps of where it is going (a place in a row, a share of a fade). */
    Smooth(float tauMs, float eps) {
        this.tau = Math.max(1f, tauMs);
        this.eps = eps;
    }

    /** Going to t from where it is. */
    void to(float t) {
        target = t;
    }

    /** At t at once. */
    void jump(float t) {
        value = target = t;
        last = 0;
    }

    /** True once it has arrived. */
    boolean settled() {
        return value == target;
    }

    /** One frame's step; true while it is still moving (ask for another frame). */
    boolean step() {
        if (value == target) {
            last = 0;
            return false;
        }
        if (Exec.inline) {
            value = target;
            last = 0;
            return false;
        }
        long now = AnimationUtils.currentAnimationTimeMillis();
        float dt = last == 0 ? 16f : Math.max(1f, Math.min(48f, now - last));
        last = now;
        float k = 1f - (float) Math.exp(-dt / tau);
        value += (target - value) * k;
        float gap = Math.abs(target - value);
        boolean there = eps > 0 ? gap < eps : gap < 0.35f && Math.abs(target) > 2f || gap < 0.002f;
        if (there) {
            value = target;
            last = 0;
            return false;
        }
        return true;
    }
}
