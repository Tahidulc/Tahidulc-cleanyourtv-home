package com.gathin.homefieldtv;

import android.content.Context;
import android.os.Bundle;

import com.gathin.broadcast.BoardAlert;
import com.gathin.homefieldtv.engine.Exec;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * The family's alert switches for the scoreboard's own game (Settings › Notifications in the TV launcher), so one switch
 * controls both apps: what the board says beside itself (its own game's goal, red card, half and full time, kick-off and
 * line-ups, VAR) is held back as the launcher holds back its own (Over apps, each kind's switch, the competition's, Do not
 * disturb, Match mode's quiet, a film night's), read from the launcher's provider (SyncTokenProvider, call
 * BoardAlert.ALERT_SWITCHES, for Homefield TV and the launcher's own apps alone) off the main thread, as the board takes
 * a game and every READ_MS after while it has one. With no launcher to ask (none on this TV, or one from before it gave
 * them), the family's own as both TVs have them now: every kind on but Goal Rush and the live table (BoardAlert.byDefault).
 * Alerts the launcher hands the board (another game's) passed its switches there already.
 */
final class AlertSwitches {
    private AlertSwitches() {}

    /** How often they are read again while the board has a game. */
    static final long READ_MS = 60_000L;

    /** Who answers: the launcher's provider (a fake in the tests); null when no launcher answered. */
    static volatile BiFunction<Context, Bundle, Bundle> launcher = AlertSwitches::ask;

    private static volatile Map<String, Boolean> last;
    private static volatile String lastFor;
    private static volatile long readAt;

    private static Bundle ask(Context c, Bundle question) {
        try {
            return c.getApplicationContext().getContentResolver().call(android.net.Uri.parse(BoardAlert.LAUNCHER_SYNC),
                    BoardAlert.ALERT_SWITCHES, null, question);
        } catch (RuntimeException e) {
            // no launcher here, one from before it gave them, or not the family's own: the family's defaults
            return null;
        }
    }

    /** Pure: the switches in an answer (null for none). */
    static Map<String, Boolean> of(Bundle b) {
        if (b == null) return null;
        Map<String, Boolean> m = new HashMap<>();
        for (String k : BoardAlert.KINDS) if (b.containsKey(k)) m.put(k, b.getBoolean(k));
        return m.isEmpty() ? null : m;
    }

    /** Read again for game (its id and competition) when they are for another game or older than READ_MS (main thread). */
    static void refresh(Context c, String gameId, String slug) {
        long now = System.currentTimeMillis();
        String forGame = gameId == null ? "" : gameId;
        if (forGame.equals(lastFor) && now - readAt < READ_MS) return;
        lastFor = forGame;
        readAt = now;
        Context app = c.getApplicationContext();
        Bundle q = new Bundle();
        q.putString(BoardAlert.GAME, gameId);
        q.putString(BoardAlert.SLUG, slug);
        Exec.io(() -> {
            Map<String, Boolean> got = null;
            try {
                got = of(launcher.apply(app, q));
            } catch (RuntimeException ignored) {
                // the defaults, then
            }
            last = got;
        });
    }

    /** Whether the board's own alert of this kind may slide out now (BoardAlert.kindOfChip's kind; null: no switch of its own). */
    static boolean allowed(String kind) {
        return BoardAlert.allowed(last, kind);
    }

    /** Whether the launcher answered last time (the tests; the log). */
    static boolean fromLauncher() {
        return last != null;
    }

    /** Forgotten (the tests). */
    static void reset() {
        last = null;
        lastFor = null;
        readAt = 0;
    }
}
