package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import com.gathin.broadcast.LogoColour;
import com.gathin.broadcast.LogoHalo;
import com.gathin.broadcast.LogoTile;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.LogoPack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every channel logo the app draws, on any ground, as Settings › Appearance › Logos › Behind logos says:
 *
 * <pre>
 *   COLOUR  Logo's colour (as it comes): the logo on a rounded tile of its own colour: a rich, deeper shade of its main
 *           colour with a soft fall from top to foot, taken lighter or deeper until the logo reads on it; a logo with no
 *           real colour (white, black or grey marks) on a neutral dark slate, never black
 *   NONE    the logo straight on the ground, its colours as they are; one too faint to see there gets tv-logos' white
 *           version where there is one, else a soft halo behind its own shape (never a box)
 *   BLACK   Black tile: the old look's black tile, the logo made ready for black (LogoTile)
 * </pre>
 *
 * The colours and the halo are the family's shared rule (overlays: LogoColour and LogoHalo), the same the phone and the
 * launcher use, so a channel has one colour on every screen of the house. What this app keeps itself: the work done once
 * per logo and size as it is read, off the main thread, kept with the picture in Pics (modes TINT, LOGO, TILE), each
 * logo's colours by its address (for its channel's cell: ChannelLook), and the drawing (07's tile: its fall, a line of
 * light along its top, a hairline round it). Drawing is one picture, nothing made a frame. Before a logo has loaded (or
 * when there is none) the empty tile is drawn in its place, slate or black, so nothing jumps.
 *
 * <pre>
 *   Logos.draw(c, channel, box, redraw)          the channel's logo (its own choice, the new one, or the playlist's)
 *   Logos.draw(c, url, box, redraw)              a logo by its address (a reminder's, a recording's)
 *                                                box: the tile's place (screen px; the logo fills FILL_W x FILL_H of
 *                                                it); true when the logo is drawn, false while it loads or for none
 *                                                (write the channel's name over the tile then)
 *   Logos.prefetch(url, w, h)                    read ahead at a box's size (screen px)
 *   Logos.tint(url)                              its colours once known (null before): tile and channel cell
 *   Logos.colour(url)                            the tile's colour once known (0 before)
 *   Logos.behind() / setBehind(c, which)         the setting; NAMES for its choices
 * </pre>
 */
final class Logos {
    private Logos() {}

    static final int COLOUR = 0, NONE = 1, BLACK = 2;
    static final String[] NAMES = {"Logo's colour", "None", "Black tile"};
    static final String KEY = "logo_behind_07";

    /** How much of its tile (or box) a logo fills (LogoTile's), and the tile's corners as a share of its height. */
    static final float FILL_W = LogoTile.FILL_W, FILL_H = LogoTile.FILL_H, RADIUS = 0.22f;
    /** 07's tile edge (.ct): a 1 px line of light along its top (white at 24 %) and a hairline round it (12 %). */
    static final int TOP_LIGHT = 0x3DFFFFFF, RING = 0x1FFFFFFF;
    /** A neutral tile's edge (.ct-glass), a little stronger: 30 % and 14 %. */
    static final int SLATE_TOP_LIGHT = 0x4DFFFFFF, SLATE_RING = 0x24FFFFFF;
    /**
     * 07's approved deep shade for a channel's cell (the colour-rows stills: HSV value 0.49 at the top to 0.39 at the
     * foot, saturation about 0.8): a cell's top and foot at most these values, a coloured cell at least this saturated.
     */
    static final float ROW_TOP_V = 0.50f, ROW_FOOT_V = 0.39f, ROW_SAT = 0.68f;
    /** A tile's colour this grey (HSV saturation) is the neutral slate (a logo with no real colour). */
    static final float NEUTRAL_SAT = 0.25f;
    /** The ground a bare logo is judged on for its halo (None): 07's ground. */
    static final int HALO_GROUND = Look07.GROUND;

    private static volatile int behind = COLOUR;

    /**
     * A logo's colours (LogoColour's: the tile's top and foot, and its channel cell's, the number, logo and name behind
     * it, a touch lighter and richer than the tile and deep enough for white words), with their middles and the cell's
     * fall as a shader in a unit box (stretched to each cell: ChannelLook). Neutral: no real colour (the slate).
     *
     * <p>row: the channel cell as it is drawn now, the logo alone on it (no tile inside, so nothing for it to stand
     * apart from): the shared row colour held to 07's approved deep shade, opaque (rowDeep: HSV value at most ROW_TOP_V
     * at its top and ROW_FOOT_V at its foot, saturation at least ROW_SAT), so a pale logo stands out on a rich, deep
     * cell of its own colour and the cell never comes out light or pastel.
     */
    static final class Tint {
        final int tileTop, tileFoot, tile, cellTop, cellFoot, cell, rowTop, rowFoot, row;
        final boolean neutral;
        final Shader cellFall, rowFall;

        /** c: LogoColour's {tile top, tile foot, row top, row foot}. */
        Tint(int[] c) {
            tileTop = c[0];
            tileFoot = c[1];
            tile = Theme.mix(c[0], c[1], 0.5f) | 0xFF000000;
            cellTop = c[2];
            cellFoot = c[3];
            cell = Theme.mix(c[2], c[3], 0.5f) | 0xFF000000;
            neutral = saturation(tile) < NEUTRAL_SAT;
            cellFall = new LinearGradient(0, 0, 0, 1, cellTop, cellFoot, Shader.TileMode.CLAMP);
            int[] deep = rowDeep(c[2], c[3], neutral);
            rowTop = deep[0];
            rowFoot = deep[1];
            row = Theme.mix(rowTop, rowFoot, 0.5f) | 0xFF000000;
            rowFall = new LinearGradient(0, 0, 0, 1, rowTop, rowFoot, Shader.TileMode.CLAMP);
        }

        /** While a logo is read (and for a channel with none): the neutral slate's (LogoColour.waiting). */
        static final Tint WAITING = new Tint(LogoColour.waiting());
    }

    /** Each logo's colours by its address, as worked out when it was read (the most recent 1500). */
    private static final Map<String, Tint> COLOURS = Collections.synchronizedMap(new LinkedHashMap<String, Tint>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Tint> e) {
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

    /** The tile's colour (its middle) of a logo already read, or 0 before. */
    static int colour(String url) {
        Tint t = tint(url);
        return t == null ? 0 : t.tile;
    }

    /** A logo's colours once it has been read on its tile (null before). */
    static Tint tint(String url) {
        return url == null || url.isEmpty() ? null : COLOURS.get(url);
    }

    // ---- drawing (main thread: nothing made) --------------------------------------------------------------------------

    private static final Paint BMP = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), FILL = new Paint(Paint.ANTI_ALIAS_FLAG),
            EDGE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();

    static {
        EDGE.setStyle(Paint.Style.STROKE);
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

    /** The empty tile where a logo goes (still loading, or none): the neutral slate, black or nothing, as the setting is. */
    static void empty(Canvas c, RectF box) {
        empty(c, box, 255);
    }

    static void empty(Canvas c, RectF box, int alpha) {
        if (behind == NONE) return;
        float r = box.height() * RADIUS;
        if (behind == BLACK) {
            FILL.setColor(Palette.alpha(LogoTile.GROUND, alpha));
            c.drawRoundRect(box, r, r, FILL);
            return;
        }
        FILL.setColor(Palette.alpha(Tint.WAITING.tile, alpha));
        c.drawRoundRect(box, r, r, FILL);
        float sw = Math.max(1f, Ui.f(1f));
        EDGE.setStrokeWidth(sw);
        EDGE.setColor(Palette.alpha(SLATE_RING, (SLATE_RING >>> 24) * alpha / 255));
        R.set(box.left + sw / 2f, box.top + sw / 2f, box.right - sw / 2f, box.bottom - sw / 2f);
        c.drawRoundRect(R, r, r, EDGE);
    }

    /** Read ahead (a row coming into sight), at a box's size (screen px). */
    static void prefetch(String url, float w, float h) {
        if (url != null && !url.isEmpty()) Pics.prefetch(url, Math.round(w), Math.round(h), mode());
    }

    // ---- making the pictures (Pics' readers, off the main thread) ------------------------------------------------------

    /** Pure: whether a logo's pixels are mostly too dark for a dark ground (LogoTile's rule). */
    static boolean dark(int[] px) {
        return LogoTile.dark(px);
    }

    /**
     * The colours of a logo as it comes (LogoColour, on a small copy), kept for its address: what its tile and its
     * channel's cell are filled with.
     */
    static Tint colours(String url, Bitmap asItComes) {
        Tint t = new Tint(LogoColour.tile(asItComes));
        if (url != null && !url.isEmpty()) COLOURS.put(url, t);
        return t;
    }

    /**
     * COLOUR: the logo (made clear, LogoTile.clear) fitted on its colours' tile (Tint), w x h, as 07 draws it: the
     * tile's fall, a line of light along its top, a hairline round it.
     */
    static Bitmap onColour(Tint t, Bitmap logo, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        float r = h * RADIUS, sw = Math.max(1f, h / 64f);
        RectF box = new RectF(0, 0, w, h);
        p.setShader(new LinearGradient(0, 0, 0, h, t.tileTop, t.tileFoot, Shader.TileMode.CLAMP));
        c.drawRoundRect(box, r, r, p);
        p.setShader(null);
        // The line of light along its top (CSS: inset 0 1px 0): the tile less itself moved down a pixel.
        Path top = new Path(), under = new Path();
        top.addRoundRect(box, r, r, Path.Direction.CW);
        under.addRoundRect(new RectF(0, sw, w, h + sw), r, r, Path.Direction.CW);
        top.op(under, Path.Op.DIFFERENCE);
        p.setColor(t.neutral ? SLATE_TOP_LIGHT : TOP_LIGHT);
        c.drawPath(top, p);
        // Its hairline round it (inset 0 0 0 1px).
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setColor(t.neutral ? SLATE_RING : RING);
        c.drawRoundRect(new RectF(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f), r, r, p);
        if (logo != null) {
            int lw = logo.getWidth(), lh = logo.getHeight();
            float[] d = LogoTile.fit(lw, lh, w, h);
            Paint b = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(logo, new Rect(0, 0, lw, lh), new RectF(d[0], d[1], d[2], d[3]), b);
            logo.recycle();
        }
        return out;
    }

    /**
     * NONE: the logo (made clear) at its size in a w x h box (FILL_W x FILL_H of it, as on a tile), with a soft halo
     * behind its shape when it is too faint on the ground (LogoHalo, judged on HALO_GROUND); the picture is a little
     * larger than the logo then (the halo's room), drawn centred in the box.
     */
    static Bitmap bare(Bitmap logo, int w, int h) {
        int lw = logo.getWidth(), lh = logo.getHeight();
        float[] d = LogoTile.fit(lw, lh, w, h);
        int dw = Math.max(1, Math.round(d[2] - d[0])), dh = Math.max(1, Math.round(d[3] - d[1]));
        Bitmap sized = dw == lw && dh == lh ? logo : Bitmap.createScaledBitmap(logo, dw, dh, true);
        if (sized != logo) logo.recycle();
        Bitmap out = LogoHalo.apply(sized, HALO_GROUND);
        if (out != sized) sized.recycle();
        return out;
    }

    /** Pure: a colour's HSV saturation (0 to 1). */
    static float saturation(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return max == 0 ? 0f : (max - min) / (float) max;
    }

    /**
     * Pure: a channel cell's {top, foot} for the logo alone on it, from the shared row colours: opaque, its hue kept, at
     * most ROW_TOP_V and ROW_FOOT_V bright (darker where the shared rule made it darker), at least ROW_SAT saturated; a
     * neutral (slate) row as it is, opaque.
     */
    static int[] rowDeep(int top, int foot, boolean neutral) {
        if (neutral) return new int[]{top | 0xFF000000, foot | 0xFF000000};
        float[] t = new float[3], f = new float[3];
        android.graphics.Color.colorToHSV(top | 0xFF000000, t);
        android.graphics.Color.colorToHSV(foot | 0xFF000000, f);
        float hue = t[1] >= f[1] ? t[0] : f[0], sat = Math.min(0.95f, Math.max(ROW_SAT, Math.max(t[1], f[1])));
        float tv = Math.min(t[2], ROW_TOP_V), fv = Math.min(Math.min(f[2], ROW_FOOT_V), tv);
        return new int[]{android.graphics.Color.HSVToColor(new float[]{hue, sat, tv}) | 0xFF000000,
                android.graphics.Color.HSVToColor(new float[]{hue, sat, fv}) | 0xFF000000};
    }

    // ---- a channel's cell: the logo alone (ChannelLook) ---------------------------------------------------------------

    /** The cell logo's picture for the setting in use (Pics.CELL_COLOUR, CELL_NONE or CELL_BLACK). */
    static int cellMode() {
        return behind == BLACK ? Pics.CELL_BLACK : behind == NONE ? Pics.CELL_NONE : Pics.CELL_COLOUR;
    }

    /**
     * The channel's logo alone in a cell's box (screen px: the cell less its room), as large as the box allows at its own
     * shape, centred on whole pixels (drawn as it was read: nothing scaled a frame); false while it loads or for none.
     */
    static boolean drawCell(Canvas c, String url, RectF box, Runnable redraw) {
        int w = Math.round(box.width()), h = Math.round(box.height());
        if (w < 2 || h < 2 || url == null || url.isEmpty()) return false;
        Bitmap b = Pics.get(url, w, h, cellMode(), redraw);
        if (b == null) return false;
        BMP.setAlpha(255);
        c.drawBitmap(b, Math.round(box.centerX() - b.getWidth() / 2f), Math.round(box.centerY() - b.getHeight() / 2f), BMP);
        return true;
    }

    /** Read a cell's logo ahead (a row coming into sight), at its box's size (screen px). */
    static void prefetchCell(String url, float w, float h) {
        if (url != null && !url.isEmpty()) Pics.prefetch(url, Math.round(w), Math.round(h), cellMode());
    }

    /**
     * Pics' readers: a logo (read sharp for a box w x h) made for its cell as the mode says, its colours worked out and
     * kept first (its cell's colour): a plain box round it taken away and its margins trimmed at the size it was read
     * (never through a small copy, so a large logo stays sharp), then made to read there (Black tile: made ready for
     * black; else a logo that would not show on its ground takes tv-logos' white version, else the shared halo judged
     * on that ground: the cell's deep colour, or 07's for None) and fitted to the box at its own shape. null: nothing
     * of it to draw.
     */
    static Bitmap cell(String url, Bitmap read, int w, int h, int mode) {
        Tint t = colours(url, read);
        boolean black = mode == Pics.CELL_BLACK;
        Bitmap logo = prepared(read, black);
        read.recycle();
        if (logo == null) return null;
        if (black) return fitted(logo, w, h);
        int ground = mode == Pics.CELL_COLOUR ? t.row : HALO_GROUND;
        if (unseen(logo, ground)) {
            Bitmap white = Pics.lighterSharp(url, w, h);
            Bitmap ready = white == null ? null : prepared(white, false);
            if (white != null) white.recycle();
            if (ready != null && !unseen(ready, ground)) {
                logo.recycle();
                logo = ready;
            } else if (ready != null) {
                ready.recycle();
            }
        }
        Bitmap sized = fitted(logo, w, h);
        Bitmap out = LogoHalo.apply(sized, ground);
        if (out != sized) sized.recycle();
        return out;
    }

    /** Whether a made-clear logo hardly shows on a ground (mostly dark there, or too faint: LogoHalo's rule). */
    private static boolean unseen(Bitmap logo, int ground) {
        int w = logo.getWidth(), h = logo.getHeight();
        int[] px = new int[w * h];
        logo.getPixels(px, 0, w, 0, 0, w, h);
        return LogoHalo.needs(px, w, h, ground) || ground == HALO_GROUND && dark(px);
    }

    /**
     * A logo with a plain box round it taken away (and, flip, made ready for black: LogoTile's rule) and its empty
     * margins trimmed, at the size it is (LogoTile.clean and bounds on all its pixels); null for nothing to draw.
     */
    static Bitmap prepared(Bitmap logo, boolean flip) {
        Bitmap soft = logo.getConfig() == Bitmap.Config.HARDWARE ? logo.copy(Bitmap.Config.ARGB_8888, false) : logo;
        if (soft == null) return null;
        int w = soft.getWidth(), h = soft.getHeight();
        int[] px = new int[w * h];
        soft.getPixels(px, 0, w, 0, 0, w, h);
        if (soft != logo) soft.recycle();
        int[] clean = LogoTile.clean(px, w, h, flip);
        int[] bb = LogoTile.bounds(clean, w, h);
        if (bb == null) return null;
        int bw = bb[2] - bb[0], bh = bb[3] - bb[1];
        int[] cut = new int[bw * bh];
        for (int y = 0; y < bh; y++) System.arraycopy(clean, (bb[1] + y) * w + bb[0], cut, y * bw, bw);
        return Bitmap.createBitmap(cut, bw, bh, Bitmap.Config.ARGB_8888);
    }

    /** Pure: a logo bw x bh fitted in a box w x h at its own shape, as large as it allows: {width, height}, whole pixels. */
    static int[] fit(int bw, int bh, int w, int h) {
        float s = Math.min(w / (float) Math.max(1, bw), h / (float) Math.max(1, bh));
        return new int[]{Math.max(1, Math.min(w, Math.round(bw * s))), Math.max(1, Math.min(h, Math.round(bh * s)))};
    }

    /** The logo fitted to the box (fit): halved with filtering while that is still larger, so a large logo shrinks cleanly. */
    private static Bitmap fitted(Bitmap logo, int w, int h) {
        int[] d = fit(logo.getWidth(), logo.getHeight(), w, h);
        Bitmap b = logo;
        while (b.getWidth() / 2 >= d[0] * 1.2f && b.getHeight() / 2 >= d[1] * 1.2f) {
            Bitmap half = Bitmap.createScaledBitmap(b, Math.max(1, b.getWidth() / 2), Math.max(1, b.getHeight() / 2), true);
            if (b != logo) b.recycle();
            b = half;
        }
        if (b.getWidth() == d[0] && b.getHeight() == d[1]) {
            if (b != logo) logo.recycle();
            return b;
        }
        Bitmap out = Bitmap.createScaledBitmap(b, d[0], d[1], true);
        if (b != logo && b != out) b.recycle();
        if (logo != out) logo.recycle();
        return out;
    }
}
