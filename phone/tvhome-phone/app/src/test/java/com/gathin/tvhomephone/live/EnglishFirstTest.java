package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** "Also Showing On" puts English channels first, and the match page names its pick plainly. */
public class EnglishFirstTest {
    @Test public void englishChannelsAreToldFromTheRest() {
        assertEquals(2, AlsoShowing.english("UK: Sky Sports Football FHD"));
        assertEquals(2, AlsoShowing.english("|US| ESPN 2 HD"));
        assertEquals(2, AlsoShowing.english("IE - Premier Sports 1"));
        assertEquals(2, AlsoShowing.english("[EN] beIN Sports 1"));
        assertEquals(2, AlsoShowing.english("TNT Sports 1 HD"));
        assertEquals(2, AlsoShowing.english("ITV 1 HD"));
        assertEquals(2, AlsoShowing.english("Al Jazeera English"));
        assertEquals(2, AlsoShowing.english("UK EVENTS 03: Switzerland v Scotland"));
        assertEquals(0, AlsoShowing.english("DE: Sky Sport Top Event"));
        assertEquals(0, AlsoShowing.english("|FR| beIN Sports 1"));
        assertEquals(0, AlsoShowing.english("AR: beIN Sports 2 HD"));
        assertEquals(0, AlsoShowing.english("ESPN Deportes"));
        assertEquals(0, AlsoShowing.english("SRF zwei HD CH"));
        assertEquals(0, AlsoShowing.english("Arabic Sports | beIN 1"));
        assertEquals(1, AlsoShowing.english("Event 07"));
        assertEquals(1, AlsoShowing.english("Canal+ Sport"));
        assertEquals(1, AlsoShowing.english(""));
    }

    @Test public void aPlaylistNameIsMadePlain() {
        assertEquals("Premier Sports 1", AlsoShowing.tidy("UK: PREMIER SPORTS 1 FHD"));
        assertEquals("TNT Sports 2", AlsoShowing.tidy("|UK| TNT SPORTS 2 HD"));
        assertEquals("Sky Sports Main Event", AlsoShowing.tidy("UK - Sky Sports Main Event UHD"));
        assertEquals("ITV 1", AlsoShowing.tidy("ITV 1 HD"));
        assertEquals("SRF zwei", AlsoShowing.tidy("CH: SRF zwei HD"));
        // A first word that isn't a country's tag stays.
        assertEquals("Sky Sports News", AlsoShowing.tidy("Sky Sports News"));
        assertEquals("BBC One", AlsoShowing.tidy("BBC ONE"));
        assertEquals("HD", AlsoShowing.tidy("HD"));
    }
}
