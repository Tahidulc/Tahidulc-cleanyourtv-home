package com.gathin.tvhomephone.sports;

import com.gathin.tvhomephone.Net;

import org.json.JSONArray;
import org.json.JSONObject;

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
 * Which British channel shows a game live (the TV's TvGuide): the "Live" listings of the football channels in the
 * public schedule behind Sky's TV guide, matched to the two clubs and the kick-off; then ESPN's own channel names and
 * the competition's main broadcasters, for the names a playlist may carry. Off the main thread.
 */
public final class Channels {
    private Channels() {}

    private static final String SCHEDULE = "https://awk.epgsky.com/hawk/linear/schedule/%s/%s";
    private static final long FRESH_MS = 30 * 60_000L;

    /** Sky guide service id and name, best channel first (the TV's football_tv_channels). */
    static final String[][] FOOTBALL = {{"4002", "Sky Sports Main Event"}, {"4010", "Sky Sports Premier League"},
            {"3625", "TNT Sports 1"}, {"3939", "Sky Sports Football"}, {"3627", "TNT Sports 2"}, {"3629", "TNT Sports 3"},
            {"4040", "TNT Sports 4"}, {"3940", "Sky Sports+"}, {"4090", "Sky Sports Mix"}, {"1289", "Premier Sports 1"},
            {"1290", "Premier Sports 2"}, {"3007", "BBC One"}, {"6504", "ITV1"}, {"4075", "Channel 4"}, {"4058", "5"}};

    private static final String[][] ALIASES = {{"spurs", "tottenham"}, {"man utd", "manchester united"},
            {"man united", "manchester united"}, {"man city", "manchester city"}, {"c. palace", "crystal palace"},
            {"b. munich", "bayern munich"}, {"b. dortmund", "borussia dortmund"}, {"nott'm forest", "nottingham forest"},
            {"forest", "nottingham forest"}, {"wolves", "wolverhampton"}, {"psg", "paris saint-germain"},
            {"inter", "internazionale"}, {"atletico", "atletico madrid"}, {"sheff utd", "sheffield united"},
            {"sheff wed", "sheffield wednesday"}, {"qpr", "queens park rangers"}};

    private static final Map<String, String[]> RIGHTS = new HashMap<>();
    static {
        RIGHTS.put("eng.1", new String[]{"Sky Sports Premier League", "TNT Sports 1"});
        RIGHTS.put("eng.2", new String[]{"Sky Sports Football", "Sky Sports+"});
        RIGHTS.put("eng.league_cup", new String[]{"Sky Sports Football", "Sky Sports Main Event", "Sky Sports+"});
        RIGHTS.put("eng.fa", new String[]{"BBC One", "ITV1", "TNT Sports 1"});
        RIGHTS.put("uefa.champions", new String[]{"TNT Sports 1", "TNT Sports 2", "Amazon Prime Video"});
        RIGHTS.put("uefa.europa", new String[]{"TNT Sports 2", "TNT Sports 3"});
        RIGHTS.put("uefa.europa.conf", new String[]{"TNT Sports 3", "TNT Sports 4"});
        RIGHTS.put("esp.1", new String[]{"Premier Sports 1", "LaLiga TV"});
        RIGHTS.put("ita.1", new String[]{"TNT Sports 1"});
        RIGHTS.put("ger.1", new String[]{"Sky Sports Football", "Sky Sports Mix"});
        RIGHTS.put("sco.1", new String[]{"Sky Sports Main Event", "Sky Sports Football", "Premier Sports 1"});
    }

    private static final Map<String, List<Object[]>> DAYS = new HashMap<>();
    private static final Map<String, Long> READ_AT = new HashMap<>();
    private static final Map<String, String> FOUND = new HashMap<>();

    /** The British channel showing g live, or null (from Sky's guide). */
    public static synchronized String uk(Game g) {
        if (g == null || g.kickoff == 0 || g.homeName == null || g.awayName == null) return null;
        if (FOUND.containsKey(g.id)) return FOUND.get(g.id);
        String date = Instant.ofEpochMilli(g.kickoff).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        List<Object[]> events = schedule(date);
        String h = norm(g.homeName), a = norm(g.awayName);
        String hit = null;
        outer:
        for (String[] ch : FOOTBALL) {
            for (Object[] e : events) {
                if (!ch[0].equals(e[0])) continue;
                String title = (String) e[1];
                long st = (Long) e[2], en = (Long) e[3];
                if (g.kickoff < st || g.kickoff > en || !title.toLowerCase(Locale.ROOT).startsWith("live")) continue;
                int v = title.indexOf(" v ");
                if (v < 0) continue;
                int colon = title.lastIndexOf(':', v);
                String t1 = norm(title.substring(colon >= 0 ? colon + 1 : 4, v)), t2 = norm(title.substring(v + 3));
                if (same(t1, h) && same(t2, a) || same(t1, a) && same(t2, h)) { hit = ch[1]; break outer; }
            }
        }
        if (!events.isEmpty()) FOUND.put(g.id, hit);
        return hit;
    }

    /** Every name to look for in the playlist, best first: the British channel, ESPN's, the competition's. */
    public static List<String> names(Game g) {
        List<String> out = new ArrayList<>();
        String uk = uk(g);
        if (uk != null) out.add(uk);
        for (String b : g.broadcasts) if (!out.contains(b)) out.add(b);
        String[] r = g.slug == null ? null : RIGHTS.get(g.slug);
        if (r != null) for (String n : r) if (!out.contains(n)) out.add(n);
        return out;
    }

    /** The channel to show as "Live on …": the first the playlist has, else the British one, else ESPN's first. */
    public static String best(android.content.Context c, Game g) {
        List<String> n = names(g);
        for (String name : n) if (com.gathin.tvhomephone.live.LiveLinks.has(c, name)) return name;
        String uk = uk(g);
        if (uk != null) return uk;
        return g.broadcasts.isEmpty() ? null : g.broadcasts.get(0);
    }

    private static List<Object[]> schedule(String date) {
        Long at = READ_AT.get(date);
        if (at != null && System.currentTimeMillis() - at < FRESH_MS) return DAYS.get(date);
        List<Object[]> out = new ArrayList<>();
        StringBuilder ids = new StringBuilder();
        for (String[] ch : FOOTBALL) {
            if (ids.length() > 0) ids.append(',');
            ids.append(ch[0]);
        }
        try {
            JSONArray schedule = new JSONObject(Net.get(String.format(SCHEDULE, date, ids))).optJSONArray("schedule");
            for (int i = 0; schedule != null && i < schedule.length(); i++) {
                JSONObject s = schedule.getJSONObject(i);
                JSONArray ev = s.optJSONArray("events");
                for (int k = 0; ev != null && k < ev.length(); k++) {
                    JSONObject e = ev.getJSONObject(k);
                    long st = e.optLong("st") * 1000L;
                    out.add(new Object[]{s.optString("sid"), e.optString("t"), st, st + e.optLong("d") * 1000L});
                }
            }
            DAYS.put(date, out);
            READ_AT.put(date, System.currentTimeMillis());
        } catch (Exception ignored) {
            if (DAYS.containsKey(date)) return DAYS.get(date);
        }
        return out;
    }

    static boolean same(String sky, String espn) {
        for (String[] alias : ALIASES) if (sky.equals(alias[0]) || sky.startsWith(alias[0] + " ")) sky = alias[1];
        if (sky.equals(espn)) return true;
        for (String w : sky.split(" ")) {
            if (w.length() < 4 || w.equals("city") || w.equals("united") || w.equals("town") || w.equals("real")) continue;
            if ((" " + espn + " ").contains(" " + w + " ")) return true;
        }
        return false;
    }

    static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replace(" fc", "").replace("fc ", "").replaceAll("\\s+", " ").trim();
    }
}
