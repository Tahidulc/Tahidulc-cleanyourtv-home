package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.widget.LinearLayout;

/**
 * An alert's pill that opens and closes as the goal alerts do: shown from its left end only as far as visible[0],
 * with round ends. On the graphics chip its outline clips it (GoalWatchService sets that up); drawn by the CPU, as
 * over another app on the TCL, an outline does not clip, so the pill cuts itself to shape here: drawn into a
 * picture, then painted through a round-ended shape. While it opens (frozen) nothing in it changes, so the picture
 * is drawn once and each frame only paints the shape a little wider: no redrawing of the glass frame by frame.
 */
final class RevealBox extends LinearLayout {
    /** How much of the pill shows, from its left end, in pixels; null or past its width for all of it. */
    float[] visible;
    /** Set while the pill opens: its picture is drawn once and reused. */
    boolean frozen;
    private Bitmap picture;
    private boolean fresh;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    RevealBox(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    void setFrozen(boolean f) {
        frozen = f;
        fresh = false;
        if (!f) invalidate();
    }

    @Override public void draw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (canvas.isHardwareAccelerated() || visible == null || visible[0] >= w || w <= 0 || h <= 0) {
            if (picture != null && !frozen) { picture.recycle(); picture = null; }
            super.draw(canvas);
            return;
        }
        if (picture == null || picture.getWidth() != w || picture.getHeight() != h) {
            if (picture != null) picture.recycle();
            picture = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            fresh = false;
        }
        if (!frozen || !fresh) {
            picture.eraseColor(0);
            super.draw(new Canvas(picture));
            paint.setShader(new BitmapShader(picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            fresh = true;
        }
        float shown = Math.max(Math.min(h, w), visible[0]), r = h / 2f;
        canvas.drawRoundRect(0, 0, shown, h, r, r, paint);
    }
}
