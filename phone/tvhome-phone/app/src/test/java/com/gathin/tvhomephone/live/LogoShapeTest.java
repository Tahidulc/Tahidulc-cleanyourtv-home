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

/** See-through without a blur: a channel's logo learned pixel by pixel (what it lets through, what it adds) and taken back out. */
public class LogoShapeTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final int W = 120, H = 90, BL = 30, BT = 25, BR = 90, BB = 65;

    /**
     * A picture unlike the one before and the ones after, soft as a match's are close up (its light at its own level,
     * with broad shading at its own slant and some finer at another), as one shot after another.
     */
    private static int[] picture(int t) {
        java.util.Random r = new java.util.Random(1000L + t);
        double level = 40 + 160 * r.nextDouble(), th = 2 * Math.PI * r.nextDouble(), th2 = 2 * Math.PI * r.nextDouble();
        double f = 25 + 40 * r.nextDouble(), f2 = 9 + 6 * r.nextDouble(), ph = 6.3 * r.nextDouble(), ph2 = 6.3 * r.nextDouble();
        int tint = r.nextInt(40) - 20;
        int[] px = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double v = level + 45 * Math.sin((x * Math.cos(th) + y * Math.sin(th)) / f + ph)
                        + 12 * Math.sin((x * Math.cos(th2) + y * Math.sin(th2)) / f2 + ph2);
                int g = (int) Math.max(0, Math.min(255, Math.round(v)));
                int rr = Math.max(0, Math.min(255, g + tint)), b = Math.max(0, Math.min(255, g - tint));
                px[y * W + x] = 0xFF000000 | (rr << 16) | (g << 8) | b;
            }
        }
        return px;
    }

    /** How solid a broadcaster's logo is at a pixel: a see-through box (22%) with a bar across it and a post (85%). */
    private static float seeThroughLogo(int x, int y) {
        boolean bar = y >= 40 && y < 43 && x >= 40 && x < 80, post = x >= 40 && x < 43 && y >= 33 && y < 58;
        if (bar || post) return 0.85f;
        return x >= 36 && x < 84 && y >= 30 && y < 50 ? 0.22f : 0f;
    }

    /** A solid block of a logo. */
    private static float blockLogo(int x, int y) {
        return x >= 35 && x < 85 && y >= 30 && y < 62 ? 1f : 0f;
    }

    private static float noLogo(int x, int y) {
        return 0f;
    }

    private interface Logo {
        float at(int x, int y);
    }

    /** The picture with the logo laid over it (white, as solid as it is at each pixel). */
    private static int[] withLogo(int[] px, Logo logo) {
        int[] out = px.clone();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float a = logo.at(x, y);
                if (a <= 0f) continue;
                int p = out[y * W + x], q = 0xFF000000;
                for (int c = 0; c < 3; c++) {
                    int s = 16 - 8 * c, v = Math.round(a * 255 + (1 - a) * ((p >> s) & 0xFF));
                    q |= v << s;
                }
                out[y * W + x] = q;
            }
        }
        return out;
    }

    private static List<int[]> frames(int n, Logo logo) {
        List<int[]> out = new ArrayList<>();
        for (int t = 0; t < n; t++) out.add(withLogo(picture(t), logo));
        return out;
    }

    @Test public void aSeeThroughLogoIsLearnedPixelByPixel() {
        LogoShape.Model m = LogoShape.learn(frames(40, LogoShapeTest::seeThroughLogo), W, H, BL, BT, BR, BB, 1f);
        assertNotNull(m);
        assertEquals(LogoShape.Model.SEEN, m.state);
        int inLogo = 0, found = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                float a = seeThroughLogo(x, y);
                if (a > 0f) {
                    inLogo++;
                    if (m.logo[i]) found++;
                } else {
                    // Nothing far off the logo is taken for it.
                    boolean near = x >= 33 && x < 87 && y >= 27 && y < 53;
                    if (!near) assertFalse("(" + x + "," + y + ")", m.logo[i]);
                }
            }
        }
        assertTrue("found " + found + " of " + inLogo, found >= inLogo * 0.97);
        // The box lets most of the picture through and is worked back; the bar and the post are too solid (filled in).
        float boxT = m.t[45 * W + 60], barT = m.t[41 * W + 60];
        assertTrue("box " + boxT, Math.abs(boxT - 0.78f) < 0.12f);
        assertTrue("bar " + barT, barT < LogoShape.HI);
        assertNull(LogoShape.learn(frames(2, LogoShapeTest::seeThroughLogo), W, H, BL, BT, BR, BB, 1f));
    }

    @Test public void theLogoIsTakenBackOutOfAFreshPicture() {
        LogoShape.Model m = LogoShape.learn(frames(40, LogoShapeTest::seeThroughLogo), W, H, BL, BT, BR, BB, 1f);
        assertNotNull(m);
        for (int t = 100; t < 104; t++) {
            int[] clean = picture(t), shown = withLogo(clean, LogoShapeTest::seeThroughLogo);
            int[] fill = LogoShape.patch(shown, W, H, m, 0, 0, W, H);
            assertNotNull(fill);
            double err = 0, before = 0;
            int n = 0;
            for (int y = 32; y < 48; y++) {
                for (int x = 45; x < 80; x++) {
                    int i = y * W + x, f = fill[i];
                    float a = (f >>> 24) / 255f;
                    for (int c = 0; c < 3; c++) {
                        int s = 16 - 8 * c;
                        float live = (shown[i] >> s) & 0xFF, want = (clean[i] >> s) & 0xFF, d = (f >> s) & 0xFF;
                        err += Math.abs(live * (1 - a) + d * a - want);
                        before += Math.abs(live - want);
                        n++;
                    }
                }
            }
            // Over the see-through box (and the bar across it): close to the picture as it is without the logo.
            assertTrue("picture " + t + ": " + err / n + " (before " + before / n + ")", err / n < 4 && before / n > 12);
        }
        // Off the logo nothing is laid over the picture.
        int[] fill = LogoShape.patch(withLogo(picture(7), LogoShapeTest::seeThroughLogo), W, H, m, 0, 0, W, H);
        assertEquals(0, fill[5 * W + 5]);
    }

    @Test public void aSolidBlockStaysOnItsPlate() {
        LogoShape.Model m = LogoShape.learn(frames(30, LogoShapeTest::blockLogo), W, H, BL, BT, BR, BB, 1f);
        assertNotNull(m);
        assertEquals(LogoShape.Model.BLOCK, m.state);
    }

    @Test public void nothingFoundIsStillLearningNeverClear() {
        LogoShape.Model m = LogoShape.learn(frames(30, LogoShapeTest::noLogo), W, H, BL, BT, BR, BB, 1f);
        assertNotNull(m);
        assertEquals(LogoShape.Model.NONE, m.state);
        LogoShape.Here here = new LogoShape.Here(null, "plain", 1920, 1080, 0, 0, W, H, BL, BT, BR, BB, Runnable::run);
        for (int t = 0; t < 30; t++) assertNull(here.take(picture(t), t * 500L));
        assertFalse(here.known());
    }

    @Test public void picturesAreKeptOnlyWhenUnlikeAllTheOthersAndApart() {
        LogoShape.Learner l = new LogoShape.Learner(W, H);
        assertTrue(l.offer(picture(0), 0L, 400L));
        // Too soon after the last kept; the same picture again.
        assertFalse(l.offer(picture(1), 300L, 400L));
        assertFalse(l.offer(picture(0), 1_000L, 400L));
        assertTrue(l.offer(picture(1), 1_400L, 400L));
        // Like one kept before (not the last): not kept either (a shot held for long counts once).
        assertFalse(l.offer(picture(0), 2_000L, 400L));
        assertEquals(2, l.count());
        assertEquals(2, l.fresh());
        assertEquals(2, l.take().size());
        assertEquals(0, l.fresh());
        assertFalse(l.offer(new int[3], 9_000L, 400L));
    }

    @Test public void aChannelsLogoIsKeptAndComesBackAtOnce() {
        File dir = new File(tmp.getRoot(), "logo_shapes");
        LogoShape.Here here = new LogoShape.Here(dir, "sports 1", 1920, 1080, 1700, 40, W, H, BL, BT, BR, BB, Runnable::run);
        LogoShape.Fill f = null;
        int t = 0;
        // The plate (no fill) while it learns (here at once; on its own thread in the apps); then the fill.
        for (; t < LogoShape.ENOUGH - 1; t++) assertNull(here.take(withLogo(picture(t), LogoShapeTest::seeThroughLogo), t * 500L));
        assertFalse(here.known());
        for (; t < 40 && f == null; t++) f = here.take(withLogo(picture(t), LogoShapeTest::seeThroughLogo), t * 500L);
        assertNotNull(f);
        assertTrue(here.known());
        assertFalse(f.empty());
        // In the space's own pixels, round the logo.
        assertTrue(f.x >= 1700 + 30 && f.x <= 1700 + 37 && f.y >= 40 + 25 && f.y <= 40 + 31);
        // The same channel again: its logo from its file, the fill at once.
        LogoShape.Here again = new LogoShape.Here(dir, "sports 1", 1920, 1080, 1700, 40, W, H, BL, BT, BR, BB, Runnable::run);
        assertNotNull(again.take(withLogo(picture(99), LogoShapeTest::seeThroughLogo), 0L));
        assertTrue(again.known());
        // The cover moved a little: the logo is where it was on the screen, so it still serves.
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

    @Test public void theWorkingsHoldTogether() {
        byte[] s = new byte[1000];
        for (int i = 300; i < 700; i++) s[i] = (byte) (i % 7 == 0 ? 1 : 2);
        byte[] packed = LogoShape.pack(s);
        assertTrue(packed.length < s.length);
        assertArrayEquals(s, LogoShape.unpack(packed, s.length));
        assertNull(LogoShape.unpack(packed, s.length - 1));
        boolean[] m = new boolean[36];
        m[3 * 6 + 2] = true;
        m[3 * 6 + 4] = true;
        m[5 * 6 + 2] = true;
        assertArrayEquals(new int[]{2, 3, 5, 6}, LogoShape.bounds(m, 6, 6));
        assertNull(LogoShape.bounds(new boolean[9], 3, 3));
        assertEquals(1f, LogoShape.overlap(new boolean[4], new boolean[4]), 0f);
        assertEquals(0.5f, LogoShape.overlap(new boolean[]{true, true, false}, new boolean[]{true, false, false}), 1e-6f);
        boolean[] ring = new boolean[25];
        for (int i = 0; i < 25; i++) ring[i] = i % 5 > 0 && i % 5 < 4 && i / 5 > 0 && i / 5 < 4;
        assertEquals(2, LogoShape.depth(ring, 5, 5));
    }

    @Test public void aLookAtTheLogoIsWrittenOnlyWhenAskedForAndOnce() throws Exception {
        File out = tmp.newFolder("see-through");
        File before = LogoShape.dumpDir;
        LogoShape.dumpDir = out;
        try {
            LogoShape.Here here = new LogoShape.Here(null, "sports 3", 1920, 1080, 1700, 40, W, H, BL, BT, BR, BB, Runnable::run);
            int t = 0;
            // Not asked for: nothing written.
            for (; t < LogoShape.ENOUGH; t++) here.take(withLogo(picture(t), LogoShapeTest::seeThroughLogo), t * 500L);
            assertFalse(new File(out, "frames.bin").exists());
            // Asked for: the next learning writes the pictures and what was learned, and the ask is used up.
            File ask = new File(out, "dump");
            assertTrue(ask.createNewFile());
            for (; t < 80 && ask.exists(); t++) here.take(withLogo(picture(t), LogoShapeTest::seeThroughLogo), t * 500L);
            assertFalse(ask.exists());
            File frames = new File(out, "frames.bin"), model = new File(out, "model.bin");
            int nf;
            try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.FileInputStream(frames))) {
                assertEquals(0x4C534431, in.readInt());
                int[] head = new int[11];
                for (int i = 0; i < head.length; i++) head[i] = in.readInt();
                assertArrayEquals(new int[]{1920, 1080, 1700, 40, W, H, BL, BT, BR, BB}, java.util.Arrays.copyOf(head, 10));
                nf = head[10];
            }
            assertTrue(nf > LogoShape.ENOUGH);
            assertEquals(48L + (long) nf * W * H * 4, frames.length());
            try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.FileInputStream(model))) {
                assertEquals(0x4C534431, in.readInt());
                assertEquals(LogoShape.Model.SEEN, in.readInt());
            }
            assertEquals(8L + W * H + 4L * 4 * W * H, model.length());
            assertFalse(new File(out, "frames.bin.new").exists());
        } finally {
            LogoShape.dumpDir = before;
        }
    }
}
