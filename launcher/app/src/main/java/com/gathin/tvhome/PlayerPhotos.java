package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * A player's photo when ESPN has none: TheSportsDB's cut-out (its free test key), the small version, cropped to the
 * face. Each name is looked up once and the answer kept ("" for none), so later line-ups need no lookups at all.
 * Off the main thread.
 */
final class PlayerPhotos {
    private PlayerPhotos() { }

    static Bitmap find(Context ctx, String fullName, String team, int px) {
        if (fullName == null || fullName.isEmpty()) return null;
        SharedPreferences cache = ctx.getSharedPreferences("player_photos2", Context.MODE_PRIVATE);
        String url = cache.getString(fullName, null);
        if (url == null) {
            url = lookUp(fullName, team);
            if (url == null) return null;   // the lookup failed: try again another time
            cache.edit().putString(fullName, url).apply();
        }
        if (url.isEmpty()) return null;
        Bitmap b = ScoresTicker.cachedLogo(ctx, url, px);
        return b == null ? null : face(b);
    }

    /** A player's cut-out, whole (head and shoulders in the club's shirt), and his nationality as TheSportsDB has it. */
    static final class Cutout {
        /** The cut-out's address ("" when there is none); null when the lookup failed and may be tried again. */
        String url;
        String nation = "";
    }

    /** The cut-out's picture, fetched (and kept); null without one. */
    static Bitmap photo(Context ctx, Cutout c) {
        return c == null || c.url == null || c.url.isEmpty() ? null : ScoresTicker.cachedLogo(ctx, c.url, 350);
    }

    /**
     * Where the whole cut-out is (its 350-pixel version) and the player's nationality, for the line-up card; looked up
     * once a name and kept ("" for none); a null url when the lookup failed. Off the main thread.
     */
    static Cutout cutout(Context ctx, String fullName, String team) {
        Cutout out = new Cutout();
        if (fullName == null || fullName.isEmpty()) return out;
        SharedPreferences cache = ctx.getSharedPreferences("player_cutouts2", Context.MODE_PRIVATE);
        String kept = cache.getString(fullName, null);
        if (kept == null) {
            String[] found = lookUpWhole(fullName, team);
            if (found == null) return out;   // the lookup failed: try again another time
            kept = found[0] + "\t" + found[1];
            cache.edit().putString(fullName, kept).apply();
        }
        String[] parts = kept.split("\t", -1);
        out.url = parts[0];
        out.nation = parts.length > 1 ? parts[1] : "";
        return out;
    }

