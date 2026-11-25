package com.gathin.broadcast;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Each club's own goal animation (GoalReveal) for the 96 clubs of this season's Premier League, LaLiga, Serie A,
 * Bundesliga and Ligue 1 (the season intros' clubs, tools/season_clubs.json, by ESPN's team id): the two colours its
 * sweep runs through (its own, primary then second), GOAL!'s ink on them (at least WCAG 4.5 on both), the colour of what
 * it is known by on the dark strip, and that motif itself: "e:" an emoji drawn as a silhouette in the device's own emoji
 * font (the same pictures as the season intros'), "s:" a shape drawn by GoalShapes (the season intros' cannon, gull,
 * tower and others, drawn our own way), or "p:" a phrase its fans are known by (YNWA, HALA MADRID, MIA SAN MIA...).
 * A club is found by its ESPN id first, then by its name as any feed writes it (accents, FC/AFC/CF/SC and the like,
 * short forms). Any other team has the generic: its own colour from the board, a football, then GOAL!. Pure Java.
 */
public final class ClubGoals {
    private ClubGoals() { }

    /** One club's animation, or the generic one made for a team's colour. */
    public static final class Club {
        /** ESPN's id ("" for the generic) and the club's name. */
        public final String id, name;
        /** The sweep's colours (from the scorers' name across), GOAL!'s ink on them, the motif's colour, the dark strip. */
        public final int sweep0, sweep1, ink, motifInk, ground;
        /** "e:<code points>", "s:<shape>" or "p:<phrase>". */
        public final String motif;
        /** Other names it goes by, as written (for the matching). */
        final String[] aliases;

        Club(String id, String name, String[] aliases, int sweep0, int sweep1, int ink, int motifInk, String motif) {
            this.id = id;
            this.name = name;
            this.aliases = aliases;
            this.sweep0 = sweep0;
            this.sweep1 = sweep1;
            this.ink = ink;
            this.motifInk = motifInk;
            this.motif = motif;
            this.ground = groundOf(sweep0, ink);
        }

        /** The generic one (any team outside the five leagues, or every team when Generic is chosen). */
        public boolean generic() {
            return id.isEmpty();
        }

        /** The motif's kind: 'e' an emoji, 's' a shape, 'p' a phrase. */
        public char kind() {
            return motif.length() > 2 ? motif.charAt(0) : 's';
        }

        /** The motif without its kind ("1f981", "cannon", "YNWA"). */
        public String what() {
            return motif.length() > 2 ? motif.substring(2) : "ball";
        }
    }

