package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The recent channels: newest first, each once, eleven kept so the row still has ten without the one playing. */
public class RecentChannelsTest {
    private static List<String> keys(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add("c" + i);
        return out;
    }

    @Test public void newestFirst() {
        List<String> l = RecentChannels.add(Collections.emptyList(), "a");
        l = RecentChannels.add(l, "b");
        l = RecentChannels.add(l, "c");
        assertEquals(Arrays.asList("c", "b", "a"), l);
    }

    @Test public void watchedAgainMovesToTheFrontOnce() {
        assertEquals(Arrays.asList("b", "a", "c"), RecentChannels.add(Arrays.asList("a", "b", "c"), "b"));
        assertEquals(Arrays.asList("a", "b"), RecentChannels.add(Arrays.asList("a", "a", "b"), "a"));
        // Nothing to add: the list as it was, tidied.
        assertEquals(Arrays.asList("a", "b"), RecentChannels.add(Arrays.asList("a", "", "b"), ""));
    }

    @Test public void elevenKeptTheOldestDropped() {
        List<String> l = RecentChannels.add(keys(11), "new");
        assertEquals(RecentChannels.MAX + 1, l.size());
        assertEquals("new", l.get(0));
        assertFalse(l.contains("c10"));
        assertTrue(l.contains("c9"));
    }

    @Test public void theRowLeavesOutTheOnePlayingAndShowsTen() {
        assertEquals(Arrays.asList("a", "c"), RecentChannels.shown(Arrays.asList("b", "a", "c"), "b"));
        List<String> eleven = keys(11);
        assertEquals(10, RecentChannels.shown(eleven, null).size());
        List<String> row = RecentChannels.shown(eleven, "c0");
        assertEquals(10, row.size());
        assertEquals("c1", row.get(0));
        assertFalse(row.contains("c0"));
        assertTrue(RecentChannels.shown(null, "a").isEmpty());
    }

    @Test public void keptAsALine() {
        assertEquals(Arrays.asList("p0|1", "p0|2"), RecentChannels.parse("p0|1\n\np0|2\np0|1"));
        assertEquals("p0|1\np0|2", RecentChannels.join(Arrays.asList("p0|1", " ", "p0|2")));
        assertTrue(RecentChannels.parse(null).isEmpty());
        assertEquals("", RecentChannels.join(null));
    }
}
