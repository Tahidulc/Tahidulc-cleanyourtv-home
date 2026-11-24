package com.gathin.homefieldtv.engine;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TMDB's lists for the Editorial rows: trending, popular, a themed discover (a genre, a keyword, a running time, a
 * decade), a list, a network's shows, a title's recommendations, a page of 20 at a time; and the names and ids they
 * need (a network's name, a keyword's id, a watched title's id). Asked with the launcher's key as Tmdb asks (its generic
 * user agent, nothing about the family), kept on this TV for a few hours (trending), half a day (popular and the like)
 * or two days (the rest), and a little in memory, so the rows come at once next time and work from what was kept when
 * TMDB can't be reached. Only names, years, pictures, scores and genres are kept: no address with the key. Off the main
 * thread.
 */
public final class TmdbLists {
    private TmdbLists() {}

    /** At most this many pages of a list are read (20 titles each). */
    public static final int MAX_PAGES = 10;

    /** One title in a list: TMDB's id, film or show, its names, year, pictures (addresses at their sizes), story, score. */
    public static final class Item {
        public final int id;
        public final boolean tv;
        public final String name, original, year, poster, backdrop, plot, rating;
        public final int[] genres;

        public Item(int id, boolean tv, String name, String original, String year, String poster, String backdrop, String plot,
                    String rating, int[] genres) {
            this.id = id;
            this.tv = tv;
            this.name = name == null ? "" : name;
            this.original = original == null ? "" : original;
            this.year = year == null ? "" : year;
            this.poster = poster == null ? "" : poster;
            this.backdrop = backdrop == null ? "" : backdrop;
            this.plot = plot == null ? "" : plot;
            this.rating = rating == null ? "" : rating;
            this.genres = genres == null ? new int[0] : genres;
        }

        /** Its year as a number (0 for none). */
        public int yearNumber() {
            if (year.length() < 4) return 0;
            try {
                return Integer.parseInt(year.substring(0, 4));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    /** A page of a list: its titles, which page it is, how many the list has, and a list's own name ("" for others). */
    public static final class Page {
        public final List<Item> items;
        public final int page, pages;
        public final String name;

        public Page(List<Item> items, int page, int pages) {
            this(items, page, pages, "");
        }

        public Page(List<Item> items, int page, int pages, String name) {
            this.items = Collections.unmodifiableList(items == null ? new ArrayList<>() : items);
            this.page = page;
            this.pages = Math.max(page, Math.min(MAX_PAGES, pages));
            this.name = name == null ? "" : name;
        }
    }

    /** Previews and tests: TMDB made up, a page for any path asked (null: TMDB has nothing there). */
    public interface Made {
        Page page(String path, int page);
    }

    private static final Map<String, Page> MEMORY = Collections.synchronizedMap(new LinkedHashMap<String, Page>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Page> e) {
            return size() > 96;
        }
    });
    private static final Map<String, Long> AT = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, String> NAMES = Collections.synchronizedMap(new HashMap<>());
    private static volatile Made made;

    /**
     * A page (from 1) of one of TMDB's lists by its path ("trending/all/day", "discover/tv?with_networks=49", "list/28"):
     * kept from before while fresh, else asked now, else what was kept even if old; null when there is nothing to be had.
     */
    public static Page page(Context c, String path, int page) {
        Made m = made;
        if (m != null) return m.page(path, page);
        String key = path + "#" + page;
        Page had = MEMORY.get(key);
        long at = atOf(key);
        File f = file(c, "l|" + key);
        if (had == null) {
            JSONObject kept = read(f);
            if (kept != null) {
                had = parse(kept, mediaOf(path), page);
                at = kept.optLong("_at");
                MEMORY.put(key, had);
                AT.put(key, at);
            }
        }
        if (had != null && System.currentTimeMillis() - at < keepMs(path)) return had;
        String ask = path.startsWith("list/") ? path + (page > 1 ? "?page=" + page : "")
                : path + (path.contains("?") ? "&" : "?") + "page=" + page;
        JSONObject j = Tmdb.ask(c, ask);
        if (j == null) return had;
        Page p = parse(j, mediaOf(path), page);
        MEMORY.put(key, p);
        AT.put(key, System.currentTimeMillis());
        write(f, trimmed(j));
        return p;
    }

    private static long atOf(String key) {
        Long at = AT.get(key);
        return at == null ? 0L : at;
    }

    /** A network's name as TMDB gives it ("" while it can't be had): kept a month. */
    public static String network(Context c, int id) {
        return named(c, "n|" + id, "network/" + id, j -> j.optString("name", "").trim());
    }

    /**
     * A keyword's id by its name ("based on true story"), as TMDB's own search finds it, the one with exactly that name
     * (0 when TMDB has none such, or can't be asked): kept a month.
     */
    public static int keyword(Context c, String name) {
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return 0;
        if (made != null) return Math.abs(n.hashCode() % 100_000) + 1;
        String got = named(c, "k|" + n, "search/keyword?query=" + enc(n), j -> {
            JSONArray rs = j.optJSONArray("results");
            for (int i = 0; rs != null && i < rs.length(); i++) {
                JSONObject o = rs.optJSONObject(i);
                if (o != null && o.optString("name", "").trim().equalsIgnoreCase(n) && o.optInt("id") > 0) return String.valueOf(o.optInt("id"));
            }
            return "0";
        });
        try {
            return got.isEmpty() ? 0 : Integer.parseInt(got);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * TMDB's id for a title the family watched (its name and year as the playlist has them): 0 when TMDB has no such
     * title, -1 when it can't be asked now. Kept by Tmdb, as for a title's page.
     */
    public static int idOf(Context c, boolean tv, String name, int year) {
        if (made != null) return Math.abs((name + tv).hashCode() % 100_000) + 1;
        Tmdb.Match m = Tmdb.find(c, tv, name, year);
        return m == null ? -1 : m.id;
    }

    private interface Pick {
        String of(JSONObject j);
    }

    /** A word TMDB gives (a name, an id), kept a month in memory and on this TV; "" while it can't be had. */
    private static String named(Context c, String key, String path, Pick pick) {
        String had = NAMES.get(key);
        if (had != null) return had;
        if (made != null) return "";
        File f = file(c, key);
        JSONObject kept = read(f);
        if (kept != null && System.currentTimeMillis() - kept.optLong("_at") < 30L * 24 * 3600_000L) {
            String v = kept.optString("v", "");
            NAMES.put(key, v);
            return v;
        }
        JSONObject j = Tmdb.ask(c, path);
        String v = j == null ? "" : pick.of(j);
        if (v.isEmpty()) return kept == null ? "" : kept.optString("v", "");
        NAMES.put(key, v);
        try {
            write(f, new JSONObject().put("_at", System.currentTimeMillis()).put("v", v).toString());
        } catch (JSONException ignored) {
            // kept in memory only
        }
        return v;
    }

    /** How long a list is kept: trending today 3 hours; popular, playing, airing and this week's half a day; others two days. */
    static long keepMs(String path) {
        long hour = 3600_000L;
        if (path.startsWith("trending/") && path.contains("/day")) return 3 * hour;
        if (path.startsWith("trending/") || path.contains("popular") || path.contains("now_playing") || path.contains("airing_today")
                || path.contains("on_the_air") || path.contains("upcoming") || path.contains(".desc&primary_release_date.lte")
                || path.contains("first_air_date.desc")) return 12 * hour;
        return 48 * hour;
    }

    /** Pure: whether a list's titles are shows ("tv"), films ("movie"), or say so each ("" for trending/all, a list). */
    static String mediaOf(String path) {
        String p = path == null ? "" : path;
        if (p.startsWith("trending/all") || p.startsWith("search/multi") || p.startsWith("list/")) return "";
        if (p.startsWith("tv/") || p.startsWith("trending/tv") || p.startsWith("discover/tv")) return "tv";
        return "movie";
    }

    /**
     * Pure: a page of TMDB's answer: its titles (people and the like left out) with their names, years, pictures (posters
     * at w342, backdrops at w1280), stories, scores and genres; media "tv", "movie", or "" to go by each one's own. A list's
     * answer has its titles as "items" and its own name.
     */
    static Page parse(JSONObject j, String media, int page) {
        List<Item> items = new ArrayList<>();
        JSONArray rs = j == null ? null : j.has("results") ? j.optJSONArray("results") : j.optJSONArray("items");
        for (int i = 0; rs != null && i < rs.length(); i++) {
            JSONObject r = rs.optJSONObject(i);
            if (r == null) continue;
            String kind = media.isEmpty() ? r.optString("media_type", "") : media;
            if (!kind.equals("tv") && !kind.equals("movie")) continue;
            boolean tv = kind.equals("tv");
            int id = r.optInt("id", 0);
            String name = r.optString(tv ? "name" : "title", "").trim();
            if (id <= 0 || name.isEmpty()) continue;
            String date = r.optString(tv ? "first_air_date" : "release_date", "");
            String year = date.length() >= 4 && Character.isDigit(date.charAt(0)) ? date.substring(0, 4) : "";
            JSONArray g = r.optJSONArray("genre_ids");
            int[] genres = new int[g == null ? 0 : g.length()];
            for (int k = 0; k < genres.length; k++) genres[k] = g.optInt(k);
            items.add(new Item(id, tv, name, r.optString(tv ? "original_name" : "original_title", "").trim(), year,
                    Tmdb.image(r.optString("poster_path", ""), "w342"), Tmdb.image(r.optString("backdrop_path", ""), "w1280"),
                    r.optString("overview", ""), Tmdb.rating(r.optDouble("vote_average", 0)), genres));
        }
        int pages = j == null ? page : j.optInt("total_pages", page);
        return new Page(items, j == null ? page : j.optInt("page", page), pages, j == null ? "" : j.optString("name", ""));
    }

    /** TMDB's genres by id, films' and shows' (the row's line under it names a title's first). */
    private static final Map<Integer, String> GENRES = new HashMap<>();

    static {
        Object[] g = {28, "Action", 12, "Adventure", 16, "Animation", 35, "Comedy", 80, "Crime", 99, "Documentary", 18, "Drama",
                10751, "Family", 14, "Fantasy", 36, "History", 27, "Horror", 10402, "Music", 9648, "Mystery", 10749, "Romance",
                878, "Science Fiction", 10770, "TV Movie", 53, "Thriller", 10752, "War", 37, "Western", 10759, "Action & Adventure",
                10762, "Kids", 10763, "News", 10764, "Reality", 10765, "Sci-Fi & Fantasy", 10766, "Soap", 10767, "Talk",
                10768, "War & Politics"};
        for (int i = 0; i < g.length; i += 2) GENRES.put((Integer) g[i], (String) g[i + 1]);
    }

    /** Pure: TMDB's name for a genre id ("" for one it doesn't have). */
    public static String genre(int id) {
        String n = GENRES.get(id);
        return n == null ? "" : n;
    }

    /** What is kept of an answer: its page, its count, a list's name, and only the fields the rows read. */
    private static String trimmed(JSONObject j) {
        try {
            JSONObject o = new JSONObject();
            o.put("_at", System.currentTimeMillis()).put("page", j.optInt("page", 1)).put("total_pages", j.optInt("total_pages", 1))
                    .put("name", j.optString("name", ""));
            JSONArray out = new JSONArray();
            JSONArray rs = j.has("results") ? j.optJSONArray("results") : j.optJSONArray("items");
            String[] keep = {"id", "media_type", "title", "name", "original_title", "original_name", "release_date", "first_air_date",
                    "poster_path", "backdrop_path", "overview", "vote_average", "genre_ids"};
            for (int i = 0; rs != null && i < rs.length(); i++) {
                JSONObject r = rs.optJSONObject(i);
                if (r == null) continue;
                JSONObject k = new JSONObject();
                for (String f : keep) if (r.has(f) && !r.isNull(f)) k.put(f, r.get(f));
                out.put(k);
            }
            o.put("results", out);
            return o.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    // ---- previews and tests ------------------------------------------------------------------------------------------

    /**
     * Previews and tests: TMDB made up (every list, keyword and watched title answered by it, nothing asked or kept), the
     * networks named as given; null: as it comes.
     */
    static void preview(Made pages, Map<Integer, String> networks) {
        NAMES.clear();
        MEMORY.clear();
        AT.clear();
        made = pages;
        if (networks != null) for (Map.Entry<Integer, String> e : networks.entrySet()) NAMES.put("n|" + e.getKey(), e.getValue());
    }

    // ---- kept on this TV ---------------------------------------------------------------------------------------------

    private static String enc(String v) {
        try {
            return URLEncoder.encode(v, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static File file(Context c, String name) {
        File d = new File(c.getApplicationContext().getFilesDir(), "tmdb-lists");
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
        if (!f.exists() || f.length() > 400_000) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
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
}
