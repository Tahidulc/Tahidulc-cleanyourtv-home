package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** A fixture's highlights: ESPN's videos (played here), FotMob's link (opened elsewhere), clips that name the event. */
public class HighlightsTest {
    private static JSONObject video(String headline, String hd, String hls, int seconds) throws Exception {
        JSONObject source = new JSONObject();
        if (hd != null) source.put("HD", new JSONObject().put("href", hd));
        if (hls != null) source.put("HLS", new JSONObject().put("href", hls));
        return new JSONObject().put("headline", headline).put("thumbnail", "https://example.invalid/still.jpg").put("duration", seconds)
                .put("links", new JSONObject().put("source", source));
    }

    @Test public void espnsVideosPlayHereTheMp4First() throws Exception {
        JSONObject root = new JSONObject().put("videos", new JSONArray()
                .put(video("Herons edge it late", "https://example.invalid/a.mp4", "https://example.invalid/a/playlist.m3u8", 118))
                .put(video("Only a stream", null, "https://example.invalid/b/playlist.m3u8", 40))
                .put(video("Nothing to play", null, null, 30)));
        List<Highlights.Clip> clips = Highlights.summaryClips(root, 6);
        assertEquals(2, clips.size());
        assertEquals("https://example.invalid/a.mp4", clips.get(0).play);
        assertEquals("Herons edge it late", clips.get(0).title);
        assertEquals(118, clips.get(0).seconds);
        assertTrue(clips.get(0).here());
        assertEquals("https://example.invalid/b/playlist.m3u8", clips.get(1).play);
        assertEquals(1, Highlights.summaryClips(root, 1).size());
        assertTrue(Highlights.summaryClips(null, 6).isEmpty());
    }

    @Test public void aClipsOwnRecord() throws Exception {
        JSONObject root = new JSONObject().put("videos", new JSONArray().put(video("Spinning heel finish", "https://example.invalid/c.mp4", null, 33)));
        Highlights.Clip c = Highlights.clipFrom(root);
        assertEquals("https://example.invalid/c.mp4", c.play);
        assertNull(Highlights.clipFrom(new JSONObject()));
    }

    @Test public void newsClipsThatNameTheEventAroundItsDay() throws Exception {
        JSONArray arts = new JSONArray()
                .put(media("Delacour stops Brann in the third", "2026-11-15T04:00:00Z", "Delacour vs. Brann", "https://example.invalid/clips/1"))
                .put(media("Marsh: 'I want the belt next'", "2026-11-15T05:00:00Z", "Marsh vs. Okafor", "https://example.invalid/clips/2"))
                .put(media("Delacour v Brann, the year before", "2025-11-15T04:00:00Z", "Delacour vs. Brann", "https://example.invalid/clips/3"))
                .put(new JSONObject().put("type", "Story").put("headline", "Delacour v Brann preview").put("published", "2026-11-14T04:00:00Z"));
        JSONObject news = new JSONObject().put("articles", arts);
        List<String[][]> finds = new ArrayList<>();
        finds.add(new String[][]{{"Rafe Delacour", "Delacour"}, {"Tomas Brann", "Brann"}});
        long from = Highlights.time("2026-11-13T00:00:00Z"), to = Highlights.time("2026-11-18T00:00:00Z");
        List<String[]> got = Highlights.newsMedia(news, finds, from, to, 4);
        assertEquals(1, got.size());
        assertEquals("https://example.invalid/clips/1", got.get(0)[0]);
        assertEquals(2, Highlights.newsMedia(news, finds, 0, 0, 4).size());
    }

    private static JSONObject media(String headline, String published, String event, String clip) throws Exception {
        return new JSONObject().put("type", "Media").put("headline", headline).put("published", published)
                .put("categories", new JSONArray().put(new JSONObject().put("type", "event").put("description", event)))
                .put("links", new JSONObject().put("api", new JSONObject().put("self", new JSONObject().put("href", clip))))
                .put("images", new JSONArray().put(new JSONObject().put("url", "https://example.invalid/pic.jpg")));
    }

    @Test public void fotMobsLinkOpensElsewhere() {
        Highlights.Clip yt = Highlights.fotmob(new String[]{"https://www.youtube.com/watch?v=abcdefghijk", "", "www.youtube.com"});
        assertFalse(yt.here());
        assertEquals("YouTube", yt.source);
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg", yt.still);
        Highlights.Clip site = Highlights.fotmob(new String[]{"https://www.example.invalid/match/1", "https://example.invalid/p.jpg", "www.example.invalid"});
        assertEquals("example.invalid", site.source);
        assertEquals("https://example.invalid/p.jpg", site.still);
        assertNull(Highlights.fotmob(null));
        assertNull(Highlights.fotmob(new String[]{""}));
    }

    @Test public void youtubesIds() {
        assertEquals("abcdefghijk", Highlights.youtubeId("https://www.youtube.com/watch?v=abcdefghijk"));
        assertEquals("abcdefghijk", Highlights.youtubeId("https://youtu.be/abcdefghijk?t=3"));
        assertEquals("abcdefghijk", Highlights.youtubeId("https://m.youtube.com/watch?feature=share&v=abcdefghijk"));
        assertEquals("abcdefghijk", Highlights.youtubeId("https://www.youtube.com/shorts/abcdefghijk"));
        assertNull(Highlights.youtubeId("https://www.example.invalid/watch?v=abcdefghijk"));
    }

    @Test public void lengthsAndTimes() {
        assertEquals("1:58", EventViews.length(118));
        assertEquals("12:03", EventViews.length(723));
        assertEquals("0:00", EventViews.length(-4));
        long now = Highlights.time("2026-11-16T12:00:00Z");
        assertEquals("", EventViews.when(0, now));
        assertTrue(EventViews.when(now - 60_000L, now).startsWith("Started "));
        assertFalse(EventViews.when(now + 8L * 24 * 3_600_000L, now).isEmpty());
    }
}
