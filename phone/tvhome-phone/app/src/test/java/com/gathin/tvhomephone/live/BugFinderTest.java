package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The channel's scoreboard found on drawn frames: a moving picture, a channel logo, a box with a running clock. */
public class BugFinderTest {
    private static final String[] FONT = {
            "01110100011001110101110011000101110", // 0
            "00100011000010000100001000010001110", // 1
            "01110100010000100010001000100011111", // 2
            "11111000100010000010000011000101110", // 3
            "00010001100101010010111110001000010", // 4
            "11111100001111000001000011000101110", // 5
            "00110010001000011110100011000101110", // 6
            "11111000010001000100010000100001000", // 7
            "01110100011000101110100011000101110", // 8
            "01110100011000101111000010001001100", // 9
    };

    /** A 5 by 7 digit at (x, y), each dot s by s pixels. */
    private static void digit(byte[] f, int w, int x, int y, int d, int s, int ink) {
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 5; c++) {
                if (FONT[d].charAt(r * 5 + c) != '1') continue;
                for (int yy = 0; yy < s; yy++) for (int xx = 0; xx < s; xx++) f[(y + r * s + yy) * w + x + c * s + xx] = (byte) ink;
            }
        }
    }

    /** "mm:ss" at (x, y). */
    private static void clock(byte[] f, int w, int x, int y, int secs, int s, int ink) {
        int m = secs / 60, sec = secs % 60, step = 6 * s;
        digit(f, w, x, y, m / 10 % 10, s, ink);
        digit(f, w, x + step, y, m % 10, s, ink);
        // The colon.
        for (int yy = 0; yy < s; yy++) for (int xx = 0; xx < s; xx++) {
            f[(y + 2 * s + yy) * w + x + 2 * step + xx] = (byte) ink;
            f[(y + 4 * s + yy) * w + x + 2 * step + xx] = (byte) ink;
        }
        digit(f, w, x + 2 * step + 2 * s, y, sec / 10, s, ink);
        digit(f, w, x + 3 * step + 2 * s, y, sec % 10, s, ink);
    }

    private static void box(byte[] f, int w, int x0, int y0, int x1, int y1, int v) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) f[y * w + x] = (byte) v;
    }

    /** Frame i of a broadcast, four a second: the picture panning under a scoreboard top left and a logo top right. */
    private static byte[] frame(int w, int h, int i, int startSecs, Random noise, boolean withBug) {
        byte[] f = new byte[w * h];
        double pan = i * 2.7, tilt = i * 0.9;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double v = 95 + 55 * Math.sin((x + pan) * 0.41) * Math.cos((y + tilt) * 0.29) + 25 * Math.sin((x + pan) * 0.07 + y * 0.05);
                f[y * w + x] = (byte) Math.max(0, Math.min(255, (int) v + noise.nextInt(9) - 4));
            }
        }
        // The pitch at the foot: one plain green, still whatever the camera does, with no edges.
        box(f, w, 0, h - 14, w, h, 88);
        // The channel's logo, top right: still, with edges, never ticking.
        box(f, w, 160, 6, 184, 16, 20);
        box(f, w, 164, 9, 180, 13, 235);
        if (withBug) {
            // The scoreboard, top left: its ground, two clubs' names as strokes, the score, the clock.
            box(f, w, 8, 5, 82, 17, 28);
            for (int k = 0; k < 4; k++) box(f, w, 11 + k * 3, 8, 12 + k * 3, 14, 230);
            for (int k = 0; k < 4; k++) box(f, w, 35 + k * 3, 8, 36 + k * 3, 14, 230);
            box(f, w, 25, 8, 27, 14, 250);
            box(f, w, 29, 8, 31, 14, 250);
            clock(f, w, 52, 7, startSecs + i / 4, 1, 240);
        }
        return f;
    }

    @Test public void findsTheScoreboardNotTheLogoOrThePitch() {
        int w = 192, h = 108;
        BugFinder b = new BugFinder(w, h);
        Random r = new Random(7);
        for (int i = 0; i < BugFinder.ENOUGH; i++) b.add(frame(w, h, i, 67 * 60 + 10, r, true));
        BugFinder.Box box = b.find();
        assertNotNull(box);
        // The box drawn at 8..82, 5..17 of 192 by 108.
        assertEquals(8 / 192f, box.left, 0.025f);
        assertEquals(82 / 192f, box.right, 0.025f);
        assertEquals(5 / 108f, box.top, 0.03f);
        assertEquals(17 / 108f, box.bottom, 0.03f);
        assertTrue(box.ticking);
    }

    @Test public void aLogoAloneIsNotAScoreboardWhenItIsTooSquare() {
        int w = 192, h = 108;
        BugFinder b = new BugFinder(w, h);
        Random r = new Random(3);
        for (int i = 0; i < BugFinder.ENOUGH; i++) b.add(frame(w, h, i, 0, r, false));
        BugFinder.Box box = b.find();
        // The logo is 24 by 10: it may be taken, but never as ticking, and never anywhere else.
        if (box != null) {
            assertFalse(box.ticking);
            assertTrue(box.left > 0.75f);
        }
    }

    @Test public void tooFewFramesSayNothing() {
        BugFinder b = new BugFinder(64, 36);
        Random r = new Random(1);
        for (int i = 0; i < 5; i++) b.add(frame(64, 36, i, 0, r, false));
        assertNull(b.find());
    }

    /** The scoreboard's crop, bigger: 3 pixels a dot, the clock running from startSecs, four crops a second. */
    private static List<byte[]> crops(int cw, int ch, int startSecs, int count, long seed) {
        Random r = new Random(seed);
        List<byte[]> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            byte[] f = new byte[cw * ch];
            for (int k = 0; k < f.length; k++) f[k] = (byte) (30 + r.nextInt(13) - 6);
            box(f, cw, 4, 6, 40, 27, 230);
            clock(f, cw, 60, 3, startSecs + i / 4, 3, 240);
            out.add(f);
        }
        return out;
    }

    @Test public void theClockIsSeenTurningAMinuteAndNotAtTenSeconds() {
        int cw = 160, ch = 28;
        // From 66:31, 90 seconds: it turns 67:00 at second 29 (crop 116) and 68:00 at second 89 (crop 356).
        List<byte[]> crops = crops(cw, ch, 66 * 60 + 31, 360, 11);
        BugFinder.ClockReader reader = new BugFinder.ClockReader(cw, ch);
        List<Integer> turned = new ArrayList<>();
        for (int i = 0; i < crops.size(); i++) if (reader.add(crops.get(i))) turned.add(i);
        assertEquals(2, turned.size());
        assertEquals(116, (int) turned.get(0));
        assertEquals(356, (int) turned.get(1));
    }

    @Test public void aStoppedClockNeverTurns() {
        int cw = 160, ch = 28;
        BugFinder.ClockReader reader = new BugFinder.ClockReader(cw, ch);
        Random r = new Random(5);
        for (int i = 0; i < 300; i++) {
            byte[] f = new byte[cw * ch];
            for (int k = 0; k < f.length; k++) f[k] = (byte) (30 + r.nextInt(13) - 6);
            clock(f, cw, 60, 3, 45 * 60, 3, 240);
            assertFalse(reader.add(f));
        }
    }

    @Test public void unevenCropsStillSeeTheMinute() {
        int cw = 160, ch = 28;
        Random r = new Random(21);
        BugFinder.ClockReader reader = new BugFinder.ClockReader(cw, ch);
        long t = 0;
        int start = 23 * 60 + 25, turns = 0;
        long turnedAt = -1;
        // Crops 200 to 320 ms apart for 50 seconds: the clock turns 24:00 at 35 s (two ten-second flips seen first).
        while (t < 50_000) {
            byte[] f = new byte[cw * ch];
            for (int k = 0; k < f.length; k++) f[k] = (byte) (30 + r.nextInt(13) - 6);
            box(f, cw, 4, 6, 40, 27, 230);
            clock(f, cw, 60, 3, start + (int) (t / 1000), 3, 240);
            if (reader.add(f)) {
                turns++;
                turnedAt = t;
            }
            t += 200 + r.nextInt(121);
        }
        assertEquals(1, turns);
        assertTrue("seen at " + turnedAt, turnedAt >= 35_000 && turnedAt < 35_400);
    }
}
