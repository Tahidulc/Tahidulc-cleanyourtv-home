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

    // ---- the picture kept in sight (Pin) -------------------------------------------------------------------------

    /** The picture 600 tall over 900 of details and the rest, kept down to 300. */
    private static final int PIN = 600, HEAD = 900, KEEP = 300;

    @Test public void nothingPlayingNothingIsKept() {
        // Not kept: it folds away with the rest, as before (the whole of both).
        assertEquals(0, Fold.keep(false, PIN, KEEP));
        assertEquals(PIN + HEAD, Fold.range(PIN, HEAD, 0));
        assertEquals(0f, Fold.shrunk(Fold.pinShown(1200, PIN, HEAD, 0), PIN, 0), 0f);
        // No picture at all (gone), or nothing to shrink to: nothing kept either.
        assertEquals(0, Fold.keep(true, 0, KEEP));
        assertEquals(0, Fold.keep(true, PIN, 0));
        assertEquals(HEAD, Fold.range(0, HEAD, 0));
    }

    @Test public void keptItStopsAtItsSmallHeight() {
        assertEquals(KEEP, Fold.keep(true, PIN, KEEP));
        assertEquals(PIN + HEAD - KEEP, Fold.range(PIN, HEAD, KEEP));
        // Never more kept than there is of it.
        assertEquals(200, Fold.keep(true, 200, KEEP));
        assertEquals(0f, Fold.shrunk(200, 200, 200), 0f);
    }

    @Test public void theRestFoldsAwayBeneathItFirst() {
        // While the details and the rest fold away, all of the picture shows and it hasn't shrunk.
        for (int s : new int[]{0, 1, 450, 899, 900}) {
            assertEquals(PIN, Fold.pinShown(s, PIN, HEAD, KEEP));
            assertEquals(0f, Fold.shrunk(Fold.pinShown(s, PIN, HEAD, KEEP), PIN, KEEP), 0f);
        }
    }

    @Test public void thenItShrinksWithTheFingerToItsSmallSize() {
        // Past the rest: the guide comes up under it as it shrinks.
        assertEquals(450, Fold.pinShown(1050, PIN, HEAD, KEEP));
        assertEquals(0.5f, Fold.shrunk(450, PIN, KEEP), 1e-6f);
        assertEquals(KEEP, Fold.pinShown(1200, PIN, HEAD, KEEP));
        assertEquals(1f, Fold.shrunk(KEEP, PIN, KEEP), 0f);
        // Folded all it goes (or past, settling): no smaller.
        assertEquals(KEEP, Fold.pinShown(5000, PIN, HEAD, KEEP));
        assertEquals(1f, Fold.shrunk(Fold.pinShown(5000, PIN, HEAD, KEEP), PIN, KEEP), 0f);
    }

    @Test public void scrolledBackItGrowsAgainBeforeTheRestComesBack() {
        float small = Fold.shrunk(Fold.pinShown(1200, PIN, HEAD, KEEP), PIN, KEEP);
        float half = Fold.shrunk(Fold.pinShown(1050, PIN, HEAD, KEEP), PIN, KEEP);
        float whole = Fold.shrunk(Fold.pinShown(900, PIN, HEAD, KEEP), PIN, KEEP);
        assertTrue(small > half && half > whole);
        assertEquals(0f, whole, 0f);
        // All the way back: as it was.
        assertEquals(PIN, Fold.pinShown(0, PIN, HEAD, KEEP));
    }

    @Test public void withNothingUnderItItShrinksAtOnce() {
        // Your channels, Recently watched and the details all gone: the picture alone.
        assertEquals(PIN - KEEP, Fold.range(PIN, 0, KEEP));
        assertEquals(PIN, Fold.pinShown(0, PIN, 0, KEEP));
        assertEquals(450, Fold.pinShown(150, PIN, 0, KEEP));
    }

    @Test public void keptOrLetGoFoldedItStaysFolded() {
        // Folded with nothing kept, then a channel plays and the picture is kept: still folded, at its small size.
        int was = Fold.range(PIN, HEAD, 0), now = Fold.range(PIN, HEAD, KEEP);
        assertEquals(now, Fold.refold(was, was, now, true));
        assertEquals(KEEP, Fold.pinShown(now, PIN, HEAD, KEEP));
        // It stops playing (full screen took it, the tab left): folded still, the picture folded away too.
        assertEquals(was, Fold.refold(now, now, was, true));
        // Open stays open either way.
        assertEquals(0, Fold.refold(0, was, now, false));
    }
}
