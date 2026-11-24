package com.gathin.homefieldtv.subs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * OpenSubtitles for the film player, against a fake line and made-up answers (the real API is never asked, and the key,
 * name and password are obviously made up): the search's query by IMDb, TMDB or name, the results read and offered in
 * the family's order, signing in and downloading with the right headers (the key, the token, a plain user agent and
 * nothing else), and what the family is told when it doesn't work.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OpenSubtitlesTest {
    private static final String KEY = "made-up-key-0000", USER = "made-up-user", PASS = "not-a-real-password";
    private final List<String> asked = new ArrayList<>();
    private final List<Map<String, String>> headers = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private OpenSubtitles.Http was;
    private Context c;

    /** A fake OpenSubtitles: what each address answers. */
    private interface Answers {
        OpenSubtitles.Reply answer(String method, String url);
    }

    private void fake(Answers a) {
        OpenSubtitles.http = (method, url, h, body) -> {
            asked.add(method + " " + url);
            headers.add(h);
            bodies.add(body == null ? "" : new String(body, StandardCharsets.UTF_8));
            return a.answer(method, url);
        };
    }

    private static OpenSubtitles.Reply json(int code, String body) {
        return new OpenSubtitles.Reply(code, body.getBytes(StandardCharsets.UTF_8));
    }

    @Before public void setUp() {
        c = RuntimeEnvironment.getApplication();
        SubsPrefs.forget(c);
        was = OpenSubtitles.http;
        OpenSubtitles.version = "9.9";
    }

    @After public void tearDown() {
        OpenSubtitles.http = was;
        SubsPrefs.forget(c);
    }

    @Test public void queryByIdsOrName() {
        List<String> langs = Arrays.asList("fr", "en");
        assertEquals("imdb_id=1234567&languages=en,fr&type=movie",
                OpenSubtitles.query(new OpenSubtitles.Ask("tt01234567", 550, false, 0, 0, "Glass Harbour", 2024), langs));
        assertEquals("languages=en,fr&tmdb_id=550&type=movie",
                OpenSubtitles.query(new OpenSubtitles.Ask("", 550, false, 0, 0, "Glass Harbour", 2024), langs));
        assertEquals("languages=en&query=glass%20harbour&type=movie&year=2024".replace("%20", "+"),
                OpenSubtitles.query(new OpenSubtitles.Ask("", 0, false, 0, 0, "UK: Glass Harbour (2024) 4K", 2024), Collections.singletonList("EN")));
        assertEquals("episode_number=3&parent_tmdb_id=1399&season_number=2&type=episode",
                OpenSubtitles.query(new OpenSubtitles.Ask("", 1399, true, 2, 3, "The Quiet Coast", 0), null));
        assertEquals("episode_number=3&languages=en&parent_imdb_id=944947&season_number=2&type=episode",
                OpenSubtitles.query(new OpenSubtitles.Ask("tt0944947", 1399, true, 2, 3, "The Quiet Coast", 0), Collections.singletonList("en")));
        assertEquals("", OpenSubtitles.imdbNumber("nm123"));
        assertEquals("by its IMDb id", new OpenSubtitles.Ask("tt1", 0, false, 0, 0, "", 0).by());
        assertEquals("by its name", new OpenSubtitles.Ask("", 0, false, 0, 0, "Glass Harbour", 0).by());
    }

    private static final String RESULTS = "{\"total_count\":4,\"data\":["
            + "{\"attributes\":{\"language\":\"fr\",\"release\":\"Glass.Harbour.2024.1080p.WEB\",\"download_count\":900,"
            + "\"hearing_impaired\":false,\"files\":[{\"file_id\":11,\"file_name\":\"gh.fr.srt\"}]}},"
            + "{\"attributes\":{\"language\":\"en\",\"release\":\"Glass.Harbour.2024.720p\",\"download_count\":120,"
            + "\"hearing_impaired\":true,\"from_trusted\":true,\"files\":[{\"file_id\":22,\"file_name\":\"gh.en.sdh.srt\"}]}},"
            + "{\"attributes\":{\"language\":\"en\",\"release\":\"Glass.Harbour.2024.1080p.WEB\",\"download_count\":12345,"
            + "\"files\":[{\"file_id\":33,\"file_name\":\"gh.en.srt\"}]}},"
            + "{\"attributes\":{\"language\":\"en\",\"release\":\"no files\",\"files\":[]}},"
            + "{\"attributes\":{\"language\":\"en\",\"release\":\"\",\"machine_translated\":true,\"download_count\":99999,"
            + "\"files\":[{\"file_id\":44,\"file_name\":\"gh.en.mt.srt\"}]}}]}";

    @Test public void resultsReadAndOfferedInTheFamilysOrder() {
        List<OpenSubtitles.Result> rs = OpenSubtitles.results(RESULTS);
        assertEquals(4, rs.size());
        assertEquals(11, rs.get(0).fileId);
        assertEquals("gh.en.mt.srt", rs.get(3).release);
        assertTrue(rs.get(3).machine);
        List<OpenSubtitles.Result> ranked = OpenSubtitles.rank(rs, Arrays.asList("en", "fr"), false, "1080p");
        // English first: not machine translated, the release of the same quality, most downloaded; French after.
        assertEquals(33, ranked.get(0).fileId);
        assertEquals(22, ranked.get(1).fileId);
        assertEquals(44, ranked.get(2).fileId);
        assertEquals(11, ranked.get(3).fileId);
        // Hearing impaired wanted first.
        assertEquals(22, OpenSubtitles.rank(rs, Collections.singletonList("en"), true, "").get(0).fileId);
        assertEquals("English  ·  Hearing impaired  ·  120 downloads  ·  Trusted", ranked.get(1).words());
        assertEquals("English  ·  12.3k downloads", ranked.get(0).words());
        assertTrue(OpenSubtitles.results("not json").isEmpty());
        assertEquals("1.2M", OpenSubtitles.count(1_234_567));
    }

    @Test public void searchSendsTheKeyAndAPlainUserAgent() throws IOException {
        SubsPrefs.setKey(c, KEY);
        fake((m, u) -> json(200, RESULTS));
        List<OpenSubtitles.Result> rs = OpenSubtitles.search(c, new OpenSubtitles.Ask("", 550, false, 0, 0, "Glass Harbour", 2024),
                Collections.singletonList("en"));
        assertEquals(4, rs.size());
        assertEquals("GET https://api.opensubtitles.com/api/v1/subtitles?languages=en&tmdb_id=550&type=movie", asked.get(0));
        Map<String, String> h = headers.get(0);
        assertEquals(KEY, h.get("Api-Key"));
        assertEquals("HomefieldTV v9.9", h.get("User-Agent"));
        assertNull(h.get("Authorization"));
        assertEquals(3, h.size());
    }

    @Test public void noKeyNoSearch() {
        fake((m, u) -> json(200, RESULTS));
        try {
            OpenSubtitles.search(c, new OpenSubtitles.Ask("", 550, false, 0, 0, "", 0), null);
            fail();
        } catch (IOException e) {
            assertEquals(OpenSubtitles.NEEDS_KEY, e.getMessage());
        }
        assertTrue(asked.isEmpty());
    }

    @Test public void signsInThenDownloads() throws IOException {
        SubsPrefs.setKey(c, KEY);
        SubsPrefs.setUser(c, USER);
        SubsPrefs.setPassword(c, PASS);
        fake((m, u) -> {
            if (u.endsWith("/login")) return json(200, "{\"token\":\"made-up-token\",\"base_url\":\"vip-api.opensubtitles.com\"}");
            if (u.endsWith("/download")) return json(200, "{\"link\":\"https://files.example/1.srt\",\"remaining\":19}");
            if (u.startsWith("https://files.example/")) return json(200, "1\n00:00:01,000 --> 00:00:02,000\nHello\n");
            return json(404, "{}");
        });
        OpenSubtitles.Result r = new OpenSubtitles.Result();
        r.fileId = 33;
        r.lang = "en";
        byte[] got = OpenSubtitles.download(c, r);
        assertEquals("1\n00:00:01,000 --> 00:00:02,000\nHello\n", new String(got, StandardCharsets.UTF_8));
        assertEquals("POST https://api.opensubtitles.com/api/v1/login", asked.get(0));
        assertTrue(bodies.get(0).contains("\"username\":\"" + USER + "\""));
        // A VIP sign-in's own server for the download, with the token.
        assertEquals("POST https://vip-api.opensubtitles.com/api/v1/download", asked.get(1));
        assertEquals("Bearer made-up-token", headers.get(1).get("Authorization"));
        assertEquals(KEY, headers.get(1).get("Api-Key"));
        assertEquals("{\"file_id\":33}", bodies.get(1));
        // The file's own link: none of OpenSubtitles' headers.
        assertEquals("GET https://files.example/1.srt", asked.get(2));
        assertNull(headers.get(2).get("Api-Key"));
        assertNull(headers.get(2).get("Authorization"));
        // The sign-in is used again.
        asked.clear();
        OpenSubtitles.download(c, r);
        assertFalse(asked.get(0).endsWith("/login"));
    }

    @Test public void whatTheFamilyIsTold() {
        SubsPrefs.setKey(c, KEY);
        fake((m, u) -> json(406, "{\"message\":\"You have downloaded your allowed 20 subtitles for 24h\"}"));
        OpenSubtitles.Result r = new OpenSubtitles.Result();
        r.fileId = 1;
        try {
            OpenSubtitles.download(c, r);
            fail();
        } catch (IOException e) {
            assertEquals("OpenSubtitles: You have downloaded your allowed 20 subtitles for 24h", e.getMessage());
            assertFalse(e.getMessage().contains(KEY));
        }
        assertEquals("OpenSubtitles is busy: try again in a moment", OpenSubtitles.words(429, ""));
        assertTrue(OpenSubtitles.words(401, "").contains("Settings"));
        assertEquals("OpenSubtitles isn't answering right now", OpenSubtitles.words(503, "boom"));
        // A line that breaks: a plain word, never the address.
        OpenSubtitles.http = (m, u, h, b) -> {
            throw new IOException("https://api.opensubtitles.com/... " + h.get("Api-Key"));
        };
        try {
            OpenSubtitles.search(c, new OpenSubtitles.Ask("", 1, false, 0, 0, "", 0), null);
            fail();
        } catch (IOException e) {
            assertEquals("OpenSubtitles couldn't be reached", e.getMessage());
        }
        assertEquals("api.opensubtitles.com", OpenSubtitles.host("https://evil.example/api"));
        assertEquals("vip-api.opensubtitles.com", OpenSubtitles.host("https://vip-api.opensubtitles.com/"));
    }

    @Test public void theLoginIsKeptOnlyAsDots() {
        SubsPrefs.setKey(c, "  " + KEY + "  ");
        assertTrue(SubsPrefs.hasKey(c));
        assertEquals("••••••••0000", SubsPrefs.maskedKey(c));
        SubsPrefs.forget(c);
        assertFalse(SubsPrefs.hasKey(c));
        assertEquals("", SubsPrefs.maskedKey(c));
    }
}
