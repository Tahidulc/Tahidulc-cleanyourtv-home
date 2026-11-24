package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** The wider search for a programme the guide gives no picture: what is looked for on TMDB, and what is taken. */
public class ArtQueryTest {
    @Test public void aFilmIsLookedForAsAFilmWithItsYear() {
        ArtQuery q = ArtQuery.of("Lumen Cinema FHD", "UK | Movies", "Film: Echoes of Iron (2019)", "A blacksmith's daughter.");
        assertNotNull(q);
        assertEquals(ArtQuery.MOVIE, q.type);
        assertEquals("Echoes of Iron", q.title);
        assertEquals("2019", q.year);
        assertEquals("search/movie?query=Echoes+of+Iron&include_adult=false&language=en-GB&year=2019", q.path());
        // The year from the description's start, on any channel.
        ArtQuery d = ArtQuery.of("Harbour One", "UK | Entertainment", "Echoes of Iron", "(2019) Drama. A blacksmith's daughter.");
        assertEquals(ArtQuery.MOVIE, d.type);
        assertEquals("2019", d.year);
    }

    @Test public void aSeriesIsLookedForAsASeries() {
        ArtQuery q = ArtQuery.of("Harbour One HD", "UK | Entertainment", "The Lantern House", "Series 2, Episode 4. Mara finds the letter.");
        assertEquals(ArtQuery.TV, q.type);
        assertEquals("The Lantern House", q.title);
        assertEquals("", q.year);
        assertEquals("search/tv?query=The+Lantern+House&include_adult=false&language=en-GB", q.path());
        // On a kids' channel, as a series without an episode said.
        ArtQuery k = ArtQuery.of("Kestrel Kids", "UK | Kids", "Pip and the Moon", "Pip builds a ladder.");
        assertEquals(ArtQuery.TV, k.type);
        // Anything else as either.
        ArtQuery o = ArtQuery.of("Meadow Life", "UK | Documentaries", "Secret Gardens", "Hidden gardens of old towns.");
        assertEquals(ArtQuery.MULTI, o.type);
    }

    @Test public void sportNewsAndNoTitleTakeTheChannelsPicture() {
        assertNull(ArtQuery.of("Riverside Sport 1 HD", "UK | Sport", "Riverside Live: Cup Night", ""));
        assertNull(ArtQuery.of("Brightwater News", "UK | News", "Evening Headlines", ""));
        assertNull(ArtQuery.of("Harbour One", "UK | Entertainment", "News", ""));
        assertNull(ArtQuery.of("Harbour One", "UK | Entertainment", "To Be Announced", ""));
        assertNull(ArtQuery.of("Harbour One", "UK | Entertainment", "Harbour One", ""));
        assertNull(ArtQuery.of("Harbour One", "UK | Entertainment", "", ""));
        // A short title is still a programme ("Up" is a film).
        assertNotNull(ArtQuery.of("Lumen Cinema", "UK | Movies", "Up", ""));
    }

    @Test public void theSameTitleIsLookedForOnce() {
        ArtQuery a = ArtQuery.of("Harbour One", "UK | Entertainment", "The Quiet Inspector", "S1 E5. A missing painting.");
        ArtQuery b = ArtQuery.of("Harbour One +1", "UK | Entertainment", "New: Quiet Inspector", "S1 E6. The town hall.");
        assertEquals(a.key(), b.key());
        assertTrue(ArtQuery.same("Café & Bar", "cafe and bar"));
    }

    private static JSONObject result(String media, String name, String date, String backdrop, String poster) throws Exception {
        JSONObject o = new JSONObject().put("media_type", media);
        if ("tv".equals(media)) o.put("name", name).put("first_air_date", date);
        else o.put("title", name).put("release_date", date);
        if (backdrop != null) o.put("backdrop_path", backdrop);
        if (poster != null) o.put("poster_path", poster);
        return o;
    }

    @Test public void onlyTheSameTitleIsTakenTheFilmOfItsYearFirst() throws Exception {
        ArtQuery q = ArtQuery.of("Lumen Cinema", "UK | Movies", "Echoes of Iron (2019)", "");
        JSONObject found = new JSONObject().put("results", new JSONArray()
                .put(result("movie", "Echoes of Iron Returns", "2021-03-01", "/returns.jpg", null))
                .put(result("movie", "Echoes of Iron", "1988-05-01", "/old.jpg", null))
                .put(result("movie", "Echoes of Iron", "2019-10-11", "/new.jpg", "/new-poster.jpg")));
        assertEquals("/new.jpg", q.pick(found));
        // A poster when it has no backdrop; nothing when no title is the same.
        ArtQuery s = ArtQuery.of("Meadow Life", "UK | Documentaries", "Secret Gardens", "");
        JSONObject multi = new JSONObject().put("results", new JSONArray()
                .put(new JSONObject().put("media_type", "person").put("name", "Secret Gardens").put("profile_path", "/face.jpg"))
                .put(result("tv", "Secret Gardens", "2015-01-01", null, "/gardens-poster.jpg")));
        assertEquals("/gardens-poster.jpg", s.pick(multi));
        JSONObject other = new JSONObject().put("results", new JSONArray().put(result("tv", "Secret Gardens of Rome", "2015-01-01", "/x.jpg", null)));
        assertEquals("", s.pick(other));
        assertEquals("", s.pick(null));
    }
}
