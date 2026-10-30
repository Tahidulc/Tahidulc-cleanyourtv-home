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
