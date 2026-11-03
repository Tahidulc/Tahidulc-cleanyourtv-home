package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The look of the launcher's notifications (ScoreCard's Sky-style bars), for the top bar too: a navy band with fine
 * diagonal stripes carrying the competition's mark, a pale stage with soft streaks and navy words, a slanted navy end
 * block. One place for its colours and grounds.
 */
final class SkyStyle {
    private SkyStyle() {
    }

    static final int NAVY = 0xFF1B2591, NAVY_DEEP = 0xFF0D1360, END = 0xFF151A63;
    static final int STAGE = 0xFFF6F7FD, STAGE_DEEP = 0xFFD3D6F3;
    static final int INK = 0xFF0A0D33, INK_SOFT = 0xFF2B3170;
    static final int F1_RED = 0xFFE10600, F1_DEEP = 0xFF8E0400;

    /**
     * The corner of the top bar's ticker and of the bars on Home under it, on the 1920 design: square-edged, a corner
     * only just softened (6), not pill-shaped.
     */
    static final float CORNER = 6f;

    /** CORNER in pixels on this screen. */
    static float corner(android.content.Context c) {
        return CORNER * c.getResources().getDisplayMetrics().widthPixels / 1920f;
    }

    /** The band's ground: its two colours and light stripes (gap and width in pixels). */
    static Drawable band(int top, int bottom, int gap, int width) {
        return new Streaks(top, bottom, 0x1AFFFFFF, gap, width);
    }

    /** The stage's ground: pale, with soft white streaks. */
    static Drawable stage(int gap, int width) {
        return new Streaks(STAGE, STAGE_DEEP, 0x40FFFFFF, gap, width);
    }

    /**
     * Liquid glass with square corners (the top bar's scores): a smoked, see-through body lit from the top (a soft
     * sheen over its upper half), a hairline rim brighter along the top than the foot, and a fine specular line just
     * inside the top edge that fades out towards the ends. The shaders are made when its size changes, never per frame.
     */
    static final class Glass extends Drawable {
        private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG),
                rim = new Paint(Paint.ANTI_ALIAS_FLAG), spec = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float radius, line;
        private final android.graphics.RectF box = new android.graphics.RectF(), top = new android.graphics.RectF();

        /** radius: the corner (CORNER, in pixels); line: a hairline's width in pixels. */
        Glass(float radius, float line) {
            this.radius = radius;
            this.line = Math.max(1f, line);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(this.line);
            spec.setStyle(Paint.Style.STROKE);
            spec.setStrokeWidth(this.line);
        }

        @Override protected void onBoundsChange(Rect b) {
            float h = Math.max(1, b.height());
            body.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0x80222740, 0x99101226, Shader.TileMode.CLAMP));
            sheen.setShader(new LinearGradient(0, b.top, 0, b.top + h * 0.55f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            rim.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0x8CFFFFFF, 0x1FFFFFFF, Shader.TileMode.CLAMP));
            spec.setShader(new LinearGradient(b.left, 0, b.right, 0, new int[]{0x00FFFFFF, 0xB3FFFFFF, 0xB3FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.25f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            box.set(b.left + line / 2f, b.top + line / 2f, b.right - line / 2f, b.bottom - line / 2f);
            top.set(b.left, b.top, b.right, b.top + h * 0.55f);
        }

        @Override public void draw(Canvas c) {
            c.drawRoundRect(box, radius, radius, body);
            c.save();
            c.clipRect(top);
            c.drawRoundRect(box, radius, radius, sheen);
            c.restore();
            c.drawRoundRect(box, radius, radius, rim);
            float y = box.top + line * 1.5f;
            c.drawLine(box.left + radius, y, box.right - radius, y, spec);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** A vertical gradient with diagonal bands (as ScoreCard's and the line-up card's grounds). */
    static final class Streaks extends Drawable {
        private final Paint fill = new Paint(), band = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int top, bottom, gap, width;

        Streaks(int top, int bottom, int bandColour, int gap, int width) {
            this.top = top;
            this.bottom = bottom;
            this.gap = Math.max(4, gap);
            this.width = Math.max(1, width);
            band.setColor(bandColour);
        }

        @Override public void draw(Canvas c) {
            Rect r = getBounds();
            fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, top, bottom, Shader.TileMode.CLAMP));
            c.drawRect(r, fill);
            if (band.getColor() == 0) return;
            float slant = r.height() * 0.4f;
            Path p = new Path();
            for (float x = r.left - slant; x < r.right + slant; x += gap + width) {
                p.reset();
                p.moveTo(x, r.bottom);
                p.lineTo(x + width, r.bottom);
                p.lineTo(x + width + slant, r.top);
                p.lineTo(x + slant, r.top);
                p.close();
                c.drawPath(p, band);
            }
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
