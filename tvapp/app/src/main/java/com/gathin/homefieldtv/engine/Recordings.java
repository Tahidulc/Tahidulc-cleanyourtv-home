package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Recordings, as Homefield TV can make them: the family's provider allows one stream per account, so nothing is
 * recorded by a second stream while another channel is watched, and nothing is written to the TV. A programme on a
 * channel whose provider keeps an archive (catch-up) is kept instead as a recording that plays from that archive: set
 * before it is shown (Scheduled), while it is on (Recording: what has been shown so far plays), and after it ends
 * (Ready) for as long as the archive keeps it (its channel's catch-up days from the programme's start), then Gone.
 *
 * <p>Kept on this TV in the app's own storage, as Reminders are: the channel's key, name and logo address, the
 * programme's title, its description and times, and when it was set. Nothing of it leaves the TV. Main thread.
 */
public final class Recordings {
    private Recordings() {}

    public static final int SCHEDULED = 0, RECORDING = 1, READY = 2, GONE = 3;
    /** The most kept; a recording gone from the archive this long is let go. */
    static final int MAX = 300;
    static final long DAY = 86_400_000L, KEEP_GONE_MS = 30 * DAY;
    private static final String PREFS = "recordings", KEY = "list";

    public static final class Recording {
        public final String key, channel, logo, title, about;
        public final long start, end, added;

        public Recording(String key, String channel, String logo, String title, String about, long start, long end, long added) {
            this.key = key == null ? "" : key;
            this.channel = channel == null ? "" : channel;
            this.logo = logo == null ? "" : logo;
            this.title = title == null ? "" : title;
            this.about = about == null ? "" : about;
            this.start = start;
            this.end = Math.max(start, end);
            this.added = added;
        }

        public String id() {
            return Recordings.id(key, start);
        }
    }

    private static List<Recording> cache;

    /** Pure: one recording's id: its channel and its start. */
    public static String id(String key, long start) {
        return key + "@" + start;
    }

    // ---- pure -----------------------------------------------------------------------------------------------------

    /**
     * Pure: where a recording is at now, its channel's archive keeping archiveDays (0: none now): to come, being
     * shown, ready to play from the archive, or gone from it.
     */
    public static int state(Recording r, int archiveDays, long now) {
        if (now < r.start) return SCHEDULED;
        if (now < r.end) return archiveDays > 0 ? RECORDING : GONE;
        return archiveDays > 0 && now < expires(r, archiveDays) ? READY : GONE;
    }

    /** Pure: until when the archive keeps it (its channel's catch-up days from its start). */
    public static long expires(Recording r, int archiveDays) {
        return r.start + Math.max(0, archiveDays) * DAY;
    }

    /** Pure: whether a programme can be recorded: its channel keeps an archive, and it hasn't gone from it already. */
    public static boolean recordable(int archiveDays, long start, long end, long now) {
        return archiveDays > 0 && end > start && start >= now - archiveDays * DAY;
    }

    /** Pure: the part of the archive a recording plays, before and after its programme by the margins (minutes). */
    public static long[] span(Recording r, int beforeMin, int afterMin) {
        return new long[]{r.start - beforeMin * 60_000L, r.end + afterMin * 60_000L};
    }

    /**
     * Pure: the ones still worth listing at now: all but those that ended longer ago than the longest archive believed
     * (Catchup's 31 days) and KEEP_GONE_MS more, so a gone one stays listed (as Gone) for a while before it goes.
     */
    static List<Recording> prune(List<Recording> in, long now) {
        List<Recording> out = new ArrayList<>();
        for (Recording r : in) if (now - r.end < KEEP_GONE_MS + 31 * DAY) out.add(r);
        return sort(out);
    }

    /** Pure: the newest programme first (as a recordings list reads: what was shown last, at the top). */
    static List<Recording> sort(List<Recording> in) {
        List<Recording> out = new ArrayList<>(in);
        Collections.sort(out, (a, b) -> a.start != b.start ? Long.compare(b.start, a.start) : a.channel.compareTo(b.channel));
        return out;
    }

    /** Pure: the list as it is kept. */
    public static String join(List<Recording> list) {
        JSONArray a = new JSONArray();
        try {
            for (Recording r : list) {
                a.put(new JSONObject().put("key", r.key).put("channel", r.channel).put("logo", r.logo).put("title", r.title)
                        .put("about", r.about).put("start", r.start).put("end", r.end).put("added", r.added));
            }
        } catch (JSONException ignored) {
            // the values are plain strings and numbers
        }
        return a.toString();
    }

    /** Pure: the list read back (whatever can't be read is left out). */
    public static List<Recording> parse(String text) {
        List<Recording> out = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return out;
        try {
            JSONArray a = new JSONArray(text);
            for (int i = 0; i < a.length() && out.size() < MAX; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String key = o.optString("key", "");
                long start = o.optLong("start", 0L);
                if (key.isEmpty() || start <= 0) continue;
                out.add(new Recording(key, o.optString("channel", ""), o.optString("logo", ""), o.optString("title", ""),
                        o.optString("about", ""), start, o.optLong("end", start), o.optLong("added", 0L)));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return sort(out);
    }

    // ---- kept on the TV -------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static synchronized List<Recording> all(Context c) {
        if (cache == null) cache = parse(prefs(c).getString(KEY, ""));
        return cache;
    }

    private static synchronized void save(Context c, List<Recording> list) {
        cache = sort(list);
        prefs(c).edit().putString(KEY, join(cache)).apply();
    }

    /** The recordings, the newest programme first (long-gone ones let go). */
    public static synchronized List<Recording> list(Context c, long now) {
        List<Recording> kept = prune(all(c), now);
        if (kept.size() != all(c).size()) save(c, kept);
        return new ArrayList<>(kept);
    }

    public static synchronized boolean has(Context c, String key, long start) {
        String id = id(key, start);
        for (Recording r : all(c)) if (r.id().equals(id)) return true;
        return false;
    }

    public static synchronized void add(Context c, Recording r) {
        List<Recording> list = new ArrayList<>(all(c));
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id().equals(r.id())) list.remove(i);
        list.add(r);
        List<Recording> sorted = sort(list);
        save(c, sorted.size() > MAX ? sorted.subList(0, MAX) : sorted);
    }

    public static synchronized void remove(Context c, String id) {
        List<Recording> list = new ArrayList<>(all(c));
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id().equals(id)) {
                list.remove(i);
                changed = true;
            }
        }
        if (changed) save(c, list);
    }

    /** Previews and tests: forget what was read (the next look reads it again). */
    public static synchronized void reset() {
        cache = null;
    }
}
