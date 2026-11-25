package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * The UK guide catalogues read the same way in both apps (Catalogues): each one's listings made small, our channels
 * found in each one's own list, the programme found by its time and title together (never another one in the same
 * slot), and each one's picture. Made-up pages: nothing is fetched.
 */
public class CataloguesTest {
    static final long T = Catalogues.isoTime("2026-11-25T09:30:00Z");

    @Test public void channelsAreFoundUnderEachCataloguesOwnNames() {
        // Our playlists' names, Sky's, Freeview's and Virgin Media's for the same channel: one key.
        String[][] same = {
                {"UK: Sky Sports Main Event FHD", "SkySpMainEvHD", "Sky SportsMainEventHD", "Sky Sports Main Event"},
                {"UK: Sky Sports Premier League HD", "SkySp PL HD", "Sky Sports PL HD", "Sky Sports Premier League"},
                {"UK | BBC One North West HD", "BBC One NW HD", "BBC ONE Lon", "BBC ONE London HD"},
                {"ITV1 Granada HD", "ITV1 HD", "ITV1", "ITV 1 London"},
                {"Channel 5 HD", "5 HD", "5", "Channel 5 HD"},
                {"UK: Channel 4", "Channel 4 HD", "Channel 4", "Channel 4HD"},
                {"[UK] Gold", "U&Gold HD", "U&GOLD HD", "Gold"},
                {"UK: Dave", "U&Dave HD", "U&Dave", "UKTV Dave HD".replace("UKTV ", "")},
                {"UK: Sky Cinema Premiere", "SkyPremiereHD", "Sky Premiere HD", "Sky Premiere"},
                {"BBC News Channel", "BBC NEWS HD", "BBC NEWS", "BBC NEWS HD"},
                {"UK: TNT Sports 1 UHD", "TNTSports1 HD", "TNT Sports 1 HD", "BT Sport 1"}};
        for (String[] s : same) {
            String k = Catalogues.channelKey(s[0]);
            for (String n : s) assertEquals(n + " vs " + s[0], k, Catalogues.channelKey(n));
        }
        assertEquals("skysportsmainevent", Catalogues.channelKey("Sky Sports Main Event"));
        assertEquals("5", Catalogues.channelKey("Channel 5"));
        // Other channels stay apart.
        assertFalse(Catalogues.channelKey("BBC Two").equals(Catalogues.channelKey("BBC One")));
        assertFalse(Catalogues.channelKey("Sky Sports Football").equals(Catalogues.channelKey("Sky Sports Main Event")));
        assertFalse(Catalogues.channelKey("E4 Extra").equals(Catalogues.channelKey("E4")));
    }

    @Test public void aPlusOneIsItsChannelAnHourOn() {
        assertEquals(1, Catalogues.plusHours("ITV1 +1"));
        assertEquals(1, Catalogues.plusHours("Channel 4+1 HD"));
        assertEquals(1, Catalogues.plusHours("UK: Dave +1"));
        assertEquals(0, Catalogues.plusHours("ITV1 HD"));
        assertEquals(Catalogues.channelKey("Channel 4"), Catalogues.channelKey("Channel 4+1 HD"));
        assertEquals(Catalogues.channelKey("ITV1"), Catalogues.channelKey("ITV1 +1"));
    }

    @Test public void severalRegionsTheFamilysFirst() {
        Map<String, String> m = new HashMap<>();
        m.put("BBC ONE Yorkshire HD", "1");
        m.put("BBC ONE London HD", "2");
        m.put("BBC ONE North West HD", "3");
        m.put("BBC ONE +1", "4");
        assertEquals("3", Catalogues.byKey(m).get("bbcone"));
        m.remove("BBC ONE North West HD");
        assertEquals("2", Catalogues.byKey(m).get("bbcone"));
    }

