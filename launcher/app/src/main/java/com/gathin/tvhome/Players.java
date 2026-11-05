package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Players followed (Follow players, PlayersPanel): their goals pop up wherever they play, in any game the scores'
 * poll reads (their clubs' competitions are read for them, and those games are announced for their goals only), the
 * goal card saying it is a player followed. Kept on this TV ("players"); matching a scorer is pure, for the tests.
 */
final class Players {
    private Players() { }

    /** A player followed: ESPN's id, the name, the club (its id, name and league). */
    static final class Followed {
        String id = "", name = "", teamId = "", team = "", league = "";

        JSONObject json() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id).put("name", name).put("teamId", teamId).put("team", team).put("league", league);
            } catch (JSONException ignored) {
                // all strings
            }
            return o;
        }

        static Followed from(JSONObject o) {
            Followed f = new Followed();
            f.id = o.optString("id");
            f.name = o.optString("name");
            f.teamId = o.optString("teamId");
            f.team = o.optString("team");
            f.league = o.optString("league");
            return f;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("players", Context.MODE_PRIVATE);
    }

    static List<Followed> list(Context c) {
        List<Followed> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Followed.from(a.getJSONObject(i)));
        } catch (JSONException ignored) {
            // none
        }
        return out;
    }

    private static void save(Context c, List<Followed> l) {
        JSONArray a = new JSONArray();
        for (Followed f : l) a.put(f.json());
        prefs(c).edit().putString("list", a.toString()).apply();
    }

    static boolean follows(Context c, String playerId) {
        for (Followed f : list(c)) if (f.id.equals(playerId)) return true;
        return false;
    }

    /** Follows this squad player (or stops). */
    static void set(Context c, SportsData.Player p, SportsData.Team t, boolean on) {
        List<Followed> l = list(c);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(p.id)) l.remove(i);
        if (on) {
            Followed f = new Followed();
            f.id = p.id;
            f.name = p.name;
            f.teamId = t.id;
            f.team = t.name;
            f.league = t.league;
            l.add(f);
        }
        save(c, l);
        AlertLog.note(c, (on ? "following " : "no longer following ") + p.name);
    }

    static void unfollow(Context c, String playerId, boolean on, Followed was) {
        List<Followed> l = list(c);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(playerId)) l.remove(i);
        if (on && was != null) l.add(was);
        save(c, l);
    }

    /** The clubs of the players followed (their games are read for them). */
    static Set<String> teamIds(Context c) {
        Set<String> out = new LinkedHashSet<>();
        for (Followed f : list(c)) if (!f.teamId.isEmpty()) out.add(f.teamId);
        return out;
    }

    /** Their clubs' competitions, as the poll's league entries. */
    static Set<String> leagues(Context c) {
        Set<String> out = new LinkedHashSet<>();
        for (Followed f : list(c)) if (!f.league.isEmpty()) out.add(f.league);
        return out;
    }

    /**
     * The player followed who scored this goal ({key, scorer, minute, "home"/"away", "pen"/"og"/""}) in game g, or
     * null: an own goal is never a followed player's.
     */
    static Followed scorer(Context c, ScoresTicker.Game g, String[] goal) {
        if (g == null || goal == null || goal.length < 5 || "og".equals(goal[4])) return null;
        String team = "home".equals(goal[3]) ? g.homeId : "away".equals(goal[3]) ? g.awayId : null;
        return match(list(c), goal[1], team);
    }

    /** Pure: the player among these whose name is the scorer's (the full name, or the surname at the same club). */
    static Followed match(List<Followed> followed, String scorer, String teamId) {
        String k = key(scorer);
        if (k.isEmpty()) return null;
        for (Followed f : followed) if (key(f.name).equals(k)) return f;
        String sur = surname(scorer);
        for (Followed f : followed) {
            if (sur.isEmpty() || teamId == null || !teamId.equals(f.teamId)) continue;
            if (surname(f.name).equals(sur)) return f;
        }
        return null;
    }

    /** Pure: a name to compare: no accents, lower case, letters only ("Martin Ødegaard" and "Martin Odegaard" alike). */
    static String key(String name) {
        if (name == null) return "";
        String n = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.replace("ø", "o").replace("Ø", "O").replace("ß", "ss").replace("æ", "ae").replace("Æ", "AE");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    /** Pure: a name's last word, compared as key does ("M. Salah" and "Mohamed Salah" both "salah"). */
    static String surname(String name) {
        if (name == null) return "";
        String[] w = name.trim().split("\\s+");
        return w.length == 0 ? "" : key(w[w.length - 1]);
    }
}
