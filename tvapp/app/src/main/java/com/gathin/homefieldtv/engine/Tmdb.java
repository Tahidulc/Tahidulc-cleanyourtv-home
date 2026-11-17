package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TMDB, for the posters, stories and episode names of the family's own TorBox titles that no playlist has, as the phone
 * finds them. Its key comes from the launcher on this TV (its provider's "tmdb", answered only for an app signed as the
 * launcher is): kept in memory only, never shown, written to the log or put in an error, and sent to TMDB alone. Each
 * title is asked about once: what TMDB said (or that it has no such title) is kept on this TV, so the rows come at once
 * and work without it. Without a launcher that gives the key, nothing is asked and the posters are drawn. Off the main
 * thread.
 */
public final class Tmdb {
    private Tmdb() {}

    private static final String API = "https://api.themoviedb.org/3/", IMAGES = "https://image.tmdb.org/t/p/";
    private static final String PROVIDER = "content://com.gathin.tvhome.sync";
    /** What TMDB said kept this long; that it had nothing, less long (it may have it later). */
    private static final long KEEP_MS = 14L * 24 * 3600_000L, NONE_MS = 3L * 24 * 3600_000L;
    /** A launcher that gave no key asked again after this long (it may have been updated). */
    private static final long ASK_AGAIN_MS = 10 * 60_000L;

    private static volatile String key;
    private static volatile long keyAskedAt;

    /** The launcher's key for TMDB, asked of it now and then while it has given none; null without one. */
    private static String key(Context c) {
        String k = key;
        if (k != null) return k;
        long now = System.currentTimeMillis();
        if (keyAskedAt != 0 && now - keyAskedAt < ASK_AGAIN_MS) return null;
        keyAskedAt = now;
        try {
            Bundle b = c.getApplicationContext().getContentResolver().call(Uri.parse(PROVIDER), "tmdb", null, null);
            String got = b == null ? null : b.getString("key");
            if (got != null && !got.trim().isEmpty()) key = got.trim();
        } catch (RuntimeException e) {
            // no launcher on this TV, or one from before it gave the key: the posters are drawn
        }
        return key;
    }

    /** Whether TMDB can be asked (the launcher gave its key). */
    public static boolean ready(Context c) {
        return key(c) != null;
    }

    /** Previews and tests: as if the launcher had (or hadn't) a key, without asking it. */
    static void forget() {
        key = null;
        keyAskedAt = 0;
    }

    // ---- a title ----------------------------------------------------------------------------------------------------

    /** What TMDB says of a title: its id (0: it has no such title), its name, year, pictures, story and rating. */
    public static final class Match {
        public final int id;
        public final String name, year, poster, backdrop, plot, rating;

