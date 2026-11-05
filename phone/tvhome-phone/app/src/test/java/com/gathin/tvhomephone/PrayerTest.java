package com.gathin.tvhomephone;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** The mosque's timetable read, and the day's prayers worked out from it. */
public class PrayerTest {
    /** A year's table as the mosque's page has it: a date, then eleven times. */
    private static String page(int days) {
        StringBuilder b = new StringBuilder("<div class='top'>Thursday 14th Jumada-Al-Awaal, 1448</div><table>");
        b.append("<tr><th>Date</th><th>Fajr</th></tr>");
        LocalDate d = LocalDate.of(2026, 1, 1);
        String[] mon = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        for (int i = 0; i < days; i++, d = d.plusDays(1)) {
            b.append("<tr><td>").append(mon[d.getMonthValue() - 1]).append(' ').append(String.format("%02d", d.getDayOfMonth())).append("</td>");
            for (String t : new String[]{"6:01", "6:30", "7:22", "11:58", "12:30", "14:05", "14:30", "16:24", "16:29", "17:57", "19:30"}) {
                b.append("<td> ").append(t).append(" </td>");
            }
            b.append("</tr>");
        }
        return b.append("</table>").toString();
    }

    @Test public void theMosquesYearIsRead() throws Exception {
        JSONObject y = Prayer.year(page(365), 2026);
        assertNotNull(y);
        assertEquals(2026, y.getInt("year"));
        assertEquals("06:01", y.getJSONObject("days").getJSONArray("11-05").getString(0));
        assertEquals("19:30", y.getJSONObject("days").getJSONArray("11-05").getString(10));
        // Most of a year or nothing: a changed page never takes the place of a good year.
        assertNull(Prayer.year(page(40), 2026));
    }

    @Test public void theMosquesIslamicDateIsRead() {
        assertArrayEquals(new int[]{14, 5, 1448}, Prayer.hijriOf(page(1)));
        assertEquals(6, Prayer.hijriMonth("Jumada al-Thani"));
        assertEquals(9, Prayer.hijriMonth("Ramadan"));
        assertEquals(0, Prayer.hijriMonth("November"));
        assertEquals("14 Jumada al-Awwal 1448", Prayer.hijri(new int[]{14, 5, 1448}));
    }

    private static final String[][] DAY = {{"06:01", "07:22", "11:58", "14:05", "16:24", "17:57"}, {"06:30", "12:30", "14:30", "16:29", "19:30"}};

    @Test public void theDaysFivePrayers() {
        LocalDate thu = LocalDate.of(2026, 11, 5);
        List<Prayer.Slot> s = Prayer.slots(DAY, DAY, thu);
        assertEquals(5, s.size());
        assertEquals("Fajr", s.get(0).name);
        // Fajr ends at sunrise; Isha at the next day's Fajr.
        assertEquals(LocalDateTime.of(2026, 11, 5, 7, 22), s.get(0).ends);
        assertEquals(LocalDateTime.of(2026, 11, 6, 6, 1), s.get(4).ends);
        assertEquals("Zuhr", s.get(1).label());
        // Friday's Zuhr is Jumu'ah.
        assertEquals("Jumu'ah", Prayer.slots(DAY, DAY, thu.plusDays(1)).get(1).label());
    }

    @Test public void whatsNext() {
        LocalDate d = LocalDate.of(2026, 11, 5);
        List<Prayer.Slot> today = Prayer.slots(DAY, DAY, d), tomorrow = Prayer.slots(DAY, DAY, d.plusDays(1));
        LocalDateTime noon = LocalDateTime.of(d, java.time.LocalTime.of(12, 0));
        // Zuhr's time has begun (11:58), its jamā'ah (12:30) is still to come.
        Prayer.Slot n = Prayer.next(today, tomorrow, noon);
        assertEquals("Zuhr", n.name);
        assertEquals("Zuhr jamā'ah 12:30 · in 31m", Prayer.nextLine(n, noon));
        LocalDateTime one = LocalDateTime.of(d, java.time.LocalTime.of(13, 0));
        assertEquals("Asr 14:05 · in 1h 6m", Prayer.nextLine(Prayer.next(today, tomorrow, one), one));
        // After Isha's jamā'ah: tomorrow's Fajr.
        LocalDateTime late = LocalDateTime.of(d, java.time.LocalTime.of(21, 0));
        Prayer.Slot f = Prayer.next(today, tomorrow, late);
        assertEquals("Fajr", f.name);
        assertTrue(f.begins.toLocalDate().equals(d.plusDays(1)));
        // Worked-out times have no jamā'ah.
        List<Prayer.Slot> worked = Prayer.slots(new String[][]{DAY[0], null}, null, d);
        assertNull(worked.get(1).jamaah);
        assertEquals("Asr 14:05 · in 1h 6m", Prayer.nextLine(Prayer.next(worked, worked, one), one));
    }
}
