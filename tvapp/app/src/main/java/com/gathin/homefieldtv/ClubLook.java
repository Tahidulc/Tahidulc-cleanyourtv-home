package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;

import java.util.Random;

/**
 * The look of the sports pages in a club's or a team's own colour (the squad, a player, an F1 driver), in 07 (the
 * approved mock-ups 19 to 24): the ground in the colour, brightest a little above the middle and deepening to its edges
 * (radial-gradient(1200px 800px at 50% 40%)), with a fine grain and a soft vignette; the cards of frosted glass over it,
 * the remote's brighter; the number's coin in the colour, lit from its top left; the pills (white when on, glass when
 * off, a white ring where the remote is). Where a picture is missing, the pages draw the club's crest and the man's
 * initials, never a drawn person (PlayerArt); only an F1 car with no picture is drawn, in the team's colour. Colours are
 * worked out when a page is given them, never while a frame is drawn; the grain and the shapes are made once.
 */
final class ClubLook {
    private ClubLook() {}

    /** How light the ground, a card (its foot, its top) and the coin are, as relative luminance: white reads on each. */
    static final double GROUND = 0.036, CARD = 0.066, CARD_TOP = 0.10, COIN = 0.14;
    /** 07's ground: its middle, its 55 % ring and its edge (the mock-ups' #1a2cc0, #0c1680, #060b45 for Everton). */
    static final double GROUND_MID = 0.058, GROUND_RING = 0.022, GROUND_EDGE = 0.0072;
    /** 07's coin: its lit top left and its body (the mock-ups' #4f63ff and #2233ee). */
    static final double COIN_LIGHT = 0.17, COIN_BODY = 0.088;
    /** The colour for a club the sources give none for. */
    static final int NEUTRAL = 0xFF465068;
    /** The writing on a white pill. */
    static final int INK = 0xFF121318;

    // ---- colours (pure) ---------------------------------------------------------------------------------------------

    /** Pure: a colour's relative luminance (WCAG's), 0 for black to 1 for white. */
    static double luminance(int c) {
        return 0.2126 * linear((c >> 16) & 0xFF) + 0.7152 * linear((c >> 8) & 0xFF) + 0.0722 * linear(c & 0xFF);
    }

    private static double linear(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** Pure: white's contrast on a colour (WCAG's ratio: 4.5 and over reads easily). */
    static double whiteContrast(int c) {
        return 1.05 / (luminance(c) + 0.05);
    }

    /** Pure: whether a colour has a hue of its own (not white, black or a grey). */
    static boolean chromatic(int c) {
        if (c == 0) return false;
        float[] hsv = new float[3];
        Cine.hsv(c, hsv);
        return hsv[1] >= 0.2f && hsv[2] >= 0.08f;
    }

    /**
     * Pure: the club's colour from what the sources give (0 for none): ESPN's own when it has a hue, else the other
     * source's (FotMob's), else ESPN's second colour (Tottenham's white gives its navy), else whatever there is.
     */
    static int pick(int espn, int espnAlt, int other) {
        if (chromatic(espn)) return espn;
        if (chromatic(other)) return other;
        if (chromatic(espnAlt)) return espnAlt;
        return espn != 0 ? espn : other;
    }

    /**
     * Pure: the colour made as light as target (relative luminance): its own hue, at least minSat of strength (a grey
     * stays grey), its brightness found to fit. Brighter than the colour can go, it is at its brightest.
     */
    static int at(int colour, double target, float minSat) {
        int c = colour == 0 ? NEUTRAL : colour;
        float[] hsv = new float[3];
        Cine.hsv(c, hsv);
        float s = hsv[1] < 0.12f ? hsv[1] : Math.max(hsv[1], minSat);
        float lo = 0f, hi = 1f;
        for (int i = 0; i < 22; i++) {
            float mid = (lo + hi) / 2f;
            if (luminance(Cine.colour(hsv[0], s, mid)) > target) hi = mid;
            else lo = mid;
        }
        return Cine.colour(hsv[0], s, lo);
    }

