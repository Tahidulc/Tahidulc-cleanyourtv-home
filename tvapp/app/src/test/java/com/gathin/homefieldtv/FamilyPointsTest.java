package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.prefs.General;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The family's points of 24 Nov, where they are pure: the last channel playing as Homefield TV opens and comes back (6),
 * the reminder's panel and Remind again in 5 min (2). Invented programmes.
 */
public class FamilyPointsTest {
    private static final long NOW = 1_764_000_000_000L;

    @Test public void theLastChannelPlaysAsTheAppOpensAgain() {
        assertEquals(General.START_LIVE, General.START_DEFAULT);
        assertEquals("Last channel", General.START_NAMES[General.START_LIVE]);
        // Opened again from the TV's Home: always; back after a while away (Recents, the TV waking): after two minutes.
        assertTrue(StartUp.playOnReturn(General.START_LIVE, true, 1_000L, false));
        assertTrue(StartUp.playOnReturn(General.START_LIVE, false, StartUp.AWAY_MS, false));
        assertFalse(StartUp.playOnReturn(General.START_LIVE, false, StartUp.AWAY_MS - 1, false));
        // Back from one of its own screens (a film, the installer): where it was.
        assertFalse(StartUp.playOnReturn(General.START_LIVE, false, 10 * StartUp.AWAY_MS, true));
        // Home (or the guide, films, TV shows) chosen: only when it starts afresh.
        assertFalse(StartUp.playOnReturn(General.START_HOME, true, 10 * StartUp.AWAY_MS, false));
        assertFalse(StartUp.playOnReturn(General.START_GUIDE, true, 10 * StartUp.AWAY_MS, false));
    }

    @Test public void theReminderPanelSaysWhenAndWhat() {
        assertEquals("Starts in 2:45", ReminderCard.Face.countdown(165_000L));
        assertEquals("Starts in 12 min", ReminderCard.Face.countdown(12 * 60_000L));
        assertEquals("Starting now", ReminderCard.Face.countdown(10_000L));
        assertEquals("Starting now", ReminderCard.Face.countdown(-60_000L));
        String m = ReminderCard.Face.message("The Long Tide", "Harbour TV", NOW + 60_000L, NOW);
        assertTrue(m.startsWith("Your reminder: The Long Tide starts at "));
        assertTrue(m.endsWith(" on Harbour TV"));
        assertEquals("Your reminder: The Long Tide is starting now on Harbour TV",
                ReminderCard.Face.message("The Long Tide", "Harbour TV", NOW, NOW));
        assertEquals("Watch", ReminderCard.Face.BUTTONS[ReminderCard.Face.WATCH]);
        assertEquals("Remind again in 5 min", ReminderCard.Face.BUTTONS[ReminderCard.Face.AGAIN]);
        assertEquals("Dismiss", ReminderCard.Face.BUTTONS[ReminderCard.Face.DISMISS]);
    }

    @Test public void theCatchUpBarMovesFasterTheLongerItIsHeld() {
        // Skip steps (10 seconds as it comes) for the first presses, then 30 seconds, a minute and three minutes.
        assertEquals(10_000L, CatchupScreen.step(1, 10_000L));
        assertEquals(10_000L, CatchupScreen.step(5, 10_000L));
        assertEquals(30_000L, CatchupScreen.step(6, 10_000L));
        assertEquals(60_000L, CatchupScreen.step(16, 10_000L));
        assertEquals(180_000L, CatchupScreen.step(31, 10_000L));
        // A bigger Skip step stays at least that.
        assertEquals(60_000L, CatchupScreen.step(7, 60_000L));
        assertEquals(CatchupScreen.SEEK, 7);
    }

    @Test public void remindAgainInFiveMinutesComesAgainThen() {
        long start = NOW + 60_000L;
        Reminders.Reminder r = new Reminders.Reminder("x:1", "Harbour TV", "", "The Long Tide", start, start + 3_600_000L);
        assertEquals(start - Reminders.AHEAD_MS, r.cardAt());
        Reminders.Reminder later = r.againAt(NOW + Reminders.AGAIN_MS);
        assertEquals(NOW + Reminders.AGAIN_MS, later.cardAt());
        assertEquals(r.id(), later.id());
        List<Reminders.Reminder> one = Collections.singletonList(later);
        // Not before it is due again; then due, even six minutes after the programme started.
        assertTrue(Reminders.due(one, NOW + 60_000L).isEmpty());
        assertEquals(1, Reminders.due(one, NOW + Reminders.AGAIN_MS + 1_000L).size());
        assertEquals(1, Reminders.prune(new ArrayList<>(one), NOW + Reminders.AGAIN_MS + 60_000L).size());
        // Kept as it was set, again and all.
        List<Reminders.Reminder> back = Reminders.parse(Reminders.join(one));
        assertEquals(later.again, back.get(0).again);
    }
}
