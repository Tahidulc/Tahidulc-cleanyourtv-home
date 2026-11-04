package com.gathin.tvhomephone.sports;

import com.gathin.tvhomephone.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ESPN's public football feeds, as the TV reads them: each competition's scoreboard for a day, a game's summary (its
 * events, line-ups and stats), a league's table and its clubs. Off the main thread; short caches so the screens and
 * the alerts can ask often.
 */
public final class Espn {
    private Espn() {}

    private static final String SITE = "https://site.api.espn.com/apis/site/v2/sports/soccer/";

    /** The competitions read, {slug, short tag, name}: the TV's leagues, cups and England's internationals. */
    public static final String[][] LEAGUES = {
            {"eng.1", "PL", "Premier League"}, {"uefa.champions", "UCL", "Champions League"}, {"eng.fa", "FA CUP", "FA Cup"},
            {"eng.league_cup", "EFL CUP", "EFL Cup"}, {"eng.2", "CHAMP", "Championship"}, {"uefa.europa", "UEL", "Europa League"},
            {"uefa.europa.conf", "UECL", "Conference League"}, {"esp.1", "LALIGA", "LaLiga"}, {"ita.1", "SERIE A", "Serie A"},
            {"ger.1", "BUNDESLIGA", "Bundesliga"}, {"fra.1", "LIGUE 1", "Ligue 1"}, {"sco.1", "SPFL", "Scottish Premiership"},
            {"esp.copa_del_rey", "COPA", "Copa del Rey"}, {"esp.super_cup", "SUPERCOPA", "Supercopa"},
            {"uefa.nations", "UNL", "Nations League"}, {"fifa.worldq.uefa", "WCQ", "World Cup qualifying"},
            {"uefa.euroq", "EUROQ", "Euro qualifying"}, {"fifa.friendly", "FRIENDLY", "Friendlies"},
            {"fifa.world", "WORLD CUP", "World Cup"}, {"uefa.euro", "EURO", "Euro"}};

    /** The tables shown on Sports, {slug, name}, as the TV's table_leagues. */
    public static final String[][] TABLES = {
            {"eng.1", "Premier League"}, {"uefa.champions", "Champions League"}, {"eng.2", "Championship"},
            {"sco.1", "Scottish Premiership"}, {"esp.1", "LaLiga"}, {"ita.1", "Serie A"}, {"ger.1", "Bundesliga"},
            {"fra.1", "Ligue 1"}, {"uefa.europa", "Europa League"}, {"uefa.europa.conf", "Conference League"}};

    private static final Map<String, Object[]> CACHE = new HashMap<>();

    private static String cached(String url, long freshMs) throws Exception {
        synchronized (CACHE) {
            Object[] had = CACHE.get(url);
            if (had != null && System.currentTimeMillis() - (Long) had[0] < freshMs) return (String) had[1];
        }
        String body = Net.get(url);
        synchronized (CACHE) {
            CACHE.put(url, new Object[]{System.currentTimeMillis(), body});
            if (CACHE.size() > 200) CACHE.clear();
        }
        return body;
    }

    public static String tag(String slug) {
        for (String[] l : LEAGUES) if (l[0].equals(slug)) return l[1];
        return slug.toUpperCase(Locale.ROOT);
    }

    public static String name(String slug) {
        for (String[] l : LEAGUES) if (l[0].equals(slug)) return l[2];
        return slug;
    }

    /** One competition's games on one day (in the phone's own time zone); empty when ESPN does not answer. */
    public static List<Game> scoreboard(String slug, LocalDate day, long freshMs) {
        List<Game> out = new ArrayList<>();
        try {
            String url = SITE + slug + "/scoreboard?dates=" + day.format(DateTimeFormatter.BASIC_ISO_DATE);
            JSONObject root = new JSONObject(cached(url, freshMs));
            String logo = null, leagueName = name(slug);
            JSONArray leagues = root.optJSONArray("leagues");
            if (leagues != null && leagues.length() > 0) {
                // The competition's logo for dark grounds (the TV's choice), else its default one.
                logo = Competitions.learn(slug, leagues.getJSONObject(0).optJSONArray("logos"));
            }
            if (logo == null) logo = Competitions.logo(slug);
            JSONArray events = root.optJSONArray("events");
            ZoneId zone = ZoneId.systemDefault();
            for (int i = 0; events != null && i < events.length(); i++) {
                Game g = parse(events.getJSONObject(i), zone);
                if (g == null) continue;
                g.slug = slug;
                g.league = tag(slug);
                g.leagueName = leagueName;
                g.leagueLogo = logo;
                out.add(g);
            }
        } catch (Exception ignored) {
            // nothing from ESPN this time
        }
        return out;
    }

