package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;

/**
 * The picture's touch, the full screen's and the guide's alike: a tap, a double tap, a pinch, a swipe up, down, left or
 * right, and (full screen only) a slide up or down along the left or right edge. Two fingers are a pinch, never a tap
 * or a swipe; a slide that starts at an edge is the edge's, never a change of channel.
 */
final class Gestures implements View.OnTouchListener {
    static final int NONE = 0, UP = 1, DOWN = 2, LEFT = 3, RIGHT = 4;
    static final int MIDDLE = 0, LEFT_EDGE = 1, RIGHT_EDGE = 2;
    /** The share of the width either side that is an edge, for its slides. */
    static final float EDGE = 0.3f;

    interface Callback {
        void onTap();

        void onDoubleTap();

        /** A double tap in the left third (-1), the middle (0) or the right third (1); the plain double tap unless taken. */
        default void onDoubleTapAt(int third) {
            onDoubleTap();
        }

        /** A pinch under way, scaled this much so far. */
        void onPinch(float scale);

        void onPinchEnd(float scale);

        /** UP, DOWN, LEFT or RIGHT. */
        void onSwipe(int direction);

        /** Where a slide along this edge starts from (0 to 1), or below 0 for no edge slides here. */
        default float edgeLevel(int edge) { return -1f; }

        /** The slide along the edge has reached this level (from its start, up for more). */
        default void onEdge(int edge, float level) {}

        default void onEdgeEnd() {}
    }

    /**
     * Which way a swipe went (dx and dy from where it started, vx and vy its speed): clearly more one way than the
     * other, at least minDistance and minVelocity that way; NONE otherwise.
     */
    static int direction(float dx, float dy, float vx, float vy, float minDistance, float minVelocity) {
        float ax = Math.abs(dx), ay = Math.abs(dy);
        if (ay > ax * 1.3f && ay >= minDistance && Math.abs(vy) >= minVelocity) return dy < 0 ? UP : DOWN;
        if (ax > ay * 1.3f && ax >= minDistance && Math.abs(vx) >= minVelocity) return dx < 0 ? LEFT : RIGHT;
        return NONE;
    }

    /** Pure: which third of a width x is in: -1 the left, 0 the middle, 1 the right. */
    static int third(float x, float width) {
        if (width <= 0) return 0;
        return x < width / 3f ? -1 : x > width * 2f / 3f ? 1 : 0;
    }

    /** Where a touch at x landed across a width: an edge (for its slides) or the middle. */
    static int zone(float x, float width) {
        if (width <= 0) return MIDDLE;
        return x < width * EDGE ? LEFT_EDGE : x > width * (1 - EDGE) ? RIGHT_EDGE : MIDDLE;
    }

    private final Callback cb;
    private final GestureDetector taps;
    private final ScaleGestureDetector scale;
    private final int slop;
    private final float minDistance, minVelocity;
    private int zone;
    private boolean edge, multi;
    private float downX, downY, startLevel, pinch = 1f, width;

    Gestures(Context c, Callback cb, float minDistanceDp) {
        this.cb = cb;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        minDistance = Ui.dp(c, minDistanceDp);
        minVelocity = Ui.dp(c, 300);
        taps = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(@NonNull MotionEvent e) { return true; }

            @Override public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                cb.onTap();
                return true;
            }

            @Override public boolean onDoubleTap(@NonNull MotionEvent e) {
                cb.onDoubleTapAt(third(e.getX(), width));
                return true;
            }

            @Override public boolean onFling(MotionEvent a, @NonNull MotionEvent b, float vx, float vy) {
                if (edge || multi) return false;
                int d = direction(b.getX() - downX, b.getY() - downY, vx, vy, minDistance, minVelocity);
                if (d == NONE) return false;
                // Up and down from an edge is the edge's slide.
                if ((d == UP || d == DOWN) && zone != MIDDLE) return false;
                cb.onSwipe(d);
                return true;
            }
        });
        scale = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(@NonNull ScaleGestureDetector d) {
                pinch = 1f;
                return true;
            }

            @Override public boolean onScale(@NonNull ScaleGestureDetector d) {
                pinch *= d.getScaleFactor();
                cb.onPinch(pinch);
                return true;
            }

            @Override public void onScaleEnd(@NonNull ScaleGestureDetector d) {
                cb.onPinchEnd(pinch);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouch(View v, MotionEvent e) {
        scale.onTouchEvent(e);
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            multi = false;
            edge = false;
            downX = e.getX();
            downY = e.getY();
            width = v.getWidth();
            zone = zone(downX, v.getWidth());
            startLevel = zone == MIDDLE ? -1f : cb.edgeLevel(zone);
            // No edge slides here (the guide's picture): the whole width is the middle's.
            if (startLevel < 0) zone = MIDDLE;
            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
        } else if (action == MotionEvent.ACTION_POINTER_DOWN && !multi) {
            // Two fingers: a pinch, not a tap or a swipe.
            multi = true;
            MotionEvent cancel = MotionEvent.obtain(e);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            taps.onTouchEvent(cancel);
            cancel.recycle();
        }
        if (multi) return true;
        if (action == MotionEvent.ACTION_MOVE && zone != MIDDLE) {
            float dy = downY - e.getY(), dx = e.getX() - downX;
            if (!edge && Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) edge = true;
            if (edge) cb.onEdge(zone, startLevel + dy / Math.max(1f, v.getHeight() * 0.8f));
        }
        taps.onTouchEvent(e);
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && edge) {
            edge = false;
            cb.onEdgeEnd();
        }
        return true;
    }
}
