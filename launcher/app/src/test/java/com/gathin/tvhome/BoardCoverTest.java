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
    public void addedTimeRunsOnWithTheTimePutUpBeside() {
        // 4.14: the clock runs on through the minutes added, never stopping at the period's end.
        assertArrayEquals(new String[]{"46:30", ""}, BoardCover.clockWords(2790, "45'+2'"));
        assertArrayEquals(new String[]{"93:00", ""}, BoardCover.clockWords(5580, "90'+4'"));
        assertArrayEquals(new String[]{"105:20", ""}, BoardCover.clockWords(105 * 60 + 20, "105'+1'"));
        // Beside it, the added time the fourth official put up for that half, once known; nothing before the half's end.
        int[] put = {3, 4, 0, 0};
        assertEquals("+4", BoardCover.addedWords("93:00", "90'+4'", put));
        assertEquals("+4", BoardCover.addedWords("90:05", "88'", put));
        assertEquals("+3", BoardCover.addedWords("46:30", "45'+2'", put));
        assertEquals("", BoardCover.addedWords("88:10", "88'", put));
        // 4.15: from the half's last minute (the 90th, the 45th) once it is known.
        assertEquals("+4", BoardCover.addedWords("89:20", "89'", put));
        assertEquals("+3", BoardCover.addedWords("44:05", "44'", put));
        assertEquals("", BoardCover.addedWords("43:59", "44'", put));
        assertEquals("", BoardCover.addedWords("93:00", "90'+4'", null));
        assertEquals("", BoardCover.addedWords("106:00", "105'+1'", put));
        assertEquals("", BoardCover.addedWords("HT", "HT", put));
        assertEquals(4, BoardCover.addedIn("Fourth official has announced 4 minutes of added time."));
        assertEquals(1, BoardCover.addedIn("Fourth official has announced 1 minute of added time."));
        assertEquals(0, BoardCover.addedIn("Corner, Arsenal. Conceded by Jarrad Branthwaite."));
        assertEquals(0, BoardCover.addedIn(null));
    }

    @Test
    public void theClockNeverSticksWhenEspnsStandsStillInAddedTime() {
        // ESPN's clock at 90:00 while its minute says the 93rd: from the 93rd minute's start, on from there.
        BoardCover.Reading r = live(1_000L, "2", "1", 5400, "90'+3'");
        r.since = 1_000L;
        assertArrayEquals(new String[]{"92:30", ""}, BoardCover.clock(r, 31_000L));
        // 4.15: and on, however long the next reading takes to come (it never stops at a minute's end).
        assertArrayEquals(new String[]{"94:00", ""}, BoardCover.clock(r, 121_000L));
        // Never stepping back by a little; a real correction (half a minute or more) taken as it is.
        assertEquals(5530, BoardCover.steadied(5525, 5530), 0.001);
        assertEquals(5531, BoardCover.steadied(5531, 5530), 0.001);
        assertEquals(5480, BoardCover.steadied(5480, 5530), 0.001);
        assertEquals(5525, BoardCover.steadied(5525, -1), 0.001);
        // The summary read every half a minute near a half's end, for the added time put up.
        assertTrue(BoardCover.nearHalfsEnd(44));
        assertTrue(BoardCover.nearHalfsEnd(89));
        assertTrue(BoardCover.nearHalfsEnd(96));
        assertFalse(BoardCover.nearHalfsEnd(67));
        assertFalse(BoardCover.nearHalfsEnd(-1));
        // Full time on the board under a minute after it shows, whatever is out by then.
        assertTrue(BoardCover.FT_SHOWN_MS < 60_000L);
        assertTrue(BoardCover.FT_GONE_MS <= 60_000L && BoardCover.FT_GONE_MS >= BoardCover.FT_SHOWN_MS);
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
    public void theClockCountsOnFromWhenItWasSeenAndNeverStops() {
        BoardCover.Reading r = live(1_000_000L, "1", "0", 4020, "68'");
        assertArrayEquals(new String[]{"67:00", ""}, BoardCover.clock(r, 1_000_000L));
        assertArrayEquals(new String[]{"67:25", ""}, BoardCover.clock(r, 1_025_000L));
        // 4.15: ESPN's next minute not in yet: it counts on all the same (the clock never stops).
        assertArrayEquals(new String[]{"68:30", ""}, BoardCover.clock(r, 1_090_000L));
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
        // A black kit on the navy: its second colour (white, toned down a little for the screen).
        assertEquals(BoardBug.softer(0xFFFFFFFF), l.awayColour);
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
    public void theLooksIncludeCrestsAndTimeAndStacked() {
        assertEquals(BoardBug.Style.CRESTS, BoardBug.Style.of("CRESTS"));
        assertEquals(BoardBug.Style.STACKED, BoardBug.Style.of("STACKED"));
        assertEquals(BoardBug.Style.CLASSIC, BoardBug.Style.of("nonsense"));
        assertEquals("Crests and time", BoardBug.Style.CRESTS.label);
        // Stacked is drawn taller (its clubs one above the other); the rest at the height set.
        assertTrue(BoardBug.Style.STACKED.tall > 1.5f);
        assertEquals(1f, BoardBug.Style.CRESTS.tall, 0f);
        assertEquals(1f, BoardBug.Style.CLASSIC.tall, 0f);
    }

    @Test
    public void veryLightClubColoursAreToneDownForTheScreen() {
        // White (Spurs' second colour) and a bright yellow come down a little; a red stays as it is.
        assertTrue((BoardBug.softer(0xFFFFFFFF) & 0xFF) < 0xFF);
        assertTrue(((BoardBug.softer(0xFFFFEE00) >> 8) & 0xFF) < 0xEE);
        assertEquals(0xFFDA291C, BoardBug.softer(0xFFDA291C));
        assertEquals(0xFFDA291C, BoardBug.colour(0xFFDA291C, 0));
        // Spurs' navy is too dark for the navy board: its white, toned down.
        assertEquals(BoardBug.softer(0xFFFFFFFF), BoardBug.colour(0xFF132257, 0xFFFFFFFF));
    }

    @Test
    public void itShowsOverTheChannelAppsOnly() {
        assertTrue(BoardCover.isPlayer("ar.tvplayer.tv"));
        assertTrue(BoardCover.isPlayer("com.gathin.homefieldtv"));
        assertFalse(BoardCover.isPlayer("com.google.android.youtube.tv"));
    }

    @Test
    public void theHintSaysWhatTheArrowsDo() {
        // Each of the two by its position, its size, its height, then its width; the board's timing between them.
        assertTrue(BoardCover.hintFor(BoardCover.B_POSITION, true).startsWith("Scoreboard  ·  POSITION"));
        assertTrue(BoardCover.hintFor(BoardCover.B_POSITION, true).contains("OK: its size"));
        assertTrue(BoardCover.hintFor(BoardCover.B_SIZE, true).contains("SIZE") && BoardCover.hintFor(BoardCover.B_SIZE, true).contains("its shape kept"));
        assertTrue(BoardCover.hintFor(BoardCover.B_HEIGHT, true).contains("HEIGHT") && BoardCover.hintFor(BoardCover.B_HEIGHT, true).contains("its width kept"));
        assertTrue(BoardCover.hintFor(BoardCover.B_WIDTH, true).contains("WIDTH") && BoardCover.hintFor(BoardCover.B_WIDTH, true).contains("its height kept"));
        // Timing comes after the width, then the logo's cover (or back to moving the board without one).
        assertTrue(BoardCover.hintFor(BoardCover.TIMING, true).contains("the logo's cover"));
        assertTrue(BoardCover.hintFor(BoardCover.TIMING, false).contains("OK: move it"));
        assertTrue(BoardCover.hintFor(BoardCover.TIMING, true).contains("a second later"));
        assertTrue(BoardCover.hintFor(BoardCover.L_POSITION, true).startsWith("Logo's cover  ·  POSITION"));
        assertTrue(BoardCover.hintFor(BoardCover.L_SIZE, true).contains("SIZE"));
        assertTrue(BoardCover.hintFor(BoardCover.L_HEIGHT, true).contains("HEIGHT"));
        assertTrue(BoardCover.hintFor(BoardCover.L_WIDTH, true).contains("WIDTH") && BoardCover.hintFor(BoardCover.L_WIDTH, true).contains("OK: the scoreboard"));
        // What each is now, as shares of the screen.
        BoardPlace p = BoardPlace.standard();
        assertEquals("20% wide, 5% high", BoardCover.sizeWords(BoardCover.B_SIZE, p, 0.2f));
        assertEquals("5% high", BoardCover.sizeWords(BoardCover.B_HEIGHT, p, 0.2f));
        assertEquals("20% wide", BoardCover.sizeWords(BoardCover.B_WIDTH, p, 0.2f));
        assertEquals("10% wide, 7% high", BoardCover.sizeWords(BoardCover.L_SIZE, p, 0.2f));
        assertEquals("10% wide", BoardCover.sizeWords(BoardCover.L_WIDTH, p, 0.2f));
        assertEquals("5% across, 5% down", BoardCover.sizeWords(BoardCover.B_POSITION, p, 0.2f));
    }

    @Test
    public void theLogosSizeKeepsItsShape() {
        BoardPlace p = BoardPlace.standard();
        float ratio = p.lw / p.lh;
        p.scaleLogo(1.2f);
        assertEquals(ratio, p.lw / p.lh, 1e-4f);
        assertEquals(0.12f, p.lw, 1e-4f);
        // Its middle stays where it was.
        assertEquals(0.855f + 0.05f, p.lx + p.lw / 2, 1e-4f);
        // As big as it may go, still its shape.
        for (int i = 0; i < 100; i++) p.scaleLogo(1.04f);
        assertEquals(ratio, p.lw / p.lh, 1e-3f);
        assertTrue(p.lw <= BoardPlace.MAX_W + 1e-4f && p.lh <= BoardPlace.MAX_LH + 1e-4f);
        assertTrue(p.lx + p.lw <= 1f + 1e-4f);
    }

    @Test
    public void theScoreboardsWidthIsSetApartFromItsHeight() {
        BoardPlace p = BoardPlace.standard();
        float h = p.bh;
        p.widthBoard(1.5f, 0.2f);
        assertEquals(0.3f, p.bw, 1e-4f);
        assertEquals(h, p.bh, 1e-6f);
        for (int i = 0; i < 200; i++) p.widthBoard(1.04f, 0f);
        assertEquals(BoardPlace.MAX_BW, p.bw, 1e-4f);
        // Kept on the screen as it grows.
        assertTrue(p.bx + p.bw <= 1f + 1e-4f);
        for (int i = 0; i < 300; i++) p.widthBoard(1 / 1.04f, 0f);
        assertTrue(p.bw >= BoardPlace.MIN_BW);
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

    // ---- 4.08: the phone's scoreboard on the TV ----------------------------------------------------------------------

    @Test
    public void theLooksAreThePhonesEight() {
        assertEquals(9, BoardBug.Style.values().length);
        // 4.31: Frosted glass, crests and time only.
        assertEquals(BoardBug.Style.FROSTED, BoardBug.Style.of("FROSTED"));
        // 4.13: Card, the results card, a little taller than the others.
        assertEquals(BoardBug.Style.CARD, BoardBug.Style.of("CARD"));
        assertEquals("Card", BoardBug.Style.CARD.label);
        assertTrue(BoardBug.Style.CARD.tall > 1.2f && BoardBug.Style.CARD.tall < BoardBug.Style.STACKED.tall);
        assertEquals(BoardBug.Style.GLASS, BoardBug.Style.of("GLASS"));
        assertEquals(BoardBug.Style.LIGHT, BoardBug.Style.of("LIGHT"));
        assertEquals("Club colours", BoardBug.Style.of("CLUB").label);
        // The looks kept from before still read as they were.
        assertEquals(BoardBug.Style.MINIMAL, BoardBug.Style.of("MINIMAL"));
        assertEquals(BoardBug.Style.STACKED, BoardBug.Style.of("STACKED"));
    }

    @Test
    public void itComesInAsABroadcastersDoes() {
        float w = 400, h = 40, cap = 40;
        assertEquals(0f, BoardBug.shownPart(0f, w, h, cap)[2], 0.01f);
        float[] opening = BoardBug.shownPart(0.1f, w, h, cap);
        assertEquals(cap, opening[2], 0.01f);
        assertTrue(opening[1] > 0 && opening[3] < h);
        float[] block = BoardBug.shownPart(BoardBug.OPEN_AT, w, h, cap);
        assertEquals(0f, block[1], 0.01f);
        assertEquals(cap, block[2], 0.01f);
        assertEquals(w, BoardBug.shownPart(1f, w, h, cap)[2], 0.01f);
        float last = -1;
        for (int i = 0; i <= 20; i++) {
            float[] p = BoardBug.shownPart(i / 20f, w, h, cap);
            float area = (p[2] - p[0]) * (p[3] - p[1]);
            assertTrue(area >= last);
            last = area;
        }
    }

    @Test
    public void aGoalSweepsAcrossFromTheScorersSideAndOnOut() {
        float[] before = BoardBug.goalBand(BoardBug.BAND_IN - 1, true);
        assertEquals(0f, before[1] - before[0], 0f);
        float[] coming = BoardBug.goalBand(BoardBug.BAND_IN + BoardBug.BAND_MS / 3, true);
        assertEquals(0f, coming[0], 0.001f);
        assertTrue(coming[1] > 0f && coming[1] < 1f);
        float[] held = BoardBug.goalBand(2000, true);
        assertEquals(0f, held[0], 0.001f);
        assertEquals(1f, held[1], 0.001f);
        float[] going = BoardBug.goalBand(BoardBug.BAND_OUT + BoardBug.BAND_MS / 3, true);
        assertTrue(going[0] > 0f && going[0] < 1f);
        // The away side's from the right, out to the left.
        float[] away = BoardBug.goalBand(BoardBug.BAND_IN + BoardBug.BAND_MS / 3, false);
        assertEquals(1f, away[1], 0.001f);
        assertTrue(away[0] > 0f && away[0] < 1f);
        float[] gone = BoardBug.goalBand(BoardBug.BAND_OUT + BoardBug.BAND_MS, false);
        assertEquals(0f, gone[1] - gone[0], 0f);
        // Then the new score's glow, 1 down to 0, the moment over at GOAL_MS.
        long from = BoardBug.BAND_OUT + BoardBug.BAND_MS;
        assertEquals(0f, BoardBug.glow(from - 1), 0f);
        assertEquals(1f, BoardBug.glow(from), 0.001f);
        assertEquals(0f, BoardBug.glow(BoardBug.GOAL_MS), 0f);
        assertEquals(BoardBug.GOAL_MS, from + BoardBug.GLOW_MS);
    }

    @Test
    public void aClubsColourOnALitSideStaysReadable() {
        // White over navy at full glow: near white, so its letters go dark.
        int lit = BoardBug.blend(0xFF0B1640, 0xFFFFFFFF, 200 / 255f);
        assertEquals(0xFF0A0D33, BoardBug.ink(lit));
        assertEquals(0xFF0B1640, BoardBug.blend(0xFF0B1640, 0xFFFFFFFF, 0f));
    }

    @Test
    public void theClockSetAtKickOffCountsFromThatMomentInItsHalf() {
        long at = 1_000_000L;
        long[] a = BoardCover.anchorOf("g1|0|" + at, "g1");
        assertNotNull(a);
        assertNull(BoardCover.anchorOf("g1|0|" + at, "g2"));
        assertNull(BoardCover.anchorOf("rubbish", "g1"));
        assertNull(BoardCover.anchorOf(null, "g1"));
        // 23 min 7 s after the OK at kick-off: 23:07, whatever ESPN's minute says.
        assertArrayEquals(new String[]{"23:07", ""}, BoardCover.anchored(a, at + (23 * 60 + 7) * 1000L, "22'"));
        // Into added time: running on.
        assertArrayEquals(new String[]{"46:00", ""}, BoardCover.anchored(a, at + (46 * 60) * 1000L, "45'+2'"));
        // The second half on the screen: the first half's moment no longer counts.
        assertNull(BoardCover.anchored(a, at + 3_600_000L, "47'"));
        // Set at the second half's kick-off: from 45:00.
        long[] b = BoardCover.anchorOf("g1|2700|" + at, "g1");
        assertArrayEquals(new String[]{"46:30", ""}, BoardCover.anchored(b, at + 90_000L, "47'"));
        assertNull(BoardCover.anchored(b, at - 1, "47'"));
        assertEquals(1, BoardCover.periodOfSecs(0));
        assertEquals(2, BoardCover.periodOfSecs(2700));
        assertEquals(3, BoardCover.periodOfSecs(5400));
    }

    @Test
    public void beforeKickOffItShowsTheKickOffsTime() {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.home = "LEE";
        g.away = "TOT";
        g.kickoff = java.time.ZonedDateTime.of(2026, 11, 7, 15, 0, 0, 0, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        BoardBug.Look l = BoardCover.preLook(g);
        assertEquals("v", l.score);
        assertEquals("15:00", l.clock);
        assertFalse(l.live);
    }

    private static ScoreCard.Data card(String kind, String label, String small) {
        ScoreCard.Data d = new ScoreCard.Data();
        d.kind = kind;
        d.label = label;
        d.labelSmall = small;
        d.gameId = "g1";
        d.homeName = "Leeds";
        d.awayName = "Tottenham";
        d.score = "2–1";
        return d;
    }

    @Test
    public void aGoalsCardFoldsOutWithItsScorerAndSide() {
        ScoreCard.Data d = card("goal", "GOAL", null);
        d.first = "Dominic";
        d.last = "Calvert-Lewin";
        d.line = "67'";
        d.scoreBefore = "1–1";
        BoardDrop.Drop o = BoardCover.dropOf(d, 0xFF1D428A, 0xFFC9CEDD);
        assertEquals("GOAL", o.chip);
        assertEquals("Leeds 2–1 Tottenham", o.title);
        assertEquals("Dominic Calvert-Lewin 67'", o.line);
        assertEquals(1, o.side);
        assertEquals(BoardCover.kindColour("goal"), o.colour);
        assertEquals("g1", o.gameId);
        // 4.14: a goal is captioned once, by its game and new score.
        assertEquals("g1|2–1", o.key);
        // The card's pictures go with it (none here).
        assertNull(o.photo);
        assertNull(o.homeCrest);
        // Its away side's goal: the stripe in their colour.
        d.scoreBefore = "2–0";
        assertEquals(2, BoardCover.dropOf(d, 1, 2).side);
    }

    @Test
    public void fullTimeAndKickOffFoldOutInTheirOwnWords() {
        ScoreCard.Data ft = card("ft", "FULL", "TIME");
        ft.last = "Leeds win";
        ft.first = "Calvert-Lewin 12' · Son 38'";
        ft.line = "";
        BoardDrop.Drop o = BoardCover.dropOf(ft, 1, 2);
        assertEquals("FULL TIME", o.chip);
        assertEquals("Leeds win · Calvert-Lewin 12' · Son 38'", o.line);
        assertEquals(0, o.side);
        assertEquals(BoardDrop.PLAIN, o.colour);
        ScoreCard.Data ko = card("kickoff", "KICK-OFF", "IN 15 MIN");
        ko.first = "Premier League";
        ko.line = "Elland Road · Kick-off 15:00";
        BoardDrop.Drop k = BoardCover.dropOf(ko, 1, 2);
        assertEquals("KICK-OFF IN 15 MIN", k.chip);
        assertEquals("Leeds v Tottenham", k.title);
        assertEquals("Premier League · Elland Road · Kick-off 15:00", k.line);
    }

    @Test
    public void aPopUpsLineFoldsOutWithoutItsIcon() {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "g1";
        g.homeName = "Leeds";
        g.awayName = "Tottenham";
        g.homeScore = "2";
        g.awayScore = "1";
        BoardDrop.Drop o = BoardCover.dropOfLine("⚽  Calvert-Lewin  67'", g, "goal", "home");
        assertEquals("GOAL", o.chip);
        assertEquals("Leeds 2–1 Tottenham", o.title);
        assertEquals("Calvert-Lewin 67'", o.line);
        assertEquals(1, o.side);
        assertEquals("RED CARD", BoardCover.chipOf("red"));
        assertEquals("FULL TIME", BoardCover.chipOf("fulltime"));
    }

    @Test
    public void theLineUpsFoldOutWithBothElevens() {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "g1";
        g.homeName = "Leeds";
        g.awayName = "Tottenham";
        ScoresTicker.Side home = new ScoresTicker.Side(), away = new ScoresTicker.Side();
        home.formation = "4-3-3";
        home.players.add(new String[]{"Meslier"});
        home.players.add(new String[]{"Bogle"});
        away.players.add(new String[]{"Vicario"});
        BoardDrop.Drop o = BoardCover.lineupsDrop(g, new ScoresTicker.Side[]{home, away});
        assertTrue(o.lineups);
        assertEquals("LINE-UPS", o.chip);
        assertTrue(o.title.startsWith("Leeds v Tottenham"));
        assertEquals("Leeds (4-3-3): Meslier, Bogle", o.line);
        assertEquals("Tottenham: Vicario", o.line2);
    }

    @Test
    public void theStatsAreTheBroadcastersInTheirOrder() {
        java.util.Map<String, String> h = new java.util.HashMap<>(), a = new java.util.HashMap<>();
        h.put("possessionPct", "58.3");
        a.put("possessionPct", "41.7");
        h.put("totalShots", "9");
        a.put("totalShots", "4");
        h.put("wonCorners", "5");
        a.put("wonCorners", "2");
        // A stat one side lacks is left out.
        h.put("saves", "2");
        java.util.List<String[]> s = BoardCover.statsList(h, a);
        assertEquals(3, s.size());
        assertArrayEquals(new String[]{"Possession", "58%", "42%"}, s.get(0));
        assertArrayEquals(new String[]{"Shots", "9", "4"}, s.get(1));
        assertEquals(0.58f, BoardStats.share("58%", "42%"), 0.001f);
        assertEquals(0.5f, BoardStats.share("0", "0"), 0.001f);
    }

    @Test
    public void clubColoursStayReadableOnTheStrips() {
        int navy = BoardDrop.legible(0xFF132257, false);
        assertTrue(((navy >> 8) & 0xFF) > 0x80);
        int white = BoardDrop.legible(0xFFFFFFFF, true);
        assertTrue((white & 0xFF) < 0x90);
        assertEquals(0xFFE5333B, BoardDrop.legible(0xFFE5333B, false));
        assertEquals(0xFFE5333B, BoardStats.visible(0xFFE5333B));
        assertTrue(BoardStats.visible(0xFF000000) != 0xFF000000);
    }

    @Test
    public void theBoardsWidthCanBeKeptApartFromItsHeight() {
        BoardPlace p = BoardPlace.standard();
        assertEquals(0f, p.bw, 0f);
        float h = p.bh;
        p.heightBoard(1.2f, 0.25f);
        assertEquals(0.25f, p.bw, 1e-5);
        assertEquals(h * 1.2f, p.bh, 1e-5);
        // Its size step scales both.
        p.sizeBoard(1.04f, 1f);
        assertEquals(0.25f * 1.04f, p.bw, 1e-4);
        // Kept as eight numbers; seven (before the width was kept) still read, with no width of its own.
        BoardPlace back = BoardPlace.decode(p.encode());
        assertNotNull(back);
        assertEquals(p.bw, back.bw, 1e-4);
        BoardPlace old = BoardPlace.decode("0.045,0.045,0.045,0.855,0.045,0.1,0.065");
        assertNotNull(old);
        assertEquals(0f, old.bw, 0f);
        assertNull(BoardPlace.decode("0.1,0.1,0.05,0.8,0.05,0.1,0.06,0.2,0.3"));
    }

    @Test
    public void partsAreJoinedWithoutGaps() {
        assertEquals("a · b", BoardCover.join("a", "", null, " b "));
        assertEquals("", BoardCover.join());
    }

    @Test
    public void aGeneralChannelDropsTheSportFromTheLogo() {
        BoardCover.MenuState m = new BoardCover.MenuState();
        m.logoLook = "Whitebrook Road Sports";
        m.sportsLook = true;
        m.what = "premier league";
        m.nameOnly = true;
        String[] on = BoardCover.menuLines(m);
        assertTrue(on[7].startsWith("Channel:") && on[7].contains("General") && !on[7].contains("Sports"));
        assertTrue(on[6].contains("on a sports channel only"));
        m.nameOnly = false;
        on = BoardCover.menuLines(m);
        assertTrue(on[7].contains("Sports · Whitebrook Road Sports"));
        assertEquals("", SportsMark.onFor(false, "premier league"));
        assertEquals("", SportsMark.onFor(false, ""));
        assertEquals("premier league", SportsMark.onFor(true, "premier league"));
        assertEquals("football", SportsMark.onFor(true, ""));
    }

    @Test
    public void theMenuOverTheMatchSaysWhatEachLineWillDo() {
        BoardCover.MenuState m = new BoardCover.MenuState();
        m.boardShown = true;
        m.logoOn = true;
        m.look = "Classic";
        m.logoLook = "Whitebrook Road Sports";
        m.sportsLook = true;
        m.what = "Auto, from the game (premier league)";
        m.tag = "Auto, from the channel";
        m.find = true;
        m.lineups = true;
        m.active = true;
        m.manual = true;
        m.showing = "Everton v Spurs";
        String[] on = BoardCover.menuLines(m);
        assertEquals(12, on.length);
        assertTrue(on[0].startsWith("Up by hand:") && on[0].contains("On · Everton v Spurs") && on[0].contains("OK: down"));
        assertTrue(on[1].startsWith("Move and resize") && on[1].contains("position, size, height, width"));
        assertTrue(on[2].startsWith("Scoreboard:") && on[2].contains("On"));
        assertTrue(on[3].startsWith("Logo's cover:") && on[3].contains("On"));
        assertTrue(on[4].contains("Classic"));
        assertTrue(on[5].contains("Whitebrook Road Sports"));
        assertTrue(on[6].contains("premier league") && !on[6].contains("looks)"));
        assertTrue(on[7].contains("Whitebrook Road Sports"));
        assertTrue(on[8].startsWith("Picture tag") && on[8].contains("Auto"));
        assertTrue(on[9].startsWith("See-through") && on[9].contains("Off"));
        assertTrue(on[10].startsWith("Find the channel's scoreboard") && on[10].contains("look again now"));
        assertTrue(on[11].startsWith("Line-ups on screen") && on[11].contains("On"));
        m.manual = false;
        assertTrue(BoardCover.menuLines(m)[0].contains("On by itself · Everton v Spurs, off after full time"));
        m.boardShown = false;
        m.logoOn = false;
        m.nameOnly = true;
        m.seeThrough = true;
        m.lineups = false;
        m.active = false;
        m.sportsLook = false;
        String[] off = BoardCover.menuLines(m);
        assertTrue(off[0].contains("‹  Off  ›") && off[0].contains("OK: the scoreboard and logo up now"));
        assertTrue(off[2].contains("Off"));
        assertTrue(off[3].contains("Off"));
        assertTrue(off[6].contains("(on a sports channel only)"));
        assertTrue(off[7].contains("‹  General · Whitebrook Road  ›"));
        m.nameOnly = false;
        assertTrue(BoardCover.menuLines(m)[6].contains("(Whitebrook Road Sports looks)"));
        assertTrue(off[9].contains("On"));
        assertTrue(off[11].contains("Off"));
    }

    @Test
    public void byHandStepsFromOffToTheGameOnTodaysGamesAndTheLogoOnly() {
        java.util.List<String> ids = java.util.Arrays.asList("g1", "g2");
        assertEquals("", BoardCover.nextHand(null, ids, 1));
        assertEquals("g1", BoardCover.nextHand("", ids, 1));
        assertEquals("g2", BoardCover.nextHand("g1", ids, 1));
        assertEquals(BoardCover.NO_GAME, BoardCover.nextHand("g2", ids, 1));
        assertNull(BoardCover.nextHand(BoardCover.NO_GAME, ids, 1));
        assertEquals(BoardCover.NO_GAME, BoardCover.nextHand(null, ids, -1));
        assertEquals("g2", BoardCover.nextHand(BoardCover.NO_GAME, ids, -1));
        // a game no longer among today's: on from the game on the channel
        assertEquals("g1", BoardCover.nextHand("gone", ids, 1));
        // no games today: off, the game on the channel, the logo only
        assertEquals(BoardCover.NO_GAME, BoardCover.nextHand("", java.util.Collections.emptyList(), 1));
    }

    @Test
    public void theBroadcastLogosTagIsTheChannelsPicture() {
        assertEquals("HD", LogoMark.tagOf("UK: Sky Sports Main Event HD"));
        assertEquals("UHD", LogoMark.tagOf("UK: Sky Sports Premier League UHD"));
        assertEquals("4K", LogoMark.tagOf("TNT Sports 1 4K"));
        assertEquals("HD", LogoMark.tagOf("BBC One FHD"));
        assertEquals("", LogoMark.tagOf("Sky Sports Football"));
        assertEquals("", LogoMark.tagOf(null));
        // "HD" inside a word is no tag.
        assertEquals("", LogoMark.tagOf("Shdtv Sports"));
        assertTrue(LogoMark.Look.BROADCAST.broadcast());
        assertTrue(LogoMark.Look.BROADCAST_BOARD.broadcast());
        assertFalse(LogoMark.Look.GLASS.broadcast());
        assertEquals(LogoMark.Look.BROADCAST_BOARD, LogoMark.Look.of("BROADCAST_BOARD"));
    }

    // ---- 4.19: Whitebrook Road's own channel looks, the picture tag ---------------------------------------------------

    @Test
    public void thePictureTagReadsUhd4kAndHdrFromTheChannel() {
        assertEquals("UHD HDR", ChannelMark.tagOf("UK: Sky Sports Main Event UHD HDR"));
        assertEquals("4K HDR", ChannelMark.tagOf("TNT Sports 1 4K HDR"));
        assertEquals("4K HDR", ChannelMark.tagOf("Sports 4K Dolby Vision"));
        assertEquals("4K", ChannelMark.tagOf("DAZN 1 2160p"));
        assertEquals("UHD", ChannelMark.tagOf("Sky Sports F1 UHD"));
        assertEquals("HD", ChannelMark.tagOf("Sky Sports F1 1080p"));
        // HDR on an HD channel, or alone, is no picture tag of its own.
        assertEquals("HD", ChannelMark.tagOf("Sports HD HDR"));
        assertEquals("", ChannelMark.tagOf("Sports HDR"));
        assertEquals("", ChannelMark.tagOf(""));
    }

    @Test
    public void thePictureTagChosenWinsOverTheChannelsName() {
        assertEquals("UHD", ChannelMark.Tag.AUTO.on("Sky Sports Football UHD"));
        assertEquals("", ChannelMark.Tag.NONE.on("Sky Sports Football UHD"));
        assertEquals("4K HDR", ChannelMark.Tag.K4_HDR.on("Sky Sports Football"));
        assertEquals("HD", ChannelMark.Tag.HD.on(null));
        assertEquals(ChannelMark.Tag.AUTO, ChannelMark.Tag.of(""));
        assertEquals(ChannelMark.Tag.UHD_HDR, ChannelMark.Tag.of("UHD_HDR"));
        assertEquals(7, ChannelMark.Tag.values().length);
    }

    @Test
    public void theChannelLooksAreTheirOwn() {
        int channel = 0;
        for (LogoMark.Look l : LogoMark.Look.values()) {
            if (l.channel()) channel++;
            assertFalse(l.channel() && l.broadcast());
            assertEquals(l, LogoMark.Look.of(l.name()));
        }
        assertEquals(10, channel);
        assertTrue(LogoMark.Look.FROSTED.channel());
        assertTrue(LogoMark.Look.SIGNATURE.channel());
        assertTrue(LogoMark.Look.SMOKED.channel());
        assertFalse(LogoMark.Look.GLASS.channel());
        // An old choice still reads as it was; nothing chosen is Glass, as before.
        assertEquals(LogoMark.Look.GLASS, LogoMark.Look.of(""));
        assertEquals("GRAND PRIX", ChannelMark.channelFor("f1"));
        assertEquals("MATCHDAY", ChannelMark.channelFor(""));
        // The competition's own logo is a look of its own, neither a channel look nor Broadcast.
        assertEquals(LogoMark.Look.COMPETITION, LogoMark.Look.of("COMPETITION"));
        assertFalse(LogoMark.Look.COMPETITION.channel());
        assertFalse(LogoMark.Look.COMPETITION.broadcast());
    }

    // ---- Event: the name and "sports" over the channel's own name ---------------------------------------------------

    @Test
    public void eventIsAChannelLookDrawnWholeInTwoInks() {
        for (LogoMark.Look l : new LogoMark.Look[]{LogoMark.Look.EVENT_NAVY, LogoMark.Look.EVENT_WHITE}) {
            assertTrue(l.event());
            // In the channel looks' picker, drawn whole (no LIVE foot, no block beside), see-through when chosen.
            assertTrue(l.channel());
            assertTrue(l.own());
            assertFalse(l.sports());
            assertFalse(l.broadcast());
            assertEquals(l, LogoMark.Look.of(l.name()));
        }
        assertEquals("Event, navy", LogoMark.Look.EVENT_NAVY.label);
        assertEquals("Event, white", LogoMark.Look.EVENT_WHITE.label);
        assertFalse(LogoMark.Look.SMOKED.event());
        assertFalse(LogoMark.Look.SMOKED.own());
        assertTrue(LogoMark.Look.STYLES.own());
        // Never the default.
        assertEquals(LogoMark.Look.GLASS, LogoMark.Look.of(""));
        assertEquals(LogoMark.Look.GLASS, LogoMark.Look.of(null));
    }

    @Test
    public void eventsNameIsTheChannelsOwnWithoutTheBroadcaster() {
        assertEquals("main event", ChannelMark.subOf("UK: Sky Sports Main Event HD"));
        assertEquals("main event", ChannelMark.subOf("Sky Sports Main Event UHD HDR"));
        assertEquals("premier league", ChannelMark.subOf("Sky Sports Premier League"));
        assertEquals("premier league", ChannelMark.subOf("|UK| SKY SPORTS PREMIER LEAGUE FHD 50FPS"));
        assertEquals("f1", ChannelMark.subOf("Sky Sports F1 UHD"));
        assertEquals("f1", ChannelMark.subOf("Sky Sports F1 \u1D34\u1D30"));
        assertEquals("football", ChannelMark.subOf("Sky Sports Football"));
        assertEquals("main event", ChannelMark.subOf("Sky Sports Main Event (Backup) [Multi-Audio]"));
        assertEquals("box office", ChannelMark.subOf("TNT Sports Box Office 1080p"));
        assertEquals("ultimate", ChannelMark.subOf("TNT Sports Ultimate 4K"));
        assertEquals("bundesliga", ChannelMark.subOf("Sky Sport Bundesliga HD"));
        // A number alone is the family's "sports 1".
        assertEquals("sports 1", ChannelMark.subOf("TNT Sports 1"));
        assertEquals("sports 2", ChannelMark.subOf("BT Sport 2 HD"));
        assertEquals("sports 1", ChannelMark.subOf("Premier Sports 1 FHD (UK)"));
        assertEquals("sports 2", ChannelMark.subOf("Eurosport2"));
        assertEquals("sports 3", ChannelMark.subOf("beIN SPORTS 3"));
        assertEquals("sports 1", ChannelMark.subOf("DAZN 1"));
        // "premier" goes with Premier Sports, not with the Premier League.
        assertEquals("premier league", ChannelMark.subOf("Premier Sports Premier League"));
        // Nothing left: no name.
        assertEquals("", ChannelMark.subOf("ESPN"));
        assertEquals("", ChannelMark.subOf("Sky Sports+"));
        assertEquals("", ChannelMark.subOf("Amazon Prime Video HD"));
        // A channel of no sports broadcaster known here has none, so no other broadcaster's name ever shows.
        assertEquals("", ChannelMark.subOf("BBC One HD"));
        assertEquals("", ChannelMark.subOf("Sky Atlantic"));
        assertEquals("", ChannelMark.subOf("Racing TV"));
        assertEquals("", ChannelMark.subOf(""));
        assertEquals("", ChannelMark.subOf(null));
        // Never a broadcaster's word in what is left.
        for (String ch : new String[]{"Sky Sports Main Event", "TNT Sports 3 HD", "SkySports Arena", "Viaplay Sports 1",
                "Setanta Sports Ireland", "NBC Sports Gold", "Fox Sports Racing"}) {
            for (String word : ChannelMark.subOf(ch).split(" ")) {
                assertFalse(ch, java.util.Arrays.asList("sky", "tnt", "bt", "viaplay", "setanta", "nbc", "fox", "skysports").contains(word));
            }
        }
    }

    // ---- 4.13: out sideways, a few stats moments a game ----------------------------------------------------------------

    @Test
    public void theStatsComeAFewTimesAGame() {
        // Before kick-off: in its last ten minutes (not its last one).
        assertEquals("pre", BoardCover.statsSlot(true, 8 * 60_000L, 1, false, -1));
        assertNull(BoardCover.statsSlot(true, 15 * 60_000L, 1, false, -1));
        assertNull(BoardCover.statsSlot(true, 30_000L, 1, false, -1));
        // The first half's middle, half time, the second half's middle, full time; nothing in between.
        assertNull(BoardCover.statsSlot(false, 0, 0, false, 10));
        assertEquals("h1", BoardCover.statsSlot(false, 0, 0, false, 25));
        assertEquals("h1", BoardCover.statsSlot(false, 0, 0, false, 41));
        assertNull(BoardCover.statsSlot(false, 0, 0, false, 50));
        assertEquals("ht", BoardCover.statsSlot(false, 0, 0, true, 45));
        assertEquals("h2", BoardCover.statsSlot(false, 0, 0, false, 70));
        assertNull(BoardCover.statsSlot(false, 0, 0, false, 88));
        assertEquals("ft", BoardCover.statsSlot(false, 0, 2, false, -1));
        assertEquals(67, BoardCover.minuteOfClock("67:23"));
        assertEquals(-1, BoardCover.minuteOfClock("HT"));
        assertEquals(-1, BoardCover.minuteOfClock(null));
    }

    @Test
    public void eachMomentShowsItsOwnFewStats() {
        java.util.Map<String, String[]> by = new java.util.HashMap<>();
        by.put("Possession", new String[]{"Possession", "58%", "42%"});
        by.put("Shots", new String[]{"Shots", "9", "4"});
        by.put("Corners", new String[]{"Corners", "5", "2"});
        by.put("Form", new String[]{"Form", "WWDLW", "LDWWL", "form"});
        // The first half: possession and shots; corners make up the third when on target is not there.
        java.util.List<String[]> h1 = BoardCover.statsFor("h1", by);
        assertEquals(3, h1.size());
        assertEquals("Possession", h1.get(0)[0]);
        assertEquals("Corners", h1.get(2)[0]);
        // The second half: the player of the match so far once there is one.
        by.put("Player of the match", new String[]{"Player of the match", "Saka (ARS) · 2 goals", "", "one"});
        assertEquals("Player of the match", BoardCover.statsFor("h2", by).get(1)[0]);
        // Before kick-off one fact is enough; in the game two at least.
        assertEquals(1, BoardCover.statsFor("pre", by).size());
        java.util.Map<String, String[]> one = new java.util.HashMap<>();
        one.put("Possession", by.get("Possession"));
        assertTrue(BoardCover.statsFor("h1", one).isEmpty());
        assertTrue(BoardCover.statsFor("nonsense", by).isEmpty());
        assertTrue(BoardCover.statsFor("ht", by).size() <= 5);
    }

    @Test
    public void passAccuracyAndPlacesReadAsBroadcastersSayThem() {
        java.util.Map<String, String> h = new java.util.HashMap<>(), a = new java.util.HashMap<>();
        h.put("accuratePasses", "420");
        h.put("totalPasses", "500");
        a.put("accuratePasses", "300");
        a.put("totalPasses", "400");
        java.util.List<String[]> s = BoardCover.statsList(h, a);
        assertArrayEquals(new String[]{"Pass accuracy", "84%", "75%"}, s.get(s.size() - 1));
        assertEquals("", BoardCover.passes(new java.util.HashMap<>()));
        assertEquals("1st", BoardCover.ordinal(1));
        assertEquals("2nd", BoardCover.ordinal(2));
        assertEquals("3rd", BoardCover.ordinal(3));
        assertEquals("11th", BoardCover.ordinal(11));
        assertEquals("12th", BoardCover.ordinal(12));
        assertEquals("21st", BoardCover.ordinal(21));
        // Each stat four seconds, sliding out and back in around them.
        assertEquals(2 * BoardSide.SLIDE_MS + 3 * BoardStats.EACH_MS + 2 * 300L, BoardStats.showFor(3));
    }

    @Test
    public void whatSlidesOutKeepsRoomForItsEnds() {
        BoardBug.StripLook s = new BoardBug.StripLook();
        float square = BoardSide.ends(s, 48f, 52f, 7, true);
        // Its pads, the tucked end and the colour at its far end.
        assertEquals(2 * 48f * 0.3f + 7 + BoardSide.stripeW(48f), square, 0.01f);
        // Beside a pill it stands apart: no tucked end, rounder pads.
        s.apart = true;
        assertEquals(2 * 52f * 0.42f + BoardSide.stripeW(48f), BoardSide.ends(s, 48f, 52f, 7, true), 0.01f);
    }

    @Test
    public void theBroadcastLogoSaysTheFamilysName() {
        assertEquals("Whitebrook Road", LogoMark.NAME);
        assertEquals("sports", LogoMark.WORD);
    }

    // ---- 4.14: the scorer's caption -------------------------------------------------------------------------------------

    @Test
    public void theBoardFindsTheScorerOfTheGoalItShows() {
        java.util.List<String[]> goals = new java.util.ArrayList<>();
        goals.add(new String[]{"a", "Bukayo Saka", "23'", "home", ""});
        goals.add(new String[]{"b", "Beto", "30'", "away", ""});
        goals.add(new String[]{"c", "Kai Havertz", "41'", "home", "pen"});
        assertEquals(2, BoardCover.goalsOf("2–1", 1));
        assertEquals(1, BoardCover.goalsOf("2–1", 2));
        assertEquals(-1, BoardCover.goalsOf("v", 1));
        assertEquals("Kai Havertz", BoardCover.scorerOf(goals, "home", 2)[1]);
        assertEquals("Beto", BoardCover.scorerOf(goals, "away", 1)[1]);
        // The scores not caught up yet: none.
        assertNull(BoardCover.scorerOf(goals, "away", 2));
        assertNull(BoardCover.scorerOf(null, "home", 1));
        assertEquals("Kai Havertz 41' · penalty", BoardCover.scorerLine(goals.get(2)));
        assertEquals("Bukayo Saka 23'", BoardCover.scorerLine(goals.get(0)));
        assertEquals("", BoardCover.scorerLine(null));
    }

    // ---- 4.16: the overlays button, places by broadcaster ---------------------------------------------------------------

    @Test
    public void aChannelsBroadcasterIsKnownByItsName() {
        assertEquals("sky", BoardPlace.broadcaster("UK: Sky Sports Main Event HD"));
        assertEquals("sky", BoardPlace.broadcaster("Sky Sports F1 UHD"));
        assertEquals("tnt", BoardPlace.broadcaster("TNT Sports 1 HD"));
        assertEquals("tnt", BoardPlace.broadcaster("BT Sport 2"));
        assertEquals("prime", BoardPlace.broadcaster("Amazon Prime Video PL 1"));
        assertEquals("", BoardPlace.broadcaster("BBC One HD"));
        assertEquals("", BoardPlace.broadcaster(null));
        // Sky's own places: the score bar top left, its logo (with LIVE under it) top right, within the screen.
        BoardPlace sky = BoardPlace.preset("sky");
        assertNotNull(sky);
        assertTrue(sky.bx < 0.1f && sky.by < 0.1f);
        assertTrue(sky.lx > 0.8f && sky.lx + sky.lw <= 1f && sky.ly + sky.lh < 0.2f);
        assertNotNull(BoardPlace.preset("tnt"));
        assertNull(BoardPlace.preset(""));
    }

    @Test
    public void thePlayersBannerNamesTheChannelWatched() {
        java.util.Map<String, java.util.List<String>> on = new java.util.HashMap<>();
        on.put("game:1", java.util.Arrays.asList("Sky Sports Main Event", "Sky Sports Premier League"));
        on.put("game:2", java.util.Arrays.asList("TNT Sports 1"));
        // The banner as the channel changes: the channel, its programme, its times.
        String[] hit = OverlayPrompt.among(java.util.Arrays.asList("UK: Sky Sports Main Event HD", "Arsenal v Everton", "17:30 - 19:30"), on, on);
        assertNotNull(hit);
        assertEquals("game:1", hit[0]);
        assertEquals("UK: Sky Sports Main Event HD", hit[1]);
        // A guide or a list naming both: nothing to say.
        assertNull(OverlayPrompt.among(java.util.Arrays.asList("Sky Sports Premier League HD", "TNT Sports 1 HD"), on, on));
        assertNull(OverlayPrompt.among(java.util.Arrays.asList("BBC One HD", "Pointless"), on, on));
        // Asked once a game, F1 once a day.
        assertEquals("g|401", OverlayPrompt.askKey("football", gameWithId("401"), 0L));
        assertTrue(OverlayPrompt.askKey("f1", null, 1_762_500_000_000L).startsWith("f1|2025"));
    }

    private static ScoresTicker.Game gameWithId(String id) {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = id;
        return g;
    }

    // ---- 4.17: Card in red, moving; one stat at a time --------------------------------------------------------------------

    @Test
    public void cardsRedMovesSlowlyAndItsShinePassesNowAndThen() {
        assertEquals(0f, BoardBug.drift(0), 0.0001f);
        assertEquals(0.5f, BoardBug.drift(BoardBug.SLANT_MS / 2), 0.0001f);
        // Round again, seamlessly.
        assertEquals(BoardBug.drift(250), BoardBug.drift(BoardBug.SLANT_MS + 250), 0.0001f);
        // The shine crosses in SHINE_MS, then rests until the next.
        assertTrue(BoardBug.shineAt(0) >= 0f);
        assertTrue(BoardBug.shineAt(BoardBug.SHINE_MS / 2) > 0.3f);
        assertEquals(-1f, BoardBug.shineAt(BoardBug.SHINE_MS + 100), 0f);
        assertTrue(BoardBug.shineAt(BoardBug.SHINE_EVERY + 10) >= 0f);
        assertEquals(0xFFD7192F, BoardBug.CARD_RED);
    }

    @Test
    public void statsTurnOneAtATimeNeverOverEachOther() {
        for (float t = 0f; t <= 1f; t += 0.05f) {
            float[] o = BoardStats.turnParts(t);
            // Never both showing at once.
            assertTrue("at " + t, o[1] == 0f || o[3] == 0f);
        }
        assertArrayEquals(new float[]{1f, 0f, 0f, 1f}, BoardStats.turnParts(1f), 0.0001f);
        assertEquals(1f, BoardStats.turnParts(0f)[1], 0.0001f);
    }

    // ---- 4.18: every look moves in added time --------------------------------------------------------------------------

    @Test
    public void addedTimeIsKnownByTheHalfOnTheScreen() {
        assertTrue(BoardCover.inAddedTime("92:15", "90'+3'"));
        assertTrue(BoardCover.inAddedTime("90:00", "90'"));
        assertFalse(BoardCover.inAddedTime("89:59", "90'"));
        // 46:30 in the first half's added time, but not in the second half's normal time.
        assertTrue(BoardCover.inAddedTime("46:30", "45'+2'"));
        assertFalse(BoardCover.inAddedTime("46:30", "47'"));
        assertTrue(BoardCover.inAddedTime("106:00", "105'+1'"));
        assertFalse(BoardCover.inAddedTime("HT", "HT"));
        assertFalse(BoardCover.inAddedTime(null, null));
    }

    // ---- 4.31: frosted glass, the new moments, FotMob ----------------------------------------------------------------

    @Test
    public void theClockBeatsRedOnlyInAddedTime() {
        BoardBug.Look l = new BoardBug.Look();
        l.live = true;
        l.clock = "88:10";
        // A close game's last minutes: no beat any more.
        assertFalse(BoardBug.pulses(l));
        // Into the half's added time: it beats, on every look.
        l.clock = "90:40";
        l.stoppage = true;
        assertTrue(BoardBug.pulses(l));
        // Not once the game is over, nor with none.
        l.live = false;
        assertFalse(BoardBug.pulses(l));
        assertFalse(BoardBug.pulses(null));
    }

    @Test
    public void theHeartbeatIsTwoPulsesASecond() {
        float most = 0f, least = 1f;
        for (long t = 0; t < 1000; t += 10) {
            float v = BoardBug.heartbeat(t);
            assertTrue(v >= 0f && v <= 1f);
            most = Math.max(most, v);
            least = Math.min(least, v);
            assertEquals(v, BoardBug.heartbeat(t + 1000), 1e-4f);
        }
        assertTrue(most > 0.8f);
        assertTrue(least < 0.1f);
    }

    @Test
    public void fotMobsClubsAreMatchedByName() {
        assertTrue(FotMob.sameClub("Tottenham Hotspur", "Tottenham"));
        assertTrue(FotMob.sameClub("Man City", "Manchester City"));
        assertTrue(FotMob.sameClub("Manchester United", "Man Utd"));
        assertFalse(FotMob.sameClub("Manchester City", "Manchester United"));
        assertTrue(FotMob.sameClub("Brighton & Hove Albion", "Brighton"));
        assertFalse(FotMob.sameClub("Arsenal", "Aston Villa"));
        assertFalse(FotMob.sameClub("", "Arsenal"));
    }

    @Test
    public void whoIsOnTopIsTheLastTenMinutesMomentum() {
        java.util.List<int[]> m = new java.util.ArrayList<>();
        for (int i = 1; i <= 30; i++) m.add(new int[]{i, i > 20 ? 40 : -30});
        assertEquals("home", FotMob.onTop(m)[0]);
        m.clear();
        for (int i = 1; i <= 30; i++) m.add(new int[]{i, i % 2 == 0 ? 10 : -10});
        assertEquals("", FotMob.onTop(m)[0]);
        assertEquals("", FotMob.onTop(new java.util.ArrayList<>())[0]);
    }

    @Test
    public void fotMobsFiguresComeAsTheBoardsStats() {
        FotMob.Match m = new FotMob.Match();
        m.stats.put("expected_goals", new String[]{"1.84", "0.62"});
        m.stats.put("big_chance", new String[]{"3", "1"});
        m.stats.put("physical_metrics_distance_covered", new String[]{"108345", "105120"});
        m.homeTop = new String[]{"Bukayo Saka", "8.4", "1"};
        m.awayTop = new String[]{"Cole Palmer", "7.6", "2"};
        for (int i = 1; i <= 20; i++) m.momentum.add(new int[]{i, -50});
        m.homeTeamId = 9825;
        m.insights.add(new String[]{"Have scored 8 goals in their last 5 matches", "9825", ""});
        java.util.Map<String, String[]> by = FotMob.statsOf(m, "ARS", "CHE");
        assertEquals("1.84", by.get("Expected goals")[1]);
        assertEquals("bar", by.get("Expected goals")[3]);
        assertEquals("108.3 km", by.get("Distance run")[1]);
        assertEquals("Saka 8.4", by.get("Top rated")[1]);
        assertEquals("text", by.get("Top rated")[3]);
        assertEquals("CHE on top", by.get("Momentum")[1]);
        assertEquals("one", by.get("Insight")[3]);
        assertEquals("ARS have scored 8 goals in their last 5 matches", by.get("Insight")[1]);
        assertNull(FotMob.insight(new String[]{"Haven't kept a clean sheet in 7 matches", "1", ""}, m, "ARS", "CHE"));
        assertEquals("Arsenal haven't lost to Hull City in their last 10 meetings (8W, 2D)",
                FotMob.insight(new String[]{"Arsenal haven't lost to Hull City in their last 10 meetings (8W, 2D).", "9825", ""}, m, "ARS", "HUL"));
        assertNull(by.get("Player of the match"));
        assertEquals("10.4", FotMob.km("10.4"));
        assertNull(FotMob.km("n/a"));
        // The half-time stats lead with expected goals once FotMob has them.
        by.put("Possession", new String[]{"Possession", "58%", "42%"});
        assertEquals("Possession", BoardCover.statsFor("ht", by).get(0)[0]);
        assertEquals("Expected goals", BoardCover.statsFor("ht", by).get(1)[0]);
        assertEquals("Expected goals", BoardCover.statsFor("ft", by).get(0)[0]);
    }

    @Test
    public void aSubstitutionSaysWhoCameOnAndWhoWentOff() {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "1";
        g.homeName = "Arsenal";
        g.awayName = "Chelsea";
        BoardDrop.Drop d = BoardCover.subDrop(g, new String[]{"Substitution", "67", "1", "Gabriel Martinelli", "9", "Leandro Trossard", "8"});
        assertEquals("SUBSTITUTION", d.chip);
        assertEquals("Arsenal", d.title);
        assertEquals("On Gabriel Martinelli · off Leandro Trossard · 67'", d.line);
        assertEquals(1, d.side);
    }

    // ---- 4.32: FotMob on every scoreboard ---------------------------------------------------------------------------

    @Test
    public void fotMobsRowsComeBestFirstForEveryScoreboard() {
        FotMob.Match m = new FotMob.Match();
        m.stats.put("touches_opp_box", new String[]{"45", "18"});
        m.stats.put("expected_goals", new String[]{"1.26", "0.36"});
        m.stats.put("big_chance", new String[]{"2", "0"});
        java.util.List<String[]> rows = FotMob.rows(m);
        assertEquals(3, rows.size());
        assertEquals("Expected goals", rows.get(0)[0]);
        assertEquals("Big chances", rows.get(1)[0]);
        assertEquals("Touches in box", rows.get(2)[0]);
        assertTrue(FotMob.rows(null).isEmpty());
        assertEquals(126, FotMob.hundredths("1.26"));
        assertEquals(0, FotMob.hundredths("n/a"));
    }

    // ---- 4.33: the backup channel, Also in 4K, ratings, the ground's weather, the predictions board -------------------

    private static java.util.List<String[]> guide(String... names) {
        java.util.List<String[]> g = new java.util.ArrayList<>();
        for (String n : names) g.add(new String[]{"0", n, "Sport"});
        return g;
    }

    @Test
    public void aFrozenPictureIsTenSecondsOfSilenceAfterPlaying() {
        assertTrue(ChannelWatch.frozen(60_000L, 10_000L));
        assertFalse(ChannelWatch.frozen(60_000L, 9_000L));
        // A channel just tuned is still loading: not frozen.
        assertFalse(ChannelWatch.frozen(5_000L, 30_000L));
    }

    @Test
    public void theBackupIsTheSameChannelsOtherFormFirstThenTheMatchsOthers() {
        java.util.List<String[]> g = guide("Sky Sports Main Event UHD", "Sky Sports Main Event HD", "Sky Sports Premier League HD",
                "BBC One HD", "TNT Sports 1 HD");
        java.util.List<String> alt = ChannelWatch.alternatives("Sky Sports Main Event UHD",
                java.util.Arrays.asList("Sky Sports Main Event", "Sky Sports Premier League"), g);
        assertEquals(java.util.Arrays.asList("Sky Sports Main Event HD", "Sky Sports Premier League HD"), alt);
        // Without the playlist's list: the match's channels by name, the one on left out.
        assertEquals(java.util.Collections.singletonList("Sky Sports Premier League"), ChannelWatch.alternatives("Sky Sports Main Event",
                java.util.Arrays.asList("Sky Sports Main Event", "Sky Sports Premier League"), new java.util.ArrayList<>()));
    }

    @Test
    public void alsoIn4kOnlyWhenThePlaylistHasIt() {
        java.util.List<String[]> g = guide("Sky Sports Main Event HD", "Sky Sports Main Event UHD", "TNT Sports 1 HD");
        assertEquals("Sky Sports Main Event UHD", ChannelWatch.uhdFor("Sky Sports Main Event HD", g));
        assertNull(ChannelWatch.uhdFor("Sky Sports Main Event UHD", g));
        assertNull(ChannelWatch.uhdFor("TNT Sports 1 HD", g));
        assertNull(ChannelWatch.uhdFor("", g));
        assertTrue(ChannelWatch.fourK("UK: TNT Sports 4K"));
        assertFalse(ChannelWatch.fourK("Sky Sports Mix HD"));
    }

    @Test
    public void theRatingsPagesAreTheThreeBestThenTheRest() {
        java.util.List<String[]> top = new java.util.ArrayList<>();
        String[][] r = {{"Martin Ødegaard", "8.5", "1", "1"}, {"David Raya", "8.2", "2", "1"}, {"Konstantinos Tzolakis", "8.0", "3", "0"},
                {"Semi Ajayi", "8.0", "4", "0"}, {"Jurriën Timber", "7.9", "5", "1"}};
        for (String[] x : r) top.add(x);
        String[][] pages = MatchExtras.ratingPages(top, "Martin Ødegaard");
        assertEquals(4, pages.length);
        assertEquals("1  Ødegaard", pages[0][0]);
        assertEquals("8.5 · Player of the match", pages[0][1]);
        assertEquals("8.2", pages[1][1]);
        assertEquals("Ajayi 8.0 · Timber 7.9", pages[3][1]);
        assertEquals("Ødegaard 8.5 · Player of the match", MatchExtras.ratingsHeadline(top, "Martin Ødegaard"));
        assertEquals("Ødegaard 8.5 · best rated", MatchExtras.ratingsHeadline(top, "Someone Else"));
        assertEquals("8.5", FotMob.rating(8.49));
        assertEquals("", FotMob.rating(0));
    }

    @Test
    public void theGroundsWeatherReadsAsAForecaster() {
        assertEquals("9° · Light rain at Emirates Stadium", MatchExtras.weatherHeadline(9.4, "Light rain", "Emirates Stadium"));
        assertEquals("Wind 14 mph SW · Rain 80% by full time · 6° at full time", MatchExtras.weatherLine(13.6, "SW", 80, 6.2, false));
        assertEquals("Wind 5 mph · Dry to full time", MatchExtras.weatherLine(5, "", 10, Double.NaN, true));
        assertEquals("SW", MatchExtras.compass(225));
        assertEquals("N", MatchExtras.compass(359));
        assertEquals("GB", MatchExtras.countryCode("England"));
        assertEquals("", MatchExtras.countryCode("Narnia"));
    }

    @Test
    public void thePredictionsBoardIsBestFirstThenTheSeason() {
        java.util.List<Object[]> picks = new java.util.ArrayList<>();
        picks.add(new Object[]{"Player 3", 1, 1});
        picks.add(new Object[]{"You", 2, 1});
        picks.add(new Object[]{"Player 2", 3, 1});
        picks.add(new Object[]{"Taken back", -1, -1});
        java.util.List<String[]> rows = PredictionBoard.results(picks, 2, 1);
        assertEquals(3, rows.size());
        assertEquals("You", rows.get(0)[0]);
        assertEquals("Spot on · +3", rows.get(0)[2]);
        assertEquals("Right result · +1", rows.get(1)[2]);
        assertEquals("No points", rows.get(2)[2]);
        String[][] pages = PredictionBoard.boardPages(rows, "You 41 · Player 2 37");
        assertEquals(4, pages.length);
        assertEquals("You  2–1", pages[0][0]);
        assertEquals("Season", pages[3][0]);
        assertEquals(3, PredictionBoard.score(2, 1, 2, 1));
        assertEquals(1, PredictionBoard.score(3, 0, 2, 1));
        assertEquals(0, PredictionBoard.score(0, 0, 2, 1));
    }

    @Test
    public void anAlertTurnsThroughItsPages() {
        BoardDrop.Drop d = new BoardDrop.Drop();
        assertEquals(1, BoardDrop.pageCount(d));
        d.pages = new String[][]{{"1  Ødegaard", "8.5"}, {"2  Raya", "8.2"}, {"3  Tzolakis", "8.0"}};
        assertEquals(3, BoardDrop.pageCount(d));
        d.lineups = true;
        assertEquals(2, BoardDrop.pageCount(d));
        assertEquals(1, BoardDrop.pageCount(null));
    }
}
