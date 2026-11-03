package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prayer times where the TV is (the weather's latitude and longitude, Manchester here), from Aladhan's free timetable
 * service, for the day and the next: Fajr, Sunrise, Dhuhr, Asr, Maghrib and Isha, and the Islamic date. In Ramadan,
 * sehri (it ends at Fajr) and iftar (Maghrib) with how long until each, in the Control Centre's own section and on
 * Ambient mode's bar. On when the theme says prayer_times; the
 * method and school are the theme's prayer_method (15, Moonsighting Committee, as most British mosques) and
 * prayer_school (1, Hanafi, for Asr). Read at most once a day for each date, kept in the app's settings.
 */
final class Prayer {
    private Prayer() { }

    static final String[] NAMES = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.UK);

    static boolean on(Context c) {
        return Config.themeFlag(c, "prayer_times", false);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("prayer", Context.MODE_PRIVATE);
    }

    /** Today's and tomorrow's timetables, read when not already kept (off the main thread). */
    static void refresh(Context c) {
        if (!on(c)) return;
        String lat = c.getString(R.string.weather_latitude), lon = c.getString(R.string.weather_longitude);
        if (lat.isEmpty() || lon.isEmpty()) return;
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        boolean changed = false;
        LocalDate today = LocalDate.now();
        for (LocalDate d : new LocalDate[]{today, today.plusDays(1)}) {
            String k = d.toString();
            if (p.contains(k)) continue;
            try {
                String url = "https://api.aladhan.com/v1/timings/" + d.format(DAY) + "?latitude=" + lat + "&longitude=" + lon
                        + "&method=" + Config.themeString(c, "prayer_method", "15") + "&school=" + Config.themeString(c, "prayer_school", "1");
                JSONObject data = new JSONObject(HomeV2.get(url)).getJSONObject("data");
                JSONObject t = data.getJSONObject("timings");
                JSONObject kept = new JSONObject();
                for (String n : NAMES) kept.put(n, t.optString(n).split(" ")[0]);
                kept.put("Imsak", t.optString("Imsak").split(" ")[0]);
                JSONObject hijri = data.getJSONObject("date").getJSONObject("hijri");
                kept.put("hMonth", hijri.getJSONObject("month").optInt("number"));
                kept.put("hDay", hijri.optString("day"));
                kept.put("hName", hijri.getJSONObject("month").optString("en"));
                kept.put("hYear", hijri.optString("year"));
                e.putString(k, kept.toString());
                changed = true;
            } catch (Exception ignored) {
                // no timetable today: tried again next time
            }
        }
        // Old days go.
        for (String k : p.getAll().keySet()) {
            if (k.compareTo(today.minusDays(1).toString()) < 0) { e.remove(k); changed = true; }
        }
        if (changed) e.apply();
    }

    private static JSONObject day(Context c, LocalDate d) {
        try {
            String s = prefs(c).getString(d.toString(), null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Today's times: {name, "HH:mm"}, Sunrise included; empty when not read yet. */
    static List<String[]> today(Context c) {
        List<String[]> out = new ArrayList<>();
        JSONObject d = day(c, LocalDate.now());
        if (d == null) return out;
        for (String n : NAMES) out.add(new String[]{n, d.optString(n)});
        return out;
    }

    private static LocalDateTime at(LocalDate d, String hm) {
        try {
            return LocalDateTime.of(d, LocalTime.parse(hm));
        } catch (Exception e) {
            return null;
        }
    }

    /** The next prayer (Sunrise skipped): {name, "HH:mm", minutes until}; tomorrow's Fajr after Isha; null unknown. */
    static Object[] next(Context c) {
        LocalDateTime now = LocalDateTime.now();
        for (int add = 0; add < 2; add++) {
            LocalDate d = now.toLocalDate().plusDays(add);
            JSONObject t = day(c, d);
            if (t == null) return null;
            for (String n : NAMES) {
                if ("Sunrise".equals(n)) continue;
                LocalDateTime when = at(d, t.optString(n));
                if (when != null && when.isAfter(now)) return new Object[]{n, t.optString(n), Duration.between(now, when).toMinutes()};
            }
        }
        return null;
    }

    /** Ramadan today (by the Islamic date), and its day. */
    static int ramadanDay(Context c) {
        JSONObject t = day(c, LocalDate.now());
        if (t == null || t.optInt("hMonth") != 9) return 0;
        try {
            return Integer.parseInt(t.optString("hDay"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * In Ramadan: {"Iftar" or "Sehri ends", "HH:mm", minutes until} — iftar from Fajr until Maghrib, sehri (Fajr,
     * tomorrow's after iftar) otherwise; null outside Ramadan.
     */
    static Object[] ramadan(Context c) {
        if (ramadanDay(c) == 0) return null;
        LocalDateTime now = LocalDateTime.now();
        JSONObject t = day(c, now.toLocalDate());
        LocalDateTime fajr = at(now.toLocalDate(), t.optString("Fajr")), maghrib = at(now.toLocalDate(), t.optString("Maghrib"));
        if (fajr == null || maghrib == null) return null;
        if (now.isBefore(fajr)) return new Object[]{"Sehri ends", t.optString("Fajr"), Duration.between(now, fajr).toMinutes()};
        if (now.isBefore(maghrib)) return new Object[]{"Iftar", t.optString("Maghrib"), Duration.between(now, maghrib).toMinutes()};
        JSONObject n = day(c, now.toLocalDate().plusDays(1));
        LocalDateTime f2 = n == null ? null : at(now.toLocalDate().plusDays(1), n.optString("Fajr"));
        return f2 == null ? null : new Object[]{"Sehri ends", n.optString("Fajr"), Duration.between(now, f2).toMinutes()};
    }

    /** "2h 14m", "45m". */
    static String until(long minutes) {
        long m = Math.max(0, minutes);
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }

    /**
     * The Control Centre's heading for the section: in Ramadan "RAMADAN · IFTAR 16:33 · IN 2H 14M", else the next
     * prayer and how long until it, "PRAYER TIMES · ASR IN 1H 20M"; null when off or not read yet.
     */
    static String heading(Context c) {
        if (!on(c) || today(c).isEmpty()) return null;
        Object[] r = ramadan(c);
        if (r != null) return ("Ramadan · " + r[0] + " " + r[1] + " · in " + until((Long) r[2])).toUpperCase(Locale.UK);
        Object[] n = next(c);
        return ("Prayer times" + (n == null ? "" : " · " + n[0] + " in " + until((Long) n[2]))).toUpperCase(Locale.UK);
    }

    /** The Islamic date: "12 Jumada al-Awwal 1448"; null unknown. */
    static String hijri(Context c) {
        JSONObject t = day(c, LocalDate.now());
        return t == null ? null : t.optString("hDay") + " " + t.optString("hName") + " " + t.optString("hYear");
    }
}
