package com.gathin.homefieldtv.sync;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.RecentChannels;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Homefield TV's side of the family's sync (phone/tvhome-phone/SYNC.md), the same rules as the phone app's LiveSync: its
 * playlists (logins included), favourite and recent channels, where each film and episode is up to, and the settings it
 * shares with the phones, put into SyncItems before each sync (export) and written back after it (apply). A type goes
 * out only once the family's has been taken in, so this TV never overwrites the others with what it had before; a
 * removal is a tombstone; each kind has its own switch (Settings, Sync).
 */
public final class TvLiveSync {
    private TvLiveSync() {}

    public static final String PLAYLIST = "playlist", LIVEFAV = "livefav", LIVERECENT = "liverecent", RESUME = "resume",
            SETTING = "setting";
    /** The switches: the playlists, favourites and recents, Continue watching, settings. */
    public static final String PLAYLISTS = "playlists", PICKS = "picks", WATCHING = "resume", SETTINGS = "settings";
    private static final String[] TYPES = {PLAYLIST, LIVEFAV, LIVERECENT, RESUME, SETTING};
    /** Bounds that keep each item well inside what the launcher keeps of one (64 KB). */
    static final int FAVS_MAX = 500, RECENT_MAX = 12, LONGEST = 150;
    /** At most this many points from elsewhere looked for in this TV's films and series in one go. */
    private static final int ADOPT_MAX = 4;
    private static final Pattern SE = Pattern.compile("S(\\d{1,3})\\s*E(\\d{1,4})");

    /** After playlists came or went: the channels and the films read again (tests put in their own). */
    static Runnable refresh;

    // ---- the switches ---------------------------------------------------------------------------------------------

    private static SharedPreferences kinds(Context c) {
        return c.getApplicationContext().getSharedPreferences("sync_kinds", Context.MODE_PRIVATE);
    }

    private static SharedPreferences sent(Context c) {
        return c.getApplicationContext().getSharedPreferences("sync_live_sent", Context.MODE_PRIVATE);
    }

    private static SharedPreferences base(Context c) {
        return c.getApplicationContext().getSharedPreferences("sync_live_base", Context.MODE_PRIVATE);
    }

    public static boolean on(Context c, String kind) {
        return kinds(c).getBoolean(kind, true);
    }

    /** Switched on again, a kind starts afresh: the family's taken in first, this TV's own kept, then sent. */
    public static void setOn(Context c, String kind, boolean on) {
        kinds(c).edit().putBoolean(kind, on).apply();
        if (!on) return;
        SharedPreferences.Editor b = base(c).edit(), s = sent(c).edit();
        for (String type : TYPES) {
            if (!kindOf(type).equals(kind)) continue;
            b.remove(type);
            for (String id : sent(c).getAll().keySet()) if (id.startsWith(type + "|")) s.remove(id);
        }
        b.apply();
        s.apply();
    }

    /** Pure: the switch a type is under. */
    static String kindOf(String type) {
        switch (type) {
            case PLAYLIST: return PLAYLISTS;
            case RESUME: return WATCHING;
            case SETTING: return SETTINGS;
            default: return PICKS;
        }
    }

    // ---- this TV's things as items -------------------------------------------------------------------------------

    static Map<String, JSONObject> local(Context c, String type) {
        switch (type) {
            case PLAYLIST: {
                Map<String, JSONObject> out = new LinkedHashMap<>();
                for (Sources.Entry e : Sources.list(c)) if (shared(e.source)) out.put(keyOf(e.source), playlistData(e.name, e.source));
                return out;
            }
            case LIVEFAV: return listItem(c, LIVEFAV, Library.get(c).favourites(), FAVS_MAX);
            case LIVERECENT: return listItem(c, LIVERECENT, recent(c), RECENT_MAX);
            case RESUME: return resume(c);
            case SETTING: return TvSettingsList.read(c);
            default: return new LinkedHashMap<>();
        }
    }

