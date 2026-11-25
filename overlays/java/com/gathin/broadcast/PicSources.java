package com.gathin.broadcast;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a programme's picture comes from, the same order in Homefield TV and the phone app (the family's "preferably I'd
 * want every channel to have a programme background"), pure:
 *
 * <pre>
 *   1 EPG        the guide's own picture of the programme (XMLTV {@code <icon>} or {@code <image>}, the provider's)
 *   2 CATALOGUES the programme found by its channel and time in the UK guide catalogues (PicFinder, Catalogues): Sky's
 *                episode picture, Freeview's, Virgin Media's, then Sky's season or series picture
 *   3 EVENT      a game's own picture (TheSportsDB)
 *   4 TITLE      by its title: TVMaze's episode, TMDB's episode still, then the series' (PicQuery: matched with care)
 *   5 FIXTURE    the last resorts, drawn: a game's two clubs' crests (ESPN's, as the scores have them) on their colours
 *   6 GENRE      a picture by what the programme is (football, news, film, kids...), drawn in code (PicArt)
 *   7 CHANNEL    the channel's own: its logo on its colour (each app's own), else BRAND, its name's colour
 * </pre>
 *
 * A programme's picture is a chain: the addresses found, best first, then one that is always there (a fixture, a genre,
 * the channel's), kept as one address (chain()), so the screens and the caches see one picture. Each place takes the
 * first in the chain that is large enough for it (PicPlace.tooSmall: never enlarged past 1.5 times) and is no
 * placeholder (PicVet); the made-up ones ("fixture:", "genre:", "brand:") are drawn at any size (PicArt). Nothing here
 * fetches anything (PicFinder does, through PicFetch).
 */
public final class PicSources {
    private PicSources() {}

    /** The order, for the tests and the READMEs. */
    public static final String EPG = "epg", CATALOGUES = "catalogues", EVENT = "event", TITLE = "title", FIXTURE = "fixture",
            GENRE = "genre", CHANNEL = "channel";
    public static final List<String> ORDER = java.util.Arrays.asList(EPG, CATALOGUES, EVENT, TITLE, FIXTURE, GENRE, CHANNEL);

    /** The made-up pictures' addresses. */
    public static final String FIXTURE_AT = "fixture:", GENRE_AT = "genre:", BRAND_AT = "brand:";
    /** Between a chain's addresses, and between a made-up picture's fields: characters no address has. */
    static final char NEXT = '\u001E', FIELD = '\u001F';

    // ---- chains -----------------------------------------------------------------------------------------------------

    /** Pure: the addresses (null and "" left out, the same one once) as one, best first; "" for none. */
    public static String chain(String... urls) {
        Set<String> seen = new LinkedHashSet<>();
        if (urls != null) {
            for (String u : urls) {
                if (u == null || u.isEmpty()) continue;
                for (String p : parts(u)) if (!p.isEmpty()) seen.add(p);
            }
        }
        StringBuilder b = new StringBuilder();
        for (String s : seen) {
            if (b.length() > 0) b.append(NEXT);
            b.append(s);
        }
        return b.toString();
    }

    /** Pure: a chain's addresses in order (one for a plain address, none for ""). */
    public static List<String> parts(String chain) {
        List<String> out = new ArrayList<>(3);
        if (chain == null || chain.isEmpty()) return out;
        int from = 0;
        while (true) {
            int at = chain.indexOf(NEXT, from);
            String p = (at < 0 ? chain.substring(from) : chain.substring(from, at)).trim();
            if (!p.isEmpty()) out.add(p);
            if (at < 0) break;
            from = at + 1;
        }
        return out;
    }

    /** Whether an address is a chain of more than one. */
    public static boolean isChain(String url) {
        return url != null && url.indexOf(NEXT) >= 0;
    }

    /** Whether an address is one of the made-up pictures (drawn here, never fetched). */
    public static boolean madeUp(String url) {
        return url != null && (url.startsWith(FIXTURE_AT) || url.startsWith(GENRE_AT) || url.startsWith(BRAND_AT));
    }

