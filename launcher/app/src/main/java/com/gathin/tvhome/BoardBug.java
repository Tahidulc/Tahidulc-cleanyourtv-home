package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.Objects;

/**
 * The scoreboard BoardCover puts over the channel's own, as the phone's player draws it (Homefield's OverlayBug), along
 * one row: the competition, each club's colour, crest and three letters, the score, and the clock in minutes and
 * seconds with the minutes added beside it. Classic is the launcher's navy with the score on blue; Minimal one line of
 * words on a dark ground. Kind to the panel: dark grounds only, the words a soft white rather than a pure one, and an
 * edge of its ground (inset) round everything, so the pixel or two it moves against burn-in never uncovers what it
 * covers. A goal lights the scoring club's side in its colour for four seconds, GOAL in the clock's box.
 */
final class BoardBug extends View {
    /** What it shows. */
    static final class Look {
        String home = "", away = "", score = "", clock = "", added = "", tag = "";
        int homeColour = INK, awayColour = INK;
        boolean live;

        @Override public boolean equals(Object o) {
            if (!(o instanceof Look)) return false;
            Look l = (Look) o;
            return live == l.live && home.equals(l.home) && away.equals(l.away) && score.equals(l.score) && clock.equals(l.clock)
                    && added.equals(l.added) && tag.equals(l.tag) && homeColour == l.homeColour && awayColour == l.awayColour;
        }

        @Override public int hashCode() {
            return Objects.hash(home, away, score, clock, added);
        }
    }

    /** Its looks, as the Scoreboard card names them. */
    enum Style {
        CLASSIC("Classic"), MINIMAL("Minimal");

        final String label;

        Style(String label) {
            this.label = label;
        }

        static Style of(String name) {
            for (Style s : values()) if (s.name().equals(name)) return s;
            return CLASSIC;
        }
    }

    /** The words' white, a little short of pure white, and the quieter words. */
    static final int INK = 0xFFE6E9F2, SOFT = 0xFFA9B1D6;
    private static final int NAVY = 0xFF0B1640, NAVY_DEEP = 0xFF070C26, CLOCK = 0xFF050816, ADDED = 0xFFC81E34,
            AMBER = 0xFFE8A33D, SCORE_TOP = 0xFF2443B4, SCORE_BOTTOM = 0xFF142A78, MINIMAL_GROUND = 0xFF070912;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
            image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private Look look = new Look();
    private Style style = Style.CLASSIC;
    private Bitmap homeCrest, awayCrest, comp;
    /** Room kept for the crests (the game has them), so the board's width holds as they arrive. */
    private boolean crests;
    /** Its ground's edge round everything, in pixels (the room for BoardCover's burn-in step). */
    private int inset;
    private boolean outlined;
    /** Made once for a size and kept: the score's blue for a height, the ring's dashes for a width of line. */
    private LinearGradient scoreBlue;
    private float scoreBlueH = -1, dashFor = -1;
    /** The goal's light: 1 to 0 over its seconds, on side 1 (home) or 2 (away). */
    private float flash;
    private int flashSide;
    private long flashUntil;

