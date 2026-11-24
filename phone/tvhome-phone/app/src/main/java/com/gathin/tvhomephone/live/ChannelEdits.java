package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.tvhomephone.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The family's own edits to Live TV's channels, as Homefield TV lets them make from a channel's menu and Settings ›
 * Channels &amp; categories: one row per channel (merge qualities, on as it comes), the Quality picked for a channel in
 * several, a name of their own (Rename channel), the logo chosen (Change logo), a place in its category (Move channel)
 * and how the lists are sorted (Channels sorting; a category's own is Groups'). By the channel's key, so they hold
 * across reloads; a channel shown as one row in several qualities is edited as a whole (its every version's key).
 * Kept on this phone (the "live_edits" preferences), read once into memory and replaced whole on a change.
 */
public final class ChannelEdits {
    private ChannelEdits() {}

    /** Channels sorting, and a category's own. */
    static final int PLAYLIST = 0, NAME = 1, NUMBER = 2, MANUAL = 3;
    static final int[] SORT_NAMES = {R.string.ps_sort_playlist, R.string.ps_sort_name, R.string.ps_sort_number, R.string.ps_sort_manual};
    /** A name of the family's own is at most this long. */
    static final int NAME_MAX = 60;
    static final String PREFS = "live_edits", MERGE = "merge", SORT = "sort", NAMES = "names", LOGOS = "logos", PICKS = "picks",
            ORDERS = "orders";

    /** Everything kept, at one moment. */
    static final class State {
        final boolean merge;
        final int sort;
        /** A channel's key: its name, its logo, the version picked for it (by key). */
        final Map<String, String> names, logos, picks;
        /** A category: its channels' keys in the family's order (Move channel). */
        final Map<String, List<String>> orders;

        State(boolean merge, int sort, Map<String, String> names, Map<String, String> logos, Map<String, String> picks,
              Map<String, List<String>> orders) {
            this.merge = merge;
            this.sort = sort;
            this.names = Collections.unmodifiableMap(names);
            this.logos = Collections.unmodifiableMap(logos);
            this.picks = Collections.unmodifiableMap(picks);
            this.orders = Collections.unmodifiableMap(orders);
        }

        /** The first of the keys that has one in m (the channel's own first, then its versions'), or null. */
        static String first(Map<String, String> m, Collection<String> keys) {
            for (String k : keys) {
                String v = m.get(k);
                if (v != null && !v.isEmpty()) return v;
            }
            return null;
        }
    }

    private static volatile State state;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static State state(Context c) {
        State s = state;
        if (s == null) state = s = read(prefs(c));
        return s;
    }

    private static State read(SharedPreferences p) {
        return new State(p.getBoolean(MERGE, true), clampSort(p.getInt(SORT, PLAYLIST)), strings(p.getString(NAMES, "")),
                strings(p.getString(LOGOS, "")), strings(p.getString(PICKS, "")), lists(p.getString(ORDERS, "")));
    }

    private static synchronized void write(Context c, State s) {
        state = s;
        prefs(c).edit().putBoolean(MERGE, s.merge).putInt(SORT, s.sort).putString(NAMES, json(s.names).toString())
                .putString(LOGOS, json(s.logos).toString()).putString(PICKS, json(s.picks).toString())
                .putString(ORDERS, jsonLists(s.orders).toString()).apply();
    }

    // ---- one row per channel, and its Quality ------------------------------------------------------------------------

    public static boolean merging(Context c) {
        return state(c).merge;
    }

    static void setMerging(Context c, boolean on) {
        State s = state(c);
        if (s.merge == on) return;
        write(c, new State(on, s.sort, s.names, s.logos, s.picks, s.orders));
        Library.get(c).editsChanged();
    }

    /** The version picked for a channel (by its versions' keys), or null for its best. */
    static String pick(Context c, Collection<String> keys) {
        return State.first(state(c).picks, keys);
    }

    /** A version picked for the channel whose versions' keys these are (null: its best again). */
    static void setPick(Context c, Collection<String> keys, String version) {
        State s = state(c);
        Map<String, String> m = new HashMap<>(s.picks);
        for (String k : keys) {
            if (version == null || version.isEmpty()) m.remove(k);
            else m.put(k, version);
        }
        write(c, new State(s.merge, s.sort, s.names, s.logos, m, s.orders));
        Library.get(c).picksOfQualityChanged();
    }

    // ---- names and logos ----------------------------------------------------------------------------------------------

    /** The family's name for a channel (its versions' keys), or null for the playlist's. */
    static String name(Context c, Collection<String> keys) {
        return State.first(state(c).names, keys);
    }

    /** A name of the family's own for every version of a channel; nothing typed (or the playlist's) is the playlist's again. */
    static void rename(Context c, Collection<String> keys, String typed, String playlistName) {
        String name = clean(typed);
        if (name.equals(playlistName == null ? "" : playlistName.trim())) name = "";
        State s = state(c);
        Map<String, String> m = new HashMap<>(s.names);
        for (String k : keys) {
            if (name.isEmpty()) m.remove(k);
            else m.put(k, name);
        }
        write(c, new State(s.merge, s.sort, m, s.logos, s.picks, s.orders));
        Library.get(c).editsChanged();
    }

    /** The logo chosen for a channel (its versions' keys), or null for its own. */
    static String logo(Context c, Collection<String> keys) {
        return State.first(state(c).logos, keys);
    }

    /** A logo for every version of a channel (an address), or its own again (null or empty). */
    static void setLogo(Context c, Collection<String> keys, String logo) {
        State s = state(c);
        Map<String, String> m = new HashMap<>(s.logos);
        for (String k : keys) {
            if (logo == null || logo.isEmpty()) m.remove(k);
            else m.put(k, logo);
        }
        write(c, new State(s.merge, s.sort, s.names, m, s.picks, s.orders));
        Library.get(c).editsChanged();
    }

    /** How many channels the family renamed (each key once). */
    static int renamedCount(Context c) {
        return state(c).names.size();
    }

    // ---- the order --------------------------------------------------------------------------------------------------

    public static int sort(Context c) {
        return state(c).sort;
    }

    static void setSort(Context c, int sort) {
        State s = state(c);
        write(c, new State(s.merge, clampSort(sort), s.names, s.logos, s.picks, s.orders));
        Library.get(c).orderChanged();
    }

    /** A category's channels in the family's order (Move channel), by key; empty when they haven't moved any. */
    static List<String> order(Context c, String group) {
        List<String> l = state(c).orders.get(group == null ? "" : group);
        return l == null ? Collections.emptyList() : l;
    }

    static void setOrder(Context c, String group, List<String> keys) {
        State s = state(c);
        Map<String, List<String>> m = new LinkedHashMap<>(s.orders);
        m.put(group == null ? "" : group, Collections.unmodifiableList(new ArrayList<>(keys)));
        write(c, new State(s.merge, s.sort, s.names, s.logos, s.picks, m));
        Library.get(c).orderChanged();
    }

    /** Everything forgotten (Live TV's Remove everything). */
    static void clear(Context c) {
        prefs(c).edit().clear().apply();
        state = null;
    }

    // ---- pure ---------------------------------------------------------------------------------------------------

    static int clampSort(int s) {
        return s >= PLAYLIST && s <= MANUAL ? s : PLAYLIST;
    }

    /** A name as typed, made tidy: no spaces at its ends or doubled, no line breaks or tabs, at most NAME_MAX long. */
    static String clean(String typed) {
        if (typed == null) return "";
        String t = typed.replaceAll("[\\t\\r\\n]+", " ").trim().replaceAll(" {2,}", " ");
        return t.length() > NAME_MAX ? t.substring(0, NAME_MAX).trim() : t;
    }

    /** The name a channel shows: the family's own, else its channel's without the quality (one row in several), else its own. */
    static String shown(String playlistName, String own, String rowTitle) {
        if (own != null && !own.isEmpty()) return own;
        if (rowTitle != null && !rowTitle.isEmpty()) return rowTitle;
        return playlistName == null ? "" : playlistName;
    }

    /**
     * The places in the order a sorting says: as given (the playlist's), by name (names[p], already plain and lower
     * case), by number (numbers[p], none last) or by the family's order (manual[p], none last); ties as given.
     */
    static Integer[] sorted(Integer[] places, int mode, String[] names, int[] numbers, int[] manual) {
        Integer[] out = places.clone();
        if (mode == PLAYLIST || out.length < 2) return out;
        Map<Integer, Integer> given = new HashMap<>();
        for (int i = 0; i < out.length; i++) given.put(out[i], i);
        java.util.Arrays.sort(out, (a, b) -> {
            int c = 0;
            if (mode == NAME) c = names[a].compareTo(names[b]);
            else if (mode == NUMBER) c = Integer.compare(numbers[a] > 0 ? numbers[a] : Integer.MAX_VALUE, numbers[b] > 0 ? numbers[b] : Integer.MAX_VALUE);
            else if (mode == MANUAL) c = Integer.compare(manual[a], manual[b]);
            return c != 0 ? c : Integer.compare(given.get(a), given.get(b));
        });
        return out;
    }

    static JSONObject json(Map<String, String> m) {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, String> e : m.entrySet()) o.put(e.getKey(), e.getValue());
        } catch (JSONException ignored) {
            // plain strings
        }
        return o;
    }

    static Map<String, String> strings(String json) {
        Map<String, String> out = new HashMap<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONObject o = new JSONObject(json);
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                String v = o.optString(k, "");
                if (!v.isEmpty()) out.put(k, v);
            }
        } catch (JSONException ignored) {
            // nothing kept
        }
        return out;
    }

    static JSONObject jsonLists(Map<String, List<String>> m) {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, List<String>> e : m.entrySet()) o.put(e.getKey(), new JSONArray(e.getValue()));
        } catch (JSONException ignored) {
            // plain strings
        }
        return o;
    }

    static Map<String, List<String>> lists(String json) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONObject o = new JSONObject(json);
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONArray a = o.optJSONArray(k);
                if (a == null) continue;
                List<String> l = new ArrayList<>(a.length());
                for (int i = 0; i < a.length(); i++) {
                    String v = a.optString(i, "");
                    if (!v.isEmpty() && !l.contains(v)) l.add(v);
                }
                out.put(k, Collections.unmodifiableList(l));
            }
        } catch (JSONException ignored) {
            // nothing kept
        }
        return out;
    }
}
