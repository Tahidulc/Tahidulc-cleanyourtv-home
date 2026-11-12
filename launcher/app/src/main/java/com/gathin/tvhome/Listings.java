package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What is on the channel watched, from the playlist's own TV guide: the guide TiviMate shows is the playlist's, so the
 * same listings are read here from the playlist's server (its Xtream API, with the theme's playlist login, as
 * PlaylistSport does), never from TiviMate itself. The channel's name (InStep's) is found among the playlist's live
 * channels (read at most every six hours, names only kept) by its base name, its country tag and HD, FHD, UHD or 4K
 * tag aside; then its short guide (the programme on now and the next few, their titles in base64) is read at most
 * every five minutes a channel. Off the main thread, except bannerChannel, which only looks in what is already read.
 * The login, the server and stream addresses are never logged.
 */
final class Listings {
    private Listings() { }

    /** A programme in the guide: its title and description (as written), its start and end (ms). */
    static final class Prog {
        final String title, desc;
        final long start, end;

        Prog(String title, String desc, long start, long end) {
            this.title = title == null ? "" : title;
            this.desc = desc == null ? "" : desc;
            this.start = start;
            this.end = end;
        }

        boolean on(long at) {
            return at >= start && at < end;
        }

        @Override public String toString() {
            java.text.SimpleDateFormat hm = new java.text.SimpleDateFormat("HH:mm", Locale.UK);
            return "\"" + title + "\" " + hm.format(new java.util.Date(start)) + "–" + hm.format(new java.util.Date(end));
        }
    }

    static final long STREAMS_FRESH_MS = 6 * 3_600_000L, GUIDE_FRESH_MS = 5 * 60_000L;

    /** The playlist's live channels: base name -> {name as written, stream id}. */
    private static volatile Map<String, String[]> streams = Collections.emptyMap();
    private static long streamsAt;
    private static final Map<String, List<Prog>> guides = new HashMap<>();
    private static final Map<String, Long> guidesAt = new HashMap<>();

    // ---- pure ----------------------------------------------------------------------------------------------------------

    /** Country tags before a channel's name ("UK: ", "UK | ", "|UK| ", "[UK] ", "EN - "). */
    private static final Pattern COUNTRY = Pattern.compile("(?i)^\\s*(?:[|\\[(]\\s*)?(?:uk|gb|en|eng|ie)\\s*(?:[|\\])]\\s*)?\\s*[:|\\-]?\\s+");
    /** Quality and copy tags after it ("HD", "FHD", "UHD", "4K", "SD", "HEVC", "50FPS", "(UK)", "ᴴᴰ", "(Backup)"). */
    private static final Pattern TAGS = Pattern.compile("(?i)(?:\\s*(?:\\(\\s*(?:uk|gb|backup|alt|\\d+)\\s*\\)|\\[[^\\]]*\\]|\\b(?:f?hd|uhd|4k|sd|hevc|h265|raw|50\\s*fps|60\\s*fps|backup|alt)\\b|[\\u1D2C-\\u1D6A\\u02B0-\\u02FF]+))+\\s*$");