    /**
     * id|name|other names (;)|sweep from|sweep to|GOAL! ink|motif colour|motif. The colours are the clubs' own, the
     * sweep's two chosen so one ink reads on both (a red to the club's navy or black under white, a sky blue to white
     * under navy); the motifs as the season intros have them, or the phrase a club is better known by.
     */
    private static final String[] ROWS = {
            // ---- Premier League
            "349|AFC Bournemouth|bournemouth;cherries|DA291C|111111|FFFFFF|FFFFFF|e:1f352",
            "359|Arsenal|arsenal fc|DB0007|063672|FFFFFF|F6C342|s:cannon",
            "362|Aston Villa|villa|7A0E3A|2F6DB5|FFFFFF|95BFE5|e:1f981",
            "337|Brentford|brentford fc|D71920|111111|FFFFFF|FFD400|e:1f41d",
            "331|Brighton & Hove Albion|brighton;brighton and hove albion|0A6AD0|001E5A|FFFFFF|FFFFFF|s:gull",
            "363|Chelsea|chelsea fc|034694|1C61D6|FFFFFF|F5C518|e:1f981",
            "388|Coventry City|coventry|6CC5EE|FFFFFF|0B2545|6CC5EE|e:1f418",
            "384|Crystal Palace|palace|1B458F|C4122E|FFFFFF|FFFFFF|e:1f985",
            "368|Everton|everton fc|1F4DC2|001A5E|FFFFFF|FFFFFF|s:tower",
            "370|Fulham|fulham fc|CC0000|111111|FFFFFF|FFFFFF|e:1f3e1",
            "306|Hull City|hull|F5A12D|FFC340|111111|F5A12D|e:1f42f",
            "373|Ipswich Town|ipswich|1E5BC6|062A6B|FFFFFF|FFFFFF|e:1f40e",
            "357|Leeds United|leeds|FFFFFF|FFCD00|1D428A|FFFFFF|s:rose",
            "364|Liverpool|liverpool fc|D00027|6E0516|FFFFFF|F6EB61|p:YNWA",
            "382|Manchester City|man city;man city fc|6CABDD|FFFFFF|1C2C5B|6CABDD|e:26f5",
            "360|Manchester United|man united;man utd;manchester utd|DA291C|111111|FFFFFF|FBE122|s:trident",
            "361|Newcastle United|newcastle;newcastle utd|111111|5A5A5A|FFFFFF|FFFFFF|e:1f426",
            "393|Nottingham Forest|nottm forest;nott'm forest;forest|E1001A|6B0010|FFFFFF|FFFFFF|e:1f333",
            "366|Sunderland|sunderland afc|DC1428|111111|FFFFFF|FFFFFF|e:1f408-200d-2b1b",
            "367|Tottenham Hotspur|tottenham;spurs|1E3A8A|0A1430|FFFFFF|FFFFFF|e:1f413",
            // ---- LaLiga
            "96|Alavés|deportivo alaves|0A3FAF|062066|FFFFFF|FFFFFF|e:1f3f0",
            "93|Athletic Club|athletic bilbao|D71920|111111|FFFFFF|FFFFFF|e:1f981",
            "1068|Atlético Madrid|atletico de madrid;club atletico de madrid;atleti;atletico|CB3524|272E61|FFFFFF|FFFFFF|e:1f43b",
            "83|Barcelona|fc barcelona;barca|004D98|A50044|FFFFFF|EDBB00|p:BARÇA",
            "85|Celta Vigo|celta;rc celta;celta de vigo|8AC3EE|FFFFFF|0E3E73|8AC3EE|e:1f41f",
            "90|Deportivo|deportivo la coruna;rc deportivo;depor|1B57C2|0A2A6B|FFFFFF|FFFFFF|s:lighthouse",
            "3751|Elche|elche cf|0F7F38|063D1A|FFFFFF|FFFFFF|e:1f334",
            "88|Espanyol|rcd espanyol;espanyol barcelona|1F6FD1|0A2E6E|FFFFFF|FFFFFF|e:1f99c",
            "2922|Getafe|getafe cf|1E6FD9|062E66|FFFFFF|FFFFFF|e:2708",
            "1538|Levante|levante ud|0B4FA3|B5121B|FFFFFF|FFD400|e:1f438",
            "99|Málaga|malaga cf|6EC1E4|FFFFFF|0A2E5C|6EC1E4|e:2600",
            "97|Osasuna|ca osasuna|D91A21|0A2B6B|FFFFFF|FFFFFF|e:1f402",
            "87|Racing Santander|real racing club;racing de santander|127A35|063D1A|FFFFFF|FFFFFF|e:26f5",
            "101|Rayo Vallecano|rayo|E30613|8A0010|FFFFFF|FFFFFF|e:26a1",
            "244|Real Betis|betis;real betis balompie|008045|003D21|FFFFFF|FFFFFF|e:1f33f",
            "86|Real Madrid|real madrid cf|FFFFFF|FEBE10|1B1B3A|FEBE10|p:HALA MADRID",
            "89|Real Sociedad|la real;real sociedad de futbol|0067B1|003E73|FFFFFF|FFFFFF|e:1f41a",
            "243|Sevilla|sevilla fc|C8102E|7A0019|FFFFFF|FFFFFF|e:1f34a",
            "94|Valencia|valencia cf|FFFFFF|EE7F00|111111|FFFFFF|e:1f987",
            "102|Villarreal|villarreal cf|FFE667|F2C200|0A2B6B|FFE667|s:submarine",
            // ---- Serie A
            "103|AC Milan|milan|E2001A|111111|FFFFFF|FFFFFF|e:1f608",
            "104|AS Roma|roma|8E1B2E|C8400A|FFFFFF|F0BC42|e:1f43a",
            "105|Atalanta|atalanta bc|1E71B8|111111|FFFFFF|F2C230|e:1f34e",
            "107|Bologna|bologna fc 1909|A21C26|1A2F48|FFFFFF|FFFFFF|s:towers",
            "2925|Cagliari|cagliari calcio|A6192E|002350|FFFFFF|FFFFFF|e:1f9a9",
            "2572|Como|como 1907|2F6BD6|0C2A66|FFFFFF|FFFFFF|e:1f3d4",
            "109|Fiorentina|acf fiorentina|6B2FA8|2A0D4D|FFFFFF|FFFFFF|e:269c",
            "4057|Frosinone|frosinone calcio|FFD84D|F2B200|1A3D8F|FFD84D|e:1f424",
            "3263|Genoa|genoa cfc|A6192E|0A2140|FFFFFF|F2C230|e:1f985",
            "110|Inter|internazionale;inter milan;fc internazionale milano;inter milano|0068A8|111111|FFFFFF|F2C230|e:1f40d",
            "111|Juventus|juve;juventus fc|141414|6A6A6A|FFFFFF|FFFFFF|p:FINO ALLA FINE",
            "112|Lazio|ss lazio|87D8F7|FFFFFF|0A2F5A|87D8F7|e:1f985",
            "113|Lecce|us lecce|FFE500|F5B800|8A0010|FFE500|e:1f43a",
            "4007|Monza|ac monza|E2001A|7A0010|FFFFFF|FFFFFF|e:1f451",
            "114|Napoli|ssc napoli|12A0D7|6CC8F0|002B5C|6CC8F0|e:1f30b",
            "115|Parma|parma calcio 1913|1A3D8F|0A1E52|FFFFFF|FFD400|e:1f9c0",
            "3997|Sassuolo|us sassuolo|00843F|0A0A0A|FFFFFF|00A752|p:NEROVERDI",
            "239|Torino|torino fc|8A1E03|3D0D01|FFFFFF|FFFFFF|e:1f402",
            "118|Udinese|udinese calcio|111111|5B5B5B|FFFFFF|FFFFFF|e:1f993",
            "17530|Venezia|venezia fc|00843D|111111|FFFFFF|F37A1F|e:1f6f6",
            // ---- Bundesliga
            "598|1. FC Union Berlin|union berlin|D4011D|5C0010|FFFFFF|FFD400|p:EISERN UNION",
            "131|Bayer Leverkusen|leverkusen;bayer 04 leverkusen|E32221|111111|FFFFFF|FFFFFF|e:2699",
            "132|FC Bayern München|bayern munich;bayern;fc bayern munich|DC052D|0066B2|FFFFFF|FFFFFF|p:MIA SAN MIA",
            "124|Borussia Dortmund|dortmund;bvb|FDE100|F2B705|111111|FDE100|p:ECHTE LIEBE",
            "268|Borussia Mönchengladbach|monchengladbach;gladbach;borussia m'gladbach|0B7F3A|111111|FFFFFF|FFFFFF|e:1f40e",
            "125|Eintracht Frankfurt|frankfurt|E1000F|111111|FFFFFF|FFFFFF|e:1f985",
            "3841|FC Augsburg|augsburg|BA3733|46793A|FFFFFF|FFFFFF|s:pinecone",
            "122|1. FC Köln|fc cologne;cologne|D41920|6B0A0F|FFFFFF|FFFFFF|e:1f410",
            "127|Hamburger SV|hamburg;hamburg sv;hsv|0A3EA8|04153D|FFFFFF|FFFFFF|e:2693",
            "2950|1. FSV Mainz 05|mainz|C8102E|6B0A0F|FFFFFF|FFFFFF|e:1f3ad",
            "11420|RB Leipzig|leipzig;rasenballsport leipzig|DD0741|0C2043|FFFFFF|FFFFFF|e:1f402",
            "126|SC Freiburg|freiburg|D2122E|111111|FFFFFF|FFFFFF|e:1f332",
            "3307|SC Paderborn 07|paderborn|0A50A1|04173D|FFFFFF|FFFFFF|e:1f4a7",
            "10388|SV Elversberg|elversberg|2A2A2A|6A6A6A|FFFFFF|FFFFFF|e:1f680",
            "133|Schalke 04|schalke|004D9D|00163D|FFFFFF|FFFFFF|e:26cf",
            "7911|TSG Hoffenheim|hoffenheim;tsg 1899 hoffenheim|1961B5|062A5A|FFFFFF|FFFFFF|e:1f33b",
            "134|VfB Stuttgart|stuttgart|E32219|7A0A10|FFFFFF|FFFFFF|e:1f98c",
            "137|Werder Bremen|bremen;sv werder bremen|0F7A42|063D21|FFFFFF|FFFFFF|e:1f511",
            // ---- Ligue 1
            "172|AJ Auxerre|auxerre|0F3D8F|2F6BC8|FFFFFF|FFFFFF|e:1f40c",
            "174|AS Monaco|monaco|E51B22|7A0010|FFFFFF|FFFFFF|e:1f48e",
            "7868|Angers SCO|angers|1A1A1A|5E5E5E|FFFFFF|FFFFFF|e:1f3f0",
            "6997|Stade Brestois|brest;stade brestois 29|E2001A|5E000B|FFFFFF|FFFFFF|e:2693",
            "3236|Le Havre AC|le havre;havre ac|0B2A63|2C6BB8|FFFFFF|8CC8EC|e:1f6a2",
            "2697|Le Mans FC|le mans|D62B11|111111|FFFFFF|FFD400|e:1f3c1",
            "175|RC Lens|lens|FFD400|F2A900|8A0010|FFD400|e:26cf",
            "166|LOSC Lille|lille;lille osc|E01E13|0B1F4D|FFFFFF|FFFFFF|e:1f415",
            "273|FC Lorient|lorient|F58113|FFB347|111111|F58113|e:1f41f",
            "167|Olympique Lyonnais|lyon;ol|DA0812|14387F|FFFFFF|FFFFFF|e:1f981",
            "176|Olympique de Marseille|marseille;om|2FAEE0|FFFFFF|0B2A4F|2FAEE0|p:DROIT AU BUT",
            "2502|OGC Nice|nice|E2001A|111111|FFFFFF|FFFFFF|e:1f985",
            "6851|Paris FC|paris fc|1B2A4A|2F5FA8|FFFFFF|8FC8EE|e:1f950",
            "160|Paris Saint-Germain|psg;paris sg|004170|DA291C|FFFFFF|FFFFFF|p:ICI C'EST PARIS",
            "169|Stade Rennais|rennes;stade rennais fc|E2001A|111111|FFFFFF|FFFFFF|e:1f95e",
            "180|RC Strasbourg|strasbourg;rc strasbourg alsace;racing strasbourg|0A5FD1|032A6B|FFFFFF|FFFFFF|e:1f9a9",
            "179|Toulouse FC|toulouse|6A1B9A|2A0846|FFFFFF|C9A0F0|e:1f338",
            "170|ESTAC Troyes|troyes;es troyes ac|1F5FD1|072A66|FFFFFF|FFFFFF|e:1f3d8",
    };

