package com.gathin.broadcast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tennis and UFC for the scoreboard, from ESPN's public scoreboards, and boxing from the programme's own title (ESPN has
 * no boxing to go by): what a programme on the channel is (sportOf), and which match or fight on now it is showing
 * (pick). The same on the TV and the phone. ESPN gives tennis by the game (no points, no server): the board shows the
 * points only where the app reads them off the channel's own scoreboard.
 *
 * <p>Pure but for fetch, which the apps call off the main thread (a generic browser's name only, nothing of the
 * family's).
 */
public final class SportFeeds {
    private SportFeeds() { }

    static final String FEED = "https://site.api.espn.com/apis/site/v2/sports/%s/scoreboard";
    /** UFC's logo (the boards tint it to their ink). Tennis and boxing have none: the board draws a ball, a glove. */
    public static final String UFC_LOGO = "https://a.espncdn.com/i/teamlogos/leagues/500/ufc.png";

    /** The feed's words for a sport's board: "tennis/atp", "tennis/wta", "mma/ufc". Off the main thread. */
    public static String fetch(String path) throws java.io.IOException {
        return new String(Net.get(String.format(FEED, path), 8_000, 4 << 20), java.nio.charset.StandardCharsets.UTF_8);
    }

    // ---- what a programme is --------------------------------------------------------------------------------------

    private static final Pattern UFC = Pattern.compile("\\bUFC\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOXING = Pattern.compile("\\bboxing\\b|\\bfight night\\b|\\bboxe\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TENNIS = Pattern.compile(
            "\\btennis\\b|\\bATP\\b|\\bWTA\\b|wimbledon|roland[- ]garros|french open|australian open|davis cup|billie jean king cup"
                    + "|laver cup|united cup", Pattern.CASE_INSENSITIVE);
    private static final Pattern US_OPEN = Pattern.compile("\\bUS Open\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_LIVE = Pattern.compile("\\b(highlights|review|reaction|preview|countdown|classic|"
            + "best of|rewind|magazine|news|weigh[- ]?in|press conference|embedded|replay)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Pure: the sport of a programme by its title in the guide (Sky's, or the playlist's): TENNIS, BOXING, UFC, or 0
     * for anything else (a highlights show, a preview, a press conference: not a live event).
     */
    public static int sportOf(String title) {
        if (title == null || title.trim().isEmpty()) return 0;
        if (NOT_LIVE.matcher(title).find()) return 0;
        if (UFC.matcher(title).find()) return SportBoard.UFC;
        if (BOXING.matcher(title).find()) return SportBoard.BOXING;
        if (TENNIS.matcher(title).find()) return SportBoard.TENNIS;
        String t = title.toLowerCase(Locale.ROOT);
        if (US_OPEN.matcher(title).find() && !t.contains("golf")) return SportBoard.TENNIS;
        return 0;
    }

    private static final Pattern VERSUS = Pattern.compile("(.+?)\\s+(?:v|vs\\.?|versus)\\s+(.+)", Pattern.CASE_INSENSITIVE);

    /**
     * Pure: the two names a title sets against each other ("Live Boxing: Joshua v Dubois", "Gane vs. Hokit - Heavyweight"):
     * {first, second}, each without what the guide adds round them; null when it names none.
     */
    public static String[] versus(String title) {
        if (title == null) return null;
        String t = title.trim();
        int colon = t.lastIndexOf(':');
        if (colon >= 0) t = t.substring(colon + 1).trim();
        t = t.replaceAll("\\s+[-–—|(].*$", "").replaceAll("(?i)^(live|boxing|fight night|ufc \\d+|ufc)\\b[:\\s]*", "").trim();
        Matcher m = VERSUS.matcher(t);
        if (!m.matches()) return null;
        String x = tidyName(m.group(1)), y = tidyName(m.group(2));
        return x.isEmpty() || y.isEmpty() ? null : new String[]{x, y};
    }

    private static String tidyName(String s) {
        return s == null ? "" : s.replaceAll("(?i)^(live|boxing)\\s+", "").replaceAll("[\"'’]+$", "").trim();
    }

    // ---- tennis ---------------------------------------------------------------------------------------------------

    /** Pure: the matches in ESPN's tennis scoreboard (tour "ATP" or "WTA"), singles only, each as a board. */
    public static List<SportBoard> tennis(String json, String tour) {
        List<SportBoard> out = new ArrayList<>();
        try {
            JSONObject board = new JSONObject(json);
            JSONArray events = board.optJSONArray("events");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject e = events.getJSONObject(i);
                String event = eventName(e, tour);
                boolean major = e.optBoolean("major");
                JSONArray groups = e.optJSONArray("groupings");
                for (int g = 0; groups != null && g < groups.length(); g++) {
                    JSONObject group = groups.getJSONObject(g);
                    JSONObject gr = group.optJSONObject("grouping");
                    String gn = gr == null ? "" : gr.optString("displayName");
                    if (!gn.contains("Singles")) continue;
                    JSONArray comps = group.optJSONArray("competitions");
                    for (int c = 0; comps != null && c < comps.length(); c++) {
                        SportBoard s = match(comps.getJSONObject(c));
                        if (s == null) continue;
                        s.event = event;
                        s.division = gn;
                        s.bestOf = major && gn.startsWith("Men") ? 5 : s.bestOf;
                        out.add(s);
                    }
                }
            }
        } catch (Exception ignored) {
            // what was read stands
        }
        return out;
    }

    /** Pure: a tournament's name as the board shows it, without its sponsor: a major's own, the Finals, else tour and city. */
    static String eventName(JSONObject e, String tour) {
        String name = e.optString("name", e.optString("shortName"));
        String low = name.toLowerCase(Locale.ROOT);
        String[] majors = {"Wimbledon", "US Open", "Australian Open", "Roland Garros", "Roland-Garros", "French Open",
                "Davis Cup", "Billie Jean King Cup", "Laver Cup", "United Cup", "Olympic"};
        for (String m : majors) if (low.contains(m.toLowerCase(Locale.ROOT))) return m.equals("Roland-Garros") ? "Roland Garros" : m;
        String t = tour == null ? "" : tour.toUpperCase(Locale.ROOT);
        if (low.contains("finals")) return low.contains("next gen") ? "Next Gen " + t + " Finals" : t + " Finals";
        String city = "";
        JSONArray groups = e.optJSONArray("groupings");
        JSONObject venue = null;
        if (groups != null && groups.length() > 0) {
            JSONArray comps = groups.optJSONObject(0) == null ? null : groups.optJSONObject(0).optJSONArray("competitions");
            if (comps != null && comps.length() > 0 && comps.optJSONObject(0) != null) venue = comps.optJSONObject(0).optJSONObject("venue");
        }
        if (venue != null) city = venue.optString("fullName").split(",")[0].trim();
        return city.isEmpty() ? t : (t + " " + city).trim();
    }

    /** Pure: one tennis match. Null when it has not two players. */
    static SportBoard match(JSONObject comp) {
        JSONArray cs = comp.optJSONArray("competitors");
        if (cs == null || cs.length() < 2) return null;
        SportBoard s = new SportBoard();
        s.sport = SportBoard.TENNIS;
        s.id = comp.optString("id");
        JSONObject st = comp.optJSONObject("status");
        JSONObject type = st == null ? null : st.optJSONObject("type");
        s.state = type == null ? "pre" : type.optString("state", "pre");
        s.set = st == null ? 0 : st.optInt("period");
        JSONObject round = comp.optJSONObject("round");
        s.stage = round == null ? "" : round.optString("displayName");
        JSONObject fmt = comp.optJSONObject("format");
        JSONObject reg = fmt == null ? null : fmt.optJSONObject("regulation");
        if (reg != null && reg.optInt("periods") > 0) s.bestOf = reg.optInt("periods");
        s.start = time(comp.optString("startDate", comp.optString("date")));
        String detail = type == null ? "" : type.optString("description");
        if (detail.toLowerCase(Locale.ROOT).contains("retired")) s.method = "Retired";
        JSONObject first = null, second = null;
        for (int i = 0; i < cs.length(); i++) {
            JSONObject x = cs.optJSONObject(i);
            if (x == null) continue;
            if (x.optInt("order", i + 1) == 1 && first == null) first = x;
            else if (second == null) second = x;
            else if (first == null) first = x;
        }
        if (first == null || second == null) return null;
        player(first, s.a);
        player(second, s.b);
        if (s.a.name.isEmpty() || s.b.name.isEmpty()) return null;
        return s;
    }

    private static void player(JSONObject x, SportBoard.Side side) {
        JSONObject a = x.optJSONObject("athlete");
        if (a == null) a = new JSONObject();
        side.name = a.optString("displayName", a.optString("fullName"));
        side.shortName = surname(a.optString("shortName"), side.name);
        JSONObject flag = a.optJSONObject("flag");
        side.flagUrl = flag == null ? "" : flag.optString("href");
        side.country = countryOf(side.flagUrl);
        side.colour = colourOf(side.country);
        side.winner = x.optBoolean("winner");
        JSONObject rank = x.optJSONObject("curatedRank");
        side.seed = rank != null && rank.optInt("current") > 0 && rank.optInt("current") < 99 ? String.valueOf(rank.optInt("current")) : "";
        JSONArray ls = x.optJSONArray("linescores");
        int n = ls == null ? 0 : ls.length();
        side.games = new int[n];
        side.tiebreak = new int[n];
        for (int i = 0; i < n; i++) {
            JSONObject l = ls.optJSONObject(i);
            side.games[i] = l == null ? 0 : (int) Math.round(l.optDouble("value", 0));
            side.tiebreak[i] = l == null || !l.has("tiebreak") ? -1 : l.optInt("tiebreak", -1);
        }
        String serving = x.optString("possession", x.optString("serving", ""));
        side.serving = "true".equalsIgnoreCase(serving);
    }

    /** Pure: the surname the boards use, from ESPN's short name ("A. Sabalenka", "S. Lozano Avellaneda") or the full one. */
    public static String surname(String shortName, String full) {
        String s = shortName == null ? "" : shortName.trim();
        if (s.matches("^\\p{L}{1,2}\\.\\s+.+")) return s.substring(s.indexOf('.') + 1).trim();
        if (!s.isEmpty() && !s.contains(" ")) return s;
        String f = full == null ? "" : full.trim();
        int sp = f.indexOf(' ');
        return sp > 0 ? f.substring(sp + 1).trim() : f.isEmpty() ? s : f;
    }

    /** Pure: the rank of a match by its players (the lower the likelier on television): the sum of their rankings. */
    static int rank(SportBoard s) {
        return seedOr(s.a.seed) + seedOr(s.b.seed);
    }

    private static int seedOr(String seed) {
        try {
            return Integer.parseInt(seed);
        } catch (RuntimeException e) {
            return 200;
        }
    }

    // ---- UFC ------------------------------------------------------------------------------------------------------

    /** Pure: the fights in ESPN's UFC scoreboard, each as a board, in the card's order (the main event last). */
    public static List<SportBoard> ufc(String json) {
        List<SportBoard> out = new ArrayList<>();
        try {
            JSONObject board = new JSONObject(json);
            JSONArray events = board.optJSONArray("events");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject e = events.getJSONObject(i);
                String event = e.optString("shortName", e.optString("name"));
                JSONArray comps = e.optJSONArray("competitions");
                for (int c = 0; comps != null && c < comps.length(); c++) {
                    SportBoard s = fight(comps.getJSONObject(c));
                    if (s == null) continue;
                    s.event = event;
                    s.mainEvent = c == comps.length() - 1;
                    out.add(s);
                }
            }
        } catch (Exception ignored) {
            // what was read stands
        }
        return out;
    }

    /** Pure: one UFC fight. ESPN's round clock counts up (the time into it): kept as secsIn. */
    static SportBoard fight(JSONObject comp) {
        JSONArray cs = comp.optJSONArray("competitors");
        if (cs == null || cs.length() < 2) return null;
        SportBoard s = new SportBoard();
        s.sport = SportBoard.UFC;
        s.id = comp.optString("id");
        s.roundSecs = 300;
        s.restSecs = 60;
        JSONObject fmt = comp.optJSONObject("format");
        JSONObject reg = fmt == null ? null : fmt.optJSONObject("regulation");
        if (reg != null && reg.optInt("periods") > 0) s.rounds = reg.optInt("periods");
        JSONObject type = comp.optJSONObject("type");
        s.stage = type == null ? "" : type.optString("abbreviation", type.optString("text"));
        JSONObject st = comp.optJSONObject("status");
        JSONObject stType = st == null ? null : st.optJSONObject("type");
        s.state = stType == null ? "pre" : stType.optString("state", "pre");
        s.round = st == null ? 0 : st.optInt("period");
        String clock = st == null ? "" : st.optString("displayClock");
        s.secsIn = seconds(clock);
        String detail = stType == null ? "" : stType.optString("detail", stType.optString("description")).toLowerCase(Locale.ROOT);
        s.resting = s.live() && (detail.contains("end of") || detail.contains("between"));
        JSONObject res = st == null ? null : st.optJSONObject("result");
        s.method = res != null ? res.optString("displayName", res.optString("shortDisplayName")) : "";
        if (s.over() && s.round > 0) s.finish = "R" + s.round + (s.secsIn > 0 ? " " + clock.trim() : "");
        s.start = time(comp.optString("startDate", comp.optString("date")));
        JSONObject red = null, blue = null;
        for (int i = 0; i < cs.length(); i++) {
            JSONObject x = cs.optJSONObject(i);
            if (x == null) continue;
            if (x.optInt("order", i + 1) == 1 && red == null) red = x;
            else if (blue == null) blue = x;
            else if (red == null) red = x;
        }
        if (red == null || blue == null) return null;
        fighter(red, s.a, SportBoard.RED_CORNER);
        fighter(blue, s.b, SportBoard.BLUE_CORNER);
        if (s.a.name.isEmpty() || s.b.name.isEmpty()) return null;
        return s;
    }

    private static void fighter(JSONObject x, SportBoard.Side side, int corner) {
        JSONObject a = x.optJSONObject("athlete");
        if (a == null) a = new JSONObject();
        side.name = a.optString("displayName", a.optString("fullName"));
        side.shortName = surname(a.optString("shortName"), side.name);
        JSONObject flag = a.optJSONObject("flag");
        side.flagUrl = flag == null ? "" : flag.optString("href");
        side.country = countryOf(side.flagUrl);
        side.colour = corner;
        side.winner = x.optBoolean("winner");
        String id = x.optString("id");
        side.photoUrl = id.isEmpty() ? "" : "https://a.espncdn.com/i/headshots/mma/players/full/" + id + ".png";
        JSONArray recs = x.optJSONArray("records");
        for (int i = 0; recs != null && i < recs.length(); i++) {
            JSONObject r = recs.optJSONObject(i);
            if (r != null && ("overall".equals(r.optString("name")) || "total".equals(r.optString("type")))) {
                side.record = r.optString("summary");
                break;
            }
        }
    }

    /** Pure: "2:27" as seconds (147); -1 for none ("-", ""). */
    public static int seconds(String clock) {
        if (clock == null) return -1;
        Matcher m = Pattern.compile("(\\d+):(\\d{2})").matcher(clock);
        return m.find() ? Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)) : -1;
    }

    private static long time(String iso) {
        try {
            String s = iso == null ? "" : iso.trim();
            if (s.matches(".*T\\d{2}:\\d{2}Z$")) s = s.replace("Z", ":00Z");
            return s.isEmpty() ? 0 : Instant.parse(s).toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ---- boxing ---------------------------------------------------------------------------------------------------

    /**
     * Pure: a boxing board from the programme's title (the two names it sets against each other; none: an empty one the
     * board fills with BOXING). Twelve rounds of three minutes, a minute between: the round clock starts at the first
     * bell, as seen on the screen (SportBoard.bell).
     */
    public static SportBoard boxing(String title, long start) {
        SportBoard s = new SportBoard();
        s.sport = SportBoard.BOXING;
        s.rounds = 12;
        s.roundSecs = 180;
        s.restSecs = 60;
        s.start = start;
        s.id = "boxing:" + (title == null ? "" : title.trim().toLowerCase(Locale.ROOT));
        String[] v = versus(title);
        if (v != null) {
            s.a.name = v[0];
            s.b.name = v[1];
            s.a.shortName = surname("", v[0]);
            s.b.shortName = surname("", v[1]);
        }
        s.a.colour = SportBoard.RED_CORNER;
        s.b.colour = SportBoard.BLUE_CORNER;
        if (title != null) {
            Matcher m = Pattern.compile("(?i)\\b(heavyweight|cruiserweight|light[- ]heavyweight|super[- ]middleweight|middleweight"
                    + "|super[- ]welterweight|welterweight|super[- ]lightweight|lightweight|super[- ]featherweight|featherweight"
                    + "|super[- ]bantamweight|bantamweight|flyweight)\\b").matcher(title);
            if (m.find()) s.stage = m.group(1).substring(0, 1).toUpperCase(Locale.ROOT) + m.group(1).substring(1).toLowerCase(Locale.ROOT);
        }
        s.event = "Boxing";
        return s;
    }

    // ---- which one is on --------------------------------------------------------------------------------------------

    /**
     * Pure: the match or fight a programme is showing, among the boards of its sport: one whose two names the title (or
     * the words read off the picture, seen) carries; else a fight on now, else the next on the card (the one after the
     * last over), else the last over; tennis: the live match of the title's tour with the
     * best-ranked players. Null when none fits.
     */
    public static SportBoard pick(List<SportBoard> boards, String title, String seen, long now) {
        if (boards == null || boards.isEmpty()) return null;
        String words = ((title == null ? "" : title) + " " + (seen == null ? "" : seen)).toLowerCase(Locale.ROOT);
        SportBoard named = null;
        for (SportBoard s : boards) {
            if (s.over() && now - s.start > 12 * 3_600_000L) continue;
            if (named(words, s.a) && named(words, s.b)) {
                if (named == null || s.live() || !named.live() && s.start > named.start) named = s;
            }
        }
        if (named != null) return named;
        boolean fights = boards.get(0).fight();
        if (fights) {
            for (SportBoard s : boards) if (s.live()) return s;
            int lastOver = -1;
            for (int i = 0; i < boards.size(); i++) if (boards.get(i).over()) lastOver = i;
            for (int i = lastOver + 1; i < boards.size(); i++) if ("pre".equals(boards.get(i).state)) return boards.get(i);
            return lastOver >= 0 ? boards.get(lastOver) : null;
        }
        String tour = words.contains("wta") || words.contains("women") ? "WTA" : words.contains("atp") || words.contains("men's") ? "ATP" : "";
        SportBoard best = null;
        for (SportBoard s : boards) {
            if (!s.live() || !ofTour(s, tour)) continue;
            if (best == null || rank(s) < rank(best)) best = s;
        }
        return best;
    }

    /** Pure: a match is of the tour a title names (WTA: the women's draw; ATP: the men's; none named: any). */
    static boolean ofTour(SportBoard s, String tour) {
        if (tour.isEmpty()) return true;
        String d = s.division == null ? "" : s.division.toLowerCase(Locale.ROOT), e = s.event.toUpperCase(Locale.ROOT);
        return "WTA".equals(tour) ? d.startsWith("women") || e.contains("WTA") : d.startsWith("men") || e.contains("ATP");
    }

    /** Pure: whether the words carry this side's surname (its last word, at least three letters, as a word). */
    static boolean named(String words, SportBoard.Side side) {
        String sur = side.shortName == null || side.shortName.isEmpty() ? surname("", side.name) : side.shortName;
        String[] parts = sur.toLowerCase(Locale.ROOT).split("[\\s-]+");
        String last = parts.length == 0 ? "" : parts[parts.length - 1];
        if (last.length() < 3) return false;
        return Pattern.compile("\\b" + Pattern.quote(last) + "\\b").matcher(words).find();
    }

    // ---- countries --------------------------------------------------------------------------------------------------

    /** Pure: the country code in an ESPN flag's address (".../countries/500/cro.png": CRO). */
    public static String countryOf(String flagUrl) {
        if (flagUrl == null) return "";
        Matcher m = Pattern.compile("/([a-z]{2,3})\\.png").matcher(flagUrl.toLowerCase(Locale.ROOT));
        return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : "";
    }

    /** Pure: a country's sporting colour (its shirts', its flag's strongest), for a tennis player's strip; a navy for others. */
    public static int colourOf(String code) {
        switch (code == null ? "" : code.toUpperCase(Locale.ROOT)) {
            case "GBR": case "ENG": case "SCO": return 0xFF1D3A8A;
            case "USA": return 0xFF1F3C88;
            case "ESP": return 0xFFC8102E;
            case "ITA": return 0xFF1C63B7;
            case "FRA": return 0xFF1E3FA6;
            case "GER": return 0xFFDD0000;
            case "SRB": return 0xFFC6363C;
            case "RUS": return 0xFF1C57A5;
            case "BLR": return 0xFFC8313E;
            case "UKR": return 0xFF0057B7;
            case "POL": return 0xFFDC143C;
            case "CZE": return 0xFF11457E;
            case "SVK": return 0xFF0B4EA2;
            case "SUI": return 0xFFD52B1E;
            case "AUT": return 0xFFC8102E;
            case "CRO": return 0xFFE4002B;
            case "GRE": return 0xFF0D5EAF;
            case "NOR": return 0xFFBA0C2F;
            case "DEN": return 0xFFC8102E;
            case "SWE": return 0xFF006AA7;
            case "FIN": return 0xFF003580;
            case "NED": return 0xFFF36C21;
            case "BEL": return 0xFFC8102E;
            case "POR": return 0xFF046A38;
            case "ARG": return 0xFF4BA3DC;
            case "BRA": return 0xFF009C3B;
            case "CHI": return 0xFFD52B1E;
            case "COL": return 0xFFFCD116;
            case "MEX": return 0xFF006847;
            case "CAN": return 0xFFD80621;
            case "AUS": return 0xFF00843D;
            case "NZL": return 0xFF111111;
            case "JPN": return 0xFFBC002D;
            case "CHN": return 0xFFDE2910;
            case "KOR": return 0xFFCD2E3A;
            case "KAZ": return 0xFF00AFCA;
            case "BUL": return 0xFF00966E;
            case "ROU": return 0xFF002B7F;
            case "HUN": return 0xFF436F4D;
            case "TUN": return 0xFFE70013;
            case "RSA": return 0xFF007A4D;
            case "IND": return 0xFFFF9933;
            case "IRL": return 0xFF169B62;
            case "NGA": return 0xFF008751;
            case "GEO": return 0xFFE8112D;
            case "ARM": return 0xFFD90012;
            case "TUR": return 0xFFE30A17;
            case "ISR": return 0xFF0038B8;
            case "LAT": return 0xFF9E3039;
            case "LTU": return 0xFF006A44;
            case "EST": return 0xFF0072CE;
            case "SLO": return 0xFF005DA4;
            case "BIH": return 0xFF002395;
            case "MNE": return 0xFFC40308;
            case "CUB": return 0xFF002A8F;
            case "PUR": return 0xFFED0000;
            case "DOM": return 0xFF002D62;
            case "PER": return 0xFFD91023;
            case "ECU": return 0xFFFFD100;
            case "URU": return 0xFF5CBFEB;
            case "PHI": return 0xFF0038A8;
            case "THA": return 0xFF2D2A4A;
            default: return 0xFF2A3560;
        }
    }
}
