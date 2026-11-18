package com.gathin.homefieldtv.sports;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A club's squad and its management, for the club's page: each player with his shirt number, his line (goalkeepers,
 * defenders, midfielders, forwards), his position, country and age, and his cut-out photo; the manager and any staff the
 * source names. FotMob's squad first (the club found by its name in FotMob's search, the id then kept on this TV), ESPN's
 * roster when FotMob can't be read (ESPN names no manager here: its coach list is the club's managers of years gone by).
 * With them a player's season (Season): FotMob's figures for his main competition. Read off the main thread and kept a
 * while; anything a source doesn't give is left out, never made up.
 */
public final class Squad {
    public static final int GK = 0, DEF = 1, MID = 2, FWD = 3, STAFF = 4;
    /** Each line's name, one and many, by line. */
    public static final String[] LINE = {"Goalkeeper", "Defender", "Midfielder", "Forward", "Manager"};
    public static final String[] LINES = {"Goalkeepers", "Defenders", "Midfielders", "Forwards", "Management"};

    /** The club as the page that opens it knows it (a game's side, a table's row). */
    public static final class Club {
        /** ESPN's id and competition ("363", "eng.1"), its names (short, full), crest and colours ("144992"; "" none). */
        public String espnId = "", slug = "", name = "", fullName = "", crest = "", colour = "", alt = "";
        /** FotMob's id when it is known already (a game FotMob has read); 0 not yet. */
        public int fotmobId;

        /** A game's home or away side, in its own colours (not the kit worn), FotMob's id when the game has been read. */
        public static Club of(Game g, boolean away) {
            Club c = new Club();
            if (g == null) return c;
            c.espnId = nz(away ? g.awayId : g.homeId);
            c.slug = nz(g.slug);
            c.name = nz(away ? g.awayName : g.homeName);
            if (c.name.isEmpty()) c.name = nz(away ? g.away : g.home);
            c.fullName = nz(away ? g.awayFull : g.homeFull);
            c.crest = nz(away ? g.awayLogo : g.homeLogo);
            c.colour = nz(away ? g.awayOwn : g.homeOwn);
            c.alt = nz(away ? g.awayOwnAlt : g.homeOwnAlt);
            int[] ids = FotMob.teamIds(g.id);
            if (ids != null) c.fotmobId = away ? ids[1] : ids[0];
            return c;
        }

        /** A row of a competition's table (Espn.table), its colours from the competition's clubs (Espn.teams) when known. */
        public static Club of(String slug, String[] row, String[] known) {
            Club c = new Club();
            c.slug = nz(slug);
            c.espnId = nz(row[Espn.T_ID]);
            c.name = nz(row[Espn.T_NAME]);
            c.fullName = row.length > Espn.T_FULL ? nz(row[Espn.T_FULL]) : c.name;
            c.crest = nz(row[Espn.T_LOGO]);
            if (known != null && known.length > Espn.C_ALT) {
                c.colour = nz(known[Espn.C_COLOUR]);
                c.alt = nz(known[Espn.C_ALT]);
            }
            return c;
        }

        private static String nz(String s) {
            return s == null ? "" : s;
        }
    }

    /** One player, or one of the staff. */
    public static final class Person {
        /** The source's id (FotMob's: his photo and his season), "" none. */
        public String id = "";
        public String name = "", first = "", last = "";
        /** His shirt number, "" when none is given. */
        public String number = "";
        public int line = MID;
        /** His position ("Centre-back", "Striker", "Manager"), else his line's name. */
        public String position = "";
        /** His country and its code ("England", "ENG"), "" when not given; his age, -1 when not given. */
        public String country = "", code = "";
        public int age = -1;
        /** His cut-out photo and his country's flag (addresses), "" when none. */
        public String photo = "", flag = "";
        /** The photo is a face (FotMob's, square) rather than head and shoulders (ESPN's, wide). */
        public boolean face;
        /** FotMob's: his season can be read (season()). */
        public boolean fotmob;
        /** The season's figures as the squad lists them, {label, value}, in the page's order (ORDER). */
        public final List<String[]> figures = new ArrayList<>();
    }

