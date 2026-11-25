package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Each row of CategorySet made into its titles, from what the app already has: the playlists' films and series (and the
 * family's own TorBox titles), where the family left off, My List, what they watched lately, and TMDB's lists (trending,
 * a themed discover, a list, a network's shows, a watched title's recommendations) matched to what the playlists hold
 * (MatchIndex), so every card plays. A row with nothing in it is empty and the page leaves it out. A TMDB row is read a
 * page at a time: the first pages as it is made, the next as the remote nears its end (more()), at most
 * TmdbLists.MAX_PAGES. Rows from the family's own lists (local) are quick and can be made on the main thread; a TMDB row
 * (CategorySet.Entry.tmdb) only off it. A themed row's titles turn with the day (DayRows.turn), so the same row shows
 * other titles first tomorrow; a Top 10 has ten at most; a Coming Soon row is the only one with titles the playlists
 * don't have yet (a Pick with no title, only TMDB's item), never any other.
 */
public final class Shelves {
    private Shelves() {}

    /** How many cards a row of the family's own lists has (its "All" card opens the rest); a TMDB row's most. */
    public static final int ROW = 40, MOST = 80;
    /** A TMDB row's first pages are read until it has this many, or FIRST_PAGES pages are read. */
    static final int ENOUGH = 10, FIRST_PAGES = 3;
    /** How many of the titles watched lately the recommendations come from, and how many are kept. */
    static final int TASTE = 5, TASTE_KEPT = 12;

    /** At most this many titles in a Top 10. */
    static final int TOP = 10;
    /** A themed row's titles turn among this many of its first. */
    static final int TURN = 12;

    /** One card of a row: a title (with TMDB's word on it, where from a list), and where it was left. */
    public static final class Pick {
        public final Catalog.Title title;
        public final TmdbLists.Item item;
        public final Resume.Point point;

        Pick(Catalog.Title title, TmdbLists.Item item, Resume.Point point) {
            this.title = title;
            this.item = item;
            this.point = point;
        }

        /** Its poster: TMDB's where the list gave one, else the playlist's. */
        public String poster() {
            if (item != null && !item.poster.isEmpty()) return item.poster;
            return title == null ? "" : title.poster;
        }

        /** Its wide picture: TMDB's backdrop where the list gave one, else the playlist's ("" for none). */
        public String backdrop() {
            if (item != null && !item.backdrop.isEmpty()) return item.backdrop;
            return title == null ? "" : title.backdrop;
        }

        /** What it is kept by: its title's key, or (a Coming Soon card the playlists don't have) TMDB's id. */
        public String key() {
            if (title != null) return title.key;
            return item == null ? "" : "tmdb:" + (item.tv ? "tv:" : "movie:") + item.id;
        }
    }

    /** A row made: its entry, its title (a network's as TMDB names it), its cards, its whole list (local rows), its pages. */
    public static final class Shelf {
        public final CategorySet.Entry entry;
        public final String title;
        public final List<Pick> picks;
        /** A local row's whole list, for its "All" card (null for a TMDB row, which pages instead). */
        public final List<Catalog.Title> all;
        /** Each TMDB part's path, next page to read (beyond its last: none) and its pages, as made. */
        final String[] paths;
        final int[] next, pages;
        /** Titles never shown in it (a recommendation row's own watched ones). */
        final Set<String> skip;
        /** When it was made (ms), and for which catalogue (its lists). */
        final long at;
        final List<Catalog.Title> madeFor;

        Shelf(CategorySet.Entry entry, String title, List<Pick> picks, List<Catalog.Title> all, String[] paths, int[] next,
              int[] pages, Set<String> skip, List<Catalog.Title> madeFor) {
            this.entry = entry;
            this.title = title == null ? "" : title;
            this.picks = Collections.unmodifiableList(picks);
            this.all = all;
            this.paths = paths == null ? new String[0] : paths;
            this.next = next == null ? new int[0] : next;
            this.pages = pages == null ? new int[0] : pages;
            this.skip = skip == null ? Collections.emptySet() : skip;
            this.at = DayRows.now();
            this.madeFor = madeFor;
        }

        public boolean isEmpty() {
            return picks.isEmpty();
        }

        /** Whether a TMDB row has more pages to read (more()). */
        public boolean more() {
            if (picks.size() >= MOST) return false;
            for (int i = 0; i < next.length; i++) if (next[i] <= pages[i]) return true;
            return false;
        }

