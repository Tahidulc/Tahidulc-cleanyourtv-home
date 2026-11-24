package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * The family's three changes: programme pictures taking up their whole cell at any size with their faces whole
 * (PicFill), channel cells as the logo alone (ChannelLook: no number, no tile, no name; the logo as large as the cell
 * allows at its own shape, its marks small in the corner), on the approved deep, opaque shade of its colour (a pale
 * logo stands out on it).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class PanelsLogosTest {
    /** Faces as the drawn people have them (two heads, upper middle-left), and a group across the picture. */
    static final float[] TWO = {0.226f, 0.219f, 0.537f, 0.474f, 16f / 9f}, GROUP = {0.10f, 0.20f, 0.90f, 0.46f, 16f / 9f};

    @After public void after() {
        Exec.inline = false;
    }

    private static void assertCovers(String what, float[] out, float a, float w, float h) {
        float k = out[PicFill.K];
        assertTrue(what + ": covers left " + out[PicFill.X], out[PicFill.X] <= 0.01f);
        assertTrue(what + ": covers right", out[PicFill.X] + a * k >= w - 0.01f);
        assertTrue(what + ": covers top", out[PicFill.Y] <= 0.01f);
        assertTrue(what + ": covers foot", out[PicFill.Y] + k >= h - 0.01f);
    }

    private static void assertFacesWhole(String what, float[] out, float w, float h) {
        assertTrue(what + ": faces' left " + out[PicFill.FL], out[PicFill.FL] >= -0.01f);
        assertTrue(what + ": faces' right " + out[PicFill.FR], out[PicFill.FR] <= w + 0.01f);
        assertTrue(what + ": faces' top " + out[PicFill.FT], out[PicFill.FT] >= -0.01f);
        assertTrue(what + ": faces' foot " + out[PicFill.FB], out[PicFill.FB] <= h + 0.01f);
    }

    @Test public void aProgrammesPictureTakesUpItsWholeCellWithItsFacesWhole() {
        float a = 16f / 9f;
        float[] out = new float[PicFill.SIZE];
        // The guide's cells (84 high) from a sliver to the window's width, a short row's programme, the info card.
        float[][] cells = {{60, 84}, {150, 84}, {300, 84}, {450, 84}, {600, 84}, {900, 84}, {1340, 84}, {362, 88}, {1824, 322}, {400, 225}};
        for (float[] box : new float[][]{TWO, GROUP}) {
            for (float[] cell : cells) {
                float w = cell[0], h = cell[1];
                String what = (box == TWO ? "two" : "group") + " in " + (int) w + "x" + (int) h;
                PicFill.layout(a, box, w, h, false, out);
                if (out[PicFill.KIND] == PicFill.COVER) {
                    assertCovers(what, out, a, w, h);
                    // A narrow cell may cut a wide group's outer faces; any other keeps them whole.
                    if (!(box == GROUP && w < PicFill.WIDE * h)) assertFacesWhole(what, out, w, h);
                } else {
                    // The ambient fill: only for a wide cell; the sharp picture as tall as the cell, its faces whole,
                    // out from under the words.
                    assertTrue(what + ": ambient only in a wide cell", w >= PicFill.WIDE * h);
                    assertTrue(what + ": as tall as the cell", out[PicFill.K] >= h - 0.01f && out[PicFill.Y] <= 0.01f
                            && out[PicFill.Y] + out[PicFill.K] >= h - 0.01f);
                    assertFacesWhole(what, out, w, h);
                    assertTrue(what + ": beside the words " + out[PicFill.FL], out[PicFill.FL] >= PicFill.room(w, h, false) * w - 1f
                            || out[PicFill.FR] >= w - PicFill.ROOM_H * h - 1f);
                }
            }
        }
        // A very wide, short cell cannot cover with two heads whole: the ambient fill, the faces toward the right.
        PicFill.layout(a, TWO, 1340, 84, false, out);
        assertEquals(PicFill.AMBIENT, out[PicFill.KIND], 0f);
        float mid = (out[PicFill.FL] + out[PicFill.FR]) / 2f;
        assertEquals((PicFill.room(1340, 84, false) + 1f) / 2f * 1340, mid, 30f);
        // A short row's programme (its words take more of it): its faces past them, at its right.
        PicFill.layout(a, TWO, 362, 88, false, out);
        assertTrue("past the words: " + out[PicFill.FL], out[PicFill.FL] >= 0.58f * 362);
        assertFacesWhole("short row", out, 362, 88);
        assertTrue("its left edge feathered into the blur", out[PicFill.FEATHER_L] > 10f);
        // A tile as tall as the picture's shape: covering, the faces in view.
        PicFill.layout(a, TWO, 400, 225, false, out);
        assertEquals(PicFill.COVER, out[PicFill.KIND], 0f);
    }

    @Test public void withNoFacesKnownItCoversFromTheMiddle() {
        float a = 16f / 9f;
        float[] out = new float[PicFill.SIZE];
        PicFill.layout(a, Faces.FALLBACK, 600, 84, false, out);
        assertEquals(PicFill.COVER, out[PicFill.KIND], 0f);
        assertCovers("no faces", out, a, 600, 84);
        assertEquals("the middle across", (600 - a * out[PicFill.K]) / 2f, out[PicFill.X], 0.5f);
        PicFill.layout(a, null, 1824, 322, true, out);
        assertCovers("none", out, a, 1824, 322);
    }

    @Test public void theInfoCardsFacesAreBetweenItsWordsAndWhatsNext() {
        float[] out = new float[PicFill.SIZE];
        PicFill.layout(16f / 9f, TWO, 1824, 322, true, out);
        assertFacesWhole("card", out, 1824, 322);
        // In card px: the words end by about 1000 (their limit before the faces), what's next starts at 1364.
        assertTrue("right of the words: " + out[PicFill.FL], out[PicFill.FL] >= InfoCard.WORDS_MIN + InfoCard.TEXT_X - InfoCard.X);
        assertTrue("left of what's next: " + out[PicFill.FR], out[PicFill.FR] <= InfoCard.NEXT_X - InfoCard.X);
    }

    @Test public void aCellCutShortShowsItsFaces() {
        // The picture made for a 900 px programme, faces at 560..700; 200 px of it on screen, words to 120.
        float x = PicFill.window(900, 200, 560, 700, 120, 8);
        assertTrue("the faces in the window: " + x, x <= 560 - 8 + 0.01f && x + 200 >= 700 + 8 - 0.01f);
        // Room beside the words: the faces there; none: centred on them.
        float wide = PicFill.window(900, 500, 560, 700, 150, 8);
        assertEquals((560 + 700) / 2f - (150 + 350 / 2f), wide, 0.5f);
        float narrow = PicFill.window(900, 120, 560, 700, 110, 8);
        assertEquals((560 + 700) / 2f - 60f, narrow, 0.5f);
        assertEquals(0f, PicFill.window(900, 900, 560, 700, 110, 8), 0f);
        // The guide reads a programme's picture at its whole width (so it stays one picture as the times glide).
        Ui.init(RuntimeEnvironment.getApplication());
        long half = 30 * 60_000L;
        assertEquals(GuideGrid.fullWidth(0, half), GuideGrid.fullWidth(0, half));
        assertTrue(GuideGrid.fullWidth(0, half) >= Math.round(300 - GuideGrid.GAP) && GuideGrid.fullWidth(0, half) % 8 == 0);
        assertTrue("at most the window", GuideGrid.fullWidth(0, 10 * half) <= Math.round(GuideGrid.RIGHT - GuideGrid.COLUMN) + 8);
    }

    private static float value(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        return hsv[2];
    }

    private static float sat(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        return hsv[1];
    }

    private static float contrast(int a, int b) {
        float la = Color.luminance(a), lb = Color.luminance(b);
        return (Math.max(la, lb) + 0.05f) / (Math.min(la, lb) + 0.05f);
    }

    /** A made-up logo w x h: a bar of a colour across its middle on clear. */
    private static Bitmap logo(int colour, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(0);
        for (int y = h / 4; y < h - h / 4; y++) for (int x = w / 8; x < w - w / 8; x++) b.setPixel(x, y, colour);
        return b;
    }

    @Test public void theCellIsTheApprovedDeepOpaqueShadeSoAPaleLogoStandsOut() {
        // The shared rule's rows as they could come: pastel blue (light, washed out), a bright blue, a red.
        int[][] rows = {{0xFF6E8FA6, 0xFF657F94}, {0xFF2F6FE0, 0xFF2A63C8}, {0xFF870C0E, 0xFF76090B}};
        for (int[] row : rows) {
            int[] d = Logos.rowDeep(row[0], row[1], false);
            for (int c : d) {
                assertEquals("opaque", 0xFF, c >>> 24);
                assertTrue("rich: " + Integer.toHexString(c), sat(c) >= Logos.ROW_SAT - 0.01f);
            }
            assertTrue("deep at the top: " + value(d[0]), value(d[0]) <= Logos.ROW_TOP_V + 0.01f);
            assertTrue("deep at the foot: " + value(d[1]), value(d[1]) <= Logos.ROW_FOOT_V + 0.01f);
            assertTrue("white marks read on it", contrast(0xFFFFFFFF, Theme.mix(d[0], d[1], 0.5f)) >= 5f);
        }
        // A pale logo (the family's photo): its cell in its own hue, deep and rich, and the logo stands out on it.
        int pale = 0xFF9ED6F0;
        Logos.Tint t = Logos.colours("test:pale", logo(pale, 120, 60));
        assertEquals(0xFF, t.row >>> 24);
        assertTrue("deep: " + value(t.row), value(t.row) <= Logos.ROW_TOP_V);
        assertTrue("not pastel: " + sat(t.row), sat(t.row) >= Logos.ROW_SAT - 0.01f);
        assertTrue("the logo stands out: " + contrast(pale, t.row), contrast(pale, t.row) >= 3.5f);
        // No real colour: the opaque neutral slate.
        Logos.Tint white = Logos.colours("test:white-cell", logo(0xFFFFFFFF, 120, 60));
        assertTrue(white.neutral);
        assertEquals(0xFF, white.row >>> 24);
    }

    @Test public void aCellsLogoFillsItsBoxAtItsOwnShapeWithTheSameRoomAllRound() {
        Ui.init(RuntimeEnvironment.getApplication());
        // A wide logo fills the width, a tall one the height; never stretched.
        int[] wide = Logos.fit(400, 100, 352, 64);
        assertEquals(64, wide[1]);
        assertEquals(256, wide[0]);
        int[] long_ = Logos.fit(800, 50, 352, 64);
        assertEquals(352, long_[0]);
        assertEquals(22, long_[1]);
        int[] tall = Logos.fit(100, 200, 352, 64);
        assertEquals(64, tall[1]);
        assertEquals(32, tall[0]);
        // The box: the cell less the same room on every side (12 % of its height), less the marks' corner at both
        // sides when it is kept (so the logo stays centred).
        RectF cell = new RectF(74, 500, 500, 584), box = new RectF();
        ChannelLook.logoBox(cell, false, box);
        float p = 0.12f * 84f;
        assertEquals(p, box.left - cell.left, 0.01f);
        assertEquals(p, cell.right - box.right, 0.01f);
        assertEquals(p, box.top - cell.top, 0.01f);
        assertEquals(p, cell.bottom - box.bottom, 0.01f);
        ChannelLook.logoBox(cell, true, box);
        assertEquals(box.left - cell.left, cell.right - box.right, 0.01f);
        assertEquals(cell.centerX(), box.centerX(), 0.01f);
        // The logo made for its cell: fitted, as read (no box round it).
        Bitmap red = Logos.cell("test:red-cell", logo(0xFFE2401E, 400, 200), 352, 64, Pics.CELL_COLOUR);
        assertNotNull(red);
        assertTrue("fitted: " + red.getWidth() + "x" + red.getHeight(), red.getHeight() == 64 && red.getWidth() <= 352);
        assertEquals("its own shape (the bar is 3:1)", 3f, red.getWidth() / (float) red.getHeight(), 0.08f);
        // A navy logo on its own deep cell would not show: the shared halo round it, judged on the cell.
        Bitmap navy = Logos.cell("test:navy-cell", logo(0xFF1D2D61, 400, 200), 352, 64, Pics.CELL_COLOUR);
        assertNotNull(navy);
        assertTrue("room for its halo", navy.getHeight() > 64);
    }

    /** A canvas that counts the words drawn. */
    static final class Words extends Canvas {
        int text;

        Words(Bitmap b) {
            super(b);
        }

        @Override public void drawText(String t, float x, float y, Paint p) {
            text++;
            super.drawText(t, x, y, p);
        }

        @Override public void drawText(CharSequence t, int s, int e, float x, float y, Paint p) {
            text++;
            super.drawText(t, s, e, x, y, p);
        }

        @Override public void drawText(char[] t, int i, int n, float x, float y, Paint p) {
            text++;
            super.drawText(t, i, n, x, y, p);
        }

        @Override public void drawText(String t, int s, int e, float x, float y, Paint p) {
            text++;
            super.drawText(t, s, e, x, y, p);
        }
    }

    @Test public void aChannelsCellIsTheLogoAloneWithNoNumberOrName() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        Ui.init(c);
        Type.init(c);
        c.getSharedPreferences("look", Context.MODE_PRIVATE).edit().clear().commit();
        Logos.load(c);
        Exec.inline = true;
        File f = File.createTempFile("logo", ".png");
        try (FileOutputStream o = new FileOutputStream(f)) {
            logo(0xFF9ED6F0, 420, 200).compress(Bitmap.CompressFormat.PNG, 100, o);
        }
        try {
            Channel ch = new Channel("t:lagoon", "Lagoon HD", "", "file:" + f.getPath(), "", "", null, null, 103);
            Bitmap b = Bitmap.createBitmap(520, 120, Bitmap.Config.ARGB_8888);
            RectF cell = new RectF(20, 20, 446, 104);
            // Once to read its logo (and so its colours), then as it is drawn from then on.
            ChannelLook.cell(new Canvas(b), cell, 20f, ch, 0f, 0, "", true, null);
            b.eraseColor(0);
            Words cv = new Words(b);
            ChannelLook.cell(cv, cell, 20f, ch, 0f, 0, "", true, null);
            assertEquals("no number and no name: only the logo", 0, cv.text);
            // Its cell in the logo's deep colour (at the room by its left edge), opaque; the logo itself in the middle.
            Logos.Tint t = Logos.tint("file:" + f.getPath());
            assertNotNull("its colours read with it", t);
            int edge = b.getPixel(30, 62);
            assertEquals(0xFF, edge >>> 24);
            assertTrue("deep: " + Integer.toHexString(edge), value(edge) <= Logos.ROW_TOP_V + 0.06f);
            int mid = b.getPixel((int) cell.centerX(), (int) cell.centerY());
            assertTrue("the pale logo in the middle: " + Integer.toHexString(mid), contrast(mid, edge) >= 3f);
            // The marks: small, in the corner; the HD the only words.
            cv.text = 0;
            ChannelLook.cell(cv, cell, 20f, ch, 1f, ChannelLook.PLAYING | ChannelLook.FAVOURITE | ChannelLook.ARCHIVE, "HD", true, null);
            assertEquals(1, cv.text);
            // None: nothing behind (the plain row), the logo still drawn.
            Logos.setBehind(c, Logos.NONE);
            assertEquals(0, ChannelLook.colour(ch));
            Bitmap n = Bitmap.createBitmap(520, 120, Bitmap.Config.ARGB_8888);
            ChannelLook.cell(new Canvas(n), cell, 20f, ch, 0f, 0, "", true, null);
            assertEquals("nothing behind", 0, n.getPixel(30, 62) >>> 24);
            assertTrue("the logo drawn", (n.getPixel((int) cell.centerX(), (int) cell.centerY()) >>> 24) > 0xC0);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            c.getSharedPreferences("look", Context.MODE_PRIVATE).edit().clear().commit();
            Logos.load(c);
        }
    }

    @Test public void theNumberIsForAccessibility() {
        assertEquals("Channel 102, Meadow 4 HD, Harbour v Granite", GuideGrid.describe(102, "Meadow 4 HD", "Harbour v Granite"));
        assertEquals("Meadow 4 HD", GuideGrid.describe(0, "Meadow 4 HD", ""));
    }
}
