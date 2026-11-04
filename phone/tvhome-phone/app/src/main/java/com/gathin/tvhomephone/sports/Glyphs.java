package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * The match page's and the alerts' small drawn marks, the same everywhere: the ball for a goal, the cards, the arrows
 * of a substitution, VAR's tag; and the kits' colours made to read on the navy and on the pitch.
 */
public final class Glyphs {
    private Glyphs() {}

    public static final int GOAL = 0, YELLOW = 1, RED = 2, SECOND_YELLOW = 3, SUB = 4, VAR = 5, MISS = 6, OWN_GOAL = 7,
            ON = 8, OFF = 9, DOT = 10;

    public static final int YELLOW_CARD = 0xFFFFD43B, RED_CARD = 0xFFE5484D, GREEN = 0xFF3DDC84, AMBER = 0xFFFFB238;

    /** Which mark an event's words ("Goal", "Penalty - Scored", "Yellow Card", "Substitution", "VAR"...) want. */
    public static int of(String what) {
        String w = what == null ? "" : what.toLowerCase(java.util.Locale.ROOT);
        if (w.contains("own goal")) return OWN_GOAL;
        if (w.contains("var")) return VAR;
        if (w.contains("penalty") && (w.contains("miss") || w.contains("saved"))) return MISS;
        if (w.contains("goal") || w.contains("penalty - scored")) return GOAL;
        if (w.contains("second yellow") || w.contains("yellow") && w.contains("red")) return SECOND_YELLOW;
        if (w.contains("red")) return RED;
        if (w.contains("yellow")) return YELLOW;
        if (w.contains("sub")) return SUB;
        return DOT;
    }

    /** Whether an event's words are a goal that stands (an own goal too; not one VAR took away). */
    public static boolean scored(String what) {
        String w = what == null ? "" : what.toLowerCase(java.util.Locale.ROOT);
        if (w.contains("var") || w.contains("disallowed") || w.contains("miss") || w.contains("saved")) return false;
        return w.contains("goal") || w.contains("penalty - scored");
    }

    /** Draws mark kind centred on cx, cy, about size across. */
    public static void draw(Canvas c, int kind, float cx, float cy, float size) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = size / 2f;
        switch (kind) {
            case YELLOW: card(c, cx, cy, size, YELLOW_CARD, p); break;
            case RED: card(c, cx, cy, size, RED_CARD, p); break;
            case SECOND_YELLOW:
                card(c, cx - size * 0.14f, cy - size * 0.06f, size * 0.92f, YELLOW_CARD, p);
                card(c, cx + size * 0.14f, cy + size * 0.06f, size * 0.92f, RED_CARD, p);
                break;
            case SUB:
                arrow(c, cx - r * 0.42f, cy, size * 0.8f, true, GREEN, p);
                arrow(c, cx + r * 0.42f, cy, size * 0.8f, false, RED_CARD, p);
                break;
            case ON: disc(c, cx, cy, r, GREEN, true, p); break;
            case OFF: disc(c, cx, cy, r, RED_CARD, false, p); break;
            case VAR: var(c, cx, cy, size, p); break;
            case MISS:
                ball(c, cx, cy, r, Color.WHITE, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(size * 0.13f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(RED_CARD);
                c.drawLine(cx - r * 0.95f, cy + r * 0.95f, cx + r * 0.95f, cy - r * 0.95f, p);
                break;
            case OWN_GOAL: ball(c, cx, cy, r, 0xFFFFC2C4, p); break;
            case DOT:
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xB3FFFFFF);
                c.drawCircle(cx, cy, r * 0.32f, p);
                break;
            default: ball(c, cx, cy, r, Color.WHITE, p);
        }
    }

