package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** The free stats worked out from FotMob's and ESPN's reads, with made-up clubs (Whitebrook WHI, Riverside RIV). */
public class MatchStatsTest {
    private static JSONObject meeting(String date, String homeId, String awayId, String score) throws Exception {
        return new JSONObject().put("time", new JSONObject().put("utcTime", date + "T19:45:00.000Z"))
                .put("home", new JSONObject().put("id", homeId).put("name", homeId))
                .put("away", new JSONObject().put("id", awayId).put("name", awayId))
                .put("status", new JSONObject().put("finished", true).put("cancelled", false).put("scoreStr", score));
    }

    @Test
    public void theHeadToHeadIsTheHomeSidesRecordAndTheAwaySidesLastWinThere() throws Exception {
        JSONArray ms = new JSONArray()
                .put(meeting("2026-03-01", "2002", "1001", "1 - 2"))
                .put(meeting("2025-10-04", "1001", "2002", "0 - 0"))
                .put(meeting("2024-02-11", "1001", "2002", "1 - 3"))
                .put(meeting("2023-09-02", "2002", "1001", "2 - 0"))
                .put(meeting("2019-04-20", "1001", "2002", "0 - 1"));
        JSONObject details = new JSONObject().put("content", new JSONObject().put("h2h", new JSONObject().put("matches", ms)));
        String[] h = MatchStats.h2h(details, 1001, 2002, "WHI", "RIV");
        assertEquals("Head to head", h[0]);
        assertEquals("Last 5: WHI 1W 1D 3L", h[1]);
        // The latest Riverside win at Whitebrook's home (the list is newest first).
        assertEquals("RIV last won away at WHI in 2024", h[2]);
        assertEquals("one", h[3]);
        // From Riverside's side, as the home side.
        String[] r = MatchStats.h2h(details, 2002, 1001, "RIV", "WHI");
        assertEquals("Last 5: RIV 3W 1D 1L", r[1]);
        assertEquals("WHI last won away at RIV in 2026", r[2]);
        // The h2h on its own reads the same; none finished, nothing to say.
        assertEquals(h[1], MatchStats.h2h(new JSONObject().put("matches", ms), 1001, 2002, "WHI", "RIV")[1]);
        JSONObject unplayed = meeting("2027-01-01", "1001", "2002", "");
        unplayed.getJSONObject("status").put("finished", false);
        assertNull(MatchStats.h2h(new JSONObject().put("matches", new JSONArray().put(unplayed)), 1001, 2002, "WHI", "RIV"));
        assertNull(MatchStats.h2h(null, 1001, 2002, "WHI", "RIV"));
    }

    /** A side's form as FotMob gives it, oldest first: each {its goals, theirs, at home}. */
    private static JSONArray form(int[][] games) throws Exception {
        JSONArray a = new JSONArray();
        for (int[] g : games) {
            boolean home = g[2] == 1;
            String score = home ? g[0] + " - " + g[1] : g[1] + " - " + g[0];
            a.put(new JSONObject().put("score", score)
                    .put("home", new JSONObject().put("id", home ? "1001" : "9").put("isOurTeam", home))
                    .put("away", new JSONObject().put("id", home ? "9" : "1001").put("isOurTeam", !home)));
        }
        return a;
    }