    @Test public void titlesAreTidiedAndMatchedWithCare() {
        assertEquals("testcricket", Catalogues.tidyTitle("Live: Test Cricket (R)"));
        assertEquals("chase", Catalogues.tidyTitle("The Chase S12 E4"));
        assertEquals("lanternhouse", Catalogues.tidyTitle("New: The Lantern House [S,AD]".replace("[S,AD]", "")));
        assertEquals("nightshift", Catalogues.tidyTitle("Night Shift (3/6)"));
        assertTrue(Catalogues.sameTitle("Live: Test Cricket", "Test Cricket: South Africa v Bangladesh"));
        assertTrue(Catalogues.sameTitle("Live: Arsenal v Chelsea", "Premier League: Arsenal vs Chelsea"));
        assertTrue(Catalogues.sameTitle("Grey's Anatomy", "Greys Anatomy"));
        assertTrue(Catalogues.sameTitle("Coastal Kitchens", "Coastal Kitchen"));
        assertFalse(Catalogues.sameTitle("Breakfast", "Morning Live"));
        assertFalse(Catalogues.sameTitle("Live: Arsenal v Chelsea", "Live: Arsenal v Fulham"));
        assertFalse(Catalogues.sameTitle("News", "Newsround"));
        assertFalse(Catalogues.sameTitle("", "Breakfast"));
    }

    private static Catalogues.Listing listing(String... titlesEvery30) {
        int n = titlesEvery30.length;
        long[] a = new long[n], b = new long[n];
        String[] p = new String[n];
        for (int i = 0; i < n; i++) {
            a[i] = T + i * 1800_000L;
            b[i] = a[i] + 1800_000L;
            p[i] = "pic-" + i;
        }
        return new Catalogues.Listing(a, b, titlesEvery30, p);
    }

    @Test public void theProgrammeIsFoundByItsTimeAndTitleNeverAnotherInItsSlot() {
        Catalogues.Listing l = listing("Morning Live", "Homes Under the Hammer", "Bargain Hunt", "BBC News at One");
        // Our guide's times a few minutes off theirs, its title a little different: found.
        assertEquals(1, Catalogues.match(l, T + 1800_000L + 5 * 60_000L, T + 3600_000L + 5 * 60_000L, "Homes Under The Hammer (R)"));
        assertEquals(2, Catalogues.match(l, T + 3600_000L, T + 5400_000L, "New: Bargain Hunt S69 E19"));
        // Another programme in the same slot: none, never the wrong picture.
        assertEquals(-1, Catalogues.match(l, T + 3600_000L, T + 5400_000L, "Escape to the Country"));
        // The same title at another time: not that one.
        assertEquals(-1, Catalogues.match(l, T + 6 * 3600_000L, T + 7 * 3600_000L, "Bargain Hunt"));
        assertEquals(-1, Catalogues.match(null, T, T + 1, "Bargain Hunt"));
    }

    @Test public void freeviewsDayIsReadSmall() throws Exception {
        JSONObject e1 = new JSONObject().put("main_title", "Morning Live").put("start_time", "2026-11-25T09:30:00+0000").put("duration", "PT1H15M")
                .put("image_url", "https://img.invalid/pa1");
        JSONObject e2 = new JSONObject().put("main_title", "Close").put("start_time", "2026-11-25T10:45:00+0000").put("duration", "PT5M");
        String json = new JSONObject().put("status", "success").put("data", new JSONObject().put("programs", new JSONArray()
                .put(new JSONObject().put("service_id", "1").put("title", "BBC ONE Lon").put("events", new JSONArray().put(e2).put(e1))))).toString();
        Map<String, Catalogues.Listing> m = Catalogues.freeview(json);
        Catalogues.Listing l = m.get("BBC ONE Lon");
        assertEquals(2, l.size());
        assertEquals(T, l.start[0]);
        assertEquals(T + 75 * 60_000L, l.end[0]);
        assertEquals("https://img.invalid/pa1?w=800", l.pic[0]);
        assertEquals("", l.pic[1]);
        assertTrue(Catalogues.freeview("not json").isEmpty());
        assertEquals(Catalogues.isoTime("2026-11-25T00:00:00Z") / 1000L, Catalogues.dayStart(T));
    }

