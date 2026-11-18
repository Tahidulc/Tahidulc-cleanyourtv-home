package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Channel logos as they are, on no tile: only a logo too faint for its ground gets a soft halo behind its shape. */
public class LogoHaloTest {
    private static final int W = 40, H = 20;
    private static final int BLACK = 0xFF000000, NAVY_PAGE = 0xFF0D0F2C, WHITE = 0xFFFFFFFF;

    /** A w by h logo: clear (or a box's colour) all round, a block of ink in the middle. */
    private static int[] logo(int ground, int ink) {
        int[] px = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) px[y * W + x] = x >= 10 && x < 30 && y >= 6 && y < 14 ? ink : ground;
        }
        return px;
    }

    /** A wordmark: half its ink one colour, half the other (the letters and an emblem). */
    private static int[] twoInks(int a, int b) {
        int[] px = logo(0x00000000, a);
        for (int y = 6; y < 14; y++) for (int x = 20; x < 30; x++) px[y * W + x] = b;
        return px;
    }

    @Test
    public void blackWritingOnTheBlackGuideGetsAHalo() {
        assertTrue(LogoHalo.needs(logo(0x00000000, 0xFF101010), W, H, BLACK));
    }

    @Test
    public void navyOnTheNavyPageGetsAHalo() {
        assertTrue(LogoHalo.needs(logo(0x00000000, 0xFF14215C), W, H, NAVY_PAGE));
    }

    @Test
    public void whiteAndBrightLogosAreLeftAlone() {
        assertFalse(LogoHalo.needs(logo(0x00000000, WHITE), W, H, BLACK));
        assertFalse(LogoHalo.needs(logo(0x00000000, 0xFFFFC21A), W, H, BLACK));
        // a strong red reads on black
        assertFalse(LogoHalo.needs(logo(0x00000000, 0xFFE53935), W, H, BLACK));
    }

    @Test
    public void onlyAMostlyDarkLogoGetsOne() {
        // half black, half yellow: as much faint as not ("mostly"), a halo
        assertTrue(LogoHalo.needs(twoInks(0xFF000000, 0xFFFFC21A), W, H, BLACK));
        // a quarter dark: it reads well enough as it is
        int[] px = logo(0x00000000, 0xFFFFC21A);
        for (int y = 6; y < 14; y++) for (int x = 10; x < 15; x++) px[y * W + x] = 0xFF000000;
        assertFalse(LogoHalo.needs(px, W, H, BLACK));
    }

    @Test
    public void aLogoWithItsOwnBoxIsLeftAsItIs() {
        // a JPEG's white box with dark writing: its own ground, no halo (a halo would only outline the box)
        assertFalse(LogoHalo.needs(logo(WHITE, 0xFF9E1B1B), W, H, BLACK));
        // a black box with white letters on the black guide: the letters read
        assertFalse(LogoHalo.needs(logo(BLACK, WHITE), W, H, BLACK));
    }

    @Test
    public void onALightGroundTheWhiteLogoIsTheFaintOne() {
        assertTrue(LogoHalo.needs(logo(0x00000000, WHITE), W, H, WHITE));
        assertFalse(LogoHalo.needs(logo(0x00000000, 0xFF101010), W, H, WHITE));
        assertEquals(0xFF000000, LogoHalo.colour(WHITE));
        assertEquals(0xFFFFFFFF, LogoHalo.colour(BLACK));
        assertEquals(0xFFFFFFFF, LogoHalo.colour(NAVY_PAGE));
    }

    @Test
    public void halfSeeThroughEdgesAreNotCounted() {
        // only clear and faint-alpha pixels: nothing solid, nothing to judge
        int[] px = logo(0x00000000, 0x40000000);
        assertFalse(LogoHalo.needs(px, W, H, BLACK));
    }

    @Test
    public void theHaloFollowsTheShapeNeverABox() {
        int r = LogoHalo.room(W, H);
        assertTrue(r >= 2);
        int[] a = LogoHalo.glow(logo(0x00000000, 0xFF000000), W, H, r);
        int gw = W + 2 * r, gh = H + 2 * r;
        assertEquals(gw * gh, a.length);
        // strong over the logo's middle, nothing at the picture's corners, softer away from the shape
        int mid = a[(r + 10) * gw + r + 20];
        assertTrue("strong under the logo: " + mid, mid >= LogoHalo.STRENGTH * 0.9f);
        assertEquals(0, a[0]);
        assertEquals(0, a[gw * gh - 1]);
        int near = a[(r + 10) * gw + r + 10 - 1], farther = a[(r + 10) * gw + r + 10 - r + 1];
        assertTrue("fades out from the shape: " + near + " then " + farther, near > farther);
        // round the block's corner it is weaker than along its side: it follows the shape, it is not a rectangle
        int side = a[(r + 10) * gw + r + 10 - 2], corner = a[(r + 6 - 2) * gw + r + 10 - 2];
        assertTrue("softer at the corner: " + corner + " < " + side, corner < side);
        // and never stronger than its strength
        for (int v : a) assertTrue(v <= LogoHalo.STRENGTH);
    }

    @Test
    public void nothingToWorkOn() {
        assertFalse(LogoHalo.needs(null, 4, 4, BLACK));
        assertFalse(LogoHalo.needs(new int[3], 4, 4, BLACK));
    }
}
