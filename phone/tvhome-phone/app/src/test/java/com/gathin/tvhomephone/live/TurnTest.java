package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Turning the phone: steady readings, a band either side, and a turn only after it was seen upright. */
public class TurnTest {
    @Test public void anglesReadAsUprightSidewaysOrNeither() {
        assertEquals(Turn.UPRIGHT, Turn.read(0));
        assertEquals(Turn.UPRIGHT, Turn.read(20));
        assertEquals(Turn.UPRIGHT, Turn.read(345));
        assertEquals(Turn.SIDEWAYS, Turn.read(90));
        assertEquals(Turn.SIDEWAYS, Turn.read(270));
        assertEquals(Turn.SIDEWAYS, Turn.read(65));
        assertEquals(Turn.NONE, Turn.read(45));
        assertEquals(Turn.NONE, Turn.read(180));
        // Flat on a table.
        assertEquals(Turn.NONE, Turn.read(-1));
    }

    @Test public void aTurnCountsOnceItHoldsAfterBeingUpright() {
        Turn t = new Turn();
        assertEquals(Turn.NONE, t.feed(5, 0));
        assertEquals(Turn.NONE, t.feed(2, 200));
        assertEquals(Turn.UPRIGHT, t.feed(0, 500));
        // Said once.
        assertEquals(Turn.NONE, t.feed(0, 900));
        // On its side: not at once, then once it has held.
        assertEquals(Turn.NONE, t.feed(88, 1000));
        assertEquals(Turn.NONE, t.feed(91, 1300));
        assertEquals(Turn.SIDEWAYS, t.feed(90, 1460));
        assertEquals(Turn.NONE, t.feed(270, 1600));
        // Back upright.
        assertEquals(Turn.NONE, t.feed(0, 2000));
        assertEquals(Turn.UPRIGHT, t.feed(0, 2500));
    }

    @Test public void aWobbleDoesNotCount() {
        Turn t = new Turn();
        t.feed(0, 0);
        assertEquals(Turn.UPRIGHT, t.feed(0, 500));
        // Tipped past the band for a moment, then back: no turn.
        assertEquals(Turn.NONE, t.feed(80, 600));
        assertEquals(Turn.NONE, t.feed(80, 800));
        assertEquals(Turn.NONE, t.feed(10, 900));
        assertEquals(Turn.NONE, t.feed(10, 1500));
        // In between the two bands for as long as you like: no turn.
        assertEquals(Turn.NONE, t.feed(45, 2000));
        assertEquals(Turn.NONE, t.feed(45, 4000));
    }

    @Test public void aPhoneAlreadyOnItsSideIsNotTurned() {
        Turn t = new Turn();
        // Lying down with it: on its side from the start.
        assertEquals(Turn.NONE, t.feed(90, 0));
        assertEquals(Turn.NONE, t.feed(90, 600));
        assertEquals(Turn.NONE, t.feed(90, 3000));
        // Sat up (upright), then turned: that counts.
        t.feed(0, 4000);
        assertEquals(Turn.UPRIGHT, t.feed(0, 4500));
        t.feed(90, 5000);
        assertEquals(Turn.SIDEWAYS, t.feed(90, 5500));
    }

    @Test public void flatOnATableStartsTheWaitAgain() {
        Turn t = new Turn();
        t.feed(0, 0);
        t.feed(0, 500);
        t.feed(90, 600);
        // Laid flat before it held: the wait starts again when it reads on its side once more.
        assertEquals(Turn.NONE, t.feed(-1, 900));
        assertEquals(Turn.NONE, t.feed(90, 1000));
        assertEquals(Turn.NONE, t.feed(90, 1200));
        assertEquals(Turn.SIDEWAYS, t.feed(90, 1450));
    }
}
