package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import com.gathin.broadcast.AiKey;
import com.gathin.homefieldtv.engine.Exec;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowToast;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ask AI's key from the TV launcher on this TV (the phone's "Send key to the TVs" reaches the launcher; its provider's
 * call("aikey") hands it to Homefield TV alone): taken by itself as Settings › Other › Ask AI opens with no key here, and
 * said; taken when asked (Get the key from the TV launcher); never over the TV's own unless asked; not again by itself
 * after Take the key away; nothing taken when no launcher gives it. A fake launcher with a made-up key only.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class AiKeyFromLauncherTest {
    private static final String KEY = "sk-ant-made-up-key-from-the-launcher-1234";
    /** The real ask of the launcher's provider (none on the test's TV). */
    private static final java.util.function.Function<Context, String> REAL = AiSettings.launcher;
    private ActivityController<TvActivity> tv;
    private Context app;
    private final AtomicInteger asked = new AtomicInteger();
    private volatile String launcherHas;

    @Before public void setUp() {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        AiKey.set(app, "");
        app.getSharedPreferences("ai_settings", Context.MODE_PRIVATE).edit().clear().commit();
        AiSettings.forgetAsked();
        launcherHas = KEY;
        AiSettings.launcher = c -> {
            asked.incrementAndGet();
            return launcherHas;
        };
    }

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        tv = null;
        AiKey.set(app, "");
        AiSettings.launcher = REAL;
        AiSettings.forgetAsked();
        Exec.inline = false;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private SettingsScreen page() {
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        TvActivity a = tv.get();
        a.push(new SettingsScreen(a, AiSettings.PAGE));
        idle();
        return (SettingsScreen) a.top();
    }

    private static RowList.Row row(List<RowList.Row> rows, String tag) {
        for (RowList.Row r : rows) if (tag.equals(r.tag)) return r;
        return null;
    }

    @Test public void theAskAiPageTakesTheLaunchersKeyByItselfAndSaysSo() {
        SettingsScreen s = page();
        // Opening the page with no key here asked the launcher, kept its key and said so.
        assertTrue(asked.get() >= 1);
        assertEquals(KEY, AiKey.get(app));
        assertEquals(AiSettings.said(AiSettings.TAKEN), ShadowToast.getTextOfLatestToast());
        List<RowList.Row> rows = s.rows(AiSettings.PAGE);
        // Shown masked, never whole.
        assertEquals("sk-ant-••••••••1234", row(rows, "ai:key").value);
        for (RowList.Row r : rows) {
            assertFalse(String.valueOf(r.title).contains("made-up"));
            assertFalse(String.valueOf(r.value).contains("made-up"));
        }
        // With a key here the launcher is not asked again by itself.
        int before = asked.get();
        s.rows(AiSettings.PAGE);
        idle();
        assertEquals(before, asked.get());

        // Take the key away: not taken back by itself...
        s.chosen(null, row(rows, "ai:forget"));
        idle();
        assertEquals("", AiKey.get(app));
        AiSettings.forgetAsked();
        List<RowList.Row> without = s.rows(AiSettings.PAGE);
        idle();
        assertEquals("", AiKey.get(app));
        // ...only when asked: Get the key from the TV launcher.
        RowList.Row get = row(without, "ai:launcher");
        assertNotNull(get);
        assertEquals("Get the key from the TV launcher", get.title);
        s.chosen(null, get);
        idle();
        assertEquals(KEY, AiKey.get(app));
        assertEquals(AiSettings.said(AiSettings.TAKEN), ShadowToast.getTextOfLatestToast());
    }

    @Test public void theTvsOwnKeyIsKeptUnlessAsked() {
        String own = "sk-ant-made-up-key-typed-on-this-tv-5678";
        AiKey.set(app, own);
        int[] got = {99};
        AiSettings.fromLauncher(app, false, r -> got[0] = r);
        idle();
        assertEquals(AiSettings.OWN_KEPT, got[0]);
        assertEquals(own, AiKey.get(app));
        AiSettings.fromLauncher(app, true, r -> got[0] = r);
        idle();
        assertEquals(AiSettings.TAKEN, got[0]);
        assertEquals(KEY, AiKey.get(app));
    }

    @Test public void noLauncherOrNoKeyThereTakesNothing() {
        assertEquals(AiSettings.NO_LAUNCHER, AiSettings.outcome(null));
        assertEquals(AiSettings.NONE_THERE, AiSettings.outcome(""));
        assertEquals(AiSettings.NONE_THERE, AiSettings.outcome("  "));
        assertEquals(AiSettings.TAKEN, AiSettings.outcome(KEY));
        int[] got = {99};
        launcherHas = "";
        AiSettings.fromLauncher(app, true, r -> got[0] = r);
        idle();
        assertEquals(AiSettings.NONE_THERE, got[0]);
        assertEquals("", AiKey.get(app));
        launcherHas = null;
        AiSettings.fromLauncher(app, true, r -> got[0] = r);
        idle();
        assertEquals(AiSettings.NO_LAUNCHER, got[0]);
        assertEquals("", AiKey.get(app));
        // What it says names no key.
        for (int o : new int[]{AiSettings.TAKEN, AiSettings.NONE_THERE, AiSettings.NO_LAUNCHER, AiSettings.OWN_KEPT}) {
            assertFalse(AiSettings.said(o).contains("sk-ant"));
        }
        // The real ask, with no launcher on this test's TV: nothing, and no error.
        assertEquals(AiSettings.NO_LAUNCHER, AiSettings.outcome(REAL.apply(app)));
    }
}
