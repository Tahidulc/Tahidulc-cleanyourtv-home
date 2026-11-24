package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The family's reminders, set in the guide: a programme on a channel, its card coming a minute before it starts. Kept on
 * this TV in the app's own storage (the channel's key and name, its logo's address, the programme's title and times),
 * soonest first; one whose programme is over goes. Nothing of it leaves the TV. Main thread, and the reminder's alarm.
 */
public final class Reminders {
    private Reminders() {}

    /** The card comes this long before the start. */
    public static final long AHEAD_MS = 60_000L;
    /** A card is still shown this long after the start ("Starting now"), and not after (a TV that was asleep). */
    public static final long LATE_MS = 3 * 60_000L;
    private static final String PREFS = "reminders", KEY = "list";

    /** Remind again in 5 min (the reminder's panel). */
    public static final long AGAIN_MS = 5 * 60_000L;

    public static final class Reminder {
        public final String key, channel, logo, title;
        public final long start, end;
        /** Remind again: when its card comes again (0: a minute before the start, as set). */
        public final long again;

        public Reminder(String key, String channel, String logo, String title, long start, long end) {
            this(key, channel, logo, title, start, end, 0L);
        }

        public Reminder(String key, String channel, String logo, String title, long start, long end, long again) {
            this.key = key == null ? "" : key;
            this.channel = channel == null ? "" : channel;
            this.logo = logo == null ? "" : logo;
            this.title = title == null ? "" : title;
            this.start = start;
            this.end = end;
            this.again = Math.max(0L, again);
        }

        /** The same reminder, its card coming again at at. */
        public Reminder againAt(long at) {
            return new Reminder(key, channel, logo, title, start, end, at);
        }

        /** The last moment its card is still shown. */
        long lastMoment() {
            return Math.max(start + LATE_MS, again + LATE_MS);
        }

        public String id() {
            return Reminders.id(key, start);
        }

        /** When its card is due. */
        public long cardAt() {
            return again > 0 ? again : start - AHEAD_MS;
        }
    }

    private static List<Reminder> cache;
    private static final Set<String> ids = new HashSet<>();
    /** Each channel's reminders' starts (the guide asks for every cell it draws: found without making anything). */
    private static final java.util.Map<String, long[]> starts = new java.util.HashMap<>();

    private static void index(List<Reminder> list) {
        ids.clear();
        starts.clear();
        for (Reminder r : list) {
            ids.add(r.id());
            long[] had = starts.get(r.key);
            long[] now = had == null ? new long[1] : java.util.Arrays.copyOf(had, had.length + 1);
            now[now.length - 1] = r.start;
            starts.put(r.key, now);
        }
    }

    /** Pure: one reminder's id, its channel and its start. */
    public static String id(String key, long start) {
        return key + "@" + start;
    }

    // ---- kept on the TV ---------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Read again on next use (Settings → Backup has just put back another list). */
    public static synchronized void forget() {
        cache = null;
    }

    private static synchronized List<Reminder> all(Context c) {
        if (cache == null) {
            cache = parse(prefs(c).getString(KEY, ""));
            index(cache);
        }
        return cache;
    }

    private static synchronized void save(Context c, List<Reminder> list) {
        cache = list;
        index(list);
        prefs(c).edit().putString(KEY, join(list)).apply();
    }

    /** The reminders still to come (or on now and due), soonest first; ones over are let go. */
    public static synchronized List<Reminder> list(Context c, long now) {
        List<Reminder> kept = prune(all(c), now);
        if (kept.size() != all(c).size()) save(c, kept);
        return new ArrayList<>(kept);
    }

    /** Whether a reminder is set for a programme on a channel; one row per channel, on any of its quality versions. */
    public static synchronized boolean has(Context c, String key, long start) {
        all(c);
        if (at(key, start)) return true;
        if (starts.isEmpty()) return false;
        Versions v = versionsOf(c, key);
        if (v == null) return false;
        for (Channel o : v.all) if (!o.key.equals(key) && at(o.key, start)) return true;
        return false;
    }

    private static boolean at(String key, long start) {
        long[] at = starts.get(key);
        if (at == null) return false;
        for (long t : at) if (t == start) return true;
        return false;
    }

