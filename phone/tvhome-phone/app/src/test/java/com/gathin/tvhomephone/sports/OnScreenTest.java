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
        assertTrue(s.screenClock(69 * 60 + 31, t + 10_000));
        assertArrayEquals(new String[]{"69:36", ""}, s.clock(t + 15_000));
        assertEquals(-1, s.measuredBehind());
        s.screenClock(69 * 60 + 33, t + 12_000);
        s.screenClock(69 * 60 + 35, t + 14_000);
        assertEquals(39_000, s.measuredBehind(), 1000);
    }

    @Test public void aClockFarFromTheGamesIsNotTaken() {
        OnScreen s = new OnScreen();
        long t = 10_000_000;
        s.add(live(70, 0, 0, 2), t);
        assertFalse(s.screenClock(20 * 60, t));
    }

    @Test public void theScoreChangesAsTheChannelsScoreboardDoes() {
        OnScreen s = new OnScreen();
        s.behind(60_000);
        long t = 11_000_000;
        s.add(live(60, 0, 0, 2), t);
        s.add(live(61, 1, 0, 2), t + 20_000);
        // A minute's delay would hold the goal till t+80s; the channel's own scoreboard shows 1-0 at t+50s.
        assertEquals(0, s.game(t + 50_000).homeScore);
        assertTrue(s.screenScore((h, a) -> h == 1 && a == 0, t + 50_000));
        assertEquals(1, s.game(t + 50_000).homeScore);
    }

    @Test public void theChannelQuickerThanTheScoresIsBelievedWhenSeenTwice() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 12_000_000;
        s.add(live(30, 0, 0, 1), t);
        assertFalse(s.screenScore((h, a) -> h == 0 && a == 1, t + 1000));
        assertTrue(s.screenScore((h, a) -> h == 0 && a == 1, t + 3000));
        assertEquals(1, s.game(t + 3000).awayScore);
    }
}
