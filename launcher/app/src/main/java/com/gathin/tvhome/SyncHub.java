package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The TV's side of the sync with the phone app and the house's other TVs (phone/tvhome-phone/SYNC.md): My list,
 * ratings, the Film night plan, Coming up's release mutes, reminders and starred clubs, as items merged last writer
 * wins. The stores keep their own records and code paths; this keeps a small note per item (its data's fingerprint,
 * when it last changed and where, when it got here) and, looking at the stores again, stamps whatever changed here.
 *
 * <p>DeliveryLink answers /sync, /sync/watching, /play and /tmdb with it. The TVs sync with each other ten seconds
 * after a change here and every ten minutes (from the alerts service); a phone syncs when it opens and every 15
 * minutes. Nothing goes beyond the home network or the tailnet.
 */
final class SyncHub {
    private SyncHub() { }

    static final String MYLIST = "mylist", RATING = "rating", FILMNIGHT = "filmnight", MUTE = "mute", REMINDER = "reminder",
            CLUBS = "clubs", MYBOARD = com.gathin.broadcast.MyBoards.TYPE;
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(MYLIST, RATING, FILMNIGHT, MUTE, REMINDER, CLUBS, MYBOARD));
    static {
        TYPES.addAll(SyncRules.RELAYED);
    }
    /** A relayed item's data in a snapshot: its JSON as it came, under a name no store's data has. */
    private static final String RAW = "\u0000raw";
    private static final String CLUBS_ID = SyncRules.id(CLUBS, "starred");
    private static final String PREFS = "sync_hub";
    private static final long PEER_DELAY_MS = 10_000L, PEER_EVERY_MS = 10 * 60_000L, FIND_EVERY_MS = 3600_000L;
    private static final int READ_CAP = 2 * 1024 * 1024;

    private static Map<String, SyncRules.Meta> metas;
    private static volatile Handler worker;
    private static volatile Context app;
    private static long lastFind;
    private static final Runnable PUSH = SyncHub::syncPeers;
    private static final Runnable EVERY = new Runnable() {
        @Override public void run() {
            syncPeers();
            if (worker != null) worker.postDelayed(this, PEER_EVERY_MS);
        }
    };

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The relayed kinds (SyncRules.RELAYED) as they came, by item id: in the app's own storage, never on /sdcard. */
    private static SharedPreferences relay(Context c) {
        return c.getApplicationContext().getSharedPreferences("sync_relay", Context.MODE_PRIVATE);
    }

    /** Homefield TV's token on this TV (SyncTokenProvider hands it over): made once, then kept. */
    static synchronized String appToken(Context c) {
        String t = prefs(c).getString("app_token", null);
        if (t == null || t.length() < 32) {
            byte[] b = new byte[16];
            new java.security.SecureRandom().nextBytes(b);
            StringBuilder hex = new StringBuilder();
            for (byte x : b) hex.append(String.format("%02x", x));
            t = hex.toString();
            prefs(c).edit().putString("app_token", t).apply();
        }
        return t;
    }

    /** Whether a call carries Homefield TV's token (only ever taken from the TV itself: DeliveryLink checks loopback). */
    static boolean isAppToken(Context c, String given) {
        return SyncRules.sameToken(prefs(c).getString("app_token", null), given);
    }

    /**
     * The worker thread, made once. Its own lock, not the class's: the stores call changed() while holding theirs, and
     * a merge holds the class's while it writes to them.
     */
    private static final Object WORKER_LOCK = new Object();

    private static void ensure(Context c) {
        synchronized (WORKER_LOCK) {
            if (app == null) app = c.getApplicationContext();
            if (worker != null) return;
            HandlerThread t = new HandlerThread("sync-hub", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            t.start();
            worker = new Handler(t.getLooper());
        }
    }

    /** A store changed here: the other TVs hear of it in ten seconds (one sync for a burst of changes). */
    static void changed(Context c) {
        ensure(c);
        worker.removeCallbacks(PUSH);
        worker.postDelayed(PUSH, PEER_DELAY_MS);
    }

    /** The alerts service started: a sync with the other TVs now and every ten minutes. */
    static void startPeers(Context c) {
        ensure(c);
        worker.removeCallbacks(EVERY);
        worker.postDelayed(EVERY, 20_000L);
    }

    // ---- the starred clubs -------------------------------------------------------------------------------------------

    /** The starred clubs (ESPN team ids): the ones chosen on a phone or another TV, else this TV's own goal_alert_teams. */
    static String[] starredClubs(Context c) {
        String s = prefs(c).getString("clubs", null);
        if (s != null) {
            try {
                JSONArray a = new JSONArray(s);
                String[] out = new String[a.length()];
                for (int i = 0; i < a.length(); i++) out[i] = a.optString(i);
                return out;
            } catch (Exception ignored) {
                // the TV's own
            }
        }
        return c.getResources().getStringArray(R.array.goal_alert_teams);
    }

    /**
     * The starred clubs chosen on this TV (the Control Centre's Teams & competitions): kept as the list, and the next
     * look at the stores stamps it as changed here, so the other TV hears of it in ten seconds and the phone next time
     * it syncs.
     */
    static void setStarredClubs(Context c, List<String> ids) {
        JSONArray a = new JSONArray();
        for (String id : ids) {
            String t = id == null ? "" : id.trim();
            if (!t.isEmpty() && t.length() < 12 && a.length() < 40) a.put(t);
        }
        prefs(c).edit().putString("clubs", a.toString()).apply();
        changed(c);
    }

    // ---- the stores as items -----------------------------------------------------------------------------------------

    /** Every item the TV holds now, by id ("mylist|tv:1399"), as data maps in a fixed order. */
    private static Map<String, Map<String, Object>> snapshot(Context c) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (JSONObject o : MyList.all(c)) {
            int id = o.optInt("id");
            boolean tv = o.optBoolean("tv");
            if (id <= 0) continue;
            out.put(SyncRules.id(MYLIST, SyncRules.titleKey(tv, id)), SyncRules.myListData(id, tv, o.optString("title"),
                    o.optString("poster", null), o.optString("backdrop", null), o.optLong("added")));
        }
        for (Map.Entry<String, String> e : Ratings.records(c).entrySet()) {
            Map<String, Object> d = SyncRules.ratingData(e.getValue());
            if (d != null) out.put(SyncRules.id(RATING, e.getKey()), d);
        }
        FilmNight.Plan p = FilmNight.get(c);
        if (p != null && p.id > 0) {
            out.put(SyncRules.id(FILMNIGHT, "plan"), SyncRules.planData(p.id, p.title, p.start, p.runtime, p.dim, p.remind, p.quiet,
                    p.poster, p.backdrop));
        }
        JSONObject titles = json(prefs(c).getString("mute_titles", "{}"));
        for (String k : ComingUp.mutedKeys(c)) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("title", titles.optString(k, ""));
            out.put(SyncRules.id(MUTE, k), d);
        }
        for (JSONObject o : Reminders.all(c)) {
            String key = SyncRules.reminderKey(o.optString("k", ""), o.optString("c"), o.optLong("s"), o.optString("t"));
            out.put(SyncRules.id(REMINDER, key), SyncRules.reminderData(o.optString("t"), o.optLong("s"), o.optString("c"),
                    o.optString("l"), o.optString("kind", "show")));
        }
        Map<String, Object> clubs = new LinkedHashMap<>();
        clubs.put("teams", new ArrayList<>(Arrays.asList(starredClubs(c))));
        out.put(CLUBS_ID, clubs);
        // My scoreboards: each saved board's data as it is kept (its JSON), under the name a relayed item's has.
        for (Map.Entry<String, ?> e : MyBoardStore.raw(c).entrySet()) {
            if (!(e.getValue() instanceof String) || !com.gathin.broadcast.MyBoards.validId(e.getKey())) continue;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put(RAW, e.getValue());
            out.put(SyncRules.id(MYBOARD, e.getKey()), d);
        }
        // The relayed kinds as they came (the phones' playlists, favourites, resume points and settings).
        for (Map.Entry<String, ?> e : relay(c).getAll().entrySet()) {
            if (!(e.getValue() instanceof String)) continue;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put(RAW, e.getValue());
            out.put(e.getKey(), d);
        }
        return out;
    }

    private static Map<String, String> canonical(Map<String, Map<String, Object>> snap) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : snap.entrySet()) out.put(e.getKey(), SyncRules.canonical(e.getValue()));
        return out;
    }

    private static Set<String> zero(Context c) {
        return prefs(c).contains("clubs") ? Collections.emptySet() : Collections.singleton(CLUBS_ID);
    }

    // ---- the notes kept per item ---------------------------------------------------------------------------------------

    private static Map<String, SyncRules.Meta> metas(Context c) {
        if (metas != null) return metas;
        metas = new HashMap<>();
        JSONObject all = json(prefs(c).getString("metas", "{}"));
        for (Iterator<String> it = all.keys(); it.hasNext(); ) {
            String id = it.next();
            JSONObject o = all.optJSONObject(id);
            if (o == null) continue;
            SyncRules.Meta m = new SyncRules.Meta();
            m.hash = o.optString("h");
            m.updated = o.optLong("u");
            m.seen = o.optLong("s");
            m.deleted = o.optBoolean("d");
            m.from = o.optString("f");
            metas.put(id, m);
        }
        return metas;
    }

    private static void save(Context c) {
        JSONObject all = new JSONObject();
        try {
            for (Map.Entry<String, SyncRules.Meta> e : metas(c).entrySet()) {
                SyncRules.Meta m = e.getValue();
                all.put(e.getKey(), new JSONObject().put("h", m.hash).put("u", m.updated).put("s", m.seen).put("d", m.deleted).put("f", m.from));
            }
        } catch (Exception ignored) {
            return;
        }
        prefs(c).edit().putString("metas", all.toString()).apply();
    }

    /** The stores looked at again: what changed here since the last look is stamped now. */
    private static synchronized Map<String, Map<String, Object>> scan(Context c) {
        Map<String, Map<String, Object>> snap = snapshot(c);
        long now = System.currentTimeMillis();
        List<String> changed = SyncRules.scan(canonical(snap), metas(c), now, TvLink.myName(c), zero(c));
        boolean pruned = SyncRules.prune(metas(c), now);
        if (!changed.isEmpty() || pruned) save(c);
        return snap;
    }

    /** Every item that reached this TV after since (its clock), tombstones included, but those in skip. */
    static synchronized JSONArray changedSince(Context c, long since, Set<String> skip) {
        return changedSince(c, since, skip, false);
    }

    /** The same; trusted: the caller has a token (a phone, a tablet, Homefield TV), so the playlists go too. */
    static synchronized JSONArray changedSince(Context c, long since, Set<String> skip, boolean trusted) {
        Map<String, Map<String, Object>> snap = scan(c);
        JSONArray out = new JSONArray();
        for (String id : SyncRules.since(metas(c), since)) {
            SyncRules.Meta m = metas(c).get(id);
            if (m == null || (skip != null && skip.contains(id + "@" + m.updated + "@" + m.from))) continue;
            int bar = id.indexOf('|');
            if (bar <= 0 || !SyncRules.shared(id.substring(0, bar), trusted)) continue;
            try {
                Map<String, Object> d = m.deleted ? null : snap.get(id);
                JSONObject data = d == null ? new JSONObject() : d.containsKey(RAW) ? new JSONObject((String) d.get(RAW)) : toJson(d);
                out.put(new JSONObject().put("type", id.substring(0, bar)).put("key", id.substring(bar + 1))
                        .put("data", data).put("updated", m.updated).put("deleted", m.deleted || d == null)
                        .put("from", m.from));
            } catch (Exception ignored) {
                // that one left out
            }
        }
        return out;
    }

    /**
     * Items from a phone or another TV merged in, last writer wins, each that wins written to its store. Returns the
     * received ones as "id@updated@from", to leave out of the reply.
     */
    static synchronized Set<String> merge(Context c, JSONArray items) {
        return merge(c, items, false);
    }

    /** The same; trusted: from a caller with a token, so its playlists are taken in too (never from a TV on its own). */
    static synchronized Set<String> merge(Context c, JSONArray items, boolean trusted) {
        Set<String> received = new HashSet<>();
        if (items == null || items.length() == 0) return received;
        scan(c);
        Map<String, JSONObject> applied = new LinkedHashMap<>();
        List<Object[]> lookups = new ArrayList<>();
        SharedPreferences.Editor relayEdit = relay(c).edit();
        Map<String, Integer> relayCounts = relayCounts(c);
        for (int i = 0; i < items.length(); i++) {
            JSONObject in = items.optJSONObject(i);
            if (in == null) continue;
            String type = in.optString("type"), key = in.optString("key");
            if (!TYPES.contains(type) || key.isEmpty() || key.length() > 200) continue;
            if (!SyncRules.shared(type, trusted)) continue;
            String id = SyncRules.id(type, key);
            long updated = in.optLong("updated");
            String from = in.optString("from");
            received.add(id + "@" + updated + "@" + from);
            if (!SyncRules.wins(updated, from, metas(c).get(id))) continue;
            boolean deleted = in.optBoolean("deleted");
            // Another TV's own clubs (never chosen there): it does not replace this TV's.
            if (CLUBS.equals(type) && updated == 0 && !deleted) continue;
            JSONObject data = in.optJSONObject("data");
            try {
                JSONObject d = data == null ? new JSONObject() : data;
                boolean took = SyncRules.RELAYED.contains(type) ? applyRelay(c, type, key, d, deleted, relayEdit, relayCounts)
                        : apply(c, type, key, d, deleted, lookups);
                if (took) applied.put(id, in);
            } catch (RuntimeException e) {
                android.util.Log.w("SyncHub", "could not apply a " + type + " item: " + e.getClass().getSimpleName());
            }
        }
        // Written before the snapshot below reads them back.
        relayEdit.commit();
        if (applied.isEmpty()) return received;
        // Each one noted as it now is here, with the time and the device it came with, so it is not taken for a change
        // made here.
        Map<String, Map<String, Object>> snap = snapshot(c);
        long now = System.currentTimeMillis();
        for (Map.Entry<String, JSONObject> e : applied.entrySet()) {
            Map<String, Object> d = snap.get(e.getKey());
            SyncRules.Meta m = new SyncRules.Meta();
            m.hash = d == null ? "" : SyncRules.hash(SyncRules.canonical(d));
            m.updated = e.getValue().optLong("updated");
            m.from = e.getValue().optString("from");
            m.deleted = d == null;
            m.seen = now;
            metas(c).put(e.getKey(), m);
        }
        save(c);
        AlertLog.note(c, "sync: " + applied.size() + " change" + (applied.size() == 1 ? "" : "s") + " taken in");
        if (!lookups.isEmpty()) {
            ensure(c);
            worker.post(() -> markOnApps(c, lookups));
        }
        return received;
    }

    /** How many of each relayed kind are kept now. */
    private static Map<String, Integer> relayCounts(Context c) {
        Map<String, Integer> out = new HashMap<>();
        for (String id : relay(c).getAll().keySet()) {
            int bar = id.indexOf('|');
            if (bar > 0) out.merge(id.substring(0, bar), 1, Integer::sum);
        }
        return out;
    }

    /** One relayed item kept as it came (or let go for a tombstone); false when there was nothing to write. */
    private static boolean applyRelay(Context c, String type, String key, JSONObject d, boolean deleted, SharedPreferences.Editor edit,
                                      Map<String, Integer> counts) {
        String id = SyncRules.id(type, key);
        SharedPreferences r = relay(c);
        boolean known = r.contains(id);
        if (deleted) {
            if (!known) return false;
            edit.remove(id);
            counts.merge(type, -1, Integer::sum);
            return true;
        }
        String raw = d.toString();
        Integer had = counts.get(type);
        if (!SyncRules.relayFits(type, key, raw.length(), known, had == null ? 0 : had)) return false;
        if (raw.equals(r.getString(id, null))) return false;
        edit.putString(id, raw);
        if (!known) counts.merge(type, 1, Integer::sum);
        return true;
    }

    /** One item written to its store; false when there was nothing to write. */
    private static boolean apply(Context c, String type, String key, JSONObject d, boolean deleted, List<Object[]> lookups) {
        switch (type) {
            case MYLIST: {
                Object[] k = SyncRules.parseTitleKey(key);
                if (k == null) return false;
                boolean tv = (Boolean) k[0];
                int id = (Integer) k[1];
                if (deleted) return MyList.removeSynced(c, id, tv);
                boolean added = MyList.putSynced(c, id, tv, d.optString("title"), blank(d.optString("poster", null)),
                        blank(d.optString("backdrop", null)), d.optLong("added", System.currentTimeMillis()));
                if (added) lookups.add(new Object[]{id, tv});
                return true;
            }
            case RATING: {
                if (deleted) return Ratings.removeSynced(c, key);
                String rec = SyncRules.ratingRecord(d.optInt("value"), d.optLong("at", System.currentTimeMillis()),
                        d.optString("genre", Ratings.genre(c, key)), d.optString("title"));
                if (rec == null) return false;
                Ratings.putSynced(c, key, rec);
                return true;
            }
            case FILMNIGHT: {
                if (!"plan".equals(key)) return false;
                FilmNight.Plan had = FilmNight.get(c);
                if (deleted) {
                    if (had == null) return false;
                    FilmNight.set(c, null);
                    return true;
                }
                int id = d.optInt("id");
                long start = d.optLong("start");
                if (id <= 0 || start <= 0) return false;
                FilmNight.Plan p = had != null && had.id == id && had.start == start ? FilmNight.Plan.of(had.json()) : new FilmNight.Plan();
                p.id = id;
                p.title = d.optString("title", p.title);
                p.start = start;
                p.runtime = d.optInt("runtime", p.runtime > 0 ? p.runtime : 120);
                p.dim = d.optBoolean("lights", p.dim);
                p.remind = d.optBoolean("remind", p.remind);
                p.quiet = d.optBoolean("quiet", p.quiet);
                String poster = blank(d.optString("poster", null)), backdrop = blank(d.optString("backdrop", null));
                if (poster != null) p.poster = poster;
                if (backdrop != null) p.backdrop = backdrop;
                FilmNight.set(c, p);
                return true;
            }
            case MUTE: {
                if (SyncRules.parseTitleKey(key) == null) return false;
                JSONObject titles = json(prefs(c).getString("mute_titles", "{}"));
                try {
                    if (deleted) titles.remove(key);
                    else titles.put(key, d.optString("title", ""));
                } catch (Exception ignored) {
                    // no title kept
                }
                prefs(c).edit().putString("mute_titles", titles.toString()).apply();
                ComingUp.setMuted(c, key, !deleted);
                return true;
            }
            case REMINDER: {
                if (deleted) return Reminders.removeSynced(c, key);
                long at = d.optLong("at");
                // Long past: not set again (its alert would come a second time).
                if (at <= 0 || at < System.currentTimeMillis() - 30 * 60_000L) return false;
                Reminders.putSynced(c, key, d.optString("title"), at, d.optString("channel", ""), d.optString("logo", ""),
                        d.optString("kind", ""));
                return true;
            }
            case MYBOARD: {
                // One of My scoreboards, from a phone or another TV: read safely (a bad spec is a safe board), else let go.
                if (deleted) return MyBoardStore.delete(c, key, false);
                com.gathin.broadcast.MyBoards.Saved s = com.gathin.broadcast.MyBoards.read(key, d);
                if (s == null || d.toString().length() > SyncRules.RELAY_BYTES) return false;
                return MyBoardStore.save(c, s, false);
            }
            case CLUBS: {
                if (!"starred".equals(key)) return false;
                if (deleted) {
                    prefs(c).edit().remove("clubs").apply();
                    return true;
                }
                JSONArray teams = d.optJSONArray("teams");
                if (teams == null) return false;
                JSONArray clean = new JSONArray();
                for (int i = 0; i < teams.length() && i < 40; i++) {
                    String t = teams.optString(i).trim();
                    if (!t.isEmpty() && t.length() < 12) clean.put(t);
                }
                prefs(c).edit().putString("clubs", clean.toString()).apply();
                return true;
            }
            default:
                return false;
        }
    }

    /** My list's titles added from elsewhere: marked as on the TV's apps when they are, so no "now on" pop-up. */
    private static void markOnApps(Context c, List<Object[]> titles) {
        String key = Config.themeString(c, "tmdb_key", "").trim();
        if (key.isEmpty()) return;
        for (Object[] t : titles) {
            int id = (Integer) t[0];
            boolean tv = (Boolean) t[1];
            if (onApps(c, PagesV2.providersGb(key, id, tv)) != null) MyList.arrived(c, id, tv);
        }
    }

    // ---- the house's other TVs -----------------------------------------------------------------------------------------

    /** Whether a /sync without a token comes from one of the house's TVs: one TvLink knows, or one that says so now. */
    static boolean houseTv(Context c, String host) {
        if (host == null) return false;
        for (String[] p : TvLink.known(c)) if (host.equals(p[1])) return true;
        String[] r = TvLink.http(host, "POST", "/hello", "{}", 400, 1500);
        if (r == null || !"200".equals(r[0])) return false;
        String name = json(r[1]).optString("tv");
        if (name.isEmpty() || name.equals(TvLink.myName(c))) return false;
        TvLink.add(c, name, host);
        return true;
    }

    /** One sync with every other TV (on the worker thread). */
    private static void syncPeers() {
        Context c = app;
        if (c == null) return;
        List<String[]> peers = TvLink.known(c);
        if (peers.isEmpty() && System.currentTimeMillis() - lastFind > FIND_EVERY_MS) {
            lastFind = System.currentTimeMillis();
            peers = TvLink.find(c);
        }
        for (String[] p : peers) {
            try {
                syncWith(c, p[0], p[1]);
            } catch (RuntimeException e) {
                android.util.Log.w("SyncHub", "sync with " + p[0] + ": " + e);
            }
        }
    }

    private static void syncWith(Context c, String name, String host) {
        SharedPreferences pr = prefs(c);
        long sent = pr.getLong("sent." + host, 0), since = pr.getLong("since." + host, 0);
        long stamp = System.currentTimeMillis();
        // Between TVs on their own (no token): never the playlists' logins, either way.
        JSONArray out = changedSince(c, sent, null, false);
        String body;
        try {
            body = new JSONObject().put("since", since).put("device", TvLink.myName(c)).put("items", out).toString();
        } catch (Exception e) {
            return;
        }
        String[] r = post(host, "/sync", body, 1500, 10_000);
        if (r == null || !"200".equals(r[0])) return;
        JSONObject reply = json(r[1]);
        merge(c, reply.optJSONArray("items"), false);
        pr.edit().putLong("since." + host, reply.optLong("now", since)).putLong("sent." + host, stamp).apply();
    }

    /** {status, body} of a POST to another TV's link, the reply up to 2 MB; null when nothing answered. */
    private static String[] post(String host, String path, String body, int connectMs, int readMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, DeliveryLink.PORT), connectMs);
            s.setSoTimeout(readMs);
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            OutputStream o = s.getOutputStream();
            o.write(("POST " + path + " HTTP/1.1\r\nHost: " + host + "\r\nContent-Type: application/json\r\nContent-Length: " + b.length
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            o.write(b);
            o.flush();
            InputStream in = s.getInputStream();
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) {
                all.write(buf, 0, n);
                if (all.size() > READ_CAP) return null;
            }
            String text = all.toString("UTF-8");
            int sp = text.indexOf(' '), end = text.indexOf("\r\n\r\n");
            if (sp < 0) return null;
            return new String[]{text.substring(sp + 1, Math.min(text.length(), sp + 4)), end < 0 ? "" : text.substring(end + 4)};
        } catch (Exception e) {
            return null;
        }
    }

    // ---- what the phone reads and asks for -------------------------------------------------------------------------------

    /** What is being watched here, for the phone (read only): Your shows' series and Continue watching's titles. */
    static JSONObject watching(Context c) {
        JSONObject out = new JSONObject();
        JSONArray shows = new JSONArray(), resume = new JSONArray();
        try {
            Set<Integer> seen = new HashSet<>();
            for (WatchHistory.Entry e : WatchHistory.load(c)) {
                if (!e.tv || e.tmdbId <= 0 || !seen.add(e.tmdbId) || shows.length() >= 30) continue;
                JSONObject s = new JSONObject().put("id", e.tmdbId).put("title", e.show != null ? e.show : e.title)
                        .put("poster", blank(e.poster)).put("backdrop", blank(e.backdrop)).put("progress", e.progress())
                        .put("app", e.pkg).put("at", e.at);
                int[] pos = YourShows.position(e);
                if (pos != null) s.put("season", pos[0]).put("episode", pos[1]);
                JSONObject d = YourShows.cached(e.tmdbId);
                JSONObject next = d == null ? null : d.optJSONObject("next_episode_to_air");
                if (next != null) {
                    s.put("next", new JSONObject().put("season", next.optInt("season_number")).put("episode", next.optInt("episode_number"))
                            .put("name", next.optString("name")).put("date", next.optString("air_date")));
                }
                shows.put(s);
            }
            for (PagesV2.Title t : WatchHistory.titles(c, null)) {
                if (resume.length() >= 30) break;
                Float p = WatchHistory.progress(c).get(t.id);
                resume.put(new JSONObject().put("id", t.id).put("tv", t.tv).put("title", t.title).put("poster", blank(t.poster))
                        .put("backdrop", blank(t.backdrop)).put("progress", p == null ? 0f : p).put("what", t.what).put("app", t.app));
            }
            out.put("shows", shows).put("resume", resume);
        } catch (Exception ignored) {
            // what could be read
        }
        return out;
    }

    /**
     * A title opened on this TV from the phone: in the licensed streaming app here that carries it in the UK, else the
     * store that rents or sells it, else a note on screen. Never Debrid Stream, Kleva TV, TiviMate or any IPTV app.
     */
    static void play(Context c, int id, boolean tv, String title) {
        ensure(c);
        worker.post(() -> {
            String key = Config.themeString(c, "tmdb_key", "").trim();
            JSONObject gb = id > 0 && !key.isEmpty() ? PagesV2.providersGb(key, id, tv) : null;
            String where = onApps(c, gb);
            if (where == null) where = storeFor(c, gb);
            String pkg = where;
            AlertLog.note(c, "play from the phone: " + title + (pkg == null ? " (not on the TV's apps)" : " in " + pkg));
            new Handler(Looper.getMainLooper()).post(() -> {
                if (pkg == null) {
                    android.widget.Toast.makeText(c, title + " isn't on your streaming apps yet", android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
                Runnable open = () -> AppSearchService.search(c, pkg, title, tv);
                if (!AppSearchService.running()) {
                    AppSearchService.ensure(c);
                    new Handler(Looper.getMainLooper()).postDelayed(open, 1600);
                } else {
                    open.run();
                }
            });
        });
    }

    /** The licensed streaming app on this TV that carries a title in the UK (gb: TMDB's providers there), or null. */
    private static String onApps(Context c, JSONObject gb) {
        JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
        for (int i = 0; flat != null && i < flat.length(); i++) {
            JSONObject p = flat.optJSONObject(i);
            String pkg = p == null ? null : HomeV2.serviceApp(String.valueOf(p.optInt("provider_id")));
            if (pkg != null && installed(c, pkg)) return pkg;
        }
        return null;
    }

    private static String storeFor(Context c, JSONObject gb) {
        String[][] stores = {{"10", "com.amazon.amazonvideo.livingroom"}, {"2", "com.apple.atve.androidtv.appletv"},
                {"192", "com.google.android.youtube.tv"}, {"3", "com.google.android.videos"}};
        for (String kind : new String[]{"rent", "buy"}) {
            JSONArray list = gb == null ? null : gb.optJSONArray(kind);
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject p = list.optJSONObject(i);
                String id = p == null ? "" : String.valueOf(p.optInt("provider_id"));
                for (String[] s : stores) if (s[0].equals(id) && installed(c, s[1])) return s[1];
            }
        }
        return null;
    }

    private static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** adb only (test_sync): how many items the TV holds, by type, in the alert log. */
    static void dump(Context c) {
        ensure(c);
        worker.post(() -> {
            Map<String, Integer> n = new LinkedHashMap<>();
            for (String id : scan(c).keySet()) {
                String t = id.substring(0, id.indexOf('|'));
                Integer had = n.get(t);
                n.put(t, had == null ? 1 : had + 1);
            }
            int tomb = 0;
            synchronized (SyncHub.class) {
                for (SyncRules.Meta m : metas(c).values()) if (m.deleted) tomb++;
            }
            AlertLog.note(c, "sync items: " + n + ", tombstones " + tomb + ", other TVs " + TvLink.known(c).size());
        });
    }

    // ---- small helpers ---------------------------------------------------------------------------------------------------

    private static JSONObject toJson(Map<String, Object> d) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, Object> e : d.entrySet()) {
            Object v = e.getValue();
            if (v instanceof List) {
                JSONArray a = new JSONArray();
                for (Object x : (List<?>) v) a.put(x);
                o.put(e.getKey(), a);
            } else {
                o.put(e.getKey(), v);
            }
        }
        return o;
    }

    private static JSONObject json(String s) {
        try {
            return new JSONObject(s == null ? "{}" : s);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String blank(String s) {
        return s == null || s.isEmpty() || "null".equals(s) ? null : s;
    }
}
