package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The line-ups' styles in turn, the goal shirt's words and the board's shirt frames' size. */
public class KitBoardTest {
    @Test public void lineupsStylesInTurn() {
        assertEquals("stadium", ClassicIntro.nextLineups("classic"));
        assertEquals("studio", ClassicIntro.nextLineups("stadium"));
        assertEquals("off", ClassicIntro.nextLineups("studio"));
        assertEquals("classic", ClassicIntro.nextLineups("off"));
        assertEquals("classic", ClassicIntro.nextLineups(null));
        assertEquals("studio", ClassicIntro.lineupsPage("studio"));
        assertEquals("stadium", ClassicIntro.lineupsPage("stadium"));
        assertEquals("lineups", ClassicIntro.lineupsPage("classic"));
    }

    @Test public void wordsUnderTheShirt() {
        assertEquals("41'  JAY HOLLIS", KitBoard.caption("41'", "Jay Hollis", false));
        assertEquals("41'  JAY HOLLIS", KitBoard.caption("41", "Jay Hollis", false));
        assertEquals("90'+3'  SAM OKAFOR (PEN)", KitBoard.caption("90'+3'", "Sam Okafor", true));
        assertEquals("LEO BRANDT", KitBoard.caption("", "Leo Brandt", false));
    }

    @Test public void framesALittleTallerThanTheBoard() {
        assertEquals(72, KitBoard.frameSize(60));
        assertEquals(64, KitBoard.frameSize(10));
        assertEquals(224, KitBoard.frameSize(400));
    }

    @Test public void sidesTurnedTowardsTheMiddle() {
        assertEquals(0.35f, BoardBug.kitRest(true), 1e-6);
        assertEquals(-0.35f, BoardBug.kitRest(false), 1e-6);
    }
}
