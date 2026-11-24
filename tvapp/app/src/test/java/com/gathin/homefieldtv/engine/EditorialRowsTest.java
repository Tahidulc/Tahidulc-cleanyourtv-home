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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Editorial Home, Movies and TV Shows' rows (CategorySet, Shelves): the other film app's rows in its order, TMDB's
 * lists (made up here, EditorialData) matched to the family's invented playlist by name and year, nothing the playlists
 * don't have, a row with nothing in it left empty (the page leaves it out), paging, keywords found by name, a list only
 * while it is the list it was, Because you watched and the recommendations from what the family watched, networks named
 * as TMDB names them but never Apple's or Netflix's, the playlists' own when TMDB can't be asked, and the name index over
 * 200,000 titles.
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
        rows.add("c\tm\t1\tUK | Comedy Movies");
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
        c.getSharedPreferences("editorial", Context.MODE_PRIVATE).edit().clear().commit();
        EditorialData.load(Catalog.get(c).films(), Catalog.get(c).series());
    }

    @After public void tearDown() {
        EditorialData.clear();
        Resume.clear(c);
    }

    // ---- the table ---------------------------------------------------------------------------------------------------

    @Test public void homeHasTheOtherAppsTwentyThreeRowsInItsOrder() {
        assertEquals(Arrays.asList("Continue Watching", "Latest Releases", "Trending Movies", "Latest TV Shows", "Because you watched {title}",
                "We think you will like these", "Because you watched {title}", "Based on True Story", "Critically Acclaimed", "90 Minute Movies",
                "Sci-Fi Movies: Top Picks", "Comedy Movies", "Oscar Winners: Best Picture", "Visually Pleasing", "Crazy Plot Twists",
                "British Gangster Movies", "Cult Classics", "Survival Movies", "Nostalgia", "Revenge Movies", "Zero Gravity Watchlist",
                "No brainers", "Thriller Movies"), titles(CategorySet.HOME));
        for (CategorySet.Entry e : CategorySet.of(CategorySet.HOME)) assertEquals(CategorySet.ROW, e.kind);
    }

    @Test public void moviesHasItsCarouselAndTwentyTwoRows() {
        List<CategorySet.Entry> all = CategorySet.of(CategorySet.MOVIES);
        assertEquals(CategorySet.HERO, all.get(0).kind);
        assertEquals(Arrays.asList("", "Trending Movies", "Conspiracy Theories", "Crime Movies: Top Picks", "Survival Movies",
                "Thriller Movies: Top Picks", "Revenge Movies", "Crazy Plot Twists", "Top Rated Crime Films", "We know you'll love these",
                "Top Bank Robbery Movies", "Laughs & Laughter", "Old But Fresh", "Watch More Than Once", "Need a Good Laugh?",
                "Romantic Comedies", "Comedy Movies: Top Picks", "90s Classics", "Cult Classics", "Mystery Thrillers", "Vacations Gone Wrong",
                "Sci-Fi Movies: Top Picks", "Family Favorites"), titles(CategorySet.MOVIES));
    }

    @Test public void tvShowsHasItsCarouselTrendingAndAServiceRowEachButAppleAndNetflix() {
        assertEquals(Arrays.asList("", "Trending Shows", "Latest {network} Shows", "Latest {network} Shows", "{network} Shows"),
                titles(CategorySet.SHOWS));
        assertEquals("latest:3186/49", entry(CategorySet.SHOWS, "network.3186").source);
        assertEquals("latest:1024", entry(CategorySet.SHOWS, "network.1024").source);
        assertEquals("network:2739", entry(CategorySet.SHOWS, "network.2739").source);
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
                assertFalse(e.id, e.source.matches(".*(network|latest):([0-9/]*/)?(213|2552)(/.*)?"));
            }
        }
        for (int id : CategorySet.NETWORKS) assertTrue(id != 213 && id != 2552);
        assertEquals(2, CategorySet.parts("tmdb:trending/movie/day|new:movie").size());
        assertEquals(2, CategorySet.parts("tmdb:movie/top_rated+tmdb:tv/top_rated").get(0).size());
        assertTrue(CategorySet.tmdb("tmdb:trending/all/day|new:all"));
        assertTrue(CategorySet.tmdb("because:0"));
        assertTrue(CategorySet.tmdb("list:28~best picture"));
        assertFalse(CategorySet.tmdb("new:all"));
        assertFalse(CategorySet.tmdb("continue:movie"));
    }

    private static List<String> titles(String page) {
        List<String> out = new ArrayList<>();
        for (CategorySet.Entry e : CategorySet.of(page)) out.add(e.title);
        return out;
    }

    private static CategorySet.Entry entry(String page, String id) {
        for (CategorySet.Entry e : CategorySet.of(page)) if (e.id.equals(id)) return e;
        throw new AssertionError(id);
    }

    // ---- TMDB's lists matched ----------------------------------------------------------------------------------------

    @Test public void aTmdbListIsMatchedToWhatThePlaylistsHoldAndNothingElse() {
        Shelves.Shelf s = Shelves.make(c, entry(CategorySet.MOVIES, "trending"));
        assertFalse(s.isEmpty());
        Set<String> keys = new HashSet<>();
        for (Shelves.Pick p : s.picks) {
            assertNotNull(p.title);
            assertFalse(p.title.series);
            assertTrue("each title once", keys.add(p.title.key));
            for (String no : EditorialData.ELSEWHERE) assertFalse(no.equals(p.title.name));
            assertNotNull(p.item);
        }
        // Every themed row of Home finds the family's films (a keyword's id found by its name, a list by its name).
        for (String id : new String[]{"true.story", "acclaimed", "ninety", "oscars", "visual", "twists", "british.gangsters", "zero.gravity"}) {
            assertFalse(id, Shelves.make(c, entry(CategorySet.HOME, id)).isEmpty());
        }
    }

    @Test public void moreReadsTheNextPage() {
        CategorySet.Entry e = new CategorySet.Entry("test", "both", "Both", "tmdb:trending/all/day", 0, CategorySet.ROW);
        Map<String, TmdbLists.Page> pages = new HashMap<>();
        List<TmdbLists.Item> one = new ArrayList<>(), two = new ArrayList<>();
        List<Catalog.Title> all = new ArrayList<>(Catalog.get(c).films());
        all.addAll(Catalog.get(c).series());
        for (int i = 0; i < 12; i++) one.add(EditorialData.item(i + 1, all.get(i).series, all.get(i).name, all.get(i).year));
        for (int i = 12; i < all.size(); i++) two.add(EditorialData.item(i + 1, all.get(i).series, all.get(i).name, all.get(i).year));
        pages.put("trending/all/day#1", new TmdbLists.Page(one, 1, 2));
        pages.put("trending/all/day#2", new TmdbLists.Page(two, 2, 2));
        EditorialData.pages(pages, new HashMap<>());
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
        Map<String, TmdbLists.Page> pages = new HashMap<>();
        List<TmdbLists.Item> none = new ArrayList<>();
        for (int i = 0; i < 20; i++) none.add(EditorialData.item(i + 1, false, "Elsewhere " + i, "2020"));
        pages.put("trending/movie/day#1", new TmdbLists.Page(none, 1, 1));
        EditorialData.pages(pages, new HashMap<>());
        assertTrue(Shelves.make(c, entry(CategorySet.MOVIES, "trending")).isEmpty());
        // Nothing watched yet: no Continue Watching, no Because you watched, nothing for you.
        assertTrue(Shelves.local(c, entry(CategorySet.HOME, "continue")).isEmpty());
        EditorialData.load(Catalog.get(c).films(), Catalog.get(c).series());
        assertTrue(Shelves.make(c, entry(CategorySet.HOME, "because.0")).isEmpty());
        assertTrue(Shelves.make(c, entry(CategorySet.HOME, "foryou")).isEmpty());
        Resume.save(c, new Resume.Point("p0|m|3", "", "Ember Road", "", "", 20 * 60_000L, 100 * 60_000L, System.currentTimeMillis()));
        Shelves.Shelf going = Shelves.local(c, entry(CategorySet.HOME, "continue"));
        assertEquals(1, going.picks.size());
        assertEquals("Ember Road", going.picks.get(0).title.name);
        assertNotNull(going.picks.get(0).point);
    }

    @Test public void becauseYouWatchedComesFromWhatTheFamilyWatched() {
        long now = System.currentTimeMillis();
        Resume.save(c, new Resume.Point("p0|m|3", "", "Ember Road", "", "", 20 * 60_000L, 100 * 60_000L, now - 60_000L));
        Resume.save(c, new Resume.Point("p0|s|2", "e1", "Signal Lost", "S1 E2 · Pilot", "", 10 * 60_000L, 40 * 60_000L, now));
        List<Catalog.Title> watched = Shelves.taste(c);
        assertEquals("Signal Lost", watched.get(0).name);
        assertEquals("Ember Road", watched.get(1).name);
        Shelves.Shelf first = Shelves.make(c, entry(CategorySet.HOME, "because.0"));
        assertEquals("Because you watched Signal Lost", first.title);
        assertFalse(first.isEmpty());
        // A show's recommendations are shows; never one the family watched.
        for (Shelves.Pick p : first.picks) {
            assertTrue(p.title.series);
            assertFalse(p.title.name.equals("Signal Lost"));
        }
        Shelves.Shelf second = Shelves.make(c, entry(CategorySet.HOME, "because.1"));
        assertEquals("Because you watched Ember Road", second.title);
        for (Shelves.Pick p : second.picks) assertFalse(p.title.name.equals("Ember Road"));
        // Films for Movies' "We know you'll love these", both on Home's.
        for (Shelves.Pick p : Shelves.make(c, entry(CategorySet.MOVIES, "foryou")).picks) assertFalse(p.title.series);
        Shelves.Shelf both = Shelves.make(c, entry(CategorySet.HOME, "foryou"));
        boolean films = false, shows = false;
        for (Shelves.Pick p : both.picks) {
            films |= !p.title.series;
            shows |= p.title.series;
        }
        assertTrue(films && shows);
        // Watched to the end (Continue Watching forgets it): still what the family watched.
        Resume.clear(c);
        assertEquals(2, Shelves.taste(c).size());
    }

    @Test public void keywordsAndDatesAreMadeRealAndAListMustStillBeTheList() {
        long day = 1_764_000_000_000L;
        String p = Shelves.resolve(c, "discover/movie?with_keywords={revenge/vengeance}&primary_release_date.lte={today}&x={today-365}", day);
        assertNotNull(p);
        assertTrue(p, p.matches("discover/movie\\?with_keywords=\\d+%7C\\d+&primary_release_date\\.lte=\\d{4}-\\d{2}-\\d{2}&x=\\d{4}-\\d{2}-\\d{2}"));
        // A list renamed (no longer the one the row is for): left out.
        Map<String, TmdbLists.Page> pages = new HashMap<>();
        List<TmdbLists.Item> items = new ArrayList<>();
        for (Catalog.Title t : Catalog.get(c).films()) items.add(EditorialData.item(t.name.length(), false, t.name, t.year));
        pages.put("list/28#1", new TmdbLists.Page(items, 1, 1, "Our Favourite Picnics"));
        EditorialData.pages(pages, new HashMap<>());
        assertTrue(Shelves.make(c, entry(CategorySet.HOME, "oscars")).isEmpty());
        pages.put("list/28#1", new TmdbLists.Page(items, 1, 1, "Best Picture Winners - the Academy Awards"));
        assertFalse(Shelves.make(c, entry(CategorySet.HOME, "oscars")).isEmpty());
        // Without TMDB a keyword can't be found: the themed row is left out rather than shown as something else.
        EditorialData.clear();
        assertNull(Shelves.resolve(c, "discover/movie?with_keywords={revenge}", day));
        assertEquals("discover/movie?a=1", Shelves.resolve(c, "discover/movie?a=1", day));
    }

    @Test public void networksAreNamedAsTmdbNamesThemButNeverAppleOrNetflix() {
        Shelves.Shelf s = Shelves.make(c, entry(CategorySet.SHOWS, "network.3186"));
        assertEquals("Latest " + EditorialData.NETWORKS.get(3186) + " Shows", s.title);
        assertFalse(s.isEmpty());
        for (Shelves.Pick p : s.picks) assertTrue(p.title.series);
        assertEquals(EditorialData.NETWORKS.get(2739) + " Shows", Shelves.make(c, entry(CategorySet.SHOWS, "network.2739")).title);
        assertFalse(Shelves.allowedName("Apple TV+"));
        assertFalse(Shelves.allowedName("Netflix"));
        assertFalse(Shelves.allowedName(""));
        assertTrue(Shelves.allowedName("Harbour+"));
        // Whatever TMDB calls a network, an Apple or Netflix one is left out; one it can't name too.
        Map<Integer, String> names = new HashMap<>(EditorialData.NETWORKS);
        names.put(1024, "Apple TV+");
        List<Catalog.Title> f = Catalog.get(c).films(), sh = Catalog.get(c).series();
        TmdbLists.preview((path, page) -> EditorialData.page(path, page, f, sh), names);
        assertTrue(Shelves.make(c, entry(CategorySet.SHOWS, "network.1024")).isEmpty());
        TmdbLists.preview((path, page) -> EditorialData.page(path, page, f, sh), new HashMap<>());
        assertTrue(Shelves.make(c, entry(CategorySet.SHOWS, "network.2739")).isEmpty());
    }

    @Test public void withoutTmdbTheRowsAreThePlaylistsOwnOrLeftOut() {
        EditorialData.none();
        Shelves.Shelf comedy = Shelves.make(c, entry(CategorySet.HOME, "comedy"));
        assertFalse(comedy.isEmpty());
        for (Shelves.Pick p : comedy.picks) assertEquals("UK | Comedy Movies", p.title.category);
        assertNotNull(comedy.all);
        assertTrue(Shelves.make(c, entry(CategorySet.HOME, "thriller")).isEmpty());
        assertTrue(Shelves.make(c, entry(CategorySet.HOME, "trending.movies")).isEmpty());
        assertTrue(Shelves.make(c, entry(CategorySet.HOME, "true.story")).isEmpty());
        // Latest Releases and the carousels: the playlists' newest.
        Shelves.Shelf latest = Shelves.make(c, entry(CategorySet.HOME, "latest.releases"));
        assertEquals("The Long Harbour", latest.picks.get(0).title.name);
        assertEquals("Harbour Lights", Shelves.make(c, entry(CategorySet.SHOWS, "hero")).picks.get(0).title.name);
        assertTrue(Shelves.named("UK | Comedy Movies", new String[]{"comedy"}));
        assertFalse(Shelves.named("Transaction Films", new String[]{"action"}));
        assertTrue(Shelves.named("Sci-Fi", new String[]{"sci-fi"}));
    }

    @Test public void localRowsAreTheFamilysOwnLists() {
        // A row's own choice of the playlists' newest while TMDB's is read.
        Shelves.Shelf latest = Shelves.local(c, entry(CategorySet.HOME, "latest.releases"));
        assertEquals("The Long Harbour", latest.picks.get(0).title.name);
        assertEquals(FILMS.length, latest.total());
        assertFalse(Shelves.local(c, entry(CategorySet.MOVIES, "hero")).isEmpty());
        // A TMDB-only row has nothing local.
        assertTrue(Shelves.local(c, entry(CategorySet.HOME, "trending.movies")).isEmpty());
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
        assertEquals("Action", TmdbLists.genre(film.genres[0]));
        assertTrue(p.items.get(1).tv);
        assertEquals("", p.items.get(1).poster);
        // At most ten pages of a list are read.
        assertEquals(TmdbLists.MAX_PAGES, p.pages);
        // A list's answer: its titles as "items", its own name.
        JSONObject list = new JSONObject().put("name", "Best Picture Winners").put("items", new JSONArray()
                .put(new JSONObject().put("id", 9).put("media_type", "movie").put("title", "Iron Bay").put("release_date", "2019-01-01")));
        TmdbLists.Page l = TmdbLists.parse(list, TmdbLists.mediaOf("list/28"), 1);
        assertEquals("Best Picture Winners", l.name);
        assertEquals("Iron Bay", l.items.get(0).name);
        // A show list says nothing of each one's kind: all shows.
        assertTrue(TmdbLists.parse(new JSONObject().put("results", new JSONArray().put(new JSONObject().put("id", 9).put("name", "Signal Lost"))),
                "tv", 1).items.get(0).tv);
        assertEquals("", TmdbLists.mediaOf("trending/all/day"));
        assertEquals("tv", TmdbLists.mediaOf("discover/tv?with_networks=4"));
        assertEquals("movie", TmdbLists.mediaOf("movie/now_playing?region=GB"));
        assertEquals("tv", TmdbLists.mediaOf("tv/123/recommendations"));
        assertTrue(TmdbLists.keepMs("trending/all/day") < TmdbLists.keepMs("movie/popular"));
        assertTrue(TmdbLists.keepMs("movie/popular") < TmdbLists.keepMs("discover/movie?with_genres=28"));
    }
}
