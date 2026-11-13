package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which competition's look a game takes, and the scores read off the board's score. */
public class CompBugTest {
    @Test
    public void eachCompetitionHasItsLookBySlug() {
        assertEquals(CompBug.Kind.PL, CompBug.forGame("eng.1", "English Premier League"));
        assertEquals(CompBug.Kind.FACUP, CompBug.forGame("eng.fa", ""));
        assertEquals(CompBug.Kind.EFL, CompBug.forGame("eng.2", "English Championship"));
        assertEquals(CompBug.Kind.EFL, CompBug.forGame("eng.league_cup", "Carabao Cup"));
        assertEquals(CompBug.Kind.UCL, CompBug.forGame("uefa.champions", ""));
        assertEquals(CompBug.Kind.UEL, CompBug.forGame("uefa.europa", ""));
        assertEquals(CompBug.Kind.UECL, CompBug.forGame("uefa.europa.conf", ""));
        assertEquals(CompBug.Kind.SPFL, CompBug.forGame("sco.1", ""));
        assertEquals(CompBug.Kind.LALIGA, CompBug.forGame("esp.1", ""));
        assertEquals(CompBug.Kind.SERIEA, CompBug.forGame("ita.1", ""));
        assertEquals(CompBug.Kind.BUNDESLIGA, CompBug.forGame("ger.1", ""));
        assertEquals(CompBug.Kind.LIGUE1, CompBug.forGame("fra.1", ""));
        assertEquals(CompBug.Kind.EREDIVISIE, CompBug.forGame("ned.1", ""));
        assertEquals(CompBug.Kind.LIGAPORTUGAL, CompBug.forGame("por.1", ""));
        assertEquals(CompBug.Kind.MLS, CompBug.forGame("usa.1", ""));
        assertEquals(CompBug.Kind.LIGAMX, CompBug.forGame("mex.1", ""));
    }

    @Test
    public void orByItsNameWhenTheSlugIsNotKnown() {
        assertEquals(CompBug.Kind.UCL, CompBug.forGame(null, "UEFA Champions League"));
        assertEquals(CompBug.Kind.LALIGA, CompBug.forGame("", "LaLiga"));
        assertEquals(CompBug.Kind.SERIEA, CompBug.forGame("", "Italian Serie A"));
        assertEquals(CompBug.Kind.EFL, CompBug.forGame("", "Sky Bet Championship"));
        assertEquals(CompBug.Kind.SPFL, CompBug.forGame("", "Scottish Premiership"));
        assertEquals(CompBug.Kind.PL, CompBug.forGame("", "Premier League"));
        // Other champions leagues are not UEFA's.
        assertNull(CompBug.forGame("", "AFC Champions League"));
        // No look of its own (England v Croatia): the board's own look.
        assertNull(CompBug.forGame("uefa.nations", "UEFA Nations League"));
        assertNull(CompBug.forGame(null, null));
    }

    @Test
    public void theScoresAreReadOffTheBoardsScore() {
        assertArrayEquals(new String[]{"2", "1"}, CompBug.scores("2–1"));
        assertArrayEquals(new String[]{"0", "0"}, CompBug.scores("0 - 0"));
        assertArrayEquals(new String[]{"10", "3"}, CompBug.scores("10-3"));
        assertNull(CompBug.scores("19:45"));
        assertNull(CompBug.scores("v"));
        assertNull(CompBug.scores(null));
    }

    @Test
    public void everyLookHasItsStripsAndAHeight() {
        for (CompBug.Kind k : CompBug.Kind.values()) {
            int[] s = CompBug.strip(k);
            assertEquals(4, s.length);
            assertEquals(0xFF, s[0] >>> 24);
            // Some stand an emblem over their row (the FA Cup's trophy, 2016's lion): up to a little over three rows.
            assertTrue(k.tall >= 1f && k.tall <= 3.3f);
            // Their row lies inside them, one row high.
            float h = 100f * k.tall;
            float[] bar = CompBug.bar(k, h);
            assertTrue(bar[0] >= 0f && bar[1] <= h + 0.01f);
            if (k != CompBug.Kind.LALIGA && k != CompBug.Kind.LIGAPORTUGAL && k != CompBug.Kind.BUNDESLIGA && k != CompBug.Kind.LIGUE1
                    && k != CompBug.Kind.R2004 && k.tall > 1f) {
                assertEquals(100f, bar[1] - bar[0], 0.5f);
            }
            assertTrue(!k.label.isEmpty());
        }
        assertEquals(20, CompBug.Kind.values().length);
    }

    @Test
    public void theRetroBoardsNameTheClubsShort() {
        assertEquals("MAN UTD", CompBug.shortName("Manchester United", "MUN"));
        assertEquals("MAN CITY", CompBug.shortName("Manchester City", "MCI"));
        assertEquals("NEWCASTLE", CompBug.shortName("Newcastle United", "NEW"));
        assertEquals("CHELSEA", CompBug.shortName("Chelsea", "CHE"));
        assertEquals("ARSENAL", CompBug.shortName("Arsenal FC", "ARS"));
        assertEquals("WOLVES", CompBug.shortName("Wolverhampton Wanderers", "WOL"));
        assertEquals("SPURS", CompBug.shortName("Tottenham Hotspur", "TOT"));
        assertEquals("BOURNEMOUTH", CompBug.shortName("AFC Bournemouth", "BOU"));
        assertEquals("NOTT'M FOREST", CompBug.shortName("Nottingham Forest", "NFO"));
        assertEquals("LEICESTER", CompBug.shortName("Leicester City", "LEI"));
        // No name: the code; a name too long even short: the code.
        assertEquals("LIV", CompBug.shortName("", "liv"));
        assertEquals("BMG", CompBug.shortName("Borussia Mönchengladbach Football", "BMG"));
        // No retro board is any competition's own: By competition never picks one.
        for (CompBug.Kind k : new CompBug.Kind[]{CompBug.Kind.R2004, CompBug.Kind.R2008, CompBug.Kind.R2012, CompBug.Kind.R2016}) {
            assertTrue(k.label.startsWith("Retro"));
        }
    }

