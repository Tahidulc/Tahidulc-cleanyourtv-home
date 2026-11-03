package com.gathin.tvhome;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Option B on Home: an alert's bar grows out of the score pill and shrinks back into it. The bar's window is clipped to
 * a rounded rectangle that starts as the pill (its place and size, in the window's own pixels) and opens out to the
 * bar's whole size, its words fading in once it has; closing runs the other way, the words going first and the bar
 * folding back into the pill, which then shows again in its place.
 */
final class PillMorph {
    private PillMorph() {
    }

    /** The score pill on the screen, in pixels (HomeV2's option B sets it; left -1: none). */
    static volatile int pillLeft = -1, pillTop, pillRight, pillBottom;

    static void pill(int left, int top, int right, int bottom) {
        pillLeft = left;
        pillTop = top;
        pillRight = right;
        pillBottom = bottom;
    }

    /** Whether a bar on Home grows out of the pill: option B's top bar, not pop-up mode. */
    static boolean on() {
        return pillLeft >= 0 && pillRight > pillLeft && ScoresTicker.corner();
    }

    private static final long OPEN_MS = 460, WORDS_MS = 240, CLOSE_MS = 340;

    /** The corner, h high: square-edged under the Sky top bar (as the cards over the apps), else round-ended. */
    static volatile float squareCorner = -1;

    private static float radius(int h) {
        return squareCorner >= 0 ? squareCorner : h / 2f;
    }
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final PathInterpolator EASE_IN = new PathInterpolator(0.4f, 0f, 0.7f, 0.2f);

    /** The clip: a rounded rectangle between the pill's and the bar's own. */
    private static final class Shape extends ViewOutlineProvider {
        final float[] from = new float[5], to = new float[5];
        float t;

        @Override public void getOutline(View v, Outline o) {
            float l = mix(0), top = mix(1), r = mix(2), b = mix(3), radius = mix(4);
            o.setRoundRect(Math.round(l), Math.round(top), Math.round(r), Math.round(b), radius);
        }

        private float mix(int i) {
            return from[i] + (to[i] - from[i]) * t;
        }
    }

    /** The words and pictures inside the bar (the box's own children), which wait until the bar has opened. */
    private static List<View> words(View win) {
        List<View> out = new ArrayList<>();
        if (!(win instanceof ViewGroup) || ((ViewGroup) win).getChildCount() == 0) return out;
        View box = ((ViewGroup) win).getChildAt(0);
        if (box instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) box;
            for (int i = 0; i < g.getChildCount(); i++) out.add(g.getChildAt(i));
        }
        return out;
    }

    /** The pill in the window's own pixels {left, top, right, bottom, radius}, the window at (x, y) on the screen. */
    private static void pillIn(float[] into, int x, int y) {
        into[0] = pillLeft - x;
        into[1] = pillTop - y;
        into[2] = pillRight - x;
        into[3] = pillBottom - y;
        into[4] = radius(pillBottom - pillTop);
    }

    /**
     * The bar's window (just added at screen (x, y)) opens out of the pill: hidden until it is laid out, then clipped
     * to the pill and grown to its own size, its words fading in at the end. Nothing happens without option B.
     */
    static void open(View win, int x, int y) {
        if (!on() || win == null) return;
        win.setAlpha(0f);
        List<View> inside = words(win);
        for (View v : inside) v.setAlpha(0f);
        win.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                win.getViewTreeObserver().removeOnPreDrawListener(this);
                int w = win.getWidth(), h = win.getHeight();
                Shape s = new Shape();
                pillIn(s.from, x, y);
                s.to[0] = 0;
                s.to[1] = 0;
                s.to[2] = w;
                s.to[3] = h;
                s.to[4] = radius(h);
                win.setOutlineProvider(s);
                win.setClipToOutline(true);
                win.setAlpha(1f);
                android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
                va.setDuration(OPEN_MS);
                va.setInterpolator(EASE);
                va.addUpdateListener(a -> {
                    s.t = (float) a.getAnimatedValue();
                    win.invalidateOutline();
                });
                va.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator a) {
                        // Opened: back to the bar's own rounded box (its children clip themselves).
                        win.setClipToOutline(false);
                        win.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                    }
                });
                va.start();
                for (View v : inside) v.animate().alpha(1f).setStartDelay(OPEN_MS * 3 / 5).setDuration(WORDS_MS).start();
                return true;
            }
        });
    }

    /**
     * The bar folds back into the pill: its words fade first, then it shrinks to the pill's place and size; gone runs
     * at the end (it takes the window down and gives the pill back). False without option B (the caller closes it its
     * own way).
     */
    static boolean close(View win, Runnable gone) {
        if (!on() || win == null || !win.isAttachedToWindow() || win.getWidth() == 0) return false;
        android.view.WindowManager.LayoutParams lp;
        try {
            lp = (android.view.WindowManager.LayoutParams) win.getLayoutParams();
        } catch (ClassCastException e) {
            return false;
        }
        int[] at = new int[2];
        win.getLocationOnScreen(at);
        Shape s = new Shape();
        s.from[0] = 0;
        s.from[1] = 0;
        s.from[2] = win.getWidth();
        s.from[3] = win.getHeight();
        s.from[4] = radius(win.getHeight());
        pillIn(s.to, at[0], at[1]);
        win.animate().cancel();
        win.setAlpha(1f);
        win.setScaleX(1f);
        win.setScaleY(1f);
        win.setTranslationX(0);
        win.setTranslationY(0);
        win.setOutlineProvider(s);
        win.setClipToOutline(true);
        for (View v : words(win)) v.animate().alpha(0f).setStartDelay(0).setDuration(140).start();
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
        va.setStartDelay(110);
        va.setDuration(CLOSE_MS);
        va.setInterpolator(EASE_IN);
        va.addUpdateListener(a -> {
            s.t = (float) a.getAnimatedValue();
            win.invalidateOutline();
        });
        final boolean[] done = {false};
        Runnable end = () -> {
            if (done[0]) return;
            done[0] = true;
            gone.run();
        };
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                end.run();
            }
        });
        va.start();
        // An interrupted animation never reports its end: the window goes all the same.
        win.postDelayed(end, 110 + CLOSE_MS + 400);
        return lp != null;
    }
}
