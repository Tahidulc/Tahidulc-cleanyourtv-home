package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A test alert for every kind there is, never under a real one's key. */
public class TestAlertsTest {
    @Test public void everyKindHasOne() {
        Game g = new Game();
        g.id = "1";
        g.homeName = "Home";
        g.awayName = "Away";
        g.goals.add(new String[]{"TEST|goal", "C. Player", "67'", "home", ""});
        g.reds.add(new String[]{"TEST|red", "D. Player", "70'", "away"});
        for (String[] k : TestAlerts.kinds()) {
            Events.Alert a = TestAlerts.alert(k[0], g);
            assertNotNull(k[0], a);
            assertEquals(k[0], a.kind);
            assertTrue(a.key, a.key.contains("TEST|"));
        }
        assertEquals(Kinds.ALL.length, TestAlerts.kinds().size());
    }

    @Test public void clubCodes() {
        assertEquals("MAN", TestAlerts.code("Man United"));
        assertEquals("EVE", TestAlerts.code("Everton"));
    }
}
