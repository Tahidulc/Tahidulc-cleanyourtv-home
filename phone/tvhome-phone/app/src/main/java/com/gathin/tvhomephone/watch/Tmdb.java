package com.gathin.tvhomephone.watch;

import android.content.Context;

import com.gathin.tvhomephone.Net;
import com.gathin.tvhomephone.sync.Tvs;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TMDB reads with the key a paired TV gave (Tvs.tmdbKey; never shown, logged or put in an error), kept 30 minutes in
 * memory and three days on disk, so a list opens at once and works away from Wi-Fi. Off the main thread.
 */
public final class Tmdb {
    private static final String API = "https://api.themoviedb.org/3/";
    private static final long FRESH_MS = 30 * 60_000L, KEEP_MS = 3L * 24 * 3600_000L;
    private static final Map<String, Object[]> MEMORY = new LinkedHashMap<String, Object[]>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Object[]> e) {
            return size() > 120;
        }
    };

    private Tmdb() {}

    /** A failed read, without the address (it carries the key). */
    public static final class Failed extends IOException {
        Failed(String why) {
            super(why);
        }
    }

    public static boolean ready(Context c) {
        return Tvs.tmdbKey(c) != null;
    }

    public static String image(String path, String size) {
        if (path == null || path.isEmpty() || path.equals("null")) return null;
        if (path.startsWith("http")) return path;
        return "https://image.tmdb.org/t/p/" + size + (path.startsWith("/") ? "" : "/") + path;
    }

    /** One TMDB path ("movie/popular?region=GB"): its JSON. */
    public static JSONObject get(Context c, String path) throws IOException {
        String key = Tvs.tmdbKey(c);
        if (key == null) throw new Failed("No TMDB key yet: pair with a TV");
        synchronized (MEMORY) {
            Object[] had = MEMORY.get(path);
            if (had != null && System.currentTimeMillis() - (Long) had[0] < FRESH_MS) return (JSONObject) had[1];
        }
        File disk = file(c, path);
        try {
            String url = API + path + (path.contains("?") ? "&" : "?") + "api_key=" + key;
            JSONObject j = new JSONObject(Net.get(url));
            synchronized (MEMORY) {
                MEMORY.put(path, new Object[]{System.currentTimeMillis(), j});
            }
            write(disk, j.toString());
            return j;
        } catch (IOException | JSONException e) {
            // Away from the network, or TMDB busy: the copy kept on disk, if it is recent enough.
            if (disk.exists() && System.currentTimeMillis() - disk.lastModified() < KEEP_MS) {
                try {
                    return new JSONObject(read(disk));
                } catch (IOException | JSONException ignored) {
                    // fall through
                }
            }
            throw new Failed("TMDB didn't answer");
        }
    }

    /** A list's results, from as many pages as asked (TMDB gives 20 a page); tvHint for paths that don't say. */
    public static List<Title> list(Context c, String path, Boolean tvHint, int pages) throws IOException {
        List<Title> out = new ArrayList<>();
        for (int p = 1; p <= pages; p++) {
            JSONObject j = get(c, path + (path.contains("?") ? "&" : "?") + "page=" + p);
            JSONArray r = j.optJSONArray("results");
            for (int i = 0; r != null && i < r.length(); i++) {
                Title t = Title.from(r.optJSONObject(i), tvHint);
                if (t != null && t.poster != null) out.add(t);
            }
            if (p >= j.optInt("total_pages", 1)) break;
        }
        return out;
    }

    /** A title's details with its credits, videos, logos and UK providers in one read. */
    public static JSONObject details(Context c, int id, boolean tv) throws IOException {
        return get(c, (tv ? "tv/" : "movie/") + id
                + "?append_to_response=credits,videos,images,watch/providers,release_dates&include_image_language=en,null");
    }

    /** The UK's providers out of a details read (watch/providers.results.GB), or null. */
    public static JSONObject providersGb(JSONObject details) {
        JSONObject w = details == null ? null : details.optJSONObject("watch/providers");
        JSONObject r = w == null ? null : w.optJSONObject("results");
        return r == null ? null : r.optJSONObject("GB");
    }

    private static File file(Context c, String path) {
        File dir = new File(c.getCacheDir(), "tmdb");
        if (!dir.exists() && !dir.mkdirs()) return new File(c.getCacheDir(), "tmdb-" + hash(path));
        return new File(dir, hash(path) + ".json");
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 12; i++) b.append(String.format("%02x", d[i]));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static void write(File f, String s) {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // memory only
        }
    }

    private static String read(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) break;
                n += r;
            }
            return new String(b, 0, n, StandardCharsets.UTF_8);
        }
    }

    /** TMDB's genre names, films' and series' ids together. */
    public static String genre(int id) {
        switch (id) {
            case 28: return "Action";
            case 12: return "Adventure";
            case 16: return "Animation";
            case 35: return "Comedy";
            case 80: return "Crime";
            case 99: return "Documentary";
            case 18: return "Drama";
            case 10751: return "Family";
            case 14: return "Fantasy";
            case 36: return "History";
            case 27: return "Horror";
            case 10402: return "Music";
            case 9648: return "Mystery";
            case 10749: return "Romance";
            case 878: return "Science Fiction";
            case 53: return "Thriller";
            case 10752: return "War";
            case 37: return "Western";
            case 10759: return "Action & Adventure";
            case 10762: return "Kids";
            case 10763: return "News";
            case 10764: return "Reality";
            case 10765: return "Sci-Fi & Fantasy";
            case 10766: return "Soap";
            case 10767: return "Talk";
            case 10768: return "War & Politics";
            default: return null;
        }
    }
}
