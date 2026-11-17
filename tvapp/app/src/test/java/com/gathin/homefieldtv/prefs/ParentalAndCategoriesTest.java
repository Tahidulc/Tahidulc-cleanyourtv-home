package com.gathin.homefieldtv.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.gathin.homefieldtv.engine.Channel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Settings → Parental control and Categories: the PIN, what it locks, and which channels the lists show. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ParentalAndCategoriesTest {
    private Context c;

    @Before public void fresh() {
        c = RuntimeEnvironment.getApplication();
        Parental.forget();
        Categories.forget();
    }

    @Test public void thePin() {
        assertFalse(Parental.hasPin(c));
        assertFalse(Parental.valid("123"));
        assertFalse(Parental.valid("12a4"));
        assertFalse(Parental.valid("12345"));
        assertTrue(Parental.valid("0042"));
        assertFalse(Parental.setPin(c, "12"));
        assertFalse(Parental.hasPin(c));
        assertTrue(Parental.setPin(c, "4711"));
        assertTrue(Parental.hasPin(c));
        // Kept only as a salted hash.
        String kept = c.getSharedPreferences(Parental.PREFS, Context.MODE_PRIVATE).getString(Parental.PIN, "");
        assertFalse(kept.contains("4711"));
        assertTrue(kept.indexOf(':') > 0);
        assertTrue(Parental.check(c, "4711"));
        assertFalse(Parental.check(c, "1174"));
        // The same PIN set again hashes differently (a new salt), and still checks.
        Parental.setPin(c, "4711");
        assertNotEquals(kept, c.getSharedPreferences(Parental.PREFS, Context.MODE_PRIVATE).getString(Parental.PIN, ""));
        assertTrue(Parental.check(c, "4711"));
        // Read afresh (a restored backup): the same.
        Parental.forget();
        assertTrue(Parental.hasPin(c));
        assertTrue(Parental.check(c, "4711"));
        Parental.clearPin(c);
        assertFalse(Parental.hasPin(c));
        assertFalse(Parental.check(c, "4711"));
    }

    @Test public void fiveWrongWaitThirtySeconds() {
        Parental.setPin(c, "2580");
        for (int i = 0; i < Parental.TRIES - 1; i++) assertFalse(Parental.check(c, "0000"));
        assertEquals(0, Parental.waitMs());
        assertFalse(Parental.check(c, "0000"));
        assertTrue(Parental.waitMs() > 25_000);
        // Even the right PIN waits.
        assertFalse(Parental.check(c, "2580"));
    }

    @Test public void lockedUntilThePinThenOpenUntilLeft() {
        Channel film = ch("x:1", "Film4", "Movies"), news = ch("x:2", "BBC News", "News"), kids = ch("x:3", "CBeebies", "Kids");
        Parental.lockGroup(c, "Movies", true);
        Parental.lockChannel(c, "x:2", true);
        // Without a PIN nothing is locked.
        assertFalse(Parental.locked(c, film));
        assertFalse(Parental.blocks(c, news));
        Parental.setPin(c, "1357");
        // Setting the PIN opens it, as it was just typed.
        assertTrue(Parental.open());
        assertFalse(Parental.blocks(c, film));
        Parental.relock();
        assertTrue(Parental.blocks(c, film));
        assertTrue(Parental.blocks(c, news));
        assertFalse(Parental.blocks(c, kids));
        Parental.unlock();
        assertFalse(Parental.blocks(c, film));
        Parental.relock();
        assertFalse(Parental.settingsBlocked(c));
        Parental.setLocksSettings(c, true);
        assertTrue(Parental.settingsBlocked(c));
        Parental.lockChannel(c, "x:2", false);
        assertFalse(Parental.blocks(c, news));
    }

    @Test public void lockedAwayOrAsked() {
        Channel film = ch("x:1", "Film4", "Movies"), news = ch("x:2", "BBC News", "News");
        List<Channel> all = Arrays.asList(film, news);
        Parental.setPin(c, "1357");
        Parental.lockGroup(c, "Movies", true);
        Parental.relock();
        // Ask for the PIN: listed, but asked for before it plays.
        assertTrue(Shown.listed(c, film));
        assertSame(all, Shown.of(c, all));
        // Hide them: out of the lists, Favourites and Recent too, until the PIN is entered.
        Parental.setHides(c, true);
        assertFalse(Shown.listed(c, film));
        assertFalse(Shown.kept(c, film));
        assertEquals(Collections.singletonList(news), Shown.of(c, all));
        Parental.unlock();
        assertEquals(all, Shown.of(c, all));
        Parental.relock();
        assertEquals(1, Shown.of(c, all).size());
    }

    @Test public void categoriesHiddenMovedAndNamed() {
        List<String> playlist = Arrays.asList("News", "Sport", "Movies", "Kids", "");
        assertEquals(playlist, Categories.arranged(c, playlist));
        Categories.setHidden(c, "Movies", true);
        assertEquals(Arrays.asList("News", "Sport", "Kids", ""), Categories.arranged(c, playlist));
        assertEquals(playlist, Categories.every(c, playlist));
        // Kids to the top, then Sport down a place: the whole order kept.
        Categories.move(c, playlist, "Kids", 0);
        Categories.move(c, playlist, "Sport", 1);
        assertEquals(Arrays.asList("Kids", "News", "Movies", "Sport", ""), Categories.every(c, playlist));
        assertEquals(Arrays.asList("Kids", "News", "Sport", ""), Categories.arranged(c, playlist));
        Categories.rename(c, "Sport", "  Football  ");
        assertEquals("Football", Categories.label(c, "Sport"));
        assertTrue(Categories.renamed(c, "Sport"));
        assertEquals(Categories.UNNAMED, Categories.label(c, ""));
        assertEquals("News", Categories.label(c, "News"));
        // Read afresh: the same.
        Categories.forget();
        assertEquals(Arrays.asList("Kids", "News", "Sport", ""), Categories.arranged(c, playlist));
        assertEquals("Football", Categories.label(c, "Sport"));
        Categories.rename(c, "Sport", "");
        assertEquals("Sport", Categories.label(c, "Sport"));
        Categories.showAll(c);
        Categories.resetOrder(c);
        assertEquals(playlist, Categories.arranged(c, playlist));
    }

    @Test public void arrangeIsPure() {
        List<String> groups = Arrays.asList("A", "B", "C", "D");
        // Ones the order names first (if still there), then the rest in the playlist's order; hidden ones out.
        assertEquals(Arrays.asList("C", "A", "B"),
                Categories.arrange(groups, Arrays.asList("C", "Gone", "A"), new HashSet<>(Collections.singletonList("D"))));
        assertEquals(Arrays.asList("B", "A", "C"), Categories.moved(Arrays.asList("A", "B", "C"), "B", -1));
        assertEquals(Arrays.asList("A", "C", "B"), Categories.moved(Arrays.asList("A", "B", "C"), "B", 1));
        assertEquals(Arrays.asList("C", "A", "B"), Categories.moved(Arrays.asList("A", "B", "C"), "C", 0));
        assertEquals(Arrays.asList("A", "B", "C"), Categories.moved(Arrays.asList("A", "B", "C"), "C", 1));
        assertEquals(Arrays.asList("A", "B"), Categories.moved(Arrays.asList("A", "B"), "Z", -1));
    }

    @Test public void hiddenCategoriesLeaveTheListsNotFavourites() {
        Channel a = ch("x:1", "One", "News"), b = ch("x:2", "Two", "Adult"), d = ch("x:3", "Three", "News");
        List<Channel> all = Arrays.asList(a, b, d);
        Categories.setHidden(c, "Adult", true);
        assertEquals(Arrays.asList(a, d), Shown.of(c, all));
        assertFalse(Shown.listed(c, b));
        assertTrue(Shown.kept(c, b));
        // Made once for each change.
        assertSame(Shown.of(c, all), Shown.of(c, all));
        Categories.setHidden(c, "Adult", false);
        assertSame(all, Shown.of(c, all));
    }

    @Test public void channelUpAndDownPastLocked() {
        Channel a = ch("x:1", "One", "G"), b = ch("x:2", "Two", "G"), d = ch("x:3", "Three", "G");
        List<Channel> all = Arrays.asList(a, b, d);
        assertEquals(b, Shown.step(all, a, 1, x -> false));
        assertEquals(d, Shown.step(all, a, -1, x -> false));
        assertEquals(d, Shown.step(all, a, 1, x -> x == b));
        assertEquals(a, Shown.step(all, d, 1, x -> false));
        // All the others skipped: stays.
        assertEquals(a, Shown.step(all, a, 1, x -> x != a));
        // Not in the list: the first (or last) there.
        Channel gone = ch("x:9", "Gone", "G");
        assertEquals(a, Shown.step(all, gone, 1, x -> false));
        assertEquals(d, Shown.step(all, gone, -1, x -> false));
        assertNull(Shown.step(Collections.emptyList(), a, 1, x -> false));
        // The mini TV never starts a locked channel by itself.
        Parental.setPin(c, "1111");
        Parental.lockChannel(c, "x:1", true);
        Parental.relock();
        assertEquals(b, Shown.playable(c, a, all));
        assertEquals(d, Shown.playable(c, d, all));
        assertEquals(b, Shown.step(c, all, d, 1));
    }

    private static Channel ch(String key, String name, String group) {
        return new Channel(key, name, group, "", "http://example.invalid/" + key.replace(':', '-'), "", "", "", 0);
    }
}
