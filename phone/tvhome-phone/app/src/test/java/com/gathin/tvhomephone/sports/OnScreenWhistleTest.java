package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The phone's board through made-up matches: half time and full time as the whistle reaches the picture, and each way
 * the board was seen to go wrong (goals added that weren't, half time that wasn't), replayed as it happened.
 */
public class OnScreenWhistleTest {
    private static Game live(int minute, int home, int away, int period) {
        Game g = new Game();
        g.id = "g1";
        g.state = Game.LIVE;
        g.clock = minute * 60;
        g.period = period;
        g.homeScore = home;
        g.awayScore = away;
        g.detail = minute + "'";
        return g;
    }

    private static Game ht(int home, int away) {
        Game g = live(45, home, away, 1);
        g.halftime = true;
        return g;
    }

    /**
     * 45 + 3 put up, the referee's whistle at 45+4:10 (49:10), the stream 45 s behind, ESPN's half time 30 s after the
     * whistle. The channel's clock read every two seconds; it stands still at 49:10. The board's clock never runs past
     * it, half time is never shown before it and comes within a few seconds of it.
     */
    @Test public void aFortyFivePlusThreeHalfEndsAsTheChannelsClockStops() {
        OnScreen s = new OnScreen();
        long delay = 45_000, t0 = 50_000_000, end = 2950;
        s.behind(delay);
        s.whistle().added("g1", 1, 3);
        long first = -1;
        for (long wall = t0 + 2600_000L; wall <= t0 + 3060_000L; wall += 1000) {
            long real = (wall - t0) / 1000;
            if (real % 15 == 0) s.add(real < end + 30 ? live((int) (real / 60), 0, 0, 1) : ht(0, 0), wall);
            long pic = Math.min((wall - delay - t0) / 1000, end);
            if (real % 2 == 0) s.screenClock(pic, 3, wall);
            String[] clock = s.boardClock(wall);
            Game g = s.game(wall);
            if (g == null) continue;
            if (g.halftime && first < 0) first = wall;
            if ((wall - delay - t0) / 1000 < end) assertFalse("half time before the whistle on the picture", g.halftime);
            // never more than the seconds it takes to see the channel's clock stand still past it (two reads alike)
            if (clock != null) assertTrue(clock[0], clock[0].compareTo("49:13") < 0);
        }
        long onPicture = t0 + end * 1000 + delay;
        assertTrue(first > 0);
        assertTrue("half time " + (first - onPicture) + " ms after the whistle on the picture", first - onPicture <= 5_000);
    }

