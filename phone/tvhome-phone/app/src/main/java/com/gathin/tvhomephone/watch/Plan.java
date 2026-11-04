package com.gathin.tvhomephone.watch;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The evening's arithmetic (pure, for the tests): when something ends if started now, how that sits with bedtime,
 * the binge plan's finishing day, the Episode map's colour step, Coming up's day labels and Film night's start times.
 */
public final class Plan {
    private Plan() {}

    /** The rating's step on the Episode map's 5-step ramp, measured against the series' own range (a point at least). */
    public static int step(double rating, double lo, double hi) {
        if (hi - lo < 1) {
            double mid = (hi + lo) / 2;
            lo = mid - 0.5;
            hi = mid + 0.5;
        }
        int s = (int) Math.floor((rating - lo) / (hi - lo) * 5);
        return Math.max(0, Math.min(4, s));
    }

    public static final int[] RAMP = {0xFF34306B, 0xFF5B4A6A, 0xFF8A6862, 0xFFC08C4F, 0xFFF2B33D};

    /** Nights to watch n episodes at perNight a night. */
    public static int nights(int n, int perNight) {
        if (n <= 0) return 0;
        return (n + Math.max(1, perNight) - 1) / Math.max(1, perNight);
    }

    /** The day the last of n is watched, starting tonight. */
    public static LocalDate doneOn(LocalDate today, int n, int perNight) {
        int k = nights(n, perNight);
        return today.plusDays(Math.max(0, k - 1));
    }

    /** Minutes for the first k of these runtimes (45 for any unknown). */
    public static int minutes(List<Integer> runtimes, int k) {
        int sum = 0;
        for (int i = 0; i < Math.min(k, runtimes.size()); i++) {
            Integer r = runtimes.get(i);
            sum += r == null || r <= 0 ? 45 : r;
        }
        return sum;
    }

    /** Minutes from end to bedtime (both minutes after midnight; an end after midnight counts as late); negative = past bed. */
    public static int spare(int endMinutes, int bedMinutes) {
        int bed = bedMinutes < 12 * 60 ? bedMinutes + 24 * 60 : bedMinutes;
        return bed - endMinutes;
    }

    /** Minutes after midnight for a moment (ms), with a moment on the next day counted past 24:00. */
    public static int clock(long ms, long dayStartMs) {
        return (int) ((ms - dayStartMs) / 60_000L);
    }

    public static String hm(int minutesAfterMidnight) {
        int m = ((minutesAfterMidnight % (24 * 60)) + 24 * 60) % (24 * 60);
        return String.format(Locale.UK, "%02d:%02d", m / 60, m % 60);
    }

    /** "1h 46m", "52m". */
    public static String length(int minutes) {
        if (minutes <= 0) return "";
        return minutes >= 60 ? minutes / 60 + "h " + String.format(Locale.UK, "%02d", minutes % 60) + "m" : minutes + "m";
    }

    /** "TODAY", "TOMORROW", "THU 5". */
    public static String day(LocalDate d, LocalDate today) {
        if (d.equals(today)) return "TODAY";
        if (d.equals(today.plusDays(1))) return "TOMORROW";
        return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK).toUpperCase(Locale.UK) + " " + d.getDayOfMonth();
    }

    /** "Sat 7 Nov". */
    public static String date(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK) + " " + d.getDayOfMonth() + " "
                + d.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK);
    }

    /** Film night's four start times: the next half hours from now (ms each). */
    public static long[] starts(long nowMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(nowMs);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int m = c.get(Calendar.MINUTE);
        c.set(Calendar.MINUTE, m < 30 ? 30 : 0);
        if (m >= 30) c.add(Calendar.HOUR_OF_DAY, 1);
        long[] out = new long[4];
        for (int i = 0; i < 4; i++) {
            out[i] = c.getTimeInMillis();
            c.add(Calendar.MINUTE, 30);
        }
        return out;
    }
}
