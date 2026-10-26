package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What is on British TV now, from the public schedule behind Sky's online TV guide: for each channel in config.xml
 * uk_tv_channels ("service id|name"), the programme now on, its picture, and what follows. Off the main thread.
 */
final class UkTv {
    static final class Now {
        String channel, title, synopsis, image, logo, next;
        long start, end, nextStart;
    }

    private static final String SCHEDULE = "https://awk.epgsky.com/hawk/linear/schedule/%s/%s";

    static List<Now> now(String[] channels) {
        List<Now> out = new ArrayList<>();
        if (channels.length == 0) return out;
        StringBuilder ids = new StringBuilder();
        Map<String, String> names = new HashMap<>();
        for (String c : channels) {
            String[] p = c.split("\\|", 2);
            if (ids.length() > 0) ids.append(',');
            ids.append(p[0].trim());
            names.put(p[0].trim(), p.length > 1 ? p[1].trim() : p[0].trim());
        }
        long now = System.currentTimeMillis();
        // The schedule is by UTC day: late in the evening the next programme can be on tomorrow's.
        Map<String, List<JSONObject>> events = new HashMap<>();
        for (int day = 0; day < 2; day++) {
            // As a plain date: a zoned one prints its offset too ("20261026Z"), which the guide refuses.
            String date = Instant.ofEpochMilli(now).plusSeconds(day * 86_400L).atZone(ZoneOffset.UTC).toLocalDate()
                    .format(DateTimeFormatter.BASIC_ISO_DATE);
            try {
                JSONArray schedule = new JSONObject(get(String.format(SCHEDULE, date, ids))).optJSONArray("schedule");
                for (int i = 0; schedule != null && i < schedule.length(); i++) {
                    JSONObject s = schedule.getJSONObject(i);
                    List<JSONObject> list = events.computeIfAbsent(s.optString("sid"), k -> new ArrayList<>());
                    JSONArray ev = s.optJSONArray("events");
                    for (int k = 0; ev != null && k < ev.length(); k++) list.add(ev.getJSONObject(k));
                }
            } catch (Exception ignored) {
            }
        }
        for (String c : channels) {
            String sid = c.split("\\|", 2)[0].trim();
            List<JSONObject> list = events.get(sid);
            if (list == null) continue;
            list.sort((a, b) -> Long.compare(a.optLong("st"), b.optLong("st")));
            for (int i = 0; i < list.size(); i++) {
                JSONObject e = list.get(i);
                long st = e.optLong("st") * 1000L, en = st + e.optLong("d") * 1000L;
                if (st > now || en <= now) continue;
                Now n = new Now();
                n.channel = names.get(sid);
                n.title = e.optString("t");
                n.synopsis = e.optString("sy", "");
                n.start = st;
                n.end = en;
                String uuid = e.optString("programmeuuid", "");
                n.image = uuid.isEmpty() ? null : "https://images.metadata.sky.com/pd-image/" + uuid + "/16-9/640";
                n.logo = "https://d2n0069hmnqmmx.cloudfront.net/epgdata/1.0/newchanlogos/600/600/skychb" + sid + ".png";
                if (i + 1 < list.size()) {
                    n.next = list.get(i + 1).optString("t");
                    n.nextStart = list.get(i + 1).optLong("st") * 1000L;
                }
                out.add(n);
                break;
            }
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
