package com.gathin.tvhomephone.sync;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.tvhomephone.SyncStore;
import com.gathin.tvhomephone.live.LiveSyncHooks;
import com.gathin.tvhomephone.live.Source;
import com.gathin.tvhomephone.live.Sources;
import com.gathin.tvhomephone.live.VodHistory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything else the family's devices share through the TVs (SYNC.md): the IPTV playlists, the favourite and recent
 * channels, the categories and channels hidden, where each film or episode is up to, and the settings every device has.
 * Before each sync what changed here is put into SyncStore (export); after it, SyncStore's items are written back into
 * the app's own stores (apply). Each kind can be switched off (Settings, Sync). TorBox's key is never shared.
 */
public final class LiveSync {
    private LiveSync() {}

    public static final String PLAYLIST = "playlist", LIVEFAV = "livefav", LIVECAT = "livecat", LIVERECENT = "liverecent",
            RESUME = "resume", SETTING = "setting";
    /** The switches in Settings: the playlists, favourites and recents (with categories), Continue watching, settings. */
    public static final String PLAYLISTS = "playlists", PICKS = "picks", WATCHING = "resume", SETTINGS = "settings";
    private static final String[] TYPES = {PLAYLIST, LIVEFAV, LIVECAT, LIVERECENT, RESUME, SETTING};
    /** At most this many synced points looked for in this device's playlists in one go (they read the indexes). */
    private static final int ADOPT_MAX = 4;
    /**
     * Bounds that keep each item well inside what a TV keeps of one (64 KB): favourite channels, recent ones, a channel's
     * own key, and the hidden channels' list as JSON (any more stay on this device only).
     */
    static final int FAVS_MAX = 500, RECENT_MAX = 12, LONGEST = 150, ITEM_BYTES = 60_000;

    // ---- the switches -------------------------------------------------------------------------------------------------

    private static SharedPreferences kinds(Context c) {
        return c.getApplicationContext().getSharedPreferences("sync_kinds", Context.MODE_PRIVATE);
    }

    public static boolean on(Context c, String kind) {
        return kinds(c).getBoolean(kind, true);
    }

    /**
     * A kind switched on (again) starts afresh: at its next sync this device first takes in what the family has, keeping
     * its own as well, and only then sends its own, so nothing changed elsewhere meanwhile is overwritten.
     */
    public static synchronized void setOn(Context c, String kind, boolean on) {
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
        if (PLAYLISTS.equals(kind)) {
            // The family's playlists can come again; the films and episodes not found before are looked for again.
            kinds(c).edit().remove(RESET).apply();
            tried(c).edit().clear().apply();
        }
    }

    /** Set by Remove everything, which switched Playlists sync off on this device; gone when it is switched on again. */
    private static final String RESET = "off_by_remove_everything";

    /** Whether Playlists sync is off here because Remove everything switched it off (Settings, Sync says so). */
    public static boolean offByRemoveEverything(Context c) {
        return !on(c, PLAYLISTS) && kinds(c).getBoolean(RESET, false);
    }

    /**
     * Remove everything (Live TV's settings) for this device only. clear (what it removes: the playlists, their channels,
     * favourites, categories) runs with the sync held still, so none of it goes out as a removal: the family keep theirs.
     * Playlists sync is switched off so the family's playlists don't come straight back; switched on again, they are taken
     * in as on a new device. The live data starts afresh too: the family's is taken in at the next sync before anything of
     * this device's is sent. A single playlist removed on purpose still goes everywhere (export's tombstone).
     */
    public static synchronized void removeEverything(Context c, Runnable clear) {
        // apply(): what is read next, on any thread, sees it at once; written out before the app could stop.
        kinds(c).edit().putBoolean(PLAYLISTS, false).putBoolean(RESET, true).apply();
        SharedPreferences.Editor b = base(c).edit(), s = sent(c).edit();
        for (String type : TYPES) {
            if (SETTING.equals(type)) continue;
            b.remove(type);
            for (String id : sent(c).getAll().keySet()) if (id.startsWith(type + "|")) s.remove(id);
        }
        b.apply();
        s.apply();
        tried(c).edit().clear().apply();
        clear.run();
    }

