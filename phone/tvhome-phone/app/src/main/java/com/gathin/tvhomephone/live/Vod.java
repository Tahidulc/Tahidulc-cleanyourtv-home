package com.gathin.tvhomephone.live;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The films and series in the family's own playlists, for Watch: a film or series found there plays in the app (where
 * the agreed rule would have opened it in Debrid Stream). An M3U playlist's film and episode entries are kept as Live
 * TV reads it (Library); an Xtream Codes account's films and series are read from its player API, at most once a day.
 * Each playlist has its own index in the app's own files (an Xtream account's addresses are made at play time from the
 * login, never kept). Titles are matched by TMDB's id when the provider gives one, else by name and year, with the
 * providers' tags ("EN -", "4K", "(1999)") set aside. A title in more than one of the playlists in use is found once,
 * the others' copies kept as its backups (and an episode's the same way), for the player to go on to when one won't
 * play. Off the main thread.
 */
public final class Vod {
    private Vod() {}

    /** A film, or a series (its episodes read when it is chosen), in one playlist (source, its id). */
    public static final class Item {
        public final String kind, name, url, ua, ref, tmdb, year, seriesId, source;
        /** The same title in the other playlists in use, to go on to. */
        public final List<Item> alternates = new ArrayList<>(0);

        Item(String kind, String name, String url, String ua, String ref, String tmdb, String year, String seriesId, String source) {
            this.kind = kind;
            this.name = name;
            this.url = url;
            this.ua = ua;
            this.ref = ref;
            this.tmdb = tmdb;
            this.year = year;
            this.seriesId = seriesId;
            this.source = source;
        }

        /** Where it plays: {address, user agent, referrer}, its own first, then its backups'. */
        public List<String[]> plays() {
            List<String[]> out = new ArrayList<>();
            out.add(new String[]{url, ua, ref});
            for (Item a : alternates) out.add(new String[]{a.url, a.ua, a.ref});
            return out;
        }
    }

    /** One episode: its season and number, its name ("" when the playlist gives none) and where it plays. */
    public static final class Episode {
        public final int season, number;
        public final String title, url, ua, ref;
        /** The same episode in the other playlists in use. */
        public final List<Episode> alternates = new ArrayList<>(0);

        Episode(int season, int number, String title, String url, String ua, String ref) {
            this.season = season;
            this.number = number;
            this.title = title;
            this.url = url;
            this.ua = ua;
            this.ref = ref;
        }

        /** Where it plays: {address, user agent, referrer}, its own first, then its backups'. */
        public List<String[]> plays() {
            List<String[]> out = new ArrayList<>();
            out.add(new String[]{url, ua, ref});
            for (Episode a : alternates) out.add(new String[]{a.url, a.ua, a.ref});
            return out;
        }
    }

    /** The first playlist's index (as it was before there could be several); the others' are vod.<id>.tsv. */
    private static final String FILE = "vod.tsv";
    private static final long DAY = 24 * 3600_000L;
    private static final Pattern EPISODE = Pattern.compile("(?i)^(.*?)[\\s._-]*S(\\d{1,2})[\\s._-]*E(\\d{1,3})\\b[\\s._-]*(.*)$");
    private static final Pattern LEAD = Pattern.compile("^\\s*[\\[(|]?\\s*([A-Za-z0-9+]{1,6})\\s*[\\])|:\\-]+\\s*");
    private static final Pattern YEAR = Pattern.compile("(?:^|[\\s(\\[._-])((?:19|20)\\d{2})(?:$|[\\s)\\]._-])");

    // ---- the index ----------------------------------------------------------------------------------------------

    /** A playlist's index file. */
    private static File file(Context c, String id) {
        return new File(c.getFilesDir(), id == null || id.isEmpty() || "p0".equals(id) ? FILE : "vod." + id.replaceAll("[^A-Za-z0-9]", "") + ".tsv");
    }

