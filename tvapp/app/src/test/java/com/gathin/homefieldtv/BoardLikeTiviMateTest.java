package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import com.gathin.broadcast.BoardAlert;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Homefield TV's scoreboard and logo as the TV launcher's over TiviMate (the family: "make the scoreboard and logo pop up
 * logic the same as tivimate. With the other games goals notifications coming from the scoreboard ... only when I'm
 * watching it on the full screen in homefield tv app"): only in the channel, live and full screen, each screen over it
 * suspending it and the plain picture bringing it back (InChannel); another game's goal handed over by the launcher
 * coming out of the board in its look, queued and timed as the launcher's (BoardInbox, BoardAlert); no doubles (taken:
 * the launcher shows no card; the board's own game's said once, by the board); and the launcher's Notifications switches
 * on the board's own alerts (AlertSwitches). Invented channels (MadeUpChannels) and invented matches.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class BoardLikeTiviMateTest {
    private ActivityController<TvActivity> tv;
    private Context app;
    private List<Channel> ch;

    @Before public void setUp() {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        clear();
        AlertSwitches.reset();
        AlertSwitches.launcher = (c, q) -> null;
        LogoHere.launcher = (c, q) -> null;
        ch = MadeUpChannels.load(app);
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
        AlertSwitches.reset();
        tv = null;
        Exec.inline = false;
    }

    private void clear() {
        for (String p : new String[]{"scoreboard", "remote", "in_step", "sync_store", "logo_extras"}) {
            app.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200));
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

    private LiveScreen plainPlayback(int i) {
        TvActivity a = tv.get();
        a.watch(ch.get(i));
        idle();
        LiveScreen live = (LiveScreen) a.top();
        press(a, KeyEvent.KEYCODE_BACK);
        return live;
    }

    private static BoardLayer board(LiveScreen s) {
        View v = ((ViewGroup) s.view()).getChildAt(0);
        assertTrue(v instanceof BoardLayer);
        return (BoardLayer) v;
    }

    static Game match(String id) {
        Game g = new Game();
        g.id = id;
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

    private void overAMatch(BoardLayer b, int i, String id) {
        InStep.setDelay(app, ch.get(i).key, 0);
        long now = System.currentTimeMillis();
        b.found(match(id), Collections.singletonList(new BoardClock.Reading(now - 3_000L, 1, 0, Game.LIVE, false, 67 * 60, "68'")));
        idle();
        assertEquals(View.VISIBLE, b.bug.getVisibility());
        assertTrue(b.coverUp());
    }

    /** Another game's goal as the launcher hands it over (BoardCover.dropOf's words for a card). */
    static Bundle otherGoal(String gameId, String score, String scorer) {
        BoardAlert a = new BoardAlert();
        a.gameId = gameId;
        a.kind = "goal";
        a.chip = "GOAL";
        a.title = "Eastgate " + score + " Westbury";
        a.line = scorer;
        a.colour = 0xFF3DDC84;
        a.homeColour = 0xFFC8102E;
        a.awayColour = 0xFF1B458F;
        a.side = 1;
        a.key = gameId + "|" + score;
        a.what = "GOAL " + a.title + " " + scorer;
        a.homeCrest = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
        a.awayCrest = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
        return a.toBundle();
    }

    static Bundle alert(String gameId, String kind, String chip, String title) {
        BoardAlert a = new BoardAlert();
        a.gameId = gameId;
        a.kind = kind;
        a.chip = chip;
        a.title = title;
        a.what = chip + " " + title;
        return a.toBundle();
    }

    // ---- only in the channel ----------------------------------------------------------------------------------------------

    @Test public void theOneRuleForBothApps() {
        assertTrue(com.gathin.broadcast.InChannel.shows(true, true, true, false));
        assertFalse("not the app's picture in front", com.gathin.broadcast.InChannel.shows(false, true, true, false));
        assertFalse("a small window", com.gathin.broadcast.InChannel.shows(true, false, true, false));
        assertFalse("behind live: catch-up, paused or rewound", com.gathin.broadcast.InChannel.shows(true, true, false, false));
        assertFalse("a guide, list, panel or menu over it", com.gathin.broadcast.InChannel.shows(true, true, true, true));
        assertTrue(BoardGate.plain(true, true, true, true, false, false));
        assertFalse(BoardGate.plain(true, true, false, true, false, false));
        // The five-part rule as before: live.
        assertTrue(BoardGate.plain(true, true, true, false, false));
    }

    @Test public void eachScreenOverThePictureSuspendsItAndThePlainPictureBringsItBack() throws Exception {
        TvActivity a = tv.get();
        LiveScreen live = plainPlayback(3);
        BoardLayer b = board(live);
        overAMatch(b, 3, "tm-1");
        assertTrue("up over the plain live picture: it takes the launcher's alerts", b.takesAlerts());

        // The info card at the foot (its channel's card): suspended; back as it drops away.
        press(a, KeyEvent.KEYCODE_INFO);
        assertSuspended(b);
        press(a, KeyEvent.KEYCODE_BACK);
        assertBack(b);
        // The OK overlay.
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertSuspended(b);
        press(a, KeyEvent.KEYCODE_BACK);
        assertBack(b);
        // The channel list.
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertSuspended(b);
        for (int i = 0; i < 3 && b.covered(); i++) press(a, KeyEvent.KEYCODE_BACK);
        assertBack(b);
        // The player's settings (MENU: the bar).
        press(a, KeyEvent.KEYCODE_MENU);
        assertSuspended(b);
        for (int i = 0; i < 3 && b.covered(); i++) press(a, KeyEvent.KEYCODE_BACK);
        assertBack(b);
        // A number being typed.
        press(a, KeyEvent.KEYCODE_7);
        assertSuspended(b);
        press(a, KeyEvent.KEYCODE_BACK);
        assertBack(b);
        // Settings over the player.
        a.push(new SettingsScreen(a));
        idle();
        assertFalse(b.takesAlerts());
        assertEquals(View.INVISIBLE, b.getVisibility());
        for (int i = 0; i < 4 && a.top() != live; i++) a.pop();
        idle();
        assertTrue(a.top() == live);
        assertBack(b);
        // The channel in a small window (as the guide's preview and the opaque list have it).
        Rect full = a.fullScreen();
        a.live.place(new Rect(1200, 100, 1800, 440));
        idle();
        assertSuspended(b);
        a.live.place(full);
        idle();
        assertBack(b);
        // The picture moved back behind live (paused or rewound in the stream's own window): not the live channel.
        java.lang.reflect.Field inWindow = LiveTv.class.getDeclaredField("inWindow");
        inWindow.setAccessible(true);
        inWindow.setBoolean(a.live, true);
        idle();
        assertSuspended(b);
        inWindow.setBoolean(a.live, false);
        idle();
        assertBack(b);
        // The TV guide (with this channel in its preview window): the live screen gone, the board stopped, nothing taken.
        a.toGuide();
        idle();
        assertFalse(a.top() == live);
        assertEquals(View.INVISIBLE, b.getVisibility());
        assertFalse(b.takesAlerts());
        assertFalse(BoardInbox.takes());
        // Full screen again: the board back over the match.
        LiveScreen again = plainPlayback(3);
        BoardLayer b2 = board(again);
        overAMatch(b2, 3, "tm-1");
        assertBack(b2);
    }

    private static void assertSuspended(BoardLayer b) {
        assertTrue(b.covered());
        assertEquals(View.INVISIBLE, b.getVisibility());
        assertFalse("nothing handed over while it is away: the launcher's card shows", b.takesAlerts());
    }

    private static void assertBack(BoardLayer b) {
        assertFalse(b.covered());
        assertEquals(View.VISIBLE, b.getVisibility());
        assertEquals(View.VISIBLE, b.bug.getVisibility());
        assertTrue(b.coverUp());
        assertTrue(b.takesAlerts());
    }

    // ---- another game's goal out of the board --------------------------------------------------------------------------

    @Test public void anotherGamesGoalComesOutOfTheBoardAsOverTiviMate() {
        LiveScreen live = plainPlayback(2);
        BoardLayer b = board(live);
        overAMatch(b, 2, "tm-own");
        Bundle answer = BoardInbox.offer(otherGoal("tm-other", "2–1", "Alex Player 67'"));
        assertTrue(answer.getBoolean(BoardAlert.TAKEN));
        assertEquals("out of the scoreboard", answer.getString(BoardAlert.WHY));
        idle();
        assertTrue(b.drop.isOut());
        BoardDrop.Drop d = b.drop.showing();
        assertEquals("GOAL", d.chip);
        assertEquals("Eastgate 2–1 Westbury", d.title);
        assertEquals("Alex Player 67'", d.line);
        assertFalse("another game's: both crests and the score", d.own);
        assertNotNull(d.homeCrest);
        // Out for the launcher's DROP_MS at least, then in.
        idleFor(BoardLayer.DROP_MS - 2_000L);
        assertTrue(b.drop.isOut());
        idleFor(6_000L);
        assertFalse(b.drop.isOut() && b.drop.showing() == d);
        // A goal said once: the same one handed over again is let go.
        BoardInbox.offer(otherGoal("tm-other", "2–1", "Alex Player 67'"));
        idle();
        assertTrue(b.queued().isEmpty());
        assertFalse(b.drop.isOut());
    }

    @Test public void queuedAsTheLaunchersBoardQueuesThem() {
        LiveScreen live = plainPlayback(2);
        BoardLayer b = board(live);
        overAMatch(b, 2, "tm-own");
        // The board's own goal sweeping across it: what is handed over waits for its band (as broadcasters caption a goal
        // once they have shown it).
        b.bug.goal(1);
        BoardInbox.offer(otherGoal("tm-a", "1–0", "First 10'"));
        idleFor(200L);
        assertFalse(b.drop.isOut());
        assertEquals(1, b.queued().size());
        // Five more while it waits: four at most wait, the oldest let go; VAR's goes to the front.
        BoardInbox.offer(otherGoal("tm-b", "1–0", "Second 11'"));
        BoardInbox.offer(otherGoal("tm-c", "1–0", "Third 12'"));
        BoardInbox.offer(otherGoal("tm-d", "1–0", "Fourth 13'"));
        BoardInbox.offer(otherGoal("tm-e", "1–0", "Fifth 14'"));
        BoardInbox.offer(alert("tm-f", "var", "VAR", "Check complete · No goal"));
        idleFor(200L);
        List<BoardDrop.Drop> q = b.queued();
        assertEquals(4, q.size());
        assertEquals("VAR", q.get(0).chip);
        // Once the band is done: out in turn, VAR first.
        // (The goal's moment runs on the wall clock: waited for as it is.)
        long left = b.bug.bandLeft(System.currentTimeMillis());
        assertTrue("the goal's own moment still to come", left > 0);
        try {
            Thread.sleep(left + 300L);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        idleFor(left + 1_000L);
        assertTrue(b.drop.isOut());
        assertEquals("VAR", b.drop.showing().chip);
        // Each out for the launcher's DROP_MS at least (a VAR decision for its WHY's time), then the next in turn: the goals
        // in the order they came.
        idleFor(Math.max(VarExplained.WHY_MS, BoardLayer.DROP_MS) + 1_500L);
        assertTrue(b.drop.isOut());
        assertEquals("Eastgate 1–0 Westbury", b.drop.showing().title);
        // (The first let go as the fifth came, the last as VAR's went to the front: as BoardCover.drop.)
        assertEquals("Second 11'", b.drop.showing().line);
        assertEquals(2, b.queued().size());
    }

    // ---- no doubles --------------------------------------------------------------------------------------------------------

    @Test public void noDoublesWithTheLauncher() {
        TvActivity a = tv.get();
        LiveScreen live = plainPlayback(2);
        BoardLayer b = board(live);
        overAMatch(b, 2, "tm-own");
        // The board's own game's goal: taken (the launcher shows no card) and let go: the board says it itself.
        Bundle own = BoardInbox.offer(otherGoal("tm-own", "2–0", "Own Scorer 70'"));
        assertTrue(own.getBoolean(BoardAlert.TAKEN));
        assertEquals("the scoreboard says its own game's itself", own.getString(BoardAlert.WHY));
        idle();
        assertFalse(b.drop.isOut());
        assertTrue(b.queued().isEmpty());
        // Its own game's ratings (a kind the board does not say): out of the board.
        BoardAlert ratings = new BoardAlert();
        ratings.gameId = "tm-own";
        ratings.kind = null;
        ratings.chip = "RATINGS";
        ratings.title = "Player of the match";
        ratings.pages = new String[][]{{"8.9", "Own Scorer"}};
        assertTrue(BoardInbox.offer(ratings.toBundle()).getBoolean(BoardAlert.TAKEN));
        idle();
        assertTrue(b.drop.isOut());
        assertTrue(b.drop.showing().own);

        // Covered (the OK overlay): not taken, so the launcher shows its own card over the app, as over TiviMate's guide.
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(BoardInbox.offer(otherGoal("tm-x", "1–1", "Covered 50'")).getBoolean(BoardAlert.TAKEN));
        press(a, KeyEvent.KEYCODE_BACK);
        assertTrue(BoardInbox.offer(otherGoal("tm-y", "1–1", "Back 51'")).getBoolean(BoardAlert.TAKEN));
        // The alerts beside the board switched off in the menu: not taken (the launcher's card).
        BoardPrefs.setDropsOn(app, false);
        idle();
        assertFalse(BoardInbox.offer(otherGoal("tm-z", "1–1", "Off 52'")).getBoolean(BoardAlert.TAKEN));
        BoardPrefs.setDropsOn(app, true);
        // The scoreboard switched off: not taken.
        b.boardShown(false);
        idle();
        assertFalse(BoardInbox.offer(otherGoal("tm-w", "1–1", "Board off 53'")).getBoolean(BoardAlert.TAKEN));
        b.boardShown(true);
        idle();
        assertTrue(BoardInbox.offer(otherGoal("tm-v", "1–1", "On again 54'")).getBoolean(BoardAlert.TAKEN));
        // Homefield TV away (another screen over the player, the app gone): nothing taken.
        a.push(new SettingsScreen(a));
        idle();
        assertFalse(BoardInbox.offer(otherGoal("tm-u", "1–1", "Away 55'")).getBoolean(BoardAlert.TAKEN));
    }

    @Test public void theRulesOfTheHandOver() {
        assertEquals(BoardAlert.NOT_UP, BoardAlert.decide(false, "g", "x", "goal", false, false));
        assertEquals(BoardAlert.SHOWN, BoardAlert.decide(true, "g", "x", "goal", false, false));
        assertEquals(BoardAlert.SAID, BoardAlert.decide(true, "g", "g", "goal", false, false));
        assertEquals(BoardAlert.SAID, BoardAlert.decide(true, "g", "g", "red", false, false));
        assertEquals(BoardAlert.SAID, BoardAlert.decide(true, "g", "g", "lineups", false, false));
        assertEquals("its own game's ratings: shown", BoardAlert.SHOWN, BoardAlert.decide(true, "g", "g", null, true, false));
        assertEquals("ratings late in full time's minute: the launcher's card", BoardAlert.NOT_UP,
                BoardAlert.decide(true, "g", "x", null, true, true));
        // Across the two apps: the words, colours and pictures as they went.
        BoardAlert a = BoardAlert.from(otherGoal("tm-1", "3–2", "Late Winner 90+4'"));
        assertNotNull(a);
        assertEquals("GOAL", a.chip);
        assertEquals("tm-1|3–2", a.key);
        assertEquals(0xFFC8102E, a.homeColour);
        assertEquals(1, a.side);
        assertNotNull(a.homeCrest);
        assertEquals(40, a.homeCrest.getWidth());
        assertNull(BoardAlert.from(new Bundle()));
        BoardAlert big = new BoardAlert();
        big.chip = "GOAL";
        big.photo = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888);
        BoardAlert back = BoardAlert.from(big.toBundle());
        assertEquals(BoardAlert.PICTURE_MAX, back.photo.getWidth());
        assertEquals(BoardAlert.PICTURE_MAX / 2, back.photo.getHeight());
        // The provider's call: only board_alert, from the family's own apps (this one is itself).
        ChannelCardsProvider p = new ChannelCardsProvider();
        p.attachInfo(app, null);
        assertNull(p.call("something else", null, null));
        assertNotNull(p.call(BoardAlert.BOARD_ALERT, null, otherGoal("tm-2", "1–0", "x")));
    }

    // ---- the family's switches -----------------------------------------------------------------------------------------

    @Test public void theLaunchersSwitchesHoldBackTheBoardsOwnAlerts() {
        // As the family has them: Goal Rush and the live table off; red cards switched off here for the test.
        AlertSwitches.launcher = (c, q) -> {
            Bundle s = new Bundle();
            for (String k : BoardAlert.KINDS) s.putBoolean(k, true);
            s.putBoolean("goalrush", false);
            s.putBoolean("table", false);
            s.putBoolean("red", false);
            assertEquals("tm-sw", q.getString(BoardAlert.GAME));
            assertEquals("made.up", q.getString(BoardAlert.SLUG));
            return s;
        };
        AlertSwitches.reset();
        LiveScreen live = plainPlayback(1);
        BoardLayer b = board(live);
        overAMatch(b, 1, "tm-sw");
        assertTrue(AlertSwitches.fromLauncher());
        assertFalse(AlertSwitches.allowed("red"));
        assertTrue(AlertSwitches.allowed("goal"));
        assertFalse(AlertSwitches.allowed("goalrush"));
        // The board's own red card: held back, as the launcher's board would hold it back; its own goal still comes.
        b.drop(b.cardDrop("RED CARD", BoardLayer.RED, new String[]{"55'", "Red card", "A. Defender", "home", "e1", ""}));
        idle();
        assertTrue(b.queued().isEmpty());
        assertFalse(b.drop.isOut());
        b.drop(b.goalDrop(1, "A. Striker", "60'"));
        idle();
        assertTrue(b.drop.isOut());
        assertEquals("GOAL", b.drop.showing().chip);
        // What the launcher hands over passed its switches there already: shown.
        idleFor(10_000L);
        assertTrue(BoardInbox.offer(alert("tm-other", "red", "RED CARD", "Eastgate 0–0 Westbury")).getBoolean(BoardAlert.TAKEN));
        idle();
        assertTrue(b.drop.isOut());
        assertEquals("RED CARD", b.drop.showing().chip);
        // Substitutions and the minutes added have no switch of their own: as the launcher's board's own.
        assertTrue(AlertSwitches.allowed(null));
    }

    @Test public void withNoLauncherTheFamilysOwnDefaults() {
        assertNull(AlertSwitches.of(null));
        assertTrue(com.gathin.broadcast.BoardAlert.allowed(null, "goal"));
        assertTrue(com.gathin.broadcast.BoardAlert.allowed(null, "red"));
        assertTrue(com.gathin.broadcast.BoardAlert.allowed(null, "ft"));
        assertTrue(com.gathin.broadcast.BoardAlert.allowed(null, "kickoff"));
        assertTrue(com.gathin.broadcast.BoardAlert.allowed(null, "ht"));
        assertTrue(com.gathin.broadcast.BoardAlert.allowed(null, "var"));
        assertFalse(com.gathin.broadcast.BoardAlert.allowed(null, "goalrush"));
        assertFalse(com.gathin.broadcast.BoardAlert.allowed(null, "table"));
        Bundle b = new Bundle();
        b.putBoolean("goal", false);
        assertFalse(AlertSwitches.of(b).get("goal"));
        assertFalse(com.gathin.broadcast.BoardAlert.allowed(AlertSwitches.of(b), "goal"));
        assertTrue("not answered for: the default", com.gathin.broadcast.BoardAlert.allowed(AlertSwitches.of(b), "red"));
        assertEquals("goal", BoardAlert.kindOfChip("GOAL"));
        assertEquals("red", BoardAlert.kindOfChip("RED CARD"));
        assertEquals("kickoff", BoardAlert.kindOfChip("SECOND HALF"));
        assertEquals("kickoff", BoardAlert.kindOfChip("LINE-UPS"));
        assertNull(BoardAlert.kindOfChip("SUBSTITUTION"));
    }
}
