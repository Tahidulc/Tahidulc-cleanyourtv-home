package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** What the plates over the channel's pop-outs write, and how a plate is matched with the one before. */
public class BugCoverTest {
    @Test
    public void wordsAreTidiedIntoCapitals() {
        assertEquals("GOAL OKAFOR 33'", BugCover.words(Collections.singletonList("Goal  Okafor 33'")));
        assertEquals("ADDED TIME: +4", BugCover.words(Collections.singletonList("Added time: +4")));
        assertEquals("POSSESSION\n54% 46%", BugCover.words(Arrays.asList("Possession", "54% 46%")));
    }

    @Test
    public void atMostTwoLinesAndNotTooLong() {
        String w = BugCover.words(Arrays.asList("one line here", "two lines here", "three lines here"));
        assertEquals(2, w.split("\n").length);
        String long1 = BugCover.words(Collections.singletonList("a very long line read off a graphic that goes on and on and on"));
        assertTrue(long1.length() <= 42);
        assertTrue(long1.endsWith("…"));
    }

    @Test
    public void noBroadcastersNamesAndNoNoise() {
        assertNull(BugCover.words(Arrays.asList("SKY SPORTS", "tnt sports", "|", ".")));
        assertEquals("YELLOW CARD", BugCover.words(Arrays.asList("Sky Sports Premier League", "Yellow card")));
        assertNull(BugCover.words(null));
        assertNull(BugCover.words(Collections.emptyList()));
        // A line with letters stuck to a mark is kept, the mark gone.
        assertEquals("SUB ON SMITH", BugCover.words(Collections.singletonList("Sub on ▲ Smith")));
    }

    @Test
    public void platesAreMatchedByOverlap() {
        assertEquals(1f, BugCover.overlap(new int[]{0, 0, 10, 10}, new int[]{0, 0, 10, 10}), 1e-6f);
        assertEquals(0f, BugCover.overlap(new int[]{0, 0, 10, 10}, new int[]{20, 20, 30, 30}), 1e-6f);
        assertEquals(0.5f, BugCover.overlap(new int[]{0, 0, 10, 10}, new int[]{0, 0, 10, 5}), 1e-6f);
    }
}
