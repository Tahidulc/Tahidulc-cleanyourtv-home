package com.gathin.tvhomephone.live;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * The guide grid's time across: where a moment sits in pixels from the grid's start (its origin, a half hour), and back,
 * the half hours the ruler marks, the days it covers, and how far it may scroll. Every row draws from one shared
 * horizontal offset with this, so the rows and the ruler always line up.
 */
final class Timeline {
    static final long MINUTE = 60_000L, HALF_HOUR = 30 * MINUTE, DAY = 24 * 3600_000L;

    final long origin, end;
    /** Pixels per millisecond (a double, so a moment and its x go back and forth exactly). */
    final double pxPerMs;

    Timeline(long origin, long end, float pxPerMinute) {
        this.origin = origin;
        this.end = Math.max(end, origin + HALF_HOUR);
        this.pxPerMs = pxPerMinute / (double) MINUTE;
    }

    /** A grid from the half hour at or before from to the half hour at or after to (at least two hours on from now). */
    static Timeline span(long from, long to, long now, float pxPerMinute, TimeZone tz) {
        long a = floorHalfHour(Math.min(from, now), tz);
        long b = ceilHalfHour(Math.max(to, now + 2 * 3600_000L), tz);
        return new Timeline(a, b, pxPerMinute);
    }

    /** The x of a moment, in pixels from the origin. */
    float x(long t) {
        return (float) ((t - origin) * pxPerMs);
    }

    /** The moment at an x, in pixels from the origin. */
    long time(float x) {
        return origin + Math.round(x / pxPerMs);
    }

    float width() {
        return x(end);
    }

    /** An offset kept inside the grid: from 0 to where the end meets the viewport's right edge. */
    float clamp(float offset, float viewport) {
        float max = Math.max(0f, width() - viewport);
        if (offset < 0 || Float.isNaN(offset)) return 0f;
        return Math.min(offset, max);
    }

    /** The local half hour (:00 or :30) at or before t. */
    static long floorHalfHour(long t, TimeZone tz) {
        long local = t + tz.getOffset(t);
        return t - Math.floorMod(local, HALF_HOUR);
    }

    /** The local half hour at or after t. */
    static long ceilHalfHour(long t, TimeZone tz) {
        long f = floorHalfHour(t, tz);
        return f == t ? t : f + HALF_HOUR;
    }

    /** The half hours from the first at or after from, up to (not past) to: the ruler's marks. */
    static List<Long> ticks(long from, long to, TimeZone tz) {
        List<Long> out = new ArrayList<>();
        for (long t = ceilHalfHour(from, tz); t <= to; t += HALF_HOUR) out.add(t);
        return out;
    }

    /** The start of each local day the grid touches, the origin's own day first. */
    List<Long> days(ZoneId zone) {
        List<Long> out = new ArrayList<>();
        LocalDate d = Instant.ofEpochMilli(origin).atZone(zone).toLocalDate();
        while (true) {
            long start = d.atStartOfDay(zone).toInstant().toEpochMilli();
            if (start >= end) break;
            out.add(start);
            d = d.plusDays(1);
        }
        return out;
    }

    /** Where to scroll for a day: now for today, otherwise that day's start (never before the grid's origin). */
    long dayTarget(long dayStart, long now) {
        if (now >= dayStart && now < dayStart + DAY) return now;
        return Math.max(origin, dayStart);
    }

    /** The first programme still on at t (its stop after t), or the schedule's length when none is. */
    static int first(Guide.Schedule s, long t) {
        int n = s.start.length, lo = 0, hi = n - 1, at = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (s.start[mid] <= t) { at = mid; lo = mid + 1; } else hi = mid - 1;
        }
        if (at < 0) return 0;
        // The last to start by t may have ended; one before it may still be on (a long film beside a short filler).
        int i = at;
        while (i > 0 && s.stop[i - 1] > t) i--;
        while (i < n && s.stop[i] <= t) i++;
        return i;
    }
}
