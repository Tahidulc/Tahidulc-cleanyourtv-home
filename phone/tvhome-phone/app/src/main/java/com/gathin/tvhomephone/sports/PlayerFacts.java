package com.gathin.tvhomephone.sports;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a player's page says about him, worked out from what the sources give, pure for the tests: his places in words,
 * the line under his name (place, country, age), and his season's figures, ESPN's for the competition and FotMob's
 * rating, each only when a source gave it (nothing is ever filled in).
 */
final class PlayerFacts {
    private PlayerFacts() {}

    /** FotMob's short names for the places on the pitch, in words. */
    static String place(String code) {
        switch (code == null ? "" : code.trim().toUpperCase(Locale.ROOT)) {
            case "GK": return "Goalkeeper";
            case "CB": case "LCB": case "RCB": return "Centre-back";
            case "RB": return "Right-back";
            case "LB": return "Left-back";
            case "RWB": return "Right wing-back";
            case "LWB": return "Left wing-back";
            case "DM": case "CDM": return "Defensive midfield";
            case "CM": case "LCM": case "RCM": return "Central midfield";
            case "AM": case "CAM": return "Attacking midfield";
            case "RM": return "Right midfield";
            case "LM": return "Left midfield";
            case "RW": return "Right winger";
            case "LW": return "Left winger";
            case "ST": return "Striker";
            case "CF": return "Centre-forward";
            case "SS": return "Second striker";
            default: return "";
        }
    }

    /** Pure: FotMob's places ("RB,LB,LWB") as {his main place in words, the others as given}; {"", ""} for none. */
    static String[] positions(String desc) {
        if (desc == null || desc.trim().isEmpty()) return new String[]{"", ""};
        String[] codes = desc.split(",");
        String main = place(codes[0]);
        if (main.isEmpty()) main = codes[0].trim();
        List<String> rest = new ArrayList<>();
        for (int i = 1; i < codes.length; i++) {
            String c = codes[i].trim();
            if (!c.isEmpty() && !rest.contains(c)) rest.add(c);
        }
        return new String[]{main, String.join(", ", rest)};
    }

    /** Pure: the line's label, as the carousel sets it beside the card: "DEFENDERS", "MANAGER". */
    static String lineLabel(String line) {
        switch (line == null ? "" : line) {
            case "Goalkeepers": return "GOALKEEPERS";
            case "Defenders": return "DEFENDERS";
            case "Midfielders": return "MIDFIELDERS";
            case "Forwards": return "FORWARDS";
            case "Manager": return "MANAGER";
            default: return "SQUAD";
        }
    }

    /** Pure: one of the line in words, for the page: "Defender", "Manager". */
    static String lineOne(String line) {
        switch (line == null ? "" : line) {
            case "Goalkeepers": return "Goalkeeper";
            case "Defenders": return "Defender";
            case "Midfielders": return "Midfielder";
            case "Forwards": return "Forward";
            case "Manager": return "Manager";
            default: return "";
        }
    }

    /** Pure: his age on a day from his birthday ("1999-03-18"), else the age given; 0 when neither is known. */
    static int age(String born, int given, LocalDate today) {
        if (born != null && born.length() == 10) {
            try {
                LocalDate b = LocalDate.parse(born);
                int a = b.until(today).getYears();
                if (a > 10 && a < 80) return a;
            } catch (RuntimeException ignored) {
                // the age given, then
            }
        }
        return given > 0 ? given : 0;
    }

    /** Pure: his birthday as the page puts it ("18 March 1999"); "" when not known. */
    static String birthday(String born) {
        if (born == null || born.length() != 10) return "";
        try {
            return LocalDate.parse(born).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK));
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** A figure on the page: its value, its name, and where it is from ("ESPN" or "FotMob"). */
    static final class Figure {
        final String value, name, from;

        Figure(String value, String name, String from) {
            this.value = value;
            this.name = name;
            this.from = from;
        }
    }

    /**
     * Pure: his season in figures, in the page's order: ESPN's for the competition (appearances, goals, assists, shots,
     * on target, cards, fouls, offsides; a keeper's saves and goals let in), FotMob's average rating, and FotMob's goals,
     * assists and cards where ESPN gives none. Only figures a source gave; none for a manager.
     */
    static List<Figure> season(TeamData.Player p) {
        List<Figure> out = new ArrayList<>();
        if ("Manager".equals(p.line)) return out;
        Map<String, Integer> e = p.espnStats;
        boolean keeper = "Goalkeepers".equals(p.line) || "Goalkeeper".equals(p.position);
        boolean espn = e.containsKey("appearances");
        if (espn) {
            out.add(new Figure(String.valueOf(e.get("appearances")), "Appearances", "ESPN"));
            Integer bench = e.get("subIns");
            if (bench != null && bench > 0) out.add(new Figure(String.valueOf(bench), "Off the bench", "ESPN"));
            if (keeper) {
                add(out, e, "saves", "Saves");
                add(out, e, "goalsConceded", "Goals let in");
            }
            add(out, e, "totalGoals", "Goals");
            add(out, e, "goalAssists", "Assists");
        }
        Double rating = p.fotmobStats.get("rating");
        if (rating != null && rating > 0) out.add(new Figure(String.format(Locale.UK, "%.2f", rating), "Average rating", "FotMob"));
        if (espn) {
            if (!keeper) {
                add(out, e, "totalShots", "Shots");
                add(out, e, "shotsOnTarget", "On target");
            }
            add(out, e, "yellowCards", "Yellow cards");
            add(out, e, "redCards", "Red cards");
            add(out, e, "foulsCommitted", "Fouls");
            add(out, e, "foulsSuffered", "Fouled");
            if (!keeper) add(out, e, "offsides", "Offside");
            Integer own = e.get("ownGoals");
            if (own != null && own > 0) out.add(new Figure(String.valueOf(own), "Own goals", "ESPN"));
        } else {
            fot(out, p, "goals", "Goals");
            fot(out, p, "assists", "Assists");
            fot(out, p, "ycards", "Yellow cards");
            fot(out, p, "rcards", "Red cards");
        }
        return out;
    }

    private static void add(List<Figure> out, Map<String, Integer> e, String key, String name) {
        Integer v = e.get(key);
        if (v != null) out.add(new Figure(String.valueOf(v), name, "ESPN"));
    }

    private static void fot(List<Figure> out, TeamData.Player p, String key, String name) {
        Double v = p.fotmobStats.get(key);
        if (v != null) out.add(new Figure(String.valueOf(Math.round(v)), name, "FotMob"));
    }

    /** Pure: where the figures come from, for the line under them: "ESPN · 2026-27 English Premier League; rating FotMob". */
    static String sources(List<Figure> figures, String season) {
        boolean espn = false, fotmob = false;
        for (Figure f : figures) {
            if ("ESPN".equals(f.from)) espn = true;
            if ("FotMob".equals(f.from)) fotmob = true;
        }
        List<String> parts = new ArrayList<>();
        if (espn) parts.add("ESPN" + (season == null || season.isEmpty() ? "" : " · " + season));
        if (fotmob) parts.add("FotMob");
        return parts.isEmpty() ? "" : "Figures from " + String.join(", ", parts);
    }
}
