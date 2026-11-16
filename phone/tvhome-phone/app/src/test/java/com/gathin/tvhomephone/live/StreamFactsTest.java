package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** What the provider itself says of a stream (get_vod_info's "video" and "audio"), for the posters' corners. */
public class StreamFactsTest {
    private static JSONObject video(int w, int h, String transfer, String tag) throws Exception {
        return new JSONObject().put("width", w).put("height", h).put("color_transfer", transfer).put("codec_tag_string", tag)
                .put("codec_name", "hevc");
    }

    @Test public void theStreamsPictureAndHdr() throws Exception {
        int uhdPq = StreamFacts.bits(video(3840, 2160, "smpte2084", "hev1"), null);
        assertEquals("4K", SourceQuality.picture(uhdPq));
        assertEquals("HDR10", SourceQuality.hdr(uhdPq));
        // A wide film is 4K by its width (3840x1600).
        assertEquals("4K", SourceQuality.picture(StreamFacts.bits(video(3840, 1600, "bt709", "hev1"), null)));
        assertEquals("1080p", SourceQuality.picture(StreamFacts.bits(video(1920, 800, "bt709", "avc1"), null)));
        assertEquals("720p", SourceQuality.picture(StreamFacts.bits(video(1280, 720, "", ""), null)));
        assertEquals("HLG", SourceQuality.hdr(StreamFacts.bits(video(3840, 2160, "arib-std-b67", "hvc1"), null)));
        // Dolby Vision by its codec tag, or its configuration record.
        assertTrue(SourceQuality.dolbyVision(StreamFacts.bits(video(3840, 2160, "smpte2084", "dvh1"), null)));
        JSONObject rec = video(3840, 2160, "smpte2084", "hev1")
                .put("side_data_list", new JSONArray().put(new JSONObject().put("side_data_type", "DOVI configuration record")));
        assertTrue(SourceQuality.dolbyVision(StreamFacts.bits(rec, null)));
        assertEquals(0, StreamFacts.bits(null, null));
    }

    @Test public void atmosWhenTheSoundSaysSo() throws Exception {
        JSONObject atmos = new JSONObject().put("codec_name", "eac3").put("profile", "Dolby Digital Plus + Dolby Atmos");
        assertTrue(SourceQuality.atmos(StreamFacts.bits(null, atmos)));
        assertFalse(SourceQuality.atmos(StreamFacts.bits(null, new JSONObject().put("codec_name", "aac").put("profile", "LC"))));
    }

    @Test public void aFilmsAndASeriesAnswer() throws Exception {
        JSONObject film = new JSONObject().put("info", new JSONObject().put("video", video(3840, 2160, "smpte2084", "dvhe")));
        assertTrue(SourceQuality.dolbyVision(StreamFacts.ofFilm(film)));
        // A panel that read nothing gives an empty list for "video".
        assertEquals(0, StreamFacts.ofFilm(new JSONObject().put("info", new JSONObject().put("video", new JSONArray()))));
        JSONObject ep = new JSONObject().put("id", "77").put("info", new JSONObject().put("video", video(1920, 1080, "bt709", "avc1")));
        JSONObject series = new JSONObject().put("episodes", new JSONObject().put("1", new JSONArray().put(ep)));
        assertEquals("1080p", SourceQuality.picture(StreamFacts.ofSeries(series)));
        assertEquals(0, StreamFacts.ofSeries(new JSONObject()));
    }

    @Test public void aFilmsIdFromItsAddressAndItsKey() {
        assertEquals("123", StreamFacts.movieId("p1|x:movie:123.mkv"));
        assertEquals("456", StreamFacts.movieId("x:movie:456.mp4"));
        assertEquals("", StreamFacts.movieId("http://example.invalid/film.mp4"));
        assertEquals("mv:9001", StreamFacts.key(false, 9001, "Harbour Lights", "2024"));
        assertEquals("tv:" + Vod.key("Quiet Field") + ":2023", StreamFacts.key(true, 0, "Quiet Field", "2023"));
    }
}
