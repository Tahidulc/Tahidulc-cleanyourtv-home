package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.JsonReader;
import android.util.JsonToken;

import com.gathin.homefieldtv.prefs.GuidePrefs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live TV's channels and guide from the family's own playlists: read from the providers off the main thread, kept on
 * disk in the app's private files (drawn at once next time), the channels again after a day and the guide after six
 * hours. A channel in more than one playlist is shown once, the others' copies its backups. Also keeps the TV's
 * favourite channels and the last one watched (the mini TV starts on it).
 */
public final class Library {
    public static final String ALL = "", FAVOURITES = "★";
    // How often the playlists and the guide are read again: Settings → Guide (GuidePrefs; as it came, a day and 6 hours).
    private static Library instance;

    private final Context app;
    private volatile List<Channel> channels = Collections.emptyList();
    private volatile String[] lower = new String[0];
    private volatile List<String> groups = Collections.emptyList();
    private volatile Map<String, Integer> byKey = Collections.emptyMap();
    private volatile Map<String, Guide.Schedule> guide = Collections.emptyMap();
    private volatile String fingerprint = "", guideUrl = "", status = "";
    /** Catch-up: {channels whose provider keeps an archive, the most days kept}. */
    private volatile int[] archive = {0, 0};
    private volatile long loadedAt, guideAt;
    private volatile boolean read;
    private final AtomicBoolean busy = new AtomicBoolean(), guideBusy = new AtomicBoolean();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** Told on the main thread how a test or refresh went: the channel count, or an error to show. */
    public interface Callback {
        void done(int channels, String error);
    }

    private Library(Context app) {
        this.app = app;
    }

    public static synchronized Library get(Context c) {
        if (instance == null) instance = new Library(c.getApplicationContext());
        return instance;
    }

    // ---- what the screens read ----------------------------------------------------------------------------------

    public boolean hasSource() { return !Sources.list(app).isEmpty(); }
    public boolean ready() { return !channels.isEmpty(); }
    public boolean busy() { return busy.get(); }
    public String status() { return status; }
    public int count() { return channels.size(); }
    /** Catch-up: {how many channels keep programmes, for up to how many days}. */
    public int[] archive() { return archive; }
    public boolean hasGuide() { return !guide.isEmpty(); }
    /** The playlist's own groups, in first-seen order. */
    public List<String> groups() { return groups; }
    /**
     * The channels the screens list: without hidden categories and channels locked away (Settings → Categories and
     * Parental control, prefs.Shown).
     */
    public List<Channel> all() { return com.gathin.homefieldtv.prefs.Shown.of(app, channels); }
    /** Every channel, hidden ones too (a channel's other copies, its quality levels). */
    public List<Channel> every() { return channels; }

    public Channel byKey(String key) {
        Integer i = key == null ? null : byKey.get(key);
        List<Channel> ch = channels;
        return i == null || i >= ch.size() ? null : ch.get(i);
    }

    public Guide.Schedule schedule(String key) { return guide.get(key); }

    /** The channel's place in the list (0 first), or -1. */
    public int position(String key) {
        Integer i = key == null ? null : byKey.get(key);
        return i == null ? -1 : i;
    }

    /** The number shown for a channel: the playlist's own (tvg-chno, Xtream's num), else its place in the playlist. */
    public int number(Channel c) {
        if (c.number > 0) return c.number;
        int p = position(c.key);
        return p < 0 ? 0 : p + 1;
    }

