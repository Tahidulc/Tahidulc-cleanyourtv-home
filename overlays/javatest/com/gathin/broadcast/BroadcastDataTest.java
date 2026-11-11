package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/** The live overlays' data: competitions, colours, minutes, scores, and FotMob's line-ups as the page reads them. */
public class BroadcastDataTest {
    @Test
    public void theSixCompetitionsAreKnownByTheirFeedsIdsOrNames() {
        assertEquals("pl", BroadcastData.compKey("eng.1", ""));
        assertEquals("pl", BroadcastData.compKey("47", null));
        assertEquals("facup", BroadcastData.compKey("eng.fa", ""));
        assertEquals("leaguecup", BroadcastData.compKey("eng.league_cup", ""));
        assertEquals("ucl", BroadcastData.compKey("uefa.champions", ""));
        assertEquals("uel", BroadcastData.compKey("uefa.europa", ""));
        assertEquals("uecl", BroadcastData.compKey("uefa.europa.conf", ""));
        assertEquals("uecl", BroadcastData.compKey("", "UEFA Europa Conference League"));
        assertEquals("leaguecup", BroadcastData.compKey("", "Carabao Cup"));
        assertEquals("pl", BroadcastData.compKey("", "English Premier League"));
        // others are a plain cup with their own name: never another league's trophy
        assertEquals("", BroadcastData.compKey("sco.1", "Scottish Premiership"));
        assertEquals("", BroadcastData.compKey("", "Premier League 2"));
        assertEquals("", BroadcastData.compKey("", "AFC Champions League Elite"));
    }

    @Test
    public void theTabNamesTheCompetitionAsItIsKnown() {
        assertEquals("Carabao Cup", BroadcastData.compName("leaguecup", "League Cup"));
        assertEquals("Premier League", BroadcastData.compName("pl", "English Premier League"));
        assertEquals("Bundesliga", BroadcastData.compName("", "Bundesliga"));
        assertEquals("", BroadcastData.compName("", null));
    }

    @Test
    public void coloursMinutesAndScoresAreWrittenAsThePageReadsThem() {
        assertEquals("#ef0107", BroadcastData.hex(0xFFEF0107));
        assertNull(BroadcastData.hex(0));
        assertEquals("23'", BroadcastData.minute("23"));
        assertEquals("45'+2'", BroadcastData.minute("45'+2'"));
        assertEquals("90'+1'", BroadcastData.minute("90’+1’"));
        assertEquals("", BroadcastData.minute(null));
        assertArrayEquals(new int[]{2, 1}, BroadcastData.score("2–1"));
        assertArrayEquals(new int[]{0, 0}, BroadcastData.score("0 - 0"));
        assertNull(BroadcastData.score("v"));
        assertEquals("MUN", BroadcastData.shortOf("MUN", "Manchester United"));
        assertEquals("HUL", BroadcastData.shortOf(null, "Hull City"));
    }

    @Test
    public void floodlightsFromFiveInWinterAndSevenInSummer() {
        ZoneId z = ZoneId.of("Europe/London");
        assertFalse(BroadcastData.night(ZonedDateTime.of(2026, 11, 7, 15, 0, 0, 0, z)));
        assertTrue(BroadcastData.night(ZonedDateTime.of(2026, 11, 7, 17, 30, 0, 0, z)));
        assertFalse(BroadcastData.night(ZonedDateTime.of(2026, 8, 16, 17, 30, 0, 0, z)));
        assertTrue(BroadcastData.night(ZonedDateTime.of(2026, 8, 16, 20, 0, 0, 0, z)));
    }

    private static JSONObject player(int id, String name, String last, int pos, int usual, double x, double y, boolean cap) throws Exception {
        JSONObject p = new JSONObject().put("id", id).put("name", name).put("shirtNumber", String.valueOf(id % 40))
                .put("positionId", pos).put("usualPlayingPositionId", usual).put("isCaptain", cap)
                .put("verticalLayout", new JSONObject().put("x", x).put("y", y));
        if (last != null) p.put("lastName", last);
        return p;
    }

