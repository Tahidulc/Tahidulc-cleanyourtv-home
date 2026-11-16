package com.gathin.homefieldtv.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;

/** The scoreboard's game as the picture shows it: readings held back, the clock counted on, the minutes added, when it's up. */
public class BoardClockTest {
    private static BoardClock.Reading live(long seen, int h, int a, double clock, String detail) {
        return new BoardClock.Reading(seen, h, a, Game.LIVE, false, clock, detail);
    }

    @Test public void readingsKeptInOrderWithTheirClocksFirstSight() {
        ArrayDeque<BoardClock.Reading> list = new ArrayDeque<>();
        BoardClock.add(list, live(1_000, 0, 0, 600, "11'"));
        BoardClock.Reading same = live(21_000, 0, 0, 600, "11'");
        BoardClock.add(list, same);
        // ESPN's clock not moved: counted on from when it was first seen, not stepping back.
        assertEquals(1_000, same.since);
        assertEquals(630.0, BoardClock.clockSecs(same, 31_000), 0.001);
        // One out of order is not kept.
        BoardClock.add(list, live(500, 0, 0, 0, "1'"));
        assertEquals(2, list.size());
        for (int i = 0; i < 30; i++) BoardClock.add(list, live(40_000 + i * 20_000L, 0, 0, 700 + i, "12'"));
        assertEquals(BoardClock.KEEP, list.size());
    }

    @Test public void theScreenShowsTheReadingItHasReached() {
        ArrayDeque<BoardClock.Reading> list = new ArrayDeque<>();
        BoardClock.Reading a = live(1_000, 0, 0, 60, "2'"), b = live(21_000, 1, 0, 80, "2'"), c = live(41_000, 1, 1, 100, "2'");
        BoardClock.add(list, a);
        BoardClock.add(list, b);
        BoardClock.add(list, c);
        assertSame(b, BoardClock.atScreen(list, 30_000));
        assertSame(c, BoardClock.atScreen(list, 60_000));
        // The channel hasn't reached even the first yet: the first.
        assertSame(a, BoardClock.atScreen(list, 500));
        assertNull(BoardClock.atScreen(new ArrayDeque<>(), 500));
    }

    @Test public void theClockRunsFromTheMinuteAndThroughAddedTime() {
        assertEquals(-1, BoardClock.clockSecs(new BoardClock.Reading(0, 0, 0, Game.PRE, false, 0, ""), 10_000), 0.001);
        assertEquals(-1, BoardClock.clockSecs(new BoardClock.Reading(0, 0, 0, Game.LIVE, true, 2700, "HT"), 10_000), 0.001);
        // Only the minute: it began a minute before.
        assertEquals(66 * 60 + 10, BoardClock.clockSecs(live(0, 0, 0, 0, "67'"), 10_000), 0.001);
        // In added time ESPN's clock stands at 45:00 while its minute goes on.
        assertEquals(46 * 60, BoardClock.clockSecs(live(0, 0, 0, 2700, "45'+2'"), 0), 0.001);
        assertEquals("67:23", BoardClock.clockWords(4043.7));
        assertEquals("92:15", BoardClock.clockWords(92 * 60 + 15));
    }

    @Test public void neverStepsBackByALittle() {
        assertEquals(100, BoardClock.steadied(100, -1), 0.001);
        assertEquals(105, BoardClock.steadied(100, 105), 0.001);
        assertEquals(110, BoardClock.steadied(110, 105), 0.001);
        // Half a minute or more ahead: a real correction.
        assertEquals(100, BoardClock.steadied(100, 140), 0.001);
        BoardClock.Steady s = new BoardClock.Steady();
        assertEquals(600, s.shown("g", "11'", 600, 0), 0.001);
        assertEquals(610, s.shown("g", "11'", 600, 10_000), 0.001);
    }

