package com.gathin.tvhomephone.glass;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** A tablet's sizing (Wide): its type, the floating bar, a centred column and how many go across. */
public class WideTest {
    @Test public void typeIsALittleLargerAndNeverHuge() {
        assertEquals(1.1f, Wide.fontScale(1f), 1e-4f);
        assertEquals(1.1f * 1.15f, Wide.fontScale(1.15f), 1e-4f);
        // Past the cap only when the family chose larger text themselves.
        assertEquals(1.3f, Wide.fontScale(1.2f), 1e-4f);
        assertEquals(1.5f, Wide.fontScale(1.5f), 1e-4f);
        assertEquals(1.1f, Wide.fontScale(0f), 1e-4f);
    }

    @Test public void theBarIsNoWiderThanItNeeds() {
        // 1280dp on its side at 1.5x: 560dp; a narrow window: the width less its margins.
        assertEquals(840, Wide.barWidth(1920, 840, 36));
        assertEquals(828, Wide.barWidth(900, 840, 36));
        assertEquals(0, Wide.barWidth(40, 840, 36));
    }

    @Test public void aColumnSitsInTheMiddle() {
        assertEquals(300, Wide.side(1280, 680, 32));
        assertEquals(60, Wide.side(800, 680, 32));
        // Narrower than the column: the least room either side.
        assertEquals(32, Wide.side(600, 680, 32));
    }

    @Test public void moreAcrossOnAWiderScreen() {
        assertEquals(4, Wide.across(580, 140, 0, 4, 10));
        assertEquals(5, Wide.across(780, 140, 0, 4, 10));
        assertEquals(9, Wide.across(1260, 140, 0, 4, 10));
        assertEquals(10, Wide.across(3000, 140, 0, 4, 10));
        assertEquals(4, Wide.across(200, 140, 0, 4, 10));
        assertEquals(3, Wide.across(500, 140, 20, 1, 9));
    }
}
