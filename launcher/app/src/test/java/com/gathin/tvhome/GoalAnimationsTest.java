package com.gathin.tvhome;

import com.gathin.broadcast.BoardFx;
import com.gathin.broadcast.ClubGoals;
import com.gathin.broadcast.CompBug;
import com.gathin.broadcast.GoalLooks;
import com.gathin.broadcast.GoalReveal;
import com.gathin.broadcast.Sfx;

import org.junit.Test;

import java.util.LinkedHashMap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The goal animation chosen on the TV's board (GoalLooks): the clubs' own and the generic (GoalReveal) picked for a goal,
 * their timing among the board's sets, the score under them, where they play, and the hold-OK menu's lines; "As today"
 * as it was.
 */
public class GoalAnimationsTest {

    private static BoardBug.Look look() {
        BoardBug.Look l = new BoardBug.Look();
        l.home = "ARS";
        l.away = "CHE";
        l.homeColour = 0xFFEF0107;
        l.awayColour = 0xFF034694;
        l.score = "0–0";
        return l;
    }

    @Test
    public void aGoalWithClubAnimationsPlaysTheScorersOwn() {
        BoardBug.Look l = look();
        assertSame(ClubGoals.byId("359"), BoardBug.revealFor(GoalLooks.CLUBS, 1, "359", "363", "Arsenal", "Chelsea", l));
        assertSame(ClubGoals.byId("363"), BoardBug.revealFor(GoalLooks.CLUBS, 2, "359", "363", "Arsenal", "Chelsea", l));
        // no id from the feed: its name
        assertSame(ClubGoals.byId("363"), BoardBug.revealFor(GoalLooks.CLUBS, 2, "", null, "Arsenal", "Chelsea FC", l));
    }

    @Test
    public void anUnknownClubHasTheGenericInItsColourOnTheBoard() {
        BoardBug.Look l = look();
        l.home = "WHI";
        l.homeColour = 0xFF1B458F;
        ClubGoals.Club g = BoardBug.revealFor(GoalLooks.CLUBS, 1, "77777", "363", "Whitebrook Rovers", "Chelsea", l);
        assertTrue(g.generic());
        assertEquals(0xFF1B458F, g.sweep0);
        assertEquals(ClubGoals.BALL, g.motif);
        // Generic chosen: every team's the generic, the 96 too
        assertTrue(BoardBug.revealFor(GoalLooks.GENERIC, 2, "359", "363", "Arsenal", "Chelsea", l).generic());
        assertEquals(0xFF034694, BoardBug.revealFor(GoalLooks.GENERIC, 2, "359", "363", "Arsenal", "Chelsea", l).sweep0);
    }

    @Test
    public void asTodayIsUnchanged() {
        String game = "any game 1";
        int todays = BoardFx.goalSet(game);
        assertEquals(GoalLooks.TODAY, GoalLooks.of(null));
        assertNull(BoardBug.revealFor(GoalLooks.TODAY, 1, "359", "363", "Arsenal", "Chelsea", look()));
        // every look the sequence, the 3D kits its gold GOAL band, as before the choice came
        assertEquals(BoardFx.GOAL_SEQUENCE, BoardBug.setFor(BoardBug.Style.CLASSIC, GoalLooks.set(GoalLooks.TODAY, todays)));
        assertEquals(BoardFx.GOAL_SEQUENCE, BoardBug.setFor(BoardBug.Style.PL_WORLD, GoalLooks.set(GoalLooks.TODAY, todays)));
        assertEquals(BoardFx.GOAL_BAND, BoardBug.setFor(BoardBug.Style.KITS, GoalLooks.set(GoalLooks.TODAY, todays)));
        // the score as ever under today's sets
        for (int set : new int[]{BoardFx.GOAL_BAND, BoardFx.GOAL_FLIP, BoardFx.GOAL_BURST, BoardFx.GOAL_SEQUENCE}) {
            assertEquals("1–0", BoardBug.scoreDrawn(set, 100, "0–0", "1–0"));
            assertTrue(BoardBug.meshAt(set) < BoardBug.goalMs(set));
        }
    }

    @Test
    public void theOtherChoicesAreTheBoardsOwnSetsOnEveryLook() {
        assertEquals(BoardFx.GOAL_BAND, BoardBug.setFor(BoardBug.Style.CLASSIC, GoalLooks.set(GoalLooks.BAND, 3)));
        assertEquals(BoardFx.GOAL_FLIP, BoardBug.setFor(BoardBug.Style.CARD, GoalLooks.set(GoalLooks.FLIP, 3)));
        assertEquals(BoardFx.GOAL_BURST, BoardBug.setFor(BoardBug.Style.FLOODLIGHT, GoalLooks.set(GoalLooks.BURST, 3)));
        assertEquals(BoardFx.GOAL_BAND, BoardBug.setFor(BoardBug.Style.GLASS, GoalLooks.set(GoalLooks.SHIRT, 3)));
        // the new ones on every look, the 3D kits too
        for (BoardBug.Style s : BoardBug.Style.values()) {
            assertEquals(s.name(), BoardFx.GOAL_REVEAL, BoardBug.setFor(s, GoalLooks.set(GoalLooks.CLUBS, 3)));
            assertEquals(s.name(), BoardFx.GOAL_REVEAL, BoardBug.setFor(s, GoalLooks.set(GoalLooks.GENERIC, 3)));
        }
    }

