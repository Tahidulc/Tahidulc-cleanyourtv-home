package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Home's Recently watched: one card per film, series, game and programme, all kinds together, newest first. */
public class WatchedTest {
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final long HOUR = 3600_000L;
    /** Friday 6 November 2026, 21:00 in London. */
    private static final long NOW = ZonedDateTime.of(2026, 11, 6, 21, 0, 0, 0, LONDON).toInstant().toEpochMilli();

    private static VodHistory.Entry vod(String key, String name, boolean tv, int s, int e, long at) {
        VodHistory.Entry v = new VodHistory.Entry();
        v.key = key;
        v.name = name;
        v.title = name;
        v.tv = tv;
        v.season = s;
        v.episode = e;
        v.at = at;
        v.position = 30 * 60_000L;
        v.duration = 95 * 60_000L;
        v.plays.add(new String[]{"x:movie:" + key + ".mkv", "", "", ""});
        return v;
    }

    private static Watched.Entry programme(String channel, String title, long start, long at) {
        Watched.Entry e = new Watched.Entry();
        e.channelKey = channel;
        e.channelName = channel;
        e.title = title;
        e.start = start;
        e.stop = start + HOUR;
        e.at = at;
        return e;
    }

    private static Game game(String id, int state, int home, int away, long kickoff) {
        Game g = new Game();
        g.id = id;
        g.slug = "eng.1";
        g.home = g.homeName = "Arsenal";
        g.away = g.awayName = "Chelsea";
        g.state = state;
        g.homeScore = home;
        g.awayScore = away;
        g.kickoff = kickoff;
        return g;
    }

    @Test public void filmsSeriesGamesAndProgrammesAreMixedNewestFirst() {
        List<Watched.Entry> list = new ArrayList<>();
        list = Watched.put(list, Watched.ofVod(vod("a", "The Matrix", false, 0, 0, NOW - 5 * HOUR), false), Watched.MAX);
        Watched.Entry match = programme("sky-sports", "Arsenal v Chelsea", NOW - 3 * HOUR, NOW - 2 * HOUR);
        match.kind = Watched.SPORT;
        match.game = game("401", Game.POST, 2, 1, NOW - 3 * HOUR);
        list = Watched.put(list, match, Watched.MAX);
        list = Watched.put(list, Watched.ofVod(vod("b", "Slow Horses", true, 2, 4, NOW - HOUR), false), Watched.MAX);
        list = Watched.put(list, programme("bbc-news", "BBC News at Six", NOW - 4 * HOUR, NOW - 30 * 60_000L), Watched.MAX);
        List<Watched.Entry> shown = Watched.mix(list, Collections.emptyList(), NOW);
        assertEquals(4, shown.size());
        assertEquals("BBC News at Six", shown.get(0).name());
        assertEquals("Slow Horses", shown.get(1).name());
        assertEquals("Arsenal v Chelsea", shown.get(2).name());
        assertEquals(Watched.SPORT, shown.get(2).kind);
        assertEquals("The Matrix", shown.get(3).name());
        assertEquals(Watched.FILM, shown.get(3).kind);
        assertEquals(Watched.EPISODE, shown.get(1).kind);
    }

    @Test public void aSeriesIsThereOnceAtItsLatestEpisode() {
        List<Watched.Entry> list = new ArrayList<>();
        list = Watched.put(list, Watched.ofVod(vod("e3", "Slow Horses", true, 2, 3, NOW - 2 * HOUR), true), Watched.MAX);
        list = Watched.put(list, Watched.ofVod(vod("e4", "Slow Horses", true, 2, 4, NOW - HOUR), false), Watched.MAX);
        assertEquals(1, list.size());
        assertEquals(4, list.get(0).vod.episode);
        assertFalse(list.get(0).finished);
    }

    @Test public void theSameProgrammeIsOneCardAndANewOneIsAnother() {
        long start = NOW - HOUR;
        List<Watched.Entry> list = new ArrayList<>();
        list = Watched.put(list, programme("itv1", "The Chase", start, NOW - 50 * 60_000L), Watched.MAX);
        list = Watched.put(list, programme("itv1", "The Chase", start, NOW - 10 * 60_000L), Watched.MAX);
        assertEquals(1, list.size());
        assertEquals(NOW - 10 * 60_000L, list.get(0).at);
        list = Watched.put(list, programme("itv1", "ITV News", NOW, NOW), Watched.MAX);
        assertEquals(2, list.size());
        // Another channel at the same time is another card.
        list = Watched.put(list, programme("bbc1", "Pointless", start, NOW), Watched.MAX);
        assertEquals(3, list.size());
    }

