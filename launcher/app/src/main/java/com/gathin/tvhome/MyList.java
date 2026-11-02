package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * My list: films and series kept for later, added and taken off from a title's panel (hold OK on a poster). Kept in
 * the app's own preferences, newest first: {id, tv, title, poster, backdrop, added, apps (it was on one of the
 * TV's streaming apps when added)}. ReleaseAlerts says when one not on the TV's apps then arrives on one.
 */
final class MyList {
    private MyList() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("mylist", Context.MODE_PRIVATE);
    }

    static synchronized List<JSONObject> all(Context c) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getJSONObject(i));
        } catch (Exception ignored) {
            // an empty list
        }
        return out;
    }

    static boolean has(Context c, int id, boolean tv) {
        for (JSONObject o : all(c)) if (o.optInt("id") == id && o.optBoolean("tv") == tv) return true;
        return false;
    }

    /** On the list now (true) or off it (false). */
    static synchronized boolean toggle(Context c, PagesV2.Title t, boolean onApps) {
        List<JSONObject> list = all(c);
        for (int i = 0; i < list.size(); i++) {
            JSONObject o = list.get(i);
            if (o.optInt("id") == t.id && o.optBoolean("tv") == t.tv) {
                list.remove(i);
                store(c, list);
                return false;
            }
        }
        try {
            list.add(0, new JSONObject().put("id", t.id).put("tv", t.tv).put("title", t.title).put("poster", t.poster)
                    .put("backdrop", t.backdrop).put("added", System.currentTimeMillis()).put("apps", onApps));
        } catch (Exception ignored) {
            // not added
        }
        while (list.size() > 100) list.remove(list.size() - 1);
        store(c, list);
        return true;
    }

    /** Marks one as now on the TV's apps (told once). */
    static synchronized void arrived(Context c, int id, boolean tv) {
        List<JSONObject> list = all(c);
        for (JSONObject o : list) {
            if (o.optInt("id") != id || o.optBoolean("tv") != tv) continue;
            try {
                o.put("apps", true);
            } catch (Exception ignored) {
                // left as it was
            }
        }
        store(c, list);
    }

    private static void store(Context c, List<JSONObject> list) {
        JSONArray a = new JSONArray();
        for (JSONObject o : list) a.put(o);
        prefs(c).edit().putString("list", a.toString()).apply();
    }

    /** The list as poster titles (Discover's My list tab). */
    static List<PagesV2.Title> titles(Context c) {
        List<PagesV2.Title> out = new ArrayList<>();
        for (JSONObject o : all(c)) {
            PagesV2.Title t = new PagesV2.Title();
            t.id = o.optInt("id");
            t.tv = o.optBoolean("tv");
            t.title = o.optString("title");
            t.poster = o.optString("poster", null);
            t.backdrop = o.optString("backdrop", null);
            t.overview = "";
            t.year = "";
            t.what = "";
            if (t.poster != null && !t.poster.isEmpty() && !"null".equals(t.poster)) out.add(t);
        }
        return out;
    }
}
