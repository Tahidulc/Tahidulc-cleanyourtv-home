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
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
