package com.gathin.tvhomephone.watch;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Where a title stands, for its poster's corner: "#3 Today" in TMDB's trending of the day (films and series each
 * counted on their own, the first twenty), "Trending" in the week's (the first forty), "New" when it came out in the
 * last thirty days. Read at most every three hours; nothing when TMDB can't be read.
 */
public final class Ranks {
    private Ranks() {}

    static final long KEEP_MS = 3 * 3600_000L;
    static final int TODAY = 20, WEEK = 40, NEW_DAYS = 30;

    /** Today's places (1 first) and this week's trending, by key ("mv:123", "tv:45"). */
    public static final class Places {
        final Map<String, Integer> today = new HashMap<>();
        final Set<String> week = new HashSet<>();
    }

    private static volatile Places kept;
    private static volatile long keptAt;

    /** Today's and this week's, read again when three hours old. Off the main thread; never null. */
    public static Places read(Context c) {
        Places had = kept;
        long now = System.currentTimeMillis();
        if (had != null && now - keptAt < KEEP_MS) return had;
        Places out = new Places();
        if (!Tmdb.ready(c)) return out;
        boolean any = false;
        for (boolean tv : new boolean[]{false, true}) {
            String kind = tv ? "tv" : "movie";
            try {
                place(out.today, Tmdb.getKept(c, "trending/" + kind + "/day?page=1", KEEP_MS), tv, 1, TODAY);
                any = true;
            } catch (IOException | RuntimeException ignored) {
                // none of today's for this kind
            }
            for (int page = 1; page <= 2; page++) {
                try {
                    Map<String, Integer> week = new HashMap<>();
                    place(week, Tmdb.getKept(c, "trending/" + kind + "/week?page=" + page, KEEP_MS), tv, 1 + (page - 1) * 20, WEEK);
                    out.week.addAll(week.keySet());
                    any = true;
                } catch (IOException | RuntimeException ignored) {
                    // the week's first page is enough
                }
            }
        }
        if (any) {
            kept = out;
            keptAt = now;
        }
        return out;
    }

    /** Pure: a list's results into places (from first on), up to max; titles without an id are skipped. */
    static void place(Map<String, Integer> into, JSONObject list, boolean tv, int first, int max) {
        JSONArray r = list == null ? null : list.optJSONArray("results");
        int at = first;
        for (int i = 0; r != null && i < r.length() && at <= max; i++) {
            JSONObject t = r.optJSONObject(i);
            if (t == null || t.optInt("id") <= 0) continue;
            String key = (tv ? "tv:" : "mv:") + t.optInt("id");
            if (!into.containsKey(key)) into.put(key, at);
            at++;
        }
    }

    /** Pure: the word on a title's poster: "#3 Today", "Trending", "New", or null for none. */
    public static String label(Places p, boolean tv, int id, String date, LocalDate today) {
        String key = (tv ? "tv:" : "mv:") + id;
        Integer place = p == null || id <= 0 ? null : p.today.get(key);
        if (place != null && place <= TODAY) return "#" + place + " Today";
        if (p != null && id > 0 && p.week.contains(key)) return "Trending";
        LocalDate out = date(date);
        if (out != null && today != null && !out.isAfter(today) && !out.isBefore(today.minusDays(NEW_DAYS))) return "New";
        return null;
    }

    /** Pure: "2026-10-31" as a date; null for none or anything else. */
    static LocalDate date(String s) {
        if (s == null || s.length() < 10) return null;
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
