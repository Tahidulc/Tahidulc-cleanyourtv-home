package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import com.gathin.broadcast.PicPlace;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.LogoPack;

/**
 * "The programme background on the tiles", in one call, laid out so faces are seen clearly. A programme's own picture
 * (Sky's public guide, through ProgrammePictures), its faces found once as it is read (Faces, kept with it in Pics).
 *
 * <pre>
 *   fill     a programme's cell of any size (the guide's cells): the cell taken up by its picture, placed by the rule
 *            shared with the phone (PicPlace, PicFill.row): covering a cell close to its shape, else (a long cell) the
 *            whole picture at the cell's height after the words, the cell's own colour from the picture's edges
 *            continuing smoothly either side, its faces whole; made once, pre-scaled, for the whole programme's width
 *            (Pics.SLIDE); as the guide scrolls and the cell is cut by its edges, the picture glides along the cell
 *            (PicPlace.slide: its faces at the middle of the part in sight after the words), never re-cropped, never
 *            jumping; then shade() under the words only: a soft shade from the cell's left that ends a little past
 *            them, and a light fade along the foot for the progress line; the picture clear everywhere else
 *   COVER    a tile whose words are under it (a card, a strip): the picture fills it, the faces in the middle; with
 *            none (yet), the channel's logo on its tile in its middle (Logos)
 * </pre>
 *
 * Each picture is read once at the size drawn (within Pics' budget) and drawn through a kept shader; where its faces
 * landed is a few sums a frame, nothing made.
 *
 * <pre>
 *   PicTile tiles = new PicTile(view);                                          one per view, made with it
 *   if (tiles.fill(c, cell, radius, url, fullLeft, fullW, wordsEnd, focused, done))
 *       tiles.shade(c, cell, radius, wordsEnd, foot);
 *   else the cell plain (its picture is read, or there is none)
 *       cell: screen px, its part in sight; fullLeft, fullW: the whole cell's left and width (the guide's programme,
 *       uncut; the size it is read at); wordsEnd: where its words end (screen px); done: already shown (dimmer)
 *   tiles.draw(c, cell, radius, ch, s, i, PicTile.COVER, focused, done, settled)   programme i's picture (settled false
 *       while a key is held: only what is known)
 *   tiles.draw(c, cell, radius, url, PicTile.COVER, focused, done)               by address; -1 while it is read
 *   tiles.colour(cellColour)                                                    the cells' own colour (Look07.CELL)
 * </pre>
 */
final class PicTile {
    static final int COVER = 1;
    /** The words' shade (fill): its colour, its strength at the cell's left and where the words end, its fade past
     *  them (design px: as long as FADE, shorter to stop before the faces, never under FADE_MIN); the foot's fade: its
     *  strength at the foot, the share of the cell it rises. */
    static final int SHADE = 0x070A12;
    static final int SHADE_LEFT = 0xA3, SHADE_WORDS = 0x80, FOOT = 0x6B;
    static final float FADE = 72f, FADE_MIN = 24f, FOOT_SHARE = 0.36f;
    /** A cell narrower than this (design px) takes no picture: nothing of it would show. */
    static final float MIN_W = 24f;

