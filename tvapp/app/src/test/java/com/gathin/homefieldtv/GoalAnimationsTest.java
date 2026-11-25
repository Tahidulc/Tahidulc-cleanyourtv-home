package com.gathin.homefieldtv;

import com.gathin.broadcast.BoardFx;
import com.gathin.broadcast.ClubGoals;
import com.gathin.broadcast.CompBug;
import com.gathin.broadcast.GoalLooks;
import com.gathin.broadcast.GoalReveal;
import com.gathin.broadcast.Sfx;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The goal animation chosen on Homefield TV's board (GoalLooks), as on the launcher's: the clubs' own and the generic
 * (GoalReveal) picked for a goal, their timing among the board's sets, the score under them, where they play, the
 * hold-OK menu's line; "As today" as it was, and no 3D shirt here.
 */
public class GoalAnimationsTest {

    private static BoardBug.Look look() {
        BoardBug.Look l = new BoardBug.Look();
        l.home = "LIV";
        l.away = "EVE";
        l.homeColour = 0xFFC8102E;
        l.awayColour = 0xFF274488;
        l.score = "0–0";
        return l;
    }

    @Test
    public void aGoalWithClubAnimationsPlaysTheScorersOwn() {
        BoardBug.Look l = look();
        assertSame(ClubGoals.byId("364"), BoardBug.revealFor(GoalLooks.CLUBS, 1, "364", "368", "Liverpool", "Everton", l));
        assertSame(ClubGoals.byId("368"), BoardBug.revealFor(GoalLooks.CLUBS, 2, "364", "368", "Liverpool", "Everton", l));
        assertEquals("p:YNWA", BoardBug.revealFor(GoalLooks.CLUBS, 1, "", "", "Liverpool FC", "Everton", l).motif);
        // an unknown club: the generic in its colour on the board; Generic chosen: every team's
        ClubGoals.Club g = BoardBug.revealFor(GoalLooks.CLUBS, 1, "9999", "368", "Whitebrook Rovers", "Everton", l);
        assertTrue(g.generic());
        assertEquals(0xFFC8102E, g.sweep0);
        assertTrue(BoardBug.revealFor(GoalLooks.GENERIC, 1, "364", "368", "Liverpool", "Everton", l).generic());
    }

    @Test
    public void asTodayIsUnchangedAndTheNewOnesAreOnEveryLook() {
        int todays = BoardFx.goalSet("game 7");
        assertNull(BoardBug.revealFor(GoalLooks.TODAY, 1, "364", "368", "Liverpool", "Everton", look()));
        assertEquals(BoardFx.GOAL_SEQUENCE, BoardBug.setFor(BoardBug.Style.CLASSIC, GoalLooks.set(GoalLooks.TODAY, todays)));
        assertEquals(BoardFx.GOAL_BAND, BoardBug.setFor(BoardBug.Style.KITS, GoalLooks.set(GoalLooks.TODAY, todays)));
        for (BoardBug.Style s : BoardBug.Style.values()) {
            assertEquals(s.name(), BoardFx.GOAL_REVEAL, BoardBug.setFor(s, GoalLooks.set(GoalLooks.CLUBS, todays)));
        }
        assertEquals("1–0", BoardBug.scoreDrawn(BoardFx.GOAL_SEQUENCE, 100, "0–0", "1–0"));
    }

    @Test
    public void theNewAnimationsTimingAndScore() {
        int set = BoardFx.GOAL_REVEAL;
        assertEquals(GoalReveal.TOTAL_MS, BoardBug.goalMs(set));
        assertTrue(BoardBug.meshAt(set) > BoardBug.goalMs(set));
        for (long t = 0; t < GoalReveal.TOTAL_MS; t += 100) {
            assertFalse(BoardBug.bandOn(set, t));
            assertEquals(-1, BoardBug.labAt(set, t));
            assertArrayEquals(new float[]{0f, 1f}, BoardBug.moveAt(set, false, t), 1e-6f);
        }
        assertEquals("0–0", BoardBug.scoreDrawn(set, GoalReveal.SCORE_AT - 1, "0–0", "1–0"));
        assertEquals("1–0", BoardBug.scoreDrawn(set, GoalReveal.SCORE_AT, "0–0", "1–0"));
        assertEquals(Long.valueOf(GoalReveal.BURST_FROM), BoardBug.goalSounds(set, false).get(Sfx.BURST));
        assertFalse(BoardBug.revealWhole(null, true, 300, 50));
        assertTrue(BoardBug.revealWhole(CompBug.Kind.UCL, true, 300, 50));
        assertFalse(BoardBug.revealOnShape(CompBug.Kind.PL));
    }

    @Test
    public void theHoldOkMenuHasTheGoalAnimationWithoutTheShirt() {
        BoardMenu.MenuState m = new BoardMenu.MenuState();
        String[] lines = BoardMenu.menuLines(m);
        assertEquals(BoardMenu.LINES, lines.length);
        assertEquals("Goal animation:  ‹  As today  ›", lines[BoardMenu.M_GOAL]);
        assertTrue(lines[BoardMenu.M_SOUND].startsWith("Goal sound:"));
        assertEquals(BoardMenu.M_GOAL + 1, BoardMenu.M_SOUND);
        m.goalLook = GoalLooks.name(GoalLooks.CLUBS);
        assertEquals("Goal animation:  ‹  Club animations  ›", BoardMenu.menuLines(m)[BoardMenu.M_GOAL]);
        // Homefield TV has no 3D shirt: never offered, Burst on to Club animations
        for (String k : GoalLooks.offered(false)) assertNotEquals(GoalLooks.SHIRT, k);
        assertEquals(GoalLooks.CLUBS, GoalLooks.next(GoalLooks.BURST, 1, false));
    }
}
