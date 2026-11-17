package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ByteLruTest {
    /** A stand-in for a picture: only its size in bytes. */
    private static final class Pic {
        final long bytes;

        Pic(long bytes) {
            this.bytes = bytes;
        }
    }

    private static ByteLru<String, Pic> cache(long max) {
        return new ByteLru<>("test", max, p -> p.bytes);
    }

    @Test
    public void heldToItsSizeInBytesNotItsCount() {
        ByteLru<String, Pic> c = cache(1_000);
        for (int i = 0; i < 100; i++) c.put("k" + i, new Pic(100));
        assertEquals(1_000, c.size());
        assertEquals(10, c.count());
        // the newest ten are the ones kept
        for (int i = 90; i < 100; i++) assertTrue(c.containsKey("k" + i));
        assertFalse(c.containsKey("k89"));
        assertTrue(c.evictions() >= 90);
    }

    @Test
    public void oneBigPictureCannotHoldTheRestHostage() {
        // As the ticker's 80-logo cache did: a count says nothing of how big each is.
        ByteLru<String, Pic> c = cache(4L * 1024 * 1024);
        c.put("small", new Pic(64 * 1024));
        c.put("huge", new Pic(4L * 1024 * 1024));
        assertTrue(c.size() <= c.maxSize());
        c.put("bigger than the cache", new Pic(5L * 1024 * 1024));
        assertFalse("an entry bigger than the whole cache is not kept", c.containsKey("bigger than the cache"));
        assertTrue(c.size() <= c.maxSize());
    }

    @Test
    public void leastRecentlyUsedGoesFirst() {
        ByteLru<String, Pic> c = cache(300);
        c.put("a", new Pic(100));
        c.put("b", new Pic(100));
        c.put("c", new Pic(100));
        assertSame(c.get("a"), c.get("a"));   // a used: b is now the oldest
        c.put("d", new Pic(100));
        assertTrue(c.containsKey("a"));
        assertFalse(c.containsKey("b"));
        assertTrue(c.containsKey("c"));
        assertTrue(c.containsKey("d"));
    }

    @Test
    public void replacingCountsOnlyTheNewSize() {
        ByteLru<String, Pic> c = cache(1_000);
        c.put("a", new Pic(400));
        c.put("a", new Pic(100));
        assertEquals(100, c.size());
        assertEquals(1, c.count());
        c.remove("a");
        assertEquals(0, c.size());
        assertNull(c.remove("a"));
    }

    @Test
    public void nothingFoundIsKeptAndTakesNoRoom() {
        ByteLru<String, Pic> c = cache(100);
        c.put("none", null);
        assertTrue(c.containsKey("none"));
        assertNull(c.get("none"));
        assertEquals(0, c.size());
    }

    @Test
    public void trimmedAndEmptied() {
        ByteLru<String, Pic> c = cache(1_000);
        for (int i = 0; i < 10; i++) c.put("k" + i, new Pic(100));
        c.trimTo(500);
        assertEquals(500, c.size());
        assertTrue(c.containsKey("k9"));
        assertFalse(c.containsKey("k0"));
        c.evictAll();
        assertEquals(0, c.size());
        assertEquals(0, c.count());
    }

    @Test
    public void everyCacheTrimmedAtOnceOnAWarning() {
        List<ByteLru<String, Pic>> keep = new ArrayList<>();
        for (int n = 0; n < 3; n++) {
            ByteLru<String, Pic> c = cache(1_000);
            for (int i = 0; i < 10; i++) c.put("k" + i, new Pic(100));
            keep.add(c);
        }
        long before = ByteLru.totalBytes();
        assertTrue(before >= 3_000);
        long freed = ByteLru.trimAll(0.5f);
        assertTrue(freed >= 1_500);
        for (ByteLru<String, Pic> c : keep) assertEquals(500, c.size());
        ByteLru.trimAll(0f);
        for (ByteLru<String, Pic> c : keep) assertEquals(0, c.size());
        assertTrue(ByteLru.caches() >= 3);
    }

    @Test
    public void theBiggestNamedForTheLog() {
        ByteLru<String, Pic> a = new ByteLru<>("alpha", 10_000, p -> p.bytes), b = new ByteLru<>("beta", 10_000, p -> p.bytes);
        ByteLru.trimAll(0f);
        a.put("x", new Pic(2_048));
        b.put("y", new Pic(8_192));
        String big = ByteLru.biggest(2);
        assertTrue(big, big.startsWith("beta:8K"));
        assertTrue(big, big.contains("alpha:2K"));
        assertEquals(10_240, a.size() + b.size());
    }

    @Test
    public void snapshotIsACopy() {
        ByteLru<String, Pic> c = cache(1_000);
        c.put("a", new Pic(10));
        c.snapshot().clear();
        assertTrue(c.containsKey("a"));
    }
}