    /** Pure: the first address of a chain that names a real picture (fetched), else "". */
    public static String firstReal(String chain) {
        for (String p : parts(chain)) if (!madeUp(p) && (p.startsWith("http") || p.startsWith("file:"))) return p;
        return "";
    }

    // ---- the made-up pictures ----------------------------------------------------------------------------------------

    /** A game's picture: the home and away clubs' crests (their addresses, "" for none known) on their colours. */
    public static String fixture(String homeCrest, String awayCrest, int homeColour, int awayColour) {
        return FIXTURE_AT + hex(homeColour) + FIELD + hex(awayColour) + FIELD + clean(homeCrest) + FIELD + clean(awayCrest);
    }

    /** Pure: a fixture address's fields {home colour, away colour} and crests {home, away}; null for another address. */
    public static int[] fixtureColours(String url) {
        String[] f = fields(url, FIXTURE_AT);
        if (f == null) return null;
        return new int[]{colour(f[0]), colour(f[1])};
    }

    public static String[] fixtureCrests(String url) {
        String[] f = fields(url, FIXTURE_AT);
        if (f == null) return null;
        return new String[]{f.length > 2 ? f[2] : "", f.length > 3 ? f[3] : ""};
    }

    /** A picture by what the programme is (one of KINDS). */
    public static String genre(String kind) {
        return GENRE_AT + kind;
    }

    /** Pure: a genre address's kind, else null. */
    public static String genreOf(String url) {
        return url != null && url.startsWith(GENRE_AT) ? url.substring(GENRE_AT.length()) : null;
    }

    /** The channel's own ground with no logo to show: its name's colour. */
    public static String brand(int colour) {
        return BRAND_AT + hex(colour);
    }

    /** Pure: a brand address's colour, else 0. */
    public static int brandColour(String url) {
        return url != null && url.startsWith(BRAND_AT) ? colour(url.substring(BRAND_AT.length())) : 0;
    }

    /** Pure: a deep, distinct colour for a channel by its name (the same name always the same). */
    public static int nameColour(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        int h = 0;
        for (int i = 0; i < n.length(); i++) h = h * 31 + n.charAt(i);
        return BRANDS[Math.floorMod(h, BRANDS.length)];
    }

    private static final int[] BRANDS = {0xFF1B4F8C, 0xFF7A1F3D, 0xFF1F6B52, 0xFF6A3E9C, 0xFF8C4A12, 0xFF0F6676, 0xFF8A1E1E,
            0xFF3C5A14, 0xFF4A2F7A, 0xFF125E8A};

    private static String[] fields(String url, String head) {
        if (url == null || !url.startsWith(head)) return null;
        List<String> out = new ArrayList<>(4);
        String s = url.substring(head.length());
        int from = 0;
        while (true) {
            int at = s.indexOf(FIELD, from);
            out.add(at < 0 ? s.substring(from) : s.substring(from, at));
            if (at < 0) break;
            from = at + 1;
        }
        return out.toArray(new String[0]);
    }

    private static String hex(int c) {
        return String.format(Locale.ROOT, "%08X", c);
    }

    private static int colour(String s) {
        try {
            return (int) Long.parseLong(s.trim(), 16);
        } catch (RuntimeException e) {
            return 0xFF333A44;
        }
    }

    private static String clean(String u) {
        if (u == null) return "";
        StringBuilder b = new StringBuilder(u.length());
        for (int i = 0; i < u.length(); i++) {
            char c = u.charAt(i);
            if (c != NEXT && c != FIELD && c >= ' ') b.append(c);
        }
        return b.toString().trim();
    }

    // ---- a game ------------------------------------------------------------------------------------------------------

    /** ESPN's crest of a club by its id (the scores' own pictures). */
    public static String crest(String espnId) {
        return espnId == null || espnId.isEmpty() ? "" : "https://a.espncdn.com/i/teamlogos/soccer/500/" + espnId + ".png";
    }

