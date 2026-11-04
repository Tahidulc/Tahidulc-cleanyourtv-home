package com.gathin.tvhome;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Do not disturb (the Control Centre's tile): while it is on nothing pops up, over apps or on Home, but the doorbell
 * and the TV's own questions and codes (NotifyPrefs keeps it, and Today's alerts says why each was not shown). OK on
 * the tile steps through Off, an hour, until seven in the morning and until it is turned off again.
 *
 * <p>Pure Java (no Android), so the unit tests can read it; NotifyPrefs keeps the choice.
 */
final class DoNotDisturb {
    private DoNotDisturb() { }

    static final int OFF = 0, HOUR = 1, MORNING = 2, ALWAYS = 3;
    /** The hour "until the morning" ends at. */
    static final int MORNING_HOUR = 7;

    /** The step after this one on the tile, back to Off after Always. */
    static int next(int mode) {
        return mode >= ALWAYS || mode < OFF ? OFF : mode + 1;
    }

    /** When a choice made at now ends: 0 for Off, Long.MAX_VALUE for Always. */
    static long until(int mode, long now, TimeZone tz) {
        switch (mode) {
            case HOUR: return now + 3_600_000L;
            case MORNING: {
                Calendar c = Calendar.getInstance(tz, Locale.UK);
                c.setTimeInMillis(now);
                c.set(Calendar.HOUR_OF_DAY, MORNING_HOUR);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                if (c.getTimeInMillis() <= now) c.add(Calendar.DAY_OF_MONTH, 1);
                return c.getTimeInMillis();
            }
            case ALWAYS: return Long.MAX_VALUE;
            default: return 0;
        }
    }

    /** Whether a choice ending at until is still on at now. */
    static boolean on(long until, long now) {
        return until > now;
    }

    /** The mode a saved choice is in now: Off once its time has passed. */
    static int mode(int saved, long until, long now) {
        return saved != OFF && on(until, now) ? saved : OFF;
    }

    /** The tile's line: "Off", "On until 21:45", "On until 7:00", "On until you turn it off". */
    static String words(int saved, long until, long now, TimeZone tz) {
        int m = mode(saved, until, now);
        if (m == OFF) return "Off";
        if (m == ALWAYS || until == Long.MAX_VALUE) return "On until you turn it off";
        Calendar c = Calendar.getInstance(tz, Locale.UK);
        c.setTimeInMillis(until);
        return String.format(Locale.UK, "On until %d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }
}
