package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Editorial Home, Movies and TV Shows' rows (CategorySet, Shelves): the table's rows in their order, TMDB's lists
 * (made up here, EditorialData) matched to the family's invented playlist by name and year, nothing the playlists don't
 * have, a row with nothing left empty (the page leaves it out), paging, Top 10, networks named as TMDB names them but
 * never Apple's or Netflix's, the genres without TMDB, and the name index over 200,000 titles.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EditorialRowsTest {
    private static final String[] FILMS = {"The Long Harbour (2019)", "Glass Meridian (2021)", "Ember Road (2018)", "Paper Kites (2022)",
            "Midnight Orchard (2020)", "Copper Valley (2017)", "Salt & Starlight (2023)", "Northbound (2016)", "Quiet Meridian (2024)",
            "Lantern Street (2015)", "Fern Hollow (2021)", "Iron Bay (2019)", "Pebble Shore (2022)", "Amber Lane (2020)"};
    private static final String[] SHOWS = {"Harbour Lights", "Signal Lost", "The Quiet County", "Kitchen Table", "Fern & Fable", "Low Tide",
            "Copper Keys", "Night Ferry"};
    private Context c;

    @Before public void setUp() {
        c = RuntimeEnvironment.getApplication();
        Sources.clear(c);
        Sources.put(c, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "test"));
        List<String> rows = new ArrayList<>();
        rows.add("c\tm\t1\tUK | Action Movies");
        rows.add("c\tm\t2\tDrama and mystery");
        rows.add("c\ts\t1\tDrama and crime");
        rows.add("c\ts\t2\tComedy and kids");
        long added = System.currentTimeMillis() / 1000;
        for (int i = 0; i < FILMS.length; i++) {
            String year = FILMS[i].substring(FILMS[i].length() - 5, FILMS[i].length() - 1);
            rows.add("m\t" + (i + 1) + "\t" + FILMS[i] + "\t" + (i % 2 + 1) + "\thttp://pics.invalid/f" + i + ".jpg\t7.1\t" + (added - i * 3600L) * 1000L
                    + "\tmkv\t" + year + "\t\t\t");
        }
        for (int i = 0; i < SHOWS.length; i++) {
            rows.add("s\t" + (i + 1) + "\t" + SHOWS[i] + "\t" + (i % 2 + 1) + "\thttp://pics.invalid/s" + i + ".jpg\t7.4\t" + (added - i * 7200L) * 1000L
                    + "\t" + (2019 + i % 4) + "\tA harbour town keeps its secrets.\t");
        }
        Catalog.get(c).useForPreview(rows, new HashMap<>());
        Resume.clear(c);
        EditorialData.load(Catalog.get(c).films(), Catalog.get(c).series());
    }

    @After public void tearDown() {
        EditorialData.clear();
        Resume.clear(c);
    }

    // ---- the table ---------------------------------------------------------------------------------------------------

    @Test public void eachPageHasTheRowsAskedForInOrder() {
        List<String> home = ids(CategorySet.HOME);
        assertEquals("hero", home.get(0));
        for (String id : new String[]{"continue", "trending", "popular.films", "popular.shows", "new", "top.rated", "top10.films",
                "top10.shows", "genre.action", "genre.comedy", "genre.drama"}) assertTrue(id, home.contains(id));
        assertTrue(home.indexOf("continue") < home.indexOf("trending"));
        assertTrue(home.indexOf("trending") < home.indexOf("popular.films"));
        assertTrue(home.indexOf("top.rated") < home.indexOf("network.49"));
        assertTrue(home.indexOf("network.49") < home.indexOf("genre.action"));

        List<String> movies = ids(CategorySet.MOVIES);
        String[] want = {"trending", "popular", "now.playing", "top.rated", "upcoming", "genre.action", "genre.comedy", "genre.drama",
                "genre.horror", "genre.scifi", "genre.thriller", "genre.animation", "genre.family", "genre.documentary"};
        for (int i = 0; i < want.length; i++) {
            assertTrue(want[i], movies.contains(want[i]));
            if (i > 0) assertTrue(want[i - 1] + " before " + want[i], movies.indexOf(want[i - 1]) < movies.indexOf(want[i]));
        }

        List<String> shows = ids(CategorySet.SHOWS);
        String[] tv = {"trending", "popular", "airing.today", "on.the.air", "top.rated", "network.49", "genre.drama"};
        for (int i = 0; i < tv.length; i++) {
            assertTrue(tv[i], shows.contains(tv[i]));
            if (i > 0) assertTrue(tv[i - 1] + " before " + tv[i], shows.indexOf(tv[i - 1]) < shows.indexOf(tv[i]));
        }
    }

    @Test public void theTableIsOnePlaceWithUniqueIdsAndNoBrandsOfItsOwn() {
        for (String page : new String[]{CategorySet.HOME, CategorySet.MOVIES, CategorySet.SHOWS}) {
            Set<String> seen = new HashSet<>();
            int order = -1;
            for (CategorySet.Entry e : CategorySet.of(page)) {
                assertTrue(page + " has " + e.id + " twice", seen.add(e.id));
                assertTrue(e.order > order);
                order = e.order;
                String words = (e.title + " " + e.source).toLowerCase(Locale.ROOT);
                assertFalse(e.id, words.contains("apple") || words.contains("netflix") || words.contains("debrid"));
                // Netflix's network (213) and Apple TV+'s (2552) are never asked for.
                assertFalse(e.id, e.source.equals("network:213") || e.source.equals("network:2552"));
                assertTrue(e.id, e.kind == CategorySet.HERO || !e.title.isEmpty() || e.source.startsWith("network:"));
            }
        }
        for (int id : CategorySet.NETWORKS) assertTrue(id != 213 && id != 2552);
        assertEquals(2, CategorySet.parts("tmdb:trending/all/day|new:all").size());
        assertEquals(2, CategorySet.parts("tmdb:movie/top_rated+tmdb:tv/top_rated").get(0).size());
        assertTrue(CategorySet.tmdb("tmdb:trending/all/day|new:all"));
        assertFalse(CategorySet.tmdb("new:all"));
        assertFalse(CategorySet.tmdb("continue:movie"));
    }

    private static List<String> ids(String page) {
        List<String> out = new ArrayList<>();
        for (CategorySet.Entry e : CategorySet.of(page)) out.add(e.id);
        return out;
    }

    private static CategorySet.Entry entry(String page, String id) {
        for (CategorySet.Entry e : CategorySet.of(page)) if (e.id.equals(id)) return e;
        throw new AssertionError(id);
    }

    // ---- TMDB's lists matched ----------------------------------------------------------------------------------------

    @Test public void aTmdbListIsMatchedToWhatThePlaylistsHoldAndNothingElse() {
        Shelves.Shelf s = Shelves.make(c, entry(CategorySet.MOVIES, "popular"));
        assertFalse(s.isEmpty());
        Set<String> keys = new HashSet<>();
        for (Shelves.Pick p : s.picks) {
            assertNotNull(p.title);
            assertFalse(p.title.series);
            assertTrue("each title once", keys.add(p.title.key));
            for (String no : EditorialData.ELSEWHERE) assertFalse(no.equals(p.title.name));
            assertNotNull(p.item);
        }
        // Films and shows in turn where a row takes both.
        Shelves.Shelf mixed = Shelves.make(c, entry(CategorySet.HOME, "top.rated"));
        assertFalse(mixed.picks.get(0).title.series);
        assertTrue(mixed.picks.get(1).title.series);
        // The second page is read as the remote nears the end.
        Shelves.Shelf shows = Shelves.make(c, entry(CategorySet.SHOWS, "popular"));
        assertTrue(shows.picks.size() <= SHOWS.length);
    }

    @Test public void moreReadsTheNextPage() {
        // Twenty-two titles, two pages: the first gives at most twenty, more() the rest.
        CategorySet.Entry e = new CategorySet.Entry("test", "both", "Both", "tmdb:trending/all/day", 0, CategorySet.POSTERS);
        Map<String, TmdbLists.Page> pages = new HashMap<>();
        List<TmdbLists.Item> one = new ArrayList<>(), two = new ArrayList<>();
        List<Catalog.Title> all = new ArrayList<>(Catalog.get(c).films());
        all.addAll(Catalog.get(c).series());
        for (int i = 0; i < 12; i++) one.add(EditorialData.item(i + 1, all.get(i).series, all.get(i).name, all.get(i).year));
        for (int i = 12; i < all.size(); i++) two.add(EditorialData.item(i + 1, all.get(i).series, all.get(i).name, all.get(i).year));
        pages.put(TmdbLists.previewKey("trending/all/day", 1), new TmdbLists.Page(one, 1, 2));
        pages.put(TmdbLists.previewKey("trending/all/day", 2), new TmdbLists.Page(two, 2, 2));
        TmdbLists.preview(pages, new HashMap<>());
        Shelves.Shelf s = Shelves.make(c, e);
        // Twelve on the first page: enough, so the second waits for more().
        assertEquals(12, s.picks.size());
        assertTrue(s.more());
        Shelves.Shelf after = Shelves.more(c, s);
        assertEquals(all.size(), after.picks.size());
        assertFalse(after.more());
        assertSame(after, Shelves.more(c, after));
    }

    @Test public void aRowWithNothingInItIsEmptySoThePageLeavesItOut() {
        // A list of titles no playlist has.
        Map<String, TmdbLists.Page> pages = new HashMap<>();
        List<TmdbLists.Item> none = new ArrayList<>();
        for (int i = 0; i < 20; i++) none.add(EditorialData.item(i + 1, false, "Elsewhere " + i, "2020"));
        pages.put(TmdbLists.previewKey("movie/upcoming?region=GB", 1), new TmdbLists.Page(none, 1, 1));
        TmdbLists.preview(pages, new HashMap<>());
        assertTrue(Shelves.make(c, entry(CategorySet.MOVIES, "upcoming")).isEmpty());
        // Nothing to go on for Continue watching yet either.
        assertTrue(Shelves.local(c, entry(CategorySet.HOME, "continue")).isEmpty());
        Resume.save(c, new Resume.Point("p0|m|3", "", "Ember Road", "", "", 20 * 60_000L, 100 * 60_000L, System.currentTimeMillis()));
        Shelves.Shelf going = Shelves.local(c, entry(CategorySet.HOME, "continue"));
        assertEquals(1, going.picks.size());
        assertEquals("Ember Road", going.picks.get(0).title.name);
        assertNotNull(going.picks.get(0).point);
        // Films only on Movies' row, so nothing on TV Shows'.
        assertTrue(Shelves.local(c, entry(CategorySet.SHOWS, "continue")).isEmpty());
    }

    @Test public void topTenIsTenInOrder() {
        Shelves.Shelf s = Shelves.make(c, entry(CategorySet.HOME, "top10.films"));
        assertEquals(10, s.picks.size());
        assertFalse(s.more());
        for (Shelves.Pick p : s.picks) assertFalse(p.title.series);
        // In TMDB's order.
        TmdbLists.Page first = TmdbLists.page(c, "trending/movie/week", 1);
        List<String> want = new ArrayList<>();
        for (TmdbLists.Item it : first.items) {
            boolean ours = true;
            for (String no : EditorialData.ELSEWHERE) ours &= !no.equals(it.name);
            if (ours && !want.contains(it.name)) want.add(it.name);
        }
        for (int i = 0; i < Math.min(10, want.size()); i++) assertEquals(want.get(i), s.picks.get(i).title.name);
    }

    @Test public void networksAreNamedAsTmdbNamesThemButNeverAppleOrNetflix() {
        Shelves.Shelf s = Shelves.make(c, entry(CategorySet.SHOWS, "network.49"));
        assertEquals(EditorialData.NETWORKS.get(49), s.title);
        assertFalse(s.isEmpty());
        for (Shelves.Pick p : s.picks) assertTrue(p.title.series);
        assertFalse(Shelves.allowedName("Apple TV+"));
        assertFalse(Shelves.allowedName("Netflix"));
        assertFalse(Shelves.allowedName(""));
        assertTrue(Shelves.allowedName("Harbour+"));
        // Whatever TMDB calls a network, an Apple or Netflix one is left out.
        Map<Integer, String> names = new HashMap<>(EditorialData.NETWORKS);
        names.put(49, "Apple TV+");
        Map<String, TmdbLists.Page> pages = new HashMap<>();
        List<TmdbLists.Item> items = new ArrayList<>();
        for (Catalog.Title t : Catalog.get(c).series()) items.add(EditorialData.item(t.name.length(), true, t.name, t.year));
        pages.put(TmdbLists.previewKey("discover/tv?with_networks=49&sort_by=popularity.desc", 1), new TmdbLists.Page(items, 1, 1));
        TmdbLists.preview(pages, names);
        assertTrue(Shelves.make(c, entry(CategorySet.SHOWS, "network.49")).isEmpty());
        // A network TMDB can't name (no key) is left out too.
        TmdbLists.preview(pages, new HashMap<>());
        assertTrue(Shelves.make(c, entry(CategorySet.SHOWS, "network.49")).isEmpty());
    }

    @Test public void withoutTmdbGenresComeFromThePlaylistsCategoriesAndTheHeroFromTheNewest() {
        EditorialData.none();
        Shelves.Shelf action = Shelves.make(c, entry(CategorySet.MOVIES, "genre.action"));
        assertFalse(action.isEmpty());
        for (Shelves.Pick p : action.picks) assertEquals("UK | Action Movies", p.title.category);
        assertNotNull(action.all);
        Shelves.Shelf comedy = Shelves.make(c, entry(CategorySet.SHOWS, "genre.comedy"));
        for (Shelves.Pick p : comedy.picks) assertEquals("Comedy and kids", p.title.category);
        assertTrue(Shelves.make(c, entry(CategorySet.MOVIES, "genre.horror")).isEmpty());
        assertTrue(Shelves.make(c, entry(CategorySet.HOME, "trending")).isEmpty());
        Shelves.Shelf hero = Shelves.make(c, entry(CategorySet.HOME, "hero"));
        assertFalse(hero.isEmpty());
        // The playlists' newest, films and shows in turn.
        assertEquals("The Long Harbour", hero.picks.get(0).title.name);
        assertEquals("Harbour Lights", hero.picks.get(1).title.name);
        assertTrue(Shelves.named("UK | Action Movies", new String[]{"action"}));
        assertFalse(Shelves.named("Transaction Films", new String[]{"action"}));
        assertTrue(Shelves.named("Sci-Fi", new String[]{"sci-fi"}));
    }

    @Test public void localRowsAreTheFamilysOwnLists() {
        Shelves.Shelf fresh = Shelves.local(c, entry(CategorySet.MOVIES, "recent"));
        assertEquals("The Long Harbour", fresh.picks.get(0).title.name);
        assertEquals(FILMS.length, fresh.total());
        // The hero's first local choice while TMDB is read.
        assertFalse(Shelves.local(c, entry(CategorySet.HOME, "hero")).isEmpty());
        // A TMDB-only row has nothing local.
        assertTrue(Shelves.local(c, entry(CategorySet.HOME, "trending")).isEmpty());
        Shelves.Shelf tiles = Shelves.local(c, entry(CategorySet.MOVIES, "categories"));
        assertEquals(2, tiles.picks.size());
        assertEquals("UK | Action Movies", tiles.picks.get(0).name());
        assertEquals(CategorySet.MOODS.size(), Shelves.local(c, entry(CategorySet.HOME, "moods")).picks.size());
        MyList.toggle(c, "p0|s|2");
        try {
            assertEquals("Signal Lost", Shelves.local(c, entry(CategorySet.SHOWS, "mylist")).picks.get(0).title.name);
            assertTrue(Shelves.local(c, entry(CategorySet.MOVIES, "mylist")).isEmpty());
        } finally {
            MyList.toggle(c, "p0|s|2");
        }
    }

    @Test public void aMoodsTitles() {
        List<Catalog.Title> laugh = Shelves.mood(c, CategorySet.MOODS.get(2), "all");
        assertFalse(laugh.isEmpty());
        assertEquals("genre:tv=35;words=comedy,comedies,sitcom", Shelves.only("genre:movie=35;tv=35;words=comedy,comedies,sitcom", "tv"));
        assertEquals("tmdb:tv/top_rated", Shelves.only("tmdb:movie/top_rated", "tv"));
    }

    // ---- the name index --------------------------------------------------------------------------------------------------

    @Test public void namesAreFoundAsTmdbWritesThem() {
        assertEquals("longharbour", TitleIndex.squash("The Long Harbour", false));
        assertEquals("saltandstarlight", TitleIndex.squash("Salt & Starlight", false));
        assertEquals("amelie", TitleIndex.squash("Amélie", false));
        assertEquals("theatre", TitleIndex.squash("Theatre", false));
        assertEquals("officeus", TitleIndex.squash("The Office (US)", false));
        assertEquals("office", TitleIndex.squash("The Office (US)", true));
        assertEquals("500dayssummer", TitleIndex.squash("(500) Days: Summer", true));
        assertEquals("dontlookup", TitleIndex.squash("Don't Look Up", false));
        assertEquals(TitleIndex.hash("Salt and Starlight", false), TitleIndex.hash("SALT & STARLIGHT!", false));
        assertEquals(0, TitleIndex.hash("—", false));

        TitleIndex index = TitleIndex.of(Catalog.get(c));
        assertEquals("Salt & Starlight", index.find(false, "Salt and Starlight", "", 2023).name);
        // A year either side, not two.
        assertNotNull(index.find(false, "Ember Road", "", 2019));
        assertNull(index.find(false, "Ember Road", "", 2021));
        // Any year for a show (its playlist may date a later season), and its original name too.
        assertEquals("Low Tide", index.find(true, "Low Tide", "", 2001).name);
        assertEquals("Night Ferry", index.find(true, "Le Ferry de Nuit", "Night Ferry", 0).name);
        // Films and shows apart.
        assertNull(index.find(true, "Ember Road", "", 0));
        assertNull(index.find(false, "Nowhere At All", "", 0));
        assertSame(index, TitleIndex.of(Catalog.get(c)));
    }

    @Test public void twoHundredThousandTitlesAreIndexedSmallAndQuickly() {
        List<Catalog.Title> films = new ArrayList<>(), shows = new ArrayList<>();
        String[] a = {"Harbour", "Meridian", "Ember", "Kite", "Orchard", "Valley", "Starlight", "North", "Lantern", "Hollow"};
        for (int i = 0; i < 160_000; i++) {
            films.add(new Catalog.Title("p0", false, String.valueOf(i), a[i % 10] + " " + a[(i / 10) % 10] + " " + i + " (" + (1950 + i % 70) + ")",
                    "Films", "", "", "", "", 0, "mkv", "", "", "", ""));
        }
        for (int i = 0; i < 40_000; i++) {
            shows.add(new Catalog.Title("p0", true, String.valueOf(i), "The " + a[i % 10] + " Show " + i, "Shows", "", "", "", "", 0, "", "", "", "",
                    "2020"));
        }
        long t0 = System.nanoTime();
        TitleIndex index = TitleIndex.of(films, shows);
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.println("TitleIndex: 200,000 titles indexed in " + Math.round(ms) + " ms (JVM), " + index.size() + " keys");
        assertTrue("indexed in " + ms + " ms", ms < 4000);
        // At most two longs a title (a name with brackets: also without them).
        assertTrue(index.size() <= 2 * 200_000);
        long t1 = System.nanoTime();
        int found = 0;
        for (int i = 0; i < 2000; i++) {
            int k = i * 79;
            if (index.find(false, a[k % 10] + " " + a[(k / 10) % 10] + " " + k, "", 1950 + k % 70) == films.get(k)) found++;
        }
        double each = (System.nanoTime() - t1) / 1e6 / 2000;
        assertEquals(2000, found);
        assertTrue("a find took " + each + " ms", each < 1.0);
        assertEquals(shows.get(777), index.find(true, "The North Show 777", "", 0));
    }

    // ---- TMDB's answer read ----------------------------------------------------------------------------------------------

    @Test public void aListPageIsReadAsTmdbGivesIt() throws Exception {
        JSONArray rs = new JSONArray()
                .put(new JSONObject().put("id", 1).put("media_type", "movie").put("title", "Ember Road").put("original_title", "Ember Road")
                        .put("release_date", "2018-03-02").put("poster_path", "/p.jpg").put("backdrop_path", "/b.jpg").put("vote_average", 7.4)
                        .put("genre_ids", new JSONArray().put(28).put(18)))
                .put(new JSONObject().put("id", 2).put("media_type", "tv").put("name", "Low Tide").put("first_air_date", "2021-01-01"))
                .put(new JSONObject().put("id", 3).put("media_type", "person").put("name", "Someone"))
                .put(new JSONObject().put("id", 0).put("media_type", "movie").put("title", "No id"))
                .put(new JSONObject().put("id", 4).put("media_type", "movie").put("title", "").put("release_date", ""));
        JSONObject j = new JSONObject().put("page", 1).put("total_pages", 480).put("results", rs);
        TmdbLists.Page p = TmdbLists.parse(j, "", 1);
        assertEquals(2, p.items.size());
        TmdbLists.Item film = p.items.get(0);
        assertFalse(film.tv);
        assertEquals("Ember Road", film.name);
        assertEquals(2018, film.yearNumber());
        assertEquals(Tmdb.image("/p.jpg", "w342"), film.poster);
        assertEquals(Tmdb.image("/b.jpg", "w1280"), film.backdrop);
        assertEquals("7.4", film.rating);
        assertEquals(2, film.genres.length);
        assertTrue(p.items.get(1).tv);
        assertEquals("", p.items.get(1).poster);
        // At most ten pages of a list are read.
        assertEquals(TmdbLists.MAX_PAGES, p.pages);
        // A show list says nothing of each one's kind: all shows.
        assertTrue(TmdbLists.parse(new JSONObject().put("results", new JSONArray().put(new JSONObject().put("id", 9).put("name", "Signal Lost"))),
                "tv", 1).items.get(0).tv);
        assertEquals("", TmdbLists.mediaOf("trending/all/day"));
        assertEquals("tv", TmdbLists.mediaOf("discover/tv?with_networks=4"));
        assertEquals("movie", TmdbLists.mediaOf("movie/now_playing?region=GB"));
        assertTrue(TmdbLists.keepMs("trending/all/day") < TmdbLists.keepMs("movie/popular"));
        assertTrue(TmdbLists.keepMs("movie/popular") < TmdbLists.keepMs("discover/movie?with_genres=28"));
    }
}
