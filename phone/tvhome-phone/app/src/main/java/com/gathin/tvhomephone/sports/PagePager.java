package com.gathin.tvhomephone.sports;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

/**
 * Pages side by side, one on the screen at a time (a player's, a driver's): a swipe follows the finger and lets go onto
 * the page before or after, a fling carrying it over; past the first or the last it gives a little and comes back.
 * Three page views are kept (the one shown and one either side), bound to their pages only when the pager comes to rest;
 * a frame only moves them. A page's own vertical scrolling still works: only a sideways drag is the pager's.
 */
final class PagePager extends ViewGroup {
    interface Pages {
        int count();

        /** A new, empty page view (three are made). */
        View make();

        /** Shows page index in the view (when the pager rests, or the pages change). */
        void bind(View page, int index);

        /** Every move: where the pager is (2.5: half way from the third page to the fourth). */
        void moved(float pos);

        /** The pager came to rest on a page. */
        void settled(int index);
    }

    private Pages pages;
    private final View[] views = new View[3];
    private final int[] bound = {-1, -1, -1};
    private float pos, raw, from, to;
    private int at, target, count, dragStart;
    private final ValueAnimator settle = ValueAnimator.ofFloat(0f, 1f);
    private VelocityTracker velocity;
    private float downX, downY, lastX;
    private boolean dragging;
    private final int slop, minFling, maxFling;

