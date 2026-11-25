package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** The choice of goal animation (GoalLooks) and the new animations' timeline (GoalReveal), as pure sums. */
public class GoalRevealTest {

    // ---- the choice --------------------------------------------------------------------------------------------------

    @Test
    public void theChoiceOffersTodaysAnimationsThenTheNewOnesAndKeepsTodayByDefault() {
        assertEquals(7, GoalLooks.ALL.length);
        assertEquals(GoalLooks.TODAY, GoalLooks.DEFAULT);
        assertEquals(GoalLooks.TODAY, GoalLooks.of(null));
        assertEquals(GoalLooks.TODAY, GoalLooks.of(""));
        assertEquals(GoalLooks.TODAY, GoalLooks.of("fireworks"));
        Set<String> names = new HashSet<>();
        for (String k : GoalLooks.ALL) {
            assertEquals(k, GoalLooks.of(k));
            assertTrue(names.add(GoalLooks.name(k)));
            assertFalse(GoalLooks.note(k).isEmpty());
        }
        assertEquals("As today", GoalLooks.name(GoalLooks.TODAY));
        assertEquals("Club animations", GoalLooks.name(GoalLooks.CLUBS));
        assertEquals("Generic", GoalLooks.name(GoalLooks.GENERIC));
        assertEquals("3D shirt", GoalLooks.name(GoalLooks.SHIRT));
        assertEquals("As today", GoalLooks.name("anything"));
    }

    @Test
    public void theChoiceStepsRoundBothWays() {
        String k = GoalLooks.DEFAULT;
        for (int i = 0; i < GoalLooks.ALL.length; i++) k = GoalLooks.next(k, 1);
        assertEquals(GoalLooks.DEFAULT, k);
        assertEquals(GoalLooks.BAND, GoalLooks.next(GoalLooks.TODAY, 1));
        assertEquals(GoalLooks.GENERIC, GoalLooks.next(GoalLooks.TODAY, -1));
        assertEquals(GoalLooks.TODAY, GoalLooks.next(GoalLooks.GENERIC, 1));
        assertEquals(GoalLooks.CLUBS, GoalLooks.next(GoalLooks.GENERIC, -1));
        assertEquals(GoalLooks.BAND, GoalLooks.next("nonsense", 1));
    }

    @Test
    public void eachChoiceIsTheBoardsSetForIt() {
        int todays = BoardFx.goalSet("any game");
        assertEquals(todays, GoalLooks.set(GoalLooks.TODAY, todays));
        assertEquals(BoardFx.GOAL_SEQUENCE, GoalLooks.set(GoalLooks.TODAY, BoardFx.GOAL_SEQUENCE));
        assertEquals(BoardFx.GOAL_BAND, GoalLooks.set(GoalLooks.BAND, todays));
        assertEquals(BoardFx.GOAL_FLIP, GoalLooks.set(GoalLooks.FLIP, todays));
        assertEquals(BoardFx.GOAL_BURST, GoalLooks.set(GoalLooks.BURST, todays));
        assertEquals(BoardFx.GOAL_BAND, GoalLooks.set(GoalLooks.SHIRT, todays));
        assertEquals(BoardFx.GOAL_REVEAL, GoalLooks.set(GoalLooks.CLUBS, todays));
        assertEquals(BoardFx.GOAL_REVEAL, GoalLooks.set(GoalLooks.GENERIC, todays));
        assertEquals(todays, GoalLooks.set("unknown", todays));
        assertTrue(GoalLooks.reveal(GoalLooks.CLUBS) && GoalLooks.reveal(GoalLooks.GENERIC));
        assertFalse(GoalLooks.reveal(GoalLooks.TODAY) || GoalLooks.reveal(GoalLooks.SHIRT));
        assertTrue(GoalLooks.shirt(GoalLooks.SHIRT));
        assertFalse(GoalLooks.shirt(GoalLooks.TODAY));
        // the new set is none of the old ones, and never a game's own
        for (int s : new int[]{BoardFx.GOAL_BAND, BoardFx.GOAL_FLIP, BoardFx.GOAL_BURST, BoardFx.GOAL_SEQUENCE}) assertNotEquals(s, BoardFx.GOAL_REVEAL);
        assertNotEquals(BoardFx.GOAL_REVEAL, BoardFx.goalSet("game 1"));
    }

    // ---- the timeline ------------------------------------------------------------------------------------------------

