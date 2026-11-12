package com.gathin.tvhome;

/**
 * Pure: the frosted glass under the logo cover (its Frosted glass background, and Transparent's while the channel's logo
 * is learned): the picture behind the cover, with the channel's logo taken out where see-through has learned it (its
 * fill laid over first), made small and softened a long way, its colours a little richer, as frosted glass shows what
 * is behind it. The cover lays it under its marks, lightened or smoked, with a fine edge (LogoMark).
 */
final class FrostPane {
    private FrostPane() { }

    /** The pane: its pixels (opaque ARGB, w by h) and the place it covers (l, t, r, b in the pictures' space). */
    static final class Pane {
        final int[] argb;
        final int w, h, l, t, r, b;

        Pane(int[] argb, int w, int h, int l, int t, int r, int b) {
            this.argb = argb;
            this.w = w;
            this.h = h;
            this.l = l;
            this.t = t;
            this.r = r;
            this.b = b;
        }
    }

    /**
     * The pane for the cover's box l, t, r, b in a picture px (w by h, ARGB) whose top left is ox, oy in the pictures'
     * space; fill (null: none) is see-through's for this picture, in that space. Room round the box for the softening;
     * null when the box is not in the picture.
     */
    static Pane of(int[] px, int w, int h, LogoShape.Fill fill, int ox, int oy, int l, int t, int r, int b) {
        return of(px, w, h, fill, ox, oy, l, t, r, b, 0.26f);
    }

    /** As above, softened by soft of the cover's height (0.26 frosted; less for clear, tempered glass). */
    static Pane of(int[] px, int w, int h, LogoShape.Fill fill, int ox, int oy, int l, int t, int r, int b, float soft) {
        if (px == null || px.length != w * h || r <= l || b <= t) return null;
        int boxH = b - t, m = Math.max(4, boxH / 2);
        int x0 = Math.max(0, l - m), y0 = Math.max(0, t - m), x1 = Math.min(w, r + m), y1 = Math.min(h, b + m);
        if (x1 - x0 < 4 || y1 - y0 < 4) return null;
        // About 22 cells down the cover's height: soft enough to scale up smoothly, cheap to soften.
        int s = Math.max(2, Math.round(boxH / 22f));
        int sw = (x1 - x0 + s - 1) / s, sh = (y1 - y0 + s - 1) / s, n = sw * sh;
        float[] red = new float[n], green = new float[n], blue = new float[n];
        int[] count = new int[n];
        for (int y = y0; y < y1; y++) {
            int row = ((y - y0) / s) * sw;
            for (int x = x0; x < x1; x++) {
                int p = px[y * w + x];
                if (fill != null) {
                    int fx = x + ox - fill.x, fy = y + oy - fill.y;
                    if (fx >= 0 && fy >= 0 && fx < fill.w && fy < fill.h) p = over(fill.argb[fy * fill.w + fx], p);
                }
                int k = row + (x - x0) / s;
                red[k] += (p >> 16) & 0xFF;
                green[k] += (p >> 8) & 0xFF;
                blue[k] += p & 0xFF;
                count[k]++;
            }
        }
        for (int k = 0; k < n; k++) {
            int c = Math.max(1, count[k]);
            red[k] /= c;
            green[k] /= c;
            blue[k] /= c;
        }
        // Softened a long way: three box passes each way (as good as a Gaussian), a quarter of the cover's height.
        int rad = Math.max(1, Math.round(boxH / (float) s * soft));
        float[] tmp = new float[n];
        for (float[] ch : new float[][]{red, green, blue}) {
            for (int pass = 0; pass < 3; pass++) {
                box(ch, tmp, sw, sh, rad, true);
                box(tmp, ch, sw, sh, rad, false);
            }
        }
        int[] out = new int[n];
        for (int k = 0; k < n; k++) {
            float lum = 0.299f * red[k] + 0.587f * green[k] + 0.114f * blue[k];
            out[k] = 0xFF000000 | (rich(red[k], lum) << 16) | (rich(green[k], lum) << 8) | rich(blue[k], lum);
        }
        return new Pane(out, sw, sh, ox + x0, oy + y0, ox + x0 + sw * s, oy + y0 + sh * s);
    }

    /** A colour a little richer than it was (frosted glass's colours glow), kept in range. */
    private static int rich(float c, float lum) {
        return Math.max(0, Math.min(255, Math.round(lum + (c - lum) * 1.25f)));
    }

    /** Pure: the fill's pixel (ARGB, alpha as laid) over the picture's (opaque). */
    static int over(int top, int under) {
        int a = top >>> 24;
        if (a == 0) return under;
        if (a == 255) return top | 0xFF000000;
        int r = (((top >> 16) & 0xFF) * a + ((under >> 16) & 0xFF) * (255 - a)) / 255;
        int g = (((top >> 8) & 0xFF) * a + ((under >> 8) & 0xFF) * (255 - a)) / 255;
        int b = ((top & 0xFF) * a + (under & 0xFF) * (255 - a)) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** One box pass, across (or down), its ends held at the edge's own value. */
    private static void box(float[] in, float[] out, int w, int h, int r, boolean across) {
        int len = across ? w : h, lines = across ? h : w;
        float scale = 1f / (2 * r + 1);
        for (int line = 0; line < lines; line++) {
            int base = across ? line * w : line, step = across ? 1 : w;
            float sum = 0f;
            for (int i = -r; i <= r; i++) sum += in[base + Math.max(0, Math.min(len - 1, i)) * step];
            for (int i = 0; i < len; i++) {
                out[base + i * step] = sum * scale;
                int add = Math.min(len - 1, i + r + 1), drop = Math.max(0, i - r);
                sum += in[base + add * step] - in[base + drop * step];
            }
        }
    }
}