    /** The generic's motif: a football. */
    public static final String BALL = "s:ball";
    /** The dark strip's night, and the generic's colour for a team the board has none for. */
    static final int NIGHT = 0xFF070A16, PLAIN = 0xFF2A4FD0, DARK_INK = 0xFF0B1020;

    /** The words a club's name is matched without: what kind of club it is, not which. */
    private static final Set<String> NOT_NAMES = new HashSet<>(java.util.Arrays.asList("fc", "afc", "cf", "cfc", "sc", "ac", "acf",
            "as", "ss", "ssc", "us", "bc", "rc", "rcd", "ca", "cd", "ud", "sd", "sad", "sv", "vfb", "vfl", "tsg", "fsv", "ogc",
            "losc", "aj", "sco", "estac", "club", "calcio", "futbol", "football", "de", "the"));

    private static final List<Club> ALL = new ArrayList<>();
    private static final Map<String, Club> BY_ID = new HashMap<>(), BY_NAME = new HashMap<>();
    /** The names of two words or more, longest first, for a name that carries one ("Manchester City Women"). */
    private static final List<String> LONG = new ArrayList<>();

    static {
        for (String row : ROWS) {
            String[] f = row.split("\\|", -1);
            String[] aliases = f[2].isEmpty() ? new String[0] : f[2].split(";");
            Club c = new Club(f[0], f[1], aliases, argb(f[3]), argb(f[4]), argb(f[5]), argb(f[6]), f[7]);
            ALL.add(c);
            BY_ID.put(c.id, c);
            index(c.name, c);
            for (String a : aliases) index(a, c);
        }
        for (String k : BY_NAME.keySet()) if (k.indexOf(' ') > 0) LONG.add(k);
        Collections.sort(LONG, (a, b) -> b.length() != a.length() ? b.length() - a.length() : a.compareTo(b));
    }

