package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONObject;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Movies' "Fits tonight" tab: films on the TV's own streaming apps (TMDB's UK providers for the apps installed) that
 * end before bed if started now, each poster saying when it would end. Bed is the theme's "bedtime" ("23:00" when not
 * set). In the day the films are up to three hours; late, with under 80 minutes to bed, the short ones (95 minutes).
 */
final class FitsTonight {
    private FitsTonight() { }

    /** TMDB's running times, by film. */
    private static final Map<Integer, Integer> RUNTIMES = new ConcurrentHashMap<>();
    private static final ExecutorService LOOKUPS = Executors.newFixedThreadPool(4);

    /** Bed, in minutes after midnight (the theme's "bedtime", 23:00 by default; 00:30 counts as 24:30). */
    static int bedMinutes(Context c) {
        String s = Config.themeString(c, "bedtime", "23:00").trim();
        try {
            String[] p = s.split(":");
            int m = Integer.parseInt(p[0]) * 60 + (p.length > 1 ? Integer.parseInt(p[1]) : 0);
            return m < 5 * 60 ? m + 24 * 60 : m;
        } catch (RuntimeException e) {
            return 23 * 60;
        }
    }

    private static int nowMinutes() {
        int m = LocalTime.now().toSecondOfDay() / 60;
        return m < 5 * 60 ? m + 24 * 60 : m;
    }

    /** The longest film that fits now, in minutes. */
    static int window(Context c) {
        int left = bedMinutes(c) - nowMinutes();
        if (left < 80) return 95;
        return Math.min(left, 180);
    }

    /** "Ends 22:38", started now. */
    static String ends(int runtime) {
        int end = (LocalTime.now().toSecondOfDay() / 60 + runtime) % (24 * 60);
        return String.format(Locale.UK, "Ends %02d:%02d", end / 60, end % 60);
    }

    /** The hero's line: "Ends 22:38, 22 min before bed", or just when it ends when bed has passed. */
    static String heroLine(Context c, int runtime) {
        int spare = bedMinutes(c) - (nowMinutes() + runtime);
        String end = ends(runtime);
        if (spare < 0 || spare > 6 * 60) return end;
        return end + (spare == 0 ? ", right on bedtime" : ", " + spare + " min before bed");
    }

    static PagesV2.Row row(HomeV2 h) {
        List<PagesV2.Title> items = new ArrayList<>();
        String providers = HomeV2.installedProviders(h);
        PagesV2.Row out = PagesV2.row(null, null, false, items);
        if (providers.isEmpty() || h.tmdbKey.isEmpty()) {
            out.empty = "Films that fit before bed come from the streaming apps on this TV: none is installed yet.";
            return out;
        }
        int w = window(h.a);
        List<PagesV2.Title> found = PagesV2.tmdb(h.tmdbKey, "discover/movie?watch_region=GB&with_watch_monetization_types=flatrate"
                + "&with_watch_providers=" + providers + "&with_runtime.gte=70&with_runtime.lte=" + w
                + "&vote_count.gte=40&sort_by=popularity.desc", false);
        // Each film's running time (TMDB's lists leave it out), a few at a time.
        List<Future<Integer>> times = new ArrayList<>();
        for (PagesV2.Title t : found) times.add(LOOKUPS.submit(() -> runtime(h.tmdbKey, t.id)));
        for (int i = 0; i < found.size(); i++) {
            int rt;
            try {
                rt = times.get(i).get(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                rt = 0;
            }
            if (rt <= 0 || rt > w) continue;
            PagesV2.Title t = found.get(i);
            t.runtime = rt;
            items.add(t);
        }
        out.empty = "Nothing on your apps fits before bed tonight.";
        return out;
    }

    private static int runtime(String key, int id) {
        Integer known = RUNTIMES.get(id);
        if (known != null) return known;
        try {
            int rt = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/movie/" + id + "?api_key=" + key)).optInt("runtime");
            if (rt > 0) RUNTIMES.put(id, rt);
            return rt;
        } catch (Exception e) {
            return 0;
        }
    }
}
