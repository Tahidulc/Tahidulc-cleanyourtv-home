package com.gathin.broadcast;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Channel logos on black tiles, every one alike: the logo as the playlist (or the guide) gives it, made ready for black,
 * its own empty margins trimmed, then fitted in the middle of the tile with the same room all round. The TV launcher,
 * the phone and Homefield TV draw their channels' logos this way.
 *
 * Made ready for black: a plain white, grey or black box round the logo (a JPEG's background) is taken away from the
 * edges in; then a logo that is mostly dark (black writing, the black blocks of a boxed name) has its lightness turned
 * over, keeping its colours' hue: black writing comes out white, black blocks with white letters come out white blocks
 * with black letters, navy comes out a light blue, and the bright colours stay as they were. A logo already made for a
 * dark screen is left as it is.
 *
 * clean(), bounds(), fit() and flip() are pure, for the tests; ready() and tile() make the pictures.
 */
public final class LogoTile {
    private LogoTile() {
    }

    /** The tile's own colour, and how much of the tile the logo may fill across and up. */
    public static final int GROUND = 0xFF000000;
    public static final float FILL_W = 0.74f, FILL_H = 0.62f;
    /** The size the sums are worked at (and the most a made-ready logo is across). */
    public static final int WORK = 320;

    /** sRGB to linear light, for the luminance a pixel shows on black. */
    private static final float[] LIN = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            float v = i / 255f;
            LIN[i] = v <= 0.04045f ? v / 12.92f : (float) Math.pow((v + 0.055f) / 1.055f, 2.4f);
        }
    }

    /** Pure: how bright a colour shows (relative luminance, 0 black to 1 white). */
    static float luminance(int c) {
        return 0.2126f * LIN[(c >> 16) & 0xFF] + 0.7152f * LIN[(c >> 8) & 0xFF] + 0.0722f * LIN[c & 0xFF];
    }

    /**
     * Pure: a logo's pixels (ARGB, w by h) made ready for a black ground, as a new array (the same array back when there
     * is nothing to work on).
     */
    public static int[] clean(int[] px, int w, int h) {
        return clean(px, w, h, true);
    }

    /**
     * Pure: a logo's pixels with a plain box round it taken away and, flip true, a mostly dark one turned light; as a new
     * array (the same array back when there is nothing to work on). flip false keeps its colours as they are (Homefield
     * TV's logos with no tile: a dark one gets a soft light halo there instead).
     */
    public static int[] clean(int[] px, int w, int h, boolean flip) {
        int n = w * h;
        if (px == null || w <= 0 || h <= 0 || px.length < n) return px;
        int[] out = java.util.Arrays.copyOf(px, n);
        int plate = plate(out, w, h);
        if (plate != 0) unplate(out, w, h, plate);
        if (flip && darkShare(out) >= DARK_SHARE) {
            for (int i = 0; i < n; i++) if ((out[i] >>> 24) != 0) out[i] = flip(out[i]);
        }
        return out;
    }

    /** Pure: whether a logo is mostly dark: at least half its solid pixels all but vanish on black. */
    public static boolean dark(int[] px) {
        return darkShare(px) >= DARK_SHARE;
    }

    /** A pixel this dark all but vanishes on black (contrast under about 2:1). */
    static final float DARK = 0.055f;
    /** A logo this much dark (of its solid pixels) is turned over. */
    static final float DARK_SHARE = 0.5f;

    /** Pure: the share of a logo's solid pixels (alpha over half) that would all but vanish on black. */
    static float darkShare(int[] px) {
        int solid = 0, dark = 0;
        for (int c : px) {
            if ((c >>> 24) < 128) continue;
            solid++;
            if (luminance(c) < DARK) dark++;
        }
        return solid == 0 ? 0f : dark / (float) solid;
    }

    /**
     * Pure: the colour of a plain box round the logo (nearly all its edge opaque, alike, and white, grey or black), or
     * 0 when there is none. A coloured box is the logo's own (a red square with white letters) and stays.
     */
    static int plate(int[] px, int w, int h) {
        if (w < 3 || h < 3) return 0;
        long r = 0, g = 0, b = 0;
        int count = 0, opaque = 0;
        for (int i : edge(w, h)) {
            int c = px[i];
            count++;
            if ((c >>> 24) > 200) {
                opaque++;
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
            }
        }
        if (opaque < count * 0.85f) return 0;
        int mr = (int) (r / opaque), mg = (int) (g / opaque), mb = (int) (b / opaque);
        if (Math.max(mr, Math.max(mg, mb)) - Math.min(mr, Math.min(mg, mb)) > 40) return 0;
        int mean = 0xFF000000 | mr << 16 | mg << 8 | mb;
        int alike = 0;
        for (int i : edge(w, h)) if ((px[i] >>> 24) > 200 && dist(px[i], mean) <= 36) alike++;
        return alike >= count * 0.85f ? mean : 0;
    }

    /** The edge's pixels, each once. */
    private static int[] edge(int w, int h) {
        int[] e = new int[2 * w + 2 * (h - 2)];
        int k = 0;
        for (int x = 0; x < w; x++) {
            e[k++] = x;
            e[k++] = (h - 1) * w + x;
        }
        for (int y = 1; y < h - 1; y++) {
            e[k++] = y * w;
            e[k++] = y * w + w - 1;
        }
        return e;
    }

    /** The box taken away: every pixel like it that joins the edge made clear (the logo's soft edge partly). */
    private static void unplate(int[] px, int w, int h, int plate) {
        int n = w * h;
        boolean[] seen = new boolean[n];
        int[] queue = new int[n];
        int head = 0, tail = 0;
        for (int i : edge(w, h)) {
            if (seen[i]) continue;
            seen[i] = true;
            queue[tail++] = i;
        }
        while (head < tail) {
            int i = queue[head++];
            int c = px[i];
            int d = dist(c, plate);
            // Only through the box's own colour (or what is already clear): the logo's own pixels stop it.
            if (d > 48 && (c >>> 24) > 40) continue;
            // Nearly the box's colour: clear; a little off it (where the logo's edge blends into it): partly clear.
            int a = d <= 20 ? 0 : Math.min(255, (d - 20) * 255 / 28);
            px[i] = (Math.min(c >>> 24, a) << 24) | (c & 0xFFFFFF);
            int x = i % w, y = i / w;
            if (x > 0 && !seen[i - 1]) {
                seen[i - 1] = true;
                queue[tail++] = i - 1;
            }
            if (x + 1 < w && !seen[i + 1]) {
                seen[i + 1] = true;
                queue[tail++] = i + 1;
            }
            if (y > 0 && !seen[i - w]) {
                seen[i - w] = true;
                queue[tail++] = i - w;
            }
            if (y + 1 < h && !seen[i + w]) {
                seen[i + w] = true;
                queue[tail++] = i + w;
            }
        }
    }

    /** Pure: a colour with its lightness turned over (hue, saturation and alpha kept): black to white, navy to light blue. */
    static int flip(int c) {
        int a = c >>> 24;
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        float l = (mx + mn) / 2f, l2 = 1f - l;
        if (mx - mn < 1e-4f) {
            int v = Math.round(l2 * 255f);
            return a << 24 | v << 16 | v << 8 | v;
        }
        float d = mx - mn;
        float s = l > 0.5f ? d / (2f - mx - mn) : d / (mx + mn);
        float hue;
        if (mx == r) hue = (g - b) / d + (g < b ? 6f : 0f);
        else if (mx == g) hue = (b - r) / d + 2f;
        else hue = (r - g) / d + 4f;
        hue /= 6f;
        float q = l2 < 0.5f ? l2 * (1f + s) : l2 + s - l2 * s, p = 2f * l2 - q;
        int R = Math.round(255f * channel(p, q, hue + 1f / 3f));
        int G = Math.round(255f * channel(p, q, hue));
        int B = Math.round(255f * channel(p, q, hue - 1f / 3f));
        return a << 24 | clamp(R) << 16 | clamp(G) << 8 | clamp(B);
    }

    private static float channel(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 0.5f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Pure: what is drawn of a logo (alpha over 16), {left, top, right, bottom} (right and bottom just past it); null for nothing. */
    public static int[] bounds(int[] px, int w, int h) {
        int l = w, t = h, r = -1, b = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) <= 16) continue;
                if (x < l) l = x;
                if (x > r) r = x;
                if (y < t) t = y;
                if (y > b) b = y;
            }
        }
        return r < 0 ? null : new int[]{l, t, r + 1, b + 1};
    }

    /** Pure: where a logo bw by bh goes on a tile tw by th: in the middle, as large as FILL_W and FILL_H let it be. */
    public static float[] fit(float bw, float bh, float tw, float th) {
        float s = Math.min(tw * FILL_W / bw, th * FILL_H / bh);
        float dw = bw * s, dh = bh * s;
        return new float[]{(tw - dw) / 2f, (th - dh) / 2f, (tw + dw) / 2f, (th + dh) / 2f};
    }

    /**
     * A logo made ready for black, its empty margins trimmed, at most WORK across (or tall); null when there is nothing
     * of it to draw. The logo itself is left as it was.
     */
    public static Bitmap ready(Bitmap logo) {
        return prepared(logo, true);
    }

    /**
     * A logo with a plain box round it taken away and its empty margins trimmed, its colours as they are (for a ground
     * of any colour, with no tile), at most WORK across (or tall); null when there is nothing of it to draw.
     */
    public static Bitmap clear(Bitmap logo) {
        return prepared(logo, false);
    }

    private static Bitmap prepared(Bitmap logo, boolean flip) {
        if (logo == null || logo.isRecycled() || logo.getWidth() < 2 || logo.getHeight() < 2) return null;
        Bitmap small = logo;
        float k = Math.min(1f, WORK / (float) Math.max(logo.getWidth(), logo.getHeight()));
        if (k < 1f) {
            small = Bitmap.createScaledBitmap(logo, Math.max(2, Math.round(logo.getWidth() * k)), Math.max(2, Math.round(logo.getHeight() * k)), true);
        }
        if (small.getConfig() == Bitmap.Config.HARDWARE) {
            Bitmap soft = small.copy(Bitmap.Config.ARGB_8888, false);
            if (small != logo) small.recycle();
            small = soft;
            if (small == null) return null;
        }
        int w = small.getWidth(), h = small.getHeight();
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        if (small != logo) small.recycle();
        int[] clean = clean(px, w, h, flip);
        int[] bb = bounds(clean, w, h);
        if (bb == null) return null;
        int bw = bb[2] - bb[0], bh = bb[3] - bb[1];
        int[] cut = new int[bw * bh];
        for (int y = 0; y < bh; y++) System.arraycopy(clean, (bb[1] + y) * w + bb[0], cut, y * bw, bw);
        return Bitmap.createBitmap(cut, bw, bh, Bitmap.Config.ARGB_8888);
    }

    /** The logo on its black tile, tw by th, corners round by round; no logo (or nothing of it): the bare tile. */
    public static Bitmap tile(Bitmap logo, int tw, int th, float round) {
        Bitmap ready = ready(logo);
        Bitmap out = tileOf(ready, tw, th, round);
        if (ready != null) ready.recycle();
        return out;
    }

    /** A logo already made ready (ready()) on its black tile. */
    public static Bitmap tileOf(Bitmap ready, int tw, int th, float round) {
        return onGround(ready, tw, th, round, GROUND);
    }

    /**
     * A logo already made ready, fitted in the middle of a clear box tw by th, as it sits on its tile: for a view whose
     * own background is the black tile (the phone's boxes, with their touch ripple).
     */
    public static Bitmap placed(Bitmap ready, int tw, int th) {
        return onGround(ready, tw, th, 0f, 0);
    }

    private static Bitmap onGround(Bitmap ready, int tw, int th, float round, int ground) {
        tw = Math.max(2, tw);
        th = Math.max(2, th);
        Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        if (ground != 0) {
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(ground);
            cv.drawRoundRect(new RectF(0, 0, tw, th), round, round, fill);
        }
        if (ready == null || ready.isRecycled()) return out;
        float[] d = fit(ready.getWidth(), ready.getHeight(), tw, th);
        cv.drawBitmap(ready, new Rect(0, 0, ready.getWidth(), ready.getHeight()), new RectF(d[0], d[1], d[2], d[3]),
                new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    private static int dist(int a, int b) {
        return Math.max(Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)),
                Math.max(Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)), Math.abs((a & 0xFF) - (b & 0xFF))));
    }
}
