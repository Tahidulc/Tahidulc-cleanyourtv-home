package com.gathin.tvhomephone.live;

/**
 * Whether the channel has gone to an advert break, from its own logo: UK channels take their logo off the picture for
 * the adverts and put it back for the programme. The logo's outline is learnt where BugFinder found it (the edges that
 * stayed put frame after frame); a frame then says how much of that outline is still there. Gone for four checks in a
 * row (about eight seconds) is a break; back for two in a row, the break is over. Pure: the tests feed it frames.
 */
final class BreakWatch {
    private static final int EDGE = 36;
    static final float GONE = 0.3f, BACK = 0.55f;
    static final int GONE_CHECKS = 4, BACK_CHECKS = 2;

    private final boolean[] mask;
    private final int w, h, edges;
    private int gone, back;
    private boolean onBreak;

    /** The logo's outline (BugFinder.logoMask) on frames w by h. */
    BreakWatch(boolean[] mask, int w, int h) {
        this.mask = mask;
        this.w = w;
        this.h = h;
        int n = 0;
        for (boolean m : mask) if (m) n++;
        edges = n;
    }

    /** Whether there is enough outline to go by. */
    boolean usable() {
        return edges >= 12;
    }

    /** How much of the logo's outline a frame (grey values, w by h) still has, 0 to 1. */
    float present(byte[] lum) {
        if (lum == null || lum.length < w * h || edges == 0) return 0f;
        int there = 0;
        for (int y = 0; y + 1 < h; y++) {
            for (int x = 0; x + 1 < w; x++) {
                int i = y * w + x;
                if (!mask[i]) continue;
                int v = lum[i] & 0xFF;
                int g = Math.abs(v - (lum[i + 1] & 0xFF)) + Math.abs(v - (lum[i + w] & 0xFF));
                if (g >= EDGE) there++;
            }
        }
        return there / (float) edges;
    }

    /** One check's share of the outline there; true while on a break. */
    boolean add(float presence) {
        if (presence < GONE) {
            gone++;
            back = 0;
        } else if (presence > BACK) {
            back++;
            gone = 0;
        } else {
            gone = 0;
            back = 0;
        }
        if (!onBreak && gone >= GONE_CHECKS) onBreak = true;
        else if (onBreak && back >= BACK_CHECKS) onBreak = false;
        return onBreak;
    }

    boolean onBreak() {
        return onBreak;
    }

    /** Pure: whether a break can be taken for one at all: the game before kick-off, at half time or over (never in play). */
    static boolean allowed(com.gathin.tvhomephone.sports.Game g) {
        if (g == null) return false;
        return g.state != com.gathin.tvhomephone.sports.Game.LIVE || g.halftime;
    }
}
