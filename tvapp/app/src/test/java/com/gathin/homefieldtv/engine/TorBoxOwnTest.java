package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.10: the family's own TorBox titles that no playlist has, their episodes, TMDB's answers for them, and the order for
 * a TV that shows 4K. Invented films, shows and files only; nothing is asked of TorBox or TMDB.
 */
public class TorBoxOwnTest {
    private static final long MB = 1024L * 1024, GB = 1024 * MB;

    private static TorBox.File file(int item, int id, String name, String download, long size) {
        return new TorBox.File("torrent", item, id, name, download + "/" + name, download, size);
    }

    private static List<String> names(List<TorBox.File> files) {
        List<String> out = new ArrayList<>();
        for (TorBox.File f : files) out.add(f.name);
        return out;
    }

    private static Catalog.Title playlistFilm(String id, String name, String year) {
        return new Catalog.Title("p0", false, id, name, "Films", "", "", "", "", 0, "mkv", "", null, null, year);
    }

    private static Catalog.Title playlistShow(String id, String name) {
        return new Catalog.Title("p0", true, id, name, "Series", "", "", "", "", 0, "", "", null, null, "");
    }

    private static Catalog.Episode episode(String id, int season, int number) {
        return new Catalog.Episode(id, season, number, "", "", "", 0, "mkv", "", "", "");
    }

    // ---- the order on a TV that shows 4K -------------------------------------------------------------------------

    private static List<TorBox.File> versions() {
        List<TorBox.File> lib = new ArrayList<>();
        lib.add(file(1, 1, "Glass.Harbour.2024.720p.WEB.mp4", "Glass.Harbour.2024.720p", 2 * GB));
        lib.add(file(2, 1, "Glass.Harbour.2024.2160p.REMUX.mkv", "Glass.Harbour.2024.2160p.REMUX", 58 * GB));
        lib.add(file(3, 1, "Glass.Harbour.2024.1080p.WEB-DL.mkv", "Glass.Harbour.2024.1080p", 7 * GB));
        lib.add(file(4, 1, "Glass.Harbour.2024.2160p.WEB-DL.DV.mkv", "Glass.Harbour.2024.2160p.WEB", 16 * GB));
        lib.add(file(5, 1, "Glass.Harbour.2024.1080p.BluRay.avi", "Glass.Harbour.2024.1080p.AVI", 9 * GB));
        return lib;
    }

    @Test public void fourKUnderTwentyGigabytesFirstOnAFourKScreen() {
        List<TorBox.File> lib = versions();
        assertEquals(Arrays.asList("Glass.Harbour.2024.2160p.WEB-DL.DV.mkv", "Glass.Harbour.2024.1080p.WEB-DL.mkv",
                "Glass.Harbour.2024.2160p.REMUX.mkv", "Glass.Harbour.2024.720p.WEB.mp4"),
                names(TorBox.films(lib, "Glass Harbour", null, "2024", true)).subList(0, 4));
        // An AVI goes after them all, whatever its picture.
        List<TorBox.File> all = new ArrayList<>(lib);
        all.sort((a, b) -> TorBox.compare(a, b, true));
        assertEquals("Glass.Harbour.2024.1080p.BluRay.avi", all.get(all.size() - 1).name);
    }

    @Test public void thePhonesOrderElsewhere() {
        List<TorBox.File> lib = versions();
        List<String> order = names(TorBox.films(lib, "Glass Harbour", null, "2024", false));
        assertEquals("Glass.Harbour.2024.1080p.WEB-DL.mkv", order.get(0));
        assertEquals("Glass.Harbour.2024.2160p.WEB-DL.DV.mkv", order.get(1));
        assertEquals("Glass.Harbour.2024.2160p.REMUX.mkv", order.get(2));
        // The four-argument form is the phone's order too.
        assertEquals(order, names(TorBox.films(lib, "Glass Harbour", null, "2024")));
    }

    @Test public void episodesInTheSameOrders() {
        List<TorBox.File> lib = new ArrayList<>();
        lib.add(file(1, 1, "Paper.Lanterns.S01E02.1080p.WEB.mkv", "Paper.Lanterns.S01.1080p", 2 * GB));
        lib.add(file(2, 1, "Paper.Lanterns.S01E02.2160p.WEB.mkv", "Paper.Lanterns.S01.2160p", 6 * GB));
        assertEquals("Paper.Lanterns.S01E02.2160p.WEB.mkv", TorBox.episodeVersions(lib, "Paper Lanterns", null, 1, 2, true).get(0).name);
        assertEquals("Paper.Lanterns.S01E02.1080p.WEB.mkv", TorBox.episodeVersions(lib, "Paper Lanterns", null, 1, 2, false).get(0).name);
    }

