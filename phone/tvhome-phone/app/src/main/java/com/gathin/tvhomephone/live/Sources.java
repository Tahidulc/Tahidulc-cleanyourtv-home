package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The family's playlists, one or several: each an M3U playlist or an Xtream Codes login (Source) with a name, in the
 * order they were added. Live TV and Watch use all of them together, a channel or film in more than one of them shown
 * once with the others as its backups, or just the one chosen (in Live TV's playlist chip or Settings). What each
 * provider says of its account is kept here too: when it runs out, its status, its connections. The first playlist
 * (the one set up before there could be several) keeps its channels' keys as they were, so its favourites and hidden
 * channels stay; the others' keys start with their id. Kept on this phone only, never synced.
 */
public final class Sources {
    private Sources() {}

    /** All the playlists together (the active choice). */
    public static final String ALL = "";
    private static final String PREFS = "live_sources";

    /** One playlist: its id ("p0", "p1"…), its name, and the source. */
    public static final class Entry {
        public final String id, name;
        public final Source source;

        public Entry(String id, String name, Source source) {
            this.id = id;
            this.name = name == null || name.trim().isEmpty() ? Source.host(source.type.equals(Source.M3U) ? source.m3uUrl : source.server) : name.trim();
            this.source = source;
        }

        /** What its channels' keys start with: nothing for the first playlist, "p1:" and so on for the rest. */
        public String prefix() {
            return "p0".equals(id) ? "" : id + ":";
        }
    }

    /** What a provider says of an account: when it runs out (0 unknown, -1 never), its status, its connections. */
    public static final class Info {
        public final long expires, checkedAt;
        public final String status;
        public final int maxConnections, activeConnections;

        Info(long expires, String status, int maxConnections, int activeConnections, long checkedAt) {
            this.expires = expires;
            this.status = status == null ? "" : status;
            this.maxConnections = maxConnections;
            this.activeConnections = activeConnections;
            this.checkedAt = checkedAt;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Every playlist, in the order added; the one set up before (Source's own) moved in as the first. */
    public static synchronized List<Entry> list(Context c) {
        SharedPreferences p = prefs(c);
        String raw = p.getString("list", null);
        if (raw == null) {
            List<Entry> out = new ArrayList<>();
            Source old = Source.loadOld(c);
            if (old != null) out.add(new Entry("p0", "", old));
            save(c, out);
            return out;
        }
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Source s = new Source(o.optString("type"), o.optString("m3u"), o.optString("guide"), o.optString("server"),
                        o.optString("user"), o.optString("pass"));
                if (s.usable()) out.add(new Entry(o.optString("id"), o.optString("name"), s));
            }
        } catch (JSONException ignored) {
            // none readable
        }
        return out;
    }

    private static void save(Context c, List<Entry> list) {
        JSONArray a = new JSONArray();
        try {
            for (Entry e : list) {
                a.put(new JSONObject().put("id", e.id).put("name", e.name).put("type", e.source.type).put("m3u", e.source.m3uUrl)
                        .put("guide", e.source.guideUrl).put("server", e.source.server).put("user", e.source.user)
                        .put("pass", e.source.pass));
            }
        } catch (JSONException ignored) {
            // not saved
        }
        prefs(c).edit().putString("list", a.toString()).apply();
    }

    public static Entry get(Context c, String id) {
        for (Entry e : list(c)) if (e.id.equals(id)) return e;
        return null;
    }

    /** A playlist added (a new id) or changed (the same id). */
    public static synchronized Entry put(Context c, String id, String name, Source s) {
        List<Entry> all = list(c);
        if (id == null || id.isEmpty()) id = nextId(all);
        Entry e = new Entry(id, name, s);
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(id)) {
                all.set(i, e);
                replaced = true;
            }
        }
        if (!replaced) all.add(e);
        save(c, all);
        return e;
    }

    private static String nextId(List<Entry> all) {
        int n = 0;
        for (Entry e : all) {
            try {
                n = Math.max(n, Integer.parseInt(e.id.substring(1)) + 1);
            } catch (RuntimeException ignored) {
                // another kind of id
            }
        }
        return "p" + n;
    }

    public static synchronized void remove(Context c, String id) {
        List<Entry> all = list(c);
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).id.equals(id)) all.remove(i);
        save(c, all);
        prefs(c).edit().remove("info." + id).apply();
        Vod.forget(c, id);
        PlaylistHealth.forget(c, id);
        if (id.equals(active(c))) setActive(c, ALL);
    }

    public static synchronized void clear(Context c) {
        prefs(c).edit().clear().putString("list", "[]").apply();
    }

    /** The choice: ALL (every playlist together) or one playlist's id. */
    public static String active(Context c) {
        return prefs(c).getString("active", ALL);
    }

    public static void setActive(Context c, String id) {
        prefs(c).edit().putString("active", id == null ? ALL : id).apply();
    }

    /** The playlists in use now: the one chosen, or all of them (all when the chosen one has gone). */
    public static List<Entry> inUse(Context c) {
        List<Entry> all = list(c);
        String a = active(c);
        if (!a.isEmpty()) for (Entry e : all) if (e.id.equals(a)) return Collections.singletonList(e);
        return all;
    }

    /** A fingerprint of the playlists in use, for the cached channels to know they belong to them. */
    static String fingerprint(Context c) {
        StringBuilder b = new StringBuilder();
        for (Entry e : inUse(c)) b.append(e.id).append('=').append(e.source.fingerprint()).append(';');
        return b.length() == 0 ? "" : Integer.toHexString(b.toString().hashCode()) + b.length();
    }

    // ---- what the provider says of the account -------------------------------------------------------------------

    /** Kept from an Xtream account's user_info (exp_date in seconds, or none for an account that never runs out). */
    static void note(Context c, String id, JSONObject userInfo) {
        if (userInfo == null) return;
        String exp = userInfo.optString("exp_date", "");
        long expires = exp.isEmpty() || "null".equals(exp) ? -1 : parse(exp) * 1000L;
        try {
            JSONObject o = new JSONObject().put("expires", expires).put("status", userInfo.optString("status", ""))
                    .put("max", (int) parse(userInfo.optString("max_connections", "0")))
                    .put("active", (int) parse(userInfo.optString("active_cons", "0")))
                    .put("at", System.currentTimeMillis());
            prefs(c).edit().putString("info." + id, o.toString()).apply();
        } catch (JSONException ignored) {
            // not kept
        }
    }

    /** A playlist checked before it was added ("new"): what its provider said kept under the id it was given. */
    static void moved(Context c, String from, String to) {
        SharedPreferences p = prefs(c);
        String raw = p.getString("info." + from, null);
        SharedPreferences.Editor e = p.edit().remove("info." + from);
        if (raw != null) e.putString("info." + to, raw);
        e.apply();
    }

    /** What is known of a playlist's account, or null when its provider has said nothing (an M3U that isn't Xtream's). */
    public static Info info(Context c, String id) {
        String raw = prefs(c).getString("info." + id, null);
        if (raw == null) return null;
        try {
            JSONObject o = new JSONObject(raw);
            return new Info(o.optLong("expires"), o.optString("status"), o.optInt("max"), o.optInt("active"), o.optLong("at"));
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * An M3U address from an Xtream server (get.php?username=…&password=…): the account's API for its expiry date, as
     * {base, user, pass}; null for any other playlist. Pure.
     */
    static String[] xtreamOf(String m3uUrl) {
        if (m3uUrl == null || !m3uUrl.contains("username=") || !m3uUrl.contains("password=")) return null;
        try {
            Uri u = Uri.parse(m3uUrl);
            String user = u.getQueryParameter("username"), pass = u.getQueryParameter("password");
            if (user == null || pass == null || user.isEmpty()) return null;
            return new String[]{Xtream.base(m3uUrl), user, pass};
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "Runs out 12 Mar 2027 · 128 days left", "Doesn't run out", "Ran out on 2 Nov"; "" when not known. */
    public static String expiry(Info i, long now) {
        if (i == null || i.expires == 0) return "";
        if (i.expires < 0) return "Doesn't run out";
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.UK);
        String day = java.time.Instant.ofEpochMilli(i.expires).atZone(java.time.ZoneId.systemDefault()).format(f);
        long days = (long) Math.ceil((i.expires - now) / 86_400_000.0);
        if (days < 0) return "Ran out on " + day;
        if (days == 0) return "Runs out today";
        return "Runs out " + day + " · " + days + (days == 1 ? " day left" : " days left");
    }

    private static long parse(String v) {
        try {
            return Long.parseLong(v.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
