package com.gathin.homefieldtv;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Names;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Homefield TV as somewhere the family's phone can send a channel (its Live TV's cast button), on the home network only
 * and only while the app is open or playing. It says it is here over NSD (_homefieldtv._tcp, with v=1 and the TV's
 * name) and answers on its own port: GET /hello with {"app":"homefieldtv","v":1,"name":"…"}; POST /play with the
 * channel ({"v":1,"kind":"live","name","tvgId","streamId",…,"from"}), played only when this TV's own playlists have it
 * (by its stream id, else its guide id, else its tidied name): {"ok":true,"playing":"…"}, else 404 {"ok":false}. The
 * phone's stream address and login are never used, and only private-network addresses are answered.
 */
final class Cast {
    private Cast() {}

    static final String TYPE = "_homefieldtv._tcp", EXTRA_KEY = "cast_key", EXTRA_FROM = "cast_from", EXTRA_AT = "cast_at";
    /** A channel sent longer ago than this is not played (the app started again later from an old intent). */
    static final long FRESH_MS = 30_000;
    private static final int MAX_HEAD = 8192, MAX_BODY = 16384;
    private static final long STOP_AFTER_MS = 4000;

    /** The application's context (never an activity's), for the TV's name, the playlists and bringing the app forward. */
    @SuppressLint("StaticFieldLeak")
    private static Context app;
    private static int holds;
    private static ServerSocket socket;
    private static NsdManager.RegistrationListener registered;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Runnable stop = Cast::stop;

    static void hello(Context c) {
        app = c.getApplicationContext();
    }

    /** A screen of the app is on: the receiver runs (a few seconds' grace between screens). */
    static synchronized void hold(Context c) {
        app = c.getApplicationContext();
        main.removeCallbacks(stop);
        if (holds++ == 0 && socket == null && !Exec.inline) start();
    }

    static synchronized void release() {
        if (holds > 0 && --holds == 0) main.postDelayed(stop, STOP_AFTER_MS);
    }

    private static synchronized void start() {
        try {
            ServerSocket s = new ServerSocket(0);
            socket = s;
            Thread t = new Thread(() -> serve(s), "cast");
            t.setDaemon(true);
            t.start();
            advertise(s.getLocalPort());
        } catch (IOException | RuntimeException e) {
            socket = null;
        }
    }

