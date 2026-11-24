package com.gathin.homefieldtv.prefs;

import android.content.Context;

/**
 * Settings › Appearance › Home style, for Home, Movies and TV Shows: Editorial (as it comes: a big carousel with the
 * next titles peeking in, mood tiles and rows of titles) or Classic (one big card at a time on Home; Movies and TV Shows
 * with their lists as pills, as 2.0 had them). Read as each of those screens is made; the screens start again in a new
 * choice.
 */
public final class HomeStyle {
    private HomeStyle() {}

    public static final int EDITORIAL = 0, CLASSIC = 1;
    public static final String[] NAMES = {"Editorial", "Classic"};
    public static final String ABOUT = "Home, Movies and TV Shows. Editorial: a big carousel with the next titles peeking in, "
            + "mood tiles and rows. Classic: one big card at a time. The screens start again in it.";

    /** The style chosen (EDITORIAL as it comes): read as a screen is made, never while drawing. */
    public static int get(Context c) {
        return PlayerPrefs.clamp(Look.prefs(c).getInt("home_style", EDITORIAL), 0, 1);
    }

    public static boolean editorial(Context c) {
        return get(c) == EDITORIAL;
    }

    public static void set(Context c, int v) {
        Look.prefs(c).edit().putInt("home_style", PlayerPrefs.clamp(v, 0, 1)).apply();
    }
}
