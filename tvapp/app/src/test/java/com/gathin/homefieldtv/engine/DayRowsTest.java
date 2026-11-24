package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The day's rows (DayRows): the same all day (nothing jumps while browsing), another choice and order the next day,
 * every turning row coming up within a few days, the seasonal rows only in season, the part of the day's row, one genre
 * and one mood a day going round them all, and how long a row's titles are kept before they are asked for again.
 */
public class DayRowsTest {
    private static final LocalDateTime TUESDAY = LocalDateTime.of(2026, 11, 24, 20, 15);

    private static List<String> ids(List<CategorySet.Entry> rows) {
        List<String> out = new ArrayList<>();
        for (CategorySet.Entry e : rows) out.add(e.id);
        return out;
    }

    private static List<String> day(String page, LocalDateTime t) {
        return ids(DayRows.of(page, t));
    }

    @Test public void theSameAllDayAndAnotherTheNext() {
        for (String page : new String[]{CategorySet.HOME, CategorySet.MOVIES, CategorySet.SHOWS}) {
            List<String> morning = day(page, TUESDAY.with(LocalTime.of(9, 0))), noon = day(page, TUESDAY.with(LocalTime.of(13, 30)));
            // The same rows in the same order, but for the part of the day's own row.
            List<String> a = new ArrayList<>(morning), b = new ArrayList<>(noon);
            a.remove("morning");
            b.remove("afternoon");
            assertEquals(page, a, b);
            assertEquals(page, day(page, TUESDAY), day(page, TUESDAY.plusMinutes(30)));
            assertNotEquals(page + ": another day, other rows", day(page, TUESDAY), day(page, TUESDAY.plusDays(1)));
        }
    }

    @Test public void continueWatchingFirstTheCarouselFirstAndTheHeadsAlways() {
        for (int d = 0; d < 10; d++) {
            List<String> home = day(CategorySet.HOME, TUESDAY.plusDays(d));
            assertEquals("continue", home.get(0));
            assertTrue(home.containsAll(Arrays.asList("top10", "fresh", "because.0")));
            assertTrue("a head in second place", Arrays.asList("top10", "fresh", "because.0").contains(home.get(1)));
            assertEquals(new HashSet<>(home).size(), home.size());
            List<String> movies = day(CategorySet.MOVIES, TUESDAY.plusDays(d));
            assertEquals("hero", movies.get(0));
            assertTrue(movies.containsAll(Arrays.asList("top10", "trending", "fresh")));
            assertEquals("hero", day(CategorySet.SHOWS, TUESDAY.plusDays(d)).get(0));
        }
        // The heads' order changes with the day.
        Set<String> seconds = new HashSet<>();
        for (int d = 0; d < 14; d++) seconds.add(day(CategorySet.HOME, TUESDAY.plusDays(d)).get(1));
        assertTrue(seconds.toString(), seconds.size() > 1);
    }

    @Test public void everyTurningRowComesUpWithinAFewDays() {
        for (String page : new String[]{CategorySet.HOME, CategorySet.MOVIES, CategorySet.SHOWS}) {
            Set<String> turning = new HashSet<>(), seen = new HashSet<>();
            for (CategorySet.Entry e : CategorySet.of(page)) if (e.when.isEmpty() && e.kind == CategorySet.ROW) turning.add(e.id);
            for (int d = 0; d < 5; d++) seen.addAll(day(page, TUESDAY.plusDays(d)));
            assertTrue(page + " missed " + minus(turning, seen), seen.containsAll(turning));
        }
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.removeAll(b);
        return out;
    }

    @Test public void aGenreAndAMoodOfTheDayGoingRoundThemAll() {
        Set<String> genres = new HashSet<>(), moods = new HashSet<>();
        for (int d = 0; d < 20; d++) {
            List<String> home = day(CategorySet.HOME, TUESDAY.plusDays(d));
            int g = 0, m = 0;
            for (String id : home) {
                if (id.startsWith("genre.")) {
                    g++;
                    genres.add(id);
                }
                if (id.startsWith("mood.")) {
                    m++;
                    moods.add(id);
                }
            }
            assertEquals(1, g);
            assertEquals(1, m);
        }
        assertEquals("every genre in 17 days", 17, genres.size());
        assertEquals(7, moods.size());
    }

    @Test public void seasonalRowsOnlyInSeason() {
        assertTrue(day(CategorySet.HOME, LocalDateTime.of(2026, 12, 20, 19, 0)).contains("christmas"));
        assertFalse(day(CategorySet.HOME, TUESDAY).contains("christmas"));
        assertTrue(day(CategorySet.MOVIES, LocalDateTime.of(2026, 10, 31, 22, 0)).contains("halloween"));
        assertFalse(day(CategorySet.MOVIES, LocalDateTime.of(2026, 11, 1, 22, 0)).contains("halloween"));
        assertTrue(day(CategorySet.HOME, LocalDateTime.of(2027, 2, 14, 12, 0)).contains("valentine"));
        assertTrue(day(CategorySet.MOVIES, LocalDateTime.of(2026, 7, 4, 15, 0)).contains("summer"));
        assertTrue(day(CategorySet.SHOWS, LocalDateTime.of(2026, 12, 1, 9, 0)).contains("christmas"));
        // In season, early on the page: right after the first head.
        List<String> dec = day(CategorySet.HOME, LocalDateTime.of(2026, 12, 20, 19, 0));
        assertEquals(2, dec.indexOf("christmas"));
        assertTrue(DayRows.inSeason("12-26/01-06", LocalDate.of(2027, 1, 2)));
        assertFalse(DayRows.inSeason("12-26/01-06", LocalDate.of(2027, 1, 7)));
        assertFalse(DayRows.inSeason("nonsense", LocalDate.of(2027, 1, 7)));
    }

