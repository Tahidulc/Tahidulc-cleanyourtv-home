package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;

/**
 * What sits over the guide on an upright screen (the picture, Your channels, the categories and the programme's details),
 * folding away so the guide gets the whole height. A swipe up folds it and a swipe down brings it back, following the
 * finger and settling open or shut when let go: the way a quick flick went, or else whichever end is nearer. Scrolling
 * the guide's rows up folds it first; pulling them down at their top unfolds it. Sideways drags are left to the guide
 * and the categories, and the picture keeps its own swipes. The header and the body scroll together as one column (the
 * body as tall as the whole, as an app bar's scrolling view is), so nothing is laid out again while it moves.
 * <p>
 * Over the header there can be a pin (the guide's picture: Pin), which scrolls away with it as before, or, while it is
 * kept (a channel playing in it), stays at the top: what is under it folds away beneath it, then it shrinks, following
 * the finger, to its compact height and the guide comes up under it; back down, it grows again before the rest comes
 * back. It only moves and scales (Shrinking), so that too is drawing alone.
 */
@SuppressLint("ViewConstructor")
final class Fold extends ViewGroup implements NestedScrollingParent3 {
    /** How much of the header shows, as it moves (for anything centred in the body). */
    interface Listener {
        void onFold(int shown);
    }

    /** A pin that shrinks as it is kept (Pin). */
    interface Shrinking {
        /** Its height shrunk as far as it goes, for the width it was last measured at (px). */
        int compactHeight();

        /** As it shows now: how far it has shrunk (0 as laid out, 1 compact) and how much of its height shows (px). */
        void shrink(float by, int shown);
    }

    private final View pin, header, body;
    private final Shrinking shrinking;
    private final NestedScrollingParentHelper nested = new NestedScrollingParentHelper(this);
    private final OverScroller scroller;
    private final int slop, minFling, maxFling;
    private final int[] spare = new int[2];
    private final RectF box = new RectF();
    private View picture;
    private Listener listener;
    private VelocityTracker tracker;
    /** Where it settles (or has settled): folded away or open. */
    private boolean shut;
    private boolean canDrag, dragging, rebase, flinging;
    private float downX, downY, lastY, flingVelocity;
    private int range, lastShown = -1;
    /** The pin is wanted kept in sight (a channel plays in it). */
    private boolean pinned;
    /** As last laid out: the pin's height and the header's, and how much of the pin stays (0: none, it scrolls away). */
    private int pinH, headH, keep;

    Fold(Context c, View header, View body) {
        this(c, null, header, body);
    }

    /** pin: over the header (null for none); kept in sight while setPinned, shrinking as it implements Shrinking. */
    Fold(Context c, View pin, View header, View body) {
        super(c);
        this.pin = pin;
        this.shrinking = pin instanceof Shrinking ? (Shrinking) pin : null;
        this.header = header;
        this.body = body;
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
        scroller = new OverScroller(c);
        addView(header);
        addView(body);
        // Last: drawn over the header folding away under it, and the first to be touched.
        if (pin != null) addView(pin);
    }

    void setListener(Listener l) { listener = l; }

    /** The picture: a drag that starts on it is the picture's own (up for full screen, sideways for channels). */
    void setPicture(View v) { picture = v; }

    /** Folded away (or settling that way). */
    boolean isShut() { return shut; }

    /** How much more there is to fold now (the body runs on under the screen's foot by this much). */
    int shown() { return Math.max(0, range - getScrollY()); }

    /**
     * Keeps the pin in sight (true: a channel plays in it) or lets it scroll away with the header (false: nothing plays,
     * so nothing changes from before).
     */
    void setPinned(boolean on) {
        if (pinned == on) return;
        pinned = on;
        requestLayout();
    }

    /** The pin is kept in sight (or will be, once laid out). */
    boolean keeping() {
        return pinned && pin != null && pin.getVisibility() != GONE;
    }

    /** Folds it away or opens it, gliding there when animate. */
    void setShut(boolean on, boolean animate) {
        if (range <= 0 || !animate || !isLaidOut()) {
            shut = on;
            scroller.forceFinished(true);
            if (range > 0) scrollTo(0, on ? range : 0);
            return;
        }
        settle(on);
    }

    // ---- the pure parts --------------------------------------------------------------------------------------

    /**
     * Pure: where a let-go header settles. A flick (velocity at least minFling, positive folding it) goes its own way;
     * otherwise whichever end is nearer, open at exactly half.
     */
    static boolean settlesShut(int scrolled, int range, float velocity, float minFling) {
        if (range <= 0) return false;
        if (Math.abs(velocity) >= minFling && velocity != 0) return velocity > 0;
        return scrolled * 2 > range;
    }

