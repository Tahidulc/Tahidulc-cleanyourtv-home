package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A penalty shootout as ESPN's match summary has it ("shootout": each side's kicks in order, whether each went in), for
 * the strip under the scoreboard (BoardUnder): each side's kicks, the dots to draw (five each, one more each round of
 * sudden death), the tallies, whether it is decided. Pure: the tests check it.
 */
final class Shootout {
    private Shootout() { }

    /** One kick: whose (home or away), whether it went in, ESPN's play id (they go up as the kicks are taken), the taker. */
    static final class Kick {
        final boolean home, scored;
        final long id;
        final String taker;

        Kick(boolean home, boolean scored, long id, String taker) {
            this.home = home;
            this.scored = scored;
            this.id = id;
            this.taker = taker == null ? "" : taker;
        }
    }

    /** Pure: the shootout's kicks in the order they were taken (by ESPN's play ids); empty when there is none. */
    static List<Kick> kicks(JSONObject summary, String homeId, String awayId, String homeName) {
        List<Kick> out = new ArrayList<>();
        JSONArray sides = summary == null ? null : summary.optJSONArray("shootout");
        for (int i = 0; sides != null && i < sides.length(); i++) {
            JSONObject side = sides.optJSONObject(i);
            if (side == null) continue;
            String id = side.optString("id");
            boolean home;
            if (homeId != null && homeId.equals(id)) home = true;
            else if (awayId != null && awayId.equals(id)) home = false;
            else if (homeName != null && homeName.equalsIgnoreCase(side.optString("team"))) home = true;
            else continue;
            JSONArray shots = side.optJSONArray("shots");
            for (int k = 0; shots != null && k < shots.length(); k++) {
                JSONObject s = shots.optJSONObject(k);
                if (s == null) continue;
                long order;
                try {
                    order = Long.parseLong(s.optString("id"));
                } catch (NumberFormatException e) {
                    // no id: in turn after the others, by its number in the side's list
                    order = Long.MAX_VALUE / 2 + s.optInt("shotNumber", k + 1) * 2L + (home ? 0 : 1);
                }
                out.add(new Kick(home, s.optBoolean("didScore"), order, s.optString("player")));
            }
        }
        out.sort((a, b) -> Long.compare(a.id, b.id));
        return out;
    }

    /** Pure: whether the summary's game is in its shootout now (ESPN's status), or has kicks of one and is not over. */
    static boolean on(JSONObject summary) {
        JSONObject type = status(summary);
        String name = type == null ? "" : type.optString("name");
        if (name.contains("SHOOTOUT")) return true;
        JSONArray sides = summary == null ? null : summary.optJSONArray("shootout");
        return sides != null && sides.length() > 0 && type != null && !"post".equals(type.optString("state"));
    }

    /** Pure: whether the summary's game is over after a shootout (FT-Pens). */
    static boolean over(JSONObject summary) {
        JSONObject type = status(summary);
        return type != null && "post".equals(type.optString("state")) && summary.optJSONArray("shootout") != null
                && summary.optJSONArray("shootout").length() > 0;
    }

    private static JSONObject status(JSONObject summary) {
        JSONObject header = summary == null ? null : summary.optJSONObject("header");
        JSONArray comps = header == null ? null : header.optJSONArray("competitions");
        JSONObject comp = comps == null ? null : comps.optJSONObject(0);
        JSONObject st = comp == null ? null : comp.optJSONObject("status");
        return st == null ? null : st.optJSONObject("type");
    }

    /** Pure: one side's kicks, in order, from the first n kicks taken. */
    static List<Boolean> side(List<Kick> kicks, int n, boolean home) {
        List<Boolean> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, kicks.size()); i++) if (kicks.get(i).home == home) out.add(kicks.get(i).scored);
        return out;
    }

    /** Pure: the dots each side's row shows: five, then one more for each round of sudden death taken. */
    static int slots(int homeTaken, int awayTaken) {
        return Math.max(5, Math.max(homeTaken, awayTaken));
    }

    /** Pure: the same, with the next round of sudden death's empty dots once both have taken theirs and it goes on. */
    static int slots(int homeTaken, int awayTaken, boolean decided) {
        int n = slots(homeTaken, awayTaken);
        return !decided && homeTaken >= 5 && homeTaken == awayTaken ? n + 1 : n;
    }

    /**
     * Pure: how many kicks the screen has shown by now: each kick first read at seen[i] (in order), shown hold after it,
     * those first read together at the start all at once, every later one at least gap after the one before.
     */
    static int revealed(long[] seen, long hold, long gap, long now) {
        long last = Long.MIN_VALUE;
        int n = 0;
        for (int i = 0; i < seen.length; i++) {
            long at = seen[i] + hold;
            if (i > 0 && seen[i] != seen[0]) at = Math.max(at, last + gap);
            if (at > now) break;
            last = at;
            n++;
        }
        return n;
    }

    /** Pure: whether a game's short status says it is in (or into) its penalties ("Pens", "Shootout", "FT-Pens"). */
    static boolean likely(String detail) {
        String d = detail == null ? "" : detail.toLowerCase(java.util.Locale.ROOT);
        return d.contains("pen") || d.contains("shootout");
    }

    /** Pure: how many went in. */
    static int tally(List<Boolean> side) {
        int n = 0;
        for (Boolean b : side) if (Boolean.TRUE.equals(b)) n++;
        return n;
    }

    /**
     * Pure: whether it is decided: in the first five each, one side ahead by more than the other can still score; after
     * them, a round taken by both with one in front.
     */
    static boolean decided(List<Boolean> home, List<Boolean> away) {
        int sh = tally(home), sa = tally(away), nh = home.size(), na = away.size();
        if (nh <= 5 && na <= 5) {
            int leftH = 5 - nh, leftA = 5 - na;
            if (sh > sa + leftA || sa > sh + leftH) return true;
            return nh == 5 && na == 5 && sh != sa;
        }
        return nh == na && sh != sa;
    }
}
