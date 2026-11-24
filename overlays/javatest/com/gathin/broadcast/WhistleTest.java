package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/**
 * Half time and full time as the whistle reaches the picture: made-up halves replayed second by second, each with the
 * stream's delay, the data's own lag, the minutes added, the channel's clock read off the picture (or not), FotMob.
 */
public class WhistleTest {
    private static final String G = "g1";
    /** The real game's kick-off, on the wall clock. */
    private static final long T0 = 1_000_000_000L;

    /** The board's clock in step with the picture: the real game's clock delay ms ago (never past where it stopped). */
    private static long onPicture(long wall, long delay, long stoppedAt) {
        long s = (wall - delay - T0) / 1000;
        return Math.min(s, stoppedAt);
    }

    /**
     * A first half of 45 + 3 that the referee ends at 45+4:10 (49:10), the stream 45 s behind, ESPN's half time 30 s
     * after the whistle; the channel's clock read off the picture every two seconds, standing still at 49:10 once the
     * whistle is on the picture. Never half time before 49:10 on the picture (not at 48:00 when the minutes added are up,
     * nor when the data has it over and the picture is still playing); half time within a few seconds of it.
     */
    @Test public void aFortyFivePlusThreeHalfEndedAtFortyNineTenWithThePicturesClock() {
        Whistle w = new Whistle();
        long delay = 45_000, end = 2950, espnLag = 30, firstHt = -1;
        w.added(G, 1, 3);
        for (long wall = T0; wall <= T0 + 3100_000L; wall += 1000) {
            long real = (wall - T0) / 1000;
            // ESPN read every 15 s: in play until 30 s after the whistle, then half time.
            if (real % 15 == 0) {
                if (real < end + espnLag) w.data(wall, G, 1, Whistle.IN_PLAY, Math.min(real, end + espnLag) / 60 * 60);
                else w.data(wall, G, 1, Whistle.AT_BREAK, 2700);
            }
            long pic = onPicture(wall, delay, end);
            // The channel's clock: read every two seconds, still once it has shown 49:10 for three seconds.
            if (real % 2 == 0 && pic >= 0) {
                long stillFor = (wall - delay - T0) / 1000 - end;
                w.picture(wall, G, pic, pic == end && stillFor >= 3);
            }
            int to = w.over(wall, G, 1, pic, delay, true);
            if (to != Whistle.NONE && firstHt < 0) {
                firstHt = wall;
                assertEquals(Whistle.HT, to);
            }
            if (pic < end) assertEquals("not before the whistle on the picture (" + pic + ")", Whistle.NONE, to);
        }
        long whistleOnPicture = T0 + end * 1000 + delay;
        assertTrue("half time came", firstHt > 0);
        assertTrue("within a few seconds of the whistle on the picture: " + (firstHt - whistleOnPicture), firstHt - whistleOnPicture <= 5_000);
    }

    /**
     * The same half on the TV, where the channel's clock can't be read: the board's clock is the scores' behind the
     * stream's delay. FotMob has the half's own times (20:00:00 to 20:49:10) 15 s after the whistle: half time as the
     * board's clock reaches 49:10, rather than ESPN's flip 30 s later.
     */
    @Test public void fotMobsTimesForTheHalfStopTheClockAtItsLength() throws Exception {
        Whistle w = new Whistle();
        long delay = 45_000, end = 2950, firstHt = -1;
        w.added(G, 1, 3);
        JSONObject fm = new JSONObject().put("header", new JSONObject().put("status", new JSONObject()
                .put("halfs", new JSONObject().put("firstHalfStarted", "24.11.2026 20:00:00").put("firstHalfEnded", "24.11.2026 20:49:10"))
                .put("reason", new JSONObject().put("short", "HT")).put("finished", false)));
        Whistle.Status st = Whistle.fotmobStatus(fm);
        assertEquals(1, st.period);
        assertEquals(Whistle.AT_BREAK, st.phase);
        assertEquals(2950, st.length[1]);
        for (long wall = T0; wall <= T0 + 3100_000L; wall += 1000) {
            long real = (wall - T0) / 1000;
            if (real % 15 == 0) {
                if (real < end + 30) w.data(wall, G, 1, Whistle.IN_PLAY, real / 60 * 60);
                else w.data(wall, G, 1, Whistle.AT_BREAK, 2700);
            }
            if (real == end + 15) w.fotmob(wall, G, st);
            // the board counts on from the scores, never stopping by itself
            long board = (wall - delay - T0) / 1000;
            int to = w.over(wall, G, 1, board, delay, false);
            if (to != Whistle.NONE && firstHt < 0) firstHt = wall;
            if (board < end) assertEquals(Whistle.NONE, to);
        }
        long whistleOnPicture = T0 + end * 1000 + delay;
        assertTrue(firstHt > 0);
        assertTrue("half time at the half's length: " + (firstHt - whistleOnPicture), firstHt - whistleOnPicture <= 2_000);
    }

