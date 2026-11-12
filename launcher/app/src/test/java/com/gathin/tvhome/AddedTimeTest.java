package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AddedTimeTest {
    @Test public void theMinutesAddedComeOutAsAnAlertInAddedTimesRed() {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "9";
        BoardDrop.Drop d = BoardCover.addedDrop(g, "+4");
        assertEquals("ADDED TIME", d.chip);
        assertEquals("+4 minutes", d.title);
        assertEquals(0xFFE3061B, d.colour);
        assertEquals("9", d.gameId);
        assertEquals("+1 minute", BoardCover.addedDrop(g, "+1").title);
        assertNull(BoardCover.addedDrop(g, "+?"));
        assertNull(BoardCover.addedDrop(g, "+0"));
    }

    @Test public void eachHalfByItsClock() {
        assertEquals(1, BoardCover.periodOf("44:30", ""));
        assertEquals(1, BoardCover.periodOf("47:10", "45'+2'"));
        assertEquals(2, BoardCover.periodOf("95:00", "90'+5'"));
        assertEquals(2, BoardCover.periodOf("91:05", "90'+1'"));
        assertEquals(3, BoardCover.periodOf("100:00", ""));
    }

    @Test public void espnsCommentaryFirstThenFotMobs() {
        assertArrayEquals(new int[]{2, 5, 0, 0}, BoardCover.mergeAdded(new int[]{2, 0, 0, 0}, new int[]{3, 5, 0, 0}));
        assertArrayEquals(new int[]{3, 0, 0, 0}, BoardCover.mergeAdded(null, new int[]{3, 0, 0, 0}));
        assertArrayEquals(new int[]{0, 4, 0, 0}, BoardCover.mergeAdded(new int[]{0, 4, 0, 0}, null));
    }

    @Test public void fotMobsAddedTimeByTheMinuteItIsPutUpAt() {
        assertEquals(1, FotMob.addedHalf(45));
        assertEquals(2, FotMob.addedHalf(90));
        assertEquals(3, FotMob.addedHalf(105));
        assertEquals(4, FotMob.addedHalf(120));
        assertEquals(0, FotMob.addedHalf(0));
        assertEquals(4, FotMob.minutes("+4"));
        assertEquals(0, FotMob.minutes("soon"));
    }

    @Test public void addedTimeIsRedOnlyWhileLiveInIt() {
        BoardBug.Look l = new BoardBug.Look();
        l.live = true;
        l.stoppage = true;
        assertTrue(BoardBug.pulses(l));
        l.live = false;
        assertFalse(BoardBug.pulses(l));
    }
}
