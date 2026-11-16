package com.gathin.tvhomephone.sports;

import com.gathin.tvhomephone.live.FotMob;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One side's line-up as the match page draws it in the TV's line-ups-over-apps look: each player where he plays
 * (FotMob's own place for him on its pitch, else his line in the formation), his live rating, shirt number, position,
 * country and picture, and what he has done (goals, assists, cards, when he went off or came on, and for whom); then
 * the substitutes and the manager. Read from FotMob's match details when it has the game (its ratings, flags and
 * pictures, ESPN's exact positions put on them by shirt number), else from ESPN's line-ups (the ratings Ratings works
 * out). Pure, for the tests.
 */
final class TeamSheet {
    /** FotMob's pictures of a player (by its id) and of a country's flag (by its three-letter code). */
    private static final String FLAGS = "https://images.fotmob.com/image_resources/logo/teamlogo/";

    /** One player (or the manager). */
    static final class Man {
        String id = "", name = "", last = "", number = "", role = "", group = "", country = "", flag = "", photo = "";
        /** His live rating ("7.4"; "" until he has played). */
        String rating = "";
        /** When he came on and went off ("46'"; "" when not), and whom he came on for. */
        String on = "", off = "", replaced = "";
        int goals, assists, own, yellow, red;
        /** Where he plays: across (0 his side's left, 1 its right) and up (0 his own goal, 1 the other's); -1 not known. */
        float x = -1f, y = -1f;
    }

    String name = "", formation = "", rating = "", from = "";
    final List<Man> starters = new ArrayList<>(), subs = new ArrayList<>();
    Man manager;

    boolean has() {
        return !starters.isEmpty();
    }

    // ---- FotMob -------------------------------------------------------------------------------------------------

    /** Pure: both sides' sheets from FotMob's match details ({home, away}); null when it has no line-ups yet. */
    static TeamSheet[] fotmob(JSONObject details) {
        JSONObject content = details == null ? null : details.optJSONObject("content");
        JSONObject lu = content == null ? null : content.optJSONObject("lineup");
        if (lu == null) return null;
        TeamSheet home = side(lu.optJSONObject("homeTeam")), away = side(lu.optJSONObject("awayTeam"));
        return home.has() && away.has() ? new TeamSheet[]{home, away} : null;
    }

    private static TeamSheet side(JSONObject t) {
        TeamSheet s = new TeamSheet();
        s.from = "FotMob";
        if (t == null) return s;
        s.name = str(t, "name");
        s.formation = str(t, "formation");
        double r = t.optDouble("rating", Double.NaN);
        s.rating = Double.isNaN(r) || r <= 0 ? "" : String.format(Locale.UK, "%.1f", r);
        JSONArray starters = t.optJSONArray("starters"), subs = t.optJSONArray("subs");
        for (int i = 0; starters != null && i < starters.length(); i++) {
            Man m = man(starters.optJSONObject(i));
            if (m != null) s.starters.add(m);
        }
        for (int i = 0; subs != null && i < subs.length(); i++) {
            Man m = man(subs.optJSONObject(i));
            if (m != null) s.subs.add(m);
        }
        JSONObject coach = t.optJSONObject("coach");
        if (coach != null && !str(coach, "name").isEmpty()) {
            Man boss = new Man();
            boss.id = String.valueOf(coach.optInt("id"));
            boss.name = str(coach, "name");
            boss.last = str(coach, "lastName", boss.name);
            boss.country = str(coach, "countryName");
            boss.flag = flag(str(coach, "countryCode"));
            boss.photo = coach.optInt("id") > 0 ? FotMob.photoUrl(coach.optInt("id")) : "";
            boss.role = "Manager";
            s.manager = boss;
        }
        replacements(s);
        return s;
    }

    /** Pure: one of FotMob's line-up players. */
    static Man man(JSONObject p) {
        if (p == null || str(p, "name").isEmpty()) return null;
        Man m = new Man();
        int id = p.optInt("id");
        m.id = String.valueOf(id);
        m.name = str(p, "name");
        m.last = str(p, "lastName", lastWord(m.name));
        m.number = str(p, "shirtNumber");
        m.country = str(p, "countryName");
        m.flag = flag(str(p, "countryCode"));
        m.photo = id > 0 ? FotMob.photoUrl(id) : "";
        m.group = group(p.optInt("usualPlayingPositionId", -1), p.optInt("positionId", -1));
        JSONObject at = p.optJSONObject("verticalLayout");
        if (at != null && at.has("x") && at.has("y")) {
            // FotMob's x runs from the side's right; this sheet's from its left.
            m.x = 1f - (float) at.optDouble("x", 0.5);
            m.y = (float) at.optDouble("y", 0.5);
        }
        m.role = m.x >= 0 ? role(m.group, m.x, m.y) : m.group;
        JSONObject perf = p.optJSONObject("performance");
        if (perf != null) {
            double r = perf.optDouble("rating", Double.NaN);
            if (!Double.isNaN(r) && r > 0) m.rating = String.format(Locale.UK, "%.1f", r);
            JSONArray ev = perf.optJSONArray("events");
            for (int i = 0; ev != null && i < ev.length(); i++) {
                JSONObject e = ev.optJSONObject(i);
                String type = e == null ? "" : str(e, "type").toLowerCase(Locale.ROOT);
                if (type.contains("own")) m.own++;
                else if (type.startsWith("goal") || type.equals("penaltygoal")) m.goals++;
                else if (type.equals("assist")) m.assists++;
                else if (type.contains("yellowred") || type.contains("secondyellow")) {
                    m.yellow++;
                    m.red++;
                } else if (type.contains("red")) m.red++;
                else if (type.contains("yellow")) m.yellow++;
            }
            JSONArray subs = perf.optJSONArray("substitutionEvents");
            for (int i = 0; subs != null && i < subs.length(); i++) {
                JSONObject e = subs.optJSONObject(i);
                if (e == null) continue;
                String when = e.has("time") ? e.optInt("time") + "'" : "?";
                if ("subIn".equals(str(e, "type"))) m.on = when;
                else if ("subOut".equals(str(e, "type"))) m.off = when;
            }
        }
        return m;
    }

    /** Pure: keeper, defender, midfielder or forward from FotMob's usual position (0 to 3), else its position id. */
    static String group(int usual, int positionId) {
        switch (usual) {
            case 0: return "GK";
            case 1: return "DEF";
            case 2: return "MID";
            case 3: return "FWD";
            default:
                if (positionId == 11) return "GK";
                if (positionId >= 30 && positionId < 60) return "DEF";
                if (positionId >= 60 && positionId < 100) return "MID";
                return positionId >= 100 ? "FWD" : "";
        }
    }

    /**
     * Pure: his exact position from his line and where he stands in it (x from his side's left, y up the pitch): a full
     * back or a winger out wide, a holding, central or attacking midfielder by how far up, a centre forward up top.
     */
    static String role(String group, float x, float y) {
        boolean left = x < 0.22f, right = x > 0.78f, wide = left || right;
        switch (group) {
            case "GK": return "GK";
            case "DEF":
                if (wide) return (y > 0.36f ? (left ? "LWB" : "RWB") : (left ? "LB" : "RB"));
                return "CB";
            case "MID":
                if (wide) return y > 0.6f ? (left ? "LW" : "RW") : (left ? "LM" : "RM");
                return y < 0.45f ? "DM" : y < 0.6f ? "CM" : "AM";
            case "FWD":
                if ((x < 0.3f || x > 0.7f) && y < 0.8f) return x < 0.5f ? "LW" : "RW";
                return "ST";
            default: return group;
        }
    }

    /**
     * Pure: whom each substitute came on for: the one who went off at the same minute, when there was only one
     * (several changed at once can't be told apart).
     */
    static void replacements(TeamSheet s) {
        for (Man in : s.subs) {
            if (in.on.isEmpty()) continue;
            Man out = null;
            int n = 0;
            for (Man m : s.starters) if (in.on.equals(m.off)) { out = m; n++; }
            for (Man m : s.subs) if (m != in && in.on.equals(m.off)) { out = m; n++; }
            if (n == 1) in.replaced = out.last;
        }
    }

    /** Pure: ESPN's exact positions ("CD-L", "RB", "AM", "CF-R") put on FotMob's players by shirt number. */
    static void roles(TeamSheet s, List<String[]> espnLineup) {
        if (s == null || espnLineup == null) return;
        for (String[] p : espnLineup) {
            String role = espnRole(p[Espn.P_POSITION]);
            if (role.isEmpty() || p[Espn.P_NUMBER].isEmpty()) continue;
            for (Man m : s.starters) if (p[Espn.P_NUMBER].equals(m.number)) m.role = role;
            for (Man m : s.subs) if (p[Espn.P_NUMBER].equals(m.number)) m.role = role;
        }
    }

    /** Pure: ESPN's position as a sheet shows it ("CD-L" CB, "G" GK, "CF-R" CF); "" for a substitute's "SUB". */
    static String espnRole(String abbreviation) {
        String a = abbreviation == null ? "" : abbreviation.trim().toUpperCase(Locale.ROOT);
        if (a.endsWith("-L") || a.endsWith("-R")) a = a.substring(0, a.length() - 2);
        switch (a) {
            case "": case "SUB": return "";
            case "G": return "GK";
            case "CD": return "CB";
            case "F": return "FW";
            case "D": return "DEF";
            case "M": return "MID";
            default: return a;
        }
    }

    /** Pure: keeper, defender, midfielder or forward from an exact position. */
    static String groupOf(String role) {
        switch (role) {
            case "GK": return "GK";
            case "CB": case "LB": case "RB": case "LWB": case "RWB": case "SW": case "DEF": return "DEF";
            case "ST": case "CF": case "FW": case "LW": case "RW": case "FWD": return "FWD";
            case "": return "";
            default: return "MID";
        }
    }

    // ---- ESPN ---------------------------------------------------------------------------------------------------

    /**
     * Pure: a side's sheet from ESPN's line-up (Summary's P_* rows): the starters placed by their lines in the formation
     * (Formation), the keeper at the back, each line across from his side's left; the substitutes; Ratings' ratings.
     */
    static TeamSheet espn(List<String[]> lineup, String formation) {
        TeamSheet s = new TeamSheet();
        s.from = "ESPN";
        s.formation = formation == null ? "" : formation;
        if (lineup == null) return s;
        List<List<Integer>> rows = Formation.rows(s.formation, lineup);
        int n = rows.size();
        java.util.Set<Integer> placed = new java.util.HashSet<>();
        for (int r = 0; r < n; r++) {
            List<Integer> row = rows.get(r);
            for (int j = 0; j < row.size(); j++) {
                Man m = espnMan(lineup.get(row.get(j)));
                m.y = n == 1 ? 0.1f : 0.1f + 0.78f * r / (n - 1);
                m.x = (j + 1f) / (row.size() + 1f);
                if (m.role.isEmpty()) m.role = r == 0 ? "GK" : m.group;
                if (m.group.isEmpty()) m.group = groupOf(m.role);
                s.starters.add(m);
                placed.add(row.get(j));
            }
        }
        for (int i = 0; i < lineup.size(); i++) {
            String[] p = lineup.get(i);
            if (placed.contains(i)) continue;
            Man m = espnMan(p);
            if ("1".equals(p[Espn.P_STARTER])) s.starters.add(m);
            else s.subs.add(m);
        }
        replacements(s);
        return s;
    }

    private static Man espnMan(String[] p) {
        Man m = new Man();
        m.id = p.length > Espn.P_ID ? p[Espn.P_ID] : "";
        m.name = p[Espn.P_NAME];
        m.last = p[Espn.P_SHORT].isEmpty() ? lastWord(m.name) : p[Espn.P_SHORT];
        m.number = p[Espn.P_NUMBER];
        m.role = espnRole(p[Espn.P_POSITION]);
        m.group = groupOf(m.role);
        m.rating = p.length > Espn.P_RATING ? p[Espn.P_RATING] : "";
        m.goals = Espn.parseInt(p[Espn.P_GOALS]);
        m.own = Espn.parseInt(p[Espn.P_OWN]);
        String cards = p[Espn.P_CARDS];
        if (cards.contains("Y")) m.yellow = 1;
        if (cards.contains("R")) m.red = 1;
        m.on = minute(p[Espn.P_ON]);
        m.off = minute(p[Espn.P_OFF]);
        return m;
    }

    private static String minute(String clock) {
        if (clock == null || clock.isEmpty()) return "";
        return clock.endsWith("'") || clock.equals("?") ? clock : clock + "'";
    }

    // ---- reckoning ------------------------------------------------------------------------------------------------

    /** Pure: the best rating on these sheets ("" when none yet): the player of the match so far. */
    static String best(TeamSheet... sheets) {
        String best = "";
        double top = -1;
        for (TeamSheet s : sheets) {
            if (s == null) continue;
            for (List<Man> list : java.util.Arrays.asList(s.starters, s.subs)) {
                for (Man m : list) {
                    double v = value(m.rating);
                    if (v > top) {
                        top = v;
                        best = m.rating;
                    }
                }
            }
        }
        return best;
    }

    /** Pure: the substitutes in the order the sheet lists them: those who came on, soonest first, then the rest. */
    static List<Man> bench(TeamSheet s) {
        List<Man> on = new ArrayList<>(), rest = new ArrayList<>();
        for (Man m : s.subs) (m.on.isEmpty() ? rest : on).add(m);
        on.sort((a, b) -> Integer.compare(Game.minute(a.on), Game.minute(b.on)));
        on.addAll(rest);
        return on;
    }

    /** Pure: a rating's value, or -1 for none. */
    static double value(String rating) {
        try {
            return rating == null || rating.isEmpty() ? -1 : Double.parseDouble(rating);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Pure: a country's flag at FotMob by its three-letter code ("" for none). */
    static String flag(String code) {
        return code == null || code.isEmpty() ? "" : FLAGS + code.toLowerCase(Locale.ROOT) + ".png";
    }

    /** Pure: a name's initials for its disc when there is no picture: "Ashby Varley" → "AV", "Kettering" → "K". */
    static String initials(String name) {
        return TeamData.initials(name);
    }

    private static String lastWord(String name) {
        String n = name == null ? "" : name.trim();
        int sp = n.lastIndexOf(' ');
        return sp < 0 ? n : n.substring(sp + 1);
    }

    /** A field as text; the fallback when it is missing, null or empty (Android's optString gives "null" for a null). */
    private static String str(JSONObject o, String key, String fallback) {
        if (o == null || o.isNull(key)) return fallback;
        String s = o.optString(key, fallback);
        return s.isEmpty() ? fallback : s;
    }

    private static String str(JSONObject o, String key) {
        return str(o, key, "");
    }
}