        Match(int id, String name, String year, String poster, String backdrop, String plot, String rating) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.year = year == null ? "" : year;
            this.poster = poster == null ? "" : poster;
            this.backdrop = backdrop == null ? "" : backdrop;
            this.plot = plot == null ? "" : plot;
            this.rating = rating == null ? "" : rating;
        }
    }

    static final Match NONE = new Match(0, "", "", "", "", "", "");

    /** What TMDB said of a title before (NONE: it had none such), or null when it hasn't been asked (or long ago). */
    static Match cached(Context c, boolean tv, String name, int year) {
        JSONObject j = read(file(c, "t|" + (tv ? "tv" : "movie") + "|" + TorBox.squashed(name) + "|" + year));
        if (j == null) return null;
        long age = System.currentTimeMillis() - j.optLong("at");
        int id = j.optInt("id");
        if (age < 0 || age > (id > 0 ? KEEP_MS : NONE_MS)) return null;
        if (id <= 0) return NONE;
        return new Match(id, j.optString("name"), j.optString("year"), j.optString("poster"), j.optString("backdrop"),
                j.optString("plot"), j.optString("rating"));
    }

    /** Whether a title has been asked about (and the answer is still kept). */
    static boolean asked(Context c, boolean tv, String name, int year) {
        return cached(c, tv, name, year) != null;
    }

    /**
     * What TMDB says of a title: kept from before, else asked now (with the launcher's key): by its name and year, then
     * by its name alone; only an answer whose name is the title's counts. Null when it couldn't be asked.
     */
    static Match find(Context c, boolean tv, String name, int year) {
        Match m = cached(c, tv, name, year);
        if (m != null) return m;
        String k = key(c);
        if (k == null) return null;
        try {
            String path = (tv ? "search/tv?query=" : "search/movie?query=") + enc(name) + "&include_adult=false";
            m = year > 0 ? pick(get(k, path + (tv ? "&first_air_date_year=" : "&year=") + year), tv, name, year) : NONE;
            if (m == NONE) m = pick(get(k, path), tv, name, year);
        } catch (IOException | JSONException e) {
            return null;
        }
        JSONObject j = new JSONObject();
        try {
            j.put("at", System.currentTimeMillis()).put("id", m.id).put("name", m.name).put("year", m.year).put("poster", m.poster)
                    .put("backdrop", m.backdrop).put("plot", m.plot).put("rating", m.rating);
        } catch (JSONException ignored) {
            // kept as far as it went
        }
        write(file(c, "t|" + (tv ? "tv" : "movie") + "|" + TorBox.squashed(name) + "|" + year), j.toString());
        return m;
    }

    /**
     * Pure: the best of a search's answers: one whose name (or original name) is the title's, with the year wanted where
     * one was (a year either side; any year for a series, whose download may say a later season's), the first such;
     * NONE when none is.
     */
    static Match pick(JSONObject root, boolean tv, String name, int year) {
        JSONArray rs = root == null ? null : root.optJSONArray("results");
        Match loose = null;
        for (int i = 0; rs != null && i < rs.length(); i++) {
            JSONObject r = rs.optJSONObject(i);
            if (r == null) continue;
            String title = r.optString(tv ? "name" : "title", ""), original = r.optString(tv ? "original_name" : "original_title", "");
            if (!TorBox.sameTitle(title, name, null) && !TorBox.sameTitle(original, name, null)) continue;
            String date = r.optString(tv ? "first_air_date" : "release_date", "");
            int y = date.length() >= 4 && date.substring(0, 4).matches("\\d{4}") ? Integer.parseInt(date.substring(0, 4)) : 0;
            Match m = new Match(r.optInt("id"), title, y > 0 ? String.valueOf(y) : "", image(r.optString("poster_path", ""), "w342"),
                    image(r.optString("backdrop_path", ""), "w1280"), r.optString("overview", ""), rating(r.optDouble("vote_average", 0)));
            if (m.id <= 0) continue;
            if (year == 0 || y == 0 || Math.abs(y - year) <= 1) return m;
            if (tv && loose == null) loose = m;
        }
        return loose != null ? loose : NONE;
    }

    /** Pure: a TMDB picture's address at a width ("w342"), or "" for none. */
    static String image(String path, String size) {
        if (path == null || path.isEmpty() || path.equals("null")) return "";
        return IMAGES + size + (path.startsWith("/") ? "" : "/") + path;
    }

    /** Pure: TMDB's score as the posters show one ("7.4"), "" for none. */
    static String rating(double v) {
        return v > 0 && v <= 10 ? String.format(Locale.UK, "%.1f", v) : "";
    }

    // ---- a title's page -------------------------------------------------------------------------------------------

    /** A film's or series' page as TMDB has it (its credits and videos with it), kept a fortnight; null when not to be had. */
    static JSONObject details(Context c, boolean tv, int id) {
        return kept(c, "d|" + (tv ? "tv" : "movie") + "|" + id, (tv ? "tv/" : "movie/") + id + "?append_to_response=credits,videos");
    }

    /** A series' season as TMDB has it (its episodes' names, stories and stills), kept a fortnight; null when not to be had. */
    static JSONObject season(Context c, int id, int number) {
        return kept(c, "s|" + id + "|" + number, "tv/" + id + "/season/" + number);
    }

    private static JSONObject kept(Context c, String name, String path) {
        File f = file(c, name);
        JSONObject had = read(f);
        if (had != null && System.currentTimeMillis() - had.optLong("_at") < KEEP_MS) return had;
        String k = key(c);
        if (k == null) return had;
        try {
            JSONObject j = get(k, path);
            j.put("_at", System.currentTimeMillis());
            write(f, j.toString());
            return j;
        } catch (IOException | JSONException e) {
            return had;
        }
    }

    /**
     * Pure: a page's details from TMDB's answer: its story, genres, cast, director, date, pictures, trailer, rating and
     * running time, with the seasons given (a film's: none); what TMDB lacks from the title itself.
     */
    static Catalog.Details detailsOf(JSONObject j, Catalog.Title t, List<Catalog.Season> seasons) {
        boolean tv = t.series;
        List<String> genres = new ArrayList<>(), cast = new ArrayList<>();
        JSONArray g = j.optJSONArray("genres");
        for (int i = 0; g != null && i < g.length(); i++) {
            JSONObject o = g.optJSONObject(i);
            if (o != null && !o.optString("name").isEmpty()) genres.add(o.optString("name"));
        }
        JSONObject credits = j.optJSONObject("credits");
        JSONArray people = credits == null ? null : credits.optJSONArray("cast");
        for (int i = 0; people != null && i < people.length() && cast.size() < 8; i++) {
            JSONObject o = people.optJSONObject(i);
            if (o != null && !o.optString("name").isEmpty()) cast.add(o.optString("name"));
        }
        String director = "";
        JSONArray crew = credits == null ? null : credits.optJSONArray("crew");
        for (int i = 0; crew != null && i < crew.length() && director.isEmpty(); i++) {
            JSONObject o = crew.optJSONObject(i);
            if (o != null && "Director".equals(o.optString("job"))) director = o.optString("name");
        }
        if (tv && director.isEmpty()) {
            JSONArray made = j.optJSONArray("created_by");
            if (made != null && made.length() > 0 && made.optJSONObject(0) != null) director = made.optJSONObject(0).optString("name");
        }
        String trailer = "";
        JSONObject videos = j.optJSONObject("videos");
        JSONArray vs = videos == null ? null : videos.optJSONArray("results");
        for (int i = 0; vs != null && i < vs.length() && trailer.isEmpty(); i++) {
            JSONObject o = vs.optJSONObject(i);
            if (o != null && "YouTube".equals(o.optString("site")) && "Trailer".equals(o.optString("type"))) trailer = o.optString("key");
        }
        int minutes = j.optInt("runtime", 0);
        JSONArray runs = j.optJSONArray("episode_run_time");
        if (minutes <= 0 && runs != null && runs.length() > 0) minutes = runs.optInt(0, 0);
        String backdrop = image(j.optString("backdrop_path", ""), "w1280"), poster = image(j.optString("poster_path", ""), "w342");
        String plot = j.optString("overview", "");
        String rated = rating(j.optDouble("vote_average", 0));
        return new Catalog.Details(plot.isEmpty() ? t.plot : plot, String.join(", ", genres), String.join(", ", cast), director,
                j.optString(tv ? "first_air_date" : "release_date", t.year), backdrop.isEmpty() ? t.backdrop : backdrop,
                poster.isEmpty() ? t.poster : poster, trailer, rated.isEmpty() ? t.rating : rated, "", minutes, seasons);
    }

    /**
     * Pure: a season's episodes from the family's TorBox files, named as TMDB's season names them where it has it (its
     * title, story, still, length and when it was first shown); those it hasn't as they were.
     */
    static List<Catalog.Episode> named(List<Catalog.Episode> own, JSONObject season) {
        JSONArray eps = season == null ? null : season.optJSONArray("episodes");
        if (eps == null) return own;
        List<Catalog.Episode> out = new ArrayList<>(own.size());
        for (Catalog.Episode e : own) {
            JSONObject hit = null;
            for (int i = 0; i < eps.length() && hit == null; i++) {
                JSONObject o = eps.optJSONObject(i);
                if (o != null && o.optInt("season_number", e.season) == e.season && o.optInt("episode_number", -1) == e.number) hit = o;
            }
            if (hit == null) {
                out.add(e);
                continue;
            }
            Catalog.Episode n = new Catalog.Episode(e.id, e.season, e.number, hit.optString("name", ""), hit.optString("overview", ""),
                    image(hit.optString("still_path", ""), "w300"), hit.optInt("runtime", 0), Catalog.aired(hit.optString("air_date", "")),
                    "", "", "", "");
            n.torbox = e.torbox;
            out.add(n);
        }
        return out;
    }

    // ---- asking, and keeping ------------------------------------------------------------------------------------

    /** One TMDB answer; an error never carries the address (the key is in it). */
    private static JSONObject get(String key, String path) throws IOException, JSONException {
        HttpURLConnection h;
        try {
            h = (HttpURLConnection) new URL(API + path + (path.contains("?") ? "&" : "?") + "api_key=" + enc(key)).openConnection();
        } catch (IOException | IllegalArgumentException | ClassCastException e) {
            // A bad address's message would carry it, and the key with it: never passed on.
            throw new IOException("TMDB couldn't be asked");
        }
        h.setConnectTimeout(8_000);
        h.setReadTimeout(12_000);
        h.setRequestProperty("User-Agent", Fetch.AGENT);
        h.setRequestProperty("Accept", "application/json");
        try {
            int code;
            try {
                code = h.getResponseCode();
            } catch (IOException e) {
                throw new IOException("TMDB didn't answer");
            }
            if (code >= 400) throw new IOException("TMDB said no (" + code + ")");
            try (InputStream in = h.getInputStream()) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                return new JSONObject(new String(b.toByteArray(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IOException("TMDB's answer was cut short");
            }
        } finally {
            h.disconnect();
        }
    }

    private static File file(Context c, String name) {
        File d = new File(c.getApplicationContext().getFilesDir(), "tmdb");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return new File(d, hash(name) + ".json");
    }

    private static String hash(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 12; i++) b.append(String.format(Locale.ROOT, "%02x", h[i]));
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static JSONObject read(File f) {
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 2_000_000)];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            return new JSONObject(new String(b, 0, n, StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    private static void write(File f, String s) {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    private static String enc(String v) {
        try {
            return URLEncoder.encode(v == null ? "" : v, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }
}
