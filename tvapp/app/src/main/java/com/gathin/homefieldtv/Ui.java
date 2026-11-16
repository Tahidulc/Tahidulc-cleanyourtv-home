package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * The look shared by every screen: sizes drawn for a 1920 x 1080 screen and scaled to the TV's, the typeface, the
 * blues, and times written the way the guide writes them ("3.00pm", "Wednesday, 3.01pm").
 */
final class Ui {
    private Ui() {}

    /** Screen pixels for one design pixel. */
    static float s = 1f;
    static Typeface regular = Typeface.DEFAULT, bold = Typeface.DEFAULT_BOLD;

    // The blues, from the guide's own screens: tinted to the theme (Theme) as the app starts.
    static int BG_TOP = 0xFF1163C8, BG_BOTTOM = 0xFF0A50AE;
    static int PANEL_TOP = 0xFF2A6CB8, PANEL_BOTTOM = 0xFF0B4C9E, STRIP = 0xFF0A4594, EDGE = 0xFF4F92DA;
    static int NAVY = 0xFF0A2B68, CARD = 0xFF154E9C, CARD_LIGHT = 0xFF2563B4;
    static final int WHITE = 0xFFFFFFFF, DIM = 0xD9FFFFFF, FAINT = 0x9EFFFFFF;
    static int CYAN = 0xFF2BB5F5;
    static final int AMBER = 0xFFFFC233;
    static final int CELL = 0x24FFFFFF, CELL_NOW = 0x3AFFFFFF;
    private static float tintHue = -1f, tintSat = 1f, tintVal = 1f;

    /** The blue screens tinted to a theme (hue below 0 and shares of 1: Sky Q's own blues). */
    static void tint(float hue, float sat, float val) {
        tintHue = hue;
        tintSat = sat;
        tintVal = val;
        BG_TOP = tint(0xFF1163C8);
        BG_BOTTOM = tint(0xFF0A50AE);
        PANEL_TOP = tint(0xFF2A6CB8);
        PANEL_BOTTOM = tint(0xFF0B4C9E);
        STRIP = tint(0xFF0A4594);
        EDGE = tint(0xFF4F92DA);
        NAVY = tint(0xFF0A2B68);
        CARD = tint(0xFF154E9C);
        CARD_LIGHT = tint(0xFF2563B4);
        CYAN = tint(0xFF2BB5F5);
    }

    /** One of the blue screens' own colours, tinted to the theme. */
    static int tint(int colour) {
        return Theme.shift(colour, tintHue, tintSat, tintVal);
    }

