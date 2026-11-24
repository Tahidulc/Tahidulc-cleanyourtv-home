package com.gathin.tvhomephone.live;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The wider search for a programme the guide gives no picture (pure, for the tests): TMDB by its title as the guide's
 * words allow (TileKind.lookupTitle: "Film: Verity (2025)" is "Verity"), a film looked for as a film with its year
 * (from the title or the description's start), a series as a series (an episode in the words, or a kids' channel), the
 * rest as either; nothing looked for on a sports or news channel, or for a title that names no programme ("News",
 * "Close", "To Be Announced"): those take the channel's own picture, its logo on its colour. Only a result with the
 * same title counts (a guide's "Verity" is not TMDB's "Verity's Secret"), the film of that year first.
 */
final class ArtQuery {
    /** TMDB's search to use: a film, a series, or either. */
    static final String MOVIE = "movie", TV = "tv", MULTI = "multi";

    final String type, title, year;

    private ArtQuery(String type, String title, String year) {
        this.type = type;
        this.title = title;
        this.year = year;
    }

    private static final String[] KIDS = {"kids", "junior", "cbeebies", "cbbc", "nick jr", "nickelodeon", "nicktoons", "cartoon",
            "disney", "boomerang", "baby", "children", "toons", "ketchup", "tiny pop", "pop max"};
    /** Titles that name no programme of their own: the channel's picture for them. */
    private static final Pattern GENERIC = Pattern.compile("^(the )?(news|weather|news and weather|close|closedown|off air|"
            + "teleshopping|shopping|to be announced|tba|tbc|no information|no programme information|programmes start soon|"
            + "programmes resume|paid programming|advert|adverts|film|movie|live|sport|regional news|local news|headlines|"
            + "breakfast|\\W*)$");

    /** A children's channel, by its name or its playlist group. */
    static boolean kidsChannel(String name, String group) {
        String s = (name == null ? "" : name.toLowerCase(Locale.ROOT)) + " | " + (group == null ? "" : group.toLowerCase(Locale.ROOT));
        for (String k : KIDS) if (s.contains(k)) return true;
        return false;
    }

    /** The search for a programme, or null when none is to be made (the channel's own picture then). */
    static ArtQuery of(String channel, String group, String title, String desc) {
        if (TileKind.sportsChannel(channel, group) || TileKind.newsChannel(channel, group)) return null;
        String t = TileKind.lookupTitle(title);
        String low = norm(t);
        if (low.length() < 2 || GENERIC.matcher(t.toLowerCase(Locale.ROOT).trim()).matches()) return null;
        if (channel != null && low.equals(norm(channel))) return null;
        String d = desc == null ? "" : desc, y = TileKind.year(title, d);
        if (TileKind.film(channel == null ? "" : channel, group == null ? "" : group, title == null ? "" : title, d)) return new ArtQuery(MOVIE, t, y);
        if (kidsChannel(channel, group) || TileKind.episode(title, d) != null) return new ArtQuery(TV, t, "");
        return new ArtQuery(MULTI, t, y);
    }

    /** TMDB's path for it (the key is added by Tmdb). */
    String path() {
        String p = "search/" + type + "?query=" + enc(title) + "&include_adult=false&language=en-GB";
        if (MOVIE.equals(type) && !year.isEmpty()) p += "&year=" + year;
        return p;
    }

    /** What it is kept by: the same search for the same programme on any channel. */
    String key() {
        return type + "|" + norm(title) + "|" + year;
    }

    /**
     * Pure: the picture out of TMDB's results ({"results": [...]}): the first with the same title (a film of the year
     * asked for before one of another; for either, films and series only), its backdrop, else its poster; "" for none.
     */
    String pick(JSONObject found) {
        JSONArray r = found == null ? null : found.optJSONArray("results");
        if (r == null) return "";
        JSONObject first = null, ofYear = null;
        for (int k = 0; k < r.length(); k++) {
            JSONObject o = r.optJSONObject(k);
            if (o == null) continue;
            String media = o.optString("media_type", "");
            if (MULTI.equals(type) && !media.equals(MOVIE) && !media.equals(TV)) continue;
            String name = o.optString("title", o.optString("name", ""));
            String orig = o.optString("original_title", o.optString("original_name", ""));
            if (!same(name, title) && !same(orig, title)) continue;
            if (path(o).isEmpty()) continue;
            if (first == null) first = o;
            String date = o.optString("release_date", o.optString("first_air_date", ""));
            if (!year.isEmpty() && date.startsWith(year) && ofYear == null) ofYear = o;
        }
        JSONObject best = ofYear != null ? ofYear : first;
        return best == null ? "" : path(best);
    }

    private static String path(JSONObject o) {
        String b = o.optString("backdrop_path", "");
        if (b.isEmpty() || b.equals("null")) b = o.optString("poster_path", "");
        return b.equals("null") ? "" : b;
    }

    static boolean same(String a, String b) {
        String x = norm(a);
        return !x.isEmpty() && x.equals(norm(b));
    }

    /** Pure: a title as it is compared: no accents or case, "&" as "and", no leading "the", letters and digits only. */
    static String norm(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace("&", "and");
        if (t.startsWith("the ")) t = t.substring(4);
        StringBuilder b = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) b.append(ch);
        }
        return b.toString();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }
}
