package com.gathin.tvhomephone.sports;

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

    /** {"67:23", ""} or {"45:00", "+2"}; null when the game is not running (before, at half time, after). */
    public static synchronized String[] show(Game g, long now) {
        if (g == null || g.state != Game.LIVE || g.halftime) return null;
        double secs = g.clock > 0 ? g.clock : Game.minute(g.detail) * 60.0;
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
        double secs = g.clock > 0 ? g.clock : Game.minute(g.detail) * 60.0;
        double[] seen = SEEN.get(g.id == null ? "" : g.id);
        long elapsed = seen != null && seen[0] == secs ? Math.max(0, Math.min(59, (now - (long) seen[1]) / 1000)) : 0;
        return (long) secs + elapsed;
    }

    /** The words for a clock of total seconds in a period (pure: for the tests). */
    static String[] show(double total, int period, String displayClock) {
        int end = period <= 1 ? 45 : period == 2 ? 90 : period == 3 ? 105 : 120;
        int whole = (int) total;
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
