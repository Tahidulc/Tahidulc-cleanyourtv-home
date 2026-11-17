package com.gathin.homefieldtv.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Settings → General, Appearance and Remote: the times as shown, the guide's rows, the remote's keys. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LookAndRemoteTest {
    @Test public void timesAsTheyShow() {
        assertEquals("3.05pm", Look.clock(15, 5, false));
        assertEquals("12.00am", Look.clock(0, 0, false));
        assertEquals("12.30pm", Look.clock(12, 30, false));
        assertEquals("15:05", Look.clock(15, 5, true));
        assertEquals("09:07", Look.hm(9, 7, true));
        // A programme's span: one am/pm when both halves share it.
        assertEquals("5.40 — 6.45pm", Look.span(Look.hm(17, 40, false), Look.hm(18, 45, false)));
        assertEquals("11.30am — 12.15pm", Look.span(Look.hm(11, 30, false), Look.hm(12, 15, false)));
        assertEquals("17:40 — 18:45", Look.span(Look.hm(17, 40, true), Look.hm(18, 45, true)));
    }

    @Test public void theGuidesRows() {
        assertEquals(80f, Look.rowHeight(Look.ROWS_NORMAL), 0f);
        assertTrue(Look.rowHeight(Look.ROWS_MORE) < 80f);
        assertTrue(Look.rowHeight(Look.ROWS_FEWER) > 80f);
        assertEquals(Look.TEXT_NAMES.length, Look.TEXT_SCALES.length);
    }

    @Test public void appearanceAsItCameUntilChanged() {
        Context c = RuntimeEnvironment.getApplication();
        Look.load(c);
        // The clock 24-hour as it comes ("Tue, 17 Nov, 10:31"), programme times too.
        assertEquals(Look.CLOCK_24, Look.clock);
        assertTrue(Look.times24);
        assertTrue(Look.numbers);
        assertTrue(Look.progress);
        assertFalse(Look.logos);
        assertEquals(1f, Look.text, 0f);
        Look.setClock(c, Look.CLOCK_OFF);
        Look.setTextSize(c, 3);
        Look.load(c);
        assertEquals(Look.CLOCK_OFF, Look.clock);
        assertEquals(1.2f, Look.text, 0.0001f);
        // A choice made is kept as it is.
        Look.setClock(c, Look.CLOCK_12);
        Look.load(c);
        assertEquals(Look.CLOCK_12, Look.clock);
        Look.setClock(c, Look.CLOCK_24);
        Look.setTextSize(c, 1);
    }

    @Test public void theRemotesKeys() {
        Context c = RuntimeEnvironment.getApplication();
        // As TiviMate's remote: Up and Down change channel, Up to the next number; OK the overlay (BANNER), held the bar.
        assertEquals(1, RemoteKeys.upDown(c));
        assertTrue(RemoteKeys.reversed(c));
        assertEquals(RemoteKeys.BANNER, RemoteKeys.ok(c));
        assertEquals(RemoteKeys.CHANNEL_MENU, RemoteKeys.okLong(c));
        assertEquals(1600, RemoteKeys.waitMs(c));
        // The colour keys, and the Last key always the channel before.
        assertEquals(RemoteKeys.GUIDE, RemoteKeys.action(c, KeyEvent.KEYCODE_PROG_RED));
        assertEquals(RemoteKeys.PREVIOUS, RemoteKeys.action(c, KeyEvent.KEYCODE_PROG_BLUE));
        assertEquals(RemoteKeys.PREVIOUS, RemoteKeys.action(c, KeyEvent.KEYCODE_LAST_CHANNEL));
        assertEquals(RemoteKeys.NOTHING, RemoteKeys.action(c, KeyEvent.KEYCODE_DPAD_UP));
        RemoteKeys.setColour(c, 0, RemoteKeys.FAVOURITE);
        assertEquals(RemoteKeys.FAVOURITE, RemoteKeys.action(c, KeyEvent.KEYCODE_PROG_RED));
        // Something stored that isn't a choice goes back to the first.
        RemoteKeys.setOk(c, RemoteKeys.SEARCH);
        assertEquals(RemoteKeys.BANNER, RemoteKeys.ok(c));
        RemoteKeys.setWaitMs(c, 1234);
        assertEquals(1600, RemoteKeys.waitMs(c));
        // Channel up and down turned round.
        assertEquals(-1, RemoteKeys.zapDir(-1, false));
        assertEquals(1, RemoteKeys.zapDir(-1, true));
        assertEquals(2, RemoteKeys.colourIndex(KeyEvent.KEYCODE_PROG_YELLOW));
        assertEquals(-1, RemoteKeys.colourIndex(KeyEvent.KEYCODE_0));
        assertEquals(RemoteKeys.ACTION_NAMES.length, RemoteKeys.CHANNEL_MENU + 1);
    }

    @Test public void startingUp() {
        Context c = RuntimeEnvironment.getApplication();
        assertEquals(General.START_HOME, General.start(c));
        assertFalse(General.onBoot(c));
        General.setStart(c, General.START_LIVE);
        assertEquals(General.START_LIVE, General.start(c));
        General.setStart(c, 99);
        assertEquals(General.START_NAMES.length - 1, General.start(c));
    }
}
