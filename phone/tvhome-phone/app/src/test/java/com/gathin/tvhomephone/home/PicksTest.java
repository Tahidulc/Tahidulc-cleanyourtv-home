package com.gathin.tvhomephone.home;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.live.Catalogue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/** The playlists' catalogue (only what they have is picked), and what a pick shows from TMDB's details. */
public class PicksTest {
    @Test public void catalogueMatchesByIdThenNameAndYear() {
        Catalogue cat = new Catalogue(2026);
        cat.add("movie", "EN - The Matrix (1999) 4K", "603", "");
        cat.add("movie", "Dune: Part Two", "", "2024");
        cat.add("episode", "UK: Slow Horses", "95480", "2022");
        cat.add("episode", "UK: Slow Horses", "95480", "2022");
        cat.add("series", "Brand New Show", "", "2027");
        cat.add("channel", "Not a title", "", "");
        assertEquals(4, cat.size());
        assertTrue(cat.has(603, false, "Anything", null, ""));
        assertFalse(cat.has(603, true, "Anything", null, ""));
        assertTrue(cat.has(0, false, "The Matrix", null, "1999"));
        assertTrue(cat.has(0, false, "Dune: Part Two", null, "2025"));
        assertFalse(cat.has(0, false, "Dune: Part Two", null, "2021"));
        assertTrue(cat.has(0, false, "Nope", "Dune Part Two", ""));
        assertTrue(cat.has(95480, true, "", null, ""));
        assertTrue(cat.has(0, true, "Slow Horses", null, "2022"));
        assertFalse(cat.has(0, false, "Slow Horses", null, "2022"));
        List<Catalogue.Row> fresh = cat.newest(true, 5);
        assertEquals(1, fresh.size());
        assertEquals("Brand New Show", fresh.get(0).name);
        assertTrue(cat.newest(false, 5).isEmpty());
    }

    @Test public void detailsGivePeopleLogoAndCertificate() throws Exception {
        JSONObject d = new JSONObject("{\"genres\":[{\"id\":18},{\"id\":80}],"
                + "\"credits\":{\"cast\":[{\"id\":1},{\"id\":2},{\"id\":3},{\"id\":4},{\"id\":5},{\"id\":6},{\"id\":7}],"
                + "\"crew\":[{\"id\":9,\"job\":\"Director\"},{\"id\":10,\"job\":\"Writer\"}]},"
                + "\"created_by\":[{\"id\":11}],"
                + "\"images\":{\"logos\":[{\"file_path\":\"/fr.png\",\"iso_639_1\":\"fr\"},{\"file_path\":\"/none.png\",\"iso_639_1\":null},"
                + "{\"file_path\":\"/en.png\",\"iso_639_1\":\"en\"}]},"
                + "\"release_dates\":{\"results\":[{\"iso_3166_1\":\"US\",\"release_dates\":[{\"certification\":\"R\"}]},"
                + "{\"iso_3166_1\":\"GB\",\"release_dates\":[{\"certification\":\"\"},{\"certification\":\"15\"}]}]}}");
        assertArrayEquals(new int[]{18, 80}, Feed.genres(d));
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 9}, Feed.people(d, false));
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 9, 11}, Feed.people(d, true));
        assertEquals("https://image.tmdb.org/t/p/w500/en.png", Feed.logo(d));
        assertEquals("15", Feed.cert(d));
        JSONObject plain = new JSONObject("{\"images\":{\"logos\":[{\"file_path\":\"/none.png\",\"iso_639_1\":null}]}}");
        assertEquals("https://image.tmdb.org/t/p/w500/none.png", Feed.logo(plain));
        assertEquals("", Feed.cert(plain));
        assertNull(Feed.logo(new JSONObject("{}")));
        assertEquals(0, Feed.people(null, false).length);
    }

    @Test public void playlistNamesCleanedForSearch() {
        assertEquals("Dune", Feed.clean("EN - Dune (2021) 4K"));
        assertEquals("The Bear", Feed.clean("|UK| The Bear"));
        assertEquals("Slow Horses", Feed.clean("Slow Horses 1080p"));
    }

    @Test public void factsAndLengths() {
        Pick p = new Pick();
        p.year = "2024";
        p.genre = "Thriller";
        p.vote = 7.84;
        p.length = Pick.runtime(130);
        assertEquals("2024  ·  Thriller  ·  ★ 7.8  ·  2h 10m", p.facts());
        assertEquals("48m", Pick.runtime(48));
        assertEquals("", Pick.runtime(0));
        assertEquals("1 season", Pick.seasons(1));
        assertEquals("3 seasons", Pick.seasons(3));
        Pick bare = new Pick();
        assertEquals("", bare.facts());
    }

    @Test public void pickKeptAsJsonAndBack() throws Exception {
        Pick p = new Pick();
        p.id = 42;
        p.tv = true;
        p.title = "Some Show";
        p.poster = "https://image.tmdb.org/t/p/w342/p.jpg";
        p.why = "More Drama";
        Pick back = Pick.from(p.json());
        assertEquals(42, back.id);
        assertTrue(back.tv);
        assertEquals("Some Show", back.title);
        assertEquals(p.poster, back.poster);
        assertNull(back.backdrop);
        assertEquals("More Drama", back.why);
        assertNull(Pick.from(new JSONObject("{\"id\":1}")));
    }

    @Test public void certificateColours() {
        assertEquals(0xFF2BA84A, Hero.certColour("U"));
        assertEquals(0xFFF07F1A, Hero.certColour("12A"));
        assertEquals(0xFFE6007E, Hero.certColour("15"));
        assertEquals(0xFFD1D5DB, Hero.certColour("NR"));
    }
}
