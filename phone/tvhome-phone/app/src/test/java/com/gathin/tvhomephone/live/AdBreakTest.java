package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The ad-break screen's arithmetic: when it comes up, form, fixtures, the table round a club, if it finished now, turns. */
public class AdBreakTest {
    private static String[] game(String date, String home, String hg, String away, String ag, String state) {
        return new String[]{"1", date, home, "H" + home, hg, away, "A" + away, ag, state};
    }

    @Test public void formIsThisCompetitionsGamesOldestFirst() {
        List<String[]> s = Arrays.asList(
                game("2026-08-22T14:00Z", "360", "2", "99", "0", "post"),   // won at home
                game("2026-08-30T14:00Z", "98", "1", "360", "1", "post"),   // drew away
                game("2026-09-06T14:00Z", "360", "0", "97", "3", "post"),   // lost at home
                game("2026-09-13T14:00Z", "96", "0", "360", "2", "post"),   // won away
                game("2026-09-20T14:00Z", "360", "", "95", "", "pre"));     // still to come
        assertEquals("WDLW", AdBreak.form(s, "360", 5));
        assertEquals("LW", AdBreak.form(s, "360", 2));
        assertEquals("", AdBreak.form(s, "1234", 5));
        assertEquals("", AdBreak.form(null, "360", 5));
    }

    @Test public void fixturesAreTheNextGamesStillToCome() {
        long now = AdBreak.when("2026-09-01T00:00Z");
        List<String[]> s = Arrays.asList(
                game("2026-08-22T14:00Z", "360", "2", "99", "0", "post"),
                game("2026-09-06T14:00Z", "360", "", "97", "", "pre"),
                game("2026-09-13T14:00Z", "96", "", "360", "", "pre"));
        List<String[]> f = AdBreak.fixtures(s, "360", now, 3);
        assertEquals(2, f.size());
        assertEquals("97", f.get(0)[0]);
        assertEquals("H", f.get(0)[2]);
        assertEquals("96", f.get(1)[0]);
        assertEquals("A", f.get(1)[2]);
    }

    private static List<String[]> table() {
        List<String[]> t = new ArrayList<>();
        String[][] rows = {{"1", "a", "30", "+20"}, {"2", "b", "27", "+12"}, {"3", "c", "25", "+8"}, {"4", "mu", "24", "+9"},
                {"5", "d", "23", "+6"}, {"6", "e", "21", "+4"}, {"12", "ev", "15", "-3"}};
        for (String[] r : rows) t.add(new String[]{r[0], r[1], r[1].toUpperCase(), "", "12", r[3], r[2], "", "", "", ""});
        return t;
    }

    @Test public void theTableAroundAClubKeepsItInView() {
        List<String[]> around = AdBreak.around(table(), "mu", 4);
        assertEquals(4, around.size());
        assertEquals("b", around.get(0)[AdBreak.T_ID]);
        assertEquals("mu", around.get(2)[AdBreak.T_ID]);
        // At the top or the bottom it still shows four.
        assertEquals("a", AdBreak.around(table(), "a", 4).get(0)[AdBreak.T_ID]);
        assertEquals("ev", AdBreak.around(table(), "ev", 4).get(3)[AdBreak.T_ID]);
        assertTrue(AdBreak.around(table(), "zz", 4).isEmpty());
    }

    @Test public void ifItFinishedNowMovesTheClubsByPointsThenGoalDifference() {
        int[][] f = AdBreak.ifFinished(table(), "mu", "ev");
        // A home win: 27 points, +10 — level with b on points, behind on goal difference: 3rd.
        assertEquals(3, f[0][0]);
        // A draw: 25 points, +9 — level with c on points, ahead on goal difference: 3rd as well.
        assertEquals(3, f[0][1]);
        // An away win: 24 points, +8 — d stays below: 4th.
        assertEquals(4, f[0][2]);
        // Everton: 12th, 12th, and up on 18 points ahead of nobody else here.
        assertEquals(7, f[1][0]);
        assertEquals(7, f[1][2]);
    }

    @Test public void ordinals() {
        assertEquals("1st", AdBreak.ordinal(1));
        assertEquals("2nd", AdBreak.ordinal(2));
        assertEquals("3rd", AdBreak.ordinal(3));
        assertEquals("4th", AdBreak.ordinal(4));
        assertEquals("11th", AdBreak.ordinal(11));
        assertEquals("12th", AdBreak.ordinal(12));
        assertEquals("22nd", AdBreak.ordinal(22));
    }

    @Test public void itComesUpOnlyInTheHourBeforeKickOffDuringTheAdverts() {
        long now = 1_000_000_000L;
        Game g = new Game();
        g.state = Game.PRE;
        g.kickoff = now + 40 * 60_000L;
        assertTrue(AdBreak.wanted(true, true, g, now));
        assertFalse(AdBreak.wanted(false, true, g, now));   // switched off
        assertFalse(AdBreak.wanted(true, false, g, now));   // no adverts
        g.kickoff = now + 90 * 60_000L;
        assertFalse(AdBreak.wanted(true, true, g, now));    // too early
        g.kickoff = now - 1000;
        assertFalse(AdBreak.wanted(true, true, g, now));    // kicked off
        g.kickoff = now + 10 * 60_000L;
        g.state = Game.LIVE;
        assertFalse(AdBreak.wanted(true, true, g, now));    // in play
        assertFalse(AdBreak.wanted(true, true, null, now));
    }

    @Test public void countdownToKickOff() {
        assertEquals("12:40", AdBreak.countdown(760_000L, 0));
        assertEquals("1:02:05", AdBreak.countdown(3_725_000L, 0));
        assertEquals("", AdBreak.countdown(0, 5));
    }

    @Test public void theCardsTakeTurnsOneAfterAnother() {
        assertEquals(12_000L, AdBreak.firstTurn(0, 3));
        assertEquals(16_000L, AdBreak.firstTurn(1, 3));
        assertEquals(20_000L, AdBreak.firstTurn(2, 3));
        assertEquals(18_000L, AdBreak.firstTurn(1, 2));
        assertEquals(1, AdBreak.next(0, 8, 1));
        assertEquals(0, AdBreak.next(7, 8, 1));
        assertEquals(7, AdBreak.next(0, 8, -1));
        assertEquals(0, AdBreak.next(0, 0, 1));
    }
}
