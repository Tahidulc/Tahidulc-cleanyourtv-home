package com.gathin.homefieldtv;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.Look;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Settings' pages for the channels and groups the family edits, and for the logos (DEVICE.md §2.15, Appearance):
 *
 * <pre>
 *   appearance.guide          TV guide: Guide style (Grid, as it comes, or Shelves) · Channels sorting (By order in
 *                             playlist, By name, By number, Manual) · Channel
 *                             names editor (On as it comes) · Show channel numbers · Rows in the guide · Progress bars ·
 *                             Hidden channels › · Manage groups ›
 *   appearance.guide.hidden   Show all channels · each channel hidden (OK shows it again)
 *   appearance.guide.groups   Manage groups: Show all groups · Hide all groups · each group ›
 *   group:NAME                Show this group · Position (Move up, down, to the top) · Name (Rename, its playlist's
 *                             name again) · Sort channels
 *   appearance.groups         Groups: Groups sorting (as the family's TiviMate has it, 89-set-app-groups)
 *   appearance.logos          Logos: Logos priority (New logos (Homefield), as it comes, or Playlist's logos) · Refresh
 *                             logos · Behind logos (Logo's colour as it comes, None, Black tile: Logos)
 * </pre>
 *
 * Homefield's Settings → Categories (hide, rename and move groups) lives on in Manage groups and each group's page; the
 * guide's menus (OK held on a channel or a group) do the same from the guide. Its choices are "csort", "editor",
 * "lpriority" and "gsort:" a group; its field "gname:" a group.
 */
final class EditSettings implements SettingsScreen.Pages {
    static final String[] PRIORITY = {"New logos (Homefield)", "Playlist's logos"};

    private final TvActivity tv;
    private final SettingsScreen s;

    EditSettings(TvActivity tv, SettingsScreen s) {
        this.tv = tv;
        this.s = s;
    }

    @Override public boolean owns(String page) {
        return page.equals("appearance.guide") || page.startsWith("appearance.guide.") || page.startsWith("group:")
                || page.equals("appearance.groups") || page.equals("appearance.logos");
    }

    @Override public String title(String page) {
        if (page.startsWith("group:")) return Categories.label(tv, page.substring(6));
        switch (page) {
            case "appearance.guide": return "TV guide";
            case "appearance.guide.hidden": return "Hidden channels";
            case "appearance.guide.groups": return "Manage groups";
            case "appearance.groups": return "Groups";
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
        switch (page) {
            case "appearance.guide": guide(out); break;
            case "appearance.guide.hidden": hidden(out); break;
            case "appearance.guide.groups": groups(out); break;
            case "appearance.groups":
                out.add(MoreSettings.pick("x.sorting", "Groups sorting", ExtraSettings.SORTING[Categories.ordered(tv) ? 1 : 0]));
                out.add(RowList.Row.note("Hide, rename and move groups in TV guide › Manage groups, or hold OK on a group in the TV guide."));
                break;
            case "appearance.logos":
                out.add(MoreSettings.pick("lpriority", "Logos priority", PRIORITY[LogoPack.on() ? 0 : 1]));
                out.add(RowList.Row.item("do:logos.refresh", "Refresh logos", LogoPack.status(tv)));
                out.add(MoreSettings.pick("logos", "Behind logos", Logos.NAMES[Logos.behind()]));
                out.add(RowList.Row.note("New logos: the tv-logo project's free logos (github.com/tv-logo/tv-logos), for your own "
                        + "viewing, fetched as they show; only where a channel's name surely matches one. A channel's own: hold OK "
                        + "on it in the TV guide, then Change logo."));
                break;
            default:
                break;
        }
        return out;
    }

    /** Appearance › TV guide: the other app's first rows, Homefield's guide rows, then the two pages to bring things back. */
    private void guide(List<RowList.Row> out) {
        out.add(MoreSettings.pick("gstyle", "Guide style", Look.GUIDE_NAMES[Look.guideStyle]));
        out.add(MoreSettings.pick("csort", "Channels sorting", ChannelEdits.SORT_NAMES[ChannelEdits.sort(tv)]));
        out.add(MoreSettings.pick("editor", "Channel names editor", MoreSettings.ON_OFF[ChannelEdits.editor(tv) ? 1 : 0]));
        out.add(MoreSettings.tog("numbers", "Show channel numbers", "", Look.numbers));
        out.add(MoreSettings.pick("rows", "Rows in the guide", Look.ROWS_NAMES[Look.rows]));
        out.add(MoreSettings.tog("progress", "Progress bars", "How far through each programme is, in the guide and on live TV", Look.progress));
        int hidden = ChannelEdits.hiddenKeys(tv).size();
        out.add(MoreSettings.page("appearance.guide.hidden", "Hidden channels", hidden == 0 ? "None" : plural(hidden, "channel")));
        List<String> every = Categories.every(tv, Library.get(tv).groups());
        int off = 0;
        for (String g : every) if (Categories.isHidden(tv, g)) off++;
        out.add(MoreSettings.page("appearance.guide.groups", "Manage groups", every.isEmpty() ? ""
                : plural(every.size() - off, "group") + " shown" + (off > 0 ? "  ·  " + off + " hidden" : "")));
    }

    /** Hidden channels: each one the family hid, OK showing it again. */
    private void hidden(List<RowList.Row> out) {
        Library lib = Library.get(tv);
        List<String> keys = ChannelEdits.hiddenKeys(tv);
        if (keys.isEmpty()) {
            out.add(RowList.Row.note("None. Hold OK on a channel in the TV guide and choose Hide channel to take it out of every list."));
            return;
        }
        out.add(RowList.Row.item("do:hidden.all", "Show all channels", "Every hidden channel back in the lists"));
        out.add(RowList.Row.header("Hidden channels"));
        List<Object[]> rows = new ArrayList<>();
        for (String k : keys) {
            Channel c = lib.byKey(k);
            String name = c == null ? "A channel not in your playlists now" : ChannelEdits.name(c);
            String where = c == null ? "OK to forget it" : Categories.label(tv, c.group) + "  ·  OK to show it again";
            rows.add(new Object[]{name.toLowerCase(Locale.ROOT), RowList.Row.item("hch:" + k, name, where)});
        }
        rows.sort((a, b) -> ((String) a[0]).compareTo((String) b[0]));
        for (Object[] r : rows) out.add((RowList.Row) r[1]);
        out.add(RowList.Row.note("Hidden channels are out of the guide, the channel lists, channel up and down, the numbers and search."));
    }

    /** Manage groups: every group, shown or hidden, each opening its page. */
    private void groups(List<RowList.Row> out) {
        Library lib = Library.get(tv);
        List<String> every = Categories.every(tv, lib.groups());
        if (every.isEmpty()) {
            out.add(RowList.Row.note("Your playlists' groups show here once their channels are in."));
            return;
        }
        boolean anyHidden = false, anyShown = false;
        for (String g : every) {
            if (Categories.isHidden(tv, g)) anyHidden = true;
            else anyShown = true;
        }
        RowList.Row show = RowList.Row.item("do:groups.show", "Show all groups", "Every group back in the lists");
        show.enabled = anyHidden;
        out.add(show);
        RowList.Row hide = RowList.Row.item("do:groups.hide", "Hide all groups", "Then show the ones you want, here");
        hide.enabled = anyShown;
        out.add(hide);
        out.add(RowList.Row.header("Your playlists' groups"));
        Map<String, Integer> counts = lib.groupSizes();
        for (String g : every) {
            boolean hidden = Categories.isHidden(tv, g);
            Integer n = counts.get(g);
            int k = n == null ? 0 : n;
            int own = Categories.sortOf(tv, g);
            String sub = (hidden ? "Hidden" : "Shown") + "  ·  " + plural(k, "channel") + (own >= 0 ? "  ·  " + ChannelEdits.SORT_NAMES[own] : "")
                    + (Categories.renamed(tv, g) ? "  ·  the playlist calls it " + (g.isEmpty() ? Categories.UNNAMED : g) : "");
            out.add(MoreSettings.page("group:" + g, Categories.label(tv, g), sub));
        }
        out.add(RowList.Row.note("Hidden: out of the guide, Live TV, channel up and down and search; favourites stay."));
    }

    /** A group's page: shown or hidden, moved, named, its channels' order. */
    private void group(String g, List<RowList.Row> out) {
        Library lib = Library.get(tv);
        List<String> every = Categories.every(tv, lib.groups());
        int at = every.indexOf(g);
        if (at < 0) {
            out.add(RowList.Row.note("This group isn't in your playlists now."));
            return;
        }
        boolean hidden = Categories.isHidden(tv, g);
        out.add(MoreSettings.tog("cat.shown", "Show this group", "In the guide, Live TV, channel up and down and search", !hidden));
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
        if (t.startsWith("hch:")) {
            ChannelEdits.setHidden(tv, t.substring(4), false);
            s.refresh();
            return;
        }
        Library lib = Library.get(tv);
        switch (t) {
            case "tog:numbers": Look.setNumbers(tv, !Look.numbers); break;
            case "tog:progress": Look.setProgress(tv, !Look.progress); break;
            case "tog:cat.shown":
                if (g != null) Categories.setHidden(tv, g, !Categories.isHidden(tv, g));
                break;
            case "do:hidden.all": ChannelEdits.showAll(tv); break;
            case "do:groups.show": Categories.showAll(tv); break;
            case "do:groups.hide":
                s.ask("Hide all groups?", "Every group goes from the guide, Live TV and search until you show it again here.",
                        "Hide all groups", "Keep them", () -> Categories.hideAll(tv, Categories.every(tv, lib.groups())));
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
                    "Manual: the order you put channels in with Move channel (hold OK on a channel in the TV guide). A group can "
                            + "be sorted its own way: hold OK on it.");
            case "editor": return new SettingsScreen.Picker("Channel names editor", MoreSettings.ON_OFF, ChannelEdits.editor(tv) ? 1 : 0,
                    "On: Rename channel in a channel's menu (hold OK on it in the TV guide).");
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
            case "editor": ChannelEdits.setEditor(tv, i == 1); break;
            case "gstyle": Look.setGuideStyle(tv, i); break;
            case "lpriority": LogoPack.setOn(tv, i == 0); break;
            default: break;
        }
    }

    // ---- typed in -------------------------------------------------------------------------------------------------

    boolean ownsField(String field) {
        return field.startsWith("gname:");
    }

    @Override public String value(String field) {
        return "";
    }

    @Override public void typed(String field, String text) {
        if (field.startsWith("gname:")) Categories.rename(tv, field.substring(6), text);
    }

    private static String plural(int n, String word) {
        return String.format(Locale.UK, "%,d %s%s", n, word, n == 1 ? "" : "s");
    }
}
