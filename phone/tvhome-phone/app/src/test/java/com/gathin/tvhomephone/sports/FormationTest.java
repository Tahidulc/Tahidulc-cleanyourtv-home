package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class FormationTest {
    /** The rows as surnames, each row left to right as the side sees it. */
    private static List<String> rows(String formation, List<String[]> lineup) {
        List<String> out = new ArrayList<>();
        for (List<Integer> row : Formation.rows(formation, lineup)) {
            StringBuilder b = new StringBuilder();
            for (int i : row) b.append(b.length() == 0 ? "" : " ").append(lineup.get(i)[Espn.P_SHORT]);
            out.add(b.toString());
        }
        return out;
    }

    @Test public void dortmundBeforeKickOffByTheirPlaces() throws Exception {
        Espn.Summary s = SummaryFixtures.dortmund();
        List<String> r = rows(s.homeFormation, s.homeLineup);
        assertEquals("Kobel", r.get(0));
        assertEquals("Bensebaini Schlotterbeck Anton", r.get(1));
        assertEquals("Svensson Nmecha Veerman Ryerson", r.get(2));
        assertEquals("Silva Bellingham", r.get(3));
        assertEquals("Guirassy", r.get(4));
        assertEquals(5, r.size());
    }

    @Test public void betisFourTwoThreeOne() throws Exception {
        Espn.Summary s = SummaryFixtures.dortmund();
        List<String> r = rows(s.awayFormation, s.awayLineup);
        assertEquals("4-2-3-1", s.awayFormation);
        assertEquals("Valles", r.get(0));
        assertEquals("Gómez Natan Llorente Ortiz", r.get(1));
        assertEquals("Bernal Deossa", r.get(2));
        assertEquals("Ezzalzouli Ceballos Antony", r.get(3));
        assertEquals("Parrott", r.get(4));
    }

    @Test public void afterTheGameByTheirOwnPositions() throws Exception {
        Espn.Summary s = SummaryFixtures.aek();
        List<String> r = rows(s.homeFormation, s.homeLineup);
        assertEquals("Brignoli", r.get(0));
        assertEquals("Pilios Filipe Relvas Moukoudi Rota", r.get(1));
        assertEquals("Majer Kairinen Arriaga Koita", r.get(2));
        assertEquals("Gacinovic Jovic", r.get(3));
    }

    @Test public void unknownFormationGoesByDepth() {
        List<String[]> l = new ArrayList<>();
        String[][] ps = {{"1", "G", "1"}, {"2", "D", "2"}, {"3", "D", "3"}, {"4", "M", "4"}, {"5", "F", "5"}};
        for (String[] p : ps) {
            l.add(new String[]{p[0], "P" + p[0], p[1], "1", p[2], "P" + p[0], "", "", "0", "", "0"});
        }
        assertEquals(4, Formation.rows("9-9", l).size());
        assertNull(Formation.counts("4-4-2", 9));
    }
}
