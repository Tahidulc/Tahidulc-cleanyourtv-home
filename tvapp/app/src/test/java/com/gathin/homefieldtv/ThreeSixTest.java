package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpGuide;
import com.gathin.homefieldtv.engine.RecentChannels;

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
import java.util.List;

/**
 * The family's asks for 3.6, with invented channels: Up and Down bring up the information panel alone (no Change channel
 * panel, no "Watching" box); the guide's preview with nothing over its picture; the recent channels' row while watching
 * live TV, a channel counting once it has played a while (Right still the channel before, however briefly); and the
 * frosted panels drawn again over the picture once the ground rests (Lite: no flat colour left behind).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class ThreeSixTest {
    private ActivityController<TvActivity> tv;
    private Context made;

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        if (made != null) MadeUpGuide.unload(made);
        GuideScreen.forgetList();
        tv = null;
        made = null;
        Exec.inline = false;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    private static String playing(TvActivity a) {
        return a.live.channel() == null ? "" : a.live.channel().key;
    }

    private static View field(Object o, String name) throws Exception {
        java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (View) f.get(o);
    }

    /** The view drawn alone at the screen's size onto a clear sheet. */
    private static Bitmap drawn(View v) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 1920, 1080);
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        return b;
    }

    /** Nothing drawn in the box (screen px, every fourth pixel looked at). */
    private static void assertClear(String what, Bitmap b, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y += 4) {
            for (int x = x0; x < x1; x += 4) {
                assertEquals(what + " at " + x + "," + y, 0, b.getPixel(x, y) >>> 24);
            }
        }
    }

    /** The invented playlist and its guide, then the app opened. */
    private TvActivity open(List<Channel> out) {
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        made = app;
        out.addAll(MadeUpGuide.load(app));
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        TvActivity a = tv.get();
        idle();
        return a;
    }

    @Test public void upAndDownBringUpTheInformationPanelAlone() throws Exception {
        List<Channel> ch = new ArrayList<>();
        TvActivity a = open(ch);
        a.watch(ch.get(2));
        idle();
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        // Still a change of channel.
        assertNotEquals(ch.get(2).key, playing(a));
        assertTrue(a.top() instanceof LiveScreen);
        View panels = field(a.top(), "panels");
        assertEquals(View.VISIBLE, panels.getVisibility());
        Bitmap b = drawn(panels);
        // The information panel at the foot.
        assertTrue("the information panel", (b.getPixel(960, 900) >>> 24) > 0);
        // Nothing at the top right, where the Change channel panel and the "Watching 102" box were; nothing above the
        // panel at all.
        assertClear("over the picture", b, 0, 0, 1920, Math.round(InfoCard.FOOT_Y) - 90);
        b.recycle();
    }

    @Test public void theGuidesPreviewHasNothingOverItsPicture() throws Exception {
        List<Channel> ch = new ArrayList<>();
        TvActivity a = open(ch);
        a.watch(ch.get(2));
        idle();
        for (int i = 0; i < 3 && a.top() instanceof LiveScreen; i++) press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() instanceof GuideScreen);
        Rect p = a.live.where();
        assertFalse(p.isEmpty());
        Bitmap b = drawn(field(a.top(), "frame"));
        // Its frame: the corners filled (it is drawn), and nothing over the picture: no "102 Riverside · playing" pill.
        assertTrue("its corner", (b.getPixel(p.left + 2, p.top + 2) >>> 24) > 0);
        assertClear("over the preview", b, p.left + 24, p.top + 24, p.right - 24, p.bottom - 24);
        b.recycle();
    }

    @Test public void theRecentChannelsRowWhileWatchingLive() throws Exception {
        List<Channel> ch = new ArrayList<>();
        TvActivity a = open(ch);
        Library lib = Library.get(a);
        assertEquals(10_000L, RecentChannels.COUNT_MS);
        // Watched a while: counted.
        a.watch(ch.get(1));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(RecentChannels.COUNT_MS + 500));
        a.watch(ch.get(4));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(RecentChannels.COUNT_MS + 500));
        idle();
        assertEquals(ch.get(4).key, playing(a));
        // Up and Down through two channels, resting on the second: the one passed isn't counted.
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        String passed = playing(a);
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        String rested = playing(a);
        assertNotEquals(passed, rested);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(RecentChannels.COUNT_MS + 500));
        idle();
        List<String> keys = new ArrayList<>();
        for (Channel c : lib.recent(null)) keys.add(c.key);
        List<String> want = new ArrayList<>();
        want.add(rested);
        want.add(ch.get(4).key);
        want.add(ch.get(1).key);
        assertEquals(want, keys);
        // Right: the channel before, however briefly it played; again, back.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(passed, playing(a));
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(rested, playing(a));
        // OK while watching live: the overlay with the row (TV guide, History, then the recent channels in 07); Right to
        // the first channel and OK changes to it.
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        PlayerTiles tiles = (PlayerTiles) field(a.top(), "tiles");
        assertEquals(View.VISIBLE, tiles.getVisibility());
        assertEquals(PlayerTiles.GUIDE, tiles.focus());
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(2, tiles.focus());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(ch.get(4).key, playing(a));
    }

    @Test public void framedPanelsAreDrawnAgainOnceTheGroundRests() {
        int red = 0xFF8A2030, grey = 0xFF202024;
        // Lent a colour while it moved; now the picture (0) or another colour, at rest: drawn again.
        assertTrue(Ground.handBack(red, 0, false));
        assertTrue(Ground.handBack(red, grey, false));
        // Still moving, nothing lent, or the colour it already has: not.
        assertFalse(Ground.handBack(red, 0, true));
        assertFalse(Ground.handBack(0, 0, false));
        assertFalse(Ground.handBack(red, red, false));

        // Every view that shows in the screen is drawn again, however deep; a hidden one isn't.
        Context app = RuntimeEnvironment.getApplication();
        final List<View> drawnAgain = new ArrayList<>();
        class Panel extends View {
            Panel(Context c) {
                super(c);
            }

            @Override public void invalidate() {
                drawnAgain.add(this);
                super.invalidate();
            }
        }
        FrameLayout screen = new FrameLayout(app), inner = new FrameLayout(app);
        Panel band = new Panel(app), menu = new Panel(app), hidden = new Panel(app);
        hidden.setVisibility(View.GONE);
        inner.addView(band);
        screen.addView(inner);
        screen.addView(menu);
        screen.addView(hidden);
        drawnAgain.clear();
        Ground.redrawTree(screen);
        assertTrue(drawnAgain.contains(band));
        assertTrue(drawnAgain.contains(menu));
        assertFalse(drawnAgain.contains(hidden));
    }
}
