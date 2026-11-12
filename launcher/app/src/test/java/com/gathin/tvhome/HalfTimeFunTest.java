package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class HalfTimeFunTest {
    @Test public void bangladeshFirstAndEveryFactOnce() {
        List<String[]> d = BreakFacts.deck(new Random(3));
        assertEquals(AmbientFacts.BANGLADESH.length + AmbientFacts.WORLD.length + BreakFacts.SPORT.length, d.size());
        assertEquals(BreakFacts.BANGLADESH, d.get(0)[0]);
        assertEquals(BreakFacts.BANGLADESH, d.get(1)[0]);
        assertFalse(BreakFacts.BANGLADESH.equals(d.get(2)[0]));
        Set<String> seen = new HashSet<>();
        for (String[] f : d) assertTrue(seen.add(f[1]));
    }

    @Test public void everyQuestionHasFourDifferentShortAnswers() {
        Set<String> qs = new HashSet<>();
        for (String[] q : BreakFacts.QUIZ) {
            assertEquals(q[0], 5, q.length);
            assertTrue(q[0], qs.add(q[0]));
            assertEquals(q[0], 4, new HashSet<>(Arrays.asList(q[1], q[2], q[3], q[4])).size());
            for (int i = 1; i < 5; i++) assertTrue(q[i], !q[i].isEmpty() && q[i].length() <= 20);
            assertTrue(q[0], q[0].length() <= 80);
        }
    }

    @Test public void theAnswersAreShuffledAndTheRightOneKept() {
        Random r = new Random(7);
        Set<Integer> places = new HashSet<>();
        for (String[] row : BreakFacts.QUIZ) {
            BreakFacts.Question q = BreakFacts.ask(row, r);
            assertEquals(row[0], q.text);
            assertEquals(row[1], q.rightAnswer());
            assertEquals(4, new HashSet<>(Arrays.asList(q.answers)).size());
            places.add(q.right);
        }
        // The right answer is not always A.
        assertEquals(4, places.size());
    }

    @Test public void theQuestionsDontRepeatUntilTheBankIsUsedUp() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < BreakFacts.QUIZ.length; i++) assertTrue(seen.add(BreakFacts.question().text));
    }

    @Test public void pointsGrowWithEachRightAnswerInARow() {
        assertEquals(10, BreakFacts.points(1));
        assertEquals(15, BreakFacts.points(2));
        assertEquals(20, BreakFacts.points(3));
        assertEquals(30, BreakFacts.points(5));
        assertEquals(30, BreakFacts.points(12));
        assertEquals(10, BreakFacts.points(0));
    }

    @Test public void theArrowsGoRoundTheFourAnswersTwoByTwo() {
        // A B / C D
        assertEquals(1, BreakFacts.move(0, 1, 0));
        assertEquals(2, BreakFacts.move(0, 0, 1));
        assertEquals(3, BreakFacts.move(2, 1, 0));
        assertEquals(1, BreakFacts.move(3, 0, -1));
        // It stays at an edge.
        assertEquals(0, BreakFacts.move(0, -1, 0));
        assertEquals(0, BreakFacts.move(0, 0, -1));
        assertEquals(3, BreakFacts.move(3, 1, 1));
    }

    @Test public void theHalfTimeWidgetsComeTwoAtATimeInAmongTheCardsOwn() {
        assertEquals(Arrays.asList("q", "f", "A", "p", "q", "B", "f", "C", "D"),
                HalfTimeFun.mix(Arrays.asList("q", "f", "p", "q", "f"), Arrays.asList("A", "B", "C", "D")));
        assertEquals(Arrays.asList("q", "f"), HalfTimeFun.mix(Arrays.asList("q", "f"), Arrays.<String>asList()));
        assertEquals(Arrays.asList("A", "B"), HalfTimeFun.mix(Arrays.<String>asList(), Arrays.asList("A", "B")));
    }

    @Test public void theCreditsCoverFillsA1080pScreenAndA4kOneAlike() {
        assertEquals(2f, CreditsCover.unit(1920, 1080), 0.001f);
        assertEquals(4f, CreditsCover.unit(3840, 2160), 0.001f);
        assertEquals(4f, CreditsCover.unit(2160, 3840), 0.001f);
        assertEquals(2f, CreditsCover.unit(0, 0), 0.001f);
    }
}
