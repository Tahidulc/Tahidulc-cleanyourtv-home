package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The shared parts' rules: a list's focus passing over section names and notes without wrapping round and its scroll
 * keeping the remote's row whole in sight; the menu's items in their order at their places, Settings at the foot; the
 * groups column's remote row held at one height; and Settings' lines shown as rows one for one.
 */
public class PanelsTest {
    private static List<RowList.Row> sample() {
        return Arrays.asList(RowList.Row.header("Update options"), RowList.Row.item("a", "Update interval, hours", "4"),
                RowList.Row.note("A note"), RowList.Row.toggle("b", "Update on app start", "", false), RowList.Row.divider(),
                RowList.Row.radio("c", "Small", true), RowList.Row.icon("d", Dark.SEARCH, "Search"));
    }

    @Test public void focusPassesOverNamesAndNotesAndDoesNotWrap() {
        List<RowList.Row> rows = sample();
        assertEquals(1, RowList.next(rows, -1, 1));
        assertEquals(3, RowList.next(rows, 1, 1));
        assertEquals(5, RowList.next(rows, 3, 1));
        assertEquals(6, RowList.next(rows, 5, 1));
        assertEquals(-1, RowList.next(rows, 6, 1));
        assertEquals(3, RowList.next(rows, 5, -1));
        assertEquals(-1, RowList.next(rows, 1, -1));
        assertFalse(rows.get(0).focusable());
        assertFalse(rows.get(2).focusable());
        assertFalse(rows.get(4).focusable());
        assertTrue(rows.get(6).focusable());
    }

    @Test public void theListScrollsOnlyAsFarAsItMust() {
        // In sight already: no move.
        assertEquals(0f, RowList.scrollFor(0f, 500f, 590f, 920f, 2000f), 0.01f);
        // Below the foot: just enough for the row's foot to show.
        assertEquals(70f, RowList.scrollFor(0f, 900f, 990f, 920f, 2000f), 0.01f);
        // Above the top: its top at the top.
        assertEquals(100f, RowList.scrollFor(300f, 100f, 190f, 920f, 2000f), 0.01f);
        // Never past the list's end, nor before its start.
        assertEquals(1080f, RowList.scrollFor(0f, 1950f, 2040f, 920f, 2000f), 0.01f);
        assertEquals(0f, RowList.scrollFor(50f, -10f, 80f, 920f, 2000f), 0.01f);
        // A short list does not scroll.
        assertEquals(0f, RowList.scrollFor(0f, 300f, 390f, 920f, 400f), 0.01f);
    }

    @Test public void theMenuHasEverySectionInOrderWithSettingsAtTheFoot() {
        assertEquals(Rail.SEARCH, Rail.ORDER[0]);
        assertEquals(Rail.HOME, Rail.ORDER[1]);
        assertEquals(Rail.SETTINGS, Rail.ORDER[Rail.ORDER.length - 1]);
        Set<Integer> ids = new HashSet<>();
        for (int id : Rail.ORDER) {
            assertTrue(ids.add(id));
            assertFalse(Rail.NAMES[id].isEmpty());
        }
        assertEquals(9, ids.size());
        assertTrue(ids.contains(Rail.RECORDINGS));
        assertTrue(ids.contains(Rail.MY_LIST));
        assertEquals(224f, Rail.y(Rail.SEARCH), 0.01f);
        assertEquals(320f, Rail.y(Rail.HOME), 0.01f);
        assertEquals(1008f, Rail.y(Rail.SETTINGS), 0.01f);
        // 96 px apart, the last item's foot clear of Settings' top.
        assertEquals(Rail.PITCH, Rail.y(Rail.GUIDE) - Rail.y(Rail.HOME), 0.01f);
        assertTrue(Rail.y(Rail.MY_LIST) + Rail.ITEM_H / 2f < Rail.FOOT - Rail.ITEM_H / 2f);
        assertEquals(-1, Rail.indexOf(42));
    }

    @Test public void theGroupsColumnHoldsTheRemotesRowAtOneHeight() {
        for (int k = 0; k < 40; k++) {
            assertEquals(GroupsPanel.FOCUS_Y, GroupsPanel.TOP + k * GroupsPanel.ROW - GroupsPanel.scrollFor(k), 0.01f);
        }
        // The seventh row (the family's TNT Sports) sits where it was measured, 500 to 579 at no scroll.
        assertEquals(500f, GroupsPanel.TOP + 6 * GroupsPanel.ROW, 0.01f);
    }

    @Test public void settingsLinesBecomeRowsOneForOne() {
        ListPanel.Item head = new ListPanel.Item(null, ListPanel.HEADING, "Playlists", "");
        ListPanel.Item line = new ListPanel.Item("check", ListPanel.TEXT, "Check for updates", "Looked a minute ago");
        line.dot = true;
        ListPanel.Item note = new ListPanel.Item(null, ListPanel.NOTE, "Homefield TV is up to date.", "");
        List<RowList.Row> rows = SettingsScreen.rows(Arrays.asList(head, line, note));
        assertEquals(3, rows.size());
        assertEquals(RowList.HEADER, rows.get(0).kind);
        assertEquals("Playlists", rows.get(0).title);
        assertEquals(RowList.ITEM, rows.get(1).kind);
        assertEquals("Check for updates", rows.get(1).title);
        assertEquals("Looked a minute ago", rows.get(1).value);
        assertTrue(rows.get(1).dot);
        assertSame(line, rows.get(1).tag);
        assertEquals(RowList.NOTE, rows.get(2).kind);
    }

    @Test public void lightDiscsGetADarkTick() {
        assertTrue(RowList.luminance(0xFFFFEB3B) > 0.5f);
        assertTrue(RowList.luminance(0xFF3F51B5) < 0.5f);
    }
}
