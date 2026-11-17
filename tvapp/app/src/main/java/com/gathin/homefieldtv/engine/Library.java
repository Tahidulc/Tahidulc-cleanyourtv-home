package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.JsonReader;
import android.util.JsonToken;

import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.GuidePrefs;
import com.gathin.homefieldtv.prefs.Parental;
import com.gathin.homefieldtv.prefs.Shown;

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
import java.util.HashSet;
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
 *
 * <p>With tens of thousands of channels nothing the screens ask for here goes through them all on the main thread:
 * what a load gives is worked out once as it is read (Lists.Index: each channel's guide category, its place, the
 * groups), and the lists the screens show (Lists: Settings → Categories and Parental control applied, with each
 * channel's place in them and the channels by number) are made off the main thread, again whenever what is hidden or
 * locked changes. The screens read them at once (Lineup) and are told (the listeners) when new ones are in.
 */
public final class Library {
    public static final String ALL = "", FAVOURITES = "★";
    /** Home's rows of channels: the most on now, and the most of them sport. */
    public static final int ON_NOW = 24, SPORT_ON_NOW = 16;
    // How often the playlists and the guide are read again: Settings → Guide (GuidePrefs; as it came, a day and 6 hours).
    private static Library instance;

    private final Context app;
    /** The channels as last read with what is worked out from them, and the lists the screens show: put in place at once. */
    private volatile Lists current = Lists.EMPTY;
    private final AtomicBoolean listing = new AtomicBoolean();
    private volatile Map<String, Guide.Schedule> guide = Collections.emptyMap();
    private volatile String fingerprint = "", guideUrl = "", status = "";
    /** Catch-up: {channels whose provider keeps an archive, the most days kept}. */
    private volatile int[] archive = {0, 0};
    private volatile long loadedAt, guideAt;
    private volatile boolean read;
    private final AtomicBoolean busy = new AtomicBoolean(), guideBusy = new AtomicBoolean();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    /** The favourites and the recent channels as last read from the TV's settings (read again once the kept line changes). */
    private volatile Picks favs = Picks.NONE, recents = Picks.NONE;
    /** The favourites' channels the lists keep, for one version of the favourites and of the lists. */
    private volatile Kept kept = Kept.NONE;
    /** Home's rows of channels on now (onNow), and who asked for them while they were being found. */
    private volatile OnNow onNow = OnNow.NONE;
    private final AtomicBoolean finding = new AtomicBoolean();
    private volatile Runnable onNowTold;

    /** Told on the main thread how a test or refresh went: the channel count, or an error to show. */
    public interface Callback {
        void done(int channels, String error);
    }

    private Library(Context app) {
        this.app = app;
    }

    public static synchronized Library get(Context c) {
        if (instance == null) {
            instance = new Library(c.getApplicationContext());
            // A category hidden or shown, a channel locked, the PIN entered: the lists made again at once, off the main thread.
            Shown.listen(instance::relist);
        }
        return instance;
    }

    /** Home's rows of channels: those with something on now (favourites first) and the sport among them. */
    public static final class OnNow {
        static final OnNow NONE = new OnNow(Collections.emptyList(), Collections.emptyList(), null, null, null, -1);

        public final List<Channel> channels, sport;
        final Lists lists;
        final Map<String, Guide.Schedule> guide;
        final Picks favourites;
        final long minute;

        OnNow(List<Channel> channels, List<Channel> sport, Lists lists, Map<String, Guide.Schedule> guide, Picks favourites, long minute) {
            this.channels = channels;
            this.sport = sport;
            this.lists = lists;
            this.guide = guide;
            this.favourites = favourites;
            this.minute = minute;
        }
    }

    /** Channel keys kept in the TV's settings, as read from that very line (read again only once it changes). */
    static final class Picks {
        static final Picks NONE = new Picks(null, Collections.emptyList());

        final String raw;
        final List<String> keys;
        final Set<String> set;

        Picks(String raw, List<String> keys) {
            this.raw = raw;
            this.keys = Collections.unmodifiableList(keys);
            this.set = new HashSet<>(keys);
        }
    }

    /** The favourites' channels the lists keep, and what they were made from. */
    private static final class Kept {
        static final Kept NONE = new Kept(null, null, Collections.emptyList());

        final Picks picks;
        final Lists lists;
        final List<Channel> channels;

        Kept(Picks picks, Lists lists, List<Channel> channels) {
            this.picks = picks;
            this.lists = lists;
            this.channels = channels;
        }
    }

    /** The lists for the channels as they are; made again off the main thread when what is hidden or locked has changed. */
    private Lists lists() {
        Lists l = current;
        // No channels, no lists to make (nor settings to read for them).
        if (!l.fresh() && !l.index.channels.isEmpty()) {
            relist();
            // Made at once in previews and tests; else these until the new ones are in (the screens are told then).
            l = current;
        }
        return l;
    }

    /** The lists made again for what is hidden and locked now: off the main thread (at once in previews), the screens told. */
    private void relist() {
        if (current.index.channels.isEmpty()) return;
        if (Exec.inline) {
            synchronized (this) {
                current = new Lists(current.index, Shown.filter(app));
            }
            return;
        }
        if (!listing.compareAndSet(false, true)) return;
        Exec.work(() -> {
            boolean made = false;
            try {
                for (int tries = 0; tries < 4; tries++) {
                    Lists was = current;
                    if (was.fresh() || was.index.channels.isEmpty()) break;
                    Lists l = new Lists(was.index, Shown.filter(app));
                    synchronized (this) {
                        // A load meanwhile brought lists of its own.
                        if (current == was) {
                            current = l;
                            made = true;
                        }
                    }
                }
            } finally {
                listing.set(false);
            }
            if (!current.fresh()) relist();
            if (made) changed();
        });
    }

    // ---- what the screens read ----------------------------------------------------------------------------------

    public boolean hasSource() { return !Sources.list(app).isEmpty(); }
    public boolean ready() { return !current.index.channels.isEmpty(); }
    public boolean busy() { return busy.get(); }
    public String status() { return status; }
    public int count() { return current.index.channels.size(); }
    /** Catch-up: {how many channels keep programmes, for up to how many days}. */
    public int[] archive() { return archive; }
    public boolean hasGuide() { return !guide.isEmpty(); }
    /** Settings → EPG: when the guide was last read (0: not yet), for how many channels, and whether it is being read. */
    public long guideAt() { return guideAt; }
    public int guideChannels() { return guide.size(); }
    public boolean guideBusy() { return guideBusy.get(); }

    /** Settings → EPG → Update EPG: the guide read again now (the channels as they are). */
    public void updateGuide() {
        refreshGuide();
        changed();
    }

    /** Settings → EPG → Clear EPG: the guide kept on this TV let go, until it is read again. */
    public void clearGuideNow() {
        clearGuide();
        changed();
    }
    /** The playlist's own groups, in first-seen order. */
    public List<String> groups() { return current.index.groups; }
    /** How many channels each group has, hidden ones too (Settings → Categories), counted as they were read. */
    public Map<String, Integer> groupSizes() { return current.index.sizes; }
    /**
     * The channels the screens list: without hidden categories and channels locked away (Settings → Categories and
     * Parental control, prefs.Shown). Made off the main thread, read at once.
     */
    public List<Channel> all() { return lists().all; }
    /** Every channel, hidden ones too (a channel's other copies, its quality levels). */
    public List<Channel> every() { return current.index.channels; }

    /** All the channels listed, with where each is. */
    public Lineup lineup() {
        Lists l = lists();
        return new Lineup(l, Lineup.EVERY, "", -1, l.all);
    }

    /** A group's listed channels (none for a hidden group), with where each is. */
    public Lineup group(String group) {
        Lists l = lists();
        List<Channel> g = group == null ? null : l.groups.get(group);
        return g == null ? Lineup.NONE : new Lineup(l, Lineup.GROUP, group, -1, g);
    }

    /** One of the guide's categories (Genres) with its listed channels, with where each is. */
    public Lineup genre(String genre) {
        Lists l = lists();
        int g = Genres.index(genre);
        return g < 0 ? Lineup.NONE : new Lineup(l, Lineup.KIND, "", g, l.genres.get(g));
    }

    /** A short list of channels (the favourites, the recent ones), with where each is. */
    public Lineup lineup(List<Channel> few) {
        return new Lineup(lists(), Lineup.FEW, "", -1, few == null ? Collections.<Channel>emptyList() : few);
    }

    /** The groups that have listed channels, in the order they first come (the unnamed group too): Live TV's rows. */
    public List<String> rowGroups() { return lists().groupOrder; }

    /**
     * Every channel (hidden ones too) whose name reads as this one does (Names.key: "UK: KESTREL SPORTS 1 FHD" as
     * "Kestrel Sports 1"), in the list's order. The first ask after a load reads every name once: off the main thread
     * only.
     */
    public List<Channel> named(String name) {
        String k = Names.key(name);
        List<Channel> l = k.isEmpty() ? null : current.index.byName().get(k);
        return l == null ? Collections.<Channel>emptyList() : Collections.unmodifiableList(l);
    }

    /** Whether a channel is in the lists (not hidden, not locked away), as the lists made last say. At once. */
    public boolean listed(Channel c) {
        Lists l = lists();
        int e = l.index.find(c);
        return e >= 0 && l.at[e] >= 0;
    }

    /**
     * Channel up or down from a channel through the lists, past those that need the PIN, as Shown.step goes: from
     * itself when there is no other; the first (or last) when it isn't listed; null when nothing is. At once.
     */
    public Channel step(Channel from, int dir) {
        return lists().step(from, dir);
    }

    /**
     * The channel the mini TV may start by itself (Shown.playable): the one asked for when nothing keeps it back, else
     * the first listed that needs no PIN; null when there is none. At once.
     */
    public Channel playable(Channel want) {
        if (want != null && Shown.listed(app, want) && !Parental.blocks(app, want)) return want;
        Lists l = lists();
        Channel c = l.first < 0 ? null : l.all.get(l.first);
        // Never one that needs the PIN now, even from lists not made again yet (the screens are told once they are).
        return c == null || Parental.blocks(app, c) ? null : c;
    }

    /** The first listed channel with this number, or null. At once. */
    public Channel numbered(int number) {
        Lists l = lists();
        int k = l.numbered(number);
        return k < 0 ? null : l.all.get((int) l.numbers[k]);
    }

    public Channel byKey(String key) {
        Lists.Index ix = current.index;
        Integer i = key == null ? null : ix.byKey.get(key);
        return i == null || i >= ix.channels.size() ? null : ix.channels.get(i);
    }

    public Guide.Schedule schedule(String key) { return guide.get(key); }

    /** The channel's place in the list (0 first), or -1. */
    public int position(String key) {
        Integer i = key == null ? null : current.index.byKey.get(key);
        return i == null ? -1 : i;
    }

    /** The number shown for a channel: the playlist's own (tvg-chno, Xtream's num), else its place in the playlist. */
    public int number(Channel c) {
        if (c.number > 0) return c.number;
        int p = position(c.key);
        return p < 0 ? 0 : p + 1;
    }

    /**
     * A group's channels (ALL, FAVOURITES or a group's name), narrowed by a search; read-only. Without a search they are
     * the lists made already; a search goes through every channel, so it is for the threads off the main one.
     */
    public List<Channel> list(String group, String query) {
        String q = query == null ? "" : Names.plain(query).toLowerCase(Locale.ROOT).trim();
        if (FAVOURITES.equals(group)) {
            List<Channel> fav = favouriteChannels();
            if (q.isEmpty()) return fav;
            List<Channel> out = new ArrayList<>();
            for (Channel c : fav) {
                // Its playlist's name, or the family's own for it.
                if (Names.plain(c.name).toLowerCase(Locale.ROOT).contains(q)
                        || ChannelEdits.renamed(c) && Names.plain(ChannelEdits.name(c)).toLowerCase(Locale.ROOT).contains(q)) out.add(c);
            }
            return out;
        }
        boolean every = group == null || group.isEmpty();
        if (q.isEmpty()) return every ? all() : group(group).channels;
        return find(every ? null : group, q, Integer.MAX_VALUE);
    }

    /** The listed channels whose names have the words typed, at most max, in the lists' order. Off the main thread. */
    public List<Channel> search(String query, int max) {
        String q = query == null ? "" : Names.plain(query).toLowerCase(Locale.ROOT).trim();
        return q.isEmpty() ? new ArrayList<>() : find(null, q, max);
    }

    private List<Channel> find(String group, String q, int max) {
        Lists l = lists();
        Lists.Index ix = l.index;
        List<Channel> out = new ArrayList<>();
        for (int i = 0; i < ix.channels.size() && out.size() < max; i++) {
            // By its playlist's name, or by the name the family gave it.
            if (l.at[i] < 0 || !ix.lower[i].contains(q) && !l.renamedHas(i, q)) continue;
            Channel c = ix.channels.get(i);
            if (group == null || group.equals(c.group)) out.add(c);
        }
        return out;
    }

    /**
     * Home's rows of channels: at most ON_NOW with something on now (favourites first, then the lists' order; every
     * channel when there is no guide) and at most SPORT_ON_NOW of them sport. The last found, at once; found again off
     * the main thread when the minute, the channels, the guide, the favourites or what is hidden has changed since, and
     * whenReady then told on the main thread.
     */
    public OnNow onNow(long time, Runnable whenReady) {
        OnNow o = onNow;
        Lists l = lists();
        Map<String, Guide.Schedule> g = guide;
        Picks f = picks();
        long minute = time / 60_000L;
        if (o.lists == l && o.guide == g && o.favourites == f && o.minute == minute) return o;
        if (Exec.inline) {
            onNow = o = onNow(l, g, f, time);
            return o;
        }
        onNowTold = whenReady;
        if (finding.compareAndSet(false, true)) {
            Exec.work(() -> {
                try {
                    onNow = onNow(l, g, f, time);
                } finally {
                    finding.set(false);
                }
                Runnable r = onNowTold;
                onNowTold = null;
                if (r != null) Exec.main(r);
            });
        }
        return o;
    }

    /** Tests: what the lists are made from now, and Home's rows worked out at once. */
    Lists.Index index() {
        return current.index;
    }

    OnNow onNowAt(long time) {
        return onNow(lists(), guide, picks(), time);
    }

    /** Pure: Home's rows of channels from these lists, guide and favourites at time (see onNow). */
    static OnNow onNow(Lists l, Map<String, Guide.Schedule> g, Picks f, long time) {
        List<Channel> on = new ArrayList<>(ON_NOW), sport = new ArrayList<>(SPORT_ON_NOW);
        boolean hasGuide = !g.isEmpty();
        int sportKind = Genres.index(Genres.SPORT);
        Lists.Index ix = l.index;
        Set<String> seen = new HashSet<>();
        int favourites = f.keys.size(), n = favourites + l.all.size();
        for (int k = 0; k < n && (on.size() < ON_NOW || sport.size() < SPORT_ON_NOW); k++) {
            Channel c;
            if (k < favourites) {
                Integer e = ix.byKey.get(f.keys.get(k));
                if (e == null) continue;
                c = ix.channels.get(e);
                if (!seen.add(c.key)) continue;
            } else {
                c = l.all.get(k - favourites);
                if (seen.contains(c.key)) continue;
            }
            Guide.Schedule s = g.get(c.key);
            if (hasGuide && (s == null || s.at(time) < 0)) continue;
            if (on.size() < ON_NOW) on.add(c);
            int e = ix.find(c);
            if (sport.size() < SPORT_ON_NOW && e >= 0 && ix.genre[e] == sportKind) sport.add(c);
        }
        return new OnNow(Collections.unmodifiableList(on), Collections.unmodifiableList(sport), l, g, f, time / 60_000L);
    }

    // ---- favourites and the last channel -------------------------------------------------------------------------

    private SharedPreferences prefs() {
        return app.getSharedPreferences("live_tv", Context.MODE_PRIVATE);
    }

    /** The favourites as kept, read again only when the kept line itself has changed (here, by a sync or a backup). */
    private Picks picks() {
        String raw = prefs().getString("favourites", "[]");
        Picks p = favs;
        //noinspection StringEquality: the very line read last time, or a new one
        if (raw != p.raw) {
            List<String> out = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
            } catch (JSONException ignored) {
                // none
            }
            favs = p = new Picks(raw, out);
        }
        return p;
    }

    /** The recent channels as kept, read again only when the kept line has changed. */
    private Picks recentPicks() {
        String raw = prefs().getString("recent", "");
        Picks p = recents;
        //noinspection StringEquality: the very line read last time, or a new one
        if (raw != p.raw) recents = p = new Picks(raw, RecentChannels.parse(raw));
        return p;
    }

    /** The favourite channels' keys, in the order they were added (read-only). */
    public List<String> favourites() {
        return picks().keys;
    }

    public boolean isFavourite(Channel c) {
        return c != null && picks().set.contains(c.key);
    }

    /** The favourites' channels the lists keep (not locked away), made again only when either changes. */
    private List<Channel> favouriteChannels() {
        Picks p = picks();
        Lists l = lists();
        Kept k = kept;
        if (k.picks == p && k.lists == l) return k.channels;
        List<Channel> out = new ArrayList<>();
        for (String key : p.keys) {
            Channel c = byKey(key);
            if (c != null && l.filter != null && l.filter.kept(c)) out.add(c);
        }
        k = new Kept(p, l, Collections.unmodifiableList(out));
        kept = k;
        return k.channels;
    }

    /** A channel added to the favourites, or taken off them; true when it is one now. */
    public boolean toggleFavourite(Channel c) {
        Set<String> f = new LinkedHashSet<>(favourites());
        boolean now = !f.remove(c.key);
        if (now) f.add(c.key);
        prefs().edit().putString("favourites", new JSONArray(f).toString()).apply();
        changed();
        com.gathin.homefieldtv.sync.TvSync.changed();
        return now;
    }

    /** The favourites in a new order (Move channel in Favourites: TiviMate's Manage Favorites); any others kept after. */
    public void orderFavourites(List<String> keys) {
        Set<String> f = new LinkedHashSet<>();
        for (String k : keys) if (picks().set.contains(k)) f.add(k);
        f.addAll(favourites());
        prefs().edit().putString("favourites", new JSONArray(f).toString()).apply();
        changed();
        com.gathin.homefieldtv.sync.TvSync.changed();
    }

    /** Favourites and recent channels as the family's other devices have them (sync.TvLiveSync); null leaves either be. */
    public void setPicks(List<String> favourites, List<String> recent) {
        SharedPreferences.Editor e = prefs().edit();
        if (favourites != null) e.putString("favourites", new JSONArray(favourites).toString());
        if (recent != null) e.putString("recent", RecentChannels.join(recent));
        e.apply();
        changed();
    }

    /** The channel last watched on this TV, or the first one when none has been. */
    public Channel lastChannel() {
        Channel c = byKey(prefs().getString("last", null));
        if (c != null) return c;
        List<Channel> ch = current.index.channels;
        return ch.isEmpty() ? null : ch.get(0);
    }

    /** A channel played: the last one, and first of the recent channels (kept on this TV only). */
    public void watched(Channel c) {
        if (c == null) return;
        List<String> recent = RecentChannels.add(recentPicks().keys, c.key);
        prefs().edit().putString("last", c.key).putString("recent", RecentChannels.join(recent)).apply();
        com.gathin.homefieldtv.sync.TvSync.changed();
    }

    /**
     * The channels last watched on this TV that the playlists still have, newest first: without the one playing (null
     * for none), RecentChannels.MAX at most.
     */
    public List<Channel> recent(Channel playing) {
        List<Channel> out = new ArrayList<>();
        Lists l = lists();
        for (String k : RecentChannels.shown(recentPicks().keys, playing == null ? null : playing.key)) {
            Channel c = byKey(k);
            if (c != null && l.filter != null && l.filter.kept(c)) out.add(c);
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
        if (current.index.channels.isEmpty() || start || GuidePrefs.listsDue(app, loadedAt, now)
                || !Sources.fingerprint(app).equals(fingerprint)) {
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
                n = current.index.channels.size();
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
        synchronized (this) {
            current = Lists.EMPTY;
        }
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
            if (colon > 1 && digits(key, 1, colon)) return key.substring(0, colon);
        }
        return "p0";
    }

    /** Pure: whether s holds only digits from start to end (no regular expression made for each channel). */
    private static boolean digits(String s, int start, int end) {
        for (int i = start; i < end; i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        return end > start;
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

    /**
     * New channels in place, on the thread that read them: the key index, each channel's guide category, the groups,
     * and the lists the screens show, all made here, then put in place together.
     */
    private synchronized void apply(List<Channel> raw, String fp, String gUrl, long at) {
        Lists.Index ix = new Lists.Index(Collections.unmodifiableList(new ArrayList<>(merged(raw))));
        Lists l = new Lists(ix, Shown.filter(app));
        archive = Catchup.count(ix.channels);
        current = l;
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
        List<Channel> channels = current.index.channels;
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
                Map<String, Long> since = GuideShift.since(since(channels, now), shift);
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
    private static Map<String, Long> since(List<Channel> channels, long now) {
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
            for (Channel main : current.index.channels) {
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
            Map<String, Long> since = since(current.index.channels, now);
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
