package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The Lab's animations' timings and choices: a game keeps its own, the weather and the wave come when they should. */
public class BoardFxTest {
    @Test
    public void aGameKeepsItsChoiceAndGamesSpreadOverThem() {
        assertEquals(BoardFx.pick("401861175", "goal", 3), BoardFx.pick("401861175", "goal", 3));
        assertEquals(0, BoardFx.pick("any", "goal", 1));
        int[] seen = new int[3];
        int ht = 0, pens = 0;
        for (int i = 0; i < 3000; i++) {
            String id = String.valueOf(400000000 + i * 7);
            seen[BoardFx.pick(id, "goal", 3)]++;
            // Every game's goals: the sequence, the same order every goal (the family's choice).
            assertEquals(BoardFx.GOAL_SEQUENCE, BoardFx.goalSet(id));
            if (BoardFx.htFace(id)) ht++;
            if (BoardFx.pensInClock(id)) pens++;
        }
        assertEquals(BoardFx.GOAL_SEQUENCE, BoardFx.goalSet(null));
        assertEquals(BoardFx.GOAL_SEQUENCE, BoardFx.goalSet(""));
        // A three-way pick for about a third of games each, each two-way choice about half.
        for (int n : seen) assertTrue("spread " + Arrays.toString(seen), n > 800 && n < 1200);
        assertTrue(ht > 1300 && ht < 1700);
        assertTrue(pens > 1300 && pens < 1700);
        // A game's choice for one moment says nothing of another's (not every game flips and turns over alike).
        int same = 0;
        for (int i = 0; i < 1000; i++) {
            String id = "game" + i;
            if (BoardFx.htFace(id) == BoardFx.pensInClock(id)) same++;
        }
        assertTrue(same > 350 && same < 650);
        assertEquals(BoardFx.GOAL_BAND, 0);
        assertEquals(BoardFx.GOAL_FLIP, 1);
        assertEquals(BoardFx.GOAL_BURST, 2);
        assertEquals(BoardFx.GOAL_SEQUENCE, 3);
    }

    @Test
    public void theSequenceRunsBandThenBurstThenNet() {
        // The burst begins as the GOAL band has gone (3.3 s held, half a second going), never under it.
        assertEquals(3800L, BoardFx.SEQ_BURST_AT);
        assertEquals(-1L, BoardFx.seqBurst(BoardFx.SEQ_BURST_AT - 1));
        assertEquals(0L, BoardFx.seqBurst(BoardFx.SEQ_BURST_AT));
        // The score keeps its old figure until the burst rolls it up: whole before, landed by ROLL_TO into it.
        assertEquals(0f, BoardFx.rollMove(BoardFx.seqBurst(1500)), 0f);
        assertEquals(0f, BoardFx.rollMove(BoardFx.seqBurst(BoardFx.SEQ_BURST_AT + BoardFx.ROLL_FROM)), 0f);
        assertEquals(1f, BoardFx.rollMove(BoardFx.seqBurst(BoardFx.SEQ_BURST_AT + BoardFx.ROLL_TO)), 0f);
        // Then the net, once the score has landed, and its end the sequence's.
        assertEquals(BoardFx.SEQ_BURST_AT + BoardFx.MESH_AFTER_BURST, BoardFx.seqMeshAt());
        assertTrue(BoardFx.seqMeshAt() >= BoardFx.SEQ_BURST_AT + BoardFx.ROLL_TO);
        assertEquals(BoardFx.seqMeshAt() + BoardFx.MESH_MS, BoardFx.seqMs());
        assertTrue(BoardFx.seqMs() >= BoardFx.SEQ_BURST_AT + BoardFx.BURST_MS);
        // The family's swish loudest as the new score lands.
        assertEquals(BoardFx.SEQ_BURST_AT + BoardFx.ROLL_TO, BoardFx.seqSwishAt() + Sfx.SCORE_PEAK_MS);
        assertTrue(BoardFx.seqSwishAt() > BoardFx.SEQ_BURST_AT);
    }

