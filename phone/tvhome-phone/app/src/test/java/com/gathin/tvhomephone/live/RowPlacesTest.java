package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** A channel in several qualities keeps the place of its first version in the playlist (5.33's channels out of order). */
public class RowPlacesTest {
    @Test public void aMergedChannelStandsWhereItsFirstVersionStands() {
        // 0 Alpha SD, 1 Bravo, 2 Alpha FHD (Alpha's row is its FHD), 3 Charlie.
        int[][] fam = new int[4][];
        fam[0] = fam[2] = new int[]{2, 0};
        boolean[] rows = {false, true, true, true};
        assertArrayEquals(new int[]{2, 1, 3}, Library.rowPlaces(4, fam, rows, new boolean[4]));
    }

    @Test public void itsRowHiddenTheNextVersionStandsInItsPlace() {
        int[][] fam = new int[3][];
        fam[0] = fam[2] = new int[]{2, 0};
        boolean[] rows = {true, true, false};
        // The FHD hidden by itself: the SD is the row, at the first place.
        assertArrayEquals(new int[]{0, 1}, Library.rowPlaces(3, fam, rows, new boolean[]{false, false, true}));
    }

    @Test public void unmergedChannelsAsThePlaylistHasThemHiddenLeftOut() {
        assertArrayEquals(new int[]{0, 2}, Library.rowPlaces(3, new int[3][], new boolean[]{true, true, true},
                new boolean[]{false, true, false}));
    }
}
