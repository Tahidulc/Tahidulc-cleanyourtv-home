package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** What a title's name says of its picture and sound, its name without those tags, an episode's date, My list's line. */
public class QualityTest {
    @Test public void pictures() {
        assertEquals(Catalog.PICTURE_4K, Catalog.pictureOf("Dune: Part Two (2024) 4K"));
        assertEquals(Catalog.PICTURE_4K, Catalog.pictureOf("[UHD] Dune"));
        assertEquals(Catalog.PICTURE_4K, Catalog.pictureOf("Dune.2021.2160p.WEB"));
        assertEquals(Catalog.PICTURE_1080, Catalog.pictureOf("Dune (2021) 1080p"));
        assertEquals(Catalog.PICTURE_1080, Catalog.pictureOf("FHD | Dune"));
        assertEquals(Catalog.PICTURE_720, Catalog.pictureOf("Dune 720p"));
        assertEquals(Catalog.PICTURE_SD, Catalog.pictureOf("Dune SD"));
        // "HD" alone says no lines, and a title's own number is not a picture.
        assertEquals(0, Catalog.pictureOf("Dune HD"));
        assertEquals(0, Catalog.pictureOf("2012"));
        assertEquals(0, Catalog.pictureOf(null));
    }

    @Test public void dolby() {
        assertTrue(Catalog.dolbyVisionOf("Dune (2021) 4K DV"));
        assertTrue(Catalog.dolbyVisionOf("Dune DoVi HDR"));
        assertTrue(Catalog.dolbyVisionOf("Dune - Dolby Vision"));
        assertFalse(Catalog.dolbyVisionOf("Divergent"));
        assertTrue(Catalog.atmosOf("Dune 2160p Atmos"));
        assertFalse(Catalog.atmosOf("Atmosphere (2010)"));
    }

    @Test public void namesLoseTheirTags() {
        assertEquals("Spider-Man: Brand New Day", Catalog.displayName("Spider-Man: Brand New Day (2026) 1080p"));
        assertEquals("Collision Course", Catalog.displayName("Collision Course (2026) 4K DV Atmos"));
        assertEquals("Dune", Catalog.displayName("[4K] Dune (2021) [HDR]"));
        assertEquals("The Thing", Catalog.displayName("EN - The Thing (1982)"));
        // A name that is only a tag keeps its words.
        assertEquals("4K", Catalog.displayName("4K"));
        assertEquals("Bad Boys", Catalog.displayName("Bad Boys"));
    }

    @Test public void titlesCarryTheirPicture() {
        Catalog.Title t = new Catalog.Title("p0", false, "1", "Dune (2021) 4K DV", "UK | Films", "", "", "7", "", 0, "mkv", "", "", "", "");
        assertEquals("Dune", t.name);
        assertEquals("2021", t.year);
        assertEquals(Catalog.PICTURE_4K, t.picture);
        assertTrue(t.dolbyVision);
        assertFalse(t.atmos);
        // A category of 4K films makes each of them 4K.
        Catalog.Title u = new Catalog.Title("p0", false, "2", "Arrival (2016)", "4K UHD Movies", "", "", "8", "", 0, "mkv", "", "", "", "");
        assertEquals(Catalog.PICTURE_4K, u.bestPicture());
    }

    @Test public void episodeDates() {
        assertEquals("2022-10-09", Catalog.aired("2022-10-09"));
        assertEquals("2022-10-09", Catalog.aired("2022-10-09 21:00:00"));
        assertEquals("", Catalog.aired("Sunday"));
        assertEquals("", Catalog.aired(null));
    }

    @Test public void myListLine() {
        assertEquals(Arrays.asList("p0|m|1", "p0|s|2"), MyList.parse("p0|m|1\n\np0|s|2\np0|m|1"));
        assertEquals("p0|m|1\np0|s|2", MyList.join(Arrays.asList("p0|m|1", " ", "p0|s|2")));
        assertTrue(MyList.parse("").isEmpty());
    }
}
