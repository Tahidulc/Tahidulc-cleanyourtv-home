package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import com.gathin.broadcast.PicSources;
import com.gathin.broadcast.SkyListing;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * The family's "the Sky Sports programme backgrounds don't appear when scrolling through the TV guide on the phone but
 * appear on the TV": a sports channel's programme with no picture in the guide now takes Sky's own, found by the channel
 * and the time as the TV finds it (SkyPics, SkyListing), before TMDB and before its kind's picture. Sky's pages are made
 * up here (SkyPics.fake): nothing is asked of Sky.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SkyPicturesTest {
    private static final long NOW = System.currentTimeMillis();

    @After public void forget() {
        SkyPics.fake = null;
        SkyPics.forget();
    }

    /** Sky's list with Sky Sports Main Event (HD) and a day of its programmes around now. */
    private static void sky() throws Exception {
        Map<String, String> pages = new HashMap<>();
        JSONArray services = new JSONArray()
                .put(new JSONObject().put("sid", "4002").put("t", "SkySpMainEvHD").put("sf", "HD"))
                .put(new JSONObject().put("sid", "2306").put("t", "Dave").put("sf", "SD"));
        pages.put(SkyListing.SERVICES, new JSONObject().put("services", services).toString());
        long t = NOW / 1000;
        JSONArray events = new JSONArray()
                .put(new JSONObject().put("st", t - 1800).put("d", 7200).put("t", "Live Super Sunday").put("programmeuuid", "made-up-1"))
                .put(new JSONObject().put("st", t + 5400).put("d", 3600).put("t", "Soccer Talk").put("programmeuuid", "made-up-2"));
        String date = Instant.ofEpochMilli(NOW).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        pages.put(String.format(SkyListing.SCHEDULE, date, "4002"), new JSONObject().put("schedule", new JSONArray()
                .put(new JSONObject().put("sid", "4002").put("events", events))).toString());
        SkyPics.fake = pages;
    }

    @Test public void skyFindsASkyChannelsProgrammeByItsNameAndTime() throws Exception {
        sky();
        Context c = RuntimeEnvironment.getApplication();
        assertEquals(SkyListing.picture("made-up-1", SkyPics.WIDTH), SkyPics.find(c, "UK: Sky Sports Main Event FHD", NOW, "Live Super Sunday"));
        assertEquals(SkyListing.picture("made-up-2", SkyPics.WIDTH), SkyPics.find(c, "Sky Sports Main Event", NOW + 6000_000L, "Soccer Talk"));
        // A channel Sky doesn't list: none.
        assertEquals("", SkyPics.find(c, "Riverside Sport 1 HD", NOW, "Live Super Sunday"));
    }

    @Test public void aSkySportsProgrammeOnThePhonesGuideTakesSkysPicture() throws Exception {
        sky();
        Context c = RuntimeEnvironment.getApplication();
        Channel ch = new Channel("p:1", "UK: Sky Sports Main Event FHD", "UK | Sports", "", "http://example.invalid/1", "", "", "", 401);
        Guide.Builder b = new Guide.Builder();
        b.add(NOW - 1800_000L, NOW + 5400_000L, "Live Super Sunday", "Live coverage of the league game.");
        Guide.Schedule s = b.build();
        String url = null;
        for (int k = 0; k < 100 && url == null; k++) {
            url = ProgArt.of(c, ch, s, 0);
            if (url == null) {
                Thread.sleep(30);
                shadowOf(Looper.getMainLooper()).idle();
            }
        }
        assertNotNull(url);
        // Sky's own first, then the one always there (its kind's: a sports channel's game with no clubs known).
        assertEquals(SkyListing.picture("made-up-1", SkyPics.WIDTH), PicSources.parts(url).get(0));
        assertTrue(url, PicSources.parts(url).get(PicSources.parts(url).size() - 1).startsWith(PicSources.GENRE_AT));
    }

    @Test public void theGuidesOwnPictureComesFirstAndAtOnce() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        Channel ch = new Channel("p:2", "Kestrel Kids", "UK | Kids", "", "http://example.invalid/2", "", "", "", 402);
        com.gathin.broadcast.PicLinks.Builder links = new com.gathin.broadcast.PicLinks.Builder();
        Guide.Builder b = new Guide.Builder();
        b.add(NOW - 600_000L, NOW + 1200_000L, "Pip and the Moon", "", links.add("https://img.invalid/epg/pip.jpg"));
        Guide.Schedule s = b.build(links.build());
        String url = ProgArt.of(c, ch, s, 0);
        assertEquals(PicSources.chain("https://img.invalid/epg/pip.jpg", PicSources.genre(PicSources.KIDS)), url);
    }

    @Test public void withNothingFoundAProgrammeHasItsGamesItsKindsOrTheChannelsOwn() {
        Channel sport = new Channel("p:3", "Riverside Sport 1 HD", "UK | Sport", "", "http://example.invalid/3", "", "", "", 403);
        Guide.Builder b = new Guide.Builder();
        b.add(NOW, NOW + 3600_000L, "Live: Arsenal v Chelsea", "");
        b.add(NOW + 3600_000L, NOW + 7200_000L, "Live Darts: Night Six", "");
        Guide.Schedule s = b.build();
        assertTrue(ProgArt.fallback(sport, s, 0).startsWith(PicSources.FIXTURE_AT));
        assertEquals(PicSources.genre(PicSources.DARTS), ProgArt.fallback(sport, s, 1));
        Channel drama = new Channel("p:4", "Harbour One", "UK | Entertainment", "", "http://example.invalid/4", "", "", "", 404);
        Guide.Builder d = new Guide.Builder();
        d.add(NOW, NOW + 3600_000L, "The Long Tide", "A storm closes the harbour.");
        assertEquals("", ProgArt.fallback(drama, d.build(), 0));
        assertNull(ArtQuery.of("UK: Sky Sports Main Event FHD", "UK | Sports", "Live Super Sunday", ""));
    }
}
