package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Teams & competitions: the competitions' list, what the poll reads for them, and a league's clubs as ESPN gives them. */
public class FollowingTest {

    @Test
    public void everyCompetitionIsListedOnceWithItsTagAndName() {
        Set<String> slugs = new HashSet<>(), tags = new HashSet<>();
        for (String[] x : Following.COMPETITIONS) {
            assertEquals(3, x.length);
            assertTrue(x[0], slugs.add(x[0]));
            assertTrue(x[1], tags.add(x[1]));
            assertFalse(x[2].isEmpty());
        }
        // The ones the TV reads already keep the tags the alerts know them by.
        assertEquals("PL", Following.tag("eng.1"));
        assertEquals("UCL", Following.tag("uefa.champions"));
        assertEquals("FA CUP", Following.tag("eng.fa"));
        assertEquals("LIGA PORTUGAL", Following.tag("por.1"));
        // One not listed: its slug in capitals.
        assertEquals("BEL.1", Following.tag("bel.1"));
        assertEquals("Liga MX", Following.name("mex.1"));
        assertEquals("World Cup", Following.name("fifa.world"));
        assertEquals("bel.1", Following.name("bel.1"));
    }

    @Test
    public void addAClubLooksThroughLeaguesAndTheNationalSides() {
        Set<String> slugs = new HashSet<>();
        for (String[] l : Following.LEAGUES) assertTrue(l[0], slugs.add(l[0]));
        assertTrue(slugs.contains("eng.1"));
        assertTrue(slugs.contains("por.1"));
        assertTrue(slugs.contains("fifa.world"));
        assertTrue(Following.international("fifa.world"));
        assertTrue(Following.international("uefa.nations"));
        assertFalse(Following.international("uefa.champions"));
        assertFalse(Following.international("eng.1"));
    }

    @Test
    public void competitionsSwitchedOnAreReadAsTheirOwnEntries() {
        assertEquals(Arrays.asList("por.1|LIGA PORTUGAL", "usa.1|MLS"), Following.entries(Arrays.asList("por.1", "usa.1", "por.1", " ", null)));
        assertTrue(Following.entries(Collections.emptyList()).isEmpty());
    }

    @Test
    public void aStarredClubsLeagueAndTheConferenceLeagueAreRead() {
        List<String> e = Following.clubEntries(Arrays.asList("por.1", "", null, "eng.1"), true);
        assertEquals(Arrays.asList("por.1|LIGA PORTUGAL", "eng.1|PL", "uefa.europa.conf|UECL"), e);
        // A national side: the World Cup and friendlies as well.
        List<String> n = Following.clubEntries(Collections.singletonList("fifa.worldq.afc"), true);
        assertTrue(n.contains("fifa.world|WORLD CUP"));
        assertTrue(n.contains("fifa.friendly|FRIENDLY"));
        assertTrue(n.contains("fifa.worldq.afc|FIFA.WORLDQ.AFC"));
        // No club starred: nothing.
        assertTrue(Following.clubEntries(Collections.emptyList(), false).isEmpty());
    }

    @Test
    public void aCompetitionSwitchedOffSaysWhatIsStillFollowedInIt() {
        assertTrue(Following.offWords("eng.1", true).contains("nothing"));
        assertTrue(Following.offWords("por.1", false).contains("your clubs"));
        assertTrue(Following.offWords("fifa.world", false).contains("England"));
    }

    @Test
    public void aLeaguesClubsAreReadAToZWithTheirCrests() {
        String body = "{\"sports\":[{\"leagues\":[{\"teams\":["
                + "{\"team\":{\"id\":\"1929\",\"displayName\":\"Benfica\",\"abbreviation\":\"BEN\",\"logos\":[{\"href\":\"https://e/1929.png\"}]}},"
                + "{\"team\":{\"id\":\"437\",\"displayName\":\"FC Porto\",\"abbreviation\":\"POR\"}},"
                + "{\"team\":{\"id\":\"\",\"displayName\":\"Nobody\"}},"
                + "{\"team\":{\"id\":\"2250\",\"displayName\":\"Braga\",\"abbreviation\":\"BRA\",\"logos\":[]}}]}]}]}";
        List<SportsData.Team> clubs = SportsData.parseClubs(body, "por.1");
        assertEquals(3, clubs.size());
        assertEquals("Benfica", clubs.get(0).name);
        assertEquals("Braga", clubs.get(1).name);
        assertEquals("FC Porto", clubs.get(2).name);
        assertEquals("https://e/1929.png", clubs.get(0).logo);
        assertEquals("por.1", clubs.get(0).league);
        assertEquals("1929", clubs.get(0).id);
        assertTrue(SportsData.parseClubs("not json", "por.1").isEmpty());
        assertTrue(SportsData.parseClubs("{}", "por.1").isEmpty());
    }

    @Test
    public void aClubsCrestByItsIdWhenNothingBetterIsKnown() {
        assertEquals("https://a.espncdn.com/i/teamlogos/soccer/500/360.png", Following.crestUrl("360"));
    }
}
