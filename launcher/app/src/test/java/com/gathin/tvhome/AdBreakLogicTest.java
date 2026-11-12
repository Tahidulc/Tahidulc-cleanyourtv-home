package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        assertEquals(AdBreakLogic.NONE, t.step(now, true, true, true, true, 0.9f));
        for (int i = 0; i < 6; i++) assertEquals(AdBreakLogic.NONE, t.step(now += 1000, true, true, true, true, 0.1f));
        assertEquals(AdBreakLogic.SHOW, t.step(now += 1000, true, true, true, true, 0.1f));
        assertEquals(AdBreakLogic.NONE, t.step(now += 1000, true, true, true, true, 0.05f));
        // One look back is not enough (a flash of it in the adverts); two are.
        assertEquals(AdBreakLogic.NONE, t.step(now += 1000, true, true, true, true, 0.9f));
        assertEquals(AdBreakLogic.HIDE, t.step(now += 1000, true, true, true, true, 0.9f));
    }

    @Test public void aShortGapOrALookThatCannotTellChangesNothing() {
        AdBreakLogic.Trigger t = new AdBreakLogic.Trigger();
        long now = 0;
        for (int i = 0; i < 4; i++) t.step(now += 1000, true, true, true, true, 0.1f);
        assertEquals(AdBreakLogic.NONE, t.step(now += 1000, true, true, true, true, 0.9f));
        for (int i = 0; i < 5; i++) assertEquals(AdBreakLogic.NONE, t.step(now += 1000, true, true, true, true, 0.1f));
        assertEquals(AdBreakLogic.NONE, t.step(now += 1000, true, true, true, true, -1f));
        assertEquals(AdBreakLogic.SHOW, t.step(now += 1000, true, true, true, true, 0.1f));
    }

    @Test public void onlyInsideTheWindowOnTheChannelInThePlayerWithTheSwitchOn() {
        long now = 0;
        for (boolean[] k : new boolean[][]{{false, true, true}, {true, false, true}, {true, true, false}}) {
            AdBreakLogic.Trigger t = new AdBreakLogic.Trigger();
            for (int i = 0; i < 10; i++) assertEquals(AdBreakLogic.NONE, t.step(now += 1000, k[0], k[1], k[2], true, 0.1f));
        }
        AdBreakLogic.Trigger far = new AdBreakLogic.Trigger();
        for (int i = 0; i < 10; i++) assertEquals(AdBreakLogic.NONE, far.step(now += 1000, true, true, true, false, 0.1f));
        // Up, then the window closes (or the channel changed): away at once.
        AdBreakLogic.Trigger t = new AdBreakLogic.Trigger();
        boolean shown = false;
        for (int i = 0; i < 8; i++) shown |= t.step(now += 1000, true, true, true, true, 0.1f) == AdBreakLogic.SHOW;
        assertTrue(shown);
        assertEquals(AdBreakLogic.HIDE, t.step(now += 1000, true, true, true, false, 0.1f));
    }

    // ---- what is on: the guide's programme, the game, the window ----------------------------------------------------------

    private static String[][] game(String home, String away) {
        return new String[][]{{home}, {away}};
    }

    @Test public void aGuideTitleNamesTheGameByBothClubs() {
        String[] eng = {"England"}, cro = {"Croatia"};
        assertEquals(2, AdBreakLogic.names("Live International Football: England v Croatia", eng, cro));
        assertEquals(2, AdBreakLogic.names("03. England vs Croatia 19:45", eng, cro));
        assertEquals(2, AdBreakLogic.names("LIVE Croatia v England", eng, cro));
        assertEquals(2, AdBreakLogic.names("England v Croatia - World Cup Qualifier", eng, cro));
        assertEquals(1, AdBreakLogic.names("England v Serbia", eng, cro));
        assertEquals(0, AdBreakLogic.names("Football Focus", eng, cro));
        assertEquals(0, AdBreakLogic.names("England and Croatia preview", eng, cro));
        assertEquals(0, AdBreakLogic.names(null, eng, cro));
        // ESPN's full name and its short one both count.
        assertEquals(2, AdBreakLogic.names("Premier League: Spurs v Arsenal",
                new String[]{"Tottenham Hotspur", "Tottenham"}, new String[]{"Arsenal"}));
    }

    @Test public void theGameIsPickedOnlyWhenOneIsClearlyNamed() {
        List<String[][]> games = new ArrayList<>();
        games.add(game("Wales", "Belgium"));
        games.add(game("England", "Croatia"));
        games.add(game("England", "Serbia"));
        assertEquals(1, AdBreakLogic.pick("Live: England v Croatia", games));
        assertEquals(0, AdBreakLogic.pick("Wales v North Macedonia", games));
        // England alone names two games: neither is picked.
        assertEquals(-1, AdBreakLogic.pick("England v Spain", games));
        assertEquals(-1, AdBreakLogic.pick("Gogglebox", games));
        assertEquals(-1, AdBreakLogic.pick("England v Croatia", new ArrayList<>()));
    }

    @Test public void theWindowsOpenAndCloseWhereTheySay() {
        long start = 1_000 * MIN, end = start + 150 * MIN;
        assertArrayEquals(new long[]{start, end + 5 * MIN}, AdBreakLogic.listedWindow(start, end));
        long kick = 2_000 * MIN;
        assertArrayEquals(new long[]{kick - 60 * MIN, kick + 145 * MIN}, AdBreakLogic.gameWindow(kick, 1, kick - 90 * MIN, 0));
        // Still on long after: half an hour from now; over after extra time: half an hour after it was last on.
        assertArrayEquals(new long[]{kick - 60 * MIN, kick + 170 * MIN}, AdBreakLogic.gameWindow(kick, 0, kick + 140 * MIN, 0));
        assertArrayEquals(new long[]{kick - 60 * MIN, kick + 182 * MIN}, AdBreakLogic.gameWindow(kick, 2, kick + 160 * MIN, kick + 152 * MIN));
        assertArrayEquals(new long[]{kick - 60 * MIN, kick + 145 * MIN}, AdBreakLogic.gameWindow(kick, 2, kick + 120 * MIN, kick + 110 * MIN));
        // The next programme in the guide counts from now, its lead-in.
        assertArrayEquals(new long[]{start - 10 * MIN, end + 5 * MIN}, AdBreakLogic.listedWindow(start, end, start - 10 * MIN));
        assertArrayEquals(new long[]{start, end + 5 * MIN}, AdBreakLogic.listedWindow(start, end, start + 10 * MIN));
        assertArrayEquals(new long[]{kick - MIN, kick + 20 * MIN}, AdBreakLogic.generalWindow(kick));
        long[] w = AdBreakLogic.gameWindow(kick, 1, kick, 0);
        assertTrue(AdBreakLogic.inside(w, kick - 60 * MIN));
        assertTrue(AdBreakLogic.inside(w, kick + 100 * MIN));
        assertFalse(AdBreakLogic.inside(w, kick - 61 * MIN));
        assertFalse(AdBreakLogic.inside(w, kick + 146 * MIN));
        assertFalse(AdBreakLogic.inside(null, kick));
        // The logo is learned from half an hour before the window opens.
        assertTrue(AdBreakLogic.watch(w, kick - 90 * MIN));
        assertFalse(AdBreakLogic.watch(w, kick - 91 * MIN));
    }

    @Test public void theTriggerFollowsTheWindow() {
        long kick = 2_000 * MIN;
        long[] w = AdBreakLogic.listedWindow(kick - 15 * MIN, kick + 105 * MIN);
        AdBreakLogic.Trigger t = new AdBreakLogic.Trigger();
        long now = kick - 20 * MIN;
        // Before the programme: nothing, however long the logo is gone.
        for (int i = 0; i < 10; i++) assertEquals(AdBreakLogic.NONE, t.step(now += 1000, true, true, true, AdBreakLogic.inside(w, now), 0.1f));
        // At half-time, inside the programme: up after six seconds.
        now = kick + 50 * MIN;
        boolean shown = false;
        for (int i = 0; i < 8; i++) shown |= t.step(now += 1000, true, true, true, AdBreakLogic.inside(w, now), 0.1f) == AdBreakLogic.SHOW;
        assertTrue(shown);
        // The programme's listed end and five minutes: away.
        now = kick + 111 * MIN;
        assertEquals(AdBreakLogic.HIDE, t.step(now, true, true, true, AdBreakLogic.inside(w, now), 0.1f));
    }

    @Test public void theLineSaysWhereTheGameIs() {
        long kick = 2_000 * MIN;
        assertEquals(AdBreakLogic.BEFORE, AdBreakLogic.phase(true, 1, false, kick, kick - 10 * MIN));
        assertEquals(AdBreakLogic.LIVE, AdBreakLogic.phase(true, 0, false, kick, kick + 10 * MIN));
        assertEquals(AdBreakLogic.HALF, AdBreakLogic.phase(true, 0, true, kick, kick + 50 * MIN));
        assertEquals(AdBreakLogic.FULL, AdBreakLogic.phase(true, 2, false, kick, kick + 120 * MIN));
        assertEquals(AdBreakLogic.GENERAL, AdBreakLogic.phase(false, 0, false, 0, kick));
        assertEquals("BEFORE KICK-OFF", AdBreakLogic.label(AdBreakLogic.BEFORE));
        assertEquals("HALF-TIME", AdBreakLogic.label(AdBreakLogic.HALF));
        assertEquals("AD BREAK", AdBreakLogic.label(AdBreakLogic.LIVE));
        assertEquals("FULL TIME", AdBreakLogic.label(AdBreakLogic.FULL));
        assertEquals("AD BREAK", AdBreakLogic.label(AdBreakLogic.GENERAL));
        assertTrue(AdBreakLogic.lineups(AdBreakLogic.BEFORE));
        assertTrue(AdBreakLogic.lineups(AdBreakLogic.HALF));
        assertFalse(AdBreakLogic.lineups(AdBreakLogic.LIVE));
        assertFalse(AdBreakLogic.lineups(AdBreakLogic.FULL));
        assertFalse(AdBreakLogic.lineups(AdBreakLogic.GENERAL));
    }

    // ---- one screen at a break ------------------------------------------------------------------------------------------

    private static final int AD = AdBreakLogic.AD, BREAK = AdBreakLogic.HALF_BREAK, CARD = AdBreakLogic.HALF_CARD, HOP = AdBreakLogic.HOP;

    @Test public void broadcastModeKeepsHalfTimeAndTheAdBreakScreenTheRest() {
        // Half-time: its break, after the half-time card and the hop as before; never the ad-break screen.
        assertEquals(BREAK | CARD | HOP, AdBreakLogic.breaks(true, true, false, false, false));
        assertEquals(0, AdBreakLogic.breaks(true, true, true, false, false) & AD);
        // The build-up, a break in play, after full time: the ad-break screen.
        assertEquals(AD | CARD | HOP, AdBreakLogic.breaks(true, false, false, false, false));
        // Its break up (the Control Centre's try): no ad-break screen over it.
        assertEquals(0, AdBreakLogic.breaks(true, false, true, false, false) & AD);
        // The ad-break screen up as half-time comes: it goes, and the break waits until it has.
        assertEquals(0, AdBreakLogic.breaks(true, true, false, true, false) & (AD | BREAK));
    }

    @Test public void withoutBroadcastModeTheAdBreakScreenCoversHalfTimeAndTheCardsGiveWay() {
        assertEquals(AD | CARD | HOP, AdBreakLogic.breaks(false, true, false, false, false));
        // Up: no half-time card, no hop.
        assertEquals(AD, AdBreakLogic.breaks(false, true, false, true, false));
        assertEquals(AD, AdBreakLogic.breaks(false, false, false, true, false));
        // Broadcast mode's break never, with it off.
        for (boolean ht : new boolean[]{false, true}) {
            for (boolean up : new boolean[]{false, true}) assertEquals(0, AdBreakLogic.breaks(false, ht, false, up, false) & BREAK);
        }
    }

    @Test public void matchModesQuietLetsOnlyItsOwnGamesScreenThrough() {
        // Its own game: never kept back.
        assertFalse(MatchMode.keepsBack(true, true, "adbreak", "g1", "g1"));
        // Another game, or none known (the general screen): kept back while its quiet is on.
        assertTrue(MatchMode.keepsBack(true, true, "adbreak", "g2", "g1"));
        assertTrue(MatchMode.keepsBack(true, true, "adbreak", null, "g1"));
        assertFalse(MatchMode.keepsBack(true, false, "adbreak", "g2", "g1"));
        assertFalse(MatchMode.keepsBack(false, true, "adbreak", "g2", "g1"));
        assertEquals(CARD | HOP, AdBreakLogic.breaks(false, false, false, false, true));
        assertEquals(BREAK | CARD | HOP, AdBreakLogic.breaks(true, true, false, false, true));
    }

    @Test public void halfTimeShowsUntilTheChannelsWaitAfterTheScoresSawTheSecondHalf() {
        long t = 2_000 * MIN;
        assertTrue(AdBreakLogic.halfTimeShows(true, 0, 0, t));
        assertTrue(AdBreakLogic.halfTimeShows(false, t, 60_000L, t + 60_000L));
        assertFalse(AdBreakLogic.halfTimeShows(false, t, 60_000L, t + 66_000L));
        assertFalse(AdBreakLogic.halfTimeShows(false, 0, 60_000L, t));
    }

    @Test public void theCountdownShowsHoursOnlyOverAnHour() {
        assertEquals("4:09", AdBreakLogic.countdown(249_000L));
        assertEquals("59:59", AdBreakLogic.countdown(3_599_000L));
        assertEquals("1:05:09", AdBreakLogic.countdown(3_909_000L));
        assertEquals("0:00", AdBreakLogic.countdown(-5_000L));
    }

    @Test public void theFeedsTitlesAreReadPlainOrInCdata() {
        String xml = "<rss><channel><title>BBC Sport - Football</title><item><title><![CDATA[Spurs sack manager]]></title></item>"
                + "<item><title>Saka &amp; Rice fit for England</title></item><item><title>Third</title></item></channel></rss>";
        List<String> t = AdBreakLogic.rssTitles(xml, 2);
        assertEquals(2, t.size());
        assertEquals("Spurs sack manager", t.get(0));
        assertEquals("Saka & Rice fit for England", t.get(1));
        assertTrue(AdBreakLogic.rssTitles(null, 3).isEmpty());
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
