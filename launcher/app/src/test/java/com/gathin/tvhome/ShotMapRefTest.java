package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The shot map waits for the stats' turn at half and full time; the referee's alert says who and where from. */
public class ShotMapRefTest {
    @Test public void shotMapAfterTheStats() {
        assertNull(BoardCover.mapSlot(true, 0, false));
        assertEquals("ht", BoardCover.mapSlot(true, 0, true));
        assertEquals("ft", BoardCover.mapSlot(false, 2, true));
        assertNull(BoardCover.mapSlot(false, 0, true));
    }

    @Test public void refereeAlert() {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "1";
        BoardDrop.Drop d = BoardCover.refereeDrop(g, "Oleksiy Derevinskyi", "Ukraine");
        assertEquals("REFEREE", d.chip);
        assertEquals("Oleksiy Derevinskyi", d.title);
        assertEquals("Ukraine", d.line);
    }
}
