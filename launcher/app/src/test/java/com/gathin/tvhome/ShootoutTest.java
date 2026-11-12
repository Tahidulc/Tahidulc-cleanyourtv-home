package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.json.JSONObject;
import org.junit.Test;

public class ShootoutTest {
    /** Manchester City 4–3 Brighton on penalties, as ESPN's summary has it (City: in, in, in, missed, in). */
    private static JSONObject sample(String state, String name) throws Exception {
        return new JSONObject("{\"header\":{\"competitions\":[{\"status\":{\"type\":{\"name\":\"" + name + "\",\"state\":\"" + state + "\"}}}]},"
                + "\"shootout\":["
                + "{\"id\":\"382\",\"team\":\"Manchester City\",\"shots\":["
                + "{\"id\":\"100\",\"player\":\"Phil Foden\",\"shotNumber\":1,\"didScore\":true},"
                + "{\"id\":\"102\",\"player\":\"B\",\"shotNumber\":2,\"didScore\":true},"
                + "{\"id\":\"104\",\"player\":\"C\",\"shotNumber\":3,\"didScore\":true},"
                + "{\"id\":\"106\",\"player\":\"D\",\"shotNumber\":4,\"didScore\":false},"
                + "{\"id\":\"108\",\"player\":\"E\",\"shotNumber\":5,\"didScore\":true}]},"
                + "{\"id\":\"331\",\"team\":\"Brighton & Hove Albion\",\"shots\":["
                + "{\"id\":\"101\",\"player\":\"F\",\"shotNumber\":1,\"didScore\":true},"
                + "{\"id\":\"103\",\"player\":\"G\",\"shotNumber\":2,\"didScore\":false},"
                + "{\"id\":\"105\",\"player\":\"H\",\"shotNumber\":3,\"didScore\":true},"
                + "{\"id\":\"107\",\"player\":\"I\",\"shotNumber\":4,\"didScore\":true}]}]}");
    }

    @Test
    public void kicksComeInTheOrderTheyWereTaken() throws Exception {
        List<Shootout.Kick> k = Shootout.kicks(sample("post", "STATUS_FINAL_PEN"), "382", "331", "Manchester City");
        assertEquals(9, k.size());
        for (int i = 0; i < 9; i++) assertEquals("kick " + i, i % 2 == 0, k.get(i).home);
        assertEquals("Phil Foden", k.get(0).taker);
        assertEquals(Arrays.asList(true, true, true, false, true), Shootout.side(k, 9, true));
        assertEquals(Arrays.asList(true, false, true, true), Shootout.side(k, 9, false));
        assertEquals(4, Shootout.tally(Shootout.side(k, 9, true)));
        assertEquals(3, Shootout.tally(Shootout.side(k, 9, false)));
        // The first five shown: three of City's, two of Brighton's.
        assertEquals(3, Shootout.side(k, 5, true).size());
        assertEquals(2, Shootout.side(k, 5, false).size());
    }

    @Test
    public void sidesAreFoundByNameWhenTheIdsAreNotThere() throws Exception {
        List<Shootout.Kick> k = Shootout.kicks(sample("post", "STATUS_FINAL_PEN"), null, null, "manchester city");
        assertEquals(5, k.size());
        for (Shootout.Kick x : k) assertTrue(x.home);
    }

    @Test
    public void onWhileItIsTakenOverAfter() throws Exception {
        assertTrue(Shootout.on(sample("in", "STATUS_SHOOTOUT")));
        assertFalse(Shootout.over(sample("in", "STATUS_SHOOTOUT")));
        assertFalse(Shootout.on(sample("post", "STATUS_FINAL_PEN")));
        assertTrue(Shootout.over(sample("post", "STATUS_FINAL_PEN")));
        assertFalse(Shootout.on(new JSONObject("{\"header\":{\"competitions\":[{\"status\":{\"type\":{\"name\":\"STATUS_SECOND_HALF\",\"state\":\"in\"}}}]}}")));
        assertTrue(Shootout.kicks(new JSONObject("{}"), "1", "2", "x").isEmpty());
    }

    @Test
    public void decidedWhenOneSideCannotBeCaught() throws Exception {
        // 3–0 after three each: the other side's two left cannot catch up.
        assertTrue(Shootout.decided(Arrays.asList(true, true, true), Arrays.asList(false, false, false)));
        // 2–1 after three each: still on.
        assertFalse(Shootout.decided(Arrays.asList(true, true, false), Arrays.asList(true, false, false)));
        // City 4–3 after City's fifth and Brighton's fourth: Brighton's last could only make it 4–4. Not yet.
        assertFalse(Shootout.decided(Arrays.asList(true, true, true, false, true), Arrays.asList(true, false, true, true)));
        // Level after five each: sudden death.
        assertFalse(Shootout.decided(Arrays.asList(true, true, true, true, true), Arrays.asList(true, true, true, true, true)));
        // Sudden death: a round each, one in front.
        assertTrue(Shootout.decided(Arrays.asList(true, true, true, true, true, true), Arrays.asList(true, true, true, true, true, false)));
        assertFalse(Shootout.decided(Arrays.asList(true, true, true, true, true, true), Arrays.asList(true, true, true, true, true)));
    }

