package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** The match centre: the table as it stands with the live score, and when a pull closes the panel. */
public class MatchPanelTest {
    private static String[] row(String rank, String id, String name, String played, String gd, String pts, String w, String d, String l) {
        return new String[]{rank, id, name, "", played, gd, pts, "", w, d, l};
    }

    private static Game live(String homeId, int hs, String awayId, int as) {
        Game g = new Game();
        g.id = "1";
        g.state = Game.LIVE;
        g.homeId = homeId;
        g.awayId = awayId;
        g.homeScore = hs;
        g.awayScore = as;
        g.homeName = "Home";
        g.awayName = "Away";
        g.detail = "63'";
        return g;
    }

    @Test public void aLiveWinMovesAClubUp() {
        List<String[]> table = Arrays.asList(
                row("1", "a", "Alpha", "10", "+12", "24", "7", "3", "0"),
                row("2", "b", "Bravo", "10", "+8", "22", "7", "1", "2"),
                row("3", "c", "Charlie", "10", "+3", "21", "6", "3", "1"));
        List<String[]> now = MatchPanel.standing(table, live("c", 2, "a", 0));
        // Level on 24 points: Alpha stays top on goal difference, Charlie up to second, Bravo down to third.
        assertEquals("a", now.get(0)[1]);
        assertEquals("1", now.get(0)[0]);
        assertEquals("+10", now.get(0)[5]);
        assertEquals("1", now.get(0)[10]);
        assertEquals("c", now.get(1)[1]);
        assertEquals("2", now.get(1)[0]);
        assertEquals("24", now.get(1)[6]);
        assertEquals("+5", now.get(1)[5]);
        assertEquals("11", now.get(1)[4]);
        assertEquals("7", now.get(1)[8]);
        assertEquals("b", now.get(2)[1]);
        assertEquals("3", now.get(2)[0]);
        // The table given is left as it was.
        assertEquals("21", table.get(2)[6]);
    }

    @Test public void aLiveDrawGivesEachAPoint() {
        List<String[]> table = Arrays.asList(row("1", "a", "Alpha", "5", "0", "7", "2", "1", "2"), row("2", "b", "Bravo", "5", "-1", "7", "2", "1", "2"));
        List<String[]> now = MatchPanel.standing(table, live("a", 1, "b", 1));
        assertEquals("8", now.get(0)[6]);
        assertEquals("8", now.get(1)[6]);
        assertEquals("2", now.get(0)[9]);
        assertEquals("a", now.get(0)[1]);
    }

    @Test public void aPullClosesWhenFarOrFast() {
        assertTrue(MatchPanel.shouldClose(200, 0, 600, 16));
        assertTrue(MatchPanel.shouldClose(40, 2000, 600, 16));
        assertFalse(MatchPanel.shouldClose(100, 300, 600, 16));
        assertFalse(MatchPanel.shouldClose(8, 5000, 600, 16));
    }

    @Test public void theScoreLine() {
        // The clock in minutes and seconds (LiveClock), as everywhere a live score is shown.
        assertEquals("Home 2–1 Away · 63:00", MatchPanel.line(withScore(live("a", 2, "b", 1))));
        Game ht = live("a", 0, "b", 0);
        ht.halftime = true;
        assertEquals("Home 0–0 Away · HT", MatchPanel.line(ht));
    }

    private static Game withScore(Game g) {
        return g;
    }
}
