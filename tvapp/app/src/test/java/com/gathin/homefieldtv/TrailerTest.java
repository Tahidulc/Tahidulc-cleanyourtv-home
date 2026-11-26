package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A trailer in the poster the remote rests on (Home, Movies and TV Shows), with YouTube's player stood in for: nothing
 * until the remote has rested 0.7 s on a card that has settled (never while a key is held), then its best trailer,
 * hidden and muted; shown only once it has played clean (HOLD_MS with its time moving: YouTube's own things over its
 * start gone), its sound fading in then; back to the poster before YouTube's end screen, and on a pause, a stall or an
 * error; Right (or up to the tab bar, or the page going) stops it and its sound at once; one that fails or never starts
 * is let go for the next, and none left leaves the poster quietly; its window's maths (cropped, covering, the player laid
 * out small); sound on as it comes (and once for a TV that had it off), no captions; the Trailer button's full-screen
 * player (Back closes it; the TV's YouTube app only when none will play); Continue Watching's cards (an episode: its
 * season's trailer, then its series'), their progress drawn over the trailer; the app out of sight: the player let go.
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
        final List<String> played = new ArrayList<>(), sounds = new ArrayList<>();
        final List<float[]> fits = new ArrayList<>();
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

        @Override public void sound(boolean on, long ms) {
            sounds.add((on ? "up " : "down ") + ms);
        }

        @Override public void fit(float[] place, float radius) {
            fits.add(place);
        }
    }

    /** It plays clean: PLAYING, then its time moving every 250 ms for as long as YouTube's own things are held hidden. */
    static void plays(Stub s, float length) {
        s.l.playing();
        float t = 0f;
        for (long ms = 0; ms <= Trailer.HOLD_MS; ms += 250) {
            idle(250);
            t += 0.25f;
            s.l.time(t, length);
        }
    }

    @After public void after() {
        EditorialPageTest.drain();
        Trailer.standIn = null;
        MarksAhead.lookup = null;
        MarksAhead.clear();
        Motion.force(false);
        if (tv != null) tv.pause().stop().destroy();
        tv = null;
        EditorialData.clear();
        if (app != null) {
            TrailerPrefs.setOn(app, true);
            TrailerPrefs.setSound(app, true);
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
        return home(false);
    }

    /** As home(); continuing: a film and an episode of a series on Continue Watching. */
    private EditorialPage home(boolean continuing) {
        EditorialPageTest.drain();
        app = RuntimeEnvironment.getApplication();
        EditorialData.freshCatalog();
        Exec.inline = true;
        stub = new Stub();
        Trailer.standIn = stub;
        // TMDB stood in for: nothing found for any title unless a test says so.
        MarksAhead.lookup = (c, t) -> null;
        Sources.clear(app);
        Sources.put(app, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "test"));
        List<String> rows = new ArrayList<>();
        rows.add("c\tm\t1\tUK | Comedy Movies");
        if (continuing) rows.add("c\ts\t1\tDrama and crime");
        long added = System.currentTimeMillis() / 1000;
        for (int i = 0; i < FILMS.length; i++) {
            String year = FILMS[i].substring(FILMS[i].length() - 5, FILMS[i].length() - 1);
            rows.add("m\t" + (i + 1) + "\t" + FILMS[i] + "\t1\tfile:/nowhere/f" + i + ".jpg\t7.1\t" + (added - i * 3600L) * 1000L
                    + "\tmkv\t" + year + "\t\t\t");
        }
        Map<String, Catalog.Details> details = new HashMap<>();
        if (continuing) {
            rows.add("s\t1\tThe Quiet Coast\t1\t\t7.4\t" + added * 1000L + "\t2021\tA harbour town keeps its secrets.\t");
            List<String[]> eps = new ArrayList<>();
            for (int k = 1; k <= 4; k++) eps.add(new String[]{"e" + k, "2", String.valueOf(k), "Chapter " + k, "", "", "44", "2021-01-0" + k});
            details.put("p0|s|1", Catalog.previewDetails("A harbour town keeps its secrets.", "Drama", "Rowan Vale", "Ada Holt", "2021", "",
                    "7.9", "15", 44, eps.toArray(new String[0][])));
        }
        Catalog.get(app).useForPreview(rows, details);
        Resume.clear(app);
        if (continuing) {
            long now = System.currentTimeMillis();
            Resume.save(app, new Resume.Point("p0|m|3", "", "Ember Road", "", "", 20 * 60_000L, 100 * 60_000L, now - 60_000L));
            Resume.save(app, new Resume.Point("p0|s|1", "e2", "The Quiet Coast", "S2 E2 · Chapter 2", "", 10 * 60_000L, 44 * 60_000L, now));
        }
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

    // ---- quicker: the rest ------------------------------------------------------------------------------------------

    @Test public void itStartsOnceTheRemoteHasRestedAboutAQuarterOfTheOldWaitAndStopsAsItMoves() {
        assertEquals("the family: \"don't wait 3 seconds\"", 700L, Trailer.REST_MS);
        EditorialPage home = home();
        Trailer t = home.trailer();
        assertEquals(Trailer.RESTING, t.state());
        idle(Trailer.REST_MS - 200);
        assertTrue("nothing before the rest is over", stub.played.isEmpty());
        idle(300);
        assertEquals("its official trailer (its sound to come once shown)", List.of(key(on(home), 'a') + "+sound"), stub.played);
        assertEquals(Trailer.LOADING, t.state());
        assertTrue("its window up, hidden", t.windowUp());
        assertEquals(0f, t.windowAlpha(), 0f);
        // Right: it stops at once, and the next card rests afresh.
        int stops = stub.stops;
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        draw(home);
        assertTrue(stub.stops > stops);
        assertFalse("its window gone", t.windowUp());
        assertEquals(Trailer.RESTING, t.state());
        idle(Trailer.REST_MS + 100);
        assertEquals(key(on(home), 'a') + "+sound", stub.played.get(1));
    }

    @Test public void neverWhileTheCardStillMovesOrAKeyIsHeld() {
        EditorialPage home = home();
        idle(Trailer.REST_MS + 100);
        int was = stub.played.size();
        Editorial view = home.view();
        // Right held down (a repeat every 50 ms for 1.5 s): it never starts, however long it is held.
        Exec.inline = false;
        long t0 = SystemClock.uptimeMillis();
        for (int i = 0; i < 30; i++) {
            long t = SystemClock.uptimeMillis();
            tv.get().dispatchKeyEvent(new KeyEvent(t0, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, i));
            assertFalse("no spot while a key is held", i > 0 && home.spot(new android.graphics.RectF()));
            idle(50);
        }
        assertEquals("nothing while held", was, stub.played.size());
        // Let go: it lands, rests, and only then starts.
        long t = SystemClock.uptimeMillis();
        tv.get().dispatchKeyEvent(new KeyEvent(t0, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
        idle(Editorial.HOLD_GAP + 50);
        assertFalse(view.holding());
        Exec.inline = true;
        draw(home);
        idle(Trailer.REST_MS + 2 * Trailer.SETTLE_MS);
        assertEquals("once it has rested where it landed", was + 1, stub.played.size());
    }

    @Test public void theCarouselsCardOnlyOnceItHasSlidInAndItsOwnStepsWaitWhileOnePlays() {
        EditorialPage home = home();
        home.previewFocus(-1, 0);
        Exec.inline = false;
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        // Still sliding in: no spot yet.
        assertFalse(home.spot(new android.graphics.RectF()));
        // Its slide drawn in, a frame at a time.
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888);
        for (int f = 0; f < 40; f++) {
            idle(16);
            home.view().draw(new android.graphics.Canvas(b));
        }
        b.recycle();
        assertTrue("slid in", home.spot(new android.graphics.RectF()));
        Exec.inline = true;
        idle(Trailer.REST_MS + 2 * Trailer.SETTLE_MS);
        assertEquals(1, stub.played.size());
        assertTrue("the carousel waits for it", home.trailer().busy());
    }

    // ---- no YouTube on screen ----------------------------------------------------------------------------------------

    @Test public void shownOnlyOnceItHasPlayedCleanWithItsTimeMoving() {
        EditorialPage home = home();
        Trailer t = home.trailer();
        idle(Trailer.REST_MS + 100);
        stub.l.playing();
        // Its time stuck (a still, a stall): never shown, however long.
        for (int i = 0; i < 24; i++) {
            idle(250);
            stub.l.time(0.5f, 150f);
        }
        assertEquals("not while its time stands still", Trailer.LOADING, t.state());
        assertEquals(0f, t.windowAlpha(), 0f);
        // A stall while hidden: YouTube's things come back with it, so the hold starts again.
        stub.l.stalled();
        stub.l.playing();
        float at = 0.5f;
        for (long ms = 250; ms < Trailer.HOLD_MS; ms += 250) {
            idle(250);
            at += 0.25f;
            stub.l.time(at, 150f);
            assertEquals("hidden for its first " + ms + " ms of play", Trailer.LOADING, t.state());
        }
        idle(500);
        stub.l.time(at + 0.5f, 150f);
        assertEquals(Trailer.SHOWN, t.state());
        assertEquals(1f, t.windowAlpha(), 0f);
        assertTrue("the page under it kept as one picture", home.view().getLayerType() == View.LAYER_TYPE_HARDWARE);
        // The rules themselves.
        assertFalse(Trailer.clean(Trailer.HOLD_MS, 0.2f, 0.25f));
        assertFalse(Trailer.clean(Trailer.HOLD_MS, 4.0f, 4.0f));
        assertFalse(Trailer.clean(Trailer.HOLD_MS - 1, 4.0f, 4.25f));
        assertFalse(Trailer.clean(Trailer.HOLD_MS, -1f, 4.25f));
        assertTrue(Trailer.clean(Trailer.HOLD_MS, 4.0f, 4.25f));
    }

    @Test public void theVideoCoversItsWindowCroppedAndItsPlayerIsLaidOutSmall() {
        // A row's wide card (760 x 440, lifted 1.02): 16:9 at 1.2 times its height, centred; laid out 360 tall (480p).
        float w = 775f, h = 449f;
        float[] f = Trailer.fit(w, h, Trailer.CROP, Trailer.LAYOUT_CARD);
        assertEquals(h * 1.2f, f[3], 0.01f);
        assertEquals(f[3] * 16f / 9f, f[2], 0.01f);
        assertEquals((w - f[2]) / 2f, f[0], 0.01f);
        assertEquals((h - f[3]) / 2f, f[1], 0.01f);
        float hidden = -f[1] / f[3];
        assertTrue("YouTube's top strip and foot row (8 % of its player) outside the card: " + hidden, hidden >= 0.083f);
        assertEquals(f[3] / 360f, f[4], 1e-4f);
        // The carousel's (1740 x 580): covering it is already more than the crop; laid out 540 tall (720p).
        float[] c = Trailer.fit(1771f, 590f, Trailer.CROP, Trailer.LAYOUT_HERO);
        assertEquals(1771f, c[2], 0.01f);
        assertEquals(1771f * 9f / 16f, c[3], 0.01f);
        assertTrue(-c[1] / c[3] > 0.083f);
        assertEquals(c[3] / 540f, c[4], 1e-4f);
        // Full screen: 1.2 of 1080, laid out 720 tall (1080p).
        float[] s = Trailer.fit(1920f, 1080f, Trailer.CROP, Trailer.LAYOUT_FULL);
        assertEquals(1296f, s[3], 0.01f);
        assertEquals(1.8f, s[4], 1e-4f);
        // Never laid out bigger than it is drawn.
        assertEquals(1f, Trailer.fit(320f, 180f, 1f, 720f)[4], 0f);
        // In the page's CSS px (the TV's density).
        assertEquals("size(-12.50,-5.00,45.00,30.00,1.5000,9.00)", Trailer.sizeCall(new float[]{-25f, -10f, 90f, 60f, 1.5f}, 18f, 2f));
        // The card's window is placed with it.
        EditorialPage home = home();
        idle(Trailer.REST_MS + 100);
        assertEquals(1, stub.fits.size());
        assertEquals(Math.round(Ui.f(Editorial.CARD_H) * 1.02f) * Trailer.CROP, stub.fits.get(0)[3], 1f);
    }

    @Test public void backToThePosterBeforeTheEndScreenAndOnAPauseAStallOrAnError() {
        assertEquals(130f, Trailer.outAt(150f), 0f);
        assertEquals("a short one: a second before its end", 29f, Trailer.outAt(30f), 0f);
        EditorialPage home = home();
        Trailer t = home.trailer();
        idle(Trailer.REST_MS + 100);
        plays(stub, 30f);
        assertEquals(Trailer.SHOWN, t.state());
        int stops = stub.stops;
        stub.l.time(28.5f, 30f);
        stub.l.time(29.05f, 30f);
        assertEquals("faded back a second before its end", Trailer.DONE, t.state());
        assertEquals("its sound faded with the picture", "down " + Trailer.ENDING_MS, stub.sounds.get(stub.sounds.size() - 1));
        assertFalse(home.trailer().busy());
        idle(Trailer.ENDING_MS + 50);
        assertEquals("stopped once faded", stops + 1, stub.stops);
        assertNotEquals("the page no longer kept still", View.LAYER_TYPE_HARDWARE, home.view().getLayerType());
        // A pause or a stall once shown: back to the poster.
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        draw(home);
        idle(Trailer.REST_MS + 100);
        plays(stub, 150f);
        assertEquals(Trailer.SHOWN, t.state());
        stub.l.stalled();
        assertEquals(Trailer.DONE, t.state());
        // An error once shown: the same.
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        draw(home);
        idle(Trailer.REST_MS + 100);
        plays(stub, 150f);
        stub.l.failed();
        assertEquals(Trailer.DONE, t.state());
        // A long one: twenty seconds before its end, before YouTube's end screen can come.
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        draw(home);
        idle(Trailer.REST_MS + 100);
        plays(stub, 150f);
        stub.l.time(129f, 150f);
        assertEquals(Trailer.SHOWN, t.state());
        stub.l.time(130.1f, 150f);
        assertEquals(Trailer.DONE, t.state());
    }

    @Test public void oneThatFailsOrNeverStartsGivesWayToTheNextThenThePosterStays() {
        EditorialPage home = home();
        Trailer t = home.trailer();
        idle(Trailer.REST_MS + 100);
        Catalog.Title title = on(home);
        assertEquals(List.of(key(title, 'a') + "+sound"), stub.played);
        // Embedding turned off for it: the next trailer.
        stub.l.failed();
        idle(50);
        assertEquals(key(title, 'b') + "+sound", stub.played.get(1));
        // Nothing within the time allowed: the teaser.
        idle(Trailer.START_MS + 100);
        assertEquals(key(title, 's') + "+sound", stub.played.get(2));
        // That fails too: none left (the featurette is never one), the poster stays, nothing said.
        stub.l.failed();
        idle(Trailer.START_MS + 100);
        assertEquals(3, stub.played.size());
        assertEquals(Trailer.DONE, t.state());
        assertFalse(t.windowUp());
    }

    // ---- its sound --------------------------------------------------------------------------------------------------

    @Test public void soundIsOnAsItComesAndOnceForATvThatHadItOff() {
        app = RuntimeEnvironment.getApplication();
        android.content.SharedPreferences look = app.getSharedPreferences("look", Context.MODE_PRIVATE);
        look.edit().clear().commit();
        assertTrue("on as it comes", TrailerPrefs.sound(app));
        // A TV that had it off (as it used to come): switched on once.
        look.edit().clear().putBoolean("trailer_sound", false).commit();
        assertTrue(TrailerPrefs.sound(app));
        assertTrue(look.getBoolean("trailer_sound", false));
        // The family turns it off again: it stays off.
        TrailerPrefs.setSound(app, false);
        assertFalse(TrailerPrefs.sound(app));
        assertFalse(TrailerPrefs.sound(app));
        TrailerPrefs.setSound(app, true);
        assertTrue(TrailerPrefs.sound(app));
    }

    @Test public void mutedWhileHiddenItsSoundFadesInAtTheRevealAndStopsAtOnceAsTheRemoteMovesOrTheAppGoes() {
        EditorialPage home = home();
        Trailer t = home.trailer();
        idle(Trailer.REST_MS + 100);
        stub.l.playing();
        idle(Trailer.HOLD_MS - 500);
        stub.l.time(3.5f, 150f);
        assertTrue("no sound while it is hidden", stub.sounds.isEmpty());
        plays(stub, 150f);
        assertEquals(Trailer.SHOWN, t.state());
        assertEquals("faded in over half a second as it is shown", List.of("up " + Trailer.SOUND_IN_MS), stub.sounds);
        assertEquals(500L, Trailer.SOUND_IN_MS);
        // The remote moves: stopped (its sound cut) in the same key press.
        int stops = stub.stops;
        long now = SystemClock.uptimeMillis();
        tv.get().dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
        assertEquals(stops + 1, stub.stops);
        tv.get().dispatchKeyEvent(new KeyEvent(now, now + 40, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
        draw(home);
        // Shown again; then the app leaves the screen: stopped at once, and the player let go.
        idle(Trailer.REST_MS + 100);
        plays(stub, 150f);
        assertEquals(Trailer.SHOWN, t.state());
        stops = stub.stops;
        tv.pause();
        assertEquals(stops + 1, stub.stops);
        assertEquals(Trailer.IDLE, t.state());
        tv.resume();
        idle(50);
        // Muted in Settings: shown, no sound asked.
        TrailerPrefs.setSound(app, false);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        draw(home);
        idle(Trailer.REST_MS + 100);
        int sounds = stub.sounds.size();
        plays(stub, 150f);
        assertEquals(Trailer.SHOWN, t.state());
        assertEquals(sounds, stub.sounds.size());
        assertFalse(stub.played.get(stub.played.size() - 1).endsWith("+sound"));
    }

    @Test public void thePlayersPageHasNoCaptionsNoControlsAndNeedsNoPressForItsSound() {
        String origin = Trailer.origin("com.gathin.homefieldtv");
        assertEquals("https://com.gathin.homefieldtv", origin);
        String page = Trailer.page(origin);
        assertTrue(page.contains("https://www.youtube.com/iframe_api"));
        assertTrue(page.contains("controls:0"));
        assertTrue("starts muted", page.contains("mute:1") && page.contains("hush();p.loadVideoById"));
        assertTrue(page.contains("autoplay:1"));
        assertTrue("no captions asked for", page.contains("cc_load_policy:0"));
        assertTrue("captions turned off once it plays", page.contains("if(e.data==1)quiet()")
                && page.contains("p.unloadModule('captions')") && page.contains("p.setOption('captions','track',{})"));
        assertTrue("and again whenever a module loads", page.contains("onApiChange:function(){quiet();}"));
        assertTrue("the TV's own level at most", page.contains("var to=on?100:0"));
        assertTrue(page.contains("origin:'https://com.gathin.homefieldtv'"));
        // A generic browser's agent: no TV model, no WebView mark; its Chrome kept.
        String ua = Trailer.agent("Mozilla/5.0 (Linux; Android 11; Smart TV Pro Build/RP1A; wv) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Version/4.0 Chrome/118.0.5993.80 Mobile Safari/537.36");
        assertFalse(ua, ua.contains("Smart TV") || ua.contains("wv") || ua.contains("Build/"));
        assertTrue(ua, ua.contains("Chrome/118.0.5993.80"));
        assertNotEquals("", Trailer.agent(null));
        // The WebView itself: its sound never waits for a press, so YouTube never shows its play button.
        Trailer.Web web = new Trailer.Web(RuntimeEnvironment.getApplication());
        assertFalse(web.web().getSettings().getMediaPlaybackRequiresUserGesture());
        web.release();
    }

    // ---- the page, the tab bar, the app ------------------------------------------------------------------------------

    @Test public void theTabBarAndThePageGoingStopItAndTheAppLeavingLetsGoOfThePlayer() {
        EditorialPage home = home();
        idle(Trailer.REST_MS + 100);
        plays(stub, 150f);
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
        plays(stub, 150f);
        tv.pause();
        idle(50);
        assertEquals(Trailer.IDLE, home.trailer().state());
        tv.resume();
        idle(50);
    }

    @Test public void offInSettingsNoneAndLiteTheSameRest() {
        EditorialPage home = home();
        Motion.force(true);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        draw(home);
        idle(Trailer.REST_MS - 200);
        assertTrue("Lite: not before the rest", stub.played.isEmpty());
        idle(300);
        assertEquals(1, stub.played.size());
        plays(stub, 150f);
        assertEquals("Lite: shown at once, no cross-fade", 1f, home.trailer().windowAlpha(), 0f);
        Motion.force(false);
        TrailerPrefs.setOn(app, false);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        draw(home);
        idle(Trailer.REST_MS + Trailer.START_MS);
        assertEquals(1, stub.played.size());
    }

    // ---- Continue Watching ------------------------------------------------------------------------------------------

    @Test public void continueWatchingsCardsPlayTheirTrailersAnEpisodeItsSeasonsThenItsSeriesWithItsProgressOverIt() {
        EditorialPage home = home(true);
        Editorial view = home.view();
        int cw = home.previewRow("continue");
        assertTrue("Continue Watching is on Home", cw >= 0);
        Editorial.Row row = view.rows().get(cw);
        int ep = -1, film = -1;
        for (int i = 0; i < row.cards.size(); i++) {
            Catalog.Title t = row.cards.get(i).title();
            if (t != null && t.series) ep = i;
            else if (t != null) film = i;
        }
        assertTrue(ep >= 0 && film >= 0);
        Catalog.Title series = row.cards.get(ep).title();
        // The series' own trailer (TMDB's), and its second season's.
        List<Videos.Video> vs = new ArrayList<>();
        vs.add(new Videos.Video("QuietCoastA", "Official Trailer", "Trailer", "en", true, "2021-01-01"));
        home.previewExtras(series, new Tmdb.Extras("", null, null, "", "15", vs, false));
        List<Videos.Video> season = new ArrayList<>();
        season.add(new Videos.Video("QuietCoast2", "Season 2 Trailer", "Trailer", "en", true, "2023-01-01"));
        home.previewSeason(series, 2, season);
        // The episode's card: its season's trailer first, then its series'.
        home.previewFocus(cw, ep);
        draw(home);
        assertEquals(List.of("QuietCoast2", "QuietCoastA"), home.previewTrailers(view.card()));
        idle(Trailer.REST_MS + 100);
        assertEquals("QuietCoast2+sound", stub.played.get(stub.played.size() - 1));
        // Shown: the card's progress (and its episode) drawn over the trailer, which plays behind them.
        plays(stub, 150f);
        assertEquals(Trailer.SHOWN, home.trailer().state());
        int drawn = view.progressDrawn;
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888);
        view.over(tv.get()).draw(new android.graphics.Canvas(b));
        b.recycle();
        assertTrue("its progress drawn over its trailer", view.progressDrawn > drawn);
        // A season TMDB has no trailer for: its series'.
        home.previewSeason(series, 2, new ArrayList<>());
        assertEquals(List.of("QuietCoastA"), home.previewTrailers(view.card()));
        // The film on Continue Watching: its own trailer, as in any row.
        home.previewFocus(cw, film);
        draw(home);
        idle(Trailer.REST_MS + 100);
        assertEquals(key(view.card().title(), 'a') + "+sound", stub.played.get(stub.played.size() - 1));
        // The rule itself.
        assertEquals(2, Videos.seasonOf("S2 E2 · Chapter 2"));
        assertEquals(12, Videos.seasonOf("S12:E3"));
        assertEquals(0, Videos.seasonOf("Chapter 2"));
        assertEquals(List.of("QuietCoast2", "QuietCoastA"), Videos.episode(season, vs, "en", ""));
        assertEquals(List.of("QuietCoastA", "playlist001"), Videos.episode(new ArrayList<>(), vs, "en", "playlist001"));
    }

    // ---- the Trailer button: full screen -----------------------------------------------------------------------------

    @Test public void theTrailerButtonPlaysItFullScreenHereBackClosesItAndOnlyNoneWouldPlayOpensYouTube() {
        EditorialPage home = home();
        Catalog.Title title = on(home);
        TitleScreen page = new TitleScreen(tv.get(), title);
        tv.get().push(page);
        idle(500);
        Trailer.fullScreen(tv.get(), List.of(key(title, 'a'), key(title, 'b')));
        idle(500);
        assertTrue(tv.get().top() instanceof Trailer.Full);
        Trailer.Full full = (Trailer.Full) tv.get().top();
        // At once, with its sound to come; black until it plays clean.
        assertEquals(key(title, 'a') + "+sound", stub.played.get(stub.played.size() - 1));
        assertEquals(Trailer.LOADING, full.trailer().state());
        assertEquals(0f, full.trailer().windowAlpha(), 0f);
        float[] place = stub.fits.get(stub.fits.size() - 1);
        assertEquals(1080f * Trailer.CROP, place[3], 1f);
        plays(stub, 150f);
        assertEquals(Trailer.SHOWN, full.trailer().state());
        assertEquals("up " + Trailer.SOUND_IN_MS, stub.sounds.get(stub.sounds.size() - 1));
        // Back: closed, its sound stopped at once, back on the title's page.
        int stops = stub.stops;
        press(KeyEvent.KEYCODE_BACK);
        idle(500);
        assertTrue(stub.stops > stops);
        assertEquals(page, tv.get().top());
        assertNull(shadowOf(tv.get()).getNextStartedActivity());
        // None of them will play here (embedding refused for each): the TV's YouTube app after all.
        Trailer.fullScreen(tv.get(), List.of(key(title, 'a'), key(title, 'b')));
        idle(500);
        stub.l.failed();
        idle(50);
        stub.l.failed();
        idle(500);
        assertEquals(page, tv.get().top());
        Intent yt = shadowOf(tv.get()).getNextStartedActivity();
        assertNotNull("the YouTube app as the last resort", yt);
        assertEquals("https://www.youtube.com/watch?v=" + key(title, 'a'), yt.getDataString());
        // Which it plays: the one asked first; a video not among its trailers: only it.
        List<Videos.Video> vs = extras(title).videos;
        assertEquals(List.of(key(title, 'b'), key(title, 'a'), key(title, 's')), Trailer.keysFor(vs, "en", "", key(title, 'b')));
        assertEquals(List.of(key(title, 'f')), Trailer.keysFor(vs, "en", "", "https://youtu.be/" + key(title, 'f')));
        assertEquals(List.of(key(title, 'a'), key(title, 'b'), key(title, 's')), Trailer.keysFor(vs, "en", "", ""));
    }
}
