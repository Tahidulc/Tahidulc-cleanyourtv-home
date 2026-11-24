package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings › Appearance › Programme times, as Homefield TV has it: 24-hour as it comes ("19:40", as the guide's times
 * across the top always were) or 12-hour ("7.40pm", Sky's). Every programme time in Live TV, the guide, the programme's
 * details and the player goes by it. Read as the screens draw (a static, loaded when the app starts), so a change shows
 * as each screen next draws. Kept under Homefield TV's own name for it ("look": "times24").
 */
public final class Times {
    private Times() {}

    static final String PREFS = "look", KEY = "times24";

    /** As the screens draw them now. */
    static volatile boolean h24 = true;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The choice read for the screens (the app starting). */
    public static void load(Context c) {
        h24 = prefs(c).getBoolean(KEY, true);
    }

    public static boolean h24() {
        return h24;
    }

    static void set(Context c, boolean on) {
        h24 = on;
        prefs(c).edit().putBoolean(KEY, on).apply();
    }

    // ---- pure ---------------------------------------------------------------------------------------------------

    /** "19:40", or "7.40pm" (12 midday "12.00pm", midnight "12.00am"). */
    static String hm(int hour, int minute, boolean h24) {
        char[] b = new char[8];
        return new String(b, 0, write(b, 0, hour, minute, h24));
    }

    /**
     * The same written into buf at at, for a screen drawing it (no allocation); the index after it. buf has room for
     * seven characters from at.
     */
    static int write(char[] buf, int at, int hour, int minute, boolean h24) {
        int h = ((hour % 24) + 24) % 24, m = ((minute % 60) + 60) % 60;
        if (h24) {
            buf[at++] = (char) ('0' + h / 10);
            buf[at++] = (char) ('0' + h % 10);
            buf[at++] = ':';
        } else {
            int twelve = h % 12 == 0 ? 12 : h % 12;
            if (twelve >= 10) buf[at++] = '1';
            buf[at++] = (char) ('0' + twelve % 10);
            buf[at++] = '.';
        }
        buf[at++] = (char) ('0' + m / 10);
        buf[at++] = (char) ('0' + m % 10);
        if (!h24) {
            buf[at++] = h < 12 ? 'a' : 'p';
            buf[at++] = 'm';
        }
        return at;
    }
}
