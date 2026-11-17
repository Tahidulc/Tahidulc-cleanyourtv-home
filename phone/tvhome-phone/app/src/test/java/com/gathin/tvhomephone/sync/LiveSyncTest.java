package com.gathin.tvhomephone.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.SyncStore;
import com.gathin.tvhomephone.live.Source;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** The new kinds shared through the TVs (SYNC.md): their keys, a playlist's round trip, the lists' merge and the rules. */
public class LiveSyncTest {
    private static final Source XTREAM = new Source("xtream", "", "", "http://iptv.example.invalid:8080/", "harbourfamily", "s3cret-Pass");
    private static final Source M3U = new Source("m3u", "http://lists.example.invalid/get.php?u=harbour&p=other-Pass", "", "", "", "");

    @Test public void aPlaylistsKeyIsWhatItIsNeverItsPassword() {
        String k = LiveSync.keyOf(XTREAM);
        assertTrue(k.startsWith("pl:"));
        assertEquals(27, k.length());
        // The same account under another name, or after its password changed: the same playlist.
        assertEquals(k, LiveSync.keyOf(new Source("xtream", "", "", "HTTP://IPTV.example.invalid:8080", "harbourfamily", "new-Pass")));
        assertNotEquals(k, LiveSync.keyOf(new Source("xtream", "", "", "http://iptv.example.invalid:8080", "someoneelse", "s3cret-Pass")));
        assertFalse(k.contains("harbour") || k.contains("s3cret") || k.contains("example"));
        assertNotEquals(k, LiveSync.keyOf(M3U));
    }

    @Test public void aPlaylistGoesAndComesBackWhole() {
        JSONObject d = LiveSync.playlistData("Family", XTREAM);
        Source back = LiveSync.sourceOf(d);
        assertEquals(XTREAM.type, back.type);
        assertEquals(XTREAM.server, back.server);
        assertEquals(XTREAM.user, back.user);
        assertEquals(XTREAM.pass, back.pass);
        assertTrue(back.usable());
        Source m = LiveSync.sourceOf(LiveSync.playlistData("Lists", M3U));
        assertEquals(M3U.m3uUrl, m.m3uUrl);
        assertEquals(LiveSync.keyOf(M3U), LiveSync.keyOf(m));
    }

    @Test public void nothingSecretInWhatIsShownOrNoted() {
        String said = LiveSync.describe(LiveSync.playlistData("Family", XTREAM));
        assertEquals("Xtream Codes “Family”", said);
        for (String secret : new String[]{"harbourfamily", "s3cret", "iptv.example", "8080"}) assertFalse(said.contains(secret));
        String m = LiveSync.describe(LiveSync.playlistData("Lists", M3U));
        assertFalse(m.contains("other-Pass") || m.contains("lists.example"));
        // A Source says nothing of itself in words either.
        assertFalse(String.valueOf(XTREAM).contains("s3cret"));
    }

    @Test public void titlesAreKeyedAlikeOnEveryDevice() {
        assertEquals("dune part two", SyncKeys.title("EN - Dune: Part Two (2024) 4K"));
        assertEquals("dune part two", SyncKeys.title("Dune Part Two"));
        assertEquals("the long tide", SyncKeys.title("|UK| The Long Tide [UHD] HDR"));
        assertEquals("tv:the long tide", SyncKeys.resume(true, "The Long Tide (2025)"));
        assertEquals("mv:harbour lights", SyncKeys.resume(false, "Harbour Lights"));
    }

    @Test public void aFingerprintIgnoresTheOrderOfKeys() throws Exception {
        JSONObject a = new JSONObject().put("x", 1).put("y", new JSONArray().put("p").put("q")).put("z", "w");
        JSONObject b = new JSONObject().put("z", "w").put("y", new JSONArray().put("p").put("q")).put("x", 1);
        assertEquals(SyncKeys.fingerprint(a), SyncKeys.fingerprint(b));
        assertNotEquals(SyncKeys.fingerprint(a), SyncKeys.fingerprint(new JSONObject().put("x", 2)));
    }

    @Test public void aResumePointChangesOnlyWithWhereItIsUpTo() throws Exception {
        JSONObject a = new JSONObject().put("name", "Harbour Lights").put("title", "Harbour Lights").put("poster", "/a.jpg")
                .put("s", 1).put("e", 3).put("pos", 600000L).put("dur", 3000000L).put("at", 1000L);
        JSONObject b = new JSONObject(a.toString()).put("title", "Harbour Lights (2025)").put("poster", "/b.jpg");
        assertEquals(LiveSync.print(LiveSync.RESUME, a), LiveSync.print(LiveSync.RESUME, b));
        assertNotEquals(LiveSync.print(LiveSync.RESUME, a), LiveSync.print(LiveSync.RESUME, new JSONObject(a.toString()).put("pos", 650000L)));
        // Any other kind counts all of it.
        assertNotEquals(LiveSync.print(LiveSync.SETTING, a), LiveSync.print(LiveSync.SETTING, b));
    }

