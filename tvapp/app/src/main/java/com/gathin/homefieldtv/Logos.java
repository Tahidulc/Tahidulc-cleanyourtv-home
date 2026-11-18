package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.LogoPack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every channel logo the app draws, on any ground, as Settings › Appearance › Logos › Behind logos says:
 *
 * <pre>
 *   COLOUR  Logo's colour (as it comes): the logo on a rounded tile of its own colour, the logo's main saturated
 *           colour (its clear, near-white, near-black and grey pixels left out) made deep (HSV value about 0.37, its
 *           saturation kept) with a soft fall from top to foot, made deeper or lighter until the logo reads on it
 *           (contrast 3:1); a logo with no real colour (white, black or grey marks) on a neutral dark glass tile
 *   NONE    the logo straight on the ground, its colours as they are; one too dark to see there (mostly dark pixels)
 *           tv-logos' white version where there is one, else a soft light halo behind its shape (never a box)
 *   BLACK   Black tile: the old look's black tile, the logo made ready for black (LogoTile)
 * </pre>
 *
 * The work is done once per logo and size as it is read, off the main thread, and kept with the picture in Pics (modes
 * TINT, LOGO, TILE): drawing it is one picture, nothing made a frame. Before it has loaded (or when there is no logo)
 * the empty tile is drawn in its place, glass or black, so nothing jumps.
 *
 * <pre>
 *   Logos.draw(c, channel, box, redraw)          the channel's logo (its own choice, the new one, or the playlist's)
 *   Logos.draw(c, url, box, redraw)              a logo by its address (a reminder's, a recording's)
 *                                                box: the tile's place (screen px; the logo fills FILL_W x FILL_H of
 *                                                it); true when the logo is drawn, false while it loads or for none
 *                                                (write the channel's name over the tile then)
 *   Logos.prefetch(url, w, h)                    read ahead at a box's size (screen px)
 *   Logos.colour(url)                            the tile's colour once known (0 before): for a row's own wash
 *   Logos.behind() / setBehind(c, which)         the setting; NAMES for its choices
 * </pre>
 */
final class Logos {
    private Logos() {}

    static final int COLOUR = 0, NONE = 1, BLACK = 2;
    static final String[] NAMES = {"Logo's colour", "None", "Black tile"};
    static final String KEY = "logo_behind_07";

    /** How much of its tile a logo fills (as LogoTile's), and the tile's corners as a share of its height. */
    static final float FILL_W = 0.74f, FILL_H = 0.62f, RADIUS = 0.22f;
    /** None: the clear margin all round a logo (a share of its box's height), where a dark one's halo goes. */
    static final float PAD = 0.19f;
    /** The tile's colour: its value (HSV), how far the top is lighter and the foot deeper, the contrast to reach. */
    static final float VALUE = 0.37f, TOP = 0.05f, FOOT = 0.06f, CONTRAST = 3f;
    /** A neutral tile: dark glass (white at 14 % over the ground, a 1 px ring at 10 %); light for a dark mark. */
    static final int GLASS = 0x24FFFFFF, GLASS_RING = 0x1AFFFFFF, GLASS_LIGHT = 0xE0D9D9E0;

    private static volatile int behind = COLOUR;

    /** Each logo's tile colour as worked out when it was read (by its address); 1 for a neutral glass tile. */
    private static final Map<String, Integer> COLOURS = Collections.synchronizedMap(new LinkedHashMap<String, Integer>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Integer> e) {
            return size() > 1500;
        }
    });

    static void load(Context c) {
        behind = clamp(c.getApplicationContext().getSharedPreferences("look", Context.MODE_PRIVATE).getInt(KEY, COLOUR));
        Pics.tiles = behind == BLACK;
    }

    static int behind() {
        return behind;
    }

    static void setBehind(Context c, int which) {
        behind = clamp(which);
        Pics.tiles = behind == BLACK;
        c.getApplicationContext().getSharedPreferences("look", Context.MODE_PRIVATE).edit().putInt(KEY, behind).apply();
        // Every screen draws its logos again (TvActivity listens).
        LogoPack.changed();
    }

    private static int clamp(int b) {
        return b == NONE || b == BLACK ? b : COLOUR;
    }

    /** The picture's mode for the setting in use. */
    static int mode() {
        return behind == BLACK ? Pics.TILE : behind == NONE ? Pics.LOGO : Pics.TINT;
    }

    /** The tile's colour of a logo already read (0: not yet; Look07.CARD_FRAME for a neutral one). */
    static int colour(String url) {
        Integer c = url == null ? null : COLOURS.get(url);
        if (c == null) return 0;
        return c == 1 ? Look07.CARD_FRAME : c;
    }

    // ---- drawing (main thread: nothing made) --------------------------------------------------------------------------

    private static final Paint BMP = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), FILL = new Paint(Paint.ANTI_ALIAS_FLAG),
            RING = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();

    static {
        RING.setStyle(Paint.Style.STROKE);
    }

    static boolean draw(Canvas c, Channel ch, RectF box, Runnable redraw) {
        return draw(c, ch == null ? "" : LogoPack.url(ch), box, redraw);
    }

    static boolean draw(Canvas c, String url, RectF box, Runnable redraw) {
        return draw(c, url, box, redraw, 255);
    }

    /** The same at an opacity (0 to 255: a card cross-fading). */
    static boolean draw(Canvas c, String url, RectF box, Runnable redraw, int alpha) {
        int w = Math.round(box.width()), h = Math.round(box.height());
        if (w < 2 || h < 2 || alpha <= 0) return false;
        Bitmap b = url == null || url.isEmpty() ? null : Pics.get(url, w, h, mode(), redraw);
        if (b == null) {
            empty(c, box, alpha);
            return false;
        }
        float x = box.centerX() - b.getWidth() / 2f, y = box.centerY() - b.getHeight() / 2f;
        BMP.setAlpha(alpha);
        c.drawBitmap(b, x, y, BMP);
        return true;
    }

    /** The empty tile where a logo goes (still loading, or none): glass, black or nothing, as the setting is. */
    static void empty(Canvas c, RectF box) {
        empty(c, box, 255);
    }

    static void empty(Canvas c, RectF box, int alpha) {
        if (behind == NONE) return;
        float r = box.height() * RADIUS;
        if (behind == BLACK) {
            FILL.setColor(Palette.alpha(com.gathin.broadcast.LogoTile.GROUND, alpha));
            c.drawRoundRect(box, r, r, FILL);
            return;
        }
        FILL.setColor(Palette.alpha(GLASS, (GLASS >>> 24) * alpha / 255));
        c.drawRoundRect(box, r, r, FILL);
        float sw = Math.max(1f, Ui.f(1f));
        RING.setStrokeWidth(sw);
        RING.setColor(Palette.alpha(GLASS_RING, (GLASS_RING >>> 24) * alpha / 255));
        R.set(box.left + sw / 2f, box.top + sw / 2f, box.right - sw / 2f, box.bottom - sw / 2f);
        c.drawRoundRect(R, r, r, RING);
    }

    /** Read ahead (a row coming into sight), at a box's size (screen px). */
    static void prefetch(String url, float w, float h) {
        if (url != null && !url.isEmpty()) Pics.prefetch(url, Math.round(w), Math.round(h), mode());
    }

    // ---- making the pictures (Pics' readers, off the main thread) ------------------------------------------------------

    /** Pure: whether a logo's pixels are mostly too dark for a dark ground (LogoTile's rule). */
    static boolean dark(int[] px) {
        return com.gathin.broadcast.LogoTile.dark(px);
    }

    /** COLOUR: the logo (made clear, LogoTile.clear) fitted on its own colour's tile, w x h. */
    static Bitmap onColour(String url, Bitmap logo, int w, int h) {
        int lw = logo.getWidth(), lh = logo.getHeight();
        int[] px = new int[lw * lh];
        logo.getPixels(px, 0, lw, 0, 0, lw, lh);
        int main = dominant(px);
        float lum = luminance(px);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float r = h * RADIUS;
        RectF box = new RectF(0, 0, w, h);
        if (main != 0) {
            int[] tile = tile(main, lum);
            p.setShader(new LinearGradient(0, 0, 0, h, tile[0], tile[1], Shader.TileMode.CLAMP));
            c.drawRoundRect(box, r, r, p);
            p.setShader(null);
            COLOURS.put(url, tile[2]);
        } else {
            // No real colour: neutral dark glass, or light where the mark itself is dark.
            boolean darkMark = contrast(lum, luminance(Palette.over(Look07.GROUND, GLASS))) < CONTRAST;
            p.setColor(darkMark ? GLASS_LIGHT : GLASS);
            c.drawRoundRect(box, r, r, p);
            COLOURS.put(url, 1);
        }
        // Its edge: a hairline of light all round (inset 0 0 0 1px white at 10 %).
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        float sw = Math.max(1f, h / 64f);
        ring.setStrokeWidth(sw);
        ring.setColor(GLASS_RING);
        c.drawRoundRect(new RectF(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f), r, r, ring);
        float[] d = com.gathin.broadcast.LogoTile.fit(lw, lh, w, h);
        c.drawBitmap(logo, new Rect(0, 0, lw, lh), new RectF(d[0], d[1], d[2], d[3]), p);
        logo.recycle();
        return out;
    }

    /** NONE: the logo (made clear) fitted in w x h with PAD of h clear all round; dark: a soft light halo behind it. */
    static Bitmap bare(Bitmap logo, int w, int h, boolean dark) {
        int pad = Math.max(1, Math.round(PAD * h));
        int lw = logo.getWidth(), lh = logo.getHeight();
        float room = Math.max(1, w - 2 * pad), roomH = Math.max(1, h - 2 * pad);
        float s = Math.min(2.5f, Math.min(room / lw, roomH / lh));
        int dw = Math.max(1, Math.round(lw * s)), dh = Math.max(1, Math.round(lh * s));
        int ow = dw + 2 * pad, oh = dh + 2 * pad;
        Bitmap out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        RectF dst = new RectF(pad, pad, pad + dw, pad + dh);
        c.drawBitmap(logo, new Rect(0, 0, lw, lh), dst, p);
        logo.recycle();
        if (!dark) return out;
        // The halo: the logo's own shape, blurred, in white, faint, under it.
        int[] px = new int[ow * oh];
        out.getPixels(px, 0, ow, 0, 0, ow, oh);
        int[] glow = halo(px, ow, oh, Math.max(2, Math.round(pad * 0.8f)));
        under(px, glow);
        out.setPixels(px, 0, ow, 0, 0, ow, oh);
        return out;
    }

    /** Pure: glow put under the picture px (both unpremultiplied ARGB), in place in px. */
    static void under(int[] px, int[] glow) {
        for (int i = 0; i < px.length; i++) {
            int p = px[i], g = glow[i];
            int pa = p >>> 24, ga = g >>> 24;
            if (ga == 0) continue;
            if (pa == 255) continue;
            float a = pa / 255f, b = ga / 255f * (1f - a), out = a + b;
            int r = Math.round((((p >> 16) & 0xFF) * a + ((g >> 16) & 0xFF) * b) / out);
            int gg = Math.round((((p >> 8) & 0xFF) * a + ((g >> 8) & 0xFF) * b) / out);
            int bl = Math.round(((p & 0xFF) * a + (g & 0xFF) * b) / out);
            px[i] = Math.round(out * 255f) << 24 | r << 16 | gg << 8 | bl;
        }
    }

    /** Pure: a soft white glow from a picture's shape (its alpha, blurred by radius, strengthened and faint). */
    static int[] halo(int[] px, int w, int h, int radius) {
        int[] a = new int[w * h];
        for (int i = 0; i < a.length; i++) a[i] = (px[i] >>> 24) << 24;
        StackBlur.blur(a, w, h, radius);
        for (int i = 0; i < a.length; i++) {
            int al = Math.min(255, Math.round((a[i] >>> 24) * 2.6f));
            a[i] = Math.round(al * 0.7f) << 24 | 0xFFFFFF;
        }
        return a;
    }

    // ---- the colour (pure) ----------------------------------------------------------------------------------------------

    /**
     * Pure: a logo's main saturated colour (ARGB), or 0 when it has none: its solid pixels (alpha over half) that are
     * neither near white, near black nor grey (saturation under 0.25), in 24 hues; the heaviest hue (with its
     * neighbours) by count, given back as those pixels' average. Fewer than 6 % of the solid pixels coloured: none.
     */
    static int dominant(int[] px) {
        int bins = 24;
        float[] weight = new float[bins];
        long[] r = new long[bins], g = new long[bins], b = new long[bins];
        int[] n = new int[bins];
        int solid = 0, coloured = 0;
        float[] hsv = new float[3];
        for (int c : px) {
            if ((c >>> 24) < 128) continue;
            solid++;
            hsv(c, hsv);
            if (hsv[1] < 0.25f || hsv[2] < 0.16f || hsv[2] > 0.97f && hsv[1] < 0.35f) continue;
            coloured++;
            int bin = Math.min(bins - 1, (int) (hsv[0] / 360f * bins));
            weight[bin] += 0.5f + hsv[1];
            r[bin] += (c >> 16) & 0xFF;
            g[bin] += (c >> 8) & 0xFF;
            b[bin] += c & 0xFF;
            n[bin]++;
        }
        if (solid == 0 || coloured < Math.max(4, solid * 0.06f)) return 0;
        int best = 0;
        float bestW = -1f;
        for (int i = 0; i < bins; i++) {
            float wsum = weight[i] + 0.5f * (weight[(i + 1) % bins] + weight[(i + bins - 1) % bins]);
            if (wsum > bestW) {
                bestW = wsum;
                best = i;
            }
        }
        long sr = 0, sg = 0, sb = 0, sn = 0;
        for (int k = -1; k <= 1; k++) {
            int i = (best + k + bins) % bins;
            sr += r[i];
            sg += g[i];
            sb += b[i];
            sn += n[i];
        }
        if (sn == 0) return 0;
        return 0xFF000000 | (int) (sr / sn) << 16 | (int) (sg / sn) << 8 | (int) (sb / sn);
    }

    /**
     * Pure: the tile for a logo's main colour and its own lightness (relative luminance of its pixels): {top, foot,
     * middle}: the colour at VALUE (its saturation kept, at least 0.45 so it stays rich), the top TOP lighter and the
     * foot FOOT deeper; deeper (or, failing that, lighter) until the logo reads on its middle at CONTRAST.
     */
    static int[] tile(int main, float logoLum) {
        float[] hsv = new float[3];
        hsv(main, hsv);
        float h = hsv[0], s = Math.max(0.45f, Math.min(1f, hsv[1]));
        float v = VALUE;
        if (contrast(logoLum, luminance(rgb(h, s, v))) < CONTRAST) {
            // Deeper first where the logo is the lighter; failing that (or a logo darker than its tile), lighter, the
            // colour softening as it goes so it can get light enough (a navy logo on a pale blue).
            float tried = v;
            boolean found = false;
            if (logoLum > luminance(rgb(h, s, v))) {
                while (tried > 0.1f && !(found = contrast(logoLum, luminance(rgb(h, s, tried))) >= CONTRAST)) tried -= 0.03f;
            }
            if (!found) {
                tried = v;
                float soft = s;
                while (tried < 0.97f && !(found = contrast(logoLum, luminance(rgb(h, soft, tried))) >= CONTRAST)) {
                    tried = Math.min(0.97f, tried + 0.04f);
                    soft = Math.max(0.2f, soft - 0.04f);
                }
                s = soft;
            }
            v = Math.max(0.08f, Math.min(0.97f, tried));
        }
        int mid = rgb(h, s, v);
        return new int[]{rgb(h, s, Math.min(1f, v + TOP)), rgb(h, s, Math.max(0.05f, v - FOOT)), mid};
    }

    /** Pure: the average relative luminance of a logo's solid pixels (alpha over half); 1 for none. */
    static float luminance(int[] px) {
        double sum = 0;
        int n = 0;
        for (int c : px) {
            if ((c >>> 24) < 128) continue;
            sum += luminance(c);
            n++;
        }
        return n == 0 ? 1f : (float) (sum / n);
    }

    /** Pure: a colour's relative luminance (sRGB to linear light). */
    static float luminance(int c) {
        return (float) (0.2126 * lin((c >> 16) & 0xFF) + 0.7152 * lin((c >> 8) & 0xFF) + 0.0722 * lin(c & 0xFF));
    }

    private static double lin(int v) {
        double x = v / 255.0;
        return x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
    }

    /** Pure: the contrast between two relative luminances (1 to 21). */
    static float contrast(float a, float b) {
        return (Math.max(a, b) + 0.05f) / (Math.min(a, b) + 0.05f);
    }

    /** Pure: hue (0-360), saturation and value (0-1) of an RGB colour. */
    static void hsv(int c, float[] out) {
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d <= 1e-6f) h = 0f;
        else if (max == r) h = 60f * (((g - b) / d) % 6f);
        else if (max == g) h = 60f * ((b - r) / d + 2f);
        else h = 60f * ((r - g) / d + 4f);
        if (h < 0f) h += 360f;
        out[0] = h;
        out[1] = max <= 0f ? 0f : d / max;
        out[2] = max;
    }

    /** Pure: an opaque colour from hue (0-360), saturation and value (0-1). */
    static int rgb(float h, float s, float v) {
        float c = v * s, x = c * (1f - Math.abs((h / 60f) % 2f - 1f)), m = v - c;
        float r, g, b;
        if (h < 60f) { r = c; g = x; b = 0f; }
        else if (h < 120f) { r = x; g = c; b = 0f; }
        else if (h < 180f) { r = 0f; g = c; b = x; }
        else if (h < 240f) { r = 0f; g = x; b = c; }
        else if (h < 300f) { r = x; g = 0f; b = c; }
        else { r = c; g = 0f; b = x; }
        return 0xFF000000 | Math.round((r + m) * 255f) << 16 | Math.round((g + m) * 255f) << 8 | Math.round((b + m) * 255f);
    }
}
