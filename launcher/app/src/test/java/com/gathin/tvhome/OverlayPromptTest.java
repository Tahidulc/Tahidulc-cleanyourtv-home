package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** OverlayPrompt: "Scoreboard over matches" (Automatic, Ask, Off) and the match it sees being watched. */
public class OverlayPromptTest {
    private static final String A = OverlayPrompt.AUTO, ASK = OverlayPrompt.ASK, OFF = OverlayPrompt.OFF;
    private static final int NOTHING = OverlayPrompt.NOTHING, BUTTON = OverlayPrompt.BUTTON, BOARD = OverlayPrompt.BOARD;

    // ---- the setting -----------------------------------------------------------------------------------------------------

    @Test
    public void untilChosenItIsAskOrOffAsTheOldSwitchWas() {
        // Nobody who never touches it gets anything new: "Ask over a match" on (its default) is Ask, off is Off.
        assertEquals(ASK, OverlayPrompt.modeOf(null, true));
        assertEquals(OFF, OverlayPrompt.modeOf(null, false));
        assertEquals(ASK, OverlayPrompt.modeOf("nonsense", true));
        // Once chosen, as chosen.
        assertEquals(A, OverlayPrompt.modeOf(A, false));
        assertEquals(OFF, OverlayPrompt.modeOf(OFF, true));
        assertEquals(ASK, OverlayPrompt.modeOf(ASK, false));
    }

    @Test
    public void okStepsAskAutomaticOffAndRound() {
        assertEquals(A, OverlayPrompt.next(ASK));
        assertEquals(OFF, OverlayPrompt.next(A));
        assertEquals(ASK, OverlayPrompt.next(OFF));
    }

    // ---- the decision: mode × recognised × dismissed × already on ----------------------------------------------------------

    @Test
    public void automaticPutsTheBoardOnForASureFootballMatch() {
        assertEquals(BOARD, OverlayPrompt.decide(A, true, true, false, false));
        // Not sure of the game, F1, or a competition switched off: the button, as on Ask.
        assertEquals(BUTTON, OverlayPrompt.decide(A, true, false, false, false));
    }

    @Test
    public void askAlwaysAsksWithTheButton() {
        assertEquals(BUTTON, OverlayPrompt.decide(ASK, true, true, false, false));
        assertEquals(BUTTON, OverlayPrompt.decide(ASK, true, false, false, false));
        // Anything not known is Ask.
        assertEquals(BUTTON, OverlayPrompt.decide(null, true, true, false, false));
    }

    @Test
    public void offNeverDoesAnything() {
        for (boolean itself : new boolean[]{true, false}) {
            for (boolean dismissed : new boolean[]{true, false}) {
                for (boolean on : new boolean[]{true, false}) {
                    assertEquals(NOTHING, OverlayPrompt.decide(OFF, true, itself, dismissed, on));
                }
            }
        }
    }

    @Test
    public void nothingWhenNotRecognisedDismissedOrOnAlready() {
        for (String mode : new String[]{A, ASK, OFF}) {
            // Nothing seen being watched.
            assertEquals(NOTHING, OverlayPrompt.decide(mode, false, true, false, false));
            // Asked about, come on or put away with Back (or switched off by hand) for this game before: once a game.
            assertEquals(NOTHING, OverlayPrompt.decide(mode, true, true, true, false));
            assertEquals(NOTHING, OverlayPrompt.decide(mode, true, false, true, false));
            // On already for this game.
            assertEquals(NOTHING, OverlayPrompt.decide(mode, true, true, false, true));
        }
    }

    // ---- which games count ------------------------------------------------------------------------------------------------

    @Test
    public void aGameCountsLiveOrFifteenMinutesBeforeOrLate() {
        long now = 1_800_000_000_000L;
        assertTrue(OverlayPrompt.onOrSoon(game("1", 0, now - 3_600_000L), now));
        assertTrue(OverlayPrompt.onOrSoon(game("1", 1, now + 14 * 60_000L), now));
        assertFalse(OverlayPrompt.onOrSoon(game("1", 1, now + 16 * 60_000L), now));
        // Kick-off gone but the scores still to say so: a late start, or a poll behind.
        assertTrue(OverlayPrompt.onOrSoon(game("1", 1, now - 2 * 60_000L), now));
        assertFalse(OverlayPrompt.onOrSoon(game("1", 1, now - 31 * 60_000L), now));
        assertFalse(OverlayPrompt.onOrSoon(game("1", 2, now - 60 * 60_000L), now));
        assertFalse(OverlayPrompt.onOrSoon(null, now));
    }

    // ---- the game watched, by the channel tuned ----------------------------------------------------------------------------

    /** A Champions League evening: two games, each on its own channel by Sky's guide, both on TNT Sports 1 and 2 as usual. */
    private static Map<String, List<String>> own, all;
    private static ScoresTicker.Game arsenal, chelsea;

    private static void championsLeague(long now) {
        arsenal = game("10", 0, now - 600_000L);
        chelsea = game("11", 0, now - 600_000L);
        own = new HashMap<>();
        all = new HashMap<>();
        own.put("game:10", Collections.singletonList("TNT Sports 1"));
        own.put("game:11", Collections.singletonList("TNT Sports 2"));
        all.put("game:10", Arrays.asList("TNT Sports 1", "TNT Sports 2", "Amazon Prime Video"));
        all.put("game:11", Arrays.asList("TNT Sports 2", "TNT Sports 1", "Amazon Prime Video"));
    }

