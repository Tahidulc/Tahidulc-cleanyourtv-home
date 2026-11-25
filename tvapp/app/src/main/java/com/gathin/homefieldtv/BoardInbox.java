package com.gathin.homefieldtv;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.gathin.broadcast.BoardAlert;

/**
 * The TV launcher's football alerts for the scoreboard over Homefield TV's player (BoardLayer), as the launcher's own
 * board over TiviMate takes them (BoardCover.take): the launcher, the one source of the family's alerts, offers each one
 * here first while Homefield TV is in front (ChannelCardsProvider, call BoardAlert.BOARD_ALERT, behind the two apps'
 * signature permission), and shows no card of its own when it is taken. Taken only while the board is up over a channel
 * playing full screen and live with nothing over it (BoardGate), switched on with its alerts beside it, not being moved:
 * then another game's goal (or card, or full time) slides out beside the board in its look, queued with the board's own
 * (BoardLayer.drop); the board's own game's goals, cards, half and full time, kick-off and VAR are said by the board
 * itself from its own reading as the picture reaches it, so the launcher's is let go (BoardAlert.decide). Not taken (the
 * board away, resting, covered, off), the launcher shows its card over the app as it does over TiviMate's guide.
 *
 * <p>The board's state is kept here as it is drawn (main thread) and read on Binder's thread as the launcher asks; what is
 * taken goes on to the board on the main thread.
 */
final class BoardInbox {
    private BoardInbox() {}

    /** The board up to take an alert, its game (null: none), and whether its full time is late in its minute. */
    private static volatile boolean up, ftLate;
    private static volatile String game;
    private static volatile BoardLayer board;
    private static final Handler main = new Handler(Looper.getMainLooper());

    /** The board's state as it is drawn now (main thread): up to take alerts or not, its game, its full time late. */
    static void state(BoardLayer b, boolean takes, String gameId, boolean late) {
        board = takes ? b : null;
        up = takes;
        game = gameId;
        ftLate = late;
    }

    /** The board gone (the live screen away): nothing taken until it is back. */
    static void gone(BoardLayer b) {
        if (board == b || board == null) {
            board = null;
            up = false;
            game = null;
        }
    }

    /** Whether the board takes alerts now (the tests; the launcher's answer). */
    static boolean takes() {
        return up && board != null;
    }

    /** An alert the launcher offers (Binder's thread): the answer at once; what is taken goes on to the board. */
    static Bundle offer(Bundle extras) {
        BoardAlert a = BoardAlert.from(extras);
        if (a == null) return BoardAlert.answer(false, "nothing to say");
        BoardLayer b = board;
        boolean paged = a.pages != null && a.pages.length > 0;
        int what = BoardAlert.decide(up && b != null, game, a.gameId, a.kind, paged, ftLate);
        if (what == BoardAlert.NOT_UP) return BoardAlert.answer(false, b == null || !up ? "the scoreboard isn't up over the channel" : "full time is nearly over");
        if (what == BoardAlert.SAID) return BoardAlert.answer(true, "the scoreboard says its own game's itself");
        main.post(() -> b.fromLauncher(a));
        return BoardAlert.answer(true, "out of the scoreboard");
    }
}