    public String name = "";
    /** The club's own colour as the source gives it (0xFFrrggbb), 0 when it gives none. */
    public int colour;
    public final List<Person> players = new ArrayList<>(), staff = new ArrayList<>();

    /** Pure: who the page shows: the staff, else the players of a line (-1: every line), in the squad's order. */
    public List<Person> shown(boolean management, int line) {
        if (management) return new ArrayList<>(staff);
        List<Person> out = new ArrayList<>();
        for (Person p : players) if (line < 0 || p.line == line) out.add(p);
        return out;
    }

    // ---- FotMob's squad --------------------------------------------------------------------------------------------

    /** Pure: FotMob's team page (its squad by line, the coach's first) as a Squad. */
    static Squad fotmob(JSONObject d) {
        Squad s = new Squad();
        JSONObject details = d.optJSONObject("details");
        if (details != null) {
            s.name = text(details, "name");
            s.colour = colour(text(details, "teamColor"));
        }
        JSONObject sq = d.optJSONObject("squad");
        JSONArray groups = sq == null ? null : sq.optJSONArray("squad");
        boolean manager = false;
        for (int i = 0; groups != null && i < groups.length(); i++) {
            JSONObject g = groups.optJSONObject(i);
            if (g == null) continue;
            String title = text(g, "title").toLowerCase(Locale.ROOT);
            JSONArray members = g.optJSONArray("members");
            for (int k = 0; members != null && k < members.length(); k++) {
                Person p = fotmobPerson(members.optJSONObject(k), title);
                if (p == null) continue;
                if (p.line == STAFF) {
                    // The coach FotMob lists first is the manager; anyone after him by the role FotMob gives.
                    if ("Manager".equals(p.position)) {
                        if (manager) p.position = "Coach";
                        manager = true;
                    }
                    s.staff.add(p);
                } else {
                    s.players.add(p);
                }
            }
        }
        order(s.players);
        return s;
    }

    private static Person fotmobPerson(JSONObject m, String title) {
        if (m == null) return null;
        String name = text(m, "name");
        if (name.isEmpty()) return null;
        Person p = new Person();
        int id = m.optInt("id", 0);
        p.id = id > 0 ? String.valueOf(id) : "";
        p.name = name;
        String[] parts = split(name);
        p.first = parts[0];
        p.last = parts[1];
        int shirt = m.optInt("shirtNumber", -1);
        p.number = shirt >= 1 && shirt <= 99 ? String.valueOf(shirt) : "";
        JSONObject role = m.optJSONObject("role");
        String roleKey = role == null ? "" : text(role, "key").toLowerCase(Locale.ROOT);
        p.line = lineOf(title, roleKey, m.has("positionId") ? m.optInt("positionId", -1) : -1);
        if (p.line == STAFF) {
            // FotMob's head coach is the manager; the rest by the role FotMob gives them.
            String said = role == null ? "" : text(role, "fallback");
            p.position = "coach".equals(title) || "coach".equals(roleKey) ? "Manager" : said.isEmpty() ? "Staff" : said;
        } else {
            p.position = position(text(m, "positionIdsDesc"), p.line);
        }
        p.country = text(m, "cname");
        p.code = text(m, "ccode").toUpperCase(Locale.ROOT);
        p.flag = flag(p.code);
        int age = m.optInt("age", -1);
        p.age = age > 0 && age < 100 ? age : -1;
        p.fotmob = id > 0;
        p.photo = id > 0 ? FotMob.photoUrl(id) : "";
        p.face = true;
        if (p.line != STAFF) {
            add(p.figures, "Goals", m.opt("goals"), false);
            add(p.figures, "Assists", m.opt("assists"), false);
            add(p.figures, "Yellow cards", m.opt("ycards"), false);
            add(p.figures, "Red cards", m.opt("rcards"), false);
            add(p.figures, "Rating", m.opt("rating"), true);
            sort(p.figures);
        }
        return p;
    }

