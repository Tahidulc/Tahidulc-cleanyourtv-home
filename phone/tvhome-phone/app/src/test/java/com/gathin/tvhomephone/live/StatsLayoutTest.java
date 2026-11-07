package com.gathin.tvhomephone.live;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 4.19: the stats beside the scoreboard never overlap, at any size the board is made: every part of every kind of stat
 * (the name, each figure, the bar or the line between, each side's squares) laid out in rooms from cramped to wide, for
 * boards small to large, in one row and in two (beside Stacked and Card), stays in its strip and clear of the rest.
 */
public class StatsLayoutTest {
    /** Each stat as it comes: {kind, name, home, away}. */
    private static final String[][] STATS = {
            {"bar", "Possession", "58%", "42%"},
            {"bar", "Shots", "13", "4"},
            {"bar", "Win chance", "64%", "13%"},
            {"bar", "Pass accuracy", "88%", "79%"},
            {"bar", "Shots (on target)", "13 (6)", "4 (1)"},
            {"text", "League table", "2nd · 61 pts", "15th · 30 pts"},
            {"form", "Form", "WWDLW", "LDWWL"},
            {"form", "Form", "WWD", "L"},
            {"one", "Player of the match", "Saka (ARS) · 2 goals · 1 assist", ""},
    };

    /** Rough widths, as wide as the bold face runs or wider: a letter at a little over half its size, the name spaced out. */
    private static float nameW(String s, float size) {
        return s.toUpperCase(Locale.UK).length() * size * 0.68f * 1.1f;
    }

    private static float valueW(String s, float size) {
        return s.length() * size * 0.6f;
    }

    @Test
    public void noPartOverlapsAnotherAtAnySize() {
        int checked = 0;
        for (float unit : new float[]{10f, 16f, 24f, 36f, 54f, 80f}) {
            for (float tall : new float[]{1f, 1.6f, 1.7f}) {
                float h = unit * tall;
                boolean two = h >= unit * 1.45f;
                for (float roomUnits : new float[]{1.5f, 2f, 3f, 4.5f, 6f, 9f, 15f, 30f}) {
                    float start = unit * 0.3f, end = start + roomUnits * unit;
                    for (String[] st : STATS) {
                        String kind = st[0];
                        float nameSize = unit * (two ? 0.28f : 0.27f), valueSize = unit * (two ? 0.46f : 0.44f);
                        float hw = "form".equals(kind) ? 0 : valueW(st[2], valueSize);
                        float aw = "one".equals(kind) || "form".equals(kind) ? 0 : valueW(st[3], valueSize);
                        int hn = "form".equals(kind) ? Math.min(5, st[2].length()) : 0, an = "form".equals(kind) ? Math.min(5, st[3].length()) : 0;
                        BoardStats.Parts p = BoardStats.layout(kind, nameW(st[1], nameSize), hw, aw, hn, an, unit, h, start, end, two);
                        String where = st[1] + " at unit " + unit + ", height " + h + ", room " + roomUnits + " units";
                        assertTrue(where + ": scale " + p.k, p.k > 0f && p.k <= 1f);
                        if ("bar".equals(kind)) assertTrue(where + ": bar " + p.bar, p.bar >= BoardStats.BAR_LEAST * unit - 0.01f);
                        float[][] seen = new float[p.boxes.size()][];
                        for (int i = 0; i < p.boxes.size(); i++) {
                            float[] b = p.boxes.get(i);
                            // As drawn: scaled by k about the room's start and the strip's middle.
                            float[] s = {start + (b[0] - start) * p.k, p.mid + (b[1] - p.mid) * p.k,
                                    start + (b[2] - start) * p.k, p.mid + (b[3] - p.mid) * p.k};
                            if (s[0] < start - 0.5f || s[2] > end + 0.5f || s[1] < -0.5f || s[3] > h + 0.5f) {
                                fail(where + ": part " + i + " leaves its strip " + java.util.Arrays.toString(s));
                            }
                            for (int j = 0; j < i; j++) {
                                float[] o = seen[j];
                                boolean overlap = s[0] < o[2] - 0.01f && o[0] < s[2] - 0.01f && s[1] < o[3] - 0.01f && o[1] < s[3] - 0.01f;
                                if (overlap) fail(where + ": parts " + j + " and " + i + " overlap");
                            }
                            seen[i] = s;
                        }
                        checked++;
                    }
                }
            }
        }
        assertEquals(6 * 3 * 8 * STATS.length, checked);
    }

    @Test
    public void aShortRoomTakesTheBarInBeforeDrawingItSmaller() {
        float unit = 30f;
        float nameSize = unit * 0.27f, valueSize = unit * 0.44f;
        float nw = nameW("Possession", nameSize), hw = valueW("58%", valueSize), aw = valueW("42%", valueSize);
        // Room to spare: the bar as drawn, at its size.
        BoardStats.Parts wide = BoardStats.layout("bar", nw, hw, aw, 0, 0, unit, unit, 0, 20 * unit, false);
        assertEquals(BoardStats.BAR * unit, wide.bar, 0.01f);
        assertEquals(1f, wide.k, 0f);
        // A little short: the bar gives way, the words keep their size.
        float need = wide.need;
        BoardStats.Parts tight = BoardStats.layout("bar", nw, hw, aw, 0, 0, unit, unit, 0, need - unit, false);
        assertTrue(tight.bar < wide.bar);
        assertEquals(1f, tight.k, 0f);
        // Far too short: the bar at its least, and all of it drawn smaller.
        BoardStats.Parts cramped = BoardStats.layout("bar", nw, hw, aw, 0, 0, unit, unit, 0, 2 * unit, false);
        assertEquals(BoardStats.BAR_LEAST * unit, cramped.bar, 0.01f);
        assertTrue(cramped.k < 1f);
    }

    @Test
    public void cardsRedLivesSlowlyAndItsShineComesNowAndThen() {
        // The slants a period along every six seconds, round again.
        assertEquals(0f, OverlayBug.drift(0), 0f);
        assertEquals(0.5f, OverlayBug.drift(3_000), 0.001f);
        assertEquals(0f, OverlayBug.drift(OverlayBug.DRIFT_MS), 0.001f);
        // The shine across it over 1.2 s, every nine seconds; not between.
        assertEquals(0f, OverlayBug.shineAt(0), 0f);
        assertEquals(0.5f, OverlayBug.shineAt(600), 0.001f);
        assertEquals(-1f, OverlayBug.shineAt(1_500), 0f);
        assertEquals(0.5f, OverlayBug.shineAt(OverlayBug.SHINE_EVERY_MS + 600), 0.001f);
    }
}