    /** A football: white (or tint) with its dark middle panel and seams. */
    public static void ball(Canvas c, float cx, float cy, float r, int fill, Paint p) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawCircle(cx, cy, r, p);
        p.setColor(0xFF15182E);
        Path pent = new Path();
        float pr = r * 0.42f;
        float[] vx = new float[5], vy = new float[5];
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(-90 + i * 72);
            vx[i] = cx + (float) Math.cos(a) * pr;
            vy[i] = cy + (float) Math.sin(a) * pr;
            if (i == 0) pent.moveTo(vx[i], vy[i]); else pent.lineTo(vx[i], vy[i]);
        }
        pent.close();
        c.drawPath(pent, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, r * 0.13f));
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(-90 + i * 72);
            c.drawLine(vx[i], vy[i], cx + (float) Math.cos(a) * r * 0.98f, cy + (float) Math.sin(a) * r * 0.98f, p);
        }
        c.drawCircle(cx, cy, r - p.getStrokeWidth() / 2f, p);
    }

    private static void card(Canvas c, float cx, float cy, float size, int color, Paint p) {
        float h = size * 0.92f, w = h * 0.7f;
        c.save();
        c.rotate(10, cx, cy);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        c.drawRoundRect(new RectF(cx - w / 2 + 1, cy - h / 2 + 1, cx + w / 2 + 1, cy + h / 2 + 1), w * 0.14f, w * 0.14f, p);
        p.setColor(color);
        c.drawRoundRect(new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), w * 0.14f, w * 0.14f, p);
        c.restore();
    }

    private static void arrow(Canvas c, float cx, float cy, float size, boolean up, int color, Paint p) {
        float h = size * 0.95f, w = size * 0.62f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        Path a = new Path();
        float tip = up ? cy - h / 2 : cy + h / 2, base = up ? cy + h / 2 : cy - h / 2, mid = up ? cy - h * 0.02f : cy + h * 0.02f;
        float stem = w * 0.18f;
        a.moveTo(cx, tip);
        a.lineTo(cx + w / 2, mid);
        a.lineTo(cx + stem, mid);
        a.lineTo(cx + stem, base);
        a.lineTo(cx - stem, base);
        a.lineTo(cx - stem, mid);
        a.lineTo(cx - w / 2, mid);
        a.close();
        c.drawPath(a, p);
    }

    /** A round badge with an arrow in it: on (green, up) or off (red, down). */
    private static void disc(Canvas c, float cx, float cy, float r, int color, boolean up, Paint p) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawCircle(cx, cy, r, p);
        p.setColor(Color.WHITE);
        arrow(c, cx, cy, r * 1.25f, up, Color.WHITE, p);
    }

    private static void var(Canvas c, float cx, float cy, float size, Paint p) {
        float w = size * 1.5f, h = size * 0.82f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(AMBER);
        c.drawRoundRect(new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), h * 0.28f, h * 0.28f, p);
        p.setColor(0xFF2A1A00);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(h * 0.62f);
        p.setFakeBoldText(true);
        p.setLetterSpacing(0.04f);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText("VAR", cx, cy - (fm.ascent + fm.descent) / 2, p);
    }

    /** A mark as a drawable, sizeDp square (VAR's tag is wider), for rows and image views. */
    public static Drawable drawable(Context c, int kind, float sizeDp) {
        int s = Ui.dp(c, sizeDp);
        int w = kind == VAR ? Math.round(s * 1.6f) : s;
        return new Drawable() {
            @Override public void draw(@NonNull Canvas canvas) {
                android.graphics.Rect b = getBounds();
                Glyphs.draw(canvas, kind, b.exactCenterX(), b.exactCenterY(), s * 0.86f);
            }

            @Override public int getIntrinsicWidth() { return w; }

            @Override public int getIntrinsicHeight() { return s; }

            @Override public void setAlpha(int alpha) {}

            @Override public void setColorFilter(ColorFilter cf) {}

            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    /** Words cut to width with an ellipsis, as they will be drawn with this paint. */
    public static String fit(String words, Paint p, float width) {
        if (words == null) return "";
        if (p.measureText(words) <= width) return words;
        String dots = "…";
        int end = words.length();
        while (end > 0 && p.measureText(words, 0, end) + p.measureText(dots) > width) end--;
        return end <= 0 ? "" : words.substring(0, end).trim() + dots;
    }

    // ---- kits ---------------------------------------------------------------------------------------------------

    /** "fcdd39" or "#fcdd39" to a colour; fallback when it is not one. */
    public static int color(String hex, int fallback) {
        if (hex == null) return fallback;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) return fallback;
        try {
            return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Relative brightness, 0 (black) to 1 (white). */
    public static double luminance(int c) {
        double r = lin(Color.red(c)), g = lin(Color.green(c)), b = lin(Color.blue(c));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double lin(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** The number's colour on a shirt: navy on light kits, white on dark ones. */
    public static int ink(int kit) {
        return luminance(kit) > 0.42 ? 0xFF10142E : Color.WHITE;
    }

    /** How far apart two colours look (0 the same, about 3 the widest). */
    static double distance(int a, int b) {
        double dr = (Color.red(a) - Color.red(b)) / 255.0, dg = (Color.green(a) - Color.green(b)) / 255.0,
                db = (Color.blue(a) - Color.blue(b)) / 255.0;
        return Math.sqrt(2 * dr * dr + 4 * dg * dg + 3 * db * db);
    }

    /** A colour lifted towards white until it shows on the dark navy (a black kit as a soft grey). */
    public static int onDark(int c) {
        int out = c;
        for (int i = 0; i < 6 && luminance(out) < 0.09; i++) {
            out = Color.rgb(Color.red(out) + (255 - Color.red(out)) / 4, Color.green(out) + (255 - Color.green(out)) / 4,
                    Color.blue(out) + (255 - Color.blue(out)) / 4);
        }
        return out;
    }

    /**
     * Both sides' colours, told apart: the home kit, then the away kit, or the away side's second colour (or white, or
     * navy) when the two would look alike. Each from the kit worn, else the club's colour, else a blue and a white.
     */
    public static int[] kits(String homeKit, String homeAlt, String homeClub, String awayKit, String awayAlt, String awayClub) {
        int h = color(homeKit, color(homeClub, 0xFF2A4FD0));
        int a = color(awayKit, color(awayClub, 0xFFF4F5FA));
        if (distance(h, a) < 0.55) {
            int alt = color(awayAlt, color(homeAlt, 0));
            if (alt != 0 && distance(h, alt) >= 0.55) a = alt;
            else a = luminance(h) > 0.4 ? 0xFF1A2466 : 0xFFF4F5FA;
        }
        return new int[]{h, a};
    }
}