    /** One game off ESPN's scoreboard (the TV's ScoresTicker.parse). Pure: for the tests too. */
    public static Game parse(JSONObject e, ZoneId zone) throws Exception {
        JSONObject c = e.getJSONArray("competitions").getJSONObject(0);
        JSONObject status = c.getJSONObject("status");
        JSONObject type = status.getJSONObject("type");
        JSONObject home = null, away = null;
        JSONArray teams = c.getJSONArray("competitors");
        for (int t = 0; t < teams.length(); t++) {
            JSONObject team = teams.getJSONObject(t);
            if ("home".equals(team.optString("homeAway"))) home = team; else away = team;
        }
        if (home == null || away == null) return null;
        Game g = new Game();
        g.id = e.optString("id");
        JSONObject ht = home.getJSONObject("team"), at = away.getJSONObject("team");
        g.homeId = ht.optString("id");
        g.awayId = at.optString("id");
        g.home = ht.optString("abbreviation");
        g.away = at.optString("abbreviation");
        g.homeName = ht.optString("shortDisplayName", ht.optString("displayName", g.home));
        g.awayName = at.optString("shortDisplayName", at.optString("displayName", g.away));
        g.homeLogo = ht.optString("logo", null);
        g.awayLogo = at.optString("logo", null);
        g.homeFull = ht.optString("displayName", "");
        g.awayFull = at.optString("displayName", "");
        g.homeForm = form(home.optString("form", ""));
        g.awayForm = form(away.optString("form", ""));
        g.homeColor = ht.optString("color", "");
        g.awayColor = at.optString("color", "");
        JSONObject ground = c.optJSONObject("venue");
        if (ground != null && !ground.optString("fullName").isEmpty()) g.venue = ground.optString("fullName");
        JSONArray details = c.optJSONArray("details");
        for (int d = 0; details != null && d < details.length(); d++) {
            JSONObject x = details.getJSONObject(d);
            JSONObject xt = x.optJSONObject("type");
            boolean red = x.optBoolean("redCard") || (xt != null && xt.optString("text").toLowerCase(Locale.ROOT).contains("red card"));
            if (!x.optBoolean("scoringPlay") && !red) continue;
            JSONArray who = x.optJSONArray("athletesInvolved");
            String player = who != null && who.length() > 0 ? who.getJSONObject(0).optString("displayName") : "";
            String minute = x.optJSONObject("clock") != null ? x.getJSONObject("clock").optString("displayValue") : "";
            JSONObject team = x.optJSONObject("team");
            String side = g.homeId.equals(team != null ? team.optString("id") : "") ? "home" : "away";
            String kind = x.optBoolean("ownGoal") ? "og" : x.optBoolean("penaltyKick") ? "pen" : "";
            if (red) g.reds.add(new String[]{"R|" + g.id + "|" + minute + "|" + player, player, minute, side});
            else g.goals.add(new String[]{"G|" + g.id + "|" + minute + "|" + player, player, minute, side, kind});
        }
        g.homeScore = parseInt(home.optString("score", "0"));
        g.awayScore = parseInt(away.optString("score", "0"));
        ZonedDateTime when = OffsetDateTime.parse(e.getString("date")).toInstant().atZone(zone);
        g.kickoff = when.toInstant().toEpochMilli();
        String state = type.optString("state");
        if ("in".equals(state)) {
            g.state = Game.LIVE;
            g.detail = type.optString("shortDetail", "LIVE");
            g.halftime = type.optString("name").contains("HALFTIME");
        } else if ("pre".equals(state)) {
            g.state = Game.PRE;
            g.detail = when.format(DateTimeFormatter.ofPattern("HH:mm"));
        } else {
            g.state = Game.POST;
            g.detail = type.optString("shortDetail", "FT");
        }
        JSONArray geo = c.optJSONArray("geoBroadcasts");
        for (int i = 0; geo != null && i < geo.length(); i++) {
            JSONObject media = geo.getJSONObject(i).optJSONObject("media");
            String n = media == null ? "" : media.optString("shortName", "");
            if (!n.isEmpty() && !g.broadcasts.contains(n)) g.broadcasts.add(n);
        }
        return g;
    }

