package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpGuide;
import com.gathin.homefieldtv.engine.Reminders;
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

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The channel list over the picture as the family picked it (mock-up "A · Refined"), with invented channels: each card
 * on its own logo's colour (the family's change), readable on a light one; the remote's card's white ring; the
 * timeline's band for the one on now; the card's own picture with LIVE on it; and the programmes the remote can go
 * onto (Right), along (Up and Down) and back from (Left): those over in the archive with the catch-up mark, OK on one
 * playing it from its start in catch-up, OK on the one on now playing the channel live, OK on one to come its reminder.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class ChannelListTest {
    private static final String[] NAMES = {"Kestrel One", "Harbour One", "Lumen Cinema", "Meadow Life", "Northfield News",
            "Orchard Comedy", "Arcadia Drama", "Saltmarsh Travel"};
    /** The logos' own colours: a deep one each, the fourth a light yellow. */
    private static final int[] LOGOS = {0xFF0B7A3E, 0xFFB3123A, 0xFF1D5FBF, 0xFFF5E663, 0xFF2E7D4F, 0xFF0E6BA8, 0xFFD0611C, 0xFF6B3FA0};
    private ActivityController<TvActivity> tv;
    private Context made;

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        if (made != null) MadeUpGuide.unload(made);
        Reminders.reset();
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

    private static Object field(Object o, String name) throws Exception {
        java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static int mode(TvActivity a) throws Exception {
        return (Integer) field(a.top(), "mode");
    }

    /** The view drawn alone at the screen's size onto a black sheet (the picture black under it). */
    private static Bitmap drawn(View v) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 1920, 1080);
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xFF000000);
        v.draw(new Canvas(b));
        return b;
    }

    private static int red(int c) {
        return (c >> 16) & 0xFF;
    }

    private static int green(int c) {
        return (c >> 8) & 0xFF;
    }

    private static int blue(int c) {
        return c & 0xFF;
    }

    /** The brightest of a box's pixels (its red, green and blue's largest). */
    private static int brightest(Bitmap b, int x0, int y0, int x1, int y1) {
        int most = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            int c = b.getPixel(x, y);
            most = Math.max(most, Math.max(red(c), Math.max(green(c), blue(c))));
        }
        return most;
    }

    private static boolean near(int want, int got, int by) {
        return Math.abs(red(want) - red(got)) <= by && Math.abs(green(want) - green(got)) <= by && Math.abs(blue(want) - blue(got)) <= by;
    }

    /** An invented logo's colours made known for its address, as when Pics reads it: its initial on a square of its colour. */
    private static void logo(String url, int colour) {
        Bitmap b = Bitmap.createBitmap(160, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(colour);
        c.drawRoundRect(4, 4, 76, 76, 14, 14, p);
        c.drawRect(84, 30, 156, 50, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(40, 40, 16, p);
        Logos.colours(url, b);
    }

    /**
     * Eight invented channels in one group, half-hour programmes from two days back to six hours on; the first keeps a
     * day of archive (catch-up), the others none; then the app opened and the first one playing, the list opened (Left).
     */
    private TvActivity open(List<Channel> out, Map<String, Guide.Schedule> guide) throws Exception {
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        made = app;
        Sources.clear(app);
        Sources.put(app, "p0", "Riverside", new Source(Source.XTREAM, null, null, "http://provider.example:8080", "family", "test"));
        long now = System.currentTimeMillis(), half = 30 * 60_000L;
        for (int i = 0; i < NAMES.length; i++) {
            String logo = "file:" + new File(app.getCacheDir(), "no-logo-" + i + ".png").getPath();
            logo(logo, LOGOS[i]);
            Channel c = new Channel("g:" + (4000 + i), NAMES[i], "Entertainment", logo, "http://provider.example:8080/live/family/test/" + (4000 + i)
                    + ".ts", "", null, null, 101 + i, i == 0 ? Catchup.XC : "", "", i == 0 ? 1 : 0, "Europe/London");
            out.add(c);
            int n = 96 + 12;
            long[] st = new long[n], en = new long[n];
            for (int k = 0; k < n; k++) {
                st[k] = now - now % half - 96 * half + k * half;
                en[k] = st[k] + half;
            }
            guide.put(c.key, MadeUpGuide.schedule(st, en));
        }
        Library.get(app).useForPreview(out, guide);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        TvActivity a = tv.get();
        idle();
        a.watch(Library.get(app).byKey(out.get(0).key));
        idle();
        press(a, KeyEvent.KEYCODE_BACK);
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(LiveScreen.LIST, mode(a));
        return a;
    }

    private static ChannelList list(TvActivity a) throws Exception {
        return (ChannelList) field(a.top(), "list");
    }

    @Test public void theTimelinesSums() {
        // The timeline: back as far as the archive keeps, at least to the one before now; the one on now 65 high.
        long[] start = {0, 100, 200, 300, 400, 500};
        assertEquals(3, ChannelList.firstShown(start, 4, Long.MAX_VALUE));
        assertEquals(1, ChannelList.firstShown(start, 4, 50));
        assertEquals(0, ChannelList.firstShown(start, 0, Long.MAX_VALUE));
        assertEquals(0, ChannelList.firstShown(start, 4, -1));
        assertEquals(-55f, ChannelList.rowY(3, 4, 4), 0f);
        assertEquals(0f, ChannelList.rowY(4, 4, 4), 0f);
        assertEquals(71f, ChannelList.rowY(5, 4, 4), 0f);
        assertEquals(55f, ChannelList.rowY(5, 4, -1), 0f);
        // At rest the one on now is the second row; the remote's programme too, within the list's ends.
        assertEquals(-55f, ChannelList.timelineFor(4, 1, 5, 4, 4), 0f);
        assertEquals(-110f, ChannelList.timelineFor(3, 1, 5, 4, 4), 0f);
        assertEquals(-165f, ChannelList.timelineFor(1, 1, 5, 4, 4), 0f);
        assertEquals(0f, ChannelList.timelineFor(5, 1, 5, 4, 4), 0f);
        assertEquals(71f, ChannelList.timelineFor(9, 1, 5, 4, 4), 0f);
    }

    @Test public void whiteWordsReadOnEveryColourAndADeepOneIsLeftAlone() {
        for (int c : new int[]{0xFFF5E663, 0xFFFFFFFF, 0xFFB8C400, 0xFF7FD4F0, 0xFFE8A0B8, 0xFF808080}) {
            int r = ChannelList.readable(c);
            assertTrue(Integer.toHexString(c), ClubLook.whiteContrast(r) >= ChannelList.READABLE);
            // Only shaded (a mix with black): its hue kept.
            assertTrue(red(r) <= red(c) && green(r) <= green(c) && blue(r) <= blue(c));
            if (red(c) > blue(c) + 40) assertTrue(red(r) > blue(r));
        }
        for (int c : new int[]{0xFF0B7A3E, 0xFFB3123A, 0xFF1D5FBF, 0xFF6B3FA0, 0xFF000000}) assertEquals(c, ChannelList.readable(c));
    }

    @Test public void eachCardIsOnItsLogosColourAndTheRemotesHasItsRing() throws Exception {
        List<Channel> ch = new ArrayList<>();
        TvActivity a = open(ch, new HashMap<>());
        ChannelList list = list(a);
        assertEquals(ch.get(0).key, list.channel().key);
        Bitmap b = drawn(list);
        // Cards 1 to 4 (the remote's, the first, lifted): each card's ground, below its words, its channel's logo colour.
        List<Integer> seen = new ArrayList<>();
        for (int k = 1; k <= 4; k++) {
            Channel c = Library.get(made).byKey(ch.get(k).key);
            int want = ChannelList.cardColour(c), top = 116 + k * 116;
            assertEquals(ChannelList.readable(ChannelLook.colour(c)), want);
            int got = b.getPixel(48 + 560, top + 97);
            assertTrue(NAMES[k] + ": " + Integer.toHexString(got) + " for " + Integer.toHexString(want), near(want, got, 2));
            assertTrue(ClubLook.whiteContrast(got) >= ChannelList.READABLE - 0.05);
            seen.add(want);
        }
        assertEquals("every card its own colour", 4, new java.util.HashSet<>(seen).size());
        // The light yellow logo: its card still yellow (red and green over blue), and white words read on it.
        int yellow = b.getPixel(48 + 560, 116 + 3 * 116 + 97);
        assertTrue(Integer.toHexString(yellow), red(yellow) > blue(yellow) + 30 && green(yellow) > blue(yellow) + 30);
        assertTrue(ClubLook.whiteContrast(yellow) >= ChannelList.READABLE - 0.05);
        // The logo on a darker tile of the card's colour.
        int tile = b.getPixel(48 + 14, 116 + 116 + 52), card = b.getPixel(48 + 560, 116 + 116 + 97);
        assertTrue(red(tile) < red(card) || green(tile) < green(card) || blue(tile) < blue(card));
        // The remote's card: lifted 1.03 with a 3 px white ring just outside it (none round another card).
        int ring = b.getPixel(348, 113), left = b.getPixel(37, 168);
        assertTrue(Integer.toHexString(ring), near(0xFFFFFFFF, ring, 12));
        assertTrue(Integer.toHexString(left), near(0xFFFFFFFF, left, 12));
        assertFalse(near(0xFFFFFFFF, b.getPixel(348, 116 + 116 - 2), 60));
        b.recycle();
    }

    @Test public void theTimelinesNowBandAndTheCardsPictureWithLive() throws Exception {
        List<Channel> ch = new ArrayList<>();
        Map<String, Guide.Schedule> guide = new HashMap<>();
        TvActivity a = open(ch, guide);
        ChannelList list = list(a);
        // The programme on now's own picture (an invented one: a blue sky over green ground; one all one colour would be
        // taken for a placeholder card and passed over).
        Context app = made;
        Bitmap pic = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
        pic.eraseColor(0xFF3366CC);
        android.graphics.Paint ground = new android.graphics.Paint();
        ground.setColor(0xFF2E7D32);
        new android.graphics.Canvas(pic).drawRect(0, 230, 640, 360, ground);
        File f = new File(app.getCacheDir(), "list-picture.png");
        try (FileOutputStream o = new FileOutputStream(f)) {
            pic.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
        Channel c0 = Library.get(app).byKey(ch.get(0).key);
        Guide.Schedule s = Library.get(app).schedule(c0.key);
        int at = s.at(System.currentTimeMillis());
        ProgrammePictures.preview(c0, s, at, "file:" + f.getPath());
        list.refresh();
        idle();
        Bitmap b = drawn(list);
        // The one on now: the second row (from 218, 65 high), on an amber-tinted band, its dot amber; the row above plain.
        int band = b.getPixel(1152, 226), above = b.getPixel(1152, 190);
        assertTrue(Integer.toHexString(band) + " / " + Integer.toHexString(above), red(band) > red(above) + 15 && red(band) > blue(band) + 10);
        int dot = b.getPixel(710, 246);
        assertTrue(Integer.toHexString(dot), near(ChannelList.AMBER, dot, 30));
        // The card at the top right: its picture across its top, LIVE's red dot on it.
        int top = b.getPixel(1210 + 500, 48 + 100);
        assertTrue(Integer.toHexString(top), near(0xFF3366CC, top, 14));
        int live = b.getPixel(1210 + 51, 48 + 43);
        assertTrue(Integer.toHexString(live), red(live) > 200 && green(live) < 120 && blue(live) < 120);
        // Under the picture, the card's dark glass.
        int glass = b.getPixel(1210 + 640, 48 + 420);
        assertTrue(Integer.toHexString(glass), Math.max(red(glass), Math.max(green(glass), blue(glass))) < 60);
        b.recycle();
    }

    @Test public void rightGoesOntoTheProgrammesAndAlongThemAndLeftComesBack() throws Exception {
        List<Channel> ch = new ArrayList<>();
        TvActivity a = open(ch, new HashMap<>());
        ChannelList list = list(a);
        Guide.Schedule s = Library.get(made).schedule(ch.get(0).key);
        long now = System.currentTimeMillis();
        int at = s.at(now);
        assertFalse(list.onProgrammes());
        Bitmap b = drawn(list);
        assertFalse("no ring on the programmes yet", near(0xFFFFFFFF, b.getPixel(930, 216), 40));
        b.recycle();
        // Right: onto the one on now, lit with its ring.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(list.onProgrammes());
        assertEquals(at, list.programme());
        b = drawn(list);
        assertTrue(Integer.toHexString(b.getPixel(930, 216)), near(0xFFFFFFFF, b.getPixel(930, 216), 16));
        b.recycle();
        // Down along those to come, Up back past now into those over.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(at + 2, list.programme());
        for (int i = 0; i < 4; i++) press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(at - 2, list.programme());
        assertTrue(list.getChildAt(0).getContentDescription().toString().contains("catch-up"));
        // Up as far as the archive keeps (a day: 48 half hours), and no further.
        for (int i = 0; i < 12; i++) press(a, KeyEvent.KEYCODE_CHANNEL_UP);
        int oldest = list.programme();
        assertTrue(Catchup.has(ch.get(0), s.start[oldest], now));
        assertFalse(Catchup.has(ch.get(0), s.start[oldest - 1], now));
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(oldest, list.programme());
        // Left: back on the channels, the same channel.
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertFalse(list.onProgrammes());
        assertEquals(ch.get(0).key, list.channel().key);
        assertEquals(LiveScreen.LIST, mode(a));
        // A channel with no archive: the one before now is shown but can't be chosen.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        int at1 = Library.get(made).schedule(ch.get(1).key).at(now);
        assertEquals(at1, list.programme());
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(at1, list.programme());
        // Back leaves the programmes first, then the list.
        press(a, KeyEvent.KEYCODE_BACK);
        assertFalse(list.onProgrammes());
        assertEquals(LiveScreen.LIST, mode(a));
    }

    @Test public void theCatchUpMarkOnlyWhereTheArchiveKeepsIt() throws Exception {
        List<Channel> ch = new ArrayList<>();
        TvActivity a = open(ch, new HashMap<>());
        ChannelList list = list(a);
        // The first channel keeps a day: the one before now (the first row, from y 163) has the mark beside its time.
        Bitmap b = drawn(list);
        assertTrue("the mark", brightest(b, 799, 181, 813, 195) > 120);
        b.recycle();
        // The second keeps none: the same row, no mark (and nothing listed further back).
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        b = drawn(list);
        assertTrue("no mark", brightest(b, 799, 181, 811, 195) < 60);
        b.recycle();
        // The rule is the guide's: a day back and no further, only once it has started.
        long now = System.currentTimeMillis();
        assertTrue(Catchup.has(ch.get(0), now - 23 * 3600_000L, now));
        assertFalse(Catchup.has(ch.get(0), now - 25 * 3600_000L, now));
        assertFalse(Catchup.has(ch.get(1), now - 3600_000L, now));
    }

    @Test public void okOnOneOverPlaysItFromItsStartInCatchUp() throws Exception {
        List<Channel> ch = new ArrayList<>();
        TvActivity a = open(ch, new HashMap<>());
        Guide.Schedule s = Library.get(made).schedule(ch.get(0).key);
        int at = s.at(System.currentTimeMillis());
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(at - 2, list(a).programme());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        idle();
        assertTrue(String.valueOf(a.top()), a.top() instanceof CatchupScreen);
        assertTrue(a.live.behind());
        long[] asked = a.live.asked();
        assertTrue(asked != null && Math.abs(asked[0] - s.start[at - 2]) <= 60_000L);
        assertEquals(ch.get(0).key, a.live.channel().key);
    }

    @Test public void okOnNowPlaysTheChannelLiveAndOnOneToComeSetsItsReminder() throws Exception {
        List<Channel> ch = new ArrayList<>();
        TvActivity a = open(ch, new HashMap<>());
        ChannelList list = list(a);
        // The second channel (not playing): Right onto now, OK plays it live, the list staying.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(ch.get(1).key, a.live.channel().key);
        assertFalse(a.live.behind());
        assertEquals(LiveScreen.LIST, mode(a));
        // Down to the next one: OK sets its reminder (a bell at its right), OK again takes it off.
        Guide.Schedule s = Library.get(made).schedule(ch.get(1).key);
        int at = s.at(System.currentTimeMillis());
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(Reminders.has(made, ch.get(1).key, s.start[at + 1]));
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(Reminders.has(made, ch.get(1).key, s.start[at + 1]));
        // Up onto now on the channel playing: OK goes full screen.
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(at, list.programme());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertNotEquals(LiveScreen.LIST, mode(a));
        assertEquals(ch.get(1).key, a.live.channel().key);
    }
}
