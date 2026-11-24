package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The logo catalogue: the packs' indexes read (made-up ones here, no downloads), cached, and searched by a channel's name. */
public class LogoPacksTest {
    /** A made-up GitHub tree of a logo repository: invented channels. */
    private static final String TREE = "{\"sha\":\"x\",\"url\":\"u\",\"tree\":["
            + "{\"path\":\"README.md\",\"mode\":\"100644\",\"type\":\"blob\",\"sha\":\"a\",\"size\":10},"
            + "{\"path\":\"countries\",\"type\":\"tree\"},"
            + "{\"path\":\"countries/united-kingdom/harbour-sports-main-event-uk.png\",\"type\":\"blob\",\"size\":1000},"
            + "{\"path\":\"countries/united-kingdom/harbour-sports-news-uk.png\",\"type\":\"blob\",\"size\":1000},"
            + "{\"path\":\"countries/united-kingdom/millbrook-one-uk.png\",\"type\":\"blob\",\"size\":1000},"
            + "{\"path\":\"countries/united-kingdom/0_mosaic.png\",\"type\":\"blob\"},"
            + "{\"path\":\"countries/ireland/riverside-tv-ie.png\",\"type\":\"blob\"},"
            + "{\"path\":\"misc/thing.png\",\"type\":\"blob\"}],\"truncated\":false}";

    /** A made-up iptv-org logos.json. */
    private static final String IPTV = "[{\"channel\":\"HarbourSportsMainEvent.uk\",\"feed\":null,\"in_use\":true,\"tags\":[\"picons\"],"
            + "\"width\":512,\"height\":288,\"format\":\"PNG\",\"url\":\"https://example.org/hsme.png\"},"
            + "{\"channel\":\"HarbourSportsMainEvent.uk\",\"feed\":null,\"tags\":[],\"format\":\"SVG\",\"url\":\"https://example.org/hsme.svg\"},"
            + "{\"channel\":\"MillbrookOne.uk\",\"feed\":\"HD\",\"tags\":[],\"format\":\"PNG\",\"url\":\"http://example.org/plain.png\"},"
            + "{\"channel\":\"Channel4\\u002B1.uk\",\"tags\":[],\"format\":\"PNG\",\"url\":\"https://example.org/c4.png\"}]";

    @Test
    public void tvLogosEntriesComeFromTheTree() {
        List<LogoPacks.Logo> l = LogoPacks.parseTvLogos(TREE);
        assertEquals(4, l.size());
        assertEquals("Harbour Sports Main Event", l.get(0).name);
        assertEquals("united-kingdom", l.get(0).country);
        assertEquals("https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/united-kingdom/harbour-sports-main-event-uk.png", l.get(0).url);
        assertEquals("Riverside Tv", l.get(3).name);
    }

    @Test
    public void iptvOrgEntriesLeaveOutVectorsAndPlainHttp() {
        List<LogoPacks.Logo> l = LogoPacks.parseIptvOrg(IPTV);
        assertEquals(2, l.size());
        assertEquals("Harbour Sports Main Event", l.get(0).name);
        assertEquals("uk", l.get(0).country);
        assertEquals("Channel 4+1", l.get(1).name);
    }

    @Test
    public void namesAreReadAsPeopleWriteThem() {
        assertEquals("BBC One", LogoPacks.iptvName("BBCOne"));
        assertEquals("Channel 4", LogoPacks.iptvName("Channel4"));
        assertEquals("Millbrook One", LogoPacks.tvLogosName("millbrook-one-uk.png"));
    }

    @Test
    public void theCacheGivesBackWhatWasKept() {
        List<LogoPacks.Logo> l = LogoPacks.parseTvLogos(TREE);
        List<LogoPacks.Logo> back = LogoPacks.fromLines(LogoPacks.toLines(l), LogoPacks.Pack.TV_LOGOS);
        assertEquals(l.size(), back.size());
        for (int i = 0; i < l.size(); i++) {
            assertEquals(l.get(i).name, back.get(i).name);
            assertEquals(l.get(i).url, back.get(i).url);
        }
        assertTrue(LogoPacks.fromLines("garbage\nno\ttabs\n", LogoPacks.Pack.TV_LOGOS).isEmpty());
    }

    private static Map<LogoPacks.Pack, List<LogoPacks.Logo>> packs() {
        Map<LogoPacks.Pack, List<LogoPacks.Logo>> p = new LinkedHashMap<>();
        p.put(LogoPacks.Pack.TV_LOGOS, LogoPacks.parseTvLogos(TREE));
        p.put(LogoPacks.Pack.IPTV_ORG, LogoPacks.parseIptvOrg(IPTV));
        return p;
    }

    @Test
    public void aPlaylistsChannelNameFindsItsLogoInEachPack() {
        Map<LogoPacks.Pack, List<LogoPacks.Logo>> r = LogoPacks.search(packs(), "UK: Harbour Sports Main Event FHD", 5);
        assertEquals("Harbour Sports Main Event", r.get(LogoPacks.Pack.TV_LOGOS).get(0).name);
        assertEquals(1, r.get(LogoPacks.Pack.TV_LOGOS).size());
        assertEquals(1, r.get(LogoPacks.Pack.IPTV_ORG).size());
    }

    @Test
    public void theBestMatchComesFirst() {
        Map<LogoPacks.Pack, List<LogoPacks.Logo>> r = LogoPacks.search(packs(), "harbour sports", 5);
        List<LogoPacks.Logo> tv = r.get(LogoPacks.Pack.TV_LOGOS);
        assertEquals(2, tv.size());
        assertTrue(tv.get(0).name.startsWith("Harbour Sports"));
        assertTrue(LogoPacks.search(packs(), "nothing like it", 5).get(LogoPacks.Pack.TV_LOGOS).isEmpty());
        assertFalse(LogoPacks.search(packs(), "millbrook", 5).get(LogoPacks.Pack.TV_LOGOS).isEmpty());
    }

    @Test
    public void escapedAndNestedJsonIsRead() {
        List<Map<String, String>> seen = new java.util.ArrayList<>();
        LogoPacks.FlatJson.objects("{\"a\":\"x\\\"y\",\"n\":3,\"z\":null,\"o\":{\"b\":\"c\"},\"arr\":[1,{\"d\":true}]}", seen::add);
        assertEquals(3, seen.size());
        assertEquals("c", seen.get(0).get("b"));
        assertEquals("true", seen.get(1).get("d"));
        assertEquals("x\"y", seen.get(2).get("a"));
        assertEquals("3", seen.get(2).get("n"));
        assertFalse(seen.get(2).containsKey("z"));
    }

    @Test
    public void theLicencesNameEachPack() {
        assertTrue(LogoPacks.LICENCES.contains("tv-logo/tv-logos"));
        assertTrue(LogoPacks.LICENCES.contains("Unlicense"));
    }
}
