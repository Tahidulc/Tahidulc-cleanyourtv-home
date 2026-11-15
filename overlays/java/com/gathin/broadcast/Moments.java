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
        return phase(pre, state, halftime, was, 0);
    }

    /**
     * Pure: as phase, and when the half cannot be told from what was seen (the board came on, or the app started, during
     * the game), the half the scores give (period: 1 the first, 2 the second, 3 and 4 extra time; 0 not known): so a goal's
     * restart pop-up still comes for a game joined part way through.
     */
    public static String phase(boolean pre, int state, boolean halftime, String was, int period) {
        if (pre || state == 1) return "pre";
        if (state == 2) return "ft";
        if (halftime) return "ht";
        if ("ht".equals(was) || "h2".equals(was)) return "h2";
        if ("pre".equals(was) || "h1".equals(was)) return "h1";
        if (state == 0 && period == 1) return "h1";
        if (state == 0 && period >= 2) return "h2";
        return "";
    }

    /**
     * Pure: whether the app's own scoreboard stays away for the classic pop-up, the same on the TV and the phone: while a
     * moment's pop-up is on (the kick-off, the restart after a goal, the second half: never on the screen with it), and,
     * once already away after a goal (its replays), while that goal's restart pop-up is still to come, so it never comes
     * back only to go again. Once it has gone the board comes back in, with its swish.
     */
    public static boolean boardAwayForPopup(boolean popupOn, boolean restartToCome, boolean boardAway) {
        return popupOn || boardAway && restartToCome;
    }

    /** Pure: the moments' pop-ups the board stays away for (the strap is the board's own look and folds into it). */
    public static boolean boardMoment(String kind) {
        return "kickoff".equals(kind) || "restart".equals(kind) || "second".equals(kind);
    }

    /** How long after the goal the restart's pop-up waits at most for live play (the replays to end): LivePlay's most. */
    public static final long RESTART_WAIT_MS = LivePlay.MOST_MS;

    /**
     * Pure: what the restart's pop-up does sinceGoal ms after the goal reached the picture, with the channel in its
     * replays (the board away) or not: 1 show it now, 0 wait a second more, -1 let it go (waited too long).
     */
    public static int restartNow(long sinceGoal, boolean inReplay) {
        return restartNow(sinceGoal, inReplay, LivePlay.BLIND);
    }

    /**
     * Pure: as restartNow, with what the picture says (LivePlay's verdict): live play back after the goal shows it then
     * (never sooner than LivePlay.FIRST_MS); still waiting (the celebrations, replays the channel's scoreboard stays up
     * in, a VAR check) holds it until RESTART_WAIT_MS; held its longest without play seen lets it go. With no pictures
     * to tell by (BLIND), as before: RESTART_MS after the goal, once the channel's scoreboard is back from a replay.
     */
    public static int restartNow(long sinceGoal, boolean inReplay, int verdict) {
        if (verdict == LivePlay.LATE) return -1;
        if (inReplay) return sinceGoal < RESTART_WAIT_MS ? 0 : -1;
        if (verdict == LivePlay.BLIND) return sinceGoal < RESTART_MS ? 0 : 1;
        if (sinceGoal < LivePlay.FIRST_MS) return 0;
        if (verdict == LivePlay.LIVE) return 1;
        return sinceGoal < RESTART_WAIT_MS ? 0 : -1;
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
     * The matchday intro, for a channel whose adverts have not brought one (the BBC's, which has none, or a break not seen):
     * twelve minutes before the kick-off on the screen, well before the line-ups, the walk-out and the anthems, not later
     * than nine; made ready out of sight from thirteen and a half. A break's intro in the twenty minutes before leaves it
     * out (matchdayWanted), and no intro comes within five minutes of another (introSpaced).
     */
    public static final long INTRO_FROM_MS = 12 * 60_000L, INTRO_UNTIL_MS = 9 * 60_000L, INTRO_PREP_MS = 810_000L;

    /** Pure: whether the intro is due this long before the kick-off. */
    public static boolean introDue(long untilKickoffMs) {
        return untilKickoffMs <= INTRO_FROM_MS && untilKickoffMs >= INTRO_UNTIL_MS;
    }

    /** The matchday intro is left out when an advert break has had one this lately: the channel's breaks bring them. */
    public static final long INTRO_AFTER_BREAK_MS = 20 * 60_000L;

    /** Pure: whether the matchday intro is wanted now, a break's intro for the game last shown at lastBreakAt (0: none). */
    public static boolean matchdayWanted(long lastBreakAt, long now) {
        return lastBreakAt <= 0 || now < lastBreakAt || now - lastBreakAt >= INTRO_AFTER_BREAK_MS;
    }

    /** No intro starts within this of the last one's start (a break straight after the matchday intro has the ad-break screen). */
    public static final long INTRO_GAP_MS = 5 * 60_000L;

    /** Pure: whether an intro may start now, the game's last (of any kind) having started at lastAt (0: none). */
    public static boolean introSpaced(long lastAt, long now) {
        return lastAt <= 0 || now < lastAt || now - lastAt >= INTRO_GAP_MS;
    }

    /**
     * A sport's programme (UFC, boxing, F1's race): its intro as its titles, from four minutes before its start, not later
     * than 100 seconds before (the race's) or a minute into it (a programme's).
     */
    public static final long PROGRAMME_FROM_MS = 240_000L, PROGRAMME_UNTIL_MS = 100_000L;

    /** Pure: whether F1's race, starting this far off, is due its intro. */
    public static boolean raceIntroDue(long untilStartMs) {
        return untilStartMs <= PROGRAMME_FROM_MS && untilStartMs >= PROGRAMME_UNTIL_MS;
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
        return startMs > 0 && until <= PROGRAMME_FROM_MS && until >= -60_000L;
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

    /**
     * The intro over the advert breaks before the game (the one in its competition's style): every break that starts from
     * an hour and a half to eight minutes before the kick-off (none nearer it: the line-ups, the walk-out and the anthems
     * are the programme's).
     */
    public static final long BREAKS_FROM_MS = 90 * 60_000L, BREAKS_UNTIL_MS = 8 * 60_000L;

    /** Pure: whether an advert break starting this long before the kick-off has the intros over it. */
    public static boolean breakIntrosDue(long untilKickoffMs) {
        return untilKickoffMs <= BREAKS_FROM_MS && untilKickoffMs >= BREAKS_UNTIL_MS;
    }

    /** The channel's logo is learnt from this long before the breaks' intros begin, so their first break is not missed. */
    public static final long BREAKS_LEARN_MS = 30 * 60_000L;

    /**
     * Pure: whether the channel's logo should be watched for the breaks' intros this long before the kick-off (whether
     * or not the ad-break screen is on): from half an hour before they begin to a minute after they end.
     */
    public static boolean breaksWatch(long untilKickoffMs) {
        return untilKickoffMs <= BREAKS_FROM_MS + BREAKS_LEARN_MS && untilKickoffMs >= BREAKS_UNTIL_MS - 60_000L;
    }

    /**
     * The match intro's settings (overlays/src/match: the game's clubs and players) take a while to gather: made from
     * twenty minutes before the breaks' intros begin until the kick-off, and again every twenty minutes for the line-ups
     * that come out an hour before.
     */
    public static final long MATCH_WARM_FROM_MS = BREAKS_FROM_MS + 20 * 60_000L, MATCH_REMAKE_MS = 20 * 60_000L;

    /** Pure: whether the match intro's settings should be ready this long before the kick-off. */
    public static boolean matchWarm(long untilKickoffMs) {
        return untilKickoffMs <= MATCH_WARM_FROM_MS && untilKickoffMs > -5 * 60_000L;
    }

    /**
     * Pure: the style of the n-th advert break's intro before a game (n from 0), one a break: the match intro first, then
     * in turn with the competition's own (a top-five league's season intro, else its film); in a Champions League game its
     * own style of the match intro and the network's in turn. match: the match intro is on and ready; other: the
     * competition's own is on. "ucl", "network", "other", or "" for none.
     */
    public static String breakStyle(int n, boolean match, boolean other, boolean ucl) {
        if (match && ucl) return n % 2 == 0 ? "ucl" : "network";
        if (match && (!other || n % 2 == 0)) return "network";
        return other ? "other" : "";
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
