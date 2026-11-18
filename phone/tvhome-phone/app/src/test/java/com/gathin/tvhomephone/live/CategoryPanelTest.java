package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Live TV's categories down the side (CategoryPanel): the list in the app's order (favourites first, hidden ones out),
 * the button's words, when it docks beside the guide, the swipe that puts it away, and the panel itself: it slides in
 * and out, a tap picks a category, a hold asks for its menu, the scrim and a swipe to the left close it.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w411dp-h891dp-xhdpi")
public class CategoryPanelTest {
    private static final List<String> PLAYLIST = Arrays.asList("UK: Entertainment", "UK: Sports", "UK: News", "Radio", "4K UHD");

    private static List<String[]> items() {
        return CategoryPanel.items("★ Favourites", "All", "Recent", "Sport", PLAYLIST, new HashSet<>(Collections.singletonList("Radio")),
                Arrays.asList("4K UHD", "UK: Sports"));
    }

    @Test public void theCategoriesInTheAppsOrder() {
        List<String[]> it = items();
        List<String> keys = new ArrayList<>();
        for (String[] x : it) keys.add(x[1]);
        // The guide's own first, then the favourites in the order they were made, then the rest; Radio hidden.
        assertEquals(Arrays.asList(Library.FAVOURITES, Library.ALL, Library.RECENT, SportGuide.GROUP, "4K UHD", "UK: Sports",
                "UK: Entertainment", "UK: News"), keys);
        assertEquals("★ 4K UHD", it.get(4)[0]);
        assertEquals("UK: Entertainment", it.get(6)[0]);
        assertEquals(2, it.get(0).length);
        assertEquals("own", it.get(6)[2]);
    }

    @Test public void theButtonSaysTheCategoryShowing() {
        List<String[]> it = items();
        assertEquals("All", CategoryPanel.words(it, Library.ALL));
        assertEquals("Sport", CategoryPanel.words(it, SportGuide.GROUP));
        // A playlist's own with its country's flag, as the chips had it.
        assertEquals(GuideLook.chip("UK: News", "UK: News"), CategoryPanel.words(it, "UK: News"));
        assertEquals("Gone", CategoryPanel.words(it, "Gone"));
        assertEquals("", CategoryPanel.words(it, null));
    }

    @Test public void itDocksOnlyOnATabletOnItsSideWithRoom() {
        assertTrue(CategoryPanel.docks(true, true, 1280, 380, 560));
        assertFalse(CategoryPanel.docks(true, false, 800, 380, 560));
        assertFalse(CategoryPanel.docks(false, true, 891, 300, 560));
        // A smaller tablet on its side: the guide would be too narrow beside it.
        assertFalse(CategoryPanel.docks(true, true, 1024, 380, 560));
    }

    @Test public void aSwipeToTheLeftPutsItAway() {
        // Past the slop, to the left, more across than down: the panel's own.
        assertTrue(CategoryPanel.swipes(-30, 5, 16));
        assertFalse(CategoryPanel.swipes(-10, 0, 16));
        assertFalse(CategoryPanel.swipes(30, 0, 16));
        assertFalse(CategoryPanel.swipes(-30, 40, 16));
        // Let go: a flick either way goes its way; else a third of the way across closes it.
        assertTrue(CategoryPanel.shuts(-20, -900, 600, 100));
        assertFalse(CategoryPanel.shuts(-400, 900, 600, 100));
        assertTrue(CategoryPanel.shuts(-250, 0, 600, 100));
        assertFalse(CategoryPanel.shuts(-150, 0, 600, 100));
    }

    @Test public void itOpensAtItsTopWhileTheOneShowingIsInSight() {
        // Rows of 100 in 1000: the eighth (index 7) shows from the top; the eighth of a long list further down doesn't.
        assertEquals(0, CategoryPanel.firstShown(7, 100, 1000));
        assertEquals(0, CategoryPanel.firstShown(-1, 100, 1000));
        assertEquals(12, CategoryPanel.firstShown(12, 100, 1000));
        assertTrue(CategoryPanel.inSight(8, 100, 1000));
        assertFalse(CategoryPanel.inSight(9, 100, 1000));
    }

    @Test public void itTakesMostOfANarrowScreenAndNoMore() {
        assertEquals(640, CategoryPanel.sheetWidth(1000, 640));
        assertEquals(420, CategoryPanel.sheetWidth(500, 640));
    }

    // ---- the panel itself ---------------------------------------------------------------------------------------

    private static void idle(int ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static final class Told implements CategoryPanel.Listener {
        String picked, held;
        int shut, manage;

        @Override public void onPick(String key) { picked = key; }

        @Override public void onHold(View row, String key) { held = key; }

        @Override public void onManage() { manage++; }

        @Override public void onShut() { shut++; }
    }

    private static CategoryPanel panel(Activity a, Told told) {
        FrameLayout root = new FrameLayout(a);
        CategoryPanel p = new CategoryPanel(a, false);
        p.setListener(told);
        p.set(items(), "UK: Entertainment");
        root.addView(p, new FrameLayout.LayoutParams(-1, -1));
        a.setContentView(root);
        layout(root);
        return p;
    }

    private static void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(822, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1782, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 822, 1782);
    }

