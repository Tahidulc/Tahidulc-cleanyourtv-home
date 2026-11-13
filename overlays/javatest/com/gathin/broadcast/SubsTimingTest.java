package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A substitution beside the board as the picture shows it: its minute against the board's clock, the channel's own graphic. */
public class SubsTimingTest {

    @Test
    public void aMinuteIsHalfWayThroughIt() {
        // The 71st minute is the clock's 70:00 to 70:59.
        assertEquals(70 * 60 + 30, SubsTiming.secondsOf("71"));
        assertEquals(70 * 60 + 30, SubsTiming.secondsOf("71'"));
        assertEquals(92 * 60 + 30, SubsTiming.secondsOf("90+3"));
        assertEquals(92 * 60 + 30, SubsTiming.secondsOf("90'+3'"));
        assertEquals(-1, SubsTiming.secondsOf(""));
        assertEquals(-1, SubsTiming.secondsOf(null));
        assertEquals(70 * 60 + 41, SubsTiming.clockSeconds("70:41"));
        assertEquals(-1, SubsTiming.clockSeconds("HT"));
    }

    @Test
    public void itWaitsForThePictureToReachIt() {
        // Known at 69:50 on the board: not yet; at 70:30, out.
        assertFalse(SubsTiming.due("71", 69 * 60 + 50, false, 5_000L));
        assertTrue(SubsTiming.due("71", 70 * 60 + 30, false, 5_000L));
        // Known late: at once.
        assertTrue(SubsTiming.due("71", 72 * 60, false, 0L));
        // The channel has just shown one, or no clock to go by, or waited its longest: at once.
        assertTrue(SubsTiming.due("71", 60 * 60, true, 0L));
        assertTrue(SubsTiming.due("71", -1, false, 0L));
        assertTrue(SubsTiming.due("71", 60 * 60, false, SubsTiming.MOST_WAIT_MS));
    }

    @Test
    public void theChannelsGraphicForASubIsTold() {
        assertTrue(SubsTiming.onScreen("SUBSTITUTION\nGILMOUR"));
        assertTrue(SubsTiming.onScreen("SUB ADAMS"));
        assertTrue(SubsTiming.onScreen("ON MCGINN OFF CHRISTIE"));
        assertTrue(SubsTiming.onScreen("IN: DYKES\nOUT: ADAMS"));
        // A goal's, a card's, added time's: not a substitution.
        assertFalse(SubsTiming.onScreen("GOAL MCTOMINAY 23'"));
        assertFalse(SubsTiming.onScreen("YELLOW CARD TIERNEY"));
        assertFalse(SubsTiming.onScreen("+4 ADDED TIME"));
        assertFalse(SubsTiming.onScreen("ON TARGET 5"));
        assertFalse(SubsTiming.onScreen(null));
        // A surname that starts with the letters is not the word.
        assertFalse(SubsTiming.onScreen("SUBOTIC 12'"));
    }
}
