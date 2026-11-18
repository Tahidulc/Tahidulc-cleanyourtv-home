package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/**
 * The film pages' and Home's pure helpers: an artwork's colour (Cine.vivid), the page's shade of it, colours mixed as
 * a page fades from one to the next, and a Continue watching tile's line.
 */
public class CineLookTest {
    private static float[] hsv(int c) {
        float[] out = new float[3];
        Cine.hsv(c, out);
        return out;
    }

    @Test public void vividTakesTheMostColourfulHueNotTheMostPixels() {
        // Mostly a dark grey with a few strong red pixels: the page takes red, at the red's own strength.
        int[] px = new int[100];
        Arrays.fill(px, 0xFF202020);
        for (int i = 0; i < 12; i++) px[i] = 0xFFE02020;
        float[] h = hsv(Cine.vivid(px));
        assertTrue(h[0] < 4f || h[0] > 356f);
        assertEquals(hsv(0xFFE02020)[1], h[1], 0.02f);
        assertEquals(hsv(0xFFE02020)[2], h[2], 0.02f);
    }

    @Test public void vividOfAGreyPictureIsItsGrey() {
        assertEquals(0xFF808080, Cine.vivid(new int[]{0xFF808080, 0xFF808080, 0xFF808080}));
        // Black and white: their middle.
        assertEquals(0xFF7F7F7F, Cine.vivid(new int[]{0xFF000000, 0xFFFFFFFF}));
        // See-through pixels count for nothing; none left, no colour.
        assertEquals(0, Cine.vivid(new int[]{0x00FF0000, 0x10FF0000}));
        assertEquals(0, Cine.vivid(new int[0]));
    }

    @Test public void colourAndHsvRoundTrip() {
        for (int c : new int[]{0xFF102030, 0xFFFF8800, 0xFF00FF7F, 0xFF7F00FF, 0xFF808080, 0xFFE0206A}) {
            float[] h = hsv(c);
            int back = Cine.colour(h[0], h[1], h[2]);
            assertEquals(0xFF, back >>> 24);
            for (int shift = 0; shift <= 16; shift += 8) assertEquals((c >> shift) & 0xFF, (back >> shift) & 0xFF, 1);
        }
    }

    @Test public void aPagesShadeKeepsTheHueAtItsOwnStrength() {
        int art = 0xFF2E7DE0;
        float[] page = hsv(Cine.shade(art, 0.6f, 0.15f)), was = hsv(art);
        assertEquals(was[0], page[0], 4f);
        // No stronger than asked (the artwork's 0.79 held to 0.6), at the brightness asked.
        assertEquals(0.6f, page[1], 0.03f);
        assertEquals(0.15f, page[2], 0.01f);
        // A pale artwork keeps its paleness.
        assertEquals(hsv(0xFF8A9099)[1], hsv(Cine.shade(0xFF8A9099, 0.6f, 0.3f))[1], 0.05f);
        // Nothing to go on: the page's own ground.
        assertEquals(Cine.GROUND, Cine.shade(0, 0.6f, 0.15f));
    }

    @Test public void mixGoesBetweenTwoColoursAlphaToo() {
        assertEquals(0xFF000000, Cine.mix(0xFF000000, 0xFFFFFFFF, 0f));
        assertEquals(0xFFFFFFFF, Cine.mix(0xFF000000, 0xFFFFFFFF, 1f));
        assertEquals(0xFF808080, Cine.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0x80FF0000, Cine.mix(0x00FF0000, 0xFFFF0000, 0.5f));
        assertEquals(0xFF000000, Cine.mix(0xFF000000, 0xFFFFFFFF, -1f));
        assertEquals(0x40123456, Cine.alpha(0xFF123456, 0x40));
        assertEquals(0xFF123456, Cine.alpha(0x00123456, 300));
        assertEquals(0x00123456, Cine.alpha(0xFF123456, -5));
    }

    @Test public void continueWatchingLines() {
        long min = 60_000L;
        assertEquals("S2 E4 · 32m left", BrowseScreen.resumeLine("S2 E4 · Pilot", 16 * min, 48 * min));
        assertEquals("S2 E4 · 32m left", BrowseScreen.resumeLine("S2 E4  ·  Pilot", 16 * min, 48 * min));
        assertEquals("1h 15m left", BrowseScreen.resumeLine("", 52 * min, 127 * min));
        assertEquals("S1 E1", BrowseScreen.resumeLine("S1 E1", 0, 0));
        assertEquals("S1 E2", BrowseScreen.resumeLine("S1 E2 · Home", 50 * min, 48 * min));
        assertEquals("", BrowseScreen.resumeLine(null, 0, 3000));
        // Seconds from the end: still a minute left, never "0m".
        assertEquals("1m left", BrowseScreen.resumeLine("", 48 * min - 10_000L, 48 * min));
    }
}
