package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Broadcast mode's fun facts and quiz. */
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

    @Test public void eachFitsTheCard() {
        for (String[] list : new String[][]{Facts.BD, Facts.EVERYWHERE, Facts.SPORT}) {
            for (String f : list) {
                assertTrue(f, f.length() > 10 && f.length() <= 130);
                assertTrue(f, f.endsWith(".") || f.endsWith("!"));
            }
        }
        for (String[] q : Facts.QUIZ) {
            assertEquals(2, q.length);
            assertTrue(q[0], q[0].endsWith("?"));
            assertFalse(q[1].isEmpty());
        }
    }

    @Test public void theBreaksDontRepeat() {
        Set<String> seen = new HashSet<>();
        int n = Facts.BD.length + Facts.EVERYWHERE.length + Facts.SPORT.length;
        for (int i = 0; i < n / 3; i++) for (String[] f : Facts.next(3)) assertTrue(seen.add(f[1]));
        Set<String> qs = new HashSet<>();
        for (int i = 0; i < Facts.QUIZ.length / 2; i++) for (String[] q : Facts.questions(2)) assertTrue(qs.add(q[0]));
    }
}
