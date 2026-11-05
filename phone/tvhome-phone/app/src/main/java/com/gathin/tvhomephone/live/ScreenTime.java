package com.gathin.tvhomephone.live;

import android.view.View;

import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.GameClock;
import com.gathin.tvhomephone.sports.LiveClock;
import com.gathin.tvhomephone.sports.OnScreen;

/**
 * The game as the picture shows it, for everything in the player that shows its score or clock: the channel's tile,
 * the small window and the board itself all read it here, so they agree with each other and with the channel's own
 * scoreboard (the clock it reads, the delay it measures). Without a board up for this game: the scores as they are.
 * Main thread.
 */
final class ScreenTime {
    private ScreenTime() {}

    /** The board's in-step game while it shows the channel's own game, and that game's id. */
    private static OnScreen screen;
    private static String gameId;

    static void set(OnScreen s, String id) {
        screen = s;
        gameId = id;
    }

    static void clear(OnScreen s) {
        if (screen == s) {
            screen = null;
            gameId = null;
        }
    }

    /** How far the picture runs behind the scores (0 when not known). */
    static long behindMs() {
        return screen == null ? 0 : screen.behind();
    }

    private static boolean mine(Game g) {
        return screen != null && g != null && g.id != null && g.id.equals(gameId) && screen.game(System.currentTimeMillis()) != null;
    }

    /** The game as the picture shows it now (its score as the channel's shows it). */
    static Game game(Game g, long now) {
        if (!mine(g)) return g;
        Game s = screen.game(now);
        return s == null ? g : s;
    }

    /** The clock as the picture shows it: {"67:23", ""} or {"45:00", "+2"}; null when not running. */
    static String[] clock(Game g, long now) {
        if (!mine(g)) return GameClock.show(g, now);
        return screen.clock(now);
    }

    /** The clock's words: "67:23", "45:00 +2", HT, FT; "" before kick-off. */
    static String words(Game g, long now) {
        Game s = game(g, now);
        if (s == null || s.state == Game.PRE) return "";
        if (s.state == Game.POST) return "FT";
        if (s.halftime) return "HT";
        String[] c = clock(g, now);
        if (c == null) return LiveClock.words(s, now);
        return c[1].isEmpty() ? c[0] : c[0] + " " + c[1];
    }

    /**
     * A board drawn for the game as the picture shows it, ticking four times a second while it is on the screen: the
     * tile's and the small window's, in the look chosen for the board.
     */
    static void bind(OverlayBug bug, Game g) {
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            bug.removeCallbacks(tick[0]);
            long now = System.currentTimeMillis();
            Game s = game(g, now);
            if (s != null) bug.set(ScoreBoard.look(s, clock(g, now)));
            if (bug.isAttachedToWindow()) bug.postDelayed(tick[0], 250);
        };
        bug.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                tick[0].run();
            }

            @Override public void onViewDetachedFromWindow(View v) {
                v.removeCallbacks(tick[0]);
            }
        });
        tick[0].run();
    }
}
