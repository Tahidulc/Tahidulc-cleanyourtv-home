package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Two cards around a game the family follows, each slid out beside the scoreboard over the channel when it is up
 * (ScoreCard.show hands it to BoardCover), else a pop-up over the app or Home's bar as every card is:
 *
 * <ul>
 *   <li>At full time, FotMob's player ratings: the three best with their pictures, the player of the match first,
 *   then every other rating of note.</li>
 *   <li>At kick-off and at half time, the weather at the ground: its temperature and sky, the wind, the chance of rain
 *   by full time and how cold it will be by then (Open-Meteo, the ground's town found once by its name).</li>
 * </ul>
 *
 * Built off the main thread (the alerts' card thread), shown on it.
 */
final class MatchExtras {
    private MatchExtras() { }

    private static Handler main;

    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    // ---- the switches ------------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("match_extras", Context.MODE_PRIVATE);
    }

    /** FotMob's player ratings at full time (on unless switched off on the Scoreboard card). */
    static boolean ratingsOn(Context c) {
        return prefs(c).getBoolean("ratings", true);
    }

    static void setRatingsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("ratings", on).apply();
    }

    /** The weather at the ground at kick-off and half time (on unless switched off). */
    static boolean weatherOn(Context c) {
        return prefs(c).getBoolean("weather", true);
    }

    static void setWeatherOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("weather", on).apply();
    }

    // ---- the ratings at full time ---------------------------------------------------------------------------------------

    /** Full time's ratings card for g (off the main thread), shown once; nothing when FotMob has no ratings for it. */
    static void ratings(GoalWatchService s, ScoresTicker.Game g) {
        if (s == null || g == null || g.id == null || !ratingsOn(s) || !BoardCover.fotmobOn(s)) return;
        SharedPreferences pr = prefs(s);
        if (pr.getBoolean("r:" + g.id, false)) return;
        pr.edit().putBoolean("r:" + g.id, true).apply();
        FotMob.Match m = FotMob.forGame(g.id, g.homeName, g.awayName, g.kickoff);
        List<String[]> top = FotMob.topRated(m, 9);
        if (top.size() < 3) {
            AlertLog.note(s, "ratings: none from FotMob for " + AlertLog.game(g));
            return;
        }
        ScoreCard.Data d = s.footballData(g);
        d.kind = "ratings";
        d.notify = "ft";
        d.key = "RT|" + g.id;
        d.minute = "FT";
        d.watch = null;
        d.channel = null;
        d.channelLogo = null;
        d.label = "PLAYER";
        d.labelSmall = "RATINGS";
        d.labelColour = 0xFF0E7A3E;
        String potm = m.potm != null && !m.potm[0].isEmpty() ? m.potm[0] : top.get(0)[0];
        Bitmap[] photos = new Bitmap[3];
        for (int i = 0; i < 3; i++) {
            String[] p = top.get(i);
            boolean home = "1".equals(p[3]);
            String team = home ? g.homeName : g.awayName;
            Bitmap b = GoalWatchService.photoFor(p[0], team, home ? g.homeColor : g.awayColor);
            photos[i] = b;
            Bitmap crest = home ? d.homeCrest : d.awayCrest;
            d.podium.add(new Object[]{String.valueOf(i + 1), b, crest, surname(p[0]), p[1] + (p[0].equals(potm) ? " · Player of the match" : "")});
        }
        d.headline = ratingsHeadline(top, potm);
        d.line = othersLine(top, 3, 6);
        // Beside the board: the three best in turn, each with his picture, then the rest of note.
        d.boardPages = ratingPages(top, potm);
        d.boardPhotos = new Bitmap[]{photos[0], photos[1], photos[2], null};
        d.showMs = 14_000L;
        AlertLog.note(s, "ratings: " + AlertLog.game(g) + ", " + d.headline);
        main().post(() -> ScoreCard.show(s, d));
    }

    /** Pure: "Ødegaard 8.5 · Player of the match" for the best rated (the player of the match said where he is it). */
    static String ratingsHeadline(List<String[]> top, String potm) {
        if (top == null || top.isEmpty()) return "";
        String[] first = top.get(0);
        return surname(first[0]) + " " + first[1] + (first[0].equals(potm) ? " · Player of the match" : " · best rated");
    }

    /** Pure: the ratings after the first `from`, up to `most` of them: "Raya 8.2 · Madueke 8.2 · Tzolakis 8.0". */
    static String othersLine(List<String[]> top, int from, int most) {
        StringBuilder b = new StringBuilder();
        for (int i = from; top != null && i < top.size() && i < from + most; i++) {
            if (b.length() > 0) b.append(" · ");
            b.append(surname(top.get(i)[0])).append(' ').append(top.get(i)[1]);
        }
        return b.toString();
    }

    /** Pure: the board's pages: the three best ("1 Ødegaard", "8.5 · Player of the match"), then the rest of note. */
    static String[][] ratingPages(List<String[]> top, String potm) {
        List<String[]> pages = new ArrayList<>();
        for (int i = 0; i < 3 && i < top.size(); i++) {
            String[] p = top.get(i);
            pages.add(new String[]{(i + 1) + "  " + surname(p[0]), p[1] + (p[0].equals(potm) ? " · Player of the match" : "")});
        }
        String rest = othersLine(top, 3, 6);
        if (!rest.isEmpty()) pages.add(new String[]{"Also", rest});
        return pages.toArray(new String[0][]);
    }

    private static String surname(String full) {
        String f = full == null ? "" : full.trim();
        int sp = f.lastIndexOf(' ');
        return sp < 0 ? f : f.substring(sp + 1);
    }

    // ---- the weather at the ground ---------------------------------------------------------------------------------------

    /** The ground's weather card for g at its kick-off ("kickoff") or half time ("ht"), once each (off the main thread). */
    static void weather(GoalWatchService s, ScoresTicker.Game g, String moment) {
        if (s == null || g == null || g.id == null || !weatherOn(s)) return;
        String key = "w:" + g.id + ":" + moment;
        SharedPreferences p = prefs(s);
        if (p.getBoolean(key, false)) return;
        p.edit().putBoolean(key, true).apply();
        double[] at = place(s, g.venueCity, g.venueCountry);
        if (at == null) {
            AlertLog.note(s, "ground's weather: no place for " + AlertLog.game(g) + " (" + g.venueCity + ")");
            return;
        }
        Object[] w = read(at[0], at[1], "ht".equals(moment) ? 1 : 2);
        if (w == null) {
            AlertLog.note(s, "ground's weather: Open-Meteo did not answer for " + AlertLog.game(g));
            return;
        }
        ScoreCard.Data d = s.footballData(g);
        d.kind = "weather";
        d.notify = "ht".equals(moment) ? "ht" : "kickoff";
        d.key = "WX|" + g.id + "|" + moment;
        d.watch = null;
        d.channel = null;
        d.channelLogo = null;
        d.label = "AT THE";
        d.labelSmall = "GROUND";
        d.labelColour = 0xFF2B5FB8;
        String ground = g.venue != null ? g.venue : g.venueCity;
        double temp = (Double) w[0], wind = (Double) w[2], later = (Double) w[4];
        int code = (Integer) w[1], rain = (Integer) w[3];
        d.headline = weatherHeadline(temp, WeatherLine.describe(code), ground);
        d.line = weatherLine(wind, (String) w[5], rain, later, "ht".equals(moment));
        String wind1 = "Wind " + Math.round(wind) + " mph" + (((String) w[5]).isEmpty() ? "" : " " + w[5]);
        int cut = d.line.indexOf(" · ");
        d.boardPages = new String[][]{{weatherHead(temp, WeatherLine.describe(code)), ground == null ? "" : ground},
                {wind1, cut > 0 ? d.line.substring(cut + 3) : ""}};
        d.showMs = 10_000L;
        AlertLog.note(s, "ground's weather (" + moment + "): " + AlertLog.game(g) + ", " + d.headline + " · " + d.line);
        main().post(() -> ScoreCard.show(s, d));
    }

    /**
     * The sky over the ground now for the board's weather touch (BoardFx.skyOfWmo: Open-Meteo's weather code, day or
     * night, the wind), where FotMob gives no weather for the game; null when it can't be read (io thread).
     */
    static com.gathin.broadcast.BoardFx.Sky skyNow(Context c, ScoresTicker.Game g) {
        if (c == null || g == null) return null;
        try {
            double[] at = place(c, g.venueCity, g.venueCountry);
            if (at == null) return null;
            JSONObject json = new JSONObject(ScoresTicker.get("https://api.open-meteo.com/v1/forecast?latitude=" + at[0] + "&longitude=" + at[1]
                    + "&current=weather_code,wind_speed_10m,is_day&wind_speed_unit=mph&timezone=auto"));
            JSONObject now = json.getJSONObject("current");
            return com.gathin.broadcast.BoardFx.skyOfWmo(now.optInt("weather_code"), now.optInt("is_day", 1) == 1,
                    now.optDouble("wind_speed_10m", 0));
        } catch (Exception e) {
            return null;
        }
    }

    /** The ground's weather now as the kick-off strap says it ("9° · light rain"), "" when it can't be read (io thread). */
    static String weatherNow(Context c, ScoresTicker.Game g) {
        if (c == null || g == null) return "";
        try {
            double[] at = place(c, g.venueCity, g.venueCountry);
            Object[] w = at == null ? null : read(at[0], at[1], 2);
            if (w == null) return "";
            return weatherHead((Double) w[0], WeatherLine.describe((Integer) w[1]).toLowerCase(java.util.Locale.UK));
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * The ground's weather for the ad-break screen: {"9° · light rain", "Wind 14 mph SW · Dry to full time"}; null when it
     * cannot be read (io thread).
     */
    static String[] weatherParts(Context c, ScoresTicker.Game g) {
        if (c == null || g == null) return null;
        try {
            double[] at = place(c, g.venueCity, g.venueCountry);
            long toFt = Math.max(0, g.kickoff + 115 * 60_000L - System.currentTimeMillis());
            Object[] w = at == null ? null : read(at[0], at[1], (int) Math.max(2, toFt / 3_600_000L + 1));
            if (w == null) return null;
            String head = weatherHead((Double) w[0], WeatherLine.describe((Integer) w[1]));
            String line = weatherLine((Double) w[2], (String) w[5], (Integer) w[3], (Double) w[4], false);
            return new String[]{head, line};
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Pure: "9° · Light rain at the Emirates Stadium" (the ground left off when not known). */
    static String weatherHeadline(double temp, String sky, String ground) {
        return weatherHead(temp, sky) + (ground == null || ground.isEmpty() ? "" : " at " + ground);
    }

    /** Pure: "9° · Light rain". */
    static String weatherHead(double temp, String sky) {
        return Math.round(temp) + "° · " + (sky == null || sky.isEmpty() ? "" : sky);
    }

    /**
     * Pure: "Wind 14 mph SW · Rain 80% by full time · 6° at full time": the rain's chance only from 20%, the
     * temperature by full time only when it will be two degrees or more apart from now.
     */
    static String weatherLine(double wind, String from, int rainChance, double laterTemp, boolean halfTime) {
        StringBuilder b = new StringBuilder("Wind " + Math.round(wind) + " mph" + (from == null || from.isEmpty() ? "" : " " + from));
        if (rainChance >= 20) b.append(" · Rain ").append(rainChance).append("% by full time");
        else b.append(" · Dry to full time");
        if (!Double.isNaN(laterTemp)) b.append(" · ").append(Math.round(laterTemp)).append("° at full time");
        return b.toString();
    }

    /** Pure: a wind's direction (degrees it comes from) as a compass point ("SW"). */
    static String compass(double degrees) {
        String[] p = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int i = (int) Math.round(((degrees % 360) + 360) % 360 / 45.0) % 8;
        return p[i];
    }

    /** The ground's town found by its name, once (kept): {latitude, longitude}; null when it cannot be found. */
    private static double[] place(Context c, String city, String country) {
        if (city == null || city.trim().isEmpty()) return null;
        SharedPreferences p = prefs(c);
        String kept = p.getString("at:" + city, null);
        if (kept != null) {
            String[] f = kept.split(",");
            try {
                return new double[]{Double.parseDouble(f[0]), Double.parseDouble(f[1])};
            } catch (RuntimeException e) {
                return null;
            }
        }
        try {
            String cc = countryCode(country);
            JSONObject json = new JSONObject(ScoresTicker.get("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                    + java.net.URLEncoder.encode(city.trim(), "UTF-8") + (cc.isEmpty() ? "" : "&countryCode=" + cc)));
            JSONArray r = json.optJSONArray("results");
            if (r == null || r.length() == 0) return null;
            double lat = r.getJSONObject(0).getDouble("latitude"), lon = r.getJSONObject(0).getDouble("longitude");
            p.edit().putString("at:" + city, lat + "," + lon).apply();
            return new double[]{lat, lon};
        } catch (Exception e) {
            return null;
        }
    }

    /** Pure: a country as Open-Meteo's search narrows by it ("England": GB); "" when not one it knows here. */
    static String countryCode(String country) {
        switch (country == null ? "" : country.trim().toLowerCase(Locale.ROOT)) {
            case "england": case "scotland": case "wales": case "northern ireland": case "united kingdom": return "GB";
            case "spain": return "ES";
            case "italy": return "IT";
            case "germany": return "DE";
            case "france": return "FR";
            case "netherlands": return "NL";
            case "portugal": return "PT";
            case "ireland": return "IE";
            default: return "";
        }
    }

    /**
     * The weather there now and by full time (hoursToFt ahead): {temperature, weather code, wind mph, rain chance by
     * then (the most of the hours to come), the temperature then, the wind's direction}; null when it did not answer.
     */
    private static Object[] read(double lat, double lon, int hoursToFt) {
        try {
            JSONObject json = new JSONObject(ScoresTicker.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m"
                    + "&hourly=temperature_2m,precipitation_probability&forecast_hours=" + (hoursToFt + 1)
                    + "&wind_speed_unit=mph&timezone=auto"));
            JSONObject now = json.getJSONObject("current");
            JSONObject hourly = json.optJSONObject("hourly");
            int rain = 0;
            double later = Double.NaN;
            if (hourly != null) {
                JSONArray pr = hourly.optJSONArray("precipitation_probability"), t = hourly.optJSONArray("temperature_2m");
                for (int i = 0; pr != null && i < pr.length(); i++) if (!pr.isNull(i)) rain = Math.max(rain, pr.getInt(i));
                if (t != null && t.length() > 0 && !t.isNull(t.length() - 1)) later = t.getDouble(t.length() - 1);
            }
            double temp = now.getDouble("temperature_2m");
            if (!Double.isNaN(later) && Math.abs(later - temp) < 2) later = Double.NaN;
            return new Object[]{temp, now.optInt("weather_code"), now.optDouble("wind_speed_10m", 0), rain, later,
                    compass(now.optDouble("wind_direction_10m", 0))};
        } catch (Exception e) {
            return null;
        }
    }
}
