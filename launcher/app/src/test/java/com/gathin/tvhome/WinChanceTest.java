package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.json.JSONObject;
import org.junit.Test;

public class WinChanceTest {
    @Test
    public void pricesBecomeChances() throws Exception {
        assertEquals(100.0 / 420.0, WinChance.implied(320), 1e-9);
        assertEquals(125.0 / 225.0, WinChance.implied(-125), 1e-9);
        double[] p = WinChance.fromPrices(-125, 260, 320);
        assertNotNull(p);
        assertEquals(1.0, p[0] + p[1] + p[2], 1e-9);
        assertTrue(p[0] > p[2] && p[2] > 0);
        assertNull(WinChance.fromPrices(-125, Double.NaN, 320));
    }

    @Test
    public void theSummarysPricesAreReadTheOddsFirstThenPickcenter() throws Exception {
        JSONObject s = new JSONObject("{\"odds\":[{\"homeTeamOdds\":{\"moneyLine\":-150},\"awayTeamOdds\":{\"moneyLine\":400},"
                + "\"drawOdds\":{\"moneyLine\":280}}]}");
        double[] p = WinChance.fromSummary(s);
        assertNotNull(p);
        assertTrue(p[0] > 0.5);
        JSONObject pc = new JSONObject("{\"odds\":[{}],\"pickcenter\":[{\"homeTeamOdds\":{\"moneyLine\":300},\"awayTeamOdds\":{\"moneyLine\":-110},"
                + "\"drawOdds\":{\"moneyLine\":250}}]}");
        assertTrue(WinChance.fromSummary(pc)[2] > WinChance.fromSummary(pc)[0]);
        assertNull(WinChance.fromSummary(new JSONObject("{}")));
    }

    @Test
    public void theRatesFittedGiveBackTheChances() throws Exception {
        double[] before = {0.50, 0.26, 0.24};
        double[] r = WinChance.fit(before);
        double[] p = WinChance.outcome(r[0], r[1], 0);
        for (int i = 0; i < 3; i++) assertEquals(before[i], p[i], 0.02);
        assertArrayEquals(new double[]{WinChance.HOME_RATE, WinChance.AWAY_RATE}, WinChance.fit(null), 1e-9);
    }

    @Test
    public void theTimeLeftComesDownToTheMinutesAdded() throws Exception {
        assertEquals(1.0, WinChance.left(0, false, 0), 1e-9);
        assertEquals(0.5, WinChance.left(45, false, 0), 1e-9);
        assertEquals(30.0 / 90, WinChance.left(90, true, 0), 1e-9);
        // In the 92nd minute with four added: two left.
        assertEquals(2.0 / 90, WinChance.left(92, false, 4), 1e-9);
        // Past them, or none put up: a minute.
        assertEquals(1.0 / 90, WinChance.left(96, false, 4), 1e-9);
        assertEquals(1.0 / 90, WinChance.left(93, false, 0), 1e-9);
    }

    @Test
    public void aGoalAndTheClockMoveTheChances() throws Exception {
        double[] r = {1.4, 1.1};
        double[] start = WinChance.now(r, 1.0, 0, 0, 0), ahead = WinChance.now(r, 0.5, 1, 0, 0), late = WinChance.now(r, 0.05, 1, 0, 0);
        assertTrue(ahead[0] > start[0]);
        assertTrue(late[0] > ahead[0]);
        assertTrue(late[0] > 0.85);
        // None left: as it stands.
        assertArrayEquals(new double[]{0, 1, 0}, WinChance.now(r, 0, 0, 0, 0), 1e-9);
        assertArrayEquals(new double[]{0, 0, 1}, WinChance.now(r, 0, -2, 0, 0), 1e-9);
        // A red card for the home side: their chance down.
        assertTrue(WinChance.now(r, 0.6, 0, 1, 0)[0] < WinChance.now(r, 0.6, 0, 0, 0)[0]);
        assertTrue(WinChance.now(r, 0.6, 0, 0, 1)[0] > WinChance.now(r, 0.6, 0, 0, 0)[0]);
    }

    @Test
    public void percentagesAlwaysMakeAHundred() throws Exception {
        int[] p = WinChance.percents(new double[]{1 / 3.0, 1 / 3.0, 1 / 3.0});
        assertEquals(100, p[0] + p[1] + p[2]);
        assertArrayEquals(new int[]{45, 27, 28}, WinChance.percents(new double[]{0.4512, 0.2701, 0.2787}));
        assertArrayEquals(new int[]{100, 0, 0}, WinChance.percents(new double[]{1, 0, 0}));
    }

    @Test
    public void aGoalsAlertSaysHowFarItMovedNeverWithBettingWords() throws Exception {
        assertEquals("Everton 34% → 71%", WinChance.alertLine("Everton", 34, 71));
        assertNull(WinChance.alertLine("Everton", 90, 94));
        assertEquals("Spurs 50% → 20%", WinChance.alertLine("Spurs", 50, 20));
        String l = WinChance.alertLine("Everton", 34, 71).toLowerCase();
        for (String w : new String[]{"odds", "bet", "price", "bookmaker", "stake"}) assertTrue(w, !l.contains(w));
    }

    @Test
    public void redCardsCountOnceTheClockHasReachedThem() throws Exception {
        java.util.List<String[]> reds = Arrays.asList(new String[]{"R|1", "A", "34'", "home", ""},
                new String[]{"R|2", "B", "45'+2'", "away", ""}, new String[]{"R|3", "C", "80'", "home", ""});
        assertEquals(0, WinChance.reds(reds, "home", 30));
        assertEquals(1, WinChance.reds(reds, "home", 60));
        assertEquals(0, WinChance.reds(reds, "away", 46));
        assertEquals(1, WinChance.reds(reds, "away", 47));
        assertEquals(2, WinChance.reds(reds, "home", 999));
        assertEquals(47.0, WinChance.minuteOf("45'+2'"), 1e-9);
        assertEquals(67.0, WinChance.minuteOf("67'"), 1e-9);
    }

    @Test
    public void theClocksSeconds() throws Exception {
        assertEquals(23, BoardCover.secondsOfClock("67:23"));
        assertEquals(0, BoardCover.secondsOfClock("HT"));
    }
}
