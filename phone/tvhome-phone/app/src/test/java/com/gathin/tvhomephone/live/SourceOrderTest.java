package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every source the best first, with no limit on size: the picture, then HDR, then the sound, then the bigger file; AVI
 * and the like after the rest; TorBox's copies before the playlists' (or after, as Settings say). Invented titles only.
 */
public class SourceOrderTest {
    private static final long GB = 1024L * 1024 * 1024;

    private static String[] playlist(String url, String playlist, String name) {
        return new String[]{url, "", "", Vod.label(playlist, name, url), SourceInfo.aboutPlaylist(playlist, name)};
    }

    @Test public void thePlaylistsCopiesBestFirstWhereTheirNamesSay() {
        String[] plain = playlist("x:movie:1.mkv", "Main", "Glass Harbour");
        String[] fhd = playlist("p1|x:movie:2.mkv", "Spare", "Glass Harbour 1080p");
        String[] uhd = playlist("x:movie:3.mp4", "Main", "Glass Harbour 4K HDR");
        String[] avi = playlist("p2|x:movie:4.avi", "Old", "Glass Harbour 4K");
        List<String[]> all = new ArrayList<>(Arrays.asList(plain, avi, fhd, uhd));
        all.sort(SourceInfo::compare);
        // 4K, then 1080p, then one whose name says nothing; AVI last, even in 4K.
        assertEquals(Arrays.asList(uhd, fhd, plain, avi), all);
        // Two alike keep their order.
        String[] plain2 = playlist("p1|x:movie:5.mkv", "Spare", "Glass Harbour");
        List<String[]> two = new ArrayList<>(Arrays.asList(plain, plain2));
        two.sort(SourceInfo::compare);
        assertSame(plain, two.get(0));
    }

    @Test public void surroundIsMoreThanTwoChannels() {
        assertTrue(SourceInfo.surround("Dolby Atmos"));
        assertTrue(SourceInfo.surround("DTS-HD 7.1"));
        assertTrue(SourceInfo.surround("Dolby Digital+ 5.1"));
        assertTrue(SourceInfo.surround("Dolby Digital+"));
        assertTrue(SourceInfo.surround("Dolby TrueHD"));
        assertTrue(SourceInfo.surround("AAC 5.1"));
        assertTrue(SourceInfo.surround("5.1"));
        assertTrue(SourceInfo.surround("DTS"));
        assertFalse(SourceInfo.surround("Dolby Digital+ Stereo"));
        assertFalse(SourceInfo.surround("AAC Stereo"));
        assertFalse(SourceInfo.surround("MPEG"));
        assertFalse(SourceInfo.surround(""));
        assertFalse(SourceInfo.surround(null));
    }

    @Test public void filmCopiesFromThePlaylistsBestFirst() {
        Vod.Item a = new Vod.Item("movie", "Glass Harbour", "x:movie:1.mkv", "", "", "", "2024", "", "p0");
        a.label = Vod.label("Main", a.name, a.url);
        a.about = SourceInfo.aboutPlaylist("Main", a.name);
        Vod.Item b = new Vod.Item("movie", "Glass Harbour (2024) 4K DV Atmos", "p1|x:movie:9.mkv", "", "", "", "2024", "", "p1");
        b.label = Vod.label("Spare", b.name, b.url);
        b.about = SourceInfo.aboutPlaylist("Spare", b.name);
        Vod.Item c = new Vod.Item("movie", "Glass Harbour (2024) 4K", "p1|x:movie:8.mkv", "", "", "", "2024", "", "p1");
        c.label = Vod.label("Spare", c.name, c.url);
        c.about = SourceInfo.aboutPlaylist("Spare", c.name);
        List<Vod.Item> sorted = Vod.bestFirst(Arrays.asList(a, c, b));
        assertEquals(Arrays.asList(b, c, a), sorted);
    }

    private static Vod.Episode episode(String url, String title, String label, String about) {
        Vod.Episode e = new Vod.Episode(1, 2, title, url, "", "");
        e.label = label;
        e.about = about;
        return e;
    }

    @Test public void anEpisodesCopiesBestFirstTorBoxsFirst() {
        Vod.Episode main = episode("x:series:11.mkv", "Low Water", "Main · MKV", SourceInfo.aboutPlaylist("Main", "Paper Lanterns Low Water"));
        Vod.Episode uhd = episode("p1|x:series:12.mkv", "", "Spare · 4K · MKV", SourceInfo.aboutPlaylist("Spare", "Paper Lanterns 4K"));
        Vod.Episode tb1080 = episode("torbox:torrent:5:2", "", "TorBox · 1080p · MKV",
                SourceInfo.aboutTorBox("Paper.Lanterns.S01E02.1080p.mkv", 2 * GB, "Paper.Lanterns.S01"));
        Vod.Episode tb4k = episode("torbox:torrent:6:2", "", "TorBox · 4K · MKV",
                SourceInfo.aboutTorBox("Paper.Lanterns.S01E02.2160p.mkv", 6 * GB, "Paper.Lanterns.S01.2160p"));
        main.alternates.addAll(Arrays.asList(uhd, tb4k, tb1080));

        // TorBox's first (in TorBox's own order), then the playlists' the best first: the 4K before the plain one.
        Vod.Episode first = Vod.bestFirst(main, true);
        assertNotSame(main, first);
        assertEquals("torbox:torrent:6:2", first.url);
        assertEquals("Low Water", first.title);
        List<String> rest = new ArrayList<>();
        for (Vod.Episode e : first.alternates) rest.add(e.url);
        assertEquals(Arrays.asList("torbox:torrent:5:2", "p1|x:series:12.mkv", "x:series:11.mkv"), rest);
        // The episode it was, now a backup, carries no list of its own.
        assertTrue(first.alternates.get(2).alternates.isEmpty());

        // TorBox's last, as Settings may say: the playlists' 4K first.
        Vod.Episode main2 = episode("x:series:11.mkv", "Low Water", "Main · MKV", SourceInfo.aboutPlaylist("Main", "Paper Lanterns Low Water"));
        main2.alternates.addAll(Arrays.asList(uhd, tb1080));
        Vod.Episode last = Vod.bestFirst(main2, false);
        assertEquals("p1|x:series:12.mkv", last.url);
        assertEquals("torbox:torrent:5:2", last.alternates.get(last.alternates.size() - 1).url);

        // Already the best: the same episode, its list as it was.
        Vod.Episode only = episode("torbox:torrent:6:2", "", "TorBox · 4K · MKV",
                SourceInfo.aboutTorBox("Paper.Lanterns.S01E02.2160p.mkv", 6 * GB, "Paper.Lanterns.S01.2160p"));
        only.alternates.add(episode("x:series:11.mkv", "", "Main · MKV", SourceInfo.aboutPlaylist("Main", "Paper Lanterns")));
        assertSame(only, Vod.bestFirst(only, true));
        assertEquals(1, only.alternates.size());
    }
}
