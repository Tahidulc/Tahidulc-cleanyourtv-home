package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

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

    /** The keys, the one added last first. */
    public static synchronized List<String> keys(Context c) {
        return parse(prefs(c).getString("keys", ""));
    }

    public static boolean has(Context c, String key) {
        return key != null && keys(c).contains(key);
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
