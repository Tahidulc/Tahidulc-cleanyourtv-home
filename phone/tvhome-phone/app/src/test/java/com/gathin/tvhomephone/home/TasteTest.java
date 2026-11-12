package com.gathin.tvhomephone.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Recommendation scoring from what was watched (Taste): weights, scores, ranking, and why each is there. */
public class TasteTest {
    private static final long DAY = 86_400_000L, NOW = 1_800_000_000_000L;
    private static final int CRIME = 80, DRAMA = 18, COMEDY = 35, ANIMATION = 16, THRILLER = 53;

    private static Taste.Seen seen(int id, boolean tv, String name, double w, int[] genres, int[] people) {
        Taste.Seen s = new Taste.Seen(id, tv, name, w);
        s.genres = genres;
        s.people = people;
        return s;
    }

    private static Taste.Candidate cand(int id, boolean tv, int[] genres, int[] people, double vote) {
        Taste.Candidate c = new Taste.Candidate(id, tv, "T" + id);
        c.genres = genres;
        c.people = people;
        c.vote = vote;
        return c;
    }

    @Test public void finishedOutweighsBarelyStarted() {
        double done = Taste.weight(true, 1f, false, NOW, NOW);
        double started = Taste.weight(false, 0.05f, false, NOW, NOW);
        double half = Taste.weight(false, 0.5f, false, NOW, NOW);
        double loved = Taste.weight(true, 1f, true, NOW, NOW);
        assertEquals(1.0, done, 1e-9);
        assertTrue(started < half && half < done && done < loved);
        assertEquals(0.25 + 0.75 * 0.05, started, 1e-6);
    }

    @Test public void olderViewingCountsLess() {
        double now = Taste.weight(true, 1f, false, NOW, NOW);
        double then = Taste.weight(true, 1f, false, NOW - 45 * DAY, NOW);
        assertEquals(now / 2, then, 1e-6);
        // No time known: as if now; a time ahead of now: no more than now.
        assertEquals(now, Taste.weight(true, 1f, false, 0, NOW), 1e-9);
        assertEquals(now, Taste.weight(true, 1f, false, NOW + DAY, NOW), 1e-9);
    }

    @Test public void sharedGenresAndPeopleScoreHigher() {
        Taste t = Taste.of(Arrays.asList(
                seen(1, false, "Heat", 1.0, new int[]{CRIME, THRILLER}, new int[]{100, 101}),
                seen(2, false, "Collateral", 0.8, new int[]{CRIME, DRAMA}, new int[]{100, 102})));
        Taste.Candidate crime = cand(10, false, new int[]{CRIME, THRILLER}, new int[0], 7);
        Taste.Candidate comedy = cand(11, false, new int[]{COMEDY}, new int[0], 7);
        Taste.Candidate samePeople = cand(12, false, new int[]{CRIME, THRILLER}, new int[]{100}, 7);
        assertTrue(t.score(crime) > t.score(comedy));
        assertTrue(t.score(samePeople) > t.score(crime));
        // People not yet read count for nothing (not against it).
        Taste.Candidate unread = cand(13, false, new int[]{CRIME, THRILLER}, null, 7);
        assertEquals(t.score(crime), t.score(unread), 1e-9);
    }

    @Test public void recommendedForWhatWasWatchedCounts() {
        Taste t = Taste.of(Collections.singletonList(seen(1, true, "The Wire", 1, new int[]{CRIME}, new int[0])));
        Taste.Candidate a = cand(20, true, new int[]{DRAMA}, new int[0], 7);
        Taste.Candidate b = cand(21, true, new int[]{DRAMA}, new int[0], 7);
        b.because.add("The Wire");
        assertTrue(t.score(b) > t.score(a));
        assertEquals("Because you watched The Wire", t.why(b));
        // Otherwise the liked genre it shares; none shared: no reason given.
        Taste.Candidate crime = cand(22, true, new int[]{DRAMA, CRIME}, new int[0], 7);
        assertEquals("More Crime", t.why(crime));
        assertNull(t.why(a));
    }

    @Test public void rankKeepsKindLeavesOutWatchedAndSortsBest() {
        Taste t = Taste.of(Collections.singletonList(seen(1, false, "Toy Story", 1, new int[]{ANIMATION}, new int[0])));
        List<Taste.Candidate> all = new ArrayList<>();
        all.add(cand(1, false, new int[]{ANIMATION}, new int[0], 8));      // watched already
        all.add(cand(2, false, new int[]{COMEDY}, new int[0], 9));
        all.add(cand(3, false, new int[]{ANIMATION}, new int[0], 7));
        all.add(cand(4, true, new int[]{ANIMATION}, new int[0], 8));       // a series: not a film
        all.add(cand(5, false, new int[]{ANIMATION}, new int[0], 6));      // rated down
        Set<String> skip = new HashSet<>(Arrays.asList("mv:1", "mv:5"));
        List<Taste.Candidate> films = t.rank(all, false, skip, 10);
        assertEquals(2, films.size());
        assertEquals(3, films.get(0).id);
        assertEquals(2, films.get(1).id);
        List<Taste.Candidate> shows = t.rank(all, true, skip, 10);
        assertEquals(1, shows.size());
        assertEquals(1, t.rank(all, false, skip, 1).size());
    }

    @Test public void withNoTasteItRanksByPopularity() {
        Taste t = Taste.of(new ArrayList<>());
        assertTrue(t.empty());
        Taste.Candidate a = cand(1, false, new int[]{COMEDY}, null, 6);
        Taste.Candidate b = cand(2, false, new int[]{DRAMA}, null, 8);
        a.popularity = 3;
        b.popularity = 12;
        List<Taste.Candidate> out = t.rank(Arrays.asList(b, a), false, new HashSet<>(), 5);
        assertEquals(1, out.get(0).id);
        assertEquals(2, out.get(1).id);
    }

    @Test public void topGenresStrongestFirst() {
        Taste t = Taste.of(Arrays.asList(
                seen(1, false, "a", 1, new int[]{CRIME}, new int[0]),
                seen(2, false, "b", 1, new int[]{CRIME, DRAMA}, new int[0]),
                seen(3, false, "c", 0.5, new int[]{COMEDY}, new int[0])));
        assertEquals(Arrays.asList(CRIME, DRAMA), t.topGenres(2, null));
        assertEquals(Collections.singletonList(COMEDY), t.topGenres(2, new HashSet<>(Collections.singletonList(COMEDY))));
        assertFalse(t.empty());
    }
}
