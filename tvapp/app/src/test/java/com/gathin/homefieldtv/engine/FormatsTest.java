package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What names say of HDR and sound, read as the phone reads them, and the order copies are offered in: the best first,
 * with no limit on size. Invented names only.
 */
public class FormatsTest {
    private static void says(String name, String hdr, String audio) {
        assertEquals(name + ": HDR", hdr, Formats.hdr(name));
        assertEquals(name + ": sound", audio, Formats.audio(name));
    }

    @Test public void namesSayHdrAndSoundAsThePhoneReadsThem() {
        says("Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR10.H.265-GRP.mkv", "Dolby Vision", "Dolby Atmos");
        says("Glass.Harbour.2024.1080p.BluRay.TrueHD.7.1.x264.mkv", "", "Dolby TrueHD 7.1");
        says("Film.2019.720p.WEB.H264.AAC2.0.mp4", "", "AAC Stereo");
        says("Film.2021.2160p.UHD.BluRay.REMUX.HDR10+.DTS-HD.MA.7.1.mkv", "HDR10+", "DTS-HD 7.1");
        says("Film 2020 1080p HLG DD5.1", "HLG", "Dolby Digital 5.1");
        says("EN - The Long Tide (2023) 4K HDR", "HDR", "");
        says("Movie.2024.1080p.WEB-DL.EAC3.6CH.mkv", "", "Dolby Digital+ 5.1");
        says("Film.2023.1080p.WEB-DL.DD+5.1.H.264.mkv", "", "Dolby Digital+ 5.1");
        says("Film.2022.2160p.BluRay.DTS-X.7.1.mkv", "", "DTS:X 7.1");
        says("Film.2022.1080p.DDPA5.1.mkv", "", "Dolby Atmos");
        // A year beside the picture, and the codec's bit depth, are no channels.
        says("Movie.2024.2160p.x265.10bit.mkv", "", "");
        says("Movie 2.0 (2001)", "", "");
        // The same words kept once, however many names say them.
        assertSame(Formats.audio("Film.DDP5.1.mkv"), Formats.audio("Other.Film.DDP5.1.mkv"));
    }

    @Test public void surroundIsMoreThanTwoChannels() {
        assertTrue(Formats.surround("Dolby Atmos"));
        assertTrue(Formats.surround("DTS-HD 7.1"));
        assertTrue(Formats.surround("Dolby Digital+ 5.1"));
        assertTrue(Formats.surround("Dolby Digital+"));
        assertTrue(Formats.surround("Dolby TrueHD"));
        assertTrue(Formats.surround("AAC 5.1"));
        assertTrue(Formats.surround("5.1"));
        assertTrue(Formats.surround("DTS"));
        assertFalse(Formats.surround("Dolby Digital+ Stereo"));
        assertFalse(Formats.surround("AAC Stereo"));
        assertFalse(Formats.surround("MPEG"));
        assertFalse(Formats.surround(""));
        assertFalse(Formats.surround(null));
        assertEquals("5.1", Formats.channelsOf("Dolby Digital+ 5.1"));
        assertEquals("Stereo", Formats.channelsOf("AAC Stereo"));
        assertEquals("", Formats.channelsOf("Dolby Atmos"));
    }

    @Test public void onlyNamesThatMaySayItAreRead() {
        assertTrue(Formats.mayHdr("Glass Harbour (2024) 4K HDR"));
        assertTrue(Formats.mayHdr("UHD HDR10 MOVIES"));
        assertTrue(Formats.mayHdr("Film HLG"));
        assertFalse(Formats.mayHdr("Glass Harbour (2024) 4K"));
        assertFalse(Formats.mayHdr(null));
        assertTrue(Formats.maySound("Glass Harbour 1080p DDP5.1"));
        assertTrue(Formats.maySound("Film 7.1"));
        assertTrue(Formats.maySound("Film Dolby"));
        assertFalse(Formats.maySound("Paper Lanterns"));
        assertFalse(Formats.maySound(""));
    }

