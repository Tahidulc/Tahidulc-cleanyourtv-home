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
import java.util.LinkedHashMap;
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
 * The thermostat too (a radiator valve, a heater): what it can do read from its own specification, its target set, on or
 * off, its mode; optional "tuya_thermostat" (name or device id) picks one when there are several.
 * Every call is off the main thread.
 */
final class TuyaCloud {
    /** One light: a bulb, a strip or a dimmer. level is 1-100, or -1 for one that only switches. */
    static final class Light {
        String id, name, category, switchCode, brightCode;
        /** A colour bulb's colour ("colour_data_v2", or "colour_data" on older ones) and mode ("work_mode"); null: white only. */
        String colourCode, modeCode;
        boolean online = true, on;
        int level = -1, min = 10, max = 1000;
        /** Its colour now (0xRRGGBB with full alpha) when it is in colour ("work_mode" colour); 0 when white or unknown. */
        int rgb;
    }

    private static final String[] SWITCHES = {"switch_led", "switch_led_1", "switch_1", "switch"};
    private static final String[] BRIGHTS = {"bright_value_v2", "bright_value", "bright_value_1", "bright_value_v2_1"};
    private static final String[] COLOURS = {"colour_data_v2", "colour_data"};
    /** What a light's look is made of, kept before Match mode's goal lights and put back after (MatchLights). */
    private static final String[] LOOK = {"work_mode", "bright_value_v2", "bright_value", "temp_value_v2", "temp_value",
            "colour_data_v2", "colour_data"};
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

    /** Several commands to one light at once; false when Tuya refused or could not be reached. */
    static boolean send(Context c, Light l, JSONArray cmds) {
        if (cmds == null || cmds.length() == 0) return true;
        try {
            return call(c, "POST", "/v1.0/devices/" + l.id + "/commands", new JSONObject().put("commands", cmds).toString())
                    .optBoolean("success");
        } catch (Exception e) {
            return false;
        }
    }

    /** Pure: a colour as Tuya's colour value: hue 0-360, saturation and value 0-1000 (0-255 for "colour_data"). */
    static JSONObject hsv(int rgb, String colourCode) {
        float[] f = new float[3];
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float h = 0;
        if (max != min) {
            float d = max - min;
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
            if (h < 0) h += 360f;
        }
        f[0] = h;
        f[1] = max == 0 ? 0 : (max - min) / (float) max;
        f[2] = max / 255f;
        int top = "colour_data".equals(colourCode) ? 255 : 1000;
        JSONObject o = new JSONObject();
        try {
            o.put("h", Math.round(f[0]) % 360).put("s", Math.round(f[1] * top)).put("v", Math.max(1, Math.round(f[2] * top)));
        } catch (Exception ignored) {
            // all numbers
        }
        return o;
    }

    /** Pure: Tuya's colour value ({"h":0-360,"s":..,"v":..}; 0-1000, or 0-255 for "colour_data") as RGB; 0 if unreadable. */
    static int rgbOf(String value, String colourCode) {
        try {
            JSONObject o = new JSONObject(value);
            float top = "colour_data".equals(colourCode) ? 255f : 1000f;
            return rgbOf((float) o.optDouble("h", 0), (float) (o.optDouble("s", 0) / top));
        } catch (Exception e) {
            return 0;
        }
    }

