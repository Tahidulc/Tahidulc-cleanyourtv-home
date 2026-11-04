package com.gathin.tvhome;

/**
 * Launcher v2's dock under the focus: how much each item swells, how far an app's icon slides aside from the focused
 * one, and the look Home keeps for each app so that a key press only repaints the apps whose look has changed.
 */
final class DockSwell {
    private DockSwell() { }

    /**
     * How much the item at place {@code at} along the dock swells while the item at place {@code focus} has the focus
     * (-1: the focus is not in the dock): the item itself most, its neighbours a little.
     */
    static float scale(int at, int focus) {
        if (focus < 0) return 1f;
        int d = Math.abs(at - focus);
        return d == 0 ? 1.2f : d == 1 ? 1.07f : 1f;
    }

    /**
     * How far an app's icon moves aside (design px) while app `at` has the focus: its glass reaches 13.5 past the
     * swollen icon's edge, more than the gap to the next, so the neighbours slide out of its way (10, those beyond 12)
     * and the glass keeps 8 clear of every icon. Nothing moves while the focus is elsewhere.
     */
    static float aside(int i, int at, int apps) {
        if (at < 0 || at >= apps || i >= apps || i == at) return 0f;
        return Math.signum(i - at) * (Math.abs(i - at) == 1 ? 10f : 12f);
    }

    /**
     * The look of one dock app as a single number for HomeV2.restyle: whether it has the focus, its swell and how far it
     * has slid aside. The slide is part of it because an app two or more places from the focus keeps the same swell
     * on either side of it and while the focus is away from the dock; without it, an app that had slid one way was not
     * moved back when the focus came down into the dock past it, and its neighbour, sliding towards it, overlapped it.
     */
    static int state(boolean on, float scale, float aside) {
        return (on ? 1 : 0) | Math.round(scale * 100) << 1 | (Math.round(aside) + 128) << 9;
    }
}
