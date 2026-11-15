package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The Lab's channel logos' words: the family's name and SPORTS as each look sets them. */
public class LabMarksTest {
    @Test
    public void fiveLooksEachWithItsName() {
        assertEquals(5, LabMarks.Look.values().length);
        assertEquals("Floodlight", LabMarks.Look.FLOODLIGHT.label);
        assertEquals("Split kit", LabMarks.Look.SPLITKIT.label);
        assertEquals("Ticker", LabMarks.Look.TICKER.label);
        assertEquals("Duo pills", LabMarks.Look.DUO.label);
        assertEquals("Stadium LED", LabMarks.Look.LED.label);
        assertEquals(LabMarks.Look.LED, LabMarks.Look.of("LED"));
        assertNull(LabMarks.Look.of("SIGNATURE"));
        assertNull(LabMarks.Look.of(null));
    }

    @Test
    public void floodlightsHeavyWordIsTheNameOrItsFirstWord() {
        assertEquals("WHITEBROOK", LabMarks.bigName("Whitebrook Road"));
        assertEquals("WHITEBROOK", LabMarks.bigName(null));
        assertEquals("ELM PARK", LabMarks.bigName("Elm Park"));
        assertEquals("HOLLYCROFT", LabMarks.bigName("Hollycroft Avenue"));
    }

    @Test
    public void theSlimLooksSayTheInitialsAndSports() {
        assertEquals("WR", LabMarks.initials("Whitebrook Road"));
        assertEquals("WR", LabMarks.initials(""));
        assertEquals("ELH", LabMarks.initials("elm lane house club"));
        assertEquals("WR SPORTS", LabMarks.slimWords("Whitebrook Road", "Sports"));
        assertEquals("WR SPORTS", LabMarks.slimWords(null, "SPORTS"));
        // A general channel (sports dropped): the whole name, as the initials alone would say little.
        assertEquals("WHITEBROOK ROAD", LabMarks.slimWords("Whitebrook Road", ""));
        assertEquals("ELM PARK", LabMarks.slimWords("Elm Park", null));
    }

    @Test
    public void theFamilysMarkKeepsItsShape() {
        // The house is 52 wide in the Lab's drawing: its strokes for round corners scale with it.
        assertEquals(5f, LabMarks.houseStroke(52f), 0.001f);
        assertEquals(2.6f, LabMarks.playStroke(52f), 0.001f);
        assertTrue(LabMarks.houseStroke(26f) < LabMarks.houseStroke(52f));
        assertEquals(LabMarks.NAME, "Whitebrook Road");
    }
}
