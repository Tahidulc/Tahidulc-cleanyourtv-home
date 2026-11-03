package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/**
 * Brand marks drawn in place, so no logo file has to be fetched: the BBC's three blocks with the section beside
 * them, and the tennis tours' wordmarks on their colour.
 */
final class Badges {
    /** B B C in white on three black blocks, then "NEWS" or "SPORT" in the section's colour. */
    static Drawable bbc(float d, String section, int color) {
        return new Mark(d, new String[]{"B", "B", "C"}, 0xFF111318, 0xFFFFFFFF, section, color, false);
    }

    /**
     * The BBC as its sections brand themselves, as a chip: BBC News (and the regions, BBC Manchester) white blocks
     * with red letters and the section in white on BBC red; BBC Sport black blocks with yellow letters and SPORT in
     * black on BBC Sport's yellow. section: "NEWS", "SPORT", "MANCHESTER"; d: one dp on this screen.
     */
    static Drawable bbcChip(float d, String section) {
        boolean sport = "SPORT".equals(section);
        int red = 0xFFB80000, yellow = 0xFFFFD230, black = 0xFF111318;
        Mark m = new Mark(d, new String[]{"B", "B", "C"}, sport ? black : 0xFFFFFFFF, sport ? yellow : red, section,
                sport ? black : 0xFFFFFFFF, false);
        return new Chip(m, sport ? yellow : red, d);
    }

    /** The section of a BBC tag ("BBC SPORT" to "SPORT"); null when it is not the BBC's. */
    static String bbcSection(String tag) {
        return tag != null && tag.startsWith("BBC ") ? tag.substring(4).trim() : null;
    }

    /** A mark on a rounded tile of colour, padded round. */
    private static final class Chip extends Drawable {
        private final Mark mark;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float padX, padY, radius;

        Chip(Mark mark, int colour, float d) {
            this.mark = mark;
            paint.setColor(colour);
            padX = 7 * d;
            padY = 4 * d;
            radius = 4 * d;
        }

        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            c.drawRoundRect(new RectF(b), radius, radius, paint);
            mark.setBounds(Math.round(b.left + padX), Math.round(b.top + padY), Math.round(b.right - padX), Math.round(b.bottom - padY));
            mark.draw(c);
        }

        @Override public int getIntrinsicWidth() { return Math.round(mark.getIntrinsicWidth() + 2 * padX); }
        @Override public int getIntrinsicHeight() { return Math.round(mark.getIntrinsicHeight() + 2 * padY); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); mark.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** A wordmark on one rounded tile: "ATP" white on navy, "WTA" white on purple. */
    static Drawable tile(float d, String text, int bg, int fg) {
        return new Mark(d, new String[]{text}, bg, fg, null, 0, true);
    }

    private static final class Mark extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d, h, gap;
        private final String[] boxes;
        private final String tail;
        private final int boxColor, boxText, tailColor;
        private final boolean wide;
        private final int width;

        Mark(float d, String[] boxes, int boxColor, int boxText, String tail, int tailColor, boolean wide) {
            this.d = d;
            this.boxes = boxes;
            this.boxColor = boxColor;
            this.boxText = boxText;
            this.tail = tail;
            this.tailColor = tailColor;
            this.wide = wide;
            h = 17 * d;
            gap = 1.5f * d;
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(h * 0.72f);
            float w = 0;
            for (String b : boxes) w += box(b) + gap;
            if (tail != null) w += 3 * d + paint.measureText(tail);
            width = Math.round(w);
        }

        private float box(String text) {
            return wide ? paint.measureText(text) + 8 * d : h;
        }

        @Override public void draw(Canvas c) {
            float x = getBounds().left, top = getBounds().top + (getBounds().height() - h) / 2f;
            Paint.FontMetrics fm = paint.getFontMetrics();
            float baseline = top + h / 2f - (fm.ascent + fm.descent) / 2f;
            for (String b : boxes) {
                float w = box(b);
                paint.setColor(boxColor);
                c.drawRoundRect(new RectF(x, top, x + w, top + h), wide ? h / 4f : d, wide ? h / 4f : d, paint);
                paint.setColor(boxText);
                c.drawText(b, x + (w - paint.measureText(b)) / 2f, baseline, paint);
                x += w + gap;
            }
            if (tail != null) {
                paint.setColor(tailColor);
                c.drawText(tail, x + 3 * d - gap, baseline, paint);
            }
        }

        @Override public int getIntrinsicWidth() { return width; }
        @Override public int getIntrinsicHeight() { return Math.round(h); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
