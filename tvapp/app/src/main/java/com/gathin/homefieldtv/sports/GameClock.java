package com.gathin.homefieldtv.sports;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A live game's clock as a scoreboard shows it, minutes and seconds ("67:23"), going on between ESPN's reads: ESPN
 * gives the clock in whole minutes, so the seconds count from when each new minute was first seen (never past 59). In
 * added time it stays at the period's end with the minutes added beside it ("45:00", "+2"), as broadcasters show it.
 */
public final class GameClock {
    private GameClock() {}

    /** Each game's clock as last read and when that value was first seen: {seconds, ms}. */
    private static final Map<String, double[]> SEEN = new HashMap<>();

    /**
     * Pure: ESPN's football clock as the clock on the screen has it. ESPN gives the minute being played, in whole minutes
     * ("46'" is 2760 from the moment the second half kicks off at 45:00, watched against a live game), so each minute's
     * clock starts a minute lower; in added time ("45'+2'") at the period's end at least, the minutes added beside it.
     * A hair above nothing in the first minute, so it still reads as a clock. 0 when ESPN gives none.
     */
    public static double minuteStart(double clock, String displayClock) {
        if (clock <= 0) return 0;
        double c = clock;
        if (c >= 60 && Math.abs(c - Math.rint(c / 60) * 60) < 0.001) c -= 60;
        if (added(displayClock) > 0) c = Math.max(c, firstMinute(displayClock) * 60.0);
        return Math.max(0.001, c);
    }

    /** The minute before any minutes added ("45'+2'": 45); 0 when none. */
    static int firstMinute(String displayClock) {
        if (displayClock == null) return 0;
        int n = 0;
        for (int i = 0; i < displayClock.length() && Character.isDigit(displayClock.charAt(i)); i++) n = n * 10 + (displayClock.charAt(i) - '0');
        return n;
    }

    /** The clock in seconds at the start of the minute a game's words give, when ESPN gave no clock ("67'": 66:00). */
    public static double fromWords(Game g) {
        int m = Game.minute(g.detail);
        return m <= 0 ? 0 : (m - 1) * 60.0;
    }

    /** {"67:23", ""} or {"45:00", "+2"}; null when the game is not running (before, at half time, after). */
    public static synchronized String[] show(Game g, long now) {
        if (g == null || g.state != Game.LIVE || g.halftime) return null;
        double secs = g.clock > 0 ? g.clock : fromWords(g);
        String key = g.id == null ? "" : g.id;
        double[] seen = SEEN.get(key);
        if (seen == null || seen[0] != secs) {
            seen = new double[]{secs, now};
            SEEN.put(key, seen);
        }
        long elapsed = Math.max(0, Math.min(59, (now - (long) seen[1]) / 1000));
        return show(secs + elapsed, g.period, g.displayClock);
    }

    /** The clock in seconds as it stands now (going on from ESPN's last minute); -1 when the game is not running. */
    public static synchronized long seconds(Game g, long now) {
        if (g == null || g.state != Game.LIVE || g.halftime) return -1;
        double secs = g.clock > 0 ? g.clock : fromWords(g);
        double[] seen = SEEN.get(g.id == null ? "" : g.id);
        long elapsed = seen != null && seen[0] == secs ? Math.max(0, Math.min(59, (now - (long) seen[1]) / 1000)) : 0;
        return (long) secs + elapsed;
    }

    /** The words for a clock of total seconds in a period (pure: for the tests). */
    static String[] show(double total, int period, String displayClock) {
        int whole = (int) total;
        // No period given: the one the clock is in, the minutes added taken off first ("45'+2'" is still the first half).
        if (period <= 0) {
            int plus = Math.max(0, added(displayClock)), base = whole - plus * 60;
            // In added time the base is the period's end itself (45:00 in "45'+2'"); otherwise past it is the next.
            int in = plus > 0 ? 1 : 0;
            period = base < 45 * 60 + in ? 1 : base < 90 * 60 + in ? 2 : base < 105 * 60 + in ? 3 : 4;
        }
        int end = period <= 1 ? 45 : period == 2 ? 90 : period == 3 ? 105 : 120;
        if (whole >= end * 60) {
            int added = added(displayClock);
            if (added <= 0) added = Math.max(1, (whole - end * 60) / 60 + 1);
            return new String[]{String.format(Locale.UK, "%02d:00", end), "+" + added};
        }
        return new String[]{String.format(Locale.UK, "%02d:%02d", whole / 60, whole % 60), ""};
    }

    /** The minutes added in "45'+2'"; 0 when none. */
    static int added(String displayClock) {
        if (displayClock == null) return 0;
        int plus = displayClock.indexOf('+');
        if (plus < 0) return 0;
        int n = 0;
        for (int i = plus + 1; i < displayClock.length() && Character.isDigit(displayClock.charAt(i)); i++) {
            n = n * 10 + (displayClock.charAt(i) - '0');
        }
        return n;
    }
}