    @Test public void channelKeysSplitAndJoinAsLiveTvKeepsThem() {
        HashSetOf ids = new HashSetOf("p0", "p1", "p2");
        assertEquals(Arrays.asList("p1", "x:42"), Arrays.asList(LiveSync.splitChannel("p1:x:42", ids.set)));
        assertEquals(Arrays.asList("p0", "bbc1.uk|BBC One"), Arrays.asList(LiveSync.splitChannel("bbc1.uk|BBC One", ids.set)));
        assertEquals("p2:x:7", LiveSync.joinChannel("p2", "x:7"));
        assertEquals("x:7", LiveSync.joinChannel("p0", "x:7"));
        // A first playlist gone: an unprefixed key belongs to none.
        assertNull(LiveSync.splitChannel("x:9", new HashSetOf("p1").set));
    }

    @Test public void listsKeepOtherDevicesChannelsInTheirPlaces() throws Exception {
        JSONArray store = new JSONArray()
                .put(ch("pl:A", "1")).put(ch("pl:B", "2")).put(ch("pl:A", "3")).put(ch("pl:C", "4"));
        // This device has playlists A and C, its own order now 3, 1 (4 gone, 5 new).
        List<JSONObject> mine = Arrays.asList(ch("pl:A", "3"), ch("pl:A", "1"), ch("pl:C", "5"));
        JSONArray out = LiveSync.mergeList(store, mine, new HashSetOf("pl:A", "pl:C").set, 10);
        assertEquals("[3, 2, 1, 5]", chs(out));
        // Nothing changed here: the family's list as it was.
        List<JSONObject> same = Arrays.asList(ch("pl:A", "1"), ch("pl:A", "3"), ch("pl:C", "4"));
        assertEquals(chs(store), chs(LiveSync.mergeList(store, same, new HashSetOf("pl:A", "pl:C").set, 10)));
        // A device with none of them keeps the list whole; a cap is kept.
        assertEquals(chs(store), chs(LiveSync.mergeList(store, Collections.emptyList(), new HashSetOf().set, 10)));
        assertEquals(2, LiveSync.mergeList(store, mine, new HashSetOf("pl:A", "pl:C").set, 2).length());
    }

    @Test public void lastWriterWinsForEveryNewKindTombstonesToo() {
        for (String type : new String[]{LiveSync.PLAYLIST, LiveSync.LIVEFAV, LiveSync.LIVECAT, LiveSync.LIVERECENT, LiveSync.RESUME,
                LiveSync.SETTING}) {
            JSONObject item = SyncStore.item(type, "k", new JSONObject(), 1000, false, "Phone");
            JSONObject laterTombstone = SyncStore.item(type, "k", new JSONObject(), 2000, true, "Tablet");
            JSONObject earlierTombstone = SyncStore.item(type, "k", new JSONObject(), 500, true, "Tablet");
            assertTrue(type, SyncStore.wins(laterTombstone, item));
            assertFalse(type, SyncStore.wins(earlierTombstone, item));
            assertTrue(type, SyncStore.wins(item, earlierTombstone));
            // A tie goes to the greater device name.
            assertTrue(type, SyncStore.wins(SyncStore.item(type, "k", new JSONObject(), 1000, true, "Tablet"), item));
        }
        assertEquals(LiveSync.PLAYLISTS, LiveSync.kindOf(LiveSync.PLAYLIST));
        assertEquals(LiveSync.PICKS, LiveSync.kindOf(LiveSync.LIVECAT));
        assertEquals(LiveSync.WATCHING, LiveSync.kindOf(LiveSync.RESUME));
    }

    @Test public void twoDevicesListsPutTogetherKeepTheFirstsOrder() {
        assertEquals(Arrays.asList("a", "b", "c"), LiveSync.union(Arrays.asList("a", "b"), Arrays.asList("b", "c", "d"), 3));
        assertEquals(Arrays.asList("b", "c", "d"), LiveSync.union(Collections.emptyList(), Arrays.asList("b", "c", "d"), 10));
    }

    @Test public void aMapTooBigToSendIsCutTheSameWayEveryTime() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("id:c|c", "C");
        m.put("id:a|a", "A");
        m.put("id:b|b", "B");
        java.util.Map<String, String> all = LiveSync.fitMap(m, 1000);
        assertEquals(Arrays.asList("id:a|a", "id:b|b", "id:c|c"), new java.util.ArrayList<>(all.keySet()));
        java.util.Map<String, String> two = LiveSync.fitMap(m, 2 + 2 * ("\"id:a|a\"".length() + "\"A\"".length() + 2));
        assertEquals(Arrays.asList("id:a|a", "id:b|b"), new java.util.ArrayList<>(two.keySet()));
        assertTrue(new JSONObject(two).toString().length() <= 2 + 2 * ("\"id:a|a\"".length() + "\"A\"".length() + 2));
    }

    @Test public void settingsCarryTheirType() {
        assertEquals("b", SyncSettingsList.typeOf(Boolean.TRUE));
        assertEquals("i", SyncSettingsList.typeOf(3));
        assertEquals("s", SyncSettingsList.typeOf("en,bn"));
        JSONObject d = SyncSettingsList.data(true, "b");
        assertTrue(d.optBoolean("v"));
        assertEquals("b", d.optString("t"));
    }

    private static JSONObject ch(String pl, String ch) throws Exception {
        return new JSONObject().put("pl", pl).put("ch", ch);
    }

    private static String chs(JSONArray a) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < a.length(); i++) b.append(i > 0 ? ", " : "").append(a.optJSONObject(i).optString("ch"));
        return b.append(']').toString();
    }

    private static final class HashSetOf {
        final java.util.Set<String> set;

        HashSetOf(String... s) {
            set = new HashSet<>(Arrays.asList(s));
        }
    }
}