    private static synchronized void stop() {
        if (holds > 0) return;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
            // closed
        }
        socket = null;
        if (registered != null && app != null) {
            try {
                ((NsdManager) app.getSystemService(Context.NSD_SERVICE)).unregisterService(registered);
            } catch (RuntimeException ignored) {
                // gone
            }
        }
        registered = null;
    }

    /** The TV's name, as Android's settings give it. */
    static String tvName(Context c) {
        String n = null;
        try {
            n = android.provider.Settings.Global.getString(c.getContentResolver(), android.provider.Settings.Global.DEVICE_NAME);
        } catch (RuntimeException ignored) {
            // the model instead
        }
        return n == null || n.trim().isEmpty() ? Build.MODEL : n.trim();
    }

    private static void advertise(int port) {
        NsdManager nsd = (NsdManager) app.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) return;
        String name = clip(tvName(app), 60);
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(name);
        info.setServiceType(TYPE);
        info.setPort(port);
        info.setAttribute("v", "1");
        info.setAttribute("name", name);
        NsdManager.RegistrationListener l = new NsdManager.RegistrationListener() {
            @Override public void onRegistrationFailed(NsdServiceInfo s, int code) {}

            @Override public void onUnregistrationFailed(NsdServiceInfo s, int code) {}

            @Override public void onServiceRegistered(NsdServiceInfo s) {}

            @Override public void onServiceUnregistered(NsdServiceInfo s) {}
        };
        registered = l;
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l);
    }

    private static void serve(ServerSocket s) {
        while (!s.isClosed()) {
            try (Socket c = s.accept()) {
                c.setSoTimeout(3000);
                if (!isPrivate(c.getInetAddress().getAddress())) continue;
                handle(c.getInputStream(), c.getOutputStream());
            } catch (IOException | RuntimeException e) {
                // the next one
            }
        }
    }

    private static void handle(InputStream in, OutputStream out) throws IOException {
        String head = readHead(in);
        if (head == null) return;
        String[] first = head.split("\r\n", 2)[0].split(" ");
        String method = first.length > 0 ? first[0] : "", path = first.length > 1 ? first[1] : "";
        if ("GET".equals(method) && "/hello".equals(path)) {
            reply(out, 200, "{\"app\":\"homefieldtv\",\"v\":1,\"name\":" + quote(tvName(app)) + "}");
            return;
        }
        if (!"POST".equals(method) || !"/play".equals(path)) {
            reply(out, 404, "{\"ok\":false}");
            return;
        }
        int length = contentLength(head);
        if (length <= 0 || length > MAX_BODY) {
            reply(out, 400, "{\"ok\":false}");
            return;
        }
        byte[] body = new byte[length];
        int n = 0;
        while (n < length) {
            int r = in.read(body, n, length - n);
            if (r < 0) break;
            n += r;
        }
        String playing = play(new String(body, 0, n, StandardCharsets.UTF_8));
        if (playing == null) reply(out, 404, "{\"ok\":false}");
        else reply(out, 200, "{\"ok\":true,\"playing\":" + quote(playing) + "}");
    }

    /** The phone's channel played when this TV's playlists have it; its name, or null. */
    private static String play(String json) {
        String streamId, tvgId, name, from;
        try {
            JSONObject o = new JSONObject(json);
            if (o.optInt("v", 0) != 1 || !"live".equals(o.optString("kind"))) return null;
            streamId = o.optString("streamId");
            tvgId = o.optString("tvgId");
            name = o.optString("name");
            from = o.optString("from");
        } catch (JSONException e) {
            return null;
        }
        Channel c = match(Library.get(app).all(), streamId, tvgId, name);
        if (c == null) return null;
        // To the front and playing, on the main thread; answered once it is on its way.
        CountDownLatch done = new CountDownLatch(1);
        main.post(() -> {
            Intent i = new Intent(app, TvActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_KEY, c.key).putExtra(EXTRA_FROM, clip(from, 40)).putExtra(EXTRA_AT, System.currentTimeMillis());
            try {
                app.startActivity(i);
            } catch (RuntimeException ignored) {
                // not allowed now
            }
            done.countDown();
        });
        try {
            done.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return c.name;
    }

    // ---- pure -------------------------------------------------------------------------------------------------------

    /** Pure: the playlist's channel the phone means: by its stream id, else its guide id, else its tidied name; or null. */
    static Channel match(List<Channel> all, String streamId, String tvgId, String name) {
        if (all == null) return null;
        String sid = streamId == null ? "" : streamId.trim();
        if (!sid.isEmpty()) for (Channel c : all) if (sid.equals(streamIdOf(c.key))) return c;
        String tid = tvgId == null ? "" : tvgId.trim();
        if (!tid.isEmpty()) for (Channel c : all) if (tid.equalsIgnoreCase(c.tvgId)) return c;
        String k = name == null ? "" : Names.key(name);
        if (!k.isEmpty()) for (Channel c : all) if (k.equals(Names.key(c.name))) return c;
        return null;
    }

    /** Pure: an Xtream channel's stream id from its key ("x:1234", "p1:x:1234" → "1234"), "" for any other. */
    static String streamIdOf(String key) {
        if (key == null) return "";
        int i = key.lastIndexOf("x:");
        if (i < 0 || (i > 0 && key.charAt(i - 1) != ':') || i + 2 >= key.length()) return "";
        for (int k = i + 2; k < key.length(); k++) if (key.charAt(k) < '0' || key.charAt(k) > '9') return "";
        return key.substring(i + 2);
    }

    /** Pure: an address on a private network (10/8, 172.16/12, 192.168/16, link-local, fc00::/7, fe80::/10, loopback). */
    static boolean isPrivate(byte[] a) {
        if (a == null) return false;
        if (a.length == 4) {
            int b0 = a[0] & 0xFF, b1 = a[1] & 0xFF;
            return b0 == 10 || b0 == 127 || (b0 == 172 && b1 >= 16 && b1 <= 31) || (b0 == 192 && b1 == 168) || (b0 == 169 && b1 == 254);
        }
        if (a.length == 16) {
            boolean loop = true, mapped = (a[10] & 0xFF) == 0xFF && (a[11] & 0xFF) == 0xFF;
            for (int i = 0; i < 15; i++) loop &= a[i] == 0;
            if (loop && a[15] == 1) return true;
            for (int i = 0; i < 10; i++) mapped &= a[i] == 0;
            // An IPv4 address written as IPv6 (::ffff:192.168.1.20): as that address.
            if (mapped) return isPrivate(new byte[]{a[12], a[13], a[14], a[15]});
            int b0 = a[0] & 0xFF, b1 = a[1] & 0xFF;
            return (b0 & 0xFE) == 0xFC || (b0 == 0xFE && (b1 & 0xC0) == 0x80);
        }
        return false;
    }

    /** Pure: a JSON string. */
    static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : (s == null ? "" : s).toCharArray()) {
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 0x20) b.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
            else b.append(ch);
        }
        return b.append('"').toString();
    }

    /** Pure: the Content-Length a request's head gives, -1 for none. */
    static int contentLength(String head) {
        for (String line : head.split("\r\n")) {
            int c = line.indexOf(':');
            if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase("content-length")) {
                try {
                    return Integer.parseInt(line.substring(c + 1).trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.replaceAll("\\p{Cc}", "").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int last4 = 0;
        while (b.size() < MAX_HEAD) {
            int ch = in.read();
            if (ch < 0) return null;
            b.write(ch);
            last4 = (last4 << 8) | ch;
            if (last4 == 0x0D0A0D0A) return b.toString(StandardCharsets.ISO_8859_1.name());
        }
        return null;
    }

    private static void reply(OutputStream out, int code, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "OK" : code == 404 ? "Not Found" : "Bad Request";
        String head = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "
                + body.length + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }
}
