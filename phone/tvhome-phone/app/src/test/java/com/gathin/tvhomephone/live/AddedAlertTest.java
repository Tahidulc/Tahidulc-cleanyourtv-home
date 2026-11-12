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
        assertEquals(0xFFE3061B, d.colour);
        assertEquals("+1 minute", ScoreBoard.addedDrop(g, "+1").title);
        assertNull(ScoreBoard.addedDrop(g, "+?"));
        assertNull(ScoreBoard.addedDrop(g, "0"));
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
}
