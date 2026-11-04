package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class SummaryTest {
    private static String[] player(List<String[]> side, String name) {
        for (String[] p : side) if (p[Espn.P_NAME].equals(name)) return p;
        throw new AssertionError(name);
    }

    @Test public void startersHavePlacesSurnamesAndWhatTheyDid() throws Exception {
        Espn.Summary s = SummaryFixtures.aek();
        assertEquals("4-4-2", s.homeFormation);
        assertEquals("fcdd39", s.homeKit);
        String[] koita = player(s.homeLineup, "Aboubakary Koita");
        assertEquals("11", koita[Espn.P_NUMBER]);
        assertEquals("7", koita[Espn.P_PLACE]);
        assertEquals("Koita", koita[Espn.P_SHORT]);
        assertEquals("1", koita[Espn.P_GOALS]);
        assertEquals("R", koita[Espn.P_CARDS]);
        String[] majer = player(s.homeLineup, "Lovro Majer");
        assertEquals("53'", majer[Espn.P_OFF]);
        assertEquals("", majer[Espn.P_ON]);
        // No surname given: the short name as it is.
        assertEquals("Filipe Relvas", player(s.homeLineup, "Filipe Relvas")[Espn.P_SHORT]);
    }

    @Test public void substitutesCameOnWithTheirMinute() throws Exception {
        Espn.Summary s = SummaryFixtures.aek();
        String[] zini = player(s.homeLineup, "Zini");
        assertEquals("0", zini[Espn.P_STARTER]);
        assertEquals("53'", zini[Espn.P_ON]);
        assertEquals("", zini[Espn.P_PLACE]);
        // On at 81', then scored his assist's goal: on, not off.
        String[] valverde = player(s.awayLineup, "Federico Valverde");
        assertEquals("67'", valverde[Espn.P_ON]);
        assertEquals("1", valverde[Espn.P_GOALS]);
        assertEquals("", valverde[Espn.P_OFF]);
    }

    @Test public void timelineKeepsTheMomentsWithTheSecondPlayer() throws Exception {
        Espn.Summary s = SummaryFixtures.aek();
        for (String[] e : s.events) {
            assertFalse(e[1], e[1].toLowerCase(java.util.Locale.ROOT).contains("delay"));
            assertFalse(e[1], e[1].toLowerCase(java.util.Locale.ROOT).startsWith("end "));
        }
        String[] first = s.events.get(0);
        assertEquals("12'", first[0]);
        assertEquals("Aboubakary Koita", first[2]);
        assertEquals("Luka Jovic", first[5]);
        assertEquals("home", first[3]);
        // Half time came after the two first-half goals and the yellow card.
        assertEquals(3, s.halfTimeAt);
        String[] sub = s.events.get(3);
        assertEquals("Substitution", sub[1]);
        assertEquals("Zini", sub[2]);
        assertEquals("Lovro Majer", sub[5]);
    }

    @Test public void extrasForTheFacts() throws Exception {
        Espn.Summary s = SummaryFixtures.aek();
        assertEquals("887", s.homeId);
        assertEquals("86", s.awayId);
        assertEquals(5, s.homeLast.size());
        assertEquals("L", s.homeLast.get(4)[0]);
        assertEquals("0", s.homeLast.get(4)[1]);
        assertEquals("2", s.homeLast.get(4)[2]);
        assertEquals(2, s.meetings.size());
        assertEquals("2002", s.meetings.get(0)[0]);
        assertTrue(s.table.size() >= 2);
        assertEquals("69.6", s.awayNumbers.get("possessionPct"));
        assertFalse(s.leaders.isEmpty());
    }

    @Test public void beforeKickOffThePositionsAreLettersOnly() throws Exception {
        Espn.Summary s = SummaryFixtures.dortmund();
        assertEquals("3-4-2-1", s.homeFormation);
        assertTrue(s.events.isEmpty());
        assertEquals(-1, s.halfTimeAt);
        String[] kobel = player(s.homeLineup, "Gregor Kobel");
        assertEquals("G", kobel[Espn.P_POSITION]);
        assertEquals("1", kobel[Espn.P_PLACE]);
        assertEquals("Natan", player(s.awayLineup, "Natan")[Espn.P_SHORT]);
    }

    @Test public void playsHaveTheirPlacesAndCardsTheirFouls() throws Exception {
        Espn.Summary s = SummaryFixtures.aek();
        int goals = 0, shots = 0, cards = 0;
        for (String[] m : s.plays) {
            if (Espn.GOAL.equals(m[Espn.M_KIND])) goals++;
            else if (Espn.YELLOW.equals(m[Espn.M_KIND]) || Espn.RED.equals(m[Espn.M_KIND])) cards++;
            else shots++;
        }
        assertEquals(5, goals);
        assertEquals(28, shots);
        assertTrue("cards placed by their fouls: " + cards, cards >= 1);
        String[] first = null;
        for (String[] m : s.plays) if (Espn.GOAL.equals(m[Espn.M_KIND])) { first = m; break; }
        assertEquals("12'", first[Espn.M_MINUTE]);
        assertEquals("home", first[Espn.M_SIDE]);
        assertEquals("86.1", first[Espn.M_X]);
        assertEquals("100.0", first[Espn.M_X2].startsWith("100") ? "100.0" : first[Espn.M_X2]);
    }
}
