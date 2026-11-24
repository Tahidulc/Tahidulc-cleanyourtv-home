package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.MadeUpGuide;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Catch-up's moves where they are pure: the steps, the overlay's jumps, where the spot can go, the programmes before and
 * after, the bubble's words, and what is asked of the archive for a move (every host and login here is invented).
 */
public class CatchupSeekTest {
    private static final long S = 1000L, MIN = 60_000L;
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final String LIVE_URL = "http://box.example:8080/live/viewer/made-up/101.ts";

    /** 16 Nov 2026, 20:00 in London (GMT). */
    private static final long EIGHT = ZonedDateTime.of(2026, 11, 16, 20, 0, 0, 0, LONDON).toInstant().toEpochMilli();

    @Test public void theStepsAndTheOverlaysJumps() {
        // A key held: its repeat count + 1 is the step's place; taps are each the first.
        assertEquals(10 * S, CatchupScreen.step(1, 10 * S));
        assertEquals(30 * S, CatchupScreen.step(6, 10 * S));
        assertEquals(60 * S, CatchupScreen.step(16, 10 * S));
        assertEquals(180 * S, CatchupScreen.step(40, 10 * S));
        assertEquals(5 * S, CatchupScreen.step(2, 5 * S));
        // ‹10 back a Skip step, 30› on 30 seconds (the step when it is longer).
        assertArrayEquals(new long[]{10 * S, 30 * S}, CatchupScreen.jumps(10 * S));
        assertArrayEquals(new long[]{5 * S, 30 * S}, CatchupScreen.jumps(5 * S));
        assertArrayEquals(new long[]{60 * S, 60 * S}, CatchupScreen.jumps(60 * S));
    }

    @Test public void theSpotStaysInsideTheArchiveAndNoLaterThanNow() {
        long now = EIGHT + 3 * 3600_000L, earliest = now - 86_400_000L;
        assertEquals(EIGHT, CatchupScreen.spot(EIGHT, earliest, now));
        assertEquals(earliest, CatchupScreen.spot(earliest - MIN, earliest, now));
        assertEquals(now, CatchupScreen.spot(now + MIN, earliest, now));
        // No archive known (a stream's own window): only now bounds it.
        assertEquals(earliest - MIN, CatchupScreen.spot(earliest - MIN, Long.MAX_VALUE, now));
        // The live edge: within a minute of now.
        assertTrue(CatchupScreen.liveEdge(now - 30 * S, now));
        assertTrue(CatchupScreen.liveEdge(now - LiveTv.LIVE_EDGE_MS, now));
        assertFalse(CatchupScreen.liveEdge(now - LiveTv.LIVE_EDGE_MS - 1, now));
    }

    @Test public void theProgrammesBeforeAndAfter() {
        // 19:00–20:00, 20:00–21:00, 21:00–21:30.
        long h = 3600_000L;
        Guide.Schedule s = MadeUpGuide.schedule(new long[]{EIGHT - h, EIGHT, EIGHT + h}, new long[]{EIGHT, EIGHT + h, EIGHT + h + 30 * MIN});
        long[] p = CatchupScreen.programme(s, EIGHT + 34 * MIN, new long[3]);
        assertArrayEquals(new long[]{EIGHT, EIGHT + h, 1}, p);
        // 10 seconds before 20:00: the programme before's line.
        assertArrayEquals(new long[]{EIGHT - h, EIGHT, 0}, CatchupScreen.programme(s, EIGHT - 10 * S, new long[3]));
        // |◀ from anywhere in a programme: the one before's start; ▶|: the next one's.
        assertEquals(EIGHT - h, CatchupScreen.previousStart(s, EIGHT + 34 * MIN));
        assertEquals(EIGHT - h, CatchupScreen.previousStart(s, EIGHT));
        assertEquals(EIGHT, CatchupScreen.previousStart(s, EIGHT + h + MIN));
        assertEquals(EIGHT + h, CatchupScreen.nextStart(s, EIGHT + 34 * MIN));
        assertEquals(EIGHT + h + 30 * MIN, CatchupScreen.nextStart(s, EIGHT + h));
        // No guide there: the hour from the half hour round it.
        assertArrayEquals(new long[]{EIGHT + 2 * h, EIGHT + 3 * h, -1}, CatchupScreen.programme(s, EIGHT + 2 * h + 10 * MIN, new long[3]));
        assertArrayEquals(new long[]{EIGHT + 30 * MIN, EIGHT + 90 * MIN, -1}, CatchupScreen.programme(null, EIGHT + 40 * MIN, new long[3]));
    }

    @Test public void theBubblesWords() {
        // "21:14 · 34:10 of 1:00:00": the clock then, into the programme, its length.
        assertEquals("21:14 · 34:10 of 1:00:00", "21:14" + CatchupScreen.bubbleRest(34 * MIN + 10 * S, 60 * MIN, false));
        assertEquals(" · 00:05 of 30:00", CatchupScreen.bubbleRest(5 * S, 30 * MIN, false));
        assertEquals(" · 1:02:03 of 2:00:00", CatchupScreen.bubbleRest(3_723_000L, 2 * 3600_000L, false));
        assertEquals(" · Live", CatchupScreen.bubbleRest(10 * MIN, 30 * MIN, true));
        // How far the spot is from the picture.
        assertEquals("−1:30", CatchupScreen.delta(-90 * S));
        assertEquals("+0:10", CatchupScreen.delta(10 * S));
        assertEquals("−12:00", CatchupScreen.delta(-12 * MIN));
        assertEquals("−1:02:03", CatchupScreen.delta(-3_723_000L));
    }

    @Test public void whatIsAskedOfTheArchiveForAMove() {
        long t = EIGHT + 5 * MIN + 25 * S, current = EIGHT + 5 * MIN + 10 * S;
        // A stream that can be moved in: from t's minute on an Xtream server, the picture moved on 25 seconds.
        assertArrayEquals(new long[]{EIGHT + 5 * MIN, 25 * S}, Catchup.ask(Catchup.XC, t, current, 1, true));
        assertArrayEquals(new long[]{EIGHT + 4 * MIN, 55 * S}, Catchup.ask(Catchup.XC, EIGHT + 4 * MIN + 55 * S, current, -1, true));
        // To the second on the other kinds.
        assertArrayEquals(new long[]{t, 400}, Catchup.ask(Catchup.SHIFT, t + 400, current, 1, true));
        // One that can't be moved in plays from where it is asked for: on, never behind where it plays (the next minute).
        assertArrayEquals(new long[]{EIGHT + 6 * MIN, 0}, Catchup.ask(Catchup.XC, t, current, 1, false));
        assertArrayEquals(new long[]{EIGHT + 4 * MIN, 0}, Catchup.ask(Catchup.XC, EIGHT + 4 * MIN + 55 * S, current, -1, false));
        // The address for a move back across 20:00 into the programme before: from 19:59, 35 minutes (to past its end).
        long[] a = Catchup.ask(Catchup.XC, EIGHT - 10 * S, EIGHT + 20 * S, -1, true);
        assertArrayEquals(new long[]{EIGHT - MIN, 50 * S}, a);
        long end = Catchup.requestEnd(a[0], EIGHT);
        assertEquals("http://box.example:8080/timeshift/viewer/made-up/35/2026-11-16:19-59/101.ts",
                Catchup.url(Catchup.XC, "", LIVE_URL, "Europe/London", a[0], end, EIGHT + 3 * 3600_000L));
    }
}
