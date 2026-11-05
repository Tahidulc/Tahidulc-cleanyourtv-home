package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A club's season (Your club's season, SeasonPanel): its league games played and to come, its place, its points game by
 * game, at home and away, its scorers (each game's goals from the scoreboard of its day, kept for good once the game
 * is over) and how hard each of the next five looks (the other side's place). Read off the main thread; the sums are
 * pure, for the tests.
 */
final class Season {
    private Season() { }

    /** All of it for one club. */
    static final class Data {
        SportsData.Team team;
        final List<SportsData.Fixture> played = new ArrayList<>(), next = new ArrayList<>();
        final Map<String, Integer> places = new HashMap<>();
        final Map<String, Integer> scorers = new HashMap<>();
    }

    /** Reads a club's season (off the main thread); null when ESPN can't say. */
    static Data read(Context c, String teamId) {
        SportsData.Team t = SportsData.team(c, teamId);
        if (t == null || t.league.isEmpty()) return null;
        Data d = new Data();
        d.team = t;
        for (SportsData.Fixture f : SportsData.games(c, t.league, teamId, false)) if (f.over && f.us >= 0) d.played.add(f);
        for (SportsData.Fixture f : SportsData.games(c, t.league, teamId, true)) {
            if (!f.over && d.next.size() < 5) d.next.add(f);
        }
        d.places.putAll(SportsData.places(c, t.league));
        scorers(c, t, d);
        return d;
    }

    /** Each game's scorers for the club, read once from its day's scoreboard and kept. */
    private static void scorers(Context c, SportsData.Team t, Data d) {
        SharedPreferences p = c.getApplicationContext().getSharedPreferences("season_goals", Context.MODE_PRIVATE);
        SharedPreferences.Editor e = p.edit();
        for (SportsData.Fixture f : d.played) {
            String kept = p.getString(f.id, null);
            if (kept == null) {
                JSONObject got = new JSONObject();
                LocalDate day = Instant.ofEpochMilli(f.at).atZone(ZoneId.systemDefault()).toLocalDate();
                for (ScoresTicker.Game g : ScoresTicker.load(new String[]{t.league}, day)) {
                    boolean home = t.id.equals(g.homeId), away = t.id.equals(g.awayId);
                    if (!home && !away) continue;
                    for (String[] goal : g.goals) {
                        // Ours: on our side, an own goal aside (that's the other side's player).
                        if (!(home ? "home" : "away").equals(goal[3]) || "og".equals(goal[4])) continue;
                        try {
                            got.put(goal[1], got.optInt(goal[1]) + 1);
                        } catch (JSONException ignored) {
                            // a name
                        }
                    }
                }
                kept = got.toString();
                e.putString(f.id, kept);
            }
            try {
                JSONObject o = new JSONObject(kept);
                for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                    String name = it.next();
                    Integer was = d.scorers.get(name);
                    d.scorers.put(name, (was == null ? 0 : was) + o.optInt(name));
                }
            } catch (JSONException ignored) {
                // none kept
            }
        }
        e.apply();
    }

    // ---- the sums (pure) ---------------------------------------------------------------------------------------------

    /** Pure: 3 for a win, 1 for a draw, 0 for a loss. */
    static int points(SportsData.Fixture f) {
        return f.us > f.them ? 3 : f.us == f.them ? 1 : 0;
    }

    /** Pure: the points after each game played, in order. */
    static int[] running(List<SportsData.Fixture> played) {
        int[] out = new int[played.size()];
        int sum = 0;
        for (int i = 0; i < out.length; i++) {
            sum += points(played.get(i));
            out[i] = sum;
        }
        return out;
    }

    /** Pure: {wins, draws, losses} at home (home true), away (false), or all (null). */
    static int[] record(List<SportsData.Fixture> played, Boolean home) {
        int[] r = new int[3];
        for (SportsData.Fixture f : played) {
            if (home != null && f.home != home) continue;
            r[f.us > f.them ? 0 : f.us == f.them ? 1 : 2]++;
        }
        return r;
    }

    /** Pure: how hard a game looks by the other side's place in a table of n: 2 hard (top third), 1, 0 easy. */
    static int difficulty(Integer place, int n) {
        if (place == null || n <= 0) return 1;
        if (place <= Math.max(1, n / 3)) return 2;
        if (place > n - Math.max(1, n / 3)) return 0;
        return 1;
    }

    /** Pure: the scorers, most goals first (then by name), at most max. */
    static List<Map.Entry<String, Integer>> top(Map<String, Integer> scorers, int max) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(scorers.entrySet());
        l.sort((a, b) -> b.getValue().equals(a.getValue()) ? a.getKey().compareTo(b.getKey()) : b.getValue() - a.getValue());
        return l.size() > max ? new ArrayList<>(l.subList(0, max)) : l;
    }

    /** Pure: "W5 D2 L4". */
    static String words(int[] r) {
        return "W" + r[0] + " D" + r[1] + " L" + r[2];
    }
}
