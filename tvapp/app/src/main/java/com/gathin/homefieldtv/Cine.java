package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;

/**
 * The look of Movies, TV shows, a title's page and Home (design pixels, 1920 x 1080): a page the colour of the artwork
 * the remote is on, the artwork across it, big lettering, the red Play pill and grey buttons, tiles that grow where the
 * remote is. Outfit for the headings and Home's titles, Inter for the rest and Barlow Condensed for a title's lettering
 * (the faces broadcast/fonts already carries). Nothing here is made while a frame is drawn: shaders are made when a
 * colour or a size changes (Wash), paints at start.
 */
final class Cine {
    private Cine() {}

    /** The Play pill and progress, and the Play pill lit; Homefield's amber. */
    static final int RED = 0xFFE3262E, RED_LIT = 0xFFF23A41, AMBER = 0xFFFFB547, AMBER_INK = 0xFF1E1606;
    static final int TEXT = 0xFFFFFFFF, SOFT = 0xE6FFFFFF, MUTED = 0xB3FFFFFF, FAINT = 0x80FFFFFF, LINE = 0x66FFFFFF;
    /** The grey buttons (My list, Trailer…), white with the ink on it where the remote is. */
    static final int BUTTON = 0x66808085, WHITE = 0xFFFFFFFF, INK = 0xFF141416;
    /** The page before its artwork's colour is known. */
    static final int GROUND = 0xFF15161B;

    // ---- words ----------------------------------------------------------------------------------------------------

    private static Context app;
    private static final Typeface[] OUTFIT = new Typeface[10];
    private static Typeface condensed;

    static void init(Context c) {
        app = c.getApplicationContext();
        Type.init(c);
    }

    /** Outfit at a weight (100 to 900, the variable font's axis); the system's bold or plain when it can't be read. */
    static Typeface outfit(int weight) {
        int i = Math.max(1, Math.min(9, Math.round(weight / 100f)));
        Typeface t = OUTFIT[i];
        if (t != null) return t;
        try {
            if (app == null) throw new IllegalStateException("not started");
            t = new Typeface.Builder(app.getAssets(), "broadcast/fonts/outfit.ttf").setFontVariationSettings("'wght' " + (i * 100)).build();
        } catch (RuntimeException e) {
            t = null;
        }
        if (t == null) t = Typeface.create("sans-serif", i >= 6 ? Typeface.BOLD : Typeface.NORMAL);
        OUTFIT[i] = t;
        return t;
    }

    /** Inter for the words (its medium cut: the app's own, Type); semi for the stronger ones. */
    static Typeface inter() {
        return Type.face(Type.MEDIUM);
    }

    static Typeface interSemi() {
        return Type.face(Type.SEMIBOLD);
    }

    /** A title's own lettering when it has no logo: tall and bold (Barlow Condensed). */
    static Typeface condensed() {
        if (condensed == null) condensed = asset("broadcast/fonts/BarlowCondensed-800.ttf", Typeface.BOLD);
        return condensed;
    }

    private static Typeface asset(String path, int fallback) {
        try {
            if (app != null) return Typeface.createFromAsset(app.getAssets(), path);
        } catch (RuntimeException ignored) {
            // the system's
        }
        return Typeface.create("sans-serif", fallback);
    }