    @Test
    public void fotmobsLineUpIsTurnedRoundSoTheRightBackIsOnTheLeft() throws Exception {
        JSONArray st = new JSONArray()
                .put(player(1, "Senne Lammens", "Lammens", 11, 0, 0.5, 0.1, false))
                .put(player(3, "Noussair Mazraoui", "Mazraoui", 32, 1, 0.125, 0.292, false))
                .put(player(2, "Diogo Dalot", null, 38, 1, 0.875, 0.292, true));
        JSONArray subs = new JSONArray().put(new JSONObject().put("id", 26).put("name", "Altay Bayindir").put("shirtNumber", "1")
                .put("usualPlayingPositionId", 0));
        JSONObject t = BroadcastData.team(new JSONObject().put("name", "Manchester United").put("formation", "4-2-3-1")
                .put("starters", st).put("subs", subs).put("coach", new JSONObject().put("name", "Ruben Amorim")));
        assertEquals("4-2-3-1", t.getString("formation"));
        JSONArray xi = t.getJSONArray("xi");
        assertTrue(xi.getJSONObject(0).getBoolean("gk"));
        assertEquals(0.5, xi.getJSONObject(0).getDouble("x"), 1e-9);
        assertEquals(0.1, xi.getJSONObject(0).getDouble("y"), 1e-9);
        // the right-back (FotMob's x 0.125) near 1, the left-back near 0
        assertEquals(0.875, xi.getJSONObject(1).getDouble("x"), 1e-9);
        assertEquals(0.125, xi.getJSONObject(2).getDouble("x"), 1e-9);
        assertEquals(32, xi.getJSONObject(1).getInt("pos"));
        assertTrue(xi.getJSONObject(2).getBoolean("cap"));
        assertEquals("Dalot", xi.getJSONObject(2).getString("last"));   // no lastName: the name's last word
        JSONObject sub = t.getJSONArray("subs").getJSONObject(0);
        assertTrue(sub.getBoolean("gk"));
        assertFalse(sub.has("x"));
    }

    @Test
    public void noLineUpsUntilBothElevensAreThere() throws Exception {
        JSONObject d = new JSONObject().put("content", new JSONObject().put("lineup", new JSONObject()
                .put("homeTeam", new JSONObject().put("starters", new JSONArray()))
                .put("awayTeam", new JSONObject().put("starters", new JSONArray()))));
        assertNull(BroadcastData.lineups(new BroadcastData.Match(), d, null));
        assertNull(BroadcastData.lineups(new BroadcastData.Match(), null, null));
    }

    @Test
    public void theStrapCarriesTheBoardsLookAndPlaceAndTheRefereeIsReadFromTheInfoBox() throws Exception {
        BroadcastData.Match m = new BroadcastData.Match();
        m.homeName = "Arsenal"; m.awayName = "Everton"; m.homeShort = "ARS"; m.awayShort = "EVE"; m.compId = "eng.1"; m.venue = "Emirates Stadium";
        org.json.JSONObject s = BroadcastData.strap("kickoff", m, null, null, null, "FROSTED", new float[]{64, 46, 380, 49}, "9° · light rain");
        assertEquals("FROSTED", s.getString("look"));
        assertEquals(380, s.getJSONObject("board").getInt("w"));
        assertEquals("9° · light rain", s.getString("weather"));
        assertEquals("pl", s.getString("comp"));
        assertEquals("Michael Oliver", BroadcastData.referee(new org.json.JSONObject("{\"Referee\":{\"text\":\" Michael Oliver \"}}")));
        assertEquals("", BroadcastData.referee(null));
    }

    @Test
    public void introTrophyCoversTheLeaguesItHas() {
        assertEquals("pl", BroadcastData.introTrophy("eng.1", "Premier League"));
        assertEquals("ucl", BroadcastData.introTrophy("uefa.champions", ""));
        assertEquals("laliga", BroadcastData.introTrophy("esp.1", "LALIGA"));
        assertEquals("laliga", BroadcastData.introTrophy("", "Spanish LaLiga"));
        assertEquals("seriea", BroadcastData.introTrophy("ita.1", ""));
        assertEquals("bundesliga", BroadcastData.introTrophy("", "German Bundesliga"));
        assertEquals("ligue1", BroadcastData.introTrophy("fra.1", "French Ligue 1"));
        assertEquals("", BroadcastData.introTrophy("esp.2", "LaLiga 2"));
        assertEquals("", BroadcastData.introTrophy("ned.1", "Eredivisie"));
    }

    @Test
    public void introSettingsCarryTheTeamsTrophyAndFilm() throws Exception {
        BroadcastData.Match m = new BroadcastData.Match();
        m.homeName = "Atlético Madrid"; m.awayName = "Barcelona"; m.compId = "esp.1"; m.compName = "LALIGA";
        org.json.JSONObject o = BroadcastData.intro(m, true);
        assertEquals("laliga", o.getString("trophy"));
        assertEquals("Atlético Madrid", o.getJSONObject("home").getString("name"));
        assertEquals("Barcelona", o.getJSONObject("away").getString("name"));
        assertEquals("footage/intro-footage.mp4", o.getString("footage"));
        assertFalse(o.has("footageFallback"));
        assertFalse(BroadcastData.intro(m, false).has("footage"));
        org.json.JSONObject own = BroadcastData.intro(m, "file:///data/x/own.mp4", 15.0);
        assertEquals("file:///data/x/own.mp4", own.getString("footage"));
        assertEquals("footage/intro-footage.mp4", own.getString("footageFallback"));
        assertEquals(15.0, own.getDouble("startAt"), 0);
    }
}
