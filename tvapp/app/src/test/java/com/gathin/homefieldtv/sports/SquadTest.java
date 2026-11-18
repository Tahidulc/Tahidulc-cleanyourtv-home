package com.gathin.homefieldtv.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/**
 * A club's page's data, from invented answers in the sources' shapes: FotMob's squad by line with the manager first,
 * ESPN's roster when FotMob can't be read (its list of past managers left out), FotMob's search to the senior club, a
 * player's and a manager's season with nothing made up, the names split for the big surname, the flags and colours, and
 * a table's full names and its clubs' colours.
 */
public class SquadTest {
    private static final String FOTMOB_TEAM = "{\"details\":{\"name\":\"Harbour Town\",\"teamColor\":\"#1E3FA8\"},\"squad\":{\"squad\":["
            + "{\"title\":\"coach\",\"members\":[{\"id\":501,\"name\":\"Erik Holmgren\",\"ccode\":\"NOR\",\"cname\":\"Norway\",\"age\":51,"
            + "\"role\":{\"key\":\"coach\",\"fallback\":\"Coach\"}}]},"
            + "{\"title\":\"keepers\",\"members\":[{\"id\":601,\"name\":\"Tom Waverley\",\"shirtNumber\":1,\"ccode\":\"ENG\",\"cname\":\"England\","
            + "\"age\":29,\"positionId\":0,\"positionIdsDesc\":\"GK\",\"role\":{\"key\":\"keeper_long\"},\"rating\":7.02,\"goals\":0,"
            + "\"assists\":0,\"ycards\":1,\"rcards\":0}]},"
            + "{\"title\":\"defenders\",\"members\":[{\"id\":602,\"name\":\"Ruben de Vlaar\",\"shirtNumber\":5,\"ccode\":\"NED\","
            + "\"cname\":\"Netherlands\",\"age\":25,\"positionId\":1,\"positionIdsDesc\":\"CB,RB\",\"role\":{\"key\":\"defender_long\"},"
            + "\"goals\":1,\"assists\":0,\"ycards\":2,\"rcards\":0,\"rating\":null}]},"
            + "{\"title\":\"midfielders\",\"members\":[{\"id\":604,\"name\":\"Rafael\",\"shirtNumber\":null,\"ccode\":\"POR\",\"cname\":\"Portugal\","
            + "\"positionIdsDesc\":\"CAM\",\"role\":{\"key\":\"midfielder_long\"}},{\"id\":603,\"name\":\"Alfie Brennan\",\"shirtNumber\":8,"
            + "\"ccode\":\"ENG\",\"cname\":\"England\",\"age\":24,\"positionId\":2,\"positionIdsDesc\":\"CM,CAM\",\"role\":{\"key\":\"midfielder_long\"},"
            + "\"rating\":7.414,\"goals\":6,\"assists\":4,\"ycards\":2,\"rcards\":0}]},"
            + "{\"title\":\"attackers\",\"members\":[{\"id\":605,\"name\":\"Kai Thorsen\",\"shirtNumber\":9,\"ccode\":\"NOR\",\"cname\":\"Norway\","
            + "\"age\":27,\"positionIdsDesc\":\"ST\",\"role\":{\"key\":\"attacker_long\"}}]}]}}";

