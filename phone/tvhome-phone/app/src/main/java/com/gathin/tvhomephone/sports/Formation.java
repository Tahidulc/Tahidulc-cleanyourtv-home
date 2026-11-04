package com.gathin.tvhomephone.sports;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where each starter stands: the goalkeeper, then the formation's lines from the back, each from the side's own left
 * to its right. ESPN numbers the places of each formation the same way every game (place 1 the goalkeeper); before
 * kick-off it names the positions only as G, D, M and F, so the places' positions are kept here, as its finished
 * games name them. An unknown formation goes by each player's own position.
 */
public final class Formation {
    private Formation() {}

    /** For each formation, the positions of places 2 to 11. */
    static final Map<String, String[]> PLACES = new HashMap<>();

    static {
        put("4-2-3-1", "RB LB LM CD-R CD-L AM-R RM F AM AM-L");
        put("4-3-3", "RB LB CM CD-R CD-L RM LM F RF LF");
        put("3-4-2-1", "RM LM CD-L CD CD-R CM-R CM-L F CF-R CF-L");
        put("4-4-2", "RB LB CM-R CD-R CD-L RM CM-L CF-L CF-R LM");
        put("3-5-2", "RM LM CD-L CD CD-R CM-R CM-L CF-L CF-R AM");
        put("3-4-3", "RM LM CD-L CD CD-R CM-R CM-L F CF-R CF-L");
        put("4-1-4-1", "RB LB DM CD-R CD-L RM CM-R F CM-L LM");
        put("5-3-2", "RB LB CD-L CD CD-R CM-R CM CF-L CF-R CM-L");
        // ESPN calls place 8 here "SW"; it is the midfield's holding player.
        put("3-1-4-2", "RM LM CD CD-R CD-L CM-R DM CF-R CF-L CM-L");
        put("5-4-1", "RB LB CD-L CD CD-R RM CM-R F CM-L LM");
        put("4-5-1", "RB LB CM-R CD-R CD-L RM CM-L F AM LM");
        put("4-2-2-2", "RB LB CM-R CD-R CD-L AM-R CM-L CF-L CF-R AM-L");
        put("4-3-2-1", "RB LB CM CD-R CD-L CM-L CM-R F CF-R CF-L");
        put("3-4-1-2", "RM LM CD-L CD CD-R CM-R CM-L AM CF-R CF-L");
        put("4-4-1-1", "RB LB CM-R CD-R CD-L RM CM-L F AM LM");
    }

    private static void put(String formation, String places) {
        PLACES.put(formation, places.split(" "));
    }

    /** How far up the pitch a position plays: 0 in goal, 1 the defence, about 3 midfield, 5 the striker. */
    static double depth(String position) {
        String p = position == null ? "" : position.toUpperCase(Locale.ROOT);
        switch (p) {
            case "G": case "GK": return 0;
            case "D": case "CD": case "CD-L": case "CD-R": case "RB": case "LB": case "SW": return 1;
            case "RWB": case "LWB": return 1.6;
            case "DM": case "DM-L": case "DM-R": return 2;
            case "M": case "CM": case "CM-L": case "CM-R": case "RM": case "LM": return 3;
            case "AM": case "AM-L": case "AM-R": return 4;
            case "RW": case "LW": case "RF": case "LF": return 4.5;
            case "CF-L": case "CF-R": case "SS": return 4.6;
            case "F": case "CF": case "ST": return 5;
            default: return 3;
        }
    }

    /** Where across a position plays, from the side's left (negative) to its right. */
    static double across(String position) {
        String p = position == null ? "" : position.toUpperCase(Locale.ROOT);
        switch (p) {
            case "RB": case "RWB": case "RM": case "RW": return 2;
            case "LB": case "LWB": case "LM": case "LW": return -2;
            case "AM-R": case "RF": return 1.5;
            case "AM-L": case "LF": return -1.5;
            case "CD-R": case "CM-R": case "DM-R": case "CF-R": return 1;
            case "CD-L": case "CM-L": case "DM-L": case "CF-L": return -1;
            default: return 0;
        }
    }

    /** A place's position in this formation, else the player's own. */
    static String position(String formation, String place, String own) {
        String[] places = PLACES.get(formation == null ? "" : formation.trim());
        int n = Espn.parseInt(place == null ? "" : place);
        if (places != null && n >= 2 && n <= 11) return places[n - 2];
        return own;
    }

    /**
     * The starters by line: the first row the goalkeeper, then each line from the back, each from the side's left to
     * its right; indexes into lineup (Espn's line-up rows). Empty when there are no starters.
     */
    public static List<List<Integer>> rows(String formation, List<String[]> lineup) {
        List<Integer> starters = new ArrayList<>();
        for (int i = 0; i < lineup.size(); i++) if ("1".equals(lineup.get(i)[Espn.P_STARTER])) starters.add(i);
        List<List<Integer>> rows = new ArrayList<>();
        if (starters.isEmpty()) return rows;
        int keeper = -1;
        for (int i : starters) if (keeper < 0 && "1".equals(place(lineup.get(i)))) keeper = i;
        for (int i : starters) if (keeper < 0 && depth(lineup.get(i)[Espn.P_POSITION]) == 0) keeper = i;
        if (keeper < 0) keeper = starters.get(0);
        List<Integer> keeperRow = new ArrayList<>();
        keeperRow.add(keeper);
        rows.add(keeperRow);
        List<Integer> outfield = new ArrayList<>(starters);
        outfield.remove(Integer.valueOf(keeper));
        Map<Integer, String> pos = new HashMap<>();
        for (int i : outfield) {
            String[] p = lineup.get(i);
            pos.put(i, position(formation, place(p), p[Espn.P_POSITION]));
        }
        outfield.sort((a, b) -> {
            int d = Double.compare(depth(pos.get(a)), depth(pos.get(b)));
            return d != 0 ? d : Integer.compare(Espn.parseInt(place(lineup.get(a))), Espn.parseInt(place(lineup.get(b))));
        });
        List<Integer> counts = counts(formation, outfield.size());
        int at = 0;
        if (counts != null) {
            for (int n : counts) {
                rows.add(new ArrayList<>(outfield.subList(at, at + n)));
                at += n;
            }
        } else {
            // No formation to go by: a line for each depth.
            List<Integer> line = new ArrayList<>();
            double last = Double.NaN;
            for (int i : outfield) {
                double d = Math.round(depth(pos.get(i)));
                if (!line.isEmpty() && d != last) {
                    rows.add(line);
                    line = new ArrayList<>();
                }
                line.add(i);
                last = d;
            }
            if (!line.isEmpty()) rows.add(line);
        }
        for (int r = 1; r < rows.size(); r++) {
            rows.get(r).sort((a, b) -> {
                int c = Double.compare(across(pos.get(a)), across(pos.get(b)));
                return c != 0 ? c : Integer.compare(Espn.parseInt(place(lineup.get(b))), Espn.parseInt(place(lineup.get(a))));
            });
        }
        return rows;
    }

    private static String place(String[] p) {
        return p.length > Espn.P_PLACE ? p[Espn.P_PLACE] : "";
    }

    /** "4-2-3-1" as {4, 2, 3, 1}, when it adds up to the outfield players; null otherwise. */
    static List<Integer> counts(String formation, int outfield) {
        if (formation == null || formation.trim().isEmpty()) return null;
        List<Integer> out = new ArrayList<>();
        int sum = 0;
        for (String part : formation.trim().split("-")) {
            int n = Espn.parseInt(part);
            if (n <= 0) return null;
            out.add(n);
            sum += n;
        }
        return sum == outfield ? out : null;
    }
}
