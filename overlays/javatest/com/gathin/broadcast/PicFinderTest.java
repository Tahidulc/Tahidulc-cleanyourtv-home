package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A programme's own pictures looked for in the shared order (PicFinder): the guide's own, Sky's episode, Freeview's,
 * Virgin Media's, Sky's series, a game's, then by title; a +1 channel an hour on; never another programme's picture in
 * the same slot, never a placeholder or the channel's logo. Every page made up (PicFetch.fake): nothing is fetched.
 */
public class PicFinderTest {
    static final long T = Catalogues.isoTime("2026-11-25T19:00:00Z"), H = 3600_000L;
    private final Map<String, String> pages = new HashMap<>();

    @Before public void pages() throws Exception {
        PicFetch.fake = pages;
        PicFetch.fakeMissing = new HashSet<>();
        pages.put(SkyListing.SERVICES, new JSONObject().put("services", new JSONArray()
                .put(new JSONObject().put("sid", "4002").put("t", "SkySpMainEvHD").put("sf", "HD"))
                .put(new JSONObject().put("sid", "3007").put("t", "BBC One NW HD").put("sf", "HD"))
                .put(new JSONObject().put("sid", "4075").put("t", "Channel 4 HD").put("sf", "HD"))
                .put(new JSONObject().put("sid", "3809").put("t", "U&Dave HD").put("sf", "HD"))).toString());
        sky("4002", event("Live: Test Cricket", T, 3 * H, "ep-cricket", "", "series-cricket"),
                event("Live: Arsenal v Chelsea", T + 3 * H, 2 * H, "ep-game", "", ""));
        sky("3007", event("Bargain Hunt", T, H, "ep-bargain", "season-bargain", "series-bargain"),
                event("Escape to the Country", T + H, H, "ep-escape", "", ""));
        sky("4075", event("The Lantern House", T, H, "ep-lantern", "", ""));
        sky("3809", event("Taskmaster", T, H, "ep-task", "", "series-task"));
    }

    @After public void forget() {
        PicFetch.fake = null;
        PicFetch.fakeMissing = null;
    }

    private static JSONObject event(String t, long st, long d, String ep, String season, String series) throws Exception {
        JSONObject e = new JSONObject().put("t", t).put("st", st / 1000).put("d", d / 1000).put("programmeuuid", ep);
        if (!season.isEmpty()) e.put("seasonuuid", season);
        if (!series.isEmpty()) e.put("seriesuuid", series);
        return e;
    }

    private void sky(String sid, JSONObject... events) throws Exception {
        JSONArray ev = new JSONArray();
        for (JSONObject e : events) ev.put(e);
        String date = PicFinder.days(T, T + 1).get(0);
        pages.put(String.format(SkyListing.SCHEDULE, date, sid), new JSONObject().put("schedule", new JSONArray()
                .put(new JSONObject().put("sid", sid).put("events", ev))).toString());
    }

    private static String skyPic(String id) {
        return SkyListing.picture(id, 640);
    }

    private static PicFinder.Programme prog(String channel, String title, long start, long stop, String epg) {
        return new PicFinder.Programme(channel, "UK | Entertainment", title, "", "", start, stop, epg, "https://logos.invalid/ch.png");
    }

    private static List<String> parts(PicFinder.Found f) {
        return PicSources.parts(f.chain);
    }

    @Test public void theGuidesOwnThenSkysEpisode() {
        PicFinder f = new PicFinder(null, null, 640, 640);
        PicFinder.Found got = f.find(prog("UK: BBC One North West HD", "Bargain Hunt", T + 2 * 60_000L, T + H, "https://img.invalid/epg/bh.jpg"));
        assertEquals(Arrays.asList("https://img.invalid/epg/bh.jpg", skyPic("ep-bargain")), parts(got));
        assertEquals(PicFinder.EPG, got.source);
        // A placeholder or the channel's own logo given by the guide is passed over.
        got = f.find(prog("UK: BBC One North West HD", "Bargain Hunt", T, T + H, "https://logos.invalid/ch.png"));
        assertEquals(Arrays.asList(skyPic("ep-bargain")), parts(got));
        assertEquals(PicFinder.SKY, got.source);
    }

    @Test public void neverAnotherProgrammesPictureInTheSameSlot() {
        PicFinder f = new PicFinder(null, null, 640, 640);
        PicFinder.Found got = f.find(prog("BBC One HD", "Homes Under the Hammer", T, T + H, ""));
        assertTrue(got.chain, parts(got).isEmpty());
    }

