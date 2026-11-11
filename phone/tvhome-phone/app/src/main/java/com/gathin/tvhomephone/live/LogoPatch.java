package com.gathin.tvhomephone.live;

/**
 * What the picture is like under the channel's own logo, made up from what is round it, so a see-through cover
 * (SportsMark, plain) can stand on the picture with the channel's logo gone: a picture of the logo's place and room
 * round it (ScreenShots on the TV; the player's own frame on the phone), taken at the screen's own sharpness. grain
 * carries the picture's light in smoothly from every edge (harmonic) and lays the picture's own texture over it, taken
 * from the calmest stretch beside the logo with its shapes left out, so it reads as the pitch, the crowd or the stand
 * going on under the cover rather than a blurred box; nothing from inside the logo's place is used, so none of it can
 * show. fill, the older smooth blend (as broadcasters' "delogo" filters do), is kept for a place with too little
 * picture round it. Pure.
 */
final class LogoPatch {
    private LogoPatch() {
    }

    /** How far in from its edge the patch fades into the picture (a share of its shorter side), and how see-through its edge is. */
    static final float FEATHER = 0.08f, EDGE_ALPHA = 0.6f;

    /**
     * The inside of a w by h picture (px, row by row, ARGB), all but a margin mx wide and my high round it, filled from
     * that margin: (w - 2 mx) by (h - 2 my) pixels, row by row; solid but for its edge, which fades a little into the
     * picture round it (no hard line where it meets it). Null when there is no inside or no margin.
     */
    static int[] fill(int[] px, int w, int h, int mx, int my) {
        return fill(px, w, h, mx, my, mx, my);
    }

    /**
     * The same with a margin of its own on each side (left, top, right, bottom; none where the place is at the screen's
     * edge): a side with none takes the opposite side's colours, and with neither the fill runs the other way only.
     */
    static int[] fill(int[] px, int w, int h, int ml, int mt, int mr, int mb) {
        int iw = w - ml - mr, ih = h - mt - mb;
        if (px == null || px.length < w * h || iw <= 0 || ih <= 0 || ml < 0 || mt < 0 || mr < 0 || mb < 0) return null;
        boolean across = ml > 0 || mr > 0, down = mt > 0 || mb > 0;
        if (!across && !down) return null;
        float[][] left = new float[ih][], right = new float[ih][], top = new float[iw][], bottom = new float[iw][];
        for (int j = 0; j < ih; j++) {
            int row = (mt + j) * w;
            if (ml > 0) left[j] = mean(px, row + ml - Math.min(4, ml), 1, Math.min(4, ml));
            if (mr > 0) right[j] = mean(px, row + w - mr, 1, Math.min(4, mr));
            if (left[j] == null) left[j] = right[j];
            if (right[j] == null) right[j] = left[j];
        }
        for (int i = 0; i < iw; i++) {
            if (mt > 0) top[i] = mean(px, (mt - Math.min(4, mt)) * w + ml + i, w, Math.min(4, mt));
            if (mb > 0) bottom[i] = mean(px, (h - mb) * w + ml + i, w, Math.min(4, mb));
            if (top[i] == null) top[i] = bottom[i];
            if (bottom[i] == null) bottom[i] = top[i];
        }
        if (across) {
            smooth(left);
            smooth(right);
        }
        if (down) {
            smooth(top);
            smooth(bottom);
        }
        int[] out = new int[iw * ih];
        float e = 0.02f, feather = Math.max(1f, FEATHER * Math.min(iw, ih));
        for (int j = 0; j < ih; j++) {
            float wy = (j + 0.5f) / ih, dy = Math.min(wy, 1 - wy);
            for (int i = 0; i < iw; i++) {
                float wx = (i + 0.5f) / iw, dx = Math.min(wx, 1 - wx);
                // Across from the left and right edges, and down from the top and bottom; the nearer edge counts more.
                float kh = across ? dy + e : 0f, kv = down ? dx + e : 0f, k = kh + kv;
                // Its edge fading a little into the picture round it (only where there was picture to fill from).
                float edge = Math.min(Math.min(ml > 0 ? i + 0.5f : feather, mr > 0 ? iw - i - 0.5f : feather),
                        Math.min(mt > 0 ? j + 0.5f : feather, mb > 0 ? ih - j - 0.5f : feather));
                float a = EDGE_ALPHA + (1 - EDGE_ALPHA) * Math.min(1f, edge / feather);
                int c = Math.round(a * 255) << 24;
                for (int ch = 0; ch < 3; ch++) {
                    float hz = across ? left[j][ch] * (1 - wx) + right[j][ch] * wx : 0f;
                    float vt = down ? top[i][ch] * (1 - wy) + bottom[i][ch] * wy : 0f;
                    int v = Math.round((hz * kh + vt * kv) / k);
                    c |= Math.max(0, Math.min(255, v)) << (16 - 8 * ch);
                }
                out[j * iw + i] = c;
            }
        }
        return out;
    }

