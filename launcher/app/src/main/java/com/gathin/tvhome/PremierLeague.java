package com.gathin.tvhome;

import android.content.Context;
import android.util.JsonReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Premier League's own photos of its players: this season's, in this season's kit, all framed alike, as on its
 * site and app. The Fantasy Premier League app's public list of players gives each one's code, which names his photo on
 * the league's picture server. The list is read once a day and kept as a small file. Off the main thread.
 */
final class PremierLeague {
    private static final String LIST = "https://fantasy.premierleague.com/api/bootstrap-static/";
    private static final String PHOTO = "https://resources.premierleague.com/premierleague25/photos/players/110x140/%s.png";
    private static final String UA = "cleanyourtv-launcher/1.0 (personal TV launcher; github.com/Gathin23/cleanyourtv)";
    private static final long KEEP_MS = 24 * 3_600_000L;
    /** The app's short club names, as the scores name the clubs. */
    private static final Map<String, String> CLUBS = new HashMap<>();
    static {
        CLUBS.put("Man Utd", "Manchester United");
        CLUBS.put("Man City", "Manchester City");
        CLUBS.put("Nott'm Forest", "Nottingham Forest");
        CLUBS.put("Spurs", "Tottenham Hotspur");
        CLUBS.put("Wolves", "Wolverhampton Wanderers");
    }

    /** Each player: {code, club, first names, surnames, the name the app shows}. */
    private static List<String[]> players;
    private static long readAt;

    private PremierLeague() { }

    /** His photo's address, when he is a Premier League player (at that club, when it is given); null otherwise. */
    static synchronized String photo(Context c, String fullName, String club) {
        if (fullName == null || fullName.trim().isEmpty()) return null;
        List<String[]> all = players(c);
        if (all == null || all.isEmpty()) return null;
        String[] want = plain(fullName).split(" ");
        String first = want[0], last = want[want.length - 1];
        boolean atClub = club != null && !club.isEmpty();
        List<String[]> sameSurname = new ArrayList<>(), named = new ArrayList<>();
        for (String[] p : all) {
            if (atClub && !PlayerPhotos.sameClub(p[1], club)) continue;
            String[] firsts = plain(p[2]).split(" "), seconds = plain(p[3]).split(" "), shown = plain(p[4]).split(" ");
            // The name the app shows is one of his names (Fatawu for "Fatawu Issahaku", Tanaka for "Ao Tanaka").
            if (java.util.Arrays.asList(want).contains(plain(p[4]))) named.add(p);
            // The surname: his last name, any of his surnames (Diogo Dalot Teixeira), or the name the app shows (Dalot).
            boolean surname = seconds[seconds.length - 1].equals(last) || shown[shown.length - 1].equals(last);
            for (String s : seconds) surname |= s.equals(last);
            if (!surname) continue;
            // The first name too, when both have one: Bruno for Bruno Borges, or the same initial.
            boolean firstName = want.length == 1 || firsts[0].isEmpty() || firsts[0].equals(first)
                    || firsts[0].charAt(0) == first.charAt(0) || plain(p[4]).equals(plain(fullName));
            if (firstName) return String.format(Locale.ROOT, PHOTO, p[0]);
            sameSurname.add(p);
        }
        // At his club, the only one by that surname ("Chema" Andrés is José María), or the only one by the name shown.
        if (atClub && sameSurname.size() == 1) return String.format(Locale.ROOT, PHOTO, sameSurname.get(0)[0]);
        if ((atClub || want.length == 1) && named.size() == 1) return String.format(Locale.ROOT, PHOTO, named.get(0)[0]);
        return null;
    }

    /** Last season's photo for the same player (square, on the league's older address); null for null. */
    static String older(String photo) {
        if (photo == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/(\\d+)\\.png$").matcher(photo);
        return m.find() ? "https://resources.premierleague.com/premierleague/photos/players/250x250/p" + m.group(1) + ".png" : null;
    }

    /** The list: kept in memory, else from its file when under a day old, else read again. */
    private static List<String[]> players(Context c) {
        long now = System.currentTimeMillis();
        if (players != null && now - readAt < KEEP_MS) return players;
        File file = new File(c.getCacheDir(), "premier_league_players.tsv");
        if (file.isFile() && now - file.lastModified() < KEEP_MS) {
            List<String[]> kept = load(file);
            if (!kept.isEmpty()) { players = kept; readAt = file.lastModified(); return players; }
        }
        List<String[]> read = read();
        if (read != null && !read.isEmpty()) {
            players = read;
            readAt = now;
            save(file, read);
        } else if (players == null && file.isFile()) {
            players = load(file);   // offline: an older list is better than none
            readAt = now - KEEP_MS + 30 * 60_000L;
        }
        return players;
    }

    /** The app's list, read as it streams in (it is large): the clubs, then each player. */
    private static List<String[]> read() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(LIST).openConnection();
            conn.setRequestProperty("User-Agent", UA);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            Map<String, String> clubs = new HashMap<>();
            List<String[]> raw = new ArrayList<>();
            try (InputStream in = conn.getInputStream();
                 JsonReader r = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                r.beginObject();
                while (r.hasNext()) {
                    String name = r.nextName();
                    if ("teams".equals(name)) {
                        r.beginArray();
                        while (r.hasNext()) {
                            String id = null, club = null;
                            r.beginObject();
                            while (r.hasNext()) {
                                String k = r.nextName();
                                if ("id".equals(k)) id = r.nextString();
                                else if ("name".equals(k)) club = r.nextString();
                                else r.skipValue();
                            }
                            r.endObject();
                            if (id != null && club != null) clubs.put(id, CLUBS.containsKey(club) ? CLUBS.get(club) : club);
                        }
                        r.endArray();
                    } else if ("elements".equals(name)) {
                        r.beginArray();
                        while (r.hasNext()) {
                            String[] p = new String[5];
                            r.beginObject();
                            while (r.hasNext()) {
                                String k = r.nextName();
                                switch (k) {
                                    case "code": p[0] = r.nextString(); break;
                                    case "team": p[1] = r.nextString(); break;
                                    case "first_name": p[2] = r.nextString(); break;
                                    case "second_name": p[3] = r.nextString(); break;
                                    case "web_name": p[4] = r.nextString(); break;
                                    default: r.skipValue();
                                }
                            }
                            r.endObject();
                            if (p[0] != null && p[3] != null) raw.add(p);
                        }
                        r.endArray();
                    } else {
                        r.skipValue();
                    }
                }
                r.endObject();
            }
            for (String[] p : raw) {
                p[1] = clubs.containsKey(p[1]) ? clubs.get(p[1]) : "";
                if (p[2] == null) p[2] = "";
                if (p[4] == null) p[4] = p[3];
            }
            return raw;
        } catch (Exception e) {
            android.util.Log.w("Photos", "Premier League list: " + e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void save(File file, List<String[]> list) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            for (String[] p : list) w.write(String.join("\t", p) + "\n");
        } catch (Exception ignored) {
            // kept in memory only
        }
    }

    private static List<String[]> load(File file) {
        List<String[]> out = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length == 5) out.add(p);
            }
        } catch (Exception ignored) {
            // read again instead
        }
        return out;
    }

    /** Lower case, no accents, letters and spaces only: "Groß" is "gross", "B.Fernandes" is "b fernandes". */
    private static String plain(String s) {
        String t = (s == null ? "" : s).replace("ß", "ss").replace("ø", "o").replace("Ø", "O").replace("æ", "ae")
                .replace("œ", "oe").replace("ł", "l").replace("đ", "d").replace("Đ", "D").replace("ı", "i");
        String n = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
    }
}
