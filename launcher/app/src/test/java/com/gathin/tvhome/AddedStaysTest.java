package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The minutes added stay out beside the board for the rest of their half, and go in once it is over. */
public class AddedStaysTest {
    @Test public void stayOutUntilTheHalfIsOver() {
        assertTrue(BoardCover.addedStands(0, false, true, 1, 1));
        assertTrue(BoardCover.addedStands(0, false, true, 2, 2));
        assertFalse(BoardCover.addedStands(0, true, true, 1, 1));   // half time
        assertFalse(BoardCover.addedStands(2, false, false, 2, 2)); // full time
        assertFalse(BoardCover.addedStands(0, false, true, 2, 1));  // the second half begun
        assertFalse(BoardCover.addedStands(0, false, false, 1, 1)); // not live on the board
        assertTrue(BoardCover.addedStands(0, false, true, 3, 3));   // extra time's first half
    }
}
