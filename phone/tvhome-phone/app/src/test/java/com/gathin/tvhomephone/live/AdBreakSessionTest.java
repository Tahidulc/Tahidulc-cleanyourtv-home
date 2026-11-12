package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * The ad-break screen's session on the channel playing: from the listings (the playlist's own guide, as TiviMate shows
 * it), else a game found another way, else the broadcaster's scoreboard seen; whether the ball is in play; and what the
 * line over the cards says.
 */
public class AdBreakSessionTest {
    private static final long NOW = 1_762_278_300_000L;   // 4 Nov 2025 17:45 UTC
    private static final long MIN = 60_000L;

    private static Game game(String id, String home, String away, int state, long kickoff) {
        Game g = new Game();
        g.id = id;
        g.homeName = home;
        g.homeFull = home;
        g.home = home.substring(0, 3).toUpperCase(java.util.Locale.ROOT);
        g.awayName = away;
        g.awayFull = away;
        g.away = away.substring(0, 3).toUpperCase(java.util.Locale.ROOT);
        g.state = state;
        g.kickoff = kickoff;
        return g;
    }

    private static Game arsenal, palace;

    /** Arsenal v Chelsea at 19:00 (not yet), Crystal Palace v Everton on since 16:55. */
    private static List<Game> today() {
        arsenal = game("1", "Arsenal", "Chelsea", Game.PRE, NOW + 75 * MIN);
        palace = game("2", "Crystal Palace", "Everton", Game.LIVE, NOW - 50 * MIN);
        return Arrays.asList(arsenal, palace);
    }

    // ---- the listings ------------------------------------------------------------------------------------------------

    /** The channel's guide as read from the playlist's XMLTV (its times as XMLTV writes them). */
    private static Guide.Schedule epg(String[][] rows) {
        Guide.Builder b = new Guide.Builder();
        for (String[] r : rows) b.add(Guide.time(r[0]), Guide.time(r[1]), r[2], r[3]);
        return b.build();
    }

    @Test
    public void theListingsAreTheProgrammeOnNowAndTheNextWithinAQuarterOfAnHour() {
        // 4 Nov 2025: 17:00–18:00 build-up, 18:00–20:30 the match, 20:30 the highlights (XMLTV times, one in BST's form).
        Guide.Schedule s = epg(new String[][]{
                {"20251104170000 +0000", "20251104180000 +0000", "Football Build-up", ""},
                {"20251104190000 +0100", "20251104203000 +0000", "Live: Arsenal v Chelsea", "Premier League football."},
                {"20251104203000 +0000", "20251104213000 +0000", "Highlights", ""}});
        List<OverMatches.Programme> g = OverMatches.guide(s, NOW, AdBreak.NEXT_WITHIN_MS);
        assertEquals(2, g.size());
        assertEquals("Football Build-up", g.get(0).title);
        assertEquals("Live: Arsenal v Chelsea", g.get(1).title);
        assertEquals(Guide.time("20251104180000 +0000"), g.get(1).start);
        // Twenty minutes before the match: only the build-up.
        assertEquals(1, OverMatches.guide(s, NOW - 5 * MIN, AdBreak.NEXT_WITHIN_MS).size());
    }

    @Test
    public void aProgrammeNamingTheGameIsTheSessionFromItsStartToItsEndAndFiveMinutes() {
        List<Game> games = today();
        long start = NOW - 30 * MIN, stop = NOW + 3 * 60 * MIN;
        List<OverMatches.Programme> guide = Collections.singletonList(
                new OverMatches.Programme("Live PL: Arsenal v Chelsea", "", start, stop));
        AdBreak.Session s = AdBreak.fromListings(guide, games, NOW);
        assertSame(arsenal, s.game);
        assertEquals(AdBreak.LISTINGS, s.source);
        assertEquals("Live PL: Arsenal v Chelsea", s.title);
        assertEquals(start, s.from);
        assertEquals(stop + AdBreak.AFTER_PROGRAMME_MS, s.to);
        // Not an hour before kick-off only: the whole programme (75 minutes before kick-off here).
        assertTrue(AdBreak.eligible(s, NOW));
    }

    @Test
    public void theNextProgrammeCountsAQuarterOfAnHourBeforeItStarts() {
        List<Game> games = today();
        long start = NOW + 10 * MIN, stop = NOW + 3 * 60 * MIN;
        List<OverMatches.Programme> guide = Arrays.asList(
                new OverMatches.Programme("Sky Sports News", "", NOW - 60 * MIN, start),
                new OverMatches.Programme("Arsenal v Chelsea", "", start, stop));
        AdBreak.Session s = AdBreak.fromListings(guide, games, NOW);
        assertSame(arsenal, s.game);
        assertEquals(start - AdBreak.NEXT_WITHIN_MS, s.from);
        assertTrue(s.in(NOW));
    }

