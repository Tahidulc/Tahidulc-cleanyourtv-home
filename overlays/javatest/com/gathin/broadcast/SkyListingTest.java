package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Locale;
import java.util.Map;

/** Sky's public schedule read the same in both apps (SkyListing): names spelt out, the list, the programme on, its picture. */
public class SkyListingTest {
    /** A stand-in for the apps' Names.key: lower case, letters and figures, no HD. */
    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replace(" hd", "").replaceAll("[^a-z0-9]", "");
    }

    @Test public void skysShortNamesSpeltOut() {
        assertEquals("Sky Sports Main Event HD", SkyListing.skyName("SkySpMainEvHD"));
        assertEquals("Sky Sports Premier League", SkyListing.skyName("SkySp PL"));
        assertEquals("Dave", SkyListing.skyName("U&Dave"));
        assertEquals("BBC One HD", SkyListing.skyName("BBC One NW HD"));
        assertEquals("", SkyListing.skyName(null));
        assertEquals("natgeowildjr", SkyListing.keyOf("nationalgeographicwildjunior"));
    }

    @Test public void theListByNameTheFamilysRegionAndHdFirst() throws Exception {
        JSONArray a = new JSONArray()
                .put(new JSONObject().put("sid", "1").put("t", "SkySpMainEv").put("sf", "SD"))
                .put(new JSONObject().put("sid", "2").put("t", "SkySpMainEvHD").put("sf", "HD"))
                .put(new JSONObject().put("sid", "3").put("t", "BBC One Lon HD").put("sf", "HD"))
                .put(new JSONObject().put("sid", "4").put("t", "BBC One NW HD").put("sf", "HD"))
                .put(new JSONObject().put("sid", "5").put("t", "Late Night").put("adult", true));
        Map<String, String> m = SkyListing.services(new JSONObject().put("services", a).toString(), SkyListingTest::key);
        assertEquals("2", m.get("skysportsmainevent"));
        assertEquals("4", m.get("bbcone"));
        assertFalse(m.containsKey("latenight"));
        assertTrue(SkyListing.services("not json", SkyListingTest::key).isEmpty());
    }

    @Test public void theProgrammeOnItsTitleFirstAndItsPicture() throws Exception {
        long t = 1_800_000_000L;
        JSONArray ev = new JSONArray()
                .put(new JSONObject().put("st", t - 600).put("d", 3600).put("t", "Live Super Sunday").put("programmeuuid", "a1"))
                .put(new JSONObject().put("st", t - 600).put("d", 3600).put("t", "Soccer Talk").put("programmeuuid", "b2"))
                .put(new JSONObject().put("st", t + 7200).put("d", 3600).put("t", "Later").put("programmeuuid", "c3"));
        assertEquals("b2", SkyListing.uuidAt(ev, t * 1000, "Soccer Talk"));
        assertEquals("a1", SkyListing.uuidAt(ev, t * 1000, "Something Else"));
        assertEquals("", SkyListing.uuidAt(ev, (t + 4000) * 1000, ""));
        assertEquals("https://images.metadata.sky.com/pd-image/b2/16-9/1280", SkyListing.picture("b2", 1280));
        assertEquals("", SkyListing.picture("", 640));
        JSONObject day = new JSONObject().put("schedule", new JSONArray().put(new JSONObject().put("sid", "2").put("events", ev)));
        assertEquals(3, SkyListing.events(day.toString(), "2").length());
        assertEquals(0, SkyListing.events(day.toString(), "9").length());
        // The time looked at: now for the one on now, else ten minutes in (or half way through a short one).
        assertEquals(500L, SkyListing.lookAt(0, 1000, 500));
        assertEquals(10 * 60_000L, SkyListing.lookAt(0, 3600_000L, 5_000_000L));
    }
}
