package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.sports.Espn;
import com.gathin.homefieldtv.sports.F1;
import com.gathin.homefieldtv.sports.Game;
import com.gathin.homefieldtv.sports.Matching;
import com.gathin.homefieldtv.sports.SportsData;
import com.gathin.homefieldtv.sports.Squad;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The sports pages with the remote: a club's row of cards (a press a player, a held key gliding on, the ends), its pills
 * (Up to them, Left and Right picking, Down back, Back), a player's page through the same filter and back on him, the
 * management's one card, an F1 driver's page through the championship; the hub's sections, a game's clubs and a table's
 * club opening their pages; the game's page up to its sides. With them the club colours white must read on, and the
 * driver page's dates, countdown and age.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class ClubPagesTest {
    private ActivityController<TvActivity> controller;
    private TvActivity a;

    @Before public void before() {
        Exec.inline = true;
        controller = Robolectric.buildActivity(TvActivity.class).setup();
        a = controller.get();
        idle();
    }

    @After public void after() {
        if (controller != null) controller.pause().stop().destroy();
        Exec.inline = false;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void press(int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    /** A key held: its press and then repeats, as the remote sends them, let go at the end. */
    private void hold(int code, int repeats) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        for (int i = 1; i <= repeats; i++) a.dispatchKeyEvent(new KeyEvent(t, t + 400 + i * 50L, KeyEvent.ACTION_DOWN, code, i));
        a.dispatchKeyEvent(new KeyEvent(t, t + 500 + repeats * 50L, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    private static Squad.Person person(String name, String number, int line) {
        Squad.Person p = new Squad.Person();
        p.id = String.valueOf(1000 + name.length() * 7 + number.hashCode());
        p.name = name;
        String[] s = Squad.split(name);
        p.first = s[0];
        p.last = s[1];
        p.number = number;
        p.line = line;
        p.position = Squad.LINE[line];
        return p;
    }

    /** An invented squad, no pictures (each card's photo drawn). */
    private static Squad squad() {
        Squad s = new Squad();
        s.name = "Harbour Town";
        s.colour = 0xFF1E3FA8;
        String[][] who = {{"Tom Waverley", "1", "0"}, {"Mateo Lind", "13", "0"}, {"Kofi Mensah", "2", "1"}, {"Luca Bertolini", "4", "1"},
                {"Ruben de Vlaar", "5", "1"}, {"Pierre Lacasse", "6", "2"}, {"Alfie Brennan", "8", "2"}, {"Rafael Moreira", "10", "2"},
                {"Elias Varga", "7", "3"}, {"Kai Thorsen", "9", "3"}};
        for (String[] w : who) s.players.add(person(w[0], w[1], Integer.parseInt(w[2])));
        s.staff.add(person("Erik Holmgren", "", Squad.STAFF));
        return s;
    }

    private TeamScreen openTeam() {
        Squad.Club club = new Squad.Club();
        club.name = "Harbour Town";
        club.colour = "1E3FA8";
        TeamScreen t = new TeamScreen(a, club);
        a.push(t);
        idle();
        t.preview(squad());
        idle();
        return t;
    }

    @Test public void theRowGlidesAPlayerAPressHeldKeysGlideOnAndItStopsAtItsEnds() {
        TeamScreen t = openTeam();
        assertEquals(10, t.shown.size());
        assertEquals(0, t.focus);
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(0, t.focus);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1, t.focus);
        assertEquals(1f, t.pos.target(), 0f);
        hold(KeyEvent.KEYCODE_DPAD_RIGHT, 4);
        assertEquals(6, t.focus);
        hold(KeyEvent.KEYCODE_DPAD_RIGHT, 20);
        assertEquals(9, t.focus);
        assertEquals(9f, t.pos.target(), 0f);
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(8, t.focus);
        assertSame(t, a.top());
    }

    @Test public void upToThePillsLeftAndRightPickDownComesBack() {
        TeamScreen t = openTeam();
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(t.onPills);
        assertEquals(TeamScreen.P_ALL, t.pill);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(Squad.GK, t.line);
        assertEquals(2, t.shown.size());
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(Squad.DEF, t.line);
        assertEquals(3, t.shown.size());
        for (Squad.Person p : t.shown) assertEquals(Squad.DEF, p.line);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertFalse(t.onPills);
        assertEquals(0, t.focus);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        // Up lands on the pill chosen; Left along the pills picks each it passes, to Management: the staff, one card.
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(TeamScreen.P_ALL + 1 + Squad.DEF, t.pill);
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(Squad.GK, t.line);
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(-1, t.line);
        assertEquals(10, t.shown.size());
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(TeamScreen.P_STAFF, t.pill);
        assertTrue(t.staff);
        assertEquals(1, t.shown.size());
        assertEquals("Erik Holmgren", t.shown.get(0).name);
        // Players brings back the players, every line (the last one picked); Left stops at the first pill.
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertFalse(t.staff);
        assertEquals(-1, t.line);
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(TeamScreen.P_PLAYERS, t.pill);
        // OK picks and comes down.
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(t.onPills);
        // Back from the pills comes down to the cards; Back from the cards closes the page.
        press(KeyEvent.KEYCODE_DPAD_UP);
        press(KeyEvent.KEYCODE_BACK);
        assertFalse(t.onPills);
        assertSame(t, a.top());
        press(KeyEvent.KEYCODE_BACK);
        assertFalse(a.top() == t);
    }

    @Test public void okOpensThePlayersPageWhichGoesThroughTheSameFilter() {
        TeamScreen t = openTeam();
        t.pick(TeamScreen.P_ALL + 1 + Squad.MID);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals("Alfie Brennan", t.focused().name);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof PlayerScreen);
        PlayerScreen p = (PlayerScreen) a.top();
        assertEquals(3, p.list.size());
        assertEquals("Alfie Brennan", p.person().name);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals("Rafael Moreira", p.person().name);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals("Rafael Moreira", p.person().name);
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals("Pierre Lacasse", p.person().name);
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals("Pierre Lacasse", p.person().name);
        press(KeyEvent.KEYCODE_BACK);
        assertSame(t, a.top());
        assertEquals("the club's row is on him", "Pierre Lacasse", t.focused().name);
    }

    /** Invented drivers whose teams formula1.com has no folder for: no picture is asked of anyone. */
    private static List<F1.Standing> drivers() {
        List<F1.Standing> out = new ArrayList<>();
        String[][] d = {{"leo_m", "Leo", "Marchetti", ""}, {"finn_h", "Finn", "Halvorsen", ""}, {"sami_o", "Sami", "Okoro", ""}};
        for (int i = 0; i < d.length; i++) {
            F1.Standing st = new F1.Standing();
            st.driverId = d[i][0];
            st.given = d[i][1];
            st.family = d[i][2];
            st.name = d[i][1] + " " + d[i][2];
            st.teamId = d[i][3];
            st.team = "Team " + i;
            st.position = String.valueOf(i + 1);
            st.points = String.valueOf(300 - i * 20);
            st.season = 2026;
            out.add(st);
        }
        return out;
    }

    @Test public void theDriversPageStepsThroughTheChampionship() {
        List<F1.Standing> list = drivers();
        DriverScreen ds = new DriverScreen(a, list, 0, null);
        // Drawn stand-ins (no picture is asked for): none.
        for (F1.Standing st : list) ds.previewPictures.put(st, new String[]{null, null, null, null});
        a.push(ds);
        idle();
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(0, ds.at);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1, ds.at);
        assertEquals(1f, ds.pos.target(), 0f);
        hold(KeyEvent.KEYCODE_DPAD_RIGHT, 5);
        assertEquals(2, ds.at);
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals("Finn Halvorsen", ds.driver().name);
        press(KeyEvent.KEYCODE_BACK);
        assertFalse(a.top() == ds);
    }

    private static List<SportsData.Item> today() {
        List<SportsData.Item> out = new ArrayList<>();
        SportsData.Item it = new SportsData.Item();
        it.kind = SportsData.FOOTBALL;
        it.key = "football:900";
        it.home = "Harbour Town";
        it.away = "Riverside";
        it.comp = "Coastal League";
        it.live = true;
        Game g = new Game();
        g.id = "900";
        g.slug = "eng.1";
        g.homeId = "30";
        g.awayId = "31";
        g.homeName = "Harbour Town";
        g.awayName = "Riverside";
        g.awayOwn = "c8102e";
        g.state = Game.LIVE;
        it.game = g;
        out.add(it);
        SportsData.Item nba = new SportsData.Item();
        nba.kind = SportsData.NBA;
        nba.key = "nba:1";
        nba.home = "North";
        nba.away = "South";
        out.add(nba);
        return out;
    }

    @Test public void theHubsSectionsOpenTheClubsAndTheDrivers() {
        SportsScreen hub = new SportsScreen(a);
        a.push(hub);
        idle();
        hub.preview(today(), new HashMap<String, List<Matching.Row>>());
        // Today: Right goes on to the game's clubs, Left and Right between them, OK opens the club's page.
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(SportsScreen.SIDES, hub.zone);
        assertEquals(0, hub.side);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1, hub.side);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof TeamScreen);
        TeamScreen away = (TeamScreen) a.top();
        assertEquals("31", away.club.espnId);
        assertEquals("c8102e", away.club.colour);
        press(KeyEvent.KEYCODE_BACK);
        assertSame(hub, a.top());
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(SportsScreen.LIST, hub.zone);
        // A game of another sport has no clubs' pages to go on to.
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(SportsScreen.LIST, hub.zone);
        press(KeyEvent.KEYCODE_DPAD_UP);
        // Up from the top of the list: the pills; Right picks Tables, Down goes to its leagues.
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(SportsScreen.PILLS, hub.zone);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(SportsScreen.TABLES, hub.section);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"1", "30", "Harbour Town", "", "13", "+12", "30", "", "10", "0", "3", "Harbour Town"});
        rows.add(new String[]{"2", "31", "Riverside", "", "13", "+10", "28", "", "9", "1", "3", "Riverside Athletic"});
        List<String[]> teams = new ArrayList<>();
        teams.add(new String[]{"31", "Riverside Athletic", "", "c8102e", "ffffff"});
        hub.previewTable(Espn.TABLES[0][0], rows, teams);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(SportsScreen.LEAGUES, hub.zone);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(SportsScreen.TABLE, hub.zone);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof TeamScreen);
        TeamScreen club = (TeamScreen) a.top();
        assertEquals("31", club.club.espnId);
        assertEquals("Riverside Athletic", club.club.fullName);
        assertEquals("c8102e", club.club.colour);
        press(KeyEvent.KEYCODE_BACK);
        // Back from the table goes to the leagues; Up from the top of them, the pills; Right, Formula 1.
        press(KeyEvent.KEYCODE_BACK);
        assertEquals(SportsScreen.LEAGUES, hub.zone);
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(SportsScreen.PILLS, hub.zone);
        hub.previewF1(drivers(), null);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(SportsScreen.FORMULA, hub.section);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(SportsScreen.DRIVERS, hub.zone);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(1, hub.driver);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof DriverScreen);
        assertEquals("Finn Halvorsen", ((DriverScreen) a.top()).driver().name);
    }

    @Test public void theGamesPageGoesUpToItsSides() {
        SportsData.Item it = today().get(0);
        GameScreen g = new GameScreen(a, it, new ArrayList<>());
        a.push(g);
        idle();
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(0, g.side);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1, g.side);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(-1, g.side);
        press(KeyEvent.KEYCODE_DPAD_UP);
        press(KeyEvent.KEYCODE_BACK);
        assertEquals(-1, g.side);
        assertSame(g, a.top());
        press(KeyEvent.KEYCODE_DPAD_UP);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof TeamScreen);
        assertEquals("30", ((TeamScreen) a.top()).club.espnId);
    }

    @Test public void clubColoursCarryWhiteWriting() {
        int[] colours = {0xFFFFFFFF, 0xFF000000, 0xFFFFCD00, 0xFF99C5EA, 0xFFE20520, 0xFF0B1426, 0xFF27F4D2, 0xFFFF8000, 0xFF0000FF, 0};
        for (int c : colours) {
            assertTrue("ground for " + Integer.toHexString(c), ClubLook.whiteContrast(ClubLook.ground(c)) >= 7.0);
            assertTrue("card for " + Integer.toHexString(c), ClubLook.whiteContrast(ClubLook.card(c)) >= 7.0);
            assertTrue("coin for " + Integer.toHexString(c), ClubLook.whiteContrast(ClubLook.coin(c)) >= 4.5);
        }
        // A white club's page takes its navy; a club's own hue comes before the other source's.
        assertEquals(0xFF0B1426, ClubLook.pick(0xFFFFFFFF, 0xFF0B1426, 0));
        assertEquals(0xFFE20520, ClubLook.pick(0xFFE20520, 0xFF003399, 0xFFBD0510));
        assertEquals(0xFFBD0510, ClubLook.pick(0xFFFFFFFF, 0xFFFFFFFF, 0xFFBD0510));
        assertEquals(0xFF000000, ClubLook.pick(0xFF000000, 0, 0));
        assertEquals(0, ClubLook.pick(0, 0, 0));
        // The ground keeps the club's hue.
        float[] hsv = new float[3];
        Cine.hsv(ClubLook.ground(0xFFE20520), hsv);
        assertTrue(hsv[0] < 15f || hsv[0] > 345f);
    }

    @Test public void theSportsPagesAreIn07sShellWithTheirBackLines() {
        SportsScreen hub = new SportsScreen(a);
        a.fromHome(hub);
        idle();
        hub.preview(today(), new HashMap<String, List<Matching.Row>>());
        assertTrue(hub.shell());
        assertEquals(TabBar.SPORTS, hub.tab());
        assertEquals(TabBar.SPORTS, a.tabs().page());
        assertTrue(a.tabs().drawn());
        assertEquals("Sports · Today", hub.backWords());
        // The game's page and, from it, a club's: each with its "‹ back" line where Back goes.
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof GameScreen);
        assertTrue(a.top().shell());
        assertEquals("Sports · Today", a.tabs().backWords());
        press(KeyEvent.KEYCODE_DPAD_UP);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof TeamScreen);
        assertEquals("Harbour Town v Riverside", a.tabs().backWords());
        TeamScreen club = (TeamScreen) a.top();
        club.preview(squad());
        idle();
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof PlayerScreen);
        assertEquals("the club's page", "Harbour Town", a.tabs().backWords());
        press(KeyEvent.KEYCODE_BACK);
        press(KeyEvent.KEYCODE_BACK);
        // The game's page: Back comes down from its side first, then closes it.
        press(KeyEvent.KEYCODE_BACK);
        press(KeyEvent.KEYCODE_BACK);
        assertSame(hub, a.top());
        assertNull(a.tabs().backWords());
        DriverScreen ds = new DriverScreen(a, drivers(), 0, null);
        for (F1.Standing st : ds.drivers) ds.previewPictures.put(st, new String[]{null, null, null, null});
        a.push(ds);
        idle();
        assertEquals("Sports · Formula 1", a.tabs().backWords());
    }

    @Test public void backAtTheHubsRootAndUpFromItsPillsGoToTheTabBar() {
        SportsScreen hub = new SportsScreen(a);
        a.fromHome(hub);
        idle();
        hub.preview(today(), new HashMap<String, List<Matching.Row>>());
        assertFalse(a.tabs().active());
        press(KeyEvent.KEYCODE_BACK);
        assertTrue("Back at the list's root: the tab bar", a.tabs().active());
        assertSame(hub, a.top());
        // Down from the bar: the sections' pills; Down again the list; Up and Up the bar.
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertFalse(a.tabs().active());
        assertEquals(SportsScreen.PILLS, hub.zone);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(SportsScreen.LIST, hub.zone);
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(SportsScreen.PILLS, hub.zone);
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(a.tabs().active());
    }

    @Test public void aPremierLeagueClubsPicturesAreLookedUpAsTheirCardsComeNear() {
        Squad.Club club = new Squad.Club();
        club.slug = "eng.1";
        club.name = "Harbour Town";
        club.fullName = "Harbour Town";
        com.gathin.homefieldtv.sports.LeaguePhotos.GIVEN.put("Kofi Mensah", "file:/previews/premierleague.com/p2.png");
        try {
            TeamScreen t = new TeamScreen(a, club);
            a.push(t);
            idle();
            Squad s = squad();
            t.preview(s);
            idle();
            assertEquals(com.gathin.homefieldtv.sports.LeaguePhotos.PREMIER, t.league);
            // The cards in sight and those either side are looked up (none further away yet).
            assertEquals("file:/previews/premierleague.com/p2.png", s.players.get(2).league);
            assertEquals("", s.players.get(0).league);
            assertNull(s.players.get(9).league);
            assertEquals(Squad.LEAGUE, Squad.kind(Squad.photos(s.players.get(2))[0]));
            for (int i = 0; i < 9; i++) press(KeyEvent.KEYCODE_DPAD_RIGHT);
            assertEquals("", s.players.get(9).league);
        } finally {
            com.gathin.homefieldtv.sports.LeaguePhotos.GIVEN.clear();
        }
    }

    @Test public void noDrawnPersonIsLeftToStandInForAPicture() {
        // The family's change: no shirt under a face, no head and shoulders, no figure; only an F1 car may be drawn.
        for (java.lang.reflect.Method m : ClubLook.class.getDeclaredMethods()) {
            String n = m.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(m.getName(), n.contains("bust") || n.contains("shirt") || n.contains("collar") || n.equals("kit")
                    || n.contains("standing") || n.contains("figure") || n.contains("silhouette"));
        }
    }

    @Test public void theHubsColoursZonesAndCapitals() {
        assertTrue("Northland's red and Marisca's red", SportsScreen.clash(0xFFC8102E, 0xFFD7263D));
        assertFalse(SportsScreen.clash(0xFFC8102E, 0xFF1F7A3A));
        assertEquals(0xFF3F8CFF, SportsScreen.zone("Champions League"));
        assertEquals(0xFF3F8CFF, SportsScreen.zone("Promotion"));
        assertEquals(0xFFFF8A3D, SportsScreen.zone("Europa League"));
        assertEquals(0xFFFF8A3D, SportsScreen.zone("Promotion Playoff"));
        assertEquals(0xFFFF453A, SportsScreen.zone("Relegation"));
        assertEquals(0xFFFF453A, SportsScreen.zone("Relegation Playoff"));
        assertEquals(0, SportsScreen.zone(""));
        assertArrayEquals(new String[]{"NATIONS", "LEAGUE"}, SportsScreen.tag("UEFA Nations League"));
        assertArrayEquals(new String[]{"FRIENDLY"}, SportsScreen.tag("International Friendly"));
        assertArrayEquals(new String[]{"PREMIER", "LEAGUE"}, SportsScreen.tag("Premier League"));
        assertArrayEquals(new String[]{"NBA"}, SportsScreen.tag("NBA"));
        assertArrayEquals(new String[]{"WORLD CUP", "QUALIFYING"}, SportsScreen.tag("FIFA World Cup Qualifying"));
    }

    @Test public void theDriversPagesDatesCountdownAndAge() {
        ZoneId utc0 = ZoneId.of("UTC");
        long fri0 = java.time.Instant.parse("2026-11-20T11:30:00Z").toEpochMilli(), sun0 = java.time.Instant.parse("2026-11-22T14:00:00Z").toEpochMilli();
        assertEquals("Fri 20 – Sun 22 Nov", DriverScreen.days(fri0, sun0, utc0));
        assertEquals("Fri 30 Oct – Sun 1 Nov", DriverScreen.days(java.time.Instant.parse("2026-10-30T11:30:00Z").toEpochMilli(),
                java.time.Instant.parse("2026-11-01T14:00:00Z").toEpochMilli(), utc0));
        assertEquals("Sun 22 Nov", DriverScreen.days(0, sun0, utc0));
        assertEquals("", DriverScreen.days(fri0, 0, utc0));
        theDriversDatesCountdownAndAge();
    }

    private void theDriversDatesCountdownAndAge() {
        ZoneId utc = ZoneId.of("UTC");
        long fri = java.time.Instant.parse("2026-11-20T11:30:00Z").toEpochMilli(), sun = java.time.Instant.parse("2026-11-22T14:00:00Z").toEpochMilli();
        assertEquals("20 – 22 Nov", DriverScreen.dates(fri, sun, utc));
        long oct = java.time.Instant.parse("2026-10-30T11:30:00Z").toEpochMilli(), nov = java.time.Instant.parse("2026-11-01T14:00:00Z").toEpochMilli();
        assertEquals("30 Oct – 1 Nov", DriverScreen.dates(oct, nov, utc));
        assertEquals("22 Nov", DriverScreen.dates(0, sun, utc));
        assertEquals("", DriverScreen.dates(fri, 0, utc));
        long now = sun - (4L * 86_400_000L + 20L * 3_600_000L + 35L * 60_000L + 30_000L);
        String[] c = DriverScreen.countdown(sun, now);
        assertEquals("04", c[0]);
        assertEquals("20", c[1]);
        assertEquals("35", c[2]);
        String[] over = DriverScreen.countdown(sun, sun + 60_000L);
        assertEquals("00", over[0]);
        assertEquals("00", over[2]);
        assertEquals(29, DriverScreen.age("1997-09-30", LocalDate.of(2026, 11, 18)));
        assertEquals(28, DriverScreen.age("1997-12-30", LocalDate.of(2026, 11, 18)));
        assertEquals(-1, DriverScreen.age("", LocalDate.of(2026, 11, 18)));
    }
}
