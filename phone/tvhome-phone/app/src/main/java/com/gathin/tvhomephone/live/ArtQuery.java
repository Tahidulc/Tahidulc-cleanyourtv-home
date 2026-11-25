package com.gathin.tvhomephone.live;

import com.gathin.broadcast.PicQuery;
import com.gathin.broadcast.PicSources;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The wider search for a programme the guide and Sky give no picture (pure, for the tests): TMDB by its title as the
 * guide's words allow (TileKind.lookupTitle: "Film: Verity (2025)" is "Verity"), a film looked for as a film with its
 * year (from the title or the description's start), a series as a series (an episode in the words, a kids' channel, or a
 * sports or news channel's own programme: a magazine, a talk show), the rest as either; nothing looked for that names no
 * programme of its own (PicQuery.worth, shared with Homefield TV: a game, a live event, a bulletin, "Close", "To Be
 * Announced", the channel's own name): those take their game's, their kind's or the channel's own picture. Only a
 * result that is the same programme counts (PicQuery.same: a guide's "Verity" is never TMDB's "Verity's Secret"), the
 * film of that year first.
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
        boolean sports = TileKind.sportsChannel(channel, group), news = TileKind.newsChannel(channel, group);
        String kind = sports ? PicSources.SPORT : news ? PicSources.NEWS : PicSources.kindOf(channel, group, title, desc, "");
        if (sports) {
            String k = PicSources.kindOf(channel, group, title, desc, "");
            if (k != null) kind = k;
        }
        if (!PicQuery.worth(title, channel, kind)) return null;
        String t = TileKind.lookupTitle(title);
        String low = norm(t);
        if (low.length() < 2 || GENERIC.matcher(t.toLowerCase(Locale.ROOT).trim()).matches()) return null;
        if (channel != null && low.equals(norm(channel))) return null;
        // A sports or news channel's own programme (not a game or a bulletin): a series by its name.
        if (sports || news) return new ArtQuery(TV, t, "");
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
        return PicQuery.pick(found, type, title, year);
    }

    /** As pick(), with the answer's TMDB id and kind: {path, id, "tv" or "movie"} (a series' gives its episodes' stills); null for none. */
    String[] pickWithId(JSONObject found) {
        return PicQuery.pickWithId(found, type, title, year);
    }

    static boolean same(String a, String b) {
        return PicQuery.same(a, b);
    }

    /** Pure: a title as it is compared: no accents or case, "&" as "and", no leading "the", letters and digits only. */
    static String norm(String s) {
        return PicQuery.norm(s);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }
}
