package com.gathin.tvhomephone.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.tvhomephone.SyncStore;
import com.gathin.tvhomephone.live.LiveSyncHooks;
import com.gathin.tvhomephone.live.Source;
import com.gathin.tvhomephone.live.Sources;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

/**
 * Two devices in one: what one puts in at a sync arrives on the other as the TVs pass it on (SYNC.md). A playlist comes
 * with its login and is never typed in again; one removed goes everywhere; favourites follow their playlist whatever its
 * place on each device; a new device takes the family's first and loses nothing of its own; a kind switched off neither
 * goes nor comes. The host and login here are invented.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LiveSyncDevicesTest {
    private static final Source XTREAM = new Source("xtream", "", "", "http://iptv.example.invalid:8080", "harbourfamily", "s3cret-Pass");
    private static final Source M3U = new Source("m3u", "http://lists.example.invalid/family.m3u", "", "", "", "");
    private Context c;

    @Before public void fresh() {
        c = RuntimeEnvironment.getApplication();
        newDevice();
    }

    /** This app's stores as on a device that has never synced. */
    private void newDevice() {
        Sources.clear(c);
        for (String p : new String[]{"sync_store", "sync_live_sent", "sync_live_base", "sync_kinds", "sync_resume_tried", "subtitles",
                "live_picks", "live_groups", "live_hidden"}) {
            c.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    /** A sync that reached a TV with nothing new on it: the family's taken in (none yet), then this device's own put in. */
    private void firstSync() {
        if (LiveSync.apply(c, true)) LiveSync.export(c);
    }

    /** What this device sends at its next sync, as if made on the tablet at a given time. */
    private JSONArray sent(long updated) throws Exception {
        firstSync();
        LiveSync.export(c);
        JSONArray all = SyncStore.changedSince(c, 0);
        JSONArray out = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject it = all.getJSONObject(i);
            it.put("from", "Tablet").put("updated", updated);
            it.remove("seen");
            out.put(it);
        }
        return out;
    }

    private static JSONObject only(List<JSONObject> items, String key) {
        for (JSONObject it : items) if (key.equals(it.optString("key"))) return it;
        return null;
    }

    @Test public void aPlaylistAddedOnOneDeviceArrivesWithItsLogin() throws Exception {
        Sources.put(c, null, "Family", XTREAM);
        long then = System.currentTimeMillis() - 60_000L;
        JSONArray wire = sent(then);
        newDevice();
        Sources.put(c, null, "Lists", M3U);
        SyncStore.merge(c, wire);
        assertTrue(LiveSync.apply(c, true));
        LiveSync.export(c);
        List<Sources.Entry> here = Sources.list(c);
        assertEquals(2, here.size());
        Sources.Entry got = here.get(1);
        assertEquals("Family", got.name);
        assertEquals(XTREAM.server, got.source.server);
        assertEquals(XTREAM.user, got.source.user);
        assertEquals(XTREAM.pass, got.source.pass);
        // Written back here, it isn't sent on again as a change made here...
        JSONObject item = only(SyncStore.items(c, LiveSync.PLAYLIST), LiveSync.keyOf(XTREAM));
        assertNotNull(item);
        assertEquals("Tablet", item.optString("from"));
        assertEquals(then, item.optLong("updated"));
        // ... and this device's own goes the other way.
        assertNotNull(SyncStore.get(c, LiveSync.PLAYLIST, LiveSync.keyOf(M3U)));
    }

    @Test public void aPlaylistRemovedElsewhereGoesHereTooAndStaysGone() throws Exception {
        Sources.put(c, null, "Family", XTREAM);
        firstSync();
        String key = LiveSync.keyOf(XTREAM);
        assertNotNull(SyncStore.get(c, LiveSync.PLAYLIST, key));
        SyncStore.merge(c, new JSONArray().put(SyncStore.item(LiveSync.PLAYLIST, key, new JSONObject(),
                System.currentTimeMillis() + 1000, true, "Tablet")));
        assertFalse(LiveSync.apply(c, true));
        assertTrue(Sources.list(c).isEmpty());
        LiveSync.export(c);
        assertNull(SyncStore.get(c, LiveSync.PLAYLIST, key));
        assertEquals("Tablet", only(SyncStore.items(c, LiveSync.PLAYLIST), key).optString("from"));
    }

    @Test public void aPlaylistRemovedHereLeavesATombstone() {
        Sources.Entry e = Sources.put(c, null, "Family", XTREAM);
        firstSync();
        assertNotNull(SyncStore.get(c, LiveSync.PLAYLIST, LiveSync.keyOf(XTREAM)));
        Sources.remove(c, e.id);
        LiveSync.export(c);
        JSONObject item = only(SyncStore.items(c, LiveSync.PLAYLIST), LiveSync.keyOf(XTREAM));
        assertNotNull(item);
        assertTrue(item.optBoolean("deleted"));
    }

    @Test public void aNewDeviceSendsNothingBeforeItHasTakenTheFamilysIn() throws Exception {
        Sources.put(c, null, "Family", XTREAM);
        LiveSyncHooks.setPicks(c, Arrays.asList("x:42"), null);
        JSONArray wire = sent(System.currentTimeMillis() - 60_000L);
        newDevice();
        // The tablet had the same playlist and a favourite of its own before it was paired.
        Sources.put(c, null, "Ours", XTREAM);
        LiveSyncHooks.setPicks(c, Arrays.asList("x:99"), null);
        LiveSync.export(c);
        LiveSync.apply(c, false);
        LiveSync.export(c);
        assertTrue(SyncStore.items(c, LiveSync.LIVEFAV).isEmpty());
        assertTrue(SyncStore.items(c, LiveSync.PLAYLIST).isEmpty());
        // Then a TV answers: the family's first, its own after them, and the whole list goes out.
        SyncStore.merge(c, wire);
        assertTrue(LiveSync.apply(c, true));
        LiveSync.export(c);
        assertEquals(Arrays.asList("x:42", "x:99"), LiveSyncHooks.favourites(c));
        JSONArray list = SyncStore.get(c, LiveSync.LIVEFAV, "list").optJSONArray("channels");
        assertEquals(2, list.length());
        assertEquals("x:99", list.getJSONObject(1).optString("ch"));
    }

    @Test public void aPlaylistTheFamilyRemovedStaysOnANewDeviceThatHasIt() {
        Sources.put(c, null, "Ours", XTREAM);
        String key = LiveSync.keyOf(XTREAM);
        SyncStore.merge(c, new JSONArray().put(SyncStore.item(LiveSync.PLAYLIST, key, new JSONObject(),
                System.currentTimeMillis() - 60_000L, true, "Tablet")));
        assertTrue(LiveSync.apply(c, true));
        assertEquals(1, Sources.list(c).size());
        LiveSync.export(c);
        assertNotNull(SyncStore.get(c, LiveSync.PLAYLIST, key));
    }

    @Test public void removeEverythingClearsThisPhoneOnlyAndSwitchesPlaylistsOff() throws Exception {
        Sources.put(c, null, "Family", XTREAM);
        LiveSyncHooks.setPicks(c, Arrays.asList("x:42"), Arrays.asList("x:42"));
        LiveSyncHooks.setGroups(c, new java.util.HashSet<>(Arrays.asList("Adult")), Arrays.asList("Sports"));
        firstSync();
        String key = LiveSync.keyOf(XTREAM);
        assertNotNull(SyncStore.get(c, LiveSync.PLAYLIST, key));
        // What Live TV's Remove everything clears (Library.clear), with the sync held still.
        LiveSync.removeEverything(c, () -> {
            Sources.clear(c);
            LiveSyncHooks.setPicks(c, new java.util.ArrayList<>(), new java.util.ArrayList<>());
            LiveSyncHooks.setGroups(c, new java.util.HashSet<>(), new java.util.ArrayList<>());
        });
        assertFalse(LiveSync.on(c, LiveSync.PLAYLISTS));
        assertTrue(LiveSync.offByRemoveEverything(c));
        // The next syncs: nothing goes out as a removal, and the family's playlist doesn't come straight back.
        LiveSync.export(c);
        assertTrue(LiveSync.apply(c, true));
        LiveSync.export(c);
        for (String type : new String[]{LiveSync.PLAYLIST, LiveSync.LIVEFAV, LiveSync.LIVECAT, LiveSync.LIVERECENT, LiveSync.RESUME}) {
            for (JSONObject it : SyncStore.items(c, type)) assertFalse(type + " " + it.optString("key"), it.optBoolean("deleted"));
        }
        assertNotNull(SyncStore.get(c, LiveSync.PLAYLIST, key));
        assertTrue(SyncStore.get(c, LiveSync.LIVECAT, "groups").getJSONArray("hidden").toString().contains("Adult"));
        assertEquals(1, SyncStore.get(c, LiveSync.LIVEFAV, "list").getJSONArray("channels").length());
        assertTrue(Sources.list(c).isEmpty());
        // Switched on again: the family's playlist is taken in, login and all, as on a new device.
        LiveSync.setOn(c, LiveSync.PLAYLISTS, true);
        assertFalse(LiveSync.offByRemoveEverything(c));
        assertTrue(LiveSync.apply(c, true));
        assertEquals(1, Sources.list(c).size());
        assertEquals(XTREAM.pass, Sources.list(c).get(0).source.pass);
        assertEquals(Arrays.asList("x:42"), LiveSyncHooks.favourites(c));
    }

    @Test public void favouritesFollowTheirPlaylistWhateverItsPlaceHere() throws Exception {
        Sources.put(c, null, "Family", XTREAM);
        LiveSyncHooks.setPicks(c, Arrays.asList("x:42", "x:7"), Arrays.asList("x:7"));
        JSONArray wire = sent(System.currentTimeMillis() - 60_000L);
        newDevice();
        // Here another playlist came first, so the family's one is p1.
        Sources.put(c, null, "Lists", M3U);
        SyncStore.merge(c, wire);
        LiveSync.apply(c, true);
        assertEquals(Arrays.asList("p1:x:42", "p1:x:7"), LiveSyncHooks.favourites(c));
        assertEquals(Arrays.asList("p1:x:7"), LiveSyncHooks.recent(c));
    }

    @Test public void hiddenChannelsTooManyToSendStayHere() throws Exception {
        java.util.LinkedHashMap<String, String> many = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 3000; i++) many.put(String.format(java.util.Locale.ROOT, "id:channel%04d.example|channel %d", i, i), "Channel " + i);
        LiveSyncHooks.setHiddenChannels(c, many);
        firstSync();
        JSONObject went = SyncStore.get(c, LiveSync.LIVECAT, "channels").optJSONObject("hidden");
        assertTrue(went.length() > 100 && went.length() < 3000);
        assertTrue(went.toString().length() <= LiveSync.ITEM_BYTES);
        // Another device unhides one that went: it goes here too; those that never went stay hidden.
        String gone = went.keys().next();
        JSONObject now = new JSONObject(went.toString());
        now.remove(gone);
        SyncStore.merge(c, new JSONArray().put(SyncStore.item(LiveSync.LIVECAT, "channels", new JSONObject().put("hidden", now),
                System.currentTimeMillis() + 1000, false, "Tablet")));
        LiveSync.apply(c, true);
        assertEquals(2999, LiveSyncHooks.hiddenChannels(c).size());
        assertFalse(LiveSyncHooks.hiddenChannels(c).containsKey(gone));
    }

    @Test public void aSettingArrivesUnderThisAppsOwnName() throws Exception {
        c.getSharedPreferences("subtitles", Context.MODE_PRIVATE).edit().putInt("size", 3).putBoolean("on", true).commit();
        JSONArray wire = sent(System.currentTimeMillis() - 60_000L);
        newDevice();
        SyncStore.merge(c, wire);
        LiveSync.apply(c, true);
        SharedPreferences p = c.getSharedPreferences("subtitles", Context.MODE_PRIVATE);
        assertEquals(3, p.getInt("size", 0));
        assertTrue(p.getBoolean("on", false));
    }

    @Test public void aKindSwitchedOffNeitherGoesNorComes() {
        LiveSync.setOn(c, LiveSync.PLAYLISTS, false);
        Sources.put(c, null, "Family", XTREAM);
        firstSync();
        LiveSync.export(c);
        assertTrue(SyncStore.items(c, LiveSync.PLAYLIST).isEmpty());
        SyncStore.merge(c, new JSONArray().put(SyncStore.item(LiveSync.PLAYLIST, LiveSync.keyOf(M3U), LiveSync.playlistData("Lists", M3U),
                System.currentTimeMillis(), false, "Tablet")));
        LiveSync.apply(c, true);
        assertEquals(1, Sources.list(c).size());
        // Switched on again, it starts afresh: the family's taken in first, nothing of this device's lost.
        LiveSync.setOn(c, LiveSync.PLAYLISTS, true);
        assertTrue(LiveSync.apply(c, true));
        LiveSync.export(c);
        assertEquals(2, Sources.list(c).size());
        assertNotNull(SyncStore.get(c, LiveSync.PLAYLIST, LiveSync.keyOf(XTREAM)));
    }
}
