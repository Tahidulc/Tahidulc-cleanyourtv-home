package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FoldTest {
    private static final float MIN = 150;

    @Test public void aFlickGoesItsOwnWay() {
        // Barely moved, but flicked up: folds; nearly folded, but flicked down: opens.
        assertTrue(Fold.settlesShut(40, 1000, 2400, MIN));
        assertFalse(Fold.settlesShut(960, 1000, -2400, MIN));
        assertTrue(Fold.settlesShut(500, 1000, MIN, MIN));
    }

    @Test public void letGoSlowlyTheNearerEnd() {
        assertTrue(Fold.settlesShut(700, 1000, 0, MIN));
        assertFalse(Fold.settlesShut(300, 1000, 80, MIN));
        assertTrue(Fold.settlesShut(501, 1000, -100, MIN));
        // Exactly half: open.
        assertFalse(Fold.settlesShut(500, 1000, 0, MIN));
        // Nothing to fold.
        assertFalse(Fold.settlesShut(0, 0, 5000, MIN));
    }

    @Test public void takesOnlyWhatIsLeft() {
        assertEquals(120, Fold.take(120, 0, 1000));
        assertEquals(50, Fold.take(120, 950, 1000));
        assertEquals(0, Fold.take(30, 1000, 1000));
        assertEquals(-80, Fold.take(-80, 500, 1000));
        assertEquals(-20, Fold.take(-80, 20, 1000));
        assertEquals(0, Fold.take(-10, 0, 1000));
        assertEquals(0, Fold.take(40, 0, 0));
    }

    @Test public void shutStaysShutAndOpenStaysOpenAsTheHeaderChanges() {
        // Folded, then the picture opened in it (taller): still folded, the picture too.
        assertEquals(1500, Fold.refold(1000, 1000, 1500, true));
        // Folded, then shorter (the details gone with the list): still folded.
        assertEquals(600, Fold.refold(1000, 1000, 600, true));
        // Just starting to open from shut when it grew: shut still, then opens all of it.
        assertEquals(1500, Fold.refold(1000, 1000, 1500, false));
        // Open stays open.
        assertEquals(0, Fold.refold(0, 1000, 1500, false));
        // Nothing left in it.
        assertEquals(0, Fold.refold(1000, 1000, 0, true));
    }

    @Test public void withNothingInItBeforeAsLastChosen() {
        // The first layout (or the header back after a search emptied it).
        assertEquals(800, Fold.refold(0, 0, 800, true));
        assertEquals(0, Fold.refold(0, 0, 800, false));
    }

    @Test public void partWayTheGuideStaysPut() {
        // 400 of 1000 showing; 500 more on top: still 400 showing.
        assertEquals(1100, Fold.refold(600, 1000, 1500, false));
        // Shorter than what showed: all of it shows.
        assertEquals(0, Fold.refold(200, 1000, 500, true));
    }

    @Test public void settlesInAMomentAsMaterialDoes() {
        assertEquals(150, Fold.settleMs(0, 1000));
        assertEquals(225, Fold.settleMs(-500, 1000));
        assertEquals(300, Fold.settleMs(1000, 1000));
        assertEquals(300, Fold.settleMs(4000, 1000));
        assertEquals(0, Fold.settleMs(10, 0));
    }
}
