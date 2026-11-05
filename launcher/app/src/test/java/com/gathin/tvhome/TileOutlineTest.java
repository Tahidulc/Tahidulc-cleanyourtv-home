package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * PagesV2.Tile.outlineWidth on lite_home: the rounded outline a trailer clips to is made at the width the tile shows,
 * so an opened poster with its trailer playing is shown whole, not cut back to the narrow poster it was.
 */
public class TileOutlineTest {

    @Test
    public void wholeTileWhenNothingIsCut() {
        assertEquals(560, PagesV2.Tile.outlineWidth(-1f, 560));
    }

    @Test
    public void theShownWidthWhileItOpens() {
        assertEquals(212, PagesV2.Tile.outlineWidth(211.6f, 560));
    }

    @Test
    public void neverWiderThanTheTile() {
        assertEquals(560, PagesV2.Tile.outlineWidth(900f, 560));
    }

    @Test
    public void anOpenedPosterIsShownWhole() {
        // The poster opened (vis follows its width): the outline is the whole opened width, not the poster's 190 px.
        assertEquals(560, PagesV2.Tile.outlineWidth(560f, 560));
    }
}