    PagePager(Context c) {
        super(c);
        setFocusable(true);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
        settle.setInterpolator(new DecelerateInterpolator(1.6f));
        settle.addUpdateListener(a -> {
            pos = from + (to - from) * a.getAnimatedFraction();
            raw = pos;
            arrange();
        });
        settle.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationStart(Animator a) {
                cancelled = false;
            }

            @Override public void onAnimationCancel(Animator a) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator a) {
                if (cancelled) return;
                pos = raw = to;
                arrive(target);
            }
        });
    }

    /** The pages, with start shown. */
    void setPages(Pages p, int start) {
        settle.cancel();
        pages = p;
        count = p.count();
        for (int i = 0; i < 3; i++) {
            if (views[i] == null) {
                views[i] = p.make();
                addView(views[i]);
            }
            bound[i] = -1;
        }
        at = Math.max(0, Math.min(count - 1, start));
        pos = raw = at;
        bindAround(at);
        arrange();
        pages.settled(at);
        requestLayout();
    }

    /** The page shown (the one nearest, while moving). */
    int current() {
        return CarouselMath.nearest(pos, count);
    }

    int count() {
        return count;
    }

    /** The view showing page index, or null when it is not bound. */
    View viewFor(int index) {
        for (int i = 0; i < 3; i++) if (bound[i] == index) return views[i];
        return null;
    }

    /** Binds the pages either side of index (and it) to the three views, keeping a view on a page it already shows. */
    private void bindAround(int index) {
        boolean[] keep = new boolean[3];
        for (int i = 0; i < 3; i++) keep[i] = bound[i] >= 0 && Math.abs(bound[i] - index) <= 1 && bound[i] < count;
        for (int want = index - 1; want <= index + 1; want++) {
            if (want < 0 || want >= count) continue;
            boolean has = false;
            for (int i = 0; i < 3; i++) if (keep[i] && bound[i] == want) has = true;
            if (has) continue;
            for (int i = 0; i < 3; i++) {
                if (keep[i]) continue;
                bound[i] = want;
                keep[i] = true;
                pages.bind(views[i], want);
                break;
            }
        }
        for (int i = 0; i < 3; i++) if (!keep[i]) bound[i] = -1;
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        for (View v : views) {
            if (v != null) v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (View v : views) if (v != null) v.layout(0, 0, r - l, b - t);
        arrange();
    }

    /** Each page's place for the position now: no allocation, only moves. */
    private void arrange() {
        float w = getWidth();
        for (int i = 0; i < 3; i++) {
            View v = views[i];
            if (v == null) continue;
            float d = bound[i] - pos;
            boolean shows = bound[i] >= 0 && Math.abs(d) < 1f;
            if (!shows) {
                if (v.getVisibility() != INVISIBLE) v.setVisibility(INVISIBLE);
                continue;
            }
            if (v.getVisibility() != VISIBLE) v.setVisibility(VISIBLE);
            v.setTranslationX(d * w);
        }
        if (pages != null) pages.moved(pos);
    }

    /** Goes to page i, sliding (one page at a time is bound either side, so a jump further is at once). */
    void goTo(int i) {
        if (count == 0) return;
        int t = Math.max(0, Math.min(count - 1, i));
        settle.cancel();
        if (Math.abs(t - at) > 1) {
            pos = raw = t;
            bindAround(t);
            arrange();
            arrive(t);
            return;
        }
        from = pos;
        to = t;
        target = t;
        long ms = CarouselMath.settleMs(to - from) + 60;
        if (Math.abs(to - from) < 0.001f) {
            pos = raw = t;
            arrange();
            arrive(t);
            return;
        }
        settle.setDuration(ms);
        settle.start();
    }

    private void arrive(int i) {
        boolean moved = i != at;
        at = i;
        bindAround(i);
        arrange();
        if (moved && pages != null) pages.settled(i);
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED);
    }

    // ---- touch ------------------------------------------------------------------------------------------------------

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        track(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                dragging = false;
                if (settle.isRunning()) {
                    settle.cancel();
                    startDrag();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && wantsDrag(e)) {
                    startDrag();
                    lastX = e.getX();
                }
                return dragging;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) release(e.getActionMasked() == MotionEvent.ACTION_CANCEL);
                return false;
            default:
                return dragging;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        track(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                if (settle.isRunning()) {
                    settle.cancel();
                    startDrag();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && wantsDrag(e)) {
                    startDrag();
                    lastX = e.getX();
                }
                if (dragging) {
                    float dx = e.getX() - lastX;
                    lastX = e.getX();
                    raw -= dx / Math.max(1f, getWidth());
                    // a page either side at most in one go, with a little give past either end
                    float held = Math.max(dragStart - 1, Math.min(dragStart + 1, raw));
                    pos = CarouselMath.rubber(held, count);
                    arrange();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) release(false);
                else goTo(CarouselMath.nearest(pos, count));
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) release(true);
                else goTo(CarouselMath.nearest(pos, count));
                return true;
            default:
                return true;
        }
    }

    private boolean wantsDrag(MotionEvent e) {
        float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
        return count > 0 && dx > slop && dx > dy * 1.2f;
    }

    private void startDrag() {
        dragging = true;
        dragStart = at;
        raw = pos;
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    private void track(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && velocity != null) velocity.clear();
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
    }

    private void release(boolean cancelled) {
        dragging = false;
        float v = 0;
        if (velocity != null && !cancelled) {
            velocity.computeCurrentVelocity(1000, maxFling);
            v = -velocity.getXVelocity() / Math.max(1f, getWidth());
        }
        goTo(pageTarget(pos, v, dragStart, minFling * 2f / Math.max(1f, getWidth()), count));
    }

    /**
     * Pure: where a let-go page swipe settles: a fling past minFling (pages a second) goes on to the next page that way,
     * a slow one to the nearest; never more than a page from where the drag began, kept to the pages.
     */
    static int pageTarget(float pos, float velocity, int start, float minFling, int count) {
        if (count <= 0) return 0;
        int t;
        if (velocity >= minFling) t = Math.max(start + 1, Math.round(pos));
        else if (velocity <= -minFling) t = Math.min(start - 1, Math.round(pos));
        else t = Math.round(pos);
        t = Math.max(start - 1, Math.min(start + 1, t));
        return Math.max(0, Math.min(count - 1, t));
    }

    @Override protected void onDetachedFromWindow() {
        settle.cancel();
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        super.onDetachedFromWindow();
    }

    // ---- keys and screen readers ----------------------------------------------------------------------------------

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && at > 0) {
            goTo(at - 1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && at < count - 1) {
            goTo(at + 1);
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (at > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        if (at < count - 1) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.setScrollable(count > 1);
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD && at < count - 1) {
            goTo(at + 1);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD && at > 0) {
            goTo(at - 1);
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }

    @Override public CharSequence getAccessibilityClassName() {
        return "android.widget.HorizontalScrollView";
    }
}