    /** Pure: of a move of dy (positive folding it), the part the header takes, from scrolled of range. */
    static int take(int dy, int scrolled, int range) {
        return clamp(scrolled + dy, 0, Math.max(0, range)) - scrolled;
    }

    /**
     * Pure: how far it is folded once the header's height went from was to now. Shut stays shut and open stays open (with
     * nothing in it before: as last chosen); part way, the guide under it stays where it was (as much of it shows).
     */
    static int refold(int scrolled, int was, int now, boolean shut) {
        if (now <= 0) return 0;
        if (was <= 0) return shut ? now : 0;
        if (scrolled >= was) return now;
        if (scrolled <= 0) return 0;
        return clamp(now - (was - scrolled), 0, now);
    }

    /** Pure: how long settling takes (150 to 300 ms, as Material's app bar), from how far it has to go. */
    static int settleMs(int distance, int range) {
        if (range <= 0) return 0;
        return Math.round(150 * (1 + Math.min(1f, Math.abs(distance) / (float) range)));
    }

    /** Pure: how much of a pin pinH tall stays in sight (its compact height, never more than all of it); 0 when not kept. */
    static int keep(boolean kept, int pinH, int compact) {
        if (!kept || pinH <= 0 || compact <= 0) return 0;
        return Math.min(compact, pinH);
    }

    /** Pure: how far it folds: the pin's height and the header's, less what of the pin stays. */
    static int range(int pinH, int headH, int keep) {
        return Math.max(0, pinH + headH - keep);
    }

    /**
     * Pure: how much of a kept pin shows, scrolled by scrolled: all of it while the header under it folds away, then less
     * as the guide comes up under it, down to keep.
     */
    static int pinShown(int scrolled, int pinH, int headH, int keep) {
        if (keep <= 0) return pinH;
        return clamp(pinH + headH - scrolled, Math.min(keep, pinH), pinH);
    }

