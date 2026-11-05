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

    /** Reads kept this long past the delay; the delay itself is at most LiveAlerts' three minutes. */
    private static final long KEEP_MS = 4 * 60_000L;
    /** Seconds go on at most this far past a minute's first read, until the next read (ESPN gives whole minutes). */
    private static final long RUN_ON_MS = 59_999L;
    /** The screen's own clock is let go if the scores disagree with it by more than this. */
    private static final long TRUST_MS = 150_000L;

    private final List<Read> reads = new ArrayList<>();
    private long behindMs;
    /** The broadcaster's clock: at anchorAt (when it was seen) it read anchorSecs, in this period; 0 when not seen. */
    private long anchorAt;
    private long anchorSecs;
    private int anchorPeriod;

    /** Everything read forgotten (another channel). */
    public void clear() {
        reads.clear();
        anchorAt = 0;
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
            reads.clear();
            anchorAt = 0;
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
        anchorSecs = minute * 60L;
        anchorPeriod = r.g.period;
        return true;
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
        return best;
    }

    /** The clock in ms at now as the screen shows it: the broadcaster's when seen this period, else the scores'. */
    private long ms(Read r, long now) {
        long scores = scoresMs(r, now);
        if (scores < 0) return -1;
        if (anchorAt > 0 && r.g.period == anchorPeriod) {
            long seen = anchorSecs * 1000L + (now - anchorAt);
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
