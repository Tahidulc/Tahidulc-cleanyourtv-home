package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The game's stats sliding out sideways from the scoreboard over the channel (BoardCover), in line with it and as high
 * as it (BoardSide), as broadcasters' stat bars come out of their score bug: one stat at a time, for a few seconds each,
 * the next turning up into its place, then back in under the board. Each is its name and its figures: each side's
 * number with a bar between them split in the clubs' colours by their share ("bar"); two figures with a line between
 * them, as places in the table ("text"); each side's last five results as coloured squares ("form"); or one line, as
 * the player of the match ("one"). Beside a taller board (Stacked, Card) the name sits over the figures.
 */
final class BoardStats extends BoardSide {
    /** How long each stat shows. */
    static final long EACH_MS = 4000L;
    private static final long TURN_MS = 300L;
    private static final int WIN = 0xFF2DB560, DRAW = 0xFF8C94A8, LOSS = 0xFFE04848;

    private final List<String[]> items = new ArrayList<>();
    private int at;
    /** The turn to this stat: 0 the last one, 1 this one. */
    private float turn = 1f;
    private int homeColour = 0xFFD8323B, awayColour = 0xFF4F8BFF, homeOwn, awayOwn;
    private ValueAnimator turning;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Runnable nextOne = this::next;

    BoardStats(Context c) {
        super(c);
        text.setTypeface(ScoreCard.outfit(c, 700));
        text.setFontFeatureSettings("tnum");
    }

    /**
     * The clubs' colours for the bar: as on the board for a dark ground (a very dark one lightened), their own as they
     * show on white for a light one (0: none known, the board's darkened).
     */
    void colours(int home, int away, int homeOnWhite, int awayOnWhite) {
        if (home == homeColour && away == awayColour && homeOnWhite == homeOwn && awayOnWhite == awayOwn) return;
        homeColour = home;
        awayColour = away;
        homeOwn = homeOnWhite;
        awayOwn = awayOnWhite;
        if (isOut()) invalidate();
    }

    private int barColour(boolean home) {
        int c = home ? homeColour : awayColour, own = home ? homeOwn : awayOwn;
        if (!look.light) return visible(c);
        return own != 0 ? own : BoardDrop.legible(c, true);
    }

