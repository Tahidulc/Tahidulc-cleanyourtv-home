package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Live TV's favourites ("Your channels", in the family's own order) and the channels watched last, on this phone only
 * (by channel key).
 */
final class Picks {
    private Picks() {}

    private static final String PREFS = "live_picks";
    private static final int RECENT_MAX = 12;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Set<String> favourites(Context c) {
        return new HashSet<>(prefs(c).getStringSet("fav", new HashSet<>()));
    }

    static boolean isFavourite(Context c, String key) {
        return prefs(c).getStringSet("fav", new HashSet<>()).contains(key);
    }

    /** Turns a favourite on or off; true when it is now one. A new favourite goes to the end of the order. */
    static boolean toggle(Context c, String key) {
        Set<String> f = favourites(c);
        boolean on = !f.remove(key);
        if (on) f.add(key);
        List<String> o = order(c);
        o.remove(key);
        if (on) o.add(key);
        prefs(c).edit().putStringSet("fav", f).putString("fav_order", new JSONArray(o).toString()).apply();
        Library.get(c).picksChanged();
        return on;
    }

    /** The favourites in the order they were put in (or dragged to); any without a place yet are not in it. */
    static List<String> order(Context c) {
        Set<String> f = favourites(c);
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("fav_order", "[]"));
            for (int i = 0; i < a.length(); i++) {
                String k = a.getString(i);
                if (f.contains(k) && !out.contains(k)) out.add(k);
            }
        } catch (org.json.JSONException ignored) {
            // no order yet
        }
        return out;
    }

    /** The favourites' new order (from Manage favourites). */
    static void setOrder(Context c, List<String> keys) {
        prefs(c).edit().putString("fav_order", new JSONArray(keys).toString()).apply();
        Library.get(c).picksChanged();
    }

    static List<String> recent(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("recent", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (org.json.JSONException ignored) {
            // none yet
        }
        return out;
    }

    static void watched(Context c, String key) {
        List<String> r = recent(c);
        r.remove(key);
        r.add(0, key);
        while (r.size() > RECENT_MAX) r.remove(r.size() - 1);
        prefs(c).edit().putString("recent", new JSONArray(r).toString()).apply();
        Library.get(c).picksChanged();
    }

    static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }
}
