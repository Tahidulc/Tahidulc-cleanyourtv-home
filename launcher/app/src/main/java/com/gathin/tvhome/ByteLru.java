package com.gathin.tvhome;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A cache held to a size in bytes, not to a number of entries: the least recently used entries go first once it is
 * full, and an entry bigger than the whole cache is not kept at all. Every picture cache in the launcher is one
 * (BitmapCache), so a cache of crests can never grow with the day the way a count of 80 "small" logos could when one
 * of them is a 1024-pixel picture. Each registers itself (weakly) in a list that a memory warning empties (trimAll)
 * and that the self-check adds up (totalBytes, MemoryWatch). Null values are allowed (a picture looked for and not
 * found), and take no room. Plain Java: the sizes come from a Sizer, so the tests need no Android.
 */
class ByteLru<K, V> {
    /** How much room a value takes, in bytes. */
    interface Sizer<V> {
        long bytes(V v);
    }

    private final LinkedHashMap<K, V> map = new LinkedHashMap<>(16, 0.75f, true);
    /** Each entry's size as it was put (a picture recycled since is still counted as what it held). */
    private final Map<K, Long> sizes = new LinkedHashMap<>();
    private final Sizer<V> sizer;
    private final String name;
    private long max, size;
    private long evicted;

    ByteLru(String name, long maxBytes, Sizer<V> sizer) {
        this.name = name;
        this.max = Math.max(0, maxBytes);
        this.sizer = sizer;
        register(this);
    }

    String name() {
        return name;
    }

    synchronized V get(K key) {
        return map.get(key);
    }

    synchronized boolean containsKey(K key) {
        return map.containsKey(key);
    }

    /** Puts value for key (replacing what was there) and makes room for it; returns the value it replaced. */
    synchronized V put(K key, V value) {
        long s = value == null ? 0 : Math.max(0, sizer.bytes(value));
        V was = map.put(key, value);
        Long old = sizes.put(key, s);
        if (old != null) size -= old;
        size += s;
        trimTo(max);
        return was;
    }

    synchronized V remove(K key) {
        if (!map.containsKey(key)) return null;
        V was = map.remove(key);
        Long s = sizes.remove(key);
        if (s != null) size -= s;
        return was;
    }

    /** Everything let go (nothing is recycled: a picture still on the screen stays there until its view lets it go). */
    synchronized void evictAll() {
        if (!map.isEmpty()) evicted += map.size();
        map.clear();
        sizes.clear();
        size = 0;
    }

    void clear() {
        evictAll();
    }

    /** The least recently used let go until at most bytes are held. */
    synchronized void trimTo(long bytes) {
        Iterator<Map.Entry<K, V>> it = map.entrySet().iterator();
        while (size > bytes && it.hasNext()) {
            Map.Entry<K, V> e = it.next();
            Long s = sizes.remove(e.getKey());
            if (s != null) size -= s;
            it.remove();
            evicted++;
        }
        if (map.isEmpty()) size = 0;
    }

    /** Bytes held now. */
    synchronized long size() {
        return size;
    }

    synchronized long maxSize() {
        return max;
    }

    synchronized int count() {
        return map.size();
    }

    /** Entries let go to make room or by a trim, since the start. */
    synchronized long evictions() {
        return evicted;
    }

    /** A copy of what is held, least recently used first. */
    synchronized LinkedHashMap<K, V> snapshot() {
        return new LinkedHashMap<>(map);
    }

    // ---- every cache, for memory warnings and the self-check ------------------------------------------------------------

    private static final List<WeakReference<ByteLru<?, ?>>> ALL = new ArrayList<>();

    private static void register(ByteLru<?, ?> c) {
        synchronized (ALL) {
            prune();
            ALL.add(new WeakReference<>(c));
        }
    }

    private static void prune() {
        for (Iterator<WeakReference<ByteLru<?, ?>>> it = ALL.iterator(); it.hasNext(); ) if (it.next().get() == null) it.remove();
    }

    private static List<ByteLru<?, ?>> live() {
        List<ByteLru<?, ?>> out = new ArrayList<>();
        synchronized (ALL) {
            prune();
            for (WeakReference<ByteLru<?, ?>> r : ALL) {
                ByteLru<?, ?> c = r.get();
                if (c != null) out.add(c);
            }
        }
        return out;
    }

    /** Every cache trimmed to share (0: emptied, 0.5: halved) of what it may hold; the bytes let go. */
    static long trimAll(float share) {
        long freed = 0;
        for (ByteLru<?, ?> c : live()) {
            long before = c.size();
            if (share <= 0f) c.evictAll();
            else c.trimTo((long) (c.maxSize() * Math.min(1f, share)));
            freed += before - c.size();
        }
        return freed;
    }

    /** Bytes held by every cache now. */
    static long totalBytes() {
        long t = 0;
        for (ByteLru<?, ?> c : live()) t += c.size();
        return t;
    }

    /** How many caches there are now. */
    static int caches() {
        return live().size();
    }

    /** The biggest few, for the self-check's line: "name:KB", largest first ("" when all are empty). */
    static String biggest(int few) {
        List<ByteLru<?, ?>> all = live();
        all.sort((a, b) -> Long.compare(b.size(), a.size()));
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < all.size() && i < few; i++) {
            ByteLru<?, ?> c = all.get(i);
            if (c.size() <= 0) break;
            if (s.length() > 0) s.append(',');
            s.append(c.name()).append(':').append(c.size() / 1024).append('K');
        }
        return s.toString();
    }
}
