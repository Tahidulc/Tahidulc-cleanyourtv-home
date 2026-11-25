package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.Moments;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.OnScreen;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The phone's full-time pop-up (PlayerIntro.tick): once, with the final score, as the board's own full time reaches the
 * picture (OnScreen: the whistle, never the data's flip a minute early). A made-up second half (Northfield v Riverside,
 * 2-1), the data's full time a minute before the stream shows it, replayed second by second as the scoreboard draws.
 */
public class FullTimePopupTest {
    private static Game read(int minute, boolean over) {
        Game g = new Game();
        g.id = "g-nor-riv";
        g.homeName = "Northfield";
        g.awayName = "Riverside";
        g.state = over ? Game.POST : Game.LIVE;
        g.clock = minute * 60;
        g.period = 2;
        g.homeScore = 2;
        g.awayScore = 1;
        g.detail = over ? "FT" : minute + "'";
        return g;
    }

    @Test
    public void fullTimesPopUpComesOnceWithTheBoardsFullTime() {
        OnScreen s = new OnScreen();
        long t0 = 70_000_000, delay = 60_000;
        s.behind(delay);
        String was = "h2";
        List<Long> fulls = new ArrayList<>();
        int[] score = null;
        for (long wall = t0 + 5600_000L; wall <= t0 + 5900_000L; wall += 1000) {
            long real = (wall - t0) / 1000;
            if (real % 15 == 0) s.add(real < 5700 ? read((int) (real / 60), false) : read(95, true), wall);
            s.boardClock(wall);
            Game g = s.game(wall);
            if (g == null) continue;
            // as tick follows the game the board shows
            String now = PlayerIntro.phase(g, was);
            String kind = Moments.moment(was, now);
            if (kind != null) {
                assertEquals("full", kind);
                fulls.add(wall);
                score = PlayerIntro.momentScore(kind, g);
            }
            if (!now.isEmpty()) was = now;
        }
        assertEquals(1, fulls.size());
        long onPicture = t0 + 5700_000L + delay;
        assertTrue("came " + (fulls.get(0) - onPicture) + " ms after the whistle on the picture",
                fulls.get(0) >= onPicture && fulls.get(0) - onPicture <= 15_000);
        assertArrayEquals(new int[]{2, 1}, score);
        // the scoreboard away while it is on, as for the other moments
        assertTrue(PlayerIntro.moment("full"));
    }

    @Test
    public void theKickOffHasNoScoreTheOthersTheGames() {
        Game g = read(50, false);
        assertNull(PlayerIntro.momentScore("kickoff", g));
        assertArrayEquals(new int[]{2, 1}, PlayerIntro.momentScore("restart", g));
        assertArrayEquals(new int[]{2, 1}, PlayerIntro.momentScore("second", g));
        // joined in the second half: its half from the scores, so full time still comes
        assertEquals("h2", PlayerIntro.phase(g, null));
        assertEquals("full", Moments.moment(PlayerIntro.phase(g, null), PlayerIntro.phase(read(95, true), "h2")));
        // the test notifications have every moment
        assertEquals(java.util.Arrays.asList(Moments.KINDS), PlayerTests.tests(PlayerTests.POPUP));
    }
}
