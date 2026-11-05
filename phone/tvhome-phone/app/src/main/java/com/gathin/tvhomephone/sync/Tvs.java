package com.gathin.tvhomephone.sync;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.SyncStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The house's TVs this phone is paired with, and what the rest of the app asks of them: the TMDB key (from a paired
 * TV, kept on the phone, never shown or logged), Play on TV, and what is being watched on them (SYNC.md).
 * Each TV: {name, host, port, token, since, pushed, synced, error}.
 */
public final class Tvs {
    private static final String PREFS = "tvs";
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private Tvs() {}

    /** An answer on the main thread: the value, or null with why. */
    public interface Result<T> {
        void done(T value, String error);
    }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The paired TVs, as stored. */
    public static synchronized List<JSONObject> list(Context c) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) out.add(a.optJSONObject(i));
        } catch (JSONException ignored) {
            // none
        }
        return out;
    }

    static synchronized void save(Context c, List<JSONObject> tvs) {
        JSONArray a = new JSONArray();
        for (JSONObject t : tvs) a.put(t);
        prefs(c).edit().putString("list", a.toString()).apply();
        changed();
    }

    /** One TV's record changed (its address, its sync state): written back over the stored one of that name. */
    static synchronized void update(Context c, JSONObject tv) {
        List<JSONObject> all = list(c);
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).optString("name").equals(tv.optString("name"))) {
                all.set(i, tv);
                found = true;
            }
        }
        if (!found) all.add(tv);
        save(c, all);
    }

    public static boolean paired(Context c) {
        for (JSONObject t : list(c)) if (!t.optString("token").isEmpty()) return true;
        return false;
    }

    /** The paired TVs' names, for Play on TV's choice. */
    public static List<String> names(Context c) {
        List<String> out = new ArrayList<>();
        for (JSONObject t : list(c)) if (!t.optString("token").isEmpty()) out.add(t.optString("name"));
        return out;
    }

    /** TMDB's key from a paired TV, or null until one has given it. */
    public static String tmdbKey(Context c) {
        String k = prefs(c).getString("tmdb", null);
        return k == null || k.isEmpty() ? null : k;
    }

    static void setTmdbKey(Context c, String key) {
        if (key == null || key.isEmpty() || key.equals(tmdbKey(c))) return;
        prefs(c).edit().putString("tmdb", key).apply();
        changed();
    }

    /** A sync with every paired TV now (Settings' Sync now, a pull to refresh); done on the main thread. */
    public static void syncNow(Context c, Runnable done) {
        Context a = c.getApplicationContext();
        App.io.execute(() -> {
            Syncer.syncAll(a, true);
            if (done != null) App.main.post(done);
        });
    }

    /** Opens the title on that TV in the licensed streaming app that carries it (POST /play). */
    public static void playOnTv(Context c, String tvName, int id, boolean isTv, String title, Result<Boolean> cb) {
        Context a = c.getApplicationContext();
        App.io.execute(() -> {
            JSONObject tv = find(a, tvName);
            String err = null;
            if (tv == null) err = "That TV isn't paired";
            else {
                try {
                    JSONObject body = new JSONObject().put("id", id).put("tv", isTv).put("title", title);
                    Syncer.call(a, tv, "/play", body, 6_000);
                } catch (IOException e) {
                    int s = Link.status(e);
                    err = s == 409 ? tvName + " is off" : s == 401 ? "Pair with " + tvName + " again in Settings"
                            : "Couldn't reach " + tvName;
                } catch (JSONException e) {
                    err = "Couldn't send it";
                }
            }
            String e = err;
            App.main.post(() -> cb.done(e == null, e));
        });
    }

    /**
     * A live channel put on a TV (its player, by the channel's names, the first it has): the phone's own stream can then
     * stop. 409 from the TV: it is off.
     */
    public static void tuneOnTv(Context c, String tvName, java.util.List<String> channels, Result<Boolean> cb) {
        Context a = c.getApplicationContext();
        App.io.execute(() -> {
            JSONObject tv = find(a, tvName);
            String err = null;
            if (tv == null) err = "That TV isn't paired";
            else {
                try {
                    org.json.JSONArray names = new org.json.JSONArray();
                    for (String n : channels) names.put(n);
                    Syncer.call(a, tv, "/tune", new JSONObject().put("channels", names), 8_000);
                } catch (IOException e) {
                    int s = Link.status(e);
                    err = s == 409 ? tvName + " is off" : s == 401 ? "Pair with " + tvName + " again in Settings"
                            : s == 404 ? tvName + " needs the TV Home update (3.91) for this" : "Couldn't reach " + tvName;
                } catch (JSONException e) {
                    err = "Couldn't send it";
                }
            }
            String e = err;
            App.main.post(() -> cb.done(e == null, e));
        });
    }

    /**
     * What is being watched on the TVs (their /sync/watching merged: "shows" and "resume", each item once, the TV's
     * name in "on"); the last answer when none can be reached now (away from home), or empty lists.
     */
    public static void watching(Context c, Result<JSONObject> cb) {
        Context a = c.getApplicationContext();
        App.io.execute(() -> {
            JSONObject merged = fetchWatching(a);
            App.main.post(() -> cb.done(merged, null));
        });
    }

    /** The last merged answer, straight away (Home's cards before the TVs answer). */
    public static JSONObject watchingCached(Context c) {
        try {
            return new JSONObject(prefs(c).getString("watching", "{\"shows\":[],\"resume\":[]}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static JSONObject fetchWatching(Context a) {
        Map<String, JSONObject> shows = new HashMap<>(), resume = new HashMap<>();
        boolean any = false;
        for (JSONObject tv : list(a)) {
            if (tv.optString("token").isEmpty()) continue;
            try {
                JSONObject r = Syncer.call(a, tv, "/sync/watching", new JSONObject(), 8_000);
                any = true;
                take(shows, r.optJSONArray("shows"), tv.optString("name"));
                take(resume, r.optJSONArray("resume"), tv.optString("name"));
            } catch (IOException | JSONException ignored) {
                // that TV not reachable now
            }
        }
        if (!any) return watchingCached(a);
        JSONObject out = new JSONObject();
        try {
            out.put("shows", new JSONArray(shows.values())).put("resume", new JSONArray(resume.values()))
                    .put("at", System.currentTimeMillis());
        } catch (JSONException ignored) {
            // plain values
        }
        prefs(a).edit().putString("watching", out.toString()).apply();
        return out;
    }

    /** Each title once: the one further on (a later season and episode, or more of it watched) wins. */
    private static void take(Map<String, JSONObject> into, JSONArray from, String tvName) throws JSONException {
        for (int i = 0; from != null && i < from.length(); i++) {
            JSONObject t = from.optJSONObject(i);
            if (t == null) continue;
            String k = (t.optBoolean("tv", t.has("season")) ? "tv:" : "mv:") + t.optInt("id") + ":" + t.optString("title");
            t.put("on", tvName);
            JSONObject had = into.get(k);
            if (had == null || further(t, had)) into.put(k, t);
        }
    }

    static boolean further(JSONObject a, JSONObject b) {
        int sa = a.optInt("season"), sb = b.optInt("season");
        if (sa != sb) return sa > sb;
        int ea = a.optInt("episode"), eb = b.optInt("episode");
        if (ea != eb) return ea > eb;
        return a.optDouble("progress", 0) > b.optDouble("progress", 0);
    }

    static JSONObject find(Context c, String name) {
        for (JSONObject t : list(c)) if (t.optString("name").equals(name) && !t.optString("token").isEmpty()) return t;
        return null;
    }

    /** Forget a TV: it stops syncing; the TV is told, when it can be reached. */
    public static void forget(Context c, String name) {
        Context a = c.getApplicationContext();
        JSONObject tv = find(a, name);
        List<JSONObject> all = list(a);
        all.removeIf(t -> t.optString("name").equals(name));
        save(a, all);
        if (tv != null) App.io.execute(() -> {
            try {
                Link.post(tv.optString("host"), tv.optInt("port", Link.PORT), "/unpair", new JSONObject(), tv.optString("token"), 4_000);
            } catch (IOException ignored) {
                // forgotten here all the same
            }
        });
    }

    /** Asks a TV for a code: it shows four digits on its screen. */
    public static void startPair(Context c, String host, int port, Result<Boolean> cb) {
        App.io.execute(() -> {
            String err = null;
            try {
                Link.post(host, port, "/pair", new JSONObject().put("phone", SyncStore.device()), null, 6_000);
            } catch (IOException | JSONException e) {
                err = "Couldn't reach the TV";
            }
            String e = err;
            App.main.post(() -> cb.done(e == null, e));
        });
    }

    /** The code typed in: the TV's token kept with its name and address, then a first full sync. */
    public static void confirmPair(Context c, String host, int port, String code, Result<String> cb) {
        Context a = c.getApplicationContext();
        App.io.execute(() -> {
            String err = null, name = null;
            try {
                JSONObject r = Link.post(host, port, "/pair/confirm", new JSONObject().put("code", code.trim()), null, 6_000);
                name = r.optString("tv", host);
                JSONObject tv = new JSONObject().put("name", name).put("host", host).put("port", port)
                        .put("token", r.optString("token")).put("since", 0).put("pushed", 0);
                update(a, tv);
                Syncer.syncAll(a, true);
            } catch (IOException e) {
                err = Link.status(e) == 403 ? "That code isn't right (or it timed out)" : "Couldn't reach the TV";
            } catch (JSONException e) {
                err = "The TV's answer wasn't understood";
            }
            String e = err, n = name;
            App.main.post(() -> cb.done(n, e));
        });
    }

    public static void addListener(Runnable r) { LISTENERS.add(r); }

    public static void removeListener(Runnable r) { LISTENERS.remove(r); }

    static void changed() {
        for (Runnable r : LISTENERS) App.main.post(r);
    }
}
