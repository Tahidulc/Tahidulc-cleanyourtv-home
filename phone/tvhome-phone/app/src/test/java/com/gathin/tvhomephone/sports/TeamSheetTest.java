package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The line-ups' sheets read from FotMob's match details and ESPN's line-ups (made-up clubs and players throughout). */
public class TeamSheetTest {
    private static final String CREST = "https://images.fotmob.com/image_resources/logo/teamlogo/";
    private static final String PHOTO = "https://images.fotmob.com/image_resources/playerimages/";

    private static JSONObject player(int id, String first, String last, String shirt, String ccode, int usual, double x, double y,
                                     double rating, String[] events, Object[][] subs) throws Exception {
        JSONObject perf = new JSONObject().put("rating", rating);
        JSONArray ev = new JSONArray();
        for (String e : events) ev.put(new JSONObject().put("type", e));
        perf.put("events", ev);
        JSONArray se = new JSONArray();
        for (Object[] s : subs) se.put(new JSONObject().put("time", s[1]).put("type", s[0]));
        perf.put("substitutionEvents", se);
        JSONObject p = new JSONObject().put("id", id).put("name", first + " " + last).put("firstName", first).put("lastName", last)
                .put("shirtNumber", shirt).put("countryCode", ccode).put("countryName", "Wales").put("usualPlayingPositionId", usual)
                .put("performance", perf);
        if (x >= 0) p.put("verticalLayout", new JSONObject().put("x", x).put("y", y));
        return p;
    }

    private static JSONObject details() throws Exception {
        String[] none = {};
        Object[][] no = {};
        JSONArray starters = new JSONArray()
                .put(player(9001, "Tobias", "Ashby", "1", "WAL", 0, 0.5, 0.1, 6.8, none, no))
                .put(player(9002, "Rhys", "Lumley", "2", "WAL", 1, 0.125, 0.292, 7.1, new String[]{"assist"}, no))
                .put(player(9003, "Iwan", "Kettering", "5", "WAL", 1, 0.375, 0.292, 6.2, none, new Object[][]{{"subOut", 46}}))
                .put(player(9004, "Callum", "Varley", "9", "SCO", 3, 0.5, 0.87, 8.7, new String[]{"goal", "goal", "yellowCard"}, new Object[][]{{"subOut", 80}}))
                .put(player(9005, "Owen", "Pryce", "11", "WAL", 3, 0.162, 0.678, 7.3, none, new Object[][]{{"subOut", 80}}));
        JSONArray subs = new JSONArray()
                .put(player(9010, "Dylan", "Hough", "14", "WAL", 1, -1, -1, 7.0, none, new Object[][]{{"subIn", 46}}))
                .put(player(9011, "Gethin", "Moss", "17", "WAL", 3, -1, -1, 6.5, none, new Object[][]{{"subIn", 80}}))
                .put(player(9012, "Huw", "Tanner", "19", "WAL", 2, -1, -1, 6.4, none, new Object[][]{{"subIn", 80}}))
                .put(new JSONObject().put("id", 9013).put("name", "Emlyn Cross").put("shirtNumber", "30").put("usualPlayingPositionId", 0));
        JSONObject home = new JSONObject().put("name", "Harbour City Herons").put("formation", "4-3-3").put("rating", 7.2)
                .put("starters", starters).put("subs", subs)
                .put("coach", new JSONObject().put("id", 9100).put("name", "Elias Brandt").put("lastName", "Brandt")
                        .put("countryCode", "NOR").put("countryName", "Norway"));
        JSONObject away = new JSONObject().put("name", "Ashcombe Rovers").put("formation", "4-4-2")
                .put("starters", new JSONArray().put(player(9201, "Rory", "Fenwick", "1", "SCO", 0, 0.5, 0.1, 9.1, none, no)));
        return new JSONObject().put("content", new JSONObject().put("lineup", new JSONObject().put("homeTeam", home).put("awayTeam", away)));
    }

    @Test public void fotmobsSheetsWithRatingsPlacesAndEvents() throws Exception {
        TeamSheet[] s = TeamSheet.fotmob(details());
        TeamSheet h = s[0];
        assertEquals("FotMob", h.from);
        assertEquals("4-3-3", h.formation);
        assertEquals("7.2", h.rating);
        assertEquals(5, h.starters.size());
        TeamSheet.Man keeper = h.starters.get(0);
        assertEquals("GK", keeper.role);
        assertEquals("6.8", keeper.rating);
        assertEquals(PHOTO + "9001.png", keeper.photo);
        assertEquals(CREST + "wal.png", keeper.flag);
        // FotMob's x runs from the side's right: its 0.125 is this sheet's right-back.
        TeamSheet.Man back = h.starters.get(1);
        assertEquals(0.875f, back.x, 1e-6);
        assertEquals("RB", back.role);
        assertEquals(1, back.assists);
        assertEquals("CB", h.starters.get(2).role);
        assertEquals("46'", h.starters.get(2).off);
        TeamSheet.Man striker = h.starters.get(3);
        assertEquals("ST", striker.role);
        assertEquals(2, striker.goals);
        assertEquals(1, striker.yellow);
        assertEquals("8.7", striker.rating);
        assertEquals("RW", h.starters.get(4).role);
        // The one who came on at 46' came on for the one who went off then; the two at 80' can't be told apart.
        assertEquals("46'", h.subs.get(0).on);
        assertEquals("Kettering", h.subs.get(0).replaced);
        assertEquals("", h.subs.get(1).replaced);
        assertEquals("", h.subs.get(3).rating);
        assertEquals("Elias Brandt", h.manager.name);
        assertEquals(CREST + "nor.png", h.manager.flag);
        assertEquals(PHOTO + "9100.png", h.manager.photo);
        assertEquals("9.1", TeamSheet.best(s));
        assertEquals("8.7", TeamSheet.best(h));
    }

