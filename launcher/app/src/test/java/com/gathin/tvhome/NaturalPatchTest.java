package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

/** LogoPatch.grain: the channel's logo hidden under the picture's own light and texture, meeting the picture at its edges. */
public class NaturalPatchTest {
    /** A pitch-like picture: green with mowing stripes and grain, w by h; a white logo in the box (x0, y0, iw, ih). */
    private static int[] picture(int w, int h, int x0, int y0, int iw, int ih, long seed) {
        Random r = new Random(seed);
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int stripe = ((x + y / 2) / 12) % 2 == 0 ? 18 : 0;
                int g = 110 + stripe + r.nextInt(17) - 8, rd = 40 + stripe / 2 + r.nextInt(9) - 4, b = 35 + r.nextInt(9) - 4;
                px[y * w + x] = 0xFF000000 | (rd << 16) | (g << 8) | b;
            }
        }
        for (int y = y0 + 3; y < y0 + ih - 3; y++) for (int x = x0 + 4; x < x0 + iw - 4; x++) px[y * w + x] = 0xFFFFFFFF;
        return px;
    }

    private static double grad(int[] p, int w, int h) {
        double s = 0;
        int n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x + 1 < w; x++) {
                s += Math.abs(((p[y * w + x] >> 8) & 0xFF) - ((p[y * w + x + 1] >> 8) & 0xFF));
                n++;
            }
        }
        return s / n;
    }

    @Test
    public void theLogoGoesUnderThePicturesOwnTexture() {
        int iw = 60, ih = 30, ml = 60, mt = 20, mr = 60, mb = 30;
        int w = ml + iw + mr, h = mt + ih + mb;
        int[] px = picture(w, h, ml, mt, iw, ih, 7);
        int[] out = LogoPatch.grain(px, w, h, ml, mt, mr, mb);
        assertNotNull(out);
        assertEquals(iw * ih, out.length);
        // Nothing of the white logo: every pixel as dark as the pitch round it.
        for (int c : out) assertTrue(((c >> 16) & 0xFF) < 90 && (c & 0xFF) < 90);
        // Sharp: the grain and stripes carried in, not smoothed away (the old blend's would be near nothing).
        int[] around = new int[iw * ih];
        for (int y = 0; y < ih; y++) for (int x = 0; x < iw; x++) around[y * iw + x] = px[(mt + ih + y) * w + ml + x];
        assertTrue(grad(out, iw, ih) > 0.5 * grad(around, iw, ih));
        int[] old = LogoPatch.fill(px, w, h, ml, mt, mr, mb);
        assertTrue(grad(out, iw, ih) > 1.5 * grad(old, iw, ih));
        // Solid inside, the outermost pixel a little see-through.
        assertEquals(0xFF, (out[(ih / 2) * iw + iw / 2] >>> 24));
        assertTrue((out[0] >>> 24) < 0xFF);
    }

    @Test
    public void itMeetsThePictureAtItsEdges() {
        // A picture lit from one side to the other: the copy shaded to match on both sides.
        int iw = 40, ih = 20, ml = 40, mt = 0, mr = 40, mb = 20;
        int w = ml + iw + mr, h = mt + ih + mb;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int v = 40 + x;
            px[y * w + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        int[] out = LogoPatch.grain(px, w, h, ml, mt, mr, mb);
        assertNotNull(out);
        for (int y = 2; y < ih - 2; y++) {
            int left = out[y * iw + 1] & 0xFF, right = out[y * iw + iw - 2] & 0xFF;
            assertTrue("left " + left, Math.abs(left - (40 + ml + 1)) <= 6);
            assertTrue("right " + right, Math.abs(right - (40 + ml + iw - 2)) <= 6);
        }
    }

    @Test
    public void tooLittleRoomFallsBackToTheBlend() {
        int iw = 40, ih = 20, m = 3;
        int w = iw + 2 * m, h = ih + 2 * m;
        int[] px = picture(w, h, m, m, iw, ih, 3);
        assertNull(LogoPatch.grain(px, w, h, m, m, m, m));
        assertNotNull(LogoPatch.best(px, w, h, m, m, m, m));
    }

    @Test
    public void softeningKeepsAFlatPictureFlat() {
        int[] flat = new int[30 * 20];
        java.util.Arrays.fill(flat, 77);
        for (int v : LogoPatch.boxBlur(flat, 30, 20, 6)) assertEquals(77, v);
        int[] dot = new int[9 * 9];
        dot[40] = 90;
        int[] soft = LogoPatch.boxBlur(dot, 9, 9, 1);
        assertTrue(soft[40] > 0 && soft[40] < 90 && soft[0] == 0);
    }

    @Test
    public void mirroredFromBesideItAndBackAndForthInANarrowStrip() {
        assertEquals(0, LogoPatch.bounce(0, 5));
        assertEquals(4, LogoPatch.bounce(4, 5));
        assertEquals(4, LogoPatch.bounce(5, 5));
        assertEquals(0, LogoPatch.bounce(9, 5));
        assertEquals(1, LogoPatch.bounce(11, 5));
        // below: the inside's bottom row from the picture's row just under it
        assertEquals((2 + 3) * 10 + 1, LogoPatch.from(0, 0, 2, 10, 4, 3, 1, 2, 5));
        // left: the inside's first column from the picture's column just left of it
        assertEquals(2 * 10 + 0, LogoPatch.from(2, 0, 0, 10, 4, 3, 1, 2, 1));
        float[] room = LogoPatch.room(1700, 40, 180, 90, 1920, 1080, 20);
        assertEquals(180, room[0], 0.01);
        assertEquals(40, room[1], 0.01);
        assertEquals(40, room[2], 0.01);
        assertEquals(90, room[3], 0.01);
    }
}
