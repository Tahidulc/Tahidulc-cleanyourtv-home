package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.StringReader;

public class M3uTest {
    private static final String LIST = "﻿#EXTM3U url-tvg=\"http://guide.example/epg.xml.gz,http://other\" x-tvg-url=\"ignored\"\n"
            + "#EXTINF:-1 group-title=\"UK | Sports\" tvg-logo=\"http://logo/a.png\" tvg-id=\"SkySpMainEv.uk\" tvg-name=\"Sky Sports Main Event\",UK: Sky Sports Main Event FHD\n"
            + "http://host/live/a.m3u8\n"
            + "\n"
            + "#EXTINF:-1 tvg-id='bbc1.uk' tvg-chno=\"101\",BBC One, London\n"
            + "#EXTGRP:Entertainment\n"
            + "#EXTVLCOPT:http-user-agent=SomePlayer/2\n"
            + "#EXTVLCOPT:http-referrer=http://ref/\n"
            + "http://host/live/b.ts\n"
            + "#EXTINF:-1 group-title=\"Films\",A Film (2024)\n"
            + "http://host/movie/u/p/123.mkv\n"
            + "#EXTINF:-1 tvg-type=\"series\" group-title=\"Box sets\",A Series S01E01\n"
            + "http://host/whatever/9\n"
            + "#EXTINF:-1,Sky Sports Main Event FHD\n"
            + "http://host/live/c.ts\n"
            + "#EXTINF:-1 tvg-id=\"bbc1.uk\" tvg-chno=\"101\",BBC One, London\n"
            + "http://host/live/b2.ts\n";

    @Test public void readsLiveChannelsAndTheirAttributesInAnyOrder() throws Exception {
        M3u.Result r = M3u.parse(new StringReader(LIST));
        assertEquals("http://guide.example/epg.xml.gz", r.guideUrl);
        assertEquals(4, r.channels.size());
        assertEquals(2, r.skipped);
        Channel a = r.channels.get(0);
        assertEquals("UK: Sky Sports Main Event FHD", a.name);
        assertEquals("UK | Sports", a.group);
        assertEquals("http://logo/a.png", a.logo);
        assertEquals("SkySpMainEv.uk", a.tvgId);
        assertEquals("http://host/live/a.m3u8", a.url);
        Channel b = r.channels.get(1);
        assertEquals("BBC One, London", b.name);
        assertEquals("Entertainment", b.group);
        assertEquals("bbc1.uk", b.tvgId);
        assertEquals(101, b.number);
        assertEquals("SomePlayer/2", b.userAgent);
        assertEquals("http://ref/", b.referrer);
        // Options belong to one entry only.
        assertEquals("", r.channels.get(2).userAgent);
    }

    @Test public void keysStayUniqueAndStable() throws Exception {
        M3u.Result r = M3u.parse(new StringReader(LIST));
        assertEquals("bbc1.uk|BBC One, London", r.channels.get(1).key);
        assertEquals("bbc1.uk|BBC One, London#2", r.channels.get(3).key);
        assertEquals("|Sky Sports Main Event FHD", r.channels.get(2).key);
    }

    @Test public void filmsAndSeriesAreNotLive() {
        assertTrue(M3u.isVod("http://h/movie/u/p/1.mp4", null));
        assertTrue(M3u.isVod("http://h/series/u/p/1.mkv", null));
        assertTrue(M3u.isVod("http://h/x/1.mp4?token=1", null));
        assertTrue(M3u.isVod("http://h/live/1.ts", "movie"));
        assertFalse(M3u.isVod("http://h/live/u/p/1.ts", null));
        assertFalse(M3u.isVod("http://h/live/1.m3u8", "live"));
    }

    @Test public void catchupFromTheEntryOrThePlaylistHeader() throws Exception {
        String list = "#EXTM3U url-tvg=\"http://guide.example/epg.xml\" catchup=\"append\" catchup-days=\"5\" catchup-source=\"?utc={utc}&lutc={lutc}\"\n"
                + "#EXTINF:-1 tvg-id=\"one.example\",Made Up One\n"
                + "http://tv.example/hls/one.m3u8\n"
                + "#EXTINF:-1 tvg-id=\"two.example\" catchup=\"shift\" catchup-days=\"2\",Made Up Two\n"
                + "http://tv.example/hls/two.m3u8\n"
                + "#EXTINF:-1 tvg-id=\"three.example\" catchup=\"default\" catchup-source=\"http://archive.example/three/{utc}-{utcend}.ts\",Made Up Three\n"
                + "http://tv.example/hls/three.m3u8\n"
                + "#EXTINF:-1 tvg-id=\"four.example\" catchup=\"vod\",Made Up Four\n"
                + "http://tv.example/hls/four.m3u8\n";
        M3u.Result r = M3u.parse(new StringReader(list));
        assertEquals("http://guide.example/epg.xml", r.guideUrl);
        Channel one = r.channels.get(0), two = r.channels.get(1), three = r.channels.get(2), four = r.channels.get(3);
        // As the header says.
        assertEquals(Catchup.APPEND, one.catchup);
        assertEquals(5, one.archiveDays);
        assertEquals("?utc={utc}&lutc={lutc}", one.catchupSource);
        // Its own words first.
        assertEquals(Catchup.SHIFT, two.catchup);
        assertEquals(2, two.archiveDays);
        assertEquals(Catchup.DEFAULT, three.catchup);
        assertEquals(5, three.archiveDays);
        assertEquals("http://archive.example/three/{utc}-{utcend}.ts", three.catchupSource);
        assertEquals("", four.catchup);
        assertEquals(0, four.archiveDays);
    }

    @Test public void catchupDaysAloneOnAnXtreamPlaylist() throws Exception {
        String list = "#EXTM3U\n"
                + "#EXTINF:-1 tvg-id=\"one.example\" timeshift=\"7\",Made Up One\n"
                + "http://box.example:8080/viewer/secret/101\n"
                + "#EXTINF:-1 tvg-id=\"two.example\" tvg-rec=\"3\",Made Up Two\n"
                + "http://tv.example/hls/two.m3u8\n"
                + "#EXTINF:-1 tvg-id=\"three.example\",Made Up Three\n"
                + "http://box.example:8080/viewer/secret/103\n";
        M3u.Result r = M3u.parse(new StringReader(list));
        assertEquals(Catchup.XC, r.channels.get(0).catchup);
        assertEquals(7, r.channels.get(0).archiveDays);
        assertEquals(Catchup.SHIFT, r.channels.get(1).catchup);
        assertEquals(3, r.channels.get(1).archiveDays);
        assertEquals("", r.channels.get(2).catchup);
        assertEquals(0, r.channels.get(2).archiveDays);
    }

    @Test public void barePlaylistWithoutExtinfStillReads() throws Exception {
        M3u.Result r = M3u.parse(new StringReader("http://h/live/one.ts\nhttp://h/live/two.m3u8?x=1\n"));
        assertEquals(2, r.channels.size());
        assertEquals("one.ts", r.channels.get(0).name);
        assertEquals("two.m3u8", r.channels.get(1).name);
    }
}
