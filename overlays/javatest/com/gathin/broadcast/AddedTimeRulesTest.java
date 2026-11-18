package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Added time's one rule for every look on both boards: the tab or the panel, where the tab stands, never lost. */
public class AddedTimeRulesTest {
    @Test
    public void theTabOrThePanelNeverBoth() {
        assertEquals("", AddedTime.boardAdded("+4", true));
        assertEquals("+4", AddedTime.boardAdded("+4", false));
        assertEquals("", AddedTime.boardAdded(null, false));
        assertTrue(AddedTime.stands(true, false, true, 2, 2));
        assertFalse(AddedTime.stands(true, true, false, 2, 2));
        assertFalse(AddedTime.stands(false, false, false, 2, 2));
        assertFalse(AddedTime.stands(true, false, true, 3, 2));
    }

    @Test
    public void aLookThatCannotWriteThemByItsClockKeepsTheTab() {
        // Room for itself beside the tab: every look has it.
        assertTrue(AddedTime.outside(true, 240, 60, 60, AddedTime.LEAST));
        assertTrue(AddedTime.outside(false, 240, 60, 60, AddedTime.LEAST));
        // Kept narrow: the board's own looks write them by the clock; a competition's keeps its tab.
        assertFalse(AddedTime.outside(true, 90, 60, 60, AddedTime.LEAST));
        assertTrue(AddedTime.outside(false, 90, 60, 60, AddedTime.LEAST));
        // Card keeps more room for itself.
        assertFalse(AddedTime.outside(true, 150, 60, 60, AddedTime.LEAST_CARD));
        // No minutes, no tab.
        assertFalse(AddedTime.outside(false, 240, 0, 60, AddedTime.LEAST));
        assertTrue(AddedTime.drawsInside(null));
        assertFalse(AddedTime.drawsInside(CompBug.Kind.TICKER));
    }

    @Test
    public void theTabStandsBesideTheMainRow() {
        // A one-row look: 0.8 of it across the middle; a tall one: half of it on its foot.
        assertArrayEquals(new float[]{6f, 48f}, AddedTime.band(null, false, 60f), 0.01f);
        assertArrayEquals(new float[]{51f, 51f}, AddedTime.band(null, true, 102f), 0.01f);
        // The Premier League's world feed: beside its row of clubs, not across its clock's tab under it.
        float h = 60f * CompBug.Kind.PL.tall;
        float[] row = CompBug.bar(CompBug.Kind.PL, h), band = AddedTime.band(CompBug.Kind.PL, true, h);
        assertEquals((row[1] - row[0]) * 0.8f, band[1], 0.01f);
        assertTrue(band[0] >= row[0] && band[0] + band[1] <= row[1]);
        // LaLiga, two rows with no main row of its own: a row's tab beside its foot row, not one as tall as the board.
        float lh = 60f * CompBug.Kind.LALIGA.tall;
        float[] lb = AddedTime.band(CompBug.Kind.LALIGA, true, lh);
        assertEquals(48f, lb[1], 0.01f);
        assertEquals(lh - 54f, lb[0], 0.01f);
        // A one-row competition look: across its middle.
        assertArrayEquals(new float[]{6f, 48f}, AddedTime.band(CompBug.Kind.UCL, false, 60f), 0.01f);
    }
}
