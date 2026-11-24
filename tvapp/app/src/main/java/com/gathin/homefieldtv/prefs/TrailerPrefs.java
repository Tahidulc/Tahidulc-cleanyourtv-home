package com.gathin.homefieldtv.prefs;

import android.content.Context;

/**
 * Settings › Appearance › Home, for Home, Movies and TV Shows: a title's trailer playing in the poster the remote rests
 * on (Trailers: On, as it comes) and whether it plays with its sound (Sound: Off, as it comes, so a trailer starting by
 * itself is never loud). Read as a trailer is about to play, never while drawing.
 */
public final class TrailerPrefs {
    private TrailerPrefs() {}

    public static boolean on(Context c) {
        return Look.prefs(c).getBoolean("trailers", true);
    }

    public static void setOn(Context c, boolean v) {
        Look.prefs(c).edit().putBoolean("trailers", v).apply();
    }

    public static boolean sound(Context c) {
        return Look.prefs(c).getBoolean("trailer_sound", false);
    }

    public static void setSound(Context c, boolean v) {
        Look.prefs(c).edit().putBoolean("trailer_sound", v).apply();
    }
}