    @Test
    public void aSidesRunIsSaidAsItStands() throws Exception {
        assertEquals("WHI have won their last 5", MatchStats.streak(form(new int[][]{{2, 0, 1}, {3, 1, 0}, {1, 0, 1}, {2, 1, 0}, {4, 2, 1}}), "WHI")[1]);
        assertEquals("WHI have won 3 in a row", MatchStats.streak(form(new int[][]{{0, 1, 1}, {1, 1, 0}, {2, 1, 1}, {1, 0, 0}, {3, 2, 1}}), "WHI")[1]);
        assertEquals("WHI unbeaten in 4", MatchStats.streak(form(new int[][]{{0, 2, 1}, {1, 1, 0}, {2, 1, 1}, {0, 0, 0}, {3, 2, 1}}), "WHI")[1]);
        assertEquals("WHI have lost their last 5", MatchStats.streak(form(new int[][]{{0, 1, 1}, {1, 2, 0}, {0, 3, 1}, {1, 4, 0}, {0, 2, 1}}), "WHI")[1]);
        assertEquals("WHI without a win in 3", MatchStats.streak(form(new int[][]{{2, 1, 1}, {3, 0, 0}, {1, 1, 1}, {0, 2, 0}, {2, 2, 1}}), "WHI")[1]);
        assertEquals("WHI have scored in each of their last 5",
                MatchStats.streak(form(new int[][]{{1, 2, 1}, {2, 1, 0}, {1, 3, 1}, {1, 1, 0}, {2, 1, 1}}), "WHI")[1]);
        // Nothing worth a word: no line.
        assertNull(MatchStats.streak(form(new int[][]{{1, 0, 1}, {0, 1, 0}, {2, 1, 1}, {0, 1, 0}, {1, 0, 1}}), "WHI"));
        assertNull(MatchStats.streak(form(new int[][]{{1, 0, 1}, {2, 0, 0}}), "WHI"));
        assertNull(MatchStats.streak(null, "WHI"));
        assertEquals("Streak", MatchStats.streak(form(new int[][]{{2, 0, 1}, {3, 1, 0}, {1, 0, 1}}), "WHI")[0]);
    }

    @Test
    public void aDroughtIsSaidOnceTheyHaveNoneTodayByTheHalfHour() throws Exception {
        JSONArray dry = form(new int[][]{{2, 0, 1}, {1, 1, 0}, {0, 1, 1}, {0, 0, 0}, {0, 2, 1}});
        assertEquals("RIV without a goal in their last 3 games", MatchStats.drought(dry, "RIV", 0, 34)[1]);
        assertNull(MatchStats.drought(dry, "RIV", 0, 20));
        assertNull(MatchStats.drought(dry, "RIV", 1, 34));
        assertNull(MatchStats.drought(form(new int[][]{{2, 0, 1}, {0, 1, 1}}), "RIV", 0, 50));
    }

    private static JSONObject entry(String id, double pts, double gd) throws Exception {
        return new JSONObject().put("id", id).put("stats", new JSONArray()
                .put(new JSONObject().put("name", "points").put("value", pts))
                .put(new JSONObject().put("name", "pointDifferential").put("value", gd)));
    }

    @Test
    public void theTableAsItStandsMovesTheSidesByTheScore() throws Exception {
        JSONObject standings = new JSONObject().put("groups", new JSONArray().put(new JSONObject().put("standings",
                new JSONObject().put("entries", new JSONArray()
                        .put(entry("77", 30, 12)).put(entry("1001", 29, 10)).put(entry("55", 27, 4)).put(entry("2002", 26, 2))))));
        // Whitebrook winning go top; Riverside stay fourth.
        assertEquals("WHI 1st (up 1) · RIV 4th", MatchStats.asItStands(standings, "1001", "2002", 2, 1, "WHI", "RIV")[1]);
        // Riverside winning by two climb one, Whitebrook staying ahead on goal difference.
        assertEquals("WHI 2nd · RIV 3rd (up 1)", MatchStats.asItStands(standings, "1001", "2002", 0, 2, "WHI", "RIV")[1]);
        // By four, level on both, Riverside's goals put them past Whitebrook and the third side.
        assertEquals("WHI 3rd (down 1) · RIV 2nd (up 2)", MatchStats.asItStands(standings, "1001", "2002", 0, 4, "WHI", "RIV")[1]);
        // A draw: Whitebrook level on points with the top side but behind on goal difference.
        assertEquals("WHI 2nd · RIV 4th", MatchStats.asItStands(standings, "1001", "2002", 1, 1, "WHI", "RIV")[1]);
        assertNull(MatchStats.asItStands(standings, "1001", "3003", 1, 0, "WHI", "OTH"));
        assertNull(MatchStats.asItStands(null, "1001", "2002", 1, 0, "WHI", "RIV"));
        assertEquals("As it stands", MatchStats.asItStands(standings, "1001", "2002", 2, 1, "WHI", "RIV")[0]);
        assertEquals("1st", MatchStats.ordinal(1));
        assertEquals("2nd", MatchStats.ordinal(2));
        assertEquals("3rd", MatchStats.ordinal(3));
        assertEquals("11th", MatchStats.ordinal(11));
        assertEquals("12th", MatchStats.ordinal(12));
        assertEquals("22nd", MatchStats.ordinal(22));
    }

