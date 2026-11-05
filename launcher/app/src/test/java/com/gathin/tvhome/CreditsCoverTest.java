package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** The credits cover's sums: stars to ratings, the time left, a series' next film, a scene after the credits, extras, the next episode. */
public class CreditsCoverTest {
    @Test public void starsBecomeTheLaunchersRatings() {
        assertEquals(WatchRules.NOT_FOR_ME, CreditsCover.rating(1));
        assertEquals(WatchRules.NOT_FOR_ME, CreditsCover.rating(2));
        assertEquals(WatchRules.LIKED, CreditsCover.rating(3));
        assertEquals(WatchRules.LIKED, CreditsCover.rating(4));
        assertEquals(WatchRules.LOVED, CreditsCover.rating(5));
        assertEquals("Loved it", CreditsCover.starWord(5));
        assertEquals("", CreditsCover.starWord(0));
    }

    @Test public void theTimeLeftAndTheLength() {
        assertEquals("6:12", CreditsCover.clock(372_000L));
        assertEquals("0:05", CreditsCover.clock(5_400L));
        assertEquals("0:00", CreditsCover.clock(-3_000L));
        assertEquals("1:02:30", CreditsCover.clock(3_750_000L));
        assertEquals("2h 36m", CreditsCover.runtime(156));
        assertEquals("49m", CreditsCover.runtime(49));
        assertEquals("", CreditsCover.runtime(0));
    }

    @Test public void theSeriesNextFilmByDate() {
        List<String[]> parts = Arrays.asList(new String[]{"3", ""}, new String[]{"1", "2021-10-22"}, new String[]{"2", "2024-03-01"});
        assertEquals(2, CreditsCover.nextPart(parts, 1));
        // The undated one comes last, after the newest.
        assertEquals(0, CreditsCover.nextPart(parts, 2));
        assertEquals(-1, CreditsCover.nextPart(parts, 3));
        assertEquals(-1, CreditsCover.nextPart(parts, 99));
    }

    @Test public void aSceneInTheCreditsByKeyword() {
        assertEquals(2, CreditsCover.stingerOf(179430, "aftercreditsstinger"));
        assertEquals(2, CreditsCover.stingerOf(0, "AfterCreditsStinger"));
        assertEquals(1, CreditsCover.stingerOf(179431, "duringcreditsstinger"));
        assertEquals(0, CreditsCover.stingerOf(1, "space"));
        assertEquals(0, CreditsCover.stingerOf(0, null));
    }

    @Test public void theCoverLiftsAtTheEndOrForTheScene() {
        long dur = 9_000_000L;
        assertFalse(CreditsCover.lifts(dur, dur - 300_000L, 0));
        assertTrue(CreditsCover.lifts(dur, dur - 500L, 0));
        // A scene after the credits: lifted two and a half minutes before the end, so it is seen.
        assertTrue(CreditsCover.lifts(dur, dur - CreditsCover.STINGER_MS, 2));
        assertFalse(CreditsCover.lifts(dur, dur - CreditsCover.STINGER_MS - 1_000L, 2));
        assertFalse(CreditsCover.lifts(0, 10_000L, 0));
    }

    @Test public void extrasFeaturettesFirstYouTubeOnly() {
        List<String[]> v = Arrays.asList(
                new String[]{"YouTube", "Trailer", "t1"},
                new String[]{"Vimeo", "Featurette", "v1"},
                new String[]{"YouTube", "Featurette", "f1"},
                new String[]{"YouTube", "Teaser", "s1"},
                new String[]{"YouTube", "Featurette", ""});
        assertEquals(Arrays.asList(2, 3, 0), CreditsCover.extraOrder(v, 3));
        assertEquals(Arrays.asList(2), CreditsCover.extraOrder(v, 1));
    }

    @Test public void whenTheNextEpisodeWouldEnd() {
        assertEquals(23 * 60 + 47, CreditsCover.endsAt(22 * 60 + 58, 49));
        assertEquals(40, CreditsCover.endsAt(23 * 60 + 51, 49));
        assertEquals(-1, CreditsCover.endsAt(22 * 60, 0));
        int bed = 23 * 60 + 30;
        assertEquals("ends 23:47 · 17 min past bed", CreditsCover.endsLine(22 * 60 + 58, 49, bed));
        assertEquals("ends 22:49 · 41 min before bed", CreditsCover.endsLine(22 * 60, 49, bed));
        assertEquals("ends 23:30 · right at bedtime", CreditsCover.endsLine(22 * 60 + 41, 49, bed));
        // Far from bed, bed isn't said; past midnight still counts as the same night.
        assertEquals("ends 15:49", CreditsCover.endsLine(15 * 60, 49, bed));
        assertEquals("ends 00:40 · 1h 10m past bed", CreditsCover.endsLine(23 * 60 + 51, 49, bed));
        assertEquals("", CreditsCover.endsLine(22 * 60, 0, bed));
    }

    @Test public void theSeasonLeftAndTheAirDate() {
        assertEquals("3 left this season", CreditsCover.leftLine(7, 10));
        assertEquals("The season finale is next", CreditsCover.leftLine(9, 10));
        assertEquals("", CreditsCover.leftLine(10, 10));
        assertEquals("", CreditsCover.leftLine(0, 10));
        assertEquals("Thu 12 Nov", CreditsCover.dayOf("2026-11-12"));
        assertEquals("", CreditsCover.dayOf(""));
        assertEquals("", CreditsCover.dayOf("soon"));
        assertEquals("12 Mar 2027", CreditsCover.longDay("2027-03-12"));
        assertEquals("2027", CreditsCover.longDay("2027"));
        assertTrue(CreditsCover.isOut("2026-11-05", "2026-11-05"));
        assertTrue(CreditsCover.isOut("2025-10-12", "2026-11-05"));
        assertFalse(CreditsCover.isOut("2026-11-12", "2026-11-05"));
        assertFalse(CreditsCover.isOut("", "2026-11-05"));
    }
}
