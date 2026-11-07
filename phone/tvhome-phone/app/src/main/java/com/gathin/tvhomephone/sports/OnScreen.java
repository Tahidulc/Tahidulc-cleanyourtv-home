package com.gathin.tvhomephone.sports;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A game as the picture shows it. While the channel's own scoreboard can be read off the picture (ChannelReader), that
 * is what it shows: the clock as the channel has it, to the second (46:12 in added time if the channel runs on, frozen
 * when the channel's is frozen, its "+4" when it shows one), the score the channel shows (read twice running, to be
 * sure) and HT or FT when the channel says so. Without a reading it falls back on the scores: each read kept with when
 * it came and read back as it stood the picture's delay ago (the delay measured off the channel's clock when it was
 * last read, else as set for the channel), the seconds going on between ESPN's whole minutes, or from the moment the
 * channel's clock was seen to turn a minute. Pure: the tests drive it.
 */
public final class OnScreen {
    /** One read of the scores: the game, when it came, and when its clock's minute was first read. */
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
    /** Reads kept this long past the delay. */
    private static final long KEEP_MS = 6 * 60_000L;
    /** Seconds go on at most this far past a minute's first read, until the next read (ESPN gives whole minutes). */
    private static final long RUN_ON_MS = 59_999L;
    /** A reading of the channel's scoreboard holds this long; its score and state a little longer. */
    private static final long READING_MS = 45_000L, SAID_MS = 180_000L, STATE_MS = 40_000L;
    /** The channel's clock seen to turn a minute: let go if the scores disagree with it by more than this. */
    private static final long TRUST_MS = 150_000L;

    private final List<Read> reads = new ArrayList<>();
    private long behindMs;

    /** The channel's clock as read: clockMs at clockAt (frozen: it stays there), the minutes added it showed. */
    private long readAt, clockAt, clockMs, lastSecs = -1, lastSecsAt;
    private boolean frozen;
    private int added = -1;
    /** A read that jumped from where the clock should be, waiting for the next to agree: jumpMs at jumpAt. */
    private long jumpMs, jumpAt;
    /** The score the channel shows (read twice running), and when; the one read once, waiting for a second. */
    private int[] said, pending;
    private long saidAt;
    /** HT or FT as the channel said it, and when. */
    private String state;
    private long stateAt;
    /** A copy of a read with the channel's score or state, kept while it is the one shown. */
    private Game shown;
    private Read shownFrom;
    private String shownKey = "";
    /** The channel's clock seen turning a minute (no reading): from anchorMs at anchorAt, in this period. */
    private long anchorAt, anchorMs;
    private int anchorPeriod;
    /** How far the picture was measured behind the scores ({when, ms}), from its own clock read off it. */
    private final ArrayDeque<long[]> measured = new ArrayDeque<>();

    /** Everything read forgotten (another channel). */
    public void clear() {
        reads.clear();
        readAt = 0;
        lastSecs = -1;
        frozen = false;
        added = -1;
        jumpAt = 0;
        said = pending = null;
        state = null;
        shown = null;
        shownFrom = null;
        anchorAt = 0;
        measured.clear();
    }

