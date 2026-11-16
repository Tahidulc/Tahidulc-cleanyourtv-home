package com.gathin.homefieldtv.sports;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The scoreboard's game as the picture shows it, the TV launcher's sums for its board over TiviMate (BoardCover) copied
 * as they are: each reading of the scores is kept with when it was seen, and the board shows the one the channel has
 * reached (its wait, InStep, behind); its clock counts on in minutes and seconds from when that clock was first seen,
 * never stopping or stepping back by a little, through the minutes added; or, once OK was pressed at a kick-off on the
 * screen (the in-step card), from that moment, exactly the channel's. The minutes added come beside it from the half's
 * last minute, once put up. All pure, for the tests.
 */
public final class BoardClock {
    private BoardClock() {}

    /** The readings kept for each game: one a poll (20 s apart while live), more than the longest wait. */
    public static final int KEEP = 16;

    /** One reading of a game's score and clock, and when it was seen. */
    public static final class Reading {
        public final long seen;
        public final int home, away;
        /** Game.LIVE (0), Game.PRE (1) or Game.POST (2). */
        public final int state;
        public final boolean halftime;
        public final double clockSec;
        /** ESPN's minute in words ("67'", "45'+2'"). */
        public final String detail;
        /** When this clock was first seen (ESPN's moves a minute at a time): the seconds count on from it. */
        long since;

        public Reading(long seen, int home, int away, int state, boolean halftime, double clockSec, String detail) {
            this.seen = seen;
            this.home = home;
            this.away = away;
            this.state = state;
            this.halftime = halftime;
            this.clockSec = clockSec;
            this.detail = detail == null ? "" : detail;
            since = seen;
        }

        public static Reading of(Game g, long seen) {
            return new Reading(seen, g.homeScore, g.awayScore, g.state, g.halftime, g.clock, g.detail);
        }

        public String score() {
            return home + "–" + away;
        }
    }

    /** Pure: a reading kept after the others (none out of order), its clock's first sight carried on while it has not moved. */
    public static void add(ArrayDeque<Reading> list, Reading r) {
        Reading last = list.peekLast();
        if (last != null && r.seen < last.seen) return;
        if (last != null && last.state == r.state && last.halftime == r.halftime && last.clockSec == r.clockSec
                && minuteOf(last.detail) == minuteOf(r.detail)) r.since = last.since;
        list.addLast(r);
        while (list.size() > KEEP) list.removeFirst();
    }

    /** Pure: the reading the screen shows at screenNow: the last one seen by then, else the first there is (null: none). */
    public static Reading atScreen(Iterable<Reading> list, long screenNow) {
        Reading first = null, pick = null;
        for (Reading r : list) {
            if (first == null) first = r;
            if (r.seen <= screenNow) pick = r;
        }
        return pick != null ? pick : first;
    }

    /**
     * Pure: the clock's seconds from a reading at screenNow (-1: none), counted on from when it was first seen for as long
     * as the next reading takes to come, so it never stops. Only the minute ("67'"): the minute began a minute before. In
     * added time ESPN's clock can stand at the period's end while its minute goes on ("90'+3'"): the later of the two.
     */
    public static double clockSecs(Reading r, long screenNow) {
        if (r == null || r.state != Game.LIVE || r.halftime) return -1;
        double secs = Math.max(r.clockSec, (minuteOf(r.detail) - 1) * 60.0);
        if (secs <= 0) return -1;
        return secs + Math.max(0, (screenNow - r.since) / 1000);
    }

    /**
     * Pure: the clock to show from what is said now and the one running on from the last shown (-1: none): the later of
     * the two, unless the running one is half a minute or more ahead (a real correction).
     */
    public static double steadied(double said, double running) {
        if (running < 0 || said >= running || running - said >= 30) return said;
        return running;
    }

    /** The clock as last shown, so it counts on from there (one board at a time: the main thread's). */
    public static final class Steady {
        private String key;
        private double secs;
        private long at;

        /** The clock to show for game id now: what is said, never stopping or stepping back by a little in the same half. */
        public double shown(String id, String detail, double said, long now) {
            int first = firstMinute(detail);
            int period = first > 0 ? (first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4) : periodOfSecs((long) said);
            String k = id + "|" + period;
            double running = k.equals(key) ? secs + (now - at) / 1000.0 : -1;
            double out = steadied(said, running);
            key = k;
            secs = out;
            at = now;
            return out;
        }

        /** Counted afresh (set at a kick-off, or a second sooner or later as asked). */
        public void reset() {
            key = null;
        }
    }

