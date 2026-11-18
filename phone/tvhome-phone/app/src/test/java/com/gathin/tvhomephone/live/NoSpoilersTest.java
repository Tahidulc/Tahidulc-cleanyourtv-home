package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.AsSeen;
import com.gathin.broadcast.MatchStats;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.OnScreen;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * No spoilers on the phone's board, end to end: a made-up game (Riverside v Whitebrook) whose away side's Thomas scores
 * at 62:20 from a 0.05 chance and whose keeper makes a big save at 70:10. FotMob has each the moment it happens, ESPN 20
 * seconds later; the channel's picture is 45 or 90 seconds behind the real game. The board in step with the picture as
 * the player keeps it (OnScreen: the channel's own clock and score read off the picture, its lag measured from them), or
 * with nothing read off it (the scores as ESPN has them, held as long as the channel's delay set, none or a wrong guess).
 * The great finish, the big save and the expected-goals total in the stats never come before the picture shows the
 * goal or the save.
 */
public class NoSpoilersTest {
    private static final int HOME = 11, AWAY = 22, ESPN_S = 20, GOAL = 62 * 60 + 20, SAVE = 70 * 60 + 10;

    private static JSONObject shot(String id, String type, int team, int min, double xg, double xgot, String last) throws Exception {
        return new JSONObject().put("id", id).put("eventType", type).put("teamId", team).put("min", min).put("minAdded", 0)
                .put("period", "SecondHalf").put("expectedGoals", xg).put("expectedGoalsOnTarget", xgot)
                .put("isOnTarget", !"Miss".equals(type)).put("isBlocked", false).put("isOwnGoal", false).put("situation", "RegularPlay")
                .put("lastName", last).put("playerName", "Sam " + last);
    }

    /** FotMob's shot map as it stands at real second t. */
    private static JSONArray fotmob(int t) throws Exception {
        JSONArray a = new JSONArray().put(shot("a1", "Miss", HOME, 56, 0.12, 0, "Hollis"));
        if (t >= GOAL) a.put(shot("a2", "Goal", AWAY, 63, 0.05, 0.3, "Thomas"));
        if (t >= SAVE) a.put(shot("a3", "AttemptSaved", HOME, 71, 0.2, 0.71, "Okafor"));
        return a;
    }

    /** ESPN's read of the game at real second t: its clock ESPN_S behind. */
    private static Game espn(int t) {
        Game g = new Game();
        g.id = "nospoil-1";
        g.home = "RIV";
        g.away = "WHI";
        g.state = Game.LIVE;
        g.period = 2;
        int secs = t - ESPN_S;
        g.clock = secs;
        g.displayClock = (secs / 60 + 1) + "'";
        g.homeScore = 0;
        g.awayScore = secs >= GOAL ? 1 : 0;
        return g;
    }

    /**
     * From 50:00 to 80:00, a second at a time: ESPN read every 15 s, FotMob every 30 s, the board looked at every second.
     * read: the channel's clock and score read off the picture every 2 s; else the delay set for the channel (guessS).
     */
    private static void play(int lagS, boolean read, int guessS) throws Exception {
        OnScreen s = new OnScreen();
        s.behind(read ? 0 : guessS * 1000L);
        Set<String> shown = new HashSet<>();
        Map<String, Long> first = new HashMap<>();
        AsSeen.Reads<Double> xg = new AsSeen.Reads<>();
        JSONArray lastShots = new JSONArray();
        long finishAt = -1, saveAt = -1, xgAt = -1;
        for (int t = 50 * 60; t <= 80 * 60; t++) {
            long now = t * 1000L;
            if (t % 15 == 0) s.add(espn(t), now);
            if (read && t % 2 == 0) {
                int pic = t - lagS;
                s.screenClock(pic, -1, now);
                s.screenScore(0, pic >= GOAL ? 1 : 0, now);
                // The player measures the channel's lag off its own clock and holds the board that long (ScoreOverlay).
                long m = s.measuredBehind();
                if (m >= 0) s.behind(m);
            }
            Game espnNow = espn(t);
            if (t % 30 == 0) {
                lastShots = fotmob(t);
                MatchStats.firstSeen(lastShots, first, now);
                int[] goals = AsSeen.goals(lastShots, HOME);
                double total = 0;
                for (int i = 0; i < lastShots.length(); i++) total += lastShots.getJSONObject(i).optDouble("expectedGoals");
                xg.add(ScoreBoard.stamp(espnNow, now, AsSeen.latest(lastShots, null), goals[0], goals[1]), total);
            }
            Game g = s.game(now);
            String[] clock = s.boardClock(now);
            AsSeen.Picture p = ScoreBoard.picture(g, clock == null ? "" : clock[0], now, s.reading(now), s.behind());
            // The scores' latest (ESPN's read last), as the board gives it.
            p.scoresHome = espnNow.homeScore;
            p.scoresAway = espnNow.awayScore;
            String[] said = MatchStats.shotMoment(lastShots, shown, p, first, HOME);
            if (said != null && "Great finish".equals(said[0])) {
                assertEquals("Thomas scored a 0.05 xG chance", said[1]);
                finishAt = t;
            }
            if (said != null && "Big save".equals(said[0])) saveAt = t;
            Double total = xg.pick(p);
            if (xgAt < 0 && total != null && total > 0.16) xgAt = t;
        }
        String how = "picture " + lagS + " s behind, " + (read ? "its clock read off it" : "nothing read off it, the delay set " + guessS + " s");
        assertTrue(how + ": great finish said", finishAt > 0);
        assertTrue(how + ": great finish at " + finishAt + ", the goal on the picture at " + (GOAL + lagS), finishAt >= GOAL + lagS);
        assertTrue(how + ": xG moved", xgAt > 0);
        assertTrue(how + ": xG moved at " + xgAt + ", the goal on the picture at " + (GOAL + lagS), xgAt >= GOAL + lagS);
        assertTrue(how + ": big save said", saveAt > 0);
        assertTrue(how + ": big save at " + saveAt + ", the save on the picture at " + (SAVE + lagS), saveAt >= SAVE + lagS);
    }

    @Test public void fotMob45SecondsAheadOfThePicture() throws Exception {
        play(45, true, 0);
    }

    @Test public void fotMob90SecondsAheadOfThePicture() throws Exception {
        play(90, true, 0);
    }

    @Test public void anUnknownLag() throws Exception {
        // Nothing read off the picture: no delay set, or one set for another channel, or one too long.
        for (int lag : new int[]{45, 90}) {
            for (int guess : new int[]{0, 30, 120}) play(lag, false, guess);
        }
    }

    @Test public void theBoardSaysWhereThePictureIs() {
        Game g = espn(70 * 60);
        AsSeen.Picture p = ScoreBoard.picture(g, "69:40", 1_000L, true, 40_000L);
        assertEquals(AsSeen.PLAY, p.state);
        assertEquals(2, p.period);
        assertEquals(69 * 60 + 40, p.secs);
        assertTrue(p.known);
        assertEquals(1, p.away);
        g.halftime = true;
        g.period = 1;
        p = ScoreBoard.picture(g, "HT", 1_000L, false, 0);
        assertEquals(AsSeen.BREAK, p.state);
        assertEquals(-1, p.secs);
        g.halftime = false;
        g.state = Game.POST;
        assertEquals(AsSeen.OVER, ScoreBoard.picture(g, "FT", 1_000L, false, 0).state);
        g.state = Game.PRE;
        p = ScoreBoard.picture(g, "", 1_000L, false, 0);
        assertEquals(AsSeen.BEFORE, p.state);
        assertEquals(0, p.home);
        // A read's stamp: the game as the scores had it, FotMob's goals when it has more.
        AsSeen.Stamp st = ScoreBoard.stamp(espn(62 * 60 + 30), 5_000L, AsSeen.at(2, 63 * 60), 0, 1);
        assertEquals(AsSeen.PLAY, st.phase);
        assertEquals(1, st.away);
    }
}
