package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * FotMob's figures for a game, beside ESPN's: expected goals and on target, big chances, touches in the box, distance
 * covered, each side's best rated player and the player of the match, the momentum (which side is on top), FotMob's own
 * insights ("Haven't won in 5"), every card and substitution with the player's id, and the player's picture by that id.
 * FotMob has no published API: these are the addresses its own site reads, no key, so they may change; every call is off
 * the main thread and anything it cannot read is left out (ESPN's stats stand alone then).
 */
final class FotMob {
    private FotMob() { }

    private static final String BASE = "https://www.fotmob.com/api/data/";
    /** ESPN's game id to FotMob's (-1: looked for and not found, kept so it is not looked for again for a while). */
    private static final Map<String, Integer> ids = new HashMap<>();
    private static final Map<String, Long> lookedAt = new HashMap<>();
    /** The players met in the last game read, by name (as FotMob writes it, and its surname), to their FotMob ids. */
    private static final Map<String, Integer> players = new HashMap<>();

    /** One game's figures as read. */
    static final class Match {
        int id;
        /** Each stat FotMob lists, by its key ("expected_goals"), as {home, away}. */
        final Map<String, String[]> stats = new HashMap<>();
        /** The momentum, a value a minute (above 0: the home side on top). */
        final List<int[]> momentum = new ArrayList<>();
        /** Each side's best rated player: {name, rating, id}; null when FotMob rates none yet. */
        String[] homeTop, awayTop;
        /** The player of the match {name, team, rating, id}; null until FotMob names one. */
        String[] potm;
        /** FotMob's insights, best first: {text, teamId (or ""), playerId (or "")}. */
        final List<String[]> insights = new ArrayList<>();
        /** Cards and substitutions: {type ("Card"/"Substitution"), minute, home ("1"/"0"), name, id, card or name off, id off}. */
        final List<String[]> events = new ArrayList<>();
        boolean varPending;
        int homeTeamId, awayTeamId;
        /** Every player FotMob has rated, best first: {name, rating ("8.5"), id, home ("1"/"0")}. */
        final List<String[]> ratings = new ArrayList<>();
    }

    /** Pure: the n best rated (fewer when fewer are rated), each {name, rating, id, home}. */
    static List<String[]> topRated(Match m, int n) {
        List<String[]> out = new ArrayList<>();
        if (m == null) return out;
        for (String[] r : m.ratings) {
            if (out.size() >= n) break;
            out.add(r);
        }
        return out;
    }

    /** Pure: a rating as FotMob shows it, to one place ("8.5", "7.0"); "" when there is none. */
    static String rating(double r) {
        return r <= 0 ? "" : String.format(Locale.UK, "%.1f", r);
    }

    /** Each game read lately, by ESPN's id: {the Match (or null when not found), when}. Shared by every scoreboard. */
    private static final Map<String, Object[]> read = new HashMap<>();
    private static final long READ_FRESH_MS = 60_000L;

    /**
     * The game's FotMob figures, read at most once a minute whichever scoreboard asks (off the main thread); null when
     * FotMob doesn't have it.
     */
    static Match forGame(String espnId, String home, String away, long kickoff) {
        if (espnId == null) return null;
        long now = System.currentTimeMillis();
        synchronized (read) {
            Object[] r = read.get(espnId);
            if (r != null && now - (Long) r[1] < READ_FRESH_MS) return (Match) r[0];
        }
        Match m = null;
        try {
            int id = find(espnId, home, away, kickoff);
            m = id > 0 ? details(id) : null;
        } catch (RuntimeException ignored) {
            m = null;
        }
        synchronized (read) {
            if (read.size() > 30) read.clear();
            read.put(espnId, new Object[]{m, now});
        }
        return m;
    }

    /**
     * Pure: FotMob's figures as plain rows for a scoreboard's own stats ({name, home, away}), best first: expected
     * goals, big chances, xG on target, touches in the box. Empty without them.
     */
    static java.util.List<String[]> rows(Match m) {
        java.util.List<String[]> out = new ArrayList<>();
        if (m == null) return out;
        String[][] want = {{"expected_goals", "Expected goals"}, {"big_chance", "Big chances"},
                {"expected_goals_on_target", "xG on target"}, {"touches_opp_box", "Touches in box"}};
        for (String[] w : want) {
            String[] v = m.stats.get(w[0]);
            if (v != null && !v[0].isEmpty() && !v[1].isEmpty()) out.add(new String[]{w[1], v[0], v[1]});
        }
        return out;
    }

    /** Pure: "1.26" as 126, for a bar's share; 0 when it is no number. */
    static int hundredths(String v) {
        try {
            return (int) Math.round(Double.parseDouble(v.trim()) * 100);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** A player's picture at FotMob, by its id. */
    static String photoUrl(int playerId) {
        return "https://images.fotmob.com/image_resources/playerimages/" + playerId + ".png";
    }

    /** The FotMob id of a player in the last game read, by full name or surname; 0 when not met. */
    static synchronized int playerId(String name) {
        if (name == null) return 0;
        Integer id = players.get(plain(name));
        if (id == null) id = players.get(surname(plain(name)));
        return id == null ? 0 : id;
    }

    /**
     * The game's FotMob id, found on FotMob's day of matches by both clubs' names (off the main thread); 0 when it is
     * not there. Looked up once a game, and again after ten minutes when it was not found.
     */
    static int find(String espnId, String home, String away, long kickoff) {
        if (espnId == null || home == null || away == null) return 0;
        synchronized (ids) {
            Integer known = ids.get(espnId);
            Long at = lookedAt.get(espnId);
            if (known != null && (known > 0 || at != null && System.currentTimeMillis() - at < 600_000L)) return Math.max(0, known);
        }
        int found = 0;
        for (int day = 0; day < 2 && found == 0; day++) {
            try {
                SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                String date = f.format(new Date((kickoff > 0 ? kickoff : System.currentTimeMillis()) - day * 86_400_000L * (day == 1 ? 1 : 0)));
                if (day == 1) {
                    // The day before by FotMob's reckoning (a late kick-off listed on the evening's date).
                    date = f.format(new Date((kickoff > 0 ? kickoff : System.currentTimeMillis()) - 6 * 3_600_000L));
                }
                JSONObject all = new JSONObject(get(BASE + "matches?date=" + date));
                found = matchIn(all.optJSONArray("leagues"), home, away);
            } catch (Exception ignored) {
                // not this time
            }
        }
        synchronized (ids) {
            ids.put(espnId, found > 0 ? found : -1);
            lookedAt.put(espnId, System.currentTimeMillis());
        }
        return found;
    }

    /** Pure: the id of the match between these two clubs among FotMob's leagues of matches; 0 when none. */
    static int matchIn(JSONArray leagues, String home, String away) {
        for (int i = 0; leagues != null && i < leagues.length(); i++) {
            JSONObject lg = leagues.optJSONObject(i);
            JSONArray ms = lg == null ? null : lg.optJSONArray("matches");
            for (int k = 0; ms != null && k < ms.length(); k++) {
                JSONObject m = ms.optJSONObject(k);
                if (m == null) continue;
                JSONObject h = m.optJSONObject("home"), a = m.optJSONObject("away");
                if (h == null || a == null) continue;
                if (sameClub(h.optString("name"), home) && sameClub(a.optString("name"), away)
                        || sameClub(h.optString("longName", h.optString("name")), home) && sameClub(a.optString("longName", a.optString("name")), away)) {
                    return m.optInt("id");
                }
            }
        }
        return 0;
    }

    /** The game's figures (off the main thread); null when FotMob cannot be read. */
    static Match details(int id) {
        if (id <= 0) return null;
        try {
            Match m = parse(new JSONObject(get(BASE + "matchDetails?matchId=" + id)));
            m.id = id;
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    /** FotMob's match details as it gives them (off the main thread), for the classic line-ups; null when not read. */
    static JSONObject raw(int id) {
        if (id <= 0) return null;
        try {
            return new JSONObject(get(BASE + "matchDetails?matchId=" + id));
        } catch (Exception e) {
            return null;
        }
    }

    /** Pure, but for the players it notes: FotMob's match details as a Match. */
    static Match parse(JSONObject d) {
        Match m = new Match();
        m.varPending = d.optBoolean("hasPendingVAR");
        JSONObject general = d.optJSONObject("general");
        if (general != null) {
            JSONObject ht = general.optJSONObject("homeTeam"), at = general.optJSONObject("awayTeam");
            if (ht != null) m.homeTeamId = ht.optInt("id");
            if (at != null) m.awayTeamId = at.optInt("id");
        }
        JSONObject c = d.optJSONObject("content");
        if (c == null) return m;
        // The stats, every group's, by key.
        JSONObject per = c.optJSONObject("stats") == null ? null : c.optJSONObject("stats").optJSONObject("Periods");
        JSONObject all = per == null ? null : per.optJSONObject("All");
        JSONArray groups = all == null ? null : all.optJSONArray("stats");
        for (int i = 0; groups != null && i < groups.length(); i++) {
            JSONArray st = groups.optJSONObject(i) == null ? null : groups.optJSONObject(i).optJSONArray("stats");
            for (int k = 0; st != null && k < st.length(); k++) {
                JSONObject s = st.optJSONObject(k);
                JSONArray v = s == null ? null : s.optJSONArray("stats");
                if (v == null || v.length() < 2 || v.isNull(0) || v.isNull(1)) continue;
                String key = s.optString("key");
                if (!key.isEmpty() && !m.stats.containsKey(key)) m.stats.put(key, new String[]{v.optString(0), v.optString(1)});
            }
        }
        // The momentum.
        JSONObject mom = c.optJSONObject("momentum") == null ? null : c.optJSONObject("momentum").optJSONObject("main");
        JSONArray md = mom == null ? null : mom.optJSONArray("data");
        for (int i = 0; md != null && i < md.length(); i++) {
            JSONObject x = md.optJSONObject(i);
            if (x != null) m.momentum.add(new int[]{(int) Math.round(x.optDouble("minute")), x.optInt("value")});
        }
        JSONObject facts = c.optJSONObject("matchFacts");
        if (facts != null) {
            JSONObject top = facts.optJSONObject("topPlayers");
            m.homeTop = topOf(top == null ? null : top.optJSONArray("homeTopPlayers"));
            m.awayTop = topOf(top == null ? null : top.optJSONArray("awayTopPlayers"));
            JSONObject p = facts.optJSONObject("playerOfTheMatch");
            if (p != null && p.optInt("id") > 0) {
                JSONObject nm = p.optJSONObject("name"), rt = p.optJSONObject("rating");
                m.potm = new String[]{nm == null ? "" : nm.optString("fullName"), p.optString("teamName"), rt == null ? "" : rt.optString("num"),
                        String.valueOf(p.optInt("id"))};
            }
            JSONArray ins = facts.optJSONArray("insights");
            List<Object[]> ranked = new ArrayList<>();
            for (int i = 0; ins != null && i < ins.length(); i++) {
                JSONObject x = ins.optJSONObject(i);
                if (x == null || x.optString("text").isEmpty()) continue;
                ranked.add(new Object[]{x.optInt("priority"), new String[]{x.optString("text"), x.isNull("teamId") ? "" : String.valueOf(x.optInt("teamId")),
                        x.isNull("playerId") ? "" : String.valueOf(x.optInt("playerId"))}});
            }
            ranked.sort((a, b) -> Integer.compare((Integer) b[0], (Integer) a[0]));
            for (Object[] o : ranked) m.insights.add((String[]) o[1]);
            JSONObject evs = facts.optJSONObject("events");
            JSONArray ev = evs == null ? null : evs.optJSONArray("events");
            for (int i = 0; ev != null && i < ev.length(); i++) {
                JSONObject e = ev.optJSONObject(i);
                if (e == null) continue;
                String type = e.optString("type");
                String minute = String.valueOf(e.optInt("time")) + (e.optInt("overloadTime") > 0 ? "+" + e.optInt("overloadTime") : "");
                String home = e.optBoolean("isHome") ? "1" : "0";
                if ("Card".equals(type)) {
                    JSONObject pl = e.optJSONObject("player");
                    m.events.add(new String[]{type, minute, home, pl == null ? "" : pl.optString("name"), pl == null ? "" : pl.optString("id"),
                            e.optString("card"), ""});
                } else if ("Substitution".equals(type)) {
                    JSONArray sw = e.optJSONArray("swap");
                    JSONObject on = sw == null ? null : sw.optJSONObject(0), off = sw == null ? null : sw.optJSONObject(1);
                    if (on == null || off == null) continue;
                    m.events.add(new String[]{type, minute, home, on.optString("name"), on.optString("id"), off.optString("name"), off.optString("id")});
                }
            }
        }
        // Every player in the line-ups, for their pictures (and which of a substitution's two was on before).
        JSONObject lu = c.optJSONObject("lineup");
        java.util.Set<String> starters = new java.util.HashSet<>();
        synchronized (FotMob.class) {
            players.clear();
            for (String side : new String[]{"homeTeam", "awayTeam"}) {
                JSONObject t = lu == null ? null : lu.optJSONObject(side);
                for (String list : new String[]{"starters", "subs"}) {
                    JSONArray ps = t == null ? null : t.optJSONArray(list);
                    for (int i = 0; ps != null && i < ps.length(); i++) {
                        JSONObject pl = ps.optJSONObject(i);
                        if (pl == null || pl.optInt("id") <= 0) continue;
                        String n = plain(pl.optString("name"));
                        JSONObject perf = pl.optJSONObject("performance");
                        double r = perf == null ? 0 : perf.optDouble("rating", 0);
                        if (r > 0) {
                            m.ratings.add(new String[]{pl.optString("name"), rating(r), String.valueOf(pl.optInt("id")),
                                    "homeTeam".equals(side) ? "1" : "0"});
                        }
                        players.put(n, pl.optInt("id"));
                        players.put(surname(n), pl.optInt("id"));
                        if ("starters".equals(list)) starters.add(String.valueOf(pl.optInt("id")));
                    }
                }
            }
        }
        m.ratings.sort((x, y) -> Double.compare(Double.parseDouble(y[1]), Double.parseDouble(x[1])));
        // FotMob lists a substitution's two players in either order: the one who started (or came on before) went off.
        for (String[] e : m.events) {
            if (!"Substitution".equals(e[0])) continue;
            if (starters.contains(e[4]) && !starters.contains(e[6])) {
                String n = e[3], id = e[4];
                e[3] = e[5];
                e[4] = e[6];
                e[5] = n;
                e[6] = id;
            }
            starters.remove(e[6]);
            starters.add(e[4]);
        }
        return m;
    }

    private static String[] topOf(JSONArray list) {
        JSONObject p = list == null || list.length() == 0 ? null : list.optJSONObject(0);
        if (p == null) return null;
        JSONObject nm = p.optJSONObject("name");
        String name = nm == null ? "" : nm.optString("lastName", nm.optString("fullName"));
        if (name.isEmpty() && nm != null) name = nm.optString("fullName");
        String rating = p.optString("playerRatingRounded");
        return name.isEmpty() || rating.isEmpty() ? null : new String[]{name, rating, String.valueOf(p.optInt("playerId"))};
    }

    /**
     * Pure: who is on top now from the momentum (the last ten minutes' values): "home", "away", or "" when it is even;
     * and how strongly, 0 to 100.
     */
    static Object[] onTop(List<int[]> momentum) {
        if (momentum == null || momentum.isEmpty()) return new Object[]{"", 0};
        int last = momentum.get(momentum.size() - 1)[0], n = 0;
        double sum = 0;
        for (int[] x : momentum) {
            if (x[0] > last - 10) {
                sum += x[1];
                n++;
            }
        }
        double avg = n == 0 ? 0 : sum / n;
        int strength = (int) Math.min(100, Math.round(Math.abs(avg)));
        return new Object[]{avg > 12 ? "home" : avg < -12 ? "away" : "", strength};
    }

    /**
     * Pure: what FotMob adds to the board's stats, by the names the stats moments ask for ({name, home, away, kind}):
     * expected goals and on target, big chances, touches in the box, distance run, each side's best rated, who is on
     * top now, FotMob's best insight and its player of the match. homeCode and awayCode name the sides ("ARS").
     */
    static Map<String, String[]> statsOf(Match m, String homeCode, String awayCode) {
        Map<String, String[]> out = new HashMap<>();
        if (m == null) return out;
        bar(out, m, "expected_goals", "Expected goals", "");
        bar(out, m, "expected_goals_on_target", "xG on target", "");
        bar(out, m, "big_chance", "Big chances", "");
        bar(out, m, "touches_opp_box", "Touches in box", "");
        bar(out, m, "physical_metrics_distance_covered", "Distance run", " km");
        if (m.homeTop != null && m.awayTop != null && !m.homeTop[1].isEmpty() && !m.awayTop[1].isEmpty()) {
            out.put("Top rated", new String[]{"Top rated", lastName(m.homeTop[0]) + " " + m.homeTop[1], lastName(m.awayTop[0]) + " " + m.awayTop[1], "text"});
        }
        Object[] top = onTop(m.momentum);
        if (!"".equals(top[0])) {
            String who = "home".equals(top[0]) ? homeCode : awayCode;
            out.put("Momentum", new String[]{"Momentum", (who == null ? "" : who) + " on top", "", "one"});
        }
        for (String[] i : m.insights) {
            String said = insight(i, m, homeCode, awayCode);
            if (said != null && said.length() <= 64) {
                out.put("Insight", new String[]{"Did you know", said, "", "one"});
                break;
            }
        }
        if (m.potm != null && !m.potm[0].isEmpty()) {
            out.put("Player of the match", new String[]{"Player of the match", lastName(m.potm[0]) + (m.potm[2].isEmpty() ? "" : " · " + m.potm[2]), "", "one"});
        }
        return out;
    }

    /**
     * Pure: an insight as the board says it: FotMob leaves out who ("Have scored 8 goals in their last 5 matches"), so
     * the side's code goes first ("ARS have scored…"); null when it is about a side it cannot name.
     */
    static String insight(String[] i, Match m, String homeCode, String awayCode) {
        String t = i[0] == null ? "" : i[0].trim().replaceAll("\\.$", "");
        if (t.isEmpty()) return null;
        if (!t.matches("(?i)^(have|has|haven't|hasn't|are|aren't|is|isn't|had|were)\\b.*")) return t;
        String team = String.valueOf(m.homeTeamId).equals(i[1]) ? homeCode : String.valueOf(m.awayTeamId).equals(i[1]) ? awayCode : null;
        if (team == null || team.isEmpty()) return null;
        return team + " " + Character.toLowerCase(t.charAt(0)) + t.substring(1);
    }

    private static void bar(Map<String, String[]> out, Match m, String key, String name, String unit) {
        String[] v = m.stats.get(key);
        if (v == null || v[0].isEmpty() || v[1].isEmpty()) return;
        String h = v[0], a = v[1];
        if (!unit.isEmpty()) {
            h = km(h);
            a = km(a);
            if (h == null || a == null) return;
        }
        out.put(name, new String[]{name, h + unit, a + unit, "bar"});
    }

    /** Pure: a distance as FotMob gives it (km, or metres), in km to one place; null when it is no number. */
    static String km(String v) {
        try {
            double d = Double.parseDouble(v.trim());
            if (d > 500) d /= 1000.0;
            return String.format(Locale.UK, "%.1f", d);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String lastName(String full) {
        String f = full == null ? "" : full.trim();
        int sp = f.lastIndexOf(' ');
        return sp < 0 ? f : f.substring(sp + 1);
    }

    /** Pure: two clubs' names are one club's ("Tottenham Hotspur" and "Tottenham", "Man City" and "Manchester City"). */
    static boolean sameClub(String a, String b) {
        String x = club(a), y = club(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        if (x.equals(y) || x.startsWith(y + " ") || y.startsWith(x + " ")) return true;
        String[] xs = x.split(" "), ys = y.split(" ");
        // The first word, when it says who they are ("Tottenham", "Brighton"), or "Man" for "Manchester".
        String fx = xs[0].equals("man") ? "manchester" : xs[0], fy = ys[0].equals("man") ? "manchester" : ys[0];
        if (fx.equals(fy) && fx.length() >= 5 && !fx.equals("manchester")) return true;
        if (fx.equals("manchester") && fy.equals("manchester") && xs.length > 1 && ys.length > 1) {
            return xs[xs.length - 1].charAt(0) == ys[ys.length - 1].charAt(0);
        }
        return false;
    }

    private static String club(String s) {
        String n = plain(s);
        n = (" " + n + " ").replaceAll(" (fc|afc|cf|sc|ac|the|and|of|utd) ", " ").replace(" united ", " ").trim();
        n = n.replaceAll("\\s+", " ");
        if (n.startsWith("man ")) n = "manchester " + n.substring(4);
        // "Manchester United", "Man Utd": United, its "United" gone above.
        return n.equals("manchester") || n.equals("man") ? "manchester united" : n;
    }

    private static String plain(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static String surname(String plain) {
        int sp = plain.lastIndexOf(' ');
        return sp < 0 ? plain : plain.substring(sp + 1);
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36");
        conn.setRequestProperty("Accept", "application/json");
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 3 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
