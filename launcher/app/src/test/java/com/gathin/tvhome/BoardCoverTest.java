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
        assertTrue(BoardCover.hintFor(0, true).startsWith("Moving the scoreboard"));
        assertTrue(BoardCover.hintFor(1, true).contains("OK: its height"));
        assertTrue(BoardCover.hintFor(2, true).contains("its width kept"));
        // Timing comes after the height, then the logo's cover (or back to moving the board without one).
        assertTrue(BoardCover.hintFor(3, true).contains("the logo's cover"));
        assertTrue(BoardCover.hintFor(3, false).contains("OK: move it"));
        assertTrue(BoardCover.hintFor(3, true).contains("a second later"));
        assertTrue(BoardCover.hintFor(5, true).contains("its width"));
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
        assertEquals(8, BoardBug.Style.values().length);
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
        // Into added time: 45:00 with the minutes added beside it.
        assertArrayEquals(new String[]{"45:00", "+2"}, BoardCover.anchored(a, at + (46 * 60) * 1000L, "45'+2'"));
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
    public void theMenuOverTheMatchSaysWhatEachLineWillDo() {
        String[] on = BoardCover.menuLines(true, true, "Classic", "Plain");
        assertEquals(6, on.length);
        assertEquals("Move and resize", on[0]);
        assertEquals("Hide the scoreboard", on[1]);
        assertEquals("Hide the logo's cover", on[2]);
        assertTrue(on[3].contains("Classic"));
        assertTrue(on[4].contains("Plain"));
        assertEquals("Turn the scoreboard off", on[5]);
        String[] off = BoardCover.menuLines(false, false, "Glass", "Mark only");
        assertEquals("Show the scoreboard", off[1]);
        assertEquals("Show the logo's cover", off[2]);
    }

    @Test
    public void theBroadcastLogosTagIsTheChannelsPicture() {
        assertEquals("HD", LogoMark.tagOf("UK: Sky Sports Main Event HD"));
        assertEquals("UHD", LogoMark.tagOf("UK: Sky Sports Premier League UHD"));
        assertEquals("UHD", LogoMark.tagOf("TNT Sports 1 4K"));
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
}
