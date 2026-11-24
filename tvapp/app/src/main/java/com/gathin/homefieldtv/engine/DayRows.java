package com.gathin.homefieldtv.engine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The day's rows of the Editorial Home, Movies and TV Shows, picked from CategorySet's rows: "the categories for all this
 * should be fluid and ever changing and the catalogue for these categories should always be changing and updated". Which
 * rows show and in what order changes with the day, the same all day long, so nothing jumps while the family browses
 * (deterministic: the day and the page are its only seeds):
 * <ul>
 *   <li>a page's first rows (Continue Watching) always first; its head rows (Top 10 Today, New This Week, Because you
 *   watched…) always, in the day's order;</li>
 *   <li>a seasonal row while it is in season (Christmas in December, Halloween in October, Valentine's in February's
 *   first fortnight, summer's blockbusters), early on the page;</li>
 *   <li>the row for the part of the day (morning, afternoon, evening, late at night), early on the page (its part as the
 *   page was made: it changes as the page is next come back to, never while browsing);</li>
 *   <li>one genre and one mood of the day, each going round all of theirs;</li>
 *   <li>and the rest taken in turn: a window of the page's other rows, a different one each day, so every row comes up
 *   within a few days; the day's choice then shuffled.</li>
 * </ul>
 * A row's titles are kept for a few hours (expiry: the day's lists less, themed ones more) and asked for again in the
 * background when the app comes back after that, or while the page is open; a themed row's titles also turn with the
 * day (turn). Pure but for the clock, which the tests set.
 */
public final class DayRows {
    private DayRows() {}

    /** How many of a page's turning rows each day takes (its genre and mood of the day besides). */
    static final int HOME_TAKE = 12, MOVIES_TAKE = 12, SHOWS_TAKE = 8;
    /** The parts of the day: from 05:00, 12:00, 17:00 and 21:00 (to 05:00). */
    public static final String[] PARTS = {"morning", "afternoon", "evening", "late"};
    private static final long HOUR = 3600_000L;

    /** Tests and previews: the time it is (null: the clock's). */
    public static volatile Long now;

    /** The time now (ms). */
    public static long now() {
        Long n = now;
        return n != null ? n : System.currentTimeMillis();
    }

