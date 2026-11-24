package com.gathin.homefieldtv.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Editorial rows' made-up TMDB (tests and previews): every list CategorySet asks for as preview pages, each a turn
 * of the family's own invented titles (so they match the playlists) with a few titles no playlist has between them (so
 * matching is tried), and invented names for the networks. Nothing is asked of TMDB.
 */
public final class EditorialData {
    private EditorialData() {}

    /** Titles no playlist has, put into every list (the rows must leave them out). */
    public static final String[] ELSEWHERE = {"A Film Not Here", "Nowhere Show", "Missing Reel"};
    /** The networks' invented names, by the ids CategorySet asks for. */
    public static final Map<Integer, String> NETWORKS = new HashMap<>();

    static {
        String[] names = {"Harbour+", "Northline Max", "Velvet TV", "Kestrel One", "Lantern Plus", "Fieldside", "Copper Atlantic",
                "Riverside One", "Meadow 1", "Tidal 4"};
        for (int i = 0; i < CategorySet.NETWORKS.length; i++) NETWORKS.put(CategorySet.NETWORKS[i], names[i % names.length]);
    }

    /** Every TMDB list the table asks for, from these titles (films and shows of the catalogue as loaded). */
    public static void load(List<Catalog.Title> films, List<Catalog.Title> shows) {
        Map<String, TmdbLists.Page> pages = new HashMap<>();
        Set<String> paths = new LinkedHashSet<>();
        for (String page : new String[]{CategorySet.HOME, CategorySet.MOVIES, CategorySet.SHOWS}) {
            for (CategorySet.Entry e : CategorySet.of(page)) paths.addAll(paths(e.source));
        }
        for (CategorySet.Mood m : CategorySet.MOODS) {
            paths.addAll(paths(m.source));
            paths.addAll(paths(Shelves.only(m.source, "tv")));
        }
        int turn = 0;
        for (String path : paths) {
            String media = TmdbLists.mediaOf(path);
            List<TmdbLists.Item> items = new ArrayList<>();
            List<Catalog.Title> from = new ArrayList<>();
            if (!media.equals("tv")) from.addAll(films);
            if (!media.equals("movie")) from.addAll(shows);
            int n = from.size(), step = 1;
            for (int k : new int[]{7, 5, 3}) if (gcd(k, n) == 1) {
                step = k;
                break;
            }
            for (int i = 0; i < n; i++) {
                Catalog.Title t = from.get((i * step + turn * 3) % n);
                if (i % 6 == 2) items.add(item(9000 + i, t.series, ELSEWHERE[i % ELSEWHERE.length], ""));
                items.add(item(100 + i + turn * 50, t.series, t.name, t.year, t.poster, t.backdrop));
            }
            turn++;
            // One page of twenty (as TMDB gives them), the rest a second page.
            List<TmdbLists.Item> first = items.subList(0, Math.min(20, items.size()));
            pages.put(TmdbLists.previewKey(path, 1), new TmdbLists.Page(new ArrayList<>(first), 1, items.size() > 20 ? 2 : 1));
            if (items.size() > 20) pages.put(TmdbLists.previewKey(path, 2), new TmdbLists.Page(new ArrayList<>(items.subList(20, items.size())), 2, 2));
        }
        TmdbLists.preview(pages, NETWORKS);
        Shelves.forget();
    }

    /** As it comes: TMDB asked again (and nothing to ask in a test, so its rows are left out). */
    public static void clear() {
        TmdbLists.preview(null, null);
        Shelves.forget();
    }

    /** No TMDB at all (the launcher gave no key): every TMDB list empty. */
    public static void none() {
        TmdbLists.preview(new HashMap<>(), new HashMap<>());
        Shelves.forget();
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    static TmdbLists.Item item(int id, boolean tv, String name, String year) {
        return item(id, tv, name, year, "", "");
    }

    static TmdbLists.Item item(int id, boolean tv, String name, String year, String poster, String backdrop) {
        return new TmdbLists.Item(id, tv, name, name, year, poster, backdrop, "A story told in the made-up TMDB.", "7.6", new int[]{18});
    }

    /** Pure: the TMDB lists a source asks for, as Shelves asks for them. */
    public static List<String> paths(String source) {
        List<String> out = new ArrayList<>();
        for (List<String> choice : CategorySet.parts(source)) {
            for (String part : choice) {
                int colon = part.indexOf(':');
                String kind = colon < 0 ? part : part.substring(0, colon), arg = colon < 0 ? "" : part.substring(colon + 1);
                switch (kind) {
                    case "tmdb":
                        out.add(arg);
                        break;
                    case "top10":
                        out.add(arg.equals("tv") ? "trending/tv/week" : "trending/movie/week");
                        break;
                    case "network":
                        out.add("discover/tv?with_networks=" + arg + "&sort_by=popularity.desc");
                        break;
                    case "genre":
                        for (String f : arg.split(";")) {
                            if (f.startsWith("movie=")) out.add("discover/movie?with_genres=" + f.substring(6) + "&sort_by=popularity.desc&vote_count.gte=100");
                            if (f.startsWith("tv=")) out.add("discover/tv?with_genres=" + f.substring(3) + "&sort_by=popularity.desc&vote_count.gte=50");
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        return out;
    }
}
