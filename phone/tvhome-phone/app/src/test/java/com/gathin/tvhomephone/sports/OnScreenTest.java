package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The game as the picture shows it, the stream's delay behind the scores. */
public class OnScreenTest {
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

    @Test public void theScoreChangesAsTheGoalShowsNotAsItIsRead() {
        OnScreen s = new OnScreen();
        s.behind(40_000);
        long t = 1_000_000;
        s.add(live(66, 0, 0, 2), t);
        s.add(live(67, 1, 0, 2), t + 30_000);
        // The goal read at t+30s; the stream shows it 40 s later.
        assertEquals(0, s.game(t + 60_000).homeScore);
        assertEquals(1, s.game(t + 70_000).homeScore);
    }

    @Test public void theClockIsTheScreensBehindTheScores() {
        OnScreen s = new OnScreen();
        s.behind(45_000);
        long t = 2_000_000;
        s.add(live(66, 0, 0, 2), t);
        s.add(live(67, 0, 0, 2), t + 60_000);
        // 67:00 was read at t+60s; the screen gets there at t+105s, and 20 s on shows 67:20.
        assertArrayEquals(new String[]{"67:20", ""}, s.clock(t + 125_000));
        assertEquals(67 * 60 + 20, s.seconds(t + 125_000));
    }

    @Test public void justOpenedTheClockIsTakenBack() {
        OnScreen s = new OnScreen();
        s.behind(30_000);
        long t = 3_000_000;
        s.add(live(67, 0, 0, 2), t);
        assertArrayEquals(new String[]{"66:30", ""}, s.clock(t));
    }

    @Test public void halfTimeShowsUntilTheScreenGetsThere() {
        OnScreen s = new OnScreen();
        s.behind(50_000);
        long t = 4_000_000;
        Game ht = live(45, 1, 1, 1);
        ht.halftime = true;
        s.add(ht, t);
        s.add(live(45, 1, 1, 2), t + 900_000);
        assertNull(s.clock(t + 920_000));
        assertArrayEquals(new String[]{"45:10", ""}, s.clock(t + 960_000));
    }

    @Test public void theBroadcastersMinuteSetsTheSeconds() {
        OnScreen s = new OnScreen();
        s.behind(40_000);
        long t = 5_000_000;
        s.add(live(70, 0, 0, 2), t);
        // The scores make it 70:12 at t+92s on the screen; the screen's own clock turned 71:00 at t+98s.
        assertTrue(s.minuteTurned(t + 98_000));
        assertTrue(s.matched());
        assertArrayEquals(new String[]{"71:05", ""}, s.clock(t + 103_000));
    }