    @Test public void skysPlaceholderGivesWayToFreeviewThenVirginMediaThenSkysSeries() throws Exception {
        PicFetch.fakeMissing.add(skyPic("ep-bargain"));
        long day = Catalogues.dayStart(T);
        pages.put(String.format(Locale.ROOT, Catalogues.FREEVIEW, day), new JSONObject().put("data", new JSONObject().put("programs", new JSONArray()
                .put(new JSONObject().put("title", "BBC ONE Lon").put("events", new JSONArray().put(new JSONObject()
                        .put("main_title", "Bargain Hunt").put("start_time", "2026-11-25T19:00:00+0000").put("duration", "PT1H")
                        .put("image_url", "https://img.invalid/fv/bh")))))).toString());
        PicFinder f = new PicFinder(null, null, 640, 640);
        PicFinder.Found got = f.find(prog("BBC One", "Bargain Hunt", T, T + H, ""));
        assertEquals(Arrays.asList("https://img.invalid/fv/bh?w=800"), parts(got));
        assertEquals(PicFinder.FREEVIEW, got.source);

        // Dave isn't Freeview's here: Virgin Media's picture by the programme's id.
        PicFetch.fakeMissing.add(skyPic("ep-task"));
        pages.put(Catalogues.VIRGIN_CHANNELS, new JSONArray().put(new JSONObject().put("id", "1571").put("name", "U&Dave HD")).toString());
        pages.put(String.format(Catalogues.VIRGIN_SEGMENT, Catalogues.virginSegment(T)), new JSONObject().put("entries", new JSONArray()
                .put(new JSONObject().put("channelId", "1571").put("events", new JSONArray().put(new JSONObject().put("title", "Taskmaster")
                        .put("startTime", T / 1000).put("endTime", (T + H) / 1000).put("id", "crid:~~2F~~2Fmade.up~~2F7,imi:x"))))).toString());
        got = f.find(prog("UK: Dave HD", "Taskmaster", T, T + H, ""));
        assertEquals(Arrays.asList(Catalogues.virginImage("crid:~~2F~~2Fmade.up~~2F7", 640)), parts(got));
        assertEquals(PicFinder.VIRGIN, got.source);

        // Virgin Media has none either: Sky's series picture.
        PicFetch.fakeMissing.add(Catalogues.virginImage("crid:~~2F~~2Fmade.up~~2F7", 640));
        got = new PicFinder(null, null, 640, 640).find(prog("UK: Dave HD", "Taskmaster", T, T + H, ""));
        assertEquals(Arrays.asList(skyPic("series-task")), parts(got));
        assertEquals(PicFinder.SKY_SERIES, got.source);
    }

    @Test public void aPlusOneChannelIsItsOwnAnHourOn() {
        PicFinder.Found got = new PicFinder(null, null, 640, 640).find(prog("Channel 4+1 HD", "The Lantern House", T + H, T + 2 * H, ""));
        assertEquals(Arrays.asList(skyPic("ep-lantern")), parts(got));
    }

    @Test public void aGameTakesSkysPictureElseItsOwnFromTheSportsDb() throws Exception {
        PicFinder.Found got = new PicFinder(null, null, 640, 640).find(prog("Sky Sports Main Event", "Live: Arsenal v Chelsea", T + 3 * H,
                T + 5 * H, ""));
        assertEquals(Arrays.asList(skyPic("ep-game")), parts(got));
        PicFetch.fakeMissing.add(skyPic("ep-game"));
        pages.put(String.format(Catalogues.SPORTSDB_EVENTS, "Arsenal_vs_Chelsea"), new JSONObject().put("event", new JSONArray().put(new JSONObject()
                .put("strHomeTeam", "Arsenal").put("strAwayTeam", "Chelsea").put("strTimestamp", "2026-11-25T22:00:00")
                .put("strThumb", "https://img.invalid/tsdb/game.jpg"))).toString());
        got = new PicFinder(null, null, 640, 640).find(prog("Sky Sports Main Event", "Live: Arsenal v Chelsea", T + 3 * H, T + 5 * H, ""));
        assertEquals(Arrays.asList("https://img.invalid/tsdb/game.jpg"), parts(got));
        assertEquals(PicFinder.SPORTSDB, got.source);
    }

    @Test public void byTitleTheEpisodesThenTheSeries() throws Exception {
        pages.put(String.format(Catalogues.TVMAZE_SHOW, "Night+Shift"), new JSONObject().put("id", 42).put("name", "Night Shift")
                .put("image", new JSONObject().put("original", "https://img.invalid/maze/poster.jpg")).toString());
        pages.put(String.format(Locale.ROOT, Catalogues.TVMAZE_DAY, 42, "2026-11-25"), new JSONArray().put(new JSONObject()
                .put("airstamp", "2026-11-25T21:00:00+00:00").put("image", new JSONObject().put("original", "https://img.invalid/maze/ep.jpg"))).toString());
        int[] asked = new int[2];
        PicFinder f = new PicFinder(null, (p, s, e) -> {
            asked[0] = s;
            asked[1] = e;
            return new String[]{"https://img.invalid/tmdb/still.jpg", "https://img.invalid/tmdb/backdrop.jpg"};
        }, 640, 640);
        PicFinder.Programme p = new PicFinder.Programme("Harbour One", "UK | Entertainment", "Night Shift", "S3 E2. The ward fills up.", "",
                T + 2 * H, T + 3 * H, "", "");
        PicFinder.Found got = f.find(p);
        assertEquals(Arrays.asList("https://img.invalid/maze/ep.jpg", "https://img.invalid/tmdb/still.jpg", "https://img.invalid/tmdb/backdrop.jpg",
                "https://img.invalid/maze/poster.jpg"), parts(got));
        assertEquals(PicFinder.TVMAZE, got.source);
        assertEquals(3, asked[0]);
        assertEquals(2, asked[1]);
        assertFalse(got.later);
        // TMDB can't be asked now: asked again later.
        got = new PicFinder(null, (q, s, e) -> null, 640, 640).find(p);
        assertTrue(got.later);
    }
}
