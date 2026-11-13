package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** The channel's own scoreboard watched from under yours: replays, and what grows out of it. */
public class BugWatchTest {
    /** A board top left on a 1920 x 1080 screen, over the channel's own. */
    private static BugWatch.Place topLeft() {
        return BugWatch.place(1920, 1080, 60, 40, 480, 54, null);
    }

    /** The picture: grass-ish noise that runs (the camera moving) unless held at a still frame number. */
    private static int ground(int x, int y, int t) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (t * 83492791);
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        int g = 70 + (h & 0x7F), r = 20 + ((h >>> 8) & 0x3F), b = 20 + ((h >>> 16) & 0x3F);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The channel's scoreboard: a navy box with white words (stripes), its clock's last digits changing each second. */
    private static int bugPixel(int x, int y, BugWatch.Rect r, int t) {
        int lx = x - r.l, ly = y - r.t;
        boolean words = ly >= r.height() / 4 && ly < r.height() * 3 / 4 && (lx / 2) % 3 == 0;
        boolean clock = lx >= r.width() * 3 / 4;
        if (clock && words) return ((lx + t) % 3 == 0) ? 0xFFFFFFFF : 0xFF0B1A40;
        return words ? 0xFFF4F4F4 : 0xFF0B1A40;
    }

    /** A strip in the channel's colours (navy, white words). */
    private static int stripPixel(int x, int y, BugWatch.Rect r) {
        int lx = x - r.l, ly = y - r.t;
        boolean words = ly >= r.height() / 3 && ly < r.height() * 2 / 3 && (lx / 2) % 4 == 0;
        return words ? 0xFFFFFFFF : 0xFF0B1A40;
    }

    private static int[] frame(BugWatch.Place p, int t, boolean bug, BugWatch.Rect strip, int still) {
        int[] f = new int[p.fw * p.fh];
        int tt = still >= 0 ? still : t;
        for (int y = 0; y < p.fh; y++) {
            for (int x = 0; x < p.fw; x++) {
                int c = ground(x, y, tt);
                if (bug && x >= p.bug.l && x < p.bug.r && y >= p.bug.t && y < p.bug.b) c = bugPixel(x, y, p.bug, t);
                if (strip != null && x >= strip.l && x < strip.r && y >= strip.t && y < strip.b) c = stripPixel(x, y, strip);
                f[y * p.fw + x] = c;
            }
        }
        return f;
    }

    /** Pictures 400 ms apart, from t0 for count of them. */
    private static int feed(BugWatch w, int t0, int count, boolean bug, BugWatch.Rect strip, boolean stillCamera) {
        BugWatch.Place p = w.place();
        int t = t0;
        for (int i = 0; i < count; i++, t++) w.add(frame(p, t, bug, strip, stillCamera ? t0 : -1), t * 400L);
        return t;
    }

    private static BugWatch learned() {
        BugWatch w = new BugWatch(topLeft());
        feed(w, 0, 36, true, null, false);
        assertTrue("learned in about fourteen seconds", w.known());
        return w;
    }

    @Test
    public void thePlaceReachesTowardTheMiddleAndIsSmall() {
        BugWatch.Place p = topLeft();
        assertNotNull(p);
        // Your board about BOARD_PX high in the small pictures.
        assertTrue(Math.abs(p.core.height() - BugWatch.BOARD_PX) <= 2);
        // Room under it (a top-left board's strips drop down) and to its right, a little above and to its left.
        assertTrue(p.fh - p.core.b >= p.core.height() * 2.5f);
        assertTrue(p.fw - p.core.r >= p.core.width() * 0.8f);
        assertTrue(p.core.t <= p.core.height());
        assertEquals(0, p.x);
        // A board at the foot on the right: room above and to its left instead.
        BugWatch.Place q = BugWatch.place(1920, 1080, 1300, 960, 480, 54, null);
        assertTrue(q.core.t >= q.core.height() * 2.5f);
        assertTrue(q.core.l >= q.core.width() * 0.8f);
        // Back on the screen: the core's box as the board's place.
        int[] s = p.toScreen(p.core);
        assertTrue(Math.abs(s[0] - 60) <= 4 && Math.abs(s[1] - 40) <= 4 && Math.abs(s[2] - 540) <= 4 && Math.abs(s[3] - 94) <= 4);
        assertNull(BugWatch.place(1920, 1080, 0, 0, 2, 2, null));
    }

    @Test
    public void theChannelsBoxFoundWiderIsWatchedWhole() {
        BugWatch.Place p = BugWatch.place(1920, 1080, 60, 40, 400, 54, new int[]{50, 38, 560, 96});
        assertTrue(p.bug.width() > p.core.width());
        assertTrue(p.bug.l <= p.core.l && p.bug.r > p.core.r);
    }

    @Test
    public void notLearnedItIsNeverAway() {
        BugWatch w = new BugWatch(topLeft());
        // An empty place, the picture running: nothing holds still to learn.
        feed(w, 0, 60, false, null, false);
        assertFalse(w.known());
        assertFalse(w.away());
        assertTrue(w.extras().isEmpty());
    }

    @Test
    public void aBlackScreenIsNotLearnedAsTheBoard() {
        BugWatch w = new BugWatch(topLeft());
        BugWatch.Place p = w.place();
        int[] black = new int[p.fw * p.fh];
        java.util.Arrays.fill(black, 0xFF000000);
        for (int i = 0; i < 40; i++) w.add(black, i * 400L);
        assertFalse(w.known());
    }

