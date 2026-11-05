package com.gathin.tvhomephone.sports;

import java.util.ArrayList;
import java.util.List;

/**
 * A game as the picture shows it. The stream runs behind the real game (its delay, LiveAlerts' for the channel), so
 * each read of the scores is kept with when it came and read back as it stood that long ago: the score changes as the
 * goal shows, and the clock is the one on the screen. Between ESPN's whole minutes the seconds go on from when each
 * minute was first read. When the broadcaster's own clock has been seen to turn a minute (BugFinder's ticks), the
 * clock runs from that moment instead, to the second, until the period changes. Pure: the tests drive it.
 */
public final class OnScreen {
    /** One read: the game, when it came, and when its clock's minute was first read. */
    private static final class Read {
        final Game g;
        final long at, minuteAt;
        final double secs;

        Read(Game g, long at, long minuteAt) {
            this.g = g;
            this.at = at;
            this.minuteAt = minuteAt;
            this.secs = clockOf(g);
        }
    }

    /** The picture's delay is measured over this long. */
    private static final long MEASURE_MS = 5 * 60_000L;
    /** Reads kept this long past the delay; the delay itself is at most LiveAlerts' three minutes. */
    private static final long KEEP_MS = 4 * 60_000L;
    /** Seconds go on at most this far past a minute's first read, until the next read (ESPN gives whole minutes). */
    private static final long RUN_ON_MS = 59_999L;
    /** The screen's own clock is let go if the scores disagree with it by more than this. */
    private static final long TRUST_MS = 150_000L;

    private final List<Read> reads = new ArrayList<>();
    private long behindMs;
    /** The broadcaster's clock: at anchorAt (when it was seen) it read anchorMs, in this period; 0 when not seen. */
    private long anchorAt;
    private long anchorMs;
    private int anchorPeriod;
    /** A read shown before its time: the channel's own scoreboard was read showing its score. */
    private Read forced;
    private int lead;
    /** How far the picture was measured behind the scores ({when, ms}), from its own clock read off it. */
    private final java.util.ArrayDeque<long[]> measured = new java.util.ArrayDeque<>();

    /** Everything read forgotten (another channel). */
    public void clear() {
        reads.clear();
        anchorAt = 0;
        forced = null;
        lead = 0;
        measured.clear();
    }

    /** The stream's delay behind the real game. */
    public void behind(long ms) {
        behindMs = Math.max(0, ms);
    }

    public long behind() {
        return behindMs;
    }

    /** A read of the scores, as it came at now. */
    public void add(Game g, long now) {
        if (g == null) return;
        Read last = reads.isEmpty() ? null : reads.get(reads.size() - 1);
        if (last != null && last.g.id != null && !last.g.id.equals(g.id)) {
            clear();
            last = null;
        }
        double secs = clockOf(g);
        // The same minute as before: its seconds still count from when it was first read.
        long minuteAt = last != null && last.secs == secs && last.g.period == g.period ? last.minuteAt : now;
        reads.add(new Read(g, now, minuteAt));
        // The old reads dropped, but always the one the screen is at.
        long screen = now - behindMs;
        while (reads.size() > 1 && reads.get(1).at <= screen - KEEP_MS) reads.remove(0);
    }

    /** The game as the screen shows it at now (the first read when the screen is not there yet); null when none. */
    public Game game(long now) {
        Read r = at(now - behindMs);
        return r == null ? null : r.g;
    }

    /** {"67:23", ""} or {"45:00", "+2"} as the screen shows it at now; null when the game is not running there. */
    public String[] clock(long now) {
        Read r = at(now - behindMs);
        long ms = ms(r, now);
        if (ms < 0) return null;
        return GameClock.show(ms / 1000.0, r.g.period, r.g.displayClock);
    }

    /** The clock on the screen at now in whole seconds; -1 when the game is not running there. */
    public long seconds(long now) {
        long ms = ms(at(now - behindMs), now);
        return ms < 0 ? -1 : ms / 1000;
    }

    /** Whether the clock runs from the broadcaster's own (seen to turn a minute), not only the scores'. */
    public boolean matched() {
        return anchorAt > 0;
    }

    /**
     * The broadcaster's clock was seen to turn to a new minute at the moment at (when that frame was on the screen): the
     * minute is the nearest whole one to what the scores make it, and the clock runs from there. False when the game is
     * not running there or the scores are too far from any minute to say which (more than 25 s either side).
     */
    public boolean minuteTurned(long at) {
        Read r = at(at - behindMs);
        long ms = scoresMs(r, at);
        if (ms < 0) return false;
        long minute = Math.round(ms / 60_000.0);
        if (Math.abs(ms - minute * 60_000L) > 25_000L) return false;
        anchorAt = at;
        anchorMs = minute * 60_000L;
        anchorPeriod = r.g.period;
        return true;
    }