    @Test public void fotmobsSquadByLineTheManagerFirst() throws Exception {
        Squad s = Squad.fotmob(new JSONObject(FOTMOB_TEAM));
        assertEquals("Harbour Town", s.name);
        assertEquals(0xFF1E3FA8, s.colour);
        assertEquals(1, s.staff.size());
        Squad.Person boss = s.staff.get(0);
        assertEquals("Manager", boss.position);
        assertEquals(Squad.STAFF, boss.line);
        assertEquals("", boss.number);
        assertEquals("Norway", boss.country);
        assertEquals(51, boss.age);
        assertTrue(boss.figures.isEmpty());

        assertEquals(5, s.players.size());
        Squad.Person gk = s.players.get(0);
        assertEquals(Squad.GK, gk.line);
        assertEquals("1", gk.number);
        assertEquals("Goalkeeper", gk.position);
        assertEquals("601", gk.id);
        assertTrue(gk.fotmob);
        assertTrue(gk.face);
        assertEquals("https://images.fotmob.com/image_resources/playerimages/601.png", gk.photo);
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/eng.png", gk.flag);

        Squad.Person def = s.players.get(1);
        assertEquals("Ruben", def.first);
        assertEquals("de Vlaar", def.last);
        assertEquals("Centre-back", def.position);
        // A rating FotMob gives as null is left out, never shown as 0.
        for (String[] f : def.figures) assertFalse("Rating".equals(f[0]));

        // The midfielders by number, the one with none last; a single name is all surname.
        Squad.Person mid = s.players.get(2), single = s.players.get(3);
        assertEquals("Alfie Brennan", mid.name);
        assertEquals("Central midfielder", mid.position);
        assertEquals(24, mid.age);
        assertArrayEquals(new String[]{"Goals", "6"}, mid.figures.get(0));
        assertArrayEquals(new String[]{"Assists", "4"}, mid.figures.get(1));
        assertArrayEquals(new String[]{"Yellow cards", "2"}, mid.figures.get(2));
        assertArrayEquals(new String[]{"Red cards", "0"}, mid.figures.get(3));
        assertArrayEquals(new String[]{"Rating", "7.41"}, mid.figures.get(4));
        assertEquals("Rafael", single.name);
        assertEquals("", single.first);
        assertEquals("Rafael", single.last);
        assertEquals("", single.number);
        assertEquals(-1, single.age);
        assertEquals("Attacking midfielder", single.position);

        assertEquals(Squad.FWD, s.players.get(4).line);
        assertEquals("Striker", s.players.get(4).position);

        // What the pills show.
        assertEquals(1, s.shown(false, Squad.DEF).size());
        assertEquals(2, s.shown(false, Squad.MID).size());
        assertEquals(5, s.shown(false, -1).size());
        assertEquals(1, s.shown(true, -1).size());
    }

    @Test public void aSecondCoachIsNotTheManager() throws Exception {
        String two = "{\"squad\":{\"squad\":[{\"title\":\"coach\",\"members\":[{\"id\":1,\"name\":\"Ann Lead\",\"role\":{\"key\":\"coach\"}},"
                + "{\"id\":2,\"name\":\"Ben Second\",\"role\":{\"key\":\"coach\"}}]},{\"title\":\"staff\",\"members\":[{\"id\":3,"
                + "\"name\":\"Cal Keeping\",\"role\":{\"key\":\"gk_coach\",\"fallback\":\"Goalkeeping coach\"}}]}]}}";
        List<Squad.Person> staff = Squad.fotmob(new JSONObject(two)).staff;
        assertEquals(3, staff.size());
        assertEquals("Manager", staff.get(0).position);
        assertEquals("Coach", staff.get(1).position);
        assertEquals(Squad.STAFF, staff.get(2).line);
        assertEquals("Goalkeeping coach", staff.get(2).position);
    }

