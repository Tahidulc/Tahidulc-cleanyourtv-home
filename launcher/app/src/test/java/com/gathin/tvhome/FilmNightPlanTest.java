package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.Test;

public class FilmNightPlanTest {
    private static LocalDateTime at(int h, int m) {
        return LocalDateTime.of(2026, 11, 4, h, m);
    }

    @Test
    public void startTimesAreTheNextFourHalfHours() {
        List<LocalDateTime> c = FilmNightPlan.choices(at(19, 42));
        assertEquals(4, c.size());
        assertEquals(at(20, 0), c.get(0));
        assertEquals(at(21, 30), c.get(3));
        // Under five minutes to go: that half hour is skipped.
        assertEquals(at(20, 30), FilmNightPlan.choices(at(19, 57)).get(0));
        assertEquals(at(20, 0), FilmNightPlan.choices(at(19, 55)).get(0));
        // Past midnight: the next day's times.
        assertEquals(LocalDateTime.of(2026, 11, 5, 0, 0), FilmNightPlan.choices(at(23, 50)).get(0));
    }

    @Test
    public void spareTimeIsCountedToThatEveningsBed() {
        // Verity, 1h 57m, at 20:00: ends 21:57, 63 minutes before bed at 23:00.
        assertEquals(63, FilmNightPlan.spare(at(20, 0), 117, 23 * 60));
        assertEquals("63 min spare", FilmNightPlan.spareWords(63));
        assertEquals("63 min before bed", FilmNightPlan.bedWords(63));
        // At 21:00 it ends at 22:57: three minutes, in amber.
        long late = FilmNightPlan.spare(at(21, 0), 117, 23 * 60);
        assertEquals(3, late);
        assertTrue(FilmNightPlan.tight(late));
        assertFalse(FilmNightPlan.tight(63));
        // Past bed, and bed after midnight (00:30 is 1470): a start at 00:15 belongs to the evening before.
        assertEquals(-12, FilmNightPlan.spare(at(21, 15), 117, 23 * 60));
        assertEquals("12 min past bed", FilmNightPlan.spareWords(-12));
        assertEquals(-102, FilmNightPlan.spare(LocalDateTime.of(2026, 11, 5, 0, 15), 117, 24 * 60 + 30));
        assertEquals("65 min spare", FilmNightPlan.spareWords(65));
        assertEquals("2 h 5 min past bed", FilmNightPlan.spareWords(-125));
        assertEquals("Right on bedtime", FilmNightPlan.spareWords(0));
    }

    @Test
    public void theReminderComesTenMinutesBeforeOnce() {
        long start = 1_000_000_000L;
        assertFalse(FilmNightPlan.remindDue(start - 11 * 60_000L, start, false));
        assertTrue(FilmNightPlan.remindDue(start - 10 * 60_000L, start, false));
        assertTrue(FilmNightPlan.remindDue(start + 5 * 60_000L, start, false));
        assertFalse(FilmNightPlan.remindDue(start - 5 * 60_000L, start, true));
        assertFalse(FilmNightPlan.remindDue(start + 20 * 60_000L, start, false));
    }

    @Test
    public void popUpsWaitFromTheStartToTenMinutesAfterTheEndOrTheCredits() {
        long start = 1_000_000_000L, end = start + 117 * 60_000L;
        assertFalse(FilmNightPlan.quiet(start - 1, start, end, 0));
        assertTrue(FilmNightPlan.quiet(start, start, end, 0));
        assertTrue(FilmNightPlan.quiet(end + 9 * 60_000L, start, end, 0));
        assertFalse(FilmNightPlan.quiet(end + 10 * 60_000L, start, end, 0));
        // The credits came twenty minutes early: quiet until ten minutes after them.
        long credits = end - 20 * 60_000L;
        assertEquals(credits + 10 * 60_000L, FilmNightPlan.quietUntil(start, end, credits));
        assertFalse(FilmNightPlan.quiet(credits + 11 * 60_000L, start, end, credits));
        // Credits seen before the start belong to some other film.
        assertEquals(end + 10 * 60_000L, FilmNightPlan.quietUntil(start, end, start - 1));
        assertTrue(FilmNightPlan.over(end + 71 * 60_000L, start, end, 0));
        assertFalse(FilmNightPlan.over(end + 60 * 60_000L, start, end, 0));
    }
}