    /** Pure: a colour too dark for a dark strip mixed two fifths of the way to white; any other as it is. */
    static int visible(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        if ((r * 299 + g * 587 + b * 114) / 1000 >= 80) return c;
        r += (255 - r) * 2 / 5;
        g += (255 - g) * 2 / 5;
        b += (255 - b) * 2 / 5;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Out with these {name, home, away[, kind]}, one after another, then back in. */
    void show(List<String[]> list) {
        if (list == null || list.isEmpty()) return;
        removeCallbacks(nextOne);
        if (turning != null) turning.cancel();
        items.clear();
        items.addAll(list);
        at = 0;
        turn = 1f;
        slideOut();
        postDelayed(nextOne, SLIDE_MS + EACH_MS);
    }

    /** Pure: how long n stats take, out, each in turn and back in. */
    static long showFor(int n) {
        return 2 * SLIDE_MS + n * EACH_MS + Math.max(0, n - 1) * TURN_MS;
    }

    /** Previews: shown at once, this far out, on this stat. */
    void showNow(List<String[]> list, int index, float howFar) {
        removeCallbacks(nextOne);
        items.clear();
        items.addAll(list);
        at = Math.max(0, Math.min(index, items.size() - 1));
        turn = 1f;
        outNow(howFar);
    }

    private void next() {
        if (!isOut()) return;
        if (at + 1 >= items.size()) {
            fold(null);
            return;
        }
        at++;
        if (turning != null) turning.cancel();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(TURN_MS);
        va.addUpdateListener(a -> {
            turn = (float) a.getAnimatedValue();
            invalidate();
        });
        turning = va;
        va.start();
        postDelayed(nextOne, TURN_MS + EACH_MS);
    }

    @Override protected void onFolding() {
        removeCallbacks(nextOne);
        if (turning != null) turning.cancel();
        turn = 1f;
    }

    // ---- size --------------------------------------------------------------------------------------------------------

    /** Its width for these at height h (after place()): the widest one's words and its ends, up to room. */
    int widthFor(List<String[]> list, int h, int room) {
        float most = 0;
        for (String[] s : list) most = Math.max(most, itemW(s, h));
        return Math.round(Math.min(room, most + ends(look, unit, h, tuck, false)));
    }

    /** Beside a taller board: the name over the figures. */
    private boolean twoRows(int h) {
        return h >= unit * 1.45f;
    }

    private static String kind(String[] s) {
        return s.length > 3 && s[3] != null ? s[3] : "bar";
    }

    private float nameSize(boolean two) {
        return unit * (two ? 0.28f : 0.26f);
    }

    private float valueSize(boolean two) {
        return unit * (two ? 0.46f : 0.42f);
    }

    private float itemW(String[] s, int h) {
        boolean two = twoRows(h);
        float name = nameW(s[0], two), group = groupW(s, two);
        return two ? Math.max(name, group) : name + unit * 0.45f + group;
    }

    private float nameW(String n, boolean two) {
        text.setTextSize(nameSize(two));
        text.setLetterSpacing(0.1f);
        float w = text.measureText(n == null ? "" : n.toUpperCase(Locale.UK));
        text.setLetterSpacing(0f);
        return w;
    }

    private float valueW(String v, boolean two) {
        text.setTextSize(valueSize(two));
        return text.measureText(v == null ? "" : v);
    }

    private float formW(String f) {
        int n = f == null ? 0 : Math.min(5, f.length());
        return n == 0 ? 0 : n * unit * 0.44f + (n - 1) * unit * 0.06f;
    }

    private float groupW(String[] s, boolean two) {
        switch (kind(s)) {
            case "one":
                return valueW(s[1], two);
            case "form":
                return formW(s[1]) + unit * 0.4f + formW(s[2]);
            case "text":
                return valueW(s[1], two) + unit * 0.6f + valueW(s[2], two);
            default:
                return valueW(s[1], two) + unit * (0.44f + 2.4f) + valueW(s[2], two);
        }
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    /** Pure: the home side's share of a stat, from its two numbers ("58%" and "42%", "7" and "3"); a half when neither has any. */
    static float share(String home, String away) {
        float h = number(home), a = number(away);
        return h + a <= 0 ? 0.5f : h / (h + a);
    }

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private static float number(String s) {
        if (s == null) return 0;
        Matcher m = NUMBER.matcher(s);
        return m.find() ? Float.parseFloat(m.group()) : 0;
    }

    @Override protected void content(Canvas cv, int w, int h) {
        if (items.isEmpty()) return;
        int i = Math.min(at, items.size() - 1);
        if (turn < 1f && i > 0) item(cv, items.get(i - 1), h, -h * 0.6f * turn, 1f - turn);
        item(cv, items.get(i), h, h * 0.6f * (1f - turn), turn);
    }

    /** One stat at height offset dy, faded to alpha: its name, then its figures (its name over them beside a taller board). */
    private void item(Canvas cv, String[] s, int h, float dy, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        if (a <= 0) return;
        boolean two = twoRows(h);
        // Too long for its room (a narrow screen's edge): drawn smaller.
        float need = itemW(s, h), room = Math.max(1f, end - start), k = Math.min(1f, room / Math.max(1f, need));
        float far = k < 1f ? start + need : end;
        cv.save();
        if (k < 1f) cv.scale(k, k, start, h / 2f);
        String name = s[0] == null ? "" : s[0].toUpperCase(Locale.UK);
        if (two) {
            float mid = (start + far) / 2f;
            words(cv, name, mid, dy + h * 0.1f, h * 0.34f, nameSize(true), 0.1f, look.dim, a, Paint.Align.CENTER);
            float gw = groupW(s, true);
            group(cv, s, mid - gw / 2f, dy + h * 0.42f, h * 0.48f, a, true);
        } else {
            words(cv, name, start, dy, h, nameSize(false), 0.1f, look.dim, a, Paint.Align.LEFT);
            group(cv, s, far - groupW(s, false), dy, h, a, false);
        }
        cv.restore();
    }

    /** The figures from x along, in the middle of (y, rowH): each side's, with the bar of shares, a line or nothing between. */
    private void group(Canvas cv, String[] s, float x, float y, float rowH, int a, boolean two) {
        float vs = valueSize(two);
        switch (kind(s)) {
            case "one":
                words(cv, s[1], x, y, rowH, vs, 0f, look.ink, a, Paint.Align.LEFT);
                return;
            case "form":
                float after = form(cv, s[1], x, y, rowH, a);
                form(cv, s[2], after + unit * 0.4f, y, rowH, a);
                return;
            case "text": {
                float hw = valueW(s[1], two);
                words(cv, s[1], x, y, rowH, vs, 0f, look.ink, a, Paint.Align.LEFT);
                float lx = x + hw + unit * 0.3f, lw = Math.max(1f, unit * 0.03f);
                fill.setColor(look.dim);
                fill.setAlpha(a / 2);
                cv.drawRect(lx - lw / 2f, y + rowH * 0.26f, lx + lw / 2f, y + rowH * 0.74f, fill);
                words(cv, s[2], lx + unit * 0.3f, y, rowH, vs, 0f, look.ink, a, Paint.Align.LEFT);
                return;
            }
            default: {
                float hw = valueW(s[1], two);
                words(cv, s[1], x, y, rowH, vs, 0f, look.ink, a, Paint.Align.LEFT);
                float left = x + hw + unit * 0.22f, right = left + unit * 2.4f;
                bar(cv, left, right, y + rowH / 2f, share(s[1], s[2]), a);
                words(cv, s[2], right + unit * 0.22f, y, rowH, vs, 0f, look.ink, a, Paint.Align.LEFT);
            }
        }
    }

    /** The bar: each club's share in its colour, a hair apart. */
    private void bar(Canvas cv, float left, float right, float mid, float share, int a) {
        float t = Math.max(3f, unit * 0.12f), br = t / 2f, gap = Math.max(1f, unit * 0.03f);
        float split = left + (right - left) * share;
        fill.setColor(barColour(true));
        fill.setAlpha(a);
        r.set(left, mid - br, Math.max(left + br * 2, split - gap), mid + br);
        cv.drawRoundRect(r, br, br, fill);
        fill.setColor(barColour(false));
        fill.setAlpha(a);
        r.set(Math.min(right - br * 2, split + gap), mid - br, right, mid + br);
        cv.drawRoundRect(r, br, br, fill);
        fill.setAlpha(255);
    }

    /** A side's last five as squares (won green, drawn grey, lost red), each with its letter; returns where they end. */
    private float form(Canvas cv, String f, float x, float y, float rowH, int a) {
        if (f == null) return x;
        float s = unit * 0.44f, gap = unit * 0.06f, top = y + (rowH - s) / 2f, rad = s * 0.18f;
        for (int i = 0; i < Math.min(5, f.length()); i++) {
            char ch = Character.toUpperCase(f.charAt(i));
            fill.setColor(ch == 'W' ? WIN : ch == 'L' ? LOSS : DRAW);
            fill.setAlpha(a);
            r.set(x, top, x + s, top + s);
            cv.drawRoundRect(r, rad, rad, fill);
            words(cv, String.valueOf(ch), x + s / 2f, top, s, s * 0.6f, 0f, 0xFFFFFFFF, a, Paint.Align.CENTER);
            x += s + gap;
        }
        fill.setAlpha(255);
        return x - gap;
    }

    /** Words at x (from it, or centred on it), in the middle of (y, rowH). */
    private void words(Canvas cv, String s, float x, float y, float rowH, float size, float spacing, int colour, int a, Paint.Align align) {
        if (s == null || s.isEmpty()) return;
        text.setTextSize(size);
        text.setLetterSpacing(spacing);
        text.setColor(colour);
        text.setAlpha(Math.round(a * ((colour >>> 24) / 255f)));
        text.setTextAlign(align);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(s, x, y + rowH / 2f - (fm.ascent + fm.descent) / 2f, text);
        text.setLetterSpacing(0f);
    }
}
