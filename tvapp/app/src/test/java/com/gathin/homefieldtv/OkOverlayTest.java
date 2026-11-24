package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
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
 * The family's OK overlay while a channel plays (the mock-up "C with programme pictures"), with invented channels: OK
 * brings the information panel up low with the ten channels watched last under it in two rows of five; Left and Right
 * along a row, Down to the second and Up back; OK on a card watches it; Down past the second row, the settings' pills
 * in the cards' place with the card staying put, and Up from their first row back; MENU straight to the settings; no
 * Scoreboard among them; everything inside the screen and every pill's words whole.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class OkOverlayTest {
    private static final String[] NAMES = {"Kestrel One", "Harbour One", "Lumen Cinema", "Meadow Life", "Northfield News",
            "Orchard Comedy", "Arcadia Drama", "Saltmarsh Travel", "Ember Music", "Willow Kids", "Copperfield Gold", "Granite Crime"};
    private ActivityController<TvActivity> tv;
    private Context made;

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        if (made != null) MadeUpGuide.unload(made);
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

    private static void press(TvActivity a, int code, int times) {
        for (int i = 0; i < times; i++) press(a, code);
    }

    private static Object field(Object o, String name) throws Exception {
        java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static int mode(TvActivity a) throws Exception {
        return (Integer) field(a.top(), "mode");
    }

    private static String playing(TvActivity a) {
        return a.live.channel() == null ? "" : a.live.channel().key;
    }

    /** The view drawn alone at the screen's size onto a clear sheet. */
    private static Bitmap drawn(View v) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 1920, 1080);
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        return b;
    }

    private static boolean clear(Bitmap b, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y += 4) for (int x = x0; x < x1; x += 4) if ((b.getPixel(x, y) >>> 24) != 0) return false;
        return true;
    }

    /**
     * Twelve invented channels with half-hour programmes around now, eleven of them watched lately (the first, which
     * plays, the newest), then the app opened and the first one playing.
     */
    private TvActivity open(List<Channel> out) {
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        made = app;
        Sources.clear(app);
        Sources.put(app, "p0", "Riverside", new Source(Source.XTREAM, null, null, "http://provider.example:8080", "family", "test"));
        long now = System.currentTimeMillis(), half = 30 * 60_000L;
        Map<String, Guide.Schedule> guide = new HashMap<>();
        for (int i = 0; i < NAMES.length; i++) {
            Channel c = new Channel("k:" + (700 + i), NAMES[i], i % 2 == 0 ? "Entertainment" : "Factual", "",
                    "http://provider.example/live/" + i + ".ts", "", null, null, 101 + i);
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
        List<String> recent = new ArrayList<>();
        for (int i = 0; i <= 10; i++) recent.add(out.get(i).key);
        lib.setPicks(new ArrayList<>(), recent);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        TvActivity a = tv.get();
        idle();
        a.watch(lib.byKey(out.get(0).key));
        idle();
        return a;
    }

    @Test public void okBringsTheCardLowWithTenChannelsUnderItAndTheKeysGoAcrossThem() throws Exception {
        List<Channel> ch = new ArrayList<>();
        TvActivity a = open(ch);
        assertTrue(a.top() instanceof LiveScreen);
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(LiveScreen.NONE, mode(a));
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(LiveScreen.OVERLAY, mode(a));
        RecentGrid recents = (RecentGrid) field(a.top(), "recents");
        View panels = (View) field(a.top(), "panels");
        assertEquals(View.VISIBLE, recents.getVisibility());
        assertEquals(View.VISIBLE, panels.getVisibility());
        // Ten channels, newest first, never the one playing.
        assertEquals(10, recents.count());
        assertEquals(0, recents.focus());
        assertEquals(ch.get(1).key, recents.focused().key);

        // The card low: drawn from 352 to 674 and nothing above the scrim's start (the picture clear at the top).
        Bitmap b = drawn(panels);
        assertTrue("the card", (b.getPixel(960, 500) >>> 24) > 200);
        assertTrue("its bottom", (b.getPixel(960, 670) >>> 24) > 200);
        assertTrue("over the picture's top (the card's soft shadow aside)", clear(b, 0, 0, 1920, Math.round(InfoCard.STACK_Y) - 100));
        b.recycle();
        // The cards: both rows drawn, and nothing under the keys' line.
        b = drawn(recents);
        assertTrue("first row", (b.getPixel(200, 794) >>> 24) == 255);
        assertTrue("second row", (b.getPixel(1700, 928) >>> 24) == 255);
        assertTrue("nothing between the card and the line", clear(b, 0, 0, 1920, 680));
        assertTrue("nothing at the foot", clear(b, 0, 1060, 1920, 1080));
        b.recycle();

        // Left and Right along a row, stopping at its ends; Down to the second row, Up back.
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(0, recents.focus());
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT, 6);
        assertEquals(4, recents.focus());
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(9, recents.focus());
        press(a, KeyEvent.KEYCODE_DPAD_LEFT, 2);
        assertEquals(7, recents.focus());
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(2, recents.focus());
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals("nothing above but the card", 2, recents.focus());
        assertEquals(LiveScreen.OVERLAY, mode(a));

        // OK on a card watches its channel (its card comes up).
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(ch.get(3).key, playing(a));
        assertEquals(LiveScreen.BANNER, mode(a));
    }

    @Test public void downPastTheSecondRowIsTheSettingsAndUpComesBack() throws Exception {
        List<Channel> ch = new ArrayList<>();
        TvActivity a = open(ch);
        press(a, KeyEvent.KEYCODE_BACK);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        RecentGrid recents = (RecentGrid) field(a.top(), "recents");
        PlayerBar bar = (PlayerBar) field(a.top(), "bar");
        View panels = (View) field(a.top(), "panels");
        Bitmap before = drawn(panels);
        // The middle card of the second row, then Down: the settings, the remote on the pill under it.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT, 2);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(7, recents.focus());
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(LiveScreen.BAR, mode(a));
        assertTrue(bar.isOpen());
        assertEquals(View.GONE, recents.getVisibility());
        assertEquals(PlayerBar.ORDER[4], bar.focusedItem());
        // The card stays exactly where it was.
        Bitmap after = drawn(panels);
        for (int y = 360; y < 670; y += 37) assertEquals("the card at " + y, before.getPixel(400, y), after.getPixel(400, y));
        before.recycle();
        after.recycle();
        // Along the pills, down to the second row, up, and up again: back to the second row's card under it.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT, 20);
        assertEquals(PlayerBar.ORDER[PlayerBar.ROW - 1], bar.focusedItem());
        press(a, KeyEvent.KEYCODE_DPAD_DOWN, 2);
        assertEquals(PlayerBar.EXIT, bar.focusedItem());
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(PlayerBar.SETTINGS, bar.focusedItem());
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(LiveScreen.OVERLAY, mode(a));
        assertFalse(bar.isOpen());
        assertEquals(View.VISIBLE, recents.getVisibility());
        assertEquals(9, recents.focus());

        // A picture and sound pill opens its picker, the card staying; Back closes it all.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_LEFT, 4);
        assertEquals(PlayerBar.DISPLAY, bar.focusedItem());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(bar.pickerOpen());
        assertEquals(LiveScreen.BAR, mode(a));
        assertEquals(View.VISIBLE, panels.getVisibility());
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(LiveScreen.NONE, mode(a));
        assertFalse(bar.isOpen());
    }

    @Test public void menuOpensStraightOnTheSettingsWithNoScoreboard() throws Exception {
        List<Channel> ch = new ArrayList<>();
        TvActivity a = open(ch);
        press(a, KeyEvent.KEYCODE_BACK);
        press(a, KeyEvent.KEYCODE_MENU);
        assertEquals(LiveScreen.BAR, mode(a));
        PlayerBar bar = (PlayerBar) field(a.top(), "bar");
        View panels = (View) field(a.top(), "panels");
        assertTrue(bar.isOpen());
        assertEquals(PlayerBar.GUIDE, bar.focusedItem());
        // The card with them, low as with OK.
        Bitmap b = drawn(panels);
        assertTrue("the card", (b.getPixel(960, 500) >>> 24) > 200);
        assertTrue("over the picture's top (the card's soft shadow aside)", clear(b, 0, 0, 1920, Math.round(InfoCard.STACK_Y) - 100));
        b.recycle();
        // Every button but the Scoreboard (held OK's now), and the TV guide first.
        assertEquals(18, PlayerBar.ORDER.length);
        List<String> names = new ArrayList<>();
        for (int k = 0; k < PlayerBar.ORDER.length; k++) names.add(bar.words(k)[0]);
        assertFalse(names.toString(), names.contains("Scoreboard"));
        assertEquals("TV guide", names.get(0));
        for (String n : new String[]{"Search", "Channels list", "History", "Movies", "TV shows", "Recordings", "My list", "Settings",
                "Picture", "Sound", "Audio offset", "Subtitles", "Display", "Sleep timer", "Favourites", "Channel", "Exit"}) {
            assertTrue(n, names.contains(n));
        }
        assertEquals("Add this channel", bar.words(15)[1]);
        // MENU again: away (as the bar's MENU was); then OK on TV guide: the guide, the channel in its picture.
        press(a, KeyEvent.KEYCODE_MENU);
        assertEquals(LiveScreen.NONE, mode(a));
        press(a, KeyEvent.KEYCODE_MENU);
        assertEquals(PlayerBar.GUIDE, bar.focusedItem());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof GuideScreen);
        assertNotEquals("", playing(a));
    }

    @Test public void everythingIsInsideTheScreenAndEveryPillsWordsAreWhole() {
        Type.init(RuntimeEnvironment.getApplication());
        Ui.init(RuntimeEnvironment.getApplication());
        // The card ends a line above the recent channels' line; the cards, the pills and their keys' lines in the screen.
        assertTrue(InfoCard.STACK_Y + InfoCard.H + 12f <= RecentGrid.HEAD_Y - 12f);
        assertTrue(InfoCard.STACK_Y > PlayerBar.SCRIM_TOP);
        float cards = RecentGrid.TOP + 2 * RecentGrid.CARD_H + RecentGrid.GAP;
        assertTrue(cards + RecentGrid.HINT_BELOW + 16f <= 1080f - 24f);
        float pills = PlayerBar.TOP + 2 * PlayerBar.PILL_H + PlayerBar.GAP;
        assertTrue(pills + PlayerBar.HINT_BELOW + 16f <= 1080f - 24f);
        assertEquals(1872f, RecentGrid.X + RecentGrid.W, 0f);
        assertEquals(RecentGrid.W, 5 * RecentGrid.CARD_W + 4 * RecentGrid.GAP, 0.01f);
        assertEquals(RecentGrid.W, PlayerBar.ROW * PlayerBar.PILL_W + (PlayerBar.ROW - 1) * PlayerBar.GAP, 0.01f);
        // The pills' words (the longest each can say) whole in their room, never cut.
        TextPaint name = Type.paint(18f, Type.SEMIBOLD, 0xFFFFFFFF), value = Type.paint(15f, Type.REGULAR, 0xFFFFFFFF);
        float room = Ui.f(PlayerBar.PILL_W - PlayerBar.WORDS_X - PlayerBar.WORDS_PAD);
        for (String n : new String[]{"TV guide", "Channels list", "Recordings", "Audio offset", "Sleep timer", "Favourites", "Subtitles"}) {
            assertTrue(n + " " + name.measureText(n), name.measureText(n) <= room);
        }
        for (String v : new String[]{"Channels, films", "Watched lately", "From catch-up", "Saved to watch", "Homefield TV", "1920 × 1080",
                "English · Stereo", "From the channel", "Zoom - 2.35:1", "Add this channel", "Close the app"}) {
            assertTrue(v + " " + value.measureText(v), value.measureText(v) <= room);
        }
        // The lines over and under the cards fit across the overlay.
        TextPaint keys = Type.paint(21f, Type.REGULAR, 0xFFFFFFFF);
        assertTrue(keys.measureText(PlayerBar.KEYS_UP) <= Ui.f(RecentGrid.TEXT_R - RecentGrid.TEXT_X));
        assertTrue(keys.measureText(RecentGrid.KEYS_TWO) <= Ui.f(RecentGrid.TEXT_R - RecentGrid.TEXT_X));
    }
}