    /** Pure: a playlist's item data, its login included (only ever sent to the launcher on this TV, with its token). */
    static JSONObject playlistData(String name, Source s) {
        JSONObject d = new JSONObject();
        try {
            d.put("name", name == null ? "" : name).put("kind", s.type).put("m3u", s.m3uUrl).put("guide", s.guideUrl)
                    .put("server", s.server).put("user", s.user).put("pass", s.pass);
        } catch (JSONException ignored) {
            // plain values
        }
        return d;
    }

    /** Pure: the playlist an item's data describes. */
    static Source sourceOf(JSONObject d) {
        return new Source(d.optString("kind"), d.optString("m3u"), d.optString("guide"), d.optString("server"), d.optString("user"),
                d.optString("pass"));
    }

    static String keyOf(Source s) {
        return SyncKeys.playlist(s.type, s.m3uUrl, s.server, s.user);
    }

    /** Pure: only IPTV playlists (an M3U address, an Xtream Codes login) are shared; any other source, TorBox's key above all, never. */
    static boolean shared(Source s) {
        return Source.M3U.equals(s.type) || Source.XTREAM.equals(s.type);
    }

    private static Map<String, String> playlistKeys(Context c) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Sources.Entry e : Sources.list(c)) out.put(e.id, keyOf(e.source));
        return out;
    }

    private static List<String> recent(Context c) {
        return RecentChannels.parse(c.getApplicationContext().getSharedPreferences("live_tv", Context.MODE_PRIVATE).getString("recent", ""));
    }

    /** Pure: a channel key as Live TV keeps it, split into its playlist's local id and its own key ("p1:x:5" → p1, x:5). */
    static String[] splitChannel(String key, Set<String> localIds) {
        for (String id : localIds) {
            if (!"p0".equals(id) && key.startsWith(id + ":")) return new String[]{id, key.substring(id.length() + 1)};
        }
        return localIds.contains("p0") ? new String[]{"p0", key} : null;
    }

    /** Pure: a channel's key as Live TV keeps it for a playlist's local id ("p0" has no prefix). */
    static String joinChannel(String localId, String channelKey) {
        return "p0".equals(localId) ? channelKey : localId + ":" + channelKey;
    }

    /**
     * Pure: this TV's channels put into the family's list: the channels of playlists this TV doesn't have keep their
     * places; this TV's own fill the places its playlists had, in its own order (any more after).
     */
    static JSONArray mergeList(JSONArray store, List<JSONObject> mine, Set<String> myPlaylists, int max) {
        JSONArray out = new JSONArray();
        int next = 0;
        for (int i = 0; store != null && i < store.length(); i++) {
            JSONObject o = store.optJSONObject(i);
            if (o == null) continue;
            if (!myPlaylists.contains(o.optString("pl"))) out.put(o);
            else if (next < mine.size()) out.put(mine.get(next++));
        }
        while (next < mine.size()) out.put(mine.get(next++));
        if (out.length() <= max) return out;
        JSONArray cut = new JSONArray();
        for (int i = 0; i < max; i++) cut.put(out.opt(i));
        return cut;
    }

    /** Pure: a list then what another has that it hasn't, in order, at most max. */
    static List<String> union(List<String> first, List<String> then, int max) {
        List<String> out = new ArrayList<>();
        for (String s : first) if (out.size() < max && !out.contains(s)) out.add(s);
        for (String s : then) if (out.size() < max && !out.contains(s)) out.add(s);
        return out;
    }

    private static Map<String, JSONObject> listItem(Context c, String type, List<String> keys, int max) {
        Map<String, String> pl = playlistKeys(c);
        List<JSONObject> mine = new ArrayList<>();
        for (String k : keys) {
            String[] split = splitChannel(k, pl.keySet());
            if (split == null || split[1].length() > LONGEST) continue;
            try {
                mine.add(new JSONObject().put("pl", pl.get(split[0])).put("ch", split[1]));
            } catch (JSONException ignored) {
                // left out
            }
        }
        JSONObject kept = SyncItems.get(c, type, "list");
        JSONArray merged = mergeList(kept == null ? null : kept.optJSONArray("channels"), mine, new LinkedHashSet<>(pl.values()), max);
        Map<String, JSONObject> out = new LinkedHashMap<>();
        try {
            out.put("list", new JSONObject().put("channels", merged));
        } catch (JSONException ignored) {
            // left out
        }
        return out;
    }

    /** Pure: a series' title key, as Catalog makes them ("p0|s|123"). */
    static boolean isSeries(String titleKey) {
        return titleKey != null && titleKey.contains("|s|");
    }

    /** Pure: the season and episode in a resume point's line ("S1 E3  ·  Pilot" → 1, 3; none → 0, 0). */
    static int[] seasonEpisode(String subtitle) {
        Matcher m = SE.matcher(subtitle == null ? "" : subtitle);
        if (!m.find()) return new int[]{0, 0};
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    }

    /** Pure: what a resume item says of a point on this TV (no address: each device finds its own copy). */
    static JSONObject resumeData(Resume.Point p) {
        int[] se = seasonEpisode(p.subtitle);
        JSONObject d = new JSONObject();
        try {
            d.put("tv", isSeries(p.title)).put("name", p.name).put("year", "").put("alt", "").put("tmdb", 0).put("s", se[0])
                    .put("e", se[1]).put("pos", p.position).put("dur", p.duration).put("at", p.at).put("poster", "").put("title", p.name);
        } catch (JSONException ignored) {
            // plain values
        }
        return d;
    }

    private static Map<String, JSONObject> resume(Context c) {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        for (Resume.Point p : Resume.all(c)) {
            if (p.name.isEmpty()) continue;
            String k = SyncKeys.resume(isSeries(p.title), p.name);
            if (k.length() <= 3 || out.containsKey(k)) continue;
            out.put(k, resumeData(p));
        }
        return out;
    }

    /** Pure: the part of an item's data that counts as a change (for a resume point, where it is up to). */
    static String print(String type, JSONObject d) {
        if (!RESUME.equals(type) || d == null) return SyncKeys.fingerprint(d);
        JSONObject core = new JSONObject();
        try {
            core.put("s", d.optInt("s")).put("e", d.optInt("e")).put("pos", d.optLong("pos")).put("dur", d.optLong("dur"))
                    .put("at", d.optLong("at"));
        } catch (JSONException ignored) {
            // plain values
        }
        return SyncKeys.fingerprint(core);
    }

    // ---- before a sync ---------------------------------------------------------------------------------------------

    /** What changed here since the last look goes into SyncItems (stamped now); what went is a tombstone. Off the main thread. */
    public static synchronized void export(Context c) {
        SharedPreferences sent = sent(c);
        SharedPreferences.Editor ed = sent.edit();
        Map<String, ?> before = sent.getAll();
        for (String type : TYPES) {
            if (!on(c, kindOf(type)) || !base(c).getBoolean(type, false)) continue;
            Map<String, JSONObject> mine;
            try {
                mine = local(c, type);
            } catch (RuntimeException e) {
                continue;
            }
            for (Map.Entry<String, JSONObject> e : mine.entrySet()) {
                String id = type + "|" + e.getKey(), print = print(type, e.getValue());
                if (print.equals(before.get(id))) continue;
                JSONObject kept = SyncItems.get(c, type, e.getKey());
                if (kept == null || !print.equals(print(type, kept))) SyncItems.put(c, type, e.getKey(), e.getValue());
                ed.putString(id, print);
            }
            for (String id : before.keySet()) {
                if (!id.startsWith(type + "|")) continue;
                String key = id.substring(type.length() + 1);
                if (mine.containsKey(key)) continue;
                if (SyncItems.get(c, type, key) != null) SyncItems.delete(c, type, key);
                ed.remove(id);
            }
        }
        ed.apply();
    }

    // ---- after a sync ------------------------------------------------------------------------------------------------

    /**
     * The family's things written back into this TV's own stores (each kind switched on). The first time for a type,
     * and only after the launcher answered (pulled), they are taken in with this TV's own kept too (nothing removed):
     * true when that happened, so this TV's own go out now. Off the main thread.
     */
    public static synchronized boolean apply(Context c, boolean pulled) {
        boolean first = false;
        for (String type : TYPES) {
            if (!on(c, kindOf(type))) continue;
            boolean fresh = !base(c).getBoolean(type, false);
            if (fresh && !pulled) continue;
            try {
                List<JSONObject> items = SyncItems.items(c, type);
                switch (type) {
                    case PLAYLIST: applyPlaylists(c, items, fresh); break;
                    case LIVEFAV: applyList(c, items, fresh, true); break;
                    case LIVERECENT: applyList(c, items, fresh, false); break;
                    case RESUME: applyResume(c, items); break;
                    case SETTING: TvSettingsList.write(c, items); break;
                    default: break;
                }
                settle(c, type);
                if (fresh) {
                    base(c).edit().putBoolean(type, true).apply();
                    first = true;
                }
            } catch (RuntimeException e) {
                // the next sync tries again
            }
        }
        return first;
    }

    /** What is here now and the same as the family's is noted as sent, so it isn't put in again as a change made here. */
    private static void settle(Context c, String type) {
        SharedPreferences.Editor ed = sent(c).edit();
        for (Map.Entry<String, JSONObject> e : local(c, type).entrySet()) {
            JSONObject kept = SyncItems.get(c, type, e.getKey());
            String print = print(type, e.getValue());
            if (kept != null && print.equals(print(type, kept))) ed.putString(type + "|" + e.getKey(), print);
        }
        ed.apply();
    }

    private static void applyPlaylists(Context c, List<JSONObject> items, boolean fresh) {
        Map<String, Sources.Entry> here = new HashMap<>();
        for (Sources.Entry e : Sources.list(c)) here.put(keyOf(e.source), e);
        boolean changed = false;
        for (JSONObject it : items) {
            String key = it.optString("key");
            Sources.Entry e = here.get(key);
            if (it.optBoolean("deleted")) {
                // The first time, one this TV still has stays (and goes out to the others).
                if (e != null && !fresh) {
                    Sources.remove(c, e.id);
                    changed = true;
                }
                continue;
            }
            JSONObject d = it.optJSONObject("data");
            if (d == null) continue;
            Source s = sourceOf(d);
            if (!shared(s) || !s.usable() || !key.equals(keyOf(s))) continue;
            if (e == null) {
                Sources.put(c, null, d.optString("name"), s);
                changed = true;
            } else if (!playlistData(e.name, e.source).toString().equals(playlistData(d.optString("name"), s).toString())) {
                Sources.put(c, e.id, d.optString("name"), s);
                changed = true;
            }
        }
        if (!changed) return;
        Runnable r = refresh;
        if (r != null) {
            r.run();
            return;
        }
        Library.get(c).refresh(null);
        Catalog.get(c).refresh(true);
    }

    /** The family's list as this TV's channel keys (those of playlists not on this TV left out); null for none. */
    private static List<String> mine(Context c, List<JSONObject> items) {
        Map<String, String> ids = new HashMap<>();
        for (Map.Entry<String, String> e : playlistKeys(c).entrySet()) ids.put(e.getValue(), e.getKey());
        for (JSONObject it : items) {
            if (it.optBoolean("deleted") || !"list".equals(it.optString("key"))) continue;
            JSONObject d = it.optJSONObject("data");
            JSONArray a = d == null ? null : d.optJSONArray("channels");
            List<String> out = new ArrayList<>();
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject ch = a.optJSONObject(i);
                String id = ch == null ? null : ids.get(ch.optString("pl"));
                String k = id == null || ch.optString("ch").isEmpty() ? null : joinChannel(id, ch.optString("ch"));
                if (k != null && !out.contains(k)) out.add(k);
            }
            return out;
        }
        return null;
    }

    private static void applyList(Context c, List<JSONObject> items, boolean fresh, boolean favourites) {
        List<String> theirs = mine(c, items);
        if (theirs == null) return;
        List<String> had = favourites ? Library.get(c).favourites() : recent(c);
        int max = favourites ? FAVS_MAX : RecentChannels.MAX + 1;
        List<String> now = fresh ? union(theirs, had, max) : union(theirs, new ArrayList<>(), max);
        if (now.equals(had)) return;
        if (favourites) Library.get(c).setPicks(now, null);
        else Library.get(c).setPicks(null, now);
    }

    /**
     * Where each film and episode is up to: a point this TV has moves on when the family's is later (to another episode
     * when this TV knows the series' episodes); one it hasn't is looked for among its films and series by name, a few at
     * a time and once each; a removal elsewhere goes here too, unless watched here since.
     */
    private static void applyResume(Context c, List<JSONObject> items) {
        Map<String, Resume.Point> here = new HashMap<>();
        for (Resume.Point p : Resume.all(c)) if (!p.name.isEmpty()) here.put(SyncKeys.resume(isSeries(p.title), p.name), p);
        Catalog cat = Catalog.get(c);
        Map<String, Catalog.Title> index = null;
        SharedPreferences tried = c.getApplicationContext().getSharedPreferences("sync_resume_tried", Context.MODE_PRIVATE);
        int adopted = 0;
        for (JSONObject it : items) {
            String key = it.optString("key");
            Resume.Point e = here.get(key);
            if (it.optBoolean("deleted")) {
                if (e != null && it.optLong("updated") > e.at) Resume.remove(c, e.title);
                continue;
            }
            JSONObject d = it.optJSONObject("data");
            if (d == null) continue;
            long at = d.optLong("at"), pos = d.optLong("pos"), dur = d.optLong("dur");
            int s = d.optInt("s"), ep = d.optInt("e");
            if (e != null) {
                if (at <= e.at) continue;
                int[] se = seasonEpisode(e.subtitle);
                if (!isSeries(e.title) || (se[0] == s && se[1] == ep)) {
                    if (e.position != pos) Resume.adopt(c, new Resume.Point(e.title, e.episode, e.name, e.subtitle, e.picture, pos, dur, at));
                } else {
                    Catalog.Title t = cat.title(e.title);
                    Catalog.Episode x = episode(t == null ? null : cat.cachedDetails(t), s, ep);
                    if (x != null) Resume.adopt(c, new Resume.Point(e.title, x.id, e.name, line(x), x.still.isEmpty() ? e.picture : x.still, pos, dur, at));
                }
                continue;
            }
            if (adopted >= ADOPT_MAX || !cat.ready() || System.currentTimeMillis() - at > 30L * 24 * 3600_000L) continue;
            if (tried.getLong(key, 0) == at) continue;
            adopted++;
            if (index == null) index = index(cat);
            boolean series = d.optBoolean("tv");
            Catalog.Title t = index.get((series ? "tv:" : "mv:") + SyncKeys.title(d.optString("name")));
            boolean found = false;
            if (t != null && !series) {
                Resume.adopt(c, new Resume.Point(t.key, "", t.name, "", t.backdrop, pos, dur, at));
                found = true;
            } else if (t != null) {
                Catalog.Episode x = episode(cat.cachedDetails(t), s, ep);
                if (x != null) {
                    Resume.adopt(c, new Resume.Point(t.key, x.id, t.name, line(x), x.still.isEmpty() ? t.backdrop : x.still, pos, dur, at));
                    found = true;
                }
            }
            if (!found) tried.edit().putLong(key, at).apply();
        }
    }

    /** This TV's films and series by the family's key for them. */
    private static Map<String, Catalog.Title> index(Catalog cat) {
        Map<String, Catalog.Title> out = new HashMap<>();
        for (Catalog.Title t : cat.films()) out.putIfAbsent("mv:" + SyncKeys.title(t.name), t);
        for (Catalog.Title t : cat.series()) out.putIfAbsent("tv:" + SyncKeys.title(t.name), t);
        return out;
    }

    private static Catalog.Episode episode(Catalog.Details d, int season, int number) {
        if (d == null) return null;
        for (Catalog.Episode x : d.episodes()) if (x.season == season && x.number == number) return x;
        return null;
    }

    /** An episode's line, as the player writes it. */
    private static String line(Catalog.Episode x) {
        return String.format(Locale.UK, "S%d E%d", x.season, x.number) + (x.title.isEmpty() ? "" : "  ·  " + x.title);
    }
}
