package com.gathin.tvhomephone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class SyncStoreTest {
    @Test public void laterChangeWins() {
        JSONObject old = SyncStore.item("mylist", "tv:1", new JSONObject(), 1000, false, "TV");
        JSONObject now = SyncStore.item("mylist", "tv:1", new JSONObject(), 2000, true, "Phone");
        assertTrue(SyncStore.wins(now, old));
        assertFalse(SyncStore.wins(old, now));
    }

    @Test public void tieGoesToTheGreaterName() {
        JSONObject a = SyncStore.item("rating", "mv:2", new JSONObject(), 5000, false, "A");
        JSONObject b = SyncStore.item("rating", "mv:2", new JSONObject(), 5000, false, "B");
        assertTrue(SyncStore.wins(b, a));
        assertFalse(SyncStore.wins(a, b));
    }

    @Test public void anythingBeatsNothing() {
        assertTrue(SyncStore.wins(SyncStore.item("mute", "tv:3", null, 1, false, "x"), null));
    }
}
