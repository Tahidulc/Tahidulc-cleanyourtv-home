package com.gathin.tvhomephone.watch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.time.LocalDate;

/** Where a title stands on its poster, its logo, and what its poster shows. */
public class RanksTest {
    private static JSONObject list(int... ids) throws Exception {
        JSONArray r = new JSONArray();
        for (int id : ids) r.put(new JSONObject().put("id", id));
        return new JSONObject().put("results", r);
    }

    @Test public void todaysPlacesThenTheWeeksThenNew() throws Exception {
        Ranks.Places p = new Ranks.Places();
        Ranks.place(p.today, list(11, 0, 12, 13), false, 1, Ranks.TODAY);
        Ranks.place(p.today, list(21, 22), true, 1, Ranks.TODAY);
        p.week.add("mv:40");
        LocalDate today = LocalDate.of(2026, 11, 16);

        assertEquals("#1 Today", Ranks.label(p, false, 11, "", today));
        // A result without an id takes no place.
        assertEquals("#2 Today", Ranks.label(p, false, 12, "", today));
        // Films and series are counted on their own.
        assertEquals("#1 Today", Ranks.label(p, true, 21, "", today));
        assertNull(Ranks.label(p, true, 12, "", today));
        assertEquals("Trending", Ranks.label(p, false, 40, "", today));
        assertEquals("New", Ranks.label(p, false, 99, "2026-10-30", today));
        assertNull(Ranks.label(p, false, 99, "2026-10-01", today));
        // Not out yet is not new.
        assertNull(Ranks.label(p, false, 99, "2026-12-01", today));
        assertNull(Ranks.label(null, false, 11, "nonsense", today));
        assertEquals("New", Ranks.label(null, false, 0, "2026-11-16", today));
    }

    @Test public void placesStopAtTheirLimit() throws Exception {
        Ranks.Places p = new Ranks.Places();
        Ranks.place(p.today, list(1, 2, 3, 4), false, 19, Ranks.TODAY);
        assertEquals(Integer.valueOf(19), p.today.get("mv:1"));
        assertEquals(Integer.valueOf(20), p.today.get("mv:2"));
        assertNull(p.today.get("mv:3"));
    }

    @Test public void theEnglishLogoFirstThenOneWithoutWords() throws Exception {
        JSONArray logos = new JSONArray()
                .put(new JSONObject().put("file_path", "/fr.png").put("iso_639_1", "fr"))
                .put(new JSONObject().put("file_path", "/none.png").put("iso_639_1", JSONObject.NULL))
                .put(new JSONObject().put("file_path", "/en.png").put("iso_639_1", "en"));
        assertEquals("https://image.tmdb.org/t/p/w500/en.png", Logos.pick(logos));
        logos.remove(2);
        assertEquals("https://image.tmdb.org/t/p/w500/none.png", Logos.pick(logos));
        logos.remove(1);
        assertNull(Logos.pick(logos));
        assertNull(Logos.pick(null));
    }

    @Test public void aPosterShowsTheListsOwnWordsFirst() {
        Title t = new Title();
        t.id = 7;
        t.title = "Dune";
        t.year = "2021";
        t.vote = 7.84;
        t.genres = new int[]{878, 12};
        t.badge = "#2 Today";
        t.quality = com.gathin.tvhomephone.live.SourceQuality.of("Dune 2160p Atmos");
        assertEquals("Sci-Fi • ★ 7.8", PosterAdapter.info(t).line);
        assertEquals("#2 Today", PosterAdapter.info(t).badge);
        t.foot = "S1 E3 watched";
        assertEquals("S1 E3 watched", PosterAdapter.info(t).line);
        assertEquals("Dune, 2021, #2 Today, S1 E3 watched, 4K, Dolby Atmos", PosterAdapter.spoken(t));
        Logos.put(false, 7, "");
        assertEquals("", PosterAdapter.info(t).logo);
        assertEquals(true, PosterAdapter.info(t).logoKnown);
    }
}