    /** Resume points looked for here and not found, with when each was watched: not looked for again until it changes. */
    private static SharedPreferences tried(Context c) {
        return c.getApplicationContext().getSharedPreferences("sync_resume_tried", Context.MODE_PRIVATE);
    }

    /** The types whose family things this device has taken in at least once: until then it sends none of its own. */
    private static SharedPreferences base(Context c) {
        return c.getApplicationContext().getSharedPreferences("sync_live_base", Context.MODE_PRIVATE);
    }

    /** Pure: the switch an item's type is under. */
    static String kindOf(String type) {
        switch (type) {
            case PLAYLIST: return PLAYLISTS;
            case RESUME: return WATCHING;
            case SETTING: return SETTINGS;
            default: return PICKS;
        }
    }

    // ---- this device's things as items ---------------------------------------------------------------------------------

    /** The fingerprints of what was last put in (or written back), by "type|key": what changed since is put in again. */
    private static SharedPreferences sent(Context c) {
        return c.getApplicationContext().getSharedPreferences("sync_live_sent", Context.MODE_PRIVATE);
    }

    /** This device's things of one type as items' data, by key. */
    static Map<String, JSONObject> local(Context c, String type) {
        switch (type) {
            case PLAYLIST: return playlists(c);
            case LIVEFAV: return listItem(c, LIVEFAV, LiveSyncHooks.favourites(c), FAVS_MAX);
            case LIVECAT: return categories(c);
            case LIVERECENT: return listItem(c, LIVERECENT, LiveSyncHooks.recent(c), RECENT_MAX);
            case RESUME: return resume(c);
            case SETTING: return SyncSettingsList.read(c);
            default: return new LinkedHashMap<>();
        }
    }

    /** Pure: a playlist's item data (its login included: these only ever go over the paired links). */
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

    /** Pure: a playlist item described without its address or login (for anything shown or noted). */
    static String describe(JSONObject d) {
        boolean xtream = "xtream".equals(d.optString("kind"));
        return (xtream ? "Xtream Codes" : "M3U playlist") + " “" + d.optString("name") + "”";
    }

    static String keyOf(Source s) {
        return SyncKeys.playlist(s.type, s.m3uUrl, s.server, s.user);
    }

    /** Pure: only IPTV playlists (an M3U address, an Xtream Codes login) are shared; any other source, TorBox's key above all, never. */
    static boolean shared(Source s) {
        return Source.M3U.equals(s.type) || Source.XTREAM.equals(s.type);
    }

    private static Map<String, JSONObject> playlists(Context c) {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        for (Sources.Entry e : Sources.list(c)) if (shared(e.source)) out.put(keyOf(e.source), playlistData(e.name, e.source));
        return out;
    }

