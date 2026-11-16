package com.gathin.tvhomephone.cast;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.gathin.tvhomephone.live.Channel;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Homefield TV on the family's TVs, as a place to send a channel: found by its announcement on the Wi-Fi
 * ("_homefieldtv._tcp", its name in the TXT record) or, where the announcement doesn't reach the phone, at its usual
 * port on a paired TV's address; checked with GET /hello; given the channel with POST /play. The TV plays it only from
 * its own playlists (by the stream's id, then the guide's id, then the name), so nothing it can't play is ever sent to
 * play. The stream's address goes with it for that match; it is never logged.
 */
public final class HomefieldLink {
    private HomefieldLink() {}

    static final String TYPE = "_homefieldtv._tcp";
    /** Where Homefield TV listens when it can (the TV app's side uses it unless taken). */
    static final int DEFAULT_PORT = 47830;
    private static final Pattern STREAM_ID = Pattern.compile("/([0-9]+)(\\.[A-Za-z0-9]{2,4})?(\\?.*)?$");
    private static final ExecutorService IO = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /**
     * Looks for about ms: the announcement, and the paired TVs' addresses at the usual port. Each found once, on the main
     * thread; done after.
     */
    @SuppressWarnings("deprecation")
    static void find(Context c, List<String> pairedHosts, long ms, Consumer<CastDevice> found, Runnable done) {
        Context a = c.getApplicationContext();
        java.util.Set<String> seen = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        Consumer<CastDevice> once = d -> MAIN.post(() -> {
            if (seen.add(d.id)) found.accept(d);
        });
        NsdManager nsd = a.getSystemService(NsdManager.class);
        WifiManager wifi = a.getSystemService(WifiManager.class);
        WifiManager.MulticastLock lock = wifi == null ? null : wifi.createMulticastLock("homefield-cast");
        if (lock != null) lock.acquire();
        ArrayDeque<NsdServiceInfo> queue = new ArrayDeque<>();
        boolean[] busy = {false};
        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override public void onServiceFound(NsdServiceInfo info) {
                MAIN.post(() -> {
                    queue.add(info);
                    resolveNext(nsd, queue, busy, once);
                });
            }
            @Override public void onDiscoveryStarted(String t) { }
            @Override public void onDiscoveryStopped(String t) { }
            @Override public void onServiceLost(NsdServiceInfo i) { }
            @Override public void onStartDiscoveryFailed(String t, int e) { }
            @Override public void onStopDiscoveryFailed(String t, int e) { }
        };
        boolean started = false;
        try {
            if (nsd != null) {
                nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener);
                started = true;
            }
        } catch (RuntimeException ignored) {
            // the paired TVs are still asked below
        }
        for (String host : pairedHosts) {
            IO.execute(() -> {
                String name = hello(host, DEFAULT_PORT, 2_500);
                if (name != null) once.accept(CastDevice.homefield(name, host, DEFAULT_PORT));
            });
        }
        boolean on = started;
        MAIN.postDelayed(() -> {
            if (on) {
                try {
                    nsd.stopServiceDiscovery(listener);
                } catch (RuntimeException ignored) {
                    // already stopped
                }
            }
            if (lock != null && lock.isHeld()) lock.release();
            if (done != null) done.run();
        }, ms);
    }

    @SuppressWarnings("deprecation")
    private static void resolveNext(NsdManager nsd, ArrayDeque<NsdServiceInfo> queue, boolean[] busy, Consumer<CastDevice> cb) {
        if (busy[0] || queue.isEmpty()) return;
        NsdServiceInfo info = queue.poll();
        busy[0] = true;
        nsd.resolveService(info, new NsdManager.ResolveListener() {
            int tries;
            @Override public void onResolveFailed(NsdServiceInfo i, int e) {
                if (++tries <= 2) MAIN.postDelayed(() -> nsd.resolveService(info, this), 500);
                else MAIN.post(() -> { busy[0] = false; resolveNext(nsd, queue, busy, cb); });
            }
            @Override public void onServiceResolved(NsdServiceInfo i) {
                String host = i.getHost() == null ? null : i.getHost().getHostAddress();
                int port = i.getPort();
                IO.execute(() -> {
                    String name = host == null ? null : hello(host, port, 3_000);
                    if (name != null) cb.accept(CastDevice.homefield(name, host, port));
                });
                MAIN.post(() -> { busy[0] = false; resolveNext(nsd, queue, busy, cb); });
            }
        });
    }

    /** The TV's name when Homefield TV answers there; null otherwise. Blocking. */
    static String hello(String host, int port, int timeoutMs) {
        try {
            return helloName(request(host, port, "GET", "/hello", null, timeoutMs));
        } catch (IOException e) {
            return null;
        }
    }

    /** The channel sent to play: what the TV says is playing, or why it couldn't. Blocking. */
    static String play(CastDevice tv, Channel ch, String from) throws IOException {
        try {
            JSONObject r = new JSONObject(request(tv.host, tv.port, "POST", "/play", body(ch, from).toString(), 8_000));
            if (!r.optBoolean("ok")) throw new IOException("not found");
            String playing = r.optString("playing");
            return playing.isEmpty() ? ch.name : playing;
        } catch (JSONException e) {
            throw new IOException("unreadable answer");
        }
    }

    /** Stop playing (a TV that doesn't know /stop just carries on). Blocking. */
    static void stop(CastDevice tv) {
        try {
            request(tv.host, tv.port, "POST", "/stop", "{\"v\":1}", 4_000);
        } catch (IOException ignored) {
            // the TV plays on
        }
    }

    private static String request(String host, int port, String method, String path, String body, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL("http", host, port, path).openConnection();
        try {
            c.setConnectTimeout(Math.min(timeoutMs, 4_000));
            c.setReadTimeout(timeoutMs);
            c.setRequestMethod(method);
            c.setRequestProperty("Accept", "application/json");
            if (body != null) {
                byte[] b = body.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setFixedLengthStreamingMode(b.length);
                try (OutputStream o = c.getOutputStream()) {
                    o.write(b);
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : read(in);
            if (code == 404 && "/play".equals(path)) return text.isEmpty() ? "{\"ok\":false}" : text;
            if (code >= 400) throw new IOException("TV said " + code);
            return text;
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = i.read(buf)) > 0 && b.size() < 64 * 1024) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }

    // ---- pure ------------------------------------------------------------------------------------------------------

    /** Pure: /play's body for a channel (version 1 of the protocol). */
    static JSONObject body(Channel ch, String from) {
        JSONObject o = new JSONObject();
        try {
            o.put("v", 1).put("kind", "live").put("name", ch.name).put("tvgId", ch.tvgId).put("streamId", streamId(ch.url))
                    .put("url", ch.url).put("userAgent", ch.userAgent).put("referrer", ch.referrer)
                    .put("from", from == null ? "" : from);
        } catch (JSONException ignored) {
            // plain values
        }
        return o;
    }

    /** Pure: a stream's id, the number its address ends with (an Xtream Codes stream: …/1234.ts); "" for none. */
    static String streamId(String url) {
        if (url == null) return "";
        Matcher m = STREAM_ID.matcher(url);
        return m.find() ? m.group(1) : "";
    }

    /** Pure: the TV's name from /hello, when it is Homefield TV answering; null otherwise. */
    static String helloName(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!"homefieldtv".equals(o.optString("app"))) return null;
            String name = o.optString("name").trim();
            return name.isEmpty() ? "TV" : name;
        } catch (JSONException | NullPointerException e) {
            return null;
        }
    }
}
