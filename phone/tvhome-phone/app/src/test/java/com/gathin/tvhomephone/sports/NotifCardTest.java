package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class NotifCardTest {
    private static Game game() {
        Game g = new Game();
        g.id = "9";
        g.slug = "uefa.champions";
        g.homeName = "Man Utd";
        g.awayName = "Roma";
        g.home = "MUN";
        g.away = "ROM";
        g.leagueName = "Champions League";
        g.state = Game.LIVE;
        g.detail = "86'";
        g.goals.add(new String[]{"G1", "Matías Soulé", "11'", "away", ""});
        g.goals.add(new String[]{"G2", "Donyell Malen", "17'", "away", "pen"});
        g.goals.add(new String[]{"G3", "Bruno Fernandes", "58'", "home", "pen"});
        g.goals.add(new String[]{"G4", "Matheus Cunha", "83'", "home", ""});
        g.goals.add(new String[]{"G5", "Matheus Cunha", "86'", "home", ""});
        g.homeScore = 3;
        g.awayScore = 2;
        return g;
    }

    @Test public void aGoalTellsTheScoreAsItWasThen() {
        Game g = game();
        Events.Alert a = new Events.Alert("goal", "G4", "GOAL · Man Utd 3–2 Roma", "Matheus Cunha 83' for Man Utd", g);
        assertArrayEquals(new int[]{2, 2}, NotifCard.score(a));
        assertEquals("GOAL!  Man Utd 2–2 Roma", NotifCard.title(a));
        assertEquals("Matheus Cunha 83' · Man Utd", NotifCard.line(a));
        assertEquals("home", NotifCard.side(a));
        assertEquals("83'", NotifCard.clock(a));
        assertEquals("GOAL", NotifCard.pill(a));
        assertTrue(NotifCard.drawn(a));
    }

    @Test public void aPenaltyAndTheAwaySide() {
        Game g = game();
        Events.Alert a = new Events.Alert("goal", "G2", "GOAL · Man Utd 3–2 Roma", "Donyell Malen 17' (pen) for Roma", g);
        assertEquals("away", NotifCard.side(a));
        assertEquals("Donyell Malen (pen)", NotifCard.who(a));
        assertArrayEquals(new int[]{0, 2}, NotifCard.score(a));
    }

    @Test public void lateDramaIsTheGoalsCard() {
        Game g = game();
        Events.Alert a = new Events.Alert("drama", "D|G5", "LATE DRAMA · 86' winner", "Matheus Cunha for Man Utd · Man Utd 3–2 Roma", g);
        assertEquals("LATE DRAMA", NotifCard.pill(a));
        assertArrayEquals(new int[]{3, 2}, NotifCard.score(a));
        assertEquals("Late drama · Man Utd 3–2 Roma", NotifCard.title(a));
    }

    @Test public void halfTimeAndFullTime() {
        Game g = game();
        Events.Alert ft = new Events.Alert("ft", "F|9", "FULL TIME · Man Utd 3–2 Roma", "", g);
        assertEquals("Full time · Man Utd 3–2 Roma", NotifCard.title(ft));
        assertEquals("FT", NotifCard.clock(ft));
        assertEquals("Fernandes 58' (p), Cunha 83', Cunha 86'", NotifCard.scorers(g, "home"));
        assertEquals("Soulé 11', Malen 17' (p)", NotifCard.scorers(g, "away"));
    }

    @Test public void lineUpsKeepTheirWords() {
        Game g = game();
        Events.Alert l = new Events.Alert("kickoff", "L|9", "LINE-UPS · Man Utd v Roma · 20:00", "...", g);
        assertFalse(NotifCard.drawn(l));
        Events.Alert none = new Events.Alert("weather", "W|1", "Rain", "Later", null);
        assertFalse(NotifCard.drawn(none));
    }

    @Test public void redCardSide() {
        Game g = game();
        g.reds.add(new String[]{"R1", "Bryan Cristante", "70'", "away"});
        Events.Alert r = new Events.Alert("red", "R1", "RED CARD · Roma", "Bryan Cristante 70' · Man Utd 3–2 Roma", g);
        assertEquals("away", NotifCard.side(r));
        assertEquals("Red card · Roma", NotifCard.title(r));
        assertEquals("70'", NotifCard.clock(r));
        assertEquals("Bryan Cristante · Roma", NotifCard.who(r));
        List<String[]> reds = g.reds;
        assertEquals(1, reds.size());
    }
}
