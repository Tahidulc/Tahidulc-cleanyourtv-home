package com.gathin.tvhome;

import com.gathin.broadcast.BrandStyles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** The 54 Whitebrook Road logo styles: each named for what it looks like, never for the channel it calls to mind. */
public class BrandStylesTest {
    /** The channel logos the family picked: no style's name may name one of them. */
    private static final String[] CHANNELS = {"4 HD", "5 HD", "7mate", "abc", "abc Kids", "abc TV", "AMC", "AMC+", "Apple TV+",
            "Apple", "BBC iPlayer", "iPlayer", "BBC One", "BBC Two", "BBC Three", "BBC", "CBS All Access", "All Access", "CBS",
            "Cinemax", "Comedy Central", "crave", "CW", "Discovery", "Discovery+", "Discovery D", "Disney+", "Disney", "E4", "epix",
            "ESPN+", "ESPN", "FOX", "FOXTEL", "freeform", "FX", "FXX", "HBO Max", "HBO", "HISTORY", "hulu", "itv",
            "National Geographic", "Nat Geo", "NETFLIX", "9", "Nine", "Paramount+", "Paramount", "peacock", "prime video", "prime",
            "Roku", "sky atlantic", "sky max", "sky one", "sky", "Spike", "Stan", "SyFy", "tbs", "TNT", "truTV", "tvnz2", "tvnz", "USA"};

    private static String words(String s) {
        return " " + s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+]+", " ").trim() + " ";
    }

    @Test public void fiftyFourStyles() {
        assertEquals(54, BrandStyles.COUNT);
    }

    @Test public void eachHasItsOwnName() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < BrandStyles.COUNT; i++) {
            String n = BrandStyles.name(i);
            assertNotNull(n);
            assertFalse(n.trim().isEmpty());
            assertTrue("named twice: " + n, seen.add(n.toLowerCase(Locale.ROOT)));
        }
    }

    @Test public void noNameNamesAChannel() {
        for (int i = 0; i < BrandStyles.COUNT; i++) {
            String n = words(BrandStyles.name(i));
            for (String c : CHANNELS) assertFalse(BrandStyles.name(i) + " names " + c, n.contains(words(c)));
        }
    }

    @Test public void eachIsWiderThanItIsTall() {
        for (int i = 0; i < BrandStyles.COUNT; i++) {
            float a = BrandStyles.aspect(i);
            assertTrue(BrandStyles.name(i) + " " + a, a >= 1.5f && a <= 6f);
        }
    }

    @Test public void aStyleOutOfRangeIsTheNearest() {
        assertEquals(0, BrandStyles.clamp(-3));
        assertEquals(BrandStyles.COUNT - 1, BrandStyles.clamp(99));
        assertEquals(BrandStyles.name(BrandStyles.COUNT - 1), BrandStyles.name(1000));
        assertEquals(BrandStyles.aspect(0), BrandStyles.aspect(-1), 0f);
    }

    @Test public void lightness() {
        assertEquals(1f, BrandStyles.lum(0xFFFFFFFF), 0.01f);
        assertEquals(0f, BrandStyles.lum(0xFF000000), 0.01f);
        assertTrue(BrandStyles.lum(0xFFF4F4F2) > 0.6f);
        assertTrue(BrandStyles.lum(0xFF16357A) < 0.6f);
    }
}