    /** Pure: a member's line from FotMob's group ("keepers"), his role ("keeper_long") or position id (0 to 3). */
    static int lineOf(String group, String role, int positionId) {
        String g = group == null ? "" : group, r = role == null ? "" : role;
        if (g.contains("coach") || r.contains("coach") || g.contains("staff") || g.contains("manage")) return STAFF;
        if (g.startsWith("keeper") || g.startsWith("goalkeeper") || r.startsWith("keeper")) return GK;
        if (g.startsWith("defender") || r.startsWith("defender")) return DEF;
        if (g.startsWith("midfielder") || r.startsWith("midfielder")) return MID;
        if (g.startsWith("attacker") || g.startsWith("forward") || r.startsWith("attacker") || r.startsWith("forward")) return FWD;
        return positionId >= 0 && positionId <= 3 ? positionId : STAFF;
    }

    private static final Map<String, String> POSITIONS = new HashMap<>();
    static {
        String[][] p = {{"GK", "Goalkeeper"}, {"CB", "Centre-back"}, {"LCB", "Centre-back"}, {"RCB", "Centre-back"},
                {"RB", "Right-back"}, {"LB", "Left-back"}, {"RWB", "Right wing-back"}, {"LWB", "Left wing-back"},
                {"DM", "Defensive midfielder"}, {"CDM", "Defensive midfielder"}, {"CM", "Central midfielder"},
                {"AM", "Attacking midfielder"}, {"CAM", "Attacking midfielder"}, {"RM", "Right midfielder"},
                {"LM", "Left midfielder"}, {"RW", "Right winger"}, {"LW", "Left winger"}, {"ST", "Striker"},
                {"CF", "Centre-forward"}, {"SS", "Second striker"}};
        for (String[] x : p) POSITIONS.put(x[0], x[1]);
    }

    /** Pure: the first of FotMob's positions ("LB,CB,LWB") in words ("Left-back"); else the line's name. */
    static String position(String desc, int line) {
        String first = desc == null ? "" : desc.split(",")[0].trim().toUpperCase(Locale.ROOT);
        String word = POSITIONS.get(first);
        return word != null ? word : LINE[Math.max(0, Math.min(LINE.length - 1, line))];
    }

    // ---- ESPN's roster, when FotMob can't be read ------------------------------------------------------------------

