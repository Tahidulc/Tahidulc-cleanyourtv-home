package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * A title's page extras as TMDB's page gives them (made-up pages here): the English PNG logo, the first ten of the cast
 * with their parts and photos, the seasons' posters, the years and the age rating; and kept on the TV, read back the same.
 */
public class TmdbExtrasTest {
    private static JSONObject series() throws Exception {
        JSONObject j = new JSONObject();
        j.put("first_air_date", "2019-03-01").put("last_air_date", "2024-11-20");
        JSONArray logos = new JSONArray()
                .put(new JSONObject().put("iso_639_1", "de").put("file_path", "/de.png"))
                .put(new JSONObject().put("iso_639_1", JSONObject.NULL).put("file_path", "/plain.png"))
                .put(new JSONObject().put("iso_639_1", "en").put("file_path", "/en.svg"))
                .put(new JSONObject().put("iso_639_1", "en").put("file_path", "/en.png"));
        j.put("images", new JSONObject().put("logos", logos));
        // Someone with no name first (left out), then twelve (the first ten kept).
        JSONArray cast = new JSONArray().put(new JSONObject().put("name", "").put("character", "Nobody"));
        for (int i = 0; i < 12; i++) {
            cast.put(new JSONObject().put("name", "Person " + i).put("character", i == 1 ? JSONObject.NULL : "Part " + i)
                    .put("profile_path", i == 2 ? "" : "/p" + i + ".jpg"));
        }
        j.put("credits", new JSONObject().put("cast", cast));
        j.put("seasons", new JSONArray()
                .put(new JSONObject().put("season_number", 0).put("poster_path", "/s0.jpg"))
                .put(new JSONObject().put("season_number", 1).put("poster_path", "/s1.jpg"))
                .put(new JSONObject().put("season_number", 2).put("poster_path", JSONObject.NULL)));
        j.put("content_ratings", new JSONObject().put("results", new JSONArray()
                .put(new JSONObject().put("iso_3166_1", "US").put("rating", "TV-14"))
                .put(new JSONObject().put("iso_3166_1", "GB").put("rating", " 15 "))));
        return j;
    }

    @Test public void aSeriesExtras() throws Exception {
        Tmdb.Extras x = Tmdb.extrasOf(series(), true);
        // English and a PNG first (the wordless one only when there is none), never another language's.
        assertEquals(Tmdb.image("/en.png", "w500"), x.logo);
        assertEquals(10, x.cast.size());
        assertArrayEquals(new String[]{"Person 0", "Part 0", Tmdb.image("/p0.jpg", "w185")}, x.cast.get(0));
        assertEquals("Person 9", x.cast.get(9)[0]);
        assertEquals("", x.cast.get(1)[1]);
        assertEquals("", x.cast.get(2)[2]);
        assertEquals(Tmdb.image("/s1.jpg", "w92"), x.seasons.get(1));
        assertEquals(Tmdb.image("/s0.jpg", "w92"), x.seasons.get(0));
        assertTrue(!x.seasons.containsKey(2));
        assertEquals("2019–2024", x.years);
        // The UK's rating before the US's.
        assertEquals("15", x.age);
    }

    @Test public void theWordlessLogoWhenThereIsNoEnglishOne() throws Exception {
        JSONObject j = new JSONObject().put("images", new JSONObject().put("logos", new JSONArray()
                .put(new JSONObject().put("iso_639_1", "fr").put("file_path", "/fr.png"))
                .put(new JSONObject().put("iso_639_1", JSONObject.NULL).put("file_path", "/plain.png"))));
        assertEquals(Tmdb.image("/plain.png", "w500"), Tmdb.extrasOf(j, true).logo);
        assertEquals("", Tmdb.extrasOf(new JSONObject(), true).logo);
    }

    @Test public void aFilmsExtras() throws Exception {
        JSONObject f = new JSONObject().put("release_date", "2025-05-14");
        f.put("release_dates", new JSONObject().put("results", new JSONArray()
                .put(new JSONObject().put("iso_3166_1", "US").put("release_dates", new JSONArray()
                        .put(new JSONObject().put("certification", ""))
                        .put(new JSONObject().put("certification", "PG-13"))))));
        Tmdb.Extras x = Tmdb.extrasOf(f, false);
        assertEquals("", x.logo);
        assertEquals("2025", x.years);
        // No UK rating: the US's first one given.
        assertEquals("PG-13", x.age);
        assertTrue(x.cast.isEmpty());
        assertTrue(x.seasons.isEmpty());
        // A series still running this year: one year, not "2025–2025".
        JSONObject s = new JSONObject().put("first_air_date", "2025-01-02").put("last_air_date", "2025-03-04");
        assertEquals("2025", Tmdb.extrasOf(s, true).years);
    }

    @Test public void keptAndReadBackTheSame() throws Exception {
        Tmdb.Extras x = Tmdb.extrasOf(series(), true);
        JSONObject kept = new JSONObject(Tmdb.kept(x).toString());
        assertTrue(kept.optLong("_at") > 0);
        Tmdb.Extras back = Tmdb.fromKept(kept);
        assertEquals(x.logo, back.logo);
        assertEquals(x.years, back.years);
        assertEquals(x.age, back.age);
        assertEquals(x.cast.size(), back.cast.size());
        for (int i = 0; i < x.cast.size(); i++) assertArrayEquals(x.cast.get(i), back.cast.get(i));
        assertEquals(x.seasons, back.seasons);
        // Nothing kept: nothing shown, nothing broken.
        Tmdb.Extras none = Tmdb.fromKept(new JSONObject());
        assertEquals("", none.logo);
        assertTrue(none.cast.isEmpty());
        assertTrue(none.seasons.isEmpty());
    }
}
