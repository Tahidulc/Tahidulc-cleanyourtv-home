package com.gathin.tvhomephone.live;

import java.util.ArrayList;
import java.util.List;

/**
 * A penalty shootout as the strip under the scoreboard shows it (BoardUnder): each side's kicks as dots, scored, missed
 * or still to come, five each and more as sudden death goes on (both sides always the same number), and each side's
 * tally. Pure: the tests check it.
 */
final class Shootout {
    private Shootout() { }

    static final int TO_COME = -1, MISSED = 0, SCORED = 1;

    /** Pure: ESPN's kicks for a side ({taker, "1"/"0"}) as scored (true) or not, in order. */
    static List<Boolean> kicks(List<String[]> pens) {
        List<Boolean> out = new ArrayList<>();
        if (pens == null) return out;
        for (String[] p : pens) out.add(p != null && p.length > 1 && "1".equals(p[1]));
        return out;
    }

    /** Pure: each side's dots {home, away}: its kicks so far, then those to come, five each or as many as sudden death has reached. */
    static int[][] dots(List<Boolean> home, List<Boolean> away) {
        int n = Math.max(5, Math.max(home.size(), away.size()));
        int[][] out = new int[2][n];
        for (int side = 0; side < 2; side++) {
            List<Boolean> k = side == 0 ? home : away;
            for (int i = 0; i < n; i++) out[side][i] = i < k.size() ? (k.get(i) ? SCORED : MISSED) : TO_COME;
        }
        return out;
    }

    /** Pure: how many a side has scored. */
    static int scored(List<Boolean> kicks) {
        int n = 0;
        for (Boolean b : kicks) if (b != null && b) n++;
        return n;
    }

    /** Pure: the first n kicks of a shootout revealed alternately, the first side kicking first ({home kicks, away kicks}). */
    static int[] shownSplit(int n, boolean homeFirst, int homeHave, int awayHave) {
        int h = 0, a = 0;
        for (int i = 0; i < n; i++) {
            boolean homeTurn = (i % 2 == 0) == homeFirst;
            if (homeTurn && h < homeHave) h++;
            else if (!homeTurn && a < awayHave) a++;
            else if (h < homeHave) h++;
            else if (a < awayHave) a++;
        }
        return new int[]{h, a};
    }

    /**
     * Pure: a side's kicks as read now merged into those had: each new one kept with when it came (the picture shows it
     * the stream's delay later), those had put right where the read says otherwise.
     */
    static void arrived(List<Boolean> have, List<Long> at, List<Boolean> read, long now) {
        for (int i = 0; i < read.size(); i++) {
            if (i < have.size()) {
                have.set(i, read.get(i));
            } else {
                have.add(read.get(i));
                at.add(now);
            }
        }
    }

    /** Pure: the kicks the picture shows by seen (each came at its time in at, revealed at it). */
    static List<Boolean> shown(List<Boolean> have, List<Long> at, long seen) {
        List<Boolean> out = new ArrayList<>();
        for (int i = 0; i < have.size() && i < at.size(); i++) {
            if (at.get(i) > seen) break;
            out.add(have.get(i));
        }
        return out;
    }

    /** The tests' made-up shootout: five each level at four, then sudden death, the home side winning it 5-4. */
    static final boolean[] TEST_HOME = {true, true, false, true, true, true}, TEST_AWAY = {true, false, true, true, true, false};
    /** A made-up kick this often. */
    static final long TEST_EVERY_MS = 3_000L;

    /** Pure: the made-up shootout since ms into its test: a kick every TEST_EVERY_MS (none at first), home first, in turn. */
    static List<Boolean>[] test(long since) {
        int n = since < 0 ? 0 : (int) Math.min(TEST_HOME.length + TEST_AWAY.length, since / TEST_EVERY_MS);
        int[] split = shownSplit(n, true, TEST_HOME.length, TEST_AWAY.length);
        @SuppressWarnings("unchecked")
        List<Boolean>[] out = new List[]{new ArrayList<Boolean>(), new ArrayList<Boolean>()};
        for (int i = 0; i < split[0]; i++) out[0].add(TEST_HOME[i]);
        for (int i = 0; i < split[1]; i++) out[1].add(TEST_AWAY[i]);
        return out;
    }
}
