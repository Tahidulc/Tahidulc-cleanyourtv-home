package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The ad-break screen's sums: when it comes and goes, the cards' turns, form, fixtures, the table, the meetings. */
public class AdBreakLogicTest {
    private static final long MIN = 60_000L;

    // ---- when -------------------------------------------------------------------------------------------------------

    @Test public void comesAfterTheLogoHasBeenGoneSixSecondsAndGoesWhenItIsBack() {
        AdBreakLogic.Trigger t = new AdBreakLogic.Trigger();
        long now = 0;
        assertEquals(AdBreakLogic.NONE, t.step(now, true, true, true, 30 * MIN, 0.9f));
        for (int i = 0; i < 6; i++) assertEquals(AdBreakLogic.NONE, t.step(now += 1000, true, true, true, 30 * MIN, 0.1f));
        assertEquals(AdBreakLogic.SHOW, t.step(now += 1000, true, true, true, 30 * MIN, 0.1f));
        assertEquals(AdBreakLogic.NONE, t.step(now += 1000, true, true, true, 30 * MIN, 0.05f));
        // One look back is not enough (a flash of it in the adverts); two are.
        assertEquals(AdBreakLogic.NONE, t.step(now += 1000, true, true, true, 30 * MIN, 0.9f));
        assertEquals(AdBreakLogic.HIDE, t.step(now += 1000, true, true, true, 30 * MIN, 0.9f));
    }

    @Test public void aShortGapOrALookThatCannotTellChangesNothing() {
        AdBreakLogic.Trigger t = new AdBreakLogic.Trigger();
        long now = 0;
        for (int i = 0; i < 4; i++) t.step(now += 1000, true, true, true, 30 * MIN, 0.1f);
        assertEquals(AdBreakLogic.NONE, t.step(now += 1000, true, true, true, 30 * MIN, 0.9f));
        for (int i = 0; i < 5; i++) assertEquals(AdBreakLogic.NONE, t.step(now += 1000, true, true, true, 30 * MIN, 0.1f));
        assertEquals(AdBreakLogic.NONE, t.step(now += 1000, true, true, true, 30 * MIN, -1f));
        assertEquals(AdBreakLogic.SHOW, t.step(now += 1000, true, true, true, 30 * MIN, 0.1f));
    }

    @Test public void onlyWithinTheHourBeforeKickOffOnTheGamesChannelInThePlayerWithTheSwitchOn() {
        long now = 0;
        for (boolean[] k : new boolean[][]{{false, true, true}, {true, false, true}, {true, true, false}}) {
            AdBreakLogic.Trigger t = new AdBreakLogic.Trigger();
            for (int i = 0; i < 10; i++) assertEquals(AdBreakLogic.NONE, t.step(now += 1000, k[0], k[1], k[2], 30 * MIN, 0.1f));
        }
        AdBreakLogic.Trigger far = new AdBreakLogic.Trigger();
        for (int i = 0; i < 10; i++) assertEquals(AdBreakLogic.NONE, far.step(now += 1000, true, true, true, 61 * MIN, 0.1f));
        // Up, then kick-off (or the channel changed): away at once.
        AdBreakLogic.Trigger t = new AdBreakLogic.Trigger();
        boolean shown = false;
        for (int i = 0; i < 8; i++) shown |= t.step(now += 1000, true, true, true, 10 * MIN, 0.1f) == AdBreakLogic.SHOW;
        assertTrue(shown);
        assertEquals(AdBreakLogic.HIDE, t.step(now += 1000, true, true, true, 0, 0.1f));
    }

    @Test public void theCardsChangeFourSecondsApartAndGoRound() {
        assertEquals(12_000L, AdBreakLogic.firstTurn(0));
        assertEquals(16_000L, AdBreakLogic.firstTurn(1));
        assertEquals(20_000L, AdBreakLogic.firstTurn(2));
        assertEquals(0, AdBreakLogic.next(7, 1, 8));
        assertEquals(7, AdBreakLogic.next(0, -1, 8));
        assertEquals(3, AdBreakLogic.next(2, 1, 8));
    }

    // ---- form and fixtures ---------------------------------------------------------------------------------------------

    private static JSONObject event(String date, boolean done, String us, String usHa, int usGoals, String them, int themGoals) throws Exception {
        JSONObject a = new JSONObject().put("id", us).put("homeAway", usHa).put("winner", done && usGoals > themGoals)
                .put("score", new JSONObject().put("value", (double) usGoals).put("displayValue", String.valueOf(usGoals)))
                .put("team", new JSONObject().put("id", us).put("displayName", "Us"));
        JSONObject b = new JSONObject().put("id", them).put("homeAway", "home".equals(usHa) ? "away" : "home").put("winner", done && themGoals > usGoals)
                .put("score", new JSONObject().put("value", (double) themGoals).put("displayValue", String.valueOf(themGoals)))
                .put("team", new JSONObject().put("id", them).put("shortDisplayName", "Them " + them)
                        .put("logos", new JSONArray().put(new JSONObject().put("href", "https://x/" + them + ".png"))));
        JSONObject comp = new JSONObject().put("status", new JSONObject().put("type", new JSONObject().put("completed", done)))
                .put("competitors", new JSONArray().put(a).put(b));
        return new JSONObject().put("date", date).put("competitions", new JSONArray().put(comp));
    }

