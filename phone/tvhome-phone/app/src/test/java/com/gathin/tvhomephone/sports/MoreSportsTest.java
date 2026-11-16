package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.SportBoard;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** The other sports' feeds read (made-up teams and players), and which rows and matches the page shows. */
public class MoreSportsTest {
    private static JSONObject team(String side, String name, String shortName, String score, boolean won) throws Exception {
        return new JSONObject().put("homeAway", side).put("score", score).put("winner", won)
                .put("team", new JSONObject().put("displayName", name).put("shortDisplayName", shortName)
                        .put("logo", "https://example.invalid/" + shortName + ".png"));
    }

    private static JSONObject game(String id, String date, String state, String detail, JSONObject home, JSONObject away) throws Exception {
        return new JSONObject().put("id", id).put("date", date).put("competitions", new JSONArray().put(new JSONObject()
                .put("status", new JSONObject().put("type", new JSONObject().put("state", state).put("shortDetail", detail)))
                .put("competitors", new JSONArray().put(home).put(away))));
    }

    @Test public void teamSportGamesLiveFirstThenToComeThenFinished() throws Exception {
        JSONObject root = new JSONObject()
                .put("leagues", new JSONArray().put(new JSONObject().put("abbreviation", "NFL")
                        .put("logos", new JSONArray().put(new JSONObject().put("href", "https://example.invalid/nfl.png")))))
                .put("events", new JSONArray()
                        .put(game("1", "2026-11-15T18:00Z", "post", "Final", team("home", "Harbour Hawks", "Hawks", "13", false),
                                team("away", "River Kings", "Kings", "35", true)))
                        .put(game("2", "2026-11-17T01:15Z", "pre", "8:15 PM", team("home", "Bay Rams", "Rams", "0", false),
                                team("away", "Lake Bears", "Bears", "0", false)))
                        .put(game("3", "2026-11-16T21:25Z", "in", "Q3 4:12", team("home", "Hill Owls", "Owls", "17", false),
                                team("away", "Port Foxes", "Foxes", "10", false))));
        List<MoreSports.Match> m = MoreSports.matches(root);
        assertEquals(3, m.size());
        assertEquals("3", m.get(0).id);
        assertTrue(m.get(0).live());
        assertEquals("Q3 4:12", m.get(0).detail);
        assertEquals("Owls", m.get(0).homeShort);
        assertEquals("Foxes", m.get(0).awayShort);
        assertEquals("2", m.get(1).id);
        assertEquals("1", m.get(2).id);
        assertTrue(m.get(2).awayWin);
        assertEquals("NFL", m.get(2).league);
        assertEquals("https://example.invalid/nfl.png", m.get(2).leagueLogo);
    }

    private static JSONObject golfer(int order, String name, String flag, String score) throws Exception {
        return new JSONObject().put("order", order).put("score", score)
                .put("athlete", new JSONObject().put("displayName", name).put("flag", new JSONObject().put("href", flag)))
                .put("linescores", new JSONArray().put(new JSONObject().put("displayValue", "-4")).put(new JSONObject().put("displayValue", "-3")));
    }

    @Test public void golfLeadersWithTiesSharingTheirPlace() throws Exception {
        JSONObject root = new JSONObject()
                .put("leagues", new JSONArray().put(new JSONObject().put("name", "PGA Tour")))
                .put("events", new JSONArray().put(new JSONObject().put("name", "Harbour Classic")
                        .put("status", new JSONObject().put("type", new JSONObject().put("state", "in").put("shortDetail", "Round 3")))
                        .put("competitions", new JSONArray().put(new JSONObject().put("competitors", new JSONArray()
                                .put(golfer(3, "C Player", "c.png", "-9"))
                                .put(golfer(1, "A Player", "a.png", "-12"))
                                .put(golfer(2, "B Player", "b.png", "-9"))
                                .put(golfer(4, "D Player", "d.png", "-7")))))));
        MoreSports.Leaderboard b = MoreSports.leaderboard(root, 3);
        assertEquals("Harbour Classic", b.event);
        assertEquals("PGA Tour", b.tour);
        assertEquals("in", b.state);
        assertEquals(3, b.rows.size());
        assertEquals("1", b.rows.get(0)[0]);
        assertEquals("A Player", b.rows.get(0)[1]);
        assertEquals("T2", b.rows.get(1)[0]);
        assertEquals("T2", b.rows.get(2)[0]);
        assertEquals("-4 -3", b.rows.get(0)[4]);
    }

