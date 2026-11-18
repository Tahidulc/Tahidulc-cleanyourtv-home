package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Each channel logo's tile in its own colour: a deeper shade of its main colour, a neutral dark for a colourless mark. */
public class LogoColourTest {
    private static final int W = 40, H = 20;

    /** A w by h logo: clear (or a box's colour) all round, a block of ink in the middle. */
    private static int[] logo(int ground, int ink) {
        int[] px = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) px[y * W + x] = x >= 10 && x < 30 && y >= 6 && y < 14 ? ink : ground;
        }
        return px;
    }

    private static int mid(int[] t) {
        return LogoColour.mix(t[0], t[1], 0.5f);
    }

    private static float hue(int c) {
        return LogoColour.hsv(c)[0];
    }

    private static float value(int c) {
        return LogoColour.hsv(c)[2];
    }

    private static float hueGap(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return d > 180 ? 360 - d : d;
    }

    @Test
    public void aGreenLogoSitsOnDeepGreen() {
        int green = 0xFF2EB34A;
        int[] t = LogoColour.tile(logo(0x00000000, green), W, H);
        int m = mid(t);
        assertTrue("green: " + hue(m), hueGap(hue(m), hue(green)) < 12);
        assertTrue("deep: " + value(m), value(m) >= 0.30f && value(m) <= 0.44f);
        // its saturation kept
        assertEquals(LogoColour.hsv(green)[1], LogoColour.hsv(m)[1], 0.08f);
    }

    @Test
    public void aRedLogoSitsOnDeepRed() {
        int red = 0xFFD7263D;
        int[] t = LogoColour.tile(logo(0x00000000, red), W, H);
        int m = mid(t);
        assertTrue("red: " + hue(m), hueGap(hue(m), hue(red)) < 12);
        assertTrue("deep: " + value(m), value(m) >= 0.30f && value(m) <= 0.44f);
        // the logo reads on it
        assertTrue(LogoHalo.contrast(red, m) >= LogoColour.FAINT);
    }

    @Test
    public void theMainColourWinsOverWhiteWritingAndAFewOtherSpecks() {
        // a white wordmark (the most of it) with a purple block: purple is its colour; white and grey do not count
        int[] px = logo(0x00000000, 0xFFFFFFFF);
        for (int y = 6; y < 14; y++) for (int x = 10; x < 16; x++) px[y * W + x] = 0xFF6A1B9A;
        px[0] = 0xFFFFC21A;
        int m = mid(LogoColour.tile(px, W, H));
        assertTrue("purple: " + hue(m), hueGap(hue(m), hue(0xFF6A1B9A)) < 15);
    }

    @Test
    public void aWhiteMarkGetsANeutralDarkTileNotBlack() {
        int[] t = LogoColour.tile(logo(0x00000000, 0xFFFFFFFF), W, H);
        int m = mid(t);
        assertTrue("no colour: " + LogoColour.hsv(m)[1], LogoColour.hsv(m)[1] < 0.2f);
        float l = LogoTile.luminance(m);
        assertTrue("dark but not black: " + l, l > 0.012f && l < 0.08f);
        assertNotEquals(0xFF000000, t[0]);
        assertNotEquals(0xFF000000, t[1]);
    }

    @Test
    public void aGreyMarkIsColourlessToo() {
        int m = mid(LogoColour.tile(logo(0x00000000, 0xFF9A9A9A), W, H));
        assertTrue(LogoColour.hsv(m)[1] < 0.2f);
    }

    @Test
    public void aBlackMarkGetsATileLightEnoughToRead() {
        int[] t = LogoColour.tile(logo(0x00000000, 0xFF050505), W, H);
        int m = mid(t);
        assertTrue("reads: " + LogoHalo.contrast(0xFF050505, m), LogoHalo.contrast(0xFF050505, m) >= LogoColour.FAINT);
        assertTrue(LogoColour.hsv(m)[1] < 0.2f);
    }

    @Test
    public void aNavyLogoIsNotLostOnItsOwnDeepBlue() {
        int navy = 0xFF14215C;
        int m = mid(LogoColour.tile(logo(0x00000000, navy), W, H));
        assertTrue("blue: " + hue(m), hueGap(hue(m), hue(navy)) < 20);
        assertTrue("taken lighter until it reads: " + LogoHalo.contrast(navy, m), LogoHalo.contrast(navy, m) >= LogoColour.FAINT);
        assertTrue(value(m) > LogoColour.VALUE);
    }

    @Test
    public void aPaleLogoGetsItsTileTakenDeeperRatherThanLost() {
        // a pale yellow mark: its deep shade reads already
        int pale = 0xFFF6E27A;
        int m = mid(LogoColour.tile(logo(0x00000000, pale), W, H));
        assertTrue(LogoHalo.contrast(pale, m) >= LogoColour.FAINT);
    }

    @Test
    public void aJpegBoxIsNotTheLogosColour() {
        // dark red writing on a white JPEG box: red is the colour; the white box reads on the deep red
        int[] t = LogoColour.tile(logo(0xFFFFFFFF, 0xFF9E1B1B), W, H);
        int m = mid(t);
        assertTrue("red: " + hue(m), hueGap(hue(m), 0f) < 15);
    }

    @Test
    public void aSoftGradientTopToFoot() {
        int[] t = LogoColour.tile(logo(0x00000000, 0xFF2EB34A), W, H);
        assertTrue(LogoTile.luminance(t[0]) > LogoTile.luminance(t[1]));
        assertTrue("soft", LogoHalo.contrast(t[0], t[1]) < 1.6f);
        int[] n = LogoColour.gradient(LogoColour.NEUTRAL);
        assertTrue(LogoTile.luminance(n[0]) > LogoTile.luminance(n[1]));
    }

    // ---- the row round the tile (a channel's number, logo and name together) ---------------------------------------

    private static int row(int[] t) {
        return LogoColour.mix(t[2], t[3], 0.5f);
    }

    @Test
    public void theRowIsTheLogosColourATouchLighterAndRicherThanItsTile() {
        for (int ink : new int[]{0xFF2EB34A, 0xFFD7263D, 0xFF1F6FE0, 0xFFE8742A}) {
            int[] t = LogoColour.tile(logo(0x00000000, ink), W, H);
            int tile = mid(t), row = row(t);
            assertTrue("same colour: " + hue(row) + " v " + hue(tile), hueGap(hue(row), hue(tile)) < 15);
            assertTrue("lighter: " + Integer.toHexString(ink), LogoTile.luminance(row) > LogoTile.luminance(tile));
            assertTrue("richer or as rich", LogoColour.hsv(row)[1] >= LogoColour.hsv(tile)[1] - 0.02f);
        }
    }

    @Test
    public void whiteNumbersAndNamesReadOnEveryRow() {
        int[] inks = {0xFF2EB34A, 0xFFD7263D, 0xFFFFE14D, 0xFF9BE7FF, 0xFFFFFFFF, 0xFF050505, 0xFF14215C, 0xFF9A9A9A};
        for (int ink : inks) {
            int row = row(LogoColour.tile(logo(0x00000000, ink), W, H));
            assertTrue("white on the row of " + Integer.toHexString(ink) + ": " + LogoHalo.contrast(0xFFFFFFFF, row),
                    LogoHalo.contrast(0xFFFFFFFF, row) >= LogoColour.WRITING - 0.3f);
        }
    }

    @Test
    public void theTileStillShowsInsideItsRow() {
        int[] inks = {0xFF2EB34A, 0xFFD7263D, 0xFFFFE14D, 0xFFFFFFFF, 0xFF050505, 0xFF14215C, 0xFF9A9A9A};
        for (int ink : inks) {
            int[] t = LogoColour.tile(logo(0x00000000, ink), W, H);
            assertTrue("apart for " + Integer.toHexString(ink) + ": " + LogoHalo.contrast(mid(t), row(t)),
                    LogoHalo.contrast(mid(t), row(t)) >= LogoColour.APART - 0.06f);
        }
    }

    @Test
    public void aColourlessLogosRowIsANeutralSlate() {
        int row = row(LogoColour.tile(logo(0x00000000, 0xFFFFFFFF), W, H));
        assertTrue(LogoColour.hsv(row)[1] < 0.2f);
        assertNotEquals(0xFF000000, row);
        int[] w = LogoColour.waiting();
        assertEquals(4, w.length);
        assertEquals(LogoColour.gradient(LogoColour.NEUTRAL)[0], w[0]);
    }

    @Test
    public void nothingToWorkOnIsNeutral() {
        assertEquals(LogoColour.gradient(LogoColour.NEUTRAL)[0], LogoColour.tile(new int[W * H], W, H)[0]);
        assertEquals(LogoColour.gradient(LogoColour.NEUTRAL)[0], LogoColour.tile(null, W, H)[0]);
        assertEquals(0, LogoColour.dominant(new int[3], 4, 4));
    }
}
