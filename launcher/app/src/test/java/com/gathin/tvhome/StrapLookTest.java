package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.BroadcastData;
import com.gathin.broadcast.CompBug;
import com.gathin.broadcast.StrapLooksTest;

import org.junit.Test;

import java.util.Set;

/** The kick-off pop-up in the TV board's look: every look the board has is one the strap draws (strap.js). */
public class StrapLookTest {
    /** Games of each competition the board knows (CompBug.forGame), and of none. */
    private static final String[][] GAMES = {{"eng.1", "English Premier League"}, {"eng.fa", "FA Cup"}, {"eng.league_cup", "Carabao Cup"},
            {"uefa.champions", ""}, {"uefa.europa", ""}, {"uefa.europa.conf", ""}, {"sco.1", "Scottish Premiership"}, {"esp.1", "LaLiga"},
            {"ita.1", "Italian Serie A"}, {"ger.1", "German Bundesliga"}, {"fra.1", "French Ligue 1"}, {"ned.1", "Dutch Eredivisie"},
            {"por.1", "Liga Portugal"}, {"usa.1", "MLS"}, {"mex.1", "Mexican Liga BBVA MX"}, {"fifa.friendly", "International Friendly"},
            {"", ""}, {null, null}};

    @Test
    public void everyLookOfTheBoardHasItsStrap() throws Exception {
        Set<String> page = StrapLooksTest.pageLooks();
        for (BoardBug.Style s : BoardBug.Style.values()) {
            for (String[] g : GAMES) {
                String l = BroadcastData.strapLook(s.name(), g[0], g[1]);
                assertTrue(s + " on " + g[1] + ": " + l + " is not in strap.js", page.contains(l));
                // its own look, never another's (By competition aside)
                if (s != BoardBug.Style.COMPETITION) assertEquals(s.name(), l);
            }
        }
    }

    @Test
    public void byCompetitionIsTheLookTheBoardDrawsForTheGame() {
        for (String[] g : GAMES) {
            // the board's: its competition's look (BoardBug.kind()), Classic for one with none
            CompBug.Kind k = CompBug.forGame(g[0], g[1]);
            String board = "CLASSIC";
            for (BoardBug.Style s : BoardBug.Style.values()) if (k != null && s.kind == k) board = s.name();
            assertEquals(g[1], board, BroadcastData.strapLook(BoardBug.Style.COMPETITION.name(), g[0], g[1]));
        }
    }

    @Test
    public void thePopUpFollowsTheBoardOnceAndOkStepsThroughTheThree() {
        assertEquals("strap", ClassicIntro.followBoard("classic", true));
        assertEquals("strap", ClassicIntro.followBoard(null, true));
        assertEquals("off", ClassicIntro.followBoard(null, false));
        assertEquals("off", ClassicIntro.followBoard("off", true));
        assertEquals("strap", ClassicIntro.followBoard("strap", false));
        assertEquals("classic", ClassicIntro.nextPopup("strap"));
        assertEquals("off", ClassicIntro.nextPopup("classic"));
        assertEquals("strap", ClassicIntro.nextPopup("off"));
    }
}
