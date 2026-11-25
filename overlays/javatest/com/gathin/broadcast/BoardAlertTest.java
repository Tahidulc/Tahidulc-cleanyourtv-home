package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * The one source of the football alerts and the one place they show (BoardAlert): what Homefield TV's board does with
 * one the launcher hands it, which kinds its own board says itself, the launcher's switches and the family's defaults; the
 * one rule for both apps' overlays (InChannel); the shared covers' pure parts (CoverLooks, Outfit).
 */
public class BoardAlertTest {
    @Test public void whatHomefieldsBoardDoesWithAnAlert() {
        // Its board away (covered, resting, off): not taken, the launcher's card shows instead.
        assertEquals(BoardAlert.NOT_UP, BoardAlert.decide(false, "g1", "g2", "goal", false, false));
        // Another game's: out of the board.
        assertEquals(BoardAlert.SHOWN, BoardAlert.decide(true, "g1", "g2", "goal", false, false));
        assertEquals(BoardAlert.SHOWN, BoardAlert.decide(true, null, "g2", "red", false, false));
        // Its own game's goals, cards, half and full time, kick-off, line-ups and VAR: said by the board itself, once.
        for (String k : new String[]{"goal", "drama", "red", "ft", "ht", "kickoff", "var", "lineups"}) {
            assertTrue(k, BoardAlert.boardSaysIt(k));
            assertEquals(k, BoardAlert.SAID, BoardAlert.decide(true, "g1", "g1", k, false, false));
        }
        // Its own game's of a kind it does not say (the ratings, the weather): out of the board.
        assertFalse(BoardAlert.boardSaysIt(null));
        assertFalse(BoardAlert.boardSaysIt("prediction"));
        assertEquals(BoardAlert.SHOWN, BoardAlert.decide(true, "g1", "g1", "prediction", true, false));
        // Pages late in full time's minute: the launcher's card instead, as the launcher's board.
        assertEquals(BoardAlert.NOT_UP, BoardAlert.decide(true, "g1", "g2", null, true, true));
        assertEquals(BoardAlert.SHOWN, BoardAlert.decide(true, "g1", "g2", "goal", false, true));
    }

    @Test public void theSwitchesAndTheFamilysDefaults() {
        assertTrue(BoardAlert.allowed(null, "goal"));
        assertFalse("Goal Rush off on both TVs", BoardAlert.allowed(null, "goalrush"));
        assertFalse("the live table off on both TVs", BoardAlert.allowed(null, "table"));
        assertTrue("no switch of its own", BoardAlert.allowed(null, null));
        Map<String, Boolean> s = new HashMap<>();
        s.put("goal", false);
        s.put("table", true);
        assertFalse(BoardAlert.allowed(s, "goal"));
        assertTrue(BoardAlert.allowed(s, "table"));
        assertTrue(BoardAlert.allowed(s, "red"));
        assertTrue(BoardAlert.allowed(s, null));
        assertEquals("goal", BoardAlert.kindOfChip("goal"));
        assertEquals("ft", BoardAlert.kindOfChip("FULL TIME"));
        assertEquals("ht", BoardAlert.kindOfChip("HALF TIME"));
        assertEquals("kickoff", BoardAlert.kindOfChip("KICK-OFF"));
        assertEquals("var", BoardAlert.kindOfChip("VAR"));
        assertNull(BoardAlert.kindOfChip("ADDED TIME"));
        assertNull(BoardAlert.kindOfChip(null));
        assertEquals(11, BoardAlert.KINDS.length);
    }

    @Test public void picturesSmallEnoughToHandOver() {
        assertArrayEquals(new int[]{256, 128}, BoardAlert.fitted(1200, 600, 256));
        assertArrayEquals(new int[]{96, 96}, BoardAlert.fitted(96, 96, 256));
        assertArrayEquals(new int[]{128, 256}, BoardAlert.fitted(300, 600, 256));
        assertArrayEquals(new int[]{0, 0}, BoardAlert.fitted(0, 10, 256));
    }

    @Test public void onlyInTheChannelInBothApps() {
        assertTrue(InChannel.shows(true, true, true, false));
        assertFalse(InChannel.shows(false, true, true, false));
        assertFalse(InChannel.shows(true, false, true, false));
        assertFalse(InChannel.shows(true, true, false, false));
        assertFalse(InChannel.shows(true, true, true, true));
    }

    @Test public void theCoversPureParts() {
        assertEquals(24, CoverLooks.Look.values().length);
        assertEquals(CoverLooks.Look.GLASS, CoverLooks.Look.of("nothing"));
        assertNull(CoverLooks.Look.find("nothing"));
        assertTrue(CoverLooks.Look.EVENT_NAVY.event());
        assertTrue(CoverLooks.Look.EVENT_NAVY.channel());
        assertTrue(CoverLooks.Look.SPORTS_STACKED.sports());
        assertTrue(CoverLooks.Look.STYLES.own());
        assertEquals(LabMarks.Look.DUO, CoverLooks.Look.DUO.lab());
        assertNull(CoverLooks.Look.GLASS.lab());
        assertTrue(CoverLooks.Look.BROADCAST_BOARD.broadcast());
        assertFalse(CoverLooks.Look.COMPETITION.channel());
        assertEquals("4K HDR", CoverLooks.tagOf("Sky Sports F1 4K HDR"));
        assertEquals("HD", CoverLooks.tagOf("BBC One HD"));
        assertEquals("", CoverLooks.tagOf("Channel"));
        assertEquals("None", CoverLooks.tagWords("", "x"));
        assertEquals("", CoverLooks.nextTag(null, 1));
        assertNull(CoverLooks.nextTag("4K HDR", 1));
        assertEquals(5, CoverLooks.GROUNDS.length);
        assertEquals("Transparent", CoverLooks.groundWords(CoverLooks.CLEAR));
        int[] seeThrough = CoverLooks.broadcastColours(null);
        assertEquals(0xC20E1014, seeThrough[0]);
        int[] board = CoverLooks.broadcastColours(new CoverLooks.Board(0xF70B1033, 0xFF2443B4, 0xFFE6E9F2, false));
        assertEquals(0xFF0B1033, board[0]);
        assertEquals(0xFF2443B4, board[1]);
        assertEquals(0xFFE6E9F2, board[5]);
        assertEquals(40f, CoverLooks.labHeight(120f, 160f, 40f), 0f);
        assertEquals(32f, CoverLooks.labHeight(200f, 160f, 40f), 1e-4f);
        assertEquals(700, Outfit.weightOf(750));
        assertEquals(500, Outfit.weightOf(500));
        assertEquals(200, Outfit.weightOf(100));
        assertEquals("broadcast/formats/fmt_hdr10plus.png", Outfit.formatAsset("HDR10+"));
        assertNull(Outfit.formatAsset("4K"));
    }
}