    /** Pure: a clock of total seconds as broadcasters show it, "67:23", running on through the minutes added ("92:15"). */
    public static String clockWords(double total) {
        int whole = Math.max(0, (int) total);
        return String.format(Locale.UK, "%02d:%02d", whole / 60, whole % 60);
    }

    /**
     * Pure: the box beside the clock from the half's last minute (the 45th, the 90th) on: the minutes the fourth official
     * put up for that half ("+4"), once known (announced: {first half, second half, extra time's two}, 0 or less: not
     * yet); none before, or when not known.
     */
    public static String addedWords(String clock, String detail, int[] announced) {
        int m = minuteOfClock(clock);
        if (m < 0 || announced == null) return "";
        int period = periodOf(clock, detail);
        int end = period == 1 ? 45 : period == 2 ? 90 : period == 3 ? 105 : 120;
        if (m < end - 1 || period > announced.length) return "";
        int n = announced[period - 1];
        return n > 0 ? "+" + n : "";
    }

    /** Pure: whether a clock is in its half's added time: its minute at or past the half's end (the half by ESPN's minute). */
    public static boolean inAddedTime(String clock, String detail) {
        int m = minuteOfClock(clock);
        if (m < 0) return false;
        int period = periodOf(clock, detail);
        return m >= (period == 1 ? 45 : period == 2 ? 90 : period == 3 ? 105 : 120);
    }

    /** Pure: the half a clock is in (1, 2, extra time's 3 and 4), by ESPN's first minute when it gives one. */
    public static int periodOf(String clock, String detail) {
        int m = minuteOfClock(clock), first = firstMinute(detail);
        if (first > 0) return first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4;
        return m < 0 || m < 45 ? 1 : m < 90 ? 2 : m < 105 ? 3 : 4;
    }

    /** Pure: the half ESPN's minute is in (1, 2, then extra time's 3 and 4); 0 when it gives none. */
    public static int periodOfDetail(String detail) {
        int first = firstMinute(detail);
        return first <= 0 ? 0 : first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4;
    }

    /** Pure: the period a clock of total seconds is in (1, 2, then extra time's 3 and 4). */
    public static int periodOfSecs(long secs) {
        return secs < 45 * 60 ? 1 : secs < 90 * 60 ? 2 : secs < 105 * 60 ? 3 : 4;
    }

    private static final Pattern MINUTE = Pattern.compile("(\\d+)'?\\s*(?:\\+\\s*(\\d+))?");

