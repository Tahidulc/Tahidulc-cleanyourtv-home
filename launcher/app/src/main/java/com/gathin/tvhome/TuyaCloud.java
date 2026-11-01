package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The lights from Tuya's cloud (Tuya's OpenAPI, the official one): which there are, whether each is on and how bright,
 * and switching or dimming one. It needs a Tuya IoT cloud project with the Tuya Smart app account linked to it, its
 * Access ID and Access Secret in the TV's own config.json ("tuya_access_id", "tuya_access_secret", and "tuya_region":
 * eu, us, cn, in; eu when not given). They are read from there each time and never written anywhere else or logged.
 * Optional "tuya_lights": the lights to show, by name or device id, in that order (else every light on the account).
 * Every call is off the main thread.
 */
final class TuyaCloud {
    /** One light: a bulb, a strip or a dimmer. level is 1-100, or -1 for one that only switches. */
    static final class Light {
        String id, name, category, switchCode, brightCode;
        boolean online = true, on;
        int level = -1, min = 10, max = 1000;
    }

    private static final String[] SWITCHES = {"switch_led", "switch_led_1", "switch_1", "switch"};
    private static final String[] BRIGHTS = {"bright_value_v2", "bright_value", "bright_value_1", "bright_value_v2_1"};
    /** Tuya's categories for lights: bulbs, strips, ceiling and ambience lights, dimmers and dimmer switches. */
    private static final String[] LIGHTS = {"dj", "dd", "dc", "xdd", "fwd", "tgq", "tgkg", "fsd", "gyd", "tyndj", "sxd"};

    private static String token;
    private static long tokenUntil;
    private static final Map<String, int[]> ranges = new HashMap<>();

    private TuyaCloud() { }

    static boolean configured(Context c) {
        return !Config.themeString(c, "tuya_access_id", "").trim().isEmpty()
                && !Config.themeString(c, "tuya_access_secret", "").trim().isEmpty();
    }

    /** The account's lights with their state now; an exception when Tuya cannot be reached or refuses. */
    static List<Light> lights(Context c) throws Exception {
        JSONObject r = call(c, "GET", "/v1.0/iot-01/associated-users/devices?size=100", null);
        JSONArray devices = r.optJSONObject("result") == null ? null : r.getJSONObject("result").optJSONArray("devices");
        List<Light> out = new ArrayList<>();
        for (int i = 0; devices != null && i < devices.length(); i++) {
            JSONObject d = devices.getJSONObject(i);
            Light l = new Light();
            l.id = d.optString("id");
            l.name = d.optString("name", "Light").trim();
            l.category = d.optString("category");
            l.online = d.optBoolean("online", true);
            JSONArray status = d.optJSONArray("status");
            if (status == null) status = status(c, l.id);
            read(l, status);
            boolean light = false;
            for (String k : LIGHTS) light |= k.equals(l.category);
            // A plain switch only when it is called a light or a lamp.
            if (!light && l.switchCode != null && ("kg".equals(l.category) || "cz".equals(l.category))) {
                String n = l.name.toLowerCase(Locale.ROOT);
                light = n.contains("light") || n.contains("lamp") || n.contains("dimmer");
            }
            if (!light || l.switchCode == null) continue;
            if (l.brightCode != null) range(c, l);
            out.add(l);
        }
        return pick(c, out);
    }

    /** The theme's "tuya_lights" (names or ids) in their order; without it every light, by name. */
    private static List<Light> pick(Context c, List<Light> all) {
        List<String> want = Config.themeStrings(c, "tuya_lights", new ArrayList<>());
        if (want.isEmpty()) {
            all.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            return all;
        }
        List<Light> out = new ArrayList<>();
        for (String w : want) {
            for (Light l : all) {
                if (!out.contains(l) && (w.equalsIgnoreCase(l.id) || w.equalsIgnoreCase(l.name))) out.add(l);
            }
        }
        return out;
    }

    /** One light's state again (after a change). */
    static void refresh(Context c, Light l) throws Exception {
        read(l, status(c, l.id));
    }

