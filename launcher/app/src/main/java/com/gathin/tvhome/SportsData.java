package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A club's season from ESPN's public site API, for Follow players, Your club's season and the weekend planner: the
 * club itself (its league, name, colours, place, next game), its games (played and to come), its squad, and its
 * league's table. Every read is off the main thread and kept a while in the app's settings; the parsing is pure, for
 * the tests.
 */
final class SportsData {
    private SportsData() { }

    private static final String SITE = "https://site.api.espn.com/apis/site/v2/sports/soccer/";
    private static final long TEAM_FOR = 12 * 3_600_000L, GAMES_FOR = 30 * 60_000L;

    /** A club: its id, league, names, colours, its place ("7th in English Premier League") and next game's id. */
    static final class Team {
        String id, league = "", name = "", abbr = "", standing = "", logo, nextEventId;
        /** Its next game: when, and as ESPN names it ("EVE @ TOT"); 0 and "" when none is known. */
        long nextAt;
        String nextName = "";
        int colour, alt;
    }

    /** One of a club's games: when, against whom, home or away, the score (-1 before it), and whether it is over. */
    static final class Fixture {
        String id, opponentId, opponent = "", opponentAbbr = "", competition = "";
        long at;
        boolean home, over, live;
        int us = -1, them = -1;
        final List<String> channels = new ArrayList<>();
    }

    /** A squad player: {ESPN id, name, shirt number, position}. */
    static final class Player {
        String id, name, number = "", position = "";
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("sports_data", Context.MODE_PRIVATE);
    }

    /** A page from ESPN, kept for keepFor (the kept one when ESPN can't be reached); null when there is none. */
    private static String cached(Context c, String url, long keepFor) {
        SharedPreferences p = prefs(c);
        String key = "u." + Integer.toHexString(url.hashCode());
        long at = p.getLong(key + ".at", 0);
        String kept = p.getString(key, null);
        if (kept != null && System.currentTimeMillis() - at < keepFor) return kept;
        String got = get(url);
        if (got != null) {
            p.edit().putString(key, got).putLong(key + ".at", System.currentTimeMillis()).apply();
            return got;
        }
        return kept;
    }

