package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each film and episode was left on this TV (its resume point), newest first: the Continue watching row and the
 * title pages' Resume read it. Nothing here leaves the TV; no address is kept, only the title's key.
 */
public final class Resume {
    private Resume() {}

    static final int MAX = 40;
    /** Under this much in, it starts from the beginning again; past FINISHED of the way through, it was watched. */
    static final long MIN_MS = 60_000L;
    static final float FINISHED = 0.95f;

    public static final class Point {
        /** The title's key (Catalog.Title.key) and, for a series, the episode's id ("" for a film). */
        public final String title, episode;
        /** What the tile shows: the name, "S1 E3 · Pilot" for an episode, a picture. */
        public final String name, subtitle, picture;
        public final long position, duration, at;

        public Point(String title, String episode, String name, String subtitle, String picture, long position, long duration, long at) {
            this.title = title == null ? "" : title;
            this.episode = episode == null ? "" : episode;
            this.name = name == null ? "" : name;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.picture = picture == null ? "" : picture;
            this.position = Math.max(0, position);
            this.duration = Math.max(0, duration);
            this.at = at;
        }

        /** How far through, 0 to 1 (0 when the length isn't known). */
        public float progress() {
            return duration <= 0 ? 0f : Math.min(1f, position / (float) duration);
        }

        boolean finished() {
            return duration > 0 && (position >= duration * FINISHED || duration - position < 30_000L);
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("resume", Context.MODE_PRIVATE);
    }

    /** Every point, newest first. */
    public static synchronized List<Point> all(Context c) {
        return parse(prefs(c).getString("points", "[]"));
    }

    /** The point for a film (episode "") or one episode, or null. */
    public static Point get(Context c, String title, String episode) {
        String e = episode == null ? "" : episode;
        for (Point p : all(c)) if (p.title.equals(title) && p.episode.equals(e)) return p;
        return null;
    }

    /** A series' latest point (the episode watched last), or null. */
    public static Point latest(Context c, String title) {
        for (Point p : all(c)) if (p.title.equals(title)) return p;
        return null;
    }

    /** Where it was left: kept first; the start or the very end forgets it (a film watched to the end is done). */
    public static void save(Context c, Point p) {
        put(c, p, p.position >= MIN_MS && !p.finished());
    }

    /** A series' next episode, after one watched to the end: kept from its start for Continue watching. */
    public static void upNext(Context c, Point p) {
        put(c, p, true);
    }

    private static synchronized void put(Context c, Point p, boolean keep) {
        List<Point> list = all(c);
        List<Point> out = new ArrayList<>(list.size() + 1);
        if (keep) out.add(p);
        for (Point o : list) {
            // One point a series: the episode watched last.
            if (o.title.equals(p.title) && (p.episode.isEmpty() || !o.episode.isEmpty())) continue;
            if (out.size() < MAX) out.add(o);
        }
        prefs(c).edit().putString("points", json(out)).apply();
        com.gathin.homefieldtv.sync.TvSync.changed();
    }

    /**
     * Where a film or episode is up to on another of the family's devices (sync.TvLiveSync): in its place by when it was
     * watched, the title's other point gone, and not sent back as a change made here.
     */
    public static synchronized void adopt(Context c, Point p) {
        List<Point> out = new ArrayList<>();
        boolean placed = false;
        for (Point o : all(c)) {
            if (o.title.equals(p.title)) continue;
            if (!placed && p.at >= o.at) {
                out.add(p);
                placed = true;
            }
            out.add(o);
        }
        if (!placed) out.add(p);
        while (out.size() > MAX) out.remove(out.size() - 1);
        prefs(c).edit().putString("points", json(out)).apply();
    }

    public static synchronized void remove(Context c, String title) {
        List<Point> out = new ArrayList<>();
        for (Point o : all(c)) if (!o.title.equals(title)) out.add(o);
        prefs(c).edit().putString("points", json(out)).apply();
        com.gathin.homefieldtv.sync.TvSync.changed();
    }

    public static synchronized void clear(Context c) {
        prefs(c).edit().clear().apply();
    }

    static String json(List<Point> list) {
        JSONArray a = new JSONArray();
        try {
            for (Point p : list) {
                a.put(new JSONObject().put("t", p.title).put("e", p.episode).put("n", p.name).put("s", p.subtitle).put("p", p.picture)
                        .put("pos", p.position).put("dur", p.duration).put("at", p.at));
            }
        } catch (JSONException ignored) {
            // what was made so far
        }
        return a.toString();
    }

    static List<Point> parse(String json) {
        List<Point> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || o.optString("t").isEmpty()) continue;
                out.add(new Point(o.optString("t"), o.optString("e"), o.optString("n"), o.optString("s"), o.optString("p"),
                        o.optLong("pos"), o.optLong("dur"), o.optLong("at")));
            }
        } catch (JSONException ignored) {
            // none
        }
        return out;
    }
}
