package com.gathin.tvhomephone.sports;

import com.gathin.tvhomephone.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Live sport on British TV, from the public schedule behind Sky's TV guide (as Channels reads it for football): every
 * "Live …" listing on the sports channels and on BBC One and Two, ITV1 and ITV4, Channel 4 and 5, its sport worked out
 * from its title (tennis, cricket, darts, golf, the NFL, F1…), one event shown on several channels kept once with all of
 * them, the channel of its own sport first. For Sports' "On TV" list, and to put a channel's logo on each sport's events.
 * Off the main thread; each day read again after half an hour.
 */
public final class OnTv {
    private OnTv() {}

    private static final String SCHEDULE = "https://awk.epgsky.com/hawk/linear/schedule/%s/%s";
    private static final long FRESH_MS = 30 * 60_000L;
    /** Sky's schedule answers for this many channels at a time (more is refused). */
    private static final int BATCH = 11;

    /** {Sky guide service id, name}: each sport's own channel first, then the rest, as one event on several is ordered. */
    static final String[][] CHANNELS = {
            {"3835", "Sky Sports F1"}, {"1284", "Sky Sports Tennis"}, {"4081", "Sky Sports Cricket"}, {"4026", "Sky Sports Golf"},
            {"4022", "Sky Sports NFL"}, {"4002", "Sky Sports Main Event"}, {"4010", "Sky Sports Premier League"},
            {"3939", "Sky Sports Football"}, {"3940", "Sky Sports+"}, {"4090", "Sky Sports Mix"}, {"3625", "TNT Sports 1"},
            {"3627", "TNT Sports 2"}, {"3629", "TNT Sports 3"}, {"4040", "TNT Sports 4"}, {"1289", "Premier Sports 1"},
            {"1290", "Premier Sports 2"}, {"3007", "BBC One"}, {"2075", "BBC Two"}, {"6504", "ITV1"}, {"6534", "ITV4"},
            {"4075", "Channel 4"}, {"4058", "5"}};

    /** The sports, as keys. */
    public static final String F1 = "f1", TENNIS = "tennis", CRICKET = "cricket", DARTS = "darts", SNOOKER = "snooker",
            GOLF = "golf", NFL = "nfl", NBA = "nba", NHL = "nhl", MLB = "mlb", UFC = "ufc", BOXING = "boxing", RUGBY = "rugby",
            MOTOR = "motorsport", RACING = "racing", CYCLING = "cycling", FOOTBALL = "football";

    /** Each sport by the words its listings use, in this order (UFC's "Fight Night" before boxing's, F1 before racing). */
    private static final Object[][] SPORTS = {
            {UFC, Pattern.compile("(?i)\\bUFC\\b|\\bMMA\\b|\\bPFL\\b")},
            {BOXING, Pattern.compile("(?i)boxing|fight night|\\bboxe\\b")},
            {F1, Pattern.compile("(?i)\\bF1\\b|formula (1|one)\\b|grand prix|\\bGP\\b")},
            {TENNIS, Pattern.compile("(?i)tennis|\\bATP\\b|\\bWTA\\b|wimbledon|roland[- ]garros|davis cup|billie jean king|laver cup|united cup")},
            {CRICKET, Pattern.compile("(?i)cricket|\\btest\\b|\\bODI\\b|\\bT20I?\\b|\\bT10\\b|\\bW?BBL\\b|\\bIPL\\b|\\bashes\\b|the hundred")},
            {DARTS, Pattern.compile("(?i)darts")},
            {SNOOKER, Pattern.compile("(?i)snooker")},
            {GOLF, Pattern.compile("(?i)golf|\\bL?PGA\\b|dp world|ryder cup|solheim|\\bW?TGL\\b|\\bLIV\\b")},
            {NFL, Pattern.compile("(?i)\\bNFL\\b")},
            {NBA, Pattern.compile("(?i)\\bNBA\\b")},
            {NHL, Pattern.compile("(?i)\\bNHL\\b")},
            {MLB, Pattern.compile("(?i)\\bMLB\\b")},
            {RUGBY, Pattern.compile("(?i)rugby|six nations|\\bURC\\b|champions cup|challenge cup|super league|\\bNRL\\b")},
            {MOTOR, Pattern.compile("(?i)moto ?gp|superbikes|indycar|nascar|formula e\\b|\\bBTCC\\b|\\bWRC\\b")},
            {RACING, Pattern.compile("(?i)racing|\\bthe derby\\b|grand national")},
            {CYCLING, Pattern.compile("(?i)cycling|tour de france|\\bgiro\\b|vuelta")},
            {FOOTBALL, Pattern.compile("(?i)football|premier league|\\bEFL\\b|\\bFA cup\\b|\\bUEFA\\b|champions league|europa"
                    + "|\\bWSL\\b|\\bSPFL\\b|\\bSNF\\b|\\bMNF\\b|\\sv\\s")},
    };

