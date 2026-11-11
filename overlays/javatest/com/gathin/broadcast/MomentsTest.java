package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** When the classic pop-up and line-ups come over the channel: from the board's phases and the time to kick-off. */
public class MomentsTest {
    @Test
    public void theBoardsPhaseFollowsTheGameOnTheScreen() {
        assertEquals("pre", Moments.phase(true, 0, false, null));
        assertEquals("pre", Moments.phase(false, 1, false, null));
        assertEquals("h1", Moments.phase(false, 0, false, "pre"));
        assertEquals("ht", Moments.phase(false, 0, true, "h1"));
        assertEquals("h2", Moments.phase(false, 0, false, "ht"));
        assertEquals("h2", Moments.phase(false, 0, false, "h2"));
        assertEquals("ft", Moments.phase(false, 2, false, "h2"));
        // switched on during the game: which half it is cannot be told, so no pop-up comes from it
        assertEquals("", Moments.phase(false, 0, false, null));
    }

    @Test
    public void thePopUpComesAtEachKickOffOnly() {
        assertEquals("kickoff", Moments.moment("pre", "h1"));
        assertEquals("second", Moments.moment("ht", "h2"));
        assertNull(Moments.moment("h1", "h1"));
        assertNull(Moments.moment("h1", "ht"));
        assertNull(Moments.moment("h2", "ft"));
        assertNull(Moments.moment("", "h1"));
    }

    @Test
    public void theLineUpsComeInTheLastSevenMinutesBeforeKickOff() {
        assertFalse(Moments.lineupsDue(8 * 60_000L));
        assertTrue(Moments.lineupsDue(7 * 60_000L));
        assertTrue(Moments.lineupsDue(3 * 60_000L));
        assertFalse(Moments.lineupsDue(60_000L));   // too late for a minute and a half of them
        assertFalse(Moments.lineupsDue(-60_000L));
    }

    @Test
    public void theStadiumLineUpsComeAtTheWalkOut() {
        assertFalse(Moments.lineupsDue("stadium", 5 * 60_000L));
        assertTrue(Moments.lineupsDue("stadium", 210_000L));
        assertTrue(Moments.lineupsDue("stadium", 90_000L));
        assertFalse(Moments.lineupsDue("stadium", 50_000L));
        assertTrue(Moments.lineupsDue("classic", 5 * 60_000L));
    }

    @Test
    public void theRestartNamesTheSidesLatestScorer() {
        List<String[]> goals = Arrays.asList(new String[]{"a", "Saka", "12'", "home", ""}, new String[]{"b", "Delap", "30'", "away", ""},
                new String[]{"c", "Havertz", "41'", "home", "pen"});
        assertEquals("Havertz", Moments.lastGoal(goals, 1)[1]);
        assertEquals("Delap", Moments.lastGoal(goals, 2)[1]);
        assertNull(Moments.lastGoal(Arrays.<String[]>asList(), 1));
        assertEquals("Havertz", Moments.scorer(Moments.lastGoal(goals, 1)));
        assertEquals("Gabriel (OG)", Moments.scorer(new String[]{"d", "Gabriel", "77'", "away", "og"}));
        assertEquals("", Moments.scorer(null));
    }

    @Test
    public void introOverTheLastBreakBeforeKickoff() {
        assertFalse(Moments.introDue(300_000L));
        assertTrue(Moments.introDue(240_000L));
        assertTrue(Moments.introDue(150_000L));
        assertFalse(Moments.introDue(90_000L));
        assertTrue(Moments.introPrep(300_000L));
        assertFalse(Moments.introPrep(240_000L));
        assertFalse(Moments.introPrep(400_000L));
    }

    @Test
    public void sportsIntrosFromTheProgramme() {
        assertEquals("ufc", Moments.sportOf("Live UFC 323: Dvalishvili v Yan"));
        assertEquals("ufc", Moments.sportOf("UFC Fight Night: Royval v Kape"));
        assertEquals("boxing", Moments.sportOf("Live Boxing: Usyk v Dubois"));
        assertEquals("boxing", Moments.sportOf("Sky Sports Box Office: Joshua v Ngannou"));
        assertEquals("f1", Moments.sportOf("Live F1: The Abu Dhabi Grand Prix"));
        assertEquals("f1", Moments.sportOf("Formula 1: Las Vegas GP - Race"));
        assertNull(Moments.sportOf("Live F1: Abu Dhabi GP Qualifying"));
        assertNull(Moments.sportOf("F1 Practice 2"));
        assertNull(Moments.sportOf("UFC 322 Highlights"));
        assertNull(Moments.sportOf("Live PL: Arsenal v Everton"));
        assertNull(Moments.sportOf(null));
        assertEquals("UFC 323: Dvalishvili v Yan", Moments.eventLine("Live UFC 323: Dvalishvili v Yan"));
        assertEquals("Boxing: Usyk v Dubois", Moments.eventLine("LIVE: Boxing: Usyk v Dubois"));
    }

    @Test
    public void aProgrammesIntroFourMinutesBeforeItToAMinuteIn() {
        long start = 1_000_000_000L;
        assertFalse(Moments.programmeDue(start, start - 300_000L));
        assertTrue(Moments.programmeDue(start, start - 240_000L));
        assertTrue(Moments.programmeDue(start, start + 30_000L));
        assertFalse(Moments.programmeDue(start, start + 90_000L));
        assertFalse(Moments.programmeDue(0, start));
    }

    @Test
    public void anOwnFilmEndsOnTheTrophyFlash() {
        assertEquals(0.0, Moments.introStart(0), 0);
        assertEquals(0.0, Moments.introStart(52_000), 0);
        assertEquals(0.0, Moments.introStart(40_000), 0);
        assertEquals(15.0, Moments.introStart(25_000), 0);
        assertEquals(29.33, Moments.introStart(10_670), 0);
    }
}
