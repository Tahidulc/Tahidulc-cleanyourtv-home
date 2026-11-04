package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The events a person asked to be reminded of (a long press of OK on a fixture in v2's Sport page): kept on the TV,
 * and turned into an alert by GoalWatchService a few minutes before each starts, on Home or over an app.
 */
final class Reminders {
    /** How long before the start the alert comes. */
    static final long BEFORE_MS = 5 * 60_000L;
    private static final String PREFS = "event_reminders", KEY = "list";

    private Reminders() {
    }

    /** {title, start ms, channel, channel logo url}; false when it was already set (it is then taken off instead). */
    static synchronized boolean toggle(Context c, String title, long start, String channel, String logo) {
        List<JSONObject> all = read(c);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).optString("t").equals(title) && all.get(i).optLong("s") == start) {
                all.remove(i);
                write(c, all);
                return false;
            }
        }
        try {
            all.add(new JSONObject().put("t", title).put("s", start).put("c", channel == null ? "" : channel).put("l", logo == null ? "" : logo));
        } catch (Exception ignored) {
        }
        write(c, all);
        return true;
    }

    /** Whether a reminder is set for this event (its title and start, as toggle keeps them). */
    static synchronized boolean has(Context c, String title, long start) {
        for (JSONObject o : read(c)) if (o.optString("t").equals(title) && o.optLong("s") == start) return true;
        return false;
    }

    /** The reminders now due (and drops them, and any long past). */
    static synchronized List<JSONObject> due(Context c, long now) {
        List<JSONObject> all = read(c), due = new ArrayList<>(), keep = new ArrayList<>();
        for (JSONObject o : all) {
            long s = o.optLong("s");
            if (s - now <= BEFORE_MS) { if (now - s < 30 * 60_000L) due.add(o); }
            else keep.add(o);
        }
        if (keep.size() != all.size()) write(c, keep);
        return due;
    }

    private static List<JSONObject> read(Context c) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getJSONObject(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void write(Context c, List<JSONObject> all) {
        JSONArray a = new JSONArray();
        for (JSONObject o : all) a.put(o);
        prefs(c).edit().putString(KEY, a.toString()).apply();
        SyncHub.changed(c);
    }

    /** Every reminder set: {t title, s start, c channel, l logo, k its sync key when it came from elsewhere, kind}. */
    static synchronized List<JSONObject> all(Context c) {
        return read(c);
    }

    /** A reminder set on a phone or the other TV (SyncHub), under its key; one already set for it is replaced. */
    static synchronized void putSynced(Context c, String key, String title, long start, String channel, String logo, String kind) {
        List<JSONObject> all = read(c);
        for (int i = all.size() - 1; i >= 0; i--) if (key.equals(syncKey(all.get(i)))) all.remove(i);
        try {
            JSONObject o = new JSONObject().put("t", title).put("s", start).put("c", channel == null ? "" : channel)
                    .put("l", logo == null ? "" : logo).put("k", key);
            if (kind != null && !kind.isEmpty()) o.put("kind", kind);
            all.add(o);
        } catch (Exception ignored) {
        }
        write(c, all);
    }

    /** A reminder taken off elsewhere (SyncHub); false when it was not set here. */
    static synchronized boolean removeSynced(Context c, String key) {
        List<JSONObject> all = read(c);
        boolean any = false;
        for (int i = all.size() - 1; i >= 0; i--) if (key.equals(syncKey(all.get(i)))) { all.remove(i); any = true; }
        if (any) write(c, all);
        return any;
    }

    private static String syncKey(JSONObject o) {
        return SyncRules.reminderKey(o.optString("k", ""), o.optString("c"), o.optLong("s"), o.optString("t"));
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
