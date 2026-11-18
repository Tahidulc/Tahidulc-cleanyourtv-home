package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.LogoPack;

/**
 * "The programme background on the tiles", in one call, laid out so faces are seen clearly, as the family asked ("the
 * pictures fit in with the panel so you can see faces clearly"). A programme's own picture (Sky's public guide, through
 * ProgrammePictures), its faces found once as it is read (Faces, kept with it in Pics) and cropped round so they are
 * whole, never cut by an edge, and never under a scrim. As the guide mock-ups (05) lay it out:
 *
 * <pre>
 *   a row's cell (under 120 px tall: the guide's, a list's)
 *       330 px wide and up: the picture at its own shape (16:9, 150 x 84 in the guide's 84 px cells) on the cell's
 *           right, faces inside it, the cell's own colour fading into its left edge and ending before the faces
 *       200 to 330 px: a small clean thumbnail of the whole picture (72 x 40) at its right; narrower, no picture (never
 *           half a face); under 60 px tall, the thumbnail only (as tall as it fits, 40 at most)
 *   a wide panel (120 px tall and up, 2.4 times as wide as tall: the details panel): the picture at its own shape on
 *       its right (587 x 330 in the guide's), up to six-tenths of it, the words on their own side
 *   a tile (120 px tall and up, less wide): the picture fills it, cropped with the faces toward the right; the words'
 *       fade from the left ends before the faces; where the faces leave no room there but do below them, a fade from
 *       the foot instead, the words at the foot (wordsTop)
 *   COVER: the picture fills it, the faces in the middle (a card or a strip tile whose words are under it)
 *   no picture (yet): the cell's colour (with COVER, the channel's logo on its tile in its middle: Logos)
 * </pre>
 *
 * Each picture is read once at the size drawn (Pics.FACE, within Pics' budget) and drawn through a kept shader; the
 * faces' place is a few sums a frame, nothing made.
 *
 * <pre>
 *   PicTile tiles = new PicTile(view);                                          one per view, made with it
 *   float wordsEnd = tiles.draw(c, cell, radius, ch, s, i, PicTile.WORDS_LEFT, focused, done, settled);
 *       cell: screen px; ch, s, i: the programme (i -1: none); focused: the remote's (its picture whole-bright);
 *       done: already shown (its picture dimmer); settled: the remote has rested (ask for the picture), else only what
 *       is known (holding a key asks for nothing); returns where the cell's words must end (screen px)
 *   tiles.wordsTop()                                                            where they may start down (screen px):
 *                                                                               the cell's top, or a foot fade's
 *   tiles.draw(c, cell, radius, url, kind, focused, done)                       a picture by its address (-1 while it
 *                                                                               is read, or for none: draw it plain)
 *   tiles.colour(cellColour)                                                    the cells' own colour (Look07.CELL)
 * </pre>
 */
final class PicTile {
    static final int WORDS_LEFT = 0, COVER = 1;
    /** Under this height (design px) a cell is a row's (a guide's, a list's); at or over it, a tile or a panel. */
    static final float TILE_H = 120f;
    /** A face can't be seen well in a picture under this height (design px): a whole thumbnail instead. */
    static final float FACE_H = 60f;
    /** A row's cell this wide (design px) and WIDE times as wide as tall has its picture at its own shape on its right,
     *  up to EDGE_SHARE of it (a panel's: PANEL_SHARE). */
    static final float EDGE_W = 330f, WIDE = 2.4f, EDGE_SHARE = 0.46f, PANEL_SHARE = 0.6f;
    /** A thumbnail (a row's cell from THUMB_W wide): 40 px tall (less in a shorter cell, 28 at the least), 16:9, 12 px
     *  in from the right. */
    static final float THUMB_W = 200f, THUMB_H = 40f, THUMB_MIN = 28f, THUMB_IN = 12f;
    static final float PICTURE_ASPECT = 16f / 9f;
    /** A tile's words need this share of its width beside the faces (else of its height under them). */
    static final float LEFT_ROOM = 0.35f, FOOT_ROOM = 0.28f;

