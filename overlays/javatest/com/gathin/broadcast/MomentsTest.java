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
}
