package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
 * What every screen shares: sizes drawn for a 1920 x 1080 screen and scaled to the TV's, words cut to fit and laid out
 * in lines, chevrons and stars, and times written the way the guide writes them ("3.00pm", "Wednesday, 3.01pm"). The
 * colours and the typeface are the dark look's (Dark, Theme).
 */
final class Ui {
    private Ui() {}

    /** Screen pixels for one design pixel. */
    static float s = 1f;

    static void init(Context c) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int w = Math.max(dm.widthPixels, dm.heightPixels);
        s = w > 0 ? w / 1920f : 1f;
    }

    static int px(float design) {
        return Math.round(design * s);
    }

    static float f(float design) {
        return design * s;
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

    // ---- times, as the guide writes them -----------------------------------------------------------------------

    private static ZonedDateTime at(long t) {
        return Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault());
    }

    /** "3.00pm", "12.05am". */
    static String clock(long t) {
        ZonedDateTime z = at(t);
        // "3.01pm", or "15:01" (Settings → Appearance → Clock).
        return com.gathin.homefieldtv.prefs.Look.clock(z.getHour(), z.getMinute(),
                com.gathin.homefieldtv.prefs.Look.clock == com.gathin.homefieldtv.prefs.Look.CLOCK_24);
    }

    /** The clock in a screen's corner: "" when Settings → Appearance → Clock is off. */
    static String cornerClock(long t) {
        return com.gathin.homefieldtv.prefs.Look.clock == com.gathin.homefieldtv.prefs.Look.CLOCK_OFF ? "" : clock(t);
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
