package com.gathin.tvhomephone.live;

/**
 * What the picture is like under the channel's own logo, made up from what is round it, so a see-through cover
 * (SportsMark, plain) can stand on the picture with the channel's logo gone: a small picture of the logo's place and a
 * margin round it (ScreenShots on the TV; the player's own frame on the phone), its inside filled from the margin as
 * broadcasters' "delogo" filters do. Each inside pixel is the blend of the margin level with it (left and right) and
 * above and below it, the nearer side counting more, so the fill runs smoothly from one edge's colours to the other's;
 * drawn larger over the logo with smoothing, it reads as the pitch, the crowd or the sky behind. Pure.
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