    @Test public void noLineUpsYetIsNone() throws Exception {
        assertNull(TeamSheet.fotmob(new JSONObject().put("content", new JSONObject())));
        assertNull(TeamSheet.fotmob(null));
    }

    @Test public void theBenchListsThoseWhoCameOnFirst() throws Exception {
        TeamSheet h = TeamSheet.fotmob(details())[0];
        List<String> order = new ArrayList<>();
        for (TeamSheet.Man m : TeamSheet.bench(h)) order.add(m.last);
        assertEquals(Arrays.asList("Hough", "Moss", "Tanner", "Cross"), order);
        assertEquals("On 46' for Kettering", SheetViews.cameOn(h.subs.get(0)));
        assertEquals("Unused", SheetViews.cameOn(h.subs.get(3)));
        assertEquals("#14 · DEF · Wales", SheetViews.about(h.subs.get(0)));
        List<String> pics = SheetViews.pictures(h);
        assertTrue(pics.contains(PHOTO + "9100.png"));
        assertEquals(1, pics.stream().filter(u -> u.equals(CREST + "wal.png")).count());
    }

    private static String[] espnRow(String number, String name, String position, boolean starter, String place, String shortName,
                                    String on, String off, int goals, String cards, String rating) {
        return new String[]{number, name, position, starter ? "1" : "0", place, shortName, on, off, String.valueOf(goals), cards, "0", "", rating};
    }

    @Test public void espnsLineUpsPlacedByTheirFormation() {
        List<String[]> lineup = new ArrayList<>();
        lineup.add(espnRow("1", "Tobias Ashby", "G", true, "1", "Ashby", "", "", 0, "", "6.6"));
        lineup.add(espnRow("2", "Rhys Lumley", "RB", true, "2", "Lumley", "", "", 0, "", "6.9"));
        lineup.add(espnRow("5", "Iwan Kettering", "CD-R", true, "3", "Kettering", "", "46'", 0, "Y", "6.1"));
        lineup.add(espnRow("6", "Bryn Calder", "CD-L", true, "4", "Calder", "", "", 0, "", "6.4"));
        lineup.add(espnRow("3", "Alun Rees", "LB", true, "5", "Rees", "", "", 0, "", "6.5"));
        lineup.add(espnRow("9", "Callum Varley", "CF", true, "6", "Varley", "", "", 1, "", "7.8"));
        lineup.add(espnRow("14", "Dylan Hough", "SUB", false, "", "Hough", "46'", "", 0, "", "6.7"));
        TeamSheet s = TeamSheet.espn(lineup, "4-1");
        assertEquals("ESPN", s.from);
        assertEquals(6, s.starters.size());
        assertEquals(1, s.subs.size());
        TeamSheet.Man keeper = s.starters.get(0);
        assertEquals("GK", keeper.role);
        assertEquals(0.1f, keeper.y, 1e-6);
        TeamSheet.Man centre = null;
        for (TeamSheet.Man m : s.starters) if (m.name.equals("Iwan Kettering")) centre = m;
        assertEquals("CB", centre.role);
        assertEquals("DEF", centre.group);
        assertEquals(1, centre.yellow);
        assertEquals("46'", centre.off);
        assertEquals("Kettering", s.subs.get(0).replaced);
        assertEquals("7.8", TeamSheet.best(s));
    }

    @Test public void espnsExactPositionsOnFotMobsPlayers() throws Exception {
        TeamSheet h = TeamSheet.fotmob(details())[0];
        List<String[]> lineup = new ArrayList<>();
        lineup.add(espnRow("5", "Iwan Kettering", "CD-R", true, "3", "Kettering", "", "", 0, "", ""));
        lineup.add(espnRow("11", "Owen Pryce", "LW", true, "10", "Pryce", "", "", 0, "", ""));
        lineup.add(espnRow("14", "Dylan Hough", "SUB", false, "", "Hough", "", "", 0, "", ""));
        TeamSheet.roles(h, lineup);
        assertEquals("CB", h.starters.get(2).role);
        assertEquals("LW", h.starters.get(4).role);
        assertEquals("DEF", h.subs.get(0).role);
    }

    @Test public void positionsInWords() {
        assertEquals("GK", TeamSheet.espnRole("G"));
        assertEquals("CB", TeamSheet.espnRole("CD-L"));
        assertEquals("CF", TeamSheet.espnRole("CF-R"));
        assertEquals("", TeamSheet.espnRole("SUB"));
        assertEquals("AM", TeamSheet.espnRole("AM"));
        assertEquals("MID", TeamSheet.groupOf("AM"));
        assertEquals("FWD", TeamSheet.groupOf("CF"));
        assertEquals("DEF", TeamSheet.groupOf("LWB"));
        assertEquals("GK", TeamSheet.group(0, -1));
        assertEquals("MID", TeamSheet.group(-1, 64));
        assertEquals("FWD", TeamSheet.group(-1, 115));
        assertEquals("LB", TeamSheet.role("DEF", 0.1f, 0.29f));
        assertEquals("RWB", TeamSheet.role("DEF", 0.9f, 0.45f));
        assertEquals("DM", TeamSheet.role("MID", 0.5f, 0.4f));
        assertEquals("AM", TeamSheet.role("MID", 0.5f, 0.68f));
        assertEquals("LW", TeamSheet.role("FWD", 0.16f, 0.68f));
        assertEquals("ST", TeamSheet.role("FWD", 0.5f, 0.87f));
    }
}
