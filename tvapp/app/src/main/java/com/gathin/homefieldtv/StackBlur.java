package com.gathin.homefieldtv;

/**
 * A stack blur (Mario Klingemann's) of ARGB pixels in place: close to a Gaussian of about radius / 2.45 for its
 * standard deviation, in two passes whatever the radius. 07's blur is faked with it, never live: each page picture is
 * blurred once at an eighth of its size (Pics.BLUR) and each soft shadow once (Shadows). Pure; any thread.
 *
 * <pre>
 *   StackBlur.blur(pixels, w, h, radius)          every channel, alpha too
 *   StackBlur.colour(pixels, saturate, bright)    the mock-ups' saturate() and brightness() on the blurred picture
 * </pre>
 */
final class StackBlur {
    private StackBlur() {}

    /** Pure: the pixels (w x h, ARGB) blurred in place by radius (1 or more; 0 leaves them). */
    static void blur(int[] pix, int w, int h, int radius) {
        if (radius < 1 || w < 1 || h < 1 || pix == null || pix.length < w * h) return;
        int wm = w - 1, hm = h - 1, wh = w * h, div = radius + radius + 1;
        int[] r = new int[wh], g = new int[wh], b = new int[wh], a = new int[wh];
        int[] vmin = new int[Math.max(w, h)];
        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] dv = new int[256 * divsum];
        for (int i = 0; i < dv.length; i++) dv[i] = i / divsum;
        int[][] stack = new int[div][4];
        int r1 = radius + 1;
        int yw = 0, yi = 0;
        for (int y = 0; y < h; y++) {
            int rin = 0, gin = 0, bin = 0, ain = 0, rout = 0, gout = 0, bout = 0, aout = 0, rs = 0, gs = 0, bs = 0, as = 0;
            for (int i = -radius; i <= radius; i++) {
                int p = pix[yi + Math.min(wm, Math.max(i, 0))];
                int[] sir = stack[i + radius];
                sir[0] = (p >> 16) & 0xFF;
                sir[1] = (p >> 8) & 0xFF;
                sir[2] = p & 0xFF;
                sir[3] = p >>> 24;
                int rbs = r1 - Math.abs(i);
                rs += sir[0] * rbs;
                gs += sir[1] * rbs;
                bs += sir[2] * rbs;
                as += sir[3] * rbs;
                if (i > 0) {
                    rin += sir[0];
                    gin += sir[1];
                    bin += sir[2];
                    ain += sir[3];
                } else {
                    rout += sir[0];
                    gout += sir[1];
                    bout += sir[2];
                    aout += sir[3];
                }
            }
            int sp = radius;
            for (int x = 0; x < w; x++) {
                r[yi] = dv[rs];
                g[yi] = dv[gs];
                b[yi] = dv[bs];
                a[yi] = dv[as];
                rs -= rout;
                gs -= gout;
                bs -= bout;
                as -= aout;
                int start = sp - radius + div;
                int[] sir = stack[start % div];
                rout -= sir[0];
                gout -= sir[1];
                bout -= sir[2];
                aout -= sir[3];
                if (y == 0) vmin[x] = Math.min(x + radius + 1, wm);
                int p = pix[yw + vmin[x]];
                sir[0] = (p >> 16) & 0xFF;
                sir[1] = (p >> 8) & 0xFF;
                sir[2] = p & 0xFF;
                sir[3] = p >>> 24;
                rin += sir[0];
                gin += sir[1];
                bin += sir[2];
                ain += sir[3];
                rs += rin;
                gs += gin;
                bs += bin;
                as += ain;
                sp = (sp + 1) % div;
                sir = stack[sp % div];
                rout += sir[0];
                gout += sir[1];
                bout += sir[2];
                aout += sir[3];
                rin -= sir[0];
                gin -= sir[1];
                bin -= sir[2];
                ain -= sir[3];
                yi++;
            }
            yw += w;
        }
        for (int x = 0; x < w; x++) {
            int rin = 0, gin = 0, bin = 0, ain = 0, rout = 0, gout = 0, bout = 0, aout = 0, rs = 0, gs = 0, bs = 0, as = 0;
            int yp = -radius * w;
            for (int i = -radius; i <= radius; i++) {
                int yi2 = Math.max(0, yp) + x;
                int[] sir = stack[i + radius];
                sir[0] = r[yi2];
                sir[1] = g[yi2];
                sir[2] = b[yi2];
                sir[3] = a[yi2];
                int rbs = r1 - Math.abs(i);
                rs += r[yi2] * rbs;
                gs += g[yi2] * rbs;
                bs += b[yi2] * rbs;
                as += a[yi2] * rbs;
                if (i > 0) {
                    rin += sir[0];
                    gin += sir[1];
                    bin += sir[2];
                    ain += sir[3];
                } else {
                    rout += sir[0];
                    gout += sir[1];
                    bout += sir[2];
                    aout += sir[3];
                }
                if (i < hm) yp += w;
            }
            int yi2 = x, sp = radius;
            for (int y = 0; y < h; y++) {
                pix[yi2] = dv[as] << 24 | dv[rs] << 16 | dv[gs] << 8 | dv[bs];
                rs -= rout;
                gs -= gout;
                bs -= bout;
                as -= aout;
                int start = sp - radius + div;
                int[] sir = stack[start % div];
                rout -= sir[0];
                gout -= sir[1];
                bout -= sir[2];
                aout -= sir[3];
                if (x == 0) vmin[y] = Math.min(y + r1, hm) * w;
                int p = x + vmin[y];
                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];
                sir[3] = a[p];
                rin += sir[0];
                gin += sir[1];
                bin += sir[2];
                ain += sir[3];
                rs += rin;
                gs += gin;
                bs += bin;
                as += ain;
                sp = (sp + 1) % div;
                sir = stack[sp];
                rout += sir[0];
                gout += sir[1];
                bout += sir[2];
                aout += sir[3];
                rin -= sir[0];
                gin -= sir[1];
                bin -= sir[2];
                ain -= sir[3];
                yi2 += w;
            }
        }
    }

    /**
     * Pure: the CSS filters saturate(s) and brightness(k) on opaque pixels, in place (the luminance-preserving
     * saturation matrix the browser uses).
     */
    static void colour(int[] pix, float saturate, float bright) {
        float s = saturate;
        float rr = (0.213f + 0.787f * s) * bright, rg = (0.715f - 0.715f * s) * bright, rb = (0.072f - 0.072f * s) * bright;
        float gr = (0.213f - 0.213f * s) * bright, gg = (0.715f + 0.285f * s) * bright, gb = (0.072f - 0.072f * s) * bright;
        float br = (0.213f - 0.213f * s) * bright, bg = (0.715f - 0.715f * s) * bright, bb = (0.072f + 0.928f * s) * bright;
        for (int i = 0; i < pix.length; i++) {
            int p = pix[i];
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            int nr = clamp(Math.round(r * rr + g * rg + b * rb));
            int ng = clamp(Math.round(r * gr + g * gg + b * gb));
            int nb = clamp(Math.round(r * br + g * bg + b * bb));
            pix[i] = (p & 0xFF000000) | nr << 16 | ng << 8 | nb;
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }
}
