package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.Sfx;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.TestAlerts;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The test notifications in the player's options: every group and what is in it, and the info tiles' made-up parts. */
public class PlayerTestsTest {
    @Test public void everyGroupHasItsTestsOnceEach() {
        for (String group : PlayerTests.GROUPS) {
            List<String> ids = PlayerTests.tests(group);
            assertFalse(group, ids.isEmpty());
            assertEquals(group, ids.size(), new HashSet<>(ids).size());
        }
        assertEquals(PlayerTests.GROUPS.length, new HashSet<>(java.util.Arrays.asList(PlayerTests.GROUPS)).size());
    }

    @Test public void theAlertsAreAllThenEachKindThenTheLineUps() {
        List<String> ids = PlayerTests.tests(PlayerTests.ALERTS);
        assertEquals(PlayerTests.ALL, ids.get(0));
        assertEquals(PlayerTests.LINEUPS_ALERT, ids.get(ids.size() - 1));
        assertEquals(TestAlerts.kinds().size() + 2, ids.size());
        for (String[] k : TestAlerts.kinds()) assertTrue(k[0], ids.contains(k[0]));
    }

    @Test public void bothPopUpStylesHaveEachMomentAndBothLineUpsStyles() {
        for (String g : new String[]{PlayerTests.POPUP, PlayerTests.STRAP}) {
            assertEquals(java.util.Arrays.asList("kickoff", "restart", "second"), PlayerTests.tests(g));
        }
        assertEquals(java.util.Arrays.asList("lineups", "stadium", "intro", "intro:f1", "intro:ufc", "intro:boxing", "intro:football"),
                PlayerTests.tests(PlayerTests.LINEUPS));
    }

    @Test public void everyGoalSoundButOffAndEveryTileStyleButAuto() {
        List<String> sounds = PlayerTests.tests(PlayerTests.SOUNDS);
        assertEquals(Sfx.GOALS.length - 1, sounds.size());
        assertFalse(sounds.contains("off"));
        List<String> tiles = PlayerTests.tests(PlayerTests.TILES);
        assertEquals(TileKind.Style.values().length - 1, tiles.size());
        assertFalse(tiles.contains(TileKind.Style.AUTO.name()));
        for (String t : tiles) TileKind.Style.valueOf(t);
    }

    @Test public void theScoreboardHasEachOfItsMoments() {
        List<String> board = PlayerTests.tests(PlayerTests.BOARD);
        for (String id : new String[]{"goal_home", "goal_away", "scorer", "stats", "card", "sub", "var", "added"}) {
            assertTrue(id, board.contains(id));
        }
    }

    @Test public void settingsHasEveryOneThatNeedsNoChannel() {
        // What needs no channel playing is what Settings offers: the alerts and TestAlerts' others.
        Set<String> free = new HashSet<>();
        for (String group : PlayerTests.GROUPS) {
            for (String id : PlayerTests.tests(group)) if (!PlayerTests.needsPlayer(group, id)) free.add(id);
        }
        Set<String> settings = new HashSet<>();
        settings.add(PlayerTests.ALL);
        settings.add(PlayerTests.LINEUPS_ALERT);
        for (String[] k : TestAlerts.kinds()) settings.add(k[0]);
        for (String id : TestAlerts.OTHERS) {
            settings.add(id);
            assertNotEquals(id, 0, TestAlerts.label(id));
        }
        // The scoreboard over other apps keeps out of the player's way: Settings alone has it.
        free.add(TestAlerts.FLOAT);
        assertEquals(settings, free);
        assertFalse(PlayerTests.tests(PlayerTests.OTHER).contains(TestAlerts.FLOAT));
        assertTrue(PlayerTests.needsPlayer(PlayerTests.OTHER, PlayerTests.BANNER));
        assertTrue(PlayerTests.needsPlayer(PlayerTests.OTHER, PlayerTests.BREAK));
        assertTrue(PlayerTests.needsPlayer(PlayerTests.OTHER, PlayerTests.HELD));
        assertTrue(PlayerTests.needsPlayer(PlayerTests.TILES, "MATCH"));
    }

    @Test public void onlyTheOverlaysNeedTheScoreboard() {
        assertTrue(PlayerTests.needsOverlay(PlayerTests.POPUP));
        assertTrue(PlayerTests.needsOverlay(PlayerTests.STRAP));
        assertTrue(PlayerTests.needsOverlay(PlayerTests.LINEUPS));
        assertTrue(PlayerTests.needsOverlay(PlayerTests.BOARD));
        assertFalse(PlayerTests.needsOverlay(PlayerTests.ALERTS));
        assertFalse(PlayerTests.needsOverlay(PlayerTests.TILES));
        assertFalse(PlayerTests.needsOverlay(PlayerTests.SOUNDS));
    }

    private static TileData.Info news() {
        TileData.Info i = new TileData.Info();
        i.title = "BBC News at Six";
        i.desc = "The latest national and international news.";
        i.kind = TileKind.Kind.OTHER;
        i.nextTitle = "Regional News";
        return i;
    }

    private static Game madeUp() {
        Game g = new Game();
        g.id = "test-";
        g.homeName = "Homefield";
        g.awayName = "Visitors";
        return g;
    }

    @Test public void matchNightGetsAMadeUpGameWhenNoneIsOn() {
        TileData.Info from = news();
        TileData.Info t = PlayerTests.tileInfo(from, TileKind.Style.MATCH, madeUp());
        assertEquals(TileKind.Kind.MATCH, t.kind);
        assertEquals("Homefield v Visitors", t.title);
        assertEquals(3, t.stats.size());
        assertEquals("Regional News", t.nextTitle);
        // what's on is left as it was
        assertNull(from.game);
        assertEquals(TileKind.Kind.OTHER, from.kind);
        assertEquals("BBC News at Six", from.title);
    }

    @Test public void matchNightKeepsTheRealGame() {
        TileData.Info from = news();
        Game real = new Game();
        real.homeName = "Everton";
        from.game = real;
        from.stats = new ArrayList<>();
        TileData.Info t = PlayerTests.tileInfo(from, TileKind.Style.MATCH, madeUp());
        assertSame(real, t.game);
        assertEquals("BBC News at Six", t.title);
        assertTrue(t.stats.isEmpty());
    }

    @Test public void cinematicAndCardMakeUpAFilmAndAnEpisode() {
        TileData.Info film = PlayerTests.tileInfo(news(), TileKind.Style.CINEMA, madeUp());
        assertEquals(TileKind.Kind.FILM, film.kind);
        assertEquals("2024", film.year);
        assertFalse(film.genres.isEmpty());
        TileData.Info ep = PlayerTests.tileInfo(news(), TileKind.Style.CARD, madeUp());
        assertEquals(TileKind.Kind.SERIES, ep.kind);
        assertEquals("S2 · E4", ep.episode);
        // a film on now stays itself
        TileData.Info real = news();
        real.kind = TileKind.Kind.FILM;
        real.title = "Paddington (2014)";
        assertEquals("Paddington (2014)", PlayerTests.tileInfo(real, TileKind.Style.CINEMA, madeUp()).title);
    }

    @Test public void theRibbonIsWhatsOn() {
        TileData.Info t = PlayerTests.tileInfo(news(), TileKind.Style.RIBBON, madeUp());
        assertEquals("BBC News at Six", t.title);
        assertEquals(TileKind.Kind.OTHER, t.kind);
        assertNull(t.game);
    }
}
