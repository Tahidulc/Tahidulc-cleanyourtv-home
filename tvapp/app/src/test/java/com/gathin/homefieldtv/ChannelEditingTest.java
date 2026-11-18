package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpGuide;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.ChannelEdits;

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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Channels and groups edited from the TV guide, as TiviMate's menus have it: the long-OK card's channel lines (Add to
 * Favourites, Block, Hide, Rename, Change logo, Move channel), the row lifted and moved, OK held on a group (Rename,
 * Hide, Move group, Sort channels), and Settings bringing them back. Invented channels (MadeUpGuide).
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class ChannelEditingTest {
    private ActivityController<TvActivity> tv;
    private Context app;
    private List<Channel> ch;

    @Before public void setUp() {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        clear();
        ch = MadeUpGuide.load(app);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
    }

    private void clear() {
        app.getSharedPreferences("channel_edits", Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences("live_groups", Context.MODE_PRIVATE).edit().clear().commit();
        ChannelEdits.forget();
        Categories.forget();
    }

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        MadeUpGuide.unload(app);
        clear();
        GuideScreen.forgetList();
        tv = null;
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

    private GuideScreen guide() {
        TvActivity a = tv.get();
        a.openGuide(null);
        idle();
        assertTrue(a.top() instanceof GuideScreen);
        return (GuideScreen) a.top();
    }

    private static List<String> titles(List<GuideMenu.Item> items) {
        List<String> out = new ArrayList<>();
        for (GuideMenu.Item it : items) out.add(it.title);
        return out;
    }

    private static List<String> keys(List<Channel> l) {
        List<String> out = new ArrayList<>();
        for (Channel c : l) out.add(c.key);
        return out;
    }

    @Test public void theCardHasTiviMatesChannelLinesThenTheEditing() {
        Channel kestrel = ch.get(0);
        Guide.Schedule s = Library.get(app).schedule(kestrel.key);
        List<GuideMenu.Item> card = GuideActions.card(app, kestrel, s, s.at(System.currentTimeMillis()), true, true);
        List<String> t = titles(card);
        int at = t.indexOf("Kestrel One HD");
        assertTrue(at > 0);
        assertEquals(GuideMenu.SECTION, card.get(at).kind);
        assertEquals(Arrays.asList("Add to Favourites", "Block channel", "Hide channel", "Rename channel", "Change logo", "Move channel",
                "Catch-up"), t.subList(at + 1, t.size()));
        // Channel names editor off: no Rename; the archive's card: no Move.
        ChannelEdits.setEditor(app, false);
        t = titles(GuideActions.card(app, kestrel, s, -1, false));
        assertFalse(t.contains("Rename channel"));
        assertFalse(t.contains("Move channel"));
        assertTrue(t.contains("Hide channel"));
        ChannelEdits.setEditor(app, true);
        // Renamed: under its own name, with Restore channel name.
        ChannelEdits.rename(app, kestrel, "Kestrel Classic");
        t = titles(GuideActions.card(app, kestrel, s, -1, true, true));
        assertTrue(t.contains("Kestrel Classic"));
        assertTrue(t.contains("Restore channel name"));
    }

    @Test public void hideRenameFromTheGuide() {
        TvActivity a = tv.get();
        GuideScreen g = guide();
        assertEquals(ch.get(0).key, g.focusedChannel().key);
        // OK held, Hide channel: gone from every list, the remote on the channel after it.
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(g.menuOpen());
        g.chosen(null, ChannelMenu.HIDE);
        idle();
        assertFalse(g.menuOpen());
        assertEquals(ch.get(1).key, g.focusedChannel().key);
        assertFalse(keys(Library.get(app).all()).contains(ch.get(0).key));
        assertTrue(ChannelEdits.hidden(ch.get(0).key));
        // Settings › Appearance › TV guide › Hidden channels lists it; OK there brings it back.
        a.push(new SettingsScreen(a, "appearance.guide.hidden"));
        idle();
        SettingsScreen set = (SettingsScreen) a.top();
        List<RowList.Row> rows = set.rows("appearance.guide.hidden");
        RowList.Row row = null;
        for (RowList.Row r : rows) if (("hch:" + ch.get(0).key).equals(r.tag)) row = r;
        assertNotNull(row);
        assertEquals("Kestrel One HD", row.title);
        set.chosen(null, row);
        assertFalse(ChannelEdits.hidden(ch.get(0).key));
        for (int i = 0; i < 6 && !(a.top() instanceof GuideScreen); i++) press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() instanceof GuideScreen);
        // OK held, Rename channel: the keyboard, the name typed kept; search finds it by both names.
        g = (GuideScreen) a.top();
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        g.chosen(null, ChannelMenu.RENAME);
        idle();
        assertTrue(a.top() instanceof RenameScreen);
        ((RenameScreen) a.top()).typeAndDone("Lighthouse Plays");
        idle();
        assertTrue(a.top() instanceof GuideScreen);
        Channel renamed = g.focusedChannel();
        assertEquals("Lighthouse Plays", ChannelEdits.name(renamed));
        assertEquals(1, Library.get(app).search("lighthouse", 5).size());
        assertEquals(renamed.key, Library.get(app).search(renamed.name, 5).get(0).key);
    }

    @Test public void moveChannelLiftsTheRowOkPutsItDownBackLeavesIt() {
        TvActivity a = tv.get();
        GuideScreen g = guide();
        // From All channels: its own group is shown, the row lifted there.
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        g.chosen(null, ChannelMenu.MOVE);
        idle();
        assertTrue(g.moving());
        assertEquals("group:Entertainment", g.list());
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        // Nothing else while it is lifted.
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(0, g.area());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(g.moving());
        List<Channel> ent = Library.get(app).group("Entertainment").channels;
        assertEquals(Arrays.asList(ch.get(1).key, ch.get(5).key, ch.get(0).key), keys(ent));
        assertEquals(ChannelEdits.MANUAL, Categories.sortOf(app, "Entertainment"));
        assertEquals(ch.get(0).key, g.focusedChannel().key);
        // Lifted again and moved up; Back leaves it where it was.
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        g.chosen(null, ChannelMenu.MOVE);
        idle();
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        press(a, KeyEvent.KEYCODE_BACK);
        assertFalse(g.moving());
        assertEquals(Arrays.asList(ch.get(1).key, ch.get(5).key, ch.get(0).key), keys(Library.get(app).group("Entertainment").channels));
        assertEquals(ch.get(0).key, g.focusedChannel().key);
    }

    @Test public void aGroupsMenu() {
        TvActivity a = tv.get();
        GuideScreen g = guide();
        // Left: the groups; down to Entertainment; OK held: its menu.
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals("group:Entertainment", g.list());
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(g.menuOpen());
        assertEquals(Arrays.asList("Entertainment", "Rename group", "Hide group", "Move group", "Sort channels",
                "Groups", "Show all groups", "Hide all groups", "Manage groups"), titles(GroupMenu.items(app, "Entertainment")));
        // Sort channels: its choices, the one in use ticked; by name.
        assertEquals(Arrays.asList("Sort channels  ·  Entertainment", "By order in playlist", "By name", "By number", "Manual"),
                titles(GroupMenu.sortItems(app, "Entertainment")));
        assertTrue(GroupMenu.sortItems(app, "Entertainment").get(1).checked);
        g.chosen(null, GroupMenu.SORT);
        assertTrue(g.menuOpen());
        g.chosen(null, GroupMenu.SORT_BY + ChannelEdits.NAME);
        idle();
        assertEquals(ChannelEdits.NAME, Categories.sortOf(app, "Entertainment"));
        assertEquals(Arrays.asList(ch.get(5).key, ch.get(0).key, ch.get(1).key), keys(Library.get(app).group("Entertainment").channels));
        // Rename group.
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        g.chosen(null, GroupMenu.RENAME);
        idle();
        ((RenameScreen) a.top()).typeAndDone("Shows");
        idle();
        assertEquals("Shows", Categories.label(app, "Entertainment"));
        // Move group: lifted, Down past Sports, put down there.
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        g.chosen(null, GroupMenu.MOVE);
        idle();
        assertTrue(g.moving());
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(g.moving());
        assertEquals(Arrays.asList("Sports", "Entertainment", "News"), Categories.arranged(app, Library.get(app).groups()));
        // Hide group, then Show all groups (from All channels' menu).
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        g.chosen(null, GroupMenu.HIDE);
        idle();
        assertTrue(Categories.isHidden(app, "Entertainment"));
        assertTrue(Library.get(app).group("Entertainment").channels.isEmpty());
        assertEquals(Arrays.asList("Groups", "Show all groups", "Hide all groups", "Manage groups"), titles(GroupMenu.items(app, null)));
        GroupMenu.act(a, GroupMenu.SHOW_ALL, null);
        assertFalse(Categories.isHidden(app, "Entertainment"));
    }
}
