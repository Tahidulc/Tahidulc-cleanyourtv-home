package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;

import java.util.Random;

/**
 * The look of the sports pages in a club's or a team's own colour (the squad, a player, an F1 driver), after the family's
 * two pictures: the ground in the colour, deep enough for white writing, with a fine grain and a soft vignette; the
 * cards a shade lighter; the number's coin in the colour at its brightest that still carries white; the pills (white
 * when on, dark when off, a ring where the remote is); and the shapes drawn where a source has no picture (a head and
 * shoulders, a driver standing, a car from the side), in the colour. Colours are worked out when a page is given them,
 * never while a frame is drawn; the grain and the shapes are made once.
 */
final class ClubLook {
    private ClubLook() {}

    /** How light the ground, a card (its foot, its top) and the coin are, as relative luminance: white reads on each. */
    static final double GROUND = 0.036, CARD = 0.066, CARD_TOP = 0.10, COIN = 0.14;
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

    /** The page's ground: deep, the colour still plain. */
    static int ground(int c) {
        return at(c, GROUND, 0.55f);
    }

    /** A card's foot, a shade lighter than the ground; its top, lighter again. */
    static int card(int c) {
        return at(c, CARD, 0.5f);
    }

    static int cardTop(int c) {
        return at(c, CARD_TOP, 0.45f);
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

    // ---- the grain and the vignette ---------------------------------------------------------------------------------

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

    /** The vignette for a page w by h (screen pixels): clear over the middle, darker to the edges and corners. */
    static void vignette(Paint p, int w, int h) {
        float r = (float) Math.hypot(w, h) * 0.58f;
        p.setShader(new RadialGradient(w / 2f, h * 0.48f, Math.max(1f, r), new int[]{0x00000000, 0x00000000, 0x33000000, 0x99000000},
                new float[]{0f, 0.42f, 0.74f, 1f}, Shader.TileMode.CLAMP));
    }

    /** A soft light behind the middle of a page (over any ground): white fading to nothing. */
    static void glow(Paint p, float cx, float cy, float r) {
        p.setShader(new RadialGradient(cx, cy, Math.max(1f, r), new int[]{0x2EFFFFFF, 0x12FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    // ---- shapes where there is no picture (made once, at 100 units, drawn scaled) -------------------------------------

    private static Path bust, figure, car, rim;

    /** A head and shoulders h tall, the middle of its foot at (cx, bottom) (screen pixels). */
    static void bust(Canvas c, float cx, float bottom, float h, Paint p) {
        if (bust == null) {
            Path b = new Path();
            b.moveTo(-47f, 0f);
            b.cubicTo(-47f, -21f, -40f, -35f, -21f, -40f);
            b.lineTo(-9f, -43f);
            b.lineTo(9f, -43f);
            b.lineTo(21f, -40f);
            b.cubicTo(40f, -35f, 47f, -21f, 47f, 0f);
            b.close();
            b.addRect(-8f, -54f, 8f, -40f, Path.Direction.CW);
            b.addOval(new RectF(-17f, -95f, 17f, -50f), Path.Direction.CW);
            bust = b;
        }
        c.save();
        c.translate(cx, bottom);
        c.scale(h / 100f, h / 100f);
        c.drawPath(bust, p);
        c.restore();
    }

    /** A driver standing, h tall, the middle of his head's top at (cx, top) (screen pixels). */
    static void standing(Canvas c, float cx, float top, float h, Paint p) {
        if (figure == null) {
            Path f = new Path();
            f.addOval(new RectF(-6.5f, 0f, 6.5f, 15f), Path.Direction.CW);
            f.addRect(-3f, 13f, 3f, 19f, Path.Direction.CW);
            f.moveTo(-15f, 21f);
            f.quadTo(0f, 16f, 15f, 21f);
            f.lineTo(13f, 54f);
            f.lineTo(-13f, 54f);
            f.close();
            f.addRoundRect(new RectF(-21f, 21f, -14f, 53f), 3.5f, 3.5f, Path.Direction.CW);
            f.addRoundRect(new RectF(14f, 21f, 21f, 53f), 3.5f, 3.5f, Path.Direction.CW);
            f.addRoundRect(new RectF(-12.5f, 50f, -1.5f, 99f), 4f, 4f, Path.Direction.CW);
            f.addRoundRect(new RectF(1.5f, 50f, 12.5f, 99f), 4f, 4f, Path.Direction.CW);
            figure = f;
        }
        c.save();
        c.translate(cx, top);
        c.scale(h / 100f, h / 100f);
        c.drawPath(figure, p);
        c.restore();
    }

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

    private static Path shirt, collar;

    /**
     * The shirt a face stands on, at 100 units the face's size: its round neck's ends at (±17, 0), its shoulders out to
     * ±54, down to 66 (where its paint, kit(), has faded out). Made once.
     */
    static Path shirt() {
        if (shirt == null) {
            Path s = new Path();
            s.moveTo(-17f, 0f);
            s.quadTo(0f, 10f, 17f, 0f);
            s.cubicTo(29f, 3f, 46f, 6f, 52f, 19f);
            s.lineTo(55f, 66f);
            s.lineTo(-55f, 66f);
            s.lineTo(-52f, 19f);
            s.cubicTo(-46f, 6f, -29f, 3f, -17f, 0f);
            s.close();
            shirt = s;
        }
        return shirt;
    }

    /** The shirt's round neck, a band for collar's paint. */
    static Path collar() {
        if (collar == null) {
            Path s = new Path();
            s.moveTo(-17f, 0f);
            s.quadTo(0f, 10f, 17f, 0f);
            collar = s;
        }
        return collar;
    }

    static Paint collarPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.6f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFFFFFFFF);
        return p;
    }

    /** The shirt's paint for a club's colour: a shade lighter than the cards, fading out down its body (shirt()'s units). */
    static void kit(Paint p, int colour) {
        int k = at(colour, 0.095, 0.65f);
        p.setShader(new android.graphics.LinearGradient(0, 0, 0, 66f, new int[]{k, k, Cine.alpha(k, 0)}, new float[]{0f, 0.42f, 1f},
                Shader.TileMode.CLAMP));
    }

    // ---- pills ------------------------------------------------------------------------------------------------------

    /**
     * A row of pills, after the squad page's "Players | Management": white with dark writing when on, dark glass with
     * white writing when off, a white ring round the one the remote is on. Measured once; drawing makes nothing new.
     */
    static final class Pills {
        static final float H = 52f, PAD = 28f;
        final String[] words;
        /** Each pill's left edge and width (screen pixels), as last laid out. */
        final float[] x, w;
        private final TextPaint on, off;
        private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG), glass = new Paint(Paint.ANTI_ALIAS_FLAG),
                ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Pills(String[] words, float size) {
            this.words = words;
            x = new float[words.length];
            w = new float[words.length];
            on = Cine.text(size, Cine.outfit(700), INK);
            off = Cine.text(size, Cine.outfit(700), 0xFFFFFFFF);
            on.setLetterSpacing(0.08f);
            off.setLetterSpacing(0.08f);
            white.setColor(0xFFFFFFFF);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Math.max(1f, Ui.f(3f)));
            ring.setColor(0xFFFFFFFF);
            for (int i = 0; i < words.length; i++) w[i] = off.measureText(words[i]) + Ui.f(2 * PAD);
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
            float h = Ui.f(H);
            r.set(x[i], cy - h / 2f, x[i] + w[i], cy + h / 2f);
            int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
            if (chosen) {
                white.setAlpha(a);
                c.drawRoundRect(r, h / 2f, h / 2f, white);
            } else {
                glass.setColor(focused ? 0x52FFFFFF : 0x33000000);
                glass.setAlpha(Math.round(glass.getAlpha() * a / 255f));
                c.drawRoundRect(r, h / 2f, h / 2f, glass);
            }
            TextPaint t = chosen ? on : off;
            t.setAlpha(chosen ? a : Math.round((focused ? 255 : 215) * a / 255f));
            c.drawText(words[i], r.centerX() - (t.measureText(words[i]) - t.getLetterSpacing() * t.getTextSize()) / 2f, Cine.mid(t, cy), t);
            if (focused) {
                float k = Ui.f(6f);
                ring.setAlpha(a);
                c.drawRoundRect(r.left - k, r.top - k, r.right + k, r.bottom + k, h / 2f + k, h / 2f + k, ring);
            }
        }
    }
}
