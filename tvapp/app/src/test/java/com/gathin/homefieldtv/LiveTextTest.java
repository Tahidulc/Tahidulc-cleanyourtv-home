package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Live TV's words: times, how far through and what's left, when one starts, the player's picture, a name's HD or 4K. */
public class LiveTextTest {
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final long MIN = 60_000L;

    private static long at(int h, int m) {
        return ZonedDateTime.of(2026, 11, 16, h, m, 0, 0, UTC).toInstant().toEpochMilli();
    }

    @Test public void twentyFourHourTimes() {
        assertEquals("17:40", LiveText.hm(at(17, 40), UTC));
        assertEquals("09:05", LiveText.hm(at(9, 5), UTC));
        assertEquals("00:00", LiveText.hm(at(0, 0), UTC));
        assertEquals("17:40 – 18:45", LiveText.span(at(17, 40), at(18, 45), UTC));
        // British Summer Time is an hour on.
        long july = ZonedDateTime.of(2026, 7, 1, 17, 40, 0, 0, UTC).toInstant().toEpochMilli();
        assertEquals("18:40", LiveText.hm(july, ZoneId.of("Europe/London")));
    }

    @Test public void catchUpsTimes() {
        assertEquals("17:40:00", LiveText.hms(at(17, 40), UTC));
        assertEquals("09:05:30", LiveText.hms(at(9, 5) + 30_000L, UTC));
        assertEquals("Live", LiveText.behind(0));
        assertEquals("Live", LiveText.behind(4_000L));
        assertEquals("−0:05 behind live", LiveText.behind(5_000L));
        assertEquals("−12:34 behind live", LiveText.behind(12 * MIN + 34_000L));
        assertEquals("−1:02:03 behind live", LiveText.behind(62 * MIN + 3_000L));
        assertEquals("Live", LiveText.behind(-3_000L));
    }

    @Test public void howFarThrough() {
        assertEquals(0f, LiveText.progress(at(17, 40), at(17, 40), at(18, 40)), 0.0001f);
        assertEquals(0.5f, LiveText.progress(at(18, 10), at(17, 40), at(18, 40)), 0.0001f);
        assertEquals(1f, LiveText.progress(at(19, 0), at(17, 40), at(18, 40)), 0.0001f);
        assertEquals(0f, LiveText.progress(at(17, 0), at(17, 40), at(18, 40)), 0.0001f);
        assertEquals(0f, LiveText.progress(at(17, 0), at(17, 40), at(17, 40)), 0.0001f);
    }

    @Test public void whatIsLeftAndWhenItStarts() {
        assertEquals("58 min left", LiveText.left(at(17, 47), at(18, 45)));
        assertEquals("1 min left", LiveText.left(at(18, 44) + 30_000L, at(18, 45)));
        assertEquals("1h 5m left", LiveText.left(at(17, 40), at(18, 45)));
        assertEquals("2h left", LiveText.left(at(16, 45), at(18, 45)));
        assertEquals("", LiveText.left(at(18, 45), at(18, 45)));
        assertEquals("Starts in 12 min", LiveText.startsIn(at(18, 33), at(18, 45)));
        assertEquals("Starts in 1h 15m", LiveText.startsIn(at(17, 30), at(18, 45)));
        assertEquals("", LiveText.startsIn(at(18, 50), at(18, 45)));
        assertEquals("1 min", LiveText.mins(MIN / 2));
    }

    @Test public void thePlayersPicture() {
        assertEquals("1280×720", LiveText.size(1280, 720));
        assertEquals("1920×1080", LiveText.size(1920, 1080));
        assertEquals("", LiveText.size(0, 720));
        assertEquals("", LiveText.size(-1, -1));
        assertEquals("25 fps", LiveText.fps(25f));
        assertEquals("50 fps", LiveText.fps(50.0f));
        assertEquals("29.97 fps", LiveText.fps(29.97f));
        assertEquals("23.98 fps", LiveText.fps(23.976f));
        assertEquals("", LiveText.fps(-1f));
        assertEquals("", LiveText.fps(Float.NaN));
        assertEquals("", LiveText.fps(0f));
    }

    @Test public void thePictureANamePromises() {
        assertEquals("4K", LiveText.quality("Sky Sports Main Event UHD"));
        assertEquals("4K", LiveText.quality("UK: SKY SPORTS F1 4K"));
        assertEquals("HD", LiveText.quality("BBC One HD"));
        assertEquals("HD", LiveText.quality("|FR| TV BREIZH FHD"));
        assertEquals("HD", LiveText.quality("ITV1 (1080p)"));
        assertEquals("", LiveText.quality("Dave"));
        assertEquals("", LiveText.quality("Shadow"));
        assertEquals("", LiveText.quality(null));
    }

    @Test public void theAnimationEases() {
        assertEquals(0f, Fade.ease(0f), 0.0001f);
        assertEquals(1f, Fade.ease(1f), 0.0001f);
        assertEquals(1f, Fade.ease(2f), 0.0001f);
        assertTrue(Fade.ease(0.5f) > 0.5f);
        assertTrue(Fade.ease(0.25f) < Fade.ease(0.5f));
        assertNotEquals(Dark.ACCENT_SOFT, Dark.ACCENT);
    }
}