    @Test
    public void theFiveStagesComeInOrderInAboutFiveSeconds() {
        assertTrue(GoalReveal.TOTAL_MS >= 4000 && GoalReveal.TOTAL_MS <= 5200);
        assertEquals(GoalReveal.STAGE_SWEEP, GoalReveal.stage(0));
        assertEquals(GoalReveal.STAGE_SWEEP, GoalReveal.stage(GoalReveal.CREST_FROM - 1));
        assertEquals(GoalReveal.STAGE_CREST, GoalReveal.stage(GoalReveal.CREST_FROM));
        assertEquals(GoalReveal.STAGE_CREST, GoalReveal.stage(GoalReveal.MOTIF_FROM - 1));
        assertEquals(GoalReveal.STAGE_MOTIF, GoalReveal.stage(GoalReveal.MOTIF_FROM));
        assertEquals(GoalReveal.STAGE_MOTIF, GoalReveal.stage(GoalReveal.MORPH_FROM - 1));
        assertEquals(GoalReveal.STAGE_GOAL, GoalReveal.stage(GoalReveal.MORPH_FROM));
        assertEquals(GoalReveal.STAGE_GOAL, GoalReveal.stage(GoalReveal.DIM_FROM - 1));
        assertEquals(GoalReveal.STAGE_OUT, GoalReveal.stage(GoalReveal.DIM_FROM));
        assertEquals(GoalReveal.STAGE_OUT, GoalReveal.stage(GoalReveal.TOTAL_MS - 1));
        assertEquals(GoalReveal.STAGE_DONE, GoalReveal.stage(GoalReveal.TOTAL_MS));
        assertEquals(GoalReveal.STAGE_DONE, GoalReveal.stage(-1));
        int last = -1;
        for (long t = 0; t < GoalReveal.TOTAL_MS; t += 10) {
            int s = GoalReveal.stage(t);
            assertTrue(s >= last);
            last = s;
        }
        for (int s = GoalReveal.STAGE_SWEEP; s < GoalReveal.STAGE_DONE; s++) assertTrue(GoalReveal.stageAt(s) < GoalReveal.stageAt(s + 1));
        // each part inside the stage it belongs to, nothing overlapping that should not
        assertTrue(GoalReveal.CREST_EDGE < GoalReveal.CREST_FACED && GoalReveal.CREST_FACED < GoalReveal.DARK_FROM);
        assertTrue(GoalReveal.DARK_FROM + GoalReveal.DARK_MS <= GoalReveal.MOTIF_FROM + 200);
        assertTrue(GoalReveal.MOTIF_FROM + GoalReveal.MOTIF_MS < GoalReveal.MORPH_FROM);
        assertTrue(GoalReveal.LETTERS_FROM + GoalReveal.LETTERS_MS <= GoalReveal.BURST_FROM + 50);
        assertTrue(GoalReveal.BURST_FROM + GoalReveal.BURST_MS <= GoalReveal.DIM_FROM);
        assertTrue(GoalReveal.DIM_FROM + GoalReveal.DIM_MS <= GoalReveal.SWEEP2_FROM);
    }

    @Test
    public void theSweepCoversTheStripThenMovesOffTwice() {
        // in from the scorers' name: covering more and more, all of it by SWEEP_IN
        assertEquals(0f, GoalReveal.bandTo(0), 1e-6);
        float was = 0f;
        for (long t = 10; t <= GoalReveal.SWEEP_IN; t += 10) {
            assertEquals(0f, GoalReveal.bandFrom(t), 1e-6);
            assertTrue(GoalReveal.bandTo(t) >= was);
            was = GoalReveal.bandTo(t);
        }
        assertEquals(1f, GoalReveal.bandTo(GoalReveal.SWEEP_IN), 1e-6);
        // held over it while the crest turns
        assertEquals(1f, GoalReveal.bandTo(GoalReveal.CREST_FACED), 1e-6);
        assertEquals(0f, GoalReveal.bandFrom(GoalReveal.CREST_FACED), 1e-6);
        // off along it: the dark strip behind
        long mid = GoalReveal.DARK_FROM + GoalReveal.DARK_MS / 2;
        assertTrue(GoalReveal.bandFrom(mid) > 0.2f && GoalReveal.bandFrom(mid) < 0.8f);
        assertEquals(0f, GoalReveal.bandTo(GoalReveal.MOTIF_FROM + 150) - GoalReveal.bandFrom(GoalReveal.MOTIF_FROM + 150), 1e-6);
        assertEquals(0f, GoalReveal.bandTo(GoalReveal.BURST_FROM), 1e-6);
        // again over it all as the score changes, then off to show it
        assertEquals(1f, GoalReveal.bandTo(GoalReveal.SCORE_AT), 1e-6);
        assertEquals(0f, GoalReveal.bandFrom(GoalReveal.SCORE_AT), 1e-6);
        long out = GoalReveal.OUT_FROM + GoalReveal.OUT_MS / 2;
        assertTrue(GoalReveal.bandFrom(out) > 0.2f && GoalReveal.bandFrom(out) < 0.8f);
        assertEquals(0f, GoalReveal.bandTo(GoalReveal.TOTAL_MS), 1e-6);
        assertEquals(0f, GoalReveal.bandTo(-5), 1e-6);
    }