    @Test
    public void fiveDotsEachThenOneMoreARound() throws Exception {
        assertEquals(5, Shootout.slots(0, 0));
        assertEquals(5, Shootout.slots(5, 4));
        assertEquals(6, Shootout.slots(6, 5));
        // Level after five each and still on: the next round's empty dots.
        assertEquals(6, Shootout.slots(5, 5, false));
        assertEquals(5, Shootout.slots(5, 5, true));
        assertEquals(7, Shootout.slots(6, 6, false));
        assertEquals(5, Shootout.slots(3, 3, false));
    }

    @Test
    public void kicksShowAsTheScreenShowsThem() throws Exception {
        long hold = 30_000L, gap = 3_000L;
        // Two read at the start together: both at once, hold after.
        long[] start = {1_000L, 1_000L};
        assertEquals(0, Shootout.revealed(start, hold, gap, 30_999L));
        assertEquals(2, Shootout.revealed(start, hold, gap, 31_000L));
        // Then two read together later: one, and the other at least three seconds after it.
        long[] later = {1_000L, 1_000L, 50_000L, 50_000L};
        assertEquals(2, Shootout.revealed(later, hold, gap, 79_999L));
        assertEquals(3, Shootout.revealed(later, hold, gap, 80_000L));
        assertEquals(3, Shootout.revealed(later, hold, gap, 82_999L));
        assertEquals(4, Shootout.revealed(later, hold, gap, 83_000L));
        assertTrue(Shootout.likely("Pens"));
        assertTrue(Shootout.likely("FT-Pens"));
        assertFalse(Shootout.likely("67'"));
    }

    @Test
    public void aKickScoredLandsOnTheSwooshsLoudestMoment() throws Exception {
        // The swoosh starts first; the dot's landing begins so that its pop is on the swoosh's peak.
        assertEquals(com.gathin.broadcast.Sfx.SWOOSH_PEAK_MS, BoardUnder.swooshLead() + BoardUnder.SCORED_POP_MS);
        assertTrue(BoardUnder.SCORED_MS >= 600 && BoardUnder.SCORED_MS <= 800);
        float pa = BoardUnder.popAt();
        assertEquals(BoardUnder.SCORED_POP_MS / (float) BoardUnder.SCORED_MS, pa, 1e-6);
        // In from small, past its size at the pop, settled by the end.
        assertTrue(BoardUnder.scoredScale(0f) < 0.5f);
        assertEquals(1.25f, BoardUnder.scoredScale(pa), 1e-3);
        assertTrue(BoardUnder.scoredScale(pa + 0.1f) > 1f);
        assertEquals(1f, BoardUnder.scoredScale(1f), 1e-6);
        for (float t = 0f; t + 0.05f <= pa; t += 0.05f) assertTrue(BoardUnder.scoredScale(t) <= BoardUnder.scoredScale(t + 0.05f) + 1e-6);
        // The green all the way round by the pop; the ring only after it, gone by the end.
        assertEquals(0f, BoardUnder.sweepOf(0f), 1e-6);
        assertEquals(1f, BoardUnder.sweepOf(pa), 1e-6);
        assertEquals(0f, BoardUnder.glowOf(pa), 1e-6);
        assertEquals(1f, BoardUnder.glowOf(1f), 1e-6);
        assertEquals(0.5f, BoardUnder.drawnOf(0.5f, 0.25f, 0.75f), 1e-6);
        assertEquals(0f, BoardUnder.drawnOf(0.1f, 0.25f, 0.75f), 1e-6);
        assertEquals(1f, BoardUnder.drawnOf(0.9f, 0.25f, 0.75f), 1e-6);
    }

    @Test
    public void aKickMissedShakesALittleThenStill() throws Exception {
        assertEquals(0f, BoardUnder.shakeOf(0f), 1e-6);
        assertEquals(0f, BoardUnder.shakeOf(0.3f), 1e-6);
        assertEquals(0f, BoardUnder.shakeOf(1f), 1e-6);
        boolean moved = false;
        for (float t = 0.36f; t < 1f; t += 0.02f) {
            assertTrue(Math.abs(BoardUnder.shakeOf(t)) <= 1f);
            moved |= Math.abs(BoardUnder.shakeOf(t)) > 0.3f;
        }
        assertTrue(moved);
        assertTrue(BoardUnder.MISSED_MS < BoardUnder.SCORED_MS);
    }
}
