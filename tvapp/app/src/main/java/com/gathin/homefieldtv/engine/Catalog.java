package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;

import org.json.JSONArray;
import org.json.JSONException;
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
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Movies and TV shows from the family's own playlists, and from nowhere else: an Xtream Codes account's film and
 * series catalogues (its categories, its films, its series, read again after a day), an M3U playlist's film and episode
 * entries (grouped by their group-title, each episode under its series by its S01E02). Kept in the app's private files
 * so the shelves draw at once next time. A title's details (synopsis, cast, seasons and episodes) are asked for when its
 * page opens. Where a film or an episode plays is made from the playlist's login only at play time: never kept in a
 * catalogue file for an Xtream account, and never logged.
 */
public final class Catalog {
    static final long REFRESH_MS = 24 * 3600_000L;
    private static Catalog instance;

    /** A film or a series from one of the family's playlists. */
    public static final class Title {
        /** "p0|m|123": the playlist, film or series, the provider's id. The same across refreshes (resume points use it). */
        public final String key;
        public final boolean series;
        /** The name as shown (the providers' tags and the year taken off), its year, its category's name. */
        public final String name, year, category;
        /** Pictures: the poster (2:3), and a wide backdrop when the list gives one ("" otherwise). */
        public final String poster, backdrop;
        /** Out of ten, "" when not known. */
        public final String rating;
        /** A series' synopsis from the list ("" for films until their details are read). */
        public final String plot;
        /** When the provider added it (ms), 0 when not known. */
        public final long added;
        /**
         * The picture its name (or its category's) says this copy has: PICTURE_4K, PICTURE_1080, PICTURE_720, PICTURE_SD,
         * or 0 when it says none; and whether it says Dolby Vision or Dolby Atmos.
         */
        public final int picture;
        public final boolean dolbyVision, atmos;
        final String source, id, ext, url, userAgent, referrer;
        /** The same title in the family's other playlists, played when this one doesn't start. */
        final List<Title> copies = new ArrayList<>(0);

        Title(String source, boolean series, String id, String rawName, String category, String poster, String backdrop,
              String rating, String plot, long added, String ext, String url, String userAgent, String referrer, String year) {
            this.source = source;
            this.series = series;
            this.id = id;
            this.key = source + (series ? "|s|" : "|m|") + id;
            this.name = displayName(rawName);
            String y = year == null || year.isEmpty() ? year(rawName) : year(year);
            this.year = y;
            this.category = category == null ? "" : category;
            this.poster = poster == null ? "" : poster.trim();
            this.backdrop = backdrop == null ? "" : backdrop.trim();
            this.rating = rating(rating);
            this.plot = plot == null ? "" : plot.trim();
            this.added = added;
            this.ext = ext == null ? "" : ext;
            this.url = url == null ? "" : url;
            this.userAgent = userAgent == null ? "" : userAgent;
            this.referrer = referrer == null ? "" : referrer;
            this.picture = Math.max(pictureOf(rawName), pictureOf(category) == PICTURE_4K ? PICTURE_4K : 0);
            this.dolbyVision = dolbyVisionOf(rawName) || dolbyVisionOf(category);
            this.atmos = atmosOf(rawName) || atmosOf(category);
        }

        /** The playlist it is from. */
        public String playlist() {
            return source;
        }

        /** The best picture this title has in any of the family's playlists (its copies' too). */
        public int bestPicture() {
            int p = picture;
            for (Title c : copies) p = Math.max(p, c.picture);
            return p;
        }

        /** Dolby Vision in any of its copies. */
        public boolean anyDolbyVision() {
            if (dolbyVision) return true;
            for (Title c : copies) if (c.dolbyVision) return true;
            return false;
        }

        /** Dolby Atmos in any of its copies. */
        public boolean anyAtmos() {
            if (atmos) return true;
            for (Title c : copies) if (c.atmos) return true;
            return false;
        }
    }

    /** The pictures a name can say it has, best last. */
    public static final int PICTURE_SD = 1, PICTURE_720 = 2, PICTURE_1080 = 3, PICTURE_4K = 4;

    /** Pure: the picture a name says ("Dune (2021) 4K", "[UHD] Dune", "Dune 1080p"), else 0. "HD" alone says no lines. */
    public static int pictureOf(String name) {
        int best = 0;
        for (String t : words(name)) {
            switch (t) {
                case "4k": case "uhd": case "2160p": case "2160": case "4kuhd": case "uhd4k": best = Math.max(best, PICTURE_4K); break;
                case "1080p": case "1080": case "fhd": case "fullhd": best = Math.max(best, PICTURE_1080); break;
                case "720p": case "720": best = Math.max(best, PICTURE_720); break;
                case "sd": case "480p": case "576p": best = Math.max(best, PICTURE_SD); break;
                default: break;
            }
        }
        return best;
    }

    /** Pure: a name saying Dolby Vision ("DV", "DoVi", "Dolby Vision"). */
    public static boolean dolbyVisionOf(String name) {
        List<String> w = words(name);
        for (int i = 0; i < w.size(); i++) {
            String t = w.get(i);
            if (t.equals("dv") || t.equals("dovi") || t.equals("dolbyvision")) return true;
            if (t.equals("dolby") && i + 1 < w.size() && w.get(i + 1).equals("vision")) return true;
        }
        return false;
    }

    /** Pure: a name saying Dolby Atmos. */
    public static boolean atmosOf(String name) {
        return words(name).contains("atmos");
    }

