package com.gathin.tvhomephone.live;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;

import com.gathin.tvhomephone.App;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live TV's channels and guide from the family's own source: read from the provider off the main thread, kept on disk in
 * the app's private files (drawn at once next time), the channels again after a day and the guide after six hours. Holds
 * the index that finds a broadcaster's channel by name (LiveLinks), and the guide's search index (ProgrammeSearch).
 * Channels hidden on this phone (Hidden) are left out of every list, search and Similar channels it gives.
 */
public final class Library {
    public static final String ALL = "", FAVOURITES = "★", RECENT = "↺";
    static final long REFRESH_MS = 24 * 3600_000L, GUIDE_MS = 6 * 3600_000L;
    private static Library instance;

    private final Context app;
    private volatile List<Channel> channels = Collections.emptyList();
    private volatile String[] lower = new String[0];
    private volatile List<String> groups = Collections.emptyList();
    private volatile Map<String, Integer> byKey = Collections.emptyMap();
    private volatile Map<String, int[]> byName = Collections.emptyMap();
    private volatile Names.Info[] infos = new Names.Info[0];
    /** Each channel's lasting key for Hidden, and whether it is hidden (both line up with channels). */
    private volatile String[] hideKeys = new String[0];
    private volatile boolean[] hidden = new boolean[0];
    private volatile int visible;
    private volatile Map<String, Guide.Schedule> guide = Collections.emptyMap();
    private volatile ProgrammeSearch.Index programmes = ProgrammeSearch.Index.EMPTY;
    private volatile String fingerprint = "", guideUrl = "", status = "";
    private volatile long loadedAt, guideAt;
    private volatile boolean read;
    private volatile Set<String> fav;
    private final AtomicBoolean busy = new AtomicBoolean(), guideBusy = new AtomicBoolean();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> matches = new ConcurrentHashMap<>();

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

    public boolean hasSource() { return Source.load(app) != null; }
    public boolean ready() { return !channels.isEmpty(); }
    public boolean busy() { return busy.get(); }
    public String status() { return status; }
    public long loadedAt() { return loadedAt; }
    public int count() { return channels.size(); }
    /** The channels not hidden. */
    public int visibleCount() { return visible; }
    public int guideCount() { return guide.size(); }
    public List<String> groups() { return groups; }

    public Channel byKey(String key) {
        Integer i = key == null ? null : byKey.get(key);
        List<Channel> ch = channels;
        return i == null || i >= ch.size() ? null : ch.get(i);
    }

    public Guide.Schedule schedule(String key) { return guide.get(key); }

    /** Whether the channel is hidden on this phone. */
    public boolean isHidden(Channel c) {
        return c != null && hiddenAt(position(c.key));
    }

    /** The playlist's hidden channels, by their Hidden key. */
    Map<String, Channel> hiddenChannels() {
        List<Channel> ch = channels;
        boolean[] m = hidden;
        String[] k = hideKeys;
        Map<String, Channel> out = new HashMap<>();
        for (int i = 0; i < Math.min(ch.size(), Math.min(m.length, k.length)); i++) if (m[i]) out.put(k[i], ch.get(i));
        return out;
    }

    private boolean hiddenAt(int i) {
        boolean[] m = hidden;
        return i >= 0 && i < m.length && m[i];
    }

    /** The channel's place in the playlist (0 first), or -1. */
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

    /** The earliest start and latest end the guide has, or {0, 0} with no guide. */
    public long[] guideSpan() {
        ProgrammeSearch.Index ix = programmes;
        return new long[]{ix.from, ix.to};
    }

    /** Programmes whose title (or description) has the search in it, best first (none on hidden channels). Off the main thread. */
    public List<ProgrammeSearch.Hit> searchProgrammes(String query, long now, int limit) {
        return ProgrammeSearch.search(programmes, query, now, limit, hidden);
    }

    /** Channels like this one (its family, then its group) for Similar channels; none that are hidden. */
    public List<Channel> similar(Channel c, int limit) {
        List<Channel> ch = channels;
        List<Channel> out = new ArrayList<>();
        int self = position(c.key);
        if (self < 0 || self >= ch.size()) return out;
        for (int i : Similar.pick(ch, infos, self, limit + 16)) {
            if (hiddenAt(i)) continue;
            out.add(ch.get(i));
            if (out.size() >= limit) break;
        }
        return out;
    }

