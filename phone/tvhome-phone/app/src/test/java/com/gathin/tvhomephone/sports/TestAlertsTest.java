package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A test alert for every kind there is, never under a real one's key. */
public class TestAlertsTest {
    @Test public void everyKindHasOne() {
        Game g = new Game();
        g.id = "1";
        g.homeName = "Home";
        g.awayName = "Away";
        g.goals.add(new String[]{"TEST|goal", "C. Player", "67'", "home", ""});
        g.reds.add(new String[]{"TEST|red", "D. Player", "70'", "away"});
        for (String[] k : TestAlerts.kinds()) {
            Events.Alert a = TestAlerts.alert(k[0], g);
            assertNotNull(k[0], a);
            assertEquals(k[0], a.kind);
            assertTrue(a.key, a.key.contains("TEST|"));
        }
        assertEquals(Kinds.ALL.length, TestAlerts.kinds().size());
    }

    @Test public void clubCodes() {
        assertEquals("MAN", TestAlerts.code("Man United"));
        assertEquals("EVE", TestAlerts.code("Everton"));
    }

    private static Game game(String id, int state, long kickoff) {
        Game g = new Game();
        g.id = id;
        g.state = state;
        g.kickoff = kickoff;
        return g;
    }

    @Test public void theGamesToTryOnAreTheOneWatchedThenTheLatestPlayed() {
        // As the TV's tries find them: the one watched first, then each day's under way or played, the latest first.
        Game watched = game("9", Game.PRE, 500);
        List<Game> out = new ArrayList<>();
        out.add(watched);
        TestAlerts.addPlayed(out, watched, Arrays.asList(game("1", Game.POST, 100), game("2", Game.PRE, 400),
                game("3", Game.POST, 300), game("9", Game.LIVE, 500), game(null, Game.POST, 200), game("4", Game.LIVE, 350)));
        assertEquals(4, out.size());
        assertEquals("9", out.get(0).id);
        assertEquals("4", out.get(1).id);
        assertEquals("3", out.get(2).id);
        assertEquals("1", out.get(3).id);
        List<Game> none = new ArrayList<>();
        TestAlerts.addPlayed(none, null, Arrays.asList(game("5", Game.POST, 1), game("6", Game.PRE, 2)));
        assertEquals(1, none.size());
    }

    @Test public void theKickOffHeadsUpIsTwoMinutesBeforeWithNoScore() {
        Game g = game("test-7", Game.LIVE, 1);
        g.homeName = "Home";
        g.awayName = "Away";
        g.homeScore = 2;
        g.goals.add(new String[]{"TEST|goal", "C. Player", "67'", "home", ""});
        Game k = TestAlerts.soon(g, 1_000_000L);
        assertEquals(Game.PRE, k.state);
        assertEquals(1_000_000L + KickOff.SOON_MS, k.kickoff);
        assertEquals(0, k.homeScore);
        assertTrue(k.goals.isEmpty());
        assertEquals("test-7", k.id);
        assertTrue(KickOff.due(k.kickoff, 1_000_000L));
        assertEquals(2, KickOff.minutes(k.kickoff, 1_000_000L));
        // the game it came from is left as it was
        assertEquals(2, g.homeScore);
    }

    @Test public void eachOtherNotificationHasItsName() {
        for (String id : TestAlerts.OTHERS) assertNotEquals(id, 0, TestAlerts.label(id));
        assertEquals(0, TestAlerts.label("nothing"));
    }
}
