package com.gathin.broadcast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Free stats worked out from what both apps already read (FotMob's match details, ESPN's summary), the same on the TV
 * and the phone, each as the board's stats moments take a line: {name, line, more ("" or a second line), "one"}, or null
 * when there is nothing worth saying. Nothing here is a paid figure, a guess or a price: the head to head, a side's run
 * of results, a goal drought, the league table as it stands, a big save or a great finish by FotMob's own chances, a
 * comeback, an early goal, and the referee's cards so far. Pure, for the tests.
 */
public final class MatchStats {
    private MatchStats() { }

    private static final String ONE = "one";

    // ---- head to head ---------------------------------------------------------------------------------------------

    /**
     * Pure: the head to head from FotMob's match details (or its content's "h2h" itself): the home side's record over the
     * last meetings ("Last 9: ESP 5W 2D 2L"), and when the away side last won away at the home side ("ENG last won away at
     * ESP in 2018"; "" when not in the meetings listed). Null with no finished meetings.
     */
    public static String[] h2h(JSONObject fotmob, int homeTeamId, int awayTeamId, String homeCode, String awayCode) {
        JSONObject h = h2hOf(fotmob);
        JSONArray ms = h == null ? null : h.optJSONArray("matches");
        if (ms == null) return null;
        String home = String.valueOf(homeTeamId), away = String.valueOf(awayTeamId);
        int w = 0, dr = 0, l = 0, n = 0, lastWinYear = 0;
        for (int i = 0; i < ms.length() && n < 10; i++) {
            JSONObject m = ms.optJSONObject(i);
            if (m == null) continue;
            JSONObject st = m.optJSONObject("status");
            if (st == null || !st.optBoolean("finished") || st.optBoolean("cancelled")) continue;
            int[] g = goals(st.optString("scoreStr"));
            String mh = idOf(m.optJSONObject("home")), ma = idOf(m.optJSONObject("away"));
            if (g == null || !(mh.equals(home) && ma.equals(away) || mh.equals(away) && ma.equals(home))) continue;
            n++;
            int ours = mh.equals(home) ? g[0] : g[1], theirs = mh.equals(home) ? g[1] : g[0];
            if (ours > theirs) w++;
            else if (ours == theirs) dr++;
            else l++;
            // The away side winning at the home side's own: the latest such (the list is newest first).
            if (lastWinYear == 0 && mh.equals(home) && theirs > ours) lastWinYear = year(m);
        }
        if (n == 0) return null;
        String line = "Last " + n + ": " + code(homeCode) + " " + w + "W " + dr + "D " + l + "L";
        String more = lastWinYear > 0 ? code(awayCode) + " last won away at " + code(homeCode) + " in " + lastWinYear : "";
        return new String[]{"Head to head", line, more, ONE};
    }

    private static JSONObject h2hOf(JSONObject o) {
        if (o == null) return null;
        if (o.has("matches")) return o;
        JSONObject c = o.optJSONObject("content");
        if (c != null && c.optJSONObject("h2h") != null) return c.optJSONObject("h2h");
        return o.optJSONObject("h2h");
    }

    private static int year(JSONObject m) {
        JSONObject t = m.optJSONObject("time");
        String u = t == null ? "" : t.optString("utcTime");
        if (u.length() >= 4) {
            try {
                return Integer.parseInt(u.substring(0, 4));
            } catch (NumberFormatException ignored) {
                // no year
            }
        }
        return 0;
    }

    // ---- a side's run, and its drought -----------------------------------------------------------------------------------

