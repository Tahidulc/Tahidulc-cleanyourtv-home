package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The family's own list of films and series (the heart on a title's page, the side panel's My list), kept on this TV,
 * the one added last first. Keys are the titles' own (Catalog.Title.key), the same across a playlist's refreshes.
 */
public final class MyList {
    private MyList() {}

    static final int MAX = 300;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("my_list", Context.MODE_PRIVATE);
    }

    /** The keys as last read (and as a set), read again only when the kept line itself changes. */
    private static String readFrom;
    private static List<String> read = Collections.emptyList();
    private static Set<String> readSet = Collections.emptySet();

    private static List<String> kept(Context c) {
        String raw = prefs(c).getString("keys", "");
        //noinspection StringEquality: the very line read last time, or a new one
        if (raw != readFrom) {
            read = Collections.unmodifiableList(parse(raw));
            readSet = new HashSet<>(read);
            readFrom = raw;
        }
        return read;
    }

    /** The keys, the one added last first. */
    public static synchronized List<String> keys(Context c) {
        return new ArrayList<>(kept(c));
    }

    /** Whether a title is on the list (at once: every poster asks). */
    public static synchronized boolean has(Context c, String key) {
        if (key == null) return false;
        kept(c);
        return readSet.contains(key);
    }

    /** On the list or off it; true when it is on it now. */
    public static synchronized boolean toggle(Context c, String key) {
        if (key == null || key.isEmpty()) return false;
        List<String> all = keys(c);
        boolean on = !all.remove(key);
        if (on) all.add(0, key);
        prefs(c).edit().putString("keys", join(all)).apply();
        return on;
    }

    /** Pure: the kept line as keys, without blanks or repeats, at most MAX. */
    static List<String> parse(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String k : s.split("\n")) {
            String t = k.trim();
            if (!t.isEmpty() && !out.contains(t) && out.size() < MAX) out.add(t);
        }
        return out;
    }

    /** Pure: the keys as one kept line. */
    static String join(List<String> keys) {
        StringBuilder b = new StringBuilder();
        for (String k : keys) {
            if (k == null || k.trim().isEmpty()) continue;
            if (b.length() > 0) b.append('\n');
            b.append(k.trim());
        }
        return b.toString();
    }
}