    /** ESPN's form ("DWLLW"), kept to its W, D and L letters, the last five at most. */
    static String form(String f) {
        String t = f == null ? "" : f.toUpperCase(Locale.ROOT).replaceAll("[^WDL]", "");
        return t.length() > 5 ? t.substring(t.length() - 5) : t;
    }

    static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    // ---- a game's summary ----------------------------------------------------------------------------------------

    /** A game's events, line-ups and stats (ESPN's summary). */
    public static final class Summary {
        /** {minute, what ("Goal", "Yellow Card", "Substitution", "VAR"...), player, "home"/"away"/"", key}. */
        public final List<String[]> events = new ArrayList<>();
        /** Each side's starters and bench: {number, name, position, "1" starter / "0" bench}. */
        public final List<String[]> homeLineup = new ArrayList<>(), awayLineup = new ArrayList<>();
        public String homeFormation = "", awayFormation = "";
        /** {label, home value, away value}: possession, shots, on target, corners, fouls. */
        public final List<String[]> stats = new ArrayList<>();
    }

    public static Summary summary(String slug, String id, long freshMs) {
        Summary s = new Summary();
        try {
            JSONObject root = new JSONObject(cached(SITE + slug + "/summary?event=" + id, freshMs));
            String homeId = "";
            JSONObject header = root.optJSONObject("header");
            JSONArray comps = header == null ? null : header.optJSONArray("competitions");
            JSONArray competitors = comps != null && comps.length() > 0 ? comps.getJSONObject(0).optJSONArray("competitors") : null;
            for (int i = 0; competitors != null && i < competitors.length(); i++) {
                JSONObject t = competitors.getJSONObject(i);
                if ("home".equals(t.optString("homeAway"))) homeId = t.optString("id");
            }
            JSONArray key = root.optJSONArray("keyEvents");
            for (int i = 0; key != null && i < key.length(); i++) {
                JSONObject k = key.getJSONObject(i);
                String what = k.optJSONObject("type") == null ? "" : k.getJSONObject("type").optString("text");
                if (what.isEmpty() || what.equalsIgnoreCase("Kickoff") || what.toLowerCase(Locale.ROOT).startsWith("end ")
                        || what.equalsIgnoreCase("Halftime") || what.equalsIgnoreCase("Start 2nd Half")) continue;
                JSONArray who = k.optJSONArray("participants");
                JSONObject athlete = who != null && who.length() > 0 ? who.getJSONObject(0).optJSONObject("athlete") : null;
                String minute = k.optJSONObject("clock") == null ? "" : k.getJSONObject("clock").optString("displayValue");
                JSONObject team = k.optJSONObject("team");
                String side = team == null ? "" : homeId.equals(team.optString("id")) ? "home" : "away";
                String player = athlete == null ? "" : athlete.optString("displayName");
                if (player.isEmpty()) player = k.optString("shortText", k.optString("text", ""));
                s.events.add(new String[]{minute, what, player, side, "E|" + id + "|" + k.optString("id", what + minute)});
            }
            JSONArray rosters = root.optJSONArray("rosters");
            for (int i = 0; rosters != null && i < rosters.length(); i++) {
                JSONObject r = rosters.getJSONObject(i);
                boolean isHome = "home".equals(r.optString("homeAway"));
                List<String[]> into = isHome ? s.homeLineup : s.awayLineup;
                if (isHome) s.homeFormation = r.optString("formation", ""); else s.awayFormation = r.optString("formation", "");
                JSONArray players = r.optJSONArray("roster");
                for (int p = 0; players != null && p < players.length(); p++) {
                    JSONObject pl = players.getJSONObject(p);
                    JSONObject a = pl.optJSONObject("athlete");
                    JSONObject pos = pl.optJSONObject("position");
                    into.add(new String[]{pl.optString("jersey", ""), a == null ? "" : a.optString("displayName"),
                            pos == null ? "" : pos.optString("abbreviation", ""), pl.optBoolean("starter") ? "1" : "0"});
                }
            }
            JSONObject box = root.optJSONObject("boxscore");
            JSONArray bt = box == null ? null : box.optJSONArray("teams");
            if (bt != null && bt.length() == 2) {
                JSONObject a = bt.getJSONObject(0), b = bt.getJSONObject(1);
                boolean aHome = homeId.equals(a.optJSONObject("team") == null ? "" : a.getJSONObject("team").optString("id"));
                JSONObject h = aHome ? a : b, w = aHome ? b : a;
                String[][] want = {{"possessionPct", "Possession"}, {"totalShots", "Shots"}, {"shotsOnTarget", "On target"},
                        {"wonCorners", "Corners"}, {"foulsCommitted", "Fouls"}, {"yellowCards", "Yellow cards"}};
                for (String[] wnt : want) {
                    String hv = stat(h, wnt[0]), av = stat(w, wnt[0]);
                    if (hv == null || av == null) continue;
                    if (wnt[0].equals("possessionPct")) { hv = hv + "%"; av = av + "%"; }
                    s.stats.add(new String[]{wnt[1], hv, av});
                }
            }
        } catch (Exception ignored) {
            // the summary is not out yet
        }
        return s;
    }

