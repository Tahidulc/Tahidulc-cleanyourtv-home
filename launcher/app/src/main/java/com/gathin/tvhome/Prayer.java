package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prayer times from the family's mosque, Darus Salam in Manchester: its year timetable (the page's table has every day
 * of the year, each prayer's beginning and its jamā'ah, and sunrise) read once a day with the mosque's own Islamic date
 * from the top of the page, and kept in the app's settings, so a day with no internet still has its times. The theme's
 * prayer_url names another mosque's page of the same kind ("" to go without); when no page can be read, the times
 * are worked out for where the TV is (the weather's latitude and longitude) by Aladhan's free service, with the
 * theme's prayer_method (15, as most British mosques) and prayer_school (1, Hanafi), as before, without jamā'ah.
 * On Fridays the mosque's Zuhr jamā'ah is its Jumu'ah khutbah. In Ramadan, sehri (it ends at Fajr) and iftar
 * (Maghrib). On when the theme says prayer_times.
 */
final class Prayer {
    private Prayer() { }

    /** The day's times in order: Sunrise is not a prayer, but the end of Fajr's time. */
    static final String[] NAMES = {"Fajr", "Sunrise", "Zuhr", "Asr", "Maghrib", "Isha"};
    static final String[] FIVE = {"Fajr", "Zuhr", "Asr", "Maghrib", "Isha"};
    static final String MOSQUE = "https://darussalammosque.org/prayer-times/";
    static final String MOSQUE_NAME = "Darus Salam Mosque";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.UK);
    private static final String[] MON = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] HIJRI = {"Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Awwal",
            "Jumada al-Thani", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah"};
    private static final long RETRY_MS = 30 * 60_000L;

    static boolean on(Context c) {
        return Config.themeFlag(c, "prayer_times", false);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("prayer", Context.MODE_PRIVATE);
    }

    private static String mosqueUrl(Context c) {
        return Config.themeString(c, "prayer_url", MOSQUE).trim();
    }

    /** Whose times these are, for the headings: the mosque's name, or "Manchester" when worked out. */
    static String source(Context c) {
        return mosqueDay(c, LocalDate.now()) != null ? Config.themeString(c, "prayer_mosque", MOSQUE_NAME) : "Worked out for Manchester";
    }

    /** The mosque's page once a day (and Aladhan for any day it has no times for); off the main thread. */
    static void refresh(Context c) {
        if (!on(c)) return;
        SharedPreferences p = prefs(c);
        LocalDate today = LocalDate.now();
        String url = mosqueUrl(c);
        if (!url.isEmpty() && !today.toString().equals(p.getString("mosque_read", ""))
                && System.currentTimeMillis() - p.getLong("mosque_tried", 0) > RETRY_MS) {
            p.edit().putLong("mosque_tried", System.currentTimeMillis()).apply();
            try {
                readMosque(c, fetch(url), today);
            } catch (Exception ignored) {
                // the kept year still serves; tried again in half an hour
            }
        }
        // Aladhan only for a day the mosque's table does not have (another year, no page ever read).
        String lat = c.getString(R.string.weather_latitude), lon = c.getString(R.string.weather_longitude);
        SharedPreferences.Editor e = p.edit();
        boolean changed = false;
        for (LocalDate d : new LocalDate[]{today, today.plusDays(1)}) {
            String k = d.toString();
            if (mosqueDay(c, d) != null || p.contains(k) || lat.isEmpty() || lon.isEmpty()) continue;
            try {
                String u = "https://api.aladhan.com/v1/timings/" + d.format(DAY) + "?latitude=" + lat + "&longitude=" + lon
                        + "&method=" + Config.themeString(c, "prayer_method", "15") + "&school=" + Config.themeString(c, "prayer_school", "1");
                JSONObject data = new JSONObject(HomeV2.get(u)).getJSONObject("data");
                JSONObject t = data.getJSONObject("timings");
                JSONObject kept = new JSONObject();
                kept.put("Fajr", hm(t.optString("Fajr")));
                kept.put("Sunrise", hm(t.optString("Sunrise")));
                kept.put("Zuhr", hm(t.optString("Dhuhr")));
                kept.put("Asr", hm(t.optString("Asr")));
                kept.put("Maghrib", hm(t.optString("Maghrib")));
                kept.put("Isha", hm(t.optString("Isha")));
                JSONObject hijri = data.getJSONObject("date").getJSONObject("hijri");
                kept.put("hMonth", hijri.getJSONObject("month").optInt("number"));
                kept.put("hDay", hijri.optString("day"));
                kept.put("hYear", hijri.optString("year"));
                e.putString(k, kept.toString());
                changed = true;
            } catch (Exception ignored) {
                // no timetable today: tried again next time
            }
        }
        // Old days go (only the dated ones: the mosque's year stays).
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

    private static String fetch(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(8000);
        h.setReadTimeout(20000);
        h.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) TVHome");
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 2_000_000) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            h.disconnect();
        }
    }

    private static final Pattern ROW = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern CELL = Pattern.compile("<td[^>]*>\\s*([^<]*?)\\s*</td>", Pattern.DOTALL);
    private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d{2}");
    private static final Pattern DATE = Pattern.compile("([A-Z][a-z]{2})\\s+(\\d{1,2})");
    private static final Pattern ISLAMIC = Pattern.compile("(\\d{1,2})(?:st|nd|rd|th)?\\s+([A-Za-z'‘’\\- ]{3,30}?),?\\s+(1[3-5]\\d\\d)");

    /**
     * The table: each row a date ("Nov 03") then eleven times (Fajr begins and jamā'ah, sunrise, then begins and
     * jamā'ah for Zuhr, Asr, Maghrib and Isha). Kept only when most of the year is there, so a changed page does not
     * wipe a good year.
     */
    static void readMosque(Context c, String html, LocalDate today) throws Exception {
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
                if (!t.find()) { ok = false; break; }
                String s = t.group();
                times.put(s.length() == 4 ? "0" + s : s);
            }
            if (ok) days.put(String.format(Locale.UK, "%02d-%02d", month, Integer.parseInt(d.group(2))), times);
        }
        if (days.length() < 300) throw new IllegalStateException("no year table");
        SharedPreferences.Editor e = prefs(c).edit();
        e.putString("mosque_year", new JSONObject().put("year", today.getYear()).put("days", days).toString());
        e.putString("mosque_read", today.toString());
        // The mosque's own Islamic date, at the top of the page ("23th Jumada-Al-Awaal, 1448").
        String text = html.replaceAll("<[^>]+>", " ").replaceAll("&[#a-z0-9]+;", " ");
        Matcher m = ISLAMIC.matcher(text);
        while (m.find()) {
            int hm = hijriMonth(m.group(2));
            if (hm == 0) continue;
            e.putString("mosque_hijri", new JSONObject().put("date", today.toString()).put("d", Integer.parseInt(m.group(1)))
                    .put("m", hm).put("y", m.group(3)).toString());
            break;
        }
        e.apply();
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

    /** The mosque's eleven times for a day of the kept year; null when not kept. */
    private static String yearRaw;
    private static JSONObject yearParsed;

    private static JSONArray mosqueDay(Context c, LocalDate d) {
        try {
            String s = prefs(c).getString("mosque_year", null);
            if (s == null) return null;
            JSONObject y;
            synchronized (Prayer.class) {
                // The year is parsed once (the arc asks many times a minute), again only when a new one is kept.
                if (!s.equals(yearRaw)) { yearParsed = new JSONObject(s); yearRaw = s; }
                y = yearParsed;
            }
            if (y.optInt("year") != d.getYear()) return null;
            JSONArray t = y.getJSONObject("days").optJSONArray(String.format(Locale.UK, "%02d-%02d", d.getMonthValue(), d.getDayOfMonth()));
            return t != null && t.length() == 11 ? t : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** One prayer on one day: when its time begins, its jamā'ah at the mosque (null when worked out) and when it ends. */
    static final class Slot {
        String name;
        LocalDateTime begins, jamaah, ends;
        /** Friday's Zuhr: the jamā'ah is the khutbah. */
        boolean jumuah;

        String label() {
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

    /** {Fajr, Sunrise, Zuhr, Asr, Maghrib, Isha} beginnings and {Fajr, Zuhr, Asr, Maghrib, Isha} jamā'ah; null unknown. */
    private static String[][] times(Context c, LocalDate d) {
        JSONArray m = mosqueDay(c, d);
        if (m != null) {
            String[] begins = {m.optString(0), m.optString(2), m.optString(3), m.optString(5), m.optString(7), m.optString(9)};
            String[] jamaah = {m.optString(1), m.optString(4), m.optString(6), m.optString(8), m.optString(10)};
            return new String[][]{begins, jamaah};
        }
        try {
            String s = prefs(c).getString(d.toString(), null);
            if (s == null) return null;
            JSONObject t = new JSONObject(s);
            String zuhr = t.has("Zuhr") ? t.optString("Zuhr") : t.optString("Dhuhr");
            return new String[][]{{t.optString("Fajr"), t.optString("Sunrise"), zuhr, t.optString("Asr"), t.optString("Maghrib"), t.optString("Isha")}, null};
        } catch (Exception e) {
            return null;
        }
    }

    /** The five prayers of a day, each with its end (Fajr at sunrise, Isha at the next Fajr or midnight); empty unknown. */
    static List<Slot> slots(Context c, LocalDate d) {
        List<Slot> out = new ArrayList<>();
        String[][] t = times(c, d);
        if (t == null) return out;
        String[][] tomorrow = times(c, d.plusDays(1));
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

    /** Today's sunrise; null unknown. */
    static LocalDateTime sunrise(Context c, LocalDate d) {
        String[][] t = times(c, d);
        return t == null ? null : at(d, t[0][1]);
    }

    /** Whether the mosque's jamā'ah times are known (the worked-out times have none). */
    static boolean hasJamaah(Context c) {
        return mosqueDay(c, LocalDate.now()) != null;
    }

    /** Today's times for the strips: {name, begins, jamā'ah or ""}, Sunrise included; empty when not read yet. */
    static List<String[]> today(Context c) {
        List<String[]> out = new ArrayList<>();
        LocalDate d = LocalDate.now();
        List<Slot> s = slots(c, d);
        if (s.isEmpty()) return out;
        LocalDateTime rise = sunrise(c, d);
        for (Slot x : s) {
            out.add(new String[]{x.label(), x.begins.toLocalTime().toString(), x.jamaah == null ? "" : x.jamaah.toLocalTime().toString()});
            if ("Fajr".equals(x.name) && rise != null) out.add(new String[]{"Sunrise", rise.toLocalTime().toString(), ""});
        }
        return out;
    }

    /**
     * What to show now: the prayer whose time has begun but whose jamā'ah is still to come, else the next one to
     * begin (tomorrow's Fajr after Isha's jamā'ah); null unknown.
     */
    static Slot next(Context c) {
        LocalDateTime now = LocalDateTime.now();
        for (int add = 0; add < 2; add++) {
            for (Slot s : slots(c, now.toLocalDate().plusDays(add))) {
                if (s.begins.isAfter(now)) return s;
                if (s.jamaah != null && s.jamaah.isAfter(now)) return s;
            }
        }
        return null;
    }

    /** The prayer whose time it is now (begun, not yet ended); null between Fajr's end at sunrise and Zuhr, or unknown. */
    static Slot current(Context c) {
        LocalDateTime now = LocalDateTime.now();
        for (int add = -1; add < 1; add++) {
            for (Slot s : slots(c, now.toLocalDate().plusDays(add))) {
                if (!s.begins.isAfter(now) && s.ends != null && s.ends.isAfter(now)) return s;
            }
        }
        return null;
    }

    static long minutesTo(LocalDateTime t) {
        return Duration.between(LocalDateTime.now(), t).toMinutes() + 1;
    }

    /** "Asr begins in 1h 20m" or "Asr jamā'ah 14:45 · in 25m" (Jumu'ah: "khutbah"), for headings and bars. */
    static String nextLine(Context c) {
        Slot n = next(c);
        if (n == null) return null;
        LocalDateTime now = LocalDateTime.now();
        if (!n.begins.isAfter(now) && n.jamaah != null) {
            return n.label() + (n.jumuah ? " khutbah " : " jamā'ah ") + hm(n.jamaah) + " · in " + until(minutesTo(n.jamaah));
        }
        return n.label() + " " + hm(n.begins) + " · in " + until(minutesTo(n.begins));
    }

    static String hm(LocalDateTime t) {
        return t == null ? "" : String.format(Locale.UK, "%02d:%02d", t.getHour(), t.getMinute());
    }

    /** Today's Islamic date, the mosque's when it has been read today (else yesterday's plus one, or Aladhan's). */
    private static int[] hijriToday(Context c) {
        LocalDate today = LocalDate.now();
        try {
            String s = prefs(c).getString("mosque_hijri", null);
            if (s != null) {
                JSONObject h = new JSONObject(s);
                LocalDate read = LocalDate.parse(h.optString("date"));
                int day = h.optInt("d");
                if (read.equals(today)) return new int[]{day, h.optInt("m"), Integer.parseInt(h.optString("y"))};
                if (read.plusDays(1).equals(today) && day < 29) return new int[]{day + 1, h.optInt("m"), Integer.parseInt(h.optString("y"))};
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

    /** Ramadan today (by the Islamic date), and its day. */
    static int ramadanDay(Context c) {
        int[] h = hijriToday(c);
        return h != null && h[1] == 9 ? h[0] : 0;
    }

    /**
     * In Ramadan: {"Iftar" or "Sehri ends", "HH:mm", minutes until} — iftar from Fajr until Maghrib, sehri (Fajr,
     * tomorrow's after iftar) otherwise; null outside Ramadan.
     */
    static Object[] ramadan(Context c) {
        if (ramadanDay(c) == 0) return null;
        LocalDateTime now = LocalDateTime.now();
        List<Slot> s = slots(c, now.toLocalDate());
        if (s.size() < 5) return null;
        LocalDateTime fajr = s.get(0).begins, maghrib = s.get(3).begins;
        if (now.isBefore(fajr)) return new Object[]{"Sehri ends", hm(fajr), minutesTo(fajr)};
        if (now.isBefore(maghrib)) return new Object[]{"Iftar", hm(maghrib), minutesTo(maghrib)};
        List<Slot> n = slots(c, now.toLocalDate().plusDays(1));
        return n.isEmpty() ? null : new Object[]{"Sehri ends", hm(n.get(0).begins), minutesTo(n.get(0).begins)};
    }

    /** "2h 14m", "45m". */
    static String until(long minutes) {
        long m = Math.max(0, minutes);
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }

    /**
     * The Control Centre's heading for the section: in Ramadan "RAMADAN · DAY 12 · IFTAR 16:33", else whose times they
     * are and the Islamic date, "DARUS SALAM MOSQUE · 23 JUMADA AL-AWWAL 1448"; null when off or not read yet.
     */
    static String heading(Context c) {
        if (!on(c) || slots(c, LocalDate.now()).isEmpty()) return null;
        Object[] r = ramadan(c);
        if (r != null) return ("Ramadan · day " + ramadanDay(c) + " · " + r[0] + " " + r[1]).toUpperCase(Locale.UK);
        String h = hijri(c);
        return (source(c) + (h == null ? "" : " · " + h)).toUpperCase(Locale.UK);
    }

    /** The Islamic date: "23 Jumada al-Awwal 1448"; null unknown. */
    static String hijri(Context c) {
        int[] h = hijriToday(c);
        return h == null || h[1] < 1 || h[1] > 12 ? null : h[0] + " " + HIJRI[h[1] - 1] + " " + h[2];
    }
}
