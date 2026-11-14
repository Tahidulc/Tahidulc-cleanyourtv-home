package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.sports.Game;
import org.junit.Test;

/** The minutes added stay out beside the board for the rest of their half, and go in once it is over. */
public class AddedStaysTest {
    @Test public void stayOutUntilTheHalfIsOver() {
        assertTrue(ScoreBoard.addedStands(Game.LIVE, false, true, 1, 1));
        assertTrue(ScoreBoard.addedStands(Game.LIVE, false, true, 2, 2));
        assertFalse(ScoreBoard.addedStands(Game.LIVE, true, true, 1, 1));   // half time
        assertFalse(ScoreBoard.addedStands(Game.POST, false, false, 2, 2)); // full time
        assertFalse(ScoreBoard.addedStands(Game.LIVE, false, true, 2, 1));  // the second half begun
        assertFalse(ScoreBoard.addedStands(Game.LIVE, false, false, 1, 1)); // not live on the board
        assertTrue(ScoreBoard.addedStands(Game.LIVE, false, true, 3, 3));   // extra time's first half
    }
}
