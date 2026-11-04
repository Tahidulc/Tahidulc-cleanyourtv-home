package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

public class TimelineTest {
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final TimeZone TZ = TimeZone.getTimeZone(LONDON);

    private static long at(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(LONDON).toInstant().toEpochMilli();
    }

    @Test public void timeAndXRoundTrip() {
        long origin = at(2026, 11, 4, 18, 0);
        Timeline tl = new Timeline(origin, origin + 12 * 3600_000L, 4f);
        assertEquals(0f, tl.x(origin), 0.001f);
        assertEquals(120f, tl.x(origin + Timeline.HALF_HOUR), 0.001f);
        assertEquals(12 * 60 * 4f, tl.width(), 0.01f);
        long t = at(2026, 11, 4, 21, 17);
        assertEquals(t, tl.time(tl.x(t)));
        assertEquals(origin + 15 * 60_000L, tl.time(60f));
    }

    @Test public void halfHoursAreLocal() {
        assertEquals(at(2026, 11, 4, 19, 30), Timeline.floorHalfHour(at(2026, 11, 4, 19, 42), TZ));
        assertEquals(at(2026, 11, 4, 19, 30), Timeline.floorHalfHour(at(2026, 11, 4, 19, 30), TZ));
        assertEquals(at(2026, 11, 4, 20, 0), Timeline.ceilHalfHour(at(2026, 11, 4, 19, 42), TZ));
        // British Summer Time: still on the local half hour.
        assertEquals(at(2026, 7, 1, 9, 0), Timeline.floorHalfHour(at(2026, 7, 1, 9, 29), TZ));
        // A zone a quarter of an hour off the hour.
        TimeZone nepal = TimeZone.getTimeZone("Asia/Kathmandu");
        long t = LocalDateTime.of(2026, 11, 4, 19, 50).atZone(nepal.toZoneId()).toInstant().toEpochMilli();
        long want = LocalDateTime.of(2026, 11, 4, 19, 30).atZone(nepal.toZoneId()).toInstant().toEpochMilli();
        assertEquals(want, Timeline.floorHalfHour(t, nepal));
    }

    @Test public void spanStartsAtTheHalfHourAndReachesTheGuidesEnd() {
        long now = at(2026, 11, 4, 19, 42);
        Timeline tl = Timeline.span(now - 3600_000L, at(2026, 11, 5, 7, 10), now, 4f, TZ);
        assertEquals(at(2026, 11, 4, 18, 30), tl.origin);
        assertEquals(at(2026, 11, 5, 7, 30), tl.end);
        // No guide: two hours on from now at least.
        Timeline bare = Timeline.span(now, now, now, 4f, TZ);
        assertEquals(at(2026, 11, 4, 19, 30), bare.origin);
        assertEquals(at(2026, 11, 4, 22, 0), bare.end);
    }

    @Test public void offsetStaysInsideTheGrid() {
        Timeline tl = new Timeline(0, 4 * 3600_000L, 4f); // 960 px wide
        assertEquals(0f, tl.clamp(-50f, 300f), 0f);
        assertEquals(660f, tl.clamp(5000f, 300f), 0f);
        assertEquals(200f, tl.clamp(200f, 300f), 0f);
        assertEquals(0f, tl.clamp(100f, 2000f), 0f);
        assertEquals(0f, tl.clamp(Float.NaN, 300f), 0f);
    }

    @Test public void ticksAndDays() {
        long a = at(2026, 11, 4, 22, 10);
        List<Long> ticks = Timeline.ticks(a, at(2026, 11, 5, 0, 30), TZ);
        assertEquals(5, ticks.size());
        assertEquals(at(2026, 11, 4, 22, 30), (long) ticks.get(0));
        assertEquals(at(2026, 11, 5, 0, 30), (long) ticks.get(4));
        Timeline tl = new Timeline(at(2026, 11, 4, 18, 30), at(2026, 11, 5, 7, 30), 4f);
        List<Long> days = tl.days(LONDON);
        assertEquals(2, days.size());
        assertEquals(at(2026, 11, 4, 0, 0), (long) days.get(0));
        assertEquals(at(2026, 11, 5, 0, 0), (long) days.get(1));
        long now = at(2026, 11, 4, 19, 42);
        assertEquals(now, tl.dayTarget(days.get(0), now));
        assertEquals(at(2026, 11, 5, 0, 0), tl.dayTarget(days.get(1), now));
    }

    @Test public void firstProgrammeInView() {
        Guide.Builder b = new Guide.Builder();
        b.add(1000, 2000, "A");
        b.add(2000, 9000, "Film");
        b.add(3000, 4000, "Filler");
        b.add(9000, 10000, "C");
        Guide.Schedule s = b.build();
        assertEquals(0, Timeline.first(s, 500));
        assertEquals(0, Timeline.first(s, 1500));
        assertEquals(1, Timeline.first(s, 2500));
        // The film started earlier and is still on behind the filler.
        assertEquals(1, Timeline.first(s, 3500));
        assertEquals(3, Timeline.first(s, 9500));
        assertTrue(Timeline.first(s, 20000) >= s.start.length);
    }
}
