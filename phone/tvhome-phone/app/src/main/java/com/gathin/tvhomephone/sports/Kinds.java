package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.List;

/**
 * The phone's notification switches, one per kind of alert, grouped as the TV's Control Centre groups them (its
 * NotifyKinds). Kept on this phone only, not synced; every kind is on until switched off here.
 */
public final class Kinds {
    private Kinds() {}

    public static final String FOOTBALL = "Football", SPORT = "F1 and other sport", NEWS = "News and weather", REMINDERS = "Reminders";
    public static final List<String> GROUPS = Arrays.asList(FOOTBALL, SPORT, NEWS, REMINDERS);

    /** {id, name, what it is, group}. */
    public static final String[][] ALL = {
            {"goal", "Goals", "Every goal in the games you follow", FOOTBALL},
            {"red", "Red cards", "A player sent off", FOOTBALL},
            {"kickoff", "Kick-off and line-ups", "Both starting elevens before kick-off, then kick-off", FOOTBALL},
            {"ht", "Half time", "The score and the scorers at the break", FOOTBALL},
            {"ft", "Full time", "The final score as a game ends", FOOTBALL},
            {"var", "VAR and penalties", "A check under way, its decision, a penalty missed", FOOTBALL},
            {"drama", "Late drama and comebacks", "A late equaliser or winner, a side back from two down", FOOTBALL},
            {"table", "Live table", "Where a goal moves a club in the league table", FOOTBALL},
            {"milestone", "Milestones", "Braces and hat-tricks", FOOTBALL},
            {"f1live", "F1 live", "Lights out for the race", SPORT},
            {"f1grid", "F1 starting grid", "The grid fifteen minutes before the race", SPORT},
            {"f1result", "F1 results", "The podium as the race ends", SPORT},
            {"weather", "Weather", "Rain on the way in Manchester, once a day at most", NEWS},
            {"reminder", "Your reminders", "Games and shows you asked about, 15 minutes before", REMINDERS},
    };

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("sports_kinds", Context.MODE_PRIVATE);
    }

    /** When a game's moments are sent: as soon as they're seen, or held back as the TV holds its cards back. */
    public static final long[] DELAYS = {0, 30_000L, 60_000L};
    public static final String[] DELAY_NAMES = {"As it happens", "30 seconds later", "1 minute later, as the TV"};
    /** The TV's own wait before its goal and result cards (GoalWatchService's DELAY_MS): the phone's default. */
    public static final long TV_DELAY_MS = 60_000L;

    /** How long a game's moments wait before they are sent (one of DELAYS). */
    public static long delayMs(Context c) {
        long d = prefs(c).getLong("delay_ms", TV_DELAY_MS);
        for (long x : DELAYS) if (x == d) return d;
        return TV_DELAY_MS;
    }

    public static void setDelay(Context c, long ms) {
        prefs(c).edit().putLong("delay_ms", ms).apply();
    }

    /**
     * Whether a kind is held back with the delay: what happens in a game, which the TV's picture shows a little later
     * (goals, cards, VAR, drama, half and full time, the live table, milestones). Kick-off and line-ups, F1, the
     * weather and reminders come at once. Pure.
     */
    public static boolean waits(String kind) {
        switch (kind == null ? "" : kind) {
            case "goal": case "red": case "var": case "drama": case "ht": case "ft": case "table": case "milestone":
                return true;
            default:
                return false;
        }
    }

    public static boolean on(Context c, String kind) {
        return prefs(c).getBoolean(kind, true);
    }

    public static void set(Context c, String kind, boolean on) {
        prefs(c).edit().putBoolean(kind, on).apply();
    }

    public static String group(String kind) {
        for (String[] k : ALL) if (k[0].equals(kind)) return k[3];
        return FOOTBALL;
    }
}
