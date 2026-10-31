package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tennis and UFC for the alerts, from ESPN's public scoreboards: the ATP and WTA singles from the quarterfinals on
 * (the last sixteen at a major), and the main card of each UFC event. Off the main thread.
 */
final class MoreSports {
    static final String UFC_LOGO = "https://a.espncdn.com/i/teamlogos/leagues/500/ufc.png";
    private static final String FEED = "https://site.api.espn.com/apis/site/v2/sports/%s/scoreboard";

    /** One match or fight. */
    static final class Result {
        String id, tour, state, round, event;   // tour: ATP, WTA or UFC; state: pre, in, post
        String winner, loser, winnerFlag, loserFlag;   // before the end: the two sides, in ESPN's order
        String score;   // tennis: "6-4 7-6"; UFC: "KO/TKO · R1 3:54"
        boolean mainEvent;
        /** UFC: both fighters' ESPN ids (for their head-and-shoulders cut-outs) and full names, winner first. */
        String winnerId, loserId, winnerFull, loserFull;
    }

    static List<Result> tennis() {
        List<Result> out = new ArrayList<>();
        for (String tour : new String[]{"atp", "wta"}) {
            try {
                JSONObject board = new JSONObject(get(String.format(FEED, "tennis/" + tour)));
                JSONArray events = board.optJSONArray("events");
                for (int i = 0; events != null && i < events.length(); i++) {
                    JSONObject e = events.getJSONObject(i);
                    boolean major = e.optBoolean("major");
                    JSONArray groups = e.optJSONArray("groupings");
                    for (int g = 0; groups != null && g < groups.length(); g++) {
                        JSONObject group = groups.getJSONObject(g);
                        if (!group.getJSONObject("grouping").optString("displayName").contains("Singles")) continue;
                        JSONArray comps = group.optJSONArray("competitions");
                        for (int c = 0; comps != null && c < comps.length(); c++) {
                            JSONObject comp = comps.getJSONObject(c);
                            String round = comp.optJSONObject("round") == null ? "" : comp.getJSONObject("round").optString("displayName");
                            if (!counts(round, major)) continue;
                            Result r = match(comp, round);
                            if (r == null) continue;
                            r.tour = tour.toUpperCase(Locale.ROOT);
                            r.event = e.optString("shortName", e.optString("name"));
                            out.add(r);
                        }
                    }
                }
            } catch (Exception ignored) {
                // one tour failing leaves the other
            }
        }
        return out;
    }

    /** The rounds worth an alert: quarterfinals on, and at a major the round of sixteen too. */
    private static boolean counts(String round, boolean major) {
        if (round.contains("Qualifying")) return false;
        if (round.equals("Quarterfinal") || round.equals("Semifinal") || round.equals("Final")) return true;
        return major && (round.equals("Round 4") || round.contains("16"));
    }

    private static Result match(JSONObject comp, String round) throws Exception {
        JSONArray sides = comp.optJSONArray("competitors");
        if (sides == null || sides.length() != 2) return null;
        JSONObject a = sides.getJSONObject(0), b = sides.getJSONObject(1);
        if (a.optJSONObject("athlete") == null || b.optJSONObject("athlete") == null) return null;
        if (b.optBoolean("winner")) { JSONObject t = a; a = b; b = t; }
        Result r = new Result();
        r.id = "T|" + comp.optString("id");
        r.round = round;
        r.state = comp.getJSONObject("status").getJSONObject("type").optString("state");
        r.winner = GoalWatchService.surname(a.getJSONObject("athlete").optString("displayName"));
        r.loser = GoalWatchService.surname(b.getJSONObject("athlete").optString("displayName"));
        r.winnerFlag = flag(a.getJSONObject("athlete"));
        r.loserFlag = flag(b.getJSONObject("athlete"));
        JSONArray wa = a.optJSONArray("linescores"), wb = b.optJSONArray("linescores");
        StringBuilder score = new StringBuilder();
        for (int s = 0; wa != null && wb != null && s < Math.min(wa.length(), wb.length()); s++) {
            if (score.length() > 0) score.append(' ');
            score.append(wa.getJSONObject(s).optInt("value")).append('-').append(wb.getJSONObject(s).optInt("value"));
        }
        String detail = comp.getJSONObject("status").getJSONObject("type").optString("detail", "");
        if (detail.toLowerCase(Locale.ROOT).contains("ret")) score.append(" ret.");
        r.score = score.toString();
        return r;
    }

    /** The fights on each event's main card (the last start time on its card), the main event marked. */
    static List<Result> ufc() {
        List<Result> out = new ArrayList<>();
        try {
            JSONObject board = new JSONObject(get(String.format(FEED, "mma/ufc")));
            JSONArray events = board.optJSONArray("events");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject e = events.getJSONObject(i);
                JSONArray comps = e.optJSONArray("competitions");
                if (comps == null || comps.length() == 0) continue;
                String mainCard = "";
                for (int c = 0; c < comps.length(); c++) {
                    String d = comps.getJSONObject(c).optString("date");
                    if (d.compareTo(mainCard) > 0) mainCard = d;
                }
                for (int c = 0; c < comps.length(); c++) {
                    JSONObject comp = comps.getJSONObject(c);
                    if (!mainCard.equals(comp.optString("date"))) continue;
                    JSONArray sides = comp.optJSONArray("competitors");
                    if (sides == null || sides.length() != 2) continue;
                    JSONObject a = sides.getJSONObject(0), b = sides.getJSONObject(1);
                    if (b.optBoolean("winner")) { JSONObject t = a; a = b; b = t; }
                    Result r = new Result();
                    r.id = "U|" + comp.optString("id");
                    r.tour = "UFC";
                    r.event = e.optString("shortName", e.optString("name"));
                    r.mainEvent = c == comps.length() - 1;
                    r.round = comp.optJSONObject("type") == null ? "" : comp.getJSONObject("type").optString("abbreviation");
                    JSONObject status = comp.getJSONObject("status");
                    r.state = status.getJSONObject("type").optString("state");
                    r.winner = GoalWatchService.surname(a.getJSONObject("athlete").optString("displayName"));
                    r.loser = GoalWatchService.surname(b.getJSONObject("athlete").optString("displayName"));
                    r.winnerId = a.optString("id", null);
                    r.loserId = b.optString("id", null);
                    r.winnerFull = a.getJSONObject("athlete").optString("displayName");
                    r.loserFull = b.getJSONObject("athlete").optString("displayName");
                    r.winnerFlag = flag(a.getJSONObject("athlete"));
                    r.loserFlag = flag(b.getJSONObject("athlete"));
                    int round = status.optInt("period", 0);
                    r.score = method(comp.optJSONArray("details"))
                            + (round > 0 ? " · R" + round + " " + status.optString("displayClock", "") : "");
                    out.add(r);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** "Unofficial Winner Kotko" to "KO/TKO", "... Decision" to "Decision", "... Submission" to "Submission". */
    private static String method(JSONArray details) {
        for (int i = 0; details != null && i < details.length(); i++) {
            JSONObject type = details.optJSONObject(i) == null ? null : details.optJSONObject(i).optJSONObject("type");
            String t = type == null ? "" : type.optString("text");
            if (!t.startsWith("Unofficial Winner")) continue;
            String m = t.substring("Unofficial Winner".length()).trim();
            if (m.equalsIgnoreCase("Kotko")) return "KO/TKO";
            return m.isEmpty() ? "Win" : m;
        }
        return "Win";
    }

    private static String flag(JSONObject athlete) {
        JSONObject f = athlete.optJSONObject("flag");
        return f == null ? null : f.optString("href", null);
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(10000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 3 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
