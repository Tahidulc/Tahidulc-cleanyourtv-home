package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.Sfx;

import org.junit.Test;

/** A shootout kick landing under the scoreboard: its timings, and the swoosh's loudest moment on the scored mark's pop. */
public class PenMarkTest {
    @Test public void theOvershootGoesPastAndSettles() {
        assertEquals(0f, PenMark.overshoot(0f), 1e-6);
        assertEquals(1f, PenMark.overshoot(1f), 1e-6);
        float peak = PenMark.overshoot(PenMark.PEAK_AT);
        assertTrue(peak > 1.08f && peak < 1.2f);
        // Its peak is its biggest.
        for (float t = 0f; t <= 1f; t += 0.02f) assertTrue(PenMark.overshoot(t) <= peak + 1e-5f);
    }

    @Test public void theSwooshsLoudestMomentLandsOnThePop() {
        long wait = PenMark.startAfterSound(Sfx.SWOOSH_PEAK_MS);
        assertEquals(Sfx.SWOOSH_PEAK_MS, wait + PenMark.popPeakMs());
        assertTrue(PenMark.popPeakMs() > 0 && PenMark.popPeakMs() < PenMark.POP_MS);
        // A sound that peaks before the pop could: the pop at once.
        assertEquals(0L, PenMark.startAfterSound(50L));
    }

    @Test public void aScoredMarkPopsSweepsTicksAndGlows() {
        assertEquals(0f, PenMark.scale(0), 1e-6);
        assertTrue(PenMark.scale(PenMark.popPeakMs()) > 1.05f);
        assertEquals(1f, PenMark.scale(PenMark.POP_MS), 1e-6);
        assertEquals(1f, PenMark.scale(PenMark.SCORED_MS), 1e-6);
        // The green sweeps round, then the tick comes in, then the glow ring opens and fades out.
        assertEquals(0f, PenMark.sweep(0), 1e-6);
        assertEquals(1f, PenMark.sweep(PenMark.SCORED_MS), 1e-6);
        assertTrue(PenMark.sweep(240) > 0f && PenMark.sweep(240) < 1f);
        assertEquals(0f, PenMark.tick(200), 1e-6);
        assertEquals(1f, PenMark.tick(PenMark.SCORED_MS), 1e-6);
        assertEquals(0f, PenMark.glow(100)[1], 1e-6);
        float[] early = PenMark.glow(420), late = PenMark.glow(680);
        assertTrue(early[1] > late[1]);
        assertTrue(late[0] > early[0]);
        assertTrue(late[0] <= 1.6f);
        assertEquals(0f, PenMark.glow(PenMark.SCORED_MS)[1], 1e-6);
        assertTrue(PenMark.SCORED_MS >= 600 && PenMark.SCORED_MS <= 800);
    }

    @Test public void aMissIsQuieterWithAShakeThatDiesAway() {
        assertTrue(PenMark.missScale(0) < 1f);
        assertEquals(1f, PenMark.missScale(PenMark.MISSED_MS), 1e-6);
        // Never past its own size.
        for (long ms = 0; ms <= PenMark.MISSED_MS; ms += 10) assertTrue(PenMark.missScale(ms) <= 1f + 1e-6f);
        assertEquals(0f, PenMark.cross(0), 1e-6);
        assertEquals(1f, PenMark.cross(PenMark.MISSED_MS), 1e-6);
        assertEquals(0f, PenMark.shake(100), 1e-6);
        assertEquals(0f, PenMark.shake(PenMark.MISSED_MS), 1e-6);
        float most = 0f;
        for (long ms = 150; ms < PenMark.MISSED_MS; ms += 5) most = Math.max(most, Math.abs(PenMark.shake(ms)));
        assertTrue(most > 0.3f && most <= 1f);
        assertTrue(Math.abs(PenMark.shake(400)) < 0.2f);
    }

    @Test public void eachRunsItsCourse() {
        assertFalse(PenMark.done(true, PenMark.SCORED_MS - 1));
        assertTrue(PenMark.done(true, PenMark.SCORED_MS));
        assertTrue(PenMark.done(false, PenMark.MISSED_MS));
        assertTrue(PenMark.MISSED_MS < PenMark.SCORED_MS);
        // Every made-up kick in the test lands before the next one comes.
        assertTrue(PenMark.startAfterSound(Sfx.SWOOSH_PEAK_MS) + PenMark.SCORED_MS < Shootout.TEST_EVERY_MS);
    }
}
