package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SubsPageTest {
    private static String[] sub(String minute, String home, String on, String off) {
        return new String[]{"Substitution", minute, home, on, "1", off, "2"};
    }

    @Test public void aClubsChangesInTheSameMinuteComeTogether() {
        List<List<String[]>> g = SubsPage.group(Arrays.asList(
                sub("60", "1", "A On", "A Off"), sub("60", "1", "B On", "B Off"),
                sub("60", "0", "C On", "C Off"), sub("61", "1", "D On", "D Off"), sub("60", "1", "E On", "E Off")));
        assertEquals(3, g.size());
        // The home side's three at 60' together, in the order they came; the away side's alone; the 61st minute's alone.
        assertEquals(3, g.get(0).size());
        assertEquals("E On", g.get(0).get(2)[3]);
        assertEquals("C On", g.get(1).get(0)[3]);
        assertEquals("D On", g.get(2).get(0)[3]);
    }

    @Test public void moreThanFiveAtOnceComeInASecondAlert() {
        List<String[]> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) six.add(sub("90", "1", "On " + i, "Off " + i));
        List<List<String[]>> g = SubsPage.group(six);
        assertEquals(2, g.size());
        assertEquals(SubsPage.MOST, g.get(0).size());
        assertEquals(1, g.get(1).size());
    }

    @Test public void oneSubstitutionIsAnOffPageThenAnOnPageWithWholeNames() {
        SubsPage.Subs s = SubsPage.of(Arrays.<String[]>asList(sub("67", "1", "Gabriel Martinelli", "Leandro Trossard")));
        assertEquals(2, s.pages());
        assertTrue(s.offPage(0));
        assertFalse(s.offPage(1));
        assertEquals("Leandro Trossard", s.off[0]);
        assertEquals("Gabriel Martinelli", s.on[0]);
        assertEquals("67", s.minute);
        assertEquals(1, s.onFaces.length);
        assertEquals(1, s.offFaces.length);
        assertEquals("1", s.onIds[0]);
        assertEquals("2", s.offIds[0]);
    }

    @Test public void severalAtOnceAreEveryoneOnOnePageBySurname() {
        SubsPage.Subs s = SubsPage.of(Arrays.asList(sub("75", "0", "Virgil van Dijk", "Kevin De Bruyne"),
                sub("75", "0", "Mohamed Salah", "Bukayo Saka"), sub("75", "0", "Pedro", "Cole Palmer")));
        assertEquals(2, s.pages());
        assertEquals(Arrays.asList("Virgil van Dijk", "Mohamed Salah", "Pedro"), Arrays.asList(s.onFull));
        assertEquals(Arrays.asList("van Dijk", "Salah", "Pedro"), Arrays.asList(s.on));
        assertEquals(Arrays.asList("De Bruyne", "Saka", "Palmer"), Arrays.asList(s.off));
        assertEquals(3, s.onFaces.length);
        assertEquals(3, s.offFaces.length);
    }

    @Test public void noOneKnownGoingOffLeavesTheOnPageAlone() {
        SubsPage.Subs s = SubsPage.of(Arrays.<String[]>asList(sub("80", "1", "Ethan Nwaneri", "")));
        assertEquals(1, s.pages());
        assertFalse(s.offPage(0));
        assertEquals("Ethan Nwaneri", s.on[0]);
    }

    @Test public void initialsForAPlayerWithNoPicture() {
        assertEquals("GM", SubsPage.initials("Gabriel Martinelli"));
        assertEquals("VD", SubsPage.initials("Virgil van Dijk"));
        assertEquals("TA", SubsPage.initials("Trent Alexander-Arnold"));
        assertEquals("P", SubsPage.initials("Pedro"));
        assertEquals("EP", SubsPage.initials("E. Player"));
        assertEquals("", SubsPage.initials(null));
    }

    @Test public void theAlertTurnsThroughOffAndOn() {
        com.gathin.tvhomephone.sports.Game g = new com.gathin.tvhomephone.sports.Game();
        g.id = "1";
        g.homeName = "Arsenal";
        g.awayName = "Chelsea";
        ScoreBoard.Drop d = ScoreBoard.subDrop(g, Arrays.asList(sub("70", "1", "Gabriel Jesus", "Kai Havertz"), sub("70", "1", "Ethan Nwaneri", "Martin Odegaard")));
        assertNotNull(d.subs);
        assertEquals(2, BoardDrop.pages(d));
        assertEquals(1, d.side);
        assertEquals(Arrays.asList("Jesus", "Nwaneri"), Arrays.asList(d.subs.on));
        assertEquals(Arrays.asList("Havertz", "Odegaard"), Arrays.asList(d.subs.off));
        assertTrue(d.line.startsWith("On Gabriel Jesus, Ethan Nwaneri"));
    }
}
