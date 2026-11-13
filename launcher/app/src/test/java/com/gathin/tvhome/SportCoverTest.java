package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.SportBoard;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The tennis, boxing and UFC board over TiviMate: its sounds' moments, boxing's bell, its tab, what it keeps showing. */
public class SportCoverTest {

    private static SportBoard tennis(String state, int[] a, int[] b) {
        SportBoard s = SportCover.sample(SportBoard.TENNIS);
        s.state = state;
        s.a.games = a;
        s.b.games = b;
        s.a.tiebreak = new int[a.length];
        s.b.tiebreak = new int[b.length];
        Arrays.fill(s.a.tiebreak, -1);
        Arrays.fill(s.b.tiebreak, -1);
        return s;
    }

    @Test
    public void aTennisGameWonIsToldFromASet() {
        SportBoard was = tennis("in", new int[]{6, 3}, new int[]{4, 3});
        // A game in the set in play: the score swish.
        assertTrue(SportCover.gameWon(was, tennis("in", new int[]{6, 4}, new int[]{4, 3})));
        assertTrue(SportCover.gameWon(was, tennis("in", new int[]{6, 3}, new int[]{4, 4})));
        // Nothing new; a new set begun (a set's moment, not a game's); another match; a fight: no.
        assertFalse(SportCover.gameWon(was, tennis("in", new int[]{6, 3}, new int[]{4, 3})));
        assertFalse(SportCover.gameWon(was, tennis("in", new int[]{6, 6, 0}, new int[]{4, 3, 0})));
        SportBoard other = tennis("in", new int[]{6, 4}, new int[]{4, 3});
        other.id = "another";
        assertFalse(SportCover.gameWon(was, other));
        assertFalse(SportCover.gameWon(null, was));
        assertFalse(SportCover.gameWon(SportCover.sample(SportBoard.UFC), SportCover.sample(SportBoard.UFC)));
        // The match over: its moment (the goal sound), not a game's swish.
        assertFalse(SportCover.gameWon(was, tennis("post", new int[]{6, 4}, new int[]{4, 3})));
    }

    @Test
    public void theBellCardAsksAtEachBell() {
        SportBoard b = SportCover.sample(SportBoard.BOXING);
        // Before the first bell: round one's, while the first bell is still to come.
        b.state = "pre";
        assertEquals(1, SportCover.bellDue(0L, b, 60_000L));
        assertEquals(0, SportCover.bellDue(0L, b, SportCover.BELL_WAIT_MS));
        // Between rounds: the next round's.
        b.state = "in";
        b.round = 3;
        b.resting = true;
        b.secsIn = 20;
        assertEquals(4, SportCover.bellDue(1L, b, 0L));
        // In a round's first seconds, that round's; later in it, none.
        b.resting = false;
        b.secsIn = 5;
        assertEquals(3, SportCover.bellDue(1L, b, 0L));
        b.secsIn = SportCover.BELL_LATE_S;
        assertEquals(0, SportCover.bellDue(1L, b, 0L));
        // All the rounds gone: none.
        b.state = "post";
        assertEquals(0, SportCover.bellDue(1L, b, 0L));
        assertEquals("Start the round clock with your channel's", SportCover.bellWords(1));
        assertEquals("Round 4 · keep the clock with your channel's", SportCover.bellWords(4));
    }

    @Test
    public void okAtABellPutsTheClockRight() {
        long pressed = 10_000_000L;
        // Round one's bell: the clock from that moment.
        assertEquals(pressed, SportCover.bellFrom(pressed, 1, 180, 60));
        // Round four's: three rounds and their rests before it.
        long first = SportCover.bellFrom(pressed, 4, 180, 60);
        assertEquals(pressed - 3 * 240_000L, first);
        int[] st = SportBoard.bell(first, pressed + 1_000L, 180, 60, 12);
        assertEquals(4, st[0]);
        assertEquals(1, st[1]);
        assertEquals(0, st[2]);
    }

    @Test
    public void theTabIsUpAtItsMoments() {
        SportBoard live = SportCover.sample(SportBoard.UFC);
        assertTrue(SportCover.tabWanted(live, 5_000L, false));
        assertFalse(SportCover.tabWanted(live, SportCover.TAB_MS, false));
        assertTrue(SportCover.tabWanted(live, SportCover.TAB_MS, true));
        live.resting = true;
        assertTrue(SportCover.tabWanted(live, SportCover.TAB_MS, false));
        live.resting = false;
        live.state = "post";
        assertTrue(SportCover.tabWanted(live, SportCover.TAB_MS, false));
        SportBoard tie = tennis("in", new int[]{6, 6}, new int[]{4, 6});
        assertTrue(SportCover.tabWanted(tie, SportCover.TAB_MS, false));
        assertFalse(SportCover.tabWanted(null, 0L, true));
    }

    @Test
    public void aFinishedMatchStaysUpAWhile() {
        SportBoard shown = tennis("post", new int[]{6, 6}, new int[]{4, 3});
        shown.id = "m1";
        SportBoard next = tennis("in", new int[]{2}, new int[]{1});
        next.id = "m2";
        List<SportBoard> all = Arrays.asList(shown, next);
        // Just over: its result kept, though another is on.
        assertSame(shown, SportCover.keep(all, "m1", next, 30_000L));
        // Over a while: on to the next.
        assertSame(next, SportCover.keep(all, "m1", next, SportCover.RESULT_MS));
        // Nothing else on: kept.
        assertSame(shown, SportCover.keep(all, "m1", null, SportCover.RESULT_MS * 5));
        // The one shown still on: what was picked.
        assertSame(next, SportCover.keep(all, "m2", next, 0L));
        assertNull(SportCover.keep(Collections.emptyList(), "", null, 0L));
    }

    @Test
    public void theTrySamplesAreMadeUp() {
        SportBoard t = SportCover.sample(SportBoard.TENNIS), b = SportCover.sample(SportBoard.BOXING), u = SportCover.sample(SportBoard.UFC);
        assertEquals("Kovalenko", t.a.shortName);
        assertEquals("Hartwell", t.b.shortName);
        assertEquals("Archer", b.a.shortName);
        assertEquals("Mendes", b.b.shortName);
        assertEquals("Vance", u.a.shortName);
        assertEquals("Morrow", u.b.shortName);
        assertTrue(t.live() && b.live() && u.live());
        assertEquals(SportBoard.RED_CORNER, u.a.colour);
    }

    @Test
    public void aMatchOnTheChannelKeepsItsPlace() {
        Map<String, List<String>> on = new HashMap<>();
        on.put("game:1", Arrays.asList("TNT Sports 1", "TNT Sports 1 HD"));
        assertTrue(OverlayPrompt.footballHere(Collections.singletonList("TNT Sports 1"), on));
        assertFalse(OverlayPrompt.footballHere(Collections.singletonList("Sky Sports Tennis"), on));
        assertFalse(OverlayPrompt.footballHere(Collections.emptyList(), on));
        assertEquals("s|100|Live Tennis: Sabalenka v Svitolina", OverlayPrompt.sportKey("Live Tennis: Sabalenka v Svitolina", 100L));
    }
}
