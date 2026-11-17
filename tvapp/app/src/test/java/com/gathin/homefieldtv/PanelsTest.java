package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The shared parts' rules: a list's focus passing over section names and notes without wrapping round and its scroll
 * keeping the remote's row whole in sight; the menu's items in their order at their places, Settings at the foot; the
 * groups column's remote row held at one height; and Settings' sections, pages and choices.
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

    @Test public void settingsHasTheOtherAppsSectionsInItsOrder() {
        assertEquals(Arrays.asList("General", "Playlists", "EPG", "Appearance", "Playback", "Remote control", "Parental controls",
                "Other", "About"), Arrays.asList(SettingsScreen.SECTION_NAMES));
        assertEquals(SettingsScreen.SECTIONS.length, SettingsScreen.SECTION_NAMES.length);
    }

    @Test public void aPageIsReachedThroughItsParents() {
        assertEquals(Arrays.asList("appearance", "appearance.guide"), SettingsScreen.chain("appearance.guide"));
        assertEquals(Arrays.asList("playlists", "playlists.add", "playlists.add.m3u"), SettingsScreen.chain("playlists.add.m3u"));
        // A playlist's or a group's own page, whose name can hold dots.
        assertEquals(Arrays.asList("playlists", "playlist:p0"), SettingsScreen.chain("playlist:p0"));
        assertEquals(Arrays.asList("appearance", "appearance.groups", "group:U.K. | Sport"), SettingsScreen.chain("group:U.K. | Sport"));
        assertEquals(Arrays.asList("appearance", "theme"), SettingsScreen.chain("theme"));
        // A setting's choices, under the page it is on.
        assertEquals(Arrays.asList("appearance", "appearance.player", "pick:clock"), SettingsScreen.chain("pick:clock"));
        assertEquals(Arrays.asList("epg", "pick:x.guide.hours"), SettingsScreen.chain("pick:x.guide.hours"));
        assertEquals(Arrays.asList("remote", "remote.player", "pick:colour.2"), SettingsScreen.chain("pick:colour.2"));
        assertEquals(Arrays.asList("other", "other.recording"), SettingsScreen.chain("other.recording"));
        assertTrue(SettingsScreen.chain("").isEmpty());
    }

    @Test public void aSettingsChoicesAreRadioRowsTheOneInUseChosen() {
        SettingsScreen.Picker p = new SettingsScreen.Picker("Buffer size", new String[]{"", "Small", "Normal", "Large"}, 2, "More buffer");
        List<RowList.Row> rows = SettingsScreen.pickerRows(p);
        // The blank choice left out, the note last.
        assertEquals(4, rows.size());
        assertEquals(RowList.RADIO, rows.get(0).kind);
        assertEquals("Small", rows.get(0).title);
        assertEquals("opt:1", rows.get(0).tag);
        assertTrue(rows.get(1).checked);
        assertFalse(rows.get(0).checked);
        assertEquals(RowList.NOTE, rows.get(3).kind);
        assertEquals(1, SettingsScreen.checkedRow(rows));
    }

    @Test public void addressesShowOnlyTheirServerAndTheGuideSaysWhenItWasRead() {
        assertEquals("provider.example:8080", ExtraSettings.host("http://provider.example:8080/get.php?username=a&password=b"));
        assertEquals("host.example", ExtraSettings.host("http://user:secret@host.example/list.m3u"));
        assertEquals("", ExtraSettings.host(""));
        assertEquals("••••", ExtraSettings.dots(4));
        assertEquals("Not read yet", ExtraSettings.epgStatus(false, 0, 0));
        assertTrue(ExtraSettings.epgStatus(false, 16436, 1_700_000_000_000L).startsWith("EPG is successfully updated for 16436 channels on "));
        assertEquals("The guide is being read…", ExtraSettings.epgStatus(true, 10, 1));
    }

    @Test public void lightDiscsGetADarkTick() {
        assertTrue(RowList.luminance(0xFFFFEB3B) > 0.5f);
        assertTrue(RowList.luminance(0xFF3F51B5) < 0.5f);
    }
}
