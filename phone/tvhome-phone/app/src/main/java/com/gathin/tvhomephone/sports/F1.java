package com.gathin.tvhomephone.sports;

import com.gathin.tvhomephone.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Formula 1 from Jolpica's Ergast-style API, as the TV reads it: the drivers' and teams' standings, the next Grand Prix
 * with its sessions, the qualifying order (the grid) and the last race's result. With them, as the TV draws them: each
 * team's colour and its logo from formula1.com, and each driver's photo from ESPN (matched by his last name).
 */
public final class F1 {
    private F1() {}

    private static final String BASE = "https://api.jolpi.ca/ergast/f1/current";

    /** One line of the drivers' or the teams' standings. */
    public static final class Standing {
        /** Place, name ("Lando Norris" or "McLaren"), the driver's team's name, points and (teams) wins. */
        public String position = "", name = "", team = "", points = "", wins = "";
        /** Jolpica's constructor id ("mclaren", "red_bull"): the team's colour and logo; "" when not known. */
        public String teamId = "";
        /** A driver's family name, and his headshot on ESPN (null when ESPN has none for him). */
        public String family = "", headshot;
        /** The season the standings are for: formula1.com files the team logos by it. */
        public int season;
    }

    /** The drivers' standings, each with his team, its colour and logo, and his photo from ESPN. */
    public static List<Standing> drivers() {
        List<Standing> out = new ArrayList<>();
        try {
            JSONObject table = new JSONObject(Net.get(BASE + "/driverStandings.json?limit=100")).getJSONObject("MRData")
                    .getJSONObject("StandingsTable");
            JSONArray lists = table.getJSONArray("StandingsLists");
            int season = Espn.parseInt(table.optString("season", lists.getJSONObject(0).optString("season")));
            JSONArray s = lists.getJSONObject(0).getJSONArray("DriverStandings");
            for (int i = 0; i < s.length(); i++) {
                JSONObject d = s.getJSONObject(i), dr = d.getJSONObject("Driver");
                JSONArray cons = d.optJSONArray("Constructors");
                // The latest of his teams this season (Jolpica lists them in the order he drove for them).
                JSONObject team = cons != null && cons.length() > 0 ? cons.getJSONObject(cons.length() - 1) : null;
                Standing st = new Standing();
                st.position = d.optString("position", String.valueOf(i + 1));
                st.family = dr.optString("familyName");
                st.name = (dr.optString("givenName") + " " + st.family).trim();
                st.team = team == null ? "" : team.optString("name");
                st.teamId = team == null ? "" : team.optString("constructorId");
                st.points = d.optString("points");
                st.season = season;
                out.add(st);
            }
            Map<String, String> ids = espnIds(out, season);
            for (Standing st : out) st.headshot = headshot(ids.get(key(st.family)));
        } catch (Exception ignored) {
            // none this time
        }
        return out;
    }

    /** The teams' standings, each with its colour and logo. */
    public static List<Standing> teams() {
        List<Standing> out = new ArrayList<>();
        try {
            JSONObject table = new JSONObject(Net.get(BASE + "/constructorStandings.json?limit=100")).getJSONObject("MRData")
                    .getJSONObject("StandingsTable");
            JSONArray lists = table.getJSONArray("StandingsLists");
            int season = Espn.parseInt(table.optString("season", lists.getJSONObject(0).optString("season")));
            JSONArray s = lists.getJSONObject(0).getJSONArray("ConstructorStandings");
            for (int i = 0; i < s.length(); i++) {
                JSONObject d = s.getJSONObject(i), con = d.getJSONObject("Constructor");
                Standing st = new Standing();
                st.position = d.optString("position", String.valueOf(i + 1));
                st.name = con.optString("name");
                st.team = st.name;
                st.teamId = con.optString("constructorId");
                st.wins = d.optString("wins");
                st.points = d.optString("points");
                st.season = season;
                out.add(st);
            }
        } catch (Exception ignored) {
            // none this time
        }
        return out;
    }

    // ---- the teams' colours and logos, the drivers' photos ------------------------------------------------------