    /** A group's channels (ALL, FAVOURITES or a group's name), narrowed by a search. */
    public List<Channel> list(String group, String query) {
        List<Channel> ch = channels;
        String[] low = lower;
        String q = query == null ? "" : Names.plain(query).toLowerCase(Locale.ROOT).trim();
        List<Channel> out = new ArrayList<>();
        if (FAVOURITES.equals(group)) {
            for (String k : favourites()) {
                Channel c = byKey(k);
                if (c == null || !com.gathin.homefieldtv.prefs.Shown.kept(app, c)) continue;
                if (q.isEmpty() || Names.plain(c.name).toLowerCase(Locale.ROOT).contains(q)) out.add(c);
            }
            return out;
        }
        for (int i = 0; i < ch.size(); i++) {
            Channel c = ch.get(i);
            if (group != null && !group.isEmpty() && !group.equals(c.group)) continue;
            if (!q.isEmpty() && (i >= low.length || !low[i].contains(q))) continue;
            if (!com.gathin.homefieldtv.prefs.Shown.listed(app, c)) continue;
            out.add(c);
        }
        return out;
    }

    // ---- favourites and the last channel -------------------------------------------------------------------------

    private SharedPreferences prefs() {
        return app.getSharedPreferences("live_tv", Context.MODE_PRIVATE);
    }

