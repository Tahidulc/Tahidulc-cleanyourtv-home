package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.MadeUpChannels;

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

/**
 * The scoreboard and the logo cover only over a channel playing full screen with nothing over it (BoardGate): away at
 * once with the info card, the OK overlay, the channel list, a number typed or any screen over the player, back with
 * plain playback; pop-ups held meanwhile shown only while fresh.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class BoardOnlyInTheChannelTest {
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
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    /** The live screen's scoreboard layer (its first view). */
    private static View board(LiveScreen s) {
        View v = ((ViewGroup) s.view()).getChildAt(0);
        assertTrue(v instanceof BoardLayer);
        return v;
    }

    @Test public void theRule() {
        assertTrue(BoardGate.plain(true, true, true, false, false));
        assertFalse(BoardGate.plain(false, true, true, false, false));
        assertFalse(BoardGate.plain(true, false, true, false, false));
        assertFalse(BoardGate.plain(true, true, false, false, false));
        assertFalse(BoardGate.plain(true, true, true, true, false));
        assertFalse(BoardGate.plain(true, true, true, false, true));
        long t = 1_000_000L;
        assertTrue(BoardGate.fresh("kickoff", t, t + 60_000L));
        assertFalse(BoardGate.fresh("kickoff", t, t + 3 * 60_000L));
        assertTrue(BoardGate.fresh("restart", t, t + 80_000L));
        assertFalse(BoardGate.fresh("restart", t, t + 100_000L));
        assertFalse(BoardGate.stale(t, t + 60_000L));
        assertTrue(BoardGate.stale(t, t + 3 * 60_000L));
        assertFalse(BoardGate.stale(null, t));
    }

    @Test public void awayWithAnythingOverThePictureBackWithPlainPlayback() {
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        made = app;
        List<Channel> ch = MadeUpChannels.load(app);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        TvActivity a = tv.get();
        idle();
        a.watch(ch.get(2));
        idle();
        LiveScreen live = (LiveScreen) a.top();
        View board = board(live);
        // A channel changed to: its info card at the foot, the board away.
        assertEquals(View.INVISIBLE, board.getVisibility());
        // The card gone (Back): plain playback, the board back.
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(View.VISIBLE, board.getVisibility());
        // The OK overlay.
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(View.INVISIBLE, board.getVisibility());
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(View.VISIBLE, board.getVisibility());
        // The channel list over the picture.
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(View.INVISIBLE, board.getVisibility());
        for (int i = 0; i < 3 && board.getVisibility() != View.VISIBLE; i++) press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() == live);
        assertEquals(View.VISIBLE, board.getVisibility());
        // A number being typed: its box over the picture; Back drops it.
        press(a, KeyEvent.KEYCODE_5);
        assertEquals(View.INVISIBLE, board.getVisibility());
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(View.VISIBLE, board.getVisibility());
        // Settings over the player (its panel over the picture): the board stopped and away; back with the picture.
        a.push(new SettingsScreen(a));
        idle();
        assertEquals(View.INVISIBLE, board.getVisibility());
        press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() == live);
        assertEquals(View.VISIBLE, board.getVisibility());
    }
}
