package com.gathin.tvhomephone.sports;

import java.util.Locale;
import java.util.Map;

/**
 * A player's live rating out of ten, worked out from ESPN's figures for him in the game so far (ESPN gives no rating
 * of its own). Everyone who has been on starts at 6.4: goals (worth most from the back), assists, shots on target and
 * fouls won add to it; fouls given away, offsides, cards and own goals take from it; a keeper gains for his saves and
 * loses for goals let in, a defender loses a little for them too, and both gain for a clean sheet as the game goes on;
 * and the score so far moves everyone's a touch. Held between 3.0 and 10.0, to one place. Pure, for the tests.
 */
public final class Ratings {
    private Ratings() {}

    /** The rating as shown ("7.4"), or "" for a player not on yet. */
    public static String of(Map<String, Integer> stats, String position, int teamFor, int teamAgainst, float progress) {
        double r = value(stats, position, teamFor, teamAgainst, progress);
        return r < 0 ? "" : String.format(Locale.UK, "%.1f", r);
    }

    /** The rating, or -1 for a player not on yet. */
    static double value(Map<String, Integer> s, String position, int teamFor, int teamAgainst, float progress) {
        if (s == null || n(s, "appearances") <= 0) return -1;
        String role = role(position);
        int goals = n(s, "totalGoals"), assists = n(s, "goalAssists"), onTarget = n(s, "shotsOnTarget");
        int conceded = n(s, "goalsConceded"), red = n(s, "redCards");
        double r = 6.4;
        r += goals * ("G".equals(role) || "D".equals(role) ? 1.3 : "M".equals(role) ? 1.1 : 1.0);
        r += assists * 0.75;
        r += Math.max(0, onTarget - goals) * 0.12;
        r += n(s, "foulsSuffered") * 0.06;
        r -= n(s, "foulsCommitted") * 0.05;
        r -= n(s, "offsides") * 0.05;
        r -= Math.min(1, n(s, "yellowCards")) * 0.3;
        r -= red * 1.5;
        r -= n(s, "ownGoals") * 1.0;
        float p = Math.max(0f, Math.min(1f, progress));
        // A clean sheet only while the side has let in none (his own figure can miss an own goal).
        boolean clean = conceded == 0 && teamAgainst == 0 && red == 0;
        if ("G".equals(role)) {
            r += n(s, "saves") * 0.3;
            r -= conceded * 0.3;
            if (clean) r += 0.5 * p;
        } else if ("D".equals(role)) {
            r -= conceded * 0.12;
            if (clean) r += 0.35 * p;
        }
        r += Math.max(-0.45, Math.min(0.45, 0.15 * (teamFor - teamAgainst)));
        r = Math.max(3.0, Math.min(10.0, r));
        return Math.round(r * 10) / 10.0;
    }

    /** Keeper (G), defender (D), midfielder (M) or forward (F), from ESPN's position ("G", "CD-L", "RB", "AM", "CF-R"). */
    static String role(String position) {
        String p = position == null ? "" : position.toUpperCase(Locale.ROOT);
        if (p.startsWith("G")) return "G";
        if (p.startsWith("CD") || p.startsWith("D") || p.equals("LB") || p.equals("RB") || p.endsWith("WB") || p.startsWith("SW")) return "D";
        if (p.startsWith("F") || p.startsWith("CF") || p.startsWith("ST") || p.equals("LW") || p.equals("RW")) return "F";
        return "M";
    }

    /** The colour a rating is shown in: blue for 8 and up, green for 7, amber for 6, orange-red below. */
    public static int colour(String rating) {
        double v;
        try {
            v = Double.parseDouble(rating);
        } catch (RuntimeException e) {
            return 0x00000000;
        }
        return v >= 8.0 ? 0xFF2F7BEF : v >= 7.0 ? 0xFF1FA05A : v >= 6.0 ? 0xFFE59A12 : 0xFFE5533D;
    }

    private static int n(Map<String, Integer> s, String k) {
        Integer v = s.get(k);
        return v == null ? 0 : v;
    }
}