    /** Switches it on or off. */
    static boolean power(Context c, Light l, boolean on) {
        try {
            JSONObject cmd = new JSONObject().put("code", l.switchCode).put("value", on);
            boolean ok = call(c, "POST", "/v1.0/devices/" + l.id + "/commands",
                    new JSONObject().put("commands", new JSONArray().put(cmd)).toString()).optBoolean("success");
            if (ok) l.on = on;
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    /** Sets its brightness (1-100%), switching it on as well when it is off. */
    static boolean level(Context c, Light l, int percent) {
        if (l.brightCode == null) return power(c, l, true);
        try {
            int value = Math.max(l.min, Math.min(l.max, Math.round(percent / 100f * l.max)));
            JSONArray cmds = new JSONArray();
            if (!l.on) cmds.put(new JSONObject().put("code", l.switchCode).put("value", true));
            cmds.put(new JSONObject().put("code", l.brightCode).put("value", value));
            boolean ok = call(c, "POST", "/v1.0/devices/" + l.id + "/commands", new JSONObject().put("commands", cmds).toString())
                    .optBoolean("success");
            if (ok) { l.on = true; l.level = percent; }
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- the state ------------------------------------------------------------------------------------------------

    private static JSONArray status(Context c, String id) throws Exception {
        JSONArray s = call(c, "GET", "/v1.0/devices/" + id + "/status", null).optJSONArray("result");
        return s == null ? new JSONArray() : s;
    }

    private static void read(Light l, JSONArray status) {
        Map<String, Object> v = new HashMap<>();
        for (int i = 0; status != null && i < status.length(); i++) {
            JSONObject x = status.optJSONObject(i);
            if (x != null) v.put(x.optString("code"), x.opt("value"));
        }
        for (String s : SWITCHES) if (v.get(s) instanceof Boolean) { l.switchCode = s; l.on = (Boolean) v.get(s); break; }
        for (String b : BRIGHTS) {
            if (!(v.get(b) instanceof Number)) continue;
            l.brightCode = b;
            int raw = ((Number) v.get(b)).intValue();
            if (l.max <= 0) l.max = 1000;
            l.level = Math.max(1, Math.min(100, Math.round(raw * 100f / l.max)));
            break;
        }
    }

    /** The brightness's range (10-1000 for most, 25-255 for older bulbs), from the light's own functions; kept. */
    private static void range(Context c, Light l) {
        int[] r = ranges.get(l.id + "|" + l.brightCode);
        if (r == null) {
            r = new int[]{10, 1000};
            try {
                JSONObject res = call(c, "GET", "/v1.0/devices/" + l.id + "/functions", null).optJSONObject("result");
                JSONArray fs = res == null ? null : res.optJSONArray("functions");
                for (int i = 0; fs != null && i < fs.length(); i++) {
                    JSONObject f = fs.getJSONObject(i);
                    if (!l.brightCode.equals(f.optString("code"))) continue;
                    JSONObject values = new JSONObject(f.optString("values", "{}"));
                    r = new int[]{values.optInt("min", 10), values.optInt("max", 1000)};
                }
            } catch (Exception ignored) {
            }
            ranges.put(l.id + "|" + l.brightCode, r);
        }
        int before = l.max;
        l.min = r[0];
        l.max = Math.max(r[0] + 1, r[1]);
        // The level was read against 1000: read again against the real top.
        if (l.level > 0 && before != l.max) l.level = Math.max(1, Math.min(100, Math.round(l.level * before / (float) l.max)));
    }

    // ---- the API --------------------------------------------------------------------------------------------------

    private static String base(Context c) {
        switch (Config.themeString(c, "tuya_region", "eu").trim().toLowerCase(Locale.ROOT)) {
            case "us": return "https://openapi.tuyaus.com";
            case "cn": return "https://openapi.tuyacn.com";
            case "in": return "https://openapi.tuyain.com";
            case "weu": return "https://openapi-weaz.tuyaeu.com";
            case "ueaz": return "https://openapi-ueaz.tuyaus.com";
            default: return "https://openapi.tuyaeu.com";
        }
    }

    /** A signed call with the project's token (fetched first, and again when it has run out). */
    private static synchronized JSONObject call(Context c, String method, String path, String body) throws Exception {
        String id = Config.themeString(c, "tuya_access_id", "").trim(), secret = Config.themeString(c, "tuya_access_secret", "").trim();
        if (id.isEmpty() || secret.isEmpty()) throw new IllegalStateException("no tuya project");
        if (token == null || System.currentTimeMillis() > tokenUntil) {
            JSONObject t = request(base(c), id, secret, null, "GET", "/v1.0/token?grant_type=1", null);
            JSONObject res = t.optJSONObject("result");
            if (!t.optBoolean("success") || res == null) throw new IllegalStateException("tuya token refused: " + t.optString("msg"));
            token = res.optString("access_token");
            tokenUntil = System.currentTimeMillis() + Math.max(60, res.optLong("expire_time", 7200) - 120) * 1000L;
        }
        JSONObject r = request(base(c), id, secret, token, method, path, body);
        if (!r.optBoolean("success") && r.optInt("code") == 1010) {   // token invalid: once more with a new one
            token = null;
            return call(c, method, path, body);
        }
        if (!r.optBoolean("success")) throw new IllegalStateException("tuya: " + r.optInt("code") + " " + r.optString("msg"));
        return r;
    }

    /**
     * Tuya's signature: HMAC-SHA256 with the secret over the client id, the token (business calls), the time and the
     * string to sign (the method, the body's SHA-256, no signed headers, and the path with its query sorted).
     */
    private static JSONObject request(String base, String id, String secret, String accessToken, String method, String path,
                                      String body) throws Exception {
        String t = String.valueOf(System.currentTimeMillis());
        String url = sortedQuery(path);
        String toSign = method + "\n" + sha256(body == null ? "" : body) + "\n\n" + url;
        String sign = hmac(secret, id + (accessToken == null ? "" : accessToken) + t + toSign).toUpperCase(Locale.ROOT);
        HttpURLConnection conn = (HttpURLConnection) new URL(base + url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("client_id", id);
        conn.setRequestProperty("sign", sign);
        conn.setRequestProperty("t", t);
        conn.setRequestProperty("sign_method", "HMAC-SHA256");
        if (accessToken != null) conn.setRequestProperty("access_token", accessToken);
        try {
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (OutputStream o = conn.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            InputStream in = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while (in != null && (n = in.read(buf)) > 0 && out.size() < 1024 * 1024) out.write(buf, 0, n);
            if (in != null) in.close();
            return new JSONObject(out.toString("UTF-8"));
        } finally {
            conn.disconnect();
        }
    }

    private static String sortedQuery(String path) {
        int q = path.indexOf('?');
        if (q < 0) return path;
        String[] parts = path.substring(q + 1).split("&");
        java.util.Arrays.sort(parts);
        return path.substring(0, q) + "?" + String.join("&", parts);
    }

    private static String sha256(String s) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hmac(String key, String s) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return hex(mac.doFinal(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }
}
