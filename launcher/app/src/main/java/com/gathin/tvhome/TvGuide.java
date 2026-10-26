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