        /** How many its whole list has (its "All" card), else its cards. */
        public int total() {
            return all != null ? all.size() : picks.size();
        }
    }

    // ---- made ------------------------------------------------------------------------------------------------------

    /** Made once, kept for the screens: by page and id, for the catalogue it was made from. */
    private static final Map<String, Shelf> KEPT = Collections.synchronizedMap(new HashMap<>());

    /** The row as made before for this catalogue (null: not yet, or the catalogue changed). */
    public static Shelf kept(Context c, CategorySet.Entry e) {
        Shelf s = KEPT.get(e.toString());
        return s != null && s.madeFor == Catalog.get(c).films() ? s : null;
    }

    /** Whether a row kept is old enough to be made again (TMDB's lists move: ten minutes). */
    public static boolean stale(Shelf s) {
        return s == null || DayRows.now() - s.at > 10 * 60_000L;
    }

    /**
     * Whether a row's titles are past their expiry (DayRows.expiry: a few hours) at a time: its lists are then asked of
     * TMDB again (TmdbLists keeps them no longer than that) and the row made again, off the main thread.
     */
    public static boolean expired(Shelf s, long now) {
        return s == null || DayRows.expired(s.at, now, s.entry.source);
    }

    public static void forget() {
        KEPT.clear();
    }

    /** The rows kept for a page but those of the day (by id): the rest let go of (memory). */
    public static void keepOnly(String page, Set<String> ids) {
        synchronized (KEPT) {
            KEPT.keySet().removeIf(k -> k.startsWith(page + ".") && !ids.contains(k.substring(page.length() + 1)));
        }
    }

    /**
     * A row made from the family's own lists only (its TMDB parts left out): quick, for the main thread. A "|" of
     * choices takes its first local one that has anything (the carousel's newest while TMDB's are read).
     */
    public static Shelf local(Context c, CategorySet.Entry e) {
        for (List<String> choice : CategorySet.parts(e.source)) {
            boolean local = true;
            for (String p : choice) local &= !CategorySet.tmdb(p);
            if (!local) continue;
            Shelf s = make(c, e, choice);
            if (!s.isEmpty()) return s;
        }
        return empty(c, e);
    }

    /** A row made from all its source: TMDB's pages asked for (kept ones first). Off the main thread. */
    public static Shelf make(Context c, CategorySet.Entry e) {
        Shelf out = null;
        for (List<String> choice : CategorySet.parts(e.source)) {
            Shelf s = make(c, e, choice);
            if (!s.isEmpty()) {
                out = s;
                break;
            }
        }
        if (out == null) out = empty(c, e);
        KEPT.put(e.toString(), out);
        return out;
    }

    private static Shelf empty(Context c, CategorySet.Entry e) {
        return new Shelf(e, e.title, new ArrayList<>(), null, null, null, null, null, Catalog.get(c).films());
    }

