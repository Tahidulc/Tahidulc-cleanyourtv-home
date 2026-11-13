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
    public void aReadingCountsFiveSeconds() {
        assertTrue(SportCover.fresh(1_000L, 1_000L + SportCover.FRESH_MS));
        assertFalse(SportCover.fresh(1_000L, 1_001L + SportCover.FRESH_MS));
        assertFalse(SportCover.fresh(0L, 10L));
    }

    @Test
    public void theirBoardIsLookedAtWhereItWasGrown() {
        // Their box at 100..500 x 900..980 on a 1920x1080 screen, grown 30% round its middle.
        float[] f = SportCover.lookBox(new float[]{100, 900, 500, 980}, 1920, 1080, 1.3f);
        assertEquals((300 - 260) / 1920f, f[0], 1e-4);
        assertEquals((940 - 52) / 1080f, f[1], 1e-4);
        assertEquals((300 + 260) / 1920f, f[2], 1e-4);
        assertEquals((940 + 52) / 1080f, f[3], 1e-4);
        // Kept on the screen.
        float[] edge = SportCover.lookBox(new float[]{0, 1000, 300, 1080}, 1920, 1080, 1.3f);
        assertEquals(0f, edge[0], 0f);
        assertEquals(1f, edge[3], 0f);
        // Rows 30 px apart read at least 40 tall: the part made a third taller; rows 60 apart as they are.
        assertEquals(Math.round(104 * 40f / 30f), SportCover.lookHeight(30f, 104f));
        assertEquals(104, SportCover.lookHeight(60f, 104f));
        assertEquals(SportCover.MOST_H, SportCover.lookHeight(5f, 900f));
    }

    @Test
    public void wordsComeInThePicturesOwnPixelsAndNeverOurs() {
        // A part 0.5..1 x 0.5..1 of a 1920x1080 screen, read off a bitmap 480x270 (half size).
        float[] part = {0.5f, 0.5f, 1f, 1f};
        List<String> texts = Arrays.asList("SABALENKA", "15");
        List<int[]> boxes = Arrays.asList(new int[]{10, 20, 110, 40}, new int[]{300, 20, 330, 40});
        List<com.gathin.broadcast.TennisScreen.Word> all = SportCover.words(texts, boxes, part, 480, 270, 1920, 1080, null);
        assertEquals(2, all.size());
        assertEquals(10f, all.get(0).l, 0f);
        assertEquals(40f, all.get(0).b, 0f);
        // Ours on the screen where "15" sits (960 + 315*2 = 1590, 540 + 30*2 = 600): left out.
        List<com.gathin.broadcast.TennisScreen.Word> mine = SportCover.words(texts, boxes, part, 480, 270, 1920, 1080,
                new int[][]{{1500, 560, 1700, 640}});
        assertEquals(1, mine.size());
        assertEquals("SABALENKA", mine.get(0).text);
        // The box found, put back on the screen.
        com.gathin.broadcast.TennisScreen.Reading r = new com.gathin.broadcast.TennisScreen.Reading();
        r.l = 10;
        r.t = 20;
        r.r = 330;
        r.b = 80;
        r.row = 25;
        SportCover.toScreen(r, part, 480, 270, 1920, 1080);
        assertEquals(980f, r.l, 1e-3);
        assertEquals(580f, r.t, 1e-3);
        assertEquals(1620f, r.r, 1e-3);
        assertEquals(700f, r.b, 1e-3);
        assertEquals(50f, r.row, 1e-3);
        // Ours over it: at its box, as wide, its rows at least half its height.
        float[] c = SportCover.coverAt(r);
        assertEquals(980f, c[0], 1e-3);
        assertEquals(580f, c[1], 1e-3);
        assertEquals(640f, c[2], 1e-3);
        assertEquals(60f, c[3], 1e-3);
        assertFalse(SportCover.moved(c, new float[]{981, 581, 645, 61}));
        assertTrue(SportCover.moved(c, new float[]{990, 580, 640, 60}));
        assertTrue(SportCover.moved(c, new float[]{980, 580, 700, 60}));
    }

    @Test
    public void theServerIsKeptSteady() {
        long t = 100_000L;
        // Seen once: not yet; twice running: shown.
        long[] st = SportCover.serve(-1, -1, 0L, 0, t);
        assertEquals(-1, st[0]);
        st = SportCover.serve((int) st[0], (int) st[1], st[2], 0, t + 1_300L);
        assertEquals(0, st[0]);
        // The other once: still the first; nothing said meanwhile keeps both.
        st = SportCover.serve((int) st[0], (int) st[1], st[2], 1, t + 2_600L);
        assertEquals(0, st[0]);
        st = SportCover.serve((int) st[0], (int) st[1], st[2], -1, t + 3_900L);
        assertEquals(0, st[0]);
        st = SportCover.serve((int) st[0], (int) st[1], st[2], 1, t + 5_200L);
        assertEquals(1, st[0]);
        // Unsaid: kept twenty seconds, then none (ESPN's).
        st = SportCover.serve((int) st[0], (int) st[1], st[2], -1, t + 5_200L + SportCover.SERVE_KEEP_MS);
        assertEquals(1, st[0]);
        st = SportCover.serve((int) st[0], (int) st[1], st[2], -1, t + 5_201L + SportCover.SERVE_KEEP_MS);
        assertEquals(-1, st[0]);
    }

    @Test
    public void aSetsMomentComesOnce() {
        SportBoard b = tennis("in", new int[]{6, 2}, new int[]{4, 1});
        SportBoard.Moment m = SportBoard.moment(tennis("in", new int[]{5}, new int[]{4}), tennis("in", new int[]{6, 0}, new int[]{4, 0}));
        assertEquals("SET KOVALENKO", m.words);
        // Its key holds the sets then: the same set from the picture and later from ESPN is one key.
        assertEquals(SportCover.momentKey(m, tennis("in", new int[]{6, 0}, new int[]{4, 0})),
                SportCover.momentKey(m, tennis("in", new int[]{6, 0}, new int[]{4, 0})));
        assertFalse(SportCover.momentKey(m, b).equals(SportCover.momentKey(m, tennis("in", new int[]{6, 6, 0}, new int[]{4, 2, 0}))));
        assertEquals(15, SportCover.gamesTotal(tennis("in", new int[]{6, 2}, new int[]{4, 3})));
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
