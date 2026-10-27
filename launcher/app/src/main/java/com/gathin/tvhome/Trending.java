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
                out.add(t);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String label(JSONObject e, String key) {
        JSONObject o = e.optJSONObject(key);
        return o == null ? "" : o.optString("label", "");
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(10000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 2 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
