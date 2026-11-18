package com.gathin.broadcast;

import android.graphics.Bitmap;

/**
 * A channel logo's tile in the logo's own colour, the launcher's and the phone's default for "Behind logos" (Logo's
 * colour): the logo's main colour taken from its pixels (its strongest hue, leaving out what is clear, near white,
 * near black or grey) and the tile filled with a rich, deeper shade of it (HSV value about 0.32 to 0.42, its saturation
 * kept, a soft gradient top to foot), so Channel 4's green sits on deep green and BBC One's red on deep red. A logo with
 * no real colour (a white, black or grey mark) gets a neutral dark tile, never pure black. When the logo's own marks
 * would not read on that tile (most of them at a low contrast to it), the tile is taken lighter or deeper until they do.
 * The logo itself is drawn as it is. Where a channel's number, logo and name sit together in one row, the row is
 * filled with the logo's colour too, a touch lighter and richer than the tile so the tile still shows inside it, and
 * deep enough that white numbers and names read on it (cell). Worked out once per logo off the main thread; the apps
 * keep the answer.
 *
 * tile(int[], int, int) and its parts are pure, for the tests; tile(Bitmap) reads a picture's pixels.
 */
public final class LogoColour {
    private LogoColour() {
    }

    /** A logo with no real colour: its tile a neutral dark slate (not black). */
    public static final int NEUTRAL = 0xFF34373E;
    /** The deep shade's HSV value (the tile's middle; its top and foot a little either side), and its top and foot. */
    static final float VALUE = 0.37f, SPREAD = 0.05f;
    /** A pixel this far from grey, and this bright, counts for the logo's colour (HSV saturation and value). */
    static final float MIN_SAT = 0.28f, MIN_VAL = 0.18f;
    /** A colour this much of the logo's counted pixels makes it a coloured logo (less: a white, black or grey mark). */
    static final float COLOURED = 0.08f;
    /** The logo reads on its tile when fewer than this share of its marks are under FAINT contrast to it. */
    static final float FAINT_SHARE = 0.4f, FAINT = 2.0f;

