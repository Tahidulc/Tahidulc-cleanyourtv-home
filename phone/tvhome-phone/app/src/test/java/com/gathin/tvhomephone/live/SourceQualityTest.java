package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** What the playlists' names and the account's own files say a title comes in, for its poster's corner. */
public class SourceQualityTest {
    private static final long GB = 1024L * 1024 * 1024;

    @Test public void namesSayTheirPictureAndSound() {
        int q = SourceQuality.of("Dune Part Two 2024 2160p DV HDR10 Atmos");
        assertEquals("4K", SourceQuality.picture(q));
        assertEquals("Dolby Vision", SourceQuality.hdr(q));
        assertTrue(SourceQuality.dolbyVision(q));
        assertTrue(SourceQuality.atmos(q));
        assertEquals("4K, Dolby Vision, Dolby Atmos", SourceQuality.spoken(q));

        assertEquals("1080p", SourceQuality.picture(SourceQuality.of("EN - The Batman (2022) 1080p")));
        assertEquals("720p", SourceQuality.picture(SourceQuality.of("Paddington 720p")));
        assertEquals("4K", SourceQuality.picture(SourceQuality.of("[UHD] Oppenheimer")));
        assertEquals("HDR10+", SourceQuality.hdr(SourceQuality.of("Film 4K HDR10+")));
        // SD is known but not worth a mark; a name that says nothing gives nothing.
        assertEquals("", SourceQuality.picture(SourceQuality.of("Old Film SD")));
        assertEquals(0, SourceQuality.of("Paddington in Peru"));
        assertEquals(0, SourceQuality.of("Wednesday"));
        assertEquals(0, SourceQuality.of(null));
        assertEquals("", SourceQuality.spoken(0));
    }

    @Test public void theBestOfTwoIsTakenPartByPart() {
        int a = SourceQuality.of("Film 1080p Atmos"), b = SourceQuality.of("Film 2160p HDR10");
        int best = SourceQuality.best(a, b);
        assertEquals("4K", SourceQuality.picture(best));
        assertEquals("HDR10", SourceQuality.hdr(best));
        assertTrue(SourceQuality.atmos(best));
        assertEquals(a, SourceQuality.best(a, 0));
        assertEquals(best, SourceQuality.best(b, a));
    }

    @Test public void theCatalogueKeepsTheBestEachTitleComesIn() {
        Catalogue cat = new Catalogue(2026);
        cat.add("movie", "Dune: Part Two (2024) 1080p", "693134", "2024");
        cat.add("movie", "4K | Dune: Part Two", "693134", "2024");
        cat.add("movie", "Wicked (2024)", "", "2024");
        cat.add("movie", "Wicked 2024 2160p DV", "", "2024");
        cat.add("movie", "Wicked (1999)", "", "1999");
        cat.add("series", "Slow Horses", "95480", "2022");
        cat.add("episode", "Slow Horses", "95480", "2022", "Slow Horses S01E01 2160p");
        cat.add("movie", "Paddington", "", "2014");

        assertEquals("4K", SourceQuality.picture(cat.quality(693134, false, "Anything", null, "")));
        int wicked = cat.quality(0, false, "Wicked", null, "2024");
        assertEquals("4K", SourceQuality.picture(wicked));
        assertTrue(SourceQuality.dolbyVision(wicked));
        // Another year's title of the same name keeps its own (none said).
        assertEquals(0, cat.quality(0, false, "Wicked", null, "1999"));
        assertEquals("4K", SourceQuality.picture(cat.quality(95480, true, "", null, "")));
        assertEquals(0, cat.quality(0, false, "Paddington", null, "2014"));
        assertEquals(0, cat.quality(0, false, "Not there", null, ""));
        // Still found as before.
        assertTrue(cat.has(0, false, "Wicked", null, "2024"));
        assertFalse(cat.has(0, true, "Wicked", null, "2024"));
    }

    @Test public void theAccountsOwnFilesSayTheirsToo() {
        List<TorBox.File> lib = Arrays.asList(
                new TorBox.File("torrent", 1, 11, "Dune.Part.Two.2024.2160p.UHD.BluRay.DV.HDR.TrueHD.Atmos.mkv", "Dune.Part.Two.2024.2160p.UHD.BluRay.DV.HDR.TrueHD.Atmos.mkv", "Dune Part Two 2024 2160p", 40 * GB),
                new TorBox.File("torrent", 2, 21, "The.Batman.2022.1080p.WEB-DL.mkv", "The.Batman.2022.1080p.WEB-DL.mkv", "The Batman 2022", 6 * GB),
                new TorBox.File("torrent", 3, 31, "Slow.Horses.S04E02.1080p.WEB.mkv", "Slow Horses S04/Slow.Horses.S04E02.1080p.WEB.mkv", "Slow Horses S04", 2 * GB));
        int dune = TorBox.posterQuality(lib, "Dune: Part Two", "2024", false);
        assertEquals("4K", SourceQuality.picture(dune));
        assertTrue(SourceQuality.dolbyVision(dune));
        assertTrue(SourceQuality.atmos(dune));
        assertEquals("1080p", SourceQuality.picture(TorBox.posterQuality(lib, "The Batman", "2022", false)));
        assertEquals("1080p", SourceQuality.picture(TorBox.posterQuality(lib, "Slow Horses", "", true)));
        assertEquals(0, TorBox.posterQuality(lib, "Wicked", "2024", false));
        assertEquals("dune", TorBox.firstWord("Dune: Part Two"));
        assertEquals("odyssey", TorBox.firstWord("The Odyssey"));
        assertEquals("", TorBox.firstWord("It"));
    }
}
