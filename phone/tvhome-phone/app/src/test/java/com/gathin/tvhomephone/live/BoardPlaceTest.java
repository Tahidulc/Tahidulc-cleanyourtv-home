package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The scoreboard's and the logo mark's places set by hand: on the picture, sizes within bounds, kept as written. */
public class BoardPlaceTest {
    private static final float E = 1e-4f;

    @Test public void nothingIsPlacedAtFirst() {
        BoardPlace p = new BoardPlace();
        assertFalse(p.board());
        assertFalse(p.logo());
        BoardPlace q = BoardPlace.from(null);
        assertFalse(q.board());
        assertFalse(q.logo());
    }

    @Test public void movesStayOnThePicture() {
        BoardPlace p = new BoardPlace();
        p.bh = 0.08f;
        p.bx = 0;
        p.moveBoard(0.5f, 0.2f, 0.3f);
        assertEquals(0.5f, p.bx, E);
        assertEquals(0.2f, p.by, E);
        // Off the right and the bottom: up against them.
        p.moveBoard(0.9f, 0.99f, 0.3f);
        assertEquals(0.7f, p.bx, E);
        assertEquals(0.92f, p.by, E);
        // Off the left and the top.
        p.moveBoard(-0.2f, -1f, 0.3f);
        assertEquals(0f, p.bx, E);
        assertEquals(0f, p.by, E);
        p.lw = 0.1f;
        p.lh = 0.06f;
        p.lx = 0;
        p.moveLogo(1.2f, 0.5f);
        assertEquals(0.9f, p.lx, E);
        assertEquals(0.5f, p.ly, E);
    }

    @Test public void sizesStayWithinBounds() {
        BoardPlace p = new BoardPlace();
        p.bx = 0.1f;
        p.by = 0.9f;
        p.bh = 0.08f;
        p.sizeBoard(1.5f);
        assertEquals(0.12f, p.bh, E);
        // Grown at the foot: moved up to stay on the picture.
        assertEquals(0.88f, p.by, E);
        for (int i = 0; i < 30; i++) p.sizeBoard(1.5f);
        assertEquals(BoardPlace.MAX_H, p.bh, E);
        for (int i = 0; i < 60; i++) p.sizeBoard(0.5f);
        assertEquals(BoardPlace.MIN_H, p.bh, E);
    }

    @Test public void theLogoMarkGrowsAboutItsMiddleKeepingItsShape() {
        BoardPlace p = new BoardPlace();
        p.lx = 0.4f;
        p.ly = 0.4f;
        p.lw = 0.1f;
        p.lh = 0.05f;
        p.sizeLogo(2f);
        assertEquals(0.2f, p.lw, E);
        assertEquals(0.1f, p.lh, E);
        assertEquals(0.35f, p.lx, E);
        assertEquals(0.375f, p.ly, E);
        for (int i = 0; i < 20; i++) p.sizeLogo(2f);
        assertEquals(BoardPlace.MAX_W, p.lw, E);
        assertTrue(p.lx >= 0 && p.lx + p.lw <= 1 + E);
    }

    @Test public void placesComeBackAsWritten() {
        BoardPlace p = new BoardPlace();
        p.bx = 0.05f;
        p.by = 0.06f;
        p.bh = 0.09f;
        p.lx = 0.8f;
        p.ly = 0.05f;
        p.lw = 0.12f;
        p.lh = 0.07f;
        BoardPlace q = BoardPlace.from(p.json());
        assertTrue(q.board());
        assertTrue(q.logo());
        assertEquals(0.05f, q.bx, E);
        assertEquals(0.09f, q.bh, E);
        assertEquals(0.8f, q.lx, E);
        assertEquals(0.07f, q.lh, E);
        // Only the scoreboard placed.
        BoardPlace r = new BoardPlace();
        r.bx = 0.1f;
        r.bh = 0.1f;
        BoardPlace s = BoardPlace.from(r.json());
        assertTrue(s.board());
        assertFalse(s.logo());
    }
}
