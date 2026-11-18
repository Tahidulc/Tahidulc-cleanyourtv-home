package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * No spoilers: a "Great finish", a big save and the stats' totals wait for the picture. A made-up game (Whitebrook v
 * Riverside, FotMob's ids 101 and 202) played second by second: FotMob has each shot the moment it happens, ESPN 20
 * seconds later, and the channel's picture runs 45 or 90 seconds behind the real game; the board in step with the picture
 * (the channel's own clock read off it), or with no reading of it (the scores' guess). Nothing is said before the picture
 * shows it.
 */
public class AsSeenTest {
    private static final int HOME = 101, AWAY = 202;
    /** ESPN's scores this long behind the real game. */
    private static final int ESPN_S = 20;

    private static JSONObject shot(String id, String type, int team, int min, int added, String period, double xg, double xgot, String last)
            throws Exception {
        return new JSONObject().put("id", id).put("eventType", type).put("teamId", team).put("min", min).put("minAdded", added)
                .put("period", period).put("expectedGoals", xg).put("expectedGoalsOnTarget", xgot)
                .put("isOnTarget", !"Miss".equals(type)).put("isBlocked", false).put("isOwnGoal", false).put("situation", "RegularPlay")
                .put("lastName", last).put("playerName", "Sam " + last);
    }

    /** The made-up game's shots, each with the real second it happened (the clock's), in order. */
    private static final class Game {
        final List<JSONObject> shots = new ArrayList<>();
        final List<Integer> when = new ArrayList<>();

        void add(int secs, JSONObject s) {
            shots.add(s);
            when.add(secs);
        }

        /** FotMob's shot map as it stands at real second t. */
        JSONArray at(int t) {
            JSONArray a = new JSONArray();
            for (int i = 0; i < shots.size(); i++) if (when.get(i) <= t) a.put(shots.get(i));
            return a;
        }

        double xg(int t) {
            double x = 0;
            for (int i = 0; i < shots.size(); i++) if (when.get(i) <= t) x += shots.get(i).optDouble("expectedGoals");
            return x;
        }

        int[] goals(int t) {
            return AsSeen.goals(at(t), HOME);
        }
    }

    /** Thomas's goal at 62:20 on the clock (FotMob: the 63rd minute) from a 0.05 chance; a big save at 70:10 (the 71st). */
    private static Game wales() throws Exception {
        Game g = new Game();
        g.add(55 * 60 + 5, shot("s1", "Miss", HOME, 56, 0, "SecondHalf", 0.12, 0, "Hollis"));
        g.add(62 * 60 + 20, shot("s2", "Goal", AWAY, 63, 0, "SecondHalf", 0.05, 0.31, "Thomas"));
        g.add(70 * 60 + 10, shot("s3", "AttemptSaved", HOME, 71, 0, "SecondHalf", 0.2, 0.71, "Okafor"));
        return g;
    }

