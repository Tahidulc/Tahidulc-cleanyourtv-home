package com.gathin.broadcast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The penalty card, the same on the TV and the phone: a penalty given in the game, out of the scoreboard until its kick
 * is taken. From ESPN's commentary, which says when one is given ("Penalty Norway. Andreas Schjelderup draws a foul in
 * the penalty area.", "Penalty conceded by Connor Roberts (Wales) after a foul in the penalty area.", "VAR Decision:
 * Penalty Denmark.") and how it went ("converts the penalty", "Penalty saved.", "Penalty missed!").
 *
 * <p>Its pages: who won it and who gave it away; the likely taker (of the side's players on the pitch, the one who has
 * taken the most penalties in the competition this season, else last season) and his record; the keeper facing it and
 * how many he has saved. The records are ESPN's season figures for the competition (penalty goals and kicks taken, and
 * for a keeper penalties faced and saved); a page with no record to show is left out, never made up.
 */
public final class Penalty {
    private Penalty() { }

    /** A penalty given and not yet taken: the side it is for, who won it, who gave it away (each "" when not said). */
    public static final class Given {
        public String team = "", wonBy = "", concededBy = "", concededTeam = "", minute = "";
        /** Given after a VAR check (VAR's decision says so). */
        public boolean var;

        /** The same penalty read again has the same key. */
        public String key() {
            return minute + "|" + team;
        }
    }

    // ICU and java.util.regex alike: no possessives, no lookbehind.
    private static final Pattern AWARD = Pattern.compile("^Penalty ([^.]+)\\. (.+?) draws a foul in the penalty area");
    private static final Pattern CONCEDED = Pattern.compile("^Penalty conceded by (.+?) \\(([^()]+)\\)");
    private static final Pattern VAR = Pattern.compile("^VAR Decision: Penalty ([^.]+)");

    /** Pure: whether a commentary line is the kick (scored, saved, missed) or a penalty taken back. */
    static boolean taken(String t) {
        String s = t == null ? "" : t.trim();
        String low = s.toLowerCase(Locale.ROOT);
        return low.startsWith("penalty saved") || low.startsWith("penalty missed") || low.contains("converts the penalty")
                || low.startsWith("var decision: no penalty") || low.startsWith("goal!") && low.contains("penalty");
    }

    /** Pure: a commentary minute ("85'", "90'+3'") as minutes from kick-off (93); -1 when it is none. */
    static int minuteOf(String m) {
        if (m == null) return -1;
        int total = 0, n = 0;
        boolean any = false;
        for (int i = 0; i < m.length(); i++) {
            char ch = m.charAt(i);
            if (ch >= '0' && ch <= '9') {
                n = n * 10 + (ch - '0');
                any = true;
            } else if (ch == '+') {
                total += n;
                n = 0;
            }
        }
        return any ? total + n : -1;
    }

    /** A penalty given this many minutes before the commentary's latest line, with no kick said, is over. */
    static final int STALE_MINUTES = 6;

    /**
     * Pure: the penalty given in the game whose kick is still to come, from its commentary ({text, minute, sequence}
     * a line, oldest first); null when there is none. Its side is named as the commentary names it (else as the other
     * side of who gave it away: homeName or awayName).
     */
    public static Given pending(List<String[]> lines, String homeName, String awayName) {
        Given cur = null;
        int last = -1;
        for (String[] l : lines == null ? new ArrayList<String[]>() : lines) {
            String t = l == null || l.length == 0 || l[0] == null ? "" : l[0].trim();
            int at = minuteOf(l != null && l.length > 1 ? l[1] : null);
            if (at >= 0) last = Math.max(last, at);
            if (taken(t)) {
                cur = null;
                continue;
            }
            Matcher a = AWARD.matcher(t), c = CONCEDED.matcher(t), v = VAR.matcher(t);
            boolean award = a.find(), conceded = !award && c.find(), var = !award && !conceded && v.find();
            if (!award && !conceded && !var) continue;
            String minute = l.length > 1 && l[1] != null ? l[1] : "";
            // Another penalty, minutes on from one never taken: this one now.
            if (cur == null || Math.abs(minuteOf(cur.minute) - minuteOf(minute)) > 3) {
                cur = new Given();
                cur.minute = minute;
            }
            if (award) {
                cur.team = a.group(1).trim();
                cur.wonBy = a.group(2).trim();
            } else if (conceded) {
                cur.concededBy = c.group(1).trim();
                cur.concededTeam = c.group(2).trim();
            } else {
                cur.team = v.group(1).trim();
                cur.var = true;
            }
        }
        if (cur == null) return null;
        if (last >= 0 && minuteOf(cur.minute) >= 0 && last - minuteOf(cur.minute) > STALE_MINUTES) return null;
        if (cur.team.isEmpty() && !cur.concededTeam.isEmpty()) {
            if (same(cur.concededTeam, homeName)) cur.team = awayName == null ? "" : awayName;
            else if (same(cur.concededTeam, awayName)) cur.team = homeName == null ? "" : homeName;
        }
        return cur.team.isEmpty() ? null : cur;
    }