    @Test
    public void theFlipAndTheBurstRunInTheirOrder() {
        assertEquals(0f, BoardFx.flipMove(0), 0f);
        assertEquals(0f, BoardFx.flipMove(BoardFx.FLIP_FROM), 0f);
        assertEquals(0.5f, BoardFx.flipMove((BoardFx.FLIP_FROM + BoardFx.FLIP_TO) / 2), 0.01f);
        assertEquals(1f, BoardFx.flipMove(BoardFx.FLIP_TO), 0f);
        assertEquals(1f, BoardFx.flipMove(BoardFx.FLIP_MS), 0f);
        assertEquals(0f, BoardFx.rollMove(BoardFx.ROLL_FROM - 1), 0f);
        assertEquals(1f, BoardFx.rollMove(BoardFx.ROLL_TO), 0f);
        // The wash: up to three quarters a fifth of the way, gone by its end.
        assertEquals(0.75f, BoardFx.washAlpha(BoardFx.WASH_MS / 5), 0.01f);
        assertEquals(0f, BoardFx.washAlpha(BoardFx.WASH_MS), 0f);
        assertEquals(0f, BoardFx.washAlpha(0), 0f);
        // The glow comes after the crest has popped, and is gone by the burst's end.
        assertEquals(0f, BoardFx.glowAlpha(BoardFx.GLOW_FROM), 0f);
        assertTrue(BoardFx.glowAlpha(BoardFx.GLOW_FROM + 300) > 0.5f);
        assertEquals(0f, BoardFx.glowAlpha(BoardFx.BURST_MS), 0f);
        // The shine crosses from off the left to off the right.
        assertEquals(-1.2f, BoardFx.shineAt(0), 0.001f);
        assertTrue(BoardFx.shineAt(BoardFx.SHINE_MS - 1) > 1.1f);
        assertTrue(Float.isNaN(BoardFx.shineAt(BoardFx.SHINE_MS)));
        // Two rings, the second after the first, each running out and fading.
        float[] r0 = BoardFx.ringAt(100, 0);
        assertNotNull(r0);
        assertNull(BoardFx.ringAt(100, 1));
        assertNotNull(BoardFx.ringAt(BoardFx.RING_GAP + 1, 1));
        float[] late = BoardFx.ringAt(BoardFx.RING_MS - 10, 0);
        assertTrue(late[0] > r0[0] && late[1] < r0[1]);
        assertNull(BoardFx.ringAt(BoardFx.RINGS_MS, 1));
        // The crest pops up to 1.35 and settles.
        assertEquals(1.35f, BoardFx.popAt(BoardFx.POP_MS / 4), 0.01f);
        assertEquals(1f, BoardFx.popAt(BoardFx.POP_MS), 0f);
        assertTrue(BoardFx.FLIP_TO < BoardFx.FLIP_MS && BoardFx.ROLL_TO < BoardFx.BURST_MS);
    }

    @Test
    public void everyGoalsNetShiversAndSettles() {
        assertEquals(0f, BoardFx.meshAlpha(0), 0f);
        assertEquals(0.9f, BoardFx.meshAlpha(BoardFx.MESH_PEAK), 0.01f);
        assertEquals(0.3f, BoardFx.meshAlpha(BoardFx.MESH_SETTLE), 0.01f);
        assertEquals(0f, BoardFx.meshAlpha(BoardFx.MESH_MS), 0f);
        // It swells about the middle to 1.05 across and 1.3 up at its height, and is back to its size as it settles.
        assertArrayEquals(new float[]{1.05f, 1.3f}, BoardFx.meshSwell(BoardFx.MESH_PEAK), 0.01f);
        assertArrayEquals(new float[]{1f, 1f}, BoardFx.meshSwell(BoardFx.MESH_SETTLE), 0.001f);
        assertArrayEquals(new float[]{1f, 1f}, BoardFx.meshSwell(0), 0.001f);
        // Each set's net comes once its own part is done.
        assertTrue(BoardFx.MESH_AFTER_FLIP + BoardFx.MESH_MS <= BoardFx.FLIP_MS + BoardFx.MESH_MS);
        assertTrue(BoardFx.MESH_AFTER_FLIP >= BoardFx.FLIP_TO && BoardFx.MESH_AFTER_BURST >= BoardFx.ROLL_TO);
    }

