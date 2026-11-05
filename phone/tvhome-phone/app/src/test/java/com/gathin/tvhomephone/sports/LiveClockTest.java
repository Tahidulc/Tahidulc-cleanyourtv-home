package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The clock's words wherever a score is shown, and when Android's own ticking clock can carry it. */
public class LiveClockTest {
    @Test public void theWordsAreMinutesAndSeconds() {
        assertEquals("67:23", LiveClock.join(new String[]{"67:23", ""}, "67'"));
        assertEquals("45:00 +2", LiveClock.join(new String[]{"45:00", "+2"}, "45'+2'"));
        assertEquals("HT", LiveClock.join(null, "HT"));
    }

    @Test public void beforeAndAfter() {
        Game g = new Game();
        g.state = Game.PRE;
        assertEquals("", LiveClock.words(g, 0));
        g.state = Game.POST;
        assertEquals("FT", LiveClock.words(g, 0));
        g.state = Game.LIVE;
        g.halftime = true;
        assertEquals("HT", LiveClock.words(g, 0));
    }

    @Test public void aLiveGameTicks() {
        Game g = new Game();
        g.id = "lc1";
        g.state = Game.LIVE;
        g.clock = 23 * 60;
        g.period = 1;
        long t = 50_000_000;
        assertEquals("23:00", LiveClock.words(g, t));
        assertEquals("23:17", LiveClock.words(g, t + 17_000));
    }

    @Test public void androidsClockOnlyUnderTheHour() {
        assertTrue(Notifier.chronometer(23 * 60));
        assertTrue(Notifier.chronometer(59 * 60));
        // From 59:30 the next redraw would find it showing "1:00:00".
        assertFalse(Notifier.chronometer(59 * 60 + 31));
        assertFalse(Notifier.chronometer(67 * 60));
        assertFalse(Notifier.chronometer(-1));
    }
}
