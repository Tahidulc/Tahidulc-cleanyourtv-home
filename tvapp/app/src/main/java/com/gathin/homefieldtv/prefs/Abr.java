package com.gathin.homefieldtv.prefs;

/**
 * When the live picture steps between a channel's versions (Ladder), as the phone's Quality and Playback decide it
 * (Homefield 5.21). Down before the picture freezes: the buffer draining under 2.5 s, or a stall that lasts 5 s, or
 * three stalls in a minute. Back up once it has played steadily for a while: 45 s, then twice as long after each try up
 * that stalled within a minute, up to 15 minutes. A failure: the same stream tried again at once twice, then another
 * playlist's copy, then a step down, then tried again 1, 2, 4, 8, then 10 s apart. Pure (tests).
 */
public final class Abr {
    private Abr() {}

    /** A failure: the same stream tried again this many times at once before another copy of the channel. */
    public static final int QUICK_TRIES = 2;
    /** Stepped this lately: the buffer isn't read yet (it fills from nothing). */
    public static final long SETTLE_MS = 10_000L;
    /** A stall still going this long steps down. */
    public static final long STALL_STEP_MS = 5_000L;
    /** A try up that stalls this soon after didn't hold. */
    public static final long TRY_HOLDS_MS = 60_000L;

    /**
     * The buffer (ms, a second apart, oldest first) draining as it plays, the connection falling behind: under 2.5 s
     * now, not growing from one second to the next (a fifth of a second's wobble allowed), 1.5 s lost overall. A step
     * down then, before the picture freezes.
     */
    public static boolean draining(long[] bufferedMs) {
        int n = bufferedMs.length;
        if (n < 3 || bufferedMs[n - 1] >= 2500) return false;
        for (int i = 1; i < n; i++) if (bufferedMs[i] > bufferedMs[i - 1] + 200) return false;
        return bufferedMs[0] - bufferedMs[n - 1] >= 1500;
    }

    /** How long a version must play steadily before the next one up is tried: 45 s, then 1.5, 3, 6, 12, 15 min. */
    public static long upAfter(int failedUps) {
        return Math.min(20, 1L << Math.min(5, failedUps)) * 45_000L;
    }

    /** Whether the stalls (their start times, newest last) say the connection can't keep up: 3 in a minute, or one of 5 s. */
    public static boolean struggling(long[] stallStarts, int n, long now, long longestStallMs) {
        if (longestStallMs >= 5000) return true;
        int recent = 0;
        for (int i = 0; i < n; i++) if (now - stallStarts[i] <= 60_000L) recent++;
        return recent >= 3;
    }

    /** The wait before the nth try again: 1, 2, 4, 8 s, then 8 s (the quick tries) or 10 s (staying on the stream). */
    public static long retryDelay(int retries, boolean stay) {
        long d = 1000L << Math.max(0, Math.min(4, retries - 1));
        return Math.min(stay ? 10_000L : 8000L, d);
    }
}