    /**
     * Pure: ESPN's roster as a Squad: its players with their season there (appearances, goals, assists, cards). No staff:
     * ESPN's coach list for a club is its managers of years gone by, not the staff now.
     */
    static Squad espn(JSONObject root) {
        Squad s = new Squad();
        JSONObject team = root.optJSONObject("team");
        if (team != null) {
            s.name = text(team, "displayName");
            s.colour = colour(text(team, "color"));
        }
        JSONArray as = root.optJSONArray("athletes");
        for (int i = 0; as != null && i < as.length(); i++) {
            JSONObject a = as.optJSONObject(i);
            if (a == null) continue;
            Person p = new Person();
            p.id = text(a, "id");
            String first = text(a, "firstName"), last = text(a, "lastName"), display = text(a, "displayName");
            p.name = display.isEmpty() ? (first + " " + last).trim() : display;
            if (p.name.isEmpty()) continue;
            if (last.isEmpty()) {
                // "João Pedro", "Estêvão": one name, the big one.
                p.first = "";
                p.last = p.name;
            } else {
                p.first = first;
                p.last = last;
            }
            String jersey = text(a, "jersey");
            p.number = jersey.matches("[0-9]{1,2}") ? jersey : "";
            JSONObject pos = a.optJSONObject("position");
            String abbr = pos == null ? "" : text(pos, "abbreviation").toUpperCase(Locale.ROOT);
            p.line = abbr.startsWith("G") ? GK : abbr.startsWith("D") ? DEF : abbr.startsWith("F") ? FWD : MID;
            String said = pos == null ? "" : text(pos, "name");
            p.position = said.isEmpty() ? LINE[p.line] : said;
            int age = a.optInt("age", -1);
            p.age = age > 0 && age < 100 ? age : -1;
            p.country = text(a, "citizenship");
            JSONObject cc = a.optJSONObject("citizenshipCountry");
            p.code = cc == null ? "" : text(cc, "abbreviation").toUpperCase(Locale.ROOT);
            JSONObject flag = a.optJSONObject("flag");
            String href = flag == null ? "" : text(flag, "href");
            p.flag = href.startsWith("https://a.espncdn.com/") ? href : flag(p.code);
            JSONObject hs = a.optJSONObject("headshot");
            String shot = hs == null ? "" : text(hs, "href");
            p.photo = shot.startsWith("https://a.espncdn.com/") ? shot : "";
            p.face = false;
            Map<String, String> st = new HashMap<>();
            JSONObject stats = a.optJSONObject("statistics");
            JSONObject splits = stats == null ? null : stats.optJSONObject("splits");
            JSONArray cats = splits == null ? null : splits.optJSONArray("categories");
            for (int c = 0; cats != null && c < cats.length(); c++) {
                JSONArray list = cats.optJSONObject(c) == null ? null : cats.optJSONObject(c).optJSONArray("stats");
                for (int k = 0; list != null && k < list.length(); k++) {
                    JSONObject x = list.optJSONObject(k);
                    if (x != null && !text(x, "name").isEmpty()) st.put(text(x, "name"), text(x, "displayValue"));
                }
            }
            add(p.figures, "Apps", st.get("appearances"), false);
            add(p.figures, "Goals", st.get("totalGoals"), false);
            add(p.figures, "Assists", st.get("goalAssists"), false);
            add(p.figures, "Yellow cards", st.get("yellowCards"), false);
            add(p.figures, "Red cards", st.get("redCards"), false);
            sort(p.figures);
            s.players.add(p);
        }
        order(s.players);
        return s;
    }

    /** The players by line, then shirt number (none last), then name. */
    private static void order(List<Person> players) {
        players.sort((a, b) -> {
            if (a.line != b.line) return Integer.compare(a.line, b.line);
            int na = a.number.isEmpty() ? 1000 : Integer.parseInt(a.number), nb = b.number.isEmpty() ? 1000 : Integer.parseInt(b.number);
            return na != nb ? Integer.compare(na, nb) : a.name.compareTo(b.name);
        });
    }

    // ---- finding the club on FotMob ---------------------------------------------------------------------------------

    /** ESPN's league to FotMob's, to choose between clubs of one name (read from FotMob's search, November 2026). */
    static final Map<String, Integer> LEAGUES = new HashMap<>();
    static {
        String[][] l = {{"eng.1", "47"}, {"eng.2", "48"}, {"esp.1", "87"}, {"ita.1", "55"}, {"ger.1", "54"}, {"fra.1", "53"}, {"sco.1", "64"}};
        for (String[] x : l) LEAGUES.put(x[0], Integer.parseInt(x[1]));
    }

    /** Women's, youth and reserve sides, which a club's page never means. */
    private static final Pattern JUNIOR = Pattern.compile("(?i)(\\(w\\)|\\bu-?[0-9]{2}\\b|\\breserves?\\b|\\bwomen\\b|\\bladies\\b|\\s(b|c|ii|iii)$)");

    static boolean senior(String name) {
        return name != null && !JUNIOR.matcher(name.trim()).find();
    }

    /**
     * Pure: the club FotMob's search answer means: a senior side of the club's name in its league, else the first senior
     * side of its name, else the first senior side it suggests at all (it ranks the club itself first: "Internazionale"
     * finds "Inter"); 0 when it suggests none.
     */
    static int pickTeam(JSONArray groups, String name, int leagueId) {
        int first = 0, named = 0, inLeague = 0;
        for (int i = 0; groups != null && i < groups.length(); i++) {
            JSONObject g = groups.optJSONObject(i);
            JSONArray sg = g == null ? null : g.optJSONArray("suggestions");
            for (int k = 0; sg != null && k < sg.length(); k++) {
                JSONObject x = sg.optJSONObject(k);
                if (x == null || !"team".equals(text(x, "type"))) continue;
                int id;
                try {
                    id = Integer.parseInt(text(x, "id"));
                } catch (NumberFormatException e) {
                    continue;
                }
                String n = text(x, "name");
                if (id <= 0 || !senior(n)) continue;
                if (first == 0) first = id;
                if (!FotMob.sameClub(n, name)) continue;
                if (named == 0) named = id;
                if (leagueId > 0 && inLeague == 0 && x.optInt("leagueId", 0) == leagueId) inLeague = id;
            }
        }
        return inLeague > 0 ? inLeague : named > 0 ? named : first;
    }

