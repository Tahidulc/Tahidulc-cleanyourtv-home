package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/**
 * Where a programme's picture comes from, in both apps (PicSources): the order, the chain of what was found with one
 * always there at its end, a game's picture from its title, and what a programme is for its kind's picture. Invented
 * channels and programmes; nothing is fetched.
 */
public class PicSourcesTest {
    @Test public void theOrderIsTheGuidesOwnTheCataloguesTheEventTheTitleThenTheDrawnLastResorts() {
        assertEquals(Arrays.asList("epg", "catalogues", "event", "title", "fixture", "genre", "channel"), PicSources.ORDER);
    }

    @Test public void aChainKeepsWhatWasFoundBestFirstOnceEach() {
        String epg = "https://epg.invalid/p/1.jpg", sky = "https://img.invalid/pd-image/abc/16-9/640";
        String c = PicSources.chain(epg, null, "", sky, epg, PicSources.genre(PicSources.NEWS));
        assertTrue(PicSources.isChain(c));
        assertEquals(Arrays.asList(epg, sky, "genre:news"), PicSources.parts(c));
        // One address alone is that address; nothing is "".
        assertEquals(sky, PicSources.chain(null, sky));
        assertFalse(PicSources.isChain(sky));
        assertEquals("", PicSources.chain(null, "", null));
        assertEquals(Arrays.asList(sky), PicSources.parts(sky));
        assertTrue(PicSources.parts("").isEmpty());
        // A chain in a chain is flattened.
        assertEquals(Arrays.asList(epg, sky, "genre:news"), PicSources.parts(PicSources.chain(PicSources.chain(epg, sky), "genre:news")));
        assertEquals(epg, PicSources.firstReal(PicSources.chain("genre:film", epg)));
    }

    @Test public void theMadeUpPicturesCarryWhatTheyAreDrawnFrom() {
        String f = PicSources.fixture("https://crest.invalid/1.png", "", 0xFFB71C1C, 0xFF0D47A1);
        assertTrue(PicSources.madeUp(f) && !PicSources.isChain(f));
        assertArrayEquals(new String[]{"https://crest.invalid/1.png", ""}, PicSources.fixtureCrests(f));
        assertArrayEquals(new int[]{0xFFB71C1C, 0xFF0D47A1}, PicSources.fixtureColours(f));
        assertEquals(PicSources.FOOTBALL, PicSources.genreOf(PicSources.genre(PicSources.FOOTBALL)));
        assertNull(PicSources.genreOf(f));
        assertEquals(0xFF1B4F8C, PicSources.brandColour(PicSources.brand(0xFF1B4F8C)));
        assertFalse(PicSources.madeUp("https://img.invalid/a.jpg"));
        // The same channel's name always the same colour.
        assertEquals(PicSources.nameColour("Harbour One"), PicSources.nameColour("harbour one"));
    }

    @Test public void aGamesSidesFromItsTitle() {
        assertArrayEquals(new String[]{"Arsenal", "Chelsea"}, PicSources.sides("Live: Arsenal v Chelsea"));
        assertArrayEquals(new String[]{"Arsenal", "Chelsea"}, PicSources.sides("Premier League: Arsenal v Chelsea"));
        assertArrayEquals(new String[]{"Leeds United", "Sheffield Wednesday"}, PicSources.sides("Live EFL: Leeds United vs Sheffield Wednesday (Live)"));
        assertArrayEquals(new String[]{"Northbridge", "Eastfield"}, PicSources.sides("Northbridge v Eastfield - Highlights"));
        assertNull(PicSources.sides("The Long Tide"));
        assertNull(PicSources.sides("Kramer v Kramer v Kramer"));
        assertNull(PicSources.sides("Live: 3 v 3"));
    }