    @Test
    public void theScoreChangesOnlyOnceTheSecondSweepCoversIt() {
        assertTrue(GoalReveal.newScore(-1));
        assertFalse(GoalReveal.newScore(0));
        assertFalse(GoalReveal.newScore(GoalReveal.MORPH_FROM));
        assertFalse(GoalReveal.newScore(GoalReveal.SCORE_AT - 1));
        assertTrue(GoalReveal.newScore(GoalReveal.SCORE_AT));
        assertTrue(GoalReveal.newScore(GoalReveal.TOTAL_MS));
        // covered whole at that moment, so the change is never seen
        assertEquals(1f, GoalReveal.bandTo(GoalReveal.SCORE_AT - 1), 0.01f);
        assertEquals(0f, GoalReveal.bandFrom(GoalReveal.SCORE_AT - 1), 1e-6);
        // the dark strip down from the first sweep's covering to the second's
        assertFalse(GoalReveal.dark(GoalReveal.SWEEP_IN - 1));
        assertTrue(GoalReveal.dark(GoalReveal.SWEEP_IN));
        assertTrue(GoalReveal.dark(GoalReveal.SCORE_AT - 1));
        assertFalse(GoalReveal.dark(GoalReveal.SCORE_AT));
        // the family's swish peaks as the new score is uncovered
        long peak = GoalReveal.swishAt() + Sfx.SCORE_PEAK_MS;
        assertTrue(peak > GoalReveal.OUT_FROM && peak < GoalReveal.TOTAL_MS);
    }

    @Test
    public void theCrestTurnsToFaceUsThenSlidesBesideTheLetters() {
        assertEquals(0f, GoalReveal.crestEdge(GoalReveal.CREST_FROM), 1e-6);
        assertEquals(1f, GoalReveal.crestEdge(GoalReveal.CREST_EDGE), 1e-6);
        assertEquals(90f, GoalReveal.crestTurn(GoalReveal.CREST_FROM), 1e-6);
        assertEquals(90f, GoalReveal.crestTurn(GoalReveal.CREST_EDGE), 1e-6);
        assertEquals(0f, GoalReveal.crestTurn(GoalReveal.CREST_FACED), 1e-4);
        // past facing us a little on the way, then back
        float least = 90f;
        for (long t = GoalReveal.CREST_EDGE; t <= GoalReveal.CREST_FACED; t += 5) least = Math.min(least, GoalReveal.crestTurn(t));
        assertTrue(least < -2f && least > -25f);
        assertEquals(0f, GoalReveal.crestSlide(GoalReveal.DARK_FROM), 1e-6);
        assertEquals(1f, GoalReveal.crestSlide(GoalReveal.DARK_FROM + GoalReveal.DARK_MS), 1e-6);
    }

    @Test
    public void theMotifTurnsIntoGoalWhoseLettersPullTogether() {
        assertEquals(0f, GoalReveal.motifIn(GoalReveal.MOTIF_FROM), 1e-6);
        assertEquals(1f, GoalReveal.motifIn(GoalReveal.MOTIF_FROM + GoalReveal.MOTIF_MS), 1e-6);
        assertEquals(0f, GoalReveal.motifOut(GoalReveal.MORPH_FROM), 1e-6);
        assertEquals(1f, GoalReveal.motifOut(GoalReveal.MORPH_FROM + GoalReveal.MORPH_MS), 1e-6);
        // the letters come in while the motif goes
        assertTrue(GoalReveal.LETTERS_FROM > GoalReveal.MORPH_FROM && GoalReveal.LETTERS_FROM < GoalReveal.MORPH_FROM + GoalReveal.MORPH_MS);
        assertEquals(1f, GoalReveal.spread(GoalReveal.letters(GoalReveal.LETTERS_FROM)), 1e-6);
        assertEquals(0f, GoalReveal.spread(GoalReveal.letters(GoalReveal.LETTERS_FROM + GoalReveal.LETTERS_MS)), 1e-5);
        float least = 1f;
        for (float p = 0; p <= 1f; p += 0.01f) least = Math.min(least, GoalReveal.spread(p));
        assertTrue(least < 0f && least > -0.2f);
        assertEquals(1f, GoalReveal.panel(GoalReveal.BURST_FROM), 1e-6);
        assertEquals(-1f, GoalReveal.burst(GoalReveal.BURST_FROM - 1), 1e-6);
        assertEquals(0f, GoalReveal.burst(GoalReveal.BURST_FROM), 1e-6);
        assertEquals(-1f, GoalReveal.burst(GoalReveal.BURST_FROM + GoalReveal.BURST_MS), 1e-6);
        // the letters dim before the second sweep
        assertEquals(1f, GoalReveal.codeAlpha(GoalReveal.DIM_FROM), 1e-6);
        assertEquals(0.35f, GoalReveal.codeAlpha(GoalReveal.SWEEP2_FROM), 1e-6);
    }

    @Test
    public void everyShapeAMotifNamesIsDrawn() {
        for (ClubGoals.Club c : ClubGoals.all()) if (c.kind() == 's') assertTrue(c.motif, GoalShapes.has(c.what()));
        assertTrue(GoalShapes.has("ball"));
        assertFalse(GoalShapes.has("dragon"));
    }
}
