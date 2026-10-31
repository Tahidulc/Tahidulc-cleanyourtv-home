package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fixtures the playlist itself carries, as Kleva TV shows them: its "Selected Live Football" and "Requested Live
 * Events" channels are named for the games they show tonight ("03. Elversberg vs Mainz 19:30"). Each becomes a Sports
 * tile: both clubs' crests (ESPN's) side by side, the time, and the channel that has it: the broadcaster when Sky's
 * guide or the playlist's own guide (TNT Sports, Premier Sports, LaLiga TV…) lists the game, with its logo; else the
 * event channel. OK tunes TiviMate to it (its search looks in every playlist, Royal and Sanctum alike), the event
 * channel next when the broadcaster is not in a playlist. Read at most every half hour; the theme's playlist login.
 */
final class PlaylistSport {
    private static final long FRESH_MS = 30 * 60_000L;
    private static final ZoneId UK = ZoneId.of("Europe/London");
    private static final Pattern GAME = Pattern.compile("^\\s*\\d+\\s*[.:)-]\\s*(.+?)\\s+(\\d{1,2}[:.]\\d{2}(?:\\s*/\\s*\\d{1,2}[:.]\\d{2})*)\\s*$");
    private static final Pattern VS = Pattern.compile("(?i)\\s+(?:vs?\\.?|v)\\s+");

    private static List<Event> events;
    private static long readAt;
    private static final Map<String, String> CRESTS = new HashMap<>();
    /** The broadcaster groups' channel names, {name, broadcaster, logo}, and the clubs' own channels {name, "EPL"}. */
    private static List<String[]> groupNames = new ArrayList<>(), clubChannels = new ArrayList<>();
    /** The leagues' coming games, read every quarter of an hour. */
    private static List<ScoresTicker.Game> fixtures;
    private static long fixturesAt;
    /** The competitions whose games join the Sports tiles beyond those the guides list: ESPN slugs. */
    private static final String[] FIXTURE_LEAGUES = {"eng.1", "uefa.champions", "uefa.europa", "uefa.europa.conf", "esp.1", "ita.1",
            "ger.1", "fra.1", "sco.1", "eng.fa", "eng.league_cup"};
    /** Each club's league by ESPN slug ("ger.1"), found with its crest. */
    private static final Map<String, String> LEAGUES = new HashMap<>();

    /**
     * The channel each league's games are on as Kleva TV lists them, when no British channel has the game: its
     * name and logo. Bundesliga games Sky UK leaves out are on Sky Sport Bundesliga, LaLiga's on LaLiga TV.
     */
    private static final Map<String, String[]> LEAGUE_TV = new HashMap<>();
    static {
        LEAGUE_TV.put("ger.1", new String[]{"Sky Sport Bundesliga",
                "https://upload.wikimedia.org/wikipedia/de/thumb/c/ca/Sky_Sport_Bundesliga_2020.svg/960px-Sky_Sport_Bundesliga_2020.svg.png"});
        LEAGUE_TV.put("esp.1", new String[]{"LaLiga TV", "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/united-kingdom/laliga-tv-uk.png"});
    }

    /**
     * The playlist's broadcaster groups whose channels are named for the game they show ("US (Paramount 001) |
     * Sheffield United vs. Wrexham"), in the order to name them: the group, the broadcaster's name, its logo (none
     * where the logo is too dark to read on the tiles: the name then).
     */
    private static final String[][] GROUPS = {
            {"(?i)^live \\| dazn \\(uk\\)$", "DAZN", null},
            {"(?i)^live \\| peacock\\b.*", "Peacock", null},
            {"(?i)^live \\| paramount\\+ \\(usa\\)$", "Paramount+", "https://web-storage.ultrabot.live/logos/streams/paramount-plus.png"},
            {"(?i)^live \\| espn\\+ \\(direct\\)$", "ESPN+", null},
            {"(?i)^canada \\| tsn\\+$", "TSN+", null},
            {"(?i)^live \\| viaplay \\(.*\\)$", "Viaplay", null},
    };

    private PlaylistSport() {
    }