    private static final Pattern VERSUS = Pattern.compile("(?i)\\s+(?:v|vs\\.?|versus)\\s+");
    private static final Pattern LEAD_LIVE = Pattern.compile("(?i)^\\s*(?:live|new|replay|highlights|extended highlights|full match)\\b\\s*[:\\-–]?\\s*");
    private static final Pattern TAIL = Pattern.compile("(?i)\\s*(?:\\(.*\\)|\\[.*\\]|\\s[-–:|]\\s.*|\\s+live|\\s+highlights)\\s*$");

    /**
     * Pure: a game's two sides from its title ("Live: Arsenal v Chelsea", "Premier League: Arsenal v Chelsea", "Arsenal
     * vs Chelsea (Live)"), else null.
     */
    public static String[] sides(String title) {
        if (title == null) return null;
        Matcher v = VERSUS.matcher(title);
        if (!v.find()) return null;
        String left = title.substring(0, v.start()), right = title.substring(v.end());
        if (VERSUS.matcher(right).find()) return null;
        int colon = Math.max(left.lastIndexOf(':'), Math.max(left.lastIndexOf(" - "), left.lastIndexOf(" – ")));
        if (colon >= 0) left = left.substring(colon + 1);
        left = LEAD_LIVE.matcher(left.trim()).replaceFirst("").trim();
        for (int i = 0; i < 3; i++) right = TAIL.matcher(right).replaceFirst("");
        right = right.trim();
        if (!side(left) || !side(right)) return null;
        return new String[]{left, right};
    }

    private static boolean side(String s) {
        if (s.length() < 2 || s.length() > 40) return false;
        int words = s.trim().split("\\s+").length;
        return words <= 5 && Character.isLetter(s.codePointAt(0));
    }

    /**
     * Pure: a game's picture from its title, when it names a game (sides()) of clubs the apps know (ClubGoals: their
     * crests and colours): both known, or one in a sporting context (a sports channel or a sporting category); else null.
     */
    public static String fixtureOf(String title, boolean sporting) {
        String[] s = sides(title);
        if (s == null) return null;
        ClubGoals.Club h = ClubGoals.find(null, s[0]), a = ClubGoals.find(null, s[1]);
        if (h == null && a == null || (h == null || a == null) && !sporting) return null;
        int hc = h != null ? h.sweep0 : 0xFF3A4150, ac = a != null ? a.sweep0 : 0xFF3A4150;
        if (h != null && a != null && near(hc, ac)) ac = a.sweep1;
        return fixture(h != null ? crest(h.id) : "", a != null ? crest(a.id) : "", hc, ac);
    }

    private static boolean near(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db < 60 * 60;
    }

    // ---- what a programme is -----------------------------------------------------------------------------------------

    public static final String FOOTBALL = "football", MOTOR = "motor", TENNIS = "tennis", CRICKET = "cricket", GOLF = "golf",
            RUGBY = "rugby", FIGHT = "fight", DARTS = "darts", SNOOKER = "snooker", SPORT = "sport", NEWS = "news",
            WEATHER = "weather", FILM = "film", KIDS = "kids", NATURE = "nature", MUSIC = "music", FOOD = "food", STAGE = "stage";
    /** Every kind a genre picture is drawn for. */
    public static final List<String> KINDS = java.util.Arrays.asList(FOOTBALL, MOTOR, TENNIS, CRICKET, GOLF, RUGBY, FIGHT, DARTS,
            SNOOKER, SPORT, NEWS, WEATHER, FILM, KIDS, NATURE, MUSIC, FOOD, STAGE);

    private static Pattern words(String list) {
        return Pattern.compile("(?:^|[^a-z0-9])(?:" + list + ")(?:$|[^a-z0-9])");
    }

