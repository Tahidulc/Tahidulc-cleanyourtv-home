package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The clubs' own goal animations (ClubGoals): the 96, found by ESPN's id or their names, each with its colours and motif. */
public class ClubGoalsTest {

    private static String id(String espnId, String... names) {
        ClubGoals.Club c = ClubGoals.find(espnId, names);
        return c == null ? null : c.id;
    }

    @Test
    public void theTableHasTheFiveLeaguesNinetySixClubsOnceEach() {
        List<ClubGoals.Club> all = ClubGoals.all();
        assertEquals(96, all.size());
        Set<String> ids = new HashSet<>();
        for (ClubGoals.Club c : all) assertTrue(c.name, ids.add(c.id));
        Map<String, List<String>> leagues = ClubGoals.byLeague();
        assertEquals(20, leagues.get("Premier League").size());
        assertTrue(leagues.get("Premier League").contains("Arsenal"));
        assertTrue(leagues.get("LaLiga").contains("Real Madrid"));
        assertTrue(leagues.get("Serie A").contains("Juventus"));
        assertTrue(leagues.get("Bundesliga").contains("Borussia Dortmund"));
        assertTrue(leagues.get("Ligue 1").contains("Paris Saint-Germain"));
    }

    @Test
    public void aClubIsFoundByItsEspnIdFirst() {
        assertEquals("Arsenal", ClubGoals.byId("359").name);
        assertEquals("Real Madrid", ClubGoals.byId(" 86 ").name);
        // the id wins over a name that says otherwise
        assertEquals("359", id("359", "Chelsea"));
        assertNull(ClubGoals.byId("999999"));
        assertNull(ClubGoals.byId(null));
    }

    @Test
    public void elseByItsNameAsAnyFeedWritesIt() {
        // accents, either way
        assertEquals("1068", id(null, "Atlético Madrid"));
        assertEquals("1068", id("", "Atletico Madrid"));
        assertEquals("1068", id(null, "Club Atlético de Madrid"));
        assertEquals("132", id(null, "FC Bayern München"));
        assertEquals("132", id(null, "Bayern Munich"));
        assertEquals("268", id(null, "Borussia Mönchengladbach"));
        assertEquals("268", id(null, "Borussia M'gladbach"));
        assertEquals("122", id(null, "1. FC Köln"));
        assertEquals("122", id(null, "FC Cologne"));
        assertEquals("96", id(null, "Deportivo Alavés"));
        assertEquals("99", id(null, "MÁLAGA CF"));
        // FC / AFC / CF / SC / AC / AS and the like, and the years
        assertEquals("349", id(null, "AFC Bournemouth"));
        assertEquals("349", id(null, "Bournemouth"));
        assertEquals("86", id(null, "Real Madrid CF"));
        assertEquals("83", id(null, "FC Barcelona"));
        assertEquals("103", id(null, "AC Milan"));
        assertEquals("104", id(null, "AS Roma"));
        assertEquals("126", id(null, "SC Freiburg"));
        assertEquals("2950", id(null, "1. FSV Mainz 05"));
        assertEquals("2950", id(null, "Mainz"));
        assertEquals("107", id(null, "Bologna FC 1909"));
        assertEquals("133", id(null, "FC Schalke 04"));
        assertEquals("7911", id(null, "TSG 1899 Hoffenheim"));
        assertEquals("127", id(null, "Hamburger SV"));
        assertEquals("137", id(null, "SV Werder Bremen"));
        assertEquals("114", id(null, "SSC Napoli"));
        assertEquals("112", id(null, "SS Lazio"));
        assertEquals("166", id(null, "LOSC Lille"));
        assertEquals("2502", id(null, "OGC Nice"));
        // short forms and other names
        assertEquals("382", id(null, "Man City"));
        assertEquals("360", id(null, "Man Utd"));
        assertEquals("367", id(null, "Spurs"));
        assertEquals("393", id(null, "Nott'm Forest"));
        assertEquals("110", id(null, "Internazionale"));
        assertEquals("110", id(null, "Inter Milan"));
        assertEquals("160", id(null, "Paris Saint-Germain"));
        assertEquals("160", id(null, "PSG"));
        assertEquals("176", id(null, "Olympique de Marseille"));
        assertEquals("176", id(null, "Marseille"));
        assertEquals("167", id(null, "Lyon"));
        assertEquals("331", id(null, "Brighton & Hove Albion"));
        assertEquals("331", id(null, "Brighton and Hove Albion"));
        // the first name that matches, of those given
        assertEquals("124", id(null, "Unknown United", "Borussia Dortmund"));
    }

