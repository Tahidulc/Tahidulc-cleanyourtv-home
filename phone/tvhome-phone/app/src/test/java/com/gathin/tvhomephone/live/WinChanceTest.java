package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

/** Each side's chance of winning, and the draw's, under the scoreboard: never the prices, only the chances. */
public class WinChanceTest {
    private static double sum(double[] p) {
        double s = 0;
        for (double v : p) s += v;
        return s;
    }

    @Test public void goalsToComeAreACount() {
        assertEquals(1.0, sum(WinChance.pmf(1.4)), 1e-6);
        assertEquals(Math.exp(-1.4), WinChance.pmf(1.4)[0], 1e-9);
        assertEquals(1.0, WinChance.pmf(0)[0], 1e-9);
    }

    @Test public void evenSidesAreEven() {
        double[] c = WinChance.chances(1.3, 1.3, 0);
        assertEquals(1.0, sum(c), 1e-9);
        assertEquals(c[0], c[2], 1e-9);
        assertTrue(c[1] > 0.2 && c[1] < 0.35);
        // Nothing left to play: the score is the result.
        assertArrayEquals(new double[]{1, 0, 0}, WinChance.chances(0, 0, 1), 1e-9);
        assertArrayEquals(new double[]{0, 1, 0}, WinChance.chances(0, 0, 0), 1e-9);
    }

    @Test public void theRatesFitThePricesChances() {
        double[] want = WinChance.chances(2.1, 0.8, 0);
        double[] r = WinChance.rates(want);
        double[] got = WinChance.chances(r[0], r[1], 0);
        for (int i = 0; i < 3; i++) assertEquals(want[i], got[i], 0.01);
        assertTrue(r[0] > r[1]);
        // Without prices: a home side's usual.
        assertArrayEquals(new double[]{WinChance.HOME, WinChance.AWAY}, WinChance.rates(null), 1e-9);
    }

    @Test public void whatIsLeftOfTheGame() {
        assertEquals(1.0, WinChance.rest(0, 1, 0), 1e-9);
        assertEquals(60 / 90.0, WinChance.rest(30, 1, 0), 1e-9);
        // The first half's added time: the whole second half still to come.
        assertEquals(44 / 90.0, WinChance.rest(46, 1, 2), 1e-9);
        // The second half's: what is left of the minutes added (3 when not known), a minute at least.
        assertEquals(2 / 90.0, WinChance.rest(92, 2, 4), 1e-9);
        assertEquals(1 / 90.0, WinChance.rest(95, 2, 4), 1e-9);
        assertEquals(1 / 90.0, WinChance.rest(92, 2, 0), 1e-9);
        // Extra time goes to 120.
        assertEquals(20 / 90.0, WinChance.rest(100, 3, 0), 1e-9);
    }

    @Test public void aLateLeadIsAlmostAWin() {
        double[] r = {WinChance.HOME, WinChance.AWAY};
        double[] early = WinChance.now(r, 1, 0, WinChance.rest(10, 1, 0), 0, 0);
        double[] late = WinChance.now(r, 1, 0, WinChance.rest(88, 2, 0), 0, 0);
        assertTrue(late[0] > early[0]);
        assertTrue(late[0] > 0.9);
    }

    @Test public void aSendingOffCostsTheSide() {
        double[] r = {1.4, 1.4};
        double[] even = WinChance.now(r, 0, 0, 0.5, 0, 0);
        double[] tenMen = WinChance.now(r, 0, 0, 0.5, 1, 0);
        assertTrue(tenMen[0] < even[0]);
        assertTrue(tenMen[2] > even[2]);
    }

    @Test public void percentagesAddUpToAHundred() {
        int[] p = WinChance.percents(new double[]{1 / 3.0, 1 / 3.0, 1 / 3.0});
        assertEquals(100, p[0] + p[1] + p[2]);
        p = WinChance.percents(new double[]{0.3449, 0.2951, 0.36});
        assertArrayEquals(new int[]{34, 30, 36}, p);
        assertArrayEquals(new int[]{100, 0, 0}, WinChance.percents(new double[]{1, 0, 0}));
    }

