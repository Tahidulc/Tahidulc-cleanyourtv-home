package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Delivery updates from the phone (the Delivery to TV app, which reads the delivery apps' notifications there): a
 * small HTTP endpoint on the home network, announced over mDNS as "_tvhome._tcp" so the phone finds the TV.
 *
 * <p>A phone pairs once: it asks, the TV shows a four-digit code, the phone sends it back and gets a token, which
 * every update then carries. Only addresses on the home network or a tailnet are answered.
 */
final class DeliveryLink {
    interface Sink {
        /** Show the pairing code for a phone. Main thread not guaranteed. */
        void pairing(String phone, String code);

        /** A delivery update: {app, status, stage (0..4), eta, icon (base64 PNG)}. Main thread not guaranteed. */
        void delivery(JSONObject update);
    }

    static final int PORT = 47820;
    private static final String TYPE = "_tvhome._tcp";
    private static final long CODE_MS = 2 * 60_000L;
    private static final int MAX_BODY = 192 * 1024;

    private final Context context;
    private final Sink sink;
    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();
    private ServerSocket server;
    private Thread thread;
    private NsdManager.RegistrationListener registration;
    private String code;
    private long codeAt;
    private int attempts;

    DeliveryLink(Context context, Sink sink) {
        this.context = context.getApplicationContext();
        this.sink = sink;
        prefs = this.context.getSharedPreferences("delivery_link", Context.MODE_PRIVATE);
    }

    void start() {
        if (thread != null) return;
        thread = new Thread(this::serve, "delivery-link");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    void stop() {
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        server = null;
        thread = null;
        unregister();
    }

    private void serve() {
        try {
            server = new ServerSocket(PORT);
        } catch (Exception e) {
            return;   // the port is taken: no delivery updates on this TV
        }
        register();
        while (server != null && !server.isClosed()) {
            try (Socket s = server.accept()) {
                s.setSoTimeout(5_000);
                if (!homeAddress(s.getInetAddress())) continue;
                handle(s);
            } catch (Exception ignored) {
                // one bad request; keep listening
            }
        }
    }

    /** The home network (private ranges) or a tailnet (100.64.0.0/10): nothing from anywhere else. */
    private static boolean homeAddress(InetAddress a) {
        if (a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isLoopbackAddress()) return true;
        byte[] b = a.getAddress();
        if (b.length == 4) return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;
        return (b[0] & 0xFE) == 0xFC;   // IPv6 unique local (Tailscale's fd7a:...)
    }

    private void handle(Socket s) throws Exception {
        InputStream in = new BufferedInputStream(s.getInputStream());
        String request = line(in);
        if (request == null) return;
        String[] parts = request.split(" ");
        String method = parts.length > 0 ? parts[0] : "", path = parts.length > 1 ? parts[1] : "";
        int length = 0;
        String token = null;
        for (String h; (h = line(in)) != null && !h.isEmpty(); ) {
            int colon = h.indexOf(':');
            if (colon < 0) continue;
            String name = h.substring(0, colon).trim().toLowerCase(Locale.ROOT), value = h.substring(colon + 1).trim();
            if (name.equals("content-length")) length = Integer.parseInt(value);
            if (name.equals("x-token")) token = value;
        }
        if (length < 0 || length > MAX_BODY) { reply(s, 413, "{\"error\":\"too big\"}"); return; }
        byte[] body = new byte[length];
        for (int read = 0; read < length; ) {
            int n = in.read(body, read, length - read);
            if (n < 0) break;
            read += n;
        }
        JSONObject json = length > 0 ? new JSONObject(new String(body, StandardCharsets.UTF_8)) : new JSONObject();
        if (!method.equals("POST")) { reply(s, 405, "{\"error\":\"POST only\"}"); return; }
        switch (path) {
            case "/pair": {
                // A new code, shown on the TV for two minutes.
                code = String.format(Locale.ROOT, "%04d", random.nextInt(10_000));
                codeAt = System.currentTimeMillis();
                attempts = 0;
                sink.pairing(json.optString("phone", "A phone"), code);
                reply(s, 200, "{\"ok\":true}");
                return;
            }
            case "/pair/confirm": {
                boolean live = code != null && System.currentTimeMillis() - codeAt < CODE_MS && attempts++ < 5;
                if (!live || !code.equals(json.optString("code"))) { reply(s, 403, "{\"error\":\"wrong code\"}"); return; }
                code = null;
                byte[] t = new byte[24];
                random.nextBytes(t);
                StringBuilder hex = new StringBuilder();
                for (byte x : t) hex.append(String.format(Locale.ROOT, "%02x", x));
                Set<String> tokens = new HashSet<>(prefs.getStringSet("tokens", new HashSet<>()));
                tokens.add(hex.toString());
                prefs.edit().putStringSet("tokens", tokens).apply();
                reply(s, 200, new JSONObject().put("token", hex.toString()).put("tv", name()).toString());
                return;
            }
            case "/delivery": {
                if (token == null || !prefs.getStringSet("tokens", new HashSet<>()).contains(token)) {
                    reply(s, 401, "{\"error\":\"not paired\"}");
                    return;
                }
                sink.delivery(json);
                reply(s, 200, "{\"ok\":true}");
                return;
            }
            case "/unpair": {
                // A phone forgetting this TV: its token stops working.
                Set<String> tokens = new HashSet<>(prefs.getStringSet("tokens", new HashSet<>()));
                if (token != null && tokens.remove(token)) prefs.edit().putStringSet("tokens", tokens).apply();
                reply(s, 200, "{\"ok\":true}");
                return;
            }
            default:
                reply(s, 404, "{\"error\":\"unknown\"}");
        }
    }

    /** One line of the request, or null at its end (or for one far too long). */
    private static String line(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
            if (c != '\r') out.write(c);
            if (out.size() > 8192) return null;
        }
        if (c == -1 && out.size() == 0) return null;
        return out.toString("UTF-8");
    }

    private static void reply(Socket s, int status, String body) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        OutputStream out = s.getOutputStream();
        out.write(("HTTP/1.1 " + status + " OK\r\nContent-Type: application/json\r\nContent-Length: " + b.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }

    /** The TV's name as the phone shows it: the name set in its settings, else its model. */
    private String name() {
        String n = android.provider.Settings.Global.getString(context.getContentResolver(), "device_name");
        return n == null || n.trim().isEmpty() ? Build.MODEL : n.trim();
    }

    private void register() {
        NsdManager nsd = context.getSystemService(NsdManager.class);
        if (nsd == null) return;
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(name());
        info.setServiceType(TYPE);
        info.setPort(PORT);
        registration = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo i) { }
            @Override public void onRegistrationFailed(NsdServiceInfo i, int e) { }
            @Override public void onServiceUnregistered(NsdServiceInfo i) { }
            @Override public void onUnregistrationFailed(NsdServiceInfo i, int e) { }
        };
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration);
        } catch (Exception e) {
            registration = null;
        }
    }

    private void unregister() {
        NsdManager nsd = context.getSystemService(NsdManager.class);
        if (nsd == null || registration == null) return;
        try { nsd.unregisterService(registration); } catch (Exception ignored) { }
        registration = null;
    }
}