    /**
     * A channel's name as compared: its country tag, its quality and copy tags aside, letters and digits only, lower case
     * ("UK: Sky Sports Main Event UHD" and "Sky Sports Main Event HD" are both "skysportsmainevent").
     */
    static String base(String name) {
        if (name == null) return "";
        String s = COUNTRY.matcher(name.trim()).replaceFirst("");
        String before;
        do {
            before = s;
            s = TAGS.matcher(s).replaceFirst("").trim();
        } while (!s.equals(before) && !s.isEmpty());
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Of the playlist's channels ({name, stream id} each), the one named as the channel watched is: the first with the
     * same base name, the first name tried first; null when none is.
     */
    static String[] find(List<String[]> channels, List<String> names) {
        if (channels == null || names == null) return null;
        for (String n : names) {
            String want = base(n);
            if (want.isEmpty()) continue;
            for (String[] c : channels) if (want.equals(base(c[0]))) return c;
        }
        return null;
    }

    /** The playlist's short guide (get_short_epg's answer): its programmes in time order, titles read out of base64. */
    static List<Prog> parseShortEpg(String json) {
        List<Prog> out = new ArrayList<>();
        try {
            JSONArray l = new JSONObject(json).optJSONArray("epg_listings");
            for (int i = 0; l != null && i < l.length(); i++) {
                JSONObject x = l.optJSONObject(i);
                if (x == null) continue;
                long st = x.optLong("start_timestamp") * 1000L, en = x.optLong("stop_timestamp") * 1000L;
                if (st <= 0) st = AdBreakLogic.time(x.optString("start").replace(' ', 'T') + "Z");
                if (en <= 0) en = AdBreakLogic.time(x.optString("end", x.optString("stop")).replace(' ', 'T') + "Z");
                if (st <= 0 || en <= st) continue;
                out.add(new Prog(b64(x.optString("title")), b64(x.optString("description")), st, en));
            }
        } catch (Exception ignored) {
            // no guide
        }
        Collections.sort(out, (a, b) -> Long.compare(a.start, b.start));
        return out;
    }

    /** Base64 read back to words; words that were never base64 kept as they are. */
    static String b64(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            String t = new String(java.util.Base64.getMimeDecoder().decode(s), StandardCharsets.UTF_8).trim();
            // A plain title that happens to decode is gibberish: keep the decoding only when it reads as words.
            int ok = 0;
            for (int i = 0; i < t.length(); i++) {
                char ch = t.charAt(i);
                if (Character.isLetterOrDigit(ch) || Character.isWhitespace(ch) || ".,:;!?'\"&()-–—/|+".indexOf(ch) >= 0) ok++;
            }
            return t.length() > 0 && ok >= t.length() * 0.9 ? t : s;
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    /** Of programmes in time order, the one on at at, and the next if it starts within soonMs: 0, 1 or 2 of them. */
    static List<Prog> around(List<Prog> progs, long at, long soonMs) {
        List<Prog> out = new ArrayList<>();
        if (progs == null) return out;
        for (Prog p : progs) {
            if (p.on(at)) out.add(p);
            else if (p.start > at && p.start - at <= soonMs) out.add(p);
            if (out.size() == 2) break;
        }
        return out;
    }

    /**
     * Which one channel of the playlist's a screen's words name (TiviMate's banner as the channel changes): the channel
     * when exactly one of the words (of two words or more) is one channel's whole name, else null (a list or the guide
     * names many).
     */
    static String named(List<String> texts, Map<String, String[]> byBase) {
        if (texts == null || byBase == null || byBase.isEmpty() || texts.size() > 25) return null;
        String hit = null;
        for (String t : texts) {
            // Two words at least: a lone word ("Football", "News") is more likely a heading than a channel.
            if (t == null || t.length() < 5 || t.length() > 60 || t.trim().split("\\s+").length < 2) continue;
            String b = base(t);
            if (b.length() < 5) continue;
            String[] c = byBase.get(b);
            if (c == null) continue;
            if (hit != null && !base(hit).equals(b)) return null;
            hit = c[0];
        }
        return hit;
    }

    // ---- the playlist's server ------------------------------------------------------------------------------------------

    /** Whether there is a playlist login to read the guide with. */
    static boolean have(Context c) {
        return !Config.themeString(c, "iptv_server", "").isEmpty();
    }

    /** The programme on the channel now and the next within soonMs, from the playlist's guide; empty when unknown. */
    static List<Prog> onNow(Context c, List<String> names, long now, long soonMs) {
        List<Prog> out = new ArrayList<>();
        if (names == null || names.isEmpty() || !have(c)) return out;
        loadStreams(c);
        String[] ch = null;
        for (String n : names) {
            ch = streams.get(base(n));
            if (ch != null) break;
        }
        if (ch == null) return out;
        return around(guide(c, ch[1]), now, soonMs);
    }

    /** The banner's channel (see named) among the playlist's channels read so far; null when none or not read yet. */
    static String bannerChannel(List<String> texts) {
        return named(texts, streams);
    }

    private static synchronized List<Prog> guide(Context c, String streamId) {
        Long at = guidesAt.get(streamId);
        long now = System.currentTimeMillis();
        if (at != null && now - at < GUIDE_FRESH_MS) return guides.get(streamId);
        List<Prog> got = new ArrayList<>();
        try {
            got = parseShortEpg(read(c, "get_short_epg&stream_id=" + URLEncoder.encode(streamId, "UTF-8") + "&limit=6"));
        } catch (Exception e) {
            android.util.Log.i("Listings", "the playlist's guide not read: " + e.getClass().getSimpleName());
        }
        guides.put(streamId, got);
        guidesAt.put(streamId, now);
        return got;
    }

    /** The playlist's live channels' names and stream ids, read as a stream (a big playlist has tens of thousands). */
    private static synchronized void loadStreams(Context c) {
        long now = System.currentTimeMillis();
        if (!streams.isEmpty() && now - streamsAt < STREAMS_FRESH_MS) return;
        if (streams.isEmpty() && streamsAt != 0 && now - streamsAt < 10 * 60_000L) return;
        streamsAt = now;
        Map<String, String[]> out = new HashMap<>();
        HttpURLConnection con = null;
        try {
            con = open(c, "get_live_streams");
            try (android.util.JsonReader r = new android.util.JsonReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                r.beginArray();
                while (r.hasNext()) {
                    String name = null, id = null;
                    r.beginObject();
                    while (r.hasNext()) {
                        String k = r.nextName();
                        if (r.peek() == android.util.JsonToken.NULL) { r.nextNull(); continue; }
                        if ("name".equals(k)) name = r.nextString();
                        else if ("stream_id".equals(k)) id = r.nextString();
                        else r.skipValue();
                    }
                    r.endObject();
                    if (name == null || id == null) continue;
                    String b = base(name);
                    if (!b.isEmpty() && !out.containsKey(b)) out.put(b, new String[]{name.trim(), id});
                }
            }
            android.util.Log.i("Listings", "the playlist's channels read: " + out.size());
        } catch (Exception e) {
            android.util.Log.i("Listings", "the playlist's channels not read: " + e.getClass().getSimpleName());
        } finally {
            if (con != null) con.disconnect();
        }
        if (!out.isEmpty()) streams = out;
    }

    private static String read(Context c, String action) throws Exception {
        HttpURLConnection con = open(c, action);
        try (java.io.InputStream in = con.getInputStream()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && b.size() < 2 * 1024 * 1024) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }

    private static HttpURLConnection open(Context c, String action) throws Exception {
        String server = Config.themeString(c, "iptv_server", "").replaceAll("/+$", "");
        URL u = new URL(server + "/player_api.php?username=" + URLEncoder.encode(Config.themeString(c, "iptv_user", ""), "UTF-8")
                + "&password=" + URLEncoder.encode(Config.themeString(c, "iptv_pass", ""), "UTF-8") + "&action=" + action);
        HttpURLConnection con = (HttpURLConnection) u.openConnection();
        con.setConnectTimeout(8_000);
        con.setReadTimeout(20_000);
        con.setRequestProperty("User-Agent", "TVHomeLauncher/1.0 (TV launcher)");
        return con;
    }
}