    /** A group's channels (ALL, FAVOURITES, RECENT or a group's name), narrowed by a search; hidden ones left out. */
    public List<Channel> list(String group, String query) {
        List<Channel> ch = channels;
        String[] low = lower;
        boolean[] hid = hidden;
        String q = query == null ? "" : Names.plain(query).toLowerCase(Locale.ROOT).trim();
        List<Channel> out = new ArrayList<>();
        if (RECENT.equals(group)) {
            for (String k : Picks.recent(app)) {
                Channel c = byKey(k);
                if (c == null || isHidden(c)) continue;
                if (q.isEmpty() || Names.plain(c.name).toLowerCase(Locale.ROOT).contains(q)) out.add(c);
            }
            return out;
        }
        Set<String> f = FAVOURITES.equals(group) ? favourites() : null;
        if (f != null) {
            // Your channels in their own order (any not placed yet after, in the playlist's order).
            List<Channel> placed = new ArrayList<>();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (String k : Picks.order(app)) {
                Channel c = byKey(k);
                if (c != null && seen.add(k)) placed.add(c);
            }
            for (Channel c : ch) if (f.contains(c.key) && !seen.contains(c.key)) placed.add(c);
            for (Channel c : placed) {
                if (isHidden(c)) continue;
                if (q.isEmpty() || Names.plain(c.name).toLowerCase(Locale.ROOT).contains(q)) out.add(c);
            }
            return out;
        }
        for (int i = 0; i < ch.size(); i++) {
            Channel c = ch.get(i);
            if (i < hid.length && hid[i]) continue;
            if (f != null && !f.contains(c.key)) continue;
            if (f == null && group != null && !group.isEmpty() && !group.equals(c.group)) continue;
            if (!q.isEmpty() && (i >= low.length || !low[i].contains(q))) continue;
            out.add(c);
        }
        return out;
    }

    // ---- finding a broadcaster's channel ----------------------------------------------------------------------------

    /** The playlist's channel for a listing's broadcaster ("Sky Sports Main Event / Sky Sports Premier League"), or null. */
    public Channel match(String listing) {
        if (listing == null || channels.isEmpty()) return null;
        Integer had = matches.get(listing);
        if (had != null) return had < 0 ? null : channels.get(had);
        int found = -1;
        for (String part : Names.parts(listing)) {
            if (Names.isService(part)) continue;
            int[] idx = byName.get(Names.info(part, null).key);
            if (idx == null || idx.length == 0) continue;
            found = best(idx);
            break;
        }
        matches.put(listing, found);
        return found < 0 ? null : channels.get(found);
    }

    /** Of the channels sharing a name: not hidden, a favourite, then tagged British, the best picture, not a backup. */
    private int best(int[] idx) {
        Set<String> f = favourites();
        Names.Info[] in = infos;
        List<Channel> ch = channels;
        boolean[] hid = hidden;
        int best = idx[0];
        for (int i : idx) if (better(i, best, f, in, ch, hid)) best = i;
        return best;
    }

    private static boolean better(int a, int b, Set<String> f, Names.Info[] in, List<Channel> ch, boolean[] hid) {
        boolean ha = a < hid.length && hid[a], hb = b < hid.length && hid[b];
        if (ha != hb) return !ha;
        boolean fa = f.contains(ch.get(a).key), fb = f.contains(ch.get(b).key);
        if (fa != fb) return fa;
        if (in[a].uk != in[b].uk) return in[a].uk;
        if (in[a].quality != in[b].quality) return in[a].quality > in[b].quality;
        if (in[a].backup != in[b].backup) return !in[a].backup;
        return a < b;
    }

    Set<String> favourites() {
        Set<String> f = fav;
        if (f == null) fav = f = Picks.favourites(app);
        return f;
    }

    void picksChanged() {
        fav = null;
        matches.clear();
        changed();
    }

    /** Channels were hidden or unhidden (Hidden): the lists again. */
    void hiddenChanged() {
        remask();
        matches.clear();
        changed();
    }

