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
    public void theMatchdayIntroComesTwelveMinutesBeforeKickoffNotInTheLastFew() {
        long min = 60_000L;
        assertFalse(Moments.introDue(13 * min));
        assertTrue(Moments.introDue(12 * min));
        assertTrue(Moments.introDue(10 * min));
        assertTrue(Moments.introDue(9 * min));
        assertFalse(Moments.introDue(8 * min));
        // never over the walk-out or the anthems (it was four minutes before)
        assertFalse(Moments.introDue(4 * min));
        assertFalse(Moments.introDue(100_000L));
        // made ready out of sight a minute and a half before
        assertTrue(Moments.introPrep(13 * min));
        assertFalse(Moments.introPrep(12 * min));
        assertFalse(Moments.introPrep(14 * min));
    }

    @Test
    public void theIntrosOverEveryBreakFromAnHourAndAHalfToEightMinutesBefore() {
        long min = 60_000L;
        assertTrue(Moments.breakIntrosDue(90 * min));
        assertTrue(Moments.breakIntrosDue(20 * min));
        assertTrue(Moments.breakIntrosDue(8 * min));
        assertFalse(Moments.breakIntrosDue(7 * min));
        assertFalse(Moments.breakIntrosDue(5 * min));
        assertFalse(Moments.breakIntrosDue(91 * min));
        assertFalse(Moments.breakIntrosDue(-min));
        // the logo watched for them from half an hour before they begin, the ad-break screen on or not
        assertTrue(Moments.breaksWatch(120 * min));
        assertFalse(Moments.breaksWatch(121 * min));
        assertTrue(Moments.breaksWatch(7 * min));
        assertFalse(Moments.breaksWatch(6 * min));
        assertFalse(Moments.breaksWatch(-10 * min));
    }

    @Test
    public void aBreaksIntroLeavesTheMatchdaysOutAndNoneComesStraightAfterAnother() {
        long min = 60_000L, now = 1_000_000_000L;
        // no break has had one: the matchday intro comes
        assertTrue(Moments.matchdayWanted(0, now));
        // a break's intro in the twenty minutes before: the adverts bring them, so not
        assertFalse(Moments.matchdayWanted(now - 6 * min, now));
        assertFalse(Moments.matchdayWanted(now - 19 * min, now));
        assertTrue(Moments.matchdayWanted(now - 20 * min, now));
        // five minutes at least from one intro's start to the next's
        assertTrue(Moments.introSpaced(0, now));
        assertFalse(Moments.introSpaced(now - 2 * min, now));
        assertFalse(Moments.introSpaced(now - 4 * min, now));
        assertTrue(Moments.introSpaced(now - 5 * min, now));
        // a clock put back never holds one back
        assertTrue(Moments.introSpaced(now + min, now));
        assertTrue(Moments.matchdayWanted(now + min, now));
    }

    @Test
    public void theMatchIntroIsReadyBeforeTheBreaksAndTakesTurnsWithTheCompetitionsOwn() {
        // gathered from twenty minutes before the breaks' intros begin, until just after the kick-off
        assertTrue(Moments.matchWarm(100 * 60_000L));
        assertTrue(Moments.matchWarm(110 * 60_000L));
        assertFalse(Moments.matchWarm(111 * 60_000L));
        assertTrue(Moments.matchWarm(0));
        assertFalse(Moments.matchWarm(-6 * 60_000L));
        // the match intro first, then in turn with the competition's own
        assertEquals("network", Moments.breakStyle(0, true, true, false));
        assertEquals("other", Moments.breakStyle(1, true, true, false));
        assertEquals("network", Moments.breakStyle(2, true, true, false));
        // alone when the competition's own is off, the competition's own when the match intro is not ready
        assertEquals("network", Moments.breakStyle(1, true, false, false));
        assertEquals("other", Moments.breakStyle(0, false, true, false));
        assertEquals("", Moments.breakStyle(0, false, false, false));
        // a Champions League game: its own style, then the network's
        assertEquals("ucl", Moments.breakStyle(0, true, true, true));
        assertEquals("network", Moments.breakStyle(1, true, true, true));
        assertEquals("ucl", Moments.breakStyle(2, true, false, true));
        assertEquals("other", Moments.breakStyle(3, false, true, true));
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
        // F1's race: four minutes before its start, not later than 100 seconds (the football's moved, this did not)
        assertFalse(Moments.raceIntroDue(300_000L));
        assertTrue(Moments.raceIntroDue(240_000L));
        assertTrue(Moments.raceIntroDue(150_000L));
        assertFalse(Moments.raceIntroDue(90_000L));
    }

    @Test
    public void anOwnFilmEndsOnTheTrophyFlash() {
        assertEquals(0.0, Moments.introStart(0), 0);
        assertEquals(0.0, Moments.introStart(52_000), 0);
        assertEquals(0.0, Moments.introStart(40_000), 0);
        assertEquals(15.0, Moments.introStart(25_000), 0);
        assertEquals(29.33, Moments.introStart(10_670), 0);
    }

    @Test
    public void aGameJoinedPartWayThroughTakesItsHalfFromTheScores() {
        // England v Croatia: the board up by hand in the second half, no kick-off or half time ever seen.
        assertEquals("h2", Moments.phase(false, 0, false, null, 2));
        assertEquals("h1", Moments.phase(false, 0, false, null, 1));
        assertEquals("h2", Moments.phase(false, 0, false, null, 3));
        // Not known: as before; what was seen always wins over the scores.
        assertEquals("", Moments.phase(false, 0, false, null, 0));
        assertEquals("h1", Moments.phase(false, 0, false, "pre", 2));
        assertEquals("ht", Moments.phase(false, 0, true, null, 1));
        assertEquals("ft", Moments.phase(false, 2, false, null, 2));
        // Joined in the second half: no kick-off pop-up for it.
        assertNull(Moments.moment(null, Moments.phase(false, 0, false, null, 2)));
    }

    @Test
    public void theRestartWaitsForTheReplaysToEnd() {
        assertEquals(0, Moments.restartNow(10_000L, false));
        assertEquals(1, Moments.restartNow(Moments.RESTART_MS, false));
        assertEquals(0, Moments.restartNow(Moments.RESTART_MS + 5_000L, true));
        assertEquals(1, Moments.restartNow(Moments.RESTART_MS + 20_000L, false));
        assertEquals(-1, Moments.restartNow(Moments.RESTART_WAIT_MS, true));
    }

    @Test
    public void theBoardIsNeverOnTheScreenWithAMomentsPopUp() {
        // on while a moment's pop-up is on
        org.junit.Assert.assertTrue(Moments.boardAwayForPopup(true, false, false));
        org.junit.Assert.assertTrue(Moments.boardAwayForPopup(true, true, false));
        // away in a goal's replays with its restart pop-up still to come: kept away until that has been
        org.junit.Assert.assertTrue(Moments.boardAwayForPopup(false, true, true));
        // up with the restart's pop-up still to come (no replays seen): it goes only when the pop-up comes
        org.junit.Assert.assertFalse(Moments.boardAwayForPopup(false, true, false));
        // nothing on, nothing to come: back
        org.junit.Assert.assertFalse(Moments.boardAwayForPopup(false, false, true));
        org.junit.Assert.assertFalse(Moments.boardAwayForPopup(false, false, false));
        // the moments: the kick-off, the restart after a goal, the second half; not the line-ups, the strap or the intro
        org.junit.Assert.assertTrue(Moments.boardMoment("kickoff"));
        org.junit.Assert.assertTrue(Moments.boardMoment("restart"));
        org.junit.Assert.assertTrue(Moments.boardMoment("second"));
        org.junit.Assert.assertFalse(Moments.boardMoment("strap"));
        org.junit.Assert.assertFalse(Moments.boardMoment("lineups"));
        org.junit.Assert.assertFalse(Moments.boardMoment("intro"));
        org.junit.Assert.assertFalse(Moments.boardMoment(null));
    }
}
