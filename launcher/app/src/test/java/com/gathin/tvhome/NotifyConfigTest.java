package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * NotifyConfig, the ADB switch for one kind of alert: the ids it is sent by stay the ids the Notifications page lists
 * (the live table is "table"), and an id that names no kind changes nothing and says so.
 */
public class NotifyConfigTest {
    @Test public void theLiveTableIsTable() {
        assertNotNull(NotifyKinds.get("table"));
        assertEquals("Live table", NotifyKinds.get("table").name);
    }

    @Test public void anUnknownKindChangesNothing() {
        assertEquals("no kind tabel", NotifyConfig.apply(null, "tabel", false));
        assertEquals("no kind null", NotifyConfig.apply(null, null, null));
    }
}
