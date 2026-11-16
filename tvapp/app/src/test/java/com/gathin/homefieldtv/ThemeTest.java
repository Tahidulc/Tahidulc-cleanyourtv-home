package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** The colour themes: mixing and tinting, every theme readable on a TV, and a theme reaching the screens' colours. */
public class ThemeTest {
    @Test public void mixBlendsEachChannelAndKeepsTheFirstsAlpha() {
        assertEquals(0xFF808080, Theme.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0x80102030, Theme.mix(0x80102030, 0xFF203040, 0f));
        assertEquals(0x80203040, Theme.mix(0x80102030, 0xFF203040, 1f));
        assertEquals(Theme.mix(0xFF102030, 0xFF203040, 1f), Theme.mix(0xFF102030, 0xFF203040, 3f));
    }

    @Test public void homefieldLeavesTheBlueScreensAsTheyCame() {
        Theme home = Theme.ALL[0];
        assertEquals("Homefield", home.name);
        assertEquals(0xFF0C4FAE, Theme.shift(0xFF0C4FAE, home.hue, home.sat, home.val));
        assertEquals(0x804F92DA, Theme.shift(0x804F92DA, home.hue, home.sat, home.val));
    }

    @Test public void shiftMovesTheHueAndKeepsAlpha() {
        int c = Theme.shift(0x800C4FAE, 158f, 1f, 0.5f);
        assertEquals(0x80, c >>> 24);
        float[] was = PosterArt.hsv(0x0C4FAE), now = PosterArt.hsv(c & 0xFFFFFF);
        assertEquals(158f, now[0], 2f);
        assertEquals(was[1], now[1], 0.03f);
        assertEquals(was[2] * 0.5f, now[2], 0.02f);
    }

    private static double luminance(int c) {
        double[] v = {((c >> 16) & 0xFF) / 255.0, ((c >> 8) & 0xFF) / 255.0, (c & 0xFF) / 255.0};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    private static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    @Test public void everyThemeReadsOnATv() {
        assertEquals(5, Theme.ALL.length);
        Set<String> names = new HashSet<>();
        for (Theme t : Theme.ALL) {
            assertTrue(t.name, names.add(t.name));
            assertTrue(t.name + ": a dark ground", luminance(t.ground) < 0.02);
            assertTrue(t.name + ": white on its cards", contrast(0xFFFFFFFF, t.cardHi) >= 7);
            assertTrue(t.name + ": its accent on the ground", contrast(t.accent, t.ground) >= 4.5);
            assertTrue(t.name + ": the accent's ink on it", contrast(t.accentInk, t.accent) >= 4.5);
            assertTrue(t.name + ": the New badge's ink", contrast(t.accentInk, t.badge) >= 4.5);
            assertTrue(t.name + ": its ring on a lit card", contrast(t.ring, t.cardHi) >= 3);
        }
    }

    @Test public void aThemeReachesTheScreensColours() {
        try {
            Theme.apply(2);
            Theme t = Theme.ALL[2];
            assertEquals(2, Theme.current());
            assertEquals(t.ground, Dark.GROUND);
            assertEquals(t.accent, Dark.ACCENT);
            assertEquals(t.badge, Dark.BADGE);
            assertEquals(Theme.mix(Theme.DEEP[0], t.tint, t.tintShare), Dark.DEEP[0]);
            assertEquals(Theme.shift(0xFF1163C8, t.hue, t.sat, t.val), Ui.BG_TOP);
            // Out of range: the first.
            Theme.apply(99);
            assertEquals(0, Theme.current());
        } finally {
            Theme.apply(0);
        }
        assertEquals(0xFFFFB547, Dark.ACCENT);
        assertArrayEquals(Theme.DEEP, Dark.DEEP);
        assertEquals(0xFF1163C8, Ui.BG_TOP);
        assertEquals(0xFF2BB5F5, Ui.tint(0xFF2BB5F5));
    }

    @Test public void thePreviewDrawsATheme() {
        int[] p = ThemePreview.palette(Theme.ALL[3]);
        assertEquals(Theme.ALL[3].accent, p[5]);
        assertEquals(Theme.shift(0xFF0C4FAE, Theme.ALL[3].hue, Theme.ALL[3].sat, Theme.ALL[3].val), p[13]);
    }
}
