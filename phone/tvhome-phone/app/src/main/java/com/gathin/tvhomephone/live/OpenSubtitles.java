package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.tvhomephone.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * OpenSubtitles.com for films and episodes the playlist plays without subtitles of their own: a search by the TMDB id
 * (the series' with its season and episode) in the family's languages, and a download of the one chosen. Both need the
 * family's own free API key from opensubtitles.com (without one, only searches it already has to hand are answered),
 * and signing in with their account there (optional) gives more downloads a day. The key, the password and the sign-in's token stay in this
 * phone's own settings and are never written to the log. Call off the main thread.
 */
public final class OpenSubtitles {
    private OpenSubtitles() {}

    private static final String HOST = "api.opensubtitles.com", PREFS = "opensubtitles";
    /** A sign-in is used again for this long before signing in afresh. */
    private static final long TOKEN_FOR = 20 * 3_600_000L;
    private static final int TIMEOUT = 15_000;
    static final String NEEDS_KEY = "To find subtitles on OpenSubtitles, add your free API key in Settings, Subtitles";
    /** No file bigger than this is taken (a subtitle file is tens of kilobytes). */
    private static final int MAX_FILE = 4 * 1024 * 1024;

    /** Why it didn't work, in words for the player's message. */
    public static final class Failed extends IOException {
        public Failed(String words) { super(words); }
    }

    /** A file downloaded: where it was put and how many downloads today are left (-1: not said). */
    public static final class Got {
        public final File file;
        public final int remaining;

        Got(File file, int remaining) {
            this.file = file;
            this.remaining = remaining;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- the account ----------------------------------------------------------------------------------------------

    public static boolean hasKey(Context c) { return !prefs(c).getString("key", "").isEmpty(); }

    public static String user(Context c) { return prefs(c).getString("user", ""); }

    /** The API key and (both empty: none) the account, the old sign-in let go. */
    public static void setAccount(Context c, String key, String user, String password) {
        SharedPreferences.Editor e = prefs(c).edit().remove("token").remove("token_at").remove("base");
        if (key != null) e.putString("key", key.trim());
        if (user != null) e.putString("user", user.trim());
        if (password != null) e.putString("pass", password);
        e.apply();
    }

    public static void forget(Context c) { prefs(c).edit().clear().apply(); }

    /** Pure: the sign-in's server (a VIP account's own) when it is OpenSubtitles', the usual one otherwise. */
    static String host(String fromLogin) {
        if (fromLogin == null) return HOST;
        String h = fromLogin.trim().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceAll("/.*$", "");
        return h.equals("opensubtitles.com") || h.endsWith(".opensubtitles.com") ? h : HOST;
    }

    private static String agent() { return "Homefield v" + BuildConfig.VERSION_NAME; }

    /** Signs in (when there is an account and the last sign-in is old): its token, or "" without an account. */
    private static String token(Context c, boolean fresh) throws IOException {
        SharedPreferences p = prefs(c);
        String user = p.getString("user", ""), pass = p.getString("pass", "");
        if (user.isEmpty() || pass.isEmpty()) return "";
        String t = p.getString("token", "");
        if (!fresh && !t.isEmpty() && System.currentTimeMillis() - p.getLong("token_at", 0) < TOKEN_FOR) return t;
        JSONObject body = new JSONObject();
        try {
            body.put("username", user).put("password", pass);
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

    /** Checks the key and account in Settings: how many downloads a day they give (-1 when it isn't said). */
    public static int check(Context c) throws IOException {
        if (!hasKey(c)) throw new Failed("Add the API key first");
        String t = token(c, true);
        if (t.isEmpty()) {
            // No account: the key on its own, tried on the list of languages.
            Reply r = send(c, "GET", "https://" + HOST + "/api/v1/infos/languages", null, "");
            if (r.code == 401 || r.code == 403) throw new Failed("OpenSubtitles didn't accept the API key");
            if (r.code >= 400) throw failure(r);
            return -1;
        }
        Reply r = send(c, "GET", "https://" + base(c) + "/api/v1/infos/user", null, t);
        if (r.code >= 400) return -1;
        try {
            return new JSONObject(r.text()).optJSONObject("data").optInt("allowed_downloads", -1);
        } catch (JSONException | NullPointerException e) {
            return -1;
        }
    }

    private static String base(Context c) { return host(prefs(c).getString("base", HOST)); }

    // ---- search and download --------------------------------------------------------------------------------------

    /** The subtitles OpenSubtitles has for it (Subtitles.query's terms), as they came. */
    public static List<Subtitles.Result> search(Context c, int tmdb, boolean tv, int season, int episode, String name,
                                                List<String> languages) throws IOException {
        if (tmdb <= 0 && Subtitles.searchName(name).isEmpty()) throw new Failed("Nothing to look for it by");
        String q = Subtitles.query(tmdb, tv, season, episode, name, languages);
        Reply r = send(c, "GET", "https://" + HOST + "/api/v1/subtitles?" + q, null, "");
        if ((r.code == 401 || r.code == 403) && !hasKey(c)) throw new Failed(NEEDS_KEY);
        if (r.code >= 400) throw failure(r);
        return Subtitles.results(r.text());
    }

    /** That file downloaded (one of the day's downloads), in UTF-8, into the phone's subtitles folder. */
    public static Got download(Context c, Subtitles.Result result) throws IOException {
        if (!hasKey(c)) throw new Failed(NEEDS_KEY);
        File out = new File(Subtitles.folder(c), result.fileId + ".sub");
        String t = token(c, false);
        JSONObject body = new JSONObject();
        try {
            body.put("file_id", result.fileId);
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
        int remaining;
        try {
            JSONObject o = new JSONObject(r.text());
            link = o.optString("link");
            remaining = o.has("remaining") ? o.optInt("remaining", -1) : -1;
        } catch (JSONException e) {
            throw new Failed("OpenSubtitles sent something odd back");
        }
        if (!link.startsWith("https://")) throw new Failed("OpenSubtitles sent no file");
        Reply f = send(c, "GET", link, null, null);
        if (f.code >= 400 || f.body.length == 0) throw new Failed("The file wouldn't download");
        byte[] text = Subtitles.utf8(f.body, result.lang);
        try (OutputStream o = new FileOutputStream(out)) {
            o.write(text);
        }
        return new Got(out, remaining);
    }

    /** Pure: what to tell the family about a reply that didn't work. */
    static String words(int code, String message) {
        String m = message == null ? "" : message.trim();
        switch (code) {
            case 401: case 403: return "OpenSubtitles didn't accept the API key or account (Settings)";
            case 406: return m.isEmpty() ? "That's all of today's OpenSubtitles downloads" : "OpenSubtitles: " + m;
            case 429: return "OpenSubtitles is busy: try again in a moment";
            case 404: return "OpenSubtitles doesn't have that one any more";
            default: return code >= 500 ? "OpenSubtitles isn't answering right now" : m.isEmpty() ? "OpenSubtitles said no (" + code + ")" : "OpenSubtitles: " + m;
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
        return new Failed(words(r.code, m));
    }

    // ---- the line to it -------------------------------------------------------------------------------------------

    private static final class Reply {
        final int code;
        final byte[] body;

        Reply(int code, byte[] body) {
            this.code = code;
            this.body = body;
        }

        String text() { return new String(body, StandardCharsets.UTF_8); }
    }

    /** One request (token null: none of OpenSubtitles' headers, for the file's own link). Never logs what it sends. */
    private static Reply send(Context c, String method, String url, JSONObject body, String token) throws IOException {
        HttpURLConnection h;
        try {
            h = (HttpURLConnection) new URL(url).openConnection();
        } catch (IOException e) {
            throw new Failed("OpenSubtitles couldn't be reached");
        }
        h.setConnectTimeout(TIMEOUT);
        h.setReadTimeout(TIMEOUT);
        h.setRequestMethod(method);
        h.setRequestProperty("User-Agent", agent());
        h.setRequestProperty("Accept", "application/json");
        if (token != null) {
            String key = prefs(c).getString("key", "");
            if (!key.isEmpty()) h.setRequestProperty("Api-Key", key);
            if (!token.isEmpty()) h.setRequestProperty("Authorization", "Bearer " + token);
        }
        try {
            if (body != null) {
                byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/json");
                h.setFixedLengthStreamingMode(b.length);
                try (OutputStream o = h.getOutputStream()) {
                    o.write(b);
                }
            }
            int code = h.getResponseCode();
            InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
            return new Reply(code, in == null ? new byte[0] : read(in));
        } catch (IOException e) {
            throw new Failed("OpenSubtitles couldn't be reached");
        } finally {
            h.disconnect();
        }
    }

    private static byte[] read(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = s.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() > MAX_FILE) throw new IOException("too big");
            }
            return out.toByteArray();
        }
    }
}
