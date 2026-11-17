package com.gathin.homefieldtv.backups;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings → Backup's file: everything the family set on this TV, as one JSON document. The playlists (with their
 * logins) and the TorBox key, favourites, recent and last channels, reminders, My List and where films were left off, and every setting:
 * player, sound, stream problems, general, appearance, remote, guide, categories, parental control (the PIN as its
 * hash), live subtitles, the scoreboard, sports competitions and their timing. Not the channels, the guide or the
 * pictures: those are read again from the providers. Each settings file is kept whole, with each value's type, so a
 * restore puts back exactly what was there (a file the backup has, emptied first; one it doesn't mention, left alone).
 */
public final class Backup {
    private Backup() {}

    public static final String APP = "homefieldtv", KIND = "backup";
    public static final int VERSION = 1;

    /** The settings files a backup carries (SharedPreferences names), and nothing else. */
    static final List<String> FILES = Collections.unmodifiableList(Arrays.asList(
            "live_sources", "live_source", "torbox", "live_tv", "reminders", "reminders_ui", "my_list", "resume",
            "player", "look", "general", "remote", "guide", "live_groups", "parental",
            "live_subs", "scoreboard", "sports_competitions", "in_step"));

    /** A backup read back: when it was made, by which version, and its settings files. */
    public static final class Contents {
        public final long made;
        public final String appVersion, from;
        final JSONObject prefs;

        Contents(long made, String appVersion, String from, JSONObject prefs) {
            this.made = made;
            this.appVersion = appVersion;
            this.from = from;
            this.prefs = prefs;
        }
    }

    // ---- made --------------------------------------------------------------------------------------------------------

    /** Everything on this TV as a backup's JSON text. */
    public static String make(Context c, String appVersion, long now) {
        JSONObject prefs = new JSONObject();
        try {
            for (String name : FILES) {
                SharedPreferences p = c.getApplicationContext().getSharedPreferences(name, Context.MODE_PRIVATE);
                prefs.put(name, write(p.getAll()));
            }
            return wrap(prefs, appVersion, now).toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Pure: one settings file's values with their types: {"key": ["i", 5], "on": ["b", true], "set": ["S", […]]}. */
    static JSONObject write(Map<String, ?> all) throws JSONException {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            Object v = e.getValue();
            JSONArray t = new JSONArray();
            if (v instanceof String) t.put("s").put(v);
            else if (v instanceof Integer) t.put("i").put(v);
            else if (v instanceof Long) t.put("l").put(v);
            else if (v instanceof Float) t.put("f").put(((Float) v).doubleValue());
            else if (v instanceof Boolean) t.put("b").put(v);
            else if (v instanceof Set) {
                JSONArray s = new JSONArray();
                for (Object x : (Set<?>) v) s.put(String.valueOf(x));
                t.put("S").put(s);
            } else continue;
            o.put(e.getKey(), t);
        }
        return o;
    }

    static JSONObject wrap(JSONObject prefs, String appVersion, long now) throws JSONException {
        return new JSONObject().put("app", APP).put("kind", KIND).put("v", VERSION).put("made", now)
                .put("version", appVersion == null ? "" : appVersion).put("prefs", prefs);
    }

    // ---- read back ---------------------------------------------------------------------------------------------------

    /** A backup's text read back; null when it isn't one of Homefield TV's (or is from a newer one it can't read). */
    public static Contents read(String text) {
        if (text == null) return null;
        try {
            JSONObject o = new JSONObject(text);
            if (!APP.equals(o.optString("app")) || !KIND.equals(o.optString("kind"))) return null;
            int v = o.optInt("v", 0);
            if (v < 1 || v > VERSION) return null;
            JSONObject prefs = o.optJSONObject("prefs");
            if (prefs == null) return null;
            return new Contents(o.optLong("made", 0), o.optString("version"), o.optString("from"), prefs);
        } catch (JSONException e) {
            return null;
        }
    }

    /** A backup's text with a word of where it came from ("from": the phone that kept it), for Settings to say. */
    static String from(String text, String who) {
        try {
            return new JSONObject(text).put("from", who == null ? "" : who).toString();
        } catch (JSONException e) {
            return text;
        }
    }

    /**
     * A backup put back: each settings file it has, emptied and written as it was (at once, so nothing is lost if the
     * app is stopped straight after). What other parts keep in memory is forgotten by the caller (Restore).
     */
    public static int restore(Context c, Contents b) {
        int files = 0;
        for (Iterator<String> it = b.prefs.keys(); it.hasNext(); ) {
            String name = it.next();
            if (!FILES.contains(name)) continue;
            JSONObject values = b.prefs.optJSONObject(name);
            if (values == null) continue;
            SharedPreferences.Editor e = c.getApplicationContext().getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear();
            put(e, values);
            e.commit();
            files++;
        }
        return files;
    }

    /** Pure (over an Editor): one settings file's values put back with their types; ones that can't be read left out. */
    static void put(SharedPreferences.Editor e, JSONObject values) {
        for (Iterator<String> it = values.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONArray t = values.optJSONArray(k);
            if (t == null || t.length() < 2) continue;
            try {
                switch (t.getString(0)) {
                    case "s": e.putString(k, t.getString(1)); break;
                    case "i": e.putInt(k, t.getInt(1)); break;
                    case "l": e.putLong(k, t.getLong(1)); break;
                    case "f": e.putFloat(k, (float) t.getDouble(1)); break;
                    case "b": e.putBoolean(k, t.getBoolean(1)); break;
                    case "S": {
                        JSONArray a = t.getJSONArray(1);
                        Set<String> s = new HashSet<>();
                        for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                        e.putStringSet(k, s);
                        break;
                    }
                    default: break;
                }
            } catch (JSONException ignored) {
                // that one left out
            }
        }
    }

    /** How many favourites, playlists and reminders a backup has, for Settings to say ({playlists, favourites, reminders}). */
    public static int[] counts(Contents b) {
        int lists = 0, favs = 0, reminders = 0;
        try {
            JSONObject src = b.prefs.optJSONObject("live_sources");
            JSONArray t = src == null ? null : src.optJSONArray("list");
            if (t != null && t.length() > 1) lists = new JSONArray(t.getString(1)).length();
            JSONObject live = b.prefs.optJSONObject("live_tv");
            JSONArray f = live == null ? null : live.optJSONArray("favourites");
            if (f != null && f.length() > 1) favs = new JSONArray(f.getString(1)).length();
            JSONObject rem = b.prefs.optJSONObject("reminders");
            JSONArray r = rem == null ? null : rem.optJSONArray("list");
            if (r != null && r.length() > 1) reminders = com.gathin.homefieldtv.engine.Reminders.parse(r.getString(1)).size();
        } catch (JSONException ignored) {
            // what could be counted
        }
        return new int[]{lists, favs, reminders};
    }
}
