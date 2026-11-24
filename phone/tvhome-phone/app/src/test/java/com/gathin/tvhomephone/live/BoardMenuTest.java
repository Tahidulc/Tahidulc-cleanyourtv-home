package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Game;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The scoreboard's and the channel logo's pop-up menus (the family: "make the pop up menu easier to use and have all the
 * options for it in the pop up menu when I click on the scoreboard and channel logo"): a real tap on the board (over the
 * player's touch layer) opens the board's menu, one on the logo's cover the logo's; a hold still drags, a double tap
 * still goes to Move and resize; a switch in a menu changes the setting and the board or the cover at once; every
 * option is in one of the menus; in the small window nothing opens; the screen reader's click reaches them. Invented
 * channel and teams.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w411dp-h891dp-xhdpi")
public class BoardMenuTest {
    private Activity act;
    private FrameLayout root;
    private ScoreOverlay o;
    private final Channel ch = new Channel("test.northbridge", "Northbridge Sport", "Sport", "", "http://example.invalid/n", "", null, null, 0);
    private Game game;
    private int matches;

    @Before public void setUp() {
        ActivityController<Activity> ctl = Robolectric.buildActivity(Activity.class);
        ctl.get().setTheme(R.style.Theme_TvHome);
        act = ctl.setup().get();
        root = new FrameLayout(act);
        act.setContentView(root, new ViewGroup.LayoutParams(-1, -1));
        Video video = new Video(act);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));
        // The player's touch layer over the picture: it takes every touch that reaches it.
        View touch = new View(act);
        touch.setTag(Gestures.LAYER);
        touch.setClickable(true);
        root.addView(touch, new FrameLayout.LayoutParams(-1, -1));
        root.addView(new View(act), new FrameLayout.LayoutParams(-1, -1));   // the subtitles
        game = new Game();
        game.id = "test-1";
        game.home = "NBR";
        game.away = "EST";
        game.homeName = "Northbridge";
        game.awayName = "Easton Vale";
        game.homeScore = 2;
        game.awayScore = 1;
        game.state = Game.LIVE;
        game.clock = 67 * 60;
        game.displayClock = "67'";
        game.period = 2;
        game.kickoff = System.currentTimeMillis() - 80 * 60_000L;
        // The logo's cover put by hand on this channel, so it shows without a logo found on the picture.
        BoardPlace p = new BoardPlace();
        p.lw = 0.13f;
        p.lh = 0.075f;
        p.lx = 0.83f;
        p.ly = 0.05f;
        BoardPlace.put(act, ch.key, p);
        ScoreBoard.setOn(act, true);
        o = new ScoreOverlay(act, root, video, new ScoreOverlay.Host() {
            @Override public Channel current() { return ch; }

            @Override public Game game() { return game; }

            @Override public void openMatch() { matches++; }

            @Override public void play(Channel c) {}

            @Override public void mute(boolean on) {}
        });
        o.start();
        o.game(game);
        // The board reads its game off the main thread (the made-up one has nowhere to be read from: as it is), then
        // shows it on its next tick.
        for (int i = 0; i < 100 && o.boardView().getVisibility() != View.VISIBLE; i++) {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            settle(260);
        }
        settle(800);
    }

    private void settle(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    /** The middle of a view on the player. */
    private float[] middle(View v) {
        int[] p = new int[2], r = new int[2];
        v.getLocationInWindow(p);
        root.getLocationInWindow(r);
        return new float[]{p[0] - r[0] + v.getWidth() / 2f, p[1] - r[1] + v.getHeight() / 2f};
    }

    private void touch(int action, float[] at, long down) {
        MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, at[0], at[1], 0);
        root.dispatchTouchEvent(e);
        e.recycle();
    }

    /** A finger's tap at a view's middle, the double tap's wait let go by. */
    private void tap(View v) {
        float[] at = middle(v);
        long down = SystemClock.uptimeMillis();
        touch(MotionEvent.ACTION_DOWN, at, down);
        settle(60);
        touch(MotionEvent.ACTION_UP, at, down);
        settle(450);
    }

    private void boardUp() {
        View b = o.boardView();
        assertEquals("the board is up over the game", View.VISIBLE, b.getVisibility());
        assertTrue(b.getWidth() > 0 && b.isShown());
    }

    private void coverUp() {
        View cv = o.coverView();
        assertEquals("the logo's cover is up", View.VISIBLE, cv.getVisibility());
        assertTrue(cv.getWidth() > 0 && cv.isShown());
    }

    // ---- a tap, a hold, a double tap ----------------------------------------------------------------------------

    @Test public void aTapOnTheBoardOpensTheBoardsMenuWithTheMatchCentreFirst() {
        boardUp();
        tap(o.boardView());
        assertEquals(OverlayMenu.BOARD, o.menuOpen());
        assertEquals("a tap no longer goes straight to the match centre", 0, matches);
        // Its first button: Match centre, which opens it (and puts the menu away).
        TextView first = firstButton(o.menu());
        assertEquals(act.getString(R.string.live3_match_centre), first.getText().toString());
        first.performClick();
        assertEquals(1, matches);
        assertEquals(0, o.menuOpen());
    }

    @Test public void aTapOnTheLogosCoverOpensTheLogosMenu() {
        coverUp();
        tap(o.coverView());
        assertEquals(OverlayMenu.LOGO, o.menuOpen());
    }

    @Test public void aTapOnThePictureOutsideItPutsItAwayAndOneOnTheLogoSwitchesToItsMenu() {
        tap(o.boardView());
        assertEquals(OverlayMenu.BOARD, o.menuOpen());
        // The logo's cover, still in sight beside the menu: its menu instead.
        tap(o.coverView());
        assertEquals(OverlayMenu.LOGO, o.menuOpen());
        // The picture away from both: put away.
        float[] at = {root.getWidth() / 2f, root.getHeight() * 0.3f};
        long down = SystemClock.uptimeMillis();
        touch(MotionEvent.ACTION_DOWN, at, down);
        touch(MotionEvent.ACTION_UP, at, down);
        settle(300);
        assertEquals(0, o.menuOpen());
    }

    @Test public void backPutsItAway() {
        tap(o.boardView());
        assertTrue(o.closeMenu());
        assertEquals(0, o.menuOpen());
        assertFalse("nothing more to put away", o.closeMenu());
    }

    @Test public void aHoldStillDragsTheBoard() {
        boardUp();
        View b = o.boardView();
        float[] at = middle(b);
        long down = SystemClock.uptimeMillis();
        touch(MotionEvent.ACTION_DOWN, at, down);
        settle(700);   // held
        touch(MotionEvent.ACTION_MOVE, new float[]{at[0] + 120, at[1] + 160}, down);
        touch(MotionEvent.ACTION_UP, new float[]{at[0] + 120, at[1] + 160}, down);
        settle(100);
        assertEquals("no menu for a hold", 0, o.menuOpen());
        assertFalse("nor Move and resize", o.editing());
        BoardPlace kept = BoardPlace.get(act, ch.key);
        assertTrue("dragged and kept for the channel", kept.board());
        assertTrue(kept.by > 0.05f);
    }

    @Test public void aDoubleTapStillGoesToMoveAndResize() {
        boardUp();
        float[] at = middle(o.boardView());
        long d1 = SystemClock.uptimeMillis();
        touch(MotionEvent.ACTION_DOWN, at, d1);
        settle(50);
        touch(MotionEvent.ACTION_UP, at, d1);
        settle(80);
        long d2 = SystemClock.uptimeMillis();
        touch(MotionEvent.ACTION_DOWN, at, d2);
        settle(50);
        touch(MotionEvent.ACTION_UP, at, d2);
        settle(400);
        assertTrue(o.editing());
        assertEquals("no menu over Move and resize", 0, o.menuOpen());
        o.endAdjust(true);
    }

    @Test public void aDoubleTapOnTheLogoToo() {
        coverUp();
        float[] at = middle(o.coverView());
        long d1 = SystemClock.uptimeMillis();
        touch(MotionEvent.ACTION_DOWN, at, d1);
        settle(50);
        touch(MotionEvent.ACTION_UP, at, d1);
        settle(80);
        long d2 = SystemClock.uptimeMillis();
        touch(MotionEvent.ACTION_DOWN, at, d2);
        settle(50);
        touch(MotionEvent.ACTION_UP, at, d2);
        settle(400);
        assertTrue(o.editing());
        o.endAdjust(true);
    }

    // ---- the small window, the screen reader ------------------------------------------------------------------

    @Test public void inTheSmallWindowNothingOpens() {
        o.boardMenu();
        assertEquals(OverlayMenu.BOARD, o.menuOpen());
        o.pip(true);
        assertEquals("the small window puts it away", 0, o.menuOpen());
        settle(200);
        o.boardMenu();
        o.logoMenu();
        o.boardView().performClick();
        assertEquals(0, o.menuOpen());
        o.pip(false);
    }

    @Test public void theScreenReadersClickReachesTheMenusAndItsLongPressMoveAndResize() {
        boardUp();
        assertTrue(o.boardView().performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null));
        assertEquals(OverlayMenu.BOARD, o.menuOpen());
        o.closeMenu();
        coverUp();
        assertTrue(o.coverView().performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null));
        assertEquals(OverlayMenu.LOGO, o.menuOpen());
        o.closeMenu();
        // Named for what they do.
        AccessibilityNodeInfo info = o.boardView().createAccessibilityNodeInfo();
        boolean named = false;
        for (AccessibilityNodeInfo.AccessibilityAction x : info.getActionList()) {
            if (x.getId() == AccessibilityNodeInfo.ACTION_CLICK && x.getLabel() != null && x.getLabel().toString().contains("menu")) named = true;
        }
        assertTrue(named);
        assertTrue(o.coverView().performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null));
        assertTrue(o.editing());
        o.endAdjust(true);
    }

    @Test public void theTennisBoardsTapOpensTheScoreboardsMenuToo() {
        View s = o.sportView();
        assertTrue(s.isClickable());
        s.performClick();
        assertEquals(OverlayMenu.BOARD, o.menuOpen());
    }

    // ---- what the menus hold, and changes made at once -----------------------------------------------------------

    @Test public void aSwitchInTheLogosMenuChangesItsSettingAndTheCoverAtOnce() {
        coverUp();
        o.logoMenu();
        MaterialSwitch sw = switchNamed(o.menu(), "Cover the channel's logo");
        assertTrue(sw.isChecked());
        sw.setChecked(false);
        assertEquals(LogoCover.Look.OFF, ChannelOverlays.look(act, ch.key));
        assertEquals("the cover off the picture at once", View.GONE, o.coverView().getVisibility());
        // Back on: the look it had.
        MaterialSwitch again = switchNamed(o.menu(), "Cover the channel's logo");
        again.setChecked(true);
        assertEquals(View.VISIBLE, o.coverView().getVisibility());
    }

    @Test public void aLookPickedInTheBoardsMenuIsOnTheBoardAtOnce() {
        boardUp();
        o.boardMenu();
        View tile = viewNamed(o.menu().panel, OverlayBug.Style.GLASS.label);
        assertNotNull(tile);
        tile.performClick();
        assertEquals(OverlayBug.Style.GLASS, ScoreBoard.style(act));
        assertEquals(OverlayBug.Style.GLASS, ((OverlayBug) o.boardView()).style());
        // A switch: the scoreboard's swish off, the setting with it.
        MaterialSwitch sw = switchNamed(o.menu(), "A swish as things slide out");
        boolean was = sw.isChecked();
        sw.setChecked(!was);
        assertEquals(!was, PlayerIntro.swishOn(act));
    }

    @Test public void theSizeStepperChangesThisChannelsSizeAtOnce() {
        boardUp();
        float before = o.sizeHere();
        o.boardSize(1);
        assertTrue(o.sizeHere() > before);
        assertEquals("kept for this channel", Float.toString(o.sizeHere()), ChannelOverlays.opt(act, ch.key, "size"));
        o.boardSize(-1);
        o.boardSize(-1);
        assertTrue(o.sizeHere() < before);
    }

    @Test public void everyScoreboardOptionIsInItsMenu() {
        o.boardMenu();
        List<String> words = words(o.menu().panel);
        String[] want = {
                // the match centre, and the board itself
                "Match centre", "Scoreboard on the picture", "Show the board itself", "Over matches", "Up by hand",
                // its look, My scoreboards, the editor, Ask AI
                "Its look", "My scoreboards", "Scoreboard editor", "Ask AI's key",
                // timing
                "Behind the live scores", "It's just happened", "Hold this game's alerts",
                // pop-ups and alerts
                "Goals and football alerts", "F1 and other sport alerts", "Breaking news alerts", "Kick-off pop-up",
                "Line-ups over the picture", "Line-ups beside the board", "VAR explained", "Penalty card", "Referee at kick-off",
                "Substitutions", "Penalty shootout tracker", "Milestones", "Shot map at half and full time",
                "The scorer's shirt in 3D", "Minutes added stay out", "Sponsor strap under alerts", "FotMob stats and pictures",
                act.getString(R.string.lab_stats_title),
                // sounds
                "A swish as things slide out", "A swish on a new score", "Goal sound",
                // the channel's own scoreboard
                "Cover it", "Away in its replays", act.getString(R.string.live_bug_pop_outs_short), "Read its clock and score",
                // ad breaks
                act.getString(R.string.live58_ad_break), "Show it now", "Show it as at half time",
                // place and size
                "Move and resize", "Size", "Height", "Width", "Keep the scoreboard and logo still", act.getString(R.string.live5_burn),
                "Reset to the standard place",
                // this channel, tennis and boxing, over other apps
                "The scoreboard and logo here", act.getString(R.string.live81_try_title), act.getString(R.string.live4_float_start),
                "Other apps' picture behind by"};
        for (String w : want) assertTrue("in the scoreboard's menu: " + w, words.contains(w));
        // Each section to jump to along its top.
        for (String s : new String[]{"Scoreboard", "Look", "Timing", "Pop-ups and alerts", "Sounds", "Place and size", "This channel"}) {
            assertTrue("a pill for " + s, words.contains(s));
        }
        // Rows saying where they hold.
        assertTrue(words.contains("Every channel"));
        assertTrue(words.contains("Just on Northbridge Sport"));
    }

    @Test public void everyLogoOptionIsInItsMenu() {
        o.logoMenu();
        List<String> words = words(o.menu().panel);
        String[] want = {"Cover the channel's logo", "Its look", "Use this look on every channel", act.getString(R.string.live28_styles),
                "On the Sports wordmark", "A general channel: no SPORTS", "See-through", act.getString(R.string.live20_logo_tag), "LIVE bar",
                "Competition logo beside it", act.getString(R.string.live83_next), act.getString(R.string.live83_prayer),
                act.getString(R.string.live83_clock), act.getString(R.string.live83_marks), "Out in the ad breaks", "Pick or make its logo",
                "Move and resize", "Size", "Height", "Width", "Keep the scoreboard and logo still", act.getString(R.string.live5_burn),
                "Reset to the standard place", "The scoreboard and logo here", "The logo alone, now"};
        for (String w : want) assertTrue("in the logo's menu: " + w, words.contains(w));
        // Its looks drawn to pick from (Off is the switch's), "as every channel" first.
        assertNotNull(viewNamed(o.menu().panel, LogoCover.Look.SIGNATURE.label));
        assertTrue(words.contains("Every channel's"));
    }

    @Test public void rowsAreBigEnoughForAFinger() {
        o.logoMenu();
        settle(50);
        root.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getWidth(), root.getHeight());
        float d = act.getResources().getDisplayMetrics().density;
        MaterialSwitch sw = switchNamed(o.menu(), "LIVE bar");
        View row = (View) sw.getParent();
        assertTrue("a switch's row at least 48 dp", row.getHeight() >= 48 * d);
    }

    // ---- where it goes ------------------------------------------------------------------------------------------

    @Test public void alongTheFootUprightDownTheSideOnItsSideOrOnATablet() {
        assertFalse(OverlayMenu.side(411, 891, false));
        assertTrue(OverlayMenu.side(891, 411, false));
        assertTrue(OverlayMenu.side(800, 1280, true));
        // Away from the one tapped: the board on the left, the panel on the right; the logo on the right, on the left.
        assertTrue(OverlayMenu.onRight(100, 1280));
        assertFalse(OverlayMenu.onRight(1100, 1280));
        // 380 dp, never more than 46% of the width, never under 300 dp.
        assertEquals(380, OverlayMenu.sideWidth(1280, 1f));
        assertEquals(380, OverlayMenu.sideWidth(891, 1f));
        assertEquals(Math.round(700 * 0.46f), OverlayMenu.sideWidth(700, 1f));
        assertEquals(300, OverlayMenu.sideWidth(600, 1f));
        assertEquals(760, OverlayMenu.sideWidth(1782, 2f));
        // Upright: never over the board (its foot at 400 of 891), between 40% and 62% of the height.
        assertEquals(891 - 400 - 12, OverlayMenu.sheetMost(891, 400, 1f));
        assertEquals(Math.round(891 * 0.62f), OverlayMenu.sheetMost(891, 100, 1f));
        assertEquals(Math.round(891 * 0.4f), OverlayMenu.sheetMost(891, 700, 1f));
    }

    @Test public void uprightItLiesAlongTheFootLeavingTheBoardInSight() {
        boardUp();
        tap(o.boardView());
        settle(400);
        OverlayMenu m = o.menu();
        assertTrue(m.sheet());
        int[] b = new int[2], p = new int[2];
        o.boardView().getLocationInWindow(b);
        m.panel.getLocationInWindow(p);
        assertTrue("the board above the menu", b[1] + o.boardView().getHeight() <= p[1]);
    }

    @Test public void theNearestNamedSize() {
        assertEquals(0, ScoreOverlay.sizeIndex(0.7f));
        assertEquals(1, ScoreOverlay.sizeIndex(1f));
        assertEquals(2, ScoreOverlay.sizeIndex(1.2f));
        assertEquals(3, ScoreOverlay.sizeIndex(2f));
    }

    // ---- finding things in a menu ----------------------------------------------------------------------------------

    private static TextView firstButton(OverlayMenu m) {
        List<TextView> all = new ArrayList<>();
        texts(m.panel, all);
        for (TextView t : all) if (t.isClickable()) return t;
        throw new AssertionError("no button");
    }

    private static void texts(View v, List<TextView> out) {
        if (v instanceof TextView && !(v instanceof MaterialSwitch)) out.add((TextView) v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) texts(g.getChildAt(i), out);
        }
    }

    private static List<String> words(View v) {
        List<TextView> all = new ArrayList<>();
        texts(v, all);
        List<String> out = new ArrayList<>();
        for (TextView t : all) out.add(t.getText().toString());
        return out;
    }

    private static MaterialSwitch switchNamed(OverlayMenu m, String name) {
        MaterialSwitch[] got = {null};
        find(m.panel, v -> {
            if (v instanceof MaterialSwitch && name.contentEquals(v.getContentDescription())) got[0] = (MaterialSwitch) v;
        });
        assertNotNull("a switch for " + name, got[0]);
        return got[0];
    }

    private static View viewNamed(View root, String name) {
        View[] got = {null};
        find(root, v -> {
            if (got[0] == null && v.isClickable() && v.getContentDescription() != null && name.contentEquals(v.getContentDescription())) got[0] = v;
        });
        return got[0];
    }

    private static void find(View v, java.util.function.Consumer<View> each) {
        each.accept(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) find(g.getChildAt(i), each);
        }
    }
}