    /** A time on this TV's clock. */
    public static LocalDateTime local(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    /** Pure: the part of the day a time is in ("morning" from 05:00, "afternoon" 12:00, "evening" 17:00, "late" 21:00). */
    public static String part(LocalTime t) {
        int h = t.getHour();
        if (h >= 5 && h < 12) return PARTS[0];
        if (h >= 12 && h < 17) return PARTS[1];
        if (h >= 17 && h < 21) return PARTS[2];
        return PARTS[3];
    }

    /** Pure: what the day's rows hang on: the day and its part ("2026-11-24|evening"); a page made again when it changes. */
    public static String key(LocalDateTime t) {
        return t.toLocalDate() + "|" + part(t.toLocalTime());
    }

    /** The day's rows of a page now. */
    public static List<CategorySet.Entry> today(String page) {
        return of(page, local(now()));
    }

    /** Pure: a page's rows on a day at a time, in their order (the carousel first where the page has one). */
    public static List<CategorySet.Entry> of(String page, LocalDateTime when) {
        List<CategorySet.Entry> all = CategorySet.of(page);
        LocalDate day = when.toLocalDate();
        long d = day.toEpochDay();
        Random r = new Random(d * 1_000_003L + page.hashCode());
        List<CategorySet.Entry> hero = new ArrayList<>(), first = new ArrayList<>(), heads = new ArrayList<>(), season = new ArrayList<>(),
                parts = new ArrayList<>(), turning = new ArrayList<>();
        Map<String, List<CategorySet.Entry>> groups = new LinkedHashMap<>();
        String nowPart = part(when.toLocalTime());
        for (CategorySet.Entry e : all) {
            String w = e.when;
            if (e.kind == CategorySet.HERO) hero.add(e);
            else if (w.equals("first")) first.add(e);
            else if (w.equals("head")) heads.add(e);
            else if (w.startsWith("season:")) {
                if (inSeason(w.substring(7), day)) season.add(e);
            } else if (w.startsWith("part:")) {
                if (w.substring(5).equals(nowPart)) parts.add(e);
            } else if (w.startsWith("pick:")) {
                groups.computeIfAbsent(w.substring(5), k -> new ArrayList<>()).add(e);
            } else turning.add(e);
        }
        Collections.shuffle(heads, r);
        // The rest in turn: the page's own fixed order of them (shuffled once, by the page), a window of it each day.
        List<CategorySet.Entry> today = window(turning, take(page), d, page.hashCode());
        // One of each group, going round them all (in an order of the group's own).
        for (List<CategorySet.Entry> g : groups.values()) {
            List<CategorySet.Entry> order = new ArrayList<>(g);
            Collections.shuffle(order, new Random(page.hashCode() * 31L + g.get(0).when.hashCode()));
            today.add(order.get((int) Math.floorMod(d, (long) order.size())));
        }
        Collections.shuffle(today, r);
        List<CategorySet.Entry> out = new ArrayList<>(hero);
        out.addAll(first);
        // The day's first head, then what is in season and the part of the day's row, then the other heads.
        if (!heads.isEmpty()) out.add(heads.remove(0));
        out.addAll(season);
        out.addAll(parts);
        out.addAll(heads);
        out.addAll(today);
        return out;
    }

    private static int take(String page) {
        return page.equals(CategorySet.SHOWS) ? SHOWS_TAKE : page.equals(CategorySet.MOVIES) ? MOVIES_TAKE : HOME_TAKE;
    }

    /**
     * Pure: n of the rows for a day, a window of them in an order of the page's own (seed), moved on by n each day:
     * every row comes up within size / n days, and none twice in a day.
     */
    static <T> List<T> window(List<T> rows, int n, long day, long seed) {
        List<T> order = new ArrayList<>(rows);
        Collections.shuffle(order, new Random(seed));
        List<T> out = new ArrayList<>();
        int size = order.size();
        if (size == 0) return out;
        int k = Math.min(n, size);
        int start = (int) Math.floorMod(day * k, (long) size);
        for (int i = 0; i < k; i++) out.add(order.get((start + i) % size));
        return out;
    }

    /** Pure: whether a day is in a season "MM-DD/MM-DD" (both days in it; one that runs over the new year too). */
    public static boolean inSeason(String season, LocalDate day) {
        try {
            String[] ends = season.split("/");
            int from = monthDay(ends[0]), to = monthDay(ends[1]), at = day.getMonthValue() * 100 + day.getDayOfMonth();
            return from <= to ? at >= from && at <= to : at >= from || at <= to;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int monthDay(String md) {
        String[] p = md.trim().split("-");
        return Integer.parseInt(p[0]) * 100 + Integer.parseInt(p[1]);
    }

    // ---- how long a row's titles are kept ------------------------------------------------------------------------

    /**
     * Pure: how long a row's titles are kept before they are asked for again: today's lists (trending today, a Top 10)
     * three hours; what is new, coming or on this week four; the rest (themed rows) six.
     */
    public static long expiry(String source) {
        String s = source == null ? "" : source;
        if (s.startsWith("top:") || s.contains("/day")) return 3 * HOUR;
        if (s.contains("{today") || s.contains("trending/") || s.contains("now_playing") || s.contains("on_the_air")
                || s.contains("airing_today") || s.contains("upcoming") || s.startsWith("soon:") || s.contains("|soon:")
                || s.startsWith("latest:") || s.startsWith("network:")) return 4 * HOUR;
        return 6 * HOUR;
    }

    /** Pure: whether a row made at a time is past its expiry now (a clock put back: past it too). */
    public static boolean expired(long madeAt, long now, String source) {
        return now < madeAt || now - madeAt >= expiry(source);
    }

    // ---- a themed row's titles turning with the day ----------------------------------------------------------------

    /**
     * Pure: whether a row's titles turn with the day: a themed list (TMDB's discover of a genre, a keyword, a decade, a
     * list), not one in its own order (trending, a Top 10, the newest, what's coming, the family's own lists).
     */
    public static boolean turns(String source) {
        String s = source == null ? "" : source;
        if (s.contains("sort_by=primary_release_date") || s.contains("first_air_date.desc") || s.contains("revenue.desc")) return false;
        return s.startsWith("tmdb:discover/") || s.startsWith("genre:") || s.startsWith("list:");
    }

    /** Pure: a list turned for a day: from a place of the day's among its first ones (at most `among`), round to it. */
    public static <T> List<T> turn(List<T> list, long day, int among) {
        int n = list.size(), k = Math.min(n, Math.max(1, among));
        if (n < 2) return list;
        int at = (int) Math.floorMod(day * 7L, (long) k);
        if (at == 0) return list;
        List<T> out = new ArrayList<>(n);
        out.addAll(list.subList(at, n));
        out.addAll(list.subList(0, at));
        return out;
    }

    /** The day today, as turn() counts it. */
    public static long day() {
        return local(now()).toLocalDate().toEpochDay();
    }
}
