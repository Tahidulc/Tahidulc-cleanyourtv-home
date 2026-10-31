package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which British channel shows a game live, from the public schedule behind Sky's TV guide: the first channel in
 * config.xml football_tv_channels (in order of preference, the ones that carry 4K first) with a "Live" listing of the
 * two clubs that is on at kick-off. Sky's guide titles are short ("Live PL: Spurs v C. Palace"), so names are
 * compared word by word, with the usual nicknames. Off the main thread; each day's schedule is read at most every
 * 30 minutes.
 */
final class TvGuide {
    static final class Channel {
        final String name, logo;
        Channel(String name, String logo) { this.name = name; this.logo = logo; }
    }

    private static final String SCHEDULE = "https://awk.epgsky.com/hawk/linear/schedule/%s/%s";
    private static final long FRESH_MS = 30 * 60_000L;
    private static final Map<String, List<Object[]>> days = new HashMap<>();   // date -> {sid, title, start, end}
    private static final Map<String, Long> readAt = new HashMap<>();

    /** Nicknames in Sky's titles, to the words ESPN uses. */
    private static final String[][] ALIASES = {{"spurs", "tottenham"}, {"man utd", "manchester united"},
            {"man united", "manchester united"}, {"man city", "manchester city"}, {"c. palace", "crystal palace"},
            {"b. munich", "bayern munich"}, {"b. dortmund", "borussia dortmund"}, {"nott'm forest", "nottingham forest"},
            {"forest", "nottingham forest"}, {"wolves", "wolverhampton"}, {"psg", "paris saint-germain"},
            {"inter", "internazionale"}, {"atletico", "atletico madrid"}, {"brighton", "brighton"}, {"west ham", "west ham"},
            {"sheff utd", "sheffield united"}, {"sheff wed", "sheffield wednesday"}, {"qpr", "queens park rangers"}};

    static synchronized Channel find(String[] channels, String home, String away, long kickoff) {
        if (home == null || away == null || kickoff == 0) return null;
        String date = Instant.ofEpochMilli(kickoff).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        List<Object[]> events = schedule(channels, date);
        String h = norm(home), a = norm(away);
        for (String c : channels) {
            String[] p = c.split("\\|", 2);
            String sid = p[0].trim();
            for (Object[] e : events) {
                if (!sid.equals(e[0])) continue;
                String title = (String) e[1];
                long st = (Long) e[2], en = (Long) e[3];
                if (kickoff < st || kickoff > en || !title.toLowerCase(Locale.ROOT).startsWith("live")) continue;
                int v = title.indexOf(" v ");
                if (v < 0) continue;
                int colon = title.lastIndexOf(':', v);
                String t1 = norm(title.substring(colon >= 0 ? colon + 1 : 4, v));
                String t2 = norm(title.substring(v + 3));
                if (same(t1, h) && same(t2, a) || same(t1, a) && same(t2, h)) {
                    return new Channel(p.length > 1 ? p[1].trim() : sid,
                            "https://d2n0069hmnqmmx.cloudfront.net/epgdata/1.0/newchanlogos/600/600/skychb" + sid + ".png");
                }
            }
        }
        return null;
    }

    /** Where to watch a game in TiviMate: the channel names to try in turn, and the first one's logo (or null). */
    static final class Watch {
        final List<String> names;
        final String logo;
        Watch(List<String> names, String logo) { this.names = names; this.logo = logo; }
    }

