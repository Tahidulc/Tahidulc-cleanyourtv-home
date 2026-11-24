package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * F1's mark, one way everywhere the launcher shows it (24 Nov: "the f1 logo needs to be bigger and it shades into the
 * red"): ESPN's mark (F1Results.F1_LOGO), as the launcher has always fetched it, cut to its own edges (it sits small
 * in the middle of a square, so untrimmed it showed a fraction of the place it was given) so it fills its place, in
 * white wherever F1's red is behind it (the top bar's F1 pill, the F1 cards, boards and timing tower) and in its own
 * red on the dark glass (the Sports tables' tab, the sport rows' chips, the plain score pills). No artwork of our own.
 * Each kept once read: two small pictures, shared, never recycled.
 */
final class F1Mark {
    private F1Mark() { }

    /** The address the score pills ask their logo loader for, for the mark on F1's red. */
    static final String ON_RED = "f1mark:red", ON_DARK = "f1mark:dark";

    private static Bitmap red, white;

    /** Whether url is F1's mark (F1Results.F1_LOGO, or the same mark from Sky's guide's sport rows). */
    static boolean is(String url) {
        return url != null && (F1Results.F1_LOGO.equals(url) || url.endsWith("/teamlogos/leagues/500/f1.png"));
    }

    /** The mark in its own red, cut to its edges, for a dark ground. Off the main thread; null until it can be had. */
    static synchronized Bitmap onDark(Context c) {
        if (red == null) {
            Bitmap m = ScoresTicker.cachedLogo(c, F1Results.F1_LOGO, 200);
            if (m != null) {
                Bitmap t = HomeV2.trim(m);
                if (t != m) m.recycle();
                red = t;
            }
        }
        return red;
    }

    /** The mark in white, cut to its edges, for F1's red. Off the main thread; null until it can be had. */
    static synchronized Bitmap onRed(Context c) {
        if (white == null) {
            Bitmap r = onDark(c);
            if (r != null) white = GoalWatchService.white(r);
        }
        return white;
    }

    /** onRed or onDark for one of the pills' addresses (ON_RED, ON_DARK); null for any other. */
    static Bitmap of(Context c, String address) {
        return ON_RED.equals(address) ? onRed(c) : ON_DARK.equals(address) ? onDark(c) : null;
    }
}
