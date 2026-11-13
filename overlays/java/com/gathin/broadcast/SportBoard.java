package com.gathin.broadcast;

import android.graphics.Bitmap;

import java.util.Arrays;
import java.util.Locale;

/**
 * A tennis match or a fight (boxing, UFC) as the scoreboard shows it, the same on the TV and the phone: the two sides
 * (tennis: the players in the feed's order; a fight: the red corner, then the blue), what is on now (the set and each
 * set's games; the round and its clock) and how it ended. SportFeeds fills it from the scores' feeds (or a boxing
 * programme's title), SportBug draws it in the board's look.
 *
 * <p>Pure but for the two pictures a side may carry, which the apps fill.
 */
public final class SportBoard {
    public static final int TENNIS = 1, BOXING = 2, UFC = 3;
    /** The corners' colours: a fight's red and blue. */
    public static final int RED_CORNER = 0xFFC8102E, BLUE_CORNER = 0xFF1F4FD8;

    public int sport;
    public String id = "";
    /** pre, in or post, as ESPN has them. */
    public String state = "pre";
    /** The event ("WTA Finals", "UFC 334") and where in it ("Group Stage"; "Heavyweight"; "WBC heavyweight title"). */
    public String event = "", stage = "";
    /** Its start, ms (0: not known). Tennis: the draw it is in ("Men's Singles", "Women's Singles"). */
    public long start;
    public String division = "";
    /** Tennis: the player listed first, then the second. A fight: the red corner, then the blue. */
    public final Side a = new Side(), b = new Side();

    // ---- tennis ---------------------------------------------------------------------------------------------------
    /** Sets to play at most (three, or five at the men's majors) and the set in play (1 on; 0 before the first). */
    public int bestOf = 3, set;

    // ---- a fight --------------------------------------------------------------------------------------------------
    /** The round on (0 before the first) of the rounds scheduled; each round's length and the rest between, seconds. */
    public int round, rounds = 3, roundSecs = 300, restSecs = 60;
    /** Seconds into the round now (-1: not known); between rounds. */
    public int secsIn = -1;
    public boolean resting;
    /** How it ended ("KO/TKO", "Submission", "Decision"; tennis: "Retired") and when ("R3 2:13"). */
    public String method = "", finish = "";
    /** The main event of the card. */
    public boolean mainEvent;

    /** One side. */
    public static final class Side {
        /** Their name, the short one the boards use (a surname, in capitals by the looks that want it), country (IOC). */
        public String name = "", shortName = "", country = "", flagUrl = "", photoUrl = "", record = "", seed = "";
        /** Their colour: a country's for tennis, the corner's for a fight. */
        public int colour;
        /** Tennis: games in each set so far (the set in play last), and the tiebreak's points in a set (-1: none). */
        public int[] games = new int[0], tiebreak = new int[0];
        /** Tennis: the points in the game in play ("0", "15", "30", "40", "AD"; a tiebreak's count), "" when not known. */
        public String points = "";
        public boolean serving, winner;
        /** The flag and the photo, when the app has them. */
        public Bitmap flag, photo;

        void copyTo(Side s) {
            s.name = name;
            s.shortName = shortName;
            s.country = country;
            s.flagUrl = flagUrl;
            s.photoUrl = photoUrl;
            s.record = record;
            s.seed = seed;
            s.colour = colour;
            s.games = games.clone();
            s.tiebreak = tiebreak.clone();
            s.points = points;
            s.serving = serving;
            s.winner = winner;
            s.flag = flag;
            s.photo = photo;
        }
    }

    public boolean live() {
        return "in".equals(state);
    }

    public boolean over() {
        return "post".equals(state);
    }

    public boolean fight() {
        return sport == BOXING || sport == UFC;
    }

    public SportBoard copy() {
        SportBoard c = new SportBoard();
        c.sport = sport;
        c.id = id;
        c.state = state;
        c.event = event;
        c.stage = stage;
        c.start = start;
        c.division = division;
        a.copyTo(c.a);
        b.copyTo(c.b);
        c.bestOf = bestOf;
        c.set = set;
        c.round = round;
        c.rounds = rounds;
        c.roundSecs = roundSecs;
        c.restSecs = restSecs;
        c.secsIn = secsIn;
        c.resting = resting;
        c.method = method;
        c.finish = finish;
        c.mainEvent = mainEvent;
        return c;
    }

    // ---- tennis, pure ---------------------------------------------------------------------------------------------

    /** Pure: whether set i (0 on) is over: its games say so (6 with two clear, 7) or a later set has begun. */
    public static boolean setDone(int[] x, int[] y, int i, int setsPlayed) {
        if (i < setsPlayed - 1) return true;
        if (i >= x.length || i >= y.length) return false;
        int p = x[i], q = y[i];
        return Math.max(p, q) >= 6 && Math.abs(p - q) >= 2 || Math.max(p, q) == 7 && Math.min(p, q) >= 5;
    }

    /** Pure: the sets each side has won, {first, second}. */
    public int[] sets() {
        int n = Math.max(a.games.length, b.games.length), wa = 0, wb = 0;
        for (int i = 0; i < n; i++) {
            int p = i < a.games.length ? a.games[i] : 0, q = i < b.games.length ? b.games[i] : 0;
            if (!setDone(a.games, b.games, i, over() ? n + 1 : n)) continue;
            if (p > q) wa++;
            else if (q > p) wb++;
        }
        return new int[]{wa, wb};
    }

    /** Pure: the number of set columns the board shows (the sets begun; one, empty, before the first ball). */
    public int setColumns() {
        return Math.max(1, Math.max(a.games.length, b.games.length));
    }

