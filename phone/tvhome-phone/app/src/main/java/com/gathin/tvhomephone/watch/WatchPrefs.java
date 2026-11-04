package com.gathin.tvhomephone.watch;

import android.content.Context;

import java.util.Locale;

/** The Watch tab's own settings on this phone: bedtime (minutes after midnight, 23:00 unless set). */
public final class WatchPrefs {
    private WatchPrefs() {}

    private static android.content.SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("watch", Context.MODE_PRIVATE);
    }

    public static int bedtime(Context c) {
        return p(c).getInt("bedtime", 23 * 60);
    }

    public static void setBedtime(Context c, int minutes) {
        p(c).edit().putInt("bedtime", Math.max(0, Math.min(24 * 60 - 1, minutes))).apply();
    }

    public static String bedtimeText(Context c) {
        int m = bedtime(c);
        return String.format(Locale.UK, "%02d:%02d", m / 60, m % 60);
    }
}