    @Test public void virginMediasSegmentAndChannelsAreReadSmall() throws Exception {
        JSONObject ev = new JSONObject().put("startTime", T / 1000).put("endTime", T / 1000 + 3600).put("title", "Live: Test Cricket")
                .put("id", "crid:~~2F~~2Fmade.up~~2F1~~2FEP1,imi:abc");
        String seg = new JSONObject().put("entries", new JSONArray().put(new JSONObject().put("channelId", "1520")
                .put("events", new JSONArray().put(ev)))).toString();
        Catalogues.Listing l = Catalogues.virgin(seg).get("1520");
        assertEquals("crid:~~2F~~2Fmade.up~~2F1~~2FEP1", l.pic[0]);
        assertEquals("https://staticqbr-prod-gb.gnp.cloud.virgintvgo.virginmedia.com/image-service/intent/crid:~~2F~~2Fmade.up~~2F1~~2FEP1"
                + "/detailedBackground?w=1280", Catalogues.virginImage(l.pic[0], 1280));
        assertEquals("", Catalogues.virginImage("not-a-crid", 640));
        String chans = new JSONArray().put(new JSONObject().put("id", "1520").put("name", "Sky SportsMainEventHD"))
                .put(new JSONObject().put("id", "9").put("name", "PPV").put("isHidden", true)).toString();
        Map<String, String> c = Catalogues.byKey(Catalogues.virginChannels(chans));
        assertEquals("1520", c.get(Catalogues.channelKey("UK: Sky Sports Main Event FHD")));
        assertEquals(1, c.size());
        assertEquals("20261125060000", Catalogues.virginSegment(T));
    }

    @Test public void tvmazeAndTheSportsDbGiveTheirPicturesOnlyForTheSameProgramme() throws Exception {
        String show = new JSONObject().put("id", 793).put("name", "EastEnders").put("image", new JSONObject().put("original", "https://img.invalid/ee.jpg")).toString();
        assertEquals("https://img.invalid/ee.jpg", Catalogues.tvmazeShow(show, "EastEnders")[1]);
        assertNull(Catalogues.tvmazeShow(show, "EastEnders Revealed"));
        String eps = new JSONArray().put(new JSONObject().put("airstamp", "2026-11-25T19:30:00+00:00").put("image", new JSONObject().put("original", "https://img.invalid/ep.jpg")))
                .put(new JSONObject().put("airstamp", "2026-11-25T06:00:00+00:00").put("image", JSONObject.NULL)).toString();
        assertEquals("https://img.invalid/ep.jpg", Catalogues.tvmazeEpisode(eps, Catalogues.isoTime("2026-11-25T19:30:00Z")));
        JSONObject game = new JSONObject().put("strHomeTeam", "Arsenal").put("strAwayTeam", "Chelsea").put("strTimestamp", "2026-11-25T20:00:00")
                .put("strThumb", "https://img.invalid/game.jpg");
        String found = new JSONObject().put("event", new JSONArray().put(game)).toString();
        long kick = Catalogues.isoTime("2026-11-25T19:30:00Z");
        assertEquals("https://img.invalid/game.jpg", Catalogues.sportsdbThumb(found, "Arsenal", "Chelsea", kick));
        // Another season's game between the same two: not this one.
        assertEquals("", Catalogues.sportsdbThumb(found, "Arsenal", "Chelsea", kick + 90 * 24 * 3600_000L));
        assertEquals("", Catalogues.sportsdbThumb(found, "Arsenal", "Fulham", kick));
        assertEquals("Arsenal_vs_Chelsea", Catalogues.sportsdbQuery("Arsenal", "Chelsea"));
    }

    @Test public void timesAndLengths() {
        assertEquals(T, Catalogues.isoTime("2026-11-25T09:30:00+0000"));
        assertEquals(T, Catalogues.isoTime("2026-11-25T10:30:00+01:00"));
        assertEquals(T, Catalogues.isoTime("2026-11-25T09:30:00.000Z"));
        assertEquals(0, Catalogues.isoTime("soon"));
        assertEquals(75 * 60_000L, Catalogues.duration("PT1H15M"));
        assertEquals(5 * 60_000L, Catalogues.duration("PT5M"));
        assertEquals(0, Catalogues.duration("an hour"));
    }
}
