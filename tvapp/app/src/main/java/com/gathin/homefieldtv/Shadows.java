package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * 07's soft shadows (CSS box-shadow: an offset, a blur, a spread) under rounded boxes, without a blur at draw time:
 * each kind is a small picture of a blurred rounded box made once (at a quarter of its size, a stack blur) and kept as a
 * nine-patch, drawn in one call stretched to the box, its corners whole. Looked up with nothing new made a frame once
 * prepared: the box's corner radius is taken to the nearest step of a ladder 15 % apart (a blurred corner a pixel or two
 * rounder looks the same), so a card growing as it lifts uses the same few pictures every frame and never makes one.
 *
 * <pre>
 *   Shadows.prepare(Look07.SH_PANEL, Look07.R_MENU)            when the view is made (so no frame makes it)
 *   Shadows.draw(c, rect, Look07.R_MENU, Look07.SH_PANEL, 1f)  under the box (rect and radius: screen px; the last
 *                                                              argument scales its opacity, for a fade or a lift)
 *   Shadows.under(c, rect, radius, spec, 1f)                   the same under a box that is opaque (drawn over it at
 *                                                              once, whole): the part the box hides is not drawn at all
 *   Shadows.glow(c, rect, radius, Look07.GLOW_CHANNEL, 1f)     the same in white: a soft light round the box
 * </pre>
 */
final class Shadows {
    private Shadows() {}

    /** The sprites are made at this share of their size (the blur hides it). */
    private static final float SCALE = 0.25f;
    /** The corner radii a sprite is made for: steps this far apart (the ladder), from 1 px; 0 for square boxes. */
    static final float LADDER = 1.15f;
    private static final double LN_LADDER = Math.log(LADDER);

    private static final class Sprite {
        final Bitmap bitmap;
        /** Drawn whole, and with its middle (what an opaque box hides) left out. */
        final NinePatch full, hollow;
        /** Its corner slice (sprite px), and how far past the box the blur reaches (screen px). */
        final int corner;
        final float reach;

        Sprite(Bitmap bitmap, int corner, float reach) {
            this.bitmap = bitmap;
            this.corner = corner;
            this.reach = reach;
            full = new NinePatch(bitmap, chunk(corner, corner + 1, false));
            hollow = new NinePatch(bitmap, chunk(corner, corner + 1, true));
        }
    }