    private static Catalog.Play copy(String url, int picture, String hdr, String sound, String kind, long size) {
        Catalog.Play p = new Catalog.Play(url, null, null);
        p.picture = picture;
        p.hdr = hdr;
        p.sound = sound;
        p.kind = kind;
        p.size = size;
        return p;
    }

    @Test public void theBestFirst() {
        long gb = 1024L * 1024 * 1024;
        Catalog.Play big4k = copy("a", Catalog.PICTURE_4K, "", "", "MKV", 60 * gb);
        Catalog.Play dv4k = copy("b", Catalog.PICTURE_4K, "Dolby Vision", "", "MKV", 15 * gb);
        Catalog.Play hdrAtmos = copy("c", Catalog.PICTURE_4K, "HDR10", "Dolby Atmos", "MP4", 14 * gb);
        Catalog.Play fhd = copy("d", Catalog.PICTURE_1080, "", "Dolby Digital+ 5.1", "MKV", 8 * gb);
        Catalog.Play hd = copy("e", Catalog.PICTURE_720, "", "", "MKV", 2 * gb);
        Catalog.Play sd = copy("f", Catalog.PICTURE_SD, "", "", "MKV", gb);
        Catalog.Play plain = copy("g", 0, "", "", "", 0);
        Catalog.Play avi = copy("h", Catalog.PICTURE_4K, "Dolby Vision", "Dolby Atmos", "AVI", 70 * gb);
        List<Catalog.Play> all = new ArrayList<>(Arrays.asList(avi, plain, sd, hd, fhd, big4k, dv4k, hdrAtmos));
        Formats.sort(all);
        // 4K first (HDR before SDR, surround before stereo, then the bigger); 1080p; 720p; the rest; AVI after all.
        assertEquals(Arrays.asList(hdrAtmos, dv4k, big4k, fhd, hd, sd, plain, avi), all);
        // Alike: their order kept.
        Catalog.Play a = copy("x", 0, "", "", "", 0), b = copy("y", 0, "", "", "", 0);
        List<Catalog.Play> two = new ArrayList<>(Arrays.asList(a, b));
        Formats.sort(two);
        assertSame(a, two.get(0));
        assertNotEquals(0, Formats.compare(big4k, fhd));
        assertEquals(0, Formats.pictureRank(Catalog.PICTURE_4K));
        assertEquals(3, Formats.pictureRank(0));
        assertTrue(Formats.poor("AVI"));
        assertTrue(Formats.poor("TS"));
        assertFalse(Formats.poor("MKV"));
        assertFalse(Formats.poor(""));
    }

    @Test public void aPlaylistsTitleSaysItsHdrAndSound() {
        Catalog.Title t = new Catalog.Title("p0", false, "1", "EN - Glass Harbour (2024) 4K HDR10 DDP5.1", "Films", "", "", "", "",
                0, "mkv", "", null, null, "2024");
        assertEquals("HDR10", t.hdr);
        assertEquals("Dolby Digital+ 5.1", t.sound);
        Catalog.Title dv = new Catalog.Title("p0", false, "2", "Glass Harbour (2024) DV Atmos", "Films", "", "", "", "", 0, "mkv", "",
                null, null, "2024");
        assertEquals("Dolby Vision", dv.hdr);
        assertEquals("Dolby Atmos", dv.sound);
        // From its category, as the picture is.
        Catalog.Title cat = new Catalog.Title("p0", false, "3", "Glass Harbour (2024)", "4K HDR MOVIES", "", "", "", "", 0, "mkv", "",
                null, null, "2024");
        assertEquals("HDR", cat.hdr);
        Catalog.Title none = new Catalog.Title("p0", false, "4", "Paper Kites (2026)", "Family films", "", "", "", "", 0, "mkv", "",
                null, null, "2026");
        assertEquals("", none.hdr);
        assertEquals("", none.sound);
    }
}
