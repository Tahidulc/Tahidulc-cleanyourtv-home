package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.History;
import com.gathin.homefieldtv.engine.MadeUpChannels;
import com.gathin.homefieldtv.prefs.PlayerPrefs;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.List;

/** The remote while watching, as TiviMate's: pressed and held told apart, CH± only showing, Back to the guide. */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class PlayerKeysTest {
    private ActivityController<TvActivity> tv;
    private Context made;

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        if (made != null) MadeUpChannels.unload(made);
        tv = null;
        made = null;
        Exec.inline = false;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    private static void hold(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 100, KeyEvent.ACTION_DOWN, code, 1));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 120, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    private static String playing(TvActivity a) {
        return a.live.channel() == null ? "" : a.live.channel().key;
    }

    @Test public void theRemoteWhileWatching() {
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        made = app;
        List<Channel> ch = MadeUpChannels.load(app);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        TvActivity a = tv.get();
        idle();
        a.watch(ch.get(3));
        idle();
        assertTrue(a.top() instanceof LiveScreen);
        assertEquals(ch.get(3).key, playing(a));

        // CH+: the next channel's card, the channel playing on; OK then changes to it.
        press(a, KeyEvent.KEYCODE_CHANNEL_UP);
        assertEquals(ch.get(3).key, playing(a));
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(ch.get(4).key, playing(a));

        // Down: the channel before (the row above); Up: the next.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(ch.get(3).key, playing(a));
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(ch.get(4).key, playing(a));

        // Right: the channel watched last; again, back.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(ch.get(3).key, playing(a));
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(ch.get(4).key, playing(a));

        // Left held: the list with the picture in its window; Back to the picture.
        hold(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertTrue(a.top() instanceof ChannelListScreen);
        press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() instanceof LiveScreen);

        // Up held: History.
        hold(a, KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(a.top() instanceof HistoryScreen);
        press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() instanceof LiveScreen);

        // Back: the TV guide.
        press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() instanceof GuideScreen);
    }

    @Test public void theOffsetIsTheChannelsOwnUntilAppliedToAll() {
        Context app = RuntimeEnvironment.getApplication();
        PlayerPrefs.setDelayAll(app, 0);
        PlayerPrefs.setDelayFor(app, "t:1", 150);
        assertEquals(150, PlayerPrefs.delayFor(app, "t:1"));
        assertEquals(0, PlayerPrefs.delayFor(app, "t:2"));
        PlayerPrefs.setDelayAll(app, -100);
        assertEquals(-100, PlayerPrefs.delayFor(app, "t:1"));
        assertEquals(-100, PlayerPrefs.delayFor(app, "t:2"));
        assertEquals(-100, PlayerPrefs.delayMs(app));
        PlayerPrefs.setDelayAll(app, 0);
        // The display mode, applied to all: no channel keeps its own.
        PlayerPrefs.setAspectFor(app, "t:1", PlayerPrefs.ZOOM);
        assertEquals(PlayerPrefs.ZOOM, PlayerPrefs.aspectFor(app, "t:1"));
        PlayerPrefs.setAspectAll(app, PlayerPrefs.REDUCED);
        assertEquals(PlayerPrefs.REDUCED, PlayerPrefs.aspectFor(app, "t:1"));
        PlayerPrefs.setAspectAll(app, PlayerPrefs.FIT);
    }

    @Test public void historyIsKeptOnTheTv() {
        Context app = RuntimeEnvironment.getApplication();
        History.clear(app);
        long now = System.currentTimeMillis();
        History.add(app, new History.Entry("t:1", "The Long Tide", now - 600_000L, now + 600_000L, now - 60_000L, false));
        History.add(app, new History.Entry("t:2", "Northbound", now - 600_000L, now + 600_000L, now, false));
        assertEquals(2, History.list(app, now).size());
        History.remove(app, History.list(app, now).get(0));
        assertEquals("t:2", History.list(app, now).get(0).key);
        History.clear(app);
        assertEquals(0, History.list(app, now).size());
    }
}
