package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The over-the-channel overlays: only in the channel, never over Homefield TV or a player's guide or menus. */
public class OverlayScopeTest {
    @Test
    public void neverOverHomefieldTv() {
        assertFalse(OverlayScope.isPlayer(OverlayScope.HOMEFIELD));
        assertFalse(OverlayScope.inChannel("com.gathin.homefieldtv", false));
        assertFalse(BoardCover.isPlayer("com.gathin.homefieldtv"));
        for (String p : BoardCover.PLAYERS) assertFalse(OverlayScope.HOMEFIELD.equals(p));
    }

    @Test
    public void overTheChannelInTheAppsTheyAreMadeFor() {
        assertTrue(OverlayScope.inChannel("ar.tvplayer.tv", false));
        assertTrue(OverlayScope.inChannel("com.plaintextdev.klevaTv", false));
        assertEquals(2, OverlayScope.PLAYERS.length);
    }

    @Test
    public void notOverAGuideOrMenu() {
        assertFalse(OverlayScope.inChannel("ar.tvplayer.tv", true));
    }

    @Test
    public void notOverAnythingElseOrWhatCannotBeTold() {
        assertFalse(OverlayScope.inChannel(null, false));
        assertFalse(OverlayScope.inChannel("com.gathin.tvhome", false));
        assertFalse(OverlayScope.inChannel("com.netflix.ninja", false));
        assertFalse(OverlayScope.inChannel("com.android.tv.settings", false));
    }

    @Test
    public void theNewerReadingOfThePlayersScreenCounts() {
        assertFalse("never read", OverlayScope.menuUp(0, 0));
        assertTrue("the guide read last", OverlayScope.menuUp(2_000, 1_000));
        assertFalse("back on the channel since", OverlayScope.menuUp(2_000, 3_000));
        assertFalse(OverlayScope.menuUp(0, 5_000));
    }

    @Test
    public void aGuideIsTheScreenWithManyWords() {
        assertTrue(AppSearchService.listLike(40));
        assertFalse(AppSearchService.listLike(12));
    }
}
