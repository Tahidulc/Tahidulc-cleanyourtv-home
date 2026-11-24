package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.EditorialData;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.FrameCostData;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.HomeStyle;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The frame budget of the Editorial Home, Movies and TV Shows while the remote moves, as FrameCostTest measures every
 * other 07 screen (FrameMeter, the made-up family playlist of FrameCostData with its drawn pictures, TMDB's lists made up
 * by EditorialData): along a row (the card left shrinking to its poster as the next grows into its backdrop, the row
 * sliding), Continue Watching, the rows down (the page rising, the next row dimmed), the carousels along and from them
 * down into the rows; in the Full tier and in Lite. Each frame must make next to nothing on the heap, no shadow may be
 * made while moving, and Lite draws nothing in perspective and nothing in a layer.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class EditorialFrameTest {
    private Context app;
    private TvActivity a;
    private FrameMeter meter;
    private final List<FrameMeter.Run> runs = new ArrayList<>();

    @After public void after() {
        Exec.inline = false;
        EditorialPageTest.drain();
        if (app != null) {
            Library.get(app).clear();
            Sources.clear(app);
        }
        EditorialData.clear();
        Exec.inline = false;
        // The next test's own app makes its own catalogue.
        EditorialData.freshCatalog();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void start() throws Exception {
        EditorialPageTest.drain();
        app = RuntimeEnvironment.getApplication();
        EditorialData.freshCatalog();
        Exec.inline = true;
        FrameCostData.load(app, new File(app.getCacheDir(), "frame-cost"));
        EditorialData.load(Catalog.get(app).films(), Catalog.get(app).series());
        HomeStyle.set(app, HomeStyle.EDITORIAL);
        a = Robolectric.buildActivity(TvActivity.class).setup().get();
        idle();
        View root = a.getWindow().getDecorView();
        FrameMeter.layout(root);
        meter = new FrameMeter(root);
    }

    /** Warm: the motion once with everything in place at once, then once as it will be measured; back each time. */
    private void warm(Runnable motion, Runnable back) {
        Exec.inline = true;
        for (int i = 0; i < 3; i++) meter.tick();
        motion.run();
        for (int i = 0; i < 3; i++) meter.tick();
        back.run();
        for (int i = 0; i < 3; i++) meter.tick();
        Exec.inline = false;
        for (int i = 0; i < 70; i++) meter.tick();
        motion.run();
        for (int i = 0; i < 40; i++) meter.tick();
        back.run();
        for (int i = 0; i < 70; i++) meter.tick();
    }

    private void keys(int code, int n) {
        for (int i = 0; i < n; i++) {
            FrameMeter.down(a, code, 0);
            FrameMeter.up(a, code);
            for (int f = 0; f < 4; f++) meter.tick();
        }
    }

    private void measure(String name, int code, int back, int n) {
        warm(() -> keys(code, n), () -> keys(back, n));
        FrameMeter.Run run = new FrameMeter.Run(name);
        meter.hold(a, run, code, n);
        meter.hold(a, run, back, n);
        meter.press(a, run, code);
        runs.add(run);
    }

    private void leaveBar() {
        if (a.tabs().active()) a.tabs().leave();
        idle();
    }

    /** The remote on a page's row by its id (CategorySet), its first card. */
    private void onRow(EditorialPage page, String id) {
        int i = page.previewRow(id);
        assertTrue(id + " is shown", i >= 0);
        page.previewFocus(i, 0);
        for (int f = 0; f < 30; f++) meter.tick();
    }

    @Test public void theEditorialPagesMoveWithinTheirBudget() throws Exception {
        start();
        Motion.force(false);
        // Once along a row before anything is measured: the JVM's first calls of each drawing path make garbage.
        leaveBar();
        onRow(a.homeScreen().editorial(), "latest.releases");
        warm(() -> keys(KeyEvent.KEYCODE_DPAD_RIGHT, 8), () -> keys(KeyEvent.KEYCODE_DPAD_LEFT, 8));
        a.tabs().enter();
        idle();
        scenarios(" (Full)");
        a.home();
        idle();
        Motion.force(true);
        scenarios(" (Lite)");
        Motion.force(false);

        StringBuilder b = new StringBuilder("\nEDITORIAL FRAME COST (JVM, a frame of motion; ops: calls drawn, rec: recorded on the TV's canvas;"
                + " fill and damage in whole 1920x1080 screens)\n");
        b.append(FrameMeter.Run.header()).append('\n');
        for (FrameMeter.Run r : runs) b.append(r.row()).append('\n');
        if (FrameMeter.TRACE) for (FrameMeter.Run r : runs) b.append(r.views()).append('\n');
        System.out.println(b);
        for (FrameMeter.Run r : runs) {
            assertTrue(r.name + " drew no frame", r.frames > 0);
            assertTrue(r.name + ": a frame made " + r.bytesMedian() + " bytes", r.bytesMedian() <= FrameCostTest.FRAME_BYTES);
            assertTrue(r.name + ": frames made " + r.bytesPerFrame() + " bytes on average", r.bytesPerFrame() <= FrameCostTest.MEAN_BYTES);
            assertEquals(r.name + ": shadows made while moving", 0, r.sprites);
            if (r.name.endsWith("(Lite)")) {
                assertEquals(r.name + ": drawn in perspective (Lite has no tilt)", 0.0, r.perspectivePerFrame(), 0.0);
                assertEquals(r.name + ": offscreen layers (Lite has none)", 0.0, r.layersPerFrame(), 0.0);
            }
        }
    }

    private void scenarios(String tier) {
        // Home: along a row (the card left shrinking to a poster as the next grows), then down the rows.
        leaveBar();
        EditorialPage home = a.homeScreen().editorial();
        assertNotNull("Home is Editorial as it comes", home);
        onRow(home, "latest.releases");
        measure("Editorial Home: a row" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 6);
        onRow(home, "continue");
        measure("Editorial Home: Continue Watching" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 2);
        measure("Editorial Home: rows down" + tier, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, 3);

        // Movies: its carousel along, from it down to the rows, along a row.
        a.fromHome(new BrowseScreen(a, false));
        idle();
        leaveBar();
        EditorialPage movies = ((BrowseScreen) a.top()).editorial();
        assertNotNull("Movies is Editorial as it comes", movies);
        movies.previewFocus(-1, 0);
        measure("Editorial Movies: carousel" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 4);
        movies.previewFocus(-1, 0);
        measure("Editorial Movies: carousel to rows" + tier, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, 1);
        onRow(movies, "trending");
        measure("Editorial Movies: a row" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 6);
        measure("Editorial Movies: rows down" + tier, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, 3);

        // TV Shows: its carousel, a streaming service's row.
        a.home();
        idle();
        a.fromHome(new BrowseScreen(a, true));
        idle();
        leaveBar();
        EditorialPage shows = ((BrowseScreen) a.top()).editorial();
        shows.previewFocus(-1, 0);
        measure("Editorial TV Shows: carousel" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 3);
        onRow(shows, "network.3186");
        measure("Editorial TV Shows: a service" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 4);
        a.home();
        idle();
    }
}