    /** {cut-out url ("" for none), nationality} for the best match (same club first); null on failure. */
    private static String[] lookUpWhole(String name, String team) {
        try {
            JSONObject root = new JSONObject(get("https://www.thesportsdb.com/api/v1/json/123/searchplayers.php?p="
                    + URLEncoder.encode(name, "UTF-8")));
            JSONArray players = root.optJSONArray("player");
            String[] best = {"", ""};
            for (int i = 0; players != null && i < players.length(); i++) {
                JSONObject p = players.getJSONObject(i);
                if (!"Soccer".equalsIgnoreCase(p.optString("strSport"))) continue;
                String img = p.optString("strCutout", "");
                if ("null".equals(img)) img = "";
                boolean sameClub = team != null && !team.isEmpty() && p.optString("strTeam", "").contains(team);
                // Only him: the same club, or the same name ("Ben White" also finds Benjamin Whiteman).
                if (!sameClub && !sameName(name, p.optString("strPlayer", ""))) continue;
                String nation = p.optString("strNationality", "");
                if ("null".equals(nation)) nation = "";
                if (best[0].isEmpty() || sameClub) best = new String[]{img.isEmpty() ? "" : img + "/medium", nation};
                if (sameClub) break;
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    /** The cut-out's small version for the best match (same club first), "" when there is none, null on failure. */
    private static String lookUp(String name, String team) {
        try {
            JSONObject root = new JSONObject(get("https://www.thesportsdb.com/api/v1/json/123/searchplayers.php?p="
                    + URLEncoder.encode(name, "UTF-8")));
            JSONArray players = root.optJSONArray("player");
            String best = "";
            for (int i = 0; players != null && i < players.length(); i++) {
                JSONObject p = players.getJSONObject(i);
                if (!"Soccer".equalsIgnoreCase(p.optString("strSport"))) continue;
                String img = p.optString("strCutout", "");
                if (img.isEmpty() || "null".equals(img)) img = p.optString("strThumb", "");
                if (img.isEmpty() || "null".equals(img)) continue;
                boolean sameClub = team != null && !team.isEmpty() && p.optString("strTeam", "").contains(team);
                if (!sameClub && !sameName(name, p.optString("strPlayer", ""))) continue;
                if (best.isEmpty() || sameClub) best = img + "/tiny";
                if (sameClub) break;
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- F1 drivers ------------------------------------------------------------------------------------------

    /** An F1 driver's cut-out by his surname (TheSportsDB's, a motorsport entry of that surname); null without one. */
    static Bitmap driver(Context ctx, String surname) {
        if (surname == null || surname.isEmpty()) return null;
        SharedPreferences cache = ctx.getSharedPreferences("driver_photos", Context.MODE_PRIVATE);
        String url = cache.getString(surname, null);
        if (url == null) {
            try {
                JSONObject root = new JSONObject(get("https://www.thesportsdb.com/api/v1/json/123/searchplayers.php?p="
                        + URLEncoder.encode(surname, "UTF-8")));
                JSONArray players = root.optJSONArray("player");
                url = "";
                for (int i = 0; players != null && i < players.length(); i++) {
                    JSONObject p = players.getJSONObject(i);
                    if (!"Motorsport".equalsIgnoreCase(p.optString("strSport"))) continue;
                    if (!p.optString("strPlayer").toLowerCase(java.util.Locale.ROOT).endsWith(surname.toLowerCase(java.util.Locale.ROOT))) continue;
                    String img = p.optString("strCutout", "");
                    if (!img.isEmpty() && !"null".equals(img)) { url = img + "/medium"; break; }
                }
                cache.edit().putString(surname, url).apply();
            } catch (Exception e) {
                return null;
            }
        }
        return url.isEmpty() ? null : ScoresTicker.cachedLogo(ctx, url, 350);
    }

    // ---- his nationality from ESPN, when TheSportsDB has none --------------------------------------------------

    /**
     * {nationality, ESPN's flag picture} for an ESPN player (its athlete record), looked up once and kept ("" for
     * none); null when ESPN cannot be asked. Off the main thread.
     */
    static String[] espnNation(Context ctx, String slug, String athleteId) {
        if (athleteId == null || athleteId.isEmpty()) return null;
        SharedPreferences cache = ctx.getSharedPreferences("espn_nations", Context.MODE_PRIVATE);
        String kept = cache.getString(athleteId, null);
        if (kept == null) {
            try {
                JSONObject a = new JSONObject(get("https://sports.core.api.espn.com/v2/sports/soccer/leagues/"
                        + (slug == null || slug.isEmpty() ? "eng.1" : slug) + "/athletes/" + athleteId + "?lang=en&region=gb"));
                String nation = a.optString("citizenship", "");
                JSONObject flag = a.optJSONObject("flag");
                kept = nation + "\t" + (flag == null ? "" : flag.optString("href", ""));
                cache.edit().putString(athleteId, kept).apply();
            } catch (Exception e) {
                return null;
            }
        }
        String[] parts = kept.split("\t", -1);
        return new String[]{parts[0], parts.length > 1 ? parts[1] : ""};
    }

    // ---- when TheSportsDB has no cut-out: Wikipedia's photo of him ----------------------------------------------

    private static final String UA = "cleanyourtv-launcher/1.0 (personal TV launcher; github.com/Gathin23/cleanyourtv)";

    /**
     * A photo of the player from Wikipedia, cropped to his head and shoulders: his English article's picture (by his
     * name, or "name (footballer)"), else the picture Wikidata keeps for him (from any language's article). Looked up
     * once a name and kept ("" for none). An article counts only when it says he is a footballer. Off the main thread.
     */
    static Bitmap wikiPhoto(Context ctx, String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return null;
        SharedPreferences cache = ctx.getSharedPreferences("player_wiki2", Context.MODE_PRIVATE);
        String url = cache.getString(fullName, null);
        if (url == null) {
            url = wikiLookUp(fullName.trim());
            android.util.Log.i("LineupCard", "wiki " + fullName + " -> " + url);
            if (url == null) return null;   // failed: another time
            cache.edit().putString(fullName, url).apply();
        }
        if (url.isEmpty()) return null;
        Bitmap b = ScoresTicker.cachedLogo(ctx, url, 400);
        if (b == null) android.util.Log.i("LineupCard", "wiki photo not fetched: " + url);
        return b == null ? null : headAndShoulders(b);
    }

    /** The photo's address ("" for none), or null when Wikipedia could not be asked. */
    private static String wikiLookUp(String name) {
        try {
            String title = name.replace(' ', '_');
            String[] titles = {title + "_(footballer)", title};
            String wikidata = null;
            for (String t : titles) {
                JSONObject page;
                try {
                    page = new JSONObject(getWiki("https://en.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(t, "UTF-8").replace("+", "_")));
                } catch (java.io.FileNotFoundException missing) {
                    continue;
                }
                String about = (page.optString("description", "") + " " + page.optString("extract", "")).toLowerCase(java.util.Locale.ROOT);
                if (!about.contains("footballer") && !about.contains("football player")) continue;
                JSONObject thumb = page.optJSONObject("thumbnail");
                if (thumb != null && !thumb.optString("source", "").isEmpty()) return thumb.optString("source");
                wikidata = page.optString("wikibase_item", null);
                if (wikidata != null) break;
            }
            if (wikidata == null) return "";
            JSONObject entity = new JSONObject(getWiki("https://www.wikidata.org/wiki/Special:EntityData/" + wikidata + ".json"))
                    .getJSONObject("entities").getJSONObject(wikidata);
            JSONArray images = entity.optJSONObject("claims") == null ? null : entity.getJSONObject("claims").optJSONArray("P18");
            if (images == null || images.length() == 0) return "";
            String file = images.getJSONObject(0).getJSONObject("mainsnak").getJSONObject("datavalue").getString("value");
            return "https://commons.wikimedia.org/wiki/Special:FilePath/" + URLEncoder.encode(file.replace(' ', '_'), "UTF-8") + "?width=330";
        } catch (Exception e) {
            android.util.Log.i("LineupCard", "wiki lookup " + name + ": " + e);
            return null;
        }
    }

    private static String getWiki(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", UA);
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    /**
     * A photo cut down to the face, found by Android's face finder (a square about three eye-widths across, centred a
     * little below the eyes); without a face, the top middle of the picture.
     */
    static Bitmap headAndShoulders(Bitmap src) {
        int w = src.getWidth() & ~1, h = src.getHeight();
        if (w < 40 || h < 40) return src;
        Bitmap rgb = src.copy(Bitmap.Config.RGB_565, false);
        if (rgb.getWidth() != w) rgb = Bitmap.createBitmap(rgb, 0, 0, w, h);
        android.media.FaceDetector.Face[] faces = new android.media.FaceDetector.Face[1];
        int found = 0;
        try {
            found = new android.media.FaceDetector(w, h, 1).findFaces(rgb, faces);
        } catch (RuntimeException ignored) { }
        float cx, cy, side;
        if (found > 0 && faces[0] != null) {
            android.graphics.PointF mid = new android.graphics.PointF();
            faces[0].getMidPoint(mid);
            float eyes = faces[0].eyesDistance();
            // The face alone (it goes in a round frame on shoulders in his club's colours, so no old shirt shows).
            cx = mid.x;
            side = eyes * 3.4f;
            cy = mid.y + eyes * 0.35f;
        } else {
            side = Math.min(w, h) * 0.5f;
            cx = w / 2f;
            cy = side * 0.6f;
        }
        side = Math.min(side, Math.min(w, h));
        int left = Math.round(Math.max(0, Math.min(w - side, cx - side / 2)));
        int top = Math.round(Math.max(0, Math.min(h - side, cy - side / 2)));
        return Bitmap.createBitmap(src, left, top, Math.round(side), Math.round(side));
    }

    /** The same player's name: the same surname, and the same first name or its first letter (accents aside). */
    static boolean sameName(String a, String b) {
        String[] x = plain(a).split(" "), y = plain(b).split(" ");
        if (x.length == 0 || y.length == 0 || x[0].isEmpty() || y[0].isEmpty()) return false;
        if (!x[x.length - 1].equals(y[y.length - 1])) return false;
        if (x.length == 1 || y.length == 1) return true;
        return x[0].equals(y[0]) || x[0].charAt(0) == y[0].charAt(0) && (x[0].length() <= 2 || y[0].length() <= 2
                || x[0].startsWith(y[0]) || y[0].startsWith(x[0]));
    }

    private static String plain(String s) {
        String n = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
    }

    /** A cut-out shows the upper body: the top-middle square is the face. */
    private static Bitmap face(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int side = Math.round(Math.min(w, h) * 0.56f);
        int x = (w - side) / 2;
        return Bitmap.createBitmap(b, Math.max(0, x), 0, Math.min(side, w), Math.min(side, h));
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 512 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
