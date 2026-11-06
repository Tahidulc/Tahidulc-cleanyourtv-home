package com.gathin.tvhomephone.live;

/**
 * Whether the phone has been turned, from its tilt as the sensor reads it (OrientationEventListener's angle): upright
 * within 30 degrees of upright, on its side within 30 degrees of either side, and nothing in between, so a wobble
 * doesn't count. A reading has to hold for STEADY_MS. A turn on to its side only counts once the phone has been seen
 * upright, so a phone already on its side, lying down with it, never counts as turned. The guide uses it to go full
 * screen with the phone's rotation locked; full screen opened upright uses it to go on its side and back. Pure.
 */
final class Turn {
    static final int NONE = -1, UPRIGHT = 0, SIDEWAYS = 1;
    static final long STEADY_MS = 450;

    /** The last steady way it was held, and the reading that may become it. */
    private int held = NONE, pending = NONE;
    private long since;
    private boolean seenUpright;

    /** The way the angle reads: UPRIGHT, SIDEWAYS, or NONE (flat, or between the two). */
    static int read(int angle) {
        if (angle < 0) return NONE;
        int a = angle % 360;
        if (a <= 30 || a >= 330) return UPRIGHT;
        if ((a >= 60 && a <= 120) || (a >= 240 && a <= 300)) return SIDEWAYS;
        return NONE;
    }

    /** A reading at time t (ms): UPRIGHT or SIDEWAYS when the phone has just been turned that way, else NONE. */
    int feed(int angle, long t) {
        int r = read(angle);
        if (r == NONE) {
            pending = NONE;
            return NONE;
        }
        if (r != pending) {
            pending = r;
            since = t;
            return NONE;
        }
        if (t - since < STEADY_MS || r == held) return NONE;
        held = r;
        if (r == UPRIGHT) {
            seenUpright = true;
            return UPRIGHT;
        }
        return seenUpright ? SIDEWAYS : NONE;
    }
}
