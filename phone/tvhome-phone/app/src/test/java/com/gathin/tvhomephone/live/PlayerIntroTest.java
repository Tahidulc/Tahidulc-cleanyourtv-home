package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;

import com.gathin.broadcast.BroadcastData;
import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

/** The phone's game as the classic pop-up and line-ups read it: ESPN's colours, the full names, the competition. */
public class PlayerIntroTest {
    @Test
    public void espnsColoursAreReadWithOrWithoutTheirHash() {
        assertEquals(0xFFFFFF00, PlayerIntro.colour("ffff00"));
        assertEquals(0xFFDA291C, PlayerIntro.colour("#DA291C"));
        assertEquals(0, PlayerIntro.colour(""));
        assertEquals(0, PlayerIntro.colour("zzzzzz"));
        assertEquals(0, PlayerIntro.colour(null));
    }

    @Test
    public void theFullNamesAndTheCompetitionGoToThePopUp() {
        Game g = new Game();
        g.homeName = "Man United";
        g.homeFull = "Manchester United";
        g.awayName = "Aston Villa";
        g.home = "MUN";
        g.away = "AVL";
        g.homeColor = "da291c";
        g.slug = "eng.1";
        g.leagueName = "English Premier League";
        g.venue = "Old Trafford";
        BroadcastData.Match m = PlayerIntro.match(g);
        assertEquals("Manchester United", m.homeName);
        assertEquals("Aston Villa", m.awayName);   // no full name: ESPN's own
        assertEquals("MUN", m.homeShort);
        assertEquals(0xFFDA291C, m.homeColor);
        assertEquals("pl", BroadcastData.compKey(m.compId, m.compName));
        assertEquals("Old Trafford", m.venue);
    }

    @Test
    public void aTestRestartHasAGoalInIt() {
        Game g = new Game();
        assertEquals(1, PlayerIntro.testScore(g, true)[0]);
        assertEquals(0, PlayerIntro.testScore(g, true)[1]);
        assertEquals(0, PlayerIntro.testScore(g, false)[0]);
        g.homeScore = 2;
        g.awayScore = 3;
        assertEquals(2, PlayerIntro.testScore(g, true)[0]);
        assertEquals(3, PlayerIntro.testScore(g, false)[1]);
    }
}
