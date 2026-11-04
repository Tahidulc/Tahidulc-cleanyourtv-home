package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GesturesTest {
    private static int dir(float dx, float dy, float vx, float vy) {
        return Gestures.direction(dx, dy, vx, vy, 100, 400);
    }

    @Test public void swipesOneWayClearly() {
        assertEquals(Gestures.UP, dir(10, -300, 0, -2000));
        assertEquals(Gestures.DOWN, dir(-20, 250, 0, 1500));
        assertEquals(Gestures.LEFT, dir(-300, 20, -1800, 0));
        assertEquals(Gestures.RIGHT, dir(280, -40, 1600, 0));
    }

    @Test public void notASwipe() {
        // Diagonal, too short, too slow.
        assertEquals(Gestures.NONE, dir(200, 200, 1500, 1500));
        assertEquals(Gestures.NONE, dir(0, -60, 0, -3000));
        assertEquals(Gestures.NONE, dir(300, 0, 200, 0));
    }

    @Test public void edgesAndTheMiddle() {
        assertEquals(Gestures.LEFT_EDGE, Gestures.zone(10, 1000));
        assertEquals(Gestures.MIDDLE, Gestures.zone(500, 1000));
        assertEquals(Gestures.RIGHT_EDGE, Gestures.zone(950, 1000));
        assertEquals(Gestures.MIDDLE, Gestures.zone(5, 0));
    }
}
