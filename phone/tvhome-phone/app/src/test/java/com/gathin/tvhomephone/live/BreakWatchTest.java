package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

import java.util.Random;

/** The channel's adverts told by its logo going and coming back; the burn-in guard's moves. */
public class BreakWatchTest {
    private static final int W = 192, H = 108;

    /** A moving picture, and the channel's see-through logo top right when there is one. */
    private static byte[] frame(int i, Random r, boolean logo) {
        byte[] f = new byte[W * H];
        double pan = i * 2.7;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double v = 95 + 55 * Math.sin((x + pan) * 0.41) * Math.cos((y + i * 0.9) * 0.29);
                f[y * W + x] = (byte) Math.max(0, Math.min(255, (int) v + r.nextInt(9) - 4));
            }
        }
        if (logo) {
            for (int y = 8; y < 15; y++) {
                for (int x = 150; x < 178; x++) {
                    if ((x - 150) % 5 >= 2 && y != 8 && y != 14) continue;
                    int under = f[y * W + x] & 0xFF;
                    f[y * W + x] = (byte) Math.min(255, (int) (under * 0.25 + 240 * 0.75));
                }
            }
        }
        return f;
    }

    @Test public void theLogoGoingIsABreakAndComingBackEndsIt() {
        Random r = new Random(4);
        BugFinder f = new BugFinder(W, H);
        for (int i = 0; i < BugFinder.ENOUGH; i++) f.add(frame(i, r, true));
        BugFinder.Box logo = f.logo(null);
        assertNotNull(logo);
        BreakWatch w = new BreakWatch(f.logoMask(logo), W, H);
        assertTrue(w.usable());
        assertTrue(w.present(frame(40, r, true)) > BreakWatch.BACK);
        assertTrue(w.present(frame(41, r, false)) < BreakWatch.GONE);
        // The adverts: gone four checks running.
        for (int i = 0; i < BreakWatch.GONE_CHECKS - 1; i++) assertFalse(w.add(w.present(frame(50 + i, r, false))));
        assertTrue(w.add(w.present(frame(60, r, false))));
        // Back: two checks with it there.
        assertTrue(w.add(w.present(frame(70, r, true))));
        assertFalse(w.add(w.present(frame(71, r, true))));
    }

    @Test public void aMomentWithoutItIsNoBreak() {
        Random r = new Random(6);
        BugFinder f = new BugFinder(W, H);
        for (int i = 0; i < BugFinder.ENOUGH; i++) f.add(frame(i, r, true));
        BreakWatch w = new BreakWatch(f.logoMask(f.logo(null)), W, H);
        assertFalse(w.add(w.present(frame(40, r, false))));
        assertFalse(w.add(w.present(frame(41, r, true))));
        assertFalse(w.add(w.present(frame(42, r, false))));
        assertFalse(w.onBreak());
    }

    @Test public void neverInPlay() {
        Game g = new Game();
        g.state = Game.LIVE;
        assertFalse(BreakWatch.allowed(g));
        g.halftime = true;
        assertTrue(BreakWatch.allowed(g));
        g.halftime = false;
        g.state = Game.PRE;
        assertTrue(BreakWatch.allowed(g));
        g.state = Game.POST;
        assertTrue(BreakWatch.allowed(g));
        assertFalse(BreakWatch.allowed(null));
    }

    @Test public void theGuardGoesRoundAndComesBack() {
        assertEquals(0, BurnGuard.orbit(0)[0]);
        assertEquals(0, BurnGuard.orbit(9)[1]);
        for (int n = 0; n < 20; n++) {
            int[] o = BurnGuard.orbit(n);
            assertTrue(Math.abs(o[0]) <= 1 && Math.abs(o[1]) <= 1);
        }
        assertFalse(BurnGuard.dimmed(0, 4 * 60_000L));
        assertTrue(BurnGuard.dimmed(0, 5 * 60_000L));
    }
}