    @Test
    public void theRedCardSwingsUpHoldsAndTucksAway() {
        assertArrayEquals(new float[]{2f, -30f}, BoardFx.redCardPose(0), 0.001f);
        float[] up = BoardFx.redCardPose(BoardFx.RED_UP + 100);
        assertArrayEquals(new float[]{0f, 8f}, up, 0.001f);
        float[] going = BoardFx.redCardPose(BoardFx.RED_AWAY + 200);
        assertTrue(going[0] > 0f && going[1] < 8f);
        assertArrayEquals(new float[]{2f, -30f}, BoardFx.redCardPose(BoardFx.RED_MS), 0.001f);
    }

    @Test
    public void varsDotPulsesEachSecond() {
        assertArrayEquals(new float[]{1f, 1f}, BoardFx.varPulse(0), 0.001f);
        assertArrayEquals(new float[]{0.55f, 0.4f}, BoardFx.varPulse(500), 0.001f);
        assertArrayEquals(BoardFx.varPulse(250), BoardFx.varPulse(1250), 0.001f);
    }

    @Test
    public void theSubstitutionsPlayersCrossOverInTurn() {
        // At the start the player on is dim and a little to the left, the player off waiting, still, at 0.4.
        float[] s = BoardFx.swapAt(0);
        assertEquals(0.35f, s[BoardFx.SW_IN_ALPHA], 0.001f);
        assertEquals(-0.33f, s[BoardFx.SW_IN_SHIFT], 0.001f);
        assertEquals(0.4f, s[BoardFx.SW_OUT_ALPHA], 0.001f);
        assertEquals(0f, s[BoardFx.SW_OUT_SHIFT], 0.001f);
        // The player on bright and in place from 30% to 60% of the round, his arrow popped on the way.
        float[] on = BoardFx.swapAt(Math.round(BoardFx.SWAP_MS * 0.45));
        assertEquals(1f, on[BoardFx.SW_IN_ALPHA], 0.001f);
        assertEquals(0f, on[BoardFx.SW_IN_SHIFT], 0.001f);
        assertTrue(BoardFx.swapAt(Math.round(BoardFx.SWAP_MS * 0.15))[BoardFx.SW_IN_POP] > 1.2f);
        assertEquals(1f, on[BoardFx.SW_IN_POP], 0.001f);
        // The player off's turn comes SWAP_LAG later, the same way.
        float[] off = BoardFx.swapAt(BoardFx.SWAP_LAG + Math.round(BoardFx.SWAP_MS * 0.45));
        assertEquals(1f, off[BoardFx.SW_OUT_ALPHA], 0.001f);
        assertEquals(0f, off[BoardFx.SW_OUT_SHIFT], 0.001f);
        assertTrue(off[BoardFx.SW_IN_ALPHA] < 1f);
        // Round and round while the card is out.
        assertArrayEquals(BoardFx.swapAt(5000), BoardFx.swapAt(5000 + BoardFx.SWAP_MS), 0.001f);
        for (long t = 0; t < 8000; t += 37) {
            float[] v = BoardFx.swapAt(t);
            assertEquals(6, v.length);
            for (int i : new int[]{BoardFx.SW_IN_ALPHA, BoardFx.SW_OUT_ALPHA}) assertTrue(v[i] >= 0.35f - 0.001f && v[i] <= 1.001f);
            for (int i : new int[]{BoardFx.SW_IN_SHIFT, BoardFx.SW_OUT_SHIFT}) assertTrue(v[i] >= -0.34f && v[i] <= 0.001f);
        }
        assertEquals(0xFF3DDC84, BoardFx.ON_GREEN);
        assertEquals(0xFFFF6B6B, BoardFx.OFF_RED);
    }

