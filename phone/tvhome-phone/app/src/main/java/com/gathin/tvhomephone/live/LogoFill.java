package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * See-through's fill (LogoShape): the channel's own logo filled in from the picture just round it, its strokes alone,
 * drawn under the cover exactly where the logo is on the screen. It stays put while the cover takes its burn-in step
 * (BurnGuard) and is never dimmed with it: the fill is the picture, not ours. A new fill fades in over the one before
 * (the two added up on a layer of their own, so the edge never thickens as they cross).
 */
final class LogoFill extends View {
    private Bitmap now, was;
    private final RectF at = new RectF(), wasAt = new RectF(), r = new RectF(), both = new RectF();
    private long since;
    private long fadeMs = 250L;
    private final int[] loc = new int[2];
    private final Paint plain = new Paint(Paint.FILTER_BITMAP_FLAG), add = new Paint(Paint.FILTER_BITMAP_FLAG);

    LogoFill(Context c) {
        super(c);
        add.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** How long a new fill takes to fade in over the one before. */
    void fade(long ms) {
        fadeMs = Math.max(0L, ms);
    }

    /** A fresh fill (null: none) for the place l, t, r, b on the screen (pixels). */
    void show(Bitmap b, float l, float t, float rt, float bm) {
        if (b == null) {
            clear();
            return;
        }
        boolean fadeIn = now != null && at.left == l && at.top == t && at.right == rt && at.bottom == bm;
        was = fadeIn ? now : null;
        wasAt.set(at);
        now = b;
        at.set(l, t, rt, bm);
        since = SystemClock.uptimeMillis();
        invalidate();
    }

    /** No fill. */
    void clear() {
        if (now == null && was == null) return;
        now = null;
        was = null;
        invalidate();
    }

    boolean showing() {
        return now != null;
    }

    @Override protected void onDraw(Canvas cv) {
        Bitmap b = now;
        if (b == null || b.isRecycled()) return;
        // Where the logo is on the screen, from where this view is now.
        getLocationOnScreen(loc);
        float dx = -loc[0], dy = -loc[1];
        float k = fadeMs <= 0 ? 1f : Math.min(1f, (SystemClock.uptimeMillis() - since) / (float) fadeMs);
        Bitmap old = was;
        if (old != null && k < 1f && !old.isRecycled()) {
            // On a clear layer just their size: the one before at what is left of it, the new one added at its share.
            both.set(wasAt);
            both.union(at);
            both.offset(dx, dy);
            int saved = cv.saveLayer(both, null);
            r.set(wasAt);
            r.offset(dx, dy);
            plain.setAlpha(Math.round(255 * (1f - k)));
            cv.drawBitmap(old, null, r, plain);
            r.set(at);
            r.offset(dx, dy);
            add.setAlpha(Math.round(255 * k));
            cv.drawBitmap(b, null, r, add);
            cv.restoreToCount(saved);
            postInvalidateOnAnimation();
        } else {
            was = null;
            r.set(at);
            r.offset(dx, dy);
            plain.setAlpha(255);
            cv.drawBitmap(b, null, r, plain);
        }
    }
}
