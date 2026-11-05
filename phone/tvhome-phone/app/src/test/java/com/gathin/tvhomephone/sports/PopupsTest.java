package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The pop-up's swipe and words. */
public class PopupsTest {
    @Test public void aSwipeFarEnoughOrFlungSendsItAway() {
        // A card 1000 by 300 px; flings count from 2000 px a second.
        assertTrue(Popups.away(320, 0, 0, 0, 1000, 300, 2000));
        assertTrue(Popups.away(-320, 0, 0, 0, 1000, 300, 2000));
        assertTrue(Popups.away(0, -120, 0, 0, 1000, 300, 2000));
        assertTrue(Popups.away(80, 0, 2500, 100, 1000, 300, 2000));
        assertTrue(Popups.away(0, -30, 0, -2600, 1000, 300, 2000));
        assertFalse(Popups.away(120, 0, 300, 0, 1000, 300, 2000));
        assertFalse(Popups.away(0, -40, 0, -500, 1000, 300, 2000));
    }

    @Test public void theTitleLosesItsShoutedHead() {
        assertEquals("Monaco", Popups.title(new Events.Alert("f1result", "k", "F1 RESULT · Monaco", "", null)));
        assertEquals("Rain on the way", Popups.title(new Events.Alert("weather", "k", "Rain on the way", "", null)));
    }

    @Test public void slowLineUpsStayLonger() {
        Events.Alert l = new Events.Alert("kickoff", "L|1", "Line-ups", "", null);
        Events.Alert w = new Events.Alert("weather", "w", "Rain", "", null);
        assertTrue(Popups.showMs(l, true) > Popups.showMs(w, false));
    }
}
