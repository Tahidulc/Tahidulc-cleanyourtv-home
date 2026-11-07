package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** 4.18: the board's clock always counts on, through added time; the added time put up, read from ESPN's commentary. */
public class BoardClockTest {
    private static Game live(double clock, String display, int period) {
        Game g = new Game();
        g.id = "g1";
        g.state = Game.LIVE;
        g.clock = clock;
        g.period = period;
        g.displayClock = display;
        g.detail = display;
        return g;
    }

    @Test public void itCountsOnThroughAddedTimeWhenEspnsClockStandsStill() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 7_000_000;
        // ESPN's clock stands still at 90:00 while its words go on ("90'+3'", the 93rd minute): the board counts on.
        s.add(live(5400, "90'+3'", 2), t);
        String[] at = s.boardClock(t + 5_000);
        assertEquals("92:05", at[0]);
        assertEquals("", at[1]);
        s.add(live(5400, "90'+3'", 2), t + 15_000);
        assertEquals("92:20", s.boardClock(t + 20_000)[0]);
        s.add(live(5400, "90'+4'", 2), t + 60_000);
        assertEquals("93:01", s.boardClock(t + 61_000)[0]);
        // Two minutes with no new read: still counting, never held at a minute's end.
        assertEquals("95:01", s.boardClock(t + 181_000)[0]);
    }

    @Test public void itNeverStepsBackALittleButTakesARealCorrection() {
        OnScreen s = new OnScreen();
        long t = 8_000_000;
        assertEquals(3_610_000L, s.running("g1|2", 3_610_000L, t));
        // Told a few seconds less than where it was counting: it keeps counting.
        assertEquals(3_613_000L, s.running("g1|2", 3_600_000L, t + 3_000));
        // Told it is further on: it goes on from there.
        assertEquals(3_640_000L, s.running("g1|2", 3_640_000L, t + 4_000));
        // Told it is far behind (over half a minute): put right.
        assertEquals(2_700_000L, s.running("g1|2", 2_700_000L, t + 5_000));
        // Another half, or another game: from what it is told.
        assertEquals(100_000L, s.running("g1|1", 100_000L, t + 6_000));
    }

    @Test public void theChannelsClockStandingStillAtTheEndDoesNotStopIt() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 9_000_000;
        s.add(live(5340, "90'", 2), t);
        s.screenClock(90 * 60, 4, t);
        assertEquals("90:01", s.boardClock(t + 1_000)[0]);
        s.screenClock(90 * 60, 4, t + 4_000);
        // The channel stands its clock still at 90:00 with +4 beside it: the board counts on, the +4 kept.
        String[] at = s.boardClock(t + 20_000);
        assertEquals("90:20", at[0]);
        assertEquals("+4", at[1]);
    }

    @Test public void halfTimeAndFullTimeStopIt() {
        OnScreen s = new OnScreen();
        s.behind(0);
        long t = 9_500_000;
        Game g = live(2760, "45'+2'", 1);
        g.halftime = true;
        s.add(g, t);
        assertNull(s.boardClock(t + 1000));
        Game end = live(5700, "FT", 2);
        end.state = Game.POST;
        OnScreen f = new OnScreen();
        f.add(end, t);
        assertNull(f.boardClock(t + 1000));
    }

    @Test public void theAddedTimePutUpIsReadFromTheCommentary() throws Exception {
        assertEquals(4, Espn.addedIn("Fourth official has announced 4 minutes of added time."));
        assertEquals(1, Espn.addedIn("Fourth official has announced 1 minute of added time."));
        assertEquals(-1, Espn.addedIn("Corner, Everton. Conceded by Ben White."));
        assertEquals(1, Espn.halfOf("45'"));
        assertEquals(2, Espn.halfOf("90'+2'"));
        assertEquals(3, Espn.halfOf("105'"));
        assertEquals(0, Espn.halfOf(""));
        JSONArray com = new JSONArray()
                .put(new JSONObject().put("text", "Fourth official has announced 2 minutes of added time.")
                        .put("time", new JSONObject().put("displayValue", "45'")))
                .put(new JSONObject().put("text", "Fourth official has announced 5 minutes of added time.")
                        .put("time", new JSONObject().put("displayValue", "90'")));
        int[] added = {-1, -1, -1, -1};
        Espn.addedTime(com, added);
        assertArrayEquals(new int[]{2, 5, -1, -1}, added);
        // Prices as chances: +100 is even, -300 three in four.
        assertEquals(0.5, Espn.chance(100), 0.001);
        assertEquals(0.75, Espn.chance(-300), 0.001);
        assertEquals(0, Espn.chance(0), 0.01);
    }
}
