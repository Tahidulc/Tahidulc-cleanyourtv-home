package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The shootout under the scoreboard: its dots, its tally, its kicks in step with the picture, its test. */
public class ShootoutTest {
    private static List<Boolean> k(Boolean... b) {
        return new ArrayList<>(Arrays.asList(b));
    }

    @Test public void espnsKicksAreScoredOrNot() {
        List<String[]> pens = new ArrayList<>();
        pens.add(new String[]{"A. Taker", "1"});
        pens.add(new String[]{"B. Taker", "0"});
        pens.add(new String[]{"C. Taker", "1"});
        assertEquals(k(true, false, true), Shootout.kicks(pens));
        assertTrue(Shootout.kicks(null).isEmpty());
    }

    @Test public void fiveDotsEachThenMoreForSuddenDeath() {
        int[][] d = Shootout.dots(k(true, false), k(true));
        assertEquals(5, d[0].length);
        assertEquals(5, d[1].length);
        assertArrayEquals(new int[]{Shootout.SCORED, Shootout.MISSED, Shootout.TO_COME, Shootout.TO_COME, Shootout.TO_COME}, d[0]);
        assertArrayEquals(new int[]{Shootout.SCORED, Shootout.TO_COME, Shootout.TO_COME, Shootout.TO_COME, Shootout.TO_COME}, d[1]);
        // Sudden death: both sides' rows as long as the longest.
        int[][] sd = Shootout.dots(k(true, true, true, true, true, true, true), k(true, true, true, true, true, true));
        assertEquals(7, sd[0].length);
        assertEquals(7, sd[1].length);
        assertEquals(Shootout.TO_COME, sd[1][6]);
    }

    @Test public void theTallyCountsTheScored() {
        assertEquals(3, Shootout.scored(k(true, false, true, true)));
        assertEquals(0, Shootout.scored(k()));
    }

    @Test public void kicksAreTakenInTurn() {
        assertArrayEquals(new int[]{0, 0}, Shootout.shownSplit(0, true, 5, 5));
        assertArrayEquals(new int[]{1, 0}, Shootout.shownSplit(1, true, 5, 5));
        assertArrayEquals(new int[]{1, 1}, Shootout.shownSplit(2, true, 5, 5));
        assertArrayEquals(new int[]{2, 1}, Shootout.shownSplit(3, true, 5, 5));
        assertArrayEquals(new int[]{0, 1}, Shootout.shownSplit(1, false, 5, 5));
        // One side done early: the other's go on.
        assertArrayEquals(new int[]{2, 4}, Shootout.shownSplit(6, true, 2, 5));
    }

    @Test public void newKicksKeepWhenTheyCameAndReadsPutThemRight() {
        List<Boolean> have = new ArrayList<>();
        List<Long> at = new ArrayList<>();
        Shootout.arrived(have, at, k(true), 1_000L);
        Shootout.arrived(have, at, k(true, false), 11_000L);
        assertEquals(k(true, false), have);
        assertEquals(Arrays.asList(1_000L, 11_000L), at);
        // ESPN corrects the first: put right, its time kept.
        Shootout.arrived(have, at, k(false, false), 21_000L);
        assertEquals(k(false, false), have);
        assertEquals(Arrays.asList(1_000L, 11_000L), at);
    }

    @Test public void thePictureShowsEachKickTheStreamsDelayLater() {
        List<Boolean> have = k(true, false, true);
        List<Long> at = Arrays.asList(1_000L, 50_000L, 90_000L);
        long behind = 30_000L;
        assertEquals(k(), Shootout.shown(have, at, 20_000L - behind));
        assertEquals(k(true), Shootout.shown(have, at, 40_000L - behind));
        assertEquals(k(true, false), Shootout.shown(have, at, 80_000L - behind));
        assertEquals(have, Shootout.shown(have, at, 200_000L));
    }

    @Test public void theTestGoesIntoSuddenDeathAndTheHomeSideWins() {
        List<Boolean>[] none = Shootout.test(1_000L);
        assertTrue(none[0].isEmpty() && none[1].isEmpty());
        List<Boolean>[] first = Shootout.test(Shootout.TEST_EVERY_MS);
        assertEquals(1, first[0].size());
        assertEquals(0, first[1].size());
        List<Boolean>[] all = Shootout.test(60_000L);
        assertEquals(6, all[0].size());
        assertEquals(6, all[1].size());
        assertEquals(5, Shootout.scored(all[0]));
        assertEquals(4, Shootout.scored(all[1]));
        // Level after five each: it went to sudden death.
        assertEquals(Shootout.scored(all[0].subList(0, 5)), Shootout.scored(all[1].subList(0, 5)));
        assertEquals(6, Shootout.dots(all[0], all[1])[0].length);
    }

    @Test public void theStripIsSmallUnderTheBoard() {
        // The shootout's two rows a board high at most; nothing when it shows nothing.
        assertTrue(BoardUnder.heightFor(BoardUnder.PENS, 40f) <= 40);
        assertTrue(BoardUnder.heightFor(BoardUnder.PENS, 40f) >= 32);
        assertEquals(0, BoardUnder.heightFor(BoardUnder.NONE, 40f));
        // Each sudden-death kick a dot wider.
        assertTrue(BoardUnder.pensWidth(40f, 6) > BoardUnder.pensWidth(40f, 5));
        assertTrue(BoardUnder.pensWidth(40f, 5) < 40f * 5);
    }

    @Test public void clubsOfOneColourAreToldApart() {
        assertTrue(BoardUnder.near(0xFFC8102E, 0xFFD0142F));
        assertTrue(!BoardUnder.near(0xFFC8102E, 0xFF1B458F));
    }

    @Test public void aShootoutIsTheFifthPeriod() {
        Game g = new Game();
        g.id = "1";
        g.state = Game.LIVE;
        g.homeScore = 1;
        g.awayScore = 1;
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
}
