package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.homefieldtv.sports.BoardClock;
import com.gathin.homefieldtv.sports.Espn;
import com.gathin.homefieldtv.sports.Game;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The scoreboard in the player: which games it takes, what it shows, the goal a score brings, its places and switches. */
public class BoardLayerTest {
    private static Game game(String id, int state, long kickoff) {
        Game g = new Game();
        g.id = id;
        g.home = "ARS";
        g.away = "CHE";
        g.homeName = "Arsenal";
        g.awayName = "Chelsea";
        g.leagueName = "Premier League";
        g.homeColor = "ef0107";
        g.awayColor = "034694";
        g.state = state;
        g.kickoff = kickoff;
        return g;
    }

    @Test public void onlyGamesLiveOrKickingOffSoon() {
        long now = 1_000_000_000L;
        Game live = game("1", Game.LIVE, now - 3_600_000L), soon = game("2", Game.PRE, now + 10 * 60_000L),
                later = game("3", Game.PRE, now + 2 * 3_600_000L), over = game("4", Game.POST, now - 3 * 3_600_000L);
        List<Game> out = BoardLayer.relevant(Arrays.asList(live, soon, later, over), now);
        assertEquals(Arrays.asList(live, soon), out);
        assertTrue(BoardLayer.relevant(null, now).isEmpty());
    }

    @Test public void whatTheBoardShows() {
        Game g = game("1", Game.LIVE, 0);
        BoardBug.Look l = BoardLayer.look(g, new BoardClock.Reading(0, 1, 0, Game.LIVE, false, 600, "11'"));
        assertEquals("ARS", l.home);
        assertEquals("CHE", l.away);
        assertEquals("1–0", l.score);
        assertEquals("PL", l.tag);
        assertTrue(l.live);
        assertEquals("HT", BoardLayer.look(g, new BoardClock.Reading(0, 1, 0, Game.LIVE, true, 2700, "HT")).clock);
        BoardBug.Look ft = BoardLayer.look(g, new BoardClock.Reading(0, 2, 1, Game.POST, false, 5400, "FT"));
        assertEquals("FT", ft.clock);
        assertFalse(ft.live);
        assertEquals("v", BoardLayer.preLook(g).score);
        assertEquals(0xFFEF0107, BoardLayer.hex("ef0107"));
        assertEquals(0xFF00FF00, BoardLayer.hex("#00ff00"));
        assertEquals(0, BoardLayer.hex(""));
        assertEquals(0, BoardLayer.hex(null));
    }

    @Test public void theGoalANewScoreBrings() {
        List<String[]> goals = Arrays.asList(new String[]{"k1", "Saka", "12'", "home", ""},
                new String[]{"k2", "Palmer", "30'", "away", ""}, new String[]{"k3", "Rice", "60'", "home", ""});
        assertEquals("Saka", BoardLayer.nthGoal(goals, 1, "1–1")[1]);
        assertEquals("Rice", BoardLayer.nthGoal(goals, 1, "2–1")[1]);
        assertEquals("Palmer", BoardLayer.nthGoal(goals, 2, "1–1")[1]);
        assertNull(BoardLayer.nthGoal(null, 1, "1–0"));
    }

    private static String[] player(String name, String surname, boolean starter) {
        String[] p = new String[16];
        Arrays.fill(p, "");
        p[Espn.P_NAME] = name;
        p[Espn.P_SHORT] = surname;
        p[Espn.P_STARTER] = starter ? "1" : "0";
        return p;
    }

    @Test public void theElevensBesideTheBoard() {
        List<String[]> side = new ArrayList<>(Arrays.asList(player("David Raya", "Raya", true), player("Ben White", "White", true),
                player("Kai Havertz", "Havertz", false)));
        assertEquals("Arsenal (4-3-3): Raya, White", BoardLayer.eleven(side, "Arsenal", "4-3-3"));
        assertEquals("Arsenal: Raya, White", BoardLayer.eleven(side, "Arsenal", ""));
        assertEquals(400, BoardLayer.shortRoom(50, 1_000));
        assertEquals(150, BoardLayer.shortRoom(50, 100));
    }

    @Test public void placesKeptOnTheScreen() {
        BoardPrefs.Place p = BoardPrefs.Place.standard();
        BoardPrefs.Place back = BoardPrefs.Place.parse(p.words());
        assertEquals(p.bx, back.bx, 1e-6);
        assertEquals(p.lh, back.lh, 1e-6);
        assertNull(BoardPrefs.Place.parse("not a place"));
        p.moveBoard(-1f, -1f, 4f);
        assertEquals(0f, p.bx, 1e-6);
        assertEquals(0f, p.by, 1e-6);
        p.moveBoard(5f, 5f, 4f);
        assertEquals(1f - p.bh * 4f, p.bx, 1e-6);
        assertEquals(1f - p.bh, p.by, 1e-6);
        p.sizeBoard(100f, 4f);
        assertEquals(BoardPrefs.Place.MAX_H, p.bh, 1e-6);
        p.moveLogo(5f, 5f);
        assertEquals(1f - p.lw, p.lx, 1e-6);
        p.sizeLogo(0.01f);
        assertEquals(BoardPrefs.Place.MIN_W, p.lw, 1e-6);
        assertEquals(BoardPrefs.Place.MIN_LH, p.lh, 1e-6);
    }

    @Test public void soundsOnlyWhileUpAndSwitchedOn() {
        assertTrue(BoardPrefs.heard(true, 1f, true));
        assertFalse(BoardPrefs.heard(true, 0.01f, true));
        assertFalse(BoardPrefs.heard(false, 1f, true));
        assertFalse(BoardPrefs.heard(true, 1f, false));
        assertArrayEquals(new float[]{0f, 1f, 1f, 0f}, BoardPrefs.turnParts(0f), 1e-6f);
        assertArrayEquals(new float[]{1f, 0f, 0f, 1f}, BoardPrefs.turnParts(1f), 1e-6f);
    }
}
