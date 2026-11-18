package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.BroadcastData;
import com.gathin.broadcast.CompBug;
import com.gathin.broadcast.StrapLooksTest;

import org.junit.Test;

import java.util.Set;

/** The kick-off pop-up in the phone's scoreboard's look: every look the board has is one the strap draws (strap.js). */
public class StrapLookTest {
    /** Games of each competition the board knows (CompBug.forGame), and of none. */
    private static final String[][] GAMES = {{"eng.1", "English Premier League"}, {"eng.fa", "FA Cup"}, {"eng.2", "EFL Championship"},
            {"uefa.champions", ""}, {"uefa.europa", ""}, {"uefa.europa.conf", ""}, {"sco.1", "Scottish Premiership"}, {"esp.1", "LaLiga"},
            {"ita.1", "Italian Serie A"}, {"ger.1", "German Bundesliga"}, {"fra.1", "French Ligue 1"}, {"ned.1", "Dutch Eredivisie"},
            {"por.1", "Liga Portugal"}, {"usa.1", "MLS"}, {"mex.1", "Mexican Liga BBVA MX"}, {"", "Premier League"},
            {"fifa.friendly", "International Friendly"}, {"uefa.nations", "UEFA Nations League"}, {null, null}};

    @Test
    public void everyLookOfTheBoardHasItsStrap() throws Exception {
        Set<String> page = StrapLooksTest.pageLooks();
        for (OverlayBug.Style s : OverlayBug.Style.values()) {
            for (String[] g : GAMES) {
                String l = BroadcastData.strapLook(s.name(), g[0], g[1]);
                assertTrue(s + " on " + g[1] + ": " + l + " is not in strap.js", page.contains(l));
                // its own look, never another's (By competition aside)
                if (s != OverlayBug.Style.COMPETITION) assertEquals(s.name(), l);
            }
        }
    }

    @Test
    public void byCompetitionIsTheLookTheBoardDrawsForTheGame() {
        for (String[] g : GAMES) {
            // the board's: its competition's look (OverlayBug.kind()), Classic for one with none
            CompBug.Kind k = CompBug.forGame(g[0], g[1]);
            String board = "CLASSIC";
            for (OverlayBug.Style s : OverlayBug.Style.values()) if (k != null && s.kind == k) board = s.name();
            assertEquals(g[1], board, BroadcastData.strapLook(OverlayBug.Style.COMPETITION.name(), g[0], g[1]));
        }
    }

    @Test
    public void thePopUpFollowsTheBoardOnce() {
        // the family's classic (and one never chosen) becomes the strap in the board's look; off stays off
        assertEquals("strap", PlayerIntro.followBoard("classic", true));
        assertEquals("strap", PlayerIntro.followBoard(null, true));
        assertEquals("off", PlayerIntro.followBoard(null, false));
        assertEquals("off", PlayerIntro.followBoard("off", true));
        assertEquals("strap", PlayerIntro.followBoard("strap", false));
    }
}