    @Test
    public void theNewAnimationsTimingAmongTheSets() {
        int set = BoardFx.GOAL_REVEAL;
        assertEquals(GoalReveal.TOTAL_MS, BoardBug.setMs(set));
        assertEquals(GoalReveal.TOTAL_MS, BoardBug.goalMs(set));
        // no net, no GOAL band, no Lab moment, no figure moving: the animation shows the new score itself
        assertTrue(BoardBug.meshAt(set) > BoardBug.goalMs(set));
        for (long t = 0; t < GoalReveal.TOTAL_MS; t += 50) {
            assertFalse(BoardBug.bandOn(set, t));
            assertEquals(-1, BoardBug.labAt(set, t));
            assertArrayEquals(new float[]{0f, 1f}, BoardBug.moveAt(set, true, t), 1e-6f);
        }
        // an alert beside the board waits for it
        assertEquals(GoalReveal.TOTAL_MS, BoardBug.bandLeft(1000, set, 1000));
        assertEquals(0, BoardBug.bandLeft(1000, set, 1000 + GoalReveal.TOTAL_MS));
        // its quiet sound on GOAL!'s burst, no net's
        LinkedHashMap<String, Long> sounds = BoardBug.goalSounds(set, false);
        assertEquals(Long.valueOf(GoalReveal.BURST_FROM), sounds.get(Sfx.BURST));
        assertFalse(sounds.containsKey(Sfx.NET));
    }

    @Test
    public void theScoreChangesUnderTheSecondSweep() {
        int set = BoardFx.GOAL_REVEAL;
        assertEquals("0–0", BoardBug.scoreDrawn(set, 0, "0–0", "1–0"));
        assertEquals("0–0", BoardBug.scoreDrawn(set, GoalReveal.SCORE_AT - 1, "0–0", "1–0"));
        assertEquals("1–0", BoardBug.scoreDrawn(set, GoalReveal.SCORE_AT, "0–0", "1–0"));
        assertEquals("1–0", BoardBug.scoreDrawn(set, GoalReveal.TOTAL_MS, "0–0", "1–0"));
        assertEquals("1–0", BoardBug.scoreDrawn(set, -1, "0–0", "1–0"));
        // nothing known before it: the score as it is
        assertEquals("1–0", BoardBug.scoreDrawn(set, 100, "", "1–0"));
        assertEquals("1–0", BoardBug.scoreDrawn(set, 100, null, "1–0"));
    }

    @Test
    public void itPlaysOverTheScorersPartOrTheWholeBoard() {
        // a long part (Classic's name, score and the other's name): there
        assertFalse(BoardBug.revealWhole(null, true, 300, 50));
        // a competition's look: its row whole, kept to its shapes
        assertTrue(BoardBug.revealWhole(CompBug.Kind.PL, true, 300, 50));
        assertTrue(BoardBug.revealWhole(CompBug.Kind.DUO, false, 0, 0));
        // a look with no part of its own (Card, Minimal, two rows), or one too short
        assertTrue(BoardBug.revealWhole(null, false, 0, 0));
        assertTrue(BoardBug.revealWhole(null, true, 120, 50));
        // laid on a look's own shapes (Duo's pills, the segments), but the world feed's row whole (its score stands through it)
        assertTrue(BoardBug.revealOnShape(CompBug.Kind.DUO));
        assertTrue(BoardBug.revealOnShape(CompBug.Kind.UCL));
        assertFalse(BoardBug.revealOnShape(CompBug.Kind.PL));
        assertFalse(BoardBug.revealOnShape(CompBug.Kind.LIGUE1));
        assertFalse(BoardBug.revealOnShape(CompBug.Kind.FACUP));
        assertFalse(BoardBug.revealOnShape(CompBug.Kind.R2004));
    }

    @Test
    public void aCompetitionsScoreStandingOutOfItsRowIsLeftOutWhileTheAnimationCoversIt() {
        int set = BoardFx.GOAL_REVEAL;
        assertFalse(BoardBug.figuresAway(set, -1));
        assertTrue(BoardBug.figuresAway(set, GoalReveal.SWEEP_IN));
        assertTrue(BoardBug.figuresAway(set, GoalReveal.MORPH_FROM));
        assertTrue(BoardBug.figuresAway(set, GoalReveal.SCORE_AT));
        assertFalse(BoardBug.figuresAway(set, GoalReveal.TOTAL_MS));
        // the boards' own animations never leave it out
        for (int other : new int[]{BoardFx.GOAL_BAND, BoardFx.GOAL_FLIP, BoardFx.GOAL_BURST, BoardFx.GOAL_SEQUENCE}) {
            for (long t = 0; t < 6000; t += 250) assertFalse(BoardBug.figuresAway(other, t));
        }
    }

    @Test
    public void theHoldOkMenuHasTheGoalAnimationAndItsSound() {
        BoardCover.MenuState m = new BoardCover.MenuState();
        String[] on = BoardCover.menuLines(m);
        assertEquals(18, on.length);
        assertEquals(16, BoardCover.M_GOAL);
        assertEquals(17, BoardCover.M_GOAL_SOUND);
        assertTrue(on[BoardCover.M_GOAL].startsWith("Goal animation:") && on[BoardCover.M_GOAL].contains("‹  As today  ›"));
        assertTrue(on[BoardCover.M_GOAL_SOUND].startsWith("Goal sound:") && on[BoardCover.M_GOAL_SOUND].contains("‹  Your swoosh  ›"));
        m.goalLook = GoalLooks.name(GoalLooks.CLUBS);
        m.goalSound = Sfx.goalName("thunder");
        on = BoardCover.menuLines(m);
        assertTrue(on[BoardCover.M_GOAL].contains("‹  Club animations  ›"));
        assertTrue(on[BoardCover.M_GOAL_SOUND].contains("‹  Thunder and sparkle  ›"));
        // the lines before it where they were
        assertTrue(on[BoardCover.M_MINE].startsWith("My scoreboards"));
    }
}
