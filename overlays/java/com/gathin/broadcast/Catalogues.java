package com.gathin.broadcast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The UK TV guide catalogues the programme pictures are looked for in, read the same way by both apps, pure (PicFinder
 * fetches): each one's listings made small (Listing: starts, ends, titles, a picture each), a playlist's channel found
 * in each one's own list (channelKey: HD, UK, regions and +1 tidied, a hand-kept table for the big UK channels' other
 * names), and the programme found in a listing by its time and its title together (match: never another programme in
 * the same slot), then each one's picture of it.
 *
 * <pre>
 *   Freeview       its public guide's day (the one freeview.co.uk's own guide page reads): every Freeview channel's
 *                  programmes, each with the broadcaster's own picture of it (img.freeviewplay.tv, 800 wide)
 *   Virgin Media   its public guide's six hours (the TV Go web listings): every channel it carries, pay channels too
 *                  (Sky Sports, TNT Sports, Sky Cinema, Gold, Discovery), each programme's picture by its id (16:9,
 *                  1280 wide; the series' where the episode has none)
 *   TVMaze         a show by its name (its poster) and its episode that day (a still)
 *   TheSportsDB    a game by its two sides (its own 16:9 thumbnail), on its day only
 * </pre>
 */
public final class Catalogues {
    private Catalogues() {}

    public static final String FREEVIEW = "https://www.freeview.co.uk/api/tv-guide?nid=64257&start=%d";
    public static final String VIRGIN_CHANNELS = "https://spark-prod-gb.gnp.cloud.virgintvgo.virginmedia.com/eng/web/linear-service/v2/channels"
            + "?cityId=1&language=en&productClass=Orion-DASH";
    public static final String VIRGIN_SEGMENT = "https://staticqbr-prod-gb.gnp.cloud.virgintvgo.virginmedia.com/eng/web/epg-service-lite/gb/en"
            + "/events/segments/%s";
    public static final String VIRGIN_IMAGE = "https://staticqbr-prod-gb.gnp.cloud.virgintvgo.virginmedia.com/image-service/intent/%s/"
            + "detailedBackground?w=%d";
    public static final String TVMAZE_SHOW = "https://api.tvmaze.com/singlesearch/shows?q=%s";
    public static final String TVMAZE_DAY = "https://api.tvmaze.com/shows/%d/episodesbydate?date=%s";
    /** TheSportsDB's free public test key ("123"), as its own documentation gives it. */
    public static final String SPORTSDB_EVENTS = "https://www.thesportsdb.com/api/v1/json/123/searchevents.php?e=%s";
    /** Freeview's pictures at the width its own guide asks for (a width it doesn't serve is refused). */
    public static final int FREEVIEW_WIDTH = 800;

    /** One channel's programmes in a catalogue, small: start and end (ms), title, and its picture (address or id). */
    public static final class Listing {
        public final long[] start, end;
        public final String[] title, pic;

        public Listing(long[] start, long[] end, String[] title, String[] pic) {
            this.start = start;
            this.end = end;
            this.title = title;
            this.pic = pic;
        }

        public int size() {
            return start.length;
        }
    }

    private static final class Grow {
        final List<long[]> times = new ArrayList<>();
        final List<String[]> words = new ArrayList<>();

        void add(long a, long b, String t, String p) {
            times.add(new long[]{a, b});
            words.add(new String[]{t == null ? "" : t, p == null ? "" : p});
        }

        Listing done() {
            int n = times.size();
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            java.util.Arrays.sort(order, (x, y) -> Long.compare(times.get(x)[0], times.get(y)[0]));
            long[] s = new long[n], e = new long[n];
            String[] t = new String[n], p = new String[n];
            for (int i = 0; i < n; i++) {
                s[i] = times.get(order[i])[0];
                e[i] = times.get(order[i])[1];
                t[i] = words.get(order[i])[0];
                p[i] = words.get(order[i])[1];
            }
            return new Listing(s, e, t, p);
        }
    }

    // ---- Freeview ----------------------------------------------------------------------------------------------------

