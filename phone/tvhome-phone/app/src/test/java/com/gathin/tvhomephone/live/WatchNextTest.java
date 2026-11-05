package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Continue watching's list, the next episode, and the Sport guide's order (pure parts). */
public class WatchNextTest {
    private static Vod.Episode ep(int s, int n) {
        return new Vod.Episode(s, n, "Ep " + n, "x:series:" + s + n + ".mkv", "", "");
    }

    @Test public void theNextEpisodeIsInTheSameSeasonThenTheNext() {
        List<Vod.Episode> eps = Arrays.asList(ep(1, 1), ep(1, 2), ep(1, 3), ep(2, 1), ep(2, 2));
        assertEquals(1, Vod.nextIndex(eps, 1, 1));
        assertEquals(3, Vod.nextIndex(eps, 1, 3));
        assertEquals(4, Vod.nextIndex(eps, 2, 1));
        assertEquals(-1, Vod.nextIndex(eps, 2, 2));
        // A gap in the numbers (episode 4 missing): on to the next there is.
        assertEquals(3, Vod.nextIndex(Arrays.asList(ep(1, 1), ep(1, 3), ep(1, 5), ep(2, 1)), 1, 5));
        assertEquals(-1, Vod.nextIndex(new ArrayList<>(), 1, 1));
    }

    private static VodHistory.Entry entry(String key, String name, boolean tv, int tmdb, long at) {
        VodHistory.Entry e = new VodHistory.Entry();
        e.key = key;
        e.name = name;
        e.title = name;
        e.tv = tv;
        e.tmdb = tmdb;
        e.at = at;
        e.plays.add(new String[]{"x:movie:" + key + ".mkv", "", ""});
        return e;
    }

    @Test public void aSeriesIsThereOnceAtItsLatestEpisodeNewestFirst() {
        List<VodHistory.Entry> list = new ArrayList<>();
        list = VodHistory.put(list, entry("a", "The Matrix", false, 603, 1), 15);
        list = VodHistory.put(list, entry("b", "Slow Horses", true, 95480, 2), 15);
        list = VodHistory.put(list, entry("c", "Slow Horses", true, 95480, 3), 15);
        assertEquals(2, list.size());
        assertEquals("c", list.get(0).key);
        assertEquals("a", list.get(1).key);
        // Matched by name when there is no TMDB id.
        list = VodHistory.put(list, entry("d", "EN - Slow Horses", true, 0, 4), 15);
        assertEquals(2, list.size());
        assertEquals("d", list.get(0).key);
        // Two films of the same name stay apart.
        list = VodHistory.put(list, entry("e", "The Matrix", false, 0, 5), 15);
        assertEquals(3, list.size());
    }

    @Test public void theListKeepsItsNewestAndDropsTheWatched() {
        List<VodHistory.Entry> list = new ArrayList<>();
        for (int i = 0; i < 20; i++) list = VodHistory.put(list, entry("k" + i, "Film " + i, false, 0, i), 15);
        assertEquals(15, list.size());
        assertEquals("k19", list.get(0).key);
        assertEquals(14, VodHistory.without(list, "k19").size());
        assertTrue(VodHistory.finished(100 * 60_000L, 101 * 60_000L));
        assertFalse(VodHistory.finished(10 * 60_000L, 101 * 60_000L));
        assertFalse(VodHistory.finished(10 * 60_000L, 0));
    }

    @Test public void episodesGoThroughTheIntentAndBack() {
        Vod.Episode a = ep(1, 2);
        a.alternates.add(new Vod.Episode(1, 2, "Ep 2", "p1|x:series:9.mkv", "ua", "ref"));
        List<Vod.Episode> back = Vod.episodesFrom(Vod.episodesJson(Arrays.asList(ep(2, 1), a, ep(1, 1))));
        assertEquals(3, back.size());
        assertEquals(1, back.get(0).number);
        assertEquals(2, back.get(1).number);
        assertEquals(1, back.get(1).alternates.size());
        assertEquals("p1|x:series:9.mkv", back.get(1).plays().get(1)[0]);
        assertEquals(2, back.get(2).season);
        assertTrue(Vod.episodesFrom("").isEmpty());
        assertTrue(Vod.episodesFrom("not json").isEmpty());
    }

    private static SportGuide.Row row(String slug, boolean live, boolean mine, long when) {
        SportGuide.Row r = new SportGuide.Row();
        r.slug = slug;
        r.live = live;
        r.mine = mine;
        r.when = when;
        r.title = slug + when;
        return r;
    }

    @Test public void sportIsByCompetitionTheFamilysClubAndLiveFirst() {
        List<SportGuide.Row> rows = SportGuide.order(Arrays.asList(row("esp.1", false, false, 5), row("eng.1", false, false, 9),
                row("eng.1", true, false, 8), row("eng.1", false, true, 10), row("f1", false, false, 1), row("ita.1", true, false, 3)));
        // Premier League (the club's game, then the live one, then by kick-off), then Serie A (live), then LaLiga, F1 last.
        assertEquals("eng.110", rows.get(0).title);
        assertEquals("eng.18", rows.get(1).title);
        assertEquals("eng.19", rows.get(2).title);
        assertEquals("ita.13", rows.get(3).title);
        assertEquals("esp.15", rows.get(4).title);
        assertEquals("f11", rows.get(5).title);
        assertEquals(2, SportGuide.filtered(rows, SportGuide.LIVE).size());
    }

    @Test public void aChannelLineSaysWhereElse() {
        assertEquals("Sports 1 HD", SportGuide.channelLine("Sports 1 HD", false, 0));
        assertEquals("Sports 1 HD · also on 2 more", SportGuide.channelLine("Sports 1 HD", false, 2));
        assertEquals("Sports 1 HD · also in 4K and on 2 more", SportGuide.channelLine("Sports 1 HD", true, 2));
        assertEquals("Sports 1 HD · also in 4K", SportGuide.channelLine("Sports 1 HD", true, 0));
    }

    @Test public void onlyWhatIsStillToSeeToday() {
        long now = 1_000_000_000_000L;
        Game live = new Game();
        live.state = Game.LIVE;
        Game later = new Game();
        later.state = Game.PRE;
        later.kickoff = now + 3600_000L;
        Game tomorrow = new Game();
        tomorrow.state = Game.PRE;
        tomorrow.kickoff = now + 30 * 3600_000L;
        Game done = new Game();
        done.state = Game.POST;
        long end = now + 10 * 3600_000L;
        assertTrue(SportGuide.wanted(live, now, end));
        assertTrue(SportGuide.wanted(later, now, end));
        assertFalse(SportGuide.wanted(tomorrow, now, end));
        assertFalse(SportGuide.wanted(done, now, end));
    }
}