    @Test public void aChannelWithNoGuideIsOneCardForAFewHours() {
        List<Watched.Entry> list = new ArrayList<>();
        list = Watched.put(list, programme("local", "", 0, NOW - 2 * HOUR), Watched.MAX);
        list = Watched.put(list, programme("local", "", 0, NOW), Watched.MAX);
        assertEquals(1, list.size());
        list = Watched.put(list, programme("local", "", 0, NOW + 4 * HOUR), Watched.MAX);
        assertEquals(2, list.size());
        assertEquals("local", list.get(0).name());
    }

    @Test public void aGameIsOneCardOnWhicheverChannelAndKeepsWhatWasFound() {
        Watched.Entry first = programme("sky-main", "Arsenal v Chelsea", NOW - 2 * HOUR, NOW - HOUR);
        first.kind = Watched.SPORT;
        first.game = game("401", Game.LIVE, 1, 0, NOW - 2 * HOUR);
        List<Watched.Entry> list = Watched.put(new ArrayList<>(), first, Watched.MAX);
        // The same programme kept again before its game is looked up: the game found before stays with it.
        Watched.Entry again = programme("sky-main", "Arsenal v Chelsea", NOW - 2 * HOUR, NOW - 30 * 60_000L);
        list = Watched.put(list, again, Watched.MAX);
        assertEquals(1, list.size());
        assertEquals(Watched.SPORT, list.get(0).kind);
        assertEquals("401", list.get(0).game.id);
        // The same game on another channel: still one card.
        Watched.Entry other = programme("tnt-1", "Arsenal v Chelsea", NOW - 2 * HOUR - 15 * 60_000L, NOW);
        other.kind = Watched.SPORT;
        other.game = game("401", Game.LIVE, 2, 0, NOW - 2 * HOUR);
        list = Watched.put(list, other, Watched.MAX);
        assertEquals(1, list.size());
        assertEquals("tnt-1", list.get(0).channelKey);
    }

    @Test public void theListKeepsMaxAndDropsOldOrUnplayableOnes() {
        List<Watched.Entry> list = new ArrayList<>();
        for (int i = 0; i < Watched.MAX + 5; i++) list = Watched.put(list, programme("ch" + i, "P" + i, NOW - i * HOUR, NOW - i * 60_000L), Watched.MAX);
        assertEquals(Watched.MAX, list.size());
        assertEquals("P" + (Watched.MAX + 4), list.get(0).name());
        List<Watched.Entry> old = new ArrayList<>();
        old.add(programme("ch", "Long ago", NOW - Watched.MAX_AGE - HOUR, NOW - Watched.MAX_AGE - HOUR));
        Watched.Entry noPlays = Watched.ofVod(vod("z", "Gone", false, 0, 0, NOW), false);
        noPlays.vod.plays.clear();
        old.add(noPlays);
        assertTrue(Watched.mix(old, Collections.emptyList(), NOW).isEmpty());
    }

    @Test public void continueWatchingFromBeforeTheListIsInItOnce() {
        List<Watched.Entry> list = Watched.put(new ArrayList<>(), Watched.ofVod(vod("e4", "Slow Horses", true, 2, 4, NOW - HOUR), true), Watched.MAX);
        List<VodHistory.Entry> resume = Arrays.asList(vod("e3", "Slow Horses", true, 2, 3, NOW - 3 * HOUR), vod("m", "Dune", false, 0, 0, NOW - 2 * HOUR));
        List<Watched.Entry> shown = Watched.mix(list, resume, NOW);
        assertEquals(2, shown.size());
        assertEquals(4, shown.get(0).vod.episode);
        assertEquals("Dune", shown.get(1).name());
        assertFalse(shown.get(1).finished);
    }

    @Test public void theCardsSayWhenTheyWereWatched() {
        assertEquals("Today 20:15", Watched.when(NOW - 45 * 60_000L, NOW, LONDON));
        assertEquals("Yesterday 21:40", Watched.when(NOW - 23 * HOUR - 20 * 60_000L, NOW, LONDON));
        assertEquals("Mon 19:30", Watched.when(ZonedDateTime.of(2026, 11, 2, 19, 30, 0, 0, LONDON).toInstant().toEpochMilli(), NOW, LONDON));
        assertEquals("30 Oct", Watched.when(ZonedDateTime.of(2026, 10, 30, 19, 30, 0, 0, LONDON).toInstant().toEpochMilli(), NOW, LONDON));
    }

