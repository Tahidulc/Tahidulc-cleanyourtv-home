package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * A programme's picture filling a guide cell or the panel, as Homefield TV draws its cells (its PicTile): the picture
 * made for the whole cell (ProgPics), a window of it on its faces where the cell is cut by the guide's edges, then a
 * soft shade under the words only: from the cell's left, a little stronger there, to where the words end, fading to
 * nothing a little past them and before the faces; and a light fade along the foot under the progress line. Nothing
 * made while drawing: the shades are unit boxes stretched to each cell.
 */
final class PicDraw {
    /** The shade's colour, its strength at the left and where the words end, and at the foot. */
    static final int SHADE = 0x070A12, SHADE_LEFT = 0xA3, SHADE_WORDS = 0x80, FOOT = 0x6B;
    static final float FOOT_SHARE = 0.4f;

    private final Paint pic = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG),
            solid = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), fade = new Paint(Paint.DITHER_FLAG),
            foot = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    /** The fade past the words (px): as long as fadeW, shorter to stop before the faces, never under fadeMin. */
    final float fadeW, fadeMin, gap;
    private float facesLeft;

    PicDraw(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        fadeW = 40f * d;
        fadeMin = 14f * d;
        gap = 4f * d;
        solid.setShader(new LinearGradient(0, 0, 1, 0, SHADE_LEFT << 24 | SHADE, SHADE_WORDS << 24 | SHADE, Shader.TileMode.CLAMP));
        fade.setShader(new LinearGradient(0, 0, 1, 0, new int[]{SHADE_WORDS << 24 | SHADE, (SHADE_WORDS * 11 / 20) << 24 | SHADE, SHADE},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        foot.setShader(new LinearGradient(0, 0, 0, 1, new int[]{SHADE, (FOOT / 3) << 24 | SHADE, FOOT << 24 | SHADE}, new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
    }

    private final android.graphics.Matrix scale = new android.graphics.Matrix();
    private float ox;

    /**
     * Where picture m (made for the whole cell) goes in r (screen px, its part in sight): a window of it on its faces,
     * beside the words (ending wordsEnd) where there is room. Whether its faces are then clear of the words (or it has
     * none): a cell whose words leave its faces no room takes the picture's own colours instead (ground()).
     */
    boolean place(RectF r, ProgPics.Made m, float wordsEnd, float room) {
        ox = PicFill.window(m.b.getWidth(), r.width(), m.facesLeft, m.facesRight, wordsEnd - r.left, room);
        facesLeft = r.left + m.facesLeft - ox;
        return PicFill.clear(m.faces, facesLeft, wordsEnd, gap);
    }

    /** Picture m filling r, as place() put it. */
    void fill(Canvas cv, RectF r, float radius, ProgPics.Made m) {
        float w = r.width(), h = r.height();
        pic.setShader(m.shader());
        cv.save();
        cv.translate(r.left - ox, r.top);
        cv.drawRoundRect(ox, 0, ox + w, h, radius, radius, pic);
        cv.restore();
        pic.setShader(null);
    }

    /** The picture's own colours filling r, smooth (a few of its pixels drawn up), no faces; false with none kept. */
    boolean ground(Canvas cv, RectF r, float radius, ProgPics.Made m) {
        android.graphics.BitmapShader s = m.groundShader();
        if (s == null) return false;
        scale.setScale(r.width() / m.ground.getWidth(), r.height() / m.ground.getHeight());
        scale.postTranslate(r.left, r.top);
        s.setLocalMatrix(scale);
        pic.setShader(s);
        cv.drawRoundRect(r, radius, radius, pic);
        pic.setShader(null);
        facesLeft = r.right;
        return true;
    }

    /** Where the last picture's faces start across (screen px). */
    float facesLeft() {
        return facesLeft;
    }

    /** As if the faces started at x (a stand-in with none: the logo's place). */
    void facesAt(float x) {
        facesLeft = x;
    }

    /**
     * The words' shade over r: from its left to wordsEnd, then fading to nothing before the faces; footFade: a light
     * fade along its foot (under the progress line).
     */
    void shade(Canvas cv, RectF r, float radius, float wordsEnd, boolean footFade) {
        float e = Math.min(r.right, wordsEnd), h = r.height();
        float fw = PicFill.shadeFade(e, facesLeft, fadeW, fadeMin, gap);
        if (e > r.left + 1f) {
            boolean all = e > r.right - radius - 1f;
            float span = all ? r.width() : e - r.left + 2f * radius;
            cv.save();
            if (!all) cv.clipRect(r.left, r.top, e, r.bottom);
            cv.translate(r.left, r.top);
            cv.scale(span, 1f);
            cv.drawRoundRect(0, 0, 1f, h, radius / span, radius, solid);
            cv.restore();
        }
        float fe = Math.min(r.right, e + fw);
        if (fe > e + 1f) {
            cv.save();
            cv.translate(e, r.top);
            cv.scale(fe - e, 1f);
            cv.drawRect(0, 0, 1f, h, fade);
            cv.restore();
        }
        if (footFade) {
            float fh = Math.max(1f, h * FOOT_SHARE);
            cv.save();
            cv.translate(r.left, r.bottom - fh);
            cv.scale(1f, fh);
            cv.drawRoundRect(0, 0, r.width(), 1f, radius, radius / fh, foot);
            cv.restore();
        }
    }
}
