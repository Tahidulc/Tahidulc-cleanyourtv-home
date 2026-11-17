package com.gathin.homefieldtv.sync;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Pure: the names the family's devices give the shared things (phone/tvhome-phone/SYNC.md), the same on the phone, the
 * tablet and Homefield TV (a copy of the phone app's class: keep the two the same): a playlist's key from what it is (never its password), a channel's
 * key within its playlist, a film's or series' key from its name, and a stable fingerprint of an item's data.
 */
public final class SyncKeys {
    private SyncKeys() {}

    /**
     * A playlist's key: "pl:" and 24 hex digits of a hash of its kind and where it is (an M3U playlist's address, an
     * Xtream account's server and user name), never its password: the same playlist on every device, whatever name it
     * was given there, and still the same one after its password changes.
     */
    public static String playlist(String kind, String m3u, String server, String user) {
        String what;
        if ("xtream".equals(kind)) what = "xtream\n" + base(server) + "\n" + (user == null ? "" : user.trim());
        else what = "m3u\n" + (m3u == null ? "" : m3u.trim());
        return "pl:" + hex(what, 12);
    }

    /** A server's address as the key reads it: lower case, without the scheme and the slash at its end. */
    static String base(String server) {
        String s = server == null ? "" : server.trim().toLowerCase(Locale.ROOT);
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** A channel's key within the family's things: its playlist's key and its own key there ("x:123", "tvg-id|name"). */
    public static String channel(String playlistKey, String channelKey) {
        String k = playlistKey + "/" + channelKey;
        return k.length() <= 180 ? k : playlistKey + "/#" + hex(channelKey, 16);
    }

    /**
     * A film's or series' name as a key: lower case, the playlists' tags off ("EN - ", "[4K]", "(2021)", "UHD"), only
     * letters and digits, single spaces ("EN - Dune: Part Two (2024) 4K" → "dune part two").
     */
    public static String title(String name) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT);
        s = s.replaceAll("^\\s*[\\[(|]?\\s*[a-z]{2,3}\\s*[\\])|:\\-]+\\s+", " ");
        s = s.replaceAll("[\\[(][^\\])]*[\\])]", " ");
        s = s.replaceAll("\\b(19|20)\\d{2}\\b", " ");
        s = s.replaceAll("\\b(4k|uhd|fhd|hd|sd|hdr10\\+?|hdr|dv|dovi|atmos|2160p|1080p|720p|480p|x264|x265|hevc|multi|sub|dub)\\b", " ");
        s = s.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
        return s;
    }

    /** A resume item's key: "mv:" or "tv:" and the title's key. */
    public static String resume(boolean tv, String name) {
        return (tv ? "tv:" : "mv:") + title(name);
    }

    /** A fingerprint of an item's data: the same for the same content in any key order. */
    public static String fingerprint(Object data) {
        return hex(canonical(data), 16);
    }

    /** The data written the same way every time: object keys in order, arrays as they are. */
    public static String canonical(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v);
        return b.toString();
    }

    private static void write(StringBuilder b, Object v) {
        if (v == null || v == JSONObject.NULL) {
            b.append("null");
        } else if (v instanceof JSONObject) {
            JSONObject o = (JSONObject) v;
            List<String> keys = new ArrayList<>();
            for (Iterator<String> it = o.keys(); it.hasNext(); ) keys.add(it.next());
            Collections.sort(keys);
            b.append('{');
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) b.append(',');
                b.append(JSONObject.quote(keys.get(i))).append(':');
                write(b, o.opt(keys.get(i)));
            }
            b.append('}');
        } else if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            b.append('[');
            for (int i = 0; i < a.length(); i++) {
                if (i > 0) b.append(',');
                write(b, a.opt(i));
            }
            b.append(']');
        } else if (v instanceof Number || v instanceof Boolean) {
            b.append(v);
        } else {
            b.append(JSONObject.quote(String.valueOf(v)));
        }
    }

    private static String hex(String s, int bytes) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < bytes; i++) out.append(String.format("%02x", h[i]));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
