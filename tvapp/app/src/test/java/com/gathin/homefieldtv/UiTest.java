package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;

import com.gathin.homefieldtv.prefs.Look;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Times and lengths written as the guide writes them. */
public class UiTest {
    private static long at(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Test public void clockTimes() {
        int was = Look.clock;
        try {
            // As it comes: 24-hour, the date and the clock as the family's other app shows them.
            Look.clock = Look.CLOCK_24;
            assertEquals("15:00", Ui.clock(at(2026, 11, 4, 15, 0)));
            assertEquals("09:07", Ui.clock(at(2026, 11, 4, 9, 7)));
            assertEquals("Wed, 4 Nov, 10:31", Ui.dateClock(at(2026, 11, 4, 10, 31)));
            // Settings → Appearance → Clock: 12-hour.
            Look.clock = Look.CLOCK_12;
            assertEquals("3.00pm", Ui.clock(at(2026, 11, 4, 15, 0)));
            assertEquals("12.05am", Ui.clock(at(2026, 11, 4, 0, 5)));
            assertEquals("12.30pm", Ui.clock(at(2026, 11, 4, 12, 30)));
            assertEquals("9.07am", Ui.clock(at(2026, 11, 4, 9, 7)));
            assertEquals("Wednesday, 3.01pm", Ui.dayClock(at(2026, 11, 4, 15, 1)));
            assertEquals("3.00pm - 4.30pm", Ui.span(at(2026, 11, 4, 15, 0), at(2026, 11, 4, 16, 30)));
            // Off: the date alone.
            Look.clock = Look.CLOCK_OFF;
            assertEquals("Wed, 4 Nov", Ui.dateClock(at(2026, 11, 4, 10, 31)));
        } finally {
            Look.clock = was;
        }
    }

    @Test public void days() {
        long now = at(2026, 11, 4, 20, 0);
        assertEquals("Today", Ui.day(at(2026, 11, 4, 23, 0), now));
        assertEquals("Tomorrow", Ui.day(at(2026, 11, 5, 1, 0), now));
        assertEquals("Friday", Ui.day(at(2026, 11, 6, 9, 0), now));
        assertEquals("Fri 20 Nov", Ui.day(at(2026, 11, 20, 9, 0), now));
    }

    @Test public void lengths() {
        assertEquals("1h 56m", Ui.duration(116));
        assertEquals("45m", Ui.duration(45));
        assertEquals("2h", Ui.duration(120));
        assertEquals("", Ui.duration(0));
        assertEquals("25m left", Ui.left(25 * 60_000L));
        assertEquals("1h 2m left", Ui.left(62 * 60_000L));
    }
}