    /** Pure: a side's games in set i, or "" for a set not reached. */
    public static String gamesIn(Side s, int i) {
        return i < s.games.length ? String.valueOf(s.games[i]) : "";
    }

    /** Pure: the set in play is a tiebreak (six all). */
    public boolean tiebreakNow() {
        int i = setColumns() - 1;
        return live() && i < a.games.length && i < b.games.length && a.games[i] == 6 && b.games[i] == 6;
    }

    // ---- a fight, pure --------------------------------------------------------------------------------------------

    /**
     * Pure: the board as it stands now, read at readAt: a fight's round clock counted on from the feed's (which may sit
     * still between reads), never past the round's end. The same board when nothing moves.
     */
    public SportBoard at(long readAt, long now) {
        if (!fight() || !live() || resting || secsIn < 0 || readAt <= 0 || now <= readAt) return this;
        SportBoard c = copy();
        c.secsIn = (int) Math.min(roundSecs, secsIn + (now - readAt) / 1000L);
        return c;
    }

    /** Pure: the round's clock as the boards show it, the time left in it ("3:41"); "" when not known. */
    public String clockLeft() {
        if (secsIn < 0 || round <= 0) return "";
        int len = resting ? restSecs : roundSecs, left = Math.max(0, len - Math.min(len, secsIn));
        return String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60);
    }

    /** Pure: the round's words, short ("R2") or long ("ROUND 2"); "" before the first. */
    public String roundWords(boolean longer) {
        if (round <= 0) return "";
        return (longer ? "ROUND " : "R") + round;
    }

    /** Pure: the result's few words for the middle of the board: KO, TKO, SUB, DEC, DRAW, NC (or the feed's own). */
    public static String methodShort(String m) {
        String s = m == null ? "" : m.trim().toUpperCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("FINAL")) return "";
        if (s.contains("KO/TKO")) return "KO/TKO";
        if (s.contains("TKO")) return "TKO";
        if (s.contains("KO")) return "KO";
        if (s.startsWith("SUB")) return "SUB";
        if (s.contains("DRAW")) return "DRAW";
        if (s.contains("NO CONTEST") || s.equals("NC")) return "NC";
        if (s.contains("DISQ") || s.equals("DQ")) return "DQ";
        if (s.contains("SPLIT")) return "SPLIT DEC";
        if (s.contains("MAJORITY")) return "MAJ DEC";
        if (s.contains("DEC") || s.contains("POINTS")) return "DEC";
        if (s.contains("RET")) return "RET";
        return s.length() <= 9 ? s : s.substring(0, 9).trim();
    }

    /**
     * Pure: a boxing night's round and its clock, counted from the first bell as seen on the screen (bellAt, ms): rounds
     * of roundSecs with restSecs between, rounds of them. {round (1 on), seconds into it, 1 between rounds (0 not), 1
     * over (all the rounds gone)}. Before the bell: round 0.
     */
    public static int[] bell(long bellAt, long now, int roundSecs, int restSecs, int rounds) {
        if (bellAt <= 0 || now < bellAt || roundSecs <= 0) return new int[]{0, -1, 0, 0};
        long s = (now - bellAt) / 1000L, each = roundSecs + Math.max(0, restSecs);
        int r = (int) (s / each) + 1;
        if (r > rounds) return new int[]{rounds, roundSecs, 0, 1};
        int in = (int) (s % each);
        boolean rest = in >= roundSecs;
        if (rest && r == rounds) return new int[]{rounds, roundSecs, 0, 1};
        return new int[]{r, rest ? in - roundSecs : in, rest ? 1 : 0, 0};
    }

    /** A moment the board sweeps across itself for: its words and the side's colour. */
    public static final class Moment {
        public final String words;
        public final int colour;

        Moment(String words, int colour) {
            this.words = words;
            this.colour = colour;
        }
    }

    /**
     * Pure: the moment between two readings of the same match or fight, or null: tennis, a set won ("SET SABALENKA") or
     * the match ("GAME, SET AND MATCH · SABALENKA"); a fight, a round begun ("ROUND 3") or its winner ("VANCE WINS · SUB").
     */
    public static Moment moment(SportBoard was, SportBoard now) {
        if (was == null || now == null || !was.id.equals(now.id) || was.sport != now.sport) return null;
        if (now.sport == TENNIS) {
            if (!was.over() && now.over()) {
                Side w = now.a.winner ? now.a : now.b.winner ? now.b : null;
                return w == null ? null : new Moment("GAME, SET AND MATCH · " + upper(w), w.colour);
            }
            int[] before = was.sets(), after = now.sets();
            if (after[0] > before[0]) return new Moment("SET " + upper(now.a), now.a.colour);
            if (after[1] > before[1]) return new Moment("SET " + upper(now.b), now.b.colour);
            return null;
        }
        if (!was.over() && now.over()) {
            Side w = now.a.winner ? now.a : now.b.winner ? now.b : null;
            if (w == null) return null;
            String m = methodShort(now.method);
            return new Moment(upper(w) + " WINS" + (m.isEmpty() ? "" : " · " + m), w.colour);
        }
        if (now.live() && !now.resting && now.round > Math.max(0, was.round)) return new Moment("ROUND " + now.round, 0);
        return null;
    }

    private static String upper(Side s) {
        String n = s.shortName == null || s.shortName.isEmpty() ? s.name : s.shortName;
        return n == null ? "" : n.toUpperCase(Locale.ROOT);
    }

    @Override public String toString() {
        return "SportBoard{" + sport + " " + state + " " + a.name + " v " + b.name + " " + Arrays.toString(a.games) + "/"
                + Arrays.toString(b.games) + " R" + round + " " + secsIn + "}";
    }
}
