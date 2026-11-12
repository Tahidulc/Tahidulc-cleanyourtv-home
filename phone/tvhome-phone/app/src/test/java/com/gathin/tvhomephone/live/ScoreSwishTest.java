package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.Sfx;

import org.junit.Test;

import java.io.File;

/** The family's swish over a goal's new score: started so its loudest moment is the score's pop, and packed with the app. */
public class ScoreSwishTest {
    @Test
    public void loudestOnThePop() {
        long peak = OverlayBug.scoreSwishAt() + Sfx.SCORE_PEAK_MS;
        assertEquals(1f, OverlayBug.glow(peak), 1e-6f);
        assertEquals(0f, OverlayBug.glow(peak - 1), 1e-6f);
        assertEquals(OverlayBug.BAND_OUT, OverlayBug.scoreSwishAt());
        assertTrue(OverlayBug.scoreSwishAt() > OverlayBug.HIT_MS);
    }

    @Test
    public void goalSwooshOnTheBand() {
        long landed = OverlayBug.BAND_IN + OverlayBug.BAND_MS;
        assertTrue(Math.abs(Sfx.SWOOSH_PEAK_MS - landed) <= 60);
        assertTrue(new File("../../../overlays/assets/broadcast/sfx/goal-" + Sfx.GOAL_DEFAULT + ".ogg").isFile());
    }

    @Test
    public void packed() {
        assertTrue(new File("../../../overlays/assets/broadcast/sfx/" + Sfx.SCORE + ".ogg").isFile());
    }
}
