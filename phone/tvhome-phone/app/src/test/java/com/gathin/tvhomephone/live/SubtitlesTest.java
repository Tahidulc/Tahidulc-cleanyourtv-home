package com.gathin.tvhomephone.live;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Subtitles and OpenSubtitles: the search's address, the order offered, the file's kind and writing, what shows when. */
public class SubtitlesTest {

    @Test
    public void aFilmIsLookedForByItsTmdbIdWithTheParametersInOrder() {
        assertEquals("languages=bn,en&tmdb_id=550&type=movie",
                Subtitles.query(550, false, 0, 0, "Fight Club", Arrays.asList("en", "bn")));
    }

    @Test
    public void anEpisodeByItsSeriesSeasonAndNumber() {
        assertEquals("episode_number=2&languages=en&parent_tmdb_id=1396&season_number=1&type=episode",
                Subtitles.query(1396, true, 1, 2, "Breaking Bad", Collections.singletonList("en")));
    }

    @Test
    public void withNoTmdbIdByItsNameInLowerCase() {
        assertEquals("episode_number=3&query=the+bear&season_number=2&type=episode",
                Subtitles.query(0, true, 2, 3, "|UK| The Bear (2022) 4K", null));
        assertEquals("languages=pt-br&query=fight+club&type=movie",
                Subtitles.query(0, false, 0, 0, "EN: Fight Club 1999", Collections.singletonList("pt-BR")));
    }

    @Test
    public void aPlaylistsNameLosesItsTagsQualityAndYear() {
        assertEquals("Fight Club", Subtitles.searchName("UK: Fight Club (1999) 4K"));
        assertEquals("The Bear", Subtitles.searchName("|EN| The Bear 2022"));
        assertEquals("Dune Part Two", Subtitles.searchName("[MULTI] Dune Part Two (2024) [HEVC] 2160p"));
        assertEquals("", Subtitles.searchName(null));
    }

    private static final String REPLY = "{\"total_count\":3,\"data\":["
            + "{\"id\":\"1\",\"attributes\":{\"language\":\"en\",\"download_count\":118,\"new_download_count\":481,"
            + "\"hearing_impaired\":false,\"fps\":23.976,\"from_trusted\":false,\"ai_translated\":false,\"machine_translated\":false,"
            + "\"release\":\"Fight.Club.1999.2160p.BluRay\",\"files\":[{\"file_id\":12883653,\"file_name\":\"Fight.Club.en\"}]}},"
            + "{\"id\":\"2\",\"attributes\":{\"language\":\"en\",\"download_count\":9000,\"fps\":25,\"machine_translated\":true,"
            + "\"release\":\"Fight.Club.DVDRip\",\"files\":[{\"file_id\":2}]}},"
            + "{\"id\":\"3\",\"attributes\":{\"language\":\"en\",\"files\":[]}}]}";

    @Test
    public void theRepliesResultsWithTheirFiles() {
        List<Subtitles.Result> r = Subtitles.results(REPLY);
        assertEquals(2, r.size());
        assertEquals(12883653L, r.get(0).fileId);
        assertEquals(481, r.get(0).downloads);
        assertEquals(23.976, r.get(0).fps, 0.0001);
        assertEquals("Fight.Club.1999.2160p.BluRay", r.get(0).release);
        assertTrue(r.get(1).machine);
        assertTrue(Subtitles.results("not json").isEmpty());
        assertNull(Subtitles.Result.from(null));
    }

    private static Subtitles.Result result(long id, String lang, int downloads, double fps, String release) {
        Subtitles.Result r = new Subtitles.Result();
        r.fileId = id;
        r.lang = lang;
        r.downloads = downloads;
        r.fps = fps;
        r.release = release;
        return r;
    }

    @Test
    public void theFamilysLanguagesComeFirstInTheirOrder() {
        List<Subtitles.Result> in = new ArrayList<>(Arrays.asList(result(1, "fr", 9999, 0, ""), result(2, "en", 10, 0, ""),
                result(3, "bn", 5, 0, "")));
        List<Subtitles.Result> out = Subtitles.rank(in, Arrays.asList("bn", "en"), 0, "", false);
        assertEquals(3, out.get(0).fileId);
        assertEquals(2, out.get(1).fileId);
        assertEquals(1, out.get(2).fileId);
    }

    @Test
    public void oneThatFitsThePictureBeatsOneMoreDownloaded() {
        Subtitles.Result popular = result(1, "en", 50_000, 25, "Film.DVDRip");
        Subtitles.Result fits = result(2, "en", 100, 23.976, "Film.1080p.WEB");
        Subtitles.Result machine = result(3, "en", 90_000, 23.976, "Film.1080p");
        machine.machine = true;
        List<Subtitles.Result> out = Subtitles.rank(Arrays.asList(popular, fits, machine), Collections.singletonList("en"), 23.976f, "1080p", false);
        assertEquals(2, out.get(0).fileId);
        assertEquals(1, out.get(1).fileId);
        assertEquals(3, out.get(2).fileId);
    }

