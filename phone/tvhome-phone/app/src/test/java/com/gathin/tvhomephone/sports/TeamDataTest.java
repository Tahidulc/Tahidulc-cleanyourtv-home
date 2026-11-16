package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** A club's page read from ESPN's and FotMob's feeds (made-up clubs, players and figures throughout). */
public class TeamDataTest {
    private static final String CREST = "https://images.fotmob.com/image_resources/logo/teamlogo/";
    private static final String PHOTO = "https://images.fotmob.com/image_resources/playerimages/";

    private static JSONArray stats(Object... nameValue) throws Exception {
        JSONArray a = new JSONArray();
        for (int i = 0; i < nameValue.length; i += 2) a.put(new JSONObject().put("name", nameValue[i]).put("value", ((Number) nameValue[i + 1]).doubleValue()));
        return a;
    }

    private static JSONObject side(String id, String name, String where, Integer score) throws Exception {
        JSONObject s = new JSONObject().put("homeAway", where).put("team", new JSONObject().put("id", id).put("displayName", name)
                .put("shortDisplayName", name).put("logos", new JSONArray().put(new JSONObject().put("href", "https://example.invalid/" + id + ".png"))));
        if (score != null) s.put("score", new JSONObject().put("value", score.doubleValue()).put("displayValue", String.valueOf(score)));
        return s;
    }

    private static JSONObject event(String date, String homeId, String homeName, Integer hs, String awayId, String awayName, Integer as,
                                    boolean done) throws Exception {
        JSONObject status = new JSONObject().put("type", new JSONObject().put("completed", done).put("state", done ? "post" : "pre"));
        JSONObject comp = new JSONObject().put("status", status)
                .put("competitors", new JSONArray().put(side(homeId, homeName, "home", hs)).put(side(awayId, awayName, "away", as)));
        return new JSONObject().put("date", date).put("league", new JSONObject().put("abbreviation", "Lakeside League"))
                .put("competitions", new JSONArray().put(comp));
    }

    @Test public void espnRecordWithHomeAndAwayAndTheNextGame() throws Exception {
        JSONObject record = new JSONObject().put("type", "total").put("stats", stats(
                "gamesPlayed", 10, "wins", 6, "ties", 2, "losses", 2, "pointsFor", 18, "pointsAgainst", 9, "points", 20, "rank", 4,
                "homeGamesPlayed", 5, "homeWins", 4, "homeTies", 1, "homeLosses", 0, "homePointsFor", 11, "homePointsAgainst", 3,
                "awayGamesPlayed", 5, "awayWins", 2, "awayTies", 1, "awayLosses", 2, "awayPointsFor", 7, "awayPointsAgainst", 6));
        JSONObject root = new JSONObject().put("team", new JSONObject().put("id", "901").put("displayName", "Harbour City Herons")
                .put("shortDisplayName", "Herons").put("color", "1a7f4b").put("standingSummary", "4th in Lakeside League")
                .put("logos", new JSONArray().put(new JSONObject().put("href", "https://example.invalid/901.png")))
                .put("record", new JSONObject().put("items", new JSONArray().put(record)))
                .put("nextEvent", new JSONArray().put(event("2026-11-22T15:00Z", "903", "Ashcombe Rovers", null, "901", "Harbour City Herons", null, false))));
        TeamData t = new TeamData();
        t.id = "901";
        TeamData.espnTeam(t, root);
        assertEquals("Harbour City Herons", t.name);
        assertEquals("Herons", t.shortName);
        assertEquals("https://example.invalid/901.png", t.logo);
        assertEquals("4th in Lakeside League", t.standing);
        assertEquals(4, t.rank);
        assertEquals(10, t.all.played);
        assertEquals(20, t.all.points());
        assertEquals(9, t.all.difference());
        assertEquals(5, t.home.played);
        assertEquals(13, t.home.points());
        assertEquals(11, t.home.scored);
        assertEquals(7, t.away.points());
        assertEquals(6, t.away.conceded);
        assertEquals("Ashcombe Rovers", t.next.other);
        assertFalse(t.next.home);
        assertEquals(' ', t.next.result());
        assertEquals("Lakeside League", t.next.comp);
        assertEquals(0xFF1A7F4B, TeamData.colour(t.color, 0));
    }

