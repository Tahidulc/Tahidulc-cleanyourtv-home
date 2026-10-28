package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What is trending in films and TV, from Apple's public charts for one country (the iTunes Store's top films and top
 * TV seasons, no key needed): title, rank, genre, year, the film's synopsis and artwork the image server cuts to any
 * size. Films and TV alternate. Off the main thread.
 */
final class Trending {
    static final class Title {
        boolean tv;
        int rank;
        String title, season, genre, year, summary;
        /** Its page on the store, and what that page's badges say: "4K" or "HD", Dolby Vision, HDR, Atmos, age, RT. */
        String link, quality, age, tomatoes, stars;
        /** The trailer: the HD one on Apple's TV page, else the store's short preview clip (a film's, in the chart). */
        String trailer, preview;
        boolean dolbyVision, hdr, hdr10plus, atmos, rotten, certified;
        /** The image without its size: add "/{w}x{h}bb.jpg" for the whole picture, "/{w}x{h}sr.jpg" for a crop. */
        String art;
        /** Netflix's: one wide picture (cropped to the card) and the title's logo, and no store page. */
        String image, logo;
        boolean netflix;
        /** TMDB's: a poster (image) and a wide backdrop, and the service's own logo for the corner. */
        String backdrop, service;
        boolean hasPoster;
        /** The pill on the poster instead of "#N Today" ("NEW" for a new release), or null. */
        String pill;
        /** The title's id on TMDB when it came from there, so its logo needs no search. */
        int tmdbId;

        String poster(int w, int h) { return art == null ? image : art + "/" + w + "x" + h + "bb.jpg"; }
        String crop(int w, int h) { return art == null ? (backdrop != null ? backdrop : image) : art + "/" + w + "x" + h + "sr.jpg"; }
    }

    private static final String FEED = "https://itunes.apple.com/%s/rss/%s/limit=%d/json";
    private static final Pattern SIZE = Pattern.compile("/\\d+x\\d+[a-z]*\\.(png|jpg)$");
    private static final Pattern SEASON = Pattern.compile("^(.*), ((Season|Series|Part|Volume) .*)$");

    /** A row of the trending section: a chart of the store's (overall or one genre) or Netflix's weekly top ten. */
    static final class Category {
        final String name;
        final boolean tv, netflix;
        final int genre;
        /** Debrid Stream's own Continue Watching, as AppSearchService last copied it. */
        boolean resume;
        /** TMDB's trending films and series of the day; Netflix's films and series together, in one row. */
        boolean trending, both;
        /** Films just out to stream or buy in the country, and series just begun. */
        boolean fresh;
        /** TMDB's watch provider id for a streaming service's row (0 otherwise), and its logo. */
        int provider;
        String providerLogo;
        Category(String name, boolean tv, boolean netflix, int genre) { this.name = name; this.tv = tv; this.netflix = netflix; this.genre = genre; }
    }

    /** UK streaming services for TMDB's rows: {name, watch provider id, logo path}. */
    private static final Object[][] SERVICES = {{"Prime Video", 9, "/gMZdpavHmxFNnLpMHwVxfqeux2g.png"},
            {"Disney+", 337, "/5eZ872CghnHFLB1j8grszbrx0dx.png"}, {"Apple TV+", 350, "/9icYBfYFcwgCbky5VdGUIKJ4C5i.png"}, {"NOW", 39, "/oFAnvlaEW2KT6J7XM0DXjlWqKu9.png"},
            {"Paramount+", 531, "/pkx3klJlwW5JdtaulvDx6hDNtch.png"}, {"BBC iPlayer", 38, "/vi6XJCpJ9GpAPD3rDSRzMC812Z.png"},
            {"ITVX", 41, "/nIViE3wOPV5Jyv45T7hetHyG6iS.png"}, {"Channel 4", 103, "/kMT8p43W3BsXBe9mqCIPDNepser.png"}};

    /** The rows, with TMDB's trending first and a row for each UK streaming service after Netflix's when the theme has a TMDB key. */
    static List<Category> categories(boolean tmdb) {
        List<Category> c = categories();
        if (!tmdb) return c;
        Category trending = new Category("Trending", false, false, 0);
        trending.trending = true;
        c.add(0, trending);
        Category fresh = new Category("New Releases", false, false, 0);
        fresh.fresh = true;
        c.add(1, fresh);
        List<Category> services = new ArrayList<>();
        for (Object[] sv : SERVICES) {
            Category cat = new Category((String) sv[0], false, false, 0);
            cat.provider = (Integer) sv[1];
            cat.providerLogo = sv[2] == null ? null : "https://image.tmdb.org/t/p/w92" + sv[2];
            services.add(cat);
        }
        int netflix = 0;
        while (netflix < c.size() && !c.get(netflix).netflix) netflix++;
        c.addAll(Math.min(netflix + 1, c.size()), services);
        return c;
    }

