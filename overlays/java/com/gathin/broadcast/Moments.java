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

    /** Where the film ends in the intro's music: the flash into the trophy (40 s). */
    public static final double INTRO_FILM_END = 40.0;

    /**
     * Pure: where in the intro's music a film of this length starts (seconds), so that it ends on the flash into the
     * trophy: 0 for one of 40 seconds or longer (cut there).
     */
    public static double introStart(long filmMs) {
        if (filmMs <= 0) return 0;
        double s = INTRO_FILM_END - filmMs / 1000.0;
        return s <= 0 ? 0 : Math.round(s * 100) / 100.0;
    }

    /** Pure: whether a programme starting at startMs is due its intro now: four minutes before it, or up to a minute into it. */
    public static boolean programmeDue(long startMs, long now) {
        long until = startMs - now;
        return startMs > 0 && until <= INTRO_FROM_MS && until >= -60_000L;
    }

    /**
     * Pure: the intro a programme's title calls for: "ufc", "boxing" or "f1" (a race day, not practice or qualifying);
     * null for anything else (football has its own way in, by its game), and for highlights and replays.
     */
    public static String sportOf(String title) {
        if (title == null) return null;
        String t = " " + title.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ") + " ";
        if (t.contains(" highlights ") || t.contains(" replay ") || t.contains(" review ") || t.contains(" classic ")
                || t.contains(" rewind ") || t.contains(" preview ")) return null;
        if (t.contains(" ufc ")) return "ufc";
        if (t.contains(" boxing ") || t.contains(" box office ")) return "boxing";
        if (t.contains(" f1 ") || t.contains(" formula 1 ") || t.contains(" formula one ")) {
            for (String not : new String[]{" practice ", " qualifying ", " shootout ", " press conference ", " notebook ", " debrief ", " show "}) {
                if (t.contains(not)) return null;
            }
            return "f1";
        }
        return null;
    }

    /** Pure: a programme's title as the line under the wordmark: "Live UFC 323: Dvalishvili v Yan" to "UFC 323: Dvalishvili v Yan". */
    public static String eventLine(String title) {
        if (title == null) return "";
        String s = title.trim().replaceFirst("(?i)^live\\s*[:\\-]?\\s*", "");
        return s.length() > 48 ? s.substring(0, 48).trim() : s;
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
