package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import com.gathin.tvhomephone.glass.Soften;

/**
 * The TV section's deep, softly lit ground (07's backdrop): near-black, with what is on glowing through it. While a
 * channel plays in the guide's picture, that picture: a copy a few dozen pixels across, made smooth and blurred once
 * (never a live blur), saturated and dimmed, filling the page from the top. Otherwise the chosen channel's own colour
 * (its logo's, LogoTiles) as two soft glows, warm at the top left and lower on the right. Each change fades in over the
 * last (FADE_MS); a shade darkens it toward the foot so writing stays clear. Nothing is made while drawing: the glows'
 * shaders are made when the colour or the size changes.
 */
final class TvGround extends View {
    private static final long FADE_MS = TvLook.FADE_MS;
    /** The picture's copy: this small, then made smooth at four times the size. */
    static final int COPY_W = 48, COPY_H = 27;
    private final Paint pic = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG), shade = new Paint(Paint.DITHER_FLAG),
            glowA = new Paint(Paint.DITHER_FLAG), glowB = new Paint(Paint.DITHER_FLAG), oldA = new Paint(Paint.DITHER_FLAG),
            oldB = new Paint(Paint.DITHER_FLAG);
    private final RectF dst = new RectF();
    private final Rect all = new Rect();
    private Bitmap now, was;
    private long fadeAt, tintAt;
    private int tint, oldTint;
    private boolean hasTint, hadTint;

    TvGround(Context c) {
        super(c);
        // 07's backdrop: saturate(1.75) brightness(.86), a touch dimmer here (a phone is held close).
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(1.6f);
        ColorMatrix dim = new ColorMatrix();
        dim.setScale(0.62f, 0.62f, 0.62f, 1f);
        m.postConcat(dim);
        pic.setColorFilter(new ColorMatrixColorFilter(m));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The chosen channel's colour (its logo's row colour), glowing when no picture is in hand; 0 for none. */
    void setTint(int argb) {
        if (hasTint && argb == tint || !hasTint && argb == 0) return;
        oldTint = tint;
        hadTint = hasTint;
        tint = argb;
        hasTint = argb != 0;
        tintAt = SystemClock.uptimeMillis();
        glows(getWidth(), getHeight());
        postInvalidateOnAnimation();
    }

    /** The picture playing, already made smooth (smooth()): faded in over the last; null lets it go. */
    void setPicture(Bitmap smooth) {
        if (smooth == now) return;
        was = now;
        now = smooth;
        fadeAt = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    boolean hasPicture() {
        return now != null;
    }

    /**
     * A small copy of the picture (COPY_W by COPY_H) made smooth: drawn four times larger with filtering, blurred, on the
     * ground. Off the main thread (the caller's worker).
     */
    static Bitmap smooth(Bitmap tiny) {
        if (tiny == null) return null;
        int w = Math.max(2, tiny.getWidth() * 4), h = Math.max(2, tiny.getHeight() * 4);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.drawColor(TvLook.GROUND);
        cv.drawBitmap(tiny, null, new Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
        Soften.blur(out, 9);
        return out;
    }

    /** Whether a small copy is all but black (a channel starting, a blank frame): no picture to glow. */
    static boolean dark(Bitmap b) {
        if (b == null) return true;
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        return dark(px);
    }

    /** Pure: the same on packed pixels (their average light under 10 of 255). */
    static boolean dark(int[] px) {
        if (px == null || px.length == 0) return true;
        long sum = 0;
        for (int p : px) sum += (((p >> 16) & 0xFF) * 77 + ((p >> 8) & 0xFF) * 150 + (p & 0xFF) * 29) >> 8;
        return sum / px.length < 10;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        glows(w, h);
        if (h > 0) {
            int g = TvLook.GROUND & 0xFFFFFF;
            // 07's .dim: deeper at the top's very edge, clearest a third of the way down, deepest at the foot.
            shade.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x80000000 | g, 0x5C000000 | g, 0x99000000 | g, 0xDB000000 | g},
                    new float[]{0f, 0.3f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        }
    }

    private void glows(int w, int h) {
        if (w <= 0 || h <= 0) return;
        set(oldA, oldB, oldTint, hadTint, w, h);
        set(glowA, glowB, tint, hasTint, w, h);
    }

    private static void set(Paint a, Paint b, int colour, boolean on, int w, int h) {
        int rgb = colour & 0xFFFFFF;
        if (!on) {
            // No channel yet: 07's quiet default, a warm glow and a cool one.
            a.setShader(new RadialGradient(w * 0.12f, h * 0.06f, Math.max(w, h) * 0.8f, 0x6E6B4A2E, 0x006B4A2E, Shader.TileMode.CLAMP));
            b.setShader(new RadialGradient(w * 1.02f, h * 0.42f, Math.max(w, h) * 0.7f, 0x4A2E4E5C, 0x002E4E5C, Shader.TileMode.CLAMP));
            return;
        }
        a.setShader(new RadialGradient(w * 0.18f, h * 0.04f, Math.max(w, h) * 0.85f, 0xA6000000 | rgb, rgb, Shader.TileMode.CLAMP));
        b.setShader(new RadialGradient(w * 0.95f, h * 0.48f, Math.max(w, h) * 0.6f, 0x66000000 | rgb, rgb, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        cv.drawColor(TvLook.GROUND);
        long t = SystemClock.uptimeMillis();
        float ft = Math.min(1f, (t - tintAt) / (float) FADE_MS), fp = now == null && was == null ? 1f : Math.min(1f, (t - fadeAt) / (float) FADE_MS);
        all.set(0, 0, w, h);
        // The colour's glows (under the picture, so they show while it fades in, and where it is not).
        if (ft < 1f) {
            oldA.setAlpha(Math.round(255 * (1 - ft)));
            oldB.setAlpha(oldA.getAlpha());
            cv.drawRect(all, oldA);
            cv.drawRect(all, oldB);
        }
        glowA.setAlpha(Math.round(255 * ft));
        glowB.setAlpha(glowA.getAlpha());
        cv.drawRect(all, glowA);
        cv.drawRect(all, glowB);
        // The picture over the top of the page, wider than it so its soft edges never show.
        float ph = Math.max(h * 0.75f, w * 0.75f);
        dst.set(-w * 0.12f, -h * 0.04f, w * 1.12f, ph);
        if (was != null && fp < 1f) {
            pic.setAlpha(Math.round(255 * (1 - fp)));
            cv.drawBitmap(was, null, dst, pic);
        }
        if (now != null) {
            pic.setAlpha(Math.round(255 * fp));
            cv.drawBitmap(now, null, dst, pic);
        }
        cv.drawRect(all, shade);
        if (ft < 1f || fp < 1f) postInvalidateOnAnimation();
        else was = null;
    }
}
