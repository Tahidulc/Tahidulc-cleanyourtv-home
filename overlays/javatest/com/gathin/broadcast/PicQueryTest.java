package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * A programme looked for by its title (PicQuery), in both apps: its name tidied, its year and episode, whether it is worth
 * looking for, and TMDB's answer matched with care so a wrong show is never shown. Made-up answers; nothing is asked.
 */
public class PicQueryTest {
    @Test public void theTitleTidiedToItsName() {
        assertEquals("Verity", PicQuery.name("Film: Verity (2025)"));
        assertEquals("Doctor Who", PicQuery.name("New: Doctor Who S14 E2"));
        assertEquals("The Quiet Inspector", PicQuery.name("The Quiet Inspector, Series 1, Episode 5"));
        assertEquals("Night Shift", PicQuery.name("Night Shift (3/6)"));
        assertEquals("2019", PicQuery.year("Echoes of Iron (2019)", ""));
        assertEquals("2019", PicQuery.year("Echoes of Iron", "(2019) Drama."));
        assertEquals("", PicQuery.year("Echoes of Iron", "A blacksmith's daughter."));
        assertArrayEquals(new int[]{3, 2}, PicQuery.episode("Night Shift", "S3 E2. The ward fills up."));
        assertArrayEquals(new int[]{2, 4}, PicQuery.episode("The Lantern House", "Series 2, Episode 4. Mara finds the letter."));
        assertArrayEquals(new int[]{0, 7}, PicQuery.episode("Pip and the Moon", "Episode 7."));
        assertArrayEquals(new int[]{0, 0}, PicQuery.episode("Pip and the Moon", ""));
    }

    @Test public void onlyAProgrammeOfItsOwnIsLookedFor() {
        assertTrue(PicQuery.worth("The Lantern House", "Harbour One", null));
        assertTrue(PicQuery.worth("Soccer Talk Weekly", "Riverside Sport 1", PicSources.SPORT));
        assertFalse(PicQuery.worth("Live: Arsenal v Chelsea", "Riverside Sport 1", PicSources.FOOTBALL));
        assertFalse(PicQuery.worth("Riverside Live: Cup Night", "Riverside Sport 1", PicSources.SPORT));
        assertFalse(PicQuery.worth("Evening Headlines", "Brightwater News", PicSources.NEWS));
        assertFalse(PicQuery.worth("To Be Announced", "Harbour One", null));
        assertFalse(PicQuery.worth("Close", "Harbour One", null));
        assertFalse(PicQuery.worth("Harbour One", "Harbour One", null));
        assertFalse(PicQuery.worth("", "Harbour One", null));
    }

    @Test public void theSameProgrammeAndNoOther() {
        assertTrue(PicQuery.same("Café & Bar", "cafe and bar"));
        assertTrue(PicQuery.same("The Quiet Inspector", "Quiet Inspector"));
        assertTrue(PicQuery.same("The Lantern House (UK)", "The Lantern House"));
        assertTrue(PicQuery.same("Doctor Who", "Doctor Who: The Star Beast"));
        assertTrue(PicQuery.same("Grey's Anatomy", "Greys Anatomy"));
        // A long name with a letter slipped; never a longer or shorter name.
        assertTrue(PicQuery.same("Coastal Kitchens", "Coastal Kitchins"));
        assertFalse(PicQuery.same("Verity's Secret", "Verity"));
        assertFalse(PicQuery.same("Top Gear: Extra Gear", "Top Gear"));
        assertFalse(PicQuery.same("Secret Gardens of Rome", "Secret Gardens"));
        assertFalse(PicQuery.same("Live: Cup", "Cup"));
        assertFalse(PicQuery.same("Pip", "Pop"));
    }

    private static JSONObject result(String media, String name, String date, String backdrop, String poster) throws Exception {
        JSONObject o = new JSONObject().put("media_type", media);
        if ("tv".equals(media)) o.put("name", name).put("first_air_date", date);
        else o.put("title", name).put("release_date", date);
        if (backdrop != null) o.put("backdrop_path", backdrop);
        if (poster != null) o.put("poster_path", poster);
        return o;
    }

    @Test public void theAnswerPickedWithCare() throws Exception {
        JSONObject films = new JSONObject().put("results", new JSONArray()
                .put(result("movie", "Echoes of Iron Returns", "2021-03-01", "/returns.jpg", null))
                .put(result("movie", "Echoes of Iron", "1988-05-01", "/old.jpg", null))
                .put(result("movie", "Echoes of Iron", "2019-10-11", "/new.jpg", "/new-poster.jpg")));
        assertEquals("/new.jpg", PicQuery.pick(films, "movie", "Echoes of Iron", "2019"));
        assertEquals("/new.jpg", PicQuery.pick(films, "movie", "Echoes of Iron", "2020"));
        // A film of another year is another film.
        assertEquals("", PicQuery.pick(films, "movie", "Echoes of Iron", "2005"));
        assertEquals("/old.jpg", PicQuery.pick(films, "movie", "Echoes of Iron", ""));
        // Either: films and series only (not a person); a poster when there is no backdrop; nothing with no picture.
        JSONObject multi = new JSONObject().put("results", new JSONArray()
                .put(new JSONObject().put("media_type", "person").put("name", "Secret Gardens").put("profile_path", "/face.jpg"))
                .put(result("tv", "Secret Gardens", "2015-01-01", null, null))
                .put(result("tv", "Secret Gardens", "2016-01-01", null, "/gardens-poster.jpg")));
        assertEquals("/gardens-poster.jpg", PicQuery.pick(multi, "multi", "Secret Gardens", ""));
        assertEquals("", PicQuery.pick(new JSONObject().put("results", new JSONArray()
                .put(result("tv", "Secret Gardens of Rome", "2015-01-01", "/x.jpg", null))), "tv", "Secret Gardens", ""));
        JSONObject adult = new JSONObject().put("results", new JSONArray().put(result("movie", "Night Shift", "2001-01-01", "/a.jpg", null)
                .put("adult", true)));
        assertEquals("", PicQuery.pick(adult, "movie", "Night Shift", ""));
        assertEquals("", PicQuery.pick(null, "tv", "Night Shift", ""));
    }
}
