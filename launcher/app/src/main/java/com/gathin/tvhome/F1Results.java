package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** The current F1 race from ESPN's public scoreboard: its state, and the finishing order once it has finished. */
final class F1Results {
    static final class Race {
        String id, name, state;   // state: pre, in, post
        final List<String> top = new ArrayList<>();   // surnames in finishing order, up to ten
        final List<String> logos = new ArrayList<>(); // each one's team logo url, or null
    }

    private static final String FEED = "https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard";
    static final String F1_LOGO = "https://a.espncdn.com/i/teamlogos/leagues/500/f1.png";

    /** One session of the weekend: practice, sprint qualifying, sprint, qualifying or the race. */
    static final class Session {
        String id, abbr, label, state;   // abbr as ESPN writes it (FP1, SS, SR, Qual, Race); state: pre, in, post
        long start;
        int lap;   // laps run so far (ESPN's period); 0 when not known
        final List<String> top = new ArrayList<>();    // surnames in running order, up to ten
        final List<String> logos = new ArrayList<>();  // each one's team logo url, or null

        /** Qualifying, the sprint and the race: the sessions worth an alert. Practice is not. */
        boolean counts() {
            return !abbr.startsWith("FP");
        }

        boolean isRace() {
            return "Race".equals(abbr) || "SR".equals(abbr);
        }
    }

    /** The scoreboard's current weekend: the Grand Prix and its sessions in order. */
    static final class Weekend {
        String name;
        final List<Session> sessions = new ArrayList<>();
    }

