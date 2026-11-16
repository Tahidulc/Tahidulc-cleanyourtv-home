package com.gathin.tvhomephone.glass;

import android.graphics.Bitmap;

/**
 * A small picture blurred in place: three passes of a box blur each way, near enough a Gaussian, for the page's soft
 * backdrop and the glass bars' frosting when there is no GPU blur to ask for. For small, opaque pictures only.
 */
public final class Soften {
    private Soften() {}

    /** Blurs b (mutable, ARGB_8888) by about radius pixels. */
    public static void blur(Bitmap b, int radius) {
        if (b == null || radius < 1 || !b.isMutable()) return;
        int w = b.getWidth(), h = b.getHeight();
        if (w < 2 || h < 2) return;
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        blur(px, tmp, w, h, radius);
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    /** Pure: the same on packed ARGB pixels (px blurred in place; tmp the same size, for the passes). */
    static void blur(int[] px, int[] tmp, int w, int h, int radius) {
        int r = Math.max(1, Math.round(radius / 1.6f));
        for (int pass = 0; pass < 3; pass++) {
            across(px, tmp, w, h, r);
            down(tmp, px, w, h, r);
        }
    }

    private static void across(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w, sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[row + Math.max(0, Math.min(w - 1, i))];
                sa += p >>> 24;
                sr += (p >> 16) & 255;
                sg += (p >> 8) & 255;
                sb += p & 255;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = ((sa / div) << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = in[row + Math.min(w - 1, x + r + 1)], sub = in[row + Math.max(0, x - r)];
                sa += (add >>> 24) - (sub >>> 24);
                sr += ((add >> 16) & 255) - ((sub >> 16) & 255);
                sg += ((add >> 8) & 255) - ((sub >> 8) & 255);
                sb += (add & 255) - (sub & 255);
            }
        }
    }

    private static void down(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[Math.max(0, Math.min(h - 1, i)) * w + x];
                sa += p >>> 24;
                sr += (p >> 16) & 255;
                sg += (p >> 8) & 255;
                sb += p & 255;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = ((sa / div) << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = in[Math.min(h - 1, y + r + 1) * w + x], sub = in[Math.max(0, y - r) * w + x];
                sa += (add >>> 24) - (sub >>> 24);
                sr += ((add >> 16) & 255) - ((sub >> 16) & 255);
                sg += ((add >> 8) & 255) - ((sub >> 8) & 255);
                sb += (add & 255) - (sub & 255);
            }
        }
    }
}
