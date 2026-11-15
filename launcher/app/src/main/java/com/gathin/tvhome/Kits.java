package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A club's kits this season, from its Wikipedia article's infobox (the home, away and third kits as the article draws
 * them: shirt body, sleeves, shorts and socks colours). Read once a week a club and kept. Off the main thread.
 */
final class Kits {
    /** One kit's colours (0 for any the article does not give). */
    static final class Kit {
        int body, sleeves, shorts, socks;

        boolean known() {
            return body != 0;
        }
    }

    private static final String UA = "cleanyourtv-launcher/1.0 (personal TV launcher; github.com/Gathin23/cleanyourtv)";
    private static final long KEEP_MS = 7 * 86_400_000L;

    private Kits() { }

    /** The club's kits, home first (an empty array when its article cannot be read). */
    static Kit[] of(Context ctx, String club) {
        if (club == null || club.trim().isEmpty()) return new Kit[0];
        SharedPreferences cache = ctx.getSharedPreferences("kits", Context.MODE_PRIVATE);
        String kept = cache.getString(club, null);
        long at = cache.getLong(club + "@", 0);
        if (kept == null || System.currentTimeMillis() - at > KEEP_MS) {
            String read = read(club.trim());
            if (read != null) {
                kept = read;
                cache.edit().putString(club, read).putLong(club + "@", System.currentTimeMillis()).apply();
            }
        }
        if (kept == null || kept.isEmpty()) return new Kit[0];
        String[] kits = kept.split(";");
        Kit[] out = new Kit[kits.length];
        for (int i = 0; i < kits.length; i++) {
            String[] c = kits[i].split(",", -1);
            Kit k = new Kit();
            k.body = colour(c.length > 0 ? c[0] : "");
            k.sleeves = colour(c.length > 1 ? c[1] : "");
            k.shorts = colour(c.length > 2 ? c[2] : "");
            k.socks = colour(c.length > 3 ? c[3] : "");
            if (k.sleeves == 0) k.sleeves = k.body;
            out[i] = k;
        }
        return out;
    }

    /**
     * The kit the club wears against the other: its home kit, unless the away side's home shirt is too close to the
     * home side's, when the away side wears its second kit (or its third, when the second clashes too).
     */
    static Kit wearing(Kit[] mine, Kit[] theirs, boolean away) {
        if (mine.length == 0 || !mine[0].known()) return null;
        if (!away || theirs.length == 0 || !theirs[0].known()) return mine[0];
        int against = theirs[0].body;
        for (Kit k : mine) if (k.known() && com.gathin.broadcast.Worn.distance(k.body, against) >= 120) return k;
        return mine[0];
    }

    /**
     * The kit the club wears in this game: the one ESPN's line-ups name (named; this season's kit of that name from the
     * article, where its colour agrees, for its sleeves, shorts and socks; else ESPN's colour alone), else as wearing
     * has it. Null when neither is known.
     */
    static Kit worn(Kit[] mine, Kit[] theirs, boolean away, com.gathin.broadcast.Worn.Kit named) {
        if (named == null) return wearing(mine, theirs, away);
        int at = "home".equals(named.type) ? 0 : "away".equals(named.type) ? 1 : "third".equals(named.type) ? 2 : -1;
        if (at >= 0 && at < mine.length && mine[at].known() && com.gathin.broadcast.Worn.distance(mine[at].body, named.body) < 120) return mine[at];
        for (Kit k : mine) if (k.known() && com.gathin.broadcast.Worn.distance(k.body, named.body) < 60) return k;
        Kit k = new Kit();
        k.body = named.body;
        k.sleeves = named.body;
        return k;
    }

    /** "body,sleeves,shorts,socks;…" for each kit in the infobox, "" when it has none, null when it cannot be read. */
    private static String read(String club) {
        String[] titles = {club + " F.C.", club + " FC", "FC " + club, club + " CF", club + " A.F.C.",
                club + " national football team", club};
        for (String t : titles) {
            try {
                JSONObject j = new JSONObject(get("https://en.wikipedia.org/w/api.php?action=parse&format=json&redirects=1"
                        + "&prop=wikitext&section=0&page=" + URLEncoder.encode(t, "UTF-8")));
                JSONObject parse = j.optJSONObject("parse");
                if (parse == null) continue;
                String text = parse.getJSONObject("wikitext").optString("*", "");
                if (!text.contains("body1")) continue;
                StringBuilder out = new StringBuilder();
                for (int n = 1; n <= 3; n++) {
                    String body = field(text, "body" + n);
                    if (body.isEmpty()) continue;
                    if (out.length() > 0) out.append(';');
                    out.append(body).append(',').append(field(text, "leftarm" + n)).append(',')
                            .append(field(text, "shorts" + n)).append(',').append(field(text, "socks" + n));
                }
                return out.toString();
            } catch (java.io.FileNotFoundException missing) {
                // not this title
            } catch (Exception e) {
                return null;
            }
        }
        return "";
    }

    private static String field(String text, String name) {
        Matcher m = Pattern.compile("\\|\\s*" + name + "\\s*=\\s*#?([0-9A-Fa-f]{6})").matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private static int colour(String hex) {
        try { return hex.length() == 6 ? 0xFF000000 | Integer.parseInt(hex, 16) : 0; } catch (NumberFormatException e) { return 0; }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", UA);
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 2 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
