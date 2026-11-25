package com.gathin.tvhomephone.live;

import android.content.Context;

import com.gathin.broadcast.SkyListing;
import com.gathin.tvhomephone.Net;

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
 * Sky's own programme pictures for the guide, as Homefield TV has them (its SkyGuide, the same rules: SkyListing): Sky's
 * list of channels read once a week and kept in the app's files, a playlist's channel matched to one of them by its name
 * (Sky Sports, BBC One, Dave...), that channel's day of programmes read the first time it is wanted (kept in memory, a
 * few dozen days at most), and the programme on at a time picked by its start, its title when it can be; its picture
 * at 1280 wide (a phone's whole panel). Only the channel's number and the day are asked for: no key, nothing about the
 * family. Off the main thread only (ProgArt's worker).
 */
final class SkyPics {
    private SkyPics() {}

    static final long SERVICES_MS = 7L * 24 * 3600_000L, RETRY_MS = 10 * 60_000L;
    /** The picture's width: a phone's panel across, read smaller for a cell. */
    static final int WIDTH = 1280;
    /** Tests: the pages as Sky would give them (address → body), instead of asking Sky. */
    static volatile Map<String, String> fake;

    private static volatile Map<String, String> services;
    private static volatile long servicesAt;
    private static final Map<String, JSONArray> DAYS = new LinkedHashMap<String, JSONArray>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, JSONArray> e) {
            return size() > 48;
        }
    };
    private static final Object READING = new Object();

    /** The picture of what a channel shows at a time (its title helps pick it): its address, "" when Sky has none. */
    static String find(Context c, String channelName, long at, String title) {
        Map<String, String> s = services(c);
        String sid = s.get(SkyListing.keyOf(Names.key(channelName)));
        if (sid == null || sid.isEmpty()) return "";
        String date = Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        return SkyListing.picture(SkyListing.uuidAt(day(sid, date), at, title), WIDTH);
    }

    /** Tests: forgotten. */
    static void forget() {
        services = null;
        servicesAt = 0;
        synchronized (DAYS) {
            DAYS.clear();
        }
    }

    private static boolean usable(Map<String, String> s) {
        // An empty list (Sky not answering, none kept): asked again ten minutes on, not on every programme.
        return s != null && (!s.isEmpty() || System.currentTimeMillis() - servicesAt < RETRY_MS);
    }

    private static Map<String, String> services(Context c) {
        Map<String, String> s = services;
        if (usable(s)) return s;
        synchronized (READING) {
            if (usable(services)) return services;
            File f = c == null ? null : new File(c.getCacheDir(), "sky_services.json");
            String json = null;
            if (f != null && f.exists() && System.currentTimeMillis() - f.lastModified() < SERVICES_MS) json = read(f);
            if (json == null) {
                try {
                    json = get(SkyListing.SERVICES);
                    if (f != null) write(f, json);
                } catch (IOException | RuntimeException e) {
                    json = f != null && f.exists() ? read(f) : null;
                }
            }
            Map<String, String> m = json == null ? new HashMap<>() : SkyListing.services(json, Names::key);
            servicesAt = System.currentTimeMillis();
            services = m;
            return m;
        }
    }

    private static JSONArray day(String sid, String date) {
        String k = sid + "|" + date;
        synchronized (DAYS) {
            JSONArray had = DAYS.get(k);
            if (had != null) return had;
        }
        JSONArray events;
        try {
            events = SkyListing.events(get(String.format(SkyListing.SCHEDULE, date, sid)), sid);
        } catch (IOException | RuntimeException e) {
            events = new JSONArray();
        }
        synchronized (DAYS) {
            DAYS.put(k, events);
        }
        return events;
    }

    private static String get(String url) throws IOException {
        Map<String, String> f = fake;
        if (f != null) {
            String body = f.get(url);
            if (body == null) throw new IOException("not given");
            return body;
        }
        return Net.get(url, null, 10_000);
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
