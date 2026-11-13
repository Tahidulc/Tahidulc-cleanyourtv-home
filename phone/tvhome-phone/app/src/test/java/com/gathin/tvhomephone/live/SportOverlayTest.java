package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import com.gathin.broadcast.SportBoard;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The player's tennis, boxing and UFC board: the channel's tennis read off the picture, the bell's clock, its moments. */
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
        return s;
    }

    @Test
    public void theChannelsTennisScoreboardIsReadRowByRow() {
        SportBoard m = tennis(new int[]{6, 3}, new int[]{4, 2});
        // One line a row.
        SportScreen.Reading r = SportScreen.tennis(Arrays.asList("KOVALENKO 6 3 40", "HARTWELL 4 2 15"),
                Arrays.asList(new Rect(10, 10, 300, 40), new Rect(10, 50, 300, 80)), m);
        assertTrue(r.found);
        assertArrayEquals(new String[]{"40", "15"}, r.points);
        assertArrayEquals(new int[]{3, 2}, r.games);
        // Columns read apart: the numbers level with each name, left to right; a seed before the name left out.
        List<String> lines = Arrays.asList("(2) KOVALENKO", "6", "3", "A", "HARTWELL", "4", "2", "40");
        List<Rect> boxes = Arrays.asList(new Rect(10, 10, 150, 40), new Rect(200, 12, 220, 38), new Rect(240, 12, 260, 38),
                new Rect(290, 12, 310, 38), new Rect(10, 50, 150, 80), new Rect(200, 52, 220, 78), new Rect(240, 52, 260, 78),
                new Rect(290, 52, 310, 78));
        r = SportScreen.tennis(lines, boxes, m);
        assertTrue(r.found);
        assertArrayEquals(new String[]{"AD", "40"}, r.points);
        // The picture a game ahead of the feed: taken; further apart (another court, a misread): not.
        r = SportScreen.tennis(Arrays.asList("KOVALENKO 6 4 0", "HARTWELL 4 2 0"),
                Arrays.asList(new Rect(10, 10, 300, 40), new Rect(10, 50, 300, 80)), m);
        assertTrue(r.found);
        assertEquals(4, r.games[0]);
        r = SportScreen.tennis(Arrays.asList("KOVALENKO 6 5 0", "HARTWELL 4 2 0"),
                Arrays.asList(new Rect(10, 10, 300, 40), new Rect(10, 50, 300, 80)), m);
        assertFalse(r.found);
        // Points on one row only, a row not found, a points column that isn't one: not taken.
        assertFalse(SportScreen.tennis(Arrays.asList("KOVALENKO 6 3 40", "HARTWELL 4 2"),
                Arrays.asList(new Rect(10, 10, 300, 40), new Rect(10, 50, 300, 80)), m).found);
        assertFalse(SportScreen.tennis(Arrays.asList("KOVALENKO 6 3 40"), Arrays.asList(new Rect(10, 10, 300, 40)), m).found);
        assertFalse(SportScreen.tennis(Arrays.asList("KOVALENKO 6 3 45", "HARTWELL 4 2 15"),
                Arrays.asList(new Rect(10, 10, 300, 40), new Rect(10, 50, 300, 80)), m).found);
        // A tiebreak's count is its points.
        SportBoard tb = tennis(new int[]{6, 6}, new int[]{4, 6});
        r = SportScreen.tennis(Arrays.asList("KOVALENKO 6 6 5", "HARTWELL 4 6 3"),
                Arrays.asList(new Rect(10, 10, 300, 40), new Rect(10, 50, 300, 80)), tb);
        assertTrue(r.found);
        assertArrayEquals(new String[]{"5", "3"}, r.points);
        // Taken onto the feed's board.
        SportOverlay.withScreen(m, SportScreen.tennis(Arrays.asList("KOVALENKO 6 4 15", "HARTWELL 4 2 0"),
                Arrays.asList(new Rect(10, 10, 300, 40), new Rect(10, 50, 300, 80)), m));
        assertEquals("15", m.a.points);
        assertEquals(4, m.a.games[1]);
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
        SportBoard rest = SportOverlay.boxingAt(box, bell, bell + 200_000L);
        assertTrue(rest.resting);
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
    public void aGameWonIsNotASetOrTheMatch() {
        SportBoard was = tennis(new int[]{6, 3}, new int[]{4, 2});
        SportBoard game = tennis(new int[]{6, 4}, new int[]{4, 2});
        assertTrue(SportOverlay.gameWon(was, game));
        assertFalse(SportOverlay.gameWon(was, was.copy()));
        SportBoard setUp = tennis(new int[]{5}, new int[]{4});
        SportBoard set = tennis(new int[]{6, 0}, new int[]{4, 0});
        assertFalse(SportOverlay.gameWon(setUp, set));
        assertEquals("SET KOVALENKO", SportBoard.moment(setUp, set).words);
        // The tab: its first twenty seconds, between rounds, at the end.
        SportBoard f = SportOverlay.sample(SportBoard.UFC, false);
        assertTrue(SportOverlay.tabWanted(f, 5_000L));
        assertFalse(SportOverlay.tabWanted(f, 60_000L));
        f.resting = true;
        assertTrue(SportOverlay.tabWanted(f, 60_000L));
        assertTrue(SportOverlay.tabWanted(SportOverlay.sample(SportBoard.UFC, true), 60_000L));
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