    /** Pure: 1 when the penalty is the home side's, 2 the away side's, 0 when its side's name matches neither. */
    public static int side(Given g, String homeName, String awayName) {
        if (g == null) return 0;
        if (same(g.team, homeName)) return 1;
        if (same(g.team, awayName)) return 2;
        if (same(g.concededTeam, homeName)) return 2;
        if (same(g.concededTeam, awayName)) return 1;
        return 0;
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && !a.isEmpty() && a.trim().equalsIgnoreCase(b.trim());
    }

    // ---- the players on the pitch -----------------------------------------------------------------------------------

    /** One player on the pitch: ESPN's id, his name, whether he is the keeper. */
    public static final class OnPitch {
        public final String id, name;
        public final boolean keeper;

        OnPitch(String id, String name, boolean keeper) {
            this.id = id;
            this.name = name;
            this.keeper = keeper;
        }
    }

    /**
     * Pure: a side's players on the pitch from a match summary's rosters (homeAway "home" or "away"): those who started
     * and have not gone off, and those who came on and have not gone off.
     */
    public static List<OnPitch> onPitch(JSONArray rosters, boolean home) {
        List<OnPitch> out = new ArrayList<>();
        for (int i = 0; rosters != null && i < rosters.length(); i++) {
            JSONObject r = rosters.optJSONObject(i);
            if (r == null || !(home ? "home" : "away").equals(r.optString("homeAway"))) continue;
            JSONArray list = r.optJSONArray("roster");
            for (int k = 0; list != null && k < list.length(); k++) {
                JSONObject p = list.optJSONObject(k);
                if (p == null) continue;
                boolean on = (p.optBoolean("starter") || p.optBoolean("subbedIn")) && !p.optBoolean("subbedOut");
                JSONObject a = p.optJSONObject("athlete");
                if (!on || a == null || a.optString("id").isEmpty()) continue;
                JSONObject pos = p.optJSONObject("position");
                boolean keeper = pos != null && "G".equals(pos.optString("abbreviation"));
                out.add(new OnPitch(a.optString("id"), a.optString("displayName", a.optString("shortName", "")), keeper));
            }
        }
        return out;
    }

    // ---- the records ------------------------------------------------------------------------------------------------

    /** Where a player's figures for the competition's season are: ESPN's statistics for him. */
    public static String statsUrl(String slug, int season, String athleteId) {
        return "https://sports.core.api.espn.com/v2/sports/soccer/leagues/" + slug + "/seasons/" + season + "/types/1/athletes/"
                + athleteId + "/statistics";
    }

    /** Pure: the season's year in a summary's header (2026 for 2026-27); 0 when not given. */
    public static int season(JSONObject summary) {
        JSONObject h = summary == null ? null : summary.optJSONObject("header");
        JSONObject s = h == null ? null : h.optJSONObject("season");
        return s == null ? 0 : s.optInt("year", 0);
    }

    /**
     * Pure: from ESPN's statistics for a player, {penalties scored, penalties taken, penalties faced, penalties saved};
     * null when it has none of them.
     */
    public static int[] figures(JSONObject stats) {
        JSONObject splits = stats == null ? null : stats.optJSONObject("splits");
        JSONArray cats = splits == null ? null : splits.optJSONArray("categories");
        int[] f = new int[4];
        boolean any = false;
        for (int i = 0; cats != null && i < cats.length(); i++) {
            JSONObject cat = cats.optJSONObject(i);
            JSONArray st = cat == null ? null : cat.optJSONArray("stats");
            for (int k = 0; st != null && k < st.length(); k++) {
                JSONObject s = st.optJSONObject(k);
                if (s == null) continue;
                int at;
                switch (s.optString("name")) {
                    case "penaltyKickGoals": at = 0; break;
                    case "penaltyKickShots": at = 1; break;
                    case "penaltyKicksFaced": at = 2; break;
                    case "penaltyKicksSaved": at = 3; break;
                    default: at = -1;
                }
                if (at < 0) continue;
                f[at] = (int) Math.round(s.optDouble("value", 0));
                any = true;
            }
        }
        return any ? f : null;
    }

    /**
     * Pure: which of the players is likely to take it: the one who has taken the most this season (then scored the
     * most), else the most last season; -1 when none of them has taken one in either. Figures null where not known.
     */
    public static int likelyTaker(int[][] thisSeason, int[][] lastSeason) {
        int best = -1, bestTaken = 0, bestScored = 0;
        for (int i = 0; thisSeason != null && i < thisSeason.length; i++) {
            int[] f = thisSeason[i];
            if (f == null || f[1] <= 0) continue;
            if (f[1] > bestTaken || f[1] == bestTaken && f[0] > bestScored) {
                best = i;
                bestTaken = f[1];
                bestScored = f[0];
            }
        }
        if (best >= 0) return best;
        for (int i = 0; lastSeason != null && i < lastSeason.length; i++) {
            int[] f = lastSeason[i];
            if (f == null || f[1] <= 0) continue;
            if (f[1] > bestTaken || f[1] == bestTaken && f[0] > bestScored) {
                best = i;
                bestTaken = f[1];
                bestScored = f[0];
            }
        }
        return best;
    }

