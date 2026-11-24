package com.gathin.homefieldtv.engine;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
 * TMDB's lists for the Editorial rows: trending, popular, top rated, now playing, airing today, a network's shows, a
 * genre's, a page of 20 at a time, and a network's name. Asked with the launcher's key as Tmdb asks (its generic user
 * agent, nothing about the family), kept on this TV for a few hours (trending), half a day (popular and the like) or two
 * days (top rated, a genre's, a network's), and kept a little in memory, so Home draws its rows at once next time and
 * works from what was kept when TMDB can't be reached. Only names, years, pictures and scores are kept: no address with
 * the key. Off the main thread.
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

    /** A page of a list: its titles, which page it is and how many the list has. */
    public static final class Page {
        public final List<Item> items;
        public final int page, pages;

        public Page(List<Item> items, int page, int pages) {
            this.items = Collections.unmodifiableList(items == null ? new ArrayList<>() : items);
            this.page = page;
            this.pages = Math.max(page, Math.min(MAX_PAGES, pages));
        }
    }

    private static final Map<String, Page> MEMORY = Collections.synchronizedMap(new LinkedHashMap<String, Page>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Page> e) {
            return size() > 96;
        }
    });
    private static final Map<String, Page> PREVIEW = Collections.synchronizedMap(new HashMap<>());
    private static final Map<Integer, String> NAMES = Collections.synchronizedMap(new HashMap<>());
    private static volatile boolean previewing;

    /**
     * A page (from 1) of one of TMDB's lists by its path ("trending/all/day", "discover/tv?with_networks=49"): kept from
     * before while fresh, else asked now, else what was kept even if old; null when there is nothing to be had.
     */
    public static Page page(Context c, String path, int page) {
        String key = path + "#" + page;
        if (previewing) return PREVIEW.get(key);
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
        JSONObject j = Tmdb.ask(c, path + (path.contains("?") ? "&" : "?") + "page=" + page);
        if (j == null) return had;
        Page p = parse(j, mediaOf(path), page);
        MEMORY.put(key, p);
        AT.put(key, System.currentTimeMillis());
        write(f, trimmed(j));
        return p;
    }

    private static final Map<String, Long> AT = Collections.synchronizedMap(new HashMap<>());

    private static long atOf(String key) {
        Long at = AT.get(key);
        return at == null ? 0L : at;
    }

    /** A network's name as TMDB gives it ("" while it can't be had): kept a month. */
    public static String network(Context c, int id) {
        String had = NAMES.get(id);
        if (had != null) return had;
        if (previewing) return "";
        File f = file(c, "n|" + id);
        JSONObject kept = read(f);
        if (kept != null && System.currentTimeMillis() - kept.optLong("_at") < 30L * 24 * 3600_000L) {
            String n = kept.optString("name", "");
            if (!n.isEmpty()) NAMES.put(id, n);
            return n;
        }
        JSONObject j = Tmdb.ask(c, "network/" + id);
        String name = j == null ? "" : j.optString("name", "").trim();
        if (name.isEmpty()) return kept == null ? "" : kept.optString("name", "");
        NAMES.put(id, name);
        try {
            write(f, new JSONObject().put("_at", System.currentTimeMillis()).put("name", name).toString());
        } catch (JSONException ignored) {
            // kept in memory only
        }
        return name;
    }

    /** How long a list is kept: trending today 3 hours; popular, playing, airing and this week's half a day; others two days. */
    static long keepMs(String path) {
        long hour = 3600_000L;
        if (path.startsWith("trending/") && path.contains("/day")) return 3 * hour;
        if (path.startsWith("trending/") || path.contains("popular") || path.contains("now_playing") || path.contains("airing_today")
                || path.contains("on_the_air") || path.contains("upcoming")) return 12 * hour;
        return 48 * hour;
    }

    /** Pure: whether a list's titles are shows ("tv"), films ("movie"), or say so each ("" for trending/all). */
    static String mediaOf(String path) {
        String p = path == null ? "" : path;
        if (p.startsWith("trending/all") || p.startsWith("search/multi")) return "";
        if (p.startsWith("tv/") || p.startsWith("trending/tv") || p.startsWith("discover/tv")) return "tv";
        return "movie";
    }

    /**
     * Pure: a page of TMDB's answer: its titles (people and the like left out) with their names, years, pictures (posters
     * at w342, backdrops at w1280), stories, scores and genres; media "tv", "movie", or "" to go by each one's own.
     */
    static Page parse(JSONObject j, String media, int page) {
        List<Item> items = new ArrayList<>();
        JSONArray rs = j == null ? null : j.optJSONArray("results");
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
        return new Page(items, j == null ? page : j.optInt("page", page), pages);
    }

    /** What is kept of an answer: its page, its count and only the fields the rows read. */
    private static String trimmed(JSONObject j) {
        try {
            JSONObject o = new JSONObject();
            o.put("_at", System.currentTimeMillis()).put("page", j.optInt("page", 1)).put("total_pages", j.optInt("total_pages", 1));
            JSONArray out = new JSONArray();
            JSONArray rs = j.optJSONArray("results");
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

    /** Previews and tests: TMDB's lists as these pages (by path and page), nothing asked or kept; null: as it comes. */
    static void preview(Map<String, Page> pages, Map<Integer, String> networks) {
        PREVIEW.clear();
        NAMES.clear();
        MEMORY.clear();
        previewing = pages != null;
        if (pages != null) PREVIEW.putAll(pages);
        if (networks != null) NAMES.putAll(networks);
    }

    /** Previews and tests: the key a page goes by in preview(). */
    static String previewKey(String path, int page) {
        return path + "#" + page;
    }

    // ---- kept on this TV ---------------------------------------------------------------------------------------------

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
