package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a copy really is, for its row on the cover ("can you tell me the quality of the sources and how big the file
 * is"): made-up Xtream info (HDR10, Dolby Vision, HDR10+, HLG, SDR; Atmos, TrueHD, DTS-HD MA, 5.1, stereo; a size, none,
 * a HEAD's, an estimate) read into its picture, HDR, sound and size; a film's page and an episode's keeping theirs; the
 * copies filled in one after another (a HEAD only through StreamGate, never beside a stream, never for TorBox, asked once
 * a copy); and the best first again by what they really are. Nothing is fetched: the HEAD is a stand-in, the addresses
 * made up.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StreamFactsTest {
    private static final String FILM = "p0|m|101";
    private Context app;
    private Object catalogueWas;
    private final List<String> heads = new ArrayList<>();

    private static java.lang.reflect.Field catalogue() throws Exception {
        java.lang.reflect.Field f = Catalog.class.getDeclaredField("instance");
        f.setAccessible(true);
        return f;
    }

    @Before public void setUp() throws Exception {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        catalogueWas = catalogue().get(null);
        catalogue().set(null, null);
        CopyFacts.forget();
        heads.clear();
    }

    @After public void tearDown() throws Exception {
        CopyFacts.head = null;
        CopyFacts.forget();
        catalogue().set(null, catalogueWas);
        Exec.inline = false;
    }

    /** What the fill posted to the main thread done. */
    private static void settle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static StreamInfo info(String json) throws Exception {
        return StreamInfo.of(new JSONObject(json));
    }

    @Test public void theProvidersInfoSaysWhatTheStreamIs() throws Exception {
        // HDR10, E-AC-3 5.1, its bitrate and length but no size.
        StreamInfo hdr10 = info("{\"bitrate\":\"25000\",\"duration_secs\":\"7200\",\"video\":{\"codec_name\":\"hevc\",\"width\":3840,"
                + "\"height\":2160,\"color_transfer\":\"smpte2084\",\"color_primaries\":\"bt2020\"},\"audio\":{\"codec_name\":\"eac3\","
                + "\"channels\":6,\"channel_layout\":\"5.1(side)\"}}");
        assertEquals(Catalog.PICTURE_4K, hdr10.picture);
        assertEquals("HDR10", hdr10.hdr);
        assertEquals("Dolby Digital+ 5.1", hdr10.sound);
        assertEquals(0, hdr10.size);
        assertEquals(25_000_000L / 8 * 7200, hdr10.estimate());
        // Dolby Vision (its configuration record), TrueHD with Atmos.
        StreamInfo dv = info("{\"video\":{\"codec_name\":\"hevc\",\"width\":3840,\"height\":1600,\"color_transfer\":\"smpte2084\","
                + "\"side_data_list\":[{\"side_data_type\":\"DOVI configuration record\",\"dv_profile\":8}]},"
                + "\"audio\":{\"codec_name\":\"truehd\",\"profile\":\"Dolby TrueHD + Dolby Atmos\",\"channels\":8}}");
        assertEquals("a scope picture 3840 wide is 4K", Catalog.PICTURE_4K, dv.picture);
        assertEquals("Dolby Vision", dv.hdr);
        assertEquals("Dolby Atmos", dv.sound);
        // HDR10+ (its dynamic metadata), E-AC-3 JOC (Atmos).
        StreamInfo plus = info("{\"video\":{\"codec_name\":\"hevc\",\"width\":3840,\"height\":2160,\"color_transfer\":\"smpte2084\","
                + "\"side_data_list\":[{\"side_data_type\":\"HDR Dynamic Metadata SMPTE2094-40 (HDR10+)\"}]},"
                + "\"audio\":{\"codec_name\":\"eac3\",\"profile\":\"E-AC-3 JOC\",\"channels\":6}}");
        assertEquals("HDR10+", plus.hdr);
        assertEquals("Dolby Atmos", plus.sound);
        // HLG, AAC stereo, 1080p.
        StreamInfo hlg = info("{\"video\":{\"codec_name\":\"hevc\",\"width\":1920,\"height\":1080,\"color_transfer\":\"arib-std-b67\"},"
                + "\"audio\":{\"codec_name\":\"aac\",\"channels\":2}}");
        assertEquals(Catalog.PICTURE_1080, hlg.picture);
        assertEquals("HLG", hlg.hdr);
        assertEquals("Stereo", hlg.sound);
        // SDR, Dolby Digital 5.1, 720p, with its size given.
        StreamInfo sdr = info("{\"size\":\"4400000000\",\"video\":{\"codec_name\":\"h264\",\"width\":1280,\"height\":720,"
                + "\"color_transfer\":\"bt709\"},\"audio\":{\"codec_name\":\"ac3\",\"channels\":6}}");
        assertEquals(Catalog.PICTURE_720, sdr.picture);
        assertEquals("SDR", sdr.hdr);
        assertEquals("Dolby Digital 5.1", sdr.sound);
        assertEquals(4_400_000_000L, sdr.size);
        // DTS-HD MA 7.1 and plain TrueHD 7.1; SD; and nothing said: nothing.
        assertEquals("DTS-HD MA 7.1", StreamInfo.sound(new JSONObject("{\"codec_name\":\"dts\",\"profile\":\"DTS-HD MA\",\"channels\":8}")));
        assertEquals("Dolby TrueHD 7.1", StreamInfo.sound(new JSONObject("{\"codec_name\":\"truehd\",\"channels\":8}")));
        assertEquals("DTS 5.1", StreamInfo.sound(new JSONObject("{\"codec_name\":\"dts\",\"channels\":6}")));
        assertEquals("5.1", StreamInfo.sound(new JSONObject("{\"codec_name\":\"aac\",\"channels\":6}")));
        assertEquals(Catalog.PICTURE_SD, StreamInfo.picture(720, 576));
        assertNull(info("{\"plot\":\"A made-up story.\",\"video\":[],\"audio\":[]}"));
        // As a row says them.
        assertEquals(Arrays.asList("Dolby Vision", "Dolby Atmos"), Formats.labels("The Lantern House", dv.hdr, dv.sound));
        assertEquals(Arrays.asList("HLG", "Stereo"), Formats.labels("The Lantern House", hlg.hdr, hlg.sound));
        assertEquals(Arrays.asList("SDR", "Dolby Digital 5.1"), Formats.labels("The Lantern House", sdr.hdr, sdr.sound));
    }

    @Test public void aFilmsPageAndAnEpisodesKeepTheirs() throws Exception {
        Catalog.Details film = Catalog.filmInfo("{\"info\":{\"plot\":\"A made-up story.\",\"duration_secs\":\"8040\",\"bitrate\":9000,"
                + "\"video\":{\"codec_name\":\"hevc\",\"width\":3840,\"height\":2160,\"color_transfer\":\"smpte2084\"},"
                + "\"audio\":{\"codec_name\":\"eac3\",\"channels\":6}}}", null);
        assertEquals("HDR10", film.stream.hdr);
        assertEquals(134, film.minutes);
        Catalog.Details show = Catalog.seriesInfo("{\"info\":{\"name\":\"The Quiet Coast\"},\"episodes\":{\"1\":[{\"id\":\"9\","
                + "\"episode_num\":1,\"title\":\"Arrival\",\"container_extension\":\"mkv\",\"info\":{\"duration_secs\":\"2700\","
                + "\"bitrate\":4000,\"video\":{\"width\":1920,\"height\":1080,\"codec_name\":\"h264\"},\"audio\":{\"codec_name\":\"aac\","
                + "\"channels\":2}}}]}}", null);
        Catalog.Episode e = show.episode("9");
        assertEquals(Catalog.PICTURE_1080, e.stream.picture);
        assertEquals("SDR", e.stream.hdr);
        assertEquals("Stereo", e.stream.sound);
        // Put on a copy: what it says over what the name said.
        Catalog.Play p = Catalog.previewPlay("http://a.example/9.mkv", "Home");
        p.picture = Catalog.PICTURE_4K;
        e.stream.applyTo(p);
        assertEquals(Catalog.PICTURE_1080, p.picture);
        assertTrue(p.info);
        assertEquals(4_000_000L / 8 * 2700, p.stream.estimate());
    }

    private void film(StreamInfo s) {
        Sources.clear(app);
        Sources.put(app, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.example:8080", "made-up", "made-up"));
        List<String> rows = new ArrayList<>();
        rows.add("c\tm\t1\tFilms");
        rows.add("m\t101\tThe Lantern House (2025) 4K\t1\t\t7.9\t" + System.currentTimeMillis() + "\tmkv\t2025\t\t\t");
        Map<String, Catalog.Details> pages = new HashMap<>();
        Catalog.Details d = Catalog.previewDetails("A made-up story.", "Drama", "", "", "2025", "", "7.9", "12A", 134, null);
        d.stream = s;
        pages.put(FILM, d);
        Catalog.get(app).useForPreview(rows, pages);
    }

    private static Catalog.Play copy(String url, String playlist, String key) {
        Catalog.Play p = Catalog.previewPlay(url, playlist);
        p.key = key;
        return p;
    }

    @Test public void theCopiesFilledInTheSizeAskedOnceThenWorkedOut() throws Exception {
        film(info("{\"bitrate\":4000,\"duration_secs\":\"8040\",\"video\":{\"codec_name\":\"h264\",\"width\":1920,\"height\":1080},"
                + "\"audio\":{\"codec_name\":\"ac3\",\"channels\":6}}"));
        CopyFacts.head = (url, ua, ref) -> {
            heads.add(url);
            return url.contains("/sized/") ? 4_100L << 20 : 0L;
        };
        Catalog.Play main = copy("http://provider.example:8080/movie/made-up/made-up/sized/101.mkv", "Home", FILM);
        Catalog.Play other = copy("http://other.example/movie/101.mkv", "Spare", "");
        Catalog.Play torbox = Catalog.previewPlay("torbox:torrent:5:1", "");
        torbox.size = 58L << 30;
        int[] changed = {0};
        CopyFacts.fill(app, Arrays.asList(torbox, main, other), () -> changed[0]++);
        settle();
        // The film's page read: 1080p SDR, Dolby Digital 5.1; its size from the HEAD.
        assertEquals(Catalog.PICTURE_1080, main.picture);
        assertEquals("SDR", main.hdr);
        assertEquals("Dolby Digital 5.1", main.sound);
        assertEquals(4_100L << 20, main.size);
        assertFalse(main.sizeGuessed);
        // TorBox's never asked; the other's HEAD said nothing and it has no page: no size.
        assertEquals(2, heads.size());
        assertEquals(58L << 30, torbox.size);
        assertEquals(0, other.size);
        assertTrue(changed[0] >= 2);
        // Asked once a copy: again, from what was kept.
        Catalog.Play again = copy(main.url, "Home", FILM);
        CopyFacts.fill(app, Arrays.asList(again), () -> { });
        settle();
        assertEquals(2, heads.size());
        assertEquals(4_100L << 20, again.size);
    }

    @Test public void noSizeAnywhereItsWorkedOutFromItsBitrate() throws Exception {
        film(info("{\"bitrate\":4000,\"duration_secs\":\"8040\",\"video\":{\"codec_name\":\"h264\",\"width\":1920,\"height\":1080}}"));
        CopyFacts.head = (url, ua, ref) -> {
            heads.add(url);
            return 0L;
        };
        Catalog.Play main = copy("http://provider.example:8080/movie/made-up/made-up/101.mkv", "Home", FILM);
        CopyFacts.fill(app, Arrays.asList(main), () -> { });
        settle();
        assertEquals(4_000_000L / 8 * 8040, main.size);
        assertTrue(main.sizeGuessed);
        assertTrue(com.gathin.homefieldtv.engine.Formats.labels(main.name, main.hdr, main.sound).contains("SDR"));
    }

    @Test public void neverBesideAStream() throws Exception {
        film(info("{\"bitrate\":4000,\"duration_secs\":\"8040\",\"video\":{\"width\":1920,\"height\":1080,\"codec_name\":\"h264\"}}"));
        CopyFacts.head = (url, ua, ref) -> {
            heads.add(url);
            return 9L << 30;
        };
        StreamGate.Entry playing = StreamGate.enter(StreamGate.newSession(), null);
        try {
            Catalog.Play main = copy("http://provider.example:8080/movie/made-up/made-up/101.mkv", "Home", FILM);
            CopyFacts.fill(app, Arrays.asList(main), () -> { });
        settle();
            assertTrue("not asked while a stream is open", heads.isEmpty());
            assertTrue("worked out meanwhile", main.sizeGuessed);
        } finally {
            StreamGate.leave(playing);
        }
        // Once nothing plays it is asked (it wasn't kept as answered).
        Catalog.Play later = copy("http://provider.example:8080/movie/made-up/made-up/101.mkv", "Home", FILM);
        CopyFacts.fill(app, Arrays.asList(later), () -> { });
        settle();
        assertEquals(1, heads.size());
        assertEquals(9L << 30, later.size);
        assertFalse(later.sizeGuessed);
        assertEquals(0, StreamGate.open());
    }

    @Test public void theBestFirstByWhatTheyReallyAre() {
        Catalog.Play torbox = Catalog.previewPlay("torbox:torrent:5:1", "");
        torbox.picture = Catalog.PICTURE_720;
        // Its name said 4K; its page says 1080p SDR.
        Catalog.Play named = Catalog.previewPlay("http://a.example/1.mkv", "Home");
        named.picture = Catalog.PICTURE_1080;
        named.hdr = "SDR";
        named.sound = "Stereo";
        Catalog.Play real = Catalog.previewPlay("http://b.example/1.mkv", "Spare");
        real.picture = Catalog.PICTURE_4K;
        real.hdr = "HDR10";
        real.sound = "Dolby Atmos";
        Catalog.Play bigger = Catalog.previewPlay("http://c.example/1.mkv", "Third");
        bigger.picture = Catalog.PICTURE_1080;
        bigger.hdr = "SDR";
        bigger.sound = "Stereo";
        bigger.size = 9L << 30;
        List<Catalog.Play> plays = new ArrayList<>(Arrays.asList(torbox, named, bigger, real));
        assertTrue(CopyFacts.rank(plays));
        // TorBox's where it was; then 4K HDR, then the bigger of the two alike.
        assertSame(torbox, plays.get(0));
        assertSame(real, plays.get(1));
        assertSame(bigger, plays.get(2));
        assertSame(named, plays.get(3));
        assertFalse(CopyFacts.rank(plays));
        // SDR is no HDR: surround before stereo decides between two SDR 1080p copies.
        named.sound = "Dolby Digital+ 5.1";
        named.size = 0;
        bigger.size = 0;
        assertTrue(CopyFacts.rank(plays));
        assertSame(named, plays.get(2));
    }
}