    /** The weekend on the scoreboard (dates "yyyyMMdd" for a past one, or null for the current), or null. */
    static Weekend weekend(String dates) {
        try {
            JSONObject board = new JSONObject(get(dates == null ? FEED : FEED + "?dates=" + dates));
            JSONArray events = board.optJSONArray("events");
            if (events == null || events.length() == 0) return null;
            JSONObject e = events.getJSONObject(0);
            Weekend w = new Weekend();
            w.name = shortName(e.optString("name"));
            java.util.Map<String, String> teams = null;
            JSONArray comps = e.optJSONArray("competitions");
            for (int c = 0; comps != null && c < comps.length(); c++) {
                JSONObject comp = comps.getJSONObject(c);
                Session s = new Session();
                s.abbr = comp.optJSONObject("type") == null ? "" : comp.getJSONObject("type").optString("abbreviation");
                s.id = e.optString("id") + "|" + s.abbr;
                s.label = label(s.abbr);
                JSONObject status = comp.getJSONObject("status");
                s.state = status.getJSONObject("type").optString("state");
                s.lap = status.optInt("period", 0);
                s.start = java.time.OffsetDateTime.parse(comp.optString("date")).toInstant().toEpochMilli();
                learnIds(comp);
                if (!"pre".equals(s.state)) {
                    if (teams == null) teams = teamsCached();
                    JSONArray drivers = comp.optJSONArray("competitors");
                    List<JSONObject> list = new ArrayList<>();
                    for (int d = 0; drivers != null && d < drivers.length(); d++) list.add(drivers.getJSONObject(d));
                    list.sort((a, b) -> Integer.compare(a.optInt("order", 99), b.optInt("order", 99)));
                    for (JSONObject x : list) {
                        if (s.top.size() >= 22) break;
                        JSONObject a = x.optJSONObject("athlete");
                        String surname = GoalWatchService.surname(a == null ? "" : a.optString("displayName", a.optString("shortName")));
                        s.top.add(surname);
                        s.logos.add(logo(teams.get(key(surname))));
                    }
                }
                w.sessions.add(s);
            }
            return w;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** ESPN's id for each driver (by surname key), learnt from the scoreboard: his headshot is named by it. */
    private static final java.util.Map<String, String> espnIds = new java.util.concurrent.ConcurrentHashMap<>();
    private static long espnIdsAt;

    private static void learnIds(JSONObject comp) {
        JSONArray drivers = comp.optJSONArray("competitors");
        for (int d = 0; drivers != null && d < drivers.length(); d++) {
            JSONObject x = drivers.optJSONObject(d);
            JSONObject a = x == null ? null : x.optJSONObject("athlete");
            if (a == null || x.optString("id").isEmpty()) continue;
            espnIds.put(key(GoalWatchService.surname(a.optString("displayName", a.optString("shortName")))), x.optString("id"));
        }
    }

    /**
     * ESPN's headshot of a driver by his surname (all framed alike, in this season's team kit, on no background), or
     * null when ESPN does not know him. The scoreboard is read for the ids when they are not known yet.
     */
    static String espnHeadshot(String surname) {
        if (surname == null || surname.isEmpty()) return null;
        String k = key(surname);
        if (!espnIds.containsKey(k) && System.currentTimeMillis() - espnIdsAt > 30 * 60_000L) {
            espnIdsAt = System.currentTimeMillis();
            try {
                JSONObject board = new JSONObject(get(FEED));
                JSONArray events = board.optJSONArray("events");
                for (int i = 0; events != null && i < events.length(); i++) {
                    JSONArray comps = events.getJSONObject(i).optJSONArray("competitions");
                    for (int c = 0; comps != null && c < comps.length(); c++) learnIds(comps.getJSONObject(c));
                }
            } catch (Exception ignored) {
            }
        }
        String id = espnIds.get(k);
        return id == null ? null : "https://a.espncdn.com/i/headshots/rpm/players/full/" + id + ".png";
    }

    /** "Qual" to "QUALIFYING", "SS" to "SPRINT QUALI": the names the TV shows. */
    static String label(String abbr) {
        switch (abbr) {
            case "Qual": return "QUALIFYING";
            case "Race": return "RACE";
            case "SS": return "SPRINT QUALI";
            case "SR": return "SPRINT";
            default: return abbr.toUpperCase(java.util.Locale.ROOT);
        }
    }

    /** "Antonelli" to "ANT": the three letters F1's own graphics use for nearly every driver. */
    static String code(String surname) {
        String k = key(surname).replaceAll("[^a-z]", "");
        return (k.length() > 3 ? k.substring(0, 3) : k).toUpperCase(java.util.Locale.ROOT);
    }

    private static java.util.Map<String, String> teamCache;
    private static long teamCacheAt;

    /** teams(), read at most every six hours: a driver changes team between seasons, not between laps. */
    static synchronized java.util.Map<String, String> teamsCached() {
        if (teamCache == null || teamCache.isEmpty() || System.currentTimeMillis() - teamCacheAt > 6 * 60 * 60_000L) {
            teamCache = teams();
            teamCacheAt = System.currentTimeMillis();
        }
        return teamCache;
    }

    /** The race session of the scoreboard's event (dates "yyyyMMdd" for a past weekend, or null), or null. */
    static Race load(String dates) {
        try {
            JSONObject board = new JSONObject(get(dates == null ? FEED : FEED + "?dates=" + dates));
            JSONArray events = board.optJSONArray("events");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject e = events.getJSONObject(i);
                JSONArray comps = e.optJSONArray("competitions");
                for (int c = 0; comps != null && c < comps.length(); c++) {
                    JSONObject comp = comps.getJSONObject(c);
                    if (!"Race".equals(comp.optJSONObject("type") == null ? "" : comp.getJSONObject("type").optString("abbreviation"))) continue;
                    Race r = new Race();
                    r.id = comp.optString("id");
                    r.name = shortName(e.optString("name"));
                    r.state = comp.getJSONObject("status").getJSONObject("type").optString("state");
                    JSONArray drivers = comp.optJSONArray("competitors");
                    List<JSONObject> list = new ArrayList<>();
                    for (int d = 0; drivers != null && d < drivers.length(); d++) list.add(drivers.getJSONObject(d));
                    list.sort((a, b) -> Integer.compare(a.optInt("order", 99), b.optInt("order", 99)));
                    java.util.Map<String, String> teams = teamsCached();
                    for (JSONObject x : list) {
                        if (r.top.size() >= 22) break;
                        JSONObject a = x.optJSONObject("athlete");
                        String n = a == null ? "" : a.optString("displayName", a.optString("shortName"));
                        String surname = GoalWatchService.surname(n);
                        r.top.add(surname);
                        r.logos.add(logo(teams.get(key(surname))));
                    }
                    return r;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Driver (surname key) to team id for this season, from Jolpica's driver standings (the Ergast successor). */
    static java.util.Map<String, String> teams() {
        java.util.Map<String, String> out = new java.util.HashMap<>();
        try {
            JSONObject root = new JSONObject(get("https://api.jolpi.ca/ergast/f1/current/driverStandings.json"));
            JSONArray lists = root.getJSONObject("MRData").getJSONObject("StandingsTable").getJSONArray("StandingsLists");
            JSONArray standings = lists.getJSONObject(0).getJSONArray("DriverStandings");
            for (int i = 0; i < standings.length(); i++) {
                JSONObject st = standings.getJSONObject(i);
                JSONArray cons = st.getJSONArray("Constructors");
                String team = cons.getJSONObject(cons.length() - 1).optString("constructorId");   // the latest one
                out.put(key(st.getJSONObject("Driver").optString("familyName")), team);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** One driver's line in a classification or on the grid. */
    static final class Row {
        int pos;
        String first = "", last = "", code = "", team = "", gap = "", status = "";
        /** Jolpica's constructor id ("mclaren", "red_bull"): the team's colour and logo; "" when not known. */
        String teamId = "";
        int points = -1;
        /** His team's logo address, or null. */
        String logo;
    }

    /** A classification with its Grand Prix's name ("Mexico City Grand Prix"). */
    static final class Board {
        String name = "";
        final List<Row> rows = new ArrayList<>();
    }

    /** Whether two names are the same Grand Prix: "Mexico City Grand Prix" and ESPN's "Mexico City GP", alike. */
    static boolean sameGrandPrix(String a, String b) {
        if (a == null || b == null) return false;
        String x = key(a).replace("grand prix", "").replace("gp", "").trim(), y = key(b).replace("grand prix", "").replace("gp", "").trim();
        return !x.isEmpty() && !y.isEmpty() && (x.contains(y) || y.contains(x));
    }

    /**
     * The last race's (or sprint's) full result from Jolpica, every driver with his gap or time, points and how he
     * finished, when it is this Grand Prix's (gp null: whichever it is); null when it is not in yet or cannot be read.
     */
    static Board jolpicaResult(String gp, boolean sprint) {
        try {
            JSONObject root = new JSONObject(get("https://api.jolpi.ca/ergast/f1/current/last/" + (sprint ? "sprint" : "results") + ".json"));
            JSONArray races = root.getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races");
            if (races.length() == 0) return null;
            JSONObject race = races.getJSONObject(0);
            Board b = new Board();
            b.name = race.optString("raceName");
            if (gp != null && !sameGrandPrix(gp, b.name)) return null;
            JSONArray results = race.getJSONArray(sprint ? "SprintResults" : "Results");
            for (int i = 0; i < results.length(); i++) {
                JSONObject x = results.getJSONObject(i);
                Row r = driver(x);
                r.pos = i + 1;
                r.status = x.optString("status");
                String points = x.optString("points", "");
                try { r.points = (int) Math.round(Double.parseDouble(points)); } catch (NumberFormatException ignored) { }
                JSONObject time = x.optJSONObject("Time");
                if (time != null) r.gap = time.optString("time");
                else if (r.status.startsWith("+")) r.gap = r.status.replace(" Laps", " laps").replace(" Lap", " lap");
                else if (!"Finished".equals(r.status)) r.gap = "DNF";
                b.rows.add(r);
            }
            return b.rows.isEmpty() ? null : b;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The coming race's qualifying from Jolpica (each driver's best lap in the last part he reached), when it is this
     * Grand Prix's (gp null: whichever it is); null when it is not in yet or cannot be read.
     */
    static Board jolpicaQualifying(String gp) {
        try {
            JSONObject next = new JSONObject(get("https://api.jolpi.ca/ergast/f1/current/next.json"));
            JSONArray nr = next.getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races");
            String round = nr.length() > 0 ? nr.getJSONObject(0).optString("round") : "last";
            JSONObject root = new JSONObject(get("https://api.jolpi.ca/ergast/f1/current/" + round + "/qualifying.json"));
            JSONArray races = root.getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races");
            if (races.length() == 0) return null;
            JSONObject race = races.getJSONObject(0);
            Board b = new Board();
            b.name = race.optString("raceName");
            if (gp != null && !sameGrandPrix(gp, b.name)) return null;
            JSONArray results = race.getJSONArray("QualifyingResults");
            for (int i = 0; i < results.length(); i++) {
                JSONObject x = results.getJSONObject(i);
                Row r = driver(x);
                r.pos = i + 1;
                r.gap = x.optString("Q3", x.optString("Q2", x.optString("Q1", "")));
                b.rows.add(r);
            }
            return b.rows.isEmpty() ? null : b;
        } catch (Exception e) {
            return null;
        }
    }

    private static Row driver(JSONObject x) throws org.json.JSONException {
        Row r = new Row();
        JSONObject d = x.getJSONObject("Driver");
        r.first = d.optString("givenName");
        r.last = d.optString("familyName");
        r.code = d.optString("code", code(r.last));
        JSONObject team = x.optJSONObject("Constructor");
        if (team != null) {
            r.team = team.optString("name");
            r.teamId = team.optString("constructorId");
            r.logo = logo(r.teamId);
        }
        return r;
    }

    private static java.util.Map<String, String> fullNames;
    private static long fullNamesAt;

    /**
     * A driver's full name by his surname ("Norris" is Lando Norris), from this season's standings, read once a day;
     * null when he is not in them or they cannot be read. A photo search by surname alone finds the wrong Norris.
     */
    static synchronized String fullName(String surname) {
        long now = System.currentTimeMillis();
        if (fullNames == null || now - fullNamesAt > 86_400_000L) {
            java.util.Map<String, String> out = new java.util.HashMap<>();
            try {
                JSONObject root = new JSONObject(get("https://api.jolpi.ca/ergast/f1/current/driverStandings.json"));
                JSONArray lists = root.getJSONObject("MRData").getJSONObject("StandingsTable").getJSONArray("StandingsLists");
                JSONArray standings = lists.getJSONObject(0).getJSONArray("DriverStandings");
                for (int i = 0; i < standings.length(); i++) {
                    JSONObject dr = standings.getJSONObject(i).getJSONObject("Driver");
                    String family = dr.optString("familyName");
                    out.put(key(family), (dr.optString("givenName") + " " + family).trim());
                }
            } catch (Exception ignored) {
            }
            if (!out.isEmpty() || fullNames == null) { fullNames = out; fullNamesAt = out.isEmpty() ? now - 86_400_000L + 600_000L : now; }
        }
        return fullNames.get(key(surname));
    }

    /** A team's colour, as its cars and F1's graphics use it, by Jolpica's constructor id; F1's red for one not known. */
    static int teamColour(String id) {
        if (id == null) return 0xFFE10600;
        switch (id) {
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

    /** "Hülkenberg" and "Hulkenberg" alike: lower case without accents. */
    static String key(String name) {
        return java.text.Normalizer.normalize(name == null ? "" : name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).trim();
    }

    /** The team's coloured logo on formula1.com, by Jolpica's constructor id. */
    static String logo(String team) {
        if (team == null || team.isEmpty()) return null;
        java.util.Map<String, String> slug = new java.util.HashMap<>();
        slug.put("red_bull", "redbullracing");
        slug.put("rb", "racingbulls");
        slug.put("aston_martin", "astonmartin");
        String s = slug.containsKey(team) ? slug.get(team) : team.replace("_", "");
        return "https://media.formula1.com/image/upload/c_lfill,w_96/q_auto/v1740000001/common/f1/"
                + java.time.Year.now().getValue() + "/" + s + "/" + java.time.Year.now().getValue() + s + "logo.webp";
    }

    /** "MSC Cruises United States Grand Prix" to "United States Grand Prix": the sponsor goes. */
    static String shortName(String name) {
        String[] places = {"Grand Prix"};
        int gp = name.indexOf(places[0]);
        if (gp < 0) return name;
        String before = name.substring(0, gp).trim();
        String[] words = before.split(" ");
        // Keep the last one to three capitalised words before "Grand Prix" that look like a place.
        String[] known = {"United States", "Mexico City", "São Paulo", "Las Vegas", "Abu Dhabi", "Saudi Arabian", "Emilia Romagna", "Great Britain"};
        for (String k : known) if (before.endsWith(k)) return k + " Grand Prix";
        return (words.length == 0 ? "" : words[words.length - 1] + " ") + "Grand Prix";
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 2 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
