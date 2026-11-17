package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReminderCardTest {
    private static final long NOW = 1_800_000_000_000L;

    @Test public void theCardSaysWhenItStarts() {
        assertEquals("Starts in 1 min", ReminderCard.when(NOW, NOW + 60_000L));
        assertEquals("Starts in 1 min", ReminderCard.when(NOW, NOW + 45_000L));
        assertEquals("Starts in 2 min", ReminderCard.when(NOW, NOW + 100_000L));
        assertEquals("Starting now", ReminderCard.when(NOW, NOW + 20_000L));
        assertEquals("Starting now", ReminderCard.when(NOW, NOW - 90_000L));
    }
}
