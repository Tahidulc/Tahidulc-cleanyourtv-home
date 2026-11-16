package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Map;

/** Programme pictures from Sky's public guide: its short names, a playlist's channel matched to one, the programme picked. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SkyGuideTest {
    @Test public void skysShortNamesSpeltOut() {
        assertEquals("Sky Sports Main Event HD", SkyGuide.skyName("SkySpMainEvHD"));
        assertEquals("Sky Sports Premier League", SkyGuide.skyName("SkySp PL"));
        assertEquals("Dave", SkyGuide.skyName("U&Dave"));
        assertEquals("Comedy Central HD", SkyGuide.skyName("ComedyCentHD"));
        assertEquals("Nat Geo Wild HD", SkyGuide.skyName("NatGeoWild HD"));
        // BBC One's regions are the one channel.
        assertEquals("BBC One HD", SkyGuide.skyName("BBC One NW HD"));
        assertEquals("BBC One", SkyGuide.skyName("BBC One Lon"));
        assertEquals("", SkyGuide.skyName(null));
    }

    @Test public void aPlaylistsNameMatchesSkys() {
        assertEquals(SkyGuide.key(SkyGuide.skyName("SkySpMainEvHD")), SkyGuide.key("UK: Sky Sports Main Event FHD"));
        assertEquals(SkyGuide.key(SkyGuide.skyName("NatGeoWild HD")), SkyGuide.key("UK: National Geographic Wild HD"));
        assertEquals(SkyGuide.key(SkyGuide.skyName("BBC One NW HD")), SkyGuide.key("BBC One HD"));
    }

    @Test public void theNorthWestsBbcOneThenHdNotAdult() {
        String json = "{\"services\":["
                + "{\"sid\":\"2002\",\"t\":\"BBC One Lon HD\",\"sf\":\"HD\"},"
                + "{\"sid\":\"2153\",\"t\":\"BBC One NW HD\",\"sf\":\"HD\"},"
                + "{\"sid\":\"3007\",\"t\":\"BBC One NW\",\"sf\":\"SD\"},"
                + "{\"sid\":\"4002\",\"t\":\"SkySpMainEvHD\",\"sf\":\"HD\"},"
                + "{\"sid\":\"6000\",\"t\":\"Late Night Room\",\"sf\":\"SD\",\"adult\":true}]}";
        Map<String, String> s = SkyGuide.services(json);
        assertEquals("2153", s.get(SkyGuide.key("BBC One HD")));
        assertEquals("4002", s.get(SkyGuide.key("Sky Sports Main Event")));
        assertFalse(s.containsKey(SkyGuide.key("Late Night Room")));
        assertEquals(0, SkyGuide.services("not json").size());
    }

    @Test public void theProgrammeOnAtATime() throws Exception {
        JSONArray events = new JSONArray("["
                + "{\"st\":1000,\"d\":1800,\"t\":\"News at Six\",\"programmeuuid\":\"a\"},"
                + "{\"st\":2800,\"d\":1800,\"t\":\"The One Show\",\"programmeuuid\":\"b\"},"
                + "{\"st\":2800,\"d\":600,\"t\":\"Regional News\",\"programmeuuid\":\"c\"}]");
        assertEquals("a", SkyGuide.uuidAt(events, 1_500_000L, ""));
        // Two on at once: the one with the title.
        assertEquals("c", SkyGuide.uuidAt(events, 3_000_000L, "Regional News"));
        assertEquals("b", SkyGuide.uuidAt(events, 3_000_000L, "The One Show"));
        assertEquals("b", SkyGuide.uuidAt(events, 3_000_000L, "Something else"));
        // Nothing on yet: one starting within five minutes.
        assertEquals("a", SkyGuide.uuidAt(events, 990_000L, ""));
        assertEquals("", SkyGuide.uuidAt(events, 500_000L, ""));
        assertEquals("", SkyGuide.uuidAt(null, 500_000L, ""));
    }
}