    /**
     * The game played on from 50:00 to 80:00, the picture lagS behind the real game; the board's clock the channel's own
     * (known) or the scores' guess (guessS behind ESPN). FotMob read every 30 s (its cache a minute: what it says is up to
     * a minute old when it comes, but never later than the game); the board's moments asked for every second. Checks
     * that the great finish, the big save and the xG total never come before the picture shows what they are about, and
     * that each does come in the end.
     */
    private static void play(int lagS, boolean known, int guessS) throws Exception {
        Game g = wales();
        Set<String> shown = new HashSet<>();
        Map<String, Long> first = new HashMap<>();
        AsSeen.Reads<Double> xg = new AsSeen.Reads<>();
        long finishAt = -1, saveAt = -1, xgAt = -1;
        int goalSecs = 62 * 60 + 20, saveSecs = 70 * 60 + 10;
        JSONArray lastRead = new JSONArray();
        for (int t = 50 * 60; t <= 80 * 60; t++) {
            long now = t * 1000L;
            // FotMob read every 30 s, as it stood (it came at once: the worst case).
            if (t % 30 == 0) {
                JSONArray shots = g.at(t);
                lastRead = shots;
                MatchStats.firstSeen(shots, first, now);
                int[] goals = g.goals(t);
                xg.add(new AsSeen.Stamp(now, AsSeen.PLAY, 2, AsSeen.latest(shots, null), goals[0], goals[1], true), g.xg(t));
            }
            // The picture: the real game lagS ago. The board's clock: the channel's (known), or ESPN's guessS later.
            int pictureSecs = t - lagS;
            int boardSecs = known ? pictureSecs : t - ESPN_S - guessS;
            // The board's score: the channel's own (known), else ESPN's as the guess has it.
            int scoreSecs = known ? pictureSecs : t - ESPN_S - guessS;
            int[] board = {0, scoreSecs >= goalSecs ? 1 : 0};
            AsSeen.Picture p = AsSeen.picture(now, AsSeen.PLAY, 2, boardSecs, known, (lagS - ESPN_S) * 1000L, board[0], board[1]);
            // The scores' latest: ESPN's, ESPN_S behind the real game.
            p.scoresHome = 0;
            p.scoresAway = t - ESPN_S >= goalSecs ? 1 : 0;
            String[] said = MatchStats.shotMoment(lastRead, shown, p, first, HOME);
            if (said != null && "Great finish".equals(said[0])) {
                assertEquals("Thomas scored a 0.05 xG chance", said[1]);
                finishAt = t;
            }
            if (said != null && "Big save".equals(said[0])) saveAt = t;
            Double total = xg.pick(p);
            if (total != null && total > 0.16 && xgAt < 0) xgAt = t;
        }
        String how = lagS + " s behind, " + (known ? "the channel's clock" : "no clock read, guessed " + guessS + " s");
        // The goal's moments only once the picture has shown it (it reaches the picture lagS after it happened).
        assertTrue(how + ": great finish said", finishAt > 0);
        assertTrue(how + ": great finish at " + finishAt, finishAt >= goalSecs + lagS);
        assertTrue(how + ": xG total moved", xgAt > 0);
        assertTrue(how + ": xG total moved at " + xgAt, xgAt >= goalSecs + lagS);
        // The big save once its minute (the 71st) is over on the picture.
        assertTrue(how + ": big save said", saveAt > 0);
        assertTrue(how + ": big save at " + saveAt, saveAt >= saveSecs + lagS);
        if (known) assertTrue(how + ": big save after its minute on the board", saveAt - lagS >= 71 * 60);
    }

    @Test
    public void fotMob45SecondsAheadOfThePicture() throws Exception {
        play(45, true, 0);
    }

    @Test
    public void fotMob90SecondsAheadOfThePicture() throws Exception {
        play(90, true, 0);
    }

    @Test
    public void anUnknownLagWaitsForTheBoardsGoalAndTheLongestLag() throws Exception {
        // No reading of the channel's clock: the board's own guess anywhere from none to too much, the picture 45 or 90 s.
        for (int lag : new int[]{45, 90}) {
            for (int guess : new int[]{0, 25, 60}) play(lag, false, guess);
        }
    }

    @Test
    public void aGreatFinishWaitsForTheBoardsGoalEvenWithTheClockPast() throws Exception {
        Game g = wales();
        JSONArray shots = g.at(80 * 60);
        Map<String, Long> first = new HashMap<>();
        MatchStats.firstSeen(shots, first, 0L);
        Set<String> shown = new HashSet<>();
        shown.add("s3");
        // The board's clock well past the goal's minute, but its score without it (the channel's goal not on it yet).
        AsSeen.Picture p = AsSeen.picture(300_000L, AsSeen.PLAY, 2, 66 * 60, true, 30_000L, 0, 0);
        assertNull(MatchStats.shotMoment(shots, shown, p, first, HOME));
        p.away = 1;
        assertArrayEquals(new String[]{"Great finish", "Thomas scored a 0.05 xG chance", "", "one"}, MatchStats.shotMoment(shots, shown, p, first, HOME));
        assertNull(MatchStats.shotMoment(shots, shown, p, first, HOME));
    }