    /** Pure: the taker's record: "Scored 4 of 5 this season" (else last season's); null with none taken in either. */
    public static String takerLine(int[] now, int[] last) {
        if (now != null && now[1] > 0) return "Scored " + now[0] + " of " + now[1] + " this season";
        if (last != null && last[1] > 0) return "Scored " + last[0] + " of " + last[1] + " last season";
        return null;
    }

    /** Pure: the keeper's record: "Saved 1 of 6 this season", "Faced 3 this season, saved none"; null with none faced. */
    public static String keeperLine(int[] now, int[] last) {
        int[] f = now != null && now[2] > 0 ? now : last != null && last[2] > 0 ? last : null;
        if (f == null) return null;
        String when = f == now ? "this season" : "last season";
        if (f[3] <= 0) return "Faced " + f[2] + " " + when + ", saved none";
        return "Saved " + f[3] + " of " + f[2] + " " + when;
    }

    /** Pure: the first page's line: "Won by Schjelderup · given away by Roberts" (or either alone, or "After a VAR check"). */
    public static String wonLine(Given g) {
        String won = g.wonBy.isEmpty() ? "" : "Won by " + surname(g.wonBy);
        String gave = g.concededBy.isEmpty() ? "" : (won.isEmpty() ? "Given away by " : "given away by ") + surname(g.concededBy);
        String both = won.isEmpty() ? gave : gave.isEmpty() ? won : won + " · " + gave;
        if (both.isEmpty()) return g.var ? "After a VAR check" : "";
        return g.var && !both.contains("VAR") ? both + " · after VAR" : both;
    }

    // ---- the records, looked up --------------------------------------------------------------------------------------

    /** How the app reads a page (its own client, a generic user agent); throws when it cannot. */
    public interface Fetch {
        String get(String url) throws Exception;
    }

    /** What the card's records say: the likely taker and his line, the keeper and his (each null when none). */
    public static final class Records {
        public String taker, takerLine, keeper, keeperLine;
    }

    /** Each player's figures as read, by their address: read once a session (a side can have two penalties). */
    private static final java.util.LinkedHashMap<String, int[]> read = new java.util.LinkedHashMap<String, int[]>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, int[]> eldest) {
            return size() > 200;
        }
    };
    private static final int[] NONE = new int[0];

    /** Never longer than this for all the lookups: the card comes out with what is known by then. */
    static final long LOOK_MS = 8_000L;

    /**
     * Off the main thread: the records for a penalty to the home side (home) or the away side, from the game's summary
     * (its players on the pitch and its season) and each player's figures in the competition (slug), this season and,
     * where this season has none, the last.
     */
    public static Records records(JSONObject summary, String slug, boolean home, Fetch fetch) {
        Records r = new Records();
        int season = season(summary);
        JSONArray rosters = summary == null ? null : summary.optJSONArray("rosters");
        if (season <= 0 || slug == null || fetch == null) return r;
        long until = System.currentTimeMillis() + LOOK_MS;
        List<OnPitch> mine = onPitch(rosters, home), theirs = onPitch(rosters, !home);
        List<OnPitch> takers = new ArrayList<>();
        for (OnPitch p : mine) if (!p.keeper) takers.add(p);
        int[][] now = new int[takers.size()][], last = new int[takers.size()][];
        for (int i = 0; i < takers.size() && System.currentTimeMillis() < until; i++) now[i] = look(fetch, statsUrl(slug, season, takers.get(i).id));
        int at = likelyTaker(now, null);
        if (at < 0) {
            for (int i = 0; i < takers.size() && System.currentTimeMillis() < until; i++) last[i] = look(fetch, statsUrl(slug, season - 1, takers.get(i).id));
            at = likelyTaker(now, last);
        }
        if (at >= 0) {
            r.taker = takers.get(at).name;
            r.takerLine = takerLine(now[at], last[at]);
        }
        OnPitch keeper = null;
        for (OnPitch p : theirs) if (p.keeper) { keeper = p; break; }
        if (keeper != null && System.currentTimeMillis() < until) {
            int[] kn = look(fetch, statsUrl(slug, season, keeper.id));
            int[] kl = kn != null && kn[2] > 0 || System.currentTimeMillis() >= until ? null : look(fetch, statsUrl(slug, season - 1, keeper.id));
            r.keeper = keeper.name;
            r.keeperLine = keeperLine(kn, kl);
        }
        return r;
    }

    private static int[] look(Fetch fetch, String url) {
        synchronized (read) {
            int[] f = read.get(url);
            if (f != null) return f == NONE ? null : f;
        }
        int[] f = null;
        try {
            f = figures(new JSONObject(fetch.get(url)));
        } catch (Exception ignored) {
            // no figures for him there
        }
        synchronized (read) {
            read.put(url, f == null ? NONE : f);
        }
        return f;
    }

    /** Pure: a name's last word ("Andreas Schjelderup" → "Schjelderup"). */
    public static String surname(String full) {
        if (full == null) return "";
        String s = full.trim();
        int sp = s.lastIndexOf(' ');
        return sp > 0 ? s.substring(sp + 1) : s;
    }
}
