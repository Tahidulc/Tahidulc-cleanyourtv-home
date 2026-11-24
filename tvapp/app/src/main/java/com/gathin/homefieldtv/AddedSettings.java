package com.gathin.homefieldtv;

import android.content.Context;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Problems;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.Merges;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Settings' pages new in 3.4, each reached from a line on the page it belongs to:
 *
 * <pre>
 *   about.problems         About › If something goes wrong: the last problem noted (a crash, out of memory, a freeze for
 *                          some seconds, trouble in the background the app carried on through) with when and where, the
 *                          ones before it, Clear these notes; take a photo of them first to send
 *   about.auto             About › Updates › Automatic updates: Off (updates only from a computer) or Every time it
 *                          opens, the Update address (empty: off), Check now, what stood in the way, the room on the TV
 *   channels.merged        Channels &amp; categories › Merged channels: each merge (OK to separate it), Separate all
 *   other.askai            Other › Ask AI: the family's own Anthropic API key, masked, and the model (AiSettings)
 *   about.logopacks        About › Logo packs' licences: the logo catalogue's packs and their terms (LogoPacks.LICENCES)
 * </pre>
 *
 * The scoreboard's and the logo's settings have no page here: they are all in their menu over the player (hold OK while a
 * channel plays full screen: BoardMenu), the family's "Yes get rid of this" for the Scoreboard page.
 */
final class AddedSettings implements SettingsScreen.Pages {
    private final TvActivity tv;
    private final SettingsScreen s;

    AddedSettings(TvActivity tv, SettingsScreen s) {
        this.tv = tv;
        this.s = s;
    }

    /** The logo packs' licences' page. */
    static final String LICENCES = "about.logopacks";

    @Override public boolean owns(String page) {
        return page.equals("about.problems") || page.equals("about.auto") || page.equals("channels.merged")
                || page.equals(AiSettings.PAGE) || page.equals(LICENCES);
    }

    @Override public String title(String page) {
        switch (page) {
            case "about.problems": return "If something goes wrong";
            case "about.auto": return "Automatic updates";
            case "channels.merged": return "Merged channels";
            case AiSettings.PAGE: return "Ask AI";
            case LICENCES: return "Logo packs' licences";
            default: return "";
        }
    }

    // ---- the lines on other pages ---------------------------------------------------------------------------------------

    /** About's line: the last problem, or that nothing has happened. */
    static RowList.Row problemsRow() {
        Problems.Note n = Problems.last();
        return RowList.Row.item("page:about.problems", "If something goes wrong", n == null ? "Nothing has happened yet."
                : "Last problem: " + n.headline());
    }

    /** Updates' line. */
    static RowList.Row autoRow(Context c) {
        String a = AutoUpdate.address(c);
        return RowList.Row.item("page:about.auto", "Automatic updates", AutoUpdate.mode(c) == AutoUpdate.OFF || a.isEmpty()
                ? AutoUpdate.ABOUT[AutoUpdate.OFF] : AutoUpdate.NAMES[AutoUpdate.ON_OPEN]);
    }

    /** Channels & categories' line. */
    static RowList.Row mergedRow(Context c) {
        int n = Merges.pairs(c).size();
        return RowList.Row.item("page:channels.merged", "Merged channels", n == 0 ? "None" : n == 1 ? "1 channel merged" : n + " channels merged");
    }

    // ---- the pages ----------------------------------------------------------------------------------------------------

    @Override public List<RowList.Row> rows(String page) {
        List<RowList.Row> out = new ArrayList<>();
        switch (page) {
            case "about.problems": problems(out); break;
            case "about.auto": auto(out); break;
            case "channels.merged": merged(out); break;
            case AiSettings.PAGE: AiSettings.rows(s, tv, out); break;
            case LICENCES: licences(out); break;
            default: break;
        }
        return out;
    }

    /** The logo catalogue's packs, each with its terms, a paragraph a note. */
    static void licences(List<RowList.Row> out) {
        for (String para : com.gathin.broadcast.LogoPacks.LICENCES.split("\n\n")) {
            String t = para.trim();
            if (t.isEmpty()) continue;
            if (t.equals("Channel logo catalogue")) out.add(RowList.Row.header(t));
            else out.add(RowList.Row.note(t));
        }
    }

    private void problems(List<RowList.Row> out) {
        List<Problems.Note> notes = Problems.notes();
        out.add(RowList.Row.header("Last problem"));
        if (notes.isEmpty()) {
            out.add(RowList.Row.note("Nothing has happened yet."));
            return;
        }
        for (int i = notes.size() - 1; i >= 0; i--) {
            Problems.Note n = notes.get(i);
            if (i == notes.size() - 2) out.add(RowList.Row.header("Before it"));
            String d = n.detail();
            out.add(RowList.Row.item("p:" + i, n.headline(), Problems.when(n.at) + (d.isEmpty() ? "" : "  ·  " + d)));
        }
        out.add(RowList.Row.item("do:clear", "Clear these notes", notes.size() == 1 ? "1 note" : notes.size() + " notes"));
        out.add(RowList.Row.note("Take a photo of them first to send. They stay on this TV and are never sent anywhere: where in "
                + "Homefield TV's own code it happened, never a channel, an address or a login."));
    }