    /**
     * Full time in the data a minute before the stream shows it (the stream 60 s behind; ESPN's whistle at 95:00 with no
     * lag of its own). Not before the picture's whistle, and at it: by the data's flip as far behind as the stream is, and
     * where the channel's clock is read, by it going from the picture at the whistle.
     */
    @Test public void dataThatEndsAMinuteBeforeTheStreamShowsIt() {
        long delay = 60_000, end = 95 * 60;
        for (boolean read : new boolean[]{false, true}) {
            Whistle w = new Whistle();
            w.added(G, 2, 5);
            long first = -1;
            for (long wall = T0 + 5000_000L; wall <= T0 + 5900_000L; wall += 1000) {
                long real = (wall - T0) / 1000;
                if (real % 15 == 0) {
                    if (real < end) w.data(wall, G, 2, Whistle.IN_PLAY, real / 60 * 60);
                    else w.data(wall, G, 2, Whistle.AT_END, 5400);
                }
                long pic = onPicture(wall, delay, end);
                if (read && real % 2 == 0) {
                    // the channel's scoreboard goes at the whistle: no clock on it after
                    if ((wall - delay - T0) / 1000 < end) w.picture(wall, G, pic, false);
                    else w.pictureNone(wall, G);
                }
                int to = w.over(wall, G, 2, pic, delay, read);
                if (to != Whistle.NONE && first < 0) {
                    first = wall;
                    assertEquals(Whistle.FT, to);
                }
            }
            long whistleOnPicture = T0 + end * 1000 + delay;
            assertTrue(first > 0);
            assertTrue((read ? "read: " : "not read: ") + (first - whistleOnPicture), first >= whistleOnPicture && first - whistleOnPicture <= 7_000);
        }
    }

    /** The stream's delay lost (the board thinks it is in step with the scores): the picture's running clock still holds full time back. */
    @Test public void aLostDelayNeverBringsFullTimeEarlyWhileThePictureIsPlaying() {
        Whistle w = new Whistle();
        long realDelay = 60_000, end = 95 * 60;
        long first = -1;
        for (long wall = T0 + 5500_000L; wall <= T0 + 5900_000L; wall += 1000) {
            long real = (wall - T0) / 1000;
            if (real % 15 == 0) w.data(wall, G, 2, real < end ? Whistle.IN_PLAY : Whistle.AT_END, real < end ? real / 60 * 60 : 5400);
            long pic = onPicture(wall, realDelay, end);
            if (real % 2 == 0) {
                if ((wall - realDelay - T0) / 1000 < end) w.picture(wall, G, pic, false);
                else w.pictureNone(wall, G);
            }
            // told the picture is 0 s behind
            int to = w.over(wall, G, 2, pic, 0, true);
            if (to != Whistle.NONE && first < 0) first = wall;
        }
        assertTrue(first >= T0 + end * 1000 + realDelay);
    }

    /**
     * A missing status: ESPN never says half time and FotMob isn't there. The channel's clock standing at 47:20 for 20 s
     * is half time; a second half's clock standing still is never full time by itself (it waits for the data).
     */
    @Test public void aMissingStatusAndTheChannelsClockStandingStill() {
        Whistle w = new Whistle();
        long delay = 30_000;
        w.data(T0, G, 1, Whistle.IN_PLAY, 2820);
        long stop = T0 + 100_000;
        int to = Whistle.NONE;
        for (long wall = stop; wall <= stop + 30_000; wall += 2000) {
            w.picture(wall, G, 2840, wall - stop >= 3000);
            to = w.over(wall, G, 1, 2840, delay, true);
            if (wall - stop < 23_000) assertEquals(Whistle.NONE, to);
        }
        assertEquals(Whistle.HT, to);
        // the second half: the clock stands at 93:40, and nothing from the data
        Whistle w2 = new Whistle();
        w2.data(T0, G, 2, Whistle.IN_PLAY, 5580);
        for (long wall = stop; wall <= stop + 120_000; wall += 2000) {
            w2.picture(wall, G, 5620, wall - stop >= 3000);
            assertEquals(Whistle.NONE, w2.over(wall, G, 2, 5620, delay, true));
        }
        // ESPN says it is over at last: full time at once (the picture's clock stopped past 90:00)
        w2.data(stop + 121_000, G, 2, Whistle.AT_END, 5400);
        w2.picture(stop + 122_000, G, 5620, true);
        assertEquals(Whistle.FT, w2.over(stop + 122_000, G, 2, 5620, delay, true));
        // and with no picture and no data, nothing
        Whistle w3 = new Whistle();
        w3.data(T0, G, 1, Whistle.IN_PLAY, 2820);
        assertEquals(Whistle.NONE, w3.over(T0 + 600_000, G, 1, 3400, delay, false));
    }

