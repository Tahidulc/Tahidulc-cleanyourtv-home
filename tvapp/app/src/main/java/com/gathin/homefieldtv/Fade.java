package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.gathin.homefieldtv.engine.Exec;

/**
 * The dark screens' animation when the remote moves to another title or channel, kept light for 1 GB TVs: its picture
 * cross-fades in over the last one (about a third of a second; the last one stays until the new one has loaded), and
 * its writing fades in and rises a few pixels into place a moment after. Alpha and a small move only: nothing blurred,
 * every picture drawn at the size it was read. In previews everything is in place at once.
 */
final class Fade {
    static final long PICTURE_MS = 350, TEXT_DELAY_MS = 90, TEXT_MS = 260;
    /** How far below its place the writing starts (design pixels). */
    static final float RISE = 14f;

    private Bitmap shown, leaving;
    private long shownAt = -10_000L, textAt = -10_000L;
    private Object key;

    /** The remote is on something else (k tells them apart): its writing comes in again. */
    void focus(Object k) {
        if (k == null ? key == null : k.equals(key)) return;
        key = k;
        textAt = SystemClock.uptimeMillis();
    }

    /**
     * The picture of what the remote is on: b, or null when it has none. While its picture is still loading (b null,
     * loading true) the last one stays.
     */
    void picture(Bitmap b, boolean loading) {
        if (b == shown || b == null && loading) return;
        leaving = shown;
        shown = b;
        shownAt = SystemClock.uptimeMillis();
    }

    /** Pure: ease-out over 0 to 1. */
    static float ease(float x) {
        float t = Math.max(0f, Math.min(1f, x));
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** How far in the new picture is, 0 to 1. */
    float in() {
        return Exec.inline ? 1f : ease((SystemClock.uptimeMillis() - shownAt) / (float) PICTURE_MS);
    }

    /** The writing's opacity, 0 to 1. */
    float text() {
        return Exec.inline ? 1f : ease((SystemClock.uptimeMillis() - textAt - TEXT_DELAY_MS) / (float) TEXT_MS);
    }

    /** How far below its place the writing still is, in screen pixels. */
    float rise() {
        return (1f - text()) * Ui.f(RISE);
    }

    /** True while anything is still moving: draw again next frame. */
    boolean busy() {
        if (Exec.inline) return false;
        long t = SystemClock.uptimeMillis();
        return t - shownAt < PICTURE_MS || t - textAt < TEXT_DELAY_MS + TEXT_MS;
    }

    boolean hasPicture() {
        return shown != null || leaving != null;
    }

    /** The pictures filling dst (a picture read smaller is drawn larger): the last one going as the new one comes. */
    void draw(Canvas c, RectF dst, Paint p) {
        int full = p.getAlpha();
        float in = in();
        if (leaving != null && in < 1f) {
            p.setAlpha(Math.round(full * (1f - in)));
            c.drawBitmap(leaving, null, dst, p);
        }
        if (in >= 1f) leaving = null;
        if (shown != null) {
            p.setAlpha(Math.round(full * in));
            c.drawBitmap(shown, null, dst, p);
        }
        p.setAlpha(full);
    }

    /** A whole view's writing coming in: faded up and risen into place by the view's own animator (a GPU layer). */
    static void in(View v) {
        v.animate().cancel();
        if (Exec.inline) {
            v.setAlpha(1f);
            v.setTranslationY(0f);
            return;
        }
        v.setAlpha(0f);
        v.setTranslationY(Ui.f(RISE));
        v.animate().alpha(1f).translationY(0f).setStartDelay(TEXT_DELAY_MS).setDuration(TEXT_MS)
                .setInterpolator(EASE).withLayer().start();
    }

    /** A view shown or hidden with a short fade (pages, overlays). */
    static void show(View v, boolean on, long ms) {
        v.animate().cancel();
        if (Exec.inline) {
            v.setAlpha(1f);
            v.setVisibility(on ? View.VISIBLE : View.GONE);
            return;
        }
        if (on) {
            if (v.getVisibility() != View.VISIBLE) {
                v.setAlpha(0f);
                v.setVisibility(View.VISIBLE);
            }
            v.animate().alpha(1f).setStartDelay(0).setDuration(ms).setInterpolator(EASE).withLayer().start();
        } else if (v.getVisibility() == View.VISIBLE) {
            v.animate().alpha(0f).setStartDelay(0).setDuration(ms).setInterpolator(EASE).withLayer()
                    .withEndAction(() -> {
                        v.setVisibility(View.GONE);
                        v.setAlpha(1f);
                    }).start();
        }
    }

    static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.6f);
}
