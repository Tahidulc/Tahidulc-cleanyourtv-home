package com.gathin.broadcast;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A photo of the ground a game is played at, for the line-ups' background: a freely licensed picture from Wikimedia
 * Commons (CC0, CC BY, CC BY-SA or public domain), from the stadium's own category first, preferring views from the
 * stands over the pitch, with its credit to show. Kept two months per ground (a miss three days). The page grades it
 * for the game's time of day.
 */
public final class Grounds {
    private Grounds() { }

    /** Wikimedia asks clients to say who they are; nothing about the family is sent. */
    private static final String UA = "TVHomeLauncher/1.0 (TV launcher; stadium backgrounds)";
    private static final String API = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search"
            + "&gsrnamespace=6&gsrlimit=40&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata&iiurlwidth=1920"
            + "&iiextmetadatafilter=Artist%7CLicenseShortName&gsrsearch=";
    private static final long KEEP_MS = 60L * 24 * 3600_000L, MISS_MS = 3L * 24 * 3600_000L;

    /** {photo: data URL (JPEG), credit: "Photo: …"} for the ground, or null (io thread; cached). */
    public static JSONObject ground(String stadium, File cacheDir) {
        if (stadium == null || stadium.trim().length() < 3 || cacheDir == null) return null;
        String name = stadium.trim();
        File dir = new File(cacheDir, "grounds");
        dir.mkdirs();
        String key = Integer.toHexString(name.toLowerCase(Locale.ROOT).hashCode());
        File jpg = new File(dir, key + ".jpg"), txt = new File(dir, key + ".txt"), miss = new File(dir, key + ".none");
        long now = System.currentTimeMillis();
        try {
            if (jpg.exists() && txt.exists() && now - jpg.lastModified() < KEEP_MS) return made(read(jpg), new String(read(txt), StandardCharsets.UTF_8));
            if (miss.exists() && now - miss.lastModified() < MISS_MS) return null;
            String[] queries = {"incategory:\"" + name + "\" filetype:bitmap", "\"" + name + "\" stadium filetype:bitmap"};
            for (String q : queries) {
                JSONObject best = pick(fetch(API + URLEncoder.encode(q, "UTF-8")), name, q.startsWith("incategory:"));
                if (best == null) continue;
                byte[] jpeg = shrink(get(best.getString("url"), 8 << 20));
                if (jpeg == null) continue;
                String credit = best.getString("credit");
                write(jpg, jpeg);
                write(txt, credit.getBytes(StandardCharsets.UTF_8));
                return made(jpeg, credit);
            }
            write(miss, new byte[0]);
        } catch (Exception e) {
            // no photo: the drawn ground, as before
        }
        return null;
    }

    private static JSONObject made(byte[] jpeg, String credit) throws JSONException {
        return new JSONObject().put("photo", "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)).put("credit", credit);
    }

    /** The best photo in a Commons search's answer {url, credit}, or null. inCategory: the ground's own category. */
    static JSONObject pick(JSONObject answer, String stadium, boolean inCategory) throws JSONException {
        JSONObject pages = answer == null ? null : answer.optJSONObject("query");
        pages = pages == null ? null : pages.optJSONObject("pages");
        if (pages == null) return null;
        JSONObject best = null;
        int bestScore = Integer.MIN_VALUE;
        JSONArray names = pages.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            JSONObject p = pages.getJSONObject(names.getString(i));
            JSONArray infos = p.optJSONArray("imageinfo");
            JSONObject ii = infos == null ? null : infos.optJSONObject(0);
            if (ii == null) continue;
            JSONObject md = ii.optJSONObject("extmetadata");
            String licence = md == null ? "" : value(md, "LicenseShortName");
            String title = p.optString("title");
            int s = score(title, ii.optInt("width"), ii.optInt("height"), ii.optString("mime"), licence, stadium, inCategory);
            if (s > bestScore) {
                String url = ii.optString("thumburl", ii.optString("url"));
                if (url.isEmpty()) continue;
                bestScore = s;
                String who = md == null ? "" : value(md, "Artist").replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
                if (who.length() > 40) who = who.substring(0, 40).trim() + "…";
                best = new JSONObject().put("url", url).put("credit", "Photo: " + (who.isEmpty() ? "" : who + ", ") + licence + " (Wikimedia Commons)");
            }
        }
        return bestScore < 0 ? null : best;
    }

    private static String value(JSONObject md, String k) {
        JSONObject o = md.optJSONObject(k);
        return o == null ? "" : o.optString("value", "").trim();
    }

    /**
     * Pure: how good a Commons file is as the line-ups' background (below 0: not to be used). A wide photo, at least
     * 1400 pixels across, freely licensed, of the ground from the stands or during a game; not its outside, a sign, a
     * statue or a map. Outside its own category the title must name the ground.
     */
    static int score(String title, int w, int h, String mime, String licence, String stadium, boolean inCategory) {
        if (title == null || h <= 0) return -1000;
        if (!"image/jpeg".equals(mime) || w < 1400) return -1000;
        double r = (double) w / h;
        if (r < 1.3 || r > 2.6) return -1000;
        String l = licence == null ? "" : licence.toLowerCase(Locale.ROOT);
        if (!(l.startsWith("cc0") || l.startsWith("cc by") || l.contains("public domain") || l.equals("pd"))) return -1000;
        // words as words ("sign" not in "design"): the title with its punctuation as spaces, a space each side
        String t = " " + title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9à-ÿ]+", " ") + " ";
        String ground = " " + stadium.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9à-ÿ]+", " ").trim() + " ";
        if (!inCategory && !t.contains(ground)) return -1000;
        int s = 0;
        for (String k : new String[]{"interior", "inside", "pitch", "panorama", "from the", "v", "vs", "match", "kick off", "crowd"}) {
            if (t.contains(" " + k + " ")) s += 3;
        }
        if (t.contains(" stand ") || t.contains(" end ")) s += 1;
        for (String k : new String[]{"facade", "façade", "exterior", "outside", "aerial", "statue", "sign", "station", "gate", "entrance",
                "plaque", "logo", "map", "museum", "shop", "ticket", "construction", "demolition", "car park"}) {
            if (t.contains(" " + k + " ")) s -= 4;
        }
        return s + Math.min(3, w / 1500);
    }

    /** At most 1920 across, JPEG (about 300 KB): what the page is given. */
    private static byte[] shrink(byte[] b) {
        if (b == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(b, 0, b.length, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 1920) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length, d);
        if (bm == null) return null;
        if (bm.getWidth() > 1920) {
            Bitmap s = Bitmap.createScaledBitmap(bm, 1920, Math.round(bm.getHeight() * 1920f / bm.getWidth()), true);
            bm.recycle();
            bm = s;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 82, out);
        bm.recycle();
        return out.toByteArray();
    }

    private static JSONObject fetch(String url) throws IOException, JSONException {
        return new JSONObject(new String(get(url, 2 << 20), StandardCharsets.UTF_8));
    }

    private static byte[] get(String url, int max) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("User-Agent", UA);
        try (java.io.InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > max) throw new IOException("too big");
            }
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private static byte[] read(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void write(File f, byte[] b) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(b);
        }
    }
}
