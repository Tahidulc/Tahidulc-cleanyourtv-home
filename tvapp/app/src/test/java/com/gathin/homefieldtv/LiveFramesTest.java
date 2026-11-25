package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpGuide;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A channel full screen with nothing changing draws nothing (FrameMeter, a frame every 16 ms of the app's own clock):
 * the family's TCL drew hundreds of frames a minute on the live screen, at 20 to 35 ms each, time its decoder and the
 * stream's reading thread needed. A minute of plain playback (no scoreboard: BoardLayer has its own tests) must draw no
 * frame at all, the information panel only while it rises and drops (its five seconds up are still), and the screen
 * with the minute's tick (the clock's minute, the guide's next programme) one frame a minute at most. Invented channels.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class LiveFramesTest {
    /** A minute of frames at 16 ms. */
    static final int MINUTE = 3_750;
    private ActivityController<TvActivity> tv;
    private Context made;

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        if (made != null) MadeUpGuide.unload(made);
        tv = null;
        Exec.inline = false;
    }

    private TvActivity open() {
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        made = app;
        Sources.clear(app);
        Sources.put(app, "p0", "Riverside", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "test"));
        long now = System.currentTimeMillis(), half = 30 * 60_000L;
        Map<String, Guide.Schedule> guide = new HashMap<>();
        List<Channel> out = new ArrayList<>();
        String[] names = {"Kestrel One", "Harbour One", "Lumen Cinema", "Meadow Life"};
        for (int i = 0; i < names.length; i++) {
            Channel c = new Channel("k:" + (700 + i), names[i], "Entertainment", "", "http://provider.invalid/live/" + i + ".ts", "", null,
                    null, 101 + i);
            out.add(c);
            long[] st = new long[10], en = new long[10];
            for (int k = 0; k < st.length; k++) {
                st[k] = now - now % half - 2 * half + k * half;
                en[k] = st[k] + half;
            }
            guide.put(c.key, MadeUpGuide.schedule(st, en));
        }
        Library lib = Library.get(app);
        lib.useForPreview(out, guide);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        TvActivity a = tv.get();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        a.watch(lib.byKey(out.get(0).key));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        return a;
    }

    @Test public void aChannelFullScreenWithNothingChangingDrawsNothing() {
        TvActivity a = open();
        assertTrue(a.top() instanceof LiveScreen);
        // As on the TV: the animations run (the information panel's rise and drop).
        Exec.inline = false;
        View root = a.getWindow().getDecorView();
        FrameMeter.layout(root);
        FrameMeter meter = new FrameMeter(root);
        // The information panel that came with the channel: past its stay and its drop.
        for (int i = 0; i < 600; i++) meter.tick();

        FrameMeter.Run plain = new FrameMeter.Run("live, plain, a minute");
        meter.frames(plain, MINUTE);
        System.out.println("FRAMES live full screen, nothing changing: " + plain.frames + " drawn in a minute (" + plain.idle + " idle)");
        assertTrue("a frame a minute at most (the minute's tick): " + plain.frames, plain.frames <= 1);

        // INFO: the information panel rises, stays still for its five seconds, and drops away.
        FrameMeter.Run banner = new FrameMeter.Run("live, the information panel");
        FrameMeter.down(a, KeyEvent.KEYCODE_INFO, 0);
        FrameMeter.up(a, KeyEvent.KEYCODE_INFO);
        meter.frames(banner, 600);
        System.out.println("FRAMES the information panel up and down (9.6 s): " + banner.frames + " drawn");
        assertTrue("only while it rises and drops (and its picture comes): " + banner.frames, banner.frames > 0 && banner.frames < 90);
        // And still again.
        FrameMeter.Run after = new FrameMeter.Run("live, plain again");
        meter.frames(after, 600);
        assertEquals(0, after.frames);
    }
}