    /**
     * The channel's own clock read off the picture (ChannelReader): it showed secs at the moment at. The clock runs
     * from it, pulled into the second it showed each time it is read (so it settles to within a fraction of one), and
     * how far the picture runs behind the scores is measured from it. False when the reading can't be this game's.
     */
    public boolean screenClock(long secs, long at) {
        Read r = at(at - behindMs);
        if (r == null || r.g.state != Game.LIVE || secs < 0) return false;
        long raw = rawMs(at);
        if (raw >= 0) {
            long behind = raw - secs * 1000L;
            if (behind < -60_000L || behind > 300_000L) return false;
            measured.addLast(new long[]{at, behind});
            while (!measured.isEmpty() && measured.peekFirst()[0] < at - MEASURE_MS) measured.removeFirst();
        }
        int period = r.g.halftime ? r.g.period + 1 : r.g.period;
        long lo = secs * 1000L, hi = lo + 999;
        if (anchorAt > 0 && anchorPeriod == period) {
            long predicted = anchorMs + (at - anchorAt);
            if (predicted >= lo - 2000 && predicted <= hi + 2000) {
                anchorMs = Math.max(lo, Math.min(hi, predicted));
                anchorAt = at;
                return true;
            }
        }
        anchorAt = at;
        anchorMs = lo + 500;
        anchorPeriod = period;
        return true;
    }

    /**
     * How far the picture runs behind the scores, as its own clock says (the most of the last few minutes' readings:
     * the scores' seconds are late by up to their read's interval, never early); -1 until read three times.
     */
    public long measuredBehind() {
        if (measured.size() < 3) return -1;
        long most = Long.MIN_VALUE;
        for (long[] m : measured) most = Math.max(most, m[1]);
        return Math.max(0, most);
    }

    /**
     * The channel's own scoreboard read, shows tells whether it shows a score: a newer read's score it shows is shown
     * from now (the score changes as the channel's does); one goal more than the scores have yet, seen twice, is shown
     * too (the channel was quicker). True when the score shown changed.
     */
    public boolean screenScore(java.util.function.BiPredicate<Integer, Integer> shows, long now) {
        Read cur = at(now - behindMs);
        if (cur == null || cur.g.state == Game.PRE) return false;
        int i = reads.indexOf(cur);
        for (int k = reads.size() - 1; k > i; k--) {
            Read r = reads.get(k);
            boolean differs = r.g.homeScore != cur.g.homeScore || r.g.awayScore != cur.g.awayScore;
            if (differs && shows.test(r.g.homeScore, r.g.awayScore)) {
                forced = r;
                lead = 0;
                return true;
            }
        }
        int h = cur.g.homeScore, a = cur.g.awayScore;
        if (shows.test(h, a)) {
            lead = 0;
            return false;
        }
        for (int[] n : new int[][]{{h + 1, a}, {h, a + 1}}) {
            if (!shows.test(n[0], n[1])) continue;
            if (++lead < 2) return false;
            Game copy = Game.from(cur.g.json());
            copy.homeScore = n[0];
            copy.awayScore = n[1];
            Read r = new Read(copy, now, cur.minuteAt);
            reads.add(r);
            forced = r;
            lead = 0;
            return true;
        }
        lead = 0;
        return false;
    }

    /** The screen's clock let go (a new channel, or it was wrong). */
    public void unmatch() {
        anchorAt = 0;
    }

    // ---- the sums ------------------------------------------------------------------------------------------------

    /** The read the screen is at: the latest that came by then, else the first. */
    private Read at(long screen) {
        if (reads.isEmpty()) return null;
        Read best = reads.get(0);
        for (Read r : reads) {
            if (r.at <= screen) best = r;
            else break;
        }
        // The channel's own scoreboard already shows a later read's score: that one, until the reads catch it up.
        if (forced != null) {
            int f = reads.indexOf(forced);
            if (f > reads.indexOf(best)) return forced;
            forced = null;
        }
        return best;
    }

    /** The scores' clock in ms at at, as read (no delay); -1 when not running. */
    private long rawMs(long at) {
        if (reads.isEmpty()) return -1;
        Read r = reads.get(0);
        for (Read x : reads) {
            if (x.at <= at) r = x;
            else break;
        }
        if (r.g.state != Game.LIVE || r.g.halftime) return -1;
        return (long) (r.secs * 1000) + Math.max(0, Math.min(RUN_ON_MS, at - r.minuteAt));
    }

    /** The clock in ms at now as the screen shows it: the broadcaster's when seen this period, else the scores'. */
    private long ms(Read r, long now) {
        long scores = scoresMs(r, now);
        if (scores < 0) return -1;
        if (anchorAt > 0 && r.g.period == anchorPeriod) {
            long seen = anchorMs + (now - anchorAt);
            if (Math.abs(seen - scores) <= TRUST_MS) return Math.max(start(r.g.period), seen);
            anchorAt = 0;
        }
        return scores;
    }

    /** The scores' clock in ms for the screen at now - behind: the read's minute and the seconds since it was read. */
    private long scoresMs(Read r, long now) {
        if (r == null || r.g.state != Game.LIVE || r.g.halftime) return -1;
        long screen = now - behindMs;
        // Before the first read came (just opened, a long delay) the clock is taken back from it, not frozen.
        long since = Math.min(RUN_ON_MS, screen - r.minuteAt);
        long ms = (long) (r.secs * 1000) + since;
        return Math.max(start(r.g.period), ms);
    }

    /** Where a period's clock starts: 0, 45:00, 90:00, 105:00. */
    static long start(int period) {
        return period <= 1 ? 0 : period == 2 ? 45 * 60_000L : period == 3 ? 90 * 60_000L : 105 * 60_000L;
    }

    private static double clockOf(Game g) {
        return g.clock > 0 ? g.clock : Game.minute(g.detail) * 60.0;
    }
}