    // ---- the account's titles --------------------------------------------------------------------------------------

    @Test public void filmsAndSeriesInTheAccount() {
        List<TorBox.File> lib = new ArrayList<>();
        lib.add(file(1, 1, "Glass.Harbour.2024.1080p.WEB-DL.mkv", "Glass.Harbour.2024.1080p", 7 * GB));
        lib.add(file(2, 1, "Glass.Harbour.2024.2160p.DV.Atmos.mkv", "Glass.Harbour.2024.2160p", 15 * GB));
        // Its own name says nothing; its download's says what it is.
        TorBox.File q = file(3, 1, "qm-main.mkv", "Quiet.Meridian.2023.1080p.BluRay", 9 * GB);
        q.single = true;
        lib.add(q);
        // No year anywhere: it can't be told from another, so it is left out.
        lib.add(file(4, 1, "Salt.Road.1080p.mkv", "Salt.Road.1080p", 4 * GB));
        lib.add(file(5, 1, "Paper.Lanterns.S01E01.1080p.mkv", "Paper.Lanterns.S01.2019.1080p", 2 * GB));
        lib.add(file(5, 2, "Paper.Lanterns.S01E02.1080p.mkv", "Paper.Lanterns.S01.2019.1080p", 2 * GB));
        // An episode named by its number only, in its season's folder.
        lib.add(new TorBox.File("torrent", 6, 1, "S02E01.mkv", "Paper Lanterns Season 2/S02E01.mkv", "x7f3k9q2", 2 * GB));
        // A code for a name, and a sample: neither is a title.
        lib.add(file(7, 1, "a1b2c3d4.mkv", "a1b2c3d4", 3 * GB));
        lib.add(file(8, 1, "Glass.Harbour.2024.Sample.mkv", "Glass.Harbour.2024.1080p", 40 * MB));

        List<TorBox.Own> own = TorBox.titles(lib);
        assertEquals(3, own.size());
        TorBox.Own film = own.get(0);
        assertFalse(film.series);
        assertEquals("Glass Harbour", film.name);
        assertEquals(2024, film.year);
        assertEquals(2, film.files.size());
        assertEquals(Catalog.PICTURE_4K, film.picture());
        assertTrue(film.dolbyVision());
        assertTrue(film.atmos());
        assertEquals("glassharbour|2024", film.id());

        assertEquals("Quiet Meridian", own.get(1).name);
        assertEquals(2023, own.get(1).year);

        TorBox.Own show = own.get(2);
        assertTrue(show.series);
        assertEquals("Paper Lanterns", show.name);
        assertEquals(3, show.files.size());
        assertEquals("paperlanterns", show.id());
    }

    @Test public void namesThatCouldBeTitles() {
        assertTrue(TorBox.named("1917"));
        assertTrue(TorBox.named("Up"));
        assertTrue(TorBox.named("The 4400"));
        assertTrue(TorBox.named("Glass Harbour"));
        assertFalse(TorBox.named("a1b2c3"));
        assertFalse(TorBox.named("x"));
        assertFalse(TorBox.named(""));
        assertFalse(TorBox.named(null));
    }

    @Test public void onlyThoseNoPlaylistHas() {
        List<TorBox.Own> own = new ArrayList<>();
        own.add(new TorBox.Own(false, "Glass Harbour", 2024));
        own.add(new TorBox.Own(false, "Quiet Meridian", 2023));
        own.add(new TorBox.Own(false, "Ember Lake", 2019));
        own.add(new TorBox.Own(true, "Paper Lanterns", 0));
        own.add(new TorBox.Own(true, "Night Ferry", 0));
        List<Catalog.Title> films = Arrays.asList(
                // A year apart: the same film.
                playlistFilm("11", "Glass Harbour (2023) 4K", "2023"),
                // Another film of the name, years before: not the family's.
                playlistFilm("12", "Ember Lake", "1988"));
        List<Catalog.Title> series = Collections.singletonList(playlistShow("21", "Paper Lanterns"));
        List<TorBox.Own> only = TorBox.onlyOwn(own, films, series);
        List<String> left = new ArrayList<>();
        for (TorBox.Own o : only) left.add(o.name);
        assertEquals(Arrays.asList("Quiet Meridian", "Ember Lake", "Night Ferry"), left);
    }

