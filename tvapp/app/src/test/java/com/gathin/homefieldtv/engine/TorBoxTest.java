package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** TorBox's pure parts, with invented films, shows and files (and a made-up key that is no one's). */
public class TorBoxTest {
    private static final long MB = 1024L * 1024, GB = 1024 * MB;

    private static TorBox.File file(int item, int id, String name, String download, long size) {
        return new TorBox.File("torrent", item, id, name, download + "/" + name, download, size);
    }

    private static TorBox.File single(int item, int id, String name, String download, long size) {
        TorBox.File f = file(item, id, name, download, size);
        f.single = true;
        return f;
    }

    private static List<String> names(List<TorBox.File> files) {
        List<String> out = new ArrayList<>();
        for (TorBox.File f : files) out.add(f.name);
        return out;
    }

    @Test public void releaseNamesRead() {
        TorBox.Release r = TorBox.parse("Glass.Harbour.2024.1080p.WEB-DL.DDP5.1.H.264-GRP.mkv");
        assertEquals("Glass Harbour", r.title);
        assertEquals(2024, r.year);
        assertFalse(r.episodic());

        r = TorBox.parse("Paper.Lanterns.S02E05.720p.HDTV.x264.mkv");
        assertEquals("Paper Lanterns", r.title);
        assertEquals(2, r.season);
        assertEquals(5, r.episode);

        r = TorBox.parse("Paper Lanterns Season 2");
        assertEquals("Paper Lanterns", r.title);
        assertEquals(2, r.season);
        assertEquals(-1, r.episode);

        // A fansub's numbering, after its group's tag.
        r = TorBox.parse("[Moonfish] Copper Kites - 05 (1080p).mkv");
        assertEquals("Copper Kites", r.title);
        assertEquals(5, r.episode);

        // A title that is a year, and one with a year-like number in it.
        r = TorBox.parse("1917.2019.2160p.UHD.BluRay.mkv");
        assertEquals("1917", r.title);
        assertEquals(2019, r.year);
        r = TorBox.parse("Harbour.Lights.2049.2017.1080p.mkv");
        assertEquals("Harbour Lights 2049", r.title);
        assertEquals(2017, r.year);

        // A site's name before it, an edition's word after it, a country after a show's name.
        assertEquals("Glass Harbour", TorBox.parse("www.Site.com - Glass.Harbour.2024.1080p.mkv").title);
        assertEquals("Glass Harbour", TorBox.parse("Glass.Harbour.Extended.1080p.mkv").title);
        assertEquals("The Night Office", TorBox.parse("The.Night.Office.US.S01E01.mkv").title);
    }

    @Test public void onlyPicturesThatPlay() {
        assertTrue(TorBox.video("Glass.Harbour.2024.1080p.mkv", 2 * GB));
        assertTrue(TorBox.video("Glass.Harbour.2024.1080p.mkv", 0));
        assertFalse(TorBox.video("Glass.Harbour.Sample.mkv", 50 * MB));
        assertFalse(TorBox.video("Glass.Harbour.2024.1080p.mkv", 5 * MB));
        assertFalse(TorBox.video("Glass.Harbour.2024.nfo", 2 * GB));
        assertFalse(TorBox.video(null, 2 * GB));
    }

    @Test public void finishedFilesOnly() throws Exception {
        JSONArray items = new JSONArray("[" +
                "{\"id\":12,\"name\":\"Glass.Harbour.2024.1080p.WEB-DL\",\"download_finished\":true,\"files\":[" +
                "  {\"id\":0,\"name\":\"Glass.Harbour.2024.1080p.WEB-DL/Glass.Harbour.2024.1080p.WEB-DL.mkv\",\"size\":" + 6 * GB + "}," +
                "  {\"id\":1,\"name\":\"Glass.Harbour.2024.1080p.WEB-DL/Sample/sample.mkv\",\"size\":" + 40 * MB + "}," +
                "  {\"id\":2,\"name\":\"Glass.Harbour.2024.1080p.WEB-DL/info.nfo\",\"size\":2000}]}," +
                "{\"id\":13,\"name\":\"Copper.Kites.S01\",\"download_finished\":false,\"files\":[" +
                "  {\"id\":0,\"name\":\"Copper.Kites.S01/Copper.Kites.S01E01.mkv\",\"size\":" + GB + "}]}," +
                "{\"id\":14,\"name\":\"Paper.Lanterns.S01E01\",\"download_finished\":false,\"download_present\":true,\"files\":[" +
                "  {\"id\":5,\"short_name\":\"Paper.Lanterns.S01E01.mkv\",\"name\":\"x/Paper.Lanterns.S01E01.mkv\",\"size\":" + GB + "}]}]");
        List<TorBox.File> out = new ArrayList<>();
        TorBox.files(out, items, "torrent");
        assertEquals(Arrays.asList("Glass.Harbour.2024.1080p.WEB-DL.mkv", "sample.mkv", "info.nfo", "Paper.Lanterns.S01E01.mkv"), names(out));
        // The film the only picture in its download; the sample isn't one.
        assertTrue(out.get(0).single);
        assertFalse(out.get(1).single);
        assertEquals("torbox:torrent:12:0", out.get(0).stored());
        assertEquals("torbox:torrent:14:5", out.get(3).stored());
        assertEquals("Glass.Harbour.2024.1080p.WEB-DL", out.get(0).item);
    }

