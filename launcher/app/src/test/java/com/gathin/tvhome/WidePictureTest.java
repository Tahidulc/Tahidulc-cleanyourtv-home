package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** An opened poster's wide picture follows how far the poster is open, never left hidden while it is open. */
public class WidePictureTest {
    @Test public void shutIsHiddenOpenIsWhole() {
        assertEquals(0f, PagesV2.PosterPage.wideAlpha(0f), 0.0001f);
        assertEquals(1f, PagesV2.PosterPage.wideAlpha(1f), 0.0001f);
    }

    @Test public void allThereBeforeTheOpeningEnds() {
        assertEquals(1f, PagesV2.PosterPage.wideAlpha(0.6f), 0.0001f);
        assertEquals(0.5f, PagesV2.PosterPage.wideAlpha(0.3f), 0.0001f);
    }

    @Test public void outOfRangeHeld() {
        assertEquals(0f, PagesV2.PosterPage.wideAlpha(-0.2f), 0.0001f);
        assertEquals(1f, PagesV2.PosterPage.wideAlpha(1.3f), 0.0001f);
    }
}
