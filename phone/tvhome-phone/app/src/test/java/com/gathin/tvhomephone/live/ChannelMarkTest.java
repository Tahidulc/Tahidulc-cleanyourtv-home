package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 4.20: Whitebrook Road's channel looks on the logo's cover, and the picture tag (as the TV's 4.19). */
public class ChannelMarkTest {
    @Test
    public void thePictureTagReadsUhd4kAndHdrFromTheChannel() {
        assertEquals("UHD HDR", ChannelMark.tagOf("UK: Sky Sports Main Event UHD HDR"));
        assertEquals("4K HDR", ChannelMark.tagOf("TNT Sports 1 4K HDR"));
        assertEquals("4K", ChannelMark.tagOf("DAZN 1 2160p"));
        assertEquals("UHD", ChannelMark.tagOf("Sky Sports F1 UHD"));
        assertEquals("HD", ChannelMark.tagOf("UK: Sky Sports Main Event HD"));
        assertEquals("HD", ChannelMark.tagOf("BBC One FHD"));
        assertEquals("HD", ChannelMark.tagOf("Sports HD HDR"));
        assertEquals("", ChannelMark.tagOf("Sports HDR"));
        assertEquals("", ChannelMark.tagOf("Shdtv Sports"));
        assertEquals("", ChannelMark.tagOf(null));
        assertEquals("4K", LogoCover.tagOf("TNT Sports 1 4K"));
    }

    @Test
    public void thePictureTagChosenWinsOverTheChannelsName() {
        assertEquals("UHD", ChannelMark.Tag.AUTO.on("Sky Sports Football UHD"));
        assertEquals("", ChannelMark.Tag.NONE.on("Sky Sports Football UHD"));
        assertEquals("4K HDR", ChannelMark.Tag.K4_HDR.on("Sky Sports Football"));
        assertEquals("UHD HDR", ChannelMark.Tag.UHD_HDR.on(null));
        assertEquals(ChannelMark.Tag.AUTO, ChannelMark.Tag.of(null));
        assertEquals(ChannelMark.Tag.HD, ChannelMark.Tag.of("HD"));
    }

    @Test
    public void theChannelLooksAreTheirOwn() {
        int channel = 0;
        for (LogoCover.Look l : LogoCover.Look.values()) {
            if (l.channel()) channel++;
            assertFalse(l.channel() && l.broadcast());
            assertEquals(l, LogoCover.Look.of(l.name()));
        }
        assertEquals(10, channel);
        assertTrue(LogoCover.Look.FROSTED.channel());
        assertFalse(LogoCover.Look.OFF.channel());
        assertFalse(LogoCover.Look.BADGE.channel());
        assertTrue(LogoCover.Look.MATCHDAY.channel());
        // Nothing chosen is Badge, as before.
        assertEquals(LogoCover.Look.BADGE, LogoCover.Look.of(""));
        assertEquals("GRAND PRIX", ChannelMark.channelFor("f1"));
        assertEquals("MATCHDAY", ChannelMark.channelFor(null));
        assertFalse(LogoCover.Look.COMPETITION.channel());
        assertEquals(LogoCover.Look.COMPETITION, LogoCover.Look.of("COMPETITION"));
    }

    @Test
    public void eventIsAChannelLookDrawnWholeInTwoInks() {
        for (LogoCover.Look l : new LogoCover.Look[]{LogoCover.Look.EVENT_NAVY, LogoCover.Look.EVENT_WHITE}) {
            assertTrue(l.event());
            assertTrue(l.channel());
            assertTrue(l.own());
            assertFalse(l.sports());
            assertFalse(l.broadcast());
            assertEquals(l, LogoCover.Look.of(l.name()));
        }
        // Named as the TV names them.
        assertEquals("Event, navy", LogoCover.Look.EVENT_NAVY.label);
        assertEquals("Event, white", LogoCover.Look.EVENT_WHITE.label);
        assertFalse(LogoCover.Look.SMOKED.event());
        assertFalse(LogoCover.Look.SMOKED.own());
        assertEquals(LogoCover.Look.BADGE, LogoCover.Look.of(""));
    }

    @Test
    public void eventsNameIsTheChannelsOwnWithoutTheBroadcaster() {
        assertEquals("main event", ChannelMark.subOf("UK: Sky Sports Main Event HD"));
        assertEquals("premier league", ChannelMark.subOf("Sky Sports Premier League"));
        assertEquals("premier league", ChannelMark.subOf("|UK| SKY SPORTS PREMIER LEAGUE FHD 50FPS"));
        assertEquals("f1", ChannelMark.subOf("Sky Sports F1 UHD"));
        assertEquals("f1", ChannelMark.subOf("Sky Sports F1 \u1D34\u1D30"));
        assertEquals("football", ChannelMark.subOf("Sky Sports Football"));
        assertEquals("main event", ChannelMark.subOf("Sky Sports Main Event (Backup) [Multi-Audio]"));
        assertEquals("sports 1", ChannelMark.subOf("TNT Sports 1"));
        assertEquals("sports 1", ChannelMark.subOf("Premier Sports 1 FHD (UK)"));
        assertEquals("sports 2", ChannelMark.subOf("Eurosport2"));
        assertEquals("premier league", ChannelMark.subOf("Premier Sports Premier League"));
        assertEquals("", ChannelMark.subOf("Sky Sports+"));
        assertEquals("", ChannelMark.subOf("BBC One HD"));
        assertEquals("", ChannelMark.subOf("Sky Atlantic"));
        assertEquals("", ChannelMark.subOf(null));
    }
}