    /** Pure: a hue (0-360) and saturation (0-1) as RGB at full value (its brightness is the light's level, apart). */
    static int rgbOf(float hue, float sat) {
        hue = ((hue % 360f) + 360f) % 360f;
        float c = Math.max(0f, Math.min(1f, sat)), x = c * (1 - Math.abs((hue / 60f) % 2 - 1)), m = 1 - c;
        float r, g, b;
        if (hue < 60) { r = c; g = x; b = 0; } else if (hue < 120) { r = x; g = c; b = 0; } else if (hue < 180) { r = 0; g = c; b = x; }
        else if (hue < 240) { r = 0; g = x; b = c; } else if (hue < 300) { r = x; g = 0; b = c; } else { r = c; g = 0; b = x; }
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    /** A colour bulb in this colour, full and on; a white-only light at full brightness. */
    static boolean colour(Context c, Light l, int rgb) {
        try {
            JSONArray cmds = new JSONArray();
            if (!l.on) cmds.put(new JSONObject().put("code", l.switchCode).put("value", true));
            if (l.colourCode == null) {
                if (l.brightCode != null) cmds.put(new JSONObject().put("code", l.brightCode).put("value", l.max));
            } else {
                if (l.modeCode != null) cmds.put(new JSONObject().put("code", l.modeCode).put("value", "colour"));
                cmds.put(new JSONObject().put("code", l.colourCode).put("value", hsv(rgb, l.colourCode)));
            }
            return send(c, l, cmds);
        } catch (Exception e) {
            return false;
        }
    }

    /** A light's look now (its mode, brightness, warmth and colour) to put back later; empty when it can't be read. */
    static JSONArray look(Context c, Light l) {
        JSONArray out = new JSONArray();
        try {
            JSONArray st = status(c, l.id);
            for (String code : LOOK) {
                for (int i = 0; i < st.length(); i++) {
                    JSONObject x = st.optJSONObject(i);
                    if (x == null || !code.equals(x.optString("code"))) continue;
                    Object v = x.opt("value");
                    // A colour read back as text is sent as the object it is.
                    if (v instanceof String && ((String) v).trim().startsWith("{")) v = new JSONObject((String) v);
                    out.put(new JSONObject().put("code", code).put("value", v));
                }
            }
        } catch (Exception ignored) {
            // nothing kept: nothing put back
        }
        return out;
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
        for (String k : COLOURS) if (v.containsKey(k)) { l.colourCode = k; break; }
        if (v.containsKey("work_mode")) l.modeCode = "work_mode";
        l.rgb = 0;
        if (l.colourCode != null && (l.modeCode == null || "colour".equals(String.valueOf(v.get("work_mode"))))) {
            l.rgb = rgbOf(String.valueOf(v.get(l.colourCode)), l.colourCode);
        }
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

    // ---- cameras ------------------------------------------------------------------------------------------------

    /** Tuya's categories for cameras: the smart camera, the doorbell camera and the like. */
    private static final String[] CAMERAS = {"sp", "dghsxj", "sp_wnq", "spsxj", "kj"};

    /**
     * Every device the project sees: a Smart Home project lists those of its linked app accounts; a project of another
     * kind (custom, hotel and so on) lists them as the project's own things.
     */
    static JSONArray devices(Context c) throws Exception {
        JSONArray found = null;
        try {
            JSONObject r = call(c, "GET", "/v1.0/iot-01/associated-users/devices?size=100", null);
            found = r.optJSONObject("result") == null ? null : r.getJSONObject("result").optJSONArray("devices");
        } catch (Exception ignored) { }
        if (found != null && found.length() > 0) return found;
        try {
            JSONObject r = call(c, "GET", "/v2.0/cloud/thing/device?page_size=20", null);
            Object res = r.opt("result");
            if (res instanceof JSONArray && ((JSONArray) res).length() > 0) return (JSONArray) res;
        } catch (Exception ignored) { }
        JSONObject r = call(c, "GET", "/v1.3/iot-03/devices?page_size=50", null);
        JSONObject res = r.optJSONObject("result");
        return res == null ? new JSONArray() : res.optJSONArray("list") != null ? res.getJSONArray("list") : new JSONArray();
    }

    /** The account's cameras: {device id, name, "1" when online}. */
    static List<String[]> cameras(Context c) throws Exception {
        JSONArray devices = devices(c);
        List<String[]> out = new ArrayList<>();
        for (int i = 0; devices != null && i < devices.length(); i++) {
            JSONObject d = devices.getJSONObject(i);
            String cat = d.optString("category");
            boolean cam = false;
            for (String k : CAMERAS) cam |= k.equals(cat);
            if (cam) out.add(new String[]{d.optString("id"), d.optString("name", "Camera").trim(), d.optBoolean("online", true) ? "1" : "0"});
        }
        return out;
    }

    /**
     * A camera's live stream from Tuya's cloud (the IoT Video Live Stream service): its address for "hls" or "rtsp",
     * good for a few minutes; an exception when Tuya refuses (the service not authorised to the project, say).
     */
    static String stream(Context c, String deviceId, String type) throws Exception {
        JSONObject r = call(c, "POST", "/v1.0/devices/" + deviceId + "/stream/actions/allocate", new JSONObject().put("type", type).toString());
        JSONObject res = r.optJSONObject("result");
        return res == null ? null : res.optString("url", null);
    }

    // ---- thermostats ------------------------------------------------------------------------------------------------

    /** Tuya's categories for heating: the thermostat, the radiator valve, the wall thermostat and the heater. */
    private static final String[] HEATING = {"wk", "wkf", "wkcz", "rs"};
    /** A reading not had: no such code, or not read yet. */
    static final int NONE = Integer.MIN_VALUE;

    /** A number's range from a device's specification. Tuya sends whole numbers: real = raw / 10^scale, the step raw too. */
    static final class Range {
        final int min, max, scale, step;
        final String unit;
        /** Made up from the value's size, the specification not read. */
        final boolean guessed;

        Range(int min, int max, int scale, int step, String unit, boolean guessed) {
            this.min = Math.min(min, max);
            this.max = Math.max(min, max);
            this.scale = Math.max(0, Math.min(4, scale));
            this.step = Math.max(1, step);
            this.unit = unit == null ? "" : unit;
            this.guessed = guessed;
        }
    }

    /**
     * A thermostat (a radiator valve, a heater): its codes and ranges from its specification, and what it is doing as
     * Tuya last said. A code is null when the device has none of that kind.
     */
    static final class Thermostat {
        String id, name, category;
        boolean online = true;
        /** The target's, the room's, on/off's, the mode's and what it is doing's codes. */
        String setCode, roomCode, switchCode, modeCode, stateCode;
        Range set, room;
        boolean fahrenheit;
        /** The modes it can be put in, from its specification; empty when not known. */
        List<String> modes = new ArrayList<>();
        /** The target and the room's temperature, raw (NONE when not given); on (true without a switch), mode, state. */
        int target = NONE, temp = NONE;
        boolean on = true;
        String mode, state;
        /** Its specification: code to {type, values, "set" when it can be changed or "read"}; read once, kept. */
        Map<String, String[]> spec = new LinkedHashMap<>();
    }

    /** The thermostat found (its readings not kept), and when the project was last looked through. */
    private static Thermostat known;
    private static long scannedAt, specAt;
    /** Each device's specification once read (never the keys): it does not change. */
    private static final Map<String, Map<String, String[]>> specs = new HashMap<>();
    /** How many devices the project showed at its last look (-1: not looked yet): none at all is its own note. */
    static volatile int devicesSeen = -1;

    /**
     * The thermostat with its readings now: the one named in "tuya_thermostat" (its name or id), else the first online
     * one. Found once (its specification read once); after that one call for its state. Null when the project has none
     * (looked for again after a minute); an exception when Tuya cannot be reached or refuses.
     */
    static synchronized Thermostat thermostat(Context c) throws Exception {
        long now = System.currentTimeMillis();
        if (known == null) {
            if (scannedAt > 0 && now - scannedAt < 60_000L) return null;
            scannedAt = now;
            JSONArray devices;
            try {
                devices = devices(c);
            } catch (IllegalStateException e) {
                // Tuya answered but none of its lists gives a device (the last refusing): none to be seen. A refused
                // key is said as it is.
                String m = String.valueOf(e.getMessage());
                if (m.startsWith("tuya token") || m.startsWith("no tuya")) throw e;
                devices = null;
            }
            devicesSeen = devices == null ? 0 : devices.length();
            List<Thermostat> all = new ArrayList<>();
            for (int i = 0; devices != null && i < devices.length(); i++) {
                Thermostat t = heatingOf(devices.optJSONObject(i));
                if (t != null) all.add(t);
            }
            Thermostat t = choose(all, Config.themeString(c, "tuya_thermostat", ""));
            if (t == null) return null;
            known = t;
            specAt = 0;
        }
        Map<String, String[]> sp = specs.get(known.id);
        if (sp == null && (specAt == 0 || now - specAt > 10 * 60_000L)) {
            // Read once; not had (Tuya busy, say), asked again every ten minutes, the state read meanwhile.
            specAt = now;
            sp = spec(c, known.id, null);
            if (!sp.isEmpty()) specs.put(known.id, sp);
        }
        known.spec = sp != null ? sp : new LinkedHashMap<>();
        Thermostat t = new Thermostat();
        t.id = known.id;
        t.name = known.name;
        t.category = known.category;
        t.spec = known.spec;
        JSONArray status;
        try {
            // The device's own record: whether it is online, and its state (else its state alone).
            JSONObject d = call(c, "GET", "/v1.0/devices/" + t.id, null).optJSONObject("result");
            status = d == null ? null : d.optJSONArray("status");
            if (d != null && d.has("online")) t.online = d.optBoolean("online", true);
            if (status == null) status = status(c, t.id);
        } catch (Exception e) {
            try {
                status = status(c, t.id);
            } catch (IllegalStateException refused) {
                // Tuya refused it (gone from the project, say): the project looked through afresh next time.
                known = null;
                scannedAt = 0;
                throw refused;
            }
        }
        read(t, status);
        return t;
    }

    /** Pure: a device from the project's list as a thermostat when it is one (by its category, or a target in its state). */
    static Thermostat heatingOf(JSONObject d) {
        if (d == null) return null;
        String cat = d.optString("category");
        JSONArray status = d.optJSONArray("status");
        boolean yes = heatingCategory(cat);
        for (int i = 0; !yes && status != null && i < status.length(); i++) {
            JSONObject x = status.optJSONObject(i);
            yes = x != null && targetLike(x.optString("code"));
        }
        if (!yes) return null;
        Thermostat t = new Thermostat();
        t.id = d.optString("id");
        t.name = d.optString("name", "").trim();
        if (t.name.isEmpty()) t.name = "Thermostat";
        t.category = cat;
        // The Smart Home list says "online", the IoT Core list "isOnline".
        t.online = d.has("online") ? d.optBoolean("online", true) : d.optBoolean("isOnline", true);
        return t;
    }

    private static boolean heatingCategory(String cat) {
        for (String k : HEATING) if (k.equals(cat)) return true;
        return false;
    }

    /** Pure: a code for the temperature to keep (temp_set, temp_set_f, set_temp, TempSet and the like). */
    static boolean targetLike(String code) {
        String n = code == null ? "" : code.toLowerCase(Locale.ROOT).replace("_", "");
        return n.startsWith("tempset") || n.startsWith("settemp") || n.startsWith("targettemp");
    }

    /** Pure: a code for the room's temperature (temp_current, temp_current_f, temp_indoor and the like). */
    static boolean roomLike(String code) {
        String n = code == null ? "" : code.toLowerCase(Locale.ROOT).replace("_", "");
        return n.startsWith("tempcurrent") || n.startsWith("currenttemp") || n.startsWith("tempindoor") || n.startsWith("roomtemp");
    }

    /**
     * Pure: the thermostat to show: the one named (by its name or id, any case); else the first online one, a
     * thermostat, valve or heater before anything else with a target (an air conditioner, say); null when there is none.
     */
    static Thermostat choose(List<Thermostat> all, String want) {
        if (all == null || all.isEmpty()) return null;
        String w = want == null ? "" : want.trim();
        if (!w.isEmpty()) {
            for (Thermostat t : all) if (w.equalsIgnoreCase(t.id) || w.equalsIgnoreCase(t.name)) return t;
        }
        Thermostat best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Thermostat t : all) {
            int score = (t.online ? 0 : 2) + (heatingCategory(t.category) ? 0 : 1);
            if (score < bestScore) { best = t; bestScore = score; }
        }
        return best;
    }

    /**
     * A device's specification, from the first of Tuya's three ways of giving it that answers: code to {type, values,
     * "set" or "read"}; empty when none does. Each way's answer (codes, or why not) goes into report when given.
     */
    private static Map<String, String[]> spec(Context c, String id, StringBuilder report) {
        for (String path : new String[]{"/v1.0/devices/" + id + "/specifications", "/v1.0/iot-03/devices/" + id + "/specification",
                "/v1.0/devices/" + id + "/functions"}) {
            String shown = path.replace(id, "{id}");
            try {
                Map<String, String[]> m = specCodes(call(c, "GET", path, null).optJSONObject("result"));
                if (report != null) report.append("  specification ").append(shown).append(": ").append(m.size()).append(" codes\n");
                if (!m.isEmpty()) return m;
            } catch (Exception e) {
                if (report != null) report.append("  specification ").append(shown).append(": refused, ").append(e.getMessage()).append('\n');
            }
        }
        return new LinkedHashMap<>();
    }

    /** Pure: a specification's codes (what can be set first, then what can only be read): {type, values, "set" or "read"}. */
    static Map<String, String[]> specCodes(JSONObject result) {
        Map<String, String[]> out = new LinkedHashMap<>();
        if (result == null) return out;
        for (String part : new String[]{"functions", "status"}) {
            JSONArray a = result.optJSONArray(part);
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject f = a.optJSONObject(i);
                String code = f == null ? "" : f.optString("code");
                if (code.isEmpty() || out.containsKey(code)) continue;
                Object v = f.opt("values");
                out.put(code, new String[]{f.optString("type"), v == null ? "{}" : v.toString(), "functions".equals(part) ? "set" : "read"});
            }
        }
        return out;
    }

