package com.gathin.tvhomephone.live;

import com.gathin.broadcast.BoardFx;
import com.gathin.broadcast.ClubGoals;
import com.gathin.broadcast.CompBug;
import com.gathin.broadcast.GoalLooks;
import com.gathin.broadcast.GoalReveal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The goal animation chosen on the phone's board (GoalLooks, ScoreBoard.goalLook): the clubs' own and the generic
 * (GoalReveal) picked for a goal on OverlayBug, their timing among its sets, the score under them, where they play;
 * "As today" as it was. Its pick in the scoreboard's pop-up menu: BoardMenuTest.
 */
public class GoalAnimationsTest {

    private static OverlayBug.Look look() {
        OverlayBug.Look l = new OverlayBug.Look();
        l.home = "BAY";
        l.away = "BVB";
        l.homeColour = 0xFFDC052D;
        l.awayColour = 0xFFFDE100;
        l.score = "0–0";
        return l;
    }

    @Test
    public void aGoalWithClubAnimationsPlaysTheScorersOwn() {
        OverlayBug.Look l = look();
        assertSame(ClubGoals.byId("132"), OverlayBug.revealFor(GoalLooks.CLUBS, 1, "132", "124", "Bayern Munich", "Borussia Dortmund", l));
        assertSame(ClubGoals.byId("124"), OverlayBug.revealFor(GoalLooks.CLUBS, 2, "132", "124", "Bayern Munich", "Borussia Dortmund", l));
        assertEquals("p:ECHTE LIEBE", OverlayBug.revealFor(GoalLooks.CLUBS, 2, null, null, "Bayern Munich", "Dortmund", l).motif);
        // an unknown club the generic in its colour on the board; Generic chosen, every team's
        ClubGoals.Club g = OverlayBug.revealFor(GoalLooks.CLUBS, 1, "", "", "Whitebrook Rovers", "Dortmund", l);
        assertTrue(g.generic());
        assertEquals(0xFFDC052D, g.sweep0);
        assertTrue(OverlayBug.revealFor(GoalLooks.GENERIC, 2, "132", "124", "Bayern Munich", "Borussia Dortmund", l).generic());
        assertNull(OverlayBug.revealFor(GoalLooks.TODAY, 1, "132", "124", "Bayern Munich", "Borussia Dortmund", l));
    }

    @Test
    public void asTodayIsUnchangedAndTheNewOnesAreOnEveryLook() {
        String game = "game 12";
        assertEquals(OverlayBug.setFor(OverlayBug.Style.CLASSIC, game), OverlayBug.setFor(OverlayBug.Style.CLASSIC, game, GoalLooks.TODAY));
        assertEquals(BoardFx.GOAL_SEQUENCE, OverlayBug.setFor(OverlayBug.Style.CLASSIC, game, GoalLooks.TODAY));
        assertEquals(BoardFx.GOAL_BAND, OverlayBug.setFor(OverlayBug.Style.KITS, game, GoalLooks.TODAY));
        assertEquals(BoardFx.GOAL_BAND, OverlayBug.setFor(OverlayBug.Style.KITS, game, GoalLooks.FLIP));
        assertEquals(BoardFx.GOAL_FLIP, OverlayBug.setFor(OverlayBug.Style.CLASSIC, game, GoalLooks.FLIP));
        for (OverlayBug.Style s : OverlayBug.Style.values()) {
            assertEquals(s.name(), BoardFx.GOAL_REVEAL, OverlayBug.setFor(s, game, GoalLooks.CLUBS));
        }
    }

    @Test
    public void theNewAnimationsTimingAndScore() {
        int set = BoardFx.GOAL_REVEAL;
        assertEquals(GoalReveal.TOTAL_MS, OverlayBug.goalLen(set));
        assertTrue(OverlayBug.meshAt(set) > OverlayBug.goalLen(set));
        for (long t = 0; t < GoalReveal.TOTAL_MS; t += 100) {
            assertFalse(OverlayBug.bandOn(set, t));
            assertEquals(-1, OverlayBug.labAt(set, t));
        }
        assertEquals("0–0", OverlayBug.scoreDrawn(set, GoalReveal.SCORE_AT - 1, "0–0", "1–0"));
        assertEquals("1–0", OverlayBug.scoreDrawn(set, GoalReveal.SCORE_AT, "0–0", "1–0"));
        assertEquals("1–0", OverlayBug.scoreDrawn(BoardFx.GOAL_SEQUENCE, 100, "0–0", "1–0"));
        assertFalse(OverlayBug.revealWhole(null, true, 300, 50));
        assertTrue(OverlayBug.revealWhole(CompBug.Kind.DUO, true, 300, 50));
        assertTrue(OverlayBug.revealWhole(null, false, 0, 0));
        assertTrue(OverlayBug.revealOnShape(CompBug.Kind.DUO));
        assertFalse(OverlayBug.revealOnShape(CompBug.Kind.LIGUE1));
    }
}
