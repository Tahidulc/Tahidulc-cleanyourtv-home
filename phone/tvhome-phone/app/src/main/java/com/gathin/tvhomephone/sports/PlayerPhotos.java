package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.gathin.tvhomephone.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.Locale;

/**
 * A player's photo for the line-ups, as the TV finds them: TheSportsDB's cut-out (head and shoulders, its small size),
 * the same club first or else the same name, then ESPN's headshot by his id. Each name is looked up once and the answer
 * kept ("" for none), the pictures kept on the phone; when TheSportsDB says "too many" it is left alone for ten
 * minutes. Off the main thread.
 */
public final class PlayerPhotos {
    private PlayerPhotos() {}

    private static long coolUntil;

    /** The photo for a player of this club; null when there is none (yet). */
    public static Bitmap get(Context c, String name, String club, String espnId) {
        if (name == null || name.isEmpty()) return null;
        SharedPreferences kept = c.getApplicationContext().getSharedPreferences("player_photos", Context.MODE_PRIVATE);
        String url = kept.getString(name, null);
        if (url == null) {
            url = lookUp(name, club);
            if (url == null) return null;   // the lookup failed: another time
            if (url.isEmpty() && espnId != null && !espnId.isEmpty()) {
                String espn = "https://a.espncdn.com/i/headshots/soccer/players/full/" + espnId + ".png";
                if (picture(c, espn) != null) url = espn;
            }
            kept.edit().putString(name, url).apply();
        }
        return url.isEmpty() ? null : picture(c, url);
    }

    /** The best cut-out's address ("" for none), null when TheSportsDB could not be asked. */
    static String lookUp(String name, String club) {
        if (System.currentTimeMillis() < coolUntil) return null;
        try {
            JSONObject root = new JSONObject(Net.get("https://www.thesportsdb.com/api/v1/json/123/searchplayers.php?p="
                    + URLEncoder.encode(name, "UTF-8")));
            JSONArray players = root.optJSONArray("player");
            String best = "";
            for (int i = 0; players != null && i < players.length(); i++) {
                JSONObject p = players.getJSONObject(i);
                if (!"Soccer".equalsIgnoreCase(p.optString("strSport"))) continue;
                String img = p.optString("strCutout", "");
                if (img.isEmpty() || "null".equals(img)) img = p.optString("strThumb", "");
                if (img.isEmpty() || "null".equals(img)) continue;
                boolean sameClub = sameClub(p.optString("strTeam", ""), club);
                if (!sameClub && !sameName(name, p.optString("strPlayer", ""))) continue;
                if (best.isEmpty() || sameClub) best = img + "/small";
                if (sameClub) break;
            }
            return best;
        } catch (Exception e) {
            if (String.valueOf(e.getMessage()).contains("429") || e instanceof java.io.FileNotFoundException) {
                coolUntil = System.currentTimeMillis() + 10 * 60_000L;
            }
            return null;
        }
    }

    /** A picture from its address, kept in the app's cache; null when it does not come. */
    public static Bitmap picture(Context c, String url) {
        File dir = new File(c.getCacheDir(), "player_photos");
        File f = new File(dir, Integer.toHexString(url.hashCode()) + ".img");
        try {
            if (!f.exists()) {
                if (!dir.exists() && !dir.mkdirs()) return null;
                HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                h.setConnectTimeout(8000);
                h.setReadTimeout(10000);
                if (h.getResponseCode() != 200) return null;
                File tmp = new File(dir, f.getName() + ".part");
                try (InputStream in = h.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                if (!tmp.renameTo(f)) return null;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 1;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean sameName(String a, String b) {
        String[] x = plain(a).split(" "), y = plain(b).split(" ");
        if (x.length == 0 || y.length == 0 || x[0].isEmpty() || y[0].isEmpty()) return false;
        if (!x[x.length - 1].equals(y[y.length - 1])) return false;
        if (x.length == 1 || y.length == 1) return true;
        return x[0].equals(y[0]) || x[0].charAt(0) == y[0].charAt(0) && (x[0].length() <= 2 || y[0].length() <= 2
                || x[0].startsWith(y[0]) || y[0].startsWith(x[0]));
    }

    static boolean sameClub(String theirs, String ours) {
        if (theirs == null || ours == null || theirs.isEmpty() || ours.isEmpty()) return false;
        String a = club(theirs), b = club(ours);
        return !a.isEmpty() && !b.isEmpty() && (a.contains(b) || b.contains(a));
    }

    private static String club(String s) {
        return (" " + plain(s.replace("&", " and ")) + " ").replaceAll(" (a?fc|cf|sc|bv|real|borussia) ", " ").trim();
    }

    static String plain(String s) {
        String t = (s == null ? "" : s).replace("ß", "ss").replace("ø", "o").replace("Ø", "O").replace("æ", "ae")
                .replace("Æ", "Ae").replace("œ", "oe").replace("ł", "l").replace("Ł", "L").replace("đ", "d").replace("Đ", "D")
                .replace("ı", "i");
        String n = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
    }
}
