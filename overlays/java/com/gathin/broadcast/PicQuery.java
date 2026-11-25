package com.gathin.broadcast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A programme's picture looked for by its title (TMDB, where the app has its key), the same in Homefield TV and the
 * phone app, pure: the title tidied of what isn't its name ("New:", "Live:", "Film:", its episode's numbers, a year),
 * the year and the episode its words give, whether it is worth looking for at all (not a game, a bulletin, a closedown,
 * "To Be Announced", the channel's own name), and the answer matched with care so a wrong show is never shown: the same
 * name once accents, case, "&" and a leading "The" are set aside (a country or year in brackets either side, or the
 * guide's episode after a colon, allowed; "Verity" is never "Verity's Secret"), a film of the year asked for before one
 * of another, nothing with no picture.
 */
public final class PicQuery {
    private PicQuery() {}

    private static final Pattern LEAD = Pattern.compile("(?i)^\\s*(?:new|live|premiere|film|movie|brand new|season premiere|"
            + "series premiere|repeat|catch up|sky cinema premiere|tv premiere)\\s*[:\\-–]\\s*");
    private static final Pattern EPISODE = Pattern.compile("(?i)[\\s,:\\-–]*\\(?\\s*(?:s(?:eries|eason)?\\s*\\.?\\s*\\d{1,2}\\s*[,:/-]?\\s*"
            + "e(?:p(?:isode)?)?\\s*\\.?\\s*\\d{1,3}|series\\s+\\d{1,2}|season\\s+\\d{1,2}|episode\\s+\\d{1,3}|ep\\.?\\s*\\d{1,3}|"
            + "part\\s+\\d{1,2}|\\d{1,2}/\\d{1,2})\\s*\\)?.*$");
    private static final Pattern YEAR_IN_NAME = Pattern.compile("\\s*[\\[(]((?:19|20)\\d{2})[\\])]\\s*$");
    private static final Pattern YEAR_IN_DESC = Pattern.compile("^\\s*\\(?((?:19|20)\\d{2})\\)?[\\s.:,-]|\\(((?:19|20)\\d{2})\\)");
    private static final Pattern SE = Pattern.compile("(?i)\\bs(?:eries|eason)?\\s*\\.?\\s*(\\d{1,2})\\s*[,:/-]?\\s*e(?:p(?:isode)?)?\\s*\\.?\\s*(\\d{1,3})\\b");
    private static final Pattern EP = Pattern.compile("(?i)\\b(?:episode|ep\\.?)\\s*(\\d{1,3})\\b");
    private static final Pattern NOT_A_PROGRAMME = Pattern.compile("(?i)^(?:the )?(?:news|weather|news and weather|close|closedown|"
            + "off air|teleshopping|shopping|to be announced|tba|tbc|no information|no programme information|programmes start soon|"
            + "programmes resume|paid programming|advert|adverts|film|movie|live|sport|regional news|local news|headlines|"
            + "breakfast|live coverage|coverage|tv shop|this is bbc .*|[^a-z0-9]*)$");
    private static final Pattern LIVE = Pattern.compile("(?i)\\blive\\b");
    private static final Pattern BULLETIN = Pattern.compile("(?i)\\b(?:news|headlines|bulletin|weather)\\b");

    /** The title without what isn't its name ("Film: Verity (2025)" is "Verity", "Doctor Who S14 E2" is "Doctor Who"). */
    public static String name(String title) {
        String t = title == null ? "" : title.trim();
        t = LEAD.matcher(t).replaceFirst("");
        Matcher y = YEAR_IN_NAME.matcher(t);
        if (y.find()) t = t.substring(0, y.start()).trim();
        t = EPISODE.matcher(t).replaceFirst("").trim();
        return t;
    }

    /** The year the title (its brackets) or the description's start gives ("" for none). */
    public static String year(String title, String desc) {
        Matcher y = YEAR_IN_NAME.matcher(title == null ? "" : title.trim());
        if (y.find()) return y.group(1);
        Matcher d = YEAR_IN_DESC.matcher(desc == null ? "" : desc);
        if (d.find()) return d.group(1) != null ? d.group(1) : d.group(2);
        return "";
    }

    /** The season and episode the title or description gives ({0, 0} for none; {0, e} an episode alone). */
    public static int[] episode(String title, String desc) {
        for (String s : new String[]{title, desc}) {
            if (s == null) continue;
            Matcher m = SE.matcher(s);
            if (m.find()) return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        for (String s : new String[]{title, desc}) {
            if (s == null) continue;
            Matcher m = EP.matcher(s);
            if (m.find()) return new int[]{0, Integer.parseInt(m.group(1))};
        }
        return new int[]{0, 0};
    }

    /**
     * Whether a programme is worth looking for by its title: its name says something (two letters or more), isn't one
     * that names no programme, isn't a game (PicSources.sides: its fixture's picture then) or a bulletin on a news or
     * sports channel, and isn't the channel's own name.
     */
    public static boolean worth(String title, String channel, String kind) {
        String n = name(title);
        if (norm(n).length() < 2 || NOT_A_PROGRAMME.matcher(n.trim()).matches()) return false;
        if (PicSources.sides(title) != null) return false;
        if (PicSources.NEWS.equals(kind) && BULLETIN.matcher(n).find()) return false;
        if (PicSources.sporting(kind) && title != null && LIVE.matcher(title).find()) return false;
        return channel == null || !norm(n).equals(norm(channel));
    }

    /** Pure: a title as compared: no accents or case, "&" as "and", no leading "the", letters and digits only. */
    public static String norm(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace("&", "and");
        t = t.trim();
        if (t.startsWith("the ")) t = t.substring(4);
        StringBuilder b = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9') b.append(ch);
        }
        return b.toString();
    }

    private static final Pattern TAG = Pattern.compile("(?i)\\s*[\\[(](?:uk|us|usa|gb|au|nz|ca|ie|(?:19|20)\\d{2})[\\])]\\s*$");

    /**
     * Pure: whether a search's answer (its name) is the guide's programme (its title): the same once compared (norm), or
     * with a country or year in brackets set aside on either side, or the guide's part before a colon (its episode's name
     * after it) of two words or more; or, for a long name, one letter's slip. Never a longer or shorter name.
     */
    public static boolean same(String answer, String title) {
        String a = norm(answer), t = norm(title);
        if (a.isEmpty() || t.isEmpty()) return false;
        if (a.equals(t)) return true;
        String a2 = norm(TAG.matcher(answer).replaceFirst("")), t2 = norm(TAG.matcher(title).replaceFirst(""));
        if (!a2.isEmpty() && a2.equals(t2)) return true;
        int colon = title.indexOf(':');
        if (colon > 0 && answer.indexOf(':') < 0) {
            String head = title.substring(0, colon).trim();
            if (head.split("\\s+").length >= 2 && norm(head).equals(a)) return true;
        }
        return a.length() >= 10 && a.length() == t.length() && slips(a, t) <= 1;
    }

    private static int slips(String a, String b) {
        int n = 0;
        for (int i = 0; i < a.length(); i++) if (a.charAt(i) != b.charAt(i)) n++;
        return n;
    }

    /**
     * Pure: the picture's path out of TMDB's search answer ({"results": [...]}) for a title: the first that is the same
     * programme (same()) with a picture (its backdrop, else its poster), a film or series only when either was asked
     * (type "multi"), the one of the year asked for (a year either side) before another; "" for none.
     */
    public static String pick(JSONObject found, String type, String title, String year) {
        JSONArray r = found == null ? null : found.optJSONArray("results");
        if (r == null) return "";
        JSONObject first = null, ofYear = null;
        int want = year == null || year.isEmpty() ? 0 : Integer.parseInt(year);
        for (int k = 0; k < r.length(); k++) {
            JSONObject o = r.optJSONObject(k);
            if (o == null || o.optBoolean("adult")) continue;
            String media = o.optString("media_type", "");
            if ("multi".equals(type) && !"movie".equals(media) && !"tv".equals(media)) continue;
            String name = o.optString("title", o.optString("name", ""));
            String orig = o.optString("original_title", o.optString("original_name", ""));
            if (!same(name, title) && !same(orig, title)) continue;
            if (path(o).isEmpty()) continue;
            if (first == null) first = o;
            String date = o.optString("release_date", o.optString("first_air_date", ""));
            int y = date.length() >= 4 && Character.isDigit(date.charAt(0)) ? Integer.parseInt(date.substring(0, 4)) : 0;
            if (want > 0 && y > 0 && Math.abs(y - want) <= 1 && ofYear == null) ofYear = o;
        }
        // A film asked for by its year: a film of another year is another film.
        if ("movie".equals(type) && want > 0 && ofYear == null) return "";
        JSONObject best = ofYear != null ? ofYear : first;
        return best == null ? "" : path(best);
    }

    /**
     * Pure: as pick(), the answer's TMDB id and kind with its picture: {path, id, "tv" or "movie"}; null for none. A series'
     * id gives its episode's still (still()).
     */
    public static String[] pickWithId(JSONObject found, String type, String title, String year) {
        String p = pick(found, type, title, year);
        JSONArray r = found == null || p.isEmpty() ? null : found.optJSONArray("results");
        for (int k = 0; r != null && k < r.length(); k++) {
            JSONObject o = r.optJSONObject(k);
            if (o == null || !p.equals(path(o))) continue;
            String media = o.optString("media_type", "");
            if (media.isEmpty()) media = o.has("first_air_date") || o.has("name") && !o.has("title") ? "tv" : "movie";
            return new String[]{p, String.valueOf(o.optInt("id")), media};
        }
        return null;
    }

    /** Pure: an episode's own still from TMDB's season ({"episodes": [{episode_number, still_path}]}); "" for none. */
    public static String still(JSONObject season, int episode) {
        JSONArray eps = season == null || episode <= 0 ? null : season.optJSONArray("episodes");
        for (int i = 0; eps != null && i < eps.length(); i++) {
            JSONObject e = eps.optJSONObject(i);
            if (e == null || e.optInt("episode_number") != episode) continue;
            String s = e.optString("still_path", "");
            return s.equals("null") ? "" : s;
        }
        return "";
    }

    private static String path(JSONObject o) {
        String b = o.optString("backdrop_path", "");
        if (b.isEmpty() || b.equals("null")) b = o.optString("poster_path", "");
        return b.equals("null") ? "" : b;
    }
}
