package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class DoNotDisturbTest {
    private static final TimeZone LONDON = TimeZone.getTimeZone("Europe/London");

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(LONDON, Locale.UK);
        c.clear();
        c.set(year, month - 1, day, hour, minute, 0);
        return c.getTimeInMillis();
    }

    @Test public void theTileStepsRoundAndBack() {
        assertEquals(DoNotDisturb.HOUR, DoNotDisturb.next(DoNotDisturb.OFF));
        assertEquals(DoNotDisturb.MORNING, DoNotDisturb.next(DoNotDisturb.HOUR));
        assertEquals(DoNotDisturb.ALWAYS, DoNotDisturb.next(DoNotDisturb.MORNING));
        assertEquals(DoNotDisturb.OFF, DoNotDisturb.next(DoNotDisturb.ALWAYS));
    }

    @Test public void anHourFromNow() {
        long now = at(2026, 11, 4, 20, 45);
        long until = DoNotDisturb.until(DoNotDisturb.HOUR, now, LONDON);
        assertEquals(now + 3_600_000L, until);
        assertEquals("On until 21:45", DoNotDisturb.words(DoNotDisturb.HOUR, until, now, LONDON));
    }

    @Test public void untilTheMorningIsTomorrowAtSevenInTheEvening() {
        long now = at(2026, 11, 4, 20, 45);
        long until = DoNotDisturb.until(DoNotDisturb.MORNING, now, LONDON);
        assertEquals(at(2026, 11, 5, 7, 0), until);
        assertEquals("On until 7:00", DoNotDisturb.words(DoNotDisturb.MORNING, until, now, LONDON));
    }

    @Test public void untilTheMorningIsTodayAtSevenInTheSmallHours() {
        long now = at(2026, 11, 5, 2, 10);
        assertEquals(at(2026, 11, 5, 7, 0), DoNotDisturb.until(DoNotDisturb.MORNING, now, LONDON));
    }

    @Test public void untilTheMorningAcrossTheClocksGoingBack() {
        // The clocks go back at 2am on 25 October 2026: seven is still seven.
        long now = at(2026, 10, 24, 23, 0);
        assertEquals(at(2026, 10, 25, 7, 0), DoNotDisturb.until(DoNotDisturb.MORNING, now, LONDON));
    }

    @Test public void alwaysUntilTurnedOffAndOffWhenItsTimeHasPassed() {
        long now = at(2026, 11, 4, 20, 45);
        assertEquals(Long.MAX_VALUE, DoNotDisturb.until(DoNotDisturb.ALWAYS, now, LONDON));
        assertEquals("On until you turn it off", DoNotDisturb.words(DoNotDisturb.ALWAYS, Long.MAX_VALUE, now, LONDON));
        long hour = DoNotDisturb.until(DoNotDisturb.HOUR, now, LONDON);
        assertTrue(DoNotDisturb.on(hour, now));
        assertFalse(DoNotDisturb.on(hour, hour + 1));
        assertEquals(DoNotDisturb.OFF, DoNotDisturb.mode(DoNotDisturb.HOUR, hour, hour + 1));
        assertEquals("Off", DoNotDisturb.words(DoNotDisturb.HOUR, hour, hour + 1, LONDON));
        assertEquals(0, DoNotDisturb.until(DoNotDisturb.OFF, now, LONDON));
    }
}
