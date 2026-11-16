package com.gathin.homefieldtv.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** A dropped channel as Homefield TV comes: it stays on the channel and connects it again; a backup copy only when asked. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StreamDropTest {
    @Test public void stayingOnTheChannelIsTheDefault() {
        Context c = RuntimeEnvironment.getApplication();
        assertFalse("never another copy unless Settings say so", PlayerPrefs.switchToBackup(c));
        assertEquals("keeps connecting the same stream", -1, PlayerPrefs.retries(c));
        assertEquals(PlayerPrefs.QUALITY_AUTO, PlayerPrefs.quality(c));
        assertTrue(PlayerPrefs.hlsFirst(c));
        assertEquals(PlayerPrefs.NORMAL, PlayerPrefs.buffer(c, true));
        assertEquals(PlayerPrefs.NORMAL, PlayerPrefs.buffer(c, false));

        PlayerPrefs.setSwitchToBackup(c, true);
        assertTrue(PlayerPrefs.switchToBackup(c));
        PlayerPrefs.setSwitchToBackup(c, false);
        assertFalse(PlayerPrefs.switchToBackup(c));
    }

    @Test public void aPlaylistWhoseHlsFailedGoesBackToMpegTs() {
        Context c = RuntimeEnvironment.getApplication();
        assertFalse(Adaptive.hlsFailed(c, "p2"));
        Adaptive.markHlsFailed(c, "p2");
        assertTrue(Adaptive.hlsFailed(c, "p2"));
        // Only that playlist.
        assertFalse(Adaptive.hlsFailed(c, "p0"));
    }

    @Test public void aPlaylistsOwnUserAgent() {
        Context c = RuntimeEnvironment.getApplication();
        assertEquals("the channel's own when none is set", "Own/1.0", PlayerPrefs.agent(c, "p2:x:1", "Own/1.0"));
        PlayerPrefs.setAgentFor(c, "p2", "Set/2.0");
        assertEquals("Set/2.0", PlayerPrefs.agent(c, "p2:x:1", "Own/1.0"));
        // The first playlist's channels have no prefix.
        assertEquals("Own/1.0", PlayerPrefs.agent(c, "x:1", "Own/1.0"));
    }
}