    private static void index(String name, Club c) {
        String k = norm(name);
        if (!k.isEmpty() && !BY_NAME.containsKey(k)) BY_NAME.put(k, c);
    }

    private static int argb(String hex) {
        return 0xFF000000 | Integer.parseInt(hex, 16);
    }

    /** The 96 clubs, in the table's order (league by league). */
    public static List<Club> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** A club by ESPN's team id, else null. */
    public static Club byId(String id) {
        return id == null ? null : BY_ID.get(id.trim());
    }

    /**
     * A club by ESPN's id first, then by any of the names given (the feed's full name, its short name): exactly as the
     * table names it once accents, the clubs' FC/AFC/CF/SC and the like and the numbers are taken out, else a name of two
     * words or more that the name given carries whole. Null for a team that is none of them.
     */
    public static Club find(String id, String... names) {
        Club c = byId(id);
        if (c != null) return c;
        if (names == null) return null;
        for (String n : names) {
            c = BY_NAME.get(norm(n));
            if (c != null) return c;
        }
        for (String n : names) {
            String k = " " + norm(n) + " ";
            if (k.trim().isEmpty()) continue;
            for (String l : LONG) if (k.contains(" " + l + " ")) return BY_NAME.get(l);
        }
        return null;
    }

    /**
     * Pure: a club's name as the table matches it: accents gone (München munchen, Atlético atletico, ß ss), lower case,
     * anything but letters and figures a space, and the words saying what kind of club it is (FC, AFC, CF, SC, AC, AS,
     * Calcio, Club...) and the numbers (1. FC Köln, Mainz 05, Bologna 1909) left out.
     */
    public static String norm(String name) {
        if (name == null) return "";
        String t = name.replace("ß", "ss").replace("ø", "o").replace("Ø", "o").replace("æ", "ae").replace("Æ", "ae");
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        t = t.replaceAll("[^a-z0-9]+", " ").trim();
        if (t.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String w : t.split(" ")) {
            if (w.isEmpty() || NOT_NAMES.contains(w) || w.matches("[0-9]+")) continue;
            if (out.length() > 0) out.append(' ');
            out.append(w);
        }
        return out.toString();
    }