    @Test public void everyVersionOfAFilmTheBestFirst() {
        List<TorBox.File> lib = Arrays.asList(
                file(1, 0, "Glass.Harbour.2024.2160p.UHD.BluRay.x265.mkv", "Glass.Harbour.2024.2160p.UHD.BluRay.x265", 40 * GB),
                file(2, 0, "Glass.Harbour.2024.1080p.WEB-DL.mkv", "Glass.Harbour.2024.1080p.WEB-DL", 6 * GB),
                file(3, 0, "Glass.Harbour.2024.720p.mkv", "Glass.Harbour.2024.720p", 2 * GB),
                file(4, 0, "Glass.Harbour.2024.1080p.avi", "Glass.Harbour.2024.1080p", 3 * GB),
                // Another film of the same name, years apart; an episode of a show of that name.
                file(5, 0, "Glass.Harbour.1998.1080p.mkv", "Glass.Harbour.1998.1080p", 8 * GB),
                file(6, 0, "Glass.Harbour.S01E01.1080p.mkv", "Glass.Harbour.S01", 2 * GB),
                // A file whose own name says nothing: its download's name does, as the only picture in it.
                single(7, 3, "abc123.mkv", "Glass.Harbour.2024.2160p.WEB-DL", 15 * GB));
        List<TorBox.File> got = TorBox.films(lib, "Glass Harbour", null, "2024");
        // The best first, with no limit on size: both 4K (the bigger first), then 1080p, 720p, and the AVI after them all.
        assertEquals(Arrays.asList("Glass.Harbour.2024.2160p.UHD.BluRay.x265.mkv", "abc123.mkv", "Glass.Harbour.2024.1080p.WEB-DL.mkv",
                "Glass.Harbour.2024.720p.mkv", "Glass.Harbour.2024.1080p.avi"), names(got));
        // The other film's year, and none given.
        assertEquals(Arrays.asList("Glass.Harbour.1998.1080p.mkv"), names(TorBox.films(lib, "Glass Harbour", null, "1998")));
        assertEquals(6, TorBox.films(lib, "Glass Harbour", null, "").size());
        // Names alike as the playlists' are.
        assertTrue(TorBox.sameTitle("Oceans Eleven", "Ocean's Eleven", null));
        assertTrue(TorBox.sameTitle("1917", "1917", null));
        assertFalse(TorBox.sameTitle("Glass Harbour", "Glass Harbor", null));
    }

    @Test public void anEpisodesVersions() {
        List<TorBox.File> lib = Arrays.asList(
                file(1, 0, "Paper.Lanterns.S01E01.1080p.mkv", "Paper.Lanterns.S01", GB),
                file(1, 1, "Paper.Lanterns.S01E02.1080p.mkv", "Paper.Lanterns.S01", GB),
                file(2, 0, "Paper.Lanterns.S01E01.720p.mkv", "Paper.Lanterns.S01E01.720p", GB / 2),
                new TorBox.File("torrent", 3, 4, "Paper Lanterns - E03.mkv", "Paper Lanterns/Season 2/Paper Lanterns - E03.mkv",
                        "Paper Lanterns", GB),
                file(4, 0, "Copper.Kites.S01E01.1080p.mkv", "Copper.Kites.S01", GB));
        assertEquals(4, TorBox.episodeFiles(lib, "Paper Lanterns", null).size());
        assertEquals(Arrays.asList("Paper.Lanterns.S01E01.1080p.mkv", "Paper.Lanterns.S01E01.720p.mkv"),
                names(TorBox.episodeVersions(lib, "Paper Lanterns", null, 1, 1)));
        // The season from its folder.
        assertEquals(Arrays.asList("Paper Lanterns - E03.mkv"), names(TorBox.episodeVersions(lib, "Paper Lanterns", null, 2, 3)));
        assertTrue(TorBox.episodeVersions(lib, "Paper Lanterns", null, 3, 1).isEmpty());
    }