    @Test public void minutesAddedFromTheHalfsLastMinute() {
        int[] first = {3, 0, 0, 0}, second = {3, 4, 0, 0};
        assertEquals("", BoardClock.addedWords("43:59", "43'", first));
        assertEquals("+3", BoardClock.addedWords("44:10", "44'", first));
        assertEquals("+3", BoardClock.addedWords("46:30", "45'+1'", first));
        assertEquals("", BoardClock.addedWords("89:00", "89'", first));
        assertEquals("+4", BoardClock.addedWords("89:00", "89'", second));
        assertTrue(BoardClock.inAddedTime("45:10", "45'+1'"));
        assertFalse(BoardClock.inAddedTime("44:59", "44'"));
        assertTrue(BoardClock.inAddedTime("90:30", "90'+1'"));
        assertEquals(1, BoardClock.periodOf("46:30", "45'+1'"));
        assertEquals(2, BoardClock.periodOf("46:30", "46'"));
        assertEquals(47, BoardClock.minuteOf("45'+2'"));
        assertEquals(45, BoardClock.firstMinute("45'+2'"));
        assertEquals(67, BoardClock.minuteOfClock("67:23"));
        assertEquals(-1, BoardClock.minuteOfClock("HT"));
    }

    @Test public void goalsCodesAndTags() {
        assertEquals(2, BoardClock.goalSide("1–0", "1–1"));
        assertEquals(1, BoardClock.goalSide("0–0", "1–0"));
        assertEquals(0, BoardClock.goalSide("1–0", "1–0"));
        assertEquals(0, BoardClock.goalSide("v", "1–0"));
        assertEquals("ARS", BoardClock.code("ARS", "Arsenal"));
        assertEquals("BRI", BoardClock.code("", "Brighton & Hove Albion"));
        assertEquals("PL", BoardClock.tag("Premier League"));
        assertEquals("BUN", BoardClock.tag("Bundesliga"));
        assertEquals("UCL", BoardClock.tag("UEFA Champions League"));
    }

    @Test public void theClockSetAtKickOffOnTheScreen() {
        assertArrayEquals(new long[]{0, 5_000, 1}, BoardClock.anchorOf("123|0|5000|1", "123"));
        assertNull(BoardClock.anchorOf("999|0|5000", "123"));
        assertEquals("123|2700|5000|2", BoardClock.anchorWords("123", 2700, 5000, 2));
        long[] a = {0, 5_000, 1};
        assertEquals(60.0, BoardClock.anchoredSecs(a, 65_000, "1'"), 0.001);
        // Set in the first half: not the second half's clock.
        assertEquals(-1, BoardClock.anchoredSecs(a, 65_000, "46'"), 0.001);
        assertEquals(2760.0, BoardClock.anchoredSecs(new long[]{2700, 5_000, 2}, 65_000, "46'"), 0.001);
        // A step later: counted from five seconds later.
        assertEquals(10_000, BoardClock.moved(a, 5_000)[1]);
    }

    @Test public void upBeforeKickOffRestingAtHalfTimeGoneAfterFullTime() {
        long now = 1_000_000_000L;
        assertEquals(BoardClock.AWAY, BoardClock.phase(null, now + 30 * 60_000L, now, -1, -1, false));
        assertEquals(BoardClock.PRE, BoardClock.phase(null, now + 10 * 60_000L, now, -1, -1, false));
        assertEquals(BoardClock.UP, BoardClock.phase(live(now, 0, 0, 60, "2'"), now, now, -1, -1, false));
        BoardClock.Reading ht = new BoardClock.Reading(now, 1, 0, Game.LIVE, true, 2700, "HT");
        assertEquals(BoardClock.UP, BoardClock.phase(ht, 0, now, 10_000, -1, false));
        assertEquals(BoardClock.REST, BoardClock.phase(ht, 0, now, 25_000, -1, false));
        assertEquals(BoardClock.UP, BoardClock.phase(ht, 0, now, 25_000, -1, true));
        BoardClock.Reading ft = new BoardClock.Reading(now, 2, 1, Game.POST, false, 5400, "FT");
        assertEquals(BoardClock.UP, BoardClock.phase(ft, 0, now, -1, 30_000, false));
        assertEquals(BoardClock.REST, BoardClock.phase(ft, 0, now, -1, 60_000, false));
        assertEquals(BoardClock.AWAY, BoardClock.phase(ft, 0, now, -1, 11 * 60_000L, false));
    }

    @Test public void kindToTheScreen() {
        assertArrayEquals(new int[]{2, 0}, BoardClock.drift(0, 2));
        assertArrayEquals(new int[]{0, 2}, BoardClock.drift(2 * BoardClock.STEP_MS, 2));
        assertTrue(BoardClock.dimmed(6 * 60_000L));
        assertFalse(BoardClock.dimmed(4 * 60_000L));
    }
}