    @Test public void aMinuteTooFarFromTheScoresIsNotTaken() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 6_000_000;
        s.add(live(70, 0, 0, 2), t);
        // The scores make it 70:30 at t+30s: halfway between minutes, no telling which.
        assertFalse(s.minuteTurned(t + 30_000));
        assertFalse(s.matched());
    }

    @Test public void addedTimeStaysAtTheEndWithTheMinutesAdded() {
        OnScreen s = new OnScreen();
        s.behind(0);
        Game g = live(92, 2, 1, 2);
        g.displayClock = "90'+3'";
        long t = 7_000_000;
        s.add(g, t);
        assertArrayEquals(new String[]{"90:00", "+3"}, s.clock(t + 5_000));
    }

    @Test public void anotherGameStartsAfresh() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 8_000_000;
        s.add(live(30, 0, 0, 1), t);
        Game other = live(10, 0, 0, 1);
        other.id = "g2";
        s.add(other, t + 1000);
        assertEquals("g2", s.game(t + 1000).id);
    }

    @Test public void theChannelsClockReadSetsItToTheSecond() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 9_000_000;
        s.add(live(70, 0, 0, 2), t);
        // Read 69:31 off the picture at t+10s: the scores make it 70:10, so the picture is 39 s behind.
        assertTrue(s.screenClock(69 * 60 + 31, -1, t + 10_000));
        assertArrayEquals(new String[]{"69:36", ""}, s.clock(t + 15_000));
        assertEquals(-1, s.measuredBehind());
        s.screenClock(69 * 60 + 33, -1, t + 12_000);
        s.screenClock(69 * 60 + 35, -1, t + 14_000);
        assertEquals(39_000, s.measuredBehind(), 1000);
    }

    @Test public void theChannelsClockIsShownHoweverFarFromTheScores() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 10_000_000;
        // The scores say 45' in the first half; the channel runs on into added time and shows 46:12 +3.
        s.add(live(45, 0, 0, 1), t);
        assertTrue(s.screenClock(46 * 60 + 12, 3, t + 1000));
        assertArrayEquals(new String[]{"46:12", "+3"}, s.clock(t + 1000));
        assertArrayEquals(new String[]{"46:17", "+3"}, s.clock(t + 6000));
        // And one far behind them (a long delay): 20:00 is what the picture shows, so 20:00 it is.
        OnScreen o = new OnScreen();
        o.add(live(70, 0, 0, 2), t);
        assertTrue(o.screenClock(20 * 60, -1, t));
        assertArrayEquals(new String[]{"20:00", ""}, o.clock(t + 400));
        assertFalse(o.screenClock(200 * 60, -1, t + 1000));
    }

    @Test public void theClockStopsWhenTheChannelsDoes() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 10_500_000;
        s.add(live(45, 0, 0, 1), t);
        s.screenClock(45 * 60, 2, t);
        s.screenClock(45 * 60, 2, t + 4000);
        // Read the same 45:00 four seconds on: the channel's clock stands still, and so does the board's.
        assertArrayEquals(new String[]{"45:00", "+2"}, s.clock(t + 20_000));
        // And goes on with it.
        s.screenClock(45 * 60 + 1, 2, t + 30_000);
        assertArrayEquals(new String[]{"45:03", "+2"}, s.clock(t + 32_000));
    }

    @Test public void theClockSettlesIntoTheChannelsSecond() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 10_700_000;
        s.add(live(60, 0, 0, 2), t);
        // First read at t: 60:10 (somewhere in that second). At t+1300 it already shows 60:12, so the second began
        // by then: the board moves on to it rather than lagging behind.
        s.screenClock(60 * 60 + 10, -1, t);
        s.screenClock(60 * 60 + 12, -1, t + 1300);
        assertEquals(60 * 60 + 12, s.seconds(t + 1300));
        assertEquals(60 * 60 + 13, s.seconds(t + 2300));
    }

    @Test public void withoutAReadingTheScoresAreFollowedAgain() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 10_900_000;
        s.add(live(30, 0, 0, 1), t);
        s.screenClock(10 * 60, -1, t);
        assertArrayEquals(new String[]{"10:00", ""}, s.clock(t));
        // No reading for a minute (a replay, the camera on the crowd): back to the scores' clock.
        s.add(live(31, 0, 0, 1), t + 60_000);
        assertArrayEquals(new String[]{"31:00", ""}, s.clock(t + 60_000));
    }

    @Test public void aGoalTheChannelHasNotShownIsHeldBack() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 11_000_000;
        s.add(live(60, 0, 0, 2), t);
        s.add(live(61, 1, 0, 2), t + 20_000);
        // The scores have 1-0, with no delay set; the channel's own scoreboard still shows 0-0, read so steadily: 0-0 it is.
        s.screenScore(0, 0, t + 21_000);
        s.screenScore(0, 0, t + 23_000);
        s.screenScore(0, 0, t + 25_000);
        assertEquals(0, s.game(t + 25_000).homeScore);
        // The channel shows the goal: so does the board, at the same read (a score the scores have had).
        s.screenScore(1, 0, t + 50_000);
        assertEquals(1, s.game(t + 50_000).homeScore);
    }

    @Test public void aGoalTakenBackIsReadSteadilyAndSeveralGoalsAtOnceNever() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 11_500_000;
        s.add(live(60, 0, 0, 2), t);
        s.add(live(61, 1, 0, 2), t + 20_000);
        s.screenScore(1, 0, t + 21_000);
        assertEquals(1, s.game(t + 21_000).homeScore);
        // Read as 0-0 (a goal ruled out, or a misread): the board waits for it to read so steadily (three reads, four
        // seconds) while the scores' latest still has the goal.
        s.screenScore(0, 0, t + 23_000);
        assertEquals(1, s.game(t + 23_000).homeScore);
        s.screenScore(0, 0, t + 25_000);
        assertEquals(1, s.game(t + 25_000).homeScore);
        s.screenScore(0, 0, t + 27_000);
        assertEquals(0, s.game(t + 27_000).homeScore);
        // 2-0 from 0-0, which the scores haven't had: two goals at once from the picture alone are a misread, however
        // often they are read.
        for (int i = 0; i < 6; i++) s.screenScore(2, 0, t + 29_000 + i * 2000L);
        assertEquals(0, s.game(t + 41_000).homeScore);
    }

    @Test public void theChannelQuickerThanTheScoresIsBelievedWhenSeenSteadily() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 12_000_000;
        s.add(live(30, 0, 0, 1), t);
        s.screenScore(0, 1, t + 1000);
        assertEquals(0, s.game(t + 1000).awayScore);
        s.screenScore(0, 1, t + 3000);
        assertEquals(0, s.game(t + 3000).awayScore);
        s.screenScore(0, 1, t + 5000);
        assertEquals(1, s.game(t + 5000).awayScore);
        // Its clock and the rest are the scores' read, a copy: the read itself is left alone.
        assertEquals(30 * 60, s.game(t + 5000).clock, 0.5);
    }

    @Test public void halfTimeAndFullTimeAreTheChannels() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 13_000_000;
        s.add(live(46, 1, 1, 1), t);
        // The channel's clock standing at 46:10 (the whistle): its HT is taken.
        s.screenClock(46 * 60 + 10, 1, t);
        s.screenClock(46 * 60 + 10, 1, t + 4000);
        s.screenState("HT", t + 5000);
        assertTrue(s.game(t + 5000).halftime);
        assertNull(s.clock(t + 5000));
        Game end = live(90, 2, 1, 2);
        end.state = Game.POST;
        OnScreen f = new OnScreen();
        f.add(live(92, 2, 1, 2), t);
        f.screenClock(92 * 60 + 30, 3, t);
        f.screenClock(92 * 60 + 30, 3, t + 4000);
        f.screenState("FT", t + 5000);
        assertEquals(Game.POST, f.game(t + 5000).state);
        // The scores say it's over, but the channel's clock still runs (the picture is behind): it's still on.
        OnScreen r = new OnScreen();
        r.add(end, t);
        r.screenClock(93 * 60 + 10, 4, t + 1000);
        assertEquals(Game.LIVE, r.game(t + 1000).state);
        assertArrayEquals(new String[]{"93:10", "+4"}, r.clock(t + 1000));
    }

    @Test public void whatTheChannelShowsFitsTheGameOrNot() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 14_000_000;
        s.add(live(60, 1, 0, 2), t);
        assertTrue(s.fits(new int[]{1, 0}, -1, t));
        assertTrue(s.fits(null, 58 * 60, t));
        // Another game on the channel: 3-2 at 12:00.
        assertFalse(s.fits(new int[]{3, 2}, 12 * 60, t));
    }

    @Test public void aMisreadIsNotShown() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 15_000_000;
        s.add(live(67, 0, 0, 2), t);
        s.screenClock(67 * 60 + 20, -1, t);
        s.screenClock(67 * 60 + 22, -1, t + 2000);
        // "67:24" read as "07:24" once: the board stays at the channel's 67 minutes.
        s.screenClock(7 * 60 + 24, -1, t + 4000);
        assertArrayEquals(new String[]{"67:24", ""}, s.clock(t + 4000));
        s.screenClock(67 * 60 + 26, -1, t + 6000);
        assertArrayEquals(new String[]{"67:26", ""}, s.clock(t + 6000));
        // The channel puts its clock right (68:40, then 68:42): read twice, it is taken.
        s.screenClock(68 * 60 + 40, -1, t + 8000);
        assertArrayEquals(new String[]{"67:28", ""}, s.clock(t + 8000));
        s.screenClock(68 * 60 + 42, -1, t + 10_000);
        assertArrayEquals(new String[]{"68:42", ""}, s.clock(t + 10_000));
    }

    @Test public void theScoreboardIsToldFromATimeOfDay() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 16_000_000;
        s.add(live(30, 0, 0, 1), t);
        assertTrue(s.plausible(29 * 60 + 15, t));
        // 19:45, the time of day in a corner: not this game's clock.
        assertFalse(s.plausible(19 * 60 + 45, t));
        Game ht = live(45, 1, 0, 1);
        ht.halftime = true;
        OnScreen h = new OnScreen();
        h.add(ht, t);
        assertTrue(h.plausible(45 * 60, t));
        assertFalse(h.plausible(19 * 60 + 45, t));
        Game pre = live(0, 0, 0, 0);
        pre.state = Game.PRE;
        OnScreen p = new OnScreen();
        p.add(pre, t);
        assertTrue(p.plausible(40, t));
        assertFalse(p.plausible(19 * 60 + 45, t));
    }

    @Test public void aGoalOnItsWayDownTheStreamIsKnown() {
        OnScreen s = new OnScreen();
        s.behind(40_000);
        long t = 20_000_000;
        s.add(live(60, 0, 0, 2), t);
        assertFalse(s.ahead(t + 1000));
        // The scores have 1-0; the picture, 40 s behind, hasn't shown it yet.
        s.add(live(61, 1, 0, 2), t + 20_000);
        assertTrue(s.ahead(t + 30_000));
        // Once it has, nothing is ahead.
        assertFalse(s.ahead(t + 61_000));
    }
}
