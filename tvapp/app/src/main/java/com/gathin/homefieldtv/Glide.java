package com.gathin.homefieldtv;

import android.view.animation.AnimationUtils;

import com.gathin.homefieldtv.engine.Exec;

/**
 * A scroll that glides to where the remote sends it, stepped once a frame on the display's own clock (the
 * Choreographer's frame time), so every list moves smoothly and keeps moving smoothly while a key is held:
 *
 * <pre>
 *   Glide g = new Glide(GuideGrid.ROW_MS);   about the time a single step takes (DEVICE.md §5's timings), in design
 *                                            pixels; new Glide(ms, unit) for another unit (unit: its design pixels)
 *   g.to(target)                             where to go: from rest it starts at its quickest and eases out, as the
 *                                            family's TV moves; already moving, it keeps its speed and turns smoothly
 *                                            to the new place (a held key's 20 repeats a second glide on evenly, with
 *                                            no restart and no snap)
 *   g.jump(target)                           there at once (a new list)
 *   g.shift(d)                               the place and where it goes moved together (what is drawn doesn't move)
 *   if (g.step()) postInvalidateOnAnimation();   in onDraw: the frame's step; true while it still moves
 *   g.value()                                where to draw it now
 * </pre>
 *
 * It is a critically damped spring followed exactly (no overshoot, no wobble, the same at 50 and 60 frames a second):
 * a step from rest is the plain ease-out (its speed set to fit the distance), done to within a quarter pixel in about
 * the time given. In previews and tests (Exec.inline) everything is in place at once.
 */
final class Glide {
    /** A step this close to its end (design pixels) is done. */
    static final float NEAR = 0.25f;
    /** Moving slower than this share of a fresh start's speed counts as starting again (a tap after a tap). */
    static final float KICK = 0.25f;

    private final float omega, near;
    private float value, target, velocity;
    /** The last frame's time (ms; 0: at rest, -1: sent off and not stepped yet). */
    private long last;

    /** ms: about how long a single step takes; the values are design pixels. */
    Glide(long ms) {
        this(ms, 1f);
    }

    /** ms: about how long a single step takes; unit: how many design pixels one of its values is. */
    Glide(long ms, float unit) {
        omega = 5f / Math.max(0.016f, ms / 1000f);
        near = NEAR / Math.max(1e-6f, unit);
    }

    float value() {
        return value;
    }

    float target() {
        return target;
    }

    /** Still moving (draw again next frame). */
    boolean moving() {
        return last != 0;
    }

    /** There at once. */
    void jump(float to) {
        value = target = to;
        velocity = 0f;
        last = 0;
    }

    /** Sent to a new place (see the class's note). */
    void to(float to) {
        if (Exec.inline) {
            jump(to);
            return;
        }
        if (to == target && last == 0) return;
        float e = to - value;
        // From rest (or a tap after a tap that has nearly finished): its quickest start, easing out from there. Moving
        // already (a held key): its speed kept, the spring turning it smoothly.
        if (last == 0 || velocity * e <= 0f || Math.abs(velocity) < KICK * omega * Math.abs(e)) {
            float kick = omega * e;
            velocity = e > 0 ? Math.max(velocity, kick) : Math.min(velocity, kick);
        }
        target = to;
        if (last == 0) last = -1;
    }

    /** The place and where it goes moved together by d: nothing drawn moves (a new start for the same scroll). */
    void shift(float d) {
        value += d;
        target += d;
    }

    /** One frame's step, on the frame's own time; true while it still moves. */
    boolean step() {
        return step(AnimationUtils.currentAnimationTimeMillis());
    }

    /** One frame's step to time now (ms); true while it still moves. */
    boolean step(long now) {
        if (last == 0) return false;
        if (Exec.inline) {
            jump(target);
            return false;
        }
        // The first frame of a move shows one frame's worth of it (nothing is lost to the frame the key came in).
        float dt = last < 0 ? 0.016f : Math.max(0f, Math.min(0.048f, (now - last) / 1000f));
        last = Math.max(1L, now);
        float e0 = value - target, v0 = velocity;
        float ex = (float) Math.exp(-omega * dt);
        float c = v0 + omega * e0;
        float e1 = (e0 + c * dt) * ex;
        float v1 = (v0 - omega * c * dt) * ex;
        value = target + e1;
        velocity = v1;
        // Done: under a quarter pixel from its place and slower than a pixel a frame or so.
        if (Math.abs(e1) < near && Math.abs(v1) < 2f * omega * near) {
            jump(target);
            return false;
        }
        return true;
    }
}
