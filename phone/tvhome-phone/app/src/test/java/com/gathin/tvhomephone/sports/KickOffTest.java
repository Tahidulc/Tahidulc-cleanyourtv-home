package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

/** Kick-off in 2 min, your club's goals, and the widget's choice of game (pure parts). */
public class KickOffTest {
    private static final long MIN = 60_000L, K = 1_800_000_000_000L;

    @Test public void theWindowIsTwoAndAHalfToHalfAMinuteBefore() {
        assertFalse(KickOff.due(K, K - 3 * MIN));
        assertTrue(KickOff.due(K, K - 150_000L));
        assertTrue(KickOff.due(K, K - 2 * MIN));
        assertTrue(KickOff.due(K, K - 31_000L));
        assertFalse(KickOff.due(K, K - 30_000L));
        assertFalse(KickOff.due(K, K + MIN));
        assertFalse(KickOff.due(0, K));
    }

    @Test public void minutesToGo() {
        assertEquals(2, KickOff.minutes(K, K - 2 * MIN));
        assertEquals(2, KickOff.minutes(K, K - 150_000L + 30_000L));
        assertEquals(1, KickOff.minutes(K, K - 40_000L));
    }

    @Test public void theAlarmIsTwoMinutesBeforeTheNextKickOff() {
        assertEquals(K - 2 * MIN, KickOff.alarmAt(Arrays.asList(K + 3 * 3_600_000L, K), K - 3_600_000L));
        // Already inside the window: now.
        assertEquals(K - MIN, KickOff.alarmAt(Collections.singletonList(K), K - MIN));
        // Past: none.
        assertEquals(-1, KickOff.alarmAt(Collections.singletonList(K), K + MIN));
        assertEquals(-1, KickOff.alarmAt(Collections.emptyList(), K));
    }

    @Test public void onlyAFollowedClubsGoalBuzzesDifferently() {
        HashSet<String> mine = new HashSet<>(Collections.singletonList("368"));
        assertTrue(Notifier.clubGoal("goal", "home", "368", "337", mine));
        assertFalse(Notifier.clubGoal("goal", "away", "368", "337", mine));
        assertTrue(Notifier.clubGoal("goal", "away", "337", "368", mine));
        assertFalse(Notifier.clubGoal("red", "home", "368", "337", mine));
        assertFalse(Notifier.clubGoal("goal", "", "368", "337", mine));
    }

    private static Game game(String id, int state, long kickoff) {
        Game g = new Game();
        g.id = id;
        g.state = state;
        g.kickoff = kickoff;
        g.homeName = "Everton";
        g.awayName = "Fulham";
        return g;
    }

    @Test public void theWidgetShowsLiveThenNextThenLast() {
        Game done = game("1", Game.POST, K - 5 * 3_600_000L), soon = game("2", Game.PRE, K + 3_600_000L), live = game("3", Game.LIVE, K - 1_800_000L);
        assertSame(live, ClubWidget.pick(Arrays.asList(done, soon, live), K));
        assertSame(soon, ClubWidget.pick(Arrays.asList(done, soon), K));
        assertSame(done, ClubWidget.pick(Collections.singletonList(done), K));
        assertNull(ClubWidget.pick(Collections.emptyList(), K));
        assertEquals("Everton v Fulham · Sat 15:00", ClubWidget.fixture(game("4", Game.PRE,
                java.time.LocalDateTime.of(2026, 11, 7, 15, 0).atZone(ZoneId.of("Europe/London")).toInstant().toEpochMilli()), ZoneId.of("Europe/London")));
    }
}
