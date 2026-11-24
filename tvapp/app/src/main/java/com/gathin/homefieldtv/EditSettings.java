package com.gathin.homefieldtv;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.engine.Versions;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.Look;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Settings' pages for the channels and categories the family edits, and for the logos (DEVICE.md §2.15, Appearance).
 * The family calls the playlist's groups categories, so every word they read does (the code keeps "group"):
 *
 * <pre>
 *   channels                  Channels &amp; categories (a section of its own, beside Appearance): One row per channel
 *                             (merge qualities), on as it comes · Hidden channels › · Manage categories › · Rename
 *                             channels › · Channels sorting
 *   appearance.guide          TV guide: Guide style (Grid, as it comes, or Shelves) · Channels sorting (By order in
 *                             playlist, By name, By number, Manual) · One row per channel · Show channel numbers · Rows
 *                             in the guide · Progress bars · Hidden channels › · Manage categories ›
 *   channels.hidden           Show all channels · each channel hidden (OK shows it again): a channel in several
 *                             qualities hidden whole is one line (OK brings every version back), a version hidden by
 *                             itself its own (appearance.guide.hidden is the same page)
 *   channels.categories       Manage categories: Show all categories · Hide all categories · each category ›
 *                             (appearance.guide.groups is the same page)
 *   group:NAME                Show this category · Position (Move up, down, to the top) · Name (Rename, its playlist's
 *                             name again) · Sort channels
 *   channels.rename           Rename channels: the channels renamed, then each category ›
 *   rename:NAME               a category's channels, each with its logo and the name it shows under (the playlist's
 *                             under it when the family's differs): OK types a new one (nothing typed: the playlist's)
 *   appearance.groups         Categories: Categories sorting (as the family's TiviMate has it, 89-set-app-groups)
 *   appearance.logos          Logos: Logos priority (New logos (Homefield), as it comes, or Playlist's logos) · Refresh
 *                             logos · Behind logos (Logo's colour as it comes, None, Black tile: Logos)
 * </pre>
 *
 * Homefield's Settings → Categories (hide, rename and move them) lives on in Manage categories and each category's page;
 * the guide's menus (OK held on a channel or a category) do the same from the guide. Its choices are "csort",
 * "lpriority" and "gsort:" a category; its fields "gname:" a category and "chname:" a channel's key. Rename channel is
 * always in a channel's menu now (the family asked), so the old Channel names editor setting is no longer shown.
 */
final class EditSettings implements SettingsScreen.Pages {
    static final String[] PRIORITY = {"New logos (Homefield)", "Playlist's logos"};
    /** A category's page in Rename channels lists at most this many of its channels (the rest: from the guide). */
    static final int RENAME_MAX = 300;

    private final TvActivity tv;
    private final SettingsScreen s;

    EditSettings(TvActivity tv, SettingsScreen s) {
        this.tv = tv;
        this.s = s;
    }

    @Override public boolean owns(String page) {
        return page.equals("appearance.guide") || page.startsWith("appearance.guide.") || page.startsWith("group:")
                || page.equals("appearance.groups") || page.equals("appearance.logos") || page.equals("channels")
                || page.startsWith("channels.") || page.startsWith("rename:");
    }

    @Override public String title(String page) {
        if (page.startsWith("group:")) return Categories.label(tv, page.substring(6));
        if (page.startsWith("rename:")) return Categories.label(tv, page.substring(7));
        switch (page) {
            case "channels": return "Channels & categories";
            case "appearance.guide": return "TV guide";
            case "appearance.guide.hidden":
            case "channels.hidden": return "Hidden channels";
            case "appearance.guide.groups":
            case "channels.categories": return "Manage categories";
            case "channels.rename": return "Rename channels";
            case "appearance.groups": return "Categories";
            case "appearance.logos": return "Logos";
            default: return "";
        }
    }

    // ---- the pages ------------------------------------------------------------------------------------------------

    @Override public List<RowList.Row> rows(String page) {
        List<RowList.Row> out = new ArrayList<>();
        if (page.startsWith("group:")) {
            group(page.substring(6), out);
            return out;
        }
        if (page.startsWith("rename:")) {
            renameCategory(page.substring(7), out);
            return out;
        }
        switch (page) {
            case "channels": channels(out); break;
            case "appearance.guide": guide(out); break;
            case "appearance.guide.hidden":
            case "channels.hidden": hidden(out); break;
            case "appearance.guide.groups":
            case "channels.categories": groups(out); break;
            case "channels.rename": rename(out); break;
            case "appearance.groups":
                out.add(MoreSettings.pick("x.sorting", "Categories sorting", ExtraSettings.SORTING[Categories.ordered(tv) ? 1 : 0]));
                out.add(RowList.Row.note("Hide, rename and move categories in Channels & categories › Manage categories, or hold OK "
                        + "on a category in the TV guide."));
                break;
            case "appearance.logos":
                out.add(MoreSettings.pick("lpriority", "Logos priority", PRIORITY[LogoPack.on() ? 0 : 1]));
                out.add(RowList.Row.item("do:logos.refresh", "Refresh logos", LogoPack.status(tv)));
                out.add(MoreSettings.pick("logos", "Logo box colour", Logos.NAMES[Logos.behind()]));
                out.add(RowList.Row.note("New logos: the tv-logo project's free logos (github.com/tv-logo/tv-logos), for your own "
                        + "viewing, fetched as they show; only where a channel's name surely matches one. A channel's own: hold OK "
                        + "on it in the TV guide, then Change logo."));
                break;
            default:
                break;
        }
        return out;
    }

    /** The one row per channel switch, as both pages show it. */
    private RowList.Row mergeRow() {
        return MoreSettings.tog("merge", "One row per channel (merge qualities)",
                "A channel's FHD, HD and SD as one row, its best played; hold OK on it to pick a quality", ChannelEdits.merging(tv));
    }

    /** Channels &amp; categories: what the family hides, renames and sorts, in one place. */
    private void channels(List<RowList.Row> out) {
        out.add(mergeRow());
        int hidden = hiddenLines();
        out.add(MoreSettings.page("channels.hidden", "Hidden channels", hidden == 0 ? "None" : count(hidden, "channel", "channels")));
        out.add(MoreSettings.page("channels.categories", "Manage categories", categoriesWords()));
        int renamed = renamed().size();
        out.add(MoreSettings.page("channels.rename", "Rename channels", renamed == 0 ? "Give any channel a name of your own"
                : count(renamed, "channel", "channels") + " renamed"));
        // Merged channels (AddedSettings' page): the merges made by hand in the TV guide.
        out.add(AddedSettings.mergedRow(tv));
        out.add(MoreSettings.pick("csort", "Channels sorting", ChannelEdits.SORT_NAMES[ChannelEdits.sort(tv)]));
        out.add(RowList.Row.note("Hold OK on a channel in the TV guide to hide, rename, move or merge it, or to pick its quality; "
                + "on a category, to hide, rename, move or sort it."));
    }

    /** Appearance › TV guide: the other app's first rows, Homefield's guide rows, then the two pages to bring things back. */
    private void guide(List<RowList.Row> out) {
        out.add(MoreSettings.pick("gstyle", "Guide style", Look.GUIDE_NAMES[Look.guideStyle]));
        out.add(MoreSettings.pick("csort", "Channels sorting", ChannelEdits.SORT_NAMES[ChannelEdits.sort(tv)]));
        out.add(mergeRow());
        out.add(MoreSettings.tog("numbers", "Show channel numbers", "", Look.numbers));
        out.add(MoreSettings.pick("rows", "Rows in the guide", Look.ROWS_NAMES[Look.rows]));
        out.add(MoreSettings.tog("progress", "Progress bars", "How far through each programme is, in the guide and on live TV", Look.progress));
        int hidden = hiddenLines();
        out.add(MoreSettings.page("appearance.guide.hidden", "Hidden channels", hidden == 0 ? "None" : count(hidden, "channel", "channels")));
        out.add(MoreSettings.page("appearance.guide.groups", "Manage categories", categoriesWords()));
    }

    /** "12 categories shown · 2 hidden" ("" before the channels are in). */
    private String categoriesWords() {
        List<String> every = Categories.every(tv, Library.get(tv).groups());
        int off = 0;
        for (String g : every) if (Categories.isHidden(tv, g)) off++;
        return every.isEmpty() ? "" : count(every.size() - off, "category", "categories") + " shown" + (off > 0 ? "  ·  " + off + " hidden" : "");
    }

    /**
     * Hidden channels' lines, each {name, where, tag}: one row per channel, a channel in several qualities hidden whole is
     * one line ("hgrp:" its key: OK shows every version again); a version hidden by itself (its channel showing) is its
     * own line by its own name ("hch:"); with the setting off, every version its own line as before.
     */
    private List<String[]> hiddenRows() {
        Library lib = Library.get(tv);
        List<String[]> out = new ArrayList<>();
        Set<String> done = new HashSet<>();
        for (String k : ChannelEdits.hiddenKeys(tv)) {
            if (!done.add(k)) continue;
            Channel c = lib.exact(k);
            if (c == null) {
                out.add(new String[]{"A channel not in your playlists now", "OK to forget it", "hch:" + k});
                continue;
            }
            Versions v = Versions.of(c);
            String where = Categories.label(tv, c.group);
            if (v != null && ChannelEdits.hidden(c)) {
                for (Channel o : v.all) done.add(o.key);
                out.add(new String[]{ChannelEdits.name(c), where + "  ·  " + v.size() + " qualities  ·  OK to show it again", "hgrp:" + k});
            } else if (v != null) {
                out.add(new String[]{c.name, "One of " + ChannelEdits.name(c) + "'s qualities  ·  OK to show it again", "hch:" + k});
            } else {
                out.add(new String[]{ChannelEdits.name(c), where + "  ·  OK to show it again", "hch:" + k});
            }
        }
        return out;
    }

    private int hiddenLines() {
        return hiddenRows().size();
    }

    /** Hidden channels: each one the family hid, OK showing it again. */
    private void hidden(List<RowList.Row> out) {
        List<String[]> lines = hiddenRows();
        if (lines.isEmpty()) {
            out.add(RowList.Row.note("None. Hold OK on a channel in the TV guide and choose Hide channel to take it out of every list."));
            return;
        }
        out.add(RowList.Row.item("do:hidden.all", "Show all channels", "Every hidden channel back in the lists"));
        out.add(RowList.Row.header("Hidden channels"));
        lines.sort((a, b) -> a[0].toLowerCase(Locale.ROOT).compareTo(b[0].toLowerCase(Locale.ROOT)));
        for (String[] l : lines) out.add(RowList.Row.item(l[2], l[0], l[1]));
        out.add(RowList.Row.note("Hidden channels are out of the guide, the channel lists, channel up and down, the numbers and search."));
    }

    /** Manage categories: every category, shown or hidden, each opening its page. */
    private void groups(List<RowList.Row> out) {
        Library lib = Library.get(tv);
        List<String> every = Categories.every(tv, lib.groups());
        if (every.isEmpty()) {
            out.add(RowList.Row.note("Your playlists' categories show here once their channels are in."));
            return;
        }
        boolean anyHidden = false, anyShown = false;
        for (String g : every) {
            if (Categories.isHidden(tv, g)) anyHidden = true;
            else anyShown = true;
        }
        RowList.Row show = RowList.Row.item("do:groups.show", "Show all categories", "Every category back in the lists");
        show.enabled = anyHidden;
        out.add(show);
        RowList.Row hide = RowList.Row.item("do:groups.hide", "Hide all categories", "Then show the ones you want, here");
        hide.enabled = anyShown;
        out.add(hide);
        out.add(RowList.Row.header("Your playlists' categories"));
        Map<String, Integer> counts = lib.groupSizes();
        for (String g : every) {
            boolean hidden = Categories.isHidden(tv, g);
            Integer n = counts.get(g);
            int k = n == null ? 0 : n;
            int own = Categories.sortOf(tv, g);
            String sub = (hidden ? "Hidden" : "Shown") + "  ·  " + count(k, "channel", "channels") + (own >= 0 ? "  ·  " + ChannelEdits.SORT_NAMES[own] : "")
                    + (Categories.renamed(tv, g) ? "  ·  the playlist calls it " + (g.isEmpty() ? Categories.UNNAMED : g) : "");
            out.add(MoreSettings.page("group:" + g, Categories.label(tv, g), sub));
        }
        out.add(RowList.Row.note("Hidden: out of the guide, Live TV, channel up and down and search; favourites stay."));
    }

    /** A category's page: shown or hidden, moved, named, its channels' order. */
    private void group(String g, List<RowList.Row> out) {
        Library lib = Library.get(tv);
        List<String> every = Categories.every(tv, lib.groups());
        int at = every.indexOf(g);
        if (at < 0) {
            out.add(RowList.Row.note("This category isn't in your playlists now."));
            return;
        }
        boolean hidden = Categories.isHidden(tv, g);
        out.add(MoreSettings.tog("cat.shown", "Show this category", "In the guide, Live TV, channel up and down and search", !hidden));
        out.add(RowList.Row.header("Position  ·  " + MoreSettings.ordinal(at + 1) + " of " + every.size()));
        if (at > 0) out.add(RowList.Row.item("do:cat.up", "Move up", ""));
        if (at < every.size() - 1) out.add(RowList.Row.item("do:cat.down", "Move down", ""));
        if (at > 0) out.add(RowList.Row.item("do:cat.top", "Move to the top", ""));
        out.add(RowList.Row.header("Name"));
        out.add(RowList.Row.item("type:gname", "Rename", Categories.label(tv, g)));
        if (Categories.renamed(tv, g)) {
            out.add(RowList.Row.item("do:cat.unname", "Its playlist's name again", g.isEmpty() ? Categories.UNNAMED : g));
        }
        out.add(RowList.Row.header("Channels"));
        int own = Categories.sortOf(tv, g);
        out.add(MoreSettings.pick("gsort:" + g, "Sort channels", own >= 0 ? ChannelEdits.SORT_NAMES[own]
                : ChannelEdits.SORT_NAMES[ChannelEdits.sort(tv)] + "  (as Channels sorting)"));
    }

    /** The channels the family renamed, as the lists show them (a channel in several qualities once), by name. */
    private List<Channel> renamed() {
        Library lib = Library.get(tv);
        List<Channel> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String k : ChannelEdits.renamedKeys(tv)) {
            Channel c = lib.byKey(k);
            if (c != null && ChannelEdits.renamed(c) && seen.add(c.key)) out.add(c);
        }
        out.sort((a, b) -> ChannelEdits.name(a).compareToIgnoreCase(ChannelEdits.name(b)));
        return out;
    }

    /** A channel's line: its logo, the name it shows under, the playlist's under it when the family's differs. */
    private RowList.Row channelRow(Channel c) {
        String now = ChannelEdits.name(c), was = ChannelEdits.playlistName(c);
        return RowList.Row.channel("ren:" + c.key, c, now, now.equals(was) ? "" : "Playlist's name: " + was);
    }

    /** Rename channels: the ones renamed, then each category to find the others in. */
    private void rename(List<RowList.Row> out) {
        Library lib = Library.get(tv);
        out.add(RowList.Row.note("OK on a channel to type a name of your own. Leave it empty to give it its playlist's name again."));
        List<Channel> done = renamed();
        if (!done.isEmpty()) {
            out.add(RowList.Row.header("Renamed"));
            for (Channel c : done) out.add(channelRow(c));
        }
        List<String> groups = Categories.arranged(tv, lib.rowGroups());
        if (groups.isEmpty()) {
            out.add(RowList.Row.note("Your playlists' channels show here once they are in."));
            return;
        }
        out.add(RowList.Row.header("Categories"));
        for (String g : groups) {
            int n = lib.group(g).channels.size();
            if (n > 0) out.add(MoreSettings.page("rename:" + g, Categories.label(tv, g), count(n, "channel", "channels")));
        }
    }

    /** A category's channels to rename (at most RENAME_MAX; the rest from the guide). */
    private void renameCategory(String g, List<RowList.Row> out) {
        List<Channel> l = Library.get(tv).group(g).channels;
        if (l.isEmpty()) {
            out.add(RowList.Row.note("This category has no channels shown now."));
            return;
        }
        for (int i = 0; i < l.size() && i < RENAME_MAX; i++) out.add(channelRow(l.get(i)));
        if (l.size() > RENAME_MAX) {
            out.add(RowList.Row.note("The first " + RENAME_MAX + " of " + l.size() + ". Rename any other from the TV guide: hold OK on it, "
                    + "then Rename channel."));
        }
    }

    // ---- OK on a row ----------------------------------------------------------------------------------------------

    @Override public void chosen(String page, RowList.Row row) {
        String t = row.tag instanceof String ? (String) row.tag : "";
        String g = page.startsWith("group:") ? page.substring(6) : null;
        if (t.startsWith("page:")) {
            s.open(t.substring(5));
            return;
        }
        if (t.startsWith("pick:")) {
            s.open(SettingsScreen.PICK + t.substring(5));
            return;
        }
        Library lib = Library.get(tv);
        if (t.startsWith("hch:")) {
            ChannelEdits.setHidden(tv, t.substring(4), false);
            s.refresh();
            return;
        }
        if (t.startsWith("hgrp:")) {
            // A channel in several qualities: every version back in the lists.
            Channel c = lib.exact(t.substring(5));
            if (c != null) ChannelEdits.setHidden(tv, c, false);
            else ChannelEdits.setHidden(tv, t.substring(5), false);
            s.refresh();
            return;
        }
        if (t.startsWith("ren:")) {
            Channel c = lib.byKey(t.substring(4));
            if (c == null) return;
            String now = ChannelEdits.name(c);
            s.type("chname:" + c.key, "A name for " + now, now, false);
            return;
        }
        switch (t) {
            case "tog:merge": ChannelEdits.setMerging(tv, !ChannelEdits.merging(tv)); break;
            case "tog:numbers": Look.setNumbers(tv, !Look.numbers); break;
            case "tog:progress": Look.setProgress(tv, !Look.progress); break;
            case "tog:cat.shown":
                if (g != null) Categories.setHidden(tv, g, !Categories.isHidden(tv, g));
                break;
            case "do:hidden.all": ChannelEdits.showAll(tv); break;
            case "do:groups.show": Categories.showAll(tv); break;
            case "do:groups.hide":
                s.ask("Hide all categories?", "Every category goes from the guide, Live TV and search until you show it again here.",
                        "Hide all categories", "Keep them", () -> Categories.hideAll(tv, Categories.every(tv, lib.groups())));
                return;
            case "do:cat.up":
            case "do:cat.down":
            case "do:cat.top":
                if (g == null) return;
                Categories.move(tv, lib.groups(), g, t.equals("do:cat.up") ? -1 : t.equals("do:cat.down") ? 1 : 0);
                // The remote stays on the same move where it is still offered, to move it again.
                s.refreshOn(t);
                return;
            case "do:cat.unname":
                if (g != null) Categories.rename(tv, g, "");
                break;
            case "type:gname":
                if (g != null) s.type("gname:" + g, "A name for " + Categories.label(tv, g), Categories.label(tv, g), false);
                return;
            case "do:logos.refresh":
                LogoPack.refresh(tv);
                s.toast("The list of logos is asked for again, and each logo fetched again as it shows");
                break;
            default:
                return;
        }
        s.refresh();
    }

    // ---- a setting's choices --------------------------------------------------------------------------------------

    @Override public SettingsScreen.Picker picker(String key) {
        if (key.startsWith("gsort:")) {
            String g = key.substring(6);
            int own = Categories.sortOf(tv, g);
            return new SettingsScreen.Picker("Sort channels", ChannelEdits.SORT_NAMES, own >= 0 ? own : ChannelEdits.sort(tv),
                    "Manual: the order you put them in with Move channel (hold OK on a channel in the TV guide).");
        }
        switch (key) {
            case "gstyle": return new SettingsScreen.Picker("Guide style", Look.GUIDE_NAMES, Look.guideStyle,
                    "Grid: your guide as it is, a row for each channel along the times, the programme's details and the channel "
                            + "playing over it. Shelves: each channel's evening as pictures, what's on now first.");
            case "csort": return new SettingsScreen.Picker("Channels sorting", ChannelEdits.SORT_NAMES, ChannelEdits.sort(tv),
                    "Manual: the order you put channels in with Move channel (hold OK on a channel in the TV guide). A category "
                            + "can be sorted its own way: hold OK on it.");
            case "lpriority": return new SettingsScreen.Picker("Logos priority", PRIORITY, LogoPack.on() ? 0 : 1,
                    "New logos: a channel whose name surely matches one of the tv-logo project's gets its logo; the others keep "
                            + "the playlist's.");
            default: return null;
        }
    }

    @Override public void picked(String key, int i) {
        if (key.startsWith("gsort:")) {
            if (i >= 0 && i < ChannelEdits.SORT_NAMES.length) Categories.setSort(tv, key.substring(6), i);
            return;
        }
        switch (key) {
            case "csort":
                if (i >= 0 && i < ChannelEdits.SORT_NAMES.length) ChannelEdits.setSort(tv, i);
                break;
            case "gstyle": Look.setGuideStyle(tv, i); break;
            case "lpriority": LogoPack.setOn(tv, i == 0); break;
            default: break;
        }
    }

    // ---- typed in -------------------------------------------------------------------------------------------------

    boolean ownsField(String field) {
        return field.startsWith("gname:") || field.startsWith("chname:");
    }

    @Override public String value(String field) {
        return "";
    }

    @Override public void typed(String field, String text) {
        if (field.startsWith("gname:")) Categories.rename(tv, field.substring(6), text);
        if (field.startsWith("chname:")) {
            // The channel as a whole (one row per channel: every version); nothing typed is its playlist's name again.
            Channel c = Library.get(tv).byKey(field.substring(7));
            if (c != null) ChannelEdits.rename(tv, c, text);
        }
    }

    /** "1 channel", "12,345 categories". */
    static String count(int n, String one, String many) {
        return String.format(Locale.UK, "%,d %s", n, n == 1 ? one : many);
    }
}