    private static String stat(JSONObject team, String name) {
        JSONArray st = team.optJSONArray("statistics");
        for (int i = 0; st != null && i < st.length(); i++) {
            JSONObject x = st.optJSONObject(i);
            if (x != null && name.equals(x.optString("name"))) return x.optString("displayValue");
        }
        return null;
    }

    // ---- tables and clubs ----------------------------------------------------------------------------------------

    /**
     * One row of a table: {rank, team id, name, logo, played, goal difference, points, group or "", won, drawn, lost}
     * (ESPN's stats "wins", "ties" and "losses"; "" when not given).
     */
    public static List<String[]> table(String slug) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(cached("https://site.api.espn.com/apis/v2/sports/soccer/" + slug + "/standings", 10 * 60_000L));
            JSONArray children = root.optJSONArray("children");
            if (children == null) children = new JSONArray().put(root);
            for (int c = 0; c < children.length(); c++) {
                JSONObject child = children.getJSONObject(c);
                String group = children.length() > 1 ? child.optString("name", "") : "";
                JSONObject st = child.optJSONObject("standings");
                JSONArray entries = st == null ? null : st.optJSONArray("entries");
                for (int i = 0; entries != null && i < entries.length(); i++) {
                    JSONObject en = entries.getJSONObject(i);
                    JSONObject t = en.getJSONObject("team");
                    JSONArray logos = t.optJSONArray("logos");
                    String logo = logos != null && logos.length() > 0 ? logos.getJSONObject(0).optString("href") : "";
                    Map<String, String> v = new HashMap<>();
                    JSONArray stats = en.optJSONArray("stats");
                    for (int k = 0; stats != null && k < stats.length(); k++) {
                        JSONObject x = stats.getJSONObject(k);
                        v.put(x.optString("name"), x.optString("displayValue"));
                    }
                    out.add(new String[]{v.getOrDefault("rank", String.valueOf(i + 1)), t.optString("id"),
                            t.optString("shortDisplayName", t.optString("displayName")), logo, v.getOrDefault("gamesPlayed", ""),
                            v.getOrDefault("pointDifferential", ""), v.getOrDefault("points", ""), group,
                            v.getOrDefault("wins", ""), v.getOrDefault("ties", ""), v.getOrDefault("losses", "")});
                }
            }
            out.sort((a, b) -> a[7].equals(b[7]) ? Integer.compare(parseInt(a[0]), parseInt(b[0])) : a[7].compareTo(b[7]));
        } catch (Exception ignored) {
            // no table this time
        }
        return out;
    }

    /** A competition's clubs: {id, name, logo}. */
    public static List<String[]> teams(String slug) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(cached(SITE + slug + "/teams", 24 * 3600_000L));
            JSONArray leagues = root.getJSONArray("sports").getJSONObject(0).getJSONArray("leagues");
            JSONArray teams = leagues.getJSONObject(0).getJSONArray("teams");
            for (int i = 0; i < teams.length(); i++) {
                JSONObject t = teams.getJSONObject(i).getJSONObject("team");
                JSONArray logos = t.optJSONArray("logos");
                out.add(new String[]{t.optString("id"), t.optString("displayName"),
                        logos != null && logos.length() > 0 ? logos.getJSONObject(0).optString("href") : ""});
            }
        } catch (Exception ignored) {
            // none this time
        }
        return out;
    }

    /** A club's crest by ESPN id (the TV's own URLs). */
    public static String crest(String teamId) {
        return "https://a.espncdn.com/i/teamlogos/soccer/500/" + teamId + ".png";
    }
}
