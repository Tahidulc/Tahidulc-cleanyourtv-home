package com.gathin.tvhomephone.live;

/**
 * How a penalty shootout's newest kick lands under the scoreboard (BoardUnder), ms into its animation. A scored kick
 * (SCORED_MS): its mark pops in from small past its size and settles, a green fill sweeps round it, the tick comes in,
 * then a soft glow ring opens out and fades; the family's swoosh starts first so its loudest moment lands on the pop. A
 * missed or saved kick (MISSED_MS), quieter and without a sound: its red mark comes in, the cross is drawn stroke by
 * stroke and the mark gives a small shake. Pure: the tests check it.
 */
final class PenMark {
    private PenMark() { }

    /** A scored kick's whole animation, the pop's part of it, and a missed kick's. */
    static final long SCORED_MS = 720L, POP_MS = 300L, MISSED_MS = 420L;
    /** The overshoot's tension: past its size by an eighth at the most. */
    private static final float TENSION = 2f;
    /** Where in the pop it is biggest (its overshoot's peak): 2T / (3(T + 1)) before its end, as a share of it. */
    static final float PEAK_AT = 1f - 2f * TENSION / (3f * (TENSION + 1f));

    /** Pure: the overshoot curve from 0 to 1, past 1 on its way (OvershootInterpolator's, tension 2). */
    static float overshoot(float t) {
        float x = clamp(t) - 1f;
        return x * x * ((TENSION + 1f) * x + TENSION) + 1f;
    }

    /** Pure: ms into the pop when it is biggest. */
    static long popPeakMs() {
        return Math.round(POP_MS * PEAK_AT);
    }

    /** Pure: how long after the sound starts the pop starts, for the sound's loudest moment (peakMs in) to land on it. */
    static long startAfterSound(long soundPeakMs) {
        return Math.max(0L, soundPeakMs - popPeakMs());
    }

    /** Pure: a scored mark's size, its own size 1: from nothing, past it, settled once the pop is done. */
    static float scale(long ms) {
        if (ms <= 0) return 0f;
        return ms >= POP_MS ? 1f : overshoot(ms / (float) POP_MS);
    }

    /** Pure: how far round the green fill has swept (0 to 1), from early in the pop to just after it. */
    static float sweep(long ms) {
        return easeOut((ms - 90L) / 300f);
    }

    /** Pure: the tick coming in (0 to 1) as the sweep closes. */
    static float tick(long ms) {
        return easeOut((ms - 330L) / 170f);
    }

    /** Pure: the glow ring {its size (1 the mark's), how much of it shows (0 to 1)}: opening out and fading at the end. */
    static float[] glow(long ms) {
        if (ms < 380L || ms >= SCORED_MS) return new float[]{1f, 0f};
        float p = easeOut((ms - 380L) / (float) (SCORED_MS - 380L));
        return new float[]{1f + 0.6f * p, 0.55f * (1f - p)};
    }

    /** Pure: a missed mark's size: in from a little smaller, quickly and without overshoot. */
    static float missScale(long ms) {
        if (ms <= 0) return 0.55f;
        return 0.55f + 0.45f * easeOut(ms / 160f);
    }

    /** Pure: how much of the cross is drawn (0 to 1: its first stroke, then its second). */
    static float cross(long ms) {
        return clamp((ms - 80L) / 220f);
    }

    /** Pure: the missed mark's shake, a share of its radius each way: a few small swings that die away, none after. */
    static float shake(long ms) {
        if (ms < 150L || ms >= MISSED_MS) return 0f;
        float p = (ms - 150L) / (float) (MISSED_MS - 150L);
        return (float) Math.sin(p * 3.0 * 2.0 * Math.PI) * (1f - p);
    }

    /** Pure: whether the animation has run its course. */
    static boolean done(boolean scored, long ms) {
        return ms >= (scored ? SCORED_MS : MISSED_MS);
    }

    private static float easeOut(float t) {
        float x = clamp(t);
        return 1f - (1f - x) * (1f - x);
    }

    private static float clamp(float t) {
        return Math.max(0f, Math.min(1f, t));
    }
}
