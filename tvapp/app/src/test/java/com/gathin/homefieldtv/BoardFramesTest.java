package com.gathin.homefieldtv;

import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpChannels;
import com.gathin.homefieldtv.sports.BoardClock;
import com.gathin.homefieldtv.sports.Game;
import com.gathin.homefieldtv.sports.InStep;

import org.junit.After;
import org.junit.Before;
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
import java.util.Collections;
import java.util.List;

/**
 * The scoreboard over the live picture draws only when something on it changes (FrameMeter, a frame every 16 ms of the
 * app's own clock): the family's TCL drew hundreds of frames a minute with the board up, 20 to 35 ms each, time the
 * player needed. A minute with the board and the cover up and nothing happening (a live game, its clock running, no
 * goal, nothing out beside it) draws about one frame a second at most, for the clock, and only small parts of the screen
 * (the board, the cover); on Lite the same or fewer, with nothing decorative moving. In every look the boards have.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class BoardFramesTest {
    /** FRAMES_REPORT set: every number printed, nothing asserted (to measure before and after a change). */
    private static final boolean REPORT = System.getenv("FRAMES_REPORT") != null;

    private static void check(String what, boolean ok) {
        if (!REPORT) assertTrue(what, ok);
    }

    private ActivityController<TvActivity> tv;
    private Context app;
    private List<Channel> ch;

    @Before public void setUp() {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        clear();
        AlertSwitches.reset();
        AlertSwitches.launcher = (c, q) -> null;
        LogoHere.launcher = (c, q) -> null;
        ch = MadeUpChannels.load(app);
        List<String> recent = new ArrayList<>();
        for (Channel c : ch) recent.add(c.key);
        Library.get(app).setPicks(new ArrayList<>(), recent);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
    }

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        Library.get(app).setPicks(new ArrayList<>(), new ArrayList<>());
        MadeUpChannels.unload(app);
        clear();
        Motion.force(false);
        tv = null;
        Exec.inline = false;
    }

    private void clear() {
        for (String p : new String[]{"scoreboard", "remote", "in_step", "sync_store", "logo_extras", "motion"}) {
            app.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    /** The board up over a live game on channel 3, its info card gone: nothing else to happen (no reads, no find). */
    private BoardLayer boardUp() throws Exception {
        TvActivity a = tv.get();
        a.watch(ch.get(3));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200));
        FrameMeter.down(a, KeyEvent.KEYCODE_BACK, 0);
        FrameMeter.up(a, KeyEvent.KEYCODE_BACK);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800));
        LiveScreen live = (LiveScreen) a.top();
        BoardLayer b = (BoardLayer) ((ViewGroup) live.view()).getChildAt(0);
        InStep.setDelay(app, ch.get(3).key, 0);
        long now = System.currentTimeMillis();
        b.found(BoardLikeTiviMateTest.match("frames-1"),
                Collections.singletonList(new BoardClock.Reading(now - 3_000L, 1, 0, Game.LIVE, false, 67 * 60, "68'")));
        // Nothing read or looked for again within the test's minutes (no network, nothing new).
        for (String f : new String[]{"nextFind", "nextPoll", "nextSummary"}) {
            java.lang.reflect.Field x = BoardLayer.class.getDeclaredField(f);
            x.setAccessible(true);
            x.setLong(b, Long.MAX_VALUE);
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200));
        assertTrue(b.bug.getVisibility() == View.VISIBLE);
        assertTrue(b.coverUp());
        last = b;
        return b;
    }

    /** A minute measured with the board up and nothing happening, after its coming in has settled. */
    private FrameMeter.Run minute(String name) throws Exception {
        return measured(name, LiveFramesTest.MINUTE);
    }

    /** n frames (16 ms each) measured with the board up and nothing happening, after its coming in has settled. */
    private FrameMeter.Run measured(String name, int n) throws Exception {
        boardUp();
        TvActivity a = tv.get();
        // As on the TV: the animations run.
        Exec.inline = false;
        View root = a.getWindow().getDecorView();
        FrameMeter.layout(root);
        FrameMeter meter = new FrameMeter(root);
        // Its coming in, and the first seconds.
        for (int i = 0; i < 300; i++) meter.tick();
        FrameMeter.Run run = new FrameMeter.Run(name);
        meter.frames(run, n);
        System.out.printf(java.util.Locale.ROOT, "FRAMES %s: %d drawn in %d s (%d idle), damage %.2f%% of the screen a frame%n",
                name, run.frames, n * 16 / 1000, run.idle, run.damagePerFrame() * 100);
        return run;
    }

    /** The board measured last: its view's share of the screen. */
    private BoardLayer last;

    private double boardShare() {
        return last == null ? 0 : (double) last.bug.getWidth() * last.bug.getHeight() / (1920.0 * 1080.0);
    }

    /** The app opened afresh with this look, on this tier. */
    private void fresh(BoardBug.Style s, boolean lite) {
        if (tv != null) tv.pause().stop().destroy();
        Motion.force(lite);
        Exec.inline = true;
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        BoardPrefs.setStyle(app, s);
    }

    /** A minute of the family's look (By competition), on Full and on Lite. */
    @Test public void aMinuteWithTheBoardUpAndNothingHappening() throws Exception {
        fresh(BoardBug.Style.COMPETITION, false);
        FrameMeter.Run full = minute("Full, By competition, a minute");
        check("about one frame a second at most (the clock): " + full.frames, full.frames <= 62);
        check("only the board's part of the screen: " + full.damagePerFrame(), full.damagePerFrame() < 0.02);
        fresh(BoardBug.Style.COMPETITION, true);
        FrameMeter.Run lite = minute("Lite, By competition, a minute");
        check("a frame a second at most on Lite: " + lite.frames, lite.frames <= 60);
        check("only the board's part of the screen on Lite: " + lite.damagePerFrame(), lite.damagePerFrame() < 0.02);
        // The family's Event logo with its extras (Up next, the prayer pill, the clock): still too, nothing out.
        fresh(BoardBug.Style.COMPETITION, true);
        BoardPrefs.setLogoLook(app, "EVENT_NAVY");
        BoardLayer.previewChannelName = "UK: Sky Sports Main Event UHD HDR10+ ATMOS";
        try {
            FrameMeter.Run event = minute("Lite, Event logo with its extras, a minute");
            check("Event: a frame a second at most: " + event.frames, event.frames <= 60);
            check("Event: only the board's part of the screen: " + event.damagePerFrame(), event.damagePerFrame() < 0.02);
        } finally {
            BoardLayer.previewChannelName = null;
        }
    }

    /** Another game's goal out of the board: it moves while it comes out and runs along, then all is still again. */
    @Test public void anAlertMovesWhileItIsOutThenAllIsStill() throws Exception {
        fresh(BoardBug.Style.COMPETITION, true);
        boardUp();
        TvActivity a = tv.get();
        Exec.inline = false;
        View root = a.getWindow().getDecorView();
        FrameMeter.layout(root);
        FrameMeter meter = new FrameMeter(root);
        for (int i = 0; i < 300; i++) meter.tick();
        BoardInbox.offer(BoardLikeTiviMateTest.otherGoal("frames-other", "2–1", "Alex Player 67'"));
        FrameMeter.Run out = new FrameMeter.Run("Lite, another game's goal out (12 s)");
        meter.frames(out, 750);
        FrameMeter.Run after = new FrameMeter.Run("Lite, after it (20 s)");
        meter.frames(after, 1250);
        System.out.printf(java.util.Locale.ROOT, "FRAMES %s: %d; %s: %d, damage %.2f%%%n", out.name, out.frames, after.name, after.frames,
                after.damagePerFrame() * 100);
        check("it moved while it came out: " + out.frames, out.frames > 5);
        check("then a frame a second at most: " + after.frames, after.frames <= 21);
        check("then the board's part only: " + after.damagePerFrame(), after.damagePerFrame() < 0.02);
    }

    /** Every look the boards have, ten seconds each, on Full and on Lite: about a frame a second, small parts only. */
    @Test public void everyLookStillWhenNothingHappens() throws Exception {
        int n = 625;
        for (boolean lite : new boolean[]{false, true}) {
            for (BoardBug.Style s : BoardPrefs.looks()) {
                fresh(s, lite);
                FrameMeter.Run run = measured((lite ? "Lite " : "Full ") + s.name(), n);
                check(s + (lite ? " on Lite" : "") + ": about a frame a second at most: " + run.frames, run.frames <= 12);
                // Only the board drawn again (a wide competition look is wider): never the cover, nor the strip between.
                double board = boardShare();
                check(s + ": the board's own part of the screen only: " + run.damagePerFrame() + " (the board " + board + ")",
                        run.damagePerFrame() <= board * 1.02 + 1e-4 && run.damagePerFrame() < 0.05);
            }
        }
    }
}