    /** Pure: the minute in ESPN's words, the minutes added counted ("45'+2'": 47); 0 when none. */
    public static int minuteOf(String detail) {
        Matcher m = MINUTE.matcher(detail == null ? "" : detail);
        if (!m.find()) return 0;
        return Integer.parseInt(m.group(1)) + (m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
    }

    /** Pure: the minute in ESPN's words before any minutes added ("45'+2'": 45); 0 when none. */
    public static int firstMinute(String detail) {
        Matcher m = MINUTE.matcher(detail == null ? "" : detail);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** Pure: the minute a clock shows ("67:23": 67); -1 when it shows none ("HT", ""). */
    public static int minuteOfClock(String clock) {
        int colon = clock == null ? -1 : clock.indexOf(':');
        if (colon <= 0) return -1;
        try {
            return Integer.parseInt(clock.substring(0, colon).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Pure: which side scored between two scores ("1–0" to "1–1": 2), 0 for neither. */
    public static int goalSide(String before, String after) {
        int[] a = goals(before), b = goals(after);
        if (a == null || b == null) return 0;
        return b[0] > a[0] ? 1 : b[1] > a[1] ? 2 : 0;
    }

    private static int[] goals(String score) {
        if (score == null) return null;
        String[] p = score.split("–");
        if (p.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Pure: "EVE" from ESPN's abbreviation, else the name's first three letters. */
    public static String code(String abbr, String name) {
        if (abbr != null && !abbr.isEmpty() && abbr.length() <= 4) return abbr.toUpperCase(Locale.ROOT);
        String n = name == null ? "" : name.replaceAll("[^\\p{L}]", "");
        return (n.length() > 3 ? n.substring(0, 3) : n).toUpperCase(Locale.ROOT);
    }

    /** Pure: the competition in letters, while its logo is not there: "PREMIER LEAGUE" "PL", "BUNDESLIGA" "BUN". */
    public static String tag(String league) {
        if (league == null || league.trim().isEmpty()) return "";
        String[] w = league.trim().toUpperCase(Locale.ROOT).split("\\s+");
        if (w.length == 1) return w[0].length() > 3 ? w[0].substring(0, 3) : w[0];
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length && b.length() < 3; i++) b.append(w[i].charAt(0));
        return b.toString();
    }

    // ---- its clock set with the channel's ----------------------------------------------------------------------------

    /**
     * Pure: the clock set for this game, {base seconds, when, its half (0: by the seconds)}, from "id|base|at" or
     * "id|base|at|half"; null for none, or another game's.
     */
    public static long[] anchorOf(String s, String gameId) {
        if (s == null || gameId == null) return null;
        String[] f = s.split("\\|");
        if ((f.length != 3 && f.length != 4) || !f[0].equals(gameId)) return null;
        try {
            return new long[]{Long.parseLong(f[1]), Long.parseLong(f[2]), f.length > 3 ? Long.parseLong(f[3]) : 0};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Pure: the words an anchor is kept as. */
    public static String anchorWords(String gameId, long base, long at, int half) {
        return gameId + "|" + base + "|" + at + (half > 0 ? "|" + half : "");
    }

    /**
     * Pure: the clock counted from a moment set by OK at a kick-off (a = {base seconds, when, half}) to now, as seconds;
     * -1 when the half on the screen (ESPN's minute) is another than the one it was set in, or there is none.
     */
    public static double anchoredSecs(long[] a, long now, String detail) {
        if (a == null || now < a[1]) return -1;
        int period = a.length > 2 && a[2] > 0 ? (int) a[2] : periodOfSecs(a[0]), first = firstMinute(detail);
        if (first > 0 && (first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4) != period) return -1;
        return a[0] + (now - a[1]) / 1000.0;
    }

    /** Pure: the anchor moved delta ms (the board a second sooner or later, as asked); null stays null. */
    public static long[] moved(long[] a, long delta) {
        return a == null ? null : new long[]{a[0], a[1] + delta, a.length > 2 ? a[2] : 0};
    }

    // ---- when it is up -------------------------------------------------------------------------------------------------

    /**
     * How long before kick-off it is up (with the kick-off's time on its clock), how long half time stays on it before it
     * rests, how long full time stays, and how long after full time it lets the game go.
     */
    public static final long PRE_MS = 20 * 60_000L, HT_SHOWN_MS = 20_000L, FT_SHOWN_MS = 50_000L, AFTER_FT_MS = 10 * 60_000L;
    /** A kick-off a little late (or the scores a poll behind): the board stays up as it was, ready for the game. */
    public static final long LATE_MS = 15 * 60_000L;

    /** What the board does now: away, up before kick-off, up live (or at half or full time on it), resting. */
    public static final int AWAY = 0, PRE = 1, UP = 2, REST = 3;

    /**
     * Pure: what the board does for a game: r the reading the screen shows (null: none yet), kickoff and now; sinceHt and
     * sinceFt how long the screen has shown half time or full time (-1: it hasn't); busy: something is out of it (an alert).
     */
    public static int phase(Reading r, long kickoff, long now, long sinceHt, long sinceFt, boolean busy) {
        if (r == null || r.state == Game.PRE) {
            if (kickoff > 0 && kickoff - now <= PRE_MS && now - kickoff <= LATE_MS) return PRE;
            return AWAY;
        }
        if (r.state == Game.POST) {
            if (sinceFt >= 0 && sinceFt > AFTER_FT_MS) return AWAY;
            return sinceFt >= 0 && sinceFt > FT_SHOWN_MS && !busy ? REST : UP;
        }
        if (r.halftime) return sinceHt >= 0 && sinceHt > HT_SHOWN_MS && !busy ? REST : UP;
        return UP;
    }

    // ---- burn-in -----------------------------------------------------------------------------------------------------------

    /** Once a minute it steps round a slow circle, a pixel or two; after five minutes without a goal it dims. */
    public static final long STEP_MS = 60_000L, DIM_AFTER_MS = 5 * 60_000L;
    /** How dim: the colours at this share of their brightness, never see-through. */
    public static final float DIM = 0.7f;

    /** Pure: the step round the circle at now, {dx, dy} in pixels (radius r): eight steps, one a minute. */
    public static int[] drift(long now, int r) {
        int step = (int) ((now / STEP_MS) % 8);
        double a = step * Math.PI / 4;
        return new int[]{(int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r)};
    }

    /** Pure: whether it is dimmed now: five minutes since it last changed (a goal, the kick-off), and not at a moment. */
    public static boolean dimmed(long sinceChange) {
        return sinceChange > DIM_AFTER_MS;
    }
}
