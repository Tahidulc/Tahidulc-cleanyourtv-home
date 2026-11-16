package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** When the connection drops and the channel is kept: its own stream tried again, waiting a little longer each time. */
public class StayOnChannelTest {
    @Test public void triesAgainSoonerFirstThenEveryTenSeconds() {
        assertEquals(1000L, Playback.retryDelay(1, true));
        assertEquals(2000L, Playback.retryDelay(2, true));
        assertEquals(4000L, Playback.retryDelay(3, true));
        assertEquals(8000L, Playback.retryDelay(4, true));
        assertEquals(10_000L, Playback.retryDelay(5, true));
        assertEquals(10_000L, Playback.retryDelay(30, true));
        // Moving on to backups, as before: never more than eight seconds apart.
        assertEquals(8000L, Playback.retryDelay(5, false));
        // About four and a half minutes of trying before it gives up.
        long total = 0;
        for (int i = 1; i <= Playback.STAY_RETRIES; i++) total += Playback.retryDelay(i, true);
        assertEquals(275_000L, total);
    }
}
