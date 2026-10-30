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

    private PlaylistSport() {
    }

    /** One game or event a playlist channel is named for. */
    static final class Event {
        String home, away, title, channel, comp, stream;
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
            List<String[]> broadcasters = new ArrayList<>();
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
            }
            List<Listing> guide = guide(h, broadcasters);
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
    private static List<Listing> guide(HomeV2 h, List<String[]> cats) {
        List<Listing> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String[] cat : cats) {
            try {
                JSONArray s = new JSONArray(api(h, "get_live_streams&category_id=" + cat[0]));
                for (int i = 0; i < s.length() && seen.size() < 14; i++) {
                    JSONObject st = s.getJSONObject(i);
                    String name = st.optString("name");
                    String base = name.toLowerCase(Locale.ROOT).replaceAll("\\s*(\\(uk\\)|f?hd|uhd|4k|sd|50fps)\\s*", " ").replaceAll("\\s+", " ").trim();
                    if (!base.matches("(tnt sports [1-4]|premier sports [12]|laliga tv|viaplay sports [12]|eurosport [12])") || !seen.add(base)) continue;
                    JSONArray epg = new JSONObject(api(h, "get_short_epg&stream_id=" + st.optString("stream_id") + "&limit=16")).optJSONArray("epg_listings");
                    for (int k = 0; epg != null && k < epg.length(); k++) {
                        JSONObject x = epg.getJSONObject(k);
                        Listing l = new Listing();
                        l.title = new String(android.util.Base64.decode(x.optString("title"), android.util.Base64.DEFAULT), "UTF-8");
                        l.start = x.optLong("start_timestamp") * 1000L;
                        l.channel = name;
                        l.logo = st.optString("stream_icon", null);
                        out.add(l);
                    }
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
        for (Event e : read(h)) {
            if (e.start + 2 * 3_600_000L < now) continue;
            boolean onSky = false;
            if (e.home != null) for (HomeV2.Tile t : sky) if (t.title != null && same(t.title, e.home, e.away)) { onSky = true; break; }
            if (onSky) continue;
            String[] ch = e.channel.split("\u0000", 2);
            String eventChannel = ch[0];
            String[] by = ch.length > 1 && !ch[1].isEmpty() ? ch[1].split("\u0001", 2) : null;
            HomeV2.Tile t = new HomeV2.Tile();
            t.title = e.title;
            t.group = e.football ? "football" : "other";
            boolean on = e.start <= now;
            t.chip = on ? "LIVE NOW" : when.format(Instant.ofEpochMilli(e.start)).toUpperCase(Locale.UK);
            String showing = by != null ? clean(by[0]) : null;
            t.channel = showing != null ? showing : label(eventChannel, e.football);
            t.badge = by != null && by.length > 1 && !by[1].isEmpty() ? by[1] : null;
            t.sub = "Live on " + t.channel;
            t.heroTop = t.badge;
            t.heroTitle = e.title;
            t.heroMeta = (on ? "On now" : when.format(Instant.ofEpochMilli(e.start))) + " · Live on " + t.channel;
            t.heroText = e.home != null ? e.home + " against " + e.away + ", live." : e.title + ", live.";
            if (e.home != null) {
                String hl = crest(e.home), al = crest(e.away);
                if (hl != null && al != null) t.image = "crest:" + hl + "|" + al;
            }
            t.heroImage = t.image;
            t.start = e.start;
            // OK: TiviMate on the broadcaster when one is known (its usual channel, found by its search); else the
            // playlist's event channel itself, full screen here (TiviMate leaves those groups out on some TVs, and
            // neither it nor Kleva TV opens a channel from a link).
            String logo = t.badge;
            if (by != null) {
                List<String> names = new ArrayList<>();
                names.add(by[0]);
                t.ok = () -> h.tune(names, logo);
            } else if (e.stream != null && !e.stream.isEmpty()) {
                String url = Config.themeString(h.a, "iptv_server", "").replaceAll("/+$", "") + "/live/" + Config.themeString(h.a, "iptv_user", "")
                        + "/" + Config.themeString(h.a, "iptv_pass", "") + "/" + e.stream;
                String line = t.chip + " · " + t.channel;
                t.ok = () -> StreamActivity.play(h.a, url, e.title, line);
            }
            out.add(t);
        }
        return out;
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
                    if ("soccer".equals(t.optString("sport")) && img.startsWith("http")) { found = img; break; }
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
