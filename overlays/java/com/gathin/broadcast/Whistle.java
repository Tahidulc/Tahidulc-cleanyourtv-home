package com.gathin.broadcast;

/**
 * When a half is over on the picture the family sees, the same on the TV and the phone: the board's clock stops and HT
 * or FT shows as the whistle reaches the screen, never before it and never long after.
 *
 * <p>Every signal there is, each as it came (the board's own sums put the stream's delay on them):
 * <ul>
 * <li>the data's flip: a read of the scores (ESPN) or of FotMob that has the half over (half time, full time, the next
 * half begun), and FotMob's own times for the half, which give its length to the second;</li>
 * <li>the channel's own clock read off the picture (the phone; the TV where it can see the picture): standing still
 * past the half's end, or gone from its scoreboard after it was seen in the half's last minutes;</li>
 * <li>the minutes added put up: the referee plays at least those, so with the board's clock the picture's own, no half
 * is over before them.</li>
 * </ul>
 *
 * <p>The half is over on the board when, and only when: the board's clock has reached the half's end (and the minutes
 * added, on the picture's clock), the picture is not showing its clock running, and either the data has the half over
 * and one of these says the picture has got there: the channel's clock stopped past the end, or gone; the board's clock
 * at FotMob's length of the half; the data's flip as far behind as the stream is; the most the half can have run by the
 * data's last clock in play. Or, without the data yet, the channel's clock stopped past the end of the first half (or of
 * extra time's first) for a while. Once over it stays over (the clock stopped) until the picture shows play again or the
 * board moves on to the next half.
 *
 * <p>Pure, one game at a time: the tests replay made-up halves through it.
 */
public final class Whistle {
    /** What the board shows: the half still on, half time (or a break: before extra time), full time. */
    public static final int NONE = 0, HT = 1, FT = 2;
    /** What a read says: in play, at a break (half time), over (full time), not begun. */
    public static final int IN_PLAY = 0, AT_BREAK = 1, AT_END = 2, NOT_BEGUN = -1;
    /** A read of the channel's clock is the picture's now for this long (it is read every two seconds or so). */
    static final long FRESH_MS = 5_000L;
    /** The channel's clock gone from its scoreboard this long, the data having the half over: the whistle on the picture. */
    static final long GONE_MS = 6_000L;
    /** ...counted only while the clock was seen this recently (a scoreboard gone for minutes says nothing more). */
    static final long GONE_MOST_MS = 150_000L;
    /** The channel's clock gone this close to the end of the half or after it (in seconds): its end. */
    static final long GONE_NEAR_S = 30;
    /** The channel's clock stopped past the first half's end this long, with no data yet: half time. */
    static final long LONE_STOP_MS = 20_000L;
    /** The board's clock a second past FotMob's length of the half: its times are to the second. */
    static final long EXACT_SLACK_S = 1;

    private String game = "";
    /** For each half (1 to 4): when the data first had it over (-1 not yet), and what it became. */
    private final long[] endedAt = {-1, -1, -1, -1, -1};
    private final int[] endedTo = new int[5];
    /** FotMob's length of each half on the clock, in seconds (2700 + 4:10 for a first half to 49:10); -1 unknown. */
    private final long[] exact = {-1, -1, -1, -1, -1};
    /** The data's last clock in play for each half (seconds), and when that read came. */
    private final long[] liveSecs = {-1, -1, -1, -1, -1}, liveAt = new long[5];
    /** The minutes added put up for each half (-1 unknown). */
    private final int[] added = {-1, -1, -1, -1, -1};
    /** The half the data last had in play. */
    private int livePeriod;
    /** The channel's clock as last read: its seconds, when, standing still since when (-1 running); a read without it. */
    private long picSecs = -1, picAt, stillSince = -1, noneAt;
    /** The half over on the board, kept: which, and what it shows. */
    private int latched, latchedTo;

    /** Pure: where a half's clock ends without its added time, in seconds: 45:00, 90:00, 105:00, 120:00. */
    public static long end(int period) {
        return period <= 1 ? 2700 : period == 2 ? 5400 : period == 3 ? 6300 : 7200;
    }