    static String get(String url) {
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(url).openConnection();
            h.setConnectTimeout(10_000);
            h.setReadTimeout(15_000);
            h.setRequestProperty("User-Agent", "TVHome");
            if (h.getResponseCode() != 200) return null;
            try (InputStream in = h.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16_384];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (h != null) h.disconnect();
        }
    }

    // ---- reading -----------------------------------------------------------------------------------------------------

    /** A club by its ESPN id (off the main thread); null when ESPN can't say. */
    static Team team(Context c, String id) {
        String body = cached(c, SITE + "all/teams/" + id, TEAM_FOR);
        return body == null ? null : parseTeam(body);
    }

    /** A club's games this season (fixtures true: those to come, else those played), oldest first. */
    static List<Fixture> games(Context c, String league, String teamId, boolean fixtures) {
        String body = cached(c, SITE + league + "/teams/" + teamId + "/schedule" + (fixtures ? "?fixture=true" : ""), GAMES_FOR);
        return body == null ? new ArrayList<>() : parseSchedule(body, teamId);
    }

    /** A club's squad. */
    static List<Player> squad(Context c, String league, String teamId) {
        String body = cached(c, SITE + league + "/teams/" + teamId + "/roster", TEAM_FOR);
        return body == null ? new ArrayList<>() : parseRoster(body);
    }

    /** A league's table: each club's id to its place (1 the top). */
    static Map<String, Integer> places(Context c, String league) {
        String body = cached(c, "https://site.api.espn.com/apis/v2/sports/soccer/" + league + "/standings", GAMES_FOR);
        return body == null ? new HashMap<>() : parsePlaces(body);
    }

    // ---- parsing (pure) --------------------------------------------------------------------------------------------

    static Team parseTeam(String body) {
        try {
            JSONObject t = new JSONObject(body).optJSONObject("team");
            if (t == null) return null;
            Team out = new Team();
            out.id = t.optString("id");
            out.name = t.optString("displayName", t.optString("name"));
            out.abbr = t.optString("abbreviation");
            out.standing = t.optString("standingSummary");
            out.colour = colour(t.optString("color"));
            out.alt = colour(t.optString("alternateColor"));
            Object league = t.opt("defaultLeague");
            out.league = league instanceof JSONObject ? ((JSONObject) league).optString("slug") : "";
            JSONArray logos = t.optJSONArray("logos");
            if (logos != null && logos.length() > 0) out.logo = logos.optJSONObject(0).optString("href", null);
            JSONArray next = t.optJSONArray("nextEvent");
            if (next != null && next.length() > 0) {
                JSONObject n = next.optJSONObject(0);
                out.nextEventId = n.optString("id", null);
                out.nextAt = time(n.optString("date"));
                out.nextName = n.optString("shortName", n.optString("name", ""));
            }
            return out;
        } catch (JSONException e) {
            return null;
        }
    }

    static int colour(String hex) {
        if (hex == null || !hex.matches("[0-9a-fA-F]{6}")) return 0;
        return 0xFF000000 | Integer.parseInt(hex, 16);
    }

    static List<Fixture> parseSchedule(String body, String teamId) {
        List<Fixture> out = new ArrayList<>();
        try {
            JSONArray events = new JSONObject(body).optJSONArray("events");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject e = events.optJSONObject(i);
                JSONArray comps = e == null ? null : e.optJSONArray("competitions");
                JSONObject comp = comps == null || comps.length() == 0 ? null : comps.optJSONObject(0);
                if (comp == null) continue;
                Fixture f = new Fixture();
                f.id = e.optString("id");
                f.at = time(comp.optString("date", e.optString("date")));
                JSONObject league = e.optJSONObject("league");
                f.competition = league != null ? league.optString("abbreviation", league.optString("name", "")) : "";
                JSONObject type = comp.optJSONObject("status") == null ? null : comp.optJSONObject("status").optJSONObject("type");
                String state = type == null ? "pre" : type.optString("state", "pre");
                f.over = "post".equals(state);
                f.live = "in".equals(state);
                JSONArray teams = comp.optJSONArray("competitors");
                for (int k = 0; teams != null && k < teams.length(); k++) {
                    JSONObject x = teams.optJSONObject(k);
                    JSONObject tm = x.optJSONObject("team");
                    String id = tm == null ? x.optString("id") : tm.optString("id", x.optString("id"));
                    int score = score(x.opt("score"));
                    if (teamId.equals(id)) {
                        f.home = "home".equals(x.optString("homeAway"));
                        f.us = score;
                    } else {
                        f.opponentId = id;
                        f.opponent = tm == null ? "" : tm.optString("shortDisplayName", tm.optString("displayName", ""));
                        f.opponentAbbr = tm == null ? "" : tm.optString("abbreviation", "");
                        f.them = score;
                    }
                }
                JSONArray casts = comp.optJSONArray("broadcasts");
                for (int k = 0; casts != null && k < casts.length(); k++) {
                    JSONObject m = casts.optJSONObject(k) == null ? null : casts.optJSONObject(k).optJSONObject("media");
                    String n = m == null ? "" : m.optString("shortName", "");
                    if (!n.isEmpty() && !f.channels.contains(n)) f.channels.add(n);
                }
                if (!f.over) { f.us = f.live ? f.us : -1; f.them = f.live ? f.them : -1; }
                out.add(f);
            }
        } catch (JSONException ignored) {
            // what was read
        }
        out.sort((a, b) -> Long.compare(a.at, b.at));
        return out;
    }

    /** A score as ESPN gives it: a number, its words, or an object with value or displayValue; -1 for none. */
    static int score(Object s) {
        if (s == null || s == JSONObject.NULL) return -1;
        if (s instanceof Number) return ((Number) s).intValue();
        if (s instanceof JSONObject) {
            JSONObject o = (JSONObject) s;
            if (o.has("value")) return (int) Math.round(o.optDouble("value", -1));
            return score(o.optString("displayValue", ""));
        }
        try {
            return Integer.parseInt(s.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static long time(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try {
            // ESPN writes "2026-11-08T15:00Z" (no seconds) as well as with them.
            String s = iso.matches(".*T\\d\\d:\\d\\dZ$") ? iso.replace("Z", ":00Z") : iso;
            return Instant.parse(s).toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    static List<Player> parseRoster(String body) {
        List<Player> out = new ArrayList<>();
        try {
            JSONArray a = new JSONObject(body).optJSONArray("athletes");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x == null) continue;
                // Some squads come in groups (goalkeepers, defenders…), each with its own items.
                JSONArray items = x.optJSONArray("items");
                if (items != null) {
                    for (int k = 0; k < items.length(); k++) add(out, items.optJSONObject(k));
                } else {
                    add(out, x);
                }
            }
        } catch (JSONException ignored) {
            // what was read
        }
        return out;
    }

    private static void add(List<Player> out, JSONObject x) {
        if (x == null || x.optString("displayName").isEmpty()) return;
        Player p = new Player();
        p.id = x.optString("id");
        p.name = x.optString("displayName");
        p.number = x.optString("jersey", "");
        JSONObject pos = x.optJSONObject("position");
        p.position = pos == null ? "" : pos.optString("abbreviation", "");
        out.add(p);
    }

    static Map<String, Integer> parsePlaces(String body) {
        Map<String, Integer> out = new HashMap<>();
        try {
            JSONObject root = new JSONObject(body);
            JSONArray children = root.optJSONArray("children");
            List<JSONObject> tables = new ArrayList<>();
            if (children != null) for (int i = 0; i < children.length(); i++) tables.add(children.optJSONObject(i));
            else tables.add(root);
            for (JSONObject t : tables) {
                JSONObject st = t == null ? null : t.optJSONObject("standings");
                JSONArray entries = st == null ? null : st.optJSONArray("entries");
                for (int i = 0; entries != null && i < entries.length(); i++) {
                    JSONObject e = entries.optJSONObject(i);
                    JSONObject tm = e.optJSONObject("team");
                    if (tm == null) continue;
                    int rank = i + 1;
                    JSONArray stats = e.optJSONArray("stats");
                    for (int k = 0; stats != null && k < stats.length(); k++) {
                        JSONObject s = stats.optJSONObject(k);
                        if (s != null && "rank".equals(s.optString("name"))) rank = (int) s.optDouble("value", rank);
                    }
                    out.put(tm.optString("id"), rank);
                }
            }
        } catch (JSONException ignored) {
            // what was read
        }
        return out;
    }
}