    @Test
    public void aNameCarryingAClubsWholeNameIsThatClubButNotOneWordOfIt() {
        assertEquals("382", id(null, "Manchester City Women"));
        assertEquals("86", id(null, "Real Madrid Castilla"));
        // one word alone is never enough: Inter Miami is not Inter, Paris FC not Paris Saint-Germain
        assertNull(id(null, "Inter Miami"));
        assertEquals("6851", id(null, "Paris FC"));
        assertNull(id(null, "Racing Club"));
        assertNull(id(null, "Newcastle Jets"));
        assertNull(id(null, "Charlton Athletic"));
        assertNull(id(null, "Whitebrook Rovers"));
        assertNull(id(null));
        assertNull(id(null, (String) null));
        assertNull(id(null, ""));
    }

    @Test
    public void namesAreMatchedWithoutAccentsKindsOrNumbers() {
        assertEquals("atletico madrid", ClubGoals.norm("Club Atlético de Madrid"));
        assertEquals("bayern munchen", ClubGoals.norm("FC Bayern München"));
        assertEquals("koln", ClubGoals.norm("1. FC Köln"));
        assertEquals("brighton hove albion", ClubGoals.norm("Brighton & Hove Albion"));
        assertEquals("paris saint germain", ClubGoals.norm("Paris Saint-Germain"));
        assertEquals("", ClubGoals.norm(null));
        assertEquals("", ClubGoals.norm("  FC  "));
    }

    @Test
    public void everyClubHasAMotifAndTwoColoursGoalReadsOn() {
        Set<String> shapes = new HashSet<>(java.util.Arrays.asList(GoalShapes.NAMES));
        for (ClubGoals.Club c : ClubGoals.all()) {
            assertNotNull(c.name, c.motif);
            char k = c.kind();
            assertTrue(c.name + " " + c.motif, k == 'e' || k == 's' || k == 'p');
            assertFalse(c.name, c.what().isEmpty());
            if (k == 's') assertTrue(c.name + " " + c.motif, shapes.contains(c.what()));
            if (k == 'e') assertTrue(c.name + " " + c.motif, c.what().matches("[0-9a-f]{4,5}(-[0-9a-f]{4,5})*"));
            // its sweep runs through two colours, and GOAL! reads on both at WCAG's 4.5
            assertNotEquals(c.name, c.sweep0, c.sweep1);
            assertTrue(c.name + " on its first", BoardColours.contrast(c.ink, c.sweep0) >= 4.5);
            assertTrue(c.name + " on its second", BoardColours.contrast(c.ink, c.sweep1) >= 4.5);
            // the dark strip: the letters white on it, the motif seen on it
            assertTrue(c.name + " letters", BoardColours.contrast(0xFFFFFFFF, c.ground) >= 7.0);
            assertTrue(c.name + " motif", BoardColours.contrast(c.motifInk, c.ground) >= 3.0);
            assertFalse(c.generic());
        }
    }

