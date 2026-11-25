package com.gathin.broadcast;

import org.json.JSONArray;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A programme's own pictures, looked for the same way by Homefield TV (ProgrammePictures) and the phone app (ProgArt),
 * off the main thread: the programme found in each UK guide catalogue for its channel at its time (Catalogues: the
 * channel by its name, HD, UK, regions and +1 tidied, a +1 channel as its own an hour on; the programme by its time and
 * its title together, never another programme in the same slot), then by its title, in this order:
 *
 * <pre>
 *   1 the guide's own        its {@code <icon>} or {@code <image>} (not a placeholder, not the channel's logo)
 *   2 Sky                    the episode's own picture (SkyListing; Sky's "Image unavailable" card passed over)
 *   3 Freeview               the broadcaster's picture of it (BBC, ITV, Channel 4, 5, E4, Dave, CBeebies...)
 *   4 Virgin Media           its picture by its id (pay channels too: Sky Sports, TNT Sports, Sky Cinema, Gold...)
 *   5 Sky's series           the season's, then the series' picture
 *   6 a game                 TheSportsDB's picture of the game itself (on its day, the same two sides)
 *   7 by its title           TVMaze's episode that day, TMDB's episode still (the app's own key), then the series'
 *                            picture (TMDB's backdrop, TVMaze's poster); never for a game, a live event or a bulletin
 * </pre>
 *
 * It stops at the first of 2 to 6 it finds. Found: the pictures found, best first (one chain: each place takes the first
 * large enough for it), where the first came from, and whether to ask again later (a source couldn't be asked now). Every
 * answer is kept: the catalogues' listings by channel and day (PicFetch on disk, and small in memory), each picture
 * checked once. Nothing is made or asked for while the screens draw.
 */
public final class PicFinder {
    /** TMDB by the app's own client and key. */
    public interface ByTitle {
        /** TMDB's pictures of a programme: {its episode's still, its series' picture} ("" for none), null if not asked now. */
        String[] tmdb(Programme p, int season, int episode);
    }

    /** A programme as the apps' guides have it. */
    public static final class Programme {
        public final String channel, group, title, desc, category, epg, logo;
        public final long start, stop;

        public Programme(String channel, String group, String title, String desc, String category, long start, long stop, String epg,
                         String logo) {
            this.channel = channel == null ? "" : channel;
            this.group = group == null ? "" : group;
            this.title = title == null ? "" : title;
            this.desc = desc == null ? "" : desc;
            this.category = category == null ? "" : category;
            this.start = start;
            this.stop = stop;
            this.epg = epg == null ? "" : epg;
            this.logo = logo == null ? "" : logo;
        }
    }

    /** What was found: the pictures (a chain, best first; "" for none), the first's source, whether to ask again later. */
    public static final class Found {
        public final String chain, source;
        public final boolean later;

        Found(String chain, String source, boolean later) {
            this.chain = chain;
            this.source = source;
            this.later = later;
        }
    }

    public static final String EPG = "epg", SKY = "sky", FREEVIEW = "freeview", VIRGIN = "virgin", SKY_SERIES = "sky-series",
            SPORTSDB = "sportsdb", TVMAZE = "tvmaze", TMDB = "tmdb", NONE = "";

    static final long HOUR = 3600_000L, LISTINGS_MS = 6 * HOUR, CHANNELS_MS = 7 * 24 * HOUR, TITLES_MS = 7 * 24 * HOUR, DAY_MS = 24 * HOUR;

    private final PicFetch fetch;
    private final ByTitle byTitle;
    private final int skyWidth, virginWidth;
    private final Object LOAD = new Object();
    private Map<String, String> skyServices, virginIds;
    private long skyAt, virginAt;
    private final Map<String, Catalogues.Listing> skyDays = lru(48);
    private final Map<Long, Map<String, Catalogues.Listing>> freeviewDays = lru(2);
    private final Map<String, Map<String, Catalogues.Listing>> virginSegments = lru(5);
    private final Map<String, Boolean> heads = lru(2000);
    private final Map<String, String> titles = lru(600);

    /**
     * A finder keeping its pages in cacheDir (null: memory only), TMDB through byTitle (null: none), Sky's and Virgin
     * Media's pictures asked for at these widths (the TV's 640, a phone's whole panel 1280).
     */
    public PicFinder(File cacheDir, ByTitle byTitle, int skyWidth, int virginWidth) {
        this.fetch = new PicFetch(cacheDir);
        this.byTitle = byTitle;
        this.skyWidth = skyWidth;
        this.virginWidth = virginWidth;
    }

    private static <K, V> Map<K, V> lru(int max) {
        return java.util.Collections.synchronizedMap(new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> e) {
                return size() > max;
            }
        });
    }

    /** Android is short of memory: the listings kept in memory let go (read again from disk when wanted). */
    public void trim() {
        skyDays.clear();
        freeviewDays.clear();
        virginSegments.clear();
        heads.clear();
    }

    /** A programme's own pictures (off the main thread; asks the catalogues as it needs to, each answer kept). */
    public Found find(Programme p) {
        List<String> out = new ArrayList<>(4);
        String source = NONE;
        boolean later = false;
        if (usable(p.epg, p)) {
            out.add(p.epg);
            source = EPG;
        }
        String key = Catalogues.channelKey(p.channel);
        long shift = Catalogues.plusHours(p.channel) * HOUR, a = p.start - shift, b = p.stop - shift;
        String found = "", from = NONE;
        String[] sky = skyPictures(key, a, b, p.title);
        if (sky.length > 0 && checked(sky[0], p)) {
            found = sky[0];
            from = SKY;
        }
        if (found.isEmpty()) {
            found = freeview(key, a, b, p.title, p);
            if (!found.isEmpty()) from = FREEVIEW;
        }
        if (found.isEmpty()) {
            found = virgin(key, a, b, p.title, p);
            if (!found.isEmpty()) from = VIRGIN;
        }
        for (int i = 1; found.isEmpty() && i < sky.length; i++) {
            if (checked(sky[i], p)) {
                found = sky[i];
                from = SKY_SERIES;
            }
        }
        String[] sides = PicSources.sides(p.title);
        if (found.isEmpty() && sides != null) {
            found = sportsdb(sides[0], sides[1], p.start);
            if (!found.isEmpty()) from = SPORTSDB;
        }
        if (!found.isEmpty()) {
            out.add(found);
            if (source.equals(NONE)) source = from;
            return new Found(PicSources.chain(out.toArray(new String[0])), source, false);
        }
        // By its title: the episode's own picture first, then the series'.
        String kind = PicSources.kindOf(p.channel, p.group, p.title, p.desc, p.category);
        if (PicQuery.worth(p.title, p.channel, kind)) {
            String name = PicQuery.name(p.title);
            int[] se = PicQuery.episode(p.title, p.desc);
            String[] maze = tvmaze(name, p.start);
            String[] tmdb = byTitle == null ? new String[]{"", ""} : byTitle.tmdb(p, se[0], se[1]);
            if (tmdb == null) {
                later = true;
                tmdb = new String[]{"", ""};
            }
            String[] order = {maze[1], tmdb[0], tmdb.length > 1 ? tmdb[1] : "", maze[0]};
            String[] src = {TVMAZE, TMDB, TMDB, TVMAZE};
            for (int i = 0; i < order.length; i++) {
                if (usable(order[i], p)) {
                    out.add(order[i]);
                    if (source.equals(NONE)) source = src[i];
                }
            }
        }
        return new Found(PicSources.chain(out.toArray(new String[0])), source, later);
    }

    /** Whether an address may be a programme's picture: not a known placeholder, not the channel's logo. */
    private static boolean usable(String url, Programme p) {
        return url != null && !url.isEmpty() && (url.startsWith("http") || url.startsWith("file:")) && !PicVet.placeholderUrl(url)
                && !PicVet.logo(url, p.logo);
    }

    /** A picture checked once (a HEAD: a JPEG or WebP, never a placeholder card), and usable. */
    private boolean checked(String url, Programme p) {
        if (!usable(url, p)) return false;
        Boolean had = heads.get(url);
        if (had != null) return had;
        boolean ok = fetch.isImage(url);
        heads.put(url, ok);
        return ok;
    }

    // ---- Sky ---------------------------------------------------------------------------------------------------------

    /** Sky's pictures of the programme (episode, season, series), none when Sky doesn't have it. */
    String[] skyPictures(String key, long a, long b, String title) {
        Map<String, String> s = skyServices();
        String sid = s.get(SkyListing.keyOf(key));
        if (sid == null) return new String[0];
        String[] out = new String[0];
        for (String date : days(a, b)) {
            Catalogues.Listing l = skyDay(sid, date);
            int i = Catalogues.match(l, a, b, title);
            if (i >= 0) return SkyListing.pictures(l.pic[i], skyWidth);
        }
        return out;
    }

    private Map<String, String> skyServices() {
        synchronized (LOAD) {
            if (skyServices != null && (!skyServices.isEmpty() || System.currentTimeMillis() - skyAt < 10 * 60_000L)) return skyServices;
            String json = fetch.text(SkyListing.SERVICES, CHANNELS_MS);
            skyServices = json == null ? new java.util.HashMap<>() : SkyListing.services(json, Catalogues::channelKey);
            skyAt = System.currentTimeMillis();
            return skyServices;
        }
    }

    private Catalogues.Listing skyDay(String sid, String date) {
        String k = sid + "|" + date;
        Catalogues.Listing had = skyDays.get(k);
        if (had != null) return had;
        synchronized (LOAD) {
            had = skyDays.get(k);
            if (had != null) return had;
            String json = fetch.text(String.format(SkyListing.SCHEDULE, date, sid), LISTINGS_MS);
            JSONArray ev = json == null ? new JSONArray() : SkyListing.events(json, sid);
            Catalogues.Listing l = SkyListing.listing(ev);
            skyDays.put(k, l);
            return l;
        }
    }

    /** Pure: the days (UTC, "20261125") a programme from a to b is listed on. */
    static List<String> days(long a, long b) {
        List<String> out = new ArrayList<>(2);
        for (long t : new long[]{a, Math.max(a, b - 1)}) {
            java.time.LocalDate d = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneOffset.UTC).toLocalDate();
            String s = String.format(Locale.ROOT, "%04d%02d%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth());
            if (!out.contains(s)) out.add(s);
        }
        return out;
    }

    // ---- Freeview and Virgin Media -----------------------------------------------------------------------------------

    private String freeview(String key, long a, long b, String title, Programme p) {
        for (long day : new long[]{Catalogues.dayStart(a), Catalogues.dayStart(Math.max(a, b - 1))}) {
            Map<String, Catalogues.Listing> m = freeviewDay(day);
            Catalogues.Listing l = m.get(key);
            int i = Catalogues.match(l, a, b, title);
            if (i >= 0 && usable(l.pic[i], p)) return l.pic[i];
        }
        return "";
    }

    private Map<String, Catalogues.Listing> freeviewDay(long day) {
        Map<String, Catalogues.Listing> had = freeviewDays.get(day);
        if (had != null) return had;
        synchronized (LOAD) {
            had = freeviewDays.get(day);
            if (had != null) return had;
            String json = fetch.text(String.format(Locale.ROOT, Catalogues.FREEVIEW, day), LISTINGS_MS);
            Map<String, Catalogues.Listing> raw = json == null ? new java.util.HashMap<>() : Catalogues.freeview(json);
            // By the channels' own keys (Freeview's "BBC ONE Lon", "U&Dave"), the family's region or London first.
            Map<String, String> names = new java.util.HashMap<>();
            for (String n : raw.keySet()) names.put(n, n);
            Map<String, Catalogues.Listing> out = new java.util.HashMap<>();
            for (Map.Entry<String, String> e : Catalogues.byKey(names).entrySet()) out.put(e.getKey(), raw.get(e.getValue()));
            freeviewDays.put(day, out);
            return out;
        }
    }

    private String virgin(String key, long a, long b, String title, Programme p) {
        String id = virginIds().get(key);
        if (id == null) return "";
        String[] segs = {Catalogues.virginSegment(a), Catalogues.virginSegment(Math.max(a, b - 1))};
        for (int s = 0; s < segs.length; s++) {
            if (s == 1 && segs[1].equals(segs[0])) break;
            Catalogues.Listing l = virginSegment(segs[s]).get(id);
            int i = Catalogues.match(l, a, b, title);
            if (i >= 0) {
                String u = Catalogues.virginImage(l.pic[i], virginWidth);
                return checked(u, p) ? u : "";
            }
        }
        return "";
    }

    private Map<String, String> virginIds() {
        synchronized (LOAD) {
            if (virginIds != null && (!virginIds.isEmpty() || System.currentTimeMillis() - virginAt < 10 * 60_000L)) return virginIds;
            String json = fetch.text(Catalogues.VIRGIN_CHANNELS, CHANNELS_MS);
            virginIds = json == null ? new java.util.HashMap<>() : Catalogues.byKey(Catalogues.virginChannels(json));
            virginAt = System.currentTimeMillis();
            return virginIds;
        }
    }

    private Map<String, Catalogues.Listing> virginSegment(String stamp) {
        Map<String, Catalogues.Listing> had = virginSegments.get(stamp);
        if (had != null) return had;
        synchronized (LOAD) {
            had = virginSegments.get(stamp);
            if (had != null) return had;
            String json = fetch.text(String.format(Catalogues.VIRGIN_SEGMENT, stamp), LISTINGS_MS);
            Map<String, Catalogues.Listing> m = json == null ? new java.util.HashMap<>() : Catalogues.virgin(json);
            virginSegments.put(stamp, m);
            return m;
        }
    }

    // ---- by title and by game ----------------------------------------------------------------------------------------

    /** TVMaze's {show's picture, the episode's on the day} for a name ("" for none). */
    private String[] tvmaze(String name, long at) {
        String k = "maze|" + PicQuery.norm(name);
        String had = titles.get(k);
        String[] show;
        if (had == null) {
            String json = fetch.text(String.format(Catalogues.TVMAZE_SHOW, enc(name)), TITLES_MS);
            show = json == null ? null : Catalogues.tvmazeShow(json, name);
            titles.put(k, show == null ? "" : show[0] + " " + show[1]);
        } else {
            int sp = had.indexOf(' ');
            show = had.isEmpty() || sp < 0 ? null : new String[]{had.substring(0, sp), had.substring(sp + 1)};
        }
        if (show == null) return new String[]{"", ""};
        String ep = "";
        try {
            int id = Integer.parseInt(show[0]);
            String date = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();
            String json = fetch.text(String.format(Locale.ROOT, Catalogues.TVMAZE_DAY, id, date), DAY_MS);
            ep = json == null ? "" : Catalogues.tvmazeEpisode(json, at);
        } catch (NumberFormatException e) {
            ep = "";
        }
        return new String[]{show[1], ep};
    }

    /** TheSportsDB's picture of a game between two sides on its day ("" for none). */
    private String sportsdb(String home, String away, long at) {
        String q = Catalogues.sportsdbQuery(home, away);
        String k = "tsdb|" + q + "|" + Math.floorDiv(at, DAY_MS);
        String had = titles.get(k);
        if (had != null) return had;
        String json = fetch.text(String.format(Catalogues.SPORTSDB_EVENTS, enc(q)), DAY_MS);
        String t = json == null ? "" : Catalogues.sportsdbThumb(json, home, away, at);
        titles.put(k, t);
        return t;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }
}