    /**
     * Pure: whether ESPN's status name is a break with no clock running: half time (and extra time's, HALFTIME_ET), the
     * end of normal time before extra time, the end of extra time before a shootout.
     */
    public static boolean atBreak(String espnStatus) {
        String n = espnStatus == null ? "" : espnStatus;
        return n.contains("HALFTIME") || n.contains("END_OF_REGULATION") || n.contains("END_OF_EXTRATIME")
                || n.contains("END_OF_EXTRA_TIME");
    }

    /** Pure: the half a clock of total seconds is in by itself (when the data gives none): 1 to 4. */
    public static int periodOf(long secs) {
        return secs < 2700 ? 1 : secs < 5400 ? 2 : secs < 6300 ? 3 : 4;
    }

    /** Everything forgotten (another channel). */
    public void forget() {
        game = null;
        forGame("");
    }

    private void forGame(String id) {
        String g = id == null ? "" : id;
        if (g.equals(game)) return;
        game = g;
        java.util.Arrays.fill(endedAt, -1);
        java.util.Arrays.fill(endedTo, NONE);
        java.util.Arrays.fill(exact, -1);
        java.util.Arrays.fill(liveSecs, -1);
        java.util.Arrays.fill(liveAt, 0);
        java.util.Arrays.fill(added, -1);
        livePeriod = 0;
        picSecs = -1;
        picAt = 0;
        stillSince = -1;
        noneAt = 0;
        latched = 0;
        latchedTo = NONE;
    }

    private static boolean half(int p) {
        return p >= 1 && p <= 4;
    }

    /**
     * A read of the scores as it came at at (no delay on it): the game, its half (ESPN's period), what it says (IN_PLAY,
     * AT_BREAK, AT_END, NOT_BEGUN) and its clock in seconds while in play.
     */
    public void data(long at, String id, int period, int phase, double secs) {
        forGame(id);
        if (phase == NOT_BEGUN) return;
        if (phase == IN_PLAY) {
            int p = half(period) ? period : periodOf((long) secs);
            // Into the next half with no break seen between: the one before is over.
            if (half(livePeriod) && p > livePeriod) over(livePeriod, at, AT_BREAK);
            livePeriod = Math.max(livePeriod, p);
            if (p == livePeriod) {
                liveSecs[p] = Math.max(liveSecs[p], (long) secs);
                liveAt[p] = at;
            }
            return;
        }
        int p = half(period) ? period : livePeriod;
        if (!half(p)) return;
        over(p, at, phase);
    }

    private void over(int p, long at, int phase) {
        if (endedAt[p] < 0 || at < endedAt[p]) endedAt[p] = at;
        // Full time wins over a break once the data says it is over.
        if (endedTo[p] != FT) endedTo[p] = phase == AT_END ? FT : HT;
    }

    /**
     * FotMob's read as it came at at: the half it has over (0: none yet), what it became (AT_BREAK or AT_END), and that
     * half's length on the clock in seconds from its own times (-1: it gives none).
     */
    public void fotmob(long at, String id, int period, int phase, long lengthSecs) {
        forGame(id);
        if (!half(period)) return;
        if (lengthSecs >= end(period) && lengthSecs < end(period) + 30 * 60) exact[period] = lengthSecs;
        if (phase == AT_BREAK || phase == AT_END) over(period, at, phase);
    }

    /** FotMob's status as read at at (fotmobStatus): each half's length it has, and the half it has over last. */
    public void fotmob(long at, String id, Status st) {
        if (st == null) return;
        for (int p = 1; p <= 4; p++) if (st.length[p] > 0) fotmob(at, id, p, IN_PLAY, st.length[p]);
        if (half(st.period)) fotmob(at, id, st.period, st.phase, st.length[st.period]);
    }

    /** FotMob's match status: the half it has over last (0: none) and what it became, each half's length (s; -1 unknown). */
    public static final class Status {
        public int period;
        public int phase = IN_PLAY;
        public final long[] length = {-1, -1, -1, -1, -1};
    }

    private static final String[][] HALFS = {{"firstHalfStarted", "firstHalfEnded"}, {"secondHalfStarted", "secondHalfEnded"},
            {"firstExtraHalfStarted", "firstExtraHalfEnded"}, {"secondExtraHalfStarted", "secondExtraHalfEnded"}};