    @Test
    public void aBigSaveWaitsForItsMinute() throws Exception {
        JSONArray shots = new JSONArray().put(shot("k1", "AttemptSaved", AWAY, 71, 0, "SecondHalf", 0.3, 0.71, "Okafor"));
        Map<String, Long> first = new HashMap<>();
        MatchStats.firstSeen(shots, first, 0L);
        Set<String> shown = new HashSet<>();
        // Its minute (the 71st: 70:00 to 70:59) and a minute more on the board's clock, whatever the lag.
        for (int secs = 69 * 60; secs < 72 * 60; secs += 5) {
            AsSeen.Picture p = AsSeen.picture(300_000L, AsSeen.PLAY, 2, secs, true, 40_000L, 0, 0);
            assertNull("at " + secs, MatchStats.shotMoment(shots, new HashSet<>(), p, first, HOME));
        }
        AsSeen.Picture p = AsSeen.picture(300_000L, AsSeen.PLAY, 2, 72 * 60, true, 40_000L, 0, 0);
        assertEquals("Big save", MatchStats.shotMoment(shots, shown, p, first, HOME)[0]);
        // In the first half's added time: 45+2 is the clock's 46:00 to 46:59 of the first half, never of the second.
        JSONArray late = new JSONArray().put(shot("k2", "AttemptSaved", HOME, 45, 2, "FirstHalf", 0.3, 0.6, "Brandt"));
        Map<String, Long> f2 = new HashMap<>();
        MatchStats.firstSeen(late, f2, 0L);
        assertNull(MatchStats.shotMoment(late, new HashSet<>(), AsSeen.picture(300_000L, AsSeen.PLAY, 1, 47 * 60 + 30, true, 0, 0, 0), f2, HOME));
        assertNotNull(MatchStats.shotMoment(late, new HashSet<>(), AsSeen.picture(300_000L, AsSeen.PLAY, 1, 48 * 60, true, 0, 0, 0), f2, HOME));
        // At half time on the picture the first half's are all shown.
        assertNotNull(MatchStats.shotMoment(late, new HashSet<>(), AsSeen.picture(300_000L, AsSeen.BREAK, 1, -1, true, 0, 0, 0), f2, HOME));
    }

    @Test
    public void aMomentGoneStaleIsDroppedUnsaid() throws Exception {
        JSONArray shots = new JSONArray().put(shot("k1", "AttemptSaved", AWAY, 30, 0, "FirstHalf", 0.3, 0.71, "Okafor"));
        Map<String, Long> first = new HashMap<>();
        MatchStats.firstSeen(shots, first, 0L);
        Set<String> shown = new HashSet<>();
        // The second half on the picture: a first half's save is old news.
        AsSeen.Picture p = AsSeen.picture(1_200_000L, AsSeen.PLAY, 2, 47 * 60, true, 30_000L, 0, 0);
        assertNull(MatchStats.shotMoment(shots, shown, p, first, HOME));
        assertTrue(shown.contains("k1"));
        // Five minutes and more past it on the board's clock: dropped too.
        Set<String> s2 = new HashSet<>();
        assertNull(MatchStats.shotMoment(shots, s2, AsSeen.picture(400_000L, AsSeen.PLAY, 1, 37 * 60 + 1, true, 30_000L, 0, 0), first, HOME));
        assertTrue(s2.contains("k1"));
        // No clock read: first read longer ago than the longest lag and five minutes more.
        Set<String> s3 = new HashSet<>();
        assertNull(MatchStats.shotMoment(shots, s3, AsSeen.picture(AsSeen.STALE_MS + 1, AsSeen.PLAY, 1, 35 * 60, false, 0, 0, 0), first, HOME));
        assertTrue(s3.contains("k1"));
    }

