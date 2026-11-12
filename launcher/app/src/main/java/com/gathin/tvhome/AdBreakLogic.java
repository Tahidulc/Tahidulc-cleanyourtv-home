package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ad-break screen's sums, kept apart from the screen so the tests can run them: when the break has started and
 * ended (the channel's own logo gone and back), how the strip's cards take turns, and what the widgets show, worked
 * out from ESPN's own pages (a club's fixtures in the game's competition, the competition's table, the meetings
 * between the two clubs). Nothing here is ever made up: a widget whose data is missing is left out.
 */
final class AdBreakLogic {
    private AdBreakLogic() { }

    /** Without a guide's word: how long before kick-off the screen may come (a channel's build-up). */
    static final long WINDOW_MS = 60 * 60_000L;
    /** Without a guide's word: how long after kick-off it still may (full time, about 115 minutes in, then 30). */
    static final long AFTER_MS = 145 * 60_000L;
    /** A programme in the guide counts to its listed end and this much over (a game running long). */
    static final long LISTED_TAIL_MS = 5 * 60_000L;
    /** The programme to come counts when it starts within this. */
    static final long SOON_MS = 15 * 60_000L;
    /** No game known, only the channel's scoreboard seen: breaks count this long after it was last seen. */
    static final long GENERAL_MS = 20 * 60_000L;
    /** The channel's logo is learned from this long before the window opens, so the first break is not missed. */
    static final long LEARN_AHEAD_MS = 30 * 60_000L;
    /** The logo gone this long (each look about a second apart): the adverts. */
    static final long GONE_MS = 6_000L;
    /** Below ABSENT of the logo's solid strokes where they should be: gone; above PRESENT: there. */
    static final float ABSENT = 0.35f, PRESENT = 0.6f;
    /** Looks in a row that see the logo back before the screen goes. */
    static final int BACK_LOOKS = 2;
    /** A widget's time on a card, and how far apart the three cards change. */
    static final long PERIOD_MS = 12_000L, STAGGER_MS = 4_000L;

    // ---- when -------------------------------------------------------------------------------------------------------

    static final int NONE = 0, SHOW = 1, HIDE = 2;

    /**
     * Whether the screen should come or go, a look at a time (about one a second while a match's programme is on in the
     * player): it comes once the channel's logo has been gone GONE_MS inside the window (the programme in the guide,
     * else the hour before kick-off to half an hour after full time); it goes when the logo is seen back (BACK_LOOKS in
     * a row), when the window closes, or when the channel, the app or the switch says otherwise. A look that cannot
     * tell (presence below 0: no logo learned yet, no picture) changes nothing.
     */
    static final class Trigger {
        private long goneSince = -1;
        private int backRun;
        boolean showing;

        int step(long now, boolean switchOn, boolean playerFront, boolean onChannel, boolean inWindow, float presence) {
            boolean eligible = switchOn && playerFront && onChannel && inWindow;
            if (!eligible) {
                goneSince = -1;
                backRun = 0;
                if (showing) {
                    showing = false;
                    return HIDE;
                }
                return NONE;
            }
            if (presence < 0) return NONE;
            if (presence < ABSENT) {
                backRun = 0;
                if (goneSince < 0) goneSince = now;
                if (!showing && now - goneSince >= GONE_MS) {
                    showing = true;
                    return SHOW;
                }
                return NONE;
            }
            if (presence > PRESENT) {
                goneSince = -1;
                if (showing && ++backRun >= BACK_LOOKS) {
                    showing = false;
                    backRun = 0;
                    return HIDE;
                }
            }
            return NONE;
        }

        /** Shown or put away from outside (the test broadcast, Back): the looks start again from there. */
        void set(boolean on) {
            showing = on;
            goneSince = -1;
            backRun = 0;
        }
    }

    // ---- what is on: the guide's programme, the game, the window ----------------------------------------------------------

