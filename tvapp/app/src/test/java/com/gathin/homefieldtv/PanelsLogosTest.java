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

import com.gathin.broadcast.PicPlace;
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
 * (PicFill, PicPlace: covering a cell of their shape, else the whole picture at the cell's height beside the words, the
 * cell's own colour from it behind; never scaled past covering, never a deep crop), channel cells as the logo alone (ChannelLook: no number, no tile, no name; the logo as large as the cell
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

    private static void assertFacesWhole(String what, float[] out, float w, float h) {
        assertTrue(what + ": faces' left " + out[PicPlace.FL], out[PicPlace.FL] >= -1f);
        assertTrue(what + ": faces' right " + out[PicPlace.FR], out[PicPlace.FR] <= w + 1f);
        assertTrue(what + ": faces' top " + out[PicPlace.FT], out[PicPlace.FT] >= -1f);
        assertTrue(what + ": faces' foot " + out[PicPlace.FB], out[PicPlace.FB] <= h + 1f);
    }

    @Test public void aProgrammesPictureFillsItsCellSeenWholeWithItsFacesWhole() {
        float[] out = new float[PicPlace.SIZE];
        // The guide's cells (84 high) from a sliver to the window's width, a short row's programme, the info card, a tile.
        float[][] cells = {{60, 84}, {150, 84}, {300, 84}, {450, 84}, {600, 84}, {900, 84}, {1340, 84}, {362, 88}, {1824, 322}, {400, 225}};
        for (float[] box : new float[][]{TWO, GROUP, null}) {
            for (float[] cell : cells) {
                float w = cell[0], h = cell[1];
                String what = (box == TWO ? "two" : box == GROUP ? "group" : "none") + " in " + (int) w + "x" + (int) h;
                PicFill.row(1280, 720, box, w, h, out);
                assertTrue(what + ": never past covering", out[PicPlace.K] <= Math.max(w / 1280f, h / 720f) + 1e-4f);
                if (box != null) assertFacesWhole(what, out, w, h);
                if (out[PicPlace.KIND] == PicPlace.COVER) {
                    // Covering only a cell close to its shape: at least 70 % of it shows.
                    assertTrue(what + ": no deep crop", Math.min(w / out[PicPlace.PW], h / out[PicPlace.PH]) >= PicPlace.KEEP - 0.01f);
                } else {
                    // Shown: nearly all of it (its height or its width), inside the cell.
                    assertTrue(what + ": nearly whole", Math.min(w / out[PicPlace.PW], h / out[PicPlace.PH]) >= PicPlace.SHOW_KEEP - 0.01f
                            || out[PicPlace.PW] <= w + 0.5f && out[PicPlace.PH] <= h + 0.5f);
                }
            }
        }
        // A very wide, short cell: the whole picture at its height, its faces in the middle of the part after the words.
        PicFill.row(1280, 720, TWO, 1340, 84, out);
        assertEquals(PicPlace.SHOW, out[PicPlace.KIND], 0f);
        float mid = (out[PicPlace.FL] + out[PicPlace.FR]) / 2f;
        assertEquals((PicPlace.room(1340, 84) + 1f) / 2f * 1340, mid, 30f);
        assertTrue("its edges feathered into the cell's own colour", out[PicPlace.FEATHER_L] > 10f && out[PicPlace.FEATHER_R] > 10f);
        // A short row's programme: the picture at its right, after the words as far as it can be.
        PicFill.row(1280, 720, TWO, 362, 88, out);
        assertFacesWhole("short row", out, 362, 88);
        assertEquals(362 - out[PicPlace.PW], out[PicPlace.X], 0.5f);
        // A tile as tall as the picture's shape: covering.
        PicFill.row(1280, 720, TWO, 400, 225, out);
        assertEquals(PicPlace.COVER, out[PicPlace.KIND], 0f);
    }

    @Test public void theInfoCardsFacesAreBetweenItsWordsAndWhatsNext() {
        float[] out = new float[PicPlace.SIZE];
        PicFill.card(1280, 720, TWO, 1824, 322, out);
        assertFacesWhole("card", out, 1824, 322);
        assertEquals(PicPlace.SHOW, out[PicPlace.KIND], 0f);
        // In card px: the words end by about 1000 (their limit before the faces), what's next starts at 1364.
        assertTrue("right of the words: " + out[PicPlace.FL], out[PicPlace.FL] >= InfoCard.WORDS_MIN + InfoCard.TEXT_X - InfoCard.X);
        assertTrue("left of what's next: " + out[PicPlace.FR], out[PicPlace.FR] <= InfoCard.NEXT_X - InfoCard.X);
        // Sky's 640 wide picture fills the card without being enlarged past 1.5; an EPG's 240 wide thumbnail would be.
        PicFill.card(640, 360, null, 1824, 322, out);
        assertTrue(!PicPlace.tooSmall(out));
        PicFill.card(240, 135, null, 1824, 322, out);
        assertTrue(PicPlace.tooSmall(out));
    }

    @Test public void theInfoCardsChannelNameSitsUnderItsLogoInsideTheCard() {
        // "The channel name should be underneath the channel logo here like before" (3.9): under the cell, in the card,
        // centred under the logo and clear of the words.
        assertTrue("under the logo", InfoCard.NAME_Y - 20f > 40f + InfoCard.CELL_H);
        assertTrue("in the card", InfoCard.NAME_Y + 8f < InfoCard.H);
        float mid = InfoCard.LOGO_X + InfoCard.LOGO_W / 2f;
        assertTrue("clear of the words", mid + InfoCard.NAME_W / 2f < InfoCard.TEXT_X);
        assertTrue("inside the card's left", mid - InfoCard.NAME_W / 2f > InfoCard.X);
    }

    @Test public void aCellCutShortKeepsItsPictureGliding() {
        // The picture made for a 900 px programme (180 px of it, its faces' middle 120 in); the cell cut as the times glide.
        float last = PicPlace.slide(900, 180, 120, 0, 900, 150);
        for (int cut = 1; cut < 880; cut++) {
            float x = PicPlace.slide(900, 180, 120, cut, 900, cut + 150);
            assertTrue("a jump at " + cut, Math.abs(x - last) <= 1.01f);
            last = x;
        }
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
