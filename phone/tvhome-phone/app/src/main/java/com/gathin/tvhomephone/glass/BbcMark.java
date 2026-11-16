package com.gathin.tvhomephone.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * The BBC as its sections mark themselves, drawn in place as the TV launcher draws it (so no logo file is fetched): on
 * a chip, BBC News white blocks with red letters and NEWS in white on BBC red; BBC Sport black blocks with yellow
 * letters and SPORT in black on BBC Sport's yellow. For the stories on Home, the breaking news alerts and the ad-break
 * screen's club news.
 */
public final class BbcMark extends Drawable {
    private static final int RED = 0xFFB80000, YELLOW = 0xFFFFD230, BLACK = 0xFF111318, WHITE = 0xFFFFFFFF;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final boolean sport;
    private final String tail;
    /** The blocks' height; the chip's padding round them; the gap between the blocks. */
    private final float h, padX, padY, gap;
    private final int width, height;
    private int alpha = 255;

    private BbcMark(float blockPx, String section) {
        String s = section == null ? "NEWS" : section.toUpperCase(Locale.ROOT);
        sport = "SPORT".equals(s);
        tail = s;
        h = blockPx;
        padX = blockPx * 0.42f;
        padY = blockPx * 0.24f;
        gap = Math.max(1f, blockPx * 0.09f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(h * 0.72f);
        float w = 3 * h + 2 * gap + h * 0.22f + paint.measureText(tail);
        width = Math.round(w + 2 * padX);
        height = Math.round(h + 2 * padY);
    }

    /** The chip for a section ("NEWS", "SPORT"), heightPx tall in all (where no Context is to hand). */
    public static BbcMark px(float heightPx, String section) {
        return new BbcMark(heightPx / 1.48f, section);
    }

    /** The section a BBC story's link says ("…/sport/…" SPORT, any other BBC link NEWS), or null for none of the BBC's. */
    public static String sectionOfLink(String link) {
        if (link == null) return null;
        String l = link.toLowerCase(Locale.ROOT);
        if (!l.contains("bbc.co.uk") && !l.contains("bbc.com")) return null;
        return l.contains("/sport") ? "SPORT" : "NEWS";
    }

    /** The chip for a section ("NEWS", "SPORT"), heightDp tall in all. */
    public static BbcMark chip(Context c, String section, float heightDp) {
        float px = Glass.dp(c, heightDp);
        return new BbcMark(px / 1.48f, section);
    }

    /** The section a source's name says ("BBC Sport" is SPORT, "BBC News" NEWS), or null when it is not the BBC's. */
    public static String section(String source) {
        if (source == null) return null;
        String s = source.trim().toUpperCase(Locale.ROOT);
        if (!s.startsWith("BBC")) return null;
        if (s.contains("SPORT")) return "SPORT";
        if (s.contains("NEWS")) return "NEWS";
        return null;
    }

    /** The chip for a source's name ("BBC Sport"), heightDp tall; null when the source is not the BBC's. */
    public static BbcMark of(Context c, String source, float heightDp) {
        String s = section(source);
        return s == null ? null : chip(c, s, heightDp);
    }

    /** The chip drawn on a bitmap of its own size (for a notification or a pop-up's head). */
    public Bitmap bitmap() {
        Bitmap b = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        setBounds(0, 0, width, height);
        draw(new Canvas(b));
        return b;
    }

    /** The chip in the middle of a square of its colour, for a notification's large picture. */
    public Bitmap square(int sizePx) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, sizePx), Math.max(1, sizePx), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawColor(sport ? YELLOW : RED);
        float scale = Math.min(1f, sizePx * 0.86f / width);
        int w = Math.round(width * scale), hh = Math.round(height * scale);
        int left = (sizePx - w) / 2, top = (sizePx - hh) / 2;
        cv.save();
        cv.translate(left, top);
        cv.scale(scale, scale);
        setBounds(0, 0, width, height);
        draw(cv);
        cv.restore();
        return b;
    }

    @Override public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        float sx = b.width() / (float) width, sy = b.height() / (float) height;
        int save = c.save();
        c.translate(b.left, b.top);
        c.scale(sx, sy);
        tone(sport ? YELLOW : RED);
        r.set(0, 0, width, height);
        float corner = h * 0.24f;
        c.drawRoundRect(r, corner, corner, paint);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = padY + h / 2f - (fm.ascent + fm.descent) / 2f;
        float x = padX;
        for (String letter : new String[]{"B", "B", "C"}) {
            tone(sport ? BLACK : WHITE);
            r.set(x, padY, x + h, padY + h);
            c.drawRoundRect(r, h * 0.06f, h * 0.06f, paint);
            tone(sport ? YELLOW : RED);
            c.drawText(letter, x + (h - paint.measureText(letter)) / 2f, baseline, paint);
            x += h + gap;
        }
        tone(sport ? BLACK : WHITE);
        c.drawText(tail, x - gap + h * 0.22f, baseline, paint);
        c.restoreToCount(save);
    }

    @Override public int getIntrinsicWidth() {
        return width;
    }

    @Override public int getIntrinsicHeight() {
        return height;
    }

    private void tone(int colour) {
        paint.setColor(colour);
        paint.setAlpha(Color.alpha(colour) * alpha / 255);
    }

    @Override public void setAlpha(int alpha) {
        this.alpha = Math.max(0, Math.min(255, alpha));
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
