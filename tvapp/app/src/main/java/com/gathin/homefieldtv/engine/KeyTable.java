package com.gathin.homefieldtv.engine;

import java.util.List;

/**
 * The channels' places by their keys, found at once however many there are, in one int table (open addressing on the
 * key's own hash): no map node and no boxed number per channel, so 50,000 channels take a few hundred kilobytes rather
 * than megabytes. A key that two channels share finds the last (as a map would). Made once a load, then only read (any
 * thread).
 */
final class KeyTable {
    private final List<Channel> channels;
    /** Each slot: a channel's place + 1 (0: empty). */
    private final int[] slots;
    private final int mask;

    KeyTable(List<Channel> channels) {
        this.channels = channels;
        int n = channels.size(), size = 16;
        while (size < n * 2) size <<= 1;
        slots = new int[size];
        mask = size - 1;
        for (int i = 0; i < n; i++) {
            String k = channels.get(i).key;
            int at = spread(k.hashCode()) & mask;
            while (slots[at] != 0 && !channels.get(slots[at] - 1).key.equals(k)) at = (at + 1) & mask;
            slots[at] = i + 1;
        }
    }

    private static int spread(int h) {
        return h ^ (h >>> 16) ^ (h >>> 7);
    }

    /** The place of the channel with this key, or -1. */
    int get(String key) {
        if (key == null) return -1;
        int at = spread(key.hashCode()) & mask;
        for (int s; (s = slots[at]) != 0; at = (at + 1) & mask) {
            if (channels.get(s - 1).key.equals(key)) return s - 1;
        }
        return -1;
    }
}