    @Test public void formIsTheLastFiveInThisCompetitionOldestFirst() throws Exception {
        JSONArray ev = new JSONArray()
                // Listed out of order, as ESPN's schedule can be.
                .put(event("2026-10-25T15:00Z", true, "360", "home", 0, "349", 3))
                .put(event("2026-09-20T15:00Z", true, "360", "away", 2, "370", 0))
                .put(event("2026-10-18T15:00Z", true, "360", "away", 1, "357", 1))
                .put(event("2026-10-31T17:30Z", true, "360", "away", 0, "363", 2))
                .put(event("2026-11-08T16:30Z", true, "360", "home", 4, "362", 1))
                .put(event("2026-09-13T15:00Z", true, "360", "home", 3, "382", 0))
                .put(event("2026-11-22T15:00Z", false, "360", "home", 0, "368", 0));
        JSONObject sched = new JSONObject().put("events", ev);
        long before = AdBreakLogic.time("2026-11-22T15:00Z");
        assertEquals("DLLWW".length(), AdBreakLogic.form(sched, "360", before, 5).length());
        assertEquals("WDLLW", AdBreakLogic.form(sched, "360", before, 5));
        assertEquals("LW", AdBreakLogic.form(sched, "360", before, 2));
        assertEquals("", AdBreakLogic.form(new JSONObject(), "360", before, 5));
    }

    @Test public void aDrawIsADrawAndAScoreAsAStringIsRead() throws Exception {
        JSONArray cs = new JSONArray().put(new JSONObject().put("id", "1").put("score", "2")).put(new JSONObject().put("id", "2").put("score", "2"));
        assertEquals("D", AdBreakLogic.result(cs, "1"));
        assertNull(AdBreakLogic.result(cs, "9"));
    }

    @Test public void comingUpIsTheNextThreeWithWhereAndWho() throws Exception {
        JSONArray ev = new JSONArray()
                .put(event("2026-12-06T15:00Z", false, "360", "home", 0, "361", 0))
                .put(event("2026-11-29T16:30Z", false, "360", "away", 0, "363", 0))
                .put(event("2026-11-22T15:00Z", false, "360", "home", 0, "368", 0))
                .put(event("2026-12-13T15:00Z", false, "360", "home", 0, "364", 0));
        List<String[]> next = AdBreakLogic.comingUp(new JSONObject().put("events", ev), "360", AdBreakLogic.time("2026-11-22T15:01Z"), 3);
        assertEquals(3, next.size());
        assertArrayEquals(new String[]{"363", "Them 363", "https://x/363.png", "A"}, java.util.Arrays.copyOf(next.get(0), 4));
        assertEquals("361", next.get(1)[0]);
        assertEquals("H", next.get(1)[3]);
    }

    // ---- the table ------------------------------------------------------------------------------------------------------

    private static List<AdBreakLogic.Row> table(int... ptsGd) {
        List<AdBreakLogic.Row> rows = new ArrayList<>();
        for (int i = 0; i < ptsGd.length / 2; i++) rows.add(new AdBreakLogic.Row("t" + i, "Team " + i, i + 1, 12, ptsGd[2 * i], ptsGd[2 * i + 1], 0, 0, 0));
        return rows;
    }

    @Test public void theTableIsReadFromTheSummary() throws Exception {
        JSONArray stats = new JSONArray().put(new JSONObject().put("name", "rank").put("value", 4.0)).put(new JSONObject().put("name", "points").put("value", 24.0))
                .put(new JSONObject().put("name", "pointDifferential").put("displayValue", "+9")).put(new JSONObject().put("name", "gamesPlayed").put("value", 12.0));
        JSONObject entry = new JSONObject().put("id", "360").put("team", "Manchester United").put("stats", stats);
        JSONObject sum = new JSONObject().put("standings", new JSONObject().put("groups", new JSONArray().put(
                new JSONObject().put("standings", new JSONObject().put("entries", new JSONArray().put(entry))))));
        List<AdBreakLogic.Row> rows = AdBreakLogic.table(sum);
        assertEquals(1, rows.size());
        assertEquals(4, rows.get(0).rank);
        assertEquals(24, rows.get(0).points);
        assertEquals(9, rows.get(0).gd);
        assertEquals(12, rows.get(0).played);
    }