    private static final Map<Long, Sprite> SPRITES = new HashMap<>();
    private static final Paint PAINT = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG),
            GLOW = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    static {
        GLOW.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
    }
    private static final RectF DST = new RectF(), BOX = new RectF();
    private static float madeFor = -1f;
    /** How many sprites have been made (tests: none while moving). */
    static int made;

    /** Pure: the ladder's step for a corner radius (screen px): 0 for a square box. */
    static int step(float radius) {
        if (radius < 1f) return -1;
        return (int) Math.floor(Math.log(radius) / LN_LADDER + 0.5);
    }

    /** Pure: the radius a step is made for. */
    static float radiusOf(int step) {
        return step < 0 ? 0f : (float) Math.pow(LADDER, step);
    }

    private static long key(int step, float blur) {
        return ((long) step << 32) | (Math.round(blur * 4f) & 0xFFFFFFFFL);
    }

    /** The sprite for a box radius and a CSS blur (screen px), made the first time. */
    private static Sprite sprite(float radius, float blur) {
        if (madeFor != Ui.s) {
            SPRITES.clear();
            java.util.Arrays.fill(recent, null);
            madeFor = Ui.s;
        }
        int step = step(radius);
        long k = key(step, blur);
        // The sprites drawn lately, looked up without boxing the key (a frame draws many shadows: nothing made).
        for (int i = 0; i < RECENT; i++) if (recentKeys[i] == k && recent[i] != null) return recent[i];
        Sprite s = SPRITES.get(k);
        if (s != null) {
            remember(k, s);
            return s;
        }
        // CSS: a blur of b is a Gaussian of sigma b / 2, reaching about 1.5 b past the box.
        float reach = blur * 1.5f, rq = radiusOf(step);
        int r = Math.max(1, Math.round(rq * SCALE)), m = Math.max(1, Math.round(reach * SCALE));
        int corner = r + m, size = corner * 2 + 1;
        int[] px = new int[size * size];
        // The box: a rounded square of side 2r + 1 in the middle, solid black (alpha only matters).
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float ix = Math.max(0f, Math.abs(x - corner) - 0.5f), iy = Math.max(0f, Math.abs(y - corner) - 0.5f);
                // Inside the box when within r of its middle line, its corners rounded by r.
                boolean in = ix <= r && iy <= r && (ix <= 0f || iy <= 0f || ix * ix + iy * iy <= (float) r * r);
                px[y * size + x] = in ? 0xFF000000 : 0;
            }
        }
        int sigma = Math.max(1, Math.round(blur / 2f * SCALE));
        StackBlur.blur(px, size, size, Math.max(1, Math.round(sigma * 2.45f)));
        Bitmap b = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
        b.prepareToDraw();
        s = new Sprite(b, corner, reach);
        made++;
        SPRITES.put(k, s);
        remember(k, s);
        return s;
    }

    /**
     * Pure: a nine-patch's chunk (Android's Res_png_9patch, serialised) for a sprite stretched along its one middle row
     * and column (x0 to x1, y the same), its middle region left out when hollow.
     */
    static byte[] chunk(int x0, int x1, boolean hollow) {
        ByteBuffer b = ByteBuffer.allocate(32 + 4 * 4 + 9 * 4).order(ByteOrder.nativeOrder());
        b.put((byte) 0);
        b.put((byte) 2);
        b.put((byte) 2);
        b.put((byte) 9);
        // The offsets (filled in by Android as it reads it), the padding (none) and the colours' offset.
        for (int i = 0; i < 7; i++) b.putInt(0);
        b.putInt(x0).putInt(x1);
        b.putInt(x0).putInt(x1);
        // Each region's colour hint: drawn (NO_COLOR, 1), the middle transparent (0: not drawn) when hollow.
        for (int i = 0; i < 9; i++) b.putInt(hollow && i == 4 ? 0x00000000 : 0x00000001);
        return b.array();
    }

    /** The last RECENT sprites by key (main thread), so a frame's lookups make nothing. */
    private static final int RECENT = 24;
    private static final long[] recentKeys = new long[RECENT];
    private static final Sprite[] recent = new Sprite[RECENT];
    private static int recentNext;

    private static void remember(long k, Sprite s) {
        recentKeys[recentNext] = k;
        recent[recentNext] = s;
        recentNext = (recentNext + 1) % RECENT;
    }

    /** Made now (a view's constructor), so no frame has to: spec as Look07's SH_ kinds (design px), radius design px. */
    static void prepare(float[] spec, float radius) {
        // The radii a box of this kind is drawn at as it lifts (grown to 1.12, its spread taken off) are made now too.
        for (int i = 0; i <= 12; i++) sprite(Math.max(0f, Ui.f(radius) * (1f + 0.01f * i) + Ui.f(spec[2])), Ui.f(spec[1]));
    }

    /**
     * A shadow of kind spec ({dy, blur, spread, alpha}, design px) under the box r (screen px) rounded by radius (screen
     * px), its opacity scaled by k (0 to 1).
     */
    static void draw(Canvas c, RectF r, float radius, float[] spec, float k) {
        draw(c, r, radius, spec, k, PAINT, false);
    }

    /**
     * A shadow under a box that is opaque and drawn whole over it now (a card's picture or frame, a hero, a solid panel):
     * the part the box hides is left out (one region less to fill), where the shadow's offset lets it.
     */
    static void under(Canvas c, RectF r, float radius, float[] spec, float k) {
        draw(c, r, radius, spec, k, PAINT, true);
    }

    /** A soft white light round the box r (CSS: a white box-shadow), of kind spec, its opacity scaled by k. */
    static void glow(Canvas c, RectF r, float radius, float[] spec, float k) {
        draw(c, r, radius, spec, k, GLOW, false);
    }

    private static void draw(Canvas c, RectF r, float radius, float[] spec, float k, Paint paint, boolean opaqueBox) {
        int alpha = Math.round(spec[3] * Math.max(0f, Math.min(1f, k)));
        if (alpha <= 0) return;
        float spread = Ui.f(spec[2]);
        BOX.set(r.left - spread, r.top - spread + Ui.f(spec[0]), r.right + spread, r.bottom + spread + Ui.f(spec[0]));
        if (BOX.width() <= 0f || BOX.height() <= 0f) return;
        float rad = Math.max(0f, Math.min(radius + spread, Math.min(BOX.width(), BOX.height()) / 2f));
        Sprite s = sprite(rad, Ui.f(spec[1]));
        paint.setAlpha(alpha);
        float reach = s.reach, cs = s.corner / SCALE;
        float l = BOX.left - reach, t = BOX.top - reach, rr = BOX.right + reach, b = BOX.bottom + reach;
        // The middle region (stretched from the sprite's middle pixel): left out when the opaque box covers it all.
        boolean hollow = opaqueBox && covers(r, radius, l + cs, t + cs, rr - cs, b - cs);
        // Drawn at the sprite's own scale (its corners at a quarter size), stretched to the box: one call. A box smaller
        // than two corners has them shrunk to share the room (as the nine-patch does).
        c.save();
        c.translate(l, t);
        c.scale(1f / SCALE, 1f / SCALE);
        DST.set(0, 0, (rr - l) * SCALE, (b - t) * SCALE);
        c.drawPatch(hollow ? s.hollow : s.full, DST, paint);
        c.restore();
    }

    /** Pure: whether the rounded box r (radius rad) covers the rect (l, t, r2, b) wholly (its corners checked on the curve). */
    static boolean covers(RectF r, float rad, float l, float t, float r2, float b) {
        if (r2 <= l || b <= t) return true;
        return inside(r, rad, l, t) && inside(r, rad, r2, t) && inside(r, rad, l, b) && inside(r, rad, r2, b);
    }

    /** Pure: the point (x, y) inside the box r with its corners rounded by rad. */
    static boolean inside(RectF r, float rad, float x, float y) {
        if (x < r.left || x > r.right || y < r.top || y > r.bottom) return false;
        float cx = x < r.left + rad ? r.left + rad : x > r.right - rad ? r.right - rad : x;
        float cy = y < r.top + rad ? r.top + rad : y > r.bottom - rad ? r.bottom - rad : y;
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= rad * rad + 0.01f;
    }
}
