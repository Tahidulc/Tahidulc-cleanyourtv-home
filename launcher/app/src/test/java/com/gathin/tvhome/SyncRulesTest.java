package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncRulesTest {
    private static SyncRules.Meta meta(long updated, String from) {
        SyncRules.Meta m = new SyncRules.Meta();
        m.updated = updated;
        m.from = from;
        return m;
    }

    @Test public void laterWriterWins() {
        assertTrue(SyncRules.wins(2000, "Phone", meta(1000, "TV")));
        assertFalse(SyncRules.wins(1000, "Phone", meta(2000, "TV")));
    }

    @Test public void aTieGoesToTheGreaterNameAndAnEqualOneLoses() {
        assertTrue(SyncRules.wins(5000, "Pixel", meta(5000, "Living room")));
        assertFalse(SyncRules.wins(5000, "Living room", meta(5000, "Pixel")));
        assertFalse(SyncRules.wins(5000, "Pixel", meta(5000, "Pixel")));
        assertTrue(SyncRules.wins(1, "x", null));
    }

    @Test public void scanStampsNewAndChangedAndTombstonesWhatWent() {
        Map<String, SyncRules.Meta> metas = new HashMap<>();
        Map<String, String> now = new HashMap<>();
        now.put("mylist|tv:1", "{\"title\":\"A\"}");
        now.put("mute|tv:2", "{}");
        List<String> first = SyncRules.scan(now, metas, 100, "TV", null);
        assertEquals(2, first.size());
        assertEquals(100, metas.get("mylist|tv:1").updated);
        // Nothing changed: nothing stamped.
        assertTrue(SyncRules.scan(now, metas, 200, "TV", null).isEmpty());
        // One changed, one gone.
        now.put("mylist|tv:1", "{\"title\":\"B\"}");
        now.remove("mute|tv:2");
        List<String> second = SyncRules.scan(now, metas, 300, "TV", null);
        assertEquals(2, second.size());
        assertEquals(300, metas.get("mylist|tv:1").updated);
        assertTrue(metas.get("mute|tv:2").deleted);
        assertEquals(300, metas.get("mute|tv:2").updated);
        // A tombstone stays one.
        assertTrue(SyncRules.scan(now, metas, 400, "TV", null).isEmpty());
        // And comes back to life when the item does.
        now.put("mute|tv:2", "{}");
        assertEquals(1, SyncRules.scan(now, metas, 500, "TV", null).size());
        assertFalse(metas.get("mute|tv:2").deleted);
    }

    @Test public void theTvsOwnClubsAreStampedZero() {
        Map<String, SyncRules.Meta> metas = new HashMap<>();
        Map<String, String> now = new HashMap<>();
        now.put("clubs|starred", "{\"teams\":[\"83\"]}");
        SyncRules.scan(now, metas, 100, "TV", Collections.singleton("clubs|starred"));
        assertEquals(0, metas.get("clubs|starred").updated);
        assertEquals(100, metas.get("clubs|starred").seen);
        // Anyone's choice beats a default.
        assertTrue(SyncRules.wins(1, "Phone", metas.get("clubs|starred")));
    }

    @Test public void whatGoesOutIsWhatArrivedAfterSince() {
        Map<String, SyncRules.Meta> metas = new HashMap<>();
        SyncRules.Meta a = meta(10, "TV");
        a.seen = 50;
        SyncRules.Meta b = meta(5, "Phone");
        b.seen = 150;   // an older change that only reached this TV later still goes out
        metas.put("a", a);
        metas.put("b", b);
        assertEquals(Collections.singletonList("b"), SyncRules.since(metas, 100));
        assertEquals(2, SyncRules.since(metas, 0).size());
    }

    @Test public void oldTombstonesGo() {
        Map<String, SyncRules.Meta> metas = new HashMap<>();
        SyncRules.Meta old = meta(0, "TV");
        old.deleted = true;
        SyncRules.Meta live = meta(0, "TV");
        metas.put("old", old);
        metas.put("live", live);
        assertTrue(SyncRules.prune(metas, SyncRules.TOMBSTONE_MS + 1));
        assertFalse(metas.containsKey("old"));
        assertTrue(metas.containsKey("live"));
    }

    @Test public void titleKeys() {
        assertEquals("tv:1399", SyncRules.titleKey(true, 1399));
        Object[] k = SyncRules.parseTitleKey("mv:550");
        assertEquals(false, k[0]);
        assertEquals(550, k[1]);
        Object[] season = SyncRules.parseTitleKey("tv:1399:s2");
        assertEquals(true, season[0]);
        assertEquals(1399, season[1]);
        assertNull(SyncRules.parseTitleKey("game:401"));
        assertNull(SyncRules.parseTitleKey("tv:abc"));
    }

    @Test public void aRatingGoesOutAndComesBackTheSame() {
        String kept = WatchRules.encode(WatchRules.LOVED, 1234L, "Drama", "Wildwood");
        Map<String, Object> d = SyncRules.ratingData(kept);
        assertEquals(WatchRules.LOVED, d.get("value"));
        assertEquals("Wildwood", d.get("title"));
        assertEquals(1234L, d.get("at"));
        String back = SyncRules.ratingRecord((Integer) d.get("value"), (Long) d.get("at"), (String) d.get("genre"), (String) d.get("title"));
        assertEquals(kept, back);
        assertNull(SyncRules.ratingRecord(7, 1, "", "x"));
    }

    @Test public void aReminderKeepsTheKeyItCameWith() {
        assertEquals("game:401", SyncRules.reminderKey("game:401", "Sky Sports", 1000, "Man Utd v Roma"));
        assertEquals("show:Sky Sports:1000:Man Utd v Roma", SyncRules.reminderKey("", "Sky Sports", 1000, "Man Utd v Roma"));
        Map<String, Object> d = SyncRules.reminderData("Man Utd v Roma", 1000, "Sky Sports", "", "");
        assertEquals("show", d.get("kind"));
        assertEquals(1000L, d.get("at"));
    }

    @Test public void myListDataLeavesTheTvsOwnMarkOut() {
        Map<String, Object> d = SyncRules.myListData(1399, true, "Nocturne", "https://image.tmdb.org/p.jpg", null, 42);
        assertFalse(d.containsKey("apps"));
        assertEquals("", d.get("backdrop"));
        String c = SyncRules.canonical(d);
        assertEquals("{\"id\":1399,\"tv\":true,\"title\":\"Nocturne\",\"poster\":\"https://image.tmdb.org/p.jpg\",\"backdrop\":\"\",\"added\":42}", c);
        assertEquals(SyncRules.hash(c), SyncRules.hash(SyncRules.canonical(SyncRules.myListData(1399, true, "Nocturne",
                "https://image.tmdb.org/p.jpg", "null", 42))));
    }

    @Test public void aPlansDataIsWhatThePhoneSets() {
        Map<String, Object> d = SyncRules.planData(7, "Verity", 2000L, 117, true, true, false, null, null);
        assertEquals(true, d.get("lights"));
        assertEquals(117, d.get("runtime"));
        assertEquals(2000L, d.get("start"));
    }
}
