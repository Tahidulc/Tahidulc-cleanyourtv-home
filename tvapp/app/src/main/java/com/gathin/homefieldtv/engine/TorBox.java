package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TorBox as a source for films and episodes beside the playlists, as the phone has it: the family's own files already
 * in their TorBox account (its torrents, usenet and web downloads that have finished), matched to the film or episode
 * chosen by their names (the title and its year, a series' season and episode), each offered as a copy to play as a
 * playlist's is, TorBox's first unless Settings say otherwise. Only what is already there is used: nothing is searched
 * for or added to the account. The key stays on this TV (never shown again once saved, never in the log); an address
 * carries it only when the player asks TorBox for the file.
 */
public final class TorBox {
    private TorBox() {}

    /** The source's name in the pickers. */
    public static final String NAME = "TorBox";
    static final String SOURCE = "torbox";
    private static final String API = "https://api.torbox.app/v1/api/";
    private static final String PREFS = "torbox";
    /** The library read again after this long: a film just added to TorBox shows here within it (or at once, Check). */
    private static final long FRESH_MS = 2 * 60_000L;
    /** Every version of a film the account has is offered (TorBox's first, all of them); this only a guard. */
    /** After TorBox didn't answer, films play without it this long (then it is asked again; a retry first, at once). */
    private static final long FAILED_MS = 20_000L;
    /** A list read a page at a time, at most this many pages. */
    private static final int PAGE = 1000, PAGES = 5;
    private static final long MB = 1024L * 1024;

    // ---- the key and the switches ---------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean hasKey(Context c) {
        return !key(c).isEmpty();
    }

    private static String key(Context c) {
        return prefs(c).getString("key", "");
    }

    /** Saves the key (trimmed), TorBox then in use; the library read again with it. */
    public static void setKey(Context c, String key) {
        prefs(c).edit().putString("key", key == null ? "" : key.trim()).putBoolean("on", true).apply();
        forgetLibrary();
    }

    public static void forget(Context c) {
        prefs(c).edit().remove("key").apply();
        forgetLibrary();
    }

    /** In use: a key saved and the switch on. */
    public static boolean on(Context c) {
        return hasKey(c) && prefs(c).getBoolean("on", true);
    }

    /** The switch alone (on unless turned off), key or none. */
    public static boolean wanted(Context c) {
        return prefs(c).getBoolean("on", true);
    }

    public static void setOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("on", on).apply();
    }

    /** TorBox's copies offered before the playlists' (on unless turned off). */
    public static boolean first(Context c) {
        return prefs(c).getBoolean("first", true);
    }

    public static void setFirst(Context c, boolean first) {
        prefs(c).edit().putBoolean("first", first).apply();
    }

    // ---- the account and its files --------------------------------------------------------------------------------

    /** One file in the account: its kind (torrent, usenet, webdl), its download's id and its own, its names and size. */
    static final class File {
        /** Its kind; its own name ("Show.S01E02.mkv"); its path in the download (folders too); the download's name. */
        final String kind, name, path, item;
        final int itemId, id;
        final long size;
        /** The only film-sized picture in its download (a sample or an extra aside). */
        boolean single;
        /** When its download was added to the account (ms), 0 when not known. */
        long added;

        File(String kind, int itemId, int id, String name, String path, String item, long size) {
            this.kind = kind;
            this.itemId = itemId;
            this.id = id;
            this.name = name == null ? "" : name;
            this.path = path == null ? "" : path;
            this.item = item == null ? "" : item;
            this.size = size;
        }

        /** How it is kept while it plays: "torbox:torrent:12:3", never with the key. */
        String stored() {
            return SOURCE + ":" + kind + ":" + itemId + ":" + id;
        }
    }

    private static volatile List<File> library;
    private static volatile long libraryAt, failedAt;
    private static volatile String libraryFor = "", failedFor = "";
    /** One read of the library at a time (forgetting it never waits on one, so Settings' main thread never does). */
    private static final Object READING = new Object();
    private static final AtomicBoolean WARMING = new AtomicBoolean();

    static void forgetLibrary() {
        library = null;
        libraryAt = 0;
        libraryFor = "";
        failedAt = 0;
        failedFor = "";
    }

    /**
     * The account's finished files, read again when older than FRESH_MS (or when fresh is asked): the torrents', the
     * usenet downloads' and the web downloads'. Off the main thread. Throws when the key is refused or TorBox can't be
     * reached (and, but for a fresh read, again without asking for a while after); usenet or web downloads the plan
     * hasn't are left out quietly.
     */
    static List<File> library(Context c, boolean fresh) throws IOException {
        synchronized (READING) {
            return libraryLocked(c, fresh);
        }
    }

    private static List<File> libraryLocked(Context c, boolean fresh) throws IOException {
        String k = key(c);
        if (k.isEmpty()) return new ArrayList<>();
        long now = System.currentTimeMillis();
        List<File> have = library;
        if (!fresh && have != null && k.equals(libraryFor) && now - libraryAt < FRESH_MS) return have;
        // Not answering a moment ago: films don't wait on it again until the while is up.
        if (!fresh && k.equals(failedFor) && now - failedAt < FAILED_MS) throw new IOException("TorBox wasn't answering");
        List<File> out = new ArrayList<>();
        try {
            read(out, k, "torrents", "torrent");
        } catch (IOException first) {
            // A moment's retry before the film goes without TorBox: its files are the ones offered first.
            out.clear();
            try {
                Thread.sleep(1200);
                read(out, k, "torrents", "torrent");
            } catch (IOException | InterruptedException e) {
                failedAt = System.currentTimeMillis();
                failedFor = k;
                throw first;
            }
        }
        try {
            read(out, k, "usenet", "usenet");
        } catch (IOException ignored) {
            // not in the plan, or not answering: the torrents still count
        }
        try {
            read(out, k, "webdl", "webdl");
        } catch (IOException ignored) {
            // the same
        }
        List<File> lib = Collections.unmodifiableList(out);
        library = lib;
        libraryAt = now;
        libraryFor = k;
        return lib;
    }

    /** Whether the library read last is fresh enough to use as it is (a film then starts without waiting on TorBox). */
    public static boolean fresh(Context c) {
        List<File> have = library;
        return have != null && key(c).equals(libraryFor) && System.currentTimeMillis() - libraryAt < FRESH_MS;
    }

    /** The library read in the background (a film's page opened), so that Play finds TorBox's copies at once. */
    public static void warm(Context c) {
        if (!on(c) || fresh(c) || !WARMING.compareAndSet(false, true)) return;
        Context app = c.getApplicationContext();
        Exec.io(() -> {
            try {
                library(app, false);
            } catch (IOException | RuntimeException ignored) {
                // none this time: Play asks again
            } finally {
                WARMING.set(false);
            }
        });
    }

    private static void read(List<File> out, String key, String path, String kind) throws IOException {
        for (int page = 0; page < PAGES; page++) {
            JSONObject root = get(key, path + "/mylist?bypass_cache=true&offset=" + page * PAGE + "&limit=" + PAGE);
            JSONArray items = root.optJSONArray("data");
            files(out, items, kind);
            if (items == null || items.length() < PAGE) return;
        }
    }

    /** Pure: the finished files of one list's items ({id, name, download_finished, files: [{id, name, size}]}). */
    static void files(List<File> out, JSONArray items, String kind) {
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null || !ready(it)) continue;
            JSONArray fs = it.optJSONArray("files");
            List<File> mine = new ArrayList<>();
            long at = time(it.optString("created_at", ""));
            for (int j = 0; fs != null && j < fs.length(); j++) {
                JSONObject f = fs.optJSONObject(j);
                if (f == null) continue;
                String path = f.optString("name", "");
                String name = f.optString("short_name", "");
                if (name.isEmpty()) name = last(path);
                mine.add(new File(kind, it.optInt("id"), f.optInt("id"), name, path, it.optString("name", ""), f.optLong("size")));
            }
            for (File f : mine) f.added = at;
            int pictures = 0;
            for (File f : mine) if (video(f.name, f.size)) pictures++;
            for (File f : mine) f.single = pictures == 1 && video(f.name, f.size);
            out.addAll(mine);
        }
    }

    /** Pure: a time as TorBox gives it ("2024-05-01T12:34:56Z", with or without a zone), in ms; 0 when it can't be read. */
    static long time(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            try {
                return java.time.LocalDateTime.parse(s).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
            } catch (RuntimeException e2) {
                return 0;
            }
        }
    }

    /** Pure: an item ready to play (finished, or present on TorBox's servers); one saying neither is taken as ready. */
    static boolean ready(JSONObject it) {
        if (!it.has("download_finished") && !it.has("download_present")) return true;
        return it.optBoolean("download_finished", false) || it.optBoolean("download_present", false);
    }

    /** Checks the key with TorBox (the library read afresh): how many pictures it holds ready, or throws with why. */
    public static int check(Context c) throws IOException {
        int n = 0;
        for (File f : library(c, true)) if (video(f.name, f.size)) n++;
        return n;
    }

    /** Pure: what Settings says after a check ("Connected · 12 films and episodes ready"). */
    public static String checked(int n) {
        return "Connected  ·  " + n + (n == 1 ? " film or episode ready" : " films and episodes ready");
    }

    /** The key refused (or the account closed): said as such, not as a network fault. */
    static final class Refused extends IOException {
        Refused() {
            super("TorBox didn't accept that key");
        }
    }

    /** A TorBox reply ({success, data, detail}), the key in its header; errors never repeat the address or the key. */
    private static JSONObject get(String key, String path) throws IOException {
        HttpURLConnection h;
        try {
            h = (HttpURLConnection) new URL(API + path).openConnection();
        } catch (IllegalArgumentException e) {
            throw new IOException("Couldn't ask TorBox");
        }
        h.setConnectTimeout(10_000);
        h.setReadTimeout(20_000);
        h.setRequestProperty("Authorization", "Bearer " + key);
        h.setRequestProperty("User-Agent", Fetch.AGENT);
        h.setRequestProperty("Accept", "application/json");
        try {
            int code;
            try {
                code = h.getResponseCode();
            } catch (java.net.UnknownHostException e) {
                throw new IOException("Couldn't reach TorBox");
            } catch (java.net.SocketTimeoutException e) {
                throw new IOException("TorBox didn't answer in time");
            } catch (IOException e) {
                throw new IOException("Couldn't reach TorBox");
            }
            if (code == 401 || code == 403) throw new Refused();
            if (code == 429) throw new IOException("TorBox asked to wait a moment; try again shortly");
            if (code >= 400) throw new IOException("TorBox couldn't answer just now (" + code + ")");
            String body;
            try (InputStream in = h.getInputStream()) {
                body = Bounded.text(in, h.getContentLengthLong(), 48 << 20);
            } catch (IOException e) {
                throw new IOException("TorBox's answer was cut short");
            }
            JSONObject root = new JSONObject(body);
            if (!root.optBoolean("success", true)) {
                if (refusal(root.optString("error", ""))) throw new Refused();
                throw new IOException("TorBox said no just now");
            }
            return root;
        } catch (JSONException e) {
            throw new IOException("TorBox's answer couldn't be read");
        } finally {
            h.disconnect();
        }
    }

    /** Pure: an error TorBox gives for a key it won't take ("BAD_TOKEN", "NO_AUTH", "AUTH_ERROR"). */
    static boolean refusal(String error) {
        String e = error == null ? "" : error.toUpperCase(Locale.ROOT);
        return e.contains("AUTH") || e.contains("TOKEN");
    }


    // ---- names ----------------------------------------------------------------------------------------------------

    /** What a file's (or a download's) name says: the title, its year, a season and episode (0 and -1 where none). */
    static final class Release {
        String title = "";
        int year = 0, season = -1, episode = -1;

        boolean episodic() {
            return season >= 0 || episode >= 0;
        }
    }

    private static final Pattern SXE = Pattern.compile("(?i)\\bS(\\d{1,2})\\s?E(\\d{1,3})(?!\\d)");
    private static final Pattern NXN = Pattern.compile("(?i)\\b(\\d{1,2})x(\\d{2,3})\\b");
    private static final Pattern SEASON = Pattern.compile("(?i)\\b(?:S(\\d{1,2})|Season\\s?(\\d{1,2}))\\b");
    private static final Pattern EPISODE = Pattern.compile("(?i)\\b(?:E|EP|Episode)\\s?(\\d{1,3})\\b");
    /** A fansub's numbering: "[Group] Show - 05 (1080p)". */
    private static final Pattern DASHED = Pattern.compile("\\s-\\s(\\d{1,3})(?=\\s|$)");
    private static final Pattern YEAR = Pattern.compile("(?:^|[\\s(\\[\\-])((?:19|20)\\d{2})(?=$|[\\s)\\]\\-])");
    /** Where a release name's title surely ends: the picture, the copy, the codec, the sound, the service. */
    private static final Pattern TAG = Pattern.compile("(?i)(?:^|[\\s\\[(])(?:2160p|1080p|1080i|720p|576p|480p|4k|uhd|bluray|blu-ray|"
            + "brrip|bdrip|bdremux|remux|webrip|web-dl|webdl|hdtv|hdrip|dvdrip|x264|x265|h264|h265|hevc|avc|hdr10|hdr|10bit|ddp5|ddp|"
            + "dd5|aac|ac3|eac3|truehd|atmos|amzn|dsnp|atvp|hmax)(?=$|[\\s\\-\\[\\]()])");
    /** Words a release adds after its title ("Extended", "Complete Series"), not the title's own. */
    private static final Pattern TAIL = Pattern.compile("(?i)\\s+(?:extended|unrated|uncut|remastered|theatrical|proper|repack|"
            + "internal|imax|limited|criterion|multi|subbed|dubbed|(?:the\\s+)?complete(?:\\s+series)?)$");
    /** A country after a series' name ("The Office US"). */
    private static final Pattern COUNTRY = Pattern.compile("\\s+(?:US|UK|AU|NZ|CA)$");
    private static final Pattern GROUP = Pattern.compile("^\\[[^\\]]{1,40}\\]\\s*");
    private static final Pattern SITE = Pattern.compile("(?i)^www\\s\\S+(?:\\s(?:com|org|net|to|io|cc|ws|me|tv|co|uk|nl|mx|in))+\\s*-?\\s*");
    private static final Pattern VIDEO = Pattern.compile("(?i).*\\.(mkv|mp4|m4v|avi|mov|webm|ts|wmv|mpg|mpeg)$");

    private static String last(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /**
     * Pure: a release name read ("Glass.Harbour.2024.1080p.WEB-DL.mkv", "Show.S02E05.720p.mkv", "Show Season 2",
     * "[Group] Show - 05 (1080p).mkv"): the title up to its year, its season and episode, or the first tag.
     */
    static Release parse(String raw) {
        Release r = new Release();
        if (raw == null) return r;
        String n = last(raw).replaceAll("(?i)\\.(mkv|mp4|m4v|avi|mov|webm|ts|wmv|mpg|mpeg|srt)$", "");
        String s = n.replaceAll("[._]+", " ").replaceAll("\\s+", " ").trim();
        boolean group = false;
        Matcher g = GROUP.matcher(s);
        if (g.lookingAt()) {
            s = s.substring(g.end());
            group = true;
        }
        s = SITE.matcher(s).replaceFirst("");
        int cut = s.length();
        Matcher m = SXE.matcher(s);
        if (m.find()) {
            r.season = Integer.parseInt(m.group(1));
            r.episode = Integer.parseInt(m.group(2));
            cut = m.start();
        } else if ((m = NXN.matcher(s)).find() && Integer.parseInt(m.group(1)) <= 30) {
            r.season = Integer.parseInt(m.group(1));
            r.episode = Integer.parseInt(m.group(2));
            cut = m.start();
        } else if ((m = SEASON.matcher(s)).find()) {
            r.season = Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2));
            cut = m.start();
            Matcher e = EPISODE.matcher(s.substring(m.end()));
            if (e.find()) r.episode = Integer.parseInt(e.group(1));
        } else if ((m = EPISODE.matcher(s)).find()) {
            r.episode = Integer.parseInt(m.group(1));
            cut = m.start();
        } else if (group && (m = DASHED.matcher(s)).find()) {
            r.episode = Integer.parseInt(m.group(1));
            cut = m.start();
        }
        Matcher tag = TAG.matcher(s);
        if (tag.find() && tag.start() < cut) cut = tag.start();
        // The last year before the rest is the release's ("Blade Runner 2049 2017"); one opening the name, or one still to
        // come, is its title's ("1917", "2001 A Space Odyssey 1968", "Blade Runner 2049").
        Matcher y = YEAR.matcher(s);
        int yearAt = -1, latest = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1;
        while (y.find()) {
            int at = y.start(1);
            if (at >= cut) break;
            if (at > 0 && Integer.parseInt(y.group(1)) <= latest) {
                r.year = Integer.parseInt(y.group(1));
                yearAt = at;
            }
        }
        if (yearAt > 0) cut = yearAt;
        String title = s.substring(0, Math.min(cut, s.length())).replaceAll("[\\s\\-\\[(]+$", "").trim();
        for (Matcher t = TAIL.matcher(title); t.find() && t.start() > 0; t = TAIL.matcher(title)) title = title.substring(0, t.start());
        if (r.episodic()) title = COUNTRY.matcher(title).replaceFirst("");
        r.title = title.replaceAll("[\\s\\-]+$", "").trim();
        return r;
    }

    /** Pure: a playable picture file, not a sample, a trailer or a few seconds' extra. */
    static boolean video(String name, long size) {
        if (name == null || !VIDEO.matcher(name).matches()) return false;
        String n = " " + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ") + " ";
        boolean sample = n.contains(" sample ") || n.contains(" trailer ");
        if (size <= 0) return !sample;
        return size >= 15 * MB && !(sample && size < 300 * MB);
    }

    /** Pure: the picture a file's name says, else its download's (Catalog's PICTURE_4K … PICTURE_SD, 0 for none). */
    static int picture(File f) {
        int p = Catalog.pictureOf(f.name);
        return p > 0 ? p : Catalog.pictureOf(f.item);
    }

    /**
     * Pure: the order versions are offered in, the best first, as the phone's, with no limit on size (Formats.compare):
     * 4K, then 1080p, 720p and the rest; within the same picture HDR (Dolby Vision or any other) before SDR, Dolby Atmos
     * or surround before stereo, then the bigger file; AVI and the like after them all.
     */
    static int compare(File a, File b) {
        return compare(said(a), said(b));
    }

    /** What a file's name (and its download's) says of it: its kind, picture, HDR, sound and size. */
    static final class Said {
        final String kind, hdr, sound;
        final int picture;
        final long size;

        Said(File f) {
            String words = f.name + " " + f.item;
            kind = Catalog.kindOf(f.name);
            picture = picture(f);
            hdr = Formats.hdr(words);
            sound = Formats.audio(words);
            size = f.size;
        }
    }

    static Said said(File f) {
        return new Said(f);
    }

    private static int compare(Said a, Said b) {
        return Formats.compare(a.kind, a.picture, a.hdr, a.sound, a.size, b.kind, b.picture, b.hdr, b.sound, b.size);
    }

    /** Files put in that order, each one's names read once. */
    static void sortBest(List<File> files) {
        Map<File, Said> by = new java.util.IdentityHashMap<>();
        for (File f : files) by.put(f, said(f));
        Collections.sort(files, (a, b) -> compare(by.get(a), by.get(b)));
    }

    /** Pure: two titles alike as the playlists' are (Catalog.key), and with their spaces gone ("Ocean's" and "Oceans"). */
    static boolean sameTitle(String title, String want, String alt) {
        String k = squash(title);
        if (k.isEmpty()) return false;
        return k.equals(squash(want)) || (alt != null && !alt.isEmpty() && k.equals(squash(alt)));
    }

    /** A title as compared (squash), for the answers kept about it. */
    static String squashed(String title) {
        return squash(title);
    }

    /** A title as Catalog.key makes it, spaces gone; one that is only a year ("1917", "2012") as it is. */
    private static String squash(String title) {
        String t = title == null ? "" : title.replace("'", "").replace("’", "");
        String k = Catalog.key(t).replace(" ", "");
        return k.isEmpty() ? t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "") : k;
    }

    private static boolean fits(Release r, String title, String alt, int year) {
        return sameTitle(r.title, title, alt) && (year == 0 || r.year == 0 || Math.abs(r.year - year) <= 1);
    }

    /**
     * Pure: a film's files in the library: by the file's own name (the same title or its original one, the year a year
     * apart at most), else, for the only picture in its download, by the download's name; the surest first.
     */
    static List<File> films(List<File> lib, String title, String alt, String year) {
        int want = 0;
        try {
            want = year == null || year.length() < 4 ? 0 : Integer.parseInt(year.substring(0, 4));
        } catch (NumberFormatException ignored) {
            // no year to go by
        }
        List<File> out = new ArrayList<>();
        for (File f : lib) {
            if (!video(f.name, f.size)) continue;
            Release own = parse(f.name);
            if (own.episodic()) continue;
            Release item = parse(f.item);
            if (own.year == 0 && !item.episodic() && sameTitle(item.title, own.title, null)) own.year = item.year;
            boolean match = fits(own, title, alt, want)
                    || (f.single && !item.episodic() && item.year > 0 && fits(item, title, alt, want));
            if (match) out.add(f);
        }
        // Every version, none left out (the family's: "make sure you include all of them").
        sortBest(out);
        return out;
    }

    /**
     * Pure: an episode file's season and number (the season from its folders or its download's name when its own name
     * hasn't one; the first season when none says), or null for a file that isn't an episode.
     */
    static Release episode(File f) {
        Release own = parse(f.name);
        if (own.episode < 0) return null;
        if (own.season < 0) {
            String[] folders = f.path.split("[/\\\\]");
            for (int i = folders.length - 2; i >= 0 && own.season < 0; i--) own.season = parse(folders[i]).season;
            if (own.season < 0) own.season = parse(f.item).season;
            if (own.season < 0) own.season = 1;
        }
        return own;
    }

    /** Pure: a series' episode files in the library, by its name (or its original one): its own, its folders' or its download's. */
    static List<File> episodeFiles(List<File> lib, String show, String alt) {
        List<File> out = new ArrayList<>();
        for (File f : lib) {
            if (!video(f.name, f.size)) continue;
            Release r = episode(f);
            if (r == null) continue;
            boolean named = sameTitle(r.title, show, alt) || sameTitle(parse(f.item).title, show, alt);
            if (!named) {
                String[] folders = f.path.split("[/\\\\]");
                for (int i = folders.length - 2; i >= 0 && !named; i--) named = sameTitle(parse(folders[i]).title, show, alt);
            }
            if (named) out.add(f);
        }
        sortBest(out);
        return out;
    }

    /** Pure: one episode's versions in the library (its season and number), the surest first. */
    static List<File> episodeVersions(List<File> lib, String show, String alt, int season, int number) {
        List<File> out = new ArrayList<>();
        for (File f : episodeFiles(lib, show, alt)) {
            Release r = episode(f);
            if (r != null && r.season == season && r.episode == number) out.add(f);
        }
        return out;
    }

    // ---- the account's own titles: those no playlist has -------------------------------------------------------------

    /** One title in the account: a film (by its name and year) or a series (by its name), with all its files. */
    static final class Own {
        final boolean series;
        final String name;
        /** A film's year; a series' where its download's name says one (else 0). */
        final int year;
        final List<File> files = new ArrayList<>();

        Own(boolean series, String name, int year) {
            this.series = series;
            this.name = name;
            this.year = year;
        }

        /** Its id among the catalogue's titles: "glassharbour|2024" for a film, "paperlanterns" for a series. */
        String id() {
            return squash(name) + (series ? "" : "|" + year);
        }

        /** When the newest of its downloads came into the account (ms), 0 when not known. */
        long added() {
            long at = 0;
            for (File f : files) at = Math.max(at, f.added);
            return at;
        }

        /** The best picture its files' names say (Catalog's PICTURE_*, 0 for none). */
        int picture() {
            int p = 0;
            for (File f : files) p = Math.max(p, TorBox.picture(f));
            return p;
        }

        /** Whether one of its files' (or downloads') names says Dolby Vision, or Dolby Atmos. */
        boolean dolbyVision() {
            for (File f : files) if (Catalog.dolbyVisionOf(f.name) || Catalog.dolbyVisionOf(f.item)) return true;
            return false;
        }

        boolean atmos() {
            for (File f : files) if (Catalog.atmosOf(f.name) || Catalog.atmosOf(f.item)) return true;
            return false;
        }
    }

    /**
     * Pure: the account's films and series, each once: a film by its name and the year its names say (its own name's,
     * else, for the only picture in its download, the download's), a series by the name of its episodes (their own,
     * their folders' or their download's). A film whose names say no year is left out (it can't be told from another).
     */
    static List<Own> titles(List<File> lib) {
        Map<String, Own> by = new LinkedHashMap<>();
        for (File f : lib) {
            if (!video(f.name, f.size)) continue;
            Release ep = episode(f);
            Own o;
            if (ep != null) {
                String show = showOf(f, ep);
                if (!named(show)) continue;
                String k = "s|" + squash(show);
                o = by.get(k);
                if (o == null) by.put(k, o = new Own(true, show, parse(f.item).year));
            } else {
                Release own = parse(f.name), item = parse(f.item);
                if (own.year == 0 && !item.episodic() && sameTitle(item.title, own.title, null)) own.year = item.year;
                Release use = own.year > 0 && named(own.title) ? own
                        : f.single && !item.episodic() && item.year > 0 && named(item.title) ? item : null;
                if (use == null) continue;
                String k = "m|" + squash(use.title) + "|" + use.year;
                o = by.get(k);
                if (o == null) by.put(k, o = new Own(false, use.title, use.year));
            }
            o.files.add(f);
        }
        return new ArrayList<>(by.values());
    }

    /** An episode file's series: its own name's title, else its folders' (the nearest first), else its download's. */
    private static String showOf(File f, Release ep) {
        if (named(ep.title)) return ep.title;
        String[] folders = f.path.split("[/\\\\]");
        for (int i = folders.length - 2; i >= 0; i--) {
            String t = parse(folders[i]).title;
            if (named(t)) return t;
        }
        String t = parse(f.item).title;
        return named(t) ? t : "";
    }

    /** Pure: a name a title could have: a few letters (or a number such as "1917"), not a code such as "a1b2c3". */
    static boolean named(String t) {
        String s = t == null ? "" : t.trim();
        if (s.matches("\\d{1,4}")) return true;
        int letters = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) letters++;
        if (letters < 2) return false;
        return s.indexOf(' ') >= 0 || s.length() < 6 || !s.matches(".*\\d.*");
    }

    /**
     * Pure: those of the account's titles that no playlist has: a film that none has by its name with a year at most one
     * apart (or none said), a series that none has by its name.
     */
    static List<Own> onlyOwn(List<Own> own, List<Catalog.Title> films, List<Catalog.Title> series) {
        Map<String, List<Integer>> years = new HashMap<>();
        for (Catalog.Title t : films) {
            String k = squash(t.name);
            List<Integer> l = years.get(k);
            if (l == null) years.put(k, l = new ArrayList<>(1));
            l.add(yearOf(t.year));
        }
        java.util.Set<String> shows = new java.util.HashSet<>();
        for (Catalog.Title t : series) shows.add(squash(t.name));
        List<Own> out = new ArrayList<>();
        for (Own o : own) {
            if (o.series) {
                if (!shows.contains(squash(o.name))) out.add(o);
                continue;
            }
            boolean had = false;
            List<Integer> l = years.get(squash(o.name));
            for (int i = 0; l != null && i < l.size() && !had; i++) had = l.get(i) == 0 || Math.abs(l.get(i) - o.year) <= 1;
            if (!had) out.add(o);
        }
        return out;
    }

    static int yearOf(String y) {
        try {
            return y == null || y.length() < 4 ? 0 : Integer.parseInt(y.substring(0, 4));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Pure: an own title as the catalogue's, listed under Catalog.TORBOX: its name and year, what TMDB says of it where it
     * matched one (its name as TMDB writes it, its poster, backdrop, story and rating), and the best picture and sound its
     * files' names say (for the poster's chips).
     */
    static Catalog.Title title(Own o, Tmdb.Match m) {
        boolean found = m != null && m.id > 0;
        String name = found && !m.name.isEmpty() ? m.name : tidy(o.name);
        // The picture and sound go after the name as a playlist's names have them, for the poster's chips; the name
        // shown loses them again (Catalog.displayName).
        StringBuilder raw = new StringBuilder(name);
        String q = Catalog.pictureName(o.picture());
        if (!q.isEmpty()) raw.append(' ').append(q);
        if (o.dolbyVision()) raw.append(" DV");
        if (o.atmos()) raw.append(" Atmos");
        String year = found && !m.year.isEmpty() ? m.year : o.year > 0 ? String.valueOf(o.year) : "";
        return new Catalog.Title(SOURCE, o.series, o.id(), raw.toString(), Catalog.TORBOX, found ? m.poster : "",
                found ? m.backdrop : "", found ? m.rating : "", found ? m.plot : "", o.added(), "", "", null, null, year);
    }

    /** Pure: a name from a file as a title is written: one all in small letters given capitals ("the long tide"). */
    static String tidy(String name) {
        if (name == null) return "";
        if (!name.equals(name.toLowerCase(Locale.ROOT))) return name;
        StringBuilder b = new StringBuilder(name.length());
        boolean start = true;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            b.append(start ? Character.toUpperCase(ch) : ch);
            start = ch == ' ' || ch == '-' || ch == '(';
        }
        return b.toString();
    }

    /**
     * The account's films and series that none of the playlists has (films and series: the playlists'), as catalogue
     * titles, newest first, with what TMDB said of each before; asked of TMDB for those it hasn't been where the launcher
     * gives its key (lookUp). Empty with TorBox not in use; null when TorBox couldn't be read (the titles shown stay).
     * Off the main thread.
     */
    static List<List<Catalog.Title>> ownTitles(Context c, List<Catalog.Title> films, List<Catalog.Title> series, boolean lookUp) {
        List<Catalog.Title> f = new ArrayList<>(), s = new ArrayList<>();
        List<List<Catalog.Title>> out = new ArrayList<>();
        out.add(f);
        out.add(s);
        if (!on(c)) return out;
        List<File> lib;
        try {
            lib = library(c, false);
        } catch (IOException e) {
            return null;
        }
        List<Own> own = onlyOwn(titles(lib), films, series);
        own.sort((a, b) -> {
            long x = a.added(), y = b.added();
            return x != y ? Long.compare(y, x) : a.name.compareToIgnoreCase(b.name);
        });
        Map<String, Own> files = new HashMap<>();
        Map<String, Tmdb.Match> said = new HashMap<>();
        for (Own o : own) {
            Tmdb.Match m = lookUp ? Tmdb.find(c, o.series, o.name, o.year) : Tmdb.cached(c, o.series, o.name, o.year);
            Catalog.Title t = title(o, m);
            files.put(t.key, o);
            if (m != null && m.id > 0) said.put(t.key, m);
            (o.series ? s : f).add(t);
        }
        owns = files;
        matched = said;
        return out;
    }

    /** Whether some of these own titles haven't been asked of TMDB yet and could be now (the launcher gives its key). */
    static boolean unasked(Context c, List<Catalog.Title> titles) {
        if (titles.isEmpty() || !Tmdb.ready(c)) return false;
        Map<String, Own> by = owns;
        for (Catalog.Title t : titles) {
            Own o = by.get(t.key);
            if (o != null && !Tmdb.asked(c, o.series, o.name, o.year)) return true;
        }
        return false;
    }

    /** The own titles read last, by their keys: their files, and what TMDB said of them. */
    private static volatile Map<String, Own> owns = new HashMap<>();
    private static volatile Map<String, Tmdb.Match> matched = new HashMap<>();

    /**
     * An own title's page: what TMDB says of it (its story, genres, cast and trailer) where it matched one, and a
     * series' seasons and episodes from its files, each named as TMDB's season names it. Off the main thread.
     */
    static Catalog.Details details(Context c, Catalog.Title t) {
        Tmdb.Match m = matched.get(t.key);
        boolean found = m != null && m.id > 0;
        List<Catalog.Season> seasons = null;
        if (t.series) {
            Own o = owns.get(t.key);
            List<Catalog.Episode> eps;
            if (o != null) {
                eps = ownEpisodes(o.files, o.name);
            } else {
                try {
                    eps = ownEpisodes(library(c, false), t.name);
                } catch (IOException e) {
                    eps = new ArrayList<>();
                }
            }
            if (found) {
                List<Catalog.Episode> named = new ArrayList<>(eps.size());
                java.util.TreeMap<Integer, List<Catalog.Episode>> by = new java.util.TreeMap<>();
                for (Catalog.Episode e : eps) {
                    List<Catalog.Episode> l = by.get(e.season);
                    if (l == null) by.put(e.season, l = new ArrayList<>());
                    l.add(e);
                }
                int asked = 0;
                for (Map.Entry<Integer, List<Catalog.Episode>> e : by.entrySet()) {
                    // A dozen seasons' names at most; the rest as their files say.
                    named.addAll(asked++ < 12 ? Tmdb.named(e.getValue(), Tmdb.season(c, m.id, e.getKey())) : e.getValue());
                }
                eps = named;
            }
            seasons = Catalog.seasonsOf(eps);
        }
        org.json.JSONObject j = found ? Tmdb.details(c, t.series, m.id) : null;
        return j != null ? Tmdb.detailsOf(j, t, seasons) : Catalog.plainDetails(t, seasons);
    }

    /** Pure: a TorBox series' episodes as its page lists them: each (season, number) once, from its files, in order. */
    static List<Catalog.Episode> ownEpisodes(List<File> lib, String show) {
        Map<Integer, Catalog.Episode> at = new java.util.TreeMap<>();
        for (File f : episodeFiles(lib, show, null)) {
            Release r = episode(f);
            if (r == null || r.season < 0 || r.episode < 0) continue;
            int k = r.season * 10000 + r.episode;
            if (at.containsKey(k)) continue;
            Catalog.Episode e = new Catalog.Episode(episodeId(r.season, r.episode), r.season, r.episode, "", "", "", 0, "", "", "", "");
            e.torbox = true;
            at.put(k, e);
        }
        return new ArrayList<>(at.values());
    }

    /** An episode only TorBox has: its id ("tb:2:5"), the same on every page and in Continue watching. */
    static String episodeId(int season, int number) {
        return "tb:" + season + ":" + number;
    }

    /**
     * Pure: a playlist series' episodes with those only TorBox has (by season and number) put in among them, marked as
     * TorBox's; the playlist's own as they were.
     */
    static List<Catalog.Episode> withOwn(List<Catalog.Episode> playlist, List<Catalog.Episode> torbox) {
        java.util.Set<Integer> have = new java.util.HashSet<>();
        for (Catalog.Episode e : playlist) have.add(e.season * 10000 + e.number);
        List<Catalog.Episode> out = new ArrayList<>(playlist);
        for (Catalog.Episode e : torbox) if (have.add(e.season * 10000 + e.number)) out.add(e);
        out.sort((a, b) -> a.season != b.season ? Integer.compare(a.season, b.season) : Integer.compare(a.number, b.number));
        return out;
    }

    /**
     * A series' page's details with its episodes that only TorBox has put in among the playlist's (or, for a TorBox
     * series, all of them from its files); as they were with TorBox not in use or not answering. Off the main thread.
     */
    static Catalog.Details withOwnEpisodes(Context c, Catalog.Title t, Catalog.Details d) {
        if (t == null || !t.series || !on(c)) return d;
        List<File> lib;
        try {
            lib = library(c, false);
        } catch (IOException e) {
            return d;
        }
        List<Catalog.Episode> own = ownEpisodes(lib, t.name);
        if (own.isEmpty()) return d;
        List<Catalog.Episode> had = d == null ? new ArrayList<>() : d.episodes();
        List<Catalog.Episode> all = withOwn(had, own);
        if (all.size() == had.size()) return d;
        return Catalog.withEpisodes(d, t, namedOwn(c, t, all));
    }

    /**
     * A playlist series' episodes with TorBox's own among them named as TMDB's seasons name them (the series found by its
     * name, where the launcher gives its key); the playlist's, and all of them without TMDB, as they were.
     */
    private static List<Catalog.Episode> namedOwn(Context c, Catalog.Title t, List<Catalog.Episode> all) {
        if (!Tmdb.ready(c)) return all;
        Tmdb.Match m = Tmdb.find(c, true, t.name, yearOf(t.year));
        if (m == null || m.id <= 0) return all;
        java.util.TreeMap<Integer, List<Catalog.Episode>> bySeason = new java.util.TreeMap<>();
        for (Catalog.Episode e : all) {
            if (!e.torbox) continue;
            List<Catalog.Episode> l = bySeason.get(e.season);
            if (l == null) bySeason.put(e.season, l = new ArrayList<>());
            l.add(e);
        }
        Map<String, Catalog.Episode> named = new HashMap<>();
        int asked = 0;
        for (Map.Entry<Integer, List<Catalog.Episode>> s : bySeason.entrySet()) {
            // A dozen seasons' names at most; the rest as their files say.
            if (asked++ >= 12) break;
            for (Catalog.Episode e : Tmdb.named(s.getValue(), Tmdb.season(c, m.id, s.getKey()))) named.put(e.id, e);
        }
        List<Catalog.Episode> out = new ArrayList<>(all.size());
        for (Catalog.Episode e : all) out.add(named.containsKey(e.id) ? named.get(e.id) : e);
        return out;
    }

    // ---- for the player ---------------------------------------------------------------------------------------------

    /** Pure: a file as the player's copy: kept as "torbox:…" (its address made only as it starts), with what it is. */
    static Catalog.Play play(File f) {
        Catalog.Play p = new Catalog.Play(f.stored(), null, null);
        p.torbox = true;
        p.name = f.name;
        p.item = f.item == null || f.item.equals(f.name) ? "" : f.item;
        p.size = f.size;
        p.picture = picture(f);
        p.kind = Catalog.kindOf(f.name);
        String words = f.name + " " + f.item;
        p.hdr = Formats.hdr(words);
        p.sound = Formats.audio(words);
        return p;
    }

    /**
     * The account's copies of a film (each version, the surest first) or of a series' episode (its season and number),
     * as the player's; none when TorBox isn't in use, can't be reached or hasn't it. Off the main thread.
     */
    public static List<Catalog.Play> plays(Context c, Catalog.Title t, Catalog.Episode e) {
        List<Catalog.Play> out = new ArrayList<>();
        if (t == null || !on(c) || t.series && e == null) return out;
        List<File> lib;
        try {
            lib = library(c, false);
        } catch (IOException x) {
            return out;
        }
        Own own = t.torbox() ? owns.get(t.key) : null;
        List<File> found;
        if (own != null) {
            // One of the family's own titles: its own files, whatever name TMDB gave it.
            found = t.series ? episodeVersions(own.files, own.name, null, e.season, e.number)
                    : films(own.files, own.name, null, String.valueOf(own.year));
        } else {
            found = t.series ? episodeVersions(lib, t.name, null, e.season, e.number) : films(lib, t.name, null, t.year);
        }
        for (File f : found) {
            out.add(play(f));
        }
        return out;
    }

    /**
     * Pure: every copy in the order the player tries them: TorBox's (all of them) before the playlist's copy and its
     * copies in the family's other playlists, or after them all when Settings say so.
     */
    public static List<Catalog.Play> order(List<Catalog.Play> torbox, Catalog.Play playlist, boolean torboxFirst) {
        List<Catalog.Play> theirs = new ArrayList<>();
        if (playlist != null) {
            theirs.add(playlist);
            theirs.addAll(playlist.backups);
        }
        // The playlists' copies the best first too, as far as their names say (those alike in the playlists' order).
        Formats.sort(theirs);
        List<Catalog.Play> out = new ArrayList<>();
        if (torboxFirst && torbox != null) out.addAll(torbox);
        out.addAll(theirs);
        if (!torboxFirst && torbox != null) out.addAll(torbox);
        return out;
    }

    /** Pure: an address kept as TorBox's ("torbox:…"). */
    public static boolean ours(String stored) {
        return stored != null && stored.startsWith(SOURCE + ":");
    }

    /**
     * Where the player asks for a file kept as "torbox:kind:download:file": TorBox's own link for it, which sends the
     * player on to the file (with the key in it, as TorBox asks); null without a key or with TorBox turned off.
     */
    public static String address(Context c, String stored) {
        return on(c) ? address(key(c), stored) : null;
    }

    /** Pure: the same, given the key. */
    static String address(String key, String stored) {
        if (key == null || key.isEmpty() || !ours(stored)) return null;
        String[] p = stored.split(":");
        if (p.length < 4 || !p[2].matches("\\d+") || !p[3].matches("\\d+")) return null;
        String path, id;
        switch (p[1]) {
            case "usenet": path = "usenet"; id = "usenet_id"; break;
            case "webdl": path = "webdl"; id = "web_id"; break;
            default: path = "torrents"; id = "torrent_id"; break;
        }
        return API + path + "/requestdl?token=" + enc(key) + "&" + id + "=" + p[2] + "&file_id=" + p[3] + "&zip_link=false&redirect=true";
    }

    private static String enc(String v) {
        try {
            return URLEncoder.encode(v, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return v;
        }
    }
}