    /** Pure: how far a pin showing shown of pinH has shrunk toward keep: 0 (all of it) to 1 (compact). */
    static float shrunk(int shown, int pinH, int keep) {
        if (keep <= 0 || pinH <= keep) return 0f;
        return Math.max(0f, Math.min(1f, (pinH - shown) / (float) (pinH - keep)));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- measuring and laying out ----------------------------------------------------------------------------

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        int ws = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int k = 0;
        if (pin != null && pin.getVisibility() != GONE) {
            pin.measure(ws, free);
            k = keep(pinned, pin.getMeasuredHeight(), shrinking == null ? pin.getMeasuredHeight() : shrinking.compactHeight());
        }
        keep = k;
        header.measure(ws, free);
        // Kept, the pin's compact height stays over the guide: the body ends at the screen's foot when folded.
        body.measure(ws, MeasureSpec.makeMeasureSpec(Math.max(0, h - k), MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, ph = pin == null || pin.getVisibility() == GONE ? 0 : pin.getMeasuredHeight();
        int hh = header.getVisibility() == GONE ? 0 : header.getMeasuredHeight();
        if (ph > 0) pin.layout(0, 0, w, ph);
        header.layout(0, ph, w, ph + hh);
        body.layout(0, ph + hh, w, ph + hh + body.getMeasuredHeight());
        pinH = ph;
        headH = hh;
        int now = range(ph, hh, keep);
        if (now != range) {
            int was = range;
            range = now;
            scrollTo(0, refold(getScrollY(), was, now, shut));
            // Settling: on to the same end, as it now is.
            if (!scroller.isFinished()) settle(shut);
        }
        place();
        tell();
    }

    @Override protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        place();
        tell();
    }

    /** How much of the pin and the header together shows at the top. */
    private int visible() {
        return Math.max(0, pinH + headH - getScrollY());
    }

    /** The pin where it shows: kept at the top as the rest scrolls (only moved and scaled), or scrolling with it. */
    private void place() {
        if (pin == null || pinH <= 0) return;
        int s = getScrollY();
        pin.setTranslationY(keep > 0 ? s : 0);
        int ps = pinShown(s, pinH, headH, keep);
        if (shrinking != null) shrinking.shrink(shrunk(ps, pinH, keep), ps);
    }

    /** Kept, the header folds away beneath the pin: drawn only under what shows of the pin. */
    @Override protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child != header || keep <= 0 || pinH <= 0) return super.drawChild(canvas, child, drawingTime);
        int s = getScrollY();
        canvas.save();
        canvas.clipRect(0, s + pinShown(s, pinH, headH, keep), getWidth(), s + getHeight());
        boolean more = super.drawChild(canvas, child, drawingTime);
        canvas.restore();
        return more;
    }

    private void tell() {
        int s = shown();
        if (s == lastShown) return;
        lastShown = s;
        if (listener != null) listener.onFold(s);
    }

    // ---- settling ----------------------------------------------------------------------------------------------

    private void settle(boolean on) {
        shut = on;
        int from = getScrollY(), to = on ? range : 0;
        scroller.forceFinished(true);
        if (from == to) return;
        scroller.startScroll(0, from, 0, to - from, settleMs(to - from, range));
        postInvalidateOnAnimation();
    }

    /** Let go part way, with no fling to follow: the nearer end. */
    private void settleNearer() {
        int y = getScrollY();
        if (y > 0 && y < range) settle(settlesShut(y, range, 0, minFling));
        else if (range > 0) shut = y >= range;
    }

    @Override public void computeScroll() {
        if (scroller.isFinished()) return;
        if (scroller.computeScrollOffset()) {
            scrollTo(0, clamp(scroller.getCurrY(), 0, range));
            postInvalidateOnAnimation();
        }
        if (scroller.isFinished()) scrollTo(0, shut ? range : 0);
    }

    /** Moves it by dy (positive folding it); the part taken. */
    private int moveBy(int dy) {
        int d = take(dy, getScrollY(), range);
        if (d != 0) scrollTo(0, getScrollY() + d);
        return d;
    }

    // ---- the guide's (and the list's) scrolling: nested ------------------------------------------------------

    @Override public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0 && range > 0;
    }

    @Override public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        nested.onNestedScrollAccepted(child, target, axes, type);
        if (type == ViewCompat.TYPE_TOUCH) flingVelocity = 0;
        else flinging = true;
    }

    @Override public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        // Up: the header folds first, then the rows scroll.
        if (dy > 0 && mayMove(type)) consumed[1] += moveBy(dy);
    }

    @Override public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed,
                                         int dyUnconsumed, int type, @NonNull int[] consumed) {
        // Down past the rows' top: the header comes back.
        if (dyUnconsumed < 0 && mayMove(type)) consumed[1] += moveBy(dyUnconsumed);
    }

    @Override public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed,
                                         int dyUnconsumed, int type) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, spare);
    }

    /** A finger takes it over from a settle under way; a fling's scrolling leaves the settle alone. */
    private boolean mayMove(int type) {
        if (scroller.isFinished()) return true;
        if (type != ViewCompat.TYPE_TOUCH) return false;
        scroller.forceFinished(true);
        return true;
    }

    @Override public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        flingVelocity = velocityY;
        int y = getScrollY();
        if (y <= 0 || y >= range) return false;
        // Part way: the flick's way, and the rows stay put.
        settle(velocityY > 0);
        return true;
    }

    @Override public boolean onNestedFling(@NonNull View target, float velocityX, float velocityY, boolean consumed) {
        return false;
    }

    @Override public void onStopNestedScroll(@NonNull View target, int type) {
        nested.onStopNestedScroll(target, type);
        if (type == ViewCompat.TYPE_TOUCH) {
            // A fling that carries on (or a settle) decides where it rests.
            if (!flinging && scroller.isFinished()) settleNearer();
        } else {
            flinging = false;
            int y = getScrollY();
            if (scroller.isFinished() && y > 0 && y < range) settle(settlesShut(y, range, flingVelocity, minFling));
            else if (scroller.isFinished() && range > 0) shut = y >= range;
        }
    }

    // The same for the framework's own nested scrolling (the Sport choice's ScrollView), always a touch.

    @Override public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes) {
        return onStartNestedScroll(child, target, axes, ViewCompat.TYPE_TOUCH);
    }

    @Override public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes) {
        onNestedScrollAccepted(child, target, axes, ViewCompat.TYPE_TOUCH);
    }

    @Override public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed) {
        onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH);
    }

    @Override public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed,
                                         int dyUnconsumed) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, ViewCompat.TYPE_TOUCH, spare);
    }

    @Override public void onStopNestedScroll(@NonNull View target) {
        onStopNestedScroll(target, ViewCompat.TYPE_TOUCH);
    }

    @Override public int getNestedScrollAxes() {
        return nested.getNestedScrollAxes();
    }

    // ---- dragging the header itself ----------------------------------------------------------------------------

    /** Whether a touch at x, y (this view's own) is on the header (or the pin) as it shows, and not on the picture. */
    private boolean onHeader(float x, float y) {
        if (range <= 0 || y >= visible()) return false;
        return !onPicture(x, y);
    }

    /** Whether x, y (this view's own) is on the picture as it is drawn (moved and scaled as it may be). */
    private boolean onPicture(float x, float y) {
        if (picture == null || !picture.isShown()) return false;
        box.set(0, 0, picture.getWidth(), picture.getHeight());
        View v = picture;
        while (v != this) {
            Matrix m = v.getMatrix();
            if (!m.isIdentity()) m.mapRect(box);
            box.offset(v.getLeft(), v.getTop());
            ViewParent p = v.getParent();
            if (!(p instanceof View)) return false;
            v = (View) p;
            box.offset(-v.getScrollX(), -v.getScrollY());
        }
        return box.contains(x, y);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = lastY = e.getY();
                dragging = false;
                canDrag = onHeader(downX, downY);
                if (!canDrag) return false;
                if (tracker == null) tracker = VelocityTracker.obtain();
                else tracker.clear();
                tracker.addMovement(e);
                if (!scroller.isFinished()) {
                    // Caught while it settles: held where it is, and dragged from there.
                    scroller.forceFinished(true);
                    startDrag(e);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!canDrag) return false;
                if (tracker != null) tracker.addMovement(e);
                return dragging || startsDrag(e);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                canDrag = false;
                dragging = false;
                return false;
            default:
                return dragging;
        }
    }

    /** A drag that has gone up or down further than sideways: the header's from here on. */
    private boolean startsDrag(MotionEvent e) {
        float dx = e.getX() - downX, dy = e.getY() - downY;
        if (Math.abs(dx) > slop && Math.abs(dx) >= Math.abs(dy)) {
            // Sideways: the categories' (or Your channels') own.
            canDrag = false;
            return false;
        }
        if (Math.abs(dy) <= slop) return false;
        startDrag(e);
        return true;
    }

    private void startDrag(MotionEvent e) {
        dragging = true;
        rebase = false;
        lastY = e.getY();
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Nothing under the finger took it (a gap in the header): the header's to drag.
                return canDrag;
            case MotionEvent.ACTION_MOVE:
                if (!canDrag) return false;
                if (tracker != null) tracker.addMovement(e);
                if (!dragging && !startsDrag(e)) return true;
                if (rebase) {
                    rebase = false;
                    lastY = e.getY();
                }
                // Whole pixels moved; what is left over waits for the next move.
                int dy = Math.round(lastY - e.getY());
                if (dy != 0) {
                    moveBy(dy);
                    lastY -= dy;
                }
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                // Another finger: carry on from wherever the first is next.
                rebase = true;
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging && tracker != null) {
                    tracker.addMovement(e);
                    tracker.computeCurrentVelocity(1000, maxFling);
                    // The finger going up folds it: the opposite sign of its own speed.
                    settle(settlesShut(getScrollY(), range, -tracker.getYVelocity(), minFling));
                }
                endDrag();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) settleNearer();
                endDrag();
                return true;
            default:
                return true;
        }
    }

    private void endDrag() {
        dragging = false;
        canDrag = false;
    }

    @Override protected void onDetachedFromWindow() {
        scroller.forceFinished(true);
        if (tracker != null) {
            tracker.recycle();
            tracker = null;
        }
        super.onDetachedFromWindow();
    }

    // ---- TalkBack and the keyboard --------------------------------------------------------------------------

    /** Something in the folded part asked to be seen (TalkBack's show on screen, a focus moving there): it opens. */
    @Override public boolean requestChildRectangleOnScreen(View child, Rect r, boolean immediate) {
        int s = getScrollY();
        // The header's own top hidden: scrolled up past it, or beneath the pin kept over it; a pin not kept, scrolled away.
        boolean hidden = child == header ? r.top + pinH < s + (keep > 0 ? pinShown(s, pinH, headH, keep) : 0)
                : child == pin && keep <= 0 && r.top < s;
        if (s > 0 && hidden) {
            setShut(false, !immediate);
            return true;
        }
        return super.requestChildRectangleOnScreen(child, r, immediate);
    }

    /** Scrolling forward folds it, backward opens it (TalkBack's scroll gestures). */
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (range <= 0) return;
        info.setScrollable(true);
        if (getScrollY() < range) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        if (getScrollY() > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if (range > 0 && action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD && getScrollY() < range) {
            setShut(true, true);
            return true;
        }
        if (range > 0 && action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD && getScrollY() > 0) {
            setShut(false, true);
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }
}