    /** A team's colour, as its cars and F1's graphics use it, by Jolpica's constructor id; F1's red for one not known. */
    public static int colour(String teamId) {
        if (teamId == null) return 0xFFE10600;
        switch (teamId) {
            case "mercedes": return 0xFF27F4D2;
            case "ferrari": return 0xFFE8002D;
            case "mclaren": return 0xFFFF8000;
            case "red_bull": return 0xFF3671C6;
            case "aston_martin": return 0xFF229971;
            case "alpine": return 0xFFFF87BC;
            case "williams": return 0xFF64C4FF;
            case "rb": return 0xFF6692FF;
            case "haas": return 0xFFB6BABD;
            case "sauber": return 0xFF52E252;
            case "audi": return 0xFFBB0A30;
            case "cadillac": return 0xFFC9A227;
            default: return 0xFFE10600;
        }
    }

    /** The team's coloured logo on formula1.com for a season, by Jolpica's constructor id (the TV's own addresses). */
    public static String logo(String teamId, int season) {
        if (teamId == null || teamId.isEmpty()) return null;
        String slug;
        switch (teamId) {
            case "red_bull": slug = "redbullracing"; break;
            case "rb": slug = "racingbulls"; break;
            case "aston_martin": slug = "astonmartin"; break;
            default: slug = teamId.replace("_", "");
        }
        int y = season > 0 ? season : java.time.Year.now().getValue();
        return "https://media.formula1.com/image/upload/c_lfill,w_96/q_auto/v1740000001/common/f1/" + y + "/" + slug + "/" + y + slug + "logo.webp";
    }

    /** The logo to try, then the one before it: the season's, then last season's (before formula1.com has the new ones). */
    public static String[] logos(String teamId, int season) {
        int y = season > 0 ? season : java.time.Year.now().getValue();
        String now = logo(teamId, y);
        return now == null ? new String[0] : new String[]{now, logo(teamId, y - 1)};
    }

    /** ESPN's headshot by its athlete id (each in this season's team kit, on no background), or null. */
    static String headshot(String espnId) {
        return espnId == null || espnId.isEmpty() ? null : "https://a.espncdn.com/i/headshots/rpm/players/full/" + espnId + ".png";
    }

    /** "Hülkenberg", "Nico Hulkenberg" and "N. Hülkenberg" alike: the last name, lower case without accents. */
    static String key(String name) {
        String n = java.text.Normalizer.normalize(name == null ? "" : name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).trim();
        int sp = n.lastIndexOf(' ');
        return sp >= 0 ? n.substring(sp + 1) : n;
    }

    /** The initials in a name: "Lando Norris" to "LN", "Kimi Antonelli" to "KA", "McLaren" to "M". */
    public static String initials(String name) {
        StringBuilder s = new StringBuilder();
        for (String w : (name == null ? "" : name.trim()).split("\\s+")) {
            if (w.isEmpty() || s.length() >= 2) continue;
            s.append(Character.toUpperCase(w.charAt(0)));
        }
        return s.toString();
    }

    private static final String ESPN = "https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard";
    private static final Map<String, String> ESPN_IDS = new ConcurrentHashMap<>();
    private static final Object IDS_LOCK = new Object();
    private static long boardAt, seasonAt;

    /**
     * Each driver's ESPN id by his last name (key), learnt from ESPN's F1 scoreboard: the weekend's sessions first, then
     * (at most twice a day, when some driver is still not found) the whole season's.
     */
    private static Map<String, String> espnIds(List<Standing> drivers, int season) {
        synchronized (IDS_LOCK) {
            return readIds(drivers, season);
        }
    }

    private static Map<String, String> readIds(List<Standing> drivers, int season) {
        long now = System.currentTimeMillis();
        if (missing(drivers) && now - boardAt > 30 * 60_000L) {
            boardAt = now;
            try {
                learn(new JSONObject(Net.get(ESPN)), ESPN_IDS);
            } catch (Exception ignored) {
                // ESPN did not answer
            }
        }
        if (missing(drivers) && now - seasonAt > 12 * 3600_000L) {
            seasonAt = now;
            try {
                learn(new JSONObject(Net.get(ESPN + "?dates=" + (season > 0 ? season : java.time.Year.now().getValue()))), ESPN_IDS);
            } catch (Exception ignored) {
                // ESPN did not answer
            }
        }
        return ESPN_IDS;
    }

    private static boolean missing(List<Standing> drivers) {
        for (Standing d : drivers) if (!ESPN_IDS.containsKey(key(d.family))) return true;
        return false;
    }

