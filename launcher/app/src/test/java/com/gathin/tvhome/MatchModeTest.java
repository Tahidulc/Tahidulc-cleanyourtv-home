package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Match mode: the side cheered, the goal lights' colours, its quiet, and the games offered. */
public class MatchModeTest {

    @Test
    public void theStarredClubIsTheSideCheered() {
        assertEquals("home", MatchMode.side(new HashSet<>(Collections.singletonList("368")), "368", "367"));
        assertEquals("away", MatchMode.side(new HashSet<>(Collections.singletonList("367")), "368", "367"));
        assertEquals("", MatchMode.side(new HashSet<>(Arrays.asList("368", "367")), "368", "367"));
        assertEquals("", MatchMode.side(new HashSet<>(), "368", "367"));
    }

    @Test
    public void onlyTheCheeredSidesGoalsCelebrateUnlessNoneIs() {
        assertTrue(MatchMode.cheers("home", "home"));
        assertFalse(MatchMode.cheers("home", "away"));
        assertTrue(MatchMode.cheers("", "away"));
    }

    @Test
    public void coloursAreTheKitAtItsBrightestThenWhite() {
        // Everton's blue, at a bulb's brightest, then white.
        int[] c = MatchMode.colours(0x274488, 0xFFFFFF);
        assertEquals(0xFFFFFFFF, c[1]);
        int r = (c[0] >> 16) & 0xFF, g = (c[0] >> 8) & 0xFF, b = c[0] & 0xFF;
        assertEquals(255, b);
        assertTrue(r < g && g < b);
    }

    @Test
    public void aWhiteOrBlackKitFlashesWhiteThenItsSecondColour() {
        assertArrayEquals(new int[]{0xFFFFFFFF, 0xFFFF0000}, MatchMode.colours(0xFFFFFF, 0xFF0000));
        // Black and white stripes: white, then gold.
        assertArrayEquals(new int[]{0xFFFFFFFF, 0xFFFFC21A}, MatchMode.colours(0x000000 | 0x010101, 0xFFFFFF));
        // Not known: white and gold.
        assertArrayEquals(new int[]{0xFFFFFFFF, 0xFFFFC21A}, MatchMode.colours(0, 0));
    }

    @Test
    public void quietKeepsBackAllButItsGameTheDoorbellAndQuestions() {
        assertTrue(MatchMode.keepsBack(true, true, "goal", "other", "game1"));
        assertTrue(MatchMode.keepsBack(true, true, "news", null, "game1"));
        assertFalse(MatchMode.keepsBack(true, true, "goal", "game1", "game1"));
        assertFalse(MatchMode.keepsBack(true, true, "doorbell", null, "game1"));
        assertFalse(MatchMode.keepsBack(true, true, NotifyKinds.ALWAYS, null, "game1"));
        assertFalse(MatchMode.keepsBack(true, false, "goal", "other", "game1"));
        assertFalse(MatchMode.keepsBack(false, true, "goal", "other", "game1"));
    }

    private static ScoresTicker.Game game(String id, int state, long kickoff, String home, String away) {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = id;
        g.state = state;
        g.kickoff = kickoff;
        g.homeId = home;
        g.awayId = away;
        return g;
    }

    @Test
    public void theGamesOfferedAreTheStarredClubsFirstLiveBeforeToCome() {
        List<ScoresTicker.Game> all = new ArrayList<>(Arrays.asList(
                game("a", 1, 3000, "1", "2"), game("b", 0, 1000, "3", "4"), game("c", 2, 500, "368", "5"),
                game("d", 1, 2000, "368", "6"), game("e", 0, 1500, "7", "8")));
        List<ScoresTicker.Game> out = MatchPanel.choices(all, new HashSet<>(Collections.singletonList("368")));
        assertEquals(4, out.size());
        assertEquals("d", out.get(0).id);
        assertEquals("b", out.get(1).id);
        assertEquals("e", out.get(2).id);
        assertEquals("a", out.get(3).id);
    }
}
