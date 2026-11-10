package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Move and resize on the phone: the scoreboard's and the logo's width set apart from their height and size. */
public class SizeAndWidthTest {
    @Test public void theScoreboardGetsWiderOrNarrowerItsHeightKept() {
        BoardPlace p = new BoardPlace();
        p.bx = 0.05f;
        p.by = 0.05f;
        p.bh = 0.08f;
        p.widthBoard(1.5f, 0.3f);
        assertEquals(0.45f, p.bw, 1e-4f);
        assertEquals(0.08f, p.bh, 1e-6f);
        for (int i = 0; i < 100; i++) p.widthBoard(1.12f, 0f);
        assertEquals(BoardPlace.MAX_BW, p.bw, 1e-4f);
        // Kept on the picture as it grows.
        assertTrue(p.bx + p.bw <= 1f + 1e-4f);
        for (int i = 0; i < 100; i++) p.widthBoard(1 / 1.12f, 0f);
        assertEquals(BoardPlace.MIN_BW, p.bw, 1e-4f);
    }

    @Test public void theLogoGetsWiderOrNarrowerAboutItsMiddle() {
        BoardPlace p = new BoardPlace();
        p.lx = 0.8f;
        p.ly = 0.05f;
        p.lw = 0.1f;
        p.lh = 0.06f;
        p.widthLogo(1.2f);
        assertEquals(0.12f, p.lw, 1e-4f);
        assertEquals(0.06f, p.lh, 1e-6f);
        assertEquals(0.85f, p.lx + p.lw / 2, 1e-4f);
        for (int i = 0; i < 60; i++) p.widthLogo(1.12f);
        assertEquals(BoardPlace.MAX_W, p.lw, 1e-4f);
        assertTrue(p.lx + p.lw <= 1f + 1e-4f);
    }
}
