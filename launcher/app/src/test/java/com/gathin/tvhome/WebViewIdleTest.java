package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** WebViews.Idle: when YouTube's player (TilePlayer) is let go after nothing has played. */
public class WebViewIdleTest {
    private static final long IDLE = 3 * 60_000L;

    @Test
    public void neverLetGoWhileInUse() {
        WebViews.Idle idle = new WebViews.Idle(IDLE);
        idle.use();
        assertFalse(idle.due(10 * IDLE));
        assertEquals(Long.MAX_VALUE, idle.left(10 * IDLE));
        assertTrue(idle.using());
    }

    @Test
    public void letGoOnceFreeForItsTime() {
        WebViews.Idle idle = new WebViews.Idle(IDLE);
        idle.use();
        idle.free(1_000L);
        assertFalse(idle.due(1_000L + IDLE - 1));
        assertEquals(1, idle.left(1_000L + IDLE - 1));
        assertTrue(idle.due(1_000L + IDLE));
        assertEquals(0, idle.left(1_000L + IDLE + 5_000L));
    }

    @Test
    public void usedAgainBeforeItsTimeIsKept() {
        WebViews.Idle idle = new WebViews.Idle(IDLE);
        idle.free(0L);
        idle.use();
        assertFalse("a trailer started again", idle.due(IDLE * 2));
        idle.free(IDLE * 2);
        assertFalse(idle.due(IDLE * 2 + 1));
        assertTrue(idle.due(IDLE * 3));
    }

    @Test
    public void neverUsedIsNotDue() {
        WebViews.Idle idle = new WebViews.Idle(IDLE);
        assertFalse(idle.due(Long.MAX_VALUE / 2));
        assertEquals(Long.MAX_VALUE, idle.left(0L));
    }
}
