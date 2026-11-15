package com.gathin.tvhome;

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
 * What slides out sideways from the scoreboard over the channel (BoardCover), in line with it and as high as it, as
 * broadcasters' captions come out of their score bug: the stats (BoardStats) and the alerts (BoardDrop). In the board's
 * own look (BoardBug's StripLook): its ground, square at its near end, which tucks under the board, and round at its far
 * end; beside the Glass pill, a pill of its own a little apart. Out to the board's right, or to its left when the board
 * is nearer the screen's right. Out from under the board far end first, and back in the same way. What stands out of it
 * (the scorer's picture, over) may rise above its top into the room kept there (head).
 */
abstract class BoardSide extends View {
    static final long SLIDE_MS = 420L;

    protected BoardBug.StripLook look = new BoardBug.StripLook();
    /** The board's height (its unit) in pixels: what the words and their room are sized from. */
    protected float unit = 48f;
    /** How far its near end tucks under the board, in pixels (beside a pill: none, it stands apart). */
    protected int tuck;
    /** Out to the board's left: its near end on its right. */
    protected boolean leftOf;
    /** 0 in under the board, 1 all the way out. */
    protected float out;
    /** Where its words go, from start to end, in pixels (set as it is drawn). */
    protected float start, end;
    /** The room over its top, in pixels, for what stands out of it (the strip is the rest of its height, at its foot). */
    protected int head;
    /** Room under its strip for the sponsor's strap (Stride), in pixels: 0 for none. */
    protected int strap;
    /** The light passing across the strap as it comes out (0 to 1; outside that, none). */
    private float sweep = -1f;
    private ValueAnimator sweeping;

    /** The sponsor's strap under it, px high with its gap (0: none). */
    void strap(int px) {
        if (strap == px) return;
        strap = px;
        invalidate();
    }

    /** Pure: the room the sponsor's strap takes under a strip h high, its gap included. */
    static int strapRoom(int h) {
        return com.gathin.broadcast.Stride.strapHeight(h) + com.gathin.broadcast.Stride.gap(h);
    }
    private ValueAnimator sliding;
    private Runnable after;
    private final Paint ground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF box = new RectF();
    /** Its corners, each {x, y}: top left, top right, bottom right, bottom left. */
    private final float[] corners = new float[8];

    BoardSide(Context c) {
        super(c);
        setVisibility(GONE);
    }

    /** Set for a board unit pixels high in this look, its near end tucked so far under it, on the board's left or right. */
    void place(BoardBug.StripLook s, float unit, int tuck, boolean leftOf) {
        place(s, unit, tuck, leftOf, 0);
    }

    /** The same, with head pixels of room over its top for what stands out of it. */
    void place(BoardBug.StripLook s, float unit, int tuck, boolean leftOf, int head) {
        look = s;
        this.unit = Math.max(16f, unit);
        this.tuck = s.apart ? 0 : Math.max(0, tuck);
        this.leftOf = leftOf;
        this.head = Math.max(0, head);
        if (isOut()) invalidate();
    }

    /** Pure: the room its ends keep (the far end's colour, the pads, the tucked end), for its words' width at height h. */
    static float ends(BoardBug.StripLook s, float unit, float h, int tuck, boolean stripe) {
        float pad = s.apart ? h * 0.42f : unit * 0.3f;
        return 2 * pad + (s.apart ? 0 : tuck) + (stripe ? stripeW(unit) : 0);
    }

    static float stripeW(float unit) {
        return Math.max(3f, unit * 0.09f);
    }

    /** Out from under the board (shown at once when it already is). */
    protected void slideOut() {
        // The sponsor's strap: a light across it once it is out.
        if (!isOut() && strap > 0) {
            if (sweeping != null) sweeping.cancel();
            ValueAnimator sv = ValueAnimator.ofFloat(0f, 1f);
            sv.setStartDelay(SLIDE_MS);
            sv.setDuration(900);
            sv.addUpdateListener(a -> {
                sweep = (float) a.getAnimatedValue();
                invalidate();
            });
            sweeping = sv;
            sv.start();
        }
        if (!isOut()) swish();
        goingIn = false;
        if (!isOut()) out = 0f;
        after = null;
        setVisibility(VISIBLE);
        to(1f, null);
    }

    /** On its way back in under the board. */
    private boolean goingIn;

    /**
     * The line-ups' tab swish, panned to where it is on the screen, for each of its moves: out from under the board, each
     * turn of its pages (the next stat, the alert's next page, the second eleven) and back in. Not with the switch off.
     */
    protected void swish() {
        if (!BoardCover.swishOn(getContext())) return;
        com.gathin.broadcast.Sfx.play(getContext(), com.gathin.broadcast.Sfx.SWISH, panNow(), 0.7f);
    }

    /** Where it is across the screen, as a sound's pan (-0.6 left to 0.6 right). */
    protected float panNow() {
        int[] at = new int[2];
        getLocationOnScreen(at);
        int sw = getResources().getDisplayMetrics().widthPixels;
        return sw > 0 ? Math.max(-0.6f, Math.min(0.6f, ((at[0] + getWidth() / 2f) / sw * 2f - 1f) * 0.6f)) : 0f;
    }

    /** A Scoreboard Lab sound for what it shows, quietly (Sfx.QUIET): only while it is up, not with the swish switched off. */
    protected void lab(String name) {
        if (!BoardLab.heard(isAttachedToWindow() && isShown(), getAlpha(), BoardCover.swishOn(getContext()))) return;
        com.gathin.broadcast.Sfx.play(getContext(), name, panNow(), com.gathin.broadcast.Sfx.QUIET);
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
        // A swish going back in too, once (not again for a fold asked for while it is already on its way).
        if (!goingIn) swish();
        goingIn = true;
        to(0f, () -> {
            goingIn = false;
            setVisibility(GONE);
            onGone();
            Runnable a = after;
            after = null;
            if (a != null) a.run();
        });
    }

    /** Away at once (the board's window going). */
    void gone() {
        if (sliding != null) sliding.cancel();
        goingIn = false;
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

    /** What it shows, between start and end, h high. */
    protected abstract void content(Canvas cv, int w, int h);

    /** What stands out of it, drawn unclipped by its ground (above its top, up to head): the strip h high from 0. */
    protected void over(Canvas cv, int w, int h) {
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight() - head - strap;
        if (w <= 0 || h <= 0 || out <= 0f) return;
        float seen = w * BoardBug.ease(out), tx = leftOf ? w - seen : seen - w;
        cv.save();
        // Out from under the board, its far end first.
        cv.translate(tx, head);
        float rad = look.apart ? h / 2f : Math.max(3f, h * look.round);
        float near = look.apart ? rad : 0f, left = leftOf ? rad : near, right = leftOf ? near : rad;
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
        cv.restore();
        // What stands out of it, only where it is out from under the board.
        cv.save();
        cv.clipRect(leftOf ? 0 : tuck, 0, leftOf ? w - tuck : w, h + head);
        cv.translate(tx, head);
        over(cv, w, h);
        cv.restore();
        // Under it, the sponsor's strap (Stride), sliding out with it, its end at the far end.
        if (strap > 0) {
            int g = com.gathin.broadcast.Stride.gap(h);
            cv.save();
            cv.clipRect(leftOf ? 0 : tuck, 0, leftOf ? w - tuck : w, getHeight());
            cv.translate(tx, head + h + g);
            com.gathin.broadcast.Stride.draw(cv, leftOf ? 0 : tuck, 0, w - tuck, strap - g, look.ground, !leftOf, sweep);
            cv.restore();
        }
    }
}