    @Test
    public void oneClubInAFixtureTitleIsEnough() {
        // The listings call Chelsea something ESPN doesn't ("The Blues"): Arsenal alone, in a title that reads as a fixture.
        List<Game> games = today();
        List<OverMatches.Programme> guide = Collections.singletonList(
                new OverMatches.Programme("Live: Arsenal v The Blues", "", NOW - 30 * MIN, NOW + 3 * 60 * MIN));
        assertSame(arsenal, AdBreak.fromListings(guide, games, NOW).game);
    }

    @Test
    public void listingsThatNameNoGameGiveNoSession() {
        List<Game> games = today();
        assertNull(AdBreak.fromListings(Collections.singletonList(
                new OverMatches.Programme("Taskmaster", "", NOW - 30 * MIN, NOW + 30 * MIN)), games, NOW));
        // A programme with no times, one long over, one too far off.
        assertNull(AdBreak.fromListings(Collections.singletonList(new OverMatches.Programme("Arsenal v Chelsea", "", 0, 0)), games, NOW));
        assertNull(AdBreak.fromListings(Collections.singletonList(
                new OverMatches.Programme("Arsenal v Chelsea", "", NOW - 4 * 60 * MIN, NOW - 10 * MIN)), games, NOW));
        assertNull(AdBreak.fromListings(Collections.singletonList(
                new OverMatches.Programme("Arsenal v Chelsea", "", NOW + 20 * MIN, NOW + 3 * 60 * MIN)), games, NOW));
        assertNull(AdBreak.fromListings(Collections.emptyList(), games, NOW));
        assertNull(AdBreak.fromListings(null, games, NOW));
        assertNull(AdBreak.fromListings(Collections.singletonList(
                new OverMatches.Programme("Arsenal v Chelsea", "", NOW - 30 * MIN, NOW + 30 * MIN)), Collections.emptyList(), NOW));
    }

    // ---- a game found without listings ---------------------------------------------------------------------------------

    @Test
    public void aGameFoundIsTheHourBeforeKickOffToHalfAnHourAfterFullTime() {
        Game g = game("1", "Arsenal", "Chelsea", Game.PRE, NOW + 30 * MIN);
        AdBreak.Session s = AdBreak.fromGame(g, "Super Sunday", NOW);
        assertEquals(AdBreak.FOUND, s.source);
        assertEquals(g.kickoff - 60 * MIN, s.from);
        assertEquals(g.kickoff + 145 * MIN, s.to);
        assertEquals("Super Sunday", s.title);
        // Still on (extra time, a late finish): half an hour after now at least.
        g.state = Game.LIVE;
        g.kickoff = NOW - 150 * MIN;
        assertEquals(NOW + 30 * MIN, AdBreak.fromGame(g, null, NOW).to);
        assertNull(AdBreak.fromGame(null, null, NOW));
        assertNull(AdBreak.fromGame(game("3", "Leeds", "Burnley", Game.PRE, 0), null, NOW));
    }

    @Test
    public void theCandidatesAreTodaysGamesWhoseSessionIsOn() {
        List<Game> games = today();
        Game late = game("3", "Leeds", "Burnley", Game.PRE, NOW + 5 * 60 * MIN);
        Game done = game("4", "Fulham", "Brentford", Game.POST, NOW - 4 * 60 * MIN);
        List<Game> all = Arrays.asList(arsenal, palace, late, done, null);
        List<Game> on = AdBreak.candidates(all, NOW);
        assertEquals(Collections.singletonList(palace), on);   // Arsenal is 75 minutes off: not yet
        assertEquals(Arrays.asList(arsenal, palace), AdBreak.candidates(games, NOW + 20 * MIN));
        assertTrue(AdBreak.candidates(null, NOW).isEmpty());
    }

    @Test
    public void aScoreboardSeenWithNoGameHoldsTwentyMinutes() {
        AdBreak.Session s = AdBreak.general("Live Football", NOW);
        assertNull(s.game);
        assertEquals(AdBreak.GENERAL, s.source);
        assertTrue(s.in(NOW + 19 * MIN));
        assertFalse(s.in(NOW + 21 * MIN));
        assertTrue(AdBreak.eligible(s, NOW));
        assertNull(AdBreak.general("Live Football", 0));
        assertEquals("", AdBreak.general(null, NOW).title);
    }

