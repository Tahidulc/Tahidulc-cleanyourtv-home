package com.gathin.tvhomephone;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.tvhomephone.sports.Weather;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prayer times from the family's mosque, Darus Salam in Manchester, as the TVs have them: its year timetable (every
 * day's beginnings and jamā'ah, and sunrise) read once a day with the mosque's own Islamic date, and kept, so a day
 * with no internet still has its times. When the page can't be read, the times are worked out for Manchester by
 * Aladhan's free service (method 15, Hanafi), without jamā'ah. On Fridays the Zuhr jamā'ah is the Jumu'ah khutbah; in
 * Ramadan, sehri (it ends at Fajr) and iftar (Maghrib). The sums are pure, for the tests.
 */
public final class Prayer {
    private Prayer() {}

    public static final String[] FIVE = {"Fajr", "Zuhr", "Asr", "Maghrib", "Isha"};
    static final String MOSQUE = "https://darussalammosque.org/prayer-times/";
    public static final String MOSQUE_NAME = "Darus Salam Mosque";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.UK);
    private static final String[] MON = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] HIJRI = {"Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Awwal",
            "Jumada al-Thani", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah"};
    private static final long RETRY_MS = 30 * 60_000L;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("prayer", Context.MODE_PRIVATE);
    }

    /** The mosque's page once a day (and Aladhan for a day it has no times for). Off the main thread. */
    public static void refresh(Context c) {
        SharedPreferences p = prefs(c);
        LocalDate today = LocalDate.now();
        if (!today.toString().equals(p.getString("mosque_read", "")) && System.currentTimeMillis() - p.getLong("mosque_tried", 0) > RETRY_MS) {
            p.edit().putLong("mosque_tried", System.currentTimeMillis()).apply();
            try {
                String html = Net.get(MOSQUE, Collections.singletonMap("User-Agent", "Mozilla/5.0 (Linux; Android) Homefield"), 20_000);
                JSONObject y = year(html, today.getYear());
                if (y != null) {
                    SharedPreferences.Editor e = p.edit().putString("mosque_year", y.toString()).putString("mosque_read", today.toString());
                    int[] h = hijriOf(html);
                    if (h != null) {
                        e.putString("mosque_hijri", new JSONObject().put("date", today.toString()).put("d", h[0]).put("m", h[1]).put("y", h[2]).toString());
                    }
                    e.apply();
                }
            } catch (Exception ignored) {
                // the kept year still serves; tried again in half an hour
            }
        }
        SharedPreferences.Editor e = p.edit();
        boolean changed = false;
        for (LocalDate d : new LocalDate[]{today, today.plusDays(1)}) {
            String k = d.toString();
            if (mosqueDay(c, d) != null || p.contains(k)) continue;
            try {
                String u = "https://api.aladhan.com/v1/timings/" + d.format(DAY) + "?latitude=" + Weather.LAT + "&longitude=" + Weather.LON
                        + "&method=15&school=1";
                JSONObject data = new JSONObject(Net.get(u)).getJSONObject("data");
                JSONObject t = data.getJSONObject("timings");
                JSONObject hijri = data.getJSONObject("date").getJSONObject("hijri");
                JSONObject kept = new JSONObject()
                        .put("Fajr", hm(t.optString("Fajr"))).put("Sunrise", hm(t.optString("Sunrise"))).put("Zuhr", hm(t.optString("Dhuhr")))
                        .put("Asr", hm(t.optString("Asr"))).put("Maghrib", hm(t.optString("Maghrib"))).put("Isha", hm(t.optString("Isha")))
                        .put("hMonth", hijri.getJSONObject("month").optInt("number")).put("hDay", hijri.optString("day")).put("hYear", hijri.optString("year"));
                e.putString(k, kept.toString());
                changed = true;
            } catch (Exception ignored) {
                // no times today: tried again next time
            }
        }
        for (String k : p.getAll().keySet()) {
            if (!k.isEmpty() && Character.isDigit(k.charAt(0)) && k.compareTo(today.minusDays(1).toString()) < 0) {
                e.remove(k);
                changed = true;
            }
        }
        if (changed) e.apply();
    }

    private static String hm(String s) {
        return s == null ? "" : s.split(" ")[0];
    }

    private static final Pattern ROW = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern CELL = Pattern.compile("<td[^>]*>\\s*([^<]*?)\\s*</td>", Pattern.DOTALL);
    private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d{2}");
    private static final Pattern DATE = Pattern.compile("([A-Z][a-z]{2})\\s+(\\d{1,2})");
    private static final Pattern ISLAMIC = Pattern.compile("(\\d{1,2})(?:st|nd|rd|th)?\\s+([A-Za-z'‘’\\- ]{3,30}?),?\\s+(1[3-5]\\d\\d)");

    /**
     * The mosque's year table: each row a date ("Nov 03") then eleven times (Fajr begins and jamā'ah, sunrise, then
     * begins and jamā'ah for Zuhr, Asr, Maghrib and Isha), as {"year", "days": {"11-03": [...]}}; null unless most of
     * the year is there (a changed page never wipes a good year).
     */
    static JSONObject year(String html, int year) throws Exception {
        JSONObject days = new JSONObject();
        Matcher r = ROW.matcher(html);
        while (r.find()) {
            Matcher cell = CELL.matcher(r.group(1));
            List<String> cells = new ArrayList<>();
            while (cell.find()) cells.add(cell.group(1).trim());
            if (cells.size() < 12) continue;
            Matcher d = DATE.matcher(cells.get(0));
            if (!d.find()) continue;
            int month = -1;
            for (int i = 0; i < 12; i++) if (MON[i].equalsIgnoreCase(d.group(1))) month = i + 1;
            if (month < 0) continue;
            JSONArray times = new JSONArray();
            boolean ok = true;
            for (int i = 1; i <= 11; i++) {
                Matcher t = TIME.matcher(cells.get(i));
                if (!t.find()) {
                    ok = false;
                    break;
                }
                String s = t.group();
                times.put(s.length() == 4 ? "0" + s : s);
            }
            if (ok) days.put(String.format(Locale.UK, "%02d-%02d", month, Integer.parseInt(d.group(2))), times);
        }
        return days.length() < 300 ? null : new JSONObject().put("year", year).put("days", days);
    }

    /** The mosque's Islamic date at the top of its page ("23th Jumada-Al-Awaal, 1448"): {23, 5, 1448}; null none. */
    static int[] hijriOf(String html) {
        String text = html.replaceAll("<[^>]+>", " ").replaceAll("&[#a-z0-9]+;", " ");
        Matcher m = ISLAMIC.matcher(text);
        while (m.find()) {
            int month = hijriMonth(m.group(2));
            if (month != 0) return new int[]{Integer.parseInt(m.group(1)), month, Integer.parseInt(m.group(3))};
        }
        return null;
    }

    /** "Jumada-Al-Awaal" and its other spellings: 5; 0 when not a month's name. */
    static int hijriMonth(String name) {
        String s = name.toLowerCase(Locale.UK).replaceAll("[^a-z]", "");
        boolean second = s.contains("thani") || s.contains("akhir") || s.contains("akhar") || s.contains("ukhra");
        if (s.startsWith("muharr")) return 1;
        if (s.startsWith("safar")) return 2;
        if (s.startsWith("rabi")) return second ? 4 : 3;
        if (s.startsWith("jumad") || s.startsWith("jamad")) return second ? 6 : 5;
        if (s.startsWith("rajab")) return 7;
        if (s.startsWith("sha") && s.contains("ban")) return 8;
        if (s.startsWith("rama")) return 9;
        if (s.startsWith("shaw")) return 10;
        if (s.contains("qad") || s.contains("qid")) return 11;
        if (s.contains("hij")) return 12;
        return 0;
    }

    private static String yearRaw;
    private static JSONObject yearParsed;

    /** The mosque's eleven times for a day of the kept year; null when not kept. */
    private static JSONArray mosqueDay(Context c, LocalDate d) {
        try {
            String s = prefs(c).getString("mosque_year", null);
            if (s == null) return null;
            JSONObject y;
            synchronized (Prayer.class) {
                if (!s.equals(yearRaw)) {
                    yearParsed = new JSONObject(s);
                    yearRaw = s;
                }
                y = yearParsed;
            }
            if (y.optInt("year") != d.getYear()) return null;
            JSONArray t = y.getJSONObject("days").optJSONArray(String.format(Locale.UK, "%02d-%02d", d.getMonthValue(), d.getDayOfMonth()));
            return t != null && t.length() == 11 ? t : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** {Fajr, Sunrise, Zuhr, Asr, Maghrib, Isha} beginnings and {Fajr, Zuhr, Asr, Maghrib, Isha} jamā'ah (null when worked out). */
    static String[][] times(Context c, LocalDate d) {
        JSONArray m = mosqueDay(c, d);
        if (m != null) {
            return new String[][]{{m.optString(0), m.optString(2), m.optString(3), m.optString(5), m.optString(7), m.optString(9)},
                    {m.optString(1), m.optString(4), m.optString(6), m.optString(8), m.optString(10)}};
        }
        try {
            String s = prefs(c).getString(d.toString(), null);
            if (s == null) return null;
            JSONObject t = new JSONObject(s);
            return new String[][]{{t.optString("Fajr"), t.optString("Sunrise"), t.optString("Zuhr"), t.optString("Asr"), t.optString("Maghrib"), t.optString("Isha")}, null};
        } catch (Exception e) {
            return null;
        }
    }

    /** One prayer on one day: when its time begins, its jamā'ah at the mosque (null when worked out), when it ends. */
    public static final class Slot {
        public String name;
        public LocalDateTime begins, jamaah, ends;
        /** Friday's Zuhr: the jamā'ah is the khutbah. */
        public boolean jumuah;

        public String label() {
            return jumuah ? "Jumu'ah" : name;
        }
    }

    private static LocalDateTime at(LocalDate d, String hm) {
        try {
            return hm == null || hm.isEmpty() ? null : LocalDateTime.of(d, LocalTime.parse(hm));
        } catch (Exception e) {
            return null;
        }
    }

    /** A day's five prayers from its times (and the next day's, for Isha's end); empty when they can't be told. */
    static List<Slot> slots(String[][] t, String[][] tomorrow, LocalDate d) {
        List<Slot> out = new ArrayList<>();
        if (t == null) return out;
        LocalDateTime[] b = new LocalDateTime[6];
        for (int i = 0; i < 6; i++) b[i] = at(d, t[0][i]);
        for (int i = 0; i < 5; i++) {
            Slot s = new Slot();
            s.name = FIVE[i];
            int bi = i == 0 ? 0 : i + 1;
            s.begins = b[bi];
            if (s.begins == null) return new ArrayList<>();
            s.jamaah = t[1] == null ? null : at(d, t[1][i]);
            if (i == 0) s.ends = b[1];
            else if (i < 4) s.ends = b[bi + 1];
            else s.ends = tomorrow != null ? at(d.plusDays(1), tomorrow[0][0]) : d.plusDays(1).atStartOfDay();
            s.jumuah = i == 1 && d.getDayOfWeek() == DayOfWeek.FRIDAY && s.jamaah != null;
            out.add(s);
        }
        return out;
    }

    public static List<Slot> slots(Context c, LocalDate d) {
        return slots(times(c, d), times(c, d.plusDays(1)), d);
    }

    /** Today's sunrise; null unknown. */
    public static LocalDateTime sunrise(Context c, LocalDate d) {
        String[][] t = times(c, d);
        return t == null ? null : at(d, t[0][1]);
    }

    /**
     * What's next at now: the prayer whose time has begun but whose jamā'ah is still to come, else the next to begin
     * (tomorrow's Fajr after Isha's jamā'ah); null unknown.
     */
    static Slot next(List<Slot> today, List<Slot> tomorrow, LocalDateTime now) {
        for (List<Slot> day : java.util.Arrays.asList(today, tomorrow)) {
            for (Slot s : day) {
                if (s.begins.isAfter(now)) return s;
                if (s.jamaah != null && s.jamaah.isAfter(now)) return s;
            }
        }
        return null;
    }

    public static Slot next(Context c) {
        LocalDate d = LocalDate.now();
        return next(slots(c, d), slots(c, d.plusDays(1)), LocalDateTime.now());
    }

    /** "Asr 14:12 · in 1h 20m", or "Asr jamā'ah 14:45 · in 25m" once its time has begun; null unknown. */
    static String nextLine(Slot n, LocalDateTime now) {
        if (n == null) return null;
        if (!n.begins.isAfter(now) && n.jamaah != null) {
            return n.label() + (n.jumuah ? " khutbah " : " jamā'ah ") + hm(n.jamaah) + " · in " + until(minutes(now, n.jamaah));
        }
        return n.label() + " " + hm(n.begins) + " · in " + until(minutes(now, n.begins));
    }

    public static String nextLine(Context c) {
        return nextLine(next(c), LocalDateTime.now());
    }

    private static long minutes(LocalDateTime now, LocalDateTime t) {
        return Duration.between(now, t).toMinutes() + 1;
    }

    public static String hm(LocalDateTime t) {
        return t == null ? "" : String.format(Locale.UK, "%02d:%02d", t.getHour(), t.getMinute());
    }

    /** "2h 14m", "45m". */
    public static String until(long minutes) {
        long m = Math.max(0, minutes);
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }

    /** Whose times these are: the mosque's, or worked out for Manchester. */
    public static String source(Context c) {
        return mosqueDay(c, LocalDate.now()) != null ? MOSQUE_NAME : "Worked out for Manchester";
    }

    /** Today's Islamic date, the mosque's when read today (else yesterday's plus one, or Aladhan's); null unknown. */
    private static int[] hijriToday(Context c) {
        LocalDate today = LocalDate.now();
        try {
            String s = prefs(c).getString("mosque_hijri", null);
            if (s != null) {
                JSONObject h = new JSONObject(s);
                LocalDate read = LocalDate.parse(h.optString("date"));
                int day = h.optInt("d");
                if (read.equals(today)) return new int[]{day, h.optInt("m"), h.optInt("y")};
                if (read.plusDays(1).equals(today) && day < 29) return new int[]{day + 1, h.optInt("m"), h.optInt("y")};
            }
            String a = prefs(c).getString(today.toString(), null);
            if (a != null) {
                JSONObject t = new JSONObject(a);
                return new int[]{Integer.parseInt(t.optString("hDay")), t.optInt("hMonth"), Integer.parseInt(t.optString("hYear"))};
            }
        } catch (Exception ignored) {
            // unknown
        }
        return null;
    }

    /** The Islamic date: "23 Jumada al-Awwal 1448"; null unknown. */
    public static String hijri(Context c) {
        return hijri(hijriToday(c));
    }

    static String hijri(int[] h) {
        return h == null || h[1] < 1 || h[1] > 12 ? null : h[0] + " " + HIJRI[h[1] - 1] + " " + h[2];
    }

    /** Ramadan's day today (0 outside it). */
    public static int ramadanDay(Context c) {
        int[] h = hijriToday(c);
        return h != null && h[1] == 9 ? h[0] : 0;
    }

    /** In Ramadan: {"Iftar" or "Sehri ends", "HH:mm"} next; null outside Ramadan. */
    public static String[] ramadan(Context c) {
        if (ramadanDay(c) == 0) return null;
        LocalDateTime now = LocalDateTime.now();
        List<Slot> s = slots(c, now.toLocalDate());
        if (s.size() < 5) return null;
        LocalDateTime fajr = s.get(0).begins, maghrib = s.get(3).begins;
        if (now.isBefore(fajr)) return new String[]{"Sehri ends", hm(fajr)};
        if (now.isBefore(maghrib)) return new String[]{"Iftar", hm(maghrib)};
        List<Slot> n = slots(c, now.toLocalDate().plusDays(1));
        return n.isEmpty() ? null : new String[]{"Sehri ends", hm(n.get(0).begins)};
    }
}
