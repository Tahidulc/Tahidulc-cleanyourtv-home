package com.gathin.homefieldtv.prefs;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.audio.ChannelMixingMatrix;

import com.gathin.homefieldtv.engine.Channel;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Settings → Player, Sound and Stream problems: the buffer presets, the picture's shapes, the refresh rates, the mixing. */
public class PlayerSettingsTest {
    @Test public void everyBufferPresetIsOneThePlayerTakes() {
        for (boolean live : new boolean[]{true, false}) {
            int[] last = null;
            for (int s = PlayerPrefs.MINIMAL; s <= PlayerPrefs.VERY_LARGE; s++) {
                int[] b = PlayerPrefs.bufferMs(live, s);
                // DefaultLoadControl's rules: the least kept at least what it starts and starts again with; most >= least.
                assertTrue(b[0] >= b[2] && b[0] >= b[3] && b[1] >= b[0] && b[2] > 0);
                if (last != null) assertTrue("bigger each step", b[1] >= last[1] && b[0] >= last[0]);
                last = b;
            }
        }
        // As Homefield came: Normal is the old live and film buffers.
        assertArrayEquals(new int[]{15_000, 50_000, 1_000, 2_000}, PlayerPrefs.bufferMs(true, PlayerPrefs.NORMAL));
        assertArrayEquals(new int[]{20_000, 60_000, 1_500, 3_000}, PlayerPrefs.bufferMs(false, PlayerPrefs.NORMAL));
        // Films have no Minimal: it is Small for them.
        assertArrayEquals(PlayerPrefs.bufferMs(false, PlayerPrefs.SMALL), PlayerPrefs.bufferMs(false, PlayerPrefs.MINIMAL));
    }

    @Test public void thePicturesShapes() {
        // A 4:3 picture on a 16:9 screen: Fit with bars at the sides, Fill stretched, Zoom cut top and bottom.
        assertArrayEquals(new int[]{240, 0, 1440, 1080}, Picture.place(PlayerPrefs.FIT, 4, 3, 0, 0, 1920, 1080));
        assertArrayEquals(new int[]{0, 0, 1920, 1080}, Picture.place(PlayerPrefs.FILL, 4, 3, 0, 0, 1920, 1080));
        assertArrayEquals(new int[]{0, -180, 1920, 1440}, Picture.place(PlayerPrefs.ZOOM, 4, 3, 0, 0, 1920, 1080));
        // A 16:9 picture shown as 4:3, and a 4:3 one shown as 16:9.
        assertArrayEquals(new int[]{240, 0, 1440, 1080}, Picture.place(PlayerPrefs.STANDARD, 16, 9, 0, 0, 1920, 1080));
        assertArrayEquals(new int[]{0, 0, 1920, 1080}, Picture.place(PlayerPrefs.WIDE, 4, 3, 0, 0, 1920, 1080));
        // A cinema-wide film fitted: bars top and bottom.
        assertArrayEquals(new int[]{0, 138, 1920, 803}, Picture.place(PlayerPrefs.FIT, 2390, 1000, 0, 0, 1920, 1080));
        // Not known yet: the whole space.
        assertArrayEquals(new int[]{10, 20, 300, 200}, Picture.place(PlayerPrefs.FIT, 0, 0, 10, 20, 300, 200));
    }

    @Test public void theTvsRefreshRateForTheProgramme() {
        float[] rates = {60f, 59.94f, 50f, 30f, 25f, 24f, 23.976f};
        // A UK channel's 25 frames: 25 Hz when the TV has it (the lowest whole multiple), else 50.
        assertEquals(4, Afr.pick(25f, rates));
        assertEquals(1, Afr.pick(50f, new float[]{60f, 50f}));
        assertEquals(1, Afr.pick(25f, new float[]{60f, 50f}));
        // A film's 23.976: 23.976 rather than 24; 24 when the TV has no 23.976.
        assertEquals(6, Afr.pick(23.976f, rates));
        assertEquals(1, Afr.pick(23.976f, new float[]{60f, 24f}));
        assertEquals(1, Afr.pick(59.94f, new float[]{60f, 59.94f}));
        // Nothing fits, or no rate known.
        assertEquals(-1, Afr.pick(25f, new float[]{60f, 30f}));
        assertEquals(-1, Afr.pick(0f, rates));
    }

    @Test public void framesASecondFromTheFramesTimes() {
        long[] t = new long[24];
        for (int i = 0; i < t.length; i++) t[i] = 1_000_000L + i * 40_000L;
        assertEquals(25f, Afr.fpsFrom(t), 0.001f);
        for (int i = 0; i < t.length; i++) t[i] = i * 41_708L;
        assertEquals(23.976f, Afr.fpsFrom(t), 0.001f);
        assertEquals(0f, Afr.fpsFrom(new long[]{0, 1}), 0f);
    }

    @Test public void surroundToTwoSpeakers() {
        ChannelMixingMatrix six = PlayerSetup.toStereo(6);
        assertEquals(6, six.getInputChannelCount());
        assertEquals(2, six.getOutputChannelCount());
        // Front left only to the left; the centre to both; the low frequencies to neither; the back pairs to their side.
        assertTrue(six.getMixingCoefficient(0, 0) > 0f);
        assertEquals(0f, six.getMixingCoefficient(0, 1), 0f);
        assertTrue(six.getMixingCoefficient(2, 0) > 0f && six.getMixingCoefficient(2, 1) > 0f);
        assertEquals(0f, six.getMixingCoefficient(3, 0), 0f);
        assertTrue(six.getMixingCoefficient(4, 0) > 0f);
        assertEquals(0f, six.getMixingCoefficient(4, 1), 0f);
        // Never louder than full on either speaker.
        for (int n = 1; n <= 8; n++) {
            ChannelMixingMatrix m = PlayerSetup.toStereo(n);
            for (int out = 0; out < 2; out++) {
                float sum = 0f;
                for (int in = 0; in < n; in++) sum += m.getMixingCoefficient(in, out);
                assertTrue("n=" + n, sum <= 1.0001f && sum > 0f);
            }
        }
        // Stereo stays as it is.
        assertTrue(PlayerSetup.toStereo(2).isIdentity());
    }

