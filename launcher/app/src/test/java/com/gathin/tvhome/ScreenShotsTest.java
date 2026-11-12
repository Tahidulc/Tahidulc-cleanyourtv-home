package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pictures of TiviMate's window alone: a hitch pauses them a while, it never turns them off for good. */
public class ScreenShotsTest {
    @Test
    public void oneMissedWindowPictureIsTriedAgainAtOnce() {
        ScreenShots.WindowPause p = new ScreenShots.WindowPause();
        assertTrue(p.on(0));
        // Android gave up on one (TiviMate busy a moment): the next is of the window again.
        p.missed(1_000);
        assertTrue(p.on(1_350));
        p.missed(1_350);
        assertTrue(p.on(1_700));
        // A good one in between: the count starts again.
        p.good();
        assertEquals(0, p.misses());
        p.missed(2_000);
        p.missed(2_350);
        assertTrue(p.on(2_700));
    }

    @Test
    public void threeRunningPauseThenTheWindowIsTriedAgain() {
        ScreenShots.WindowPause p = new ScreenShots.WindowPause();
        p.missed(1_000);
        p.missed(1_350);
        p.missed(1_700);
        // The whole screen for 30 s.
        assertFalse(p.on(1_701));
        assertFalse(p.on(1_700 + ScreenShots.WindowPause.FIRST - 1));
        assertTrue(p.on(1_700 + ScreenShots.WindowPause.FIRST));
        // Missing again straight after: twice as long, then longer, never more than five minutes.
        long t = 1_700 + ScreenShots.WindowPause.FIRST;
        p.missed(t);
        p.missed(t + 350);
        p.missed(t + 700);
        assertFalse(p.on(t + 700 + 2 * ScreenShots.WindowPause.FIRST - 1));
        assertTrue(p.on(t + 700 + 2 * ScreenShots.WindowPause.FIRST));
        long at = t + 700;
        for (int i = 0; i < 10; i++) {
            at = at + ScreenShots.WindowPause.LONGEST;
            p.missed(at);
            p.missed(at);
            p.missed(at);
        }
        assertFalse(p.on(at + ScreenShots.WindowPause.LONGEST - 1));
        assertTrue(p.on(at + ScreenShots.WindowPause.LONGEST));
        // A good one: the next pause is the short one again.
        p.good();
        at += ScreenShots.WindowPause.LONGEST;
        p.missed(at);
        p.missed(at);
        p.missed(at);
        assertTrue(p.on(at + ScreenShots.WindowPause.FIRST));
    }
}
