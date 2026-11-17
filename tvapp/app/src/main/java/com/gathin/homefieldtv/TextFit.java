package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.text.TextPaint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Words cut to fit a width and broken into lines, as the guide's cells and rows draw them every frame, without
 * measuring them again or making anything new each frame: each string is measured once for its paint (the widths of
 * all its starts), kept while it is drawn (the most recently drawn MAX), and every frame's cut is worked out from those
 * widths and drawn straight from the string. The rules are Ui.fit's (cut at a word where it can be, with an ellipsis)
 * and two lines broken at a space, the second cut.
 *
 * <pre>
 *   TextFit f = new TextFit(paint, 512);
 *   f.line(c, text, x, y, width)                       one line, cut to fit
 *   f.lines(c, text, x, yOne, yFirst, ySecond, width, 2)   one line at yOne when it fits, else two (yFirst, ySecond)
 *   f.width(text) / f.fitsOne(text, width)             its whole width; whether it needs one line
 * </pre>
 *
 * Main thread only (it draws). hits and misses count the cache's use for the tests.
 */
final class TextFit {
    /** One string's widths with its paint: at[i] is the width of its first i letters. */
    static final class Measured {
        final String text;
        final float[] at;

        Measured(String text, float[] at) {
            this.text = text;
            this.at = at;
        }

        float width() {
            return at[at.length - 1];
        }

        /** The width of letters from..to. */
        float width(int from, int to) {
            return at[to] - at[from];
        }

        /** The most letters from start whose width is at most room (0 when not even one fits). */
        int fit(int start, float room) {
            float limit = at[start] + room;
            int lo = start, hi = at.length - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (at[mid] <= limit) lo = mid;
                else hi = mid - 1;
            }
            return lo;
        }
    }

    static final String ELLIPSIS = "…";

    private final TextPaint paint;
    private final int max;
    private final LinkedHashMap<String, Measured> kept;
    private float size = -1f, ellipsis;
    int hits, misses;

    TextFit(TextPaint paint, int max) {
        this.paint = paint;
        this.max = max;
        this.kept = new LinkedHashMap<String, Measured>(Math.max(16, max / 2), 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Measured> e) {
                return size() > TextFit.this.max;
            }
        };
    }

    TextPaint paint() {
        return paint;
    }

    /** Everything measured again (the text size changed). */
    void clear() {
        kept.clear();
        size = -1f;
    }

    /** The string's widths with this paint (measured the first time it is drawn). */
    Measured measure(String text) {
        if (paint.getTextSize() != size) {
            kept.clear();
            size = paint.getTextSize();
            ellipsis = paint.measureText(ELLIPSIS);
        }
        Measured m = kept.get(text);
        if (m != null) {
            hits++;
            return m;
        }
        misses++;
        int n = text.length();
        float[] at = new float[n + 1];
        if (n > 0) {
            float[] w = new float[n];
            paint.getTextWidths(text, 0, n, w);
            for (int i = 0; i < n; i++) at[i + 1] = at[i] + w[i];
        }
        m = new Measured(text, at);
        kept.put(text, m);
        return m;
    }

    float width(String text) {
        return text == null || text.isEmpty() ? 0f : measure(text).width();
    }

    boolean fitsOne(String text, float width) {
        return width(text) <= width;
    }

    /**
     * Where a cut of letters from..to (with an ellipsis) ends to fit room: the longest start that fits beside the
     * ellipsis, back to the word before when that loses little, past spaces and commas; -1: it all fits.
     */
    private int cut(Measured m, int from, int to, float room) {
        if (m.width(from, to) <= room) return -1;
        float r = room - ellipsis;
        if (r <= 0) return from;
        int c = m.fit(from, r);
        if (c > to) c = to;
        String t = m.text;
        int space = t.lastIndexOf(' ', c);
        if (space > from && space - from > (c - from) * 0.6f) c = space;
        while (c > from && (t.charAt(c - 1) == ' ' || t.charAt(c - 1) == ',' || t.charAt(c - 1) == '-')) c--;
        if (c > from && Character.isHighSurrogate(t.charAt(c - 1))) c--;
        return c;
    }

    /** Letters from..to drawn at (x, y), cut to room with an ellipsis; the width drawn. */
    private float draw(Canvas c, Measured m, int from, int to, float x, float y, float room) {
        int end = cut(m, from, to, room);
        if (end < 0) {
            c.drawText(m.text, from, to, x, y, paint);
            return m.width(from, to);
        }
        if (end <= from) return 0f;
        c.drawText(m.text, from, end, x, y, paint);
        float w = m.width(from, end);
        c.drawText(ELLIPSIS, x + w, y, paint);
        return w + ellipsis;
    }

    /** One line at (x, y), cut to width with an ellipsis; the width drawn. */
    float line(Canvas c, String text, float x, float y, float width) {
        if (text == null || text.isEmpty() || width <= 0) return 0f;
        Measured m = measure(text);
        return draw(c, m, 0, text.length(), x, y, width);
    }

    /**
     * Up to max lines (1 or 2) of text in width: one line at baseline yOne when it fits (or max is 1); else the words
     * that fit on the first at yFirst, broken at a space, and the rest on the second at ySecond, cut with an ellipsis.
     */
    void lines(Canvas c, String text, float x, float yOne, float yFirst, float ySecond, float width, int max) {
        if (text == null || text.isEmpty() || width <= 0) return;
        Measured m = measure(text);
        int n = text.length();
        if (max <= 1 || m.width() <= width) {
            draw(c, m, 0, n, x, yOne, width);
            return;
        }
        int fit = Math.max(1, m.fit(0, width)), cut = fit;
        if (fit < n && text.charAt(fit) != ' ') {
            int sp = text.lastIndexOf(' ', fit);
            if (sp > 0) cut = sp;
        }
        int end1 = cut;
        while (end1 > 0 && text.charAt(end1 - 1) == ' ') end1--;
        int start2 = cut;
        while (start2 < n && text.charAt(start2) == ' ') start2++;
        if (start2 >= n) {
            draw(c, m, 0, end1, x, yOne, width);
            return;
        }
        c.drawText(text, 0, end1, x, yFirst, paint);
        draw(c, m, start2, n, x, ySecond, width);
    }
}
