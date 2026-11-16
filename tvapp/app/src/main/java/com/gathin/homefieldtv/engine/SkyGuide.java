package com.gathin.homefieldtv.engine;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Programme pictures for the TV guide, from the public schedule behind Sky's UK TV guide, the same source the TV
 * launcher's own guide uses: Sky's list of channels (read once a week, kept on the TV), a playlist's channel matched to
 * one of them by its name, that channel's day of programmes read the first time it is wanted (kept while the app runs),
 * and the programme on at a time picked by its start, its title when it can be. Only the channel's number and the day
 * are asked for: no key, nothing about the family.
 */
public final class SkyGuide {
    private SkyGuide() {}

    static final String SERVICES = "https://awk.epgsky.com/hawk/linear/services/4101/1";
    static final String SCHEDULE = "https://awk.epgsky.com/hawk/linear/schedule/%s/%s";
    static final String PICTURE = "https://images.metadata.sky.com/pd-image/%s/16-9/640";
    static final long SERVICES_MS = 7L * 24 * 3600_000L;

    public interface Callback {
        /** On the main thread: the picture's address, or "" when there is none. */
        void got(String url);
    }

    static final long RETRY_MS = 10 * 60_000L;
    private static volatile Map<String, String> services;
    private static volatile long servicesAt;
    private static final Map<String, JSONArray> days = new LinkedHashMap<String, JSONArray>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, JSONArray> e) {
            return size() > 48;
        }
    };
    private static final Object READING = new Object();

    /** The picture of what a channel shows at a time (its title helps pick it), "" when Sky has none for it. */
    public static void picture(Context c, String channelName, long at, String title, Callback cb) {
        Context app = c.getApplicationContext();
        Exec.io(() -> {
            String url = "";
            try {
                url = find(app, channelName, at, title);
            } catch (RuntimeException e) {
                url = "";
            }
            String got = url;
            Exec.main(() -> cb.got(got));
        });
    }

    /** Off the main thread: the picture's address, or "". */
    static String find(Context app, String channelName, long at, String title) {
        Map<String, String> s = services(app);
        String sid = s.get(key(channelName));
        if (sid == null || sid.isEmpty()) return "";
        String date = Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        JSONArray events = day(sid, date);
        String uuid = uuidAt(events, at, title);
        return uuid.isEmpty() ? "" : String.format(PICTURE, uuid);
    }

    private static boolean usable(Map<String, String> s) {
        // An empty list (Sky not answering, no list kept): asked again ten minutes on, not on every move.
        return s != null && (!s.isEmpty() || System.currentTimeMillis() - servicesAt < RETRY_MS);
    }

    private static Map<String, String> services(Context app) {
        Map<String, String> s = services;
        if (usable(s)) return s;
        synchronized (READING) {
            if (usable(services)) return services;
            File f = new File(app.getCacheDir(), "sky_services.json");
            String json = null;
            boolean fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < SERVICES_MS;
            if (fresh) json = read(f);
            if (json == null) {
                try {
                    json = Library.text(SERVICES);
                    write(f, json);
                } catch (IOException e) {
                    json = f.exists() ? read(f) : null;
                }
            }
            Map<String, String> m = json == null ? new HashMap<>() : services(json);
            servicesAt = System.currentTimeMillis();
            services = m;
            return m;
        }
    }

    private static JSONArray day(String sid, String date) {
        String k = sid + "|" + date;
        synchronized (days) {
            JSONArray had = days.get(k);
            if (had != null) return had;
        }
        JSONArray events = new JSONArray();
        try {
            JSONObject root = new JSONObject(Library.text(String.format(SCHEDULE, date, sid)));
            JSONArray sch = root.optJSONArray("schedule");
            for (int i = 0; sch != null && i < sch.length(); i++) {
                JSONObject one = sch.optJSONObject(i);
                if (one != null && sid.equals(one.optString("sid")) && one.optJSONArray("events") != null) events = one.optJSONArray("events");
            }
        } catch (IOException | JSONException e) {
            events = new JSONArray();
        }
        synchronized (days) {
            days.put(k, events);
        }
        return events;
    }

    // ---- pure: names, the list, the programme -----------------------------------------------------------------------

    /** Sky's short names spelt out ("SkySpMainEvHD" → "Sky Sports Main Event HD", "U&Dave" → "Dave"). Pure. */
    static String skyName(String t) {
        if (t == null) return "";
        String s = t.trim();
        if (s.startsWith("U&")) s = s.substring(2);
        String[][] spell = {
                {"SkySpMainEv", "Sky Sports Main Event"}, {"SkySp PL", "Sky Sports Premier League"},
                {"SkySp F'ball", "Sky Sports Football"}, {"SkySpBoxOff", "Sky Sports Box Office"}, {"SkySp", "Sky Sports "},
                {"TNTSBoxOff", "TNT Sports Box Office"}, {"TNTSports", "TNT Sports "}, {"ComedyXtra", "Comedy Central Extra"},
                {"ComedyCent", "Comedy Central "}, {"SkyDocmntrs", "Sky Documentaries "}, {"SkyPremiere", "Sky Premiere "},
                {"Disc.", "Discovery "}, {"Animal Plnt", "Animal Planet"}, {"Food Netwrk", "Food Network"},
                {"Cartoon Netwrk", "Cartoon Network"}, {"Cartoon Net ", "Cartoon Network "}, {"Crime+Inv", "Crime and Investigation"},
                {"Premier 1", "Premier Sports 1"}, {"Premier 2", "Premier Sports 2"}, {"NatGeoWild", "Nat Geo Wild"},
                {"BBCScotland", "BBC Scotland"}};
        for (String[] p : spell) s = s.replace(p[0], p[1]);
        // "NickelodeonHD", "Sky HistoryHD": the HD on its own.
        s = GLUED_HD.matcher(s).replaceAll(" HD");
        // BBC One's and Two's regions ("BBC One Lon HD", "BBC One NW HD"): the channel the same everywhere.
        if (s.startsWith("BBC One") || s.startsWith("BBC Two") || s.startsWith("ITV1")) s = REGION.matcher(s).replaceAll(" ");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static final Pattern GLUED_HD = Pattern.compile("(?<=[a-z0-9])HD\\b");
    private static final Pattern REGION = Pattern.compile("\\s(?:Lon|NE|NW|Yks|Y&L|WM|EMid|East|SE|Wst|Sth|SW|CI|Scot|Wal|NI)(?=\\s|$)");

    /** Pure: a channel's name as both sides are compared ("UK: Sky Sports Main Event FHD" → "skysportmainevent"). */
    static String key(String name) {
        String k = Names.key(name);
        return k.replace("junior", "jr").replace("geographic", "geo").replace("national", "nat");
    }

    /** Pure: Sky's list of channels as name → its number: the North West's BBC One first, then an HD copy. */
    static Map<String, String> services(String json) {
        Map<String, String> out = new HashMap<>();
        Map<String, Integer> rank = new HashMap<>();
        try {
            JSONArray a = new JSONObject(json).optJSONArray("services");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject s = a.optJSONObject(i);
                if (s == null || s.optString("sid").isEmpty() || s.optBoolean("adult")) continue;
                String raw = s.optString("t");
                String k = key(skyName(raw));
                if (k.isEmpty()) continue;
                // The family's region (Manchester) first, then HD, then the list's own order.
                int r = (raw.contains(" NW ") || raw.endsWith(" NW") ? 4 : 0) + ("HD".equals(s.optString("sf")) ? 2 : 0);
                Integer had = rank.get(k);
                if (had == null || r > had) {
                    out.put(k, s.optString("sid"));
                    rank.put(k, r);
                }
            }
        } catch (JSONException e) {
            // none
        }
        return out;
    }

    /**
     * Pure: the programme on at a time (start seconds "st", length "d"), its title preferred among several; one starting
     * within five minutes after the time when none is on at it. Its "programmeuuid", or "".
     */
    static String uuidAt(JSONArray events, long at, String title) {
        if (events == null) return "";
        long t = at / 1000L;
        String want = title == null ? "" : Names.plain(title).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        List<JSONObject> on = new ArrayList<>();
        JSONObject soon = null;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            long st = e.optLong("st"), en = st + e.optLong("d");
            if (st <= t && t < en) on.add(e);
            else if (st > t && st - t <= 300 && soon == null) soon = e;
        }
        JSONObject pick = null;
        for (JSONObject e : on) {
            String name = Names.plain(e.optString("t")).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!want.isEmpty() && (name.contains(want) || want.contains(name))) {
                pick = e;
                break;
            }
        }
        if (pick == null && !on.isEmpty()) pick = on.get(0);
        if (pick == null) pick = soon;
        return pick == null ? "" : pick.optString("programmeuuid", "");
    }

    private static String read(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 4 << 20)];
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) break;
                n += r;
            }
            return new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static void write(File f, String s) {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // read again next time
        }
    }
}
