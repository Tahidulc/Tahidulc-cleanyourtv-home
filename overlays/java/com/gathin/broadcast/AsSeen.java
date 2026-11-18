package com.gathin.broadcast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * No spoilers, the same on the TV and the phone: whatever the scoreboard says from FotMob's or ESPN's live data waits
 * for the picture. The channel runs behind the real game (30 to 90 seconds, a playlist's more) and the data runs ahead
 * of it, FotMob's often ahead of ESPN's: a "Great finish", an expected-goals total that jumps, a booking or a VAR check
 * said as soon as the data has it gives away what the family is about to see.
 *
 * <p>What the picture has shown is the board's own game as it shows it (Picture): its half, its clock in step with the
 * channel's (known when it is the channel's own, read off it or set at kick-off; else the scores' guess), its score, and
 * how far the channel runs behind the scores. Each thing read carries what it could give away (Stamp): when it came, the
 * game as it stood then, the latest moment in it (a shot's minute, a card's), the goals it has. It is said only once the
 * picture has reached all of that: its goals on the board, its minute passed by the board's clock, and it at least the
 * channel's lag old (the longest a channel runs behind, SAFE_MS, while the board's clock is only the scores' guess). A
 * short history of reads (Reads) gives the newest one that is so: the totals as they stood at the moment on the picture.
 *
 * <p>Pure, for the tests: the apps keep the reads and say where the picture is.
 */
public final class AsSeen {
    private AsSeen() { }

    /** The longest a channel runs behind the scores (both apps' most): the wait while the board's clock is not the channel's. */
    public static final long SAFE_MS = 180_000L;
    /** A known lag's own error: a read is held this much longer. */
    public static final long LAG_MARGIN_MS = 10_000L;
    /**
     * A moment's minute is passed by the board's clock by this much before it is said: a whole minute, whichever way the
     * data counts its minutes (the 63rd minute is the clock's 62:00 to 62:59), and the board's clock running on a little.
     */
    public static final int MARGIN_S = 60;
    /** A moment the board's clock passed this long ago (beyond MARGIN_S), or first read this long ago, is too old to say. */
    public static final int STALE_S = 300;
    public static final long STALE_MS = SAFE_MS + 5 * 60_000L;
    /** Reads kept: a few minutes' worth, more than the longest lag. */
    public static final long KEEP_MS = 6 * 60_000L;
    static final int MOST_READS = 40;

    /** Where the game is (the picture, or the data when it was read): before kick-off, in play, at a break, over. */
    public static final int BEFORE = 0, PLAY = 1, BREAK = 2, OVER = 3;
    /** One period's room in a time in the game (at). */
    static final int PERIOD = 100_000;

    /** Pure: a time in the game as one number, its period first (1, 2, extra time's 3 and 4), then the clock's seconds. */
    public static int at(int period, int secs) {
        return Math.max(0, period) * PERIOD + Math.max(0, Math.min(PERIOD - 1, secs));
    }

    /** Pure: the period a time in the game (at) is in. */
    public static int periodOf(int at) {
        return at < 0 ? 0 : at / PERIOD;
    }

    // ---- the picture ----------------------------------------------------------------------------------------------

    /** Where the picture is now: the board's own game as it shows it. */
    public static final class Picture {
        /** Now (the same clock as the stamps'). */
        public long now;
        /** BEFORE, PLAY, BREAK (half time, before extra time) or OVER. */
        public int state = BEFORE;
        /** The period on the picture (1, 2, extra time's 3 and 4; at a break, the one just over); 0 not known. */
        public int period;
        /** The board's clock in seconds into the game, in step with the picture (-1: none). */
        public int secs = -1;
        /** Whether that clock is the channel's own (read off it, or set at kick-off), not the scores' guess. */
        public boolean known;
        /** How far the channel runs behind the scores, as the board's in-step logic has it for this channel (ms). */
        public long lagMs;
        /** The board's score (-1: not known). */
        public int home = -1, away = -1;
        /**
         * The scores' latest goals, ahead of the picture (-1: not known): a read's goal the scores never had (a goal ruled
         * out, that FotMob still counts) holds nothing back once its moment and lag are passed.
         */
        public int scoresHome = -1, scoresAway = -1;

        /** Pure: how far into the game the picture is (at): nothing before kick-off, its clock in play, a break's half all. */
        public int position() {
            switch (state) {
                case OVER: return Integer.MAX_VALUE;
                case BREAK: return at(Math.max(1, period), PERIOD - 1);
                case PLAY: return at(Math.max(1, period), Math.max(0, secs));
                default: return 0;
            }
        }

        /**
         * The same picture with its lag taken as the board's in-step logic has it, known or not: for what is part of the
         * board's own clock (the minutes added beside it), held as the clock is, never the longest lag.
         */
        public Picture inStep() {
            Picture p = picture(now, state, period, secs, true, lagMs, home, away);
            p.scoresHome = scoresHome;
            p.scoresAway = scoresAway;
            return p;
        }

        /** How long a read is held at least: the lag and its margin when the clock is known, else the longest lag. */
        public long waitMs() {
            return known ? Math.max(0, lagMs) + LAG_MARGIN_MS : Math.max(lagMs, SAFE_MS);
        }

        @Override public String toString() {
            return "state " + state + " period " + period + " secs " + secs + (known ? " known" : " guessed") + " lag " + lagMs
                    + " score " + home + "-" + away;
        }
    }

    /** Pure: a picture: state, period, clock (seconds, -1 none), whether it is known, the lag, the board's score. */
    public static Picture picture(long now, int state, int period, int secs, boolean known, long lagMs, int home, int away) {
        Picture p = new Picture();
        p.now = now;
        p.state = state;
        p.period = period;
        p.secs = secs;
        p.known = known;
        p.lagMs = lagMs;
        p.home = home;
        p.away = away;
        return p;
    }

    // ---- what a read could give away ----------------------------------------------------------------------------------

    /** What a read (or one thing in it) could give away. */
    public static final class Stamp {
        /** When it came. */
        public final long at;
        /** The game when it was read: BEFORE, PLAY, BREAK, OVER, and its period. */
        public final int phase, period;
        /** The latest moment in it (at(); -1: none known). */
        public final int upTo;
        /** The goals in it (-1: not known). */
        public final int home, away;
        /** Whether its goals may count one the scores never had (FotMob's count: a goal VAR ruled out). */
        public final boolean mayOvercount;

        public Stamp(long at, int phase, int period, int upTo, int home, int away) {
            this(at, phase, period, upTo, home, away, false);
        }

        public Stamp(long at, int phase, int period, int upTo, int home, int away, boolean mayOvercount) {
            this.at = at;
            this.phase = phase;
            this.period = period;
            this.upTo = upTo;
            this.home = home;
            this.away = away;
            this.mayOvercount = mayOvercount;
        }

        @Override public String toString() {
            return "read at " + at + " phase " + phase + "/" + period + " up to " + upTo + " goals " + home + "-" + away;
        }
    }

    /**
     * Pure: whether the picture has shown everything a read holds: none of its goals still to come on the board, its
     * latest moment passed by the board's clock (by MARGIN_S in play), and it the channel's lag old (the longest lag while
     * the clock is a guess). A read made while the game stood where the picture stands now (before kick-off, at the same
     * break, over) holds nothing more than the picture has shown, and needs no lag.
     */
    public static boolean due(Picture p, Stamp s) {
        if (p == null || s == null) return false;
        int home = s.mayOvercount ? heldGoals(s.home, p.home, p.scoresHome) : s.home;
        int away = s.mayOvercount ? heldGoals(s.away, p.away, p.scoresAway) : s.away;
        if (home > 0 && (p.home < 0 || home > p.home)) return false;
        if (away > 0 && (p.away < 0 || away > p.away)) return false;
        if (s.upTo >= 0) {
            long most = (long) p.position() - (p.state == PLAY ? MARGIN_S : 0);
            if (s.upTo > most) return false;
        }
        boolean same = s.phase == p.state && (s.phase == BEFORE || s.phase == OVER || s.phase == BREAK && s.period == p.period);
        return same || p.now - s.at >= p.waitMs();
    }

    /**
     * Pure: a read's goals that hold it back: those the board or the scores (their latest) have had; one neither has had
     * (ruled out since) holds nothing back. All of them while the scores' latest is not known.
     */
    static int heldGoals(int read, int board, int scores) {
        if (scores < 0) return read;
        return Math.min(read, Math.max(board, scores));
    }

    /**
     * Pure: whether a moment is too old to say now: first read STALE_MS ago, from a half before the one in play on the
     * picture, or passed by the board's clock STALE_S ago.
     */
    public static boolean stale(Picture p, Stamp s) {
        if (p == null || s == null) return true;
        if (p.now - s.at > STALE_MS) return true;
        if (s.upTo < 0 || p.state != PLAY) return false;
        if (periodOf(s.upTo) < Math.max(1, p.period)) return true;
        return p.known && (long) p.position() - s.upTo > MARGIN_S + STALE_S;
    }

    // ---- a short history of reads ----------------------------------------------------------------------------------

    /** The reads of a game kept a few minutes with their stamps, oldest first: the newest the picture has reached. */
    public static final class Reads<T> {
        private final ArrayDeque<Object[]> list = new ArrayDeque<>();

        /** A read as it came (none out of order: one older than the last is left out). */
        public void add(Stamp s, T value) {
            if (s == null) return;
            Object[] last = list.peekLast();
            if (last != null && ((Stamp) last[0]).at > s.at) return;
            list.addLast(new Object[]{s, value});
            // A few minutes' worth, and always the newest one older than that (still what the longest lag shows).
            while (list.size() > 1 && second().at <= s.at - KEEP_MS) list.removeFirst();
            while (list.size() > MOST_READS) list.removeFirst();
        }

        private Stamp second() {
            Iterator<Object[]> it = list.iterator();
            it.next();
            return (Stamp) it.next()[0];
        }

        /** The newest read the picture has reached (due); null when none has been. */
        @SuppressWarnings("unchecked")
        public T pick(Picture p) {
            Iterator<Object[]> it = list.descendingIterator();
            while (it.hasNext()) {
                Object[] r = it.next();
                if (due(p, (Stamp) r[0])) return (T) r[1];
            }
            return null;
        }

        /** The newest read whatever the picture shows (null: none): for what gives nothing away. */
        @SuppressWarnings("unchecked")
        public T latest() {
            Object[] r = list.peekLast();
            return r == null ? null : (T) r[1];
        }

        public int size() {
            return list.size();
        }

        public void clear() {
            list.clear();
        }
    }

    // ---- reads held back until the picture reaches them ---------------------------------------------------------------

    /**
     * What came with a read and says something at once (a penalty given, a VAR check, a booking's scan), held in the order
     * the reads came until the picture reaches its read; a stale one is let go unsaid.
     */
    public static final class Held {
        private final ArrayDeque<Object[]> list = new ArrayDeque<>();

        /** What to do once the picture reaches the read stamped s (kept for the game named, in order). */
        public void add(String game, Stamp s, Runnable say) {
            if (s == null || say == null) return;
            list.addLast(new Object[]{game == null ? "" : game, s, say});
            while (list.size() > MOST_READS) list.removeFirst();
        }

        /** Each one the picture has reached, in order, done now (stale ones dropped); those for another game let go. */
        public void release(String game, Picture p) {
            String g = game == null ? "" : game;
            while (!list.isEmpty()) {
                Object[] h = list.peekFirst();
                if (!g.equals(h[0])) {
                    list.removeFirst();
                    continue;
                }
                Stamp s = (Stamp) h[1];
                if (stale(p, s)) {
                    list.removeFirst();
                    continue;
                }
                if (!due(p, s)) return;
                list.removeFirst();
                ((Runnable) h[2]).run();
            }
        }

        /** Whether anything is waiting for the game named. */
        public boolean waiting(String game) {
            for (Object[] h : list) if (h[0].equals(game == null ? "" : game)) return true;
            return false;
        }

        public void clear() {
            list.clear();
        }

        public int size() {
            return list.size();
        }
    }

    // ---- what people say -------------------------------------------------------------------------------------------

    /**
     * Pure: whether something people watching wrote at postAt (a fan's post on the ad-break screen) may be shown at now
     * around a game: only once it is the longest lag old, so it can say nothing the picture has not shown yet (the end of
     * half time on the picture is already the second half for them).
     */
    public static boolean fanSafe(long postAt, long now) {
        return postAt > 0 && now - postAt >= SAFE_MS;
    }

    // ---- FotMob's times ---------------------------------------------------------------------------------------------

    /** Pure: FotMob's period ("FirstHalf", "SecondHalf", "FirstExtra", "SecondExtra", "PenaltyShootout") as 1 to 5; 0 none. */
    public static int period(String fotmob) {
        String p = fotmob == null ? "" : fotmob.toLowerCase(Locale.ROOT);
        if (p.contains("penalt") || p.contains("shootout")) return 5;
        boolean extra = p.contains("extra") || p.contains("overtime");
        if (p.contains("first")) return extra ? 3 : 1;
        if (p.contains("second")) return extra ? 4 : 2;
        return 0;
    }

    /** Pure: the period a minute is in when the data says no more ("45+2": the first half's; 46: the second's). */
    public static int periodOfMinute(int minute) {
        return minute <= 45 ? 1 : minute <= 90 ? 2 : minute <= 105 ? 3 : 4;
    }

    /**
     * Pure: a FotMob shot's time in the game (at): its period, and the end of its minute (min and minAdded: the 63rd
     * minute ends at 63:00, 90+2 at 92:00); -1 without a minute, or in a shootout.
     */
    public static int shotAt(JSONObject shot) {
        if (shot == null || !shot.has("min")) return -1;
        int min = shot.optInt("min", -1), added = Math.max(0, shot.optInt("minAdded", 0));
        if (min < 0) return -1;
        int period = period(shot.optString("period", ""));
        if (period == 5) return -1;
        if (period == 0) period = periodOfMinute(min);
        return at(period, (min + added) * 60);
    }

    /**
     * Pure: a minute as the data writes it ("63", "90+2", "45'+2'", "67'") as a time in the game (at): its period by its
     * first number, and the end of the minute; -1 without one.
     */
    public static int minuteAt(String minute) {
        if (minute == null) return -1;
        int i = 0, n = minute.length();
        while (i < n && !Character.isDigit(minute.charAt(i))) i++;
        int base = 0, digits = 0;
        for (; i < n && Character.isDigit(minute.charAt(i)) && digits < 4; i++, digits++) base = base * 10 + (minute.charAt(i) - '0');
        if (digits == 0) return -1;
        int plus = minute.indexOf('+', i);
        int extra = 0;
        if (plus >= 0) {
            for (int j = plus + 1, d = 0; j < n && d < 3; j++) {
                char ch = minute.charAt(j);
                if (Character.isDigit(ch)) {
                    extra = extra * 10 + (ch - '0');
                    d++;
                } else if (d > 0 || ch != ' ' && ch != '\'') {
                    break;
                }
            }
        }
        return at(periodOfMinute(base), (base + extra) * 60);
    }

    /**
     * Pure: the latest moment in a FotMob read (at): its shots' and its events' ({type, minute, ...}: cards and
     * substitutions); -1 for none.
     */
    public static int latest(JSONArray shots, List<String[]> events) {
        int most = -1;
        for (int i = 0; shots != null && i < shots.length(); i++) most = Math.max(most, shotAt(shots.optJSONObject(i)));
        if (events != null) {
            for (String[] e : events) if (e != null && e.length > 1) most = Math.max(most, minuteAt(e[1]));
        }
        return most;
    }

    /** Pure: the latest of a list's minutes (each row's minute at index i, as the data writes it), as at(); -1 for none. */
    public static int latestMinute(List<String[]> rows, int i) {
        int most = -1;
        if (rows != null) {
            for (String[] r : rows) if (r != null && r.length > i) most = Math.max(most, minuteAt(r[i]));
        }
        return most;
    }

    /** Pure: the goals in FotMob's shot map, {home, away} (an own goal for the side it went in for). */
    public static int[] goals(JSONArray shots, int homeTeamId) {
        int h = 0, a = 0;
        for (int i = 0; shots != null && i < shots.length(); i++) {
            JSONObject s = shots.optJSONObject(i);
            if (s == null || !"Goal".equals(s.optString("eventType"))) continue;
            if (period(s.optString("period", "")) == 5) continue;
            if (forHome(s, homeTeamId)) h++;
            else a++;
        }
        return new int[]{h, a};
    }

    /** Pure: whether a goal in the shot map counts for the home side (an own goal for the other side's). */
    static boolean forHome(JSONObject shot, int homeTeamId) {
        boolean byHome = shot.optInt("teamId") == homeTeamId;
        return byHome != shot.optBoolean("isOwnGoal");
    }

    /**
     * Pure: a goal's place among its side's in the shot map: {home goals, away goals} on the board once it is there (-1
     * for the other side); null when the shot is no goal.
     */
    public static int[] goalOnBoard(JSONArray shots, int index, int homeTeamId) {
        JSONObject me = shots == null ? null : shots.optJSONObject(index);
        if (me == null || !"Goal".equals(me.optString("eventType"))) return null;
        boolean home = forHome(me, homeTeamId);
        int myAt = shotAt(me), n = 0;
        for (int i = 0; i < shots.length(); i++) {
            JSONObject s = shots.optJSONObject(i);
            if (s == null || !"Goal".equals(s.optString("eventType")) || forHome(s, homeTeamId) != home) continue;
            if (period(s.optString("period", "")) == 5) continue;
            int sAt = shotAt(s);
            if (i == index || sAt < myAt || sAt == myAt && i < index) n++;
        }
        return home ? new int[]{n, -1} : new int[]{-1, n};
    }
}