    /** Each club's FotMob id this run, by idKey (-1: looked for, FotMob has none). */
    private static final Map<String, Integer> IDS = new HashMap<>();

    static String idKey(Club c) {
        return !c.espnId.isEmpty() ? "espn:" + c.espnId : "name:" + plain(c.fullName.isEmpty() ? c.name : c.fullName);
    }

    /** The club's FotMob id: known, kept on this TV, or found by FotMob's search (then kept). Off the main thread. */
    static int fotmobId(Context c, Club club) {
        String key = idKey(club);
        synchronized (IDS) {
            Integer had = IDS.get(key);
            if (had != null) return Math.max(0, had);
        }
        SharedPreferences prefs = c == null ? null : c.getApplicationContext().getSharedPreferences("sports_clubs", Context.MODE_PRIVATE);
        int kept = prefs == null ? 0 : prefs.getInt(key, 0);
        if (kept > 0) {
            synchronized (IDS) {
                IDS.put(key, kept);
            }
            return kept;
        }
        String query = club.fullName.isEmpty() ? club.name : club.fullName;
        if (query.isEmpty()) return 0;
        int id;
        try {
            Integer league = LEAGUES.get(club.slug);
            id = pickTeam(new JSONArray(FotMob.data("search/suggest?term=" + URLEncoder.encode(query, "UTF-8") + "&lang=en")),
                    query, league == null ? 0 : league);
        } catch (Exception e) {
            // FotMob didn't answer: asked again next time
            return 0;
        }
        if (id > 0 && prefs != null) prefs.edit().putInt(key, id).apply();
        synchronized (IDS) {
            IDS.put(key, id > 0 ? id : -1);
        }
        return id;
    }

    // ---- reading -------------------------------------------------------------------------------------------------

