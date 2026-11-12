package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The frosted glass under the logo cover: the picture behind it softened, with the channel's logo taken out first. */
public class FrostPaneTest {
    private static final int W = 200, H = 120;

    /** A green pitch with a bright white logo in the box 60..140 x 40..80. */
    private static int[] pitchWithLogo() {
        int[] px = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean logo = x >= 80 && x < 120 && y >= 50 && y < 70 && (x / 4 + y / 4) % 2 == 0;
                px[y * W + x] = logo ? 0xFFFFFFFF : 0xFF000000 | (40 << 16) | ((110 + (x % 7)) << 8) | 50;
            }
        }
        return px;
    }

    private static int lum(int c) {
        return (((c >> 16) & 0xFF) * 299 + ((c >> 8) & 0xFF) * 587 + (c & 0xFF) * 114) / 1000;
    }

    @Test public void theGlassIsTheSoftenedPictureRoundTheCover() {
        FrostPane.Pane p = FrostPane.of(pitchWithLogo(), W, H, null, 1600, 30, 60, 40, 140, 80);
        assertNotNull(p);
        // Room round the box for the softening, in the pictures' space.
        assertTrue(p.l <= 1600 + 60 && p.t <= 30 + 40 && p.r >= 1600 + 140 && p.b >= 30 + 80);
        assertEquals(p.w * p.h, p.argb.length);
        // Soft: no pixel far from the next.
        for (int y = 0; y < p.h; y++) {
            for (int x = 1; x < p.w; x++) assertTrue(Math.abs(lum(p.argb[y * p.w + x]) - lum(p.argb[y * p.w + x - 1])) < 40);
        }
        for (int c : p.argb) assertEquals(0xFF, c >>> 24);
        assertNull(FrostPane.of(pitchWithLogo(), W, H, null, 0, 0, 60, 40, 60, 80));
    }

    @Test public void theChannelsLogoIsTakenOutBeforeItIsSoftened() {
        int[] px = pitchWithLogo();
        // See-through's fill for this picture: the pitch over the logo's box, solid.
        int[] fill = new int[40 * 20];
        for (int y = 0; y < 20; y++) for (int x = 0; x < 40; x++) fill[y * 40 + x] = 0xFF000000 | (40 << 16) | ((110 + ((80 + x) % 7)) << 8) | 50;
        LogoShape.Fill f = new LogoShape.Fill(fill, 1600 + 80, 30 + 50, 40, 20);
        FrostPane.Pane with = FrostPane.of(px, W, H, null, 1600, 30, 60, 40, 140, 80);
        FrostPane.Pane without = FrostPane.of(px, W, H, f, 1600, 30, 60, 40, 140, 80);
        int mid = (with.h / 2) * with.w + with.w / 2;
        // Left in, the logo glows through the glass; taken out, the glass is the pitch's green.
        assertTrue(lum(with.argb[mid]) > lum(without.argb[mid]) + 15);
        int g = without.argb[mid];
        assertTrue(((g >> 8) & 0xFF) > ((g >> 16) & 0xFF) + 40);
        // A fill's pixel over the picture as laid.
        assertEquals(0xFFFFFFFF, FrostPane.over(0xFFFFFFFF, 0xFF000000));
        assertEquals(0xFF123456, FrostPane.over(0x00FFFFFF, 0xFF123456));
        assertEquals(0xFF808080, FrostPane.over(0x80FFFFFF, 0xFF000000));
    }
}
