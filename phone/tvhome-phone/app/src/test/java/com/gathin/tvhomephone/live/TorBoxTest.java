package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** TorBox: release names read, the family's files matched to a film or a series, the order offered, the play link. */
public class TorBoxTest {
    private static final long GB = 1024L * 1024 * 1024, MB = 1024L * 1024;

    private static TorBox.File file(int item, int id, String path, String itemName, long size) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return new TorBox.File("torrent", item, id, name, path, itemName, size);
    }

    private static void is(String raw, String title, int year, int season, int episode) {
        TorBox.Release r = TorBox.parse(raw);
        assertEquals(raw + ": title", title, r.title);
        assertEquals(raw + ": year", year, r.year);
        assertEquals(raw + ": season", season, r.season);
        assertEquals(raw + ": episode", episode, r.episode);
    }

    @Test public void filmNamesRead() {
        is("Glass.Harbour.2024.1080p.WEB-DL.DDP5.1.H.264-GRP.mkv", "Glass Harbour", 2024, -1, -1);
        is("Blade.Runner.2049.2017.2160p.UHD.BluRay.x265.mkv", "Blade Runner 2049", 2017, -1, -1);
        is("Blade.Runner.2049.1080p.mkv", "Blade Runner 2049", 0, -1, -1);
        is("1917.2019.1080p.mkv", "1917", 2019, -1, -1);
        is("2001.A.Space.Odyssey.1968.1080p.BluRay.mkv", "2001 A Space Odyssey", 1968, -1, -1);
        is("Charlotte's.Web.2006.1080p.mkv", "Charlotte's Web", 2006, -1, -1);
        is("Movie.Title.2010.EXTENDED.1080p.BluRay.mkv", "Movie Title", 2010, -1, -1);
        is("Movie Title EXTENDED 1080p.mkv", "Movie Title", 0, -1, -1);
        is("Glass Harbour (2024) [1080p].mp4", "Glass Harbour", 2024, -1, -1);
        is("www.Site.org - Glass.Harbour.2024.1080p.mkv", "Glass Harbour", 2024, -1, -1);
        is("Ocean's.8.2018.720p.mkv", "Ocean's 8", 2018, -1, -1);
        is("Some Folder/Glass.Harbour.2024.1080p.mkv", "Glass Harbour", 2024, -1, -1);
    }

    @Test public void episodeNamesRead() {
        is("The.Long.Tide.S02E05.Episode.Name.720p.WEB.h264.mkv", "The Long Tide", 0, 2, 5);
        is("The Long Tide S02 E05.mkv", "The Long Tide", 0, 2, 5);
        is("Show.Name.S01.1080p.WEBRip.x265", "Show Name", 0, 1, -1);
        is("Show Name Season 3", "Show Name", 0, 3, -1);
        is("Show Name Season 3 Episode 4.mkv", "Show Name", 0, 3, 4);
        is("Show.Name.3x07.HDTV.avi", "Show Name", 0, 3, 7);
        is("Doctor.Who.2005.S13E01.mkv", "Doctor Who", 2005, 13, 1);
        is("The.Office.US.S01E01.mkv", "The Office", 0, 1, 1);
        is("[SubsGroup] Harbour Lights - 05 (1080p) [ABCD1234].mkv", "Harbour Lights", 0, -1, 5);
        is("Episode 3.mkv", "", 0, -1, 3);
        is("The Long Tide Complete Series", "The Long Tide", 0, -1, -1);
        is("Show.S01E05E06.mkv", "Show", 0, 1, 5);
    }

    @Test public void onlyFilmSizedPicturesCount() {
        assertTrue(TorBox.video("Movie.2010.1080p.mkv", 4 * GB));
        assertTrue(TorBox.video("Movie.mkv", 0));
        assertFalse(TorBox.video("Movie.2010.1080p.sample.mkv", 50 * MB));
        assertFalse(TorBox.video("Sample/movie-sample.mkv", 80 * MB));
        assertFalse(TorBox.video("Movie.2010.nfo", 2048));
        assertFalse(TorBox.video("Featurette.mkv", 5 * MB));
        assertFalse(TorBox.video("Movie.2010.srt", 90 * 1024));
    }

    @Test public void filmsMatchedByNameAndYear() {
        List<TorBox.File> lib = new ArrayList<>();
        lib.add(file(1, 1, "Glass.Harbour.2024.1080p.WEB-DL/Glass.Harbour.2024.1080p.WEB-DL.mkv", "Glass.Harbour.2024.1080p.WEB-DL", 6 * GB));
        lib.add(file(1, 2, "Glass.Harbour.2024.1080p.WEB-DL/Sample/sample.mkv", "Glass.Harbour.2024.1080p.WEB-DL", 40 * MB));
        lib.add(file(2, 1, "Glass.Harbour.2024.2160p.UHD.BluRay/Glass.Harbour.2024.2160p.UHD.BluRay.mkv", "Glass.Harbour.2024.2160p.UHD.BluRay", 30 * GB));
        lib.add(file(3, 1, "Glass Harbour 1988 720p.mkv", "Glass Harbour 1988 720p", 2 * GB));
        // An obfuscated name, the only picture in its download: matched by the download's name.
        TorBox.File gh = file(4, 7, "Glass.Harbour.2024.720p.WEB/gh-720p.mkv", "Glass.Harbour.2024.720p.WEB", 3 * GB);
        gh.single = true;
        lib.add(gh);
        // A collection: its pictures are other films, never this one by the download's name.
        lib.add(file(5, 1, "Glass Harbour Collection/Other Film 2010.mkv", "Glass Harbour Collection", 4 * GB));
        lib.add(file(5, 2, "Glass Harbour Collection/Third Film 2012.mkv", "Glass Harbour Collection", 4 * GB));
        // An episode of a series of the same name is not the film.
        lib.add(file(6, 1, "Glass.Harbour.S01E01.mkv", "Glass.Harbour.S01E01", 1 * GB));

        List<TorBox.File> got = TorBox.films(lib, "Glass Harbour", "", "2024");
        assertEquals(3, got.size());
        // The best first: the 4K, then the 1080p, then the 720p.
        assertEquals("torbox:torrent:2:1", got.get(0).stored());
        assertEquals("torbox:torrent:1:1", got.get(1).stored());
        assertEquals("torbox:torrent:4:7", got.get(2).stored());

        // No year to go by: the 1988 film too.
        assertEquals(4, TorBox.films(lib, "Glass Harbour", "", "").size());
        // Found by its original title as well.
        assertEquals(3, TorBox.films(lib, "Le Port de Verre", "Glass Harbour", "2024-03-01").size());
        assertTrue(TorBox.films(lib, "Another Film", "", "2024").isEmpty());
    }

    @Test public void yearOnlyAndApostropheTitlesMatch() {
        List<TorBox.File> lib = Arrays.asList(
                file(1, 1, "1917.2019.1080p.mkv", "1917.2019.1080p", 8 * GB),
                file(2, 1, "Oceans.Eleven.2001.1080p.mkv", "Oceans.Eleven.2001.1080p", 8 * GB));
        assertEquals(1, TorBox.films(lib, "1917", "", "2019").size());
        assertEquals(1, TorBox.films(lib, "Ocean's Eleven", "", "2001").size());
    }

    @Test public void episodesFromNamesFoldersAndPacks() {
        List<TorBox.File> lib = new ArrayList<>();
        lib.add(file(1, 1, "The.Long.Tide.S02.1080p.WEB/The.Long.Tide.S02E01.1080p.WEB.mkv", "The.Long.Tide.S02.1080p.WEB", 2 * GB));
        lib.add(file(1, 2, "The.Long.Tide.S02.1080p.WEB/The.Long.Tide.S02E02.1080p.WEB.mkv", "The.Long.Tide.S02.1080p.WEB", 2 * GB));
        lib.add(file(2, 5, "The Long Tide Complete Series/Season 1/Episode 3.mkv", "The Long Tide Complete Series", 1 * GB));
        lib.add(file(3, 1, "Long.Tide.Rising.S01E01.mkv", "Long.Tide.Rising.S01E01", 1 * GB));
        lib.add(file(4, 1, "The.Long.Tide.2019.1080p.mkv", "The.Long.Tide.2019.1080p", 9 * GB));

        List<TorBox.File> got = TorBox.episodeFiles(lib, "The Long Tide", "");
        assertEquals(3, got.size());
        List<String> eps = new ArrayList<>();
        for (TorBox.File f : got) {
            TorBox.Release r = TorBox.episode(f);
            assertNotNull(r);
            eps.add(r.season + "x" + r.episode);
        }
        assertTrue(eps.containsAll(Arrays.asList("2x1", "2x2", "1x3")));
        // An episode whose name and folders say no season: the first.
        assertEquals(1, TorBox.episode(file(9, 1, "E07.mkv", "Harbour Lights", GB)).season);
        assertNull(TorBox.episode(file(9, 2, "Harbour.Lights.2020.mkv", "Harbour Lights", GB)));
    }

    @Test public void bestVersionFirstWithNoSizeLimit() {
        TorBox.File remux4k = file(1, 1, "Film.2024.2160p.UHD.BluRay.REMUX.mkv", "x", 61 * GB);
        TorBox.File web4k = file(2, 1, "Film.2024.2160p.WEB-DL.mkv", "x", 18 * GB);
        TorBox.File fhd = file(3, 1, "Film.2024.1080p.mkv", "x", 9 * GB);
        TorBox.File remux = file(4, 1, "Film.2024.1080p.Remux.mkv", "x", 35 * GB);
        TorBox.File hd = file(5, 1, "Film.2024.720p.mkv", "x", 4 * GB);
        TorBox.File sd = file(6, 1, "Film.2024.480p.mkv", "x", GB);
        TorBox.File avi = file(7, 1, "Film.2024.2160p.avi", "x", 30 * GB);
        List<TorBox.File> all = new ArrayList<>(Arrays.asList(avi, sd, hd, remux, fhd, web4k, remux4k));
        all.sort(TorBox::compare);
        // 4K first however big, the bigger first; then 1080p, 720p, the rest; AVI after them all, even in 4K.
        assertEquals(Arrays.asList(remux4k, web4k, remux, fhd, hd, sd, avi), all);
        // The same through sortBest, each name read once.
        List<TorBox.File> again = new ArrayList<>(Arrays.asList(avi, sd, hd, remux, fhd, web4k, remux4k));
        TorBox.sortBest(again);
        assertEquals(all, again);
    }

    @Test public void hdrThenSurroundBeforeSize() {
        TorBox.File sdr = file(1, 1, "Film.2024.2160p.WEB-DL.mkv", "x", 40 * GB);
        TorBox.File dv = file(2, 1, "Film.2024.2160p.WEB-DL.DV.mkv", "x", 16 * GB);
        TorBox.File hdr10 = file(3, 1, "Film.2024.2160p.HDR10.DDP5.1.mkv", "x", 15 * GB);
        TorBox.File hdrStereo = file(4, 1, "Film.2024.2160p.HDR.AAC2.0.mkv", "x", 20 * GB);
        TorBox.File atmos = file(5, 1, "Film.2024.2160p.DV.Atmos.mkv", "x", 12 * GB);
        List<TorBox.File> all = new ArrayList<>(Arrays.asList(sdr, hdrStereo, hdr10, dv, atmos));
        all.sort(TorBox::compare);
        // HDR before SDR; within HDR, Atmos or surround before stereo (or sound not said); then the bigger.
        assertEquals(Arrays.asList(hdr10, atmos, hdrStereo, dv, sdr), all);
        // What a download's name says counts for a file whose own name says nothing.
        TorBox.File plain = file(6, 1, "gh-main.mkv", "Glass.Harbour.2024.2160p.DV.TrueHD.7.1", 10 * GB);
        assertEquals("Dolby Vision", TorBox.info(plain).hdr);
        assertTrue(TorBox.compare(plain, sdr) < 0);
    }

    @Test public void labelsSayTheSourceAndPicture() {
        assertEquals("TorBox · 1080p · MKV", TorBox.label(file(1, 1, "Glass.Harbour.2024.1080p.WEB-DL.mkv", "", GB)));
        assertEquals("TorBox · 4K · MP4", TorBox.label(file(1, 1, "gh.mp4", "Glass.Harbour.2024.2160p", GB)));
        assertEquals("TorBox", TorBox.label(file(1, 1, "gh", "", GB)));
    }

    @Test public void finishedDownloadsOnly() throws Exception {
        JSONArray items = new JSONArray("[" +
                "{\"id\":11,\"name\":\"Glass.Harbour.2024.1080p\",\"download_finished\":true,\"files\":[" +
                "  {\"id\":0,\"name\":\"Glass.Harbour.2024.1080p/Glass.Harbour.2024.1080p.mkv\",\"short_name\":\"Glass.Harbour.2024.1080p.mkv\",\"size\":6442450944}," +
                "  {\"id\":1,\"name\":\"Glass.Harbour.2024.1080p/sample.mkv\",\"short_name\":\"sample.mkv\",\"size\":41943040}]}," +
                "{\"id\":12,\"name\":\"Still.Coming.2025\",\"download_finished\":false,\"download_present\":false,\"files\":[" +
                "  {\"id\":0,\"name\":\"Still.Coming.2025.mkv\",\"size\":6442450944}]}," +
                "{\"id\":13,\"name\":\"Cached.Film.2023\",\"download_finished\":false,\"download_present\":true,\"files\":[" +
                "  {\"id\":4,\"name\":\"Cached.Film.2023/Cached.Film.2023.mkv\",\"size\":6442450944}]}]");
        List<TorBox.File> out = new ArrayList<>();
        TorBox.files(out, items, "torrent");
        assertEquals(3, out.size());
        assertEquals("Glass.Harbour.2024.1080p.mkv", out.get(0).name);
        assertTrue(out.get(0).single);
        assertFalse(out.get(1).single);
        // No short name: the last part of its path.
        assertEquals("Cached.Film.2023.mkv", out.get(2).name);
        assertEquals("torbox:torrent:13:4", out.get(2).stored());
        TorBox.files(out, null, "usenet");
        assertEquals(3, out.size());
    }

    @Test public void refusalsKnown() {
        assertTrue(TorBox.refusal("BAD_TOKEN"));
        assertTrue(TorBox.refusal("NO_AUTH"));
        assertTrue(TorBox.refusal("AUTH_ERROR"));
        assertFalse(TorBox.refusal("DATABASE_ERROR"));
        assertFalse(TorBox.refusal(null));
    }

    @Test public void playLinkAsTorBoxAsks() {
        assertEquals("https://api.torbox.app/v1/api/torrents/requestdl?token=k-1&torrent_id=12&file_id=3&zip_link=false&redirect=true",
                TorBox.address("k-1", "torbox:torrent:12:3"));
        assertEquals("https://api.torbox.app/v1/api/usenet/requestdl?token=k-1&usenet_id=5&file_id=0&zip_link=false&redirect=true",
                TorBox.address("k-1", "torbox:usenet:5:0"));
        assertEquals("https://api.torbox.app/v1/api/webdl/requestdl?token=k-1&web_id=9&file_id=2&zip_link=false&redirect=true",
                TorBox.address("k-1", "torbox:webdl:9:2"));
        assertNull(TorBox.address("", "torbox:torrent:12:3"));
        assertNull(TorBox.address("k-1", "torbox:torrent:x:3"));
        assertNull(TorBox.address("k-1", "http://example.invalid/film.mkv"));
        assertTrue(TorBox.ours("torbox:torrent:12:3"));
        assertFalse(TorBox.ours("p1|x:movie:1.mkv"));
    }
}
