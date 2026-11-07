package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** BoardCover and BoardPlace: the board as the screen shows it, its places, and its care for the screen. */
public class BoardCoverTest {

    private static BoardCover.Reading live(long seen, String home, String away, double clock, String detail) {
        return new BoardCover.Reading(seen, home, away, 0, false, clock, detail);
    }

    // ---- the clock ---------------------------------------------------------------------------------------------------

    @Test
    public void theClockIsMinutesAndSeconds() {
        assertArrayEquals(new String[]{"66:52", ""}, BoardCover.clockWords(4012, "67'"));
        assertArrayEquals(new String[]{"20:00", ""}, BoardCover.clockWords(1200, "21'"));
        assertArrayEquals(new String[]{"00:00", ""}, BoardCover.clockWords(0, "1'"));
    }

    @Test
    public void addedTimeStaysAtThePeriodsEndWithTheMinutesBeside() {
        // 46:30 by ESPN's clock in "45'+2'" is the first half's added time, not the second half.
        assertArrayEquals(new String[]{"45:00", "+2"}, BoardCover.clockWords(2790, "45'+2'"));
        assertArrayEquals(new String[]{"90:00", "+4"}, BoardCover.clockWords(5580, "90'+4'"));
        // No minutes added in ESPN's words: counted from the clock.
        assertArrayEquals(new String[]{"90:00", "+1"}, BoardCover.clockWords(5410, "90'"));
        assertArrayEquals(new String[]{"105:00", "+1"}, BoardCover.clockWords(105 * 60 + 20, "105'+1'"));
    }

    @Test
    public void theMinuteAndTheMinutesAddedAreReadFromEspnsWords() {
        assertEquals(67, BoardCover.minuteOf("67'"));
        assertEquals(47, BoardCover.minuteOf("45'+2'"));
        assertEquals(0, BoardCover.minuteOf("HT"));
        assertEquals(2, BoardCover.added("45'+2'"));
        assertEquals(0, BoardCover.added("67'"));
        assertEquals(0, BoardCover.added(null));
    }

    @Test
    public void theClockCountsOnFromWhenItWasSeenNeverPastTheMinute() {
        BoardCover.Reading r = live(1_000_000L, "1", "0", 4020, "68'");
        assertArrayEquals(new String[]{"67:00", ""}, BoardCover.clock(r, 1_000_000L));
        assertArrayEquals(new String[]{"67:25", ""}, BoardCover.clock(r, 1_025_000L));
        // ESPN's next minute not in yet: held at the minute's end.
        assertArrayEquals(new String[]{"67:59", ""}, BoardCover.clock(r, 1_090_000L));
        // Only the minute known: it began a minute before.
        assertArrayEquals(new String[]{"66:00", ""}, BoardCover.clock(live(1_000_000L, "1", "0", 0, "67'"), 1_000_000L));
        // Not running: no clock.
        assertNull(BoardCover.clock(new BoardCover.Reading(1_000_000L, "1", "0", 0, true, 2700, "HT"), 1_000_000L));
        assertNull(BoardCover.clock(new BoardCover.Reading(1_000_000L, "1", "0", 2, false, 5400, "FT"), 1_000_000L));
    }

    // ---- in step with the picture ------------------------------------------------------------------------------------

    @Test
    public void theBoardShowsWhatThePictureHasReached() {
        ArrayDeque<BoardCover.Reading> list = new ArrayDeque<>();
        BoardCover.add(list, live(100_000L, "0", "0", 3600, "61'"));
        BoardCover.add(list, live(120_000L, "1", "0", 3620, "61'"));
        BoardCover.add(list, live(140_000L, "1", "0", 3660, "62'"));
        // The channel 40 s behind: at 150 s the picture is at 110 s, before the goal the scores saw at 120 s.
        assertEquals("0", BoardCover.atScreen(list, 150_000L - 40_000L).home);
        assertEquals("1", BoardCover.atScreen(list, 160_000L - 40_000L).home);
        // Switched on with nothing old enough: the first there is.
        assertEquals("0", BoardCover.atScreen(list, 50_000L).home);
        assertNull(BoardCover.atScreen(new ArrayDeque<>(), 50_000L));
    }

