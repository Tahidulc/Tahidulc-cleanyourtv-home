package com.gathin.tvhomephone.sync;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SyncStateTest {
    @Test public void aSyncThatWentThroughMovesBothMarks() {
        SyncState s = new SyncState(0, 0);
        long started = s.begin(10_000);
        s.done(started, 500_000);
        assertEquals(500_000, s.since);
        assertEquals(9_999, s.pushed);
    }

    @Test public void marksNeverGoBack() {
        SyncState s = new SyncState(800, 9_000);
        s.done(5_000, 100);
        assertEquals(800, s.since);
        assertEquals(9_000, s.pushed);
    }

    @Test public void forgottenByTheTvStartsAgain() {
        SyncState s = new SyncState(800, 9_000);
        s.reset();
        assertEquals(0, s.since);
        assertEquals(0, s.pushed);
    }

    @Test public void furtherOnWins() throws Exception {
        org.json.JSONObject a = new org.json.JSONObject("{\"season\":2,\"episode\":3}");
        org.json.JSONObject b = new org.json.JSONObject("{\"season\":1,\"episode\":9}");
        org.junit.Assert.assertTrue(Tvs.further(a, b));
        org.junit.Assert.assertFalse(Tvs.further(b, a));
    }
}
