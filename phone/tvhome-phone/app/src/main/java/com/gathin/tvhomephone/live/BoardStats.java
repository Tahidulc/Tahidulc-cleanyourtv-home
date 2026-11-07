package com.gathin.tvhomephone.live;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The game's stats sliding out sideways from the scoreboard over the picture (ScoreBoard), in line with it and as high
 * as it (BoardSide), as broadcasters' stat bars come out of their score bug: one stat at a time, for a few seconds each,
 * the next turning up into its place, then back in under the board. Each is its name and its figures: each side's
 * number with a bar between them split in the clubs' colours by their share ("bar"); two figures with a line between
 * them, as places in the table ("text"); each side's last five results as coloured squares ("form"); or one line, as
 * the player of the match ("one"). Beside a taller board (Stacked, Card) the name sits over the figures. Laid out by
 * layout() so nothing ever overlaps at any size: a short room takes the bar in first, then draws it all smaller.
 */
final class BoardStats extends BoardSide {
    /** How long each stat shows. */
    static final long EACH_MS = 4000L;
    static final long TURN_MS = 300L;
    private static final int WIN = 0xFF2DB560, DRAW = 0xFF8C94A8, LOSS = 0xFFE04848;

    private final List<String[]> items = new ArrayList<>();
    private int at;
    /** The turn to this stat: 0 the last one, 1 this one. */
    private float turn = 1f;
    private int homeColour = 0xFFE5333B, awayColour = 0xFF4F8BFF;
    private ValueAnimator turning;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Runnable nextOne = this::next;

    BoardStats(Context c) {
        super(c);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setFontFeatureSettings("tnum");
    }

    /** The clubs' colours for the bar (on a dark ground a very dark one lightened, on a light one a very light one darkened). */
    void colours(int home, int away) {
        if (home == homeColour && away == awayColour) return;
        homeColour = home;
        awayColour = away;
        if (isOut()) invalidate();
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

    /** Its width for these at strip height h (after place()): the widest one's words and its ends, up to room. */
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
        return unit * (two ? 0.28f : 0.27f);
    }

    private float valueSize(boolean two) {
        return unit * (two ? 0.46f : 0.44f);
    }

    private float itemW(String[] s, int h) {
        Parts p = parts(s, h, 0f, 1e6f);
        return p.need;
    }

    /** One stat laid out in its room from start to end at strip height h (layout(), with its words measured here). */
    private Parts parts(String[] s, int h, float from, float to) {
        boolean two = twoRows(h);
        String k = kind(s), name = s[0] == null ? "" : s[0].toUpperCase(Locale.UK);
        float hw = "form".equals(k) ? 0 : valueW(s[1], two), aw = "one".equals(k) || "form".equals(k) ? 0 : valueW(s[2], two);
        return layout(k, nameW(name, two), hw, aw, formN(s[1]), formN(s[2]), unit, h, from, to, two);
    }

    private static int formN(String f) {
        return f == null ? 0 : Math.min(5, f.length());
    }

    private float nameW(String n, boolean two) {
        text.setTextSize(nameSize(two));
        text.setLetterSpacing(0.1f);
        float w = text.measureText(n == null ? "" : n.toUpperCase(Locale.UK));
        text.setLetterSpacing(0f);
        return w;
    }

    // ---- where each part goes ----------------------------------------------------------------------------------------

    /** The bar's width as drawn, and the least it gives way to (in board heights). */
    static final float BAR = 2.4f, BAR_LEAST = 0.8f;

    /**
     * One stat's parts as laid out, each {left, top, right, bottom} in the strip, unscaled: the name first, then the
     * figures (home's, the bar or the line between, away's; or each side's squares; or the one line). It is drawn
     * scaled by k about (start, the strip's middle); need is what it needs across, as drawn (before k).
     */
    static final class Parts {
        final java.util.List<float[]> boxes = new ArrayList<>();
        float k = 1f, need, bar, nameSize, valueSize, start, mid;
        boolean two;
        String kind = "bar";
    }

