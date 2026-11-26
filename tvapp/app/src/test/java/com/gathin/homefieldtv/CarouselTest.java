package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.DayRows;
import com.gathin.homefieldtv.engine.EditorialData;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.engine.Tmdb;
import com.gathin.homefieldtv.engine.Videos;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The carousel at the top of Home, Movies and TV Shows ("a carousel of titles that I can move myself and will move
 * automatically"): on by itself every AUTO_MS, from its last back round to its first; Left and Right by hand, as far as
 * its ends, holding its own steps PAUSE_MS; never moving while the remote is in the rows below; and waiting while a
 * trailer plays in it.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class CarouselTest {
    private static final String[] FILMS = {"The Long Harbour (2019)", "Glass Meridian (2021)", "Ember Road (2018)", "Paper Kites (2022)",
            "Midnight Orchard (2020)", "Copper Valley (2017)"};
    private ActivityController<TvActivity> tv;
    private Context app;

    @After public void after() {
        EditorialPageTest.drain();
        Trailer.standIn = null;
        MarksAhead.clear();
        Motion.force(false);
        if (tv != null) tv.pause().stop().destroy();
        tv = null;
        EditorialData.clear();
        DayRows.now = null;
        if (app != null) {
            Library.get(app).clear();
            Sources.clear(app);
            Resume.clear(app);
        }
        Exec.inline = false;
        EditorialData.freshCatalog();
    }

    private static void idle(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void press(int code) {
        long t = SystemClock.uptimeMillis();
        tv.get().dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        tv.get().dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle(20);
    }

    /** Home with made-up films (TMDB made up: every film trending), the remote down on its carousel. */
    private Editorial home(Trailer.Player player) {
        EditorialPageTest.drain();
        app = RuntimeEnvironment.getApplication();
        EditorialData.freshCatalog();
        Exec.inline = true;
        DayRows.now = System.currentTimeMillis();
        Trailer.standIn = player;
        Sources.clear(app);
        Sources.put(app, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "test"));
        List<String> rows = new ArrayList<>();
        rows.add("c\tm\t1\tUK | Comedy Movies");
        long added = System.currentTimeMillis() / 1000;
        for (int i = 0; i < FILMS.length; i++) {
            String year = FILMS[i].substring(FILMS[i].length() - 5, FILMS[i].length() - 1);
            rows.add("m\t" + (i + 1) + "\t" + FILMS[i] + "\t1\tfile:/nowhere/f" + i + ".jpg\t7.1\t" + (added - i * 3600L) * 1000L
                    + "\tmkv\t" + year + "\t\t\t");
        }
        Catalog.get(app).useForPreview(rows, new HashMap<>());
        Resume.clear(app);
        EditorialData.load(Catalog.get(app).films(), Catalog.get(app).series());
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle(500);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        Editorial view = tv.get().homeScreen().editorial().view();
        assertEquals(Editorial.HERO, view.area());
        assertTrue("a carousel of several", view.heroCount() > 3);
        return view;
    }

    @Test public void itMovesOnByItselfAndRoundFromItsLast() {
        Editorial view = home(null);
        assertEquals(0, view.heroAt());
        idle(EditorialPage.AUTO_MS - 200);
        assertEquals("not yet", 0, view.heroAt());
        idle(300);
        assertEquals(1, view.heroAt());
        idle(EditorialPage.AUTO_MS);
        assertEquals(2, view.heroAt());
        // From its last, round to its first.
        int n = view.heroCount();
        for (int i = 2; i < n - 1; i++) idle(EditorialPage.AUTO_MS);
        assertEquals(n - 1, view.heroAt());
        idle(EditorialPage.AUTO_MS);
        assertEquals(0, view.heroAt());
    }

    @Test public void byHandItGoesAsFarAsItsEndsAndItsOwnStepsWait() {
        Editorial view = home(null);
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals("Left at its first: stays", 0, view.heroAt());
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1, view.heroAt());
        // Its own step waits PAUSE_MS after a move by hand.
        idle(EditorialPage.AUTO_MS + 500);
        assertEquals("held after a move by hand", 1, view.heroAt());
        idle(EditorialPage.PAUSE_MS - EditorialPage.AUTO_MS);
        assertEquals("on by itself again", 2, view.heroAt());
        int n = view.heroCount();
        for (int i = 0; i < n + 2; i++) press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals("Right at its last: stays", n - 1, view.heroAt());
    }

    @Test public void itNeverMovesWhileTheRemoteIsInTheRows() {
        Editorial view = home(null);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(Editorial.ROWS, view.area());
        idle(EditorialPage.AUTO_MS * 3);
        assertEquals(0, view.heroAt());
        // Back up on it: on by itself again.
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(Editorial.HERO, view.area());
        idle(EditorialPage.AUTO_MS + 100);
        assertEquals(1, view.heroAt());
    }

    @Test public void aTrailerInItHoldsItsSteps() {
        TrailerTest.Stub stub = new TrailerTest.Stub();
        Editorial view = home(stub);
        EditorialPage page = tv.get().homeScreen().editorial();
        for (Catalog.Title t : Catalog.get(app).films()) {
            List<Videos.Video> vs = new ArrayList<>();
            vs.add(new Videos.Video(TrailerTest.key(t, 'a'), "Trailer", "Trailer", "en", true, "2024-01-01"));
            page.previewExtras(t, new Tmdb.Extras("", null, null, "", "12A", vs, false));
        }
        // Rested on its card: its trailer plays behind its words.
        page.previewFocus(-1, 0);
        idle(Trailer.REST_MS + 100);
        assertEquals(1, stub.played.size());
        // Its own step waits while it starts hidden, and while it plays.
        TrailerTest.plays(stub, 150f);
        assertEquals(Trailer.SHOWN, page.trailer().state());
        assertEquals("held while its trailer starts", 0, view.heroAt());
        idle(EditorialPage.AUTO_MS * 2);
        assertEquals("held while its trailer plays", 0, view.heroAt());
        // The trailer ends: the poster back, and on it goes shortly after.
        stub.l.ended();
        idle(EditorialPage.AFTER_MS + 100);
        assertEquals(1, view.heroAt());
    }

    /** The page drawn once (its glides stepped as far as the clock has gone). */
    private static void drawOnce(Editorial view) {
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888);
        view.draw(new android.graphics.Canvas(b));
        b.recycle();
    }

    /** Frames drawn a sixtieth of a second apart, as on screen (the glides stepped through). */
    private static void frames(Editorial view, int n) {
        for (int i = 0; i < n; i++) {
            idle(16);
            drawOnce(view);
        }
    }

    @Test public void oneCardAcrossThePageNothingOfTheNextAtItsSide() {
        Editorial view = home(null);
        assertEquals("one card across the page", 1920f - 2 * Editorial.LEFT, Editorial.HERO_W, 0.01f);
        drawOnce(view);
        assertEquals("at rest: the one card alone", 1, view.previewHeroDrawn());
        // Moving on: the old card slides out as the new one slides in, then the new one alone.
        Exec.inline = false;
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        drawOnce(view);
        assertEquals("while it slides: the one going and the one coming", 2, view.previewHeroDrawn());
        frames(view, 60);
        assertEquals(1, view.previewHeroDrawn());
        // Lite: the two cross-fade in place, then the new one alone.
        Motion.force(true);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        drawOnce(view);
        assertEquals(2, view.previewHeroDrawn());
        frames(view, 60);
        assertEquals(1, view.previewHeroDrawn());
        Motion.force(false);
        Exec.inline = true;
        assertEquals(2, view.heroAt());
    }
}