    private static JSONObject shot(int id, String type, double xg, double xgot, String last) throws Exception {
        return new JSONObject().put("id", id).put("eventType", type).put("expectedGoals", xg).put("expectedGoalsOnTarget", xgot)
                .put("isOnTarget", !"Miss".equals(type)).put("isBlocked", false).put("isOwnGoal", false).put("situation", "RegularPlay")
                .put("lastName", last).put("playerName", "Sam " + last);
    }

    @Test
    public void aBigSaveAndAGreatFinishAreEachSaidOnce() throws Exception {
        JSONArray shots = new JSONArray()
                .put(shot(1, "AttemptSaved", 0.12, 0.71, "Okafor"))
                .put(shot(2, "Miss", 0.4, 0, "Brandt"))
                .put(shot(3, "Goal", 0.05, 0.3, "Brandt"))
                .put(shot(4, "AttemptSaved", 0.1, 0.2, "Hollis"));
        Set<String> shown = new HashSet<>();
        assertArrayEquals(new String[]{"Great finish", "Brandt scored a 0.05 xG chance", "", "one"}, MatchStats.shotMoment(shots, shown));
        assertArrayEquals(new String[]{"Big save", "0.71 xGOT from Okafor's shot", "", "one"}, MatchStats.shotMoment(shots, shown));
        assertNull(MatchStats.shotMoment(shots, shown));
        assertNull(MatchStats.shotMoment(null, shown));
        // A penalty scored is no great finish, whatever its chance.
        JSONArray pen = new JSONArray().put(shot(9, "Goal", 0.04, 0.9, "Marsh").put("situation", "Penalty"));
        assertNull(MatchStats.shotMoment(pen, new HashSet<>()));
    }

    @Test
    public void aComebackIsSaidAtTheGoalThatTurnsIt() {
        // 0-2, 1-2, 2-2: Whitebrook level after being two down.
        assertArrayEquals(new String[]{"Comeback", "WHI were 2–0 down", "", "one"},
                MatchStats.comeback(new int[]{2, 2, 1, 1}, new int[]{10, 25, 60, 70}, "WHI", "RIV"));
        // Then 3-2: ahead for the first time since.
        assertEquals("WHI were 2–0 down and now lead",
                MatchStats.comeback(new int[]{2, 2, 1, 1, 1}, new int[]{10, 25, 60, 70, 85}, "WHI", "RIV")[1]);
        // The goal that made it 1-2 turned nothing yet.
        assertNull(MatchStats.comeback(new int[]{2, 2, 1}, new int[]{10, 25, 60}, "WHI", "RIV"));
        // One behind: a comeback only when the leveller comes past the hour.
        assertNull(MatchStats.comeback(new int[]{1, 2}, new int[]{10, 30}, "WHI", "RIV"));
        assertEquals("RIV were 1–0 down", MatchStats.comeback(new int[]{1, 2}, new int[]{10, 75}, "WHI", "RIV")[1]);
        assertNull(MatchStats.comeback(new int[]{}, new int[]{}, "WHI", "RIV"));
    }

    @Test
    public void anEarlyGoalIsSaidByItsClock() {
        assertEquals("Goal after 48 seconds", MatchStats.early("0:48")[1]);
        assertEquals("Goal after 3:12", MatchStats.early("3:12")[1]);
        assertNull(MatchStats.early("5:00"));
        assertNull(MatchStats.early("67:10"));
        assertNull(MatchStats.early("HT"));
        assertNull(MatchStats.early(null));
    }

    @Test
    public void theRefereesCardsSoFar() {
        assertEquals("Ref. M. Example", MatchStats.refCards("Mark Example", 0, 0)[1]);
        assertEquals("Ref. M. Example · 1 yellow so far", MatchStats.refCards("Mark Example", 1, 0)[1]);
        assertEquals("Ref. M. Example · 4 yellows so far · 1 red", MatchStats.refCards("Mark Example", 4, 1)[1]);
        assertEquals("Ref. Example", MatchStats.refCards("Example", 0, 0)[1]);
        assertNull(MatchStats.refCards("", 2, 0));
        assertNull(MatchStats.refCards(null, 2, 0));
    }
}
