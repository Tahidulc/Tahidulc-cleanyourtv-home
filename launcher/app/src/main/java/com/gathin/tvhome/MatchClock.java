package com.gathin.tvhome;

/**
 * A live football game's clock as the broadcasters' clocks show it, from ESPN's. ESPN gives the minute being played,
 * in whole minutes ("46'" is 2760 from the moment the second half kicks off at 45:00, watched against a live game on
 * 7 November 2026), so each minute's clock starts a minute lower than ESPN's number; it is counted on from when that
 * minute was first seen, to the minute's end at most until ESPN gives the next. Pure, for the tests.
 */
final class MatchClock {
    private MatchClock() { }

    /**
     * ESPN's clock (seconds) as the start of the minute being played; in added time ("45'+2'") at the period's end at
     * least. A hair above nothing in the first minute, so it still reads as a clock; 0 when ESPN gives none.
     */
    static double minuteStart(double clock, String displayClock) {
        if (clock <= 0) return 0;
        double c = clock;
        if (c >= 60 && Math.abs(c - Math.rint(c / 60) * 60) < 0.001) c -= 60;
        String d = displayClock == null ? "" : displayClock;
        int plus = d.indexOf('+');
        if (plus > 0) {
            int first = 0;
            for (int i = 0; i < plus && Character.isDigit(d.charAt(i)); i++) first = first * 10 + (d.charAt(i) - '0');
            c = Math.max(c, first * 60.0);
        }
        return Math.max(0.001, c);
    }

    /** The clock in seconds at now: clockSec as first seen at clockAt, counted on to the minute's end at most. */
    static long secsNow(double clockSec, long clockAt, long now) {
        return (long) clockSec + Math.max(0, Math.min(59, (now - clockAt) / 1000L));
    }
}