    /** A name's words, lower case, split at anything but letters, digits and "+". */
    private static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = Character.toLowerCase(s.charAt(i));
            if (Character.isLetterOrDigit(ch)) {
                b.append(ch);
            } else if (b.length() > 0) {
                out.add(b.toString());
                b.setLength(0);
            }
        }
        if (b.length() > 0) out.add(b.toString());
        return out;
    }

    /** One of a provider's categories with its titles, in the provider's order. */
    public static final class Category {
        public final String name;
        public final List<Title> titles;

        Category(String name, List<Title> titles) {
            this.name = name;
            this.titles = Collections.unmodifiableList(titles);
        }
    }

    /** What a title's page shows beyond the list: the synopsis, people, running time, seasons and episodes. */
    public static final class Details {
        public final String plot, genre, cast, director, released, backdrop, poster, trailer, rating, age;
        /** The running time in minutes, 0 when not known. */
        public final int minutes;
        /** A series' seasons in order, each with its episodes; empty for a film. */
        public final List<Season> seasons;

        Details(String plot, String genre, String cast, String director, String released, String backdrop, String poster,
                String trailer, String rating, String age, int minutes, List<Season> seasons) {
            this.plot = clean(plot);
            this.genre = clean(genre);
            this.cast = clean(cast);
            this.director = clean(director);
            this.released = clean(released);
            this.backdrop = clean(backdrop);
            this.poster = clean(poster);
            this.trailer = clean(trailer);
            this.rating = rating(rating);
            this.age = clean(age);
            this.minutes = minutes;
            this.seasons = seasons == null ? Collections.emptyList() : Collections.unmodifiableList(seasons);
        }

        /** Every episode, season by season. */
        public List<Episode> episodes() {
            List<Episode> out = new ArrayList<>();
            for (Season s : seasons) out.addAll(s.episodes);
            return out;
        }

        /** An episode by its id, or null. */
        public Episode episode(String id) {
            for (Season s : seasons) for (Episode e : s.episodes) if (e.id.equals(id)) return e;
            return null;
        }

        /** The episode after this one (the next in its season, else the next season's first), or null at the end. */
        public Episode after(Episode e) {
            List<Episode> all = episodes();
            for (int i = 0; i < all.size() - 1; i++) if (all.get(i).id.equals(e.id)) return all.get(i + 1);
            return null;
        }
    }

    public static final class Season {
        public final int number;
        public final String name;
        public final List<Episode> episodes;

        Season(int number, String name, List<Episode> episodes) {
            this.number = number;
            this.name = name == null || name.trim().isEmpty() ? "Series " + number : name.trim();
            this.episodes = Collections.unmodifiableList(episodes);
        }
    }

    public static final class Episode {
        public final String id;
        public final int season, number;
        public final String title, plot, still;
        public final int minutes;
        /** When it was first shown, as the provider gives it ("2022-10-09"); "" when not known. */
        public final String aired;
        final String ext, url, userAgent, referrer;

        Episode(String id, int season, int number, String title, String plot, String still, int minutes, String ext, String url,
                String userAgent, String referrer) {
            this(id, season, number, title, plot, still, minutes, "", ext, url, userAgent, referrer);
        }

        Episode(String id, int season, int number, String title, String plot, String still, int minutes, String aired, String ext,
                String url, String userAgent, String referrer) {
            this.aired = clean(aired);
            this.id = id;
            this.season = season;
            this.number = number;
            this.title = title == null ? "" : title.trim();
            this.plot = clean(plot);
            this.still = clean(still);
            this.minutes = minutes;
            this.ext = ext == null ? "" : ext;
            this.url = url == null ? "" : url;
            this.userAgent = userAgent == null ? "" : userAgent;
            this.referrer = referrer == null ? "" : referrer;
        }
    }

    /** Where a film or episode plays, with its copies in the family's other playlists to fall back on. */
    public static final class Play {
        public final String url, userAgent, referrer;
        public final List<Play> backups = new ArrayList<>(0);

        Play(String url, String userAgent, String referrer) {
            this.url = url;
            this.userAgent = userAgent == null ? "" : userAgent;
            this.referrer = referrer == null ? "" : referrer;
        }
    }

    public interface DetailsCallback {
        /** On the main thread: the details, or null when the provider gave none. */
        void got(Details d);
    }

    static final class Model {
        final List<Title> films, series;
        final List<Category> filmCategories, seriesCategories;
        final Map<String, Title> byKey;
        final Map<String, List<Episode>> episodes;
        final String[] filmNames, seriesNames;

        Model(List<Title> films, List<Title> series, List<Category> fc, List<Category> sc, Map<String, Title> byKey,
              Map<String, List<Episode>> episodes) {
            this.films = Collections.unmodifiableList(films);
            this.series = Collections.unmodifiableList(series);
            this.filmCategories = Collections.unmodifiableList(fc);
            this.seriesCategories = Collections.unmodifiableList(sc);
            this.byKey = byKey;
            this.episodes = episodes;
            this.filmNames = lowered(films);
            this.seriesNames = lowered(series);
        }

        static Model empty() {
            return new Model(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new HashMap<>(), new HashMap<>());
        }

        private static String[] lowered(List<Title> list) {
            String[] out = new String[list.size()];
            for (int i = 0; i < out.length; i++) out[i] = searchable(list.get(i).name);
            return out;
        }
    }

    private final Context app;
    private volatile Model model = Model.empty();
    private volatile boolean read;
    private volatile String status = "";
    private final AtomicBoolean busy = new AtomicBoolean();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Details> details = new LinkedHashMap<String, Details>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Details> e) {
            return size() > 60;
        }
    };

    private Catalog(Context app) {
        this.app = app;
    }

    public static synchronized Catalog get(Context c) {
        if (instance == null) instance = new Catalog(c.getApplicationContext());
        return instance;
    }

    // ---- what the screens read ----------------------------------------------------------------------------------

    public boolean ready() { Model m = model; return !m.films.isEmpty() || !m.series.isEmpty(); }
    public boolean busy() { return busy.get(); }
    public String status() { return status; }
    public List<Title> films() { return model.films; }
    public List<Title> series() { return model.series; }

    public List<Category> categories(boolean series) {
        Model m = model;
        return series ? m.seriesCategories : m.filmCategories;
    }

    public Title title(String key) {
        return key == null ? null : model.byKey.get(key);
    }

    /** The newest titles first, by when the provider added them; the list's last ones when it gives no dates. */
    public List<Title> recent(boolean series, int max) {
        List<Title> all = series ? model.series : model.films;
        return newest(all, max);
    }

    static List<Title> newest(List<Title> all, int max) {
        List<Title> dated = new ArrayList<>();
        for (Title t : all) if (t.added > 0) dated.add(t);
        List<Title> out;
        if (dated.size() >= Math.min(max, all.size()) / 2 && !dated.isEmpty()) {
            dated.sort((a, b) -> Long.compare(b.added, a.added));
            out = dated;
        } else {
            out = new ArrayList<>(all);
            Collections.reverse(out);
        }
        return new ArrayList<>(out.subList(0, Math.min(max, out.size())));
    }

    /** Titles whose name has the words typed: those starting with them first. */
    public List<Title> search(String query, boolean series, int max) {
        String q = searchable(query);
        List<Title> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        Model m = model;
        List<Title> all = series ? m.series : m.films;
        String[] names = series ? m.seriesNames : m.filmNames;
        List<Title> later = new ArrayList<>();
        for (int i = 0; i < names.length && out.size() < max; i++) {
            String n = names[i];
            if (n.startsWith(q) || n.startsWith("the " + q)) out.add(all.get(i));
            else if (later.size() < max && (n.contains(" " + q) || (q.length() > 2 && n.contains(q)))) later.add(all.get(i));
        }
        for (Title t : later) {
            if (out.size() >= max) break;
            out.add(t);
        }
        return out;
    }

    // ---- loading -------------------------------------------------------------------------------------------------

    /** The kept catalogues read if they haven't been, then read again from the providers when a day old. Any thread. */
    public void ensure() {
        if (read) {
            if (stale()) refresh();
            return;
        }
        Exec.io(() -> {
            synchronized (this) {
                if (!read) {
                    model = build(Sources.inUse(app));
                    read = true;
                }
            }
            changed();
            if (stale()) refresh();
        });
    }

    private boolean stale() {
        for (Sources.Entry e : Sources.inUse(app)) {
            if (!Source.XTREAM.equals(e.source.type)) continue;
            File f = file(app, e.id);
            if (!f.exists() || System.currentTimeMillis() - f.lastModified() > REFRESH_MS) return true;
        }
        return false;
    }

    /** The playlists' catalogues read again: an Xtream account's when a day old (or missing). */
    public void refresh() {
        refresh(false);
    }

    /** As refresh(), every Xtream account's read again however new (Settings' Refresh now). */
    public void refresh(boolean force) {
        if (!busy.compareAndSet(false, true)) return;
        changed();
        Exec.io(() -> {
            List<String> problems = new ArrayList<>();
            try {
                List<Sources.Entry> in = Sources.inUse(app);
                for (Sources.Entry e : in) {
                    if (!Source.XTREAM.equals(e.source.type)) continue;
                    File f = file(app, e.id);
                    if (!force && f.exists() && System.currentTimeMillis() - f.lastModified() < REFRESH_MS) continue;
                    try {
                        xtream(e.source, f);
                    } catch (IOException x) {
                        problems.add(e.name + ": " + (x.getMessage() == null ? "films and series didn't load" : x.getMessage()));
                    }
                }
                model = build(in);
                read = true;
                synchronized (details) {
                    details.clear();
                }
                status = String.join("; ", problems);
            } catch (RuntimeException e) {
                status = "Films and series didn't load";
            } finally {
                busy.set(false);
            }
            changed();
        });
    }

    /** Everything kept removed (Settings' Remove everything). */
    public void clear() {
        File[] all = dir(app).listFiles();
        if (all != null) for (File f : all) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        model = Model.empty();
        synchronized (details) {
            details.clear();
        }
        status = "";
        changed();
    }

    // ---- the files: one a playlist --------------------------------------------------------------------------------

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "catalog");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static File file(Context c, String id) {
        return new File(dir(c), (id == null ? "" : id.replaceAll("[^A-Za-z0-9]", "")) + ".tsv");
    }

    /**
     * An M3U playlist's films and episodes as Library read them ({name, address, group, user agent, referrer,
     * tvg-type, poster}): a film under its group, an episode under its series by its S01E02 (or 1x02).
     */
    public static synchronized void saveM3u(Context c, String id, List<String[]> entries) {
        File f = file(c, id), tmp = new File(f.getPath() + ".part");
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8), 64 * 1024)) {
            writeM3u(w, entries, System.currentTimeMillis());
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    static final Pattern EPISODE = Pattern.compile("(?i)^(.*?)[\\s._-]*S(\\d{1,2})[\\s._-]*E(\\d{1,3})\\b[\\s._:-]*(.*)$");
    static final Pattern EPISODE_X = Pattern.compile("(?i)^(.*?)[\\s._-]+(\\d{1,2})x(\\d{1,3})\\b[\\s._:-]*(.*)$");

    static void writeM3u(Writer w, List<String[]> entries, long at) throws IOException {
        w.write("v1\tm3u\t" + at + "\n");
        Map<String, Boolean> fc = new LinkedHashMap<>(), sc = new LinkedHashMap<>();
        Map<String, String[]> shows = new LinkedHashMap<>();
        StringBuilder films = new StringBuilder(), eps = new StringBuilder();
        Map<String, Integer> seen = new HashMap<>();
        for (String[] e : entries) {
            if (e == null || e.length < 2 || e[1] == null || e[1].isEmpty()) continue;
            String name = e[0] == null ? "" : e[0].trim(), url = e[1];
            String group = e.length > 2 && e[2] != null ? e[2].trim() : "";
            String ua = e.length > 3 ? e[3] : "", ref = e.length > 4 ? e[4] : "", type = e.length > 5 ? e[5] : "";
            String logo = e.length > 6 ? e[6] : "";
            Matcher m = EPISODE.matcher(name);
            boolean matched = m.matches();
            if (!matched) {
                m = EPISODE_X.matcher(name);
                matched = m.matches();
            }
            boolean series = matched && (url.toLowerCase(Locale.ROOT).contains("/series/") || "series".equalsIgnoreCase(type)
                    || !url.toLowerCase(Locale.ROOT).contains("/movie/"));
            if (series && !m.group(1).trim().isEmpty()) {
                String show = m.group(1).trim().replaceAll("[\\s._-]+$", "");
                String sid = key(show);
                if (sid.isEmpty()) sid = show.toLowerCase(Locale.ROOT);
                sid = sid.replace(' ', '-');
                if (!shows.containsKey(sid)) {
                    shows.put(sid, new String[]{show, group, logo});
                    if (!group.isEmpty()) sc.put(group, Boolean.TRUE);
                }
                row(eps, "e", sid, m.group(2), m.group(3), m.group(4).trim(), url, ua, ref, logo);
            } else {
                String base = Integer.toHexString((key(name) + "|" + group).hashCode());
                Integer n = seen.get(base);
                seen.put(base, n == null ? 1 : n + 1);
                String fid = n == null ? base : base + "-" + (n + 1);
                if (!group.isEmpty()) fc.put(group, Boolean.TRUE);
                row(films, "m", fid, name, group, logo, "", "0", "", year(name), url, ua, ref);
            }
        }
        for (String g : fc.keySet()) w.write(line("c", "m", g, g));
        for (String g : sc.keySet()) w.write(line("c", "s", g, g));
        w.write(films.toString());
        for (Map.Entry<String, String[]> s : shows.entrySet()) {
            String[] v = s.getValue();
            w.write(line("s", s.getKey(), v[0], v[1], v[2], "", "0", year(v[0]), "", ""));
        }
        w.write(eps.toString());
    }

    private static void row(StringBuilder b, String... cols) {
        b.append(line(cols));
    }

    private static String line(String... cols) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) b.append('\t');
            b.append(Library.cell(cols[i]));
        }
        return b.append('\n').toString();
    }

    /** A playlist checked before it was added ("new"): its catalogue kept under the id it was given. */
    static synchronized void moved(Context c, String from, String to) {
        File a = file(c, from), b = file(c, to);
        if (!a.exists() || a.equals(b)) return;
        //noinspection ResultOfMethodCallIgnored
        b.delete();
        if (!a.renameTo(b)) {
            //noinspection ResultOfMethodCallIgnored
            a.delete();
        }
    }

    /** A playlist removed: its catalogue gone. */
    static synchronized void forget(Context c, String id) {
        //noinspection ResultOfMethodCallIgnored
        file(c, id).delete();
        Catalog cat = instance;
        if (cat != null) cat.refresh();
    }

    /** An Xtream account's categories, films and series into its file (ids and names: the addresses are made to play). */
    private void xtream(Source s, File f) throws IOException {
        String base = Xtream.base(s.server);
        if (base.isEmpty()) throw new IOException("No server address");
        File tmp = new File(f.getPath() + ".part");
        boolean any = false;
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8), 64 * 1024)) {
            w.write("v1\txtream\t" + System.currentTimeMillis() + "\n");
            any |= categories(Xtream.api(base, s.user, s.pass, "get_vod_categories"), "m", w);
            any |= categories(Xtream.api(base, s.user, s.pass, "get_series_categories"), "s", w);
            try (InputStream in = Fetch.open(Xtream.api(base, s.user, s.pass, "get_vod_streams"), null)) {
                any |= readList(in, false, w) > 0;
            }
            try (InputStream in = Fetch.open(Xtream.api(base, s.user, s.pass, "get_series"), null)) {
                any |= readList(in, true, w) > 0;
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }
        if (!any) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("No films or series in it");
        }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static boolean categories(String url, String kind, Writer w) throws IOException {
        String text;
        try {
            text = Library.text(url);
        } catch (IOException e) {
            return false;
        }
        return categoriesFrom(text, kind, w) > 0;
    }

    /** get_vod_categories or get_series_categories' answer as rows (pure, for the tests). */
    static int categoriesFrom(String json, String kind, Writer w) throws IOException {
        int n = 0;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || o.optString("category_id").isEmpty()) continue;
                w.write(line("c", kind, o.optString("category_id"), o.optString("category_name")));
                n++;
            }
        } catch (JSONException e) {
            // none
        }
        return n;
    }

    /** get_vod_streams or get_series' answer, read as it arrives (tens of thousands of entries), as rows. */
    static int readList(InputStream in, boolean series, Writer w) throws IOException {
        int n = 0;
        try (JsonReader jr = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            jr.setLenient(true);
            if (jr.peek() != JsonToken.BEGIN_ARRAY) return 0;
            jr.beginArray();
            while (jr.hasNext()) {
                if (jr.peek() != JsonToken.BEGIN_OBJECT) {
                    jr.skipValue();
                    continue;
                }
                String id = "", name = "", cat = "", icon = "", rating = "", added = "", ext = "", year = "", plot = "", backdrop = "";
                jr.beginObject();
                while (jr.hasNext()) {
                    String k = jr.nextName();
                    if (k.equals("backdrop_path") && jr.peek() == JsonToken.BEGIN_ARRAY) {
                        jr.beginArray();
                        while (jr.hasNext()) {
                            String v = Library.value(jr);
                            if (backdrop.isEmpty()) backdrop = v;
                        }
                        jr.endArray();
                        continue;
                    }
                    String v = Library.value(jr);
                    switch (k) {
                        case "stream_id": case "series_id": id = v; break;
                        case "name": name = v; break;
                        case "category_id": cat = v; break;
                        case "stream_icon": case "cover": if (icon.isEmpty()) icon = v; break;
                        case "rating": rating = v; break;
                        case "added": case "last_modified": if (added.isEmpty() || k.equals("last_modified")) added = v; break;
                        case "container_extension": ext = v; break;
                        case "year": case "releaseDate": case "release_date": if (year.isEmpty()) year = year(v); break;
                        case "plot": plot = v; break;
                        case "backdrop_path": if (backdrop.isEmpty()) backdrop = v; break;
                        default: break;
                    }
                }
                jr.endObject();
                if (id.isEmpty() || name.isEmpty()) continue;
                long at = Library.number(added) > 0 ? parseLong(added) * 1000L : 0;
                if (series) w.write(line("s", id, name, cat, icon, rating, String.valueOf(at), year, plot, backdrop));
                else w.write(line("m", id, name, cat, icon, rating, String.valueOf(at), ext, year, "", "", ""));
                n++;
            }
            jr.endArray();
        } catch (IllegalStateException | NumberFormatException e) {
            if (n == 0) throw new IOException("That server didn't answer as Xtream Codes does");
        }
        return n;
    }

    private static long parseLong(String s) {
        try {
            return (long) Double.parseDouble(s.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** One playlist's file as read back. */
    static final class Read {
        final String source;
        final Map<String, String> filmCats = new LinkedHashMap<>(), seriesCats = new LinkedHashMap<>();
        final List<Title> films = new ArrayList<>(), series = new ArrayList<>();
        final Map<String, List<Episode>> episodes = new HashMap<>();

        Read(String source) {
            this.source = source;
        }
    }

    static Read read(BufferedReader r, String source) throws IOException {
        Read out = new Read(source);
        Map<String, Set<String>> ids = new HashMap<>();
        String head = r.readLine();
        if (head == null || !head.startsWith("v1\t")) return out;
        Map<String, String> pool = new HashMap<>();
        for (String line; (line = r.readLine()) != null; ) {
            String[] c = line.split("\t", -1);
            switch (c[0]) {
                case "c":
                    if (c.length >= 4) (c[1].equals("s") ? out.seriesCats : out.filmCats).put(c[2], shared(pool, c[3]));
                    break;
                case "m":
                    if (c.length >= 12) {
                        out.films.add(new Title(source, false, c[1], c[2], shared(pool, out.filmCats.containsKey(c[3]) ? out.filmCats.get(c[3]) : ""),
                                c[4], "", c[5], "", parseLong(c[6]), c[7], c[9], c[10], c[11], c[8]));
                    }
                    break;
                case "s":
                    if (c.length >= 10) {
                        out.series.add(new Title(source, true, c[1], c[2], shared(pool, out.seriesCats.containsKey(c[3]) ? out.seriesCats.get(c[3]) : ""),
                                c[4], c[9], c[5], c[8], parseLong(c[6]), "", "", "", "", c[7]));
                    }
                    break;
                case "e":
                    if (c.length >= 9) {
                        List<Episode> l = out.episodes.get(c[1]);
                        Set<String> had = ids.get(c[1]);
                        if (l == null) {
                            out.episodes.put(c[1], l = new ArrayList<>());
                            ids.put(c[1], had = new HashSet<>());
                        }
                        int season = Library.number(c[2]), number = Library.number(c[3]);
                        String id = c[1] + ":" + season + ":" + number;
                        for (int n = 2; !had.add(id); n++) id = c[1] + ":" + season + ":" + number + "#" + n;
                        l.add(new Episode(id, season, number, c[4], "", c[8], 0, "", c[5], c[6], c[7]));
                    }
                    break;
                default:
                    break;
            }
        }
        return out;
    }

    private static String shared(Map<String, String> pool, String s) {
        String had = pool.get(s);
        if (had != null) return had;
        pool.put(s, s);
        return s;
    }

    /** Every playlist in use read and made one: a title in several of them shown once, the others' copies its backups. */
    private Model build(List<Sources.Entry> in) {
        List<Read> reads = new ArrayList<>();
        for (Sources.Entry e : in) {
            File f = file(app, e.id);
            if (!f.exists()) continue;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8), 64 * 1024)) {
                reads.add(read(r, e.id));
            } catch (IOException | RuntimeException ignored) {
                // that playlist's titles left out until it is read again
            }
        }
        return merge(reads);
    }

    static Model merge(List<Read> reads) {
        List<Title> films = new ArrayList<>(), series = new ArrayList<>();
        Map<String, Title> byKey = new HashMap<>(), first = new HashMap<>();
        Map<String, List<Episode>> episodes = new HashMap<>();
        LinkedHashMap<String, List<Title>> fc = new LinkedHashMap<>(), sc = new LinkedHashMap<>();
        for (Read r : reads) {
            for (String n : r.filmCats.values()) if (!fc.containsKey(n)) fc.put(n, new ArrayList<>());
            for (String n : r.seriesCats.values()) if (!sc.containsKey(n)) sc.put(n, new ArrayList<>());
            for (int pass = 0; pass < 2; pass++) {
                boolean isSeries = pass == 1;
                for (Title t : isSeries ? r.series : r.films) {
                    String k = (isSeries ? "s|" : "m|") + key(t.name) + "|" + t.year;
                    Title had = first.get(k);
                    if (had != null && !had.source.equals(t.source)) {
                        had.copies.add(t);
                        byKey.put(t.key, had);
                        continue;
                    }
                    if (had == null) first.put(k, t);
                    (isSeries ? series : films).add(t);
                    byKey.put(t.key, t);
                    LinkedHashMap<String, List<Title>> cats = isSeries ? sc : fc;
                    String cat = t.category.isEmpty() ? (isSeries ? "TV shows" : "Movies") : t.category;
                    List<Title> l = cats.get(cat);
                    if (l == null) cats.put(cat, l = new ArrayList<>());
                    l.add(t);
                }
            }
            for (Map.Entry<String, List<Episode>> e : r.episodes.entrySet()) {
                List<Episode> l = new ArrayList<>(e.getValue());
                l.sort((a, b) -> a.season != b.season ? Integer.compare(a.season, b.season) : Integer.compare(a.number, b.number));
                episodes.put(r.source + "|s|" + e.getKey(), l);
            }
        }
        List<Category> fcs = new ArrayList<>(), scs = new ArrayList<>();
        for (Map.Entry<String, List<Title>> e : fc.entrySet()) if (!e.getValue().isEmpty()) fcs.add(new Category(e.getKey(), e.getValue()));
        for (Map.Entry<String, List<Title>> e : sc.entrySet()) if (!e.getValue().isEmpty()) scs.add(new Category(e.getKey(), e.getValue()));
        return new Model(films, series, fcs, scs, byKey, episodes);
    }

    // ---- a title's details ----------------------------------------------------------------------------------------

    /** The details kept from before (a page opened again), or null. */
    public Details cachedDetails(Title t) {
        if (t == null) return null;
        synchronized (details) {
            return details.get(t.key);
        }
    }

    /**
     * A title's details: an Xtream film's or series' asked of its provider (get_vod_info, get_series_info), an M3U
     * series' seasons from its episodes. Answered on the main thread.
     */
    public void details(Title t, DetailsCallback cb) {
        Details had = cachedDetails(t);
        if (had != null || t == null) {
            Details d = had;
            Exec.main(() -> cb.got(d));
            return;
        }
        Exec.io(() -> {
            Details d = null;
            try {
                d = fetchDetails(t);
            } catch (IOException | JSONException | RuntimeException e) {
                // the page shows what the list gave; never the address
                android.util.Log.w("HomefieldTv", "details not read: " + e.getClass().getSimpleName());
            }
            if (d != null) synchronized (details) {
                details.put(t.key, d);
            }
            Details got = d;
            Exec.main(() -> cb.got(got));
        });
    }

    private Details fetchDetails(Title t) throws IOException, JSONException {
        Sources.Entry e = Sources.get(app, t.source);
        if (e == null) return null;
        Source s = e.source;
        if (!Source.XTREAM.equals(s.type)) {
            if (!t.series) return new Details("", "", "", "", t.year, t.backdrop, t.poster, "", t.rating, "", 0, null);
            List<Episode> eps = model.episodes.get(t.key);
            return new Details(t.plot, "", "", "", t.year, t.backdrop, t.poster, "", t.rating, "", 0, seasons(eps, null));
        }
        String base = Xtream.base(s.server);
        String action = t.series ? "get_series_info&series_id=" : "get_vod_info&vod_id=";
        String json = Library.text(Xtream.api(base, s.user, s.pass, null) + "&action=" + action + Xtream.enc(t.id));
        return t.series ? seriesInfo(json, t) : filmInfo(json, t);
    }

    /** get_vod_info's answer (pure, for the tests). */
    static Details filmInfo(String json, Title t) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject i = root.optJSONObject("info");
        if (i == null) i = new JSONObject();
        int secs = Library.number(i.optString("duration_secs"));
        int mins = secs > 0 ? Math.round(secs / 60f) : minutes(i.optString("duration"));
        if (mins == 0) mins = Library.number(i.optString("episode_run_time"));
        String poster = first(i.optString("movie_image"), i.optString("cover_big"), t == null ? "" : t.poster);
        return new Details(first(i.optString("plot"), i.optString("description")), i.optString("genre"),
                first(i.optString("cast"), i.optString("actors")), i.optString("director"),
                first(i.optString("releasedate"), i.optString("release_date"), t == null ? "" : t.year), backdrop(i),
                poster, i.optString("youtube_trailer"), first(i.optString("rating"), t == null ? "" : t.rating),
                first(i.optString("mpaa_rating"), i.optString("age")), mins, null);
    }

    /** get_series_info's answer (pure, for the tests): its seasons and episodes, in order. */
    static Details seriesInfo(String json, Title t) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject i = root.optJSONObject("info");
        if (i == null) i = new JSONObject();
        Map<Integer, String> names = new HashMap<>();
        JSONArray seasons = root.optJSONArray("seasons");
        for (int k = 0; seasons != null && k < seasons.length(); k++) {
            JSONObject s = seasons.optJSONObject(k);
            if (s != null) names.put(s.optInt("season_number"), s.optString("name"));
        }
        List<Episode> eps = new ArrayList<>();
        Object raw = root.opt("episodes");
        List<JSONArray> groups = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        if (raw instanceof JSONObject) {
            JSONObject o = (JSONObject) raw;
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONArray a = o.optJSONArray(k);
                if (a != null) {
                    groups.add(a);
                    keys.add(k);
                }
            }
        } else if (raw instanceof JSONArray) {
            JSONArray o = (JSONArray) raw;
            for (int k = 0; k < o.length(); k++) {
                JSONArray a = o.optJSONArray(k);
                if (a != null) {
                    groups.add(a);
                    keys.add(String.valueOf(k + 1));
                }
            }
        }
        String show = t != null ? t.name : i.optString("name");
        for (int g = 0; g < groups.size(); g++) {
            JSONArray a = groups.get(g);
            for (int k = 0; k < a.length(); k++) {
                JSONObject e = a.optJSONObject(k);
                if (e == null || e.optString("id").isEmpty()) continue;
                JSONObject ei = e.optJSONObject("info");
                if (ei == null) ei = new JSONObject();
                int season = e.has("season") ? e.optInt("season", Library.number(keys.get(g))) : Library.number(keys.get(g));
                int number = e.optInt("episode_num", k + 1);
                int secs = Library.number(ei.optString("duration_secs"));
                int mins = secs > 0 ? Math.round(secs / 60f) : minutes(ei.optString("duration"));
                eps.add(new Episode(e.optString("id"), season, number, episodeTitle(e.optString("title"), show, season, number),
                        ei.optString("plot"), first(ei.optString("movie_image"), ei.optString("cover_big")), mins,
                        aired(first(ei.optString("releasedate"), ei.optString("release_date"), ei.optString("air_date"))),
                        e.optString("container_extension"), "", "", ""));
            }
        }
        eps.sort((a, b) -> a.season != b.season ? Integer.compare(a.season, b.season) : Integer.compare(a.number, b.number));
        int run = Library.number(i.optString("episode_run_time"));
        return new Details(first(i.optString("plot"), t == null ? "" : t.plot), i.optString("genre"), i.optString("cast"),
                i.optString("director"), first(i.optString("releaseDate"), i.optString("release_date"), t == null ? "" : t.year),
                first(backdrop(i), t == null ? "" : t.backdrop), first(i.optString("cover"), t == null ? "" : t.poster),
                i.optString("youtube_trailer"), first(i.optString("rating"), t == null ? "" : t.rating), "", run,
                seasons(eps, names));
    }

    private static List<Season> seasons(List<Episode> eps, Map<Integer, String> names) {
        List<Season> out = new ArrayList<>();
        if (eps == null) return out;
        Map<Integer, List<Episode>> by = new LinkedHashMap<>();
        for (Episode e : eps) {
            List<Episode> l = by.get(e.season);
            if (l == null) by.put(e.season, l = new ArrayList<>());
            l.add(e);
        }
        for (Map.Entry<Integer, List<Episode>> e : by.entrySet()) {
            String n = names == null ? null : names.get(e.getKey());
            out.add(new Season(e.getKey(), n == null || n.isEmpty() || n.matches("(?i)season\\s*\\d+") ? "Series " + e.getKey() : n, e.getValue()));
        }
        return out;
    }

    private static String backdrop(JSONObject i) {
        JSONArray a = i.optJSONArray("backdrop_path");
        if (a != null) {
            for (int k = 0; k < a.length(); k++) if (!a.optString(k).isEmpty()) return a.optString(k);
            return "";
        }
        String b = i.optString("backdrop_path");
        return b.startsWith("[") ? "" : b;
    }

    /** "Show - S01E02 - Pilot" as "Pilot"; a title that is only its number as "". */
    static String episodeTitle(String raw, String show, int season, int number) {
        String t = raw == null ? "" : raw.trim();
        Matcher m = EPISODE.matcher(t);
        if (m.matches()) t = m.group(4).trim();
        else {
            m = EPISODE_X.matcher(t);
            if (m.matches()) t = m.group(4).trim();
        }
        if (show != null && !show.isEmpty() && t.toLowerCase(Locale.ROOT).startsWith(show.toLowerCase(Locale.ROOT))) {
            t = t.substring(show.length()).replaceFirst("^[\\s:._-]+", "");
        }
        t = t.replaceFirst("^[\\s:._-]+", "");
        if (t.matches("(?i)(episode|ep\\.?)\\s*\\d+")) return "";
        return t;
    }

    // ---- where it plays: made now, from the playlist's login ---------------------------------------------------------

    /** Where a film plays (its copies in other playlists as backups), or null when its playlist has gone. */
    public Play play(Title t) {
        Play p = playOne(t, null);
        if (p == null) return null;
        for (Title c : t.copies) {
            Play b = playOne(c, null);
            if (b != null) p.backups.add(b);
        }
        return p;
    }

    /** Where a series' episode plays, or null when its playlist has gone. */
    public Play play(Title series, Episode e) {
        return playOne(series, e);
    }

    private Play playOne(Title t, Episode e) {
        if (t == null) return null;
        Sources.Entry src = Sources.get(app, t.source);
        if (src == null) return null;
        Source s = src.source;
        if (e != null) {
            if (!e.url.isEmpty()) return new Play(e.url, e.userAgent, e.referrer);
            if (!Source.XTREAM.equals(s.type)) return null;
            return new Play(Xtream.episode(Xtream.base(s.server), s.user, s.pass, e.id, e.ext), null, null);
        }
        if (!t.url.isEmpty()) return new Play(t.url, t.userAgent, t.referrer);
        if (!Source.XTREAM.equals(s.type)) return null;
        return new Play(Xtream.movie(Xtream.base(s.server), s.user, s.pass, t.id, t.ext), null, null);
    }

    // ---- names --------------------------------------------------------------------------------------------------

    private static final Pattern LEAD = Pattern.compile("^\\s*[\\[(|]?\\s*([A-Za-z0-9+]{1,6})\\s*[\\])|:\\-]+\\s*");
    private static final Pattern YEAR = Pattern.compile("(?:^|[\\s(\\[._-])((?:19|20)\\d{2})(?:$|[\\s)\\]._-])");
    /** A picture or sound tag at a name's end, bracketed or not ("4K", "[UHD]", "(DV)", "Atmos"). */
    private static final Pattern TRAIL = Pattern.compile("(?i)(?:[\\s._|-]+[\\[(]?|[\\[(])\\s*(?:4k|uhd|2160p|1080p|1080i|720p|fhd|hd|sd|"
            + "hdr10\\+?|hdr|dv|dovi|dolby\\s*vision|dolby\\s*atmos|atmos|hevc|x26[45]|h26[45]|multi-?sub|multi)\\s*[\\])]?\\s*$");
    /** The tags providers put before a title: languages and countries, services, the picture and the like. */
    private static final Set<String> TAGS = new HashSet<>(java.util.Arrays.asList(
            "en", "eng", "english", "uk", "gb", "us", "usa", "multi", "multisub", "sub", "subs", "dub", "dubbed", "vf", "vost",
            "4k", "uhd", "fhd", "hd", "sd", "hdr", "hdr10", "dv", "hevc", "x264", "x265", "h264", "h265", "1080p", "720p", "2160p",
            "web", "webdl", "webrip", "bluray", "netflix", "nf", "amzn", "dsnp", "atvp", "max", "hmax", "vod", "movie", "movies",
            "film", "series", "tv", "top", "new"));
    /** The tags that may come anywhere in a name, as no title's own word does: the picture's and the copy's. */
    private static final Set<String> PICTURE = new HashSet<>(java.util.Arrays.asList(
            "4k", "uhd", "fhd", "hd", "sd", "hdr", "hdr10", "hevc", "x264", "x265", "h264", "h265", "1080p", "720p", "2160p", "webdl",
            "webrip", "bluray", "multi", "multisub"));

    /** The providers' tags before a name taken off ("EN - ", "|UK| ", "[4K] "), and the year after it ("(1999)"). */
    static String displayName(String raw) {
        String n = raw == null ? "" : raw.trim();
        Matcher lead = LEAD.matcher(n);
        while (lead.lookingAt() && lead.end() < n.length()) {
            String tag = lead.group(1);
            boolean known = TAGS.contains(tag.toLowerCase(Locale.ROOT)) || (tag.length() == 2 && tag.equals(tag.toUpperCase(Locale.ROOT))
                    && !tag.equals(tag.toLowerCase(Locale.ROOT)));
            if (!known) break;
            n = n.substring(lead.end());
            lead = LEAD.matcher(n);
        }
        // The picture's and sound's tags after it ("(2024) 4K", "[UHD] [DV]", "1080p Atmos"), then the year.
        for (int i = 0; i < 6; i++) {
            Matcher tail = TRAIL.matcher(n);
            if (!tail.find() || tail.start() == 0) break;
            n = n.substring(0, tail.start()).trim();
        }
        String y = n.replaceAll("\\s*[\\[(](?:19|20)\\d{2}[\\])]\\s*$", "").trim();
        if (!y.isEmpty()) n = y;
        n = n.replaceAll("\\s+[-|]\\s*$", "").trim();
        return n.isEmpty() ? (raw == null ? "" : raw.trim()) : n;
    }

    /**
     * A title made comparable (pure, for the tests): lower case, accents dropped, "&" as and, the year and the providers'
     * tags set aside, punctuation gone: "EN - The Matrix (1999) 4K" and "The Matrix" alike.
     */
    static String key(String name) {
        String n = Normalizer.normalize(displayName(name), Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
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

    /** A name as the search reads it: lower case, accents and punctuation gone. */
    static String searchable(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return n.replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** A year in a name or a date ("The Matrix (1999)", "1999-03-31"), or "". */
    static String year(String name) {
        Matcher m = YEAR.matcher(name == null ? "" : name);
        String last = "";
        while (m.find()) last = m.group(1);
        return last;
    }

    /** "7.4", or "" for none (providers send 0 for unknown). */
    static String rating(String r) {
        if (r == null) return "";
        String t = r.trim();
        try {
            double v = Double.parseDouble(t);
            if (v <= 0 || v > 10) return "";
            return String.format(Locale.UK, "%.1f", v);
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /** "01:57:00" or "117 min" in minutes. */
    static int minutes(String d) {
        if (d == null || d.trim().isEmpty()) return 0;
        String t = d.trim();
        if (t.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
            String[] p = t.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]) + (Integer.parseInt(p[2]) >= 30 ? 1 : 0);
        }
        Matcher m = Pattern.compile("(\\d+)").matcher(t);
        return m.find() ? Library.number(m.group(1)) : 0;
    }

    private static String first(String... s) {
        for (String x : s) if (x != null && !x.trim().isEmpty() && !"null".equals(x.trim())) return x.trim();
        return "";
    }

    private static String clean(String s) {
        return s == null || "null".equals(s.trim()) ? "" : s.trim();
    }

    // ---- listeners ------------------------------------------------------------------------------------------

    public void addListener(Runnable r) { listeners.add(r); }

    public void removeListener(Runnable r) { listeners.remove(r); }

    private void changed() {
        for (Runnable r : listeners) Exec.main(r);
    }

    /** Previews only: a catalogue given straight in, as if read from a playlist. */
    public void useForPreview(List<String> rows, Map<String, Details> pages) {
        // Kept as the first playlist's file, as if just read, so nothing is read again from a provider.
        File f = file(app, "p0");
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            w.write("v1\txtream\t" + System.currentTimeMillis() + "\n");
            for (String r : rows) w.write(r + "\n");
        } catch (IOException ignored) {
            // nothing to show
        }
        model = build(Sources.inUse(app));
        synchronized (details) {
            details.clear();
            details.putAll(pages);
        }
        read = true;
        changed();
    }

    /** Previews only: a page's details made from plain values. */
    public static Details previewDetails(String plot, String genre, String cast, String director, String released, String backdrop,
                                         String rating, String age, int minutes, String[][] episodes) {
        return previewDetails(plot, genre, cast, director, released, backdrop, rating, age, minutes, episodes, "");
    }

    /** Previews only: the same with a trailer; an episode's eighth value is when it was first shown. */
    public static Details previewDetails(String plot, String genre, String cast, String director, String released, String backdrop,
                                         String rating, String age, int minutes, String[][] episodes, String trailer) {
        List<Episode> eps = new ArrayList<>();
        if (episodes != null) {
            for (String[] e : episodes) {
                eps.add(new Episode(e[0], Library.number(e[1]), Library.number(e[2]), e[3], e[4], e[5], Library.number(e[6]),
                        e.length > 7 ? aired(e[7]) : "", "mp4", "", "", ""));
            }
        }
        return new Details(plot, genre, cast, director, released, backdrop, "", trailer, rating, age, minutes,
                episodes == null ? null : seasons(eps, null));
    }

    private static final Pattern DAY = Pattern.compile("((?:19|20)\\d{2})-(\\d{2})-(\\d{2})");

    /** Pure: a provider's date ("2022-10-09", "2022-10-09 21:00:00") as its day ("2022-10-09"), else "". */
    static String aired(String s) {
        if (s == null) return "";
        Matcher m = DAY.matcher(s);
        return m.find() ? m.group(1) + "-" + m.group(2) + "-" + m.group(3) : "";
    }
}
