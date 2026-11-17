package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import androidx.media3.common.Format;

import com.gathin.homefieldtv.engine.Exec;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/**
 * The speed work's own parts: the glide every list scrolls with (a single step eases out in about its time and stops
 * dead on its place; a key held at 20 repeats a second glides on with no step back, no restart and no jump), and the
 * frames a second the player's chip shows (the stream's own, else counted from its frames: only the usual rates, only
 * once two runs agree, never a guess).
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34)
public class SmoothTest {
    @Before public void before() {
        Exec.inline = false;
    }

    @After public void after() {
        Exec.inline = false;
    }

    @Test public void aStepEasesOutAndStopsOnItsPlace() {
        Glide g = new Glide(GuideGrid.ROW_MS);
        g.jump(0f);
        g.to(88f);
        long t = 1000;
        float last = 0f, firstMove = -1f;
        int frames = 0;
        boolean moving = true;
        while (moving && frames < 200) {
            t += 16;
            moving = g.step(t);
            float v = g.value();
            assertTrue("no overshoot: " + v, v <= 88f + 1e-3f);
            assertTrue("never back: " + v, v >= last - 1e-3f);
            if (firstMove < 0f) firstMove = v;
            last = v;
            frames++;
        }
        assertEquals(88f, g.value(), 0f);
        // Its quickest at the start (an ease-out, as the family's TV moves), and done in about the time given.
        assertTrue("first frame " + firstMove, firstMove > 88f * 0.15f);
        assertTrue("frames " + frames, frames * 16 <= GuideGrid.ROW_MS * 1.6f);
    }

    @Test public void aHeldKeyGlidesOnWithNoJumpOrStepBack() {
        Glide g = new Glide(GuideGrid.ROW_MS);
        g.jump(0f);
        long t = 1000;
        float last = 0f, lastStep = -1f, biggestChange = 0f;
        for (int repeat = 0; repeat < 40; repeat++) {
            g.to(g.target() + 88f);
            for (int f = 0; f < 3; f++) {
                t += 16;
                g.step(t);
                float step = g.value() - last;
                assertTrue("never back", step >= -1e-3f);
                if (repeat > 6 && lastStep >= 0f) biggestChange = Math.max(biggestChange, Math.abs(step - lastStep));
                lastStep = step;
                last = g.value();
            }
        }
        // Held, it moves at the key's own speed (88 px every 50 ms is 28 a frame), its frame-to-frame steps within a few
        // pixels of each other: no stop and start at each repeat.
        assertTrue("uneven steps " + biggestChange, biggestChange < 8f);
        // Let go: it settles on the last row, from behind it.
        float target = g.target();
        while (g.step(t += 16)) assertTrue(g.value() <= target + 1e-3f);
        assertEquals(target, g.value(), 0f);
    }

    @Test public void sentBackHalfWayItTurnsWithoutAJump() {
        Glide g = new Glide(GuideGrid.PAGE_MS);
        g.jump(0f);
        g.to(320f);
        long t = 1000;
        for (int f = 0; f < 5; f++) g.step(t += 16);
        float before = g.value();
        g.to(0f);
        g.step(t += 16);
        // The frame after it is sent back moves it a little (it keeps its speed and turns), never back to where it began.
        assertTrue(Math.abs(g.value() - before) < 60f);
        while (g.step(t += 16)) assertTrue(g.value() > -1f);
        assertEquals(0f, g.value(), 0f);
    }

    @Test public void framesASecondAreOnlyTheUsualOnes() {
        assertEquals(23.976f, FpsMeter.usual(23.98f), 0.001f);
        assertEquals(25f, FpsMeter.usual(25.2f), 0f);
        assertEquals(29.97f, FpsMeter.usual(29.97f), 0f);
        assertEquals(50f, FpsMeter.usual(49.6f), 0f);
        assertEquals(59.94f, FpsMeter.usual(59.9f), 0.001f);
        assertEquals(0f, FpsMeter.usual(33f), 0f);
        assertEquals(0f, FpsMeter.usual(Format.NO_VALUE), 0f);
        assertEquals(24, FpsMeter.chip(23.976f));
        assertEquals(30, FpsMeter.chip(29.97f));
        assertEquals(60, FpsMeter.chip(59.94f));
        assertEquals(25, FpsMeter.chip(25f));
        assertEquals(0, FpsMeter.chip(17f));
        assertEquals("50 FPS", FrameRates.chip(50));
        assertEquals("", FrameRates.chip(0));
    }

    @Test public void aRunsMiddleGapGivesItsRate() {
        long[] times = new long[FpsMeter.WINDOW], gaps = new long[FpsMeter.WINDOW - 1];
        // 25 a second with a little jitter, and two frames dropped (a gap twice as long).
        for (int i = 0; i < times.length; i++) times[i] = i * 40_000L + (i % 3 == 0 ? 1_500L : -900L) + (i > 30 ? 40_000L : 0) + (i > 50 ? 40_000L : 0);
        assertEquals(25f, FpsMeter.rate(times, gaps), 0f);
        for (int i = 0; i < times.length; i++) times[i] = Math.round(i * 1_000_000.0 / 59.94);
        assertEquals(59.94f, FpsMeter.rate(times, gaps), 0.001f);
        // 33 a second is no usual rate: not known.
        for (int i = 0; i < times.length; i++) times[i] = i * 30_303L;
        assertEquals(0f, FpsMeter.rate(times, gaps), 0f);
    }

    @Test public void theMeterSaysOnceTwoRunsAgree() {
        float[] said = {0f}, guess = {0f};
        int[] stream = {-1};
        FpsMeter m = new FpsMeter((s, f, sure) -> {
            if (!sure) {
                guess[0] = f;
                return;
            }
            stream[0] = s;
            said[0] = f;
        });
        int s = m.reset();
        Format plain = new Format.Builder().build();
        long t = 0;
        // One run of 50 a second: a guess (for the TV's refresh rate), no chip yet.
        for (int i = 0; i < FpsMeter.WINDOW; i++) m.onVideoFrameAboutToBeRendered(t += 20_000L, 0, plain, null);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0f, said[0], 0f);
        assertEquals(50f, guess[0], 0f);
        // A second that agrees: 50.
        for (int i = 0; i < FpsMeter.WINDOW; i++) m.onVideoFrameAboutToBeRendered(t += 20_000L, 0, plain, null);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(50f, said[0], 0f);
        assertEquals(s, stream[0]);
        // A stream that says its own rate: that, at once.
        said[0] = 0f;
        int s2 = m.reset();
        m.onVideoFrameAboutToBeRendered(1000, 0, new Format.Builder().setFrameRate(25f).build(), null);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(25f, said[0], 0f);
        assertEquals(s2, stream[0]);
    }
}
