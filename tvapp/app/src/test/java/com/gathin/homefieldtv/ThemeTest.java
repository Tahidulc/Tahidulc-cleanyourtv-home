package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The colour themes: Indigo • Blue first with the colours measured on the family's TV, the tokens drawn over the ground
 * and the panels making the colours seen there, every pair readable, and a choice reaching every screen's colours.
 */
public class ThemeTest {
    @Test public void mixBlendsEachChannelAndKeepsTheFirstsAlpha() {
        assertEquals(0xFF808080, Theme.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0x80102030, Theme.mix(0x80102030, 0xFF203040, 0f));
        assertEquals(0x80203040, Theme.mix(0x80102030, 0xFF203040, 1f));
        assertEquals(Theme.mix(0xFF102030, 0xFF203040, 1f), Theme.mix(0xFF102030, 0xFF203040, 3f));
    }

    @Test public void indigoBlueIsFirstWithTheFamilysColours() {
        assertEquals("Indigo • Blue", Theme.name(Theme.INDIGO, Theme.BLUE));
        Theme.Primary p = Theme.PRIMARIES[Theme.INDIGO];
        assertEquals("Indigo", p.name);
        assertEquals(0xFF151B3F, p.ground);
        assertEquals(0xFF1F295F, p.panel);
        assertEquals(0xFF2A377F, p.header);
        assertEquals(0xFF1C2759, p.playback);
        Theme.Accent a = Theme.ACCENTS[Theme.BLUE];
        assertEquals("Blue", a.name);
        assertEquals(0xFF2196F3, a.colour);
        assertEquals(0xFF90CAF9, a.light);
        // Out of range: Indigo • Blue.
        assertEquals("Indigo • Blue", Theme.name(99, -1));
    }

    /** Within two of each channel: the device's values are medians of a few pixels. */
    private static void close(String what, int want, int got) {
        for (int shift = 0; shift <= 16; shift += 8) {
            int w = (want >> shift) & 0xFF, g = (got >> shift) & 0xFF;
            assertTrue(String.format("%s: want #%06X, got #%06X", what, want & 0xFFFFFF, got & 0xFFFFFF), Math.abs(w - g) <= 2);
        }
    }

