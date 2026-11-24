package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Edit dock's order: moves, removals, additions, no repeats, apps uninstalled and installed again (invented apps). */
public class DockOrderTest {
    private static final List<String> THEME = Arrays.asList("tv.bluefinch.live", "tv.pebble.clips", "tv.kestrel.films");

    private static Predicate<String> installed(String... pkgs) {
        Set<String> s = new HashSet<>(Arrays.asList(pkgs));
        return s::contains;
    }

    @Test public void defaultsAreTheThemesAppsThenAllAppsThenTheSections() {
        DockOrder o = DockOrder.defaults(Arrays.asList("tv.bluefinch.live", " ", "tv.bluefinch.live", "tv.pebble.clips", null));
        assertEquals(Arrays.asList("tv.bluefinch.live", "tv.pebble.clips", DockOrder.ALL), o.apps);
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 6, 5), o.sections);
        assertTrue(o.isDefault(Arrays.asList("tv.bluefinch.live", "tv.pebble.clips")));
    }

    @Test public void movesStayInsideTheirOwnPart() {
        DockOrder o = DockOrder.defaults(THEME);
        assertEquals(1, o.moveApp(0, 1));
        assertEquals(Arrays.asList("tv.pebble.clips", "tv.bluefinch.live", "tv.kestrel.films", DockOrder.ALL), o.apps);
        // At either end it stays put: an app never crosses the rule into the sections.
        assertEquals(0, o.moveApp(0, -1));
        assertEquals(3, o.moveApp(3, 1));
        assertEquals(Arrays.asList("tv.pebble.clips", "tv.bluefinch.live", "tv.kestrel.films", DockOrder.ALL), o.apps);
        // All apps moves like any other.
        assertEquals(2, o.moveApp(3, -1));
        assertEquals(DockOrder.ALL, o.apps.get(2));
        // Sections: Kids to the front, one place at a time.
        int at = o.sections.indexOf(DockOrder.KIDS);
        while (at > 0) at = o.moveSection(at, -1);
        assertEquals(Arrays.asList(6, 0, 1, 2, 3, 4, 5), o.sections);
    }

    @Test public void removeTakesAnyAppButAllApps() {
        DockOrder o = DockOrder.defaults(THEME);
        assertTrue(o.removeApp(1));
        assertEquals(Arrays.asList("tv.bluefinch.live", "tv.kestrel.films", DockOrder.ALL), o.apps);
        assertFalse(o.appRemovable(2));
        assertFalse(o.removeApp(2));
        assertFalse(o.removeApp(9));
        assertEquals(3, o.apps.size());
    }

    @Test public void tvGuideAndAppsStayOnTheDockTheOtherSectionsComeAndGo() {
        DockOrder o = DockOrder.defaults(THEME);
        assertFalse(o.removeSection(o.sections.indexOf(DockOrder.GUIDE)));
        assertFalse(o.removeSection(o.sections.indexOf(DockOrder.APPS)));
        assertTrue(o.removeSection(o.sections.indexOf(DockOrder.KIDS)));
        assertTrue(o.removeSection(o.sections.indexOf(2)));
        assertEquals(Arrays.asList(0, 1, 3, 4, 5), o.sections);
        assertEquals(Arrays.asList(2, 6), o.offSections());
        // Back at its usual place among those there, even after the others moved.
        o.moveSection(o.sections.indexOf(DockOrder.APPS), -1);
        assertEquals(Arrays.asList(0, 1, 3, 5, 4), o.sections);
        assertEquals(2, o.addSection(2));
        assertEquals(Arrays.asList(0, 1, 2, 3, 5, 4), o.sections);
        assertEquals(-1, o.addSection(2));
        assertEquals(-1, o.addSection(7));
    }

    @Test public void addPutsTheAppAfterTheFocusedOneAndNeverTwice() {
        DockOrder o = DockOrder.defaults(THEME);
        assertEquals(1, o.addApp("tv.otter.music", 0));
        assertEquals(Arrays.asList("tv.bluefinch.live", "tv.otter.music", "tv.pebble.clips", "tv.kestrel.films", DockOrder.ALL), o.apps);
        // Already there: no repeat, nothing changes.
        assertEquals(-1, o.addApp("tv.otter.music", 3));
        assertEquals(-1, o.addApp("tv.bluefinch.live", 3));
        assertEquals(-1, o.addApp(" ", 0));
        assertEquals(-1, o.addApp(DockOrder.ALL, 0));
        assertEquals(5, o.apps.size());
        // With no app chosen (past the end), before All apps when it is last.
        assertEquals(4, o.addApp("tv.heron.news", 99));
        assertEquals(DockOrder.ALL, o.apps.get(o.apps.size() - 1));
        // All apps moved away from the end: the new app goes last.
        o.moveApp(o.apps.indexOf(DockOrder.ALL), -1);
        int wren = o.addApp("tv.wren.radio", 99);
        assertEquals(o.apps.size() - 1, wren);
    }

    @Test public void theDockStopsTakingAppsWhenTheScreenIsFull() {
        int most = DockOrder.maxApps(7);
        assertTrue(DockOrder.width(most, 7) <= 1920 - 2 * DockOrder.MARGIN);
        assertTrue(DockOrder.width(most + 1, 7) > 1920 - 2 * DockOrder.MARGIN || most == DockOrder.MOST_APPS);
        // Today's dock (six apps and All apps, seven sections) has room for more.
        assertTrue(most > 7);
        // Fewer sections leave room for more apps.
        assertTrue(DockOrder.maxApps(2) > most);
        DockOrder o = DockOrder.defaults(Collections.<String>emptyList());
        for (int i = 0; o.roomForApp(); i++) assertTrue(o.addApp("tv.invented.app" + i, 99) >= 0);
        assertEquals(most, o.apps.size());
        assertEquals(-1, o.addApp("tv.one.toomany", 0));
    }

    @Test public void anUninstalledAppDropsOutAndComesBackInItsPlace() {
        DockOrder saved = DockOrder.defaults(THEME);
        // tv.pebble.clips is uninstalled: the dock shows the rest, in order.
        Predicate<String> now = installed("tv.bluefinch.live", "tv.kestrel.films", "tv.otter.music");
        List<String> shown = saved.shown(now);
        assertEquals(Arrays.asList("tv.bluefinch.live", "tv.kestrel.films", DockOrder.ALL), shown);
        // The family edits what they see: Kestrel first, Otter added after it.
        DockOrder work = new DockOrder(shown, saved.sections);
        work.moveApp(1, -1);
        work.addApp("tv.otter.music", 0);
        DockOrder kept = DockOrder.keepMissing(work, saved, now);
        // The missing app keeps its place after the one it followed (Bluefinch).
        assertEquals(Arrays.asList("tv.kestrel.films", "tv.otter.music", "tv.bluefinch.live", "tv.pebble.clips", DockOrder.ALL), kept.apps);
        // Installed again, it is back on the dock where it was.
        assertEquals(kept.apps, kept.shown(installed("tv.bluefinch.live", "tv.kestrel.films", "tv.otter.music", "tv.pebble.clips")));
    }

    @Test public void aMissingFirstAppStaysFirstAndRemovedAppsLeave() {
        DockOrder saved = DockOrder.defaults(THEME);
        Predicate<String> now = installed("tv.pebble.clips", "tv.kestrel.films");
        DockOrder work = new DockOrder(saved.shown(now), saved.sections);
        assertTrue(work.removeApp(work.apps.indexOf("tv.kestrel.films")));
        DockOrder kept = DockOrder.keepMissing(work, saved, now);
        assertEquals(Arrays.asList("tv.bluefinch.live", "tv.pebble.clips", DockOrder.ALL), kept.apps);
    }

    @Test public void keptAsTextAndReadBack() {
        DockOrder o = DockOrder.defaults(THEME);
        o.moveApp(2, -1);
        o.removeSection(o.sections.indexOf(DockOrder.KIDS));
        DockOrder back = DockOrder.decode(DockOrder.encode(o));
        assertNotNull(back);
        assertEquals(o, back);
    }

    @Test public void aDamagedOrderIsMendedOrIgnored() {
        assertNull(DockOrder.decode(null));
        assertNull(DockOrder.decode(""));
        assertNull(DockOrder.decode("tvhome-order 1\ndock a\n"));
        // Repeats dropped, All apps added, unknown sections and words dropped, TV Guide and Apps put back.
        DockOrder o = DockOrder.decode("tvhome-dock 1\napps tv.a,tv.b,tv.a,*odd,,tv.c\nsections 3,3,9,x,6\n");
        assertNotNull(o);
        assertEquals(Arrays.asList("tv.a", "tv.b", "tv.c", DockOrder.ALL), o.apps);
        assertEquals(Arrays.asList(0, 3, 6, 5), o.sections);
    }

    @Test public void packagesLeaveOutAllApps() {
        DockOrder o = DockOrder.defaults(THEME);
        o.moveApp(3, -1);
        assertEquals(THEME, o.packages());
        assertEquals(7, o.sectionArray().length);
    }
}
