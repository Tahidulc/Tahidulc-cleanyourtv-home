package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** What F1's corner shows, and the session running read from ESPN's scoreboard. */
public class F1LiveTest {
    private static F1Live.Session session(String abbr) {
        F1Live.Session s = new F1Live.Session();
        s.abbr = abbr;
        s.state = "in";
        s.start = 1_000_000;
        return s;
    }

    @Test public void theRaceShowsItsLap() {
        F1Live.Session s = session("Race");
        s.lap = 23;
        assertArrayEquals(new String[]{"LAP", "23"}, F1Live.corner(s, s.start + 3_000_000));
    }

    @Test public void practiceCountsDownAnHour() {
        F1Live.Session s = session("FP2");
        assertArrayEquals(new String[]{"FP2", "41:07"}, F1Live.corner(s, s.start + (18 * 60 + 53) * 1000L));
        assertArrayEquals(new String[]{"", ""}, F1Live.corner(s, s.start + 61 * 60_000L));
    }

    @Test public void qualifyingHasItsParts() {
        F1Live.Session s = session("Qual");
        assertArrayEquals(new String[]{"Q1", "18:00"}, F1Live.corner(s, s.start));
        assertArrayEquals(new String[]{"Q2", "NEXT"}, F1Live.corner(s, s.start + 20 * 60_000L));
        // Q2 runs from 25 to 40 minutes: at 31:29 there are 8:31 left.
        assertArrayEquals(new String[]{"Q2", "08:31"}, F1Live.corner(s, s.start + (31 * 60 + 29) * 1000L));
        assertArrayEquals(new String[]{"Q3", "12:00"}, F1Live.corner(s, s.start + 48 * 60_000L));
    }

    @Test public void sprintQualifyingIsShorter() {
        F1Live.Session s = session("SS");
        assertArrayEquals(new String[]{"SQ1", "12:00"}, F1Live.corner(s, s.start));
        assertArrayEquals(new String[]{"SQ2", "10:00"}, F1Live.corner(s, s.start + 19 * 60_000L));
    }

    @Test public void codesAreF1sThreeLetters() {
        assertEquals("VER", F1Live.code("Verstappen"));
        assertEquals("HUL", F1Live.code("Hülkenberg"));
        assertEquals("QUALIFYING", F1Live.label("Qual"));
    }

    @Test public void theRunningSessionIsRead() {
        String json = "{\"events\":[{\"name\":\"Monaco Grand Prix\",\"competitions\":["
                + "{\"type\":{\"abbreviation\":\"Qual\"},\"date\":\"2026-05-23T14:00Z\",\"status\":{\"type\":{\"state\":\"post\"}}},"
                + "{\"type\":{\"abbreviation\":\"Race\"},\"date\":\"2026-05-24T13:00Z\",\"status\":{\"period\":31,\"type\":{\"state\":\"in\"}},"
                + "\"competitors\":[{\"order\":2,\"athlete\":{\"displayName\":\"Lando Norris\"}},{\"order\":1,\"athlete\":{\"displayName\":\"Charles Leclerc\"}}]}]}]}";
        F1Live.Session s = F1Live.running(json);
        assertNotNull(s);
        assertTrue(s.race());
        assertEquals(31, s.lap);
        assertEquals("Monaco", s.name);
        assertEquals("Leclerc", s.top.get(0));
        assertEquals("Norris", s.top.get(1));
        assertFalse(s.top.isEmpty());
    }

    @Test public void nothingRunningIsNull() {
        assertNull(F1Live.running("{\"events\":[{\"competitions\":[{\"status\":{\"type\":{\"state\":\"pre\"}}}]}]}"));
        assertNull(F1Live.running("not json"));
    }
}
