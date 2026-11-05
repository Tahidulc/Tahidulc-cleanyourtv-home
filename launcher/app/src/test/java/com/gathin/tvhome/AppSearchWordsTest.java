package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** How the helper tells an app's search button and its profile picker, however they are worded. */
public class AppSearchWordsTest {
    @Test public void searchByNameOrId() {
        assertTrue(AppSearchService.isSearch("search", "", ""));
        assertTrue(AppSearchService.isSearch("", "search", ""));
        assertTrue(AppSearchService.isSearch("search movies & shows", "", ""));
        assertTrue(AppSearchService.isSearch("", "", "com.debridstream.tv:id/btn_search"));
        assertTrue(AppSearchService.isSearch("", "", "ar.tvplayer.tv:id/search"));
        // The voice search is not the search button.
        assertFalse(AppSearchService.isSearch("speak to search", "", ""));
        assertFalse(AppSearchService.isSearch("", "", "com.x:id/voice_search"));
        assertFalse(AppSearchService.isSearch("research", "", ""));
        assertFalse(AppSearchService.isSearch("", "", ""));
    }

    @Test public void profilePickerHoweverWorded() {
        assertTrue(AppSearchService.isProfilePage(AppSearchService.plain("Who’s watching?")));
        assertTrue(AppSearchService.isProfilePage(AppSearchService.plain("Who's Watching")));
        assertTrue(AppSearchService.isProfilePage(AppSearchService.plain("Choose a Profile")));
        assertTrue(AppSearchService.isProfilePage(AppSearchService.plain("Select profile")));
        assertTrue(AppSearchService.isProfilePage(AppSearchService.plain("Profiles")));
        assertTrue(AppSearchService.isProfilePage(AppSearchService.plain("Manage Profiles")));
        assertFalse(AppSearchService.isProfilePage(AppSearchService.plain("Continue watching")));
        assertFalse(AppSearchService.isProfilePage(AppSearchService.plain("Profile settings")));
        assertFalse(AppSearchService.isProfilePage(""));
    }

    @Test public void plainWords() {
        assertEquals("who's watching?", AppSearchService.plain("  Who’s Watching?  "));
        assertEquals("", AppSearchService.plain(null));
    }
}
