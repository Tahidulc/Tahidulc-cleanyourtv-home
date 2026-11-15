package com.gathin.broadcast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every player's shirt number in the games seen (ESPN's match summary: its "rosters", the starters and the substitutes),
 * for a goal's shirt turning to show the scorer's name and number. By ESPN's id for the player, a few games kept.
 */
public final class Shirts {
    private Shirts() { }

    private static final int KEEP = 12;
    private static final Map<String, Map<String, String[]>> games = new LinkedHashMap<String, Map<String, String[]>>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Map<String, String[]>> e) {
            return size() > KEEP;
        }
    };

    /** The game's squads as ESPN lists them (summary's "rosters"): each player's number and name, by his id. */
    public static synchronized void note(String gameId, JSONArray rosters) {
        if (gameId == null || rosters == null) return;
        Map<String, String[]> m = games.get(gameId);
        if (m == null) games.put(gameId, m = new HashMap<>());
        for (int i = 0; i < rosters.length(); i++) {
            JSONObject r = rosters.optJSONObject(i);
            JSONArray list = r == null ? null : r.optJSONArray("roster");
            for (int k = 0; list != null && k < list.length(); k++) {
                JSONObject p = list.optJSONObject(k);
                JSONObject a = p == null ? null : p.optJSONObject("athlete");
                if (a == null) continue;
                String id = a.optString("id", ""), num = p.optString("jersey", "").trim();
                if (id.isEmpty() || num.isEmpty()) continue;
                m.put(id, new String[]{num, a.optString("displayName", ""), a.optString("lastName", "")});
            }
        }
    }

    /** {number, his name as shown, his surname} for a player of the game, or null. */
    public static synchronized String[] of(String gameId, String athleteId) {
        Map<String, String[]> m = gameId == null ? null : games.get(gameId);
        return m == null || athleteId == null ? null : m.get(athleteId);
    }

    /** Pure: the name on a shirt's back: his surname in capitals ("VAN DER MOLEN"), else his name's last word. */
    public static String back(String surname, String name) {
        String s = surname == null ? "" : surname.trim();
        if (s.isEmpty()) {
            String n = name == null ? "" : name.trim();
            int sp = n.lastIndexOf(' ');
            s = sp >= 0 ? n.substring(sp + 1) : n;
        }
        return s.toUpperCase(java.util.Locale.ROOT);
    }

    /** Forgotten (tests). */
    static synchronized void clear() {
        games.clear();
    }
}
