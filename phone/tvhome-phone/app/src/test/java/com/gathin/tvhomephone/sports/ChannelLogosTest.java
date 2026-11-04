package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class ChannelLogosTest {
    private static final String SKY = "https://d2n0069hmnqmmx.cloudfront.net/epgdata/1.0/newchanlogos/600/600/skychb";

    @Test public void skysLogosByName() {
        assertEquals(SKY + "1301.png", ChannelLogos.sky("Sky Sports Main Event"));
        assertEquals(SKY + "1301.png", ChannelLogos.sky("sky sports main event HD"));
        assertEquals(SKY + "4010.png", ChannelLogos.sky("Sky Sports Premier League"));
        assertEquals(SKY + "3835.png", ChannelLogos.sky("Sky Sports F1"));
        assertEquals(SKY + "3940.png", ChannelLogos.sky("Sky Sports+"));
        assertEquals(SKY + "3625.png", ChannelLogos.sky("TNT Sports 1"));
        assertEquals(SKY + "3007.png", ChannelLogos.sky("BBC One HD"));
        assertEquals(SKY + "6504.png", ChannelLogos.sky("ITV 1"));
        assertEquals(SKY + "4058.png", ChannelLogos.sky("5"));
        assertEquals(SKY + "4058.png", ChannelLogos.sky("Channel 5"));
        assertNull(ChannelLogos.sky("Sky Sports"));
        assertNull(ChannelLogos.sky("Amazon Prime Video"));
        assertNull(ChannelLogos.sky(null));
    }

    @Test public void streamingServices() {
        assertEquals("Amazon Prime Video", ChannelLogos.service("Prime Video"));
        assertEquals("Amazon Prime Video", ChannelLogos.service("Amazon Prime Video"));
        assertEquals("BBC iPlayer", ChannelLogos.service("BBC iPlayer"));
        assertEquals("Discovery+", ChannelLogos.service("discovery+"));
        assertTrue(ChannelLogos.isService("DAZN"));
        assertFalse(ChannelLogos.isService("Sky Sports F1"));
    }

    @Test public void providersLogosFromTmdb() throws Exception {
        JSONObject reply = new JSONObject("{\"results\":["
                + "{\"provider_name\":\"Amazon Prime Video with Ads\",\"logo_path\":\"/ads.jpg\"},"
                + "{\"provider_name\":\"Amazon Prime Video\",\"logo_path\":\"/prime.jpg\"},"
                + "{\"provider_name\":\"Netflix Standard with Ads\",\"logo_path\":\"/nfads.jpg\"},"
                + "{\"provider_name\":\"BBC iPlayer\",\"logo_path\":null}]}");
        Map<String, String> p = new HashMap<>();
        ChannelLogos.learn(reply, p);
        assertEquals("https://image.tmdb.org/t/p/w154/prime.jpg", ChannelLogos.match(p, "Amazon Prime Video"));
        assertEquals("https://image.tmdb.org/t/p/w154/nfads.jpg", ChannelLogos.match(p, "Netflix"));
        assertNull(ChannelLogos.match(p, "BBC iPlayer"));
        assertNull(ChannelLogos.match(p, "DAZN"));
        assertNull(ChannelLogos.match(p, ""));
    }
}
