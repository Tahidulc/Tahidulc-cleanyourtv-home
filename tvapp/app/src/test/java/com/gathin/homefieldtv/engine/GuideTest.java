package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GuideTest {
    @Test public void xmltvTimes() {
        long utc = Guide.time("20261104193000 +0000");
        assertEquals(Guide.time("20261104203000 +0100"), utc);
        assertEquals(Guide.time("20261104143000 -0500"), utc);
        assertEquals(Guide.time("20261104193000"), utc);
        assertEquals(0, Guide.time("nonsense"));
    }

    @Test public void catchUpChannelsKeepTheirPast() {
        java.util.Map<String, Long> since = new java.util.HashMap<>();
        since.put("x:1", 1000L);
        since.put("x:2", 500L);
        // A channel with catch-up keeps what its archive does; one without, from the usual hour back.
        assertEquals(1000L, Guide.earliest(java.util.Arrays.asList("x:1"), 5000L, since));
        assertEquals(5000L, Guide.earliest(java.util.Arrays.asList("m:3"), 5000L, since));
        // The guide's programme shared by several channels: kept as far back as any of them needs.
        assertEquals(500L, Guide.earliest(java.util.Arrays.asList("m:3", "x:1", "x:2"), 5000L, since));
        assertEquals(5000L, Guide.earliest(java.util.Arrays.asList("x:1"), 5000L, null));
        // An archive never shortens the usual hour.
        since.put("x:4", 9000L);
        assertEquals(5000L, Guide.earliest(java.util.Arrays.asList("x:4"), 5000L, since));
    }

    @Test public void nowAndNext() {
        Guide.Builder b = new Guide.Builder();
        b.add(2000, 3000, "B");
        b.add(1000, 2000, "A");
        b.add(3000, 4000, "C");
        Guide.Schedule s = b.build();
        assertEquals("A", s.title[s.at(1500)]);
        assertEquals("B", s.title[s.at(2000)]);
        assertEquals(-1, s.at(500));
        assertEquals(-1, s.at(4500));
        assertEquals("C", s.title[s.after(2500)]);
        assertEquals(-1, s.after(3500));
    }
}
