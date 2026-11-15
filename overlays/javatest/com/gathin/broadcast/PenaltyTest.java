package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The penalty card: a penalty given read from ESPN's commentary (its own words), until its kick; and the records. */
public class PenaltyTest {
    private static List<String[]> lines(String[]... l) {
        List<String[]> out = new ArrayList<>();
        int seq = 0;
        for (String[] x : l) out.add(new String[]{x[1], x[0], String.valueOf(seq++)});
        return out;
    }

    @Test
    public void norwayGivenOneInOslo() {
        // Norway v Wales, 85': ESPN's two lines, the side's name and who gave it away first.
        List<String[]> l = lines(
                new String[]{"84'", "Attempt missed. Erling Haaland (Norway) header from the centre of the box is too high."},
                new String[]{"85'", "Penalty conceded by Connor Roberts (Wales) after a foul in the penalty area."},
                new String[]{"85'", "Penalty Norway. Andreas Schjelderup draws a foul in the penalty area."});
        Penalty.Given g = Penalty.pending(l, "Norway", "Wales");
        assertNotNull(g);
        assertEquals("Norway", g.team);
        assertEquals("Andreas Schjelderup", g.wonBy);
        assertEquals("Connor Roberts", g.concededBy);
        assertEquals(1, Penalty.side(g, "Norway", "Wales"));
        assertEquals("Won by Schjelderup · given away by Roberts", Penalty.wonLine(g));
    }

    @Test
    public void goneOnceScored() {
        List<String[]> l = lines(
                new String[]{"85'", "Penalty conceded by Connor Roberts (Wales) after a foul in the penalty area."},
                new String[]{"85'", "Penalty Norway. Andreas Schjelderup draws a foul in the penalty area."},
                new String[]{"88'", "Goal! Norway 3, Wales 1. Erling Haaland (Norway) converts the penalty with a left footed shot to the bottom right corner."});
        assertNull(Penalty.pending(l, "Norway", "Wales"));
    }

    @Test
    public void goneOnceSavedOrMissed() {
        List<String[]> saved = lines(
                new String[]{"83'", "Penalty conceded by András Schäfer (Union Berlin) after a foul in the penalty area."},
                new String[]{"83'", "Penalty Freiburg. Keisuke Goto draws a foul in the penalty area."},
                new String[]{"84'", "Penalty saved. Vincenzo Grifo (Freiburg) right footed shot saved in the top right corner by Frederik Rønnow (Union Berlin)."});
        assertNull(Penalty.pending(saved, "SC Freiburg", "1. FC Union Berlin"));
        List<String[]> missed = lines(
                new String[]{"30'", "Penalty Chelsea. Cole Palmer draws a foul in the penalty area."},
                new String[]{"31'", "Penalty missed! Bad penalty by Cole Palmer (Chelsea) right footed shot is high and wide."});
        assertNull(Penalty.pending(missed, "Chelsea", "Arsenal"));
    }

    @Test
    public void givenAfterVar() {
        // Portugal v Denmark, 40': a handball, given on VAR's say; Denmark's own line never comes.
        List<String[]> l = lines(
                new String[]{"40'", "Penalty conceded by Rúben Dias (Portugal) with a handball in the penalty area."},
                new String[]{"40'", "VAR Decision: Penalty Denmark."});
        Penalty.Given g = Penalty.pending(l, "Portugal", "Denmark");
        assertNotNull(g);
        assertEquals("Denmark", g.team);
        assertTrue(g.var);
        assertEquals(2, Penalty.side(g, "Portugal", "Denmark"));
        assertEquals("Given away by Dias · after VAR", Penalty.wonLine(g));
    }

    @Test
    public void takenBackByVar() {
        List<String[]> l = lines(
                new String[]{"52'", "Penalty Wales. Brennan Johnson draws a foul in the penalty area."},
                new String[]{"54'", "VAR Decision: No Penalty Wales."});
        assertNull(Penalty.pending(l, "Norway", "Wales"));
    }

    @Test
    public void onlyConcededSaysTheOtherSide() {
        List<String[]> l = lines(new String[]{"13'", "Penalty conceded by Boubacar Kamara (Aston Villa) after a foul in the penalty area."});
        Penalty.Given g = Penalty.pending(l, "Manchester United", "Aston Villa");
        assertNotNull(g);
        assertEquals("Manchester United", g.team);
        assertEquals(1, Penalty.side(g, "Manchester United", "Aston Villa"));
    }

    @Test
    public void oneNeverTakenGoesStale() {
        List<String[]> l = lines(
                new String[]{"20'", "Penalty Norway. Andreas Schjelderup draws a foul in the penalty area."},
                new String[]{"29'", "Corner, Wales. Conceded by Kristoffer Ajer."});
        assertNull(Penalty.pending(l, "Norway", "Wales"));
    }

    @Test
    public void minutes() {
        assertEquals(85, Penalty.minuteOf("85'"));
        assertEquals(93, Penalty.minuteOf("90'+3'"));
        assertEquals(-1, Penalty.minuteOf(""));
    }

