package com.gathin.homefieldtv.sports;

import com.gathin.broadcast.SportBoard;
import com.gathin.broadcast.SportFeeds;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sports beyond football on the Sports page, from ESPN's public scoreboards as the rest of the app reads them: the
 * NFL's week and the NBA's night (each team's logo, the score, how far on), the PGA Tour's and the DP World Tour's
 * leaderboards (each player's flag), the tennis tours' matches and UFC's next card (through SportFeeds, as the player's
 * boards read them); and each starred club's next games in every competition. Off the main thread; ESPN asked again
 * after a minute while anything is live, ten minutes otherwise. A feed that fails gives nothing.
 */
public final class MoreSports {
    private MoreSports() {}

    private static final String SITE = "https://site.api.espn.com/apis/site/v2/sports/";
    public static final String PGA_LOGO = "https://a.espncdn.com/combiner/i?img=/i/teamlogos/leagues/500/pgatour.png";
    public static final String DP_WORLD_LOGO = "https://a.espncdn.com/combiner/i?img=/i/espn/teamlogos/500/european_tour.png";

    /** One game of a team sport (the NFL, the NBA): its teams with their logos, the score, how far on, when. */
    public static final class Match {
        public String id = "", league = "", leagueLogo = "";
        public String home = "", away = "", homeShort = "", awayShort = "", homeLogo = "", awayLogo = "";
        public String homeScore = "", awayScore = "", homeRecord = "", awayRecord = "";
        /** pre, in or post, as ESPN has them; its short detail ("Q3 4:12", "Final", "Halftime"). */
        public String state = "pre", detail = "";
        public long start;
        public boolean homeWin, awayWin;
        /** The British channel it is on, when Sky's guide lists it. */
        public String channel;

        public boolean live() {
            return "in".equals(state);
        }

        public boolean over() {
            return "post".equals(state);
        }
    }

    /** A golf tournament's leaderboard: its tour, its name, how far on, and the leaders. */
    public static final class Leaderboard {
        public String tour = "", logo = "", event = "", state = "pre", detail = "";
        /** {position, name, flag, to par, today's round or the round scores}, the leaders first. */
        public final List<String[]> rows = new ArrayList<>();
        public String channel;
    }

    // ---- reading ----------------------------------------------------------------------------------------------------

    private static final Map<String, Object[]> CACHE = new HashMap<>();

    private static JSONObject read(String path, long freshMs) {
        String url = path.startsWith("http") ? path : SITE + path;
        synchronized (CACHE) {
            Object[] had = CACHE.get(url);
            if (had != null && System.currentTimeMillis() - (Long) had[0] < freshMs) return (JSONObject) had[1];
        }
        try {
            JSONObject root = new JSONObject(Net.get(url));
            synchronized (CACHE) {
                if (CACHE.size() > 40) CACHE.clear();
                CACHE.put(url, new Object[]{System.currentTimeMillis(), root});
            }
            return root;
        } catch (Exception e) {
            synchronized (CACHE) {
                Object[] had = CACHE.get(url);
                return had == null ? null : (JSONObject) had[1];
            }
        }
    }

    /** Off the main thread: this week's NFL games. */
    public static List<Match> nfl() {
        return matches(read("football/nfl/scoreboard", 60_000L));
    }

    /** Off the main thread: tonight's NBA games (the American day's). */
    public static List<Match> nba() {
        return matches(read("basketball/nba/scoreboard", 60_000L));
    }

    /** Off the main thread: the PGA Tour's tournament now (or last), its leaders. */
    public static Leaderboard pga(int max) {
        Leaderboard b = leaderboard(read("golf/pga/scoreboard", 5 * 60_000L), max);
        if (b != null) b.logo = PGA_LOGO;
        return b;
    }

    /** Off the main thread: the DP World Tour's tournament now (or last), its leaders. */
    public static Leaderboard dpWorld(int max) {
        Leaderboard b = leaderboard(read("golf/eur/scoreboard", 5 * 60_000L), max);
        if (b != null) b.logo = DP_WORLD_LOGO;
        return b;
    }

    /** Off the main thread: the ATP's or the WTA's singles matches this week (SportFeeds'), by start. */
    public static List<SportBoard> tennis(String tour) {
        try {
            List<SportBoard> out = SportFeeds.tennis(SportFeeds.fetch("tennis/" + tour.toLowerCase(java.util.Locale.ROOT)), tour);
            out.sort((a, b) -> Long.compare(a.start, b.start));
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Off the main thread: UFC's next (or current) card, the main event first. */
    public static List<SportBoard> ufc() {
        try {
            List<SportBoard> out = SportFeeds.ufc(SportFeeds.fetch("mma/ufc"));
            List<SportBoard> card = new ArrayList<>();
            for (int i = out.size() - 1; i >= 0; i--) card.add(out.get(i));
            return card;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Off the main thread: a club's next games in every competition (ESPN's team schedule), soonest first. */
    public static List<Game> nextGames(String teamId, int max) {
        List<Game> out = fixtures(read("soccer/all/teams/" + teamId + "/schedule?fixture=true", 15 * 60_000L), ZoneId.systemDefault());
        while (out.size() > max) out.remove(out.size() - 1);
        return out;
    }

    // ---- pure -------------------------------------------------------------------------------------------------------

    /** Pure: ESPN's scoreboard of a team sport as its games: live first, then those to come, then the finished. */
    static List<Match> matches(JSONObject root) {
        List<Match> out = new ArrayList<>();
        if (root == null) return out;
        String league = "", logo = "";
        JSONArray leagues = root.optJSONArray("leagues");
        if (leagues != null && leagues.length() > 0) {
            JSONObject l = leagues.optJSONObject(0);
            if (l != null) {
                league = l.optString("abbreviation", l.optString("name"));
                JSONArray logos = l.optJSONArray("logos");
                if (logos != null && logos.length() > 0) logo = logos.optJSONObject(0).optString("href");
            }
        }
        JSONArray events = root.optJSONArray("events");
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            JSONArray comps = e == null ? null : e.optJSONArray("competitions");
            JSONObject c = comps == null || comps.length() == 0 ? null : comps.optJSONObject(0);
            if (c == null) continue;
            Match m = new Match();
            m.id = e.optString("id");
            m.league = league;
            m.leagueLogo = logo;
            m.start = time(e.optString("date"));
            JSONObject st = c.optJSONObject("status");
            JSONObject type = st == null ? null : st.optJSONObject("type");
            if (type == null) type = e.optJSONObject("status") == null ? null : e.optJSONObject("status").optJSONObject("type");
            m.state = type == null ? "pre" : type.optString("state", "pre");
            m.detail = type == null ? "" : type.optString("shortDetail", type.optString("detail"));
            JSONArray cs = c.optJSONArray("competitors");
            for (int k = 0; cs != null && k < cs.length(); k++) {
                JSONObject x = cs.optJSONObject(k);
                if (x == null) continue;
                JSONObject t = x.optJSONObject("team");
                if (t == null) continue;
                boolean home = "home".equals(x.optString("homeAway"));
                String name = t.optString("displayName"), shortName = t.optString("shortDisplayName", name);
                String logoUrl = t.optString("logo", "");
                String score = x.optString("score", "");
                String record = "";
                JSONArray recs = x.optJSONArray("records");
                if (recs != null && recs.length() > 0) record = recs.optJSONObject(0).optString("summary", "");
                if (home) {
                    m.home = name;
                    m.homeShort = shortName;
                    m.homeLogo = logoUrl;
                    m.homeScore = score;
                    m.homeWin = x.optBoolean("winner");
                    m.homeRecord = record;
                } else {
                    m.away = name;
                    m.awayShort = shortName;
                    m.awayLogo = logoUrl;
                    m.awayScore = score;
                    m.awayWin = x.optBoolean("winner");
                    m.awayRecord = record;
                }
            }
            if (m.home.isEmpty() || m.away.isEmpty()) continue;
            out.add(m);
        }
        out.sort((a, b) -> {
            int ra = rank(a.state), rb = rank(b.state);
            if (ra != rb) return Integer.compare(ra, rb);
            return "post".equals(a.state) ? Long.compare(b.start, a.start) : Long.compare(a.start, b.start);
        });
        return out;
    }

    private static int rank(String state) {
        return "in".equals(state) ? 0 : "pre".equals(state) ? 1 : 2;
    }

    /** Pure: a golf scoreboard's first tournament as a leaderboard of at most max, or null for none. */
    static Leaderboard leaderboard(JSONObject root, int max) {
        if (root == null) return null;
        JSONArray events = root.optJSONArray("events");
        JSONObject e = events == null || events.length() == 0 ? null : events.optJSONObject(0);
        if (e == null) return null;
        Leaderboard b = new Leaderboard();
        JSONArray leagues = root.optJSONArray("leagues");
        if (leagues != null && leagues.length() > 0) b.tour = leagues.optJSONObject(0).optString("name");
        b.event = e.optString("name", e.optString("shortName"));
        JSONObject st = e.optJSONObject("status");
        JSONObject type = st == null ? null : st.optJSONObject("type");
        b.state = type == null ? "pre" : type.optString("state", "pre");
        b.detail = type == null ? "" : type.optString("shortDetail", type.optString("detail", ""));
        JSONArray comps = e.optJSONArray("competitions");
        JSONObject c = comps == null || comps.length() == 0 ? null : comps.optJSONObject(0);
        JSONArray cs = c == null ? null : c.optJSONArray("competitors");
        List<JSONObject> players = new ArrayList<>();
        for (int i = 0; cs != null && i < cs.length(); i++) if (cs.optJSONObject(i) != null) players.add(cs.optJSONObject(i));
        players.sort((x, y) -> Integer.compare(x.optInt("order", 999), y.optInt("order", 999)));
        String lastScore = null;
        int lastPos = 0;
        for (int i = 0; i < players.size() && b.rows.size() < max; i++) {
            JSONObject x = players.get(i);
            JSONObject a = x.optJSONObject("athlete");
            if (a == null) continue;
            JSONObject flag = a.optJSONObject("flag");
            String score = x.optString("score", "");
            // Tied players share their place ("T3"), as a leaderboard writes them.
            int pos = score.equals(lastScore) ? lastPos : i + 1;
            boolean tied = score.equals(lastScore) || (i + 1 < players.size() && score.equals(players.get(i + 1).optString("score", "")));
            lastScore = score;
            lastPos = pos;
            JSONArray rounds = x.optJSONArray("linescores");
            StringBuilder r = new StringBuilder();
            for (int k = 0; rounds != null && k < rounds.length(); k++) {
                String v = rounds.optJSONObject(k) == null ? "" : rounds.optJSONObject(k).optString("displayValue", "");
                if (v.isEmpty()) continue;
                if (r.length() > 0) r.append(' ');
                r.append(v);
            }
            b.rows.add(new String[]{(tied ? "T" : "") + pos, a.optString("displayName"), flag == null ? "" : flag.optString("href", ""),
                    score, r.toString()});
        }
        return b.rows.isEmpty() && b.event.isEmpty() ? null : b;
    }

    /** Pure: ESPN's team schedule (fixtures) as games still to come or on now, soonest first. */
    static List<Game> fixtures(JSONObject root, ZoneId zone) {
        List<Game> out = new ArrayList<>();
        JSONArray events = root == null ? null : root.optJSONArray("events");
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            try {
                // The schedule gives each team its logos as a list, the scoreboard one logo: made the scoreboard's.
                JSONArray comps = e.optJSONArray("competitions");
                JSONObject c = comps == null || comps.length() == 0 ? null : comps.optJSONObject(0);
                JSONArray cs = c == null ? null : c.optJSONArray("competitors");
                for (int k = 0; cs != null && k < cs.length(); k++) {
                    JSONObject x = cs.optJSONObject(k);
                    JSONObject t = x == null ? null : x.optJSONObject("team");
                    if (t == null) continue;
                    JSONArray logos = t.optJSONArray("logos");
                    if (!t.has("logo") && logos != null && logos.length() > 0) t.put("logo", logos.optJSONObject(0).optString("href"));
                    Object score = x.opt("score");
                    if (score instanceof JSONObject) x.put("score", ((JSONObject) score).optString("displayValue", "0"));
                }
                Game g = Espn.parse(e, zone);
                if (g == null || g.state == Game.POST) continue;
                JSONObject league = e.optJSONObject("league");
                if (league != null) {
                    g.slug = league.optString("slug", "");
                    g.league = Espn.tag(g.slug);
                    String named = Espn.name(g.slug);
                    g.leagueName = named.equals(g.slug) ? league.optString("abbreviation", league.optString("name")) : named;
                    g.leagueLogo = Competitions.logo(g.slug);
                }
                out.add(g);
            } catch (Exception ignored) {
                // that one left out
            }
        }
        out.sort((a, b) -> Long.compare(a.kickoff, b.kickoff));
        return out;
    }

    static long time(String iso) {
        try {
            return OffsetDateTime.parse(iso.length() == 17 ? iso.replace("Z", ":00Z") : iso).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }
}