    private static final Object[][] SPORTS = {
            {MOTOR, words("f1|formula (?:1|one|2|3|e)|grand prix|motogp|indycar|nascar|superbikes?|btcc|rally|le mans|motorsports?|speedway")},
            {TENNIS, words("tennis|wimbledon|atp|wta|roland garros|davis cup|billie jean king cup")},
            {CRICKET, words("cricket|test match|odi|t20|the hundred|ashes|ipl")},
            {GOLF, words("golf|pga|ryder cup|the masters|open championship|lpga|dp world tour|solheim cup")},
            {RUGBY, words("rugby|six nations|super league|premiership rugby|urc|nrl|challenge cup")},
            {FIGHT, words("boxing|ufc|mma|wrestling|wwe|aew|fight night|kickboxing|bellator|smackdown")},
            {DARTS, words("darts|pdc|premier league darts")},
            {SNOOKER, words("snooker|crucible")},
            {FOOTBALL, words("football|soccer|premier league|champions league|europa league|conference league|fa cup|efl|"
                    + "carabao cup|la ?liga|serie a|bundesliga|ligue 1|spfl|mls|match of the day|motd|soccer saturday|"
                    + "super sunday|monday night football|the football show|copa del rey")},
            {SPORT, words("racing|athletics|cycling|tour de france|horse racing|nfl|nba|mlb|nhl|basketball|baseball|ice hockey|"
                    + "hockey|netball|swimming|olympics?|paralympics?|sports?|squash|badminton|volleyball|handball|triathlon|"
                    + "marathon|skiing|winter games|equestrian|bowls|gaa|hurling")}};
    private static final Pattern FOOTBALL_TOO = words("goals|goal of the month|world cup|euro 20\\d\\d|the euros|transfer deadline");
    private static final Pattern NEWS_W = words("news|headlines|bulletin|newsnight|newsday|newsroom|breakfast|politics|"
            + "question time|press preview|the papers|briefing"), WEATHER_W = words("weather|forecast"),
            FILM_W = words("film|movie|movies|feature film|cinema"), KIDS_W = words("children|childrens|kids|animation|animated|"
            + "cartoon|cartoons|preschool|pre-school|family"), NATURE_W = words("documentary|documentaries|nature|wildlife|"
            + "history|science|natural world|planet|animals?|travel"), MUSIC_W = words("music|concert|gig|hits|chart|charts|"
            + "videos|live lounge|proms|festival|unplugged|karaoke"), FOOD_W = words("cooking|cookery|food|kitchen|kitchens|bake|"
            + "baking|chef|chefs|masterchef|recipes?|menu"), STAGE_W = words("comedy|sitcom|stand-up|standup|entertainment|"
            + "game show|gameshow|quiz|talk show|chat show|panel show|variety|talent|reality|soap");
    private static final Pattern SPORT_CHANNEL = words("sport|sports|espn|eurosport|tnt sports|premier sports|laliga|dazn|"
            + "racing|golf|f1|nba|nfl|mutv|lfctv|chelsea tv|boxnation|bein|arena|setanta|viaplay sports?|free sports"),
            NEWS_CHANNEL = words("news|cnn|bbc world|sky news|gb news|al jazeera|bloomberg|cnbc|euronews|france 24|dw|rt|"
                    + "nhk world|trt world"),
            FILM_CHANNEL = words("movies?|cinema|films?|film4|premiere|hollywood|tcm|horror|thriller|western"),
            KIDS_CHANNEL = words("kids|junior|jr|cbeebies|cbbc|nick|nickelodeon|nicktoons|cartoon|cartoonito|disney|boomerang|"
                    + "baby|children|toons|ketchup|tiny pop|pop max|pop|milkshake"),
            NATURE_CHANNEL = words("documentar(?:y|ies)|discovery|nat geo|national geographic|history|animal planet|"
                    + "eden|blaze|science|smithsonian|earth|wild|natgeo"),
            MUSIC_CHANNEL = words("music|mtv|kiss|kerrang|magic|hits|vh1|now 80s|now 90s|now rock|clubland|box|capital|heart|"
                    + "radio|4music|trace|vevo"),
            FOOD_CHANNEL = words("food|cooking|tastemade");