    @Test
    public void framesAndQualityFit() {
        assertTrue(Subtitles.fpsFits(23.976, 23.976f));
        assertFalse(Subtitles.fpsFits(25, 23.976f));
        assertFalse(Subtitles.fpsFits(0, 25f));
        assertTrue(Subtitles.qualityFits("Film.2160p.UHD", "4K"));
        assertTrue(Subtitles.qualityFits("film 1080p web-dl", "1080p"));
        assertFalse(Subtitles.qualityFits("Film.720p", "1080p"));
    }

    @Test
    public void languagesMatchAcrossTheirCodes() {
        assertTrue(Subtitles.sameLanguage("eng", "en"));
        assertTrue(Subtitles.sameLanguage("pt-BR", "pt"));
        assertTrue(Subtitles.sameLanguage("ben", "bn"));
        assertTrue(Subtitles.sameLanguage("ger", "de"));
        assertFalse(Subtitles.sameLanguage("en", "fr"));
        assertFalse(Subtitles.sameLanguage("", "en"));
        assertTrue(Arrays.asList("Bengali", "Bangla").contains(Subtitles.languageName("bn")));
        assertEquals("Portuguese (Brazil)", Subtitles.languageName("pt-BR"));
        assertEquals("", Subtitles.languageName("und"));
    }

    @Test
    public void theFilesKindByItsStart() {
        assertEquals("text/vtt", Subtitles.mime("WEBVTT\n\n00:01.000 --> 00:02.000\nHi".getBytes(StandardCharsets.UTF_8)));
        assertEquals("text/x-ssa", Subtitles.mime(((char) 0xFEFF + "[Script Info]\nTitle: x").getBytes(StandardCharsets.UTF_8)));
        assertEquals("application/x-subrip", Subtitles.mime("1\n00:00:01,000 --> 00:00:02,000\nHi".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void theWritingIsMadeUtf8() {
        byte[] utf = "Café".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(utf, Subtitles.utf8(utf, "fr"));
        // The mark at the start is left off.
        byte[] marked = new byte[utf.length + 3];
        marked[0] = (byte) 0xEF;
        marked[1] = (byte) 0xBB;
        marked[2] = (byte) 0xBF;
        System.arraycopy(utf, 0, marked, 3, utf.length);
        assertArrayEquals(utf, Subtitles.utf8(marked, "fr"));
        // Western and Arabic code pages.
        assertEquals("Café", new String(Subtitles.utf8("Café".getBytes(Charset.forName("windows-1252")), "fr"), StandardCharsets.UTF_8));
        assertEquals("مرحبا", new String(Subtitles.utf8("مرحبا".getBytes(Charset.forName("windows-1256")), "ar"), StandardCharsets.UTF_8));
        assertEquals("Привет", new String(Subtitles.utf8("Привет".getBytes(Charset.forName("windows-1251")), "ru"), StandardCharsets.UTF_8));
    }

    @Test
    public void theLinesShowingAtAMoment() {
        long[] starts = {1_000_000, 4_000_000, 4_500_000, 10_000_000};
        long[] ends = {3_000_000, 6_000_000, 5_000_000, -1};
        assertArrayEquals(new int[0], Subtitles.active(starts, ends, 500_000));
        assertArrayEquals(new int[]{0}, Subtitles.active(starts, ends, 2_000_000));
        assertArrayEquals(new int[0], Subtitles.active(starts, ends, 3_500_000));
        // Two at once, in their order.
        assertArrayEquals(new int[]{1, 2}, Subtitles.active(starts, ends, 4_700_000));
        assertArrayEquals(new int[]{1}, Subtitles.active(starts, ends, 5_500_000));
        // No end of its own: on until the next (there is none).
        assertArrayEquals(new int[]{3}, Subtitles.active(starts, ends, 99_000_000));
        assertArrayEquals(new int[0], Subtitles.active(new long[0], new long[0], 1));
    }

    @Test
    public void theTimingSaysWhichWay() {
        assertEquals("+1.5 s", Subtitles.offsetText(1500));
        assertEquals("−0.5 s", Subtitles.offsetText(-500));
        assertEquals("0.0 s", Subtitles.offsetText(0));
        assertEquals(Subtitles.MAX_OFFSET_MS, Subtitles.clampOffset(999_999));
    }

    @Test
    public void onlyOpenSubtitlesOwnServersAreUsedAfterSigningIn() {
        assertEquals("vip-api.opensubtitles.com", OpenSubtitles.host("vip-api.opensubtitles.com"));
        assertEquals("api.opensubtitles.com", OpenSubtitles.host("https://api.opensubtitles.com/"));
        assertEquals("api.opensubtitles.com", OpenSubtitles.host("evil.example.com"));
        assertEquals("api.opensubtitles.com", OpenSubtitles.host("opensubtitles.com.evil.example"));
        assertEquals("api.opensubtitles.com", OpenSubtitles.host(null));
    }

    @Test
    public void whatToSayWhenItDidntWork() {
        assertTrue(OpenSubtitles.words(406, "").contains("today"));
        assertEquals("OpenSubtitles: You have downloaded your allowed 20 subtitles", OpenSubtitles.words(406, "You have downloaded your allowed 20 subtitles"));
        assertTrue(OpenSubtitles.words(401, "").contains("API key"));
        assertTrue(OpenSubtitles.words(503, "x").contains("isn't answering"));
    }
}
