package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.EditorialData;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.engine.Tmdb;
import com.gathin.homefieldtv.engine.Videos;
import com.gathin.homefieldtv.prefs.TrailerPrefs;

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
import java.util.List;

/**
 * A trailer in the poster the remote rests on (Home, Movies and TV Shows), with YouTube's player stood in for: nothing
 * until the remote has rested (1.5 s; longer on Lite), then its best trailer; the card's picture gives way only once it
 * really plays; Right (or up to the tab bar, or the page going) stops it at once; one that fails or never starts is let
 * go for the next, and none left leaves the poster quietly; Trailers off in Settings: none; the app out of sight: the
 * player let go of whole.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class TrailerTest {
    private static final String[] FILMS = {"The Long Harbour (2019)", "Glass Meridian (2021)", "Ember Road (2018)", "Paper Kites (2022)",
            "Midnight Orchard (2020)", "Copper Valley (2017)"};
    private ActivityController<TvActivity> tv;
    private Context app;
    private Stub stub;

    /** YouTube's player stood in for: what it was asked, and it answers when told. */
    static final class Stub implements Trailer.Player {
        final List<String> played = new ArrayList<>();
        int stops, releases;
        Trailer.Listener l;
        View v;

        @Override public View view() {
            if (v == null) v = new View(RuntimeEnvironment.getApplication());
            return v;
        }

        @Override public void play(String key, boolean sound) {
            played.add(key + (sound ? "+sound" : ""));
        }

        @Override public void stop() {
            stops++;
        }

        @Override public void release() {
            releases++;
        }

        @Override public void listen(Trailer.Listener listener) {
            l = listener;
        }
    }

    @After public void after() {
        EditorialPageTest.drain();
        Trailer.standIn = null;
        Motion.force(false);
        if (tv != null) tv.pause().stop().destroy();
        tv = null;
        EditorialData.clear();
        if (app != null) {
            TrailerPrefs.setOn(app, true);
            TrailerPrefs.setSound(app, false);
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
        idle(50);
    }

    /** Home with made-up films (TMDB made up), down on the first row of films, the trailer's player stood in for. */
    private EditorialPage home() {
        EditorialPageTest.drain();
        app = RuntimeEnvironment.getApplication();
        EditorialData.freshCatalog();
        Exec.inline = true;
        stub = new Stub();
        Trailer.standIn = stub;
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
        Catalog.get(app).useForPreview(rows, new java.util.HashMap<>());
        Resume.clear(app);
        EditorialData.load(Catalog.get(app).films(), Catalog.get(app).series());
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle(500);
        EditorialPage home = tv.get().homeScreen().editorial();
        // Every film's trailers as TMDB would give them: a featurette, a teaser and two trailers.
        for (Catalog.Title t : Catalog.get(app).films()) home.previewExtras(t, extras(t));
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        home.previewFocus(firstFilmsRow(home), 0);
        draw(home);
        return home;
    }

    private static int firstFilmsRow(EditorialPage home) {
        List<Editorial.Row> rows = home.view().rows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).cards.size() > 4 && rows.get(i).cards.get(0).title() != null && !rows.get(i).id.equals("continue")) return i;
        }
        throw new AssertionError("no row of films");
    }

    /** A title's made-up videos: its trailer's key is its name's first letters, padded. */
    static Tmdb.Extras extras(Catalog.Title t) {
        List<Videos.Video> vs = new ArrayList<>();
        vs.add(new Videos.Video(key(t, 'f'), "Making of", "Featurette", "en", true, "2024-01-03"));
        vs.add(new Videos.Video(key(t, 's'), "Teaser", "Teaser", "en", true, "2024-01-02"));
        vs.add(new Videos.Video(key(t, 'a'), "Trailer", "Trailer", "en", true, "2024-01-01"));
        vs.add(new Videos.Video(key(t, 'b'), "Trailer 2", "Trailer", "en", false, "2024-02-01"));
        return new Tmdb.Extras("", null, null, "", "12A", vs, false);
    }

    static String key(Catalog.Title t, char kind) {
        String k = (t.name.replaceAll("[^A-Za-z]", "") + "xxxxxxxxxx").substring(0, 10);
        return k + kind;
    }

    /** The page drawn, its glides stepped (the card grown, the page settled). */
    private static void draw(EditorialPage home) {
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888);
        for (int i = 0; i < 3; i++) home.view().draw(new android.graphics.Canvas(b));
        b.recycle();
    }

    private Catalog.Title on(EditorialPage home) {
        return home.view().card().title();
    }

    @Test public void itStartsOnlyOnceTheRemoteHasRestedAndStopsAsItMoves() {
        EditorialPage home = home();
        Trailer t = home.trailer();
        assertEquals(Trailer.RESTING, t.state());
        idle(Trailer.REST_MS - 300);
        assertTrue("nothing before the rest is over", stub.played.isEmpty());
        idle(400);
        assertEquals("its official trailer, muted", List.of(key(on(home), 'a')), stub.played);
        assertEquals(Trailer.LOADING, t.state());
        // The card's picture stays until it really plays.
        stub.l.playing();
        assertEquals(Trailer.SHOWN, t.state());
        // Right: it stops at once, and the next card rests afresh.
        int stops = stub.stops;
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        draw(home);
        assertTrue(stub.stops > stops);
        assertEquals(Trailer.RESTING, t.state());
        assertEquals(1, stub.played.size());
        idle(Trailer.REST_MS + 100);
        assertEquals(key(on(home), 'a'), stub.played.get(1));
    }

    @Test public void oneThatFailsOrNeverStartsGivesWayToTheNextThenThePosterStays() {
        EditorialPage home = home();
        Trailer t = home.trailer();
        idle(Trailer.REST_MS + 100);
        Catalog.Title title = on(home);
        assertEquals(List.of(key(title, 'a')), stub.played);
        // Embedding turned off for it: the next trailer.
        stub.l.failed();
        idle(50);
        assertEquals(key(title, 'b'), stub.played.get(1));
        // Nothing within the time allowed: the teaser.
        idle(Trailer.START_MS + 100);
        assertEquals(key(title, 's'), stub.played.get(2));
        // That fails too: none left (the featurette is never one), the poster stays, nothing said.
        stub.l.failed();
        idle(Trailer.START_MS + 100);
        assertEquals(3, stub.played.size());
        assertEquals(Trailer.DONE, t.state());
    }

    @Test public void theTabBarAndThePageGoingStopItAndTheAppLeavingLetsGoOfThePlayer() {
        EditorialPage home = home();
        idle(Trailer.REST_MS + 100);
        stub.l.playing();
        assertEquals(Trailer.SHOWN, home.trailer().state());
        press(KeyEvent.KEYCODE_BACK);
        assertTrue(tv.get().tabs().active());
        assertEquals(Trailer.IDLE, home.trailer().state());
        // Back down, rested again, playing; then the app goes out of sight.
        tv.get().tabs().leave();
        idle(50);
        home.previewFocus(firstFilmsRow(home), 1);
        draw(home);
        idle(Trailer.REST_MS + 100);
        assertEquals(2, stub.played.size());
        stub.l.playing();
        tv.pause();
        idle(50);
        assertEquals(Trailer.IDLE, home.trailer().state());
        tv.resume();
        idle(50);
    }

    @Test public void liteRestsLongerSoundAndOffAreTheSettings() {
        EditorialPage home = home();
        // Lite (as Settings' Animations would have it): the next card rests longer.
        Motion.force(true);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        draw(home);
        idle(Trailer.REST_MS + 100);
        assertTrue("Lite: not yet", stub.played.isEmpty());
        idle(Trailer.REST_LITE_MS - Trailer.REST_MS);
        assertEquals(1, stub.played.size());
        Motion.force(false);
        // Sound on in Settings: with its sound.
        TrailerPrefs.setSound(app, true);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        draw(home);
        idle(Trailer.REST_MS + 100);
        assertTrue(stub.played.get(1), stub.played.get(1).endsWith("+sound"));
        // Trailers off: none.
        TrailerPrefs.setOn(app, false);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        draw(home);
        idle(Trailer.REST_MS + Trailer.START_MS);
        assertEquals(2, stub.played.size());
    }

    @Test public void theYouTubePageIsTheOfficialPlayerMutedWithTheAppsOwnOrigin() {
        String origin = Trailer.origin("com.gathin.homefieldtv");
        assertEquals("https://com.gathin.homefieldtv", origin);
        String page = Trailer.page(origin);
        assertTrue(page.contains("https://www.youtube.com/iframe_api"));
        assertTrue(page.contains("controls:0"));
        assertTrue(page.contains("mute:1"));
        assertTrue(page.contains("autoplay:1"));
        assertTrue(page.contains("origin:'https://com.gathin.homefieldtv'"));
        // A generic browser's agent: no TV model, no WebView mark; its Chrome kept.
        String ua = Trailer.agent("Mozilla/5.0 (Linux; Android 11; Smart TV Pro Build/RP1A; wv) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Version/4.0 Chrome/118.0.5993.80 Mobile Safari/537.36");
        assertFalse(ua, ua.contains("Smart TV") || ua.contains("wv") || ua.contains("Build/"));
        assertTrue(ua, ua.contains("Chrome/118.0.5993.80"));
        assertNotEquals("", Trailer.agent(null));
    }
}
