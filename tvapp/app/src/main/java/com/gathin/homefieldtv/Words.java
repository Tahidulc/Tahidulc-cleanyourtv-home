package com.gathin.homefieldtv;

import com.gathin.homefieldtv.prefs.Look;

/**
 * The few words the screens write again on every frame they draw (the guide's times every half hour, the date and the
 * clock, a channel's number, a programme's times and minutes left), made once and kept, so a frame makes nothing new:
 *
 * <pre>
 *   Words.hm(t)              "17:40" (LiveText.hm, as Settings → Appearance → Programme times say)
 *   Words.span(a, b)         "17:40 — 18:45" (LiveText.span)
 *   Words.dateClock(now)     "Tue, 17 Nov, 10:45" (Ui.dateClock), made again each minute
 *   Words.number(n)          "105"
 *   Words.minutes(ms)        "16 min" (GuideBand.minutes)
 * </pre>
 *
 * Main thread only. Kept by the minute and the settings they were made with: a change in Settings makes them again.
 */
final class Words {
    private Words() {}

    private static final int N = 48;
    private static final long[] hmAt = new long[N], spanA = new long[N], spanB = new long[N];
    private static final String[] hmText = new String[N], spanText = new String[N];
    private static int hmNext, spanNext, madeFor = Integer.MIN_VALUE;
    private static long clockMinute = Long.MIN_VALUE;
    private static String clockText = "";
    private static final String[] NUMBERS = new String[10_000], MINUTES = new String[24 * 60 + 1];

    /** The settings the words are made with: when they change, everything is made again. */
    private static void fresh() {
        int k = (Look.times24 ? 1 : 0) | Look.clock << 1;
        if (k == madeFor) return;
        madeFor = k;
        java.util.Arrays.fill(hmText, null);
        java.util.Arrays.fill(spanText, null);
        clockMinute = Long.MIN_VALUE;
    }

    /** "17:40" for a time (to the minute). */
    static String hm(long t) {
        fresh();
        long m = Math.floorDiv(t, 60_000L);
        for (int i = 0; i < N; i++) if (hmText[i] != null && hmAt[i] == m) return hmText[i];
        String s = LiveText.hm(t);
        hmAt[hmNext] = m;
        hmText[hmNext] = s;
        hmNext = (hmNext + 1) % N;
        return s;
    }

    /** "17:40 — 18:45". */
    static String span(long a, long b) {
        fresh();
        for (int i = 0; i < N; i++) if (spanText[i] != null && spanA[i] == a && spanB[i] == b) return spanText[i];
        String s = LiveText.span(a, b);
        spanA[spanNext] = a;
        spanB[spanNext] = b;
        spanText[spanNext] = s;
        spanNext = (spanNext + 1) % N;
        return s;
    }

    /** "Tue, 17 Nov, 10:45": the date and the clock now, made again each minute. */
    static String dateClock(long now) {
        fresh();
        long m = Math.floorDiv(now, 60_000L);
        if (m != clockMinute) {
            clockMinute = m;
            clockText = Ui.dateClock(now);
        }
        return clockText;
    }

    private static long todayFrom = Long.MAX_VALUE, todayTo = Long.MIN_VALUE, dayFrom = Long.MAX_VALUE, dayTo = Long.MIN_VALUE, dayIndex;

    /** Whether t is on the same day as now, here (worked out once a day). */
    static boolean sameDay(long t, long now) {
        if (now < todayFrom || now >= todayTo) {
            long[] b = dayBounds(now);
            todayFrom = b[0];
            todayTo = b[1];
        }
        return t >= todayFrom && t < todayTo;
    }

    /** The day t is on, here, as a number (the same all day; worked out once a day). */
    static long dayIndex(long t) {
        if (t < dayFrom || t >= dayTo) {
            long[] b = dayBounds(t);
            dayFrom = b[0];
            dayTo = b[1];
            dayIndex = b[2];
        }
        return dayIndex;
    }

    /** Pure: the day t is on, here: {its first ms, the next day's first ms, its number}. */
    static long[] dayBounds(long t) {
        java.time.ZoneId z = java.time.ZoneId.systemDefault();
        java.time.LocalDate d = java.time.Instant.ofEpochMilli(t).atZone(z).toLocalDate();
        return new long[]{d.atStartOfDay(z).toInstant().toEpochMilli(), d.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli(),
                d.toEpochDay()};
    }

    /** A whole number's words (0 to 9,999 kept). */
    static String number(int n) {
        if (n < 0 || n >= NUMBERS.length) return String.valueOf(n);
        String s = NUMBERS[n];
        if (s == null) NUMBERS[n] = s = String.valueOf(n);
        return s;
    }

    /** "16 min": whole minutes, rounded up (at least 1), as the guide and the cards write what is left. */
    static String minutes(long ms) {
        long m = Math.max(1L, (ms + 59_999L) / 60_000L);
        if (m >= MINUTES.length) return m + " min";
        String s = MINUTES[(int) m];
        if (s == null) MINUTES[(int) m] = s = m + " min";
        return s;
    }
}
