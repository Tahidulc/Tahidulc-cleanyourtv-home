package com.gathin.broadcast;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * A channel's logo drawn as it is, with nothing behind it (no tile, no box): the way the launcher and the phone draw
 * their channels' logos when "Behind logos" is None, their default (Black tile is LogoTile). Only a logo too faint to
 * see on the ground it sits on (most of it dark on a dark ground, or light on a light one, at a low contrast) gets a
 * soft halo of the other lightness behind its own shape, so it reads; never a box. A logo with a plain box of its own
 * round it (a JPEG's white or black background) carries its own ground and is left as it is.
 *
 * needs(), colour(), room() and glow() are pure, for the tests; apply() makes the picture.
 */
public final class LogoHalo {
    private LogoHalo() {
    }

    /** Under this contrast against the ground (WCAG's ratio, 1 to 21) a pixel all but vanishes. */
    static final float FAINT = 2.0f;
    /** A logo with at least this share of its solid pixels faint gets a halo ("mostly"). */
    static final float SHARE = 0.5f;
    /** The halo's strength where it is fullest, next to the logo's shape (alpha, 0 to 255). */
    static final int STRENGTH = 225;
    /** The halo is worked out at most this big across (it is soft, so a smaller copy drawn larger is the same). */
    static final int WORK = 200;

    /** Pure: how bright a colour shows (relative luminance, 0 black to 1 white). */
    static float luminance(int c) {
        return LogoTile.luminance(c);
    }

    /** Pure: the contrast between two colours (their own alpha left out), 1 (the same) to 21 (black on white). */
    static float contrast(int a, int b) {
        float la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05f) / (Math.min(la, lb) + 0.05f);
    }

    /** Pure: a pixel as it shows over the ground, its alpha mixed in (opaque). */
    static int over(int c, int ground) {
        int a = c >>> 24, k = 255 - a;
        int r = (((c >> 16) & 0xFF) * a + ((ground >> 16) & 0xFF) * k) / 255;
        int g = (((c >> 8) & 0xFF) * a + ((ground >> 8) & 0xFF) * k) / 255;
        int b = ((c & 0xFF) * a + (ground & 0xFF) * k) / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /**
     * Pure: whether a logo (ARGB, w by h) needs a halo on this ground: it has no plain box of its own round it, and at
     * least SHARE of its solid pixels (alpha over half) show at a contrast under FAINT against the ground.
     */
    public static boolean needs(int[] px, int w, int h, int ground) {
        if (px == null || w <= 0 || h <= 0 || px.length < w * h) return false;
        if (LogoTile.plate(px, w, h) != 0) return false;
        int g = 0xFF000000 | ground;
        int step = Math.max(1, (int) Math.round(Math.sqrt(w * (double) h / 40_000.0)));
        int solid = 0, faint = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int c = px[y * w + x];
                if ((c >>> 24) < 128) continue;
                solid++;
                if (contrast(over(c, g), g) < FAINT) faint++;
            }
        }
        return solid > 0 && faint >= solid * SHARE;
    }

    /** Pure: the halo's colour on a ground: a soft white on a dark ground, a soft black on a light one. */
    public static int colour(int ground) {
        return luminance(0xFF000000 | ground) < 0.4f ? 0xFFFFFFFF : 0xFF000000;
    }

    /** Pure: the room a halo takes round a logo w by h, in its pixels: an eighth of its shorter side, at least 2. */
    public static int room(int w, int h) {
        return Math.max(2, Math.round(Math.min(w, h) * 0.125f));
    }

    /**
     * Pure: the halo's alpha (0 to 255), (w + 2r) by (h + 2r): the logo's shape (its alpha) spread r round it and
     * softened (three box blurs each way), strong next to the shape and fading to nothing at r. The logo sits at (r, r).
     */
    static int[] glow(int[] px, int w, int h, int r) {
        int W = w + 2 * r, H = h + 2 * r;
        int[] a = new int[W * H];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) a[(y + r) * W + x + r] = px[y * w + x] >>> 24;
        int k = Math.max(1, r / 3);
        int[] t = new int[a.length];
        for (int pass = 0; pass < 3; pass++) {
            blur(a, t, W, H, k, true);
            blur(t, a, W, H, k, false);
        }
        for (int i = 0; i < a.length; i++) a[i] = Math.min(STRENGTH, a[i] * 3 * STRENGTH / 255);
        return a;
    }

    /** One box blur, k each side, across (or down) from src into dst. */
    private static void blur(int[] src, int[] dst, int W, int H, int k, boolean across) {
        int n = across ? W : H, lines = across ? H : W, span = 2 * k + 1;
        for (int l = 0; l < lines; l++) {
            int sum = 0;
            for (int i = -k; i <= k; i++) sum += at(src, W, across, l, i, n);
            for (int i = 0; i < n; i++) {
                dst[across ? l * W + i : i * W + l] = sum / span;
                sum += at(src, W, across, l, i + k + 1, n) - at(src, W, across, l, i - k, n);
            }
        }
    }

    private static int at(int[] a, int W, boolean across, int line, int i, int n) {
        if (i < 0 || i >= n) return 0;
        return across ? a[line * W + i] : a[i * W + line];
    }

    /**
     * The logo with a soft halo behind its shape when it needs one on this ground: a new picture with room() all round
     * (the logo in its middle, as it was); the logo itself when it needs none (or there is nothing to work on).
     */
    public static Bitmap apply(Bitmap logo, int ground) {
        if (logo == null || logo.isRecycled() || logo.getWidth() < 2 || logo.getHeight() < 2) return logo;
        Bitmap soft = logo.getConfig() == Bitmap.Config.HARDWARE ? logo.copy(Bitmap.Config.ARGB_8888, false) : logo;
        if (soft == null) return logo;
        int w = soft.getWidth(), h = soft.getHeight();
        int[] px = new int[w * h];
        soft.getPixels(px, 0, w, 0, 0, w, h);
        if (!needs(px, w, h, ground)) {
            if (soft != logo) soft.recycle();
            return logo;
        }
        // The halo worked out on a small copy (it is soft), then drawn at the logo's size behind it.
        float k = Math.min(1f, WORK / (float) Math.max(w, h));
        int sw = Math.max(2, Math.round(w * k)), sh = Math.max(2, Math.round(h * k));
        int[] small = px;
        if (k < 1f) {
            Bitmap s = Bitmap.createScaledBitmap(soft, sw, sh, true);
            small = new int[sw * sh];
            s.getPixels(small, 0, sw, 0, 0, sw, sh);
            s.recycle();
        }
        int sr = room(sw, sh);
        int[] alpha = glow(small, sw, sh, sr);
        int rgb = colour(ground) & 0xFFFFFF;
        int[] halo = new int[alpha.length];
        for (int i = 0; i < alpha.length; i++) halo[i] = alpha[i] << 24 | rgb;
        int gw = sw + 2 * sr, gh = sh + 2 * sr;
        Bitmap glow = Bitmap.createBitmap(halo, gw, gh, Bitmap.Config.ARGB_8888);
        int r = Math.round(sr / k);
        Bitmap out = Bitmap.createBitmap(w + 2 * r, h + 2 * r, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(glow, null, new Rect(0, 0, out.getWidth(), out.getHeight()), paint);
        c.drawBitmap(soft, r, r, paint);
        glow.recycle();
        if (soft != logo) soft.recycle();
        return out;
    }
}
