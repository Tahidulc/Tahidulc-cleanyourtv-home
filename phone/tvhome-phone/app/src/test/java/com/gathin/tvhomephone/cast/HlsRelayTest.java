package com.gathin.tvhomephone.cast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The phone's relay for casting: playlists' addresses sent through it, Xtream's HLS asked for, and a real round trip. */
public class HlsRelayTest {
    @Test public void everyAddressInAPlaylistGoesThroughTheRelay() {
        String master = "#EXTM3U\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud\",NAME=\"English\",URI=\"audio/eng.m3u8\"\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080\n"
                + "hd/index.m3u8?token=a b\n"
                + "\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=900000\n"
                + "https://cdn.example/sd/index.m3u8\n";
        String out = HlsRelay.rewrite(master, "http://provider.example:8080/live/u/p/42.m3u8", abs -> "R[" + abs + "]");
        assertTrue(out, out.contains("URI=\"R[http://provider.example:8080/live/u/p/audio/eng.m3u8]\""));
        assertTrue(out, out.contains("R[http://provider.example:8080/live/u/p/hd/index.m3u8?token=a b]"));
        assertTrue(out, out.contains("R[https://cdn.example/sd/index.m3u8]"));
        // Tags without addresses are left as they were.
        assertTrue(out, out.contains("#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080\n"));
        String media = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-KEY:METHOD=AES-128,URI=\"/keys/k1\"\n#EXTINF:6.0,\nseg1.ts\n#EXTINF:6.0,\n/abs/seg2.ts\n";
        String m = HlsRelay.rewrite(media, "http://h.example/hls/ch/index.m3u8", abs -> "R[" + abs + "]");
        assertTrue(m, m.contains("URI=\"R[http://h.example/keys/k1]\""));
        assertTrue(m, m.contains("R[http://h.example/hls/ch/seg1.ts]"));
        assertTrue(m, m.contains("R[http://h.example/abs/seg2.ts]"));
    }

    @Test public void anXtreamStreamIsAskedForAsHls() {
        assertEquals("http://p.example:8080/live/user/pass/1234.m3u8", HlsRelay.hlsFor("http://p.example:8080/live/user/pass/1234.ts"));
        assertEquals("http://p.example:8080/live/user/pass/1234.m3u8", HlsRelay.hlsFor("http://p.example:8080/user/pass/1234"));
        assertEquals("http://p.example/live/u/p/9.m3u8?x=1", HlsRelay.hlsFor("http://p.example/live/u/p/9.ts?x=1"));
        assertEquals("https://h.example/a/index.m3u8", HlsRelay.hlsFor("https://h.example/a/index.m3u8"));
        // A server's own folders aren't a login.
        assertNull(HlsRelay.hlsFor("http://h.example/hls/abc/1234"));
        assertNull(HlsRelay.hlsFor("http://h.example/stream.ts"));
        assertNull(HlsRelay.hlsFor(""));
    }

    @Test public void playlistsAreKnownByTypePathOrStart() {
        assertTrue(HlsRelay.isPlaylist("application/vnd.apple.mpegurl", "/x", ""));
        assertTrue(HlsRelay.isPlaylist("audio/x-mpegurl", "/x", ""));
        assertTrue(HlsRelay.isPlaylist("text/plain", "/a/index.m3u8", ""));
        assertTrue(HlsRelay.isPlaylist(null, "/a/b", "#EXTM3U"));
        assertFalse(HlsRelay.isPlaylist("video/mp2t", "/a/seg.ts", "G@"));
        assertEquals("seg-12.ts", HlsRelay.fileName("http://h/a/seg-12.ts?tok=1"));
        assertEquals("s", HlsRelay.fileName("http://h/"));
        // Signed with the channel's own secret: the same value signs the same, another secret differently.
        byte[] k1 = new byte[16], k2 = new byte[16];
        k2[0] = 1;
        assertEquals(HlsRelay.sign(k1, "abc"), HlsRelay.sign(k1, "abc"));
        assertFalse(HlsRelay.sign(k1, "abc").equals(HlsRelay.sign(k2, "abc")));
        assertEquals(12, HlsRelay.sign(k1, "abc").length());
    }

    @Test public void aChannelGoesThroughTheRelayWithCorsAndItsUserAgent() throws Exception {
        byte[] segment = new byte[200_000];
        for (int i = 0; i < segment.length; i++) segment[i] = (byte) (i * 31);
        Map<String, byte[]> files = new HashMap<>();
        files.put("/live/u/p/7.m3u8", "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nseg/1.ts\n".getBytes(StandardCharsets.UTF_8));
        files.put("/live/u/p/seg/1.ts", segment);
        try (Provider provider = new Provider(files)) {
            String base = "http://127.0.0.1:" + provider.port();
            HlsRelay relay = HlsRelay.on(InetAddress.getLoopbackAddress());
            String url = relay.publish(base + "/live/u/p/7.m3u8", base + "/live/u/p/7.ts", "FamilyBox/2", "", true);
            assertTrue(url, url.endsWith("/index.m3u8"));

            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            assertEquals(200, c.getResponseCode());
            assertEquals("*", c.getHeaderField("Access-Control-Allow-Origin"));
            String playlist = new String(read(c.getInputStream()), StandardCharsets.UTF_8);
            assertEquals("FamilyBox/2", provider.agents.get(0));
            String segLine = null;
            for (String l : playlist.split("\n")) if (l.startsWith("http")) segLine = l;
            assertTrue(playlist, segLine != null && segLine.startsWith(url.substring(0, url.indexOf("/c/"))) && segLine.endsWith("/1.ts"));

            HttpURLConnection s = (HttpURLConnection) new URL(segLine).openConnection();
            assertEquals(200, s.getResponseCode());
            assertEquals("*", s.getHeaderField("Access-Control-Allow-Origin"));
            assertArrayEquals(segment, read(s.getInputStream()));

            // An address it didn't hand out (its signature wrong) is refused.
            String forged = segLine.replaceFirst("/r/[0-9a-f]{12}/", "/r/000000000000/");
            HttpURLConnection f = (HttpURLConnection) new URL(forged).openConnection();
            assertEquals(404, f.getResponseCode());
        } finally {
            HlsRelay.stopAll();
        }
    }

    /** A provider standing in for the playlist's server: its files, and the User-Agent each request came with. */
    private static final class Provider implements AutoCloseable {
        final List<String> agents = Collections.synchronizedList(new java.util.ArrayList<>());
        private final ServerSocket server;

        Provider(Map<String, byte[]> files) throws Exception {
            server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
            Thread t = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
                        String path = in.readLine().split(" ")[1];
                        String line;
                        while ((line = in.readLine()) != null && !line.isEmpty()) {
                            if (line.regionMatches(true, 0, "User-Agent:", 0, 11)) agents.add(line.substring(11).trim());
                        }
                        byte[] body = files.get(path);
                        OutputStream o = s.getOutputStream();
                        String head = body == null ? "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                : "HTTP/1.1 200 OK\r\nContent-Type: " + (path.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t")
                                + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
                        o.write(head.getBytes(StandardCharsets.ISO_8859_1));
                        if (body != null) o.write(body);
                        o.flush();
                    } catch (Exception e) {
                        // closed
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }

        int port() {
            return server.getLocalPort();
        }

        @Override public void close() throws Exception {
            server.close();
        }
    }

    private static byte[] read(InputStream in) throws Exception {
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }
}
