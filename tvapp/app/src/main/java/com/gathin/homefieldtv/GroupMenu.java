package com.gathin.homefieldtv;

import android.content.Context;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.util.ArrayList;
import java.util.List;

/**
 * OK held on a group in the TV guide's groups column: its menu, as TiviMate's group options and Manage groups have
 * them (FUNCTIONS.md, Groups panel and Group options), on the long-OK card (GuideMenu):
 *
 * <pre>
 *   the group's name     Rename group · Restore group name (once renamed) · Hide group · Move group (it lifts, Up and
 *                        Down move it, OK puts it down, Back leaves it: MoveMode) · Sort channels › (By order in
 *                        playlist, By name, By number, Manual: the group's own, the one in use ticked)
 *   Groups               Show all groups · Hide all groups · Manage groups (Settings › Appearance › TV guide › Manage groups)
 * </pre>
 *
 * On Favourites, All channels, Recently watched or one of the guide's own kinds, the Groups lines alone. Kept with
 * Settings' (Categories), so the guide, Live TV, the channel lists and search all go by them.
 */
final class GroupMenu {
    private GroupMenu() {}

    static final String RENAME = "grp.rename", UNNAME = "grp.unname", HIDE = "grp.hide", MOVE = "grp.move", SORT = "grp.sort",
            SORT_BY = "grp.sortby:", SHOW_ALL = "grp.showall", HIDE_ALL = "grp.hideall", MANAGE = "grp.manage";

    static boolean handles(String tag) {
        return tag != null && tag.startsWith("grp.");
    }

    /** The menu for a playlist's group (group: its name), or for another entry (null). */
    static List<GuideMenu.Item> items(Context c, String group) {
        List<GuideMenu.Item> out = new ArrayList<>();
        if (group != null) {
            out.add(GuideMenu.Item.section(Categories.label(c, group)));
            out.add(GuideMenu.Item.item(RENAME, "Rename group"));
            if (Categories.renamed(c, group)) out.add(GuideMenu.Item.item(UNNAME, "Restore group name"));
            out.add(GuideMenu.Item.item(HIDE, "Hide group"));
            out.add(GuideMenu.Item.item(MOVE, "Move group"));
            // The one in use at the line's right (07), and ticked on its own card (sortItems).
            out.add(GuideMenu.Item.item(SORT, "Sort channels").value(ChannelEdits.SORT_NAMES[Math.max(0,
                    Math.min(ChannelEdits.SORT_NAMES.length - 1, sortOf(c, group)))]));
        }
        out.add(GuideMenu.Item.section("Groups"));
        out.add(GuideMenu.Item.item(SHOW_ALL, "Show all groups"));
        out.add(GuideMenu.Item.item(HIDE_ALL, "Hide all groups"));
        out.add(GuideMenu.Item.item(MANAGE, "Manage groups"));
        return out;
    }

    /** Sort channels' choices for a group, the one in use ticked. */
    static List<GuideMenu.Item> sortItems(Context c, String group) {
        List<GuideMenu.Item> out = new ArrayList<>();
        out.add(GuideMenu.Item.section("Sort channels  ·  " + Categories.label(c, group)));
        int now = sortOf(c, group);
        for (int i = 0; i < ChannelEdits.SORT_NAMES.length; i++) out.add(GuideMenu.Item.choice(SORT_BY + i, ChannelEdits.SORT_NAMES[i], i == now));
        return out;
    }

    /** Where the remote starts on Sort channels: the choice in use. */
    static int sortFocus(Context c, String group) {
        return 1 + sortOf(c, group);
    }

    /** A group's channels sorting: its own, else as Channels sorting says. */
    static int sortOf(Context c, String group) {
        int own = Categories.sortOf(c, group);
        return own >= 0 ? own : ChannelEdits.sort(c);
    }

    /**
     * What a line does for a group (null for the other entries' lines), but Move group (the guide's own) and Sort
     * channels (its choices: the guide opens them).
     */
    static void act(TvActivity tv, String tag, String group) {
        Library lib = Library.get(tv);
        if (tag.startsWith(SORT_BY)) {
            int s = parse(tag.substring(SORT_BY.length()));
            if (group == null || s < 0 || s >= ChannelEdits.SORT_NAMES.length) return;
            Categories.setSort(tv, group, s);
            Toast.makeText(tv, Categories.label(tv, group) + ": " + ChannelEdits.SORT_NAMES[s].toLowerCase(java.util.Locale.UK),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        switch (tag) {
            case RENAME:
                if (group == null) return;
                tv.push(new RenameScreen(tv, "A name for " + Categories.label(tv, group), Categories.label(tv, group),
                        text -> Categories.rename(tv, group, text)));
                break;
            case UNNAME:
                if (group != null) Categories.rename(tv, group, "");
                break;
            case HIDE:
                if (group == null) return;
                Categories.setHidden(tv, group, true);
                Toast.makeText(tv, Categories.label(tv, group) + " hidden. Settings › Appearance › TV guide › Manage groups brings it back",
                        Toast.LENGTH_LONG).show();
                break;
            case SHOW_ALL:
                Categories.showAll(tv);
                break;
            case HIDE_ALL:
                Categories.hideAll(tv, Categories.every(tv, lib.groups()));
                Toast.makeText(tv, "Every group hidden: hold OK on All channels, then Show all groups, or show some in Manage groups",
                        Toast.LENGTH_LONG).show();
                break;
            case MANAGE:
                PinScreen.forSettings(tv, () -> tv.push(new SettingsScreen(tv, "appearance.guide.groups")));
                break;
            default:
                break;
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
