package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RemindersTest {
    private static final long MIN = 60_000L, NOW = 1_800_000_000_000L;

    private static Reminders.Reminder at(String key, long start) {
        return new Reminders.Reminder(key, "Northgate One", "http://logos.example/one.png", "Quiz Night Live", start, start + 60 * MIN);
    }

    @Test public void keptAndReadBackTheSame() {
        List<Reminders.Reminder> in = Arrays.asList(at("b", NOW + 90 * MIN), at("a", NOW + 30 * MIN));
        List<Reminders.Reminder> back = Reminders.parse(Reminders.join(in));
        assertEquals(2, back.size());
        // Soonest first.
        assertEquals("a", back.get(0).key);
        assertEquals(NOW + 30 * MIN, back.get(0).start);
        assertEquals("Quiz Night Live", back.get(0).title);
        assertEquals("http://logos.example/one.png", back.get(0).logo);
        assertEquals(NOW + 150 * MIN, back.get(1).end);
    }

    @Test public void whatCantBeReadIsLeftOut() {
        assertTrue(Reminders.parse("").isEmpty());
        assertTrue(Reminders.parse("not json").isEmpty());
        assertEquals(1, Reminders.parse("[{\"key\":\"a\",\"start\":5},{\"key\":\"\",\"start\":9},{\"start\":7},3]").size());
    }

    @Test public void theCardIsDueAMinuteBeforeUntilAFewMinutesAfter() {
        List<Reminders.Reminder> list = Arrays.asList(at("a", NOW + 10 * MIN));
        assertTrue(Reminders.due(list, NOW + 8 * MIN).isEmpty());
        assertEquals(1, Reminders.due(list, NOW + 9 * MIN).size());
        assertEquals(1, Reminders.due(list, NOW + 12 * MIN).size());
        // Too late by then (a TV that was off): let go.
        assertTrue(Reminders.due(list, NOW + 13 * MIN).isEmpty());
    }

    @Test public void theNextCardIsTheSoonestStillToCome() {
        List<Reminders.Reminder> list = Arrays.asList(at("a", NOW + 90 * MIN), at("b", NOW + 30 * MIN));
        assertEquals(NOW + 29 * MIN, Reminders.nextCard(list, NOW));
        assertEquals(NOW + 89 * MIN, Reminders.nextCard(list, NOW + 29 * MIN));
        assertEquals(Long.MAX_VALUE, Reminders.nextCard(list, NOW + 89 * MIN));
        assertEquals(Long.MAX_VALUE, Reminders.nextCard(new ArrayList<>(), NOW));
    }

    @Test public void onesOverAreLetGo() {
        List<Reminders.Reminder> list = Arrays.asList(at("a", NOW - 120 * MIN), at("b", NOW - 2 * MIN), at("c", NOW + 5 * MIN));
        List<Reminders.Reminder> kept = Reminders.prune(list, NOW);
        assertEquals(2, kept.size());
        assertEquals("b", kept.get(0).key);
        assertEquals("c", kept.get(1).key);
    }

    @Test public void anIdIsTheChannelAndTheStart() {
        assertEquals("ch:7@1234", Reminders.id("ch:7", 1234L));
        assertEquals(Reminders.id("a", NOW), at("a", NOW).id());
        assertEquals(NOW - MIN, at("a", NOW).cardAt());
    }
}
