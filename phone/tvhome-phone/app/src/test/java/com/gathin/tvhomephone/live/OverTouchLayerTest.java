package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The scoreboard and the cover over the channel's logo are put over the player's touch layer, wherever it stands (the
 * family, 24 Nov: "it's not letting me hold down or double tap the scoreboard and channel logo anymore"): the swipe's
 * veil under the layer had put them under it, by a count from the bottom.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OverTouchLayerTest {
    private static FrameLayout player(Context c, boolean veil) {
        FrameLayout root = new FrameLayout(c);
        root.addView(new View(c)); // the picture
        if (veil) root.addView(new View(c)); // the swipe's veil
        View touch = new View(c);
        touch.setTag(Gestures.LAYER);
        root.addView(touch);
        root.addView(new View(c)); // the subtitles
        root.addView(new View(c)); // the panel and the rest
        return root;
    }

    @Test public void theBoardAndTheCoverGoOverTheTouchLayerWithTheVeilUnderIt() {
        Context c = RuntimeEnvironment.getApplication();
        FrameLayout root = player(c, true);
        View layer = root.getChildAt(2);
        View cover = new View(c), board = new View(c);
        root.addView(cover, Gestures.over(root, 1));
        root.addView(board, Gestures.over(root, 2));
        int t = root.indexOfChild(layer);
        assertTrue("the cover over the touch layer", root.indexOfChild(cover) > t);
        assertTrue("the board over the touch layer", root.indexOfChild(board) > t);
    }

    @Test public void asBeforeWithNoVeil() {
        Context c = RuntimeEnvironment.getApplication();
        FrameLayout root = player(c, false);
        assertEquals("just over the layer, the third child as it always was", 2, Gestures.over(root, 1));
        assertEquals(3, Gestures.over(root, 2));
    }

    @Test public void noLayerFoundItIsTakenAsTheSecondChildAndNeverPastTheEnd() {
        Context c = RuntimeEnvironment.getApplication();
        FrameLayout root = new FrameLayout(c);
        root.addView(new View(c));
        assertEquals(1, Gestures.over(root, 4));
        root.addView(new View(c));
        root.addView(new View(c));
        assertEquals(2, Gestures.over(root, 1));
    }
}
