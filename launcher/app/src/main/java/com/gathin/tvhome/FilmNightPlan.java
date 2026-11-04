package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Film night's sums, apart from Android so the unit tests can check them: the start times offered, when a film ends
 * against bedtime, when its reminder is due, and how long pop-ups stay quiet.
 */
final class FilmNightPlan {
    private FilmNightPlan() { }

    /** The reminder comes this long before the start. */
    static final long REMIND_MS = 10 * 60_000L;
    /** Pop-ups stay quiet this long after the film's end (or its credits). */
    static final long AFTER_MS = 10 * 60_000L;
    /** An end this close to bed (or after it) is shown in amber. */
    static final int TIGHT_MIN = 10;

    /**
     * The start times offered: the next four half hours with at least five minutes to go before the first (at 19:42,
     * 20:00, 20:30, 21:00 and 21:30; at 19:57, 20:30 on).
     */
    static List<LocalDateTime> choices(LocalDateTime now) {
        LocalDateTime t = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(5);
        int m = t.getMinute();
        int up = m == 0 || m == 30 ? 0 : m < 30 ? 30 - m : 60 - m;
        t = t.plusMinutes(up);
        List<LocalDateTime> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) out.add(t.plusMinutes(30L * i));
        return out;
    }

    /**
     * Bed on the evening of a start: bedMinutes after that evening's midnight (FitsTonight.bedMinutes: 23:00 is 1380, 00:30
     * is 1470). A start in the small hours (before 05:00) belongs to the evening before.
     */
    static LocalDateTime bed(LocalDateTime start, int bedMinutes) {
        LocalDate evening = start.getHour() < 5 ? start.toLocalDate().minusDays(1) : start.toLocalDate();
        return evening.atStartOfDay().plusMinutes(bedMinutes);
    }

    /** Minutes from the film's end to bed (below 0: past bedtime). */
    static long spare(LocalDateTime start, int runtime, int bedMinutes) {
        return ChronoUnit.MINUTES.between(start.plusMinutes(runtime), bed(start, bedMinutes));
    }

    /** An end that leaves under ten minutes before bed, or runs past it. */
    static boolean tight(long spare) {
        return spare < TIGHT_MIN;
    }

    /** Minutes as said: "63 min" up to 99, then "2 h 5 min". */
    private static String length(long a) {
        return a >= 100 ? a / 60 + " h" + (a % 60 == 0 ? "" : " " + a % 60 + " min") : a + " min";
    }

    /** "63 min spare", "Right on bedtime", "2 h 5 min past bed". */
    static String spareWords(long spare) {
        if (spare == 0) return "Right on bedtime";
        String n = length(Math.abs(spare));
        return spare > 0 ? n + " spare" : n + " past bed";
    }

    /** As the reminder says it: "63 min before bed", "right on bedtime", "12 min past bed". */
    static String bedWords(long spare) {
        if (spare == 0) return "right on bedtime";
        String n = length(Math.abs(spare));
        return spare > 0 ? n + " before bed" : n + " past bed";
    }

    /** The reminder is due: ten minutes before the start (or later, up to a quarter of an hour in), not yet given. */
    static boolean remindDue(long now, long start, boolean told) {
        return !told && now >= start - REMIND_MS && now < start + 15 * 60_000L;
    }

    /**
     * Until when pop-ups wait: ten minutes after the film's end, or after its credits when they come sooner (credits 0:
     * not seen). Credits before the start are some other film's.
     */
    static long quietUntil(long start, long end, long credits) {
        long until = end + AFTER_MS;
        if (credits > start) until = Math.min(until, credits + AFTER_MS);
        return until;
    }

    /** Whether pop-ups wait now: from the start to quietUntil. */
    static boolean quiet(long now, long start, long end, long credits) {
        return now >= start && now < quietUntil(start, end, credits);
    }

    /** The plan is over and can go: an hour after the quiet ends (or the end, quiet or not). */
    static boolean over(long now, long start, long end, long credits) {
        return now > quietUntil(start, end, credits) + 3600_000L;
    }
}
