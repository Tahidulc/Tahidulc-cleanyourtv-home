package com.gathin.homefieldtv.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.Whistle;

import org.junit.Test;

/**
 * Half time and full time on Homefield TV's board as the whistle reaches the picture (BoardTime: Whistle, as the TV
 * launcher's board decides it from the data), replayed second by second through made-up halves (Northfield v Riverside):
 * the scores read every 20 seconds, ESPN some seconds behind the game, the channel's picture a minute behind it, FotMob's
 * own times for the half. Never early: not before the whistle is on the picture, nor before 45:00 or 90:00, nor (on the
 * channel's own clock) before the minutes added are up; and no longer counting on long after it.
 */
public class BoardTimeTest {
    /** The wall clock at the real kick-off of the half replayed. */
    private static final long K = 1_800_000_000_000L;
    private static final String ID = "770001";

    /** One half replayed: the game's clock on the real pitch, ESPN behind it, the channel's picture behind that. */
    private static final class Half {
        final BoardTime time = new BoardTime();
        /** 1 or 2; the clock at its kick-off (0 or 45:00); the whistle on the real pitch, in the game's seconds. */
        final int half;
        final long base, whistle;
        /** ESPN's read this far behind the game, the picture this far behind it, the wait set for the channel (s). */
        long espnLag, pictureLag = 60, wait = 60;
        /** The first wall time the board shows the half over (-1: not yet), and the clock it showed last before. */
        long overAt = -1;
        double lastClock = -1;
        String lastWords = "";
        boolean overIsFt;
        int[] added = {0, 0, 0, 0};
        long addedFrom = Long.MAX_VALUE;
        /** FotMob's read (its own length of the half) at this wall time, or never. */
        long fotmobAt = Long.MAX_VALUE;

        Half(int half, long whistle) {
            this.half = half;
            this.base = half == 1 ? 0 : 2700;
            this.whistle = whistle;
        }

        /** The channel's clock set at a kick-off on the screen, setLate seconds after the picture showed it. */
        Half anchored(long setLate) {
            time.anchor = new long[]{base, K + (pictureLag + setLate) * 1000L, half};
            return this;
        }

        /** The game's clock on the real pitch at wall time w. */
        double game(long w) {
            return base + (w - K) / 1000.0;
        }

        /** The clock on the channel's picture at wall time w. */
        double picture(long w) {
            return game(w) - pictureLag;
        }

        /** ESPN's read of the game as it was at game seconds s. */
        Game espn(double s) {
            Game g = new Game();
            g.id = ID;
            g.home = "NOR";
            g.away = "RIV";
            g.homeName = "Northfield";
            g.awayName = "Riverside";
            g.homeScore = 1;
            g.awayScore = 1;
            g.period = half;
            g.state = Game.LIVE;
            if (s >= whistle) {
                if (half == 1) {
                    g.halftime = true;
                    g.detail = "HT";
                } else {
                    g.state = Game.POST;
                    g.detail = "FT";
                }
                return g;
            }
            int m = (int) (s / 60) + 1, end = half == 1 ? 45 : 90;
            g.displayClock = m <= end ? m + "'" : end + "'+" + (m - end) + "'";
            g.detail = g.displayClock;
            g.clock = GameClock.minuteStart(m * 60.0, g.displayClock);
            return g;
        }

        /** The board each second from game second from to game second to, the scores read every 20 s. */
        Half run(long from, long to) {
            for (long w = K + (from - base) * 1000L; w <= K + (to - base) * 1000L; w += 1000L) {
                if ((w - K) % 20_000L == 0) time.took(espn(game(w) - espnLag), w);
                if (w == addedFrom) time.added(ID, added);
                if (w == fotmobAt) {
                    Whistle.Status st = new Whistle.Status();
                    st.period = half;
                    st.phase = half == 1 ? Whistle.AT_BREAK : Whistle.AT_END;
                    st.length[half] = whistle;
                    time.fotmob(w, ID, st);
                }
                BoardTime.Shown s = time.at(ID, w, wait * 1000L);
                if (s.r == null) continue;
                boolean over = s.r.halftime || s.r.state == Game.POST;
                if (over) {
                    if (overAt < 0) {
                        overAt = w;
                        overIsFt = s.r.state == Game.POST;
                    }
                } else if (overAt < 0 && s.secs >= 0) {
                    lastClock = s.secs;
                    lastWords = BoardClock.clockWords(s.secs);
                } else if (overAt >= 0 && s.r.state == Game.LIVE) {
                    throw new AssertionError("the half came back on after it was over, at " + picture(w));
                }
            }
            return this;
        }
    }

    @Test public void aFortyFivePlusThreeHalfEndingAtFortyFivePlusFourTen() {
        // The fourth official puts up 3; the referee blows at 49:10. ESPN flips half time 30 s after the whistle, the
        // channel's picture runs a minute behind the game, its clock set at the kick-off on the screen; FotMob's own times
        // give the half's length (49:10) 40 s after the whistle.
        Half h = new Half(1, 2950).anchored(0);
        h.espnLag = 30;
        h.added = new int[]{3, 0, 0, 0};
        h.addedFrom = K + 2760_000L;
        h.fotmobAt = K + 2990_000L;
        h.run(2400, 3200);
        assertTrue(h.overAt > 0);
        assertFalse(h.overIsFt);
        double onPicture = h.picture(h.overAt);
        // Half time as the whistle is on the picture (49:10), a second after at most: never before it.
        assertTrue("half time at " + onPicture, onPicture >= 2950 && onPicture <= 2952);
        // The clock ran to the whistle and no further: past the minutes added (48:00), up to 49:10.
        assertEquals("49:10", h.lastWords);
        assertTrue(h.lastClock >= 2700 + 3 * 60);
    }