    @Test public void resultsGiveTheFormCleanSheetsAndTheBiggestWin() throws Exception {
        JSONArray events = new JSONArray()
                .put(event("2026-10-08T15:00Z", "903", "Ashcombe Rovers", 2, "901", "Harbour City Herons", 2, true))
                .put(event("2026-10-01T15:00Z", "901", "Harbour City Herons", 3, "902", "Kingsmoor Town", 0, true))
                .put(event("2026-10-22T15:00Z", "902", "Kingsmoor Town", 1, "901", "Harbour City Herons", 4, true))
                .put(event("2026-10-15T15:00Z", "901", "Harbour City Herons", 0, "904", "Port Albany", 1, true))
                .put(event("2026-10-29T15:00Z", "901", "Harbour City Herons", null, "905", "Fenwick Vale", null, false));
        TeamData t = new TeamData();
        t.id = "901";
        TeamData.results(t, new JSONObject().put("events", events));
        assertEquals(4, t.results.size());
        assertEquals("Kingsmoor Town", t.results.get(0).other);
        assertEquals("WDLW", TeamData.form(t.results, 5));
        assertEquals("LW", TeamData.form(t.results, 2));
        assertEquals(1, TeamData.cleanSheets(t.results));
        assertEquals(1, TeamData.blanks(t.results));
        TeamData.Fixture best = TeamData.biggest(t.results, true), worst = TeamData.biggest(t.results, false);
        assertEquals(4, best.scored);
        assertEquals(1, best.conceded);
        assertFalse(best.home);
        assertEquals("Port Albany", worst.other);
        assertEquals("Kingsmoor Town", t.last.other);
        assertEquals('W', t.last.result());
        // No record from ESPN: counted from the games.
        assertEquals(4, t.all.played);
        assertEquals(7, t.all.points());
        assertEquals(9, t.all.scored);
        assertEquals(4, t.all.conceded);
        assertEquals(2, t.home.played);
        assertEquals(3, t.home.points());
        assertEquals(6, t.away.scored);
        assertNull(TeamData.biggest(new ArrayList<>(), true));
    }

    private static JSONObject teamStat(String header, String key, String format, int fractions, double value, int rank) throws Exception {
        JSONObject stat = new JSONObject().put("name", key).put("format", format).put("fractions", fractions).put("value", value);
        return new JSONObject().put("header", header)
                .put("participant", new JSONObject().put("teamId", 5001).put("value", value).put("rank", rank).put("stat", stat));
    }

    private static JSONObject board(String header, String key, String format, Object[][] top) throws Exception {
        JSONArray three = new JSONArray();
        for (Object[] p : top) {
            three.put(new JSONObject().put("id", p[0]).put("name", p[1]).put("value", p[2])
                    .put("stat", new JSONObject().put("name", key).put("format", format).put("fractions", "kph".equals(format) ? 1 : 0)));
        }
        return new JSONObject().put("header", header).put("topThree", three);
    }

    private static JSONObject member(int id, String name, Integer shirt, String ccode, String cname, int age) throws Exception {
        JSONObject m = new JSONObject().put("id", id).put("name", name).put("ccode", ccode).put("cname", cname).put("age", age);
        if (shirt != null) m.put("shirtNumber", shirt);
        return m;
    }

    private static JSONObject fmMatch(int homeId, String home, int homeScore, int awayId, String away, int awayScore, boolean finished,
                                      String utc) throws Exception {
        return new JSONObject().put("home", new JSONObject().put("id", homeId).put("name", home).put("score", homeScore))
                .put("away", new JSONObject().put("id", awayId).put("name", away).put("score", awayScore))
                .put("tournament", new JSONObject().put("name", "Lakeside League"))
                .put("status", new JSONObject().put("utcTime", utc).put("finished", finished).put("cancelled", false));
    }

