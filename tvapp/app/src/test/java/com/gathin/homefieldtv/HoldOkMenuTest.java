package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import com.gathin.broadcast.BoardSpec;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpChannels;
import com.gathin.homefieldtv.sports.BoardClock;
import com.gathin.homefieldtv.sports.Game;
import com.gathin.homefieldtv.sports.InStep;

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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OK held in Homefield TV's player, as the launcher's over TiviMate (the family: "hold down ok when I'm in full screen
 * on the homefield tv app and the scoreboard pop up settings menu comes up ..., and then I can turn the scoreboard and
 * logo on and off just like it is on tivimate"): the scoreboard and logo menu (BoardMenu) only while a live channel plays
 * full screen with nothing over it, over a match or not; a short OK as before; Scoreboard and Logo's cover off and on;
 * Back, fifteen seconds without a key, or anything over the picture closes it; elsewhere (a panel up, the guide with its
 * preview, catch-up) OK held does what it did. Settings has no Scoreboard page now: every setting it had is in the menu.
 * Invented channels (MadeUpChannels) and an invented match (Northfield v Riverside).
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class HoldOkMenuTest {
    private ActivityController<TvActivity> tv;
    private Context app;
    private List<Channel> ch;

    @Before public void setUp() {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        clear();
        ch = MadeUpChannels.load(app);
        // Channels watched before, so OK brings up the overlay with its recent channels (with none, its settings at once).
        List<String> recent = new ArrayList<>();
        for (Channel c : ch) recent.add(c.key);
        Library.get(app).setPicks(new ArrayList<>(), recent);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
    }

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        Library.get(app).setPicks(new ArrayList<>(), new ArrayList<>());
        MadeUpChannels.unload(app);
        clear();
        tv = null;
        Exec.inline = false;
    }

    private void clear() {
        for (String p : new String[]{"scoreboard", "remote", "in_step", "sync_store"}) {
            app.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void idleFor(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    /** OK (or another key) held past TvActivity.LONG_MS, then let go. */
    private static void hold(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 100, KeyEvent.ACTION_DOWN, code, 1));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 120, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    /** A channel on, its info card put away with Back: plain full-screen playback. */
    private LiveScreen plainPlayback(int i) {
        TvActivity a = tv.get();
        a.watch(ch.get(i));
        idle();
        LiveScreen live = (LiveScreen) a.top();
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(LiveScreen.NONE, mode(live));
        return live;
    }

    private static BoardLayer board(LiveScreen s) {
        View v = ((ViewGroup) s.view()).getChildAt(0);
        assertTrue(v instanceof BoardLayer);
        return (BoardLayer) v;
    }

    /** A screen's private mode (what is over the picture), read as the tests may. */
    private static int mode(Object screen) {
        try {
            java.lang.reflect.Field f = screen.getClass().getDeclaredField("mode");
            f.setAccessible(true);
            return f.getInt(screen);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Game match() {
        Game g = new Game();
        g.id = "hold-ok-770";
        g.slug = "made.up";
        g.league = "Northern Shield";
        g.leagueName = "Northern Shield";
        g.home = "NOR";
        g.away = "RIV";
        g.homeName = "Northfield";
        g.awayName = "Riverside";
        g.homeColor = "0f8b8d";
        g.awayColor = "7a1f3d";
        g.state = Game.LIVE;
        g.homeScore = 1;
        g.detail = "68'";
        g.kickoff = System.currentTimeMillis() - 70 * 60_000L;
        return g;
    }

    /** The invented match found for channel i, in step with it (no delay), the board up over it. */
    private void overAMatch(BoardLayer b, int i) {
        InStep.setDelay(app, ch.get(i).key, 0);
        long now = System.currentTimeMillis();
        b.found(match(), Collections.singletonList(new BoardClock.Reading(now - 3_000L, 1, 0, Game.LIVE, false, 67 * 60, "68'")));
        idle();
        assertEquals(View.VISIBLE, b.bug.getVisibility());
        assertTrue(b.coverUp());
    }

    /** Down along the menu to a line. */
    private static void downTo(TvActivity a, BoardLayer b, int line) {
        while (b.menu.at < line) press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(line, b.menu.at);
    }

    // ---- the pure parts ------------------------------------------------------------------------------------------------

    @Test public void theMenusLinesAsTheSwitchesStand() {
        BoardMenu.MenuState m = new BoardMenu.MenuState();
        m.boardShown = false;
        m.look = "By competition";
        m.boardSize = 116;
        m.logoSize = 100;
        m.mine = "Floodlit";
        m.timing = "15 s behind";
        m.usual = BoardMenu.usualWords(60);
        m.var = false;
        m.popups = BoardPrefs.popupWords("strap");
        m.sound = "Swoosh";
        m.every = true;
        assertArrayEquals(new String[]{
                "Scoreboard:  ‹  Off  ›",
                "Logo's cover:  ‹  On  ›",
                "Scoreboard look:  ‹  By competition  ›",
                "Move and resize the scoreboard:  ‹  116%  ›  ·  OK: move it with the arrows",
                "Move and resize the logo's cover:  ‹  100%  ›  ·  OK: move it with the arrows",
                "Line-ups on screen:  ‹  On  ›  ·  OK: show them now",
                "My scoreboards:  ‹  Floodlit  ›  ·  OK: the editor, Generate, Ask AI",
                "New scoreboard  ·  OK: a new one in the editor",
                "Timing on this channel:  ‹  15 s behind  ›  ·  Left sooner, Right later",
                "A channel not lined up yet:  ‹  60 s behind live  ›",
                "It's just happened  ·  OK: as a goal, a card or the kick-off shows",
                "Ask at each kick-off:  ‹  On  ›",
                "Alerts beside the board:  ‹  On  ›",
                "VAR explained:  ‹  Off  ›",
                "Kick-off and goal pop-ups:  ‹  In my scoreboard's look  ›",
                "Goal sound:  ‹  Swoosh  ›",
                "Swishes:  ‹  On  ›",
                "Back to the standard places:  ‹  Every channel  ›  ·  OK: put them back"}, BoardMenu.menuLines(m));
        // None of My scoreboards in use: the launcher's words.
        assertTrue(BoardMenu.menuLines(new BoardMenu.MenuState())[BoardMenu.M_MINE].startsWith("My scoreboards:  ‹  None (the look above)  ›"));
        assertEquals("Live, no delay", BoardMenu.usualWords(0));
        assertEquals(90, BoardMenu.nextUsual(60, 1));
        assertEquals(45, BoardMenu.nextUsual(60, -1));
        assertEquals(120, BoardMenu.nextUsual(120, 1));
        assertEquals(0, BoardMenu.nextUsual(0, -1));
        assertEquals(15, BoardMenu.nextUsual(14, 0));
        assertEquals("", BoardMenu.keptFor(""));
        assertEquals("The places, sizes and timing are kept for Harbour TV", BoardMenu.keptFor("Harbour TV"));
        assertEquals(15_000L, BoardMenu.MENU_MS);
    }

    @Test public void theThreeSwitchesBeforeBecomeTheMenusTwoOnce() {
        // Pure: what showed before shows still.
        assertArrayEquals(new boolean[]{true, true}, BoardPrefs.twoSwitches(true, true, false));
        assertArrayEquals(new boolean[]{true, false}, BoardPrefs.twoSwitches(true, false, false));
        assertArrayEquals(new boolean[]{false, false}, BoardPrefs.twoSwitches(false, true, false));
        assertArrayEquals(new boolean[]{false, true}, BoardPrefs.twoSwitches(true, false, true));
        assertArrayEquals(new boolean[]{false, true}, BoardPrefs.twoSwitches(false, false, true));
        // Logo cover only kept from before: the scoreboard off, the cover on.
        android.content.SharedPreferences p = app.getSharedPreferences("scoreboard", Context.MODE_PRIVATE);
        p.edit().clear().putBoolean("on", true).putBoolean("logo", false).putBoolean("logo_only", true).commit();
        assertFalse(BoardPrefs.on(app));
        assertTrue(BoardPrefs.logoOn(app));
        assertFalse(p.contains("logo_only"));
        // Once: switched on again it stays on.
        BoardPrefs.setOn(app, true);
        assertTrue(BoardPrefs.on(app));
        // The scoreboard off before (nothing over a match): nothing still.
        p.edit().clear().putBoolean("on", false).commit();
        assertFalse(BoardPrefs.on(app));
        assertFalse(BoardPrefs.logoOn(app));
    }

    // ---- OK held in plain playback -------------------------------------------------------------------------------------

    @Test public void okHeldInPlainPlaybackOpensTheMenuWithNoMatch() {
        TvActivity a = tv.get();
        LiveScreen live = plainPlayback(2);
        BoardLayer b = board(live);
        // A short OK: the overlay, as before; not the menu.
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(b.menuOpen());
        assertEquals(LiveScreen.OVERLAY, mode(live));
        press(a, KeyEvent.KEYCODE_BACK);
        assertEquals(LiveScreen.NONE, mode(live));

        // Held: the scoreboard and logo menu, no match on the channel; the player's settings do not come up.
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(b.menuOpen());
        assertTrue(b.takesKeys());
        assertEquals(LiveScreen.NONE, mode(live));
        assertEquals(0, b.menu.at);
        BoardMenu.MenuState m = b.menuState();
        assertTrue(m.note, m.note.startsWith("No match on this channel now"));
        assertEquals(ChannelEditsName.of(ch.get(2)), m.channel);
        // Held again while it is up: nothing more.
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(b.menuOpen());
        assertEquals(LiveScreen.NONE, mode(live));

        // The two switches work on any channel, kept for the next match.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFalse(BoardPrefs.on(app));
        assertEquals("Scoreboard:  ‹  Off  ›", BoardMenu.menuLines(b.menuState())[BoardMenu.M_BOARD]);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(BoardPrefs.on(app));
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(BoardPrefs.logoOn(app));
        assertEquals("Logo's cover:  ‹  Off  ›", BoardMenu.menuLines(b.menuState())[BoardMenu.M_LOGO]);
        // Up stops at the first line, as the launcher's.
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(0, b.menu.at);

        // Back closes it; the picture plain again.
        press(a, KeyEvent.KEYCODE_BACK);
        assertFalse(b.menuOpen());
        assertTrue(a.top() == live);
        assertEquals(LiveScreen.NONE, mode(live));
    }

    @Test public void overAMatchScoreboardAndLogoOffAndOn() {
        TvActivity a = tv.get();
        LiveScreen live = plainPlayback(3);
        BoardLayer b = board(live);
        overAMatch(b, 3);
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(b.menuOpen());
        assertEquals("Northfield v Riverside", b.menuState().note);

        // Scoreboard: Right, off: the board away at once and staying away, the cover still there.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFalse(BoardPrefs.on(app));
        assertEquals(View.INVISIBLE, b.bug.getVisibility());
        assertTrue(b.coverUp());
        idleFor(3_000L);
        assertEquals(View.INVISIBLE, b.bug.getVisibility());
        assertTrue(b.coverUp());
        // Nothing slides out beside it while it is off.
        b.drop(b.momentDrop("HALF TIME", "1–0"));
        idle();
        assertFalse(b.drop.isOut());
        // Right again: back.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(BoardPrefs.on(app));
        assertEquals(View.VISIBLE, b.bug.getVisibility());

        // Logo's cover: off, the cover goes (the board stays); on again.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFalse(BoardPrefs.logoOn(app));
        assertFalse(b.coverUp());
        assertEquals(View.VISIBLE, b.bug.getVisibility());
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertTrue(BoardPrefs.logoOn(app));
        assertTrue(b.coverUp());

        // What Logo cover only was: the scoreboard off and the cover on, the cover alone.
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(View.INVISIBLE, b.bug.getVisibility());
        assertTrue(b.coverUp());

        // Back closes the menu; the switches stay as set.
        press(a, KeyEvent.KEYCODE_BACK);
        assertFalse(b.menuOpen());
        idleFor(2_000L);
        assertEquals(View.INVISIBLE, b.bug.getVisibility());
        assertTrue(b.coverUp());
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(View.VISIBLE, b.bug.getVisibility());
    }

    @Test public void itClosesItselfAfterFifteenSecondsAndWithAnythingOverThePicture() {
        TvActivity a = tv.get();
        LiveScreen live = plainPlayback(1);
        BoardLayer b = board(live);
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(b.menuOpen());
        idleFor(14_000L);
        assertTrue(b.menuOpen());
        // A key starts the fifteen seconds again.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        idleFor(14_000L);
        assertTrue(b.menuOpen());
        idleFor(2_000L);
        assertFalse(b.menuOpen());
        assertEquals(LiveScreen.NONE, mode(live));

        // The info card over the picture: it goes at once.
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(b.menuOpen());
        press(a, KeyEvent.KEYCODE_INFO);
        assertFalse(b.menuOpen());
        press(a, KeyEvent.KEYCODE_BACK);
        assertFalse(b.menuOpen());
        // Another screen over the player.
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(b.menuOpen());
        a.push(new SettingsScreen(a));
        idle();
        assertFalse(b.menuOpen());
    }

    // ---- elsewhere OK held is as it was ------------------------------------------------------------------------------

    @Test public void onlyInPlainLivePlaybackElsewhereOkHeldIsAsBefore() throws Exception {
        TvActivity a = tv.get();
        a.watch(ch.get(2));
        idle();
        LiveScreen live = (LiveScreen) a.top();
        BoardLayer b = board(live);

        // A panel up over the picture (the info card as the channel comes on): the player's settings, as OK held was before.
        assertEquals(LiveScreen.BANNER, mode(live));
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(b.menuOpen());
        assertEquals(LiveScreen.BAR, mode(live));
        press(a, KeyEvent.KEYCODE_MENU);
        assertEquals(LiveScreen.NONE, mode(live));
        // The OK overlay up: its settings too.
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(LiveScreen.OVERLAY, mode(live));
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(b.menuOpen());
        assertEquals(LiveScreen.BAR, mode(live));
        press(a, KeyEvent.KEYCODE_MENU);

        // Plain full screen: the menu.
        assertEquals(LiveScreen.NONE, mode(live));
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(b.menuOpen());
        press(a, KeyEvent.KEYCODE_BACK);
        assertFalse(b.menuOpen());

        // The TV guide with the channel in its preview: the guide's own menu, never the scoreboard's.
        press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(a.top() instanceof GuideScreen);
        GuideScreen guide = (GuideScreen) a.top();
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(b.menuOpen());
        assertTrue(a.top() == guide);
        java.lang.reflect.Field menu = GuideScreen.class.getDeclaredField("menu");
        menu.setAccessible(true);
        assertTrue(((GuideMenu) menu.get(guide)).isOpen());
        press(a, KeyEvent.KEYCODE_BACK);

        // Catch-up over the live screen (a programme from its start): its own bar, as before.
        long now = System.currentTimeMillis();
        a.watchFrom(ch.get(2), now - 20 * 60_000L, now + 10 * 60_000L);
        idle();
        assertTrue(a.top() instanceof CatchupScreen);
        CatchupScreen catchup = (CatchupScreen) a.top();
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() == catchup);
        assertEquals(CatchupScreen.BAR, mode(catchup));
        Screen under = a.below(catchup);
        assertTrue(under instanceof LiveScreen);
        assertFalse(board((LiveScreen) under).menuOpen());

        // Settings → Remote → Long OK set to the player's menu: its settings even in plain playback.
        press(a, KeyEvent.KEYCODE_BACK);
        com.gathin.homefieldtv.prefs.RemoteKeys.setOkLong(app, com.gathin.homefieldtv.prefs.RemoteKeys.CHANNEL_MENU);
        LiveScreen again = plainPlayback(4);
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(board(again).menuOpen());
        assertEquals(LiveScreen.BAR, mode(again));
    }

    // ---- Settings' Scoreboard page gone: each of its settings in the menu ------------------------------------------

    @Test public void settingsHasNoScoreboardPageAndEachOfItsSettingsIsInTheMenu() {
        TvActivity a = tv.get();
        SettingsScreen s = new SettingsScreen(a);
        a.push(s);
        idle();
        // Every page Settings has, from its sections down.
        Set<String> seen = new HashSet<>();
        Deque<String> todo = new ArrayDeque<>(Arrays.asList(SettingsScreen.SECTIONS));
        boolean askAi = false;
        while (!todo.isEmpty()) {
            String page = todo.poll();
            if (!seen.add(page) || seen.size() > 400) continue;
            for (RowList.Row r : s.rows(page)) {
                String tag = r.tag instanceof String ? (String) r.tag : "";
                String title = r.title == null ? "" : r.title;
                assertNotEquals(page, "Scoreboard", title);
                assertNotEquals(page, "More scoreboard settings", title);
                assertNotEquals(page, "Logo cover only", title);
                assertFalse(page + ": " + tag, tag.contains("other.scoreboard") || tag.contains("board.mine"));
                String words = (title + " " + (r.value == null ? "" : r.value)).toLowerCase(java.util.Locale.ROOT);
                assertFalse(page + ": " + words, words.contains("scoreboard settings") || words.contains("bar's scoreboard"));
                if (page.equals("other") && tag.equals("page:" + AiSettings.PAGE)) askAi = true;
                if (tag.startsWith("page:")) todo.add(tag.substring(5));
            }
        }
        assertTrue(seen.contains("other"));
        // Ask AI keeps its own page under Other.
        assertTrue(askAi);
        assertFalse(s.rows(AiSettings.PAGE).isEmpty());
        assertTrue(s.rows("other.scoreboard").isEmpty());
        assertTrue(s.rows("other.scoreboard.more").isEmpty());
        press(a, KeyEvent.KEYCODE_BACK);
        while (a.top() instanceof SettingsScreen) press(a, KeyEvent.KEYCODE_BACK);

        // Each of the page's settings from the menu over a match.
        String mine = MyBoardStore.add(app, "Floodlit", BoardSpec.of(BoardSpec.Base.GLASS).validated());
        LiveScreen live = plainPlayback(3);
        BoardLayer b = board(live);
        overAMatch(b, 3);
        String key = ch.get(3).key;
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(b.menuOpen());
        // Scoreboard and Logo cover (and so Logo cover only: one off, the other on).
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFalse(BoardPrefs.on(app));
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertTrue(BoardPrefs.on(app));
        downTo(a, b, BoardMenu.M_LOGO);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFalse(BoardPrefs.logoOn(app));
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(BoardPrefs.logoOn(app));
        // Look.
        BoardBug.Style look = BoardPrefs.style(app);
        downTo(a, b, BoardMenu.M_LOOK);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertNotEquals(look, BoardPrefs.style(app));
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(look, BoardPrefs.style(app));
        // Sizing: the board and the cover bigger, kept for the channel.
        float bh = BoardPrefs.place(app, key).bh, lw = BoardPrefs.place(app, key).lw;
        downTo(a, b, BoardMenu.M_MOVE);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(BoardPrefs.place(app, key).bh > bh);
        downTo(a, b, BoardMenu.M_MOVE_LOGO);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(BoardPrefs.place(app, key).lw > lw);
        // Moving: OK, the arrows move the cover, OK keeps it there.
        float lx = BoardPrefs.place(app, key).lx;
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(b.menuOpen());
        assertTrue(b.takesKeys());
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(b.takesKeys());
        assertTrue(BoardPrefs.place(app, key).lx < lx);
        // And the board itself.
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        float by = BoardPrefs.place(app, key).by;
        downTo(a, b, BoardMenu.M_MOVE);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(BoardPrefs.place(app, key).by > by);
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        // Line-ups before kick-off.
        downTo(a, b, BoardMenu.M_LINEUPS);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFalse(BoardPrefs.lineupsOn(app));
        // My scoreboards: the saved board in use.
        downTo(a, b, BoardMenu.M_MINE);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(mine, BoardPrefs.customId(app));
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertNull(BoardPrefs.customId(app));
        // Timing on this channel, and for a channel not lined up yet.
        downTo(a, b, BoardMenu.M_TIMING);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(InStep.STEP_S, InStep.delay(app, key));
        int usual = InStep.usual(app);
        downTo(a, b, BoardMenu.M_USUAL);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(BoardMenu.nextUsual(usual, 1), InStep.usual(app));
        // Ask at each kick-off, the alerts, VAR explained, the pop-ups, the goal sound, the swishes.
        boolean asking = InStep.asking(app);
        downTo(a, b, BoardMenu.M_ASK);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(!asking, InStep.asking(app));
        downTo(a, b, BoardMenu.M_ALERTS);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFalse(BoardPrefs.dropsOn(app));
        downTo(a, b, BoardMenu.M_VAR);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFalse(BoardPrefs.varExplainedOn(app));
        downTo(a, b, BoardMenu.M_POPUPS);
        String popups = BoardPrefs.popupStyle(app);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(BoardPrefs.nextPopup(popups, 1), BoardPrefs.popupStyle(app));
        downTo(a, b, BoardMenu.M_SOUND);
        String sound = BoardPrefs.goalSound(app);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertNotEquals(sound, BoardPrefs.goalSound(app));
        downTo(a, b, BoardMenu.M_SWISH);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFalse(BoardPrefs.swishOn(app));
        assertFalse(BoardPrefs.scoreSwishOn(app));
        // Back to the standard places, on every channel.
        downTo(a, b, BoardMenu.M_RESET);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(b.menuState().every);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(BoardPrefs.Place.standard().bh, BoardPrefs.place(app, key).bh, 1e-6f);
        assertEquals(BoardPrefs.Place.standard().lx, BoardPrefs.place(app, key).lx, 1e-6f);
        // The last line is the last: Down stays there.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(BoardMenu.LINES - 1, b.menu.at);
        // New scoreboard: the editor over the match.
        b.menu.at = BoardMenu.M_NEW;
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(b.menuOpen());
        assertTrue(b.editor.open);
    }

    /** A channel's name as the menu says it. */
    private static final class ChannelEditsName {
        static String of(Channel c) {
            return com.gathin.homefieldtv.prefs.ChannelEdits.name(c);
        }
    }
}
