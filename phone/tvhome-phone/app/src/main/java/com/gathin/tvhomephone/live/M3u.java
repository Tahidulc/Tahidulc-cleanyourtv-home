package com.gathin.tvhomephone.live;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An M3U or M3U8 playlist read line by line (large lists of tens of thousands of entries stay quick): #EXTM3U's guide
 * address (url-tvg, x-tvg-url or tvg-url), each #EXTINF's attributes in any order (tvg-id, tvg-name, tvg-logo,
 * group-title, tvg-chno) and its title after the first comma outside quotes, #EXTGRP, and #EXTVLCOPT's user agent and
 * referrer. Only live channels are kept: entries that are films or series (a /movie/ or /series/ path, a video file, or
 * tvg-type other than live) are left out.
 */
public final class M3u {
    private M3u() {}

    public static final class Result {
        public final List<Channel> channels = new ArrayList<>();
        /** The guide's address from the header, or "" when it names none. */
        public String guideUrl = "";
        /** Entries left out as films or series. */
        public int skipped;
    }

    public static Result parse(Reader in) throws IOException {
        Result r = new Result();
        BufferedReader br = in instanceof BufferedReader ? (BufferedReader) in : new BufferedReader(in, 64 * 1024);
        Map<String, String> attrs = null;
        String title = null, group = null, ua = null, ref = null;
        Map<String, Integer> seen = new HashMap<>();
        boolean first = true;
        for (String line; (line = br.readLine()) != null; ) {
            if (first) {
                // A byte-order mark before #EXTM3U.
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
                first = false;
            }
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                String upper = line.length() > 12 ? line.substring(0, 12).toUpperCase(Locale.ROOT) : line.toUpperCase(Locale.ROOT);
                if (upper.startsWith("#EXTM3U")) {
                    Map<String, String> h = attributes(line.substring(7), null);
                    String g = h.get("url-tvg");
                    if (g == null) g = h.get("x-tvg-url");
                    if (g == null) g = h.get("tvg-url");
                    if (g != null && !g.trim().isEmpty()) r.guideUrl = firstOf(g);
                } else if (upper.startsWith("#EXTINF")) {
                    int colon = line.indexOf(':');
                    String[] t = new String[1];
                    attrs = attributes(colon < 0 ? "" : line.substring(colon + 1), t);
                    title = t[0];
                } else if (upper.startsWith("#EXTGRP")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) group = line.substring(colon + 1).trim();
                } else if (upper.startsWith("#EXTVLCOPT")) {
                    int colon = line.indexOf(':');
                    String opt = colon < 0 ? "" : line.substring(colon + 1).trim();
                    int eq = opt.indexOf('=');
                    if (eq > 0) {
                        String k = opt.substring(0, eq).trim().toLowerCase(Locale.ROOT), v = opt.substring(eq + 1).trim();
                        if (k.equals("http-user-agent")) ua = v;
                        else if (k.equals("http-referrer") || k.equals("http-referer")) ref = v;
                    }
                }
                continue;
            }
            // The stream's address: the entry is complete.
            String url = line;
            Map<String, String> a = attrs == null ? new HashMap<>() : attrs;
            String name = title == null || title.trim().isEmpty() ? a.get("tvg-name") : title.trim();
            if (name == null || name.trim().isEmpty()) name = tail(url);
            String g = a.get("group-title");
            if (g == null || g.isEmpty()) g = group;
            if (isVod(url, a.get("tvg-type"))) {
                r.skipped++;
            } else {
                String id = a.get("tvg-id") == null ? "" : a.get("tvg-id").trim();
                String key = id + "|" + name.trim();
                Integer n = seen.get(key);
                seen.put(key, n == null ? 1 : n + 1);
                if (n != null) key = key + "#" + (n + 1);
                String agent = ua != null ? ua : a.get("user-agent");
                r.channels.add(new Channel(key, name.trim(), g == null ? "" : g.trim(), a.get("tvg-logo"), url, id, agent, ref,
                        number(a.get("tvg-chno"))));
            }
            attrs = null;
            title = null;
            group = null;
            ua = null;
            ref = null;
        }
        return r;
    }

    /**
     * The attributes of an #EXTINF (after its colon) or #EXTM3U line: key="value" pairs (or 'value', or bare values)
     * in any order, after the duration. title[0] gets what follows the first comma outside quotes.
     */
    static Map<String, String> attributes(String s, String[] title) {
        Map<String, String> out = new HashMap<>();
        int i = 0, n = s.length();
        // The duration (-1 or 0 for live): up to the first space or comma.
        while (i < n && s.charAt(i) != ' ' && s.charAt(i) != ',' && s.charAt(i) != '\t' && title != null) i++;
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == ' ' || ch == '\t') { i++; continue; }
            if (ch == ',') {
                if (title != null) title[0] = s.substring(i + 1);
                return out;
            }
            int keyStart = i;
            while (i < n && s.charAt(i) != '=' && s.charAt(i) != ' ' && s.charAt(i) != ',') i++;
            String key = s.substring(keyStart, i).trim().toLowerCase(Locale.ROOT);
            if (i < n && s.charAt(i) == '=') {
                i++;
                String value;
                if (i < n && (s.charAt(i) == '"' || s.charAt(i) == '\'')) {
                    char q = s.charAt(i++);
                    int end = s.indexOf(q, i);
                    if (end < 0) end = n;
                    value = s.substring(i, end);
                    i = Math.min(n, end + 1);
                } else {
                    int vs = i;
                    while (i < n && s.charAt(i) != ' ' && s.charAt(i) != ',') i++;
                    value = s.substring(vs, i);
                }
                if (!key.isEmpty()) out.put(key, value);
            }
        }
        if (title != null && title[0] == null) title[0] = "";
        return out;
    }

    /** A film or series rather than a live channel. */
    static boolean isVod(String url, String type) {
        if (type != null && !type.isEmpty() && !type.equalsIgnoreCase("live") && !type.equalsIgnoreCase("stream")) return true;
        String u = url.toLowerCase(Locale.ROOT);
        int q = u.indexOf('?');
        String path = q < 0 ? u : u.substring(0, q);
        if (path.contains("/movie/") || path.contains("/movies/") || path.contains("/series/")) return true;
        return path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi") || path.endsWith(".mov")
                || path.endsWith(".wmv") || path.endsWith(".m4v");
    }

    private static String firstOf(String list) {
        int c = list.indexOf(',');
        return (c < 0 ? list : list.substring(0, c)).trim();
    }

    private static String tail(String url) {
        String u = url;
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        int s = u.lastIndexOf('/');
        return s >= 0 && s < u.length() - 1 ? u.substring(s + 1) : u;
    }

    private static int number(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