    @Test public void aFileAsThePlayersCopy() {
        Catalog.Play p = TorBox.play(file(12, 3, "Glass.Harbour.2024.1080p.WEB-DL.mkv", "Glass.Harbour.2024.1080p.WEB-DL", 6 * GB));
        assertTrue(p.torbox);
        // Kept without the key; its address made only as it starts.
        assertEquals("torbox:torrent:12:3", p.url);
        assertTrue(TorBox.ours(p.url));
        assertEquals(Catalog.PICTURE_1080, p.picture);
        assertEquals("MKV", p.kind);
        assertEquals(6 * GB, p.size);
        assertEquals("", p.playlist);
    }

    @Test public void torboxFirstOrLast() {
        Catalog.Play a = new Catalog.Play("torbox:torrent:1:0", null, null), b = new Catalog.Play("torbox:torrent:2:0", null, null);
        Catalog.Play mine = new Catalog.Play("http://example.com/movie/1.mkv", null, null);
        Catalog.Play copy = new Catalog.Play("http://example.org/movie/9.mp4", null, null);
        mine.backups.add(copy);
        assertEquals(Arrays.asList(a, b, mine, copy), TorBox.order(Arrays.asList(a, b), mine, true));
        assertEquals(Arrays.asList(mine, copy, a, b), TorBox.order(Arrays.asList(a, b), mine, false));
        // The playlist's copy gone: TorBox's alone; none at all.
        assertEquals(Arrays.asList(a, b), TorBox.order(Arrays.asList(a, b), null, true));
        assertTrue(TorBox.order(new ArrayList<>(), null, true).isEmpty());
    }

    @Test public void theAddressAsTorBoxAsksForIt() {
        assertEquals("https://api.torbox.app/v1/api/torrents/requestdl?token=made-up-key&torrent_id=12&file_id=3&zip_link=false&redirect=true",
                TorBox.address("made-up-key", "torbox:torrent:12:3"));
        assertTrue(TorBox.address("made-up-key", "torbox:usenet:7:1").contains("/usenet/requestdl?") );
        assertTrue(TorBox.address("made-up-key", "torbox:usenet:7:1").contains("&usenet_id=7&file_id=1"));
        assertTrue(TorBox.address("made-up-key", "torbox:webdl:8:2").contains("/webdl/requestdl?"));
        assertTrue(TorBox.address("made-up-key", "torbox:webdl:8:2").contains("&web_id=8&file_id=2"));
        assertNull(TorBox.address("", "torbox:torrent:12:3"));
        assertNull(TorBox.address("made-up-key", "torbox:torrent:x:3"));
        assertNull(TorBox.address("made-up-key", "http://example.com/movie/1.mkv"));
    }

    @Test public void aRefusedKeySaidAsSuch() {
        assertTrue(TorBox.refusal("BAD_TOKEN"));
        assertTrue(TorBox.refusal("NO_AUTH"));
        assertFalse(TorBox.refusal("DATABASE_ERROR"));
        assertFalse(TorBox.refusal(null));
        assertEquals("Connected  ·  1 film or episode ready", TorBox.checked(1));
        assertEquals("Connected  ·  12 films and episodes ready", TorBox.checked(12));
    }

    @Test public void kindsAndPicturesNamed() {
        assertEquals("MKV", Catalog.kindOf("mkv"));
        assertEquals("MP4", Catalog.kindOf("http://example.com/movie/u/p/123.mp4"));
        assertEquals("", Catalog.kindOf("http://example.com/get.php?username=u&type=m3u_plus"));
        assertEquals("", Catalog.kindOf("http://example.com/live/stream"));
        assertEquals("", Catalog.kindOf(null));
        assertEquals("4K", Catalog.pictureName(Catalog.PICTURE_4K));
        assertEquals("720p", Catalog.pictureName(Catalog.PICTURE_720));
        assertEquals("", Catalog.pictureName(0));
    }
}