    /**
     * An M3U playlist's films and episodes, as Live TV read them ({name, url, group, user agent, referrer, tvg-type}):
     * kept in its index, an episode under its series' name with its season and number.
     */
    static synchronized void saveM3u(Context c, String id, List<String[]> entries) {
        File f = file(c, id);
        File tmp = new File(f.getPath() + ".part");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            w.write("#m3u\n");
            for (String[] e : entries) {
                String name = e[0], url = e[1], ua = e[3] == null ? "" : e[3], ref = e[4] == null ? "" : e[4];
                Matcher m = EPISODE.matcher(name);
                boolean series = url.contains("/series/") || "series".equalsIgnoreCase(e[5]) || m.matches();
                if (series && m.matches()) {
                    row(w, "episode", m.group(1).trim(), m.group(2), m.group(3), m.group(4).trim(), url, ua, ref, "", "", "");
                } else {
                    row(w, "movie", name, "", "", "", url, ua, ref, "", year(name), "");
                }
            }
        } catch (IOException e) {
            return;
        }
        if (!tmp.renameTo(f)) tmp.delete();
    }

    private static void row(BufferedWriter w, String... cols) throws IOException {
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) w.write('\t');
            w.write(cols[i] == null ? "" : cols[i].replace('\t', ' ').replace('\n', ' '));
        }
        w.write('\n');
    }

    /** A playlist checked before it was added ("new"): its index kept under the id it was given. */
    static synchronized void moved(Context c, String from, String to) {
        File a = file(c, from), b = file(c, to);
        if (!a.exists() || a.equals(b)) return;
        b.delete();
        if (!a.renameTo(b)) a.delete();
    }

    /** A playlist removed: its index gone. */
    static synchronized void forget(Context c, String id) {
        file(c, id).delete();
    }

    /** Every playlist's index gone (Live TV's Remove everything). */
    static synchronized void clear(Context c) {
        File[] all = c.getFilesDir().listFiles((d, n) -> n.startsWith("vod") && (n.endsWith(".tsv") || n.endsWith(".part")));
        if (all != null) for (File f : all) f.delete();
    }

    /** A playlist's index now: an Xtream account's read again when a day old (or missing). */
    private static synchronized File index(Context c, Sources.Entry e) {
        File f = file(c, e.id);
        Source s = e.source;
        if (Source.XTREAM.equals(s.type)) {
            if (!f.exists() || System.currentTimeMillis() - f.lastModified() > DAY || !header(f).equals("#xtream")) xtream(c, s, f);
        } else if (!f.exists() || !header(f).equals("#m3u")) {
            // Not read yet (Live TV reads it too, and keeps its films then): the playlist read for them now.
            try (InputStream in = Fetch.open(s.m3uUrl, null); Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                saveM3u(c, e.id, M3u.parse(r).vod);
            } catch (IOException ignored) {
                // nothing to find in
            }
        }
        return f;
    }

    private static String header(File f) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String h = r.readLine();
            return h == null ? "" : h;
        } catch (IOException e) {
            return "";
        }
    }

    /** An Xtream account's films and series into the index (ids and names only; the addresses are made to play). */
    private static void xtream(Context c, Source s, File f) {
        String base = Xtream.base(s.server);
        if (base.isEmpty()) return;
        File tmp = new File(f.getPath() + ".part");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            w.write("#xtream\n");
            list(Xtream.api(base, s.user, s.pass, "get_vod_streams"), w, false);
            list(Xtream.api(base, s.user, s.pass, "get_series"), w, true);
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) tmp.delete();
    }

    private static void list(String url, BufferedWriter w, boolean series) throws IOException {
        try (InputStream in = Fetch.open(url, null); JsonReader jr = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            jr.setLenient(true);
            if (jr.peek() != JsonToken.BEGIN_ARRAY) return;
            jr.beginArray();
            while (jr.hasNext()) {
                if (jr.peek() != JsonToken.BEGIN_OBJECT) {
                    jr.skipValue();
                    continue;
                }
                String id = "", name = "", tmdb = "", ext = "", year = "";
                jr.beginObject();
                while (jr.hasNext()) {
                    String k = jr.nextName();
                    JsonToken t = jr.peek();
                    if (t == JsonToken.BEGIN_OBJECT || t == JsonToken.BEGIN_ARRAY || t == JsonToken.NULL || t == JsonToken.BOOLEAN) {
                        jr.skipValue();
                        continue;
                    }
                    String v = jr.nextString();
                    switch (k) {
                        case "stream_id": case "series_id": id = v; break;
                        case "name": name = v; break;
                        case "tmdb": case "tmdb_id": if (tmdb.isEmpty()) tmdb = v; break;
                        case "container_extension": ext = v; break;
                        case "releaseDate": case "release_date": case "year": if (year.isEmpty()) year = year(v); break;
                        default: break;
                    }
                }
                jr.endObject();
                if (id.isEmpty() || name.isEmpty()) continue;
                if (year.isEmpty()) year = year(name);
                if (series) row(w, "series", name, "", "", "", "", "", "", tmdb, year, id);
                else row(w, "movie", name, "", "", "", "x:movie:" + id + "." + (ext.isEmpty() ? "mp4" : ext), "", "", tmdb, year, "");
            }
            jr.endArray();
        } catch (IllegalStateException | NumberFormatException e) {
            throw new IOException("not the player API's answer");
        }
    }

    // ---- finding a title ------------------------------------------------------------------------------------------

    /**
     * The playlists' film (tv false) or series for a title: TMDB's id when the provider gives it, else the same name and
     * a year no more than one apart (either may be missing); the first playlist's that has it, with the others' copies as
     * its alternates; null when none has it, or there is no playlist.
     */
    public static Item find(Context c, String title, String altTitle, String year, int tmdbId, boolean tv) {
        Item main = null;
        for (Sources.Entry e : Sources.inUse(c)) {
            Item it = findIn(c, e, title, altTitle, year, tmdbId, tv);
            if (it == null) continue;
            if (main == null) main = it;
            else main.alternates.add(it);
        }
        return main;
    }

    private static Item findIn(Context c, Sources.Entry e, String title, String altTitle, String year, int tmdbId, boolean tv) {
        File f = index(c, e);
        if (!f.exists()) return null;
        String want = key(title), alt = altTitle == null ? "" : key(altTitle), id = tmdbId > 0 ? String.valueOf(tmdbId) : "";
        Item byName = null, byNameNoYear = null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            r.readLine();
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t", -1);
                if (p.length < 11) continue;
                String kind = p[0];
                boolean isSeries = kind.equals("series") || kind.equals("episode");
                if (isSeries != tv) continue;
                if (!id.isEmpty() && id.equals(p[8])) return item(kind, p, e.id);
                String k = key(p[1]);
                if (k.isEmpty() || !(k.equals(want) || (!alt.isEmpty() && k.equals(alt)))) continue;
                String y = p[9].isEmpty() ? year(p[1]) : p[9];
                if (!y.isEmpty() && year != null && !year.isEmpty()) {
                    if (Math.abs(parse(y) - parse(year)) <= 1 && byName == null) byName = item(kind, p, e.id);
                } else if (byNameNoYear == null) {
                    byNameNoYear = item(kind, p, e.id);
                }
            }
        } catch (IOException x) {
            return null;
        }
        return byName != null ? byName : byNameNoYear;
    }

    /** An index row: {kind, name, season, episode, episode's name, address, user agent, referrer, TMDB id, year, series id}. */
    private static Item item(String kind, String[] p, String source) {
        if (kind.equals("episode")) return new Item("series", p[1], "", p[6], p[7], p[8], p[9], "", source);
        return new Item(kind, p[1], stored(source, p[5]), p[6], p[7], p[8], p[9], p[10], source);
    }

    /**
     * An address as the player is given it: an Xtream one ("x:movie:123.mkv") marked with its playlist's id for the
     * login to be found ("p1|x:…"; the first playlist's unmarked, as before), any other as it is. Pure.
     */
    static String stored(String source, String url) {
        if (url == null || !url.startsWith("x:") || source == null || source.isEmpty() || "p0".equals(source)) return url;
        return source + "|" + url;
    }

    /**
     * A series' episodes, by season then number, from every playlist it was found in: an episode in more than one of them
     * once, the others' copies as its alternates.
     */
    public static List<Episode> episodes(Context c, Item series) {
        List<Episode> out = new ArrayList<>();
        if (series == null) return out;
        java.util.Map<Integer, Episode> at = new java.util.HashMap<>();
        List<Item> copies = new ArrayList<>();
        copies.add(series);
        copies.addAll(series.alternates);
        for (Item copy : copies) {
            for (Episode e : episodesIn(c, copy)) {
                Episode first = at.get(e.season * 10000 + e.number);
                if (first == null) {
                    at.put(e.season * 10000 + e.number, e);
                    out.add(e);
                } else {
                    first.alternates.add(e);
                }
            }
        }
        Collections.sort(out, (a, b) -> a.season != b.season ? Integer.compare(a.season, b.season) : Integer.compare(a.number, b.number));
        return out;
    }

    /** One playlist's episodes of a series: an Xtream account's asked for, an M3U playlist's from its index. */
    private static List<Episode> episodesIn(Context c, Item series) {
        List<Episode> out = new ArrayList<>();
        Sources.Entry src = Sources.get(c, series.source);
        if (src == null) return out;
        Source s = src.source;
        if (Source.XTREAM.equals(s.type) && !series.seriesId.isEmpty()) {
            String base = Xtream.base(s.server);
            try (InputStream in = Fetch.open(Xtream.api(base, s.user, s.pass, "get_series_info") + "&series_id=" + Xtream.enc(series.seriesId), null)) {
                JSONObject root = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
                JSONObject eps = root.optJSONObject("episodes");
                if (eps != null) {
                    for (java.util.Iterator<String> it = eps.keys(); it.hasNext(); ) {
                        String season = it.next();
                        JSONArray a = eps.optJSONArray(season);
                        for (int i = 0; a != null && i < a.length(); i++) {
                            JSONObject e = a.optJSONObject(i);
                            if (e == null || e.optString("id").isEmpty()) continue;
                            String ext = e.optString("container_extension", "mp4");
                            out.add(new Episode(e.optInt("season", parse(season)), e.optInt("episode_num", i + 1), e.optString("title", ""),
                                    stored(series.source, "x:series:" + e.optString("id") + "." + ext), "", ""));
                        }
                    }
                }
            } catch (Exception ignored) {
                // none to show
            }
        } else {
            String want = key(series.name);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file(c, series.source)), StandardCharsets.UTF_8))) {
                r.readLine();
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.split("\t", -1);
                    if (p.length < 11 || !p[0].equals("episode") || !key(p[1]).equals(want)) continue;
                    out.add(new Episode(parse(p[2]), parse(p[3]), p[4], p[5], p[6], p[7]));
                }
            } catch (IOException ignored) {
                // none to show
            }
        }
        return out;
    }

    /**
     * Where a film or episode plays: an M3U entry's own address, an Xtream one made from its playlist's login (the
     * first playlist's when unmarked); null when that playlist has gone.
     */
    static String address(Context c, String url) {
        if (url == null) return null;
        String source = "p0";
        int bar = url.indexOf('|');
        if (bar > 0 && url.startsWith("p") && url.substring(bar + 1).startsWith("x:")) {
            source = url.substring(0, bar);
            url = url.substring(bar + 1);
        }
        if (!url.startsWith("x:")) return url;
        Sources.Entry e = Sources.get(c, source);
        if (e == null || !Source.XTREAM.equals(e.source.type)) return null;
        Source s = e.source;
        String base = Xtream.base(s.server);
        String[] p = url.split(":", 3);
        return base + "/" + p[1] + "/" + Xtream.path(s.user) + "/" + Xtream.path(s.pass) + "/" + p[2];
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toByteArray();
    }

    // ---- names --------------------------------------------------------------------------------------------------

    /** The tags providers put before a title: languages and countries, services, the picture and the like. */
    private static final java.util.Set<String> TAGS = new java.util.HashSet<>(java.util.Arrays.asList(
            "en", "eng", "english", "uk", "gb", "us", "usa", "multi", "multisub", "sub", "subs", "dub", "dubbed", "vf", "vost",
            "4k", "uhd", "fhd", "hd", "sd", "hdr", "hdr10", "dv", "hevc", "x264", "x265", "h264", "h265", "1080p", "720p", "2160p",
            "web", "webdl", "webrip", "bluray", "netflix", "nf", "amzn", "dsnp", "atvp", "max", "hmax", "vod", "movie", "movies",
            "film", "series", "tv"));
    /** The tags that may come anywhere in a name, as no title's own word does: the picture's and the copy's. */
    private static final java.util.Set<String> PICTURE = new java.util.HashSet<>(java.util.Arrays.asList(
            "4k", "uhd", "fhd", "hd", "sd", "hdr", "hdr10", "hevc", "x264", "x265", "h264", "h265", "1080p", "720p", "2160p", "webdl",
            "webrip", "bluray", "multi", "multisub"));

    /**
     * A title made comparable (pure, for the tests): lower case, accents dropped, "&" as and, the year and the providers'
     * tags set aside, punctuation gone: "EN - The Matrix (1999) 4K" and "The Matrix" alike.
     */
    static String key(String name) {
        String n = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        // Tags before a separator at the start ("EN - ", "|UK| ", "[4K] ", "NF: "), but not a title's own first word
        // ("X-Men", "It - Chapter Two"): a known tag, or two capitals.
        Matcher lead = LEAD.matcher(n);
        while (lead.lookingAt() && lead.end() < n.length()) {
            String tag = lead.group(1);
            boolean known = TAGS.contains(tag.toLowerCase(Locale.ROOT)) || (tag.length() == 2 && tag.equals(tag.toUpperCase(Locale.ROOT))
                    && !tag.equals(tag.toLowerCase(Locale.ROOT)));
            if (!known) break;
            n = n.substring(lead.end());
            lead = LEAD.matcher(n);
        }
        n = n.toLowerCase(Locale.ROOT).replace("&", " and ");
        n = n.replaceAll("[\\[(]?(?:19|20)\\d{2}[\\])]?", " ");
        StringBuilder b = new StringBuilder();
        for (String w : n.split("[^a-z0-9]+")) {
            if (w.isEmpty() || PICTURE.contains(w)) continue;
            if (b.length() > 0) b.append(' ');
            b.append(w);
        }
        String out = b.toString();
        if (out.startsWith("the ")) out = out.substring(4);
        return out;
    }

    /** A year in a name ("The Matrix (1999)"), or "". */
    static String year(String name) {
        Matcher m = YEAR.matcher(name == null ? "" : name);
        String last = "";
        while (m.find()) last = m.group(1);
        return last;
    }

    private static int parse(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
