package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.Moments;

import org.junit.Test;

/**
 * The moments' pop-ups as the board brings them (ClassicIntro.tick from BoardCover's reading): full time once, with the
 * final score, as the board's own full time reaches the screen (BoardCover.over: the whistle on the picture); after a goal
 * the score alone. A made-up game (Northfield v Riverside).
 */
public class MomentPopupsTest {
    private static ScoresTicker.Game game() {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "g-nor-riv";
        g.home = "NOR";
        g.away = "RIV";
        g.homeName = "Northfield";
        g.awayName = "Riverside";
        return g;
    }

    /** As tick follows it: the phase the board's reading gives after was, and the pop-up that change brings. */
    private static String[] step(String was, BoardCover.Reading r) {
        String now = Moments.phase(false, r.state, r.halftime, was, 2);
        return new String[]{now, was == null ? null : Moments.moment(was, now)};
    }

    @Test
    public void fullTimeComesOnceAsTheBoardsFullTimeReachesTheScreen() {
        ScoresTicker.Game g = game();
        // the second half in its added time on the board, then its full time as the whistle reaches the picture
        BoardCover.Reading live = new BoardCover.Reading(1_000_000L, "2", "1", 0, false, 5580, "93'");
        String[] s = step("h2", live);
        assertEquals("h2", s[0]);
        assertNull(s[1]);
        BoardCover.Reading ft = BoardCover.over(live, true);
        s = step(s[0], ft);
        assertEquals("ft", s[0]);
        assertEquals("full", s[1]);
        // its score: the final score as the board shows it at full time; the board away while it is on
        BoardBug.Look l = BoardCover.look(g, ft, 1_000_500L);
        assertEquals("FT", l.clock);
        int[] score = ClassicIntro.momentScore("full", l.score);
        assertArrayEquals(new int[]{2, 1}, score);
        assertEquals("g-nor-riv|full|2-1", ClassicIntro.popupKey(g.id, "full", score));
        assertTrue(ClassicIntro.moment("full"));
        // once: the board stays at full time, nothing more comes
        assertNull(step(s[0], ft)[1]);
        // half time is not full time
        assertNull(step("h1", BoardCover.over(live, false))[1]);
    }

    @Test
    public void eachMomentIsKnownByItsGameKindAndScore() {
        assertNull(ClassicIntro.momentScore("kickoff", "0–0"));
        assertArrayEquals(new int[]{1, 1}, ClassicIntro.momentScore("second", "1–1"));
        assertArrayEquals(new int[]{3, 2}, ClassicIntro.momentScore("restart", "3–2"));
        assertEquals("g1|kickoff", ClassicIntro.popupKey("g1", "kickoff", null));
        assertEquals("g1|second|1-1", ClassicIntro.popupKey("g1", "second", new int[]{1, 1}));
        // a second goal's pop-up is its own; full time at that score is apart from it
        assertNotEquals(ClassicIntro.popupKey("g1", "restart", new int[]{2, 1}), ClassicIntro.popupKey("g1", "full", new int[]{2, 1}));
        for (String k : Moments.KINDS) assertTrue(k, ClassicIntro.moment(k));
    }
}
