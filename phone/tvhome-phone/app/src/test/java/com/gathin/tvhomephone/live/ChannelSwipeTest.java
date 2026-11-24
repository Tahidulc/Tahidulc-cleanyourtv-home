package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Swiping between channels (ChannelSwipe): only a clear horizontal fling with one finger changes channel (far, fast,
 * quick, much more across than up or down, not from the strip of the phone's back gesture), left for the next and right
 * for the one before; and nothing else the picture does is taken over (a pinch, an edge's slide, a replay, Move and
 * resize, the touch lock, the match centre, picture in picture, casting, or the setting off).
 */
public class ChannelSwipeTest {
    /** A phone upright at xhdpi: 411dp across, two pixels to a dp. */
    private static final float D = 2f, W = 822f, MID = 411f;

    private static int fling(float startX, float dx, float dy, float vx, float vy, long ms) {
        return ChannelSwipe.direction(startX, W, dx, dy, vx, vy, ms, D);
    }

    @Test public void aClearFlingLeftIsTheNextChannelAndRightTheOneBefore() {
        assertEquals(ChannelSwipe.NEXT, fling(MID, -220, 18, -2400, 150, 160));
        assertEquals(ChannelSwipe.PREVIOUS, fling(MID, 220, -18, 2400, -150, 160));
        assertEquals(1, ChannelSwipe.step(ChannelSwipe.NEXT));
        assertEquals(-1, ChannelSwipe.step(ChannelSwipe.PREVIOUS));
        assertEquals(0, ChannelSwipe.step(ChannelSwipe.NONE));
    }

    @Test public void tooShortOrTooSlowIsNoChange() {
        // 112px is exactly 56dp: just enough; a pixel less is not.
        assertEquals(ChannelSwipe.NEXT, fling(MID, -112, 0, -1400, 0, 120));
        assertEquals(ChannelSwipe.NONE, fling(MID, -111, 0, -1400, 0, 120));
        // 1300px a second is exactly 650dp a second: just enough; slower is not.
        assertEquals(ChannelSwipe.NEXT, fling(MID, -200, 0, -1300, 0, 200));
        assertEquals(ChannelSwipe.NONE, fling(MID, -200, 0, -1299, 0, 200));
    }

    @Test public void aSlowDragIsNotAFling() {
        assertEquals(ChannelSwipe.NONE, fling(MID, -300, 0, -2000, 0, ChannelSwipe.MAX_MS + 1));
        assertEquals(ChannelSwipe.NEXT, fling(MID, -300, 0, -2000, 0, ChannelSwipe.MAX_MS));
        assertEquals(ChannelSwipe.NONE, fling(MID, -300, 0, -2000, 0, -1));
    }

    @Test public void itMustBeClearlyAcross() {
        // Across a little over 2.2 times as far as down: clear; any steeper is not (the guide's swipes up and down, a slide).
        assertEquals(ChannelSwipe.NEXT, fling(MID, -220, 99, -2400, 0, 160));
        assertEquals(ChannelSwipe.NONE, fling(MID, -220, 101, -2400, 0, 160));
        // Its speed too: across at least twice as fast as down.
        assertEquals(ChannelSwipe.NONE, fling(MID, -220, 20, -2400, 1300, 160));
        assertEquals(ChannelSwipe.NONE, fling(MID, 0, -300, 0, -3000, 160));
    }

    @Test public void notFromThePhonesBackGestureStrip() {
        // 24dp in from either side is the phone's own back gesture.
        assertEquals(ChannelSwipe.NONE, fling(30, 220, 0, 2400, 0, 160));
        assertEquals(ChannelSwipe.NONE, fling(W - 30, -220, 0, -2400, 0, 160));
        assertEquals(ChannelSwipe.PREVIOUS, fling(48, 220, 0, 2400, 0, 160));
        assertEquals(ChannelSwipe.NEXT, fling(W - 48, -220, 0, -2400, 0, 160));
    }

    @Test public void aFlingThatTurnedBackIsNone() {
        assertEquals(ChannelSwipe.NONE, fling(MID, -220, 0, 2400, 0, 160));
    }

    @Test public void theThresholdsAreInDpWhateverTheScreen() {
        // A tablet at hdpi (1.5px a dp) and a dense phone (3px a dp): the same swipe in dp is the same answer.
        assertEquals(ChannelSwipe.NEXT, ChannelSwipe.direction(600, 1200, -90, 0, -1000, 0, 150, 1.5f));
        assertEquals(ChannelSwipe.NONE, ChannelSwipe.direction(600, 1200, -80, 0, -1000, 0, 150, 1.5f));
        assertEquals(ChannelSwipe.NONE, ChannelSwipe.direction(600, 1233, -160, 0, -2100, 0, 150, 3f));
        assertEquals(ChannelSwipe.NEXT, ChannelSwipe.direction(600, 1233, -180, 0, -2100, 0, 150, 3f));
    }

    @Test public void nothingElseThePictureDoesIsTakenOver() {
        String[] flags = {"off", "pip", "multiTouch", "edgeSlide", "replaying", "adjusting", "locked", "matchOpen", "casting"};
        for (String f : flags) {
            ChannelSwipe.State s = new ChannelSwipe.State();
            assertFalse(s.blocked());
            assertEquals(ChannelSwipe.NEXT, ChannelSwipe.decide(s, MID, W, -220, 0, -2400, 0, 160, D));
            set(s, f);
            assertTrue(f, s.blocked());
            assertEquals(f, ChannelSwipe.NONE, ChannelSwipe.decide(s, MID, W, -220, 0, -2400, 0, 160, D));
        }
        // No state given: only the fling decides.
        assertEquals(ChannelSwipe.PREVIOUS, ChannelSwipe.decide(null, MID, W, 220, 0, 2400, 0, 160, D));
    }

    private static void set(ChannelSwipe.State s, String flag) {
        switch (flag) {
            case "off": s.off = true; break;
            case "pip": s.pip = true; break;
            case "multiTouch": s.multiTouch = true; break;
            case "edgeSlide": s.edgeSlide = true; break;
            case "replaying": s.replaying = true; break;
            case "adjusting": s.adjusting = true; break;
            case "locked": s.locked = true; break;
            case "matchOpen": s.matchOpen = true; break;
            default: s.casting = true; break;
        }
    }

    @Test public void theOldSwipesStillReadAsBefore() {
        // Gestures' own reading (the guide's picture, and the player with the swipe off) is unchanged.
        assertEquals(Gestures.UP, Gestures.direction(0, -200, 0, -800, 96, 600));
        assertEquals(Gestures.LEFT, Gestures.direction(-200, 0, -800, 0, 96, 600));
        // A swipe the old reading takes as left but that isn't a clear fling: no change of channel.
        assertEquals(Gestures.LEFT, Gestures.direction(-200, 140, -800, 0, 96, 600));
        assertEquals(ChannelSwipe.NONE, fling(MID, -200, 140, -2400, 0, 160));
    }

    @Test public void progressAndDarkFrames() {
        assertEquals(0.5f, TvLook.progress(1000, 3000, 2000), 1e-6f);
        assertEquals(0f, TvLook.progress(1000, 3000, 0), 1e-6f);
        assertEquals(1f, TvLook.progress(1000, 3000, 9000), 1e-6f);
        assertEquals(0f, TvLook.progress(3000, 3000, 3000), 1e-6f);
        assertTrue(TvGround.dark(new int[]{0xFF000000, 0xFF020202, 0xFF050505}));
        assertFalse(TvGround.dark(new int[]{0xFF000000, 0xFF808080, 0xFF205020}));
        assertTrue(TvGround.dark((int[]) null));
    }
}
