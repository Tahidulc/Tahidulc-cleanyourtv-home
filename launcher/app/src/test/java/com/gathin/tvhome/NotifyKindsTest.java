package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;

public class NotifyKindsTest {
    @Test
    public void idsAreUniqueAndEveryKindIsInAGroup() {
        Set<String> ids = new HashSet<>();
        for (NotifyKinds.Kind k : NotifyKinds.ALL) {
            assertTrue("duplicate id " + k.id, ids.add(k.id));
            assertTrue(k.id + " has no group", NotifyKinds.GROUPS.contains(k.group));
            assertFalse(k.name.isEmpty());
            assertFalse(k.line.isEmpty());
            assertFalse(k.icon.isEmpty());
        }
        assertFalse(ids.contains(NotifyKinds.ALWAYS));
        for (String g : NotifyKinds.GROUPS) {
            boolean any = false;
            for (NotifyKinds.Kind k : NotifyKinds.ALL) any |= g.equals(k.group);
            assertTrue(g + " is empty", any);
        }
    }

    @Test
    public void everyCardKindMapsToAListedKind() {
        for (String kind : new String[]{"goal", "red", "ft", "var", "pen", "kickoff", "reminder", "fight", "ht", "table",
                "milestone", "prediction", "release"}) {
            assertNotNull(kind, NotifyKinds.get(NotifyKinds.ofCard(null, kind, false)));
        }
        assertEquals("f1live", NotifyKinds.ofCard(null, "f1", false));
        assertEquals("f1result", NotifyKinds.ofCard(null, "f1", true));
        assertEquals("tennis", NotifyKinds.ofCard("tennis", "notice", false));
        assertNull(NotifyKinds.ofCard(null, "notice", false));
    }

    @Test
    public void everySoundMapsToAListedKind() {
        for (String sound : new String[]{"goal", "red", "fulltime", "f1", "news", "fight", "tennis", "weather", "message", "reminder"}) {
            assertNotNull(sound, NotifyKinds.get(NotifyKinds.ofSound(sound)));
        }
        assertNull(NotifyKinds.ofSound("chime"));
    }

    @Test
    public void kindsSharingAThemeSwitchAreFoundByIt() {
        assertEquals(3, NotifyKinds.byFlag("rich_alerts").size());
        assertEquals(2, NotifyKinds.byFlag("f1_live_alerts").size());
        assertNull(NotifyKinds.byFlag("sky_alerts"));
        assertNull(NotifyKinds.byFlag(null));
    }

    @Test
    public void competitionsAreOnUntilSwitchedOffButFriendlies() {
        Set<String> slugs = new HashSet<>();
        for (NotifyKinds.Competition x : NotifyKinds.COMPETITIONS) {
            assertTrue("duplicate slug " + x.slug, slugs.add(x.slug));
            assertFalse(x.name.isEmpty());
            assertFalse(x.line.isEmpty());
            assertFalse(x.icon.isEmpty());
            assertEquals(x.slug, !"fifa.friendly".equals(x.slug), NotifyKinds.competitionOn(x.slug, null));
        }
        // The friendlies: off until switched on.
        assertFalse(NotifyKinds.competitionOn("fifa.friendly", null));
        assertTrue(NotifyKinds.competitionOn("fifa.friendly", true));
        // A competition switched off, and back on.
        assertFalse(NotifyKinds.competitionOn("eng.1", false));
        assertTrue(NotifyKinds.competitionOn("eng.1", true));
        assertFalse(NotifyKinds.competitionOn("uefa.champions", false));
        // One the list does not name: on, until switched off. No competition known: always on.
        assertTrue(NotifyKinds.competitionOn("usa.1", null));
        assertFalse(NotifyKinds.competitionOn("usa.1", false));
        assertTrue(NotifyKinds.competitionOn(null, false));
        assertEquals("Premier League", NotifyKinds.competitionName("eng.1"));
        assertEquals("Friendlies", NotifyKinds.competitionName("fifa.friendly"));
        assertEquals("usa.1", NotifyKinds.competitionName("usa.1"));
        assertNull(NotifyKinds.competition("usa.1"));
        assertFalse(NotifyKinds.GROUPS.contains(NotifyKinds.COMPETITION_GROUP));
    }

    @Test
    public void onlyFootballIsHeldByItsCompetition() {
        for (NotifyKinds.Kind k : NotifyKinds.ALL) {
            assertEquals(k.id, NotifyKinds.FOOTBALL.equals(k.group), NotifyKinds.byCompetition(k.id));
        }
        assertFalse(NotifyKinds.byCompetition(NotifyKinds.ALWAYS));
        assertFalse(NotifyKinds.byCompetition("reminders"));
        assertFalse(NotifyKinds.byCompetition("f1live"));
        assertFalse(NotifyKinds.byCompetition("tennis"));
        // A football line of no listed kind (the older alerts' VAR) still has its game's competition.
        assertTrue(NotifyKinds.byCompetition(null));
    }

    @Test
    public void everyCompetitionTheAlertsReadIsListed() throws Exception {
        // GoalWatchService's own line-up competitions (England's internationals and the Supercopa).
        for (String slug : new String[]{"fifa.friendly", "uefa.nations", "fifa.worldq.uefa", "uefa.euroq", "fifa.world",
                "uefa.euro", "esp.super_cup"}) {
            assertNotNull(slug, NotifyKinds.competition(slug));
        }
        File xml = new File("src/main/res/values/config.xml");
        if (!xml.isFile()) xml = new File("app/src/main/res/values/config.xml");
        Assume.assumeTrue(xml.isFile());
        String all = new String(Files.readAllBytes(xml.toPath()), StandardCharsets.UTF_8);
        for (String array : new String[]{"goal_alert_leagues", "score_leagues", "team_alert_leagues"}) {
            Matcher m = Pattern.compile("<string-array name=\"" + array + "\"[^>]*>(.*?)</string-array>", Pattern.DOTALL).matcher(all);
            assertTrue(array, m.find());
            Matcher item = Pattern.compile("<item>([^<|]+)").matcher(m.group(1));
            int n = 0;
            while (item.find()) {
                n++;
                assertNotNull(array + ": " + item.group(1), NotifyKinds.competition(item.group(1).trim()));
            }
            assertTrue(array, n > 0);
        }
    }
}
