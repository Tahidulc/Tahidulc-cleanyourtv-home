package com.gathin.tvhome;

import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * What the weather pop-ups read: the Met Office's warnings for the region (config.xml weather_warnings_feed, kept to
 * those that name weather_warnings_area), and Open-Meteo's next two hours and today for the weather line's place.
 * Off the main thread.
 */
final class WeatherAlerts {
    /** {guid, title, description, picture url} for each warning naming the area. */
    static List<String[]> warnings(String feed, String area) {
        List<String[]> out = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(feed).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            try (InputStream in = conn.getInputStream()) {
                XmlPullParser p = Xml.newPullParser();
                p.setInput(in, null);
                String guid = null, title = null, description = null, image = null;
                boolean inItem = false;
                for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT; e = p.next()) {
                    if (e == XmlPullParser.START_TAG) {
                        String n = p.getName();
                        if ("item".equals(n)) { inItem = true; guid = title = description = image = null; }
                        else if (inItem && "title".equals(n)) title = p.nextText().trim();
                        else if (inItem && "description".equals(n)) description = p.nextText().trim();
                        else if (inItem && "guid".equals(n)) guid = p.nextText().trim();
                        else if (inItem && "enclosure".equals(n)) image = p.getAttributeValue(null, "url");
                    } else if (e == XmlPullParser.END_TAG && "item".equals(p.getName())) {
                        inItem = false;
                        if (title != null && description != null && description.contains(area)) {
                            out.add(new String[]{guid != null ? guid : title + description, title, description, image});
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            out.clear();
        } finally {
            if (conn != null) conn.disconnect();
        }
        return out;
    }

    /** The next two hours and today: when rain starts (0 if dry or already raining), and today's high, low, code, rain %. */
    static final class Outlook {
        long rainFrom;
        boolean rainingNow;
        int high, low, code, rainChance;
    }

    static Outlook outlook(String lat, String lon) {
        try {
            JSONObject json = new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&minutely_15=precipitation&forecast_minutely_15=8"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                    + "&forecast_days=1&timezone=auto&timeformat=unixtime"));
            Outlook o = new Outlook();
            JSONObject m = json.getJSONObject("minutely_15");
            JSONArray time = m.getJSONArray("time"), rain = m.getJSONArray("precipitation");
            long now = System.currentTimeMillis();
            for (int i = 0; i < time.length(); i++) {
                long at = time.getLong(i) * 1000L;
                boolean wet = !rain.isNull(i) && rain.getDouble(i) >= 0.2;
                if (at <= now && at + 15 * 60_000L > now) o.rainingNow = wet;
                if (at > now && wet && !o.rainingNow) { o.rainFrom = at; break; }
            }
            JSONObject d = json.getJSONObject("daily");
            o.high = (int) Math.round(d.getJSONArray("temperature_2m_max").getDouble(0));
            o.low = (int) Math.round(d.getJSONArray("temperature_2m_min").getDouble(0));
            o.code = d.getJSONArray("weather_code").getInt(0);
            JSONArray chance = d.optJSONArray("precipitation_probability_max");
            o.rainChance = chance == null || chance.isNull(0) ? -1 : chance.getInt(0);
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 64 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
