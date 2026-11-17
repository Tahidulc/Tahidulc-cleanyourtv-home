package com.gathin.homefieldtv;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Live TV's words as the dark guide and the channel banner write them: 24-hour times ("17:40 – 18:45"), how far
 * through a programme is and what is left of it ("58 min left"), when one starts ("Starts in 12 min"), the picture the
 * player has ("1280×720", "25 fps") and the picture a channel's name promises (HD, 4K). Pure.
 */
final class LiveText {
    private LiveText() {}

    /** "17:40". */
    static String hm(long t, ZoneId z) {
        ZonedDateTime d = Instant.ofEpochMilli(t).atZone(z);
        // "17:40", or "5.40pm" (Settings → Appearance → Programme times).
        return com.gathin.homefieldtv.prefs.Look.hm(d.getHour(), d.getMinute(), com.gathin.homefieldtv.prefs.Look.times24);
    }

    static String hm(long t) {
        return hm(t, ZoneId.systemDefault());
    }

    /** "17:40 – 18:45". */
    static String span(long a, long b, ZoneId z) {
        return com.gathin.homefieldtv.prefs.Look.span(hm(a, z), hm(b, z));
    }

    static String span(long a, long b) {
        return span(a, b, ZoneId.systemDefault());
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    /** "19:42:31". */
    static String hms(long t, ZoneId z) {
        ZonedDateTime d = Instant.ofEpochMilli(t).atZone(z);
        return two(d.getHour()) + ":" + two(d.getMinute()) + ":" + two(d.getSecond());
    }

    static String hms(long t) {
        return hms(t, ZoneId.systemDefault());
    }

    /** How far behind live the picture is: "−12:34 behind live", "−1:02:03 behind live"; "Live" within a few seconds. */
    static String behind(long ms) {
        long s = Math.max(0L, ms / 1000);
        if (s < 5) return "Live";
        long h = s / 3600, m = s / 60 % 60, sec = s % 60;
        return "−" + (h > 0 ? h + ":" + two((int) m) : String.valueOf(m)) + ":" + two((int) sec) + " behind live";
    }

    /** How far through: 0 at its start, 1 at its end (0 for one with no length). */
    static float progress(long now, long start, long stop) {
        if (stop <= start) return 0f;
        return Math.max(0f, Math.min(1f, (now - start) / (float) (stop - start)));
    }

    /** A length in whole minutes, rounded up: "58 min", "1h 5m", "2h". */
    static String mins(long ms) {
        long m = Math.max(1L, (ms + 59_999L) / 60_000L);
        if (m < 60) return m + " min";
        long h = m / 60, r = m % 60;
        return r == 0 ? h + "h" : h + "h " + r + "m";
    }

    /** "58 min left"; "" once it has ended. */
    static String left(long now, long stop) {
        return stop <= now ? "" : mins(stop - now) + " left";
    }

    /** "Starts in 12 min"; "" once it has started. */
    static String startsIn(long now, long start) {
        return start <= now ? "" : "Starts in " + mins(start - now);
    }

    /** The picture's size as the player has it: "1280×720", or "" when it doesn't know. */
    static String size(int w, int h) {
        return w > 0 && h > 0 ? w + "×" + h : "";
    }

    /** Frames a second as the player has them: "25 fps", "29.97 fps", or "" when it doesn't know. */
    static String fps(float f) {
        if (!(f > 0f) || Float.isInfinite(f)) return "";
        float r = Math.round(f);
        if (Math.abs(f - r) < 0.01f) return (int) r + " fps";
        String s = String.format(Locale.ROOT, "%.2f", f);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s + " fps";
    }

    /** The picture a channel's name promises: "4K" (UHD, 4K, 2160p), "HD" (HD, FHD, 1080p, 720p), or "". */
    static String quality(String name) {
        if (name == null) return "";
        String best = "";
        for (String w : name.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            switch (w) {
                case "uhd":
                case "4k":
                case "2160p":
                    return "4K";
                case "hd":
                case "fhd":
                case "1080p":
                case "1080i":
                case "720p":
                    best = "HD";
                    break;
                default:
                    break;
            }
        }
        return best;
    }
}
