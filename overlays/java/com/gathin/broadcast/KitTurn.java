package com.gathin.broadcast;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * A goal's shirt: the scorer's side's shirt comes out of the scoreboard (out of its own cell in the "3D kits" look, else
 * from the side's end of the board), grows into a dark disc ringed in gold, turns round to show the scorer's name and
 * number on its back, held a moment with his minute and name under it, then turns back and goes home (MS in all). The
 * frames: KitSheets' turn sheet (turnAngles) for his side's kit with his name and number. Put it over the whole screen
 * (the TV's window, the phone's player); it draws nothing when not playing and takes no touches.
 */
@SuppressLint("ViewConstructor")
public final class KitTurn extends View {
    /** How long it plays (ms), and when the shirt is back in its cell (the board shows its own again then). */
    public static final long MS = 4300, HOME_MS = 4100;

    private KitSheets.Sheet sheet;
    private float fromX, fromY, fromSize, startAngle;
    private boolean down = true;
    private String caption = "";
    private long at = -1;
    private Runnable after;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public KitTurn(Context c) {
        super(c);
        setWillNotDraw(false);
        setFocusable(false);
        setClickable(false);
        text.setColor(Color.WHITE);
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        return false;
    }

    /**
     * Plays: the shirt from (x, y), size high there (this view's pixels), turned startAngle (its rest in the board), into
     * a disc below the board (down) or above it; caption under the disc ("41'  JAY HOLLIS"). after: once it is over.
     */
    public void play(KitSheets.Sheet s, float x, float y, float size, float startAngle, boolean down, String caption, Runnable after) {
        this.sheet = s;
        this.fromX = x;
        this.fromY = y;
        this.fromSize = size;
        this.startAngle = startAngle;
        this.down = down;
        this.caption = caption == null ? "" : caption;
        this.after = after;
        at = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    public boolean playing() {
        return at >= 0;
    }

    /** Stopped at once (the board going, another game). */
    public void stop() {
        boolean was = at >= 0;
        at = -1;
        invalidate();
        Runnable a = after;
        after = null;
        if (was && a != null) a.run();
    }

    static float clamp(float x) {
        return Math.max(0f, Math.min(1f, x));
    }

    static float prog(float t, float a, float b) {
        return clamp((t - a) / (b - a));
    }

    static float eOut(float k) {
        return 1 - (float) Math.pow(1 - k, 3);
    }

    static float eInOut(float k) {
        return k < 0.5f ? 4 * k * k * k : 1 - (float) Math.pow(-2 * k + 2, 3) / 2;
    }

    /** Pure: how far out of its cell the shirt is at t seconds in (0 home, 1 all the way out). */
    public static float up(float t) {
        return eOut(prog(t, 0.15f, 0.75f)) * (1 - eInOut(prog(t, 3.4f, 4.1f)));
    }

    /** Pure: its angle at t seconds in, from its rest (start) round to its back and home again. */
    public static float angle(float t, float start) {
        float turn = (float) (eOut(prog(t, 0.2f, 1.1f)) * Math.PI - eInOut(prog(t, 3.4f, 4.1f)) * Math.PI);
        float k = clamp(turn / (float) Math.PI);
        float back = (float) Math.PI + 0.25f * (float) Math.sin(t * 1.2f);
        return start + (back - start) * k;
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (at < 0) return;
        float t = (SystemClock.uptimeMillis() - at) / 1000f;
        if (t * 1000 >= MS) {
            stop();
            return;
        }
        int vw = getWidth(), vh = getHeight();
        float u = Math.max(0.3f, vh / 1080f);
        float up = up(t);
        // the disc's middle: out to the right of where it came from, below the board (or above it), kept on the screen
        float R = 180 * u;
        float cx = Math.max(R + 12 * u, Math.min(vw - R - 12 * u, fromX + 120 * u * up));
        float cyFull = down ? fromY + 300 * u : fromY - 300 * u;
        cyFull = Math.max(R + 12 * u, Math.min(vh - R - 120 * u, cyFull));
        float cy = fromY + (cyFull - fromY) * up;
        if (up > 0.02f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xC7080C30);
            p.setAlpha(Math.round(0xC7 * up));
            cv.drawCircle(cx, cy, R * up, p);
            // the gold ring drawing round, and a burst of light as the shirt lands
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6 * u);
            p.setColor(0xFFF5C518);
            p.setAlpha(Math.round(255 * up));
            r.set(cx - R * up, cy - R * up, cx + R * up, cy + R * up);
            cv.drawArc(r, -90, 360 * eOut(prog(t, 0.3f, 1.2f)), false, p);
            float burst = prog(t, 0.6f, 1.3f);
            if (burst > 0 && burst < 1) {
                p.setStrokeWidth(3 * u);
                p.setColor(0xFFFFDC78);
                p.setAlpha(Math.round(204 * (1 - burst)));
                cv.drawCircle(cx, cy, R + burst * 120 * u, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setAlpha(255);
        }
        // the shirt: from its size in the cell to 2.3 times the disc's radius, turning round to its back and home again
        float size = fromSize + (2.3f * R - fromSize) * up;
        if (sheet != null && size > 2) {
            int i = sheet.nearest(angle(t, startAngle));
            r.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
            sheet.draw(cv, i, r, p);
        }
        // the scorer's minute and name under the disc
        float cap = eOut(prog(t, 0.9f, 1.3f)) * (1 - eInOut(prog(t, 3.4f, 4.1f)));
        if (cap > 0.01f && !caption.isEmpty()) {
            float bw = 380 * u, bh = 70 * u, top = cy + 196 * u;
            text.setTextSize(32 * u);
            bw = Math.max(bw, text.measureText(caption) + 60 * u);
            p.setColor(0xFF10185A);
            p.setAlpha(Math.round(255 * cap));
            r.set(cx - bw / 2, top, cx + bw / 2, top + bh);
            cv.drawRoundRect(r, 8 * u, 8 * u, p);
            p.setColor(0xFFF5C518);
            p.setAlpha(Math.round(255 * cap));
            cv.drawRect(cx - bw / 2, top, cx - bw / 2 + 10 * u, top + bh, p);
            p.setAlpha(255);
            text.setAlpha(Math.round(255 * cap));
            cv.drawText(caption, cx + 6 * u, top + bh / 2 + 11 * u, text);
        }
        postInvalidateOnAnimation();
    }
}
