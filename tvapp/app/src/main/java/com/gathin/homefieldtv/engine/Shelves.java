package com.gathin.homefieldtv.engine;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Each row of CategorySet made into its titles, from what the app already has: the playlists' films and series (and the
 * family's own TorBox titles), where the family left off, My List, and TMDB's lists (trending, popular, a network's, a
 * genre's) matched to what the playlists hold (TitleIndex), so every card plays. A row with nothing in it is empty and
 * the page leaves it out. A TMDB row is read a page at a time: the first pages as it is made, the next as the remote
 * nears its end (more()), at most TmdbLists.MAX_PAGES. Rows from the family's own lists (local) are quick and can be
 * made on the main thread; a TMDB row (CategorySet.Entry.tmdb) only off it.
 */
public final class Shelves {
    private Shelves() {}

    /** How many cards a row of the family's own lists has (its "All" card opens the rest); a TMDB row's most. */
    public static final int ROW = 40, MOST = 80;
    /** A TMDB row's first pages are read until it has this many, or FIRST_PAGES pages are read. */
    static final int ENOUGH = 10, FIRST_PAGES = 3;

    /** One card of a row: a title (with TMDB's word on it, where from a list), where it was left, a category, a mood. */
    public static final class Pick {
        public final Catalog.Title title;
        public final TmdbLists.Item item;
        public final Resume.Point point;
        public final Catalog.Category category;
        public final CategorySet.Mood mood;

        Pick(Catalog.Title title, TmdbLists.Item item, Resume.Point point, Catalog.Category category, CategorySet.Mood mood) {
            this.title = title;
            this.item = item;
            this.point = point;
            this.category = category;
            this.mood = mood;
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

        /** Its name as the card says it. */
        public String name() {
            if (mood != null) return mood.name;
            if (category != null) return category.name;
            return title == null ? "" : title.name;
        }
    }

    /** A row made: its entry, its title (a network's as TMDB names it), its cards, its whole list (local rows), its pages. */
    public static final class Shelf {
        public final CategorySet.Entry entry;
        public final String title;
        public final List<Pick> picks;
        /** A local row's whole list, for its "All" card (null for a TMDB row, which pages instead). */
        public final List<Catalog.Title> all;
        /** Each TMDB part's next page to read (beyond its last: none), as made. */
        final String[] paths;
        final int[] next, pages;
        /** When it was made (ms), and for which catalogue (its lists). */
        final long at;
        final List<Catalog.Title> madeFor;

        Shelf(CategorySet.Entry entry, String title, List<Pick> picks, List<Catalog.Title> all, String[] paths, int[] next,
              int[] pages, List<Catalog.Title> madeFor) {
            this.entry = entry;
            this.title = title == null ? "" : title;
            this.picks = Collections.unmodifiableList(picks);
            this.all = all;
            this.paths = paths == null ? new String[0] : paths;
            this.next = next == null ? new int[0] : next;
            this.pages = pages == null ? new int[0] : pages;
            this.at = System.currentTimeMillis();
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
        return s == null || System.currentTimeMillis() - s.at > 10 * 60_000L;
    }

    public static void forget() {
        KEPT.clear();
    }

    /**
     * A row made from the family's own lists only (its TMDB parts left out): quick, for the main thread. A "|" of
     * choices takes its first local one that has anything (the hero's "new:all" while TMDB's trending is read).
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
        return new Shelf(e, e.title, new ArrayList<>(), null, null, null, null, Catalog.get(c).films());
    }

    /** One choice of a source: its "+" parts taken in turn. */
    private static Shelf make(Context c, CategorySet.Entry e, List<String> parts) {
        Catalog cat = Catalog.get(c);
        String title = e.title;
        List<List<Pick>> streams = new ArrayList<>();
        List<Catalog.Title> all = null;
        List<String> paths = new ArrayList<>();
        List<Integer> next = new ArrayList<>(), pages = new ArrayList<>();
        boolean top = e.kind == CategorySet.TOP10;
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
                case "torbox": {
                    List<Catalog.Title> got = new ArrayList<>();
                    if (!arg.equals("tv")) got.addAll(cat.own(false));
                    if (!arg.equals("movie")) got.addAll(cat.own(true));
                    if (arg.equals("all")) got.sort((a, b) -> Long.compare(b.added, a.added));
                    all = got;
                    streams.add(titles(got, ROW));
                    break;
                }
                case "categories":
                    streams.add(categories(cat, arg.equals("tv")));
                    break;
                case "moods":
                    streams.add(moods(cat, arg));
                    break;
                case "top10":
                    streams.add(topTen(c, arg.equals("tv")));
                    break;
                case "tmdb":
                    tmdb(c, arg, streams, paths, next, pages);
                    break;
                case "network": {
                    int id = number(arg);
                    String name = id > 0 ? TmdbLists.network(c, id) : "";
                    if (!allowedName(name)) return new Shelf(e, e.title, new ArrayList<>(), null, null, null, null, cat.films());
                    if (title.isEmpty()) title = name;
                    tmdb(c, "discover/tv?with_networks=" + id + "&sort_by=popularity.desc", streams, paths, next, pages);
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
        List<Pick> picks = interleave(streams, top ? 10 : MOST);
        if (top && picks.size() < 3) picks.clear();
        int[] n = new int[next.size()], p = new int[pages.size()];
        for (int i = 0; i < n.length; i++) {
            n[i] = top ? Integer.MAX_VALUE : next.get(i);
            p[i] = pages.get(i);
        }
        return new Shelf(e, title, picks, all, paths.toArray(new String[0]), n, p, cat.films());
    }

    /**
     * A TMDB row with its next pages read and matched (a page or two from each part with more), its cards after the ones
     * it had (none twice); the row as it was when nothing more is to be had. Off the main thread.
     */
    public static Shelf more(Context c, Shelf s) {
        if (!s.more()) return s;
        TitleIndex index = TitleIndex.of(Catalog.get(c));
        List<Pick> picks = new ArrayList<>(s.picks);
        Set<String> seen = new HashSet<>();
        for (Pick p : picks) if (p.title != null) seen.add(p.title.key);
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
                for (Pick p : matched(index, page)) {
                    if (picks.size() >= MOST) break;
                    if (seen.add(p.title.key)) {
                        picks.add(p);
                        added++;
                    }
                }
            }
        }
        Shelf out = new Shelf(s.entry, s.title, picks, s.all, s.paths, next, s.pages, s.madeFor);
        KEPT.put(s.entry.toString(), out);
        return out;
    }

    // ---- the parts ---------------------------------------------------------------------------------------------------

    /** A TMDB list's first pages, matched, as one stream (its path and next page kept for more()). */
    private static void tmdb(Context c, String path, List<List<Pick>> streams, List<String> paths, List<Integer> next,
                             List<Integer> pages) {
        List<Pick> out = new ArrayList<>();
        TitleIndex index = null;
        int page = 1, total = 1;
        Set<String> seen = new HashSet<>();
        for (; page <= total && page <= FIRST_PAGES && out.size() < ENOUGH; page++) {
            TmdbLists.Page got = TmdbLists.page(c, path, page);
            if (got == null) {
                total = 0;
                break;
            }
            total = got.pages;
            if (index == null) index = TitleIndex.of(Catalog.get(c));
            for (Pick p : matched(index, got)) if (seen.add(p.title.key)) out.add(p);
        }
        streams.add(out);
        paths.add(path);
        next.add(page);
        pages.add(total);
    }

    /** Pure: a page's titles that the playlists have, in its order (each as the playlists' title, TMDB's words on it). */
    static List<Pick> matched(TitleIndex index, TmdbLists.Page page) {
        List<Pick> out = new ArrayList<>();
        for (TmdbLists.Item it : page.items) {
            Catalog.Title t = index.find(it.tv, it.name, it.original, it.yearNumber());
            if (t != null) out.add(new Pick(t, it, null, null, null));
        }
        return out;
    }

    /**
     * Ten in order: TMDB's trending this week, matched to the playlists (the app has no chart of its own yet; TopTen,
     * where it comes, takes this place).
     */
    static List<Pick> topTen(Context c, boolean tv) {
        List<List<Pick>> one = new ArrayList<>();
        tmdb(c, tv ? "trending/tv/week" : "trending/movie/week", one, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        List<Pick> got = one.get(0);
        if (got.size() < 10) {
            // A short first page or two: read on for ten.
            TitleIndex index = TitleIndex.of(Catalog.get(c));
            Set<String> seen = new HashSet<>();
            for (Pick p : got) seen.add(p.title.key);
            for (int page = FIRST_PAGES + 1; page <= 5 && got.size() < 10; page++) {
                TmdbLists.Page more = TmdbLists.page(c, tv ? "trending/tv/week" : "trending/movie/week", page);
                if (more == null || more.items.isEmpty()) break;
                for (Pick p : matched(index, more)) if (seen.add(p.title.key)) got.add(p);
            }
        }
        return got.size() > 10 ? new ArrayList<>(got.subList(0, 10)) : got;
    }

    private static List<Pick> going(Context c, String kind) {
        Catalog cat = Catalog.get(c);
        List<Pick> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Resume.Point p : Resume.all(c)) {
            Catalog.Title t = cat.title(p.title);
            if (t == null || !fits(t, kind) || !seen.add(t.key)) continue;
            out.add(new Pick(t, null, p, null, null));
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
            out.add(new Pick(t, null, null, null, null));
            if (out.size() >= ROW) break;
        }
        return out;
    }

    private static boolean fits(Catalog.Title t, String kind) {
        return kind.isEmpty() || kind.equals("all") || kind.equals("tv") == t.series;
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
        for (int i = 0; i < list.size() && out.size() < max; i++) out.add(new Pick(list.get(i), null, null, null, null));
        return out;
    }

    private static List<Pick> categories(Catalog cat, boolean series) {
        List<Pick> out = new ArrayList<>();
        for (Catalog.Category k : cat.categories(series)) {
            if (k.titles.isEmpty() || k.name.equals(Catalog.TORBOX)) continue;
            out.add(new Pick(k.titles.get(0), null, null, k, null));
            if (out.size() >= ROW) break;
        }
        return out;
    }

    private static List<Pick> moods(Catalog cat, String kind) {
        List<Pick> out = new ArrayList<>();
        boolean films = !cat.films().isEmpty(), shows = !cat.series().isEmpty();
        if (kind.equals("movie") ? !films : kind.equals("tv") ? !shows : !films && !shows) return out;
        for (CategorySet.Mood m : CategorySet.MOODS) out.add(new Pick(null, null, null, null, m));
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

    /** Pure: titles from each stream in turn (films and shows together), none twice, at most max. */
    static List<Pick> interleave(List<List<Pick>> streams, int max) {
        List<Pick> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int longest = 0;
        for (List<Pick> s : streams) longest = Math.max(longest, s.size());
        for (int i = 0; i < longest && out.size() < max; i++) {
            for (List<Pick> s : streams) {
                if (i >= s.size() || out.size() >= max) continue;
                Pick p = s.get(i);
                String key = p.title != null && p.category == null ? p.title.key : p.mood != null ? "mood:" + p.mood.name
                        : p.category != null ? "cat:" + p.category.name : "";
                if (key.isEmpty() || seen.add(key)) out.add(p);
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

    /**
     * A mood's titles (its tile opened): TMDB's popular ones of its genres matched, a few pages of each, else the
     * playlists' categories named for it; films', shows' or both (kind "movie", "tv", "all"). Off the main thread.
     */
    public static List<Catalog.Title> mood(Context c, CategorySet.Mood m, String kind) {
        String source = m.source;
        if (kind.equals("movie")) source = only(source, "movie");
        else if (kind.equals("tv")) source = only(source, "tv");
        CategorySet.Entry e = new CategorySet.Entry("mood", m.name, m.name, source, 0, CategorySet.POSTERS);
        Shelf s = make(c, e);
        for (int i = 0; i < 3 && s.more(); i++) s = more(c, s);
        KEPT.remove(e.toString());
        if (s.all != null && !s.all.isEmpty() && s.picks.size() <= ROW) return s.all;
        List<Catalog.Title> out = new ArrayList<>();
        for (Pick p : s.picks) if (p.title != null) out.add(p.title);
        return out;
    }

    /** Pure: a genre source with only its films' or shows' part ("genre:movie=35;tv=35;words=comedy", "tv"). */
    static String only(String source, String kind) {
        if (!source.startsWith("genre:")) return kind.equals("tv") && source.startsWith("tmdb:movie/") ? source.replace("tmdb:movie/", "tmdb:tv/")
                : source;
        StringBuilder b = new StringBuilder("genre:");
        for (String f : source.substring(6).split(";")) {
            if (f.startsWith("movie=") && !kind.equals("movie") || f.startsWith("tv=") && !kind.equals("tv")) continue;
            if (b.length() > 6) b.append(';');
            b.append(f);
        }
        return b.toString();
    }
}