    /** This device's playlists: local id → its key. */
    private static Map<String, String> playlistKeys(Context c) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Sources.Entry e : Sources.list(c)) out.put(e.id, keyOf(e.source));
        return out;
    }

    /** This device's playlists: key → local id. */
    private static Map<String, String> localIds(Context c) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> e : playlistKeys(c).entrySet()) out.put(e.getValue(), e.getKey());
        return out;
    }

    /** Pure: a channel key as Live TV keeps it split into its playlist's local id and its own key ("p1:x:5" → p1, x:5). */
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

    /** A list of channels ({pl, ch}, in order) as items carry it, from Live TV's keys. */
    private static List<JSONObject> channels(Context c, List<String> keys) {
        Map<String, String> pl = playlistKeys(c);
        List<JSONObject> out = new ArrayList<>();
        for (String k : keys) {
            String[] split = splitChannel(k, pl.keySet());
            if (split == null || split[1].length() > LONGEST) continue;
            try {
                out.add(new JSONObject().put("pl", pl.get(split[0])).put("ch", split[1]));
            } catch (JSONException ignored) {
                // left out
            }
        }
        return out;
    }

    /**
     * Pure: this device's list of channels put into the family's (store): the channels of playlists this device doesn't
     * have keep their places; this device's own fill the places its playlists had, in its own order (any more after).
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

    /** The one "list" item of a kind: this device's channels put into the family's list as SyncStore has it. */
    private static Map<String, JSONObject> listItem(Context c, String type, List<String> keys, int max) {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        JSONObject kept = SyncStore.get(c, type, "list");
        JSONArray merged = mergeList(kept == null ? null : kept.optJSONArray("channels"), channels(c, keys),
                new LinkedHashSet<>(playlistKeys(c).values()), max);
        try {
            out.put("list", new JSONObject().put("channels", merged));
        } catch (JSONException ignored) {
            // left out
        }
        return out;
    }

    private static Map<String, JSONObject> categories(Context c) {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        try {
            List<String> hidden = new ArrayList<>(LiveSyncHooks.hiddenGroups(c));
            java.util.Collections.sort(hidden);
            out.put("groups", new JSONObject().put("hidden", new JSONArray(hidden))
                    .put("favs", new JSONArray(LiveSyncHooks.favouriteGroups(c))));
            out.put("channels", new JSONObject().put("hidden", new JSONObject(fitMap(LiveSyncHooks.hiddenChannels(c), ITEM_BYTES))));
        } catch (JSONException ignored) {
            // left out
        }
        return out;
    }

    /**
     * Pure: as much of a map as fits in maxBytes of JSON, by its keys in order (the same part on every run), so an item
     * stays within what a TV keeps; the rest stays on this device.
     */
    static Map<String, String> fitMap(Map<String, String> m, int maxBytes) {
        List<String> keys = new ArrayList<>(m.keySet());
        java.util.Collections.sort(keys);
        Map<String, String> out = new LinkedHashMap<>();
        int bytes = 2;
        for (String k : keys) {
            String v = m.get(k) == null ? "" : m.get(k);
            bytes += JSONObject.quote(k).length() + JSONObject.quote(v).length() + 2;
            if (bytes > maxBytes) break;
            out.put(k, v);
        }
        return out;
    }

    /** Pure: a list then what another has that it hasn't, in order, at most max (two devices' lists put together). */
    static List<String> union(List<String> first, List<String> then, int max) {
        List<String> out = new ArrayList<>();
        for (String s : first) if (out.size() < max && !out.contains(s)) out.add(s);
        for (String s : then) if (out.size() < max && !out.contains(s)) out.add(s);
        return out;
    }

    /** Pure: what a resume item says of one film or episode left part-way (no address: each device finds its own). */
    static JSONObject resumeData(VodHistory.Entry e) {
        JSONObject d = new JSONObject();
        try {
            d.put("tv", e.tv).put("name", e.name).put("year", e.year).put("alt", e.alt).put("tmdb", e.tmdb).put("s", e.season)
                    .put("e", e.episode).put("pos", e.position).put("dur", e.duration).put("at", e.at).put("poster", e.poster)
                    .put("title", e.title);
        } catch (JSONException ignored) {
            // plain values
        }
        return d;
    }

    private static Map<String, JSONObject> resume(Context c) {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        for (VodHistory.Entry e : LiveSyncHooks.resume(c)) {
            if (e.name == null || e.name.isEmpty()) continue;
            String k = SyncKeys.resume(e.tv, e.name);
            if (k.length() <= 3 || out.containsKey(k)) continue;
            out.put(k, resumeData(e));
        }
        return out;
    }

    /**
     * Pure: the part of an item's data that counts as a change: for a resume point where it is up to (each device keeps
     * its own titles and pictures), for the rest all of it.
     */
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

    // ---- before a sync: what changed here goes in ------------------------------------------------------------------------

    /**
     * What changed here since the last look is put into SyncStore (stamped now); what went is a tombstone. A type is put
     * in only once this device has taken in the family's (apply after a sync): a new device, or a kind just switched on,
     * never overwrites what the others have with what it had before. Off the main thread.
     */
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
                JSONObject kept = SyncStore.get(c, type, e.getKey());
                if (kept == null || !print.equals(print(type, kept))) SyncStore.put(c, type, e.getKey(), e.getValue());
                ed.putString(id, print);
            }
            for (String id : before.keySet()) {
                if (!id.startsWith(type + "|")) continue;
                String key = id.substring(type.length() + 1);
                if (mine.containsKey(key)) continue;
                if (SyncStore.get(c, type, key) != null) SyncStore.delete(c, type, key);
                ed.remove(id);
            }
        }
        ed.apply();
    }

    // ---- after a sync: the family's things written back here --------------------------------------------------------------

    /**
     * SyncStore's items written back into the app's own stores (each kind switched on). The first time for a type, and
     * only after a sync that reached a TV (pulled), the family's things are taken in with this device's own kept too
     * (nothing removed): true when that happened, so this device's own can go out now. Off the main thread.
     */
    public static synchronized boolean apply(Context c, boolean pulled) {
        boolean first = false;
        for (String type : TYPES) {
            if (!on(c, kindOf(type))) continue;
            boolean fresh = !base(c).getBoolean(type, false);
            if (fresh && !pulled) continue;
            try {
                List<JSONObject> items = SyncStore.items(c, type);
                switch (type) {
                    case PLAYLIST: applyPlaylists(c, items, fresh); break;
                    case LIVEFAV: applyFavourites(c, items, fresh); break;
                    case LIVECAT: applyCategories(c, items, fresh); break;
                    case LIVERECENT: applyRecent(c, items, fresh); break;
                    case RESUME: applyResume(c, items); break;
                    case SETTING: SyncSettingsList.write(c, items); break;
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

    /**
     * What is here now and the same as SyncStore's is noted as sent, so it isn't put in again as a change made here
     * (what differs, a change made here meanwhile, still goes at the next sync).
     */
    private static void settle(Context c, String type) {
        SharedPreferences.Editor ed = sent(c).edit();
        for (Map.Entry<String, JSONObject> e : local(c, type).entrySet()) {
            JSONObject kept = SyncStore.get(c, type, e.getKey());
            String print = print(type, e.getValue());
            if (kept != null && print.equals(print(type, kept))) ed.putString(type + "|" + e.getKey(), print);
        }
        ed.apply();
    }

    /** fresh: this device's first time, when a playlist the family removed but this device still has stays (and goes out). */
    private static void applyPlaylists(Context c, List<JSONObject> items, boolean fresh) {
        Map<String, Sources.Entry> here = new HashMap<>();
        for (Sources.Entry e : Sources.list(c)) here.put(keyOf(e.source), e);
        for (JSONObject it : items) {
            String key = it.optString("key");
            JSONObject d = it.optJSONObject("data");
            Sources.Entry e = here.get(key);
            if (it.optBoolean("deleted")) {
                if (e != null && !fresh) Sources.remove(c, e.id);
                continue;
            }
            if (d == null) continue;
            Source s = sourceOf(d);
            if (!shared(s) || !s.usable() || !key.equals(keyOf(s))) continue;
            if (e == null) Sources.put(c, null, d.optString("name"), s);
            else if (!playlistData(e.name, e.source).toString().equals(playlistData(d.optString("name"), s).toString())) {
                Sources.put(c, e.id, d.optString("name"), s);
            }
        }
    }

    /** The family's list as this device's channel keys (the channels of playlists not on this device left out); null for none. */
    private static List<String> mine(Context c, List<JSONObject> items) {
        Map<String, String> ids = localIds(c);
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

    /** The family's favourites here (the first time, with this device's own after them). */
    private static void applyFavourites(Context c, List<JSONObject> items, boolean fresh) {
        List<String> favs = mine(c, items);
        if (favs == null) return;
        List<String> had = LiveSyncHooks.favourites(c);
        if (fresh) favs = union(favs, had, FAVS_MAX);
        if (!favs.equals(had)) LiveSyncHooks.setPicks(c, favs, null);
    }

    private static void applyRecent(Context c, List<JSONObject> items, boolean fresh) {
        List<String> recent = mine(c, items);
        if (recent == null) return;
        List<String> had = LiveSyncHooks.recent(c);
        if (fresh) recent = union(recent, had, RECENT_MAX);
        if (!recent.equals(had)) LiveSyncHooks.setPicks(c, null, recent);
    }

    /**
     * The family's hidden and favourite categories and hidden channels here: the first time with this device's own as
     * well; after that as the family has them, with any of this device's hidden channels too many to send kept.
     */
    private static void applyCategories(Context c, List<JSONObject> items, boolean fresh) {
        for (JSONObject it : items) {
            if (it.optBoolean("deleted")) continue;
            JSONObject d = it.optJSONObject("data");
            if (d == null) continue;
            if ("groups".equals(it.optString("key"))) {
                Set<String> hidden = new LinkedHashSet<>();
                JSONArray h = d.optJSONArray("hidden");
                for (int i = 0; h != null && i < h.length(); i++) hidden.add(h.optString(i));
                List<String> favs = new ArrayList<>();
                JSONArray f = d.optJSONArray("favs");
                for (int i = 0; f != null && i < f.length(); i++) favs.add(f.optString(i));
                Set<String> hadHidden = LiveSyncHooks.hiddenGroups(c);
                List<String> hadFavs = LiveSyncHooks.favouriteGroups(c);
                if (fresh) {
                    hidden.addAll(hadHidden);
                    favs = union(favs, hadFavs, Integer.MAX_VALUE);
                }
                if (!hidden.equals(hadHidden) || !favs.equals(hadFavs)) LiveSyncHooks.setGroups(c, hidden, favs);
            } else if ("channels".equals(it.optString("key"))) {
                JSONObject h = d.optJSONObject("hidden");
                Map<String, String> m = new LinkedHashMap<>();
                for (java.util.Iterator<String> k = h == null ? null : h.keys(); k != null && k.hasNext(); ) {
                    String key = k.next();
                    m.put(key, h.optString(key));
                }
                Map<String, String> had = LiveSyncHooks.hiddenChannels(c);
                Map<String, String> went = fresh ? new HashMap<>() : fitMap(had, ITEM_BYTES);
                for (Map.Entry<String, String> e : had.entrySet()) {
                    if (!went.containsKey(e.getKey()) && !m.containsKey(e.getKey())) m.put(e.getKey(), e.getValue());
                }
                if (!m.equals(had)) LiveSyncHooks.setHiddenChannels(c, m);
            }
        }
    }

    private static void applyResume(Context c, List<JSONObject> items) {
        Map<String, VodHistory.Entry> here = new HashMap<>();
        for (VodHistory.Entry e : LiveSyncHooks.resume(c)) if (e.name != null && !e.name.isEmpty()) here.put(SyncKeys.resume(e.tv, e.name), e);
        int adopted = 0;
        // With no playlist here there is nothing to look in (not noted as tried: the playlists may come back).
        boolean any = !Sources.list(c).isEmpty();
        for (JSONObject it : items) {
            String key = it.optString("key");
            VodHistory.Entry e = here.get(key);
            if (it.optBoolean("deleted")) {
                // Gone elsewhere: gone here too, unless watched here since.
                if (e != null && it.optLong("updated") > e.at) LiveSyncHooks.forget(c, e.key);
                continue;
            }
            JSONObject d = it.optJSONObject("data");
            if (d == null) continue;
            long at = d.optLong("at");
            if (e != null) {
                if (at > e.at && (e.position != d.optLong("pos") || e.season != d.optInt("s") || e.episode != d.optInt("e"))) {
                    LiveSyncHooks.moveTo(c, e, d.optInt("s"), d.optInt("e"), d.optLong("pos"), d.optLong("dur"), at);
                }
            } else if (any && adopted < ADOPT_MAX && System.currentTimeMillis() - at < 30L * 24 * 3600_000L) {
                // Looked for once per point: one this device's playlists don't have isn't looked for at every sync.
                SharedPreferences tried = tried(c);
                if (tried.getLong(key, 0) == at) continue;
                adopted++;
                boolean found = LiveSyncHooks.adopt(c, d.optString("name"), d.optString("year"), d.optString("alt"), d.optInt("tmdb"),
                        d.optBoolean("tv"), d.optInt("s"), d.optInt("e"), d.optLong("pos"), d.optLong("dur"), at, d.optString("poster"),
                        d.optString("title"));
                if (!found) tried.edit().putLong(key, at).apply();
            }
        }
    }
}
