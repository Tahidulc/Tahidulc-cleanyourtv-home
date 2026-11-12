package com.gathin.tvhome;

import com.gathin.broadcast.Sfx;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The family's swish over a goal's new score: started so its loudest moment is the score's pop, and packed with the app. */
public class ScoreSwishTest {
    @Test
    public void loudestOnThePop() {
        long peak = BoardBug.scoreSwishAt() + Sfx.SCORE_PEAK_MS;
        assertEquals(1f, BoardBug.glow(peak), 1e-6f);
        assertEquals(0f, BoardBug.glow(peak - 1), 1e-6f);
        // it starts as the band begins to go, after the white hit
        assertEquals(BoardBug.BAND_OUT, BoardBug.scoreSwishAt());
        assertTrue(BoardBug.scoreSwishAt() > BoardBug.HIT_MS);
    }

    @Test
    public void goalSwooshOnTheBand() {
        // it starts with the goal; the band has swept in by BAND_IN + BAND_MS
        long landed = BoardBug.BAND_IN + BoardBug.BAND_MS;
        assertTrue(Math.abs(Sfx.SWOOSH_PEAK_MS - landed) <= 60);
        assertEquals("swoosh", Sfx.GOAL_DEFAULT);
        assertTrue(new File("../../overlays/assets/broadcast/sfx/goal-" + Sfx.GOAL_DEFAULT + ".ogg").isFile());
    }

    @Test
    public void packed() {
        assertTrue(new File("../../overlays/assets/broadcast/sfx/" + Sfx.SCORE + ".ogg").isFile());
        assertTrue(new File("../../overlays/src/sfx/" + Sfx.SCORE + ".ogg").isFile());
    }
}
