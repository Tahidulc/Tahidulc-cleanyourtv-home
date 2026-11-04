package com.gathin.tvhomephone;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The phone's copy of what it shares with the TVs (SYNC.md): My list, ratings, the Film night plan, Coming up's mutes,
 * reminders and starred clubs, as items merged last writer wins. Kept in the app's own preferences; the sync client
 * (sync package) swaps changes with the TVs, and every screen reads and writes through here.
 */
public final class SyncStore {
    private static final String PREFS = "sync_store";
    private static final long TOMBSTONE_MS = 60L * 24 * 3600_000L;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private SyncStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** This device's name in items' "from". */
    public static String device() {
        String m = android.os.Build.MODEL;
        return m == null || m.isEmpty() ? "phone" : m;
    }

    private static String id(String type, String key) { return type + "|" + key; }

    /** Every live item's data of one type, newest change first. */
    public static synchronized List<JSONObject> list(Context c, String type) {
        List<JSONObject> out = new ArrayList<>();
        List<JSONObject> items = new ArrayList<>();
        for (Object v : prefs(c).getAll().values()) {
            JSONObject it = parse(v);
            if (it != null && type.equals(it.optString("type")) && !it.optBoolean("deleted")) items.add(it);
        }
        items.sort((a, b) -> Long.compare(b.optLong("updated"), a.optLong("updated")));
        for (JSONObject it : items) {
            JSONObject d = it.optJSONObject("data");
            out.add(d == null ? new JSONObject() : d);
        }
        return out;
    }

    /** One item's data, or null when there is none (or it was deleted). */
    public static synchronized JSONObject get(Context c, String type, String key) {
        JSONObject it = parse(prefs(c).getAll().get(id(type, key)));
        return it == null || it.optBoolean("deleted") ? null : it.optJSONObject("data");
    }

    public static void put(Context c, String type, String key, JSONObject data) {
        write(c, item(type, key, data, System.currentTimeMillis(), false, device()));
    }

    public static void delete(Context c, String type, String key) {
        write(c, item(type, key, new JSONObject(), System.currentTimeMillis(), true, device()));
    }

    private static void write(Context c, JSONObject it) {
        synchronized (SyncStore.class) {
            prefs(c).edit().putString(id(it.optString("type"), it.optString("key")), it.toString()).apply();
        }
        changed();
    }

    /** Every item that changed after since (this device's clock), tombstones included: what goes to a TV. */
    public static synchronized JSONArray changedSince(Context c, long since) {
        JSONArray out = new JSONArray();
        for (Object v : prefs(c).getAll().values()) {
            JSONObject it = parse(v);
            if (it != null && it.optLong("seen", it.optLong("updated")) > since) out.put(strip(it));
        }
        return out;
    }

    /** Items from a TV merged in (last writer wins); true when anything here changed. */
    public static boolean merge(Context c, JSONArray items) {
        boolean any = false;
        long now = System.currentTimeMillis();
        synchronized (SyncStore.class) {
            SharedPreferences p = prefs(c);
            SharedPreferences.Editor e = p.edit();
            for (int i = 0; items != null && i < items.length(); i++) {
                JSONObject in = items.optJSONObject(i);
                if (in == null || in.optString("type").isEmpty() || in.optString("key").isEmpty()) continue;
                String k = id(in.optString("type"), in.optString("key"));
                JSONObject had = parse(p.getAll().get(k));
                if (!wins(in, had)) continue;
                JSONObject kept = strip(in);
                try {
                    // When it arrived here: so it goes on to the other TVs at the next sync, even with an older clock.
                    kept.put("seen", now);
                } catch (JSONException ignored) {
                    // kept as it came
                }
                e.putString(k, kept.toString());
                any = true;
            }
            // Old tombstones go.
            for (java.util.Map.Entry<String, ?> en : p.getAll().entrySet()) {
                JSONObject it = parse(en.getValue());
                if (it != null && it.optBoolean("deleted") && now - it.optLong("updated") > TOMBSTONE_MS) e.remove(en.getKey());
            }
            e.apply();
        }
        if (any) changed();
        return any;
    }

    /** Last writer wins: the later "updated", then the greater "from" on a tie. Pure, for the tests. */
    public static boolean wins(JSONObject in, JSONObject had) {
        if (had == null) return true;
        long a = in.optLong("updated"), b = had.optLong("updated");
        if (a != b) return a > b;
        return in.optString("from").compareTo(had.optString("from")) > 0;
    }

    public static JSONObject item(String type, String key, JSONObject data, long updated, boolean deleted, String from) {
        JSONObject it = new JSONObject();
        try {
            it.put("type", type).put("key", key).put("data", data == null ? new JSONObject() : data)
                    .put("updated", updated).put("deleted", deleted).put("from", from);
        } catch (JSONException ignored) {
            // the fields are plain
        }
        return it;
    }

    private static JSONObject strip(JSONObject it) {
        JSONObject out = new JSONObject();
        for (Iterator<String> k = it.keys(); k.hasNext(); ) {
            String n = k.next();
            try {
                out.put(n, it.get(n));
            } catch (JSONException ignored) {
                // skipped
            }
        }
        return out;
    }

    private static JSONObject parse(Object v) {
        if (!(v instanceof String)) return null;
        try {
            return new JSONObject((String) v);
        } catch (JSONException e) {
            return null;
        }
    }

    public static void addListener(Runnable r) { LISTENERS.add(r); }

    public static void removeListener(Runnable r) { LISTENERS.remove(r); }

    private static void changed() {
        for (Runnable r : LISTENERS) App.main.post(r);
    }
}
