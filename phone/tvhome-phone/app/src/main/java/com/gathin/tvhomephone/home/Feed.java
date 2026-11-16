package com.gathin.tvhomephone.home;

import android.content.Context;

import com.gathin.tvhomephone.SyncStore;
import com.gathin.tvhomephone.live.Catalogue;
import com.gathin.tvhomephone.live.SourceQuality;
import com.gathin.tvhomephone.live.Sources;
import com.gathin.tvhomephone.live.VodHistory;
import com.gathin.tvhomephone.live.Watched;
import com.gathin.tvhomephone.watch.Lists;
import com.gathin.tvhomephone.watch.Logos;
import com.gathin.tvhomephone.watch.Ranks;
import com.gathin.tvhomephone.watch.Title;
import com.gathin.tvhomephone.watch.Tmdb;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Home's films and TV picks, from the family's own playlists only. What was watched on this phone (and loved, and is
 * part-way on the TVs) gives the taste (Taste: genres and people, from TMDB's details); TMDB's recommendations for
 * those titles, its trending and popular lists and its best of the genres liked are the titles to choose from; only
 * those a playlist has (Catalogue) are kept, none already watched or rated down. With too little watched yet: what's
 * popular in the playlists, and what's new in them. Without TMDB (no paired TV gave its key): what's new in the
 * playlists by name. Nothing from anywhere but the playlists in use. Off the main thread; the last picks are kept
 * on this phone so Home opens with them at once, and made again when what was watched changes or six hours on.
 */
final class Feed {
    static final long STALE_MS = 6 * 3600_000L;
    static final int ROW = 20;
    private static final long KEEP_MS = 3L * 24 * 3600_000L;
    private static final String FILE = "home_picks.json";
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "home-picks");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    /** A row of picks: its heading, its line under the heading, its titles. */
    static final class Row {
        String title = "", sub = "";
        final List<Pick> picks = new ArrayList<>();

        JSONObject json() throws JSONException {
            return new JSONObject().put("title", title).put("sub", sub).put("picks", Pick.json(picks));
        }

        static Row from(JSONObject o) {
            if (o == null) return null;
            Row r = new Row();
            r.title = o.optString("title");
            r.sub = o.optString("sub");
            r.picks.addAll(Pick.list(o.optJSONArray("picks")));
            return r;
        }
    }

    /** What Home shows of films and TV. */
    static final class Result {
        long at;
        String sig = "";
        /** No playlist yet (NO_PLAYLIST), or no TMDB (NO_TMDB): the card that says what to do; "" otherwise. */
        String note = "";
        final List<Pick> hero = new ArrayList<>();
        Row films, shows, fresh;
    }

    static final String NO_PLAYLIST = "playlist", NO_TMDB = "tmdb";
    private static Result memory;

    private Feed() {}

    // ---- kept --------------------------------------------------------------------------------------------------

    /** The last picks (this run's, else the copy on this phone), or null. Quick enough for the main thread's start. */
    static Result kept(Context c) {
        synchronized (Feed.class) {
            if (memory != null) return memory;
        }
        File f = new File(c.getFilesDir(), FILE);
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) break;
                n += r;
            }
            JSONObject o = new JSONObject(new String(b, 0, n, StandardCharsets.UTF_8));
            Result r = new Result();
            r.at = o.optLong("at");
            r.sig = o.optString("sig");
            r.note = o.optString("note");
            r.hero.addAll(Pick.list(o.optJSONArray("hero")));
            r.films = Row.from(o.optJSONObject("films"));
            r.shows = Row.from(o.optJSONObject("shows"));
            r.fresh = Row.from(o.optJSONObject("fresh"));
            synchronized (Feed.class) {
                if (memory == null) memory = r;
            }
            return r;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    private static void save(Context c, Result r) {
        try {
            JSONObject o = new JSONObject().put("at", r.at).put("sig", r.sig).put("note", r.note).put("hero", Pick.json(r.hero));
            if (r.films != null) o.put("films", r.films.json());
            if (r.shows != null) o.put("shows", r.shows.json());
            if (r.fresh != null) o.put("fresh", r.fresh.json());
            File tmp = new File(c.getFilesDir(), FILE + ".part");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(new File(c.getFilesDir(), FILE))) tmp.delete();
        } catch (IOException | JSONException ignored) {
            // memory only
        }
    }

    /** What the picks depend on: the playlists in use, TMDB, what was watched and rated. Changes make them again. */
    static String signature(Context c) {
        // "p2": the picks carry their posters' badges and the sources' picture (5.13); older ones are made again once.
        StringBuilder b = new StringBuilder("p3|");
        for (Sources.Entry e : Sources.inUse(c)) b.append(e.id).append(',');
        b.append(Tmdb.ready(c) ? "T" : "-").append('|');
        for (Watched.Entry e : Watched.list(c)) {
            if (!e.isVod() || e.vod == null) continue;
            b.append(e.vod.tmdb > 0 ? String.valueOf(e.vod.tmdb) : e.vod.name).append(e.finished ? '!' : '.');
        }
        b.append('|');
        for (JSONObject r : SyncStore.list(c, "rating")) b.append(r.optInt("id")).append(':').append(r.optInt("value")).append(',');
        return Integer.toHexString(b.toString().hashCode());
    }

    /** The picks, made again when stale or forced. Off the main thread. */
    static Result load(Context c, boolean force) {
        Result had = kept(c);
        String sig = signature(c);
        long now = System.currentTimeMillis();
        if (!force && had != null && sig.equals(had.sig) && now - had.at < STALE_MS) return had;
        Result r;
        try {
            r = build(c);
        } catch (RuntimeException e) {
            return had;
        }
        r.sig = sig;
        r.at = now;
        // A read that found nothing (away from the network) keeps the last picks.
        boolean nothing = r.note.isEmpty() && r.hero.isEmpty() && empty(r.films) && empty(r.shows) && empty(r.fresh);
        if (nothing && had != null) return had;
        synchronized (Feed.class) {
            memory = r;
        }
        save(c, r);
        return r;
    }

    private static boolean empty(Row r) {
        return r == null || r.picks.isEmpty();
    }

    // ---- making them -----------------------------------------------------------------------------------------

    private static Result build(Context c) {
        Result out = new Result();
        if (Sources.inUse(c).isEmpty()) {
            out.note = NO_PLAYLIST;
            return out;
        }
        Catalogue cat = Catalogue.of(c);
        if (!Tmdb.ready(c)) {
            out.note = NO_TMDB;
            out.films = byName(cat, false, "New films in your playlist");
            out.shows = byName(cat, true, "New TV in your playlist");
            mark(c, cat, null, out.films, out.shows);
            return out;
        }
        long now = System.currentTimeMillis();

        // What was watched: this phone's films and episodes, the titles loved, what's part-way on the TVs.
        Map<String, Taste.Seen> seen = new LinkedHashMap<>();
        Set<String> skip = new HashSet<>();
        int searches = 0;
        for (Watched.Entry e : Watched.list(c)) {
            if (!e.isVod() || e.vod == null) continue;
            VodHistory.Entry v = e.vod;
            String name = v.name.isEmpty() ? v.title : v.name;
            int id = v.tmdb;
            if (id <= 0 && searches < 6) {
                searches++;
                id = search(c, name, v.tv);
            }
            if (id > 0) add(seen, id, v.tv, name, Taste.weight(e.finished, v.progress(), false, e.at, now));
        }
        for (JSONObject r : SyncStore.list(c, "rating")) {
            int id = r.optInt("id"), value = r.optInt("value");
            boolean tv = r.optBoolean("tv");
            if (id <= 0) continue;
            if (value < 0) skip.add((tv ? "tv:" : "mv:") + id);
            else if (value > 0) add(seen, id, tv, r.optString("title"), Taste.weight(true, 1, value >= 2, r.optLong("at"), now));
        }
        for (Title t : Lists.resume(c)) {
            if (t.id > 0) add(seen, t.id, t.tv, t.title, Taste.weight(false, Math.max(0, t.progress), false, 0, now));
        }
        skip.addAll(seen.keySet());
        List<Taste.Seen> strongest = new ArrayList<>(seen.values());
        strongest.sort((a, b) -> Double.compare(b.weight, a.weight));
        if (strongest.size() > 12) strongest = new ArrayList<>(strongest.subList(0, 12));
        Map<String, JSONObject> details = details(c, keysOf(strongest));
        for (Taste.Seen s : strongest) {
            JSONObject d = details.get(s.key());
            if (d == null) continue;
            s.genres = genres(d);
            s.people = people(d, s.tv);
        }
        Taste taste = Taste.of(strongest);

        // The titles to choose from.
        Map<String, Taste.Candidate> cands = new LinkedHashMap<>();
        List<Callable<Object[]>> reads = new ArrayList<>();
        int because = 0;
        for (Taste.Seen s : strongest) {
            if (because++ >= 5) break;
            reads.add(() -> new Object[]{s.name, Tmdb.list(c, (s.tv ? "tv/" : "movie/") + s.id + "/recommendations", s.tv, 1), 0});
        }
        String[][] popular = {{"trending/movie/week", "0", "2"}, {"trending/tv/week", "1", "2"}, {"movie/popular?region=GB", "0", "1"},
                {"tv/popular", "1", "1"}, {"movie/top_rated?region=GB", "0", "1"}, {"tv/top_rated", "1", "1"}};
        for (String[] p : popular) {
            boolean tv = p[1].equals("1");
            int pages = Integer.parseInt(p[2]);
            int base = p[0].contains("top_rated") ? 100 : p[0].contains("popular") ? 40 : 0;
            reads.add(() -> new Object[]{null, Tmdb.list(c, p[0], tv, pages), base});
        }
        if (!taste.empty()) {
            for (boolean tv : new boolean[]{false, true}) {
                for (int g : taste.topGenres(2, genresOf(strongest, tv))) {
                    reads.add(() -> new Object[]{null, Tmdb.list(c, "discover/" + (tv ? "tv" : "movie")
                            + "?with_genres=" + g + "&sort_by=popularity.desc&vote_count.gte=150", tv, 2), 60});
                }
            }
        }
        for (Object[] got : all(reads)) {
            String from = (String) got[0];
            @SuppressWarnings("unchecked") List<Title> list = (List<Title>) got[1];
            int base = (Integer) got[2];
            for (int i = 0; i < list.size(); i++) {
                Title t = list.get(i);
                if (!cat.has(t.id, t.tv, t.title, null, t.year)) continue;
                Taste.Candidate k = cands.get((t.tv ? "tv:" : "mv:") + t.id);
                if (k == null) {
                    k = new Taste.Candidate(t.id, t.tv, t.title);
                    k.genres = t.genres;
                    k.vote = t.vote;
                    k.shown = t;
                    cands.put(k.key(), k);
                }
                if (from != null) k.because.add(from);
                else k.popularity = Math.min(k.popularity, base + i);
            }
        }

        // Ranked once, the best read in full (their people, logos and facts), ranked again.
        List<Taste.Candidate> films = taste.rank(cands.values(), false, skip, ROW + 4);
        List<Taste.Candidate> shows = taste.rank(cands.values(), true, skip, ROW + 4);
        List<String> want = new ArrayList<>();
        for (Taste.Candidate k : films) want.add(k.key());
        for (Taste.Candidate k : shows) want.add(k.key());
        Map<String, JSONObject> more = details(c, want);
        for (Taste.Candidate k : films) k.people = people(more.get(k.key()), false);
        for (Taste.Candidate k : shows) k.people = people(more.get(k.key()), true);
        films = taste.rank(films, false, skip, ROW);
        shows = taste.rank(shows, true, skip, ROW);

        boolean thin = taste.empty() || seen.size() < 3;
        out.films = row(films, more, taste, thin ? "Popular films in your playlist" : "Recommended films",
                thin ? "What everyone's watching, that your playlist has" : "Picked from what you watch", thin);
        out.shows = row(shows, more, taste, thin ? "Popular TV in your playlist" : "Recommended TV shows",
                thin ? "What everyone's watching, that your playlist has" : "Picked from what you watch", thin);
        if (thin) out.fresh = fresh(c, cat);
        mark(c, cat, Ranks.read(c), out.films, out.shows, out.fresh);

        // The hero: the best two films and two series, by turns, each with a backdrop.
        List<Pick> f = withBackdrop(out.films), s = withBackdrop(out.shows);
        for (int i = 0; i < 2; i++) {
            if (i < f.size()) out.hero.add(f.get(i));
            if (i < s.size()) out.hero.add(s.get(i));
        }
        List<Pick> n = withBackdrop(out.fresh);
        for (int i = 0; out.hero.size() < 3 && i < n.size(); i++) out.hero.add(n.get(i));
        return out;
    }

    /** Each pick's poster corners: where it stands today (none without places) and what the sources have of it. */
    private static void mark(Context c, Catalogue cat, Ranks.Places places, Row... rows) {
        LocalDate today = LocalDate.now();
        for (Row r : rows) {
            if (r == null) continue;
            for (Pick p : r.picks) {
                String badge = places == null ? null : Ranks.label(places, p.tv, p.id, p.date, today);
                p.badge = badge == null ? "" : badge;
                p.quality = SourceQuality.of(c, cat, p.id, p.tv, p.title, p.year);
            }
        }
    }

    private static void add(Map<String, Taste.Seen> seen, int id, boolean tv, String name, double weight) {
        String k = (tv ? "tv:" : "mv:") + id;
        Taste.Seen s = seen.get(k);
        if (s == null) seen.put(k, new Taste.Seen(id, tv, name, weight));
        else s.weight += weight;
    }

    private static List<String> keysOf(List<Taste.Seen> list) {
        List<String> out = new ArrayList<>();
        for (Taste.Seen s : list) out.add(s.key());
        return out;
    }

    private static Set<Integer> genresOf(List<Taste.Seen> list, boolean tv) {
        Set<Integer> out = new HashSet<>();
        for (Taste.Seen s : list) if (s.tv == tv) for (int g : s.genres) out.add(g);
        return out;
    }

    /** Several reads at once (four at a time); a read that fails is left out. */
    private static <T> List<T> all(List<Callable<T>> reads) {
        List<T> out = new ArrayList<>();
        try {
            for (Future<T> f : POOL.invokeAll(reads, 40, TimeUnit.SECONDS)) {
                try {
                    T t = f.get();
                    if (t != null) out.add(t);
                } catch (Exception ignored) {
                    // that one left out
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return out;
    }

    /** Titles' details ("tv:1399" → JSON), read four at a time, the copies kept three days used. */
    private static Map<String, JSONObject> details(Context c, List<String> keys) {
        List<Callable<Object[]>> reads = new ArrayList<>();
        for (String k : keys) {
            boolean tv = k.startsWith("tv:");
            int id = Integer.parseInt(k.substring(3));
            reads.add(() -> new Object[]{k, Tmdb.detailsKept(c, id, tv)});
        }
        Map<String, JSONObject> out = new HashMap<>();
        for (Object[] r : all(reads)) out.put((String) r[0], (JSONObject) r[1]);
        return out;
    }

    /** TMDB's id for a name from a playlist (its first result), or 0. */
    private static int search(Context c, String name, boolean tv) {
        String q = clean(name);
        if (q.isEmpty()) return 0;
        try {
            JSONObject j = Tmdb.getKept(c, "search/" + (tv ? "tv" : "movie") + "?query=" + URLEncoder.encode(q, "UTF-8"), KEEP_MS);
            JSONArray r = j.optJSONArray("results");
            JSONObject first = r == null ? null : r.optJSONObject(0);
            return first == null ? 0 : first.optInt("id");
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    /** A playlist's name as a search: providers' tags, the year and the picture's words out ("EN - Dune (2021) 4K" → "Dune"). Pure. */
    static String clean(String name) {
        if (name == null) return "";
        String n = name.replaceAll("^\\s*[\\[(|]?\\s*[A-Za-z0-9+]{1,6}\\s*[\\])|:\\-]+\\s+", "");
        n = n.replaceAll("[\\[(]?(?:19|20)\\d{2}[\\])]?", " ");
        n = n.replaceAll("(?i)\\b(4k|uhd|fhd|hd|sd|hdr|hdr10|hevc|x264|x265|1080p|720p|2160p|multi|multisub)\\b", " ");
        return n.replaceAll("\\s+", " ").trim();
    }

    static int[] genres(JSONObject d) {
        JSONArray g = d == null ? null : d.optJSONArray("genres");
        if (g == null) return new int[0];
        int[] out = new int[g.length()];
        for (int i = 0; i < g.length(); i++) out[i] = g.optJSONObject(i) == null ? 0 : g.optJSONObject(i).optInt("id");
        return out;
    }

    /** A title's people from its details: the six leading cast, the director (films) or creators (series). Pure. */
    static int[] people(JSONObject d, boolean tv) {
        if (d == null) return new int[0];
        List<Integer> out = new ArrayList<>();
        JSONObject credits = d.optJSONObject("credits");
        JSONArray cast = credits == null ? null : credits.optJSONArray("cast");
        for (int i = 0; cast != null && i < cast.length() && i < 6; i++) {
            JSONObject p = cast.optJSONObject(i);
            if (p != null && p.optInt("id") > 0) out.add(p.optInt("id"));
        }
        JSONArray crew = credits == null ? null : credits.optJSONArray("crew");
        for (int i = 0; crew != null && i < crew.length(); i++) {
            JSONObject p = crew.optJSONObject(i);
            if (p != null && "Director".equals(p.optString("job")) && p.optInt("id") > 0) out.add(p.optInt("id"));
        }
        JSONArray made = tv ? d.optJSONArray("created_by") : null;
        for (int i = 0; made != null && i < made.length(); i++) {
            JSONObject p = made.optJSONObject(i);
            if (p != null && p.optInt("id") > 0) out.add(p.optInt("id"));
        }
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    /** The title's logo from its details (English, else one with no words' language), w500; null for none. Pure. */
    static String logo(JSONObject d) {
        JSONObject images = d == null ? null : d.optJSONObject("images");
        JSONArray logos = images == null ? null : images.optJSONArray("logos");
        String none = null;
        for (int i = 0; logos != null && i < logos.length(); i++) {
            JSONObject l = logos.optJSONObject(i);
            if (l == null || l.optString("file_path").isEmpty()) continue;
            String lang = l.optString("iso_639_1", "");
            if (lang.equals("en")) return Tmdb.image(l.optString("file_path"), "w500");
            if ((lang.isEmpty() || lang.equals("null")) && none == null) none = Tmdb.image(l.optString("file_path"), "w500");
        }
        return none;
    }

    /** A film's UK certificate from its details ("12A", "15"), "" for none. Pure. */
    static String cert(JSONObject d) {
        JSONObject rd = d == null ? null : d.optJSONObject("release_dates");
        JSONArray r = rd == null ? null : rd.optJSONArray("results");
        for (int i = 0; r != null && i < r.length(); i++) {
            JSONObject country = r.optJSONObject(i);
            if (country == null || !"GB".equals(country.optString("iso_3166_1"))) continue;
            JSONArray dates = country.optJSONArray("release_dates");
            for (int j = 0; dates != null && j < dates.length(); j++) {
                String cert = dates.optJSONObject(j) == null ? "" : dates.optJSONObject(j).optString("certification", "").trim();
                if (!cert.isEmpty()) return cert;
            }
        }
        return "";
    }

    /** What Home shows of a title: its list's entry, filled out from its details where they were read. */
    static Pick pick(Title t, JSONObject d, String why) {
        Pick p = new Pick();
        p.id = t.id;
        p.tv = t.tv;
        p.title = t.title;
        p.year = t.year;
        p.poster = t.poster;
        p.backdrop = t.backdrop == null ? null : t.backdrop.replace("/w780/", "/w1280/");
        p.overview = t.overview;
        p.vote = t.vote;
        p.genre = t.genres.length > 0 && Taste.genreName(t.genres[0]) != null ? Taste.genreName(t.genres[0]) : "";
        p.why = why == null ? "" : why;
        p.date = t.date == null ? "" : t.date;
        if (d != null) {
            p.logo = logo(d);
            Logos.put(t.tv, t.id, p.logo);
            if (!t.tv) {
                p.cert = cert(d);
                p.length = Pick.runtime(d.optInt("runtime"));
            } else {
                p.length = Pick.seasons(d.optInt("number_of_seasons"));
            }
            if (p.backdrop == null) p.backdrop = Tmdb.image(d.optString("backdrop_path", null), "w1280");
            if (p.overview.isEmpty()) p.overview = d.optString("overview", "");
        }
        return p;
    }

    private static Row row(List<Taste.Candidate> ranked, Map<String, JSONObject> details, Taste taste, String title, String sub, boolean thin) {
        Row r = new Row();
        r.title = title;
        r.sub = sub;
        for (Taste.Candidate k : ranked) {
            if (!(k.shown instanceof Title)) continue;
            String why = thin ? "Popular in your playlist" : taste.why(k);
            r.picks.add(pick((Title) k.shown, details.get(k.key()), why == null ? "Recommended for you" : why));
        }
        return r;
    }

    private static List<Pick> withBackdrop(Row r) {
        List<Pick> out = new ArrayList<>();
        if (r == null) return out;
        for (Pick p : r.picks) if (p.backdrop != null) out.add(p);
        return out;
    }

    /** New in the playlists (this year's and last's), with their posters from TMDB: by id, else by name. */
    private static Row fresh(Context c, Catalogue cat) {
        List<Catalogue.Row> rows = new ArrayList<>();
        List<Catalogue.Row> films = cat.newest(false, 12), shows = cat.newest(true, 12);
        for (int i = 0; i < 12; i++) {
            if (i < films.size()) rows.add(films.get(i));
            if (i < shows.size()) rows.add(shows.get(i));
        }
        List<Callable<Pick>> reads = new ArrayList<>();
        for (Catalogue.Row r : rows) {
            reads.add(() -> {
                int id = r.tmdb > 0 ? r.tmdb : search(c, r.name, r.tv);
                if (id <= 0) return null;
                JSONObject d = Tmdb.detailsKept(c, id, r.tv);
                Title t = Title.from(d, r.tv);
                if (t == null || t.poster == null) return null;
                return pick(t, d, "New in your playlist");
            });
        }
        Row out = new Row();
        out.title = "New in your playlist";
        out.sub = "This year's and last year's films and TV";
        Set<String> once = new HashSet<>();
        for (Pick p : all(reads)) if (once.add((p.tv ? "tv:" : "mv:") + p.id)) out.picks.add(p);
        return out;
    }

    /** Without TMDB: the playlists' newest by name alone (their posters drawn from their names). */
    private static Row byName(Catalogue cat, boolean tv, String title) {
        Row r = new Row();
        r.title = title;
        r.sub = "Pair a TV in Settings for posters and picks made for you";
        for (Catalogue.Row row : cat.newest(tv, ROW)) {
            Pick p = new Pick();
            p.id = row.tmdb;
            p.tv = tv;
            p.title = clean(row.name);
            p.year = row.year;
            p.why = "New in your playlist";
            if (!p.title.isEmpty()) r.picks.add(p);
        }
        return r;
    }

    /** A Continue watching title's backdrop, logo and facts, for the hero. Off the main thread; null when not read. */
    static Pick art(Context c, int tmdb, boolean tv) {
        if (tmdb <= 0 || !Tmdb.ready(c)) return null;
        try {
            JSONObject d = Tmdb.detailsKept(c, tmdb, tv);
            Title t = Title.from(d, tv);
            return t == null ? null : pick(t, d, "");
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
