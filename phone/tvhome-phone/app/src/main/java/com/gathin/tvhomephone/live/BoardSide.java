package com.gathin.tvhomephone.live;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * What slides out sideways from the scoreboard over the picture (ScoreBoard), in line with it and as high as it, as
 * broadcasters' captions come out of their score bug: the stats (BoardStats) and the alerts (BoardDrop). In the board's
 * own look (OverlayBug.StripLook): its ground, square at its near end, which tucks under the board, and round at its
 * far end; beside a pill board (Glass, Minimal), a pill of its own a little apart. Out to the board's right, or to its
 * left when the board is nearer the screen's right. Out from under the board far end first, and back in the same way.
 * It may stand in a box taller than its strip (head: room above it, for a goal's scorer standing up out of it).
 */
abstract class BoardSide extends View {
    static final long SLIDE_MS = 420L;

    protected OverlayBug.StripLook look = new OverlayBug.StripLook();
    /** The board's height in pixels: what the words and their room are sized from. */
    protected float unit;
    /** How far its near end tucks under the board, in pixels (beside a pill: none, it stands apart). */
    protected int tuck;
    /** Room above its strip in its own box, in pixels (its strip is the box's foot, as high as the board). */
    protected int head;
    /** Out to the board's left: its near end on its right. */
    protected boolean leftOf;
    /** On its own, no board up to come out of: round at both ends, nothing tucked. */
    protected boolean alone;
    /** 0 in under the board, 1 all the way out. */
    protected float out;
    /** Where its words go, from start to end, in pixels (set as it is drawn). */
    protected float start, end;
    protected final float dp;
    private ValueAnimator sliding;
    private Runnable after;
    private final Paint ground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF box = new RectF();
    /** Its corners, each {x, y}: top left, top right, bottom right, bottom left. */
    private final float[] corners = new float[8];

    BoardSide(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        unit = 30 * dp;
        setVisibility(GONE);
    }

    /**
     * Set for a board unit pixels high in this look, its near end tucked so far under it, on the board's left or right,
     * with head pixels of room above its strip.
     */
    void place(OverlayBug.StripLook s, float unit, int tuck, boolean leftOf, int head) {
        look = s;
        this.unit = Math.max(12 * dp, unit);
        this.tuck = s.apart ? 0 : Math.max(0, tuck);
        this.leftOf = leftOf;
        this.head = Math.max(0, head);
        if (isOut()) invalidate();
    }

    boolean leftOf() {
        return leftOf;
    }

    /** On its own (no board up): round at both ends, nothing tucked under anything. */
    void alone(boolean on) {
        alone = on;
        if (on) tuck = 0;
    }

    /** Pure: the room its ends keep (the pads, the tucked end, the far end's colour), for its words' width at strip height h. */
    static float ends(OverlayBug.StripLook s, float unit, float h, int tuck, boolean stripe) {
        float pad = s.apart ? h * 0.42f : unit * 0.3f;
        return 2 * pad + (s.apart ? 0 : tuck) + (stripe ? stripeW(unit) : 0);
    }

    static float stripeW(float unit) {
        return Math.max(3f, unit * 0.09f);
    }

    /** Pure: fast out, settling in (a cubic). */
    static float ease(float t) {
        float u = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - u * u * u;
    }

    /** Out from under the board (shown at once when it already is). */
    protected void slideOut() {
        if (!isOut()) out = 0f;
        after = null;
        setVisibility(VISIBLE);
        to(1f, null);
    }

    /** Back in under the board; then() once it is away (at once if it is): waiting ones run in turn. */
    void fold(Runnable then) {
        if (!isOut()) {
            onFolding();
            if (then != null) then.run();
            return;
        }
        Runnable was = after;
        after = then == null ? was : was == null ? then : () -> {
            was.run();
            then.run();
        };
        onFolding();
        to(0f, () -> {
            setVisibility(GONE);
            onGone();
            Runnable a = after;
            after = null;
            if (a != null) a.run();
        });
    }

    /** Away at once (the board going, the small window). */
    void gone() {
        if (sliding != null) sliding.cancel();
        after = null;
        out = 0f;
        onFolding();
        onGone();
        setVisibility(GONE);
    }

    /** Whether it is out (or on its way out or in). */
    boolean isOut() {
        return getVisibility() == VISIBLE;
    }

    /** Previews: this far out at once. */
    protected void outNow(float f) {
        if (sliding != null) sliding.cancel();
        out = Math.max(0f, Math.min(1f, f));
        setVisibility(VISIBLE);
        invalidate();
    }

    /** Folding back in, or gone: its own timers stopped. */
    protected void onFolding() {
    }

    /** Gone: what it showed let go. */
    protected void onGone() {
    }

    private void to(float target, Runnable then) {
        if (sliding != null) sliding.cancel();
        ValueAnimator va = ValueAnimator.ofFloat(out, target);
        va.setDuration(Math.max(1, Math.round(SLIDE_MS * Math.abs(target - out))));
        va.addUpdateListener(a -> {
            out = (float) a.getAnimatedValue();
            invalidate();
        });
        va.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator a) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator a) {
                if (!cancelled && then != null) then.run();
            }
        });
        sliding = va;
        va.start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sliding != null) sliding.cancel();
        onFolding();
    }

    /** Its far end's colour (0: none), and the lower half's when it is two clubs' (0: the same). */
    protected int stripe() {
        return 0;
    }

    protected int stripeLow() {
        return 0;
    }

    /** What it shows on its strip, between start and end, h high (the canvas at the strip's top). */
    protected abstract void content(Canvas cv, int w, int h);

    /** What stands up out of its strip into the room above it (the canvas at the strip's top, nothing over the board). */
    protected void over(Canvas cv, int w, int h) {
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight() - head;
        if (w <= 0 || h <= 0 || out <= 0f) return;
        float seen = w * ease(out);
        cv.save();
        // Never over the board: its tucked end (and the room above it) stay out of sight.
        if (leftOf) cv.clipRect(0, 0, w - tuck, getHeight());
        else cv.clipRect(tuck, 0, w, getHeight());
        // Out from under the board, its far end first.
        cv.translate(leftOf ? w - seen : seen - w, head);
        float rad = look.apart ? h / 2f : Math.min(8 * dp, Math.max(3 * dp, h * look.round));
        float near = look.apart || alone ? rad : 0f, left = leftOf ? rad : near, right = leftOf ? near : rad;
        corners[0] = corners[1] = corners[6] = corners[7] = left;
        corners[2] = corners[3] = corners[4] = corners[5] = right;
        box.set(0, 0, w, h);
        shape.reset();
        shape.addRoundRect(box, corners, Path.Direction.CW);
        ground.setColor(look.ground);
        cv.drawPath(shape, ground);
        cv.save();
        cv.clipPath(shape);
        int top = stripe(), low = stripeLow();
        float sw = top != 0 ? stripeW(unit) : 0f;
        if (top != 0) {
            float x = leftOf ? 0 : w - sw;
            ground.setColor(top);
            cv.drawRect(x, 0, x + sw, low != 0 ? h / 2f : h, ground);
            if (low != 0) {
                ground.setColor(low);
                cv.drawRect(x, h / 2f, x + sw, h, ground);
            }
        }
        float pad = look.apart ? h * 0.42f : unit * 0.3f;
        start = leftOf ? sw + pad : tuck + pad;
        end = leftOf ? w - tuck - pad : w - sw - pad;
        content(cv, w, h);
        cv.restore();
        over(cv, w, h);
        cv.restore();
    }
}
