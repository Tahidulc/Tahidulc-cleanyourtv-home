package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class NamesTest {
    private static void same(String listing, String channel) {
        assertEquals(listing + " / " + channel, Names.key(listing), Names.key(channel));
    }

    @Test public void countryAndQualityTagsAreSetAside() {
        same("Sky Sports Main Event", "UK: Sky Sports Main Event FHD");
        same("Sky Sports Main Event", "|UK| SKY SPORTS MAIN EVENT ᴴᴰ");
        same("Sky Sports Main Event", "[UK] Sky Sports MainEvent HD");
        same("Sky Sports Premier League", "UK • Sky Sports Premier League 4K");
        same("Sky Sports Premier League", "Sky Sports Premier League UK RAW");
        same("TNT Sports 1", "UK: TNT Sport 1 HD");
        same("TNT Sports 1", "BT Sport 1 HD");
        same("Sky Sports F1", "UK: Sky F1 HD");
        same("BBC One", "BBC 1 HD");
        same("ITV1", "UK: ITV HD");
        same("Sky Sports+", "Sky Sports Plus");
        same("Sky Sports Main Event", "ꜱᴋʏ ꜱᴘᴏʀᴛꜱ ᴍᴀɪɴ ᴇᴠᴇɴᴛ ꜰʜᴅ");
    }

    @Test public void differentChannelsStayApart() {
        assertNotEquals(Names.key("Sky Sports Main Event"), Names.key("Sky Sports Premier League"));
        assertNotEquals(Names.key("TNT Sports 1"), Names.key("TNT Sports 2"));
        assertNotEquals(Names.key("ITV1"), Names.key("ITV1 +1"));
        assertNotEquals(Names.key("Sky Sports Main Event"), Names.key("Sky Sports Main Event Backup Feed Extra"));
    }

    @Test public void tagsAreRead() {
        Names.Info a = Names.info("UK: Sky Sports Main Event FHD", "");
        assertTrue(a.uk);
        assertEquals(3, a.quality);
        Names.Info b = Names.info("Sky Sports Main Event SD", "International");
        assertFalse(b.uk);
        assertEquals(0, b.quality);
        assertTrue(Names.info("Sky Sports Main Event", "UK | Sports").uk);
        assertTrue(Names.info("Sky Sports Main Event HD Backup", "").backup);
        assertEquals(4, Names.info("Sky Sports Main Event UHD", "").quality);
    }

    @Test public void streamingServicesAreNotChannels() {
        assertTrue(Names.isService("Amazon Prime Video"));
        assertTrue(Names.isService("Prime Video"));
        assertTrue(Names.isService("Netflix"));
        assertTrue(Names.isService("Disney+"));
        assertTrue(Names.isService("DAZN"));
        assertTrue(Names.isService(""));
        assertFalse(Names.isService("Sky Sports Main Event"));
        assertFalse(Names.isService("TNT Sports 1"));
    }

    @Test public void listingsWithSeveralChannelsSplit() {
        assertEquals(Arrays.asList("Sky Sports Main Event", "Sky Sports Premier League"),
                Names.parts("Sky Sports Main Event / Sky Sports Premier League"));
        assertEquals(Arrays.asList("TNT Sports 1", "discovery+"), Names.parts("TNT Sports 1, discovery+"));
    }

    @Test public void fourKAndUhdChannelsAreTold() {
        assertTrue(Names.uhd("Sky Sports Main Event UHD"));
        assertTrue(Names.uhd("UK: SKY SPORTS F1 4K"));
        assertTrue(Names.uhd("|UK| TNT Sports Ultimate 4K HDR"));
        assertFalse(Names.uhd("Sky Sports Main Event HD"));
        assertFalse(Names.uhd("UK: SKY SPORTS F1 FHD"));
        // The 4K channel and the HD one are one channel, in two pictures.
        assertEquals(Names.info("Sky Sports Main Event HD", null).key, Names.info("UK: Sky Sports Main Event UHD", null).key);
    }
}