    @Test public void aGoalSaysSoOnlyWhenItMovesTheChance() {
        assertTrue(WinChance.moved(34, 71));
        assertTrue(WinChance.moved(60, 55));
        assertFalse(WinChance.moved(91, 95));
        assertEquals("Everton 34% → 71%", WinChance.words("Everton", 34, 71));
    }

    private static Game game(int state, int home, int away) {
        Game g = new Game();
        g.id = "1";
        g.state = state;
        g.homeScore = home;
        g.awayScore = away;
        g.homeName = "Everton";
        g.awayName = "Arsenal";
        return g;
    }

    @Test public void theBoardsGameAsItShowsIt() {
        double[] r = {1.45, 1.15};
        // Before kick-off: the whole game to come, the home side ahead.
        double[] pre = ScoreBoard.chanceOf(game(Game.PRE, 0, 0), "", r, null);
        assertTrue(pre[0] > pre[2]);
        // A goal up at half time, and a goal up with a minute left.
        Game ht = game(Game.LIVE, 1, 0);
        ht.halftime = true;
        ht.period = 1;
        Game late = game(Game.LIVE, 1, 0);
        late.period = 2;
        assertTrue(ScoreBoard.chanceOf(late, "89:10", r, null)[0] > ScoreBoard.chanceOf(ht, "HT", r, null)[0]);
        // A red card for the home side.
        Game red = game(Game.LIVE, 0, 0);
        red.period = 1;
        double[] eleven = ScoreBoard.chanceOf(red, "30:00", r, null);
        red.reds.add(new String[]{"R|1|20|X", "X", "20'", "home"});
        assertTrue(ScoreBoard.chanceOf(red, "30:00", r, null)[0] < eleven[0]);
        // Over: the result.
        assertArrayEquals(new double[]{0, 0, 1}, ScoreBoard.chanceOf(game(Game.POST, 1, 2), "FT", r, null), 1e-9);
    }

    @Test public void aShootoutIsTheFifthPeriod() {
        Game g = game(Game.LIVE, 1, 1);
        g.period = 4;
        g.detail = "120'";
        assertFalse(ScoreBoard.inShootout(g));
        g.period = 5;
        assertTrue(ScoreBoard.inShootout(g));
        g.period = 0;
        g.detail = "Penalties";
        assertTrue(ScoreBoard.inShootout(g));
        g.state = Game.POST;
        assertFalse(ScoreBoard.inShootout(g));
    }

    @Test public void theGoalsPopOutSaysTheChanceMoving() {
        Game g = game(Game.LIVE, 1, 0);
        ScoreBoard.Drop d = ScoreBoard.chanceDrop(g, 1, 34, 71);
        assertEquals("WIN CHANCE", d.chip);
        assertEquals(ScoreBoard.WIN_CHANCE_BLUE, d.colour);
        assertEquals("Everton 34% → 71%", d.title);
        assertEquals(1, d.side);
        assertTrue(d.own);
        assertEquals("Arsenal 40% → 12%", ScoreBoard.chanceDrop(g, 2, 40, 12).title);
    }

    @Test public void noOddsOrBettingWordsAnywhere() {
        String[] shown = {ScoreBoard.chanceDrop(game(Game.LIVE, 1, 0), 1, 34, 71).chip, ScoreBoard.chanceDrop(game(Game.LIVE, 1, 0), 1, 34, 71).title};
        for (String s : shown) {
            String low = s.toLowerCase(java.util.Locale.ROOT);
            for (String bad : new String[]{"odds", "bet", "price", "bookie", "bookmaker", "wager", "stake"}) assertFalse(s, low.contains(bad));
        }
    }

    @Test public void theTestsChancesMoveWithItsGoal() {
        assertArrayEquals(ScoreBoard.TEST_BEFORE, ScoreBoard.testChance(0));
        assertArrayEquals(ScoreBoard.TEST_AFTER, ScoreBoard.testChance(ScoreBoard.TEST_GOAL_AT_MS));
        assertEquals(100, ScoreBoard.TEST_BEFORE[0] + ScoreBoard.TEST_BEFORE[1] + ScoreBoard.TEST_BEFORE[2]);
        assertEquals(100, ScoreBoard.TEST_AFTER[0] + ScoreBoard.TEST_AFTER[1] + ScoreBoard.TEST_AFTER[2]);
    }
}