    @Test public void anOwnTitleAsTheCataloguesWithTmdbsAnswer() {
        TorBox.Own o = new TorBox.Own(false, "glass harbour", 2024);
        TorBox.File f = file(1, 1, "glass.harbour.2024.2160p.dv.atmos.mkv", "glass.harbour.2024.2160p", 15 * GB);
        f.added = 1_700_000_000_000L;
        o.files.add(f);
        Tmdb.Match m = new Tmdb.Match(4242, "Glass Harbour", "2024", "https://image.example/p.jpg", "https://image.example/b.jpg",
                "A lighthouse keeper's last winter.", "7.4");
        Catalog.Title t = TorBox.title(o, m);
        assertTrue(t.torbox());
        assertFalse(t.series);
        assertEquals(Catalog.TORBOX, t.category);
        assertEquals("torbox|m|glassharbour|2024", t.key);
        assertEquals("Glass Harbour", t.name);
        assertEquals("2024", t.year);
        assertEquals("https://image.example/p.jpg", t.poster);
        assertEquals("A lighthouse keeper's last winter.", t.plot);
        assertEquals(1_700_000_000_000L, t.added);
        assertEquals(Catalog.PICTURE_4K, t.picture);
        assertTrue(t.dolbyVision);
        assertTrue(t.atmos);

        // Not matched: its own name, given capitals, and a drawn poster.
        Catalog.Title plain = TorBox.title(o, Tmdb.NONE);
        assertEquals("Glass Harbour", plain.name);
        assertEquals("", plain.poster);
        assertEquals(t.key, plain.key);
        assertEquals("", TorBox.title(o, null).poster);
    }

    @Test public void namesGivenCapitalsOnlyWhenAllSmall() {
        assertEquals("The Long Tide", TorBox.tidy("the long tide"));
        assertEquals("Glass Harbour", TorBox.tidy("Glass Harbour"));
        assertEquals("iCarly Nights", TorBox.tidy("iCarly Nights"));
        assertEquals("1917", TorBox.tidy("1917"));
        assertEquals("", TorBox.tidy(null));
    }

    @Test public void timesAsTorBoxGivesThem() {
        assertEquals(1_714_566_896_000L, TorBox.time("2024-05-01T12:34:56Z"));
        assertEquals(1_714_566_896_000L, TorBox.time("2024-05-01T12:34:56"));
        assertEquals(1_714_563_296_000L, TorBox.time("2024-05-01T12:34:56+01:00"));
        assertEquals(0, TorBox.time("yesterday"));
        assertEquals(0, TorBox.time(""));
        assertEquals(0, TorBox.time(null));
    }

    // ---- episodes only TorBox has --------------------------------------------------------------------------------

    @Test public void aSeriesEpisodesFromItsFiles() {
        List<TorBox.File> lib = new ArrayList<>();
        lib.add(file(1, 1, "Night.Ferry.S01E02.1080p.mkv", "Night.Ferry.S01", 2 * GB));
        lib.add(file(1, 2, "Night.Ferry.S01E01.1080p.mkv", "Night.Ferry.S01", 2 * GB));
        lib.add(file(2, 1, "Night.Ferry.S01E01.2160p.mkv", "Night.Ferry.S01.2160p", 5 * GB));
        lib.add(file(3, 1, "Night.Ferry.S02E01.720p.mkv", "Night.Ferry.S02", GB));
        lib.add(file(4, 1, "Other.Show.S01E01.1080p.mkv", "Other.Show.S01", 2 * GB));
        List<Catalog.Episode> eps = TorBox.ownEpisodes(lib, "Night Ferry");
        assertEquals(3, eps.size());
        assertEquals("tb:1:1", eps.get(0).id);
        assertEquals("tb:1:2", eps.get(1).id);
        assertEquals("tb:2:1", eps.get(2).id);
        for (Catalog.Episode e : eps) assertTrue(e.torbox);
    }

    @Test public void onlyTorBoxsEpisodesPutInAmongThePlaylists() {
        List<Catalog.Episode> playlist = Arrays.asList(episode("p1", 1, 1), episode("p2", 1, 2));
        Catalog.Episode b2 = episode("tb:1:2", 1, 2), b3 = episode("tb:1:3", 1, 3), c1 = episode("tb:2:1", 2, 1);
        for (Catalog.Episode e : Arrays.asList(b2, b3, c1)) e.torbox = true;
        List<Catalog.Episode> all = TorBox.withOwn(playlist, Arrays.asList(c1, b3, b2));
        List<String> ids = new ArrayList<>();
        for (Catalog.Episode e : all) ids.add(e.id);
        // The playlist's own S1 E2 stays; TorBox's S1 E3 and S2 E1 come in, in order.
        assertEquals(Arrays.asList("p1", "p2", "tb:1:3", "tb:2:1"), ids);
        assertFalse(all.get(1).torbox);
        assertTrue(all.get(2).torbox);
    }