    static void init(Context c) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int w = Math.max(dm.widthPixels, dm.heightPixels);
        s = w > 0 ? w / 1920f : 1f;
        try {
            regular = c.getResources().getFont(R.font.carlito_regular);
            bold = c.getResources().getFont(R.font.carlito_bold);
        } catch (RuntimeException e) {
            regular = Typeface.DEFAULT;
            bold = Typeface.DEFAULT_BOLD;
        }
    }

    static int px(float design) {
        return Math.round(design * s);
    }

    static float f(float design) {
        return design * s;
    }

    static TextPaint paint(float size, boolean isBold, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(isBold ? bold : regular);
        p.setTextSize(f(size));
        p.setColor(color);
        return p;
    }

    static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    /** Cut to fit a width, at a word where it can be, with an ellipsis. */
    static String fit(String text, TextPaint p, float width) {
        if (text == null || width <= 0) return "";
        if (p.measureText(text) <= width) return text;
        float room = width - p.measureText("…");
        if (room <= 0) return "";
        // The longest start that fits, found by halving.
        int lo = 0, hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (p.measureText(text, 0, mid) <= room) lo = mid;
            else hi = mid - 1;
        }
        int cut = lo;
        int space = text.lastIndexOf(' ', cut);
        if (space > cut * 0.6f) cut = space;
        while (cut > 0 && (text.charAt(cut - 1) == ' ' || text.charAt(cut - 1) == ',' || text.charAt(cut - 1) == '-')) cut--;
        if (cut > 0 && Character.isHighSurrogate(text.charAt(cut - 1))) cut--;
        return cut <= 0 ? "" : text.substring(0, cut) + "…";
    }

    static StaticLayout layout(CharSequence text, TextPaint p, int width, int maxLines) {
        return StaticLayout.Builder.obtain(text == null ? "" : text, 0, text == null ? 0 : text.length(), p, Math.max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.05f)
                .setIncludePad(false)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }

    static void draw(Canvas c, StaticLayout l, float x, float y) {
        c.save();
        c.translate(x, y);
        l.draw(c);
        c.restore();
    }

    static Paint stroke(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, f(width)));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        return p;
    }

    static final int LEFT = 0, UP = 1, RIGHT = 2, DOWN = 3;

    /** A thin chevron pointing left, up, right or down, centred on (cx, cy); size is half its height (screen px). */
    static void chevron(Canvas c, float cx, float cy, float size, int dir, Paint p) {
        Path path = new Path();
        float a = size * 0.5f;
        switch (dir) {
            case LEFT:
                path.moveTo(cx + a, cy - size);
                path.lineTo(cx - a, cy);
                path.lineTo(cx + a, cy + size);
                break;
            case RIGHT:
                path.moveTo(cx - a, cy - size);
                path.lineTo(cx + a, cy);
                path.lineTo(cx - a, cy + size);
                break;
            case UP:
                path.moveTo(cx - size, cy + a);
                path.lineTo(cx, cy - a);
                path.lineTo(cx + size, cy + a);
                break;
            default:
                path.moveTo(cx - size, cy - a);
                path.lineTo(cx, cy + a);
                path.lineTo(cx + size, cy - a);
                break;
        }
        c.drawPath(path, p);
    }

    /** A five-point star, for the ratings. */
    static Path star(float cx, float cy, float r) {
        Path p = new Path();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? r : r * 0.45f;
            float x = cx + (float) (Math.cos(a) * rr), y = cy - (float) (Math.sin(a) * rr);
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    /** Stars out of five for a rating out of ten, drawn from x; the width they took. */
    static float stars(Canvas c, float x, float baseline, float size, float outOfTen, Paint on, Paint off) {
        float r = size * 0.5f, cy = baseline - size * 0.38f, step = size * 1.08f;
        float five = Math.max(0f, Math.min(5f, outOfTen / 2f));
        for (int i = 0; i < 5; i++) {
            float cx = x + r + i * step;
            Path p = star(cx, cy, r);
            c.drawPath(p, off);
            float part = Math.max(0f, Math.min(1f, five - i));
            if (part > 0f) {
                c.save();
                c.clipRect(cx - r, cy - r, cx - r + 2 * r * (part >= 0.75f ? 1f : part >= 0.25f ? 0.5f : 0f), cy + r);
                c.drawPath(p, on);
                c.restore();
            }
        }
        return 5 * step;
    }

    // ---- times, as the guide writes them -----------------------------------------------------------------------

    private static ZonedDateTime at(long t) {
        return Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault());
    }

    /** "3.00pm", "12.05am". */
    static String clock(long t) {
        ZonedDateTime z = at(t);
        int h = z.getHour() % 12;
        return (h == 0 ? 12 : h) + "." + (z.getMinute() < 10 ? "0" : "") + z.getMinute() + (z.getHour() < 12 ? "am" : "pm");
    }

    /** "Wednesday". */
    static String dayName(long t) {
        return at(t).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK);
    }

    /** "Wednesday, 3.01pm". */
    static String dayClock(long t) {
        return dayName(t) + ", " + clock(t);
    }

    /** "Today", "Tomorrow", "Friday" (this week), else "Fri 14 Nov". */
    static String day(long t, long now) {
        LocalDate d = at(t).toLocalDate(), today = at(now).toLocalDate();
        if (d.equals(today)) return "Today";
        if (d.equals(today.plusDays(1))) return "Tomorrow";
        if (d.equals(today.minusDays(1))) return "Yesterday";
        if (d.isAfter(today) && d.isBefore(today.plusDays(7))) return dayName(t);
        return at(t).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK) + " " + d.getDayOfMonth() + " "
                + d.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK);
    }

    /** "3.00pm - 4.00pm". */
    static String span(long a, long b) {
        return clock(a) + " - " + clock(b);
    }

    /** "1h 56m", "45m", "2h". */
    static String duration(int minutes) {
        if (minutes <= 0) return "";
        int h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + "m";
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    /** "1h 2m left", "25m left". */
    static String left(long ms) {
        int mins = (int) Math.max(1, Math.round(ms / 60_000.0));
        return duration(mins) + " left";
    }
}
