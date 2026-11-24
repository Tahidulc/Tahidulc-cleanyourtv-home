package com.gathin.homefieldtv.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.homefieldtv.engine.Channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Channels merged by hand (the TV guide's Merge with, on a channel's hold-OK card; Settings › Channels &amp; categories ›
 * Merged channels): a channel merged into another leaves the lists (as a hidden one does: the guide, the channel lists,
 * channel up and down, the numbers, search, Home's rows) and stands in for the channel it is merged into: its stream plays
 * when that one's drops (after that channel's own qualities and copies), and its guide and catch-up are that channel's
 * when it has none of its own.
 *
 * <p>With One row per channel (engine.Versions) the two never fight: the automatic merge makes a channel's qualities one
 * row first; a merge by hand joins whole rows (the row's own key is kept), and a row's stand-ins come after its own
 * versions. A channel merged away can't take a merge itself, nor can a channel be merged into itself or one of its own
 * versions; merging a channel that others were merged into hands them on to the one it joins.
 *
 * <p>Kept by the channels' stable keys in the "channel_merges" settings ({@code merged<TAB>into} a line), so they hold
 * across reloads; a channel gone from the playlists keeps its line until it comes back (Settings names it "a channel not
 * in your playlists now"). Read at once from memory; a change makes the lists again (Shown.changed).
 */
public final class Merges {
    private Merges() {}

    static final String PREFS = "channel_merges", PAIRS = "pairs";
    public static final String CANT = "That channel can't take a merge";

    /** merged key → the key it is merged into, in the order they were made. */
    private static volatile Map<String, String> into;
    /** The channel with a key now (Library's exact look-up), for the stand-ins; null before the library is made. */
    public static volatile Function<String, Channel> lookup;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Read once (any thread). */
    public static void load(Context c) {
        if (into != null) return;
        synchronized (Merges.class) {
            if (into == null) into = parse(prefs(c).getString(PAIRS, ""));
        }
    }

    /** Pure: the kept lines read. */
    static Map<String, String> parse(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        if (s == null) return m;
        for (String line : s.split("\n")) {
            int t = line.indexOf('\t');
            if (t <= 0 || t == line.length() - 1) continue;
            String a = line.substring(0, t), b = line.substring(t + 1);
            if (!a.equals(b)) m.put(a, b);
        }
        return m;
    }

    private static void save(Context c, Map<String, String> m) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) b.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        into = Collections.unmodifiableMap(new LinkedHashMap<>(m));
        prefs(c).edit().putString(PAIRS, b.toString()).apply();
        Shown.changed();
    }

    /** Whether any channel is merged by hand. */
    public static boolean any() {
        Map<String, String> m = into;
        return m != null && !m.isEmpty();
    }

    /** The key a channel's key is merged into, or null. */
    public static String into(String key) {
        Map<String, String> m = into;
        return m == null || key == null || m.isEmpty() ? null : m.get(key);
    }

    /** Every merge, merged key → the key it is merged into, in the order made. */
    public static Map<String, String> pairs(Context c) {
        load(c);
        return into;
    }

    /** Whether a channel is out of the lists for being merged into another: itself, or one of its quality versions. */
    public static boolean away(Channel ch) {
        Map<String, String> m = into;
        if (ch == null || m == null || m.isEmpty()) return false;
        if (m.containsKey(ch.key)) return true;
        if (!ChannelEdits.merging() || ch.versions == null) return false;
        for (Channel v : ch.versions.all) if (m.containsKey(v.key)) return true;
        return false;
    }

    /** The keys merged into a channel (into its own key, or one of its versions'), in the order made; empty for none. */
    public static List<String> keysInto(Channel main) {
        Map<String, String> m = into;
        if (main == null || m == null || m.isEmpty()) return Collections.emptyList();
        List<String> out = null;
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (!of(main, e.getValue())) continue;
            if (out == null) out = new ArrayList<>(2);
            out.add(e.getKey());
        }
        return out == null ? Collections.emptyList() : out;
    }

    /** The channels merged into main that the playlists have now (their stream, guide and catch-up stand in for it). */
    public static List<Channel> standIns(Channel main) {
        List<String> keys = keysInto(main);
        Function<String, Channel> l = lookup;
        if (keys.isEmpty() || l == null) return Collections.emptyList();
        List<Channel> out = new ArrayList<>(keys.size());
        for (String k : keys) {
            Channel c = l.apply(k);
            if (c != null && !of(main, c.key)) out.add(c);
        }
        return out;
    }

    /** Whether key is main's own or one of its versions'. */
    private static boolean of(Channel main, String key) {
        if (main.key.equals(key)) return true;
        if (!ChannelEdits.merging() || main.versions == null) return false;
        for (Channel v : main.versions.all) if (v.key.equals(key)) return true;
        return false;
    }

    /** Pure: why merged can't be merged into main, or null when it can. */
    static String refusal(Map<String, String> m, Channel merged, Channel main) {
        if (merged == null || main == null || of(main, merged.key) || of(merged, main.key)) return CANT;
        if (m.containsKey(main.key)) return CANT;
        if (ChannelEdits.merging() && main.versions != null) for (Channel v : main.versions.all) if (m.containsKey(v.key)) return CANT;
        return null;
    }

    /**
     * merged joins main: out of the lists, standing in for it. Those merged into merged go on to main. Null when done, else
     * why not (CANT).
     */
    public static String merge(Context c, Channel merged, Channel main) {
        load(c);
        Map<String, String> m = new LinkedHashMap<>(into);
        String why = refusal(m, merged, main);
        if (why != null) return why;
        for (Map.Entry<String, String> e : m.entrySet()) if (of(merged, e.getValue())) e.setValue(main.key);
        m.remove(merged.key);
        m.put(merged.key, main.key);
        save(c, m);
        return null;
    }

    /** One merge undone (by the merged channel's key): it is back in the lists. */
    public static boolean separate(Context c, String mergedKey) {
        load(c);
        if (!into.containsKey(mergedKey)) return false;
        Map<String, String> m = new LinkedHashMap<>(into);
        m.remove(mergedKey);
        save(c, m);
        return true;
    }

    /** Every channel merged into main back in the lists; how many. */
    public static int separateFrom(Context c, Channel main) {
        load(c);
        List<String> keys = keysInto(main);
        if (keys.isEmpty()) return 0;
        Map<String, String> m = new LinkedHashMap<>(into);
        for (String k : keys) m.remove(k);
        save(c, m);
        return keys.size();
    }

    /** Separate all: every merge undone; how many. */
    public static int separateAll(Context c) {
        load(c);
        int n = into.size();
        if (n > 0) save(c, new LinkedHashMap<>());
        return n;
    }

    /** Tests: forgotten, to be read again. */
    public static void forget() {
        into = null;
    }
}
