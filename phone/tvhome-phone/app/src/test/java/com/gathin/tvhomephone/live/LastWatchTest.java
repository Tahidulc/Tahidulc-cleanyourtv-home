package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** The Live TV tab opens on the category chosen last, while the playlist still has it. */
public class LastWatchTest {
    private final List<String> groups = Arrays.asList("UK | Sports", "UK | Entertainment", "Bangla");

    @Test public void theTabsOwnCategoriesAlwaysOpen() {
        assertTrue(LastWatch.known(Library.ALL, groups));
        assertTrue(LastWatch.known(Library.FAVOURITES, groups));
        assertTrue(LastWatch.known(Library.RECENT, groups));
        assertTrue(LastWatch.known(SportGuide.GROUP, groups));
        assertTrue(LastWatch.known(Library.ALL, null));
    }

    @Test public void aPlaylistGroupOpensWhileItIsThere() {
        assertTrue(LastWatch.known("UK | Sports", groups));
        assertFalse(LastWatch.known("US | News", groups));
        assertFalse(LastWatch.known("Bangla", null));
        assertFalse(LastWatch.known(null, groups));
    }
}
