package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** OverMatches: "Scoreboard over matches" (Automatic, Ask, Off) and the match the player sees on the channel. */
public class OverMatchesTest {
    private static final String A = OverMatches.AUTO, ASK = OverMatches.ASK, OFF = OverMatches.OFF;
    private static final long NOW = 1_762_278_300_000L;   // 4 Nov 2026 17:45 UTC
    private static final List<String> BRITISH = Arrays.asList("Sky Sports Main Event", "TNT Sports 1", "TNT Sports 2", "BBC One", "5");

    // ---- the setting -----------------------------------------------------------------------------------------------------

    @Test
    public void untilChosenItIsAskOrOffAsTheBoardWas() {
        // Nobody who never touches it gets anything new: the scoreboard on the picture on (its default) is Ask, off is Off.
        assertEquals(ASK, OverMatches.modeOf("", true));
        assertEquals(ASK, OverMatches.modeOf(null, true));
        assertEquals(OFF, OverMatches.modeOf("", false));
        assertEquals(A, OverMatches.modeOf(A, true));
        assertEquals(ASK, OverMatches.modeOf(ASK, true));
        // Switched off in the player's options: Off, whatever was chosen; back on, as chosen.
        assertEquals(OFF, OverMatches.modeOf(A, false));
    }

    // ---- the decision: mode × recognised × shown already -------------------------------------------------------------------

    @Test
    public void automaticPutsTheMatchFoundOnTheBoard() {
        assertEquals(OverMatches.SHOW, OverMatches.decide(A, true, false));
        // The tile's game (or this one) on the board already, or nothing found: as it was.
        assertEquals(OverMatches.KEEP, OverMatches.decide(A, true, true));
        assertEquals(OverMatches.KEEP, OverMatches.decide(A, false, false));
    }

    @Test
    public void askIsThePlayerAsItWasAndOffIsNoBoard() {
        for (boolean recognised : new boolean[]{true, false}) {
            for (boolean shown : new boolean[]{true, false}) {
                assertEquals(OverMatches.KEEP, OverMatches.decide(ASK, recognised, shown));
                assertEquals(OverMatches.NONE, OverMatches.decide(OFF, recognised, shown));
            }
        }
    }

    // ---- which games count ------------------------------------------------------------------------------------------------

    @Test
    public void aGameCountsLiveOrFifteenMinutesBeforeOrLate() {
        assertTrue(OverMatches.onOrSoon(game("1", "Arsenal", "Chelsea", Game.LIVE, NOW - 3_600_000L), NOW));
        assertTrue(OverMatches.onOrSoon(game("1", "Arsenal", "Chelsea", Game.PRE, NOW + 15 * 60_000L), NOW));
        assertFalse(OverMatches.onOrSoon(game("1", "Arsenal", "Chelsea", Game.PRE, NOW + 16 * 60_000L), NOW));
        assertTrue(OverMatches.onOrSoon(game("1", "Arsenal", "Chelsea", Game.PRE, NOW - 5 * 60_000L), NOW));
        assertFalse(OverMatches.onOrSoon(game("1", "Arsenal", "Chelsea", Game.PRE, NOW - 31 * 60_000L), NOW));
        assertFalse(OverMatches.onOrSoon(game("1", "Arsenal", "Chelsea", Game.POST, NOW - 2 * 3_600_000L), NOW));
    }

    @Test
    public void theGuideIsTheProgrammeOnNowAndTheNextSoon() {
        Guide.Schedule s = new Guide.Schedule(new long[]{NOW - 3_600_000L, NOW + 10 * 60_000L, NOW + 3 * 3_600_000L},
                new long[]{NOW + 10 * 60_000L, NOW + 3 * 3_600_000L, NOW + 4 * 3_600_000L},
                new String[]{"Super Sunday: Build-up", "Live PL: Arsenal v Chelsea", "Highlights"}, new String[]{"", "", ""});
        List<OverMatches.Programme> g = OverMatches.guide(s, NOW);
        assertEquals(2, g.size());
        assertEquals("Live PL: Arsenal v Chelsea", g.get(1).title);
        // The next one an hour off: only the one on now.
        assertEquals(1, OverMatches.guide(s, NOW - 3_000_000L).size());
        assertTrue(OverMatches.guide(null, NOW).isEmpty());
    }

