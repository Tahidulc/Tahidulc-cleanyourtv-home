package com.gathin.tvhomephone.sports;

import com.gathin.tvhomephone.Net;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Manchester's weather, as the TV's top bar has it (Open-Meteo, no key): the temperature and the sky now, and the
 * chance of rain hour by hour for the rain alert.
 */
public final class Weather {
    private Weather() {}

    static final String LAT = "53.4808", LON = "-2.2426";
    private static Object[] last;

    /** {temperature °C, words, rain chance per hour from now (first six hours)}; null when Open-Meteo does not answer. */
    public static synchronized Object[] now() {
        if (last != null && System.currentTimeMillis() - (Long) last[3] < 10 * 60_000L) return last;
        try {
            JSONObject r = new JSONObject(Net.get("https://api.open-meteo.com/v1/forecast?latitude=" + LAT + "&longitude=" + LON
                    + "&current=temperature_2m,weather_code,is_day&hourly=precipitation_probability&forecast_hours=6&timezone=auto"));
            JSONObject cur = r.getJSONObject("current");
            JSONArray rain = r.getJSONObject("hourly").optJSONArray("precipitation_probability");
            int[] chance = new int[rain == null ? 0 : rain.length()];
            for (int i = 0; i < chance.length; i++) chance[i] = rain.optInt(i);
            last = new Object[]{(int) Math.round(cur.optDouble("temperature_2m")), words(cur.optInt("weather_code")), chance,
                    System.currentTimeMillis()};
            return last;
        } catch (Exception e) {
            return last;
        }
    }

    /** WMO weather codes in a word or two. */
    static String words(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 85 && code <= 86) return "Snow showers";
        if (code >= 95) return "Thunder";
        return "";
    }
}