    /**
     * Pure: the best thing to say about a side's last results (FotMob's teamForm for that side, oldest first): won their
     * last 5 / 3 in a row, unbeaten, lost in a row, without a win, clean sheets in a row, scored in each; null when none of
     * them is so (fewer than three in a row).
     */
    public static String[] streak(JSONArray form, String code) {
        List<int[]> games = games(form);
        if (games.size() < 3) return null;
        int win = run(games, 0), unbeaten = run(games, 1), loss = run(games, 2), winless = run(games, 3), clean = run(games, 4),
                scored = run(games, 5), all = games.size();
        String c = code(code), line = null;
        if (win >= 3) line = win == all ? c + " have won their last " + win : c + " have won " + win + " in a row";
        else if (unbeaten >= 4) line = unbeaten == all ? c + " unbeaten in their last " + unbeaten : c + " unbeaten in " + unbeaten;
        else if (loss >= 3) line = loss == all ? c + " have lost their last " + loss : c + " have lost " + loss + " in a row";
        else if (winless >= 3) line = winless == all ? c + " without a win in their last " + winless : c + " without a win in " + winless;
        else if (clean >= 3) line = c + ": " + clean + " clean sheets in a row";
        else if (scored >= 5 && scored == all) line = c + " have scored in each of their last " + scored;
        return line == null ? null : new String[]{"Streak", line, "", ONE};
    }

    /**
     * Pure: a side without a goal in its last games (two or more), said once they have none today either by the half hour:
     * "RIV without a goal in their last 3 games"; null otherwise.
     */
    public static String[] drought(JSONArray form, String code, int goalsToday, int minute) {
        if (goalsToday > 0 || minute < 30) return null;
        List<int[]> games = games(form);
        int n = 0;
        for (int i = games.size() - 1; i >= 0 && games.get(i)[0] == 0; i--) n++;
        return n < 2 ? null : new String[]{"Goal drought", code(code) + " without a goal in their last " + n + " games", "", ONE};
    }