    /** One game or event a playlist channel is named for. */
    static final class Event {
        String home, away, title, channel, comp, stream;
        /** A broadcaster in the playlist with a channel named for the game: its name and logo (or null). */
        String[] elsewhere;
        long start;
        boolean football;
    }

    /** One listing in a broadcaster's guide: its title, start, the channel's name and logo. */
    private static final class Listing {
        String title, channel, logo;
        long start;
    }

    // ---- reading -----------------------------------------------------------------------------------------------

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "TiviMate/5.1.6 (Android 14)");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private static String api(HomeV2 h, String action) throws Exception {
        String server = Config.themeString(h.a, "iptv_server", "").replaceAll("/+$", "");
        return get(server + "/player_api.php?username=" + URLEncoder.encode(Config.themeString(h.a, "iptv_user", ""), "UTF-8")
                + "&password=" + URLEncoder.encode(Config.themeString(h.a, "iptv_pass", ""), "UTF-8") + "&action=" + action);
    }

    private static synchronized List<Event> read(HomeV2 h) {
        if (events != null && System.currentTimeMillis() - readAt < FRESH_MS) return events;
        List<Event> out = new ArrayList<>();
        if (Config.themeString(h.a, "iptv_server", "").isEmpty()) return out;
        try {
            JSONArray cats = new JSONArray(api(h, "get_live_categories"));
            List<String[]> broadcasters = new ArrayList<>(), clubGroups = new ArrayList<>();
            List<List<String>> groups = new ArrayList<>();
            for (int g = 0; g < GROUPS.length; g++) groups.add(new ArrayList<>());
            for (int i = 0; i < cats.length(); i++) {
                JSONObject c = cats.getJSONObject(i);
                String name = c.optString("category_name"), id = c.optString("category_id");
                boolean football = name.matches("(?i).*(selected live football|live football).*");
                if (football || name.matches("(?i).*requested live events.*")) {
                    LocalDate day = dayOf(name);
                    JSONArray s = new JSONArray(api(h, "get_live_streams&category_id=" + id));
                    for (int k = 0; k < s.length(); k++) {
                        int before = out.size();
                        parse(s.getJSONObject(k).optString("name"), day, football, out);
                        for (int n = before; n < out.size(); n++) out.get(n).stream = s.getJSONObject(k).optString("stream_id");
                    }
                }
                if (name.matches("(?i)^uk\\s*\\|\\s*(tnt sports|sports)\\s*$")) broadcasters.add(new String[]{id, name});
                for (int g = 0; g < GROUPS.length; g++) if (name.trim().matches(GROUPS[g][0])) groups.get(g).add(id);
                if (name.matches("(?i)^live \\| (epl|scottish football) \\(.*\\)$")) clubGroups.add(new String[]{id, name.toLowerCase(Locale.ROOT).contains("epl") ? "EPL" : "SPFL"});
            }
            // The broadcasters' guides and groups read side by side: one after another they took a quarter of a minute.
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
            java.util.concurrent.Future<List<Listing>> guided = pool.submit(() -> guide(h, broadcasters, pool));
            List<List<java.util.concurrent.Future<String>>> lists = new ArrayList<>();
            for (List<String> ids : groups) {
                List<java.util.concurrent.Future<String>> f = new ArrayList<>();
                for (String id : ids) f.add(pool.submit(() -> api(h, "get_live_streams&category_id=" + id)));
                lists.add(f);
            }
            // The broadcasters' channels named for tonight's games: the first group with one for each game.
            List<java.util.concurrent.Future<String>> clubLists = new ArrayList<>();
            for (String[] cg : clubGroups) clubLists.add(pool.submit(() -> api(h, "get_live_streams&category_id=" + cg[0])));
            List<String[]> names = new ArrayList<>(), clubs = new ArrayList<>();
            for (int g = 0; g < GROUPS.length; g++) for (java.util.concurrent.Future<String> group : lists.get(g)) {
                try {
                    JSONArray s = new JSONArray(group.get(30, java.util.concurrent.TimeUnit.SECONDS));
                    for (int k = 0; k < s.length(); k++) names.add(new String[]{s.getJSONObject(k).optString("name"), GROUPS[g][1], GROUPS[g][2]});
                    for (Event e : out) {
                        if (e.home == null || e.elsewhere != null) continue;
                        for (int k = 0; k < s.length(); k++) {
                            if (!same(s.getJSONObject(k).optString("name"), e.home, e.away)) continue;
                            e.elsewhere = new String[]{GROUPS[g][1], GROUPS[g][2]};
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            for (int c = 0; c < clubLists.size(); c++) {
                try {
                    JSONArray s = new JSONArray(clubLists.get(c).get(30, java.util.concurrent.TimeUnit.SECONDS));
                    for (int k = 0; k < s.length(); k++) clubs.add(new String[]{s.getJSONObject(k).optString("name"), clubGroups.get(c)[1]});
                } catch (Exception ignored) {
                }
            }
            groupNames = names;
            clubChannels = clubs;
            List<Listing> guide;
            try {
                guide = guided.get(40, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception late) {
                guide = new ArrayList<>();
            }
            pool.shutdownNow();
            for (Event e : out) e.channel = e.channel + "\u0000" + broadcaster(e, guide);
        } catch (Exception ignored) {
            // The playlist not answering: no extra fixtures this time, Sky's as before.
        }
        events = out;
        readAt = System.currentTimeMillis();
        return out;
    }

    /** "LIVE | Selected Live Football (Fri)": the coming Friday (today, if it is one); no day named: today. */
    private static LocalDate dayOf(String category) {
        LocalDate today = LocalDate.now(UK);
        Matcher m = Pattern.compile("(?i)\\((mon|tue|wed|thu|fri|sat|sun)[a-z]*\\)").matcher(category);
        if (!m.find()) return today;
        String d = m.group(1).toLowerCase(Locale.ROOT);
        DayOfWeek want = d.startsWith("mon") ? DayOfWeek.MONDAY : d.startsWith("tue") ? DayOfWeek.TUESDAY : d.startsWith("wed") ? DayOfWeek.WEDNESDAY
                : d.startsWith("thu") ? DayOfWeek.THURSDAY : d.startsWith("fri") ? DayOfWeek.FRIDAY : d.startsWith("sat") ? DayOfWeek.SATURDAY : DayOfWeek.SUNDAY;
        LocalDate day = today;
        while (day.getDayOfWeek() != want) day = day.plusDays(1);
        return day;
    }

    /** "02. Le Havre vs PSG/Lille vs Lens 18:00/20:05": a game for each pair of names and time. */
    private static void parse(String name, LocalDate day, boolean football, List<Event> into) {
        Matcher m = GAME.matcher(name == null ? "" : name);
        if (!m.matches()) return;
        String[] games = m.group(1).split("\\s*/\\s*"), times = m.group(2).split("\\s*/\\s*");
        for (int i = 0; i < games.length; i++) {
            String game = games[i].trim();
            if (game.isEmpty()) continue;
            String time = times[Math.min(i, times.length - 1)].replace('.', ':');
            Event e = new Event();
            String[] teams = VS.split(game);
            if (teams.length == 2) { e.home = fix(teams[0]); e.away = fix(teams[1]); e.title = e.home + " v " + e.away; }
            else e.title = game;
            e.football = football && teams.length == 2;
            try {
                e.start = ZonedDateTime.of(day, LocalTime.parse(time.length() == 4 ? "0" + time : time), UK).toInstant().toEpochMilli();
            } catch (Exception ex) {
                continue;
            }
            e.channel = name.trim();
            into.add(e);
        }
    }

    /** A club as the playlist writes it, put right where it is known to be misspelt ("Le Harve"). */
    private static String fix(String club) {
        return club.trim().replaceAll("(?i)\\bharve\\b", "Havre");
    }

    /** "03. Elversberg vs Mainz 19:30" in "LIVE | Selected Live Football (Fri)": "Live Football 03". */
    private static String label(String channel, boolean football) {
        Matcher m = Pattern.compile("^\\s*(\\d+)").matcher(channel);
        return (football ? "Live Football" : "Live Events") + (m.find() ? " " + m.group(1) : "");
    }

    /** The broadcasters' guides from the playlist: TNT Sports 1 to 4, Premier Sports, LaLiga TV, Viaplay, Eurosport. */
    private static List<Listing> guide(HomeV2 h, List<String[]> cats, java.util.concurrent.ExecutorService pool) {
        List<Listing> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<JSONObject> channels = new ArrayList<>();
        for (String[] cat : cats) {
            try {
                JSONArray s = new JSONArray(api(h, "get_live_streams&category_id=" + cat[0]));
                for (int i = 0; i < s.length() && seen.size() < 14; i++) {
                    JSONObject st = s.getJSONObject(i);
                    String base = st.optString("name").toLowerCase(Locale.ROOT).replaceAll("\\s*(\\(uk\\)|f?hd|uhd|4k|sd|50fps)\\s*", " ").replaceAll("\\s+", " ").trim();
                    if (base.matches("(tnt sports [1-4]|premier sports [12]|laliga tv|viaplay sports [12]|eurosport [12])") && seen.add(base)) channels.add(st);
                }
            } catch (Exception ignored) {
            }
        }
        // Each channel's guide asked for at once.
        List<java.util.concurrent.Future<String>> asked = new ArrayList<>();
        for (JSONObject st : channels) asked.add(pool.submit(() -> api(h, "get_short_epg&stream_id=" + st.optString("stream_id") + "&limit=16")));
        for (int c = 0; c < channels.size(); c++) {
            try {
                JSONObject st = channels.get(c);
                JSONArray epg = new JSONObject(asked.get(c).get(20, java.util.concurrent.TimeUnit.SECONDS)).optJSONArray("epg_listings");
                for (int k = 0; epg != null && k < epg.length(); k++) {
                    JSONObject x = epg.getJSONObject(k);
                    Listing l = new Listing();
                    l.title = new String(android.util.Base64.decode(x.optString("title"), android.util.Base64.DEFAULT), "UTF-8");
                    l.start = x.optLong("start_timestamp") * 1000L;
                    l.channel = st.optString("name");
                    l.logo = st.optString("stream_icon", null);
                    out.add(l);
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** "TNT Sports 2 HD\u0001logo" when a broadcaster's guide lists the game within two hours of its time, else "". */
    private static String broadcaster(Event e, List<Listing> guide) {
        if (e.home == null) return "";
        for (Listing l : guide) {
            if (Math.abs(l.start - e.start) > 2 * 3_600_000L) continue;
            if (same(l.title, e.home, e.away)) return l.channel + "\u0001" + (l.logo == null ? "" : l.logo);
        }
        return "";
    }

    // ---- matching names --------------------------------------------------------------------------------------------

    /** A club's name to compare: no accents, no "FC"/"AFC"/"SV"…, "Utd" as "United". */
    static String plain(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        n = n.replace("utd", "united").replace("harve", "havre").replaceAll("[^a-z0-9 ]", " ");
        return (" " + n + " ").replaceAll(" (fc|afc|cf|sc|sv|ac|as|ss|club|de|the|1) ", " ").replaceAll("\\s+", " ").trim();
    }

    /** The word of a club's name that marks it out (its longest). */
    private static String key(String club) {
        String best = "";
        for (String w : plain(club).split(" ")) if (w.length() > best.length()) best = w;
        return best;
    }

    /** Words that say nothing about which club it is: two clubs sharing one of them are not the same. */
    private static final java.util.Set<String> COMMON = new java.util.HashSet<>(java.util.Arrays.asList("city", "united", "town",
            "athletic", "rovers", "wanderers", "county", "real", "sporting", "olympique", "borussia", "women", "albion", "hotspur",
            "north", "south", "west", "east", "saint", "inter", "racing", "deportivo", "atletico", "dynamo", "union", "club"));

    /**
     * Whether a tile already shows this game, however its title names the clubs ("Spurs v C. Palace" for Tottenham
     * Hotspur v Crystal Palace): within two and a half hours of its kick-off, naming one of its clubs by a word of its own.
     */
    static boolean shown(HomeV2.Tile t, String home, String away, long kickoff) {
        if (t.title == null) return false;
        if (same(t.title, home, away)) return true;
        // Sky's listing starts with the build-up, up to a couple of hours before kick-off (Chelsea v Man Utd: 11:00 for 12:30).
        if (t.start == 0 || Math.abs(t.start - kickoff) > 150 * 60_000L) return false;
        String title = " " + plain(t.title.replace("Spurs", "Tottenham").replace("Man Utd", "Manchester United").replace("Man United", "Manchester United")
                .replace("Man City", "Manchester City")
                .replace("Wolves", "Wolverhampton").replace("Nott'm", "Nottingham").replace("PSG", "Paris")) + " ";
        for (String club : new String[]{home, away}) {
            for (String w : plain(club).split(" ")) {
                if (w.length() >= 4 && !COMMON.contains(w) && title.contains(" " + w + " ")) return true;
            }
        }
        return false;
    }

    /**
     * The tiles with any game shown twice left once (Sky's guide naming it one way on two channels, a league's list
     * another): the one on a 4K channel kept, else the first. Order kept.
     */
    static List<HomeV2.Tile> once(List<HomeV2.Tile> tiles) {
        List<HomeV2.Tile> out = new ArrayList<>();
        for (HomeV2.Tile t : tiles) {
            String[] clubs = t.title == null ? new String[0] : VS.split(t.title.replaceFirst("^[^:]*:\\s*", ""));
            int twin = -1;
            if (clubs.length == 2) for (int i = 0; i < out.size() && twin < 0; i++) if (shown(out.get(i), clubs[0], clubs[1], t.start)) twin = i;
            if (twin < 0) out.add(t);
            else if (HomeV2.uhdName(t.channel) && !HomeV2.uhdName(out.get(twin).channel)) out.set(twin, t);
        }
        return out;
    }

    /** Whether a title is about this game: both clubs' marking words in it. */
    static boolean same(String title, String home, String away) {
        String t = " " + plain(title) + " ";
        String h = key(home), a = key(away);
        return h.length() >= 3 && a.length() >= 3 && t.contains(h) && t.contains(a);
    }

    // ---- tiles -----------------------------------------------------------------------------------------------------

    /**
     * The playlist's games as Sports tiles, those Sky's guide already has left out (its tile stays, with Sky's own
     * picture). Off the main thread.
     */
    static List<HomeV2.Tile> tiles(HomeV2 h, List<HomeV2.Tile> sky) {
        List<HomeV2.Tile> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        DateTimeFormatter when = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK).withZone(ZoneId.systemDefault());
        // Today's scores by league, read once: a game already at full time is no longer "Live now".
        Map<String, List<ScoresTicker.Game>> boards = new HashMap<>();
        for (Event e : read(h)) {
            if (e.start + 2 * 3_600_000L < now) continue;
            boolean onSky = false;
            if (e.home != null) for (HomeV2.Tile t : sky) if (shown(t, e.home, e.away, e.start)) { onSky = true; break; }
            if (e.home != null) for (HomeV2.Tile t : out) if (shown(t, e.home, e.away, e.start)) { onSky = true; break; }
            if (onSky) continue;
            HomeV2.Tile t = new HomeV2.Tile();
            t.title = e.title;
            t.group = e.football ? "football" : "other";
            boolean on = e.start <= now;
            t.chip = on ? "LIVE NOW" : when.format(Instant.ofEpochMilli(e.start)).toUpperCase(Locale.UK);
            String hl = e.home != null ? crest(e.home) : null, al = e.away != null ? crest(e.away) : null;
            String slug = e.home == null ? null : league(e.home) != null ? league(e.home) : league(e.away);
            if (slug != null && e.start <= now) {
                List<ScoresTicker.Game> board = boards.get(slug);
                if (board == null) {
                    board = ScoresTicker.load(new String[]{slug});
                    boards.put(slug, board);
                }
                boolean over = false;
                for (ScoresTicker.Game g : board) if (g.state == 2 && same(g.homeName + " " + g.awayName, e.home, e.away)) over = true;
                if (over) continue;
            }
            String[] showing = channel(e, slug);
            t.channel = showing[0];
            t.badge = showing[1];
            t.sub = "Live on " + t.channel;
            t.heroTop = t.badge;
            t.heroTitle = e.title;
            t.heroMeta = (on ? "On now" : when.format(Instant.ofEpochMilli(e.start))) + " · Live on " + t.channel;
            t.heroText = e.home != null ? e.home + " against " + e.away + ", live." : e.title + ", live.";
            if (hl != null && al != null) t.image = "crest:" + hl + "|" + al;
            t.heroImage = t.image;
            t.start = e.start;
            // OK: TiviMate on the broadcaster when one is known (its usual channel, found by its search); else the
            // playlist's event channel itself, full screen here (TiviMate leaves those groups out on some TVs, and
            // neither it nor Kleva TV opens a channel from a link).
            // OK: Kleva TV, where these games are listed, found by its search and played (its first channel for the
            // game, from the Royal or Sanctum playlist). Without Kleva TV on the TV: the event channel full screen here.
            String url = e.stream == null || e.stream.isEmpty() ? null : Config.themeString(h.a, "iptv_server", "").replaceAll("/+$", "") + "/live/"
                    + Config.themeString(h.a, "iptv_user", "") + "/" + Config.themeString(h.a, "iptv_pass", "") + "/" + e.stream;
            String line = t.chip + " · " + t.channel;
            String art = t.image;
            t.ok = () -> h.kleva(e.home, e.away, e.title, art, () -> { if (url != null) StreamActivity.play(h.a, url, e.title, line); });
            out.add(t);
        }
        return out;
    }

    /**
     * The channel a game is on, as Kleva TV would name it: {name, logo or null}. A British broadcaster whose guide has
     * the game; else the league's own channel; else a broadcaster in the playlist with a channel for it; else the
     * playlist's event channel ("Live Football 03").
     */
    private static String[] channel(Event e, String slug) {
        String[] ch = e.channel.split("\u0000", 2);
        String[] by = ch.length > 1 && !ch[1].isEmpty() ? ch[1].split("\u0001", 2) : null;
        if (by != null) return new String[]{clean(by[0]), by.length > 1 && !by[1].isEmpty() ? by[1] : null};
        String[] league = slug == null ? null : LEAGUE_TV.get(slug);
        if (league != null) return league;
        if (e.elsewhere != null) return e.elsewhere;
        return new String[]{label(ch[0], e.football), null};
    }

    /** Where a game is on and how to watch it, for one no British channel lists: its channel and OK's action. */
    static final class Where {
        String name, logo;
        Runnable ok;
    }

    /**
     * Where a game Sky's guide does not have is on (ESPN's club names and league slug), as its Sports tile names it,
     * and OK playing it in Kleva TV; null when neither the playlist nor the league says. Off the main thread.
     */
    static Where where(HomeV2 h, String home, String away, String slug, String art) {
        if (home == null || away == null) return null;
        Where w = null;
        for (Event e : read(h)) {
            if (e.home == null || !same(e.home + " " + e.away, home, away)) continue;
            String[] on = channel(e, slug);
            w = new Where();
            w.name = on[0];
            w.logo = on[1];
            String url = e.stream == null || e.stream.isEmpty() ? null : Config.themeString(h.a, "iptv_server", "").replaceAll("/+$", "") + "/live/"
                    + Config.themeString(h.a, "iptv_user", "") + "/" + Config.themeString(h.a, "iptv_pass", "") + "/" + e.stream;
            String name = on[0];
            w.ok = () -> h.kleva(e.home, e.away, e.title, art, () -> { if (url != null) StreamActivity.play(h.a, url, e.title, name); });
            return w;
        }
        String[] league = slug == null ? null : LEAGUE_TV.get(slug);
        if (league == null) return null;
        w = new Where();
        w.name = league[0];
        w.logo = league[1];
        w.ok = () -> h.kleva(home, away, home + " v " + away, art, () -> { });
        return w;
    }

    /** Today's and the next two days' games in FIXTURE_LEAGUES, from ESPN, each day and league read side by side. */
    /** The leagues' fixtures read ahead (sport() starts this beside Sky's guide). */
    static void prepareFixtures() {
        fixtures();
    }

    /** The playlist read and every club's crest (and league) looked up, side by side, ahead of tiles(). */
    static void prepare(HomeV2 h) {
        List<Event> all = read(h);
        java.util.Set<String> clubs = new java.util.LinkedHashSet<>();
        for (Event e : all) if (e.home != null) { clubs.add(e.home); clubs.add(e.away); }
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        List<java.util.concurrent.Future<?>> asked = new ArrayList<>();
        for (String c : clubs) asked.add(pool.submit(() -> crest(c)));
        for (java.util.concurrent.Future<?> f : asked) {
            try { f.get(15, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) { }
        }
        pool.shutdownNow();
    }

    private static synchronized List<ScoresTicker.Game> fixtures() {
        if (fixtures != null && System.currentTimeMillis() - fixturesAt < 15 * 60_000L) return fixtures;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        List<java.util.concurrent.Future<List<ScoresTicker.Game>>> asked = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        for (int d = 0; d < 3; d++) for (String slug : FIXTURE_LEAGUES) {
            LocalDate day = today.plusDays(d);
            asked.add(pool.submit(() -> ScoresTicker.load(new String[]{slug}, day)));
        }
        List<ScoresTicker.Game> out = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (java.util.concurrent.Future<List<ScoresTicker.Game>> f : asked) {
            try {
                for (ScoresTicker.Game g : f.get(20, java.util.concurrent.TimeUnit.SECONDS)) if (g.id == null || ids.add(g.id)) out.add(g);
            } catch (Exception ignored) {
            }
        }
        pool.shutdownNow();
        fixtures = out;
        fixturesAt = System.currentTimeMillis();
        return out;
    }

    /**
     * The leagues' games over the next three days that no tile has yet (Sky's guide and the playlist's own lists
     * have theirs), each with the channel it is on: a British one in Sky's guide (OK tunes TiviMate to it); else, as
     * the playlist tiles, the league's own channel or a playlist channel for it (OK plays it in Kleva TV); else the
     * club's own channel in the playlist's Premier League and Scottish groups; else where ESPN lists it. Off the
     * main thread, after tiles().
     */
    static List<HomeV2.Tile> leagueTiles(HomeV2 h, List<HomeV2.Tile> have) {
        List<HomeV2.Tile> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        DateTimeFormatter when = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK).withZone(ZoneId.systemDefault());
        String[] footballTv = h.a.getResources().getStringArray(R.array.football_tv_channels);
        for (ScoresTicker.Game g : fixtures()) {
            if (g.state == 2 || g.homeName == null || g.awayName == null || g.kickoff == 0 || g.kickoff > now + 3 * 86_400_000L) continue;
            boolean shown = false;
            for (HomeV2.Tile t : have) if (shown(t, g.homeName, g.awayName, g.kickoff)) { shown = true; break; }
            for (HomeV2.Tile t : out) if (shown(t, g.homeName, g.awayName, g.kickoff)) { shown = true; break; }
            if (shown) continue;
            String title = g.homeName + " v " + g.awayName;
            String art = g.homeLogo != null && g.awayLogo != null ? "crest:" + g.homeLogo + "|" + g.awayLogo : null;
            String name = null, logo = null;
            Runnable ok = null;
            TvGuide.Channel uk = TvGuide.find(footballTv, g.homeName, g.awayName, g.kickoff);
            if (uk != null) {
                name = uk.name;
                logo = uk.logo;
                String n = name, l = logo;
                ok = () -> h.tune(n, l);
            } else {
                Where w = where(h, g.homeName, g.awayName, g.slug, art);
                String[] other = w == null ? elsewhere(g.homeName, g.awayName, g.slug) : null;
                if (w != null) { name = w.name; logo = w.logo; ok = w.ok; }
                else if (other != null) { name = other[0]; logo = other[1]; }
                else if (!g.broadcasts.isEmpty()) name = g.broadcasts.get(0);
                if (ok == null) ok = () -> h.kleva(g.homeName, g.awayName, title, art, () -> { });
            }
            HomeV2.Tile t = new HomeV2.Tile();
            t.title = title;
            t.group = "football";
            // The competition's own colours, as the Sky tiles have: its white "dark" logo vanished on the corner's white chip.
            t.compLogo = g.leagueLogo == null ? null : g.leagueLogo.replace("/500-dark/", "/500/");
            boolean on = g.state == 0;
            t.chip = on ? "LIVE NOW" : when.format(Instant.ofEpochMilli(g.kickoff)).toUpperCase(Locale.UK);
            t.channel = name;
            t.badge = logo;
            String comp = ScoresTicker.competition(g);
            t.sub = name != null ? "Live on " + name : comp;
            t.image = art;
            t.heroImage = art;
            t.heroTop = logo;
            t.heroTitle = title;
            t.heroMeta = (on ? "On now" : when.format(Instant.ofEpochMilli(g.kickoff))) + (name != null ? " · Live on " + name : " · " + comp);
            t.heroText = comp + ": " + g.homeName + " against " + g.awayName + ".";
            t.start = g.kickoff;
            t.ok = ok;
            out.add(t);
        }
        return out;
    }

    /**
     * A game's channel from the playlist's lists alone (read() has run): the home club's own channel in its
     * Premier League or Scottish group, else a broadcaster's channel named for the game. {name, logo or null}.
     */
    private static String[] elsewhere(String home, String away, String slug) {
        if ("eng.1".equals(slug) || "sco.1".equals(slug)) {
            String want = plain(home);
            for (String[] c : clubChannels) {
                String n = plain(c[0]);
                if (n.equals(want) || n.equals(key(home))) return new String[]{c[1] + ": " + c[0], null};
            }
            for (String[] c : clubChannels) if ((" " + plain(c[0]) + " ").contains(" " + key(home) + " ")) return new String[]{c[1] + ": " + c[0], null};
        }
        for (String[] c : groupNames) if (same(c[0], home, away)) return new String[]{c[1], c[2]};
        return null;
    }

    /** "PREMIER SPORTS 1 FHD (UK)" as "Premier Sports 1". */
    private static String clean(String channel) {
        String c = channel.replaceAll("(?i)\\s*(\\(uk\\)|f?hd|uhd|4k|sd|50fps)\\s*", " ").replaceAll("\\s+", " ").trim();
        StringBuilder b = new StringBuilder();
        for (String w : c.split(" ")) {
            if (b.length() > 0) b.append(' ');
            b.append(w.length() <= 3 && w.equals(w.toUpperCase(Locale.ROOT)) && !w.matches("\\d+") ? w
                    : w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1).toLowerCase(Locale.ROOT));
        }
        return b.toString().replace("Tnt", "TNT").replace("Laliga", "LaLiga");
    }

    /** A club's league ("ger.1"), once its crest has been looked up; null when not known. */
    private static String league(String club) {
        synchronized (CRESTS) {
            return LEAGUES.get(plain(club));
        }
    }

    /** A club's crest from ESPN's search (its football team first), remembered. */
    private static String crest(String club) {
        String k = plain(club);
        synchronized (CRESTS) {
            if (CRESTS.containsKey(k)) return CRESTS.get(k);
        }
        String found = null;
        try {
            JSONArray r = new JSONObject(get("https://site.web.api.espn.com/apis/search/v2?query=" + URLEncoder.encode(club, "UTF-8")
                    + "&limit=4&type=team")).optJSONArray("results");
            for (int i = 0; r != null && i < r.length() && found == null; i++) {
                JSONArray c = r.getJSONObject(i).optJSONArray("contents");
                for (int j = 0; c != null && j < c.length(); j++) {
                    JSONObject t = c.getJSONObject(j);
                    String img = t.optJSONObject("image") == null ? "" : t.optJSONObject("image").optString("default");
                    if ("soccer".equals(t.optString("sport")) && img.startsWith("http")) {
                        found = img;
                        String slug = t.optString("defaultLeagueSlug", "");
                        if (!slug.isEmpty()) synchronized (CRESTS) { LEAGUES.put(k, slug); }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            return null;   // not remembered: asked again next time
        }
        synchronized (CRESTS) {
            CRESTS.put(k, found);
        }
        return found;
    }
}
