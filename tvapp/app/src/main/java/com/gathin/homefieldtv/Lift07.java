package com.gathin.homefieldtv;

/**
 * How lifted the card the remote is on is, and the one it left, on 07's springy Glide (Look07.LIFT_MS, BOUNCE): the
 * new one lifts toward you with the mock-ups' small overshoot as the old one settles back, each from wherever it was
 * (back and forth quickly, a card turns smoothly, never jumping). Asked once a frame by the view drawing the cards; in
 * previews and tests everything is in place at once.
 *
 * <pre>
 *   Lift07 lift = new Lift07();                        one per row (or screen), kept with the view
 *   if (lift.step(focusedItem)) postInvalidateOnAnimation();   once a frame, with what the remote is on (null: none)
 *   float up = lift.of(item);                          0 resting, 1 lifted (a little past 1 as it overshoots)
 * </pre>
 */
final class Lift07 {
    private Object on, was;
    private Glide up = spring(), down = spring();

    private static Glide spring() {
        Glide g = new Glide(Look07.LIFT_MS, 100f).springy(Look07.BOUNCE, Look07.BOUNCE_KICK);
        g.jump(0f);
        return g;
    }

    /** The frame's step, with what the remote is on now; true while either is still moving. */
    boolean step(Object focus) {
        if (focus != on) {
            if (focus != null && focus == was) {
                // Back to the one it just left: its own lift turns round.
                Glide t = up;
                up = down;
                down = t;
                was = on;
            } else {
                // The one left settles from wherever it was; the new one lifts from rest.
                Glide t = down;
                down = up;
                up = t;
                up.jump(0f);
                was = on;
            }
            on = focus;
            up.to(focus == null ? 0f : 1f);
            down.to(0f);
        }
        boolean a = up.step(), b = down.step();
        if (!b && was != null && Math.abs(down.value()) < 0.001f) was = null;
        return a || b;
    }

    /** An item's lift: 0 resting to 1 where the remote is (overshooting a little on its way). */
    float of(Object item) {
        if (item == null) return 0f;
        if (item == on) return up.value();
        return item == was ? down.value() : 0f;
    }

    /** What the remote is on, as last stepped. */
    Object on() {
        return on;
    }
}
