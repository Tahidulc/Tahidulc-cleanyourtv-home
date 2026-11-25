package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What the cover a film opens on says of each source (made-up release names): the labels a TorBox file's or a playlist
 * entry's name gives (HDR or SDR, the sound in short, the codec, the release), every TorBox file of a film or an episode
 * listed (none left out), and the title's logo chosen from TMDB's images (the family's language, then English, then one
 * with no words; PNGs only), "" without one.
 */
public class CoverLabelsTest {
    @Test public void labelsFromReleaseNames() {
        assertEquals(Arrays.asList("Dolby Vision", "TrueHD Atmos", "HEVC", "REMUX"),
                Formats.labels("The.Lantern.House.2025.2160p.UHD.BluRay.REMUX.DV.HDR10.TrueHD.Atmos.7.1.HEVC-MADEUP.mkv", "", ""));
        assertEquals(Arrays.asList("HDR10+", "DD+ 5.1", "HEVC", "WEB-DL"),
                Formats.labels("The Lantern House 2025 2160p AMZN WEB-DL DDP5.1 HDR10+ H.265-MadeUp.mkv", "", ""));
        assertEquals(Arrays.asList("DTS-HD MA 5.1", "H.264", "BluRay"),
                Formats.labels("The.Lantern.House.2025.1080p.BluRay.DTS-HD.MA.5.1.x264-MADEUP.mkv", "", ""));
        assertEquals(Arrays.asList("AAC Stereo", "H.264", "WEBRip"),
                Formats.labels("The.Lantern.House.2025.720p.WEBRip.AAC2.0.x264-MADEUP.mp4", "", ""));
        assertEquals(Arrays.asList("SDR", "DTS 5.1", "AV1", "WEB"), Formats.labels("the lantern house 2025 2160p sdr web dts 5.1 av1", "", ""));
        assertEquals(Arrays.asList("DD+ Atmos"), Formats.labels("The.Lantern.House.2025.DDP5.1.Atmos", "", ""));
        assertEquals(Arrays.asList("DTS:X"), Formats.labels("The Lantern House DTS-X", "", ""));
        // A series pack's episode file: its own name says little, its download's the rest.
        assertEquals(Arrays.asList("Dolby Vision", "DD+ 5.1", "HEVC", "WEB-DL"),
                Formats.labels("The Quiet Coast - S02E04.mkv The.Quiet.Coast.S02.2160p.WEB-DL.DDP5.1.DV.HEVC-MADEUP", "", ""));
        // A playlist's entry: its name's tags were read already (and taken off the name).
        assertEquals(Arrays.asList("HDR10", "DD+ 5.1"), Formats.labels("The Lantern House", "HDR10", "Dolby Digital+ 5.1"));
        assertEquals(Arrays.asList("Atmos"), Formats.labels("The Lantern House", "", "Dolby Atmos"));
        assertEquals(Collections.emptyList(), Formats.labels("The Lantern House", "", ""));
        assertEquals("TrueHD 7.1", Formats.shortSound("Dolby TrueHD 7.1"));
        assertEquals("", Formats.codec("The Lantern House 1080p"));
    }

    private static TorBox.File file(int id, String name, long size) {
        return new TorBox.File("torrent", 100 + id, id, name, "The Lantern House/" + name, "The Lantern House 2025", size);
    }

    @Test public void everyTorBoxFileIsListed() {
        List<TorBox.File> lib = new ArrayList<>();
        String[] q = {"2160p.REMUX.DV.TrueHD.Atmos", "2160p.WEB-DL.HDR10.DDP5.1", "1080p.BluRay.DTS-HD.MA.5.1", "1080p.WEB-DL.DDP5.1",
                "720p.WEBRip.AAC2.0", "1080p.HDTV.AC3"};
        for (int i = 0; i < 18; i++) {
            lib.add(file(i, "The.Lantern.House.2025." + q[i % q.length] + ".x265-MADEUP" + i + ".mkv", (20L + i) << 30));
        }
        lib.add(file(99, "Some.Other.Film.2025.1080p.mkv", 4L << 30));
        assertEquals(18, TorBox.films(lib, "The Lantern House", null, "2025").size());
        List<TorBox.File> eps = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            eps.add(new TorBox.File("torrent", 200 + i, i, "The.Quiet.Coast.S02E04.1080p.WEB-DL.v" + i + ".mkv",
                    "The.Quiet.Coast.S02/The.Quiet.Coast.S02E04.1080p.WEB-DL.v" + i + ".mkv", "The.Quiet.Coast.S02.1080p", (2L + i) << 30));
        }
        eps.add(new TorBox.File("torrent", 300, 1, "The.Quiet.Coast.S02E05.1080p.mkv", "x/The.Quiet.Coast.S02E05.1080p.mkv", "The.Quiet.Coast.S02", 2L << 30));
        assertEquals(15, TorBox.episodeVersions(eps, "The Quiet Coast", null, 2, 4).size());
    }

    @Test public void theLogoInTheFamilysLanguageThenEnglishThenNone() throws Exception {
        JSONObject images = new JSONObject("{\"logos\":["
                + "{\"iso_639_1\":\"en\",\"file_path\":\"/en-logo.svg\"},"
                + "{\"iso_639_1\":null,\"file_path\":\"/plain-logo.png\"},"
                + "{\"iso_639_1\":\"en\",\"file_path\":\"/en-logo.png\"},"
                + "{\"iso_639_1\":\"fr\",\"file_path\":\"/fr-logo.png\"}]}");
        assertEquals("https://image.tmdb.org/t/p/w500/fr-logo.png", Tmdb.logoOf(images, "fr"));
        assertEquals("https://image.tmdb.org/t/p/w500/en-logo.png", Tmdb.logoOf(images, "en"));
        assertEquals("https://image.tmdb.org/t/p/w500/en-logo.png", Tmdb.logoOf(images, "de"));
        JSONObject wordless = new JSONObject("{\"logos\":[{\"iso_639_1\":\"en\",\"file_path\":\"/en-logo.svg\"},"
                + "{\"iso_639_1\":null,\"file_path\":\"/plain-logo.png\"}]}");
        assertEquals("https://image.tmdb.org/t/p/w500/plain-logo.png", Tmdb.logoOf(wordless, "de"));
        assertEquals("", Tmdb.logoOf(new JSONObject("{\"logos\":[{\"iso_639_1\":\"en\",\"file_path\":\"/only.svg\"}]}"), "en"));
        assertEquals("", Tmdb.logoOf(new JSONObject("{}"), "en"));
        assertEquals("", Tmdb.logoOf(null, "en"));
    }
}