    /** One choice of a source: its "+" parts taken in turn. */
    private static Shelf make(Context c, CategorySet.Entry e, List<String> parts) {
        Catalog cat = Catalog.get(c);
        String title = e.title;
        List<List<Pick>> streams = new ArrayList<>();
        List<Catalog.Title> all = null;
        List<String> paths = new ArrayList<>();
        List<Integer> next = new ArrayList<>(), pages = new ArrayList<>();
        Set<String> skip = new HashSet<>();
        for (String part : parts) {
            int colon = part.indexOf(':');
            String kind = colon < 0 ? part : part.substring(0, colon), arg = colon < 0 ? "" : part.substring(colon + 1);
            switch (kind) {
                case "continue":
                    streams.add(going(c, arg));
                    break;
                case "mylist":
                    streams.add(mine(c, arg));
                    break;
                case "new": {
                    List<Catalog.Title> got = newest(cat, arg);
                    all = got;
                    streams.add(titles(got, ROW));
                    break;
                }
                case "fresh": {
                    List<Catalog.Title> got = fresh(newest(cat, arg), DayRows.now());
                    all = got;
                    streams.add(titles(got, ROW));
                    break;
                }
                case "top": {
                    String path = resolve(c, arg, DayRows.now());
                    if (path == null) break;
                    tmdb(c, path, streams, paths, next, pages);
                    // Ten, in TMDB's order, and no more pages.
                    List<Pick> s = streams.get(streams.size() - 1);
                    while (s.size() > TOP) s.remove(s.size() - 1);
                    next.set(next.size() - 1, Integer.MAX_VALUE);
                    break;
                }
                case "soon": {
                    String path = resolve(c, arg, DayRows.now());
                    if (path != null) soon(c, path, streams, paths, next, pages);
                    break;
                }
                case "torbox": {
                    List<Catalog.Title> got = new ArrayList<>();
                    if (!arg.equals("tv")) got.addAll(cat.own(false));
                    if (!arg.equals("movie")) got.addAll(cat.own(true));
                    if (arg.equals("all")) got.sort((a, b) -> Long.compare(b.added, a.added));
                    all = got;
                    streams.add(titles(got, ROW));
                    break;
                }
                case "tmdb": {
                    String path = resolve(c, arg, DayRows.now());
                    if (path != null) tmdb(c, path, streams, paths, next, pages);
                    break;
                }
                case "list": {
                    int tilde = arg.indexOf('~');
                    String id = tilde < 0 ? arg : arg.substring(0, tilde), words = tilde < 0 ? "" : arg.substring(tilde + 1);
                    TmdbLists.Page first = TmdbLists.page(c, "list/" + id, 1);
                    // Only the list it was: one whose name no longer says so is left out.
                    if (first == null || !first.name.toLowerCase(Locale.ROOT).contains(words.toLowerCase(Locale.ROOT))) break;
                    tmdb(c, "list/" + id, streams, paths, next, pages);
                    break;
                }
                case "network":
                case "latest": {
                    String[] ids = arg.split("/");
                    int id = number(ids[0]);
                    String name = id > 0 ? TmdbLists.network(c, id) : "";
                    if (!allowedName(name)) return empty(c, e);
                    title = title.replace("{network}", name);
                    String with = String.join("%7C", ids);
                    String path = kind.equals("latest")
                            ? "discover/tv?with_networks=" + with + "&sort_by=first_air_date.desc&first_air_date.lte={today}&vote_count.gte=3"
                            : "discover/tv?with_networks=" + with + "&sort_by=popularity.desc";
                    tmdb(c, resolve(c, path, DayRows.now()), streams, paths, next, pages);
                    break;
                }
                case "because": {
                    // "0", "0:movie" or "0:tv": the Nth watched lately (of that kind).
                    int k = arg.indexOf(':');
                    String of = k < 0 ? "" : arg.substring(k + 1);
                    List<Catalog.Title> watched = new ArrayList<>();
                    for (Catalog.Title w : taste(c)) if (fits(w, of)) watched.add(w);
                    int n = number(k < 0 ? arg : arg.substring(0, k));
                    if (n >= watched.size()) break;
                    Catalog.Title t = watched.get(n);
                    String path = recommendations(c, t);
                    if (path == null) break;
                    title = title.replace("{title}", t.name);
                    for (Catalog.Title w : watched) skip.add(w.key);
                    tmdb(c, path, streams, paths, next, pages);
                    break;
                }
                case "foryou": {
                    List<Catalog.Title> watched = taste(c);
                    for (Catalog.Title w : watched) skip.add(w.key);
                    int asked = 0;
                    for (Catalog.Title t : watched) {
                        if (asked >= TASTE) break;
                        if (arg.equals("movie") && t.series || arg.equals("tv") && !t.series) continue;
                        String path = recommendations(c, t);
                        if (path == null) continue;
                        asked++;
                        tmdb(c, path, streams, paths, next, pages);
                    }
                    break;
                }
                case "genre": {
                    Map<String, String> g = fields(arg);
                    int before = streams.size();
                    if (g.containsKey("movie")) {
                        tmdb(c, "discover/movie?with_genres=" + g.get("movie") + "&sort_by=popularity.desc&vote_count.gte=100",
                                streams, paths, next, pages);
                    }
                    if (g.containsKey("tv")) {
                        tmdb(c, "discover/tv?with_genres=" + g.get("tv") + "&sort_by=popularity.desc&vote_count.gte=50",
                                streams, paths, next, pages);
                    }
                    boolean found = false;
                    for (int i = before; i < streams.size(); i++) found |= !streams.get(i).isEmpty();
                    if (!found) {
                        // Without TMDB: the playlists' own categories named for it.
                        List<Catalog.Title> got = byWords(cat, g.get("words"), g.containsKey("movie"), g.containsKey("tv"));
                        all = got;
                        streams.add(titles(got, ROW));
                    }
                    break;
                }
                default:
                    break;
            }
        }
        List<Pick> picks = interleave(streams, MOST, skip);
        // A themed row's titles turn with the day (the same all day).
        if (DayRows.turns(e.source)) picks = new ArrayList<>(DayRows.turn(picks, DayRows.day(), TURN));
        int[] n = new int[next.size()], p = new int[pages.size()];
        for (int i = 0; i < n.length; i++) {
            n[i] = next.get(i);
            p[i] = pages.get(i);
        }
        return new Shelf(e, title, picks, all, paths.toArray(new String[0]), n, p, skip, cat.films());
    }