    @Test public void soundAndStreamWords() {
        assertEquals("In step", PlayerPrefs.delayWords(0));
        assertEquals("Sound 150 ms later", PlayerPrefs.delayWords(150));
        assertEquals("Sound 100 ms earlier", PlayerPrefs.delayWords(-100));
        List<String> order = Arrays.asList("en", "bn");
        assertEquals(Arrays.asList("en", "bn", "ar"), PlayerPrefs.toggleLanguage(order, "ar"));
        assertEquals(Collections.singletonList("bn"), PlayerPrefs.toggleLanguage(order, "en"));
        // The last one can't go.
        assertEquals(Collections.singletonList("en"), PlayerPrefs.toggleLanguage(Collections.singletonList("en"), "en"));
        assertEquals("p0", PlayerPrefs.playlistOf("x:123"));
        assertEquals("p2", PlayerPrefs.playlistOf("p2:x:123"));
        assertEquals(2, PlayerPrefs.indexOf(PlayerPrefs.RETRY_COUNTS, 4));
        assertEquals(4, PlayerPrefs.indexOf(PlayerPrefs.RETRY_COUNTS, -1));
    }

    @Test public void reconnectingWaitsLongerEachTime() {
        assertEquals(1000L, PlayerPrefs.backoffMs(1));
        assertEquals(2000L, PlayerPrefs.backoffMs(2));
        assertEquals(4000L, PlayerPrefs.backoffMs(3));
        assertEquals(8000L, PlayerPrefs.backoffMs(4));
        // Then 8 s apart for as long as it keeps trying, never more (and no overflow).
        assertEquals(8000L, PlayerPrefs.backoffMs(5));
        assertEquals(8000L, PlayerPrefs.backoffMs(100_000));
        assertEquals(1000L, PlayerPrefs.backoffMs(0));
    }

    @Test public void theProvidersHlsFormOfAnMpegTsChannel() {
        assertEquals("http://tv.example.invalid:8080/live/user/pass/12345.m3u8",
                Adaptive.hlsFor("http://tv.example.invalid:8080/live/user/pass/12345.ts"));
        assertEquals("https://tv.example.invalid/live/user/pass/12345.m3u8?token=a",
                Adaptive.hlsFor("https://tv.example.invalid/live/user/pass/12345.ts?token=a"));
        // Already HLS, a film, an address of another shape, nothing: left as they are.
        assertEquals(null, Adaptive.hlsFor("http://tv.example.invalid/live/user/pass/12345.m3u8"));
        assertEquals(null, Adaptive.hlsFor("http://tv.example.invalid/movie/user/pass/9.ts"));
        assertEquals(null, Adaptive.hlsFor("http://tv.example.invalid/user/pass/12345"));
        assertEquals(null, Adaptive.hlsFor("http://tv.example.invalid/channel/12345.ts"));
        assertEquals(null, Adaptive.hlsFor(null));
        // The provider refusing or not having it sets the playlist back to MPEG-TS; a dropped connection doesn't.
        assertTrue(Adaptive.notOffered(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS));
        assertTrue(Adaptive.notOffered(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED));
        assertFalse(Adaptive.notOffered(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED));
        assertFalse(Adaptive.notOffered(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT));
    }

    @Test public void backupCopiesBestPictureFirst() {
        Channel hdOther = new Channel("p2:x:1", "UK: Harbour Sports 1 HD", "UK Sports", "", "http://example.invalid/a", "", "", "", 101);
        Channel sdOther = new Channel("p3:x:1", "UK: Harbour Sports 1 SD", "UK Sports", "", "http://example.invalid/b", "", "", "", 101);
        Channel fhdGroup = new Channel("x:7", "UK: Harbour Sports 1 FHD", "UK Sports Backup", "", "http://example.invalid/c", "", "", "", 907);
        Channel hdGroup = new Channel("x:8", "UK: Harbour Sports 1 HD", "UK Sports Backup", "", "http://example.invalid/d", "", "", "", 908);
        // Each once (one found both ways), the best picture first, equal pictures in their order.
        assertEquals(Arrays.asList(fhdGroup, hdOther, hdGroup, sdOther),
                Adaptive.bestFirst(Arrays.asList(hdOther, sdOther), Arrays.asList(fhdGroup, hdGroup, hdOther)));
        assertEquals(Collections.emptyList(), Adaptive.bestFirst(null, null));
    }

    @Test public void backupCopiesInOtherGroups() {
        Channel main = new Channel("x:1", "UK: Harbour Sports 1 HD", "UK Sports", "", "http://example.invalid/1", "", "", "", 101);
        Channel backup = new Channel("x:2", "UK: Harbour Sports 1 HD", "UK Sports Backup", "", "http://example.invalid/2", "", "", "", 901);
        Channel sd = new Channel("x:3", "UK: Harbour Sports 1 SD", "UK Sports SD", "", "http://example.invalid/3", "", "", "", 902);
        Channel other = new Channel("x:4", "UK: Harbour Sports 2 HD", "UK Sports", "", "http://example.invalid/4", "", "", "", 102);
        List<Channel> found = Backups.more(Arrays.asList(main, other, sd, backup), main);
        // The same picture first, then another; never itself or another channel.
        assertEquals(Arrays.asList(backup, sd), found);
    }
}
