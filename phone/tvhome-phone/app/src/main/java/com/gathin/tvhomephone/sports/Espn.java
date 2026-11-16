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
        g.homeAlt = ht.optString("alternateColor", "");
        g.awayAlt = at.optString("alternateColor", "");
        // The kits they wear in this game where ESPN's line-ups have named them (Worn), else the clubs' colours, the
        // away side's second when they clash.
        wornOn(g);
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
            // ESPN's id for him: his shirt number from the line-ups (Shirts), for a goal's shirt turning to his name
            String athlete = who != null && who.length() > 0 ? who.getJSONObject(0).optString("id", "") : "";
            String minute = x.optJSONObject("clock") != null ? x.getJSONObject("clock").optString("displayValue") : "";
            JSONObject team = x.optJSONObject("team");
            String side = g.homeId.equals(team != null ? team.optString("id") : "") ? "home" : "away";
            String kind = x.optBoolean("ownGoal") ? "og" : x.optBoolean("penaltyKick") ? "pen" : "";
            if (red) g.reds.add(new String[]{"R|" + g.id + "|" + minute + "|" + player, player, minute, side});
            else g.goals.add(new String[]{"G|" + g.id + "|" + minute + "|" + player, player, minute, side, kind, athlete});
        }
        g.homeScore = parseInt(home.optString("score", "0"));
        g.awayScore = parseInt(away.optString("score", "0"));
        ZonedDateTime when = OffsetDateTime.parse(e.getString("date")).toInstant().atZone(zone);
        g.kickoff = when.toInstant().toEpochMilli();
        g.period = status.optInt("period", 0);
        g.displayClock = status.optString("displayClock", "");
        // The minute's start, not ESPN's minute being played (a minute ahead of the broadcasters' clocks).
        g.clock = GameClock.minuteStart(status.optDouble("clock", 0), g.displayClock);
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
        /** The kits ESPN's line-ups name for the game, {home, away} (either null), as Worn reads them. */
        public com.gathin.broadcast.Worn.Kit[] worn = new com.gathin.broadcast.Worn.Kit[2];
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
        /** ESPN's standings as given (its groups' entries with every figure), for the table as it stands (MatchStats); null without. */
        public JSONObject standings;
        /** Each side's last five, oldest first: {"W"/"D"/"L", goals for, goals against, opponent, "@" or "vs", competition}. */
        public final List<String[]> homeLast = new ArrayList<>(), awayLast = new ArrayList<>();
        /** Earlier meetings, newest first: {year, home name, home goals, away name, away goals, date, ESPN's game id, home id, away id}. */
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
        /** The added time put up in each half (and each half of extra time), in minutes; -1 until it is. */
        public final int[] added = {-1, -1, -1, -1};
        /** Each side's chance of winning as the prices before kick-off put them ("48%"); "" without prices. */
        public String winHome = "", winAway = "";
        /** The prices before kick-off as chances, {home, draw, away}, the three made 1; null without prices. */
        public double[] chances;
        /** A penalty shootout's kicks so far, each side's in order: {taker, "1" scored or "0" not}. */
        public final List<String[]> homePens = new ArrayList<>(), awayPens = new ArrayList<>();
        /** The commentary's lines about VAR and those round them ({text, minute, sequence}, oldest first): VAR explained's. */
        public final List<String[]> varLines = new ArrayList<>();
        /** The commentary's lines about penalties, and its latest line, {text, minute, sequence}, oldest first (Penalty). */
        public final List<String[]> penaltyLines = new ArrayList<>();
    }

    /** Indexes into Summary.plays, and the kinds of play. */
    public static final int M_MINUTE = 0, M_KIND = 1, M_SIDE = 2, M_X = 3, M_Y = 4, M_X2 = 5, M_Y2 = 6, M_PLAYER = 7;
    public static final String GOAL = "goal", ON_TARGET = "on", OFF_TARGET = "off", BLOCKED = "blocked", YELLOW = "yellow", RED = "red";

    /** Indexes into a line-up's rows. */
    public static final int P_NUMBER = 0, P_NAME = 1, P_POSITION = 2, P_STARTER = 3, P_PLACE = 4, P_SHORT = 5, P_ON = 6,
            P_OFF = 7, P_GOALS = 8, P_CARDS = 9, P_OWN = 10, P_ID = 11, P_RATING = 12;

    /** The game coloured by the kits named for it (Worn), else its clubs' colours with the away side's second on a clash. */
    public static void wornOn(Game g) {
        int[] kit = com.gathin.broadcast.Worn.pick(com.gathin.broadcast.Worn.get(g.id), com.gathin.broadcast.Worn.hex(g.homeColor),
                com.gathin.broadcast.Worn.hex(g.homeAlt), com.gathin.broadcast.Worn.hex(g.awayColor), com.gathin.broadcast.Worn.hex(g.awayAlt));
        g.homeColor = com.gathin.broadcast.Worn.hexOf(kit[0]);
        g.homeAlt = com.gathin.broadcast.Worn.hexOf(kit[1]);
        g.awayColor = com.gathin.broadcast.Worn.hexOf(kit[2]);
        g.awayAlt = com.gathin.broadcast.Worn.hexOf(kit[3]);
    }

    private static boolean wornBack;

    /** The kits named for recent games, kept from before (a restart mid-game keeps them), back once. */
    public static void wornKept(android.content.Context c) {
        if (wornBack) return;
        wornBack = true;
        com.gathin.broadcast.Worn.restore(c.getSharedPreferences("worn", android.content.Context.MODE_PRIVATE).getString("games", ""));
    }

    /** The kits a summary names for the game, noted and kept (off the main thread); whether they changed. */
    public static boolean wornRead(android.content.Context c, String id, Summary s) {
        wornKept(c);
        boolean changed = s != null && com.gathin.broadcast.Worn.note(id, s.worn);
        if (com.gathin.broadcast.Worn.unsaved()) {
            c.getSharedPreferences("worn", android.content.Context.MODE_PRIVATE).edit().putString("games", com.gathin.broadcast.Worn.save()).apply();
        }
        return changed;
    }

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
            int homeScore = 0, awayScore = 0;
            for (int i = 0; competitors != null && i < competitors.length(); i++) {
                JSONObject t = competitors.getJSONObject(i);
                boolean home = "home".equals(t.optString("homeAway"));
                if (home) homeId = t.optString("id");
                int sc = number(t.optString("score", "0"));
                if (home) homeScore = sc; else awayScore = sc;
            }
            // How far through the game it is, for the clean sheets in the ratings: all of it once over.
            JSONObject status = comps != null && comps.length() > 0 ? comps.getJSONObject(0).optJSONObject("status") : null;
            JSONObject stType = status == null ? null : status.optJSONObject("type");
            String state = stType == null ? "" : stType.optString("state", "");
            float progress = "post".equals(state) ? 1f : status == null ? 0f
                    : Math.max(0f, Math.min(1f, (float) status.optDouble("clock", 0) / 5400f));
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
                for (int p = 0; players != null && p < players.length(); p++) {
                    into.add(player(players.getJSONObject(p), isHome ? homeScore : awayScore, isHome ? awayScore : homeScore, progress));
                }
            }
            s.homeId = homeId;
            s.worn = com.gathin.broadcast.Worn.of(rosters, homeId);
            // every player's shirt number, for a goal's shirt turning to the scorer's name and number
            com.gathin.broadcast.Shirts.note(id, rosters);
            for (int i = 0; competitors != null && i < competitors.length(); i++) {
                JSONObject t = competitors.getJSONObject(i);
                if (!"home".equals(t.optString("homeAway"))) s.awayId = t.optString("id");
            }
            extras(root, s);
            plays(root, s, competitors);
            addedTime(root.optJSONArray("commentary"), s.added);
            varLines(root.optJSONArray("commentary"), s.varLines);
            penaltyLines(root.optJSONArray("commentary"), s.penaltyLines);
            winChance(root.optJSONArray("odds"), s);
            if (s.chances == null) winChance(root.optJSONArray("pickcenter"), s);
            shootout(root.optJSONArray("shootout"), homeId, s);
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
        s.standings = st;
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
                        w.optString("score"), date, e.optString("id"), h.optString("id"), w.optString("id")});
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

    private static final java.util.regex.Pattern ADDED = java.util.regex.Pattern.compile(
            "(?i)(\\d+)\\s+(?:additional\\s+)?minutes?\\s+of\\s+(?:added|additional|stoppage)\\s+time");

    /** Pure: the minutes of added time a line of commentary says were put up ("…announced 4 minutes of added time."); -1 for none. */
    public static int addedIn(String text) {
        if (text == null) return -1;
        java.util.regex.Matcher m = ADDED.matcher(text);
        return m.find() ? number(m.group(1)) : -1;
    }

    /** Pure: the half a minute of the game is in ("45'": 1, "90'": 2, "105'": 3, "120'": 4); 0 for none. */
    public static int halfOf(String minute) {
        int m = Game.minute(minute == null ? "" : minute.replaceAll("\\+.*", ""));
        return m <= 0 ? 0 : m <= 45 ? 1 : m <= 90 ? 2 : m <= 105 ? 3 : 4;
    }

    /** Pure: the commentary's lines that speak of a penalty (given, conceded, taken), and its latest, oldest first. */
    static void penaltyLines(JSONArray commentary, List<String[]> into) {
        int n = commentary == null ? 0 : commentary.length();
        for (int i = 0; i < n; i++) {
            JSONObject x = commentary.optJSONObject(i);
            String t = x == null ? "" : x.optString("text", "");
            if (i != n - 1 && !t.toLowerCase(java.util.Locale.ROOT).contains("penalty")) continue;
            JSONObject tm = x == null ? null : x.optJSONObject("time");
            into.add(new String[]{t, tm == null ? "" : tm.optString("displayValue", ""), x == null ? "" : String.valueOf(x.optInt("sequence", i))});
        }
    }

    /**
     * Off the main thread: the penalty card's records for a penalty to the home side (home) or the away one, from the
     * game's summary as last read and each player's figures in the competition (Penalty.records).
     */
    public static com.gathin.broadcast.Penalty.Records penaltyRecords(String slug, String id, boolean home) {
        try {
            JSONObject root = new JSONObject(cached(SITE + slug + "/summary?event=" + id, 120_000L));
            return com.gathin.broadcast.Penalty.records(root, slug, home, Net::get);
        } catch (Exception e) {
            return new com.gathin.broadcast.Penalty.Records();
        }
    }

    /**
     * The commentary's VAR lines and the six before and two after each ({text, minute, sequence}, oldest first), for VAR
     * explained (the reason and the referee's call round each check and decision); none when VAR is never mentioned.
     */
    static void varLines(JSONArray commentary, List<String[]> into) {
        int n = commentary == null ? 0 : commentary.length();
        boolean[] keep = new boolean[n];
        for (int i = 0; i < n; i++) {
            JSONObject x = commentary.optJSONObject(i);
            String t = x == null ? "" : x.optString("text", "");
            if (!t.contains("VAR")) continue;
            for (int j = Math.max(0, i - 6); j <= Math.min(n - 1, i + 2); j++) keep[j] = true;
        }
        for (int i = 0; i < n; i++) {
            if (!keep[i]) continue;
            JSONObject x = commentary.optJSONObject(i);
            JSONObject t = x == null ? null : x.optJSONObject("time");
            into.add(new String[]{x == null ? "" : x.optString("text", ""), t == null ? "" : t.optString("displayValue", ""),
                    x == null ? "" : String.valueOf(x.optInt("sequence", i))});
        }
    }

    /** The added time put up in each half, from ESPN's commentary ("Fourth official has announced 4 minutes of added time."). */
    static void addedTime(JSONArray commentary, int[] into) {
        for (int i = 0; commentary != null && i < commentary.length(); i++) {
            JSONObject x = commentary.optJSONObject(i);
            if (x == null) continue;
            int n = addedIn(x.optString("text", ""));
            if (n <= 0) continue;
            JSONObject t = x.optJSONObject("time");
            int half = halfOf(t == null ? "" : t.optString("displayValue", ""));
            if (half >= 1 && half <= into.length) into[half - 1] = n;
        }
    }

    /** Each side's chance of winning, and the draw's, from the prices before kick-off (each as a chance, the three made 100). */
    static void winChance(JSONArray odds, Summary s) {
        JSONObject o = odds == null || odds.length() == 0 ? null : odds.optJSONObject(0);
        if (o == null) return;
        double h = chance(o.optJSONObject("homeTeamOdds")), a = chance(o.optJSONObject("awayTeamOdds")), d = chance(o.optJSONObject("drawOdds"));
        if (h <= 0 || a <= 0 || d <= 0) return;
        double all = h + a + d;
        s.winHome = Math.round(100 * h / all) + "%";
        s.winAway = Math.round(100 * a / all) + "%";
        s.chances = new double[]{h / all, d / all, a / all};
    }

    /**
     * One player's season in each competition and his last games (ESPN's athlete overview: its "statistics" and
     * "gameLog"), kept a while; null when ESPN has none. Read off the main thread.
     */
    public static JSONObject athleteOverview(String slug, String athleteId) {
        if (slug == null || athleteId == null || !athleteId.matches("[0-9]{1,12}") || !slug.matches("[a-z0-9._]{1,40}")) return null;
        try {
            return new JSONObject(cached("https://site.web.api.espn.com/apis/common/v3/sports/soccer/" + slug + "/athletes/" + athleteId
                    + "/overview", 30 * 60_000L));
        } catch (Exception e) {
            return null;
        }
    }

    /** A penalty shootout's kicks from ESPN's summary ({id, team, shots: [{player, shotNumber, didScore}]} a side), in order. */
    static void shootout(JSONArray teams, String homeId, Summary s) {
        for (int i = 0; teams != null && i < teams.length(); i++) {
            JSONObject t = teams.optJSONObject(i);
            JSONArray shots = t == null ? null : t.optJSONArray("shots");
            if (shots == null) continue;
            List<JSONObject> in = new ArrayList<>();
            for (int k = 0; k < shots.length(); k++) if (shots.optJSONObject(k) != null) in.add(shots.optJSONObject(k));
            in.sort((x, y) -> Integer.compare(x.optInt("shotNumber"), y.optInt("shotNumber")));
            List<String[]> into = homeId.equals(t.optString("id")) ? s.homePens : s.awayPens;
            for (JSONObject x : in) into.add(new String[]{x.optString("player"), x.optBoolean("didScore") ? "1" : "0"});
        }
    }

    private static double chance(JSONObject x) {
        return x == null || !x.has("moneyLine") ? 0 : chance(x.optDouble("moneyLine", 0));
    }

    /** Pure: a price in American odds (+260, -500) as the chance it stands for (0 to 1); 0 for none. */
    public static double chance(double moneyLine) {
        if (moneyLine == 0 || Double.isNaN(moneyLine)) return 0;
        return moneyLine > 0 ? 100 / (moneyLine + 100) : -moneyLine / (-moneyLine + 100);
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

    static String[] player(JSONObject pl) {
        return player(pl, 0, 0, 0f);
    }

    /** "2" as 2; anything else as 0. */
    private static int number(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * One player of a roster, as Summary's line-ups hold them (P_*), with his live rating (Ratings) from his figures, his
     * side's score so far (scored, let in) and how far through the game it is.
     */
    static String[] player(JSONObject pl, int scored, int letIn, float progress) {
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
                a == null ? "" : a.optString("id", ""), Ratings.of(stats(pl), pos == null ? "" : pos.optString("abbreviation", ""),
                scored, letIn, progress)};
    }

    /** A player's figures in the game (ESPN's "stats": goals, shots, saves…) by name; empty when not given. */
    static java.util.Map<String, Integer> stats(JSONObject pl) {
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        JSONArray st = pl.optJSONArray("stats");
        for (int i = 0; st != null && i < st.length(); i++) {
            JSONObject x = st.optJSONObject(i);
            if (x == null) continue;
            String v = x.optString("value", x.optString("displayValue", ""));
            try {
                out.put(x.optString("name"), (int) Math.round(Double.parseDouble(v)));
            } catch (RuntimeException ignored) {
                // not a number
            }
        }
        return out;
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

    // ---- a club's games in one competition ------------------------------------------------------------------------

    /** Indexes into a schedule row: {game id, date (ISO), home id, home name, home goals, away id, away name, away goals, "pre"/"in"/"post"}. */
    public static final int S_ID = 0, S_DATE = 1, S_HOME = 2, S_HOME_NAME = 3, S_HOME_GOALS = 4, S_AWAY = 5, S_AWAY_NAME = 6,
            S_AWAY_GOALS = 7, S_STATE = 8;

    /**
     * A club's games in this competition only (ESPN's team schedule for the league), oldest first; fixtures: the ones
     * still to come instead of the ones played. Empty when ESPN gives none.
     */
    public static List<String[]> schedule(String slug, String teamId, boolean fixtures) {
        try {
            String url = SITE + slug + "/teams/" + teamId + "/schedule" + (fixtures ? "?fixture=true" : "");
            return schedule(new JSONObject(cached(url, 15 * 60_000L)));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    /** Pure: the rows of ESPN's team schedule, oldest first. */
    static List<String[]> schedule(JSONObject root) {
        List<String[]> out = new ArrayList<>();
        JSONArray events = root.optJSONArray("events");
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            JSONArray comps = e == null ? null : e.optJSONArray("competitions");
            JSONObject c = comps != null && comps.length() > 0 ? comps.optJSONObject(0) : null;
            JSONArray cs = c == null ? null : c.optJSONArray("competitors");
            if (cs == null || cs.length() != 2) continue;
            JSONObject h = cs.optJSONObject(0), w = cs.optJSONObject(1);
            if (h == null || w == null) continue;
            if ("away".equals(h.optString("homeAway"))) { JSONObject t = h; h = w; w = t; }
            JSONObject status = c.optJSONObject("status");
            JSONObject type = status == null ? null : status.optJSONObject("type");
            String state = type == null ? "" : type.optString("state");
            out.add(new String[]{e.optString("id"), e.optString("date"), teamId(h), teamName(h), goals(h), teamId(w), teamName(w), goals(w), state});
        }
        out.sort((a, b) -> a[S_DATE].compareTo(b[S_DATE]));
        return out;
    }

    private static String teamId(JSONObject competitor) {
        JSONObject t = competitor.optJSONObject("team");
        return t == null ? competitor.optString("id") : t.optString("id", competitor.optString("id"));
    }

    private static String teamName(JSONObject competitor) {
        JSONObject t = competitor.optJSONObject("team");
        return t == null ? "" : t.optString("shortDisplayName", t.optString("displayName", ""));
    }

    /** A competitor's goals: ESPN gives the score as a number, a string or {value, displayValue}. */
    private static String goals(JSONObject competitor) {
        Object sc = competitor.opt("score");
        if (sc instanceof JSONObject) return ((JSONObject) sc).optString("displayValue", "");
        return sc == null ? "" : String.valueOf(sc);
    }

    // ---- a game's video -----------------------------------------------------------------------------------------

    /** A game's first video as ESPN publishes it: {mp4 address, its still, its headline}; null when it has none. */
    public static String[] video(String slug, String id) {
        try {
            return video(new JSONObject(cached(SITE + slug + "/summary?event=" + id, 60 * 60_000L)));
        } catch (Exception ignored) {
            return null;
        }
    }

    /** A game's summary as ESPN gives it (for its videos), kept as fresh as asked; null when it can't be read. */
    public static JSONObject summaryRoot(String slug, String id, long freshMs) {
        if (slug == null || id == null || slug.isEmpty() || id.isEmpty()) return null;
        try {
            return new JSONObject(cached(SITE + slug + "/summary?event=" + id, freshMs));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * ESPN's soccer pages at this path (after ".../sports/soccer/": a club, its schedule, its roster), kept as fresh as
     * asked; null when it can't be read. Off the main thread.
     */
    static JSONObject site(String path, long freshMs) {
        try {
            return new JSONObject(cached(SITE + path, freshMs));
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Pure: the first video in a summary that has an mp4 to play. */
    static String[] video(JSONObject root) {
        JSONArray vs = root.optJSONArray("videos");
        for (int i = 0; vs != null && i < vs.length(); i++) {
            JSONObject v = vs.optJSONObject(i);
            JSONObject links = v == null ? null : v.optJSONObject("links");
            JSONObject source = links == null ? null : links.optJSONObject("source");
            if (source == null) continue;
            String url = "";
            for (String k : new String[]{"HD", "mezzanine", "full", "href"}) {
                Object o = source.opt(k);
                if (o instanceof JSONObject) url = ((JSONObject) o).optString("href", "");
                else if (o instanceof String) url = (String) o;
                if (url.startsWith("https://") && url.contains(".mp4")) break;
                url = "";
            }
            if (url.isEmpty()) continue;
            return new String[]{url, v.optString("thumbnail", ""), v.optString("headline", "")};
        }
        return null;
    }

    /** A club's crest by ESPN id (the TV's own URLs). */
    public static String crest(String teamId) {
        return "https://a.espncdn.com/i/teamlogos/soccer/500/" + teamId + ".png";
    }
}
