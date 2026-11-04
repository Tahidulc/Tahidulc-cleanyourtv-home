package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ScheduleTest {
    private static final long MIN = 60_000L, NOW = 1_800_000_000_000L;

    @Test public void wakesFifteenMinutesBeforeTheNextKickOff() {
        assertEquals(NOW + 45 * MIN, Schedule.next(Arrays.asList(NOW + 60 * MIN, NOW + 300 * MIN), NOW));
    }

    @Test public void nowWhenAGameIsInItsWindow() {
        assertEquals(NOW, Schedule.next(Collections.singletonList(NOW + 10 * MIN), NOW));
        assertEquals(NOW, Schedule.next(Collections.singletonList(NOW - 60 * MIN), NOW));
    }

    @Test public void nothingWhenAllAreOver() {
        assertEquals(-1, Schedule.next(Collections.singletonList(NOW - 200 * MIN), NOW));
        assertEquals(-1, Schedule.next(Collections.emptyList(), NOW));
    }

    @Test public void watchesALiveOrNearGameOnly() {
        Game g = new Game();
        g.state = Game.PRE;
        g.kickoff = NOW + 18 * MIN;
        assertTrue(Schedule.watchNow(Collections.singletonList(g), NOW));
        g.kickoff = NOW + 90 * MIN;
        assertFalse(Schedule.watchNow(Collections.singletonList(g), NOW));
        g.state = Game.LIVE;
        assertTrue(Schedule.watchNow(Collections.singletonList(g), NOW));
    }
}