    @Test
    public void theSameSessionIsTheSameGame() {
        today();
        AdBreak.Session a = AdBreak.fromGame(arsenal, null, NOW), b = AdBreak.fromGame(arsenal, "x", NOW + MIN);
        assertTrue(a.same(b));
        assertFalse(a.same(AdBreak.fromGame(palace, null, NOW)));
        assertFalse(a.same(AdBreak.general(null, NOW)));
        assertTrue(AdBreak.general(null, NOW).same(AdBreak.general("y", NOW + MIN)));
        assertFalse(a.same(null));
    }

    // ---- in play -------------------------------------------------------------------------------------------------------

    @Test
    public void theBallIsInPlayExceptAtHalfTimeAndLateInEachHalf() {
        Game g = game("1", "Arsenal", "Chelsea", Game.LIVE, NOW);
        g.period = 1;
        g.clock = 12 * 60;
        assertTrue(AdBreak.inPlay(g));
        g.clock = 40 * 60;
        assertFalse(AdBreak.inPlay(g));   // ESPN's half time comes late
        g.clock = 30 * 60;
        g.halftime = true;
        assertFalse(AdBreak.inPlay(g));
        g.halftime = false;
        g.period = 2;
        g.clock = 70 * 60;
        assertTrue(AdBreak.inPlay(g));
        g.clock = 86 * 60;
        assertFalse(AdBreak.inPlay(g));
        g.period = 3;
        g.clock = 10 * 60;
        assertTrue(AdBreak.inPlay(g));
        g.state = Game.PRE;
        assertFalse(AdBreak.inPlay(g));
        g.state = Game.POST;
        assertFalse(AdBreak.inPlay(g));
        assertFalse(AdBreak.inPlay(null));
    }

    // ---- the line over the cards ---------------------------------------------------------------------------------------

    @Test
    public void theLineSaysWhereTheMatchIs() {
        Game g = game("1", "Arsenal", "Chelsea", Game.PRE, NOW + 30 * MIN);
        assertEquals(AdBreak.BEFORE, AdBreak.phase(g));
        assertEquals("BEFORE KICK-OFF", AdBreak.tag(AdBreak.BEFORE));
        assertTrue(AdBreak.lineupsNow(AdBreak.phase(g)));
        assertEquals("", AdBreak.clockLabel(g));
        g.state = Game.LIVE;
        g.halftime = true;
        assertEquals(AdBreak.HALF_TIME, AdBreak.phase(g));
        assertEquals("HALF-TIME", AdBreak.tag(AdBreak.HALF_TIME));
        assertTrue(AdBreak.lineupsNow(AdBreak.HALF_TIME));
        assertEquals("HT", AdBreak.clockLabel(g));
        g.halftime = false;
        g.displayClock = "88'";
        assertEquals(AdBreak.LIVE_BREAK, AdBreak.phase(g));
        assertEquals("AD BREAK", AdBreak.tag(AdBreak.LIVE_BREAK));
        assertFalse(AdBreak.lineupsNow(AdBreak.LIVE_BREAK));
        assertEquals("88'", AdBreak.clockLabel(g));
        g.displayClock = "";
        g.clock = 41 * 60;
        assertEquals("41'", AdBreak.clockLabel(g));
        g.state = Game.POST;
        assertEquals(AdBreak.FULL_TIME, AdBreak.phase(g));
        assertEquals("FULL TIME", AdBreak.tag(AdBreak.FULL_TIME));
        assertFalse(AdBreak.lineupsNow(AdBreak.FULL_TIME));
        assertEquals("FT", AdBreak.clockLabel(g));
        assertEquals(AdBreak.NO_GAME, AdBreak.phase(null));
        assertEquals("AD BREAK", AdBreak.tag(AdBreak.NO_GAME));
        assertFalse(AdBreak.lineupsNow(AdBreak.NO_GAME));
    }

    @Test
    public void theFollowedClubsGamesComeFirst() {
        today();
        Game leeds = game("3", "Leeds", "Burnley", Game.LIVE, NOW);
        leeds.homeId = "357";
        palace.awayId = "368";
        List<Game> out = AdBreak.mineFirst(Arrays.asList(arsenal, palace, leeds, null), new HashSet<>(Arrays.asList("368", "357")), 2);
        assertEquals(Arrays.asList(palace, leeds), out);
        assertEquals(Arrays.asList(arsenal, palace, leeds), AdBreak.mineFirst(Arrays.asList(arsenal, palace, leeds), null, 6));
        assertTrue(AdBreak.mineFirst(null, null, 3).isEmpty());
    }

    // ---- who takes a break: the ad-break screen, at every break of its session ------------------------------------------

    private static final int NONE = AdBreak.TAKE_NONE, AD = AdBreak.TAKE_AD_BREAK;