    @Test
    public void onlyChannelsThatCouldShowAMatchAreLookedAt() {
        List<OverMatches.Programme> none = Collections.emptyList();
        assertTrue(OverMatches.worthLooking("UK: Sky Sports Main Event FHD", "Sports", none, BRITISH));
        assertTrue(OverMatches.worthLooking("BBC One HD", "UK Entertainment", none, BRITISH));
        assertTrue(OverMatches.worthLooking("Dave", "UK", Collections.singletonList(new OverMatches.Programme("Arsenal v Chelsea", "", 0, 0)), BRITISH));
        assertFalse(OverMatches.worthLooking("Dave", "UK", Collections.singletonList(new OverMatches.Programme("Taskmaster", "", 0, 0)), BRITISH));
    }

    // ---- the match on the channel -------------------------------------------------------------------------------------------

    private static Game arsenal, palace;
    private static Map<String, List<String>> own, usual;

    /** A Sunday: two Premier League games, Sky's guide has one on Sky Sports Main Event; both "usually" on Sky and TNT. */
    private static List<Game> sunday() {
        arsenal = game("1", "Arsenal", "Chelsea", Game.PRE, NOW + 10 * 60_000L);
        palace = game("2", "Crystal Palace", "Everton", Game.LIVE, NOW - 50 * 60_000L);
        own = new HashMap<>();
        usual = new HashMap<>();
        own.put("1", Collections.singletonList("Sky Sports Main Event"));
        own.put("2", Collections.emptyList());
        usual.put("1", Arrays.asList("Sky Sports Premier League", "TNT Sports 1"));
        usual.put("2", Arrays.asList("Sky Sports Premier League", "TNT Sports 1"));
        return new ArrayList<>(Arrays.asList(arsenal, palace));
    }

    @Test
    public void theGuideNamingTheClubsComesFirstEvenBeforeKickOff() {
        List<Game> games = sunday();
        List<OverMatches.Programme> guide = Collections.singletonList(
                new OverMatches.Programme("Live PL: Arsenal v Chelsea", "", NOW - 50 * 60_000L, NOW + 2 * 3_600_000L));
        assertSame(arsenal, OverMatches.recognise("Some Sports 4", guide, games, null, null, NOW));
    }

    @Test
    public void anEventChannelNamedForTheGame() {
        List<Game> games = sunday();
        assertSame(palace, OverMatches.recognise("EPL 03: Crystal Palace v Everton 16:30", Collections.emptyList(), games, null, null, NOW));
    }

    @Test
    public void theGamesOwnChannelWhenTheGuideSaysNothing() {
        List<Game> games = sunday();
        // The guide says only "Super Sunday": Sky's guide has Arsenal v Chelsea on Sky Sports Main Event at kick-off.
        List<OverMatches.Programme> guide = Collections.singletonList(new OverMatches.Programme("Super Sunday", "", NOW - 3_600_000L, NOW + 3 * 3_600_000L));
        assertSame(arsenal, OverMatches.recognise("UK: SKY SPORTS MAIN EVENT FHD", guide, games, own, usual, NOW));
    }

    @Test
    public void aUsualBroadcasterOnlyForTheOneGameOnIt() {
        List<Game> games = sunday();
        // Both games are "usually" on TNT Sports 1: no telling.
        assertNull(OverMatches.recognise("TNT Sports 1 HD", Collections.emptyList(), games, own, usual, NOW));
        // One game on now, its own channel not known: that one.
        games.remove(arsenal);
        assertSame(palace, OverMatches.recognise("TNT Sports 1 HD", Collections.emptyList(), games, own, usual, NOW));
        // Its own channel known to be another: not this one.
        own.put("2", Collections.singletonList("Sky Sports Main Event"));
        assertNull(OverMatches.recognise("TNT Sports 1 HD", Collections.emptyList(), games, own, usual, NOW));
    }

    @Test
    public void nothingTooEarlyFinishedOrElsewhere() {
        List<Game> games = sunday();
        // An hour before kick-off: not yet.
        arsenal.kickoff = NOW + 3_600_000L;
        assertNull(OverMatches.recognise("Sky Sports Main Event", Collections.emptyList(), games, own, usual, NOW));
        // Another channel altogether.
        assertNull(OverMatches.recognise("BBC One", Collections.emptyList(), games, own, usual, NOW));
        // A streaming service is never a channel.
        own.put("2", Collections.singletonList("Amazon Prime Video"));
        assertNull(OverMatches.recognise("Amazon Prime Video", Collections.emptyList(), games, own, usual, NOW));
        assertNull(OverMatches.recognise("Sky Sports Main Event", Collections.emptyList(), Collections.emptyList(), own, usual, NOW));
    }

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
}