    // ---- the picture carried over the logo, light and texture -------------------------------------------------------

    /** How see-through the patch's outermost pixel is (it meets the picture there), and the rest solid. */
    static final float NATURAL_EDGE = 0.8f;

    /**
     * Pure: where inside pixel (i, j) is copied from for a side (0 below, 1 above, 2 left, 3 right), mirrored across that
     * edge: the inside's row or column next to it from the picture's next to it, and so on outwards (back and forth
     * within a side with less room than the inside is long).
     */
    static int from(int side, int i, int j, int w, int iw, int ih, int ml, int mt, int room) {
        switch (side) {
            case 0: return (mt + ih + bounce(ih - 1 - j, room)) * w + ml + i;
            case 1: return (mt - 1 - bounce(j, room)) * w + ml + i;
            case 2: return (mt + j) * w + ml - 1 - bounce(i, room);
            default: return (mt + j) * w + ml + iw + bounce(iw - 1 - i, room);
        }
    }

    /** Pure: d steps out into a strip n wide, back and forth (0, 1, ... n-1, n-1, ... 0, 0, 1 ...). */
    static int bounce(int d, int n) {
        int t = d % (2 * n);
        return t < n ? t : 2 * n - 1 - t;
    }

    /** How busy a picture is: the mean difference between neighbouring pixels. */
    private static double gradient(int[] p, int iw, int ih) {
        double sum = 0;
        int n = 0;
        for (int j = 0; j < ih; j++) {
            for (int i = 0; i < iw; i++) {
                if (i + 1 < iw) {
                    sum += dist(p[j * iw + i], p[j * iw + i + 1]);
                    n++;
                }
                if (j + 1 < ih) {
                    sum += dist(p[j * iw + i], p[(j + 1) * iw + i]);
                    n++;
                }
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    /** How busy the picture just round the inside is (a band two pixels deep), for what a copy may be compared with. */
    private static double ringGradient(int[] px, int w, int h, int ml, int mt, int mr, int mb) {
        double sum = 0;
        int n = 0;
        for (int y = Math.max(1, mt - 3); y < Math.min(h - 1, h - mb + 3); y++) {
            for (int x = Math.max(1, ml - 3); x < Math.min(w - 1, w - mr + 3); x++) {
                boolean inside = x >= ml && x < w - mr && y >= mt && y < h - mb;
                if (inside) continue;
                sum += dist(px[y * w + x], px[y * w + x + 1]) + dist(px[y * w + x], px[(y + 1) * w + x]);
                n += 2;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    /** The difference between two pixels, their channels' mean. */
    private static double dist(int a, int b) {
        return (Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                + Math.abs((a & 0xFF) - (b & 0xFF))) / 3.0;
    }

    /**
     * Pure: the margins to take round a place pw by ph pixels at (x, y) on a screen sw by sh, so a side can be mirrored
     * in: as deep as the place on each side (at least min), up to the screen's edge: {left, top, right, bottom}.
     */
    static float[] room(float x, float y, float pw, float ph, float sw, float sh, float min) {
        return new float[]{Math.min(x, Math.max(min, pw)), Math.min(y, Math.max(min, ph)),
                Math.min(sw - x - pw, Math.max(min, pw)), Math.min(sh - y - ph, Math.max(min, ph))};
    }

    /**
     * The inside made as the picture round it is, in two layers: its colours and light run smoothly in from every edge
     * (fill, at the picture's own sharpness), and over them the picture's own grain and fine texture, taken from the
     * calmest stretch beside it (donor) with its shapes taken out (only what differs from its own softened
     * self, and no stronger than the grain round the inside), so a building, a player or a line beside it is never
     * copied in. Solid but for its outermost pixel. Null when no side has the room.
     */
    static int[] grain(int[] px, int w, int h, int ml, int mt, int mr, int mb) {
        int iw = w - ml - mr, ih = h - mt - mb;
        int[] donor = donor(px, w, h, ml, mt, mr, mb);
        if (donor == null) return null;
        float[][] base = harmonic(px, w, h, ml, mt, mr, mb);
        if (base == null) return null;
        // The copy's texture: what differs from its own softened self (its shapes, larger than DETAIL_R, left out), as
        // strong as the picture's own texture round the inside, and never stronger than a little over it.
        float[][] detail = new float[3][iw * ih];
        double sum = 0;
        for (int ch = 0; ch < 3; ch++) {
            int[] one = new int[iw * ih];
            for (int k = 0; k < one.length; k++) one[k] = (donor[k] >> (16 - 8 * ch)) & 0xFF;
            int[] soft = boxBlur(one, iw, ih, DETAIL_R);
            for (int k = 0; k < one.length; k++) {
                detail[ch][k] = one[k] - soft[k];
                sum += Math.abs(detail[ch][k]);
            }
        }
        double ring = ringTexture(px, w, h, ml, mt, mr, mb, DETAIL_R), own = sum / (3.0 * iw * ih);
        float gain = (float) (own < 0.01 ? 0 : Math.max(0.5, Math.min(1.5, ring / own)));
        float limit = (float) Math.max(4.0, 2.5 * ring);
        int[] out = new int[iw * ih];
        for (int j = 0; j < ih; j++) {
            for (int i = 0; i < iw; i++) {
                int k = j * iw + i;
                boolean rim = (ml > 0 && i == 0) || (mr > 0 && i == iw - 1) || (mt > 0 && j == 0) || (mb > 0 && j == ih - 1);
                int c = Math.round((rim ? NATURAL_EDGE : 1f) * 255) << 24;
                for (int ch = 0; ch < 3; ch++) {
                    float d = Math.max(-limit, Math.min(limit, detail[ch][k] * gain));
                    int v = Math.round(base[ch][k] + d);
                    c |= Math.max(0, Math.min(255, v)) << (16 - 8 * ch);
                }
                out[k] = c;
            }
        }
        return out;
    }

    /**
     * The inside's colours as a smooth membrane held to the picture just outside it on every side (the steady state of
     * heat spreading in from the edges): an edge's fine detail dies away within a few pixels of it rather than running
     * across in streaks, as a straight blend's does. Sides at the screen's edge hold nothing (the membrane runs flat
     * to them). Solved small, then at full size near the edges. By channel, row by row; null when no side holds.
     */
    static float[][] harmonic(int[] px, int w, int h, int ml, int mt, int mr, int mb) {
        int iw = w - ml - mr, ih = h - mt - mb;
        if (px == null || px.length < w * h || iw <= 0 || ih <= 0 || (ml <= 0 && mt <= 0 && mr <= 0 && mb <= 0)) return null;
        float[][] out = new float[3][];
        int f = Math.max(1, (Math.max(iw, ih) + 47) / 48);
        for (int ch = 0; ch < 3; ch++) {
            int shift = 16 - 8 * ch;
            // The edges at full size: the pixel just outside, NaN where there is none.
            float[] left = new float[ih], right = new float[ih], top = new float[iw], bottom = new float[iw];
            for (int j = 0; j < ih; j++) {
                left[j] = ml > 0 ? (px[(mt + j) * w + ml - 1] >> shift) & 0xFF : Float.NaN;
                right[j] = mr > 0 ? (px[(mt + j) * w + ml + iw] >> shift) & 0xFF : Float.NaN;
            }
            for (int i = 0; i < iw; i++) {
                top[i] = mt > 0 ? (px[(mt - 1) * w + ml + i] >> shift) & 0xFF : Float.NaN;
                bottom[i] = mb > 0 ? (px[(mt + ih) * w + ml + i] >> shift) & 0xFF : Float.NaN;
            }
            // Small first (the edges' blocks averaged), then that drawn up as the start at full size.
            int cw = (iw + f - 1) / f, chh = (ih + f - 1) / f;
            float[] cl = shrink(left, f, chh), cr = shrink(right, f, chh), ct = shrink(top, f, cw), cb = shrink(bottom, f, cw);
            float[] small = new float[cw * chh];
            java.util.Arrays.fill(small, mean(cl, cr, ct, cb));
            relax(small, cw, chh, cl, cr, ct, cb, 3 * Math.max(cw, chh) + 20);
            float[] full = new float[iw * ih];
            for (int j = 0; j < ih; j++) {
                float y = Math.max(0, Math.min(chh - 1, (j + 0.5f) / f - 0.5f));
                int y0 = (int) y, y1 = Math.min(chh - 1, y0 + 1);
                float fy = y - y0;
                for (int i = 0; i < iw; i++) {
                    float x = Math.max(0, Math.min(cw - 1, (i + 0.5f) / f - 0.5f));
                    int x0 = (int) x, x1 = Math.min(cw - 1, x0 + 1);
                    float fx = x - x0;
                    full[j * iw + i] = (small[y0 * cw + x0] * (1 - fx) + small[y0 * cw + x1] * fx) * (1 - fy)
                            + (small[y1 * cw + x0] * (1 - fx) + small[y1 * cw + x1] * fx) * fy;
                }
            }
            if (f > 1) relax(full, iw, ih, left, right, top, bottom, 24);
            out[ch] = full;
        }
        return out;
    }

    /** Gauss-Seidel with over-relaxation: each cell towards its neighbours' mean, an edge's held value where it has one. */
    private static void relax(float[] g, int w, int h, float[] left, float[] right, float[] top, float[] bottom, int rounds) {
        float omega = 1.85f;
        for (int r = 0; r < rounds; r++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float s = 0;
                    int n = 0;
                    float v;
                    v = x > 0 ? g[y * w + x - 1] : left[y];
                    if (!Float.isNaN(v)) { s += v; n++; }
                    v = x < w - 1 ? g[y * w + x + 1] : right[y];
                    if (!Float.isNaN(v)) { s += v; n++; }
                    v = y > 0 ? g[(y - 1) * w + x] : top[x];
                    if (!Float.isNaN(v)) { s += v; n++; }
                    v = y < h - 1 ? g[(y + 1) * w + x] : bottom[x];
                    if (!Float.isNaN(v)) { s += v; n++; }
                    if (n == 0) continue;
                    float old = g[y * w + x];
                    g[y * w + x] = old + omega * (s / n - old);
                }
            }
        }
    }

    /** An edge's values in blocks of f averaged, n of them (NaN stays NaN). */
    private static float[] shrink(float[] e, int f, int n) {
        float[] out = new float[n];
        for (int k = 0; k < n; k++) {
            float s = 0;
            int c = 0;
            for (int t = k * f; t < Math.min(e.length, (k + 1) * f); t++) {
                if (!Float.isNaN(e[t])) {
                    s += e[t];
                    c++;
                }
            }
            out[k] = c == 0 ? Float.NaN : s / c;
        }
        return out;
    }

    private static float mean(float[]... edges) {
        float s = 0;
        int n = 0;
        for (float[] e : edges) for (float v : e) if (!Float.isNaN(v)) { s += v; n++; }
        return n == 0 ? 128 : s / n;
    }

    /**
     * The stretch of picture beside the inside its texture is taken from: below, above, left or right, whichever has the
     * room (half the inside's size at least: a narrower one goes back and forth) and is calmest while as grainy as the
     * picture round the inside, mirrored in across their shared edge. Null when no side has the room.
     */
    static int[] donor(int[] px, int w, int h, int ml, int mt, int mr, int mb) {
        int iw = w - ml - mr, ih = h - mt - mb;
        if (px == null || px.length < w * h || iw <= 0 || ih <= 0 || ml < 0 || mt < 0 || mr < 0 || mb < 0) return null;
        int[] room = {mb, mt, ml, mr};
        int[] need = {ih, ih, iw, iw};
        int[] src = new int[iw * ih];
        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        double ringGrad = ringGradient(px, w, h, ml, mt, mr, mb);
        for (int side = 0; side < 4; side++) {
            if (room[side] < Math.max(3, (need[side] + 1) / 2)) continue;
            for (int j = 0; j < ih; j++) {
                for (int i = 0; i < iw; i++) src[j * iw + i] = px[from(side, i, j, w, iw, ih, ml, mt, room[side])];
            }
            // The calmest that is as grainy as the picture round the inside.
            double g = gradient(src, iw, ih), score = Math.abs(g - ringGrad) + 1.5 * Math.max(0, g - 1.1 * ringGrad);
            if (score < bestScore) {
                bestScore = score;
                best = src.clone();
            }
        }
        return best;
    }

    /** Pure: a w by h picture of one channel softened (a box r pixels each way, run twice; running sums): row by row. */
    static int[] boxBlur(int[] v, int w, int h, int r) {
        int[] a = v.clone(), b = new int[v.length];
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < h; y++) {
                int row = y * w, s = 0, n = 0;
                for (int k = 0; k <= Math.min(w - 1, r); k++, n++) s += a[row + k];
                for (int x = 0; x < w; x++) {
                    b[row + x] = s / n;
                    int add = x + r + 1, drop = x - r;
                    if (add < w) {
                        s += a[row + add];
                        n++;
                    }
                    if (drop >= 0) {
                        s -= a[row + drop];
                        n--;
                    }
                }
            }
            for (int x = 0; x < w; x++) {
                int s = 0, n = 0;
                for (int k = 0; k <= Math.min(h - 1, r); k++, n++) s += b[k * w + x];
                for (int y = 0; y < h; y++) {
                    a[y * w + x] = s / n;
                    int add = y + r + 1, drop = y - r;
                    if (add < h) {
                        s += b[add * w + x];
                        n++;
                    }
                    if (drop >= 0) {
                        s -= b[drop * w + x];
                        n--;
                    }
                }
            }
        }
        return a;
    }

    /** How far the texture taken over reaches (the softening its shapes are taken out with), in pixels. */
    static final int DETAIL_R = 6;

    /**
     * How textured the picture round the inside is at that reach: the mean difference of a pixel from its softened
     * self, in a band clear of the inside by more than the softening reaches (so nothing of the logo counts).
     */
    private static double ringTexture(int[] px, int w, int h, int ml, int mt, int mr, int mb, int r) {
        int[] grey = new int[w * h];
        for (int k = 0; k < grey.length; k++) {
            int c = px[k];
            grey[k] = (((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF)) / 3;
        }
        int[] soft = boxBlur(grey, w, h, r);
        int clear = 2 * r + 2, deep = clear + 10;
        double sum = 0;
        int n = 0;
        for (int y = Math.max(0, mt - deep); y < Math.min(h, h - mb + deep); y++) {
            for (int x = Math.max(0, ml - deep); x < Math.min(w, w - mr + deep); x++) {
                if (x >= ml - clear && x < w - mr + clear && y >= mt - clear && y < h - mb + clear) continue;
                sum += Math.abs(grey[y * w + x] - soft[y * w + x]);
                n++;
            }
        }
        // (Grey's texture is a little less than the channels'; near enough for how strong to make the copy's.)
        return n == 0 ? ringDetail(px, w, h, ml, mt, mr, mb) : 1.1 * sum / n;
    }

    /** How grainy the picture is just round the inside: the mean difference of a pixel from its neighbours' mean. */
    private static double ringDetail(int[] px, int w, int h, int ml, int mt, int mr, int mb) {
        double sum = 0;
        int n = 0;
        for (int y = Math.max(1, mt - 4); y < Math.min(h - 1, h - mb + 4); y++) {
            for (int x = Math.max(1, ml - 4); x < Math.min(w - 1, w - mr + 4); x++) {
                if (x >= ml - 1 && x <= w - mr && y >= mt - 1 && y <= h - mb) continue;
                int c = px[y * w + x], l = px[y * w + x - 1], r = px[y * w + x + 1], u = px[(y - 1) * w + x], d = px[(y + 1) * w + x];
                for (int ch = 0; ch < 3; ch++) {
                    int s = 16 - 8 * ch;
                    double m = (((l >> s) & 0xFF) + ((r >> s) & 0xFF) + ((u >> s) & 0xFF) + ((d >> s) & 0xFF)) / 4.0;
                    sum += Math.abs(((c >> s) & 0xFF) - m);
                    n++;
                }
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    /**
     * The patch for a picture of the cover's place and the margins round it (row by row, ARGB): the picture's light
     * carried in smoothly with its own grain over it (grain), else, with too little room round it, the blend (fill). Null when there is no inside.
     */
    static int[] best(int[] px, int w, int h, int ml, int mt, int mr, int mb) {
        int[] in = grain(px, w, h, ml, mt, mr, mb);
        return in != null ? in : fill(px, w, h, ml, mt, mr, mb);
    }

    /** The mean of n pixels from start, step apart: {red, green, blue}. */
    private static float[] mean(int[] px, int start, int step, int n) {
        float r = 0, g = 0, b = 0;
        for (int k = 0; k < n; k++) {
            int c = px[start + k * step];
            r += (c >> 16) & 0xFF;
            g += (c >> 8) & 0xFF;
            b += c & 0xFF;
        }
        return new float[]{r / n, g / n, b / n};
    }

    /**
     * The edge's colours softened, as a blur would (three passes of a quarter, a half and a quarter): a crowd's or an
     * advertising board's detail in the margin would otherwise run across the fill in streaks.
     */
    private static void smooth(float[][] edge) {
        int n = edge.length;
        if (n < 3) return;
        for (int pass = 0; pass < 3; pass++) {
            float[][] was = new float[n][];
            for (int i = 0; i < n; i++) was[i] = edge[i].clone();
            for (int i = 0; i < n; i++) {
                float[] a = was[Math.max(0, i - 1)], b = was[i], c = was[Math.min(n - 1, i + 1)];
                for (int ch = 0; ch < 3; ch++) edge[i][ch] = (a[ch] + 2 * b[ch] + c[ch]) / 4f;
            }
        }
    }

    /**
     * Pure: the margins, in a small picture sw by sh of a place pw by ph pixels on the screen with m pixels of margin round
     * it on each side: {mx, my}, each at least 2.
     */
    static int[] margins(int sw, int sh, float pw, float ph, float m) {
        int mx = Math.max(2, Math.round(m / (pw + 2 * m) * sw)), my = Math.max(2, Math.round(m / (ph + 2 * m) * sh));
        return new int[]{mx, my};
    }
}
