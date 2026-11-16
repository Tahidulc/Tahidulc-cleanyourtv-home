package com.gathin.tvhomephone.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.RequiresApi;

/**
 * Frosted glass that floats: whatever is under it (its sources, drawn again in their places) blurred, a tint over that
 * so words on it stay clear, then the glass's wash and rim, then what it holds. On Android 12 and later the blur is the
 * GPU's, live as the page scrolls under it; drawn off the screen (a preview) it is made small and blurred by hand; on
 * older phones the tint alone, a little deeper. Its sources must not hold it.
 */
public class BlurBehind extends FrameLayout {
    private View[] sources = new View[0];
    private final float corner;
    private float blurPx;
    private int tint, ground = Glass.GROUND;
    private boolean blurOn = true, inBlur;
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private final int[] me = new int[2], at = new int[2];
    private Bitmap soft;
    private Canvas softCanvas;
    private final Paint softPaint = new Paint(Paint.FILTER_BITMAP_FLAG), tintPaint = new Paint();
    private Object node;
    private final GlassDrawable glass;
    private final ViewTreeObserver.OnScrollChangedListener scrolled = this::invalidate;
    private final ViewTreeObserver.OnGlobalLayoutListener laidOut = this::invalidate;

    public BlurBehind(Context c, float cornerDp, int kind) {
        super(c);
        setWillNotDraw(false);
        corner = Glass.dp(c, cornerDp);
        blurPx = Glass.dp(c, 20);
        tint = kind == GlassDrawable.BAR ? 0x9E121318 : 0x6612131A;
        glass = new GlassDrawable(c, cornerDp, kind);
        setBackground(glass);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius());
            }
        });
        setClipToOutline(true);
    }

    /** What lies under it, back to front, drawn blurred behind it. */
    public void setSources(View... s) {
        sources = s == null ? new View[0] : s;
        invalidate();
    }

    /** The tint over the blur (deeper where words must read over bright pictures). */
    public void setTint(int argb) {
        tint = argb;
        invalidate();
    }

    public void setBlurRadius(float px) {
        blurPx = Math.max(1f, px);
        invalidate();
    }

    /** Off: the tint alone (over a playing picture, whose surface can't be drawn again). */
    public void setBlurOn(boolean on) {
        blurOn = on;
        invalidate();
    }

    /** The colour under everything it blurs (the ground the page fades into). */
    public void setGround(int argb) {
        ground = argb;
    }

    public GlassDrawable glass() {
        return glass;
    }

    float radius() {
        return Math.min(corner, Math.min(getWidth(), getHeight()) / 2f);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnScrollChangedListener(scrolled);
        getViewTreeObserver().addOnGlobalLayoutListener(laidOut);
    }

    @Override protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnScrollChangedListener(scrolled);
        getViewTreeObserver().removeOnGlobalLayoutListener(laidOut);
        super.onDetachedFromWindow();
    }

    @Override public void draw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w > 0 && h > 0) {
            int save = cv.save();
            float r = radius();
            box.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(box, r, r, Path.Direction.CW);
            cv.clipPath(clip);
            boolean blurred = backdrop(cv, w, h);
            // Without a blur under it, a deeper tint keeps its words clear.
            tintPaint.setColor(blurred ? tint : deeper(tint));
            cv.drawRect(box, tintPaint);
            cv.restoreToCount(save);
        }
        super.draw(cv);
    }

    /** Pure: the tint when nothing is blurred under it, half again as deep. */
    static int deeper(int argb) {
        int a = argb >>> 24;
        int more = Math.min(0xE6, a + (0xFF - a) / 2);
        return (more << 24) | (argb & 0xFFFFFF);
    }

    /** The sources drawn blurred behind it; false when there was nothing to blur, or no way to. */
    private boolean backdrop(Canvas cv, int w, int h) {
        if (!blurOn || inBlur || sources.length == 0) return false;
        inBlur = true;
        try {
            if (cv.isHardwareAccelerated()) {
                if (Build.VERSION.SDK_INT >= 31) {
                    if (node == null) node = Gpu.make();
                    Gpu.draw((RenderNode) node, this, cv, w, h, blurPx, ground, sources, me, at);
                    return true;
                }
                return false;
            }
            soft(cv, w, h);
            return true;
        } catch (RuntimeException e) {
            // Something under it can't be drawn twice: the tint alone from now on.
            blurOn = false;
            return false;
        } finally {
            inBlur = false;
        }
    }

    /** Off the screen (a preview, or no GPU): the sources drawn a sixth of the size, blurred, and stretched back. */
    private void soft(Canvas cv, int w, int h) {
        float s = 1f / 6f;
        int bw = Math.max(2, Math.round(w * s)), bh = Math.max(2, Math.round(h * s));
        if (soft == null || soft.getWidth() != bw || soft.getHeight() != bh) {
            soft = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            softCanvas = new Canvas(soft);
        }
        soft.eraseColor(ground);
        getLocationInWindow(me);
        for (View v : sources) {
            if (v == null || v.getVisibility() != VISIBLE || v.getWidth() == 0) continue;
            v.getLocationInWindow(at);
            int k = softCanvas.save();
            softCanvas.scale(s, s);
            // As its parent would draw it: at its place, moved by its own scroll (a ScrollView's).
            softCanvas.translate(at[0] - me[0] - v.getScrollX(), at[1] - me[1] - v.getScrollY());
            v.draw(softCanvas);
            softCanvas.restoreToCount(k);
        }
        Soften.blur(soft, Math.max(1, Math.round(blurPx * s)));
        cv.drawBitmap(soft, null, box, softPaint);
    }

    /** Android 12's own blur, live: the sources recorded into a node of their own, blurred as it is drawn. */
    @RequiresApi(31)
    private static final class Gpu {
        static Object make() {
            return new RenderNode("glass");
        }

        static void draw(RenderNode n, View self, Canvas cv, int w, int h, float blur, int ground, View[] sources,
                         int[] me, int[] at) {
            n.setPosition(0, 0, w, h);
            RecordingCanvas rc = n.beginRecording(w, h);
            try {
                rc.drawColor(ground);
                self.getLocationInWindow(me);
                for (View v : sources) {
                    if (v == null || v.getVisibility() != VISIBLE || v.getWidth() == 0) continue;
                    v.getLocationInWindow(at);
                    int k = rc.save();
                    rc.translate(at[0] - me[0] - v.getScrollX(), at[1] - me[1] - v.getScrollY());
                    v.draw(rc);
                    rc.restoreToCount(k);
                }
            } finally {
                n.endRecording();
            }
            n.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
            cv.drawRenderNode(n);
        }
    }
}
