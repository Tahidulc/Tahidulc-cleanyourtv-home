package com.gathin.tvhomephone.live;

import android.content.Context;

/**
 * Swipe left or right on the picture for the next or previous channel in the category playing (the family's ask), with
 * a quick channel card and the picture sliding across (PlayerActivity). Only a clear horizontal fling counts: far
 * enough, fast enough, quick, much more across than up or down, with one finger, and not from the strip at the screen's
 * sides where the phone's own back gesture lives. It never takes a touch from what else the picture does: a pinch or a
 * double tap (the picture's size), the brightness and volume slides at the edges, the scoreboard and Homefield's mark
 * held and dragged (they take their own touches, and Move and resize is left alone), a catch-up or rewind being
 * watched (the rewind's own double taps and bar), the match centre, the touch lock, picture in picture. With it on, a
 * swipe up brings the guide panel (the mini guide, and Recent) and a swipe down puts it away; turned off (the player's
 * options, Settings, Live TV) the swipes are as they were: up and down for the channels, left for the mini guide, right
 * to put it away. The choices are pure, for the tests.
 */
final class ChannelSwipe {
    private ChannelSwipe() {}

    static final int NONE = 0, NEXT = 1, PREVIOUS = -1;
    /** At least this far across (dp), this fast (dp a second), and over within this long. */
    static final float MIN_DISTANCE_DP = 56f, MIN_SPEED_DP = 650f;
    static final long MAX_MS = 450L;
    /** Across at least this many times as far as up or down, and as fast. */
    static final float RATIO = 2.2f, SPEED_RATIO = 2f;
    /** The strip along each side of the screen that is the phone's back gesture (dp). */
    static final float EDGE_DP = 24f;
    /** The player's own preference ("0" off; on unless switched off). */
    static final String PREF = "swipe_channels";

    static boolean on(Context c) {
        return !"0".equals(Aspect.prefs(c).get(PREF));
    }

    static void setOn(Context c, boolean on) {
        Aspect.prefs(c).put(PREF, on ? "1" : "0");
    }

    /**
     * Which way a fling went, as a change of channel: NEXT for a swipe to the left (the next channel comes in from the
     * right), PREVIOUS to the right, NONE when it isn't a clear horizontal fling. startX is where it began across a
     * width (px), dx and dy how far it went, vx and vy its speed (px a second), ms how long it took, density px per dp.
     */
    static int direction(float startX, float width, float dx, float dy, float vx, float vy, long ms, float density) {
        float d = Math.max(0.1f, density);
        float ax = Math.abs(dx), ay = Math.abs(dy), avx = Math.abs(vx), avy = Math.abs(vy);
        if (width > 0 && (startX < EDGE_DP * d || startX > width - EDGE_DP * d)) return NONE;
        if (ax < MIN_DISTANCE_DP * d || avx < MIN_SPEED_DP * d) return NONE;
        if (ax < ay * RATIO || avx < avy * SPEED_RATIO) return NONE;
        if (ms < 0 || ms > MAX_MS) return NONE;
        // Its speed the same way as its travel (a fling that turned back at the end is not one).
        if (Math.signum(vx) != Math.signum(dx)) return NONE;
        return dx < 0 ? NEXT : PREVIOUS;
    }

    /** Why a swipe can't change channel now: anything else the picture is doing, or the setting off. */
    static final class State {
        boolean off, pip, multiTouch, edgeSlide, replaying, adjusting, locked, matchOpen, casting;

        boolean blocked() {
            return off || pip || multiTouch || edgeSlide || replaying || adjusting || locked || matchOpen || casting;
        }
    }

    /** The change of channel a fling asks for now: its direction, unless anything else has the picture. */
    static int decide(State s, float startX, float width, float dx, float dy, float vx, float vy, long ms, float density) {
        if (s != null && s.blocked()) return NONE;
        return direction(startX, width, dx, dy, vx, vy, ms, density);
    }

    /** Pure: where the channels step to: +1 for NEXT, -1 for PREVIOUS (Hidden.step passes hidden ones over). */
    static int step(int direction) {
        return direction == NEXT ? 1 : direction == PREVIOUS ? -1 : 0;
    }
}
