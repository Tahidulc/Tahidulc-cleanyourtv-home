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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /**
     * What is most watched this week, films or TV shows (TMDB's trending lists, two pages), kept six hours: each
     * {name, year, poster, backdrop, rating}, in TMDB's order; empty when TMDB can't be asked and nothing is kept.
     */
    public static List<String[]> trending(Context c, boolean tv) {
        List<String[]> out = new ArrayList<>();
        for (int page = 1; page <= 2; page++) {
            JSONObject j = kept(c, "trend|" + (tv ? "tv" : "movie") + "|" + page, "trending/" + (tv ? "tv" : "movie") + "/week?page=" + page,
                    TREND_MS);
            out.addAll(trendingOf(j, tv));
        }
        return out;
    }

    static final long TREND_MS = 6 * 3600_000L;

    /** Pure: a trending page's titles, {name, year, poster, backdrop, rating}. */
    static List<String[]> trendingOf(JSONObject j, boolean tv) {
        List<String[]> out = new ArrayList<>();
        JSONArray rs = j == null ? null : j.optJSONArray("results");
        for (int i = 0; rs != null && i < rs.length(); i++) {
            JSONObject r = rs.optJSONObject(i);
            if (r == null) continue;
            String name = r.optString(tv ? "name" : "title", "");
            if (name.isEmpty()) continue;
            String date = r.optString(tv ? "first_air_date" : "release_date", "");
            String y = date.length() >= 4 && date.substring(0, 4).matches("\\d{4}") ? date.substring(0, 4) : "";
            out.add(new String[]{name, y, image(r.optString("poster_path", ""), "w342"), image(r.optString("backdrop_path", ""), "w1280"),
                    rating(r.optDouble("vote_average", 0))});
        }
        return out;
    }

    private static JSONObject kept(Context c, String name, String path) {
        return kept(c, name, path, KEEP_MS);
    }

    private static JSONObject kept(Context c, String name, String path, long freshMs) {
        File f = file(c, name);
        JSONObject had = read(f);
        if (had != null && System.currentTimeMillis() - had.optLong("_at") < freshMs) return had;
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
        Catalog.Details d = new Catalog.Details(plot.isEmpty() ? t.plot : plot, String.join(", ", genres), String.join(", ", cast), director,
                j.optString(tv ? "first_air_date" : "release_date", t.year), backdrop.isEmpty() ? t.backdrop : backdrop,
                poster.isEmpty() ? t.poster : poster, trailer, rated.isEmpty() ? t.rating : rated, "", minutes, seasons);
        // Its ids, for the player's subtitles (OpenSubtitles looks them up by either).
        d.tmdb = j.optInt("id", 0);
        JSONObject ext = j.optJSONObject("external_ids");
        String imdb = j.optString("imdb_id", ext == null ? "" : ext.optString("imdb_id", ""));
        d.imdb = imdb == null || "null".equals(imdb) ? "" : imdb;
        return d;
    }

    /**
     * A title's TMDB id for the player's subtitles: kept from before, else asked of TMDB now with the launcher's key (as
     * the posters are); 0 when TMDB has none or can't be asked. Off the main thread.
     */
    public static int idOf(Context c, Catalog.Title t) {
        if (t == null) return 0;
        Match m = find(c, t.series, t.name, yearOf(t.year));
        return m == null ? 0 : Math.max(0, m.id);
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

    // ---- a title's extras: its logo, its cast's faces, its seasons' posters ------------------------------------------

    /**
     * What a title's page shows from TMDB beyond its playlist's page: its logo ("" for none), its cast as {name, the part
     * they play, their photo}, its seasons' posters by number, its years ("2011–2019") and its age rating ("15").
     */
    public static final class Extras {
        public final String logo, years, age;
        public final List<String[]> cast;
        public final Map<Integer, String> seasons;

        Extras(String logo, List<String[]> cast, Map<Integer, String> seasons, String years, String age) {
            this.logo = logo == null ? "" : logo;
            this.cast = Collections.unmodifiableList(cast == null ? new ArrayList<>() : cast);
            this.seasons = Collections.unmodifiableMap(seasons == null ? new HashMap<>() : seasons);
            this.years = years == null ? "" : years;
            this.age = age == null ? "" : age;
        }
    }

    /** TMDB has no such title. */
    public static final Extras NO_EXTRAS = new Extras("", null, null, "", "");

    /**
     * A title's extras: kept from before (a fortnight), else asked of TMDB now with the launcher's key (its search, then
     * its page with credits, logos and age ratings), and kept. Null when TMDB can't be asked. Off the main thread.
     */
    public static Extras extras(Context c, Catalog.Title t) {
        if (t == null) return null;
        Match m = find(c, t.series, t.name, yearOf(t.year));
        if (m == null) return null;
        if (m.id <= 0) return NO_EXTRAS;
        File f = file(c, "x|" + (t.series ? "tv" : "movie") + "|" + m.id);
        JSONObject had = read(f);
        if (had != null && System.currentTimeMillis() - had.optLong("_at") < KEEP_MS) return fromKept(had);
        String k = key(c);
        if (k == null) return had == null ? null : fromKept(had);
        try {
            JSONObject j = get(k, (t.series ? "tv/" : "movie/") + m.id + "?append_to_response=credits,images,"
                    + (t.series ? "content_ratings" : "release_dates") + "&include_image_language=en,null");
            Extras x = extrasOf(j, t.series);
            write(f, kept(x).toString());
            return x;
        } catch (IOException | JSONException e) {
            return had == null ? null : fromKept(had);
        }
    }

    /** A title's extras when they are kept on this TV already (nothing asked), else null: for a page drawn at once. */
    public static Extras keptExtras(Context c, Catalog.Title t) {
        if (t == null) return null;
        Match m = cached(c, t.series, t.name, yearOf(t.year));
        if (m == null) return null;
        if (m.id <= 0) return NO_EXTRAS;
        JSONObject had = read(file(c, "x|" + (t.series ? "tv" : "movie") + "|" + m.id));
        return had == null ? null : fromKept(had);
    }

    private static int yearOf(String y) {
        return y != null && y.matches("\\d{4}") ? Integer.parseInt(y) : 0;
    }

    /**
     * Pure: a title's extras from TMDB's page: its English (else wordless) PNG logo, the first ten of its cast with the
     * parts they play and their photos, each season's poster (small: its colour is what is wanted), the years it ran (a
     * series) or its year (a film), and its age rating in the UK, else the US.
     */
    static Extras extrasOf(JSONObject j, boolean tv) {
        String logo = "";
        JSONObject images = j.optJSONObject("images");
        JSONArray logos = images == null ? null : images.optJSONArray("logos");
        for (String lang : new String[]{"en", ""}) {
            for (int i = 0; logos != null && i < logos.length() && logo.isEmpty(); i++) {
                JSONObject o = logos.optJSONObject(i);
                if (o == null) continue;
                String l = o.isNull("iso_639_1") ? "" : o.optString("iso_639_1", "");
                String path = o.optString("file_path", "");
                if (l.equals(lang) && path.toLowerCase(Locale.ROOT).endsWith(".png")) logo = image(path, "w500");
            }
        }
        List<String[]> cast = new ArrayList<>();
        JSONObject credits = j.optJSONObject("credits");
        JSONArray people = credits == null ? null : credits.optJSONArray("cast");
        for (int i = 0; people != null && i < people.length() && cast.size() < 10; i++) {
            JSONObject o = people.optJSONObject(i);
            if (o == null || o.optString("name").isEmpty()) continue;
            String part = o.optString("character", "");
            if (part.equals("null")) part = "";
            cast.add(new String[]{o.optString("name"), part, image(o.optString("profile_path", ""), "w185")});
        }
        Map<Integer, String> seasons = new HashMap<>();
        JSONArray ss = j.optJSONArray("seasons");
        for (int i = 0; ss != null && i < ss.length(); i++) {
            JSONObject o = ss.optJSONObject(i);
            if (o == null) continue;
            String poster = image(o.optString("poster_path", ""), "w92");
            if (!poster.isEmpty()) seasons.put(o.optInt("season_number", -1), poster);
        }
        String first = year(j.optString(tv ? "first_air_date" : "release_date", "")), last = tv ? year(j.optString("last_air_date", "")) : "";
        String years = first.isEmpty() ? "" : last.isEmpty() || last.equals(first) ? first : first + "\u2013" + last;
        String age = "";
        if (tv) {
            JSONObject cr = j.optJSONObject("content_ratings");
            JSONArray rs = cr == null ? null : cr.optJSONArray("results");
            for (String country : new String[]{"GB", "US"}) {
                for (int i = 0; rs != null && i < rs.length() && age.isEmpty(); i++) {
                    JSONObject o = rs.optJSONObject(i);
                    if (o != null && country.equals(o.optString("iso_3166_1"))) age = o.optString("rating", "").trim();
                }
            }
        } else {
            JSONObject rd = j.optJSONObject("release_dates");
            JSONArray rs = rd == null ? null : rd.optJSONArray("results");
            for (String country : new String[]{"GB", "US"}) {
                for (int i = 0; rs != null && i < rs.length() && age.isEmpty(); i++) {
                    JSONObject o = rs.optJSONObject(i);
                    if (o == null || !country.equals(o.optString("iso_3166_1"))) continue;
                    JSONArray ds = o.optJSONArray("release_dates");
                    for (int k = 0; ds != null && k < ds.length() && age.isEmpty(); k++) {
                        JSONObject d = ds.optJSONObject(k);
                        if (d != null) age = d.optString("certification", "").trim();
                    }
                }
            }
        }
        return new Extras(logo, cast, seasons, years, age);
    }

    private static String year(String date) {
        return date != null && date.length() >= 4 && date.substring(0, 4).matches("\\d{4}") ? date.substring(0, 4) : "";
    }

    /** Pure: extras as they are kept on this TV (only what the page uses, not TMDB's whole page). */
    static JSONObject kept(Extras x) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("_at", System.currentTimeMillis()).put("logo", x.logo).put("years", x.years).put("age", x.age);
        JSONArray cast = new JSONArray();
        for (String[] p : x.cast) cast.put(new JSONArray().put(p[0]).put(p[1]).put(p[2]));
        o.put("cast", cast);
        JSONObject seasons = new JSONObject();
        for (Map.Entry<Integer, String> e : x.seasons.entrySet()) seasons.put(String.valueOf(e.getKey()), e.getValue());
        o.put("seasons", seasons);
        return o;
    }

    /** Pure: extras as kept (kept()). */
    static Extras fromKept(JSONObject o) {
        List<String[]> cast = new ArrayList<>();
        JSONArray a = o.optJSONArray("cast");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONArray p = a.optJSONArray(i);
            if (p != null) cast.add(new String[]{p.optString(0, ""), p.optString(1, ""), p.optString(2, "")});
        }
        Map<Integer, String> seasons = new HashMap<>();
        JSONObject s = o.optJSONObject("seasons");
        if (s != null) {
            for (Iterator<String> it = s.keys(); it.hasNext(); ) {
                String k = it.next();
                try {
                    seasons.put(Integer.parseInt(k), s.optString(k));
                } catch (NumberFormatException ignored) {
                    // not a season
                }
            }
        }
        return new Extras(o.optString("logo", ""), cast, seasons, o.optString("years", ""), o.optString("age", ""));
    }

    // ---- asking, and keeping ------------------------------------------------------------------------------------

    /**
     * One of TMDB's lists or pages by its path ("trending/movie/day?page=2", "discover/tv?with_networks=49"), asked now
     * with the launcher's key (TmdbLists keeps what it says); null without a key or when TMDB couldn't be asked. Off the
     * main thread.
     */
    static JSONObject ask(Context c, String path) {
        String k = key(c);
        if (k == null) return null;
        try {
            return get(k, path);
        } catch (IOException | JSONException e) {
            return null;
        }
    }

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
                return new JSONObject(Bounded.text(in, h.getContentLengthLong(), 4 << 20));
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