    /**
     * Each competition's main broadcasters beyond Britain, by ESPN league slug, in the order to try them: what a
     * playlist from anywhere is likely to carry when Sky's guide and ESPN name nothing it has.
     */
    private static final Map<String, String[]> RIGHTS = new HashMap<>();
    static {
        RIGHTS.put("eng.1", new String[]{"Sky Sports Premier League", "TNT Sports 1", "NBC", "USA Network", "Peacock", "beIN Sports 1", "Canal+ Sport", "SuperSport Premier League", "Stan Sport", "DAZN 1"});
        RIGHTS.put("eng.2", new String[]{"Sky Sports Football", "Sky Sports+", "CBS Sports Golazo", "Paramount+"});
        RIGHTS.put("eng.league_cup", new String[]{"Sky Sports Football", "Sky Sports Main Event", "Sky Sports+", "CBS Sports Golazo", "Paramount+"});
        RIGHTS.put("eng.fa", new String[]{"BBC One", "ITV1", "TNT Sports 1", "ESPN", "ESPN+"});
        RIGHTS.put("sco.1", new String[]{"Sky Sports Main Event", "Sky Sports Football", "Premier Sports 1", "CBS Sports Golazo"});
        RIGHTS.put("uefa.champions", new String[]{"TNT Sports 1", "TNT Sports 2", "Amazon Prime Video", "CBS Sports Network", "Paramount+", "Stan Sport", "beIN Sports 1", "RTE 2"});
        RIGHTS.put("uefa.europa", new String[]{"TNT Sports 2", "TNT Sports 3", "CBS Sports Golazo", "Paramount+", "Stan Sport"});
        RIGHTS.put("uefa.europa.conf", new String[]{"TNT Sports 3", "TNT Sports 4", "CBS Sports Golazo", "Paramount+"});
        RIGHTS.put("esp.1", new String[]{"Premier Sports 1", "LaLiga TV", "ESPN", "ESPN+", "DAZN LaLiga", "beIN Sports 1"});
        RIGHTS.put("ita.1", new String[]{"TNT Sports 1", "CBS Sports Golazo", "Paramount+", "DAZN 1", "Sky Sport Calcio"});
        RIGHTS.put("ger.1", new String[]{"Sky Sports Football", "Sky Sports Mix", "ESPN+", "Sky Sport Bundesliga", "DAZN 1"});
        RIGHTS.put("fra.1", new String[]{"beIN Sports 1", "Ligue 1+", "DAZN 1", "Canal+ Sport"});
        RIGHTS.put("ned.1", new String[]{"Premier Sports 1", "ESPN 1", "ESPN+", "Viaplay Sports 1"});
        RIGHTS.put("por.1", new String[]{"Premier Sports 1", "Sport TV1", "GolTV", "Fox Soccer Plus"});
    }

    /** Formula 1's broadcasters, Britain first. */
    private static final String[] F1 = {"Sky Sports F1", "F1 TV", "ESPN", "Sky Sport F1", "Viaplay Formula 1", "Canal+ F1", "Kayo", "Fox Sports 506"};

    /**
     * Where to watch g: the British channel Sky's guide lists, then every channel ESPN lists, then the competition's
     * main broadcasters; for F1, Sky Sports F1 and the rest. Off the main thread (it may read the guide).
     */
    static Watch watch(String[] channels, ScoresTicker.Game g) {
        List<String> names = new ArrayList<>();
        String logo = null;
        boolean f1 = g.drivers != null || g.text != null || F1Results.F1_LOGO.equals(g.leagueLogo);
        if (f1) {
            for (String n : F1) names.add(n);
            return new Watch(names, "https://d2n0069hmnqmmx.cloudfront.net/epgdata/1.0/newchanlogos/600/600/skychb3835.png");
        }
        Channel uk = find(channels, g.homeName, g.awayName, g.tvAt != 0 ? g.tvAt : g.kickoff);
        if (uk != null) {
            names.add(uk.name);
            logo = uk.logo;
        }
        if (g.broadcasts != null) for (String n : g.broadcasts) if (!names.contains(n)) names.add(n);
        String[] rights = g.slug == null ? null : RIGHTS.get(g.slug);
        if (rights != null) for (String n : rights) if (!names.contains(n)) names.add(n);
        // Not found in the guide: the first British channel named still has its logo, by its Sky service id.
        if (logo == null) {
            for (String n : names) {
                for (String entry : channels) {
                    String[] p = entry.split("\\|", 2);
                    if (p.length == 2 && p[1].trim().equalsIgnoreCase(n.trim())) {
                        logo = "https://d2n0069hmnqmmx.cloudfront.net/epgdata/1.0/newchanlogos/600/600/skychb" + p[0].trim() + ".png";
                        break;
                    }
                }
                if (logo != null) break;
            }
        }
        return names.isEmpty() ? null : new Watch(names, logo);
    }

