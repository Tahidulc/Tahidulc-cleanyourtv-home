package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

public class ComingUpTest {
    private static final LocalDate WED = LocalDate.of(2026, 11, 4);

    @Test
    public void countdownWords() {
        assertEquals("TODAY", ComingUp.countdown(WED, WED));
        assertEquals("TOMORROW", ComingUp.countdown(WED.plusDays(1), WED));
        assertEquals("IN 2 DAYS", ComingUp.countdown(WED.plusDays(2), WED));
        assertEquals("IN A WEEK", ComingUp.countdown(WED.plusDays(7), WED));
        assertEquals("IN 10 DAYS", ComingUp.countdown(WED.plusDays(10), WED));
        assertEquals("IN 2 WEEKS", ComingUp.countdown(WED.plusDays(14), WED));
        assertEquals("THU 5", ComingUp.dayShort(WED.plusDays(1)));
    }

    @Test
    public void theFortnightInTwoWeeksFromTomorrow() {
        assertEquals(-1, ComingUp.week(WED, WED));
        assertEquals(0, ComingUp.week(WED.plusDays(1), WED));
        assertEquals(0, ComingUp.week(WED.plusDays(7), WED));
        assertEquals(1, ComingUp.week(WED.plusDays(8), WED));
        assertEquals(1, ComingUp.week(WED.plusDays(14), WED));
        assertEquals(-1, ComingUp.week(WED.plusDays(15), WED));
        assertTrue(ComingUp.ahead(WED.plusDays(3), WED));
        assertFalse(ComingUp.ahead(WED.minusDays(1), WED));
        assertFalse(ComingUp.ahead(null, WED));
    }
}