    @Test
    public void aReplayTakesYoursAwayAndLivePlayBringsItBack() {
        BugWatch w = learned();
        assertFalse(w.away());
        assertTrue(w.presence() > BugWatch.BACK);
        // The replay: the box gone, the picture running where it was. Away after a few pictures.
        int t = feed(w, 100, 2, false, null, false);
        assertFalse("not at the first picture", w.away());
        t = feed(w, t, 2, false, null, false);
        assertTrue(w.away());
        assertTrue(w.presence() < BugWatch.GONE);
        // Live play: back after two.
        t = feed(w, t, 1, true, null, false);
        assertTrue(w.away());
        feed(w, t, 1, true, null, false);
        assertFalse(w.away());
    }

    @Test
    public void aNewLookHoldingStillIsLearnedAndYoursComesBack() {
        BugWatch w = learned();
        BugWatch.Place p = w.place();
        // The second half: another box (white, dark words) where the first was.
        int t = 200;
        for (int i = 0; i < 40 && (i < 4 || w.away()); i++, t++) {
            int[] f = frame(p, t, false, null, -1);
            for (int y = p.bug.t; y < p.bug.b; y++) {
                for (int x = p.bug.l; x < p.bug.r; x++) f[y * p.fw + x] = ((x - p.bug.l) / 2) % 3 == 0 && (y - p.bug.t) > 4 ? 0xFF101010 : 0xFFF0F0F0;
            }
            w.add(f, t * 400L);
            if (i == 4) assertTrue("away while it is new", w.away());
        }
        assertFalse("learned again, back", w.away());
        assertTrue(w.presence() > BugWatch.BACK);
    }

    @Test
    public void gonePastTwoMinutesTheLookIsLetGo() {
        BugWatch w = learned();
        feed(w, 100, 10, false, null, false);
        assertTrue(w.away());
        feed(w, 110, 300, false, null, false);
        assertFalse(w.away());
    }

    @Test
    public void aStripDroppingOutOfItIsFoundAndLetGoWhenItGoes() {
        BugWatch w = learned();
        BugWatch.Place p = w.place();
        // Under the box, as wide as most of it and a little lower than its height.
        BugWatch.Rect strip = new BugWatch.Rect(p.core.l, p.core.b, p.core.l + p.core.width() * 3 / 4, p.core.b + p.core.height() - 2);
        int t = feed(w, 100, 2, true, strip, false);
        assertTrue("not before it has held still", w.extras().isEmpty());
        t = feed(w, t, 2, true, strip, false);
        List<BugWatch.Rect> out = w.extras();
        assertEquals(1, out.size());
        BugWatch.Rect r = out.get(0);
        assertTrue("covers the strip: " + r, r.l <= strip.l + 1 && r.t <= strip.t + 1 && r.r >= strip.r - 1 && r.b >= strip.b - 1);
        assertTrue("no bigger than it, much: " + r, r.width() <= strip.width() + 3 && r.height() <= strip.height() + 3);
        // Gone: its place runs again.
        t = feed(w, t, 1, true, null, false);
        assertEquals(1, w.extras().size());
        feed(w, t, 2, true, null, false);
        assertTrue(w.extras().isEmpty());
        // It comes back in the same place: taken at once.
        w.add(frame(p, 400, true, strip, -1), 400 * 400L);
        w.add(frame(p, 401, true, strip, -1), 401 * 400L);
        w.add(frame(p, 402, true, strip, -1), 402 * 400L);
        assertEquals(1, w.extras().size());
    }

    @Test
    public void aTabBesideItIsFoundToo() {
        BugWatch w = learned();
        BugWatch.Place p = w.place();
        BugWatch.Rect tab = new BugWatch.Rect(p.core.r, p.core.t, p.core.r + p.core.height() * 2, p.core.b);
        feed(w, 100, 5, true, tab, false);
        assertEquals(1, w.extras().size());
        assertTrue(w.extras().get(0).l >= p.core.r - 1);
    }

    @Test
    public void aCameraHeldStillSaysNothing() {
        BugWatch w = learned();
        // The camera stops: everything round your board holds still. Nothing is taken for a graphic.
        feed(w, 100, 12, true, null, true);
        assertTrue(w.extras().isEmpty());
        assertFalse(w.away());
    }

    @Test
    public void somethingStillAwayFromTheBoardIsNotItsGraphic() {
        BugWatch w = learned();
        BugWatch.Place p = w.place();
        // A still box down at the far corner of the place (a hoarding, another graphic): not touching your board.
        BugWatch.Rect far = new BugWatch.Rect(p.fw - 20, p.fh - 10, p.fw - 4, p.fh - 2);
        feed(w, 100, 8, true, far, false);
        assertTrue(w.extras().isEmpty());
    }

    @Test
    public void yourBoardGrowingALittleKeepsWhatWasLearned() {
        BugWatch w = learned();
        // An added-time tab makes it wider: the same watch, its board wider.
        BugWatch.Rect wider = BugWatch.coreIn(w.place(), 60, 40, 540, 54);
        assertNotNull(wider);
        w.setCore(wider);
        assertTrue(w.core().width() > w.place().core.width());
        assertTrue(w.known());
        // Moved across the screen: a new place to watch.
        assertNull(BugWatch.coreIn(w.place(), 1300, 900, 480, 54));
    }

    @Test
    public void nothingOutOfItWhileItIsAway() {
        BugWatch w = learned();
        BugWatch.Place p = w.place();
        BugWatch.Rect strip = new BugWatch.Rect(p.core.l, p.core.b, p.core.r, p.core.b + p.core.height());
        feed(w, 100, 6, false, strip, false);
        assertTrue(w.away());
        assertTrue(w.extras().isEmpty());
    }
}
