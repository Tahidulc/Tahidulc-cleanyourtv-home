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
    /** Hidden by the channel itself (Hidden), not by its category (Groups): for Favourites and the Hidden sheet. */
    private volatile boolean[] ownHidden = new boolean[0];
    private volatile int visible;
    /**
     * The channels as the playlists give them (each the same in several playlists made one), before the family's own
     * names and logos (ChannelEdits): channels is made from it, again whenever those change.
     */
    private volatile List<Channel> plain = Collections.emptyList();
    /** One row per channel: each channel's versions by place, best first (null for one in a single version, or off). */
    private volatile int[][] families = new int[0][];
    /** Whether each channel is its channel's row in the lists (the version picked, else the best one shown). */
    private volatile boolean[] rows = new boolean[0];
    /** Each channel's name as By name sorts it: plain and lower case. */
    private volatile String[] sortNames = new String[0];
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

    public boolean hasSource() { return !Sources.list(app).isEmpty(); }
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

    /**
     * A channel by its key, or the channel it was merged into when the key is one of the other playlists' copies of it
     * (merged: its alternates), as a recent channel kept on another device can be; null when the playlists have neither.
     */
    public Channel resolve(String key) {
        Channel c = byKey(key);
        if (c != null || key == null) return rowOf(c);
        List<Channel> ch = channels;
        for (Channel m : ch) {
            if (m.alternates.isEmpty()) continue;
            for (Channel a : m.alternates) if (key.equals(a.key)) return rowOf(m);
        }
        return null;
    }

    // ---- one row per channel (Versions, ChannelEdits) ------------------------------------------------------------

    /** The versions of a channel shown as one row (its places, best first), or null: one version, or the setting off. */
    private int[] familyAt(int i) {
        int[][] f = families;
        return i >= 0 && i < f.length ? f[i] : null;
    }

    /** The row a channel is shown as: itself, or the version of its channel the lists show (one row per channel). */
    public Channel rowOf(Channel c) {
        if (c == null) return null;
        int[] fam = familyAt(position(c.key));
        if (fam == null) return c;
        boolean[] r = rows;
        List<Channel> ch = channels;
        for (int p : fam) if (p < r.length && r[p] && p < ch.size()) return ch.get(p);
        return c;
    }

    /** A channel's versions, best first, as its Quality lists them (the channel alone when it comes in one). */
    public List<Channel> versions(Channel c) {
        List<Channel> out = new ArrayList<>();
        if (c == null) return out;
        int[] fam = familyAt(position(c.key));
        List<Channel> ch = channels;
        if (fam == null) {
            out.add(c);
            return out;
        }
        for (int p : fam) if (p < ch.size()) out.add(ch.get(p));
        return out;
    }

    /** The keys of a channel's versions (its own first), for what is kept for the channel as a whole. */
    public List<String> familyKeys(String key) {
        List<String> out = new ArrayList<>(3);
        if (key == null) return out;
        out.add(key);
        int[] fam = familyAt(position(key));
        List<Channel> ch = channels;
        if (fam != null) for (int p : fam) if (p < ch.size() && !ch.get(p).key.equals(key)) out.add(ch.get(p).key);
        return out;
    }

    /** The name a channel shows without one of the family's: its channel's without the quality (one row), else the playlist's. */
    public String defaultName(Channel c) {
        int[] fam = familyAt(position(c.key));
        List<Channel> ch = channels;
        return fam != null && fam[0] < ch.size() ? Versions.title(ch.get(fam[0]).playlistName) : c.playlistName;
    }

    /** Whether the family gave the channel a name of their own (Rename channel). */
    public boolean renamed(Channel c) {
        return c != null && ChannelEdits.name(app, familyKeys(c.key)) != null;
    }

    /** The channels the family renamed, as the lists show them (each once), by name. */
    List<Channel> renamedChannels() {
        List<Channel> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String k : ChannelEdits.state(app).names.keySet()) {
            Channel c = rowOf(byKey(k));
            if (c != null && seen.add(c.key)) out.add(c);
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    /** Whether the version is its channel's row now (shown and played: the one picked, else the best shown). */
    private boolean rowAt(int i) {
        boolean[] r = rows;
        return i < 0 || i >= r.length || r[i];
    }

    public Guide.Schedule schedule(String key) { return guide.get(key); }

    /** Whether the channel is hidden on this phone. */
    public boolean isHidden(Channel c) {
        return c != null && hiddenAt(position(c.key));
    }

    /** The playlist's hidden channels, by their Hidden key. */
    Map<String, Channel> hiddenChannels() {
        List<Channel> ch = channels;
        boolean[] m = ownHidden;
        String[] k = hideKeys;
        Map<String, Channel> out = new HashMap<>();
        for (int i = 0; i < Math.min(ch.size(), Math.min(m.length, k.length)); i++) if (m[i]) out.put(k[i], ch.get(i));
        return out;
    }

    private boolean hiddenAt(int i) {
        boolean[] m = hidden;
        return i >= 0 && i < m.length && m[i];
    }

    /** Whether the channel itself is hidden (a channel in a hidden category isn't, for Favourites and Recent). */
    private boolean ownHiddenAt(Channel c) {
        int i = c == null ? -1 : position(c.key);
        boolean[] m = ownHidden;
        return i >= 0 && i < m.length && m[i];
    }

    /** How many channels a category has (all of them, hidden or not). */
    public int groupSize(String group) {
        int n = 0;
        for (Channel c : channels) if (c.group.equals(group)) n++;
        return n;
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
            // Newest first, each merged channel once (Recents), whichever playlist's copy was the one watched.
            for (Channel c : Recents.list(Picks.recent(app), this::resolve, this::ownHiddenAt, null, Integer.MAX_VALUE)) {
                if (q.isEmpty() || Names.plain(c.name).toLowerCase(Locale.ROOT).contains(q)) out.add(c);
            }
            return out;
        }
        Set<String> f = FAVOURITES.equals(group) ? favourites() : null;
        if (f != null) {
            // Your channels in their own order (any not placed yet after, in the playlist's order), each channel shown
            // as one row once (a favourite on any of its versions is the channel's).
            List<Channel> placed = new ArrayList<>();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (String k : Picks.order(app)) {
                Channel c = byKey(k);
                if (c != null && seen.add(k)) placed.add(c);
            }
            for (Channel c : ch) if (f.contains(c.key) && !seen.contains(c.key)) placed.add(c);
            java.util.HashSet<String> rowsSeen = new java.util.HashSet<>();
            for (Channel c : placed) {
                if (ownHiddenAt(c)) continue;
                Channel r = rowOf(c);
                if (r != c && ownHiddenAt(r) || !rowsSeen.add(r.key)) continue;
                if (q.isEmpty() || Names.plain(r.name).toLowerCase(Locale.ROOT).contains(q)) out.add(r);
            }
            return out;
        }
        for (int at : rowPlaces(ch.size(), families, rows, hid)) {
            Channel c = ch.get(at);
            if (group != null && !group.isEmpty() && !group.equals(c.group)) continue;
            if (!q.isEmpty() && (at >= low.length || !low[at].contains(q))) continue;
            out.add(c);
        }
        return sorted(out, group);
    }

    /**
     * A category's channels (or all of them) in the order its Sort channels says, else Channels sorting's: as the
     * playlist has them, by name, by number, or the family's own (Move channel; All channels: each category's, the
     * categories as arranged).
     */
    private List<Channel> sorted(List<Channel> out, String group) {
        boolean all = group == null || group.isEmpty();
        int own = all ? -1 : Groups.sortOf(group);
        int mode = own >= 0 ? own : ChannelEdits.sort(app);
        if (mode == ChannelEdits.PLAYLIST || out.size() < 2) return out;
        List<Channel> channels = this.channels;
        String[] names = sortNames;
        int n = channels.size();
        if (names.length < n) return out;
        Integer[] places = new Integer[out.size()];
        for (int i = 0; i < places.length; i++) {
            places[i] = position(out.get(i).key);
            // Made again meanwhile: as they are.
            if (places[i] < 0 || places[i] >= n || channels.get(places[i]) != out.get(i)) return out;
        }
        int[] numbers = null, manual = null;
        if (mode == ChannelEdits.NUMBER) {
            numbers = new int[n];
            for (Integer p : places) if (p >= 0 && p < n) numbers[p] = number(channels.get(p));
        } else if (mode == ChannelEdits.MANUAL) {
            manual = new int[n];
            java.util.Map<String, Integer> rank = new HashMap<>();
            if (all) {
                List<String> arranged = Groups.arranged(groups);
                for (int i = 0; i < arranged.size(); i++) rank.put(arranged.get(i), i);
            }
            java.util.Map<String, java.util.Map<String, Integer>> at = new HashMap<>();
            for (Integer p : places) {
                if (p < 0 || p >= n) continue;
                Channel c = channels.get(p);
                java.util.Map<String, Integer> o = at.get(c.group);
                if (o == null) {
                    o = new HashMap<>();
                    List<String> keys = ChannelEdits.order(app, c.group);
                    for (int i = 0; i < keys.size(); i++) o.put(keys.get(i), i);
                    at.put(c.group, o);
                }
                int place = 99_999;
                for (String k : familyKeys(c.key)) {
                    Integer i = o.get(k);
                    if (i != null) place = Math.min(place, i);
                }
                Integer r = rank.get(c.group);
                manual[p] = (all ? (r == null ? 9_999 : r) : 0) * 100_000 + place;
            }
        }
        Integer[] order = ChannelEdits.sorted(places, mode, names, numbers, manual);
        List<Channel> s = new ArrayList<>(order.length);
        for (Integer p : order) s.add(channels.get(p));
        return s;
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

    /**
     * For a listing's broadcaster, the playlist's channel at HD or less and its 4K/UHD one, each the best of its kind
     * (best()), either null: so "Also Showing On" lists the 4K channel beside the HD one, not only one of them.
     */
    public Channel[] tiers(String listing) {
        Channel[] out = new Channel[2];
        if (listing == null || channels.isEmpty()) return out;
        for (String part : Names.parts(listing)) {
            if (Names.isService(part)) continue;
            int[] idx = byName.get(Names.info(part, null).key);
            if (idx == null || idx.length == 0) continue;
            Names.Info[] in = infos;
            boolean[] hid = hidden;
            java.util.List<Integer> plain = new java.util.ArrayList<>(), uhd = new java.util.ArrayList<>();
            for (int i : idx) {
                if (i < hid.length && hid[i]) continue;
                (i < in.length && in[i].quality >= 4 ? uhd : plain).add(i);
            }
            if (!plain.isEmpty()) out[0] = channels.get(best(toInts(plain)));
            if (!uhd.isEmpty()) out[1] = channels.get(best(toInts(uhd)));
            if (out[0] != null || out[1] != null) break;
        }
        return out;
    }

    /**
     * A channel's versions in each picture quality, best first (for adaptive and fixed quality): the channels with its
     * name, its +1 and its backup tag, one for each quality (the chosen one for its own; otherwise not hidden and the
     * best of the rest, best()). Always holds the channel itself.
     */
    public List<Channel> variants(Channel ch) {
        List<Channel> out = new ArrayList<>();
        if (ch == null) return out;
        Names.Info mine = Names.info(ch.playlistName, ch.group);
        int[] idx = mine.key.isEmpty() ? null : byName.get(mine.key);
        Names.Info[] in = infos;
        boolean[] hid = hidden;
        java.util.Map<Integer, java.util.List<Integer>> byQuality = new java.util.TreeMap<>(java.util.Collections.reverseOrder());
        if (idx != null) {
            for (int i : idx) {
                if (i >= in.length || i >= channels.size()) continue;
                Names.Info n = in[i];
                if (n.backup != mine.backup || n.quality == mine.quality) continue;
                if (i < hid.length && hid[i]) continue;
                java.util.List<Integer> l = byQuality.get(n.quality);
                if (l == null) byQuality.put(n.quality, l = new java.util.ArrayList<>(2));
                l.add(i);
            }
        }
        boolean placed = false;
        for (java.util.Map.Entry<Integer, java.util.List<Integer>> e : byQuality.entrySet()) {
            if (!placed && e.getKey() < mine.quality) {
                out.add(ch);
                placed = true;
            }
            out.add(channels.get(best(toInts(e.getValue()))));
        }
        if (!placed) out.add(ch);
        return out;
    }

    /** A channel's picture quality as Names reads it from the playlist's name (4 UHD … 0 SD). */
    static int level(Channel ch) {
        return ch == null ? 1 : Names.info(ch.playlistName, ch.group).quality;
    }

    private static int[] toInts(java.util.List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
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
        com.gathin.tvhomephone.sync.SyncStartup.soon();
    }

    /** Categories were hidden, shown or starred (Groups): the lists and the chips again. */
    public void groupsChanged() {
        remask();
        matches.clear();
        changed();
        com.gathin.tvhomephone.sync.SyncStartup.soon();
    }

    /** A channel's logo picked or let go (ChannelLogos): the channels dressed again with it, off the main thread. */
    void logosChanged() {
        if (plain.isEmpty()) return;
        App.io.execute(() -> {
            build();
            changed();
        });
    }

    /** Channels were hidden or unhidden (Hidden): the lists again. */
    void hiddenChanged() {
        remask();
        matches.clear();
        changed();
        com.gathin.tvhomephone.sync.SyncStartup.soon();
    }

    private synchronized void remask() {
        boolean[] own = Hidden.mask(hideKeys, Hidden.keys(app));
        boolean[] m = withGroups(own, channels, Groups.hidden(app));
        boolean[] r = rows(channels, families, m, ChannelEdits.state(app).picks);
        int n = 0;
        for (int i = 0; i < m.length; i++) if (!m[i] && (i >= r.length || r[i])) n++;
        ownHidden = own;
        hidden = m;
        rows = r;
        visible = n;
    }

    /** The Quality picked for a channel (ChannelEdits): its row again, the lists drawn again. */
    void picksOfQualityChanged() {
        remask();
        matches.clear();
        changed();
    }

    /** The family's names, logos or one row per channel changed (ChannelEdits): the channels made again from plain. */
    void editsChanged() {
        App.io.execute(() -> {
            build();
            reindex();
            matches.clear();
            changed();
        });
    }

    /** Channels sorting, a category's Sort channels, or a channel moved: the lists again. */
    void orderChanged() {
        changed();
    }

    /**
     * Pure: which channels are their channel's row (one row per channel): one in a single version always; of a channel's
     * versions, the one picked (picks: a version's key to the picked one's key) when it isn't hidden, else the best one
     * that isn't. hidden lines up with channels.
     */
    static boolean[] rows(List<Channel> ch, int[][] families, boolean[] hidden, Map<String, String> picks) {
        int n = ch.size();
        boolean[] r = new boolean[n];
        java.util.Arrays.fill(r, true);
        for (int i = 0; i < Math.min(n, families.length); i++) {
            int[] fam = families[i];
            // Each channel once: from the first of its versions met (its versions share one array).
            if (fam == null || min(fam) != i) continue;
            int pick = -1;
            for (int p : fam) {
                r[p] = false;
                String k = picks.get(ch.get(p).key);
                if (k == null || pick >= 0) continue;
                for (int q : fam) if (ch.get(q).key.equals(k) && !(q < hidden.length && hidden[q])) pick = q;
            }
            if (pick < 0) for (int p : fam) if (!(p < hidden.length && hidden[p])) { pick = p; break; }
            if (pick >= 0) r[pick] = true;
        }
        return r;
    }

    /**
     * The places of the channels to list, in the playlist's order: a channel in several qualities (one row) stands where
     * the first of its versions stands, whichever version is its row (the best, or the one picked) — so merging never
     * moves a channel down the list (5.33's mistake: the row stood where its best version was). Hidden ones left out.
     */
    static int[] rowPlaces(int n, int[][] families, boolean[] rows, boolean[] hidden) {
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            int at = i;
            int[] fam = i < families.length ? families[i] : null;
            if (fam != null && fam.length > 1) {
                if (min(fam) != i) continue;
                at = -1;
                for (int p : fam) {
                    boolean row = p >= rows.length || rows[p], hid = p < hidden.length && hidden[p];
                    if (row && !hid) { at = p; break; }
                }
                if (at < 0) continue;
            } else if ((i < rows.length && !rows[i]) || (i < hidden.length && hidden[i])) {
                continue;
            }
            out[k++] = at;
        }
        return java.util.Arrays.copyOf(out, k);
    }

    private static int min(int[] a) {
        int m = Integer.MAX_VALUE;
        for (int x : a) m = Math.min(m, x);
        return m;
    }

    /** Pure: the hidden channels and, besides, those in a hidden category. */
    static boolean[] withGroups(boolean[] own, List<Channel> ch, Set<String> hiddenGroups) {
        boolean[] m = own.clone();
        if (hiddenGroups.isEmpty()) return m;
        for (int i = 0; i < Math.min(m.length, ch.size()); i++) if (hiddenGroups.contains(ch.get(i).group)) m[i] = true;
        return m;
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
        if (Sources.inUse(app).isEmpty()) return;
        long now = System.currentTimeMillis();
        if (channels.isEmpty() || now - loadedAt > REFRESH_MS || !Sources.fingerprint(app).equals(fingerprint)) refresh(null);
        else if (!guideUrl.isEmpty() && now - guideAt > GUIDE_MS) refreshGuide();
    }

    /** The channels of the playlists in use read again from their providers now (then the guides). */
    public void refresh(Callback cb) {
        if (Sources.inUse(app).isEmpty()) {
            if (cb != null) App.main.post(() -> cb.done(0, "No playlist yet"));
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

    /** The playlists in use now changed (Live TV's chip, Settings): their channels read again. */
    public void switched() {
        matches.clear();
        load(null, null, null, null);
    }

    private void load(String trialId, String trialName, Source trial, Callback cb) {
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
                String tried = null;
                Loaded first = null;
                if (trial != null) {
                    // Checked on its own first: kept only when it gives live channels.
                    String probeId = trialId.isEmpty() ? "new" : trialId;
                    first = fetch(trial, probeId);
                    if (first.channels.isEmpty()) throw new IOException("No live channels in it");
                    tried = Sources.put(app, trialId, trialName, trial).id;
                    if (trialId.isEmpty()) {
                        Vod.moved(app, "new", tried);
                        Sources.moved(app, "new", tried);
                        // A playlist just added is in use at once: all of them together, when one alone was chosen.
                        if (!Sources.active(app).isEmpty()) Sources.setActive(app, Sources.ALL);
                    }
                }
                List<Channel> all = new ArrayList<>();
                List<String> guides = new ArrayList<>(), problems = new ArrayList<>();
                List<Sources.Entry> in = Sources.inUse(app);
                for (Sources.Entry e : in) {
                    try {
                        Loaded l = e.id.equals(tried) ? first : fetch(e.source, e.id);
                        for (Channel c : l.channels) {
                            all.add(e.prefix().isEmpty() ? c : new Channel(e.prefix() + c.key, c.name, c.group, c.logo, c.url, c.tvgId,
                                    c.userAgent, c.referrer, c.number));
                        }
                        if (!l.guideUrl.isEmpty() && !guides.contains(l.guideUrl)) guides.add(l.guideUrl);
                    } catch (IOException x) {
                        problems.add(e.name + ": " + (x.getMessage() == null ? "didn't load" : x.getMessage()));
                    }
                }
                if (all.isEmpty()) throw new IOException(problems.isEmpty() ? "No live channels in it" : String.join("; ", problems));
                String fp = Sources.fingerprint(app);
                if (!fp.equals(fingerprint)) clearGuide();
                apply(all, fp, String.join(" ", guides), System.currentTimeMillis());
                reindex();
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
            if (cb != null) App.main.post(() -> cb.done(count, err));
            if (err == null) refreshGuide();
        });
    }

    /**
     * Everything removed from this phone: the source, the channels, the guide, the favourites, the hidden channels and
     * Home's Recently watched.
     */
    public void clear() {
        Source.clear(app);
        Sources.clear(app);
        Vod.clear(app);
        Picks.clear(app);
        Watched.clear(app);
        Hidden.clear(app);
        Groups.clear(app);
        ChannelEdits.clear(app);
        Playback.get(app).forget();
        channels = Collections.emptyList();
        plain = Collections.emptyList();
        families = new int[0][];
        rows = new boolean[0];
        sortNames = new String[0];
        perPlaylist = Collections.emptyMap();
        lower = new String[0];
        groups = Collections.emptyList();
        byKey = Collections.emptyMap();
        byName = Collections.emptyMap();
        infos = new Names.Info[0];
        hideKeys = new String[0];
        hidden = new boolean[0];
        ownHidden = new boolean[0];
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

    /** How many channels the playlists gave before the ones in several were made one. */
    private volatile int rawCount;

    /** Every stream the playlists gave, a channel in several of them counted once for each. */
    public int streamCount() { return rawCount; }

    /** How many channels each playlist in use gave, by its id. */
    private volatile Map<String, Integer> perPlaylist = Collections.emptyMap();

    /** How many channels a playlist gave (0 when it isn't in use, or hasn't loaded). */
    public int countIn(String id) {
        Integer n = perPlaylist.get(id);
        return n == null ? 0 : n;
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
                // Its films and episodes kept for Watch (Vod), the channels for Live TV.
                Vod.saveM3u(app, id, res.vod);
                l = new Loaded(res.channels, s.guideUrl.isEmpty() ? res.guideUrl : s.guideUrl);
            }
            // An Xtream server's M3U address: its account's expiry date asked for too (no matter if it doesn't answer).
            String[] x = Sources.xtreamOf(s.m3uUrl);
            if (x != null) {
                try {
                    Sources.note(app, id, new JSONObject(text(Xtream.api(x[0], x[1], x[2], null))).optJSONObject("user_info"));
                } catch (IOException | JSONException | RuntimeException ignored) {
                    // no expiry date for it
                }
            }
            return l;
        }
        return xtream(s, id);
    }

    /** An Xtream Codes account: the login checked (its expiry date kept), its live categories, then its live streams. */
    private Loaded xtream(Source s, String id) throws IOException {
        String base = Xtream.base(s.server);
        if (base.isEmpty()) throw new IOException("Add the server's address");
        String ext;
        try {
            JSONObject acct = new JSONObject(text(Xtream.api(base, s.user, s.pass, null)));
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
                String sid = "", name = "", icon = "", epg = "", cat = "", type = "live";
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
                        default: break;
                    }
                }
                jr.endObject();
                if (sid.isEmpty() || (!type.isEmpty() && !type.equalsIgnoreCase("live") && !type.equalsIgnoreCase("created_live"))) continue;
                String group = cats.get(cat);
                out.add(new Channel("x:" + sid, name, group, icon, Xtream.stream(base, s.user, s.pass, sid, ext), epg, null, null, num));
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
    private synchronized void apply(List<Channel> raw, String fp, String gUrl, long at) {
        rawCount = raw.size();
        Map<String, Integer> per = new HashMap<>();
        for (Channel c : raw) {
            String id = playlistOf(c.key);
            Integer n = per.get(id);
            per.put(id, n == null ? 1 : n + 1);
        }
        perPlaylist = per;
        plain = Collections.unmodifiableList(new ArrayList<>(merged(raw)));
        build();
        fingerprint = fp;
        guideUrl = gUrl == null ? "" : gUrl;
        loadedAt = at;
        matches.clear();
    }

    /**
     * The channels as the family has them, from the playlists' (plain): one row per channel (Versions), their own names
     * and logos (ChannelEdits), the names without their quality for a channel shown as one row; then the indexes.
     */
    private synchronized void build() {
        List<Channel> base = plain;
        Groups.load(app);
        ChannelEdits.State ed = ChannelEdits.state(app);
        int size = base.size();
        Names.Info[] in = new Names.Info[size];
        for (int i = 0; i < size; i++) in[i] = Names.info(base.get(i).playlistName, base.get(i).group);
        int[][] fam = ed.merge ? Versions.group(base, in) : new int[size][];
        List<Channel> dressed = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Channel c = base.get(i);
            List<String> ks = new ArrayList<>(3);
            ks.add(c.key);
            if (fam[i] != null) for (int p : fam[i]) if (p != i) ks.add(base.get(p).key);
            String own = ChannelEdits.State.first(ed.names, ks), logo = ChannelEdits.State.first(ed.logos, ks);
            String title = fam[i] != null ? Versions.title(base.get(fam[i][0]).playlistName) : null;
            String name = ChannelEdits.shown(c.playlistName, own, title);
            String shownLogo = logo != null ? logo : c.playlistLogo;
            dressed.add(name.equals(c.name) && shownLogo.equals(c.logo) ? c : c.dressed(name, shownLogo));
        }
        // The logos picked from the catalogue or made here (ChannelLogos) over everything else.
        List<Channel> ch = Collections.unmodifiableList(new ArrayList<>(ChannelLogos.apply(app, dressed, new HashMap<>())));
        String[] low = new String[ch.size()];
        String[] sorts = new String[ch.size()];
        String[] hk = new String[ch.size()];
        Map<String, Integer> keys = new HashMap<>(ch.size() * 2);
        Map<String, List<Integer>> names = new HashMap<>(ch.size() * 2);
        LinkedHashMap<String, Boolean> gs = new LinkedHashMap<>();
        for (int i = 0; i < ch.size(); i++) {
            Channel c = ch.get(i);
            sorts[i] = Names.plain(c.name).toLowerCase(Locale.ROOT);
            // Found by search under its own name and the playlist's.
            low[i] = c.name.equals(c.playlistName) ? sorts[i] : sorts[i] + "\n" + Names.plain(c.playlistName).toLowerCase(Locale.ROOT);
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
        sortNames = sorts;
        infos = in;
        hideKeys = hk;
        families = fam;
        boolean[] own = Hidden.mask(hk, Hidden.keys(app));
        boolean[] m = withGroups(own, ch, Groups.hidden(app));
        boolean[] r = rows(ch, fam, m, ed.picks);
        int vis = 0;
        for (int i = 0; i < m.length; i++) if (!m[i] && r[i]) vis++;
        ownHidden = own;
        hidden = m;
        rows = r;
        visible = vis;
        byKey = keys;
        byName = idx;
        groups = Collections.unmodifiableList(new ArrayList<>(gs.keySet()));
        channels = ch;
    }

    /** The guide read again (in the background, after the channels): only the playlist's channels, a 13 hour window. */
    void refreshGuide() {
        String urls = guideUrl;
        if (urls.isEmpty() || channels.isEmpty() || !guideBusy.compareAndSet(false, true)) return;
        App.io.execute(() -> {
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
                            String k = Names.key(c.playlistName);
                            if (k.isEmpty()) continue;
                            List<String> l = names.get(k);
                            if (l == null) names.put(k, l = new ArrayList<>(1));
                            if (!l.contains(main.key)) l.add(main.key);
                        }
                    }
                }
                // Each playlist's guide, the first to have a channel giving its programmes.
                Map<String, Guide.Schedule> g = new HashMap<>();
                IOException last = null;
                for (String url : urls.split(" ")) {
                    if (url.isEmpty()) continue;
                    try (InputStream in = Fetch.open(url, null)) {
                        for (Map.Entry<String, Guide.Schedule> e : Guide.read(in, ids, names, System.currentTimeMillis()).entrySet()) {
                            if (!g.containsKey(e.getKey())) g.put(e.getKey(), e.getValue());
                        }
                    } catch (IOException e) {
                        last = e;
                    }
                }
                if (g.isEmpty() && last != null) throw last;
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
            // Each channel with its backups after it: read back, they are made one again (merged). As the playlists give
            // them: the family's names and logos are made again from ChannelEdits.
            for (Channel main : plain) {
                List<Channel> all = new ArrayList<>(1 + main.alternates.size());
                all.add(main);
                all.addAll(main.alternates);
                for (Channel c : all) {
                    w.write(cell(c.key) + "\t" + cell(c.playlistName) + "\t" + cell(c.group) + "\t" + cell(c.playlistLogo) + "\t" + cell(c.url) + "\t"
                            + cell(c.tvgId) + "\t" + cell(c.userAgent) + "\t" + cell(c.referrer) + "\t" + c.number + "\n");
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
            // v3: each programme's picture after its description.
            w.write("v3\t" + fingerprint + "\t" + guideAt + "\n");
            for (Map.Entry<String, Guide.Schedule> e : guide.entrySet()) {
                Guide.Schedule s = e.getValue();
                for (int i = 0; i < s.start.length; i++) {
                    w.write(cell(e.getKey()) + "\t" + s.start[i] + "\t" + s.stop[i] + "\t" + cell(s.title[i]) + "\t"
                            + cell(s.desc[i]) + "\t" + cell(s.pic(i)) + "\n");
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
        PicLinks.Builder links = new PicLinks.Builder();
        long at;
        long now = System.currentTimeMillis();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8), 64 * 1024)) {
            String head = r.readLine();
            String[] h = head == null ? new String[0] : head.split("\t", -1);
            // v1 had no descriptions, v2 no pictures.
            boolean v3 = h.length >= 3 && "v3".equals(h[0]), v2 = v3 || h.length >= 3 && "v2".equals(h[0]);
            if (h.length < 3 || !(v2 || "v1".equals(h[0])) || !fp.equals(h[1])) return;
            at = Long.parseLong(h[2]);
            Map<String, String> pool = new HashMap<>();
            for (String line; (line = r.readLine()) != null; ) {
                String[] c = line.split("\t", v3 ? 6 : v2 ? 5 : 4);
                if (c.length < 4) continue;
                long stop = Long.parseLong(c[2]);
                if (stop < now - Guide.BEHIND_MS) continue;
                Guide.Builder bl = b.get(c[0]);
                if (bl == null) b.put(c[0], bl = new Guide.Builder());
                bl.add(Long.parseLong(c[1]), stop, shared(pool, c[3]), c.length > 4 ? shared(pool, c[4]) : "",
                        c.length > 5 ? links.add(c[5]) : -1);
            }
        } catch (IOException | NumberFormatException e) {
            return;
        }
        PicLinks all = links.build();
        Map<String, Guide.Schedule> g = new HashMap<>();
        for (Map.Entry<String, Guide.Builder> e : b.entrySet()) g.put(e.getKey(), e.getValue().build(all));
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
