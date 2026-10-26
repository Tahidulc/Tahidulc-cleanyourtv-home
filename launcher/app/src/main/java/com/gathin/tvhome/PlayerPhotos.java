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
        SharedPreferences cache = ctx.getSharedPreferences("player_photos", Context.MODE_PRIVATE);
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
                if (best.isEmpty() || sameClub) best = img + "/tiny";
                if (sameClub) break;
            }
            return best;
        } catch (Exception e) {
            return null;
        }
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
