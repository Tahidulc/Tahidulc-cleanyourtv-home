package com.gathin.homefieldtv;

/**
 * A programme's picture with black bars of its own (a 4:3 picture pillarboxed in a 16:9 frame, a film letterboxed, a
 * thin black border round a broadcaster's still): the bars found, to be cropped away before the picture is laid out in a
 * cell, so they never show as a dark line through it (the family's photo: a dark vertical line partway along a wide
 * cell). A column (or row) is a bar when nearly all of it, edge to edge, is near-black; the bars are counted in from each
 * side, at most MOST of the picture's width (height) a side, and nothing is cropped that would leave less than KEEP of it.
 * Then INSET more pixels go from every side, so a picture's own 1 px edge (or one a scaler blended with black) never
 * shows either. Pure, on the picture's pixels: Pics crops with it as it reads a programme's picture.
 */
final class Pillarbox {
    private Pillarbox() {}

    /** A pixel this dark (its brightest channel at most) is near-black. */
    static final int DARK = 28;
    /** A line is a bar when at least this share of it is near-black (a channel's mark or a little noise allowed). */
    static final float SHARE = 0.96f;
    /** At most this share of the picture's width (height) a side is a bar; at least KEEP of it stays. */
    static final float MOST = 0.3f, KEEP = 0.5f;
    /** Pixels taken from every side after the bars. */
    static final int INSET = 2;

    /** Pure: whether an ARGB pixel is near-black. */
    static boolean dark(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return Math.max(r, Math.max(g, b)) <= DARK;
    }

    /**
     * Pure: what to crop from a picture's pixels (ARGB, w x h, row by row): {left, top, right, bottom} in pixels, the
     * bars and INSET; all 0 for a picture too small to crop.
     */
    static int[] crop(int[] px, int w, int h) {
        int[] out = new int[4];
        if (px == null || w < 16 || h < 16 || px.length < w * h) return out;
        int maxX = Math.round(w * MOST), maxY = Math.round(h * MOST);
        int l = 0, r = 0, t = 0, b = 0;
        while (l < maxX && darkColumn(px, w, h, l)) l++;
        while (r < maxX && darkColumn(px, w, h, w - 1 - r)) r++;
        while (t < maxY && darkRow(px, w, t, l, w - r)) t++;
        while (b < maxY && darkRow(px, w, h - 1 - b, l, w - r)) b++;
        // A picture that is near-black through and through (a night scene) is left as it is.
        if (w - l - r < w * KEEP) l = r = 0;
        if (h - t - b < h * KEEP) t = b = 0;
        int in = Math.min(INSET, Math.min(w, h) / 16);
        out[0] = l + in;
        out[1] = t + in;
        out[2] = r + in;
        out[3] = b + in;
        return out;
    }

    private static boolean darkColumn(int[] px, int w, int h, int x) {
        int dark = 0;
        for (int y = 0; y < h; y++) if (dark(px[y * w + x])) dark++;
        return dark >= h * SHARE;
    }

    private static boolean darkRow(int[] px, int w, int y, int from, int to) {
        int n = Math.max(1, to - from), dark = 0;
        for (int x = from; x < to; x++) if (dark(px[y * w + x])) dark++;
        return dark >= n * SHARE;
    }
}