    private static JSONObject fotmobClub() throws Exception {
        JSONObject varley = member(7003, "Callum Varley", 9, "SCO", "Scotland", 24).put("goals", 7).put("assists", 3).put("ycards", 2)
                .put("rating", 7.42).put("injury", new JSONObject().put("id", "12").put("expectedReturn", "Late December 2026"));
        JSONArray squad = new JSONArray()
                .put(new JSONObject().put("title", "coach").put("members", new JSONArray().put(member(7001, "Elias Brandt", null, "NOR", "Norway", 51))))
                .put(new JSONObject().put("title", "keepers").put("members", new JSONArray()
                        .put(member(7002, "Tobias Ashby", 1, "WAL", "Wales", 27).put("rating", 6.8).put("injury", JSONObject.NULL))))
                .put(new JSONObject().put("title", "attackers").put("members", new JSONArray().put(varley)))
                .put(new JSONObject().put("title", "midfielders").put("members", new JSONArray()));
        JSONObject stats = new JSONObject()
                .put("teams", new JSONArray()
                        .put(teamStat("Average possession", "possession_percentage_team", "percent", 1, 54.9, 6))
                        .put(teamStat("Expected goals", "expected_goals_team", "fraction", 1, 17.6, 4))
                        .put(teamStat("Yellow cards", "total_yel_card_team", "number", 0, 21, 8))
                        .put(teamStat("Distance per match", "phys_tdc_team", "meter", 1, 112524.2, 19))
                        .put(teamStat("Throw-ins won", "brand_new_team", "number", 0, 3, 12)))
                .put("players", new JSONArray()
                        .put(board("Top scorer", "goals", "number", new Object[][]{{7003, "Callum Varley", 7}, {7004, "Rhys Lumley", 4}}))
                        .put(board("Top speed", "phys_ts", "kph", new Object[][]{{7005, "Iwan Kettering", 34.62}})));
        JSONObject overview = new JSONObject()
                .put("nextMatch", fmMatch(5001, "Harbour City Herons", 0, 5003, "Ashcombe Rovers", 0, false, "2026-11-22T15:00:00.000Z"))
                .put("lastMatch", fmMatch(5004, "Port Albany", 1, 5001, "Harbour City Herons", 4, true, "2026-11-08T15:00:00.000Z"))
                .put("teamForm", new JSONArray().put(new JSONObject().put("resultString", "W")).put(new JSONObject().put("resultString", "D"))
                        .put(new JSONObject().put("resultString", "L")))
                .put("venue", new JSONObject().put("widget", new JSONObject().put("name", "Quayside Park").put("city", "Harbour City"))
                        .put("statPairs", new JSONArray().put(new JSONArray().put("Surface").put("Grass")).put(new JSONArray().put("Capacity").put(31250))));
        return new JSONObject().put("details", new JSONObject().put("id", 5001).put("name", "Harbour City Herons"))
                .put("squad", new JSONObject().put("squad", squad)).put("stats", stats).put("overview", overview);
    }

    @Test public void fotmobGivesTheSquadFiguresLeadersAndGames() throws Exception {
        TeamData t = new TeamData();
        t.id = "901";
        TeamData.fotmob(t, fotmobClub());
        assertTrue(t.fotmob);
        assertEquals("FotMob", t.squadFrom);
        assertEquals(3, t.squad.size());
        assertEquals("Manager", t.squad.get(0).title);
        assertEquals("Goalkeepers", t.squad.get(1).title);
        assertEquals("Forwards", t.squad.get(2).title);
        TeamData.Player boss = t.squad.get(0).players.get(0);
        assertEquals("Elias Brandt", boss.name);
        assertEquals("", boss.number);
        assertEquals(CREST + "nor.png", boss.flag);
        assertEquals(PHOTO + "7001.png", boss.photo);
        TeamData.Player keeper = t.squad.get(1).players.get(0);
        assertEquals("1", keeper.number);
        assertEquals("", keeper.injury);
        assertEquals(27, keeper.age);
        TeamData.Player striker = t.squad.get(2).players.get(0);
        assertEquals(7, striker.goals);
        assertEquals(3, striker.assists);
        assertEquals(2, striker.yellow);
        assertEquals(7.42, striker.rating, 1e-9);
        assertEquals("Late December 2026", striker.injury);
        assertEquals("Scotland", striker.country);

        assertEquals(5, t.figures.size());
        assertEquals("54.9%", t.figures.get(0).value);
        assertEquals(6, t.figures.get(0).rank);
        assertEquals("17.6", t.figures.get(1).value);
        assertEquals("21", t.figures.get(2).value);
        assertEquals("112.5 km", t.figures.get(3).value);
        Map<String, List<TeamData.Figure>> s = TeamData.sections(t.figures);
        assertEquals(Arrays.asList("Attack", "Possession", "Discipline", "More"), new ArrayList<>(s.keySet()));
        assertEquals("Distance per match", s.get("More").get(0).name);
        assertEquals("Throw-ins won", s.get("More").get(1).name);

        assertEquals(2, t.boards.size());
        assertEquals("goals", t.boards.get(0).key);
        assertEquals("Callum Varley", t.boards.get(0).top.get(0).name);
        assertEquals("7", t.boards.get(0).top.get(0).value);
        assertEquals(PHOTO + "7003.png", t.boards.get(0).top.get(0).photo);
        assertEquals("34.6 km/h", t.boards.get(1).top.get(0).value);

        assertEquals("Ashcombe Rovers", t.next.other);
        assertTrue(t.next.home);
        assertEquals(CREST + "5003.png", t.next.otherLogo);
        assertEquals("Port Albany", t.last.other);
        assertFalse(t.last.home);
        assertEquals('W', t.last.result());
        assertEquals(4, t.last.scored);
        assertEquals("Lakeside League", t.last.comp);
        assertEquals("WDL", t.formEverywhere);
        assertEquals("Quayside Park, Harbour City · 31,250", t.venue);
    }

