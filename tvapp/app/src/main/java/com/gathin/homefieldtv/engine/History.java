package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What was watched on this TV, programme by programme, as the player's History lists it (Long Up, or its tile): a
 * programme comes in once it has been on screen for ADD_MS, live or from the provider's archive, with the time it was
 * watched; the same programme watched again moves to the end with the new time. Kept for DAYS days, MAX at most, on
 * this TV only (never synced or sent anywhere). The lists are made from the kept line; the pure parts are tested.
 */
public final class History {
    private History() {}

    /** On screen this long before a programme is kept (TiviMate's "Delay before adding to history, sec": 10). */
    public static final long ADD_MS = 10_000L;
    /** How many days are kept (its "History day count": 7), and how many entries at most. */
    public static final int DAYS = 7, MAX = 200;
    private static final String PREFS = "history";

    /** One programme watched: its channel's key, its title, start and end (0 without a guide), when, and from the archive. */
    public static final class Entry {
        public final String key, title;
        public final long start, stop, at;
        public final boolean archived;

        public Entry(String key, String title, long start, long stop, long at, boolean archived) {
            this.key = key == null ? "" : key;
            this.title = title == null ? "" : title;
            this.start = start;
            this.stop = stop;
            this.at = at;
            this.archived = archived;
        }

        /** The same programme (its channel and start; a channel without a guide is one entry). */
        boolean same(Entry o) {
            return key.equals(o.key) && start == o.start;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The entries kept, oldest first, without those older than DAYS days. */
    public static List<Entry> list(Context c, long now) {
        return prune(parse(prefs(c).getString("list", "")), now);
    }

    /** A programme watched (after ADD_MS on screen): kept, or moved to the end with its new time. */
    public static void add(Context c, Entry e) {
        if (e == null || e.key.isEmpty()) return;
        List<Entry> l = with(list(c, e.at), e);
        prefs(c).edit().putString("list", join(l)).apply();
    }

    /** One entry taken out (its delete button). */
    public static void remove(Context c, Entry e) {
        if (e == null) return;
        List<Entry> l = new ArrayList<>(list(c, System.currentTimeMillis()));
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).same(e)) l.remove(i);
        prefs(c).edit().putString("list", join(l)).apply();
    }

    /** Every entry taken out (the bin at the top). */
    public static void clear(Context c) {
        prefs(c).edit().remove("list").apply();
    }

    // ---- pure ---------------------------------------------------------------------------------------------------

    /** Pure: the list with e at its end (an entry for the same programme taken out first), MAX at most. */
    public static List<Entry> with(List<Entry> list, Entry e) {
        List<Entry> out = new ArrayList<>(list.size() + 1);
        for (Entry x : list) if (!x.same(e)) out.add(x);
        out.add(e);
        while (out.size() > MAX) out.remove(0);
        return out;
    }

    /** Pure: without the entries watched more than DAYS days before now, oldest first. */
    public static List<Entry> prune(List<Entry> list, long now) {
        long from = now - DAYS * 24L * 3600_000L;
        List<Entry> out = new ArrayList<>(list.size());
        for (Entry e : list) if (e.at >= from) out.add(e);
        Collections.sort(out, (a, b) -> Long.compare(a.at, b.at));
        return out;
    }

    /** Pure: entries as the kept line. */
    public static String join(List<Entry> list) {
        JSONArray a = new JSONArray();
        try {
            for (Entry e : list) {
                a.put(new JSONObject().put("k", e.key).put("t", e.title).put("s", e.start).put("e", e.stop)
                        .put("a", e.at).put("c", e.archived));
            }
        } catch (Exception ignored) {
            // what was made so far
        }
        return a.toString();
    }

    /** Pure: the kept line read (anything unreadable left out). */
    public static List<Entry> parse(String raw) {
        List<Entry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || o.optString("k").isEmpty()) continue;
                out.add(new Entry(o.optString("k"), o.optString("t"), o.optLong("s"), o.optLong("e"), o.optLong("a"),
                        o.optBoolean("c")));
            }
        } catch (Exception ignored) {
            // none readable
        }
        return out;
    }
}
