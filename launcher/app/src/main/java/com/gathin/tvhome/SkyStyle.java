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

    /** The band's ground: its two colours and light stripes (gap and width in pixels). */
    static Drawable band(int top, int bottom, int gap, int width) {
        return new Streaks(top, bottom, 0x1AFFFFFF, gap, width);
    }

    /** The stage's ground: pale, with soft white streaks. */
    static Drawable stage(int gap, int width) {
        return new Streaks(STAGE, STAGE_DEEP, 0x40FFFFFF, gap, width);
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
