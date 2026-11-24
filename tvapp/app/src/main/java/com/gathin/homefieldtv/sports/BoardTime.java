package com.gathin.homefieldtv.sports;

import com.gathin.broadcast.Whistle;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Homefield TV's board's game as the picture shows it, second by second, decided as the TV launcher's board over TiviMate
 * decides it (BoardCover's screen and whistled): each reading of the scores kept as it came (BoardClock), the one the
 * channel has reached shown (its wait behind, InStep), its clock counted on (the channel's own once set at a kick-off on
 * the screen), and half time and full time as soon as the whistle reaches the picture (Whistle, the launcher's and the
 * phone's one rule, from the data: ESPN's flip at each read, FotMob's status with its own length of each half to the
 * second, the minutes added), rather than a read later behind the channel's wait.
 *
 * <p>Never early: never before the half's end on the board's clock (45:00, 90:00, 105:00, 120:00), and on the channel's
 * own clock (set at kick-off) never before the minutes added are up, whichever says the half is over (the whistle, or the
 * reading the screen has reached), though no longer than HOLD_MS after that reading reached the screen (a clock set late).
 *
 * <p>Pure, on the main thread: the tests replay made-up halves through it.
 */
public final class BoardTime {
    /** Half or full time a reading brought is held for the channel's own clock at most this long after it reached the screen. */
    public static final long HOLD_MS = 2 * 60_000L;

    /** The readings kept, oldest first (BoardClock.add). */
    public final ArrayDeque<BoardClock.Reading> readings = new ArrayDeque<>();
    /** The clock as last shown, so it counts on from there. */
    public final BoardClock.Steady steady = new BoardClock.Steady();
    /** The clock set at a kick-off on the screen (OK at the card): {base seconds, when, half}; null for none. */
    public long[] anchor;
    private final Whistle whistle = new Whistle();
    /** The minutes added put up for each half (1 to 4), as the data has them; 0 not yet. */
    private final int[] added = new int[5];
    private String game = "";

    /** What the board shows now. */
    public static final class Shown {
        /** The reading the screen is at (null: none yet), half or full time once the whistle has reached the picture. */
        public BoardClock.Reading r;
        /** Its clock in seconds while the half runs on the board (-1: none: before kick-off, at a break, at full time). */
        public double secs = -1;
        /** The whistle said it before the reading the screen is at did; a reading's break held for the channel's clock. */
        public boolean whistled, held;
    }

    /** Everything forgotten (another game, or none). */
    public void reset() {
        readings.clear();
        steady.reset();
        anchor = null;
        Arrays.fill(added, 0);
        whistle.forget();
        game = "";
    }

    private void forGame(String id) {
        String g = id == null ? "" : id;
        if (g.equals(game)) return;
        game = g;
        Arrays.fill(added, 0);
    }

    /** Pure: what a read of the scores says for the whistle: not begun, in play, at a break (half time), over. */
    public static int phase(Game g) {
        if (g == null || g.state == Game.PRE) return Whistle.NOT_BEGUN;
        if (g.state == Game.POST) return Whistle.AT_END;
        return g.halftime ? Whistle.AT_BREAK : Whistle.IN_PLAY;
    }

    /** Pure: the half a read is in: ESPN's own period, else its minute's (0: neither says). */
    public static int period(Game g) {
        if (g == null) return 0;
        if (g.period >= 1 && g.period <= 4) return g.period;
        return BoardClock.periodOfDetail(g.detail);
    }

    /** A reading of game g's scores seen at seen, as it came (the whistle puts the channel's wait on it). */
    public void took(Game g, long seen) {
        if (g == null) return;
        forGame(g.id);
        BoardClock.add(readings, BoardClock.Reading.of(g, seen));
        whistle.data(seen, g.id, period(g), phase(g), g.clock);
    }

    /** The minutes added as the game's summary has them ({first half, second, extra time's two}; 0 or less: not yet). */
    public void added(String id, int[] announced) {
        forGame(id);
        for (int i = 0; announced != null && i < Math.min(4, announced.length); i++) {
            int n = announced[i];
            if (n <= 0 || n >= 20) continue;
            added[i + 1] = n;
            whistle.added(id, i + 1, n);
        }
    }

    /** FotMob's status for game id as read at at (FotMob's own times give each half's length to the second). */
    public void fotmob(long at, String id, Whistle.Status status) {
        forGame(id);
        whistle.fotmob(at, id, status);
    }

    /** Pure: the latest reading in play (not before kick-off, not at a break, not over) the screen has reached; null none. */
    static BoardClock.Reading lastInPlay(Iterable<BoardClock.Reading> list, long screenNow) {
        BoardClock.Reading pick = null;
        for (BoardClock.Reading r : list) {
            if (r.seen > screenNow) break;
            if (r.state == Game.LIVE && !r.halftime) pick = r;
        }
        return pick;
    }

    private static int half(String detail, double secs) {
        int p = BoardClock.periodOfDetail(detail);
        return p > 0 ? p : BoardClock.periodOfSecs((long) secs);
    }

    /**
     * What the board shows for game id at now, the channel waitMs behind the scores: the reading it has reached, half time
     * or full time as the whistle reaches the picture, and the clock while the half runs.
     */
    public Shown at(String id, long now, long waitMs) {
        Shown s = new Shown();
        long wait = Math.max(0, waitMs), screenNow = now - wait;
        BoardClock.Reading r = BoardClock.atScreen(readings, screenNow);
        s.r = r;
        if (r == null || r.state == Game.PRE) return s;
        if (r.state == Game.LIVE && !r.halftime) {
            // The clock: counted from the kick-off on the screen (the channel's own), else the scores' counting on through
            // the minutes added; as the board shows it, never stopping or stepping back by a little.
            double set = BoardClock.anchoredSecs(anchor, now, r.detail);
            double said = set >= 0 ? set : BoardClock.clockSecs(r, screenNow);
            if (said < 0) return s;
            double secs = steady.shown(id, r.detail, said, now);
            int to = whistle.over(now, id, half(r.detail, secs), (long) secs, wait, set >= 0);
            if (to == Whistle.NONE) {
                s.secs = secs;
                return s;
            }
            s.r = BoardClock.over(r, to == Whistle.FT);
            s.whistled = true;
            return s;
        }
        // Half or full time as the reading the screen is at has it: on the channel's own clock (set at kick-off), not before
        // the half's end and the minutes added (the referee plays at least those), for HOLD_MS at most.
        BoardClock.Reading live = lastInPlay(readings, screenNow);
        if (live == null || now - (r.seen + wait) >= HOLD_MS) return s;
        double set = BoardClock.anchoredSecs(anchor, now, live.detail);
        if (set < 0) return s;
        int p = half(live.detail, set);
        if (set >= Whistle.end(p) + added[p] * 60L) return s;
        s.r = live;
        s.secs = steady.shown(id, live.detail, set, now);
        s.held = true;
        return s;
    }

    // ---- FotMob's read, for the whistle and the VAR check -----------------------------------------------------------------

    /** FotMob's read of the board's game: its status for the whistle, whether it flags a VAR check, when it was read. */
    public static final class FotMobRead {
        public Whistle.Status status;
        public boolean varPending;
        public long at;
    }

    /**
     * FotMob's read of game g (off the main thread): its once-a-minute read, shared with the game's page; null when FotMob
     * doesn't have the game.
     */
    public static FotMobRead readFotMob(Game g) {
        if (g == null || g.id == null) return null;
        FotMob.Match m = FotMob.forGame(g.id, g.homeName, g.awayName, g.kickoff);
        if (m == null) return null;
        FotMobRead r = new FotMobRead();
        r.status = m.status;
        r.varPending = m.varPending;
        r.at = m.readAt;
        return r;
    }
}
