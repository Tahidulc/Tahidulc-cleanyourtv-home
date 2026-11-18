package com.gathin.tvhomephone.sports;

import android.view.KeyEvent;

/**
 * The squad carousel's arithmetic, pure for the tests: where each card sits, how large and how bright, for its
 * distance d from the middle (in cards, d = its place − the carousel's position); where a fling ends; what a tap and a
 * key do. The first neighbour sits s1 from the middle, the second s2 further, the third s3 further again.
 */
final class CarouselMath {
    private CarouselMath() {}

    /** The card's size against the middle one's: 1, then 0.7, 0.55 and 0.46 for the neighbours out to the third. */
    static float scale(float d) {
        return step(Math.abs(d), 1f, 0.70f, 0.55f, 0.46f);
    }

    /** How brightly a card shows: full in the middle, dimmer out to the second, gone past the third. */
    static float alpha(float d) {
        float a = Math.abs(d);
        if (a >= 3.2f) return 0f;
        if (a > 2.6f) return 0.3f * (3.2f - a) / 0.6f;
        if (a > 2f) return 0.5f - (a - 2f) * (0.2f / 0.6f);
        return step(a, 1f, 0.82f, 0.5f, 0.5f);
    }

    /** How much of the middle card's look a card has (its big name, its coin at the top): 1 in the middle, 0 a card away. */
    static float focus(float d) {
        return Math.max(0f, 1f - Math.abs(d));
    }

    /** The card's centre from the middle (signed, as d), the gaps s1, s2 and s3 between the slots, and on at s3 a card. */
    static float offset(float d, float s1, float s2, float s3) {
        float a = Math.abs(d), x;
        if (a <= 1f) x = a * s1;
        else if (a <= 2f) x = s1 + (a - 1f) * s2;
        else x = s1 + s2 + (a - 2f) * s3;
        return d < 0 ? -x : x;
    }

    private static float step(float a, float v0, float v1, float v2, float v3) {
        if (a <= 1f) return v0 + (v1 - v0) * a;
        if (a <= 2f) return v1 + (v2 - v1) * (a - 1f);
        if (a <= 3f) return v2 + (v3 - v2) * (a - 2f);
        return v3;
    }

    /** The card nearest the middle, kept to the list. */
    static int nearest(float pos, int count) {
        if (count <= 0) return 0;
        return Math.max(0, Math.min(count - 1, Math.round(pos)));
    }

    /**
     * A drag past either end gives way less and less (at most half a card): the position shown for a dragged one.
     */
    static float rubber(float raw, int count) {
        float last = Math.max(0, count - 1);
        if (raw < 0) return -over(-raw);
        if (raw > last) return last + over(raw - last);
        return raw;
    }

    private static float over(float x) {
        return 0.5f * (1f - 1f / (x * 1.4f + 1f));
    }

    /** How long the carousel carries for, per card/s of a fling (where it would coast to, give or take). */
    static final float COAST_S = 0.2f;

    /**
     * Where a let-go settles: from the position and the fling's speed (cards a second, towards the later cards when
     * positive), a fling of at least minFling carrying on at least one card from where the drag began. Kept to the list.
     */
    static int flingTarget(float pos, float velocity, int start, float minFling, int count) {
        if (count <= 0) return 0;
        int t = Math.round(pos + velocity * COAST_S);
        if (velocity >= minFling) t = Math.max(t, start + 1);
        else if (velocity <= -minFling) t = Math.min(t, start - 1);
        else t = Math.round(pos);
        return Math.max(0, Math.min(count - 1, t));
    }

    /** How long a settle takes for a distance in cards: a short snap for one, longer (but not slow) for more. */
    static long settleMs(float distance) {
        float d = Math.abs(distance);
        if (d < 0.001f) return 0;
        return Math.min(560L, Math.round(190 + 130 * Math.sqrt(d)));
    }

    static final int NONE = 0, OPEN = 1, MOVE = 2;

    /** What a tap on a card does: the one in the middle, at rest, opens; any other comes to the middle. */
    static int tap(int tapped, float pos, boolean moving, int count) {
        if (tapped < 0 || tapped >= count) return NONE;
        if (!moving && Math.abs(pos - tapped) < 0.05f) return OPEN;
        return MOVE;
    }

    /** Where a key takes the focus (its card), or -1 when the key is not the carousel's. */
    static int keyTarget(int focus, int keyCode, int count) {
        if (count <= 0) return -1;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return Math.max(0, focus - 1);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return Math.min(count - 1, focus + 1);
            case KeyEvent.KEYCODE_MOVE_HOME:
                return 0;
            case KeyEvent.KEYCODE_MOVE_END:
                return count - 1;
            case KeyEvent.KEYCODE_PAGE_UP:
                return Math.max(0, focus - 3);
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return Math.min(count - 1, focus + 3);
            default:
                return -1;
        }
    }

    /** Whether a key opens the card in the middle. */
    static boolean opens(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }
}