    @Test public void theTokensMakeTheColoursMeasuredOnTheTv() {
        try {
            Theme.apply(Theme.INDIGO, Theme.BLUE);
            int ground = Palette.GROUND, panel = Palette.PANEL;
            close("focus on a panel", 0xFF1F5296, Palette.over(panel, Palette.FOCUS));
            close("focus on the ground", 0xFF194982, Palette.over(ground, Palette.FOCUS));
            close("selected on a panel", 0xFF323B6D, Palette.over(panel, Palette.SELECTED));
            close("selected on the ground", 0xFF292F50, Palette.over(ground, Palette.SELECTED));
            close("text on the ground", 0xFFEFF0F2, Palette.over(ground, Palette.TEXT1));
            close("text on a panel", 0xFFF0F1F4, Palette.over(panel, Palette.TEXT1));
            close("grey text on the ground", 0xFFA1A4B2, Palette.over(ground, Palette.TEXT2));
            close("grey text on a panel", 0xFFA5A9BF, Palette.over(panel, Palette.TEXT2));
            close("text switched off", 0xFF737A9B, Palette.over(panel, Palette.TEXT3));
            close("the rail's other icons", 0xFF878CA9, Palette.over(panel, Palette.ICON_OFF));
            close("a guide cell", 0xFF1D2346, Palette.over(ground, Palette.CELL));
            close("the focused row's cells", 0xFF444965, Palette.over(ground, Palette.CELL_ROW));
            close("a cell's aired part", 0xFF252B4C, Palette.over(ground, Palette.CELL_AIRED));
            close("the guide's divider", 0xFF3A3F5D, Palette.over(ground, Palette.GUIDE_DIVIDER));
            close("a progress bar's track", 0xFF3F4462, Palette.over(ground, Palette.TRACK));
            close("the ground under the scrim", 0xFF080B19, Palette.over(ground, Palette.SCRIM));
            close("a switch that is off", 0xFF656C91, Palette.over(panel, Palette.SWITCH_OFF));
            close("a switch off on the remote's row", 0xFF19427A,
                    Palette.over(Palette.over(panel, Palette.FOCUS), Palette.SWITCH_OFF_FOCUS));
            close("the playback panel over white", 0xFF434C76, Palette.over(0xFFFFFFFF, Palette.PLAYBACK));
            assertEquals(0xFF2A377F, Palette.HEADER);
            assertEquals(0xFF2196F3, Palette.ACCENT);
            assertEquals(0xFF90CAF9, Palette.ACCENT_LIGHT);
        } finally {
            Theme.apply(Theme.INDIGO, Theme.BLUE);
        }
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

    @Test public void everyPairReadsOnATv() {
        Set<String> names = new HashSet<>();
        for (Theme.Primary p : Theme.PRIMARIES) {
            assertTrue(p.name, names.add(p.name));
            assertTrue(p.name + ": a dark ground", luminance(p.ground) < 0.02);
            assertTrue(p.name + ": its steps get lighter", luminance(p.ground) < luminance(p.panel) && luminance(p.panel) < luminance(p.header));
            assertTrue(p.name + ": grey writing on its panels", contrast(Palette.over(p.panel, Palette.TEXT2), p.panel) >= 4.5);
            for (Theme.Accent a : Theme.ACCENTS) {
                int focus = Palette.over(p.panel, Palette.alpha(a.colour, Palette.FOCUS_ALPHA));
                String pair = p.name + " • " + a.name;
                assertTrue(pair + ": white where the remote is", contrast(Palette.over(focus, Palette.TEXT1), focus) >= 4);
                assertTrue(pair + ": the light accent on the ground", contrast(a.light, p.ground) >= 4.5);
            }
        }
        names.clear();
        for (Theme.Accent a : Theme.ACCENTS) {
            assertTrue(a.name, names.add(a.name));
            assertTrue(a.name + ": its writing on it", contrast(a.ink, a.colour) >= 3);
        }
        // The family's pair: the accent reads as section names on its panels.
        assertTrue(contrast(Theme.ACCENTS[Theme.BLUE].colour, Theme.PRIMARIES[Theme.INDIGO].panel) >= 3);
    }

    @Test public void aChoiceReachesTheScreensColours() {
        try {
            Theme.apply(6, 10);
            Theme.Primary p = Theme.PRIMARIES[6];
            Theme.Accent a = Theme.ACCENTS[10];
            assertEquals("Red • Amber", Theme.name());
            assertEquals(6, Theme.primary());
            assertEquals(10, Theme.accent());
            assertEquals(p.ground, Palette.GROUND);
            assertEquals(p.panel, Palette.PANEL);
            assertEquals(p.header, Palette.HEADER);
            assertEquals(Palette.alpha(p.playback, Palette.PLAYBACK_ALPHA), Palette.PLAYBACK);
            assertEquals(a.colour, Palette.ACCENT);
            assertEquals(a.light, Palette.ACCENT_LIGHT);
            assertEquals(a.ink, Palette.ACCENT_INK);
            assertEquals(Palette.alpha(a.colour, Palette.FOCUS_ALPHA), Palette.FOCUS);
            // The screens still drawn in Dark's colours take them too.
            assertEquals(p.ground, Dark.GROUND);
            assertEquals(p.panel, Dark.PANEL);
            assertEquals(a.colour, Dark.ACCENT);
            assertEquals(a.light, Dark.RING);
            assertEquals(Palette.over(p.ground, Palette.TEXT2), Dark.MUTED);
            assertArrayEquals(Theme.DEEP, Dark.DEEP);
            // Out of range: Indigo • Blue.
            Theme.apply(99, 99);
            assertEquals(Theme.INDIGO, Theme.primary());
            assertEquals(Theme.BLUE, Theme.accent());
        } finally {
            Theme.apply(Theme.INDIGO, Theme.BLUE);
        }
        assertEquals(0xFF151B3F, Dark.GROUND);
        assertEquals(0xFF2196F3, Dark.ACCENT);
    }

    @Test public void theColourDiscsPickAPair() {
        assertArrayEquals(new int[]{3, 4}, ThemeSettings.pick("p:3", 0, 4));
        assertArrayEquals(new int[]{2, 7}, ThemeSettings.pick("a:7", 2, 4));
        assertNull(ThemeSettings.pick("x:1", 0, 4));
        assertNull(ThemeSettings.pick("p:", 0, 4));
        assertNull(ThemeSettings.pick(5, 0, 4));
    }

    @Test public void theThemePageHasEveryColourAndTicksThePairInUse() {
        Theme.apply(Theme.INDIGO, Theme.BLUE);
        List<RowList.Row> rows = ThemeSettings.rows();
        assertEquals(1 + Theme.PRIMARIES.length + 1 + Theme.ACCENTS.length + 1, rows.size());
        assertEquals(RowList.HEADER, rows.get(0).kind);
        assertEquals(RowList.HEADER, rows.get(1 + Theme.PRIMARIES.length).kind);
        int ticked = 0;
        for (RowList.Row r : rows) if (r.checked) ticked++;
        assertEquals(2, ticked);
        assertEquals("Indigo", rows.get(ThemeSettings.focus(rows)).title);
        assertTrue(rows.get(2 + Theme.PRIMARIES.length + Theme.BLUE).checked);
        assertEquals(0xFF2196F3, rows.get(2 + Theme.PRIMARIES.length + Theme.BLUE).swatch);
    }
}
