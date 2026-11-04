package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class OnAirTest {
    private static final long KO = 1_762_278_300_000L;   // 4 Nov 2026 17:45 UTC

    private static Game game(String id, String homeFull, String homeShort, String home, String awayFull, String awayShort,
                             String away, long kickoff) {
        Game g = new Game();
        g.id = id;
        g.homeFull = homeFull;
        g.homeName = homeShort;
        g.home = home;
        g.awayFull = awayFull;
        g.awayName = awayShort;
        g.away = away;
        g.kickoff = kickoff;
        return g;
    }

    private static List<Game> tonight() {
        List<Game> out = new ArrayList<>();
        out.add(game("1", "AEK Athens", "AEK Athens", "AEK", "Real Madrid", "Real Madrid", "RMA", KO));
        out.add(game("2", "Manchester United", "Man United", "MAN", "AS Roma", "Roma", "ROMA", KO + 2 * 3600_000L + 15 * 60_000L));
        out.add(game("3", "Manchester City", "Man City", "MNC", "RB Leipzig", "Leipzig", "RBL", KO + 2 * 3600_000L + 15 * 60_000L));
        out.add(game("4", "PSV Eindhoven", "PSV", "PSV", "Club Brugge", "Club Brugge", "BRU", KO + 2 * 3600_000L + 15 * 60_000L));
        return out;
    }

    @Test public void fixtureTitles() {
        assertTrue(OnAir.looksLikeFixture("AEK Athens v Real Madrid"));
        assertTrue(OnAir.looksLikeFixture("Live: Arsenal vs. Chelsea"));
        assertTrue(OnAir.looksLikeFixture("England versus Wales"));
        assertFalse(OnAir.looksLikeFixture("Sky Sports News"));
        assertFalse(OnAir.looksLikeFixture("Live Champions League"));
    }

    @Test public void bothClubsAndTheKickOff() {
        Game g = OnAir.match("Live Champions League: AEK Athens v Real Madrid", "", KO - 3600_000L, KO + 2 * 3600_000L, tonight());
        assertEquals("1", g.id);
        // The usual short forms ("Man Utd") count as the club.
        g = OnAir.match("Man Utd v Roma", "Champions League football", KO + 2 * 3600_000L, KO + 5 * 3600_000L, tonight());
        assertEquals("2", g.id);
        // Manchester City is not Manchester United: "city" and "united" alone tell nothing.
        g = OnAir.match("Live: Man City v Leipzig", "", KO + 2 * 3600_000L, KO + 5 * 3600_000L, tonight());
        assertEquals("3", g.id);
    }

    @Test public void theDescriptionCounts() {
        Game g = OnAir.match("Live Champions League", "PSV take on Club Brugge in the league phase.", KO + 2 * 3600_000L,
                KO + 5 * 3600_000L, tonight());
        assertEquals("4", g.id);
    }

    @Test public void oneClubOrTheWrongTimeIsNoGame() {
        assertNull(OnAir.match("Real Madrid: The Inside Story", "", KO - 3600_000L, KO + 3600_000L, tonight()));
        // The highlights the next morning are not the game itself.
        assertNull(OnAir.match("AEK Athens v Real Madrid", "Highlights", KO + 14 * 3600_000L, KO + 15 * 3600_000L, tonight()));
        assertNull(OnAir.match("Arsenal v Chelsea", "", 0, 0, tonight()));
        assertNull(OnAir.match("AEK Athens v Real Madrid", "", 0, 0, null));
    }

    @Test public void cleaning() {
        assertEquals("atletico madrid", OnAir.clean("Atlético Madrid FC"));
        assertEquals("brighton and hove albion", OnAir.clean("Brighton & Hove Albion"));
    }

    @Test public void form() {
        assertEquals("DWLLW", Espn.form("DWLLW"));
        assertEquals("WWDLW", Espn.form("LWWDLW"));
        assertEquals("", Espn.form(null));
    }

    @Test public void heldBackLikeTheTv() {
        assertTrue(Kinds.waits("goal"));
        assertTrue(Kinds.waits("ft"));
        assertTrue(Kinds.waits("var"));
        assertFalse(Kinds.waits("kickoff"));
        assertFalse(Kinds.waits("reminder"));
        assertFalse(Kinds.waits("f1live"));
        assertEquals(60_000L, Kinds.TV_DELAY_MS);
    }
}
