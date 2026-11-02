package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * How long things play on this TV, for Your viewing: each day, the seconds in each app, in each hour, and for each
 * film or series (its TMDB id once WatchHistory has matched it, else its name) with the episodes seen. Fed by
 * WatchHistory's half-minute tick from Android's media sessions (any app, live TV in TiviMate or Kleva TV included;
 * music and the launcher left out). Kept in viewing.json in the app's own files, a year and a half of days, and
 * never sent anywhere.
 * A day: {"a": {package: seconds}, "h": [24 hours' seconds], "t": {key: {"s": seconds, "n": name, "id": TMDB id,
 * "tv": series, "e": [episodes]}}}.
 */
final class ViewingLog {
    private ViewingLog() { }

    static final String LIVE = "live";
    private static final int KEEP_DAYS = 550;
    private static JSONObject all;
    private static int unsaved;

    private static File file(Context c) {
        return new File(c.getFilesDir(), "viewing.json");
    }

    private static JSONObject all(Context c) {
        if (all != null) return all;
        try {
            all = new JSONObject(new String(Files.readAllBytes(file(c).toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            all = new JSONObject();
        }
        return all;
    }

    /** Live TV apps: their time counts as "Live TV & sport". */
    static boolean live(String pkg) {
        return pkg.equals("ar.tvplayer.tv") || pkg.contains("kleva");
    }

    /**
     * secs more of what is playing: its app, its key ("tv:123", "mv:456", or "x:" and its name), its name, and the
     * episode ("S4E2", or null). Saved every two minutes.
     */
    static synchronized void add(Context c, String pkg, String key, String name, int tmdb, boolean tv, String episode, int secs) {
        try {
            JSONObject days = all(c);
            String today = LocalDate.now().toString();
            JSONObject d = days.optJSONObject(today);
            if (d == null) {
                d = new JSONObject();
                days.put(today, d);
                prune(days);
            }
            JSONObject a = d.optJSONObject("a");
            if (a == null) d.put("a", a = new JSONObject());
            a.put(pkg, a.optInt(pkg) + secs);
            JSONArray h = d.optJSONArray("h");
            if (h == null) {
                h = new JSONArray();
                for (int i = 0; i < 24; i++) h.put(0);
                d.put("h", h);
            }
            int hour = LocalTime.now().getHour();
            h.put(hour, h.optInt(hour) + secs);
            JSONObject t = d.optJSONObject("t");
            if (t == null) d.put("t", t = new JSONObject());
            JSONObject one = t.optJSONObject(key);
            if (one == null) {
                if (t.length() >= 40) return;   // a day of clips: the first forty kept
                one = new JSONObject().put("n", name);
                if (tmdb > 0) one.put("id", tmdb).put("tv", tv);
                t.put(key, one);
            }
            one.put("s", one.optInt("s") + secs);
            if (episode != null) {
                JSONArray e = one.optJSONArray("e");
                if (e == null) one.put("e", e = new JSONArray());
                boolean has = false;
                for (int i = 0; i < e.length(); i++) if (episode.equals(e.optString(i))) { has = true; break; }
                if (!has) e.put(episode);
            }
            if (++unsaved >= 4) save(c);
        } catch (Exception ignored) {
            // a tick missed
        }
    }

    static synchronized void save(Context c) {
        if (all == null || unsaved == 0) return;
        try {
            File f = file(c), tmp = new File(f.getPath() + ".tmp");
            Files.write(tmp.toPath(), all.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(f)) Files.write(f.toPath(), all.toString().getBytes(StandardCharsets.UTF_8));
            unsaved = 0;
        } catch (Exception ignored) {
            // tried again at the next save
        }
    }

    private static void prune(JSONObject days) {
        String oldest = LocalDate.now().minusDays(KEEP_DAYS).toString();
        List<String> gone = new ArrayList<>();
        for (Iterator<String> it = days.keys(); it.hasNext(); ) {
            String k = it.next();
            if (k.compareTo(oldest) < 0) gone.add(k);
        }
        for (String k : gone) days.remove(k);
    }

    /** One day's record (a copy), or an empty one. */
    static synchronized JSONObject day(Context c, LocalDate date) {
        JSONObject d = all(c).optJSONObject(date.toString());
        try {
            return d == null ? new JSONObject() : new JSONObject(d.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** Clears it all (Your viewing's "Clear"). */
    static synchronized void clear(Context c) {
        all = new JSONObject();
        unsaved = 0;
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
    }

    /** The key, name and TMDB match for what is playing: from WatchHistory's entry for it when it has one. */
    static Object[] identify(Context c, String pkg, String title, String sub) {
        if (live(pkg)) return new Object[]{LIVE + ":" + title, title, 0, false, null};
        for (WatchHistory.Entry e : WatchHistory.load(c)) {
            if (!e.pkg.equals(pkg) || !e.title.equals(title) || !e.sub.equals(sub)) continue;
            String ep = null;
            if (e.tv) {
                int[] at = YourShows.position(e);
                ep = at == null ? sub : "S" + at[0] + "E" + at[1];
            }
            if (e.tmdbId > 0) return new Object[]{(e.tv ? "tv:" : "mv:") + e.tmdbId, e.show != null ? e.show : e.title, e.tmdbId, e.tv, ep};
            return new Object[]{"x:" + title, title, 0, e.tv, ep};
        }
        // Clips (YouTube and the like) and what WatchHistory leaves out: the app alone.
        return new Object[]{"app:" + pkg, null, 0, false, null};
    }
}
