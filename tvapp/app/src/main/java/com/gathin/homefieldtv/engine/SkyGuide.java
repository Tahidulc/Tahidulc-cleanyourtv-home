package com.gathin.homefieldtv.engine;

import android.content.Context;

import com.gathin.broadcast.SkyListing;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Programme pictures for the TV guide, from the public schedule behind Sky's UK TV guide, the same source the TV
 * launcher's own guide and the phone app's guide use (read by the same rules, shared with the phone: SkyListing): Sky's list of channels (read once a week, kept on the TV), a playlist's channel matched to
 * one of them by its name, that channel's day of programmes read the first time it is wanted (kept while the app runs),
 * and the programme on at a time picked by its start, its title when it can be. Only the channel's number and the day
 * are asked for: no key, nothing about the family.
 */
public final class SkyGuide {
    private SkyGuide() {}

    static final String SERVICES = SkyListing.SERVICES;
    static final String SCHEDULE = SkyListing.SCHEDULE;
    /** The picture's width: enough for the cells, the info card and the details' panel. */
    static final int WIDTH = 640;
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

    /** Android is short of memory: the days of programmes read so far let go (read again when wanted). */
    public static void trim() {
        synchronized (days) {
            days.clear();
        }
    }

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
        return SkyListing.picture(uuid, WIDTH);
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
        JSONArray events;
        try {
            events = SkyListing.events(Library.text(String.format(SCHEDULE, date, sid)), sid);
        } catch (IOException e) {
            events = new JSONArray();
        }
        synchronized (days) {
            days.put(k, events);
        }
        return events;
    }

    // ---- pure: names, the list, the programme (shared with the phone: SkyListing) ------------------------------------

    /** Sky's short names spelt out ("SkySpMainEvHD" → "Sky Sports Main Event HD", "U&Dave" → "Dave"). Pure. */
    static String skyName(String t) {
        return SkyListing.skyName(t);
    }

    /** Pure: a channel's name as both sides are compared ("UK: Sky Sports Main Event FHD" → "skysportmainevent"). */
    static String key(String name) {
        return SkyListing.keyOf(Names.key(name));
    }

    /** Pure: Sky's list of channels as name → its number: the North West's BBC One first, then an HD copy. */
    static Map<String, String> services(String json) {
        return SkyListing.services(json, Names::key);
    }

    /**
     * Pure: the programme on at a time (start seconds "st", length "d"), its title preferred among several; one starting
     * within five minutes after the time when none is on at it. Its "programmeuuid", or "".
     */
    static String uuidAt(JSONArray events, long at, String title) {
        return SkyListing.uuidAt(events, at, title);
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