    /**
     * Pure: FotMob's match details' status (header.status): finished, half time (its reason's or live time's "HT"), each
     * half's start and end (halfs), as a Status; nothing in it when FotMob gives none.
     */
    public static Status fotmobStatus(org.json.JSONObject details) {
        Status s = new Status();
        org.json.JSONObject header = details == null ? null : details.optJSONObject("header");
        org.json.JSONObject st = header == null ? null : header.optJSONObject("status");
        if (st == null) return s;
        org.json.JSONObject halfs = st.optJSONObject("halfs");
        int lastEnded = 0, lastStarted = 0;
        for (int p = 1; p <= 4; p++) {
            String a = halfs == null ? "" : halfs.optString(HALFS[p - 1][0], ""), b = halfs == null ? "" : halfs.optString(HALFS[p - 1][1], "");
            if (!a.trim().isEmpty()) lastStarted = p;
            if (!b.trim().isEmpty()) {
                lastEnded = p;
                s.length[p] = lengthOf(p, a, b);
            }
        }
        org.json.JSONObject reason = st.optJSONObject("reason"), live = st.optJSONObject("liveTime");
        String why = reason == null ? "" : reason.optString("short", ""), now = live == null ? "" : live.optString("short", "");
        boolean ht = "HT".equalsIgnoreCase(why.trim()) || "HT".equalsIgnoreCase(now.trim());
        if (st.optBoolean("finished")) {
            s.period = Math.max(2, Math.max(lastEnded, lastStarted));
            s.phase = AT_END;
        } else if (ht || lastEnded > 0 && lastEnded >= lastStarted) {
            s.period = lastEnded > 0 ? lastEnded : 1;
            s.phase = AT_BREAK;
        }
        return s;
    }

    /** The minutes added put up for a half (1 to 4). */
    public void added(String id, int period, int minutes) {
        forGame(id);
        if (half(period) && minutes > 0 && minutes < 20) added[period] = minutes;
    }

    /** The channel's own clock read off the picture at at: its seconds, and whether it has stood still there. */
    public void picture(long at, String id, long secs, boolean still) {
        forGame(id);
        if (secs < 0) return;
        if (still) {
            if (stillSince < 0 || picSecs != secs) stillSince = at;
        } else {
            stillSince = -1;
        }
        picSecs = secs;
        picAt = at;
    }

    /** A read of the channel's scoreboard at at with no clock on it (gone, or the scoreboard away). */
    public void pictureNone(long at, String id) {
        forGame(id);
        noneAt = at;
    }

    /** The picture's clock forgotten (another channel, or it could not be read for a long while). */
    public void noPicture() {
        picSecs = -1;
        picAt = 0;
        stillSince = -1;
        noneAt = 0;
    }

    /** Whether the channel's clock is read running on the picture now. */
    public boolean pictureRunning(long now) {
        return picAt > 0 && now - picAt <= FRESH_MS && stillSince < 0;
    }

    /**
     * Whether the half is over on the board now, and what it shows: NONE, HT or FT. period is the half the board is in
     * (1 to 4), boardSecs its clock now (in step with the picture), behindMs how far the picture runs behind the scores,
     * matched whether that clock is the picture's own (read off it, or set at its kick-off) rather than the scores'.
     */
    public int over(long now, String id, int period, long boardSecs, long behindMs, boolean matched) {
        forGame(id);
        if (!half(period)) return NONE;
        boolean fresh = picAt > 0 && now - picAt <= FRESH_MS;
        // The picture shows its clock going: play on, whatever was said (a half kept over is let go).
        if (fresh && stillSince < 0) {
            latched = 0;
            return NONE;
        }
        if (latched == period && latchedTo != NONE) return latchedTo;
        int to = decide(now, period, boardSecs, behindMs, matched, fresh);
        if (to != NONE) {
            latched = period;
            latchedTo = to;
        }
        return to;
    }