    private static final long FRESH_MS = 30 * 60_000L;
    private static final Map<String, Object[]> KEPT = new LinkedHashMap<String, Object[]>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Object[]> e) {
            return size() > 12;
        }
    };

    /** A squad read lately for the club (nothing fetched), or null. */
    public static Squad kept(Club club) {
        synchronized (KEPT) {
            Object[] had = KEPT.get(idKey(club));
            return had == null || System.currentTimeMillis() - (Long) had[1] > FRESH_MS ? null : (Squad) had[0];
        }
    }

    /**
     * The club's squad: FotMob's (one read, its id found once by its search), else ESPN's roster for the competition the
     * club was met in; null when neither answers. Kept half an hour. Off the main thread.
     */
    public static Squad read(Context c, Club club) {
        Squad had = kept(club);
        if (had != null) return had;
        Squad s = null;
        int id = club.fotmobId > 0 ? club.fotmobId : fotmobId(c, club);
        if (id > 0) {
            try {
                s = fotmob(new JSONObject(FotMob.data("teams?id=" + id)));
            } catch (Exception e) {
                s = null;
            }
        }
        if (s == null || s.players.isEmpty()) {
            JSONObject r = Espn.roster(club.slug, club.espnId);
            Squad e = r == null ? null : espn(r);
            if (e != null && !e.players.isEmpty()) s = e;
        }
        if (s == null || s.players.isEmpty() && s.staff.isEmpty()) return null;
        synchronized (KEPT) {
            KEPT.put(idKey(club), new Object[]{s, System.currentTimeMillis()});
        }
        return s;
    }

    // ---- a player's season -------------------------------------------------------------------------------------------

    /** The page's order of figures: a player's, then a manager's at his club. */
    public static final String[] ORDER = {"Apps", "Goals", "Assists", "Clean sheets", "Yellow cards", "Red cards", "Minutes",
            "Rating", "Matches", "Won", "Drawn", "Lost"};

    /** A player's season as FotMob has it (his main competition's figures), or a manager's record at his club. */
    public static final class Season {
        /** What the figures are for: "Premier League", "2026/2027"; a manager's "At Chelsea" and "". "" when not given. */
        public String competition = "", season = "";
        /** {label, value} in ORDER. */
        public final List<String[]> figures = new ArrayList<>();
        /** Where he plays most, in FotMob's words ("Attacking Midfielder"); "" none. */
        public String position = "";
    }

    private static final String[][] FIGURES = {{"matches_uppercase", "Apps"}, {"goals", "Goals"}, {"assists", "Assists"},
            {"clean_sheet_title", "Clean sheets"}, {"yellow_cards", "Yellow cards"}, {"red_cards", "Red cards"},
            {"minutes_played", "Minutes"}, {"rating", "Rating"}};
    private static final String[][] TITLES = {{"Matches", "Apps"}, {"Goals", "Goals"}, {"Assists", "Assists"},
            {"Clean sheets", "Clean sheets"}, {"Yellow cards", "Yellow cards"}, {"Red cards", "Red cards"},
            {"Minutes played", "Minutes"}, {"Rating", "Rating"}};

    /** Pure: FotMob's page for a player (or a manager) as his Season; figures it doesn't give are left out. */
    static Season season(JSONObject d) {
        Season s = new Season();
        JSONObject pd = d.optJSONObject("positionDescription");
        JSONObject primary = pd == null ? null : pd.optJSONObject("primaryPosition");
        s.position = primary == null ? "" : text(primary, "label");
        JSONObject main = d.optJSONObject("mainLeague");
        JSONArray stats = main == null ? null : main.optJSONArray("stats");
        if (stats != null) {
            s.competition = text(main, "leagueName");
            s.season = text(main, "season");
            for (int i = 0; i < stats.length(); i++) {
                JSONObject x = stats.optJSONObject(i);
                if (x == null) continue;
                String label = label(text(x, "localizedTitleId"), FIGURES);
                if (label == null) label = label(text(x, "title"), TITLES);
                if (label == null || has(s.figures, label)) continue;
                add(s.figures, label, x.opt("value"), "Rating".equals(label));
            }
        }
        if (d.optBoolean("isCoach")) {
            JSONObject cs = d.optJSONObject("coachStats");
            JSONObject at = cs == null ? null : cs.optJSONObject("activeCareerEntry");
            if (at != null && !text(at, "teamName").isEmpty()) {
                s.figures.clear();
                s.competition = "At " + text(at, "teamName");
                s.season = "";
                add(s.figures, "Matches", at.opt("matches"), false);
                add(s.figures, "Won", at.opt("wins"), false);
                add(s.figures, "Drawn", at.opt("draws"), false);
                add(s.figures, "Lost", at.opt("losses"), false);
            }
        }
        sort(s.figures);
        return s;
    }

    private static String label(String key, String[][] map) {
        if (key == null || key.isEmpty()) return null;
        for (String[] m : map) if (m[0].equalsIgnoreCase(key)) return m[1];
        return null;
    }

    private static final Map<String, Object[]> SEASONS = new LinkedHashMap<String, Object[]>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Object[]> e) {
            return size() > 40;
        }
    };

    /** A season read lately (nothing fetched), or null. */
    public static Season keptSeason(String fotmobId) {
        synchronized (SEASONS) {
            Object[] had = SEASONS.get(fotmobId);
            return had == null || System.currentTimeMillis() - (Long) had[1] > FRESH_MS ? null : (Season) had[0];
        }
    }

    /** A player's (or manager's) season from FotMob, kept half an hour; null when it can't be read. Off the main thread. */
    public static Season season(String fotmobId) {
        if (fotmobId == null || !fotmobId.matches("[0-9]{1,10}")) return null;
        Season had = keptSeason(fotmobId);
        if (had != null) return had;
        Season s;
        try {
            s = season(new JSONObject(FotMob.data("playerData?id=" + fotmobId)));
        } catch (Exception e) {
            return null;
        }
        synchronized (SEASONS) {
            SEASONS.put(fotmobId, new Object[]{s, System.currentTimeMillis()});
        }
        return s;
    }

    // ---- small pure helpers -------------------------------------------------------------------------------------------

    /** Pure: a figure as the page writes it (a rating to two places, minutes with a comma), or null for none. */
    static String figure(Object v, boolean rating) {
        if (v == null || JSONObject.NULL.equals(v)) return null;
        if (v instanceof Number) {
            double x = ((Number) v).doubleValue();
            if (Double.isNaN(x)) return null;
            if (rating) return String.format(Locale.UK, "%.2f", x);
            return x == Math.rint(x) ? String.format(Locale.UK, "%,d", (long) x) : String.valueOf(x);
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        if (rating) {
            try {
                return String.format(Locale.UK, "%.2f", Double.parseDouble(s));
            } catch (NumberFormatException e) {
                return s;
            }
        }
        return s;
    }

    private static void add(List<String[]> into, String label, Object value, boolean rating) {
        String v = figure(value, rating);
        if (v != null) into.add(new String[]{label, v});
    }

    private static boolean has(List<String[]> figures, String label) {
        for (String[] f : figures) if (f[0].equals(label)) return true;
        return false;
    }

    private static void sort(List<String[]> figures) {
        List<String> order = Arrays.asList(ORDER);
        figures.sort((a, b) -> Integer.compare(order.indexOf(a[0]), order.indexOf(b[0])));
    }

    /** A JSON field as text: "" for none or null (Android's org.json writes a null as "null"). */
    static String text(JSONObject o, String key) {
        Object v = o == null ? null : o.opt(key);
        if (v == null || JSONObject.NULL.equals(v)) return "";
        String s = String.valueOf(v).trim();
        return "null".equals(s) ? "" : s;
    }

    /** Pure: "#051495" or "144992" as an opaque colour; 0 for anything else. */
    public static int colour(String hex) {
        String h = hex == null ? "" : hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (!h.matches("[0-9A-Fa-f]{6}")) return 0;
        return 0xFF000000 | Integer.parseInt(h, 16);
    }

    /** ESPN's flag for a country's three-letter code ("ENG", as FotMob and ESPN both write them), "" for none. */
    public static String flag(String code) {
        if (code == null || !code.matches("[A-Za-z]{3}")) return "";
        return "https://a.espncdn.com/i/teamlogos/countries/500/" + code.toLowerCase(Locale.ROOT) + ".png";
    }

    private static final Set<String> PARTICLES = new HashSet<>(Arrays.asList("van", "von", "de", "da", "di", "del", "della",
            "der", "den", "dos", "das", "du", "le", "la", "ter", "ten", "mac", "st", "al", "el", "dei", "lo"));

    /**
     * Pure: a name as {first names, surname}: "Cole Palmer" {"Cole", "Palmer"}, "Virgil van Dijk" {"Virgil", "van Dijk"},
     * "Kevin De Bruyne" {"Kevin", "De Bruyne"}, "Gabriel" {"", "Gabriel"}.
     */
    public static String[] split(String full) {
        String n = full == null ? "" : full.trim().replaceAll("\\s+", " ");
        int sp = n.lastIndexOf(' ');
        if (sp < 0) return new String[]{"", n};
        int start = sp + 1;
        while (start > 1) {
            int prev = n.lastIndexOf(' ', start - 2);
            String word = n.substring(prev + 1, start - 1);
            if (!PARTICLES.contains(word.toLowerCase(Locale.ROOT))) break;
            start = prev + 1;
        }
        return new String[]{n.substring(0, Math.max(0, start - 1)).trim(), n.substring(start)};
    }

    private static String plain(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