    @Test public void aPagesSeasonsMadeAgainKeepTheirNames() {
        Catalog.Title t = playlistShow("21", "Paper Lanterns");
        List<Catalog.Episode> one = Collections.singletonList(episode("p1", 1, 1));
        List<Catalog.Season> seasons = new ArrayList<>();
        seasons.add(new Catalog.Season(1, "The Winter", one));
        Catalog.Details d = new Catalog.Details("Plot", "Drama", "", "", "2019", "", "", "", "", "", 50, seasons);
        Catalog.Episode more = episode("tb:2:1", 2, 1);
        more.torbox = true;
        Catalog.Details with = Catalog.withEpisodes(d, t, Arrays.asList(one.get(0), more));
        assertEquals(2, with.seasons.size());
        assertEquals("The Winter", with.seasons.get(0).name);
        assertEquals("Series 2", with.seasons.get(1).name);
        assertEquals("Plot", with.plot);
        assertTrue(with.episode("tb:2:1").torbox);
        // No page at all: one made from the title.
        assertEquals(2, Catalog.withEpisodes(null, t, Arrays.asList(one.get(0), more)).seasons.size());
    }

    // ---- folded into the catalogue -----------------------------------------------------------------------------------

    @Test public void yourTorBoxComesFirst() {
        Catalog.Title pf = playlistFilm("11", "Ember Lake", "1988");
        List<Catalog.Category> fc = new ArrayList<>();
        fc.add(new Catalog.Category("Films", new ArrayList<>(Collections.singletonList(pf))));
        Map<String, Catalog.Title> byKey = new HashMap<>();
        byKey.put(pf.key, pf);
        Catalog.Model base = new Catalog.Model(new ArrayList<>(Collections.singletonList(pf)), new ArrayList<>(), fc, new ArrayList<>(),
                byKey, new HashMap<>());
        TorBox.Own o = new TorBox.Own(false, "Quiet Meridian", 2023);
        Catalog.Title own = TorBox.title(o, null);
        Catalog.Model m = Catalog.withOwn(base, Collections.singletonList(own), Collections.emptyList());
        assertEquals(Catalog.TORBOX, m.filmCategories.get(0).name);
        assertEquals("Films", m.filmCategories.get(1).name);
        assertEquals(2, m.films.size());
        assertSame(own, m.byKey.get(own.key));
        assertSame(pf, m.byKey.get(pf.key));
        // No series of its own: no empty category for them.
        assertTrue(m.seriesCategories.isEmpty());
        // Nothing of its own: the playlists' as they were.
        assertSame(base, Catalog.withOwn(base, Collections.emptyList(), Collections.emptyList()));
    }

    // ---- TMDB's answers ------------------------------------------------------------------------------------------------

    @Test public void tmdbsSearchPickedByNameAndYear() throws Exception {
        JSONObject films = new JSONObject("{\"results\":["
                + "{\"id\":1,\"title\":\"Glass Harbor Nights\",\"release_date\":\"2024-03-01\"},"
                + "{\"id\":2,\"title\":\"Glass Harbour\",\"release_date\":\"1971-06-01\",\"poster_path\":\"/old.jpg\"},"
                + "{\"id\":3,\"title\":\"Glass Harbour\",\"release_date\":\"2024-09-12\",\"poster_path\":\"/new.jpg\","
                + "\"backdrop_path\":\"/wide.jpg\",\"overview\":\"A lighthouse keeper's last winter.\",\"vote_average\":7.43}]}");
        Tmdb.Match m = Tmdb.pick(films, false, "Glass Harbour", 2024);
        assertEquals(3, m.id);
        assertEquals("2024", m.year);
        assertEquals("https://image.tmdb.org/t/p/w342/new.jpg", m.poster);
        assertEquals("https://image.tmdb.org/t/p/w1280/wide.jpg", m.backdrop);
        assertEquals("7.4", m.rating);
        // A film from another year isn't it.
        assertSame(Tmdb.NONE, Tmdb.pick(films, false, "Glass Harbour", 2010));
        // No year wanted: the first of the name.
        assertEquals(2, Tmdb.pick(films, false, "Glass Harbour", 0).id);
        assertSame(Tmdb.NONE, Tmdb.pick(new JSONObject("{\"results\":[]}"), false, "Glass Harbour", 2024));
        assertSame(Tmdb.NONE, Tmdb.pick(null, false, "Glass Harbour", 2024));

        // A series whose download says a later season's year: still it, by its name.
        JSONObject shows = new JSONObject("{\"results\":[{\"id\":7,\"name\":\"Paper Lanterns\",\"original_name\":\"Papierlaternen\","
                + "\"first_air_date\":\"2019-01-10\"}]}");
        assertEquals(7, Tmdb.pick(shows, true, "Paper Lanterns", 2023).id);
        assertEquals(7, Tmdb.pick(shows, true, "Papierlaternen", 2019).id);
    }