    private int decide(long now, int p, long boardSecs, long behindMs, boolean matched, boolean fresh) {
        long end = end(p);
        boolean still = fresh && stillSince >= 0;
        // The picture's clock stopped: it is the board's.
        long secs = still ? picSecs : boardSecs;
        if (secs < end) return NONE;
        // On the picture's own clock the referee plays at least the minutes added (unless the channel's clock stands at
        // the half's end through them, as some channels' do, saying nothing of when they are up).
        long least = added[p] > 0 ? end + added[p] * 60L : end;
        boolean atEnd = still && Math.abs(picSecs - end) <= 1;
        if (matched && !atEnd && secs < least) return NONE;
        long at = endedAt[p];
        if (at < 0) {
            // No data yet: the channel's clock stopped past the end of a first half for a while.
            if (still && picSecs > end && now - stillSince >= LONE_STOP_MS && (p == 1 || p == 3)) return HT;
            return NONE;
        }
        int to = endedTo[p];
        // The channel's clock stopped past the half's end: the whistle is on the picture.
        if (still && picSecs > end) return to;
        // The channel's clock gone from its scoreboard after it was seen in the half's last moments (its minutes added up,
        // when they were put up: a replay earlier in added time hides it too).
        if (!fresh && picAt > 0 && noneAt > picAt && now - picAt >= GONE_MS && now - picAt <= GONE_MOST_MS
                && picSecs >= least - GONE_NEAR_S && picSecs < end + 30 * 60) return to;
        // FotMob's length of the half, to the second.
        if (exact[p] > 0 && secs >= exact[p] + EXACT_SLACK_S) return to;
        // The data's flip as far behind as the stream is.
        if (now >= at + Math.max(0, behindMs)) return to;
        // The most the half can have run: the data's last clock in play, a minute (it moves a minute at a time), and the
        // time from that read to the one with the half over.
        if (matched && liveSecs[p] >= 0 && liveAt[p] > 0 && at >= liveAt[p]
                && secs >= liveSecs[p] + 60 + (at - liveAt[p]) / 1000) return to;
        return NONE;
    }

    /** The half the data has over last (0: none), for the callers' state. */
    public int endedHalf() {
        for (int p = 4; p >= 1; p--) if (endedAt[p] >= 0) return p;
        return 0;
    }

    /** What the data has a half become (NONE while it is not over). */
    public int endedTo(int period) {
        return half(period) ? endedTo[period] : NONE;
    }

    /**
     * Pure: a half's length on the clock in seconds from FotMob's times for it ("24.11.2026 20:47:12" and the like, its
     * start and its end), the half's own start added (45:00 for the second); -1 when either can't be read or it is no
     * half's length.
     */
    public static long lengthOf(int period, String started, String ended) {
        long a = stamp(started), b = stamp(ended);
        if (a < 0 || b < 0 || b <= a) return -1;
        long run = (b - a) / 1000, base = period <= 1 ? 0 : period == 2 ? 2700 : period == 3 ? 5400 : 6300;
        long half = period <= 2 ? 2700 : 900;
        if (run < half || run > half + 30 * 60) return -1;
        return base + run;
    }

    /** Pure: "dd.MM.yyyy HH:mm:ss" (or ISO "yyyy-MM-ddTHH:mm:ss") as ms on one clock (no time zone: only differences count); -1. */
    static long stamp(String s) {
        if (s == null) return -1;
        String t = s.trim();
        int d, mo, y, h, mi, se;
        try {
            if (t.length() >= 19 && t.charAt(2) == '.' && t.charAt(5) == '.') {
                d = Integer.parseInt(t.substring(0, 2));
                mo = Integer.parseInt(t.substring(3, 5));
                y = Integer.parseInt(t.substring(6, 10));
                h = Integer.parseInt(t.substring(11, 13));
                mi = Integer.parseInt(t.substring(14, 16));
                se = Integer.parseInt(t.substring(17, 19));
            } else if (t.length() >= 19 && t.charAt(4) == '-' && t.charAt(7) == '-') {
                y = Integer.parseInt(t.substring(0, 4));
                mo = Integer.parseInt(t.substring(5, 7));
                d = Integer.parseInt(t.substring(8, 10));
                h = Integer.parseInt(t.substring(11, 13));
                mi = Integer.parseInt(t.substring(14, 16));
                se = Integer.parseInt(t.substring(17, 19));
            } else {
                return -1;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        if (mo < 1 || mo > 12 || d < 1 || d > 31 || h > 23 || mi > 59 || se > 60) return -1;
        // Days from a fixed start (the civil calendar's), enough for differences across a midnight or a month's end.
        long yy = mo <= 2 ? y - 1 : y;
        long era = (yy >= 0 ? yy : yy - 399) / 400, yoe = yy - era * 400;
        long doy = (153L * (mo + (mo > 2 ? -3 : 9)) + 2) / 5 + d - 1, doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        long days = era * 146097 + doe;
        return ((days * 24 + h) * 60 + mi) * 60_000L + se * 1000L;
    }
}
