package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** The ad-break screen's half-time widgets: fun facts and the quiz played for points, and where they go in the card. */
public class FactsTest {
    @Test public void bangladeshFirstAndTwiceAsOften() {
        List<String[]> d = Facts.deck(new Random(7));
        assertEquals(Facts.BD.length + Facts.EVERYWHERE.length + Facts.SPORT.length, d.size());
        assertEquals(Facts.BANGLADESH, d.get(0)[0]);
        assertEquals(Facts.BANGLADESH, d.get(1)[0]);
        assertFalse(Facts.BANGLADESH.equals(d.get(2)[0]));
        // Every fact once.
        Set<String> seen = new HashSet<>();
        for (String[] f : d) assertTrue(seen.add(f[1]));
    }

    @Test public void eachFactFitsTheCard() {
        for (String[] list : new String[][]{Facts.BD, Facts.EVERYWHERE, Facts.SPORT}) {
            for (String f : list) {
                assertTrue(f, f.length() > 10 && f.length() <= 130);
                assertTrue(f, f.endsWith(".") || f.endsWith("!"));
            }
        }
    }

    @Test public void everyQuestionHasFourDifferentShortAnswers() {
        Set<String> qs = new HashSet<>();
        for (String[] q : Facts.QUIZ) {
            assertEquals(q[0], 5, q.length);
            assertTrue(q[0], q[0].endsWith("?"));
            assertTrue(q[0], qs.add(q[0]));
            assertEquals(q[0], 4, new HashSet<>(Arrays.asList(q[1], q[2], q[3], q[4])).size());
            for (int i = 1; i < 5; i++) assertTrue(q[i], !q[i].isEmpty() && q[i].length() <= 20);
            assertTrue(q[0], q[0].length() <= 80);
        }
    }

    @Test public void theAnswersAreShuffledAndTheRightOneKept() {
        Random r = new Random(7);
        Set<Integer> places = new HashSet<>();
        for (String[] row : Facts.QUIZ) {
            Facts.Question q = Facts.ask(row, r);
            assertEquals(row[0], q.text);
            assertEquals(row[1], q.rightAnswer());
            assertEquals(4, new HashSet<>(Arrays.asList(q.answers)).size());
            places.add(q.right);
        }
        // The right answer is not always A.
        assertEquals(4, places.size());
    }

    @Test public void theBreaksDontRepeat() {
        Set<String> seen = new HashSet<>();
        int n = Facts.BD.length + Facts.EVERYWHERE.length + Facts.SPORT.length;
        for (int i = 0; i < n / 3; i++) for (String[] f : Facts.next(3)) assertTrue(seen.add(f[1]));
        Set<String> qs = new HashSet<>();
        for (int i = 0; i < Facts.QUIZ.length; i++) assertTrue(qs.add(Facts.question().text));
    }

    @Test public void pointsGrowWithEachRightAnswerInARow() {
        assertEquals(10, Facts.points(1));
        assertEquals(15, Facts.points(2));
        assertEquals(20, Facts.points(3));
        assertEquals(30, Facts.points(5));
        assertEquals(30, Facts.points(12));
        assertEquals(10, Facts.points(0));
    }

    @Test public void theHalfTimeWidgetsComeTwoAtATimeInAmongTheCardsOwn() {
        assertEquals(Arrays.asList("q", "f", "A", "p", "q", "B", "f", "C", "D"),
                HalfTimeFun.mix(Arrays.asList("q", "f", "p", "q", "f"), Arrays.asList("A", "B", "C", "D")));
        assertEquals(Arrays.asList("q", "f"), HalfTimeFun.mix(Arrays.asList("q", "f"), Arrays.<String>asList()));
        assertEquals(Arrays.asList("A", "B"), HalfTimeFun.mix(Arrays.<String>asList(), Arrays.asList("A", "B")));
    }

    @Test public void theTvAndThePhoneAskTheSameQuestions() {
        // The same bank as the launcher's BreakFacts.QUIZ: its first and last rows here as they are there.
        assertEquals("Which country won the first World Cup, in 1930?", Facts.QUIZ[0][0]);
        assertEquals("Uruguay", Facts.QUIZ[0][1]);
        assertEquals("Which is the hottest planet?", Facts.QUIZ[Facts.QUIZ.length - 1][0]);
        assertEquals(51, Facts.QUIZ.length);
    }
}
