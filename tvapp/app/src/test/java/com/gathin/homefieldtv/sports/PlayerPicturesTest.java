package com.gathin.homefieldtv.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gathin.homefieldtv.engine.Exec;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The family's change to the players' pictures (07): each card's picture is a real one, the best there is, in the
 * phone's order (the league's own cut-out for a Premier League or La Liga club, ESPN's head and shoulders, then FotMob's
 * face), none at all leaving the crest and his initials; ESPN's matched to FotMob's players as the phone matches them;
 * whose league pictures a club has (by the competition it was met in, or FotMob's main league for it); a lookup kept on
 * the player, nothing fetched in tests. With them the table's zone notes and the championship's wins and round.
 */
public class PlayerPicturesTest {
    @After public void after() {
        Exec.inline = false;
        LeaguePhotos.GIVEN.clear();
    }

    private static Squad.Person person(String name, String number) {
        Squad.Person p = new Squad.Person();
        p.name = name;
        String[] s = Squad.split(name);
        p.first = s[0];
        p.last = s[1];
        p.number = number;
        p.line = Squad.MID;
        return p;
    }

    @Test public void theBestRealPictureFirstAndNeverADrawnOne() {
        Squad.Person p = person("Tom Waverley", "1");
        p.photo = "https://images.fotmob.com/image_resources/playerimages/601.png";
        p.face = true;
        // FotMob's face alone, until there is better.
        assertArrayEquals(new String[]{p.photo}, Squad.photos(p));
        // ESPN's head and shoulders before it.
        p.espn = "https://a.espncdn.com/i/headshots/soccer/players/full/9001.png";
        assertArrayEquals(new String[]{p.espn, p.photo}, Squad.photos(p));
        // The league's own cut-out before both, once looked up; "" (none) leaves them.
        p.league = "";
        assertArrayEquals(new String[]{p.espn, p.photo}, Squad.photos(p));
        p.league = "https://resources.premierleague.com/premierleague25/photos/players/500x500/12345.png";
        assertArrayEquals(new String[]{p.league, p.espn, p.photo}, Squad.photos(p));
        // ESPN's roster as the squad: its own head and shoulders.
        Squad.Person e = person("Sol Ibe", "11");
        e.photo = "https://a.espncdn.com/i/headshots/soccer/players/full/9002.png";
        e.espn = e.photo;
        assertArrayEquals(new String[]{e.photo}, Squad.photos(e));
        // No picture at all: none to try (the card shows the crest and his initials).
        assertEquals(0, Squad.photos(person("Ainsley Maitland", "2")).length);
        assertEquals("AM", Squad.initials("Ainsley Maitland"));
        assertEquals("K", Squad.initials("Kettering"));
        assertEquals("ZC", Squad.initials(" Zé Carlos "));
        assertEquals("", Squad.initials(""));
    }

    @Test public void eachPicturesKindByWhereItIs() {
        assertEquals(Squad.LEAGUE, Squad.kind("https://resources.premierleague.com/premierleague25/photos/players/500x500/1.png"));
        assertEquals(Squad.LEAGUE, Squad.kind("file:/data/cache/" + LeaguePhotos.LA_LIGA_DIR + "/a1b2.webp"));
        assertEquals(Squad.SHOULDERS, Squad.kind("https://a.espncdn.com/i/headshots/soccer/players/full/9001.png"));
        assertEquals(Squad.HEAD, Squad.kind("https://images.fotmob.com/image_resources/playerimages/601.png"));
        assertEquals(Squad.NONE, Squad.kind(""));
        assertEquals(Squad.NONE, Squad.kind(null));
    }

    @Test public void espnsHeadAndShouldersMatchedToFotMobsPlayersAsThePhoneDoes() {
        List<Squad.Person> fotmob = new ArrayList<>(), espn = new ArrayList<>();
        Squad.Person a = person("Alfie Brennan", "8"), b = person("Kai Thorsen", "9"), c = person("João Cancelo", "20"),
                d = person("Tom Waverley", "1"), boss = person("Erik Holmgren", "");
        boss.line = Squad.STAFF;
        fotmob.add(a);
        fotmob.add(b);
        fotmob.add(c);
        fotmob.add(d);
        fotmob.add(boss);
        Squad.Person ea = person("Alfie Brennan", "8"), eb = person("K. Thorsen", "9"), ec = person("Joao Cancelo", "20"),
                ed = person("Thomas Waverley", "1"), eBoss = person("Erik Holmgren", "");
        ea.photo = "https://a.espncdn.com/i/headshots/soccer/players/full/1.png";
        eb.photo = "https://a.espncdn.com/i/headshots/soccer/players/full/2.png";
        ec.photo = "https://a.espncdn.com/i/headshots/soccer/players/full/3.png";
        ed.photo = "https://a.espncdn.com/i/headshots/soccer/players/full/4.png";
        eBoss.photo = "https://a.espncdn.com/i/headshots/soccer/players/full/5.png";
        espn.add(ea);
        espn.add(eb);
        espn.add(ec);
        espn.add(ed);
        espn.add(eBoss);
        Squad.merge(fotmob, espn);
        assertEquals(ea.photo, a.espn);
        assertEquals("an initial and the surname", eb.photo, b.espn);
        assertEquals("accents aside", ec.photo, c.espn);
        assertEquals("the same number and surname", ed.photo, d.espn);
        assertEquals("the manager's picture is never ESPN's", "", boss.espn);
        assertTrue(Squad.sameName("Alex Iwobi", "Alexander Iwobi"));
        assertFalse(Squad.sameName("Ben Davies", "Keston Davies"));
        assertFalse(Squad.sameName("Ben Davies", "Ben Godfrey"));
        // A namesake with another number: only when nobody of the same number matches.
        Squad.Person x = person("Ben Davies", "33");
        Squad.Person y = person("Ben Davies", "4"), z = person("Ben Davies", "33");
        List<Squad.Person> two = new ArrayList<>();
        two.add(y);
        two.add(z);
        assertSame(z, Squad.match(x, two));
    }

    @Test public void whoseOwnPicturesAClubHas() {
        assertEquals(LeaguePhotos.PREMIER, LeaguePhotos.league("eng.1", 0));
        assertEquals(LeaguePhotos.LA_LIGA, LeaguePhotos.league("esp.1", 0));
        assertEquals("a Premier League club met in the Champions League", LeaguePhotos.PREMIER, LeaguePhotos.league("uefa.champions", 47));
        assertEquals(LeaguePhotos.LA_LIGA, LeaguePhotos.league("uefa.europa", 87));
        assertEquals(LeaguePhotos.NONE, LeaguePhotos.league("ita.1", 55));
        assertEquals(LeaguePhotos.NONE, LeaguePhotos.league("uefa.nations", 0));
        assertEquals(LeaguePhotos.NONE, LeaguePhotos.league(null, 0));
    }

    @Test public void aLookupIsKeptOnThePlayerAndNothingIsFetchedInTests() throws Exception {
        Exec.inline = true;
        Squad.Person p = person("Thierno Barre", "11"), q = person("Tyler Dibden", "20"), boss = person("David Moyle", "");
        boss.line = Squad.STAFF;
        LeaguePhotos.GIVEN.put("Thierno Barre", "file:/previews/premierleague.com/p11.png");
        assertTrue(LeaguePhotos.pending(LeaguePhotos.PREMIER, p));
        assertFalse("no league pictures in Serie A", LeaguePhotos.pending(LeaguePhotos.NONE, p));
        assertFalse("a manager has none", LeaguePhotos.pending(LeaguePhotos.PREMIER, boss));
        int[] told = {0};
        LeaguePhotos.lookUp(null, LeaguePhotos.PREMIER, "Harbour Town", "Harbour", p, () -> told[0]++);
        LeaguePhotos.lookUp(null, LeaguePhotos.PREMIER, "Harbour Town", "Harbour", q, () -> told[0]++);
        assertEquals(2, told[0]);
        assertEquals("file:/previews/premierleague.com/p11.png", p.league);
        assertEquals("none for him: kept, not asked again", "", q.league);
        assertFalse(LeaguePhotos.pending(LeaguePhotos.PREMIER, p));
        assertEquals(Squad.LEAGUE, Squad.kind(Squad.photos(p)[0]));
        assertNull(boss.league);
        // FotMob's main league comes with its squad.
        Squad s = Squad.fotmob(new JSONObject("{\"details\":{\"name\":\"Harbour Town\",\"primaryLeagueId\":47},\"squad\":{\"squad\":[]}}"));
        assertEquals(47, s.fotmobLeague);
        assertEquals(0, Squad.fotmob(new JSONObject("{\"details\":{\"name\":\"X\"}}")).fotmobLeague);
    }

    @Test public void aTablesZonesFromEspnsNotes() throws Exception {
        String standings = "{\"children\":[{\"standings\":{\"entries\":["
                + "{\"team\":{\"id\":\"30\",\"displayName\":\"Harbour Town\"},\"note\":{\"color\":\"#81D6AC\",\"description\":\"Champions League\",\"rank\":1},"
                + "\"stats\":[{\"name\":\"rank\",\"displayValue\":\"1\"}]},"
                + "{\"team\":{\"id\":\"31\",\"displayName\":\"Riverside\"},\"stats\":[{\"name\":\"rank\",\"displayValue\":\"2\"}]}]}}]}";
        List<String[]> t = Espn.table(new JSONObject(standings));
        assertEquals("Champions League", t.get(0)[Espn.T_NOTE]);
        assertEquals("", t.get(1)[Espn.T_NOTE]);
    }

    @Test public void theChampionshipsWinsAndRound() throws Exception {
        String standings = "{\"MRData\":{\"StandingsTable\":{\"season\":\"2026\",\"StandingsLists\":[{\"season\":\"2026\",\"round\":\"20\","
                + "\"DriverStandings\":[{\"position\":\"1\",\"points\":\"390\",\"wins\":\"9\",\"Driver\":{\"driverId\":\"leo\",\"givenName\":\"Leo\","
                + "\"familyName\":\"Marchetti\"},\"Constructors\":[]},{\"position\":\"2\",\"points\":\"279\",\"wins\":\"many\",\"Driver\":"
                + "{\"driverId\":\"finn\",\"givenName\":\"Finn\",\"familyName\":\"Halvorsen\"},\"Constructors\":[]}]}]}}}";
        List<F1.Standing> d = F1.drivers(new JSONObject(standings));
        assertEquals("9", d.get(0).wins);
        assertEquals("20", d.get(0).round);
        assertEquals("not a number: left out", "", d.get(1).wins);
    }
}
