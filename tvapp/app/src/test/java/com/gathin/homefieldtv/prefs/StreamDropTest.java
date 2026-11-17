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

/** A dropped channel as Homefield TV comes: Adaptive, staying on the channel picked; a fixed quality only when chosen. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StreamDropTest {
    @Test public void adaptiveIsTheDefault() {
        Context c = RuntimeEnvironment.getApplication();
        assertEquals(PlayerPrefs.QUALITY_ADAPTIVE, PlayerPrefs.quality(c));
        assertTrue(PlayerPrefs.adaptive(PlayerPrefs.quality(c)));
        assertEquals("keeps connecting the same stream", -1, PlayerPrefs.retries(c));
        assertTrue(PlayerPrefs.hlsFirst(c));
        assertEquals(PlayerPrefs.NORMAL, PlayerPrefs.buffer(c, true));
        assertEquals(PlayerPrefs.NORMAL, PlayerPrefs.buffer(c, false));

        // A fixed quality is the way to hold one stream; something unknown is Adaptive again.
        PlayerPrefs.setQuality(c, 3);
        assertEquals(3, PlayerPrefs.quality(c));
        assertFalse(PlayerPrefs.adaptive(PlayerPrefs.quality(c)));
        PlayerPrefs.setQuality(c, 7);
        assertEquals(PlayerPrefs.QUALITY_ADAPTIVE, PlayerPrefs.quality(c));
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
