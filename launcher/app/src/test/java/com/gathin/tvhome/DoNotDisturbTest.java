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

    @Test public void okHeldOnAPopUpIsQuietForAnHour() {
        long now = at(2026, 11, 4, 21, 45);
        long[] dnd = DoNotDisturb.held(DoNotDisturb.OFF, 0, now);
        assertEquals(DoNotDisturb.HOUR, dnd[0]);
        assertEquals(now + 3_600_000L, dnd[1]);
        String[] words = DoNotDisturb.heldWords(true, dnd[1], LONDON);
        assertEquals("Quiet for 1 hour", words[0]);
        assertEquals("Do not disturb until 22:45 · the Control Centre turns it off", words[1]);
    }

    @Test public void okHeldAgainStartsTheHourAgain() {
        long now = at(2026, 11, 4, 21, 45);
        long[] dnd = DoNotDisturb.held(DoNotDisturb.HOUR, now + 20 * 60_000L, now);
        assertEquals(DoNotDisturb.HOUR, dnd[0]);
        assertEquals(now + 3_600_000L, dnd[1]);
    }

    @Test public void okHeldKeepsALongerQuiet() {
        long now = at(2026, 11, 4, 21, 45);
        long morning = at(2026, 11, 5, 7, 0);
        long[] dnd = DoNotDisturb.held(DoNotDisturb.MORNING, morning, now);
        assertEquals(DoNotDisturb.MORNING, dnd[0]);
        assertEquals(morning, dnd[1]);
        assertEquals("Already quiet", DoNotDisturb.heldWords(false, morning, LONDON)[0]);
        assertEquals("Do not disturb until 7:00", DoNotDisturb.heldWords(false, morning, LONDON)[1]);
        long[] always = DoNotDisturb.held(DoNotDisturb.ALWAYS, Long.MAX_VALUE, now);
        assertEquals(Long.MAX_VALUE, always[1]);
        assertEquals("Do not disturb until you turn it off", DoNotDisturb.heldWords(false, Long.MAX_VALUE, LONDON)[1]);
    }

    @Test public void anEndedQuietIsTreatedAsOff() {
        long now = at(2026, 11, 4, 21, 45);
        long[] dnd = DoNotDisturb.held(DoNotDisturb.MORNING, now - 1, now);
        assertEquals(DoNotDisturb.HOUR, dnd[0]);
        assertEquals(now + 3_600_000L, dnd[1]);
    }
}
