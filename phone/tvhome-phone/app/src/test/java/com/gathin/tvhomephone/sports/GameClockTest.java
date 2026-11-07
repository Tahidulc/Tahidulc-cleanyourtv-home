package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class GameClockTest {
    private static Game live(double clock, int period, String display) {
        Game g = new Game();
        g.id = "7";
        g.state = Game.LIVE;
        g.clock = clock;
        g.period = period;
        g.displayClock = display;
        g.detail = display;
        return g;
    }

    @Test public void minutesAndSeconds() {
        assertArrayEquals(new String[]{"67:23", ""}, GameClock.show(67 * 60 + 23, 2, "67'"));
        assertArrayEquals(new String[]{"00:10", ""}, GameClock.show(10, 1, "1'"));
    }

    @Test public void addedTimeStaysAtTheEndWithTheMinutesAdded() {
        assertArrayEquals(new String[]{"45:00", "+2"}, GameClock.show(2820, 1, "45'+2'"));
        assertArrayEquals(new String[]{"90:00", "+4"}, GameClock.show(5520, 2, "90'+4'"));
        assertArrayEquals(new String[]{"45:00", "+1"}, GameClock.show(2700, 1, "45'"));
        assertEquals(12, GameClock.added("90'+12'"));
    }

    @Test public void secondsGoOnFromWhenTheMinuteWasFirstSeen() {
        Game g = live(39 * 60, 1, "39'");
        long t = 1_000_000L;
        assertArrayEquals(new String[]{"39:00", ""}, GameClock.show(g, t));
        assertArrayEquals(new String[]{"39:25", ""}, GameClock.show(g, t + 25_000));
        // Never past the minute's 59th second before ESPN's next.
        assertArrayEquals(new String[]{"39:59", ""}, GameClock.show(g, t + 300_000));
        Game next = live(40 * 60, 1, "40'");
        assertArrayEquals(new String[]{"40:00", ""}, GameClock.show(next, t + 301_000));
    }

    @Test public void notRunningAtHalfTimeOrBefore() {
        Game g = live(2700, 1, "HT");
        g.halftime = true;
        assertNull(GameClock.show(g, 0));
        g.halftime = false;
        g.state = Game.PRE;
        assertNull(GameClock.show(g, 0));
    }

    @Test public void noPeriodGivenIsWorkedOutFromTheClock() {
        org.junit.Assert.assertArrayEquals(new String[]{"63:00", ""}, GameClock.show(63 * 60, 0, ""));
        org.junit.Assert.assertArrayEquals(new String[]{"45:00", "+2"}, GameClock.show(47 * 60, 0, "45'+2'"));
        org.junit.Assert.assertArrayEquals(new String[]{"12:30", ""}, GameClock.show(12 * 60 + 30, 0, ""));
        org.junit.Assert.assertArrayEquals(new String[]{"90:00", "+4"}, GameClock.show(94 * 60, 0, "90'+4'"));
    }

    @Test public void espnsMinuteBeingPlayedIsTheMinutesStart() {
        // Watched on a live game: the second half kicked off and ESPN said 2760 ("46'") at once, 2820 a minute later.
        assertEquals(2700, GameClock.minuteStart(2760, "46'"), 0.01);
        assertEquals(2760, GameClock.minuteStart(2820, "47'"), 0.01);
        // The first minute: a hair above nothing, so it still reads as a clock.
        assertEquals(0, GameClock.minuteStart(60, "1'"), 0.01);
        assertTrue(GameClock.minuteStart(60, "1'") > 0);
        // A clock with seconds is taken as it is.
        assertEquals(4012.5, GameClock.minuteStart(4012.5, "67'"), 0.01);
        // Added time: at the period's end at least, however ESPN counts it.
        assertEquals(2700, GameClock.minuteStart(2700, "45'+2'"), 0.01);
        assertEquals(2760, GameClock.minuteStart(2820, "45'+2'"), 0.01);
        assertEquals(0, GameClock.minuteStart(0, ""), 0.01);
    }
}
