package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** InStep: the wait OK at kick-off sets, and the game watched found by its channel. */
public class InStepTest {

    @Test
    public void theWaitIsHowFarThePictureIsBehindTheScores() {
        // The poll saw kick-off at 15:00:20; OK as it showed on screen 38 s later.
        assertEquals(38_000L, InStep.hold(1_000_000L, 1_038_000L));
        // The picture ahead of the scores: no wait at all.
        assertEquals(0L, InStep.hold(1_000_000L, 999_000L));
        // Never more than the most a stream is behind.
        assertEquals(InStep.MAX_MS, InStep.hold(1_000_000L, 1_000_000L + InStep.MAX_MS + 30_000L));
    }

    @Test
    public void noKickOffToGoByMeansNothingSet() {
        assertEquals(-1L, InStep.hold(0, 5_000_000L));
        assertEquals(-1L, InStep.hold(1_000_000L, 1_000_000L + InStep.REFERENCE_MS + 1));
    }

    @Test
    public void aChannelIsFoundWhateverItsPictureTag() {
        assertTrue(InStep.onChannel(Arrays.asList("Sky Sports Main Event UHD", "Sky Sports Main Event"),
                Collections.singletonList("Sky Sports Main Event")));
        assertTrue(InStep.onChannel(Collections.singletonList("UK: TNT Sports 1 HD"), Collections.singletonList("TNT Sports 1")));
        assertFalse(InStep.onChannel(Collections.singletonList("Sky Sports Premier League"),
                Collections.singletonList("Sky Sports Main Event")));
        assertFalse(InStep.onChannel(Collections.emptyList(), Collections.singletonList("Sky Sports Main Event")));
        assertFalse(InStep.onChannel(null, null));
    }

    @Test
    public void channelKeysAreLettersAndDigits() {
        assertEquals("skysportsmaineventuhd", InStep.key("Sky Sports Main Event UHD"));
        assertEquals("skysportsmainevent", InStep.base("Sky Sports Main Event 4K"));
        assertEquals("tntsports1", InStep.base("UK: TNT Sports 1 FHD"));
        assertEquals("", InStep.key(null));
    }

    @Test
    public void waitsInWords() {
        assertEquals("38 s", InStep.words(38_000L));
        assertEquals("1 min", InStep.words(60_000L));
        assertEquals("1 min 5 s", InStep.words(65_000L));
        assertEquals(0L, InStep.clamp(-5_000L));
    }
}
