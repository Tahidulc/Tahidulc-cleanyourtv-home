package com.gathin.homefieldtv.subs;

import android.content.Context;

import com.gathin.homefieldtv.engine.Bounded;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * OpenSubtitles.com for films and episodes, as the phone's: a search by the title's IMDb or TMDB id (the series' with the
 * season and episode for an episode; its name and year when neither is known) in the family's languages, the results in
 * the order offered (the family's languages first, then hearing-impaired as Settings say, trusted uploaders, the most
 * downloaded), and a download of the one chosen. Both need the family's own API key from opensubtitles.com, and signing in
 * with their account there gives more downloads a day (Settings › Other › Subtitles).
 *
 * <p>The requests carry only what OpenSubtitles needs: the key, the sign-in's token, the app's name and version as its
 * user agent, and what is looked for; the login, key and token are never written to the log or put in an error. Every
 * request has its timeouts and an answer read only up to a limit (Bounded). Off the main thread; the tests put a fake
 * line in (http) and never reach the real API.
 */
public final class OpenSubtitles {
    private OpenSubtitles() {}

    static final String HOST = "api.opensubtitles.com";
    static final int TIMEOUT_MS = 15_000, MAX_ANSWER = 4 << 20;
    /** A sign-in is used again for this long before signing in afresh. */
    static final long TOKEN_FOR = 20 * 3_600_000L;
    public static final String NEEDS_KEY = "Add your OpenSubtitles API key in Settings › Other › Subtitles";

    /** Why it didn't work, in words for the player (never the key, the login or an address with them in it). */
    public static final class Failed extends IOException {
        public Failed(String words) {
            super(words);
        }
    }

    /** The line to OpenSubtitles: the real one on the TV, a fake in the tests. */
    public interface Http {
        Reply send(String method, String url, Map<String, String> headers, byte[] body) throws IOException;
    }

    public static final class Reply {
        public final int code;
        public final byte[] body;

        public Reply(int code, byte[] body) {
            this.code = code;
            this.body = body == null ? new byte[0] : body;
        }

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    public static volatile Http http = OpenSubtitles::real;
    /** The app's name and version for the user agent (set once by the app; nothing about the family). */
    public static volatile String version = "1.0";

    static String agent() {
        return "HomefieldTV v" + version;
    }

    // ---- what is looked for ---------------------------------------------------------------------------------------------

    /** What a search is for: a film, or an episode of a series (its season and number), by its ids or its name and year. */
    public static final class Ask {
        public final String imdb;
        public final int tmdb, year, season, number;
        public final boolean episode;
        public final String name;

        public Ask(String imdb, int tmdb, boolean episode, int season, int number, String name, int year) {
            this.imdb = imdb == null ? "" : imdb;
            this.tmdb = tmdb;
            this.episode = episode;
            this.season = season;
            this.number = number;
            this.name = name == null ? "" : name;
            this.year = year;
        }

        /** What it is looked for by, in words: "by its IMDb id", "by its TMDB id", "by its name". */
        public String by() {
            return !imdbNumber(imdb).isEmpty() ? "by its IMDb id" : tmdb > 0 ? "by its TMDB id" : "by its name";
        }
    }

    /** Pure: an IMDb id as OpenSubtitles wants it: its number without "tt" or the zeros before it ("" for none). */
    static String imdbNumber(String imdb) {
        if (imdb == null) return "";
        String s = imdb.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("tt")) s = s.substring(2);
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return "";
        int z = 0;
        while (z < s.length() - 1 && s.charAt(z) == '0') z++;
        s = s.substring(z);
        return s.equals("0") ? "" : s;
    }

