package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpGuide;
import com.gathin.homefieldtv.engine.Recordings;
import com.gathin.homefieldtv.engine.Reminders;

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
import java.util.List;

/**
 * The TV guide as the family's TiviMate has it, in the 07 look under the tab bar: back from the player onto the channel
 * playing in its preview, the rows and the programmes, the groups pushing it right, the menus (Remind kept), Up to the
 * tab bar, Back's steps; the archive by day playing from the archive; and the grid's sums.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class GuideScreenTest {
    private ActivityController<TvActivity> tv;
    private Context made;

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        if (made != null) MadeUpGuide.unload(made);
        Reminders.reset();
        Recordings.reset();
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

    private static void hold(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 100, KeyEvent.ACTION_DOWN, code, 1));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 120, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    @Test public void theGridsSums() {
        // The remote's row sits second from the top where it can (07's stills); at the list's end the last row is at the foot.
        assertEquals(0, GuideGrid.topFor(0, 50, 7));
        assertEquals(0, GuideGrid.topFor(1, 50, 7));
        assertEquals(2, GuideGrid.topFor(3, 50, 7));
        assertEquals(3, GuideGrid.topFor(4, 50, 7));
        assertEquals(43, GuideGrid.topFor(49, 50, 7));
        assertEquals(0, GuideGrid.topFor(2, 3, 7));
        // 300 px a half hour from x 512 to 1860: about 2 h 15 min across; six rows 92 apart under the times.
        assertEquals(134, GuideGrid.SHOWN_MS / 60_000L);
        assertEquals(6, GuideGrid.visible());
        // What is left of a programme on now, as the cells write it.
        assertEquals("40 min left", GuideGrid.leftWords(40));
        assertEquals("1h 10m left", GuideGrid.leftWords(70));
        assertEquals("2h 05m left", GuideGrid.leftWords(125));
        long t = 1_800_000_000_000L;
        assertEquals(0, GuideGrid.half(t + 17 * 60_000L) % GuideGrid.HALF);
        assertTrue(GuideGrid.half(t + 17 * 60_000L) <= t + 17 * 60_000L);
        // What the remote is on: a programme, the gap between two, before the first and after the last, no guide.
        Guide.Schedule s = MadeUpGuide.schedule(new long[]{1000, 3000}, new long[]{2000, 4000});
        assertArrayEquals(new long[]{1000, 2000, 0}, GuideGrid.span(s, 1500));
        assertArrayEquals(new long[]{3000, 4000, 1}, GuideGrid.span(s, 3000));
        assertArrayEquals(new long[]{2000, 3000, -1}, GuideGrid.span(s, 2500));
        long[] before = GuideGrid.span(s, 500), after = GuideGrid.span(s, 5000), none = GuideGrid.span(null, 5);
        assertTrue(before[0] < 0 && before[1] == 1000 && before[2] == -1);
        assertTrue(after[0] == 4000 && after[1] > 4_000_000_000_000_000L && after[2] == -1);
        assertTrue(none[0] < 0 && none[1] > 4_000_000_000_000_000L && none[2] == -1);
        // The playlist a channel's key comes from; minutes as the TV writes them.
        assertEquals("", GuideScreen.prefix("g:4000"));
        assertEquals("p1:", GuideScreen.prefix("p1:g:4000"));
        assertEquals("p12:", GuideScreen.prefix("p12:x"));
        assertEquals("", GuideScreen.prefix("pa:1"));
        assertEquals("16 min", GuideBand.minutes(15 * 60_000L + 10_000L));
        assertEquals("435 min", GuideBand.minutes(435 * 60_000L));
        assertEquals("1 min", GuideBand.minutes(1L));
    }

    @Test public void theGuideAsTheFamilysTv() {
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        made = app;
        List<Channel> ch = MadeUpGuide.load(app);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        TvActivity a = tv.get();
        idle();

        // Back from the player (its card first): the guide, the channel playing in its preview at the top left, the remote
        // on it.
        a.watch(ch.get(2));
        idle();
        assertTrue(a.top() instanceof LiveScreen);
        for (int i = 0; i < 3 && a.top() instanceof LiveScreen; i++) press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() instanceof GuideScreen);
        GuideScreen g = (GuideScreen) a.top();
        assertEquals(ch.get(2).key, a.live.channel().key);
        assertEquals(ch.get(2).key, g.focusedChannel().key);
        assertEquals(new Rect(90, 136, 676, 466), a.live.where());
        // The guide is the tab bar's Guide page: the bar drawn over it, the remote in the guide.
        assertTrue(a.tabs().drawn());
        assertEquals(TabBar.GUIDE, a.tabs().page());
        assertFalse(a.tabs().active());
        assertEquals(0f, g.pushed(), 0f);
        assertEquals(0, g.area());

        // Down and Up: the rows.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(ch.get(3).key, g.focusedChannel().key);
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(ch.get(2).key, g.focusedChannel().key);

        // Right: the programme after the one on now; Back: the programmes on now again (not the groups yet).
        long now = System.currentTimeMillis();
        long[] onNow = g.focusedTimes();
        assertNotNull(onNow);
        assertTrue(onNow[0] <= now && now < onNow[1]);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(onNow[1], g.focusedTimes()[0]);
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(onNow[0], g.focusedTimes()[0]);
        assertEquals(0, g.area());

        // Left from the programme on now: the groups, the guide pushed right by them (504).
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(1, g.area());
        assertEquals(504f, g.pushed(), 0.5f);
        assertEquals(new Rect(594, 136, 1180, 466), a.live.where());
        // Down the groups: each shows at once (All channels, Recently watched, then the playlist's groups).
        assertEquals("all", g.list());
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals("recent", g.list());
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals("group:Entertainment", g.list());
        assertEquals("Entertainment", g.focusedChannel().group);
        // Left: nothing further (the old menu's drawer is the tab bar now); Right: the grid, the guide back in place.
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(1, g.area());
        assertEquals(504f, g.pushed(), 0.5f);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(0, g.area());
        assertEquals(0f, g.pushed(), 0f);

        // OK on a programme to come: its small menu; Back shuts it.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        long[] next = g.focusedTimes();
        assertTrue(next[0] > now);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(g.menuOpen());
        press(a, KeyEvent.KEYCODE_BACK);
        assertFalse(g.menuOpen());

        // OK held on it: the whole menu, Search first; past Settings and the programme's name, Remind sets a reminder.
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(g.menuOpen());
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(g.menuOpen());
        assertTrue(Reminders.has(app, g.focusedChannel().key, next[0]));
        // The first reminder asks once whether its card may show over other apps: Not now.
        press(a, KeyEvent.KEYCODE_BACK);

        // Up from the first row goes up to the tab bar (its Guide tab); Down comes back to the rows.
        int up = 0;
        while (!a.tabs().active() && up++ < 20) press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(a.tabs().active());
        assertEquals(TabBar.GUIDE, a.tabs().focus());
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertFalse(a.tabs().active());
        assertTrue(a.top() instanceof GuideScreen);

        // Back: the programmes on now, the groups, the tab bar, then Home's tab (Home).
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(0, g.area());
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(1, g.area());
        press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.tabs().active());
        press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() instanceof HomeScreen);
    }

    @Test public void theArchiveByDayPlaysFromTheArchive() {
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        made = app;
        List<Channel> ch = MadeUpGuide.load(app);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        TvActivity a = tv.get();
        idle();
        long now = System.currentTimeMillis();

        // Kestrel One HD keeps three days: its archive opens on the programme two hours ago.
        a.openGuide(null);
        idle();
        GuideActions.archive(a, ch.get(0), now - 2 * 3600_000L);
        idle();
        assertTrue(a.top() instanceof ArchiveScreen);
        Guide.Schedule shown = Library.get(app).schedule(ch.get(0).key);
        int i = shown.at(now - 2 * 3600_000L);
        // OK held on it: Search, Settings, then Watch, Record (kept from the channel's catch-up), Add to My list.
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        for (int k = 0; k < 3; k++) press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(Recordings.has(app, ch.get(0).key, shown.start[i]));
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        for (int k = 0; k < 4; k++) press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(MyListScreen.hasProgram(app, shown.title[i]));
        // Up and Down through the programmes, Right to the days and back.
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(a.top() instanceof ArchiveScreen);

        // OK on a programme already shown: from the archive (CatchupScreen over the channel's live screen).
        Guide.Schedule s = Library.get(app).schedule(ch.get(0).key);
        assertNotNull(s);
        GuideActions.archive(a, ch.get(0), now - 2 * 3600_000L);
        idle();
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof CatchupScreen);
        assertTrue(a.live.behind());
        // Back from it: the guide, the programme still in its preview.
        press(a, KeyEvent.KEYCODE_BACK);
        press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() instanceof GuideScreen);
        assertTrue(a.live.behind());
        assertEquals(ch.get(0).key, ((GuideScreen) a.top()).focusedChannel().key);
    }
}