    @Test public void aClubsNextGamesFromItsSchedule() throws Exception {
        JSONObject home = new JSONObject().put("homeAway", "home").put("team", new JSONObject().put("id", "1").put("abbreviation", "HAR")
                .put("displayName", "Harbour United").put("shortDisplayName", "Harbour")
                .put("logos", new JSONArray().put(new JSONObject().put("href", "https://example.invalid/1.png"))));
        JSONObject away = new JSONObject().put("homeAway", "away").put("team", new JSONObject().put("id", "2").put("abbreviation", "RIV")
                .put("displayName", "River Town").put("shortDisplayName", "River")
                .put("logos", new JSONArray().put(new JSONObject().put("href", "https://example.invalid/2.png"))));
        JSONObject e = new JSONObject().put("id", "77").put("date", "2026-11-22T16:30Z")
                .put("league", new JSONObject().put("slug", "eng.1").put("abbreviation", "Premier League"))
                .put("competitions", new JSONArray().put(new JSONObject()
                        .put("status", new JSONObject().put("type", new JSONObject().put("state", "pre")))
                        .put("competitors", new JSONArray().put(home).put(away))));
        JSONObject done = new JSONObject(e.toString()).put("id", "76").put("date", "2026-11-08T15:00Z");
        done.getJSONArray("competitions").getJSONObject(0).getJSONObject("status").getJSONObject("type").put("state", "post");
        List<Game> games = MoreSports.fixtures(new JSONObject().put("events", new JSONArray().put(done).put(e)), ZoneOffset.UTC);
        assertEquals(1, games.size());
        Game g = games.get(0);
        assertEquals("77", g.id);
        assertEquals("eng.1", g.slug);
        assertEquals("Premier League", g.leagueName);
        assertEquals("https://example.invalid/1.png", g.homeLogo);
        assertEquals("1", g.homeId);
    }

    @Test public void foldedTableKeepsTheTopAndYourClub() {
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i <= 20; i++) rows.add(new String[]{String.valueOf(i), "t" + i, "Team " + i, "", "10", "0", "20", ""});
        assertEquals(Arrays.asList(0, 1, 2, 3, 16, 17, 18), SportsScreen.condensed(rows, new HashSet<>(Arrays.asList("t18"))));
        assertEquals(Arrays.asList(0, 1, 2, 3, 4), SportsScreen.condensed(rows, new HashSet<>()));
        // Your club near the top: no gap made.
        assertEquals(Arrays.asList(0, 1, 2, 3, 4), SportsScreen.condensed(rows, new HashSet<>(Arrays.asList("t4"))));
    }

    private static SportBoard match(String state, long start) {
        SportBoard s = new SportBoard();
        s.sport = SportBoard.TENNIS;
        s.state = state;
        s.start = start;
        return s;
    }

    @Test public void tennisChannelFoundByAPlayersName() {
        SportBoard on = match("pre", 1_794_860_000_000L);
        on.a.name = "Pat Harbour";
        on.a.shortName = "Harbour";
        on.b.name = "Lee Rivers";
        on.b.shortName = "Rivers";
        OnTv.Listing finals = new OnTv.Listing("Tour Finals: Harbour v Rivers", OnTv.TENNIS, on.start - 600_000L, on.start + 7_200_000L);
        finals.channels.add("Sky Sports Tennis");
        OnTv.Listing other = new OnTv.Listing("WTA Finals: Doubles Final", OnTv.TENNIS, on.start - 86_400_000L, on.start - 80_000_000L);
        other.channels.add("Sky Sports Main Event");
        assertEquals("Sky Sports Tennis", SportsFragment.tennisChannel(Arrays.asList(other, finals), Arrays.asList(on)));
        // A tour whose players no listing names: no channel, rather than another event's.
        SportBoard elsewhere = match("pre", on.start);
        elsewhere.a.name = "Kim Lane";
        elsewhere.a.shortName = "Lane";
        elsewhere.b.name = "Jo Hill";
        elsewhere.b.shortName = "Hill";
        assertEquals(null, SportsFragment.tennisChannel(Arrays.asList(other, finals), Arrays.asList(elsewhere)));
    }

    @Test public void tennisShowsLiveThenNextThenLatestResults() {
        SportBoard old = match("post", 1000), latest = match("post", 5000), live = match("in", 6000);
        SportBoard soon = match("pre", 7000), later = match("pre", 9000), last = match("pre", 12000);
        List<SportBoard> picked = SportsScreen.pickMatches(Arrays.asList(old, latest, live, later, soon, last), 4);
        assertEquals(Arrays.asList(live, soon, latest, old), picked);
    }
}
