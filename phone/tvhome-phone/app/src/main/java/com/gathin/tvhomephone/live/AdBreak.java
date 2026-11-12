package com.gathin.tvhomephone.live;

import com.gathin.tvhomephone.sports.Espn;
import com.gathin.tvhomephone.sports.Game;

import java.util.ArrayList;
import java.util.List;

/**
 * The pre-match ad-break screen's arithmetic (AdBreakView draws it): when it comes up, each club's form in this
 * competition, the table around the two clubs and where they would be if it finished now, and how the cards take
 * turns. Pure: the tests feed it.
 */
final class AdBreak {
    private AdBreak() {}

    /** It comes up for a game kicking off within this long (and goes at kick-off). */
    static final long BEFORE_MS = 60 * 60_000L;
    /** Each widget's turn on its card, the cards' start one after another, and the wipe between widgets. */
    static final long TURN_MS = 12_000L, CLIP_MAX_MS = 30_000L, WIPE_MS = 900L;

    // ---- when -------------------------------------------------------------------------------------------------------

    /**
     * Whether the ad-break screen is wanted: switched on, the channel on its adverts, and the channel's game a football
     * game that kicks off within the hour (not yet started).
     */
    static boolean wanted(boolean on, boolean adverts, Game g, long now) {
        return on && adverts && soon(g, now);
    }

    /** Whether a game is before its kick-off, within the hour. */
    static boolean soon(Game g, long now) {
        return g != null && g.state == Game.PRE && g.kickoff > now && g.kickoff - now <= BEFORE_MS;
    }

    /** "12:40" to kick-off (minutes and seconds), "1:02:05" over an hour; "" once it is time. */
    static String countdown(long kickoff, long now) {
        long s = (kickoff - now) / 1000;
        if (s <= 0) return "";
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(java.util.Locale.UK, "%d:%02d:%02d", h, m, sec) : String.format(java.util.Locale.UK, "%d:%02d", m, sec);
    }

    // ---- the cards' turns -------------------------------------------------------------------------------------------

    /** When card i of n first changes: the cards one after another, a third (or half) of a turn apart. */
    static long firstTurn(int i, int n) {
        return TURN_MS + (n <= 1 ? 0 : TURN_MS * i / n);
    }

    /** The widget after (step 1) or before (step -1) widget at, going round n. */
    static int next(int at, int n, int step) {
        if (n <= 0) return 0;
        return ((at + step) % n + n) % n;
    }

    // ---- form, from the club's games in this competition ------------------------------------------------------------

    /**
     * A club's last n results in this competition, oldest first ("WWDLW"), from its schedule rows (Espn.schedule,
     * oldest first): only the games played.
     */
    static String form(List<String[]> schedule, String teamId, int n) {
        StringBuilder all = new StringBuilder();
        if (schedule == null || teamId == null) return "";
        for (String[] r : schedule) {
            if (!"post".equals(r[Espn.S_STATE])) continue;
            boolean home = teamId.equals(r[Espn.S_HOME]);
            if (!home && !teamId.equals(r[Espn.S_AWAY])) continue;
            int us = number(home ? r[Espn.S_HOME_GOALS] : r[Espn.S_AWAY_GOALS]), them = number(home ? r[Espn.S_AWAY_GOALS] : r[Espn.S_HOME_GOALS]);
            if (us < 0 || them < 0) continue;
            all.append(us > them ? 'W' : us < them ? 'L' : 'D');
        }
        return all.length() > n ? all.substring(all.length() - n) : all.toString();
    }

    /** A club's next n games in this competition: {opponent id, opponent name, "H"/"A", date}. */
    static List<String[]> fixtures(List<String[]> schedule, String teamId, long now, int n) {
        List<String[]> out = new ArrayList<>();
        if (schedule == null || teamId == null) return out;
        for (String[] r : schedule) {
            if ("post".equals(r[Espn.S_STATE]) || "in".equals(r[Espn.S_STATE])) continue;
            boolean home = teamId.equals(r[Espn.S_HOME]);
            if (!home && !teamId.equals(r[Espn.S_AWAY])) continue;
            long at = when(r[Espn.S_DATE]);
            if (at > 0 && at < now) continue;
            out.add(new String[]{home ? r[Espn.S_AWAY] : r[Espn.S_HOME], home ? r[Espn.S_AWAY_NAME] : r[Espn.S_HOME_NAME], home ? "H" : "A", r[Espn.S_DATE]});
            if (out.size() >= n) break;
        }
        return out;
    }

