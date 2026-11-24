package com.gathin.broadcast;

import java.util.List;

/**
 * When "Checking VAR" comes off the board, the same rule on the TV and the phone. It came out as a check began (ESPN's
 * or FotMob's commentary, FotMob's VAR flag, the alert) and used to stay until its decision or three minutes: a quick
 * check that changes nothing often has no decision anywhere, so it stood long after play had restarted.
 *
 * <p>It now goes at the first of: its decision (turned into it, as before); the data moving on (the commentary's next
 * line after the check that is not VAR's, FotMob no longer flagging a check, another event of the game: a goal, a card,
 * a substitution); the picture back in live play (the main camera's wide shot held a while, once the check has been on
 * for a little, or the channel's own scoreboard back after it went for the replays); or CAP_MS at the most. Never before
 * LEAST_MS, so it is read.
 *
 * <p>Pure: both boards ask here, the tests too.
 */
public final class VarCheck {
    private VarCheck() { }

    /** Out at most this long, whatever else is known (was three minutes). */
    public static final long CAP_MS = 100_000L;
    /** Out at least this long. */
    public static final long LEAST_MS = 8_000L;
    /** The main camera's wide shot held this long is play again... */
    public static final long PLAY_MS = 8_000L;
    /** ...once the check has been on this long (the first wide shots are often the players waiting). */
    public static final long PLAY_AFTER_MS = 20_000L;
    /** A check ended without its decision: one from FotMob's flag alone is not put out again for this long. */
    public static final long AGAIN_MS = 120_000L;

    /**
     * Pure: whether the check out of the board comes off now. since: how long it has been out; decided: its decision has
     * come; movedOn: the data has gone on past it (the commentary's next line, FotMob's flag gone, another event of the
     * game); wideMs: how long the picture has held the main camera's wide shot now (0: not, or no pictures); bugBack: the
     * channel's own scoreboard came back after going in the check's replays.
     */
    public static boolean over(long since, boolean decided, boolean movedOn, long wideMs, boolean bugBack) {
        if (decided || since >= CAP_MS) return true;
        if (since < LEAST_MS) return false;
        if (movedOn || bugBack) return true;
        return since >= PLAY_AFTER_MS && wideMs >= PLAY_MS;
    }

    /**
     * Pure: whether a game's commentary ({text, minute, sequence} lines, oldest first) has gone on past its latest VAR
     * check: a line after the last "VAR checking" line that is not about VAR (play went on: a foul, a corner, a shot,
     * a substitution). False when there is no check, or only VAR's own lines after it (its decision comes as its own).
     */
    public static boolean movedOn(List<String[]> lines) {
        if (lines == null) return false;
        int check = -1;
        for (int i = 0; i < lines.size(); i++) {
            String t = text(lines.get(i));
            if (t.contains("VAR checking") || t.contains("VAR Check") || t.contains("VAR check")) check = i;
        }
        if (check < 0) return false;
        for (int i = check + 1; i < lines.size(); i++) {
            String t = text(lines.get(i));
            if (t.isEmpty() || t.contains("VAR")) continue;
            return true;
        }
        return false;
    }

    private static String text(String[] line) {
        return line == null || line.length == 0 || line[0] == null ? "" : line[0];
    }
}
