package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class RatingsTest {
    private static Map<String, Integer> stats(Object... kv) {
        Map<String, Integer> m = new HashMap<>();
        m.put("appearances", 1);
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    @Test public void aPlayerNotOnYetHasNoRating() {
        Map<String, Integer> bench = stats();
        bench.put("appearances", 0);
        assertEquals("", Ratings.of(bench, "SUB", 1, 0, 0.5f));
        assertEquals("", Ratings.of(null, "F", 1, 0, 0.5f));
    }

    @Test public void aQuietGameIsAboutSixAndAHalf() {
        assertEquals("6.4", Ratings.of(stats(), "CM-L", 0, 0, 0.5f));
    }

    @Test public void goalsAndAssistsLiftARating() {
        double scorer = Double.parseDouble(Ratings.of(stats("totalGoals", 2, "shotsOnTarget", 3, "goalAssists", 1), "CF-L", 3, 1, 1f));
        assertTrue(scorer >= 9.0);
        double defender = Double.parseDouble(Ratings.of(stats("totalGoals", 1, "shotsOnTarget", 1), "CD-R", 1, 0, 1f));
        double forward = Double.parseDouble(Ratings.of(stats("totalGoals", 1, "shotsOnTarget", 1), "F", 1, 0, 1f));
        // A defender's goal counts for more, and his clean sheet too.
        assertTrue(defender > forward);
    }

    @Test public void aKeepersSavesAndCleanSheetCount() {
        double busy = Double.parseDouble(Ratings.of(stats("saves", 6), "G", 0, 0, 1f));
        double beaten = Double.parseDouble(Ratings.of(stats("saves", 1, "goalsConceded", 4), "G", 0, 4, 1f));
        assertTrue(busy >= 8.0);
        assertTrue(beaten < 6.0);
    }

    @Test public void cardsAndOwnGoalsCost() {
        double sentOff = Double.parseDouble(Ratings.of(stats("redCards", 1, "yellowCards", 1), "M", 0, 1, 0.6f));
        assertTrue(sentOff < 5.0);
        assertTrue(Double.parseDouble(Ratings.of(stats("ownGoals", 1), "CD", 0, 1, 1f)) < 5.5);
    }

    @Test public void ratingsStayBetweenThreeAndTen() {
        assertEquals("10.0", Ratings.of(stats("totalGoals", 5, "goalAssists", 3, "shotsOnTarget", 7), "F", 8, 0, 1f));
        assertEquals("3.0", Ratings.of(stats("redCards", 1, "ownGoals", 3, "foulsCommitted", 6), "D", 0, 6, 1f));
    }

    @Test public void positionsAndColours() {
        assertEquals("G", Ratings.role("G"));
        assertEquals("D", Ratings.role("CD-L"));
        assertEquals("D", Ratings.role("RB"));
        assertEquals("M", Ratings.role("AM-R"));
        assertEquals("F", Ratings.role("CF-R"));
        assertEquals(0xFF2F7BEF, Ratings.colour("8.3"));
        assertEquals(0xFF1FA05A, Ratings.colour("7.0"));
        assertEquals(0xFFE59A12, Ratings.colour("6.4"));
        assertEquals(0xFFE5533D, Ratings.colour("5.2"));
    }
}