    @Test
    public void theGameOnItsOwnChannelIsTheOneAndSure() {
        long now = 1_800_000_000_000L;
        championsLeague(now);
        // Before, both matched TNT Sports 1 by the competition's broadcasters: the first of them was taken.
        OverlayPrompt.Found f = OverlayPrompt.watched(Arrays.asList(arsenal, chelsea), null,
                Collections.singletonList("UK: TNT Sports 2 HD"), own, all);
        assertNotNull(f);
        assertSame(chelsea, f.game);
        assertTrue(f.sure);
        // Tuned from an alert's Watch: every channel tried kept, the first the one wanted.
        f = OverlayPrompt.watched(Arrays.asList(arsenal, chelsea), null, Arrays.asList("TNT Sports 1 UHD", "TNT Sports 1", "TNT Sports 2"), own, all);
        assertSame(arsenal, f.game);
        assertTrue(f.sure);
    }

    @Test
    public void aUsualBroadcasterIsSureOnlyForTheOneGameOnIt() {
        long now = 1_800_000_000_000L;
        championsLeague(now);
        // Neither game's own channel known (the guide not read): either could be on TNT Sports 1.
        own.put("game:10", Collections.emptyList());
        own.put("game:11", Collections.emptyList());
        OverlayPrompt.Found f = OverlayPrompt.watched(Arrays.asList(arsenal, chelsea), null, Collections.singletonList("TNT Sports 1"), own, all);
        assertNotNull(f);
        assertFalse(f.sure);
        // One game on now: that one.
        f = OverlayPrompt.watched(Collections.singletonList(arsenal), null, Collections.singletonList("TNT Sports 1"), own, all);
        assertSame(arsenal, f.game);
        assertTrue(f.sure);
        // But not when its own channel is known to be another: Sky's guide has it on TNT Sports 1, this is TNT Sports 2.
        own.put("game:10", Collections.singletonList("TNT Sports 1"));
        f = OverlayPrompt.watched(Collections.singletonList(arsenal), null, Collections.singletonList("TNT Sports 2"), own, all);
        assertSame(arsenal, f.game);
        assertFalse(f.sure);
        assertTrue(OverlayPrompt.unplaced(null));
        assertTrue(OverlayPrompt.unplaced(Collections.emptyList()));
        assertFalse(OverlayPrompt.unplaced(Collections.singletonList("TNT Sports 1")));
    }

    @Test
    public void matchModesGameIsSureAndNothingTunedIsNothing() {
        long now = 1_800_000_000_000L;
        championsLeague(now);
        OverlayPrompt.Found f = OverlayPrompt.watched(Arrays.asList(arsenal, chelsea), "11", Collections.singletonList("TNT Sports 1"), own, all);
        assertSame(chelsea, f.game);
        assertTrue(f.sure);
        assertNull(OverlayPrompt.watched(Arrays.asList(arsenal, chelsea), null, Collections.emptyList(), own, all));
        assertNull(OverlayPrompt.watched(Arrays.asList(arsenal, chelsea), null, null, own, all));
        assertNull(OverlayPrompt.watched(Arrays.asList(arsenal, chelsea), null, Collections.singletonList("BBC One"), own, all));
    }

    @Test
    public void liveComesBeforeAGameStillToKickOff() {
        long now = 1_800_000_000_000L;
        ScoresTicker.Game early = game("1", 0, now - 90 * 60_000L), late = game("2", 1, now + 10 * 60_000L);
        Map<String, List<String>> none = new HashMap<>(), usual = new HashMap<>();
        usual.put("game:1", Collections.singletonList("Sky Sports Main Event"));
        usual.put("game:2", Collections.singletonList("Sky Sports Main Event"));
        OverlayPrompt.Found f = OverlayPrompt.watched(Arrays.asList(late, early), null, Collections.singletonList("Sky Sports Main Event"), none, usual);
        assertSame(early, f.game);
        assertFalse(f.sure);
    }

    // ---- the game watched, by the player's banner --------------------------------------------------------------------------

    @Test
    public void theBannerNamesTheGameOnItsOwnChannel() {
        long now = 1_800_000_000_000L;
        championsLeague(now);
        // Before, both games' channels named TNT Sports 2: the banner said nothing on a Champions League evening.
        assertArrayEquals(new String[]{"game:11", "UK: TNT Sports 2 HD"},
                OverlayPrompt.among(Arrays.asList("UK: TNT Sports 2 HD", "UEFA Champions League", "20:00 - 22:15"), own, all));
        // The guide or a list names two channels: nothing.
        assertNull(OverlayPrompt.among(Arrays.asList("TNT Sports 1 HD", "TNT Sports 2 HD"), own, all));
        // Known only by the competition's broadcasters, two games on it: nothing either.
        Map<String, List<String>> noOwn = new HashMap<>();
        assertNull(OverlayPrompt.among(Collections.singletonList("TNT Sports 1"), noOwn, all));
        // One game on it: that one.
        all.remove("game:11");
        assertArrayEquals(new String[]{"game:10", "TNT Sports 1"}, OverlayPrompt.among(Collections.singletonList("TNT Sports 1"), noOwn, all));
    }

    private static ScoresTicker.Game game(String id, int state, long kickoff) {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = id;
        g.state = state;
        g.kickoff = kickoff;
        return g;
    }
}
