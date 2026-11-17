package com.gathin.homefieldtv.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The family's shared things as Homefield TV last had them (phone/tvhome-phone/SYNC.md): items {type, key, data,
 * updated, deleted, from}, last writer wins, a removal kept as a tombstone for 60 days. In this app's own private
 * storage, never on /sdcard; a playlist's item holds its login, so nothing here is ever logged or shown.
 */
public final class SyncItems {
    private SyncItems() {}

    private static final String PREFS = "sync_store";
    static final long TOMBSTONE_MS = 60L * 24 * 3600_000L;
    /** The kinds Homefield TV uses; the launcher's others (My list, ratings and the rest) are not kept here. */
    static final java.util.Set<String> KEPT = new java.util.HashSet<>(java.util.Arrays.asList("playlist", "livefav", "liverecent",
            "resume", "setting"));

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** This TV app's name in the items it changes. */
    static String device() {
        String m = Build.MODEL;
        return "Homefield TV" + (m == null || m.trim().isEmpty() ? "" : " (" + m.trim() + ")");
    }

    private static String id(String type, String key) {
        return type + "|" + key;
    }

    private static JSONObject parse(Object v) {
        if (!(v instanceof String)) return null;
        try {
            return new JSONObject((String) v);
        } catch (JSONException e) {
            return null;
        }
    }

    /** One item's data, or null when there is none (or it was removed). */
    public static synchronized JSONObject get(Context c, String type, String key) {
        JSONObject it = parse(prefs(c).getAll().get(id(type, key)));
        return it == null || it.optBoolean("deleted") ? null : it.optJSONObject("data");
    }

    /** Every item of one type as kept, tombstones included. */
    public static synchronized List<JSONObject> items(Context c, String type) {
        List<JSONObject> out = new ArrayList<>();
        for (Object v : prefs(c).getAll().values()) {
            JSONObject it = parse(v);
            if (it != null && type.equals(it.optString("type"))) out.add(it);
        }
        return out;
    }

    /** A change made here, stamped now. */
    public static void put(Context c, String type, String key, JSONObject data) {
        write(c, item(type, key, data, System.currentTimeMillis(), false, device()));
    }

    /** A removal made here: a tombstone, so the others remove it too. */
    public static void delete(Context c, String type, String key) {
        write(c, item(type, key, new JSONObject(), System.currentTimeMillis(), true, device()));
    }

    private static synchronized void write(Context c, JSONObject it) {
        try {
            it.put("seen", it.optLong("updated"));
        } catch (JSONException ignored) {
            // plain values
        }
        prefs(c).edit().putString(id(it.optString("type"), it.optString("key")), it.toString()).apply();
    }

    /** Every item changed here after since (this TV's clock), tombstones too: what goes to the launcher. */
    public static synchronized JSONArray changedSince(Context c, long since) {
        JSONArray out = new JSONArray();
        for (Object v : prefs(c).getAll().values()) {
            JSONObject it = parse(v);
            if (it == null || it.optLong("seen", -1) <= since) continue;
            it.remove("seen");
            out.put(it);
        }
        return out;
    }

    /**
     * Items from the launcher merged in (last writer wins); true when anything here changed. They are not sent back:
     * the launcher already has them.
     */
    public static synchronized boolean merge(Context c, JSONArray items) {
        boolean any = false;
        long now = System.currentTimeMillis();
        SharedPreferences p = prefs(c);
        Map<String, ?> all = p.getAll();
        SharedPreferences.Editor e = p.edit();
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject in = items.optJSONObject(i);
            if (in == null || !KEPT.contains(in.optString("type")) || in.optString("key").isEmpty()) continue;
            String k = id(in.optString("type"), in.optString("key"));
            if (!wins(in, parse(all.get(k)))) continue;
            JSONObject kept = copy(in);
            try {
                kept.put("seen", -1L);
            } catch (JSONException ignored) {
                // kept as it came
            }
            e.putString(k, kept.toString());
            any = true;
        }
        for (Map.Entry<String, ?> en : all.entrySet()) {
            JSONObject it = parse(en.getValue());
            if (it != null && it.optBoolean("deleted") && now - it.optLong("updated") > TOMBSTONE_MS) e.remove(en.getKey());
        }
        e.apply();
        return any;
    }

    /** Pure: last writer wins, the later "updated", then the greater "from" on a tie. */
    public static boolean wins(JSONObject in, JSONObject had) {
        if (had == null) return true;
        long a = in.optLong("updated"), b = had.optLong("updated");
        if (a != b) return a > b;
        return in.optString("from").compareTo(had.optString("from")) > 0;
    }

    /** Pure: an item. */
    public static JSONObject item(String type, String key, JSONObject data, long updated, boolean deleted, String from) {
        JSONObject it = new JSONObject();
        try {
            it.put("type", type).put("key", key).put("data", data == null ? new JSONObject() : data).put("updated", updated)
                    .put("deleted", deleted).put("from", from);
        } catch (JSONException ignored) {
            // plain values
        }
        return it;
    }

    private static JSONObject copy(JSONObject o) {
        JSONObject out = new JSONObject();
        for (Iterator<String> k = o.keys(); k.hasNext(); ) {
            String n = k.next();
            try {
                out.put(n, o.get(n));
            } catch (JSONException ignored) {
                // left out
            }
        }
        return out;
    }

    /** Everything here gone (Settings, Remove everything). */
    public static synchronized void clear(Context c) {
        prefs(c).edit().clear().apply();
    }
}
