package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.Moments;
import com.gathin.homefieldtv.sports.BoardClock;
import com.gathin.homefieldtv.sports.BoardTime;
import com.gathin.homefieldtv.sports.Game;
import com.gathin.homefieldtv.sports.GameClock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Homefield TV's full-time pop-up (BoardLayer.moments): once, with the final score, as the board's own full time reaches
 * the picture (BoardTime: Whistle, never early), in place of its FULL TIME alert; with the pop-ups off, the alert as before.
 * A made-up second half (Northfield v Riverside, 2-1) replayed second by second: ESPN has full time at once, the channel's
 * picture a minute behind.
 */
public class FullTimePopupTest {
    private static final long K = 1_800_000_000_000L;
    private static final String ID = "770002";
    /** The whistle on the real pitch, in the game's seconds (95:30). */
    private static final long WHISTLE = 5730;

    /** ESPN's read of the second half at game second s. */
    private static Game espn(double s) {
        Game g = new Game();
        g.id = ID;
        g.home = "NOR";
        g.away = "RIV";
        g.homeName = "Northfield";
        g.awayName = "Riverside";
        g.homeScore = 2;
        g.awayScore = 1;
        g.period = 2;
        g.state = Game.LIVE;
        if (s >= WHISTLE) {
            g.state = Game.POST;
            g.detail = "FT";
            return g;
        }
        int m = (int) (s / 60) + 1;
        g.displayClock = m <= 90 ? m + "'" : "90'+" + (m - 90) + "'";
        g.detail = g.displayClock;
        g.clock = GameClock.minuteStart(m * 60.0, g.displayClock);
        return g;
    }

    /** The half replayed, the board's moments as they come: {wall time, pop-up, alert} each. */
    private static List<Object[]> replay(boolean popups) {
        BoardTime time = new BoardTime();
        time.added(ID, new int[]{0, 5, 0, 0});
        List<Object[]> out = new ArrayList<>();
        String was = "h2";
        for (long w = K + (5000 - 2700) * 1000L; w <= K + (6000 - 2700) * 1000L; w += 1000L) {
            double game = 2700 + (w - K) / 1000.0;
            if ((w - K) % 20_000L == 0) time.took(espn(game), w);
            BoardTime.Shown at = time.at(ID, w, 60_000L);
            BoardClock.Reading r = at.r;
            if (r == null) continue;
            String ph = Moments.phase(false, r.state, r.halftime, was, BoardClock.periodOfDetail(r.detail));
            String kind = ph.isEmpty() ? null : Moments.moment(was, ph);
            String[] o = BoardLayer.momentOut(was, ph, kind, popups);
            if (o[0] != null || o[1] != null) out.add(new Object[]{w, o[0], o[1], r});
            if (!ph.isEmpty()) was = ph;
        }
        return out;
    }

    @Test
    public void fullTimesPopUpComesOnceAsTheBoardsFullTimeReachesThePicture() {
        List<Object[]> got = replay(true);
        assertEquals(1, got.size());
        Object[] m = got.get(0);
        assertEquals("full", m[1]);
        // its pop-up says it: no FULL TIME alert beside the board as well
        assertEquals(null, m[2]);
        // as the whistle is on the picture (a minute behind the pitch), a read later at most, never before
        double onPicture = ((Long) m[0] - K) / 1000.0 + 2700 - 60;
        assertTrue("full time at " + onPicture, onPicture >= WHISTLE && onPicture <= WHISTLE + 20);
        // the final score as the board has it
        BoardClock.Reading r = (BoardClock.Reading) m[3];
        assertEquals(Game.POST, r.state);
        assertArrayEquals(new int[]{2, 1}, new int[]{r.home, r.away});
        assertTrue(Moments.boardMoment("full"));
    }

    @Test
    public void withThePopUpsOffFullTimeIsItsAlertAsBefore() {
        List<Object[]> got = replay(false);
        assertEquals(1, got.size());
        assertEquals(null, got.get(0)[1]);
        assertEquals("FULL TIME", got.get(0)[2]);
    }

    @Test
    public void eachChangeOfPhaseBringsItsOwn() {
        assertArrayEquals(new String[]{"kickoff", null}, BoardLayer.momentOut("pre", "h1", "kickoff", true));
        assertArrayEquals(new String[]{null, "KICK-OFF"}, BoardLayer.momentOut("pre", "h1", "kickoff", false));
        assertArrayEquals(new String[]{null, "HALF TIME"}, BoardLayer.momentOut("h1", "ht", null, true));
        assertArrayEquals(new String[]{"second", null}, BoardLayer.momentOut("ht", "h2", "second", true));
        assertArrayEquals(new String[]{null, "SECOND HALF"}, BoardLayer.momentOut("ht", "h2", "second", false));
        assertArrayEquals(new String[]{"full", null}, BoardLayer.momentOut("h2", "ft", "full", true));
        assertArrayEquals(new String[]{null, "FULL TIME"}, BoardLayer.momentOut("h2", "ft", "full", false));
        // full time after half time (no second half seen): its alert, no pop-up
        assertArrayEquals(new String[]{null, "FULL TIME"}, BoardLayer.momentOut("ht", "ft", null, true));
        // nothing new: nothing
        assertArrayEquals(new String[]{null, null}, BoardLayer.momentOut("ft", "ft", null, true));
        assertArrayEquals(new String[]{null, null}, BoardLayer.momentOut(null, "h2", null, true));
    }
}
