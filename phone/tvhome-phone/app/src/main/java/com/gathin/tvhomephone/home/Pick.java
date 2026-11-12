package com.gathin.tvhomephone.home;

import com.gathin.tvhomephone.watch.Title;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One title on Home (in the hero or a row): a film or series from the family's own playlists, what it shows (its
 * poster, backdrop, logo, facts) and why it is there. Kept as JSON so Home opens at once with the last picks.
 */
final class Pick {
    int id;
    boolean tv;
    String title = "", year = "", genre = "", overview = "", cert = "", length = "";
    /** Pictures (null for none): the poster (w342), the backdrop (w1280), the title's logo (w500). */
    String poster, backdrop, logo;
    double vote;
    /** Its kicker: "Because you watched Dune", "More Thriller", "New in your playlist"; "" for none. */
    String why = "";

    /** As TitleActivity opens it. */
    Title title() {
        Title t = new Title();
        t.id = id;
        t.tv = tv;
        t.title = title;
        t.poster = poster;
        t.backdrop = backdrop;
        t.year = year;
        return t;
    }

    /** "2024 · Thriller · ★ 7.8 · 2h 10m": what there is of it, in that order. Pure. */
    String facts() {
        StringBuilder b = new StringBuilder();
        add(b, year);
        add(b, genre);
        if (vote >= 1) add(b, "★ " + String.format(java.util.Locale.UK, "%.1f", vote));
        add(b, length);
        return b.toString();
    }

    private static void add(StringBuilder b, String s) {
        if (s == null || s.isEmpty()) return;
        if (b.length() > 0) b.append("  ·  ");
        b.append(s);
    }

    /** "2h 10m", "48m" for a film's runtime in minutes; "" for none. Pure. */
    static String runtime(int minutes) {
        if (minutes <= 0) return "";
        return minutes < 60 ? minutes + "m" : minutes / 60 + "h " + String.format(java.util.Locale.UK, "%02dm", minutes % 60);
    }

    /** "3 seasons", "1 season"; "" for none. Pure. */
    static String seasons(int n) {
        return n <= 0 ? "" : n == 1 ? "1 season" : n + " seasons";
    }

    JSONObject json() throws JSONException {
        return new JSONObject().put("id", id).put("tv", tv).put("title", title).put("year", year).put("genre", genre)
                .put("overview", overview).put("cert", cert).put("length", length).put("poster", poster == null ? "" : poster)
                .put("backdrop", backdrop == null ? "" : backdrop).put("logo", logo == null ? "" : logo).put("vote", vote).put("why", why);
    }

    static Pick from(JSONObject o) {
        if (o == null) return null;
        Pick p = new Pick();
        p.id = o.optInt("id");
        p.tv = o.optBoolean("tv");
        p.title = o.optString("title");
        p.year = o.optString("year");
        p.genre = o.optString("genre");
        p.overview = o.optString("overview");
        p.cert = o.optString("cert");
        p.length = o.optString("length");
        p.poster = blank(o.optString("poster"));
        p.backdrop = blank(o.optString("backdrop"));
        p.logo = blank(o.optString("logo"));
        p.vote = o.optDouble("vote", 0);
        p.why = o.optString("why");
        return p.title.isEmpty() ? null : p;
    }

    static JSONArray json(List<Pick> list) throws JSONException {
        JSONArray a = new JSONArray();
        for (Pick p : list) a.put(p.json());
        return a;
    }

    static List<Pick> list(JSONArray a) {
        List<Pick> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            Pick p = from(a.optJSONObject(i));
            if (p != null) out.add(p);
        }
        return out;
    }

    private static String blank(String s) {
        return s == null || s.isEmpty() || s.equals("null") ? null : s;
    }
}
