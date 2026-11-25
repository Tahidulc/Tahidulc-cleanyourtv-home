package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Where a programme's picture goes (PicPlace), as the family asked: never scaled past covering, no deep crop into a long
 * cell or a short band (the whole picture, or nearly, at the place's height, beside the words), faces always whole, a
 * small picture never enlarged past 1.5 times, the same framing as it scrolls (no jumps), and no hard edge inside a place.
 */
public class PicPlaceTest {
    /** Stand-in pictures of several shapes (their own pixels). */
    static final float[][] SHAPES = {{1280, 720}, {800, 600}, {600, 600}, {500, 750}, {240, 135}, {1260, 540}};
    static final float[] TWO_FACES = {0.55f, 0.18f, 0.86f, 0.58f}, ONE_FACE = {0.12f, 0.2f, 0.3f, 0.55f};
    /** Places: a long guide cell, a short cell, the TV's info card and details' picture, a phone's panel, a tile, a tall panel. */
    static final float[][] PLACES = {{1200, 84}, {330, 84}, {150, 84}, {60, 84}, {1824, 322}, {587, 330}, {1068, 520},
            {400, 225}, {360, 640}, {1290, 160}};

    private static float[] place(float[] shape, float[] box, float[] at, float room) {
        float[] out = new float[PicPlace.SIZE];
        PicPlace.place(shape[0], shape[1], box, at[0], at[1], room, 1f, out);
        return out;
    }

    @Test public void neverScaledPastWhatCoveringNeeds() {
        for (float[] s : SHAPES) {
            for (float[] p : PLACES) {
                for (float[] box : new float[][]{null, TWO_FACES, ONE_FACE}) {
                    float[] out = place(s, box, p, PicPlace.room(p[0], p[1]));
                    float cover = Math.max(p[0] / s[0], p[1] / s[1]);
                    String what = s[0] + "x" + s[1] + " in " + p[0] + "x" + p[1];
                    assertTrue(what + ": scale " + out[PicPlace.K] + " vs cover " + cover, out[PicPlace.K] <= cover * 1.001f);
                    assertEquals(what + ": its own shape", s[0] / s[1], out[PicPlace.PW] / out[PicPlace.PH], 0.01f);
                }
            }
        }
    }

    @Test public void aPlaceCloseToItsShapeIsCovered() {
        // 16:9 in a 16:9 tile, 4:3 in 16:9 (three quarters of its height shows), 21:9 in a 2.4:1 cell.
        float[] out = place(SHAPES[0], null, new float[]{400, 225}, 0f);
        assertEquals(PicPlace.COVER, out[PicPlace.KIND], 0f);
        assertTrue(out[PicPlace.X] <= 0.01f && out[PicPlace.X] + out[PicPlace.PW] >= 399.99f);
        assertTrue(out[PicPlace.Y] <= 0.01f && out[PicPlace.Y] + out[PicPlace.PH] >= 224.99f);
        assertEquals(PicPlace.COVER, place(SHAPES[1], null, new float[]{400, 225}, 0f)[PicPlace.KIND], 0f);
        assertEquals(PicPlace.COVER, place(SHAPES[5], null, new float[]{200, 84}, 0.3f)[PicPlace.KIND], 0f);
    }

    @Test public void aLongCellShowsTheWholePictureAtItsHeightAfterTheWords() {
        float w = 1200, h = 84;
        float room = PicPlace.room(w, h);
        for (float[] box : new float[][]{null, TWO_FACES}) {
            float[] out = place(SHAPES[0], box, new float[]{w, h}, room);
            assertEquals(PicPlace.SHOW, out[PicPlace.KIND], 0f);
            // Nearly all of it: its height a sliver more than the cell's, never the deep crop covering would be (14 %).
            assertEquals(h / PicPlace.SHOW_KEEP, out[PicPlace.PH], 2f);
            assertTrue("inside the cell", out[PicPlace.X] >= 0f && out[PicPlace.X] + out[PicPlace.PW] <= w);
            assertTrue("after the words: " + out[PicPlace.X], out[PicPlace.X] >= room * w - 0.5f);
            // Its edges feathered where it ends inside the cell: never a hard edge.
            assertTrue(out[PicPlace.FEATHER_L] >= PicPlace.FEATHER_MIN_PX && out[PicPlace.FEATHER_R] >= PicPlace.FEATHER_MIN_PX);
            assertTrue(PicPlace.whole(out, w, h));
        }
    }

    @Test public void theInfoCardAndAShortBandShowNearlyAllOfThePicture() {
        // The TV's info card (1824 x 322), its words over its left 52 %, what's next from 73 %.
        float[] out = new float[PicPlace.SIZE];
        PicPlace.place(1280, 720, TWO_FACES, 1824, 322, 0.52f, 0.735f, out);
        assertEquals(PicPlace.SHOW, out[PicPlace.KIND], 0f);
        assertTrue("at least 94 % of its height shows", 322 / out[PicPlace.PH] >= PicPlace.SHOW_KEEP - 0.01f);
        float mid = (out[PicPlace.FL] + out[PicPlace.FR]) / 2f;
        assertEquals("its faces between the words and what's next", (0.52f + 0.735f) / 2f * 1824, mid, 40f);
        assertTrue(PicPlace.whole(out, 1824, 322));
        // A phone's band held upright (1290 x 160, its words over 40 %).
        PicPlace.place(780, 439, null, 1290, 160, 0.4f, 1f, out);
        assertEquals(PicPlace.SHOW, out[PicPlace.KIND], 0f);
        assertTrue(out[PicPlace.X] >= 0.4f * 1290 - 1f);
    }

    @Test public void aNarrowCellOrTallPanelShowsItsWholeWidth() {
        float[] out = place(SHAPES[0], null, new float[]{60, 84}, 0f);
        assertEquals(PicPlace.SHOW, out[PicPlace.KIND], 0f);
        assertEquals(60 / PicPlace.SHOW_KEEP, out[PicPlace.PW], 1f);
        assertTrue("inside, top and foot feathered", out[PicPlace.Y] >= 0f && out[PicPlace.Y] + out[PicPlace.PH] <= 84f
                && out[PicPlace.FEATHER_T] > 0f && out[PicPlace.FEATHER_B] > 0f);
        // A portrait picture in a tall panel covers it; a wide one shows across it, a little above the middle.
        assertEquals(PicPlace.COVER, place(SHAPES[3], null, new float[]{360, 640}, 0f)[PicPlace.KIND], 0f);
        out = place(SHAPES[0], TWO_FACES, new float[]{360, 640}, 0f);
        assertEquals(PicPlace.SHOW, out[PicPlace.KIND], 0f);
        assertTrue(out[PicPlace.Y] > 0f && out[PicPlace.Y] < (640 - out[PicPlace.PH]) / 2f + 1f);
        assertTrue(PicPlace.whole(out, 360, 640));
    }

    @Test public void facesAreAlwaysWhole() {
        for (float[] s : SHAPES) {
            for (float[] p : PLACES) {
                for (float[] box : new float[][]{TWO_FACES, ONE_FACE, {0.05f, 0.05f, 0.95f, 0.95f}}) {
                    float[] out = place(s, box, p, PicPlace.room(p[0], p[1]));
                    assertTrue(s[0] + "x" + s[1] + " in " + p[0] + "x" + p[1] + ": faces " + out[PicPlace.FL] + ".." + out[PicPlace.FR] + ", "
                            + out[PicPlace.FT] + ".." + out[PicPlace.FB], PicPlace.whole(out, p[0], p[1]));
                }
            }
        }
    }

    @Test public void aSmallPictureIsNeverEnlargedPastOneAndAHalf() {
        // 240 x 135: fine for a TV guide cell (84 high), too small for the info card or a phone's panel.
        assertFalse(PicPlace.tooSmall(240, 135, null, 1200, 84, 0.3f, 1f));
        assertTrue(PicPlace.tooSmall(240, 135, null, 1824, 322, 0.52f, 0.735f));
        assertTrue(PicPlace.tooSmall(240, 135, null, 1068, 520, 0.6f, 1f));
        // Sky's 640 x 360 fills the info card and the TV's details picture (587 x 330) at less than 1.5.
        assertFalse(PicPlace.tooSmall(640, 360, null, 1824, 322, 0.52f, 0.735f));
        assertFalse(PicPlace.tooSmall(640, 360, null, 587, 330, 0.3f, 1f));
        float[] out = new float[PicPlace.SIZE];
        PicPlace.place(240, 135, null, 1824, 322, 0.52f, 0.735f, out);
        assertTrue(out[PicPlace.K] > PicPlace.MAX_UP);
    }

    @Test public void theSamePictureGlidesAlongItsCellWithNoJumps() {
        float full = 1200, pw = 160, subject = 80, words = 220;
        float last = PicPlace.slide(full, pw, subject, 0, 1000, words);
        // The cell cut by the guide's left edge a pixel a frame (the times gliding): it moves at most a pixel, never jumps.
        for (int cut = 1; cut < 1150; cut++) {
            float x = PicPlace.slide(full, pw, subject, cut, Math.min(full, cut + 1000), cut + words);
            assertTrue("a jump at " + cut + ": " + last + " → " + x, Math.abs(x - last) <= 1.01f);
            assertTrue(x >= 0f && x + pw <= full);
            last = x;
        }
        // Uncut, after the words, in the middle of the free part; a picture as wide as its cell stays put.
        assertEquals((words + full) / 2f - subject, PicPlace.slide(full, pw, subject, 0, full, words), 0.01f);
        assertEquals(0f, PicPlace.slide(400, 400, 200, 120, 400, 180), 0f);
        // In sight, wherever the part in sight is wide enough for it.
        float x = PicPlace.slide(full, pw, subject, 700, 1000, 760);
        assertTrue(x >= 700 && x + pw <= 1000);
    }

    @Test public void theRoomForAGuideCellsWords() {
        assertEquals(PicPlace.ROW_ROOM_MAX, PicPlace.room(200, 84), 0f);
        assertEquals(PicPlace.ROW_ROOM_MIN, PicPlace.room(2000, 84), 0f);
        assertTrue(PicPlace.none(null) && PicPlace.none(PicPlace.NO_FACES) && !PicPlace.none(TWO_FACES));
    }
}
