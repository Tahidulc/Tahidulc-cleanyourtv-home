package com.gathin.tvhomephone.live;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** The guide's dark look: the tiles behind logos, the programmes' colours, the picture's tags and the flags. */
public class GuideLookTest {

    private static int[] fill(int w, int h, int colour) {
        int[] px = new int[w * h];
        Arrays.fill(px, colour);
        return px;
    }

    @Test
    public void aLogoWithItsOwnGroundKeepsIt() {
        // A blue square logo with white writing in the middle: the tile is its blue.
        int[] px = fill(8, 8, 0xFF4D7CFE);
        px[27] = px[28] = 0xFFFFFFFF;
        assertEquals(0xFF4D7CFE, GuideLook.tile(px, 8, 8));
    }

    @Test
    public void aDarkLogoOnNothingGetsAPaleTile() {
        // Navy writing on a see-through ground: a pale tile (light enough for the navy to show).
        int[] px = fill(8, 8, 0x00000000);
        for (int i = 16; i < 48; i++) px[i] = 0xFF0E1A6B;
        int t = GuideLook.tile(px, 8, 8);
        int lum = (((t >> 16) & 0xFF) * 299 + ((t >> 8) & 0xFF) * 587 + (t & 0xFF) * 114) / 1000;
        assertTrue("pale: " + Integer.toHexString(t), lum > 150);
    }

    @Test
    public void aWhiteLogoOnNothingGetsADarkTile() {
        int[] px = fill(8, 8, 0x00000000);
        for (int i = 16; i < 48; i++) px[i] = 0xFFFFFFFF;
        assertEquals(GuideLook.DARK_GREY, GuideLook.tile(px, 8, 8));
    }

    @Test
    public void aProgrammeKeepsItsColour() {
        assertEquals(GuideLook.blockColour("Match of the Day"), GuideLook.blockColour("match of the day "));
        boolean varied = false;
        for (String t : new String[]{"Breakfast", "Newsnight", "EastEnders", "The One Show"}) {
            varied |= GuideLook.blockColour(t) != GuideLook.blockColour("Breakfast");
        }
        assertTrue(varied);
    }

    @Test
    public void thePicturesTagFromTheName() {
        assertEquals("4K", GuideLook.quality("Sky Sports Main Event 4K"));
        assertEquals("UHD", GuideLook.quality("UK: BBC One UHD"));
        assertEquals("FHD", GuideLook.quality("Arte FHD"));
        assertEquals("HD", GuideLook.quality("ZDF HD"));
        assertEquals("", GuideLook.quality("Gold"));
        assertEquals("BBC ONE", GuideLook.shortName("UK: BBC One HD"));
        assertEquals("SKY SPORTS MAIN EVENT", GuideLook.shortName("Sky Sports Main Event 4K"));
    }

    @Test
    public void aCountrysFlagWhereTheCategoryNamesOne() {
        assertEquals(GuideLook.emoji("GB"), GuideLook.flag("UK | Sports"));
        assertEquals(GuideLook.emoji("DE"), GuideLook.flag("DE: Movies"));
        assertEquals(GuideLook.emoji("CA"), GuideLook.flag("Canada"));
        assertEquals(GuideLook.emoji("BD"), GuideLook.flag("Bangla Channels"));
        assertEquals(GuideLook.emoji("GB"), GuideLook.flag("United Kingdom"));
        // Words that aren't countries.
        assertEquals("", GuideLook.flag("IT Crowd box sets".toLowerCase()));
        assertEquals("", GuideLook.flag("Sports"));
        assertEquals("", GuideLook.flag(""));
        assertNotEquals("", GuideLook.chip("Spain", "Spain"));
        assertEquals("News", GuideLook.chip("News", "News"));
    }
}