    /** The favourite channels' keys, in the order they were added. */
    public List<String> favourites() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs().getString("favourites", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException ignored) {
            // none
        }
        return out;
    }

    public boolean isFavourite(Channel c) {
        return c != null && favourites().contains(c.key);
    }

    /** A channel added to the favourites, or taken off them; true when it is one now. */
    public boolean toggleFavourite(Channel c) {
        Set<String> f = new LinkedHashSet<>(favourites());
        boolean now = !f.remove(c.key);
        if (now) f.add(c.key);
        prefs().edit().putString("favourites", new JSONArray(f).toString()).apply();
        changed();
        return now;
    }

    /** The channel last watched on this TV, or the first one when none has been. */
    public Channel lastChannel() {
        Channel c = byKey(prefs().getString("last", null));
        if (c != null) return c;
        List<Channel> ch = channels;
        return ch.isEmpty() ? null : ch.get(0);
    }

    /** A channel played: the last one, and first of the recent channels (kept on this TV only). */
    public void watched(Channel c) {
        if (c == null) return;
        SharedPreferences p = prefs();
        List<String> recent = RecentChannels.add(RecentChannels.parse(p.getString("recent", "")), c.key);
        p.edit().putString("last", c.key).putString("recent", RecentChannels.join(recent)).apply();
    }

    /**
     * The channels last watched on this TV that the playlists still have, newest first: without the one playing (null
     * for none), RecentChannels.MAX at most.
     */
    public List<Channel> recent(Channel playing) {
        List<Channel> out = new ArrayList<>();
        List<String> keys = RecentChannels.parse(prefs().getString("recent", ""));
        for (String k : RecentChannels.shown(keys, playing == null ? null : playing.key)) {
            Channel c = byKey(k);
            if (c != null && com.gathin.homefieldtv.prefs.Shown.kept(app, c)) out.add(c);
        }
        return out;
    }

    // ---- loading ----------------------------------------------------------------------------------------------

    /** The cached channels read if they haven't been, then a refresh when they or the guide are old. Any thread. */
    public void ensure() {
        if (read) {
            maybeRefresh();
            return;
        }
        Exec.io(() -> {
            synchronized (this) {
                if (!read) {
                    readCache();
                    read = true;
                }
            }
            changed();
            maybeRefresh();
        });
    }

    private void maybeRefresh() {
        if (Sources.inUse(app).isEmpty()) return;
        long now = System.currentTimeMillis();
        // Settings → Guide: how often the playlists and the guide are read again, and on each start if chosen.
        boolean start = GuidePrefs.firstStart(app);
        if (channels.isEmpty() || start || GuidePrefs.listsDue(app, loadedAt, now) || !Sources.fingerprint(app).equals(fingerprint)) {
            refresh(null);
        } else if (!GuidePrefs.guides(app, guideUrl).isEmpty() && GuidePrefs.guideDue(app, guideAt, now)) {
            refreshGuide();
        }
    }

    /** The channels of the playlists in use read again from their providers now (then the guides). */
    public void refresh(Callback cb) {
        if (Sources.inUse(app).isEmpty()) {
            if (cb != null) Exec.main(() -> cb.done(0, "No playlist yet"));
            return;
        }
        load(null, null, null, cb);
    }

    /**
     * A playlist tried out (id null or empty: a new one): saved only when it gives live channels, then the playlists in
     * use read again with it.
     */
    public void test(String id, String name, Source s, Callback cb) {
        load(id == null ? "" : id, name, s, cb);
    }

    private void load(String trialId, String trialName, Source trial, Callback cb) {
        if (!busy.compareAndSet(false, true)) {
            if (cb != null) Exec.main(() -> cb.done(0, "Already loading, a moment"));
            return;
        }
        status = "";
        changed();
        Exec.io(() -> {
            int n = 0;
            String error = null;
            try {
                String tried = null;
                Loaded first = null;
                if (trial != null) {
                    // Checked on its own first: kept only when it gives live channels.
                    String probeId = trialId.isEmpty() ? "new" : trialId;
                    first = fetch(trial, probeId);
                    if (first.channels.isEmpty()) throw new IOException("No live channels in it");
                    tried = Sources.put(app, trialId, trialName, trial).id;
                    if (trialId.isEmpty()) {
                        Catalog.moved(app, "new", tried);
                        Sources.moved(app, "new", tried);
                        if (!Sources.active(app).isEmpty()) Sources.setActive(app, Sources.ALL);
                    }
                }
                List<Channel> all = new ArrayList<>();
                List<String> guides = new ArrayList<>(), problems = new ArrayList<>();
                for (Sources.Entry e : Sources.inUse(app)) {
                    try {
                        Loaded l = e.id.equals(tried) ? first : fetch(e.source, e.id);
                        for (Channel c : l.channels) all.add(e.prefix().isEmpty() ? c : c.keyed(e.prefix() + c.key));
                        if (!l.guideUrl.isEmpty() && !guides.contains(l.guideUrl)) guides.add(l.guideUrl);
                    } catch (IOException x) {
                        problems.add(e.name + ": " + (x.getMessage() == null ? "didn't load" : x.getMessage()));
                    }
                }
                if (all.isEmpty()) throw new IOException(problems.isEmpty() ? "No live channels in it" : String.join("; ", problems));
                String fp = Sources.fingerprint(app);
                if (!fp.equals(fingerprint)) clearGuide();
                apply(all, fp, String.join(" ", guides), System.currentTimeMillis());
                writeCache();
                n = channels.size();
                if (!problems.isEmpty()) status = String.join("; ", problems);
            } catch (IOException | RuntimeException e) {
                error = e instanceof IOException && e.getMessage() != null ? e.getMessage() : "That didn't work";
                status = error;
            } finally {
                busy.set(false);
            }
            int count = n;
            String err = error;
            changed();
            if (cb != null) Exec.main(() -> cb.done(count, err));
            if (err == null) {
                refreshGuide();
                // The playlists' films and series read again with them.
                Catalog.get(app).refresh();
            }
        });
    }

    /** Everything removed from this TV: the playlists, the channels, the guide, the favourites, the catalogues. */
    public void clear() {
        Sources.clear(app);
        Catalog.get(app).clear();
        prefs().edit().clear().apply();
        channels = Collections.emptyList();
        lower = new String[0];
        groups = Collections.emptyList();
        byKey = Collections.emptyMap();
        fingerprint = "";
        guideUrl = "";
        status = "";
        loadedAt = 0;
        archive = new int[]{0, 0};
        //noinspection ResultOfMethodCallIgnored
        file("channels.tsv").delete();
        clearGuide();
        changed();
    }

    private void clearGuide() {
        guide = Collections.emptyMap();
        guideAt = 0;
        //noinspection ResultOfMethodCallIgnored
        file("guide.tsv").delete();
    }

    /**
     * The same channel in several playlists made one (pure, for the tests): the first playlist's, the others' as its
     * backups (Channel.alternates), matched by name, picture and +1 (Names). Two of one playlist's own stay apart, as
     * that playlist lists them.
     */
    static List<Channel> merged(List<Channel> list) {
        Map<String, Channel> first = new HashMap<>();
        List<Channel> out = new ArrayList<>(list.size());
        for (Channel c : list) {
            Names.Info i = Names.info(c.name, c.group);
            if (i.key.isEmpty()) {
                out.add(c);
                continue;
            }
            String k = i.key + "|" + i.quality + (i.backup ? "|b" : "");
            Channel f = first.get(k);
            if (f != null && !playlistOf(f.key).equals(playlistOf(c.key))) {
                f.alternates.add(c);
                continue;
            }
            if (f == null) first.put(k, c);
            out.add(c);
        }
        return out;
    }

    /** Which playlist a channel's key is from: "p0" for the first (no prefix), else its prefix's id. */
    static String playlistOf(String key) {
        if (key != null && key.length() > 2 && key.charAt(0) == 'p') {
            int colon = key.indexOf(':');
            if (colon > 1 && key.substring(1, colon).matches("\\d+")) return key.substring(0, colon);
        }
        return "p0";
    }

    private static final class Loaded {
        final List<Channel> channels;
        final String guideUrl;

        Loaded(List<Channel> channels, String guideUrl) {
            this.channels = channels;
            this.guideUrl = guideUrl == null ? "" : guideUrl;
        }
    }

    /** One playlist read (its id, for its films, series and account details to be kept under). */
    private Loaded fetch(Source s, String id) throws IOException {
        if (Source.M3U.equals(s.type)) {
            Loaded l;
            try (InputStream in = Fetch.open(s.m3uUrl, null); Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                M3u.Result res = M3u.parse(r);
                // Its films and episodes kept for Movies and TV shows (Catalog), the channels for Live TV.
                Catalog.saveM3u(app, id, res.vod);
                l = new Loaded(res.channels, s.guideUrl.isEmpty() ? res.guideUrl : s.guideUrl);
            }
            // An Xtream server's M3U address: its account's expiry date asked for too (no matter if it doesn't answer).
            String[] x = Sources.xtreamOf(s.m3uUrl);
            if (x != null) {
                String zone = "";
                try {
                    JSONObject acct = new JSONObject(text(Xtream.api(x[0], x[1], x[2], null)));
                    Sources.note(app, id, acct.optJSONObject("user_info"));
                    zone = timezone(acct);
                } catch (IOException | JSONException | RuntimeException ignored) {
                    // no expiry date for it
                }
                // Its catch-up: as the playlist says, else as the player API says (which streams it keeps, how long).
                Map<String, Integer> days = Catchup.count(l.channels)[0] > 0 ? null : archiveDays(x[0], x[1], x[2]);
                l = new Loaded(Catchup.withXtream(l.channels, days, zone), l.guideUrl);
            }
            return l;
        }
        return xtream(s, id);
    }

    /** An Xtream Codes account: the login checked (its expiry date kept), its live categories, then its live streams. */
    private Loaded xtream(Source s, String id) throws IOException {
        String base = Xtream.base(s.server);
        if (base.isEmpty()) throw new IOException("Add the server's address");
        String ext, zone;
        try {
            JSONObject acct = new JSONObject(text(Xtream.api(base, s.user, s.pass, null)));
            zone = timezone(acct);
            JSONObject ui = acct.optJSONObject("user_info");
            if (ui == null || !"1".equals(ui.optString("auth"))) throw new IOException("The login wasn't accepted");
            Sources.note(app, id, ui);
            String st = ui.optString("status", "Active");
            if (!st.isEmpty() && !st.equalsIgnoreCase("Active")) throw new IOException("The account says it is " + st.toLowerCase(Locale.ROOT));
            List<String> formats = new ArrayList<>();
            JSONArray f = ui.optJSONArray("allowed_output_formats");
            for (int i = 0; f != null && i < f.length(); i++) formats.add(f.optString(i));
            ext = Xtream.pickFormat(formats);
        } catch (JSONException e) {
            throw new IOException("That server didn't answer as Xtream Codes does");
        }
        Map<String, String> cats = new LinkedHashMap<>();
        try {
            JSONArray a = new JSONArray(text(Xtream.api(base, s.user, s.pass, "get_live_categories")));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) cats.put(o.optString("category_id"), o.optString("category_name"));
            }
        } catch (JSONException e) {
            // channels without groups
        }
        List<Channel> out = new ArrayList<>();
        try (InputStream in = Fetch.open(Xtream.api(base, s.user, s.pass, "get_live_streams"), null);
             JsonReader jr = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            jr.setLenient(true);
            if (jr.peek() != JsonToken.BEGIN_ARRAY) throw new IOException("No live channels in it");
            jr.beginArray();
            while (jr.hasNext()) {
                if (jr.peek() != JsonToken.BEGIN_OBJECT) {
                    jr.skipValue();
                    continue;
                }
                String sid = "", name = "", icon = "", epg = "", cat = "", type = "live", kept = "", keptFor = "";
                int num = 0;
                jr.beginObject();
                while (jr.hasNext()) {
                    String k = jr.nextName();
                    String v = value(jr);
                    switch (k) {
                        case "stream_id": sid = v; break;
                        case "name": name = v; break;
                        case "stream_icon": icon = v; break;
                        case "epg_channel_id": epg = v; break;
                        case "category_id": cat = v; break;
                        case "stream_type": type = v; break;
                        case "num": num = number(v); break;
                        case "tv_archive": kept = v; break;
                        case "tv_archive_duration": keptFor = v; break;
                        default: break;
                    }
                }
                jr.endObject();
                if (sid.isEmpty() || (!type.isEmpty() && !type.equalsIgnoreCase("live") && !type.equalsIgnoreCase("created_live"))) continue;
                String group = cats.get(cat);
                int days = Catchup.xtreamDays(kept, keptFor);
                out.add(new Channel("x:" + sid, name, group, icon, Xtream.stream(base, s.user, s.pass, sid, ext), epg, null, null, num,
                        days > 0 ? Catchup.XC : "", "", days, zone));
            }
            jr.endArray();
        } catch (IllegalStateException | NumberFormatException e) {
            if (out.isEmpty()) throw new IOException("That server didn't answer as Xtream Codes does");
        }
        // In the categories' order, as the provider's own apps show them.
        if (!cats.isEmpty()) {
            List<String> order = new ArrayList<>(cats.values());
            Map<String, Integer> rank = new HashMap<>();
            for (int i = 0; i < order.size(); i++) if (!rank.containsKey(order.get(i))) rank.put(order.get(i), i);
            List<Channel> sorted = new ArrayList<>(out);
            sorted.sort((a, b) -> {
                Integer ra = rank.get(a.group), rb = rank.get(b.group);
                return Integer.compare(ra == null ? Integer.MAX_VALUE : ra, rb == null ? Integer.MAX_VALUE : rb);
            });
            out = sorted;
        }
        return new Loaded(out, s.guideUrl.isEmpty() ? Xtream.xmltv(base, s.user, s.pass) : s.guideUrl);
    }

    /** The Xtream server's time zone from its player API's answer (server_info.timezone), "" when it gives none. */
    static String timezone(JSONObject acct) {
        JSONObject si = acct == null ? null : acct.optJSONObject("server_info");
        return si == null ? "" : si.optString("timezone", "").trim();
    }

    /**
     * Which of an Xtream server's live streams it keeps an archive of, and for how many days (by stream id), from its
     * player API: for its M3U playlist, which doesn't say. Nothing when it doesn't answer.
     */
    private static Map<String, Integer> archiveDays(String base, String user, String pass) {
        Map<String, Integer> out = new HashMap<>();
        try (InputStream in = Fetch.open(Xtream.api(base, user, pass, "get_live_streams"), null);
             JsonReader jr = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            jr.setLenient(true);
            if (jr.peek() != JsonToken.BEGIN_ARRAY) return out;
            jr.beginArray();
            while (jr.hasNext()) {
                if (jr.peek() != JsonToken.BEGIN_OBJECT) {
                    jr.skipValue();
                    continue;
                }
                String sid = "", kept = "", keptFor = "";
                jr.beginObject();
                while (jr.hasNext()) {
                    String k = jr.nextName();
                    switch (k) {
                        case "stream_id": sid = value(jr); break;
                        case "tv_archive": kept = value(jr); break;
                        case "tv_archive_duration": keptFor = value(jr); break;
                        default: jr.skipValue(); break;
                    }
                }
                jr.endObject();
                int d = Catchup.xtreamDays(kept, keptFor);
                if (d > 0 && !sid.isEmpty()) out.put(sid, d);
            }
            jr.endArray();
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            // No archive known: the channels play live as before.
        }
        return out;
    }

    static String value(JsonReader jr) throws IOException {
        JsonToken t = jr.peek();
        switch (t) {
            case STRING:
            case NUMBER:
                return jr.nextString();
            case BOOLEAN:
                return String.valueOf(jr.nextBoolean());
            case NULL:
                jr.nextNull();
                return "";
            default:
                jr.skipValue();
                return "";
        }
    }

    static String text(String url) throws IOException {
        try (InputStream in = Fetch.open(url, null); Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder b = new StringBuilder();
            char[] buf = new char[8192];
            for (int n; (n = r.read(buf)) > 0; ) {
                b.append(buf, 0, n);
                if (b.length() > 8_000_000) break;
            }
            return b.toString();
        }
    }

    static int number(String s) {
        try {
            return s == null || s.isEmpty() ? 0 : (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** New channels in place: the groups in first-seen order, the key index built once. */
    private synchronized void apply(List<Channel> raw, String fp, String gUrl, long at) {
        List<Channel> ch = Collections.unmodifiableList(new ArrayList<>(merged(raw)));
        String[] low = new String[ch.size()];
        Map<String, Integer> keys = new HashMap<>(ch.size() * 2);
        LinkedHashMap<String, Boolean> gs = new LinkedHashMap<>();
        for (int i = 0; i < ch.size(); i++) {
            Channel c = ch.get(i);
            low[i] = Names.plain(c.name).toLowerCase(Locale.ROOT);
            keys.put(c.key, i);
            if (!c.group.isEmpty()) gs.put(c.group, Boolean.TRUE);
        }
        lower = low;
        byKey = keys;
        groups = Collections.unmodifiableList(new ArrayList<>(gs.keySet()));
        archive = Catchup.count(ch);
        channels = ch;
        fingerprint = fp;
        guideUrl = gUrl == null ? "" : gUrl;
        loadedAt = at;
    }

    /** Previews only: channels and a guide given straight in, as if read from a playlist. */
    public void useForPreview(List<Channel> list, Map<String, Guide.Schedule> schedules) {
        // As if just read from the playlists in use, so nothing is read again from a provider.
        apply(list, Sources.fingerprint(app), "", System.currentTimeMillis());
        guide = schedules;
        read = true;
        changed();
    }

    /** The guide read again (in the background, after the channels): only the playlist's channels, a 13 hour window. */
    void refreshGuide() {
        // The playlists' own guides, then Settings → Guide's (each read in the guide's own time when it is shifted).
        String urls = GuidePrefs.guides(app, guideUrl);
        long shift = GuidePrefs.shiftMs(app);
        if (urls.isEmpty() || channels.isEmpty() || !guideBusy.compareAndSet(false, true)) return;
        Exec.io(() -> {
            try {
                Map<String, List<String>> ids = new HashMap<>(), names = new HashMap<>();
                for (Channel main : channels) {
                    // A channel's guide by its own guide id, or one of its backups' (another playlist's guide may have it).
                    List<Channel> all = new ArrayList<>(1 + main.alternates.size());
                    all.add(main);
                    all.addAll(main.alternates);
                    boolean named = false;
                    for (Channel c : all) {
                        if (!c.tvgId.isEmpty()) {
                            String k = c.tvgId.toLowerCase(Locale.ROOT);
                            List<String> l = ids.get(k);
                            if (l == null) ids.put(k, l = new ArrayList<>(1));
                            if (!l.contains(main.key)) l.add(main.key);
                        } else if (!named) {
                            named = true;
                            String k = Names.key(c.name);
                            if (k.isEmpty()) continue;
                            List<String> l = names.get(k);
                            if (l == null) names.put(k, l = new ArrayList<>(1));
                            if (!l.contains(main.key)) l.add(main.key);
                        }
                    }
                }
                Map<String, Guide.Schedule> g = new HashMap<>();
                IOException last = null;
                long now = System.currentTimeMillis();
                Map<String, Long> since = GuideShift.since(since(now), shift);
                for (String url : urls.split(" ")) {
                    if (url.isEmpty()) continue;
                    try (InputStream in = Fetch.open(url, null)) {
                        for (Map.Entry<String, Guide.Schedule> e : Guide.read(in, ids, names, now - shift, since).entrySet()) {
                            if (!g.containsKey(e.getKey())) g.put(e.getKey(), GuideShift.by(e.getValue(), shift));
                        }
                    } catch (IOException e) {
                        last = e;
                    }
                }
                if (g.isEmpty() && last != null) throw last;
                guide = g;
                guideAt = System.currentTimeMillis();
                writeGuide();
                changed();
            } catch (IOException | RuntimeException e) {
                // The channels play without it; tried again at the next refresh. Never the address (it holds the login).
                android.util.Log.w("HomefieldTv", "guide not read: " + e.getClass().getSimpleName());
            } finally {
                guideBusy.set(false);
            }
        });
    }

    /**
     * How far back the guide is kept for each channel with catch-up (by key): its archive, at most Guide.PAST_DAYS;
     * the others keep the last hour (Guide.BEHIND_MS).
     */
    private Map<String, Long> since(long now) {
        Map<String, Long> out = new HashMap<>();
        for (Channel c : channels) {
            Channel s = Catchup.source(c);
            if (s != null) out.put(c.key, now - Math.min(s.archiveDays, Guide.PAST_DAYS) * 86_400_000L);
        }
        return out;
    }

    // ---- the disk copy (the app's private files) --------------------------------------------------------------------

    private File file(String name) {
        File dir = new File(app.getFilesDir(), "live");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, name);
    }

    static String cell(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private void writeCache() {
        File f = file("channels.tsv"), tmp = file("channels.tsv.tmp");
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8), 64 * 1024)) {
            w.write("v2\t" + fingerprint + "\t" + loadedAt + "\t" + cell(guideUrl) + "\n");
            // Each channel with its backups after it: read back, they are made one again (merged).
            for (Channel main : channels) {
                List<Channel> rows = new ArrayList<>(1 + main.alternates.size());
                rows.add(main);
                rows.addAll(main.alternates);
                for (Channel c : rows) {
                    w.write(cell(c.key) + "\t" + cell(c.name) + "\t" + cell(c.group) + "\t" + cell(c.logo) + "\t" + cell(c.url) + "\t"
                            + cell(c.tvgId) + "\t" + cell(c.userAgent) + "\t" + cell(c.referrer) + "\t" + c.number + "\t"
                            + cell(c.catchup) + "\t" + cell(c.catchupSource) + "\t" + c.archiveDays + "\t" + cell(c.zone) + "\n");
                }
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    private void writeGuide() {
        File f = file("guide.tsv"), tmp = file("guide.tsv.tmp");
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8), 64 * 1024)) {
            w.write("v2\t" + fingerprint + "\t" + guideAt + "\n");
            for (Map.Entry<String, Guide.Schedule> e : guide.entrySet()) {
                Guide.Schedule s = e.getValue();
                for (int i = 0; i < s.start.length; i++) {
                    w.write(cell(e.getKey()) + "\t" + s.start[i] + "\t" + s.stop[i] + "\t" + cell(s.title[i]) + "\t"
                            + cell(s.desc[i]) + "\n");
                }
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    private void readCache() {
        if (Sources.inUse(app).isEmpty()) return;
        String fp = Sources.fingerprint(app);
        File f = file("channels.tsv");
        if (!f.exists()) return;
        List<Channel> list = new ArrayList<>();
        String gUrl;
        long at;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8), 64 * 1024)) {
            String head = r.readLine();
            String[] h = head == null ? new String[0] : head.split("\t", -1);
            // v1 (before catch-up): shown at once, and read again from the providers straight away for the archive.
            boolean v1 = h.length >= 3 && "v1".equals(h[0]);
            if (h.length < 3 || !(v1 || "v2".equals(h[0])) || !fp.equals(h[1])) return;
            at = v1 ? 0 : Long.parseLong(h[2]);
            gUrl = h.length > 3 ? h[3] : "";
            for (String line; (line = r.readLine()) != null; ) {
                String[] c = line.split("\t", -1);
                if (c.length < 9) continue;
                if (c.length >= 13) {
                    list.add(new Channel(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], number(c[8]), c[9], c[10], number(c[11]), c[12]));
                } else {
                    list.add(new Channel(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], number(c[8])));
                }
            }
        } catch (IOException | NumberFormatException e) {
            return;
        }
        apply(list, fp, gUrl, at);
        readGuide(fp);
    }

    private void readGuide(String fp) {
        File f = file("guide.tsv");
        if (!f.exists()) return;
        Map<String, Guide.Builder> b = new HashMap<>();
        long at;
        long now = System.currentTimeMillis();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8), 64 * 1024)) {
            String head = r.readLine();
            String[] h = head == null ? new String[0] : head.split("\t", -1);
            if (h.length < 3 || !"v2".equals(h[0]) || !fp.equals(h[1])) return;
            at = Long.parseLong(h[2]);
            Map<String, String> pool = new HashMap<>();
            Map<String, Long> since = since(now);
            for (String line; (line = r.readLine()) != null; ) {
                String[] c = line.split("\t", 5);
                if (c.length < 4) continue;
                long stop = Long.parseLong(c[2]);
                Long from = since.get(c[0]);
                if (stop < (from != null ? from : now - Guide.BEHIND_MS)) continue;
                Guide.Builder bl = b.get(c[0]);
                if (bl == null) b.put(c[0], bl = new Guide.Builder());
                bl.add(Long.parseLong(c[1]), stop, shared(pool, c[3]), c.length > 4 ? shared(pool, c[4]) : "");
            }
        } catch (IOException | NumberFormatException e) {
            return;
        }
        Map<String, Guide.Schedule> g = new HashMap<>();
        for (Map.Entry<String, Guide.Builder> e : b.entrySet()) g.put(e.getKey(), e.getValue().build());
        guide = g;
        guideAt = at;
    }

    private static String shared(Map<String, String> pool, String s) {
        if (s.isEmpty()) return "";
        String had = pool.get(s);
        if (had != null) return had;
        pool.put(s, s);
        return s;
    }

    // ---- listeners ------------------------------------------------------------------------------------------

    public void addListener(Runnable r) { listeners.add(r); }

    public void removeListener(Runnable r) { listeners.remove(r); }

    private void changed() {
        for (Runnable r : listeners) Exec.main(r);
    }
}