    /** The page's ground: deep, the colour still plain (what the page fades to; 07's middle is groundMid). */
    static int ground(int c) {
        return at(c, GROUND, 0.55f);
    }

    /** 07's ground at its middle, its ring and its edge. */
    static int groundMid(int c) {
        return at(c, GROUND_MID, 0.75f);
    }

    static int groundRing(int c) {
        return at(c, GROUND_RING, 0.8f);
    }

    static int groundEdge(int c) {
        return at(c, GROUND_EDGE, 0.8f);
    }

    /** A card's foot, a shade lighter than the ground; its top, lighter again (an F1 card, a table's zones). */
    static int card(int c) {
        return at(c, CARD, 0.5f);
    }

    static int cardTop(int c) {
        return at(c, CARD_TOP, 0.45f);
    }

    /**
     * The club's colour under 07's glass on its page (the mock-ups' glass saturates what is behind it, 180 %, so their
     * tiles and pills are a vivid shade of the club's colour, not a grey): drawn under Frost.glass.
     */
    static int glassTint(int c) {
        return at(c, 0.07, 0.9f);
    }

    /** The shirt number's coin: the colour at its brightest that still carries white. */
    static int coin(int c) {
        return at(c, COIN, 0.7f);
    }

    /** Pure: a colour darkened by k (0 as it is, 1 black), its alpha kept. */
    static int dim(int c, float k) {
        float m = 1f - Math.max(0f, Math.min(1f, k));
        return (c & 0xFF000000) | (Math.round(((c >> 16) & 0xFF) * m) << 16) | (Math.round(((c >> 8) & 0xFF) * m) << 8)
                | Math.round((c & 0xFF) * m);
    }

    /** Filters that darken a picture by 0, 5, 10 … 100 %, made once (dimmed(k) picks one: nothing made per frame). */
    private static final ColorFilter[] DIMS = new ColorFilter[21];

    static ColorFilter dimmed(float k) {
        int i = Math.max(0, Math.min(20, Math.round(k * 20f)));
        if (i == 0) return null;
        if (DIMS[i] == null) {
            int g = Math.round(255 * (1f - i / 20f));
            DIMS[i] = new LightingColorFilter(0xFF000000 | g << 16 | g << 8 | g, 0);
        }
        return DIMS[i];
    }

    // ---- the ground, the grain and the vignette -------------------------------------------------------------------