    /** A paint for words of a design size in a face and colour. */
    static TextPaint text(float designSize, Typeface face, int colour) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face);
        p.setTextSize(Ui.f(designSize));
        p.setColor(colour);
        return p;
    }

    /** The baseline that centres a line of p on cy. */
    static float mid(TextPaint p, float cy) {
        Paint.FontMetrics m = p.getFontMetrics();
        return cy - (m.ascent + m.descent) / 2f;
    }

    // ---- colours from artwork (pure) --------------------------------------------------------------------------------

    /**
     * Pure: an artwork's colour from a few of its pixels: the hue where it is most colourful (each pixel counting by how
     * saturated and bright it is), at the strength it has there; a grey picture gives its grey.
     */
    static int vivid(int[] px) {
        double sx = 0, sy = 0, w = 0, ws = 0, wv = 0, r = 0, g = 0, b = 0;
        int n = 0;
        float[] hsv = new float[3];
        for (int p : px) {
            if ((p >>> 24) < 0x40) continue;
            n++;
            r += (p >> 16) & 0xFF;
            g += (p >> 8) & 0xFF;
            b += p & 0xFF;
            hsv(p, hsv);
            double k = hsv[1] * hsv[2];
            k *= k;
            if (k <= 0) continue;
            double a = Math.toRadians(hsv[0]);
            sx += Math.cos(a) * k;
            sy += Math.sin(a) * k;
            w += k;
            ws += hsv[1] * k;
            wv += hsv[2] * k;
        }
        if (n == 0) return 0;
        int grey = 0xFF000000 | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
        if (w < n * 0.004) return grey;
        float h = (float) Math.toDegrees(Math.atan2(sy, sx));
        if (h < 0) h += 360f;
        return colour(h, (float) (ws / w), (float) (wv / w));
    }

    /** Pure: a colour as its hue (0-360), saturation and value (0-1). */
    static void hsv(int c, float[] out) {
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d <= 0) h = 0;
        else if (max == r) h = 60f * (((g - b) / d) % 6f);
        else if (max == g) h = 60f * ((b - r) / d + 2f);
        else h = 60f * ((r - g) / d + 4f);
        if (h < 0) h += 360f;
        out[0] = h;
        out[1] = max <= 0 ? 0 : d / max;
        out[2] = max;
    }

    /** Pure: the colour of a hue (0-360), saturation and value (0-1), opaque. */
    static int colour(float h, float s, float v) {
        s = Math.max(0f, Math.min(1f, s));
        v = Math.max(0f, Math.min(1f, v));
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2f - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    private static final float[] HSV = new float[3];

    /**
     * Pure: an artwork's colour made into a page's: its hue, no stronger than sat, at the brightness given. On the main
     * thread only (it works in one kept array, so a frame makes nothing).
     */
    static int shade(int art, float sat, float value) {
        if (art == 0) return GROUND;
        hsv(art, HSV);
        return colour(HSV[0], Math.min(HSV[1], sat), value);
    }

    /** Pure: between two colours, t of the way from a to b (alpha too). */
    static int mix(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** A colour at an alpha (0-255) of its own. */
    static int alpha(int colour, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (colour & 0xFFFFFF);
    }

    // ---- small shapes ---------------------------------------------------------------------------------------------

    private static final Path TRI = new Path();
    private static final RectF BOX = new RectF();

    /** A play triangle pointing right, its middle at (cx, cy), size its height. */
    static void play(Canvas c, float cx, float cy, float size, Paint p) {
        TRI.reset();
        float hgt = size, w = size * 0.86f;
        TRI.moveTo(cx - w * 0.4f, cy - hgt / 2f);
        TRI.lineTo(cx + w * 0.6f, cy);
        TRI.lineTo(cx - w * 0.4f, cy + hgt / 2f);
        TRI.close();
        c.drawPath(TRI, p);
    }

    /** A plus, its middle at (cx, cy), size across, in a stroke. */
    static void plus(Canvas c, float cx, float cy, float size, Paint stroke) {
        float h = size / 2f;
        c.drawLine(cx - h, cy, cx + h, cy, stroke);
        c.drawLine(cx, cy - h, cx, cy + h, stroke);
    }

    /** A tick, its middle at (cx, cy), size across, in a stroke. */
    static void tick(Canvas c, float cx, float cy, float size, Paint stroke) {
        float h = size / 2f;
        c.drawLine(cx - h, cy + h * 0.05f, cx - h * 0.25f, cy + h * 0.7f, stroke);
        c.drawLine(cx - h * 0.25f, cy + h * 0.7f, cx + h, cy - h * 0.6f, stroke);
    }

    /** Words in a thin outlined box ("15", "4K", "HDR") with its left at x on the baseline; its width. */
    static float chip(Canvas c, String words, float x, float baseline, TextPaint p, Paint line) {
        float pad = Ui.f(9f), w = p.measureText(words);
        Paint.FontMetrics m = p.getFontMetrics();
        float top = baseline + m.ascent * 0.86f - Ui.f(6f), bottom = baseline + m.descent + Ui.f(3f);
        BOX.set(x, top, x + w + pad * 2, bottom);
        c.drawRoundRect(BOX, Ui.f(4f), Ui.f(4f), line);
        c.drawText(words, x + pad, baseline, p);
        return BOX.width();
    }

    /** A chip's width, as chip() draws it. */
    static float chipWidth(String words, TextPaint p) {
        return p.measureText(words) + Ui.f(18f);
    }

    // ---- the page's colour ----------------------------------------------------------------------------------------

    /**
     * The page's colour behind and over the artwork: its ground, and fades of that colour over the picture (the left,
     * the foot) so the words stand on it. A new colour fades in over the old (both drawn, their shares changing): its
     * shaders are made when the colour is given (a key, a picture arriving), never while a frame is drawn.
     */
    static final class Wash {
        private final List<float[]> fades = new ArrayList<>();
        private Shader[] now = new Shader[0], was = new Shader[0];
        private int nowColour, wasColour;
        private final Glide mix = new Glide(520, 300f);
        private final Paint p = new Paint(Paint.DITHER_FLAG);

        /**
         * A fade of the colour over the rect (l, t, r, b), along the line from (x0, y0) to (x1, y1): a0 of the colour at
         * its start, a1 at share p1 of the way, a2 at its end (alphas 0-1).
         */
        void add(float l, float t, float r, float b, float x0, float y0, float x1, float y1, float a0, float a1, float p1, float a2) {
            fades.add(new float[]{l, t, r, b, x0, y0, x1, y1, a0, a1, p1, a2});
            if (nowColour != 0) now = shaders(nowColour);
            if (wasColour != 0) was = shaders(wasColour);
        }

        void clear() {
            fades.clear();
            now = new Shader[0];
            was = new Shader[0];
        }

        /** The colour to fade to (its shaders made now); the same colour again changes nothing. */
        void set(int colour) {
            colour |= 0xFF000000;
            if (colour == nowColour) return;
            if (nowColour == 0) {
                nowColour = colour;
                now = shaders(colour);
                mix.jump(1f);
                return;
            }
            wasColour = colour();
            was = shaders(wasColour);
            nowColour = colour;
            now = shaders(colour);
            mix.jump(0f);
            mix.to(1f);
        }

        /** At the colour at once (a page opened). */
        void jump(int colour) {
            nowColour = colour | 0xFF000000;
            now = shaders(nowColour);
            wasColour = 0;
            was = new Shader[0];
            mix.jump(1f);
        }

        /** The colour now, as it fades. */
        int colour() {
            if (nowColour == 0) return GROUND;
            return wasColour == 0 ? nowColour : mix(wasColour, nowColour, mix.value());
        }

        /** One frame's step; true while it is still fading. */
        boolean step() {
            boolean moving = mix.step();
            if (!moving && mix.value() >= 1f) wasColour = 0;
            return moving;
        }

        /** The fades over the picture, as the colour is now. */
        void draw(Canvas c) {
            float k = mix.value();
            for (int i = 0; i < fades.size(); i++) {
                float[] f = fades.get(i);
                if (wasColour != 0 && k < 1f && i < was.length) {
                    p.setShader(was[i]);
                    p.setAlpha(Math.round(255 * (1f - k)));
                    c.drawRect(f[0], f[1], f[2], f[3], p);
                }
                if (i < now.length) {
                    p.setShader(now[i]);
                    p.setAlpha(wasColour != 0 ? Math.round(255 * k) : 255);
                    c.drawRect(f[0], f[1], f[2], f[3], p);
                }
            }
        }

        private Shader[] shaders(int colour) {
            Shader[] out = new Shader[fades.size()];
            for (int i = 0; i < out.length; i++) {
                float[] f = fades.get(i);
                out[i] = new LinearGradient(f[4], f[5], f[6], f[7],
                        new int[]{alpha(colour, Math.round(255 * f[8])), alpha(colour, Math.round(255 * f[9])), alpha(colour, Math.round(255 * f[11]))},
                        new float[]{0f, f[10], 1f}, Shader.TileMode.CLAMP);
            }
            return out;
        }
    }
}
