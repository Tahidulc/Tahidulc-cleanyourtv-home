package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;

import com.gathin.homefieldtv.engine.LogoMatch;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Arrays;

/**
 * Channel logos (Logos), as the family asked: each logo's tile in the logo's own colour, made deep so the logo reads;
 * a logo with no real colour on neutral glass; None: the logo bare, a dark one with a soft halo, never a box; and the
 * setting, Logo's colour as it comes.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class LogosTest {
    /** A made-up logo: n pixels of a colour among clear ones (and some white and black letters). */
    private static int[] logo(int colour, int n, int white, int black) {
        int[] px = new int[400];
        Arrays.fill(px, 0x00000000);
        int k = 0;
        for (int i = 0; i < n; i++) px[k++] = colour;
        for (int i = 0; i < white; i++) px[k++] = 0xFFFFFFFF;
        for (int i = 0; i < black; i++) px[k++] = 0xFF000000;
        return px;
    }

    private static float hue(int c) {
        float[] hsv = new float[3];
        Logos.hsv(c, hsv);
        return hsv[0];
    }

    @Test public void theTileTakesTheLogosOwnColourMadeDeep() {
        // A green "4" with a little white: green, its hue kept.
        int green = 0xFF8BC53F;
        int main = Logos.dominant(logo(green, 120, 30, 0));
        assertTrue(main != 0);
        assertEquals(hue(green), hue(main), 8f);
        // A red logo with white letters: red, not white.
        assertEquals(hue(0xFFE21E26), hue(Logos.dominant(logo(0xFFE21E26, 60, 140, 0))), 8f);
        // White, black and grey marks have no real colour.
        assertEquals(0, Logos.dominant(logo(0xFFFFFFFF, 200, 0, 0)));
        assertEquals(0, Logos.dominant(logo(0xFF808080, 200, 0, 40)));
        assertEquals(0, Logos.dominant(new int[16]));

        int[] tile = Logos.tile(main, Logos.luminance(logo(green, 120, 30, 0)));
        float[] hsv = new float[3];
        Logos.hsv(tile[2], hsv);
        assertEquals("the logo's hue", hue(green), hsv[0], 8f);
        assertTrue("deep: " + hsv[2], hsv[2] >= 0.30f && hsv[2] <= 0.44f);
        assertTrue("rich", hsv[1] >= 0.45f);
        assertTrue("lighter at the top", Logos.luminance(tile[0]) > Logos.luminance(tile[1]));
        assertTrue("the logo reads on it", Logos.contrast(Logos.luminance(logo(green, 120, 30, 0)), Logos.luminance(tile[2])) >= Logos.CONTRAST);
    }

    @Test public void aLogoTooCloseToItsTileGetsADeeperOrLighterOne() {
        // A navy logo: deep navy would hide it, so the tile goes light (or deeper still) until it reads.
        int navy = 0xFF1B2A5C;
        float lum = Logos.luminance(logo(navy, 200, 0, 0));
        int[] tile = Logos.tile(Logos.dominant(logo(navy, 200, 0, 0)), lum);
        assertTrue(Logos.contrast(lum, Logos.luminance(tile[2])) >= Logos.CONTRAST - 0.01f);
        assertEquals(hue(navy), hue(tile[2]), 10f);
    }

    @Test public void aDarkLogoBareGetsASoftHaloNeverABox() {
        Context c = RuntimeEnvironment.getApplication();
        Ui.init(c);
        // Black letters on clear: dark.
        Bitmap mark = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888);
        mark.eraseColor(0);
        for (int y = 6; y < 14; y++) for (int x = 6; x < 34; x++) mark.setPixel(x, y, 0xFF101010);
        int[] px = new int[40 * 20];
        mark.getPixels(px, 0, 40, 0, 0, 40, 20);
        assertTrue(Logos.dark(px));
        Bitmap bare = Logos.bare(mark.copy(Bitmap.Config.ARGB_8888, false), 120, 60, true);
        int w = bare.getWidth(), h = bare.getHeight();
        assertTrue(w <= 120 && h <= 60);
        // The halo: light, faint, round the shape (not at the corners: no box).
        int corner = bare.getPixel(0, 0);
        assertEquals(0, corner >>> 24);
        Bitmap plain = Logos.bare(mark.copy(Bitmap.Config.ARGB_8888, false), 120, 60, false);
        int top = 0;
        while (top < plain.getHeight() && (plain.getPixel(plain.getWidth() / 2, top) >>> 24) < 128) top++;
        assertTrue(top > 4);
        int near = bare.getPixel(w / 2, top - 3);
        assertTrue("a glow just above the letters: " + Integer.toHexString(near), (near >>> 24) > 0 && (near & 0xFF) > 0xC0);
        // The same logo light: no halo.
        assertEquals(0, plain.getPixel(plain.getWidth() / 2, top - 3) >>> 24);
    }

    @Test public void logosColourIsTheSettingAsItComes() {
        Context c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("look", Context.MODE_PRIVATE).edit().clear().putBoolean("logo_tiles", true).commit();
        try {
            Logos.load(c);
            assertEquals(Logos.COLOUR, Logos.behind());
            assertEquals(Pics.TINT, Logos.mode());
            assertFalse(Pics.tiles);
            Logos.setBehind(c, Logos.NONE);
            assertEquals(Pics.LOGO, Logos.mode());
            Logos.setBehind(c, Logos.BLACK);
            assertTrue(Pics.tiles);
            assertEquals(Pics.TILE, Logos.mode());
            Logos.load(c);
            assertEquals(Logos.BLACK, Logos.behind());
            assertEquals("Logo's colour", Logos.NAMES[Logos.COLOUR]);
        } finally {
            c.getSharedPreferences("look", Context.MODE_PRIVATE).edit().clear().commit();
            Logos.load(c);
        }
    }

    @Test public void aDarkNewLogosWhiteVersionIsFound() {
        String index = "# test\ncountries/united-kingdom\tbbc-news-hz-uk bbc-news-hz-white-uk bbc-news-uk sky-history-uk sky-history-light-uk\n";
        LogoMatch m = LogoMatch.parse(index, null);
        assertNotNull(m);
        assertEquals("countries/united-kingdom/bbc-news-hz-white-uk.png", m.whiteOf("countries/united-kingdom/bbc-news-hz-uk.png"));
        // Light means for light grounds: never taken; a white one has no whiter.
        assertNull(m.whiteOf("countries/united-kingdom/sky-history-uk.png"));
        assertNull(m.whiteOf("countries/united-kingdom/bbc-news-hz-white-uk.png"));
        assertNull(m.whiteOf("countries/united-kingdom/nothing-uk.png"));
    }
}
