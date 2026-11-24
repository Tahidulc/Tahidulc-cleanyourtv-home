package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpGuide;

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
import java.time.ZoneId;
import java.util.List;

/**
 * Catch-up's player with the remote, as the family watches a programme from the archive (MadeUpGuide's invented
 * channels, provider and login): Left and Right on the plain picture going back and on at once, faster held, one
 * request once the remote rests; the media keys; the overlay's buttons; Back's steps; Up and Down; across a programme's
 * start; the live edge; what is asked of the archive.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class CatchupPlayerTest {
    private static final long S = 1000L, MIN = 60_000L;
    private ActivityController<TvActivity> tv;
    private Context made;
    private List<Channel> ch;
    /** The programme two hours ago on Kestrel One HD (its place in the guide), and the guide. */
    private Guide.Schedule s;
    private int i;

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        if (made != null) MadeUpGuide.unload(made);
        tv = null;
        made = null;
        LiveTv.previewShown = -1;
        Exec.inline = false;
    }

    private static void idle(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle(100);
    }

    /** A key held: its first press and repeats more, 50 ms apart, then let go. */
    private static void hold(TvActivity a, int code, int repeats) {
        long t = SystemClock.uptimeMillis();
        for (int k = 0; k <= repeats; k++) {
            a.dispatchKeyEvent(new KeyEvent(t, t + k * 50L, KeyEvent.ACTION_DOWN, code, k));
            idle(50);
        }
        a.dispatchKeyEvent(new KeyEvent(t, t + repeats * 50L + 20, KeyEvent.ACTION_UP, code, 0));
        idle(50);
    }

    /** Kestrel One HD's programme two hours ago from into ms after its start, the card at the foot closed: the plain picture. */
    private TvActivity watching(long into) {
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        made = app;
        ch = MadeUpGuide.load(app);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        TvActivity a = tv.get();
        idle(400);
        s = Library.get(app).schedule(ch.get(0).key);
        i = s.at(System.currentTimeMillis() - 2 * 3600_000L);
        a.watchFrom(ch.get(0), s.start[i] + into, s.stop[i]);
        idle(400);
        assertTrue(a.top() instanceof CatchupScreen);
        press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() instanceof CatchupScreen);
        assertEquals(CatchupScreen.PLAIN, catchup(a).modeForTests());
        return a;
    }

    private static CatchupScreen catchup(TvActivity a) {
        assertTrue(a.top() instanceof CatchupScreen);
        return (CatchupScreen) a.top();
    }

    @Test public void leftOnThePlainPictureGoesBackAndRightOnAtOnce() {
        TvActivity a = watching(12 * MIN + 25 * S);
        long before = a.live.shownAt();
        // The family's problem: Left on the picture moved a spot that waited for OK; the picture stayed where it was.
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        idle(1500);
        assertTrue(a.top() instanceof CatchupScreen);
        assertEquals(before - 10 * S, a.live.shownAt());
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        idle(1500);
        assertEquals(before + 10 * S, a.live.shownAt());
    }

    @Test public void theBarComesUpAtOnceAndThePictureFollowsOnceTheRemoteRests() {
        TvActivity a = watching(12 * MIN + 25 * S);
        CatchupScreen c = catchup(a);
        long before = a.live.shownAt();
        long[] asked = a.live.asked();
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        // The bar by itself at once, the spot moved; nothing asked of the archive yet.
        assertEquals(CatchupScreen.STRIP, c.modeForTests());
        assertEquals(before - 10 * S, c.scrubTarget());
        assertEquals(before, a.live.shownAt());
        assertArrayEquals(asked, a.live.asked());
        idle(CatchupScreen.APPLY_MS);
        assertEquals(-1, c.scrubTarget());
        assertEquals(before - 10 * S, a.live.shownAt());
        // The bar stays a moment after, then goes.
        assertEquals(CatchupScreen.STRIP, c.modeForTests());
        idle(CatchupScreen.STRIP_MS + 200);
        assertEquals(CatchupScreen.PLAIN, c.modeForTests());
    }

    @Test public void leftHeldGoesFasterWithOneRequestAfterTheRemoteRests() {
        TvActivity a = watching(25 * MIN);
        CatchupScreen c = catchup(a);
        long before = a.live.shownAt();
        long[] asked = a.live.asked();
        // Twenty-one steps: five of 10 seconds, ten of 30 seconds, six of a minute.
        hold(a, KeyEvent.KEYCODE_DPAD_LEFT, 20);
        long to = before - (5 * 10 + 10 * 30 + 6 * 60) * S;
        assertEquals(to, c.scrubTarget());
        // Held: not one request a press.
        assertArrayEquals(asked, a.live.asked());
        idle(1000);
        assertEquals(to, a.live.shownAt());
        // One request, from the minute (an Xtream archive's), the picture moved on to the second.
        long[] now = a.live.asked();
        assertEquals(to - Math.floorMod(to, MIN), now[0]);
        assertEquals(Math.floorMod(to, MIN), now[1]);
        // Taps: 10 seconds each, however many.
        for (int k = 0; k < 8; k++) press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        idle(1000);
        assertEquals(to + 80 * S, a.live.shownAt());
    }

    @Test public void theRewindFastForwardPlayPauseAndProgrammeKeys() {
        TvActivity a = watching(12 * MIN);
        CatchupScreen c = catchup(a);
        long before = a.live.shownAt();
        press(a, KeyEvent.KEYCODE_MEDIA_REWIND);
        assertEquals(CatchupScreen.STRIP, c.modeForTests());
        press(a, KeyEvent.KEYCODE_MEDIA_REWIND);
        idle(1000);
        assertEquals(before - 20 * S, a.live.shownAt());
        press(a, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
        idle(1000);
        assertEquals(before - 10 * S, a.live.shownAt());
        // Play/pause: paused, the overlay up with the remote on it; again, playing.
        press(a, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        assertTrue(a.live.held());
        assertEquals(CatchupScreen.OVERLAY, c.modeForTests());
        assertEquals(CatchupScreen.PLAY, c.focusForTests());
        press(a, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        assertFalse(a.live.held());
        press(a, KeyEvent.KEYCODE_BACK);
        // Previous: the programme before; previous again, the one before that; next: back on to the one after it.
        press(a, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        press(a, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        assertEquals(s.start[i - 2], c.scrubTarget());
        idle(1000);
        assertEquals(s.start[i - 2], a.live.shownAt());
        press(a, KeyEvent.KEYCODE_MEDIA_NEXT);
        idle(1000);
        assertEquals(s.start[i - 1], a.live.shownAt());
        assertTrue(a.top() instanceof CatchupScreen);
    }

    @Test public void okBringsTheOverlayWithItsButtons() {
        TvActivity a = watching(12 * MIN);
        CatchupScreen c = catchup(a);
        long before = a.live.shownAt();
        // OK: the overlay, the remote on play/pause.
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(CatchupScreen.OVERLAY, c.modeForTests());
        assertEquals(CatchupScreen.PLAY, c.focusForTests());
        // ‹10: back 10 seconds a press, adding up; the picture follows once the remote rests.
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(CatchupScreen.REWIND, c.focusForTests());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(before - 20 * S, c.scrubTarget());
        idle(1000);
        assertEquals(before - 20 * S, a.live.shownAt());
        // 30›: on 30 seconds.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(CatchupScreen.FORWARD, c.focusForTests());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        idle(1000);
        assertEquals(before + 10 * S, a.live.shownAt());
        // Play/pause.
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.live.held());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(a.live.held());
        // |◀: the programme before.
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(CatchupScreen.SKIP_BACK, c.focusForTests());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        idle(1000);
        assertEquals(s.start[i - 1], a.live.shownAt());
        // ▶|: the next one (this programme again).
        for (int k = 0; k < 4; k++) press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(CatchupScreen.SKIP_NEXT, c.focusForTests());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        idle(1000);
        assertEquals(s.start[i], a.live.shownAt());
        // Up: the seek line; Left moves the spot there the same way; OK jumps at once.
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(CatchupScreen.SEEK, c.focusForTests());
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(-1, c.scrubTarget());
        assertEquals(s.start[i] + 10 * S, a.live.shownAt());
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(CatchupScreen.PLAY, c.focusForTests());
        // ⟲: the programme from its start, at once.
        for (int k = 0; k < 3; k++) press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(CatchupScreen.RESTART, c.focusForTests());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(-1, c.scrubTarget());
        assertEquals(s.start[i], a.live.shownAt());
        // LIVE: the live screen, live.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(CatchupScreen.LIVE, c.focusForTests());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        idle(400);
        assertTrue(a.top() instanceof LiveScreen);
        assertFalse(a.live.behind());
    }

    @Test public void backLetsTheMoveGoThenClosesThenIsTheGuide() {
        TvActivity a = watching(12 * MIN);
        CatchupScreen c = catchup(a);
        long before = a.live.shownAt();
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        // Back before the remote rests: the move let go, the picture where it was.
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(-1, c.scrubTarget());
        assertEquals(CatchupScreen.PLAIN, c.modeForTests());
        idle(1500);
        assertEquals(before, a.live.shownAt());
        // The overlay: Back closes it, then the TV guide with the programme still in its picture.
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(CatchupScreen.OVERLAY, c.modeForTests());
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(CatchupScreen.PLAIN, c.modeForTests());
        press(a, KeyEvent.KEYCODE_BACK);
        idle(400);
        assertTrue(a.top() instanceof GuideScreen);
        assertTrue(a.live.behind());
    }

    @Test public void upAndDownChangeChannelFromThePlainPictureNotWhileTheBarShows() {
        TvActivity a = watching(12 * MIN);
        CatchupScreen c = catchup(a);
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        idle(1000);
        // The bar up: Down is the overlay's buttons, Up its seek line, still the programme from the archive.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(a.top() instanceof CatchupScreen);
        assertEquals(CatchupScreen.OVERLAY, c.modeForTests());
        assertEquals(CatchupScreen.PLAY, c.focusForTests());
        press(a, KeyEvent.KEYCODE_BACK);
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(CatchupScreen.OVERLAY, c.modeForTests());
        assertEquals(CatchupScreen.SEEK, c.focusForTests());
        assertTrue(a.live.behind());
        // Left and Right are the seek line's, never the channel list's.
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertTrue(a.top() instanceof CatchupScreen);
        press(a, KeyEvent.KEYCODE_BACK);
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(CatchupScreen.PLAIN, c.modeForTests());
        // The plain picture: Down is the next channel, live.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        idle(400);
        assertTrue(a.top() instanceof LiveScreen);
        assertFalse(a.live.behind());
        assertFalse(ch.get(0).key.equals(a.live.channel().key));
    }

    @Test public void backAcrossTheProgrammesStartIntoTheOneBefore() {
        TvActivity a = watching(20 * S);
        CatchupScreen c = catchup(a);
        long start = s.start[i];
        assertArrayEquals(new long[]{start, s.stop[i]}, c.lineForTests());
        for (int k = 0; k < 3; k++) press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        // The line is the programme before's at once, the spot 10 seconds before its end.
        assertEquals(start - 10 * S, c.scrubTarget());
        assertArrayEquals(new long[]{s.start[i - 1], s.stop[i - 1]}, c.lineForTests());
        idle(1000);
        assertEquals(start - 10 * S, a.live.shownAt());
        // Asked for from that minute (Xtream's) to past the programme's end, moved on 50 seconds into it.
        long[] asked = a.live.asked();
        assertEquals(start - MIN, asked[0]);
        assertEquals(50 * S, asked[1]);
        assertEquals(s.stop[i - 1], asked[2]);
        long end = Catchup.requestEnd(asked[0], asked[2]);
        assertEquals("http://provider.example:8080/timeshift/family/test/35/"
                        + Catchup.xtreamStart(asked[0], ZoneId.of("Europe/London")) + "/4000.ts",
                Catchup.url(ch.get(0), asked[0], end, System.currentTimeMillis()));
        // The bar right after it: the spot's share of the programme before.
        float[] sh = c.sharesForTests();
        float len = s.stop[i - 1] - s.start[i - 1];
        assertEquals((start - 10 * S - s.start[i - 1]) / len, sh[0], 1e-4f);
        assertEquals(1f, sh[1], 1e-4f);
        // On again: into this programme, its line again.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertArrayEquals(new long[]{start, s.stop[i]}, c.lineForTests());
        idle(1000);
        assertEquals(start + 10 * S, a.live.shownAt());
    }

    @Test public void onToTheLiveEdgeIsLive() {
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        made = app;
        ch = MadeUpGuide.load(app);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        TvActivity a = tv.get();
        idle(400);
        long now = System.currentTimeMillis();
        s = Library.get(app).schedule(ch.get(0).key);
        a.watchFrom(ch.get(0), now - 150 * S, s.stop[s.at(now)]);
        idle(400);
        press(a, KeyEvent.KEYCODE_BACK);
        CatchupScreen c = catchup(a);
        // Held on: no further than now.
        hold(a, KeyEvent.KEYCODE_DPAD_RIGHT, 12);
        assertTrue(c.scrubTarget() <= System.currentTimeMillis());
        assertTrue(CatchupScreen.liveEdge(c.scrubTarget(), System.currentTimeMillis()));
        // Let go there: live, the live screen under it.
        idle(1000);
        assertTrue(a.top() instanceof LiveScreen);
        assertFalse(a.live.behind());
        assertEquals(ch.get(0).key, a.live.channel().key);
    }
}