    /** The stream's delay behind the real game (used when the channel's scoreboard can't be read). */
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
        long screen = now - behindMs;
        while (reads.size() > 1 && reads.get(1).at <= screen - KEEP_MS) reads.remove(0);
    }

    // ---- the channel's scoreboard, as read ---------------------------------------------------------------------------

    /** Whether the channel's scoreboard has been read lately (its clock): then it is what is shown. */
    public boolean reading(long now) {
        return readAt > 0 && now - readAt < READING_MS;
    }

    /**
     * The channel's clock read off the picture: it showed secs (and added, the minutes added beside it; -1 for none)
     * at the moment at. Read again a few seconds later still showing secs, it is frozen there; going on, the board's
     * runs with it, pulled into the second it showed each time so it settles within a fraction of one. A read that
     * jumps from where the clock should be (a misread, or the channel putting its clock right) is taken only when the
     * next one agrees with it. False for a reading that can't be a game's clock.
     */
    public boolean screenClock(long secs, int added, long at) {
        if (secs < 0 || secs > 130 * 60) return false;
        long lo = secs * 1000L, hi = lo + 999;
        boolean fresh = readAt > 0 && at - readAt < READING_MS;
        if (fresh && lastSecs == secs) {
            // The same as before, three seconds or more later: the channel's clock stands still (half time, a stop).
            if (at - lastSecsAt >= 3000) {
                frozen = true;
                clockMs = lo;
                clockAt = at;
            }
            jumpAt = 0;
        } else {
            long expect = frozen ? clockMs : clockMs + (at - clockAt);
            boolean near = expect >= lo - 2000 && expect <= hi + 2000;
            if (fresh && !near) {
                boolean agrees = jumpAt > 0 && at - jumpAt < 10_000L
                        && (jumpMs == lo + 500 || Math.abs(jumpMs + (at - jumpAt) - (lo + 500)) <= 2500);
                if (!agrees) {
                    jumpMs = lo + 500;
                    jumpAt = at;
                    readAt = at;
                    return true;
                }
            }
            jumpAt = 0;
            clockMs = fresh && near && !frozen ? Math.max(lo, Math.min(hi, expect)) : lo + 500;
            frozen = false;
            clockAt = at;
            lastSecs = secs;
            lastSecsAt = at;
        }
        this.added = added;
        readAt = at;
        long raw = rawMs(at);
        if (raw >= 0 && !frozen) {
            long behind = raw - secs * 1000L;
            if (behind > -60_000L && behind < 300_000L) {
                measured.addLast(new long[]{at, behind});
                while (!measured.isEmpty() && measured.peekFirst()[0] < at - MEASURE_MS) measured.removeFirst();
            }
        }
        return true;
    }

    /**
     * Whether a clock seen on the picture can be this game's, to tell its scoreboard from the rest (a time of day, a
     * countdown): within ten minutes of the scores' clock while it runs, about 45:00 or later at half time, 80:00 or
     * later once it's over, the first minutes before kick-off.
     */
    public boolean plausible(long secs, long at) {
        if (reads.isEmpty() || secs < 0 || secs > 130 * 60) return false;
        long raw = rawMs(at);
        if (raw >= 0) return Math.abs(raw - secs * 1000L) <= 10 * 60_000L;
        Game g = upTo(at).g;
        if (g.halftime) return secs >= 40 * 60;
        if (g.state == Game.POST) return secs >= 80 * 60;
        return secs <= 5 * 60;
    }

    /**
     * The score read off the channel's scoreboard at at: shown at once when it is a score the scores have had and no
     * fewer goals than the channel showed (a goal going in: the board changes with the channel's, a read later at
     * most), else once it is read the same twice running (a score the scores haven't got yet, a goal taken back, a
     * misread).
     */
    public void screenScore(int home, int away, long at) {
        if (home < 0 || away < 0 || home > 30 || away > 30) return;
        int[] s = {home, away};
        boolean twice = pending != null && pending[0] == home && pending[1] == away;
        boolean onwards = said == null || (home >= said[0] && away >= said[1]);
        if (twice || (onwards && known(home, away))) {
            said = s;
            saidAt = at;
        }
        pending = s;
    }

    /** Whether the scores have had this score. */
    private boolean known(int home, int away) {
        for (Read r : reads) if (r.g.homeScore == home && r.g.awayScore == away) return true;
        return false;
    }

    /** "HT" or "FT" read off the channel's scoreboard at at. */
    public void screenState(String s, long at) {
        state = s;
        stateAt = at;
    }

    /**
     * Whether what the channel's scoreboard shows fits this game: a score it has had, or a clock within six minutes of
     * the scores' (another game on the channel fits neither).
     */
    public boolean fits(int[] score, long secs, long at) {
        if (score != null) {
            for (Read r : reads) if (r.g.homeScore == score[0] && r.g.awayScore == score[1]) return true;
        }
        long raw = rawMs(at);
        return secs >= 0 && raw >= 0 && Math.abs(raw - secs * 1000L) <= 6 * 60_000L;
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

    // ---- what is shown ---------------------------------------------------------------------------------------------

    /** The game as the screen shows it at now (the first read when the screen is not there yet); null when none. */
    public Game game(long now) {
        Read r = at(now - behindMs);
        if (r == null) return null;
        int[] score = said != null && now - saidAt < SAID_MS ? said : null;
        String st = state != null && now - stateAt < STATE_MS ? state : null;
        boolean running = reading(now) && !frozen;
        if (score != null) {
            // The latest read with the channel's score (its clock, its stats), else the one the screen is at.
            for (int i = reads.size() - 1; i >= 0; i--) {
                Read x = reads.get(i);
                if (x.g.homeScore == score[0] && x.g.awayScore == score[1]) {
                    r = x;
                    break;
                }
            }
        }
        boolean fixScore = score != null && (r.g.homeScore != score[0] || r.g.awayScore != score[1]);
        boolean fixState = "FT".equals(st) ? r.g.state != Game.POST : "HT".equals(st) ? !r.g.halftime
                : running && (r.g.halftime || r.g.state != Game.LIVE);
        if (!fixScore && !fixState) return r.g;
        String key = (fixScore ? score[0] + "-" + score[1] : "") + "|" + (fixState ? (st == null ? "run" : st) : "");
        if (shown == null || shownFrom != r || !key.equals(shownKey)) {
            Game g = Game.from(r.g.json());
            if (fixScore) {
                g.homeScore = score[0];
                g.awayScore = score[1];
            }
            if (fixState) {
                if ("FT".equals(st)) {
                    g.state = Game.POST;
                    g.halftime = false;
                } else if ("HT".equals(st)) {
                    g.state = Game.LIVE;
                    g.halftime = true;
                } else {
                    // The channel's clock is running: in play, whatever the scores say yet.
                    g.state = Game.LIVE;
                    g.halftime = false;
                    if (g.period < 1) g.period = 1;
                }
            }
            shown = g;
            shownFrom = r;
            shownKey = key;
        }
        return shown;
    }

    /**
     * {"67:23", ""} or {"45:00", "+2"} as the screen shows it at now; null when the game is not running there. Read off
     * the channel: its clock as it has it, with its minutes added.
     */
    public String[] clock(long now) {
        Game g = game(now);
        if (g == null || g.state != Game.LIVE || g.halftime) return null;
        if (reading(now)) {
            long ms = frozen ? clockMs : clockMs + (now - clockAt);
            long s = ms / 1000;
            return new String[]{String.format(Locale.UK, "%02d:%02d", s / 60, s % 60), added > 0 ? "+" + added : ""};
        }
        Read r = at(now - behindMs);
        long ms = ms(r, now);
        if (ms < 0) return null;
        return GameClock.show(ms / 1000.0, r.g.period, r.g.displayClock);
    }

    /** The clock on the screen at now in whole seconds; -1 when the game is not running there. */
    public long seconds(long now) {
        Game g = game(now);
        if (g == null || g.state != Game.LIVE || g.halftime) return -1;
        if (reading(now)) return (frozen ? clockMs : clockMs + (now - clockAt)) / 1000;
        long ms = ms(at(now - behindMs), now);
        return ms < 0 ? -1 : ms / 1000;
    }

    /** Whether the clock is the channel's own (read off it, or seen turning a minute), not only the scores'. */
    public boolean matched() {
        return readAt > 0 || anchorAt > 0;
    }

    /**
     * Without a reading: the channel's clock was seen to turn to a new minute at the moment at: the minute is the
     * nearest whole one to what the scores make it, and the clock runs from there. False when the game is not running
     * there or the scores are too far from any minute to say which (more than 25 s either side).
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

    /** The screen's clock let go (a new channel, or it was wrong). */
    public void unmatch() {
        anchorAt = 0;
        readAt = 0;
        jumpAt = 0;
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

    /** The latest read that had come by at (no delay), else the first. */
    private Read upTo(long at) {
        Read r = reads.get(0);
        for (Read x : reads) {
            if (x.at <= at) r = x;
            else break;
        }
        return r;
    }

    /** The scores' clock in ms at at, as read (no delay); -1 when not running. */
    private long rawMs(long at) {
        if (reads.isEmpty()) return -1;
        Read r = upTo(at);
        if (r.g.state != Game.LIVE || r.g.halftime) return -1;
        return (long) (r.secs * 1000) + Math.max(0, Math.min(RUN_ON_MS, at - r.minuteAt));
    }

    /** Without a reading: the clock in ms at now, the minute-turn anchor's when seen this period, else the scores'. */
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
        return g.clock > 0 ? g.clock : GameClock.fromWords(g);
    }
}
