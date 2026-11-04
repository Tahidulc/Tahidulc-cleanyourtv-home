package com.gathin.tvhomephone.watch;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Calendar;
import org.junit.Test;

public class PlanTest {
    @Test public void rampSteps() {
        assertEquals(0, Plan.step(7.6, 7.6, 9.4));
        assertEquals(4, Plan.step(9.4, 7.6, 9.4));
        assertEquals(2, Plan.step(8.5, 7.6, 9.4));
        // A narrow range is widened to a point either way of its middle.
        assertEquals(2, Plan.step(8.0, 7.9, 8.1));
    }

    @Test public void bingePlan() {
        LocalDate wed = LocalDate.of(2026, 11, 4);
        assertEquals(4, Plan.nights(7, 2));
        assertEquals(LocalDate.of(2026, 11, 7), Plan.doneOn(wed, 7, 2));
        assertEquals(LocalDate.of(2026, 11, 6), Plan.doneOn(wed, 7, 3));
        assertEquals(LocalDate.of(2026, 11, 10), Plan.doneOn(wed, 7, 1));
        assertEquals(101, Plan.minutes(Arrays.asList(56, null, 0), 2));
    }

    @Test public void bedtime() {
        assertEquals(63, Plan.spare(21 * 60 + 57, 23 * 60));
        assertEquals(45, Plan.spare(23 * 60 + 45, 30));
        assertEquals(-7, Plan.spare(23 * 60 + 7, 23 * 60));
        assertEquals("21:57", Plan.hm(21 * 60 + 57));
        assertEquals("00:15", Plan.hm(24 * 60 + 15));
        assertEquals("1h 57m", Plan.length(117));
    }

    @Test public void dayLabels() {
        LocalDate wed = LocalDate.of(2026, 11, 4);
        assertEquals("TODAY", Plan.day(wed, wed));
        assertEquals("TOMORROW", Plan.day(wed.plusDays(1), wed));
        assertEquals("FRI 6", Plan.day(wed.plusDays(2), wed));
        assertEquals("Sat 7 Nov", Plan.date(LocalDate.of(2026, 11, 7)));
    }

    @Test public void filmNightStarts() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.NOVEMBER, 4, 19, 42, 10);
        long[] s = Plan.starts(c.getTimeInMillis());
        Calendar first = Calendar.getInstance();
        first.setTimeInMillis(s[0]);
        assertEquals(20, first.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, first.get(Calendar.MINUTE));
        assertEquals(90 * 60_000L, s[3] - s[0]);
        c.set(2026, Calendar.NOVEMBER, 4, 19, 10, 0);
        first.setTimeInMillis(Plan.starts(c.getTimeInMillis())[0]);
        assertEquals(19, first.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, first.get(Calendar.MINUTE));
    }
}
