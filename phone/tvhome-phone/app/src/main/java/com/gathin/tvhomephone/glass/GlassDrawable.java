package com.gathin.tvhomephone.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * A piece of glass: a faint white wash, brighter at the top, a soft sheen over its upper half, and a fine rim that is
 * bright where the light falls (top left) and catches again at the bottom right, as a lens would. A card lets the page's
 * picture through; a bar is a touch brighter (it sits over a blur); the lens marks what is chosen and brightens while a
 * finger moves it (lift); the rim alone goes over a picture.
 */
public final class GlassDrawable extends Drawable {
    public static final int CARD = 0, BAR = 1, LENS = 2, RIM = 3;
    private final int kind;
    private final float radius, line;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG),
            rim = new Paint(Paint.ANTI_ALIAS_FLAG), inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF(), edge = new RectF(), in = new RectF();
    private float lift;
    private int alpha = 255;

    public GlassDrawable(Context c, float radiusDp, int kind) {
        float d = c.getResources().getDisplayMetrics().density;
        this.kind = kind;
        radius = radiusDp * d;
        line = Math.max(1f, d);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(line);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(line);
        inner.setColor(0x12FFFFFF);
    }

    /** 0 at rest, 1 chosen or under a finger: the wash and the rim brighter. */
    public void setLift(float v) {
        float l = Math.max(0f, Math.min(1f, v));
        if (l == lift) return;
        lift = l;
        shaders();
        invalidateSelf();
    }

    public float radius() {
        return radius;
    }

    @Override protected void onBoundsChange(@NonNull Rect b) {
        shaders();
    }

    private static int white(int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | 0xFFFFFF;
    }

    private int mix(int a, int b) {
        return Math.round(a + (b - a) * lift);
    }

    private void shaders() {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        int top, bottom;
        switch (kind) {
            case BAR: top = white(0x24); bottom = white(0x12); break;
            case LENS: top = white(mix(0x2E, 0x44)); bottom = white(mix(0x1A, 0x26)); break;
            case RIM: top = 0; bottom = 0; break;
            default: top = white(mix(0x1F, 0x33)); bottom = white(mix(0x0C, 0x1A));
        }
        fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, bottom, Shader.TileMode.CLAMP));
        int a0 = mix(kind == RIM ? 0x55 : 0x7A, 0xB8), a3 = mix(kind == RIM ? 0x26 : 0x3A, 0x6A);
        rim.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[]{white(a0), white(0x1C), white(0x0E), white(a3)}, new float[]{0f, 0.36f, 0.64f, 1f}, Shader.TileMode.CLAMP));
        sheen.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.55f, white(mix(0x16, 0x24)), 0x00FFFFFF,
                Shader.TileMode.CLAMP));
    }

    @Override public void draw(@NonNull Canvas cv) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float r = Math.min(radius, Math.min(b.width(), b.height()) / 2f);
        box.set(b);
        if (kind != RIM) {
            cv.drawRoundRect(box, r, r, fill);
            cv.drawRoundRect(box, r, r, sheen);
        }
        edge.set(box);
        edge.inset(line / 2f, line / 2f);
        cv.drawRoundRect(edge, r, r, rim);
        if (kind != RIM && b.height() > line * 8) {
            in.set(box);
            in.inset(line * 1.6f, line * 1.6f);
            float ri = Math.max(0f, r - line * 1.6f);
            cv.drawRoundRect(in, ri, ri, inner);
        }
    }

    @Override public void getOutline(@NonNull Outline outline) {
        Rect b = getBounds();
        outline.setRoundRect(b, Math.min(radius, Math.min(b.width(), b.height()) / 2f));
    }

    @Override public void setAlpha(int a) {
        alpha = a;
        for (Paint p : new Paint[]{fill, sheen, rim}) p.setAlpha(a);
        inner.setColor(white(0x12 * a / 255));
        invalidateSelf();
    }

    @Override public int getAlpha() {
        return alpha;
    }

    @Override public void setColorFilter(ColorFilter cf) {
        for (Paint p : new Paint[]{fill, sheen, rim, inner}) p.setColorFilter(cf);
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
