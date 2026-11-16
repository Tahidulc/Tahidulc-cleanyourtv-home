package com.gathin.homefieldtv.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gathin.homefieldtv.engine.Channel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** In step with the channel (the delay, what the picture has reached), finding a channel's game, and the hub's order. */
public class InStepTest {
    @Test public void delaysKeptWithinThreeMinutes() {
        assertEquals(0, InStep.clamp(-5));
        assertEquals(45, InStep.clamp(45));
        assertEquals(InStep.MAX_DELAY_S, InStep.clamp(400));
    }

    @Test public void itsJustHappenedGoesByTheLatestNews() {
        long now = 10_000_000L;
        assertEquals(-1, InStep.calibrated(0, now));
        assertEquals(42, InStep.calibrated(now - 42_000, now));
        assertEquals(InStep.MAX_DELAY_S, InStep.calibrated(now - 200_000, now));
        // Too long ago to go by, or not yet.
        assertEquals(-1, InStep.calibrated(now - 6 * 60_000L, now));
        assertEquals(-1, InStep.calibrated(now + 1_000, now));
    }

    @Test public void theStateThePictureHasReached() {
        InStep.Line<String> line = new InStep.Line<>();
        line.add(1_000, "a", "0-0");
        line.add(21_000, "b", "1-0");
        line.add(41_000, "c", "1-0");
        assertEquals("c", line.at(45_000, 0));
        assertEquals("b", line.at(45_000, 10));
        assertEquals("a", line.at(45_000, 30));
        // The channel not yet there: the oldest kept.
        assertEquals("a", line.at(500, 0));
        assertEquals(21_000, line.lastChange());
        assertEquals("c", line.latest());
        assertEquals(3, line.size());
    }

    @Test public void theChannelWatchedLately() {
        InStep.watching("sky-main-event", 1_000);
        assertEquals("sky-main-event", InStep.watched(61_000));
        assertEquals("", InStep.watched(1_000 + 21 * 60_000L));
        assertEquals("Live, no delay", InStep.words(0));
        assertEquals("In step · 45 s behind live", InStep.words(45));
    }

    private static Game game(String id, String home, String away, int state, long kickoff) {
        Game g = new Game();
        g.id = id;
        g.homeName = home;
        g.awayName = away;
        g.homeFull = home;
        g.awayFull = away;
        g.state = state;
        g.kickoff = kickoff;
        return g;
    }

    private static Channel channel(String name) {
        return new Channel("k:" + name, name, "Sport", "", "http://example.invalid/" + name.length(), "", "", "", 1);
    }

    @Test public void aChannelsGameByItsName() {
        Game ars = game("1", "Arsenal", "Chelsea", Game.LIVE, 0), liv = game("2", "Liverpool", "Everton", Game.LIVE, 0);
        List<Game> games = Arrays.asList(liv, ars);
        assertSame(ars, Matching.gameOn(null, channel("EPL 03: Arsenal v Chelsea"), 0, games));
        // Nothing in its name, and no guide to go by.
        assertNull(Matching.gameOn(null, channel("Sky Sports Main Event"), 0, games));
        assertTrue(Matching.names(Matching.words("EPL 03: Arsenal v Chelsea"),
                new String[][]{Matching.clubNames("Arsenal", "Arsenal"), Matching.clubNames("Chelsea", "Chelsea")}));
        assertFalse(Matching.names(Matching.words("EPL 03: Arsenal v Chelsea"),
                new String[][]{Matching.clubNames("Arsenal", "Arsenal"), Matching.clubNames("Liverpool", "Liverpool")}));
        List<String> united = Arrays.asList(Matching.clubNames("Man United", "Manchester United"));
        assertTrue(united.contains("man utd"));
        assertTrue(united.contains("manchester united"));
        assertTrue(Arrays.asList(Matching.clubNames("Tottenham", "Tottenham Hotspur")).contains("tottenham"));
    }

    @Test public void englishFirst() {
        assertEquals(2, Matching.english("UK: Sky Sports Main Event"));
        assertEquals(2, Matching.english("Sky Sports Main Event"));
        assertEquals(0, Matching.english("AR: beIN Sports 1"));
        assertEquals(1, Matching.english("Random Channel"));
        assertEquals("Sky Sports Main Event", Matching.tidy("Sky Sports Main Event"));
    }

    private static SportsData.Item item(String kind, boolean live, boolean over, long start) {
        SportsData.Item it = new SportsData.Item();
        it.kind = kind;
        it.live = live;
        it.over = over;
        it.start = start;
        return it;
    }

    @Test public void theHubLiveFirstThenSoonestThenLatestFinished() {
        long now = 100 * 3_600_000L;
        SportsData.Item nba = item(SportsData.NBA, true, false, now - 3_600_000L);
        SportsData.Item football = item(SportsData.FOOTBALL, true, false, now - 1_800_000L);
        SportsData.Item later = item(SportsData.FOOTBALL, false, false, now + 7_200_000L);
        SportsData.Item soon = item(SportsData.TENNIS, false, false, now + 600_000L);
        SportsData.Item done = item(SportsData.FOOTBALL, false, true, now - 3 * 3_600_000L);
        SportsData.Item doneLater = item(SportsData.FIGHT, false, true, now - 2 * 3_600_000L);
        SportsData.Item old = item(SportsData.FOOTBALL, false, true, now - 8 * 3_600_000L);
        List<SportsData.Item> in = new ArrayList<>(Arrays.asList(old, done, later, nba, soon, football, doneLater));
        List<SportsData.Item> out = SportsData.order(in, now);
        assertEquals(Arrays.asList(football, nba, soon, later, doneLater, done), out);
    }
}
