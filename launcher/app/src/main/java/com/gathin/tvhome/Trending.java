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

        String poster(int w, int h) { return art == null ? image : art + "/" + w + "x" + h + "bb.jpg"; }
        String crop(int w, int h) { return art == null ? image : art + "/" + w + "x" + h + "sr.jpg"; }
    }

    private static final String FEED = "https://itunes.apple.com/%s/rss/%s/limit=%d/json";
    private static final Pattern SIZE = Pattern.compile("/\\d+x\\d+[a-z]*\\.(png|jpg)$");
    private static final Pattern SEASON = Pattern.compile("^(.*), ((Season|Series|Part|Volume) .*)$");

    /** A row of the trending section: a chart of the store's (overall or one genre) or Netflix's weekly top ten. */
    static final class Category {
        final String name;
        final boolean tv, netflix;
        final int genre;
        Category(String name, boolean tv, boolean netflix, int genre) { this.name = name; this.tv = tv; this.netflix = netflix; this.genre = genre; }
    }

    /** The rows, in the order the tabs show them: the two top tens, Netflix's two, then genres of films and of TV. */
    static List<Category> categories() {
        List<Category> c = new ArrayList<>();
        c.add(new Category("Top 10 Films", false, false, 0));
        c.add(new Category("Top 10 TV", true, false, 0));
        c.add(new Category("Netflix Films", false, true, 0));
        c.add(new Category("Netflix TV", true, true, 0));
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
            String key = t.netflix ? "nf:" + (t.tv ? "tv:" : "film:") + t.title : t.link;
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
                String found = t.netflix ? "" : read(t.link);
                // A series' rating from TVmaze (its viewers' average out of ten), as Apple shows none for TV.
                // Its synopsis too: Apple's TV chart has none.
                if (t.tv && !t.netflix) {
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
