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
