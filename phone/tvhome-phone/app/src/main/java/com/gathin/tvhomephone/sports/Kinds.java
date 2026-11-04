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
