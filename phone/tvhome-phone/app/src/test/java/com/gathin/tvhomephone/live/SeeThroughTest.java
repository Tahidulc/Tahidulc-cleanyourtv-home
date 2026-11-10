package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Whitebrook Road Sports on the phone: what is on it, and the patch that hides the channel's own logo under it. */
public class SeeThroughTest {
    @Test public void theWordmarkSaysWhatIsOn() {
        assertEquals("premier league", ScoreBoard.whatFor("", "Premier League", false));
        assertEquals("football", ScoreBoard.whatFor("", "Scottish Premiership", false));
        assertEquals("football", ScoreBoard.whatFor("", "Champions League", false));
        assertEquals("f1", ScoreBoard.whatFor("", "", true));
        // One chosen is kept, whatever is on.
        assertEquals("main event", ScoreBoard.whatFor("main event", "Premier League", false));
        assertEquals("", SportsMark.WHATS[0]);
    }

    @Test public void aGeneralChannelCarriesNoSport() {
        // Sports off: the name alone, nothing on it; on, the choice or football.
        assertEquals("", SportsMark.onFor(false, "premier league"));
        assertEquals("", SportsMark.onFor(false, ""));
        assertEquals("f1", SportsMark.onFor(true, "f1"));
        assertEquals("football", SportsMark.onFor(true, ""));
    }

    @Test public void thePatchFillsTheLogoFromWhatIsRoundIt() {
        int w = 16, h = 10, m = 3;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean inside = x >= m && x < w - m && y >= m && y < h - m;
                px[y * w + x] = inside ? 0xFFFFFFFF : 0xFF2E7D32;
            }
        }
        int[] in = LogoPatch.fill(px, w, h, m, m);
        assertNotNull(in);
        // The logo's white gone: the pitch's green all over (its edge fading a little into the picture).
        for (int c : in) {
            assertTrue(((c >> 8) & 0xFF) > ((c >> 16) & 0xFF));
            assertTrue((c & 0xFFFFFF) != 0xFFFFFF);
        }
        assertNull(LogoPatch.fill(px, w, h, 0, 0, 0, 0));
    }
}