    private final View owner;
    private final Runnable redraw;
    private final Shaders shaders = new Shaders();
    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
            words = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), fade = new Paint(Paint.DITHER_FLAG),
            foot = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF logo = new RectF();
    private final android.graphics.Matrix groundAt = new android.graphics.Matrix();
    private final Paint ground = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private int cellColour = Look07.CELL;
    private float facesLeft, facesRight;

    PicTile(View owner) {
        this.owner = owner;
        this.redraw = owner::invalidate;
        ring.setStyle(Paint.Style.STROKE);
        colour(Look07.CELL);
        // Unit boxes, stretched to each cell (made once: nothing made a frame).
        shades(words, fade, SHADE, SHADE_LEFT, SHADE_WORDS);
        foot.setShader(new LinearGradient(0, 0, 0, 1, new int[]{SHADE, (FOOT / 3) << 24 | SHADE, FOOT << 24 | SHADE}, new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
    }

    /** The cells' own colour (Look07.CELL as it comes), under a picture with none and while one is read. */
    void colour(int c) {
        cellColour = c | 0xFF000000;
        cell.setColor(cellColour);
    }

    /**
     * Programme picture url taking up the cell r (screen px: its part in sight) of a programme whose whole cell starts at
     * fullLeft and is fullW wide (screen px; the size it is read at, Pics.SLIDE): its ground (the picture's edges' colours
     * drawn out), then the picture, pre-scaled, where PicPlace.slide puts it for the part in sight. Nothing is scaled or
     * made while drawing (a tiny ground picture stretched aside). False while it is read (or can't be): the cell plain.
     */
    boolean fill(Canvas c, RectF r, float radius, String url, float fullLeft, int fullW, float wordsEnd, boolean focused, boolean done) {
        float w = r.width(), h = r.height();
        if (url == null || url.isEmpty() || w < Ui.f(MIN_W) || h < 2) return false;
        int bw = Math.max(fullW, Math.round(w)), bh = Math.round(h);
        Bitmap b = Pics.get(url, bw, bh, Pics.SLIDE, redraw);
        if (b == null) return false;
        Pics.Placed pl = Pics.placed(b);
        float[] lay = pl == null ? null : pl.lay;
        float at = pl == null ? 0f : pl.at;
        // The picture made starts at at across the whole cell; its subject (its faces' middle, else its own) within it.
        float subject = lay == null ? b.getWidth() / 2f : lay[PicPlace.X] + lay[PicPlace.SUBJECT] - at;
        // The whole cell (a programme longer than the window is read at the window's width, but glides along all of it).
        float full = Math.max(bw, r.right - fullLeft), inL = r.left - fullLeft;
        float x = PicPlace.slide(full, b.getWidth(), subject, inL, inL + w, wordsEnd - fullLeft);
        float left = fullLeft + x;
        boolean dim = done && !focused;
        if (dim) c.drawRoundRect(r, radius, radius, cell);
        android.graphics.BitmapShader g = pl == null ? null : pl.ground();
        if (g != null) {
            // The cell's own colour from the picture's edges, drawn out either side of where the picture is now.
            com.gathin.broadcast.PicArt.groundMatrix(pl.edges, lay, groundAt);
            groundAt.postTranslate(fullLeft + x - at, r.top);
            g.setLocalMatrix(groundAt);
            ground.setShader(g);
            ground.setAlpha(dim ? 150 : 255);
            c.drawRoundRect(r, radius, radius, ground);
        }
        Paint p = shaders.paint(b, b.getWidth(), b.getHeight(), 0f);
        p.setAlpha(dim ? 150 : 255);
        c.save();
        // Only where the picture is (its feathered edges see-through): a clip, never a layer.
        if (b.getWidth() < bw || x > 0f) c.clipRect(Math.max(r.left, left), r.top, Math.min(r.right, left + b.getWidth()), r.bottom);
        c.translate(left, r.top);
        c.drawRoundRect(r.left - left, 0, r.right - left, h, radius, radius, p);
        c.restore();
        p.setAlpha(255);
        if (lay != null && pl.faces) {
            facesLeft = left - at + lay[PicPlace.FL];
            facesRight = left - at + lay[PicPlace.FR];
        } else if (lay != null && lay[PicPlace.KIND] == PicPlace.SHOW) {
            facesLeft = left;
            facesRight = left + b.getWidth();
        } else {
            facesLeft = r.right;
            facesRight = r.right;
        }
        edge(c, r, radius);
        return true;
    }

    /** Where the last filled cell's faces are across (screen px; the whole cell for a picture with none found). */
    float facesLeft() {
        return facesLeft;
    }

    float facesRight() {
        return facesRight;
    }

    /**
     * The words' shade over a filled cell: from its left, a little stronger there, to wordsEnd, then fading to nothing
     * a little past them (FADE, ending before the faces); foot: a light fade along its foot under the progress line.
     * Nowhere else: the picture reads across the cell and its faces stay clear.
     */
    void shade(Canvas c, RectF r, float radius, float wordsEnd, boolean foot) {
        float w = r.width(), h = r.height();
        float room = facesLeft > wordsEnd ? facesLeft - wordsEnd + Ui.f(16f) : Ui.f(FADE);
        shade(c, r, radius, wordsEnd, Math.max(Ui.f(FADE_MIN), Math.min(Ui.f(FADE), room)), words, fade);
        if (foot) {
            float fh = Math.max(1f, h * FOOT_SHARE);
            c.save();
            c.translate(r.left, r.bottom - fh);
            c.scale(1f, fh);
            c.drawRoundRect(0, 0, w, 1f, radius, radius / fh, this.foot);
            c.restore();
        }
    }

    /**
     * A words' shade's two paints (unit boxes, made once): solid from strength left at the box's left to words at its
     * right, and fade from words to nothing, in colour (no alpha).
     */
    static void shades(Paint solid, Paint fade, int colour, int left, int words) {
        solid.setShader(new LinearGradient(0, 0, 1, 0, left << 24 | colour, words << 24 | colour, Shader.TileMode.CLAMP));
        fade.setShader(new LinearGradient(0, 0, 1, 0, new int[]{words << 24 | colour, (words * 11 / 20) << 24 | colour, colour},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
    }

    /**
     * A words' shade over r: solid from its left to end (its left corners round, its right ones too where it reaches
     * r's right), then fading over fadeW (screen px), within r. Nothing made.
     */
    static void shade(Canvas c, RectF r, float radius, float end, float fadeW, Paint solid, Paint fade) {
        float h = r.height(), e = Math.min(r.right, end);
        if (e > r.left + 1f) {
            boolean all = e > r.right - radius - 1f;
            // Its right corners past the clip (or the cell's own where it reaches the cell's right).
            float span = all ? r.width() : e - r.left + 2f * radius;
            c.save();
            if (!all) c.clipRect(r.left, r.top, e, r.bottom);
            c.translate(r.left, r.top);
            c.scale(span, 1f);
            c.drawRoundRect(0, 0, 1f, h, radius / span, radius, solid);
            c.restore();
        }
        float fe = Math.min(r.right, e + fadeW);
        if (fe > e + 1f) {
            c.save();
            c.translate(e, r.top);
            c.scale(fe - e, 1f);
            c.drawRect(0, 0, 1f, h, fade);
            c.restore();
        }
    }

    /** Programme i of channel ch (schedule s) behind the cell r, COVER; returns where its words must end. */
    float draw(Canvas c, RectF r, float radius, Channel ch, Guide.Schedule s, int i, int kind, boolean focused, boolean done, boolean settled) {
        String url = i < 0 || ch == null || s == null ? "" : settled ? ProgrammePictures.get(owner.getContext(), ch, s, i, redraw)
                : ProgrammePictures.peek(ch, s, i);
        if (url != null && !url.isEmpty()) {
            float end = draw(c, r, radius, url, kind, focused, done);
            if (end >= 0f) return end;
        }
        // No picture (yet): the cell's colour, the channel's logo on its tile in its middle.
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

    /** A picture by its address behind the cell r, COVER: where its words must end (screen px), or -1 while it is read. */
    float draw(Canvas c, RectF r, float radius, String url, int kind, boolean focused, boolean done) {
        float w = r.width(), h = r.height();
        if (url == null || url.isEmpty() || w < 2 || h < 2) return -1f;
        Bitmap b = Pics.get(url, Math.round(w), Math.round(h), Pics.FACE_MID, redraw);
        if (b == null) return -1f;
        c.drawRoundRect(r, radius, radius, cell);
        Paint p = shaders.paint(b, w, h, 0f);
        p.setAlpha(done && !focused ? 140 : 255);
        c.save();
        c.translate(r.left, r.top);
        c.drawRoundRect(0, 0, w, h, radius, radius, p);
        c.restore();
        p.setAlpha(255);
        edge(c, r, radius);
        return r.right;
    }

    /** The cell's hairline (inset 0 0 0 1px white at 7 %). */
    private void edge(Canvas c, RectF r, float radius) {
        float sw = Math.max(1f, Ui.f(1f));
        ring.setStrokeWidth(sw);
        ring.setColor(0x12FFFFFF);
        c.drawRoundRect(r.left + sw / 2f, r.top + sw / 2f, r.right - sw / 2f, r.bottom - sw / 2f, radius, radius, ring);
    }
}
