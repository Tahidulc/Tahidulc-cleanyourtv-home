package com.gathin.homefieldtv;

import com.gathin.broadcast.VarCheck;

/**
 * "Checking VAR" out beside Homefield TV's board, ended the way the launcher's and the phone's boards end it (VarCheck):
 * out once the picture reaches the check; off at the first of its decision (it turns into it), the game going on past it
 * as the picture reaches that (the commentary's next line after the check, FotMob flagging the check and then no longer,
 * another event of the game: a card, a substitution, a goal or the score changing on the board, half or full time), or
 * VarCheck.CAP_MS; never before VarCheck.LEAST_MS, so it is read. Homefield TV does not read its own picture, so the
 * main camera's wide shot and the channel's scoreboard coming back (the phone's and the launcher's) are not among them.
 *
 * <p>Pure, on the main thread: the board asks each second, the tests replay made-up checks through it.
 */
final class BoardVar {
    /** When the check came out (wall clock; 0: none out), for which game; whether the game has gone on past it. */
    private long outAt;
    private String game;
    private boolean moved, flagged;

    /** A check came out beside the board at now. */
    void out(String gameId, long now) {
        outAt = Math.max(1, now);
        game = gameId;
        moved = false;
        flagged = false;
    }

    /** Whether a check is out now (for game gameId: null, any). */
    boolean isOut(String gameId) {
        return outAt > 0 && (gameId == null || gameId.equals(game));
    }

    /** How long the check has been out at now (0: none out). */
    long since(long now) {
        return outAt > 0 ? Math.max(0, now - outAt) : 0;
    }

    /** The game has gone on past its check (as the picture has it): true when that game's check is out. */
    boolean movedOn(String gameId) {
        if (!isOut(gameId) || gameId == null) return false;
        moved = true;
        return true;
    }

    /** FotMob's read, as the picture reaches it: its flag on the check, then off again, is the game gone on past it. */
    void flag(String gameId, boolean pending) {
        if (!isOut(gameId) || gameId == null) return;
        if (pending) flagged = true;
        else if (flagged) moved = true;
    }

    /** Whether the check out comes off at now (decided: its decision is here, to turn into). */
    boolean over(long now, boolean decided) {
        return outAt > 0 && VarCheck.over(since(now), decided, moved, 0L, false);
    }

    /** The check gone (off, turned into its decision, or the board let its game go). */
    void off() {
        outAt = 0;
        game = null;
        moved = false;
        flagged = false;
    }
}
