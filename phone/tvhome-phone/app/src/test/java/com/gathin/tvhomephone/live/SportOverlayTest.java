package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.SportBoard;
import com.gathin.broadcast.TennisScreen;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The player's tennis, boxing and UFC board: tennis as the picture has it, the bell's clock, its moments. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SportOverlayTest {

    private static SportBoard tennis(int[] a, int[] b) {
        SportBoard s = SportOverlay.sample(SportBoard.TENNIS, false);
        s.id = "m1";
        s.a.games = a;
        s.b.games = b;
        s.a.tiebreak = new int[a.length];
        s.b.tiebreak = new int[b.length];
        Arrays.fill(s.a.tiebreak, -1);
        Arrays.fill(s.b.tiebreak, -1);
        s.a.points = "";
        s.b.points = "";
        s.a.serving = false;
        s.b.serving = false;
        return s;
    }

    /** The family's picture (WTA Finals): ESPN at 3-6 5-1, the channel's board "A. SABALENKA [2] 3 5 15 / E. SVITOLINA [6] 6 1 30". */
    private static SportBoard sabalenka() {
        SportBoard m = tennis(new int[]{3, 5}, new int[]{6, 1});
        m.a.name = "Aryna Sabalenka";
        m.a.shortName = "Sabalenka";
        m.b.name = "Elina Svitolina";
        m.b.shortName = "Svitolina";
        return m;
    }

    /** Its words as the reader gives them, in the part's pixels (rows 40 apart, words 30 tall). */
    private static List<TennisScreen.Word> words(String pa, String pb) {
        List<TennisScreen.Word> w = new ArrayList<>();
        String[][] rows = {{"A.", "SABALENKA", "[2]", "3", "5", pa}, {"E.", "SVITOLINA", "[6]", "6", "1", pb}};
        float[] xs = {100, 140, 330, 400, 450, 510};
        float[] ws = {30, 180, 40, 20, 20, 40};
        for (int r = 0; r < 2; r++) {
            float t = 200 + r * 40;
            for (int i = 0; i < rows[r].length; i++) w.add(new TennisScreen.Word(rows[r][i], xs[i], t, xs[i] + ws[i], t + 30));
        }
        return w;
    }

    @Test
    public void thePictureIsTheTruthWhileItsReadingIsFresh() {
        SportBoard espn = sabalenka();
        TennisScreen.Reading r = TennisScreen.read(words("15", "30"), espn);
        assertTrue(r.found);
        SportBoard shown = SportOverlay.truth(espn, r, 1_000L, 3_000L, -1);
        assertEquals("15", shown.a.points);
        assertEquals("30", shown.b.points);
        assertArrayEquals(new int[]{3, 5}, shown.a.games);
        // A game on in the picture before ESPN has it: the picture's.
        TennisScreen.Reading on = TennisScreen.read(words("0", "0"), espn);
        on.games = new int[][]{{3, 6}, {6, 1}};
        assertArrayEquals(new int[]{3, 6}, SportOverlay.truth(espn, on, 1_000L, 2_000L, -1).a.games);
        // Its reading older than FRESH_MS: ESPN's games, no points.
        SportBoard stale = SportOverlay.truth(espn, r, 1_000L, 1_000L + SportOverlay.FRESH_MS + 1, -1);
        assertEquals("", stale.a.points);
        assertArrayEquals(new int[]{3, 5}, stale.a.games);
        // The server given shows, fresh or not; none given: ESPN's (here none).
        assertTrue(SportOverlay.truth(espn, r, 1_000L, 3_000L, 0).a.serving);
        assertFalse(SportOverlay.truth(espn, r, 1_000L, 3_000L, 0).b.serving);
        assertTrue(SportOverlay.truth(espn, r, 1_000L, 1_000L + SportOverlay.FRESH_MS + 1, 1).b.serving);
        assertFalse(SportOverlay.truth(espn, r, 1_000L, 3_000L, -1).a.serving);
        assertEquals(-1, SportOverlay.espnServer(espn));
    }

    @Test
    public void theChannelsBoardIsFoundAndReadAgainWhereItIs() {
        SportBoard espn = sabalenka();
        TennisScreen.Reading r = TennisScreen.read(words("15", "30"), espn);
        // Back to the picture's shares: the part's pixels times its scale, from its corner.
        float[] box = SportOverlay.shares(r, 0.1f, 0.6f, 0.001f, 0.002f);
        assertEquals(0.1f + r.l * 0.001f, box[0], 1e-6);
        assertEquals(0.6f + r.t * 0.002f, box[1], 1e-6);
        assertEquals(r.row * 0.002f, box[4], 1e-6);
        // Read next: that box with a little round it, its rows scaled to read ROW_PX tall; not found, the bands in turn.
        float[] at = {0.10f, 0.70f, 0.40f, 0.80f, 0.03f};
        float[] next = SportOverlay.area(at, 0, 720);
        assertEquals(0.055f, next[0], 1e-4);
        assertEquals(0.685f, next[1], 1e-4);
        assertEquals(0.445f, next[2], 1e-4);
        assertEquals(0.815f, next[3], 1e-4);
        assertEquals(SportOverlay.ROW_PX / (0.03f * 720), next[4], 1e-4);
        assertArrayEquals(new float[]{0f, 0f, 0.62f, 0.36f, 0f}, SportOverlay.area(null, 0, 720), 0f);
        assertArrayEquals(new float[]{0f, 0.64f, 0.62f, 1f, 0f}, SportOverlay.area(null, 1, 720), 0f);
        // Our board moves with theirs only when theirs has moved, not for each reading's few pixels.
        assertTrue(SportOverlay.moved(null, at));
        assertFalse(SportOverlay.moved(at, new float[]{0.103f, 0.702f, 0.401f, 0.803f, 0.03f}));
        assertTrue(SportOverlay.moved(at, new float[]{0.20f, 0.70f, 0.50f, 0.80f, 0.03f}));
        assertTrue(SportOverlay.moved(at, new float[]{0.10f, 0.70f, 0.40f, 0.86f, 0.03f}));
    }

    @Test
    public void theServerIsKeptSteady() {
        SportOverlay.Serving s = new SportOverlay.Serving();
        assertEquals(-1, s.now(0, -1));
        s.seen(0, 1_000L);
        assertEquals(0, s.now(1_000L, -1));
        // The other once: kept; twice running: changed.
        s.seen(1, 2_000L);
        assertEquals(0, s.now(2_000L, -1));
        s.seen(1, 3_000L);
        assertEquals(1, s.now(3_000L, -1));
        // Once, then the first again: no change.
        s.seen(0, 4_000L);
        s.seen(1, 5_000L);
        assertEquals(1, s.now(5_000L, -1));
        // Nothing marked: the last kept a while, then ESPN's, else none.
        s.seen(-1, 6_000L);
        assertEquals(1, s.now(5_000L + SportOverlay.Serving.KEEP_MS, -1));
        assertEquals(0, s.now(5_000L + SportOverlay.Serving.KEEP_MS + 1, 0));
        assertEquals(-1, s.now(5_000L + SportOverlay.Serving.KEEP_MS + 1, -1));
    }

    @Test
    public void aSetOrAGameIsTakenOnceWhereverItStands() {
        // Where a match stands: its sets done, then the set in play's games.
        assertEquals(106L, SportOverlay.mark(sabalenka()));
        assertEquals(107L, SportOverlay.mark(tennis(new int[]{3, 5}, new int[]{6, 2})));
        assertEquals(200L, SportOverlay.mark(tennis(new int[]{3, 7, 0}, new int[]{6, 5, 0})));
        assertTrue(SportOverlay.mark(tennis(new int[]{3, 7, 0}, new int[]{6, 5, 0})) > SportOverlay.mark(tennis(new int[]{3, 6}, new int[]{6, 5})));
        // The tab: its first twenty seconds, between rounds, at the end.
        SportBoard f = SportOverlay.sample(SportBoard.UFC, false);
        assertTrue(SportOverlay.tabWanted(f, 5_000L));
        assertFalse(SportOverlay.tabWanted(f, 60_000L));
        f.resting = true;
        assertTrue(SportOverlay.tabWanted(f, 60_000L));
        assertTrue(SportOverlay.tabWanted(SportOverlay.sample(SportBoard.UFC, true), 60_000L));
    }

    @Test
    public void theBoxingClockRunsFromTheBellTapped() {
        SportBoard box = SportOverlay.sample(SportBoard.BOXING, false);
        assertEquals("pre", SportOverlay.boxingAt(box, 0, 5_000L).state);
        long bell = 1_000_000L;
        SportBoard r1 = SportOverlay.boxingAt(box, bell, bell + 30_000L);
        assertEquals("in", r1.state);
        assertEquals(1, r1.round);
        assertEquals("2:30", r1.clockLeft());
        assertTrue(SportOverlay.boxingAt(box, bell, bell + 200_000L).resting);
        SportBoard end = SportOverlay.boxingAt(box, bell, bell + 12 * 240_000L);
        assertEquals("post", end.state);
        assertEquals("End", end.method);
        // The first tap: round one from now. A later one in a round's first half: that round from now; in the minute
        // between: the next round from now.
        assertEquals(5_000L, SportOverlay.bellFor(box, 0, 5_000L));
        long again = SportOverlay.bellFor(box, bell, bell + 240_000L + 20_000L);
        assertEquals(2, SportOverlay.boxingAt(box, again, bell + 240_000L + 20_000L).round);
        assertEquals("3:00", SportOverlay.boxingAt(box, again, bell + 240_000L + 20_000L).clockLeft());
        long between = SportOverlay.bellFor(box, bell, bell + 200_000L);
        SportBoard r2 = SportOverlay.boxingAt(box, between, bell + 200_000L);
        assertEquals(2, r2.round);
        assertFalse(r2.resting);
    }

    @Test
    public void theMatchShownStaysUntilThePictureNamesAnother() {
        SportBoard one = tennis(new int[]{3}, new int[]{2});
        SportBoard two = SportOverlay.sample(SportBoard.TENNIS, false);
        two.id = "m2";
        two.a.name = "Ana Brightwood";
        two.a.shortName = "Brightwood";
        two.b.name = "Zoe Kettering";
        two.b.shortName = "Kettering";
        two.a.seed = "1";
        two.b.seed = "3";
        List<SportBoard> got = new ArrayList<>(Arrays.asList(one, two));
        // Nothing shown yet: the best-ranked live one.
        assertSame(two, SportOverlay.choose(got, null, 0, "", "Live WTA Finals", "", 0L));
        // Shown: kept, unless the picture names the other.
        assertSame(one, SportOverlay.choose(got, one, 0, "", "Live WTA Finals", "", 0L));
        assertSame(two, SportOverlay.choose(got, one, 0, "", "Live WTA Finals", "BRIGHTWOOD 6 2 KETTERING 3 1", 0L));
        // Chosen by hand: that one.
        assertSame(one, SportOverlay.choose(got, two, 0, "m1", "Live WTA Finals", "", 0L));
        // Over: its result kept a while, then the next.
        one.state = "post";
        assertSame(one, SportOverlay.choose(got, one, 1_000L, "", "Live WTA Finals", "", 1_000L + 60_000L));
        assertSame(two, SportOverlay.choose(got, one, 1_000L, "", "Live WTA Finals", "", 1_000L + SportOverlay.RESULT_MS + 1));
    }
}
