package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;

import com.gathin.broadcast.LogoColour;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.LogoMatch;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Channel logos (Logos), as the family asked: each logo's tile in the logo's own colour (the shared rule, LogoColour,
 * so a channel has the same colour on the TV, the phone and the launcher), a logo with no real colour on the neutral
 * slate; its channel's cell in the same colour (ChannelLook); None: the logo bare, a faint one with the shared soft
 * halo, never a box; and the setting, Logo's colour as it comes.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class LogosTest {
    /** A made-up logo w x h: a bar of a colour across its middle on clear. */
    private static Bitmap logo(int colour, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(0);
        for (int y = h / 4; y < h - h / 4; y++) for (int x = w / 8; x < w - w / 8; x++) b.setPixel(x, y, colour);
        return b;
    }

    private static float hue(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        return hsv[0];
    }

    @Test public void aLogosColoursAreTheSharedRule() {
        // A green logo: its tile and its cell are LogoColour's, kept for its address.
        Bitmap green = logo(0xFF8BC53F, 120, 60);
        Logos.Tint t = Logos.colours("test:green", green);
        int[] shared = LogoColour.tile(green);
        assertArrayEquals(shared, new int[]{t.tileTop, t.tileFoot, t.cellTop, t.cellFoot});
        assertSame(t, Logos.tint("test:green"));
        assertFalse(t.neutral);
        assertEquals("the logo's hue", hue(0xFF8BC53F), hue(t.tile), 10f);
        float[] hsv = new float[3];
        Color.colorToHSV(t.tile, hsv);
        assertTrue("deep: " + hsv[2], hsv[2] >= 0.28f && hsv[2] <= 0.46f);
        assertTrue("the cell lighter than the tile", Color.luminance(t.cell) > Color.luminance(t.tile));
        // White marks: no real colour, the neutral slate.
        Logos.Tint white = Logos.colours("test:white", logo(0xFFFFFFFF, 120, 60));
        assertTrue(white.neutral);
        assertEquals(Logos.Tint.WAITING.tile, white.tile);
    }

    @Test public void aDarkLogoAShadeLighterThanItsDeepTileStillReads() {
        // Navy a touch lighter than its own deep shade: no deeper tile shows it, so it is taken light instead.
        int navy = 0xFF1D2D61;
        Logos.Tint t = Logos.colours("test:navy", logo(navy, 120, 60));
        float a = Color.luminance(navy), b = Color.luminance(t.tile);
        float contrast = (Math.max(a, b) + 0.05f) / (Math.min(a, b) + 0.05f);
        assertTrue("reads: " + Integer.toHexString(t.tile) + " " + contrast, contrast >= 2f);
        assertEquals("still blue", hue(navy), hue(t.tile), 20f);
    }

    @Test public void aChannelsCellIsItsLogosRowColourOrThePlainRow() {
        Context c = RuntimeEnvironment.getApplication();
        Ui.init(c);
        c.getSharedPreferences("look", Context.MODE_PRIVATE).edit().clear().commit();
        try {
            Logos.load(c);
            Channel red = new Channel("t:red", "Red One", "", "test:red", "", "", null, null, 1);
            // Not read yet: the slate's cell, so the column keeps its shape while the logos come.
            assertEquals(Logos.Tint.WAITING.cell, ChannelLook.colour(red));
            Logos.Tint t = Logos.colours("test:red", logo(0xFFE2401E, 120, 60));
            assertEquals(t.cell, ChannelLook.colour(red));
            // Its words stay white on it, where the remote is or not; the cell is drawn in its colour.
            Bitmap b = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            assertEquals(Look07.L1, ChannelLook.fillCell(cv, new RectF(20, 20, 280, 80), red, false));
            assertEquals(hue(t.cell), hue(b.getPixel(150, 50)), 12f);
            assertEquals(Look07.L1, ChannelLook.fillCell(cv, new RectF(20, 20, 280, 80), red, true));
            // None and Black tile: the plain row, the white pill with dark words where the remote is.
            Logos.setBehind(c, Logos.NONE);
            assertEquals(0, ChannelLook.colour(red));
            assertEquals(Look07.INK, ChannelLook.fillCell(cv, new RectF(20, 20, 280, 80), red, true));
            assertEquals(Look07.L1, ChannelLook.fillCell(cv, new RectF(20, 20, 280, 80), red, false));
        } finally {
            c.getSharedPreferences("look", Context.MODE_PRIVATE).edit().clear().commit();
            Logos.load(c);
        }
    }

    @Test public void aLogoOnItsTileIsDrawnWhole() {
        Context c = RuntimeEnvironment.getApplication();
        Ui.init(c);
        // A white logo on the slate: the logo itself at full strength (not faded with the tile).
        Logos.Tint t = Logos.colours("test:white2", logo(0xFFFFFFFF, 120, 60));
        Bitmap tile = Logos.onColour(t, logo(0xFFFFFFFF, 120, 60), 120, 64);
        int mid = tile.getPixel(60, 32);
        assertTrue("white: " + Integer.toHexString(mid), (mid >>> 24) == 0xFF && (mid & 0xFF) > 0xF0);
        // Its corners clear (round), its middle edge the tile's colour.
        assertEquals(0, tile.getPixel(0, 0) >>> 24);
        int side = tile.getPixel(4, 32);
        assertEquals(0xFF, side >>> 24);
    }

    @Test public void aFaintLogoBareGetsTheSharedHaloNeverABox() {
        Context c = RuntimeEnvironment.getApplication();
        Ui.init(c);
        // Near-black letters on clear: too faint on the dark ground.
        Bitmap mark = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888);
        mark.eraseColor(0);
        for (int y = 12; y < 28; y++) for (int x = 12; x < 68; x++) mark.setPixel(x, y, 0xFF101010);
        Bitmap bare = Logos.bare(mark, 120, 60);
        int w = bare.getWidth(), h = bare.getHeight();
        // Larger than the logo (the halo's room) but within the box; clear at its corners (no box).
        assertTrue(w <= 120 && h <= 60);
        assertEquals(0, bare.getPixel(0, 0) >>> 24);
        // The first row of the letters down its middle, then just above them.
        int top = 0;
        while (top < h) {
            int p = bare.getPixel(w / 2, top);
            if ((p >>> 24) >= 0xF0 && (p & 0xFF) < 0x40) break;
            top++;
        }
        assertTrue("the letters are in it, with room above: " + top, top < h && top >= 2);
        int near = bare.getPixel(w / 2, top - 2);
        assertTrue("a light glow just above the letters: " + Integer.toHexString(near), (near >>> 24) > 0 && (near & 0xFF) > 0xC0);
        // A light logo: as it is, no halo.
        Bitmap light = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888);
        light.eraseColor(0);
        for (int y = 12; y < 28; y++) for (int x = 12; x < 68; x++) light.setPixel(x, y, 0xFFF0F0F0);
        Bitmap plain = Logos.bare(light, 120, 60);
        assertEquals(0, plain.getPixel(plain.getWidth() / 2, 0) >>> 24);
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
