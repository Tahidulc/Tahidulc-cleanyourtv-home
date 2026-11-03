package com.gathin.tvhome;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * The launcher's movement, in the manner of an iPhone's: springs rather than timed curves, so a panel comes out of the
 * thing it was opened from, settles without a jolt and can be turned back half-way; content arriving in a short
 * cascade; leaving the same way backwards, a little quicker. Each movement is on the view's own properties (scale,
 * translation, alpha), drawn into a layer only while it moves, so nothing is laid out again and nothing is left as a
 * layer after.
 */
final class Fluid {
    private Fluid() {
    }

    /** iOS's default spring (response about 0.35 s, a whisper of overshoot), and a firmer one for small things. */
    // Snappy, as Apple's: panels settle in about a quarter of a second with a touch of give, small things quicker
    // and without overshoot (were 380 / 700, half a second and floaty).
    static final float STIFF = 720f, DAMP = 0.88f, STIFF_SMALL = 1300f, DAMP_SMALL = 0.94f;
    /** For the timed parts (fades): quick out of the blocks, long gentle settle. */
    static final PathInterpolator EASE = new PathInterpolator(0.2f, 0.9f, 0.1f, 1f);
    static final PathInterpolator EASE_IN = new PathInterpolator(0.4f, 0f, 0.8f, 0.4f);

    /** The springs running on each view, by property, so a new one on the same property takes over from the last. */
    private static final java.util.Map<View, java.util.Map<DynamicAnimation.ViewProperty, SpringAnimation>> RUNNING =
            new java.util.WeakHashMap<>();

    /** A spring on one property of v to the given end, from where it is now; an earlier one on it is retargeted. */
    static SpringAnimation spring(View v, DynamicAnimation.ViewProperty p, float to, float stiffness, float damping) {
        java.util.Map<DynamicAnimation.ViewProperty, SpringAnimation> mine = RUNNING.get(v);
        if (mine == null) { mine = new java.util.HashMap<>(); RUNNING.put(v, mine); }
        SpringAnimation s = mine.get(p);
        if (s == null) {
            s = new SpringAnimation(v, p);
            s.setSpring(new SpringForce(to));
            mine.put(p, s);
        }
        s.getSpring().setStiffness(stiffness).setDampingRatio(damping);
        // Already moving: it carries on towards the new end with the speed it has (as an iPhone's do when turned back).
        s.animateToFinalPosition(to);
        return s;
    }

    /** The spring on that property of v stopped where it is (before a timed movement takes the property over). */
    static void stop(View v, DynamicAnimation.ViewProperty p) {
        java.util.Map<DynamicAnimation.ViewProperty, SpringAnimation> mine = RUNNING.get(v);
        SpringAnimation s = mine == null ? null : mine.get(p);
        if (s != null && s.isRunning()) s.cancel();
    }

    static SpringAnimation spring(View v, DynamicAnimation.ViewProperty p, float to) {
        return spring(v, p, to, STIFF, DAMP);
    }

