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
 * channel's clock was seen to turn a minute.
 *
 * <p>Half time and full time come as the whistle reaches the picture (Whistle: the data's flips, the channel's clock
 * stopping or gone, the minutes added), not when the delayed read gets there. The score only goes as a score can: a
 * goal a side at a time, the picture's own read steadily or one the scores have had, and back only when the scores
 * have it back (a goal ruled out) or the picture steadily shows it; HT and FT read off the picture count only when the
 * data or the channel's clock (stopped, or gone, at a half's end) say so too. Pure: the tests drive it.
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
    /** The board's clock is put back only when what it is told is this far behind where it was counting (a real correction). */
    static final long CORRECT_MS = 30_000L;
    /** The channel's clock read the same this far apart stands still (it is read every two seconds). */
    static final long STILL_MS = 1_800L;
    /** The channel's score the scores haven't had: believed when read the same this many times running, over this long. */
    static final int STEADY_READS = 3;
    static final long STEADY_MS = 4_000L;
    /** The board's score moves a goal at a time, this far apart (two goals in one read come one after the other). */
    static final long STEP_MS = 1_500L;
    /** The picture's delay as measured: readings this far from the middle one are misreads, left out. */
    static final long OUTLIER_MS = 20_000L;
    /** The channel's clock is read running on the picture when it was read this lately and not standing still. */
    static final long RUNNING_MS = 5_000L;

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
    /** When each half is over on the picture: the data's flips, the channel's clock stopping or gone, the minutes added. */
    private final com.gathin.broadcast.Whistle whistle = new com.gathin.broadcast.Whistle();
    /** The score the board has shown, for which game, since when; and whether the channel's score read lately came steadily down. */
    private int[] showing;
    private String showingGame = "";
    private long showingAt;
    /** The channel's score read the same this many times running, since when, and every one of them as a score ("2-1"). */
    private int pendingRuns;
    private long pendingFrom;
    private boolean pendingSure;

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
        runKey = null;
        runMs = -1;
        whistle.forget();
        showing = null;
        showingGame = "";
        pendingRuns = 0;
    }

    /** When each half is over on the picture (the board's controller tells it FotMob's status and the minutes added). */
    public com.gathin.broadcast.Whistle whistle() {
        return whistle;
    }

    /** The game read last ("" none). */
    private String gameId() {
        return reads.isEmpty() || reads.get(reads.size() - 1).g.id == null ? "" : reads.get(reads.size() - 1).g.id;
    }

    /** Pure: what a read of the scores says for the whistle: not begun, in play, at a break (half time), over. */
    static int phase(Game g) {
        if (g.state == Game.PRE) return com.gathin.broadcast.Whistle.NOT_BEGUN;
        if (g.state == Game.POST) return com.gathin.broadcast.Whistle.AT_END;
        return g.halftime ? com.gathin.broadcast.Whistle.AT_BREAK : com.gathin.broadcast.Whistle.IN_PLAY;
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
        whistle.data(now, g.id, g.period, phase(g), secs);
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
            // The same as before, STILL_MS or more later (a running clock would have moved on a second or two): the channel's
            // clock stands still (the whistle, a stop).
            if (at - lastSecsAt >= STILL_MS) {
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
                    // Not taken yet: the picture's clock is still read (going, as it was).
                    whistle.picture(at, gameId(), frozen ? clockMs / 1000 : (clockMs + (at - clockAt)) / 1000, frozen);
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
        whistle.picture(at, gameId(), secs, frozen);
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

    /** A read of the channel's scoreboard at at with no clock on it (gone at the whistle, or away for a replay). */
    public void screenNone(long at) {
        whistle.pictureNone(at, gameId());
    }

    /** The score read off the channel's scoreboard at at, read as a score ("2-1"). */
    public void screenScore(int home, int away, long at) {
        screenScore(home, away, at, true);
    }

    /**
     * The score read off the channel's scoreboard at at (sure: read as a score, "2-1", rather than two numbers that could be
     * anything). Taken only as a score can go from the one the board shows: a goal more for one side, at once when the scores
     * have had that score, else once read the same STEADY_READS times over STEADY_MS as a score (the channel quicker than
     * the scores); a goal fewer when the scores have had that score too and it is read so twice with the scores' latest
     * read having it, or steadily (a goal ruled out, or the board ahead of the picture); never several goals at once from
     * the picture alone (a stats graphic's numbers, a digit read wrong: they read the same time after time too).
     */
    public void screenScore(int home, int away, long at, boolean sure) {
        if (home < 0 || away < 0 || home > 30 || away > 30) return;
        boolean again = pending != null && pending[0] == home && pending[1] == away;
        if (again) {
            pendingRuns++;
            pendingSure &= sure;
        } else {
            pending = new int[]{home, away};
            pendingRuns = 1;
            pendingFrom = at;
            pendingSure = sure;
        }
        boolean steady = pendingRuns >= STEADY_READS && at - pendingFrom >= STEADY_MS;
        boolean known = known(home, away);
        int[] base = showing != null && showingGame.equals(gameId()) ? showing : said != null ? said : atScreen(at);
        boolean take;
        if (base == null) {
            take = known || steady && pendingSure;
        } else {
            int dh = home - base[0], da = away - base[1];
            if (dh == 0 && da == 0) take = true;
            else if (dh >= 0 && da >= 0 && dh + da == 1) take = known || steady && pendingSure;
            else if (dh <= 0 && da <= 0 && dh + da == -1) take = known && (pendingRuns >= 2 && latestIs(home, away) || steady);
            else take = false;
        }
        if (take) {
            said = new int[]{home, away};
            saidAt = at;
        }
    }

    /** The score of the read the screen is at, at at; null with none. */
    private int[] atScreen(long at) {
        Read r = at(at - behindMs);
        return r == null ? null : new int[]{r.g.homeScore, r.g.awayScore};
    }

    /** Whether the scores' latest read has this score. */
    private boolean latestIs(int home, int away) {
        if (reads.isEmpty()) return false;
        Game g = reads.get(reads.size() - 1).g;
        return g.homeScore == home && g.awayScore == away;
    }

    /** Whether the scores have had this score. */
    private boolean known(int home, int away) {
        for (Read r : reads) if (r.g.homeScore == home && r.g.awayScore == away) return true;
        return false;
    }

    /**
     * "HT" or "FT" read off the channel's scoreboard at at: taken only when it is so: the scores have the half over, or the
     * channel's clock is not going (stopped, or gone) and was last read at the half's end or past it. A ticker's "HT 1-0"
     * about another game, or a word misread, while the game goes on, is not half time.
     */
    public void screenState(String s, long at) {
        if (s == null || !confirmed(s, at)) return;
        state = s;
        stateAt = at;
    }

    /** Whether the data or the channel's clock say HT (or FT) read off the picture at at is so. */
    boolean confirmed(String s, long at) {
        boolean ft = "FT".equals(s);
        Game last = reads.isEmpty() ? null : reads.get(reads.size() - 1).g;
        if (last != null && (ft ? last.state == Game.POST : last.halftime || last.state == Game.POST)) return true;
        // The channel's clock going on the picture: play on.
        if (readAt > 0 && at - readAt <= RUNNING_MS && !frozen) return false;
        if (lastSecs < 0) return false;
        int p = last != null && last.period > 0 ? last.period : com.gathin.broadcast.Whistle.periodOf(lastSecs);
        if (ft && p != 2 && p != 4) return false;
        long end = com.gathin.broadcast.Whistle.end(p);
        return lastSecs >= end - 30 && lastSecs < end + 30 * 60;
    }

    /**
     * Whether what the channel's scoreboard shows fits this game: a score it has had lately, with the clock (when read)
     * within six minutes of the scores'; with no score, the clock within three minutes. Another game on the channel,
     * at a like time or with a like score, fits neither.
     */
    public boolean fits(int[] score, long secs, long at) {
        long raw = rawMs(at);
        boolean clock = secs >= 0 && raw >= 0;
        if (score != null) {
            boolean had = false;
            for (int i = reads.size() - 1; i >= 0 && i >= reads.size() - 40; i--) {
                Read r = reads.get(i);
                if (r.g.homeScore == score[0] && r.g.awayScore == score[1]) {
                    had = true;
                    break;
                }
            }
            return had && (secs < 0 || clock && Math.abs(raw - secs * 1000L) <= 6 * 60_000L);
        }
        return clock && Math.abs(raw - secs * 1000L) <= 3 * 60_000L;
    }

    /**
     * How far the picture runs behind the scores, as its own clock says (the most of the last few minutes' readings:
     * the scores' seconds are late by up to their read's interval, never early); -1 until read three times.
     */
    public long measuredBehind() {
        if (measured.size() < 3) return -1;
        long[] v = new long[measured.size()];
        int i = 0;
        for (long[] m : measured) v[i++] = m[1];
        java.util.Arrays.sort(v);
        // A clock misread (a digit wrong) far from the rest says nothing of the delay: only those near the middle one count,
        // and only when most of them agree (else the delay is not changed by them).
        long mid = v[v.length / 2], most = Long.MIN_VALUE;
        int near = 0;
        for (long x : v) {
            if (Math.abs(x - mid) > OUTLIER_MS) continue;
            near++;
            most = Math.max(most, x);
        }
        if (near * 2 <= v.length) return -1;
        return Math.max(0, most);
    }

    /**
     * Whether the scores have a score the screen hasn't shown yet (a goal on its way down the stream): what would give
     * it away (the stats' shots on target) waits.
     */
    public boolean ahead(long now) {
        if (reads.isEmpty()) return false;
        Game shown = game(now), latest = reads.get(reads.size() - 1).g;
        return shown != null && (latest.homeScore != shown.homeScore || latest.awayScore != shown.awayScore);
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
        int state0 = r.g.state, period0 = r.g.period, stateNow = state0, periodNow = period0;
        boolean ht0 = r.g.halftime, htNow = ht0;
        if ("FT".equals(st)) {
            stateNow = Game.POST;
            htNow = false;
        } else if ("HT".equals(st)) {
            stateNow = Game.LIVE;
            htNow = true;
        } else if (running && (ht0 || state0 != Game.LIVE)) {
            // The channel's clock is running: in play, whatever the scores say yet.
            stateNow = Game.LIVE;
            htNow = false;
            if (periodNow < 1) periodNow = 1;
        }
        // The whistle reaching the picture before the read the screen is at says the half is over (Whistle).
        if (stateNow == Game.LIVE && !htNow) {
            long secs = boardSecs(now);
            int p = periodNow > 0 ? periodNow : secs >= 0 ? com.gathin.broadcast.Whistle.periodOf(secs) : 0;
            int to = whistle.over(now, r.g.id, p, secs, behindMs, reading(now) || anchorAt > 0);
            if (to == com.gathin.broadcast.Whistle.FT) {
                stateNow = Game.POST;
            } else if (to == com.gathin.broadcast.Whistle.HT) {
                htNow = true;
            }
        }
        int[] sc = steadyScore(score != null ? score : new int[]{r.g.homeScore, r.g.awayScore}, score != null, r.g.id, now);
        if (sc[0] == r.g.homeScore && sc[1] == r.g.awayScore && stateNow == state0 && htNow == ht0 && periodNow == period0) return r.g;
        String key = sc[0] + "-" + sc[1] + "|" + stateNow + "|" + htNow + "|" + periodNow;
        if (shown == null || shownFrom != r || !key.equals(shownKey)) {
            Game g = Game.from(r.g.json());
            g.homeScore = sc[0];
            g.awayScore = sc[1];
            g.state = stateNow;
            g.halftime = htNow;
            g.period = periodNow;
            shown = g;
            shownFrom = r;
            shownKey = key;
        }
        return shown;
    }

    /**
     * The score the board shows for target (the channel's own when screen, else the read the screen is at): never more
     * than a goal a side at a time (STEP_MS apart), never back unless it is the channel's own (taken only as screenScore
     * allows) or the scores' latest read has it back too (a goal ruled out). A read the screen goes back to (the delay put
     * longer) takes no goal off, and gives none twice.
     */
    private int[] steadyScore(int[] target, boolean screen, String id, long now) {
        String g = id == null ? "" : id;
        if (showing == null || !g.equals(showingGame)) {
            showing = target.clone();
            showingGame = g;
            showingAt = now;
            return showing;
        }
        if (target[0] == showing[0] && target[1] == showing[1]) return showing;
        if (now - showingAt < STEP_MS) return showing;
        Game latest = reads.get(reads.size() - 1).g;
        int[] next = showing.clone();
        if (target[0] > showing[0]) next[0]++;
        else if (target[1] > showing[1]) next[1]++;
        else if (target[0] < showing[0] && (screen || latest.homeScore < showing[0])) next[0]--;
        else if (target[1] < showing[1] && (screen || latest.awayScore < showing[1])) next[1]--;
        if (next[0] == showing[0] && next[1] == showing[1]) return showing;
        showing = next;
        showingAt = now;
        return showing;
    }

    /** The board's clock now in seconds, as it was counting (the channel's while its clock stands still); -1 none. */
    private long boardSecs(long now) {
        if (reading(now) && frozen && !stoppedAtEnd()) return clockMs / 1000;
        if (runKey != null && runMs >= 0) return (runMs + Math.max(0, now - runAt)) / 1000;
        long src = sourceMs(now);
        return src < 0 ? -1 : src / 1000;
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

    /** The board's running clock: what it showed last (ms) and when, and for which game and half. */
    private String runKey;
    private long runMs = -1, runAt;

    /**
     * The board's clock at now: always counting on, a second a second, until half time or full time shows: never frozen
     * at the period's end (in added time it runs on, "92:15"), nor held at a minute's end waiting for the scores' next
     * read, nor stepping back by a little: the later of where it was counting and what the channel (read off it) or the
     * scores say, unless they say it is more than 30 s behind (a real correction). {"92:15", "+4"} with the minutes added
     * the channel shows, else ""; null when the game is not running there.
     */
    public String[] boardClock(long now) {
        Game g = game(now);
        if (g == null || g.state != Game.LIVE || g.halftime) {
            runKey = null;
            runMs = -1;
            return null;
        }
        long src = sourceMs(now);
        if (src < 0) return null;
        int half = g.period > 0 ? g.period : src < 45 * 60_000L ? 1 : src < 90 * 60_000L ? 2 : src < 105 * 60_000L ? 3 : 4;
        String key = (g.id == null ? "" : g.id) + "|" + half;
        // The channel's clock standing still (the whistle, a stop): the board's with it, not counting on past it.
        long ms = reading(now) && frozen && !stoppedAtEnd() ? hold(key, src, now) : running(key, src, now);
        long s = ms / 1000;
        return new String[]{String.format(Locale.UK, "%02d:%02d", s / 60, s % 60), reading(now) && added > 0 ? "+" + added : ""};
    }

    /** Whether the channel's clock stands at a half's end itself (45:00, 90:00, 105:00, 120:00): added time runs on there. */
    private boolean stoppedAtEnd() {
        long secs = clockMs / 1000;
        return secs == 2700 || secs == 5400 || secs == 6300 || secs == 7200;
    }

    /** The clock held where the channel's stands (src), counted on from there when it goes again. */
    long hold(String key, long src, long now) {
        runKey = key;
        runMs = src;
        runAt = now;
        return src;
    }

    /** The running clock for a game and half: on from where it was shown at least; what it is told when later, or far behind. */
    long running(String key, long src, long now) {
        if (!key.equals(runKey) || runMs < 0) {
            runKey = key;
            runMs = src;
            runAt = now;
            return src;
        }
        long counted = runMs + Math.max(0, now - runAt);
        long ms = src < counted - CORRECT_MS ? src : Math.max(counted, src);
        runMs = ms;
        runAt = now;
        return ms;
    }

    /**
     * What the channel (its clock read off it, standing still or going) or the scores say the clock is at now, in ms,
     * counted on from their last read without a cap: ESPN's clock, or its words' minute when that is later (ESPN may
     * stand its clock still in added time while its words go on, "90'+3'"), or the channel's minute seen turning. -1
     * when the game is not running there.
     */
    private long sourceMs(long now) {
        if (reading(now)) {
            // Standing still past a half's end: the whistle (the board stands with it). Standing at the half's end itself
            // (45:00, 90:00, a +4 beside it): the minutes added go on there, and so does the board, from when it stood.
            if (frozen) return stoppedAtEnd() ? clockMs + Math.max(0, now - lastSecsAt) : clockMs;
            return clockMs + (now - clockAt);
        }
        Read r = at(now - behindMs);
        if (r == null || r.g.state != Game.LIVE || r.g.halftime) return -1;
        long screen = now - behindMs;
        long ms = (long) (r.secs * 1000) + Math.max(0, screen - r.minuteAt);
        int minute = Game.minute(words(r.g));
        if (minute > 0) ms = Math.max(ms, (minute - 1) * 60_000L + Math.max(0, screen - wordsAt(r)));
        if (anchorAt > 0 && r.g.period == anchorPeriod) {
            long seen = anchorMs + (now - anchorAt);
            if (Math.abs(seen - ms) <= TRUST_MS) ms = seen;
        }
        return Math.max(start(r.g.period), ms);
    }

    private static String words(Game g) {
        return g.displayClock != null && !g.displayClock.isEmpty() ? g.displayClock : g.detail == null ? "" : g.detail;
    }

    /** When the minute ESPN's words give for read r was first read: the earliest read running up to it with the same words. */
    private long wordsAt(Read r) {
        int i = reads.indexOf(r);
        long at = r.at;
        String w = words(r.g);
        for (int j = i; j >= 0; j--) {
            Read x = reads.get(j);
            if (x.g.period != r.g.period || !w.equals(words(x.g))) break;
            at = x.at;
        }
        return at;
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
