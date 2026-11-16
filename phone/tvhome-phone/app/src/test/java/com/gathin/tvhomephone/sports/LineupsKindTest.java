package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The line-ups have their own switch now; until it is set, it follows kick-off's, as the two were one before. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LineupsKindTest {
    @Test public void lineUpsFollowKickOffUntilSetThemselves() {
        Context c = RuntimeEnvironment.getApplication();
        assertTrue(Kinds.on(c, "lineups"));
        Kinds.set(c, "kickoff", false);
        assertFalse(Kinds.on(c, "lineups"));
        Kinds.set(c, "lineups", true);
        assertTrue(Kinds.on(c, "lineups"));
        assertFalse(Kinds.on(c, "kickoff"));
    }

    @Test public void theLineUpsAlertIsItsOwnKind() {
        Espn.Summary s = TestAlerts.madeUpLineups();
        Game g = new Game();
        g.id = "9";
        g.homeName = "Harbour City";
        g.awayName = "Westfield";
        Events.Alert a = Events.lineups(g, s);
        assertNotNull(a);
        assertEquals("lineups", a.kind);
        assertTrue(a.key.startsWith("L|"));
        assertEquals("LINE-UPS", com.gathin.tvhomephone.live.ScoreBoard.chip("lineups"));
        assertFalse(Kinds.waits("lineups"));
    }
}
