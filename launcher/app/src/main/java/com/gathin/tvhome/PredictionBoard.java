package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/** Pure: the predictions' sums and their results board at full time (Predictions, MoreAlerts.predictionBoard). */
final class PredictionBoard {
    private PredictionBoard() { }

    /** The points for a pick: 3 exact, 1 the right result, 0 otherwise. */
    static int score(int ph, int pa, int h, int a) {
        if (ph == h && pa == a) return 3;
        return Integer.compare(ph - pa, 0) == Integer.compare(h - a, 0) ? 1 : 0;
    }

    /** Pure: what a pick earned, in words: "Spot on · +3", "Right result · +1", "No points". */
    static String verdict(int ph, int pa, int h, int a) {
        switch (score(ph, pa, h, a)) {
            case 3: return "Spot on · +3";
            case 1: return "Right result · +1";
            default: return "No points";
        }
    }

    /**
     * Pure: the results board's rows, best first ({who, "2–1", verdict}), from each player's pick {who, home, away}
     * (a pick taken back, -1, left out).
     */
    static List<String[]> results(List<Object[]> picks, int h, int a) {
        List<Object[]> kept = new ArrayList<>();
        for (Object[] p : picks) if ((Integer) p[1] >= 0) kept.add(p);
        kept.sort((x, y) -> {
            int px = score((Integer) x[1], (Integer) x[2], h, a), py = score((Integer) y[1], (Integer) y[2], h, a);
            if (px != py) return py - px;
            int ex = Math.abs((Integer) x[1] - h) + Math.abs((Integer) x[2] - a), ey = Math.abs((Integer) y[1] - h) + Math.abs((Integer) y[2] - a);
            return ex - ey;
        });
        List<String[]> out = new ArrayList<>();
        for (Object[] p : kept) {
            int ph = (Integer) p[1], pa = (Integer) p[2];
            out.add(new String[]{(String) p[0], ph + "–" + pa, verdict(ph, pa, h, a)});
        }
        return out;
    }

    /** Pure: the board's pages: one a player ("You 2–1", "Spot on · +3"), then the season's table. */
    static String[][] boardPages(List<String[]> rows, String table) {
        List<String[]> pages = new ArrayList<>();
        for (String[] r : rows) pages.add(new String[]{r[0] + "  " + r[1], r[2]});
        if (table != null && !table.isEmpty()) pages.add(new String[]{"Season", table});
        return pages.toArray(new String[0][]);
    }

}
