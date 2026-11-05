package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Two games at once: where each picture goes, whether a second stream can play, and which game a channel shows. */
public class TwoGamesTest {
    private static Channel ch(String key, String name) {
        return new Channel(key, name, "", "", "http://example.invalid/" + key, "", null, null, 0);
    }

    @Test public void sideBySideSplitsTheWidthAt16by9() {
        int[][] f = TwoGamesActivity.frames(1688, 780, true, 8, 28, 150);
        int half = (1688 - 8) / 2;
        assertEquals(half, f[0][2]);
        assertEquals(half * 9 / 16, f[0][3]);
        assertEquals(f[0][3], f[1][3]);
        assertEquals(f[0][1], f[1][1]);
        // The second starts after the first and the gap, and both fit the stage.
        assertTrue(f[1][0] >= f[0][0] + f[0][2] + 8);
        assertTrue(f[1][0] + f[1][2] <= 1688);
        assertTrue(f[0][1] >= 0 && f[0][1] + f[0][3] <= 780);
    }

    @Test public void aTallStageKeepsBothPicturesWhole() {
        int[][] f = TwoGamesActivity.frames(800, 200, true, 0, 0, 0);
        assertEquals(200, f[0][3]);
        assertEquals(200 * 16 / 9, f[0][2]);
        assertTrue(f[1][0] + f[1][2] <= 800);
    }

    @Test public void bigAndSmallPutsTheSecondInTheCorner() {
        int[][] f = TwoGamesActivity.frames(1688, 780, false, 8, 28, 150);
        // The big one: 16:9 as tall as the stage, in the middle.
        assertEquals(780 * 16 / 9, f[0][2]);
        assertEquals((1688 - f[0][2]) / 2, f[0][0]);
        // The small one: about a third of the width, inside the big one's lower right corner.
        assertEquals(Math.round(1688 * TwoGamesActivity.SMALL), f[1][2]);
        assertEquals(f[0][0] + f[0][2] - f[1][2] - 28, f[1][0]);
        // Up from the foot, clear of the buttons.
        assertEquals(f[0][1] + f[0][3] - f[1][3] - 150, f[1][1]);
    }

    @Test public void theSoundButtonNamesTheHomeClubOrTheChannel() {
        assertEquals("Everton", TwoGamesActivity.soundName("Everton", "Sports 1 HD"));
        assertEquals("Sports 1 HD", TwoGamesActivity.soundName(null, "Sports 1 HD"));
        assertEquals("", TwoGamesActivity.soundName("", null));
    }

    @Test public void noStageNoFrames() {
        assertArrayEquals(new int[]{0, 0, 0, 0}, TwoGamesActivity.frames(0, 500, true, 4, 4, 4)[0]);
    }

    @Test public void aSecondStreamFromAOneStreamAccountIsFlagged() {
        assertEquals(TwoGamesActivity.LIMIT, TwoGamesActivity.connection("p0", "p0", 1, 0));
        assertEquals(TwoGamesActivity.LIMIT, TwoGamesActivity.connection("p1", "p1", 2, 2));
        assertEquals(TwoGamesActivity.OK, TwoGamesActivity.connection("p0", "p0", 2, 1));
        assertEquals(TwoGamesActivity.OK, TwoGamesActivity.connection("p0", "p1", 1, 1));
        // Nothing known of the account: tried.
        assertEquals(TwoGamesActivity.OK, TwoGamesActivity.connection("p0", "p0", 0, 0));
    }

    @Test public void theCopyFromAnotherPlaylistIsOffered() {
        Channel main = ch("7", "Sports 2 HD");
        Channel backup = ch("p1:9", "Sports 2 HD");
        main.alternates.add(backup);
        assertSame(backup, TwoGamesActivity.elsewhere(main, "p0"));
        assertSame(main, TwoGamesActivity.elsewhere(main, "p1"));
        assertNull(TwoGamesActivity.elsewhere(ch("3", "Other"), "p0"));
    }

    private static Game game(String home, String away) {
        Game g = new Game();
        g.id = home + away;
        g.homeName = home;
        g.awayName = away;
        g.homeFull = home;
        g.awayFull = away;
        g.home = home.substring(0, 3).toUpperCase();
        g.away = away.substring(0, 3).toUpperCase();
        g.kickoff = System.currentTimeMillis();
        return g;
    }

    @Test public void aChannelsGameComesFromItsGuideOrItsName() {
        List<Game> today = Arrays.asList(game("Everton", "Brentford"), game("Arsenal", "Chelsea"));
        Game byGuide = TwoGamesActivity.gameFor("Premier League: Everton v Brentford", "", 0, 0, "Sports 1 HD", true, today);
        assertEquals("EvertonBrentford", byGuide.id);
        Game byName = TwoGamesActivity.gameFor("", "", 0, 0, "EPL 03: Arsenal vs Chelsea 17:30", false, today);
        assertEquals("ArsenalChelsea", byName.id);
        assertNull(TwoGamesActivity.gameFor("The News", "", 0, 0, "News 24", false, today));
        assertNull(TwoGamesActivity.gameFor("Everton v Brentford", "", 0, 0, "Sports 1", true, Collections.emptyList()));
    }
}
