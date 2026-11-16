package com.gathin.tvhomephone.glass;

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
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

/**
 * The glow behind a glass page, as the reference has it: the page's picture (a backdrop, a poster) read tiny, made
 * smooth, dimmed and a little richer, filling the top of the page and fading into the near-black ground; a new picture
 * fades in over the last. With no picture yet, a warm glow at the top left and a cool one on the right. It drifts up
 * slowly as the page scrolls (shift).
 */
public class AmbientView extends View {
    private static final long FADE_MS = 900;
    private final Paint pic = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint shade = new Paint(Paint.DITHER_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF dst = new RectF();
    private Bitmap now, was;
    private long fadeAt;
    private String url;
    private float shift;
    private int shadedFor = -1;
    private final Rect frame = new Rect();

    public AmbientView(Context c) {
        super(c);
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(1.3f);
        ColorMatrix dim = new ColorMatrix();
        dim.setScale(0.66f, 0.66f, 0.66f, 1f);
        m.postConcat(dim);
        pic.setColorFilter(new ColorMatrixColorFilter(m));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The page's picture (a URL), read tiny and faded in over the last; the same one again does nothing. */
    public void setImage(String u) {
        if (u == null || u.isEmpty() || u.equals(url)) return;
        url = u;
        try {
            Glide.with(this).asBitmap().load(u).override(32, 18).centerCrop().disallowHardwareConfig()
                    .into(new CustomTarget<Bitmap>() {
                        @Override public void onResourceReady(@NonNull Bitmap b, @Nullable Transition<? super Bitmap> t) {
                            if (u.equals(url)) setBitmap(b);
                        }

                        @Override public void onLoadCleared(@Nullable Drawable placeholder) { }
                    });
        } catch (RuntimeException ignored) {
            // the page went away
        }
    }

    /** A picture already in hand (a test's, or one made here), smooth or tiny: faded in over the last. */
    public void setBitmap(Bitmap b) {
        if (b == null) return;
        // A tiny one is made smooth first; a large one is read small, as a URL's would be.
        if (b.getWidth() > 64) b = Bitmap.createScaledBitmap(b, 32, 18, true);
        if (b.getWidth() <= 64) b = smooth(b);
        was = now;
        now = b;
        fadeAt = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    /** How far the page has scrolled: the glow drifts up at a third of it. */
    public void setShift(float scrolled) {
        float s = Math.max(0f, scrolled) * 0.33f;
        if (s == shift) return;
        shift = s;
        invalidate();
    }

    /** A tiny picture made smooth: drawn four times larger with filtering, then blurred. */
    static Bitmap smooth(Bitmap tiny) {
        int w = Math.max(2, tiny.getWidth() * 4), h = Math.max(2, tiny.getHeight() * 4);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.drawColor(Glass.GROUND);
        cv.drawBitmap(tiny, null, new Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
        Soften.blur(out, 7);
        return out;
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        cv.drawColor(Glass.GROUND);
        float f = now == null ? 0f : Math.min(1f, (SystemClock.uptimeMillis() - fadeAt) / (float) FADE_MS);
        // The picture fills the top of the page, wider than it so its edges never show, drifting up with the scroll.
        float ph = Math.max(h * 0.72f, w * 1.15f);
        dst.set(-w * 0.1f, -h * 0.04f - shift, w * 1.1f, ph - shift);
        if (now == null) {
            defaults(cv, w, h);
        } else {
            if (was != null && f < 1f) {
                pic.setAlpha(255);
                cv.drawBitmap(was, null, dst, pic);
            } else if (was == null && f < 1f) {
                defaults(cv, w, h);
            }
            pic.setAlpha(Math.round(255 * f));
            cv.drawBitmap(now, null, dst, pic);
        }
        if (shadedFor != h) {
            shadedFor = h;
            shade.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0x22000000 | (Glass.GROUND & 0xFFFFFF), 0x55000000 | (Glass.GROUND & 0xFFFFFF),
                            0xC8000000 | (Glass.GROUND & 0xFFFFFF), 0xF6000000 | (Glass.GROUND & 0xFFFFFF)},
                    new float[]{0f, 0.3f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        }
        frame.set(0, 0, w, h);
        cv.drawRect(frame, shade);
        if (now != null && f < 1f) postInvalidateOnAnimation();
        else if (f >= 1f) was = null;
    }

    /** No picture yet: a warm glow at the top left and a cool one lower on the right, as in the reference. */
    private void defaults(Canvas cv, int w, int h) {
        float top = -shift;
        glow.setShader(new RadialGradient(w * 0.12f, top + h * 0.08f, w * 0.95f, 0x7A6B4A2E, 0x006B4A2E, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, glow);
        glow.setShader(new RadialGradient(w * 1.02f, top + h * 0.42f, w * 0.85f, 0x4A2E4E5C, 0x002E4E5C, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, glow);
    }
}
