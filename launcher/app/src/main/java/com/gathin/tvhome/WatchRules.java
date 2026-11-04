package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The rules behind How was it? and the pause card (WatchCards) and the line on Because you watched's posters (Because):
 * when a film is in its credits, when an episode has ended, when a pause has lasted long enough, which apps they are
 * for, and the words they show. Pure Java (no Android), so the unit tests can check it.
 */
final class WatchRules {
    private WatchRules() { }

    /** A film: 70 minutes or more (Cinema mode's rule). */
    static final long FILM_MS = 70 * 60_000L;
    /** A film or an episode at all: a quarter of an hour or more (WatchHistory's rule). */
    static final long LONG_MS = 15 * 60_000L;
    /** YouTube: only what runs 20 minutes or more is a film or an episode, not a clip. */
    static final long YOUTUBE_MS = 20 * 60_000L;
    /** How long a pause lasts before its card comes up. */
    static final long PAUSE_MS = 10_000L;
    /** How much of a film must have been seen here (before its credits) for How was it? to ask. */
    static final long FILM_SEEN_MS = 15 * 60_000L;
    /** How much of an episode, before its end, for a season's last one to ask. */
    static final long EPISODE_SEEN_MS = 10 * 60_000L;

    /** Where a film's credits start: the last 6%, at least four minutes (Cinema mode's rule). */
    static long creditsAt(long dur) {
        return dur - Math.max(4 * 60_000L, (long) (dur * 0.06));
    }

    /** Where an episode has ended: the last 5%, at least a minute and a half (its credits, before the next one starts). */
    static long episodeEndAt(long dur) {
        return dur - Math.max(90_000L, (long) (dur * 0.05));
    }

    /**
     * A film has reached its credits after being watched here: it is long enough, the position is in its credits, and
     * the earliest position seen playing was at least FILM_SEEN_MS before them (not opened at the end, or skipped there).
     */
    static boolean filmFinished(long dur, long pos, long firstSeen) {
        if (dur < FILM_MS || pos < 0 || firstSeen < 0) return false;
        long credits = creditsAt(dur);
        return pos >= credits && pos <= dur + 60_000L && firstSeen <= credits - FILM_SEEN_MS;
    }

    /** An episode (shorter than a film) has ended after a good part of it was watched here. */
    static boolean episodeFinished(long dur, long pos, long firstSeen) {
        if (dur < LONG_MS || dur >= FILM_MS || pos < 0 || firstSeen < 0) return false;
        long end = episodeEndAt(dur);
        return pos >= end && pos <= dur + 60_000L && firstSeen <= end - EPISODE_SEEN_MS;
    }