    /**
     * The channel in its UHD form where TiviMate has one (the theme's guide_channels, by Sky service id): its UHD name
     * ("Sky Sports Main Event UHD") and its logo drawn as UHD. The channel as it was otherwise.
     */
    static Channel uhd(android.content.Context c, Channel ch) {
        if (ch == null || ch.logo == null) return ch;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("skychb(\\d+)\\.png").matcher(ch.logo);
        if (!m.find()) return ch;
        String sid = m.group(1);
        for (String[] g : HomeV2.guideChannels(c)) {
            if (sid.equals(g[0]) && HomeV2.uhdName(g[1])) return new Channel(g[1].replaceAll("(?i)\\s+uhd$", " UHD"), ScoresTicker.skyLogo(sid, g[1]));
        }
        return ch;
    }

    /** watch(), the UHD channel first and its UHD logo where TiviMate has one. */
    static Watch watch(android.content.Context c, String[] channels, ScoresTicker.Game g) {
        Watch w = watch(channels, g);
        if (w == null || w.logo == null) return w;
        Channel up = uhd(c, new Channel(w.names.isEmpty() ? null : w.names.get(0), w.logo));
        if (up.logo.equals(w.logo)) return w;
        List<String> names = new ArrayList<>(w.names);
        names.remove(up.name);
        names.add(0, up.name);
        return new Watch(names, up.logo);
    }

    /** The British channel, or else where it is on in the US ("ESPN+ (US)", no logo); null when neither is known. */
    static Channel orAbroad(Channel uk, ScoresTicker.Game g) {
        if (uk != null || g == null || g.abroad == null) return uk;
        return new Channel(g.abroad + " (US)", null);
    }

    /** One club named two ways: a shared word of four letters or more ("palace", "dortmund", "tottenham"). */
    private static boolean same(String sky, String espn) {
        for (String[] alias : ALIASES) if (sky.equals(alias[0]) || sky.startsWith(alias[0] + " ")) sky = alias[1];
        if (sky.equals(espn)) return true;
        for (String w : sky.split(" ")) {
            if (w.length() < 4 || w.equals("city") || w.equals("united") || w.equals("town") || w.equals("real")) continue;
            if ((" " + espn + " ").contains(" " + w + " ")) return true;
        }
        return false;
    }

    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replace(" fc", "").replace("fc ", "").replaceAll("\\s+", " ").trim();
    }

    private static List<Object[]> schedule(String[] channels, String date) {
        Long at = readAt.get(date);
        if (at != null && System.currentTimeMillis() - at < FRESH_MS) return days.get(date);
        List<Object[]> out = new ArrayList<>();
        StringBuilder ids = new StringBuilder();
        for (String c : channels) {
            if (ids.length() > 0) ids.append(',');
            ids.append(c.split("\\|", 2)[0].trim());
        }
        try {
            JSONArray schedule = new JSONObject(get(String.format(SCHEDULE, date, ids))).optJSONArray("schedule");
            for (int i = 0; schedule != null && i < schedule.length(); i++) {
                JSONObject s = schedule.getJSONObject(i);
                JSONArray ev = s.optJSONArray("events");
                for (int k = 0; ev != null && k < ev.length(); k++) {
                    JSONObject e = ev.getJSONObject(k);
                    long st = e.optLong("st") * 1000L;
                    out.add(new Object[]{s.optString("sid"), e.optString("t"), st, st + e.optLong("d") * 1000L});
                }
            }
            days.put(date, out);
            readAt.put(date, System.currentTimeMillis());
        } catch (Exception ignored) {
            if (days.containsKey(date)) return days.get(date);
        }
        return out;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(10000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 3 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
