package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The game's chances as it stands, for the strip under the scoreboard (BoardUnder) and a goal's WIN CHANCE alert: from
 * the chances before kick-off (ESPN's summary has the prices; only the chances made from them are ever shown, nothing
 * of the prices) each side's goals in ninety minutes are worked out, and then, with the score and the time left, how
 * likely a home win, a draw and an away win are now. Pure: the tests check it.
 */
final class WinChance {
    private WinChance() { }

    /** Each side's goals in ninety minutes without anything to go by: a home side a little ahead. */
    static final double HOME_RATE = 1.45, AWAY_RATE = 1.15;
    private static final int MOST_GOALS = 12;

    /** Pure: a price's chance (American style: +320 is 100 / 420; -125 is 125 / 225). */
    static double implied(double ml) {
        return ml > 0 ? 100.0 / (ml + 100.0) : -ml / (-ml + 100.0);
    }

    /** Pure: the chances before kick-off {home, draw, away} from the summary's prices, their margin taken out; null none. */
    static double[] fromSummary(JSONObject summary) {
        for (String key : new String[]{"odds", "pickcenter"}) {
            JSONArray list = summary == null ? null : summary.optJSONArray(key);
            JSONObject o = list == null ? null : list.optJSONObject(0);
            if (o == null) continue;
            double h = line(o.optJSONObject("homeTeamOdds")), a = line(o.optJSONObject("awayTeamOdds")), d = line(o.optJSONObject("drawOdds"));
            double[] p = fromPrices(h, d, a);
            if (p != null) return p;
        }
        return null;
    }

    private static double line(JSONObject side) {
        if (side == null || !side.has("moneyLine")) return Double.NaN;
        double v = side.optDouble("moneyLine", Double.NaN);
        return v == 0 ? Double.NaN : v;
    }

    /** Pure: {home, draw, away} from three prices, normalised; null when one is missing. */
    static double[] fromPrices(double home, double draw, double away) {
        if (Double.isNaN(home) || Double.isNaN(draw) || Double.isNaN(away)) return null;
        double h = implied(home), d = implied(draw), a = implied(away), sum = h + d + a;
        return sum <= 0 ? null : new double[]{h / sum, d / sum, a / sum};
    }

    /** Pure: the chance of exactly k goals at rate lambda. */
    static double poisson(int k, double lambda) {
        if (lambda <= 0) return k == 0 ? 1 : 0;
        double p = Math.exp(-lambda);
        for (int i = 1; i <= k; i++) p *= lambda / i;
        return p;
    }

    /**
     * Pure: {home win, draw, away win} with the home side diff goals ahead and goals still to come at these rates
     * (each side's goals as many as Poisson says, the two apart).
     */
    static double[] outcome(double homeRate, double awayRate, int diff) {
        double[] ph = new double[MOST_GOALS + 1], pa = new double[MOST_GOALS + 1];
        for (int k = 0; k <= MOST_GOALS; k++) {
            ph[k] = poisson(k, homeRate);
            pa[k] = poisson(k, awayRate);
        }
        double h = 0, d = 0, a = 0;
        for (int x = 0; x <= MOST_GOALS; x++) {
            for (int y = 0; y <= MOST_GOALS; y++) {
                double p = ph[x] * pa[y];
                int end = diff + x - y;
                if (end > 0) h += p; else if (end == 0) d += p; else a += p;
            }
        }
        double sum = h + d + a;
        return sum <= 0 ? new double[]{0, 1, 0} : new double[]{h / sum, d / sum, a / sum};
    }

    /** Pure: each side's goals in ninety minutes {home, away} that best give these chances before kick-off. */
    static double[] fit(double[] before) {
        if (before == null) return new double[]{HOME_RATE, AWAY_RATE};
        double best = Double.MAX_VALUE, bh = HOME_RATE, ba = AWAY_RATE;
        for (double h = 0.2; h <= 4.0001; h += 0.05) {
            for (double a = 0.2; a <= 4.0001; a += 0.05) {
                double[] p = outcome(h, a, 0);
                double e = sq(p[0] - before[0]) + sq(p[1] - before[1]) + sq(p[2] - before[2]);
                if (e < best) {
                    best = e;
                    bh = h;
                    ba = a;
                }
            }
        }
        return new double[]{bh, ba};
    }

    private static double sq(double v) {
        return v * v;
    }

    /**
     * Pure: the share of ninety minutes still to play: from the clock's minute to the end (90, or 120 in extra time);
     * in a half's added time, what is left of the minutes added (at least one), or a minute when none were put up.
     */
    static double left(double minute, boolean extraTime, int added) {
        int end = extraTime ? 120 : 90;
        if (minute < end) return Math.max(0, end - minute) / 90.0;
        double over = minute - end;
        double rest = added > 0 ? Math.max(1, added - over) : 1;
        return rest / 90.0;
    }

    /** Pure: the chances now {home, draw, away} at these rates, the share of the game left, the home side diff ahead, the reds. */
    static double[] now(double[] rates, double left, int diff, int homeReds, int awayReds) {
        double h = rates[0], a = rates[1];
        for (int i = 0; i < homeReds; i++) { h *= 0.7; a *= 1.2; }
        for (int i = 0; i < awayReds; i++) { a *= 0.7; h *= 1.2; }
        return outcome(h * left, a * left, diff);
    }

    /** Pure: a goal's WIN CHANCE alert's line, its scorers' chance before and after ("Everton 34% → 71%"); null under 5 points. */
    static String alertLine(String club, int before, int after) {
        if (Math.abs(after - before) < 5) return null;
        return ((club == null ? "" : club.trim()) + " " + before + "% → " + after + "%").trim();
    }

    /**
     * Pure: a side's red cards the screen has shown by this minute of the clock ({key, player, minute, side, ...}, its
     * minute as ESPN gives it: "67'", "45'+2'").
     */
    static int reds(java.util.List<String[]> reds, String side, double minute) {
        int n = 0;
        for (String[] r : reds == null ? java.util.Collections.<String[]>emptyList() : reds) {
            if (r == null || r.length < 4 || !side.equals(r[3])) continue;
            if (minuteOf(r[2]) <= minute) n++;
        }
        return n;
    }

    /** Pure: ESPN's minute as a number ("67'": 67, "45'+2'": 47; none: 0). */
    static double minuteOf(String m) {
        java.util.regex.Matcher x = java.util.regex.Pattern.compile("(\\d+)[^\\d+]*(?:\\+\\s*(\\d+))?").matcher(m == null ? "" : m);
        if (!x.find()) return 0;
        return Integer.parseInt(x.group(1)) + (x.group(2) == null ? 0 : Integer.parseInt(x.group(2)));
    }

    /** Pure: chances as whole percentages adding up to 100 (the largest remainders rounded up). */
    static int[] percents(double[] p) {
        int[] out = new int[3];
        double[] rest = new double[3];
        int sum = 0;
        for (int i = 0; i < 3; i++) {
            double v = Math.max(0, p[i]) * 100;
            out[i] = (int) Math.floor(v);
            rest[i] = v - out[i];
            sum += out[i];
        }
        while (sum < 100) {
            int k = 0;
            for (int i = 1; i < 3; i++) if (rest[i] > rest[k]) k = i;
            out[k]++;
            rest[k] = -1;
            sum++;
        }
        return out;
    }
}
