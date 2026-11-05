package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The Control Centre's card keeps every tile inside it: the rows sized to fit, the card taller only when it must be. */
public class ControlCentreTest {
    private static final int AVAIL = 860 - ControlCentre.COLS_Y - ControlCentre.FOOT;

    @Test public void fewRowsKeepTheRoomySpacing() {
        // The controls and one section: six rows, as tall as fit.
        assertArrayEquals(new int[]{88, 22, 12, 16}, ControlCentre.fitRows(3, 6, AVAIL));
        assertArrayEquals(new int[]{96, 22, 12, 16}, ControlCentre.fitRows(2, 4, AVAIL));
    }

    @Test public void eightRowsFitWithTheHeadingsCloser() {
        // The controls (4 rows), Sport (2), Notifications and the other TV: the case that was cut off at the foot.
        int[] f = ControlCentre.fitRows(4, 8, AVAIL);
        assertArrayEquals(new int[]{66, 12, 8, 10}, f);
        assertTrue(ControlCentre.need(4, 8, f[0], f[1], f[2], f[3]) <= AVAIL);
        assertEquals(860, ControlCentre.cardHeight(4, 8));
    }

    @Test public void theCardGrowsOnlyWhenItMust() {
        assertEquals(860, ControlCentre.cardHeight(3, 6));
        int h = ControlCentre.cardHeight(5, 11);
        assertTrue(h > 860 && h <= ControlCentre.MAX_H);
        int[] f = ControlCentre.fitRows(5, 11, h - ControlCentre.COLS_Y - ControlCentre.FOOT);
        assertTrue(ControlCentre.need(5, 11, f[0], f[1], f[2], f[3]) <= h - ControlCentre.COLS_Y - ControlCentre.FOOT);
    }
}