    /**
     * Pure: what a programme is for its genre picture (one of KINDS), from its channel (name, playlist group), its title,
     * its description and the guide's categories (as given, "" for none); null when it can't be told (the channel's own
     * picture then). A sport by its own words first, then the guide's category, then the channel's kind.
     */
    public static String kindOf(String channel, String group, String title, String desc, String category) {
        String ch = low(channel) + " | " + low(group), t = low(title), cat = low(category), d = low(desc);
        boolean sportChannel = SPORT_CHANNEL.matcher(ch).find(), sportCat = cat.contains("sport") || cat.contains("football");
        // A sport by name: in the title or category; the description only on a sports channel or a sporting category.
        for (Object[] s : SPORTS) {
            Pattern p = (Pattern) s[1];
            if (s[0].equals(SPORT) && !(sportChannel || sportCat)) continue;
            if (p.matcher(t).find() || p.matcher(cat).find()) return (String) s[0];
        }
        if (sportChannel || sportCat) {
            // Words that mean football only where it is sport ("Goals", "World Cup"), then the description's.
            if (FOOTBALL_TOO.matcher(t).find()) return FOOTBALL;
            for (Object[] s : SPORTS) if (((Pattern) s[1]).matcher(d).find()) return (String) s[0];
            if (sides(title) != null && (ClubGoals.find(null, sides(title)[0]) != null || ClubGoals.find(null, sides(title)[1]) != null)) {
                return FOOTBALL;
            }
            if (sportChannel && ch.contains("football")) return FOOTBALL;
            if (sportChannel && (ch.contains("golf"))) return GOLF;
            if (sportChannel && (ch.contains("f1") || ch.contains("racing"))) return MOTOR;
            return SPORT;
        }
        // The guide's own category, then the title's words.
        String byCat = byWords(cat);
        if (byCat != null) return byCat;
        if (WEATHER_W.matcher(t).find()) return WEATHER;
        if (NEWS_CHANNEL.matcher(ch).find()) return NEWS;
        if (NEWS_W.matcher(t).find()) return NEWS;
        if (FILM_W.matcher(t).find() && t.matches("^\\s*(?:film|movie|premiere)\\b.*")) return FILM;
        if (KIDS_CHANNEL.matcher(ch).find()) return KIDS;
        if (FILM_CHANNEL.matcher(ch).find()) return FILM;
        if (MUSIC_CHANNEL.matcher(ch).find()) return MUSIC;
        if (FOOD_CHANNEL.matcher(ch).find() || FOOD_W.matcher(t).find()) return FOOD;
        if (NATURE_CHANNEL.matcher(ch).find()) return NATURE;
        String byDesc = d.isEmpty() ? null : byWords(d.length() > 160 ? d.substring(0, 160) : d);
        if (FILM.equals(byDesc) || NATURE.equals(byDesc) || FOOD.equals(byDesc)) return byDesc;
        return null;
    }

    /** Pure: a kind from words that say it outright (a guide's category, the start of a description); null for none. */
    static String byWords(String s) {
        if (s.isEmpty()) return null;
        if (WEATHER_W.matcher(s).find()) return WEATHER;
        if (NEWS_W.matcher(s).find()) return NEWS;
        if (KIDS_W.matcher(s).find()) return KIDS;
        if (FILM_W.matcher(s).find()) return FILM;
        if (FOOD_W.matcher(s).find()) return FOOD;
        if (MUSIC_W.matcher(s).find()) return MUSIC;
        if (NATURE_W.matcher(s).find()) return NATURE;
        if (STAGE_W.matcher(s).find()) return STAGE;
        return null;
    }

    /** Pure: whether a programme is sport (its kind is one of the sports). */
    public static boolean sporting(String kind) {
        if (kind == null) return false;
        for (Object[] s : SPORTS) if (s[0].equals(kind)) return true;
        return false;
    }

    /** Pure: a guide category's kind as a small number (0 none), so a programme keeps it in a byte. */
    public static byte categoryCode(String category) {
        String k = category == null || category.isEmpty() ? null : kindOf("", "", "", "", category);
        return (byte) (k == null ? 0 : KINDS.indexOf(k) + 1);
    }

    /** Pure: the category word for a code from categoryCode ("" for 0), to give back to kindOf. */
    public static String categoryWord(byte code) {
        int i = code - 1;
        return i < 0 || i >= KINDS.size() ? "" : KINDS.get(i);
    }

    private static String low(String s) {
        if (s == null || s.isEmpty()) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
