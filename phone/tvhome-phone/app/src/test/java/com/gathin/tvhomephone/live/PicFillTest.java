package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.PicPlace;

import org.junit.Test;

/**
 * The phone's places for a programme's picture, by the rule shared with Homefield TV (PicPlace): a guide card seen whole
 * after its words (never the deep crop covering a long card would be), the panel's picture right of its words, a small
 * picture never enlarged past 1.5 times, and the words' shade stopping before the faces.
 */
public class PicFillTest {
    static final float[] TWO_FACES = {0.55f, 0.18f, 0.86f, 0.58f};

    @Test public void aLongGuideCardShowsTheWholePictureAfterItsWords() {
        // A phone's card (2.625 density): 60 dp high, two hours long.
        float w = 1290, h = 158;
        float[] out = new float[PicPlace.SIZE];
        PicFill.row(780, 439, TWO_FACES, w, h, out);
        assertEquals(PicPlace.SHOW, out[PicPlace.KIND], 0f);
        assertTrue(PicPlace.whole(out, w, h));
        assertTrue("after the words", out[PicPlace.FL] >= PicFill.room(w, h) * w - 1f);
        assertTrue("nearly all of its height", h / out[PicPlace.PH] >= PicPlace.SHOW_KEEP - 0.01f);
        // A card of the picture's own shape is covered.
        PicFill.row(780, 439, TWO_FACES, 280, 158, out);
        assertEquals(PicPlace.COVER, out[PicPlace.KIND], 0f);
    }

    @Test public void thePanelsPictureCoversItOrSitsRightOfItsWords() {
        float w = 1028, h = 470;
        float[] out = new float[PicPlace.SIZE];
        // Close to its shape: covering it, no further than that (its faces whole, wherever they are in it).
        PicPlace.place(1280, 720, new float[]{0.12f, 0.2f, 0.3f, 0.55f}, w, h, PicFill.PANEL_ROOM, 1f, out);
        assertEquals(PicPlace.COVER, out[PicPlace.KIND], 0f);
        assertEquals(w / 1280f, out[PicPlace.K], 1e-4f);
        assertTrue(PicPlace.whole(out, w, h));
        // A square picture: shown whole at the panel's height, right of its words.
        PicPlace.place(600, 600, TWO_FACES, w, h, PicFill.PANEL_ROOM, 1f, out);
        assertEquals(PicPlace.SHOW, out[PicPlace.KIND], 0f);
        assertTrue(PicPlace.whole(out, w, h));
        assertTrue("right of the words: " + out[PicPlace.FL], out[PicPlace.FL] >= PicFill.PANEL_ROOM * w - 1f);
        // Sky's picture at 1280 fills it; an EPG's 300 x 169 thumbnail would be enlarged past 1.5: the next one instead.
        assertTrue(!PicPlace.tooSmall(1280, 720, null, w, h, PicFill.PANEL_ROOM, 1f));
        assertTrue(PicPlace.tooSmall(300, 169, null, w, h, PicFill.PANEL_ROOM, 1f));
    }

    @Test public void theShadeStopsBeforeTheFaces() {
        float fade = 40, least = 14, gap = 4;
        assertEquals(fade, PicFill.shadeFade(200, 500, fade, least, gap), 0.01f);
        assertEquals(26f, PicFill.shadeFade(200, 230, fade, least, gap), 0.01f);
        assertEquals(least, PicFill.shadeFade(200, 210, fade, least, gap), 0.01f);
    }
}
