package com.gathin.tvhomephone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The tab bar's order (a page slides in from the side its tab lies), the pages drawn in glass and those filling the window. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityTabsTest {
    @Test
    public void theTabsLieInTheBarsOrder() {
        assertEquals(0, MainActivity.place(R.id.tab_home));
        assertEquals(1, MainActivity.place(R.id.tab_sports));
        assertEquals(2, MainActivity.place(R.id.tab_live));
        assertEquals(3, MainActivity.place(R.id.tab_watch));
        assertEquals(4, MainActivity.place(R.id.tab_settings));
        assertEquals(-1, MainActivity.place(0));
    }

    @Test
    public void homeAndWatchAreTheGlassPages() {
        assertTrue(MainActivity.glassPage(R.id.tab_home));
        assertTrue(MainActivity.glassPage(R.id.tab_watch));
        assertFalse(MainActivity.glassPage(R.id.tab_sports));
        assertFalse(MainActivity.glassPage(R.id.tab_live));
        assertFalse(MainActivity.glassPage(R.id.tab_settings));
    }

    @Test
    public void liveTvFillsTheWindowWithTheGlassPages() {
        // Edge to edge, fitting themselves to the bars: no navy bands above and below Live TV's black.
        assertTrue(MainActivity.fillsWindow(R.id.tab_home));
        assertTrue(MainActivity.fillsWindow(R.id.tab_watch));
        assertTrue(MainActivity.fillsWindow(R.id.tab_live));
        assertFalse(MainActivity.fillsWindow(R.id.tab_sports));
        assertFalse(MainActivity.fillsWindow(R.id.tab_settings));
    }
}