    @Test public void espnsRosterWithoutItsPastManagers() throws Exception {
        String roster = "{\"team\":{\"displayName\":\"Riverside\",\"color\":\"c8102e\"},\"coach\":[{\"id\":\"1\",\"firstName\":\"Old\","
                + "\"lastName\":\"Boss\"}],\"athletes\":[{\"id\":\"9001\",\"firstName\":\"Sol\",\"lastName\":\"Ibe\",\"displayName\":\"Sol Ibe\","
                + "\"jersey\":\"11\",\"age\":22,\"citizenship\":\"Ghana\",\"citizenshipCountry\":{\"abbreviation\":\"GHA\"},"
                + "\"flag\":{\"href\":\"https://a.espncdn.com/i/teamlogos/countries/500/gha.png\"},\"headshot\":{\"href\":"
                + "\"https://a.espncdn.com/i/headshots/soccer/players/full/9001.png\"},\"position\":{\"name\":\"Forward\",\"abbreviation\":\"F\"},"
                + "\"statistics\":{\"splits\":{\"categories\":[{\"name\":\"general\",\"stats\":[{\"name\":\"appearances\",\"displayValue\":\"9\"},"
                + "{\"name\":\"yellowCards\",\"displayValue\":\"1\"},{\"name\":\"redCards\",\"displayValue\":\"0\"}]},{\"name\":\"offensive\","
                + "\"stats\":[{\"name\":\"totalGoals\",\"displayValue\":\"4\"},{\"name\":\"goalAssists\",\"displayValue\":\"2\"}]}]}}},"
                + "{\"id\":\"9002\",\"firstName\":\"Zé Carlos\",\"lastName\":null,\"displayName\":\"Zé Carlos\",\"jersey\":\"1\","
                + "\"position\":{\"name\":\"Goalkeeper\",\"abbreviation\":\"G\"}}]}";
        Squad s = Squad.espn(new JSONObject(roster));
        assertEquals("Riverside", s.name);
        assertEquals(0xFFC8102E, s.colour);
        assertTrue("ESPN's coach list is the club's past managers", s.staff.isEmpty());
        assertEquals(2, s.players.size());
        Squad.Person gk = s.players.get(0), fwd = s.players.get(1);
        assertEquals("Zé Carlos", gk.last);
        assertEquals("", gk.first);
        assertEquals(Squad.GK, gk.line);
        assertEquals("", gk.photo);
        assertTrue(gk.figures.isEmpty());
        assertEquals(Squad.FWD, fwd.line);
        assertEquals("Ibe", fwd.last);
        assertEquals("11", fwd.number);
        assertEquals("Ghana", fwd.country);
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/gha.png", fwd.flag);
        assertEquals("https://a.espncdn.com/i/headshots/soccer/players/full/9001.png", fwd.photo);
        assertFalse(fwd.face);
        assertFalse(fwd.fotmob);
        assertArrayEquals(new String[]{"Apps", "9"}, fwd.figures.get(0));
        assertArrayEquals(new String[]{"Goals", "4"}, fwd.figures.get(1));
        assertArrayEquals(new String[]{"Assists", "2"}, fwd.figures.get(2));
        assertArrayEquals(new String[]{"Yellow cards", "1"}, fwd.figures.get(3));
        assertArrayEquals(new String[]{"Red cards", "0"}, fwd.figures.get(4));
    }

    private static JSONArray search(String... teams) throws Exception {
        JSONArray sg = new JSONArray();
        for (String t : teams) {
            String[] p = t.split("\\|");
            sg.put(new JSONObject().put("type", "team").put("id", p[0]).put("name", p[1]).put("leagueId", Integer.parseInt(p[2])));
        }
        sg.put(new JSONObject().put("type", "player").put("id", "77").put("name", "Harbour Town"));
        return new JSONArray().put(new JSONObject().put("suggestions", sg));
    }

    @Test public void fotmobsSearchFindsTheSeniorClub() throws Exception {
        // Its women's and youth sides passed over; the club of the name in its league before another of the name.
        assertEquals(12, Squad.pickTeam(search("10|Harbour Town (W)|9227", "11|Harbour Town U21|900", "12|Harbour Town|47"), "Harbour Town", 47));
        assertEquals(31, Squad.pickTeam(search("30|Harbour Town|130", "31|Harbour Town|47"), "Harbour Town", 47));
        assertEquals(30, Squad.pickTeam(search("30|Harbour Town|130", "31|Harbour Town|47"), "Harbour Town", 0));
        // A name the search knows differently ("Internazionale", "Inter"): its first senior side.
        assertEquals(40, Squad.pickTeam(search("41|Inter (W)|600", "40|Inter|55"), "Internazionale", 55));
        assertEquals(0, Squad.pickTeam(search("50|Harbour Town (W)|9227"), "Harbour Town", 47));
        assertEquals(0, Squad.pickTeam(new JSONArray(), "Harbour Town", 47));
        assertTrue(Squad.senior("Harbour Town"));
        assertFalse(Squad.senior("Harbour Town B"));
        assertFalse(Squad.senior("Harbour Town Reserves"));
        assertFalse(Squad.senior("Harbour Town U-23"));
    }

