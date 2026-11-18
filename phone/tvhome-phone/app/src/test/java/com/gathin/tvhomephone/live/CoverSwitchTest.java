package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * "Cover the channel's score pop-ups": off, nothing covers what grows out of the channel's own scoreboard, whatever
 * "Away in replays" says; on, it is covered while the channel's own is up (never in its replays). The channel's
 * scoreboard is watched for either switch alone.
 */
public class CoverSwitchTest {
    @Test
    public void offCoversNothingEvenWithAwayInReplaysOn() {
        assertFalse(BugCover.covers(false, false));
        assertFalse(BugCover.covers(true, false));
        assertTrue(BugCover.covers(false, true));
        assertFalse(BugCover.covers(true, true));
        assertTrue(BugCover.wanted(true, false));
        assertTrue(BugCover.wanted(false, true));
        assertFalse(BugCover.wanted(false, false));
    }
}
