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
        assertEquals(7, channel);
        assertFalse(LogoCover.Look.OFF.channel());
        assertFalse(LogoCover.Look.BADGE.channel());
        assertTrue(LogoCover.Look.MATCHDAY.channel());
        // Nothing chosen is Badge, as before.
        assertEquals(LogoCover.Look.BADGE, LogoCover.Look.of(""));
        assertEquals("GRAND PRIX", ChannelMark.channelFor("f1"));
        assertEquals("MATCHDAY", ChannelMark.channelFor(null));
    }
}
