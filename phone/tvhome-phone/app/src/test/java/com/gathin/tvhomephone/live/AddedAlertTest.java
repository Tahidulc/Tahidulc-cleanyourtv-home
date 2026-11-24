package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

public class AddedAlertTest {
    @Test public void theMinutesAddedComeOutAsAnAlertInAddedTimesRed() {
        Game g = new Game();
        g.id = "9";
        ScoreBoard.Drop d = ScoreBoard.addedDrop(g, "+4");
        assertEquals("ADDED TIME", d.chip);
        assertEquals("+4 minutes", d.title);
        assertEquals(com.gathin.broadcast.AddedTime.RED, d.colour);
        assertEquals(4, d.addedMins);
        assertEquals("+1 minute", ScoreBoard.addedDrop(g, "+1").title);
        assertNull(ScoreBoard.addedDrop(g, "+?"));
        assertNull(ScoreBoard.addedDrop(g, "0"));
    }

    @Test public void addedTimeIsOneRedOnTheBoardAndBesideIt() {
        // Card's middle in added time, the +N tab on every look and the ADDED TIME pop-out: one colour from one place.
        int red = com.gathin.broadcast.AddedTime.RED;
        assertEquals(red, OverlayBug.CARD_RED);
        assertEquals(red, OverlayBug.TAB_RED);
        Game g = new Game();
        g.id = "9";
        assertEquals(red, ScoreBoard.addedDrop(g, "+3").colour);
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

    @Test public void theMinutesAddedCountUpAsTheyComeOut() {
        long start = BoardSide.SLIDE_MS / 2;
        assertEquals(1, BoardDrop.countUp(4, start));
        assertEquals(2, BoardDrop.countUp(4, start + BoardDrop.COUNT_STEP_MS));
        assertEquals(4, BoardDrop.countUp(4, 60_000L));
        assertEquals(4, BoardDrop.countUp(4, -1));
    }

    @Test public void theTabsLineFillsAsTheMinutesAddedRun() {
        assertEquals(0f, OverlayBug.addedRun("89:30", "+4"), 0.001f);
        assertEquals(0.5f, OverlayBug.addedRun("92:00", "+4"), 0.001f);
        assertEquals(1f, OverlayBug.addedRun("95:10", "+4"), 0.001f);
        assertEquals(0.25f, OverlayBug.addedRun("45:30", "+2"), 0.001f);
        assertEquals(-1f, OverlayBug.addedRun("30:00", "+2"), 0.001f);
        assertEquals(-1f, OverlayBug.addedRun("92:00", ""), 0.001f);
    }
}