    /**
     * A panel coming in: it grows out of the place it was opened from (origin, may be null: from the middle, a little
     * small) to its own size, fading up quickly; held in a layer while it moves.
     */
    static void in(View panel, View origin) {
        if (panel == null) return;
        panel.animate().cancel();
        if (panel.getWidth() == 0 || !panel.isLaidOut()) {
            // Not laid out yet (just added): hidden until it is, then grown out of its origin.
            panel.setAlpha(0f);
            panel.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    panel.getViewTreeObserver().removeOnPreDrawListener(this);
                    grow(panel, origin);
                    return true;
                }
            });
            return;
        }
        grow(panel, origin);
    }

    private static void grow(View panel, View origin) {
        float[] o = originIn(panel, origin);
        panel.setPivotX(o[0]);
        panel.setPivotY(o[1]);
        panel.setScaleX(o[2]);
        panel.setScaleY(o[2]);
        panel.setAlpha(0f);
        layered(panel, 520);
        panel.animate().alpha(1f).setDuration(180).setInterpolator(EASE).start();
        spring(panel, DynamicAnimation.SCALE_X, 1f);
        spring(panel, DynamicAnimation.SCALE_Y, 1f);
    }

    /** A panel leaving: back into where it came from (or a little smaller), fading; gone at the end. */
    static void out(View panel, View origin, Runnable gone) {
        if (panel == null) { if (gone != null) gone.run(); return; }
        float[] o = originIn(panel, origin);
        panel.setPivotX(o[0]);
        panel.setPivotY(o[1]);
        layered(panel, 260);
        panel.animate().cancel();
        panel.animate().alpha(0f).scaleX(Math.max(o[2], 0.9f)).scaleY(Math.max(o[2], 0.9f)).setDuration(200)
                .setInterpolator(EASE_IN).withEndAction(gone).start();
    }

    /**
     * Where a panel grows from: the origin's middle in the panel's own pixels, and the scale it starts at (about the
     * origin's size, never below a quarter); no origin: its own middle, at 0.94.
     */
    private static float[] originIn(View panel, View origin) {
        int pw = panel.getWidth() > 0 ? panel.getWidth() : panel.getResources().getDisplayMetrics().widthPixels;
        int ph = panel.getHeight() > 0 ? panel.getHeight() : panel.getResources().getDisplayMetrics().heightPixels;
        if (origin == null || !origin.isAttachedToWindow() || origin.getWidth() == 0) return new float[]{pw / 2f, ph / 2f, 0.94f};
        int[] a = new int[2], b = new int[2];
        origin.getLocationOnScreen(a);
        panel.getLocationOnScreen(b);
        float cx = a[0] - b[0] + origin.getWidth() / 2f, cy = a[1] - b[1] + origin.getHeight() / 2f;
        float s = Math.max(0.25f, Math.min(0.9f, Math.max(origin.getWidth() / (float) pw, origin.getHeight() / (float) ph)));
        return new float[]{cx, cy, s};
    }

    /** The children of g arriving one after another: up from a little below and faded in, each a moment after the last. */
    static void cascade(ViewGroup g, long startDelay, long step, float rise) {
        if (g == null) return;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE) continue;
            c.setAlpha(0f);
            c.setTranslationY(rise);
            c.animate().alpha(1f).translationY(0).setStartDelay(startDelay + Math.min(i, 6) * Math.min(step, 35)).setDuration(280)
                    .setInterpolator(EASE).withLayer().start();
        }
    }

    /** Drawn into a layer for ms while it moves, then as normal again (a layer kept on a changing view costs more). */
    static void layered(View v, long ms) {
        if (v.getLayerType() == View.LAYER_TYPE_HARDWARE) return;
        v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        v.postDelayed(() -> v.setLayerType(View.LAYER_TYPE_NONE, null), ms);
    }

    /**
     * Lifted off the page (focused) or laid back down: a nudge up on a spring, no shadow (z is no longer used). The
     * shadow it once cast (raised towards the viewer, black) was cut off square at the edge of the row that clips the
     * posters, showed through see-through boxes, and trailed behind as the focus moved along a row.
     */
    static void lift(View v, boolean up, float z, float rise) {
        if (v.getTranslationZ() != 0f) {
            stop(v, DynamicAnimation.TRANSLATION_Z);
            v.setTranslationZ(0f);
        }
        spring(v, DynamicAnimation.TRANSLATION_Y, up ? -rise : 0f, STIFF_SMALL * 0.7f, 0.82f);
    }

    /** The visible rect of a view on the screen (for growing something out of it). */
    static Rect onScreen(View v) {
        int[] a = new int[2];
        v.getLocationOnScreen(a);
        return new Rect(a[0], a[1], a[0] + v.getWidth(), a[1] + v.getHeight());
    }
}