    @Test public void tmdbsPicturesAndScores() {
        assertEquals("https://image.tmdb.org/t/p/w342/a.jpg", Tmdb.image("/a.jpg", "w342"));
        assertEquals("https://image.tmdb.org/t/p/w300/a.jpg", Tmdb.image("a.jpg", "w300"));
        assertEquals("", Tmdb.image("", "w342"));
        assertEquals("", Tmdb.image("null", "w342"));
        assertEquals("", Tmdb.image(null, "w342"));
        assertEquals("7.4", Tmdb.rating(7.43));
        assertEquals("10.0", Tmdb.rating(10));
        assertEquals("", Tmdb.rating(0));
        assertEquals("", Tmdb.rating(11));
    }

    @Test public void tmdbsPageForAnOwnTitle() throws Exception {
        Catalog.Title t = TorBox.title(new TorBox.Own(true, "Night Ferry", 2021), null);
        JSONObject j = new JSONObject("{\"overview\":\"A ferry that only sails after dark.\",\"genres\":[{\"name\":\"Drama\"},{\"name\":\"Mystery\"}],"
                + "\"first_air_date\":\"2021-02-03\",\"backdrop_path\":\"/b.jpg\",\"poster_path\":\"/p.jpg\",\"vote_average\":8.1,"
                + "\"episode_run_time\":[48],\"created_by\":[{\"name\":\"Ada Quill\"}],"
                + "\"credits\":{\"cast\":[{\"name\":\"Rowan Vale\"},{\"name\":\"Iris Penn\"}],\"crew\":[]},"
                + "\"videos\":{\"results\":[{\"site\":\"Vimeo\",\"type\":\"Trailer\",\"key\":\"nope\"},"
                + "{\"site\":\"YouTube\",\"type\":\"Teaser\",\"key\":\"teaser00000\"},{\"site\":\"YouTube\",\"type\":\"Trailer\",\"key\":\"abcdefghijk\"}]}}");
        List<Catalog.Season> seasons = Catalog.seasonsOf(Collections.singletonList(episode("tb:1:1", 1, 1)));
        Catalog.Details d = Tmdb.detailsOf(j, t, seasons);
        assertEquals("A ferry that only sails after dark.", d.plot);
        assertEquals("Drama, Mystery", d.genre);
        assertEquals("Rowan Vale, Iris Penn", d.cast);
        assertEquals("Ada Quill", d.director);
        assertEquals("2021-02-03", d.released);
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", d.backdrop);
        assertEquals("abcdefghijk", d.trailer);
        assertEquals("8.1", d.rating);
        assertEquals(48, d.minutes);
        assertEquals(1, d.seasons.size());

        // Nothing from TMDB: what the title has.
        Catalog.Details plain = Catalog.plainDetails(t, seasons);
        assertEquals(t.plot, plain.plot);
        assertEquals(1, plain.seasons.size());
    }

    @Test public void episodesNamedAsTmdbsSeasonNamesThem() throws Exception {
        Catalog.Episode e1 = episode("tb:1:1", 1, 1), e2 = episode("tb:1:2", 1, 2);
        e1.torbox = true;
        e2.torbox = true;
        JSONObject season = new JSONObject("{\"episodes\":[{\"season_number\":1,\"episode_number\":1,\"name\":\"Last Crossing\","
                + "\"overview\":\"The ferry leaves without its captain.\",\"still_path\":\"/s1.jpg\",\"runtime\":51,\"air_date\":\"2021-02-03\"}]}");
        List<Catalog.Episode> named = Tmdb.named(Arrays.asList(e1, e2), season);
        assertEquals("Last Crossing", named.get(0).title);
        assertEquals("https://image.tmdb.org/t/p/w300/s1.jpg", named.get(0).still);
        assertEquals(51, named.get(0).minutes);
        assertEquals("2021-02-03", named.get(0).aired);
        assertEquals("tb:1:1", named.get(0).id);
        assertTrue(named.get(0).torbox);
        // One TMDB hasn't: as it was.
        assertSame(e2, named.get(1));
        // No season from TMDB: all as they were.
        assertEquals(Arrays.asList(e1, e2), Tmdb.named(Arrays.asList(e1, e2), null));
    }
}