    BoardBug(Context c) {
        super(c);
        text.setTypeface(ScoreCard.outfit(c, 800));
        text.setFontFeatureSettings("tnum");
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(INK);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void inset(int px) {
        inset = px;
        invalidate();
    }

    void style(Style s) {
        if (s == style) return;
        style = s;
        invalidate();
    }

    Style style() {
        return style;
    }

    /** Whether the game has crests: their room is kept from the start. */
    void crests(boolean expected) {
        crests = expected;
    }

    void images(Bitmap home, Bitmap away, Bitmap competition) {
        homeCrest = home;
        awayCrest = away;
        comp = competition;
        invalidate();
    }

    /** Ringed while Move and resize has it. */
    void outline(boolean on) {
        if (on == outlined) return;
        outlined = on;
        invalidate();
    }

    Look look() {
        return look;
    }

    /** What to show; drawn again only when it changed. */
    void set(Look l) {
        if (l.equals(look)) return;
        look = l;
        setContentDescription(l.home + " " + l.score + " " + l.away + (l.clock.isEmpty() ? "" : ", " + l.clock + " " + l.added));
        invalidate();
    }

    /** A goal for side 1 (home) or 2 (away): its half lit in its colour for four seconds. */
    void goal(int side) {
        flashSide = side;
        flashUntil = System.currentTimeMillis() + 4000;
        invalidate();
    }

    // ---- size ------------------------------------------------------------------------------------------------------

    /** Its width for words h high (its edge aside). */
    int natural(int h) {
        if (style == Style.MINIMAL) return Math.round(minimalWidth(h));
        return Math.round(h + 2 * (h * 0.1f + crestW(h) + code(h)) + scoreW(h) + clockW(h));
    }

    private float measure(String s, float size) {
        text.setTextSize(size);
        return text.measureText(s == null ? "" : s);
    }

    private float crestW(float h) {
        return crests ? h * 0.86f : 0;
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

    // ---- drawing ---------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        long now = System.currentTimeMillis();
        flash = now < flashUntil ? (flashUntil - now) / 4000f : 0f;
        int cw = w - 2 * inset, ch = h - 2 * inset;
        float rad = style == Style.MINIMAL ? h * 0.25f : h * 0.14f;
        r.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        fill.setShader(null);
        fill.setColor(style == Style.MINIMAL ? MINIMAL_GROUND : NAVY);
        cv.drawRect(r, fill);
        cv.translate(inset, inset);
        if (cw > 0 && ch > 0) {
            if (style == Style.MINIMAL) minimal(cv, cw, ch); else oneRow(cv, cw, ch);
        }
        cv.restore();
        if (outlined) {
            float sw = Math.max(2f, h * 0.05f);
            if (sw != dashFor) {
                dashFor = sw;
                edge.setStrokeWidth(sw);
                edge.setPathEffect(new DashPathEffect(new float[]{sw * 3, sw * 2}, 0));
            }
            r.set(sw / 2, sw / 2, w - sw / 2, h - sw / 2);
            cv.drawRoundRect(r, rad, rad, edge);
        }
        if (flash > 0) postInvalidateDelayed(33);
    }

    /** The clubs, the score and the clock along one row of w by h. */
    private void oneRow(Canvas cv, float w, float h) {
        int hi = Math.round(h);
        boolean withCrests = true, compBlock = true;
        float codeW = code(hi), strip = h * 0.1f, crestW = crestW(h), score = scoreW(hi), clock = clockW(hi);
        // A pixel's grace: its own width is the sum rounded.
        float room = w + 1.5f;
        float need = h + 2 * (strip + crestW + codeW) + score + clock;
        if (need > room && crestW > 0) {
            withCrests = false;
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
        float cx = 0;
        if (compBlock) {
            fill.setColor(NAVY_DEEP);
            cv.drawRect(cx, 0, cx + h, h, fill);
            if (comp != null) drawImage(cv, comp, cx + h * 0.16f, h * 0.16f, h * 0.68f);
            else words(cv, look.tag, cx + h / 2, 0, h, h * 0.3f, SOFT);
            cx += h;
        }
        float crest = withCrests ? crestW : 0;
        cx = side(cv, cx, h, strip, crest, codeW, k, true);
        if (scoreBlue == null || scoreBlueH != h) {
            scoreBlueH = h;
            scoreBlue = new LinearGradient(0, 0, 0, h, SCORE_TOP, SCORE_BOTTOM, Shader.TileMode.CLAMP);
        }
        fill.setShader(scoreBlue);
        cv.drawRect(cx, 0, cx + score, h, fill);
        fill.setShader(null);
        words(cv, look.score, cx + score / 2, 0, h, h * 0.56f * k, INK);
        cx += score;
        cx = side(cv, cx, h, strip, crest, codeW, k, false);
        if (clock > 0) clock(cv, cx, w - cx, h, k);
    }

    /** One club's part: (home) strip, crest, letters; (away) letters, crest, strip. Returns where it ends. */
    private float side(Canvas cv, float cx, float h, float strip, float crestW, float codeW, float k, boolean home) {
        int colour = home ? look.homeColour : look.awayColour;
        float width = strip + crestW + codeW;
        if (flash > 0 && flashSide == (home ? 1 : 2)) {
            fill.setColor(colour);
            fill.setAlpha(Math.round(200 * flash));
            cv.drawRect(cx, 0, cx + width, h, fill);
            fill.setAlpha(255);
        }
        Bitmap crest = home ? homeCrest : awayCrest;
        String code = home ? look.home : look.away;
        if (home) {
            fill.setColor(colour);
            cv.drawRect(cx, 0, cx + strip, h, fill);
            if (crestW > 0 && crest != null) drawImage(cv, crest, cx + strip + crestW * 0.14f, (h - crestW * 0.72f) / 2, crestW * 0.72f);
            words(cv, code, cx + strip + crestW + codeW / 2, 0, h, h * 0.46f * k, INK);
        } else {
            words(cv, code, cx + codeW / 2, 0, h, h * 0.46f * k, INK);
            if (crestW > 0 && crest != null) drawImage(cv, crest, cx + codeW + crestW * 0.14f, (h - crestW * 0.72f) / 2, crestW * 0.72f);
            fill.setColor(colour);
            cv.drawRect(cx + codeW + crestW, 0, cx + width, h, fill);
        }
        return cx + width;
    }

    /** The clock's box: minutes and seconds, the minutes added on red; GOAL as one goes in. */
    private void clock(Canvas cv, float x, float w, float h, float k) {
        fill.setColor(CLOCK);
        cv.drawRect(x, 0, x + w, h, fill);
        if (flash > 0) {
            int colour = flashSide == 1 ? look.homeColour : look.awayColour;
            fill.setColor(colour);
            cv.drawRect(x, 0, x + w, h, fill);
            words(cv, "GOAL", x + w / 2, 0, h, h * 0.42f * k, ink(colour));
            return;
        }
        int colour = "HT".equals(look.clock) ? AMBER : look.live ? INK : SOFT;
        if (look.added.isEmpty()) {
            words(cv, look.clock, x + w / 2, 0, h, h * 0.44f * k, colour);
            return;
        }
        float cw = measure(look.clock, h * 0.44f * k), aw = measure(look.added, h * 0.34f * k) + h * 0.3f;
        float start = x + (w - cw - aw - h * 0.15f) / 2;
        words(cv, look.clock, start + cw / 2, 0, h, h * 0.44f * k, colour);
        float ax = start + cw + h * 0.15f;
        fill.setColor(ADDED);
        r.set(ax, h * 0.24f, ax + aw, h * 0.76f);
        cv.drawRoundRect(r, h * 0.08f, h * 0.08f, fill);
        words(cv, look.added, ax + aw / 2, 0, h, h * 0.34f * k, INK);
    }

    /** Minimal: one line, "LIV  2–1  MUN   67:23 +2". */
    private String[] minimalParts() {
        return new String[]{look.home + "  " + look.score + "  " + look.away, look.clock, look.added};
    }

    private float minimalWidth(int h) {
        String[] m = minimalParts();
        float w = h * 0.9f + measure(m[0], h * 0.48f) + measure(m[1], h * 0.44f) + h * 0.7f;
        if (!m[2].isEmpty()) w += measure(m[2], h * 0.38f) + h * 0.5f;
        return w;
    }

    private void minimal(Canvas cv, int w, int h) {
        String[] m = minimalParts();
        float natural = minimalWidth(h), k = natural > w + 1.5f ? Math.max(0.4f, w / natural) : 1f;
        float x = (w - natural * k) / 2 + h * 0.45f * k;
        float a = measure(m[0], h * 0.48f * k);
        words(cv, m[0], x + a / 2, 0, h, h * 0.48f * k, INK);
        x += a + h * 0.7f * k;
        float b = measure(m[1], h * 0.44f * k);
        int clockInk = "HT".equals(look.clock) ? AMBER : flash > 0 ? (flashSide == 1 ? look.homeColour : look.awayColour) : INK;
        words(cv, flash > 0 ? "GOAL" : m[1], x + b / 2, 0, h, h * 0.44f * k, clockInk);
        x += b + h * 0.5f * k;
        if (!m[2].isEmpty()) {
            float c = measure(m[2], h * 0.38f * k);
            words(cv, m[2], x + c / 2, 0, h, h * 0.38f * k, ADDED);
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

    /** Dark words on a light colour, the soft white on a dark one. Pure. */
    static int ink(int colour) {
        int rr = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
        return (rr * 299 + g * 587 + b * 114) / 1000 > 150 ? 0xFF0A0D33 : INK;
    }

    /** A club's colour for the navy: its own, else its second when the first is too dark to see, else a pale one. Pure. */
    static int colour(int first, int second) {
        int c = first == 0 ? 0 : 0xFF000000 | first;
        if (c == 0 || dark(c)) {
            int a = second == 0 ? 0 : 0xFF000000 | second;
            if (a != 0 && !dark(a)) return a;
        }
        return c == 0 ? INK : dark(c) ? 0xFFDDE3FF : c;
    }

    private static boolean dark(int c) {
        int rr = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (rr * 299 + g * 587 + b * 114) / 1000 < 50;
    }
}
