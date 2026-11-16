package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** SourceInfo and the source sheet's sums: TorBox's or a playlist's, and the picture and sound a name says. */
public class SourceInfoTest {
    private static void says(String name, String video, String hdr, String audio) {
        assertEquals(name + ": picture", video, SourceInfo.video(name));
        assertEquals(name + ": HDR", hdr, SourceInfo.hdr(name));
        assertEquals(name + ": sound", audio, SourceInfo.audio(name));
    }

    @Test public void namesSayPictureHdrAndSound() {
        says("Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR10.H.265-GRP.mkv", "4K", "Dolby Vision", "Dolby Atmos");
        says("Glass.Harbour.2024.1080p.BluRay.TrueHD.7.1.x264.mkv", "1080p", "", "Dolby TrueHD 7.1");
        says("Film.2019.720p.WEB.H264.AAC2.0.mp4", "720p", "", "AAC Stereo");
        says("Film.2021.2160p.UHD.BluRay.REMUX.HDR10+.DTS-HD.MA.7.1.mkv", "4K", "HDR10+", "DTS-HD 7.1");
        says("Film 2020 1080p HLG DD5.1", "1080p", "HLG", "Dolby Digital 5.1");
        says("EN - The Long Tide (2023) 4K HDR", "4K", "HDR", "");
        says("Movie.2024.1080p.WEB-DL.EAC3.6CH.mkv", "1080p", "", "Dolby Digital+ 5.1");
        says("Film.1999.DVDRip.XviD.MP3.avi", "SD", "", "MPEG");
        says("Film.2023.1080p.WEB-DL.DD+5.1.H.264.mkv", "1080p", "", "Dolby Digital+ 5.1");
        says("Film.2022.2160p.BluRay.DTS-X.7.1.mkv", "4K", "", "DTS:X 7.1");
        says("Film.2022.1080p.DDPA5.1.mkv", "1080p", "", "Dolby Atmos");
    }

    @Test public void numbersAreNotChannels() {
        // A year beside the picture, and the codec's bit depth, come apart: no "5.1" or "2.0" read out of them.
        says("Movie.2024.2160p.x265.10bit.mkv", "4K", "", "");
        says("Movie.2020.1080p.H.264.mkv", "1080p", "", "");
        assertEquals("", SourceInfo.audio("Movie 2.0 (2001)"));
    }

    @Test public void hdWithoutLines() {
        assertTrue(SourceInfo.hd("Some Film HD"));
        assertFalse(SourceInfo.hd("Some Film 1080p HD"));
        assertFalse(SourceInfo.hd("Some Film"));
    }

    @Test public void torBoxCopiesKnownAndRead() {
        String[] play = {"torbox:torrent:1:2", "", "", "TorBox · 4K · MKV",
                SourceInfo.aboutTorBox("Dune.Part.Two.2024.2160p.WEB-DL.DV.mkv", 15_000_000_000L, "Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.Atmos.DV")};
        SourceInfo s = SourceInfo.of(play);
        assertTrue(s.torbox);
        assertEquals("TorBox", s.from());
        assertEquals("", s.playlist);
        assertEquals("4K", s.video);
        assertEquals("Dolby Vision", s.hdr);
        // The download's name says the sound when the file's doesn't.
        assertEquals("Dolby Atmos", s.audio);
        assertEquals("MKV", s.container);
        assertEquals(15_000_000_000L, s.size);
        assertEquals("Dune.Part.Two.2024.2160p.WEB-DL.DV.mkv · 14.0 GB", SourceSheet.detail(s, play));
    }

    @Test public void playlistCopiesKnownAndRead() {
        String[] play = {"p1|x:movie:5.mkv", "", "", "Backup · 1080p · MKV", SourceInfo.aboutPlaylist("Backup", "EN - Glass Harbour (2024) FHD")};
        SourceInfo s = SourceInfo.of(play);
        assertFalse(s.torbox);
        assertEquals("Backup", s.playlist);
        assertEquals("IPTV · Backup", s.from());
        assertEquals("1080p", s.video);
        assertEquals("MKV", s.container);
        assertEquals("EN - Glass Harbour (2024) FHD · MKV", SourceSheet.detail(s, play));
    }

