package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.util.LruCache;

/**
 * The colour of a picture (Cine.vivid), for the page it is shown on: worked out from the picture read very small (24 x 14,
 * a few hundred bytes, off the main thread like any other), once for each picture, and kept. Asked for when the remote
 * moves or a picture arrives, never while a frame is drawn.
 */
final class Tint {
    private Tint() {}

    private static final int W = 24, H = 14;
    private static final LruCache<String, Integer> COLOURS = new LruCache<>(600);

    /** The picture's colour, or 0 while it is read (then ready runs, on the main thread) or when it can't be read. */
    static int of(String url, Runnable ready) {
        if (url == null || url.isEmpty()) return 0;
        Integer had = COLOURS.get(url);
        if (had != null) return had;
        Bitmap b = Pics.get(url, W, H, Pics.CROP, ready);
        if (b == null) return 0;
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        int c = Cine.vivid(px);
        COLOURS.put(url, c);
        return c;
    }

    /** Already known (nothing read). */
    static int known(String url) {
        if (url == null || url.isEmpty()) return 0;
        Integer had = COLOURS.get(url);
        return had == null ? 0 : had;
    }
}