    /** Pure: Freeview's day (data.programs[]: service title, events with start, duration, title, picture) by its title. */
    public static Map<String, Listing> freeview(String json) {
        Map<String, Listing> out = new HashMap<>();
        try {
            JSONObject data = new JSONObject(json).optJSONObject("data");
            JSONArray ps = data == null ? null : data.optJSONArray("programs");
            for (int i = 0; ps != null && i < ps.length(); i++) {
                JSONObject p = ps.optJSONObject(i);
                JSONArray ev = p == null ? null : p.optJSONArray("events");
                if (ev == null) continue;
                Grow g = new Grow();
                for (int k = 0; k < ev.length(); k++) {
                    JSONObject e = ev.optJSONObject(k);
                    if (e == null) continue;
                    long a = isoTime(e.optString("start_time")), d = duration(e.optString("duration"));
                    if (a <= 0 || d <= 0) continue;
                    String img = e.optString("image_url", "");
                    g.add(a, a + d, e.optString("main_title"), img.startsWith("http") ? img + "?w=" + FREEVIEW_WIDTH : "");
                }
                out.put(p.optString("title"), g.done());
            }
        } catch (JSONException e) {
            // none
        }
        return out;
    }

    /** Pure: the start of a day (UTC) Freeview's guide is asked for, for a time. */
    public static long dayStart(long at) {
        return Math.floorDiv(at, 86_400_000L) * 86_400L;
    }

    // ---- Virgin Media -------------------------------------------------------------------------------------------------

