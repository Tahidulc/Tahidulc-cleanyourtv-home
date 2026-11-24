package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.NinePatch;
import android.graphics.RectF;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * The TCL's speed work: Motion's tiers (Automatic is Lite on a Mali-G52-class chip, under 3 GB of memory or with frames
 * measured slow, and Full on the Streamer; Settings' choice over it), the frame times FrameStats keeps and writes to the
 * memory log once a minute, and the drawing pieces' own sums (the shadows' corner ladder, their nine-patch, which
 * shadows may leave their middle out).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class MotionTest {
    @After public void after() {
        Motion.force(false);
    }

    @Test public void automaticIsLiteOnTheTclAndFullOnTheStreamer() {
        // The TCL: Mali-G52 and 2.4 GB.
        Motion.pretend("Mali-G52", (long) (2.4 * (1L << 30)), false);
        assertTrue(Motion.lite());
        assertTrue(Motion.words(), Motion.words().startsWith("Lite: a Mali-G52"));
        // A Mali-G52 with plenty of memory is still Lite; so is a small TV whatever its chip.
        Motion.pretend("Mali-G52 MC2", 8L << 30, false);
        assertTrue(Motion.lite());
        Motion.pretend("PowerVR Rogue GE9215", 2L << 30, false);
        assertTrue(Motion.lite());
        assertTrue(Motion.words(), Motion.words().contains("GB of memory"));
        // The Streamer: 4 GB, another chip: Full, unless its frames were measured slow.
        Motion.pretend("PowerVR Rogue GE9215", 4L << 30, false);
        assertFalse(Motion.lite());
        assertEquals("Full", Motion.words());
        assertEquals("Full", Motion.autoWords());
        Motion.pretend("Mali-G57", 4L << 30, true);
        assertTrue(Motion.lite());
        assertEquals("Lite (frames measured slow)", Motion.autoWords());
        // The memory unknown (0): the chip alone decides.
        Motion.pretend("Adreno (TM) 650", 0L, false);
        assertFalse(Motion.lite());
    }

    @Test public void theWeakChipsAreTheMaliG52Class() {
        assertTrue(Motion.weakGpu("Mali-G52"));
        assertTrue(Motion.weakGpu("Mali-G51"));
        assertTrue(Motion.weakGpu("Mali-T860"));
        assertTrue(Motion.weakGpu("Mali-450 MP"));
        assertTrue(Motion.weakGpu("mali-g52 mc1"));
        assertFalse(Motion.weakGpu("Mali-G57"));
        assertFalse(Motion.weakGpu("Mali-G310"));
        assertFalse(Motion.weakGpu("PowerVR Rogue GE9215"));
        assertFalse(Motion.weakGpu(null));
        assertFalse(Motion.weakGpu(""));
    }

    @Test public void settingsChoiceIsOverAutomaticAndKept() {
        Context app = RuntimeEnvironment.getApplication();
        Motion.pretend("PowerVR Rogue GE9215", 4L << 30, false);
        Motion.set(app, Motion.LITE);
        assertTrue(Motion.lite());
        assertEquals("Lite", Motion.words());
        Motion.set(app, Motion.FULL);
        assertFalse(Motion.lite());
        Motion.pretend("Mali-G52", 4L << 30, false);
        Motion.set(app, Motion.FULL);
        assertFalse("Full chosen on a Mali-G52", Motion.lite());
        Motion.set(app, Motion.AUTO);
        assertTrue(Motion.lite());
        assertEquals("Automatic (Lite)", MoreSettings.motionWords());
        // Kept: read again at the next start.
        Motion.load(app);
        assertEquals(Motion.AUTO, Motion.choice());
    }

    @Test public void framesMeasuredSlowTurnAutomaticLite() {
        Context app = RuntimeEnvironment.getApplication();
        Motion.pretend("PowerVR Rogue GE9215", 4L << 30, false);
        Motion.set(app, Motion.AUTO);
        assertTrue(Motion.measuring());
        assertFalse("16 ms is fine", Motion.measured(app, 16f));
        assertFalse(Motion.lite());
        assertTrue("19 ms is over 18", Motion.measured(app, 19f));
        assertTrue(Motion.lite());
        assertFalse(Motion.measuring());
    }

    @Test public void theFrameTimesAreKeptAndWrittenOnceAMinute() throws Exception {
        assertEquals(0f, FrameStats.percentile(new int[FrameStats.BUCKETS], 0, 0.5f), 0f);
        for (int i = 0; i < 90; i++) FrameStats.add("Home", 12f, 6f);
        for (int i = 0; i < 10; i++) FrameStats.add("Home", 24f, 15f);
        FrameStats.add("Guide", 9f, -1f);
        String line = FrameStats.line();
        assertNotNull(line);
        assertTrue(line, line.startsWith("frames motion="));
        assertTrue(line, line.contains("Home n=100 p50=12.3 p95=24.3 over20=10.0% gpu95=15.3"));
        assertTrue(line, line.contains(" | Guide n=1 p50=9.3 p95=9.3 over20=0.0%"));
        assertFalse("no chip's time where Android didn't give it", line.contains("Guide n=1 p50=9.3 p95=9.3 over20=0.0% gpu95"));
        // Counted again from nothing: a minute with nothing drawn writes no line.
        assertEquals(null, FrameStats.line());
        // Written to the memory log after the minute's memory line.
        File f = new File(RuntimeEnvironment.getApplication().getCacheDir(), "memlog-test.txt");
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        MemLog.append(f, "2026-11-18 20:31 heap=1.0/2.0/192MB");
        FrameStats.add("Home", 30f, -1f);
        MemLog.append(f, "2026-11-18 20:31 " + FrameStats.line());
        List<String> lines = Files.readAllLines(f.toPath());
        assertEquals(2, lines.size());
        assertTrue(lines.get(1), lines.get(1).matches("2026-11-18 20:31 frames motion=\\S+ Home n=1 p50=30\\.3 p95=30\\.3 over20=100\\.0%"));
    }

    @Test public void aScreensNameInTheLog() {
        assertEquals("-", FrameStats.nameOf(null));
        assertEquals(30, FrameStats.bucket(15.2f));
        assertEquals(FrameStats.BUCKETS - 1, FrameStats.bucket(400f));
    }

    @Test public void shadowsTakeAFewCornersAndOneNinePatch() {
        Ui.init(RuntimeEnvironment.getApplication());
        // The ladder: 15 % apart, so a card growing to 1.12 crosses one or two steps, never a new sprite a frame.
        assertEquals(-1, Shadows.step(0.5f));
        int a = Shadows.step(22f), b = Shadows.step(22f * 1.12f);
        assertTrue(b - a <= 1);
        assertEquals(22f, Shadows.radiusOf(a), 22f * 0.08f);
        // The nine-patch's chunk is Android's own.
        byte[] chunk = Shadows.chunk(10, 11, true);
        assertTrue(NinePatch.isNinePatchChunk(chunk));
        Bitmap sprite = Bitmap.createBitmap(21, 21, Bitmap.Config.ARGB_8888);
        new NinePatch(sprite, Shadows.chunk(10, 11, false));
        // Left out only where the box covers the middle (on its rounded corners too).
        RectF box = new RectF(0, 0, 400, 200);
        assertTrue(Shadows.covers(box, 22f, 30f, 30f, 370f, 190f));
        assertFalse("past the foot", Shadows.covers(box, 22f, 30f, 30f, 370f, 210f));
        assertFalse("outside the round corner", Shadows.covers(box, 22f, 1f, 1f, 370f, 190f));
        assertTrue("an empty middle", Shadows.covers(box, 22f, 50f, 50f, 40f, 40f));
        // Preparing a kind makes its lifted sizes now; drawing them later makes none.
        Shadows.prepare(Look07.SH_CARD_UP, Look07.R_POSTER);
        int made = Shadows.made;
        android.graphics.Canvas c = new android.graphics.Canvas(Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888));
        for (float g = 1f; g <= 1.12f; g += 0.01f) {
            RectF r = new RectF(100, 50, 100 + 210 * g, 50 + 315 * g);
            Shadows.draw(c, r, Ui.f(Look07.R_POSTER) * g, Look07.SH_CARD_UP, 1f);
        }
        assertEquals("sprites made while lifting", made, Shadows.made);
    }
}