    @Test public void aroundShowsOneRunOfFourWhenCloseElseTwoPairs() {
        List<AdBreakLogic.Row> t = table(40, 20, 35, 15, 30, 10, 28, 8, 25, 5, 20, 0, 18, -2, 15, -5, 10, -10, 5, -20);
        List<AdBreakLogic.Row> near = AdBreakLogic.around(t, "t3", "t4");
        assertEquals(4, near.size());
        assertTrue(near.contains(t.get(3)) && near.contains(t.get(4)));
        List<AdBreakLogic.Row> far = AdBreakLogic.around(t, "t1", "t8");
        assertEquals(4, far.size());
        assertEquals("t0", far.get(0).id);
        assertEquals("t1", far.get(1).id);
        assertEquals("t7", far.get(2).id);
        assertEquals("t8", far.get(3).id);
        assertTrue(AdBreakLogic.around(t, "t1", "nobody").isEmpty());
    }

    @Test public void ifItFinishedNowMovesThemByThePointsThenGoalDifference() {
        // Third on 30 (+10), fourth on 28 (+8): fourth beating third 1-0 goes above it on points.
        List<AdBreakLogic.Row> t = table(40, 20, 35, 15, 30, 10, 28, 8, 25, 5);
        assertArrayEquals(new int[]{3, 4}, AdBreakLogic.placesIf(t, "t3", "t2", 1, 0));
        assertArrayEquals(new int[]{4, 3}, AdBreakLogic.placesIf(t, "t3", "t2", 0, 0));
        assertArrayEquals(new int[]{4, 3}, AdBreakLogic.placesIf(t, "t3", "t2", 0, 1));
        assertNull(AdBreakLogic.placesIf(t, "t3", "nobody", 1, 0));
        assertEquals("1st", AdBreakLogic.ordinal(1));
        assertEquals("12th", AdBreakLogic.ordinal(12));
        assertEquals("22nd", AdBreakLogic.ordinal(22));
        assertEquals("3rd", AdBreakLogic.ordinal(3));
    }

    // ---- the meetings, the referee, the absent -------------------------------------------------------------------------

    @Test public void headToHeadIsFromTheHomeSideOldestFirst() throws Exception {
        JSONArray events = new JSONArray();
        String[][] games = {{"2026-03-15T14:00:00Z", "2", "1"}, {"2025-10-05T14:00:00Z", "0", "0"}, {"2025-02-01T14:00:00Z", "1", "2"}};
        for (String[] g : games) {
            int a = Integer.parseInt(g[1]), b = Integer.parseInt(g[2]);
            events.put(new JSONObject().put("id", "e" + g[0].substring(0, 4)).put("date", g[0])
                    .put("statusType", new JSONObject().put("completed", true))
                    .put("competitors", new JSONArray()
                            .put(new JSONObject().put("winner", a > b).put("score", g[1]).put("team", new JSONObject().put("id", "360")))
                            .put(new JSONObject().put("winner", b > a).put("score", g[2]).put("team", new JSONObject().put("id", "368")))));
        }
        JSONObject sum = new JSONObject().put("seasonseries", new JSONArray().put(new JSONObject().put("type", "head-to-head").put("events", events)));
        List<String[]> h = AdBreakLogic.headToHead(sum, "360");
        assertEquals(3, h.size());
        assertEquals("1–2", h.get(0)[0]);
        assertEquals("L", h.get(0)[1]);
        assertEquals("D", h.get(1)[1]);
        assertEquals("W", h.get(2)[1]);
        assertArrayEquals(new int[]{1, 1, 1}, AdBreakLogic.tally(h));
    }

    @Test public void theRefereeAndTheAbsentOnlyWhenNamed() throws Exception {
        JSONObject sum = new JSONObject().put("gameInfo", new JSONObject().put("officials", new JSONArray()
                .put(new JSONObject().put("displayName", "A Linesman").put("position", new JSONObject().put("name", "Assistant Referee")))
                .put(new JSONObject().put("displayName", "Sam Barrow").put("position", new JSONObject().put("name", "Referee")))));
        assertEquals("Sam Barrow", AdBreakLogic.referee(sum));
        assertNull(AdBreakLogic.referee(new JSONObject()));
        JSONObject inj = new JSONObject().put("injuries", new JSONArray().put(new JSONObject().put("team", new JSONObject().put("id", "360"))
                .put("injuries", new JSONArray().put(new JSONObject().put("status", "Out").put("athlete", new JSONObject().put("shortName", "L. Shaw"))))));
        assertEquals(1, AdBreakLogic.absent(inj).size());
        assertEquals("L. Shaw", AdBreakLogic.absent(inj).get(0)[1]);
        assertTrue(AdBreakLogic.absent(new JSONObject()).isEmpty());
    }

    @Test public void espnTimesAreRead() {
        assertEquals(AdBreakLogic.time("2026-11-08T16:30:00Z"), AdBreakLogic.time("2026-11-08T16:30Z"));
        assertEquals(0, AdBreakLogic.time("not a time"));
    }
}
