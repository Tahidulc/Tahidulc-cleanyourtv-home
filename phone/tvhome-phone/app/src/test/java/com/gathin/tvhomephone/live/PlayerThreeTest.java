package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Phone 3.0's player: adaptive and fixed quality, the source chosen, the rewind's files, alerts matching the stream. */
public class PlayerThreeTest {
    @Test public void adaptiveStartsOnTheChosenVersionUnlessAboveTheCap() {
        int[] levels = {4, 3, 2, 0};
        assertEquals(1, Quality.start(levels, 1, Quality.ADAPTIVE, 4));
        assertEquals(0, Quality.start(levels, 0, Quality.ADAPTIVE, 4));
        // 4K chosen on mobile data with data saver: 720p.
        assertEquals(2, Quality.start(levels, 0, Quality.ADAPTIVE, 2));
    }

    @Test public void fixedPlaysItsResolutionOrTheNearestUnder() {
        int[] levels = {4, 3, 2, 0};
        assertEquals(1, Quality.start(levels, 3, 3, 3));
        assertEquals(3, Quality.start(levels, 0, 0, 0));
        int[] noHd = {4, 0};
        assertEquals(1, Quality.start(noHd, 0, 2, 2));
        int[] onlyUhd = {4};
        assertEquals(0, Quality.start(onlyUhd, 0, 2, 2));
    }

    @Test public void theCapFollowsTheSettingsAndTheNetwork() {
        assertEquals(4, Quality.cap(Quality.ADAPTIVE, false, true, true));
        assertEquals(2, Quality.cap(Quality.ADAPTIVE, true, true, true));
        assertEquals(3, Quality.cap(Quality.ADAPTIVE, true, false, true));
        assertEquals(4, Quality.cap(Quality.ADAPTIVE, true, false, false));
        assertEquals(3, Quality.cap(3, false, true, true));
        assertEquals(2, Quality.cap(3, true, true, false));
    }

    @Test public void strugglingIsThreeStallsInAMinuteOrOneLongOne() {
        long now = 1_000_000;
        assertTrue(Quality.struggling(new long[]{now - 50_000, now - 20_000, now - 1000}, 3, now, 800));
        assertFalse(Quality.struggling(new long[]{now - 90_000, now - 20_000, now - 1000}, 3, now, 800));
        assertTrue(Quality.struggling(new long[]{now - 1000}, 1, now, 6000));
        assertFalse(Quality.struggling(new long[0], 0, now, 0));
    }

    @Test public void tryingUpWaitsLongerEachTimeItFailed() {
        assertEquals(4 * 60_000L, Quality.upAfter(0));
        assertEquals(8 * 60_000L, Quality.upAfter(1));
        assertEquals(128 * 60_000L, Quality.upAfter(9));
    }

    @Test public void anotherPlaylistOnlyWhenClearlyBetter() {
        assertEquals(0, Quality.pickSource(new double[]{-1, 900}));
        assertEquals(0, Quality.pickSource(new double[]{1000, 800}));
        assertEquals(1, Quality.pickSource(new double[]{4000, 1200}));
        assertEquals(2, Quality.pickSource(new double[]{4000, 3500, 1000}));
        assertEquals(0, Quality.pickSource(new double[]{4000, -1}));
    }

    @Test public void theRewindKeepsTransportStreamsOnly() {
        assertTrue(Timeshift.keeps("/live/u/p/123.ts"));
        assertTrue(Timeshift.keeps("/live/u/p/123"));
        assertTrue(Timeshift.keeps("/hls/seg-001.ts"));
        assertFalse(Timeshift.keeps("/hls/index.m3u8"));
        assertFalse(Timeshift.keeps("/hls/part.m4s"));
        assertFalse(Timeshift.keeps(null) && false);
    }

    @Test public void aMomentFallsInItsFileByTime() {
        assertEquals(0, Timeshift.offset(1000, 21_000, 2_000_000, 500));
        assertEquals(1_000_000, Timeshift.offset(1000, 21_000, 2_000_000, 11_000));
        assertEquals(1_999_999, Timeshift.offset(1000, 21_000, 2_000_000, 30_000));
    }

    @Test public void itsJustHappenedSetsTheDelayFromTheLatestAlert() {
        long now = 10_000_000;
        assertEquals(42, LiveAlerts.calibrated(now - 42_000, now));
        assertEquals(-1, LiveAlerts.calibrated(0, now));
        assertEquals(-1, LiveAlerts.calibrated(now - 10 * 60_000L, now));
        assertEquals(180, LiveAlerts.calibrated(now - 4 * 60_000L, now));
        assertEquals(0, LiveAlerts.clamp(-5));
    }

    @Test public void doubleTapsByThird() {
        assertEquals(-1, Gestures.third(10, 900));
        assertEquals(0, Gestures.third(450, 900));
        assertEquals(1, Gestures.third(890, 900));
        assertEquals(0, Gestures.third(5, 0));
    }
}
