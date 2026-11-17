package com.gathin.homefieldtv.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The token hand-off and a sync with a stand-in for the launcher on this TV: the token comes from the launcher's provider
 * (none when it refuses), goes only with POST /sync to 127.0.0.1, and a playlist from a phone arrives with its login and
 * is never typed in again. The host and login are invented; nothing of them shows in what Settings says.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TvSyncTest {
    private static final String TOKEN = "t0ken-for-this-tv";
    private static final Source XTREAM = new Source("xtream", "", "", "http://iptv.example.invalid:8080", "harbourfamily", "s3cret-Pass");
    static volatile int port;
    static volatile boolean refuse;
    private Context c;

    /** The launcher's provider as Homefield TV sees it. */
    public static final class Launcher extends ContentProvider {
        @Override public boolean onCreate() { return true; }

        @Override public Bundle call(String method, String arg, Bundle extras) {
            if (refuse) throw new SecurityException("not one of the family's own apps");
            Bundle b = new Bundle();
            b.putString("token", TOKEN);
            b.putInt("port", port);
            return b;
        }

        @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
        @Override public String getType(Uri u) { return null; }
        @Override public Uri insert(Uri u, ContentValues v) { return null; }
        @Override public int delete(Uri u, String s, String[] a) { return 0; }
        @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
    }

    @Before public void fresh() throws Exception {
        c = RuntimeEnvironment.getApplication();
        refuse = false;
        TvLiveSync.refresh = () -> { };
        // The channels' and films' singletons made again on this test's application (an earlier test's may be kept).
        for (Class<?> k : new Class<?>[]{com.gathin.homefieldtv.engine.Library.class, com.gathin.homefieldtv.engine.Catalog.class}) {
            java.lang.reflect.Field f = k.getDeclaredField("instance");
            f.setAccessible(true);
            f.set(null, null);
        }
        Sources.clear(c);
        for (String p : new String[]{"sync_store", "sync_live_sent", "sync_live_base", "sync_kinds", "sync_state", "sync_resume_tried",
                "live_tv", "resume"}) {
            c.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    @After public void done() {
        TvLiveSync.refresh = null;
    }

    @Test public void noLauncherNoToken() {
        assertNull(TvSync.token(c));
        TvSync.sync(c);
        assertEquals("The launcher on this TV isn't there to sync with", TvSync.status(c));
    }

    @Test public void theLauncherRefusingGivesNoToken() {
        Robolectric.setupContentProvider(Launcher.class, "com.gathin.tvhome.sync");
        refuse = true;
        assertNull(TvSync.token(c));
    }

    @Test public void aPlaylistFromAPhoneArrivesThroughTheLauncherOnThisTv() throws Exception {
        Robolectric.setupContentProvider(Launcher.class, "com.gathin.tvhome.sync");
        Bundle b = TvSync.token(c);
        assertNotNull(b);
        assertEquals(TOKEN, b.getString("token"));
        // This TV had its own playlist before; the phone's comes from the launcher.
        Sources.put(c, null, "Ours", new Source("m3u", "http://lists.example.invalid/family.m3u", "", "", "", ""));
        JSONObject phones = SyncItems.item(TvLiveSync.PLAYLIST, TvLiveSync.keyOf(XTREAM), TvLiveSync.playlistData("Family", XTREAM),
                System.currentTimeMillis() - 60_000L, false, "Phone");
        List<String[]> seen = new ArrayList<>();
        try (ServerSocket server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress())) {
            port = server.getLocalPort();
            Thread t = new Thread(() -> serve(server, seen, phones));
            t.start();
            TvSync.sync(c);
            t.join(10_000);
        }
        // Two calls: what it had (nothing yet), then, with the family's taken in, this TV's own.
        assertEquals(2, seen.size());
        for (String[] r : seen) {
            assertEquals("POST /sync", r[0]);
            assertEquals(TOKEN, r[1]);
        }
        assertEquals(0, new JSONObject(seen.get(0)[2]).getJSONArray("items").length());
        JSONArray second = new JSONObject(seen.get(1)[2]).getJSONArray("items");
        boolean ours = false;
        for (int i = 0; i < second.length(); i++) {
            JSONObject it = second.getJSONObject(i);
            assertFalse("not sent back", TvLiveSync.keyOf(XTREAM).equals(it.optString("key")));
            ours |= TvLiveSync.PLAYLIST.equals(it.optString("type"));
        }
        assertTrue(ours);
        assertTrue(SyncItems.items(c, "mylist").isEmpty());
        List<Sources.Entry> here = Sources.list(c);
        assertEquals(2, here.size());
        assertEquals("Family", here.get(1).name);
        assertEquals(XTREAM.pass, here.get(1).source.pass);
        // What Settings says names neither the login, the address nor the token.
        String said = TvSync.status(c);
        assertTrue(said, said.startsWith("Synced"));
        for (String secret : new String[]{"harbour", "s3cret", "example", TOKEN}) assertFalse(said.contains(secret));
    }

    @Test public void removeEverythingClearsThisTvOnlyAndSwitchesPlaylistsOff() {
        Sources.put(c, null, "Family", XTREAM);
        com.gathin.homefieldtv.engine.Library.get(c).setPicks(java.util.Arrays.asList("x:42"), java.util.Arrays.asList("x:42"));
        com.gathin.homefieldtv.engine.Resume.save(c, new com.gathin.homefieldtv.engine.Resume.Point("p0|m|12", "", "Harbour Lights", "", "",
                600_000L, 3_000_000L, System.currentTimeMillis()));
        TvLiveSync.apply(c, true);
        TvLiveSync.export(c);
        String key = TvLiveSync.keyOf(XTREAM);
        assertNotNull(SyncItems.get(c, TvLiveSync.PLAYLIST, key));
        assertNotNull(SyncItems.get(c, TvLiveSync.RESUME, "mv:harbour lights"));
        // Settings, Remove everything: what it clears, with the sync held still.
        TvLiveSync.removeEverything(c, () -> {
            com.gathin.homefieldtv.engine.Library.get(c).clear();
            com.gathin.homefieldtv.engine.Resume.clear(c);
        });
        assertFalse(TvLiveSync.on(c, TvLiveSync.PLAYLISTS));
        assertTrue(TvLiveSync.offByRemoveEverything(c));
        // The next syncs: nothing goes out as a removal, and the family's playlist doesn't come straight back.
        TvLiveSync.export(c);
        assertTrue(TvLiveSync.apply(c, true));
        TvLiveSync.export(c);
        for (String type : new String[]{TvLiveSync.PLAYLIST, TvLiveSync.LIVEFAV, TvLiveSync.LIVERECENT, TvLiveSync.RESUME}) {
            for (JSONObject it : SyncItems.items(c, type)) assertFalse(type + " " + it.optString("key"), it.optBoolean("deleted"));
        }
        assertNotNull(SyncItems.get(c, TvLiveSync.PLAYLIST, key));
        assertNotNull(SyncItems.get(c, TvLiveSync.RESUME, "mv:harbour lights"));
        assertTrue(Sources.list(c).isEmpty());
        // Switched on again: the family's playlist is taken in, login and all, as on a new TV.
        TvLiveSync.setOn(c, TvLiveSync.PLAYLISTS, true);
        assertFalse(TvLiveSync.offByRemoveEverything(c));
        assertTrue(TvLiveSync.apply(c, true));
        assertEquals(1, Sources.list(c).size());
        assertEquals(XTREAM.pass, Sources.list(c).get(0).source.pass);
        assertEquals(java.util.Collections.singletonList("x:42"), com.gathin.homefieldtv.engine.Library.get(c).favourites());
    }

    /** The launcher's /sync, twice: the request line, its token and its body noted; the phone's playlist the first time. */
    private static void serve(ServerSocket server, List<String[]> seen, JSONObject phones) {
        for (int n = 0; n < 2; n++) {
            try (Socket s = server.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String line = in.readLine(), token = "";
                int length = 0;
                for (String h; (h = in.readLine()) != null && !h.isEmpty(); ) {
                    String lower = h.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("x-token:")) token = h.substring(8).trim();
                    if (lower.startsWith("content-length:")) length = Integer.parseInt(h.substring(15).trim());
                }
                char[] body = new char[length];
                for (int read = 0; read < length; ) {
                    int r = in.read(body, read, length - read);
                    if (r < 0) break;
                    read += r;
                }
                seen.add(new String[]{line.substring(0, line.lastIndexOf(' ')), token, new String(body)});
                // With it, one of the launcher's own kinds that Homefield TV has no use for (not kept).
                JSONArray items = n == 0 ? new JSONArray().put(phones).put(SyncItems.item("mylist", "tv:1399", new JSONObject(), 1L, false, "Phone"))
                        : new JSONArray();
                JSONObject reply = new JSONObject().put("now", 5000L + n).put("items", items);
                byte[] out = reply.toString().getBytes(StandardCharsets.UTF_8);
                OutputStream o = s.getOutputStream();
                o.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + out.length + "\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                o.write(out);
                o.flush();
            } catch (Exception e) {
                return;
            }
        }
    }
}
