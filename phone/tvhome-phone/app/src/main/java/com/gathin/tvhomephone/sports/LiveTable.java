package com.gathin.tvhomephone.sports;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The league table as it stands with the live games' scores (the TV's live table alert): each club's points and goal
 * difference from the table, plus what the live score gives it now. Pure, for the tests.
 */
public final class LiveTable {
    private LiveTable() {}

    /**
     * The live position of each club. table: rows {rank, id, name, logo, played, gd, points, group} before these games;
     * live: the league's games in play. Returns id to {position before, position now}.
     */
    public static Map<String, int[]> positions(List<String[]> table, List<Game> live) {
        Map<String, int[]> out = new HashMap<>();
        List<Object[]> rows = new ArrayList<>();   // {id, points, gd, rank}
        for (String[] r : table) {
            if (!r[7].isEmpty()) continue;   // a league only, no groups
            rows.add(new Object[]{r[1], Espn.parseInt(r[6]), parseGd(r[5]), Espn.parseInt(r[0])});
        }
        Map<String, Object[]> byId = new HashMap<>();
        for (Object[] r : rows) byId.put((String) r[0], r);
        for (Game g : live) {
            if (g.state != Game.LIVE) continue;
            add(byId.get(g.homeId), g.homeScore, g.awayScore);
            add(byId.get(g.awayId), g.awayScore, g.homeScore);
        }
        List<Object[]> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> {
            int p = Integer.compare((Integer) b[1], (Integer) a[1]);
            if (p != 0) return p;
            int d = Integer.compare((Integer) b[2], (Integer) a[2]);
            return d != 0 ? d : Integer.compare((Integer) a[3], (Integer) b[3]);
        });
        for (int i = 0; i < sorted.size(); i++) {
            Object[] r = sorted.get(i);
            out.put((String) r[0], new int[]{(Integer) r[3], i + 1});
        }
        return out;
    }

    private static void add(Object[] row, int mine, int theirs) {
        if (row == null) return;
        row[1] = (Integer) row[1] + (mine > theirs ? 3 : mine == theirs ? 1 : 0);
        row[2] = (Integer) row[2] + mine - theirs;
    }

    private static int parseGd(String s) {
        if (s == null) return 0;
        return Espn.parseInt(s.replace("+", ""));
    }

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
}
