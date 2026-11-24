package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The board back where the family put it after a pop-up (the strap folds into the place Move and resize set, at its
 * size, whether the board was up or away), and half time and full time as the whistle reaches the picture.
 */
public class BoardReturnTest {
    @Test public void theStrapFoldsIntoTheFamilysPlaceAndSize() {
        BoardPlace p = BoardPlace.standard();
        // moved to the right and down, made taller and wider than its words need
        p.bx = 0.62f;
        p.by = 0.08f;
        p.bh = 0.06f;
        p.bw = 0.3f;
        assertArrayEquals(new int[]{1190, 86, 576, 65}, BoardCover.placeOf(p, 1920, 1080, 1f, 7f));
        // its own width for its height (no width set), Stacked drawn taller
        p.bw = 0;
        assertArrayEquals(new int[]{1190, 86, 455, 65}, BoardCover.placeOf(p, 1920, 1080, 1f, 7f));
        assertEquals(104, BoardCover.placeOf(p, 1920, 1080, 1.6f, 4f)[3]);
        // kept on the screen
        p.bx = 0.95f;
        int[] r = BoardCover.placeOf(p, 1920, 1080, 1f, 7f);
        assertTrue(r[0] + r[2] <= 1920);
        assertNull(BoardCover.placeOf(null, 1920, 1080, 1f, 7f));
    }

    @Test public void aReadingWithItsHalfOver() {
        BoardCover.Reading r = new BoardCover.Reading(1000, "1", "0", 0, false, 2880, "48'");
        r.since = 900;
        BoardCover.Reading ht = BoardCover.over(r, false);
        assertTrue(ht.halftime);
        assertEquals(0, ht.state);
        assertEquals("1", ht.home);
        assertEquals(900, ht.since);
        BoardCover.Reading ft = BoardCover.over(r, true);
        assertFalse(ft.halftime);
        assertEquals(2, ft.state);
        assertEquals(com.gathin.broadcast.Whistle.AT_BREAK, BoardCover.phaseOf(0, true));
        assertEquals(com.gathin.broadcast.Whistle.AT_END, BoardCover.phaseOf(2, false));
        assertEquals(com.gathin.broadcast.Whistle.IN_PLAY, BoardCover.phaseOf(0, false));
        assertEquals(com.gathin.broadcast.Whistle.NOT_BEGUN, BoardCover.phaseOf(1, false));
    }
}