    /**
     * Pure: where one stat's parts go in a strip h high, its words' room from start to end, unit the board's height;
     * nameW, homeW, awayW its words' widths as measured at their sizes (the name's at its spacing), homeN and awayN each
     * side's squares (form). In one row the name stands at the start and the figures at the end, a gap at least between;
     * beside a taller board the name sits over the figures, each centred. A short room takes the bar in first (to a
     * third of its width), then draws it all smaller: the parts never overlap and never leave the strip.
     */
    static Parts layout(String kind, float nameW, float homeW, float awayW, int homeN, int awayN, float unit, float h,
            float start, float end, boolean two) {
        Parts p = new Parts();
        p.kind = kind == null ? "bar" : kind;
        p.two = two;
        p.start = start;
        p.mid = h / 2f;
        p.nameSize = unit * (two ? 0.28f : 0.27f);
        p.valueSize = unit * (two ? 0.46f : 0.44f);
        float room = Math.max(1f, end - start), gapName = unit * 0.45f;
        float square = unit * 0.46f, squareGap = unit * 0.06f;
        float homeF = homeN == 0 ? 0 : homeN * square + (homeN - 1) * squareGap, awayF = awayN == 0 ? 0 : awayN * square + (awayN - 1) * squareGap;
        p.bar = BAR * unit;
        float group = groupWidth(p, homeW, awayW, homeF, awayF, unit);
        float need = two ? Math.max(nameW, group) : nameW + gapName + group;
        // Short of room: the bar in first, to a third of its width.
        if (need > room && "bar".equals(p.kind)) {
            p.bar = Math.max(BAR_LEAST * unit, p.bar - (need - room));
            group = groupWidth(p, homeW, awayW, homeF, awayF, unit);
            need = two ? Math.max(nameW, group) : nameW + gapName + group;
        }
        p.need = need;
        p.k = Math.min(1f, room / Math.max(1f, need));
        float far = p.k < 1f ? start + need : end;
        // The rows: the whole strip in one; the name's over the figures' beside a taller board.
        float nameY = two ? h * 0.1f : 0f, nameH = two ? h * 0.34f : h, groupY = two ? h * 0.42f : 0f, groupH = two ? h * 0.48f : h;
        float nameX = two ? (start + far) / 2f - nameW / 2f : start;
        p.boxes.add(box(nameX, nameY + nameH / 2f, nameW, p.nameSize));
        float x = two ? (start + far) / 2f - group / 2f : far - group, gy = groupY + groupH / 2f;
        switch (p.kind) {
            case "one":
                p.boxes.add(box(x, gy, homeW, p.valueSize));
                break;
            case "form":
                for (int i = 0; i < homeN; i++) p.boxes.add(box(x + i * (square + squareGap), gy, square, square));
                float ax = x + homeF + unit * 0.4f;
                for (int i = 0; i < awayN; i++) p.boxes.add(box(ax + i * (square + squareGap), gy, square, square));
                break;
            case "text": {
                p.boxes.add(box(x, gy, homeW, p.valueSize));
                float lw = Math.max(1f, unit * 0.03f), lx = x + homeW + unit * 0.3f;
                p.boxes.add(new float[]{lx - lw / 2f, groupY + groupH * 0.26f, lx + lw / 2f, groupY + groupH * 0.74f});
                p.boxes.add(box(lx + unit * 0.3f, gy, awayW, p.valueSize));
                break;
            }
            default: {
                p.boxes.add(box(x, gy, homeW, p.valueSize));
                float left = x + homeW + unit * 0.22f, t = Math.max(3f, unit * 0.12f);
                p.boxes.add(new float[]{left, gy - t / 2f, left + p.bar, gy + t / 2f});
                p.boxes.add(box(left + p.bar + unit * 0.22f, gy, awayW, p.valueSize));
            }
        }
        return p;
    }

