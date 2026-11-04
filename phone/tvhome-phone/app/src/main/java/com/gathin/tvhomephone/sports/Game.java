package com.gathin.tvhomephone.sports;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One football game as ESPN's scoreboard gives it (the TV's ScoresTicker.Game, for the phone): the two clubs, the
 * score, where the game is (before, live, finished), its clock, the goals and red cards so far, and its competition.
 * Kept as JSON between polls so the alerts can tell what changed.
 */
public final class Game {
    public static final int PRE = 1, LIVE = 0, POST = 2;

    public String id, slug, league, leagueName, leagueLogo;
    public String homeId, awayId, home, away, homeName, awayName, homeLogo, awayLogo, venue;
    /** The clubs' full names ("Manchester United"; homeName is ESPN's short one, "Man United"), "" when not given. */
    public String homeFull = "", awayFull = "";
    /** Each club's last five results, oldest first ("DWLLW"), and its colour ("ffff00"), "" when ESPN gives none. */
    public String homeForm = "", awayForm = "", homeColor = "", awayColor = "";
    /** Each club's second colour ("0a422d"), "" when ESPN gives none: the goal's chevron behind the crest. */
    public String homeAlt = "", awayAlt = "";
    public int homeScore, awayScore;
    /** PRE, LIVE or POST. */
    public int state = PRE;
    /** "45'+2", "HT", "FT", "19:45" as ESPN shortens it. */
    public String detail = "";
    public boolean halftime;
    public long kickoff;
    /** ESPN's game clock in seconds (whole minutes as it gives them), its period (1, 2, then extra time) and its words ("45'+2'"). */
    public double clock;
    public int period;
    public String displayClock = "";
    /** ESPN's own channel names for the game (mostly American), best first. */
    public List<String> broadcasts = new ArrayList<>();
    /** {key, player, minute, "home"/"away", kind ("pen", "og" or "")}, oldest first. */
    public List<String[]> goals = new ArrayList<>();
    /** {key, player, minute, "home"/"away"}. */
    public List<String[]> reds = new ArrayList<>();

    public boolean involves(java.util.Collection<String> teams) {
        return teams.contains(homeId) || teams.contains(awayId);
    }

    public String title() {
        return (homeName != null ? homeName : home) + " v " + (awayName != null ? awayName : away);
    }

    public String score() {
        return homeScore + "–" + awayScore;
    }

    /** The minute a goal was scored ("89'", "90'+3"), as a number: 89, 93. */
    public static int minute(String clock) {
        if (clock == null) return 0;
        int total = 0, cur = 0;
        boolean any = false;
        for (char ch : clock.toCharArray()) {
            if (Character.isDigit(ch)) { cur = cur * 10 + (ch - '0'); any = true; }
            else if (ch == '+') { total += cur; cur = 0; }
        }
        return any ? total + cur : 0;
    }

    public JSONObject json() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id).put("slug", slug).put("league", league).put("leagueName", leagueName).put("leagueLogo", leagueLogo)
                    .put("homeId", homeId).put("awayId", awayId).put("home", home).put("away", away)
                    .put("homeName", homeName).put("awayName", awayName).put("homeLogo", homeLogo).put("awayLogo", awayLogo)
                    .put("venue", venue).put("homeFull", homeFull).put("awayFull", awayFull).put("homeForm", homeForm)
                    .put("awayForm", awayForm).put("homeColor", homeColor).put("awayColor", awayColor).put("homeAlt", homeAlt).put("awayAlt", awayAlt).put("homeScore", homeScore).put("awayScore", awayScore).put("state", state)
                    .put("detail", detail).put("halftime", halftime).put("kickoff", kickoff)
                    .put("clock", clock).put("period", period).put("displayClock", displayClock)
                    .put("broadcasts", new JSONArray(broadcasts)).put("goals", rows(goals)).put("reds", rows(reds));
        } catch (JSONException ignored) {
            // plain fields
        }
        return o;
    }

    public static Game from(JSONObject o) {
        Game g = new Game();
        g.id = o.optString("id", null);
        g.slug = o.optString("slug", null);
        g.league = o.optString("league", "");
        g.leagueName = o.optString("leagueName", "");
        g.leagueLogo = o.optString("leagueLogo", null);
        g.homeId = o.optString("homeId", "");
        g.awayId = o.optString("awayId", "");
        g.home = o.optString("home", "");
        g.away = o.optString("away", "");
        g.homeName = o.optString("homeName", g.home);
        g.awayName = o.optString("awayName", g.away);
        g.homeLogo = o.optString("homeLogo", null);
        g.awayLogo = o.optString("awayLogo", null);
        g.venue = o.optString("venue", null);
        g.homeFull = o.optString("homeFull", "");
        g.awayFull = o.optString("awayFull", "");
        g.homeForm = o.optString("homeForm", "");
        g.awayForm = o.optString("awayForm", "");
        g.homeColor = o.optString("homeColor", "");
        g.awayColor = o.optString("awayColor", "");
        g.homeAlt = o.optString("homeAlt", "");
        g.awayAlt = o.optString("awayAlt", "");
        g.homeScore = o.optInt("homeScore");
        g.awayScore = o.optInt("awayScore");
        g.state = o.optInt("state", PRE);
        g.detail = o.optString("detail", "");
        g.halftime = o.optBoolean("halftime");
        g.kickoff = o.optLong("kickoff");
        g.clock = o.optDouble("clock", 0);
        g.period = o.optInt("period", 0);
        g.displayClock = o.optString("displayClock", "");
        JSONArray b = o.optJSONArray("broadcasts");
        for (int i = 0; b != null && i < b.length(); i++) g.broadcasts.add(b.optString(i));
        g.goals = unrows(o.optJSONArray("goals"));
        g.reds = unrows(o.optJSONArray("reds"));
        return g;
    }

    private static JSONArray rows(List<String[]> list) {
        JSONArray a = new JSONArray();
        for (String[] r : list) a.put(new JSONArray(java.util.Arrays.asList(r)));
        return a;
    }

    private static List<String[]> unrows(JSONArray a) {
        List<String[]> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONArray r = a.optJSONArray(i);
            if (r == null) continue;
            String[] row = new String[r.length()];
            for (int k = 0; k < row.length; k++) row[k] = r.optString(k);
            out.add(row);
        }
        return out;
    }
}