    /** A channel's quality versions by its key (one row per channel), or null. */
    private static Versions versionsOf(Context c, String key) {
        if (key == null || !com.gathin.homefieldtv.prefs.ChannelEdits.merging()) return null;
        return Versions.of(Library.get(c).exact(key));
    }

    /** Whether any is set (the guide looks for bells only then). */
    public static synchronized boolean any(Context c) {
        return !all(c).isEmpty();
    }

    /** The ids set, for the guide's bells (a copy). */
    public static synchronized Set<String> ids(Context c) {
        all(c);
        return new HashSet<>(ids);
    }

    public static synchronized void add(Context c, Reminder r) {
        List<Reminder> list = new ArrayList<>(all(c));
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id().equals(r.id())) list.remove(i);
        list.add(r);
        save(c, sort(list));
    }

    /** A reminder cancelled; one row per channel, set on any of the channel's quality versions. */
    public static synchronized void remove(Context c, String key, long start) {
        Set<String> gone = new HashSet<>();
        gone.add(id(key, start));
        Versions v = versionsOf(c, key);
        if (v != null) for (Channel o : v.all) gone.add(id(o.key, start));
        List<Reminder> list = new ArrayList<>(all(c));
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (gone.contains(list.get(i).id())) {
                list.remove(i);
                changed = true;
            }
        }
        if (changed) save(c, list);
    }

    /** The reminders whose card is due at now (a minute before the start, up to a few minutes after), soonest first. */
    public static synchronized List<Reminder> due(Context c, long now) {
        return due(all(c), now);
    }

    /** When the next card is due after now (Long.MAX_VALUE: none). */
    public static synchronized long nextCard(Context c, long now) {
        return nextCard(all(c), now);
    }

    // ---- pure -------------------------------------------------------------------------------------------------

    /** Pure: the ones still worth keeping at now (a card not yet past its last moment), soonest first. */
    public static List<Reminder> prune(List<Reminder> in, long now) {
        List<Reminder> out = new ArrayList<>();
        for (Reminder r : in) if (now < r.lastMoment() && now < Math.max(r.end, r.lastMoment())) out.add(r);
        return sort(out);
    }

    /** Pure: those whose card is due at now. */
    public static List<Reminder> due(List<Reminder> in, long now) {
        List<Reminder> out = new ArrayList<>();
        for (Reminder r : in) if (r.cardAt() <= now && now < r.lastMoment()) out.add(r);
        return sort(out);
    }

    /** Pure: the first moment after now a card is due (Long.MAX_VALUE: none). */
    public static long nextCard(List<Reminder> in, long now) {
        long next = Long.MAX_VALUE;
        for (Reminder r : in) if (r.cardAt() > now) next = Math.min(next, r.cardAt());
        return next;
    }

    private static List<Reminder> sort(List<Reminder> in) {
        List<Reminder> out = new ArrayList<>(in);
        Collections.sort(out, (a, b) -> a.start != b.start ? Long.compare(a.start, b.start) : a.channel.compareTo(b.channel));
        return out;
    }

    /** Pure: the list as it is kept. */
    public static String join(List<Reminder> list) {
        JSONArray a = new JSONArray();
        try {
            for (Reminder r : list) {
                JSONObject o = new JSONObject().put("key", r.key).put("channel", r.channel).put("logo", r.logo).put("title", r.title)
                        .put("start", r.start).put("end", r.end);
                if (r.again > 0) o.put("again", r.again);
                a.put(o);
            }
        } catch (JSONException ignored) {
            // the values are plain strings and numbers
        }
        return a.toString();
    }

    /** Pure: the list read back (whatever can't be read is left out). */
    public static List<Reminder> parse(String text) {
        List<Reminder> out = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return out;
        try {
            JSONArray a = new JSONArray(text);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String key = o.optString("key", "");
                long start = o.optLong("start", 0L);
                if (key.isEmpty() || start <= 0) continue;
                out.add(new Reminder(key, o.optString("channel", ""), o.optString("logo", ""), o.optString("title", ""), start,
                        o.optLong("end", start), o.optLong("again", 0L)));
            }
        } catch (JSONException e) {
            return new ArrayList<>();
        }
        return sort(out);
    }

    /** Previews and tests: forget what was read (the next look reads it again). */
    public static synchronized void reset() {
        cache = null;
        ids.clear();
        starts.clear();
    }
}
