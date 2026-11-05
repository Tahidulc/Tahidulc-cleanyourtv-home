package com.gathin.tvhomephone.sports;

import com.gathin.tvhomephone.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The F1 session on now, from ESPN's racing scoreboard (as the TV's timing tower reads it): which session, the lap the
 * race is on and the running order. With it, what F1's own pictures show in their corner: the race's lap, practice's
 * time left, and in qualifying the part (Q1, Q2, Q3) with its time left, worked out from the session's start as the
 * parts are timed (18, 15 and 12 minutes, seven and eight minutes apart; the sprint's 12, 10 and 8). A red flag stops
 * F1's clock and not this one.
 */
public final class F1Live {
    private F1Live() {}

    private static final String FEED = "https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard";

    /** One session: ESPN's abbreviation (FP1, SS, SR, Qual, Race), its state (pre, in, post), start, lap, order. */
    public static final class Session {
        public String abbr = "", state = "", name = "";
        public long start;
        public int lap;
        /** The drivers' surnames in running order. */
        public final List<String> top = new ArrayList<>();

        public boolean race() {
            return "Race".equals(abbr) || "SR".equals(abbr);
        }

        public boolean live() {
            return "in".equals(state);
        }
    }

    /** The session running now, or null. Off the main thread. */
    public static Session now() {
        try {
            return running(Net.get(FEED));
        } catch (Exception e) {
            return null;
        }
    }

    /** Pure: the session ESPN's scoreboard has running, or null. */
    static Session running(String json) {
        try {
            JSONArray events = new JSONObject(json).optJSONArray("events");
            if (events == null || events.length() == 0) return null;
            JSONObject e = events.getJSONObject(0);
            JSONArray comps = e.optJSONArray("competitions");
            for (int c = 0; comps != null && c < comps.length(); c++) {
                JSONObject comp = comps.getJSONObject(c);
                JSONObject status = comp.optJSONObject("status");
                if (status == null) continue;
                String state = status.getJSONObject("type").optString("state");
                if (!"in".equals(state)) continue;
                Session s = new Session();
                s.state = state;
                s.name = shortName(e.optString("name"));
                s.abbr = comp.optJSONObject("type") == null ? "" : comp.getJSONObject("type").optString("abbreviation");
                s.lap = status.optInt("period", 0);
                s.start = java.time.OffsetDateTime.parse(comp.optString("date")).toInstant().toEpochMilli();
                JSONArray drivers = comp.optJSONArray("competitors");
                List<JSONObject> list = new ArrayList<>();
                for (int d = 0; drivers != null && d < drivers.length(); d++) list.add(drivers.getJSONObject(d));
                list.sort((a, b) -> Integer.compare(a.optInt("order", 99), b.optInt("order", 99)));
                for (JSONObject x : list) {
                    JSONObject a = x.optJSONObject("athlete");
                    String n = a == null ? "" : a.optString("displayName", a.optString("shortName"));
                    s.top.add(n.contains(" ") ? n.substring(n.lastIndexOf(' ') + 1) : n);
                }
                return s;
            }
        } catch (Exception ignored) {
            // none running
        }
        return null;
    }

    /** "Monaco Grand Prix" to "Monaco". */
    static String shortName(String n) {
        return n == null ? "" : n.replace(" Grand Prix", "").trim();
    }

    /** "Verstappen" to "VER": the three letters F1's graphics use. */
    public static String code(String surname) {
        String k = F1.key(surname).replaceAll("[^a-z]", "");
        return (k.length() > 3 ? k.substring(0, 3) : k).toUpperCase(Locale.ROOT);
    }

    /** The session's name as F1's graphics put it. */
    public static String label(String abbr) {
        switch (abbr == null ? "" : abbr) {
            case "Qual": return "QUALIFYING";
            case "Race": return "RACE";
            case "SS": return "SPRINT QUALI";
            case "SR": return "SPRINT";
            default: return abbr == null ? "" : abbr.toUpperCase(Locale.ROOT);
        }
    }

    /**
     * Pure: what F1's corner shows at the screen's moment at: {part, clock}. The race: {"LAP", "23"}. Practice: {"FP2",
     * "41:07"} (an hour, counting down). Qualifying: {"Q2", "08:31"} while a part runs, {"Q2", "NEXT"} between parts;
     * {"", ""} when the session's time is up or not started.
     */
    public static String[] corner(Session s, long at) {
        if (s == null) return new String[]{"", ""};
        if (s.race()) return new String[]{"LAP", s.lap > 0 ? String.valueOf(s.lap) : "1"};
        long since = at - s.start;
        if (since < 0) return new String[]{"", ""};
        int[] parts = "Qual".equals(s.abbr) ? new int[]{18, 7, 15, 8, 12} : "SS".equals(s.abbr) ? new int[]{12, 7, 10, 7, 8} : null;
        if (parts == null) {
            long left = 60 * 60_000L - since;
            return left <= 0 ? new String[]{"", ""} : new String[]{s.abbr.toUpperCase(Locale.ROOT), mmss(left)};
        }
        String prefix = "SS".equals(s.abbr) ? "SQ" : "Q";
        long t = 0;
        for (int i = 0; i < parts.length; i++) {
            long len = parts[i] * 60_000L;
            if (since < t + len) {
                int part = i / 2 + 1;
                if (i % 2 == 1) return new String[]{prefix + (part + 1), "NEXT"};
                return new String[]{prefix + part, mmss(t + len - since)};
            }
            t += len;
        }
        return new String[]{"", ""};
    }

    /** ms as "08:31", rounded up to the second as F1's clock counts down. */
    static String mmss(long ms) {
        long s = (ms + 999) / 1000;
        return String.format(Locale.UK, "%02d:%02d", s / 60, s % 60);
    }
}
