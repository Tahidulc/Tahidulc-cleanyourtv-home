package com.gathin.tvhomephone.live;

import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * 4.19: added time. The board's controller decides it (the clock past its period's end, the period ESPN's own or its
 * minute's first number), every look's score comes alive in it, and the minutes added stand in a tab beside the board
 * unless it is kept too narrow for one.
 */
public class AddedTimeTest {
    private static Game live(int period, String espnMinute) {
        Game g = new Game();
        g.home = "ARS";
        g.away = "EVE";
        g.state = Game.LIVE;
        g.period = period;
        g.displayClock = espnMinute;
        g.detail = espnMinute;
        return g;
    }

    @Test
    public void addedTimeIsTheClockPastItsPeriodsEnd() {
        // The first half: from 45:00 on.
        assertFalse(ScoreBoard.stoppage(live(1, "45'"), "44:59"));
        assertTrue(ScoreBoard.stoppage(live(1, "45'+1'"), "45:00"));
        assertTrue(ScoreBoard.stoppage(live(1, "45'+2'"), "47:10"));
        // The second half begins at 45:00: not added time; from 90:00 it is.
        assertFalse(ScoreBoard.stoppage(live(2, "46'"), "45:30"));
        assertFalse(ScoreBoard.stoppage(live(2, "90'"), "89:59"));
        assertTrue(ScoreBoard.stoppage(live(2, "90'+3'"), "92:15"));
        // Extra time's two halves.
        assertFalse(ScoreBoard.stoppage(live(3, "95'"), "94:00"));
        assertTrue(ScoreBoard.stoppage(live(3, "105'+1'"), "105:20"));
        assertTrue(ScoreBoard.stoppage(live(4, "120'+2'"), "121:05"));
    }

    @Test
    public void withoutItsOwnPeriodTheMinutesFirstNumberSaysWhichHalf() {
        // "90'+3'" is the second half's (90), not the 93rd minute's (extra time's first half).
        assertEquals(2, ScoreBoard.period(live(0, "90'+3'"), "92:15"));
        assertTrue(ScoreBoard.stoppage(live(0, "90'+3'"), "92:15"));
        // "45'+2'" the first half's: 46:30 is its added time, not the second half's start.
        assertEquals(1, ScoreBoard.period(live(0, "45'+2'"), "46:30"));
        assertTrue(ScoreBoard.stoppage(live(0, "45'+2'"), "46:30"));
        assertEquals(2, ScoreBoard.period(live(0, "46'"), "45:30"));
        assertFalse(ScoreBoard.stoppage(live(0, "46'"), "45:30"));
        assertEquals(3, ScoreBoard.period(live(0, "105'+1'"), "105:30"));
        assertEquals(4, ScoreBoard.period(live(0, "106'"), "105:30"));
        // ESPN's own period first.
        assertEquals(2, ScoreBoard.period(live(2, "45'"), "45:05"));
        // Knowing neither: the clock's own minute.
        assertEquals(2, ScoreBoard.period(live(0, ""), "67:23"));
        assertEquals(90, ScoreBoard.periodEnd(2));
        assertEquals(120, ScoreBoard.periodEnd(4));
    }

    @Test
    public void notAddedTimeWhenTheGameIsNotRunning() {
        Game g = live(1, "45'+2'");
        g.halftime = true;
        assertFalse(ScoreBoard.stoppage(g, "47:00"));
        Game ft = live(2, "FT");
        ft.state = Game.POST;
        assertFalse(ScoreBoard.stoppage(ft, "95:00"));
        Game pens = live(5, "120'");
        assertFalse(ScoreBoard.stoppage(pens, "120:00"));
        assertFalse(ScoreBoard.stoppage(live(2, "90'+3'"), "HT"));
        assertFalse(ScoreBoard.stoppage(null, "92:00"));
    }

    @Test
    public void theBoardsLookCarriesIt() {
        Game g = live(2, "90'+3'");
        OverlayBug.Look in = ScoreBoard.look(g, new String[]{"92:15", "+4"});
        assertTrue(in.stoppage);
        assertEquals("+4", in.added);
        OverlayBug.Look before = ScoreBoard.look(live(2, "89'"), new String[]{"88:40", ""});
        assertFalse(before.stoppage);
        // The same words in and out of added time are not the same look: it is drawn again as it begins.
        OverlayBug.Look a = ScoreBoard.look(g, new String[]{"92:15", ""});
        OverlayBug.Look b = ScoreBoard.look(g, new String[]{"92:15", ""});
        assertEquals(a, b);
        b.stoppage = false;
        assertNotEquals(a, b);
    }

    @Test
    public void theTabGoesBesideTheBoardUnlessItIsTooNarrow() {
        int h = 60, room = 70;
        // Its own part at least twice its height: beside it.
        assertTrue(OverlayBug.outside(2 * h, room, h));
        assertTrue(OverlayBug.outside(400, room, h));
        // Narrower: back inside, beside the clock.
        assertFalse(OverlayBug.outside(2 * h - 1, room, h));
        // No minutes added: no tab.
        assertFalse(OverlayBug.outside(400, 0, h));
    }

    @Test
    public void theTabIsWideEnoughForItsMinutes() {
        float th = 0.8f * 30;
        // "+4": never narrower than 1.15 of its height.
        assertEquals(th * 1.15f, OverlayBug.tabWidth(th, th * 0.4f), 0.001f);
        // "+10" at its size: its words and half its height round them.
        assertEquals(th * 1.3f + th * 0.5f, OverlayBug.tabWidth(th, th * 1.3f), 0.001f);
    }

    @Test
    public void theShineComesAsAddedTimeBeginsAndEveryNineSeconds() {
        // Counted from added time's start: sweeping at once, across in SHINE_MS, then not until SHINE_EVERY_MS.
        assertEquals(0f, OverlayBug.shineAt(0), 0f);
        assertTrue(OverlayBug.shineAt(OverlayBug.SHINE_MS - 1) > 0.99f);
        assertEquals(-1f, OverlayBug.shineAt(OverlayBug.SHINE_MS + 1), 0f);
        assertEquals(-1f, OverlayBug.shineAt(OverlayBug.SHINE_EVERY_MS - 1), 0f);
        assertEquals(0f, OverlayBug.shineAt(OverlayBug.SHINE_EVERY_MS), 0f);
        // The slants a period along every DRIFT_MS, the same on every look.
        assertEquals(0.25f, OverlayBug.drift(OverlayBug.DRIFT_MS / 4), 0.001f);
    }
}