    /** The channel's clock standing still: the board's stands with it, not counting on half a minute past it and back. */
    @Test public void theBoardsClockStandsWithTheChannels() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 60_000_000;
        s.add(live(70, 1, 0, 2), t);
        s.screenClock(70 * 60 + 5, -1, t);
        assertArrayEquals(new String[]{"70:05", ""}, s.boardClock(t));
        s.screenClock(70 * 60 + 7, -1, t + 2000);
        s.screenClock(70 * 60 + 7, -1, t + 5000);
        // stood still at 70:07 for three seconds: the board shows 70:07, and still does twenty seconds on
        assertArrayEquals(new String[]{"70:07", ""}, s.boardClock(t + 5000));
        s.screenClock(70 * 60 + 7, -1, t + 15_000);
        assertArrayEquals(new String[]{"70:07", ""}, s.boardClock(t + 25_000));
    }

    /** A ticker's "HT 1-0" about another game while this one plays: not half time. */
    @Test public void halfTimeReadOffATickerWhileTheClockRunsIsNotHalfTime() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 61_000_000;
        s.add(live(30, 0, 0, 1), t);
        s.screenClock(30 * 60 + 10, -1, t);
        s.screenClock(30 * 60 + 12, -1, t + 2000);
        s.screenState("HT", t + 2000);
        assertFalse(s.game(t + 2000).halftime);
        assertTrue(s.boardClock(t + 2000) != null);
        // nor in the second half with the channel's clock out of sight a moment (a replay)
        OnScreen o = new OnScreen();
        o.behind(0);
        o.add(live(60, 1, 0, 2), t);
        o.screenClock(60 * 60, -1, t);
        o.screenState("HT", t + 20_000);
        assertFalse(o.game(t + 20_000).halftime);
    }

    /**
     * The delay measured off the channel's clock, a digit misread twice running ("47:30" as "43:30", taken as the channel
     * putting its clock right): that reading is left out of the delay, so when the channel's clock is out of sight a
     * minute later the board doesn't go back four and a half minutes into half time (it used to take the most of the
     * readings: 280 s).
     */
    @Test public void aMisreadClockDoesNotThrowTheDelayBackIntoHalfTime() {
        OnScreen s = new OnScreen();
        long t = 62_000_000;
        s.behind(40_000);
        s.add(ht(1, 0), t);
        s.add(live(45, 1, 0, 2), t + 60_000);
        s.add(live(46, 1, 0, 2), t + 120_000);
        s.add(live(47, 1, 0, 2), t + 180_000);
        s.add(live(48, 1, 0, 2), t + 240_000);
        // the picture 40 s behind the scores: 47:20 on the channel when the scores make it 48:00
        long at = t + 240_000;
        for (int i = 0; i < 5; i++) s.screenClock(47 * 60 + 20 + 2 * i, -1, at + 2000L * i);
        assertEquals(40_000, s.measuredBehind(), 2500);
        s.screenClock(43 * 60 + 30, -1, at + 10_000);
        s.screenClock(43 * 60 + 32, -1, at + 12_000);
        assertEquals(40_000, s.measuredBehind(), 2500);
        s.behind(s.measuredBehind());
        // a minute on, the channel's clock not read since (a replay, an advert): the scores, 40 s behind
        assertFalse(s.game(at + 60_000).halftime);
        assertEquals(Game.LIVE, s.game(at + 60_000).state);
    }

    /**
     * A stats graphic grown out of the channel's scoreboard ("SHOTS 4 2"), read as two numbers again and again, at 1-1:
     * no goals added. Nor a jump of two goals read as a score.
     */
    @Test public void aStatsGraphicsNumbersAreNoGoals() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 63_000_000;
        s.add(live(55, 1, 1, 2), t);
        assertEquals(1, s.game(t).homeScore);
        for (int i = 0; i < 8; i++) s.screenScore(4, 2, t + 2000L * i, false);
        assertEquals(1, s.game(t + 16_000).homeScore);
        assertEquals(1, s.game(t + 16_000).awayScore);
        for (int i = 0; i < 8; i++) s.screenScore(3, 1, t + 20_000 + 2000L * i, true);
        assertEquals(1, s.game(t + 36_000).homeScore);
        // two numbers that make a goal (2 1), read steadily: still not a goal without the scores or a score's dash
        for (int i = 0; i < 8; i++) s.screenScore(2, 1, t + 40_000 + 2000L * i, false);
        assertEquals(1, s.game(t + 56_000).homeScore);
    }

    /**
     * The stream's delay lost and found again (put to nothing, then back to a minute): the score never goes back, and
     * the goal is not shown a second time.
     */
    @Test public void aLostDelayGivesNoGoalTwice() {
        OnScreen s = new OnScreen();
        long t = 64_000_000;
        s.behind(60_000);
        s.add(live(20, 0, 0, 1), t);
        s.add(live(21, 1, 0, 1), t + 30_000);
        assertEquals(0, s.game(t + 40_000).homeScore);
        s.behind(0);
        assertEquals(1, s.game(t + 42_000).homeScore);
        s.behind(60_000);
        assertEquals(1, s.game(t + 45_000).homeScore);
        assertEquals(1, s.game(t + 80_000).homeScore);
        // a goal ruled out (VAR): the scores' latest read has it off, so the board does too
        s.add(live(23, 0, 0, 1), t + 150_000);
        s.behind(0);
        assertEquals(0, s.game(t + 152_000).homeScore);
    }

    /** Two goals in one read of the scores come on the board one after the other, never as a jump of two. */
    @Test public void twoGoalsInOneReadComeOneAtATime() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 65_000_000;
        s.add(live(70, 0, 0, 2), t);
        assertEquals(0, s.game(t).homeScore);
        s.add(live(72, 2, 0, 2), t + 60_000);
        assertEquals(1, s.game(t + 60_000).homeScore);
        assertEquals(1, s.game(t + 60_500).homeScore);
        assertEquals(2, s.game(t + 62_000).homeScore);
    }

    /** Another game on the channel at a like time (its score not this one's): not taken for the followed club's. */
    @Test public void anotherGameAtALikeTimeIsNotThisOne() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 66_000_000;
        s.add(live(30, 0, 0, 1), t);
        assertFalse(s.fits(new int[]{2, 1}, 30 * 60 + 20, t));
        assertTrue(s.fits(new int[]{0, 0}, 30 * 60 + 20, t));
        assertFalse(s.fits(new int[]{0, 0}, 70 * 60, t));
        assertFalse(s.fits(null, 25 * 60, t));
    }

    /** Full time a minute in the data before the picture: the board's clock goes on to the whistle, then FT. */
    @Test public void fullTimeComesWithThePictureNotTheData() {
        OnScreen s = new OnScreen();
        long t0 = 70_000_000, delay = 60_000;
        s.behind(delay);
        long first = -1;
        for (long wall = t0 + 5600_000L; wall <= t0 + 5900_000L; wall += 1000) {
            long real = (wall - t0) / 1000;
            if (real % 15 == 0) {
                Game g = real < 5700 ? live((int) (real / 60), 2, 1, 2) : live(95, 2, 1, 2);
                if (real >= 5700) g.state = Game.POST;
                s.add(g, wall);
            }
            s.boardClock(wall);
            Game g = s.game(wall);
            if (g != null && g.state == Game.POST && first < 0) first = wall;
        }
        long onPicture = t0 + 5700_000L + delay;
        assertTrue(first >= onPicture);
        assertTrue(first - onPicture <= 15_000);
        assertNull(s.boardClock(first));
    }
}