    private final View owner;
    private final Runnable redraw;
    private final Shaders shaders = new Shaders();
    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
            fade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), footFade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF box = new RectF(), face = new RectF(), logo = new RectF();
    private int cellColour = Look07.CELL;
    private float wordsTop;

    PicTile(View owner) {
        this.owner = owner;
        this.redraw = owner::invalidate;
        ring.setStyle(Paint.Style.STROKE);
        colour(Look07.CELL);
    }

    /** The cells' own colour (Look07.CELL as it comes), which the words sit on and which runs into the picture. */
    void colour(int c) {
        cellColour = c | 0xFF000000;
        cell.setColor(cellColour);
        // Opaque at the words' side, clear by its end (unit boxes, stretched to each cell).
        int[] cols = {cellColour, Palette.alpha(cellColour, 0xD9), Palette.alpha(cellColour, 0)};
        fade.setShader(new LinearGradient(0, 0, 1, 0, cols, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        footFade.setShader(new LinearGradient(0, 1, 0, 0, cols, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
    }

    /** Where the last cell's words may start down (screen px): its top, or the top of a foot fade. */
    float wordsTop() {
        return wordsTop;
    }

    /** Programme i of channel ch (schedule s) behind the cell r; returns where its words must end. */
    float draw(Canvas c, RectF r, float radius, Channel ch, Guide.Schedule s, int i, int kind, boolean focused, boolean done, boolean settled) {
        String url = i < 0 || ch == null || s == null ? "" : settled ? ProgrammePictures.get(owner.getContext(), ch, s, i, redraw)
                : ProgrammePictures.peek(ch, s, i);
        if (url != null && !url.isEmpty()) {
            float end = draw(c, r, radius, url, kind, focused, done);
            if (end >= 0f) return end;
        }
        // No picture (yet): the cell's colour, the channel's logo on its tile in its middle.
        wordsTop = r.top;
        c.drawRoundRect(r, radius, radius, cell);
        edge(c, r, radius);
        if (ch != null && kind == COVER) {
            float lh = Math.min(r.height() * 0.5f, Ui.f(72f)), lw = Math.min(r.width() * 0.7f, lh * 1.69f);
            if (lw > Ui.f(40f)) {
                logo.set(r.centerX() - lw / 2f, r.centerY() - lh / 2f, r.centerX() + lw / 2f, r.centerY() + lh / 2f);
                Logos.draw(c, LogoPack.url(ch), logo, redraw);
            }
        }
        return r.right;
    }

    /**
     * A picture by its address behind the cell r: where its words must end (screen px), or -1 while it is read or where
     * the cell takes no picture (the caller draws the cell plain then).
     */
    float draw(Canvas c, RectF r, float radius, String url, int kind, boolean focused, boolean done) {
        float w = r.width(), h = r.height();
        wordsTop = r.top;
        if (url == null || url.isEmpty() || w < 2 || h < 2) return -1f;
        int alpha = done && !focused ? 140 : 255;
        if (kind == COVER) {
            Bitmap b = Pics.get(url, Math.round(w), Math.round(h), Pics.FACE_MID, redraw);
            if (b == null) return -1f;
            c.drawRoundRect(r, radius, radius, cell);
            picture(c, b, r.left, r.top, w, h, radius, alpha, false);
            edge(c, r, radius);
            return r.right;
        }
        boolean row = h < Ui.f(TILE_H);
        if (w / h >= WIDE && (!row || h >= Ui.f(FACE_H) && w >= Ui.f(EDGE_W))) return wide(c, r, radius, url, alpha, row);
        if (row) return thumbnail(c, r, radius, url, alpha);
        return tile(c, r, radius, url, alpha);
    }

    /** Wide: the picture at its own shape on the right, faces inside; the cell's colour runs into it and ends before them. */
    private float wide(Canvas c, RectF r, float radius, String url, int alpha, boolean row) {
        float w = r.width(), h = r.height();
        float pw = Math.min(h * PICTURE_ASPECT, w * (row ? EDGE_SHARE : PANEL_SHARE));
        box.set(r.right - pw, r.top, r.right, r.bottom);
        Bitmap b = Pics.get(url, Math.round(pw), Math.round(h), Pics.FACE, redraw);
        if (b == null) return -1f;
        c.drawRoundRect(r, radius, radius, cell);
        c.save();
        c.clipRect(box);
        picture(c, b, box.left, box.top, pw, h, radius, alpha, true);
        c.restore();
        faces(url, pw, h);
        float fadeW = Math.max(0f, Math.min(pw * 0.45f, face.left - Ui.f(8f)));
        if (fadeW > Ui.f(4f)) fade(c, box.left, r.top, fadeW, h, 0f);
        edge(c, r, radius);
        return box.left - Ui.f(12f) + fadeW * 0.3f;
    }

    /** A tile: the picture fills it, faces toward the right; the words' fade from the left (or the foot) ends before them. */
    private float tile(Canvas c, RectF r, float radius, String url, int alpha) {
        float w = r.width(), h = r.height();
        Bitmap b = Pics.get(url, Math.round(w), Math.round(h), Pics.FACE, redraw);
        if (b == null) return -1f;
        c.drawRoundRect(r, radius, radius, cell);
        picture(c, b, r.left, r.top, w, h, radius, alpha, false);
        faces(url, w, h);
        float left = face.left - Ui.f(12f), foot = h - face.bottom - Ui.f(8f);
        // Beside the faces where there is room for the words; else under them; else where it covers the least of them.
        boolean byFoot = left < w * LEFT_ROOM
                && (foot >= h * FOOT_ROOM || Math.max(0f, h * FOOT_ROOM - foot) / h < Math.max(0f, w * LEFT_ROOM - left) / w);
        if (byFoot) {
            float fh = Math.max(foot, h * FOOT_ROOM);
            wordsTop = r.bottom - fh;
            footFadeAt(c, r.left, wordsTop, w, fh, radius);
            edge(c, r, radius);
            return r.right - Ui.f(16f);
        }
        float end = Math.max(left, w * LEFT_ROOM * 0.85f);
        fade(c, r.left, r.top, end, h, radius);
        edge(c, r, radius);
        return r.left + end * 0.92f;
    }

    /** A row's cell too narrow (or short) for its picture's own shape: a small clean thumbnail of it at the right, or none. */
    private float thumbnail(Canvas c, RectF r, float radius, String url, int alpha) {
        float h = r.height(), w = r.width(), inset = Ui.f(THUMB_IN);
        float th = Math.min(h - 2 * Ui.f(6f), Ui.f(THUMB_H));
        if (th < Ui.f(THUMB_MIN) || w < Ui.f(THUMB_W)) return -1f;
        float tw = th * PICTURE_ASPECT;
        Bitmap b = Pics.get(url, Math.round(tw), Math.round(th), Pics.FIT, redraw);
        if (b == null) return -1f;
        c.drawRoundRect(r, radius, radius, cell);
        float x = r.right - inset - tw, y = r.centerY() - th / 2f, rr = Math.min(radius, Ui.f(8f));
        picture(c, b, x, y, tw, th, rr, alpha, false);
        edge(c, r, radius);
        return x - Ui.f(12f);
    }

    /** The picture (read at w x h) at (x, y), rounded by radius (only its right corners: the left is clipped). */
    private void picture(Canvas c, Bitmap b, float x, float y, float w, float h, float radius, int alpha, boolean rightOnly) {
        Paint p = shaders.paint(b, w, h, 0f);
        p.setAlpha(alpha);
        c.save();
        c.translate(x, y);
        if (rightOnly) c.drawRoundRect(-radius, 0, w, h, radius, radius, p);
        else c.drawRoundRect(0, 0, w, h, radius, radius, p);
        c.restore();
    }

    /** The cell's colour running from x across w (opaque to clear), its left corners round by radius. */
    private void fade(Canvas c, float x, float y, float w, float h, float radius) {
        c.save();
        c.translate(x, y);
        c.scale(w, 1f);
        c.drawRoundRect(0, 0, 1f, h, radius / w, radius, fade);
        c.restore();
    }

    /** The cell's colour rising from the foot over fh (opaque to clear), its foot's corners round by radius. */
    private void footFadeAt(Canvas c, float x, float y, float w, float fh, float radius) {
        c.save();
        c.translate(x, y);
        c.scale(1f, fh);
        c.drawRoundRect(0, 0, w, 1f, radius, radius / fh, footFade);
        c.restore();
    }

    /** Where the faces are in a picture read at w x h (px from its top left; the upper middle when none are known). */
    private void faces(String url, float w, float h) {
        float[] f = Pics.faces(url);
        if (f == null) Faces.place(PICTURE_ASPECT, 1f, w / h, Faces.FALLBACK, Pics.FACE_BIAS, w, h, face);
        else Faces.place(f[4], 1f, w / h, f, Pics.FACE_BIAS, w, h, face);
    }

    /** The cell's hairline (inset 0 0 0 1px white at 7 %). */
    private void edge(Canvas c, RectF r, float radius) {
        float sw = Math.max(1f, Ui.f(1f));
        ring.setStrokeWidth(sw);
        ring.setColor(0x12FFFFFF);
        c.drawRoundRect(r.left + sw / 2f, r.top + sw / 2f, r.right - sw / 2f, r.bottom - sw / 2f, radius, radius, ring);
    }
}
