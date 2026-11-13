package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Channel logos made ready for their black tiles: boxes taken away, dark logos turned over, the rest left alone. */
public class LogoTileTest {
    private static final int W = 40, H = 20;

    /** A w by h logo: clear (or a box's colour) all round, a block of ink in the middle. */
    private static int[] logo(int ground, int ink) {
        int[] px = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) px[y * W + x] = x >= 10 && x < 30 && y >= 6 && y < 14 ? ink : ground;
        }
        return px;
    }

    private static int at(int[] px, int x, int y) {
        return px[y * W + x];
    }

    @Test
    public void blackWritingComesOutWhite() {
        int[] out = LogoTile.clean(logo(0x00000000, 0xFF000000), W, H);
        assertEquals(0xFFFFFFFF, at(out, 20, 10));
        // What was clear stays clear.
        assertEquals(0, at(out, 2, 2) >>> 24);
    }

    @Test
    public void aWhiteBoxRoundTheLogoIsTakenAway() {
        // A JPEG's white ground with black writing: the ground goes, the writing comes out white.
        int[] out = LogoTile.clean(logo(0xFFFFFFFF, 0xFF101010), W, H);
        assertEquals(0, at(out, 0, 0) >>> 24);
        assertEquals(0, at(out, 5, 10) >>> 24);
        assertEquals(0xFF, at(out, 20, 10) >>> 24);
        assertTrue(LogoTile.luminance(at(out, 20, 10)) > 0.8f);
        // A grey box too, and a box a JPEG left a little uneven.
        int[] grey = logo(0xFFC8C8C8, 0xFF3C3C3C);
        grey[3] = 0xFFC2C6C9;
        int[] g = LogoTile.clean(grey, W, H);
        assertEquals(0, at(g, 0, 0) >>> 24);
        assertEquals(0, at(g, 3, 0) >>> 24);
        assertTrue(LogoTile.luminance(at(g, 20, 10)) > 0.4f);
    }

    @Test
    public void aColouredBoxIsTheLogosOwnAndStays() {
        // A red square with white letters: nothing taken away, nothing turned over.
        int[] px = logo(0xFFD61E28, 0xFFFFFFFF);
        assertEquals(0, LogoTile.plate(px, W, H));
        int[] out = LogoTile.clean(px, W, H);
        assertArrayEquals(px, out);
    }

    @Test
    public void aLogoMadeForADarkScreenIsLeftAsItIs() {
        int[] white = logo(0x00000000, 0xFFFFFFFF);
        assertArrayEquals(white, LogoTile.clean(white, W, H));
        int[] yellow = logo(0x00000000, 0xFFFFCD00);
        assertArrayEquals(yellow, LogoTile.clean(yellow, W, H));
    }

    @Test
    public void blackBlocksWithWhiteLettersComeOutWhiteBlocksWithBlackLetters() {
        // Mostly black (the blocks), a little white (the letters): turned over, as a broadcaster's own reverse logo.
        int[] px = logo(0x00000000, 0xFF000000);
        px[10 * W + 20] = 0xFFFFFFFF;
        int[] out = LogoTile.clean(px, W, H);
        assertEquals(0xFFFFFFFF, at(out, 12, 8));
        assertEquals(0xFF000000, at(out, 20, 10));
    }

    @Test
    public void darkColoursTurnLightKeepingTheirHue() {
        int navy = LogoTile.flip(0xFF101C54);
        int r = (navy >> 16) & 0xFF, g = (navy >> 8) & 0xFF, b = navy & 0xFF;
        assertTrue("light", LogoTile.luminance(navy) > 0.45f);
        assertTrue("still blue", b > r && b > g);
        // Mid tones stay where they are; alpha is kept.
        assertEquals(0x80808080 >>> 24, LogoTile.flip(0x80808080) >>> 24);
        int red = LogoTile.flip(0xFFFF0000);
        assertEquals(0xFF, (red >> 16) & 0xFF);
        // A logo of a dark colour with a bright mark: turned over, the bright mark still bright.
        int[] px = logo(0x00000000, 0xFF101C54);
        px[10 * W + 20] = 0xFFFF8C00;
        int[] out = LogoTile.clean(px, W, H);
        assertTrue(LogoTile.luminance(at(out, 12, 8)) > 0.45f);
        assertTrue(LogoTile.luminance(at(out, 20, 10)) > 0.3f);
    }

    @Test
    public void itsMarginsAreTrimmed() {
        assertArrayEquals(new int[]{10, 6, 30, 14}, LogoTile.bounds(logo(0x00000000, 0xFFFFFFFF), W, H));
        assertNull(LogoTile.bounds(new int[W * H], W, H));
    }

    @Test
    public void itSitsInTheMiddleWithTheSameRoomAllRound() {
        // A wide logo: as wide as the tile allows; a square one: as tall as it allows; both in the middle.
        float[] wide = LogoTile.fit(400, 100, 200, 100);
        assertEquals(200 * LogoTile.FILL_W, wide[2] - wide[0], 0.01f);
        assertEquals(200, wide[0] + wide[2], 0.01f);
        assertEquals(50, (wide[1] + wide[3]) / 2f, 0.01f);
        float[] square = LogoTile.fit(50, 50, 200, 100);
        assertEquals(100 * LogoTile.FILL_H, square[3] - square[1], 0.01f);
        assertEquals(200, square[0] + square[2], 0.01f);
    }

    @Test
    public void nothingToWorkOnComesBackAsItWas() {
        assertNull(LogoTile.clean(null, 4, 4));
        int[] tiny = {0xFF000000};
        assertArrayEquals(new int[]{0xFFFFFFFF}, LogoTile.clean(tiny, 1, 1));
        assertEquals(0f, LogoTile.darkShare(new int[8]), 0f);
    }
}