    /** Pure: Virgin Media's channels (id, name; hidden ones left out) as name → id. */
    public static Map<String, String> virginChannels(String json) {
        Map<String, String> out = new HashMap<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject c = a.optJSONObject(i);
                if (c == null || c.optBoolean("isHidden") || c.optString("id").isEmpty()) continue;
                out.put(c.optString("name"), c.optString("id"));
            }
        } catch (JSONException e) {
            // none
        }
        return out;
    }

    /** Pure: the segment of six hours (UTC) a time is in, as Virgin Media names it ("20261125060000"). */
    public static String virginSegment(long at) {
        long s = Math.floorDiv(at, 21_600_000L) * 21_600_000L;
        java.time.LocalDateTime t = java.time.LocalDateTime.ofEpochSecond(s / 1000L, 0, java.time.ZoneOffset.UTC);
        return String.format(Locale.ROOT, "%04d%02d%02d%02d0000", t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour());
    }

    /**
     * Pure: a Virgin Media segment (entries[]: channelId, events with start and end seconds, title, id) by channel id,
     * each programme's picture its id (the part before ",imi").
     */
    public static Map<String, Listing> virgin(String json) {
        Map<String, Listing> out = new HashMap<>();
        try {
            JSONArray es = new JSONObject(json).optJSONArray("entries");
            for (int i = 0; es != null && i < es.length(); i++) {
                JSONObject en = es.optJSONObject(i);
                JSONArray ev = en == null ? null : en.optJSONArray("events");
                if (ev == null) continue;
                Grow g = new Grow();
                for (int k = 0; k < ev.length(); k++) {
                    JSONObject e = ev.optJSONObject(k);
                    if (e == null) continue;
                    long a = e.optLong("startTime") * 1000L, b = e.optLong("endTime") * 1000L;
                    String id = e.optString("id", "");
                    int cut = id.indexOf(",imi");
                    if (b <= a) continue;
                    g.add(a, b, e.optString("title"), cut > 0 ? id.substring(0, cut) : id);
                }
                out.put(en.optString("channelId"), g.done());
            }
        } catch (JSONException e) {
            // none
        }
        return out;
    }

    /** Virgin Media's picture of a programme by its id at a width (16:9; "" for none). */
    public static String virginImage(String id, int width) {
        return id == null || id.isEmpty() || !id.startsWith("crid:") ? "" : String.format(Locale.ROOT, VIRGIN_IMAGE, id, width);
    }

    // ---- TVMaze and TheSportsDB ---------------------------------------------------------------------------------------

    /** Pure: TVMaze's show for a title (singlesearch): {id, its picture} when it is the same programme, else null. */
    public static String[] tvmazeShow(String json, String title) {
        try {
            JSONObject o = new JSONObject(json);
            if (!PicQuery.same(o.optString("name"), title)) return null;
            JSONObject im = o.optJSONObject("image");
            return new String[]{String.valueOf(o.optInt("id")), im == null ? "" : im.optString("original", im.optString("medium", ""))};
        } catch (JSONException e) {
            return null;
        }
    }

    /** Pure: TVMaze's episode that day (episodesbydate) nearest a time (ms): its still, "" for none. */
    public static String tvmazeEpisode(String json, long at) {
        try {
            JSONArray a = new JSONArray(json);
            String best = "";
            long bestGap = Long.MAX_VALUE;
            for (int i = 0; i < a.length(); i++) {
                JSONObject e = a.optJSONObject(i);
                JSONObject im = e == null ? null : e.optJSONObject("image");
                if (im == null) continue;
                long t = isoTime(e.optString("airstamp"));
                long gap = t <= 0 ? Long.MAX_VALUE - 1 : Math.abs(t - at);
                if (gap < bestGap) {
                    bestGap = gap;
                    best = im.optString("original", im.optString("medium", ""));
                }
            }
            return best;
        } catch (JSONException e) {
            return "";
        }
    }

    /** Pure: TheSportsDB's search for a game ("Home_vs_Away"). */
    public static String sportsdbQuery(String home, String away) {
        return (home.trim() + "_vs_" + away.trim()).replace(' ', '_');
    }

    /**
     * Pure: a game's own picture from TheSportsDB's search answer: its 16:9 thumbnail, for the same two sides (either
     * way round, ClubGoals' names) on the programme's day (a day either side); "" for none.
     */
    public static String sportsdbThumb(String json, String home, String away, long at) {
        try {
            JSONArray a = new JSONObject(json).optJSONArray("event");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject e = a.optJSONObject(i);
                if (e == null || e.optString("strThumb", "").isEmpty() || "null".equals(e.optString("strThumb"))) continue;
                String h = e.optString("strHomeTeam"), w = e.optString("strAwayTeam");
                boolean same = side(h, home) && side(w, away) || side(h, away) && side(w, home);
                long t = isoTime(e.optString("strTimestamp") + (e.optString("strTimestamp").endsWith("Z") ? "" : "Z"));
                if (t <= 0) t = isoTime(e.optString("dateEvent") + "T12:00:00Z");
                if (same && t > 0 && Math.abs(t - at) <= 36 * 3600_000L) return e.optString("strThumb");
            }
        } catch (JSONException e) {
            // none
        }
        return "";
    }

    private static boolean side(String a, String b) {
        String x = ClubGoals.norm(a), y = ClubGoals.norm(b);
        return !x.isEmpty() && (x.equals(y) || x.contains(y) && y.length() >= 4 || y.contains(x) && x.length() >= 4);
    }

    // ---- channels ----------------------------------------------------------------------------------------------------

    private static final Pattern GLUED = Pattern.compile("(?<=[a-z0-9])(?:FHD|UHD|HD)\\b");
    private static final Pattern PLUS_ONE = Pattern.compile("(?i)\\s*(?:\\+\\s*1|plus\\s*1|\\+1h)\\s*$|\\s*\\+\\s*1\\b");
    private static final Pattern COUNTRY = Pattern.compile("(?i)^\\s*(?:\\[\\s*(?:uk|gb)\\s*\\]|(?:uk|gb|eng)\\s*[:|\\-]+)\\s*");
    private static final Pattern WORDS = Pattern.compile("[^a-z0-9]+");
    private static final java.util.Set<String> QUALITY = new java.util.HashSet<>(java.util.Arrays.asList("hd", "fhd", "uhd", "4k", "sd",
            "hevc", "raw", "uk", "gb", "h265", "50fps", "backup", "vip", "hq"));
    private static final java.util.Set<String> REGIONS = new java.util.HashSet<>(java.util.Arrays.asList("lon", "london", "nw", "northwest",
            "north", "west", "ne", "northeast", "east", "south", "southwest", "sw", "se", "southeast", "yorkshire", "yks", "scotland", "scot",
            "wales", "wal", "ni", "wm", "emid", "midlands", "cumbria", "border", "anglia", "granada", "meridian", "tyne", "tees",
            "channel", "islands", "ci", "y", "l", "and", "lincs", "oxford", "eng", "england", "wst", "sth"));
    /** Other names of the big UK channels: tidied name → the one every catalogue is matched by. */
    static final String[][] ALIASES = {
            {"channel5", "5"}, {"five", "5"}, {"channelfive", "5"}, {"bbc1", "bbcone"}, {"bbc2", "bbctwo"}, {"itv", "itv1"},
            {"skycinemapremiere", "skypremiere"}, {"skymoviespremiere", "skypremiere"}, {"skysportspl", "skysportspremierleague"},
            {"skysportsf1", "skysportsf1"}, {"skyspmainevent", "skysportsmainevent"}, {"skysportsmain", "skysportsmainevent"},
            {"btsport1", "tntsports1"}, {"btsport2", "tntsports2"}, {"btsport3", "tntsports3"}, {"tntsport1", "tntsports1"},
            {"tntsport2", "tntsports2"}, {"tntsport3", "tntsports3"}, {"tntsport4", "tntsports4"}, {"ugold", "gold"}, {"ukgold", "gold"},
            {"udave", "dave"}, {"ukdave", "dave"}, {"uw", "w"}, {"udrama", "drama"}, {"uyesterday", "yesterday"}, {"ualibi", "alibi"},
            {"ueden", "eden"}, {"bbcnewschannel", "bbcnews"}, {"bbcnews24", "bbcnews"}, {"skynewshd", "skynews"},
            {"discoverychannel", "discovery"}, {"discoveryuk", "discovery"}, {"channel4hd", "channel4"}, {"c4", "channel4"},
            {"e4uk", "e4"}, {"cbeebiesuk", "cbeebies"}, {"premiersports1", "premiersports1"}, {"laligatv", "laligatv"}};
    private static final Map<String, String> ALIAS = new HashMap<>();

    static {
        for (String[] a : ALIASES) ALIAS.put(a[0], a[1]);
    }

    /** Pure: whether a channel is another's an hour later ("ITV1 +1", "Channel 4+1"): the hours to shift back by. */
    public static int plusHours(String name) {
        return name != null && PLUS_ONE.matcher(name).find() ? 1 : 0;
    }

    /**
     * Pure: a channel's name as every catalogue is matched by ("UK: Sky Sports Main Event FHD", "Sky SportsMainEventHD"
     * and "SkySpMainEv" all "skysportsmainevent"; "BBC ONE London HD" "bbcone"; "U&GOLD HD" "gold"; "Channel 5" "5"):
     * its country tag, quality, region, "U&" and +1 left out, letters and figures only, then the hand-kept table.
     */
    public static String channelKey(String name) {
        if (name == null) return "";
        String s = SkyListing.skyName(name);
        s = GLUED.matcher(s).replaceAll(" HD");
        s = COUNTRY.matcher(s).replaceFirst("");
        s = PLUS_ONE.matcher(s).replaceAll(" ");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        if (s.startsWith("u&")) s = s.substring(2);
        s = s.replace("&", " and ");
        String[] w = WORDS.split(s.trim());
        StringBuilder b = new StringBuilder();
        // A region after BBC One's or BBC Two's name (its third word on) or ITV's (its second on).
        int from = s.startsWith("bbc one") || s.startsWith("bbc two") ? 2 : s.startsWith("itv") || s.startsWith("stv") ? 1 : Integer.MAX_VALUE;
        for (int i = 0; i < w.length; i++) {
            String t = w[i];
            if (t.isEmpty() || QUALITY.contains(t)) continue;
            if (i >= from && REGIONS.contains(t)) continue;
            b.append(t);
        }
        String k = b.toString();
        if (k.endsWith("hd") && k.length() > 4) k = k.substring(0, k.length() - 2);
        String a = ALIAS.get(k);
        return a != null ? a : k;
    }

    /**
     * Pure: a catalogue's channels (name → id) by channelKey; where several names give one key (BBC One's regions), the
     * North West's first (the family's), then London's, then the first.
     */
    public static Map<String, String> byKey(Map<String, String> nameToId) {
        Map<String, String> out = new HashMap<>();
        Map<String, Integer> rank = new HashMap<>();
        for (Map.Entry<String, String> e : nameToId.entrySet()) {
            String k = channelKey(e.getKey());
            if (k.isEmpty()) continue;
            String n = e.getKey().toLowerCase(Locale.ROOT);
            int r = n.contains("north west") || n.contains(" nw") ? 3 : n.contains("london") || n.contains(" lon") ? 2 : 1;
            if (n.contains("+1")) r -= 5;
            Integer had = rank.get(k);
            if (had == null || r > had) {
                out.put(k, e.getValue());
                rank.put(k, r);
            }
        }
        return out;
    }

    // ---- the programme in a listing ----------------------------------------------------------------------------------

    private static final Pattern LEAD = Pattern.compile("(?i)^\\s*(?:live|new|premiere|film|movie|brand new|tv premiere|"
            + "sky cinema premiere|repeat|catch up|exclusive|first on \\w+)\\s*[:\\-–]\\s*|^\\s*live\\s+(?=\\p{L})");
    private static final Pattern NOISE = Pattern.compile("(?i)\\(r\\)|\\[r\\]|\\[s\\]|\\[hd\\]|\\[ad\\]|\\[sl\\]|\\bhd\\b|\\(new series\\)|"
            + "\\(\\d{1,2}/\\d{1,2}\\)|\\b(?:s|series|season)\\s*\\d{1,2}\\s*[,:/-]?\\s*(?:e|ep|episode)\\s*\\d{1,3}\\b|"
            + "\\b(?:episode|ep)\\.?\\s*\\d{1,3}\\b|\\b(?:series|season)\\s+\\d{1,2}\\b|[\\[(](?:19|20)\\d{2}[\\])]");

    /** Pure: a programme's title as listings are compared ("Live: Test Cricket (R)" "testcricket", "The Chase S12 E4" "chase"). */
    public static String tidyTitle(String title) {
        if (title == null) return "";
        String t = title.trim();
        for (int i = 0; i < 2; i++) t = LEAD.matcher(t).replaceFirst("");
        t = NOISE.matcher(t).replaceAll(" ");
        return PicQuery.norm(t);
    }

    /** Pure: how alike two tidied titles are, 0 to 1 (letter pairs in common). */
    static float alike(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0f;
        if (a.equals(b)) return 1f;
        if (a.length() < 2 || b.length() < 2) return 0f;
        Map<String, Integer> pairs = new HashMap<>();
        for (int i = 0; i + 1 < a.length(); i++) pairs.merge(a.substring(i, i + 2), 1, Integer::sum);
        int common = 0;
        for (int i = 0; i + 1 < b.length(); i++) {
            Integer n = pairs.get(b.substring(i, i + 2));
            if (n != null && n > 0) {
                common++;
                pairs.put(b.substring(i, i + 2), n - 1);
            }
        }
        return 2f * common / (a.length() - 1 + b.length() - 1);
    }

    /** Pure: whether two titles name the same programme (tidied: the same, one the other's start, or very alike). */
    public static boolean sameTitle(String a, String b) {
        String x = tidyTitle(a), y = tidyTitle(b);
        if (x.length() < 2 || y.length() < 2) return false;
        if (x.equals(y)) return true;
        // A game: the same two sides, whatever else each title says ("Live: Arsenal v Chelsea", "Premier League: Arsenal vs Chelsea").
        String[] sa = PicSources.sides(a), sb = PicSources.sides(b);
        if (sa != null && sb != null && side(sa[0], sb[0]) && side(sa[1], sb[1])) return true;
        String s = x.length() <= y.length() ? x : y, l = s == x ? y : x;
        // "Test Cricket" and "Test Cricket: South Africa v Bangladesh": the shorter the longer's start, long enough to say so.
        if (s.length() >= 6 && l.startsWith(s)) return true;
        return Math.min(x.length(), y.length()) >= 6 && alike(x, y) >= 0.86f;
    }

    /**
     * Pure: the programme in a listing for one from start to end (ms) called title: the listing's programmes on then (at
     * least a third of ours, or of theirs, overlapping) with the same title (sameTitle), the most overlapping; -1 when
     * none is the same programme (never another one in the same slot).
     */
    public static int match(Listing l, long start, long end, String title) {
        if (l == null || title == null || tidyTitle(title).length() < 2) return -1;
        int best = -1;
        long bestO = 0;
        for (int i = 0; i < l.size(); i++) {
            long o = Math.min(end, l.end[i]) - Math.max(start, l.start[i]);
            if (o <= 0) continue;
            long ours = Math.max(1, end - start), theirs = Math.max(1, l.end[i] - l.start[i]);
            if (o * 3 < ours && o * 3 < theirs) continue;
            if (!sameTitle(title, l.title[i])) continue;
            if (o > bestO) {
                bestO = o;
                best = i;
            }
        }
        return best;
    }

    // ---- times -------------------------------------------------------------------------------------------------------

    private static final Pattern ISO = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2})(?::(\\d{2}))?(?:\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?");

    /** Pure: an ISO time ("2026-11-25T09:30:00+0000", "...Z", "...+00:00") in ms; 0 when it can't be read. */
    public static long isoTime(String s) {
        if (s == null) return 0;
        Matcher m = ISO.matcher(s.trim());
        if (!m.find()) return 0;
        try {
            java.time.LocalDateTime t = java.time.LocalDateTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)),
                    m.group(6) == null ? 0 : Integer.parseInt(m.group(6)));
            int off = 0;
            String z = m.group(7);
            if (z != null && !z.equals("Z")) {
                String d = z.replace(":", "");
                off = (Integer.parseInt(d.substring(1, 3)) * 60 + Integer.parseInt(d.substring(3, 5))) * (d.charAt(0) == '-' ? -1 : 1);
            }
            return t.toEpochSecond(java.time.ZoneOffset.UTC) * 1000L - off * 60_000L;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static final Pattern DURATION = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");

    /** Pure: an ISO duration ("PT1H15M") in ms; 0 when it can't be read. */
    public static long duration(String s) {
        Matcher m = DURATION.matcher(s == null ? "" : s.trim());
        if (!m.matches()) return 0;
        long h = m.group(1) == null ? 0 : Long.parseLong(m.group(1)), mi = m.group(2) == null ? 0 : Long.parseLong(m.group(2)),
                se = m.group(3) == null ? 0 : Long.parseLong(m.group(3));
        return (h * 3600 + mi * 60 + se) * 1000L;
    }
}
