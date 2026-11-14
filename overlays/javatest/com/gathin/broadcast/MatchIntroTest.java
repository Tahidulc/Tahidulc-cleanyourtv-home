package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** The match intro's settings (MatchIntro): names, ids, the squad's best-known players, the day's other games. */
public class MatchIntroTest {

    @Test
    public void clubsAreWrittenAsTheIntroWritesThem() {
        assertEquals("NEWCASTLE", MatchIntro.clubWord("Newcastle United"));
        assertEquals("MAN CITY", MatchIntro.clubWord("Manchester City"));
        assertEquals("MAN UTD", MatchIntro.clubWord("Manchester United"));
        assertEquals("SPURS", MatchIntro.clubWord("Tottenham Hotspur"));
        assertEquals("COVENTRY", MatchIntro.clubWord("Coventry City"));
        assertEquals("ARSENAL", MatchIntro.clubWord("Arsenal"));
        assertEquals("BARCELONA", MatchIntro.clubWord("FC Barcelona"));
        assertEquals("PSG", MatchIntro.clubWord("Paris Saint-Germain"));
        assertEquals("HULL", MatchIntro.clubWord("Hull City"));
        // a name too short alone keeps its last word
        assertEquals("AYR UNITED", MatchIntro.clubWord("Ayr United"));
        assertEquals("MCI", MatchIntro.abbrOf("MNC", "Manchester City"));
        assertEquals("MUN", MatchIntro.abbrOf("MAN", "Manchester United"));
        assertEquals("NEW", MatchIntro.abbrOf("NEW", "Newcastle United"));
        assertEquals("ARS", MatchIntro.abbrOf("", "Arsenal"));
    }

    @Test
    public void idsAndTheCompetitionAreFound() {
        assertEquals("361", MatchIntro.espnId("https://a.espncdn.com/i/teamlogos/soccer/500/361.png"));
        assertEquals("", MatchIntro.espnId("https://images.fotmob.com/image_resources/logo/teamlogo/10261.png"));
        assertEquals("", MatchIntro.espnId(null));
        assertEquals("eng.1", MatchIntro.slug("eng.1", "Premier League"));
        assertEquals("uefa.champions", MatchIntro.slug("42", "Champions League"));
        assertEquals("esp.1", MatchIntro.slug("", "LaLiga"));
        assertEquals("", MatchIntro.slug("", "Scottish Cup"));
    }

    @Test
    public void namesAreSplitAsTheCardWritesThem() {
        assertArrayEquals(new String[]{"HARVEY", "BARNES"}, MatchIntro.nameLines("Harvey Barnes", "Barnes"));
        // known by one name: nothing above it
        assertArrayEquals(new String[]{"", "GABRIEL"}, MatchIntro.nameLines("Gabriel Magalhães", "Gabriel"));
        assertArrayEquals(new String[]{"", "JOELINTON"}, MatchIntro.nameLines("Joelinton", ""));
        // the game's initialled name is not used ("J.Murphy")
        assertArrayEquals(new String[]{"JACOB", "MURPHY"}, MatchIntro.nameLines("Jacob Murphy", "J.Murphy"));
        assertArrayEquals(new String[]{"RASMUS", "HØJLUND"}, MatchIntro.nameLines("Rasmus Højlund", ""));
    }

    private static JSONObject member(long id, String name, int num, String pos, double value, String role, boolean injured) throws Exception {
        JSONObject m = new JSONObject().put("id", id).put("name", name).put("shirtNumber", num).put("positionIdsDesc", pos)
                .put("transferValue", value).put("role", new JSONObject().put("key", role));
        m.put("injury", injured ? new JSONObject().put("type", "knee") : JSONObject.NULL);
        return m;
    }

