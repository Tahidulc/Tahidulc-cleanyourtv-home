package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

/** The goal shirt's words, the board's shirt frames' size and ESPN's colours, as the TV has them (KitBoardTest). Made-up players. */
public class PhoneKitsTest {
    @Test public void wordsUnderTheShirt() {
        assertEquals("41'  JAY HOLLIS", PhoneKits.caption("41'", "Jay Hollis", false));
        assertEquals("41'  JAY HOLLIS", PhoneKits.caption("41", "Jay Hollis", false));
        assertEquals("90'+3'  SAM OKAFOR (PEN)", PhoneKits.caption("90'+3'", "Sam Okafor", true));
        assertEquals("LEO BRANDT", PhoneKits.caption("", "Leo Brandt", false));
    }

    @Test public void framesALittleTallerThanTheBoard() {
        assertEquals(72, PhoneKits.frameSize(60));
        assertEquals(64, PhoneKits.frameSize(10));
        assertEquals(224, PhoneKits.frameSize(400));
    }

    @Test public void sidesTurnedTowardsTheMiddle() {
        assertEquals(0.35f, OverlayBug.kitRest(true), 1e-6);
        assertEquals(-0.35f, OverlayBug.kitRest(false), 1e-6);
    }

    @Test public void espnsColoursAsRgb() {
        assertEquals(0xE20520, PhoneKits.rgb("e20520"));
        assertEquals(0xFFFFFF, PhoneKits.rgb("FFFFFF"));
        assertEquals(0, PhoneKits.rgb(""));
        assertEquals(0, PhoneKits.rgb(null));
        assertEquals(0, PhoneKits.rgb("nothex"));
    }

    @Test public void noShirtWithoutTheScorersId() {
        // a goal row from before ESPN's id was kept (five fields), or one with none: no back to make
        Game g = PhoneKits.sample();
        assertNull(PhoneKits.back(g, new String[]{"G|test-shirt|41'|Jay Hollis", "Jay Hollis", "41'", "home", ""}));
        assertNull(PhoneKits.back(g, new String[]{"G|test-shirt|41'|Jay Hollis", "Jay Hollis", "41'", "home", "", ""}));
        assertNull(PhoneKits.back(g, null));
    }

    @Test public void hisNumberAndNameFromTheLineUps() throws Exception {
        Game g = PhoneKits.sample();
        g.id = "kits-test-lineups";
        com.gathin.broadcast.Shirts.note(g.id, new org.json.JSONArray("[{\"roster\":["
                + "{\"jersey\":\"27\",\"athlete\":{\"id\":\"9001\",\"displayName\":\"Jay Hollis\",\"lastName\":\"Hollis\"}},"
                + "{\"jersey\":\"4\",\"athlete\":{\"id\":\"9002\",\"displayName\":\"Daan van der Molen\",\"lastName\":\"van der Molen\"}},"
                + "{\"jersey\":\"\",\"athlete\":{\"id\":\"9003\",\"displayName\":\"Leo Brandt\",\"lastName\":\"Brandt\"}}]}]"));
        assertArrayEquals(new String[]{"27", "HOLLIS"},
                PhoneKits.back(g, new String[]{"G|x|41'|Jay Hollis", "Jay Hollis", "41'", "home", "", "9001"}));
        assertArrayEquals(new String[]{"4", "VAN DER MOLEN"},
                PhoneKits.back(g, new String[]{"G|x|58'|Daan van der Molen", "Daan van der Molen", "58'", "away", "", "9002"}));
        // no number in the line-ups: no shirt
        assertNull(PhoneKits.back(g, new String[]{"G|x|70'|Leo Brandt", "Leo Brandt", "70'", "home", "", "9003"}));
    }

    @Test public void theMadeUpGameForATry() {
        Game g = PhoneKits.sample();
        assertEquals("359", g.homeId);
        assertEquals("361", g.awayId);
        assertEquals("ARS", g.home);
        assertEquals("NEW", g.away);
    }
}
