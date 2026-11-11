package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** See-through without a blur: a channel's logo learned stroke by stroke, and its strokes alone filled in. */
public class LogoShapeTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final int W = 80, H = 60, BL = 10, BT = 10, BR = 70, BB = 50;

    /** A picture that moves on from one to the next (a soft pattern drifting), as a match's does. */
    private static int[] picture(int t) {
        int[] px = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double v = 120 + 70 * Math.sin((x + 3.1 * t) / 5.0) * Math.cos((y - 2.3 * t) / 7.0);
                int g = (int) Math.round(v), r = Math.min(255, g + 20), b = Math.max(0, g - 25);
                px[y * W + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return px;
    }

    /** A thin logo: two bars and a post, white at 85% over the picture (as a channel's sits on its picture). */
    private static boolean thinLogo(int x, int y) {
        return (y >= 20 && y < 23 && x >= 15 && x < 60) || (x >= 15 && x < 18 && y >= 15 && y < 40)
                || (y >= 32 && y < 35 && x >= 30 && x < 55);
    }

    /** A solid block of a logo. */
    private static boolean blockLogo(int x, int y) {
        return x >= 20 && x < 60 && y >= 15 && y < 45;
    }

    private interface Logo {
        boolean at(int x, int y);
    }

    private static int[] withLogo(int[] px, Logo logo) {
        int[] out = px.clone();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (!logo.at(x, y)) continue;
                int p = out[y * W + x];
                int r = (int) (0.85 * 255 + 0.15 * ((p >> 16) & 0xFF)), g = (int) (0.85 * 255 + 0.15 * ((p >> 8) & 0xFF)),
                        b = (int) (0.85 * 255 + 0.15 * (p & 0xFF));
                out[y * W + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    private static List<int[]> frames(int n, Logo logo) {
        List<int[]> out = new ArrayList<>();
        for (int t = 0; t < n; t++) out.add(withLogo(picture(t), logo));
        return out;
    }

    @Test public void theLogosOwnStrokesAreLearnedAndLittleElse() {
        byte[] s = LogoShape.learn(frames(20, LogoShapeTest::thinLogo), W, H, BL, BT, BR, BB, 1f);
        assertNotNull(s);
        int logo = 0, covered = 0, marked = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean on = s[y * W + x] != 0;
                if (on) marked++;
                if (!thinLogo(x, y)) continue;
                logo++;
                if (s[y * W + x] == LogoShape.CORE) covered++;
            }
        }
        // Every pixel of the logo is in it, and not much more than the logo and its fine edge.
        assertEquals(logo, covered);
        assertTrue("marked " + marked + " for a logo of " + logo, marked < logo * 4);
        // Nothing outside the cover's box.
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean inBox = x >= BL - 2 && x < BR + 2 && y >= BT - 2 && y < BB + 2;
                if (!inBox) assertEquals(0, s[y * W + x]);
            }
        }
        assertTrue(LogoShape.deepShare(s, W, H, LogoShape.DEEP) <= LogoShape.BLOCK_SHARE);
        assertNull(LogoShape.learn(frames(2, LogoShapeTest::thinLogo), W, H, BL, BT, BR, BB, 1f));
    }

    @Test public void aSolidBlockOfALogoIsNotOneToSeeThrough() {
        byte[] s = LogoShape.learn(frames(20, LogoShapeTest::blockLogo), W, H, BL, BT, BR, BB, 1f);
        assertNotNull(s);
        assertTrue(LogoShape.deepShare(s, W, H, LogoShape.DEEP) > LogoShape.BLOCK_SHARE);
    }

    @Test public void onlyTheStrokesAreFilledInFromThePictureBesideThem() {
        int w = 12, h = 9;
        int[] px = new int[w * h];
        byte[] shape = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean stroke = y == 4 && x >= 3 && x < 9;
                boolean rim = !stroke && y >= 3 && y <= 5 && x >= 2 && x < 10;
                shape[y * w + x] = stroke ? LogoShape.CORE : rim ? LogoShape.EDGE : 0;
                // Green beside it, the logo's white on its stroke.
                px[y * w + x] = stroke ? 0xFFFFFFFF : 0xFF2E7D32;
            }
        }
        int[] out = LogoShape.patch(px, w, h, shape);
        assertNotNull(out);
        for (int i = 0; i < out.length; i++) {
            int a = out[i] >>> 24;
            if (shape[i] == 0) {
                // Clear: the live picture shows there, as sharp as it is.
                assertEquals(0, out[i]);
            } else {
                assertEquals(shape[i] == LogoShape.CORE ? 255 : LogoShape.EDGE_ALPHA, a);
                // The logo's white gone: the pitch's green.
                assertEquals(0x2E7D32, out[i] & 0xFFFFFF);
            }
        }
        assertNull(LogoShape.patch(px, w, h, new byte[3]));
    }

    @Test public void picturesAreKeptOnlyWhenTheyMovedOnAndAreApart() {
        LogoShape.Learner l = new LogoShape.Learner(W, H);
        assertTrue(l.offer(picture(0), 0L, 400L));
        // A new one too soon after the last kept, or the same picture again: not kept.
        assertFalse(l.offer(picture(1), 300L, 400L));
        assertFalse(l.offer(picture(0), 1_000L, 400L));
        assertTrue(l.offer(picture(1), 1_400L, 400L));
        assertEquals(2, l.count());
        assertEquals(2, l.fresh());
        assertEquals(2, l.take().size());
        assertEquals(0, l.fresh());
        assertFalse(l.offer(new int[3], 9_000L, 400L));
    }

    @Test public void aChannelsShapeIsKeptAndComesBackAtOnce() {
        File dir = new File(tmp.getRoot(), "logo_shapes");
        LogoShape.Here here = new LogoShape.Here(dir, "sports 1", 1920, 1080, 1700, 40, W, H, BL, BT, BR, BB, Runnable::run);
        LogoShape.Fill f = null;
        int t = 0;
        // The plate (no fill) while it learns (here at once; on its own thread in the apps); then the fill, its strokes alone.
        for (; t < LogoShape.ENOUGH - 1; t++) assertNull(here.take(withLogo(picture(t), LogoShapeTest::thinLogo), t * 500L));
        assertFalse(here.known());
        for (; t < 40 && f == null; t++) f = here.take(withLogo(picture(t), LogoShapeTest::thinLogo), t * 500L);
        assertNotNull(f);
        assertTrue(here.known());
        assertFalse(f.empty());
        // In the screen's own pixels, round the logo (its post at x 15 of the place).
        assertTrue(f.x >= 1700 && f.x <= 1700 + 15 && f.y >= 40 && f.y <= 40 + 15);
        // The same channel again: its shape from its file, the fill at once.
        LogoShape.Here again = new LogoShape.Here(dir, "sports 1", 1920, 1080, 1700, 40, W, H, BL, BT, BR, BB, Runnable::run);
        assertNotNull(again.take(picture(99), 0L));
        assertTrue(again.known());
        // The cover moved a little: the logo is where it was on the screen, so the shape still serves.
        LogoShape.Here moved = new LogoShape.Here(dir, "sports 1", 1920, 1080, 1698, 38, W, H, BL + 2, BT + 2, BR + 2, BB + 2,
                Runnable::run);
        assertNotNull(moved.take(picture(98), 0L));
        // Moved far off it, another channel, another size of picture: learned afresh.
        assertNull(new LogoShape.Here(dir, "sports 1", 1920, 1080, 1000, 400, W, H, BL, BT, BR, BB, Runnable::run).take(picture(97), 0L));
        assertNull(new LogoShape.Here(dir, "sports 2", 1920, 1080, 1700, 40, W, H, BL, BT, BR, BB, Runnable::run).take(picture(96), 0L));
        assertNull(new LogoShape.Here(dir, "sports 1", 1280, 720, 1700, 40, W, H, BL, BT, BR, BB, Runnable::run).take(picture(95), 0L));
        assertTrue(again.same("sports 1", 1920, 1080, 1700, 40, W, H, BL, BT, BR, BB));
        assertFalse(again.same("sports 1", 1920, 1080, 1701, 40, W, H, BL, BT, BR, BB));
    }

    @Test public void aShapePacksSmallAndComesBackWhole() {
        byte[] s = new byte[1000];
        for (int i = 300; i < 700; i++) s[i] = i % 7 == 0 ? LogoShape.EDGE : LogoShape.CORE;
        byte[] packed = LogoShape.pack(s);
        assertTrue(packed.length < s.length);
        assertArrayEquals(s, LogoShape.unpack(packed, s.length));
        assertNull(LogoShape.unpack(packed, s.length - 1));
        assertArrayEquals(new int[]{2, 3, 5, 6}, LogoShape.bounds(new byte[]{0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0,
                0, 0, 2, 1, 2, 0,
                0, 0, 0, 0, 0, 0,
                0, 0, 1, 0, 0, 0}, 6, 6));
        assertNull(LogoShape.bounds(new byte[9], 3, 3));
        assertEquals(1f, LogoShape.overlap(new byte[4], new byte[4]), 0f);
        assertEquals(0.5f, LogoShape.overlap(new byte[]{2, 2, 0}, new byte[]{2, 0, 0}), 1e-6f);
    }
}
