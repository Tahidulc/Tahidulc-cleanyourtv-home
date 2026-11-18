package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.HashMap;
import java.util.Map;

/**
 * 07's soft shadows (CSS box-shadow: an offset, a blur, a spread) under rounded boxes, without a blur at draw time:
 * each kind is a small picture of a blurred rounded box made once (at a quarter of its size, a stack blur), drawn in
 * nine slices stretched to the box, its corners whole. Looked up with nothing new made a frame once prepared.
 *
 * <pre>
 *   Shadows.prepare(Look07.SH_PANEL, Look07.R_MENU)            when the view is made (so no frame makes it)
 *   Shadows.draw(c, rect, Look07.R_MENU, Look07.SH_PANEL, 1f)  under the box (rect and radius: screen px; the last
 *                                                              argument scales its opacity, for a fade or a lift)
 *   Shadows.glow(c, rect, radius, Look07.GLOW_CHANNEL, 1f)     the same in white: a soft light round the box
 * </pre>
 */
final class Shadows {
    private Shadows() {}

    /** The sprites are made at this share of their size (the blur hides it). */
    private static final float SCALE = 0.25f;

    private static final class Sprite {
        final Bitmap bitmap;
        /** Its corner slice (sprite px), and how far past the box the blur reaches (screen px). */
        final int corner;
        final float reach;

        Sprite(Bitmap bitmap, int corner, float reach) {
            this.bitmap = bitmap;
            this.corner = corner;
            this.reach = reach;
        }
    }

    private static final Map<Long, Sprite> SPRITES = new HashMap<>();
    private static final Paint PAINT = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG),
            GLOW = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    static {
        GLOW.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
    }
    private static final Rect SRC = new Rect();
    private static final RectF DST = new RectF(), BOX = new RectF();
    private static float madeFor = -1f;

    private static long key(float radius, float blur) {
        return ((long) Math.round(radius * 4f) << 32) | (Math.round(blur * 4f) & 0xFFFFFFFFL);
    }

    /** The sprite for a box radius and a CSS blur (screen px), made the first time. */
    private static Sprite sprite(float radius, float blur) {
        if (madeFor != Ui.s) {
            SPRITES.clear();
            madeFor = Ui.s;
        }
        long k = key(radius, blur);
        Sprite s = SPRITES.get(k);
        if (s != null) return s;
        // CSS: a blur of b is a Gaussian of sigma b / 2, reaching about 1.5 b past the box.
        float reach = blur * 1.5f;
        int r = Math.max(1, Math.round(radius * SCALE)), m = Math.max(1, Math.round(reach * SCALE));
        int corner = r + m, size = corner * 2 + 1;
        int[] px = new int[size * size];
        // The box: a rounded square of side 2r + 1 in the middle, solid black (alpha only matters).
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = Math.max(0f, Math.abs(x - corner) - 0.5f - 0f), dy = Math.max(0f, Math.abs(y - corner) - 0.5f);
                // Inside the box when within r of its middle line, its corners rounded by r.
                float ix = Math.max(0f, dx - 0f), iy = Math.max(0f, dy);
                boolean in = ix <= r && iy <= r && (ix <= 0f || iy <= 0f || ix * ix + iy * iy <= (float) r * r);
                px[y * size + x] = in ? 0xFF000000 : 0;
            }
        }
        int sigma = Math.max(1, Math.round(blur / 2f * SCALE));
        StackBlur.blur(px, size, size, Math.max(1, Math.round(sigma * 2.45f)));
        Bitmap b = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
        s = new Sprite(b, corner, reach);
        SPRITES.put(k, s);
        return s;
    }

    /** Made now (a view's constructor), so no frame has to: spec as Look07's SH_ kinds (design px), radius design px. */
    static void prepare(float[] spec, float radius) {
        sprite(Ui.f(radius), Ui.f(spec[1]));
    }

    /**
     * A shadow of kind spec ({dy, blur, spread, alpha}, design px) under the box r (screen px) rounded by radius (screen
     * px), its opacity scaled by k (0 to 1).
     */
    static void draw(Canvas c, RectF r, float radius, float[] spec, float k) {
        draw(c, r, radius, spec, k, PAINT);
    }

    /** A soft white light round the box r (CSS: a white box-shadow), of kind spec, its opacity scaled by k. */
    static void glow(Canvas c, RectF r, float radius, float[] spec, float k) {
        draw(c, r, radius, spec, k, GLOW);
    }

    private static void draw(Canvas c, RectF r, float radius, float[] spec, float k, Paint paint) {
        int alpha = Math.round(spec[3] * Math.max(0f, Math.min(1f, k)));
        if (alpha <= 0) return;
        float spread = Ui.f(spec[2]);
        BOX.set(r.left - spread, r.top - spread + Ui.f(spec[0]), r.right + spread, r.bottom + spread + Ui.f(spec[0]));
        if (BOX.width() <= 0f || BOX.height() <= 0f) return;
        float rad = Math.max(0f, Math.min(radius + spread, Math.min(BOX.width(), BOX.height()) / 2f));
        Sprite s = sprite(rad, Ui.f(spec[1]));
        paint.setAlpha(alpha);
        float reach = s.reach, cs = s.corner / SCALE;
        // The nine slices: corners whole, edges and middle stretched (the sprite's middle line is one pixel).
        float l = BOX.left - reach, t = BOX.top - reach, rr = BOX.right + reach, b = BOX.bottom + reach;
        // A box smaller than two corners: the corners share what room there is.
        float cw = Math.min(cs, (rr - l) / 2f), ch = Math.min(cs, (b - t) / 2f);
        int n = s.bitmap.getWidth(), c0 = s.corner, c1 = s.corner + 1;
        slice(c, s.bitmap, 0, 0, c0, c0, l, t, l + cw, t + ch, paint);
        slice(c, s.bitmap, c0, 0, c1, c0, l + cw, t, rr - cw, t + ch, paint);
        slice(c, s.bitmap, c1, 0, n, c0, rr - cw, t, rr, t + ch, paint);
        slice(c, s.bitmap, 0, c0, c0, c1, l, t + ch, l + cw, b - ch, paint);
        slice(c, s.bitmap, c0, c0, c1, c1, l + cw, t + ch, rr - cw, b - ch, paint);
        slice(c, s.bitmap, c1, c0, n, c1, rr - cw, t + ch, rr, b - ch, paint);
        slice(c, s.bitmap, 0, c1, c0, n, l, b - ch, l + cw, b, paint);
        slice(c, s.bitmap, c0, c1, c1, n, l + cw, b - ch, rr - cw, b, paint);
        slice(c, s.bitmap, c1, c1, n, n, rr - cw, b - ch, rr, b, paint);
    }

    private static void slice(Canvas c, Bitmap b, int sl, int st, int sr, int sb, float l, float t, float r, float bo, Paint paint) {
        if (r - l < 0.5f || bo - t < 0.5f) return;
        SRC.set(sl, st, sr, sb);
        DST.set(l, t, r, bo);
        c.drawBitmap(b, SRC, DST, paint);
    }
}
