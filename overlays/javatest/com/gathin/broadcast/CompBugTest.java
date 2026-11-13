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
            assertTrue(k.tall >= 1f && k.tall <= 2f);
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
    public void wordsReadOnEitherGround() {
        assertEquals(0xFF111418, CompBug.ink(0xFFFFFFFF));
        assertEquals(0xFFF5F6FA, CompBug.ink(0xFF0B1A5E));
        assertEquals(0xFF000000, CompBug.darker(0xFFFFFFFF, 1f));
    }
}
