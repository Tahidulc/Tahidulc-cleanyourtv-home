package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpCards;
import com.gathin.homefieldtv.engine.MadeUpChannels;
import com.gathin.homefieldtv.engine.PlayRequest;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.Parental;

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

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The launcher's channel, immediately (PlayChannel, TvActivity's fast path): started afresh, the channel plays from the
 * channels kept on the TV with nothing else made first (no Home, no What's new, no update look, no playlist refresh: those
 * once the picture is up, or at the next ordinary start); running, the channel changes in place on the same screen,
 * whether the app was in front or out of sight; a locked channel still asks for its PIN; Back from the picture is the
 * guide, as from the app's own channels. And the main thread's work from the intent to the player told to play, cold and
 * warm (printed: TIMING). Invented channels.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class PlayChannelFastTest {
    private Context app;
    private final List<ActivityController<TvActivity>> made = new ArrayList<>();
    /** When the player was told to play the channel looked for (LiveTv.play keeps it as the last channel at once). */
    private volatile long playedAt;
    private volatile String want;
    private final SharedPreferences.OnSharedPreferenceChangeListener told = (p, k) -> {
        if ("last".equals(k) && playedAt == 0 && want != null && want.equals(p.getString("last", null))) playedAt = System.nanoTime();
    };

    @Before public void setUp() {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        clear();
        MadeUpCards.load(app);
        liveTv().registerOnSharedPreferenceChangeListener(told);
    }

    @After public void after() {
        liveTv().unregisterOnSharedPreferenceChangeListener(told);
        for (ActivityController<TvActivity> c : made) c.pause().stop().destroy();
        made.clear();
        MadeUpChannels.unload(app);
        clear();
        Exec.inline = false;
    }

    private void clear() {
        for (String p : new String[]{"channel_edits", "live_groups", "parental", "live_tv"}) {
            app.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().commit();
        }
        Updates.prefs(app).edit().clear().commit();
        ChannelEdits.forget();
        Parental.forget();
    }

    private static void idle(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** The launcher's PLAY_CHANNEL as ChannelOpen sends it: the one wanted, every name, when. */
    private static Intent play(String... names) {
        return new Intent(PlayRequest.ACTION).putExtra(PlayRequest.EXTRA_NAME, names[0]).putExtra(PlayRequest.EXTRA_NAMES, names)
                .putExtra(PlayRequest.EXTRA_AT, System.currentTimeMillis());
    }

    private static String playing(TvActivity a) {
        Channel c = a.live.channel();
        return c == null ? "" : c.key;
    }

    private static List<Screen> stack(TvActivity a) throws ReflectiveOperationException {
        Field f = TvActivity.class.getDeclaredField("stack");
        f.setAccessible(true);
        @SuppressWarnings("unchecked") List<Screen> l = (List<Screen>) f.get(a);
        return new ArrayList<>(l);
    }

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle(100);
    }

    /** Just updated, with notes: What's new would come up on an ordinary start. */
    private void whatsNewWaiting() {
        Updates.prefs(app).edit().putLong("seen_code", 1).putInt("to", (int) Updates.myCode(app)).putString("to_notes", "Faster channels.").commit();
    }

    private ActivityController<TvActivity> start(Intent i) {
        ActivityController<TvActivity> c = i == null ? Robolectric.buildActivity(TvActivity.class) : Robolectric.buildActivity(TvActivity.class, i);
        made.add(c.setup());
        return c;
    }

    @Test public void startedAfreshTheChannelPlaysWithNothingElseMadeFirst() throws Exception {
        whatsNewWaiting();
        ActivityController<TvActivity> c = start(play("Harbour TV HD", "Harbour TV"));
        TvActivity a = c.get();
        idle(400);
        // Straight on to the channel, by its name (no key from the launcher), full screen.
        assertTrue(a.top() instanceof LiveScreen);
        assertEquals("c:5", playing(a));
        // Nothing else made or looked at first: Home not built, What's new still waiting (for the next ordinary start), the
        // update folder not looked in, the playlists not read again until the picture has started.
        List<Screen> s = stack(a);
        assertEquals(2, s.size());
        assertFalse(a.homeScreen().built());
        assertEquals(1L, Updates.prefs(app).getLong("seen_code", 0));
        assertEquals(0L, Updates.prefs(app).getLong("checked", 0));
        Field quiet = Library.class.getDeclaredField("quietUntil");
        quiet.setAccessible(true);
        assertTrue((Long) quiet.get(Library.get(app)) > System.currentTimeMillis() + 10_000L);
        // Found by reading only the names with its word in them: no index of every name made (and kept) for it.
        assertFalse(Library.get(app).namesRead());
        // Once the picture is up: the update folder looked in, Home made out of sight under the picture.
        idle(TvActivity.AFTER_CHANNEL_MS + 200);
        assertTrue(Updates.prefs(app).getLong("checked", 0) > 0);
        assertTrue(a.homeScreen().built());
        assertEquals(View.GONE, a.homeScreen().view().getVisibility());
        assertTrue(a.top() instanceof LiveScreen);
        assertEquals("c:5", playing(a));
        // Back from the picture: its card first, then the guide (Live TV), as from the app's own channels.
        press(a, KeyEvent.KEYCODE_BACK);
        if (!(a.top() instanceof GuideScreen)) press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() instanceof GuideScreen);
        // Home under it, as ever.
        a.home();
        idle(400);
        assertTrue(a.top() instanceof HomeScreen);
        assertEquals(View.VISIBLE, a.homeScreen().view().getVisibility());
    }

    @Test public void homeMadeWhenFirstNeededEvenBeforeTheChannelsAreOn() throws Exception {
        ActivityController<TvActivity> c = start(play("Kestrel Two"));
        TvActivity a = c.get();
        idle(100);
        assertEquals("c:4", playing(a));
        assertFalse(a.homeScreen().built());
        // Straight back to Home (the tab bar's Home): made there and then, in its place under every page.
        a.home();
        idle(400);
        assertTrue(a.top() instanceof HomeScreen);
        assertTrue(a.homeScreen().built());
        assertEquals(View.VISIBLE, a.homeScreen().view().getVisibility());
        assertEquals(0, a.screens.indexOfChild(a.homeScreen().view()));
    }

    @Test public void runningTheChannelChangesInPlaceInFrontOrOutOfSight() throws Exception {
        whatsNewWaiting();
        ActivityController<TvActivity> c = start(null);
        TvActivity a = c.get();
        idle(400);
        // An ordinary start: Home and What's new (taken: seen now).
        assertEquals(Math.max(1L, Updates.myCode(app)), Updates.prefs(app).getLong("seen_code", 0));
        a.home();
        c.newIntent(play("Kestrel Two"));
        idle(400);
        assertTrue(a.top() instanceof LiveScreen);
        Screen live = a.top();
        int screens = a.screens.getChildCount() + a.over.getChildCount();
        assertEquals("c:4", playing(a));
        // In front: the next channel on the same screen, nothing made again.
        c.newIntent(play("harbour tv"));
        idle(400);
        assertEquals("c:5", playing(a));
        assertSame(live, a.top());
        assertEquals(screens, a.screens.getChildCount() + a.over.getChildCount());
        // Out of sight (the launcher in front): played on the same screen as the app comes back.
        c.pause().stop();
        idle(100);
        c.newIntent(play("Fogbell News UK", "Fogbell News"));
        c.restart().resume();
        idle(400);
        assertEquals("n:1", playing(a));
        assertSame(live, a.top());
        assertEquals(2, stack(a).size());
    }

    @Test public void aLockedChannelStillAsksForItsPin() throws Exception {
        ActivityController<TvActivity> c = start(play("Harbour TV"));
        TvActivity a = c.get();
        idle(400);
        assertEquals("c:5", playing(a));
        Parental.setPin(app, "2468");
        Parental.lockChannel(app, Library.get(app).byKey("c:4"), true);
        Parental.relock();
        c.newIntent(play("Kestrel Two"));
        idle(400);
        assertTrue(a.top() instanceof PinScreen);
        assertFalse("c:4".equals(playing(a)));
    }

    // ---- the main thread's work from the intent to the player told to play ---------------------------------------------

    /**
     * The library's own "live_tv" settings, where LiveTv.play keeps the channel told to play: the library is the
     * process's and outlives a test, so its context may be an earlier test's application.
     */
    private SharedPreferences liveTv() {
        try {
            java.lang.reflect.Field f = com.gathin.homefieldtv.engine.Library.class.getDeclaredField("app");
            f.setAccessible(true);
            return ((Context) f.get(com.gathin.homefieldtv.engine.Library.get(app))).getSharedPreferences("live_tv", Context.MODE_PRIVATE);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void expect(String key) {
        // Another channel last, so the one looked for is a change the listener hears.
        liveTv().edit().putString("last", "none").commit();
        want = key;
        playedAt = 0;
    }

    private double since(long t0) {
        for (int i = 0; i < 200 && playedAt == 0; i++) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        if (playedAt == 0) throw new AssertionError("never played " + want);
        return (playedAt - t0) / 1e6;
    }

    private double cold(String expect, String... names) {
        for (ActivityController<TvActivity> c : made) c.pause().stop().destroy();
        made.clear();
        whatsNewWaiting();
        expect(expect);
        Intent i = play(names);
        long t0 = System.nanoTime();
        start(i);
        return since(t0);
    }

    /** Homefield TV in front (a channel playing): the launcher's channel comes as it is paused and resumed. */
    private double front(String expect, String... names) {
        ActivityController<TvActivity> c = made.get(0);
        expect(expect);
        Intent i = play(names);
        long t0 = System.nanoTime();
        c.pause().newIntent(i).resume();
        return since(t0);
    }

    /** Homefield TV out of sight (the launcher in front): the channel comes as it is started again. */
    private double back(String expect, String... names) {
        ActivityController<TvActivity> c = made.get(0);
        c.pause().stop();
        shadowOf(Looper.getMainLooper()).idle();
        expect(expect);
        Intent i = play(names);
        long t0 = System.nanoTime();
        c.newIntent(i).restart().resume();
        return since(t0);
    }

    private static String median(List<Double> l) {
        List<Double> s = new ArrayList<>(l);
        Collections.sort(s);
        return String.format(java.util.Locale.ROOT, "%.1f", s.get(s.size() / 2));
    }

    @Test public void mainThreadWorkFromTheIntentToPlay() {
        // The JVM warmed first: one of each, thrown away.
        cold("c:4", "Kestrel Two");
        front("c:5", "Harbour TV");
        back("c:4", "Kestrel Two");
        List<Double> cold = new ArrayList<>(), front = new ArrayList<>(), back = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            cold.add(cold("c:5", "Harbour TV HD", "Harbour TV"));
            front.add(front("c:4", "Kestrel Two"));
            front.add(front("n:1", "Fogbell News"));
            back.add(back("c:5", "Harbour TV"));
            back.add(back("c:4", "Kestrel Two"));
        }
        System.out.println("TIMING PLAY_CHANNEL to play, main thread (median ms): cold " + median(cold) + ", in front " + median(front)
                + ", out of sight " + median(back));
    }
}
