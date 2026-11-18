package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

import java.lang.ref.WeakReference;

/**
 * The 07 page's ground: the picture of what the remote is on, blurred and vivid behind everything (the mock-ups' .bd:
 * blur(50px) saturate(1.75) brightness(.86), the picture spilling 90 px past the screen's edges and grown 1.1), under
 * the page's dim (Look07.DIM, darker at the top and the foot), cross-fading to the next picture in GROUND_FADE_MS. The
 * blur is never done while drawing: the picture is read once as Pics.BLUR (an eighth of its size, blurred once, kept in
 * Pics' budget) and drawn scaled up. Frost draws the same blurred picture inside its frosted panels, so they look like
 * frosted glass over this ground.
 *
 * <pre>
 *   Ground ground = new Ground(view);                 one per screen, made with its view
 *   ground.set(url)                                   the picture of what the remote is on ("" or null: none, the
 *                                                     plain ground; the last one stays while the next is read)
 *   if (ground.draw(c, w, h)) postInvalidateOnAnimation();   first in the view's onDraw (screen px)
 * </pre>
 */
final class Ground {
    /** The ground panels over the screen take their blur from (Frost): the one drawn last (held weakly: its screen may go). */
    private static WeakReference<Ground> current = new WeakReference<>(null);

    /** The ground drawn last, or null (Frost checks it is still on screen: showing()). */
    static Ground current() {
        return current.get();
    }

    private final View owner;
    private final Runnable redraw;
    private String url = "";
    private Bitmap shown, leaving;
    private long shownAt = -100_000L;
    private int w, h;
    private final Paint pic = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG), dim = new Paint(Paint.DITHER_FLAG);
    private final Matrix m = new Matrix();
    /** Where the blurred picture lies (screen px): past the screen's edges, as the mock-ups' -90 px and scale 1.1. */
    final RectF dst = new RectF();
    /** The shown picture as a shader placed on dst, for Frost (made when the picture changes, not each frame). */
    private BitmapShader shader;
    private Bitmap shaderFor;

    Ground(View owner) {
        this.owner = owner;
        this.redraw = owner::invalidate;
    }

    /** The picture of what the remote is on (its address; "" or null for none). */
    void set(String u) {
        String next = u == null ? "" : u;
        if (next.equals(url)) return;
        url = next;
        owner.invalidate();
    }

    String url() {
        return url;
    }

    /** The blurred picture on screen now (null: none yet). */
    Bitmap picture() {
        return shown;
    }

    /** Whether this ground's screen is on screen (Frost takes its blur only from one that is). */
    boolean showing() {
        return owner.isShown();
    }

    /** The ground (screen px, the view's size); true while it still cross-fades. */
    boolean draw(Canvas c, int width, int height) {
        if (width != w || height != h) {
            w = width;
            h = height;
            float spill = Ui.f(90f);
            float cw = (w + 2 * spill) * 1.1f, ch = (h + 2 * spill) * 1.1f;
            dst.set(w / 2f - cw / 2f, h / 2f - ch / 2f, w / 2f + cw / 2f, h / 2f + ch / 2f);
            dim.setShader(new LinearGradient(0, 0, 0, h, Look07.DIM, Look07.DIM_AT, Shader.TileMode.CLAMP));
            shaderFor = null;
        }
        if (current.get() != this) current = new WeakReference<>(this);
        c.drawColor(Palette.GROUND);
        Bitmap b = url.isEmpty() || w <= 0 ? null : Pics.get(url, w, h, Pics.BLUR, redraw);
        if (b != shown && (b != null || url.isEmpty() || Pics.failed(url))) {
            leaving = shown;
            shown = b;
            shownAt = SystemClock.uptimeMillis();
        }
        float k = Exec.inline ? 1f : Fade.ease((SystemClock.uptimeMillis() - shownAt) / (float) Look07.GROUND_FADE_MS);
        boolean moving = k < 1f;
        if (leaving != null && moving) blurred(c, leaving, 255);
        if (shown != null) blurred(c, shown, Math.round(255 * k));
        if (!moving) leaving = null;
        c.drawRect(0, 0, w, h, dim);
        return moving;
    }

    private void blurred(Canvas c, Bitmap b, int alpha) {
        // The picture covering dst (its middle kept), smoothly scaled up from its eighth.
        float s = Math.max(dst.width() / b.getWidth(), dst.height() / b.getHeight());
        m.setScale(s, s);
        m.postTranslate(dst.centerX() - b.getWidth() * s / 2f, dst.centerY() - b.getHeight() * s / 2f);
        pic.setAlpha(alpha);
        c.drawBitmap(b, m, pic);
    }

    /** The shown picture as a shader placed where the ground draws it (for Frost); null: none. */
    BitmapShader shader() {
        Bitmap b = shown;
        if (b == null) return null;
        if (b != shaderFor) {
            shader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float s = Math.max(dst.width() / b.getWidth(), dst.height() / b.getHeight());
            Matrix sm = new Matrix();
            sm.setScale(s, s);
            sm.postTranslate(dst.centerX() - b.getWidth() * s / 2f, dst.centerY() - b.getHeight() * s / 2f);
            shader.setLocalMatrix(sm);
            shaderFor = b;
        }
        return shader;
    }
}