    private synchronized void remask() {
        boolean[] m = Hidden.mask(hideKeys, Hidden.keys(app));
        int n = 0;
        for (boolean b : m) if (!b) n++;
        hidden = m;
        visible = n;
    }

    // ---- loading ----------------------------------------------------------------------------------------------

    /** The cached channels read if they haven't been, then a refresh when they or the guide are old. Any thread. */
    public void ensure() {
        if (read) {
            maybeRefresh();
            return;
        }
        App.io.execute(() -> {
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

    /** As ensure(), the phone's copy read now (off the main thread): for an alert's Watch while the app is closed. */
    public void ensureNow() {
        boolean was;
        synchronized (this) {
            was = read;
            if (!read) {
                readCache();
                read = true;
            }
        }
        if (!was) changed();
    }

    private void maybeRefresh() {
        Source s = Source.load(app);
        if (s == null) return;
        long now = System.currentTimeMillis();
        if (channels.isEmpty() || now - loadedAt > REFRESH_MS || !s.fingerprint().equals(fingerprint)) refresh(null);
        else if (!guideUrl.isEmpty() && now - guideAt > GUIDE_MS) refreshGuide();
    }

    /** The channels read again from the provider now (then the guide). */
    public void refresh(Callback cb) {
        Source s = Source.load(app);
        if (s == null) {
            if (cb != null) App.main.post(() -> cb.done(0, "No playlist yet"));
            return;
        }
        load(s, false, cb);
    }

    /** A source tried out: saved and used only when it gives live channels. */
    public void test(Source s, Callback cb) {
        load(s, true, cb);
    }

    private void load(Source s, boolean saveFirst, Callback cb) {
        if (!busy.compareAndSet(false, true)) {
            if (cb != null) App.main.post(() -> cb.done(0, "Already loading, a moment"));
            return;
        }
        status = "";
        changed();
        App.io.execute(() -> {
            int n = 0;
            String error = null;
            try {
                Loaded l = fetch(s);
                if (l.channels.isEmpty()) throw new IOException("No live channels in it");
                if (saveFirst) {
                    s.save(app);
                    clearGuide();
                }
                apply(l.channels, s.fingerprint(), l.guideUrl, System.currentTimeMillis());
                reindex();
                writeCache();
                n = l.channels.size();
            } catch (IOException | RuntimeException e) {
                error = e instanceof IOException && e.getMessage() != null ? e.getMessage() : "That didn't work";
                status = error;
            } finally {
                busy.set(false);
            }
            int count = n;
            String err = error;
            changed();
            if (cb != null) App.main.post(() -> cb.done(count, err));
            if (err == null) refreshGuide();
        });
    }

    /** Everything removed from this phone: the source, the channels, the guide, the favourites, the hidden channels. */
    public void clear() {
        Source.clear(app);
        Picks.clear(app);
        Hidden.clear(app);
        Playback.get(app).forget();
        channels = Collections.emptyList();
        lower = new String[0];
        groups = Collections.emptyList();
        byKey = Collections.emptyMap();
        byName = Collections.emptyMap();
        infos = new Names.Info[0];
        hideKeys = new String[0];
        hidden = new boolean[0];
        visible = 0;
        guide = Collections.emptyMap();
        programmes = ProgrammeSearch.Index.EMPTY;
        fingerprint = "";
        guideUrl = "";
        status = "";
        loadedAt = 0;
        guideAt = 0;
        matches.clear();
        fav = null;
        //noinspection ResultOfMethodCallIgnored
        file("channels.tsv").delete();
        clearGuide();
        changed();
    }

    private void clearGuide() {
        guide = Collections.emptyMap();
        programmes = ProgrammeSearch.Index.EMPTY;
        guideAt = 0;
        //noinspection ResultOfMethodCallIgnored
        file("guide.tsv").delete();
    }

    private static final class Loaded {
        final List<Channel> channels;
        final String guideUrl;

        Loaded(List<Channel> channels, String guideUrl) {
            this.channels = channels;
            this.guideUrl = guideUrl == null ? "" : guideUrl;
        }
    }

    private Loaded fetch(Source s) throws IOException {
        if (Source.M3U.equals(s.type)) {
            try (InputStream in = Fetch.open(s.m3uUrl, null); Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                M3u.Result res = M3u.parse(r);
                return new Loaded(res.channels, s.guideUrl.isEmpty() ? res.guideUrl : s.guideUrl);
            }
        }
        return xtream(s);
    }

    /** An Xtream Codes account: the login checked, its live categories, then its live streams read as a stream. */
    private Loaded xtream(Source s) throws IOException {
        String base = Xtream.base(s.server);
        if (base.isEmpty()) throw new IOException("Add the server's address");
        String ext;
        try {
            JSONObject acct = new JSONObject(text(Xtream.api(base, s.user, s.pass, null)));
            JSONObject ui = acct.optJSONObject("user_info");
            if (ui == null || !"1".equals(ui.optString("auth"))) throw new IOException("The login wasn't accepted");
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
                String id = "", name = "", icon = "", epg = "", cat = "", type = "live";
                int num = 0;
                jr.beginObject();
                while (jr.hasNext()) {
                    String k = jr.nextName();
                    String v = value(jr);
                    switch (k) {
                        case "stream_id": id = v; break;
                        case "name": name = v; break;
                        case "stream_icon": icon = v; break;
                        case "epg_channel_id": epg = v; break;
                        case "category_id": cat = v; break;
                        case "stream_type": type = v; break;
                        case "num": num = number(v); break;
                        default: break;
                    }
                }
                jr.endObject();
                if (id.isEmpty() || (!type.isEmpty() && !type.equalsIgnoreCase("live") && !type.equalsIgnoreCase("created_live"))) continue;
                String group = cats.get(cat);
                out.add(new Channel("x:" + id, name, group, icon, Xtream.stream(base, s.user, s.pass, id, ext), epg, null, null, num));
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

    private static String value(JsonReader jr) throws IOException {
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

    private static String text(String url) throws IOException {
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

    private static int number(String s) {
        try {
            return s == null || s.isEmpty() ? 0 : (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** New channels in place: the groups in first-seen order, the key and name indexes built once. */
    private synchronized void apply(List<Channel> list, String fp, String gUrl, long at) {
        List<Channel> ch = Collections.unmodifiableList(new ArrayList<>(list));
        String[] low = new String[ch.size()];
        String[] hk = new String[ch.size()];
        Names.Info[] in = new Names.Info[ch.size()];
        Map<String, Integer> keys = new HashMap<>(ch.size() * 2);
        Map<String, List<Integer>> names = new HashMap<>(ch.size() * 2);
        LinkedHashMap<String, Boolean> gs = new LinkedHashMap<>();
        for (int i = 0; i < ch.size(); i++) {
            Channel c = ch.get(i);
            low[i] = Names.plain(c.name).toLowerCase(Locale.ROOT);
            in[i] = Names.info(c.name, c.group);
            hk[i] = Hidden.key(c);
            keys.put(c.key, i);
            if (!in[i].key.isEmpty()) {
                List<Integer> l = names.get(in[i].key);
                if (l == null) names.put(in[i].key, l = new ArrayList<>(2));
                l.add(i);
            }
            if (!c.group.isEmpty()) gs.put(c.group, Boolean.TRUE);
        }
        Map<String, int[]> idx = new HashMap<>(names.size() * 2);
        for (Map.Entry<String, List<Integer>> e : names.entrySet()) {
            int[] a = new int[e.getValue().size()];
            for (int i = 0; i < a.length; i++) a[i] = e.getValue().get(i);
            idx.put(e.getKey(), a);
        }
        lower = low;
        infos = in;
        hideKeys = hk;
        boolean[] m = Hidden.mask(hk, Hidden.keys(app));
        int vis = 0;
        for (boolean b : m) if (!b) vis++;
        hidden = m;
        visible = vis;
        byKey = keys;
        byName = idx;
        groups = Collections.unmodifiableList(new ArrayList<>(gs.keySet()));
        channels = ch;
        fingerprint = fp;
        guideUrl = gUrl == null ? "" : gUrl;
        loadedAt = at;
        matches.clear();
    }

    /** The guide read again (in the background, after the channels): only the playlist's channels, a 13 hour window. */
    void refreshGuide() {
        String url = guideUrl;
        if (url.isEmpty() || channels.isEmpty() || !guideBusy.compareAndSet(false, true)) return;
        App.io.execute(() -> {
            try {
                Map<String, List<String>> ids = new HashMap<>(), names = new HashMap<>();
                for (Channel c : channels) {
                    if (!c.tvgId.isEmpty()) {
                        String k = c.tvgId.toLowerCase(Locale.ROOT);
                        List<String> l = ids.get(k);
                        if (l == null) ids.put(k, l = new ArrayList<>(1));
                        l.add(c.key);
                    } else {
                        String k = Names.key(c.name);
                        if (k.isEmpty()) continue;
                        List<String> l = names.get(k);
                        if (l == null) names.put(k, l = new ArrayList<>(1));
                        l.add(c.key);
                    }
                }
                Map<String, Guide.Schedule> g;
                try (InputStream in = Fetch.open(url, null)) {
                    g = Guide.read(in, ids, names, System.currentTimeMillis());
                }
                guide = g;
                guideAt = System.currentTimeMillis();
                reindex();
                writeGuide();
                changed();
            } catch (IOException | RuntimeException e) {
                // The channels play without it; tried again at the next refresh.
                android.util.Log.w("LiveTv", "guide not read: " + e.getClass().getSimpleName());
            } finally {
                guideBusy.set(false);
            }
        });
    }

    // ---- the disk copy (the app's private files) --------------------------------------------------------------------

    private File file(String name) {
        File dir = new File(app.getFilesDir(), "live");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, name);
    }

    private static String cell(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private void writeCache() {
        File f = file("channels.tsv"), tmp = file("channels.tsv.tmp");
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8), 64 * 1024)) {
            w.write("v1\t" + fingerprint + "\t" + loadedAt + "\t" + cell(guideUrl) + "\n");
            for (Channel c : channels) {
                w.write(cell(c.key) + "\t" + cell(c.name) + "\t" + cell(c.group) + "\t" + cell(c.logo) + "\t" + cell(c.url) + "\t"
                        + cell(c.tvgId) + "\t" + cell(c.userAgent) + "\t" + cell(c.referrer) + "\t" + c.number + "\n");
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
        Source s = Source.load(app);
        if (s == null) return;
        String fp = s.fingerprint();
        File f = file("channels.tsv");
        if (!f.exists()) return;
        List<Channel> list = new ArrayList<>();
        String gUrl = "";
        long at = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8), 64 * 1024)) {
            String head = r.readLine();
            String[] h = head == null ? new String[0] : head.split("\t", -1);
            if (h.length < 3 || !"v1".equals(h[0]) || !fp.equals(h[1])) return;
            at = Long.parseLong(h[2]);
            gUrl = h.length > 3 ? h[3] : "";
            for (String line; (line = r.readLine()) != null; ) {
                String[] c = line.split("\t", -1);
                if (c.length < 9) continue;
                list.add(new Channel(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], number(c[8])));
            }
        } catch (IOException | NumberFormatException e) {
            return;
        }
        apply(list, fp, gUrl, at);
        readGuide(fp);
        reindex();
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
            // v1 had no descriptions.
            boolean v2 = h.length >= 3 && "v2".equals(h[0]);
            if (h.length < 3 || !(v2 || "v1".equals(h[0])) || !fp.equals(h[1])) return;
            at = Long.parseLong(h[2]);
            Map<String, String> pool = new HashMap<>();
            for (String line; (line = r.readLine()) != null; ) {
                String[] c = line.split("\t", v2 ? 5 : 4);
                if (c.length < 4) continue;
                long stop = Long.parseLong(c[2]);
                if (stop < now - Guide.BEHIND_MS) continue;
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

    /** The guide's search index for the channels as they are now (after either changes). */
    private synchronized void reindex() {
        List<Channel> ch = channels;
        Map<String, Guide.Schedule> g = guide;
        Guide.Schedule[] s = new Guide.Schedule[ch.size()];
        if (!g.isEmpty()) for (int i = 0; i < s.length; i++) s[i] = g.get(ch.get(i).key);
        programmes = new ProgrammeSearch.Index(ch, s);
    }

    // ---- listeners ------------------------------------------------------------------------------------------

    public void addListener(Runnable r) { listeners.add(r); }

    public void removeListener(Runnable r) { listeners.remove(r); }

    private void changed() {
        for (Runnable r : listeners) App.main.post(r);
    }
}