    /** A live listing that is no event: a magazine, a talk show, the news, highlights. */
    private static final Pattern NOT_EVENT = Pattern.compile("(?i)\\b(fantasy|insiders|show|countdown|review|highlights|"
            + "reaction|preview|debate|talk|magazine|news|inside|classic|rewind|weigh[- ]?ins?|press conference|draw|"
            + "studio|podcast|build-?up)\\b|sky sports fc|good morning|soccer (saturday|special)|transfer");
    private static final Pattern LIVE = Pattern.compile("(?i)^\\s*live\\b\\s*[:\\-–]?\\s*");

    /** One live listing: the event, its sport, when, and every channel it is on (its own sport's first). */
    public static final class Listing {
        public final String title, sport;
        public final long start, end;
        public final List<String> channels = new ArrayList<>();

        public Listing(String title, String sport, long start, long end) {
            this.title = title;
            this.sport = sport;
            this.start = start;
            this.end = end;
        }

        /** On at this moment. */
        public boolean on(long now) {
            return start <= now && now < end;
        }

        /** The channel to show it by (its own sport's, the first). */
        public String channel() {
            return channels.isEmpty() ? null : channels.get(0);
        }
    }

    private static final Map<String, List<Listing>> DAYS = new HashMap<>();
    private static final Map<String, Long> READ_AT = new HashMap<>();

    /**
     * Off the main thread: the live sport listed on these (British) days, each event once, by its start. What was read
     * last stays when Sky's guide does not answer.
     */
    public static synchronized List<Listing> days(LocalDate... days) {
        List<Listing> all = new ArrayList<>();
        for (LocalDate d : days) all.addAll(day(d));
        return merge(all);
    }

    private static List<Listing> day(LocalDate d) {
        String date = d.format(DateTimeFormatter.BASIC_ISO_DATE);
        long now = System.currentTimeMillis();
        Long at = READ_AT.get(date);
        if (at != null && now - at < FRESH_MS) return DAYS.get(date);
        List<Listing> out = new ArrayList<>();
        boolean any = false;
        for (int i = 0; i < CHANNELS.length; i += BATCH) {
            StringBuilder ids = new StringBuilder();
            for (int k = i; k < Math.min(CHANNELS.length, i + BATCH); k++) {
                if (ids.length() > 0) ids.append(',');
                ids.append(CHANNELS[k][0]);
            }
            try {
                out.addAll(parse(new JSONObject(Net.get(String.format(SCHEDULE, date, ids)))));
                any = true;
            } catch (Exception ignored) {
                // that part of the guide this time
            }
        }
        if (any) {
            DAYS.put(date, out);
            READ_AT.put(date, now);
            return out;
        }
        List<Listing> had = DAYS.get(date);
        return had == null ? out : had;
    }

    // ---- pure -------------------------------------------------------------------------------------------------------

    /** Pure: Sky's schedule answer as live listings, one per channel, the sport known (anything else left out). */
    static List<Listing> parse(JSONObject root) {
        List<Listing> out = new ArrayList<>();
        JSONArray schedule = root == null ? null : root.optJSONArray("schedule");
        for (int i = 0; schedule != null && i < schedule.length(); i++) {
            JSONObject s = schedule.optJSONObject(i);
            if (s == null) continue;
            String channel = name(s.optString("sid"));
            if (channel == null) continue;
            JSONArray ev = s.optJSONArray("events");
            for (int k = 0; ev != null && k < ev.length(); k++) {
                JSONObject e = ev.optJSONObject(k);
                if (e == null) continue;
                String raw = e.optString("t", "");
                if (!LIVE.matcher(raw).lookingAt()) continue;
                String title = clean(raw);
                String sport = sportOf(title);
                if (sport == null || title.isEmpty()) continue;
                long st = e.optLong("st") * 1000L;
                Listing l = new Listing(title, sport, st, st + e.optLong("d") * 1000L);
                l.channels.add(channel);
                out.add(l);
            }
        }
        return out;
    }

    /** Pure: a channel's name by its Sky guide id, or null. */
    static String name(String sid) {
        for (String[] c : CHANNELS) if (c[0].equals(sid)) return c[1];
        return null;
    }

    /** Pure: "Live: NFL on 5: 49ers @ Cowboys" as "NFL on 5: 49ers @ Cowboys"; "Finals:Medvedev" as "Finals: Medvedev". */
    static String clean(String title) {
        if (title == null) return "";
        String t = LIVE.matcher(title).replaceFirst("");
        t = t.replaceAll(":(?=[^\\s\\d])", ": ").replaceAll("\\s+", " ").trim();
        return t;
    }

