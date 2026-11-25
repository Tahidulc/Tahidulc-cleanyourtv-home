package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The launcher's football alerts while Homefield TV is in front (HomefieldBoard): what its scoreboard is handed is the
 * launcher's own board's drop, word for word, so a goal comes out of Homefield TV's board as it comes out of the
 * launcher's over TiviMate; the overlays' rule over TiviMate is the shared one (InChannel).
 */
public class HomefieldBoardTest {
    @Test public void theBoardsOwnWordsAreHandedOver() {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "401";
        g.homeName = "Eastgate";
        g.awayName = "Westbury";
        g.homeScore = "2";
        g.awayScore = "1";
        BoardDrop.Drop d = BoardCover.dropOfLine("⚽  Alex Player  67'", g, "goal", "home");
        com.gathin.broadcast.BoardAlert a = HomefieldBoard.of(d, "goal");
        assertEquals("401", a.gameId);
        assertEquals("goal", a.kind);
        assertEquals("GOAL", a.chip);
        assertEquals("Eastgate 2–1 Westbury", a.title);
        assertEquals("Alex Player 67'", a.line);
        assertEquals("401|2–1", a.key);
        assertEquals(1, a.side);
        assertEquals(d.colour, a.colour);
        assertEquals(d.homeColour, a.homeColour);
        assertEquals(d.awayColour, a.awayColour);
        assertSame(d.what, a.what);
        assertFalse(a.lineups);
        // The line-ups go as the line-ups, whatever kind passed them.
        d.lineups = true;
        assertEquals("lineups", HomefieldBoard.of(d, "kickoff").kind);
        assertTrue(HomefieldBoard.of(d, "kickoff").lineups);
    }

    @Test public void theOverlaysRuleOverTiviMateIsTheSharedOne() {
        assertTrue(OverlayScope.inChannel(OverlayScope.TIVIMATE, false));
        assertFalse(OverlayScope.inChannel(OverlayScope.TIVIMATE, true));
        assertFalse(OverlayScope.inChannel(OverlayScope.HOMEFIELD, false));
        assertEquals(com.gathin.broadcast.InChannel.shows(true, true, true, false), OverlayScope.inChannel(OverlayScope.KLEVA, false));
    }
}
