package com.gathin.tvhomephone.watch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The cover page's words, worked out from TMDB's details (pure, for the tests): the line under the title, a length as
 * the page says it ("1hr 46mins"), a day ("Dec 16, 1999"), the main button's words and the time left under it, the
 * seasons to choose between and the one the page opens on, an episode's heading, and the trailers in the order their
 * row shows them.
 */
final class CoverText {
    private CoverText() {}

    /** What goes between the parts of a line. */
    static final String DOT = " • ";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    /** The kinds of video the trailers row shows, in its order. */
    private static final String[] KINDS = {"Trailer", "Teaser", "Clip", "Featurette"};

    /** "1hr 46mins", "2hrs", "52mins", "1min"; "" for none. */
    static String length(int minutes) {
        if (minutes <= 0) return "";
        int h = minutes / 60, m = minutes % 60;
        String hours = h == 0 ? "" : h + (h == 1 ? "hr" : "hrs");
        String mins = m == 0 ? "" : m + (m == 1 ? "min" : "mins");
        if (hours.isEmpty()) return mins;
        return mins.isEmpty() ? hours : hours + " " + mins;
    }

    /** TMDB's "1999-12-16" as a day, or null. */
    static LocalDate day(String iso) {
        if (iso == null || iso.length() < 10) return null;
        try {
            return LocalDate.parse(iso.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** "Dec 16, 1999"; "" for none. */
    static String date(LocalDate d) {
        return d == null ? "" : d.format(DAY);
    }

    /** "Dec 16, 1999" from TMDB's "1999-12-16"; "" when there is none. */
    static String date(String iso) {
        return date(day(iso));
    }

    /** The first few genres, with commas: "Family, Adventure, Comedy". */
    static String genres(JSONObject d, int max) {
        JSONArray g = d == null ? null : d.optJSONArray("genres");
        List<String> names = new ArrayList<>();
        for (int i = 0; g != null && i < g.length() && names.size() < max; i++) {
            JSONObject o = g.optJSONObject(i);
            String n = o == null ? "" : o.optString("name", "").trim();
            if (!n.isEmpty()) names.add(n);
        }
        return String.join(", ", names);
    }

    /**
     * The line under the title: a film's "Family, Adventure, Comedy • 2024 • 1hr 46mins", a series' "Comedy • Dec 16,
     * 1999", then " • ★ 7.4" when it has a rating.
     */
    static String meta(JSONObject d, boolean tv) {
        if (d == null) return "";
        List<String> parts = new ArrayList<>();
        String g = genres(d, 3);
        if (!g.isEmpty()) parts.add(g);
        String when = d.optString(tv ? "first_air_date" : "release_date", "");
        if (tv) {
            String day = date(when);
            if (!day.isEmpty()) parts.add(day);
        } else {
            if (when.length() >= 4 && Character.isDigit(when.charAt(0))) parts.add(when.substring(0, 4));
            String len = length(d.optInt("runtime"));
            if (!len.isEmpty()) parts.add(len);
        }
        double v = d.optDouble("vote_average", 0);
        if (v > 0) parts.add(String.format(Locale.UK, "★ %.1f", v));
        return String.join(DOT, parts);
    }

    /** "16 mins remaining", "1 min remaining"; "" when its length isn't known. */
    static String remaining(int minutesLeft) {
        if (minutesLeft <= 0) return "";
        return minutesLeft + (minutesLeft == 1 ? " min" : " mins") + " remaining";
    }

    /** The main button's words: "Play"; "Continue", or "Continue S2 E5" for a series' episode. */
    static String button(boolean resume, boolean tv, int season, int episode) {
        if (!resume) return "Play";
        if (!tv || episode <= 0) return "Continue";
        return "Continue " + (season > 0 ? "S" + season + " " : "") + "E" + episode;
    }

    /** A season's name: "Season 3", or "Specials" for season 0. */
    static String season(int n) {
        return n > 0 ? "Season " + n : "Specials";
    }

    /** The seasons to choose between, as {number, episodes}, in order; the specials (season 0) only when there is nothing else. */
    static List<int[]> seasons(JSONObject d) {
        JSONArray s = d == null ? null : d.optJSONArray("seasons");
        List<int[]> all = new ArrayList<>(), numbered = new ArrayList<>();
        for (int i = 0; s != null && i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (o == null || !o.has("season_number")) continue;
            int[] one = {o.optInt("season_number"), o.optInt("episode_count")};
            if (one[0] < 0) continue;
            all.add(one);
            if (one[0] > 0) numbered.add(one);
        }
        List<int[]> out = numbered.isEmpty() ? all : numbered;
        Collections.sort(out, (x, y) -> Integer.compare(x[0], y[0]));
        return out;
    }

    /** The season the page opens on: the one being watched when the series has it, else season 1, else its first; -1 for none. */
    static int opening(List<int[]> seasons, int watching) {
        if (seasons == null || seasons.isEmpty()) return -1;
        for (int[] s : seasons) if (watching > 0 && s[0] == watching) return watching;
        for (int[] s : seasons) if (s[0] == 1) return 1;
        return seasons.get(0)[0];
    }

    /**
     * The trailers row's videos, as {YouTube key, name}: YouTube's only, the trailers first, then the teasers, the clips
     * and the featurettes, each once, at most max.
     */
    static List<String[]> trailers(JSONObject d, int max) {
        JSONObject v = d == null ? null : d.optJSONObject("videos");
        JSONArray r = v == null ? null : v.optJSONArray("results");
        List<String[]> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String kind : KINDS) {
            for (int i = 0; r != null && i < r.length() && out.size() < max; i++) {
                JSONObject x = r.optJSONObject(i);
                if (x == null || !"YouTube".equals(x.optString("site")) || !kind.equals(x.optString("type"))) continue;
                String key = x.optString("key", "").trim();
                // A YouTube id is letters, digits, - and _: anything else is left out of the address.
                if (key.isEmpty() || !key.matches("[A-Za-z0-9_-]{1,32}") || !seen.add(key)) continue;
                String name = x.optString("name", "").trim();
                out.add(new String[]{key, name.isEmpty() ? kind : name});
            }
        }
        return out;
    }
}
