package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Broadcast mode's half-time break: when it goes up and when it ends, what its corner says, its facts and quiz. */
public class HalfTimeBreakTest {
    private static final long T = 1_000_000_000L;

    @Test public void upAMinuteAfterHalfTimeShows() {
        // Half time seen by the scores at T; the channel 45 s behind: it shows at T+45 s, the break a minute on.
        assertEquals(T + 45_000L + 60_000L, HalfTimeBreak.startsAt(T, 45_000L));
    }

    @Test public void endsJustBeforeTheSecondHalfShows() {
        // Not seen yet: fifteen minutes after half time showed, less forty seconds.
        assertEquals(T + 45_000L + 15 * 60_000L - 40_000L, HalfTimeBreak.endsAt(T, 45_000L, 0));
        // Seen starting by the scores at T+15m10s: it shows 45 s later; back forty seconds before that.
        long seen = T + 15 * 60_000L + 10_000L;
        assertEquals(seen + 5_000L, HalfTimeBreak.endsAt(T, 45_000L, seen));
        // A channel only a few seconds behind: the break is already over when the scores see it.
        assertTrue(HalfTimeBreak.endsAt(T, 10_000L, seen) < seen);
    }

    @Test public void theCornerCountsDown() {
        assertEquals("Second half in about 9 min", HalfTimeBreak.countdown(T, T + 9 * 60_000L, false));
        assertEquals("Back to the match in 0:42", HalfTimeBreak.countdown(T, T + 42_000L, true));
        // The last minute counts in seconds even when it is only an estimate.
        assertEquals("Back to the match in 0:30", HalfTimeBreak.countdown(T, T + 30_000L, false));
        assertEquals("Back to the match in 0:00", HalfTimeBreak.countdown(T + 5_000L, T, true));
    }

    @Test public void theGameAtItsBreak() {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.home = "ARS";
        g.homeName = "Arsenal";
        g.away = "CHE";
        g.homeScore = "1";
        g.awayScore = "0";
        assertEquals("Arsenal  1–0  CHE", HalfTimeBreak.gameLine(g));
    }

    @Test public void bangladeshFirstAndEveryFactOnce() {
        List<String[]> d = BreakFacts.deck(new Random(3));
        assertEquals(AmbientFacts.BANGLADESH.length + AmbientFacts.WORLD.length + BreakFacts.SPORT.length, d.size());
        assertEquals(BreakFacts.BANGLADESH, d.get(0)[0]);
        assertEquals(BreakFacts.BANGLADESH, d.get(1)[0]);
        assertFalse(BreakFacts.BANGLADESH.equals(d.get(2)[0]));
        Set<String> seen = new HashSet<>();
        for (String[] f : d) assertTrue(seen.add(f[1]));
    }

    @Test public void theQuizHasAnswersAndTheBreaksDontRepeat() {
        for (String[] q : BreakFacts.QUIZ) {
            assertTrue(q[0], q[0].endsWith("?"));
            assertFalse(q[1].isEmpty());
        }
        Set<String> qs = new HashSet<>();
        for (int i = 0; i < BreakFacts.QUIZ.length / 2; i++) for (String[] q : BreakFacts.questions(2)) assertTrue(qs.add(q[0]));
        Set<String> fs = new HashSet<>();
        int n = AmbientFacts.BANGLADESH.length + AmbientFacts.WORLD.length + BreakFacts.SPORT.length;
        for (int i = 0; i < n / 3; i++) for (String[] f : BreakFacts.next(3)) assertTrue(fs.add(f[1]));
    }

    @Test public void itFillsA1080pScreenAndA4kOneAlike() {
        assertEquals(2f, HalfTimeBreak.unit(1920, 1080), 0.001f);
        assertEquals(4f, HalfTimeBreak.unit(3840, 2160), 0.001f);
        // Whichever way round the sizes come.
        assertEquals(4f, HalfTimeBreak.unit(2160, 3840), 0.001f);
        assertEquals(2f, HalfTimeBreak.unit(0, 0), 0.001f);
    }
}
