package com.gathin.homefieldtv.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings › Appearance › Home, for Home, Movies and TV Shows: a title's trailer playing in the poster the remote rests
 * on (Trailers: On, as it comes) and whether it plays with its sound (Sound: On, as it comes since the family asked "Make
 * the trailers have sound too"; it fades in once the trailer is shown, never while it is hidden). A TV that had Sound off
 * as it used to come is switched on once (SOUND_ON_ONCE); the family's own choice after that stays. Read as a trailer is
 * about to play, never while drawing.
 */
public final class TrailerPrefs {
    private TrailerPrefs() {}

    /** Set once Sound has been switched on for the family (the one-time move to sound on as it comes). */
    static final String SOUND_ON_ONCE = "trailer_sound_on_1";

    public static boolean on(Context c) {
        return Look.prefs(c).getBoolean("trailers", true);
    }

    public static void setOn(Context c, boolean v) {
        Look.prefs(c).edit().putBoolean("trailers", v).apply();
    }

    public static boolean sound(Context c) {
        SharedPreferences p = Look.prefs(c);
        if (!p.getBoolean(SOUND_ON_ONCE, false)) {
            // Once: the trailers' sound on, whatever it was before (off was how it came).
            p.edit().putBoolean("trailer_sound", true).putBoolean(SOUND_ON_ONCE, true).apply();
            return true;
        }
        return p.getBoolean("trailer_sound", true);
    }

    public static void setSound(Context c, boolean v) {
        Look.prefs(c).edit().putBoolean("trailer_sound", v).putBoolean(SOUND_ON_ONCE, true).apply();
    }
}