    @Test
    public void aReadIsShownAsItStoodAtTheMomentOnThePicture() {
        AsSeen.Reads<String> r = new AsSeen.Reads<>();
        r.add(new AsSeen.Stamp(0, AsSeen.PLAY, 2, AsSeen.at(2, 60 * 60), 0, 0), "60'");
        r.add(new AsSeen.Stamp(30_000, AsSeen.PLAY, 2, AsSeen.at(2, 61 * 60), 0, 0), "61'");
        r.add(new AsSeen.Stamp(60_000, AsSeen.PLAY, 2, AsSeen.at(2, 63 * 60), 0, 1), "goal");
        // The picture 40 s behind: the goal's read not before the board has the goal and its minute has passed.
        assertEquals("61'", r.pick(AsSeen.picture(100_000, AsSeen.PLAY, 2, 62 * 60, true, 40_000, 0, 0)));
        assertEquals("61'", r.pick(AsSeen.picture(200_000, AsSeen.PLAY, 2, 64 * 60, true, 40_000, 0, 0)));
        assertEquals("goal", r.pick(AsSeen.picture(200_000, AsSeen.PLAY, 2, 64 * 60, true, 40_000, 0, 1)));
        // Too new for the lag: none.
        assertNull(r.pick(AsSeen.picture(20_000, AsSeen.PLAY, 2, 61 * 60, true, 40_000, 0, 0)));
        // No clock read: the longest lag.
        assertEquals("60'", r.pick(AsSeen.picture(AsSeen.SAFE_MS + 10_000, AsSeen.PLAY, 2, 70 * 60, false, 0, 0, 1)));
        // A read made at the break the picture is at holds no more than the picture has shown: no lag.
        r.add(new AsSeen.Stamp(900_000, AsSeen.BREAK, 1, AsSeen.at(1, 47 * 60), 1, 1), "half time");
        assertEquals("half time", r.pick(AsSeen.picture(900_500, AsSeen.BREAK, 1, -1, false, 0, 1, 1)));
        // A few minutes kept, and the newest older than that.
        AsSeen.Reads<Integer> many = new AsSeen.Reads<>();
        for (int i = 0; i < 100; i++) many.add(new AsSeen.Stamp(i * 30_000L, AsSeen.PLAY, 1, -1, 0, 0), i);
        assertTrue(many.size() <= AsSeen.MOST_READS);
        assertEquals(Integer.valueOf(99 - (int) (AsSeen.SAFE_MS / 30_000)), many.pick(AsSeen.picture(99 * 30_000L, AsSeen.PLAY, 1, 30 * 60, false, 0, 0, 0)));
    }

    @Test
    public void aGoalTheScoresNeverHadHoldsNothingBackForEver() {
        // FotMob still counting a goal VAR ruled out: its later reads are not held back for the rest of the game once their
        // moment and lag are passed; while the scores' latest has the goal, they wait for the board to show it.
        AsSeen.Stamp st = new AsSeen.Stamp(0, AsSeen.PLAY, 2, AsSeen.at(2, 70 * 60), 0, 1, true);
        AsSeen.Picture p = AsSeen.picture(200_000, AsSeen.PLAY, 2, 75 * 60, true, 30_000, 0, 0);
        assertFalse(AsSeen.due(p, st));
        p.scoresHome = 0;
        p.scoresAway = 1;
        assertFalse(AsSeen.due(p, st));
        p.scoresAway = 0;
        assertTrue(AsSeen.due(p, st));
        // A shot's own goal is never let go so: a great finish only for a goal the board shows.
        AsSeen.Stamp shot = new AsSeen.Stamp(0, AsSeen.PLAY, 2, AsSeen.at(2, 70 * 60), -1, 1);
        assertFalse(AsSeen.due(p, shot));
        // The same for the in-step picture (the minutes added's).
        assertTrue(AsSeen.due(p.inStep(), st));
    }

