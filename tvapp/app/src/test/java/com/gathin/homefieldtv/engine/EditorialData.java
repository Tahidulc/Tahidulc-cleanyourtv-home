package com.gathin.homefieldtv.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Editorial rows' made-up TMDB (tests and previews): every list Shelves asks for answered with a turn of the family's
 * own invented titles (so they match the playlists), a few titles no playlist has between them (so matching is tried),
 * a list named as the table expects, every keyword and watched title given an id, and invented names for the networks.
 * Nothing is asked of TMDB.
 */
public final class EditorialData {
    private EditorialData() {}

    /** Titles no playlist has, put into every list (the rows must leave them out). */
    public static final String[] ELSEWHERE = {"A Film Not Here", "Nowhere Show", "Missing Reel"};
    /** The networks' invented names, by the ids CategorySet asks for. */
    public static final Map<Integer, String> NETWORKS = new HashMap<>();

    static {
        String[] names = {"Northline Max", "Harbour+", "Kestrel Prime", "Velvet+"};
        for (int i = 0; i < CategorySet.NETWORKS.length; i++) NETWORKS.put(CategorySet.NETWORKS[i], names[i % names.length]);
    }

    /** Every TMDB list made from these titles (films and shows of the catalogue as loaded). */
    public static void load(List<Catalog.Title> films, List<Catalog.Title> shows) {
        List<Catalog.Title> f = new ArrayList<>(films), s = new ArrayList<>(shows);
        TmdbLists.preview((path, page) -> page(path, page, f, s), NETWORKS);
        Shelves.forget();
    }

    /** As it comes: TMDB asked again (and nothing to ask in a test, so its rows are left out). */
    public static void clear() {
        TmdbLists.preview(null, null);
        Shelves.forget();
    }

    /** No TMDB at all (the launcher gave no key): every TMDB list empty, no network named. */
    public static void none() {
        TmdbLists.preview((path, page) -> null, new HashMap<>());
        Shelves.forget();
    }

    /** These pages exactly, by "path#page" (any other: nothing). */
    static void pages(Map<String, TmdbLists.Page> pages, Map<Integer, String> networks) {
        TmdbLists.preview((path, page) -> pages.get(path + "#" + page), networks);
        Shelves.forget();
    }

    /** A list's page: a turn of the titles of its kind (by its path), with titles no playlist has between them. */
    static TmdbLists.Page page(String path, int page, List<Catalog.Title> films, List<Catalog.Title> shows) {
        String media = TmdbLists.mediaOf(path);
        List<Catalog.Title> from = new ArrayList<>();
        if (!media.equals("tv")) from.addAll(films);
        if (!media.equals("movie") && !path.startsWith("list/")) from.addAll(shows);
        int n = from.size();
        if (n == 0) return new TmdbLists.Page(new ArrayList<>(), page, 1);
        int step = 1;
        for (int k : new int[]{7, 5, 3}) {
            if (gcd(k, n) == 1) {
                step = k;
                break;
            }
        }
        int turn = Math.abs(path.hashCode()) % n;
        List<TmdbLists.Item> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Catalog.Title t = from.get((i * step + turn) % n);
            if (i % 6 == 2) items.add(item(9000 + i, t.series, ELSEWHERE[i % ELSEWHERE.length], ""));
            items.add(item(100 + i, t.series, t.name, t.year, t.poster, t.backdrop));
        }
        String name = path.startsWith("list/") ? "Best Picture Winners" : "";
        int pages = items.size() > 20 ? 2 : 1;
        if (page > pages) return new TmdbLists.Page(new ArrayList<>(), page, pages, name);
        List<TmdbLists.Item> part = page == 1 ? items.subList(0, Math.min(20, items.size())) : items.subList(20, items.size());
        return new TmdbLists.Page(new ArrayList<>(part), page, pages, name);
    }

    /** Previews: TMDB's extras for a title as if it had answered: its logo (an address) and its age rating. */
    public static Tmdb.Extras extras(String logo, String age) {
        return new Tmdb.Extras(logo, null, null, "", age);
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    static TmdbLists.Item item(int id, boolean tv, String name, String year) {
        return item(id, tv, name, year, "", "");
    }

    static TmdbLists.Item item(int id, boolean tv, String name, String year, String poster, String backdrop) {
        return new TmdbLists.Item(id, tv, name, name, year, poster, backdrop, "A story told in the made-up TMDB, of a harbour town and "
                + "the family who keep its lighthouse lit through the longest winter anyone remembers.", "7.6", new int[]{tv ? 18 : 53});
    }
}
