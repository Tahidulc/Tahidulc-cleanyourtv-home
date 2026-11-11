package com.gathin.broadcast;

import java.util.List;

/**
 * When the classic pop-up and line-ups come, the same on the TV and the phone, from the game as the picture shows it
 * (the app's scoreboard, its states: 0 live, 1 before kick-off, 2 full time).
 */
public final class Moments {
    private Moments() { }

    /** How long after a goal reaches the picture play starts again (the restart's pop-up then). */
    public static final long RESTART_MS = 40_000L;
    /** The line-ups come from this long before kick-off on the picture, never later than the last of these. */
    public static final long LINEUPS_FROM_MS = 7 * 60_000L, LINEUPS_UNTIL_MS = 100_000L;
    /** How often FotMob is asked again for line-ups it has not got yet. */
    public static final long RETRY_MS = 60_000L;

    /**
     * Pure: the game's phase as the picture shows it: "pre" before kick-off, "ht", "ft", or live in the half it is in
     * ("h1"; "h2" once half time has been seen); "" when the half cannot be told (the board came on during the game).
     */
    public static String phase(boolean pre, int state, boolean halftime, String was) {
        if (pre || state == 1) return "pre";
        if (state == 2) return "ft";
        if (halftime) return "ht";
        if ("ht".equals(was) || "h2".equals(was)) return "h2";
        if ("pre".equals(was) || "h1".equals(was)) return "h1";
        return "";
    }

    /** Pure: the pop-up a change of phase brings: the kick-off's ("kickoff") or the second half's ("second"); else null. */
    public static String moment(String was, String now) {
        if ("pre".equals(was) && "h1".equals(now)) return "kickoff";
        if ("ht".equals(was) && "h2".equals(now)) return "second";
        return null;
    }

    /** Pure: whether the line-ups are due, by the time left to kick-off on the picture. */
    public static boolean lineupsDue(long untilKickoffMs) {
        return untilKickoffMs <= LINEUPS_FROM_MS && untilKickoffMs >= LINEUPS_UNTIL_MS;
    }

    /**
     * The matchday intro: over the channel's last advert break before the kick-off, taken as four minutes before it on the
     * screen (after the classic line-ups, before the stadium line-ups and the walk-out), not later than 100 seconds before;
     * made ready out of sight from five and a half minutes before.
     */
    public static final long INTRO_FROM_MS = 240_000L, INTRO_UNTIL_MS = 100_000L, INTRO_PREP_MS = 330_000L;

    /** Pure: whether the intro is due this long before the kick-off. */
    public static boolean introDue(long untilKickoffMs) {
        return untilKickoffMs <= INTRO_FROM_MS && untilKickoffMs >= INTRO_UNTIL_MS;
    }

    /** Pure: whether the intro should be made ready now (shortly before it is due). */
    public static boolean introPrep(long untilKickoffMs) {
        return untilKickoffMs <= INTRO_PREP_MS && untilKickoffMs > INTRO_FROM_MS;
    }

    /** The stadium line-ups (the first design): at the walk-out, from three and a half minutes before to a minute before. */
    public static final long STADIUM_FROM_MS = 210_000L, STADIUM_UNTIL_MS = 60_000L;

    /** Pure: whether the line-ups in this style ("classic" or "stadium") are due this long before the kick-off. */
    public static boolean lineupsDue(String style, long untilKickoffMs) {
        if ("stadium".equals(style)) return untilKickoffMs <= STADIUM_FROM_MS && untilKickoffMs >= STADIUM_UNTIL_MS;
        return lineupsDue(untilKickoffMs);
    }

    /** Pure: a side's (1 home, 2 away) latest goal as the feeds list them ({key, scorer, minute, "home"/"away", kind}). */
    public static String[] lastGoal(List<String[]> goals, int side) {
        String want = side == 1 ? "home" : "away";
        String[] last = null;
        if (goals != null) for (String[] g : goals) if (g != null && g.length > 3 && want.equals(g[3])) last = g;
        return last;
    }

    /** Pure: the scorer as the restart's pop-up names them ("Havertz", "Gabriel (OG)" for an own goal). */
    public static String scorer(String[] goal) {
        if (goal == null || goal.length < 2 || goal[1] == null) return "";
        boolean og = goal.length > 4 && "og".equals(goal[4]);
        return og ? goal[1] + " (OG)" : goal[1];
    }
}
