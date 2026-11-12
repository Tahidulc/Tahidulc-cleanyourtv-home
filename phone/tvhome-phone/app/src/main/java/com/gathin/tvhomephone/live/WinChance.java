package com.gathin.tvhomephone.live;

/**
 * Each side's chance of winning, and the draw's, as the game goes (the strip under the scoreboard, BoardUnder): goals
 * from here on as two Poisson counts, each side's rate per 90 minutes fitted to the chances the prices before kick-off
 * put on the game (home 1.45, away 1.15 without them), the rest of the game what is left of it, a sending-off making its
 * side less and the other more likely to score. Only the three chances are ever shown, never the prices. Pure: the
 * tests check it.
 */
final class WinChance {
    private WinChance() { }

    static final double HOME = 1.45, AWAY = 1.15;
    private static final int MOST = 12;

    /** Pure: a Poisson count's chances of 0 to MOST at this mean. */
    static double[] pmf(double mean) {
        double[] p = new double[MOST + 1];
        double m = Math.max(0, mean);
        p[0] = Math.exp(-m);
        for (int k = 1; k <= MOST; k++) p[k] = p[k - 1] * m / k;
        return p;
    }

    /** Pure: {home, draw, away} with home leading by diff now and hm, am goals still to come on average for each side. */
    static double[] chances(double hm, double am, int diff) {
        double[] x = pmf(hm), y = pmf(am);
        double h = 0, d = 0, a = 0;
        for (int i = 0; i <= MOST; i++) {
            for (int j = 0; j <= MOST; j++) {
                double p = x[i] * y[j];
                int f = diff + i - j;
                if (f > 0) h += p; else if (f == 0) d += p; else a += p;
            }
        }
        double all = h + d + a;
        return all <= 0 ? new double[]{1 / 3.0, 1 / 3.0, 1 / 3.0} : new double[]{h / all, d / all, a / all};
    }

    /** Pure: each side's goals per 90 minutes {home, away} fitted to the chances before kick-off {home, draw, away}. */
    static double[] rates(double[] before) {
        if (before == null || before.length < 3) return new double[]{HOME, AWAY};
        double best = Double.MAX_VALUE, bh = HOME, ba = AWAY;
        for (int i = 0; i <= 76; i++) {
            double lh = 0.2 + i * 0.05;
            for (int j = 0; j <= 76; j++) {
                double la = 0.2 + j * 0.05;
                double[] c = chances(lh, la, 0);
                double e = sq(c[0] - before[0]) + sq(c[1] - before[1]) + sq(c[2] - before[2]);
                if (e < best) {
                    best = e;
                    bh = lh;
                    ba = la;
                }
            }
        }
        return new double[]{bh, ba};
    }

    private static double sq(double v) {
        return v * v;
    }

    /**
     * Pure: what is left of the game as a share of 90 minutes, at minute of period (1 and 2, 3 and 4 in extra time), with
     * added minutes put up for this half (0 when not known): to 90 (120 in extra time), in added time what is left of the
     * minutes added, at least one.
     */
    static double rest(int minute, int period, int added) {
        int end = period >= 3 ? 120 : 90;
        int halfEnd = period <= 1 ? 45 : period == 2 ? 90 : period == 3 ? 105 : 120;
        double left;
        if (minute >= halfEnd && halfEnd == end) left = Math.max(1, halfEnd + Math.max(added, 3) - minute);
        else left = Math.max(1, end - Math.max(0, minute));
        return left / 90.0;
    }

    /** Pure: the chances now {home, draw, away} from the rates per 90, the score, how much is left, the sendings-off. */
    static double[] now(double[] rates, int homeGoals, int awayGoals, double rest, int homeReds, int awayReds) {
        double lh = rates[0], la = rates[1];
        for (int i = 0; i < homeReds; i++) { lh *= 0.7; la *= 1.2; }
        for (int i = 0; i < awayReds; i++) { la *= 0.7; lh *= 1.2; }
        return chances(lh * rest, la * rest, homeGoals - awayGoals);
    }

    /** Pure: the chances as whole percentages that add up to 100 (the largest remainders rounded up). */
    static int[] percents(double[] c) {
        int[] out = new int[3];
        double[] rem = new double[3];
        int sum = 0;
        for (int i = 0; i < 3; i++) {
            double v = Math.max(0, c[i]) * 100;
            out[i] = (int) Math.floor(v);
            rem[i] = v - out[i];
            sum += out[i];
        }
        while (sum < 100) {
            int k = 0;
            for (int i = 1; i < 3; i++) if (rem[i] > rem[k]) k = i;
            out[k]++;
            rem[k] = -1;
            sum++;
        }
        return out;
    }

    /** Pure: whether a side's chance moved enough with a goal to say so (5 points or more). */
    static boolean moved(int was, int is) {
        return Math.abs(is - was) >= 5;
    }

    /** Pure: the words for a side's chance before and after a goal: "Everton 34% → 71%". */
    static String words(String club, int was, int is) {
        return (club == null ? "" : club) + " " + was + "% \u2192 " + is + "%";
    }
}