    @Test
    public void aClockThatHasNotMovedKeepsWhenItWasFirstSeen() {
        ArrayDeque<BoardCover.Reading> list = new ArrayDeque<>();
        BoardCover.add(list, live(100_000L, "0", "0", 3600, "61'"));
        BoardCover.Reading same = live(120_000L, "0", "0", 3600, "61'");
        BoardCover.add(list, same);
        assertEquals(100_000L, same.since);
        BoardCover.Reading moved = live(140_000L, "0", "0", 3660, "62'");
        BoardCover.add(list, moved);
        assertEquals(140_000L, moved.since);
        // Out of order: not kept.
        BoardCover.add(list, live(130_000L, "5", "5", 3600, "61'"));
        assertEquals(3, list.size());
    }

    @Test
    public void onlyTheLastFewReadingsAreKept() {
        ArrayDeque<BoardCover.Reading> list = new ArrayDeque<>();
        for (int i = 0; i < BoardCover.KEEP + 10; i++) BoardCover.add(list, live(i * 20_000L, "0", "0", 60 * i, i + "'"));
        assertEquals(BoardCover.KEEP, list.size());
        // More than the longest wait a channel can be behind.
        assertTrue((BoardCover.KEEP - 1) * 20_000L > InStep.MAX_MS);
    }

    @Test
    public void aGoalIsTheScoresChange() {
        assertEquals(1, BoardCover.goalSide("0–0", "1–0"));
        assertEquals(2, BoardCover.goalSide("1–0", "1–1"));
        assertEquals(0, BoardCover.goalSide("1–1", "1–1"));
        // A goal taken off (VAR): no light.
        assertEquals(0, BoardCover.goalSide("2–1", "1–1"));
        assertEquals(0, BoardCover.goalSide(null, "1–0"));
    }

    @Test
    public void theBoardSaysWhatTheGameIsAtOnTheScreen() {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.home = "EVE";
        g.away = "TOT";
        g.league = "PREMIER LEAGUE";
        g.homeColor = 0xFF003399;
        g.awayColor = 0xFF000000;
        g.awayAlt = 0xFFFFFFFF;
        BoardBug.Look l = BoardCover.look(g, live(1_000_000L, "2", "1", 4020, "68'"), 1_010_000L);
        assertEquals("EVE", l.home);
        assertEquals("2–1", l.score);
        assertEquals("67:10", l.clock);
        assertEquals("PL", l.tag);
        assertTrue(l.live);
        // A black kit on the navy: its second colour.
        assertEquals(0xFFFFFFFF, l.awayColour);
        assertEquals("HT", BoardCover.look(g, new BoardCover.Reading(1L, "1", "1", 0, true, 2700, "HT"), 2L).clock);
        assertEquals("FT", BoardCover.look(g, new BoardCover.Reading(1L, "1", "1", 2, false, 5400, "FT"), 2L).clock);
    }

    @Test
    public void clubsAndCompetitionsInLetters() {
        assertEquals("EVE", BoardCover.code("eve", "Everton"));
        assertEquals("NOT", BoardCover.code(null, "Nott'm Forest"));
        assertEquals("CL", BoardCover.tag("CHAMPIONS LEAGUE"));
        assertEquals("BUN", BoardCover.tag("BUNDESLIGA"));
        assertEquals("", BoardCover.tag(null));
    }

    // ---- kind to the screen ----------------------------------------------------------------------------------------

    @Test
    public void itMovesRoundASmallCircleAndComesBack() {
        Set<String> places = new HashSet<>();
        for (int n = 0; n < 9; n++) {
            int[] o = BoardCover.orbit(n);
            assertTrue(Math.abs(o[0]) <= 1 && Math.abs(o[1]) <= 1);
            places.add(o[0] + "," + o[1]);
        }
        assertEquals(9, places.size());
        assertArrayEquals(BoardCover.orbit(0), BoardCover.orbit(9));
        assertArrayEquals(BoardCover.orbit(-1), BoardCover.orbit(8));
        // Each step a pixel at most each way: a slow drift, never a jump.
        for (int n = 0; n < 9; n++) {
            int[] a = BoardCover.orbit(n), b = BoardCover.orbit(n + 1);
            assertTrue(Math.abs(a[0] - b[0]) <= 1 && Math.abs(a[1] - b[1]) <= 2);
        }
    }

    @Test
    public void itDimsAfterFiveQuietMinutes() {
        assertFalse(BoardCover.dims(0, BoardCover.DIM_AFTER_MS - 1));
        assertTrue(BoardCover.dims(0, BoardCover.DIM_AFTER_MS));
        // Solid still: darker colours, never see-through.
        assertTrue(BoardCover.DIM > 0.5f && BoardCover.DIM < 1f);
    }

    @Test
    public void theWordsAreNeverPureWhite() {
        assertTrue((BoardBug.INK & 0xFFFFFF) != 0xFFFFFF);
        assertEquals(0xFF0A0D33, BoardBug.ink(0xFFFFEE00));
        assertEquals(BoardBug.INK, BoardBug.ink(0xFF003399));
    }