    @Test
    public void thePhrasesAreTheClubsOwn() {
        assertEquals("p:YNWA", ClubGoals.byId("364").motif);
        assertEquals("p:HALA MADRID", ClubGoals.byId("86").motif);
        assertEquals("p:BARÇA", ClubGoals.byId("83").motif);
        assertEquals("p:MIA SAN MIA", ClubGoals.byId("132").motif);
        assertEquals("p:ECHTE LIEBE", ClubGoals.byId("124").motif);
        assertEquals("p:ICI C'EST PARIS", ClubGoals.byId("160").motif);
        assertEquals("p:DROIT AU BUT", ClubGoals.byId("176").motif);
        assertEquals("p:FINO ALLA FINE", ClubGoals.byId("111").motif);
        // the silhouettes as the season intros have them
        assertEquals("s:cannon", ClubGoals.byId("359").motif);
        assertEquals("e:1f981", ClubGoals.byId("362").motif);
        assertEquals("e:1f43b", ClubGoals.byId("1068").motif);
        assertEquals("e:1f40d", ClubGoals.byId("110").motif);
    }

    @Test
    public void anyOtherTeamHasTheGenericInItsOwnColour() {
        int[] colours = {0xFF1B458F, 0xFFFDE100, 0xFFFFFFFF, 0xFF000000, 0xFF7F7F7F, 0xFF00A752, 0xFFE30613, 0xFF6CABDD, 0, 0xFF808000};
        for (int colour : colours) {
            ClubGoals.Club g = ClubGoals.generic(colour);
            assertTrue(g.generic());
            assertEquals(ClubGoals.BALL, g.motif);
            assertEquals('s', g.kind());
            assertEquals("ball", g.what());
            assertNotEquals(g.sweep0, g.sweep1);
            assertTrue(Integer.toHexString(colour), BoardColours.contrast(g.ink, g.sweep0) >= 4.5);
            assertTrue(Integer.toHexString(colour), BoardColours.contrast(g.ink, g.sweep1) >= 4.5);
            assertTrue(Integer.toHexString(colour), BoardColours.contrast(0xFFFFFFFF, g.ground) >= 7.0);
            assertTrue(Integer.toHexString(colour), BoardColours.contrast(g.motifInk, g.ground) >= 3.0);
        }
        // a dark blue stays itself under white; a yellow sweeps lighter under a near-black
        assertEquals(0xFF1B458F, ClubGoals.generic(0xFF1B458F).sweep0);
        assertEquals(0xFFFFFFFF, ClubGoals.generic(0xFF1B458F).ink);
        assertEquals(0xFFFDE100, ClubGoals.generic(0xFFFDE100).sweep0);
        assertNotEquals(0xFFFFFFFF, ClubGoals.generic(0xFFFDE100).ink);
        // no colour: a plain blue
        assertEquals(ClubGoals.PLAIN, ClubGoals.generic(0).sweep0);
    }

    @Test
    public void theChoiceGivesTheClubsOwnTheGenericOrNone() {
        ClubGoals.Club arsenal = GoalLooks.clubFor(GoalLooks.CLUBS, "359", 0xFFEF0107, "Arsenal");
        assertSame(ClubGoals.byId("359"), arsenal);
        assertSame(ClubGoals.byId("86"), GoalLooks.clubFor(GoalLooks.CLUBS, null, 0xFFFFFFFF, "Real Madrid CF"));
        ClubGoals.Club other = GoalLooks.clubFor(GoalLooks.CLUBS, "5000", 0xFF00843D, "Whitebrook Rovers");
        assertTrue(other.generic());
        assertEquals(0xFF00843D, other.sweep0);
        // Generic: every team the generic, a club of the 96 as well
        assertTrue(GoalLooks.clubFor(GoalLooks.GENERIC, "359", 0xFFEF0107, "Arsenal").generic());
        // the boards' own animations: none
        assertNull(GoalLooks.clubFor(GoalLooks.TODAY, "359", 0xFFEF0107, "Arsenal"));
        assertNull(GoalLooks.clubFor(GoalLooks.BAND, "359", 0xFFEF0107, "Arsenal"));
        assertSame(ClubGoals.byId("359"), GoalLooks.clubFor(GoalLooks.CLUBS, "359", 0, (String[]) null));
    }
}