    /** A channel whose clock stands at 45:00 through the minutes added says nothing of when they are up: the data decides. */
    @Test public void aClockStandingAtTheHalfsEndThroughAddedTime() {
        Whistle w = new Whistle();
        w.added(G, 1, 2);
        w.data(T0, G, 1, Whistle.IN_PLAY, 2700);
        w.picture(T0 + 1000, G, 2700, true);
        assertEquals(Whistle.NONE, w.over(T0 + 1000, G, 1, 2700, 40_000, true));
        w.data(T0 + 150_000, G, 1, Whistle.AT_BREAK, 2700);
        w.picture(T0 + 160_000, G, 2700, true);
        assertEquals(Whistle.NONE, w.over(T0 + 160_000, G, 1, 2700, 40_000, true));
        w.picture(T0 + 190_000, G, 2700, true);
        assertEquals(Whistle.HT, w.over(T0 + 190_000, G, 1, 2700, 40_000, true));
    }

    /** Half time kept while the picture has it, let go as the picture's clock goes again (a replay that hid it, the second half). */
    @Test public void halfTimeStaysUntilThePictureShowsPlay() {
        Whistle w = new Whistle();
        w.data(T0, G, 1, Whistle.IN_PLAY, 2760);
        w.data(T0 + 20_000, G, 1, Whistle.AT_BREAK, 2700);
        w.picture(T0 + 30_000, G, 2790, true);
        assertEquals(Whistle.HT, w.over(T0 + 30_000, G, 1, 2790, 45_000, true));
        // the clock gone from the picture: still half time
        assertEquals(Whistle.HT, w.over(T0 + 60_000, G, 1, 2790, 45_000, true));
        // it goes again
        w.picture(T0 + 900_000, G, 2701, false);
        assertEquals(Whistle.NONE, w.over(T0 + 900_000, G, 1, 2701, 45_000, true));
    }

    @Test public void fotMobsTimesAndStatus() throws Exception {
        assertEquals(2950, Whistle.lengthOf(1, "24.11.2026 20:00:00", "24.11.2026 20:49:10"));
        assertEquals(2700 + 2880, Whistle.lengthOf(2, "24.11.2026 21:05:00", "24.11.2026 21:53:00"));
        // across midnight, and ISO times
        assertEquals(2760, Whistle.lengthOf(1, "31.12.2026 23:40:00", "01.01.2027 00:26:00"));
        assertEquals(2760, Whistle.lengthOf(1, "2026-11-24T20:00:00", "2026-11-24T20:46:00"));
        assertEquals(-1, Whistle.lengthOf(1, "", "24.11.2026 20:49:10"));
        assertEquals(-1, Whistle.lengthOf(1, "24.11.2026 20:00:00", "24.11.2026 20:10:00"));
        JSONObject done = new JSONObject().put("header", new JSONObject().put("status", new JSONObject().put("finished", true)
                .put("halfs", new JSONObject().put("firstHalfStarted", "24.11.2026 20:00:00").put("firstHalfEnded", "24.11.2026 20:47:00")
                        .put("secondHalfStarted", "24.11.2026 21:03:00").put("secondHalfEnded", "24.11.2026 21:51:30"))));
        Whistle.Status s = Whistle.fotmobStatus(done);
        assertEquals(2, s.period);
        assertEquals(Whistle.AT_END, s.phase);
        assertEquals(2820, s.length[1]);
        assertEquals(2700 + 2910, s.length[2]);
        JSONObject on = new JSONObject().put("header", new JSONObject().put("status", new JSONObject().put("finished", false)
                .put("halfs", new JSONObject().put("firstHalfStarted", "24.11.2026 20:00:00").put("firstHalfEnded", "")
                        .put("secondHalfStarted", "").put("secondHalfEnded", ""))
                .put("liveTime", new JSONObject().put("short", "44'"))));
        assertEquals(Whistle.IN_PLAY, Whistle.fotmobStatus(on).phase);
        assertEquals(0, Whistle.fotmobStatus(new JSONObject()).period);
    }

    @Test public void espnsBreaks() {
        assertTrue(Whistle.atBreak("STATUS_HALFTIME"));
        assertTrue(Whistle.atBreak("STATUS_HALFTIME_ET"));
        assertTrue(Whistle.atBreak("STATUS_END_OF_REGULATION"));
        assertTrue(!Whistle.atBreak("STATUS_SECOND_HALF"));
        assertTrue(!Whistle.atBreak(null));
    }
}
