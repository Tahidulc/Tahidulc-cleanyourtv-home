package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
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
 * <p>It costs one full screen of fill when settled: the picture and its dim are a single shader (the dim composed over
 * the picture), drawn once; the plain ground (no picture) is one gradient. A cross-fade adds the one leaving. In Motion's
 * Lite tier the big picture is not swapped while the remote moves: the ground is the picture's own colour under the dim,
 * cross-fading from one colour to the next (no picture read or drawn), and the picture comes in once the remote has
 * rested REST_MS on something (Frost's panels take that colour meanwhile, so nothing is sampled while moving).
 *
 * <pre>
 *   Ground ground = new Ground(view);                 one per screen, made with its view
 *   ground.set(url)                                   the picture of what the remote is on ("" or null: none, the
 *                                                     plain ground; the last one stays while the next is read)
 *   if (ground.draw(c, w, h)) postInvalidateOnAnimation();   first in the view's onDraw (screen px)
 * </pre>
 */
final class Ground {
    /** Lite: how long the remote rests on something before its picture is read and comes in. */
    static final long REST_MS = 320;
    /** Lite: a colour's cross-fade, and the picture coming in over it once the remote rests (and going as it moves). */
    static final long TINT_MS = 260, IN_MS = 420, OUT_MS = 200;

    /** The ground panels over the screen take their blur from (Frost): the one drawn last (held weakly: its screen may go). */
    private static WeakReference<Ground> current = new WeakReference<>(null);

    /** The ground drawn last, or null (Frost checks it is still on screen: showing()). */
    static Ground current() {
        return current.get();
    }

    /** A picture placed where the ground draws it, with the dim composed over it (made when the picture changes). */
    private static final class Layer {
        Bitmap bitmap;
        BitmapShader shader;
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

        void set(Bitmap b, RectF dst, Shader dim) {
            bitmap = b;
            if (b == null) {
                shader = null;
                paint.setShader(null);
                return;
            }
            shader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float s = Math.max(dst.width() / b.getWidth(), dst.height() / b.getHeight());
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate(dst.centerX() - b.getWidth() * s / 2f, dst.centerY() - b.getHeight() * s / 2f);
            shader.setLocalMatrix(m);
            paint.setShader(new ComposeShader(shader, dim, PorterDuff.Mode.SRC_OVER));
        }
    }

    private final View owner;
    private final Runnable redraw;
    private String url = "";
    private Layer shown = new Layer(), leaving = new Layer();
    private long shownAt = -100_000L, changedAt = -100_000L;
    private int w, h;
    private LinearGradient dim;
    private final Paint plain = new Paint(Paint.DITHER_FLAG);
    /** Where the blurred picture lies (screen px): past the screen's edges, as the mock-ups' -90 px and scale 1.1. */
    final RectF dst = new RectF();
    /** Lite: the colour shown (cross-fading from tintFrom to tintTo since tintAt) and how far the picture is in (0-1). */
    private int tintFrom = Look07.GROUND, tintTo = Look07.GROUND;
    private long tintAt = -100_000L;
    private float picIn, picFrom;
    private boolean picWant;
    private long picAt = -100_000L;
    /** Lite: a redraw once the remote has rested (one frame then, none while waiting). */
    private final Runnable rested;

    Ground(View owner) {
        this.owner = owner;
        this.redraw = owner::invalidate;
        this.rested = owner::invalidate;
    }

    /** The picture of what the remote is on (its address; "" or null for none). */
    void set(String u) {
        String next = u == null ? "" : u;
        if (next.equals(url)) return;
        // The page's first picture comes at once; the next ones (the remote moving) after it rests (Lite).
        boolean first = url.isEmpty() && shown.bitmap == null;
        url = next;
        changedAt = first ? -100_000L : SystemClock.uptimeMillis();
        if (Motion.lite() && !Exec.inline) {
            owner.removeCallbacks(rested);
            owner.postDelayed(rested, REST_MS + 10);
        }
        owner.invalidate();
    }

    String url() {
        return url;
    }

    /** The blurred picture on screen now (null: none yet). */
    Bitmap picture() {
        return shown.bitmap;
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
            dim = new LinearGradient(0, 0, 0, h, Look07.DIM, Look07.DIM_AT, Shader.TileMode.CLAMP);
            int[] over = new int[Look07.DIM.length];
            for (int i = 0; i < over.length; i++) over[i] = Palette.over(Palette.GROUND, Look07.DIM[i]);
            plain.setShader(new LinearGradient(0, 0, 0, h, over, Look07.DIM_AT, Shader.TileMode.CLAMP));
            Bitmap s = shown.bitmap, l = leaving.bitmap;
            shown.set(s, dst, dim);
            leaving.set(l, dst, dim);
            if (dimmedFor != h) {
                tintNow.setShader(null);
                tintWas.setShader(null);
            }
        }
        if (current.get() != this) current = new WeakReference<>(this);
        return Motion.lite() && !Exec.inline ? lite(c) : full(c);
    }

    /** Full: the picture cross-fading from the one before (each with its dim, one draw each). */
    private boolean full(Canvas c) {
        Bitmap b = url.isEmpty() || w <= 0 ? null : Pics.get(url, w, h, Pics.BLUR, redraw);
        if (b != shown.bitmap && (b != null || url.isEmpty() || Pics.failed(url))) {
            Layer t = leaving;
            leaving = shown;
            shown = t;
            shown.set(b, dst, dim);
            shownAt = SystemClock.uptimeMillis();
        }
        float k = Exec.inline ? 1f : Fade.ease((SystemClock.uptimeMillis() - shownAt) / (float) Look07.GROUND_FADE_MS);
        boolean moving = k < 1f;
        if (!moving && leaving.bitmap != null) leaving.set(null, dst, dim);
        int a = Math.round(255 * k);
        if (shown.bitmap != null && a >= 255) {
            // Settled: one draw (the picture with its dim) covers the screen.
            shown.paint.setAlpha(255);
            c.drawRect(0, 0, w, h, shown.paint);
            return false;
        }
        // Under the one coming in: the one leaving (whole), else the plain ground.
        if (leaving.bitmap != null && moving) {
            leaving.paint.setAlpha(255);
            c.drawRect(0, 0, w, h, leaving.paint);
        } else {
            c.drawRect(0, 0, w, h, plain);
        }
        if (shown.bitmap != null && a > 0) {
            shown.paint.setAlpha(a);
            c.drawRect(0, 0, w, h, shown.paint);
        }
        return moving;
    }

    /**
     * Lite: while the remote moves, the picture's colour under the dim (cross-fading from colour to colour); once it has
     * rested REST_MS, the picture read and brought in over it. Moving again, the picture goes and the colour follows.
     */
    private boolean lite(Canvas c) {
        long now = SystemClock.uptimeMillis();
        boolean resting = now - changedAt >= REST_MS;
        // The colour to show: the new picture's own (known once its blurred copy was made), else the one there was.
        int to = url.isEmpty() ? Look07.GROUND : Pics.tintOf(url);
        if (to != 0 && to != tintTo) {
            tintFrom = tint(now);
            tintTo = to;
            tintAt = now;
            // Each colour under the dim as one opaque gradient, made as the colour changes (a key press), not a frame.
            Paint t = tintWas;
            tintWas = tintNow;
            tintNow = t;
            dimmed(tintWas, tintFrom);
            dimmed(tintNow, tintTo);
        }
        // The picture: read only once resting; brought in over the colour.
        Bitmap b = url.isEmpty() || w <= 0 ? null : resting ? Pics.get(url, w, h, Pics.BLUR, redraw) : null;
        boolean want = b != null;
        if (want && b != shown.bitmap && picIn <= 0.001f) {
            shown.set(b, dst, dim);
        }
        boolean wantShown = want && b == shown.bitmap;
        if (wantShown != picWant) {
            picWant = wantShown;
            picFrom = picIn;
            picAt = now;
        }
        long len = picWant ? IN_MS : OUT_MS;
        float pk = Fade.ease(Math.min(1f, (now - picAt) / (float) len));
        picIn = picFrom + ((picWant ? 1f : 0f) - picFrom) * pk;
        boolean moving = pk < 1f;
        // A new picture waiting for the old to go first (going out, then the new one in).
        if (want && b != shown.bitmap) moving = true;
        int a = Math.round(255 * picIn);
        if (a < 255 || shown.bitmap == null) {
            // The colour: one opaque gradient, and the one it leaves under it while it cross-fades.
            float tk = Math.min(1f, (now - tintAt) / (float) TINT_MS);
            if (tintNow.getShader() == null) dimmed(tintNow, tintTo);
            if (tk < 1f) {
                moving = true;
                if (tintWas.getShader() == null) dimmed(tintWas, tintFrom);
                tintWas.setAlpha(255);
                c.drawRect(0, 0, w, h, tintWas);
                tintNow.setAlpha(Math.round(255 * Fade.ease(tk)));
            } else {
                tintNow.setAlpha(255);
            }
            c.drawRect(0, 0, w, h, tintNow);
        }
        if (shown.bitmap != null && a > 0) {
            shown.paint.setAlpha(a);
            c.drawRect(0, 0, w, h, shown.paint);
        }
        if (!picWant && picIn <= 0.001f && shown.bitmap != null && !want) shown.set(null, dst, dim);
        return moving;
    }

    private Paint tintNow = new Paint(Paint.DITHER_FLAG), tintWas = new Paint(Paint.DITHER_FLAG);
    private int dimmedFor = -1;

    /** A paint of the colour under the page's dim (an opaque gradient down the screen). */
    private void dimmed(Paint p, int colour) {
        if (h <= 0) return;
        int[] over = new int[Look07.DIM.length];
        for (int i = 0; i < over.length; i++) over[i] = Palette.over(colour | 0xFF000000, Look07.DIM[i]);
        p.setShader(new LinearGradient(0, 0, 0, h, over, Look07.DIM_AT, Shader.TileMode.CLAMP));
        dimmedFor = h;
    }

    /** Lite: the colour shown now, as it cross-fades (opaque). */
    private int tint(long now) {
        float k = Fade.ease(Math.min(1f, (now - tintAt) / (float) TINT_MS));
        return Theme.mix(tintFrom, tintTo, k) | 0xFF000000;
    }

    /**
     * Lite: the colour Frost puts behind its panels while the picture isn't fully in (0 when the picture is, or in Full):
     * the ground's colour as it is now, under the dim's middle.
     */
    int tintBehind() {
        if (!Motion.lite() || Exec.inline || picIn >= 0.999f && shown.bitmap != null) return 0;
        return Palette.over(tint(SystemClock.uptimeMillis()), Look07.DIM[1]);
    }

    /** The shown picture as a shader placed where the ground draws it (for Frost); null: none. */
    BitmapShader shader() {
        return shown.shader;
    }
}
