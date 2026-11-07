package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** MatchClock: ESPN's football clock as the broadcasters' clocks show it. */
public class MatchClockTest {

    @Test
    public void espnsMinuteBeingPlayedIsTheMinutesStart() {
        // Watched on a live game: the second half kicked off and ESPN said 2760 ("46'") at once, 2820 a minute later.
        assertEquals(2700, MatchClock.minuteStart(2760, "46'"), 0.01);
        assertEquals(2760, MatchClock.minuteStart(2820, "47'"), 0.01);
        // The first minute: a hair above nothing, so it still reads as a clock.
        assertEquals(0, MatchClock.minuteStart(60, "1'"), 0.01);
        assertTrue(MatchClock.minuteStart(60, "1'") > 0);
        // A clock with seconds is taken as it is; none is none.
        assertEquals(4012.5, MatchClock.minuteStart(4012.5, "67'"), 0.01);
        assertEquals(0, MatchClock.minuteStart(0, ""), 0.01);
        // Added time: at the period's end at least, however ESPN counts it.
        assertEquals(2700, MatchClock.minuteStart(2700, "45'+2'"), 0.01);
        assertEquals(2760, MatchClock.minuteStart(2820, "45'+2'"), 0.01);
        assertEquals(5400, MatchClock.minuteStart(5400, "90'+4'"), 0.01);
    }

    @Test
    public void theClockCountsOnFromWhenItWasFirstSeenToTheMinutesEnd() {
        long seen = 1_000_000L;
        assertEquals(2700, MatchClock.secsNow(2700, seen, seen));
        assertEquals(2723, MatchClock.secsNow(2700, seen, seen + 23_400));
        // ESPN's next minute not in yet: held at the minute's end, never past it.
        assertEquals(2759, MatchClock.secsNow(2700, seen, seen + 75_000));
        // A read before it was seen (a clock put right): not before the minute's start.
        assertEquals(2700, MatchClock.secsNow(2700, seen, seen - 5_000));
    }
}
