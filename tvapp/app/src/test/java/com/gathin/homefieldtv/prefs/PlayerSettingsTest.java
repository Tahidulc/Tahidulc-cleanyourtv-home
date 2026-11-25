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
        // Live TV's Normal (as it comes): it starts as soon as a second is in, but after a stall it waits for five, so the
        // next wobble doesn't stall it again straight away (it was two); films as they were.
        assertArrayEquals(new int[]{15_000, 50_000, 1_000, 5_000}, PlayerPrefs.bufferMs(true, PlayerPrefs.NORMAL));
        assertArrayEquals(new int[]{20_000, 60_000, 1_500, 3_000}, PlayerPrefs.bufferMs(false, PlayerPrefs.NORMAL));
        // Every live size waits after a stall for at least three times what it starts with (Minimal's half second: 1.5).
        for (int s = PlayerPrefs.MINIMAL; s <= PlayerPrefs.VERY_LARGE; s++) {
            int[] b = PlayerPrefs.bufferMs(true, s);
            assertTrue(PlayerPrefs.BUFFER_NAMES[s], b[3] >= 3 * b[2]);
        }
        // Films have no Minimal: it is Small for them.
        assertArrayEquals(PlayerPrefs.bufferMs(false, PlayerPrefs.SMALL), PlayerPrefs.bufferMs(false, PlayerPrefs.MINIMAL));
    }

    @Test public void theBuffersBytesAsTheTvsMemoryAllows() {
        long mb = 1L << 20;
        // The TCL (a 192 MB heap): live TV's Normal kept at 32 MB, and Large and Very large no higher (a sixth of it).
        assertEquals(32 * mb, PlayerPrefs.bufferBytes(true, PlayerPrefs.NORMAL, 192 * mb));
        assertEquals(32 * mb, PlayerPrefs.bufferBytes(true, PlayerPrefs.LARGE, 192 * mb));
        assertEquals(32 * mb, PlayerPrefs.bufferBytes(true, PlayerPrefs.VERY_LARGE, 192 * mb));
        assertEquals(12 * mb, PlayerPrefs.bufferBytes(true, PlayerPrefs.MINIMAL, 192 * mb));
        // Room to spare: half as much again from 384 MB, twice from 512 MB (a 4K channel's Normal then some 20 seconds).
        assertEquals(48 * mb, PlayerPrefs.bufferBytes(true, PlayerPrefs.NORMAL, 384 * mb));
        assertEquals(64 * mb, PlayerPrefs.bufferBytes(true, PlayerPrefs.NORMAL, 512 * mb));
        assertEquals(512 * mb / 6, PlayerPrefs.bufferBytes(true, PlayerPrefs.VERY_LARGE, 512 * mb));
        // A small heap: never under 8 MB; not known: the size's own.
        assertEquals(8 * mb, PlayerPrefs.bufferBytes(true, PlayerPrefs.NORMAL, 32 * mb));
        assertEquals(PlayerPrefs.bufferBytes(true, PlayerPrefs.NORMAL), PlayerPrefs.bufferBytes(true, PlayerPrefs.NORMAL, 0));
        // Films the same way.
        assertEquals(32 * mb, PlayerPrefs.bufferBytes(false, PlayerPrefs.NORMAL, 192 * mb));
        assertEquals(80 * mb, PlayerPrefs.bufferBytes(false, PlayerPrefs.NORMAL, 1024 * mb));
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
        // Staying on the stream: 1, 2, 4, 8, then 10 s apart for as long as it keeps trying (no overflow).
        long[] stay = {1000, 2000, 4000, 8000, 10_000, 10_000};
        for (int i = 0; i < stay.length; i++) assertEquals(stay[i], Abr.retryDelay(i + 1, true));
        assertEquals(10_000L, Abr.retryDelay(100_000, true));
        // The quick tries before another copy: 1 then 2 s (never more than 8).
        assertEquals(1000L, Abr.retryDelay(1, false));
        assertEquals(2000L, Abr.retryDelay(2, false));
        assertEquals(8000L, Abr.retryDelay(9, false));
        // A try back up: 45 s, then twice as long after each that didn't hold, up to 15 minutes.
        long[] up = {45_000, 90_000, 180_000, 360_000, 720_000, 900_000, 900_000};
        for (int i = 0; i < up.length; i++) assertEquals(up[i], Abr.upAfter(i));
    }

    @Test public void aStepDownBeforeThePictureFreezes() {
        // The buffer draining a second at a time, under 2.5 s: down now.
        assertTrue(Abr.draining(new long[]{5000, 4200, 3500, 2900, 2300}));
        // Steady, growing, still deep, or not much lost: not yet.
        assertFalse(Abr.draining(new long[]{2300, 2300, 2300, 2300, 2300}));
        assertFalse(Abr.draining(new long[]{4000, 3000, 3300, 2600, 2200}));
        assertFalse(Abr.draining(new long[]{7000, 6000, 5000, 4000, 3000}));
        assertFalse(Abr.draining(new long[]{3000, 2900, 2800, 2600, 2400}));
        // A fifth of a second's wobble allowed.
        assertTrue(Abr.draining(new long[]{4000, 3500, 3600, 2800, 2400}));
        // Stalls: three in a minute, or one of 5 s.
        long now = 1_000_000L;
        assertTrue(Abr.struggling(new long[]{now - 50_000, now - 30_000, now - 1000}, 3, now, 1000));
        assertFalse(Abr.struggling(new long[]{now - 90_000, now - 30_000, now - 1000}, 3, now, 1000));
        assertTrue(Abr.struggling(new long[]{now - 1000}, 1, now, 5000));
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

    private static Channel ch(String key, String name, String group) {
        return new Channel(key, name, group, "", "http://example.invalid/" + key.replace(':', '-'), "", "", "", 101);
    }

    @Test public void theChannelsVersions() {
        Channel hd = ch("x:1", "UK: Harbour Sports 1 HD", "UK Sports");
        Channel hdThere = ch("p2:x:1", "UK: Harbour Sports 1 HD", "UK Sports");
        hd.alternates.add(hdThere);
        Channel fhd = ch("x:7", "UK: Harbour Sports 1 FHD", "UK Sports");
        Channel fhdFavourite = ch("x:8", "Harbour Sports 1 FHD", "Sport");
        Channel sd = ch("x:3", "UK: Harbour Sports 1 SD", "UK Sports SD");
        Channel uhd = ch("x:9", "UK: Harbour Sports 1 4K", "UK 4K");
        Channel backupFeed = ch("x:10", "UK: Harbour Sports 1 HD Backup", "UK Sports");
        Channel other = ch("x:4", "UK: Harbour Sports 2 HD", "UK Sports");
        List<Channel> all = Arrays.asList(hd, other, fhd, sd, backupFeed, fhdFavourite, uhd);
        // One version a quality, best first; a favourite first at its quality; never a backup feed or another channel.
        assertEquals(Arrays.asList(uhd, fhdFavourite, hd, sd), Ladder.variants(all, hd, Collections.singleton("x:8")));
        // Without favourites: the one tagged British, else the first found.
        assertEquals(Arrays.asList(uhd, fhd, hd, sd), Ladder.variants(all, hd, Collections.emptySet()));
        // A channel with no other versions: itself.
        assertEquals(Collections.singletonList(other), Ladder.variants(all, other, Collections.emptySet()));
    }

    @Test public void whichVersionPictureQualityPlays() {
        Channel hd = ch("x:1", "UK: Harbour Sports 1 HD", "UK Sports");
        Channel uhd = ch("x:9", "UK: Harbour Sports 1 4K", ""), fhd = ch("x:7", "UK: Harbour Sports 1 FHD", "");
        Channel sd = ch("x:3", "UK: Harbour Sports 1 SD", "");
        List<Channel> v = Arrays.asList(uhd, fhd, hd, sd);
        // Adaptive: the one picked; a fixed quality: its version.
        assertEquals(2, Ladder.of(v, hd, PlayerPrefs.QUALITY_ADAPTIVE).rung());
        assertEquals(0, Ladder.of(v, hd, 4).rung());
        assertEquals(1, Ladder.of(v, hd, 3).rung());
        assertEquals(2, Ladder.of(v, hd, 2).rung());
        assertEquals(3, Ladder.of(v, hd, 0).rung());
        // Data saver: the one picked when it is 720p or under, else the best under 720p.
        assertEquals(2, Ladder.of(v, hd, PlayerPrefs.QUALITY_SAVER).rung());
        assertEquals(2, Ladder.of(v, fhd, PlayerPrefs.QUALITY_SAVER).rung());
        // A fixed quality the channel lacks: the nearest under it, else the lowest.
        assertEquals(0, Ladder.of(Arrays.asList(hd, sd), hd, 3).rung());
        assertEquals(1, Ladder.of(Arrays.asList(uhd, fhd), fhd, 0).rung());
    }

    @Test public void stepsBetweenVersionsAndPlaylists() {
        Channel hd = ch("x:1", "UK: Harbour Sports 1 HD", "UK Sports");
        Channel hdThere = ch("p2:x:1", "UK: Harbour Sports 1 HD", "UK Sports");
        hd.alternates.add(hdThere);
        Channel fhd = ch("x:7", "UK: Harbour Sports 1 FHD", "UK Sports"), sd = ch("x:3", "UK: Harbour Sports 1 SD", "UK Sports");
        Ladder l = Ladder.of(Arrays.asList(fhd, hd, sd), hd, PlayerPrefs.QUALITY_ADAPTIVE);
        assertEquals(hd, l.stream());
        assertFalse(l.canUp());
        // A failure: the same version from the other playlist, then none left there.
        assertTrue(l.nextAlt());
        assertEquals(hdThere, l.stream());
        assertFalse(l.nextAlt());
        // A step down, then back up to the version picked (never above it in Adaptive).
        l.down();
        assertEquals(sd, l.stream());
        assertEquals(Ladder.SD, l.level());
        assertFalse(l.canDown());
        assertTrue(l.canUp());
        l.up();
        assertEquals(hd, l.stream());
        assertFalse(l.canUp());
        // The channel alone: its other playlists' copies still behind it.
        Ladder one = Ladder.single(hd);
        assertTrue(one.nextAlt());
        assertEquals(hdThere, one.stream());
        // Found while it played the other playlist's copy: that stream kept.
        l.at(l.picked(), 1);
        assertEquals(hdThere, l.stream());
    }

    @Test public void theQualityBadgeAndChoices() {
        assertEquals("4K", Ladder.badge(2160, Ladder.HD));
        assertEquals("FHD", Ladder.badge(1080, Ladder.HD));
        assertEquals("HD", Ladder.badge(720, Ladder.FHD));
        assertEquals("SD", Ladder.badge(576, Ladder.FHD));
        assertEquals("FHD", Ladder.badge(0, Ladder.FHD));
        assertEquals("", Ladder.badge(0, Ladder.PLAIN));
        assertTrue(PlayerPrefs.adaptive(PlayerPrefs.QUALITY_ADAPTIVE));
        assertTrue(PlayerPrefs.adaptive(PlayerPrefs.QUALITY_SAVER));
        assertFalse(PlayerPrefs.adaptive(3));
        assertEquals(4, PlayerPrefs.cap(PlayerPrefs.QUALITY_ADAPTIVE));
        assertEquals(2, PlayerPrefs.cap(PlayerPrefs.QUALITY_SAVER));
        assertEquals(720, PlayerPrefs.maxHeight(PlayerPrefs.cap(PlayerPrefs.QUALITY_SAVER)));
        assertEquals(1080, PlayerPrefs.maxHeight(3));
        assertEquals(576, PlayerPrefs.maxHeight(0));
        assertEquals(PlayerPrefs.QUALITY_CHOICES.length, PlayerPrefs.QUALITY_NAMES.length);
        assertEquals(PlayerPrefs.QUALITY_CHOICES.length, PlayerPrefs.QUALITY_ABOUT.length);
    }
}
