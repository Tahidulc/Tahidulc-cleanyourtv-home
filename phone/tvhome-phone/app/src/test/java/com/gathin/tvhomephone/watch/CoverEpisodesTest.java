package com.gathin.tvhomephone.watch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** The one row of every season's episodes: where each season starts, which season a card is in, and each card's words. */
public class CoverEpisodesTest {
    @Test public void seasonsFollowOneAnotherInTheRow() {
        List<int[]> seasons = Arrays.asList(new int[]{1, 10}, new int[]{2, 8}, new int[]{3, 0}, new int[]{4, 6});
        int[] starts = CoverEpisodes.starts(seasons);
        assertArrayEquals(new int[]{0, 10, 18, 18}, starts);
        assertEquals(0, CoverEpisodes.seasonAt(starts, 0));
        assertEquals(0, CoverEpisodes.seasonAt(starts, 9));
        assertEquals(1, CoverEpisodes.seasonAt(starts, 10));
        assertEquals(1, CoverEpisodes.seasonAt(starts, 17));
        // An empty season has no cards: the row goes on to the next.
        assertEquals(3, CoverEpisodes.seasonAt(starts, 18));
        assertEquals(3, CoverEpisodes.seasonAt(starts, 23));
    }

    @Test public void eachCardSaysItsPlaceLengthAndDay() {
        LocalDate today = LocalDate.of(2026, 11, 16);
        assertEquals("S1 E8", CoverEpisodes.code(1, 8));
        assertEquals("E3", CoverEpisodes.code(0, 3));
        assertEquals("1h 7m", CoverEpisodes.length(67));
        assertEquals("54m", CoverEpisodes.length(54));
        assertEquals("1h 0m", CoverEpisodes.length(60));
        assertEquals("", CoverEpisodes.length(0));
        assertEquals("1h 7m, 9 Oct 2022", CoverEpisodes.facts(67, LocalDate.of(2022, 10, 9), today));
        assertEquals("9 Oct 2022", CoverEpisodes.facts(0, LocalDate.of(2022, 10, 9), today));
        assertEquals("Out 16 Dec 2026", CoverEpisodes.facts(60, LocalDate.of(2026, 12, 16), today));
        assertEquals("Date to be announced", CoverEpisodes.facts(60, null, today));
        assertEquals("4 Sep 2022", CoverEpisodes.day(LocalDate.of(2022, 9, 4)));
    }

    @Test public void aSeasonsEpisodesAreReadInOrderOnce() throws Exception {
        JSONArray eps = new JSONArray()
                .put(new JSONObject().put("episode_number", 2).put("name", "The Second").put("overview", "Two.")
                        .put("still_path", "/b.jpg").put("air_date", "2022-08-28").put("runtime", 54))
                .put(new JSONObject().put("episode_number", 1).put("name", " The First ").put("overview", "One.")
                        .put("still_path", JSONObject.NULL).put("air_date", "2022-08-21").put("runtime", 66))
                .put(new JSONObject().put("episode_number", 2).put("name", "Twice"))
                .put(new JSONObject().put("name", "No number"));
        List<CoverEpisodes.Episode> got = CoverEpisodes.episodes(1, eps);
        assertEquals(2, got.size());
        CoverEpisodes.Episode first = got.get(0);
        assertEquals(1, first.number);
        assertEquals("The First", first.name);
        assertNull(first.still);
        assertTrue(first.read);
        assertEquals(LocalDate.of(2022, 8, 21), first.air);
        assertEquals(66, first.minutes);
        assertEquals("/b.jpg", got.get(1).still);
        assertFalse(CoverEpisodes.episodes(1, null).iterator().hasNext());
    }
}
