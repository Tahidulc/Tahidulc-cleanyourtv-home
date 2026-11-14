package com.gathin.tvhomephone.update;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Homefield's update fetched from a stand-in TV (its link answering as PhoneShelf does): found with the pairing's
 * token only, fetched whole, and a download that broke off picked up where it stopped.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppUpdateFetchTest {
    private static final String TOKEN = "t0k3n";
    private ServerSocket server;
    private final byte[] apk = new byte[3 * 1024 * 1024 + 123];
    private final List<String> ranges = new CopyOnWriteArrayList<>();
    private volatile int cutOnce;   // bytes after which the next whole answer breaks off (0: none)
    private JSONObject update;
    private Context c;

    private static String sha(byte[] b) throws Exception {
        return AppUpdate.hex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    @Before public void tv() throws Exception {
        new Random(7).nextBytes(apk);
        c = RuntimeEnvironment.getApplication();
        update = new JSONObject().put("code", 99999).put("name", "9.99").put("notes", "Something new")
                .put("file", "Homefield-9.99.apk").put("size", apk.length).put("sha256", sha(apk))
                .put("patches", new JSONArray());
        server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    answer(s);
                } catch (IOException ignored) {
                    // the next
                }
            }
        }, "stand-in TV");
        t.setDaemon(true);
        t.start();
        JSONObject tv = new JSONObject().put("name", "Bedroom TV").put("host", "127.0.0.1")
                .put("port", server.getLocalPort()).put("token", TOKEN);
        c.getSharedPreferences("tvs", Context.MODE_PRIVATE).edit().putString("list", new JSONArray().put(tv).toString()).commit();
    }

    @After public void off() throws Exception {
        server.close();
        AppUpdate.prefs(c).edit().clear().commit();
        idle();
    }

    /** The updater left as at start (its state is the app's, kept between tests). */
    static void idle() throws Exception {
        java.lang.reflect.Field stage = AppUpdate.class.getDeclaredField("stage");
        stage.setAccessible(true);
        stage.set(null, AppUpdate.Stage.IDLE);
        java.lang.reflect.Field problem = AppUpdate.class.getDeclaredField("problem");
        problem.setAccessible(true);
        problem.set(null, null);
    }

    private static void head(OutputStream o, String status, long length, String extra) throws IOException {
        o.write(("HTTP/1.1 " + status + "\r\nContent-Length: " + length + "\r\n" + extra + "Connection: close\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    /** As PhoneShelf answers: the token, update.json, the file whole or from where a Range ("bytes=N-") starts. */
    private void answer(Socket s) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        String[] request = String.valueOf(in.readLine()).split(" ");
        Map<String, String> h = new HashMap<>();
        for (String line; (line = in.readLine()) != null && !line.isEmpty(); ) {
            int colon = line.indexOf(':');
            if (colon > 0) h.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
        }
        OutputStream o = s.getOutputStream();
        String path = request.length > 1 ? request[1] : "";
        if (!TOKEN.equals(h.get("x-token"))) {
            head(o, "401 Unauthorized", 0, "");
            return;
        }
        if (path.equals("/phone/update.json")) {
            byte[] b = update.toString().getBytes(StandardCharsets.UTF_8);
            head(o, "200 OK", b.length, "");
            o.write(b);
            return;
        }
        if (!path.equals("/phone/Homefield-9.99.apk")) {
            head(o, "404 Not Found", 0, "");
            return;
        }
        String range = h.get("range");
        long from = 0;
        if (range != null) {
            ranges.add(range);
            from = Long.parseLong(range.substring("bytes=".length(), range.length() - 1));
        }
        if (from > 0) head(o, "206 Partial Content", apk.length - from, "Content-Range: bytes " + from + "-" + (apk.length - 1) + "/" + apk.length + "\r\n");
        else head(o, "200 OK", apk.length, "");
        int cut = cutOnce;
        if (cut > 0 && from == 0) {
            // The connection dropped half way.
            cutOnce = 0;
            o.write(apk, 0, cut);
            o.flush();
            return;
        }
        o.write(apk, (int) from, (int) (apk.length - from));
        o.flush();
    }

    @Test public void foundWithThePairingsTokenAndFetchedWhole() throws Exception {
        AppUpdate.Look l = AppUpdate.look(c);
        assertEquals(1, l.asked);
        assertEquals(1, l.answered);
        assertNotNull(l.offer);
        assertEquals("Bedroom TV", l.offer.tv);
        AppUpdate.keep(c, l);
        AppUpdate.Offer o = AppUpdate.ready(c);
        assertNotNull(o);
        File f = AppUpdate.fetch(c, o, o.file, 0f, 1f);
        assertArrayEquals(apk, Files.readAllBytes(f.toPath()));
        assertTrue(ranges.isEmpty());
    }

    @Test public void aDownloadThatBrokeOffIsPickedUpWhereItStopped() throws Exception {
        AppUpdate.Look l = AppUpdate.look(c);
        AppUpdate.keep(c, l);
        AppUpdate.Offer o = AppUpdate.ready(c);
        cutOnce = 1024 * 1024;
        File f = AppUpdate.fetch(c, o, o.file, 0f, 1f);
        assertArrayEquals(apk, Files.readAllBytes(f.toPath()));
        assertEquals(1, ranges.size());
        assertTrue(ranges.get(0), ranges.get(0).startsWith("bytes=") && !ranges.get(0).equals("bytes=0-"));
    }

    @Test public void aTvThatNoLongerKnowsThePhoneOffersNothing() throws Exception {
        c.getSharedPreferences("tvs", Context.MODE_PRIVATE).edit().putString("list", new JSONArray()
                .put(new JSONObject().put("name", "Bedroom TV").put("host", "127.0.0.1")
                        .put("port", server.getLocalPort()).put("token", "old")).toString()).commit();
        AppUpdate.Look l = AppUpdate.look(c);
        assertEquals(1, l.answered);
        assertNull(l.offer);
    }

    @Test public void gonePairingIsSaidPlainly() throws Exception {
        AppUpdate.Look l = AppUpdate.look(c);
        AppUpdate.keep(c, l);
        AppUpdate.Offer o = AppUpdate.ready(c);
        c.getSharedPreferences("tvs", Context.MODE_PRIVATE).edit().putString("list", "[]").commit();
        try {
            AppUpdate.fetch(c, o, o.file, 0f, 1f);
            fail();
        } catch (AppUpdate.Refused e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no longer paired with Bedroom TV"));
        }
    }
}