    @Test
    public void heldMomentsComeInOrderOnceThePictureHasThem() {
        AsSeen.Held h = new AsSeen.Held();
        List<String> said = new ArrayList<>();
        h.add("g1", new AsSeen.Stamp(0, AsSeen.PLAY, 2, -1, 0, 0), () -> said.add("check"));
        h.add("g1", new AsSeen.Stamp(20_000, AsSeen.PLAY, 2, -1, 0, 0), () -> said.add("decision"));
        assertTrue(h.waiting("g1"));
        h.release("g1", AsSeen.picture(30_000, AsSeen.PLAY, 2, 3000, true, 25_000, 0, 0));
        assertTrue(said.isEmpty());
        h.release("g1", AsSeen.picture(40_000, AsSeen.PLAY, 2, 3000, true, 25_000, 0, 0));
        assertEquals("[check]", said.toString());
        h.release("g1", AsSeen.picture(60_000, AsSeen.PLAY, 2, 3000, true, 25_000, 0, 0));
        assertEquals("[check, decision]", said.toString());
        assertFalse(h.waiting("g1"));
        // Another game's are let go.
        h.add("g1", new AsSeen.Stamp(0, AsSeen.PLAY, 2, -1, 0, 0), () -> said.add("old"));
        h.release("g2", AsSeen.picture(600_000, AsSeen.PLAY, 2, 3000, true, 0, 0, 0));
        assertEquals(0, h.size());
    }

    @Test
    public void fotMobsMinutesAsTheBoardsClock() throws Exception {
        assertEquals(AsSeen.at(2, 63 * 60), AsSeen.shotAt(shot("a", "Goal", HOME, 63, 0, "SecondHalf", 0.1, 0.2, "X")));
        assertEquals(AsSeen.at(1, 47 * 60), AsSeen.shotAt(shot("a", "Goal", HOME, 45, 2, "FirstHalf", 0.1, 0.2, "X")));
        assertEquals(AsSeen.at(4, 118 * 60), AsSeen.shotAt(shot("a", "Goal", HOME, 118, 0, "SecondExtra", 0.1, 0.2, "X")));
        assertEquals(-1, AsSeen.shotAt(shot("a", "Goal", HOME, 120, 0, "PenaltyShootout", 0.1, 0.2, "X")));
        assertEquals(AsSeen.at(2, 92 * 60), AsSeen.minuteAt("90+2"));
        assertEquals(AsSeen.at(1, 47 * 60), AsSeen.minuteAt("45'+2'"));
        assertEquals(AsSeen.at(2, 67 * 60), AsSeen.minuteAt("67'"));
        assertEquals(AsSeen.at(2, 93 * 60), AsSeen.minuteAt("90 + 3"));
        assertEquals(-1, AsSeen.minuteAt("HT"));
        assertEquals(-1, AsSeen.minuteAt(null));
        List<String[]> events = new ArrayList<>();
        events.add(new String[]{"Card", "71", "1", "Sam Hollis", "1", "Yellow"});
        JSONArray shots = new JSONArray().put(shot("a", "Goal", HOME, 63, 0, "SecondHalf", 0.1, 0.2, "X"));
        assertEquals(AsSeen.at(2, 71 * 60), AsSeen.latest(shots, events));
        assertEquals(AsSeen.at(2, 63 * 60), AsSeen.latest(shots, null));
        assertEquals(-1, AsSeen.latest(null, null));
        // The goals in the shot map, an own goal for the side it went in for; each goal's place among its side's.
        JSONArray three = new JSONArray().put(shot("a", "Goal", HOME, 10, 0, "FirstHalf", 0.1, 0.2, "X"))
                .put(shot("b", "Goal", AWAY, 20, 0, "FirstHalf", 0.1, 0.2, "Y").put("isOwnGoal", true))
                .put(shot("c", "Goal", AWAY, 50, 0, "SecondHalf", 0.1, 0.2, "Z"));
        assertArrayEquals(new int[]{2, 1}, AsSeen.goals(three, HOME));
        assertArrayEquals(new int[]{2, -1}, AsSeen.goalOnBoard(three, 1, HOME));
        assertArrayEquals(new int[]{-1, 1}, AsSeen.goalOnBoard(three, 2, HOME));
        assertNull(AsSeen.goalOnBoard(new JSONArray().put(shot("m", "Miss", HOME, 5, 0, "FirstHalf", 0.1, 0, "X")), 0, HOME));
        assertEquals(3, AsSeen.period("FirstExtra"));
        assertEquals(2, AsSeen.period("SecondHalf"));
        assertEquals(0, AsSeen.period(""));
    }
}