    @Test public void thePartOfTheDaysRow() {
        assertEquals("morning", DayRows.part(LocalTime.of(5, 0)));
        assertEquals("afternoon", DayRows.part(LocalTime.of(12, 0)));
        assertEquals("evening", DayRows.part(LocalTime.of(20, 59)));
        assertEquals("late", DayRows.part(LocalTime.of(23, 0)));
        assertEquals("late", DayRows.part(LocalTime.of(4, 59)));
        List<String> evening = day(CategorySet.HOME, TUESDAY);
        assertTrue(evening.contains("evening"));
        assertFalse(evening.contains("late") || evening.contains("morning") || evening.contains("afternoon"));
        assertTrue(day(CategorySet.SHOWS, TUESDAY.withHour(23)).contains("late"));
        assertEquals("2026-11-24|evening", DayRows.key(TUESDAY));
        assertEquals("2026-11-24|late", DayRows.key(TUESDAY.withHour(23)));
    }

    @Test public void howLongARowsTitlesAreKept() {
        long hour = 3600_000L;
        assertEquals(3 * hour, DayRows.expiry("top:trending/all/day"));
        assertEquals(3 * hour, DayRows.expiry("tmdb:trending/movie/day"));
        assertEquals(4 * hour, DayRows.expiry("tmdb:trending/tv/week"));
        assertEquals(4 * hour, DayRows.expiry("soon:discover/movie?primary_release_date.gte={today+1}"));
        assertEquals(4 * hour, DayRows.expiry("tmdb:tv/on_the_air"));
        assertEquals(6 * hour, DayRows.expiry("tmdb:discover/movie?with_genres=37"));
        long made = 1_000_000_000L;
        assertFalse(DayRows.expired(made, made + 2 * hour, "tmdb:discover/movie?with_genres=37"));
        assertTrue(DayRows.expired(made, made + 6 * hour, "tmdb:discover/movie?with_genres=37"));
        assertTrue(DayRows.expired(made, made + 3 * hour, "top:trending/all/day"));
        assertTrue("a clock put back", DayRows.expired(made, made - 1, "top:trending/all/day"));
        // TMDB's lists are kept as long as their rows.
        assertEquals(3 * hour, TmdbLists.keepMs("trending/all/day"));
        assertEquals(6 * hour, TmdbLists.keepMs("discover/movie?with_genres=37"));
    }

    @Test public void aThemedRowsTitlesTurnWithTheDay() {
        assertTrue(DayRows.turns("tmdb:discover/movie?with_genres=37&sort_by=popularity.desc"));
        assertTrue(DayRows.turns("genre:movie=37;words=western"));
        assertFalse(DayRows.turns("tmdb:trending/movie/day"));
        assertFalse(DayRows.turns("top:trending/all/day"));
        assertFalse(DayRows.turns("tmdb:discover/movie?sort_by=primary_release_date.desc&primary_release_date.lte={today}|new:movie"));
        assertFalse(DayRows.turns("continue"));
        List<Integer> list = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        long d = LocalDate.of(2026, 11, 24).toEpochDay();
        assertEquals(DayRows.turn(list, d, 12), DayRows.turn(list, d, 12));
        Set<Integer> firsts = new HashSet<>();
        for (int k = 0; k < 12; k++) {
            List<Integer> t = DayRows.turn(list, d + k, 12);
            assertEquals(new HashSet<>(list), new HashSet<>(t));
            firsts.add(t.get(0));
        }
        assertTrue("the first title changes with the days", firsts.size() > 6);
        assertEquals(List.of(1), DayRows.turn(List.of(1), d, 12));
    }

    @Test public void theTableHasTheLivingRowsAndOnlyComingSoonMayShowWhatCantBePlayed() {
        Set<String> soon = new HashSet<>();
        for (String page : new String[]{CategorySet.HOME, CategorySet.MOVIES, CategorySet.SHOWS}) {
            List<String> all = ids(CategorySet.of(page));
            assertTrue(page, all.containsAll(Arrays.asList("top10", "fresh", "hidden.gems", "soon", "torbox.new", "christmas", "morning",
                    "afternoon", "evening", "late")));
            for (CategorySet.Entry e : CategorySet.of(page)) {
                if (e.soon()) {
                    soon.add(page + "." + e.id);
                    assertEquals("Coming Soon", e.title);
                }
            }
        }
        assertEquals(3, soon.size());
        assertTrue(ids(CategorySet.of(CategorySet.HOME)).containsAll(Arrays.asList("halloween", "valentine", "summer", "oscars")));
    }
}
