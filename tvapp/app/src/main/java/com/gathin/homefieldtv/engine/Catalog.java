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
    /** The category the family's own TorBox titles that no playlist has are listed under (its row, its grid). */
    public static final String TORBOX = "Your TorBox";
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
        /** The HDR and sound its name (or its category) says, as Formats words them ("HDR10", "Dolby Atmos"; "" for none). */
        public final String hdr, sound;
        final String source, ext, url, userAgent, referrer;
        /**
         * The same title in the family's other playlists, played when this one doesn't start: put in place whole by each
         * merge (never added to while a screen may read it), one shared empty list for the many with none.
         */
        volatile List<Title> copies = Collections.emptyList();
        /** Its row in the catalogue's file, hashed: the same row read again keeps this very title (Catalog.read's reuse). */
        int row;

        Title(String source, boolean series, String id, String rawName, String category, String poster, String backdrop,
              String rating, String plot, long added, String ext, String url, String userAgent, String referrer, String year) {
            // What tens of thousands of titles share (the playlist, a year, a rating, a file's kind, a category, a user
            // agent): one copy each; one empty string for all that say nothing (the memory diet).
            this.source = Channel.shared(source);
            this.series = series;
            // The provider's id lives in the key alone (id()): one string a title, not two (the memory diet, 200,000 titles).
            this.key = this.source + (series ? "|s|" : "|m|") + Channel.own(id);
            this.name = displayName(rawName);
            String y = year == null || year.isEmpty() ? year(rawName) : year(year);
            this.year = Channel.shared(y);
            this.category = Channel.shared(category);
            this.poster = poster == null ? "" : Channel.own(poster.trim());
            this.backdrop = backdrop == null ? "" : Channel.own(backdrop.trim());
            this.rating = Channel.shared(rating(rating));
            this.plot = plot == null ? "" : Channel.own(cut(plot.trim(), PLOT_MAX));
            this.added = added;
            this.ext = Channel.shared(ext);
            this.url = Channel.own(url);
            this.userAgent = Channel.shared(userAgent);
            this.referrer = Channel.shared(referrer);
            this.picture = Math.max(pictureOf(rawName), pictureOf(category) == PICTURE_4K ? PICTURE_4K : 0);
            this.dolbyVision = dolbyVisionOf(rawName) || dolbyVisionOf(category);
            this.atmos = atmosOf(rawName) || atmosOf(category);
            // Read only where its name or category may say it (most say neither): a playlist loads many thousands.
            String c = this.category;
            String h = dolbyVision ? "Dolby Vision" : "", s = atmos ? "Dolby Atmos" : "";
            if (h.isEmpty() && (Formats.mayHdr(rawName) || Formats.mayHdr(c))) h = Formats.hdr(rawName + " " + c);
            if (s.isEmpty() && (Formats.maySound(rawName) || Formats.maySound(c))) s = Formats.audio(rawName + " " + c);
            this.hdr = Channel.shared(h);
            this.sound = Channel.shared(s);
        }

        /** The playlist it is from. */
        public String playlist() {
            return source;
        }

        /** The provider's id for it (its key's last part). */
        String id() {
            return key.substring(source.length() + 3);
        }

        /** One of the family's own TorBox titles that no playlist has (Catalog.TORBOX). */
        public boolean torbox() {
            return TorBox.SOURCE.equals(source);
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

    /** A series' synopsis from the list is kept to this many characters (its page reads the whole of it). */
    static final int PLOT_MAX = 420;

    /** Pure: s cut to max characters at a word, with an ellipsis. */
    static String cut(String s, int max) {
        if (s.length() <= max) return s;
        int sp = s.lastIndexOf(' ', max - 1);
        if (sp < max / 2) sp = max - 1;
        return s.substring(0, sp).trim() + "…";
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

    /** Pure: how a picture is named for picking a copy ("4K", "1080p", "720p", "SD"; "" for none). */
    public static String pictureName(int picture) {
        switch (picture) {
            case PICTURE_4K: return "4K";
            case PICTURE_1080: return "1080p";
            case PICTURE_720: return "720p";
            case PICTURE_SD: return "SD";
            default: return "";
        }
    }

    /**
     * Pure: a file's kind from its name, its address or the provider's extension for it ("MKV", "MP4"), or "" for a
     * stream or none.
     */
    public static String kindOf(String s) {
        if (s == null) return "";
        String u = s.trim().toLowerCase(Locale.ROOT);
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        int dot = u.lastIndexOf('.'), slash = u.lastIndexOf('/');
        String ext = dot < 0 ? (slash < 0 ? u : "") : dot < slash ? "" : u.substring(dot + 1);
        switch (ext) {
            case "mkv": case "mp4": case "avi": case "ts": case "m4v": case "mov": case "webm": return ext.toUpperCase(Locale.ROOT);
            default: return "";
        }
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
        /**
         * Its TMDB id and IMDb id ("tt0111161") as the provider's page (or TMDB) gives them, 0 and "" when not given: what
         * the player's subtitles are looked for by on OpenSubtitles (a series' for its episodes).
         */
        public int tmdb;
        public String imdb = "";

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
        /** Only in the family's TorBox (no playlist has it): played from there alone, marked with its cloud. */
        public boolean torbox;

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
        /** For the source picker: TorBox's copy (else a playlist's), and the playlist's name ("" for TorBox's). */
        public boolean torbox;
        public String playlist = "";
        /** The file's (or the playlist entry's) own name, and its size in bytes (0 when not known). */
        public String name = "";
        public long size;
        /** The picture its name says (PICTURE_4K … PICTURE_SD, 0 when it says none), its file's kind ("MKV", "MP4", ""). */
        public int picture;
        public String kind = "";
        /** The HDR and sound its name says, as Formats words them ("Dolby Vision", "HDR10"; "Dolby Atmos", "DTS-HD 7.1"; ""). */
        public String hdr = "", sound = "";
        /** A TorBox file's download's name (a series pack's: where its release says what it is), "" otherwise. */
        public String item = "";

        Play(String url, String userAgent, String referrer) {
            this.url = url;
            this.userAgent = userAgent == null ? "" : userAgent;
            this.referrer = referrer == null ? "" : referrer;
        }
    }

    /** Previews and tests: a copy as if found, by its address and its playlist's name (TorBox's for a "torbox:" one). */
    public static Play previewPlay(String url, String playlist) {
        Play p = new Play(url, null, null);
        p.torbox = TorBox.ours(url);
        p.playlist = p.torbox || playlist == null ? "" : playlist;
        return p;
    }

    public interface DetailsCallback {
        /** On the main thread: the details, or null when the provider gave none. */
        void got(Details d);
    }

    /** A series' episodes by its key, from memory or (an M3U's, many thousands) read from its file when asked. */
    interface Episodes {
        /** In order (season, then number); null when it has none. */
        List<Episode> get(String key);
    }

    static final class Model {
        final List<Title> films, series;
        final List<Category> filmCategories, seriesCategories;
        /** A title by its key, a copy's key giving the title shown (TitleIndex.Keys: a few plain arrays). */
        final TitleIndex.Keys byKey;
        final Episodes episodes;
        /** The names as search reads them, all in one string (TitleIndex.Names). */
        final TitleIndex.Names filmNames, seriesNames;
        /** The titles the provider dated, newest first: sorted once as the catalogue is read, never as a screen opens. */
        final List<Title> datedFilms, datedSeries;

        /** Tests and previews: from a map of keys and one of episodes. */
        Model(List<Title> films, List<Title> series, List<Category> fc, List<Category> sc, Map<String, Title> byKey,
              Map<String, List<Episode>> episodes) {
            this(films, series, fc, sc, TitleIndex.Keys.of(byKey), episodes::get, TitleIndex.Names.of(films), TitleIndex.Names.of(series),
                    dated(films), dated(series));
        }

        Model(List<Title> films, List<Title> series, List<Category> fc, List<Category> sc, TitleIndex.Keys byKey, Episodes episodes,
              TitleIndex.Names filmNames, TitleIndex.Names seriesNames, List<Title> datedFilms, List<Title> datedSeries) {
            this.films = Collections.unmodifiableList(films);
            this.series = Collections.unmodifiableList(series);
            this.filmCategories = Collections.unmodifiableList(fc);
            this.seriesCategories = Collections.unmodifiableList(sc);
            this.byKey = byKey;
            this.episodes = episodes;
            this.filmNames = filmNames;
            this.seriesNames = seriesNames;
            this.datedFilms = datedFilms;
            this.datedSeries = datedSeries;
        }

        static List<Title> dated(List<Title> all) {
            List<Title> out = new ArrayList<>();
            for (Title t : all) if (t.added > 0) out.add(t);
            out.sort((a, b) -> Long.compare(b.added, a.added));
            return Collections.unmodifiableList(out);
        }

        /** Pure: two lists each newest first made one, newest first (the family's own TorBox titles among the playlists'). */
        static List<Title> newestOf(List<Title> a, List<Title> b) {
            if (b.isEmpty()) return a;
            List<Title> out = new ArrayList<>(a.size() + b.size());
            int i = 0, j = 0;
            while (i < a.size() || j < b.size()) {
                if (j >= b.size() || i < a.size() && a.get(i).added >= b.get(j).added) out.add(a.get(i++));
                else out.add(b.get(j++));
            }
            return Collections.unmodifiableList(out);
        }

        static Model empty() {
            return new Model(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new HashMap<>(), new HashMap<>());
        }
    }

    private final Context app;
    /** What the screens read: the playlists' (base) with the family's own TorBox titles folded in. */
    private volatile Model model = Model.empty();
    private volatile Model base = Model.empty();
    private volatile List<Title> ownFilms = Collections.emptyList(), ownSeries = Collections.emptyList();
    private final AtomicBoolean owning = new AtomicBoolean();
    /** When the family's own TorBox titles were last read (ms); read again as the app comes back after OWN_MS. */
    private volatile long ownAt;
    private volatile int ownTries;
    static final long OWN_MS = 15 * 60_000L;
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
        // The playlists' newest: the family's own TorBox titles have a row of their own.
        Model m = base;
        return newest(series ? m.series : m.films, series ? m.datedSeries : m.datedFilms, max);
    }

    static List<Title> newest(List<Title> all, int max) {
        List<Title> dated = new ArrayList<>();
        for (Title t : all) if (t.added > 0) dated.add(t);
        dated.sort((a, b) -> Long.compare(b.added, a.added));
        return newest(all, dated, max);
    }

    /** Pure: the newest max titles, dated being all's dated ones newest first: by date when enough are dated, else all's last. */
    static List<Title> newest(List<Title> all, List<Title> dated, int max) {
        if (dated.size() >= Math.min(max, all.size()) / 2 && !dated.isEmpty()) {
            return new ArrayList<>(dated.subList(0, Math.min(max, dated.size())));
        }
        List<Title> out = new ArrayList<>(Math.max(0, Math.min(max, all.size())));
        for (int i = all.size() - 1; i >= 0 && out.size() < max; i--) out.add(all.get(i));
        return out;
    }

    /** Titles whose name has the words typed: those starting with them first. Goes through every title: off the main thread. */
    public List<Title> search(String query, boolean series, int max) {
        String q = searchable(query);
        List<Title> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        Model m = model;
        List<Title> all = series ? m.series : m.films;
        TitleIndex.Names names = series ? m.seriesNames : m.filmNames;
        List<Title> later = new ArrayList<>();
        String the = "the " + q, word = " " + q;
        boolean inside = q.length() > 2;
        int count = Math.min(names.size(), all.size());
        for (int i = 0; i < count && out.size() < max; i++) {
            if (names.startsWith(i, q) || names.startsWith(i, the)) out.add(all.get(i));
            else if (later.size() < max && (names.contains(i, word) || (inside && names.contains(i, q)))) later.add(all.get(i));
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
            // The family's TorBox read again now and then as the app comes back: new downloads into the rows.
            else if (System.currentTimeMillis() - ownAt > OWN_MS) ownTitles();
            return;
        }
        Exec.io(() -> {
            synchronized (this) {
                if (!read) {
                    setBase(build(Sources.inUse(app)));
                    read = true;
                }
            }
            changed();
            ownTitles();
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
                setBase(build(in));
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
            ownTitles();
        });
    }

    /** Everything kept removed (Settings' Remove everything). */
    public void clear() {
        File[] all = dir(app).listFiles();
        if (all != null) for (File f : all) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        ownFilms = Collections.emptyList();
        ownSeries = Collections.emptyList();
        setBase(Model.empty());
        synchronized (details) {
            details.clear();
        }
        status = "";
        changed();
    }

    // ---- the family's own TorBox titles, folded in -----------------------------------------------------------------

    /** The playlists' titles made anew: the family's own TorBox titles that no playlist has folded in again. */
    private void setBase(Model m) {
        base = m;
        model = withOwn(m, ownFilms, ownSeries);
    }

    /**
     * Pure: the playlists' titles with the family's own TorBox ones (those no playlist has): in the lists of all films
     * and series, by their keys, and as the first category, Catalog.TORBOX.
     */
    static Model withOwn(Model m, List<Title> films, List<Title> series) {
        if (films.isEmpty() && series.isEmpty()) return m;
        List<Title> f = new ArrayList<>(m.films), s = new ArrayList<>(m.series);
        f.addAll(films);
        s.addAll(series);
        List<Category> fc = new ArrayList<>(), sc = new ArrayList<>();
        if (!films.isEmpty()) fc.add(new Category(TORBOX, new ArrayList<>(films)));
        if (!series.isEmpty()) sc.add(new Category(TORBOX, new ArrayList<>(series)));
        fc.addAll(m.filmCategories);
        sc.addAll(m.seriesCategories);
        // The playlists' look-ups kept as they are, the own titles over them (never a copy of 200,000 keys every 15 minutes).
        Map<String, Title> own = new HashMap<>();
        for (Title t : films) own.put(t.key, t);
        for (Title t : series) own.put(t.key, t);
        return new Model(f, s, fc, sc, TitleIndex.Keys.over(m.byKey, own), m.episodes, m.filmNames.plus(films), m.seriesNames.plus(series),
                Model.newestOf(m.datedFilms, Model.dated(films)), Model.newestOf(m.datedSeries, Model.dated(series)));
    }

    /** The family's own TorBox titles that no playlist has (films or series), newest first; empty without TorBox. */
    public List<Title> own(boolean series) {
        return series ? ownSeries : ownFilms;
    }

    /**
     * The family's own TorBox titles read again, those no playlist has folded in (in the background): at once with what
     * TMDB said of them before, then again once TMDB has been asked about the new ones (the launcher giving its key).
     */
    public void ownTitles() {
        // Only once the playlists' titles are in: those they have aren't the family's own.
        if (!read || !owning.compareAndSet(false, true)) return;
        ownAt = System.currentTimeMillis();
        Exec.io(() -> {
            boolean told = false;
            try {
                for (int pass = 0; pass < 2; pass++) {
                    Model b = base;
                    List<List<Title>> own = TorBox.ownTitles(app, b.films, b.series, pass == 1);
                    // TorBox not answering: the titles shown stay as they were.
                    if (own == null) break;
                    boolean had = !ownFilms.isEmpty() || !ownSeries.isEmpty();
                    ownFilms = Collections.unmodifiableList(own.get(0));
                    ownSeries = Collections.unmodifiableList(own.get(1));
                    model = withOwn(base, ownFilms, ownSeries);
                    if (had || !ownFilms.isEmpty() || !ownSeries.isEmpty() || TorBox.on(app)) {
                        // Pages read before may lack TorBox's episodes (or have ones gone): read again when opened.
                        synchronized (details) {
                            details.clear();
                        }
                    }
                    ownTries++;
                    told = true;
                    changed();
                    if (pass == 0 && !TorBox.unasked(app, ownFilms) && !TorBox.unasked(app, ownSeries)) break;
                }
            } catch (RuntimeException e) {
                // the titles shown stay as they were; never the key or an address
                android.util.Log.w("HomefieldTv", "own titles not read: " + e.getClass().getSimpleName());
            } finally {
                if (!told) {
                    // A player waiting on one of them is told TorBox was tried.
                    ownTries++;
                    changed();
                }
                owning.set(false);
            }
        });
    }

    /** How many times the family's TorBox has been read for its own titles (or tried): a player waiting on one gives up. */
    public int ownTries() {
        return ownTries;
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
     * An M3U playlist's films and episodes as Library reads them, written to its catalogue file as they come, never all
     * held in memory (a provider's M3U can list a few hundred thousand): each entry ({name, address, group, user agent,
     * referrer, tvg-type, poster}) a film under its group or an episode under its series by its S01E02 (or 1x02), the
     * films and the episodes into files of their own beside it, then put together after the categories and the series.
     * save() puts the file in place; close() (always) clears what is left.
     */
    public static final class M3uSink implements M3u.VodSink, java.io.Closeable {
        private final File out, films, eps;
        private final Writer fw, ew;
        private final M3uRows rows;
        private boolean saved;

        M3uSink(Context c, String id) throws IOException {
            out = file(c, id);
            films = new File(out.getPath() + ".films");
            eps = new File(out.getPath() + ".eps");
            fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(films), StandardCharsets.UTF_8), 64 * 1024);
            ew = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(eps), StandardCharsets.UTF_8), 64 * 1024);
            rows = new M3uRows(fw, ew);
        }

        @Override public void add(String[] entry) throws IOException {
            rows.add(entry);
        }

        /** The catalogue file made from what came, and put in place of the old one. */
        public void save() throws IOException {
            fw.close();
            ew.close();
            synchronized (Catalog.class) {
                File tmp = new File(out.getPath() + ".part");
                try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8), 64 * 1024)) {
                    w.write("v1\tm3u\t" + System.currentTimeMillis() + "\n");
                    rows.categories(w);
                    copy(films, w);
                    rows.shows(w);
                    copy(eps, w);
                } catch (IOException e) {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    throw e;
                }
                if (!tmp.renameTo(out)) {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                }
            }
            saved = true;
        }

        @Override public void close() {
            try {
                fw.close();
                ew.close();
            } catch (IOException ignored) {
                // gone with the files
            }
            //noinspection ResultOfMethodCallIgnored
            films.delete();
            //noinspection ResultOfMethodCallIgnored
            eps.delete();
        }

        /** Whether save() went through. */
        public boolean saved() {
            return saved;
        }

        private static void copy(File from, Writer to) throws IOException {
            try (java.io.Reader r = new InputStreamReader(new FileInputStream(from), StandardCharsets.UTF_8)) {
                char[] buf = new char[32 * 1024];
                for (int n; (n = r.read(buf)) > 0; ) to.write(buf, 0, n);
            }
        }
    }

    /** Library: where an M3U playlist's films and episodes go as the playlist is read (M3u.parse). */
    public static M3uSink m3uSink(Context c, String id) throws IOException {
        return new M3uSink(c, id);
    }

    static final Pattern EPISODE = Pattern.compile("(?i)^(.*?)[\\s._-]*S(\\d{1,2})[\\s._-]*E(\\d{1,3})\\b[\\s._:-]*(.*)$");
    static final Pattern EPISODE_X = Pattern.compile("(?i)^(.*?)[\\s._-]+(\\d{1,2})x(\\d{1,3})\\b[\\s._:-]*(.*)$");

    /** An M3U's films and episodes in memory (the tests): as M3uSink writes them, into w. */
    static void writeM3u(Writer w, List<String[]> entries, long at) throws IOException {
        java.io.StringWriter films = new java.io.StringWriter(), eps = new java.io.StringWriter();
        M3uRows rows = new M3uRows(films, eps);
        for (String[] e : entries) rows.add(e);
        w.write("v1\tm3u\t" + at + "\n");
        rows.categories(w);
        w.write(films.toString());
        rows.shows(w);
        w.write(eps.toString());
    }

    /**
     * An M3U's entries made into the catalogue file's rows: films and episodes straight out to their writers; the
     * categories and the series (one each, a few thousand) kept to be written before them. A film's id is its name's
     * and group's hash, the n-th with the same one "-n": counted in plain int arrays, not a map of strings.
     */
    static final class M3uRows {
        private final Writer films, eps;
        private final Map<String, Boolean> fc = new LinkedHashMap<>(), sc = new LinkedHashMap<>();
        private final Map<String, String[]> shows = new LinkedHashMap<>();
        private int[] seenKey = new int[1024], seenCount = new int[1024];
        private boolean[] seenUsed = new boolean[1024];
        private int seenSize;

        M3uRows(Writer films, Writer eps) {
            this.films = films;
            this.eps = eps;
        }

        void add(String[] e) throws IOException {
            if (e == null || e.length < 2 || e[1] == null || e[1].isEmpty()) return;
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
            String lower = url.toLowerCase(Locale.ROOT);
            boolean series = matched && (lower.contains("/series/") || "series".equalsIgnoreCase(type) || !lower.contains("/movie/"));
            if (series && !m.group(1).trim().isEmpty()) {
                String show = m.group(1).trim().replaceAll("[\\s._-]+$", "");
                String sid = key(show);
                if (sid.isEmpty()) sid = show.toLowerCase(Locale.ROOT);
                sid = sid.replace(' ', '-');
                if (!shows.containsKey(sid)) {
                    shows.put(sid, new String[]{show, group, logo});
                    if (!group.isEmpty()) sc.put(group, Boolean.TRUE);
                }
                eps.write(line("e", sid, m.group(2), m.group(3), m.group(4).trim(), url, ua, ref, logo));
            } else {
                int hash = (key(name) + "|" + group).hashCode();
                String base = Integer.toHexString(hash);
                int n = seen(hash);
                String fid = n == 1 ? base : base + "-" + n;
                if (!group.isEmpty()) fc.put(group, Boolean.TRUE);
                films.write(line("m", fid, name, group, logo, "", "0", "", year(name), url, ua, ref));
            }
        }

        void categories(Writer w) throws IOException {
            for (String g : fc.keySet()) w.write(line("c", "m", g, g));
            for (String g : sc.keySet()) w.write(line("c", "s", g, g));
        }

        void shows(Writer w) throws IOException {
            for (Map.Entry<String, String[]> s : shows.entrySet()) {
                String[] v = s.getValue();
                w.write(line("s", s.getKey(), v[0], v[1], v[2], "", "0", year(v[0]), "", ""));
            }
        }

        /** How many times this hash has come now (1 the first time): an open table of ints. */
        private int seen(int hash) {
            if (seenSize * 2 >= seenKey.length) grow();
            int mask = seenKey.length - 1, i = mix(hash) & mask;
            while (seenUsed[i]) {
                if (seenKey[i] == hash) return ++seenCount[i];
                i = (i + 1) & mask;
            }
            seenUsed[i] = true;
            seenKey[i] = hash;
            seenCount[i] = 1;
            seenSize++;
            return 1;
        }

        private void grow() {
            int[] k = seenKey, c = seenCount;
            boolean[] u = seenUsed;
            seenKey = new int[k.length * 2];
            seenCount = new int[k.length * 2];
            seenUsed = new boolean[k.length * 2];
            int mask = seenKey.length - 1;
            for (int j = 0; j < k.length; j++) {
                if (!u[j]) continue;
                int i = mix(k[j]) & mask;
                while (seenUsed[i]) i = (i + 1) & mask;
                seenUsed[i] = true;
                seenKey[i] = k[j];
                seenCount[i] = c[j];
            }
        }

        private static int mix(int h) {
            h ^= h >>> 16;
            h *= 0x85EBCA6B;
            return h ^ (h >>> 13);
        }
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
        /** Its series' episodes in memory (a file read from text: the tests), by the series' id. */
        final Map<String, List<Episode>> episodes = new HashMap<>();
        /** Or, read from its file, where each series' episodes are in it ({from, to} bytes), read when its page opens. */
        final Map<String, long[]> spans = new HashMap<>();
        File file;

        Read(String source) {
            this.source = source;
        }
    }

    /** The tests: a catalogue file's text read back, its episodes in memory. */
    static Read read(BufferedReader r, String source) throws IOException {
        Read out = new Read(source);
        String head = r.readLine();
        if (head == null || !head.startsWith("v1\t")) return out;
        Map<String, String> pool = new HashMap<>();
        Map<String, Set<String>> ids = new HashMap<>();
        for (String line; (line = r.readLine()) != null; ) row(out, line, pool, null, ids, -1, -1);
        return out;
    }

    /**
     * A catalogue file read back: the titles made (the same row as last time keeps last time's very title: a refresh
     * holds one catalogue in memory, not two), an M3U's episodes left in the file with where each series' are.
     */
    static Read read(File f, String source, TitleIndex.Keys before) throws IOException {
        Read out = new Read(source);
        out.file = f;
        try (Rows r = new Rows(f)) {
            String head = r.next();
            if (head == null || !head.startsWith("v1\t")) return out;
            Map<String, String> pool = new HashMap<>();
            for (String line; (line = r.next()) != null; ) row(out, line, pool, before, null, r.start(), r.end());
        }
        return out;
    }

    /** One row of a catalogue file into out. ids: the episodes' own (in memory); else where they are (from, to). */
    private static void row(Read out, String line, Map<String, String> pool, TitleIndex.Keys before, Map<String, Set<String>> ids,
                            long from, long to) {
        int tab = line.indexOf('\t');
        if (tab != 1) return;
        char kind = line.charAt(0);
        if (kind == 'e' && ids == null) {
            // Only where this series' episodes are: they are read from the file when its page opens.
            int t2 = line.indexOf('\t', 2);
            if (t2 < 0) return;
            String sid = line.substring(2, t2);
            long[] span = out.spans.get(sid);
            if (span == null) out.spans.put(sid, new long[]{from, to});
            else {
                span[0] = Math.min(span[0], from);
                span[1] = Math.max(span[1], to);
            }
            return;
        }
        String[] c = line.split("\t", -1);
        switch (kind) {
            case 'c':
                if (c.length >= 4) (c[1].equals("s") ? out.seriesCats : out.filmCats).put(c[2], shared(pool, c[3]));
                break;
            case 'm':
                if (c.length >= 12) {
                    String cat = shared(pool, out.filmCats.containsKey(c[3]) ? out.filmCats.get(c[3]) : "");
                    int hash = line.hashCode() * 31 + cat.hashCode();
                    Title t = same(before, out.source + "|m|" + c[1], hash);
                    if (t == null) {
                        t = new Title(out.source, false, c[1], c[2], cat, c[4], "", c[5], "", parseLong(c[6]), c[7], c[9], c[10], c[11], c[8]);
                        t.row = hash;
                    }
                    out.films.add(t);
                }
                break;
            case 's':
                if (c.length >= 10) {
                    String cat = shared(pool, out.seriesCats.containsKey(c[3]) ? out.seriesCats.get(c[3]) : "");
                    int hash = line.hashCode() * 31 + cat.hashCode();
                    Title t = same(before, out.source + "|s|" + c[1], hash);
                    if (t == null) {
                        t = new Title(out.source, true, c[1], c[2], cat, c[4], c[9], c[5], c[8], parseLong(c[6]), "", "", "", "", c[7]);
                        t.row = hash;
                    }
                    out.series.add(t);
                }
                break;
            case 'e':
                if (c.length >= 9) {
                    List<Episode> l = out.episodes.get(c[1]);
                    Set<String> had = ids.get(c[1]);
                    if (l == null) {
                        out.episodes.put(c[1], l = new ArrayList<>());
                        ids.put(c[1], had = new HashSet<>());
                    }
                    l.add(episode(c, had));
                }
                break;
            default:
                break;
        }
    }

    /** An "e" row's episode, its id made unique among its series' (had). */
    private static Episode episode(String[] c, Set<String> had) {
        int season = Library.number(c[2]), number = Library.number(c[3]);
        String id = c[1] + ":" + season + ":" + number;
        for (int n = 2; !had.add(id); n++) id = c[1] + ":" + season + ":" + number + "#" + n;
        return new Episode(id, season, number, c[4], "", c[8], 0, "", c[5], c[6], c[7]);
    }

    /** The title read last time from the same row (its key, its row's hash), or null. */
    private static Title same(TitleIndex.Keys before, String key, int hash) {
        if (before == null) return null;
        Title t = before.get(key);
        return t != null && t.row == hash && t.key.equals(key) ? t : null;
    }

    /** Pure: a series' episodes read from its part of the file (sid's "e" rows between from and to), in order. */
    static List<Episode> episodesFrom(File f, String sid, long from, long to) {
        List<Episode> out = new ArrayList<>();
        Set<String> had = new HashSet<>();
        String want = "e\t" + sid + "\t";
        try (Rows r = new Rows(f, from, to)) {
            for (String line; (line = r.next()) != null; ) {
                if (!line.startsWith(want)) continue;
                String[] c = line.split("\t", -1);
                if (c.length >= 9) out.add(episode(c, had));
            }
        } catch (IOException e) {
            return null;
        }
        out.sort((a, b) -> a.season != b.season ? Integer.compare(a.season, b.season) : Integer.compare(a.number, b.number));
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
        // What is in memory now: a row read again unchanged keeps its title (no second catalogue while it is read).
        TitleIndex.Keys before = base.byKey;
        for (Sources.Entry e : in) {
            File f = file(app, e.id);
            if (!f.exists()) continue;
            try {
                reads.add(read(f, e.id, before));
            } catch (IOException | RuntimeException ignored) {
                // that playlist's titles left out until it is read again
            }
        }
        return merge(reads);
    }

    static Model merge(List<Read> reads) {
        int n = 0;
        for (Read r : reads) n += r.films.size() + r.series.size();
        List<Title> films = new ArrayList<>(), series = new ArrayList<>();
        TitleIndex.Table byKey = new TitleIndex.Table(n);
        Map<String, Title> first = new HashMap<>();
        java.util.IdentityHashMap<Title, List<Title>> copies = new java.util.IdentityHashMap<>();
        Map<String, List<Episode>> episodes = new HashMap<>();
        Map<String, Object[]> spans = new HashMap<>();
        LinkedHashMap<String, List<Title>> fc = new LinkedHashMap<>(), sc = new LinkedHashMap<>();
        for (Read r : reads) {
            for (String c : r.filmCats.values()) if (!fc.containsKey(c)) fc.put(c, new ArrayList<>());
            for (String c : r.seriesCats.values()) if (!sc.containsKey(c)) sc.put(c, new ArrayList<>());
            for (int pass = 0; pass < 2; pass++) {
                boolean isSeries = pass == 1;
                for (Title t : isSeries ? r.series : r.films) {
                    String k = (isSeries ? "s|" : "m|") + key(t.name) + "|" + t.year;
                    Title had = first.get(k);
                    if (had != null && !had.source.equals(t.source)) {
                        List<Title> l = copies.get(had);
                        if (l == null) copies.put(had, l = new ArrayList<>(2));
                        l.add(t);
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
            for (Map.Entry<String, long[]> e : r.spans.entrySet()) {
                spans.put(r.source + "|s|" + e.getKey(), new Object[]{r.file, e.getKey(), e.getValue()});
            }
        }
        // Each title's copies put in place whole (a title kept from last time has its list replaced, not added to).
        for (List<Title> l : java.util.Arrays.asList(films, series)) {
            for (Title t : l) {
                List<Title> c = copies.get(t);
                t.copies = c == null ? Collections.emptyList() : Collections.unmodifiableList(c);
            }
        }
        List<Category> fcs = new ArrayList<>(), scs = new ArrayList<>();
        for (Map.Entry<String, List<Title>> e : fc.entrySet()) if (!e.getValue().isEmpty()) fcs.add(new Category(e.getKey(), e.getValue()));
        for (Map.Entry<String, List<Title>> e : sc.entrySet()) if (!e.getValue().isEmpty()) scs.add(new Category(e.getKey(), e.getValue()));
        Episodes eps = key -> {
            List<Episode> l = episodes.get(key);
            if (l != null) return l;
            Object[] s = spans.get(key);
            if (s == null) return null;
            long[] span = (long[]) s[2];
            return episodesFrom((File) s[0], (String) s[1], span[0], span[1]);
        };
        return new Model(films, series, fcs, scs, byKey, eps, TitleIndex.Names.of(films), TitleIndex.Names.of(series),
                Model.dated(films), Model.dated(series));
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
                // One of the family's own TorBox titles: TMDB's page for it and its files' episodes.
                d = t.torbox() ? TorBox.details(app, t) : fetchDetails(t);
            } catch (IOException | JSONException | RuntimeException e) {
                // the page shows what the list gave; never the address
                android.util.Log.w("HomefieldTv", "details not read: " + e.getClass().getSimpleName());
            }
            // A playlist's series: its episodes that only the family's TorBox has, put in among the playlist's.
            if (t.series && !t.torbox()) d = TorBox.withOwnEpisodes(app, t, d);
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
        String json = Library.text(Xtream.api(base, s.user, s.pass, null) + "&action=" + action + Xtream.enc(t.id()));
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
        Details d = new Details(first(i.optString("plot"), i.optString("description")), i.optString("genre"),
                first(i.optString("cast"), i.optString("actors")), i.optString("director"),
                first(i.optString("releasedate"), i.optString("release_date"), t == null ? "" : t.year), backdrop(i),
                poster, i.optString("youtube_trailer"), first(i.optString("rating"), t == null ? "" : t.rating),
                first(i.optString("mpaa_rating"), i.optString("age")), mins, null);
        ids(d, i);
        return d;
    }

    /** Pure: the TMDB and IMDb ids a provider's page gives ("tmdb_id" or "tmdb", "imdb_id" or "imdb"), kept on d. */
    static void ids(Details d, JSONObject i) {
        d.tmdb = Library.number(first(i.optString("tmdb_id"), i.optString("tmdb")));
        String imdb = first(i.optString("imdb_id"), i.optString("imdb")).trim();
        d.imdb = imdb.matches("(tt)?\\d{5,10}") ? imdb : "";
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
        Details d = new Details(first(i.optString("plot"), t == null ? "" : t.plot), i.optString("genre"), i.optString("cast"),
                i.optString("director"), first(i.optString("releaseDate"), i.optString("release_date"), t == null ? "" : t.year),
                first(backdrop(i), t == null ? "" : t.backdrop), first(i.optString("cover"), t == null ? "" : t.poster),
                i.optString("youtube_trailer"), first(i.optString("rating"), t == null ? "" : t.rating), "", run,
                seasons(eps, names));
        ids(d, i);
        return d;
    }

    /** Pure: episodes put into their seasons (each "Series N"). */
    static List<Season> seasonsOf(List<Episode> eps) {
        return seasons(eps, null);
    }

    /** Pure: a page's details from the title alone (its story, year, pictures and rating), with the seasons given. */
    static Details plainDetails(Title t, List<Season> seasons) {
        return new Details(t.plot, "", "", "", t.year, t.backdrop, t.poster, "", t.rating, "", 0, seasons);
    }

    /** Pure: a page's details with its episodes as given: the seasons made again from them, named as they were. */
    static Details withEpisodes(Details d, Title t, List<Episode> all) {
        Map<Integer, String> names = new HashMap<>();
        if (d != null) for (Season s : d.seasons) names.put(s.number, s.name);
        List<Season> seasons = seasons(all, names);
        if (d == null) return plainDetails(t, seasons);
        Details out = new Details(d.plot, d.genre, d.cast, d.director, d.released, d.backdrop, d.poster, d.trailer, d.rating, d.age,
                d.minutes, seasons);
        out.tmdb = d.tmdb;
        out.imdb = d.imdb;
        return out;
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
        // An episode only the family's TorBox has: played from there alone (TorBox.plays).
        if (t == null || e != null && e.torbox) return null;
        Sources.Entry src = Sources.get(app, t.source);
        if (src == null) return null;
        Play p = address(src.source, t, e);
        if (p == null) return null;
        // What the source picker says of it: its playlist, its name's picture, its file's kind.
        p.playlist = src.name;
        p.name = t.name;
        p.picture = t.picture;
        p.hdr = t.hdr;
        p.sound = t.sound;
        p.kind = kindOf(e != null ? e.ext : t.ext);
        if (p.kind.isEmpty()) p.kind = kindOf(e != null ? e.url : t.url);
        return p;
    }

    private static Play address(Source s, Title t, Episode e) {
        if (e != null) {
            if (!e.url.isEmpty()) return new Play(e.url, e.userAgent, e.referrer);
            if (!Source.XTREAM.equals(s.type)) return null;
            return new Play(Xtream.episode(Xtream.base(s.server), s.user, s.pass, e.id, e.ext), null, null);
        }
        if (!t.url.isEmpty()) return new Play(t.url, t.userAgent, t.referrer);
        if (!Source.XTREAM.equals(s.type)) return null;
        return new Play(Xtream.movie(Xtream.base(s.server), s.user, s.pass, t.id(), t.ext), null, null);
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
    /** The title pages' details kept from before let go (a channel has been playing a while: Guard.watching). */
    public void forgetPages() {
        synchronized (details) {
            details.clear();
        }
    }

    /** Tests: the catalogue's files read again as the daily refresh reads them (TheFamilysSizeTest). */
    void rebuildForTests() {
        setBase(build(Sources.inUse(app)));
    }

    public void useForPreview(List<String> rows, Map<String, Details> pages) {
        // Kept as the first playlist's file, as if just read, so nothing is read again from a provider.
        File f = file(app, "p0");
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            w.write("v1\txtream\t" + System.currentTimeMillis() + "\n");
            for (String r : rows) w.write(r + "\n");
        } catch (IOException ignored) {
            // nothing to show
        }
        setBase(build(Sources.inUse(app)));
        synchronized (details) {
            details.clear();
            details.putAll(pages);
        }
        read = true;
        changed();
    }

    /** Previews only: the family's own TorBox titles given straight in, as if TorBox and TMDB had answered, with their pages. */
    void useOwnForPreview(List<Title> films, List<Title> series, Map<String, Details> pages) {
        ownFilms = Collections.unmodifiableList(new ArrayList<>(films));
        ownSeries = Collections.unmodifiableList(new ArrayList<>(series));
        model = withOwn(base, ownFilms, ownSeries);
        synchronized (details) {
            details.putAll(pages);
        }
        ownAt = System.currentTimeMillis();
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