    @Test public void espnSquadByPositionWithTheManagerFirst() throws Exception {
        JSONArray athletes = new JSONArray()
                .put(new JSONObject().put("displayName", "Callum Varley").put("jersey", "9").put("age", 24).put("citizenship", "Scotland")
                        .put("position", new JSONObject().put("displayName", "Forward")))
                .put(new JSONObject().put("displayName", "Tobias Ashby").put("jersey", "1").put("age", 27).put("citizenship", "Wales")
                        .put("flag", new JSONObject().put("href", "https://example.invalid/wal.png"))
                        .put("headshot", JSONObject.NULL).put("position", new JSONObject().put("displayName", "Goalkeeper")))
                .put(new JSONObject().put("displayName", "Rhys Lumley").put("jersey", "4").put("position", new JSONObject().put("displayName", "Defender")))
                .put(new JSONObject().put("displayName", "Iwan Kettering").put("jersey", "8").put("position", new JSONObject().put("displayName", "Midfielder")));
        JSONObject root = new JSONObject().put("athletes", athletes)
                .put("coach", new JSONArray().put(new JSONObject().put("firstName", "Elias").put("lastName", "Brandt")));
        List<TeamData.Group> g = TeamData.espnSquad(root);
        List<String> titles = new ArrayList<>();
        for (TeamData.Group x : g) titles.add(x.title);
        assertEquals(Arrays.asList("Manager", "Goalkeepers", "Defenders", "Midfielders", "Forwards"), titles);
        assertEquals("Elias Brandt", g.get(0).players.get(0).name);
        TeamData.Player keeper = g.get(1).players.get(0);
        assertEquals("https://example.invalid/wal.png", keeper.flag);
        assertEquals("", keeper.photo);
        assertEquals("Wales", keeper.country);
        assertEquals("1", keeper.number);
    }

    @Test public void valuesAsFotMobShowsThem() {
        assertEquals("6.91", TeamData.value(6.912, "fraction", 2));
        assertEquals("54.9%", TeamData.value(54.94, "percent", 1));
        assertEquals("112.5 km", TeamData.value(112524.2, "meter", 1));
        assertEquals("35.4 km/h", TeamData.value(35.42, "kph", 1));
        assertEquals("74,146", TeamData.value(74146, "number", 0));
        assertEquals("-0.3", TeamData.value(-0.3, "fraction", 1));
        assertEquals("", TeamData.value(Double.NaN, "number", 0));
    }

    @Test public void smallWords() {
        assertEquals("1st", TeamData.ordinal(1));
        assertEquals("2nd", TeamData.ordinal(2));
        assertEquals("3rd", TeamData.ordinal(3));
        assertEquals("11th", TeamData.ordinal(11));
        assertEquals("13th", TeamData.ordinal(13));
        assertEquals("21st", TeamData.ordinal(21));
        assertEquals("112th", TeamData.ordinal(112));
        assertEquals("TA", TeamData.initials("Tobias Ashby"));
        assertEquals("EV", TeamData.initials("Elias van Varley"));
        assertEquals("K", TeamData.initials("Kettering"));
        assertEquals("", TeamData.initials("  "));
        assertEquals("Injured · back in a few days", TeamData.injuryLine("A few days"));
        assertEquals("Injured · back late December 2026", TeamData.injuryLine("Late December 2026"));
        assertEquals("Injured", TeamData.injuryLine(""));
        assertEquals("Injured · return not known", TeamData.injuryLine("Unknown"));
        assertEquals(0xFF2A4FD0, TeamData.colour("#2a4fd0", 0));
        assertEquals(5, TeamData.colour("blue", 5));
        assertEquals(CREST + "eng.png", TeamData.flag("ENG"));
        assertEquals("", TeamData.flag(""));
        assertEquals("Forwards", TeamData.lineTitle("attackers"));
    }

    @Test public void theLeadersListsFirstThenAll() {
        List<TeamData.Board> boards = new ArrayList<>();
        for (String k : new String[]{"mins_played", "goals", "throw_ins", "goal_assist"}) boards.add(new TeamData.Board(k, k));
        List<String> first = new ArrayList<>(), all = new ArrayList<>();
        for (TeamData.Board b : TeamData.shown(boards, false)) first.add(b.key);
        for (TeamData.Board b : TeamData.shown(boards, true)) all.add(b.key);
        assertEquals(Arrays.asList("goals", "goal_assist", "mins_played"), first);
        assertEquals(Arrays.asList("goals", "goal_assist", "mins_played", "throw_ins"), all);
    }
}
