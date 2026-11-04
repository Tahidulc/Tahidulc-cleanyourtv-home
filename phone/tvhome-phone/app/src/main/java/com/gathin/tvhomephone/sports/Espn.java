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
        g.clock = status.optDouble("clock", 0);
        g.period = status.optInt("period", 0);
        g.displayClock = status.optString("displayClock", "");
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
        /**
         * {minute, what ("Goal", "Yellow Card", "Substitution", "VAR"...), player, "home"/"away"/"", key, the second
         * player ("" when none: the one replaced for a substitution, the assist for a goal)}.
         */
        public final List<String[]> events = new ArrayList<>();
        /** How many of events came before half time; -1 when the break is not reached (or not told). */
        public int halfTimeAt = -1;
        /**
         * Each side's starters and bench, as P_* indexes them: {number, name, position, "1" starter / "0" bench, place in
         * the formation (1 the goalkeeper, "" or "0" for the bench), short name (the surname), minute on, minute off,
         * goals, cards ("Y", "R" or "YR"), own goals, ESPN's id for him}.
         */
        public final List<String[]> homeLineup = new ArrayList<>(), awayLineup = new ArrayList<>();
        public String homeFormation = "", awayFormation = "";
        /** The kits worn ("fcdd39"), "" when ESPN gives none: each side's shirt, then its second colour. */
        public String homeKit = "", awayKit = "", homeKitAlt = "", awayKitAlt = "";
        /** {label, home value, away value}: possession, shots, on target, corners, fouls. */
        public final List<String[]> stats = new ArrayList<>();
        /** Every number ESPN gives for each side ("saves", "passPct", "offsides"...): for the facts. */
        public final Map<String, String> homeNumbers = new HashMap<>(), awayNumbers = new HashMap<>();
        public String homeId = "", awayId = "";
        /** The competition's table as it stood: {team id, name, rank, points, played}. */
        public final List<String[]> table = new ArrayList<>();
        /** Each side's last five, oldest first: {"W"/"D"/"L", goals for, goals against, opponent, "@" or "vs", competition}. */
        public final List<String[]> homeLast = new ArrayList<>(), awayLast = new ArrayList<>();
        /** Earlier meetings, newest first: {year, home name, home goals, away name, away goals}. */
        public final List<String[]> meetings = new ArrayList<>();
        /**
         * ESPN's leaders for each side: {"home"/"away", what ("goalsLeaders", "totalShots", "saves"...), player, value as
         * given ("Matches: 3, Goals: 4" for the competition before the game, "7" for this game once it is on)}.
         */
        public final List<String[]> leaders = new ArrayList<>();
        public int attendance;
        public String referee = "";
        /**
         * Where things happened, oldest first (M_*): each shot and goal from where it was struck to where it went, and
         * each card where its foul was. Positions are 0 to 100 from the side's own point of view, attacking towards
         * x = 100; y = 0 its right touchline.
         */
        public final List<String[]> plays = new ArrayList<>();
    }

    /** Indexes into Summary.plays, and the kinds of play. */
    public static final int M_MINUTE = 0, M_KIND = 1, M_SIDE = 2, M_X = 3, M_Y = 4, M_X2 = 5, M_Y2 = 6, M_PLAYER = 7;
    public static final String GOAL = "goal", ON_TARGET = "on", OFF_TARGET = "off", BLOCKED = "blocked", YELLOW = "yellow", RED = "red";

    /** Indexes into a line-up's rows. */
    public static final int P_NUMBER = 0, P_NAME = 1, P_POSITION = 2, P_STARTER = 3, P_PLACE = 4, P_SHORT = 5, P_ON = 6,
            P_OFF = 7, P_GOALS = 8, P_CARDS = 9, P_OWN = 10, P_ID = 11;

    public static Summary summary(String slug, String id, long freshMs) {
        try {
            return summary(new JSONObject(cached(SITE + slug + "/summary?event=" + id, freshMs)), id);
        } catch (Exception ignored) {
            // the summary is not out yet
            return new Summary();
        }
    }

    /** A summary from ESPN's answer (as summary(slug, id) reads it). */
    static Summary summary(JSONObject root, String id) {
        Summary s = new Summary();
        try {
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
                String low = what.toLowerCase(Locale.ROOT);
                if (low.equals("halftime")) {
                    if (s.halfTimeAt < 0) s.halfTimeAt = s.events.size();
                    continue;
                }
                if (what.isEmpty() || low.equals("kickoff") || low.startsWith("end ") || low.startsWith("start ")
                        || low.contains("delay")) continue;
                JSONArray who = k.optJSONArray("participants");
                JSONObject athlete = who != null && who.length() > 0 ? who.getJSONObject(0).optJSONObject("athlete") : null;
                JSONObject second = who != null && who.length() > 1 ? who.getJSONObject(1).optJSONObject("athlete") : null;
                String minute = k.optJSONObject("clock") == null ? "" : k.getJSONObject("clock").optString("displayValue");
                JSONObject team = k.optJSONObject("team");
                String side = team == null ? "" : homeId.equals(team.optString("id")) ? "home" : "away";
                String player = athlete == null ? "" : athlete.optString("displayName");
                if (player.isEmpty()) player = k.optString("shortText", k.optString("text", ""));
                s.events.add(new String[]{minute, what, player, side, "E|" + id + "|" + k.optString("id", what + minute),
                        second == null ? "" : second.optString("displayName", "")});
            }
            JSONArray rosters = root.optJSONArray("rosters");
            for (int i = 0; rosters != null && i < rosters.length(); i++) {
                JSONObject r = rosters.getJSONObject(i);
                boolean isHome = "home".equals(r.optString("homeAway"));
                List<String[]> into = isHome ? s.homeLineup : s.awayLineup;
                JSONObject uniform = r.optJSONObject("uniform"), team = r.optJSONObject("team");
                String kit = uniform != null && !uniform.optString("color").isEmpty() ? uniform.optString("color")
                        : team == null ? "" : team.optString("color", "");
                String alt = uniform != null && !uniform.optString("alternateColor").isEmpty() ? uniform.optString("alternateColor")
                        : team == null ? "" : team.optString("alternateColor", "");
                if (isHome) {
                    s.homeFormation = r.optString("formation", "");
                    s.homeKit = kit;
                    s.homeKitAlt = alt;
                } else {
                    s.awayFormation = r.optString("formation", "");
                    s.awayKit = kit;
                    s.awayKitAlt = alt;
                }
                JSONArray players = r.optJSONArray("roster");
                for (int p = 0; players != null && p < players.length(); p++) into.add(player(players.getJSONObject(p)));
            }
            s.homeId = homeId;
            for (int i = 0; competitors != null && i < competitors.length(); i++) {
                JSONObject t = competitors.getJSONObject(i);
                if (!"home".equals(t.optString("homeAway"))) s.awayId = t.optString("id");
            }
            extras(root, s);
            plays(root, s, competitors);
            JSONObject box = root.optJSONObject("boxscore");
            JSONArray bt = box == null ? null : box.optJSONArray("teams");
            if (bt != null && bt.length() == 2) {
                JSONObject a = bt.getJSONObject(0), b = bt.getJSONObject(1);
                boolean aHome = homeId.equals(a.optJSONObject("team") == null ? "" : a.getJSONObject("team").optString("id"));
                JSONObject h = aHome ? a : b, w = aHome ? b : a;
                numbers(h, s.homeNumbers);
                numbers(w, s.awayNumbers);
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
            // what was read so far
        }
        return s;
    }

    private static void numbers(JSONObject team, Map<String, String> into) {
        JSONArray st = team.optJSONArray("statistics");
        for (int i = 0; st != null && i < st.length(); i++) {
            JSONObject x = st.optJSONObject(i);
            if (x != null && !x.optString("name").isEmpty()) into.put(x.optString("name"), x.optString("displayValue"));
        }
    }

    /** The table, both sides' last five, earlier meetings, leaders, the crowd and the referee, for the facts. */
    private static void extras(JSONObject root, Summary s) {
        JSONObject st = root.optJSONObject("standings");
        JSONArray groups = st == null ? null : st.optJSONArray("groups");
        for (int gi = 0; groups != null && gi < groups.length(); gi++) {
            JSONObject grp = groups.optJSONObject(gi);
            JSONObject inner = grp == null ? null : grp.optJSONObject("standings");
            JSONArray entries = inner == null ? null : inner.optJSONArray("entries");
            for (int i = 0; entries != null && i < entries.length(); i++) {
                JSONObject e = entries.optJSONObject(i);
                if (e == null) continue;
                Map<String, String> v = new HashMap<>();
                JSONArray stats = e.optJSONArray("stats");
                for (int k = 0; stats != null && k < stats.length(); k++) {
                    JSONObject x = stats.optJSONObject(k);
                    if (x != null) v.put(x.optString("name"), x.optString("displayValue"));
                }
                s.table.add(new String[]{e.optString("id"), e.optString("team"), v.getOrDefault("rank", String.valueOf(i + 1)),
                        v.getOrDefault("points", ""), v.getOrDefault("gamesPlayed", "")});
            }
        }
        JSONArray last = root.optJSONArray("lastFiveGames");
        for (int i = 0; last != null && i < last.length(); i++) {
            JSONObject t = last.optJSONObject(i);
            JSONObject team = t == null ? null : t.optJSONObject("team");
            if (team == null) continue;
            List<String[]> into = team.optString("id").equals(s.homeId) ? s.homeLast : team.optString("id").equals(s.awayId) ? s.awayLast : null;
            if (into == null) continue;
            JSONArray events = t.optJSONArray("events");
            for (int k = 0; events != null && k < events.length(); k++) {
                JSONObject e = events.optJSONObject(k);
                if (e == null || e.optString("gameResult").isEmpty()) continue;
                boolean away = "@".equals(e.optString("atVs"));
                String hs = e.optString("homeTeamScore"), as = e.optString("awayTeamScore");
                JSONObject opp = e.optJSONObject("opponent");
                into.add(new String[]{e.optString("gameResult"), away ? as : hs, away ? hs : as,
                        opp == null ? "" : opp.optString("displayName"), away ? "@" : "vs", e.optString("leagueName")});
            }
        }
        JSONArray series = root.optJSONArray("seasonseries");
        for (int i = 0; series != null && i < series.length(); i++) {
            JSONObject x = series.optJSONObject(i);
            JSONArray events = x == null ? null : x.optJSONArray("events");
            for (int k = 0; events != null && k < events.length(); k++) {
                JSONObject e = events.optJSONObject(k);
                if (e == null || !"post".equals(e.optJSONObject("statusType") == null ? "" : e.optJSONObject("statusType").optString("state"))) continue;
                JSONArray cs = e.optJSONArray("competitors");
                if (cs == null || cs.length() != 2) continue;
                JSONObject h = cs.optJSONObject(0), w = cs.optJSONObject(1);
                if (h == null || w == null) continue;
                if ("away".equals(h.optString("homeAway"))) { JSONObject t = h; h = w; w = t; }
                String date = e.optString("date");
                s.meetings.add(new String[]{date.length() >= 4 ? date.substring(0, 4) : "", name(h), h.optString("score"), name(w),
                        w.optString("score"), date});
            }
        }
        s.meetings.sort((a, b) -> b[5].compareTo(a[5]));
        JSONArray leaders = root.optJSONArray("leaders");
        for (int i = 0; leaders != null && i < leaders.length(); i++) {
            JSONObject t = leaders.optJSONObject(i);
            JSONObject team = t == null ? null : t.optJSONObject("team");
            if (team == null) continue;
            String side = team.optString("id").equals(s.homeId) ? "home" : team.optString("id").equals(s.awayId) ? "away" : "";
            JSONArray kinds = t.optJSONArray("leaders");
            for (int k = 0; kinds != null && k < kinds.length(); k++) {
                JSONObject kind = kinds.optJSONObject(k);
                JSONArray who = kind == null ? null : kind.optJSONArray("leaders");
                JSONObject first = who != null && who.length() > 0 ? who.optJSONObject(0) : null;
                JSONObject athlete = first == null ? null : first.optJSONObject("athlete");
                if (athlete == null || side.isEmpty()) continue;
                s.leaders.add(new String[]{side, kind.optString("name"), athlete.optString("displayName"), first.optString("displayValue")});
            }
        }
        JSONObject info = root.optJSONObject("gameInfo");
        if (info != null) {
            s.attendance = info.optInt("attendance", 0);
            JSONArray officials = info.optJSONArray("officials");
            JSONObject ref = officials != null && officials.length() > 0 ? officials.optJSONObject(0) : null;
            if (ref != null) s.referee = ref.optString("displayName", ref.optString("fullName", ""));
        }
    }

    /** The shots, goals and cards with their places on the pitch, from ESPN's commentary. */
    private static void plays(JSONObject root, Summary s, JSONArray competitors) {
        String homeName = "", awayName = "";
        for (int i = 0; competitors != null && i < competitors.length(); i++) {
            JSONObject t = competitors.optJSONObject(i);
            JSONObject team = t == null ? null : t.optJSONObject("team");
            if (team == null) continue;
            if ("home".equals(t.optString("homeAway"))) homeName = team.optString("displayName"); else awayName = team.optString("displayName");
        }
        JSONArray com = root.optJSONArray("commentary");
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; com != null && i < com.length(); i++) {
            JSONObject x = com.optJSONObject(i);
            JSONObject p = x == null ? null : x.optJSONObject("play");
            if (p != null) list.add(p);
        }
        for (int i = 0; i < list.size(); i++) {
            JSONObject p = list.get(i);
            JSONObject type = p.optJSONObject("type");
            String what = type == null ? "" : type.optString("text").toLowerCase(Locale.ROOT);
            String kind;
            if (what.contains("own goal")) continue;
            if (what.contains("goal") && !what.contains("kick") || what.contains("penalty - scored")) kind = GOAL;
            else if (what.contains("shot on target") || what.contains("penalty - saved")) kind = ON_TARGET;
            else if (what.contains("shot off target") || what.contains("woodwork") || what.contains("penalty - missed")) kind = OFF_TARGET;
            else if (what.contains("shot blocked")) kind = BLOCKED;
            else if (what.contains("yellow card")) kind = YELLOW;
            else if (what.contains("red card")) kind = RED;
            else continue;
            JSONObject team = p.optJSONObject("team");
            String teamName = team == null ? "" : team.optString("displayName");
            String side = teamName.equals(homeName) ? "home" : teamName.equals(awayName) ? "away" : "";
            if (side.isEmpty()) continue;
            JSONArray who = p.optJSONArray("participants");
            JSONObject a = who != null && who.length() > 0 ? who.optJSONObject(0) : null;
            JSONObject athlete = a == null ? null : a.optJSONObject("athlete");
            String player = athlete == null ? "" : athlete.optString("displayName");
            JSONObject clock = p.optJSONObject("clock");
            String minute = clock == null ? "" : clock.optString("displayValue");
            JSONObject at = p;
            if (!p.has("fieldPositionX")) {
                // A card has no place of its own: its foul's, the same minute and player.
                at = null;
                for (int k = i - 1; k >= 0 && k >= i - 8 && at == null; k--) {
                    JSONObject f = list.get(k);
                    JSONObject ft = f.optJSONObject("type");
                    JSONObject fc = f.optJSONObject("clock");
                    JSONArray fw = f.optJSONArray("participants");
                    JSONObject fa = fw != null && fw.length() > 0 && fw.optJSONObject(0) != null ? fw.optJSONObject(0).optJSONObject("athlete") : null;
                    if (ft != null && "foul".equalsIgnoreCase(ft.optString("text")) && fc != null && minute.equals(fc.optString("displayValue"))
                            && fa != null && player.equals(fa.optString("displayName")) && f.has("fieldPositionX")) at = f;
                }
                if (at == null) continue;
            }
            s.plays.add(new String[]{minute, kind, side, at.optString("fieldPositionX"), at.optString("fieldPositionY"),
                    at.optString("fieldPosition2X", ""), at.optString("fieldPosition2Y", ""), player});
        }
    }

    private static String name(JSONObject competitor) {
        JSONObject t = competitor.optJSONObject("team");
        return t == null ? "" : t.optString("shortDisplayName", t.optString("displayName", ""));
    }

    /** One player of a roster, as Summary's line-ups hold them (P_*). */
    static String[] player(JSONObject pl) {
        JSONObject a = pl.optJSONObject("athlete");
        JSONObject pos = pl.optJSONObject("position");
        String name = a == null ? "" : a.optString("displayName");
        String last = a == null ? "" : a.optString("lastName", "");
        String shortName = a == null ? "" : a.optString("shortName", "");
        // "A. Koita" to "Koita"; "Zini" and "Vinícius Júnior" (no surname given) as they are.
        if (last.isEmpty()) last = shortName.matches("^\\p{L}\\. .+") ? shortName.substring(3) : shortName.isEmpty() ? name : shortName;
        boolean starter = pl.optBoolean("starter");
        String on = "", off = "", cards = "";
        int goals = 0, own = 0;
        boolean cameOn = didSub(pl, "subbedIn"), wentOff = didSub(pl, "subbedOut");
        JSONArray plays = pl.optJSONArray("plays");
        for (int i = 0; plays != null && i < plays.length(); i++) {
            JSONObject x = plays.optJSONObject(i);
            if (x == null) continue;
            JSONObject clk = x.optJSONObject("clock");
            String clock = clk == null ? "" : clk.optString("displayValue", "");
            if (x.optBoolean("substitution")) {
                if (cameOn && on.isEmpty()) on = clock; else off = clock;
            }
            if (x.optBoolean("ownGoal")) own++;
            else if (x.optBoolean("didScore")) goals++;
            if (x.optBoolean("yellowCard") && !cards.contains("Y")) cards = "Y" + cards;
            if (x.optBoolean("redCard") && !cards.contains("R")) cards = cards + "R";
        }
        if (cameOn && on.isEmpty()) on = "?";
        if (wentOff && off.isEmpty()) off = "?";
        String place = pl.optString("formationPlace", "");
        return new String[]{pl.optString("jersey", ""), name, pos == null ? "" : pos.optString("abbreviation", ""),
                starter ? "1" : "0", starter ? place : "", last, on, off, String.valueOf(goals), cards, String.valueOf(own),
                a == null ? "" : a.optString("id", "")};
    }

    /** ESPN gives "subbedIn" as true or false, or as {"didSub": true}. */
    private static boolean didSub(JSONObject pl, String name) {
        Object v = pl.opt(name);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof JSONObject) return ((JSONObject) v).optBoolean("didSub");
        return false;
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