    @Test public void withoutFotMobTheDatasLastClockStillBringsItWithinSeconds() {
        // The same half with no FotMob: the data's last clock in play and its flip a read later bound the half on the
        // channel's own clock (Whistle), so half time comes 10 s after the whistle on the picture, not when the flip itself
        // reaches the picture behind the wait (30 s after: 49:40).
        Half h = new Half(1, 2950).anchored(0);
        h.espnLag = 30;
        h.added = new int[]{3, 0, 0, 0};
        h.addedFrom = K + 2760_000L;
        h.run(2400, 3200);
        double onPicture = h.picture(h.overAt);
        assertTrue("half time at " + onPicture, onPicture >= 2950 && onPicture <= 2965);
        assertTrue(h.lastClock < 2965);
    }

    @Test public void dataEndingSixtySecondsBeforeTheStream() {
        // The second half ends at 95:30 on the pitch; ESPN has full time at once, the channel shows it a minute later (its
        // wait not set: the usual minute), its clock not set at kick-off. Full time comes as the picture reaches the whistle,
        // a read later at most, never before; the clock stops counting there.
        Half h = new Half(2, 5730);
        h.espnLag = 0;
        h.added = new int[]{2, 5, 0, 0};
        h.addedFrom = K + 2760_000L;
        h.run(5000, 6000);
        assertTrue(h.overAt > 0);
        assertTrue(h.overIsFt);
        double onPicture = h.picture(h.overAt);
        assertTrue("full time at " + onPicture, onPicture >= 5730 && onPicture <= 5730 + 20);
        // The board's clock never got more than a read past the whistle.
        assertTrue("clock ran to " + h.lastWords, h.lastClock <= 5730 + 20);
        assertTrue(h.lastClock >= 5400);

        // With FotMob's own length of the half read 30 s after the whistle: the board's clock stops at 95:31 at most.
        Half f = new Half(2, 5730);
        f.added = new int[]{2, 5, 0, 0};
        f.addedFrom = K + 2760_000L;
        f.fotmobAt = K + (3030 + 30) * 1000L;
        f.run(5000, 6000);
        assertTrue(f.picture(f.overAt) >= 5730);
        assertTrue("clock ran to " + f.lastWords, f.lastClock <= 5731);
    }

    @Test public void neverBeforeTheMinutesAddedOnTheChannelsOwnClock() {
        // The clock set 30 s late at the kick-off (the channel's own clock, 30 s behind it): the referee blows at exactly
        // 48:00 with 3 put up, ESPN flips at once. On the board's clock the whistle would be 47:30: the half is not over on
        // it before 48:00, and then it is.
        Half h = new Half(1, 2880).anchored(30);
        h.espnLag = 0;
        h.added = new int[]{3, 0, 0, 0};
        h.addedFrom = K + 2760_000L;
        h.run(2400, 3100);
        assertTrue(h.overAt > 0);
        double boardClock = h.picture(h.overAt) - 30;
        assertTrue("half time on the board's clock at " + boardClock, boardClock >= 2880 && boardClock <= 2882);
        assertEquals("47:59", h.lastWords);

        // Never before 45:00 either, whatever the data says.
        Half early = new Half(1, 2690).anchored(0);
        early.run(2400, 2900);
        assertTrue(early.picture(early.overAt) >= 2700 - 1);
    }

    @Test public void aClockSetFarOutIsHeldOnlySoLong() {
        // A clock set five minutes late: half time from the reading the screen reached is held for it two minutes at most.
        Half h = new Half(1, 2900).anchored(300);
        h.run(2400, 3300);
        long reached = K + (2900 + 20 + 60) * 1000L;
        assertTrue(h.overAt > 0);
        assertTrue(h.overAt - reached <= BoardTime.HOLD_MS + 20_000L);
    }

    @Test public void theReadsSayForTheWhistle() {
        Game g = new Game();
        g.state = Game.PRE;
        assertEquals(Whistle.NOT_BEGUN, BoardTime.phase(g));
        g.state = Game.LIVE;
        assertEquals(Whistle.IN_PLAY, BoardTime.phase(g));
        g.halftime = true;
        assertEquals(Whistle.AT_BREAK, BoardTime.phase(g));
        g.state = Game.POST;
        assertEquals(Whistle.AT_END, BoardTime.phase(g));
        g.period = 0;
        g.detail = "90'+3'";
        assertEquals(2, BoardTime.period(g));
        g.period = 1;
        assertEquals(1, BoardTime.period(g));
        // A reading's copy with its half over keeps its score and words.
        BoardClock.Reading r = new BoardClock.Reading(1000, 2, 1, Game.LIVE, false, 2880, "45'+4'");
        BoardClock.Reading ht = BoardClock.over(r, false);
        assertTrue(ht.halftime);
        assertEquals(Game.LIVE, ht.state);
        assertEquals(2, ht.home);
        BoardClock.Reading ft = BoardClock.over(r, true);
        assertEquals(Game.POST, ft.state);
        assertFalse(ft.halftime);
        assertNotNull(new BoardTime().at(ID, K, 60_000L));
    }
}
