package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HomeStyleTest {
    @Test public void namesAreReadWhateverTheirCase() {
        assertEquals(HomeStyle.SWIPTV, HomeStyle.of(" SWIPTV "));
        assertEquals(HomeStyle.UHF, HomeStyle.of("uhf"));
        assertEquals(HomeStyle.HOME, HomeStyle.of("home"));
    }

    @Test public void anythingElseIsHome() {
        assertEquals(HomeStyle.HOME, HomeStyle.of(null));
        assertEquals(HomeStyle.HOME, HomeStyle.of(""));
        assertEquals(HomeStyle.HOME, HomeStyle.of("netflix"));
    }

    @Test public void theQuickSwitchGoesRoundAllThree() {
        assertEquals(HomeStyle.SWIPTV, HomeStyle.next(HomeStyle.HOME));
        assertEquals(HomeStyle.UHF, HomeStyle.next(HomeStyle.SWIPTV));
        assertEquals(HomeStyle.HOME, HomeStyle.next(HomeStyle.UHF));
        assertEquals(HomeStyle.SWIPTV, HomeStyle.next("unknown"));
    }

    @Test public void eachStyleHasItsOwnName() {
        assertEquals("Home", HomeStyle.name(HomeStyle.HOME));
        assertEquals("SWIPTV style", HomeStyle.name(HomeStyle.SWIPTV));
        assertEquals("UHF style", HomeStyle.name(HomeStyle.UHF));
        assertEquals("Home", HomeStyle.name("anything"));
    }

    @Test public void runningTimesReadAsTheStylesShowThem() {
        assertEquals("48m", Skin.span(48));
        assertEquals("1h 05m", Skin.span(65));
        assertEquals("2h 00m", Skin.span(120));
    }
}