    /**
     * How surely a guide's title (or a channel's name: "03. England vs Croatia 19:45") names a game: 2 when both clubs
     * are named either side of its "v" or "vs", 1 when one of them is, 0 when neither or there is no "v". Clubs are
     * matched as TvGuide matches Sky's titles (nicknames, a shared word of four letters or more).
     */
    static int names(String text, String[] home, String[] away) {
        if (text == null || text.isEmpty()) return 0;
        String t = " " + text.replace('\u00a0', ' ').trim() + " ";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)\\s(?:v|vs|vs\\.|versus|-v-)\\s").matcher(t);
        if (!m.find()) return 0;
        String left = t.substring(0, m.start()), right = t.substring(m.end());
        int colon = Math.max(left.lastIndexOf(':'), left.lastIndexOf('|'));
        if (colon >= 0) left = left.substring(colon + 1);
        left = left.replaceAll("(?i)^\\s*(live|\\d+\\.)\\s*", " ");
        right = right.split("\\s+[-|(]\\s*|:|,|\\s\\d{1,2}[:.]\\d{2}")[0];
        boolean lh = club(left, home), la = club(left, away), rh = club(right, home), ra = club(right, away);
        if (lh && ra || la && rh) return 2;
        return lh || la || rh || ra ? 1 : 0;
    }

    /** Whether one side of a title is the club, by any of its names (ESPN's full name, its short one). */
    private static boolean club(String side, String[] names) {
        if (side == null || side.trim().isEmpty() || names == null) return false;
        for (String n : names) if (n != null && !n.isEmpty() && TvGuide.sameClub(side.trim(), n)) return true;
        return false;
    }

    /**
     * Of the games ({home names, away names} each, in the order given), the one a title names: the first named for
     * both clubs, else the one game named for one club when only one is; -1 when none, or several for one club only.
     */
    static int pick(String text, List<String[][]> games) {
        int one = -1, ones = 0;
        for (int i = 0; i < games.size(); i++) {
            int n = names(text, games.get(i)[0], games.get(i)[1]);
            if (n == 2) return i;
            if (n == 1) { one = i; ones++; }
        }
        return ones == 1 ? one : -1;
    }

    /** The guide's programme: from its start to its listed end and LISTED_TAIL_MS over. */
    static long[] listedWindow(long start, long end) {
        return new long[]{start, end + LISTED_TAIL_MS};
    }

    /** The guide's programme from now: the next one (starting within SOON_MS) counts from now, its lead-in. */
    static long[] listedWindow(long start, long end, long now) {
        return new long[]{Math.min(start, now), end + LISTED_TAIL_MS};
    }

    /**
     * No guide's word: from an hour before kick-off to half an hour after full time: kick-off and AFTER_MS, or later
     * while it is still on (now) or when it was last seen on (lastLive: extra time, a late finish).
     */
    static long[] gameWindow(long kickoff, int state, long now, long lastLive) {
        long to = kickoff + AFTER_MS;
        if (state == 0) to = Math.max(to, now + 30 * 60_000L);
        else if (lastLive > 0) to = Math.max(to, lastLive + 30 * 60_000L);
        return new long[]{kickoff - WINDOW_MS, to};
    }

    /** No game, the channel's scoreboard seen at seenAt: from then for GENERAL_MS. */
    static long[] generalWindow(long seenAt) {
        return new long[]{seenAt - 60_000L, seenAt + GENERAL_MS};
    }

    static boolean inside(long[] w, long at) {
        return w != null && at >= w[0] && at <= w[1];
    }

    /** Whether to look at the logo (and learn it): inside the window or within LEARN_AHEAD_MS of its opening. */
    static boolean watch(long[] w, long at) {
        return w != null && at >= w[0] - LEARN_AHEAD_MS && at <= w[1];
    }

    static final int BEFORE = 0, HALF = 1, LIVE = 2, FULL = 3, GENERAL = 4;

    /** Where the game is for the screen's line: before kick-off, half-time, on, over; no game: general. */
    static int phase(boolean game, int state, boolean halftime, long kickoff, long now) {
        if (!game) return GENERAL;
        if (state == 2) return FULL;
        if (state == 0) return halftime ? HALF : LIVE;
        return BEFORE;
    }

    static String label(int phase) {
        switch (phase) {
            case BEFORE: return "BEFORE KICK-OFF";
            case HALF: return "HALF-TIME";
            case FULL: return "FULL TIME";
            default: return "AD BREAK";
        }
    }

    /** The line-up card belongs before kick-off and at half-time. */
    static boolean lineups(int phase) {
        return phase == BEFORE || phase == HALF;
    }

    // ---- one screen at a break: the ad-break screen, the half-time card, the hop -------------------------------------

    static final int AD = 1, HALF_CARD = 4, HOP = 8;

    /**
     * Pure: which of the break's screens may show now, as a mask of AD, HALF_CARD and HOP, so that they never stack.
     * adUp: the ad-break screen on screen; quietBlocks: Match mode's quiet keeps other pop-ups back and this is not its
     * game. The ad-break screen covers every break, half-time's too (its own half-time widgets: the quiz, fun facts,
     * prayer times); while it is up the half-time card and the hop don't show (one up when it comes is closed). Match
     * mode: its own game's screen always may come; any other only when its quiet is off.
     */
    static int breaks(boolean adUp, boolean quietBlocks) {
        int m = 0;
        if (!quietBlocks) m |= AD;
        if (!adUp) m |= HALF_CARD | HOP;
        return m;
    }

    /**
     * Pure: whether the game watched is at half-time as it shows on screen: the scores say so now, or said so until less
     * than the channel's wait (hold) ago (the second half shows on the channel that much after the scores see it).
     */
    static boolean halfTimeShows(boolean scoresHalf, long lastHalfSeen, long hold, long now) {
        return scoresHalf || lastHalfSeen > 0 && now - lastHalfSeen < Math.max(0, hold) + 5_000L;
    }

    /** "1:05:09" over an hour, "4:09" under. */
    static String countdown(long ms) {
        long left = Math.max(0, ms) / 1000;
        return left >= 3600 ? String.format(java.util.Locale.UK, "%d:%02d:%02d", left / 3600, left / 60 % 60, left % 60)
                : String.format(java.util.Locale.UK, "%d:%02d", left / 60, left % 60);
    }

    /** An RSS feed's item titles (CDATA or plain, entities read), the first max of them. */
    static List<String> rssTitles(String xml, int max) {
        List<String> out = new ArrayList<>();
        if (xml == null) return out;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?s)<item\\b.*?<title>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</title>").matcher(xml);
        while (m.find() && out.size() < max) {
            String t = FanFilter.entities(m.group(1)).replaceAll("\\s+", " ").trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    // ---- the cards taking turns ----------------------------------------------------------------------------------------

    /** When card i first changes after the screen comes up: a widget's time, then STAGGER_MS after the card before it. */
    static long firstTurn(int card) {
        return PERIOD_MS + card * STAGGER_MS;
    }

    /** The widget a step on (or back) from at, round and round n of them. */
    static int next(int at, int step, int n) {
        if (n <= 0) return 0;
        return ((at + step) % n + n) % n;
    }

    // ---- form -----------------------------------------------------------------------------------------------------------

    /**
     * A club's last n results in its fixtures list (ESPN's schedule for the game's competition: this competition only),
     * finished before the time given, oldest first: "W", "D" or "L" each. Empty when it has none.
     */
    static String form(JSONObject schedule, String teamId, long before, int n) {
        List<long[]> found = new ArrayList<>();
        List<String> results = new ArrayList<>();
        JSONArray events = schedule == null ? null : schedule.optJSONArray("events");
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            JSONObject comp = first(e, "competitions");
            if (comp == null || !completed(comp)) continue;
            long at = time(e.optString("date"));
            if (at <= 0 || at >= before) continue;
            String r = result(comp.optJSONArray("competitors"), teamId);
            if (r == null) continue;
            found.add(new long[]{at, results.size()});
            results.add(r);
        }
        Collections.sort(found, (a, b) -> Long.compare(a[0], b[0]));
        StringBuilder out = new StringBuilder();
        for (int i = Math.max(0, found.size() - n); i < found.size(); i++) out.append(results.get((int) found.get(i)[1]));
        return out.toString();
    }

    /** One of the club's own results in a game's competitors: won, drawn or lost; null when it is not in it or unscored. */
    static String result(JSONArray competitors, String teamId) {
        JSONObject us = null, them = null;
        for (int i = 0; competitors != null && i < competitors.length(); i++) {
            JSONObject c = competitors.optJSONObject(i);
            if (c == null) continue;
            String id = c.optString("id", c.optJSONObject("team") == null ? "" : c.optJSONObject("team").optString("id"));
            if (id.equals(teamId)) us = c; else them = c;
        }
        if (us == null || them == null) return null;
        if (us.optBoolean("winner")) return "W";
        if (them.optBoolean("winner")) return "L";
        int a = score(us), b = score(them);
        if (a < 0 || b < 0) return null;
        return a > b ? "W" : a < b ? "L" : "D";
    }

    /** A competitor's goals, whether ESPN gives them as an object ({value, displayValue}) or a string; -1 unknown. */
    static int score(JSONObject competitor) {
        Object s = competitor == null ? null : competitor.opt("score");
        try {
            if (s instanceof JSONObject) {
                JSONObject o = (JSONObject) s;
                if (o.has("value")) return (int) Math.round(o.getDouble("value"));
                return Integer.parseInt(o.optString("displayValue", "-1").trim());
            }
            if (s instanceof Number) return ((Number) s).intValue();
            if (s instanceof String && !((String) s).trim().isEmpty()) return Integer.parseInt(((String) s).trim());
        } catch (Exception ignored) {
            // unscored
        }
        return -1;
    }

    // ---- coming up ------------------------------------------------------------------------------------------------------

    /** The club's next n fixtures after the time given: {opponent id, opponent's name, its crest, "H" or "A", when}. */
    static List<String[]> comingUp(JSONObject schedule, String teamId, long after, int n) {
        List<String[]> out = new ArrayList<>();
        JSONArray events = schedule == null ? null : schedule.optJSONArray("events");
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            JSONObject comp = first(e, "competitions");
            if (comp == null || completed(comp)) continue;
            long at = time(e.optString("date"));
            if (at <= after) continue;
            JSONArray cs = comp.optJSONArray("competitors");
            String ha = null;
            JSONObject them = null;
            for (int k = 0; cs != null && k < cs.length(); k++) {
                JSONObject c = cs.optJSONObject(k);
                if (c == null) continue;
                String id = c.optString("id");
                if (id.equals(teamId)) ha = "home".equals(c.optString("homeAway")) ? "H" : "A";
                else them = c;
            }
            if (ha == null || them == null) continue;
            JSONObject team = them.optJSONObject("team");
            String name = team == null ? "" : team.optString("shortDisplayName", team.optString("displayName"));
            String crest = team == null ? null : logo(team);
            out.add(new String[]{them.optString("id"), name, crest, ha, String.valueOf(at)});
        }
        Collections.sort(out, (a, b) -> Long.compare(Long.parseLong(a[4]), Long.parseLong(b[4])));
        return out.size() > n ? new ArrayList<>(out.subList(0, n)) : out;
    }

    // ---- the table ----------------------------------------------------------------------------------------------------

    /** A club's line in the competition's table. */
    static final class Row {
        final String id, name;
        final int rank, played, points, gd, wins, draws, losses;

        Row(String id, String name, int rank, int played, int points, int gd, int wins, int draws, int losses) {
            this.id = id;
            this.name = name;
            this.rank = rank;
            this.played = played;
            this.points = points;
            this.gd = gd;
            this.wins = wins;
            this.draws = draws;
            this.losses = losses;
        }
    }

    /** The table in a game's ESPN summary (its first group: a league), in its order; empty without one. */
    static List<Row> table(JSONObject summary) {
        List<Row> rows = new ArrayList<>();
        JSONObject st = summary == null ? null : summary.optJSONObject("standings");
        JSONArray groups = st == null ? null : st.optJSONArray("groups");
        JSONObject g = groups == null ? null : groups.optJSONObject(0);
        JSONObject s = g == null ? null : g.optJSONObject("standings");
        JSONArray entries = s == null ? null : s.optJSONArray("entries");
        for (int i = 0; entries != null && i < entries.length(); i++) {
            JSONObject e = entries.optJSONObject(i);
            if (e == null) continue;
            JSONArray stats = e.optJSONArray("stats");
            int rank = stat(stats, "rank", i + 1);
            rows.add(new Row(e.optString("id"), e.optString("team"), rank, stat(stats, "gamesPlayed", 0), stat(stats, "points", 0),
                    stat(stats, "pointDifferential", 0), stat(stats, "wins", 0), stat(stats, "ties", 0), stat(stats, "losses", 0)));
        }
        Collections.sort(rows, (a, b) -> Integer.compare(a.rank, b.rank));
        return rows;
    }

    private static int stat(JSONArray stats, String name, int otherwise) {
        for (int i = 0; stats != null && i < stats.length(); i++) {
            JSONObject s = stats.optJSONObject(i);
            if (s != null && name.equals(s.optString("name"))) {
                double v = s.optDouble("value", Double.NaN);
                if (!Double.isNaN(v)) return (int) Math.round(v);
                try { return Integer.parseInt(s.optString("displayValue").replace("+", "").trim()); } catch (NumberFormatException e) { return otherwise; }
            }
        }
        return otherwise;
    }

    /** A club's line in the table; null when it is not in it. */
    static Row row(List<Row> rows, String id) {
        if (rows == null || id == null) return null;
        for (Row r : rows) if (id.equals(r.id)) return r;
        return null;
    }

    /**
     * The lines of the table to show, at most four: both clubs and the places round them when they are close (one run of
     * four), else each with the club just above it (two pairs, in order). Empty when either is not in the table.
     */
    static List<Row> around(List<Row> rows, String homeId, String awayId) {
        List<Row> out = new ArrayList<>();
        int h = index(rows, homeId), a = index(rows, awayId);
        if (h < 0 || a < 0) return out;
        int lo = Math.min(h, a), hi = Math.max(h, a);
        if (hi - lo <= 3) {
            int from = Math.max(0, Math.min(lo - (3 - (hi - lo)) / 2, rows.size() - 4));
            for (int i = from; i < Math.min(rows.size(), from + 4); i++) out.add(rows.get(i));
            return out;
        }
        for (int i : new int[]{lo, hi}) {
            int from = i == 0 ? 0 : i - 1;
            out.add(rows.get(from));
            out.add(rows.get(from + 1 < rows.size() ? from + 1 : from));
        }
        return out;
    }

    private static int index(List<Row> rows, String id) {
        for (int i = 0; rows != null && id != null && i < rows.size(); i++) if (id.equals(rows.get(i).id)) return i;
        return -1;
    }

    /**
     * Where each club would be if the game ended home - away now: three points for a win, one each for a draw, the goals
     * on their difference; the table then ordered by points, then goal difference (ESPN's own order between clubs still
     * level). {home's place, away's place}, from 1; null when either is not in the table.
     */
    static int[] placesIf(List<Row> rows, String homeId, String awayId, int home, int away) {
        if (row(rows, homeId) == null || row(rows, awayId) == null) return null;
        List<int[]> t = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int pts = r.points, gd = r.gd;
            if (r.id.equals(homeId)) {
                pts += home > away ? 3 : home == away ? 1 : 0;
                gd += home - away;
            } else if (r.id.equals(awayId)) {
                pts += away > home ? 3 : home == away ? 1 : 0;
                gd += away - home;
            }
            t.add(new int[]{pts, gd, i});
        }
        Collections.sort(t, (x, y) -> x[0] != y[0] ? Integer.compare(y[0], x[0]) : x[1] != y[1] ? Integer.compare(y[1], x[1]) : Integer.compare(x[2], y[2]));
        int hp = -1, ap = -1;
        for (int i = 0; i < t.size(); i++) {
            Row r = rows.get(t.get(i)[2]);
            if (r.id.equals(homeId)) hp = i + 1;
            if (r.id.equals(awayId)) ap = i + 1;
        }
        return new int[]{hp, ap};
    }

    /** "1st", "2nd", "3rd", "11th", "22nd". */
    static String ordinal(int n) {
        int t = n % 100;
        String s = t >= 11 && t <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + s;
    }

    // ---- head to head ----------------------------------------------------------------------------------------------------

    /**
     * The meetings between the two clubs in a game's ESPN summary (its head-to-head series, else headToHeadGames),
     * oldest first: {"2–1" from the home club's side, its result W/D/L, when (ms), the meeting's event id}. Only those
     * finished and scored.
     */
    static List<String[]> headToHead(JSONObject summary, String homeId) {
        List<String[]> out = new ArrayList<>();
        JSONArray series = summary == null ? null : summary.optJSONArray("seasonseries");
        JSONArray events = null;
        for (int i = 0; series != null && i < series.length(); i++) {
            JSONObject s = series.optJSONObject(i);
            if (s != null && "head-to-head".equals(s.optString("type"))) events = s.optJSONArray("events");
        }
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            JSONObject st = e.optJSONObject("statusType");
            if (st != null && !st.optBoolean("completed", true)) continue;
            JSONArray cs = e.optJSONArray("competitors");
            String r = result(cs, homeId);
            JSONObject us = null, them = null;
            for (int k = 0; cs != null && k < cs.length(); k++) {
                JSONObject c = cs.optJSONObject(k);
                if (c == null) continue;
                String id = c.optJSONObject("team") != null ? c.optJSONObject("team").optString("id") : c.optString("id");
                if (homeId != null && homeId.equals(id)) us = c; else them = c;
            }
            int a = score(us), b = score(them);
            if (r == null || a < 0 || b < 0) continue;
            out.add(new String[]{a + "–" + b, r, String.valueOf(time(e.optString("date"))), e.optString("id")});
        }
        Collections.sort(out, (x, y) -> Long.compare(Long.parseLong(x[2]), Long.parseLong(y[2])));
        return out.size() > 5 ? new ArrayList<>(out.subList(out.size() - 5, out.size())) : out;
    }

    /** Wins for the home club, wins for the away club and draws in the meetings: {home, away, drawn}. */
    static int[] tally(List<String[]> meetings) {
        int[] t = new int[3];
        for (String[] m : meetings) t["W".equals(m[1]) ? 0 : "L".equals(m[1]) ? 1 : 2]++;
        return t;
    }

    // ---- the referee, the absent --------------------------------------------------------------------------------------

    /** The referee as ESPN's game information names him ("Michael Oliver"); null when it does not. */
    static String referee(JSONObject summary) {
        JSONObject gi = summary == null ? null : summary.optJSONObject("gameInfo");
        JSONArray off = gi == null ? null : gi.optJSONArray("officials");
        for (int i = 0; off != null && i < off.length(); i++) {
            JSONObject o = off.optJSONObject(i);
            if (o == null) continue;
            JSONObject pos = o.optJSONObject("position");
            String role = pos == null ? o.optString("position") : pos.optString("name", pos.optString("displayName"));
            if (role.toLowerCase(java.util.Locale.ROOT).contains("referee") && !role.toLowerCase(java.util.Locale.ROOT).contains("assistant")
                    && !role.toLowerCase(java.util.Locale.ROOT).contains("video")) {
                String n = o.optString("displayName", o.optString("fullName"));
                if (!n.isEmpty()) return n;
            }
        }
        return null;
    }

    /** Each club's absent as ESPN's summary lists them (injuries): {club id, player, what (status or the injury)}. */
    static List<String[]> absent(JSONObject summary) {
        List<String[]> out = new ArrayList<>();
        JSONArray teams = summary == null ? null : summary.optJSONArray("injuries");
        for (int i = 0; teams != null && i < teams.length(); i++) {
            JSONObject t = teams.optJSONObject(i);
            JSONObject team = t == null ? null : t.optJSONObject("team");
            JSONArray list = t == null ? null : t.optJSONArray("injuries");
            for (int k = 0; team != null && list != null && k < list.length(); k++) {
                JSONObject inj = list.optJSONObject(k);
                JSONObject ath = inj == null ? null : inj.optJSONObject("athlete");
                if (ath == null) continue;
                String what = inj.optString("status");
                JSONObject type = inj.optJSONObject("type");
                if (type != null && !type.optString("description").isEmpty()) what = type.optString("description");
                out.add(new String[]{team.optString("id"), ath.optString("shortName", ath.optString("displayName")), what});
            }
        }
        return out;
    }

    // ---- small helpers ---------------------------------------------------------------------------------------------------

    private static JSONObject first(JSONObject o, String key) {
        JSONArray a = o == null ? null : o.optJSONArray(key);
        return a == null ? null : a.optJSONObject(0);
    }

    private static boolean completed(JSONObject comp) {
        JSONObject status = comp.optJSONObject("status");
        JSONObject type = status == null ? null : status.optJSONObject("type");
        return type != null && type.optBoolean("completed");
    }

    /** A club's crest from its logos (the default first), else its logo. */
    static String logo(JSONObject team) {
        JSONArray logos = team.optJSONArray("logos");
        for (int i = 0; logos != null && i < logos.length(); i++) {
            JSONObject l = logos.optJSONObject(i);
            if (l != null && !l.optString("href").isEmpty()) return l.optString("href");
        }
        String l = team.optString("logo");
        return l.isEmpty() ? null : l;
    }

    /** ESPN's times ("2026-11-08T16:30Z", with seconds or without) in ms; 0 when unreadable. */
    static long time(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        String s = iso.endsWith("Z") ? iso.substring(0, iso.length() - 1) : iso;
        if (s.length() == 16) s += ":00";
        try {
            return java.time.LocalDateTime.parse(s.length() > 19 ? s.substring(0, 19) : s).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