    @Test public void aPlayersSeasonLeavesOutWhatTheSourceLacks() throws Exception {
        String player = "{\"positionDescription\":{\"primaryPosition\":{\"label\":\"Central Midfielder\"}},\"mainLeague\":{\"leagueName\":"
                + "\"Coastal League\",\"season\":\"2026/2027\",\"stats\":[{\"title\":\"Goals\",\"localizedTitleId\":\"goals\",\"value\":6},"
                + "{\"title\":\"Rating\",\"localizedTitleId\":\"rating\",\"value\":7.4},{\"title\":\"Minutes played\",\"localizedTitleId\":"
                + "\"minutes_played\",\"value\":1138},{\"title\":\"Matches\",\"localizedTitleId\":\"matches_uppercase\",\"value\":14},"
                + "{\"title\":\"Started\",\"localizedTitleId\":\"started\",\"value\":12},{\"title\":\"Yellow cards\",\"localizedTitleId\":"
                + "\"yellow_cards\",\"value\":2},{\"title\":\"Assists\",\"localizedTitleId\":\"assists\",\"value\":null}]}}";
        Squad.Season s = Squad.season(new JSONObject(player));
        assertEquals("Coastal League", s.competition);
        assertEquals("2026/2027", s.season);
        assertEquals("Central Midfielder", s.position);
        assertEquals(5, s.figures.size());
        assertArrayEquals(new String[]{"Apps", "14"}, s.figures.get(0));
        assertArrayEquals(new String[]{"Goals", "6"}, s.figures.get(1));
        assertArrayEquals(new String[]{"Yellow cards", "2"}, s.figures.get(2));
        assertArrayEquals(new String[]{"Minutes", "1,138"}, s.figures.get(3));
        assertArrayEquals(new String[]{"Rating", "7.40"}, s.figures.get(4));
    }

    @Test public void aGoalkeepersAndAManagersSeason() throws Exception {
        String keeper = "{\"mainLeague\":{\"leagueName\":\"Coastal League\",\"season\":\"2026/2027\",\"stats\":[{\"title\":\"Clean sheets\","
                + "\"localizedTitleId\":\"clean_sheet_title\",\"value\":7},{\"title\":\"Goals conceded\",\"localizedTitleId\":\"goals_conceded\","
                + "\"value\":5},{\"title\":\"Matches\",\"localizedTitleId\":\"matches_uppercase\",\"value\":10}]}}";
        Squad.Season k = Squad.season(new JSONObject(keeper));
        assertEquals(2, k.figures.size());
        assertArrayEquals(new String[]{"Apps", "10"}, k.figures.get(0));
        assertArrayEquals(new String[]{"Clean sheets", "7"}, k.figures.get(1));

        String manager = "{\"isCoach\":true,\"mainLeague\":{\"stats\":null},\"coachStats\":{\"activeCareerEntry\":{\"teamName\":\"Harbour Town\","
                + "\"matches\":41,\"wins\":22,\"draws\":9,\"losses\":10}}}";
        Squad.Season m = Squad.season(new JSONObject(manager));
        assertEquals("At Harbour Town", m.competition);
        assertEquals("", m.season);
        assertArrayEquals(new String[]{"Matches", "41"}, m.figures.get(0));
        assertArrayEquals(new String[]{"Won", "22"}, m.figures.get(1));
        assertArrayEquals(new String[]{"Drawn", "9"}, m.figures.get(2));
        assertArrayEquals(new String[]{"Lost", "10"}, m.figures.get(3));
        assertEquals(0, Squad.season(new JSONObject("{}")).figures.size());
    }

