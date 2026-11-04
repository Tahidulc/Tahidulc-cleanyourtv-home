package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Live TV's hidden channels, on this phone only: gone from the guide, the lists, search, Your channels, Similar
 * channels and swipe-to-change until unhidden from the tab's More menu. Each is kept by a key that outlasts a refresh of
 * the playlist: its tvg-id with its name made plain (so hiding the SD copy of a channel doesn't hide the HD copy that
 * shares its guide id), or, with no tvg-id, the plain name and a short hash of the stream's address (the address itself,
 * which can hold the login, is never stored).
 */
final class Hidden {
    private Hidden() {}

    private static final String PREFS = "live_hidden", KEY = "hidden";

    // ---- the pure part (tested) -------------------------------------------------------------------------------

    /** The name as the key keeps it: small capitals and accents made plain, lower case, spaces single. */
    static String plainName(String name) {
        if (name == null) return "";
        return Names.plain(name).toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** A channel's lasting key: "id:" tvg-id and name, or "nm:" name and the address's hash. */
    static String key(String tvgId, String name, String url) {
        String id = tvgId == null ? "" : tvgId.trim().toLowerCase(Locale.ROOT);
        if (!id.isEmpty()) return "id:" + id + "|" + plainName(name);
        return "nm:" + plainName(name) + "|" + Long.toHexString(fnv(url == null ? "" : url.trim()));
    }

    static String key(Channel c) {
        return key(c.tvgId, c.name, c.url);
    }

    /** FNV-1a, 64 bits: the same on every phone and every run (unlike a salted hash). */
    static long fnv(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            h ^= c & 0xff;
            h *= 0x100000001b3L;
            h ^= c >>> 8;
            h *= 0x100000001b3L;
        }
        return h;
    }

    /** The channels that aren't hidden, in their order. */
    static List<Channel> visible(Collection<Channel> channels, Set<String> hidden) {
        List<Channel> out = new ArrayList<>(channels.size());
        for (Channel c : channels) if (hidden == null || hidden.isEmpty() || !hidden.contains(key(c))) out.add(c);
        return out;
    }

    /** For each of the keys, whether it is hidden. */
    static boolean[] mask(String[] keys, Set<String> hidden) {
        boolean[] m = new boolean[keys.length];
        if (hidden == null || hidden.isEmpty()) return m;
        for (int i = 0; i < keys.length; i++) m[i] = keys[i] != null && hidden.contains(keys[i]);
        return m;
    }

    /**
     * Swipe-to-change: the place by steps (+1 the next, -1 the previous, round the end) in a list of size, passing
     * over the places to skip (hidden channels). From outside the list (-1) the first (or last) is the first step.
     * The place itself when it is the only one left; -1 when every place is skipped.
     */
    static int step(int size, int from, int by, IntPredicate skip) {
        if (size <= 0 || by == 0) return from >= 0 && from < size ? from : -1;
        int dir = by > 0 ? 1 : -1, steps = Math.abs(by);
        int at = from >= 0 && from < size ? from : dir > 0 ? -1 : size;
        for (int s = 0; s < steps; s++) {
            int i = at, tried = 0;
            do {
                i = Math.floorMod(i + dir, size);
                tried++;
            } while (skip != null && skip.test(i) && tried < size);
            if (skip != null && skip.test(i)) return -1;
            at = i;
        }
        return at;
    }

    // ---- this phone's list ----------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The hidden channels, key to the name they had, in the order they were hidden. */
    static LinkedHashMap<String, String> all(Context c) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        try {
            JSONObject o = new JSONObject(prefs(c).getString(KEY, "{}"));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                out.put(k, o.optString(k, ""));
            }
        } catch (JSONException ignored) {
            // none yet
        }
        return out;
    }

    static Set<String> keys(Context c) {
        return all(c).keySet();
    }

    private static void save(Context c, Map<String, String> m) {
        prefs(c).edit().putString(KEY, new JSONObject(m).toString()).apply();
        Library.get(c).hiddenChanged();
    }

    static void hide(Context c, Channel ch) {
        LinkedHashMap<String, String> m = all(c);
        m.put(key(ch), ch.name);
        save(c, m);
    }

    static void unhide(Context c, String key) {
        LinkedHashMap<String, String> m = all(c);
        if (m.remove(key) != null) save(c, m);
    }

    static void unhideAll(Context c) {
        save(c, new LinkedHashMap<>());
    }

    /** With the playlist removed from the phone. */
    static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }
}
