package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The helpers keep out of the player's way while the family moves through its guide and lists. */
public class NavigationPaceTest {

    @Test
    public void picturesWaitWhileTheRemoteIsPressed() {
        long now = 100_000L;
        assertTrue(ScreenShots.keysMoving(now, now - 200L));
        assertTrue(ScreenShots.keysMoving(now, now - 1_400L));
        assertFalse(ScreenShots.keysMoving(now, now - ScreenShots.KEYS_REST_MS));
        // No press yet, or a clock gone back: pictures as ever.
        assertFalse(ScreenShots.keysMoving(now, 0L));
        assertFalse(ScreenShots.keysMoving(now, now + 5_000L));
    }

    @Test
    public void aScreenOfManyWordsIsAListNotABanner() {
        assertFalse(AppSearchService.listLike(8));
        assertTrue(AppSearchService.listLike(60));
    }
}
