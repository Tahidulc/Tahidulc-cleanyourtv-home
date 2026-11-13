package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Back in live play after a goal, from small pictures of the whole frame: never over the replays. */
public class LivePlayTest {
    private static final int W = LivePlay.W, H = LivePlay.H;
    private static final int GRASS = 0xFF2E7A30, GRASS_DARK = 0xFF245F26, STAND = 0xFF59606B, BOARDS = 0xFFD8D8D8;

    /** The main camera's wide shot: stands at the top, the boards along the far touchline, the pitch (in stripes), small players. */
    private static int[] wideShot(int shift) {
        int[] px = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int p;
                if (y < 8) p = STAND + ((x * 7 + y * 3 + shift) % 5) * 0x030303;
                else if (y < 11) p = BOARDS;
                else p = ((x + shift) / 8) % 2 == 0 ? GRASS : GRASS_DARK;
                px[y * W + x] = p;
            }
        }
        // Twenty players, a pixel or two each, here and there.
        for (int i = 0; i < 20; i++) {
            int x = (i * 13 + shift * 3) % (W - 1), y = 13 + (i * 7) % (H - 15);
            px[y * W + x] = i % 2 == 0 ? 0xFFE03030 : 0xFF2030E0;
            px[(y + 1) * W + x] = 0xFF101010;
        }
        return px;
    }

    /** A close-up: the player filling the middle (shirt, face), the pitch behind out of focus. */
    private static int[] closeUp() {
        int[] px = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean player = x >= 18 && x < 46 && y >= 3;
                boolean face = x >= 26 && x < 38 && y >= 3 && y < 14;
                px[y * W + x] = face ? 0xFFC8946E : player ? 0xFF1C2D6E : GRASS;
            }
        }
        return px;
    }

    /** The crowd: no grass at all. */
    private static int[] crowd() {
        int[] px = new int[W * H];
        for (int i = 0; i < px.length; i++) px[i] = 0xFF000000 | (int) ((i * 2654435761L) & 0xFFFFFF) | 0x404040;
        return px;
    }

    /** The wipe into a replay: the broadcaster's colour across the picture. */
    private static int[] wipe() {
        int[] px = new int[W * H];
        java.util.Arrays.fill(px, 0xFF1A2FD6);
        return px;
    }

    @Test
    public void thePitchsGreenUnderLightsAndInShadow() {
        assertTrue(LivePlay.grass(GRASS));
        assertTrue(LivePlay.grass(GRASS_DARK));
        assertTrue(LivePlay.grass(0xFF286E28));
        assertTrue(LivePlay.grass(0xFF5AA03C));
        // Not a white line, a yellow shirt, a teal one, the stands, black.
        assertFalse(LivePlay.grass(0xFFE6F0E6));
        assertFalse(LivePlay.grass(0xFF96A028));
        assertFalse(LivePlay.grass(0xFF288C82));
        assertFalse(LivePlay.grass(STAND));
        assertFalse(LivePlay.grass(0xFF000000));
    }

    @Test
    public void aWideShotIsTheMainCamerasAndACloseUpIsNot() {
        float[] wide = LivePlay.look(wideShot(0), W, H);
        assertTrue("grass " + wide[0], wide[0] > 0.7f);
        assertTrue("shape " + wide[1], wide[1] < 0.05f);
        assertTrue(LivePlay.wide(wide[0], wide[1], LivePlay.LEVEL_MOST));
        float[] close = LivePlay.look(closeUp(), W, H);
        assertTrue("shape " + close[1], close[1] > 0.3f);
        assertFalse(LivePlay.wide(close[0], close[1], LivePlay.LEVEL_MOST));
        float[] c = LivePlay.look(crowd(), W, H);
        assertFalse(LivePlay.wide(c[0], c[1], LivePlay.LEVEL_MOST));
        float[] w = LivePlay.look(wipe(), W, H);
        assertFalse(LivePlay.wide(w[0], w[1], LivePlay.LEVEL_MOST));
    }

    @Test
    public void aCutIsTold() {
        float[] a = LivePlay.squares(wideShot(0), W, H), b = LivePlay.squares(wideShot(1), W, H), c = LivePlay.squares(crowd(), W, H);
        assertTrue(LivePlay.change(a, b) < LivePlay.CUT);
        assertTrue(LivePlay.change(a, c) > LivePlay.CUT);
    }

    @Test
    public void theWideShotMustHoldLongerSoonAfterTheGoal() {
        assertEquals(Long.MAX_VALUE, LivePlay.needMs(10_000L));
        assertEquals(16_000L, LivePlay.needMs(20_000L));
        assertEquals(10_000L, LivePlay.needMs(40_000L));
        assertEquals(4_000L, LivePlay.needMs(60_000L));
        assertEquals(4_000L, LivePlay.needMs(90_000L));
        assertEquals(LivePlay.LEVEL_MOST, LivePlay.level(Float.NaN), 0f);
        assertEquals(LivePlay.LEVEL_LEAST, LivePlay.level(0.3f), 0f);
    }

    /** Pictures every half second from from to to (ms), of this kind. */
    private static long feed(LivePlay p, long from, long to, int[][] kind) {
        long t = from;
        int i = 0;
        for (; t < to; t += 500L, i++) p.frame(kind.length == 1 ? kind[0] : kind[i % kind.length], W, H, t);
        return t;
    }

    private static int[][] wideRun() {
        return new int[][]{wideShot(0), wideShot(1), wideShot(2), wideShot(3)};
    }

    @Test
    public void afterAGoalPlayIsBackOnlyOnceTheReplaysAreOver() {
        LivePlay p = new LivePlay();
        // Two minutes of play: the wide shot learned.
        long t = feed(p, 0, 120_000L, wideRun());
        long goal = t;
        p.goal(goal);
        assertEquals(LivePlay.WAIT, p.verdict(goal + 1_000L));
        // The celebrations (close-ups), a wipe, the replays (one from the main camera, ten seconds), a wipe.
        t = feed(p, goal, goal + 22_000L, new int[][]{closeUp()});
        t = feed(p, t, t + 1_000L, new int[][]{wipe()});
        t = feed(p, t, t + 10_000L, wideRun());
        assertEquals("not over a replay from the main camera", LivePlay.WAIT, p.verdict(t));
        assertTrue(p.holding(t));
        t = feed(p, t, t + 14_000L, new int[][]{closeUp()});
        t = feed(p, t, t + 1_000L, new int[][]{wipe()});
        // The players walking back (close), then the wide shot for the restart.
        t = feed(p, t, t + 8_000L, new int[][]{closeUp()});
        long back = t;
        t = feed(p, t, t + 3_000L, wideRun());
        assertEquals("a moment in: not yet", LivePlay.WAIT, p.verdict(t));
        t = feed(p, t, back + 12_000L, wideRun());
        assertEquals(LivePlay.LIVE, p.verdict(t));
        assertFalse(p.holding(t));
        // Back once: a close-up after it holds nothing.
        feed(p, t, t + 3_000L, new int[][]{closeUp()});
        assertEquals(LivePlay.LIVE, p.verdict(t + 3_000L));
    }

    @Test
    public void theChannelsScoreboardBackFromAReplaySaysSoAtOnce() {
        LivePlay p = new LivePlay();
        long t = feed(p, 0, 60_000L, wideRun());
        p.goal(t);
        long goal = t;
        t = feed(p, t, t + 15_000L, new int[][]{closeUp()});
        p.bug(true, t);
        t = feed(p, t, t + 20_000L, wideRun());
        assertEquals(LivePlay.WAIT, p.verdict(t));
        p.bug(false, t);
        assertEquals(LivePlay.WAIT, p.verdict(t + 500L));
        feed(p, t, t + 2_000L, new int[][]{closeUp()});
        assertEquals(LivePlay.LIVE, p.verdict(t + 2_000L));
        assertTrue(t + 2_000L - goal < 40_000L);
    }

    @Test
    public void aVarCheckHoldsItAndNoPicturesMeanItCannotTell() {
        LivePlay p = new LivePlay();
        long t = feed(p, 0, 60_000L, wideRun());
        p.goal(t);
        t = feed(p, t, t + 15_000L, new int[][]{closeUp()});
        p.var(true);
        t = feed(p, t, t + 40_000L, wideRun());
        assertEquals(LivePlay.WAIT, p.verdict(t));
        p.var(false);
        assertEquals(LivePlay.LIVE, p.verdict(t));
        // No pictures lately: blind (the old timing).
        LivePlay q = new LivePlay();
        long u = feed(q, 0, 10_000L, wideRun());
        q.goal(u);
        assertEquals(LivePlay.BLIND, q.verdict(u + 30_000L));
        assertFalse(q.holding(u + 30_000L));
    }

    @Test
    public void heldItsLongestWithoutPlaySeenItLetsGo() {
        LivePlay p = new LivePlay();
        long t = feed(p, 0, 60_000L, wideRun());
        long goal = t;
        p.goal(goal);
        t = feed(p, t, goal + LivePlay.MOST_MS, new int[][]{closeUp()});
        assertEquals(LivePlay.LATE, p.verdict(goal + LivePlay.MOST_MS));
        assertEquals(LivePlay.LIVE, p.verdict(goal + LivePlay.MOST_MS + 1_000L));
        // Half time or another channel: nothing held.
        p.goal(t);
        p.clear();
        assertEquals(LivePlay.LIVE, p.verdict(t + 1_000L));
    }

    @Test
    public void theRestartPopUpComesWithLivePlay() {
        // Live play seen: at once (but never in the first seconds after the goal).
        assertEquals(1, Moments.restartNow(25_000L, false, LivePlay.LIVE));
        assertEquals(0, Moments.restartNow(10_000L, false, LivePlay.LIVE));
        // A replay the channel's scoreboard stays up in: it waits.
        assertEquals(0, Moments.restartNow(Moments.RESTART_MS + 10_000L, false, LivePlay.WAIT));
        assertEquals(-1, Moments.restartNow(Moments.RESTART_WAIT_MS, false, LivePlay.WAIT));
        assertEquals(-1, Moments.restartNow(Moments.RESTART_WAIT_MS, false, LivePlay.LATE));
        // No pictures: the old timing.
        assertEquals(0, Moments.restartNow(30_000L, false, LivePlay.BLIND));
        assertEquals(1, Moments.restartNow(Moments.RESTART_MS, false, LivePlay.BLIND));
        assertEquals(0, Moments.restartNow(Moments.RESTART_MS, true, LivePlay.BLIND));
    }
}