    /** What the cards are for: not music, the launcher, live TV, or a YouTube clip under 20 minutes; a film or episode. */
    static boolean watchable(String pkg, long dur, boolean live) {
        if (pkg == null || live) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.contains("com.gathin.tvhome") || p.contains("music") || p.contains("spotify") || p.contains("bluetooth")
                || p.contains("radio") || p.contains("podcast")) return false;
        if (p.contains("youtube")) return dur >= YOUTUBE_MS;
        return dur >= LONG_MS;
    }

    /** Whether a pause that began at pausedAt has lasted long enough by now for its card. */
    static boolean pauseDue(long pausedAt, long now) {
        return pausedAt > 0 && now - pausedAt >= PAUSE_MS;
    }

    /** A length as the cards say it: "2h 12m", "47 min", "1h". */
    static String length(long ms) {
        long min = Math.max(0, Math.round(ms / 60_000.0));
        if (min < 60) return min + " min";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    /** How far in: "1h 12m in" ("8 min in" under the hour). */
    static String inLine(long pos) {
        long min = Math.max(0, pos / 60_000L);
        if (min < 60) return min + " min in";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + "h in" : h + "h " + m + "m in";
    }

    /** What is left: "47 min left", "1h 05m left"; at the end, "Nearly done". */
    static String leftLine(long pos, long dur) {
        long min = (long) Math.ceil(Math.max(0, dur - pos) / 60_000.0);
        if (min <= 0) return "Nearly done";
        if (min < 60) return min + " min left";
        return String.format(Locale.UK, "%dh %02dm left", min / 60, min % 60);
    }

    /** The minute of the day (0 to 1439) something ends: now (a minute of the day) plus what is left, rounded up. */
    static int endMinute(int nowMinute, long leftMs) {
        long add = (long) Math.ceil(Math.max(0, leftMs) / 60_000.0);
        return (int) ((nowMinute + add) % (24 * 60));
    }

    /** A minute of the day as "22:07". */
    static String clock(int minute) {
        int m = ((minute % 1440) + 1440) % 1440;
        return String.format(Locale.UK, "%02d:%02d", m / 60, m % 60);
    }

    /**
     * Minutes from end to bed (both minutes of the day; bed in the evening or just after midnight), negative when it
     * ends after bed. Something ending in the small hours after an evening bed counts as after it.
     */
    static int beforeBed(int endMinute, int bedMinute) {
        int end = endMinute, bed = bedMinute;
        // The evening runs on past midnight: before 06:00 is the same night.
        if (end < 6 * 60) end += 1440;
        if (bed < 6 * 60) bed += 1440;
        return bed - end;
    }

    /** "53 min before bed", "Ends right at bedtime", "20 min past bed". */
    static String bedLine(int minutes) {
        if (minutes == 0) return "Ends right at bedtime";
        return span(Math.abs(minutes)) + (minutes > 0 ? " before bed" : " past bed");
    }

    /** "53 min", "1h", "1h 20m". */
    private static String span(int minutes) {
        if (minutes < 60) return minutes + " min";
        return minutes % 60 == 0 ? minutes / 60 + "h" : minutes / 60 + "h " + minutes % 60 + "m";
    }

    /** A time like "23:00" as a minute of the day; fallback when it does not read. */
    static int minuteOf(String hhmm, int fallback) {
        if (hhmm == null) return fallback;
        String[] p = hhmm.trim().split(":");
        try {
            int h = Integer.parseInt(p[0].trim()), m = p.length > 1 ? Integer.parseInt(p[1].trim()) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) return fallback;
            return h * 60 + m;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * What a title shares with the one it was found from, as its poster's line: the genres both have, at most two, in
     * the title's own order ("Crime · Mystery"); when they share none, the title's first genre; "" with none at all.
     */
    static String sharedGenres(List<String> title, List<String> from) {
        List<String> both = new ArrayList<>();
        if (title == null) return "";
        for (String g : title) {
            if (g == null || g.isEmpty() || both.size() >= 2) continue;
            for (String f : from == null ? new ArrayList<String>() : from) {
                if (same(g, f) && !both.contains(g)) { both.add(g); break; }
            }
        }
        if (both.isEmpty()) {
            for (String g : title) if (g != null && !g.isEmpty()) return g;
            return "";
        }
        return String.join(" · ", both);
    }

    /** Two genre names as the same one: a series' "Action & Adventure" is a film's Action, "Sci-Fi & Fantasy" Sci-Fi. */
    static boolean same(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.toLowerCase(Locale.ROOT), y = b.toLowerCase(Locale.ROOT);
        if (x.equals(y)) return true;
        for (String[] pair : new String[][]{{"action", "action & adventure"}, {"adventure", "action & adventure"},
                {"science fiction", "sci-fi & fantasy"}, {"sci-fi", "sci-fi & fantasy"}, {"fantasy", "sci-fi & fantasy"},
                {"war", "war & politics"}, {"science fiction", "sci-fi"}}) {
            if (x.equals(pair[0]) && y.equals(pair[1]) || x.equals(pair[1]) && y.equals(pair[0])) return true;
        }
        return false;
    }

    // ---- the ratings, as they are kept ------------------------------------------------------------------------------

    static final int NOT_FOR_ME = -1, LIKED = 1, LOVED = 2;

    /** One rating as kept: "value|when|main genre|name" (the name last, so it may hold anything but a line break). */
    static String encode(int value, long at, String genre, String name) {
        return value + "|" + at + "|" + clean(genre) + "|" + (name == null ? "" : name.replace('\n', ' '));
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace('\n', ' ');
    }

    /** A kept rating back: {value, when, genre, name}, or null when it does not read. */
    static Object[] decode(String s) {
        if (s == null) return null;
        String[] p = s.split("\\|", 4);
        if (p.length < 4) return null;
        try {
            int v = Integer.parseInt(p[0]);
            if (v != NOT_FOR_ME && v != LIKED && v != LOVED) return null;
            return new Object[]{v, Long.parseLong(p[1]), p[2], p[3]};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The choice's words on How was it?'s buttons (and in the log). */
    static String word(int value) {
        return value == NOT_FOR_ME ? "Not for me" : value == LOVED ? "Loved it" : "Liked it";
    }
}
