package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** What the channel's scoreboard says, from the lines read off it. */
public class ScreenTextTest {
    @Test public void theClockIsRead() {
        assertEquals(67 * 60 + 23, ScreenText.football(Arrays.asList("LIV 2-1 MUN", "67:23")).clock);
        assertEquals(67 * 60 + 23, ScreenText.football(Collections.singletonList("LIV 2 - 1 MUN 67:23")).clock);
        // Misread letters next to digits.
        assertEquals(60 * 60 + 23, ScreenText.football(Collections.singletonList("6O:23")).clock);
        assertEquals(-1, ScreenText.football(Collections.singletonList("LIV 2-1 MUN")).clock);
    }

    @Test public void addedTimeIsRead() {
        ScreenText.Reading r = ScreenText.football(Arrays.asList("ARS 1 0 CHE", "90:00 +4"));
        assertEquals(90 * 60, r.clock);
        assertEquals(4, r.added);
    }

    @Test public void notTheTimeOfDayOrNonsense() {
        // Hours past two of play are not a game's clock.
        assertEquals(-1, ScreenText.football(Collections.singletonList("KICK-OFF 175:00")).clock);
        assertEquals(-1, ScreenText.football(Collections.singletonList("12:75")).clock);
    }

    @Test public void theScoreIsFoundAmongTheNumbers() {
        assertTrue(ScreenText.shows(Arrays.asList("LIV 2-1 MUN", "67:23"), 2, 1));
        assertFalse(ScreenText.shows(Arrays.asList("LIV 2-1 MUN", "67:23"), 1, 2));
        // In boxes, read as lines of their own.
        assertTrue(ScreenText.shows(Arrays.asList("LIV", "3", "1", "MUN", "71:02"), 3, 1));
        // The clock's digits are never taken for the score.
        assertFalse(ScreenText.shows(Collections.singletonList("LIV MUN 23:45"), 23, 45));
    }

    @Test public void f1sTowerIsRead() {
        ScreenText.Reading r = ScreenText.f1(Arrays.asList("LAP 23/57", "1 NOR", "2 VER"));
        assertEquals(23, r.lap);
        assertEquals(57, r.laps);
        ScreenText.Reading q = ScreenText.f1(Arrays.asList("Q2", "08:31"));
        assertEquals(8 * 60 + 31, q.left);
        assertEquals(-1, ScreenText.f1(Collections.singletonList("LAP 60/57")).lap);
    }

    @Test public void theScoreTheChannelShowsIsRead() {
        assertArrayEquals(new int[]{2, 1}, ScreenText.score(Arrays.asList("LIV 2-1 MUN", "67:23")));
        assertArrayEquals(new int[]{0, 0}, ScreenText.score(Collections.singletonList("ARS 0 \u2013 0 CHE 12:04")));
        // In boxes of their own, the clock and the minutes added left out.
        assertArrayEquals(new int[]{3, 1}, ScreenText.score(Arrays.asList("LIV", "3", "1", "MUN", "90:00", "+4")));
        // The clock's digits are never the score, and too many numbers can't be told apart.
        assertNull(ScreenText.score(Collections.singletonList("LIV MUN 23:45")));
        assertNull(ScreenText.score(Arrays.asList("1", "2", "3")));
    }

    @Test public void halfTimeAndFullTimeAreRead() {
        assertEquals("HT", ScreenText.state(Arrays.asList("LIV 1-1 MUN", "HT")));
        assertEquals("HT", ScreenText.state(Collections.singletonList("Half-time")));
        assertEquals("FT", ScreenText.state(Arrays.asList("LIV 2-1 MUN", "FT")));
        assertEquals("FT", ScreenText.state(Collections.singletonList("FULL TIME")));
        assertNull(ScreenText.state(Arrays.asList("LIV 2-1 MUN", "67:23")));
    }
}