    /** A side's games from its form, oldest first: {its goals, their goals}. */
    private static List<int[]> games(JSONArray form) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; form != null && i < form.length(); i++) {
            JSONObject f = form.optJSONObject(i);
            if (f == null) continue;
            int[] g = goals(f.optString("score"));
            if (g == null) continue;
            JSONObject h = f.optJSONObject("home");
            boolean ourHome = h != null && h.optBoolean("isOurTeam");
            JSONObject a = f.optJSONObject("away");
            if (!ourHome && (a == null || !a.optBoolean("isOurTeam"))) continue;
            out.add(ourHome ? new int[]{g[0], g[1]} : new int[]{g[1], g[0]});
        }
        return out;
    }

    /** How many of the latest games in a row are so: 0 won, 1 not lost, 2 lost, 3 not won, 4 nothing let in, 5 scored. */
    private static int run(List<int[]> games, int kind) {
        int n = 0;
        for (int i = games.size() - 1; i >= 0; i--) {
            int us = games.get(i)[0], them = games.get(i)[1];
            boolean so;
            switch (kind) {
                case 0: so = us > them; break;
                case 1: so = us >= them; break;
                case 2: so = us < them; break;
                case 3: so = us <= them; break;
                case 4: so = them == 0; break;
                default: so = us > 0; break;
            }
            if (!so) break;
            n++;
        }
        return n;
    }

    // ---- the table as it stands --------------------------------------------------------------------------------------

    /**
     * Pure: where each side would be if the score stayed as it is, from ESPN's summary's standings (both sides in one of
     * its groups): "ENG 1st (up 1) · ESP 2nd (down 1)", a side's places moved said only when it moved. Null without both.
     */
    public static String[] asItStands(JSONObject standings, String homeId, String awayId, int homeGoals, int awayGoals, String homeCode,
            String awayCode) {
        JSONArray groups = standings == null ? null : standings.optJSONArray("groups");
        for (int gi = 0; groups != null && gi < groups.length(); gi++) {
            JSONObject g = groups.optJSONObject(gi);
            JSONObject s = g == null ? null : g.optJSONObject("standings");
            JSONArray es = s == null ? null : s.optJSONArray("entries");
            if (es == null) continue;
            List<double[]> rows = new ArrayList<>();
            int hi = -1, ai = -1;
            for (int i = 0; i < es.length(); i++) {
                JSONObject e = es.optJSONObject(i);
                if (e == null) continue;
                String id = e.optString("id");
                double pts = stat(e, "points"), gd = stat(e, "pointDifferential"), gf = stat(e, "pointsFor");
                if (id.equals(homeId)) hi = rows.size();
                if (id.equals(awayId)) ai = rows.size();
                rows.add(new double[]{pts, gd, gf, rows.size()});
            }
            if (hi < 0 || ai < 0) continue;
            int hBefore = place(rows, hi), aBefore = place(rows, ai);
            double[] h = rows.get(hi), a = rows.get(ai);
            h[0] += homeGoals > awayGoals ? 3 : homeGoals == awayGoals ? 1 : 0;
            a[0] += awayGoals > homeGoals ? 3 : homeGoals == awayGoals ? 1 : 0;
            h[1] += homeGoals - awayGoals;
            a[1] += awayGoals - homeGoals;
            h[2] += homeGoals;
            a[2] += awayGoals;
            int hNow = place(rows, hi), aNow = place(rows, ai);
            String line = code(homeCode) + " " + ordinal(hNow) + moved(hBefore, hNow) + " · " + code(awayCode) + " " + ordinal(aNow)
                    + moved(aBefore, aNow);
            return new String[]{"As it stands", line, "", ONE};
        }
        return null;
    }

    private static double stat(JSONObject entry, String name) {
        JSONArray st = entry.optJSONArray("stats");
        for (int i = 0; st != null && i < st.length(); i++) {
            JSONObject s = st.optJSONObject(i);
            if (s != null && name.equals(s.optString("name"))) return s.optDouble("value", 0);
        }
        return 0;
    }

    /** A row's place (1 the top) by points, then goal difference, then goals scored, then as listed. */
    private static int place(List<double[]> rows, int i) {
        double[] me = rows.get(i);
        int ahead = 0;
        for (int k = 0; k < rows.size(); k++) {
            if (k == i) continue;
            double[] o = rows.get(k);
            int c = Double.compare(o[0], me[0]);
            if (c == 0) c = Double.compare(o[1], me[1]);
            if (c == 0) c = Double.compare(o[2], me[2]);
            if (c == 0) c = Double.compare(me[3], o[3]);
            if (c > 0) ahead++;
        }
        return ahead + 1;
    }

    private static String moved(int before, int now) {
        if (now < before) return " (up " + (before - now) + ")";
        if (now > before) return " (down " + (now - before) + ")";
        return "";
    }

    /** Pure: a place as said ("1st", "2nd", "3rd", "11th", "22nd"). */
    public static String ordinal(int n) {
        int t = n % 100;
        if (t >= 11 && t <= 13) return n + "th";
        switch (n % 10) {
            case 1: return n + "st";
            case 2: return n + "nd";
            case 3: return n + "rd";
            default: return n + "th";
        }
    }

    // ---- big saves and great finishes -----------------------------------------------------------------------------------

    /**
     * Pure (but for shown, which it adds the shot's id to): the latest shot worth a word that has not had one, from FotMob's
     * shot map: a shot saved that was worth 0.45 or more on target ("Big save: 0.71 xGOT from Okafor's shot"), or a goal
     * from a chance worth 0.08 or less ("Great finish: Brandt scored a 0.05 xG chance"); null for none.
     */
    public static String[] shotMoment(JSONArray shots, Set<String> shown) {
        if (shots == null) return null;
        for (int i = shots.length() - 1; i >= 0; i--) {
            JSONObject s = shots.optJSONObject(i);
            if (s == null) continue;
            String id = s.optString("id", "");
            if (id.isEmpty() || shown != null && shown.contains(id)) continue;
            String type = s.optString("eventType"), who = lastName(s);
            String[] said = null;
            if ("AttemptSaved".equals(type) && s.optBoolean("isOnTarget") && !s.optBoolean("isBlocked")
                    && s.optDouble("expectedGoalsOnTarget", 0) >= 0.45) {
                said = new String[]{"Big save", String.format(Locale.UK, "%.2f xGOT from %s's shot", s.optDouble("expectedGoalsOnTarget", 0), who), "",
                        ONE};
            } else if ("Goal".equals(type) && !s.optBoolean("isOwnGoal") && !"Penalty".equalsIgnoreCase(s.optString("situation"))
                    && s.has("expectedGoals") && s.optDouble("expectedGoals", 1) <= 0.08) {
                said = new String[]{"Great finish", String.format(Locale.UK, "%s scored a %.2f xG chance", who, s.optDouble("expectedGoals", 0)), "",
                        ONE};
            }
            if (said == null) continue;
            if (shown != null) shown.add(id);
            return said;
        }
        return null;
    }

    private static String lastName(JSONObject s) {
        String l = s.optString("lastName", "").trim();
        if (!l.isEmpty()) return l;
        String n = s.optString("playerName", "").trim();
        int sp = n.lastIndexOf(' ');
        return sp < 0 ? n : n.substring(sp + 1);
    }

    // ---- comebacks and early goals --------------------------------------------------------------------------------

    /**
     * Pure: a comeback at the latest goal (sides[i] 1 home or 2 away, minutes[i] its minute, in order): when it put a side
     * level or ahead that had been two behind, or one behind at the hour: "WHI were 2–0 down", "… and now lead"; null else.
     */
    public static String[] comeback(int[] sides, int[] minutes, String homeCode, String awayCode) {
        if (sides == null || sides.length == 0 || minutes == null || minutes.length < sides.length) return null;
        int last = sides.length - 1, side = sides[last];
        if (side != 1 && side != 2) return null;
        int h = 0, a = 0, worst = 0, worstH = 0, worstA = 0, worstAt = -1;
        int[] diff = new int[sides.length];
        for (int i = 0; i <= last; i++) {
            if (sides[i] == 1) h++;
            else a++;
            diff[i] = side == 1 ? h - a : a - h;
            if (i < last && -diff[i] > worst) {
                worst = -diff[i];
                worstH = h;
                worstA = a;
                worstAt = i;
            }
        }
        if (!(worst >= 2 || worst == 1 && minutes[last] >= 60)) return null;
        int lead = diff[last], before = lead - 1;
        if (lead == 1 && before == 0) {
            // Ahead for the first time since they were behind.
            for (int i = worstAt + 1; i < last; i++) if (diff[i] > 0) return null;
        } else if (lead != 0) {
            return null;
        }
        String c = code(side == 1 ? homeCode : awayCode);
        int big = Math.max(worstH, worstA), small = Math.min(worstH, worstA);
        String line = c + " were " + big + "–" + small + " down" + (lead > 0 ? " and now lead" : "");
        return new String[]{"Comeback", line, "", ONE};
    }

    /** Pure: an early goal by its clock ("0:48", "3:12"): "Goal after 48 seconds", "Goal after 3:12"; null from five minutes. */
    public static String[] early(String clock) {
        if (clock == null) return null;
        String c = clock.trim();
        int colon = c.indexOf(':');
        if (colon <= 0) return null;
        try {
            int m = Integer.parseInt(c.substring(0, colon).trim()), s = Integer.parseInt(c.substring(colon + 1).trim());
            int all = m * 60 + s;
            if (all >= 300 || all < 0) return null;
            return new String[]{"Early goal", all < 60 ? "Goal after " + all + " seconds" : String.format(Locale.UK, "Goal after %d:%02d", m, s), "",
                    ONE};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- the referee -------------------------------------------------------------------------------------------------

    /** Pure: the referee and the cards so far ("Ref. M. Example · 4 yellows so far · 1 red"); null without a name. */
    public static String[] refCards(String referee, int yellows, int reds) {
        String n = referee == null ? "" : referee.trim();
        if (n.isEmpty()) return null;
        int sp = n.indexOf(' ');
        String shortName = sp > 0 ? n.charAt(0) + ". " + n.substring(sp + 1).trim() : n;
        StringBuilder b = new StringBuilder("Ref. ").append(shortName);
        if (yellows > 0) b.append(" · ").append(yellows).append(yellows == 1 ? " yellow" : " yellows").append(" so far");
        if (reds > 0) b.append(" · ").append(reds).append(reds == 1 ? " red" : " reds");
        return new String[]{"Referee", b.toString(), "", ONE};
    }

    // ---- small parts --------------------------------------------------------------------------------------------------

    /** Pure: a score as FotMob writes it ("2 - 3") as {first, second}; null when it is not one. */
    static int[] goals(String score) {
        if (score == null) return null;
        String[] p = score.split("-");
        if (p.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String idOf(JSONObject team) {
        return team == null ? "" : team.optString("id", "");
    }

    private static String code(String c) {
        return c == null ? "" : c.trim();
    }
}
