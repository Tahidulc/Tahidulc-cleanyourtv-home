package com.gathin.tvhomephone.watch;

import android.content.Context;

import com.gathin.tvhomephone.SyncStore;
import com.gathin.tvhomephone.sync.Tvs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * The Watch tab's lists, the TVs' own tabs (PagesV2's discover, movies and shows): TMDB paths, and the ones made here
 * from what the TVs and this phone keep (Continue watching, My list, Because you watched, Fits tonight, Your shows,
 * New episodes, Coming up). Each list's titles carry their poster's foot line, as the TVs draw them.
 */
public final class Lists {
    private Lists() {}

    public static final class Spec {
        public final String name;
        final String path, path2, special;
        final Boolean tv;
        final boolean numbered;

        Spec(String name, String path, String path2, Boolean tv, boolean numbered, String special) {
            this.name = name;
            this.path = path;
            this.path2 = path2;
            this.tv = tv;
            this.numbered = numbered;
            this.special = special;
        }
    }

    private static Spec tmdb(String name, String path, Boolean tv) { return new Spec(name, path, null, tv, false, null); }

    private static Spec mixed(String name, String tvPath, String moviePath) { return new Spec(name, tvPath, moviePath, null, false, null); }

    private static Spec special(String name, String kind) { return new Spec(name, null, null, null, false, kind); }

    private static String on(boolean tv, int provider) {
        return "discover/" + (tv ? "tv" : "movie") + "?watch_region=GB&with_watch_providers=" + provider + "&sort_by=popularity.desc";
    }

    private static String genre(boolean tv, int id) {
        return "discover/" + (tv ? "tv" : "movie") + "?with_genres=" + id + "&sort_by=popularity.desc&vote_count.gte=150";
    }

    private static String since(int days) { return LocalDate.now().minusDays(days).toString(); }

    private static String today() { return LocalDate.now().toString(); }

    /** A section's lists: 0 Discover, 1 Movies, 2 TV shows. */
    public static List<Spec> section(Context c, int section) {
        List<Spec> s = new ArrayList<>();
        if (section == 0) {
            s.add(special("Continue watching", "@resume"));
            s.add(special("My list", "@mylist"));
            s.addAll(because(c));
            s.add(mixed("New releases", "discover/tv?first_air_date.gte=" + LocalDate.now().minusMonths(2) + "&sort_by=popularity.desc&with_original_language=en",
                    "discover/movie?region=GB&with_release_type=4|5&release_date.gte=" + LocalDate.now().minusWeeks(6)
                            + "&release_date.lte=" + today() + "&sort_by=popularity.desc"));
            s.add(tmdb("Trending", "trending/all/day", null));
            int[] providers = {8, 337, 9, 350};
            String[] names = {"Netflix", "Disney+", "Prime Video", "Apple TV+"};
            for (int i = 0; i < providers.length; i++) {
                String w = "&watch_region=GB&with_watch_providers=" + providers[i] + "&sort_by=popularity.desc";
                s.add(mixed(names[i], "discover/tv?without_genres=10763,10764,10767" + w, "discover/movie?" + w.substring(1)));
            }
        } else if (section == 1) {
            String m = "discover/movie?sort_by=popularity.desc&watch_region=GB";
            s.add(special("Fits tonight", "@fits"));
            s.add(tmdb("Popular", "movie/popular?region=GB", false));
            s.add(new Spec("Top 10", "trending/movie/week", null, false, true, null));
            s.add(tmdb("New to stream", m + "&region=GB&with_release_type=4&release_date.gte=" + since(45) + "&release_date.lte=" + today(), false));
            s.add(tmdb("In cinemas", "movie/now_playing?region=GB", false));
            s.add(tmdb("Trending", "trending/movie/day", false));
            s.add(tmdb("Netflix", on(false, 8), false));
            s.add(tmdb("Prime Video", on(false, 9), false));
            s.add(tmdb("Disney+", on(false, 337), false));
            s.add(tmdb("Apple TV+", on(false, 350), false));
            s.add(tmdb("NOW", on(false, 39), false));
            String[][] genres = {{"Action", "28"}, {"Thriller", "53"}, {"Drama", "18"}, {"Sci-fi", "878"}, {"Horror", "27"},
                    {"Crime", "80"}, {"Romance", "10749"}, {"Family", "10751"}, {"Animation", "16"}, {"Documentary", "99"}};
            for (String[] g : genres) s.add(tmdb(g[0], genre(false, Integer.parseInt(g[1])), false));
            s.add(2 + 10 + 1, tmdb("Comedy", "discover/movie?with_genres=35&without_genres=27,53&sort_by=popularity.desc&vote_count.gte=300", false));
            s.add(tmdb("Hidden gems", "discover/movie?vote_average.gte=7.4&vote_count.gte=150&vote_count.lte=1500&sort_by=vote_average.desc", false));
            s.add(tmdb("Critically acclaimed", "movie/top_rated?region=GB", false));
            s.add(tmdb("True stories", "discover/movie?with_keywords=9672&sort_by=popularity.desc&vote_count.gte=200", false));
            s.add(tmdb("Best of British", "discover/movie?with_origin_country=GB&sort_by=popularity.desc&vote_count.gte=200", false));
            s.add(tmdb("90s classics", "discover/movie?primary_release_date.gte=1990-01-01&primary_release_date.lte=1999-12-31&sort_by=popularity.desc&vote_count.gte=1000", false));
            s.add(tmdb("Korean cinema", "discover/movie?with_original_language=ko&sort_by=popularity.desc&vote_count.gte=150", false));
            s.add(tmdb("Coming soon", "movie/upcoming?region=GB", false));
        } else {
            s.add(special("Your shows", "@yourshows"));
            s.add(special("New episodes", "@newepisodes"));
            s.add(special("Coming up", "@comingup"));
            s.add(tmdb("New series", "discover/tv?first_air_date.gte=" + since(60) + "&first_air_date.lte=" + today() + "&sort_by=popularity.desc&with_original_language=en", true));
            s.add(new Spec("Top 10", "trending/tv/week", null, true, true, null));
            s.add(tmdb("On TV now", "tv/on_the_air?without_genres=10763,10764,10767", true));
            s.add(tmdb("Trending", "trending/tv/day", true));
            s.add(tmdb("Netflix", on(true, 8), true));
            s.add(tmdb("Prime Video", on(true, 9), true));
            s.add(tmdb("Disney+", on(true, 337), true));
            s.add(tmdb("Apple TV+", on(true, 350), true));
            s.add(tmdb("NOW", on(true, 39), true));
            s.add(tmdb("BBC iPlayer", on(true, 38), true));
            s.add(tmdb("Drama", genre(true, 18), true));
            s.add(tmdb("Crime", genre(true, 80), true));
            s.add(tmdb("Comedy", genre(true, 35), true));
            s.add(tmdb("Sci-fi & fantasy", genre(true, 10765), true));
            s.add(tmdb("Action", genre(true, 10759), true));
            s.add(tmdb("Mystery", genre(true, 9648), true));
            s.add(tmdb("Reality", "discover/tv?with_genres=10764&sort_by=popularity.desc&watch_region=GB", true));
            s.add(tmdb("Documentary", genre(true, 99), true));
            s.add(tmdb("Animation", "discover/tv?with_genres=16&sort_by=popularity.desc&vote_count.gte=100", true));
            s.add(tmdb("Kids", "discover/tv?with_genres=10762&sort_by=popularity.desc", true));
            s.add(tmdb("Binge in a weekend", "discover/tv?with_type=2&sort_by=popularity.desc&vote_count.gte=100", true));
            s.add(tmdb("Best of British", "discover/tv?with_origin_country=GB&sort_by=popularity.desc&vote_count.gte=100", true));
            s.add(tmdb("Critically acclaimed", "tv/top_rated", true));
            s.add(tmdb("Hidden gems", "discover/tv?vote_average.gte=8&vote_count.gte=100&vote_count.lte=1200&sort_by=vote_average.desc", true));
            s.add(tmdb("Anime", "discover/tv?with_genres=16&with_original_language=ja&sort_by=popularity.desc&vote_count.gte=100", true));
            s.add(tmdb("Korean dramas", "discover/tv?with_original_language=ko&with_genres=18&sort_by=popularity.desc&vote_count.gte=50", true));
        }
        return s;
    }

    /** Because you loved / watched: up to three tabs, the Loved it ratings first, then what plays most on the TVs. */
    static List<Spec> because(Context c) {
        List<Spec> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (JSONObject r : SyncStore.list(c, "rating")) {
            if (out.size() >= 3) break;
            if (r.optInt("value") != 2 || r.optInt("id") <= 0) continue;
            String k = (r.optBoolean("tv") ? "tv:" : "mv:") + r.optInt("id");
            if (!used.add(k)) continue;
            out.add(special("Because you loved " + r.optString("title", "this"), "@because:" + k));
        }
        JSONObject w = Tvs.watchingCached(c);
        for (String list : new String[]{"shows", "resume"}) {
            JSONArray a = w.optJSONArray(list);
            for (int i = 0; a != null && i < a.length() && out.size() < 3; i++) {
                JSONObject t = a.optJSONObject(i);
                if (t == null || t.optInt("id") <= 0) continue;
                String k = (t.optBoolean("tv", list.equals("shows")) ? "tv:" : "mv:") + t.optInt("id");
                if (!used.add(k)) continue;
                out.add(special("Because you watched " + t.optString("title", "this"), "@because:" + k));
            }
        }
        return out;
    }

    /** A list's titles, with their foot lines. Off the main thread. */
    public static List<Title> load(Context c, Spec s) throws IOException {
        if (s.special == null) {
            List<Title> out;
            if (s.path2 != null) out = mix(Tmdb.list(c, s.path, true, 2), Tmdb.list(c, s.path2, false, 2));
            else out = Tmdb.list(c, s.path, s.tv, 2);
            if (s.numbered) {
                out = new ArrayList<>(out.subList(0, Math.min(10, out.size())));
                for (int i = 0; i < out.size(); i++) out.get(i).rank = i + 1;
            }
            for (Title t : out) if (t.foot == null && t.vote > 0) t.foot = (t.year.isEmpty() ? "" : t.year + " · ") + String.format(Locale.UK, "★ %.1f", t.vote);
            return out;
        }
        if (s.special.equals("@mylist")) return myList(c);
        if (s.special.equals("@resume")) return resume(c);
        if (s.special.equals("@fits")) return fits(c);
        if (s.special.equals("@yourshows")) return yourShows(c, false);
        if (s.special.equals("@newepisodes")) return yourShows(c, true);
        if (s.special.equals("@comingup")) return ComingUp.load(c, 14);
        if (s.special.startsWith("@because:")) return because(c, s.special.substring("@because:".length()));
        if (s.special.startsWith("@search:")) return Tmdb.list(c, "search/multi?include_adult=false&query="
                + java.net.URLEncoder.encode(s.special.substring(8), "UTF-8"), null, 1);
        return new ArrayList<>();
    }

    public static Spec search(String q) {
        return special("Search", "@search:" + q);
    }

    private static List<Title> mix(List<Title> a, List<Title> b) {
        List<Title> out = new ArrayList<>();
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            if (i < a.size()) out.add(a.get(i));
            if (i < b.size()) out.add(b.get(i));
        }
        return out;
    }

    public static List<Title> myList(Context c) {
        List<Title> out = new ArrayList<>();
        for (JSONObject d : SyncStore.list(c, "mylist")) {
            Title t = Title.fromItem(d);
            if (t != null) out.add(t);
        }
        return out;
    }

    /** Continue watching, from the TVs (what is part-way through on them, the TV's name in the corner). */
    public static List<Title> resume(Context c) {
        List<Title> out = new ArrayList<>();
        JSONArray a = Tvs.watchingCached(c).optJSONArray("resume");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject d = a.optJSONObject(i);
            Title t = Title.fromItem(d);
            if (t == null) continue;
            double p = d.optDouble("progress", -1);
            t.progress = (float) (p > 1 ? p / 100 : p);
            if (d.has("season")) t.foot = "S" + d.optInt("season") + " E" + d.optInt("episode");
            if (d.has("left")) t.foot = (t.foot == null ? "" : t.foot + " · ") + d.optString("left");
            t.badge = d.optString("on", null);
            out.add(t);
        }
        return out;
    }

    /** Your shows (and New episodes: only those with one out this past week), from the TVs, as the TV badges them. */
    public static List<Title> yourShows(Context c, boolean newOnly) {
        List<Title> out = new ArrayList<>();
        JSONArray a = Tvs.watchingCached(c).optJSONArray("shows");
        LocalDate today = LocalDate.now();
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject d = a.optJSONObject(i);
            Title t = Title.fromItem(d);
            if (t == null) continue;
            t.tv = true;
            JSONObject next = d.optJSONObject("next");
            LocalDate air = null;
            try {
                // The TVs' /sync/watching names it "date"; TMDB's own episodes "air_date".
                String day = next == null ? "" : next.optString("date", next.optString("air_date"));
                if (day.length() >= 10) air = LocalDate.parse(day.substring(0, 10));
            } catch (RuntimeException ignored) {
                // no date
            }
            boolean fresh = air != null && !air.isAfter(today) && !air.isBefore(today.minusDays(7));
            if (newOnly && !fresh) continue;
            String badge = d.optString("badge", "");
            if (!badge.isEmpty()) t.badge = badge;
            else if (fresh) t.badge = "NEW · S" + next.optInt("season", d.optInt("season")) + " E" + next.optInt("episode");
            else if (d.optInt("left") > 0) t.badge = d.optInt("left") + " LEFT";
            else if (air != null && air.isAfter(today)) t.badge = "E" + next.optInt("episode") + " " + Plan.day(air, today);
            if (t.badge != null && t.badge.startsWith("NEW")) t.badgeColor = 0xFFFF4FA3;
            String foot = d.optString("foot", "");
            t.foot = !foot.isEmpty() ? foot : d.has("season") ? "S" + d.optInt("season") + " E" + d.optInt("episode") + " watched" : null;
            if (d.has("progress")) {
                double p = d.optDouble("progress");
                t.progress = (float) (p > 1 ? p / 100 : p);
            }
            out.add(t);
        }
        return out;
    }

    /** Fits tonight: films on the UK's streaming services that end before bed if started now, each saying when. */
    static List<Title> fits(Context c) throws IOException {
        Calendar now = Calendar.getInstance();
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int left = Plan.spare(nowMin, WatchPrefs.bedtime(c));
        int max = left < 80 ? 95 : Math.min(180, left);
        List<Title> base = Tmdb.list(c, "discover/movie?watch_region=GB&with_watch_monetization_types=flatrate"
                + "&with_watch_providers=8|9|337|350|39|38|531&with_runtime.gte=60&with_runtime.lte=" + max
                + "&sort_by=popularity.desc&vote_count.gte=100", false, 2);
        List<Title> top = new ArrayList<>(base.subList(0, Math.min(30, base.size())));
        List<Future<?>> jobs = new ArrayList<>();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        int bed = WatchPrefs.bedtime(c);
        for (Title t : top) {
            jobs.add(pool.submit(() -> {
                try {
                    int run = Tmdb.get(c, "movie/" + t.id).optInt("runtime");
                    if (run <= 0) return;
                    int end = nowMin + run;
                    int spare = Plan.spare(end, bed);
                    t.badge = "Ends " + Plan.hm(end);
                    t.badgeColor = spare < 10 ? 0xFFB8741A : 0xCC080A1E;
                    t.foot = Plan.length(run) + " · " + (spare >= 0 ? spare + " min spare" : -spare + " min past bed");
                } catch (IOException ignored) {
                    // shown without its end
                }
            }));
        }
        for (Future<?> f : jobs) {
            try {
                f.get();
            } catch (Exception ignored) {
                // that one without its end
            }
        }
        pool.shutdown();
        return top;
    }

    /** Because you watched or loved one title: TMDB's recommendations (then similar), without what's been seen or disliked. */
    static List<Title> because(Context c, String key) throws IOException {
        boolean tv = key.startsWith("tv:");
        int id = Integer.parseInt(key.substring(3));
        JSONObject src = Tmdb.get(c, (tv ? "tv/" : "movie/") + id);
        Set<Integer> srcGenres = new HashSet<>();
        JSONArray g = src.optJSONArray("genres");
        for (int i = 0; g != null && i < g.length(); i++) srcGenres.add(g.optJSONObject(i).optInt("id"));
        Set<String> skip = new HashSet<>(Arrays.asList(key));
        for (JSONObject r : SyncStore.list(c, "rating")) if (r.optInt("value") < 0) skip.add((r.optBoolean("tv") ? "tv:" : "mv:") + r.optInt("id"));
        JSONObject w = Tvs.watchingCached(c);
        for (String l : new String[]{"shows", "resume"}) {
            JSONArray a = w.optJSONArray(l);
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject t = a.optJSONObject(i);
                if (t != null) skip.add((t.optBoolean("tv", l.equals("shows")) ? "tv:" : "mv:") + t.optInt("id"));
            }
        }
        List<Title> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String kind : new String[]{"recommendations", "similar"}) {
            for (Title t : Tmdb.list(c, (tv ? "tv/" : "movie/") + id + "/" + kind, tv, 1)) {
                if (skip.contains(t.key()) || !seen.add(t.key())) continue;
                StringBuilder shared = new StringBuilder();
                for (int gid : t.genres) {
                    if (!srcGenres.contains(gid) || Tmdb.genre(gid) == null) continue;
                    if (shared.length() > 0) shared.append(" · ");
                    shared.append(Tmdb.genre(gid));
                    if (shared.length() > 22) break;
                }
                t.foot = shared.length() > 0 ? shared.toString() : null;
                out.add(t);
            }
            if (out.size() >= 12) break;
        }
        return out;
    }
}
