package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** Match Day's last five: oldest first, as ESPN's "lastFiveGames" lists them (by date, the latest last). */
public class MatchDayFormTest {
    private static JSONArray events(String... results) throws Exception {
        JSONArray a = new JSONArray();
        int day = 1;
        for (String r : results) a.put(new JSONObject().put("gameDate", "2026-10-" + (10 + day++) + "T15:00Z").put("gameResult", r));
        return a;
    }

    @Test public void keepsEspnsOrderOldestFirst() throws Exception {
        // A real list (Manchester United, 18 Oct to 3 Nov): drawn, lost three, then won the latest.
        assertEquals("DLLLW", MatchDay.lastFive(events("D", "L", "L", "L", "W")));
    }

    @Test public void keepsTheLatestFiveWhenGivenMore() throws Exception {
        assertEquals("LDDWW", MatchDay.lastFive(events("W", "L", "D", "D", "W", "W")));
    }

    @Test public void skipsGamesWithoutAResultAndCopesWithNothing() throws Exception {
        assertEquals("WD", MatchDay.lastFive(events("w", "", "D")));
        assertEquals("", MatchDay.lastFive(null));
    }
}
