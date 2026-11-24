package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/**
 * Which trailer plays in a poster, from TMDB's videos for a title (made-up answers, never the real API): YouTube's only,
 * trailers and teasers only, the family's language first, the trailer before the teaser and the official one before
 * the others, the newest first; the playlist's own trailer after TMDB's (or alone); a bad key never; three at most.
 */
public class TrailerChooserTest {
    private static JSONObject video(String key, String type, String lang, boolean official, String site, String when) throws Exception {
        JSONObject o = new JSONObject().put("key", key).put("type", type).put("site", site).put("official", official).put("name", type + " " + key)
                .put("published_at", when);
        if (lang == null) o.put("iso_639_1", JSONObject.NULL);
        else o.put("iso_639_1", lang);
        return o;
    }

    private static JSONObject answer(JSONObject... vs) throws Exception {
        JSONArray a = new JSONArray();
        for (JSONObject v : vs) a.put(v);
        return new JSONObject().put("id", 4242).put("results", a);
    }

    @Test public void theOfficialTrailerInTheFamilysLanguageFirst() throws Exception {
        JSONObject j = answer(
                video("featurette1", "Featurette", "en", true, "YouTube", "2024-03-01T10:00:00.000Z"),
                video("teaserEN001", "Teaser", "en", true, "YouTube", "2024-01-01T10:00:00.000Z"),
                video("trailerEN02", "Trailer", "en", false, "YouTube", "2024-02-10T10:00:00.000Z"),
                video("trailerEN01", "Trailer", "en", true, "YouTube", "2024-02-01T10:00:00.000Z"),
                video("vimeoTrail1", "Trailer", "en", true, "Vimeo", "2024-02-05T10:00:00.000Z"),
                video("clipEN00001", "Clip", "en", true, "YouTube", "2024-02-01T10:00:00.000Z"));
        List<Videos.Video> vs = Videos.parse(j);
        assertEquals("Vimeo's left out", 5, vs.size());
        assertEquals(List.of("trailerEN01", "trailerEN02", "teaserEN001"), Videos.trailers(vs, "en", ""));
        // A French family: its own trailer first, even a teaser of its own before English trailers.
        JSONObject fr = answer(
                video("trailerEN01", "Trailer", "en", true, "YouTube", "2024-02-01T10:00:00.000Z"),
                video("teaserFR001", "Teaser", "fr", true, "YouTube", "2024-01-01T10:00:00.000Z"),
                video("trailerFR01", "Trailer", "fr", true, "YouTube", "2024-01-02T10:00:00.000Z"));
        assertEquals(List.of("trailerFR01", "teaserFR001", "trailerEN01"), Videos.trailers(Videos.parse(fr), "fr", ""));
        // English before one with no language given, before another language's.
        JSONObject mixed = answer(
                video("trailerDE01", "Trailer", "de", true, "YouTube", "2024-03-01T10:00:00.000Z"),
                video("trailerNo01", "Trailer", null, true, "YouTube", "2024-03-01T10:00:00.000Z"),
                video("trailerEN01", "Trailer", "en", true, "YouTube", "2024-01-01T10:00:00.000Z"));
        assertEquals(List.of("trailerEN01", "trailerNo01", "trailerDE01"), Videos.trailers(Videos.parse(mixed), "en", ""));
    }

    @Test public void thePlaylistsOwnAfterTmdbsOrAloneAndNeverABadKey() throws Exception {
        JSONObject j = answer(video("trailerEN01", "Trailer", "en", true, "YouTube", "2024-02-01T10:00:00.000Z"));
        assertEquals(List.of("trailerEN01", "playlist001"), Videos.trailers(Videos.parse(j), "en", "https://www.youtube.com/watch?v=playlist001&t=4"));
        assertEquals("the same once", List.of("trailerEN01"), Videos.trailers(Videos.parse(j), "en", "trailerEN01"));
        assertEquals(List.of("playlist001"), Videos.trailers(Videos.parse(new JSONObject()), "en", "https://youtu.be/playlist001"));
        assertTrue(Videos.trailers(null, "en", "not a trailer").isEmpty());
        // A key that isn't one (too short, odd characters) is never played.
        JSONObject bad = answer(video("short", "Trailer", "en", true, "YouTube", ""), video("bad key \"';", "Trailer", "en", true, "YouTube", ""));
        assertTrue(Videos.parse(bad).isEmpty());
        // Three at most, none twice.
        JSONObject many = answer(
                video("trailerEN01", "Trailer", "en", true, "YouTube", "2024-01-01"), video("trailerEN01", "Trailer", "en", true, "YouTube", "2024-01-01"),
                video("trailerEN02", "Trailer", "en", true, "YouTube", "2024-01-02"), video("trailerEN03", "Trailer", "en", true, "YouTube", "2024-01-03"),
                video("trailerEN04", "Trailer", "en", true, "YouTube", "2024-01-04"));
        List<String> three = Videos.trailers(Videos.parse(many), "en", "playlist001");
        assertEquals(List.of("trailerEN04", "trailerEN03", "trailerEN02"), three);
    }

    @Test public void aTitlesVideosAreKeptWithItsExtras() throws Exception {
        JSONObject page = new JSONObject().put("videos", answer(
                video("trailerEN01", "Trailer", "en", true, "YouTube", "2024-02-01"),
                video("bts00000001", "Behind the Scenes", "en", true, "YouTube", "2024-02-02")));
        Tmdb.Extras x = Tmdb.extrasOf(page, false);
        assertEquals(2, x.videos.size());
        Tmdb.Extras back = Tmdb.fromKept(new JSONObject(Tmdb.kept(x).toString()));
        assertEquals("trailerEN01", back.videos.get(0).key);
        assertEquals("Behind the scenes", back.videos.get(1).badge());
        assertTrue(!back.old);
        // Kept before videos were: asked again when TMDB can be.
        assertTrue(Tmdb.fromKept(new JSONObject().put("logo", "")).old);
        assertEquals("dQw4w9WgXcQ", Videos.key("https://www.youtube.com/embed/dQw4w9WgXcQ"));
    }
}
