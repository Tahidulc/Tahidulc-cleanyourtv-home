package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Because you watched (Discover's tabs after My list; theme "because_tabs", on unless set false): up to three tabs,
 * "Because you loved X" for the titles rated Loved it (How was it?, newest first), then "Because you watched X" for the
 * films and series that played most here in the last 30 days (Your viewing's log, at least 20 minutes). Each is filled
 * from TMDB's recommendations for that title, then its similar titles, kept to what streams on the TV's own apps (TMDB's
 * UK providers, the licensed services only), leaving out what was watched here or rated Not for me. Each poster's foot
 * says what it shares with the title it came from ("Crime · Mystery"), its service's logo in the corner, and the hero
 * carries the tab's "Because you watched X". OK opens it in that app. No tabs without history.
 */
final class Because {
    private Because() { }

    /** A title the tabs come from. */
    static final class Source {
        int id;
        boolean tv, loved;
        String name;
    }

    private static final int MAX = 3;
    private static final int MIN_SECONDS = 20 * 60;
    private static final ExecutorService LOOKUPS = Executors.newFixedThreadPool(6);
    /** adb only (MainActivity's test_because): Lupin watched and Wildwood loved, whatever this TV has watched. */
    static volatile boolean test;

    static boolean on(Context c) {
        return Config.themeFlag(c, "because_tabs", true);
    }

    /** The tabs' titles, loved first: on the main thread (the log and the ratings are in memory or preferences). */
    static List<Source> sources(Context c) {
        List<Source> out = new ArrayList<>();
        if (test) {
            out.add(source(0, false, "Wildwood", true));
            out.add(source(0, true, "Lupin", false));
            return out;
        }
        if (!on(c)) return out;
        Set<String> nfm = Ratings.notForMe(c), have = new HashSet<>();
        for (String[] l : Ratings.loved(c)) {
            if (out.size() >= MAX) break;
            int id = id(l[0]);
            if (id <= 0 || !have.add(l[0])) continue;
            out.add(source(id, l[0].startsWith("tv:"), l[1], true));
        }
        // The most watched, the last 30 days.
        Map<String, int[]> secs = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (int d = 0; d < 30; d++) {
            JSONObject t = ViewingLog.day(c, today.minusDays(d)).optJSONObject("t");
            for (Iterator<String> it = t == null ? null : t.keys(); it != null && it.hasNext(); ) {
                String key = it.next();
                if (!key.startsWith("tv:") && !key.startsWith("mv:")) continue;
                JSONObject one = t.optJSONObject(key);
                if (one == null) continue;
                int[] s = secs.get(key);
                if (s == null) secs.put(key, s = new int[1]);
                s[0] += one.optInt("s");
                if (!one.optString("n").isEmpty()) names.put(key, one.optString("n"));
            }
        }
        List<String> keys = new ArrayList<>(secs.keySet());
        keys.sort((a, b) -> Integer.compare(secs.get(b)[0], secs.get(a)[0]));
        for (String key : keys) {
            if (out.size() >= MAX) break;
            if (secs.get(key)[0] < MIN_SECONDS || nfm.contains(key) || !have.add(key) || names.get(key) == null) continue;
            int id = id(key);
            if (id > 0) out.add(source(id, key.startsWith("tv:"), names.get(key), false));
        }
        return out;
    }

    private static Source source(int id, boolean tv, String name, boolean loved) {
        Source s = new Source();
        s.id = id;
        s.tv = tv;
        s.name = name;
        s.loved = loved;
        return s;
    }

    private static int id(String key) {
        try {
            return Integer.parseInt(key.substring(3));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** The tab's name, and the hero's line: "Because you loved Wildwood", "Because you watched Lupin". */
    static String words(Source s) {
        return (s.loved ? "Because you loved " : "Because you watched ") + s.name;
    }

    /** The tab's posters, off the main thread. */
    static PagesV2.Row row(HomeV2 h, Source s) {
        String key = h.tmdbKey;
        List<PagesV2.Title> items = new ArrayList<>();
        PagesV2.Row r = PagesV2.row(null, null, false, items);
        r.empty = "Nothing like " + s.name + " on your apps just now.";
        if (key == null || key.isEmpty()) return r;
        int id = s.id;
        boolean tv = s.tv;
        // A test's title by name.
        if (id <= 0) {
            try {
                List<PagesV2.Title> got = PagesV2.tmdb(key, "search/" + (tv ? "tv" : "movie") + "?query="
                        + java.net.URLEncoder.encode(s.name, "UTF-8"), tv);
                if (got.isEmpty()) return r;
                id = got.get(0).id;
            } catch (Exception e) {
                return r;
            }
        }
        List<String> from = genres(key, id, tv);
        String base = (tv ? "tv/" : "movie/") + id;
        List<PagesV2.Title> found = new ArrayList<>(PagesV2.tmdbRow(key, base + "/recommendations", tv));
        if (found.size() < 12) found.addAll(PagesV2.tmdbRow(key, base + "/similar", tv));
        Map<String, java.time.LocalDate> watched = ViewingLog.watched(h.a);
        Set<String> nfm = Ratings.notForMe(h.a), seen = new HashSet<>();
        String self = (tv ? "tv:" : "mv:") + id;
        List<PagesV2.Title> keep = new ArrayList<>();
        for (PagesV2.Title t : found) {
            String k = (t.tv ? "tv:" : "mv:") + t.id;
            if (k.equals(self) || watched.containsKey(k) || nfm.contains(k) || !seen.add(k)) continue;
            keep.add(t);
        }
        // Where each streams, together: only the ones on the TV's own (licensed) streaming apps stay.
        List<Future<String>> where = new ArrayList<>();
        for (PagesV2.Title t : keep) where.add(LOOKUPS.submit(() -> provider(h, PagesV2.providersGb(key, t.id, t.tv))));
        String words = words(s);
        for (int i = 0; i < keep.size() && items.size() < PagesV2.LIST_MAX; i++) {
            String provider;
            try {
                provider = where.get(i).get(15, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                provider = null;
            }
            if (provider == null) continue;
            PagesV2.Title t = keep.get(i);
            String app = HomeV2.serviceApp(provider);
            t.foot = WatchRules.sharedGenres(t.what == null || t.what.isEmpty() ? new ArrayList<>() : Arrays.asList(t.what.split(" · ")), from);
            if (t.foot.isEmpty()) t.foot = null;
            t.mark = HomeV2.serviceLogo(provider);
            t.because = words;
            // On the TV's apps (every title here is): through Debrid Stream as the posters do, else that app.
            t.ok = () -> h.openOnYourApps(t, app);
            items.add(t);
        }
        return r;
    }

    /** The first of the TV's own streaming apps that carries it in the UK (TMDB's provider id), or null. */
    private static String provider(HomeV2 h, JSONObject gb) {
        JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
        for (int i = 0; flat != null && i < flat.length(); i++) {
            JSONObject p = flat.optJSONObject(i);
            if (p == null) continue;
            String id = String.valueOf(p.optInt("provider_id"));
            String app = HomeV2.serviceApp(id);
            if (app != null && h.installed(app)) return id;
        }
        return null;
    }

    /** The genres of the title the tab came from, by name (TMDB's details). */
    private static List<String> genres(String key, int id, boolean tv) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray g = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (tv ? "tv/" : "movie/") + id
                    + "?language=en-GB&api_key=" + key)).optJSONArray("genres");
            for (int i = 0; g != null && i < g.length(); i++) {
                String n = g.optJSONObject(i) == null ? "" : g.optJSONObject(i).optString("name");
                if (!n.isEmpty()) out.add(n);
            }
        } catch (Exception ignored) {
            // shared genres then read as the title's own first one
        }
        return out;
    }
}
