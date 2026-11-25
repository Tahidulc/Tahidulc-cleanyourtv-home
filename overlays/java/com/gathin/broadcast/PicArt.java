package com.gathin.broadcast;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Programme pictures drawn, the same in Homefield TV and the phone app (off the main thread, once, at the size asked):
 *
 * <pre>
 *   compose(src, lay, w, h)   a picture placed by PicPlace in a place w x h: covering it, or shown whole with the
 *                             place's own colour from the picture continuing smoothly behind it (its edges' colours
 *                             drawn out, a little quieter) and the picture's inner edges feathered into that colour
 *   sharp(src, lay, w, h)     the picture alone as PicPlace put it, its feathered edges see-through (a guide cell's,
 *                             that glides along its cell: PicPlace.slide), with edges(): its ground's colours
 *   genre(kind, w, h)         a picture for what a programme is (PicSources.KINDS): a pitch, a court, a ring, a globe,
 *                             a projector's beam... in its colours, drawn in code (no picture of anyone, no words), its
 *                             motif toward the right where a place's words aren't
 *   fixture(home, away, ...)  a game: the two crests side by side on their clubs' colours
 *   brand(colour, w, h)       the channel's own ground with no logo: its colour, deep, softly lit
 * </pre>
 *
 * The made-up ones are 16:9 pictures like any other, so PicPlace places them as it places a programme's own.
 */
public final class PicArt {
    private PicArt() {}

    /** How much quieter the ground behind a picture is than its edges (the words over it stay white on it). */
    public static final float GROUND = 0.84f;
    /** The made-up pictures' shape. */
    public static final float ASPECT = 16f / 9f;

    // ---- a picture placed -------------------------------------------------------------------------------------------

