package com.gathin.homefieldtv.sync;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Source;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Homefield TV's side of the family's sync, the pure parts: keys the same as the phone's, the round trip, the rules. */
public class TvLiveSyncTest {
    private static final Source XTREAM = new Source("xtream", "", "", "http://iptv.example.invalid:8080/", "harbourfamily", "s3cret-Pass");
    private static final Source M3U = new Source("m3u", "http://lists.example.invalid/family.m3u", "", "", "", "");

    @Test public void playlistKeysAreThePhonesKeys() {
        // The same hash of the same parts as the phone app's SyncKeys, never the password: these are its values too.
        assertEquals("pl:063a96b96cca9912e0880267", TvLiveSync.keyOf(XTREAM));
        assertEquals("pl:063a96b96cca9912e0880267", TvLiveSync.keyOf(new Source("xtream", "", "", "HTTP://IPTV.example.invalid:8080", "harbourfamily", "new-Pass")));
        assertEquals("pl:40438a5a3e52d4044a84ca1d", TvLiveSync.keyOf(M3U));
    }

    @Test public void aPlaylistGoesAndComesBackWithItsLogin() {
        Source back = TvLiveSync.sourceOf(TvLiveSync.playlistData("Family", XTREAM));
        assertEquals(XTREAM.server, back.server);
        assertEquals(XTREAM.user, back.user);
        assertEquals(XTREAM.pass, back.pass);
        assertTrue(back.usable());
        assertEquals(TvLiveSync.keyOf(XTREAM), TvLiveSync.keyOf(back));
        assertEquals(M3U.m3uUrl, TvLiveSync.sourceOf(TvLiveSync.playlistData("Lists", M3U)).m3uUrl);
        // The key says nothing of the login or the address.
        String k = TvLiveSync.keyOf(XTREAM);
        assertFalse(k.contains("harbour") || k.contains("s3cret") || k.contains("example"));
        // Only IPTV playlists are shared: nothing else (a TorBox key) ever goes as a playlist.
        assertTrue(TvLiveSync.shared(XTREAM) && TvLiveSync.shared(M3U));
        assertFalse(TvLiveSync.shared(new Source("torbox", "", "", "", "", "a-key")));
    }

    @Test public void lastWriterWinsForEveryKindTombstonesToo() {
        for (String type : new String[]{TvLiveSync.PLAYLIST, TvLiveSync.LIVEFAV, TvLiveSync.LIVERECENT, TvLiveSync.RESUME, TvLiveSync.SETTING}) {
            JSONObject item = SyncItems.item(type, "k", new JSONObject(), 1000, false, "Phone");
            assertTrue(type, SyncItems.wins(SyncItems.item(type, "k", new JSONObject(), 2000, true, "Tablet"), item));
            assertFalse(type, SyncItems.wins(SyncItems.item(type, "k", new JSONObject(), 500, true, "Tablet"), item));
            assertTrue(type, SyncItems.wins(SyncItems.item(type, "k", new JSONObject(), 1000, true, "Tablet"), item));
            assertTrue(type, SyncItems.wins(item, null));
        }
        assertEquals(TvLiveSync.PLAYLISTS, TvLiveSync.kindOf(TvLiveSync.PLAYLIST));
        assertEquals(TvLiveSync.PICKS, TvLiveSync.kindOf(TvLiveSync.LIVERECENT));
        assertEquals(TvLiveSync.WATCHING, TvLiveSync.kindOf(TvLiveSync.RESUME));
        assertEquals(TvLiveSync.SETTINGS, TvLiveSync.kindOf(TvLiveSync.SETTING));
    }

    @Test public void channelKeysSplitAndJoinAsLiveTvKeepsThem() {
        HashSet<String> ids = new HashSet<>(Arrays.asList("p0", "p1"));
        assertArrayEquals(new String[]{"p1", "x:42"}, TvLiveSync.splitChannel("p1:x:42", ids));
        assertArrayEquals(new String[]{"p0", "bbc1.uk|BBC One"}, TvLiveSync.splitChannel("bbc1.uk|BBC One", ids));
        assertNull(TvLiveSync.splitChannel("x:9", new HashSet<>(Collections.singletonList("p1"))));
        assertEquals("p1:x:7", TvLiveSync.joinChannel("p1", "x:7"));
        assertEquals("x:7", TvLiveSync.joinChannel("p0", "x:7"));
    }

    @Test public void aListKeepsOtherDevicesChannelsInTheirPlaces() throws Exception {
        JSONArray store = new JSONArray().put(ch("pl:A", "1")).put(ch("pl:B", "2")).put(ch("pl:A", "3"));
        List<JSONObject> mine = Arrays.asList(ch("pl:A", "3"), ch("pl:A", "1"), ch("pl:A", "5"));
        JSONArray out = TvLiveSync.mergeList(store, mine, new HashSet<>(Collections.singletonList("pl:A")), 10);
        assertEquals("[3, 2, 1, 5]", chs(out));
        assertEquals(Arrays.asList("a", "b", "c"), TvLiveSync.union(Arrays.asList("a", "b"), Arrays.asList("b", "c", "d"), 3));
    }

    @Test public void aResumePointSaysWhereItIsUpToAndNothingElseCounts() throws Exception {
        Resume.Point ep = new Resume.Point("p0|s|77", "901", "EN - The Long Tide (2025)", "S2 E5  ·  Low Water", "/still.jpg", 600_000L,
                2_700_000L, 1000L);
        JSONObject d = TvLiveSync.resumeData(ep);
        assertTrue(d.optBoolean("tv"));
        assertEquals(2, d.optInt("s"));
        assertEquals(5, d.optInt("e"));
        assertEquals("tv:the long tide", SyncKeys.resume(true, ep.name));
        assertEquals("", d.optString("poster"));
        JSONObject moved = new JSONObject(d.toString()).put("title", "The Long Tide").put("name", "The Long Tide");
        assertEquals(TvLiveSync.print(TvLiveSync.RESUME, d), TvLiveSync.print(TvLiveSync.RESUME, moved));
        assertNotEquals(TvLiveSync.print(TvLiveSync.RESUME, d), TvLiveSync.print(TvLiveSync.RESUME, new JSONObject(d.toString()).put("pos", 1L)));
        assertArrayEquals(new int[]{0, 0}, TvLiveSync.seasonEpisode(""));
        assertFalse(TvLiveSync.isSeries("p0|m|12"));
    }

    @Test public void settingsCarryTheirType() {
        JSONObject d = TvSettingsList.data(true, "b");
        assertTrue(d.optBoolean("v"));
        assertEquals("b", d.optString("t"));
        assertEquals("posters.landscape", TvSettingsList.TV[0][0]);
    }

    private static JSONObject ch(String pl, String ch) throws Exception {
        return new JSONObject().put("pl", pl).put("ch", ch);
    }

    private static String chs(JSONArray a) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length(); i++) b.append(i > 0 ? ", " : "").append(a.optJSONObject(i).optString("ch"));
        return b.append(']').toString();
    }
}