    /**
     * Pure: the search's query (after "/subtitles?"), its parameters in alphabetical order and their values in lower case
     * as OpenSubtitles asks (any other order is sent round by a redirect). By the IMDb id, else the TMDB id (the series'
     * for an episode), else by its name and year; the languages as a sorted list (none: every language).
     */
    public static String query(Ask a, List<String> languages) {
        Map<String, String> p = new TreeMap<>();
        String imdb = imdbNumber(a.imdb);
        if (a.episode) {
            if (a.season > 0) p.put("season_number", String.valueOf(a.season));
            if (a.number > 0) p.put("episode_number", String.valueOf(a.number));
            if (!imdb.isEmpty()) p.put("parent_imdb_id", imdb);
            else if (a.tmdb > 0) p.put("parent_tmdb_id", String.valueOf(a.tmdb));
            else p.put("query", searchName(a.name));
            p.put("type", "episode");
        } else {
            if (!imdb.isEmpty()) p.put("imdb_id", imdb);
            else if (a.tmdb > 0) p.put("tmdb_id", String.valueOf(a.tmdb));
            else {
                p.put("query", searchName(a.name));
                if (a.year > 1900) p.put("year", String.valueOf(a.year));
            }
            p.put("type", "movie");
        }
        if (languages != null && !languages.isEmpty()) {
            List<String> l = new ArrayList<>();
            for (String s : languages) {
                String v = s.toLowerCase(Locale.ROOT);
                if (!l.contains(v)) l.add(v);
            }
            Collections.sort(l);
            p.put("languages", String.join(",", l));
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (b.length() > 0) b.append('&');
            b.append(e.getKey()).append('=').append(enc(e.getValue().toLowerCase(Locale.ROOT)));
        }
        return b.toString();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("%2C", ",");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    /** Pure: a playlist's name for a title as a search wants it: no country tag, quality, year or brackets. */
    public static String searchName(String name) {
        if (name == null) return "";
        String s = name.replaceAll("^\\s*(\\|[^|]*\\||\\[[^\\]]*\\]|[A-Z]{2,3}\\s*[:|-])\\s*", "");
        s = s.replaceAll("\\(\\s*(19|20)\\d\\d\\s*\\)", " ").replaceAll("\\[[^\\]]*\\]", " ");
        s = s.replaceAll("(?i)\\b(4k|uhd|fhd|hd|sd|hevc|x265|x264|h\\.?26[45]|2160p|1080p|720p|480p|multi|sub|subs|dub|dubbed)\\b", " ");
        s = s.replaceAll("\\b(19|20)\\d\\d\\s*$", " ").replaceAll("[|•·]", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    // ---- what comes back --------------------------------------------------------------------------------------------------

    /** One subtitle file OpenSubtitles has for it. */
    public static final class Result {
        public long fileId;
        public String lang = "", release = "", fileName = "";
        public int downloads;
        public double fps;
        public boolean hearingImpaired, trusted, machine, foreignOnly;

        /** Pure: a result from its JSON in the search's answer (null when it has no file). */
        static Result from(JSONObject item) {
            JSONObject a = item == null ? null : item.optJSONObject("attributes");
            if (a == null) return null;
            JSONArray files = a.optJSONArray("files");
            if (files == null || files.length() == 0) return null;
            JSONObject f = files.optJSONObject(0);
            if (f == null || f.optLong("file_id") <= 0) return null;
            Result r = new Result();
            r.fileId = f.optLong("file_id");
            r.fileName = f.optString("file_name");
            r.lang = a.optString("language");
            r.release = a.optString("release");
            if (r.release.isEmpty()) r.release = r.fileName;
            r.downloads = Math.max(a.optInt("download_count"), a.optInt("new_download_count"));
            r.fps = a.optDouble("fps", 0);
            if (Double.isNaN(r.fps)) r.fps = 0;
            r.hearingImpaired = a.optBoolean("hearing_impaired");
            r.trusted = a.optBoolean("from_trusted");
            r.machine = a.optBoolean("machine_translated") || a.optBoolean("ai_translated");
            r.foreignOnly = a.optBoolean("foreign_parts_only");
            return r;
        }

        /** The player's line for it: "English · Hearing impaired · 12.3k downloads". */
        public String words() {
            StringBuilder b = new StringBuilder(Langs.name(lang));
            if (hearingImpaired) b.append("  ·  Hearing impaired");
            if (foreignOnly) b.append("  ·  Foreign parts only");
            if (machine) b.append("  ·  Machine translated");
            if (downloads > 0) b.append("  ·  ").append(count(downloads)).append(downloads == 1 ? " download" : " downloads");
            if (trusted) b.append("  ·  Trusted");
            return b.toString();
        }
    }

    /** Pure: "980", "12.3k", "1.2M". */
    static String count(int n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return trimmed(n / 1000.0) + "k";
        return trimmed(n / 1_000_000.0) + "M";
    }

    private static String trimmed(double v) {
        String s = String.format(Locale.UK, v < 100 ? "%.1f" : "%.0f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Pure: the results in the search's answer, as they came. */
    public static List<Result> results(String body) {
        List<Result> out = new ArrayList<>();
        try {
            JSONArray d = new JSONObject(body).optJSONArray("data");
            if (d == null) return out;
            for (int i = 0; i < d.length(); i++) {
                Result r = Result.from(d.optJSONObject(i));
                if (r != null) out.add(r);
            }
        } catch (JSONException ignored) {
            // nothing usable
        }
        return out;
    }

    /**
     * Pure: the results in the order offered, best first: the family's languages in their order (any other after), not
     * machine translated, foreign parts only last, hearing-impaired as wanted, trusted uploaders, a release named with the
     * picture's quality ("1080p"), then the most downloaded.
     */
    public static List<Result> rank(List<Result> in, List<String> languages, boolean wantHi, String quality) {
        List<Result> out = new ArrayList<>(in);
        out.sort((a, b) -> {
            int c = Integer.compare(Langs.rank(a.lang, languages), Langs.rank(b.lang, languages));
            if (c != 0) return c;
            c = Boolean.compare(a.foreignOnly, b.foreignOnly);
            if (c != 0) return c;
            c = Boolean.compare(a.machine, b.machine);
            if (c != 0) return c;
            c = Integer.compare(score(b, wantHi, quality), score(a, wantHi, quality));
            if (c != 0) return c;
            return Integer.compare(b.downloads, a.downloads);
        });
        return out;
    }

    private static int score(Result r, boolean wantHi, String quality) {
        int s = 0;
        if (r.hearingImpaired == wantHi) s += 2;
        if (qualityFits(r.release, quality)) s += 2;
        if (r.trusted) s += 1;
        return s;
    }

    /** Pure: whether a release's name says the same quality as the copy playing ("1080p" in both). */
    static boolean qualityFits(String release, String quality) {
        if (release == null || quality == null || quality.isEmpty()) return false;
        String r = release.toLowerCase(Locale.ROOT);
        switch (quality) {
            case "4K": return r.contains("2160p") || r.contains("4k") || r.contains("uhd");
            case "1080p": return r.contains("1080");
            case "720p": return r.contains("720");
            case "SD": return r.contains("480") || r.contains("dvdrip") || r.contains("xvid");
            default: return false;
        }
    }

    // ---- asking ---------------------------------------------------------------------------------------------------------

    /** The subtitles OpenSubtitles has for it, in the family's languages, as they came (rank() orders them). */
    public static List<Result> search(Context c, Ask a, List<String> languages) throws IOException {
        if (!SubsPrefs.hasKey(c)) throw new Failed(NEEDS_KEY);
        if (imdbNumber(a.imdb).isEmpty() && a.tmdb <= 0 && searchName(a.name).isEmpty()) throw new Failed("Nothing to look for it by");
        Reply r = send(c, "GET", "https://" + HOST + "/api/v1/subtitles?" + query(a, languages), null, "");
        if (r.code >= 400) throw failure(r);
        return results(r.text());
    }

    /** That file downloaded (one of the day's downloads), its writing in UTF-8. */
    public static byte[] download(Context c, Result result) throws IOException {
        if (!SubsPrefs.hasKey(c)) throw new Failed(NEEDS_KEY);
        String t = token(c, false);
        byte[] body;
        try {
            body = new JSONObject().put("file_id", result.fileId).toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new Failed("Couldn't ask for that one");
        }
        Reply r = send(c, "POST", "https://" + base(c) + "/api/v1/download", body, t);
        if (r.code == 401 && !t.isEmpty()) {
            // The sign-in ran out: once more, signed in afresh.
            t = token(c, true);
            r = send(c, "POST", "https://" + base(c) + "/api/v1/download", body, t);
        }
        if (r.code >= 400) throw failure(r);
        String link;
        try {
            link = new JSONObject(r.text()).optString("link");
        } catch (JSONException e) {
            throw new Failed("OpenSubtitles sent something odd back");
        }
        if (!link.startsWith("https://")) throw new Failed("OpenSubtitles sent no file");
        Reply f = send(c, "GET", link, null, null);
        if (f.code >= 400 || f.body.length == 0) throw new Failed("The file wouldn't download");
        return SubtitleFile.utf8(f.body, result.lang);
    }

    /** Settings' check of the key and the account: how many downloads a day they give (-1 when it isn't said). */
    public static int check(Context c) throws IOException {
        if (!SubsPrefs.hasKey(c)) throw new Failed("Add the API key first");
        String t = token(c, true);
        if (t.isEmpty()) {
            // No account: the key on its own, tried on the list of languages.
            Reply r = send(c, "GET", "https://" + HOST + "/api/v1/infos/languages", null, "");
            if (r.code >= 400) throw failure(r);
            return -1;
        }
        Reply r = send(c, "GET", "https://" + base(c) + "/api/v1/infos/user", null, t);
        if (r.code >= 400) return -1;
        try {
            JSONObject d = new JSONObject(r.text()).optJSONObject("data");
            return d == null ? -1 : d.optInt("allowed_downloads", -1);
        } catch (JSONException e) {
            return -1;
        }
    }

    /** Pure: the sign-in's server (a VIP account's own) when it is OpenSubtitles', the usual one otherwise. */
    static String host(String fromLogin) {
        if (fromLogin == null) return HOST;
        String h = fromLogin.trim().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceAll("/.*$", "");
        return h.equals("opensubtitles.com") || h.endsWith(".opensubtitles.com") ? h : HOST;
    }

    private static String base(Context c) {
        return host(SubsPrefs.account(c).getString("base", HOST));
    }

    /** Signs in (when there is an account and the last sign-in is old): its token, or "" without an account. */
    static String token(Context c, boolean fresh) throws IOException {
        android.content.SharedPreferences p = SubsPrefs.account(c);
        String user = SubsPrefs.user(c), pass = SubsPrefs.password(c);
        if (user.isEmpty() || pass.isEmpty()) return "";
        String t = p.getString("token", "");
        if (!fresh && !t.isEmpty() && System.currentTimeMillis() - p.getLong("token_at", 0) < TOKEN_FOR) return t;
        byte[] body;
        try {
            body = new JSONObject().put("username", user).put("password", pass).toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new Failed("Couldn't sign in to OpenSubtitles");
        }
        Reply r = send(c, "POST", "https://" + HOST + "/api/v1/login", body, "");
        if (r.code == 401 || r.code == 400) throw new Failed("OpenSubtitles didn't accept the account's name or password (Settings)");
        if (r.code >= 400) throw failure(r);
        try {
            JSONObject o = new JSONObject(r.text());
            t = o.optString("token");
            if (t.isEmpty()) throw new Failed("Couldn't sign in to OpenSubtitles");
            p.edit().putString("token", t).putLong("token_at", System.currentTimeMillis())
                    .putString("base", host(o.optString("base_url"))).apply();
            return t;
        } catch (JSONException e) {
            throw new Failed("Couldn't sign in to OpenSubtitles");
        }
    }

    /** Pure: what to tell the family about an answer that didn't work. */
    static String words(int code, String message) {
        String m = message == null ? "" : message.trim();
        switch (code) {
            case 401: case 403: return "OpenSubtitles didn't accept the API key or the account (Settings › Other › Subtitles)";
            case 406: return m.isEmpty() ? "That's all of today's OpenSubtitles downloads" : "OpenSubtitles: " + m;
            case 429: return "OpenSubtitles is busy: try again in a moment";
            case 404: return "OpenSubtitles doesn't have that one any more";
            default:
                return code >= 500 ? "OpenSubtitles isn't answering right now"
                        : m.isEmpty() ? "OpenSubtitles said no (" + code + ")" : "OpenSubtitles: " + m;
        }
    }

    private static Failed failure(Reply r) {
        String m = "";
        try {
            JSONObject o = new JSONObject(r.text());
            m = o.optString("message");
            if (m.isEmpty() && o.optJSONArray("errors") != null) m = o.optJSONArray("errors").optString(0);
        } catch (JSONException ignored) {
            // not JSON
        }
        // Never more than a short sentence of what it said.
        if (m.length() > 120) m = m.substring(0, 120);
        return new Failed(words(r.code, m));
    }

    // ---- the line to it ---------------------------------------------------------------------------------------------------

    /** One request (token null: none of OpenSubtitles' headers, for the file's own link). Never logs what it sends. */
    private static Reply send(Context c, String method, String url, byte[] body, String token) throws IOException {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("User-Agent", agent());
        h.put("Accept", "application/json");
        if (token != null) {
            String key = SubsPrefs.key(c);
            if (!key.isEmpty()) h.put("Api-Key", key);
            if (!token.isEmpty()) h.put("Authorization", "Bearer " + token);
        }
        if (body != null) h.put("Content-Type", "application/json");
        try {
            return http.send(method, url, h, body);
        } catch (Failed f) {
            throw f;
        } catch (IOException | RuntimeException e) {
            throw new Failed("OpenSubtitles couldn't be reached");
        }
    }

    /** The real line: HttpURLConnection with its timeouts, the answer read only up to MAX_ANSWER. */
    private static Reply real(String method, String url, Map<String, String> headers, byte[] body) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(TIMEOUT_MS);
        h.setReadTimeout(TIMEOUT_MS);
        h.setInstanceFollowRedirects(true);
        h.setRequestMethod(method);
        for (Map.Entry<String, String> e : headers.entrySet()) h.setRequestProperty(e.getKey(), e.getValue());
        try {
            if (body != null) {
                h.setDoOutput(true);
                h.setFixedLengthStreamingMode(body.length);
                try (OutputStream o = h.getOutputStream()) {
                    o.write(body);
                }
            }
            int code = h.getResponseCode();
            InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
            if (in == null) return new Reply(code, new byte[0]);
            try (InputStream s = in) {
                return new Reply(code, Bounded.bytes(s, h.getContentLengthLong(), MAX_ANSWER));
            }
        } finally {
            h.disconnect();
        }
    }
}
