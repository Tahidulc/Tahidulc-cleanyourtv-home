package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.DayRows;
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

/**
 * A direction held down on the Editorial Home, Movies and TV Shows (the remote's repeats, 20 a second), frame by frame:
 * the remote moves on with every repeat and nothing is read for the titles it only passes (no backdrop, not the page's
 * picture, nothing read ahead) until it is let go; once let go the row (or the page) lands at once, at the glide's own
 * speed and quicker to stop, never gliding on for half a second after the remote; the carousel takes a held key's next
 * step only once its slide is nearly in, so its slides never pile up (and nothing jumps).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class HeldKeysTest {
    private Context app;
    private TvActivity a;
    private View root;
    private Bitmap screen;

    @After public void after() {
        Exec.inline = false;
        EditorialPageTest.drain();
        Motion.force(false);
        if (app != null) {
            Library.get(app).clear();
            Sources.clear(app);
        }
        EditorialData.clear();
        DayRows.now = null;
        Exec.inline = false;
        EditorialData.freshCatalog();
    }

    private void start() throws Exception {
        EditorialPageTest.drain();
        app = RuntimeEnvironment.getApplication();
        EditorialData.freshCatalog();
        Exec.inline = true;
        DayRows.now = System.currentTimeMillis();
        FrameCostData.load(app, new File(app.getCacheDir(), "frame-cost"));
        EditorialData.load(Catalog.get(app).films(), Catalog.get(app).series());
        HomeStyle.set(app, HomeStyle.EDITORIAL);
        Motion.force(false);
        a = Robolectric.buildActivity(TvActivity.class).setup().get();
        idle();
        root = a.getWindow().getDecorView();
        FrameMeter.layout(root);
        screen = Bitmap.createBitmap(FrameMeter.W, FrameMeter.H, Bitmap.Config.ARGB_8888);
        if (a.tabs().active()) a.tabs().leave();
        idle();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** A frame: the clock a sixtieth of a second on, the screen drawn (the glides stepped). */
    private void frame() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        FrameMeter.layout(root);
        root.draw(new Canvas(screen));
    }

    private void frames(int n) {
        for (int i = 0; i < n; i++) frame();
    }

    private void down(int code, int repeat) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t - repeat * 50L, t, KeyEvent.ACTION_DOWN, code, repeat));
    }

    private void up(int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    /** Frames from now until the glide rests (at most n). */
    private int framesToRest(Glide g, int n) {
        int f = 0;
        while (g.moving() && f < n) {
            frame();
            f++;
        }
        return f;
    }

    @Test public void aHeldKeyAlongARowReadsNothingItPassesAndLandsAtOnce() throws Exception {
        start();
        EditorialPage home = a.homeScreen().editorial();
        Editorial view = home.view();
        // The longest row.
        int r = 0;
        for (int i = 0; i < view.rows().size(); i++) if (view.rows().get(i).cards.size() > view.rows().get(r).cards.size()) r = i;
        home.previewFocus(r, 0);
        Exec.inline = false;
        frames(60);
        Editorial.Row row = view.current();
        assertTrue("a long row: " + row.cards.size(), row.cards.size() > 12);

        // The press: as ever (its backdrop, the page's picture).
        down(KeyEvent.KEYCODE_DPAD_RIGHT, 0);
        frames(3);
        String ground = view.previewGround();
        assertEquals(row.cards.get(1).wide, ground);
        int asked = view.widesAsked;
        // Held: a repeat every 50 ms; the remote moves on with each, nothing read for the cards it passes.
        for (int i = 1; i <= 10; i++) {
            down(KeyEvent.KEYCODE_DPAD_RIGHT, i);
            assertTrue(view.holding());
            assertEquals("moved on with the repeat", 1 + i, row.focus);
            frames(3);
            assertEquals("the page's picture waits while the key is held", ground, view.previewGround());
            assertEquals("no backdrop read for a card passed", asked, view.widesAsked);
        }
        up(KeyEvent.KEYCODE_DPAD_RIGHT);
        // Let go: the row lands (HOLD_GAP to know it was let go, then the glide quicker to stop), never half a second on.
        int f = framesToRest(view.previewRowGlide(), 120);
        System.out.println("HELD ROW: landed " + f + " frames after the key was let go");
        assertFalse(view.holding());
        assertTrue("landed " + f + " frames after the key was let go", f * 16 <= Editorial.HOLD_GAP + 3 * Editorial.LAND_MS);
        assertEquals("on its card, at its place", 11, row.focus);
        assertEquals("the page's picture: the card it landed on", row.cards.get(11).wide, view.previewGround());
        frames(2);
        assertTrue("its backdrop asked for once it landed", view.widesAsked > asked || Pics.cached(row.cards.get(11).wide,
                Math.round(Ui.f(Editorial.WIDE_W)), Math.round(Ui.f(Editorial.CARD_H)), Pics.CROP) != null);
    }

    @Test public void aHeldKeyDownTheRowsLandsAtOnce() throws Exception {
        start();
        EditorialPage home = a.homeScreen().editorial();
        Editorial view = home.view();
        home.previewFocus(0, 0);
        Exec.inline = false;
        frames(60);
        assertTrue("rows to go down", view.rows().size() > 5);
        down(KeyEvent.KEYCODE_DPAD_DOWN, 0);
        frames(3);
        String ground = view.previewGround();
        for (int i = 1; i <= 3; i++) {
            down(KeyEvent.KEYCODE_DPAD_DOWN, i);
            frames(3);
            assertEquals("the page's picture waits while the key is held", ground, view.previewGround());
        }
        up(KeyEvent.KEYCODE_DPAD_DOWN);
        int f = framesToRest(view.previewPageGlide(), 120);
        System.out.println("HELD ROWS DOWN: landed " + f + " frames after the key was let go");
        assertTrue("landed " + f + " frames after the key was let go", f * 16 <= Editorial.HOLD_GAP + 3 * Editorial.LAND_MS);
        assertEquals(view.card().wide, view.previewGround());
    }

    @Test public void theCarouselTakesAHeldKeyOneWholeSlideAtATime() throws Exception {
        start();
        a.fromHome(new BrowseScreen(a, false));
        idle();
        if (a.tabs().active()) a.tabs().leave();
        idle();
        EditorialPage movies = ((BrowseScreen) a.top()).editorial();
        assertNotNull(movies);
        Editorial view = movies.view();
        movies.previewFocus(-1, 0);
        Exec.inline = false;
        frames(60);
        assertTrue("a carousel of several", view.heroCount() >= 5);
        down(KeyEvent.KEYCODE_DPAD_RIGHT, 0);
        assertEquals("a press moves at once", 1, view.heroAt());
        frames(3);
        int steps = 0;
        for (int i = 1; i <= 24 && view.heroAt() < view.heroCount() - 1; i++) {
            float slide = view.previewHeroSlide();
            int was = view.heroAt();
            down(KeyEvent.KEYCODE_DPAD_RIGHT, i);
            if (view.heroAt() != was) {
                steps++;
                assertTrue("a held step once the slide is nearly in (" + slide + ")", slide >= Editorial.HERO_HELD);
                // It goes on from where the card in front was: never back to the start of a slide.
                assertTrue("from where it was: " + view.previewHeroSlide(), view.previewHeroSlide() <= 0f
                        && view.previewHeroSlide() >= Editorial.HERO_HELD - 100f);
            }
            for (int k = 0; k < 3; k++) {
                frame();
                assertTrue("at most the one going and the one coming", view.previewHeroDrawn() <= 2);
            }
        }
        up(KeyEvent.KEYCODE_DPAD_RIGHT);
        System.out.println("HELD CAROUSEL: " + steps + " steps in 1.2 s held");
        assertTrue("it went on while held: " + steps, steps >= 3);
        assertTrue("one slide at a time: " + steps, steps <= 6);
    }
}
