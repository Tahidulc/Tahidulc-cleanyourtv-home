package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LiveTableTest {
    @Test public void aWinMovesAClubUp() {
        List<String[]> table = Arrays.asList(
                new String[]{"1", "a", "A", "", "10", "+5", "20", ""},
                new String[]{"2", "b", "B", "", "10", "+3", "19", ""},
                new String[]{"3", "c", "C", "", "10", "+1", "18", ""});
        Game g = new Game();
        g.state = Game.LIVE;
        g.homeId = "c";
        g.awayId = "a";
        g.homeScore = 2;
        g.awayScore = 0;
        Map<String, int[]> p = LiveTable.positions(table, Collections.singletonList(g));
        assertArrayEquals(new int[]{3, 1}, p.get("c"));
        assertArrayEquals(new int[]{1, 2}, p.get("a"));
        assertArrayEquals(new int[]{2, 3}, p.get("b"));
    }

    @Test public void ordinals() {
        assertEquals("1st", LiveTable.ordinal(1));
        assertEquals("12th", LiveTable.ordinal(12));
        assertEquals("22nd", LiveTable.ordinal(22));
        assertEquals("13th", LiveTable.ordinal(13));
    }
}
