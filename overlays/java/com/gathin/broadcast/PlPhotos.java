package com.gathin.broadcast;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Premier League's own cut-out pictures of its players (head to waist, in the kit) for the classic line-ups: its
 * fantasy game's player list gives each player's picture code, matched to FotMob's line-up by club and name. Players of
 * other clubs, or not matched, have none (the line-ups draw their face on a hologram body then).
 */
public final class PlPhotos {
    private PlPhotos() { }

    static final String BOOT = "https://fantasy.premierleague.com/api/bootstrap-static/";
    static final String[] PICTURES = {
            "https://resources.premierleague.com/premierleague25/photos/players/500x500/%d.png",
            "https://resources.premierleague.com/premierleague/photos/players/250x250/p%d.png"};
    /** How long the player list is kept (on disk and in memory) before it is fetched again. */
    static final long KEEP_MS = 12 * 3600_000L;

    private static JSONObject boot;
    private static long bootAt;

    /** One player in the list. */
    static final class Element {
        final int code, team;
        final String first, second, web;

        Element(int code, int team, String first, String second, String web) {
            this.code = code;
            this.team = team;
            this.first = first == null ? "" : first;
            this.second = second == null ? "" : second;
            this.web = web == null ? "" : web;
        }
    }

    /** The list, from memory, the disk (cacheDir/fpl-boot.json) while fresh, or the web; null when none can be had. */
    static synchronized JSONObject boot(File cacheDir) {
        long now = System.currentTimeMillis();
        if (boot != null && now - bootAt < KEEP_MS) return boot;
        File f = cacheDir == null ? null : new File(cacheDir, "fpl-boot.json");
        try {
            if (f != null && f.isFile() && now - f.lastModified() < KEEP_MS) {
                boot = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                bootAt = f.lastModified();
                return boot;
            }
        } catch (Exception ignored) { }
        try {
            String s = Net.text(BOOT);
            boot = new JSONObject(s);
            bootAt = now;
            if (f != null) Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            if (boot == null && f != null && f.isFile()) {   // an old list is better than none
                try {
                    boot = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                    bootAt = now - KEEP_MS + 600_000L;
                } catch (Exception ignored) { }
            }
        }
        return boot;
    }

    /** Pure: a name folded to plain lowercase letters and digits (accents, ø, æ, ß, ł, đ, ı folded to plain letters). */
    static String fold(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : plain(s).toCharArray()) if (Character.isLetterOrDigit(ch)) b.append(ch);
        return b.toString();
    }

    /** Pure: a name in plain lowercase letters, its spaces and marks kept ("Ødegaard-Núñez": "odegaard-nunez"). */
    static String plain(String s) {
        if (s == null) return "";
        String t = s.replace("ø", "o").replace("Ø", "O").replace("æ", "ae").replace("Æ", "AE").replace("ß", "ss")
                .replace("ł", "l").replace("Ł", "L").replace("đ", "d").replace("Đ", "D").replace("ı", "i");
        return Normalizer.normalize(t, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Clubs whose full names (FotMob's, ESPN's) are not the list's short ones. */
    private static final Map<String, String> CLUBS = new HashMap<>();
    static {
        CLUBS.put("manchesterunited", "manutd");
        CLUBS.put("manchestercity", "mancity");
        CLUBS.put("tottenhamhotspur", "spurs");
        CLUBS.put("tottenham", "spurs");
        CLUBS.put("nottinghamforest", "nottmforest");
        CLUBS.put("wolverhamptonwanderers", "wolves");
        CLUBS.put("wolverhampton", "wolves");
        CLUBS.put("brightonhovealbion", "brighton");
        CLUBS.put("westhamunited", "westham");
        CLUBS.put("sheffieldunited", "sheffieldutd");
    }

    /** Pure: the list's club id for a club's name ("Manchester United": "Man Utd"), or -1 when none matches. */
    static int teamId(JSONArray teams, String club) {
        String c = fold(club), alias = CLUBS.get(c);
        int partial = -1;
        for (int i = 0; teams != null && i < teams.length(); i++) {
            JSONObject t = teams.optJSONObject(i);
            if (t == null) continue;
            String n = fold(t.optString("name"));
            if (n.isEmpty()) continue;
            if (n.equals(c) || n.equals(alias)) return t.optInt("id", -1);
            if (partial < 0 && (c.startsWith(n) || c.endsWith(n) || n.startsWith(c))) partial = t.optInt("id", -1);
        }
        return partial;
    }

    /**
     * Pure: the picture code of the one player of the club (els, already that club's) who matches a line-up's name: the
     * surname or the name the game shows (3 points), or within the full name (2), one more for the first name's start;
     * -1 when none does or two match as well.
     */
    static int codeFor(List<Element> els, String name, String last) {
        String l = fold(last == null || last.isEmpty() ? name : last), full = fold(name == null || name.isEmpty() ? last : name);
        if (l.isEmpty()) return -1;
        int best = 0, second = 0, code = -1;
        for (Element e : els) {
            String sn = fold(e.second), fn = fold(e.first);
            String[] wparts = e.web.split("\\.");
            String wn = fold(wparts[wparts.length - 1]);
            int score = 0;
            if (l.equals(wn) || l.equals(sn)) score = 3;
            else if ((!sn.isEmpty() && (sn.contains(l) || full.contains(sn))) || (!wn.isEmpty() && l.contains(wn))) score = 2;
            if (score > 0 && fn.length() >= 1 && full.contains(fn.substring(0, Math.min(3, fn.length())))) score++;
            if (score > best) { second = best; best = score; code = e.code; }
            else if (score > second) second = score;
        }
        return best > 0 && best > second ? code : -1;
    }

    /** The club's players in the list (empty when the club is not in it). */
    static List<Element> club(JSONObject boot, String club) {
        List<Element> out = new ArrayList<>();
        if (boot == null) return out;
        int id = teamId(boot.optJSONArray("teams"), club);
        JSONArray els = boot.optJSONArray("elements");
        for (int i = 0; id > 0 && els != null && i < els.length(); i++) {
            JSONObject e = els.optJSONObject(i);
            if (e == null || e.optInt("team") != id) continue;
            out.add(new Element(e.optInt("code"), id, e.optString("first_name"), e.optString("second_name"), e.optString("web_name")));
        }
        return out;
    }

    /** The cut-out for a picture code (a real one: see-through round the player), or null. */
    static Bitmap picture(int code) {
        if (code <= 0) return null;
        for (String u : PICTURES) {
            try {
                byte[] b = Net.get(String.format(Locale.ROOT, u, code), 15_000, 4 << 20);
                Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length);
                if (bm != null && cutOut(bm)) return bm;
            } catch (Exception ignored) { }
        }
        return null;
    }

    /** Whether a picture is a cut-out: at least 200 pixels and a fair share see-through (not a square photo). */
    static boolean cutOut(Bitmap bm) {
        if (bm.getWidth() < 200 || bm.getHeight() < 200 || !bm.hasAlpha()) return false;
        int clear = 0, n = 0;
        for (int y = 0; y < bm.getHeight(); y += 8) {
            for (int x = 0; x < bm.getWidth(); x += 8) {
                n++;
                if ((bm.getPixel(x, y) >>> 24) < 16) clear++;
            }
        }
        return n > 0 && clear >= n * 0.15;
    }
}