    @Test
    public void itShowsOverTheChannelAppsOnly() {
        assertTrue(BoardCover.isPlayer("ar.tvplayer.tv"));
        assertTrue(BoardCover.isPlayer("com.gathin.homefieldtv"));
        assertFalse(BoardCover.isPlayer("com.google.android.youtube.tv"));
    }

    @Test
    public void theHintSaysWhatTheArrowsDo() {
        assertTrue(BoardCover.hintFor(0, true).startsWith("Moving the scoreboard"));
        assertTrue(BoardCover.hintFor(1, true).contains("the logo's cover"));
        assertTrue(BoardCover.hintFor(1, false).contains("OK: move it"));
        assertTrue(BoardCover.hintFor(3, true).contains("its width"));
    }

    // ---- the places --------------------------------------------------------------------------------------------------

    @Test
    public void thePlacesAreKeptAsWritten() {
        BoardPlace p = BoardPlace.standard();
        p.moveBoard(0.1f, 0.05f, 8f * 1080 / 1920);
        BoardPlace back = BoardPlace.decode(p.encode());
        assertNotNull(back);
        assertEquals(p.bx, back.bx, 1e-4);
        assertEquals(p.by, back.by, 1e-4);
        assertEquals(p.lw, back.lw, 1e-4);
        assertNull(BoardPlace.decode("1,2,3"));
        assertNull(BoardPlace.decode("0.1,0.1,0.05,0.8,0.05,0.1,NaN"));
        assertNull(BoardPlace.decode("0.1,0.1,0.05,0.8,0.05,0.1,1.5"));
        assertNull(BoardPlace.decode(null));
    }

    @Test
    public void theStandardPlacesAreTopLeftAndTopRight() {
        BoardPlace p = BoardPlace.standard();
        assertTrue(p.bx < 0.1f && p.by < 0.1f);
        assertTrue(p.lx > 0.8f && p.ly < 0.1f);
        assertTrue(p.lx + p.lw <= 1f);
    }

    @Test
    public void theBoardStaysOnTheScreen() {
        BoardPlace p = BoardPlace.standard();
        float aspect = 9f * 1080 / 1920;
        p.moveBoard(-1, -1, aspect);
        assertEquals(0f, p.bx, 0f);
        assertEquals(0f, p.by, 0f);
        p.moveBoard(5, 5, aspect);
        assertEquals(1 - p.bh * aspect, p.bx, 1e-5);
        assertEquals(1 - p.bh, p.by, 1e-5);
        for (int i = 0; i < 100; i++) p.sizeBoard(1.04f, aspect);
        assertEquals(BoardPlace.MAX_H, p.bh, 1e-5);
        assertTrue(p.bx + p.bh * aspect <= 1f + 1e-5);
        for (int i = 0; i < 100; i++) p.sizeBoard(1 / 1.04f, aspect);
        assertEquals(BoardPlace.MIN_H, p.bh, 1e-5);
    }

    @Test
    public void theLogosCoverSizesAboutItsMiddleEachWayOnItsOwn() {
        BoardPlace p = BoardPlace.standard();
        float cx = p.lx + p.lw / 2, cy = p.ly + p.lh / 2, h = p.lh;
        p.sizeLogo(1.2f, 1f);
        assertEquals(cx, p.lx + p.lw / 2, 1e-5);
        assertEquals(cy, p.ly + p.lh / 2, 1e-5);
        assertEquals(h, p.lh, 1e-6);
        for (int i = 0; i < 100; i++) p.sizeLogo(1.04f, 1.04f);
        assertEquals(BoardPlace.MAX_W, p.lw, 1e-5);
        assertEquals(BoardPlace.MAX_LH, p.lh, 1e-5);
        assertTrue(p.lx >= 0 && p.lx + p.lw <= 1f + 1e-5 && p.ly >= 0 && p.ly + p.lh <= 1f + 1e-5);
    }

    @Test
    public void aChannelsPlacesGoByItsNameWithoutItsPictureTag() {
        assertEquals(BoardPlace.key("Sky Sports Premier League"), BoardPlace.key("UK: Sky Sports Premier League UHD"));
        assertEquals("c.", BoardPlace.key(""));
        BoardPlace a = BoardPlace.standard(), b = a.copy();
        b.bx = 0.5f;
        assertSame(a.getClass(), b.getClass());
        assertEquals(0.045f, a.bx, 0f);
    }
}
