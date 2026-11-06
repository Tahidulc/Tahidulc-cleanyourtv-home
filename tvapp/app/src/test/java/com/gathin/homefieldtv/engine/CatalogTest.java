package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Films and series from the playlists: Xtream's lists and details, an M3U's entries, names, and two playlists as one. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CatalogTest {
    private static Catalog.Read readBack(String file, String source) throws Exception {
        return Catalog.read(new BufferedReader(new StringReader(file)), source);
    }

    @Test public void namesLoseTheProvidersTagsAndTheirYear() {
        assertEquals("The Matrix", Catalog.displayName("EN - The Matrix (1999)"));
        assertEquals("Peppa Pig", Catalog.displayName("|UK| Peppa Pig"));
        assertEquals("X-Men", Catalog.displayName("X-Men (2000)"));
        assertEquals("It - Chapter Two", Catalog.displayName("It - Chapter Two"));
        assertEquals("1999", Catalog.year("The Matrix (1999)"));
        assertEquals("2019", Catalog.year("2019-10-11"));
        assertEquals(Catalog.key("The Matrix"), Catalog.key("EN - The Matrix (1999) 4K"));
    }

    @Test public void ratingsAndRunningTimes() {
        assertEquals("", Catalog.rating("0"));
        assertEquals("7.4", Catalog.rating("7.43"));
        assertEquals("", Catalog.rating("n/a"));
        assertEquals(117, Catalog.minutes("01:57:00"));
        assertEquals(45, Catalog.minutes("45 min"));
        assertEquals(0, Catalog.minutes(""));
    }

    @Test public void xtreamListsAreReadAsTheyArrive() throws Exception {
        StringWriter w = new StringWriter();
        w.write("v1\txtream\t0\n");
        Catalog.categoriesFrom("[{\"category_id\":\"3\",\"category_name\":\"Action\"},{\"category_id\":\"4\",\"category_name\":\"Kids\"}]", "m", w);
        String films = "[{\"num\":1,\"name\":\"EN - Heat (1995)\",\"stream_id\":101,\"stream_icon\":\"http://img/heat.jpg\","
                + "\"rating\":\"8.3\",\"added\":\"1700000000\",\"category_id\":\"3\",\"container_extension\":\"mkv\"},"
                + "{\"name\":\"Coco\",\"stream_id\":\"102\",\"stream_icon\":null,\"rating\":0,\"added\":\"1700000500\",\"category_id\":\"4\","
                + "\"container_extension\":\"mp4\",\"extra\":{\"a\":[1,2]}},"
                + "{\"name\":\"\",\"stream_id\":103}]";
        assertEquals(2, Catalog.readList(new ByteArrayInputStream(films.getBytes(StandardCharsets.UTF_8)), false, w));
        String shows = "[{\"series_id\":7,\"name\":\"Lupin\",\"cover\":\"http://img/lupin.jpg\",\"plot\":\"A thief.\","
                + "\"backdrop_path\":[\"http://img/lupin-bd.jpg\"],\"rating\":\"7.7\",\"last_modified\":\"1700001000\",\"category_id\":\"9\","
                + "\"releaseDate\":\"2021-01-08\"}]";
        assertEquals(1, Catalog.readList(new ByteArrayInputStream(shows.getBytes(StandardCharsets.UTF_8)), true, w));
        Catalog.Read r = readBack(w.toString(), "p0");
        assertEquals(2, r.films.size());
        Catalog.Title heat = r.films.get(0);
        assertEquals("Heat", heat.name);
        assertEquals("1995", heat.year);
        assertEquals("Action", heat.category);
        assertEquals("8.3", heat.rating);
        assertEquals(1_700_000_000_000L, heat.added);
        assertEquals("p0|m|101", heat.key);
        assertEquals("Kids", r.films.get(1).category);
        assertEquals("", r.films.get(1).rating);
        Catalog.Title lupin = r.series.get(0);
        assertTrue(lupin.series);
        assertEquals("A thief.", lupin.plot);
        assertEquals("http://img/lupin-bd.jpg", lupin.backdrop);
        assertEquals("2021", lupin.year);
        // A category the provider didn't name: none (the shelf's own name is used).
        assertEquals("", lupin.category);
    }

    @Test public void m3uFilmsByGroupAndEpisodesUnderTheirSeries() throws Exception {
        List<String[]> vod = new ArrayList<>();
        vod.add(new String[]{"Heat (1995)", "http://h/movie/u/p/1.mkv", "Films | Action", null, null, null, "http://img/1.jpg"});
        vod.add(new String[]{"Black Doves S02E02 Put Your Head on My Shoulder", "http://h/series/u/p/22.mkv", "Series | Drama", null, null,
                "series", "http://img/bd.jpg"});
        vod.add(new String[]{"Black Doves S02E01", "http://h/series/u/p/21.mkv", "Series | Drama", null, null, null, ""});
        vod.add(new String[]{"Black Doves 1x03 The Hit", "http://h/x/13.mp4", "Series | Drama", null, null, null, ""});
        StringWriter w = new StringWriter();
        Catalog.writeM3u(w, vod, 5);
        Catalog.Read r = readBack(w.toString(), "p1");
        assertEquals(1, r.films.size());
        assertEquals("Heat", r.films.get(0).name);
        assertEquals("Films | Action", r.films.get(0).category);
        assertEquals("http://h/movie/u/p/1.mkv", r.films.get(0).url);
        assertEquals(1, r.series.size());
        Catalog.Title show = r.series.get(0);
        assertEquals("Black Doves", show.name);
        assertEquals("Series | Drama", show.category);
        assertEquals(3, r.episodes.get(show.id).size());
        Catalog.Model m = Catalog.merge(Arrays.asList(r));
        assertNotNull(m);
    }

    @Test public void theSameFilmInTwoPlaylistsIsShownOnce() throws Exception {
        String a = "v1\txtream\t0\nc\tm\t1\tAction\nm\t5\tHeat (1995)\t1\t\t8\t0\tmkv\t\t\t\t\n";
        String b = "v1\txtream\t0\nc\tm\t9\tAction\nm\t77\tEN - Heat 1995\t9\t\t8\t0\tmp4\t\t\t\t\nm\t78\tCoco\t9\t\t8\t0\tmp4\t\t\t\t\n";
        Catalog.Model m = Catalog.merge(Arrays.asList(readBack(a, "p0"), readBack(b, "p1")));
        assertEquals(2, m.films.size());
        assertEquals(1, m.films.get(0).copies.size());
        assertEquals("p1|m|77", m.films.get(0).copies.get(0).key);
        assertEquals(1, m.filmCategories.size());
        assertEquals(2, m.filmCategories.get(0).titles.size());
        // Either playlist's key finds the film.
        assertEquals(m.films.get(0), m.byKey.get("p1|m|77"));
    }

    @Test public void aFilmsDetails() throws Exception {
        String json = "{\"info\":{\"movie_image\":\"http://img/p.jpg\",\"backdrop_path\":[\"http://img/b1.jpg\",\"http://img/b2.jpg\"],"
                + "\"plot\":\"A heist.\",\"genre\":\"Crime, Thriller\",\"cast\":\"Al Pacino, Robert De Niro\",\"director\":\"Michael Mann\","
                + "\"releasedate\":\"1995-12-15\",\"duration_secs\":10200,\"rating\":\"8.3\",\"age\":\"15\"},"
                + "\"movie_data\":{\"stream_id\":5,\"container_extension\":\"mkv\"}}";
        Catalog.Details d = Catalog.filmInfo(json, null);
        assertEquals("A heist.", d.plot);
        assertEquals("http://img/b1.jpg", d.backdrop);
        assertEquals(170, d.minutes);
        assertEquals("Michael Mann", d.director);
        assertEquals("15", d.age);
        assertTrue(d.seasons.isEmpty());
        // A provider that sends no details at all.
        assertEquals("", Catalog.filmInfo("{\"info\":[],\"movie_data\":{}}", null).plot);
    }

    @Test public void aSeriesSeasonsAndEpisodesInOrder() throws Exception {
        String json = "{\"seasons\":[{\"season_number\":1,\"name\":\"Season 1\"},{\"season_number\":2,\"name\":\"The Return\"}],"
                + "\"info\":{\"name\":\"Black Doves\",\"plot\":\"Spies.\",\"backdrop_path\":[\"http://img/bd.jpg\"],\"episode_run_time\":\"50\"},"
                + "\"episodes\":{\"2\":[{\"id\":\"202\",\"episode_num\":2,\"title\":\"Black Doves - S02E02 - Put Your Head on My Shoulder\","
                + "\"container_extension\":\"mkv\",\"season\":2,\"info\":{\"duration_secs\":3240,\"plot\":\"Helen.\"}},"
                + "{\"id\":\"201\",\"episode_num\":1,\"title\":\"Black Doves - S02E01 - Black Door\",\"container_extension\":\"mkv\",\"season\":2}],"
                + "\"1\":[{\"id\":\"101\",\"episode_num\":1,\"title\":\"Episode 1\",\"container_extension\":\"mp4\",\"season\":1}]}}";
        Catalog.Details d = Catalog.seriesInfo(json, null);
        assertEquals(2, d.seasons.size());
        assertEquals("Series 1", d.seasons.get(0).name);
        assertEquals("The Return", d.seasons.get(1).name);
        assertEquals("", d.seasons.get(0).episodes.get(0).title);
        Catalog.Episode e1 = d.seasons.get(1).episodes.get(0);
        assertEquals("Black Door", e1.title);
        assertEquals("Put Your Head on My Shoulder", d.seasons.get(1).episodes.get(1).title);
        assertEquals(54, d.seasons.get(1).episodes.get(1).minutes);
        assertEquals("202", d.after(e1).id);
        assertNull(d.after(d.seasons.get(1).episodes.get(1)));
        assertEquals("201", d.after(d.seasons.get(0).episodes.get(0)).id);
        // Some panels send the seasons as a list of lists.
        Catalog.Details listed = Catalog.seriesInfo("{\"episodes\":[[{\"id\":\"9\",\"episode_num\":1,\"title\":\"Pilot\"}]]}", null);
        assertEquals(1, listed.episodes().size());
        assertEquals("Pilot", listed.episodes().get(0).title);
    }

    @Test public void theNewestFirst() {
        List<Catalog.Title> all = new ArrayList<>();
        all.add(new Catalog.Title("p0", false, "1", "Old", "", "", "", "", "", 1000, "", "", "", "", ""));
        all.add(new Catalog.Title("p0", false, "2", "New", "", "", "", "", "", 3000, "", "", "", "", ""));
        all.add(new Catalog.Title("p0", false, "3", "Middle", "", "", "", "", "", 2000, "", "", "", "", ""));
        List<Catalog.Title> n = Catalog.newest(all, 2);
        assertEquals("New", n.get(0).name);
        assertEquals("Middle", n.get(1).name);
    }
}