    /** Picture src placed by lay (PicPlace.place for src's own size) filling w x h: opaque, made once. */
    public static Bitmap compose(Bitmap src, float[] lay, int w, int h, Bitmap.Config config) {
        Bitmap out = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), config);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        if (lay[PicPlace.KIND] == PicPlace.COVER) {
            // Over its own colour, so a part pixel at an edge is never black.
            c.drawColor(quiet(average(src), 1f));
            c.drawBitmap(src, matrix(src, lay), p);
            return out;
        }
        ground(c, src, lay, w, h);
        float[] at = new float[2];
        Bitmap s = sharp(src, lay, w, h, at);
        c.drawBitmap(s, at[0], at[1], p);
        s.recycle();
        return out;
    }

    /**
     * The picture alone as lay puts it in w x h, cut to the place (its height for a wide place, its width for a narrow
     * one), its feathered edges faded to nothing; where it goes in the place into at {x, y}. Covering: the place's size.
     */
    public static Bitmap sharp(Bitmap src, float[] lay, int w, int h, float[] at) {
        boolean cover = lay[PicPlace.KIND] == PicPlace.COVER, wide = !cover && lay[PicPlace.FEATHER_T] == 0f && lay[PicPlace.FEATHER_B] == 0f
                && lay[PicPlace.PH] >= h - 0.5f;
        float x = lay[PicPlace.X], y = lay[PicPlace.Y], pw = lay[PicPlace.PW], ph = lay[PicPlace.PH];
        int bw, bh;
        float ox, oy;
        if (cover) {
            bw = w;
            bh = h;
            ox = 0f;
            oy = 0f;
        } else if (wide) {
            // As tall as the place; across, the picture's own width (inside the place).
            float l = Math.max(0f, x), r = Math.min(w, x + pw);
            ox = (float) Math.floor(l);
            bw = Math.max(1, (int) Math.ceil(r) - (int) ox);
            bh = h;
            oy = 0f;
        } else {
            float t = Math.max(0f, y), b = Math.min(h, y + ph);
            oy = (float) Math.floor(t);
            bh = Math.max(1, (int) Math.ceil(b) - (int) oy);
            bw = w;
            ox = 0f;
        }
        Bitmap out = Bitmap.createBitmap(bw, bh, cover ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        if (cover) c.drawColor(quiet(average(src), 1f));
        Matrix m = matrix(src, lay);
        m.postTranslate(-ox, -oy);
        c.drawBitmap(src, m, p);
        if (!cover) {
            Paint cut = new Paint(Paint.DITHER_FLAG);
            cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            int[] ramp = {0x00000000, 0x8C000000, 0xFF000000};
            float[] stops = {0f, 0.55f, 1f};
            float fl = lay[PicPlace.FEATHER_L], fr = lay[PicPlace.FEATHER_R], ft = lay[PicPlace.FEATHER_T], fb = lay[PicPlace.FEATHER_B];
            float x0 = x - ox, x1 = x + pw - ox, y0 = y - oy, y1 = y + ph - oy;
            if (fl > 0.5f) {
                cut.setShader(new LinearGradient(x0, 0, x0 + fl, 0, ramp, stops, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, x0 + fl, bh, cut);
            }
            if (fr > 0.5f) {
                cut.setShader(new LinearGradient(x1, 0, x1 - fr, 0, ramp, stops, Shader.TileMode.CLAMP));
                c.drawRect(x1 - fr, 0, bw, bh, cut);
            }
            if (ft > 0.5f) {
                cut.setShader(new LinearGradient(0, y0, 0, y0 + ft, ramp, stops, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, bw, y0 + ft, cut);
            }
            if (fb > 0.5f) {
                cut.setShader(new LinearGradient(0, y1, 0, y1 - fb, ramp, stops, Shader.TileMode.CLAMP));
                c.drawRect(0, y1 - fb, bw, bh, cut);
            }
        }
        at[0] = ox;
        at[1] = oy;
        return out;
    }

    /**
     * The picture's edges' colours (a little quieter): for a picture shown across a wide place a 2 x 8 picture (its left
     * edge's colours down, its right edge's), for a narrow place 8 x 2 (its top edge's across, its foot's); drawn out
     * smooth by groundMatrix it is the place's own colour continuing from the picture's edges.
     */
    public static Bitmap edges(Bitmap src, float[] lay) {
        boolean across = lay[PicPlace.FEATHER_T] == 0f && lay[PicPlace.FEATHER_B] == 0f;
        int sw = across ? 12 : 8, sh = across ? 8 : 12;
        Bitmap small = Bitmap.createScaledBitmap(src, sw, sh, true);
        Bitmap g = Bitmap.createBitmap(across ? 2 : 8, across ? 8 : 2, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < 8; i++) {
            if (across) {
                g.setPixel(0, i, quiet(blend(small.getPixel(0, i), small.getPixel(1, i)), GROUND));
                g.setPixel(1, i, quiet(blend(small.getPixel(sw - 1, i), small.getPixel(sw - 2, i)), GROUND));
            } else {
                g.setPixel(i, 0, quiet(blend(small.getPixel(i, 0), small.getPixel(i, 1)), GROUND));
                g.setPixel(i, 1, quiet(blend(small.getPixel(i, sh - 1), small.getPixel(i, sh - 2)), GROUND));
            }
        }
        if (small != src) small.recycle();
        return g;
    }

    /** Where edges() goes in the place (into m): its two columns (rows) on the picture's two edges, drawn out beyond. */
    public static void groundMatrix(Bitmap edges, float[] lay, Matrix m) {
        float x = lay[PicPlace.X], y = lay[PicPlace.Y], pw = lay[PicPlace.PW], ph = lay[PicPlace.PH];
        m.reset();
        if (edges.getWidth() == 2) {
            m.setScale(pw, ph / edges.getHeight());
            m.postTranslate(x - pw * 0.5f, y);
        } else {
            m.setScale(pw / edges.getWidth(), ph);
            m.postTranslate(x, y - ph * 0.5f);
        }
    }

    /** The ground behind a shown picture: its edges' colours drawn out over the whole place. */
    private static void ground(Canvas c, Bitmap src, float[] lay, int w, int h) {
        Bitmap g = edges(src, lay);
        BitmapShader s = new BitmapShader(g, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        groundMatrix(g, lay, m);
        s.setLocalMatrix(m);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        p.setShader(s);
        c.drawRect(0, 0, w, h, p);
        g.recycle();
    }

    private static Matrix matrix(Bitmap src, float[] lay) {
        Matrix m = new Matrix();
        float kx = lay[PicPlace.PW] / src.getWidth(), ky = lay[PicPlace.PH] / src.getHeight();
        m.setScale(kx, ky);
        m.postTranslate(lay[PicPlace.X], lay[PicPlace.Y]);
        return m;
    }

    /** The average colour of a picture (opaque). */
    public static int average(Bitmap src) {
        Bitmap one = Bitmap.createScaledBitmap(src, 4, 4, true);
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int c = one.getPixel(x, y);
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
            }
        }
        if (one != src) one.recycle();
        return 0xFF000000 | (int) (r / 16) << 16 | (int) (g / 16) << 8 | (int) (b / 16);
    }

    private static int blend(int a, int b) {
        return 0xFF000000 | ((((a >> 16) & 0xFF) * 2 + ((b >> 16) & 0xFF)) / 3) << 16 | ((((a >> 8) & 0xFF) * 2 + ((b >> 8) & 0xFF)) / 3) << 8
                | (((a & 0xFF) * 2 + (b & 0xFF)) / 3);
    }

    /** Pure: a colour made k as bright (opaque). */
    public static int quiet(int c, float k) {
        return 0xFF000000 | Math.round(((c >> 16) & 0xFF) * k) << 16 | Math.round(((c >> 8) & 0xFF) * k) << 8 | Math.round((c & 0xFF) * k);
    }

    // ---- the made-up pictures ----------------------------------------------------------------------------------------

    /**
     * The size a made-up picture is drawn at for a place w x h: 16:9, a little larger than PicPlace will show it there
     * (never enlarged), at most 1280 x 720.
     */
    public static int[] size(int w, int h) {
        float[] lay = new float[PicPlace.SIZE];
        PicPlace.place(1600, 900, null, Math.max(1, w), Math.max(1, h), 0f, 1f, lay);
        int ph = Math.max(90, Math.min(720, Math.round(lay[PicPlace.PH] * 1.05f)));
        return new int[]{Math.round(ph * ASPECT), ph};
    }

    /** A picture for what a programme is (PicSources.KINDS; anything else the plain sport picture), w x h. */
    public static Bitmap genre(String kind, int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        float W = b.getWidth(), H = b.getHeight(), u = H / 100f;
        String k = kind == null ? "" : kind;
        switch (k) {
            case PicSources.FOOTBALL: pitch(c, p, W, H, u, 0xFF1F6B35, 0xFF237A3C, false); break;
            case PicSources.RUGBY: pitch(c, p, W, H, u, 0xFF2A6E33, 0xFF2F7B39, true); break;
            case PicSources.MOTOR: motor(c, p, W, H, u); break;
            case PicSources.TENNIS: tennis(c, p, W, H, u); break;
            case PicSources.CRICKET: cricket(c, p, W, H, u); break;
            case PicSources.GOLF: golf(c, p, W, H, u); break;
            case PicSources.FIGHT: ring(c, p, W, H, u); break;
            case PicSources.DARTS: darts(c, p, W, H, u); break;
            case PicSources.SNOOKER: snooker(c, p, W, H, u); break;
            case PicSources.NEWS: news(c, p, W, H, u); break;
            case PicSources.WEATHER: weather(c, p, W, H, u); break;
            case PicSources.FILM: film(c, p, W, H, u); break;
            case PicSources.KIDS: kids(c, p, W, H, u); break;
            case PicSources.NATURE: nature(c, p, W, H, u); break;
            case PicSources.MUSIC: music(c, p, W, H, u); break;
            case PicSources.FOOD: food(c, p, W, H, u); break;
            case PicSources.STAGE: stage(c, p, W, H, u); break;
            default: sport(c, p, W, H, u); break;
        }
        vignette(c, p, W, H);
        return b;
    }

    /**
     * A game's picture, w x h: the two clubs' colours either side of a slanted seam, their crests (null: a plain shield),
     * toward the right where a place's words aren't.
     */
    public static Bitmap fixture(Bitmap home, Bitmap away, int homeColour, int awayColour, int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        float W = b.getWidth(), H = b.getHeight(), u = H / 100f;
        int hc = deep(homeColour), ac = deep(awayColour);
        p.setShader(new LinearGradient(0, 0, W * 0.5f, H, lift(hc, 1.25f), hc, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        Path right = new Path();
        right.moveTo(W * 0.66f, 0);
        right.lineTo(W, 0);
        right.lineTo(W, H);
        right.lineTo(W * 0.54f, H);
        right.close();
        p.setShader(new LinearGradient(W * 0.5f, 0, W, H, ac, lift(ac, 1.25f), Shader.TileMode.CLAMP));
        c.drawPath(right, p);
        // The seam: a thin light line.
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f * u);
        p.setColor(0x55FFFFFF);
        c.drawLine(W * 0.66f, 0, W * 0.54f, H, p);
        p.setStyle(Paint.Style.FILL);
        // A soft light behind each crest.
        for (int i = 0; i < 2; i++) {
            float cx = W * (i == 0 ? 0.42f : 0.78f);
            p.setShader(new RadialGradient(cx, H * 0.5f, 46 * u, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, H * 0.5f, 46 * u, p);
        }
        p.setShader(null);
        crest(c, p, home, W * 0.42f, H * 0.5f, 52 * u);
        crest(c, p, away, W * 0.78f, H * 0.5f, 52 * u);
        // Between them, a small light diamond.
        Path d = new Path();
        float cx = W * 0.6f, cy = H * 0.5f, r = 4.5f * u;
        d.moveTo(cx, cy - r);
        d.lineTo(cx + r, cy);
        d.lineTo(cx, cy + r);
        d.lineTo(cx - r, cy);
        d.close();
        p.setColor(0xE6FFFFFF);
        c.drawPath(d, p);
        vignette(c, p, W, H);
        return b;
    }

    /** The channel's own ground with no logo: its colour, deep, lit softly from the right. */
    public static Bitmap brand(int colour, int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        int d = deep(colour);
        p.setShader(new LinearGradient(0, 0, w, h, quiet(d, 0.7f), d, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.72f, h * 0.45f, Math.max(w, h) * 0.45f, lift(d, 1.6f) & 0x00FFFFFF | 0x80000000, 0,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return b;
    }

    private static void crest(Canvas c, Paint p, Bitmap crest, float cx, float cy, float size) {
        if (crest != null && crest.getWidth() > 0 && crest.getHeight() > 0) {
            float k = Math.min(size / crest.getWidth(), size / crest.getHeight());
            float cw = crest.getWidth() * k, ch = crest.getHeight() * k;
            Paint q = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            c.drawBitmap(crest, null, new RectF(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f), q);
            return;
        }
        Path s = new Path();
        float w = size * 0.42f, t = cy - size * 0.46f;
        s.moveTo(cx - w, t);
        s.lineTo(cx + w, t);
        s.lineTo(cx + w, cy + size * 0.05f);
        s.cubicTo(cx + w, cy + size * 0.3f, cx + w * 0.4f, cy + size * 0.42f, cx, cy + size * 0.5f);
        s.cubicTo(cx - w * 0.4f, cy + size * 0.42f, cx - w, cy + size * 0.3f, cx - w, cy + size * 0.05f);
        s.close();
        p.setColor(0x33FFFFFF);
        c.drawPath(s, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.035f);
        p.setColor(0x80FFFFFF);
        c.drawPath(s, p);
        p.setStyle(Paint.Style.FILL);
    }

    // ---- the genres --------------------------------------------------------------------------------------------------

    private static void sky(Canvas c, Paint p, float W, float H, int top, int foot) {
        p.setShader(new LinearGradient(0, 0, W * 0.3f, H, top, foot, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
    }

    private static void glow(Canvas c, Paint p, float cx, float cy, float r, int colour) {
        p.setShader(new RadialGradient(cx, cy, r, colour, colour & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);
    }

    private static void stroke(Paint p, float width, int colour) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(colour);
    }

    private static void fill(Paint p, int colour) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(colour);
    }

    /** A pitch from above, mown in stripes: its halfway line, centre circle and the box at the right (rugby: its posts). */
    private static void pitch(Canvas c, Paint p, float W, float H, float u, int a, int b, boolean rugby) {
        fill(p, a);
        c.drawRect(0, 0, W, H, p);
        p.setColor(b);
        float sw = W / 12f;
        for (int i = 0; i < 12; i += 2) c.drawRect(i * sw, 0, (i + 1) * sw, H, p);
        glow(c, p, W * 0.78f, H * 0.1f, W * 0.5f, 0x33FFFFFF);
        stroke(p, 0.9f * u, 0xC8FFFFFF);
        float m = 8 * u, mid = W * 0.6f;
        c.drawRect(-W, m, W - m, H - m, p);
        c.drawLine(mid, m, mid, H - m, p);
        if (rugby) {
            c.drawLine(W * 0.82f, m, W * 0.82f, H - m, p);
            stroke(p, 1.6f * u, 0xE6FFFFFF);
            float px = W - m, gy = H * 0.5f;
            c.drawLine(px, gy - 9 * u, px, gy + 9 * u, p);
            c.drawLine(px - 14 * u, gy - 9 * u, px + 4 * u, gy - 9 * u, p);
            c.drawLine(px - 14 * u, gy + 9 * u, px + 4 * u, gy + 9 * u, p);
        } else {
            c.drawCircle(mid, H * 0.5f, 17 * u, p);
            c.drawRect(W - m - 22 * u, H * 0.5f - 30 * u, W - m, H * 0.5f + 30 * u, p);
            c.drawRect(W - m - 8 * u, H * 0.5f - 13 * u, W - m, H * 0.5f + 13 * u, p);
            fill(p, 0xE6FFFFFF);
            c.drawCircle(mid, H * 0.5f, 1.3f * u, p);
        }
    }

    private static void motor(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF20242C, 0xFF0E1014);
        glow(c, p, W * 0.75f, H * 0.2f, W * 0.45f, 0x44E83A30);
        Path road = new Path();
        road.moveTo(W * 0.35f, H * 1.05f);
        road.cubicTo(W * 0.55f, H * 0.45f, W * 0.85f, H * 0.55f, W * 1.05f, H * 0.12f);
        stroke(p, 26 * u, 0xFF3A3F48);
        c.drawPath(road, p);
        stroke(p, 2.2f * u, 0xFFFFFFFF);
        p.setPathEffect(new android.graphics.DashPathEffect(new float[]{5 * u, 5 * u}, 0));
        c.drawPath(road, p);
        p.setPathEffect(null);
        // A chequered corner.
        fill(p, 0x26FFFFFF);
        float s = 6 * u;
        for (int y = 0; y < 4; y++) for (int x = 0; x < 8; x++) if ((x + y) % 2 == 0) c.drawRect(W - (x + 1) * s, y * s, W - x * s, (y + 1) * s, p);
    }

    private static void tennis(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF2D6A8E, 0xFF1D4E6E);
        fill(p, 0xFF3F7FA8);
        float m = 10 * u, l = W * 0.3f;
        c.drawRect(l, m, W - m, H - m, p);
        stroke(p, 0.9f * u, 0xE6FFFFFF);
        c.drawRect(l, m, W - m, H - m, p);
        c.drawRect(l, m + 11 * u, W - m, H - m - 11 * u, p);
        float net = (l + W - m) / 2f, sv = 26 * u;
        c.drawLine(net - sv, m + 11 * u, net - sv, H - m - 11 * u, p);
        c.drawLine(net + sv, m + 11 * u, net + sv, H - m - 11 * u, p);
        c.drawLine(net - sv, H * 0.5f, net + sv, H * 0.5f, p);
        stroke(p, 1.6f * u, 0xFFEFEFEF);
        c.drawLine(net, m - 3 * u, net, H - m + 3 * u, p);
        fill(p, 0xFFD8F03C);
        c.drawCircle(net + sv * 1.6f, H * 0.32f, 2.4f * u, p);
    }

    private static void cricket(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF2E7A3A, 0xFF1E5A2A);
        fill(p, 0xFF34853F);
        c.drawOval(W * 0.18f, -H * 0.2f, W * 1.1f, H * 1.2f, p);
        stroke(p, 0.8f * u, 0x66FFFFFF);
        c.drawOval(W * 0.38f, H * 0.12f, W * 0.94f, H * 0.88f, p);
        fill(p, 0xFFC9B27A);
        float cx = W * 0.66f;
        c.drawRect(cx - 34 * u, H * 0.5f - 5 * u, cx + 34 * u, H * 0.5f + 5 * u, p);
        stroke(p, 0.8f * u, 0xE6FFFFFF);
        c.drawLine(cx - 28 * u, H * 0.5f - 7 * u, cx - 28 * u, H * 0.5f + 7 * u, p);
        c.drawLine(cx + 28 * u, H * 0.5f - 7 * u, cx + 28 * u, H * 0.5f + 7 * u, p);
    }

    private static void golf(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF7FB8E0, 0xFF3E8A4A);
        fill(p, 0xFF2F7A3A);
        c.drawOval(-W * 0.2f, H * 0.55f, W * 1.2f, H * 1.6f, p);
        float cx = W * 0.66f, cy = H * 0.74f;
        for (int i = 3; i >= 1; i--) {
            fill(p, i % 2 == 0 ? 0xFF3C9248 : 0xFF46A052);
            c.drawOval(cx - 16 * u * i, cy - 4.5f * u * i, cx + 16 * u * i, cy + 4.5f * u * i, p);
        }
        stroke(p, 1.1f * u, 0xFFF2F2F2);
        c.drawLine(cx, cy, cx, cy - 42 * u, p);
        fill(p, 0xFFE53935);
        Path f = new Path();
        f.moveTo(cx, cy - 42 * u);
        f.lineTo(cx + 15 * u, cy - 37 * u);
        f.lineTo(cx, cy - 32 * u);
        f.close();
        c.drawPath(f, p);
        fill(p, 0xFF15301A);
        c.drawOval(cx - 2.5f * u, cy - 1 * u, cx + 2.5f * u, cy + 1 * u, p);
    }

    private static void ring(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF1A1420, 0xFF07060A);
        Path beam = new Path();
        beam.moveTo(W * 0.62f, 0);
        beam.lineTo(W * 0.7f, 0);
        beam.lineTo(W * 0.95f, H);
        beam.lineTo(W * 0.38f, H);
        beam.close();
        p.setShader(new LinearGradient(0, 0, 0, H, 0x50FFFFFF, 0x10FFFFFF, Shader.TileMode.CLAMP));
        c.drawPath(beam, p);
        p.setShader(null);
        fill(p, 0xFF2A2F6E);
        c.drawRect(W * 0.3f, H * 0.78f, W * 1.05f, H, p);
        for (int i = 0; i < 3; i++) {
            stroke(p, 1.4f * u, i == 1 ? 0xFFE8E8E8 : 0xFFC62828);
            float y = H * (0.5f + i * 0.1f);
            c.drawLine(W * 0.3f, y + 6 * u, W * 1.05f, y - 8 * u, p);
        }
        fill(p, 0xFF9E9E9E);
        c.drawRect(W * 0.3f - 1.5f * u, H * 0.45f, W * 0.3f + 1.5f * u, H * 0.8f, p);
    }

    private static void darts(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF1B1F2A, 0xFF0B0D12);
        float cx = W * 0.74f, cy = H * 0.5f, r = 44 * u;
        glow(c, p, cx, cy, r * 1.5f, 0x40FFE0A0);
        fill(p, 0xFF111111);
        c.drawCircle(cx, cy, r * 1.12f, p);
        RectF o = new RectF();
        float[] rings = {1f, 0.92f, 0.62f, 0.55f, 0.14f};
        for (int ri = 0; ri < rings.length - 1; ri++) {
            float rr = r * rings[ri];
            o.set(cx - rr, cy - rr, cx + rr, cy + rr);
            for (int s = 0; s < 20; s++) {
                boolean dbl = ri == 0 || ri == 2;
                fill(p, dbl ? (s % 2 == 0 ? 0xFFC62828 : 0xFF2E7D32) : (s % 2 == 0 ? 0xFF1A1A1A : 0xFFEDE3C8));
                c.drawArc(o, s * 18 - 99, 18, true, p);
            }
        }
        fill(p, 0xFF2E7D32);
        c.drawCircle(cx, cy, r * 0.14f, p);
        fill(p, 0xFFC62828);
        c.drawCircle(cx, cy, r * 0.06f, p);
    }

    private static void snooker(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF1D6B3A, 0xFF0F4A26);
        glow(c, p, W * 0.66f, H * 0.5f, W * 0.45f, 0x30FFFFFF);
        fill(p, 0xFF4E2E1A);
        c.drawRect(0, 0, W, 6 * u, p);
        c.drawRect(0, H - 6 * u, W, H, p);
        float cx = W * 0.7f, cy = H * 0.5f, r = 3.6f * u;
        for (int row = 0; row < 5; row++) {
            for (int k = 0; k <= row; k++) {
                float x = cx + row * r * 1.75f, y = cy + (k - row / 2f) * r * 2.02f;
                fill(p, 0xFFB71C1C);
                c.drawCircle(x, y, r, p);
                fill(p, 0x66FFFFFF);
                c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.3f, p);
            }
        }
        fill(p, 0xFFF5F5F5);
        c.drawCircle(W * 0.46f, H * 0.62f, r, p);
        fill(p, 0xFF212121);
        c.drawCircle(W * 0.92f, H * 0.5f, r, p);
    }

    private static void sport(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF14284A, 0xFF0A1428);
        for (int i = 0; i < 3; i++) {
            float x = W * (0.55f + i * 0.17f);
            Path beam = new Path();
            beam.moveTo(x - 2 * u, 0);
            beam.lineTo(x + 2 * u, 0);
            beam.lineTo(x + 28 * u, H);
            beam.lineTo(x - 28 * u, H);
            beam.close();
            p.setShader(new LinearGradient(0, 0, 0, H, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawPath(beam, p);
            glow(c, p, x, 4 * u, 9 * u, 0xCCFFFFFF);
        }
        fill(p, 0xFFB5462E);
        c.drawOval(W * 0.25f, H * 0.7f, W * 1.4f, H * 1.6f, p);
        for (int i = 1; i < 5; i++) {
            stroke(p, 0.6f * u, 0xB3FFFFFF);
            float in = i * 6 * u;
            c.drawOval(W * 0.25f + in, H * 0.7f + in, W * 1.4f - in, H * 1.6f - in, p);
        }
    }

    private static void news(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF0E2350, 0xFF3A0E1E);
        float cx = W * 0.72f, cy = H * 0.52f, r = 40 * u;
        glow(c, p, cx, cy, r * 1.6f, 0x502E7BFF);
        stroke(p, 0.8f * u, 0x8CFFFFFF);
        c.drawCircle(cx, cy, r, p);
        RectF o = new RectF();
        for (int i = 1; i <= 3; i++) {
            float k = r * (1f - i * 0.28f);
            o.set(cx - k, cy - r, cx + k, cy + r);
            c.drawOval(o, p);
        }
        for (int i = -2; i <= 2; i++) {
            float y = cy + i * r * 0.36f, half = (float) Math.sqrt(Math.max(0, r * r - (y - cy) * (y - cy)));
            c.drawLine(cx - half, y, cx + half, y, p);
        }
        // A bar of light across.
        p.setShader(new LinearGradient(0, H * 0.82f, W, H * 0.82f, 0x00FFFFFF, 0x55FFFFFF, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);
        c.drawRect(0, H * 0.82f, W, H * 0.84f, p);
        p.setShader(null);
    }

    private static void weather(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF3D8BD6, 0xFFA9D3F5);
        glow(c, p, W * 0.78f, H * 0.3f, 34 * u, 0xFFFFE082);
        fill(p, 0xFFFFD54F);
        c.drawCircle(W * 0.78f, H * 0.3f, 13 * u, p);
        float[][] clouds = {{0.62f, 0.62f, 1f}, {0.88f, 0.7f, 0.8f}, {0.4f, 0.4f, 0.6f}};
        for (float[] cl : clouds) {
            float x = W * cl[0], y = H * cl[1], s = cl[2] * 10 * u;
            fill(p, 0xEFFFFFFF);
            c.drawCircle(x, y, s * 1.4f, p);
            c.drawCircle(x - s * 1.5f, y + s * 0.5f, s, p);
            c.drawCircle(x + s * 1.6f, y + s * 0.4f, s * 1.1f, p);
            c.drawRect(x - s * 1.5f, y + s * 0.4f, x + s * 1.6f, y + s * 1.5f, p);
        }
    }

    private static void film(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF2A0A12, 0xFF0A0406);
        Path beam = new Path();
        beam.moveTo(W * 1.02f, H * 0.12f);
        beam.lineTo(W * 0.3f, H * 0.02f);
        beam.lineTo(W * 0.3f, H * 0.98f);
        beam.close();
        p.setShader(new LinearGradient(W, 0, W * 0.3f, 0, 0x66FFF3D6, 0x00FFF3D6, Shader.TileMode.CLAMP));
        c.drawPath(beam, p);
        p.setShader(null);
        // A strip of film, aslant.
        c.save();
        c.rotate(-14f, W * 0.68f, H * 0.6f);
        fill(p, 0xF0101010);
        float l = W * 0.38f, r = W * 1.1f, t = H * 0.5f, b = t + 26 * u;
        c.drawRect(l, t, r, b, p);
        fill(p, 0xFFE8E0D0);
        for (float x = l + 3 * u; x < r; x += 7 * u) {
            c.drawRect(x, t + 2 * u, x + 3.5f * u, t + 5 * u, p);
            c.drawRect(x, b - 5 * u, x + 3.5f * u, b - 2 * u, p);
        }
        for (float x = l + 4 * u; x < r; x += 22 * u) {
            fill(p, 0xFF5A2A30);
            c.drawRect(x, t + 7 * u, x + 19 * u, b - 7 * u, p);
        }
        c.restore();
    }

    private static void kids(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF6A3FC4, 0xFF1FA3B5);
        int[] cs = {0xFFFFD54F, 0xFFFF7043, 0xFF66BB6A, 0xFF4FC3F7, 0xFFF06292, 0xFFFFF176};
        float[][] at = {{0.62f, 0.3f, 12}, {0.8f, 0.62f, 16}, {0.92f, 0.22f, 8}, {0.5f, 0.72f, 7}, {0.72f, 0.86f, 6}, {0.86f, 0.4f, 5},
                {0.42f, 0.25f, 4}, {0.97f, 0.85f, 9}};
        for (int i = 0; i < at.length; i++) {
            fill(p, cs[i % cs.length] & 0x00FFFFFF | 0xE0000000);
            c.drawCircle(W * at[i][0], H * at[i][1], at[i][2] * u, p);
            fill(p, 0x50FFFFFF);
            c.drawCircle(W * at[i][0] - at[i][2] * u * 0.35f, H * at[i][1] - at[i][2] * u * 0.35f, at[i][2] * u * 0.3f, p);
        }
        // A star.
        Path s = new Path();
        float cx = W * 0.66f, cy = H * 0.58f, ro = 11 * u, ri = 4.6f * u;
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 5 * i - Math.PI / 2;
            float rr = i % 2 == 0 ? ro : ri, x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            if (i == 0) s.moveTo(x, y);
            else s.lineTo(x, y);
        }
        s.close();
        fill(p, 0xFFFFFFFF);
        c.drawPath(s, p);
    }

    private static void nature(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF2B3A67, 0xFFF29E6D);
        glow(c, p, W * 0.72f, H * 0.52f, 30 * u, 0xAAFFE0B0);
        fill(p, 0xFFFFE6C0);
        c.drawCircle(W * 0.72f, H * 0.52f, 9 * u, p);
        int[] layers = {0xFF5A4A78, 0xFF3E3358, 0xFF262040};
        for (int i = 0; i < 3; i++) {
            Path m = new Path();
            float base = H * (0.62f + i * 0.13f);
            m.moveTo(0, H);
            m.lineTo(0, base);
            for (int k = 0; k <= 8; k++) {
                float x = W * k / 8f, y = base - (float) (Math.abs(Math.sin(k * 1.3 + i * 2.1)) * 18 * u * (1.2f - i * 0.25f));
                m.lineTo(x, y);
            }
            m.lineTo(W, H);
            m.close();
            fill(p, layers[i]);
            c.drawPath(m, p);
        }
    }

    private static void music(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF1E0B36, 0xFF07020F);
        glow(c, p, W * 0.72f, H * 0.9f, W * 0.4f, 0x50E040FB);
        int n = 14;
        float l = W * 0.42f, r = W * 0.98f, bw = (r - l) / n;
        for (int i = 0; i < n; i++) {
            float level = 0.25f + 0.65f * (float) Math.abs(Math.sin(i * 0.9 + 0.4) * Math.cos(i * 0.37));
            float top = H * (0.88f - level * 0.7f);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, top, 0, H * 0.88f, 0xFF40C4FF, 0xFFE040FB, Shader.TileMode.CLAMP));
            c.drawRoundRect(l + i * bw + bw * 0.18f, top, l + (i + 1) * bw - bw * 0.18f, H * 0.88f, bw * 0.2f, bw * 0.2f, p);
        }
        p.setShader(null);
    }

    private static void food(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF5A3A22, 0xFF2E1D10);
        stroke(p, 0.6f * u, 0x22000000);
        for (int i = 1; i < 8; i++) c.drawLine(0, H * i / 8f, W, H * i / 8f + 3 * u, p);
        float cx = W * 0.7f, cy = H * 0.52f;
        fill(p, 0x55000000);
        c.drawCircle(cx + 2 * u, cy + 3 * u, 32 * u, p);
        fill(p, 0xFFF3EFE8);
        c.drawCircle(cx, cy, 32 * u, p);
        fill(p, 0xFFE0DAD0);
        c.drawCircle(cx, cy, 23 * u, p);
        fill(p, 0xFF7CB342);
        c.drawCircle(cx - 6 * u, cy - 4 * u, 6 * u, p);
        fill(p, 0xFFE57373);
        c.drawCircle(cx + 7 * u, cy + 3 * u, 7 * u, p);
        fill(p, 0xFFFFCA28);
        c.drawCircle(cx - 2 * u, cy + 9 * u, 4 * u, p);
        stroke(p, 2 * u, 0xFFCFD8DC);
        c.drawLine(cx - 42 * u, cy - 26 * u, cx - 42 * u, cy + 28 * u, p);
        c.drawLine(cx + 42 * u, cy - 26 * u, cx + 42 * u, cy + 28 * u, p);
    }

    private static void stage(Canvas c, Paint p, float W, float H, float u) {
        sky(c, p, W, H, 0xFF4A0D16, 0xFF1C0408);
        int folds = 16;
        for (int i = 0; i < folds; i++) {
            float x = W * i / folds;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(x, 0, x + W / folds, 0, new int[]{0x33000000, 0x18FFFFFF, 0x33000000}, null, Shader.TileMode.CLAMP));
            c.drawRect(x, 0, x + W / folds, H, p);
        }
        p.setShader(null);
        fill(p, 0xFF1A0E08);
        c.drawRect(0, H * 0.8f, W, H, p);
        glow(c, p, W * 0.68f, H * 0.82f, 34 * u, 0x99FFF1C4);
        Path beam = new Path();
        beam.moveTo(W * 0.62f, 0);
        beam.lineTo(W * 0.74f, 0);
        beam.lineTo(W * 0.68f + 30 * u, H * 0.82f);
        beam.lineTo(W * 0.68f - 30 * u, H * 0.82f);
        beam.close();
        p.setShader(new LinearGradient(0, 0, 0, H, 0x10FFF1C4, 0x50FFF1C4, Shader.TileMode.CLAMP));
        c.drawPath(beam, p);
        p.setShader(null);
    }

    /** Every made-up picture a little darker at its edges (and its left, where a place's words are). */
    private static void vignette(Canvas c, Paint p, float W, float H) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, W * 0.45f, 0, 0x66000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(new RadialGradient(W * 0.6f, H * 0.5f, Math.max(W, H) * 0.75f, 0x00000000, 0x59000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
    }

    /** A club's colour made deep enough for a ground (its brightest part at most about 55 %), else as it is. */
    private static int deep(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF, max = Math.max(r, Math.max(g, b));
        if (max < 24) return 0xFF22262E;
        float k = max <= 140 ? 1f : 140f / max;
        return Color.rgb(Math.round(r * k), Math.round(g * k), Math.round(b * k));
    }

    private static int lift(int c, float k) {
        return Color.rgb(Math.min(255, Math.round(((c >> 16) & 0xFF) * k)), Math.min(255, Math.round(((c >> 8) & 0xFF) * k)),
                Math.min(255, Math.round((c & 0xFF) * k)));
    }
}