    @Test
    public void figuresFromEspn() throws Exception {
        JSONObject stats = new JSONObject().put("splits", new JSONObject().put("categories", new JSONArray()
                .put(new JSONObject().put("name", "offensive").put("stats", new JSONArray()
                        .put(new JSONObject().put("name", "penaltyKickGoals").put("value", 4.0))
                        .put(new JSONObject().put("name", "penaltyKickShots").put("value", 6.0))))
                .put(new JSONObject().put("name", "goalKeeping").put("stats", new JSONArray()
                        .put(new JSONObject().put("name", "penaltyKicksFaced").put("value", 0.0))
                        .put(new JSONObject().put("name", "penaltyKicksSaved").put("value", 0.0))))));
        assertTrue(Arrays.equals(new int[]{4, 6, 0, 0}, Penalty.figures(stats)));
        assertNull(Penalty.figures(new JSONObject()));
    }

    @Test
    public void likelyTakerAndLines() {
        int[][] now = {{0, 0, 0, 0}, {2, 2, 0, 0}, null, {1, 2, 0, 0}};
        assertEquals(1, Penalty.likelyTaker(now, null));
        assertEquals(-1, Penalty.likelyTaker(new int[][]{{0, 0, 0, 0}}, new int[][]{{0, 0, 0, 0}}));
        assertEquals(0, Penalty.likelyTaker(new int[][]{{0, 0, 0, 0}, {0, 0, 0, 0}}, new int[][]{{4, 6, 0, 0}, {1, 1, 0, 0}}));
        assertEquals("Scored 2 of 2 this season", Penalty.takerLine(now[1], null));
        assertEquals("Scored 4 of 6 last season", Penalty.takerLine(new int[]{0, 0, 0, 0}, new int[]{4, 6, 0, 0}));
        assertNull(Penalty.takerLine(new int[]{0, 0, 0, 0}, null));
        assertEquals("Saved 1 of 6 this season", Penalty.keeperLine(new int[]{0, 0, 6, 1}, null));
        assertEquals("Faced 3 last season, saved none", Penalty.keeperLine(new int[]{0, 0, 0, 0}, new int[]{0, 0, 3, 0}));
        assertNull(Penalty.keeperLine(new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 0}));
    }

    @Test
    public void onThePitch() throws Exception {
        JSONArray rosters = new JSONArray().put(new JSONObject().put("homeAway", "home").put("roster", new JSONArray()
                .put(player("1", "Egil Selvik", "G", true, false, false))
                .put(player("2", "Erling Haaland", "F", true, false, false))
                .put(player("3", "Martin Ødegaard", "AM", true, false, true))
                .put(player("4", "Oscar Bobb", "SUB", false, true, false))
                .put(player("5", "Jens Hauge", "SUB", false, false, false))));
        List<Penalty.OnPitch> on = Penalty.onPitch(rosters, true);
        assertEquals(3, on.size());
        assertTrue(on.get(0).keeper);
        assertEquals("Erling Haaland", on.get(1).name);
        assertEquals("Oscar Bobb", on.get(2).name);
        assertTrue(Penalty.onPitch(rosters, false).isEmpty());
    }

    private static JSONObject player(String id, String name, String pos, boolean starter, boolean in, boolean out) throws Exception {
        return new JSONObject().put("starter", starter).put("subbedIn", in).put("subbedOut", out)
                .put("position", new JSONObject().put("abbreviation", pos))
                .put("athlete", new JSONObject().put("id", id).put("displayName", name));
    }

    @Test
    public void recordsLookedUp() throws Exception {
        JSONObject summary = new JSONObject().put("header", new JSONObject().put("season", new JSONObject().put("year", 2026)))
                .put("rosters", new JSONArray()
                        .put(new JSONObject().put("homeAway", "home").put("roster", new JSONArray()
                                .put(player("10", "Leo Brandt", "F", true, false, false))
                                .put(player("11", "Sam Okafor", "AM", true, false, false))))
                        .put(new JSONObject().put("homeAway", "away").put("roster", new JSONArray()
                                .put(player("20", "Jonas Vale", "G", true, false, false)))));
        List<String> asked = new ArrayList<>();
        Penalty.Records r = Penalty.records(summary, "test.1", true, url -> {
            asked.add(url);
            int[] f = url.contains("/seasons/2026/") && url.contains("/athletes/10/") ? new int[]{4, 5, 0, 0}
                    : url.contains("/seasons/2026/") && url.contains("/athletes/20/") ? new int[]{0, 0, 6, 1} : new int[]{0, 0, 0, 0};
            return new JSONObject().put("splits", new JSONObject().put("categories", new JSONArray().put(new JSONObject().put("stats", new JSONArray()
                    .put(new JSONObject().put("name", "penaltyKickGoals").put("value", f[0]))
                    .put(new JSONObject().put("name", "penaltyKickShots").put("value", f[1]))
                    .put(new JSONObject().put("name", "penaltyKicksFaced").put("value", f[2]))
                    .put(new JSONObject().put("name", "penaltyKicksSaved").put("value", f[3])))))).toString();
        });
        assertEquals("Leo Brandt", r.taker);
        assertEquals("Scored 4 of 5 this season", r.takerLine);
        assertEquals("Jonas Vale", r.keeper);
        assertEquals("Saved 1 of 6 this season", r.keeperLine);
        assertTrue(asked.get(0).startsWith("https://sports.core.api.espn.com/v2/sports/soccer/leagues/test.1/seasons/2026/types/1/athletes/"));
        // Read once a session: the same penalty side again asks for nothing new.
        int before = asked.size();
        Penalty.records(summary, "test.1", true, url -> {
            asked.add(url);
            return "{}";
        });
        assertEquals(before, asked.size());
        assertFalse(asked.isEmpty());
    }
}