    @Test
    public void theAdBreakScreenTakesEveryBreakHalfTimeAndFullTimeToo() {
        Game g = game("1", "Arsenal", "Chelsea", Game.LIVE, NOW - 50 * MIN);
        AdBreak.Session s = AdBreak.fromGame(g, null, NOW);
        // Half time and full time: the ad-break screen's (no family break any more).
        g.halftime = true;
        assertEquals(AD, AdBreak.take(true, s, NOW));
        g.halftime = false;
        g.state = Game.POST;
        assertEquals(AD, AdBreak.take(true, s, NOW));
        // The build-up.
        g.state = Game.PRE;
        g.kickoff = NOW + 30 * MIN;
        s = AdBreak.fromGame(g, null, NOW);
        assertEquals(AD, AdBreak.take(true, s, NOW));
        assertEquals(NONE, AdBreak.take(true, null, NOW));
        assertEquals(NONE, AdBreak.take(false, s, NOW));   // switched off
        // A break while ESPN still has the game live (late in a half): the screen's.
        g.state = Game.LIVE;
        g.kickoff = NOW - 44 * MIN;
        g.period = 1;
        g.clock = 44 * 60;
        s = AdBreak.fromGame(g, null, NOW);
        assertEquals(AD, AdBreak.take(true, s, NOW));
        // The ball in play: nothing.
        g.clock = 20 * 60;
        assertEquals(NONE, AdBreak.take(true, s, NOW));
        // Half an hour after full time the session is over: nothing; a general session's break is the screen's.
        assertEquals(NONE, AdBreak.take(true, s, s.to + 1));
        assertEquals(AD, AdBreak.take(true, AdBreak.general("Live Football", NOW), NOW));
    }

    @Test
    public void halfTimeIsEspnsOrTheFirstHalfsLastMinutes() {
        Game g = game("1", "Arsenal", "Chelsea", Game.LIVE, NOW - 50 * MIN);
        g.period = 1;
        g.clock = 45 * 60;
        assertTrue(AdBreak.atHalfTime(g));   // ESPN not caught up yet
        g.clock = 20 * 60;
        assertFalse(AdBreak.atHalfTime(g));
        g.halftime = true;
        assertTrue(AdBreak.atHalfTime(g));
        g.halftime = false;
        g.period = 2;
        g.clock = 88 * 60;
        assertFalse(AdBreak.atHalfTime(g));   // that is full time coming
        g.state = Game.POST;
        assertFalse(AdBreak.atHalfTime(g));
        assertFalse(AdBreak.atHalfTime(null));
    }

    @Test
    public void theFirstHalfInFiguresAndItsScorers() {
        List<String[]> stats = Arrays.asList(new String[]{"Fouls", "6", "9"}, new String[]{"On target", "4", "1"},
                new String[]{"Possession", "61.4", "38.6"}, new String[]{"Shots", "9", "3"});
        List<String[]> f = AdBreak.firstHalf(stats);
        assertEquals(3, f.size());
        assertEquals("Possession", f.get(0)[0]);
        assertEquals("61%", f.get(0)[1]);
        assertEquals("39%", f.get(0)[2]);
        assertEquals("Shots", f.get(1)[0]);
        assertEquals("On target", f.get(2)[0]);
        assertEquals("4", f.get(2)[1]);
        assertTrue(AdBreak.firstHalf(null).isEmpty());
        assertTrue(AdBreak.firstHalf(Collections.singletonList(new String[]{"Fouls", "1", "2"})).isEmpty());

        Game g = game("1", "Arsenal", "Chelsea", Game.LIVE, NOW - 50 * MIN);
        g.goals.add(new String[]{"G|1|23'|Saka", "Bukayo Saka", "23'", "home", ""});
        g.goals.add(new String[]{"G|1|30'|Palmer", "Cole Palmer", "30'", "away", "pen"});
        g.goals.add(new String[]{"G|1|41'|Havertz", "Kai Havertz", "41'", "home", "og"});
        assertEquals("Bukayo Saka 23', Kai Havertz 41' (og)", AdBreak.scorers(g, "home"));
        assertEquals("Cole Palmer 30' (pen)", AdBreak.scorers(g, "away"));
        assertEquals("", AdBreak.scorers(game("2", "Leeds", "Burnley", Game.LIVE, NOW), "home"));
        assertEquals("", AdBreak.scorers(null, "home"));
    }

    @Test
    public void halfTimesAndFullTimesPopOutsWaitForTheAdBreakScreen() {
        assertTrue(ScoreBoard.waitsForAdBreak("ht"));
        assertTrue(ScoreBoard.waitsForAdBreak("ft"));
        assertFalse(ScoreBoard.waitsForAdBreak("goal"));
        assertFalse(ScoreBoard.waitsForAdBreak("h1"));
        assertFalse(ScoreBoard.waitsForAdBreak(null));
    }
}