    /** Pure: an Integer value's range from its specification's values ({"min":50,"max":350,"scale":1,"step":5,"unit":"℃"}). */
    static Range rangeOf(String values) {
        try {
            JSONObject v = new JSONObject(values);
            if (!v.has("min") || !v.has("max")) return null;
            return new Range(v.optInt("min"), v.optInt("max"), v.optInt("scale", 0), v.optInt("step", 1), v.optString("unit", ""), false);
        } catch (Exception e) {
            return null;
        }
    }

    /** Pure: an Enum value's choices from its specification's values ({"range":["auto","manual"]}). */
    static List<String> enumOf(String values) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray r = new JSONObject(values).optJSONArray("range");
            for (int i = 0; r != null && i < r.length(); i++) if (!r.optString(i).isEmpty()) out.add(r.optString(i));
        } catch (Exception ignored) {
            // none known
        }
        return out;
    }

    /**
     * Pure: a target's range when the specification could not be read, from the value's size: 215 is 21.5 °C (scale 1,
     * half a degree a step), 21 is 21 °C (whole degrees); 70 °F in whole degrees, 700 in tenths.
     */
    static Range guess(int raw, boolean fahrenheit) {
        if (fahrenheit) return raw != NONE && raw >= 200 ? new Range(410, 950, 1, 10, "℉", true) : new Range(41, 95, 0, 1, "℉", true);
        return raw != NONE && raw >= 100 ? new Range(50, 350, 1, 5, "℃", true) : new Range(5, 35, 0, 1, "℃", true);
    }

    /**
     * Pure: degrees Fahrenheit or Celsius: a code ending _f, or a unit that says so (℉, °F, F); then the device's own
     * temp_unit_convert ("f"), when the unit says neither.
     */
    static boolean fahrenheit(String code, String unit, boolean deviceSaysF) {
        if (code != null && code.toLowerCase(Locale.ROOT).endsWith("_f")) return true;
        String u = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        if (u.contains("℉") || u.endsWith("f")) return true;
        if (u.contains("℃") || u.endsWith("c")) return false;
        return deviceSaysF;
    }

    /** Pure: its codes, ranges and readings from its specification (t.spec) and its state now (Tuya's status list). */
    static void read(Thermostat t, JSONArray status) {
        Map<String, Object> v = new HashMap<>();
        for (int i = 0; status != null && i < status.length(); i++) {
            JSONObject x = status.optJSONObject(i);
            if (x != null) v.put(x.optString("code"), x.opt("value"));
        }
        List<String> codes = new ArrayList<>(t.spec.keySet());
        for (String k : v.keySet()) if (!codes.contains(k)) codes.add(k);
        // Both units offered (temp_set and temp_set_f): the one the device shows.
        boolean deviceF = "f".equalsIgnoreCase(String.valueOf(v.get("temp_unit_convert")).trim());
        t.setCode = code(codes, deviceF, "temp_set", "temp_set_f");
        for (int i = 0; t.setCode == null && i < codes.size(); i++) if (targetLike(codes.get(i))) t.setCode = codes.get(i);
        t.roomCode = code(codes, deviceF, "temp_current", "temp_current_f");
        for (int i = 0; t.roomCode == null && i < codes.size(); i++) if (roomLike(codes.get(i))) t.roomCode = codes.get(i);
        t.switchCode = first(t, v, Boolean.class, "Boolean", "switch", "switch_1", "power");
        t.modeCode = first(t, v, String.class, "Enum", "mode", "work_mode");
        t.stateCode = first(t, v, String.class, "Enum", "work_state", "valve_state");
        t.target = number(t.setCode == null ? null : v.get(t.setCode));
        t.temp = number(t.roomCode == null ? null : v.get(t.roomCode));
        String main = t.setCode != null ? t.setCode : t.roomCode;
        String[] ms = main == null ? null : t.spec.get(main);
        Range mr = ms == null ? null : rangeOf(ms[1]);
        t.fahrenheit = fahrenheit(main, mr == null ? null : mr.unit, deviceF);
        String[] ss = t.setCode == null ? null : t.spec.get(t.setCode);
        Range sr = ss == null ? null : rangeOf(ss[1]);
        t.set = sr != null ? sr : guess(t.target, t.fahrenheit);
        String[] rs = t.roomCode == null ? null : t.spec.get(t.roomCode);
        Range rr = rs == null ? null : rangeOf(rs[1]);
        // The room's range unknown: read at the target's scale (the same device's).
        t.room = rr != null ? rr : new Range(-1000, 10000, t.set.scale, 1, t.set.unit, true);
        t.on = t.switchCode == null || !Boolean.FALSE.equals(v.get(t.switchCode));
        Object m = t.modeCode == null ? null : v.get(t.modeCode);
        t.mode = m == null || m == JSONObject.NULL ? null : String.valueOf(m);
        Object s = t.stateCode == null ? null : v.get(t.stateCode);
        t.state = s == null || s == JSONObject.NULL ? null : String.valueOf(s);
        t.modes = new ArrayList<>();
        String[] mspec = t.modeCode == null ? null : t.spec.get(t.modeCode);
        if (mspec != null) t.modes.addAll(enumOf(mspec[1]));
    }

    private static String code(List<String> codes, boolean wantF, String c, String f) {
        if (wantF && codes.contains(f)) return f;
        if (codes.contains(c)) return c;
        return codes.contains(f) ? f : null;
    }

    /** The first of these codes the device has: of this type in its state, or in its specification. */
    private static String first(Thermostat t, Map<String, Object> v, Class<?> kind, String specType, String... codes) {
        for (String k : codes) {
            String[] s = t.spec.get(k);
            if (kind.isInstance(v.get(k)) || (s != null && specType.equalsIgnoreCase(s[0]))) return k;
        }
        return null;
    }

    private static int number(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return o == null ? NONE : (int) Math.round(Double.parseDouble(String.valueOf(o).trim()));
        } catch (NumberFormatException e) {
            return NONE;
        }
    }

    /** Pure: a raw value in real units (215 at scale 1 is 21.5). */
    static double real(int raw, int scale) {
        return raw / Math.pow(10, scale);
    }

    /** Pure: a raw value kept inside the range and on its steps (counted from its least), the nearest step that fits. */
    static int snap(int raw, Range r) {
        long v = Math.max(r.min, Math.min(r.max, raw));
        long k = Math.round((v - r.min) / (double) r.step);
        long s = r.min + k * r.step;
        if (s > r.max) s -= r.step;
        return (int) Math.max(r.min, s);
    }

    /** Pure: real degrees as the device's raw value (21.5 at scale 1 is 215), in its range and on its steps. */
    static int toRaw(double real, Range r) {
        return snap((int) Math.round(real * Math.pow(10, r.scale)), r);
    }

    /** Pure: the target moved this many of the device's steps (Left and Right), kept in its range; NONE stays NONE. */
    static int nudge(int raw, int steps, Range r) {
        if (raw == NONE || r == null) return raw;
        return snap(snap(raw, r) + steps * r.step, r);
    }

    /** Pure: degrees to show: "21.5°C", "21°C", "70°F" (unit), or "21.5°" (not); a dash when not known. */
    static String degrees(int raw, Range r, boolean fahrenheit, boolean unit) {
        if (raw == NONE || r == null) return "–";
        String s = String.format(Locale.ROOT, r.scale > 0 ? "%.1f" : "%.0f", real(raw, r.scale));
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        if ("-0".equals(s)) s = "0";
        return s + (unit ? (fahrenheit ? "°F" : "°C") : "°");
    }

    /** Pure: a mode as words ("temp_auto" is "Temp auto", "ECO" is "Eco"). */
    static String modeName(String mode) {
        if (mode == null || mode.trim().isEmpty()) return "";
        String s = mode.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Pure: what it is doing, as one of "heating", "idle", "open", "closed"; null when it says something else. */
    static String stateKind(String state) {
        if (state == null) return null;
        switch (state.trim().toLowerCase(Locale.ROOT)) {
            case "heating": case "heat": case "warming": case "working": return "heating";
            case "idle": case "standby": case "stop": case "stopped": case "hold": return "idle";
            case "open": case "opened": case "valve_open": return "open";
            case "close": case "closed": case "valve_close": return "closed";
            default: return null;
        }
    }

    /**
     * Pure: the commands that make the thermostat what is wanted, only for what differs from what it is: on or off
     * first, then the mode, then the target (in its range, on its steps). Empty when nothing differs.
     */
    static JSONArray commands(Thermostat was, int target, boolean on, String mode) {
        JSONArray out = new JSONArray();
        try {
            if (was.switchCode != null && on != was.on) out.put(new JSONObject().put("code", was.switchCode).put("value", on));
            if (was.modeCode != null && mode != null && !mode.equals(was.mode)) out.put(new JSONObject().put("code", was.modeCode).put("value", mode));
            if (was.setCode != null && target != NONE && was.set != null) {
                int to = snap(target, was.set);
                if (to != was.target) out.put(new JSONObject().put("code", was.setCode).put("value", to));
            }
        } catch (Exception ignored) {
            // codes and plain values only
        }
        return out;
    }

    /** Commands to a thermostat: null when Tuya took them; else why not, in Tuya's words ("" when not reachable). */
    static String command(Context c, String id, JSONArray cmds) {
        if (cmds == null || cmds.length() == 0) return null;
        try {
            call(c, "POST", "/v1.0/devices/" + id + "/commands", new JSONObject().put("commands", cmds).toString());
            return null;
        } catch (IllegalStateException e) {
            return why(e.getMessage());
        } catch (Exception e) {
            return "";
        }
    }

    /** Pure: Tuya's refusal in a few words ("tuya: 2001 device is offline" is "device is offline"). */
    static String why(String message) {
        if (message == null) return "";
        String m = message.trim();
        if (m.startsWith("tuya token refused:")) m = m.substring("tuya token refused:".length()).trim();
        else if (m.startsWith("tuya:")) m = m.substring(5).trim().replaceFirst("^-?\\d+\\s*", "");
        return m.length() > 60 ? m.substring(0, 59) + "…" : m;
    }

    /** Pure: a specification's code for adb's report: its type, whether it can be set, and its range or choices. */
    static String specLine(String code, String[] s) {
        StringBuilder b = new StringBuilder(code).append(": ").append(s[0]).append(", ").append(s[2]);
        Range r = "Integer".equalsIgnoreCase(s[0]) ? rangeOf(s[1]) : null;
        if (r != null) {
            boolean f = fahrenheit(code, r.unit, false);
            b.append(", ").append(degreesOrNumber(r.min, r, f)).append(" to ").append(degreesOrNumber(r.max, r, f))
                    .append(" step ").append(degreesOrNumber(r.step, r, f)).append(" (raw ").append(r.min).append('-').append(r.max)
                    .append(" scale ").append(r.scale).append(" step ").append(r.step).append(r.unit.isEmpty() ? "" : " " + r.unit).append(')');
        } else if ("Enum".equalsIgnoreCase(s[0])) {
            b.append(", ").append(String.join(" / ", enumOf(s[1])));
        } else if (!"Boolean".equalsIgnoreCase(s[0])) {
            b.append(", ").append(clip(s[1], 80));
        }
        return b.toString();
    }

    private static String degreesOrNumber(int raw, Range r, boolean f) {
        boolean temp = r.unit.contains("℃") || r.unit.contains("℉") || r.unit.contains("°");
        String s = degrees(raw, r, f, temp);
        return temp ? s : s.replace("°", "");
    }

    private static String clip(String s, int n) {
        return s == null ? "" : s.length() > n ? s.substring(0, n) + "…" : s;
    }

    /**
     * adb's report on the devices that heat: for each, its category and id, its specification's codes (type, range or
     * choices), its state's codes and values, and how the Control Centre reads it; which one it shows. Never the keys.
     */
    private static void thermostats(Context c, JSONArray devices, StringBuilder b) {
        List<Thermostat> all = new ArrayList<>();
        for (int i = 0; i < devices.length(); i++) {
            Thermostat t = heatingOf(devices.optJSONObject(i));
            if (t != null) all.add(t);
        }
        if (all.isEmpty()) {
            b.append("thermostat: none among the devices (categories wk, wkf, wkcz, rs, or a temp_set in the state)\n");
            return;
        }
        String want = Config.themeString(c, "tuya_thermostat", "").trim();
        Thermostat chosen = choose(all, want);
        if (!want.isEmpty() && !want.equalsIgnoreCase(chosen.id) && !want.equalsIgnoreCase(chosen.name)) {
            b.append("tuya_thermostat \"").append(want).append("\" matches none: the first online one is shown\n");
        }
        for (Thermostat t : all) {
            b.append("thermostat: ").append(t.name).append(" | ").append(t.category).append(" | id ").append(t.id)
                    .append(" | online ").append(t.online).append(t == chosen ? " | shown in the Control Centre" : "").append('\n');
            t.spec = spec(c, t.id, b);
            for (Map.Entry<String, String[]> e : t.spec.entrySet()) b.append("  spec ").append(specLine(e.getKey(), e.getValue())).append('\n');
            JSONArray st;
            try {
                st = status(c, t.id);
            } catch (Exception e) {
                b.append("  status: refused, ").append(e.getMessage()).append('\n');
                continue;
            }
            read(t, st);
            for (int i = 0; i < st.length(); i++) {
                JSONObject x = st.optJSONObject(i);
                if (x == null) continue;
                String code = x.optString("code");
                b.append("  status ").append(code).append(" = ").append(clip(String.valueOf(x.opt("value")), 60));
                if (code.equals(t.setCode)) b.append(" (").append(degrees(t.target, t.set, t.fahrenheit, true)).append(')');
                if (code.equals(t.roomCode)) b.append(" (").append(degrees(t.temp, t.room, t.fahrenheit, true)).append(')');
                b.append('\n');
            }
            b.append("  read as: target ").append(t.setCode).append(t.set.guessed ? " (range guessed, no specification)" : "")
                    .append(" | room ").append(t.roomCode).append(" | switch ").append(t.switchCode)
                    .append(" | mode ").append(t.modeCode).append(t.modes.isEmpty() ? "" : " " + t.modes)
                    .append(" | state ").append(t.stateCode).append(" | ").append(t.fahrenheit ? "°F" : "°C").append('\n');
        }
    }

    /**
     * adb's check of the project, off the main thread, to files/tuya.txt: whether its key is taken, every device it
     * sees (name, category, online), plainly when it sees none, each thermostat's specification and state (thermostats),
     * and for each camera whether a live stream is given (never the keys, the token or a stream's address).
     */
    static void test(Context c) {
        Context app = c.getApplicationContext();
        new Thread(() -> {
            StringBuilder b = new StringBuilder();
            try {
                call(app, "GET", "/v1.0/iot-01/associated-users/devices?size=100", null);
                b.append("key: ok\n");
                for (String path : new String[]{"/v1.0/iot-01/associated-users/devices?size=100", "/v2.0/cloud/thing/device?page_size=20", "/v1.3/iot-03/devices?page_size=50"}) {
                    try {
                        JSONObject r = call(app, "GET", path, null);
                        Object res = r.opt("result");
                        int n = res instanceof JSONArray ? ((JSONArray) res).length()
                                : res instanceof JSONObject ? (((JSONObject) res).optJSONArray("devices") != null ? ((JSONObject) res).getJSONArray("devices").length()
                                : ((JSONObject) res).optJSONArray("list") != null ? ((JSONObject) res).getJSONArray("list").length() : -1) : -1;
                        b.append("list ").append(path.substring(0, path.indexOf('?'))).append(": ").append(n).append('\n');
                    } catch (Exception e) {
                        b.append("list ").append(path.substring(0, path.indexOf('?'))).append(": refused, ").append(e.getMessage()).append('\n');
                    }
                }
                JSONArray devices;
                try {
                    devices = devices(app);
                } catch (IllegalStateException e) {
                    // The last of the lists refused and the others empty (as above): no devices to be seen.
                    devices = null;
                }
                for (int i = 0; devices != null && i < devices.length(); i++) {
                    JSONObject d = devices.getJSONObject(i);
                    b.append("device: ").append(d.optString("name")).append(" | ").append(d.optString("category"))
                            .append(" | online ").append(d.optBoolean("online")).append('\n');
                }
                if (devices == null || devices.length() == 0) {
                    b.append("no devices: link the Tuya app account to the cloud project, and check its IoT Core service has not expired\n");
                } else {
                    thermostats(app, devices, b);
                }
                for (String[] cam : devices == null || devices.length() == 0 ? new ArrayList<String[]>() : cameras(app)) {
                    for (String type : new String[]{"hls", "rtsp"}) {
                        try {
                            String url = stream(app, cam[0], type);
                            b.append("stream ").append(type).append(" ").append(cam[1]).append(": ")
                                    .append(url == null ? "none" : "ok (" + (url.startsWith("rtsp") ? "rtsp" : url.contains(".m3u8") ? "m3u8" : "other") + ")").append('\n');
                        } catch (Exception e) {
                            b.append("stream ").append(type).append(" ").append(cam[1]).append(": refused, ").append(e.getMessage()).append('\n');
                        }
                    }
                }
            } catch (Exception e) {
                b.append("failed: ").append(e.getMessage()).append('\n');
            }
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(app.getExternalFilesDir(null), "tuya.txt"))) {
                o.write(b.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
        }).start();
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
