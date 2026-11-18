package com.gathin.tvhomephone.sports;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What an F1 driver's page says, worked out from the standings, his season and the next Grand Prix, pure for the
 * tests: the weekend's dates, the countdown, his age, the line under his name, and his season's figures (each only when
 * the source gave it: nothing is filled in).
 */
final class DriverFacts {
    private DriverFacts() {}

    /** Pure: "20 – 22 Nov", or "30 Oct – 1 Nov" across two months, in the phone's time zone; "" when not known. */
    static String dates(long start, long end, ZoneId zone) {
        if (end <= 0) return "";
        LocalDate b = Instant.ofEpochMilli(end).atZone(zone).toLocalDate();
        LocalDate a = Instant.ofEpochMilli(start > 0 ? start : end).atZone(zone).toLocalDate();
        String bm = b.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK);
        if (a.equals(b)) return b.getDayOfMonth() + " " + bm;
        if (a.getMonth() == b.getMonth()) return a.getDayOfMonth() + " – " + b.getDayOfMonth() + " " + bm;
        return a.getDayOfMonth() + " " + a.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK) + " – " + b.getDayOfMonth() + " " + bm;
    }

    /** Pure: the time to a start as {days, hours, minutes} ("04", "20", "35"), none below 0. */
    static String[] countdown(long start, long now) {
        long mins = Math.max(0, (start - now) / 60_000L);
        long d = mins / (24 * 60), hh = mins / 60 % 24, mm = mins % 60;
        return new String[]{two(d), two(hh), two(mm)};
    }

    private static String two(long v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    /** Pure: how long until the countdown next changes (the next whole minute to the start), at least a second. */
    static long untilTick(long start, long now) {
        long left = start - now;
        if (left <= 0) return 60_000L;
        long r = left % 60_000L;
        return Math.max(1000L, r == 0 ? 60_000L : r + 50);
    }

    /** Pure: a driver's age on a day from his date of birth ("1997-09-30"); -1 when not known. */
    static int age(String born, LocalDate today) {
        if (born == null || born.length() != 10) return -1;
        try {
            int y = LocalDate.parse(born).until(today).getYears();
            return y > 10 && y < 80 ? y : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Pure: the line under his name: "Dutch · Age 29", either alone, or "" when neither is known. */
    static String facts(String nationality, int age) {
        String n = nationality == null ? "" : nationality.trim();
        if (age <= 0) return n;
        return n.isEmpty() ? "Age " + age : n + "  ·  Age " + age;
    }

    /** Pure: his place as words: "1st", "22nd"; "" when not a number. */
    static String place(String position) {
        try {
            int n = Integer.parseInt(position == null ? "" : position.trim());
            return n > 0 ? TeamData.ordinal(n) : "";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /**
     * Pure: his season's figures as the page labels them, in its order: Grands Prix and Podiums from his results (when
     * read), Points from the standings (when given), each {label, value}.
     */
    static List<String[]> figures(F1.Standing st, F1.Season season) {
        List<String[]> out = new ArrayList<>();
        if (season != null && season.races >= 0) out.add(new String[]{"Grands Prix", String.valueOf(season.races)});
        String pts = st == null || st.points == null ? "" : st.points.trim();
        if (pts.matches("[0-9]+(\\.[0-9]+)?")) out.add(new String[]{"Points", pts.endsWith(".0") ? pts.substring(0, pts.length() - 2) : pts});
        if (season != null && season.podiums >= 0) out.add(new String[]{"Podiums", String.valueOf(season.podiums)});
        return out;
    }

    /** Pure: the driver's place in the list by Jolpica's id, else by name; 0 when not there. */
    static int indexOf(List<F1.Standing> drivers, String driverId, String name) {
        for (int i = 0; i < drivers.size(); i++) {
            String id = drivers.get(i).driverId;
            if (driverId != null && !driverId.isEmpty() && driverId.equals(id)) return i;
        }
        for (int i = 0; i < drivers.size(); i++) if (name != null && name.equals(drivers.get(i).name)) return i;
        return 0;
    }
}