    /**
     * A TMDB row with its next pages read and matched (a page or two from each part with more), its cards after the ones
     * it had (none twice); the row as it was when nothing more is to be had. Off the main thread.
     */
    public static Shelf more(Context c, Shelf s) {
        if (!s.more()) return s;
        MatchIndex index = MatchIndex.of(Catalog.get(c));
        List<Pick> picks = new ArrayList<>(s.picks);
        Set<String> seen = new HashSet<>(s.skip);
        for (Pick p : picks) seen.add(p.key());
        boolean soon = s.entry.soon();
        int[] next = s.next.clone();
        int added = 0;
        for (int round = 0; round < 2 && added < ENOUGH; round++) {
            for (int i = 0; i < s.paths.length; i++) {
                if (next[i] > s.pages[i]) continue;
                TmdbLists.Page page = TmdbLists.page(c, s.paths[i], next[i]);
                next[i]++;
                if (page == null) {
                    next[i] = Integer.MAX_VALUE;
                    continue;
                }
                for (Pick p : soon ? coming(index, page) : matched(index, page)) {
                    if (picks.size() >= MOST) break;
                    if (seen.add(p.key())) {
                        picks.add(p);
                        added++;
                    }
                }
            }
        }
        Shelf out = new Shelf(s.entry, s.title, picks, s.all, s.paths, next, s.pages, s.skip, s.madeFor);
        KEPT.put(s.entry.toString(), out);
        return out;
    }

    // ---- the parts ---------------------------------------------------------------------------------------------------

    /** A TMDB list's first pages, matched, as one stream (its path and next page kept for more()). */
    private static void tmdb(Context c, String path, List<List<Pick>> streams, List<String> paths, List<Integer> next,
                             List<Integer> pages) {
        List<Pick> out = new ArrayList<>();
        MatchIndex index = null;
        int page = 1, total = 1;
        Set<String> seen = new HashSet<>();
        for (; page <= total && page <= FIRST_PAGES && out.size() < ENOUGH; page++) {
            TmdbLists.Page got = TmdbLists.page(c, path, page);
            if (got == null) {
                total = 0;
                break;
            }
            total = got.pages;
            if (index == null) index = MatchIndex.of(Catalog.get(c));
            for (Pick p : matched(index, got)) if (seen.add(p.title.key)) out.add(p);
        }
        streams.add(out);
        paths.add(path);
        next.add(page);
        pages.add(total);
    }

    /**
     * A TMDB list of what is still to come, its first pages, as one stream: every title in it, the playlists' own where
     * they have it already (it plays), else TMDB's alone (its card says when it comes).
     */
    private static void soon(Context c, String path, List<List<Pick>> streams, List<String> paths, List<Integer> next,
                             List<Integer> pages) {
        List<Pick> out = new ArrayList<>();
        MatchIndex index = MatchIndex.of(Catalog.get(c));
        Set<String> seen = new HashSet<>();
        int page = 1, total = 1;
        for (; page <= total && page <= 2 && out.size() < ENOUGH * 2; page++) {
            TmdbLists.Page got = TmdbLists.page(c, path, page);
            if (got == null) {
                total = 0;
                break;
            }
            total = got.pages;
            for (Pick p : coming(index, got)) if (seen.add(p.key())) out.add(p);
        }
        streams.add(out);
        paths.add(path);
        next.add(page);
        pages.add(total);
    }

    /** Pure: a page of what is still to come, every title (the playlists' own where they have it), soonest first. */
    static List<Pick> coming(MatchIndex index, TmdbLists.Page page) {
        List<Pick> out = new ArrayList<>();
        for (TmdbLists.Item it : page.items) {
            if (it.poster.isEmpty() && it.backdrop.isEmpty()) continue;
            Catalog.Title t = index == null ? null : index.find(it.tv, it.name, it.original, it.yearNumber());
            out.add(new Pick(t, it, null));
        }
        out.sort((a, b) -> a.item.date.compareTo(b.item.date));
        return out;
    }