    private static RecyclerView list(View v) {
        if (v instanceof RecyclerView) return (RecyclerView) v;
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                RecyclerView r = list(((ViewGroup) v).getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    /** The row whose words start with these (after any flag). */
    private static View row(RecyclerView r, String words) {
        for (int i = 0; i < r.getChildCount(); i++) {
            View v = r.getChildAt(i);
            CharSequence d = v.getContentDescription();
            if (d != null && d.toString().startsWith(words)) return v;
        }
        return null;
    }

    @Test public void itSlidesInAndATapPicksAndPutsItAway() {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        Told told = new Told();
        CategoryPanel p = panel(a, told);
        assertEquals(View.GONE, p.getVisibility());
        p.open(true);
        assertTrue(p.isOpen());
        assertEquals(View.VISIBLE, p.getVisibility());
        // Mid-slide it is still coming in from the left; it is all the way in once the 250ms are up.
        idle(100);
        layout(a.findViewById(android.R.id.content));
        idle((int) CategoryPanel.SLIDE_MS + 50);
        assertEquals(0f, p.sheet().getTranslationX(), 0.5f);
        RecyclerView r = list(p);
        layout(a.findViewById(android.R.id.content));
        View news = row(r, "UK: News");
        assertTrue("a row for UK: News", news != null);
        // The one showing says so; the hidden one is not there.
        assertTrue(row(r, "UK: Entertainment, showing") != null);
        assertNull(row(r, "Radio"));
        news.performClick();
        assertEquals("UK: News", told.picked);
    }

    @Test public void theScrimAndBackPutItAway() {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        Told told = new Told();
        CategoryPanel p = panel(a, told);
        p.open(false);
        p.getChildAt(0).performClick();
        assertFalse(p.isOpen());
        assertEquals(1, told.shut);
        idle((int) CategoryPanel.SLIDE_MS + 50);
        assertEquals(View.GONE, p.getVisibility());
        // Shut already: closing again tells nobody.
        p.close(true);
        assertEquals(1, told.shut);
    }

    @Test public void aHoldOnAPlaylistsOwnAsksForItsMenu() {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        Told told = new Told();
        CategoryPanel p = panel(a, told);
        p.open(false);
        layout(a.findViewById(android.R.id.content));
        RecyclerView r = list(p);
        layout(a.findViewById(android.R.id.content));
        View sports = row(r, "★ UK: Sports");
        assertTrue(sports != null);
        sports.performLongClick();
        assertEquals("UK: Sports", told.held);
        // The guide's own have no menu.
        View all = row(r, "All");
        assertTrue(all != null);
        assertFalse(all.performLongClick());
        assertNull(told.picked);
    }

    @Test public void aSwipeLeftOnThePanelClosesIt() {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        Told told = new Told();
        CategoryPanel p = panel(a, told);
        p.open(false);
        layout(a.findViewById(android.R.id.content));
        long t = SystemClock.uptimeMillis();
        float y = 600;
        p.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 500, y, 0));
        for (int i = 1; i <= 10; i++) p.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 16, MotionEvent.ACTION_MOVE, 500 - i * 40, y, 0));
        assertTrue("follows the finger", p.sheet().getTranslationX() < 0);
        p.dispatchTouchEvent(MotionEvent.obtain(t, t + 200, MotionEvent.ACTION_UP, 100, y, 0));
        assertFalse(p.isOpen());
        assertEquals(1, told.shut);
        assertNull(told.picked);
    }

    @Test public void aShortDragLeavesItOpen() {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        Told told = new Told();
        CategoryPanel p = panel(a, told);
        p.open(false);
        layout(a.findViewById(android.R.id.content));
        long t = SystemClock.uptimeMillis();
        p.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 500, 600, 0));
        p.dispatchTouchEvent(MotionEvent.obtain(t, t + 300, MotionEvent.ACTION_MOVE, 460, 600, 0));
        p.dispatchTouchEvent(MotionEvent.obtain(t, t + 900, MotionEvent.ACTION_MOVE, 440, 600, 0));
        p.dispatchTouchEvent(MotionEvent.obtain(t, t + 1500, MotionEvent.ACTION_UP, 440, 600, 0));
        assertTrue(p.isOpen());
        assertEquals(0, told.shut);
        idle((int) CategoryPanel.SLIDE_MS + 50);
        assertEquals(0f, p.sheet().getTranslationX(), 0.5f);
    }

    @Test public void dockedItHasNoScrimAndTucksAway() {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        Told told = new Told();
        FrameLayout root = new FrameLayout(a);
        CategoryPanel p = new CategoryPanel(a, true);
        p.setListener(told);
        p.set(items(), Library.ALL);
        root.addView(p, new FrameLayout.LayoutParams(400, -1));
        a.setContentView(root);
        layout(root);
        assertTrue(p.docked());
        assertEquals(View.GONE, p.getChildAt(0).getVisibility());
        p.open(false);
        assertTrue(p.isOpen());
        p.close(true);
        assertFalse(p.isOpen());
        assertEquals(1, told.shut);
    }
}
