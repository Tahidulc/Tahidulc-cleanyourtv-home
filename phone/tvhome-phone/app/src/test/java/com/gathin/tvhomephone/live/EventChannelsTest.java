package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.sports.OnTv;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Another sport's event found in the family's channels: an event channel by its name, a guide by its title. */
public class EventChannelsTest {
    private static List<String[][]> finds(String[][]... sets) {
        List<String[][]> out = new ArrayList<>();
        for (String[][] s : sets) out.add(s);
        return out;
    }

    private static boolean channel(String name, List<String[][]> finds) {
        return EventChannels.matches(AlsoShowing.words(name), EventChannels.normalized(finds));
    }

    @Test public void aTeamGameNeedsBothTeams() {
        List<String[][]> game = finds(new String[][]{{"Harbour City Herons", "Herons"}, {"Westfield Wolves", "Wolves"}});
        assertTrue(channel("UK EVENTS 03: Herons v Wolves 01:15", game));
        assertTrue(channel("NFL: Westfield Wolves at Harbour City Herons", game));
        assertFalse(channel("tv1 Sports NFL HD", game));
        assertFalse(channel("NFL: Herons at Rangers", game));
    }

    @Test public void aMatchByItsPlayers() {
        List<String[][]> match = finds(new String[][]{{"Pim Vosloo", "Vosloo"}, {"Arno Kettering", "Kettering"}});
        assertTrue(channel("Live Finals Tennis: Vosloo v Kettering", match));
        assertTrue(channel("|UK| EVENT 12 - PIM VOSLOO VS ARNO KETTERING", match));
        assertFalse(channel("Live Finals Tennis: Vosloo v Barnaby", match));
    }

    @Test public void aGrandPrixByItsPlaceAndFormulaOne() {
        List<String[][]> gp = finds(new String[][]{{"Port Albany"}, {"F1", "Formula 1", "Formula One", "Grand Prix"}});
        assertTrue(channel("Formula 1: Port Albany Grand Prix - Race", gp));
        assertTrue(channel("F1 TV: Port Albany", gp));
        // The sport's own channel alone isn't named for this race (it is listed for it another way).
        assertFalse(channel("tv1 Sports F1 HD", gp));
        assertFalse(channel("Port Albany Tonight", gp));
    }

    @Test public void aNumberedCardByItsNumber() {
        List<String[][]> card = finds(new String[][]{{"Rafe Delacour", "Delacour"}, {"Tomas Brann", "Brann"}}, new String[][]{{"UFC 412"}});
        assertTrue(channel("UFC 412: Main Card", card));
        assertTrue(channel("Live Fight Night: Delacour v Brann", card));
        assertFalse(channel("UFC 411: Main Card", card));
    }

    @Test public void shortWordsAreNotEnough() {
        // A two-letter word alone ("us") finds everything; "F1" and numbers stay.
        List<String[][]> n = EventChannels.normalized(finds(new String[][]{{"US", "F1"}}));
        assertEquals(1, n.size());
        assertArrayEquals(new String[]{"f1"}, n.get(0)[0]);
        assertTrue(EventChannels.normalized(finds(new String[][]{{"US"}})).isEmpty());
        assertTrue(EventChannels.mentions("Herons edge Wolves late on · NFL · Herons", finds(new String[][]{{"Herons"}, {"Wolves"}})));
    }

    @Test public void theSportsOwnChannel() {
        assertEquals("Sky Sports F1", EventChannels.usual(OnTv.F1)[0]);
        assertEquals("Sky Sports Golf", EventChannels.usual(OnTv.GOLF)[0]);
        assertEquals(0, EventChannels.usual("darts").length);
    }

    @Test public void looksAtItsStartUntilItIsOn() {
        com.gathin.tvhomephone.sports.SportEvent e = new com.gathin.tvhomephone.sports.SportEvent();
        e.start = 10_000_000L;
        assertEquals(10_060_000L, EventChannels.when(e, 5_000_000L));
        assertEquals(12_000_000L, EventChannels.when(e, 12_000_000L));
    }
}
