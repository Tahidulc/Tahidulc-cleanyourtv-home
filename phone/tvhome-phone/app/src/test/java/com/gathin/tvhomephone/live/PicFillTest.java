package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A programme's picture filling a guide cell or the panel: its faces whole, and clear of the words' part. */
public class PicFillTest {
    private static final float[] TWO_FACES = {0.40f, 0.15f, 0.75f, 0.60f}, ONE_FACE = {0.55f, 0.20f, 0.70f, 0.55f};
    private static final float STILL = 16f / 9f;

    @Test public void aWideCellKeepsItsFacesWholeBesideTheWords() {
        // An hour's cell on a phone: 567 x 142 px.
        float w = 567, h = 142;
        float[] out = new float[PicFill.SIZE];
        PicFill.row(STILL, TWO_FACES, w, h, out);
        assertTrue(PicFill.whole(out, w, h));
        float room = PicFill.room(w, h) * w;
        assertTrue("faces after the words: " + out[PicFill.FL] + " vs " + room, out[PicFill.FL] >= room - 1f);
        assertTrue(out[PicFill.FR] <= w);
        assertTrue(out[PicFill.FT] >= 0f && out[PicFill.FB] <= h);
        // The picture covers the cell (or its own colours are behind it, the ambient fill).
        if (out[PicFill.KIND] == PicFill.COVER) {
            assertTrue(out[PicFill.X] <= 0.5f && out[PicFill.X] + STILL * out[PicFill.K] >= w - 0.5f);
            assertTrue(out[PicFill.Y] <= 0.5f && out[PicFill.Y] + out[PicFill.K] >= h - 0.5f);
        }
    }

    @Test public void aVeryWideCellTakesTheAmbientFillFacesWhole() {
        // Two hours on a tablet: 1320 x 124 px.
        float w = 1320, h = 124;
        float[] out = new float[PicFill.SIZE];
        PicFill.row(STILL, TWO_FACES, w, h, out);
        assertEquals(PicFill.AMBIENT, out[PicFill.KIND], 0f);
        assertTrue(PicFill.whole(out, w, h));
        assertTrue(out[PicFill.FL] >= PicFill.room(w, h) * w - 1f);
        assertTrue(out[PicFill.FEATHER_L] > 0f);
    }

    @Test public void aSmallCellNeverShowsHalfAFace() {
        // A narrow cell can't hold faces this wide whole: whole() says so, and the cell takes the picture's colours.
        float[] wide = {0.05f, 0.10f, 0.95f, 0.70f};
        float[] out = new float[PicFill.SIZE];
        PicFill.row(STILL, wide, 120, 142, out);
        assertFalse(PicFill.whole(out, 120, 142));
        // With no faces found there is nothing to cut.
        PicFill.row(STILL, Faces.FALLBACK, 120, 142, out);
        assertTrue(PicFill.whole(out, 120, 142));
    }

    @Test public void thePanelPutsTheFacesRightOfItsWords() {
        // The panel upright: 1080 x 430 px, its words over the left 60 %.
        float w = 1080, h = 430;
        float[] out = new float[PicFill.SIZE];
        PicFill.layout(STILL, ONE_FACE, w, h, PicFill.PANEL_ROOM, 1f, true, out);
        assertTrue(PicFill.whole(out, w, h));
        assertTrue("faces at " + out[PicFill.FL], out[PicFill.FL] >= PicFill.PANEL_ROOM * w - 1f);
        // Faces whose picture puts them at the left are moved over to the right too.
        float[] left = {0.10f, 0.20f, 0.30f, 0.60f};
        PicFill.layout(STILL, left, w, h, PicFill.PANEL_ROOM, 1f, true, out);
        assertTrue(PicFill.whole(out, w, h));
        assertTrue(out[PicFill.FL] >= PicFill.PANEL_ROOM * w - 1f);
    }

    @Test public void aCutCellShowsAWindowOnItsFaces() {
        // A three-hour picture made 1080 wide, faces at 700..820; 400 px of it in sight, the words taking 180.
        float x = PicFill.window(1080, 400, 700, 820, 180, 20);
        assertTrue(700 - x >= 180);
        assertTrue(820 - x <= 400);
        // In sight whole: no window.
        assertEquals(0f, PicFill.window(400, 400, 100, 200, 50, 10), 0f);
    }

    @Test public void theShadeEndsBeforeTheFaces() {
        float fade = 105, least = 37, gap = 10;
        // Faces well past the words: the whole fade.
        assertEquals(fade, PicFill.shadeFade(200, 500, fade, least, gap), 0.01f);
        // Faces near: the fade stops short of them.
        float f = PicFill.shadeFade(200, 270, fade, least, gap);
        assertTrue(200 + f <= 270 - gap + 0.01f);
        // Faces so near the words that little is left: never under the least fade.
        assertEquals(least, PicFill.shadeFade(200, 230, fade, least, gap), 0.01f);
        // Faces under the words: that cell takes the picture's own colours instead (clear() is false).
        assertFalse(PicFill.clear(true, 150, 200, gap));
        assertTrue(PicFill.clear(true, 260, 200, gap));
        assertTrue(PicFill.clear(false, 0, 200, gap));
    }
}