    /** Pure: the sport a listing's title says (a key above), or null for none (or for no event: a show, the news). */
    static String sportOf(String title) {
        if (title == null || title.trim().isEmpty() || NOT_EVENT.matcher(title).find()) return null;
        for (Object[] s : SPORTS) if (((Pattern) s[1]).matcher(title).find()) return (String) s[0];
        return null;
    }

    /** Pure: one listing per event (the same title at the same time), its channels in the guide's order, by start. */
    static List<Listing> merge(List<Listing> all) {
        Map<String, Listing> byKey = new LinkedHashMap<>();
        for (Listing l : all) {
            String key = norm(l.title) + "|" + l.start / 60_000L;
            Listing had = byKey.get(key);
            if (had == null) {
                had = new Listing(l.title, l.sport, l.start, l.end);
                byKey.put(key, had);
            }
            for (String c : l.channels) if (!had.channels.contains(c)) had.channels.add(c);
        }
        List<Listing> out = new ArrayList<>(byKey.values());
        for (Listing l : out) l.channels.sort((a, b) -> Integer.compare(rank(a), rank(b)));
        out.sort((a, b) -> a.start != b.start ? Long.compare(a.start, b.start) : a.title.compareTo(b.title));
        return out;
    }

    private static int rank(String channel) {
        for (int i = 0; i < CHANNELS.length; i++) if (CHANNELS[i][1].equals(channel)) return i;
        return CHANNELS.length;
    }

    /**
     * Pure: the listing of this sport (any, for null) that is on at the moment `at` (or starts within slackMs of it) and
     * whose title has every one of the words (in any case, accents aside); null for none.
     */
    public static Listing find(List<Listing> all, String sport, long at, long slackMs, String... words) {
        if (all == null) return null;
        Listing best = null;
        for (Listing l : all) {
            if (sport != null && !sport.equals(l.sport)) continue;
            boolean when = at <= 0 || (l.start - slackMs <= at && at < Math.max(l.end, l.start + slackMs));
            if (!when) continue;
            String t = " " + norm(l.title) + " ";
            boolean every = true;
            for (String w : words) {
                String n = norm(w);
                if (!n.isEmpty() && !t.contains(" " + n + " ") && !t.contains(n)) {
                    every = false;
                    break;
                }
            }
            if (!every) continue;
            if (best == null || Math.abs(l.start - at) < Math.abs(best.start - at)) best = l;
        }
        return best;
    }

    /** Pure: a sport's name as the page heads it. */
    public static String sportName(String sport) {
        switch (sport == null ? "" : sport) {
            case F1: return "Formula 1";
            case TENNIS: return "Tennis";
            case CRICKET: return "Cricket";
            case DARTS: return "Darts";
            case SNOOKER: return "Snooker";
            case GOLF: return "Golf";
            case NFL: return "NFL";
            case NBA: return "NBA";
            case NHL: return "NHL";
            case MLB: return "MLB";
            case UFC: return "UFC";
            case BOXING: return "Boxing";
            case RUGBY: return "Rugby";
            case MOTOR: return "Motorsport";
            case RACING: return "Horse racing";
            case CYCLING: return "Cycling";
            case FOOTBALL: return "Football";
            default: return "Sport";
        }
    }

    /** Pure: the logo for a sport's listing where its league has one (the NFL's, the NBA's, F1's, UFC's…), else null. */
    public static String logo(String sport, String title) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        switch (sport == null ? "" : sport) {
            case NFL: return LEAGUES + "nfl.png";
            case NBA: return LEAGUES + "nba.png";
            case NHL: return LEAGUES + "nhl.png";
            case MLB: return LEAGUES + "mlb.png";
            case UFC: return LEAGUES + "ufc.png";
            case F1: return t.contains("academy") ? null : Competitions.F1_LOGO;
            case GOLF:
                if (t.contains("dp world")) return MoreSports.DP_WORLD_LOGO;
                if (t.contains("pga tour") && !t.contains("lpga")) return MoreSports.PGA_LOGO;
                return null;
            default: return null;
        }
    }

    /** A sport's emoji, where it has no logo. */
    public static String emoji(String sport) {
        switch (sport == null ? "" : sport) {
            case F1: case MOTOR: return "🏎️";
            case TENNIS: return "🎾";
            case CRICKET: return "🏏";
            case DARTS: return "🎯";
            case SNOOKER: return "🎱";
            case GOLF: return "⛳";
            case NFL: return "🏈";
            case NBA: return "🏀";
            case NHL: return "🏒";
            case MLB: return "⚾";
            case UFC: case BOXING: return "🥊";
            case RUGBY: return "🏉";
            case RACING: return "🏇";
            case CYCLING: return "🚴";
            default: return "⚽";
        }
    }

    static final String LEAGUES = "https://a.espncdn.com/i/teamlogos/leagues/500/";

    /** Pure: lower case, accents off, anything but letters and digits as one space. */
    static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim();
    }
}
