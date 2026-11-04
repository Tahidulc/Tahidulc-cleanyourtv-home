package com.gathin.tvhomephone.sports;

import com.gathin.tvhomephone.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Formula 1 from Jolpica's Ergast-style API, as the TV reads it: the drivers' and teams' standings, the next Grand Prix
 * with its sessions, the qualifying order (the grid) and the last race's result.
 */
public final class F1 {
    private F1() {}

    private static final String BASE = "https://api.jolpi.ca/ergast/f1/current";

    /** {position, name, team, points}. */
    public static List<String[]> drivers() {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray lists = new JSONObject(Net.get(BASE + "/driverStandings.json?limit=100")).getJSONObject("MRData")
                    .getJSONObject("StandingsTable").getJSONArray("StandingsLists");
            JSONArray s = lists.getJSONObject(0).getJSONArray("DriverStandings");
            for (int i = 0; i < s.length(); i++) {
                JSONObject d = s.getJSONObject(i), dr = d.getJSONObject("Driver");
                JSONArray cons = d.optJSONArray("Constructors");
                out.add(new String[]{d.optString("position", String.valueOf(i + 1)), dr.optString("givenName") + " " + dr.optString("familyName"),
                        cons != null && cons.length() > 0 ? cons.getJSONObject(0).optString("name") : "", d.optString("points")});
            }
        } catch (Exception ignored) {
            // none this time
        }
        return out;
    }

    /** {position, team, wins, points}. */
    public static List<String[]> teams() {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray lists = new JSONObject(Net.get(BASE + "/constructorStandings.json?limit=100")).getJSONObject("MRData")
                    .getJSONObject("StandingsTable").getJSONArray("StandingsLists");
            JSONArray s = lists.getJSONObject(0).getJSONArray("ConstructorStandings");
            for (int i = 0; i < s.length(); i++) {
                JSONObject d = s.getJSONObject(i);
                out.add(new String[]{d.optString("position", String.valueOf(i + 1)), d.getJSONObject("Constructor").optString("name"),
                        d.optString("wins"), d.optString("points")});
            }
        } catch (Exception ignored) {
            // none this time
        }
        return out;
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
