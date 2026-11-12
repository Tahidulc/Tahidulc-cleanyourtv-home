package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The playlist's guide: channel names matched past their tags, the short guide read (base64, times), what is on. */
public class ListingsTest {
    private static final long MIN = 60_000L;

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void aChannelsNameIsComparedPastItsCountryAndQualityTags() {
        assertEquals("skysportsmainevent", Listings.base("UK: Sky Sports Main Event UHD"));
        assertEquals("skysportsmainevent", Listings.base("Sky Sports Main Event HD"));
        assertEquals("skysportsmainevent", Listings.base("UK | Sky Sports Main Event FHD"));
        assertEquals("skysportsmainevent", Listings.base("[UK] Sky Sports Main Event 4K"));
        assertEquals("skysportsmainevent", Listings.base("|UK| Sky Sports Main Event ᴴᴰ"));
        assertEquals("tntsports1", Listings.base("EN - TNT Sports 1 HD (Backup)"));
        assertEquals("bbcone", Listings.base("BBC One HEVC 50fps"));
        // A name that only looks like a tag stays whole.
        assertEquals("sportsd", Listings.base("Sports D"));
        assertEquals("", Listings.base(null));
    }

    @Test public void theChannelWatchedIsFoundAmongThePlaylists() {
        List<String[]> chans = Arrays.asList(new String[]{"UK: Sky Sports Football FHD", "101"},
                new String[]{"UK: Sky Sports Main Event UHD", "102"}, new String[]{"UK: Sky Sports Main Event HD", "103"});
        assertEquals("102", Listings.find(chans, Arrays.asList("Sky Sports Main Event"))[1]);
        assertEquals("101", Listings.find(chans, Arrays.asList("Sky Sports Mix", "Sky Sports Football HD"))[1]);
        assertNull(Listings.find(chans, Arrays.asList("TNT Sports 1")));
        assertNull(Listings.find(null, Arrays.asList("TNT Sports 1")));
    }

    @Test public void theShortGuideIsReadOutOfBase64InTimeOrder() {
        String json = "{\"epg_listings\":["
                + "{\"title\":\"" + b64("Live International Football: England v Croatia") + "\",\"description\":\"" + b64("World Cup qualifier from Wembley.")
                + "\",\"start\":\"2026-11-12 19:00:00\",\"end\":\"2026-11-12 22:00:00\",\"start_timestamp\":\"1794510000\",\"stop_timestamp\":\"1794520800\"},"
                + "{\"title\":\"" + b64("Football Build-up") + "\",\"description\":\"\",\"start\":\"2026-11-12 18:00:00\",\"end\":\"2026-11-12 19:00:00\"}"
                + "]}";
        List<Listings.Prog> p = Listings.parseShortEpg(json);
        assertEquals(2, p.size());
        assertEquals("Football Build-up", p.get(0).title);
        // No timestamps: the start and end read as UTC.
        assertEquals(1794506400_000L, p.get(0).start);
        assertEquals(1794510000_000L, p.get(0).end);
        assertEquals("Live International Football: England v Croatia", p.get(1).title);
        assertEquals("World Cup qualifier from Wembley.", p.get(1).desc);
        assertEquals(1794510000_000L, p.get(1).start);
        assertEquals(1794520800_000L, p.get(1).end);
        assertTrue(Listings.parseShortEpg("not json").isEmpty());
        assertTrue(Listings.parseShortEpg("{\"epg_listings\":[]}").isEmpty());
    }

    @Test public void aTitleThatWasNeverBase64IsKeptAsItIs() {
        assertEquals("Gogglebox", Listings.b64("Gogglebox"));
        assertEquals("News", Listings.b64("News"));
        assertEquals("Match of the Day", Listings.b64(b64("Match of the Day")));
        assertEquals("", Listings.b64(null));
    }

    @Test public void theProgrammeOnNowAndTheNextWithinAQuarterOfAnHour() {
        List<Listings.Prog> p = new ArrayList<>();
        p.add(new Listings.Prog("Before", "", 0, 60 * MIN));
        p.add(new Listings.Prog("Build-up", "", 60 * MIN, 120 * MIN));
        p.add(new Listings.Prog("England v Croatia", "", 120 * MIN, 240 * MIN));
        List<Listings.Prog> a = Listings.around(p, 110 * MIN, 15 * MIN);
        assertEquals(2, a.size());
        assertEquals("Build-up", a.get(0).title);
        assertEquals("England v Croatia", a.get(1).title);
        List<Listings.Prog> b = Listings.around(p, 90 * MIN, 15 * MIN);
        assertEquals(1, b.size());
        assertEquals("Build-up", b.get(0).title);
        assertTrue(Listings.around(p, 300 * MIN, 15 * MIN).isEmpty());
        assertTrue(Listings.around(null, 0, 15 * MIN).isEmpty());
    }

    @Test public void theBannerNamesOneChannelOnlyWhenItNamesExactlyOne() {
        Map<String, String[]> byBase = new HashMap<>();
        byBase.put("skysportsmainevent", new String[]{"UK: Sky Sports Main Event HD", "102"});
        byBase.put("tntsports1", new String[]{"UK: TNT Sports 1 HD", "201"});
        byBase.put("football", new String[]{"Football", "301"});
        assertEquals("UK: Sky Sports Main Event HD", Listings.named(Arrays.asList("Sky Sports Main Event", "England v Croatia", "19:00 - 22:00"), byBase));
        // Two channels named: a list or the guide.
        assertNull(Listings.named(Arrays.asList("Sky Sports Main Event", "TNT Sports 1"), byBase));
        // A lone word is a heading, not a channel.
        assertNull(Listings.named(Arrays.asList("Football", "England v Croatia"), byBase));
        assertNull(Listings.named(Arrays.asList("Gogglebox"), byBase));
        assertNull(Listings.named(null, byBase));
    }

    @Test public void aProgrammeIsOnFromItsStartToBeforeItsEnd() {
        Listings.Prog p = new Listings.Prog("x", null, 10, 20);
        assertTrue(p.on(10));
        assertTrue(p.on(19));
        assertArrayEquals(new boolean[]{false, false}, new boolean[]{p.on(9), p.on(20)});
        assertEquals("", p.desc);
    }
}