    /** Every driver on a scoreboard, by last name to ESPN's id (each competitor's id is his athlete id). Pure. */
    static void learn(JSONObject board, Map<String, String> into) {
        JSONArray events = board.optJSONArray("events");
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONArray comps = events.optJSONObject(i) == null ? null : events.optJSONObject(i).optJSONArray("competitions");
            for (int c = 0; comps != null && c < comps.length(); c++) {
                JSONArray drivers = comps.optJSONObject(c) == null ? null : comps.optJSONObject(c).optJSONArray("competitors");
                for (int d = 0; drivers != null && d < drivers.length(); d++) {
                    JSONObject x = drivers.optJSONObject(d);
                    JSONObject a = x == null ? null : x.optJSONObject("athlete");
                    String id = x == null ? "" : x.optString("id", "");
                    if (a == null || id.isEmpty()) continue;
                    String name = a.optString("displayName", a.optString("fullName", a.optString("shortName", "")));
                    if (!name.isEmpty()) into.put(key(name), id);
                }
            }
        }
    }

    /** The next Grand Prix: its name, round and each session {name, start ms}, the race last; null when none. */
    public static final class Weekend {
        public String name, round, circuit;
        public final List<Object[]> sessions = new ArrayList<>();
        public long race;
    }

    private static Weekend next;
    private static long nextAt;

    public static synchronized Weekend next() {
        if (next != null && System.currentTimeMillis() - nextAt < 3600_000L) return next;
        try {
            JSONArray races = new JSONObject(Net.get(BASE + "/next.json")).getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races");
            if (races.length() == 0) return null;
            JSONObject r = races.getJSONObject(0);
            Weekend w = new Weekend();
            w.name = r.optString("raceName");
            w.round = r.optString("round");
            JSONObject circuit = r.optJSONObject("Circuit");
            w.circuit = circuit == null ? "" : circuit.optString("circuitName");
            String[][] parts = {{"FirstPractice", "Practice 1"}, {"SecondPractice", "Practice 2"}, {"ThirdPractice", "Practice 3"},
                    {"SprintQualifying", "Sprint qualifying"}, {"Sprint", "Sprint"}, {"Qualifying", "Qualifying"}};
            for (String[] p : parts) {
                JSONObject s = r.optJSONObject(p[0]);
                if (s != null) w.sessions.add(new Object[]{p[1], when(s.optString("date"), s.optString("time"))});
            }
            w.race = when(r.optString("date"), r.optString("time"));
            w.sessions.add(new Object[]{"Race", w.race});
            w.sessions.sort((a, b) -> Long.compare((Long) a[1], (Long) b[1]));
            next = w;
            nextAt = System.currentTimeMillis();
            return w;
        } catch (Exception e) {
            return next;
        }
    }

    static long when(String date, String time) {
        if (date == null || date.isEmpty()) return 0;
        String t = time == null || time.isEmpty() ? "12:00:00Z" : time;
        try {
            return OffsetDateTime.parse(date + "T" + t).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    /** The grid for a round: "1 Verstappen, 2 Norris, …" from qualifying; null before it is out. */
    public static String grid(String round) {
        try {
            JSONArray races = new JSONObject(Net.get(BASE + "/" + round + "/qualifying.json")).getJSONObject("MRData")
                    .getJSONObject("RaceTable").getJSONArray("Races");
            if (races.length() == 0) return null;
            JSONArray q = races.getJSONObject(0).getJSONArray("QualifyingResults");
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < q.length() && i < 10; i++) {
                JSONObject d = q.getJSONObject(i).getJSONObject("Driver");
                if (s.length() > 0) s.append(", ");
                s.append(i + 1).append(' ').append(d.optString("familyName"));
            }
            return s.length() == 0 ? null : s.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** The last race: {round, name, "1 Verstappen · 2 Norris · 3 Leclerc"}; null when not found. */
    public static String[] lastResult() {
        try {
            JSONArray races = new JSONObject(Net.get(BASE + "/last/results.json")).getJSONObject("MRData")
                    .getJSONObject("RaceTable").getJSONArray("Races");
            if (races.length() == 0) return null;
            JSONObject r = races.getJSONObject(0);
            JSONArray res = r.getJSONArray("Results");
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < res.length() && i < 3; i++) {
                JSONObject d = res.getJSONObject(i).getJSONObject("Driver");
                if (s.length() > 0) s.append(" · ");
                s.append(i + 1).append(' ').append(d.optString("familyName"));
            }
            return new String[]{r.optString("round"), r.optString("raceName"), s.toString(), r.optString("date")};
        } catch (Exception e) {
            return null;
        }
    }
}
