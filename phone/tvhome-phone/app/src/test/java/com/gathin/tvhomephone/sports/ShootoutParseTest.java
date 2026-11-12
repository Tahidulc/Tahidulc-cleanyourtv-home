package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

/** ESPN's summary: a penalty shootout's kicks, and the chances the prices before kick-off put on the game. */
public class ShootoutParseTest {
    @Test public void eachSidesKicksInOrder() throws Exception {
        JSONArray teams = new JSONArray("[{\"id\":\"368\",\"shots\":["
                + "{\"player\":\"Beto\",\"shotNumber\":3,\"didScore\":false},"
                + "{\"player\":\"Ndiaye\",\"shotNumber\":1,\"didScore\":true}]},"
                + "{\"id\":\"359\",\"shots\":[{\"player\":\"Saka\",\"shotNumber\":2,\"didScore\":true}]}]");
        Espn.Summary s = new Espn.Summary();
        Espn.shootout(teams, "368", s);
        assertEquals(2, s.homePens.size());
        assertArrayEquals(new String[]{"Ndiaye", "1"}, s.homePens.get(0));
        assertArrayEquals(new String[]{"Beto", "0"}, s.homePens.get(1));
        assertEquals(1, s.awayPens.size());
        assertArrayEquals(new String[]{"Saka", "1"}, s.awayPens.get(0));
        Espn.Summary none = new Espn.Summary();
        Espn.shootout(null, "368", none);
        assertTrue(none.homePens.isEmpty() && none.awayPens.isEmpty());
    }

    @Test public void thePricesAsChancesThatMakeOne() throws Exception {
        JSONArray odds = new JSONArray("[{\"homeTeamOdds\":{\"moneyLine\":150},\"awayTeamOdds\":{\"moneyLine\":180},"
                + "\"drawOdds\":{\"moneyLine\":230}}]");
        Espn.Summary s = new Espn.Summary();
        Espn.winChance(odds, s);
        assertEquals(1.0, s.chances[0] + s.chances[1] + s.chances[2], 1e-9);
        assertTrue(s.chances[0] > s.chances[2]);
        assertTrue(s.chances[2] > s.chances[1]);
        Espn.Summary none = new Espn.Summary();
        Espn.winChance(new JSONArray("[]"), none);
        assertNull(none.chances);
    }
}
