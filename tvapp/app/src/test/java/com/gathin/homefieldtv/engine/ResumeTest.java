package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Where a film or episode was left: kept as JSON, and done once watched to the end. */
public class ResumeTest {
    @Test public void keptAndReadBack() {
        Resume.Point a = new Resume.Point("p0|m|5", "", "Heat", "", "http://img/b.jpg", 600_000, 10_200_000, 42);
        Resume.Point b = new Resume.Point("p0|s|7", "201", "Lupin", "S2 E1  ·  Black Door", "", 1_200_000, 3_000_000, 41);
        List<Resume.Point> back = Resume.parse(Resume.json(Arrays.asList(a, b)));
        assertEquals(2, back.size());
        assertEquals("Heat", back.get(0).name);
        assertEquals(600_000, back.get(0).position);
        assertEquals("201", back.get(1).episode);
        assertEquals("S2 E1  ·  Black Door", back.get(1).subtitle);
        assertEquals(0.4f, back.get(1).progress(), 0.001f);
        assertTrue(Resume.parse("not json").isEmpty());
    }

    @Test public void theEndIsWatched() {
        assertTrue(new Resume.Point("t", "", "", "", "", 9_700_000, 10_000_000, 0).finished());
        assertTrue(new Resume.Point("t", "", "", "", "", 2_580_000, 2_600_000, 0).finished());
        assertFalse(new Resume.Point("t", "", "", "", "", 5_000_000, 10_000_000, 0).finished());
        assertFalse(new Resume.Point("t", "", "", "", "", 5_000_000, 0, 0).finished());
    }
}
