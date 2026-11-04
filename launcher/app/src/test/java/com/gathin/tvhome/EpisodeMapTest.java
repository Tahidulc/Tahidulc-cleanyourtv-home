package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.Test;

public class EpisodeMapTest {
    @Test
    public void ratingsSpreadOverTheSeriesOwnRange() {
        assertEquals(-1, EpisodeMap.step(0, 7.6, 9.4));
        assertEquals(0, EpisodeMap.step(7.6, 7.6, 9.4));
        assertEquals(4, EpisodeMap.step(9.4, 7.6, 9.4));
        assertEquals(2, EpisodeMap.step(8.5, 7.6, 9.4));
        // A narrow range is taken as a point wide, so 8.0 to 8.3 do not look worlds apart.
        assertEquals(0, EpisodeMap.step(8.0, 8.0, 8.3));
        assertEquals(1, EpisodeMap.step(8.3, 8.0, 8.3));
        for (double v = 5; v <= 10; v += 0.1) {
            int s = EpisodeMap.step(v, 6, 9);
            assertTrue(s >= 0 && s < EpisodeMap.RAMP.length);
        }
    }

    @Test
    public void watchedIsEverythingBeforeTheFurthestOne() {
        int[] at = {2, 3, 0};
        assertTrue(EpisodeMap.watchedBefore(at, 1, 10));
        assertTrue(EpisodeMap.watchedBefore(at, 2, 2));
        assertFalse(EpisodeMap.watchedBefore(at, 2, 3));
        assertTrue(EpisodeMap.watchedBefore(new int[]{2, 3, 1}, 2, 3));
        assertFalse(EpisodeMap.watchedBefore(at, 3, 1));
        assertFalse(EpisodeMap.watchedBefore(null, 1, 1));
    }

    @Test
    public void bingePlanDays() {
        LocalDate wed = LocalDate.of(2026, 11, 4);
        // Seven left, two a night from tonight: Wed, Thu, Fri, Sat.
        assertEquals(LocalDate.of(2026, 11, 7), EpisodeMap.doneOn(7, 2, wed, true));
        assertEquals(LocalDate.of(2026, 11, 6), EpisodeMap.doneOn(7, 3, wed, true));
        assertEquals(LocalDate.of(2026, 11, 10), EpisodeMap.doneOn(7, 1, wed, true));
        // Tonight gone: from tomorrow.
        assertEquals(LocalDate.of(2026, 11, 8), EpisodeMap.doneOn(7, 2, wed, false));
        assertEquals("done tonight", EpisodeMap.doneWords(wed, wed));
        assertEquals("done tomorrow", EpisodeMap.doneWords(wed.plusDays(1), wed));
        assertEquals("done Sat 7 Nov", EpisodeMap.doneWords(LocalDate.of(2026, 11, 7), wed));
        assertEquals("6h 30m", EpisodeMap.hm(390));
        assertEquals("2h", EpisodeMap.hm(120));
        assertEquals("45m", EpisodeMap.hm(45));
        assertEquals("21:38", EpisodeMap.clock(21 * 60 + 38));
        assertEquals("00:15", EpisodeMap.clock(24 * 60 + 15));
    }

    @Test
    public void nextAndLeftFromTheSample() {
        LocalDate today = LocalDate.of(2026, 11, 4);
        List<List<EpisodeMap.Ep>> s = EpisodeMap.sample(today);
        EpisodeMap.Ep next = EpisodeMap.next(s, today);
        assertEquals(2, next.season);
        assertEquals(4, next.number);
        List<EpisodeMap.Ep> left = EpisodeMap.left(s, next, today);
        assertEquals(7, left.size());
        assertEquals("E4 and E5", EpisodeMap.names(left.subList(0, 2)));
        assertEquals("E4, E5 and E6", EpisodeMap.names(left.subList(0, 3)));
        double[] r = EpisodeMap.range(s, today);
        assertEquals(7.6, r[0], 1e-9);
        assertEquals(9.4, r[1], 1e-9);
        for (List<EpisodeMap.Ep> season : s) for (EpisodeMap.Ep e : season) e.watched = true;
        assertNull(EpisodeMap.next(s, today));
    }
}