    @Test public void olderPlaysFallBackOnTheirLabels() {
        SourceInfo iptv = SourceInfo.of(new String[]{"http://example.invalid/film.mp4", "", "", "Main · 4K · MP4"});
        assertFalse(iptv.torbox);
        assertEquals("Main", iptv.playlist);
        assertEquals("4K", iptv.video);
        assertEquals("MP4", iptv.container);
        SourceInfo tb = SourceInfo.of(new String[]{"torbox:torrent:3:4", "", "", "TorBox · 1080p · MKV"});
        assertTrue(tb.torbox);
        assertEquals("1080p", tb.video);
        assertEquals("MKV", tb.container);
        SourceInfo bare = SourceInfo.of(new String[]{"http://example.invalid/live/1.ts", "", ""});
        assertFalse(bare.torbox);
        assertEquals("IPTV", bare.from());
    }

    @Test public void sizesAsPeopleSayThem() {
        assertEquals("14.0 GB", SourceInfo.size(15_000_000_000L));
        assertEquals("850 MB", SourceInfo.size(850L * 1024 * 1024));
        assertEquals("120 GB", SourceInfo.size(120L * 1024 * 1024 * 1024));
        assertEquals("", SourceInfo.size(0));
    }

    @Test public void sheetGroupsBySourceInFirstOrder() {
        List<SourceInfo> infos = Arrays.asList(
                SourceInfo.of(new String[]{"torbox:torrent:1:1", "", "", "TorBox · 4K · MKV"}),
                SourceInfo.of(new String[]{"http://a.invalid/1.mkv", "", "", "Main · 1080p · MKV"}),
                SourceInfo.of(new String[]{"torbox:torrent:2:1", "", "", "TorBox · 1080p · MKV"}),
                SourceInfo.of(new String[]{"p2|x:movie:9.mp4", "", "", "Backup · HD · MP4"}));
        List<List<Integer>> g = SourceSheet.groups(infos);
        assertEquals(3, g.size());
        assertEquals(Arrays.asList(0, 2), g.get(0));
        assertEquals(Arrays.asList(1), g.get(1));
        assertEquals(Arrays.asList(3), g.get(2));
    }

    @Test public void copiesFoundAgainMergeWithoutRepeating() {
        List<String[]> plays = new ArrayList<>();
        plays.add(new String[]{"torbox:torrent:1:1", "", "", "TorBox · 4K · MKV"});
        List<String[]> found = Arrays.asList(
                new String[]{"torbox:torrent:1:1", "", "", "TorBox · 4K · MKV", SourceInfo.aboutTorBox("Film.2024.2160p.mkv", 9, "")},
                new String[]{"p1|x:movie:5.mkv", "", "", "Main · 1080p · MKV", SourceInfo.aboutPlaylist("Main", "Film (2024)")});
        assertEquals(1, Vod.mergePlays(plays, found));
        assertEquals(2, plays.size());
        // The one already there kept its place and took the about it lacked.
        assertEquals("torbox:torrent:1:1", plays.get(0)[0]);
        assertEquals("Film.2024.2160p.mkv", SourceInfo.of(plays.get(0)).name);
        assertEquals("p1|x:movie:5.mkv", plays.get(1)[0]);
        assertEquals(0, Vod.mergePlays(plays, found));
        assertEquals(2, plays.size());
    }

    @Test public void episodesKeepWhatTheyAreThroughJson() {
        Vod.Episode e = new Vod.Episode(1, 2, "Two", "torbox:torrent:7:3", "", "");
        e.label = "TorBox · 1080p · MKV";
        e.about = SourceInfo.aboutTorBox("Show.S01E02.1080p.mkv", 2_000_000_000L, "Show.S01.1080p");
        Vod.Episode alt = new Vod.Episode(1, 2, "Two", "p1|x:series:9.mkv", "", "");
        alt.label = "Main · HD";
        alt.about = SourceInfo.aboutPlaylist("Main", "Show HD");
        e.alternates.add(alt);
        List<Vod.Episode> back = Vod.episodesFrom(Vod.episodesJson(Arrays.asList(e)));
        assertEquals(1, back.size());
        List<String[]> plays = back.get(0).plays();
        assertTrue(SourceInfo.of(plays.get(0)).torbox);
        assertEquals(2_000_000_000L, SourceInfo.of(plays.get(0)).size);
        assertEquals("IPTV · Main", SourceInfo.of(plays.get(1)).from());
    }
}