    /** Pure: a page's titles that the playlists have, in its order (each as the playlists' title, TMDB's words on it). */
    static List<Pick> matched(MatchIndex index, TmdbLists.Page page) {
        List<Pick> out = new ArrayList<>();
        for (TmdbLists.Item it : page.items) {
            Catalog.Title t = index.find(it.tv, it.name, it.original, it.yearNumber());
            if (t != null) out.add(new Pick(t, it, null));
        }
        return out;
    }

    /** TMDB's recommendations' path for a title the family watched (null when TMDB doesn't know it, or can't be asked). */
    private static String recommendations(Context c, Catalog.Title t) {
        int year = 0;
        if (t.year.length() >= 4) {
            try {
                year = Integer.parseInt(t.year.substring(0, 4));
            } catch (NumberFormatException ignored) {
                // no year to go by
            }
        }
        int id = TmdbLists.idOf(c, t.series, t.name, year);
        return id > 0 ? (t.series ? "tv/" : "movie/") + id + "/recommendations" : null;
    }

    /**
     * Pure (but for keywords' ids): a TMDB path with its words made real: {today} as today's date, {today-N} N days
     * before, {today+N} N days on, {a/b} the ids of the keywords named a or b ("123%7C456": either); null when none of a
     * keyword group's names is a keyword TMDB has (the row would be something else).
     */
    static String resolve(Context c, String path, long now) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < path.length()) {
            int open = path.indexOf('{', i);
            if (open < 0) {
                b.append(path, i, path.length());
                break;
            }
            int close = path.indexOf('}', open);
            if (close < 0) return null;
            b.append(path, i, open);
            String word = path.substring(open + 1, close);
            if (word.startsWith("today")) {
                long back = 0;
                if (word.length() > 6 && word.charAt(5) == '-') back = number(word.substring(6)) * 86_400_000L;
                if (word.length() > 6 && word.charAt(5) == '+') back = -number(word.substring(6)) * 86_400_000L;
                b.append(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(now - back)));
            } else {
                StringBuilder ids = new StringBuilder();
                for (String name : word.split("/")) {
                    int id = TmdbLists.keyword(c, name);
                    if (id <= 0) continue;
                    if (ids.length() > 0) ids.append("%7C");
                    ids.append(id);
                }
                if (ids.length() == 0) return null;
                b.append(ids);
            }
            i = close + 1;
        }
        return b.toString();
    }

    private static List<Pick> going(Context c, String kind) {
        Catalog cat = Catalog.get(c);
        List<Pick> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Resume.Point p : Resume.all(c)) {
            Catalog.Title t = cat.title(p.title);
            if (t == null || !fits(t, kind) || !seen.add(t.key)) continue;
            out.add(new Pick(t, null, p));
            if (out.size() >= ROW) break;
        }
        return out;
    }

    private static List<Pick> mine(Context c, String kind) {
        Catalog cat = Catalog.get(c);
        List<Pick> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String k : MyList.keys(c)) {
            Catalog.Title t = cat.title(k);
            if (t == null || !fits(t, kind) || !seen.add(t.key)) continue;
            out.add(new Pick(t, null, null));
            if (out.size() >= ROW) break;
        }
        return out;
    }

    private static boolean fits(Catalog.Title t, String kind) {
        return kind.isEmpty() || kind.equals("all") || kind.equals("tv") == t.series;
    }

    /**
     * The titles the family watched lately, newest first: where they left off now, and the ones before (a film watched to
     * its end leaves Continue watching, but stays here), kept on this TV only, TASTE_KEPT at most.
     */
    public static synchronized List<Catalog.Title> taste(Context c) {
        SharedPreferences p = c.getApplicationContext().getSharedPreferences("editorial", Context.MODE_PRIVATE);
        String had = p.getString("taste", "");
        List<String> keys = new ArrayList<>();
        for (Resume.Point r : Resume.all(c)) if (!r.title.isEmpty() && !keys.contains(r.title)) keys.add(r.title);
        for (String k : had.split("\n")) if (!k.isEmpty() && !keys.contains(k)) keys.add(k);
        while (keys.size() > TASTE_KEPT) keys.remove(keys.size() - 1);
        String now = String.join("\n", keys);
        if (!now.equals(had)) p.edit().putString("taste", now).apply();
        Catalog cat = Catalog.get(c);
        List<Catalog.Title> out = new ArrayList<>();
        for (String k : keys) {
            Catalog.Title t = cat.title(k);
            if (t != null && !out.contains(t)) out.add(t);
        }
        return out;
    }

    /** Pure: the titles among the newest added in the last week (by when the provider added them), in their order. */
    static List<Catalog.Title> fresh(List<Catalog.Title> newest, long now) {
        List<Catalog.Title> out = new ArrayList<>();
        long since = now - 7L * 86_400_000L;
        for (Catalog.Title t : newest) if (t.added >= since && t.added <= now + 86_400_000L) out.add(t);
        return out;
    }

    /** The playlists' newest: films', shows', or both in turn. */
    private static List<Catalog.Title> newest(Catalog cat, String kind) {
        if (kind.equals("movie")) return cat.recent(false, 120);
        if (kind.equals("tv")) return cat.recent(true, 120);
        List<Catalog.Title> f = cat.recent(false, 60), s = cat.recent(true, 60), out = new ArrayList<>(f.size() + s.size());
        for (int i = 0; i < Math.max(f.size(), s.size()); i++) {
            if (i < f.size()) out.add(f.get(i));
            if (i < s.size()) out.add(s.get(i));
        }
        return out;
    }

    private static List<Pick> titles(List<Catalog.Title> list, int max) {
        List<Pick> out = new ArrayList<>(Math.min(max, list.size()));
        for (int i = 0; i < list.size() && out.size() < max; i++) out.add(new Pick(list.get(i), null, null));
        return out;
    }

    /** The titles of the playlists' categories whose names have one of the words (films', shows' or both), at most 5,000. */
    static List<Catalog.Title> byWords(Catalog cat, String words, boolean films, boolean shows) {
        List<Catalog.Title> out = new ArrayList<>();
        if (words == null || words.isEmpty()) return out;
        String[] ws = words.toLowerCase(Locale.ROOT).split(",");
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0 ? !films : !shows) continue;
            for (Catalog.Category k : cat.categories(pass == 1)) {
                if (k.name.equals(Catalog.TORBOX) || !named(k.name, ws)) continue;
                for (Catalog.Title t : k.titles) {
                    if (out.size() >= 5000) return out;
                    out.add(t);
                }
            }
        }
        return out;
    }

    /** Pure: whether a category's name has one of the words as a word of its own ("UK | Action Movies" has "action"). */
    static boolean named(String category, String[] words) {
        String n = " " + spaced(category) + " ";
        for (String w : words) {
            String t = spaced(w).trim();
            if (t.isEmpty()) continue;
            int at = n.indexOf(t);
            while (at >= 0) {
                char before = n.charAt(at - 1 < 0 ? 0 : at - 1), after = at + t.length() < n.length() ? n.charAt(at + t.length()) : ' ';
                if (!Character.isLetterOrDigit(before) && !Character.isLetterOrDigit(after)) return true;
                at = n.indexOf(t, at + 1);
            }
        }
        return false;
    }

    private static String spaced(String s) {
        return s.toLowerCase(Locale.ROOT).replace('|', ' ').replace('-', ' ').replace('/', ' ').replace(':', ' ');
    }

    /** Pure: titles from each stream in turn (films and shows together), none twice and none of skip, at most max. */
    static List<Pick> interleave(List<List<Pick>> streams, int max, Set<String> skip) {
        List<Pick> out = new ArrayList<>();
        Set<String> seen = new HashSet<>(skip == null ? Collections.emptySet() : skip);
        int longest = 0;
        for (List<Pick> s : streams) longest = Math.max(longest, s.size());
        for (int i = 0; i < longest && out.size() < max; i++) {
            for (List<Pick> s : streams) {
                if (i >= s.size() || out.size() >= max) continue;
                Pick p = s.get(i);
                // A title of the playlists' (or TMDB's alone, from a Coming Soon list), none twice.
                if ((p.title != null || p.item != null) && !p.key().isEmpty() && seen.add(p.key())) out.add(p);
            }
        }
        return out;
    }

    /** Pure: a network's name as the app may show it: not empty, and never Apple's or Netflix's. */
    public static boolean allowedName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return !n.contains("apple") && !n.contains("netflix");
    }

    private static Map<String, String> fields(String arg) {
        Map<String, String> out = new HashMap<>();
        for (String f : arg.split(";")) {
            int eq = f.indexOf('=');
            if (eq > 0) out.put(f.substring(0, eq).trim(), f.substring(eq + 1).trim());
        }
        return out;
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