    /**
     * Pure: the generic animation for a team (any outside the five leagues): its own colour from the board (a plain blue
     * when it has none) swept to a deeper one of it under white, or for a light colour to a lighter one under a near-black,
     * the colour toned until GOAL! reads at 4.5 on both; a football for its motif.
     */
    public static Club generic(int colour) {
        int c = (colour >>> 24) == 0 ? PLAIN : BoardColours.opaque(colour);
        if (BoardColours.contrast(DARK_INK, c) >= 4.5 && BoardColours.contrast(BoardColours.WHITE, c) < 4.5) {
            // a lighter one of it; for one already near white (a white kit), a pale steel
            int light = BoardColours.luminance(c) > 0.7 ? BoardColours.mix(c, 0xFFAFC0DA, 0.7f) : BoardColours.mix(c, BoardColours.WHITE, 0.45f);
            return new Club("", "", new String[0], c, light, DARK_INK, c, BALL);
        }
        int c0 = c;
        for (int k = 0; k < 20 && BoardColours.contrast(BoardColours.WHITE, c0) < 4.5; k++) c0 = BoardColours.mix(c0, NIGHT, 0.08f);
        // a deeper one of it; for a colour already near black, a lighter one (still under white)
        int c1 = BoardColours.luminance(c0) < 0.03 ? BoardColours.mix(c0, BoardColours.WHITE, 0.3f) : BoardColours.mix(c0, NIGHT, 0.55f);
        int motif = BoardColours.legible(BoardColours.mix(c, BoardColours.WHITE, 0.7f), groundOf(c0, BoardColours.WHITE), 4.5);
        return new Club("", "", new String[0], c0, c1, BoardColours.WHITE, motif, BALL);
    }

    /**
     * Pure: the dark strip the crest, motif and GOAL! play on: the sweep's first colour deep in the night under a light
     * ink; under a dark ink (a light club: City's sky, Madrid's white) that ink deepened instead.
     */
    public static int groundOf(int sweep0, int ink) {
        boolean light = BoardColours.luminance(BoardColours.opaque(ink)) > 0.5;
        return light ? BoardColours.mix(BoardColours.opaque(sweep0), NIGHT, 0.8f) : BoardColours.mix(BoardColours.opaque(ink), NIGHT, 0.45f);
    }

    /** Pure: the clubs by league, as the table has them, for the reports and tests: {league: names}. */
    public static Map<String, List<String>> byLeague() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        String[] leagues = {"Premier League", "LaLiga", "Serie A", "Bundesliga", "Ligue 1"};
        int[] counts = {20, 20, 20, 18, 18};
        int at = 0;
        for (int i = 0; i < leagues.length; i++) {
            List<String> names = new ArrayList<>();
            for (int k = 0; k < counts[i] && at < ALL.size(); k++) names.add(ALL.get(at++).name);
            out.put(leagues[i], names);
        }
        return out;
    }
}