    // ---- the table ----------------------------------------------------------------------------------------------------

    /** Indexes into Espn.table's rows: {rank, team id, name, logo, played, goal difference, points, group, won, drawn, lost}. */
    static final int T_RANK = 0, T_ID = 1, T_NAME = 2, T_LOGO = 3, T_PLAYED = 4, T_GD = 5, T_POINTS = 6, T_GROUP = 7;

    /** The rows of the club's own group (the whole table in a league). */
    static List<String[]> group(List<String[]> table, String teamId) {
        String g = null;
        for (String[] r : table) if (r[T_ID].equals(teamId)) g = r[T_GROUP];
        List<String[]> out = new ArrayList<>();
        if (g == null) return out;
        for (String[] r : table) if (r[T_GROUP].equals(g)) out.add(r);
        return out;
    }

    /** size rows of a group around a club (the club among them, the clubs just above it first, as far as the table allows). */
    static List<String[]> around(List<String[]> group, String teamId, int size) {
        int at = -1;
        for (int i = 0; i < group.size(); i++) if (group.get(i)[T_ID].equals(teamId)) at = i;
        List<String[]> out = new ArrayList<>();
        if (at < 0) return out;
        int from = Math.max(0, Math.min(at - size / 2, group.size() - size));   // more above: who they are chasing
        for (int i = from; i < Math.min(group.size(), from + size); i++) out.add(group.get(i));
        return out;
    }

    /**
     * Where each club would be if the game finished now: {home club's place on a home win, a draw, an away win} and the
     * away club's the same, over a group's rows. Points first, then goal difference, as the tables go.
     */
    static int[][] ifFinished(List<String[]> group, String homeId, String awayId) {
        int[][] out = new int[2][3];
        int[][] change = {{3, 1, 1}, {1, 0, 0}, {0, 3, -1}};   // per result: {home points, away points, home goal difference}
        for (int r = 0; r < 3; r++) {
            int hp = change[r][0], ap = change[r][1], hgd = change[r][2];
            out[0][r] = placeAfter(group, homeId, homeId, awayId, hp, ap, hgd);
            out[1][r] = placeAfter(group, awayId, homeId, awayId, hp, ap, hgd);
        }
        return out;
    }

    private static int placeAfter(List<String[]> group, String who, String homeId, String awayId, int hp, int ap, int hgd) {
        int[] me = null;
        List<int[]> all = new ArrayList<>();
        for (String[] r : group) {
            int pts = number(r[T_POINTS]), gd = number(r[T_GD].replace("+", ""));
            if (r[T_ID].equals(homeId)) { pts += hp; gd += hgd; }
            if (r[T_ID].equals(awayId)) { pts += ap; gd -= hgd; }
            int[] row = {pts, gd, r[T_ID].equals(who) ? 1 : 0};
            all.add(row);
            if (row[2] == 1) me = row;
        }
        if (me == null) return 0;
        int place = 1;
        for (int[] o : all) if (o != me && (o[0] > me[0] || o[0] == me[0] && o[1] > me[1])) place++;
        return place;
    }

    /** "1st", "2nd", "3rd", "4th", "11th", "22nd". */
    static String ordinal(int n) {
        int t = n % 100;
        String s = t >= 11 && t <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + s;
    }

    /** A number in ESPN's words ("12", "+9", "-3"); -1 when there is none. */
    static int number(String s) {
        if (s == null) return -1;
        String t = s.trim().replace("+", "");
        if (t.isEmpty()) return -1;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** ESPN's "2026-11-08T16:30Z" as milliseconds; 0 when it can't be read. */
    static long when(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try {
            return java.time.OffsetDateTime.parse(iso.length() == 17 ? iso.replace("Z", ":00Z") : iso).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