    private static float groupWidth(Parts p, float homeW, float awayW, float homeF, float awayF, float unit) {
        switch (p.kind) {
            case "one":
                return homeW;
            case "form":
                return homeF + unit * 0.4f + awayF;
            case "text":
                return homeW + unit * 0.6f + awayW;
            default:
                return homeW + unit * 0.44f + p.bar + awayW;
        }
    }

    /** A box w wide and size high, from x, centred on cy. */
    private static float[] box(float x, float cy, float w, float size) {
        return new float[]{x, cy - size / 2f, x + w, cy + size / 2f};
    }

    private float valueW(String v, boolean two) {
        text.setTextSize(valueSize(two));
        return text.measureText(v == null ? "" : v);
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

    /** One stat at height offset dy, faded to alpha: its parts where layout() puts them, drawn smaller when its room is short. */
    private void item(Canvas cv, String[] s, int h, float dy, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        if (a <= 0) return;
        Parts p = parts(s, h, start, end);
        String name = s[0] == null ? "" : s[0].toUpperCase(Locale.UK);
        cv.save();
        cv.translate(0, dy);
        if (p.k < 1f) cv.scale(p.k, p.k, start, h / 2f);
        float[] n = p.boxes.get(0);
        words(cv, name, n[0], n[1], n[3] - n[1], p.nameSize, 0.1f, look.dim, a, Paint.Align.LEFT);
        switch (p.kind) {
            case "one": {
                float[] v = p.boxes.get(1);
                words(cv, s[1], v[0], v[1], v[3] - v[1], p.valueSize, 0f, look.ink, a, Paint.Align.LEFT);
                break;
            }
            case "form": {
                int hn = formN(s[1]);
                for (int i = 1; i < p.boxes.size(); i++) {
                    String f = i <= hn ? s[1] : s[2];
                    square(cv, Character.toUpperCase(f.charAt(i <= hn ? i - 1 : i - 1 - hn)), p.boxes.get(i), a);
                }
                break;
            }
            case "text": {
                float[] v1 = p.boxes.get(1), line = p.boxes.get(2), v2 = p.boxes.get(3);
                words(cv, s[1], v1[0], v1[1], v1[3] - v1[1], p.valueSize, 0f, look.ink, a, Paint.Align.LEFT);
                fill.setColor(look.dim);
                fill.setAlpha(a / 2);
                cv.drawRect(line[0], line[1], line[2], line[3], fill);
                fill.setAlpha(255);
                words(cv, s[2], v2[0], v2[1], v2[3] - v2[1], p.valueSize, 0f, look.ink, a, Paint.Align.LEFT);
                break;
            }
            default: {
                float[] v1 = p.boxes.get(1), b = p.boxes.get(2), v2 = p.boxes.get(3);
                words(cv, s[1], v1[0], v1[1], v1[3] - v1[1], p.valueSize, 0f, look.ink, a, Paint.Align.LEFT);
                bar(cv, b[0], b[2], (b[1] + b[3]) / 2f, share(s[1], s[2]), a);
                words(cv, s[2], v2[0], v2[1], v2[3] - v2[1], p.valueSize, 0f, look.ink, a, Paint.Align.LEFT);
            }
        }
        cv.restore();
    }

    private int barColour(boolean home) {
        int c = home ? homeColour : awayColour;
        return look.light ? BoardDrop.legible(c, true) : visible(c);
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

    /** One of a side's last five as a square (won green, drawn grey, lost red) with its letter, in its box. */
    private void square(Canvas cv, char ch, float[] b, int a) {
        float sz = b[2] - b[0], rad = sz * 0.18f;
        fill.setColor(ch == 'W' ? WIN : ch == 'L' ? LOSS : DRAW);
        fill.setAlpha(a);
        r.set(b[0], b[1], b[2], b[3]);
        cv.drawRoundRect(r, rad, rad, fill);
        fill.setAlpha(255);
        words(cv, String.valueOf(ch), (b[0] + b[2]) / 2f, b[1], b[3] - b[1], sz * 0.6f, 0f, 0xFFFFFFFF, a, Paint.Align.CENTER);
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
