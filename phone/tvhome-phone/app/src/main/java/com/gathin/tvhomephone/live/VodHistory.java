package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The films and episodes from the family's own playlists left part-way, for Watch's Continue watching row: what the
 * row shows (the name, the poster, the season and episode, how far in) and what plays it again (where it plays, and a
 * series' episodes for the next one after). A series is there once, at its latest episode; newest first, MAX at most;
 * one played nearly to its end drops off. Kept on this phone only (the addresses as the playlists' index keeps them).
 */
public final class VodHistory {
    private VodHistory() {}

    public static final int MAX = 15;
    /** Within this of its end, it counts as watched (as VodPlayerActivity's resume does). */
    static final long END_GAP = 120_000L;
    private static final String PREFS = "vod_history";

    /** One film or episode left part-way. */
    public static final class Entry {
        /** Its resume key (VodPlayerActivity's), the title shown in the player, the film's or series' name. */
        public String key = "", title = "", name = "", poster = "", episodeTitle = "";
        public int tmdb, season, episode;
        public boolean tv;
        public long position, duration, at;
        /** Where it plays: {address as the index has it, user agent, referrer}, its own first. */
        public final List<String[]> plays = new ArrayList<>();
        /** A series' episodes as the player was given them (Vod.episodesJson), "" for a film. */
        public String episodes = "";

        /** How far in, 0 to 1 (0 when its length isn't known). */
        public float progress() {
            return duration <= 0 ? 0 : Math.max(0, Math.min(1, position / (float) duration));
        }

        /** Minutes left, or -1 when its length isn't known. */
        public int minutesLeft() {
            return duration <= 0 ? -1 : (int) Math.max(1, (duration - position + 59_999) / 60_000);
        }

        JSONObject json() throws JSONException {
            JSONArray p = new JSONArray();
            for (String[] s : plays) p.put(new JSONArray().put(s[0]).put(s.length > 1 && s[1] != null ? s[1] : "").put(s.length > 2 && s[2] != null ? s[2] : "")
                    .put(s.length > 3 && s[3] != null ? s[3] : ""));
            return new JSONObject().put("key", key).put("title", title).put("name", name).put("poster", poster)
                    .put("ept", episodeTitle).put("tmdb", tmdb).put("s", season).put("e", episode).put("tv", tv)
                    .put("pos", position).put("dur", duration).put("at", at).put("plays", p).put("eps", episodes);
        }

        static Entry from(JSONObject o) {
            Entry e = new Entry();
            e.key = o.optString("key");
            e.title = o.optString("title");
            e.name = o.optString("name");
            e.poster = o.optString("poster");
            e.episodeTitle = o.optString("ept");
            e.tmdb = o.optInt("tmdb");
            e.season = o.optInt("s");
            e.episode = o.optInt("e");
            e.tv = o.optBoolean("tv");
            e.position = o.optLong("pos");
            e.duration = o.optLong("dur");
            e.at = o.optLong("at");
            e.episodes = o.optString("eps");
            JSONArray p = o.optJSONArray("plays");
            for (int i = 0; p != null && i < p.length(); i++) {
                JSONArray s = p.optJSONArray(i);
                if (s != null && !s.optString(0).isEmpty()) e.plays.add(new String[]{s.optString(0), s.optString(1), s.optString(2), s.optString(3)});
            }
            return e;
        }

        /** The same film, or the same series (at any episode). */
        boolean same(Entry o) {
            if (key.equals(o.key)) return true;
            if (!tv || !o.tv) return false;
            if (tmdb > 0 && tmdb == o.tmdb) return true;
            return !name.isEmpty() && Vod.key(name).equals(Vod.key(o.name));
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** What is left part-way, newest first. */
    public static synchronized List<Entry> list(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Entry e = Entry.from(o);
                if (!e.plays.isEmpty()) out.add(e);
            }
        } catch (JSONException ignored) {
            // none readable
        }
        return out;
    }

    /** Where a film or episode was left: kept (in place of its series' older episode), or dropped once nearly over. */
    static synchronized void record(Context c, Entry e) {
        save(c, finished(e.position, e.duration) ? without(list(c), e.key) : put(list(c), e, MAX));
    }

    /** One taken off the row (held on Watch, or played to its end). */
    public static synchronized void remove(Context c, String key) {
        save(c, without(list(c), key));
    }

    private static void save(Context c, List<Entry> list) {
        JSONArray a = new JSONArray();
        for (Entry e : list) {
            try {
                a.put(e.json());
            } catch (JSONException ignored) {
                // that one left out
            }
        }
        prefs(c).edit().putString("list", a.toString()).apply();
    }

    /** The list with e first, in place of any entry for the same film or series, at most max long. Pure. */
    static List<Entry> put(List<Entry> list, Entry e, int max) {
        List<Entry> out = new ArrayList<>();
        out.add(e);
        for (Entry o : list) {
            if (out.size() >= max) break;
            if (!o.same(e)) out.add(o);
        }
        return out;
    }

    /** The list without the entry of that key. Pure. */
    static List<Entry> without(List<Entry> list, String key) {
        List<Entry> out = new ArrayList<>();
        for (Entry o : list) if (!o.key.equals(key)) out.add(o);
        return out;
    }

    /** Whether it was played (nearly) to its end. Pure. */
    static boolean finished(long position, long duration) {
        return duration > 0 && duration - position <= END_GAP;
    }
}