    @Test public void namesFlagsAndColours() {
        assertArrayEquals(new String[]{"Cole", "Hart"}, Squad.split("Cole Hart"));
        assertArrayEquals(new String[]{"Virgil", "van Dijkstra"}, Squad.split("Virgil van Dijkstra"));
        assertArrayEquals(new String[]{"Kevin", "De Bruin"}, Squad.split("Kevin De Bruin"));
        assertArrayEquals(new String[]{"Andrea Kimi", "Antonelli"}, Squad.split("Andrea  Kimi Antonelli"));
        assertArrayEquals(new String[]{"", "Gabriel"}, Squad.split("Gabriel"));
        assertArrayEquals(new String[]{"", "van Dijkstra"}, Squad.split("van Dijkstra"));
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/ned.png", Squad.flag("NED"));
        assertEquals("", Squad.flag("NL"));
        assertEquals("", Squad.flag(null));
        assertEquals(0xFF144992, Squad.colour("144992"));
        assertEquals(0xFF051495, Squad.colour("#051495"));
        assertEquals(0, Squad.colour("blue"));
        assertEquals(0, Squad.colour(""));
        assertEquals(Squad.MID, Squad.lineOf("midfielders", "midfielder_long", 2));
        assertEquals(Squad.GK, Squad.lineOf("", "", 0));
        assertEquals(Squad.STAFF, Squad.lineOf("assistant staff", "", -1));
        assertEquals("Defender", Squad.position("XX", Squad.DEF));
    }

    @Test public void aTablesRowsKeepTheFullNameAndItsClubsTheirColours() throws Exception {
        String standings = "{\"children\":[{\"standings\":{\"entries\":[{\"team\":{\"id\":\"31\",\"displayName\":\"Riverside Athletic\","
                + "\"shortDisplayName\":\"Riverside\",\"logos\":[{\"href\":\"https://a.espncdn.com/i/teamlogos/soccer/500/31.png\"}]},"
                + "\"stats\":[{\"name\":\"rank\",\"displayValue\":\"2\"},{\"name\":\"points\",\"displayValue\":\"28\"}]},{\"team\":{\"id\":\"30\","
                + "\"displayName\":\"Harbour Town\"},\"stats\":[{\"name\":\"rank\",\"displayValue\":\"1\"},{\"name\":\"points\",\"displayValue\":\"30\"}]}]}}]}";
        List<String[]> t = Espn.table(new JSONObject(standings));
        assertEquals(2, t.size());
        assertEquals("Harbour Town", t.get(0)[Espn.T_NAME]);
        assertEquals("Riverside", t.get(1)[Espn.T_NAME]);
        assertEquals("Riverside Athletic", t.get(1)[Espn.T_FULL]);
        assertEquals("28", t.get(1)[Espn.T_POINTS]);
        String teams = "{\"sports\":[{\"leagues\":[{\"teams\":[{\"team\":{\"id\":\"31\",\"displayName\":\"Riverside Athletic\",\"color\":\"c8102e\","
                + "\"alternateColor\":\"ffffff\",\"logos\":[{\"href\":\"x\"}]}}]}]}]}";
        String[] club = Espn.teams(new JSONObject(teams)).get(0);
        assertEquals("c8102e", club[Espn.C_COLOUR]);
        assertEquals("ffffff", club[Espn.C_ALT]);
        Squad.Club c = Squad.Club.of("eng.1", t.get(1), club);
        assertEquals("31", c.espnId);
        assertEquals("Riverside Athletic", c.fullName);
        assertEquals("c8102e", c.colour);
        assertEquals("eng.1", c.slug);
    }

    @Test public void aGamesSideInItsOwnColoursNotTheKitWorn() {
        Game g = new Game();
        g.id = "900";
        g.slug = "eng.1";
        g.homeId = "30";
        g.awayId = "31";
        g.homeName = "Harbour";
        g.homeFull = "Harbour Town";
        g.awayName = "Riverside";
        g.homeColor = "ffff00";
        g.homeOwn = "1e3fa8";
        g.awayOwn = "c8102e";
        Squad.Club home = Squad.Club.of(g, false), away = Squad.Club.of(g, true);
        assertEquals("30", home.espnId);
        assertEquals("Harbour Town", home.fullName);
        assertEquals("1e3fa8", home.colour);
        assertEquals("31", away.espnId);
        assertEquals("Riverside", away.name);
        assertEquals("c8102e", away.colour);
        assertEquals(0, away.fotmobId);
    }
}
