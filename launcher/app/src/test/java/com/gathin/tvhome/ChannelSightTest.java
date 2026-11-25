package com.gathin.tvhome;

import com.gathin.broadcast.SportsMark;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** The channel's own scoreboard seen on the TV: its clock read, the places over it, the logo's patch, the wordmark. */
public class ChannelSightTest {
    @Test
    public void theGamesClockIsReadNotAnAddedTimeClockOrTheTimeOfDay() {
        // The channel's clock, near the scores' own minute (the stream a little behind).
        assertEquals(66 * 60 + 41, ChannelSight.clockOf(Collections.singletonList("ARS 2-1 EVE 66:41"), 67));
        // Beside it, the added time's own clock: never taken for the game's.
        assertEquals(45 * 60, ChannelSight.clockOf(Arrays.asList("45:00", "+2:15"), 46));
        assertEquals(-1, ChannelSight.clockOf(Collections.singletonList("+2:15"), 46));
        // A time of day, far from the game's minute: not it.
        assertEquals(-1, ChannelSight.clockOf(Collections.singletonList("20:31"), 67));
        // Letters the reader mistakes for digits.
        assertEquals(12 * 60 + 5, ChannelSight.clockOf(Collections.singletonList("I2:O5"), 13));
        // No minute known: the first clock on it.
        assertEquals(5 * 60 + 9, ChannelSight.clockOf(Collections.singletonList("05:09"), 0));
        assertEquals(-1, ChannelSight.clockOf(null, 10));
    }

    @Test
    public void boxesAreKeptAndReadBack() {
        assertArrayEquals(new float[]{0.04f, 0.05f, 0.3f, 0.1f}, ChannelSight.boxOf("0.0400,0.0500,0.3000,0.1000"), 1e-4f);
        assertNull(ChannelSight.boxOf(""));
        assertNull(ChannelSight.boxOf("0.5,0.5,0.4,0.6"));
        assertNull(ChannelSight.boxOf("a,b,c,d"));
        assertEquals("top left", ChannelSight.where(0.1f, 0.06f));
        assertEquals("top right", ChannelSight.where(0.9f, 0.06f));
        assertEquals("in the middle", ChannelSight.where(0.5f, 0.5f));
    }

    @Test
    public void theBoardIsPutOverWhatWasSeenALittleOverEachEdge() {
        BoardPlace was = BoardPlace.standard();
        float[] board = {0.03f, 0.04f, 0.33f, 0.09f}, logo = {0.86f, 0.04f, 0.96f, 0.1f};
        BoardPlace p = BoardPlace.over(was, board, logo, 1f, 1920f, 1080f);
        assertTrue(p.bx < 0.03f && p.by < 0.04f);
        // Its height the box's and a little; its width the box's and a little.
        assertEquals(0.05f * 1.16f, p.bh, 0.002f);
        assertTrue(p.bw > 0.3f && p.bw < 0.32f);
        assertTrue(p.lx < 0.86f && p.lx + p.lw > 0.96f);
        // A taller look (Card) set to the same height on the screen.
        BoardPlace card = BoardPlace.over(was, board, null, 1.6f, 1920f, 1080f);
        assertEquals(p.bh / 1.6f, card.bh, 0.002f);
        // No logo seen: the cover stays where it was.
        assertEquals(was.lx, card.lx, 1e-6f);
    }

    @Test
    public void theClockReadOffTheChannelCountsInItsOwnHalf() {
        long at = 2_000_000L;
        long[] a = BoardCover.anchorOf("g1|2830|" + at + "|1", "g1");
        assertNotNull(a);
        assertEquals(1, a[2]);
        // Read at 47:10 in the first half's added time: counts on there, in the first half.
        assertTrue(BoardCover.anchoredSecs(a, at + 20_000L, "45'+2'") > 2849);
        // The second half on: no longer.
        assertEquals(-1, BoardCover.anchoredSecs(a, at + 900_000L, "47'"), 0.0);
        assertEquals(1, BoardCover.periodOf("45'+2'"));
        assertEquals(2, BoardCover.periodOf("67'"));
        assertEquals(0, BoardCover.periodOf(""));
    }

    @Test
    public void theWordmarkSaysWhatIsOn() {
        assertEquals("premier league", SportsMark.auto("eng.1", false));
        assertEquals("football", SportsMark.auto("uefa.champions", false));
        assertEquals("football", SportsMark.auto(null, false));
        assertEquals("f1", SportsMark.auto("eng.1", true));
        assertEquals("Auto, from the game", SportsMark.label(""));
        assertEquals("", SportsMark.WHATS[0]);
        // Each its own colour.
        assertTrue(SportsMark.colourOf("premier league") != SportsMark.colourOf("football"));
        assertTrue(SportsMark.colourOf("main event") != SportsMark.colourOf("f1"));
    }
}
