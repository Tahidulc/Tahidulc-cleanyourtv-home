package com.gathin.tvhomephone.glass;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.live.SourceQuality;

import org.junit.Test;

/** The poster card's words and colours: its line, Dolby's words, its badges and the fade made from the picture. */
public class PosterCardTest {
    @Test public void theLineUnderTheLogo() {
        assertEquals("Mystery • ★ 8.2", PosterCard.line("Mystery", 8.24));
        assertEquals("★ 7.0", PosterCard.line("", 7.0));
        assertEquals("Drama", PosterCard.line("Drama", 0));
        assertEquals("", PosterCard.line(null, 0.5));
        assertEquals("Sci-Fi • ★ 8.2", PosterCard.line("Science Fiction", 8.2));
        assertEquals("Action", PosterCard.line("Action & Adventure", 0));
    }

    @Test public void dolbysWords() {
        assertEquals("ATMOS·VISION", PosterCard.dolbyWords(SourceQuality.of("Film 2160p DV Atmos")));
        assertEquals("VISION", PosterCard.dolbyWords(SourceQuality.of("Film 2160p Dolby Vision")));
        assertEquals("ATMOS", PosterCard.dolbyWords(SourceQuality.of("Film 1080p DDPA5.1")));
        assertEquals("", PosterCard.dolbyWords(SourceQuality.of("Film 2160p HDR10")));
    }

    @Test public void aLandscapeCardAsksForTheBackdropAtItsOwnSize() {
        assertEquals("https://image.tmdb.org/t/p/w780/a.jpg", PosterCard.cardSize("https://image.tmdb.org/t/p/w1280/a.jpg"));
        assertEquals("https://image.tmdb.org/t/p/w780/a.jpg", PosterCard.cardSize("https://image.tmdb.org/t/p/original/a.jpg"));
        assertEquals("https://image.tmdb.org/t/p/w780/a.jpg", PosterCard.cardSize("https://image.tmdb.org/t/p/w780/a.jpg"));
        assertEquals("", PosterCard.cardSize(""));
        assertNull(PosterCard.cardSize(null));
    }

    @Test public void eachKindOfBadgeHasItsLook() {
        assertArrayEquals(new int[]{0xD9161618, 0xFFFFFFFF}, PosterCard.badgeLook("#1 Today", 0));
        assertArrayEquals(new int[]{0xFFC9EBEE, 0xFF0F2A2E}, PosterCard.badgeLook("Trending", 0));
        assertArrayEquals(new int[]{0xFFFFD25A, 0xFF1E1606}, PosterCard.badgeLook("New", 0));
        assertArrayEquals(new int[]{0xFF2F64D8, 0xFFFFFFFF}, PosterCard.badgeLook("THU 5", 0xFF2F64D8));
    }

    @Test public void theFadeIsThePostersOwnColourDarkened() {
        assertEquals(0xFF102030, PosterCard.average(new int[]{0xFF000000, 0xFF204060}));
        assertEquals(0xFF000000, PosterCard.average(new int[0]));
        // A red poster gives a deep red; a grey one stays grey; both dark.
        int red = PosterCard.shade(0xFFC83C32);
        float[] r = PosterCard.hsv(red);
        assertTrue(r[0] < 10 || r[0] > 350);
        assertTrue(r[2] >= 0.14f && r[2] <= 0.28f);
        int grey = PosterCard.shade(0xFF808080);
        assertEquals(0f, PosterCard.hsv(grey)[1], 0.001f);
        assertTrue(PosterCard.hsv(grey)[2] < 0.25f);
        // The colour sums agree both ways.
        for (int c : new int[]{0xFF336699, 0xFFFFCC00, 0xFF0A0A0A, 0xFF7F1F5F}) {
            float[] h = PosterCard.hsv(c);
            int back = PosterCard.rgb(h[0], h[1], h[2]);
            for (int shift : new int[]{16, 8, 0}) assertEquals((c >> shift) & 0xFF, (back >> shift) & 0xFF, 1);
        }
    }
}
