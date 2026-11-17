package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** Settings → Guide → Guide time shift: programmes moved into the real time, catch-up's window into the guide's. */
public class GuideShiftTest {
    @Test public void programmesMoveByTheShift() {
        Guide.Schedule s = new Guide.Schedule(new long[]{1000, 2000}, new long[]{2000, 3000}, new String[]{"A", "B"}, new String[]{"", ""});
        Guide.Schedule later = GuideShift.by(s, 3600_000L);
        assertArrayEquals(new long[]{3601_000L, 3602_000L}, later.start);
        assertArrayEquals(new long[]{3602_000L, 3603_000L}, later.stop);
        assertArrayEquals(s.title, later.title);
        // What's on at a time, in the real time.
        assertEquals(0, later.at(3601_500L));
        assertSame(s, GuideShift.by(s, 0));
    }

    @Test public void catchUpsWindowInTheGuidesTime() {
        Map<String, Long> since = new HashMap<>();
        since.put("x:1", 10_000_000L);
        assertEquals(Long.valueOf(10_000_000L - 1800_000L), GuideShift.since(since, 1800_000L).get("x:1"));
        assertSame(since, GuideShift.since(since, 0));
    }
}