    @Test
    public void theTabsNameTheCompetitionAsTheBroadcastsDo() {
        assertEquals("PREMIER LEAGUE", CompBug.title("English Premier League"));
        assertEquals("LIGA MX", CompBug.title("Mexican Liga BBVA MX"));
        assertEquals("LIGA PORTUGAL", CompBug.title("Portuguese Primeira Liga"));
        assertEquals("CHAMPIONS LEAGUE", CompBug.title("UEFA Champions League"));
        assertEquals("NATIONS LEAGUE", CompBug.title("UEFA Nations League"));
        assertEquals("PREMIERSHIP", CompBug.title("Scottish Premiership"));
        assertEquals("", CompBug.title(null));
        // Liga MX's title has its year and half, as CBS's does.
        assertEquals("2026 LIGA MX APERTURA", CompBug.mxSeason(2026, 9));
        assertEquals("2027 LIGA MX CLAUSURA", CompBug.mxSeason(2027, 3));
    }

    /** A W by H logo: a shape in rows 2 to 9, a clear gap, then a wordmark in rows 14 to 17. */
    private static int[] logoWithWordmark(int w, int h, int shape, int words) {
        int[] px = new int[w * h];
        for (int y = 2; y < 10; y++) for (int x = 6; x < 14; x++) px[y * w + x] = shape;
        for (int y = 14; y < 18; y++) for (int x = 1; x < w - 1; x++) px[y * w + x] = words;
        return px;
    }

    @Test
    public void aLogosEmblemIsCutFromTheWordmarkUnderIt() {
        int w = 20, h = 20;
        int[] px = logoWithWordmark(w, h, 0xFFFFFFFF, 0xFFFFFFFF);
        assertArrayEquals(new int[]{2, 10}, CompBug.topBand(px, w, h));
        int[] cut = CompBug.emblemPixels(px, w, h, CompBug.E_MARK);
        assertEquals(0xFFFFFFFF, cut[5 * w + 8]);
        assertEquals(0, cut[15 * w + 8]);
        // No gap: nothing parts it, the logo stays whole.
        int[] solid = new int[w * h];
        java.util.Arrays.fill(solid, 0xFF2040A0);
        assertNull(CompBug.topBand(solid, w, h));
        // Its colour first (Serie A's blue emblem off its white plate), its white first (the FA Cup's trophy off its shield).
        int[] blueOnWhite = logoWithWordmark(w, h, 0xFF1E6BE0, 0xFF0B2A6C);
        for (int i = 0; i < blueOnWhite.length; i++) if (blueOnWhite[i] == 0) blueOnWhite[i] = 0xFFFFFFFF;
        int[] blue = CompBug.emblemPixels(blueOnWhite, w, h, CompBug.E_COLOUR_MARK);
        assertEquals(0xFF1E6BE0, blue[5 * w + 8]);
        assertEquals(0, blue[0]);
        assertEquals(0, blue[15 * w + 8]);
        int[] trophy = logoWithWordmark(w, h, 0xFFFFFFFF, 0xFFFFFFFF);
        for (int i = 0; i < trophy.length; i++) if (trophy[i] == 0) trophy[i] = 0xFF0B1E50;
        int[] white = CompBug.emblemPixels(trophy, w, h, CompBug.E_WHITE_MARK);
        assertEquals(0xFF, white[5 * w + 8] >>> 24);
        assertEquals(0, white[0]);
        // Beside a wordmark: its left part (Liga MX's ball).
        int[] side = new int[w * h];
        for (int y = 4; y < 12; y++) {
            for (int x = 1; x < 6; x++) side[y * w + x] = 0xFFE03030;
            for (int x = 9; x < 19; x++) side[y * w + x] = 0xFF1040A0;
        }
        assertArrayEquals(new int[]{1, 6}, CompBug.leftBand(side, w, h));
        int[] ball = CompBug.emblemPixels(side, w, h, CompBug.E_MARK_LEFT);
        assertEquals(0xFFE03030, ball[6 * w + 3]);
        assertEquals(0, ball[6 * w + 12]);
        // Nothing of the colour asked for: nothing.
        assertNull(CompBug.emblemPixels(new int[w * h], w, h, CompBug.E_MARK));
    }

    @Test
    public void wordsReadOnEitherGround() {
        assertEquals(0xFF111418, CompBug.ink(0xFFFFFFFF));
        assertEquals(0xFFF5F6FA, CompBug.ink(0xFF0B1A5E));
        assertEquals(0xFF000000, CompBug.darker(0xFFFFFFFF, 1f));
    }
}
