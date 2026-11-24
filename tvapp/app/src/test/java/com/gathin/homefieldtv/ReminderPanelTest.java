package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Reminders;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.List;

/**
 * The reminder's panel inside Homefield TV (the family's point 2: "when there's a reminder and it pops up over other apps
 * or within the app"): it comes over the screen that is up, Left and Right go between its buttons, Remind again in 5 min
 * keeps the reminder for five minutes on, Back dismisses it. An invented programme.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class ReminderPanelTest {
    @After public void after() {
        Exec.inline = false;
    }

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
    }

    @Test public void thePanelComesInsideTheAppAndRemindsAgain() {
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        TvActivity a = Robolectric.buildActivity(TvActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        long start = System.currentTimeMillis() + 60_000L;
        Reminders.Reminder r = new Reminders.Reminder("x:404", "Harbour TV", "", "The Long Tide", start, start + 3_600_000L);
        ReminderCard.show(a, r);
        assertTrue(a.top() instanceof ReminderScreen);
        // Right to Remind again in 5 min, OK: kept, coming again in five minutes.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(a.top() instanceof ReminderScreen);
        List<Reminders.Reminder> kept = Reminders.list(app, System.currentTimeMillis());
        Reminders.Reminder again = null;
        for (Reminders.Reminder k : kept) if (k.key.equals("x:404")) again = k;
        assertTrue(again != null && again.again > System.currentTimeMillis());
        Reminders.remove(app, "x:404", start);
        // Back dismisses it.
        ReminderCard.show(a, r);
        assertTrue(a.top() instanceof ReminderScreen);
        press(a, KeyEvent.KEYCODE_BACK);
        assertFalse(a.top() instanceof ReminderScreen);
        assertEquals(0, countFor(app));
    }

    private static int countFor(Context app) {
        int n = 0;
        for (Reminders.Reminder k : Reminders.list(app, System.currentTimeMillis())) if (k.key.equals("x:404")) n++;
        return n;
    }
}
