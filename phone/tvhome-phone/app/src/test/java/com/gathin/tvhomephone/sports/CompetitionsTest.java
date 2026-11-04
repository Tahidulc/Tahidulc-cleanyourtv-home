package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompetitionsTest {
    private static Game game(String slug) {
        Game g = new Game();
        g.id = "1";
        g.slug = slug;
        return g;
    }

    @Test public void everyCompetitionIsOnUntilSwitchedOffButFriendlies() {
        Map<String, Object> none = Collections.emptyMap();
        for (String[] l : Espn.LEAGUES) assertEquals(l[0], !"fifa.friendly".equals(l[0]), Competitions.allowed(none, l[0]));
        assertTrue(Competitions.allowed(none, Competitions.F1));
        assertTrue(Competitions.allowed(null, "eng.1"));
        assertFalse(Competitions.allowed(null, "fifa.friendly"));
    }

    @Test public void theSwitchSavedWins() {
        Map<String, Object> p = new HashMap<>();
        p.put("eng.1", false);
        p.put("fifa.friendly", true);
        p.put(Competitions.F1, false);
        assertFalse(Competitions.allowed(p, "eng.1"));
        assertTrue(Competitions.allowed(p, "fifa.friendly"));
        assertFalse(Competitions.allowed(p, Competitions.F1));
        assertTrue(Competitions.allowed(p, "uefa.champions"));
    }

    @Test public void somethingElseSavedUnderTheNameCountsAsNotSet() {
        Map<String, Object> p = new HashMap<>();
        p.put("eng.1", "off");
        p.put("fifa.friendly", 1);
        assertTrue(Competitions.allowed(p, "eng.1"));
        assertFalse(Competitions.allowed(p, "fifa.friendly"));
    }

    @Test public void aGameWithNoCompetitionIsNeverHeldBack() {
        Map<String, Object> p = new HashMap<>();
        p.put("", false);
        assertTrue(Competitions.allowed(p, ""));
        assertTrue(Competitions.allowed(p, (String) null));
    }

    @Test public void alertsFollowTheirCompetitionsSwitch() {
        Map<String, Object> p = new HashMap<>();
        p.put("eng.fa", false);
        p.put(Competitions.F1, false);
        assertFalse(Competitions.allowed(p, new Events.Alert("goal", "G|1", "GOAL", "", game("eng.fa"))));
        assertFalse(Competitions.allowed(p, new Events.Alert("table", "T|1", "LIVE TABLE", "", game("eng.fa"))));
        assertTrue(Competitions.allowed(p, new Events.Alert("goal", "G|2", "GOAL", "", game("eng.1"))));
        assertFalse(Competitions.allowed(p, new Events.Alert("f1grid", "F1G|20", "F1 GRID", "", null)));
        assertFalse(Competitions.allowed(p, new Events.Alert("f1result", "F1R|20", "F1 RESULT", "", null)));
        assertFalse(Competitions.allowed(new HashMap<>(), new Events.Alert("goal", "G|3", "GOAL", "", game("fifa.friendly"))));
        assertTrue(Competitions.allowed(p, new Events.Alert("weather", "W|1", "RAIN", "", null)));
        assertFalse(Competitions.allowed(p, (Events.Alert) null));
    }

    @Test public void remindersSetByHandAlwaysCome() {
        Map<String, Object> p = new HashMap<>();
        p.put("eng.fa", false);
        p.put(Competitions.F1, false);
        Events.Alert r = new Events.Alert("reminder", "RM|x|1", "STARTS SOON · Man Utd v Arsenal", "At 15:00", null);
        r.slug = "eng.fa";
        assertTrue(Competitions.allowed(p, r));
        assertTrue(Competitions.allowed(p, new Events.Alert("reminder", "RM|y|1", "STARTS SOON", "", game("eng.fa"))));
    }

    @Test public void theSwitchesListEveryCompetitionThenF1() {
        List<String[]> all = Competitions.all();
        assertEquals(Espn.LEAGUES.length + 1, all.size());
        assertEquals("eng.1", all.get(0)[0]);
        assertEquals("Premier League", all.get(0)[1]);
        assertEquals(Competitions.F1, all.get(all.size() - 1)[0]);
    }

    @Test public void everyCompetitionAndTableHasALogo() {
        for (String[] l : Espn.LEAGUES) {
            assertTrue(l[0], Competitions.logo(l[0]).startsWith("https://a.espncdn.com/"));
            assertTrue(l[0], Competitions.lightLogo(l[0]).startsWith("https://a.espncdn.com/i/leaguelogos/soccer/500/"));
        }
        for (String[] t : Espn.TABLES) assertTrue(t[0], Competitions.logo(t[0]).startsWith("https://a.espncdn.com/"));
        assertEquals("https://a.espncdn.com/i/leaguelogos/soccer/500-dark/23.png", Competitions.logo("eng.1"));
        assertEquals("https://a.espncdn.com/i/leaguelogos/soccer/500/23.png", Competitions.lightLogo("eng.1"));
        assertEquals("https://a.espncdn.com/i/leaguelogos/soccer/500-dark/2.png", Competitions.logo("uefa.champions"));
        assertEquals("https://a.espncdn.com/i/teamlogos/leagues/500/f1.png", Competitions.logo(Competitions.F1));
        assertNull(Competitions.logo("usa.1"));
        assertNull(Competitions.logo((String) null));
    }

    @Test public void aGamesOwnLogoFirst() {
        Game g = game("eng.1");
        assertEquals("https://a.espncdn.com/i/leaguelogos/soccer/500-dark/23.png", Competitions.logo(g));
        g.leagueLogo = "https://example.org/pl.png";
        assertEquals("https://example.org/pl.png", Competitions.logo(g));
    }

    @Test public void theScoreboardsDarkLogoIsPicked() throws Exception {
        JSONArray both = new JSONArray("[{\"href\":\"https://a.espncdn.com/i/leaguelogos/soccer/500/23.png\",\"rel\":[\"full\",\"default\"]},"
                + "{\"href\":\"https://a.espncdn.com/i/leaguelogos/soccer/500-dark/23.png\",\"rel\":[\"full\",\"dark\"]}]");
        String[] p = Competitions.pick(both);
        assertEquals("https://a.espncdn.com/i/leaguelogos/soccer/500-dark/23.png", p[0]);
        assertEquals("https://a.espncdn.com/i/leaguelogos/soccer/500/23.png", p[1]);
        JSONArray one = new JSONArray("[{\"href\":\"https://a.espncdn.com/i/leaguelogos/soccer/500/9.png\",\"rel\":[\"full\",\"default\"]}]");
        assertEquals("https://a.espncdn.com/i/leaguelogos/soccer/500/9.png", Competitions.pick(one)[0]);
        assertNull(Competitions.pick(new JSONArray("[{\"rel\":[\"dark\"]}]")));
        assertNull(Competitions.pick(null));
    }
}
