package com.gathin.homefieldtv.engine;

import com.gathin.homefieldtv.prefs.Shown;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The lists of live channels the screens show (Library's), worked out off the main thread so that no screen and no key
 * goes through tens of thousands of channels: Settings → Categories and Parental control applied (prefs.Shown), for
 * one load and one version of what is hidden or locked; with each channel's place in them, in its group and in its guide
 * category, the channels by number, and channel up and down past those that need the PIN. Made again (by Library)
 * whenever what is hidden or locked changes; read at once.
 */
final class Lists {
    static final Lists EMPTY = new Lists(Index.EMPTY, null);

    /**
     * One load's channels and what is worked out from them once as they are read: where each is in the list, its guide
     * category (Genres), its name for search, the playlist's groups, and how many channels each group has.
     */
    static final class Index {
        static final Index EMPTY = new Index(Collections.emptyList());

        final List<Channel> channels;
        /** Each channel's place by its key (KeyTable: an int table, not a map of boxed numbers). */
        final KeyTable byKey;
        final String[] lower;
        /** The playlist's own groups, in first-seen order (channels with no group have none). */
        final List<String> groups;
        /** Each channel's guide category, as its place in Genres.ORDER. */
        final byte[] genre;
        /** How many channels each group has, hidden ones too (Settings → Categories). */
        final Map<String, Integer> sizes;
        /** Every channel by its name's key (Names.key): made the first time it is asked for, off the main thread. */
        private volatile Map<String, List<Channel>> byName;

        Index(List<Channel> ch) {
            channels = ch;
            int n = ch.size();
            lower = new String[n];
            genre = new byte[n];
            byKey = new KeyTable(ch);
            Map<String, Integer> size = new HashMap<>();
            Map<String, String> groupSays = new HashMap<>();
            LinkedHashMap<String, Boolean> gs = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                Channel c = ch.get(i);
                lower[i] = Names.plain(c.name).toLowerCase(Locale.ROOT);
                if (!c.group.isEmpty()) gs.put(c.group, Boolean.TRUE);
                Integer had = size.get(c.group);
                size.put(c.group, had == null ? 1 : had + 1);
                // Genres.of: the name first, else the group, whose word is worked out once for all its channels.
                String g = Genres.byWords(c.name);
                if (g == null) {
                    g = groupSays.get(c.group);
                    if (g == null) groupSays.put(c.group, g = Genres.of(c.group, ""));
                }
                genre[i] = (byte) Genres.index(g);
            }
            groups = Collections.unmodifiableList(new ArrayList<>(gs.keySet()));
            sizes = Collections.unmodifiableMap(size);
        }

        Map<String, List<Channel>> byName() {
            Map<String, List<Channel>> m = byName;
            if (m != null) return m;
            synchronized (this) {
                if (byName == null) {
                    Map<String, List<Channel>> made = new HashMap<>();
                    for (Channel c : channels) {
                        String k = Names.key(c.name);
                        if (k.isEmpty()) continue;
                        List<Channel> l = made.get(k);
                        if (l == null) made.put(k, l = new ArrayList<>(1));
                        l.add(c);
                    }
                    byName = made;
                }
                return byName;
            }
        }

        /** A channel's place (by its key), or -1. */
        int find(Channel c) {
            return c == null ? -1 : byKey.get(c.key);
        }

        /** The number shown for a channel: the playlist's own, else its place in the list. */
        int number(Channel c) {
            if (c.number > 0) return c.number;
            int p = find(c);
            return p < 0 ? 0 : p + 1;
        }
    }

    final Index index;
    /** What is hidden and locked as it was made for (null: nothing yet). */
    final Shown.Filter filter;
    final List<Channel> all;
    /** Each channel's place (by its place in index.channels) in all, in its group's list, in its category's; -1 if not listed. */
    final int[] at, inGroup, inGenre;
    /** Each group's listed channels, the groups in the order they first come (the unnamed group too). */
    final Map<String, List<Channel>> groups;
    final List<String> groupOrder;
    /** Each guide category's listed channels, by its place in Genres.ORDER. */
    final List<List<Channel>> genres;
    /** (number << 32 | place in all), in order: the listed channels by the number shown for them. */
    final long[] numbers;
    /** Channel up and down: the next place in all, and the one before, that needs no PIN (null when none needs one). */
    final int[] next, prev;
    /** The first and last places in all that need no PIN (-1: every one does). */
    final int first, last;

    Lists(Index ix, Shown.Filter f) {
        index = ix;
        filter = f;
        List<Channel> ch = ix.channels;
        int n = ch.size();
        at = new int[n];
        inGroup = new int[n];
        inGenre = new int[n];
        List<Channel> shown = new ArrayList<>(n);
        LinkedHashMap<String, List<Channel>> byGroup = new LinkedHashMap<>();
        List<List<Channel>> byGenre = new ArrayList<>(Genres.ORDER.size());
        for (int g = 0; g < Genres.ORDER.size(); g++) byGenre.add(new ArrayList<>());
        long[] nums = new long[n];
        boolean blocked = false;
        for (int i = 0; i < n; i++) {
            Channel c = ch.get(i);
            if (f == null || !f.listed(c)) {
                at[i] = inGroup[i] = inGenre[i] = -1;
                continue;
            }
            int p = shown.size();
            at[i] = p;
            shown.add(c);
            List<Channel> g = byGroup.get(c.group);
            if (g == null) byGroup.put(c.group, g = new ArrayList<>());
            inGroup[i] = g.size();
            g.add(c);
            List<Channel> k = byGenre.get(Math.max(0, ix.genre[i]));
            inGenre[i] = k.size();
            k.add(c);
            nums[p] = (long) (c.number > 0 ? c.number : i + 1) << 32 | p;
            blocked |= f.blocks(c);
        }
        int size = shown.size();
        all = size == n ? ch : Collections.unmodifiableList(shown);
        Map<String, List<Channel>> gm = new LinkedHashMap<>();
        for (Map.Entry<String, List<Channel>> e : byGroup.entrySet()) gm.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        groups = Collections.unmodifiableMap(gm);
        groupOrder = Collections.unmodifiableList(new ArrayList<>(byGroup.keySet()));
        List<List<Channel>> gl = new ArrayList<>(byGenre.size());
        for (List<Channel> l : byGenre) gl.add(Collections.unmodifiableList(l));
        genres = Collections.unmodifiableList(gl);
        numbers = Arrays.copyOf(nums, size);
        Arrays.sort(numbers);
        if (!blocked) {
            next = prev = null;
            first = size > 0 ? 0 : -1;
            last = size - 1;
        } else {
            next = new int[size];
            prev = new int[size];
            int f0 = -1, l0 = -1;
            for (int p = 0; p < size; p++) {
                if (f.blocks(all.get(p))) continue;
                if (f0 < 0) f0 = p;
                l0 = p;
            }
            first = f0;
            last = l0;
            // Each place's next free one after it, round at the end (-1 when none but itself is free), and the one before.
            int seen = f0;
            for (int p = size - 1; p >= 0; p--) {
                next[p] = seen == p ? -1 : seen;
                if (!f.blocks(all.get(p))) seen = p;
            }
            seen = l0;
            for (int p = 0; p < size; p++) {
                prev[p] = seen == p ? -1 : seen;
                if (!f.blocks(all.get(p))) seen = p;
            }
        }
    }

    /** Whether it still says what is hidden and locked now. */
    boolean fresh() {
        return filter != null && filter.current();
    }

    /** The first place in numbers with this number, or -1. */
    int numbered(int number) {
        long want = (long) number << 32;
        int lo = 0, hi = numbers.length - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (numbers[mid] >= want) {
                found = mid;
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }
        return found >= 0 && (int) (numbers[found] >> 32) == number ? found : -1;
    }

    /** Channel up or down through all, past those that need the PIN, as Shown.step goes: at once. */
    Channel step(Channel from, int dir) {
        int size = all.size();
        if (size == 0) return null;
        int e = index.find(from), p = e < 0 ? -1 : at[e];
        boolean forward = dir >= 0;
        if (p < 0) {
            if (first < 0) return all.get(0);
            return all.get(forward ? first : last);
        }
        int to;
        if (next == null) to = size == 1 ? -1 : forward ? (p + 1) % size : (p - 1 + size) % size;
        else to = forward ? next[p] : prev[p];
        return to < 0 ? from : all.get(to);
    }
}