    private void auto(List<RowList.Row> out) {
        int mode = AutoUpdate.mode(tv);
        out.add(MoreSettings.pick("auto.mode", "Automatic updates", AutoUpdate.NAMES[mode]));
        String a = AutoUpdate.address(tv);
        out.add(RowList.Row.item("f:auto.address", "Update address (empty: off)", a.isEmpty() ? "None" : a));
        String state = AutoUpdate.state(), problem = AutoUpdate.problem(tv);
        if (!a.isEmpty()) {
            out.add(RowList.Row.item("do:autocheck", "Look at the update address now",
                    !state.isEmpty() ? state : !problem.isEmpty() ? problem
                            : Updates.checked(Updates.checkedAt(tv), System.currentTimeMillis(), java.time.ZoneId.systemDefault())));
        }
        String room = AutoUpdate.room(tv);
        if (!room.isEmpty()) out.add(RowList.Row.note(room));
        out.add(RowList.Row.note(mode == AutoUpdate.OFF || a.isEmpty() ? AutoUpdate.ABOUT[AutoUpdate.OFF]
                : AutoUpdate.ABOUT[AutoUpdate.ON_OPEN] + ". A newer Homefield TV is fetched to this TV, checked, and Android asks "
                + "before it goes in."));
    }

    private void merged(List<RowList.Row> out) {
        Map<String, String> pairs = Merges.pairs(tv);
        if (pairs.isEmpty()) {
            out.add(RowList.Row.note("None. Hold OK on a channel in the TV guide and choose Merge with"));
        } else {
            Library lib = Library.get(tv);
            for (Map.Entry<String, String> e : pairs.entrySet()) {
                Channel m = lib.exact(e.getKey()), into = lib.byKey(e.getValue());
                String title = nameOf(m) + " merged into " + nameOf(into);
                out.add(RowList.Row.channel("sep:" + e.getKey(), m, title, "OK to separate it"));
            }
            out.add(RowList.Row.item("do:sepall", "Separate all", pairs.size() == 1 ? "1 channel" : pairs.size() + " channels"));
        }
        out.add(RowList.Row.note("A merged channel is out of the lists; its stream, guide and catch-up stand in for the channel it is "
                + "merged into when that one has none or drops."));
    }

    /** A channel's name, or that the playlists don't have it now. */
    private static String nameOf(Channel c) {
        return c == null ? "a channel not in your playlists now" : ChannelEdits.name(c);
    }

    // ---- OK on a line ----------------------------------------------------------------------------------------------------

    @Override public void chosen(String page, RowList.Row row) {
        String t = row.tag instanceof String ? (String) row.tag : "";
        if (t.startsWith("pick:")) {
            s.open(SettingsScreen.PICK + t.substring(5));
            return;
        }
        if (t.startsWith("f:")) {
            s.type(t.substring(2), "Update address", AutoUpdate.address(tv), false);
            return;
        }
        if (t.startsWith("sep:")) {
            Merges.separate(tv, t.substring(4));
            Toast.makeText(tv, "1 channel separated from the one it was merged into", Toast.LENGTH_SHORT).show();
            s.refresh();
            return;
        }
        Context c = tv;
        if (AiSettings.chosen(s, c, t)) return;
        switch (t) {
            case "do:clear":
                Problems.clear();
                s.refresh();
                break;
            case "do:autocheck":
                AutoUpdate.check(tv, s::refresh);
                s.refresh();
                break;
            case "do:sepall": {
                int n = Merges.separateAll(tv);
                Toast.makeText(tv, n == 1 ? "1 channel separated" : n + " channels separated", Toast.LENGTH_SHORT).show();
                s.refresh();
                break;
            }
            default:
                break;
        }
    }

    @Override public SettingsScreen.Picker picker(String key) {
        switch (key) {
            case AiSettings.MODEL:
                return AiSettings.picker(tv);
            case "auto.mode":
                return new SettingsScreen.Picker("Automatic updates", AutoUpdate.NAMES, AutoUpdate.mode(tv), AutoUpdate.ABOUT[AutoUpdate.OFF]);
            default:
                return null;
        }
    }

    @Override public void picked(String key, int index) {
        switch (key) {
            case AiSettings.MODEL:
                AiSettings.picked(tv, index);
                break;
            case "auto.mode":
                AutoUpdate.setMode(tv, index);
                if (index == AutoUpdate.ON_OPEN && !AutoUpdate.address(tv).isEmpty()) AutoUpdate.check(tv, s::refresh);
                break;
            default:
                break;
        }
    }

    boolean ownsField(String field) {
        return field.equals("auto.address") || field.equals(AiSettings.FIELD);
    }

    @Override public String value(String field) {
        return "auto.address".equals(field) ? AutoUpdate.address(tv) : "";
    }

    @Override public void typed(String field, String text) {
        if (AiSettings.FIELD.equals(field)) {
            AiSettings.typed(s, tv, text);
            return;
        }
        if (!"auto.address".equals(field)) return;
        String t = text == null ? "" : text.trim();
        if (!t.isEmpty() && AutoUpdate.web(t) == null) {
            Toast.makeText(tv, "The update address isn't a web address", Toast.LENGTH_LONG).show();
            return;
        }
        AutoUpdate.setAddress(tv, t);
        if (!t.isEmpty() && AutoUpdate.mode(tv) == AutoUpdate.ON_OPEN) AutoUpdate.check(tv, s::refresh);
    }
}