    @Test
    public void theMomentumWaveComesOnceAHalf() {
        Set<Integer> firsts = new HashSet<>(), seconds = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            String id = "40186" + i;
            int[] m = BoardFx.waveMinutes(id);
            assertArrayEquals(m, BoardFx.waveMinutes(id));
            // One in each half, never two: the first in 20 to 28, the second in 58 to 66.
            assertEquals(2, m.length);
            assertTrue("minute " + m[0], m[0] >= 20 && m[0] <= 28);
            assertTrue("minute " + m[1], m[1] >= 58 && m[1] <= 66);
            firsts.add(m[0]);
            seconds.add(m[1]);
        }
        // Spread over each window's minutes, game by game.
        assertEquals(9, firsts.size());
        assertEquals(9, seconds.size());
        // Due at its minute (or up to three after, a board up late), once a game.
        String id = "401861175";
        int[] m = BoardFx.waveMinutes(id);
        Set<String> shown = new HashSet<>();
        assertNull(BoardFx.waveDue(id, m[0] - 1, shown));
        String slot = BoardFx.waveDue(id, m[0], shown);
        assertEquals("wave" + m[0], slot);
        assertNull(BoardFx.waveDue(id, m[0] + 1, shown));
        assertNull(BoardFx.waveDue(id, -1, shown));
        assertEquals("wave" + m[1], BoardFx.waveDue(id, m[1] + 3, shown));
        assertTrue(BoardFx.waveDrawn(0) == 0f && BoardFx.waveDrawn(BoardFx.WAVE_IN) == 1f);
        assertEquals(1f, BoardFx.waveAlpha(BoardFx.WAVE_MS / 2), 0f);
        assertEquals(0f, BoardFx.waveAlpha(BoardFx.WAVE_MS), 0f);
    }

    @Test
    public void theWaveSaysWhoIsOnTop() {
        List<int[]> all = new ArrayList<>();
        for (int m = 1; m <= 40; m++) all.add(new int[]{m, m <= 25 ? -40 : 60});
        List<int[]> last = BoardFx.lastMinutes(all, 40, 15);
        assertEquals(16, last.size());
        assertEquals(25, last.get(0)[0]);
        assertEquals(1, BoardFx.onTop(BoardFx.lastMinutes(all, 40, 10)));
        assertEquals(2, BoardFx.onTop(BoardFx.lastMinutes(all, 20, 10)));
        assertEquals(0, BoardFx.onTop(new ArrayList<>()));
        assertEquals(0, BoardFx.lastMinutes(null, 40, 15).size());
    }

    @Test
    public void theWeatherTouchFollowsTheGroundsSky() {
        // FotMob's weather.com icons.
        assertEquals(BoardFx.Sky.STORM, BoardFx.sky(4, 2, 0, 10, 90));
        assertEquals(BoardFx.Sky.RAIN, BoardFx.sky(12, 1.5, 0, 10, 90));
        assertEquals(BoardFx.Sky.DRIZZLE, BoardFx.sky(9, 0.2, 0, 10, 90));
        assertEquals(BoardFx.Sky.SNOW, BoardFx.sky(16, 0, 1, 10, 90));
        assertEquals(BoardFx.Sky.FOG, BoardFx.sky(20, 0, 0, 5, 100));
        assertEquals(BoardFx.Sky.WIND, BoardFx.sky(24, 0, 0, 50, 30));
        assertEquals(BoardFx.Sky.CLOUDY, BoardFx.sky(29, 0, 0, 4, 53));
        assertEquals(BoardFx.Sky.CLOUDY, BoardFx.sky(26, 0, 0, 4, 95));
        assertEquals(BoardFx.Sky.CLEAR_NIGHT, BoardFx.sky(31, 0, 0, 4, 0));
        assertEquals(BoardFx.Sky.CLEAR_DAY, BoardFx.sky(32, 0, 0, 4, 0));
        // What is falling counts over the icon: snow, then rain; a strong dry wind over a quiet sky.
        assertEquals(BoardFx.Sky.SNOW, BoardFx.sky(26, 0.5, 0.4, 10, 90));
        assertEquals(BoardFx.Sky.DRIZZLE, BoardFx.sky(28, 0.3, 0, 10, 90));
        assertEquals(BoardFx.Sky.RAIN, BoardFx.sky(30, 2, 0, 10, 60));
        assertEquals(BoardFx.Sky.WIND, BoardFx.sky(32, 0, 0, 45, 0));
        assertEquals(BoardFx.Sky.CLOUDY, BoardFx.sky(-1, 0, 0, 5, 80));
        assertEquals(BoardFx.Sky.CLEAR_DAY, BoardFx.sky(-1, 0, 0, 5, 10));
        // Open-Meteo's WMO codes for the TV's fallback.
        assertEquals(BoardFx.Sky.CLEAR_DAY, BoardFx.skyOfWmo(0, true, 5));
        assertEquals(BoardFx.Sky.CLEAR_NIGHT, BoardFx.skyOfWmo(1, false, 5));
        assertEquals(BoardFx.Sky.CLOUDY, BoardFx.skyOfWmo(3, true, 5));
        assertEquals(BoardFx.Sky.FOG, BoardFx.skyOfWmo(45, true, 5));
        assertEquals(BoardFx.Sky.DRIZZLE, BoardFx.skyOfWmo(53, true, 5));
        assertEquals(BoardFx.Sky.RAIN, BoardFx.skyOfWmo(63, true, 5));
        assertEquals(BoardFx.Sky.RAIN, BoardFx.skyOfWmo(81, true, 5));
        assertEquals(BoardFx.Sky.SNOW, BoardFx.skyOfWmo(73, true, 5));
        assertEquals(BoardFx.Sky.SNOW, BoardFx.skyOfWmo(85, true, 5));
        assertEquals(BoardFx.Sky.STORM, BoardFx.skyOfWmo(95, true, 5));
        assertEquals(BoardFx.Sky.WIND, BoardFx.skyOfWmo(2, true, 30));
    }

    @Test
    public void theWeatherTouchComesTwoOrThreeTimesAGame() {
        int threes = 0;
        for (int i = 0; i < 400; i++) {
            String id = "game-" + i;
            int[] m = BoardFx.weatherMinutes(id);
            assertArrayEquals(m, BoardFx.weatherMinutes(id));
            assertTrue(m.length == 2 || m.length == 3);
            assertTrue(m[0] >= 8 && m[0] <= 20);
            assertTrue(m[1] >= 33 && m[1] <= 42);
            if (m.length == 3) {
                threes++;
                assertTrue(m[2] >= 56 && m[2] <= 80);
            }
        }
        assertTrue(threes > 120 && threes < 280);
        String id = "game-7";
        int[] m = BoardFx.weatherMinutes(id);
        Set<String> shown = new HashSet<>();
        assertFalse(BoardFx.weatherDue(id, m[0] - 1, shown));
        assertTrue(BoardFx.weatherDue(id, m[0] + 2, shown));
        assertFalse(BoardFx.weatherDue(id, m[0] + 3, shown));
        assertFalse(BoardFx.weatherDue(id, m[0] + 4, shown));
        assertTrue(BoardFx.weatherDue(id, m[1], shown));
        // Subtle: in and out softly, never at once.
        assertEquals(0f, BoardFx.weatherAlpha(0), 0f);
        assertTrue(BoardFx.weatherAlpha(BoardFx.WEATHER_FADE / 4) < 0.5f);
        assertEquals(1f, BoardFx.weatherAlpha(BoardFx.WEATHER_MS / 2), 0f);
        assertEquals(0f, BoardFx.weatherAlpha(BoardFx.WEATHER_MS), 0f);
        assertEquals(9, BoardFx.Sky.values().length);
    }

    @Test
    public void aShootoutsDotsMakeRoomForSuddenDeath() {
        assertEquals(5, BoardFx.pensCols(0, 0));
        assertEquals(5, BoardFx.pensCols(5, 4));
        assertEquals(7, BoardFx.pensCols(7, 6));
    }

    @Test
    public void theBurstsCrestSettlesBackAndAMissingCrestOnlyFlashes() {
        assertEquals(1f, BoardFx.crestAlpha(0), 1e-6f);
        assertEquals(1f, BoardFx.crestAlpha(BoardFx.POP_MS), 1e-6f);
        assertEquals(0.5f, BoardFx.crestAlpha(BoardFx.POP_MS + BoardFx.SETTLE_MS / 2), 1e-3f);
        assertEquals(0f, BoardFx.crestAlpha(BoardFx.POP_MS + BoardFx.SETTLE_MS), 1e-6f);
        assertEquals(0f, BoardFx.crestAlpha(BoardFx.BURST_MS - 1), 1e-6f);
        assertEquals(1f, BoardFx.discAlpha(0), 1e-6f);
        assertEquals(0f, BoardFx.discAlpha(BoardFx.POP_MS), 1e-6f);
        assertEquals(0f, BoardFx.discAlpha(-1), 1e-6f);
    }
}