    @Test
    public void theSquadsBestKnownComeFirst() throws Exception {
        JSONArray keepers = new JSONArray().put(member(1, "Big Keeper", 1, "GK", 60_000_000, "keeper_long", false));
        JSONArray att = new JSONArray().put(member(9, "Star Striker", 9, "ST,CAM", 50_000_000, "attacker_long", false))
                .put(member(11, "Hurt Winger", 11, "LW", 90_000_000, "attacker_long", true))
                .put(member(7, "Quick Winger", 7, "RW", 30_000_000, "attacker_long", false));
        JSONArray coach = new JSONArray().put(new JSONObject().put("id", 99).put("name", "The Coach"));
        JSONObject team = new JSONObject().put("squad", new JSONObject().put("squad", new JSONArray()
                .put(new JSONObject().put("title", "coach").put("members", coach))
                .put(new JSONObject().put("title", "keepers").put("members", keepers))
                .put(new JSONObject().put("title", "attackers").put("members", att))));
        List<MatchIntro.Member> r = MatchIntro.ranked(team, new HashSet<>());
        // the injured and the coach left out; the keeper at a third of his value
        assertEquals(Arrays.asList("Star Striker", "Quick Winger", "Big Keeper"), Arrays.asList(r.get(0).name, r.get(1).name, r.get(2).name));
        assertEquals("ST", r.get(0).pos);
        assertEquals(9, r.get(0).num);
        // in the line-ups' hour only the starting eleven
        List<MatchIntro.Member> xi = MatchIntro.ranked(team, new HashSet<>(Arrays.asList(7L, 1L)));
        assertEquals(2, xi.size());
        assertEquals("Quick Winger", xi.get(0).name);
    }

    @Test
    public void theLineupsAndTheClubsIdsAreReadFromTheMatchDetails() throws Exception {
        JSONArray st = new JSONArray();
        for (int i = 1; i <= 11; i++) st.put(new JSONObject().put("id", 100 + i));
        JSONObject d = new JSONObject()
                .put("general", new JSONObject().put("homeTeam", new JSONObject().put("id", 10261)).put("awayTeam", new JSONObject().put("id", 9825)))
                .put("content", new JSONObject().put("lineup", new JSONObject().put("homeTeam", new JSONObject().put("starters", st)))
                        .put("matchFacts", new JSONObject().put("infoBox", new JSONObject().put("Stadium", new JSONObject().put("name", "St. James' Park")))));
        assertArrayEquals(new long[]{10261, 9825}, MatchIntro.teamIds(d));
        assertEquals(11, MatchIntro.starters(d, "homeTeam").size());
        assertTrue(MatchIntro.starters(d, "awayTeam").isEmpty());
        assertEquals("St. James' Park", MatchIntro.groundOf(d, "Elsewhere"));
        assertEquals("Elsewhere", MatchIntro.groundOf(null, "Elsewhere"));
        assertArrayEquals(new long[]{0, 0}, MatchIntro.teamIds(null));
    }

    private static JSONObject event(String date, String home, String away) throws Exception {
        JSONArray cs = new JSONArray()
                .put(new JSONObject().put("homeAway", "home").put("team", new JSONObject().put("id", "1").put("displayName", home)
                        .put("abbreviation", home.substring(0, 3).toUpperCase()).put("color", "ff0000").put("logo", "https://a.espncdn.com/i/teamlogos/soccer/500/1.png")))
                .put(new JSONObject().put("homeAway", "away").put("team", new JSONObject().put("id", "2").put("displayName", away)
                        .put("abbreviation", away.substring(0, 3).toUpperCase()).put("logo", "https://a.espncdn.com/i/teamlogos/soccer/500/2.png")));
        return new JSONObject().put("date", date).put("competitions", new JSONArray().put(new JSONObject().put("competitors", cs)));
    }

    @Test
    public void theDaysOtherGamesComeNearestFirstWithoutTheGameItself() throws Exception {
        long ko = java.time.Instant.parse("2026-11-21T17:30:00Z").toEpochMilli();
        JSONObject board = new JSONObject().put("events", new JSONArray()
                .put(event("2026-11-21T15:00Z", "Chelsea", "Leeds United"))
                .put(event("2026-11-21T17:30Z", "Newcastle United", "Arsenal"))
                .put(event("2026-11-21T12:30Z", "Manchester City", "Fulham"))
                .put(event("2026-11-25T20:00Z", "Everton", "Brentford")));
        JSONArray g = MatchIntro.others(board, ko, "Newcastle United", "Arsenal");
        assertEquals(2, g.length());
        assertEquals("CHELSEA", g.getJSONArray(0).getJSONObject(0).getString("name"));
        assertEquals("LEEDS", g.getJSONArray(0).getJSONObject(1).getString("name"));
        assertEquals("#ff0000", g.getJSONArray(0).getJSONObject(0).getString("color"));
        assertEquals("MAN CITY", g.getJSONArray(1).getJSONObject(0).getString("name"));
    }
}
