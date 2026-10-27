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

        String poster(int w, int h) { return art + "/" + w + "x" + h + "bb.jpg"; }
        String crop(int w, int h) { return art + "/" + w + "x" + h + "sr.jpg"; }
    }

    private static final String FEED = "https://itunes.apple.com/%s/rss/%s/limit=%d/json";
    private static final Pattern SIZE = Pattern.compile("/\\d+x\\d+[a-z]*\\.(png|jpg)$");
    private static final Pattern SEASON = Pattern.compile("^(.*), ((Season|Series|Part|Volume) .*)$");

    static List<Title> read(String country, int each) {
        List<Title> films = chart(country, "topmovies", each, false);
        List<Title> tv = chart(country, "toptvseasons", each, true);
        List<Title> out = new ArrayList<>();
        for (int i = 0; i < Math.max(films.size(), tv.size()); i++) {
            if (i < films.size()) out.add(films.get(i));
            if (i < tv.size()) out.add(tv.get(i));
        }
        return out;
    }

    private static List<Title> chart(String country, String name, int limit, boolean tv) {
        List<Title> out = new ArrayList<>();
        try {
            JSONArray entries = new JSONObject(get(String.format(FEED, country, name, limit)))
                    .getJSONObject("feed").optJSONArray("entry");
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

    // ---- badges -------------------------------------------------------------------------------------

    private static final Pattern BADGE = Pattern.compile("\\{\"\\$kind\":\"MetadataBadge\"[^}]*\\}");
    private static final Pattern FIELD = Pattern.compile("\"(\\$type|displayText|fallbackText)\":\"([^\"]*)\"");
    private static final long BADGES_MS = 24 * 60 * 60_000L;
    private static final Pattern TRAILER = Pattern.compile("\"videoUrl\":\\{\"wide\":\"(https://[^\"]+)\"");

    /**
     * Each title's badges from its Apple TV page (the store link redirects there): the first run of MetadataBadge
     * entries on it is the title's own, as the page shows under its name. Read four at a time at background priority,
     * and kept a day in the "trend_badges_v4" preferences, so a refresh reads only titles new to the chart.
     */
    static void badges(android.content.Context c, List<Title> titles) {
        android.content.SharedPreferences prefs = c.getSharedPreferences("trend_badges_v4", android.content.Context.MODE_PRIVATE);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4, r -> new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }));
        List<java.util.concurrent.Callable<Void>> jobs = new ArrayList<>();
        for (Title t : titles) {
            if (t.link == null || t.link.isEmpty()) continue;
            String kept = prefs.getString(t.link, null);
            if (kept != null) {
                int bar = kept.indexOf('|');
                long at = bar > 0 ? Long.parseLong(kept.substring(0, bar)) : 0;
                if (System.currentTimeMillis() - at < BADGES_MS) {
                    apply(t, kept.substring(bar + 1));
                    if (t.summary.isEmpty()) t.summary = prefs.getString("about:" + t.link, "");
                    continue;
                }
            }
            jobs.add(() -> {
                String found = read(t.link);
                // A series' rating from TVmaze (its viewers' average out of ten), as Apple shows none for TV.
                // Its synopsis too: Apple's TV chart has none.
                if (t.tv) {
                    String[] show = tvmaze(t.title);
                    if (show != null && show[0] != null) found = (found == null ? "" : found) + "tvmaze=" + show[0] + ";";
                    if (show != null && !show[1].isEmpty()) {
                        if (t.summary.isEmpty()) t.summary = show[1];
                        prefs.edit().putString("about:" + t.link, show[1]).apply();
                    }
                }
                if (found != null && !found.isEmpty()) {
                    prefs.edit().putString(t.link, System.currentTimeMillis() + "|" + found).apply();
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