    /**
     * 07's ground for a page w by h (screen pixels) in the club's colour: brightest at the middle a little above half
     * way, deepening through its ring at 55 % to the edge (an ellipse 1200 by 800 design px, as the mock-ups' CSS).
     */
    static void ground(Paint p, int colour, int w, int h) {
        float rx = Ui.f(1200f), ry = Ui.f(800f);
        RadialGradient g = new RadialGradient(0, 0, Math.max(1f, rx), new int[]{groundMid(colour), groundRing(colour), groundEdge(colour)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(1f, ry / Math.max(1f, rx));
        m.postTranslate(w / 2f, h * 0.4f);
        g.setLocalMatrix(m);
        p.setShader(g);
        p.setDither(true);
    }

    private static Bitmap grain;

    /** A paint that lays the fine grain over a page: a little noise, made once, repeated across it. */
    static Paint grain() {
        if (grain == null) {
            int n = 160;
            int[] px = new int[n * n];
            Random r = new Random(23);
            for (int i = 0; i < px.length; i++) {
                int a = r.nextInt(20);
                px[i] = a << 24 | (r.nextBoolean() ? 0xFFFFFF : 0);
            }
            grain = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        }
        Paint p = new Paint();
        p.setShader(new BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        return p;
    }

    /** 07's vignette for a page w by h (screen pixels): clear over the middle 55 %, black at 45 % at the edge (1300 by 900). */
    static void vignette(Paint p, int w, int h) {
        float rx = Ui.f(1300f), ry = Ui.f(900f);
        RadialGradient g = new RadialGradient(0, 0, Math.max(1f, rx), new int[]{0x00000000, 0x00000000, 0x73000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(1f, ry / Math.max(1f, rx));
        m.postTranslate(w / 2f, h * 0.45f);
        g.setLocalMatrix(m);
        p.setShader(g);
        p.setDither(true);
    }

    // ---- the cards' glass (07's .pc .panel) -------------------------------------------------------------------------

    /**
     * A card of frosted glass over the club's ground: white at 20 % fading to 8 % down it, a line of light along its top
     * (35 %), a hairline round it (12 %) and a soft shadow (0 24 50 black 30 %); the remote's card brighter (26 % to 10 %,
     * its light 50 %, its ring 20 %, a deeper shadow: 0 40 80 navy 50 %). Made for a card's height in its own units
     * (draw it under the card's scale); nothing made a frame.
     */
    static final class Glass {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), more = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
                light = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float madeTop = Float.NaN, madeH = Float.NaN;
        static final float[] SHADOW = {24f, 50f, 0f, 77f}, SHADOW_UP = {40f, 80f, 0f, 128f};

        Glass() {
            light.setStyle(Paint.Style.STROKE);
            ring.setStyle(Paint.Style.STROKE);
            Shadows.prepare(SHADOW, 30f);
            Shadows.prepare(SHADOW_UP, 30f);
        }

        private void made(float top, float h) {
            if (top == madeTop && h == madeH) return;
            madeTop = top;
            madeH = h;
            fill.setShader(new LinearGradient(0, top, 0, top + Math.max(1f, h), 0x33FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
            more.setShader(new LinearGradient(0, top, 0, top + Math.max(1f, h), 0x0FFFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP));
            light.setShader(new LinearGradient(0, top, 0, top + Ui.f(40f), 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        }

        /**
         * The glass card r (in the canvas's own units: a card drawn scaled), rounded by radius, up (0 a side card, 1 the
         * remote's) and its opacity; shadows: whether to draw its shadow (screen px scale s for its size).
         */
        void draw(Canvas c, RectF box, float radius, float up, float alpha, float scale) {
            float k = Math.max(0f, Math.min(1f, up)), a = Math.max(0f, Math.min(1f, alpha));
            if (a <= 0.004f) return;
            made(box.top, box.height());
            // The shadows are worked out in screen px: undo the card's scale for them.
            c.save();
            c.scale(1f / scale, 1f / scale);
            r.set(box.left * scale, box.top * scale, box.right * scale, box.bottom * scale);
            Shadows.draw(c, r, radius * scale, SHADOW, (1f - k) * a);
            Shadows.draw(c, r, radius * scale, SHADOW_UP, k * a);
            c.restore();
            fill.setAlpha(Math.round(255 * a));
            c.drawRoundRect(box, radius, radius, fill);
            if (k > 0.004f) {
                more.setAlpha(Math.round(255 * a * k));
                c.drawRoundRect(box, radius, radius, more);
            }
            float sw = Math.max(1f, Ui.f(1.5f)) / scale;
            r.set(box);
            r.inset(sw / 2f, sw / 2f);
            light.setStrokeWidth(sw);
            light.setAlpha(Math.round(255 * a * (0.35f + 0.15f * k)));
            c.drawRoundRect(r, radius, radius, light);
            ring.setStrokeWidth(Math.max(1f, Ui.f(1f)) / scale);
            ring.setColor(0xFFFFFFFF);
            ring.setAlpha(Math.round(255 * a * (0.12f + 0.08f * k)));
            c.drawRoundRect(r, radius, radius, ring);
        }
    }

    // ---- the number's coin (07's .pc .coin) ---------------------------------------------------------------------------

    /**
     * The shirt number's coin in the club's colour: lit from its top left (radial-gradient(circle at 35% 30%) from the
     * colour's light to its body at 70 %), a line of light at its top and a soft navy shadow (0 10 24, 45 %); the number
     * in Barlow Condensed, white. One per page, its shader remade when the colour or the size changes.
     */
    static final class Coin {
        private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), light = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint number;
        private final RectF r = new RectF();
        private int madeFor = 1;
        private float madeR = -1f;
        static final float[] SHADOW = {10f, 24f, 0f, 115f};

        Coin(float numberSize) {
            number = Cine.text(numberSize, Cine.condensed(), 0xFFFFFFFF);
            light.setStyle(Paint.Style.STROKE);
            light.setStrokeCap(Paint.Cap.ROUND);
            Shadows.prepare(SHADOW, 46f);
        }

        /** The colours for a club (when it is given), the coin radius rad (screen px at scale 1). */
        void colour(int club, float rad) {
            if (club == madeFor && rad == madeR) return;
            madeFor = club;
            madeR = rad;
            float d = rad * 2f, far = d * 0.955f;
            body.setShader(new RadialGradient(-rad + d * 0.35f, -rad + d * 0.30f, Math.max(1f, far),
                    new int[]{at(club, COIN_LIGHT, 0.7f), at(club, COIN_BODY, 0.8f), at(club, COIN_BODY, 0.8f)},
                    new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        }

        /**
         * The coin centred on (cx, cy) (the canvas's units: under a card's scale, scale is that scale), its number, at
         * an opacity; darkened by dim (0 to 1).
         */
        void draw(Canvas c, float cx, float cy, float rad, String n, float alpha, float scale, float dim) {
            int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
            if (a <= 0) return;
            c.save();
            c.scale(1f / scale, 1f / scale);
            r.set((cx - rad) * scale, (cy - rad) * scale, (cx + rad) * scale, (cy + rad) * scale);
            Shadows.draw(c, r, rad * scale, SHADOW, alpha);
            c.restore();
            c.save();
            c.translate(cx, cy);
            body.setAlpha(a);
            body.setColorFilter(dimmed(dim));
            c.drawCircle(0, 0, rad, body);
            light.setStrokeWidth(Math.max(1f, Ui.f(1.5f)) / scale);
            light.setColor(0xFFFFFFFF);
            light.setAlpha(Math.round(a * 0.4f));
            r.set(-rad + light.getStrokeWidth(), -rad + light.getStrokeWidth(), rad - light.getStrokeWidth(), rad - light.getStrokeWidth());
            c.drawArc(r, 215f, 110f, false, light);
            if (n != null && !n.isEmpty()) {
                number.setAlpha(a);
                number.setTextSize(rad * 1.0f);
                c.drawText(n, -number.measureText(n) / 2f, Cine.mid(number, 0f), number);
            }
            c.restore();
        }
    }

    // ---- a car with no picture (made once, at 100 units, drawn scaled): never a person --------------------------------

    private static Path car, rim;

    /** A car from the side, its nose to the right, length long, on the ground at (left, ground) (screen pixels). */
    static void car(Canvas c, float left, float ground, float length, Paint body, Paint wheels) {
        if (car == null) {
            Path b = new Path();
            b.moveTo(1f, -5f);
            b.lineTo(1f, -15f);
            b.lineTo(0f, -22f);
            b.lineTo(10f, -22f);
            b.lineTo(10.5f, -16f);
            b.lineTo(15f, -13f);
            b.lineTo(33f, -15.5f);
            b.lineTo(40f, -21f);
            b.lineTo(45f, -21f);
            b.lineTo(48f, -15.5f);
            b.lineTo(61f, -12f);
            b.lineTo(80f, -8.5f);
            b.lineTo(97f, -5.5f);
            b.lineTo(100f, -3f);
            b.lineTo(100f, -0.5f);
            b.lineTo(87f, -0.5f);
            b.lineTo(83f, -3.5f);
            b.lineTo(24f, -3.5f);
            b.lineTo(18f, -1.5f);
            b.lineTo(5f, -1.5f);
            b.close();
            car = b;
            Path w = new Path();
            w.addCircle(20f, -7.6f, 7.6f, Path.Direction.CW);
            w.addCircle(82f, -7.2f, 7.2f, Path.Direction.CW);
            rim = w;
        }
        c.save();
        c.translate(left, ground);
        c.scale(length / 100f, length / 100f);
        c.drawPath(car, body);
        c.drawPath(rim, wheels);
        c.restore();
    }

    // ---- pills (07's .pill: the squad's Players | Management and lines, the hub's sections) -----------------------------

    /**
     * A row of 07's capital pills (the club page's PLAYERS | MANAGEMENT and its lines, the hub's TODAY | TABLES |
     * FORMULA 1): white with dark writing when on, glass with light writing when off, a white ring (3 px, at its edge)
     * where the remote is. Measured once; drawing makes nothing new.
     */
    static final class Pills {
        final String[] words;
        /** Each pill's left edge and width (screen pixels), as last laid out. */
        final float[] x, w;
        private final float h;
        private final TextPaint on, off;
        private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
                tinted = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        /** The colour under an off pill's glass (glassTint: on a club's page), 0 for the glass alone. */
        int tint;

        /** words in capitals; height, letter size and side padding in design px; spacing in em. */
        Pills(String[] words, float height, float size, float pad, float spacing) {
            this.words = words;
            h = height;
            x = new float[words.length];
            w = new float[words.length];
            on = Type.paint(size, Type.BOLD, INK);
            off = Type.paint(size, Type.BOLD, Look07.L2);
            on.setLetterSpacing(spacing);
            off.setLetterSpacing(spacing);
            white.setColor(Look07.FOCUS_FILL);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Math.max(1f, Ui.f(3f)));
            ring.setColor(0xFFFFFFFF);
            for (int i = 0; i < words.length; i++) w[i] = off.measureText(words[i]) - off.getLetterSpacing() * off.getTextSize() + Ui.f(2 * pad);
            Shadows.prepare(Look07.SH_GLASS, height / 2f);
        }

        /** Pills from..to laid out left to right from left (screen pixels), gap apart; the right edge. */
        float from(float left, float gap, int first, int last) {
            float at = left;
            for (int i = first; i <= last; i++) {
                x[i] = at;
                at += w[i] + gap;
            }
            return at - gap;
        }

        /** Pills from..to laid out to end at right (screen pixels), gap apart; the left edge. */
        float to(float right, float gap, int first, int last) {
            float at = right;
            for (int i = last; i >= first; i--) {
                at -= w[i];
                x[i] = at;
                at -= gap;
            }
            return at + gap;
        }

        /** Pill i at cy (screen pixels): chosen (white), the remote on it (the ring), at an opacity (0 to 1). */
        void draw(Canvas c, int i, float cy, boolean chosen, boolean focused, float alpha) {
            float hh = Ui.f(h);
            r.set(x[i], cy - hh / 2f, x[i] + w[i], cy + hh / 2f);
            float a = Math.max(0f, Math.min(1f, alpha));
            int ai = Math.round(255 * a);
            if (chosen) {
                white.setAlpha(Math.round((Look07.FOCUS_FILL >>> 24) * a));
                c.drawRoundRect(r, hh / 2f, hh / 2f, white);
            } else {
                if (tint != 0) {
                    tinted.setColor(tint);
                    tinted.setAlpha(ai);
                    c.drawRoundRect(r, hh / 2f, hh / 2f, tinted);
                }
                Frost.glass(c, r, hh / 2f, a);
            }
            TextPaint t = chosen ? on : off;
            t.setAlpha(chosen ? ai : Math.round((Look07.L2 >>> 24) * a));
            float tw = t.measureText(words[i]) - t.getLetterSpacing() * t.getTextSize();
            c.drawText(words[i], r.centerX() - tw / 2f, Type.centred(t, cy), t);
            if (focused) {
                float k = ring.getStrokeWidth() / 2f;
                ring.setAlpha(ai);
                c.drawRoundRect(r.left - k, r.top - k, r.right + k, r.bottom + k, hh / 2f + k, hh / 2f + k, ring);
            }
        }
    }
}
