package com.gathin.broadcast;

import java.util.Locale;

/**
 * When a substitution comes out beside the scoreboard, the same on the TV and the phone: as the picture shows it, not
 * minutes after. The match data is read every half minute while the game is in play, so a substitution is known soon
 * after it is made; one known before the picture has reached its minute waits for it (the board's clock is in step with
 * the picture); and the channel's own graphic for it (grown out of its scoreboard and read off the picture: BugWatch)
 * brings it at once: the data is read there and then, and again every few seconds until the substitution is in it.
 *
 * <p>Pure: the apps keep the state, this says what to do.
 */
public final class SubsTiming {
    private SubsTiming() { }

    /** The match data read this often while the game is in play. */
    public static final long LIVE_READ_MS = 30_000L;
    /** After the channel showed a substitution: this long, the data read every SEEN_READ_MS and a substitution out at once. */
    public static final long SEEN_MS = 90_000L, SEEN_READ_MS = 8_000L;
    /** A substitution waiting for the picture to reach its minute waits this long at most (a clock gone wrong never keeps it). */
    public static final long MOST_WAIT_MS = 120_000L;

    /**
     * Pure: a substitution's minute as the data writes it ("71", "90+3", "71'") as seconds into the game, half way through
     * that minute (the 71st minute is the clock's 70:00 to 70:59: 70:30); -1 when it has none.
     */
    public static int secondsOf(String minute) {
        if (minute == null) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)(?:'?\\s*\\+\\s*(\\d+))?").matcher(minute);
        if (!m.find()) return -1;
        int total = Integer.parseInt(m.group(1)) + (m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
        return total <= 0 ? -1 : (total - 1) * 60 + 30;
    }

    /** Pure: the board's clock ("70:41") as seconds into the game; -1 when it shows none. */
    public static int clockSeconds(String clock) {
        if (clock == null) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+):(\\d{2})").matcher(clock);
        return m.find() ? Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)) : -1;
    }

    /**
     * Pure: whether a substitution at minute may come out now: the picture has reached it (the board's clock at least
     * half way through its minute), the clock is not known, the channel has just shown one (seen), or it has waited its
     * longest.
     */
    public static boolean due(String minute, int boardSeconds, boolean seen, long waitedMs) {
        if (seen || waitedMs >= MOST_WAIT_MS) return true;
        int at = secondsOf(minute);
        return at < 0 || boardSeconds < 0 || boardSeconds >= at;
    }

    /**
     * Pure: whether the words read off a graphic of the channel's (BugWatch.words) are a substitution's: SUB, SUBS,
     * SUBSTITUTION; or ON with OFF, IN with OUT.
     */
    public static boolean onScreen(String words) {
        if (words == null || words.isEmpty()) return false;
        String[] t = words.toUpperCase(Locale.ROOT).split("[^\\p{L}]+");
        boolean on = false, off = false, in = false, out = false;
        for (String w : t) {
            if (w.equals("SUB") || w.equals("SUBS") || w.startsWith("SUBSTIT")) return true;
            if (w.equals("ON")) on = true;
            if (w.equals("OFF")) off = true;
            if (w.equals("IN")) in = true;
            if (w.equals("OUT")) out = true;
        }
        return on && off || in && out;
    }
}
