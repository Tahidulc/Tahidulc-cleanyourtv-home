package com.gathin.tvhomephone.live;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

/**
 * Xtream Codes addresses, built from the server, username and password the family typed in: the player API's actions
 * (the account, the live categories, the live streams), a live stream's address in the account's allowed format, and the
 * guide (xmltv.php). Only live TV is asked for.
 */
public final class Xtream {
    private Xtream() {}

    /**
     * The server as typed, made into a base address: a scheme added when missing, and anything after the host and port
     * dropped (a pasted player_api.php, get.php or trailing slash).
     */
    public static String base(String server) {
        String s = server == null ? "" : server.trim();
        if (s.isEmpty()) return "";
        if (!s.contains("://")) s = "http://" + s;
        int scheme = s.indexOf("://") + 3;
        int slash = s.indexOf('/', scheme);
        if (slash >= 0) s = s.substring(0, slash);
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        return s;
    }

    public static String api(String base, String user, String pass, String action) {
        String u = base + "/player_api.php?username=" + enc(user) + "&password=" + enc(pass);
        return action == null || action.isEmpty() ? u : u + "&action=" + action;
    }

    /** A live stream: base/live/user/pass/id.ext. */
    public static String stream(String base, String user, String pass, String streamId, String ext) {
        return base + "/live/" + path(user) + "/" + path(pass) + "/" + streamId + "." + (ext == null || ext.isEmpty() ? "ts" : ext);
    }

    public static String xmltv(String base, String user, String pass) {
        return base + "/xmltv.php?username=" + enc(user) + "&password=" + enc(pass);
    }

    /** HLS when the account allows it (it plays best on phones), else MPEG-TS. */
    public static String pickFormat(List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) return "ts";
        boolean ts = false;
        for (String f : allowed) {
            String x = f == null ? "" : f.trim().toLowerCase(Locale.ROOT);
            if (x.equals("m3u8") || x.equals("hls")) return "m3u8";
            if (x.equals("ts")) ts = true;
        }
        return ts ? "ts" : "m3u8";
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    /** A path segment: as enc(), with spaces as %20. */
    static String path(String s) {
        return enc(s).replace("+", "%20");
    }
}
