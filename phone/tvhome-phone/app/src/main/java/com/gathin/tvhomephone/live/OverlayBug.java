package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import com.gathin.tvhomephone.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The scoreboard over the picture, drawn to fill whatever box it is given: on its own, a broadcast strip 30 dp high;
 * over the channel's own scoreboard, that box's size exactly. Wide boxes take one row (the competition, each club's
 * colour, crest and three letters, the score, the clock in minutes and seconds); boxes nearer square take two (the
 * clubs and score over the clock). F1: F1's red block, the session, the lap or the time left, and the first three with
 * their teams' colours. A goal lights the scoring club's side in its colour for a few seconds.
 */
final class OverlayBug extends View {
    /** What it shows. */
    static final class Look {
        boolean f1, live;
        String home = "", away = "", score = "", clock = "", added = "", tag = "";
        int homeColour = 0xFFFFFFFF, awayColour = 0xFFFFFFFF;
        /** F1: the session, the corner ({"LAP", "23"}, {"Q2", "08:31"}), the first drivers' codes and colours. */
        String label = "", cornerA = "", cornerB = "";
        final List<String> codes = new ArrayList<>();
        final List<Integer> colours = new ArrayList<>();

        @Override public boolean equals(Object o) {
            if (!(o instanceof Look)) return false;
            Look l = (Look) o;
            return f1 == l.f1 && live == l.live && home.equals(l.home) && away.equals(l.away) && score.equals(l.score)
                    && clock.equals(l.clock) && added.equals(l.added) && tag.equals(l.tag) && homeColour == l.homeColour
                    && awayColour == l.awayColour && label.equals(l.label) && cornerA.equals(l.cornerA)
                    && cornerB.equals(l.cornerB) && codes.equals(l.codes) && colours.equals(l.colours);
        }

        @Override public int hashCode() {
            return Objects.hash(f1, home, away, score, clock, label, cornerB);
        }
    }

    private static final int NAVY = 0xFF0B1640, NAVY_DEEP = 0xFF070C26, CLOCK = 0xFF050816, ADDED = 0xFFE0213A,
            AMBER = 0xFFFFB547, F1_RED = 0xFFE10600, DIM = 0xFFB9C1E6;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
            image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final int blueTop, blueBottom;
    private final float dp;
    private Look look = new Look();
    Bitmap homeCrest, awayCrest, comp;
    /** The goal's light: 1 to 0 over its seconds, on side 1 (home) or 2 (away). */
    private float flash;
    private int flashSide;
    private long flashUntil;

