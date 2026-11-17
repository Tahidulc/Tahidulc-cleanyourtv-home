package com.gathin.tvhomephone.live;

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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TorBox as a source for films and series beside the playlists: the family's own files already in their TorBox
 * account (its torrents, usenet and web downloads that have finished), matched to the title being played by their
 * names (the title and its year, a series' season and episode), each offered as a source as a playlist's is. Only what
 * is already there is used: nothing is searched for or added to the account. The key stays on this phone (never shown
 * again once saved, never in the log); an address carries it only when the player asks TorBox for the file.
 */
public final class TorBox {
    private TorBox() {}

    /** The source's name in the playlists' place (Vod.Item.source) and in the source picker. */
    static final String SOURCE = "torbox", NAME = "TorBox";
    private static final String API = "https://api.torbox.app/v1/api/";
    private static final String PREFS = "torbox";
    /** The library read again after this long: a film just added to TorBox shows here within it (or at once, Check). */
    private static final long FRESH_MS = 2 * 60_000L;
    /** At most this many versions of a film offered. */
    /** Every version of a film the account has (TorBox's are listed first and all of them); this only a guard. */
    private static final int VERSIONS = 12;
    /** After TorBox didn't answer, the pages go without it this long (then ask again; a retry first, at once). */
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

    static String key(Context c) {
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

    /** A copy found in TorBox (not a playlist's). */
    public static boolean from(Vod.Item it) {
        return it != null && SOURCE.equals(it.source);
    }

    /** An address kept for a TorBox file. */
    public static boolean from(String stored) {
        return ours(stored);
    }

    /** "your playlists or TorBox" while TorBox is in use, else "your playlists", for the pages' words. */
    public static String where(Context c) {
        return on(c) ? "your playlists or TorBox" : "your playlists";
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

        File(String kind, int itemId, int id, String name, String path, String item, long size) {
            this.kind = kind;
            this.itemId = itemId;
            this.id = id;
            this.name = name == null ? "" : name;
            this.path = path == null ? "" : path;
            this.item = item == null ? "" : item;
            this.size = size;
        }

        /** How it is kept (in Continue watching too): "torbox:torrent:12:3", never with the key. */
        String stored() {
            return SOURCE + ":" + kind + ":" + itemId + ":" + id;
        }
    }

    private static volatile List<File> library;
    private static volatile long libraryAt, failedAt;
    private static volatile String libraryFor = "", failedFor = "";
    /** One read of the library at a time (forgetting it never waits on one, so Settings' main thread never does). */
    private static final Object READING = new Object();

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
        // Not answering a moment ago: the pages don't wait on it again until the while is up.
        if (!fresh && k.equals(failedFor) && now - failedAt < FAILED_MS) throw new IOException("TorBox wasn't answering");
        List<File> out = new ArrayList<>();
        try {
            read(out, k, "torrents", "torrent");
        } catch (IOException first) {
            // A moment's retry before the page goes without TorBox: its files are the ones listed first.
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
            for (int j = 0; fs != null && j < fs.length(); j++) {
                JSONObject f = fs.optJSONObject(j);
                if (f == null) continue;
                String path = f.optString("name", "");
                String name = f.optString("short_name", "");
                if (name.isEmpty()) name = last(path);
                mine.add(new File(kind, it.optInt("id"), f.optInt("id"), name, path, it.optString("name", ""), f.optLong("size")));
            }
            int pictures = 0;
            for (File f : mine) if (video(f.name, f.size)) pictures++;
            for (File f : mine) f.single = pictures == 1 && video(f.name, f.size);
            out.addAll(mine);
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
                body = new String(readAll(in), StandardCharsets.UTF_8);
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

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toByteArray();
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

    /**
     * Pure: the order versions are offered in, the best first, with no limit on size (SourceInfo.compare): 4K, then
     * 1080p, 720p and the rest; within the same picture HDR (Dolby Vision or any other) before SDR, Dolby Atmos or
     * surround before stereo, then the bigger file; AVI and the like after them all.
     */
    static int compare(File a, File b) {
        return SourceInfo.compare(info(a), info(b));
    }

    /** What a file's name (and its download's) says of it, as the source picker shows it. */
    static SourceInfo info(File f) {
        return SourceInfo.torbox(f.name, f.size, f.item);
    }

    /** Files put in that order, each one's name read once. */
    static void sortBest(List<File> files) {
        java.util.Map<File, SourceInfo> said = new java.util.IdentityHashMap<>();
        for (File f : files) said.put(f, info(f));
        Collections.sort(files, (a, b) -> SourceInfo.compare(said.get(a), said.get(b)));
    }

    /** Pure: two titles alike as the playlists' are (Vod.key), and with their spaces gone ("Ocean's" and "Oceans"). */
    static boolean sameTitle(String title, String want, String alt) {
        String k = squash(title);
        if (k.isEmpty()) return false;
        return k.equals(squash(want)) || (alt != null && !alt.isEmpty() && k.equals(squash(alt)));
    }

    /** A title as Vod.key makes it, spaces gone; one that is only a year ("1917", "2012") as it is. */
    private static String squash(String title) {
        String t = title == null ? "" : title.replace("'", "").replace("’", "");
        String k = Vod.key(t).replace(" ", "");
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
        sortBest(out);
        return out.size() > VERSIONS ? new ArrayList<>(out.subList(0, VERSIONS)) : out;
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

    private static final java.util.concurrent.atomic.AtomicBoolean WARMING = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * For a poster's corner: the best picture and sound the account's own files of a title say they are (SourceQuality's
     * bits), from the library read last, whatever its age. Nothing is read for a poster: with no library yet, a first
     * read is started in the background and this says nothing. 0 with TorBox off, or nothing found. Off the main thread.
     */
    static int posterQuality(Context c, String title, String year, boolean tv) {
        if (title == null || title.isEmpty() || !on(c)) return 0;
        List<File> lib = library;
        if (lib == null || !key(c).equals(libraryFor)) {
            warm(c);
            return 0;
        }
        return posterQuality(lib, title, year, tv);
    }

    /** Pure: the same, given the library. */
    static int posterQuality(List<File> lib, String title, String year, boolean tv) {
        // Only the files that name the title's first word are looked at closely.
        String word = firstWord(title);
        List<File> near = new ArrayList<>();
        for (File f : lib) if (word.isEmpty() || mentions(f, word)) near.add(f);
        if (near.isEmpty()) return 0;
        int q = 0;
        for (File f : tv ? episodeFiles(near, title, null) : films(near, title, null, year)) {
            q = SourceQuality.best(q, SourceQuality.best(SourceQuality.of(f.name), SourceQuality.of(f.item)));
        }
        return q;
    }

    /** Pure: a title's first word as names compare ("dune" for "Dune: Part Two"); "" when it is under three letters. */
    static String firstWord(String title) {
        String k = Vod.key(title);
        int space = k.indexOf(' ');
        String w = space < 0 ? k : k.substring(0, space);
        return w.length() < 3 ? "" : w;
    }

    private static boolean mentions(File f, String word) {
        java.util.Locale root = java.util.Locale.ROOT;
        return f.name.toLowerCase(root).contains(word) || f.item.toLowerCase(root).contains(word)
                || f.path.toLowerCase(root).contains(word);
    }

    private static void warm(Context c) {
        Context app = c.getApplicationContext();
        if (!WARMING.compareAndSet(false, true)) return;
        com.gathin.tvhomephone.App.io.execute(() -> {
            try {
                library(app, false);
            } catch (IOException | RuntimeException ignored) {
                // none this time
            } finally {
                WARMING.set(false);
            }
        });
    }

    /** Pure: the picture a file's name says, else its download's ("1080p", "4K", "HD", "SD", or ""). */
    static String quality(File f) {
        String q = Vod.quality(f.name);
        return q.isEmpty() ? Vod.quality(f.item) : q;
    }

    /** Pure: how the source picker names a file: "TorBox · 1080p · MKV". */
    static String label(File f) {
        StringBuilder b = new StringBuilder(NAME);
        String q = quality(f), ext = Vod.extension(f.name);
        if (!q.isEmpty()) b.append(" · ").append(q);
        if (!ext.isEmpty()) b.append(" · ").append(ext);
        return b.toString();
    }

    // ---- for the playlists' lookup (Vod) --------------------------------------------------------------------------

    /**
     * The account's copies of a title as Vod's items: a film's versions (each its own item), or the series once (its
     * episodes read when it is chosen); none when TorBox isn't in use, can't be reached or hasn't it. Off the main thread.
     */
    static List<Vod.Item> find(Context c, String title, String alt, String year, boolean tv) {
        List<Vod.Item> out = new ArrayList<>();
        if (!on(c)) return out;
        List<File> lib;
        try {
            lib = library(c, false);
        } catch (IOException e) {
            return out;
        }
        String y = year == null ? "" : year;
        if (tv) {
            if (!episodeFiles(lib, title, alt).isEmpty()) {
                // The series under its own name, its original one kept beside it (in seriesId) for its episodes.
                Vod.Item it = new Vod.Item("series", title, "", "", "", "", y, alt == null ? "" : alt, SOURCE);
                it.label = NAME;
                it.about = SourceInfo.aboutTorBox("", 0, "");
                out.add(it);
            }
            return out;
        }
        for (File f : films(lib, title, alt, y)) {
            Vod.Item it = new Vod.Item("movie", f.name, f.stored(), "", "", "", y, "", SOURCE);
            it.label = label(f);
            it.about = SourceInfo.aboutTorBox(f.name, f.size, f.item);
            out.add(it);
        }
        return out;
    }

    /** A series' episodes in the account (each version of one, the surest first), for Vod.episodes. Off the main thread. */
    static List<Vod.Episode> episodes(Context c, Vod.Item series) {
        List<Vod.Episode> out = new ArrayList<>();
        if (!on(c) || series == null) return out;
        try {
            for (File f : episodeFiles(library(c, false), series.name, series.seriesId)) {
                Release r = episode(f);
                if (r == null) continue;
                Vod.Episode e = new Vod.Episode(r.season, r.episode, "", f.stored(), "", "");
                e.label = label(f);
                e.about = SourceInfo.aboutTorBox(f.name, f.size, f.item);
                out.add(e);
            }
        } catch (IOException ignored) {
            // none from TorBox this time
        }
        return out;
    }

    /** Pure: an address kept as TorBox's ("torbox:…"). */
    static boolean ours(String stored) {
        return stored != null && stored.startsWith(SOURCE + ":");
    }

    /**
     * Where the player asks for a file kept as "torbox:kind:download:file": TorBox's own link for it, which sends the
     * player on to the file (with the key in it, as TorBox asks); null without a key or with TorBox turned off.
     */
    static String address(Context c, String stored) {
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