    @Test public void aGameOfClubsTheAppsKnowIsTheirCrestsOnTheirColours() {
        String f = PicSources.fixtureOf("Live: Arsenal v Chelsea", true);
        assertNotNull(f);
        String[] crests = PicSources.fixtureCrests(f);
        assertTrue(crests[0].endsWith("/359.png") && crests[1].endsWith("/363.png"));
        // One club known: on a sports channel only; none known, or a film's "v": none.
        assertNotNull(PicSources.fixtureOf("Northbridge v Liverpool", true));
        assertNull(PicSources.fixtureOf("Northbridge v Liverpool", false));
        assertNull(PicSources.fixtureOf("Northbridge v Eastfield", true));
        assertNull(PicSources.fixtureOf("Kramer vs Kramer", false));
    }

    @Test public void whatAProgrammeIsFromItsWordsItsCategoryAndItsChannel() {
        assertEquals(PicSources.FOOTBALL, PicSources.kindOf("Riverside Sport 1 HD", "UK | Sport", "Live: Arsenal v Chelsea", "", ""));
        assertEquals(PicSources.MOTOR, PicSources.kindOf("Riverside Sport F1", "UK | Sport", "Live Formula 1: Practice", "", ""));
        assertEquals(PicSources.SNOOKER, PicSources.kindOf("Riverside Sport 2", "UK | Sport", "Live Snooker: Final", "", ""));
        assertEquals(PicSources.TENNIS, PicSources.kindOf("Harbour One", "UK | Entertainment", "Wimbledon Highlights", "", ""));
        assertEquals(PicSources.SPORT, PicSources.kindOf("Riverside Sport 1 HD", "UK | Sport", "Sportscene Tonight", "", ""));
        assertEquals(PicSources.NEWS, PicSources.kindOf("Brightwater News", "UK | News", "Evening Headlines", "", ""));
        assertEquals(PicSources.WEATHER, PicSources.kindOf("Harbour One", "UK | Entertainment", "Weather for the Week Ahead", "", ""));
        assertEquals(PicSources.KIDS, PicSources.kindOf("Kestrel Kids", "UK | Kids", "Pip and the Moon", "", ""));
        assertEquals(PicSources.FILM, PicSources.kindOf("Lumen Cinema FHD", "UK | Movies", "Echoes of Iron (2019)", "", ""));
        assertEquals(PicSources.NATURE, PicSources.kindOf("Meadow Life", "UK | Documentaries", "Secret Gardens", "", ""));
        // The guide's category says what the channel doesn't.
        assertEquals(PicSources.FOOD, PicSources.kindOf("Harbour One", "UK | Entertainment", "Coastal Kitchens", "", " | Cookery"));
        assertEquals(PicSources.STAGE, PicSources.kindOf("Harbour One", "UK | Entertainment", "Quiz at Nine", "", " | Game Show"));
        assertEquals(PicSources.FILM, PicSources.kindOf("Harbour One", "UK | Entertainment", "The Quiet Meridian", "", " | Film | Drama"));
        // Nothing to go by: the channel's own picture.
        assertNull(PicSources.kindOf("Harbour One", "UK | Entertainment", "The Long Tide", "Mara finds the letter.", ""));
        // "goals" in a drama's title on an entertainment channel is not football unless it says so.
        assertNull(PicSources.kindOf("Harbour One", "UK | Entertainment", "Life Goals", "A comedy-drama.", ""));
    }

    @Test public void aCategoryIsKeptAsAByte() {
        byte b = PicSources.categoryCode(" | Sport | Football");
        assertTrue(b != 0);
        assertEquals(PicSources.FOOTBALL, PicSources.categoryWord(b));
        assertEquals(0, PicSources.categoryCode(""));
        assertEquals("", PicSources.categoryWord((byte) 0));
        assertEquals(PicSources.NEWS, PicSources.categoryWord(PicSources.categoryCode("News")));
        assertTrue(PicSources.sporting(PicSources.DARTS) && !PicSources.sporting(PicSources.NEWS));
    }
}