    @Test public void filmsAndEpisodesSayHowFarInOrWhatIsNext() {
        Watched.Entry film = Watched.ofVod(vod("m", "Dune", false, 0, 0, NOW), false);
        assertEquals("1h 05m left", Watched.vodLine(film));
        film.vod.position = 80 * 60_000L;
        assertEquals("15m left", Watched.vodLine(film));
        film.finished = true;
        assertEquals("Watched", Watched.vodLine(film));
        Watched.Entry ep = Watched.ofVod(vod("e", "Slow Horses", true, 2, 4, NOW), false);
        assertEquals("S2 E4 · 1h 05m left", Watched.vodLine(ep));
        ep.finished = true;
        assertEquals("S2 E4 · Watched", Watched.vodLine(ep));
        assertNull(Watched.next(ep));
        ep.vod.episodes = Vod.episodesJson(Arrays.asList(
                new Vod.Episode(2, 4, "Four", "x:series:24.mkv", "", ""),
                new Vod.Episode(2, 5, "Five", "x:series:25.mkv", "", ""),
                new Vod.Episode(3, 1, "One", "x:series:31.mkv", "", "")));
        assertEquals("Next: S2 E5", Watched.vodLine(ep));
        assertEquals(5, Watched.next(ep).number);
        ep.vod.episode = 5;
        assertEquals("Next: S3 E1", Watched.vodLine(ep));
        ep.vod.episode = 1;
        ep.vod.season = 3;
        assertEquals("S3 E1 · Watched", Watched.vodLine(ep));
        // Part-way, the next one isn't offered.
        ep.finished = false;
        assertNull(Watched.next(ep));
    }

    @Test public void aGameLastSeenLiveLongAgoIsNotOnNow() {
        assertTrue(Watched.onNow(game("1", Game.LIVE, 0, 0, NOW - HOUR), NOW));
        assertFalse(Watched.onNow(game("1", Game.LIVE, 0, 0, NOW - 4 * HOUR), NOW));
        assertFalse(Watched.onNow(game("1", Game.POST, 0, 0, NOW - HOUR), NOW));
        assertFalse(Watched.onNow(null, NOW));
        Game moved = game("1", Game.LIVE, 1, 0, NOW - HOUR);
        assertFalse(Watched.changed(moved, game("1", Game.LIVE, 1, 0, NOW - HOUR)));
        assertTrue(Watched.changed(moved, game("1", Game.LIVE, 1, 1, NOW - HOUR)));
        assertTrue(Watched.changed(moved, game("1", Game.POST, 1, 0, NOW - HOUR)));
    }

    @Test public void programmesAreTaggedByWhatTheyAre() {
        assertEquals("Sport", Watched.tag(TileKind.Kind.OTHER, "Sky Sports Main Event", "UK Sports"));
        assertEquals("Sport", Watched.tag(TileKind.Kind.MATCH, "BBC One", "UK"));
        assertEquals("Film", Watched.tag(TileKind.Kind.FILM, "Sky Cinema Premiere", "Movies"));
        assertEquals("News", Watched.tag(TileKind.Kind.OTHER, "BBC News", "UK"));
        assertEquals("TV", Watched.tag(TileKind.Kind.SERIES, "ITV1", "UK"));
    }

    @Test public void anEntryComesBackAsItWasKept() throws Exception {
        Watched.Entry e = programme("sky-main", "Arsenal v Chelsea", NOW - 2 * HOUR, NOW);
        e.kind = Watched.SPORT;
        e.tag = "Sport";
        e.channelLogo = "https://example.org/logo.png";
        e.game = game("401", Game.POST, 2, 1, NOW - 2 * HOUR);
        Watched.Entry back = Watched.Entry.from(e.json());
        assertEquals(Watched.SPORT, back.kind);
        assertEquals("401", back.game.id);
        assertEquals(2, back.game.homeScore);
        assertEquals("Sport", back.tag);
        assertEquals(e.start, back.start);
        Watched.Entry f = Watched.ofVod(vod("m", "Dune", false, 0, 0, NOW), true);
        Watched.Entry fb = Watched.Entry.from(f.json());
        assertEquals(Watched.FILM, fb.kind);
        assertTrue(fb.finished);
        assertEquals("Dune", fb.name());
        assertEquals(1, fb.vod.plays.size());
        assertSame(null, fb.game);
    }
}