    OverlayBug(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        blueTop = c.getColor(R.color.bug_blue_top);
        blueBottom = c.getColor(R.color.bug_blue_bottom);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setFontFeatureSettings("tnum");
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    Look look() {
        return look;
    }

    /** What to show; drawn again only when it changed. */
    void set(Look l) {
        if (l.equals(look)) return;
        int h = getHeight() > 0 ? getHeight() : Math.round(30 * dp);
        int before = natural(h);
        look = l;
        boolean size = natural(h) != before;
        setContentDescription(l.f1 ? "F1 " + l.label + " " + l.cornerA + " " + l.cornerB
                : l.home + " " + l.score + " " + l.away + (l.clock.isEmpty() ? "" : ", " + l.clock + " " + l.added));
        if (size) requestLayout();
        invalidate();
    }

    /** A goal for side 1 (home) or 2 (away): its half lit in its colour for four seconds. */
    void goal(int side) {
        flashSide = side;
        flashUntil = System.currentTimeMillis() + 4000;
        postInvalidateOnAnimation();
    }

    void crests(Bitmap home, Bitmap away, Bitmap competition) {
        int h = getHeight() > 0 ? getHeight() : Math.round(30 * dp);
        int before = natural(h);
        homeCrest = home;
        awayCrest = away;
        comp = competition;
        if (natural(h) != before) requestLayout();
        invalidate();
    }

    // ---- size ------------------------------------------------------------------------------------------------------

    /** Its width on its own at height h. */
    int natural(int h) {
        text.setTextSize(h * 0.46f);
        if (look.f1) {
            float w = h * 1.3f + label(h) + corner(h) + h * 0.3f;
            for (String c : look.codes) w += h * 0.16f + measure(c, h * 0.44f) + h * 0.5f;
            return Math.round(w);
        }
        return Math.round(h + 2 * (h * 0.1f + crestW(h) + code(h)) + scoreW(h) + clockW(h));
    }

    @Override protected void onMeasure(int ws, int hs) {
        int h = MeasureSpec.getMode(hs) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(hs) : Math.round(30 * dp);
        int w = MeasureSpec.getMode(ws) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(ws) : natural(h);
        if (MeasureSpec.getMode(ws) == MeasureSpec.AT_MOST) w = Math.min(w, MeasureSpec.getSize(ws));
        setMeasuredDimension(w, h);
    }

    private float measure(String s, float size) {
        text.setTextSize(size);
        return text.measureText(s == null ? "" : s);
    }

    /** Room for the crests: none until they have loaded. */
    private float crestW(float h) {
        return homeCrest != null || awayCrest != null ? h * 0.86f : 0;
    }

    private float code(int h) {
        return Math.max(measure(look.home, h * 0.46f), measure(look.away, h * 0.46f)) + h * 0.5f;
    }

    private float scoreW(int h) {
        return Math.max(h * 1.4f, measure(look.score, h * 0.56f) + h * 0.6f);
    }

    private float clockW(int h) {
        if (look.clock.isEmpty()) return 0;
        return measure("88:88".length() >= look.clock.length() ? "88:88" : look.clock, h * 0.44f) + h * 0.6f
                + (look.added.isEmpty() ? 0 : measure(look.added, h * 0.34f) + h * 0.45f);
    }

    private float label(int h) {
        return look.label.isEmpty() ? 0 : measure(look.label, h * 0.36f) + h * 0.6f;
    }

    private float corner(int h) {
        return measure(look.cornerA, h * 0.32f) + measure(look.cornerB, h * 0.46f) + h * 0.9f;
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        long now = System.currentTimeMillis();
        flash = now < flashUntil ? (flashUntil - now) / 4000f : 0f;
        clip.reset();
        float rad = Math.min(h * 0.2f, 8 * dp);
        r.set(0, 0, w, h);
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        fill.setShader(null);
        fill.setColor(NAVY);
        cv.drawRect(r, fill);
        if (look.f1) {
            if (w / (float) h >= 3.6f) f1Row(cv, 0, 0, w, h); else f1Two(cv, w, h);
        } else if (w / (float) h >= 3.6f) {
            oneRow(cv, 0, 0, w, h, true);
        } else {
            twoRows(cv, w, h);
        }
        cv.restore();
        if (flash > 0) postInvalidateOnAnimation();
    }

    /** The clubs, the score and the clock along one row in (x, y, w, h). */
    private void oneRow(Canvas cv, float x, float y, float w, float h, boolean withClock) {
        int hi = Math.round(h);
        boolean crests = true, compBlock = true;
        float codeW = code(hi), strip = h * 0.1f, crestW = crestW(h), score = scoreW(hi), clock = withClock ? clockW(hi) : 0;
        // A pixel's grace: its own width is the sum rounded.
        float room = w + 1.5f;
        float need = h + 2 * (strip + crestW + codeW) + score + clock;
        if (need > room && crestW > 0) {
            crests = false;
            need -= 2 * crestW;
        }
        if (need > room) {
            compBlock = false;
            need -= h;
        }
        float k = need > room ? Math.max(0.6f, w / need) : 1f;
        float extra = Math.max(0, w - need * k);
        codeW = codeW * k + extra / 2;
        strip *= k;
        score *= k;
        clock *= k;
        float cx = x;
        if (compBlock) {
            fill.setColor(NAVY_DEEP);
            cv.drawRect(cx, y, cx + h, y + h, fill);
            if (comp != null) drawImage(cv, comp, cx + h * 0.16f, y + h * 0.16f, h * 0.68f);
            else words(cv, look.tag, cx + h / 2, y, h, h * 0.3f, DIM);
            cx += h;
        }
        cx = side(cv, cx, y, h, strip, crests ? crestW : 0, codeW, k, true);
        // The score on the blue.
        fill.setShader(new LinearGradient(0, y, 0, y + h, blueTop, blueBottom, Shader.TileMode.CLAMP));
        cv.drawRect(cx, y, cx + score, y + h, fill);
        fill.setShader(null);
        words(cv, look.score, cx + score / 2, y, h, h * 0.56f * k, 0xFFFFFFFF);
        cx += score;
        cx = side(cv, cx, y, h, strip, crests ? crestW : 0, codeW, k, false);
        if (withClock && clock > 0) clock(cv, cx, y, x + w - cx, h, k);
    }

    /** One club's part: (home) strip, crest, code; (away) code, crest, strip. Returns where it ends. */
    private float side(Canvas cv, float cx, float y, float h, float strip, float crestW, float codeW, float k, boolean home) {
        int colour = home ? look.homeColour : look.awayColour;
        float width = strip + crestW + codeW;
        if (flash > 0 && flashSide == (home ? 1 : 2)) {
            fill.setColor(colour);
            fill.setAlpha(Math.round(200 * flash));
            cv.drawRect(cx, y, cx + width, y + h, fill);
            fill.setAlpha(255);
        }
        Bitmap crest = home ? homeCrest : awayCrest;
        String code = home ? look.home : look.away;
        if (home) {
            fill.setColor(colour);
            cv.drawRect(cx, y, cx + strip, y + h, fill);
            if (crestW > 0 && crest != null) drawImage(cv, crest, cx + strip + crestW * 0.14f, y + (h - crestW * 0.72f) / 2, crestW * 0.72f);
            words(cv, code, cx + strip + crestW + codeW / 2, y, h, h * 0.46f * k, 0xFFFFFFFF);
        } else {
            words(cv, code, cx + codeW / 2, y, h, h * 0.46f * k, 0xFFFFFFFF);
            if (crestW > 0 && crest != null) drawImage(cv, crest, cx + codeW + crestW * 0.14f, y + (h - crestW * 0.72f) / 2, crestW * 0.72f);
            fill.setColor(colour);
            cv.drawRect(cx + codeW + crestW, y, cx + width, y + h, fill);
        }
        return cx + width;
    }

    /** The clock's box: minutes and seconds, the minutes added on red; HT in amber; GOAL as one goes in. */
    private void clock(Canvas cv, float x, float y, float w, float h, float k) {
        fill.setColor(CLOCK);
        cv.drawRect(x, y, x + w, y + h, fill);
        if (flash > 0) {
            fill.setColor(flashSide == 1 ? look.homeColour : look.awayColour);
            cv.drawRect(x, y, x + w, y + h, fill);
            words(cv, "GOAL", x + w / 2, y, h, h * 0.42f * k, ink(flashSide == 1 ? look.homeColour : look.awayColour));
            return;
        }
        int colour = "HT".equals(look.clock) ? AMBER : look.live ? 0xFFFFFFFF : DIM;
        if (look.added.isEmpty()) {
            words(cv, look.clock, x + w / 2, y, h, h * 0.44f * k, colour);
            return;
        }
        float cw = measure(look.clock, h * 0.44f * k), aw = measure(look.added, h * 0.34f * k) + h * 0.3f;
        float start = x + (w - cw - aw - h * 0.15f) / 2;
        words(cv, look.clock, start + cw / 2, y, h, h * 0.44f * k, colour);
        float ax = start + cw + h * 0.15f;
        fill.setColor(ADDED);
        r.set(ax, y + h * 0.24f, ax + aw, y + h * 0.76f);
        cv.drawRoundRect(r, h * 0.08f, h * 0.08f, fill);
        words(cv, look.added, ax + aw / 2, y, h, h * 0.34f * k, 0xFFFFFFFF);
    }

    /** Nearer square: the clubs and the score above, the competition and the clock below. */
    private void twoRows(Canvas cv, int w, int h) {
        float top = h * 0.58f;
        clubsRow(cv, w, top);
        float y = top, bh = h - top;
        fill.setColor(CLOCK);
        cv.drawRect(0, y, w, h, fill);
        if (comp != null) drawImage(cv, comp, bh * 0.18f, y + bh * 0.12f, bh * 0.76f);
        else if (!look.tag.isEmpty()) words(cv, look.tag, bh * 1.1f, y, bh, bh * 0.42f, DIM);
        clock(cv, w * 0.3f, y, w * 0.7f, bh, 1.25f);
    }

    private void clubsRow(Canvas cv, int w, float h) {
        // Without the competition's block (it moves below): the clubs and the score across the width.
        float codeW = code(Math.round(h)), strip = h * 0.1f, crestW = crestW(h), score = scoreW(Math.round(h));
        float need = 2 * (strip + crestW + codeW) + score;
        boolean crests = need <= w + 1.5f;
        if (!crests) need -= 2 * crestW;
        float k = need > w + 1.5f ? Math.max(0.6f, w / need) : 1f;
        float extra = Math.max(0, w - need * k);
        float cx = side(cv, 0, 0, h, strip * k, crests ? crestW : 0, codeW * k + extra / 2, k, true);
        fill.setShader(new LinearGradient(0, 0, 0, h, blueTop, blueBottom, Shader.TileMode.CLAMP));
        cv.drawRect(cx, 0, cx + score * k, h, fill);
        fill.setShader(null);
        words(cv, look.score, cx + score * k / 2, 0, h, h * 0.56f * k, 0xFFFFFFFF);
        side(cv, cx + score * k, 0, h, strip * k, crests ? crestW : 0, codeW * k + extra / 2, k, false);
    }

    /** F1 along one row: the red block, the session, the corner, the first three. */
    private void f1Row(Canvas cv, float x, float y, float w, float h) {
        float cx = x;
        fill.setColor(F1_RED);
        cv.drawRect(cx, y, cx + h * 1.3f, y + h, fill);
        text.setTextSkewX(-0.2f);
        words(cv, "F1", cx + h * 0.65f, y, h, h * 0.5f, 0xFFFFFFFF);
        text.setTextSkewX(0);
        cx += h * 1.3f;
        float lw = label(Math.round(h));
        if (lw > 0) {
            words(cv, look.label, cx + lw / 2, y, h, h * 0.36f, 0xFFFFFFFF);
            cx += lw;
        }
        float cw = corner(Math.round(h));
        fill.setColor(CLOCK);
        cv.drawRect(cx, y, cx + cw, y + h, fill);
        float aw = measure(look.cornerA, h * 0.32f);
        words(cv, look.cornerA, cx + h * 0.35f + aw / 2, y, h, h * 0.32f, DIM);
        words(cv, look.cornerB, cx + h * 0.55f + aw + measure(look.cornerB, h * 0.46f) / 2, y, h, h * 0.46f, 0xFFFFFFFF);
        cx += cw + h * 0.15f;
        drivers(cv, cx, y, x + w - cx, h);
    }

    private void f1Two(Canvas cv, int w, int h) {
        float top = h * 0.5f;
        f1Row(cv, 0, 0, w, top);
        fill.setColor(NAVY_DEEP);
        cv.drawRect(0, top, w, h, fill);
        drivers(cv, h * 0.15f, top, w - h * 0.15f, h - top);
    }

    private void drivers(Canvas cv, float x, float y, float w, float h) {
        float cx = x;
        for (int i = 0; i < look.codes.size(); i++) {
            float cw = h * 0.16f + measure(look.codes.get(i), h * 0.44f) + h * 0.5f;
            if (cx + cw > x + w + 1) break;
            words(cv, String.valueOf(i + 1), cx + h * 0.12f, y, h, h * 0.3f, DIM);
            fill.setColor(i < look.colours.size() ? look.colours.get(i) : F1_RED);
            cv.drawRect(cx + h * 0.26f, y + h * 0.24f, cx + h * 0.26f + h * 0.08f, y + h * 0.76f, fill);
            words(cv, look.codes.get(i), cx + h * 0.42f + measure(look.codes.get(i), h * 0.44f) / 2, y, h, h * 0.44f, 0xFFFFFFFF);
            cx += cw;
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    /** Words centred on cx, in the middle of (y, h). */
    private void words(Canvas cv, String s, float cx, float y, float h, float size, int colour) {
        if (s == null || s.isEmpty()) return;
        text.setTextSize(size);
        text.setColor(colour);
        text.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(s, cx, y + h / 2 - (fm.ascent + fm.descent) / 2, text);
    }

    private void drawImage(Canvas cv, Bitmap b, float x, float y, float size) {
        float s = size / Math.max(b.getWidth(), b.getHeight());
        float bw = b.getWidth() * s, bh = b.getHeight() * s;
        r.set(x + (size - bw) / 2, y + (size - bh) / 2, x + (size + bw) / 2, y + (size + bh) / 2);
        cv.drawBitmap(b, null, r, image);
    }

    /** Dark words on a light colour, white on a dark one. */
    static int ink(int colour) {
        int rr = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
        return (rr * 299 + g * 587 + b * 114) / 1000 > 150 ? 0xFF0A0D33 : 0xFFFFFFFF;
    }

    /** A club's colour from ESPN's hex ("ffff00"); white when none, its other colour when too dark to see on navy. */
    static int colour(String hex, String alt) {
        int c = parse(hex);
        if (c == 0 || dark(c)) {
            int a = parse(alt);
            if (a != 0 && !dark(a)) return a;
        }
        return c == 0 ? 0xFFFFFFFF : dark(c) ? 0xFFDDE3FF : c;
    }

    private static boolean dark(int c) {
        int rr = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (rr * 299 + g * 587 + b * 114) / 1000 < 50;
    }

    private static int parse(String hex) {
        try {
            return hex == null || hex.length() != 6 ? 0 : 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