    /** Pure: {hue 0-360, saturation 0-1, value 0-1}. */
    static float[] hsv(int c) {
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 1e-6f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
            if (h < 0) h += 360f;
        }
        return new float[]{h, max <= 0 ? 0 : d / max, max};
    }

    /** Pure: an opaque colour from hue, saturation and value. */
    static int fromHsv(float h, float s, float v) {
        s = Math.max(0f, Math.min(1f, s));
        v = Math.max(0f, Math.min(1f, v));
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; } else if (h < 120) { r = x; g = c; b = 0; } else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; } else if (h < 300) { r = x; g = 0; b = c; } else { r = c; g = 0; b = x; }
        return 0xFF000000 | Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }

    /** Pure: a mixed with b, t of the way to b. */
    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /**
     * Pure: the logo's main colour (opaque), or 0 when it has none: of its solid pixels (alpha over 160), those with a
     * real colour (saturation and value above MIN_SAT and MIN_VAL: no white, black or grey) put by hue into 24 bins,
     * each weighted by how strong it is; the fullest bin with its two neighbours gives the colour (their average). A
     * logo whose coloured pixels are under COLOURED of its solid ones has none.
     */
    static int dominant(int[] px, int w, int h) {
        if (px == null || w <= 0 || h <= 0 || px.length < w * h) return 0;
        float[] weight = new float[24];
        double[][] rgb = new double[24][3];
        int solid = 0, coloured = 0;
        int step = Math.max(1, (int) Math.round(Math.sqrt(w * (double) h / 20_000.0)));
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int c = px[y * w + x];
                if ((c >>> 24) < 160) continue;
                solid++;
                float[] v = hsv(c);
                if (v[1] < MIN_SAT || v[2] < MIN_VAL) continue;
                coloured++;
                int bin = Math.min(23, (int) (v[0] / 15f));
                float k = v[1] * v[2];
                weight[bin] += k;
                rgb[bin][0] += ((c >> 16) & 0xFF) * k;
                rgb[bin][1] += ((c >> 8) & 0xFF) * k;
                rgb[bin][2] += (c & 0xFF) * k;
            }
        }
        if (solid == 0 || coloured < Math.max(1, solid * COLOURED)) return 0;
        int best = 0;
        float bw = -1;
        for (int i = 0; i < 24; i++) {
            float around = weight[i] + 0.5f * (weight[(i + 23) % 24] + weight[(i + 1) % 24]);
            if (around > bw) { bw = around; best = i; }
        }
        double r = 0, g = 0, b = 0, sum = 0;
        for (int d = -1; d <= 1; d++) {
            int i = (best + d + 24) % 24;
            r += rgb[i][0];
            g += rgb[i][1];
            b += rgb[i][2];
            sum += weight[i];
        }
        if (sum <= 0) return 0;
        return 0xFF000000 | (int) Math.round(r / sum) << 16 | (int) Math.round(g / sum) << 8 | (int) Math.round(b / sum);
    }

    /** Pure: the deep shade of a colour: its hue and saturation, at HSV value v. */
    static int shade(int colour, float v) {
        float[] c = hsv(colour);
        return fromHsv(c[0], Math.min(c[1], 0.92f), v);
    }

    /**
     * Pure: the share of the logo's marks that hardly show on a tile colour (contrast under FAINT): its solid pixels,
     * leaving out a plain box of its own round it (a JPEG's white, grey or black ground, which is not the mark), and
     * whether those faint ones are, on average, darker than the tile ({share, darker ? 1 : 0}).
     */
    static float[] faint(int[] px, int w, int h, int tile) {
        int plate = LogoTile.plate(px, w, h);
        int marks = 0, faint = 0;
        double faintLum = 0;
        float lt = LogoTile.luminance(tile);
        int step = Math.max(1, (int) Math.round(Math.sqrt(w * (double) h / 20_000.0)));
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int c = px[y * w + x];
                if ((c >>> 24) < 128) continue;
                if (plate != 0 && distance(c, plate) <= 48) continue;
                marks++;
                int seen = LogoHalo.over(c, tile);
                if (LogoHalo.contrast(seen, tile) < FAINT) {
                    faint++;
                    faintLum += LogoTile.luminance(seen);
                }
            }
        }
        if (marks == 0 || faint == 0) return new float[]{0f, 0f};
        return new float[]{faint / (float) marks, faintLum / faint < lt ? 1f : 0f};
    }

    private static int distance(int a, int b) {
        return Math.max(Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)),
                Math.max(Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)), Math.abs((a & 0xFF) - (b & 0xFF))));
    }

    /**
     * Pure: a tile colour the logo's marks read on: the one given when they do; else taken lighter (towards white, when
     * the faint marks are darker than it) or deeper (towards black), a tenth at a time, until they do, as far as
     * eight-tenths of the way.
     */
    static int reads(int tile, int[] px, int w, int h) {
        float[] f = faint(px, w, h, tile);
        if (f[0] < FAINT_SHARE) return tile;
        int towards = f[1] > 0 ? 0xFFFFFFFF : 0xFF000000;
        int best = tile;
        float least = f[0];
        for (int i = 1; i <= 8; i++) {
            int t = mix(tile, towards, i / 10f);
            float s = faint(px, w, h, t)[0];
            if (s < least) { least = s; best = t; }
            if (s < FAINT_SHARE) return t;
        }
        return best;
    }

    /**
     * Pure: a logo's colours (ARGB, w by h), {tile top, tile foot, row top, row foot}. The tile: the deep shade of its
     * main colour (or the neutral slate for a logo with none), taken lighter or deeper when its marks would not read, a
     * little lighter at the top and deeper at the foot. The row round it (a channel's number, logo and name together):
     * cell().
     */
    public static int[] tile(int[] px, int w, int h) {
        int main = dominant(px, w, h);
        int mid = main == 0 ? NEUTRAL : shade(main, VALUE);
        if (px != null && w > 0 && h > 0 && px.length >= w * h) mid = reads(mid, px, w, h);
        int[] t = gradient(mid), r = cell(main, mid);
        return new int[]{t[0], t[1], r[0], r[1]};
    }

    /** White writing on a row reads at this contrast; the tile shows inside the row at this. */
    static final float WRITING = 4.5f, APART = 1.18f;
    /** The row's HSV value, a touch above the tile's, and how much richer its saturation. */
    static final float ROW_VALUE = 0.46f, RICHER = 1.12f;

    /**
     * Pure: the row round a logo's tile, {top, foot}: the logo's main colour a touch lighter and richer than the tile
     * (a neutral slate a touch lighter for a logo with none), deep enough that white numbers and names on it read
     * (WRITING), and apart from the tile (APART) so the tile still shows inside it: lighter where white writing allows,
     * else deeper.
     */
    static int[] cell(int main, int tileMid) {
        float h, s, v;
        if (main == 0) {
            float[] n = hsv(mix(NEUTRAL, 0xFFFFFFFF, 0.08f));
            h = n[0];
            s = n[1];
            v = n[2];
        } else {
            float[] c = hsv(main);
            h = c[0];
            s = Math.min(0.95f, c[1] * RICHER);
            v = ROW_VALUE;
        }
        int row = fromHsv(h, s, v);
        // deep enough for white writing
        for (int i = 0; i < 40 && v > 0.06f && LogoHalo.contrast(0xFFFFFFFF, row) < WRITING; i++) {
            v -= 0.02f;
            row = fromHsv(h, s, v);
        }
        if (LogoHalo.contrast(row, tileMid) < APART) {
            // apart from the tile: lighter in its own colour while white writing still reads, else deeper
            int lighter = row;
            boolean found = false;
            for (float lv = v + 0.03f; lv <= 1f; lv += 0.03f) {
                int t = fromHsv(h, s, lv);
                if (LogoHalo.contrast(0xFFFFFFFF, t) < WRITING) break;
                lighter = t;
                if (LogoHalo.contrast(t, tileMid) >= APART) {
                    found = true;
                    break;
                }
            }
            if (found) {
                row = lighter;
            } else {
                for (float dv = v - 0.03f; dv > 0.03f && LogoHalo.contrast(row, tileMid) < APART; dv -= 0.03f) row = fromHsv(h, s, dv);
            }
        }
        return gradient(row);
    }

    /** The colours a logo has while its own are worked out: the neutral tile, and its row. */
    public static int[] waiting() {
        int[] t = gradient(NEUTRAL), r = cell(0, NEUTRAL);
        return new int[]{t[0], t[1], r[0], r[1]};
    }

    /** Pure: a tile's soft gradient round its middle colour: a touch lighter at the top, a touch deeper at the foot. */
    public static int[] gradient(int mid) {
        float[] c = hsv(mid);
        if (c[1] < 0.05f) return new int[]{mix(mid, 0xFFFFFFFF, 0.06f), mix(mid, 0xFF000000, 0.14f)};
        return new int[]{fromHsv(c[0], c[1], Math.min(1f, c[2] + SPREAD)), fromHsv(c[0], c[1], Math.max(0f, c[2] - SPREAD))};
    }

    /** A loaded logo's colours ({tile top, tile foot, row top, row foot}), worked out on a small copy (never the main thread). */
    public static int[] tile(Bitmap logo) {
        if (logo == null || logo.isRecycled() || logo.getWidth() < 1 || logo.getHeight() < 1) return waiting();
        float k = Math.min(1f, 64f / Math.max(logo.getWidth(), logo.getHeight()));
        Bitmap small = logo;
        try {
            if (logo.getConfig() == Bitmap.Config.HARDWARE) small = logo.copy(Bitmap.Config.ARGB_8888, false);
            if (small == null) return waiting();
            if (k < 1f) {
                Bitmap s = Bitmap.createScaledBitmap(small, Math.max(1, Math.round(small.getWidth() * k)),
                        Math.max(1, Math.round(small.getHeight() * k)), true);
                if (small != logo) small.recycle();
                small = s;
            }
            int w = small.getWidth(), h = small.getHeight();
            int[] px = new int[w * h];
            small.getPixels(px, 0, w, 0, 0, w, h);
            return tile(px, w, h);
        } catch (RuntimeException e) {
            return waiting();
        } finally {
            if (small != null && small != logo && !small.isRecycled()) small.recycle();
        }
    }
}