    /** The Continue Watching tab, when Debrid Stream's row has been copied in the last fortnight. */
    static Category resumeCategory(android.content.Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences(AppSearchService.CONTINUE, android.content.Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - p.getLong("at", 0) > 14L * 24 * 60 * 60_000L) return null;
        Category cat = new Category("Continue Watching", false, false, 0);
        cat.resume = true;
        return cat;
    }

    /**
     * The copied Continue Watching titles with artwork: each looked up on TMDB when the theme has a key (poster,
     * backdrop and synopsis), else in the store as a film, then as a series; a found answer is kept, so the row draws
     * from memory after the first time, and one not found is asked again next time.
     */
    static List<Title> resume(android.content.Context c, String country, String key) {
        List<Title> out = new ArrayList<>();
        android.content.SharedPreferences p = c.getSharedPreferences(AppSearchService.CONTINUE, android.content.Context.MODE_PRIVATE);
        try {
            JSONArray titles = new JSONArray(p.getString("titles", "[]"));
            for (int i = 0; i < titles.length(); i++) {
                String name = titles.getString(i);
                JSONObject found;
                String kept = p.getString("found:" + name, null);
                if (kept != null) {
                    found = new JSONObject(kept);
                } else {
                    found = key.isEmpty() ? null : lookUp(key, name);
                    if (found == null) {
                        String[] art = artwork(name, country);
                        if (art != null) found = new JSONObject().put("art", art[0]).put("tv", "tv".equals(art[1]));
                    }
                    if (found == null) continue;
                    p.edit().putString("found:" + name, found.toString()).apply();
                }
                Title t = new Title();
                t.title = name;
                t.rank = 0;
                t.tv = found.optBoolean("tv");
                t.genre = found.optString("genre");
                t.year = found.optString("year");
                t.summary = found.optString("summary");
                t.art = found.has("art") ? found.optString("art") : null;
                t.image = found.has("image") ? found.optString("image") : null;
                t.backdrop = found.has("backdrop") ? found.optString("backdrop") : null;
                t.hasPoster = t.image != null;
                t.tmdbId = found.optInt("id");
                out.add(t);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static final long POSTER_RETRY_MS = 24L * 60 * 60_000L;

    /**
     * A portrait poster for a title an app publishes as a landscape still ("The Boys", "Reacher - Season 3"), from
     * TMDB: only for a film or series of exactly that name. Kept once found; a title with none is asked again after a
     * day. Off the main thread.
     */
    static String posterFor(android.content.Context c, String key, String title) {
        if (key.isEmpty() || title == null || title.trim().isEmpty()) return null;
        String name = title.replaceAll("(?i)\\s*[-:–(]?\\s*(season|series|staffel|temporada)\\s*\\d+.*$", "")
                .replaceAll("(?i)\\s*\\((4k|uhd|hd)[^)]*\\)", "").trim();
        android.content.SharedPreferences prefs = c.getSharedPreferences("app_posters_v2", android.content.Context.MODE_PRIVATE);
        String kept = prefs.getString(name, null);
        if (kept != null && !kept.startsWith("-")) return kept;
        if (kept != null && System.currentTimeMillis() - Long.parseLong(kept.substring(1)) < POSTER_RETRY_MS) return null;
        String found = null;
        try {
            JSONArray results = new JSONObject(get("https://api.themoviedb.org/3/search/multi?api_key=" + key
                    + "&include_adult=false&query=" + java.net.URLEncoder.encode(name, "UTF-8"))).optJSONArray("results");
            String want = plain(name);
            for (int i = 0; results != null && i < results.length() && found == null; i++) {
                JSONObject r = results.getJSONObject(i);
                String type = r.optString("media_type"), poster = r.optString("poster_path", "");
                if (!"movie".equals(type) && !"tv".equals(type) || poster.isEmpty() || "null".equals(poster)) continue;
                String named = r.optString("tv".equals(type) ? "name" : "title"), original = r.optString("tv".equals(type) ? "original_name" : "original_title");
                if (plain(named).equals(want) || plain(original).equals(want)) found = "https://image.tmdb.org/t/p/w342" + poster;
            }
        } catch (Exception e) {
            return null;   // asked again next time: the network, not the title
        }
        prefs.edit().putString(name, found != null ? found : "-" + System.currentTimeMillis()).apply();
        return found;
    }

    /** A name to compare: lower case, without a leading "The", letters and digits only. */
    private static String plain(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).replaceFirst("^the\\s+", "").replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** The film or series TMDB finds for a name, as the fields resume() keeps, or null. */
    private static JSONObject lookUp(String key, String name) {
        try {
            JSONArray results = new JSONObject(get("https://api.themoviedb.org/3/search/multi?api_key=" + key
                    + "&include_adult=false&query=" + java.net.URLEncoder.encode(name, "UTF-8"))).optJSONArray("results");
            for (int i = 0; results != null && i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                String type = r.optString("media_type"), poster = r.optString("poster_path", "");
                if (!"movie".equals(type) && !"tv".equals(type) || poster.isEmpty() || "null".equals(poster)) continue;
                boolean tv = "tv".equals(type);
                String back = r.optString("backdrop_path", ""), date = r.optString(tv ? "first_air_date" : "release_date", "");
                JSONArray ids = r.optJSONArray("genre_ids");
                JSONObject f = new JSONObject().put("id", r.optInt("id")).put("tv", tv).put("summary", r.optString("overview", ""))
                        .put("year", date.length() >= 4 ? date.substring(0, 4) : "")
                        .put("genre", ids != null && ids.length() > 0 && GENRES.containsKey(ids.optInt(0)) ? GENRES.get(ids.optInt(0)) : "")
                        .put("image", "https://image.tmdb.org/t/p/w500" + poster);
                if (!back.isEmpty() && !"null".equals(back)) f.put("backdrop", "https://image.tmdb.org/t/p/w780" + back);
                return f;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** {artwork without its size, "film" or "tv"} for a name from the iTunes Search API, or null. */
    private static String[] artwork(String name, String country) {
        String[][] tries = {{"movie&entity=movie", "film", "trackName"}, {"tvShow&entity=tvSeason", "tv", "collectionName"}};
        String want = name.toLowerCase(java.util.Locale.ROOT);
        for (String[] k : tries) {
            try {
                JSONArray results = new JSONObject(get("https://itunes.apple.com/search?country=" + country + "&limit=5&media=" + k[0]
                        + "&term=" + java.net.URLEncoder.encode(name, "UTF-8"))).optJSONArray("results");
                for (int i = 0; results != null && i < results.length(); i++) {
                    JSONObject r = results.getJSONObject(i);
                    String img = r.optString("artworkUrl100", "");
                    Matcher m = SIZE.matcher(img);
                    if (r.optString(k[2]).toLowerCase(java.util.Locale.ROOT).startsWith(want) && m.find()) {
                        return new String[]{img.substring(0, m.start()), k[1]};
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** The rows, in the order the tabs show them: the two top tens, Netflix's, then genres of films and of TV. */
    static List<Category> categories() {
        List<Category> c = new ArrayList<>();
        c.add(new Category("Top 10 Films", false, false, 0));
        c.add(new Category("Top 10 TV", true, false, 0));
        Category netflix = new Category("Netflix", false, true, 0);
        netflix.both = true;
        c.add(netflix);
        // The iTunes Store's genre ids (films 44xx, TV 40xx).
        Object[][] genres = {{"Action", false, 4401}, {"Comedy", false, 4404}, {"Drama", false, 4406}, {"Thriller", false, 4416},
                {"Horror", false, 4408}, {"Sci-Fi & Fantasy", false, 4413}, {"Family", false, 4410}, {"Romance", false, 4412},
                {"Documentary", false, 4405}, {"Drama Series", true, 4001}, {"Comedy Series", true, 4000},
                {"Sci-Fi & Fantasy TV", true, 4008}, {"Action TV", true, 4003}, {"Reality TV", true, 4007},
                {"Kids TV", true, 4005}, {"Docuseries", true, 4006}};
        for (Object[] g : genres) c.add(new Category((String) g[0], (Boolean) g[1], false, (Integer) g[2]));
        return c;
    }

    /** One row's titles: from the store's chart (its genre's when it has one), or Netflix's top ten for netflixCountry. */
    static List<Title> read(String country, String netflixCountry, Category c, int limit) {
        if (c.netflix && c.both) {
            // Its two top tens taken in turn, ranked again as one.
            List<Title> films = netflix(netflixCountry, false), tv = netflix(netflixCountry, true), out = new ArrayList<>();
            for (int i = 0; i < Math.max(films.size(), tv.size()); i++) {
                if (i < films.size()) out.add(films.get(i));
                if (i < tv.size()) out.add(tv.get(i));
            }
            for (int i = 0; i < out.size(); i++) out.get(i).rank = i + 1;
            return out;
        }
        if (c.netflix) return netflix(netflixCountry, c.tv);
        return chart(country, c.tv ? "toptvseasons" : "topmovies", limit, c.tv, c.genre);
    }

    static List<Title> read(String country, int each) {
        List<Title> films = chart(country, "topmovies", each, false, 0);
        List<Title> tv = chart(country, "toptvseasons", each, true, 0);
        List<Title> out = new ArrayList<>();
        for (int i = 0; i < Math.max(films.size(), tv.size()); i++) {
            if (i < films.size()) out.add(films.get(i));
            if (i < tv.size()) out.add(tv.get(i));
        }
        return out;
    }

    private static List<Title> chart(String country, String name, int limit, boolean tv, int genre) {
        List<Title> out = new ArrayList<>();
        try {
            String url = String.format(FEED, country, name, limit);
            if (genre > 0) url = url.replace("/json", "/genre=" + genre + "/json");
            JSONObject feed = new JSONObject(get(url)).getJSONObject("feed");
            // A chart of one title has it as an object, not a list.
            JSONArray entries = feed.optJSONArray("entry");
            if (entries == null && feed.optJSONObject("entry") != null) entries = new JSONArray().put(feed.getJSONObject("entry"));
            for (int i = 0; entries != null && i < entries.length(); i++) {
                JSONObject e = entries.getJSONObject(i);
                Title t = new Title();
                t.tv = tv;
                t.rank = i + 1;
                t.title = label(e, "im:name");
                Matcher m = SEASON.matcher(t.title);
                if (tv && m.matches()) { t.title = m.group(1); t.season = m.group(2); }
                JSONObject cat = e.optJSONObject("category");
                t.genre = cat == null ? "" : cat.optJSONObject("attributes") == null ? "" : cat.getJSONObject("attributes").optString("label", "");
                String date = label(e, "im:releaseDate");
                t.year = date.length() >= 4 ? date.substring(0, 4) : "";
                t.summary = label(e, "summary");
                JSONArray images = e.optJSONArray("im:image");
                String img = images == null || images.length() == 0 ? "" : images.getJSONObject(images.length() - 1).optString("label", "");
                Matcher s = SIZE.matcher(img);
                if (t.title.isEmpty() || !s.find()) continue;
                t.art = img.substring(0, s.start());
                Object link = e.opt("link");
                JSONObject l = link instanceof JSONArray ? ((JSONArray) link).optJSONObject(0) : (JSONObject) link;
                if (link instanceof JSONArray) {
                    JSONArray links = (JSONArray) link;
                    for (int k = 0; k < links.length(); k++) {
                        JSONObject a = links.getJSONObject(k).optJSONObject("attributes");
                        if (a != null && "enclosure".equals(a.optString("rel")) && a.optString("type").startsWith("video/")) t.preview = a.optString("href");
                    }
                }
                String href = l == null || l.optJSONObject("attributes") == null ? "" : l.getJSONObject("attributes").optString("href", "");
                t.link = href.replaceAll("\\?.*$", "");
                // A film's page is read on Apple's TV site, which takes the store's id; the store itself answers a TV
                // with an "item not available" stub as often as not. Seasons have no such address, so they keep it.
                if (!tv) t.link = t.link.replace("://itunes.apple.com/", "://tv.apple.com/");
                out.add(t);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---- TMDB: what is popular on each UK streaming service --------------------------------------------

    private static final String TMDB = "https://api.themoviedb.org/3/discover/%s?api_key=%s&watch_region=%s"
            + "&with_watch_providers=%d&with_watch_monetization_types=flatrate&sort_by=popularity.desc&language=en-GB&%s";
    private static final java.util.Map<Integer, String> GENRES = new java.util.HashMap<>();
    static {
        Object[][] g = {{28, "Action"}, {12, "Adventure"}, {16, "Animation"}, {35, "Comedy"}, {80, "Crime"}, {99, "Documentary"},
                {18, "Drama"}, {10751, "Family"}, {14, "Fantasy"}, {36, "History"}, {27, "Horror"}, {10402, "Music"},
                {9648, "Mystery"}, {10749, "Romance"}, {878, "Sci-Fi"}, {53, "Thriller"}, {10752, "War"}, {37, "Western"},
                {10759, "Action"}, {10762, "Kids"}, {10763, "News"}, {10764, "Reality"}, {10765, "Sci-Fi"}, {10766, "Soap"},
                {10767, "Talk"}, {10768, "War"}};
        for (Object[] x : g) GENRES.put((Integer) x[0], (String) x[1]);
    }

    /**
     * What is popular on one service in the country now (TMDB's discover, which this product uses though TMDB does not
     * endorse it): series with an episode in the last six months and films from the last two years, the two lists
     * taken in turn to ten.
     */
    static List<Title> service(String key, String region, Category c) {
        java.time.LocalDate now = java.time.LocalDate.now();
        List<Title> tv = tmdb(key, region, c, true, "air_date.gte=" + now.minusMonths(6));
        List<Title> films = tmdb(key, region, c, false, "primary_release_date.gte=" + now.minusMonths(24));
        List<Title> out = new ArrayList<>();
        for (int i = 0; out.size() < 10 && (i < tv.size() || i < films.size()); i++) {
            if (i < tv.size()) out.add(tv.get(i));
            if (i < films.size() && out.size() < 10) out.add(films.get(i));
        }
        for (int i = 0; i < out.size(); i++) out.get(i).rank = i + 1;
        return out;
    }

    /**
     * New releases: films out to stream, rent or buy in the region (a digital or physical release) in the last six
     * weeks, and series first shown in the last two months, the most watched first, the two taken in turn to twenty.
     */
    static List<Title> newReleases(String key, String region) {
        java.time.LocalDate today = java.time.LocalDate.now();
        String r = region.toUpperCase(java.util.Locale.ROOT);
        List<Title> films = discover("https://api.themoviedb.org/3/discover/movie?api_key=" + key + "&region=" + r
                + "&with_release_type=4|5&release_date.gte=" + today.minusWeeks(6) + "&release_date.lte=" + today
                + "&sort_by=popularity.desc&language=en-GB&include_adult=false", false);
        List<Title> tv = discover("https://api.themoviedb.org/3/discover/tv?api_key=" + key + "&first_air_date.gte="
                + today.minusMonths(2) + "&first_air_date.lte=" + today + "&sort_by=popularity.desc&language=en-GB"
                + "&with_original_language=en&include_adult=false", true);
        List<Title> out = new ArrayList<>();
        for (int i = 0; out.size() < 20 && (i < films.size() || i < tv.size()); i++) {
            if (i < films.size()) out.add(films.get(i));
            if (i < tv.size() && out.size() < 20) out.add(tv.get(i));
        }
        for (int i = 0; i < out.size(); i++) {
            out.get(i).rank = i + 1;
            out.get(i).pill = "NEW";
        }
        return out;
    }

    /** A TMDB discover list as titles with posters. */
    private static List<Title> discover(String url, boolean tv) {
        List<Title> out = new ArrayList<>();
        try {
            JSONArray results = new JSONObject(get(url)).optJSONArray("results");
            for (int i = 0; results != null && i < results.length() && out.size() < 12; i++) {
                JSONObject r = results.getJSONObject(i);
                String poster = r.optString("poster_path", ""), back = r.optString("backdrop_path", "");
                if (poster.isEmpty() || "null".equals(poster)) continue;
                Title t = new Title();
                t.tv = tv;
                t.title = r.optString(tv ? "name" : "title");
                t.summary = r.optString("overview", "");
                String date = r.optString(tv ? "first_air_date" : "release_date", "");
                t.year = date.length() >= 4 ? date.substring(0, 4) : "";
                JSONArray ids = r.optJSONArray("genre_ids");
                t.genre = ids != null && ids.length() > 0 && GENRES.containsKey(ids.optInt(0)) ? GENRES.get(ids.optInt(0)) : "";
                t.image = "https://image.tmdb.org/t/p/w500" + poster;
                t.backdrop = back.isEmpty() || "null".equals(back) ? null : "https://image.tmdb.org/t/p/w780" + back;
                t.hasPoster = true;
                t.tmdbId = r.optInt("id");
                out.add(t);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** TMDB's films and series trending today, worldwide, ranked as it ranks them. */
    static List<Title> trending(String key) {
        List<Title> out = new ArrayList<>();
        try {
            JSONArray results = new JSONObject(get("https://api.themoviedb.org/3/trending/all/day?language=en-GB&api_key=" + key))
                    .optJSONArray("results");
            for (int i = 0; results != null && i < results.length() && out.size() < 20; i++) {
                JSONObject r = results.getJSONObject(i);
                String type = r.optString("media_type"), poster = r.optString("poster_path", ""), back = r.optString("backdrop_path", "");
                if (!"movie".equals(type) && !"tv".equals(type) || poster.isEmpty() || "null".equals(poster)) continue;
                Title t = new Title();
                t.tv = "tv".equals(type);
                t.title = r.optString(t.tv ? "name" : "title");
                t.summary = r.optString("overview", "");
                String date = r.optString(t.tv ? "first_air_date" : "release_date", "");
                t.year = date.length() >= 4 ? date.substring(0, 4) : "";
                JSONArray ids = r.optJSONArray("genre_ids");
                t.genre = ids != null && ids.length() > 0 && GENRES.containsKey(ids.optInt(0)) ? GENRES.get(ids.optInt(0)) : "";
                t.image = "https://image.tmdb.org/t/p/w500" + poster;
                t.backdrop = back.isEmpty() || "null".equals(back) ? null : "https://image.tmdb.org/t/p/w780" + back;
                t.hasPoster = true;
                t.tmdbId = r.optInt("id");
                t.rank = out.size() + 1;
                out.add(t);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static List<Title> tmdb(String key, String region, Category c, boolean tv, String since) {
        List<Title> out = new ArrayList<>();
        try {
            JSONArray results = new JSONObject(get(String.format(java.util.Locale.ROOT, TMDB, tv ? "tv" : "movie", key, region.toUpperCase(java.util.Locale.ROOT),
                    c.provider, since))).optJSONArray("results");
            for (int i = 0; results != null && i < results.length() && out.size() < 6; i++) {
                JSONObject r = results.getJSONObject(i);
                String poster = r.optString("poster_path", ""), back = r.optString("backdrop_path", "");
                if (poster.isEmpty() || "null".equals(poster)) continue;
                Title t = new Title();
                t.tv = tv;
                t.title = r.optString(tv ? "name" : "title");
                t.summary = r.optString("overview", "");
                String date = r.optString(tv ? "first_air_date" : "release_date", "");
                t.year = date.length() >= 4 ? date.substring(0, 4) : "";
                JSONArray ids = r.optJSONArray("genre_ids");
                t.genre = ids != null && ids.length() > 0 && GENRES.containsKey(ids.optInt(0)) ? GENRES.get(ids.optInt(0)) : "";
                t.image = "https://image.tmdb.org/t/p/w500" + poster;
                t.backdrop = back.isEmpty() || "null".equals(back) ? null : "https://image.tmdb.org/t/p/w780" + back;
                t.hasPoster = true;
                t.tmdbId = r.optInt("id");
                t.service = c.providerLogo;
                out.add(t);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---- Netflix's top ten ------------------------------------------------------------------------------

    private static final Pattern NF_ITEM = Pattern.compile("\"__typename\":\"PulseTop10ItemEntity\"");
    private static final Pattern NF_RANK = Pattern.compile("\"weeklyRank\":(\\d+)");
    private static final Pattern NF_TITLE = Pattern.compile("\"Top10PulseVideo\",\"title\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern NF_ABOUT = Pattern.compile("\"shortSynopsis\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern NF_YEAR = Pattern.compile("\"releaseYear\":(\\d{4})");
    private static final Pattern NF_AGE = Pattern.compile("\"maturityRating\":\"([^\"]*)\"");
    private static final Pattern NF_LOGO = Pattern.compile("\"logoArt\".*?\"url\":\"([^\"]+)\"");
    private static final Pattern NF_STORY = Pattern.compile("\"storyArt\".*?\"url\":\"([^\"]+)\"");
    private static final Pattern NF_SEASON = Pattern.compile("^(.*?): ((?:Season|Limited Series|Series|Part|Volume|S\\d+).*)$");

    /**
     * Netflix's weekly top ten for a country (its Tudum page, "united-kingdom"), films or TV: rank, title, synopsis,
     * year, age, its wide picture and its logo, read from the page's own data.
     */
    static List<Title> netflix(String country, boolean tv) {
        List<Title> out = new ArrayList<>();
        String page;
        try { page = get("https://www.netflix.com/tudum/top10/" + country + (tv ? "/tv" : "")); } catch (Exception e) { return out; }
        Matcher items = NF_ITEM.matcher(page);
        List<Integer> starts = new ArrayList<>();
        while (items.find()) starts.add(items.start());
        java.util.Set<Integer> ranks = new java.util.HashSet<>();
        for (int i = 0; i < starts.size(); i++) {
            String seg = page.substring(starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : Math.min(page.length(), starts.get(i) + 20000));
            Matcher r = NF_RANK.matcher(seg), n = NF_TITLE.matcher(seg);
            if (!r.find() || !n.find()) continue;
            int rank = Integer.parseInt(r.group(1));
            if (!ranks.add(rank)) continue;
            Title t = new Title();
            t.netflix = true;
            t.tv = tv;
            t.rank = rank;
            t.title = unescape(n.group(1));
            Matcher m = NF_SEASON.matcher(t.title);
            if (tv && m.matches()) { t.title = m.group(1); t.season = m.group(2); }
            Matcher a = NF_ABOUT.matcher(seg), y = NF_YEAR.matcher(seg), g = NF_AGE.matcher(seg), lo = NF_LOGO.matcher(seg), st = NF_STORY.matcher(seg);
            t.summary = a.find() ? unescape(a.group(1)) : "";
            t.year = y.find() ? y.group(1) : "";
            t.age = g.find() && !g.group(1).isEmpty() ? g.group(1) : null;
            t.logo = lo.find() ? unescape(lo.group(1)) : null;
            t.image = st.find() ? unescape(st.group(1)) : null;
            t.genre = "";
            if (t.image != null) out.add(t);
        }
        out.sort((p, q) -> Integer.compare(p.rank, q.rank));
        return out.size() > 10 ? new ArrayList<>(out.subList(0, 10)) : out;
    }

    private static String unescape(String s) {
        return s.replace("\\u0026", "&").replace("\\'", "'").replace("\\\"", "\"").replace("\\/", "/");
    }

    // ---- badges -------------------------------------------------------------------------------------

    private static final Pattern BADGE = Pattern.compile("\\{\"\\$kind\":\"MetadataBadge\"[^}]*\\}");
    private static final Pattern FIELD = Pattern.compile("\"(\\$type|displayText|fallbackText)\":\"([^\"]*)\"");
    private static final long BADGES_MS = 24 * 60 * 60_000L;
    private static final Pattern TRAILER = Pattern.compile("\"videoUrl\":\\{\"wide\":\"(https://[^\"]+)\"");

    /**
     * Each title's badges from its Apple TV page (the store link redirects there): the first run of MetadataBadge
     * entries on it is the title's own, as the page shows under its name. Read four at a time at background priority,
     * and kept a day in the "trend_badges_v5" preferences, so a refresh reads only titles new to the chart.
     */
    static void badges(android.content.Context c, List<Title> titles) {
        android.content.SharedPreferences prefs = c.getSharedPreferences("trend_badges_v5", android.content.Context.MODE_PRIVATE);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4, r -> new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }));
        List<java.util.concurrent.Callable<Void>> jobs = new ArrayList<>();
        for (Title t : titles) {
            // Netflix's titles have no store page: they are kept by name, for their preview clip only.
            boolean page = t.link != null && !t.link.isEmpty();
            String key = page ? t.link : "nf:" + (t.tv ? "tv:" : "film:") + t.title;
            if (key == null || key.isEmpty()) continue;
            String kept = prefs.getString(key, null);
            if (kept != null) {
                int bar = kept.indexOf('|');
                long at = bar > 0 ? Long.parseLong(kept.substring(0, bar)) : 0;
                if (System.currentTimeMillis() - at < BADGES_MS) {
                    apply(t, kept.substring(bar + 1));
                    if (t.summary.isEmpty()) t.summary = prefs.getString("about:" + key, "");
                    continue;
                }
            }
            jobs.add(() -> {
                String found = page ? read(t.link) : "";
                // A series' rating from TVmaze (its viewers' average out of ten), as Apple shows none for TV.
                // Its synopsis too: Apple's TV chart has none.
                if (t.tv && page) {
                    String[] show = tvmaze(t.title);
                    if (show != null && show[0] != null) found = (found == null ? "" : found) + "tvmaze=" + show[0] + ";";
                    if (show != null && !show[1].isEmpty()) {
                        if (t.summary.isEmpty()) t.summary = show[1];
                        prefs.edit().putString("about:" + key, show[1]).apply();
                    }
                }
                // Something to play: a series has no trailer on its store page and Netflix's titles no page at all,
                // so the store's search gives an episode's preview clip, or a film's trailer when it sells it.
                if (t.preview == null && (found == null || !found.contains("trailer="))) {
                    String clip = searchPreview(t.title, t.tv, c.getString(R.string.trending_country));
                    if (clip != null) found = (found == null ? "" : found) + "preview=" + clip + ";";
                }
                if (found != null && !found.isEmpty()) {
                    prefs.edit().putString(key, System.currentTimeMillis() + "|" + found).apply();
                    apply(t, found);
                }
                return null;
            });
        }
        try { pool.invokeAll(jobs, 3, java.util.concurrent.TimeUnit.MINUTES); } catch (InterruptedException ignored) { }
        pool.shutdownNow();
    }

    private static final long LOGO_RETRY_MS = 24L * 60 * 60_000L;

    /**
     * Each title's own logo (its name as drawn on the poster) from TMDB, for the foot of a widened card: found by its
     * TMDB id, or by a search for its name; kept for good once found, and a title with none asked again after a day.
     * Netflix's titles bring their own.
     */
    static void logos(android.content.Context c, List<Title> titles, String key) {
        if (key.isEmpty()) return;
        android.content.SharedPreferences prefs = c.getSharedPreferences("trend_logos", android.content.Context.MODE_PRIVATE);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4, r -> new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }));
        List<java.util.concurrent.Callable<Void>> jobs = new ArrayList<>();
        for (Title t : titles) {
            if (t.logo != null) continue;
            String name = (t.tv ? "tv:" : "film:") + t.title;
            String kept = prefs.getString(name, null);
            if (kept != null && !kept.startsWith("-")) { t.logo = kept; continue; }
            if (kept != null && System.currentTimeMillis() - Long.parseLong(kept.substring(1)) < LOGO_RETRY_MS) continue;
            jobs.add(() -> {
                String found = logo(key, t);
                prefs.edit().putString(name, found != null ? found : "-" + System.currentTimeMillis()).apply();
                if (found != null) t.logo = found;
                return null;
            });
        }
        try { pool.invokeAll(jobs, 1, java.util.concurrent.TimeUnit.MINUTES); } catch (InterruptedException ignored) { }
        pool.shutdownNow();
    }

    /** The title's English (or wordless) PNG logo on TMDB, or null. */
    private static String logo(String key, Title t) {
        String type = t.tv ? "tv" : "movie";
        try {
            int id = t.tmdbId;
            if (id == 0) {
                JSONArray results = new JSONObject(get("https://api.themoviedb.org/3/search/" + type + "?api_key=" + key
                        + "&query=" + java.net.URLEncoder.encode(t.title, "UTF-8"))).optJSONArray("results");
                if (results == null || results.length() == 0) return null;
                // The one with exactly this name, nearest the year given; else TMDB's best match.
                String want = t.title.toLowerCase(java.util.Locale.ROOT);
                int best = -1, gap = Integer.MAX_VALUE;
                for (int i = 0; i < results.length(); i++) {
                    JSONObject r = results.getJSONObject(i);
                    if (!r.optString(t.tv ? "name" : "title").toLowerCase(java.util.Locale.ROOT).equals(want)) continue;
                    String date = r.optString(t.tv ? "first_air_date" : "release_date", "");
                    int g = date.length() >= 4 && t.year != null && t.year.matches("\\d{4}")
                            ? Math.abs(Integer.parseInt(date.substring(0, 4)) - Integer.parseInt(t.year)) : 50;
                    if (g < gap) { gap = g; best = i; }
                }
                id = results.getJSONObject(Math.max(best, 0)).optInt("id");
            }
            JSONArray logos = new JSONObject(get("https://api.themoviedb.org/3/" + type + "/" + id + "/images?api_key=" + key
                    + "&include_image_language=en,null")).optJSONArray("logos");
            String fallback = null;
            for (int i = 0; logos != null && i < logos.length(); i++) {
                JSONObject l = logos.getJSONObject(i);
                String path = l.optString("file_path", "");
                if (!path.endsWith(".png")) continue;
                String url = "https://image.tmdb.org/t/p/w300" + path;
                if ("en".equals(l.optString("iso_639_1"))) return url;
                if (fallback == null) fallback = url;
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }

    /** "type=text;type=text;..." for the title's own badges, or null when the page could not be read. */
    private static String read(String link) {
        String page;
        try { page = get(link); } catch (Exception e) { return null; }
        StringBuilder out = new StringBuilder();
        // The page's trailer, an HLS playlist: the first wide video on it is the title's own.
        Matcher video = TRAILER.matcher(page);
        if (video.find()) out.append("trailer=").append(video.group(1).replace("\\u0026", "&")).append(';');
        int first = page.indexOf("{\"$kind\":\"MetadataBadge\"");
        if (first < 0) return out.toString();
        Matcher b = BADGE.matcher(page);
        b.region(first, page.length());
        int next = first;
        // The title's badges come in a few lists side by side ("ratingBadges":[...],"playbackBadges":[...]); the run
        // ends at the first badge further on than that.
        while (b.find() && b.start() <= next + 40) {
            next = b.end();
            String type = "", text = "";
            Matcher f = FIELD.matcher(b.group());
            while (f.find()) {
                if (f.group(1).equals("$type")) type = f.group(2);
                else if (text.isEmpty()) text = f.group(2);
            }
            if (b.group().contains("\"rotten\"")) text += "!";
            if (b.group().contains("\"certifiedfresh\"")) text += "*";
            out.append(type).append('=').append(text).append(';');
        }
        return out.toString();
    }

    /** {rating out of ten or null, synopsis as plain text} for a series, from TVmaze; null when not found. */
    /**
     * A preview clip from the iTunes Search API: for a series the first of its episodes' previews whose season is
     * named after it, for a film its trailer; null when the store has none.
     */
    private static String searchPreview(String title, boolean tv, String country) {
        try {
            String q = "https://itunes.apple.com/search?country=" + country + "&limit=5&media=" + (tv ? "tvShow&entity=tvEpisode" : "movie&entity=movie")
                    + "&term=" + java.net.URLEncoder.encode(title, "UTF-8");
            JSONArray results = new JSONObject(get(q)).optJSONArray("results");
            String want = title.toLowerCase(java.util.Locale.ROOT);
            for (int i = 0; results != null && i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                String name = (tv ? r.optString("collectionName") : r.optString("trackName")).toLowerCase(java.util.Locale.ROOT);
                String clip = r.optString("previewUrl", "");
                if (!clip.isEmpty() && name.startsWith(want)) return clip;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String[] tvmaze(String title) {
        try {
            JSONObject show = new JSONObject(get("https://api.tvmaze.com/singlesearch/shows?q=" + java.net.URLEncoder.encode(title, "UTF-8")));
            double avg = show.optJSONObject("rating") == null ? 0 : show.getJSONObject("rating").optDouble("average", 0);
            String about = show.isNull("summary") ? "" : android.text.Html.fromHtml(show.optString("summary", ""),
                    android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim();
            return new String[]{avg > 0 ? String.format(java.util.Locale.ROOT, "%.1f", avg) : null, about};
        } catch (Exception e) {
            return null;
        }
    }

    private static void apply(Title t, String badges) {
        for (String b : badges.split(";")) {
            int eq = b.indexOf('=');
            if (eq < 0) continue;
            String type = b.substring(0, eq), text = b.substring(eq + 1);
            switch (type) {
                case "4k": t.quality = "4K"; break;
                case "hd": if (t.quality == null) t.quality = "HD"; break;
                case "dolby-vision": t.dolbyVision = true; break;
                case "hdr": case "hdr10": t.hdr = true; break;
                case "hdr10-plus": t.hdr10plus = true; break;
                case "dolby-atmos": t.atmos = true; break;
                case "rating": if (t.age == null && !text.isEmpty()) t.age = text; break;
                case "tvmaze": t.stars = text; break;
                case "trailer": t.trailer = text; break;
                case "preview": if (t.preview == null) t.preview = text; break;
                case "rotten-tomatoes":
                    if (t.tomatoes == null && !text.isEmpty()) {
                        t.rotten = text.contains("!");
                        t.certified = text.contains("*");
                        t.tomatoes = text.replace("!", "").replace("*", "");
                    }
                    break;
                default: break;
            }
        }
    }

    private static String label(JSONObject e, String key) {
        JSONObject o = e.optJSONObject(key);
        return o == null ? "" : o.optString("label", "");
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        // As a browser: the store answers anything else with an "item not available" stub instead of the page.
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(10000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 3 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
