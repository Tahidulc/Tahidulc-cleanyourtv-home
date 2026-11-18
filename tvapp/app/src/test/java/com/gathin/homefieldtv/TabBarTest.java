package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;

/**
 * 07's shell (TabBar), the app's main way around: it starts with the remote on Home's tab; Left and Right move the pill;
 * a page still in its own look waits for OK and takes the remote (the bar never over it); Back goes to Home's tab and
 * then leaves; Down goes into the page and Up from Home's top comes back; a screen reached from another shows its back
 * line; the guide key still opens the guide from the bar.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class TabBarTest {
    private ActivityController<TvActivity> tv;

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        tv = null;
        Exec.inline = false;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(TabBar.REST_MS + 100));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    private TvActivity start() {
        Exec.inline = true;
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        return tv.get();
    }

    /** A screen reached from another, in the shell, with its back line. */
    static final class Reached extends Screen {
        Reached(TvActivity tv) {
            super(tv);
        }

        @Override View build() {
            return new View(tv);
        }

        @Override boolean shell() {
            return true;
        }

        @Override String backLine() {
            return "Sports · Today";
        }
    }

    @Test public void theAppStartsWithTheRemoteOnHomesTab() {
        TvActivity a = start();
        TabBar bar = a.tabs();
        assertTrue(bar.active());
        assertTrue(bar.drawn());
        assertEquals(TabBar.HOME, bar.page());
        assertEquals(TabBar.HOME, bar.focus());
        assertTrue(a.top() instanceof HomeScreen);
    }

    @Test public void aPageInItsOwnLookWaitsForOkAndTakesTheRemote() {
        TvActivity a = start();
        TabBar bar = a.tabs();
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        // Rested on Guide: the guide is not in the shell yet, so Home stays under the bar.
        assertEquals(TabBar.GUIDE, bar.focus());
        assertTrue(a.top() instanceof HomeScreen);
        assertTrue(bar.active());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof GuideScreen);
        assertFalse("the guide has the remote", bar.active());
        assertFalse("no bar over the guide's own look", bar.drawn());
        // Back from the guide's root comes home in time; the bar is drawn over Home again.
        for (int i = 0; i < 6 && !(a.top() instanceof HomeScreen); i++) press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() instanceof HomeScreen);
        assertTrue(bar.drawn());
    }

    @Test public void backOnTheBarGoesToHomesTab() {
        TvActivity a = start();
        TabBar bar = a.tabs();
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(TabBar.MOVIES, bar.focus());
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(TabBar.HOME, bar.focus());
        assertTrue(bar.active());
        assertTrue(a.top() instanceof HomeScreen);
    }

    @Test public void downGoesIntoHomeAndUpComesBack() {
        TvActivity a = start();
        TabBar bar = a.tabs();
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertFalse(bar.active());
        assertTrue("the bar stays drawn over Home", bar.drawn());
        assertEquals(TabBar.HOME, bar.page());
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(bar.active());
        assertEquals(TabBar.HOME, bar.focus());
    }

    @Test public void aScreenReachedFromAnotherShowsItsBackLine() {
        TvActivity a = start();
        TabBar bar = a.tabs();
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        a.push(new Reached(a));
        idle();
        assertFalse(bar.active());
        assertTrue(bar.drawn());
        assertEquals("Sports · Today", bar.backWords());
        press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() instanceof HomeScreen);
        assertNull(bar.backWords());
    }

    @Test public void theGuideKeyStillOpensTheGuideFromTheBar() {
        TvActivity a = start();
        TabBar bar = a.tabs();
        assertTrue(bar.active());
        press(a, KeyEvent.KEYCODE_GUIDE);
        assertTrue(a.top() instanceof GuideScreen);
        assertFalse(bar.active());
    }
}
